package dev.yona.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 경로별 필요한 범위 대응표와 범위 판정을 검증한다(X-5, `architecture/api-auth-split.md`). */
class ApiScopeRulesTest {

    private fun allows(requirement: ApiRequirement, scopes: Set<String>, projectPublic: Boolean? = null) = ApiScopeRules.allows(requirement, scopes, projectPublic)

    @Test
    fun `repo 요건은 repo면 항상 되고 public repo면 공개 프로젝트만 된다`() {
        assertTrue(allows(ApiRequirement.Repo, setOf("repo"), projectPublic = false))
        assertTrue(allows(ApiRequirement.Repo, setOf("public_repo"), projectPublic = true))
        assertFalse(allows(ApiRequirement.Repo, setOf("public_repo"), projectPublic = false))
        assertFalse(allows(ApiRequirement.Repo, setOf("public_repo"), projectPublic = null), "프로젝트를 모르면 막는다")
        assertFalse(allows(ApiRequirement.Repo, setOf("user", "read:org"), projectPublic = true))
        assertTrue(allows(ApiRequirement.Repo, setOf("repo"), projectPublic = null), "repo는 프로젝트를 몰라도 된다")
    }

    @Test
    fun `repo 전용 요건은 public repo로 되지 않는다`() {
        assertTrue(allows(ApiRequirement.RepoOnly, setOf("repo")))
        assertFalse(allows(ApiRequirement.RepoOnly, setOf("public_repo"), projectPublic = true))
    }

    @Test
    fun `어느 repo 범위든 되는 요건은 public repo도 받는다`() {
        assertTrue(allows(ApiRequirement.AnyRepo, setOf("public_repo")) && allows(ApiRequirement.AnyRepo, setOf("repo")))
        assertFalse(allows(ApiRequirement.AnyRepo, setOf("user")))
    }

    @Test
    fun `이름 있는 범위는 포함 관계를 따른다`() {
        assertTrue(allows(ApiRequirement.Scope("read:user"), setOf("user")))
        assertFalse(allows(ApiRequirement.Scope("read:user"), setOf("user:email")))
        assertTrue(allows(ApiRequirement.Scope("site_admin"), setOf("site_admin")))
        assertFalse(allows(ApiRequirement.Scope("site_admin"), setOf("repo", "user", "admin:org")))
        assertTrue(allows(ApiRequirement.None, emptySet()))
    }

    @Test
    fun `대응표는 지금 있는 외부 API 경로를 모두 담는다`() {
        fun req(method: String, pattern: String) = ApiScopeRules.requirement(method, pattern)
        val base = "/-_-api/v1/owners/{owner}/projects/{projectName}"
        assertEquals(ApiRequirement.RepoRead, req("GET", "$base/issues/{number}"))
        assertEquals(ApiRequirement.Repo, req("PUT", "$base/issues/{number}"))
        assertEquals(ApiRequirement.Repo, req("PATCH", "$base/issues/{number}"))
        assertEquals(ApiRequirement.Repo, req("POST", "$base/issues"))
        assertEquals(ApiRequirement.Repo, req("POST", "$base/issues/{number}/comments"))
        assertEquals(ApiRequirement.Repo, req("POST", "$base/issuelabel/{number}"))
        assertEquals(ApiRequirement.Repo, req("POST", "$base/issues/imports"))
        assertEquals(ApiRequirement.Repo, req("POST", "$base/posts"))
        assertEquals(ApiRequirement.Repo, req("POST", "$base/posts/{number}/comments"))
        assertEquals(ApiRequirement.Repo, req("POST", "$base/labels"))
        assertEquals(ApiRequirement.Repo, req("POST", "$base/milestones"))
        assertEquals(ApiRequirement.RepoRead, req("GET", "$base/exports"))
        assertEquals(ApiRequirement.RepoRead, req("GET", "$base/posts/{number:[0-9]+}/watchers"))
        assertEquals(ApiRequirement.RepoOnly, req("POST", "/-_-api/v1/owners/{owner}/projects"))
        assertEquals(ApiRequirement.RepoOnlyRead, req("GET", "/-_-api/v1/user/issues"))
        assertEquals(ApiRequirement.Scope("read:user"), req("GET", "/-_-api/v1/users/{user}/statistics"))
        assertEquals(ApiRequirement.Scope("site_admin"), req("POST", "/-_-api/v1/users"))
        assertEquals(ApiRequirement.Scope("site_admin"), req("GET", "/-_-api/v1/admin/users"))
        assertEquals(ApiRequirement.Scope("site_admin"), req("PATCH", "/-_-api/v1/admin/users/{user}"))
        assertEquals(ApiRequirement.AnyRepo, req("POST", "/-_-api/v1/files"))
        assertEquals(ApiRequirement.None, req("GET", "/-_-api/v1/hello"))
        assertEquals(ApiRequirement.None, req("GET", "/-_-api/v1/"))
    }

