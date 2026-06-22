package me.songha.concert.seat.api.dto

import me.songha.concert.seat.application.SeatHoldToggleStatus
import java.time.Instant

data class SeatHoldToggleResponse(
    val holdId: String?,
    val scheduleId: String,
    val seatId: String,
    val userId: String,
    val status: SeatHoldToggleStatus,
    val expiresAt: Instant?,
)
