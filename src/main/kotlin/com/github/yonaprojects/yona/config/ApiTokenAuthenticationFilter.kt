package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.apitoken.ApiToken
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenAuthorizer
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenRepository
import com.github.yonaprojects.yona.domain.apitoken.hashApiToken
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.regex.Pattern

/**
 * yona의 models/User.java extractUserTokenFromRequestHeader/findByUserToken +
 * controllers/api/UserApi.java isAuthored 대응.
 * "Authorization: token <값>" 또는 "Yona-Token: <값>" 헤더로 API 토큰 인증을 지원한다.
 *
 * yona-wiki P3-02 Step3 — `/api/v1/projects/{owner}/{project}/{resource}` 네임스페이스(Step4~6에서
 * 실제 컨트롤러가 채워질 신규 REST API 전용, 이번 라운드는 필터의 인가 로직만 선행 구현)로 들어오는
 * 요청은 ApiTokenRepository의 스코프 토큰으로만 인증하고, 스코프 밖이면 403으로 거부한다. 그 외
 * 기존 URL(레거시 `/api/projects/{id}/...` 등)은 기존 UserRepository.findByToken 기반 "매칭되면
 * 전권 부여" 동작을 그대로 유지한다 — 기존 전권 토큰 사용자 마이그레이션 전략이 아직 미정이라
 * (docs/yona-wiki/plans/p3-02-cli-and-rest-api.md "리스크/미결정 사항" 참고) 레거시 경로를 이번
 * 라운드에서 강제로 끊지 않는다.
 *
 * yona-wiki P3-02 Step6.5 — 리소스 세그먼트가 없는 두 경로(개별 프로젝트 조회 `/api/v1/projects/
 * {owner}/{project}`, 목록 `/api/v1/projects/{owner}`)도 스코프 토큰으로 인증되도록 갱신했다:
 * - 개별 조회는 "metadata" 스코프(그룹/권한 매트릭스 없이 repo scope만 확인, GitHub의 "Metadata:
 *   Read-only" 자동 부여와 동일한 개념)로 취급한다. URL 자체는 바꾸지 않고(3세그먼트 요구를
 *   완화하는 대신), 리소스 세그먼트가 없는 2세그먼트 요청을 별도 패턴으로 인식해 대표
 *   ResourceType으로 `resourceSegmentToResourceType["metadata"]`(= null)를 대입한다.
 * - 목록은 "인증됨/아님"만으로 부족해(어떤 프로젝트가 보이는지는 컨트롤러가 결정해야 함) 403을
 *   내지 않고 SecurityContext에 신원만 세팅한 뒤, 인증에 쓰인 ApiToken 객체를 request attribute
 *   (SCOPED_API_TOKEN_ATTRIBUTE)로 다운스트림 컨트롤러에 넘긴다(Spring Security가 CSRF 토큰 등을
 *   넘기는 방식과 동일).
 *
 * yona-wiki P3-02 10라운드(TASK-0417~0418) — "Fine-grained PAT으로 CLI의 모든 명령이 동작해야
 * 한다"는 목표에 맞춰, 위 세 패턴이 전부 `/api/v1/projects/{owner}/...`(최소 owner 세그먼트 필요)
 * 형태만 인식하던 갭을 메웠다. 실제 서버+실제 yona-cli로 재현한 5개 URL이 전부 이 갭 때문에
 * 스코프 인식이 안 되고 있었다:
 * - `POST /api/v1/projects`(프로젝트 생성, owner 세그먼트가 아예 없음) — 신규 프로젝트를 만들
 *   권한은 특정 프로젝트에 종속될 수 없는 "계정 수준" 판정이라, 기존 그룹/권한 매트릭스는
 *   그대로 재사용하되(ResourceType.PROJECT → ADMINISTRATION 그룹, 이미 존재) project는 null로
 *   판정하는 대신 반드시 `allRepositories=true`인 토큰만 허용한다(GitHub Fine-grained PAT도
 *   "All repositories" 토큰만 새 저장소 생성이 가능한 것과 동일한 논리 — 근거는
 *   docs/yona-wiki/plans/p3-02-cli-and-rest-api.md 참고).
 * - `GET /api/v1/user/issues/status`("내 이슈 현황", 특정 프로젝트가 아니라 로그인 사용자 전체를
 *   대상으로 함) — ISSUES 그룹으로 취급하되 project는 null(repo scope 체크 자체를 건너뜀 — 여러
 *   프로젝트에 걸친 집계라 단일 project로 좁힐 수 없음).
 * - `GET /site/export`(사이트 전체 백업, 여러 서버 산하 프로젝트를 대상으로 함) — SITE_SETTING
 *   → ADMINISTRATION 그룹으로 취급하고, 프로젝트 생성과 동일한 이유로 `allRepositories=true`를
 *   요구한다. 실제 사이트 관리자 권한 여부는 이 필터가 아니라 기존 SecurityConfig의
 *   `hasAnyRole("ADMIN","SITE_ADMIN")` 요구사항이 그대로 검사한다 — 이 필터는 신원만 세팅한다.
 * - `POST /api/projects/{id}/members`(레거시 숫자 ID 기반 프로젝트 멤버 관리) — owner/name이
 *   아니라 프로젝트 PK로 식별되는 유일한 API라 별도 패턴이 필요했다. PROJECT_SETTING
 *   (ADMINISTRATION 그룹)으로 취급하고 project는 ID로 조회해 repo scope를 그대로 검사한다.
 * - `POST /projects/{owner}/{project}/webhooks`(세션/폼 기반 레거시 MVC, `/api` 밖) — URL
 *   접두어만 다를 뿐 리소스 세그먼트 구조(`/{owner}/{project}/{resource}`)는 신규 API와 동일해
 *   기존 resourceSegmentToResourceType 매핑을 그대로 재사용한다. 이 경로는 세션 인증이 기본이라
 *   대부분의 요청엔 Authorization/Yona-Token 헤더가 없으므로(extractToken이 null 반환) 기존 세션
 *   기반 웹 UI 동작에는 영향이 없다 — PAT 헤더를 실제로 들고 오는 CLI 요청에만 적용된다.
 *
 * yona-wiki P3-02 16라운드(TASK-0440) — `GET /api/v1/user/status`(`gh status` 대응, 담당
 * 이슈+담당 PR+리뷰요청 PR을 한 번에 내려줌)는 ISSUES 스코프 하나만으로는 부족하다(PR 데이터도
 * 같이 내려주므로). `AccountLevelTarget.resourceType`을 `resourceTypes: List<ResourceType?>`로
 * 바꿔 여러 그룹을 AND로 요구할 수 있게 했다 — ISSUES:READ와 PULL_REQUESTS:READ 둘 다 있어야
 * 200, 하나라도 없으면 403.
 *
 * yona-wiki P3-07(MCP 서버) Step3 — `/mcp` 이하 요청(Streamable HTTP 단일 엔드포인트)은 이
 * 필터가 스코프를 판정하지 않는다. 한 HTTP 요청(JSON-RPC POST) 안에 어떤 도구(list_issues vs
 * merge_pull_request 등)가 들어있는지는 URL만 보고는 알 수 없고, 필요한 스코프도 도구마다
 * 다르기(그룹도 권한도) 때문이다 — 대신 `authenticateScopedList()`와 동일하게 신원만 확인하고
 * 인증에 쓰인 ApiToken을 request attribute로 넘겨, 실제 스코프 판정은
 * `com.github.yonaprojects.yona.mcp.McpScopeGuard`가 각 @Tool 메서드 호출 직전에
 * ApiTokenAuthorizer(이 클래스가 스코프 API에 쓰는 것과 동일한 순수 판정 로직)로 수행한다.
 */
