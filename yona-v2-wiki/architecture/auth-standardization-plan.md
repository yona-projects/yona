---
title: 인증 구조 표준화 구현 계획
type: architecture
sources:
  - ../decisions.md
  - ../roadmap.md
  - src/main/kotlin/dev/yona/security/YonaAuthenticationFilter.kt
  - src/main/kotlin/dev/yona/security/LocalAuthenticator.kt
  - src/main/kotlin/dev/yona/security/LegacyPasswordHasher.kt
  - src/main/kotlin/dev/yona/security/RememberMeCookies.kt
  - src/main/kotlin/dev/yona/security/LoginSessionService.kt
  - src/main/kotlin/dev/yona/security/TwoFactorLogin.kt
  - src/main/kotlin/dev/yona/web/user/LoginController.kt
  - src/main/kotlin/dev/yona/web/user/PasswordResetController.kt
  - src/main/kotlin/dev/yona/web/access/BasicAuthResolver.kt
  - src/main/kotlin/dev/yona/config/SecurityConfig.kt
updated: 2026-11-10
---

# 인증 구조 표준화 구현 계획 (X-43)

> **상태: X-43 구현 완료(2026-11-10).** 1단계, 2단계(2-1~2-3), 3단계를 마쳤다. 2-4(인증 이벤트)와 2-5(`/-_-api` 체인 분리)는 하지 않았다(2-4는 선생님이 3단계로 바로 진행하라고 하셔서 접었고, 2-5는 선택 작업이었다). 아래 "1단계 결과", "2단계 결과", "3단계 결과"를 본다. 전체 시험(H2, PostgreSQL)은 아직 돌리지 않았다. 착수 승인(선생님 2026-11-10 "x-43 진행하자")과 계획 갱신은 같은 날이다. 구현은 1단계부터 시작하며 결정은 [decisions.md](../decisions.md) D-129에 기록했다. `authenticateBasic` 삭제도 선생님이 확인하셨다(2026-11-10). 남은 확인 사항은 없다.

## 1단계 결과 (2026-11-10)

| 작업 | 결과 |
|---|---|
| 1-1 특성화 | `UserPasswordCharacterizationTest` 6건(가입의 새 솔트, SSO 생성의 쓸 수 없는 비밀번호, 설정 변경·재설정 주소·관리자 임시 비밀번호의 솔트 유지, 두 확인 경로의 같은 판정)과 `LocalAuthenticatorLdapOrderTest` 4건(삭제는 LDAP에 묻기 전에, 계정이 없어도 LDAP이 판정, 안내 동반 실패, LDAP 성공 뒤 잠김). 현재 동작에서 바로 통과했다 |
| 1-2 부품 | `UserPasswordService`(`newCredentials`, `matches`, `matchesLenient`, `set`, `needsUpgrade`)와 `LegacyPasswordEncoder`(합성 표현 `{yona-sha256}솔트$해시`). 시험 8건, 6건 |
| 1-3 호출 지점 이동 | 7개 파일(`PasswordResetController`(2곳), `AccountService`(2곳), `SignupService`, `SsoAccountService`, `SiteSecret`, `LocalAuthenticator`, `LdapAuthService`)을 `UserPasswordService`로 옮겼다. 구조 시험 `PasswordHashingStructureTest`가 먼저 7개 파일을 목록으로 보이며 실패했고 옮긴 뒤 통과했다 |
| 1-4 상수 시간 비교 | `AccountService.isValidPassword`의 `==`를 `UserPasswordService.matches`(상수 시간)로. 구조 시험이 `user.password == ...` 비교를 막는다(널 검사 제외) |
| 1-5 제공자 | `YonaPasswordAuthenticationProvider`(일반 클래스)가 판정하고 `LocalAuthenticator`가 자기만의 `ProviderManager`로 쓴다. 거절은 `LoginDeniedException(result)`, 성공은 `YonaAuthentication`과 `details`의 `PasswordLoginDetails`. `authenticateBasic`과 그 시험 1건을 삭제했다(선생님 확인) |

**시험**: 영향받는 클래스(`security.*`, 로그인과 비밀번호와 가입과 프로필, LDAP, SSO, `SiteSecretTest`, `InitialDataLoaderTest` 등)가 모두 수정 없이 통과했다. **전체 시험(H2, PostgreSQL)은 아직 돌리지 않았다.** 선생님이 지시하실 때 돌린다.

**계획과 달라진 점**: `YonaUserDetailsService`는 만들지 않았다. `UserDetails`로는 v1.6의 판정(승인 대기 잠금은 비밀번호 전에, 맞은 뒤 잠김은 따로)과 "솔트가 없으면 예외" 규칙을 담을 수 없고, 빈으로 등록하면 스프링이 모든 보안 체인의 전역 인증 관리자를 바꾼다. 같은 이유로 제공자도 빈으로 등록하지 않았다(`AuthenticationProvider` 빈이 하나뿐이면 전역 관리자에 자동 등록된다). `LegacyPasswordEncoder`는 아직 쓰는 곳이 없다. 해시 강화(argon2id)에서 `DelegatingPasswordEncoder`로 붙일 자리로 만들어 두었다.

