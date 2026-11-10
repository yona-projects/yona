package dev.yona.support

import dev.yona.domain.apitoken.ApiToken
import dev.yona.domain.apitoken.ApiTokenRepository
import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.user.User
import java.util.Date

/**
 * 시험에서 사용자에게 개인용 API 토큰을 심는 도우미이다(X-5, D-36). 옛 사용자 토큰(`n4user.token`)을 쓰던 시험이 같은 값으로 새 구조(해시, 범위)에서 동작하게 한다.
 * 범위를 주지 않으면 이관한 옛 토큰과 같은 형태(범위 행 없음, 범위 제한 없음)로 만들고, 범위를 주면 그 범위만 가진 일반 토큰으로 만든다.
 */
object ApiTokenSupport {

    /** [user]에게 [value]를 값으로 하는 토큰을 만든다. [scopes]를 주지 않으면 범위 제한 없는 옛 토큰 형태이다. */
    fun grant(tokens: ApiTokenRepository, user: User, value: String, scopes: Collection<String>? = null): ApiToken =
        tokens.save(
            ApiToken(
                user = user, name = "시험 토큰", tokenHash = TokenHasher.sha256Hex(value), tokenPrefix = value.take(4),
                scopes = scopes?.toMutableSet() ?: mutableSetOf(), isLegacy = scopes == null, createdDate = Date()
            )
        )
}
