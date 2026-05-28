package me.songha.concert.seat.application

import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.redisson.api.RLockReactive
import org.redisson.api.RedissonReactiveClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class SeatHoldServiceTest {
    private lateinit var redissonClient: RedissonReactiveClient
    private lateinit var lock: RLockReactive
    private lateinit var repository: SeatHoldRedisRepository
    private lateinit var service: SeatHoldService

    @BeforeEach
    fun setUp() {
        redissonClient = mock()
        lock = mock()
        repository = mock()
        service = SeatHoldService(
            redissonClient = redissonClient,
            seatHoldRedisRepository = repository,
            holdTtlSeconds = 600,
            lockWaitSeconds = 1,
            lockLeaseSeconds = 3,
        )

        whenever(redissonClient.getLock("lock:venue:venue-1:seat:seat-1")).thenReturn(lock)
        whenever(lock.unlock()).thenReturn(Mono.empty())
    }

    @Test
    fun `hold succeeds when lock is acquired and seat is not held`() {
        whenever(lock.tryLock(1, 3, TimeUnit.SECONDS)).thenReturn(Mono.just(true))
        whenever(repository.holdIfAbsent(any(), eq(Duration.ofSeconds(600))))
            .thenReturn(Mono.just(true))

        StepVerifier.create(service.hold(command()))
            .assertNext {
                assertEquals("venue-1", it.venueId)
                assertEquals("seat-1", it.seatId)
                assertEquals("user-1", it.userId)
            }
            .verifyComplete()

        verify(lock).unlock()
    }

    @Test
    fun `hold fails when lock is not acquired`() {
        whenever(lock.tryLock(1, 3, TimeUnit.SECONDS)).thenReturn(Mono.just(false))

        StepVerifier.create(service.hold(command()))
            .expectError(SeatHoldLockUnavailableException::class.java)
            .verify()

        verify(repository, never()).holdIfAbsent(any(), any())
        verify(lock, never()).unlock()
    }

    @Test
    fun `hold fails and unlocks when seat is already held`() {
        whenever(lock.tryLock(1, 3, TimeUnit.SECONDS)).thenReturn(Mono.just(true))
        whenever(repository.holdIfAbsent(any(), eq(Duration.ofSeconds(600))))
            .thenReturn(Mono.just(false))

        StepVerifier.create(service.hold(command()))
            .expectError(SeatAlreadyHeldException::class.java)
            .verify()

        verify(lock).unlock()
    }

    @Test
    fun `hold unlocks when repository fails`() {
        whenever(lock.tryLock(1, 3, TimeUnit.SECONDS)).thenReturn(Mono.just(true))
        whenever(repository.holdIfAbsent(any(), eq(Duration.ofSeconds(600))))
            .thenReturn(Mono.error(IllegalStateException("redis failed")))

        StepVerifier.create(service.hold(command()))
            .expectError(IllegalStateException::class.java)
            .verify()

        verify(lock).unlock()
    }

    @Test
    fun `release succeeds when current hold belongs to user`() {
        whenever(repository.releaseIfOwner("venue-1", "seat-1", "hold-1", "user-1"))
            .thenReturn(Mono.just(true))

        StepVerifier.create(service.release(releaseCommand()))
            .expectNext(Unit)
            .verifyComplete()
    }

    @Test
    fun `release fails when current hold does not belong to user`() {
        whenever(repository.releaseIfOwner("venue-1", "seat-1", "hold-1", "user-1"))
            .thenReturn(Mono.just(false))

        StepVerifier.create(service.release(releaseCommand()))
            .expectError(SeatHoldReleaseNotAllowedException::class.java)
            .verify()
    }

    private fun command(): SeatHoldCommand =
        SeatHoldCommand(
            venueId = "venue-1",
            seatId = "seat-1",
            userId = "user-1",
        )

    private fun releaseCommand(): SeatHoldReleaseCommand =
        SeatHoldReleaseCommand(
            venueId = "venue-1",
            seatId = "seat-1",
            holdId = "hold-1",
            userId = "user-1",
        )
}
