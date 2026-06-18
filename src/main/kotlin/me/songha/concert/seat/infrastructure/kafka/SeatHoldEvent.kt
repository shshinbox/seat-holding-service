package me.songha.concert.seat.infrastructure.kafka

import me.songha.concert.seat.application.SeatHoldResult
import java.time.Instant
import java.util.UUID

data class SeatHoldEvent(
    val eventId: String,
    val eventType: SeatHoldEventType,
    val holdId: String,
    val scheduleId: String,
    val seatId: String,
    val userId: String,
    val expiresAt: Instant?,
    val occurredAt: Instant,
    val schemaVersion: Int = 1,
) {
    companion object {
        fun held(result: SeatHoldResult): SeatHoldEvent =
            SeatHoldEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = SeatHoldEventType.SEAT_HELD,
                holdId = result.holdId,
                scheduleId = result.scheduleId,
                seatId = result.seatId,
                userId = result.userId,
                expiresAt = result.expiresAt,
                occurredAt = result.occurredAt,
            )
    }
}

enum class SeatHoldEventType {
    SEAT_HELD,
    SEAT_HOLD_RELEASED,
}
