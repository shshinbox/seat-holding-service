package me.songha.concert.seat.api.dto

import java.time.Instant

data class SeatHoldConfirmResponse(
    val confirmationId: String,
    val scheduleId: String,
    val seatIds: List<String>,
    val userId: String,
    val occurredAt: Instant,
)
