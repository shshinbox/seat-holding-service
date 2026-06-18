package me.songha.concert.seat.api

import kotlinx.coroutines.test.runTest
import me.songha.concert.seat.application.SeatAlreadyHeldException
import me.songha.concert.seat.application.SeatAlreadySoldException
import me.songha.concert.seat.application.SeatHoldReleaseNotAllowedException
import me.songha.concert.seat.application.SeatHoldResult
import me.songha.concert.seat.application.SeatHoldService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class SeatHoldControllerTest {
    private val seatHoldService: SeatHoldService = mock()
    private val webTestClient = WebTestClient
        .bindToController(SeatHoldController(seatHoldService))
        .controllerAdvice(SeatHoldExceptionHandler())
        .build()

    @Test
    fun `hold returns created response`() = runTest {
        whenever(seatHoldService.hold(any())).thenReturn(result())

        webTestClient.post()
            .uri("/schedules/schedule-1/seats/seat-1/holds")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"userId":"user-1"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.holdId").isEqualTo("hold-1")
            .jsonPath("$.scheduleId").isEqualTo("schedule-1")
            .jsonPath("$.seatId").isEqualTo("seat-1")
            .jsonPath("$.userId").isEqualTo("user-1")
    }

    @Test
    fun `hold returns conflict when seat is already held`() = runTest {
        whenever(seatHoldService.hold(any()))
            .thenThrow(SeatAlreadyHeldException("schedule-1", "seat-1"))

        webTestClient.post()
            .uri("/schedules/schedule-1/seats/seat-1/holds")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"userId":"user-1"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `hold returns conflict when seat is already sold`() = runTest {
        whenever(seatHoldService.hold(any()))
            .thenThrow(SeatAlreadySoldException("schedule-1", "seat-1"))

        webTestClient.post()
            .uri("/schedules/schedule-1/seats/seat-1/holds")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"userId":"user-1"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `release returns no content`() = runTest {
        whenever(seatHoldService.release(any())).thenReturn(Unit)

        webTestClient.delete()
            .uri("/schedules/schedule-1/seats/seat-1/holds?userId=user-1")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `release returns conflict when release is not allowed`() = runTest {
        whenever(seatHoldService.release(any()))
            .thenThrow(SeatHoldReleaseNotAllowedException("schedule-1", "seat-1"))

        webTestClient.delete()
            .uri("/schedules/schedule-1/seats/seat-1/holds?userId=user-1")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    private fun result(): SeatHoldResult =
        SeatHoldResult(
            holdId = "hold-1",
            scheduleId = "schedule-1",
            seatId = "seat-1",
            userId = "user-1",
            expiresAt = Instant.parse("2026-05-25T12:05:00Z"),
            occurredAt = Instant.parse("2026-05-25T12:00:00Z"),
        )
}