## 3단계 결과 (2026-11-10)

비밀번호 로그인(`POST /users/login`)의 판정과 응답 처리를 `LoginController`에서 스프링의 인증 필터로 옮겼다.

| 작업 | 결과 |
|---|---|
| 3-1 특성화 | `LoginFlowCharacterizationTest` 7건을 먼저 썼다: 로그인 성공 때 세션 식별자 회전, 실패 때 유지, 성공·실패·입력 누락 응답의 캐시 금지 헤더, 로그인 감사 기록의 내용(성공은 방법 `password`, 틀린 비밀번호는 없는 사용자와 같은 이유로 거부되지 않은 실패, 삭제와 인증 뒤 잠김은 거부). 현재 동작에서 바로 통과했다. 기존 `LoginControllerTest` 38건 등이 나머지 계약을 이미 고정한다 |
| 3-2 SSO 전용 관문 | SSO 전용 모드에서 사이트 관리자가 아닌 ID를 비밀번호 전에 거절하는 판정을 컨트롤러에서 `YonaPasswordAuthenticationProvider`로 옮겼다(시험 2건을 먼저 썼다) |
| 3-3 필터 | `PasswordLoginRequestMatcher`(시험 4건 먼저), `PasswordLoginFilter`(`AbstractAuthenticationProcessingFilter`), 처리기 둘(`PasswordLoginSuccessHandler`, `PasswordLoginFailureHandler`). 구조 시험 `LoginEntranceStructureTest` 3건을 먼저 썼다(Red) |
| 3-4 연결 | 보안 설정이 필터를 `UsernamePasswordAuthenticationFilter` 앞(요청 위조 검사 뒤)에 등록한다. `LoginController`는 입력이 빠진 요청의 400 응답과 로그아웃만 남았다. `LocalAuthenticator.authenticationManager`를 필터가 쓴다. `AuditCoverageTest`의 감사 대상 목록을 새 처리기로 옮겼다 |

**시험**: 로그인 관련 묶음(비밀번호, 가입, 프로필, 2단계 인증, 보안, LDAP, SSO, OAuth 재개, 사이트 비밀 키 등 대표 클래스 19개 + 로그인 묶음)이 모두 **수정 없이** 통과했다(48초, 46초). **실제 브라우저(Playwright) 16건**도 통과했다(`e2e/login-flow.mjs`): 틀린 비밀번호의 안내와 `redirectUrl` 유지, 로그인과 로그인 유지 쿠키(30일, HttpOnly), 쿠키만으로 로그인, 로그아웃, 팝업(AJAX) 로그인의 성공 `text/plain {}`, 실패 403 JSON, 입력 누락 400 JSON, 입력 누락 폼의 400.

**지킨 것(2단계 인증 우회 방지)**: 스프링이 성공한 인증을 세션에 저장하지 않게 했다(`NullSecurityContextRepository`). 세션 시작은 2단계 인증 관문과 세션 식별자 회전을 거치는 `LoginSessionService`가 맡고, 2단계 인증을 켠 사용자는 처리기가 스프링이 둔 보안 컨텍스트를 지운다. `TwoFactorLoginTest`가 수정 없이 통과했다.

**계획과 달라진 점**: `formLogin()` 설정 대신 `AbstractAuthenticationProcessingFilter`를 직접 썼다. `formLogin()`은 사용자 이름을 다듬고(trim) 입력이 빠진 요청도 같은 필터가 맡아 v1.6의 "입력이 빠지면 로그인 화면을 400으로 다시 그림"을 지킬 수 없고, 성공한 인증을 세션에 저장하는 설정이 기본이다.

## 2단계 결과 (2026-11-10, 2-1~2-3)

