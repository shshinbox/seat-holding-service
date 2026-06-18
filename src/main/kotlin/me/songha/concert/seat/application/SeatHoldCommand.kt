package me.songha.concert.seat.application

data class SeatHoldCommand(
    val scheduleId: String,
    val seatId: String,
    val userId: String,
)

data class SeatHoldReleaseCommand(
    val scheduleId: String,
    val seatId: String,
    val userId: String,
)
