---
type: plan
id: P3-14
title: "yona를 OAuth2 서버(Authorization Server)로 제공"
status: done
priority: 11
depends_on: [p3-07]
blocks: []
source: docs/parity/tickets/p3-14.md
created: 2026-09-07
updated: 2026-09-07
tags: [plan, p3, oauth2, security]
---

# yona를 OAuth2 서버(Authorization Server)로 제공

## 배경

지금 yona는 OAuth2 **클라이언트**(소셜 로그인, `config/oauth2/`)와, [[p3-07-mcp-server]]가 만든
MCP 전용 OAuth2 **인가 서버**만 갖고 있다. 이 티켓은 그 인가 서버 인프라를 확장해 임의의 제3자
앱이 "Sign in with yona"로 로그인하거나 사용자 동의하에 yona REST API에 위임 접근하도록
지원한다 — 별도로 새 인가 서버를 만들지 않고 P3-07의 것을 그대로 확장하는 게 티켓의 핵심 요구사항.

착수 전 코드를 직접 읽어 확인한 결과, P3-07이 이미 대부분을 범용으로 설계해둬서(OAuth 스코프
포맷이 이미 `ApiTokenScopeGroup` 기반, 동의 화면/Authorized Apps UI가 이미 클라이언트 종류 무관)
실제 남은 gap은 아래 4가지로 좁혀졌다.

## 범위

### 포함 (1라운드)
- 발급 가능한 리소스를 `/mcp` 하나에서 `/mcp`+`/api/v1` 레지스트리로 일반화
- `/api/v1/**`(P3-02 REST API)에 OAuth2 JWT 인증 추가(기존 PAT과 이중 지원)
- `/api/v1/**`에서 OAuth JWT의 스코프를 P3-02와 동일한 축(ApiTokenScopeGroup/Permission)으로 인가
- 사이트 관리자가 confidential/public OAuth 앱을 직접 등록하는 UI(`/site/oauth-apps`)

### 제외 (2라운드 이후)
- OIDC("Sign in with yona" — ID 토큰/`/userinfo`/discovery), `openid`/`profile`/`email` 스코프
- 프로젝트 단위로 세분화된 OAuth 스코프(현재 PAT의 `scopedProjects`에 대응하는 개념 — v1 OAuth
  토큰은 P3-07이 이미 정한 전례(`allRepositories=true`와 동등)를 그대로 따름)

## 의존성

- **선행 조건**: [[p3-07-mcp-server]](완료) — 인가 서버 핵심 인프라(PKCE/DCR/동의화면/JWKS)
- **후속 파급**: 없음

## 조사 결과 — P3-07이 이미 만들어둔 것 (재사용, 손대지 않음)

- `AuthorizationServerConfig.kt`: PKCE 강제, RFC7591 DCR(오픈 등록 + `DcrRateLimitFilter`로 IP당
  분당 10회 제한), GitHub 스타일 동의 화면(`OAuthConsentController`/`consent.html`) — 클라이언트
  종류 무관.
- `McpOAuthScopes.kt`: OAuth 스코프를 `"<ApiTokenScopeGroup 소문자>:<read|write>"` 형식으로 이미
  파생 — 신규 축 설계 불필요.
- `UserViewController.kt`(`/user/editform/oauth-apps`) + `edit_oauth_apps.html`: "Authorized OAuth
  Apps" 목록/취소(revoke) 화면 — 이미 완성, 손댈 필요 없음.
- `OAuthRegisteredClient.kt`: 엔티티 자체는 이미 confidential 클라이언트(`clientSecret` nullable)를
  염두에 두고 설계됨.

## 조사 결과 — 실제 gap

1. **리소스/오디언스가 `/mcp` 하나로 하드코딩**: `ResourceIndicatorTokenCustomizer.kt`(발급
   시점)와 `ResourceServerConfig.kt`(검증 시점 + `securityMatcher("/mcp/**")`) 둘 다 `"$baseUrl/mcp"`
   문자열과 정확히 일치해야만 통과했다.
2. **`/api/v1/**`는 PAT 전용**: `ApiTokenAuthenticationFilter`가 유일한 인가 지점이고,
   `ApiTokenAuthorizer.isAuthorized(token: ApiToken, ...)`가 `ApiToken` 엔티티를 직접 요구해
   OAuth JWT의 `scope` 클레임(다른 축)과 바로 재사용할 수 없었다.
3. **Confidential 클라이언트를 사전등록할 방법이 없음**: `JpaRegisteredClientRepository.toEntity()`가
   `save()` 호출마다 항상 MCP 전용 정책(스코프=전체, PKCE 필수, 고정 TTL)을 강제로 덮어써(DCR 전용
   경로) GitHub OAuth Apps처럼 "이름+redirect_uri로 앱 하나 등록" 흐름이 없었다.
