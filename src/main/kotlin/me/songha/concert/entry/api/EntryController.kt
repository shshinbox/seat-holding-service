package me.songha.concert.entry.api

import me.songha.concert.entry.application.EntryService
import me.songha.concert.seat.api.auth.AuthenticatedUser
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/holding/schedule/{scheduleId}")
class EntryController(
    private val entryService: EntryService,
) {
    @PostMapping("/entry")
    suspend fun entry(
        @PathVariable scheduleId: String,
        authenticatedUser: AuthenticatedUser,
    ): String = entryService.enter(scheduleId, authenticatedUser.id)
}