    @Test
    fun `대응표에 없는 경로와 메서드는 없는 것으로 본다`() {
        assertNull(ApiScopeRules.requirement("PUT", "/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/labels"))
        assertNull(ApiScopeRules.requirement("GET", "/-_-api/v1/nothing"))
        assertNotNull(ApiScopeRules.requirement("GET", "/-_-api/v1/hello"))
    }

    @Test
    fun `읽기 전용 범위는 읽기 요청만 통과시키고 repo는 read repo를 포함한다`() {
        assertTrue(dev.yona.domain.apitoken.ApiScopes.implies(setOf("repo"), "read:repo"))
        assertFalse(dev.yona.domain.apitoken.ApiScopes.implies(setOf("read:repo"), "repo"))
        assertFalse(dev.yona.domain.apitoken.ApiScopes.implies(setOf("read:repo"), "public_repo"))
        val base = "/-_-api/v1/owners/{owner}/projects/{projectName}"
        val read = setOf("read:repo")
        // 읽기(GET)는 read:repo면 되고 프로젝트가 공개인지는 묻지 않는다
        for (pattern in listOf("$base/issues/{number}", "$base/pullrequests", "$base/wiki/pages/{name}", base)) {
            val requirement = checkNotNull(ApiScopeRules.requirement("GET", pattern)) { pattern }
            assertTrue(allows(requirement, read, projectPublic = false), "GET $pattern")
        }
        assertTrue(allows(checkNotNull(ApiScopeRules.requirement("GET", "/-_-api/v1/user/issues")), read), "프로젝트를 훑는 목록도 읽기이다")
        assertTrue(allows(checkNotNull(ApiScopeRules.requirement("GET", "/-_-api/v1/user/projects")), read))
        // 쓰기는 read:repo로 되지 않는다
        for ((method, pattern) in listOf("POST" to "$base/issues", "PATCH" to "$base/issues/{number}", "DELETE" to "$base/issues/{number}", "POST" to "$base/pullrequests/{number}/merge", "POST" to "$base/wiki/pages")) {
            assertFalse(allows(checkNotNull(ApiScopeRules.requirement(method, pattern)), read, projectPublic = true), "$method $pattern")
        }
        // 읽기 전용 범위는 다른 범위가 필요한 읽기를 대신하지 않는다
        assertFalse(allows(checkNotNull(ApiScopeRules.requirement("GET", "/-_-api/v1/user/organizations")), read))
        assertFalse(allows(checkNotNull(ApiScopeRules.requirement("GET", "/-_-api/v1/admin/audit")), read))
    }

    @Test
    fun `읽기 전용 범위는 선택 목록에 repo 바로 뒤에 나온다`() {
        val list = dev.yona.domain.apitoken.ApiScopes.selectable(siteManager = false)
        assertTrue("read:repo" in list)
        assertEquals(setOf("read:repo"), dev.yona.domain.apitoken.ApiScopes.parse("read:repo"))
    }
}
