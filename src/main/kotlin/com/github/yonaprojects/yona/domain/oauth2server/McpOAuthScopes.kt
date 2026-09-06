package com.github.yonaprojects.yona.domain.oauth2server

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup

// yona-wiki P3-07(MCP 서버) Step2 — OAuth 스코프 축을 [[p3-02-cli-and-rest-api]]의
// ApiTokenScopeGroup(8개 그룹)/ApiTokenPermission(NONE/READ/WRITE)에서 그대로 파생한다(신규 축 설계
// 없음, 계획 문서 "완료 로그 — Step 1" 참고). 문자열 형식은 "<그룹 소문자>:<권한 소문자>"
// (예: "issues:read", "issues:write") — GitHub Fine-grained PAT 스코프 표기와 유사하다.
//
// RFC7591 DCR은 클라이언트가 등록 시점에 scope를 직접 지정하는 것을 기본 정책상 거부한다
// (OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR 확인 — "scope must not
// be set during Dynamic Client Registration") — 그래서 이 프로젝트는 모든 등록 클라이언트에 전체
// 스코프 목록을 무조건 부여하고(JpaRegisteredClientRepository.toEntity() 참고), 실제로 어떤 스코프를
// 발급할지는 매 인가 요청(/oauth2/authorize)의 scope 파라미터 + 동의 화면에서 사용자가 결정하게
// 한다 — Spring의 기본 설계 의도와 일치한다.
object McpOAuthScopes {
    val ALL: Set<String> = ApiTokenScopeGroup.entries.flatMap { group ->
        listOf(ApiTokenPermission.READ, ApiTokenPermission.WRITE).map {
            "${group.name.lowercase()}:${it.name.lowercase()}"
        }
    }.toSet()
}