4. **OIDC 미설정**(2라운드로 이월).

## 설계 개요

### 1. `ProtectedResource` 레지스트리 (신규, `config/oauth2server/ProtectedResource.kt`)

`enum class ProtectedResource(path: String) { MCP("/mcp"), API("/api/v1") }` — `uri(baseUrl)`/
`fromUri(uri, baseUrl)`. `ResourceIndicatorTokenCustomizer`(발급 시점 `resource` 파라미터 검증)와
`ResourceServerConfig`(검증 시점 오디언스) 둘 다 이 레지스트리를 기준으로 삼도록 일반화했다.
`AudienceValidator`는 이미 리소스 무관하게 설계돼 있어(requiredAudience만 주입) 코드 변경 없이
그대로 재사용.

### 2. `/api/v1/**` 리소스 서버 체인 (신규, `ResourceServerConfig.apiResourceServerSecurityFilterChain`, `@Order(3)`)

`/mcp/**` 체인과 동일한 구조(PAT + JWT 이중 인증, `apiTokenAuthenticationFilter`를
`BearerTokenAuthenticationFilter` 앞에 배치)로 신설. `SecurityConfig`의 캐치올 체인(`@Order(3)`→`4`로
밀림)에 있던 `/api/v1/projects/**` 규칙(GET permitAll + 나머지 authenticated)은 이 신규 체인이
그대로 이어받고, 캐치올에서는 제거했다(도달 불가능해질 규칙이라 죽은 코드 방지).

### 3. `OAuthApiScopeAuthorizationFilter` + `OAuthScopeAuthorizer` (신규)

`ApiTokenAuthenticationFilter`의 URL→`ResourceType`/필요 권한 추론 로직(이미 계정수준/스코프드
API/owner-only-list 등 여러 패턴을 판정하던 것)에서 순수 URL 파싱 부분만
`ApiTokenAuthenticationFilter.resolveRequiredScope(request)`(companion object, public)로 뽑아
재사용했다 — 기존 PAT 경로(`authenticateScoped`/`authenticateAccountLevel`)는 전혀 건드리지 않음.
`OAuthApiScopeAuthorizationFilter`는 `SecurityContext`의 Authentication이 `JwtAuthenticationToken`일
때만(= PAT이 아닐 때만) 동작해, JWT의 `SCOPE_xxx` 권한과 `OAuthScopeAuthorizer.isAuthorized()`
(PAT의 `ApiTokenAuthorizer`와 동일한 ordinal 비교 규칙, `NONE < READ < WRITE`)로 판정한다.
v1 OAuth 토큰은 프로젝트 단위로 세분화되지 않으므로(위 "제외" 참고) repo-scope 체크는 하지 않는다.

### 4. `OAuthAppsAdminController` (신규, `/site/oauth-apps`)

`SsoAdminController`와 동일한 사이트 관리자 전용 패턴. `JpaRegisteredClientRepository.toEntity()`
(DCR 전용, 항상 MCP 정책 강제)를 거치지 않고 `OAuthRegisteredClientRepository`(JPA)에 직접
엔티티를 저장해 DCR과 완전히 분리된 경로로 둔다. Public(PKCE, `requireProofKey=true`,
`client_secret_basic` 없음)/Confidential(`client_secret_basic`, `requireProofKey=false`) 두
종류 지원. Confidential 시크릿은 `PasswordEncoder`(`AuthorizationServerConfig`에 신규 등록,
Spring Authorization Server의 `client_secret_basic` 검증이 `PasswordEncoder` 계약을 직접 쓰는
유일한 지점이라 — 이 앱의 다른 모든 비밀번호/토큰은 자체 SHA-256+Base64 해시를 쓰고
PasswordEncoder를 쓰지 않는 것과 대비됨)로 인코딩해 저장, 평문은 등록 직후 화면에 1회만 노출
(GitHub OAuth App 관례). 허용 스코프는 `ApiTokenScopeGroup x READ/WRITE` 체크박스로 관리자가
선택(McpOAuthScopes.ALL과 같은 축, DCR처럼 무조건 전체를 주지 않고 관리자가 좁힘).

## 완료 로그

