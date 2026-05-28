package me.songha.concert.seat.api

import me.songha.concert.seat.application.SeatAlreadyHeldException
import me.songha.concert.seat.application.SeatHoldCommand
import me.songha.concert.seat.application.SeatHoldLockUnavailableException
import me.songha.concert.seat.application.SeatHoldReleaseNotAllowedException
import me.songha.concert.seat.application.SeatHoldResult
import me.songha.concert.seat.application.SeatHoldService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant

class SeatHoldControllerTest {
    private val seatHoldService: SeatHoldService = mock()
    private val webTestClient = WebTestClient
        .bindToController(SeatHoldController(seatHoldService))
        .controllerAdvice(SeatHoldExceptionHandler())
        .build()

    @Test
    fun `hold returns created response`() {
        whenever(seatHoldService.hold(any())).thenReturn(Mono.just(result()))

        webTestClient.post()
            .uri("/venues/venue-1/seats/seat-1/holds")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"userId":"user-1"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.holdId").isEqualTo("hold-1")
            .jsonPath("$.venueId").isEqualTo("venue-1")
            .jsonPath("$.seatId").isEqualTo("seat-1")
            .jsonPath("$.userId").isEqualTo("user-1")
    }

    @Test
    fun `hold returns conflict when seat is already held`() {
        whenever(seatHoldService.hold(any()))
            .thenReturn(Mono.error(SeatAlreadyHeldException("venue-1", "seat-1")))

        webTestClient.post()
            .uri("/venues/venue-1/seats/seat-1/holds")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"userId":"user-1"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `hold returns conflict when lock is unavailable`() {
        whenever(seatHoldService.hold(any()))
            .thenReturn(Mono.error(SeatHoldLockUnavailableException("venue-1", "seat-1")))

        webTestClient.post()
            .uri("/venues/venue-1/seats/seat-1/holds")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"userId":"user-1"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `release returns no content`() {
        whenever(seatHoldService.release(any())).thenReturn(Mono.just(Unit))

        webTestClient.delete()
            .uri("/venues/venue-1/seats/seat-1/holds/hold-1?userId=user-1")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `release returns conflict when release is not allowed`() {
        whenever(seatHoldService.release(any()))
            .thenReturn(Mono.error(SeatHoldReleaseNotAllowedException("venue-1", "seat-1", "hold-1")))

        webTestClient.delete()
            .uri("/venues/venue-1/seats/seat-1/holds/hold-1?userId=user-1")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    private fun result(): SeatHoldResult =
        SeatHoldResult(
            holdId = "hold-1",
            venueId = "venue-1",
            seatId = "seat-1",
            userId = "user-1",
            expiresAt = Instant.parse("2026-05-25T12:05:00Z"),
            occurredAt = Instant.parse("2026-05-25T12:00:00Z"),
        )
}