| 작업 | 결과 |
|---|---|
| 2-1 쿠키 | `RememberMeAuthenticationTest` 8건을 먼저 써서 현재 동작을 고정했다(유효한 쿠키로 로그인하고 세션에 기록, 서명 불일치, 비밀번호 변경, 기간 만료, 잠금과 삭제, 없는 사용자는 무효이고 쿠키를 지움, 이미 로그인한 세션은 쿠키를 보지 않음). 이어서 `YonaRememberMeServices` 시험 6건을 먼저 쓰고(Red) 구현했다. `autoLogin`이 쿠키 판정(2단계 인증 관문 포함)을, `loginSuccess`가 쿠키 만들기를, `loginFail`이 지우기를 맡고, `LoginCompletion`과 필터가 이를 쓴다 |
| 2-2 토큰 | `TokenAuthenticationProvidersTest` 7건을 먼저 썼다(Red). `ApiTokenAuthenticationProvider`(개인용 토큰, `scopesOf`로 범위 결정)와 `OAuthAccessTokenAuthenticationProvider`(대상 RFC 8707 확인)를 만들었고, 필터가 자기만의 `ProviderManager`로 쓴다. 대상이 맞지 않는 OAuth 토큰은 `InsufficientAuthenticationException`이라 실패로 세지 않는다 |
| 2-3 필터 축소 | `YonaAuthenticationFilter`는 자격 증명을 꺼내 제공자에 맡기고, 429 차단 검사와 세션 검증과 결과의 세션 기록만 한다. 생성자에서 `TwoFactorGate`와 `RememberMeCookies`가 빠지고 `YonaRememberMeServices` 하나가 들어갔다. 두 설정 파일의 미사용 `gate` 인자를 지웠다 |

**시험**: 보안 묶음, 토큰, OAuth, 감사, Git 토큰, MCP, 2단계 인증, 로그인 묶음을 모두 수정 없이 통과했다(44초). 전체 시험은 돌리지 않았다.

**2-4(인증 이벤트)를 재검토하는 이유**: 계획서는 "감사 기록과 시도 제한이 한 곳에서" 되게 한다고 했으나 코드를 확인하니 다음과 같다. (가) 토큰 실패 횟수와 감사(`TokenFailureLimiter`, `audit.tokenFailure`)를 부르는 곳이 필터, Git HTTP(`BasicAuthResolver`), MCP(`McpAccessFilter`), SSH 서버 네 곳인데 세 곳은 `ProviderManager`를 쓰지 않아 이벤트가 나오지 않는다. 필터 한 곳만 이벤트로 옮기면 오히려 흩어진다. (나) 로그인 감사는 성공이 흐름에 따라 달라지고(2단계 인증을 켠 사용자는 2단계를 마친 뒤, SSO는 처리기에서) 실패 이유는 `LoginController`의 SSO 전용 거절 같은 제공자 밖의 판정과 섞여 있어, 이벤트로 옮기면 한 곳이 아니라 두 곳이 된다. (다) `DefaultAuthenticationEventPublisher`는 `LoginDeniedException` 같은 자체 예외를 기본으로 이벤트로 내보내지 않아 매핑을 더해야 한다. 그래서 2-4를 그대로 하기보다 범위를 줄이는 것을 제안한다(선생님 확인 대기).

## 0. 2026-11-04 계획에서 바뀐 것

계획을 쓴 뒤 코드가 달라져 전제 일곱 가지를 고쳤다.

| # | 바뀐 것 | 계획에 주는 영향 |
|---|---|---|
| 1 | **로그인 유지 쿠키는 이미 재설계되었다(R-05 해결).** 이름은 `yona.token`, 값은 `로그인ID:만료시각:서명`이다. 서명은 사이트 비밀 키로 만든 HMAC-SHA256이며 사용자의 비밀번호 해시를 서명 재료로 쓰되 값에는 담지 않는다(`RememberMeCookies`) | 2단계의 "쿠키 재설계"가 필요 없다. 형식은 그대로 두고 `RememberMeServices`로 감싸기만 한다. 제약 표와 해시 강화 선행 조건을 고쳤다 |
| 2 | **소셜 로그인을 제거했다(X-48, D-127).** `SocialLoginController`와 소셜 세션이 없다 | 쿠키를 쓰는 곳이 두 곳에서 한 곳(`LoginCompletion`)으로 줄었다 |
| 3 | 비밀번호 해시를 만들고 확인하는 곳이 정리되었다(아래 3절 표) | 1단계 이동 대상이 확정되었다. `InitialDataLoader`는 해시를 호출하지 않고 문자열 상수이다 |
| 4 | 토큰 인증은 `ApiTokenService.scopesOf(token)`으로 범위를 정한다(D-126). 옛 토큰은 범위 행이 없고 범위 제한이 없다 | 토큰 제공자가 같은 결과를 내야 하고 시험으로 고정한다 |
| 5 | SSO로 연결된 사용자는 비밀번호를 만들 수 없다(D-128, `PasswordResetController.ssoBlocked`) | 비밀번호 정책은 `UserPasswordService`가 아니라 호출하는 쪽에 둔다(5절 1-3) |
| 6 | 시험 규칙이 바뀌었다: 전체 시험은 선생님이 돌리라고 하실 때만 돌린다 | 7절을 고쳤다. 단계 안에서는 영향받는 클래스만 돌린다 |
| 7 | `LocalAuthenticator.authenticateBasic`은 아직 운영 코드에서 호출하는 곳이 없다(`LdapAuthServiceTest`만 씀) | **삭제를 선생님이 확인하셨다(2026-11-10).** 1-5에서 그 시험 두 건과 함께 지운다 |

