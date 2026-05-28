package me.songha.concert.seat.infrastructure.kafka

import me.songha.concert.seat.infrastructure.redis.SeatHoldOutboxRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SeatHoldOutboxPublisher(
    private val outboxRepository: SeatHoldOutboxRepository,
    private val producer: SeatHoldEventProducer,
) {
    @Scheduled(fixedDelayString = "\${seat-holding.outbox.poll-delay-ms}")
    fun publishPending() {
        outboxRepository.moveToProcessing()
            .flatMap { event ->
                producer.publish(event)
                    .then(Mono.defer { outboxRepository.ack(event) })
                    .onErrorResume { outboxRepository.requeue(event) }
                    .thenReturn(Unit)
            }
            .subscribe()
    }
}
