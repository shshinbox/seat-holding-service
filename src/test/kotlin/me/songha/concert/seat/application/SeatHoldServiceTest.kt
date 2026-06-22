package me.songha.concert.seat.application

import kotlinx.coroutines.test.runTest
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEvent
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEventProducer
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisRepository
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisToggleResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SeatHoldServiceTest {
    private lateinit var repository: SeatHoldRedisRepository
    private lateinit var producer: SeatHoldEventProducer
    private lateinit var service: SeatHoldService

    @BeforeEach
    fun setUp() {
        repository = mock()
        producer = mock()
        service = SeatHoldService(
            seatHoldRedisRepository = repository,
            seatHoldEventProducer = producer,
            holdTtlSeconds = 600,
        )
    }

    @Test
    fun `toggle creates hold when redis creates hold`() = runTest {
        whenever(repository.toggle(any(), any())).thenReturn(SeatHoldRedisToggleResult.HELD_CREATED)

        val result = service.toggle(command())

        assertEquals("schedule-1", result.scheduleId)
        assertEquals("seat-1", result.seatId)
        assertEquals("user-1", result.userId)
        assertEquals(SeatHoldToggleStatus.HELD, result.status)
        verify(repository).toggle(any(), eq(Duration.ofSeconds(600)))
        verify(producer, never()).publish(any())
    }

    @Test
    fun `toggle releases hold when redis releases existing hold`() = runTest {
        whenever(repository.toggle(any(), any())).thenReturn(SeatHoldRedisToggleResult.HELD_RELEASED)

        val result = service.toggle(command())

        assertEquals(SeatHoldToggleStatus.RELEASED, result.status)
        assertEquals(null, result.holdId)
        verify(producer, never()).publish(any())
    }

    @Test
    fun `toggle fails when seat is already sold`() = runTest {
        whenever(repository.toggle(any(), any())).thenReturn(SeatHoldRedisToggleResult.SOLD)

        assertFailsWith<SeatAlreadySoldException> {
            service.toggle(command())
        }

        verify(producer, never()).publish(any<SeatHoldEvent>())
    }

    @Test
    fun `toggle fails when seat is already held by another user`() = runTest {
        whenever(repository.toggle(any(), any())).thenReturn(SeatHoldRedisToggleResult.HELD_BY_ANOTHER_USER)

        assertFailsWith<SeatAlreadyHeldException> {
            service.toggle(command())
        }
    }

    @Test
    fun `toggle fails when user hold limit is exceeded`() = runTest {
        whenever(repository.toggle(any(), any())).thenReturn(SeatHoldRedisToggleResult.LIMIT_EXCEEDED)

        assertFailsWith<UserHoldLimitExceededException> {
            service.toggle(command())
        }
    }

    @Test
    fun `confirm publishes active held seats`() = runTest {
        whenever(repository.findActiveHolds("schedule-1", "user-1"))
            .thenReturn(listOf(result("seat-1"), result("seat-2")))
        whenever(producer.publish(any())).thenReturn(Unit)

        val result = service.confirm(SeatHoldConfirmCommand("schedule-1", "user-1"))

        assertEquals("schedule-1", result.scheduleId)
        assertEquals(listOf("seat-1", "seat-2"), result.seatIds)
        assertEquals("user-1", result.userId)
        verify(producer).publish(any())
    }

    @Test
    fun `confirm fails when there are no active held seats`() = runTest {
        whenever(repository.findActiveHolds("schedule-1", "user-1")).thenReturn(emptyList())

        assertFailsWith<NoSeatHoldsToConfirmException> {
            service.confirm(SeatHoldConfirmCommand("schedule-1", "user-1"))
        }

        verify(producer, never()).publish(any())
    }

    private fun command(): SeatHoldCommand =
        SeatHoldCommand(
            scheduleId = "schedule-1",
            seatId = "seat-1",
            userId = "user-1",
        )

    private fun result(seatId: String): SeatHoldResult =
        SeatHoldResult(
            holdId = "hold-$seatId",
            scheduleId = "schedule-1",
            seatId = seatId,
            userId = "user-1",
            expiresAt = java.time.Instant.parse("2026-05-25T12:05:00Z"),
            occurredAt = java.time.Instant.parse("2026-05-25T12:00:00Z"),
        )
}
