package me.songha.concert.seat.infrastructure.redis

import me.songha.concert.seat.application.SeatHoldResult
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEvent
import me.songha.concert.seat.infrastructure.kafka.SeatHoldEventType
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

@Repository
class SeatHoldRedisRepository(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${seat-holding.outbox.pending-key}") private val outboxPendingKey: String,
) {
    private val holdScript = RedisScript.of(
        """
        if redis.call('EXISTS', KEYS[1]) == 1 then
          return 0
        end
        redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
        redis.call('LPUSH', KEYS[2], ARGV[3])
        return 1
        """.trimIndent(),
        Long::class.java,
    )
    private val releaseScript = RedisScript.of(
        """
        local current = redis.call('GET', KEYS[1])
        if not current then
          return 0
        end
        local hold = cjson.decode(current)
        if hold.holdId ~= ARGV[1] or hold.userId ~= ARGV[2] then
          return 0
        end
        redis.call('DEL', KEYS[1])
        redis.call('LPUSH', KEYS[2], ARGV[3])
        return 1
        """.trimIndent(),
        Long::class.java,
    )

    fun holdIfAbsent(result: SeatHoldResult, ttl: Duration): Mono<Boolean> {
        val holdJson = objectMapper.writeValueAsString(result)
        val eventJson = objectMapper.writeValueAsString(result.toEvent())

        return redisTemplate.execute(
            holdScript,
            listOf(holdKey(result.venueId, result.seatId), outboxPendingKey),
            listOf(holdJson, ttl.seconds.toString(), eventJson),
        ).next().map { it == 1L }
    }

    fun releaseIfOwner(
        venueId: String,
        seatId: String,
        holdId: String,
        userId: String,
    ): Mono<Boolean> {
        val eventJson = objectMapper.writeValueAsString(
            SeatHoldEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = SeatHoldEventType.SEAT_HOLD_RELEASED,
                holdId = holdId,
                venueId = venueId,
                seatId = seatId,
                userId = userId,
                expiresAt = null,
                occurredAt = java.time.Instant.now(),
            ),
        )

        return redisTemplate.execute(
            releaseScript,
            listOf(holdKey(venueId, seatId), outboxPendingKey),
            listOf(holdId, userId, eventJson),
        ).next().map { it == 1L }
    }

    private fun holdKey(venueId: String, seatId: String): String =
        "hold:venue:$venueId:seat:$seatId"

    private fun SeatHoldResult.toEvent(): SeatHoldEvent =
        SeatHoldEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = SeatHoldEventType.SEAT_HELD,
            holdId = holdId,
            venueId = venueId,
            seatId = seatId,
            userId = userId,
            expiresAt = expiresAt,
            occurredAt = occurredAt,
        )
}
