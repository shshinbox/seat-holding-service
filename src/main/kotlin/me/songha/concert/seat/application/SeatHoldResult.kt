package me.songha.concert.seat.application

import java.time.Instant

data class SeatHoldResult(
    val holdId: String,
    val scheduleId: String,
    val seatId: String,
    val userId: String,
    val expiresAt: Instant,
    val occurredAt: Instant,
)
