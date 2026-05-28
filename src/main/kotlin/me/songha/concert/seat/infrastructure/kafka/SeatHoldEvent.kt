package me.songha.concert.seat.infrastructure.kafka

import java.time.Instant

data class SeatHoldEvent(
    val eventId: String,
    val eventType: SeatHoldEventType,
    val holdId: String,
    val venueId: String,
    val seatId: String,
    val userId: String,
    val expiresAt: Instant?,
    val occurredAt: Instant,
    val schemaVersion: Int = 1,
)

enum class SeatHoldEventType {
    SEAT_HELD,
    SEAT_HOLD_RELEASED,
}