- **2026-09-07 1라운드**: 위 설계 개요 1~4 항목 전부 TDD로 구현.
  - 신규 파일: `config/oauth2server/{ProtectedResource,OAuthApiScopeAuthorizationFilter}.kt`,
    `domain/oauth2server/OAuthScopeAuthorizer.kt`, `web/OAuthAppsAdminController.kt`,
    `templates/site/oauth_apps.html`.
  - 변경: `ResourceIndicatorTokenCustomizer.kt`(레지스트리 기반 검증), `ResourceServerConfig.kt`
    (`/api/v1/**` 체인 추가, JwtDecoder 2종), `SecurityConfig.kt`(캐치올 `@Order 3`→`4`, 죽은
    `/api/v1/projects/**` 규칙 제거), `AuthorizationServerConfig.kt`(`PasswordEncoder` 빈 추가),
    `ApiTokenAuthenticationFilter.kt`(`resolveRequiredScope()` 공개 함수 추가, 기존 PAT 로직
    무변경), `AudienceValidator.kt`(주석만 일반화, 동작 무변경), `site/layout.html`(사이드바
    "OAuth Apps" 메뉴 추가).
  - 테스트: `ProtectedResourceSpec`/`OAuthScopeAuthorizerSpec`(순수 로직), `OAuthAppsAdminControllerSpec`
    (등록/삭제/권한), `ApiV1OAuth2SecurityIntegrationSpec`(신규 — 실제 서명된 JWT로 오디언스
    격리 + 스코프 인가를 MockMvc+전체 SecurityFilterChain으로 검증) 전부 GREEN. 기존
    `McpOAuth2SecurityIntegrationSpec`/`ApiTokenAuthenticationFilterSpec`/
    `OAuthAuthorizedAppsServiceSpec`도 회귀 없이 GREEN 재확인(-Dyona.it.db=h2,
    [[feedback_use_h2_profile_in_sandbox]] 참고).
  - **남은 것(2라운드, 완료됨 — 아래 참고)**: OIDC("Sign in with yona"), 등록 UI에 identity
    스코프(openid/profile/email) 선택 추가.

- **2라운드 착수 전 사용자 결정사항(2026-09-07 확정)**:
  1. **UserInfo 클레임 범위**: `profile`+`email` 스코프 전부 노출(GitHub OAuth App과 동등한
     수준 — 이름/아바타/이메일까지 제3자 앱에 제공). `sub`만 노출하는 최소 범위 안은 채택하지
     않음.
  2. **identity 스코프 부여 방식**: 모든 confidential 클라이언트에 자동 포함하지 않고, 앱 등록
     폼에서 `openid`/`profile`/`email`을 앱별로 개별 선택하는 체크박스를 추가한다. (이 결정 당시
     문서는 앱 등록 폼을 `OAuthAppsAdminController`로 적었으나, 이 결정 자체보다 나중인
     [[p3-17]]에서 앱 등록이 사이트 관리자 전용에서 사용자 셀프서비스로 이미 옮겨졌다 — 실제
     구현은 `OAuthAppRegistrationService`/`UserViewController`의 `/user/editform/oauth-apps-owned/new`
     폼에 이 체크박스를 추가했다. `OAuthAppsAdminController`는 P3-17 이후 전체 앱 조회/강제
     삭제 감사(audit) 전용이라 등록 폼 자체가 없다.)