## 1. 목적과 범위

사용자에게 보이는 변화 없이, 흩어진 인증 코드를 스프링 시큐리티의 표준 부품 위에 정리한다. 얻는 것은 개발자 쪽이다.

- 비밀번호 해시를 만들고 확인하는 곳을 한 곳으로 모은다. 이것이 해시 강화(argon2id, [decisions.md](../decisions.md) "재결정 대기" 절)의 선행 조건이다.
- 로그인 성공과 실패가 표준 인증 이벤트로 흘러, 감사 기록과 시도 제한이 한 곳에서 일관되게 걸린다.
- 비밀번호, LDAP, 토큰, 로그인 유지 쿠키가 같은 `AuthenticationManager` 뒤에서 처리된다.

**범위 밖**: 데이터 형식 변경(비밀번호 해시, `password_salt` 열, 쿠키 값), 로그인 화면과 문구의 변경, 해시 강화 자체(별도 선택 기능), SSO 설정 화면과 SSO 체인의 인증 관리자 통합, 권한 판정(`AccessControl`).

## 2. 지켜야 할 제약

| 제약 | 이유 |
|---|---|
| `password`, `password_salt` 열과 Shiro SHA-256 1024회 해시를 그대로 둔다 | v1.6 데이터를 그대로 검증해야 하고, 이관 명령이 필요 없어야 한다 |
| 로그인 유지 쿠키 `yona.token`의 이름, 값 형식(`로그인ID:만료시각:서명`), 30일, `HttpOnly`, `SameSite=Lax`를 그대로 둔다 | 이미 R-05를 고친 현재 계약이다. 이 항목에서 다시 바꾸지 않는다 |
| 로그인 판정 순서와 실패 문구를 바꾸지 않는다 | `LocalAuthenticator`가 v1.6 `UserApp.loginByFormRequest`의 순서를 따른다 |
| 2단계 인증 관문(`TwoFactorGate`)을 우회하는 입구를 만들지 않는다 | `LoginSessionService.start`가 우회를 예외로 막고 있다(X-2, D-69) |
| 토큰의 범위 판정(`scopesOf`)과 `/-_-api`의 세션 무시 규칙을 바꾸지 않는다 | D-126, D-36 |
| 기존 시험이 단계마다 수정 없이 통과해야 한다 | 이 항목의 합격 기준이다 |

## 3. 현재 구조 (2026-11-10 코드에서 확인한 사실)

**비밀번호 해시를 다루는 곳** (`LegacyPasswordHasher` 호출, 운영 코드):

| 구분 | 위치 |
|---|---|
| 만들기와 바꾸기 | `SignupService.create`(가입), `AccountService.resetPassword`(설정 화면에서 변경), `PasswordResetController`(재설정 주소 사용, 관리자 임시 비밀번호), `SiteSecret`(관리자 최초 설정), `SsoAccountService.create`(무작위 비밀번호) |
| 확인 | `LocalAuthenticator.passwordMatches`, `LdapAuthService`(로컬 폴백 확인), `AccountService.isValidPassword`(`==` 비교, 상수 시간이 아님) |
| 해시 상수 | `InitialDataLoader`가 초기 관리자의 해시와 솔트 문자열을 직접 적는다(호출 아님) |

모두 8개 파일이다. **시험 코드는 사용자를 만들 때 `LegacyPasswordHasher`를 널리 쓴다**(관련 시험 파일이 100개를 넘는다). 그래서 1단계의 구조 시험은 `src/main`만 검사한다.

**로그인 유지 쿠키**: 만들기는 `LoginCompletion.complete`(`TwoFactorLogin.kt`) 한 곳이고 `RememberMeCookies.create`를 부른다. 읽기는 `YonaAuthenticationFilter.authenticateByRememberMeCookie` 한 곳이며 `RememberMeCookies.verify`를 부른다. 지우기는 필터의 `discardRememberMeCookie`이다.

**필터와 체인**: 보안 필터 체인이 세 개다(인가 서버 `@Order(1)`, SSO `@Order(2)` `/sso/**`, 기본 `SecurityConfig`). 세 체인 모두 `formLogin`, `httpBasic`, `logout`을 끈다. 기본 체인의 `YonaAuthenticationFilter`가 세션 검증, API 토큰(`/-_-api`), OAuth 접근 토큰, 쿠키를 경로에 따라 갈라 처리하고, `McpAccessFilter`가 `/mcp`를 맡는다. `AuthenticationManager`와 `UserDetailsService`는 없다.

**토큰 인증**(`authenticateApi`): 순서는 헤더 추출, 실패한 주소 차단(429), OAuth 접근 토큰(접두사)이면 `OAuthAccessTokenService.authenticate`와 대상(RFC 8707) 검사, 아니면 `ApiTokenService.authenticate`이다. 성공하면 `YonaAuthentication.of(owner, viaToken = true, scopes = apiTokens.scopesOf(token), tokenId = ...)`를 심고, 실패하면 `TokenFailureLimiter`와 감사(`audit.tokenFailure`)를 부른다.

