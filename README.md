# Seat Holding Service

좌석 선점 요청에서 발생할 수 있는 race condition을 다루기 위한 MSA 예제 서비스입니다.
동일한 `venueId`, `seatId`에 여러 요청이 동시에 들어와도 하나의 요청만 선점에 성공하도록 Redis, Redisson, Kafka를 사용합니다.

## 목표

- 같은 좌석에 대한 동시 선점 요청을 분산락으로 제어합니다.
- 선점 상태는 Redis에 TTL과 함께 저장합니다.
- 선점 성공 이벤트는 Kafka로 발행하여 예약 서비스의 pending reservation 생성을 트리거합니다.
- Kafka consumer 처리 여부와 관계없이 선점 임계 구역이 끝나면 락을 해제합니다.
- Kafka 발행 실패에 대비해 Redis 기반 Outbox 큐를 둡니다.

## 기술 스택

- Kotlin
- Spring Boot WebFlux
- Redis
- Redisson
- Kafka
- Gradle

## 흐름

```text
Client
  -> POST /venues/{venueId}/seats/{seatId}/holds
  -> SeatHoldService
  -> Redisson lock 획득
  -> Redis Lua script 실행
      1. hold key 존재 여부 확인
      2. hold 상태 TTL 저장
      3. outbox pending queue에 SeatHeldEvent 저장
  -> Redisson lock 해제
  -> SeatHoldOutboxPublisher가 pending event를 Kafka로 발행
```

이 서비스가 발행하는 Kafka 메시지는 좌석 선점 이벤트입니다.

```text
SeatHeldEvent
= 특정 사용자가 특정 좌석을 expiresAt까지 선점했다는 사실
```

최종 예약 확정은 예약 서비스의 책임입니다. 예약 서비스(TO-DO)는 `SeatHeldEvent`를 소비해 `HOLDING` 상태의 예약 초안을 만들고, 사용자의 결제/예약 버튼 액션이 들어왔을 때 상태를 변경합니다.

## 락 전략

락 키는 좌석 단위로 잡습니다.

```text
lock:venue:{venueId}:seat:{seatId}
```

이렇게 하면 서로 다른 좌석 선점 요청은 병렬로 처리되고, 같은 좌석에 대한 요청만 짧게 직렬화됩니다.

Redisson lock은 다음 목적만 가집니다.

- 같은 좌석에 대한 hold 생성 로직 동시 진입 방지
- Redis hold 상태 확인과 저장 구간 보호
- Outbox 적재까지 완료된 뒤 해제

Kafka consumer가 이벤트를 처리할 때까지 락을 잡지 않습니다. 락은 downstream 처리 완료 보장이 아니라 선점 임계 구역 보호를 위한 장치입니다.

중요한 점은 Redisson lock이 좌석 선점 상태가 아니라는 것입니다.

```text
Redisson lock
= hold key를 안전하게 만들기 위한 짧은 동시성 제어 장치

Redis hold key
= 사용자가 결제 전까지 좌석을 임시 점유하고 있음을 나타내는 비즈니스 상태
```

따라서 Redisson lock이 해제되어도 좌석 선점이 해제되는 것은 아닙니다. 좌석 선점 상태는 Redis hold key가 TTL 동안 유지하면서 보호합니다.

사용자가 페이지를 이탈했을 때 풀려야 하는 것도 Redisson lock이 아니라 Redis에 저장된 hold 상태입니다. Redisson lock은 hold 생성 임계 구역이 끝나면 바로 해제되고, hold 상태는 TTL 또는 release API로 해제됩니다.

## Redis 저장 구조

선점 상태 key:

```text
hold:venue:{venueId}:seat:{seatId}
```

값에는 `holdId`, `venueId`, `seatId`, `userId`, `expiresAt`, `occurredAt`이 저장됩니다.
TTL은 `seat-holding.hold-ttl-seconds` 설정을 따릅니다.

lock key와 hold key는 동시에 잠깐 존재할 수 있습니다.

```text
처리 중:
  lock:venue:{venueId}:seat:{seatId}
  hold:venue:{venueId}:seat:{seatId}

처리 완료 후:
  hold:venue:{venueId}:seat:{seatId}
```

정상 처리 후에는 lock key는 사라지고 hold key만 TTL 동안 남습니다.

Outbox pending queue:

```text
seat-holding:outbox:pending
```

Outbox processing queue:

```text
seat-holding:outbox:processing
```

선점 상태 저장과 Outbox 적재는 Lua script로 한 번에 처리합니다. 이 때문에 선점 상태만 저장되고 이벤트가 누락되는 중간 상태를 줄일 수 있습니다.

## Outbox 전략

이 프로젝트는 Redis 기반 Outbox를 사용합니다.

선점 성공 시 Kafka에 바로 발행하지 않고, 먼저 Redis Outbox queue에 `SeatHeldEvent`를 적재합니다. 이후 `SeatHoldOutboxPublisher`가 주기적으로 이벤트를 꺼내 Kafka에 발행합니다.

