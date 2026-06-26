package me.songha.concert.seat.api

import me.songha.concert.seat.api.auth.AuthenticatedUser
import me.songha.concert.seat.api.dto.SeatHoldConfirmResponse
import me.songha.concert.seat.api.dto.SeatHoldToggleResponse
import me.songha.concert.seat.application.SeatHoldCommand
import me.songha.concert.seat.application.SeatHoldConfirmCommand
import me.songha.concert.seat.application.SeatHoldService
import me.songha.concert.seat.application.SeatHoldToggleStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/holding/schedule/{scheduleId}")
class SeatHoldController(
    private val seatHoldService: SeatHoldService,
) {
    @PostMapping("/seats/{seatId}/holds")
    suspend fun toggle(
        @PathVariable
        scheduleId: String,
        @PathVariable
        seatId: String,
        authenticatedUser: AuthenticatedUser,
    ): ResponseEntity<SeatHoldToggleResponse> =
        seatHoldService.toggle(
            SeatHoldCommand(
                scheduleId = scheduleId,
                seatId = seatId,
                userId = authenticatedUser.id,
            ),
        ).let {
            val status = if (it.status == SeatHoldToggleStatus.HELD) {
                HttpStatus.CREATED
            } else {
                HttpStatus.OK
            }

            ResponseEntity.status(status).body(
                SeatHoldToggleResponse(
                    holdId = it.holdId,
                    scheduleId = it.scheduleId,
                    seatId = it.seatId,
                    userId = it.userId,
                    status = it.status,
                    expiresAt = it.expiresAt,
                ),
            )
        }

    @PostMapping("/holds/confirm")
    suspend fun confirm(
        @PathVariable
        scheduleId: String,
        authenticatedUser: AuthenticatedUser,
    ): SeatHoldConfirmResponse =
        seatHoldService.confirm(
            SeatHoldConfirmCommand(
                scheduleId = scheduleId,
                userId = authenticatedUser.id,
            ),
        ).let {
            SeatHoldConfirmResponse(
                confirmationId = it.confirmationId,
                scheduleId = it.scheduleId,
                seatIds = it.seatIds,
                userId = it.userId,
                occurredAt = it.occurredAt,
            )
        }
}