**로그인 판정**: `LocalAuthenticator.authenticate`가 `LoginResult`(성공, 승인 대기 잠금, 인증 뒤 잠금, 삭제, SSO 전용, 잘못된 자격, 안내 동반 실패)를 돌려주고, `LoginController`가 이를 화면 동작으로 옮긴다. 성공 뒤는 `TwoFactorLogin.begin`(2단계 대기) 또는 `LoginSessionService.start`(세션 시작)로 간다. LDAP을 켠 사이트에서는 계정이 없어도 LDAP으로 시도한다.

**세션을 시작하는 곳**: `LoginController`(폼, AJAX), `LoginCompletion`(2단계 마친 뒤), `SignupController`(가입 직후), `AccountController`(이메일 인증 뒤), SSO 로그인 처리기(`SsoLoginHandlers`), OAuth 인가 재개 경로이다. 모두 `LoginSessionService.start`를 거치며 여기가 2단계 인증 우회를 막는다.

**죽은 코드 후보**: `LocalAuthenticator.authenticateBasic`은 D-70 이후 Git HTTP가 비밀번호를 받지 않아 운영 코드에서 호출하는 곳이 없다. `LdapAuthServiceTest`만 쓴다.

## 4. 목표 구조

```
요청
 ├─ /-_-api/**   API 토큰·OAuth 토큰 ── ApiTokenAuthenticationProvider ┐
 ├─ /mcp         (기존 McpAccessFilter 유지)                           │
 ├─ /sso/**      (기존 SSO 체인 유지, 이벤트 경로만 맞춤)               ├─ AuthenticationManager(ProviderManager)
 └─ 그 밖(웹)     세션 + 로그인 유지 쿠키 ── YonaRememberMeServices     │      + DefaultAuthenticationEventPublisher
                  비밀번호 로그인 ── YonaPasswordAuthenticationProvider ┤      → 감사 기록, 시도 제한 리스너
                  (LDAP은 같은 제공자가 LocalAuthenticator 규칙으로 위임) ┘
```

핵심 부품:

- **`UserPasswordService`**: 비밀번호를 만들고 확인하는 유일한 입구(`matches(user, raw)`, `set(user, raw)`, `newCredentials(raw)`(솔트와 해시), `needsUpgrade(user)`). 해시 알고리즘은 이 안에만 있다. **정책은 담지 않는다**(SSO 사용자 차단, 최소 길이 등은 호출하는 쪽의 몫이다).
- **`LegacyPasswordEncoder`**: v1.6은 솔트를 별도 열에 두므로 스프링 `PasswordEncoder`(인코딩 문자열 하나만 받음)에 그대로 맞지 않는다. 두 열을 합친 **메모리 안의 합성 표현**(`{yona-sha256}솔트$해시`)을 인코딩 문자열로 쓰고 저장하지 않는다. 나중에 argon2id는 솔트가 문자열 안에 들어 있어 같은 구조에 `DelegatingPasswordEncoder`로 붙는다.
- **`YonaUserDetailsService`**: 로그인 ID 또는 이메일로 찾는다(`findByLoginKey`). 상태는 `accountLocked`, `enabled` 표지로 옮긴다.
- **`YonaPasswordAuthenticationProvider`**: 판정 순서를 v1.6과 같게 유지한다. 결과는 예외의 종류(코드)로 구분해 `LoginResult`로 되돌려 주므로 화면 문구가 바뀌지 않는다.
- **`YonaRememberMeServices`**: 쿠키 형식은 현재 그대로이고 `RememberMeCookies`를 감싼다. `TwoFactorGate`(알려진 기기 쿠키) 확인을 `autoLogin` 안에서 한다. 만들기 한 곳이 `loginSuccess`를 부르게 한다.
- **`ApiTokenAuthenticationProvider`**: 개인용 토큰과 OAuth 접근 토큰을 감싼다. 범위는 `scopesOf`와 OAuth의 `grant.scopes`를 그대로 싣는다.
- **인증 이벤트 리스너**: 성공과 실패 이벤트에서 감사 기록과 `TokenFailureLimiter`, `TwoFactorAttempts`를 부른다. 감사 기록 형식은 바꾸지 않는다.

SSO는 이미 스프링의 `oauth2Login`, `saml2Login`을 쓰는 전용 체인이다. 관리자를 합치는 이득이 작아서(자체 성공 처리기가 `LoginSessionService`를 부른다) **관리자는 합치지 않고 성공과 실패가 같은 이벤트 경로로 흐르게만** 한다.

