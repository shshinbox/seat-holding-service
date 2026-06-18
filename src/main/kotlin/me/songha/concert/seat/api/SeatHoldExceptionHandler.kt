package me.songha.concert.seat.api

import me.songha.concert.seat.api.response.ErrorResponse
import me.songha.concert.seat.application.SeatAlreadyHeldException
import me.songha.concert.seat.application.SeatAlreadySoldException
import me.songha.concert.seat.application.SeatHoldReleaseNotAllowedException
import me.songha.concert.seat.application.UserHoldLimitExceededException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class SeatHoldExceptionHandler {
    @ExceptionHandler(SeatAlreadySoldException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun alreadySold(exception: SeatAlreadySoldException): ErrorResponse =
        ErrorResponse(exception.message ?: "Seat is already sold")

    @ExceptionHandler(SeatAlreadyHeldException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun alreadyHeld(exception: SeatAlreadyHeldException): ErrorResponse =
        ErrorResponse(exception.message ?: "Seat is already held")

    @ExceptionHandler(UserHoldLimitExceededException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun holdLimitExceeded(exception: UserHoldLimitExceededException): ErrorResponse =
        ErrorResponse(exception.message ?: "User hold limit exceeded")

    @ExceptionHandler(SeatHoldReleaseNotAllowedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun releaseNotAllowed(exception: SeatHoldReleaseNotAllowedException): ErrorResponse =
        ErrorResponse(exception.message ?: "Seat hold release is not allowed")
}
