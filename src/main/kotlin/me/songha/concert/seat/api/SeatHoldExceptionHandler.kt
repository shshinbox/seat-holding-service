package me.songha.concert.seat.api

import me.songha.concert.seat.api.dto.ErrorResponse
import me.songha.concert.seat.api.auth.AuthenticationRequiredException
import me.songha.concert.seat.application.NoSeatHoldsToConfirmException
import me.songha.concert.seat.application.SeatAlreadyHeldException
import me.songha.concert.seat.application.SeatAlreadySoldException
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

    @ExceptionHandler(NoSeatHoldsToConfirmException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun noSeatHoldsToConfirm(exception: NoSeatHoldsToConfirmException): ErrorResponse =
        ErrorResponse(exception.message ?: "No seat holds to confirm")

    @ExceptionHandler(AuthenticationRequiredException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun authenticationRequired(exception: AuthenticationRequiredException): ErrorResponse =
        ErrorResponse(exception.message ?: "Authentication is required")
}
