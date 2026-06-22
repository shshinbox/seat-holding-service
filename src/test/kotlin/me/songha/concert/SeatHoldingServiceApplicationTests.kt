package me.songha.concert

import me.songha.concert.seat.infrastructure.kafka.SeatHoldEventProducer
import me.songha.concert.seat.infrastructure.redis.SeatHoldRedisRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class SeatHoldingServiceApplicationTests {
    @MockitoBean
    lateinit var seatHoldRedisRepository: SeatHoldRedisRepository

    @MockitoBean
    lateinit var seatHoldEventProducer: SeatHoldEventProducer

    @Test
    fun contextLoads() {
    }

}
