package me.songha.concert.seat.infrastructure.kafka

import me.songha.concert.seat.infrastructure.redis.SeatHoldOutboxRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import java.time.Instant

class SeatHoldOutboxPublisherTest {
    private val outboxRepository: SeatHoldOutboxRepository = mock()
    private val producer: SeatHoldEventProducer = mock()
    private val publisher = SeatHoldOutboxPublisher(outboxRepository, producer)

    @Test
    fun `publishPending publishes popped event`() {
        val event = event()
        whenever(outboxRepository.moveToProcessing()).thenReturn(Mono.just(event))
        whenever(producer.publish(event)).thenReturn(Mono.just(Unit))
        whenever(outboxRepository.ack(event)).thenReturn(Mono.just(1))

        publisher.publishPending()

        verify(producer).publish(event)
        verify(outboxRepository).ack(event)
        verify(outboxRepository, never()).requeue(event)
    }

    @Test
    fun `publishPending requeues event when publish fails`() {
        val event = event()
        whenever(outboxRepository.moveToProcessing()).thenReturn(Mono.just(event))
        whenever(producer.publish(event)).thenReturn(Mono.error(IllegalStateException("kafka failed")))
        whenever(outboxRepository.requeue(event)).thenReturn(Mono.just(1))

        publisher.publishPending()

        verify(producer).publish(event)
        verify(outboxRepository).requeue(event)
    }

    @Test
    fun `publishPending does nothing when no event exists`() {
        whenever(outboxRepository.moveToProcessing()).thenReturn(Mono.empty())

        publisher.publishPending()

        verify(producer, never()).publish(any())
    }

    private fun event(): SeatHoldEvent =
        SeatHoldEvent(
            eventId = "event-1",
            eventType = SeatHoldEventType.SEAT_HELD,
            holdId = "hold-1",
            venueId = "venue-1",
            seatId = "seat-1",
            userId = "user-1",
            expiresAt = Instant.parse("2026-05-25T12:05:00Z"),
            occurredAt = Instant.parse("2026-05-25T12:00:00Z"),
        )
}
