package me.songha.concert.entry.infrastructure.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WaitingQueueClientConfig {
    @Bean
    fun waitingQueueWebClient(
        @Value("\${waiting-queue.base-url}") baseUrl: String,
    ): WebClient = WebClient.builder().baseUrl(baseUrl).build()
}
