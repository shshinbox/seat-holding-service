package me.songha.concert.seat.application

import kotlinx.coroutines.test.runTest
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEvent
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEventProducer
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisHoldResult
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisReleaseResult
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisRepository
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
    fun `hold succeeds when redis creates hold`() = runTest {
        whenever(repository.hold(any(), any())).thenReturn(SeatHoldRedisHoldResult.HELD_CREATED)
        whenever(producer.publish(any())).thenReturn(Unit)

        val result = service.hold(command())

        assertEquals("schedule-1", result.scheduleId)
        assertEquals("seat-1", result.seatId)
        assertEquals("user-1", result.userId)
        verify(repository).hold(any(), eq(Duration.ofSeconds(600)))
        verify(producer).publish(any())
    }

    @Test
    fun `hold succeeds even when kafka publish fails`() = runTest {
        whenever(repository.hold(any(), any())).thenReturn(SeatHoldRedisHoldResult.HELD_CREATED)
        whenever(producer.publish(any())).thenThrow(IllegalStateException("kafka failed"))

        service.hold(command())
    }

    @Test
    fun `hold fails when seat is already sold`() = runTest {
        whenever(repository.hold(any(), any())).thenReturn(SeatHoldRedisHoldResult.SOLD)

        assertFailsWith<SeatAlreadySoldException> {
            service.hold(command())
        }

        verify(producer, never()).publish(any<SeatHoldEvent>())
    }

    @Test
    fun `hold fails when seat is already held`() = runTest {
        whenever(repository.hold(any(), any())).thenReturn(SeatHoldRedisHoldResult.HELD)

        assertFailsWith<SeatAlreadyHeldException> {
            service.hold(command())
        }
    }

    @Test
    fun `hold fails when user hold limit is exceeded`() = runTest {
        whenever(repository.hold(any(), any())).thenReturn(SeatHoldRedisHoldResult.LIMIT_EXCEEDED)

        assertFailsWith<UserHoldLimitExceededException> {
            service.hold(command())
        }
    }

    @Test
    fun `release succeeds when current hold belongs to user`() = runTest {
        whenever(repository.release("schedule-1", "seat-1", "user-1"))
            .thenReturn(SeatHoldRedisReleaseResult.Released("hold-1"))
        whenever(producer.publish(any())).thenReturn(Unit)

        service.release(releaseCommand())

        verify(producer).publish(any())
    }

    @Test
    fun `release fails when current hold does not belong to user`() = runTest {
        whenever(repository.release("schedule-1", "seat-1", "user-1"))
            .thenReturn(SeatHoldRedisReleaseResult.Denied)

        assertFailsWith<SeatHoldReleaseNotAllowedException> {
            service.release(releaseCommand())
        }
    }

    private fun command(): SeatHoldCommand =
        SeatHoldCommand(
            scheduleId = "schedule-1",
            seatId = "seat-1",
            userId = "user-1",
        )

    private fun releaseCommand(): SeatHoldReleaseCommand =
        SeatHoldReleaseCommand(
            scheduleId = "schedule-1",
            seatId = "seat-1",
            userId = "user-1",
        )
}
