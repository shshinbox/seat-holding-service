package me.songha.concert.seat.api

import me.songha.concert.seat.application.SeatAlreadyHeldException
import me.songha.concert.seat.application.SeatHoldLockUnavailableException
import me.songha.concert.seat.application.SeatHoldReleaseNotAllowedException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class SeatHoldExceptionHandler {
    @ExceptionHandler(SeatAlreadyHeldException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun alreadyHeld(exception: SeatAlreadyHeldException): ErrorResponse =
        ErrorResponse(exception.message ?: "Seat is already held")

    @ExceptionHandler(SeatHoldLockUnavailableException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun lockUnavailable(exception: SeatHoldLockUnavailableException): ErrorResponse =
        ErrorResponse(exception.message ?: "Seat hold is already in progress")

    @ExceptionHandler(SeatHoldReleaseNotAllowedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun releaseNotAllowed(exception: SeatHoldReleaseNotAllowedException): ErrorResponse =
        ErrorResponse(exception.message ?: "Seat hold release is not allowed")
}

data class ErrorResponse(
    val message: String,
)
