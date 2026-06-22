package me.songha.concert.seat.api

import kotlinx.coroutines.test.runTest
import me.songha.concert.seat.api.auth.AuthenticatedUserArgumentResolver
import me.songha.concert.seat.application.SeatAlreadyHeldException
import me.songha.concert.seat.application.SeatAlreadySoldException
import me.songha.concert.seat.application.NoSeatHoldsToConfirmException
import me.songha.concert.seat.application.SeatHoldConfirmResult
import me.songha.concert.seat.application.SeatHoldService
import me.songha.concert.seat.application.SeatHoldToggleResult
import me.songha.concert.seat.application.SeatHoldToggleStatus
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class SeatHoldControllerTest {
    private val seatHoldService: SeatHoldService = mock()
    private val webTestClient = WebTestClient
        .bindToController(SeatHoldController(seatHoldService))
        .controllerAdvice(SeatHoldExceptionHandler())
        .argumentResolvers { it.addCustomResolver(AuthenticatedUserArgumentResolver()) }
        .build()

    @Test
    fun `toggle returns created response when hold is created`() = runTest {
        whenever(seatHoldService.toggle(any())).thenReturn(toggleResult(SeatHoldToggleStatus.HELD))

        webTestClient.post()
            .uri("/schedules/schedule-1/seats/seat-1/holds")
            .header(AuthenticatedUserArgumentResolver.AUTHENTICATED_USER_ID_HEADER, "user-1")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.holdId").isEqualTo("hold-1")
            .jsonPath("$.scheduleId").isEqualTo("schedule-1")
            .jsonPath("$.seatId").isEqualTo("seat-1")
            .jsonPath("$.userId").isEqualTo("user-1")
            .jsonPath("$.status").isEqualTo("HELD")
    }

    @Test
    fun `toggle returns ok response when hold is released`() = runTest {
        whenever(seatHoldService.toggle(any())).thenReturn(toggleResult(SeatHoldToggleStatus.RELEASED))

        webTestClient.post()
            .uri("/schedules/schedule-1/seats/seat-1/holds")
            .header(AuthenticatedUserArgumentResolver.AUTHENTICATED_USER_ID_HEADER, "user-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.holdId").doesNotExist()
            .jsonPath("$.status").isEqualTo("RELEASED")
    }

    @Test
    fun `toggle returns conflict when seat is already held by another user`() = runTest {
        whenever(seatHoldService.toggle(any()))
            .thenThrow(SeatAlreadyHeldException("schedule-1", "seat-1"))

        webTestClient.post()
            .uri("/schedules/schedule-1/seats/seat-1/holds")
            .header(AuthenticatedUserArgumentResolver.AUTHENTICATED_USER_ID_HEADER, "user-1")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `toggle returns conflict when seat is already sold`() = runTest {
        whenever(seatHoldService.toggle(any()))
            .thenThrow(SeatAlreadySoldException("schedule-1", "seat-1"))

        webTestClient.post()
            .uri("/schedules/schedule-1/seats/seat-1/holds")
            .header(AuthenticatedUserArgumentResolver.AUTHENTICATED_USER_ID_HEADER, "user-1")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `confirm returns held seat ids`() = runTest {
        whenever(seatHoldService.confirm(any())).thenReturn(confirmResult())

        webTestClient.post()
            .uri("/schedules/schedule-1/holds/confirm")
            .header(AuthenticatedUserArgumentResolver.AUTHENTICATED_USER_ID_HEADER, "user-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.confirmationId").isEqualTo("confirmation-1")
            .jsonPath("$.scheduleId").isEqualTo("schedule-1")
            .jsonPath("$.seatIds[0]").isEqualTo("seat-1")
            .jsonPath("$.seatIds[1]").isEqualTo("seat-2")
            .jsonPath("$.userId").isEqualTo("user-1")
    }

    @Test
    fun `confirm returns not found when there are no active held seats`() = runTest {
        whenever(seatHoldService.confirm(any()))
            .thenThrow(NoSeatHoldsToConfirmException("schedule-1", "user-1"))

        webTestClient.post()
            .uri("/schedules/schedule-1/holds/confirm")
            .header(AuthenticatedUserArgumentResolver.AUTHENTICATED_USER_ID_HEADER, "user-1")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `toggle returns unauthorized when authenticated user header is missing`() = runTest {
        webTestClient.post()
            .uri("/schedules/schedule-1/seats/seat-1/holds")
            .exchange()
            .expectStatus().isUnauthorized
    }

    private fun toggleResult(status: SeatHoldToggleStatus): SeatHoldToggleResult =
        SeatHoldToggleResult(
            holdId = if (status == SeatHoldToggleStatus.HELD) "hold-1" else null,
            scheduleId = "schedule-1",
            seatId = "seat-1",
            userId = "user-1",
            status = status,
            expiresAt = if (status == SeatHoldToggleStatus.HELD) Instant.parse("2026-05-25T12:05:00Z") else null,
        )

    private fun confirmResult(): SeatHoldConfirmResult =
        SeatHoldConfirmResult(
            confirmationId = "confirmation-1",
            scheduleId = "schedule-1",
            seatIds = listOf("seat-1", "seat-2"),
            userId = "user-1",
            occurredAt = Instant.parse("2026-05-25T12:00:00Z"),
        )
}
