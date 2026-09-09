package com.github.yonaprojects.yona.domain.oauth2server

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.toApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.enumeration.ResourceType

// ApiTokenAuthorizer(PAT용)와 나란히 두는 OAuth2 JWT 스코프 판정 순수 함수. PAT은 ApiToken
// 엔티티(scopedProjects 등)를 직접 대조하지만, OAuth v1 토큰은 프로젝트 단위로 세분화되지
// 않으므로(항상 allRepositories=true와 동등) repo-scope 체크가 필요 없다. 그래서
// ApiTokenAuthorizer의 시그니처를 확장하는 대신 별도 오브젝트로 둔다 — PAT 경로에 새 개념
// (리소스 무관 스코프 문자열 매칭)을 섞지 않기 위함.
object OAuthScopeAuthorizer {

    // grantedScopes는 "SCOPE_" 접두어를 뗀 원시 문자열 집합(예: "issues:write") — 호출부
    // (OAuthApiScopeAuthorizationFilter)가 Spring이 JWT의 scope 클레임으로부터 자동 생성한
    // GrantedAuthority에서 접두어만 벗겨 넘긴다.
    fun isAuthorized(
        grantedScopes: Set<String>,
        resourceTypes: List<ResourceType?>,
        requiredPermission: ApiTokenPermission
    ): Boolean {
        if (requiredPermission == ApiTokenPermission.NONE) return true

        return resourceTypes.all { resourceType ->
            if (resourceType == null) return@all true // "metadata" 스코프 — 유효한 토큰이면 통과
            val group = resourceType.toApiTokenScopeGroup() ?: return@all false
            val groupName = group.name.lowercase()
            val grantedPermission = when {
                "$groupName:write" in grantedScopes -> ApiTokenPermission.WRITE
                "$groupName:read" in grantedScopes -> ApiTokenPermission.READ
                else -> ApiTokenPermission.NONE
            }
            grantedPermission.ordinal >= requiredPermission.ordinal
        }
    }
}
