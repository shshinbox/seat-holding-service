package me.songha.concert.seat.infrastructure.redis

sealed class SeatHoldRedisReleaseResult {
    data class Released(val holdId: String) : SeatHoldRedisReleaseResult()
    data object Noop : SeatHoldRedisReleaseResult()
    data object Denied : SeatHoldRedisReleaseResult()

    companion object {
        fun fromScriptResult(result: String): SeatHoldRedisReleaseResult =
            when {
                result.startsWith("RELEASED:") -> Released(result.removePrefix("RELEASED:"))
                result == "NOOP" -> Noop
                result == "DENIED" -> Denied
                else -> error("Unknown release script result: $result")
            }
    }
}
