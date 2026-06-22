package me.songha.concert.seat.application

data class SeatHoldCommand(
    val scheduleId: String,
    val seatId: String,
    val userId: String,
)

data class SeatHoldConfirmCommand(
    val scheduleId: String,
    val userId: String,
)
