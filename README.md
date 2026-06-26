# Seat Holding Service

콘서트 좌석을 선점하고, 사용자가 예약하기를 확정했을 때 선점 좌석 목록을 Kafka로 발행하는 서비스입니다.


## 기술 스택

- Kotlin
- Spring Boot WebFlux
- Kotlin Coroutines
- Reactive Redis
- Redis Lua Script
- Kafka Producer
- Gradle

## 기술 선택 사유

### Redis Lua Script로 좌석 선점 처리

좌석 선점은 동시 클릭 경쟁이 발생하므로 원자 처리가 필요합니다. 이 서비스는 Redisson 분산 락 라이브러리 대신 Redis Lua Script로 sold 확인, hold/release 판단, 사용자별 선점 수 검증, TTL 설정을 한 번에 처리합니다.

- Redis 서버에서 단일 스크립트로 실행되어 중간에 다른 명령이 끼어들지 않습니다.
- 여러 Redis 명령을 애플리케이션에서 순차 호출하지 않고 한 번의 Redis 호출로 끝냅니다.
- 좌석 선점은 짧은 상태 검증과 갱신 작업이므로, 별도 락 획득/해제보다 Lua Script가 현재 요구사항에 더 단순합니다.

### Spring MVC 대신 WebFlux와 Kotlin Coroutines 사용

현재 구조는 짧은 요청-응답 API라서 Spring MVC로도 구현할 수 있습니다. 다만 좌석 선점과 대기열 같은 API는 순간 요청량이 커질 수 있고, Redis/Kafka I/O 대기가 있기 때문에 WebFlux를 선택했습니다.

- WebFlux는 Redis/Kafka 같은 외부 I/O 대기 시간이 길어져도 thread 점유를 줄이기 쉽습니다.
- Kotlin Coroutines의 `suspend fun`을 사용해 WebFlux의 비동기 I/O 모델은 유지하면서도, 서비스 로직은 `Mono`/`Flux` 체인 없이 순차 코드처럼 작성했습니다.

### Kafka로 예약 생성 흐름 분리

이 서비스는 최종 예약 데이터 생성까지 직접 처리하지 않고, confirm 시점에 살아있는 선점 좌석을 Kafka topic `seat-hold-events`에 메시지로 발행합니다. 이 메시지의 `eventType`은 `SEAT_HOLD_CONFIRMED`이며, 예약 생성은 해당 메시지를 소비하는 별도 서비스의 책임으로 둡니다.

- 좌석 선점 API와 예약 생성 처리를 분리합니다.
- 예약 생성 쪽 부하나 실패가 앞단 좌석 선점 흐름에 직접 전파되는 범위를 줄입니다.
- 대기열, 좌석 선점, 예약 생성을 각각 다른 변경 이유를 가진 컴포넌트로 나눌 수 있습니다.
- Kafka producer는 별도 서비스로 분리하지 않고, 좌석 선점 서비스 안에서 Kafka 메시지를 발행하는 구성요소로 둡니다.


## 정책

- 좌석 선점 TTL은 600초입니다.
- 같은 사용자는 하나의 `scheduleId`에서 최대 4개 좌석까지 선점할 수 있습니다.
- 이미 판매된 좌석은 선점할 수 없습니다.
- 다른 사용자가 선점 중인 좌석은 선점할 수 없습니다.
- 같은 사용자가 이미 선점한 좌석을 다시 클릭하면 선점이 취소됩니다.
- 예약하기 confirm API 엔드포인트에서 Kafka 이벤트를 발행합니다.
- API의 사용자 식별자는 `X-Authenticated-User-Id` 헤더에서 resolver로 추출합니다.

## 인증 사용자

모든 API는 다음 헤더가 필요합니다.

```http
X-Authenticated-User-Id: user-1
```

헤더가 없거나 blank이면 `401 Unauthorized`를 반환합니다.

```json
{
  "message": "X-Authenticated-User-Id header is required."
}
```

## Redis 키

Redis Cluster를 고려해 `scheduleId`를 hash tag로 사용합니다.

```text
seat:sold:{scheduleId}:{seatId}
seat:hold:{scheduleId}:{seatId}
user:holds:{scheduleId}:{userId}
```

예시:

```text
seat:sold:{schedule-1}:A-1
seat:hold:{schedule-1}:A-1
user:holds:{schedule-1}:user-1
```

### `seat:sold:{scheduleId}:{seatId}`

판매 완료 좌석입니다.

- TTL 없음 (혹은 공연 완료 이후로 설정)
- 예약/결제 확정 흐름에서 생성된다고 가정합니다.

### `seat:hold:{scheduleId}:{seatId}`

선점 중인 좌석입니다.

