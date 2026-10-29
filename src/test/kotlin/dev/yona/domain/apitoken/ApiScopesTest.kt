package dev.yona.domain.apitoken

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Classic 토큰의 범위 모델을 검증한다(로드맵 X-5, `decisions.md` D-36, `architecture/api-token.md` 1-1절).
 * 범위 이름과 묶음 규칙은 GitHub 공식 문서의 Classic 범위를 따른다.
 */
class ApiScopesTest {

    @Test
    fun `쉼표로 이은 범위를 읽고 공백과 중복을 정리한다`() {
        assertEquals(setOf("repo", "read:org"), ApiScopes.parse(" repo , read:org,repo "))
        assertEquals(emptySet(), ApiScopes.parse(null))
        assertEquals(emptySet(), ApiScopes.parse(""))
    }

    @Test
    fun `모르는 범위나 예약 범위는 받지 않는다`() {
        assertFailsWith<IllegalArgumentException> { ApiScopes.parse("repo,nothing") }
        assertFailsWith<IllegalArgumentException> { ApiScopes.parse("workflow") }
        assertFailsWith<IllegalArgumentException> { ApiScopes.parse("*") }
    }

    @Test
    fun `user는 read user와 user email을 포함한다`() {
        val granted = setOf("user")
        assertTrue(ApiScopes.implies(granted, "read:user") && ApiScopes.implies(granted, "user:email") && ApiScopes.implies(granted, "user"))
        assertFalse(ApiScopes.implies(setOf("read:user"), "user"))
        assertFalse(ApiScopes.implies(setOf("user:email"), "read:user"))
    }

    @Test
    fun `admin org는 write org와 read org를 포함하고 웹훅 범위도 같은 단계이다`() {
        assertTrue(ApiScopes.implies(setOf("admin:org"), "read:org") && ApiScopes.implies(setOf("admin:org"), "write:org"))
        assertFalse(ApiScopes.implies(setOf("read:org"), "write:org"))
        assertTrue(ApiScopes.implies(setOf("admin:repo_hook"), "write:repo_hook") && ApiScopes.implies(setOf("write:repo_hook"), "read:repo_hook"))
        assertFalse(ApiScopes.implies(setOf("read:repo_hook"), "write:repo_hook"))
    }

    @Test
    fun `repo는 public repo와 프로젝트 웹훅 범위를 포함하지만 public repo는 repo를 포함하지 않는다`() {
        assertTrue(ApiScopes.implies(setOf("repo"), "public_repo") && ApiScopes.implies(setOf("repo"), "admin:repo_hook"))
        assertFalse(ApiScopes.implies(setOf("public_repo"), "repo"))
        assertFalse(ApiScopes.implies(setOf("public_repo"), "write:repo_hook"))
    }

    @Test
    fun `site admin과 조직과 사용자 범위는 서로 포함하지 않는다`() {
        assertFalse(ApiScopes.implies(setOf("repo", "user", "admin:org"), "site_admin"))
        assertFalse(ApiScopes.implies(setOf("admin:org"), "repo"))
    }

    @Test
    fun `사이트 관리자만 site admin을 고를 수 있다`() {
        assertFalse("site_admin" in ApiScopes.selectable(siteManager = false))
        assertTrue("site_admin" in ApiScopes.selectable(siteManager = true))
        assertTrue(ApiScopes.selectable(false).containsAll(listOf("repo", "public_repo", "admin:repo_hook", "write:repo_hook", "read:repo_hook", "admin:org", "write:org", "read:org", "user", "read:user", "user:email")))
    }

    @Test
    fun `범위를 문자열로 저장하면 같은 집합으로 되돌아온다`() {
        val scopes = setOf("repo", "user", "read:org")
        assertEquals(scopes, ApiScopes.parse(ApiScopes.format(scopes)))
        assertEquals("", ApiScopes.format(emptySet()))
    }
}
