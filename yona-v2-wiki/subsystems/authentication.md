---
title: 인증 (로컬 로그인 판정과 패스워드 해시)
type: subsystem
sources: [app/controllers/UserApp.java:197-259, app/controllers/UserApp.java:1027-1032, app/controllers/UserApp.java:1144-1154, app/models/User.java:358-366, app/models/User.java:530-533, app/models/AuthInfo.java]
updated: 2026-10-22
---

# 인증

v1.6의 네 가지 인증 경로(로컬, LDAP, 소셜, 토큰/Basic) 중 **로컬 계정 판정**부터 구현했다. 나머지는 이후 단계에서 이 문서에 추가한다. v1.6 분석은 `~/yona-convert/yona-v1.6-wiki/subsystems/auth-ldap-social.md`를 본다.

## 구현된 구성요소

| 클래스 | 역할 | 대응하는 v1.6 코드 |
|---|---|---|
| `dev.yona.security.LegacyPasswordHasher` | 솔트 포함 SHA-256 1024회 반복 + Base64. 솔트 생성(16바이트 난수 Base64) | `UserApp.hashedPassword`, `User.getHashedStringForPassword` |
| `dev.yona.domain.user.UserRepository` | 로그인 ID(대소문자 무시) 우선, 없으면 이메일로 조회 | `User.findByLoginKey` |
| `dev.yona.security.LocalAuthenticator` | 계정 상태와 패스워드로 `LoginResult` 판정 | `UserApp.loginByFormRequest` |

## 패스워드 해시

Shiro의 `Sha256Hash(평문, 솔트, 1024).toBase64()`와 비트 단위로 같다. Shiro 의존성 없이 `MessageDigest`로 구현했고, 테스트(`LegacyPasswordHasherTest`)에서 Shiro 1.13.0을 참조 구현으로 불러 여러 입력의 결과가 같음을 확인한다(Shiro는 테스트에서만 쓴다).

- 첫 반복은 `SHA-256(솔트 UTF-8 바이트 + 평문 UTF-8 바이트)`, 이후 반복은 직전 해시에 `SHA-256`을 다시 적용한다.
- 솔트는 문자열의 UTF-8 바이트다. v1.6 초기 데이터의 `[B@1032a4`도 문자열 그대로 솔트가 된다.
- 비교는 상수 시간(`MessageDigest.isEqual`)으로 한다. v1.6은 `StringUtils.equals`를 썼으므로 이 부분만 더 안전하다. 동작 결과는 같다.

## 로그인 판정 순서 (`LocalAuthenticator`)

1. 로그인 ID 또는 이메일로 계정을 찾는다. 없으면 `InvalidCredentials`.
2. 가입 승인제(`yona.signup.require-admin-confirm`, v1.6 `signup.require.admin.confirm`)이고 계정이 LOCKED이면 패스워드를 보기 전에 `Locked`.
3. 계정이 DELETED이면 `Deleted`.
4. 패스워드가 틀리면 `InvalidCredentials`.
5. 패스워드가 맞아도 LOCKED이면 `Locked`.
6. 그 외 `Success`.

이 판정은 화면과 독립이다. 결과를 안내 문구(`user.locked`, `user.deleted`, `user.login.invalid`)로 바꾸는 일은 로그인 컨트롤러가 맡는다(다음 단계).

## 현재 사용자 식별과 로그인 엔드포인트

선생님께서 "Spring Security 필터 체인"을 선택하셨다(2026-10-22). v1.6처럼 인가는 URL이 아니라 컨트롤러(`AccessControl`)에서 판단하므로 `SecurityConfig`는 모든 URL을 허용하고, 사용자 식별과 보안 헤더만 맡는다.

| 클래스 | 역할 | 대응하는 v1.6 코드 |
|---|---|---|
| `YonaAuthenticationFilter` | 세션 -> API 토큰 헤더 -> remember-me 쿠키 순으로 사용자를 찾아 보안 컨텍스트에 채운다 | `UserApp.currentUser`, `getUserFromToken`, `User.findUserIfTokenExist` |
| `YonaAuthentication`, `YonaPrincipal` | 세션에 저장되는 직렬화 가능한 최소 정보(`userId`, `loginId`, `name`) | `addUserInfoToSession`의 세션 값 |
| `CurrentUserProvider`, `CurrentUser` | 요청마다 사용자 정보를 DB에서 읽어 한 번만 캐시. 익명이면 `user == null` | `UserApp.currentUser()`, `User.anonymous` |
| `LoginController` | `POST /users/login`(폼, AJAX), `GET /users/logout` | `UserApp.login`, `loginByFormRequest`, `loginByAjaxRequest`, `logout` |
| `HttpPreference` | Accept 헤더로 JSON 선호 여부 판단 | `HttpUtil.getPreferType`, `isJSONPreferred` |
| `RedirectSupport` | `303 See Other` + 일회성(flash) 메시지 | Play `redirect(...)`, `flash(...)` |

