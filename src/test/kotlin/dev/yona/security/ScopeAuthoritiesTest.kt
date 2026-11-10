package dev.yona.security

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 토큰의 범위를 스프링 시큐리티의 표준 권한(`SCOPE_` 접두사의 `GrantedAuthority`)으로 바꾸는 [ScopeAuthorities]를 검증한다(로드맵 X-49 수준 1, `decisions.md` D-130).
 * 범위의 포함 관계(`repo`가 `read:repo`를 포함하는 것 등)를 펼친 값을 권한으로 가지므로, **`ApiScopes.implies`와 같은 답을 모든 조합에서 내야 한다.**
 * 이 단계는 동작을 바꾸지 않는다(판정은 아직 `ApiScopeInterceptor`가 한다).
 */
class ScopeAuthoritiesTest {

    private val all = ApiScopes.selectable(siteManager = true)
    private val owner = User(id = 7, loginId = "sa", name = "sa", email = "sa@example.com", state = UserState.ACTIVE)

    private fun names(scopes: Set<String>?) = ScopeAuthorities.of(scopes).map { it.authority }.toSet()

    @Test
    fun `범위가 없거나 비어 있으면 권한이 없다`() {
        assertEquals(emptySet(), names(null))
        assertEquals(emptySet(), names(emptySet()))
    }

    @Test
    fun `repo는 포함하는 범위까지 펼쳐 SCOPE 접두사의 권한이 된다`() {
        assertEquals(
            setOf("SCOPE_repo", "SCOPE_public_repo", "SCOPE_read:repo", "SCOPE_admin:repo_hook", "SCOPE_write:repo_hook", "SCOPE_read:repo_hook"),
            names(setOf("repo"))
        )
    }

    @Test
    fun `포함하지 않는 범위는 자기 자신만 권한이 된다`() {
        assertEquals(setOf("SCOPE_read:user"), names(setOf("read:user")))
        assertEquals(setOf("SCOPE_site_admin"), names(setOf("site_admin")))
    }

    @Test
    fun `모든 범위 하나씩과 두 개씩의 조합에서 implies와 권한이 같은 답을 낸다`() {
        val combinations = all.map { setOf(it) } + all.flatMap { a -> all.map { b -> setOf(a, b) } }
        var checked = 0
        for (granted in combinations) {
            val authorities = names(granted)
            for (required in all) {
                assertEquals(ApiScopes.implies(granted, required), "SCOPE_$required" in authorities, "granted=$granted required=$required")
                checked++
            }
        }
        assertTrue(checked > 1000, "조합을 충분히 확인했다: $checked")
    }

    @Test
    fun `모르는 범위는 권한으로 만들지 않는다`() {
        assertEquals(setOf("SCOPE_read:user"), names(setOf("read:user", "not_a_scope")))
    }

    @Test
    fun `토큰으로 인증한 YonaAuthentication은 범위의 권한을 갖고 세션과 쿠키 인증은 갖지 않는다`() {
        val byToken = YonaAuthentication.of(owner, viaToken = true, scopes = setOf("repo"))
        assertEquals(names(setOf("repo")), byToken.authorities.map { it.authority }.toSet())
        assertEquals(emptySet(), YonaAuthentication.of(owner).authorities.map { it.authority }.toSet(), "세션 인증은 범위 검사를 받지 않아 권한이 없다")
        assertEquals(emptySet(), YonaAuthentication.of(owner, viaCookie = true).authorities.map { it.authority }.toSet())
    }

    @Test
    fun `범위 제한 없는 옛 토큰의 범위 전체는 모든 범위의 권한이 된다`() {
        assertEquals(all.map { "SCOPE_$it" }.toSet(), names(all.toSet()))
    }
}
