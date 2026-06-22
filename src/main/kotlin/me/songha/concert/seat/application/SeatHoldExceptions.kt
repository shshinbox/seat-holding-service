package me.songha.concert.seat.application

class SeatAlreadySoldException(scheduleId: String, seatId: String) :
    RuntimeException("Seat is already sold. scheduleId=$scheduleId, seatId=$seatId")

class SeatAlreadyHeldException(scheduleId: String, seatId: String) :
    RuntimeException("Seat is already held. scheduleId=$scheduleId, seatId=$seatId")

class UserHoldLimitExceededException(scheduleId: String, userId: String) :
    RuntimeException("User hold limit exceeded. scheduleId=$scheduleId, userId=$userId")

class NoSeatHoldsToConfirmException(scheduleId: String, userId: String) :
    RuntimeException("No seat holds to confirm. scheduleId=$scheduleId, userId=$userId")
