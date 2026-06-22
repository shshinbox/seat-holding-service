package me.songha.concert.seat.api.auth

import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class AuthenticatedUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        AuthenticatedUser::class.java == parameter.parameterType

    override fun resolveArgument(
        parameter: MethodParameter,
        bindingContext: BindingContext,
        exchange: ServerWebExchange,
    ): Mono<Any> {
        val userId = exchange.request.headers.getFirst(AUTHENTICATED_USER_ID_HEADER)
        if (userId.isNullOrBlank()) {
            return Mono.error(AuthenticationRequiredException("$AUTHENTICATED_USER_ID_HEADER header is required."))
        }

        return Mono.just(AuthenticatedUser(userId))
    }

    companion object {
        const val AUTHENTICATED_USER_ID_HEADER = "X-Authenticated-User-Id"
    }
}
