package dev.yona.security

import dev.yona.domain.apitoken.ApiScopes
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * 토큰의 범위를 스프링 시큐리티의 표준 권한으로 바꾼다(로드맵 X-49 수준 1, `decisions.md` D-130). 접두사는 스프링의 OAuth 자원 서버가 범위를 권한으로 바꿀 때 쓰는
 * 관례와 같은 `SCOPE_`이다(`SCOPE_repo`, `SCOPE_read:user`).
 *
 * 범위의 포함 관계(`repo`가 `read:repo`를 포함하는 것 등)를 **펼친 값**을 권한으로 가진다([ApiScopes.expand]). 그래서 `hasAuthority("SCOPE_read:repo")`가
 * `ApiScopes.implies(scopes, "read:repo")`와 같은 답을 낸다. 세션과 로그인 유지 쿠키 인증은 범위 검사를 받지 않으므로(`scopes`가 `null`) 권한이 없다.
 */
object ScopeAuthorities {

    /** 권한 이름의 접두사이다. */
    const val PREFIX = "SCOPE_"

    /** [scopes]를 펼친 `SCOPE_` 권한 목록이다. `null`이거나 비었으면 빈 목록이다. */
    fun of(scopes: Set<String>?): List<GrantedAuthority> =
        scopes?.let { ApiScopes.expand(it).map { scope -> SimpleGrantedAuthority(PREFIX + scope) } }.orEmpty()
}
