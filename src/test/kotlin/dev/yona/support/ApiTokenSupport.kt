package dev.yona.support

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.ApiToken
import dev.yona.domain.apitoken.ApiTokenRepository
import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.user.User
import java.util.Date

/**
 * 시험에서 사용자에게 개인용 API 토큰을 심는 도우미이다(X-5, D-36). 옛 사용자 토큰(`n4user.token`)을 쓰던 시험이 같은 값으로 새 구조(해시, 범위)에서 동작하게 한다.
 * 이관한 옛 토큰과 같은 형태로 모든 범위를 준다.
 */
object ApiTokenSupport {

    /** [user]에게 [value]를 값으로 하는 토큰을 모든 범위(`site_admin` 포함)로 만든다. */
    fun grant(tokens: ApiTokenRepository, user: User, value: String, scopes: Collection<String> = ApiScopes.selectable(siteManager = true)): ApiToken =
        tokens.save(
            ApiToken(
                user = user, name = "시험 토큰", tokenHash = TokenHasher.sha256Hex(value), tokenPrefix = value.take(4), scopes = ApiScopes.format(scopes),
                isLegacy = true, createdDate = Date()
            )
        )
}
