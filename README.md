# Seat Holding Service

공연/이벤트 좌석을 짧은 시간 동안 선점하는 서비스입니다.

클라이언트가 좌석을 클릭하면 Redis에 선점 상태를 저장하고, 선점 성공/해제 이벤트를 Kafka로 발행합니다. 같은 유저는 하나의 스케줄에서 최대 4개 좌석까지만 동시에 선점할 수 있습니다.

## 기술 스택

- Kotlin
- Spring Boot WebFlux
- Kotlin Coroutines
- Redis
- Redis Lua Script
- Kafka Producer
- Gradle

## 핵심 정책

- 좌석 선점 TTL은 10분입니다.
- 한 유저는 같은 `scheduleId`에서 최대 4개 좌석까지만 선점할 수 있습니다.
- 이미 판매된 좌석은 선점할 수 없습니다.
- 이미 다른 요청이 선점한 좌석은 선점할 수 없습니다.
- 좌석 재클릭은 선점 해제로 처리합니다.
- 판매 완료 상태는 이 서비스가 직접 만들지 않습니다. 예약/결제 확정 흐름에서 `seat:sold:*` 키를 생성한다고 가정합니다.
- Redis 선점 성공 후 Kafka 발행이 실패해도 API는 성공으로 유지하고 에러 로그만 남깁니다. 강한 발행 보장은 추후 outbox 도입으로 보완합니다.

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

- TTL 없음
- 예약/결제 확정 흐름에서 생성
- 이 키가 있으면 선점 요청은 `409 Conflict`

### `seat:hold:{scheduleId}:{seatId}`

선점 중인 좌석입니다.

- TTL 600초
- 값은 `holdId`, `scheduleId`, `seatId`, `userId`, `expiresAt`, `occurredAt`을 포함한 JSON
- 이 키가 있으면 선점 요청은 `409 Conflict`

### `user:holds:{scheduleId}:{userId}`

유저가 현재 선점 중인 좌석 목록입니다.

- Redis Set
- seatId들을 저장
- TTL 600초
- Lua script 실행 시 실제 `seat:hold:*` 존재 여부를 확인하며 만료된 좌석은 Set에서 제거합니다.

## 선점 흐름

```text
POST /schedules/{scheduleId}/seats/{seatId}/holds
  -> SeatHoldController
  -> SeatHoldService
  -> Redis Lua script
      1. sold key 존재 확인
      2. hold key 존재 확인
      3. user:holds Set 정리
      4. 현재 유저 active hold 수 확인
      5. 최대 4개 제한 확인
      6. seat:hold key 생성 + TTL 설정
      7. user:holds Set 추가 + TTL 설정
  -> Kafka SEAT_HELD 이벤트 발행
  -> API 응답
```

Redis Lua script 안에서 좌석 상태 확인과 선점 생성이 한 번에 처리됩니다.

## 선점 해제 흐름

```text
DELETE /schedules/{scheduleId}/seats/{seatId}/holds?userId={userId}
  -> SeatHoldController
  -> SeatHoldService
  -> Redis Lua script
      1. seat:hold key 조회
      2. hold가 없으면 user:holds에서 seatId 제거 후 성공 처리
      3. hold가 있으면 userId 소유권 확인
      4. 소유자가 다르면 409 Conflict
      5. seat:hold 삭제
      6. user:holds에서 seatId 제거
  -> Kafka SEAT_HOLD_RELEASED 이벤트 발행
```

hold가 이미 만료된 좌석에 대한 해제 요청은 성공 처리합니다.

## API

### 좌석 선점

```http
POST /schedules/{scheduleId}/seats/{seatId}/holds
Content-Type: application/json

{
  "userId": "user-1"
}
```

성공 응답:

```http
201 Created
```

```json
{
  "holdId": "8dd1f7a1-6c2c-4f27-89b5-75f334c1b65f",
  "scheduleId": "schedule-1",
  "seatId": "A-1",
  "userId": "user-1",
  "expiresAt": "2026-06-18T12:10:00Z"
}
```

실패 응답:

```http
409 Conflict
```

```json
{
  "message": "Seat is already held. scheduleId=schedule-1, seatId=A-1"
}
```

주요 실패 케이스:

- 이미 판매된 좌석
- 이미 선점된 좌석
- 유저의 동시 선점 좌석 수가 4개 이상

### 좌석 선점 해제

```http
DELETE /schedules/{scheduleId}/seats/{seatId}/holds?userId=user-1
```

성공 응답:

```http
204 No Content
```

다른 유저가 선점한 좌석을 해제하려 하면:

```http
409 Conflict
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

### `SEAT_HELD`

```json
{
  "eventId": "event-uuid",
  "eventType": "SEAT_HELD",
  "holdId": "hold-uuid",
  "scheduleId": "schedule-1",
  "seatId": "A-1",
  "userId": "user-1",
  "expiresAt": "2026-06-18T12:10:00Z",
  "occurredAt": "2026-06-18T12:00:00Z",
  "schemaVersion": 1
}
```

### `SEAT_HOLD_RELEASED`

```json
{
  "eventId": "event-uuid",
  "eventType": "SEAT_HOLD_RELEASED",
  "holdId": "hold-uuid",
  "scheduleId": "schedule-1",
  "seatId": "A-1",
  "userId": "user-1",
  "expiresAt": null,
  "occurredAt": "2026-06-18T12:01:00Z",
  "schemaVersion": 1
}
```