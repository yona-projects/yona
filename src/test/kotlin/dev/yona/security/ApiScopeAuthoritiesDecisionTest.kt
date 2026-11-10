package dev.yona.security

import dev.yona.domain.apitoken.ApiScopes
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 범위 판정을 `SCOPE_` 표준 권한 기준으로 하는 [ApiScopeRules.allowsAuthorities]가 기존 [ApiScopeRules.allows]와
 * **모든 요건, 범위 조합, 공개 여부에서 같은 답**을 내는지 검증한다(로드맵 X-49 수준 2, `decisions.md` D-130).
 */
class ApiScopeAuthoritiesDecisionTest {

    private val all = ApiScopes.selectable(siteManager = true)
    private val requirements: List<ApiRequirement> = listOf(
        ApiRequirement.None, ApiRequirement.Repo, ApiRequirement.RepoOnly, ApiRequirement.AnyRepo,
        ApiRequirement.RepoRead, ApiRequirement.RepoOnlyRead, ApiRequirement.AnyRepoRead
    ) + all.map { ApiRequirement.Scope(it) }

    /** 포함 관계를 `ApiScopes.implies`로 직접 따르는 기준 판정이다(권한을 쓰기 전의 구현과 같다). */
    private fun oracle(requirement: ApiRequirement, scopes: Set<String>, projectPublic: Boolean?): Boolean = when (requirement) {
        ApiRequirement.None -> true
        ApiRequirement.RepoOnly -> ApiScopes.implies(scopes, ApiScopes.REPO)
        ApiRequirement.AnyRepo -> ApiScopes.implies(scopes, ApiScopes.REPO) || ApiScopes.implies(scopes, ApiScopes.PUBLIC_REPO)
        ApiRequirement.RepoOnlyRead -> ApiScopes.implies(scopes, ApiScopes.READ_REPO)
        ApiRequirement.AnyRepoRead -> ApiScopes.implies(scopes, ApiScopes.READ_REPO) || ApiScopes.implies(scopes, ApiScopes.PUBLIC_REPO)
        ApiRequirement.RepoRead -> ApiScopes.implies(scopes, ApiScopes.READ_REPO) || (ApiScopes.implies(scopes, ApiScopes.PUBLIC_REPO) && projectPublic == true)
        ApiRequirement.Repo -> ApiScopes.implies(scopes, ApiScopes.REPO) || (ApiScopes.implies(scopes, ApiScopes.PUBLIC_REPO) && projectPublic == true)
        is ApiRequirement.Scope -> ApiScopes.implies(scopes, requirement.name)
    }

    @Test
    fun `권한 기준 판정은 기존 판정과 모든 조합에서 같다`() {
        val combos = listOf(emptySet<String>()) + all.map { setOf(it) } + all.flatMap { a -> all.map { b -> setOf(a, b) } }
        var checked = 0
        for (granted in combos) {
            val authorities = ScopeAuthorities.of(granted).mapNotNull { it.authority }.toSet()
            for (req in requirements) for (public in listOf(true, false, null)) {
                assertEquals(oracle(req, granted, public), ApiScopeRules.allowsAuthorities(req, authorities, public), "granted=$granted req=$req public=$public")
                checked++
            }
        }
        assertTrue(checked > 10_000, "충분히 확인했다: $checked")
    }
}
