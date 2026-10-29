package dev.yona.security

import dev.yona.domain.apitoken.ApiScopes

/** 외부 API 경로 하나가 요구하는 범위이다(추가 개발 항목 X-5, `architecture/api-auth-split.md`). */
sealed interface ApiRequirement {
    /** 범위가 필요 없다 */
    data object None : ApiRequirement

    /** `repo`(모든 프로젝트)나, 공개 프로젝트에 한해 `public_repo` */
    data object Repo : ApiRequirement

    /** `repo`만(프로젝트를 가리키지 않거나 공개 범위와 무관한 요청: 프로젝트 만들기, 모든 프로젝트를 훑는 내 이슈 목록) */
    data object RepoOnly : ApiRequirement

    /** `repo`나 `public_repo` 어느 쪽이든 된다(프로젝트에 묶이지 않는 첨부 올리기) */
    data object AnyRepo : ApiRequirement

    /** 이름 있는 범위(포함 관계를 따른다) */
    data class Scope(val name: String) : ApiRequirement
}

/**
 * 경로와 메서드별 필요한 범위의 대응표와 판정이다(`/-_-api/v1` 아래 경로에 한한다). 표에 없는 경로는 거부한다(기본 거부).
 * 토큰이 할 수 있는 일은 범위가 허용하는 일과 사용자의 실제 권한의 교집합이므로, 여기서는 범위만 본다.
 */
object ApiScopeRules {

    private const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"

    private val TABLE: Map<Pair<String, String>, ApiRequirement> = buildMap {
        fun add(method: String, pattern: String, requirement: ApiRequirement) { put(method to pattern, requirement) }
        // 이슈(`repo`, 공개 프로젝트는 `public_repo`)
        for (m in listOf("GET", "PUT", "PATCH")) add(m, "$PROJECT/issues/{number}", ApiRequirement.Repo)
        add("POST", "$PROJECT/issues", ApiRequirement.Repo)
        add("POST", "$PROJECT/issues/{number}/comments", ApiRequirement.Repo)
        add("POST", "$PROJECT/issuelabel/{number}", ApiRequirement.Repo)
        add("POST", "$PROJECT/issues/imports", ApiRequirement.Repo)
        // 게시글
        add("POST", "$PROJECT/posts", ApiRequirement.Repo)
        add("POST", "$PROJECT/posts/{number}/comments", ApiRequirement.Repo)
        add("GET", "$PROJECT/posts/{number:[0-9]+}/watchers", ApiRequirement.Repo)
        // 풀 리퀘스트(X-15, D-39): 모두 `repo`(공개 프로젝트는 `public_repo`)
        add("GET", "$PROJECT/pullrequests", ApiRequirement.Repo)
        add("GET", "$PROJECT/pullrequests/{number}", ApiRequirement.Repo)
        for (part in listOf("commits", "files", "timeline", "reviews", "comments")) add("GET", "$PROJECT/pullrequests/{number}/$part", ApiRequirement.Repo)
        for (part in listOf("close", "reopen", "merge", "fromBranch/restore", "assignees", "labels", "requestedreviewers")) add("POST", "$PROJECT/pullrequests/{number}/$part", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/pullrequests/{number}/fromBranch", ApiRequirement.Repo)
        for (part in listOf("reviews", "comments")) add("POST", "$PROJECT/pullrequests/{number}/$part", ApiRequirement.Repo)
        add("POST", "$PROJECT/pullrequests/{number}/reviews/{reviewId}/dismissals", ApiRequirement.Repo)
        add("PATCH", "$PROJECT/pullrequests/{number}/comments/{commentId}", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/pullrequests/{number}/comments/{commentId}", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/pullrequests/{number}/pendingreview", ApiRequirement.Repo)
        for (part in listOf("open", "close")) add("POST", "$PROJECT/pullrequests/{number}/threads/{threadId}/$part", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/pullrequests/{number}/requestedreviewers/{reviewer}", ApiRequirement.Repo)
        // 프로젝트
        add("POST", "/-_-api/v1/owners/{owner}/projects", ApiRequirement.RepoOnly)
        add("POST", "$PROJECT/labels", ApiRequirement.Repo)
        add("POST", "$PROJECT/milestones", ApiRequirement.Repo)
        add("GET", "$PROJECT/exports", ApiRequirement.Repo)
        // 사용자
        add("GET", "/-_-api/v1/user/issues", ApiRequirement.RepoOnly)
        add("GET", "/-_-api/v1/users/{user}/statistics", ApiRequirement.Scope("read:user"))
        add("POST", "/-_-api/v1/users", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        add("GET", "/-_-api/v1/admin/users", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        add("PATCH", "/-_-api/v1/admin/users/{user}", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        // 첨부 올리기(토큰 전용)
        add("POST", "/-_-api/v1/files", ApiRequirement.AnyRepo)
        // 상태 확인과 v1.6의 홈 별칭
        add("GET", "/-_-api/v1/hello", ApiRequirement.None)
        add("GET", "/-_-api/v1/", ApiRequirement.None)
        add("GET", "/-_-api", ApiRequirement.None)
    }

    /** [method]와 스프링이 고른 경로 패턴 [pattern]이 요구하는 범위이다. 표에 없으면 `null`이다. */
    fun requirement(method: String, pattern: String): ApiRequirement? = TABLE[method.uppercase() to pattern]

    /**
     * [scopes]를 가진 토큰이 [requirement]를 채우는지 판정한다. [projectPublic]은 요청이 가리키는 프로젝트가 공개(`PUBLIC`)인지이며
     * 프로젝트를 모르면 `null`이다. `public_repo`는 공개 프로젝트가 확인된 경우에만 채운다.
     */
    fun allows(requirement: ApiRequirement, scopes: Set<String>, projectPublic: Boolean?): Boolean = when (requirement) {
        ApiRequirement.None -> true
        ApiRequirement.RepoOnly -> ApiScopes.implies(scopes, ApiScopes.REPO)
        ApiRequirement.AnyRepo -> ApiScopes.implies(scopes, ApiScopes.REPO) || ApiScopes.implies(scopes, ApiScopes.PUBLIC_REPO)
        ApiRequirement.Repo -> ApiScopes.implies(scopes, ApiScopes.REPO) || (ApiScopes.implies(scopes, ApiScopes.PUBLIC_REPO) && projectPublic == true)
        is ApiRequirement.Scope -> ApiScopes.implies(scopes, requirement.name)
    }
}

/**
 * 대응표에 더할 경로의 요건을 제공한다. 운영 코드는 [ApiScopeRules]의 표만 쓰고, 시험이 시험용 경로를 더할 때 쓰는 확장 지점이다.
 * 반환 맵의 키는 (메서드, 스프링 경로 패턴)이다.
 */
fun interface ApiScopeRuleProvider {
    fun rules(): Map<Pair<String, String>, ApiRequirement>
}
