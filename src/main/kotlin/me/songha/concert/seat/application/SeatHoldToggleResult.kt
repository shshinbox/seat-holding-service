package me.songha.concert.seat.application

import java.time.Instant

data class SeatHoldToggleResult(
    val holdId: String?,
    val scheduleId: String,
    val seatId: String,
    val userId: String,
    val status: SeatHoldToggleStatus,
    val expiresAt: Instant?,
)

enum class SeatHoldToggleStatus {
    HELD,
    RELEASED,
}

