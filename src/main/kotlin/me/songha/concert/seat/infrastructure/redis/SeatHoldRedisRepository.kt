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
    private val toggleScript = RedisScript.of(
        """
        if redis.call('EXISTS', KEYS[1]) == 1 then
          return 'SOLD'
        end
        
        local current = redis.call('GET', KEYS[2])
        if current then
          local hold = cjson.decode(current)
          if hold.userId == ARGV[2] then
            redis.call('DEL', KEYS[2])
            redis.call('SREM', KEYS[3], ARGV[3])
            return 'HELD_RELEASED'
          end
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
    private val activeHoldsScript = RedisScript.of(
        """
        local heldSeatIds = redis.call('SMEMBERS', KEYS[1])
        local activeHolds = {}
        
        for _, heldSeatId in ipairs(heldSeatIds) do
          local activeHoldKey = 'seat:hold:{' .. ARGV[1] .. '}:' .. heldSeatId
          local current = redis.call('GET', activeHoldKey)
          if current then
            local hold = cjson.decode(current)
            if hold.userId == ARGV[2] then
              table.insert(activeHolds, hold)
            else
              redis.call('SREM', KEYS[1], heldSeatId)
            end
          else
            redis.call('SREM', KEYS[1], heldSeatId)
          end
        end
        
        return cjson.encode(activeHolds)
        """.trimIndent(),
        String::class.java,
    )
    suspend fun toggle(result: SeatHoldResult, ttl: Duration): SeatHoldRedisToggleResult {
        val holdJson = objectMapper.writeValueAsString(result)

        return redisTemplate.execute(
            toggleScript,
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
        ).next().map {
            when (it) {
                "HELD" -> SeatHoldRedisToggleResult.HELD_BY_ANOTHER_USER
                else -> SeatHoldRedisToggleResult.valueOf(it)
            }
        }.awaitSingle()
    }

    suspend fun findActiveHolds(scheduleId: String, userId: String): List<SeatHoldResult> {
        val json = redisTemplate.execute(
            activeHoldsScript,
            listOf(userHoldsKey(scheduleId, userId)),
            listOf(scheduleId, userId),
        ).next().awaitSingle()

        return objectMapper.readValue(json, Array<SeatHoldResult>::class.java).toList()
    }

    private fun soldKey(scheduleId: String, seatId: String): String =
        "seat:sold:{$scheduleId}:$seatId"

    private fun holdKey(scheduleId: String, seatId: String): String =
        "seat:hold:{$scheduleId}:$seatId"

    private fun userHoldsKey(scheduleId: String, userId: String): String =
        "user:holds:{$scheduleId}:$userId"
}
