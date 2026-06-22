# Seat Holding Service

콘서트 좌석을 선점하고, 사용자가 예약하기를 확정했을 때 선점 좌석 목록을 Kafka로 발행하는 서비스입니다.

클라이언트의 좌석 클릭은 Redis 선점 상태만 변경합니다. Kafka 이벤트는 좌석 클릭 시점이 아니라 예약하기 버튼을 눌러 confirm API를 호출할 때 발행됩니다.

## 기술 스택

- Kotlin
- Spring Boot WebFlux
- Kotlin Coroutines
- Reactive Redis
- Redis Lua Script
- Kafka Producer
- Gradle

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

설정:

```yaml
seat-holding:
  kafka:
    topic: seat-hold-events
```

현재 실제 발행되는 이벤트는 confirm API에서 발행하는 `SEAT_HOLD_CONFIRMED`입니다.

### `SEAT_HOLD_CONFIRMED`

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

`seatIds`는 confirm 시점에 살아있는 hold 좌석만 포함합니다. Redis Set은 순서를 보장하지 않기 때문에 서비스에서 정렬 후 발행합니다.
