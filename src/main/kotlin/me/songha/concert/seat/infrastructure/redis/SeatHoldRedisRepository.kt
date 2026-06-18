package me.songha.concert.seat.infrastructure.redis

import me.songha.concert.seat.application.SeatHoldResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Repository
class SeatHoldRedisRepository(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${seat-holding.max-holds-per-user}") private val maxHoldsPerUser: Int,
) {
    private val holdScript = RedisScript.of(
        """
        if redis.call('EXISTS', KEYS[1]) == 1 then
          return 'SOLD'
        end
        
        if redis.call('EXISTS', KEYS[2]) == 1 then
          return 'HELD'
        end
        
        local heldSeatIds = redis.call('SMEMBERS', KEYS[3])
        local activeHoldCount = 0
        for _, heldSeatId in ipairs(heldSeatIds) do
          local activeHoldKey = 'seat:hold:{' .. ARGV[1] .. '}:' .. heldSeatId
          if redis.call('EXISTS', activeHoldKey) == 1 then
            activeHoldCount = activeHoldCount + 1
          else
            redis.call('SREM', KEYS[3], heldSeatId)
          end
        end
        
        if activeHoldCount >= tonumber(ARGV[5]) then
          return 'LIMIT_EXCEEDED'
        end
        
        redis.call('SET', KEYS[2], ARGV[4], 'EX', ARGV[6])
        redis.call('SADD', KEYS[3], ARGV[3])
        redis.call('EXPIRE', KEYS[3], ARGV[6])
        return 'HELD_CREATED'
        """.trimIndent(),
        String::class.java,
    )
    private val releaseScript = RedisScript.of(
        """
        local current = redis.call('GET', KEYS[1])
        if not current then
          redis.call('SREM', KEYS[2], ARGV[2])
          return 'NOOP'
        end
        
        local hold = cjson.decode(current)
        if hold.userId ~= ARGV[1] then
          return 'DENIED'
        end
        
        redis.call('DEL', KEYS[1])
        redis.call('SREM', KEYS[2], ARGV[2])
        return 'RELEASED:' .. hold.holdId
        """.trimIndent(),
        String::class.java,
    )

    suspend fun hold(result: SeatHoldResult, ttl: Duration): SeatHoldRedisHoldResult {
        val holdJson = objectMapper.writeValueAsString(result)

        return redisTemplate.execute(
            holdScript,
            listOf(
                soldKey(result.scheduleId, result.seatId),
                holdKey(result.scheduleId, result.seatId),
                userHoldsKey(result.scheduleId, result.userId),
            ),
            listOf(
                result.scheduleId,
                result.userId,
                result.seatId,
                holdJson,
                maxHoldsPerUser.toString(),
                ttl.seconds.toString(),
            ),
        ).next().map { SeatHoldRedisHoldResult.valueOf(it) }.awaitSingle()
    }

    suspend fun release(
        scheduleId: String,
        seatId: String,
        userId: String,
    ): SeatHoldRedisReleaseResult =
        redisTemplate.execute(
            releaseScript,
            listOf(holdKey(scheduleId, seatId), userHoldsKey(scheduleId, userId)),
            listOf(userId, seatId),
        ).next().map { SeatHoldRedisReleaseResult.fromScriptResult(it) }.awaitSingle()

    private fun soldKey(scheduleId: String, seatId: String): String =
        "seat:sold:{$scheduleId}:$seatId"

    private fun holdKey(scheduleId: String, seatId: String): String =
        "seat:hold:{$scheduleId}:$seatId"

    private fun userHoldsKey(scheduleId: String, userId: String): String =
        "user:holds:{$scheduleId}:$userId"
}
