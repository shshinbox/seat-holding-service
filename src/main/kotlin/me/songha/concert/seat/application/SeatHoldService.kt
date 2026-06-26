package me.songha.concert.seat.application

import me.songha.concert.seat.infrastructure.kafka.SeatHoldEvent
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEventProducer
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisRepository
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisToggleResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.*

@Service
class SeatHoldService(
    private val seatHoldRedisRepository: SeatHoldRedisRepository,
    private val seatHoldEventProducer: SeatHoldEventProducer,
    @Value("\${seat-holding.hold-ttl-seconds}") private val holdTtlSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun toggle(command: SeatHoldCommand): SeatHoldToggleResult {
        val now = Instant.now()
        val result = SeatHoldResult(
            holdId = UUID.randomUUID().toString(),
            scheduleId = command.scheduleId,
            seatId = command.seatId,
            userId = command.userId,
            expiresAt = now.plusSeconds(holdTtlSeconds),
            occurredAt = now,
        )

        return when (seatHoldRedisRepository.toggle(result, Duration.ofSeconds(holdTtlSeconds))) {
            SeatHoldRedisToggleResult.HELD_CREATED -> SeatHoldToggleResult(
                holdId = result.holdId,
                scheduleId = result.scheduleId,
                seatId = result.seatId,
                userId = result.userId,
                status = SeatHoldToggleStatus.HELD,
                expiresAt = result.expiresAt,
            )

            SeatHoldRedisToggleResult.HELD_RELEASED -> SeatHoldToggleResult(
                holdId = null,
                scheduleId = command.scheduleId,
                seatId = command.seatId,
                userId = command.userId,
                status = SeatHoldToggleStatus.RELEASED,
                expiresAt = null,
            )

            SeatHoldRedisToggleResult.SOLD -> throw SeatAlreadySoldException(command.scheduleId, command.seatId)
            SeatHoldRedisToggleResult.HELD_BY_ANOTHER_USER -> throw SeatAlreadyHeldException(
                command.scheduleId,
                command.seatId
            )

            SeatHoldRedisToggleResult.LIMIT_EXCEEDED -> throw UserHoldLimitExceededException(
                command.scheduleId,
                command.userId
            )
        }
    }

    suspend fun confirm(command: SeatHoldConfirmCommand): SeatHoldConfirmResult {
        val activeHolds = seatHoldRedisRepository.findActiveHolds(command.scheduleId, command.userId)
        if (activeHolds.isEmpty()) {
            throw NoSeatHoldsToConfirmException(command.scheduleId, command.userId)
        }

        val confirmationId = UUID.randomUUID().toString()
        val now = Instant.now()
        val result = SeatHoldConfirmResult(
            confirmationId = confirmationId,
            scheduleId = command.scheduleId,
            seatIds = activeHolds.map { it.seatId }.sorted(),
            userId = command.userId,
            occurredAt = now,
        )
        publish(
            SeatHoldEvent.confirmed(
                confirmationId = confirmationId,
                scheduleId = command.scheduleId,
                seatIds = result.seatIds,
                userId = command.userId,
                occurredAt = now,
            ),
        )

        return result
    }

    private suspend fun publish(event: SeatHoldEvent) {
        try {
            seatHoldEventProducer.publish(event)
        } catch (error: Exception) {
            log.error(
                "Failed to publish seat hold event. eventType={}, scheduleId={}, seatIds={}, userId={}",
                event.eventType,
                event.scheduleId,
                event.seatIds,
                event.userId,
                error,
            )
            throw error
        }
    }
}
