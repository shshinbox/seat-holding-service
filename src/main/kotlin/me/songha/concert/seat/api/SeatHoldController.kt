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

@RestController
@RequestMapping("/schedules/{scheduleId}/seats/{seatId}/holds")
class SeatHoldController(
    private val seatHoldService: SeatHoldService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun hold(
        @PathVariable
        scheduleId: String,
        @PathVariable
        seatId: String,
        @RequestBody request: SeatHoldRequest,
    ): SeatHoldResponse =
        seatHoldService.hold(
            SeatHoldCommand(
                scheduleId = scheduleId,
                seatId = seatId,
                userId = request.userId,
            ),
        ).let {
            SeatHoldResponse(
                holdId = it.holdId,
                scheduleId = it.scheduleId,
                seatId = it.seatId,
                userId = it.userId,
                expiresAt = it.expiresAt,
            )
        }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun release(
        @PathVariable
        scheduleId: String,
        @PathVariable
        seatId: String,
        @RequestParam
        userId: String,
    ) {
        seatHoldService.release(
            SeatHoldReleaseCommand(
                scheduleId = scheduleId,
                seatId = seatId,
                userId = userId,
            ),
        )
    }
}
