package me.songha.concert

import me.songha.concert.seat.infrastructure.kafka.SeatHoldEventProducer
import me.songha.concert.seat.infrastructure.redis.SeatHoldOutboxRepository
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisRepository
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonReactiveClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(
    properties = [
        "seat-holding.redisson.enabled=false",
    ],
)
class SeatHoldingServiceApplicationTests {
    @MockitoBean
    lateinit var redissonReactiveClient: RedissonReactiveClient

    @MockitoBean
    lateinit var seatHoldRedisRepository: SeatHoldRedisRepository

    @MockitoBean
    lateinit var seatHoldOutboxRepository: SeatHoldOutboxRepository

    @MockitoBean
    lateinit var seatHoldEventProducer: SeatHoldEventProducer

    @Test
    fun contextLoads() {
    }

}
