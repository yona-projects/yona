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
        // 이슈 쓰기 REST a단계(X-29a, D-47)
        add("DELETE", "$PROJECT/issues/{number}", ApiRequirement.Repo)
        add("GET", "$PROJECT/issues/{number}/comments", ApiRequirement.Repo)
        for (m in listOf("PATCH", "DELETE")) add(m, "$PROJECT/issues/{number}/comments/{commentId}", ApiRequirement.Repo)
        add("POST", "$PROJECT/issues/{number}/labels", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/issues/{number}/labels/{labelId}", ApiRequirement.Repo)
        for (m in listOf("POST", "DELETE")) add(m, "$PROJECT/issues/{number}/assignees", ApiRequirement.Repo)
        // 이슈의 Yona 고유 쓰기 REST d단계(X-29d, D-47)
        add("GET", "$PROJECT/issues/{number}/sharers", ApiRequirement.Repo)
        for (m in listOf("POST", "DELETE")) { add(m, "$PROJECT/issues/{number}/sharers", ApiRequirement.Repo); add(m, "$PROJECT/issues/{number}/watchers", ApiRequirement.Repo) }
        add("POST", "$PROJECT/issues/{number}/weight", ApiRequirement.Repo)
        add("POST", "$PROJECT/issues/{number}/transfer", ApiRequirement.Repo)
        add("POST", "$PROJECT/issuelabel/{number}", ApiRequirement.Repo)
        add("POST", "$PROJECT/issues/imports", ApiRequirement.Repo)
        // 게시글
        add("POST", "$PROJECT/posts", ApiRequirement.Repo)
        add("POST", "$PROJECT/posts/{number}/comments", ApiRequirement.Repo)
        add("GET", "$PROJECT/posts/{number:[0-9]+}/watchers", ApiRequirement.Repo)
        // 게시판 쓰기 REST b단계(X-29b, D-47)
        add("GET", "$PROJECT/posts", ApiRequirement.Repo)
        for (m in listOf("GET", "PATCH", "DELETE")) add(m, "$PROJECT/posts/{number:[0-9]+}", ApiRequirement.Repo)
        add("GET", "$PROJECT/posts/{number:[0-9]+}/comments", ApiRequirement.Repo)
        for (m in listOf("PATCH", "DELETE")) add(m, "$PROJECT/posts/{number:[0-9]+}/comments/{commentId}", ApiRequirement.Repo)
        add("POST", "$PROJECT/posts/{number:[0-9]+}/labels", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/posts/{number:[0-9]+}/labels/{labelId}", ApiRequirement.Repo)
        // 풀 리퀘스트(X-15, D-39): 모두 `repo`(공개 프로젝트는 `public_repo`)
        add("GET", "$PROJECT/pullrequests", ApiRequirement.Repo)
        add("GET", "$PROJECT/pullrequests/{number}", ApiRequirement.Repo)
        add("POST", "$PROJECT/pullrequests", ApiRequirement.Repo)
        add("PATCH", "$PROJECT/pullrequests/{number}", ApiRequirement.Repo)
        for (part in listOf("commits", "files", "timeline", "reviews", "comments", "reviewercandidates", "assigneecandidates")) add("GET", "$PROJECT/pullrequests/{number}/$part", ApiRequirement.Repo)
        for (part in listOf("close", "reopen", "merge", "fromBranch/restore", "assignees", "labels", "requestedreviewers")) add("POST", "$PROJECT/pullrequests/{number}/$part", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/pullrequests/{number}/fromBranch", ApiRequirement.Repo)
        for (part in listOf("reviews", "comments")) add("POST", "$PROJECT/pullrequests/{number}/$part", ApiRequirement.Repo)
        add("POST", "$PROJECT/pullrequests/{number}/reviews/{reviewId}/dismissals", ApiRequirement.Repo)
        add("PATCH", "$PROJECT/pullrequests/{number}/comments/{commentId}", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/pullrequests/{number}/comments/{commentId}", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/pullrequests/{number}/pendingreview", ApiRequirement.Repo)
        for (part in listOf("open", "close")) add("POST", "$PROJECT/pullrequests/{number}/threads/{threadId}/$part", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/pullrequests/{number}/requestedreviewers/{reviewer}", ApiRequirement.Repo)
        // 일반 REST 1차의 조회(X-19, D-40)
        add("GET", PROJECT, ApiRequirement.Repo)
        for (part in listOf("members", "assignableusers", "labels", "milestones", "issues")) add("GET", "$PROJECT/$part", ApiRequirement.Repo)
        add("GET", "/-_-api/v1/user/projects", ApiRequirement.AnyRepo)
        add("GET", "/-_-api/v1/owners/{owner}/projects", ApiRequirement.AnyRepo)
        // 프로젝트
        add("POST", "/-_-api/v1/owners/{owner}/projects", ApiRequirement.RepoOnly)
        add("POST", "$PROJECT/labels", ApiRequirement.Repo)
        add("POST", "$PROJECT/milestones", ApiRequirement.Repo)
        // 라벨과 마일스톤 관리 REST(X-29c, D-47)
        for (m in listOf("PATCH", "DELETE")) { add(m, "$PROJECT/labels/{labelId}", ApiRequirement.Repo); add(m, "$PROJECT/milestones/{milestoneId}", ApiRequirement.Repo) }
        add("GET", "$PROJECT/exports", ApiRequirement.Repo)
        // 조직(X-30a, D-49): 읽기 `read:org`, 구성원 변경 `write:org`, 만들기와 설정과 삭제 `admin:org`
        add("GET", "/-_-api/v1/user/organizations", ApiRequirement.Scope("read:org"))
        for (pattern in listOf("/-_-api/v1/orgs/{org}", "/-_-api/v1/orgs/{org}/members")) add("GET", pattern, ApiRequirement.Scope("read:org"))
        add("POST", "/-_-api/v1/orgs/{org}/members", ApiRequirement.Scope("write:org"))
        for (m in listOf("PATCH", "DELETE")) add(m, "/-_-api/v1/orgs/{org}/members/{loginId}", ApiRequirement.Scope("write:org"))
        add("POST", "/-_-api/v1/orgs", ApiRequirement.Scope("admin:org"))
        for (m in listOf("PATCH", "DELETE")) add(m, "/-_-api/v1/orgs/{org}", ApiRequirement.Scope("admin:org"))
        // 웹훅(X-30b, D-49): 읽기 `read:repo_hook`, 만들기 `write:repo_hook`, 삭제 `admin:repo_hook`(`repo`가 셋을 포함한다)
        add("GET", "$PROJECT/hooks", ApiRequirement.Scope("read:repo_hook"))
        add("POST", "$PROJECT/hooks", ApiRequirement.Scope("write:repo_hook"))
        add("DELETE", "$PROJECT/hooks/{hookId}", ApiRequirement.Scope("admin:repo_hook"))
        // 검색(X-30c, D-49): 전체와 조직은 비공개 항목도 찾으므로 `repo`, 프로젝트는 `repo`나 공개 프로젝트의 `public_repo`
        add("GET", "/-_-api/v1/search", ApiRequirement.RepoOnly)
        add("GET", "/-_-api/v1/search/pullrequests", ApiRequirement.RepoOnly)
        add("GET", "/-_-api/v1/orgs/{org}/search", ApiRequirement.RepoOnly)
        add("GET", "$PROJECT/search", ApiRequirement.Repo)
        // 포크(X-30d, D-49): `repo`나 공개 프로젝트의 `public_repo`
        add("POST", "$PROJECT/forks", ApiRequirement.Repo)
        // 프로젝트 만들기, 설정 바꾸기, 지우기(X-37a, D-52): 만들기와 지우기는 `repo`, 설정은 `repo`나 공개 프로젝트의 `public_repo`
        add("POST", "/-_-api/v1/user/projects", ApiRequirement.RepoOnly)
        add("POST", "/-_-api/v1/orgs/{org}/projects", ApiRequirement.RepoOnly)
        add("PATCH", PROJECT, ApiRequirement.Repo)
        add("DELETE", PROJECT, ApiRequirement.RepoOnly)
        // 프로젝트 구성원 더하기, 역할 바꾸기, 빼기(X-37b, D-52)
        add("POST", "$PROJECT/members", ApiRequirement.Repo)
        for (m in listOf("PATCH", "DELETE")) add(m, "$PROJECT/members/{loginId}", ApiRequirement.Repo)
        // Git 태그(X-37c, D-52)
        for (m in listOf("GET", "POST")) add(m, "$PROJECT/tags", ApiRequirement.Repo)
        add("DELETE", "$PROJECT/tags/{*name}", ApiRequirement.Repo)
        // 감사 로그(X-9 A6, D-62): 사이트 전체는 사이트 관리자 범위, 조직은 조직 관리 범위, 본인 기록은 사용자 읽기 범위
        add("GET", "/-_-api/v1/admin/audit", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        add("GET", "/-_-api/v1/admin/audit/verify", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        add("GET", "/-_-api/v1/admin/audit/streams", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        add("GET", "/-_-api/v1/orgs/{org}/audit", ApiRequirement.Scope("admin:org"))
        add("GET", "/-_-api/v1/user/audit", ApiRequirement.Scope("read:user"))
        // 프로젝트 위키(X-17, D-60): `repo`나 공개 프로젝트의 `public_repo`
        for (m in listOf("GET", "POST")) add(m, "$PROJECT/wiki/pages", ApiRequirement.Repo)
        for (m in listOf("GET", "PATCH", "DELETE")) add(m, "$PROJECT/wiki/pages/{name}", ApiRequirement.Repo)
        add("GET", "$PROJECT/wiki/pages/{name}/history", ApiRequirement.Repo)
        add("GET", "$PROJECT/wiki/pages/{name}/diff", ApiRequirement.Repo)
        add("POST", "$PROJECT/wiki/files", ApiRequirement.Repo)
        add("GET", "$PROJECT/wiki/files/{name}", ApiRequirement.Repo)
        // 토큰 주인 조회(X-37i, D-52): 범위가 필요 없고 컨트롤러가 토큰 유무를 확인한다
        add("GET", "/-_-api/v1/user", ApiRequirement.None)
        // 사용자 현황(X-37e, D-52): 비공개 프로젝트까지 걸리므로 `repo`
        add("GET", "/-_-api/v1/user/status", ApiRequirement.RepoOnly)
        // 사용자
        add("GET", "/-_-api/v1/user/issues", ApiRequirement.RepoOnly)
        add("GET", "/-_-api/v1/users/{user}/statistics", ApiRequirement.Scope("read:user"))
        add("POST", "/-_-api/v1/users", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        add("GET", "/-_-api/v1/admin/users", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        // 사이트 백업(X-37g, D-52)
        add("GET", "/-_-api/v1/admin/export", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        add("POST", "/-_-api/v1/admin/import", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        add("PATCH", "/-_-api/v1/admin/users/{user}", ApiRequirement.Scope(ApiScopes.SITE_ADMIN))
        // 첨부 올리기(토큰 전용)
        add("POST", "/-_-api/v1/files", ApiRequirement.AnyRepo)
        // 상태 확인과 v1.6의 홈 별칭
        add("GET", "/-_-api/v1/hello", ApiRequirement.None)
        // 기기 인증(X-37i, D-52): CLI가 토큰을 받기 전에 부르므로 인증이 없다
        add("POST", "/-_-api/v1/oauth/device/code", ApiRequirement.None)
        add("POST", "/-_-api/v1/oauth/token", ApiRequirement.None)
        add("GET", "/-_-api/v1/", ApiRequirement.None)
        add("GET", "/-_-api", ApiRequirement.None)
    }

    /** 명세의 경로(정규식을 뗀 `{number}` 형식)와 메서드에 해당하는 대응표의 요구이다. OpenAPI 문서가 범위를 채울 때 쓴다(X-31). 표에 없으면 `null`이다. */
    fun requirementByPlainPath(method: String, plainPath: String): ApiRequirement? =
        TABLE.entries.firstOrNull { (key, _) -> key.first.equals(method, ignoreCase = true) && plain(key.second) == plainPath }?.value

    /** 스프링 경로 패턴에서 정규식(`{number:[0-9]+}`)과 나머지 전부 잡기(`{*name}`)를 떼어 명세의 경로 형식(`{number}`, `{name}`)으로 만든다. */
    fun plain(pattern: String): String = pattern.replace(Regex("\\{(\\w+):[^}]*\\}"), "{$1}").replace(Regex("\\{\\*(\\w+)\\}"), "{$1}")

    /** 대응표에 올라 있는 모든 (메서드, 경로 패턴)이다. OpenAPI 명세와 일치하는지 시험이 비교한다(X-31). */
    fun operations(): Set<Pair<String, String>> = TABLE.keys

    /** [method]와 스프링이 고른 경로 패턴 [pattern]이 요구하는 범위이다. 표에 없으면 `null`이다. */
    fun requirement(method: String, pattern: String): ApiRequirement? =
        TABLE[method.uppercase() to pattern] ?: if (method.equals("GET", ignoreCase = true) && DOC_PREFIXES.any { pattern.startsWith(it) }) ApiRequirement.None else null

    /**
     * OpenAPI 문서의 화면과 명세와 정적 자원 경로이다(X-31, D-50). 누구나 읽으므로 범위가 필요 없다. springdoc이 자원 경로를 여럿 만들어
     * (`/-_-api/swagger-ui` 아래의 모든 자원 등) 하나씩 표에 올리지 않고 접두사로 허용한다. 문서 경로는 읽기(`GET`)만 있다.
     */
    private val DOC_PREFIXES = listOf("/-_-api/docs", "/-_-api/swagger-ui")

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
