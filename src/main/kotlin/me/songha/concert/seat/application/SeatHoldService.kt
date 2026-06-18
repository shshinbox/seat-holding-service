package me.songha.concert.seat.application

import me.songha.concert.seat.infrastructure.kafka.SeatHoldEvent
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEventProducer
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEventType
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisHoldResult
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisReleaseResult
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class SeatHoldService(
    private val seatHoldRedisRepository: SeatHoldRedisRepository,
    private val seatHoldEventProducer: SeatHoldEventProducer,
    @Value("\${seat-holding.hold-ttl-seconds}") private val holdTtlSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun hold(command: SeatHoldCommand): SeatHoldResult {
        val now = Instant.now()
        val result = SeatHoldResult(
            holdId = UUID.randomUUID().toString(),
            scheduleId = command.scheduleId,
            seatId = command.seatId,
            userId = command.userId,
            expiresAt = now.plusSeconds(holdTtlSeconds),
            occurredAt = now,
        )

        return when (seatHoldRedisRepository.hold(result, Duration.ofSeconds(holdTtlSeconds))) {
            SeatHoldRedisHoldResult.HELD_CREATED -> {
                publish(SeatHoldEvent.held(result))
                result
            }
            SeatHoldRedisHoldResult.SOLD -> throw SeatAlreadySoldException(command.scheduleId, command.seatId)
            SeatHoldRedisHoldResult.HELD -> throw SeatAlreadyHeldException(command.scheduleId, command.seatId)
            SeatHoldRedisHoldResult.LIMIT_EXCEEDED -> throw UserHoldLimitExceededException(command.scheduleId, command.userId)
        }
    }

    suspend fun release(command: SeatHoldReleaseCommand) {
        when (
            val released = seatHoldRedisRepository.release(
                scheduleId = command.scheduleId,
                seatId = command.seatId,
                userId = command.userId,
            )
        ) {
            is SeatHoldRedisReleaseResult.Released -> {
                val now = Instant.now()
                val event = SeatHoldEvent(
                    eventId = UUID.randomUUID().toString(),
                    eventType = SeatHoldEventType.SEAT_HOLD_RELEASED,
                    holdId = released.holdId,
                    scheduleId = command.scheduleId,
                    seatId = command.seatId,
                    userId = command.userId,
                    expiresAt = null,
                    occurredAt = now,
                )
                publish(event)
            }
            SeatHoldRedisReleaseResult.Noop -> Unit
            SeatHoldRedisReleaseResult.Denied ->
                throw SeatHoldReleaseNotAllowedException(command.scheduleId, command.seatId)
        }
    }

    private suspend fun publish(event: SeatHoldEvent) {
        try {
            seatHoldEventProducer.publish(event)
        } catch (error: Exception) {
            log.error(
                "Failed to publish seat hold event. eventType={}, scheduleId={}, seatId={}, userId={}",
                event.eventType,
                event.scheduleId,
                event.seatId,
                event.userId,
                error,
            )
        }
    }
}
