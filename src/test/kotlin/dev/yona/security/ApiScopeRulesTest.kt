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
        assertEquals(ApiRequirement.Repo, req("GET", "$base/issues/{number}"))
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
        assertEquals(ApiRequirement.Repo, req("GET", "$base/exports"))
        assertEquals(ApiRequirement.Repo, req("GET", "$base/posts/{number:[0-9]+}/watchers"))
        assertEquals(ApiRequirement.RepoOnly, req("POST", "/-_-api/v1/owners/{owner}/projects"))
        assertEquals(ApiRequirement.RepoOnly, req("GET", "/-_-api/v1/user/issues"))
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
}