**인증 객체 원칙(선생님 질문 2026-11-10 반영).** 로그인 상태는 이미 스프링의 `Authentication`이다. `YonaAuthentication`은 `AbstractAuthenticationToken`을 상속하고 스프링의 `SecurityContext`에 담겨 `HttpSessionSecurityContextRepository`로 세션에 저장된다(`LoginSessionService.start`). 그래서 비밀번호, 쿠키, 토큰, SSO가 모두 **`YonaAuthentication` 하나를 결과로** 내도록 맞춘다(자체 인증 종류를 새로 만들지 않는다). 다만 SSO 쪽은 스프링이 만든 `OAuth2AuthenticationToken`, `Saml2Authentication`을 그대로 세션에 두지 않고, 성공 처리기가 `YonaAuthentication`으로 바꿔 심는다(지금처럼). 모든 호출 지점이 `as? YonaAuthentication`으로 읽고 있어, 스프링 표준 토큰으로 갈아타면 영향이 크고 이득이 작다. 범위(`scopes`)를 표준 권한(`SCOPE_` 접두사의 `GrantedAuthority`)으로 옮기는 현대화는 선택 사항으로 남기며 이 항목에서는 하지 않는다. SSO 체인이 CSRF 토큰을 쓰지 않는 것은 맞다: OIDC 콜백은 `state`와 PKCE가 위조를 막고, SAML 응답은 IdP가 다른 사이트에서 POST하므로 토큰을 받을 수 없으며 서명 검증과 재사용 방지가 대신한다.

**합치는 것이 가능한지는 확인했다(2026-11-10, Spring Security 7.1.1 클래스 검사).** 세 수준이 있다. (가) 같은 이벤트 경로: 가능하고 이 계획이 택한 것이다. (나) 같은 인증 관리자: SAML은 `Saml2LoginConfigurer.authenticationManager(...)`가 있어 이미 자체 `ProviderManager`를 넣고 있으므로 공용 관리자의 제공자로 옮길 수 있다. OIDC는 `OAuth2LoginConfigurer`에 그런 설정이 없어 스프링이 안에서 만드는 제공자를 쓰며, `HttpSecurity.authenticationManager(...)`로 공용 관리자를 부모로 줄 수 있을 뿐이다. OIDC 제공자 자체를 우리 관리자로 옮기려면 로그인 필터를 직접 구성해야 해서 권하지 않는다. (다) 체인을 하나로: 가능하나 SAML 응답 수신(IdP의 교차 사이트 POST)을 위해 CSRF 예외가 필요하고, `YonaAuthenticationFilter`(세션 검증, 토큰)가 `/sso/**`에 끼어들지 않게 막아야 한다. 체인을 나눈 근거로 문서에 있는 것은 "기존 `YonaAuthenticationFilter`와 섞이지 않는다"이다(D-80 1번, `enterprise-sso.md`). CSRF 예외가 필요하다는 점은 코드(`SsoSecurityConfig`가 CSRF를 끔)에서 확인했지만 문서에 적힌 이유는 아니다. 이득보다 위험이 크다.

## 5. 단계와 작업 분해

각 작업은 "시험 먼저(실패 또는 현재 동작 고정), 구현, 영향받은 클래스 통과"의 한 묶음이며 묶음마다 커밋한다.

### 1단계: 비밀번호 처리를 한 곳으로 (순수 정리, 위험 낮음)

| 작업 | 시험 먼저 | 구현 |
|---|---|---|
| 1-1 특성화 | `UserPasswordCharacterizationTest`: 가입(새 솔트), 설정 변경(솔트 유지), 재설정 주소(솔트 유지), 관리자 임시 비밀번호(솔트 유지, 여섯 글자), SSO 생성(무작위, 로컬 로그인 불가), 최초 관리자 설정. `LocalAuthenticatorTest`에 빠진 판정 순서 보강(승인제 잠금은 비밀번호를 보기 전에, 삭제, 맞은 뒤 잠금, LDAP과 삭제) | 없음(시험만, 현재 동작 고정) |
| 1-2 부품 | `UserPasswordServiceTest`, `LegacyPasswordEncoderTest`: `matches`, `set`, 솔트나 비밀번호가 없으면 v1.6과 같은 `IllegalArgumentException("Bad password or passwordSalt!")`, 합성 표현의 왕복, `LegacyPasswordHasherTest`의 고정 벡터와 같은 결과 | `UserPasswordService`, `LegacyPasswordEncoder` |
| 1-3 호출 지점 이동 | **구조 시험**(소스 훑기, `src/main`만): `LegacyPasswordHasher`를 `UserPasswordService`와 `LegacyPasswordEncoder` 밖에서 부르면 실패. 처음에는 8개 파일을 허용 목록에 두고 파일 하나씩 옮길 때마다 목록에서 지운다 | `PasswordResetController`(두 곳), `AccountService`(두 곳), `SignupService`, `SsoAccountService`, `SiteSecret`, `LocalAuthenticator`, `LdapAuthService`. SSO 차단 정책(`ssoBlocked`)은 컨트롤러에 그대로 둔다 |
| 1-4 상수 시간 비교 | 같은 입력에 같은 결과(특성화), 비교가 `MessageDigest.isEqual`을 거치는지는 구조 시험 | `AccountService.isValidPassword`를 `UserPasswordService.matches`로 |
| 1-5 제공자 | `YonaPasswordAuthenticationProviderTest`: 6가지 판정 결과가 `LoginResult`와 일대일로 대응(예외 코드에서 결과로). `authenticateBasic`을 부르는 곳이 없음을 구조 시험으로 먼저 확인 | `YonaUserDetailsService`, `YonaPasswordAuthenticationProvider`를 만들고 `LocalAuthenticator.authenticate`가 이를 거치게 한다. `LoginResult`와 `LoginController`는 그대로. **`LocalAuthenticator.authenticateBasic`과 `LdapAuthServiceTest`의 두 호출 시험(`gitldap`, `gitbad`)을 삭제한다(선생님 확인)** |

