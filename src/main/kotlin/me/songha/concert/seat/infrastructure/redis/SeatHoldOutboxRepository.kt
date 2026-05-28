package me.songha.concert.seat.infrastructure.redis

import me.songha.concert.seat.infrastructure.kafka.SeatHoldEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Repository
class SeatHoldOutboxRepository(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${seat-holding.outbox.pending-key}") private val outboxPendingKey: String,
    @Value("\${seat-holding.outbox.processing-key}") private val outboxProcessingKey: String,
) {
    fun moveToProcessing(): Mono<SeatHoldEvent> =
        redisTemplate.opsForList()
            .rightPopAndLeftPush(outboxPendingKey, outboxProcessingKey)
            .map { objectMapper.readValue(it, SeatHoldEvent::class.java) }

    fun ack(event: SeatHoldEvent): Mono<Long> =
        redisTemplate.opsForList()
            .remove(outboxProcessingKey, 1, objectMapper.writeValueAsString(event))

    fun requeue(event: SeatHoldEvent): Mono<Long> =
        redisTemplate.opsForList()
            .remove(outboxProcessingKey, 1, objectMapper.writeValueAsString(event))
            .then(redisTemplate.opsForList().rightPush(outboxPendingKey, objectMapper.writeValueAsString(event)))
}
