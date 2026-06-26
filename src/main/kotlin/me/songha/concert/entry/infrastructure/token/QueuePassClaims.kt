package me.songha.concert.entry.infrastructure.token

data class QueuePassClaims(
    val userId: String,
    val scheduleId: String,
    val type: String?,
)