### 동작 규칙

- 폼 로그인 성공: `redirectUrl`이 있으면 각 경로 구간을 URL 인코딩해 `303`으로 이동, 없으면 `/`. 실패는 `/users/loginform?redirectUrl=<원래 값>`으로 돌아가며 flash `warning`에 메시지 키(`user.login.invalid`, `user.locked`, `user.deleted`)를 담는다.
- 패스워드가 맞아도 LOCKED인 경우는 flash `warning=user.locked`와 `success=user.logout.success`를 남기고 Referer로 돌려보낸다(`LoginResult.LockedAfterAuthentication`).
- AJAX(`Accept`가 JSON 우선): 성공 `200 {}`(Content-Type은 v1.6처럼 `text/plain`), 입력 누락 `400`, 잠김 `403`, 삭제 `404`, 인증 실패 `403`. 본문은 `{"message":"<메시지 키>"}`.
- `rememberMe=true`이면 쿠키 `yobi.token=<로그인ID>:<해시된 패스워드>`(30일). 요청에 이 쿠키만 있어도 사용자가 복원되며 세션에도 기록된다. 잘못된 값이면 쿠키를 지운다.
- API 토큰: `Authorization: token <값>` 또는 `Yona-Token`. 세션에는 기록하지 않는다.
- 로그인 응답에는 `Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`, `Expires: 0`을 붙인다.
- 로그인 직후 사용자의 `lang`을 요청 로캘로 갱신한다.

### Spring Security 설정에서 v1.6과 달라지는 점

- CSRF 보호를 끈다. v1.6의 폼과 JS는 CSRF 토큰을 보내지 않는다.
- `X-Frame-Options: SAMEORIGIN`. 레이아웃의 사이드바가 같은 출처 iframe을 쓴다.
- 캐시 방지 헤더를 전역으로 켜지 않는다. `/assets/`까지 캐시되지 않기 때문이다.
- 로그아웃 시 세션을 무효화하지 않고 인증 정보만 지운다. 일회성 메시지(flash)가 세션에 저장되기 때문이다. Play는 flash를 세션과 별도 쿠키에 저장했다.

### 이후 구현된 부분

- 입력 누락 시 400과 함께 로그인 화면을 다시 그리는 동작: [../web/layout-and-login-page.md](../web/layout-and-login-page.md).
- LDAP 인증, 소셜 로그인, 소셜 로그인 전용 모드: [../web/ldap.md](../web/ldap.md), [../web/social-login.md](../web/social-login.md). HTTP Basic 인증(Git, SVN)은 `BasicCredentials`가 공유한다.
- Play의 언어 결정 규칙: `Accept-Language`는 구현했고 `PLAY_LANG` 쿠키는 v1.6 앱이 만들지 않아 필요 없다.

## 설정 키 대응

| v1.6 키 | v2 키 | 기본값 |
|---|---|---|
| `signup.require.admin.confirm` | `yona.signup.require-admin-confirm` | false |

v1.6의 구버전 키 `signup.require.confirm`은 아직 옮기지 않았다.

## v1.6 결함을 그대로 재현한 부분

- 솔트나 패스워드가 비어 있는 계정으로 로그인하면 `IllegalArgumentException("Bad password or passwordSalt!")`이 발생한다(v1.6은 HTTP 500). [refactoring-backlog.md](../refactoring-backlog.md)에 올렸다.

## 초기 관리자 계정과 비밀번호

`initial-data.yml`의 관리자 해시(`5v4TVj...`)의 평문은 **`admin00`**이다. 처음에는 평문을 알 수 없다고 잘못 판단했다(`admin`, `1234`, `yona` 등만 대입해 보았다). 선생님께서 v1.6 저장소 이력에서 찾아보라고 하셔서 확인했다.

- Shiro를 적용한 커밋 `f4166b38c`(2012-09-06)가 `conf/initial-data.yml`의 `password: admin00`을 이 해시로 바꾸었다. 같은 커밋의 `hashedPassword`가 `Sha256Hash(평문, ByteSource.Util.bytes(솔트), 1024)`이므로 현재 구현과 같은 알고리즘이다.
- 솔트 `[B@1032a4`는 바이트 배열의 `toString()` 결과지만 문자열 그대로 솔트로 쓰인다. 따라서 문자열 솔트로 재현된다.
- 검증: `LegacyPasswordHasherTest`(해시 일치)와 `InitialDataLoaderTest`(`admin / admin00`으로 로그인 성공).

v1.6은 최초 기동 시 `application.secret`이 기본값이면 관리자 비밀번호를 바꾸는 화면으로 유도한다(`Global.java:249-411`). 이 흐름은 6e에서 구현했다([../web/welcome.md](../web/welcome.md)).
