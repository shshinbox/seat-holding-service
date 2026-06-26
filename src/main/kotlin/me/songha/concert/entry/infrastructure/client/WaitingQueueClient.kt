package me.songha.concert.entry.infrastructure.client

import me.songha.concert.seat.api.auth.AuthenticatedUserArgumentResolver.Companion.AUTHENTICATED_USER_ID_HEADER
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class WaitingQueueClient(
    private val waitingQueueWebClient: WebClient,
) {
    suspend fun admit(scheduleId: String, userId: String): String =
        waitingQueueWebClient.post()
            .uri { builder ->
                builder.path("/internal/v1/queue/admit")
                    .queryParam("scheduleId", scheduleId)
                    .build()
            }
            .header(AUTHENTICATED_USER_ID_HEADER, userId)
            .retrieve()
            .awaitBody()
}