완료 기준: 기존 시험이 수정 없이 통과, 구조 시험 통과(`LegacyPasswordHasher` 직접 호출 0곳), 새 시험 통과.

### 2단계: 토큰과 쿠키를 제공자와 `RememberMeServices`로

| 작업 | 시험 먼저 | 구현 |
|---|---|---|
| 2-1 쿠키 | 필터 수준 특성화 시험: 정상 쿠키로 로그인, 만료, 서명 불일치, 비밀번호를 바꾸면 무효, 잠긴 사용자는 세션에 기록하지 않음, 알려진 기기 쿠키가 필요한 2단계 사용자는 쿠키만으로 로그인할 수 없음(`RememberMeCookiesTest`는 이미 있다) | `YonaRememberMeServices`(읽기 `autoLogin`, 만들기 `loginSuccess`, 지우기 `loginFail`). `LoginCompletion`과 필터가 이를 쓰게 한다 |
| 2-2 토큰 | 경로별 우선순위 시험(세션만, 토큰만, 둘 다): `/-_-api`는 세션을 무시, 옛 토큰은 `scopesOf`로 범위 제한 없음, OAuth 토큰의 대상 검사, 실패 횟수 429 | `ApiTokenAuthenticationProvider`. 차단 검사(429)는 인증 전이라 필터에 남긴다 |
| 2-3 필터 축소 | 기존 시험이 그대로 통과 | `YonaAuthenticationFilter`는 자격 증명을 꺼내 `AuthenticationManager`를 부르는 일과 세션 검증(`SessionUserCache`)만 남긴다 |
| 2-4 이벤트 | 이관 전과 후의 감사 행 수와 내용이 같음(`AuditAccountTest`, `AuditApiTest` 보강), 실패 제한이 한 번만 세어짐 | 인증 이벤트 리스너가 감사(`audit.tokenFailure`, 로그인 감사)와 `TokenFailureLimiter`를 부른다. 필터와 컨트롤러가 직접 부르던 곳을 지운다 |
| 2-5 체인 분리(선택) | `/-_-api`가 세션과 쿠키를 무시하는 규칙과 v1.6 별칭 경로 규칙의 경로별 시험 | `/-_-api` 토큰 전용·무상태 체인 분리. **2-4까지 끝난 뒤 이득을 따져 여쭙고 진행한다** |

완료 기준: 기존 시험이 수정 없이 통과, 세션·토큰·쿠키 우선순위 시험 통과, 감사 기록 내용 동일.

### 3단계: `formLogin`으로 로그인 입구 교체 (위험 높음, 2단계 뒤에 재결정)

`LoginController`의 폼 로그인(`POST /users/login`)과 AJAX 팝업 로그인을 스프링 `formLogin`의 처리 URL, 성공 처리기, 실패 처리기로 옮긴다. v1.6의 리다이렉트 규칙(`redirectUrl`), 안내 문구(flash), 2단계 인증 분기(`TwoFactorLogin.begin`), SSO 전용 모드, 승인제 안내를 처리기가 그대로 수행해야 한다. 입구가 바뀐 만큼 2단계 인증 우회 위험이 있으므로 `LoginSessionService.start`의 점검은 유지한다. **2단계를 마친 뒤 이득을 다시 따져 선생님께 여쭙고 진행한다.**

## 6. 위험과 대응

