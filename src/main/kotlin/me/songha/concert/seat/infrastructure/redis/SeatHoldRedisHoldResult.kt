package me.songha.concert.seat.infrastructure.redis

enum class SeatHoldRedisHoldResult {
    HELD_CREATED,
    SOLD,
    HELD,
    LIMIT_EXCEEDED,
}
