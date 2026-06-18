package me.songha.concert.seat.infrastructure.kafka

import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Component
class SeatHoldEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, SeatHoldEvent>,
    @Value("\${seat-holding.kafka.topic}") private val topic: String,
) {
    suspend fun publish(event: SeatHoldEvent): Unit =
        suspendCancellableCoroutine { continuation ->
            val future = kafkaTemplate.send(topic, event.holdId, event)
            future.whenComplete { _, error ->
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation {
                future.cancel(true)
            }
        }
}