| 위험 | 대응 |
|---|---|
| 판정 순서나 문구가 미묘하게 바뀜 | 1-1이 특성화 시험이고 `LoginControllerTest`, `LocalAuthenticatorTest` 등 기존 시험이 수정 없이 통과해야 한다 |
| 2단계 인증 우회 | 2-1에서 "쿠키만으로, 알려진 기기 없이" 우회 시험을 먼저 둔다 |
| 토큰 범위 판정이 달라짐(옛 토큰 범위 제한 없음) | 2-2의 시험이 `scopesOf` 결과를 고정한다. D-126의 e2e(`e2e/token-scope.mjs`)를 단계 끝에 다시 돌린다 |
| 이벤트 리스너가 감사를 이중으로 기록하거나 빠뜨림 | 2-4의 전후 비교 시험 |
| 호출 지점을 놓침 | 1-3의 구조 시험(소스 훑기)으로 막는다 |
| 시험 코드가 `LegacyPasswordHasher`를 널리 씀 | 구조 시험은 `src/main`만 본다. 시험 쪽은 건드리지 않는다 |

## 7. 시험 계획 (TDD)

- 각 작업의 첫 커밋은 실패하는 시험 또는 현재 동작을 고정하는 특성화 시험이다.
- 작업 안에서는 영향받는 클래스만 돌린다(`security.*`가 아니라 클래스 이름으로 지정). **전체 시험(H2, PostgreSQL)은 선생님이 돌리라고 하실 때만 돌린다.** 단계를 마칠 때 전체 시험이 필요하다고 보고드리고 지시를 받는다.
- 화면 확인(Playwright)은 단계를 마칠 때 한 번만 한다: 로그인 화면(비밀번호, 로그인 유지, 2단계, SSO), 로그아웃. 로그인 흐름은 `e2e/sso-flow.tmp.mjs` 같은 도우미를 재사용한다.

## 8. 위키와 문서

단계마다 이 문서의 상태와 결과를 갱신하고, 결정은 `decisions.md`에, 에이전트가 정한 구현 판단은 같은 파일의 판단 표에 기록한다. 소요 시간은 `log.md`에 적는다. 사용자에게 보이는 구조가 달라지지 않으므로 `differences-from-v16.md`는 바뀔 때만 적는다.

## 9. 다른 항목과의 관계

- **해시 강화(argon2id)**: 1단계가 끝나면 시작할 수 있다. 이전 계획의 선행 조건이던 "쿠키 재설계"는 R-05로 이미 끝났다. 다만 **쿠키 서명 재료가 `user.password`(해시)**이므로 해시 알고리즘을 바꾸면 기존 로그인 유지 쿠키가 모두 무효가 된다. 한 번 다시 로그인하면 되므로 허용할지 해시 강화 때 정한다.
- **X-42(SSO 그룹과 역할 매핑)**: 이벤트 경로가 모이면 로그인 시점의 처리 연결이 쉬워지나 필수 선행은 아니다.
- **X-44(팀)**: 권한 판정(`AccessControl`)을 바꾸고 인증은 건드리지 않아 독립이다.
- **D-128(SSO 비밀번호 차단)**: 정책은 호출하는 쪽에 남긴다. `UserPasswordService`는 해시와 확인만 한다.

## 10. 결정과 확인

선생님이 "진행하자"고 하셨으므로 이전 계획의 질문 다섯 가지는 권장안으로 정하고 알려 드린다. 다르게 하고 싶으시면 말씀해 주시면 바꾼다.

| # | 사항 | 적용한 것 |
|---|---|---|
| 1 | 시기 | 지금 1단계부터 진행한다. 릴리스에 어느 단계까지 넣을지는 2단계를 마친 뒤 정한다. 단계마다 독립적으로 병합할 수 있다 |
| 2 | SSO 범위 | 관리자를 합치지 않고 이벤트 경로만 맞춘다(권장안) |
| 3 | `authenticateBasic` 삭제 | **선생님이 삭제를 확인하셨다(2026-11-10).** 1-5에서 지운다 |
| 4 | 3단계 | 2단계를 마친 뒤 이득을 따져 다시 여쭙는다(권장안) |
| 5 | 쿠키 | 이미 R-05로 재설계되어 이 항목에서는 형식을 바꾸지 않고 감싸기만 한다 |
| 6 | LDAP | `LdapAuthService`(JNDI)를 그대로 두고 `AuthenticationProvider`로 감싼다(선생님 2026-11-10 "LDAP은 그렇게 하고"). `spring-security-ldap`으로 바꾸는 것은 선택 작업으로 남기고 2단계 뒤에 이득을 보고 정한다 |
| 7 | 인증 객체 | 모든 입구가 `YonaAuthentication` 하나를 낸다. 스프링 표준 토큰으로 갈아타지 않는다(위 4절 원칙) |

## 11. 규모 (참고)

1단계는 호출 지점 8개 파일과 새 클래스 5개와 새 시험 클래스 약 5개, 2단계는 필터, 쿠키, 토큰 제공자, 이벤트 리스너 약 8개 파일과 새 클래스 4개, 3단계는 `LoginController`와 처리기 약 5개 파일이다. 단계마다 독립적으로 병합할 수 있어 어느 단계에서 멈춰도 얻은 이득이 남는다.
