package me.songha.concert.entry.infrastructure.token

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

@Component
class QueuePassTokenVerifier(
    @Value("\${waiting-queue.token.secret}") secret: String,
) {
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    fun verify(token: String): QueuePassClaims {
        val claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload

        val userId = claims.subject
            ?: throw IllegalArgumentException("subject claim is missing")
        val scheduleIdClaim = claims["scheduleId"]
            ?: throw IllegalArgumentException("scheduleId claim is missing")

        return QueuePassClaims(
            userId = userId,
            scheduleId = scheduleIdClaim.toString(),
            type = claims["type", String::class.java],
        )
    }
}
