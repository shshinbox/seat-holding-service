package me.songha.concert.seat.infrastructure.kafka

import java.time.Instant
import java.util.*

data class SeatHoldEvent(
    val eventId: String,
    val eventType: SeatHoldEventType,
    val holdId: String,
    val scheduleId: String,
    val seatIds: List<String>,
    val userId: String,
    val expiresAt: Instant?,
    val occurredAt: Instant,
    val schemaVersion: Int = 2,
) {
    companion object {
        fun confirmed(
            confirmationId: String,
            scheduleId: String,
            seatIds: List<String>,
            userId: String,
            occurredAt: Instant,
        ): SeatHoldEvent =
            SeatHoldEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = SeatHoldEventType.SEAT_HOLD_CONFIRMED,
                holdId = confirmationId,
                scheduleId = scheduleId,
                seatIds = seatIds,
                userId = userId,
                expiresAt = null,
                occurredAt = occurredAt,
            )
    }
}

enum class SeatHoldEventType {
    SEAT_HOLD_CONFIRMED,
}
