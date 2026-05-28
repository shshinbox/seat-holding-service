package me.songha.concert.seat.infrastructure.kafka

import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SeatHoldEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, SeatHoldEvent>,
    @Value("\${seat-holding.kafka.topic}") private val topic: String,
) {
    fun publish(event: SeatHoldEvent): Mono<Unit> =
        Mono.fromFuture(kafkaTemplate.send(topic, event.holdId, event))
            .thenReturn(Unit)
}
