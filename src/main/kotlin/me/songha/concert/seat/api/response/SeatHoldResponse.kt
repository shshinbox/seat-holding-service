package me.songha.concert.seat.api.response

import java.time.Instant

data class SeatHoldResponse(
    val holdId: String,
    val venueId: String,
    val seatId: String,
    val userId: String,
    val expiresAt: Instant,
)