@Component
class ApiTokenAuthenticationFilter(
    private val userRepository: UserRepository,
    private val userDetailsService: UserDetailsService,
    private val apiTokenRepository: ApiTokenRepository,
    private val projectRepository: ProjectRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val current = SecurityContextHolder.getContext().authentication
        val alreadyAuthenticated = current != null && current.isAuthenticated && current !is AnonymousAuthenticationToken

        if (!alreadyAuthenticated) {
            val token = extractToken(request)
            if (token != null) {
                val requestUri = request.requestURI
                val target = parseScopedApiTarget(requestUri)
                val accountTarget = if (target == null) parseAccountLevelTarget(requestUri) else null
                val legacyMemberProjectId = if (target == null && accountTarget == null) parseLegacyProjectIdTarget(requestUri) else null
                val legacyWebTarget = if (target == null && accountTarget == null && legacyMemberProjectId == null)
                    parseLegacyWebProjectTarget(requestUri) else null

                when {
                    target != null -> {
                        val project = projectRepository.findByOwnerAndName(target.owner, target.projectName).orElse(null)
                        if (!authenticateScoped(token, target.representativeResourceType, project, request, response)) {
                            return
                        }
                    }
                    isOwnerOnlyListRequest(requestUri) -> authenticateScopedList(token, request)
                    isMcpRequest(requestUri) -> authenticateScopedList(token, request)
                    accountTarget != null -> {
                        if (!authenticateAccountLevel(token, accountTarget, request, response)) {
                            return
                        }
                    }
                    legacyMemberProjectId != null -> {
                        val project = projectRepository.findById(legacyMemberProjectId).orElse(null)
                        if (!authenticateScoped(token, ResourceType.PROJECT_SETTING, project, request, response)) {
                            return
                        }
                    }
                    legacyWebTarget != null -> {
                        val project = projectRepository.findByOwnerAndName(legacyWebTarget.owner, legacyWebTarget.projectName).orElse(null)
                        if (!authenticateScoped(token, legacyWebTarget.representativeResourceType, project, request, response)) {
                            return
                        }
                    }
                    else -> authenticateLegacy(token)
                }
            }
        }

        filterChain.doFilter(request, response)
    }

    // 스코프 토큰 인증. 요청을 계속 진행해도 되면 true, 이미 response에 오류를 써서 체인을 끊었으면
    // false를 반환한다(GitAuthorizationFilter의 sendError + return 패턴과 동일).
    private fun authenticateScoped(
        token: String,
        resourceType: ResourceType?,
        project: Project?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Boolean {
        val apiToken = apiTokenRepository.findByTokenHash(hashApiToken(token)).orElse(null)
            ?: return true // 스코프 토큰이 아니면(모르는 토큰) 인증 없이 통과 — 컨트롤러/후속 인가에서 401 처리

        val requiredPermission = requiredPermissionFor(request.method)

        val allowed = ApiTokenAuthorizer.isAuthorized(
            token = apiToken,
            resourceType = resourceType,
            project = project,
            requiredPermission = requiredPermission
        )
        if (!allowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
            return false
        }

        markAuthenticated(apiToken, request)
        return true
    }

    // yona-wiki P3-02 10라운드 — 특정 프로젝트에 종속되지 않는 "계정 수준" 판정(신규 프로젝트 생성,
    // 로그인 사용자 전체 이슈 현황, 사이트 전체 백업 등). project를 아예 두지 않고 판정하므로
    // ApiTokenAuthorizer.isAuthorized()의 repo-scope 체크(project==null이면 항상 통과)만으로는
    // "특정 프로젝트로 좁혀진 토큰"까지 계정 전체 동작을 허용해버리는 구멍이 생긴다 — 이런
    // 액션들은 requireAllRepositories=true로 표시해 "전체 저장소" 토큰만 허용하도록 별도로 막는다
    // (GitHub Fine-grained PAT의 "All repositories" 전용 동작과 동일한 논리).
    private fun authenticateAccountLevel(
        token: String,
        target: AccountLevelTarget,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Boolean {
        val apiToken = apiTokenRepository.findByTokenHash(hashApiToken(token)).orElse(null)
            ?: return true

        val requiredPermission = requiredPermissionFor(request.method)
        // yona-wiki P3-02 16라운드 — `/api/v1/user/status`처럼 한 URL이 여러 스코프 그룹(ISSUES +
        // PULL_REQUESTS)에 걸친 데이터를 한 번에 내려줄 수 있어 resourceTypes를 리스트로 바꾸고
        // AND로 판정한다(전부 통과해야 허용) — 다른 계정 수준 URL은 전부 원소 1개짜리 리스트라
        // 동작이 그대로 유지된다.
        val scopeAllowed = target.resourceTypes.all { resourceType ->
            ApiTokenAuthorizer.isAuthorized(
                token = apiToken,
                resourceType = resourceType,
                project = null,
                requiredPermission = requiredPermission
            )
        }
        val allowed = scopeAllowed && (!target.requireAllRepositories || apiToken.allRepositories)
        if (!allowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
            return false
        }

        markAuthenticated(apiToken, request)
        return true
    }

    private fun markAuthenticated(apiToken: ApiToken, request: HttpServletRequest) {
        setAuthenticatedIdentity(apiToken)
        request.setAttribute(SCOPED_API_TOKEN_ATTRIBUTE, apiToken)

        apiToken.lastUsedAt = Instant.now()
        apiTokenRepository.save(apiToken)
    }

    // yona-wiki P3-02 Step6.5 — owner 전용 목록 경로(`/api/v1/projects/{owner}`)는 특정 프로젝트
    // 하나가 아니라 "owner 밑 전체"에 대한 요청이라 여기서 403을 내지 않는다. 어떤 프로젝트가
    // 보이는지는 컨트롤러가 request attribute로 넘겨받은 ApiToken을 보고 직접 필터링한다.
    // yona-wiki P3-07 Step3 — `/mcp` 요청도 동일한 이유(이 필터 하나로는 어떤 스코프가 필요한지
    // 알 수 없음)로 이 메서드를 재사용한다 — McpScopeGuard가 request attribute의 ApiToken을 꺼내
    // 실제 판정을 한다.
    private fun authenticateScopedList(token: String, request: HttpServletRequest) {
        val apiToken = apiTokenRepository.findByTokenHash(hashApiToken(token)).orElse(null) ?: return
        val expiresAt = apiToken.expiresAt ?: return
        if (!expiresAt.isAfter(Instant.now())) return

        setAuthenticatedIdentity(apiToken)
        request.setAttribute(SCOPED_API_TOKEN_ATTRIBUTE, apiToken)
    }

    private fun setAuthenticatedIdentity(apiToken: ApiToken) {
        val owner = apiToken.owner
        if (owner != null && owner.state != UserState.LOCKED && owner.state != UserState.DELETED) {
            val userDetails = userDetailsService.loadUserByUsername(owner.loginId) as YonaUserDetails
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        }
    }

    private fun authenticateLegacy(token: String) {
        val user = userRepository.findByToken(token).orElse(null)
        if (user != null && user.state != UserState.LOCKED && user.state != UserState.DELETED) {
            val userDetails = userDetailsService.loadUserByUsername(user.loginId) as YonaUserDetails
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        }
    }

    companion object {
        fun extractToken(request: HttpServletRequest): String? {
            val authHeader = request.getHeader("Authorization")
            if (authHeader != null && authHeader.contains("token ")) {
                return authHeader.substringAfter("token ").trim().takeIf { it.isNotBlank() }
            }
            return request.getHeader("Yona-Token")
        }

        // 신규 범용 REST API 네임스페이스(Step4~6에서 컨트롤러가 채워질 예정) 전용 패턴.
        // GitAuthorizationFilter.gitUriPattern과 동일한 접근(Pattern 상수 + group 추출)이다.
        private val scopedApiPattern = Pattern.compile("^/api/v1/projects/([^/]+)/([^/]+)/([^/]+)(?:/.*)?$")

        // yona-wiki P3-02 Step6.5 — 리소스 세그먼트가 없는 개별 프로젝트 조회
        // (`/api/v1/projects/{owner}/{project}`, 정확히 2세그먼트). scopedApiPattern은 3세그먼트를
        // 요구하므로 이 URL과 겹치지 않는다. 이 URL은 URL을 바꾸지 않고도 "metadata" 스코프로
        // 취급한다(resourceSegmentToResourceType["metadata"] 참고).
        private val individualProjectPattern = Pattern.compile("^/api/v1/projects/([^/]+)/([^/]+)/?$")

        // yona-wiki P3-02 Step6.5 — owner만 있는 목록 경로(`/api/v1/projects/{owner}`, 정확히
        // 1세그먼트). 위 두 패턴과 세그먼트 수가 달라 겹치지 않는다.
        private val ownerOnlyPattern = Pattern.compile("^/api/v1/projects/([^/]+)/?$")

        // yona-wiki P3-07(MCP 서버) Step3 — Spring AI MCP 서버 스타터의 Streamable HTTP 단일
        // 엔드포인트(`/mcp`, 기본값). 스코프 판정은 이 필터가 하지 않는다(위 클래스 KDoc 참고).
        private val mcpPattern = Pattern.compile("^/mcp(/.*)?$")

        // yona-wiki P3-02 10라운드 — owner 세그먼트조차 없는 프로젝트 "생성" 자체
        // (`POST /api/v1/projects`). 위 세 패턴 모두 최소 1개의 세그먼트(owner)를 요구하므로 겹치지
        // 않는다.
        private val projectCreatePattern = Pattern.compile("^/api/v1/projects/?$")

        // yona-wiki P3-02 10라운드 — 로그인 사용자 전체를 대상으로 하는 신규 API
        // (`/api/v1/user/issues/status` 등). `/api/v1/projects/**`와 구분되는 별도 네임스페이스다.
        private val userApiPattern = Pattern.compile("^/api/v1/user/issues(?:/.*)?$")

        // yona-wiki P3-02 16라운드(TASK-0440) — `gh status` 대응(GET /api/v1/user/status)은 이슈뿐
        // 아니라 PR(담당/리뷰요청)까지 한 번에 내려주므로 userApiPattern과 별도 패턴으로 분리한다 —
        // 아래 AccountLevelTarget.resourceTypes가 ISSUES 스코프 하나가 아니라 ISSUES+PULL_REQUESTS
        // 둘 다 요구하도록 판정해야 하기 때문이다.
        private val userStatusApiPattern = Pattern.compile("^/api/v1/user/status(?:/.*)?$")

        // yona-wiki P3-02 10라운드 — 사이트 전체 관리 API(`/site/**`, `/sites/**`). 실제 사이트
        // 관리자 권한 여부는 SecurityConfig의 hasAnyRole("ADMIN","SITE_ADMIN")이 별도로 검사하므로,
        // 이 필터는 PAT 토큰의 신원 확인 + ADMINISTRATION 스코프 보유 여부만 판정한다.
        private val siteApiPattern = Pattern.compile("^/sites?(?:/.*)?$")

        // yona-wiki P3-02 10라운드 — 레거시 숫자 프로젝트 ID 기반 API(`/api/projects/{id}/...`,
        // `ProjectMemberController`). owner/name이 아니라 PK로 프로젝트를 식별하는 유일한 경로라
        // 별도 패턴으로 분리했다.
        private val legacyProjectIdPattern = Pattern.compile("^/api/projects/(\\d+)(?:/.*)?$")

        // yona-wiki P3-02 10라운드 — 세션/폼 기반 레거시 MVC 프로젝트 리소스
        // (`/projects/{owner}/{project}/{resource}`, 예: 웹훅 생성). `/api` 접두어만 다를 뿐
        // 세그먼트 구조가 scopedApiPattern과 동일해 같은 resourceSegmentToResourceType 매핑을
        // 재사용한다.
        private val legacyWebProjectPattern = Pattern.compile("^/projects/([^/]+)/([^/]+)/([^/]+)(?:/.*)?$")

        // URL의 리소스 세그먼트(예: "issues")를 스코프 판정용 대표 ResourceType 하나로 매핑한다.
        // ApiTokenAuthorizer는 ResourceType을 ApiTokenScopeGroup으로 다시 뭉뚱그리므로, 그룹 안에서
        // 어떤 대표값을 고르는지는 판정 결과에 영향을 주지 않는다(같은 그룹이면 결과 동일).
        // "metadata" -> null은 리소스 그룹과 무관하게 repo scope만 확인하는 GitHub "Metadata:
        // Read-only" 자동 부여 대응이다 — 값 자체가 null이므로 조회는 반드시 containsKey로 해야
        // 한다(map[key] ?: return null은 "키 없음"과 "값이 null"을 구분하지 못한다).
        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — "labels"/"fork" 세그먼트 추가.
        // labels는 실제 위임 대상(ProjectViewController.newLabel/updateLabelForm/deleteLabelForm)이
        // 전부 ResourceType.ISSUE_LABEL 기준 AccessControl 체크를 쓰고 있어(ISSUES 그룹) 그대로
        // 맞춘다 - 필터가 부여하는 스코프와 컨트롤러가 실제로 요구하는 AccessControl 권한이 같은
        // 그룹이어야 "스코프는 통과했는데 컨트롤러가 거부"/그 반대의 불일치가 없다. fork는
        // ResourceType.FORK(CODE 그룹) - 저장소 코드를 복제하는 행위라 CODE 스코프가 자연스럽다.
        // yona-wiki P3-02 Step8.6 항목1(2026-09-01) — "permissions" 세그먼트 추가.
        // ProjectPermissionRestApiController가 위임하는 ProjectMemberController.listMembers()가
        // 프로젝트 매니저 권한을 요구하므로 "settings"와 동일하게 ResourceType.PROJECT_SETTING
        // (ADMINISTRATION 그룹)으로 맞춘다.
        private val resourceSegmentToResourceType: Map<String, ResourceType?> = mapOf(
            "issues" to ResourceType.ISSUE_POST,
            "pull-requests" to ResourceType.PULL_REQUEST,
            "code" to ResourceType.CODE,
            "board" to ResourceType.BOARD_POST,
            "wiki" to ResourceType.WIKI_PAGE,
            "webhooks" to ResourceType.WEBHOOK,
            "settings" to ResourceType.PROJECT_SETTING,
            "metadata" to null,
            "labels" to ResourceType.ISSUE_LABEL,
            "fork" to ResourceType.FORK,
            "permissions" to ResourceType.PROJECT_SETTING
        )

        private fun parseScopedApiTarget(requestUri: String?): ScopedApiTarget? {
            if (requestUri == null) return null

            val scopedMatcher = scopedApiPattern.matcher(requestUri)
            if (scopedMatcher.matches()) {
                val owner = scopedMatcher.group(1)
                val projectName = scopedMatcher.group(2)
                val resourceSegment = scopedMatcher.group(3)
                if (!resourceSegmentToResourceType.containsKey(resourceSegment)) return null
                return ScopedApiTarget(owner, projectName, resourceSegmentToResourceType[resourceSegment])
            }

            val individualMatcher = individualProjectPattern.matcher(requestUri)
            if (individualMatcher.matches()) {
                val owner = individualMatcher.group(1)
                val projectName = individualMatcher.group(2)
                return ScopedApiTarget(owner, projectName, resourceSegmentToResourceType.getValue("metadata"))
            }

            return null
        }

        // yona-wiki P3-02 10라운드 — 프로젝트에 종속되지 않는 계정 수준 URL 판정.
        private fun parseAccountLevelTarget(requestUri: String?): AccountLevelTarget? {
            if (requestUri == null) return null

            if (projectCreatePattern.matcher(requestUri).matches()) {
                return AccountLevelTarget(listOf(ResourceType.PROJECT), requireAllRepositories = true)
            }
            // userStatusApiPattern을 userApiPattern보다 먼저 확인할 필요는 없다(두 정규식이
            // "/api/v1/user/status"와 "/api/v1/user/issues"로 겹치지 않는다) — 가독성을 위해
            // 더 넓은 범위를 다루는 쪽을 먼저 뒀다.
            if (userStatusApiPattern.matcher(requestUri).matches()) {
                return AccountLevelTarget(listOf(ResourceType.ISSUE_POST, ResourceType.PULL_REQUEST), requireAllRepositories = false)
            }
            if (userApiPattern.matcher(requestUri).matches()) {
                return AccountLevelTarget(listOf(ResourceType.ISSUE_POST), requireAllRepositories = false)
            }
            if (siteApiPattern.matcher(requestUri).matches()) {
                return AccountLevelTarget(listOf(ResourceType.SITE_SETTING), requireAllRepositories = true)
            }
            return null
        }

        // yona-wiki P3-02 10라운드 — `/api/projects/{id}/...` 레거시 숫자 ID 패턴에서 프로젝트 ID를
        // 추출한다.
        private fun parseLegacyProjectIdTarget(requestUri: String?): Long? {
            if (requestUri == null) return null
            val matcher = legacyProjectIdPattern.matcher(requestUri)
            if (!matcher.matches()) return null
            return matcher.group(1).toLongOrNull()
        }

        // yona-wiki P3-02 10라운드 — 세션/폼 기반 레거시 MVC 프로젝트 리소스 URL 판정.
        private fun parseLegacyWebProjectTarget(requestUri: String?): ScopedApiTarget? {
            if (requestUri == null) return null
            val matcher = legacyWebProjectPattern.matcher(requestUri)
            if (!matcher.matches()) return null
            val owner = matcher.group(1)
            val projectName = matcher.group(2)
            val resourceSegment = matcher.group(3)
            if (!resourceSegmentToResourceType.containsKey(resourceSegment)) return null
            return ScopedApiTarget(owner, projectName, resourceSegmentToResourceType[resourceSegment])
        }

        private fun isOwnerOnlyListRequest(requestUri: String?): Boolean {
            if (requestUri == null) return false
            return ownerOnlyPattern.matcher(requestUri).matches()
        }

        private fun isMcpRequest(requestUri: String?): Boolean {
            if (requestUri == null) return false
            return mcpPattern.matcher(requestUri).matches()
        }

        private fun requiredPermissionFor(method: String): ApiTokenPermission {
            return when (method.uppercase()) {
                "GET", "HEAD", "OPTIONS" -> ApiTokenPermission.READ
                else -> ApiTokenPermission.WRITE
            }
        }

        // Spring Security가 CSRF 토큰 등을 필터→다운스트림으로 넘길 때 쓰는 것과 동일한 request
        // attribute 패턴 — 인증에 사용된 ApiToken을 컨트롤러(예: ProjectRestApiController 목록
        // API)가 꺼내 "어떤 프로젝트가 보이는지" 직접 필터링할 수 있게 한다.
        const val SCOPED_API_TOKEN_ATTRIBUTE = "SCOPED_API_TOKEN"
    }

    private data class ScopedApiTarget(
        val owner: String,
        val projectName: String,
        val representativeResourceType: ResourceType?
    )

    private data class AccountLevelTarget(
        val resourceTypes: List<ResourceType?>,
        val requireAllRepositories: Boolean
    )
}
