package me.songha.concert.seat.application

import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisRepository
import org.redisson.api.RedissonReactiveClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class SeatHoldService(
    private val redissonClient: RedissonReactiveClient,
    private val seatHoldRedisRepository: SeatHoldRedisRepository,
    @Value("\${seat-holding.hold-ttl-seconds}") private val holdTtlSeconds: Long,
    @Value("\${seat-holding.lock-wait-seconds}") private val lockWaitSeconds: Long,
    @Value("\${seat-holding.lock-lease-seconds}") private val lockLeaseSeconds: Long,
) {
    fun hold(command: SeatHoldCommand): Mono<SeatHoldResult> {
        val now = Instant.now()
        val result = SeatHoldResult(
            holdId = UUID.randomUUID().toString(),
            venueId = command.venueId,
            seatId = command.seatId,
            userId = command.userId,
            expiresAt = now.plusSeconds(holdTtlSeconds),
            occurredAt = now,
        )
        val lock = redissonClient.getLock(lockKey(command.venueId, command.seatId))

        return lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS)
            .flatMap { locked ->
                if (!locked) {
                    Mono.error(SeatHoldLockUnavailableException(command.venueId, command.seatId))
                } else {
                    seatHoldRedisRepository.holdIfAbsent(result, Duration.ofSeconds(holdTtlSeconds))
                        .flatMap { created ->
                            if (created) Mono.just(result)
                            else Mono.error(SeatAlreadyHeldException(command.venueId, command.seatId))
                        }
                        .flatMap { createdResult ->
                            lock.unlock()
                                .onErrorResume { Mono.empty() }
                                .thenReturn(createdResult)
                        }
                        .onErrorResume { error ->
                            lock.unlock()
                                .onErrorResume { Mono.empty() }
                                .then(Mono.error(error))
                        }
                }
            }
    }

    fun release(command: SeatHoldReleaseCommand): Mono<Unit> =
        seatHoldRedisRepository.releaseIfOwner(
            venueId = command.venueId,
            seatId = command.seatId,
            holdId = command.holdId,
            userId = command.userId,
        ).flatMap { released ->
            if (released) Mono.just(Unit)
            else Mono.error(SeatHoldReleaseNotAllowedException(command.venueId, command.seatId, command.holdId))
        }

    private fun lockKey(venueId: String, seatId: String): String =
        "lock:venue:$venueId:seat:$seatId"
}

data class SeatHoldCommand(
    val venueId: String,
    val seatId: String,
    val userId: String,
)

data class SeatHoldReleaseCommand(
    val venueId: String,
    val seatId: String,
    val holdId: String,
    val userId: String,
)

data class SeatHoldResult(
    val holdId: String,
    val venueId: String,
    val seatId: String,
    val userId: String,
    val expiresAt: Instant,
    val occurredAt: Instant,
)

class SeatAlreadyHeldException(venueId: String, seatId: String) :
    RuntimeException("Seat is already held. venueId=$venueId, seatId=$seatId")

class SeatHoldLockUnavailableException(venueId: String, seatId: String) :
    RuntimeException("Seat hold is already in progress. venueId=$venueId, seatId=$seatId")

class SeatHoldReleaseNotAllowedException(venueId: String, seatId: String, holdId: String) :
    RuntimeException("Seat hold release is not allowed. venueId=$venueId, seatId=$seatId, holdId=$holdId")
