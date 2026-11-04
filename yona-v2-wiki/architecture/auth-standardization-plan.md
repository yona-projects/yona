---
title: 인증 구조 표준화 구현 계획
type: architecture
sources:
  - ../decisions.md
  - ../roadmap.md
  - src/main/kotlin/dev/yona/security/YonaAuthenticationFilter.kt
  - src/main/kotlin/dev/yona/security/LocalAuthenticator.kt
  - src/main/kotlin/dev/yona/security/LegacyPasswordHasher.kt
  - src/main/kotlin/dev/yona/security/LoginSessionService.kt
  - src/main/kotlin/dev/yona/security/TwoFactorLogin.kt
  - src/main/kotlin/dev/yona/web/user/LoginController.kt
  - src/main/kotlin/dev/yona/web/access/BasicAuthResolver.kt
  - src/main/kotlin/dev/yona/config/SecurityConfig.kt
updated: 2026-11-04
---

# 인증 구조 표준화 구현 계획 (X-43)

> **상태: 계획 작성 완료, 착수 전(2026-11-04).** 선생님이 2026-11-02에 "로드맵에 올리고, 할지는 나중에 결정"으로 정하셨다. 이 문서는 결정을 돕기 위한 계획이며 구현은 선생님의 승인 뒤에 시작한다. 승인할 사항은 [10절](#10-선생님께-여쭐-것)에 모았다.

## 1. 목적과 범위

사용자에게 보이는 변화 없이, 흩어진 인증 코드를 스프링 시큐리티의 표준 부품 위에 정리한다. 얻는 것은 개발자 쪽이다.

- 비밀번호 해시를 만들고 확인하는 곳을 한 곳으로 모은다. 이것이 해시 강화(argon2id, [decisions.md](../decisions.md) "재결정 대기" 절)의 선행 조건이다.
- 로그인 성공과 실패가 표준 인증 이벤트로 흘러, 감사 기록과 시도 제한이 한 곳에서 일관되게 걸린다.
- 비밀번호, LDAP, 토큰, 로그인 유지 쿠키가 같은 `AuthenticationManager` 뒤에서 처리된다.

**범위 밖**: 데이터 형식 변경(비밀번호 해시, `password_salt` 열, 쿠키 값), 로그인 화면과 문구의 변경, 해시 강화 자체(별도 선택 기능), SSO 설정 화면, 권한 판정(`AccessControl`).

## 2. 지켜야 할 제약

| 제약 | 이유 |
|---|---|
| `password`, `password_salt` 열과 Shiro SHA-256 1024회 해시를 그대로 둔다 | v1.6 데이터를 그대로 검증해야 하고, 마이그레이션 명령이 필요 없어야 한다 |
| 로그인 유지 쿠키 `yobi.token`의 이름과 값(`로그인ID:해시된패스워드`)을 그대로 둔다 | v1.6 계약이다. 해시를 담는 결함(R-05)도 이 항목에서는 건드리지 않는다 |
| 로그인 판정 순서와 실패 문구를 바꾸지 않는다 | `LocalAuthenticator`가 v1.6 `UserApp.loginByFormRequest`의 순서를 따른다 |
| 2단계 인증 관문(`TwoFactorGate`)을 우회하는 입구를 만들지 않는다 | `LoginSessionService.start`가 우회를 예외로 막고 있다(X-2, D-69) |
| 기존 시험이 단계마다 수정 없이 통과해야 한다 | 이 항목의 합격 기준이다 |

## 3. 현재 구조 (코드에서 확인한 사실)

**비밀번호 해시를 다루는 곳** (`LegacyPasswordHasher` 호출):

| 구분 | 위치 |
|---|---|
| 만들기와 바꾸기 | `SignupService.create`, `AccountService`(변경), `PasswordResetController`(두 곳), `SiteSecret`(관리자 최초 설정), `SsoAccountService.create`(무작위 비밀번호), `LdapAuthService`(로컬 동기화) |
| 확인 | `LocalAuthenticator.passwordMatches`, `LdapAuthService`(폴백과 동기화 확인), `AccountService`(`==`로 비교, 상수 시간이 아님) |
| 해시 고정값 | `InitialDataLoader`가 초기 관리자의 해시와 솔트 문자열을 직접 적는다 |

**로그인 유지 쿠키**: 읽기는 `YonaAuthenticationFilter.authenticateByRememberMeCookie` 한 곳, 쓰기는 `TwoFactorLogin.complete`와 `SocialLoginController` 두 곳이다. 쿠키 값을 `"${user.loginId}:${user.password}"`로 각자 조립한다.

**필터와 체인**: 보안 필터 체인이 세 개다(기본 `SecurityConfig`, SSO `SsoSecurityConfig`, 인가 서버 `AuthorizationServerConfig`). 기본 체인은 `formLogin`, `httpBasic`, `logout`을 모두 끄고 `YonaAuthenticationFilter` 하나가 세션 검증, API 토큰(`/-_-api`), 쿠키를 경로에 따라 갈라 처리한다. 인증 관리자(`AuthenticationManager`)와 `UserDetailsService`는 없다.

**로그인 판정**: `LocalAuthenticator`가 `LoginResult`(성공, 잠금, 삭제, SSO 전용, 잘못된 자격, 안내 동반 실패)를 돌려주고, `LoginController`가 이를 화면 동작으로 옮긴다. 성공 뒤는 `TwoFactorLogin.begin`(2단계 대기) 또는 `LoginSessionService.start`(세션 시작)로 간다.

**이미 정리된 것**: Git과 Subversion HTTP의 Basic 인증은 비밀번호 칸에 개인용 API 토큰만 받는다(D-70). 그래서 `LocalAuthenticator.authenticateBasic`을 운영 코드에서 호출하는 곳이 없고 `LdapAuthServiceTest`만 쓴다(2026-11-04 확인). 정리 후보이다(10절 3번).

## 4. 목표 구조

```
요청
 ├─ /-_-api/**   API 토큰 전용 체인  ── ApiTokenAuthenticationProvider ┐
 ├─ /mcp         (기존 McpAccessFilter 유지)                             │
 ├─ /sso/**      (기존 SSO 체인 유지)                                    ├─ AuthenticationManager(ProviderManager)
 └─ 그 밖(웹)     세션 + 로그인 유지 쿠키 ── YonaRememberMeServices      │      + DefaultAuthenticationEventPublisher
                  비밀번호 로그인 ── YonaPasswordAuthenticationProvider ┤      → 감사 기록, 시도 제한 리스너
                  LDAP 로그인  ── LdapAuthenticationProvider ───────────┘
```

핵심 부품:

- **`UserPasswordService`**: 비밀번호를 만들고 확인하는 유일한 입구(`matches(user, raw)`, `set(user, raw)`, `needsUpgrade(user)`). 해시 알고리즘은 이 안에만 있다. v1.6은 솔트를 별도 열에 두므로 스프링의 `PasswordEncoder`(인코딩 문자열 하나만 받음)에 그대로 맞지 않는다. 그래서 `LegacyPasswordEncoder`는 두 열을 합친 **메모리 안의 합성 표현**(`{yona-sha256}솔트$해시`)을 인코딩 문자열로 쓰고, 저장하지 않는다. 나중에 argon2id는 솔트가 문자열 안에 들어 있어 같은 `PasswordEncoder` 구조에 `DelegatingPasswordEncoder`로 붙는다.
- **`YonaUserDetailsService`**: 로그인 ID 또는 이메일로 찾는다(`findByLoginKey`). 상태는 `accountLocked`, `enabled` 표지로 옮긴다.
- **`YonaPasswordAuthenticationProvider`**: 판정 순서를 v1.6과 같게 유지한다. 승인제 잠금은 비밀번호를 보기 전에, 삭제는 그다음에, 비밀번호가 맞은 뒤의 잠금은 별도로 구분한다. 결과는 예외의 종류(또는 코드)로 구분해 `LoginResult`로 되돌려 주므로 화면 문구가 바뀌지 않는다.
- **`YonaRememberMeServices`**: 쿠키 형식은 v1.6 그대로이고, `TwoFactorGate`(알려진 기기 쿠키)를 `autoLogin` 안에서 확인한다. 쓰기 두 곳이 `loginSuccess`를 부르게 한다.
- **인증 이벤트 리스너**: 성공과 실패 이벤트에서 감사 기록과 `TokenFailureLimiter`, `TwoFactorAttempts`를 부른다. 감사 기록 형식은 바꾸지 않는다.

SSO는 이미 스프링의 `oauth2Login`, `saml2Login`을 쓰는 전용 체인이다. 같은 관리자에 합치는 이득이 작아서(자체 성공 처리기가 `LoginSessionService`를 부른다) **관리자는 합치지 않고, 성공과 실패가 같은 이벤트 경로로 흐르게만** 한다. 로드맵 설명의 "`oauth2Login`과 `saml2Login`이 같은 인증 관리자에 붙음"은 이 범위로 줄이자고 제안한다(10절 2번).

## 5. 단계

### 1단계: 비밀번호 처리를 한 곳으로 (순수 정리, 위험 낮음)

1. 특성화 시험을 먼저 보강한다: 해시 고정 벡터(`LegacyPasswordHasherTest`가 있음), `AccountService`의 비교, 판정 순서 6가지(`LocalAuthenticatorTest`), LDAP 폴백 동기화(`LdapLoginTest`).
2. `UserPasswordService`와 `LegacyPasswordEncoder`를 만들고, 3절의 호출 지점을 모두 옮긴다.
3. **구조 시험**: `LegacyPasswordHasher`를 `UserPasswordService` 밖에서 부르면 실패하는 시험(소스 훑기)을 넣어 재발을 막는다.
4. `AccountService`의 `==` 비교를 상수 시간 비교로 바꾼다(동작 동일, 시간차 공격 방지).
5. `YonaUserDetailsService`와 `YonaPasswordAuthenticationProvider`를 만들고 `LocalAuthenticator`가 이를 거치게 한다. `LoginResult`와 `LoginController`는 그대로 둔다.

완료 기준: 기존 시험 전체 통과, 새 구조 시험 통과, `LegacyPasswordHasher` 직접 호출 0곳(`UserPasswordService` 제외).

### 2단계: 토큰과 쿠키를 제공자와 `RememberMeServices`로

1. 로그인 유지 쿠키의 쓰기 두 곳과 읽기 한 곳을 `YonaRememberMeServices`로 옮긴다(쿠키 이름, 값, 30일, `Path`, 잘못된 값이면 지움, 잠긴 계정은 세션 미기록 동작을 시험으로 고정한 뒤 이동).
2. API 토큰(`ApiTokenService.authenticate`)과 OAuth 접근 토큰(`OAuthAccessTokenService`)을 `AuthenticationProvider`로 감싼다. `/-_-api`는 토큰 전용·무상태 체인으로 분리하고, 세션과 쿠키를 무시하는 현재 규칙을 시험으로 확인한다.
3. `YonaAuthenticationFilter`에서 세션 검증(`SessionUserCache`)만 남기고 나머지를 제공자와 서비스로 넘긴다.
4. 인증 이벤트(`DefaultAuthenticationEventPublisher`) 리스너로 감사와 시도 제한을 모은다. `AuditBoundary`, `TokenFailureLimiter`를 직접 부르던 곳이 줄어드는지 센다.

완료 기준: 기존 시험 전체 통과, 세션·토큰·쿠키 우선순위 시험(경로별) 통과, 감사 기록 내용 동일(시험으로 비교).

### 3단계: `formLogin`으로 로그인 입구 교체 (위험 높음, 2단계 뒤에 재결정)

`LoginController`의 폼 로그인(`POST`)과 AJAX 팝업 로그인을 스프링 `formLogin`의 처리 URL, 성공 처리기, 실패 처리기로 옮긴다. v1.6의 리다이렉트 규칙(`redirectUrl`), 안내 문구(flash), 2단계 인증 분기(`TwoFactorLogin.begin`)를 처리기가 그대로 수행해야 한다. 입구가 늘어난 만큼 2단계 인증 우회 위험이 생기므로 `LoginSessionService.start`의 점검은 유지한다. **2단계를 마친 뒤 이 단계가 얼마나 이득인지 다시 따져 선생님께 여쭙고 진행한다.**

## 6. 위험과 대응

| 위험 | 대응 |
|---|---|
| 판정 순서나 문구가 미묘하게 바뀜 | 1단계 첫 작업이 특성화 시험이고, `LoginControllerTest` 등 기존 시험이 수정 없이 통과해야 한다 |
| `/-_-api` 체인 분리 시 v1.6 별칭 경로와 충돌 | 홈의 별칭 `/-_-api`가 같은 이름 공간을 쓰는 현재 규칙(필터 주석)을 경로별 시험으로 고정한 뒤 분리한다 |
| 2단계 인증 우회 | `TwoFactorGate` 확인을 제공자와 `RememberMeServices` 안으로 옮기되 우회 시험(쿠키만으로, 알려진 기기 없이)을 먼저 둔다 |
| 이벤트 리스너가 감사를 이중으로 기록 | 이관 전후의 감사 행 수와 내용을 비교하는 시험을 둔다 |
| 일부 호출 지점을 놓침 | 구조 시험(소스 훑기)과 전체 시험(H2, PostgreSQL)으로 확인한다 |

## 7. 시험 계획 (TDD)

- 각 단계의 첫 커밋은 **실패하는 시험 또는 현재 동작을 고정하는 특성화 시험**이다.
- 단계마다 H2 전체와 PostgreSQL 전체를 돌린다(이 두 데이터베이스에서 인증 동작이 다르지 않음을 확인).
- 화면 검증은 Playwright로 로그인 화면(비밀번호, 로그인 유지, 2단계, 소셜, SSO)을 단계 완료 때 한 번 확인한다.

## 8. 위키와 문서

단계마다 이 문서의 상태와 결과를 갱신하고, 결정은 `decisions.md`에, 에이전트가 정한 구현 판단은 같은 파일의 판단 표에 기록한다. 구조가 달라지는 부분은 `architecture/differences-from-v16.md`에 적는다.

## 9. 다른 항목과의 관계

- **해시 강화(argon2id)**: 1단계와 2단계가 끝나야 시작할 수 있다. 쿠키가 해시를 담는 결함(R-05) 때문에 해시를 바꾸면 기존 쿠키가 무효가 된다는 점도 그때 다룬다.
- **X-42(SSO 그룹과 역할 매핑)**: 이벤트 경로가 모이면 로그인 시점의 처리 연결이 쉬워지나 필수 선행은 아니다.
- **X-44(팀)**: 권한 판정(`AccessControl`)을 바꾸고 인증은 건드리지 않아 독립이다.

## 10. 선생님께 여쭐 것

1. **시기**: v2.0.0에 넣을지, v2.1.0으로 둘지. **권장: v2.1.0.** 사용자에게 보이는 이득이 없고 로그인 경로를 건드리므로, 2.0.0 출시 직전에 위험을 더하지 않는 편이 낫다. 1단계만 2.0.0에 넣는 절충도 가능하다.
2. **범위**: SSO 체인은 관리자를 합치지 않고 이벤트 경로만 맞추는 것으로 줄일지. **권장: 줄인다.** 이득이 작고 SSO 설정과 제공자 코드를 크게 건드린다.
3. **정리 후보 확인**: `LocalAuthenticator.authenticateBasic`은 D-70 이후 운영 코드에서 호출하는 곳이 없고 시험 한 곳(`LdapAuthServiceTest`)만 쓴다. 메서드와 그 시험을 삭제할지 여쭌다(삭제는 선생님 확인 사항이다). **권장: 1단계에서 함께 삭제한다.** 비밀번호를 받는 Git 입구가 없다는 D-70과 어긋나는 죽은 경로이기 때문이다.
4. **3단계**: 2단계가 끝난 뒤에 진행 여부를 다시 정하는 것에 동의하시는지. **권장: 그렇게 한다.**
5. **쿠키**: 이 항목에서는 v1.6 형식(해시 포함)을 그대로 두고, 재설계는 해시 강화와 묶어 따로 하는 것에 동의하시는지. **권장: 그렇게 한다.**

## 11. 규모 (참고)

1단계는 호출 지점 약 10개 파일과 새 클래스 4개, 2단계는 약 8개 파일과 새 클래스 4개, 3단계는 `LoginController`와 처리기 약 5개 파일이다. 단계마다 독립적으로 병합할 수 있어 2단계 뒤에서 멈춰도 얻은 이득이 남는다.
