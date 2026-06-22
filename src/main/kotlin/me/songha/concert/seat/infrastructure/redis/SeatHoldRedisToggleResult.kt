package me.songha.concert.seat.infrastructure.redis

enum class SeatHoldRedisToggleResult {
    HELD_CREATED,
    HELD_RELEASED,
    SOLD,
    HELD_BY_ANOTHER_USER,
    LIMIT_EXCEEDED,
}