- **2026-09-07 2라운드(OIDC, "Sign in with yona") — 완료**: 위 두 결정사항을 그대로 TDD로 구현.
  - 변경: `AuthorizationServerConfig.kt`(`.oidc { }` 활성화 + `providerConfigurationEndpoint`
    커스터마이즈로 `scopes_supported`/`claims_supported` 광고, `/userinfo`용 오디언스 무관
    JwtDecoder를 붙인 `oauth2ResourceServer{}` 추가), `ResourceServerConfig.kt`(`userInfoJwtDecoder`
    빈 추가), `ResourceIndicatorTokenCustomizer.kt`(ID 토큰 identity 클레임 매핑을 같은 클래스에
    합침 — 이유는 아래 "실제 버그" 참고), `OAuthAppRegistrationService.kt`(`identityScopes()`
    추가, `availableScopes()`에 합류), `UserViewController.kt`/`edit_oauth_apps_owned_new.html`
    (identity 스코프 체크박스 섹션), `OAuthConsentController.kt`/`consent.html`(openid/profile/
    email 세 스코프만 plain-language 설명으로 대체), `OAuthAuthorization.kt`/
    `JpaOAuth2AuthorizationService.kt`(`oidc_id_token` 4컬럼 추가 — 아래 버그 2번), messages
    properties(신규 키).
  - **클레임 매핑 설계**: ID 토큰만 `authorizedScopes` 기준으로 커스터마이즈하고, `/userinfo`는
    프레임워크 기본 `DefaultOidcUserInfoMapper`를 그대로 사용 — 그 기본 구현이 "ID 토큰의 클레임을
    액세스 토큰 스코프로 재필터링"하는 방식이라 ID 토큰만 게이팅해도 두 곳 모두 일관되게
    스코프-게이팅이 적용된다(커스텀 UserInfo 매퍼 불필요).
  - **실제 버그 2건(실측 후 즉시 수정)**: (1) `OAuth2TokenCustomizer<JwtEncodingContext>` 빈을
    2개로 늘리면 Spring이 `getBeanProvider(type).getIfUnique()`로 조회해 **예외 없이 조용히
    null을 반환**(1라운드 RFC8707 오디언스 스탬핑까지 함께 무력화될 뻔함) — 그래서 별도 클래스로
    분리하지 않고 기존 `ResourceIndicatorTokenCustomizer` 하나로 합침. (2) `OAuthAuthorization`
    엔티티가 1라운드엔 "OIDC 미사용"이라는 이유로 `oidc_id_token` 컬럼을 뺐었는데, OIDC를 켜자마자
    ID 토큰이 재저장 시 조용히 사라져 `/userinfo`가 매번 `invalid_token`으로 실패하는 진짜 gap이
    됐다 — 공식 JdbcOAuth2AuthorizationService와 동일한 4컬럼 구성으로 해소. 두 버그 모두 순수
    단위 테스트가 아니라 실제 E2E 통합테스트(`OidcSignInIntegrationSpec`)를 작성하는 과정에서
    드러났다.
  - **스코프-클레임 게이팅 검증**: `openid`만 동의하면 ID 토큰/`/userinfo` 둘 다 `sub` 외
    클레임이 전혀 없음을 GREEN으로 확인. 이 과정에서 프레임워크 자체의 진짜 동작도 하나 더
    확인했다 — "openid가 유일한 요청 스코프면 동의 화면 자체를 생략한다"(공식 소스
    `OAuth2AuthorizationCodeRequestAuthenticationProvider.isAuthorizationConsentRequired()`
    주석 확인, `requireAuthorizationConsent=true` 강제 등록 클라이언트도 예외 없음) — 버그가
    아니라 표준 OIDC IdP 관례와 일치하는 의도된 설계라 테스트 쪽에서 이 분기를 처리했다.
  - 테스트: `OidcSignInIntegrationSpec`(신규), `OAuthAppRegistrationServiceSpec`(신규),
    `JpaOAuth2AuthorizationServiceSpec`(OidcIdToken 왕복 케이스 추가) 전부 GREEN. 1라운드
    회귀 스위트(`McpOAuth2SecurityIntegrationSpec`/`ApiV1OAuth2SecurityIntegrationSpec`/
    `OAuthAppsAdminControllerSpec`/`UserViewControllerSpec` 등) 무변경 그대로 GREEN 재확인
    (-Dyona.it.db=h2).

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| PasswordEncoder 신규 도입 | 이 앱 전체에서 유일하게 Spring `PasswordEncoder`를 쓰는 지점 — 다른 모든 비밀/토큰은 자체 SHA-256+Base64 해시 | Spring Authorization Server의 client_secret_basic 검증이 프레임워크 계약상 요구하는 유일한 예외로 명시(위 설계 개요 4번 참고), 다른 곳에 전파하지 않음 |
| OAuth v1 토큰이 항상 전체 저장소 대상 | 프로젝트 단위로 좁힌 위임 접근이 불가능(PAT은 가능) | 실제 필요성이 확인되면 RFC8707 resource 파라미터를 프로젝트 단위로 세분화하는 방식으로 확장 가능(P3-07이 이미 이 방향을 언급) — 지금은 과도한 설계 |
| OIDC 2라운드 이월 | ~~"Sign in with yona"(순수 신원 델리게이션)가 이번 라운드엔 없음~~ **해소(2026-09-07 2라운드 완료)** | `.oidc { }` 활성화 + `ResourceIndicatorTokenCustomizer`의 ID 토큰 클레임 매핑으로 구현 완료. 상세: 아래 완료 로그, `docs/parity/tickets/p3-14.md` 2라운드 항목 참고 |

## 관련

- 백로그 원본: [`docs/parity/tickets/p3-14.md`](../../parity/tickets/p3-14.md)
- 관련 계획: [[p3-07-mcp-server]], [[p3-02-cli-and-rest-api]]
- 관련 소스: `config/oauth2server/*.kt`, `domain/oauth2server/*.kt`, `web/OAuthAppsAdminController.kt`,
  `config/ApiTokenAuthenticationFilter.kt`, `config/SecurityConfig.kt`
