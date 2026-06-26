package me.songha.concert.entry.api

import io.jsonwebtoken.JwtException
import me.songha.concert.entry.infrastructure.token.QueuePassTokenVerifier
import me.songha.concert.seat.api.auth.AuthenticatedUserArgumentResolver
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.pattern.PathPatternParser
import reactor.core.publisher.Mono

@Component
class QueuePassTokenFilter(
    private val tokenVerifier: QueuePassTokenVerifier,
) : WebFilter, Ordered {

    private val protectedPatterns = listOf(
        PathPatternParser.defaultInstance.parse("/api/v1/holding/schedule/{scheduleId}/seats/{seatId}/holds"),
        PathPatternParser.defaultInstance.parse("/api/v1/holding/schedule/{scheduleId}/holds/confirm"),
    )

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val scheduleId = extractScheduleId(exchange)
            ?: return chain.filter(exchange)

        val userId = extractAuthenticatedUserId(exchange)
            ?: return unauthorized(exchange, "Authenticated user is required.")

        val token = extractBearerToken(exchange)
            ?: return unauthorized(exchange, "Authorization Bearer token is required.")

        return try {
            validateToken(
                token = token,
                userId = userId,
                scheduleId = scheduleId,
            )

            chain.filter(exchange)
        } catch (_: JwtException) {
            unauthorized(exchange, "Queue pass token verification failed.")
        } catch (_: IllegalArgumentException) {
            unauthorized(exchange, "Queue pass token verification failed.")
        }
    }

    private fun extractScheduleId(exchange: ServerWebExchange): String? {
        val requestPath = exchange.request.path.pathWithinApplication()

        return protectedPatterns
            .firstOrNull { it.matches(requestPath) }
            ?.matchAndExtract(requestPath)
            ?.uriVariables
            ?.get("scheduleId")
    }

    private fun extractAuthenticatedUserId(exchange: ServerWebExchange): String? {
        return exchange.request.headers
            .getFirst(AuthenticatedUserArgumentResolver.AUTHENTICATED_USER_ID_HEADER)
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractBearerToken(exchange: ServerWebExchange): String? {
        return exchange.request.headers
            .getFirst(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun validateToken(
        token: String,
        userId: String,
        scheduleId: String,
    ) {
        val claims = tokenVerifier.verify(token)

        require(claims.type == QUEUE_PASS_TYPE) {
            "Invalid queue pass token type."
        }

        require(claims.userId == userId) {
            "Queue pass token user does not match authenticated user."
        }

        require(claims.scheduleId == scheduleId) {
            "Queue pass token scheduleId does not match."
        }
    }

    private fun unauthorized(exchange: ServerWebExchange, message: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON

        val body = """{"message":"$message"}"""
            .toByteArray(Charsets.UTF_8)

        return response.writeWith(
            Mono.just(response.bufferFactory().wrap(body))
        )
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val QUEUE_PASS_TYPE = "QUEUE_PASS"
    }
}