Kafka 발행에 실패하면 이벤트를 다시 Outbox queue에 넣어 재시도합니다.

```text
Redis hold 저장 성공
  -> Redis outbox 적재 성공
  -> API 성공 응답
  -> scheduler가 pending queue에서 processing queue로 이동
  -> Kafka 발행
  -> 성공 시 processing queue에서 제거
  -> 실패 시 pending queue로 복구
```

이 구조의 목적은 Redis에는 hold가 생겼는데 Kafka 발행 실패로 예약 서비스에 pending reservation이 생성되지 않는 상태를 줄이는 것입니다.

이 서비스의 source of truth는 Redis hold 상태입니다. 따라서 Redis hold 저장과 Redis Outbox 적재를 Lua script로 묶는 것은 현재 도메인에서는 일관성이 있습니다.

현재 구현은 Redis List 기반 Outbox이며, pending queue와 processing queue를 분리합니다.

```text
RPOPLPUSH pending -> processing
-> Kafka publish
-> 성공 시 processing에서 remove
-> 실패 시 processing에서 remove 후 pending으로 requeue
```

이 방식은 단순 `pop -> publish`보다 유실 위험이 낮습니다. 다만 프로세스 장애 시 processing queue에 남은 이벤트를 복구하는 별도 스캐너까지 있어야 더 완성도가 높습니다. 더 개선된 방식은 다음 중 하나입니다.

- processing queue 복구 스캐너를 추가합니다.
- Redis Stream과 consumer group을 사용합니다.
- 더 강한 영속성이 필요한 이벤트는 RDB Outbox 테이블을 사용합니다.

예약 확정, 결제 완료, 티켓 발급 같은 최종 상태 이벤트는 예약 서비스나 결제 서비스의 RDB transaction과 RDB Outbox로 처리하는 편이 더 적합합니다.

## 예약 서비스 연계

이 프로젝트는 Kafka consumer를 가진 별도의 `reservation-service`가 있다고 가정합니다.

`seat-holding-service`가 발행하는 이벤트:

```json
{
  "eventId": "event-uuid",
  "holdId": "hold-uuid",
  "venueId": "venue-1",
  "seatId": "A-12",
  "userId": "user-1",
  "expiresAt": "2026-05-25T12:05:00Z",
  "occurredAt": "2026-05-25T12:00:00Z"
}
```


Kafka는 적어도 한 번 전달될 수 있으므로 reservation-service는 `holdId` 또는 `eventId`에 unique constraint를 두어 중복 INSERT를 막아야 합니다.

## 페이지 이탈과 선점 해제

페이지 이탈 시 해제해야 하는 대상은 Redisson lock이 아니라 Redis hold 상태입니다.

정상 이탈 또는 사용자의 취소 액션:

```text
DELETE /venues/{venueId}/seats/{seatId}/holds/{holdId}
  -> 현재 hold의 holdId/userId 확인
  -> 일치하면 Redis hold 삭제
  -> SeatHoldReleasedEvent 발행 대상 적재
```

삭제도 단순 `DEL`이 아니라 현재 선점자가 맞는지 확인한 뒤 처리해야 합니다. 이 작업 역시 Lua script로 처리하는 편이 안전합니다.

비정상 이탈, 브라우저 강제 종료, 네트워크 단절 같은 경우 release 요청이 오지 않을 수 있습니다. 그래서 TTL은 반드시 유지해야 합니다.

## API

### 좌석 선점

```http
POST /venues/{venueId}/seats/{seatId}/holds
Content-Type: application/json

{
  "userId": "user-1"
}
```

성공 응답:

```json
{
  "holdId": "8dd1f7a1-6c2c-4f27-89b5-75f334c1b65f",
  "venueId": "venue-1",
  "seatId": "seat-1",
  "userId": "user-1",
  "expiresAt": "2026-05-25T12:00:00Z"
}
```

이미 선점된 좌석이거나 같은 좌석 선점 요청이 진행 중이면 `409 Conflict`를 반환합니다.


## 설계 포인트

- `venueId`, `seatId` 단위로 Redisson lock을 짧게 잡아 같은 좌석의 hold 생성 로직만 직렬화합니다.
- 결제 전까지 좌석을 막는 것은 lock이 아니라 Redis hold key와 TTL입니다.
- Redis TTL로 선점 만료를 자동 처리합니다.
- 선점 저장과 Outbox 적재를 Lua script로 원자 처리합니다.
- Kafka 발행은 Outbox scheduler가 담당합니다.
- Kafka consumer 처리 완료는 락 해제 조건이 아닙니다.
- Kafka 메시지는 reservation-service의 pending reservation 생성을 위한 `SeatHeldEvent`입니다.
- 페이지 이탈 시에는 lock이 아니라 hold 상태를 release해야 하며, release 누락에 대비해 TTL을 유지합니다.
