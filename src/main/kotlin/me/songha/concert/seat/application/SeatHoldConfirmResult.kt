package me.songha.concert.seat.application

import java.time.Instant

data class SeatHoldConfirmResult(
    val confirmationId: String,
    val scheduleId: String,
    val seatIds: List<String>,
    val userId: String,
    val occurredAt: Instant,
)
