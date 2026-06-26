package me.songha.concert.entry.application

import me.songha.concert.entry.infrastructure.client.WaitingQueueClient
import org.springframework.stereotype.Service

@Service
class EntryService(
    private val waitingQueueClient: WaitingQueueClient,

    ) {
    suspend fun enter(scheduleId: String, userId: String): String =
        waitingQueueClient.admit(scheduleId, userId)
}