- TTL 600초
- 값은 `holdId`, `scheduleId`, `seatId`, `userId`, `expiresAt`, `occurredAt`을 포함한 JSON입니다.
- 다른 사용자의 hold가 존재하면 좌석 클릭 API는 `409 Conflict`를 반환합니다.
- 같은 사용자의 hold가 존재하면 좌석 클릭 API는 해당 hold를 삭제하고 `RELEASED` 상태를 반환합니다.

### `user:holds:{scheduleId}:{userId}`

사용자별 동시 선점 수를 Redis Lua script 안에서 원자적으로 검증하기 위한 보조 인덱스입니다.

- Redis Set
- 사용자가 선점 시도에 성공한 `seatId` 묶음을 저장합니다.
- TTL 600초


## 좌석 클릭 흐름

```text
POST /schedules/{scheduleId}/seats/{seatId}/holds
  -> AuthenticatedUserArgumentResolver
      1. X-Authenticated-User-Id 헤더에서 userId 추출
  -> SeatHoldController.toggle
  -> SeatHoldService.toggle
  -> Redis Lua script
      1. sold key 존재 확인
      2. hold key 존재 확인
      3. 같은 user의 hold이면 seat:hold 삭제 + user:holds에서 제거
      4. 다른 user의 hold이면 conflict
      5. user:holds Set에서 만료된 seatId 정리
      6. 현재 user active hold 개수 확인
      7. 최대 4개 제한 확인
      8. 이번 seatId의 seat:hold key 생성 + TTL 설정
      9. 이번 seatId를 user:holds Set에 추가 + TTL 설정
  -> API 응답
```

좌석 클릭 API는 Kafka 이벤트를 발행하지 않습니다.

## 예약하기 confirm 흐름

```text
POST /schedules/{scheduleId}/holds/confirm
  -> AuthenticatedUserArgumentResolver
      1. X-Authenticated-User-Id 헤더에서 userId 추출
  -> SeatHoldController.confirm
  -> SeatHoldService.confirm
  -> Redis
      1. user:holds:{scheduleId}:{userId} 조회
      2. 실제 seat:hold:* 키가 살아있는 좌석만 필터링
      3. 만료되었거나 다른 user의 hold는 user:holds Set에서 정리
  -> Kafka SEAT_HOLD_CONFIRMED 이벤트 발행
  -> API 응답
```

confirm 시점에 살아있는 hold 좌석이 없으면 `404 Not Found`를 반환합니다.

## API

### 좌석 클릭 토글

좌석을 클릭하면 hold 또는 release 중 하나로 처리됩니다.

```http
POST /schedules/{scheduleId}/seats/{seatId}/holds
X-Authenticated-User-Id: user-1
```


hold 생성 응답:

```http
201 Created
```

```json
{
  "holdId": "8dd1f7a1-6c2c-4f27-89b5-75f334c1b65f",
  "scheduleId": "schedule-1",
  "seatId": "A-1",
  "userId": "user-1",
  "status": "HELD",
  "expiresAt": "2026-06-23T12:10:00Z"
}
```

같은 사용자가 다시 클릭해 hold를 취소한 응답:

```http
200 OK
```

```json
{
  "holdId": null,
  "scheduleId": "schedule-1",
  "seatId": "A-1",
  "userId": "user-1",
  "status": "RELEASED",
  "expiresAt": null
}
```

주요 실패 응답:

```http
409 Conflict
```

```json
{
  "message": "Seat is already held. scheduleId=schedule-1, seatId=A-1"
}
```

실패 케이스:

- 이미 판매된 좌석
- 다른 사용자가 선점 중인 좌석
- 사용자의 동시 선점 좌석 수가 4개 이상

### 예약하기 confirm

현재 사용자가 선점 중인 좌석 목록을 Kafka로 발행합니다.

```http
POST /schedules/{scheduleId}/holds/confirm
X-Authenticated-User-Id: user-1
```

성공 응답:

```http
200 OK
```

```json
{
  "confirmationId": "confirmation-uuid",
  "scheduleId": "schedule-1",
  "seatIds": ["A-1", "A-2"],
  "userId": "user-1",
  "occurredAt": "2026-06-23T12:00:00Z"
}
```

선점 중인 좌석이 없으면:

```http
404 Not Found
```

```json
{
  "message": "No seat holds to confirm. scheduleId=schedule-1, userId=user-1"
}
```

## Kafka 이벤트

토픽:

```text
seat-hold-events
```

```json
{
  "eventId": "event-uuid",
  "eventType": "SEAT_HOLD_CONFIRMED",
  "holdId": "confirmation-uuid",
  "scheduleId": "schedule-1",
  "seatIds": ["A-1", "A-2"],
  "userId": "user-1",
  "expiresAt": null,
  "occurredAt": "2026-06-23T12:00:00Z",
  "schemaVersion": 2
}
```
