package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.config.ApiTokenAuthenticationFilter
import com.github.yonaprojects.yona.domain.apitoken.ApiToken
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenAuthorizer
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.Project
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * yona-wiki P3-07(MCP 서버) Step3 — MCP 도구 호출마다(호출 전에) 토큰 스코프를 검증하는 단일 지점.
 * 계획 문서 보안 검증 항목("토큰 스코프가 실제로 각 도구 호출 전에 검증되는지, 특히
 * merge_pull_request")에 대응 — 이 클래스를 거치지 않고 실제 컨트롤러에 위임하는 도구 메서드가
 * 하나도 없어야 한다(IssueMcpTools/PullRequestMcpTools의 모든 @Tool 메서드가 본문 첫 줄에서
 * require()를 호출한다).
 *
 * 이중 인증(OAuth JWT / P3-02 Fine-grained PAT) 각각 다른 방식으로 스코프를 담고 있어 분기한다:
 * - JWT: ResourceServerConfig의 jwtDecoder가 기본 스코프 클레임("scope", 공백 구분 문자열)을 그대로
 *   보존한다. OAuthProtectedResourceMetadataController가 광고하는 "issues:read"/"issues:write" 등
 *   문자열 그대로 들어있어 별도 변환 없이 문자열 포함 여부만 확인한다. v1의 OAuth 토큰은 항상 전체
 *   저장소 대상(계획 문서 "완료 로그 — Step 1" 참고)이라 project 매개변수는 무시한다.
 * - PAT: ApiTokenAuthenticationFilter가 `/mcp` 이하 요청에 대해 (스코프 판정 없이) 신원만 확인하고
 *   인증에 쓰인 ApiToken을 request attribute(SCOPED_API_TOKEN_ATTRIBUTE)로 다운스트림에 넘겨둔다 —
 *   이 클래스가 그 토큰을 꺼내 기존 ApiTokenAuthorizer(P3-02가 이미 검증해둔 순수 판정 로직, 여기서
 *   재구현하지 않음)로 그룹/권한/저장소 스코프까지 전부 판정한다.
 */
@Component
class McpScopeGuard {

    fun require(
        authentication: Authentication?,
        group: ApiTokenScopeGroup,
        permission: ApiTokenPermission,
        project: Project? = null
    ) {
        if (authentication == null || !authentication.isAuthenticated) {
            throw AccessDeniedException("인증이 필요합니다.")
        }

        when (authentication) {
            is JwtAuthenticationToken -> requireJwtScope(authentication, group, permission)
            else -> requirePatScope(group, permission, project)
        }
    }

    private fun requireJwtScope(authentication: JwtAuthenticationToken, group: ApiTokenScopeGroup, permission: ApiTokenPermission) {
        val scopes = authentication.token.getClaimAsString("scope")
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        val groupName = group.name.lowercase()
        val allowed = when (permission) {
            ApiTokenPermission.NONE -> true
            ApiTokenPermission.READ -> "$groupName:read" in scopes || "$groupName:write" in scopes
            ApiTokenPermission.WRITE -> "$groupName:write" in scopes
        }
        if (!allowed) {
            throw AccessDeniedException("이 OAuth 토큰에는 '$groupName:${permission.name.lowercase()}' 스코프가 없습니다.")
        }
    }

    private fun requirePatScope(group: ApiTokenScopeGroup, permission: ApiTokenPermission, project: Project?) {
        val apiToken = currentApiToken()
            ?: throw AccessDeniedException("이 토큰으로는 이 MCP 도구를 사용할 수 없습니다.")
        val allowed = ApiTokenAuthorizer.isAuthorized(
            token = apiToken,
            resourceType = representativeResourceTypeOf(group),
            project = project,
            requiredPermission = permission
        )
        if (!allowed) {
            throw AccessDeniedException(
                "이 토큰에는 '${group.name.lowercase()}:${permission.name.lowercase()}' 스코프가 없거나 대상 저장소가 스코프 밖입니다."
            )
        }
    }

    private fun currentApiToken(): ApiToken? {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return null
        return attributes.request.getAttribute(ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE) as? ApiToken
    }

    // ApiTokenAuthorizer는 ResourceType을 다시 ApiTokenScopeGroup으로 뭉뚱그리므로(ResourceType.
    // toApiTokenScopeGroup()) 그룹 안에서 어떤 대표값을 고르는지는 판정 결과에 영향을 주지 않는다
    // (ApiTokenAuthenticationFilter.resourceSegmentToResourceType의 동일한 접근 재사용).
    private fun representativeResourceTypeOf(group: ApiTokenScopeGroup): ResourceType = when (group) {
        ApiTokenScopeGroup.ISSUES -> ResourceType.ISSUE_POST
        ApiTokenScopeGroup.PULL_REQUESTS -> ResourceType.PULL_REQUEST
        ApiTokenScopeGroup.CODE -> ResourceType.CODE
        ApiTokenScopeGroup.BOARD -> ResourceType.BOARD_POST
        ApiTokenScopeGroup.WIKI -> ResourceType.WIKI_PAGE
        ApiTokenScopeGroup.WEBHOOKS -> ResourceType.WEBHOOK
        ApiTokenScopeGroup.ADMINISTRATION -> ResourceType.PROJECT_SETTING
        ApiTokenScopeGroup.USERS -> ResourceType.USER
    }
}
