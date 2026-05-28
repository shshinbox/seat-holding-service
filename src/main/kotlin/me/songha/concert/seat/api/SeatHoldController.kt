package me.songha.concert.seat.api

import me.songha.concert.seat.api.request.SeatHoldRequest
import me.songha.concert.seat.api.response.SeatHoldResponse
import me.songha.concert.seat.application.SeatHoldCommand
import me.songha.concert.seat.application.SeatHoldReleaseCommand
import me.songha.concert.seat.application.SeatHoldService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/venues/{venueId}/seats/{seatId}/holds")
class SeatHoldController(
    private val seatHoldService: SeatHoldService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun hold(
        @PathVariable
        venueId: String,
        @PathVariable
        seatId: String,
        @RequestBody request: SeatHoldRequest,
    ): Mono<SeatHoldResponse> =
        seatHoldService.hold(
            SeatHoldCommand(
                venueId = venueId,
                seatId = seatId,
                userId = request.userId,
            ),
        ).map {
            SeatHoldResponse(
                holdId = it.holdId,
                venueId = it.venueId,
                seatId = it.seatId,
                userId = it.userId,
                expiresAt = it.expiresAt,
            )
        }

    @DeleteMapping("/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun release(
        @PathVariable
        venueId: String,
        @PathVariable
        seatId: String,
        @PathVariable
        holdId: String,
        @RequestParam
        userId: String,
    ): Mono<Unit> =
        seatHoldService.release(
            SeatHoldReleaseCommand(
                venueId = venueId,
                seatId = seatId,
                holdId = holdId,
                userId = userId,
            ),
        )
}
