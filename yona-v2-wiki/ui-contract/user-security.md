---
title: 사용자 영역 화면 계약 2 - 인증, 가입, 2단계 인증, 보안 키 설정
type: ui-contract
sources:
  - src/main/resources/templates/user/login.html
  - src/main/resources/templates/user/login-two-factor.html
  - src/main/resources/templates/user/signup.html
  - src/main/resources/templates/user/verified.html
  - src/main/resources/templates/user/reset-password.html
  - src/main/resources/templates/user/edit-two-factor.html
  - src/main/resources/templates/user/edit-two-factor-setup.html
  - src/main/resources/templates/user/edit-two-factor-key.html
  - src/main/resources/templates/user/edit-two-factor-codes.html
  - src/main/resources/templates/user/edit-gpg.html
  - src/main/resources/templates/user/edit-gpg-new.html
  - src/main/resources/templates/user/edit-ssh.html
  - src/main/resources/templates/user/edit-ssh-new.html
  - src/main/resources/templates/user/edit-token.html
  - src/main/resources/templates/user/edit-token-new.html
  - src/main/resources/templates/user/edit-token-edit.html
  - src/main/resources/templates/user/partial-token-form.html
  - src/main/resources/templates/user/edit-linked-accounts.html
  - src/main/kotlin/dev/yona/web/user/LoginController.kt
  - src/main/kotlin/dev/yona/web/user/LoginFormController.kt
  - src/main/kotlin/dev/yona/web/user/PasswordLoginFilter.kt
  - src/main/kotlin/dev/yona/web/user/PasswordConfirmation.kt
  - src/main/kotlin/dev/yona/web/user/PasswordLoginHandlers.kt
  - src/main/kotlin/dev/yona/web/user/TwoFactorLoginController.kt
  - src/main/kotlin/dev/yona/web/user/TwoFactorSettingsController.kt
  - src/main/kotlin/dev/yona/web/user/WebAuthnSettingsController.kt
  - src/main/kotlin/dev/yona/web/user/SignupController.kt
  - src/main/kotlin/dev/yona/web/user/PasswordResetController.kt
  - src/main/kotlin/dev/yona/web/user/GpgKeyController.kt
  - src/main/kotlin/dev/yona/web/user/SshKeyController.kt
  - src/main/kotlin/dev/yona/web/user/LinkedAccountsController.kt
  - src/main/kotlin/dev/yona/web/user/AccountController.kt
  - src/main/kotlin/dev/yona/security/
  - src/main/kotlin/dev/yona/domain/twofactor/
  - src/main/kotlin/dev/yona/domain/user/SignupService.kt
  - src/main/kotlin/dev/yona/domain/user/PasswordPolicy.kt
  - src/main/kotlin/dev/yona/domain/user/LoginIdRules.kt
  - src/main/kotlin/dev/yona/security/AttemptRecord.kt
  - src/main/kotlin/dev/yona/security/TwoFactorAttempts.kt
  - src/main/kotlin/dev/yona/web/access/AnonymousCheckStage.kt
  - src/main/resources/static/assets/javascripts/esm/ui/SubmitOnce.js
  - src/main/resources/static/assets/javascripts/esm/service/user.SignUp.js
  - src/main/resources/static/assets/javascripts/esm/service/resetPassword.js
  - src/main/resources/static/assets/javascripts/esm/webauthn.js
  - src/main/resources/static/assets/javascripts/common/yona.Csrf.js
updated: 2026-11-11
---

# 사용자 영역 화면 계약 2: 인증, 가입, 2단계 인증, 키와 토큰 설정

이 글은 `user/` 템플릿 가운데 로그인, 가입, 비밀번호 재설정, 2단계 인증, GPG와 SSH 키, 개인용 토큰, 연결된 계정 화면과 그 서버 규칙(시도 제한, 쿠키, 세션)을 다룬다. 프로필, 사용자 페이지, 내 파일, 이메일, 알림, 보안 기록 탭은 [user.md](user.md), OAuth 앱과 동의 화면과 기기 승인은 [oauth.md](oauth.md)에 있다.
이미 있는 글(`subsystems/authentication.md`, `architecture/two-factor.md`, `web/signup-and-password.md`, `web/account.md`)은 다시 쓰지 않고, 코드를 읽고 새로 확인한 사실과 어긋난 곳만 더한다. 위키와 코드가 다르면 코드가 맞다(어긋난 곳은 맨 끝 발견 사항 `usersec-F`).

경로 줄임말: `T/`=`src/main/resources/templates/`, `K/`=`src/main/kotlin/dev/yona/`, `J/`=`src/main/resources/static/assets/javascripts/`, `M/`=`src/main/resources/`, `TT/`=`src/test/kotlin/dev/yona/`. 줄 번호는 `ui-improvements` 브랜치의 현재 파일 기준이다.

## 1. 영역 요약

| 템플릿 | URL과 메서드 | 컨트롤러(파일:줄) | 용도 |
|---|---|---|---|
| `user/login.html` | `GET /users/loginform` | `K/web/user/LoginFormController.kt:25` (`loginPage`는 `:59`) | 전체 폭 로그인 화면. `POST /users/login` 400 재표시와 `POST /resetPassword` 성공 뒤에도 같은 화면을 그린다 |
| `user/login-two-factor.html` | `GET /users/login/2fa` | `K/web/user/TwoFactorLoginController.kt:26` | 비밀번호 통과 뒤 코드 입력 화면 |
| `user/signup.html` | `GET /users/signupform`, 오류 시 `POST /users/signup` 400 | `K/web/user/SignupController.kt:38`, `:44` | 가입 양식 |
| `user/verified.html` | `GET /verify/{loginId}/{verificationCode}` | `K/web/user/SignupController.kt:80` | 가입 이메일 확인 완료 안내 |
| `user/reset-password.html` | `GET /resetPassword?s=` | `K/web/user/PasswordResetController.kt:81` | 이메일 링크로 들어온 새 비밀번호 입력 |
| `user/edit-two-factor.html` | `GET /user/editform/two-factor` | `K/web/user/TwoFactorSettingsController.kt:48` | 2단계 인증 수단, 백업 코드, 알려진 기기 목록 |
| `user/edit-two-factor-setup.html` | `POST /user/two-factor/begin`, 코드 오류 시 `POST /user/two-factor/totp` 400 | `K/web/user/TwoFactorSettingsController.kt:59`, `:69` | 인증 앱 등록(QR, 비밀 값, 확인 코드) |
| `user/edit-two-factor-key.html` | `GET /user/editform/two-factor/security-key` | `K/web/user/TwoFactorSettingsController.kt:54` | 보안 키 이름 입력과 등록 |
| `user/edit-two-factor-codes.html` | `POST /user/two-factor/totp`, `POST /user/two-factor/backup-codes`, `GET /user/two-factor/webauthn/done` | `K/web/user/TwoFactorSettingsController.kt:69`, `:85`, `K/web/user/WebAuthnSettingsController.kt:63` | 백업 코드 한 번 보이기 |
| `user/edit-gpg.html` | `GET /user/editform/gpg` | `K/web/user/GpgKeyController.kt:41` | GPG 키 목록과 삭제 |
| `user/edit-gpg-new.html` | `GET /user/editform/gpg/new`, 오류 시 `POST /user/gpg` 400 | `K/web/user/GpgKeyController.kt:46`, `:52` | GPG 공개키 추가 |
| `user/edit-ssh.html` | `GET /user/editform/ssh` | `K/web/user/SshKeyController.kt:38` | SSH 키 목록과 삭제 |
| `user/edit-ssh-new.html` | `GET /user/editform/ssh/new`, 오류 시 `POST /user/ssh` 400 | `K/web/user/SshKeyController.kt:43`, `:49` | SSH 공개키 추가 |
| `user/edit-token.html` | `GET /user/editform/token`, `POST /user/token` 성공, `POST /user/token/{id}/regenerate` 성공 | `K/web/user/AccountController.kt:103`(`editFormByTab`의 `token`), `:267`, `:308` | 개인용 API 토큰 목록. 만든 직후 값 한 번 표시 |
| `user/edit-token-new.html` | `GET /user/editform/token/new`, 오류 시 `POST /user/token` 400 | `K/web/user/AccountController.kt:88`, `:267` | 토큰 만들기 |
| `user/edit-token-edit.html` | `GET /user/editform/token/{id}`, 오류 시 `POST /user/token/{id}` 400 | `K/web/user/AccountController.kt:94`, `:289` | 토큰 이름과 범위 수정, 다시 만들기, 삭제 |
| `user/partial-token-form.html` | (조각) | 위 세 화면이 `th:replace` | 이름, 범위, 만료 입력과 달력 스크립트 |
| `user/edit-linked-accounts.html` | `GET /user/editform/linked-accounts` | `K/web/user/LinkedAccountsController.kt:29` | 연결된 SSO와 소셜 계정 목록, 해제 |

설정 화면들(`edit-*`)은 모두 `user/partial-edit-tabmenu`(탭 줄)를 쓴다. 탭 줄 자체는 [user.md](user.md) 3절에 정리했다. 이 글의 `edit-*` 화면 탭 id는 `two-factor`, `gpg`, `ssh`, `token`, `linked-accounts`이다.

공통 레이아웃: 로그인, 가입, 재설정, 확인 화면은 `layout/site-layout`을 `'NONE'`으로 부르고 본문이 `div.page.full`이다(제목 `div.center-wrap.tag-line-wrap`, 폼 `div.login-form-wrap.frm-wrap` 또는 `signup-form-wrap`). 설정 화면은 `'USER'`이고 `site-breadcrumb-outer` 머리와 `page-wrap-outer > page-wrap` 본문이다. 내비게이션 줄이 `menu` 값을 쓰는 곳은 `T/common/navbar.html:27`의 `'PROJECTS'` 하나뿐이라 `NONE`, `USER`, `SITE_HOME`은 화면 모양을 바꾸지 않는다.

## 2. 인증 서버 규칙 (여러 화면이 함께 쓰는 계약)

### 2.1 요청 분기: 누가 `POST /users/login`을 받는가

1. 스프링 필터 `PasswordLoginFilter`가 `POST /users/login`이면서 `loginIdOrEmail`과 `password`가 둘 다 비어 있지 않을 때만 맡는다(`K/web/user/PasswordLoginRequestMatcher.kt:13-16`, 필터 `K/web/user/PasswordLoginFilter.kt:44-60`). 응답 머리에는 `Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`, `Expires: 0`을 단다.
2. 둘 중 하나라도 비면 필터가 맡지 않고 `LoginController.login`이 받는다(`K/web/user/LoginController.kt:31-42`). `Accept`가 JSON 우선이면 `400 {"message":"user.login.required"}`, 아니면 `400` 상태로 로그인 화면(`redirectUrl` 빈 값)을 그리고 같은 응답에서 flash 경고 `user.login.required`를 보인다.
3. 필터는 비밀번호를 확인하기 전에 이 요청의 로그인 키(입력한 `loginIdOrEmail`을 앞뒤 공백을 자르고 소문자로 바꾼 값)가 잠겨 있는지 먼저 본다(`PasswordLoginFilter.kt:53-58`, 2.6절). 잠겨 있으면 비밀번호 확인과 SSO 전용 검사보다 앞서 `TooManyAttempts`로 거절한다.
4. 필터가 맡은 뒤의 응답은 `Accept`로 갈린다(`HttpPreference.isJsonPreferred`). JSON 우선이면 로그인 팝업(`common/login-dialog.html`, `esm/LoginDialog.js`)이 쓰는 JSON 계약이고, 아니면 폼 계약(303)이다.

### 2.2 비밀번호 확인 순서 (`K/security/YonaPasswordAuthenticationProvider.kt:57-87`, 앞의 잠금 확인은 필터 `PasswordLoginFilter.kt:55-56`)

| 순서 | 조건 | 결과(`LoginResult`) |
|---|---|---|
| 0 | 같은 로그인 키로 5번 연달아 틀려 15분 잠겨 있음(필터가 맨 먼저 판정, 맞는 비밀번호도 받지 않음) | `TooManyAttempts` |
| 1 | SSO 전용 모드(`siteSettings.ssoOnly()`)인데 입력한 아이디가 사이트 관리자가 아님 | `SsoOnly`(비밀번호 확인 전에 거절) |
| 2 | 사용자 없음이고 LDAP 꺼짐 | `InvalidCredentials` |
| 3 | 사용자가 있고 가입 승인제(`properties.signup.required()`)이며 상태 `LOCKED` | `Locked`(비밀번호 확인 전) |
| 4 | 사용자 상태 `DELETED` | `Deleted`(비밀번호 확인 전, 내부 결과일 뿐 화면에는 `InvalidCredentials`와 같게 나간다. 2.3절) |
| 5 | LDAP이 켜져 있으면 LDAP이 인증, 아니면 `passwords.matches` | 맞지 않으면 `InvalidCredentials`, LDAP이 안내를 남겼으면 `InvalidWithNotice` |
| 6 | v1.6 해시로 성공하면 그 자리에서 argon2id로 바꿔 저장(D-131) | (성공의 부가 동작) |
| 7 | 비밀번호는 맞는데 상태 `LOCKED`(가입 승인제가 아닌 이메일 확인 대기 등) | `LockedAfterAuthentication` |

아이디 입력란은 로그인 ID와 이메일을 모두 받는다(`users.findByLoginKey`). 비밀번호 시도 횟수 제한은 같은 로그인 키 5회 실패 15분 잠금이다(2.3절, 2.6절).

### 2.3 성공과 실패 응답 (`K/web/user/PasswordLoginHandlers.kt`)

| 결과 | 폼(303, `Location`, flash) | JSON(`Accept` JSON 우선) |
|---|---|---|
| 성공, 2단계 불필요 | 세션 시작 뒤 `redirectUrl`(이 사이트 안 경로만, 아니면 `/`)로 303. LDAP 안내가 있으면 flash(`:104-110`) | `200`, `Content-Type: text/plain;charset=UTF-8`, 본문 `{}`(`:96-101`) |
| 성공, 2단계 필요 | 진행 중 로그인을 세션에 두고 `/users/login/2fa`로 303(`:105-109`) | `200 {"twoFactor":true,"securityKey":<보안 키 있음>}`(`:89-95`) |
| `SsoOnly` | `/users/loginform?redirectUrl=<원래 값>`로 303, flash 경고 `sso.error.ssoOnly` | `403 {"message":"sso.error.ssoOnly"}` |
| `TooManyAttempts` | 같은 곳으로 303, flash 경고 `user.login.tooMany`("로그인에 너무 많이 실패했습니다. 15분 뒤에 다시 시도하십시오.") | `429 {"message":"user.login.tooMany"}` |
| `Locked` | 같은 곳으로 303, flash `user.locked` | `403 {"message":"user.locked"}` |
| `LockedAfterAuthentication` | 로그아웃 처리 뒤 `Referer`로 303(없으면 `/`), flash 경고 `user.locked`와 성공 `user.logout.success`(`:174-180`) | `403 {"message":"user.locked"}` |
| `Deleted` | `InvalidCredentials`와 같다. 로그인 화면으로 303, flash `user.login.invalid`(계정이 있다는 사실을 알리지 않는다) | `403 {"message":"user.login.invalid"}` |
| `InvalidCredentials` | 로그인 화면으로 303, flash `user.login.invalid` | `403 {"message":"user.login.invalid"}` |
| `InvalidWithNotice` | 로그인 화면으로 303, LDAP 안내와 `user.login.invalid` | `403 {"message":"user.login.invalid"}` |

모든 실패는 감사 기록에 남는다(`audit.loginFailure(키, 이유, denied)`, `:149-158`, 이유: `sso_only`, `locked`, `deleted`, `too_many_attempts`, `locked_after_authentication`, `invalid_credentials`). 감사 이유는 `deleted`가 그대로 남지만 응답은 위 표처럼 일반 실패와 같다.

실패 횟수 규칙(`PasswordLoginHandlers.kt:159`, 성공 때 지우는 곳 `:84`): 결과가 `InvalidCredentials`, `InvalidWithNotice`, `Deleted`(없는 계정, 틀린 비밀번호, 삭제된 계정)일 때만 로그인 키별 횟수를 하나 올린다. `SsoOnly`, `Locked`, `LockedAfterAuthentication`과 잠겨서 거절한 요청은 세지 않는다. 5번째 실패에서 15분 잠금이 걸리고 그 응답은 아직 일반 실패이며, 다음 요청부터 `TooManyAttempts`이다. 비밀번호 확인에 성공하면 그 키의 횟수를 지운다(2단계가 필요한 사용자도 같다. 2단계 코드의 카운터는 별개 키). 성공은 2단계가 없으면 즉시 `audit.loginSuccess(user,"password")`, 2단계가 있으면 코드 통과 뒤 `"password+two_factor"`로 남는다(`K/security/TwoFactorLogin.kt:143`).

로그인 성공 처리(`LoginCompletion.complete`, `K/security/TwoFactorLogin.kt:36-43`): `rememberMe`이면 쿠키 발급, 사용자 `lang`을 요청 로캘로 저장, 세션 시작 순서이다. `rememberMe` 값은 `true`, `on`, `yes`, `1`을 받는다(`PasswordLoginHandlers.kt:28`). 로그인 화면의 체크박스는 `value` 속성이 없어 체크 시 `on`이 간다.

### 2.4 이동 주소(`redirectUrl`) 규칙

`RedirectSupport.localTarget`(`K/web/RedirectSupport.kt:24-35`)이 이 사이트 안의 경로만 돌려준다. `//호스트`, 역슬래시, 제어 문자, 다른 호스트, 스킴이 있는 값은 거절(null)하고 이 서버의 절대 주소(Referer)는 경로와 쿼리로 바꾼다. 성공 시 거절되면 `/`로 간다(`PasswordLoginHandlers.kt:104`). `GET /users/loginform`은 `redirectUrl` 파라미터가 비어 있으면 `Referer`의 경로를 쓰되 그 경로가 로그인 화면 자신이면 쓰지 않는다(`LoginFormController.kt:33-39`).

비로그인으로 보호된 화면에 가면 `AnonymousCheckStage`가 `303 /users/loginform?redirectUrl=<요청 경로>`로 보낸다(`K/web/access/AnonymousCheckStage.kt:25-36`). 요청이 `GET`이고 질의 문자열이 있으면 `redirectUrl` 값 뒤에 `%3F`와 질의 문자열 전체를 값으로 인코딩해 붙인다(예: `/users/device?user_code=ABCD` → `redirectUrl=/users/device%3Fuser_code%3DABCD`, `:30-33`). `GET`이 아닌 요청은 경로만 싣고 질의 문자열은 버려진다(발견 사항 `usersec-F6`).

### 2.5 세션과 쿠키

| 이름 | 값과 속성 | 만들 때 | 지울 때 | 근거 |
|---|---|---|---|---|
| `JSESSIONID`(세션) | HttpOnly, SameSite=Lax. 만료 시간 설정은 없다(컨테이너 기본값, 추측: 톰캣 기본 30분) | 로그인(`LoginSessionService.start`, 이미 세션이 있으면 `changeSessionId`) | `LoginSessionService.end`는 세션 안의 보안 컨텍스트 값만 지우고 세션은 무효화하지 않는다 | `M/application.yml:108-114`, `K/security/LoginSessionService.kt:27-44` |
| `yona.token`(자동 로그인) | 값 `로그인ID:만료epoch초:서명`. 서명은 사이트 비밀 키로 만든 HMAC-SHA256에 `로그인ID:만료:비밀번호 해시`를 넣은 값(base64url). `Max-Age` 30일(2,592,000초), `Path=/`, HttpOnly, `SameSite=Lax`, `Secure`는 요청이 https일 때만 | 로그인 시 `rememberMe`가 참이고 2단계를 마쳤을 때(2단계 사용자는 코드 통과 뒤) | 서명, 만료, 비밀번호 변경, 옛 형식이면 서버가 `Max-Age=0`으로 지움. 로그아웃이 지움 | `K/security/RememberMeCookies.kt:24-51`, `K/security/YonaAuthenticationFilter.kt:127-147`, `K/security/YonaRememberMeServices.kt:27-49` |
| `yona_device`(알려진 기기) | 무작위 32바이트 base64url. 서버에는 SHA-256만 저장. `Max-Age` 30일, `Path=/`, HttpOnly, `Secure`는 https일 때만. SameSite 속성은 달지 않는다 | 2단계 코드 화면의 "이 기기에서 30일 동안 묻지 않기" 체크 + 통과(`K/security/TwoFactorLogin.kt:136-141`) | 화면에서 기기 삭제(서버 행 삭제), 2단계 해제 시 서버 행 삭제. 로그아웃은 쿠키를 지우지 않는다 | `K/domain/twofactor/KnownDeviceService.kt:24-65` |
| `XSRF-TOKEN` | 스크립트가 읽을 수 있는 쿠키(HttpOnly 아님). 요청마다 `CsrfCookieFilter`가 심는다 | 모든 요청 | | `K/config/SecurityConfig.kt:60-64`, `K/security/CsrfSupport.kt` |
| `daysAgo` | 사용자 페이지의 "며칠 전" 값. `Path=/`, HttpOnly, 만료 없음(세션 쿠키) | `K/web/user/UserPageController.kt:78-80` | | [user.md](user.md) |

자동 로그인 처리(`YonaAuthenticationFilter.doFilterInternal`, `K/security/YonaAuthenticationFilter.kt:44-56`):

- 외부 API(`/-_-api`)는 토큰 전용, `/mcp`는 OAuth 토큰 전용이라 세션과 쿠키를 보지 않는다. 그 밖의 경로에서 세션 인증이 없을 때만 `yona.token` 쿠키를 본다.
- 쿠키의 사용자가 없거나 잠김, 삭제 상태이거나 서명이 맞지 않으면 쿠키를 지우고 익명이다(`YonaRememberMeServices.kt:27-35`). 이 점은 `refactoring-backlog.md` R-107 표의 "v2는 같게 따른다"와 어긋난다(`usersec-F9`).
- 2단계 인증을 켠 사용자는 `yona_device` 쿠키가 알려진 기기와 일치할 때만 `yona.token`을 인정한다. 아니면 쿠키는 지우지 않고 익명으로 둔다(`:36-37`).
- 쿠키로 복원되면 세션에 보안 컨텍스트를 저장하고 서버 캐시(`SessionUserCache`)에 넣는다(`:115-123`).
- 세션 보안 컨텍스트가 있어도 매 요청마다 사용자가 없거나 `LOCKED`, `DELETED`이면 컨텍스트를 비운다(`hasValidSession`, `:100-110`). 그래서 관리자가 계정을 잠그면 진행 중인 세션도 다음 요청에서 끊긴다.
- 비밀번호를 바꾸면 쿠키 서명에 비밀번호 해시가 들어 있어 모든 기기의 `yona.token`이 무효가 되지만, 이미 만들어진 다른 세션(`JSESSIONID`)은 끊기지 않는다(`AccountController.resetPassword`는 현재 세션만 `loginSession.end`, `K/web/user/AccountController.kt:146-147`).

### 2.6 시도 제한과 잠금 값

| 대상 | 규칙 | 값의 정의 위치 | 저장 | 시험 |
|---|---|---|---|---|
| 비밀번호 로그인 | 입력한 로그인 키(아이디 또는 이메일, 앞뒤 공백을 자르고 소문자로 바꿈)마다 5회 연속 실패 시 15분 잠금. 잠긴 동안은 맞는 비밀번호도 받지 않는다. 같은 키로 성공하면 횟수를 지운다. 없는 계정과 삭제된 계정도 센다(계정이 있는지 드러나지 않게) | `K/security/TwoFactorAttempts.kt:41-42`(`LIMIT`=5, `LOCK`=15분) | DB 테이블 `attempt_record`(키 `login:<소문자 키>`) | `TT/security/AttemptLimiterStoreTest.kt`, `TT/web/user/LoginControllerTest.kt` |
| 2단계 코드(앱 코드, 백업 코드, 보안 키 공통 카운터) | 사용자별 5회 연속 실패 시 15분 잠금. 잠긴 동안은 맞는 코드도 받지 않고(`isLocked`가 먼저), 진행 중이던 로그인을 버린다. 실패 횟수는 잠금이 끝난 뒤 첫 실패에서 0으로 시작, 성공하면 지운다 | `K/security/TwoFactorAttempts.kt:41-42` | DB 테이블 `attempt_record`(키 `2fa:<사용자 번호>`). 재시작해도 잠금이 남고 여러 서버가 나눠 쓴다 | `TT/web/user/TwoFactorLoginTest.kt:131` |
| 기기 승인 코드 입력([oauth.md](oauth.md) 4절) | 로그인한 사용자마다 5회 연속으로 없는 코드를 넣으면 15분 잠금. 잠긴 동안은 맞는 코드도 받지 않는다. 맞는 코드를 찾으면 횟수를 지운다 | 같음 | DB 테이블 `attempt_record`(키 `device:<사용자 번호>`) | `TT/web/user/DeviceAuthorizationPageTest.kt` |
| 진행 중인 2단계 로그인 | 비밀번호 통과 뒤 10분 안에 코드를 넣어야 한다. 넘으면 `Expired` | `K/security/TwoFactorAttempts.kt:45`, `K/security/TwoFactorLogin.kt:87-92` | 세션 속성 | `TwoFactorLoginTest.kt:144` |
| 2단계 사이에 계정이 잠김 | `Expired`로 처리 | `K/security/TwoFactorLogin.kt:121` | | `TwoFactorLoginTest.kt:156` |
| 외부 API와 Git/SVN 기본 인증 토큰 추측 | 주소별 5회 실패 시 15분 차단(`apiTokenFailureLimit`=5, `apiTokenBlockMinutes`=15). 차단 중 `429 {"message":"too many failed token attempts"}`(API) | `K/config/YonaProperties.kt:110-112`, `K/security/TokenFailureLimiter.kt` | 서버 메모리, 최대 10,000개 주소 | `TT/security/TokenFailureLimiterTest.kt` |
| 비밀번호 재설정 링크 | 로그인 ID당 1개, 1시간 유효(`EXPIRE_MILLIS`=3,600,000), 새로 요청하면 앞 것을 덮는다 | `K/security/PasswordResetTokens.kt:52-55` | 서버 메모리(재시작하면 모든 링크 무효) | `TT/web/user/PasswordResetTest.kt` |
| 가입 이메일 확인 링크 | 만든 뒤 24시간 안에 열어야 한다. 넘기면 행 삭제 후 404 | `K/domain/user/SignupService.kt:135-147` | DB(`UserVerification`) | `TT/web/user/SignupTest.kt` |
| 알려진 기기 | 사용자당 20대(넘으면 오래 쓰지 않은 것부터 삭제), 30일 | `K/domain/twofactor/KnownDeviceService.kt:61-62` | DB | `TT/domain/twofactor/KnownDeviceServiceTest.kt` |
| 인증 앱 코드 | 6자리, 30초 구간, 앞뒤 한 구간 허용, 이미 쓴 구간 이하는 거절(재생 방지) | `K/domain/twofactor/Totp.kt:14-16, 30-44` | DB `lastUsedStep` | `TT/domain/twofactor/TwoFactorServiceTest.kt` |
| 백업 코드 | 10개, 10자(`xxxxx-xxxxx` 꼴, 알파벳 `abcdefghjkmnpqrstuvwxyz23456789`), 한 번 쓰면 `usedDate`가 찬다. 해시만 저장 | `K/domain/twofactor/BackupCodes.kt:33-35` | DB | 같음 |

비밀번호 로그인, 2단계 코드, 기기 승인 코드의 잠금은 한 가지 규칙(`TwoFactorAttempts`, `LIMIT`=5, `LOCK`=15분)을 키만 달리해 쓴다. 횟수와 잠금 만료 시각(에폭 밀리초)은 `attempt_record` 행(`attempt_key`, `failures`, `locked_until`)에 있고(`K/security/AttemptRecord.kt:20-27`), 읽고 쓰는 `AttemptStore`의 모든 메서드는 호출한 요청과 별개의 트랜잭션(`REQUIRES_NEW`)에서 돌아 요청이 되돌아가도 실패 기록이 남는다(`AttemptRecord.kt:38-59`). 풀린 잠금은 다음 실패에서 지우고 0부터 센다.

### 2.7 2단계 로그인 흐름 (서버)

1. 비밀번호 성공 뒤 `twoFactorLogin.required(user, request)`가 `!TwoFactorGate.passes`를 돌려준다. `passes`는 "2단계 인증을 켜지 않았거나, `yona_device` 쿠키가 이 사용자의 유효한 알려진 기기와 일치"일 때 참이다(`K/security/TwoFactorGate.kt:17-18`).
2. 필요하면 세션의 `dev.yona.security.PendingLogin`에 사용자 ID, 출처, `rememberMe`, 이동 주소, flash, 생성 시각을 둔다. 세션이 이미 있으면 `changeSessionId`로 바꾼다(`TwoFactorLogin.kt:81-84`).
3. 코드 확인(`attempt`, `:118-145`): 진행 중 로그인 없음이면 `Expired`. 사용자가 없거나 잠김이면 `Expired`. 잠금 중이면 `Locked`(진행 중 로그인 삭제, 감사 `two_factor_locked`). 코드 불일치면 실패 기록 후 `Invalid`(5번째 실패면 `Locked`). 일치하면 카운터를 지우고 진행 중 로그인을 지우고, 기기 기억을 골랐으면 `yona_device` 쿠키를 만들고, 세션을 시작한다(`twoFactorVerified=true`).
4. 코드 입력은 먼저 백업 코드 모양(`looksLikeCode`: 공백과 하이픈을 뺀 10자가 알파벳 안)이면 백업 코드로, 아니면 6자리 숫자 앱 코드로 본다(`K/domain/twofactor/TwoFactorService.kt:143-159`). 입력의 공백은 무시한다.
5. 같은 `/users/login/2fa`를 이메일 확인 링크 로그인(`GET /user/email/confirm/...`)도 쓴다(출처 `email_link`, 이동 `/user/editform`, `K/web/user/AccountController.kt:202-216`).

## 3. 화면별 계약

### 3.1 `user/login.html` (로그인)

**개요.** `GET /users/loginform`(`LoginFormController.kt:25`). 이미 로그인했으면 `303 /`. 모델 `redirectUrl`(이 사이트 경로 또는 빈 문자열), `title`(`title.login`). 공통 모델 `site`(`site.siteName`, `site.useEmailVerification`, 로그인 화면 자리표시자 `loginPageLoginIdPlaceholder`, `loginPagePasswordPlaceholder`)와 `ssoLogin`(`providers`, `only`, `K/web/layout/LayoutModelAdvice.kt:80`, `K/sso/SsoLoginOptions.kt:33-62`)을 쓴다. 이 화면은 `Cache-Control` 캐시 금지 머리를 단다(`LoginFormController.kt:45-49`). 실패 안내(`user.login.invalid`, `user.login.tooMany` 등)는 303 뒤 flash로 이 화면 위쪽에 나온다.

**레이아웃 계약.** `th:with` 값 `localLogin`(`T/user/login.html:15`)은 `!ssoLogin.only` 이거나 쿼리에 `local` 파라미터(값 무관)가 있으면 참이다. SSO 전용 모드이고 `local`이 없으면 아이디, 비밀번호 입력, 로그인 단추, 로그인 유지, 비밀번호 찾기 줄이 모두 사라지고 SSO 단추만 남는다. 폼은 `.login-form-wrap.frm-wrap` 폭 고정은 CSS(`yobi.css`)에 있고 템플릿에는 값이 없다.

**동작 요소.**

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 이메일 확인 안내(`.email-verification-help`) | 글 | `site.useEmailVerification` | 표시만 | 없음 | | | `login.html:22` |
| `loginIdOrEmail` 입력(`#loginIdOrEmail`) | 텍스트 | `localLogin` | 입력. 화면이 열리면 초점이 간다 | 폼 필드 `loginIdOrEmail` | | 비로그인 | `:28`, `:61` |
| `password` 입력 | 비밀번호 | `localLogin` | 입력 | 폼 필드 `password` | | | `:31` |
| 로그인 단추 | submit | `localLogin` | 폼 제출 | `POST /users/login` 필드 `redirectUrl`, `loginIdOrEmail`, `password`, `rememberMe`(체크 시 `on`) | 2.3절 표(같은 아이디로 5번 틀리면 15분 동안 `user.login.tooMany`) | 비로그인(로그인 상태여도 처리됨) | `:23-24, :36` |
| 숨은 `redirectUrl` | hidden | 항상 | 이동 주소 전달 | 같은 요청 | | | `:24` |
| SSO 단추(`a.sso-login-btn`) | 링크 | `ssoLogin.providers`가 비어 있지 않음 | 이동 | `GET <provider.path>?returnTo=<redirectUrl>` (`/sso/oidc/login`, `/sso/saml2/login`) | SSO 시작 | | `:40-43`, `K/sso/SsoLoginOptions.kt:34-37` |
| "또는" 줄 | 글 | `localLogin`이고 공급자가 있음 | 표시 | | | | `:41` |
| 로컬 로그인 링크(`.sso-local-link`) | 링크 | `ssoLogin.only`이고 `!localLogin` | 이동 | `GET /users/loginform?local=true` | 같은 화면을 로컬 모드로 | | `:44` |
| 로그인 유지 체크박스(`#remember-me`) | 체크박스 | `localLogin` | 기본으로 체크됨 | `rememberMe` 필드 | 2.5절 쿠키 | | `:47-49` |
| 비밀번호 찾기 링크 | 링크 | `localLogin` | 이동 | `GET /lostPassword` | `site/lost-password` 화면 | 비로그인 | `:53` |

**스크립트 동작.** 인라인 스크립트(`:59-63`)가 문서 준비 때 `$('#loginIdOrEmail').focus()`를 불러 아이디 입력란에 초점을 둔다(입력란 id가 `loginIdOrEmail`이다, `usersec-F1`). 폼은 평범한 제출이고 `yona.Csrf.js`가 제출 직전에 숨은 `_csrf` 입력을 더한다(`J/common/yona.Csrf.js:60-77`). `esm/LoginDialog.js`는 경로 첫 구간이 `users`이면 초기화하지 않는다(`J/esm/LoginDialog.js:33-35`)라서 이 화면에서는 팝업이 동작하지 않는다.

**단축키.** 이 화면 범위의 단축키 없음. 전역 키는 `common` 담당.

**팝업.** 없음. 메시지는 flash로 화면 위쪽에 나온다(공통 레이아웃).

**요청과 응답.** CSRF: `POST /users/login`도 `_csrf` 또는 `X-XSRF-TOKEN` 필요(필터가 `CsrfFilter` 뒤, `K/config/SecurityConfig.kt:60-80`). 토큰이 없을 때의 응답은 스프링 기본 접근 거부로 403일 것이다(추측, 시험 없음). 입력 누락은 2.1절 2번.

**권한 행렬.** 비로그인: 전부 사용. 로그인: `GET /users/loginform`은 `/`로 이동. 서버가 막는 규칙은 2.2절과 2.6절.

**빈 상태와 경계 값.** 입력 길이 제한이 템플릿에도 서버에도 없다. 자리표시자는 사이트 설정 값이 있으면 그것, 없으면 메시지 `user.login.key`, `user.password`.

**시험.** `TT/web/user/LoginFormPageTest.kt`, `LoginControllerTest.kt`, `LoginFlowCharacterizationTest.kt`, `SsoLoginPageTest.kt`, `LoginEmailVerificationHelpTest.kt`, `PasswordLoginRequestMatcherTest.kt`.

### 3.2 `user/login-two-factor.html` (2단계 로그인)

**개요.** `GET /users/login/2fa`(`TwoFactorLoginController.kt:26-30`). 진행 중인 로그인(10분 이내)이 없으면 `303 /users/loginform?redirectUrl=`. 모델 `title`(`title.twoFactor`), `securityKey`(사용자가 보안 키를 등록했는지). 캐시 금지 머리.

**동작 요소.**

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 코드 입력(`#two-factor-code`) | 텍스트 | 항상 | `autofocus`, `autocomplete=one-time-code` | 필드 `code` | | 진행 중 로그인이 있는 세션 | `T/user/login-two-factor.html:22` |
| "이 기기에서 30일 동안 묻지 않기"(`#remember-device`) | 체크박스 | 항상, 기본 해제 | | 필드 `rememberDevice=true` | 통과 시 `yona_device` 쿠키 | | `:27` |
| 확인 단추 | submit | 항상 | 폼 제출 | `POST /users/login/2fa` 필드 `code`, `rememberDevice` | 성공 303 이동 주소(flash 그대로), 코드 불일치 303 `/users/login/2fa`+flash 경고 `user.twoFactor.invalid`, 잠김 303 `/users/loginform?redirectUrl=`+`user.twoFactor.locked`, 만료 303 로그인 화면+`user.twoFactor.expired` | | `:30`, `TwoFactorLoginController.kt:57-71` |
| 보안 키 사용(`#security-key-use`) | 단추 | `securityKey`가 참이고 브라우저가 WebAuthn을 지원할 때 스크립트가 보임 | 아래 스크립트 | `POST /users/login/2fa/webauthn/options`(폼 아님, `Accept: text/plain` 계열), `POST /users/login/2fa/webauthn` 필드 `credential`(JSON 문자열), `rememberDevice` | 성공 `200 {"redirect":<이동 주소 또는 "/">}`, 실패 `403 {"message":<키>}`(`user.twoFactor.invalid`, `locked`, `expired`) | | `:31-33, :44-61`, `TwoFactorLoginController.kt:35-55` |
| 오류 영역(`#security-key-error`) | div | 스크립트가 보임 | 실패 메시지 | | | | `:34` |
| "로그인으로 돌아가기" 링크 | 링크 | 항상 | 이동 | `GET /users/loginform` | | | `:37` |

**스크립트.** `esm/webauthn.js`(전역 `YonaWebAuthn`)와 인라인 스크립트(`:44-61`): 보안 키가 있으면 `YonaWebAuthn.supported()`일 때 `#security-key-login`을 보이고, 클릭하면 `options` 요청 → `navigator.credentials.get` → `webauthn` 요청 → `location.href = result.redirect`. 실패는 `e.responseJSON.message`의 메시지 키를 `Messages(...)`로 풀어 `#security-key-error`에 보인다(없으면 `user.twoFactor.invalid`). 보안 키 확인에서 로그인 팝업과 같은 시도 카운터를 쓴다(2.6절). 서버는 요청 `options` 때 만든 서버 쪽 요청 값을 세션(`dev.yona.security.PendingAssertion`)에 두고, 제출 때 한 번 꺼내 쓴다(`TwoFactorLogin.kt:101-114`). 보안 키 확인에는 `rememberDevice` 체크 값이 `$("#remember-device").is(":checked")`로 간다.

**단축키.** 없음. **팝업.** 없음.

**권한.** 진행 중인 로그인이 없으면 화면 `303`, JSON 단계 `403 user.twoFactor.expired`. 이 요청들은 `/users/`로 시작해 2단계 강제 인터셉터의 예외이다(`TwoFactorEnforcementInterceptor.kt:46`).

**시험.** `TT/web/user/TwoFactorLoginTest.kt`(전 흐름), `WebAuthnWebTest.kt`.

### 3.3 `user/signup.html` (가입)

**개요.** `GET /users/signupform`(로그인 상태면 `303 /`, `SignupController.kt:38-42`). 모델 `title`(`title.signup`), `requiresConfirm`(가입 승인제, `signupService.requiresAdminConfirm()`), `adminEmailReversed`(로그인 ID가 `admin`인 사용자의 이메일을 거꾸로 쓴 것, 없으면 null). 제출 오류와 이메일 도메인 거부 때도 `400`으로 같은 화면을 그린다(`:51-56`).

**레이아웃 계약.** `requiresConfirm`이면 제목 아래에 안내 두 단락(`title.signupConfirmDesc`, `title.signupConfirmDesc2(adminEmailReversed)`, 두 번째는 `th:utext`)이 더해진다(`T/user/signup.html:20-23`). 이메일을 거꾸로 쓰는 것은 크롤러 대책이다(v1.6 그대로, 화면 쪽에서 다시 뒤집는 스크립트는 이 템플릿에 없으므로 사용자는 거꾸로 쓴 글자를 그대로 본다).

**동작 요소.**

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 아이디(`#loginId`) | 입력 | 항상 | 포커스 아웃 시 소문자 변환, 형식과 중복 확인 | 필드 `loginId`. 확인 요청 `GET /user/isUsed?name=<값>` | `{"isExist":bool,"isReserved":bool}` | 비로그인 가능, 로그인 여부 검사 없음 | `signup.html:32`, `SignupController.kt:83-85` |
| 이름(`#uname`) | 입력 | 항상 | | 필드 `name` | | | `:39` |
| 이메일(`#email`) | 입력(type text) | 항상 | 포커스 아웃 시 중복 확인 | 필드 `email`. `GET /user/isEmailExist?email=<값>` | `{"isExist":bool}` | | `:46`, `SignupController.kt:92-93` |
| 비밀번호(`#password`) | 비밀번호 | 항상 | 키를 뗄 때(300ms 지연) 8자 미만 검사(앞뒤 공백을 자르지 않고 글자 수를 센다, 오류 문구 `validation.tooShortPassword` "비밀번호는 8자 이상이어야 합니다.") | 필드 `password` | | | `:53`, `user.SignUp.js:99-109` |
| 비밀번호 확인(`#retypedPassword`) | 비밀번호 | 항상 | 키를 뗄 때(200ms 지연) 일치 검사 | 필드 `retypedPassword` | | | `:60`, `user.SignUp.js:112-120` |
| 가입 단추 | submit | 항상 | 클라이언트 검증 통과 시 제출 | `POST /users/signup` 필드 `loginId`, `name`, `email`, `password`, `retypedPassword`(서버는 `retypedPassword`가 오면 `password`와 같은지만 본다) | 성공 `303 /`, 아래 | 비로그인 | `:65`, `SignupController.kt:44-67` |
| "로그인" 링크(`.go-login`) | 링크 | 항상 | 이동 | `GET /users/loginform` | | | `:69` |

**서버 규칙(`SignupService.hasErrors`, `K/domain/user/SignupService.kt:40-53`).** 아래 중 하나라도 걸리면 오류(메시지 없이 400으로 폼 재표시): 로그인 ID 비어 있음, 형식 위반(`[a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*`, 한글을 허용하고 `_`와 `.`는 한 글자씩만 사이에 둘 수 있어 `a..b`, `a_.b`는 거절, `K/domain/user/LoginIdRules.kt:16`), 예약어(`LoginIdRules.kt:24-35`, 대소문자 무시), 이메일 형식 위반(이메일이 있을 때), 로그인 ID에 공백, 비밀번호가 공백뿐, 로그인 ID 중복(사용자 또는 조직 이름과 대소문자 무시 비교), 이메일 중복(주 이메일 또는 확인된 보조 이메일). 이름은 필수 검사가 없고 `HtmlSanitizer`로 정리한다. 이 단계의 오류는 메시지 없이 `400`이다.

`hasErrors`를 통과하면 컨트롤러가 이어서 검사한다(`K/web/user/SignupController.kt:44-69`). 모두 `400`으로 가입 화면을 다시 그리고 flash 경고를 보인다(입력값은 되돌려 주지 않는다): (1) `retypedPassword`가 요청에 있고 `password`와 다르면 `user.confirmPassword.alert`("입력한 두 비밀번호가 서로 일치하지 않습니다", `PasswordConfirmation.kt:25-28`. `retypedPassword`를 아예 보내지 않으면 이 검사는 건너뛴다). (2) 비밀번호 정책(`K/domain/user/PasswordPolicy.kt:45-82`): 글자 수(유니코드 코드 포인트)가 8 미만이면 `validation.tooShortPassword`, 128 초과이면 `validation.tooLongPassword`("비밀번호는 128자 이하여야 합니다."), 번들 목록(`M/password/common-passwords.txt`)에 있는 흔한 비밀번호(대소문자 무시)이거나 한 글자만 반복한 값이면 `validation.commonPassword`("너무 흔한 비밀번호입니다. 다른 비밀번호를 정해 주세요."), 로그인 ID나 이메일(전체 또는 `@` 앞부분)과 같으면(대소문자 무시) `validation.passwordSameAsId`("비밀번호는 아이디나 이메일과 같을 수 없습니다."). 대문자, 숫자, 기호 구성은 강제하지 않고 앞뒤 공백을 자르지 않는다. (3) 이메일 도메인 제한.
이메일 도메인 제한(`properties.allowedSendingMailDomains`)이 걸리면 400과 flash 안내 `user.unacceptable.email.domain`(정보 종류).

**성공 후.** 사용자 상태는 가입 승인제나 이메일 확인이면 `LOCKED`, 아니면 `ACTIVE`(`SignupService.kt:111`). 이메일 확인을 쓰면 확인 메일 이벤트를 발행하고 flash 정보 `user.verification.mail.sent`. 가입 승인제이면(잠김 + 승인제) flash 정보 `user.signup.requested`를 쓰고 로그인시키지 않는다. 그 밖에는 `loginSession.start`로 즉시 로그인한다(잠긴 사용자는 시작하지 않는다, `LoginSessionService.kt:28`). 모두 `303 /`. 감사 `account.signup`.

**스크립트 동작.** `user.SignUp.js`(`$yona.loadModule("user.SignUp", {sLogindIdCheckUrl:"/user/isUsed?name=", sEmailCheckUrl:"/user/isEmailExist?email="})`):
- 클라이언트 검증은 `yona.ui.FormValidator`(`J/` 아래 validate 계열): `loginId`는 `required|callback_check_loginId`, `email`은 `required|valid_email`(**클라이언트에서 이메일 필수, 서버는 선택**), `password`는 `required|min_length[8]`, `retypedPassword`는 `required|matches[password]`(`user.SignUp.js:150-155`). 오류는 입력란 왼쪽 팝오버로 보인다(`:192-197`). 서버 정책의 흔한 비밀번호, 128자 초과, 아이디와 같은 값은 화면에서 검사하지 않고 제출 뒤 서버의 flash로만 알린다.
- 아이디 정규식 `^[a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*$`(`:60`)는 서버 정규식과 같이 한글을 허용한다(`usersec-F3`).
- 중복 확인 요청은 `sURL + encodeURIComponent(값)`이다(`:129-132`). 값을 URL 인코딩하므로 이메일의 `+`가 그대로 간다(`usersec-F4`). 결과가 `isExist`이면 `validation.duplicated`, `isReserved`이면 `validation.reservedWord`를 팝오버로 보이고 아니면 오류를 지운다(`:136-142`). 요청 주소에 컨텍스트 경로는 붙지 않는다(`T/user/signup.html:80-81`의 옵션이 `/user/isUsed?name=`, `/user/isEmailExist?email=`이고 루트 배포 전제).
- 확인 요청이 실패하면 조용히 끝난다(`:133-135`).
- 초기 포커스는 아이디 입력란(`:49`).

**단축키, 팝업.** 단축키 없음. 오류 팝오버만 있다.

**권한 행렬.** 비로그인: 사용. 로그인: 화면은 `/`로 이동, `POST /users/signup`은 로그인 여부를 검사하지 않는다. `GET /user/isUsed`, `GET /user/isEmailExist`는 인증 없이 열려 있어 존재 여부를 알 수 있다.

**시험.** `TT/web/user/SignupTest.kt`, `TT/domain/user/SignupServiceLoginIdTest.kt`, `SignupServiceEmailTest.kt`, `LoginIdRulesTest.kt`, `TT/web/user/UserApiTest.kt`.

### 3.4 `user/verified.html`

`GET /verify/{loginId}/{verificationCode}`(`SignupController.kt:80-92`). 로그인 상태면 `303 /`. 코드가 없거나 24시간이 지났으면 `404 text/plain "Invalid verification"`(화면이 아니라 평문). 성공하면 사용자를 `ACTIVE`로 바꾸고 확인 행을 지운 뒤 `user/verified`(제목 빈 문자열, `loginId`)를 그린다. 이 템플릿에는 링크, 단추, 스크립트가 없다(`T/user/verified.html:13-21`). 확인만 하고 로그인시키지는 않는다. 본문은 `user.verified`(utext), 로그인 ID, 구분선, `user.verified.detail`.

### 3.5 `user/reset-password.html` (이메일 링크 비밀번호 재설정)

**개요.** `GET /resetPassword?s=<해시>`(`PasswordResetController.kt:81-86`). `s`가 없으면 400. 토큰이 없거나 1시간이 지났거나 모르는 값이면 폼을 그리지 않고 `400` 오류 화면 `site.resetPasswordEmail.wrongUrl`이다(`usersec-F7`). 유효하면 모델 `title`(`title.resetPassword`), `hashString`. 이 요청 이전 단계(`GET /lostPassword`, `POST /lostPassword`)는 `site/lost-password.html`로 `site` 담당 영역이다. 코드와 규칙(1시간, 서버 메모리 한 개)은 2.6절.

**동작 요소.**

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 숨은 `hashString` | hidden | 항상 | 링크의 값 | `hashString` | | | `T/user/reset-password.html:22` |
| 비밀번호, 확인 입력 | 입력 | 항상 | 포커스 아웃 때 검증(필수, 8자 이상, 일치) | `password`, `retypedPassword` | | | `:25, :28`, `resetPassword.js:51-77` |
| 확인 단추 | submit | 항상 | 제출 | `POST /resetPassword` 필드 `hashString`, `password`, `retypedPassword` | 성공: **리다이렉트 없이 `200`으로 로그인 화면을 그린다**(URL은 `/resetPassword` 그대로) + flash 경고 `user.loginWithNewPassword`. 토큰 무효 `400` 오류 화면 `site.resetPasswordEmail.wrongUrl`. `retypedPassword`가 있고 `password`와 다르면 토큰은 두고 `400`으로 이 폼을 다시 그리며 flash 경고 `user.confirmPassword.alert`. SSO 사용자(사이트 관리자 제외)면 토큰을 지우고 `400 site.resetPasswordEmail.ssoUser`. 비밀번호 정책(3.3절의 `PasswordPolicy`)을 어기면 토큰은 두고 `400`으로 이 폼을 다시 그리며 flash 경고에 `validation.tooShortPassword`, `tooLongPassword`, `commonPassword`, `passwordSameAsId` 중 하나(아이디와 이메일은 토큰 사용자의 것과 비교) | 링크를 가진 누구나. 로그인 불필요 | `:33`, `PasswordResetController.kt:88-120` |

검사 순서는 토큰 유효, 확인 입력 일치, SSO 사용자 차단, 비밀번호 정책이다(`PasswordResetController.kt:91-111`). `password` 파라미터가 없으면 정책이 길이 0으로 보고 `400`으로 폼을 다시 그린다(`validation.tooShortPassword`). 성공하면 비밀번호를 argon2id로 저장, `account.password_changed`(`via=reset_token`) 감사, 서버 캐시 갱신, 토큰 삭제. 자동 로그인은 하지 않는다.

**스크립트 동작.** `resetPassword.js`: 검증 오류를 입력란 왼쪽 팝오버로 보인다. 단축키 없음.

**시험.** `TT/web/user/PasswordResetTest.kt`, `PasswordResetSsoTest.kt`.

### 3.6 `user/edit-two-factor.html` (2단계 인증 목록)

**개요.** `GET /user/editform/two-factor`(`TwoFactorSettingsController.kt:48-51`, 로그인 필요, 비로그인은 `AnonymousCheck(requiresLogin=true)`로 `303 /users/loginform?redirectUrl=...` + flash `user.login.alert`). 모델 `title`(`userinfo.twoFactor`), `tabId=two-factor`, `enabled`(확인된 수단이 하나라도 있음), `hasTotp`, `methods`(`MethodRow`: id, type=`TOTP` 또는 `WEBAUTHN`, name, createdText, lastUsedText), `backupRemaining`(남은 백업 코드 수), `devices`(알려진 기기).

**레이아웃.** 탭 줄 아래 설명 → `enabled`가 거짓이면 `#two-factor-off`(`twoFactor.list.empty`), 참이면 수단 표 `#two-factor-list`(열: 수단, 이름, 만든 날, 마지막 사용, 삭제). 이어서 `tf-actions`(추가 단추), 참일 때 백업 코드 구역(`section.tf-section`)과 알려진 기기 구역(표 `#device-list` 또는 `twoFactor.devices.empty`).

**동작 요소.**

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 수단 삭제 단추(`.btn-danger`) | 폼 submit | `enabled`, 행마다 | `confirm(twoFactor.delete.confirm)` 뒤 제출 | `POST /user/two-factor/credentials/{id}/delete` | 성공 `303 /user/editform/two-factor`. 내 것이 아니거나 없으면 `404` 오류 화면. 마지막 수단을 지우면 백업 코드와 알려진 기기도 삭제하고 2단계 해제(감사 `two_factor.disabled`) | 본인 소유 수단만 | `T/user/edit-two-factor.html:33-35`, `TwoFactorSettingsController.kt:96-102`, `K/domain/twofactor/TwoFactorService.kt:80-92` |
| 인증 앱 추가 단추 | 폼 submit | `!hasTotp` | 제출 | `POST /user/two-factor/begin` | 이미 TOTP가 있으면 `303` 목록. 아니면 미확인 TOTP 행을 지우고 새 비밀 값으로 등록 시작, 화면 3.7 | 로그인 | `:42-44`, `TwoFactorSettingsController.kt:59-66` |
| 보안 키 추가 링크(`#security-key-new`) | 링크 | 항상(브라우저가 WebAuthn을 못 쓰면 스크립트가 숨김) | 이동 | `GET /user/editform/two-factor/security-key` | 화면 3.8 | | `:45`, `:77-79` |
| 백업 코드 다시 만들기 | 폼 submit | `enabled` | `confirm(twoFactor.backup.regenerate.confirm)` 뒤 제출 | `POST /user/two-factor/backup-codes` | 2단계가 켜져 있으면 새 코드 10개를 만들어 화면 3.9, 아니면 `303` 목록. 기존 코드는 전부 대체 | 로그인 | `:51-53`, `TwoFactorSettingsController.kt:85-91` |
| 기기 삭제 단추 | 폼 submit | 기기 행마다 | 제출(확인창 없음) | `POST /user/two-factor/devices/{id}/delete` | `303` 목록, 내 것이 아니면 404 | 본인 소유 | `:65-67`, `TwoFactorSettingsController.kt:105-111` |

**남은 백업 코드 문구** `twoFactor.backup.remaining(n)`는 `#backup-remaining`. 수단 표의 "마지막 사용" 칸은 수단의 `lastUsedDate` 글자이고, 값이 비어 있으면(한 번도 쓰지 않은 수단) `apiToken.neverUsed`("사용한 적 없음")를 보인다(`T/user/edit-two-factor.html:31`, `usersec-F10`). 알려진 기기 표의 "마지막 사용" 칸(`d.lastUsedText`)은 항상 값이 있어 이 문구가 없다.

**권한과 2단계 강제.** 사이트 설정이 "관리자만"이나 "모두"로 강제하고(`settings.twoFactorEnforcement()`) 사용자가 2단계를 켜지 않았으면 이 화면과 `/user/two-factor/`, `/users/`, `/sites/security`, `/assets/`, `/messages.js`, `/favicon.ico`, `/-_-api` 외의 모든 화면은 `303 /user/editform/two-factor`+flash 경고 `twoFactor.enforced`로 막힌다(`K/security/TwoFactorEnforcementInterceptor.kt:30-49`). 예외 판정(`TwoFactorEnforcementInterceptor.kt:44-48`)은 `/user/editform/two-factor`와 그 아래 모든 경로(`path.startsWith("$SETUP_PATH/")`)를 허용하므로 보안 키 추가 화면(`/user/editform/two-factor/security-key`)도 강제 중에 열린다(`usersec-F5`). 외부 API 토큰, 익명은 대상이 아니다. 시험 `TT/web/site/SiteSecurityTest.kt:94-135`.

**시험.** `TT/web/user/TwoFactorSettingsTest.kt`, `TT/domain/twofactor/TwoFactorServiceTest.kt`, `KnownDeviceServiceTest.kt`.

### 3.7 `user/edit-two-factor-setup.html` (인증 앱 등록)

`POST /user/two-factor/begin`의 응답이 곧 이 화면이다(`GET`으로 열 수 없다. `GET /user/editform/two-factor/...`는 이 화면을 가리키지 않는다). 모델 `qrSvg`(`QrCode.svg`로 만든 인라인 SVG를 `th:utext`로 출력), `secret`(base32를 4글자씩 끊음), `setupError`. 캐시 금지 머리. 화면을 새로고침하면(POST 재전송) 미확인 TOTP를 지우고 **새 비밀 값**이 만들어진다.

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 오류 경고 | div | `setupError != null` | `twoFactor.setup.invalid` | | | `T/user/edit-two-factor-setup.html:22` |
| QR(`#two-factor-qr`), 비밀 값(`#two-factor-secret`) | 표시 | 항상 | `otpauth://totp/<사이트명>:<로그인ID>?secret=&issuer=&algorithm=SHA1&digits=6&period=30` | | | `:25, :29`, `K/domain/twofactor/Totp.kt:50` |
| 확인 코드(`#two-factor-code`) | 입력 | 항상 | `autofocus`, `inputmode=numeric` | 필드 `code` | | `:32` |
| 확인 단추 | submit | 항상 | 제출 | `POST /user/two-factor/totp` | 성공: 수단을 확인됨으로 바꾸고 백업 코드 10개를 만들어 화면 3.9(`twoFactor.backup.created`). 진행 중 등록이 없으면 `303` 목록. 코드 불일치면 `400`으로 이 화면을 다시 그리고 `twoFactor.setup.invalid`(같은 비밀 값 유지) | `:30-37`, `TwoFactorSettingsController.kt:69-82` |
| 취소 링크 | 링크 | 항상 | 이동 | `GET /user/editform/two-factor` | | `:35` |

등록 확인 때는 시도 제한이 없다(2단계 로그인과 달리 `TwoFactorAttempts`를 쓰지 않는다, `TwoFactorService.confirmTotp`, `:125-137`). 스크립트 없음. 단축키 없음.

### 3.8 `user/edit-two-factor-key.html` (보안 키 등록)

`GET /user/editform/two-factor/security-key`(`TwoFactorSettingsController.kt:54-56`). 인라인 스크립트(`T/user/edit-two-factor-key.html:37-54`):

1. 브라우저가 WebAuthn을 쓸 수 없으면 등록 단추를 막고 오류 `twoFactor.securityKey.unsupported`를 보인다.
2. `#security-key-add` 클릭 → `POST /user/two-factor/webauthn/options`(`text`로 받음) → `YonaWebAuthn.register(options)` → `POST /user/two-factor/webauthn/register` 필드 `name`, `credential` → 성공이면 `location.href = /user/two-factor/webauthn/done`.
3. 실패는 `e.responseJSON.message` 키를 풀어 `#security-key-error`에 보인다(없으면 `twoFactor.securityKey.rejected`).

서버(`K/web/user/WebAuthnSettingsController.kt`): `options`는 등록 요청을 만들어 세션 속성 `dev.yona.web.WebAuthnRegistrationRequest`에 두고 JSON 문자열을 돌려준다(캐시 금지). `register`는 그 값을 꺼내 쓰고(한 번만) 없으면 `400 {"message":"twoFactor.securityKey.rejected"}`, 검증 실패도 같다. 성공이면 `200 {"ok":true}`. 처음 등록하는 사용자(2단계가 없었음)이면 백업 코드가 생겨 세션 속성 `dev.yona.web.WebAuthnBackupCodes`에 둔다. `done`은 그 코드를 꺼내(한 번) 화면 3.9로 그리고, 코드가 없으면 `303` 목록이다. 이름은 공백을 다듬고 비어 있으면 `Security key`, 100자로 자른다(`K/domain/twofactor/WebAuthnService.kt:98`). 인증 장치 조건은 거주 키와 사용자 확인을 요구하지 않는다(`DISCOURAGED`, `:79`). 도메인은 요청의 `serverName`과 `scheme://서버이름[:포트]`로 정해진다(`K/web/user/WebAuthnContexts.kt:9-13`). 같은 자격 증명 ID가 이미 있으면 거절한다(`:94`).

이 화면의 입력(`#security-key-name`)은 `maxlength=100`이고 폼이 아니라 스크립트가 값을 읽는다. 취소 링크는 `GET /user/editform/two-factor`. 단축키 없음.

### 3.9 `user/edit-two-factor-codes.html` (백업 코드)

`POST /user/two-factor/totp` 성공, `POST /user/two-factor/backup-codes`, `GET /user/two-factor/webauthn/done`이 그리는 화면. 모델 `codes`(문자열 10개), `noticeKey`(`twoFactor.backup.created` 또는 `twoFactor.backup.regenerated`). `ul#backup-codes`에 코드를 그리고 "완료" 링크(`GET /user/editform/two-factor`)만 있다. 이 화면은 **다시 열 수 없다**(`POST` 응답과 세션 한 번 읽기). 캐시 금지 머리(`WebAuthnSettingsController.kt:66`, `TwoFactorSettingsController.kt:127-131`). 서버에는 코드의 해시(`BackupCodes.hash`)만 남는다.

### 3.10 `user/edit-gpg.html`, `user/edit-gpg-new.html` (GPG 키)

**목록(`GET /user/editform/gpg`, `GpgKeyController.kt:41-44`).** 모델 `title`(로그인 ID), `tabId=gpg`, `keys`(`GpgKeyRow`: id, name, keyId, fingerprint(4글자씩 공백), emails(쉼표 뒤 공백), createdText, expiresText, expired). `keys`가 비면 `p.empty`(`gpgKey.list.empty`), 아니면 표 `#gpg-list`(열: 이름, 키 ID, 지문, 이메일, 등록일, 만료일, 삭제). 만료일이 없으면 `gpgKey.noExpiration`, 지났으면 `(gpgKey.expired)` 표시(`T/user/edit-gpg.html:37-40`).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 삭제 단추 | 폼 submit | 행마다 | `confirm(gpgKey.delete.confirm)` 뒤 제출 | `POST /user/gpg/{id}/delete` | 성공 `303` 목록. 내 키가 아니거나 없으면 `404` | 본인 키만 | `edit-gpg.html:42-44`, `GpgKeyController.kt:66-72` |
| 추가 링크(`#gpg-new`) | 링크 | 항상 | 이동 | `GET /user/editform/gpg/new` | 추가 화면 | 로그인 | `:51` |

**추가(`edit-gpg-new.html`).** 필드 `name`(최대 100, 비우면 서버가 키의 첫 이메일이나 사용자 ID로 채움), `publicKey`(텍스트에어리어 12줄). `POST /user/gpg` → 성공 `303 /user/editform/gpg`, 거절이면 `400`으로 같은 화면에 오류와 입력값을 되돌려 준다(`gpgError`, `gpgName`, `gpgPublicKey`). 취소 링크는 목록으로. 거절 사유 메시지 키: `gpgKey.error.empty`, `malformed`, `private_key`, `multiple_keys`, `no_user_id`, `no_signing_key`, `expired`, `revoked`, `duplicate`(지문이 어느 사용자든 이미 있음), `limit`(사용자당 50개, `GpgKeyService.MAX_KEYS`), `name`(100자 초과) (`K/domain/gpgkey/GpgKeyService.kt:26-47`, 메시지 `M/messages_ko_KR.properties:1447-1457`). 스크립트와 단축키 없음. 시험 `TT/web/user/GpgKeySettingsTest.kt`, `TT/domain/gpgkey/`.

### 3.11 `user/edit-ssh.html`, `user/edit-ssh-new.html` (SSH 키)

GPG와 같은 구조이다. 목록 `GET /user/editform/ssh`(`SshKeyController.kt:38`): 열은 이름, 종류, 지문, 등록일, 삭제. 삭제 `POST /user/ssh/{id}/delete`(확인창 `sshKey.delete.confirm`, 성공 `303` 목록, 없거나 남의 키면 `404`). 추가 화면 `GET /user/editform/ssh/new`, `POST /user/ssh` 필드 `name`(최대 100), `publicKey`(8줄). 이름이 비면 키 주석, 그것도 없으면 지문을 쓴다. 거절 키: `sshKey.error.empty`, `malformed`, `private_key`, `multiple_keys`, `unsupported`, `weak`(RSA 2048비트 미만), `duplicate`(다른 사용자의 SSH 키나 어느 프로젝트의 배포 키와 지문이 같음), `limit`(50개), `name`(100자 초과)(`K/domain/sshkey/SshKeyService.kt:29-52`, 메시지 `:1492-1500`). 스크립트, 단축키 없음. 시험 `TT/web/user/SshKeySettingsTest.kt`, `TT/domain/sshkey/`.

### 3.12 개인용 API 토큰: `user/edit-token.html`, `edit-token-new.html`, `edit-token-edit.html`, `partial-token-form.html`

**목록(`GET /user/editform/token`, `AccountController.editFormByTab`, `:103-117`; `POST`로도 열린다).** 모델 `tokens`(`TokenRow`), `newToken`(만든 직후 한 번), 탭 `token`. 표 `#token-list`(열: 이름(링크), 접두사, 범위, 만든 날, 마지막 사용, 만료, 삭제). 행의 이름 링크 `a.token-edit` → `GET /user/editform/token/{id}`. 이전 버전에서 옮긴 토큰(`legacy`)에는 경고 상자 `apiToken.legacy`/`apiToken.legacy.warning`, 범위 칸에 `apiToken.scopes.unrestricted` 배지(`unrestricted`: 이전 토큰이고 범위가 없음). 마지막 사용이 없으면 `apiToken.neverUsed`, 만료가 없으면 `apiToken.noExpiration`, 지났으면 `(apiToken.expired)`.

`newToken != null`이면 성공 상자에 읽기 전용 입력 `#new-token-value`(클릭하면 전체 선택)와 숨은 `span.hide`에 같은 값이 있다. **토큰 원문은 이 응답에서만 보인다**(저장은 SHA-256 해시, `TOKEN_PREFIX`=`yona_pat_`, 무작위 40자, 접두사 13자 표시).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| "토큰 만들기" 링크(`#token-new`) | 링크 | 항상 | 이동 | `GET /user/editform/token/new` | 생성 화면 | 로그인 | `edit-token.html:66` |
| 삭제 단추 | 폼 submit | 행마다 | `confirm(apiToken.delete.confirm)` 뒤 제출 | `POST /user/token/{id}/delete` | 성공 `303 /user/editform/token`, 남의 것이거나 없으면 `404` | 본인 토큰만 | `:57-59`, `AccountController.kt:327-334` |

**생성(`edit-token-new.html`, `GET /user/editform/token/new`, `:88`).** 폼 `#token-create`에 `data-submit-once`가 있다(`T/user/edit-token-new.html:23`). 제출하는 순간(제출 이벤트 다음 순서에) 이 폼의 `[type=submit]` 단추를 모두 잠가 같은 요청이 두 번 가지 않게 한다. 다른 스크립트가 제출을 취소(`defaultPrevented`)하면 잠그지 않고, 뒤로 가기로 돌아와 페이지가 복원되면(`pageshow`, `persisted`) 단추를 푼다(`J/esm/ui/SubmitOnce.js:19-29`, `J/esm/index.js:41`에서 불러온다). 이 속성은 토큰 편집 화면의 폼에는 없다. `POST /user/token` 필드 `name`(최대 100, 공백 다듬기, 비면 거절), `expiration`(`7`, `30`, `60`, `90`, `none`, `custom`), `expiresDate`(`custom`일 때 `yyyy-MM-dd`, 엄격 파싱, 그날 23:59:59까지), `scopes`(여러 값). 성공이면 목록 화면을 `200`으로 그리면서 `newToken`을 보인다(리다이렉트 아님). 거절은 `400`으로 생성 화면에 `tokenError`: `apiToken.error.scope`(모르는 범위, 선택 불가 범위. 범위는 비어도 허용), `apiToken.error.expires`(형식 오류, 과거, 최대 일수 초과, `apiTokenMaxDays`>0인데 `none`), `apiToken.error.name`(빈 이름이거나 100자 초과), `apiToken.error.limit`(사용자당 50개)(`K/domain/apitoken/ApiTokenService.kt:77-82, 158-163`, `K/config/YonaProperties.kt:114`). 만료 선택 칸의 `none` 옵션은 `maxDays <= 0`일 때만 보인다(`T/user/partial-token-form.html:63`). 범위 목록은 `ApiScopes.selectable(사이트 관리자 여부)`이고 순서는 코드 정의 그대로이다. 사이트 관리자만 `site_admin`이 보인다(`K/domain/apitoken/ApiScopes.kt:43`). 기본 만료 옵션은 30일이 선택됨.

**편집(`edit-token-edit.html`, `GET /user/editform/token/{id}`, `:94-101`).** 세 개의 폼이 한 화면에 있다.
- `#token-edit`: `POST /user/token/{id}` 필드 `name`, `scopes`. 성공 `303 /user/editform/token`(값은 그대로, 만료는 바꿀 수 없음). 거절 `400` 화면 재표시. 범위 선택 목록은 선택 가능 범위에 토큰이 이미 가진 범위를 더한 것이다(`:370-372`).
- `#token-regenerate`: `POST /user/token/{id}/regenerate` 필드 `expiration`, `expiresDate`. `confirm(apiToken.regenerate.confirm)`. 성공이면 새 값을 목록 화면에 `200`으로 보인다(기존 값 폐기). 거절은 `400`으로 편집 화면.
- 삭제 폼: `POST /user/token/{id}/delete`, `confirm(apiToken.delete.confirm)`.
토큰이 없거나 남의 것이면 모두 `404` 오류 화면.

**`partial-token-form`.** `tokenStyle`(인라인 `<style>`: 카드, 범위 목록 스타일. 폭 고정 값 `.token-scopes` 최대 722px, `.token-field input` 최대 420px, `.token-expiry select` 240px, `.token-custom input` 140px), `nameField`, `scopeField`(체크박스 `name=scopes` 각각 `code`와 설명), `expiryField`(선택 상자 + 사용자 지정 달력), `tokenScripts`.

**스크립트 동작(`tokenScripts`, `partial-token-form.html:73-92`).** `vanilla-calendar-pro`(`J/lib/vanilla-calendar-pro/index.js`)와 `esm/ui/Calendar.js`를 불러, `.token-expiry`마다 `yona.ui.Calendar.inline`으로 항상 보이는 달력을 만든다. 날짜를 고르면 `input[name=expiresDate]`가 채워지고, 쓴 값은 blur에서 달력에 반영된다. 선택 가능 날짜는 내일부터(`minDate = now + 24h`). `expiration` 선택 상자가 `custom`일 때만 `.token-custom`이 보인다. 목록 화면에는 `user.Setting.js`도 로드되지만 이 화면에서 쓸 요소가 없다.

**단축키.** 없음. **팝업.** `confirm` 대화상자만.

**시험.** `TT/web/user/ApiTokenSettingsTest.kt`, `TT/domain/apitoken/ApiTokenServiceTest.kt`.

### 3.13 `user/edit-linked-accounts.html` (연결된 계정)

**개요.** `GET /user/editform/linked-accounts`(`LinkedAccountsController.kt:29-32`). 모델 `title`(`userinfo.linkedAccounts`), `rows`(`LinkedAccountService.list`: id, providerKey, subject). 비면 `#linked-empty`(`linked.empty`), 아니면 표 `#linked-list`(열: 제공자, 식별자, 해제). 제공자 칸은 `sso-oidc`→`linked.provider.oidc`, `sso-saml2`→`linked.provider.saml2`, 그 밖은 키 그대로(`T/user/edit-linked-accounts.html:26-30`).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 연결 해제 단추 | 폼 submit | 행마다 | `confirm(linked.unlink.confirm)` 뒤 제출 | `POST /user/linked-accounts/{id}/unlink` | `303 /user/editform/linked-accounts`+flash: 성공 `linked.unlinked`, SSO 전용 모드 켜짐이면 거절 flash 경고 `linked.unlink.ssoOnly`(성공하지 않음), 내 연결이 아니면 경고 `linked.notFound` | 본인 연결만 | `:33-35`, `LinkedAccountsController.kt:35-45`, `K/domain/sso/LinkedAccountService.kt:39-44` |

SSO 전용 모드가 꺼져 있으면 어떤 연결이든(소셜 포함) 해제할 수 있고, 해제하면 그 계정으로 더는 로그인할 수 없다(확인 문구 `linked.unlink.confirm`). 시험 `TT/web/user/LinkedAccountsTest.kt`, `TT/domain/sso/SsoAccountServiceTest.kt`. 스크립트와 단축키 없음. 연결 추가 단추는 이 화면에 없다(연결은 SSO 로그인으로 생성).

## 4. 권한 행렬 (이 글의 화면)

| 기능 | 비로그인 | 로그인 | 비고 |
|---|---|---|---|
| 로그인, 가입, 재설정 화면과 확인 | 사용 | `GET`은 `/`로 이동(`loginform`, `signupform`, `verify`) | 재설정 `GET /resetPassword`는 로그인 여부와 무관 |
| `/user/editform/*`, `/user/token*`, `/user/gpg*`, `/user/ssh*`, `/user/two-factor/*`, `/user/linked-accounts/*` | `303 /users/loginform?redirectUrl=<경로>`(`GET`이면 질의 문자열을 `%3F`로 인코딩해 이어 붙임)+flash 경고 `user.login.alert` | 본인 것만 | 모두 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`. 다른 사용자의 id는 `404`(소유 확인) |
| 2단계 강제 중 | 해당 없음 | 켜지 않은 사용자는 예외 경로 외 `303` | 3.6절 |
| 사이트 관리자 | 일반 사용자와 같다 | 토큰 범위에 `site_admin` 추가 | `/{user}` 관리자 재설정은 [user.md](user.md) |

그룹 관리자, 프로젝트 멤버 같은 역할 구분은 이 글의 화면에 없다.

## 5. 메시지 키 점검

이 글에서 다룬 템플릿(`user/` 전체, `oauth/`, `oauth2/`)의 `#{...}` 리터럴 키 261개가 `messages_ko_KR.properties`와 `messages.properties`에 모두 있다(스크립트 검사, 누락 0). 동적 키 `'apiToken.scope.' + 범위`는 선택 가능한 모든 범위(콜론이 `\:`로 이스케이프된 키 포함)와 `openid`, `profile`, `email`이 있고, `offline_access`만 없다(승인 화면 범위에 이 값이 들어오는 경우는 허용 범위 밖이면 라이브러리가 거부하므로 도달하지 않는다고 본다, 추측).

## 6. 발견 사항

| 번호 | 내용(확인한 사실) | 근거 |
|---|---|---|
| usersec-F1 **[브라우저로 확인됨 2026-11-11, R-160, 수정함(A-462)]** | 로그인 화면의 포커스 스크립트가 `#loginIdOrEmail`을 찾지만 입력란 id는 `loginIdOrEmailD`라 자동 초점이 동작하지 않는다(2026-11-11 수정: 입력란 id를 `loginIdOrEmail`로 바꿔 초점이 간다) | `T/user/login.html:28, :61` |
| usersec-F2 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 서버가 비밀번호 길이(4자)와 `retypedPassword` 일치를 확인하지 않는다. 가입, 비밀번호 변경(`/user/resetPassword`), 재설정(`/resetPassword`) 모두 클라이언트 검증에만 의존한다. 가입은 v1.6 화면도 오류 표시가 없었다(v1.6 `signup.scala.html`에 오류 출력 없음)(2026-11-11 수정: 가입, 비밀번호 변경, 재설정 모두 `retypedPassword`가 요청에 있고 `password`와 다르면 서버가 `400`과 flash `user.confirmPassword.alert`로 거절한다(`retypedPassword`를 보내지 않는 요청은 검사하지 않는다). 길이는 `PasswordPolicy`가 8자 이상 128자 이하로, 흔한 비밀번호와 아이디나 이메일과 같은 값도 서버에서 거절한다(A-465, `validation.tooShortPassword`, `tooLongPassword`, `commonPassword`, `passwordSameAsId`)) | `K/domain/user/SignupService.kt:49`, `AccountController.kt:131-149`, `PasswordResetController.kt:88-120` |
| usersec-F3 **[검증: 수정함(A-464, 2026-11-11)]** | 가입 화면 아이디 정규식은 한글 불허, 서버 정규식은 한글 허용(2026-11-11 수정: 화면 정규식이 `가-힣`을 허용하고, 서버 정규식은 `_`, `.`를 연달아 쓰지 못하게 바꿔 두 규칙이 같다) | `user.SignUp.js:60`, `LoginIdRules.kt:16` |
| usersec-F4 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 가입 화면의 중복 확인이 값을 URL 인코딩하지 않고 이어 붙인다(`+`가 공백으로 읽힘)(2026-11-11 수정: 값을 `encodeURIComponent`로 인코딩해 보낸다) | `user.SignUp.js:129-132` |
| usersec-F5 **[재현됨, 수정됨, A-458]** | 2단계 강제의 예외 판정이 경로 완전 일치라서 `GET /user/editform/two-factor/security-key`(보안 키 추가 화면)는 강제 중에 목록 화면으로 되돌려진다. 이 경로의 시험은 없다. 인증 앱 추가(`/user/two-factor/begin`)는 예외라 통과한다(2026-11-11 수정: 예외 판정이 `/user/editform/two-factor` 아래 경로도 허용한다) | `K/security/TwoFactorEnforcementInterceptor.kt:47, :53` |
| usersec-F6 **[브라우저로 확인됨 2026-11-11, R-161]** | 로그인 필요 화면의 로그인 이동이 `redirectUrl=`에 요청 경로만 싣는다. 질의 문자열은 사라지고, `POST` 요청이면 로그인 뒤 `GET`으로 이동해 405가 404 화면으로 바뀐다(예: `GET /users/device?user_code=...`의 코드)(2026-11-11 수정: `GET` 요청이면 질의 문자열도 `redirectUrl`에 `%3F`와 함께 인코딩해 싣는다. `POST` 등 `GET`이 아닌 요청의 질의 문자열은 여전히 버려지고, 로그인 뒤 `GET` 이동의 405가 404 화면이 되는 점은 그대로이다) | `K/web/access/AnonymousCheckStage.kt:31-33`, `K/web/error/GlobalErrorHandler.kt:66-71` |
| usersec-F7 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 재설정 링크 화면은 토큰을 검증하지 않고 폼을 그린다. 검증은 제출할 때만 한다. 링크는 서버 메모리에만 있어 재시작하면 모두 무효(2026-11-11 수정: `GET /resetPassword`가 토큰을 먼저 확인해 없거나 만료된 값이면 `400 site.resetPasswordEmail.wrongUrl`이다. 링크가 서버 메모리에만 있어 재시작하면 무효인 점은 그대로이다) | `PasswordResetController.kt:81-86, :93`, `K/security/PasswordResetTokens.kt:17-18` |
| usersec-F8 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 재설정 성공 응답이 리다이렉트가 아니라 로그인 화면을 `200`으로 직접 그려 URL이 `/resetPassword`로 남는다(v1.6 동작을 따른 것, 주석에 명시) | `PasswordResetController.kt:119-121` |
| usersec-F9 **[검증: 문서 문제]** | 위키와 코드의 불일치: `subsystems/authentication.md`는 자동 로그인 쿠키를 `yobi.token=<ID>:<해시된 패스워드>`로, "CSRF 보호를 끈다"로 적었지만 코드는 `yona.token=<ID>:<만료>:<서명>`이고 CSRF 보호가 켜져 있다(`R-02`, `R-05`). `refactoring-backlog.md` R-107은 잠긴 계정의 쿠키 로그인을 v1.6처럼 허용한다고 하나 코드는 거절한다(`user.state.isLockedOut()`) | `K/security/YonaRememberMeServices.kt:30-35`, `K/config/SecurityConfig.kt:60-64` |
| usersec-F10 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 2단계 수단 목록의 "마지막 사용" 칸은 한 번도 쓰지 않은 수단에서 빈 칸이다(`안 씀` 같은 문구가 없다). 알려진 기기 표의 `lastUsedText`는 항상 값이 있다(2026-11-11 수정: 값이 비어 있으면 `apiToken.neverUsed`("사용한 적 없음")를 보인다) | `K/web/user/TwoFactorSettingsController.kt:133-134`, `K/web/TemplateHelper.kt:41-45`, `T/user/edit-two-factor.html:31` |
| usersec-F11 **[검증: 수정함(A-464, 2026-11-11)]** | 비밀번호 로그인에 실패 횟수 제한과 지연이 없다. 잠긴 계정과 삭제된 계정은 비밀번호 확인 전에 상태 안내가 나가 계정 존재와 상태가 드러난다(가입 승인제일 때 `Locked`, `Deleted`는 항상)(2026-11-11 수정: 같은 로그인 키 5회 실패 15분 잠금(429 `user.login.tooMany`)과 삭제된 계정의 일반 실패 응답을 넣었다. 가입 승인제의 `Locked` 안내는 비밀번호 확인 전에 나가는 그대로이다) | `YonaPasswordAuthenticationProvider.kt:66-67`, `PasswordLoginFilter.kt:55-56`, `PasswordLoginHandlers.kt:149-165` |
| usersec-F12 **[검증: 수정함(A-464, 2026-11-11)]** | 2단계 시도 카운터와 재설정 링크는 서버 메모리라 재시작하거나 서버를 여럿 두면 공유되지 않는다(2026-11-11 수정: 2단계 시도 카운터는 `attempt_record` 표에 저장해 재시작해도 남고 여러 서버가 공유한다. 재설정 링크는 서버 메모리 그대로이다) | `TwoFactorAttempts.kt`, `AttemptRecord.kt:20-27`, `PasswordResetTokens.kt:17` |
| usersec-F13 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `GET /users/logout`(상태를 바꾸는 GET)이 CSRF 대상이 아니다(v1.6과 같음, `refactoring-backlog.md` R-03 관련은 `Referer`가 없을 때 `Location: null`)(2026-11-11 수정: `Referer`가 없으면 `Location: /`이다. `GET` 로그아웃이 CSRF 대상이 아닌 점은 그대로이다) | `K/web/user/LoginController.kt:44-52` |
| usersec-F14 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `th:utext`로 `title.loginFor(site.siteName)`, `title.signupFor`, `title.resetPasswordFor`를 출력하므로 사이트 이름의 HTML이 이스케이프되지 않는다(사이트 관리자 입력값) | `T/user/login.html:18`, `signup.html:15`, `reset-password.html:15` |
| usersec-F15 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `LoginDialog.js`와 로그인 화면이 같은 입력 id `loginIdOrEmailD`를 쓴다(팝업이 있는 화면에서 로그인 화면으로 이동하면 팝업 DOM은 없으니 충돌하지 않으나 `common/login-dialog.html`이 들어간 화면에 `/users/*`가 끼어들면 중복 id가 된다. 추측)(2026-11-11 수정: 로그인 화면 입력란 id를 `loginIdOrEmail`로 바꿔 팝업의 `loginIdOrEmailD`와 겹치지 않는다) | `T/common/login-dialog.html:33`, `T/user/login.html:28` |
| usersec-F16 **[검증: 수정함(A-464, 2026-11-11)]** | `POST /user/token`의 성공 응답이 `303`이 아니라 목록 화면 직접 렌더라 새로고침하면 토큰을 중복 생성하려는 재전송이 된다(원문 값을 한 번만 보이기 위한 구조)(2026-11-11 수정: 토큰 만들기 폼에 `data-submit-once`를 달아 제출 뒤 단추를 잠근다. 응답이 `303`이 아닌 직접 렌더인 점과 새로고침 재전송은 그대로이다) | `AccountController.kt:282-285` |
| usersec-F17 **[재조사 2026-11-11]** | 비밀번호 로그인 잠금의 키가 입력한 글자 그대로(앞뒤 공백 제거, 소문자)라서 같은 사용자라도 로그인 ID와 이메일은 각자 5회씩 따로 센다. 잠금은 입력 키 단위이지 계정 단위가 아니다 | `PasswordLoginHandlers.kt:141-144` |
| usersec-F18 **[재조사 2026-11-11]** | 가입에서 `hasErrors`(형식, 중복, 공백 비밀번호)에 걸린 오류는 메시지 없이 `400`이고, 그 뒤 단계(확인 입력 불일치, 비밀번호 정책, 이메일 도메인)의 오류만 flash로 알린다. 흔한 비밀번호, 128자 초과, 아이디와 같은 값은 화면 스크립트가 검사하지 않아 제출한 뒤에야 알 수 있다 | `SignupController.kt:44-69`, `user.SignUp.js:99-109` |

## 7. 범위 점검

| 템플릿 | 다룬 절 |
|---|---|
| `user/login.html` | 3.1 |
| `user/login-two-factor.html` | 3.2 |
| `user/signup.html` | 3.3 |
| `user/verified.html` | 3.4 |
| `user/reset-password.html` | 3.5 |
| `user/edit-two-factor.html` | 3.6 |
| `user/edit-two-factor-setup.html` | 3.7 |
| `user/edit-two-factor-key.html` | 3.8 |
| `user/edit-two-factor-codes.html` | 3.9 |
| `user/edit-gpg.html`, `user/edit-gpg-new.html` | 3.10 |
| `user/edit-ssh.html`, `user/edit-ssh-new.html` | 3.11 |
| `user/edit-token.html`, `user/edit-token-new.html`, `user/edit-token-edit.html`, `user/partial-token-form.html` | 3.12 |
| `user/edit-linked-accounts.html` | 3.13 |
| (이 글에서 다루지 않은 `user/` 템플릿) | `view`, `partial-issues`, `partial-projectlist`, `partial-pull-requests`, `files`, `edit`, `edit-emails`, `edit-notifications`, `edit-password`, `edit-audit`, `partial-edit-tabmenu`는 [user.md](user.md), `device`, `edit-oauth-apps`, `edit-oauth-app-form`, `edit-oauth-app-secret`는 [oauth.md](oauth.md) |

## 갱신 기록 (2026-11-11 재조사)

- 다시 확인한 템플릿: 18개(`user/login`, `login-two-factor`, `signup`, `verified`, `reset-password`, `edit-two-factor`, `edit-two-factor-setup`, `edit-two-factor-key`, `edit-two-factor-codes`, `edit-gpg`, `edit-gpg-new`, `edit-ssh`, `edit-ssh-new`, `edit-token`, `edit-token-new`, `edit-token-edit`, `partial-token-form`, `edit-linked-accounts`). 이 가운데 바뀐 것은 `login`(입력란 id), `edit-two-factor`(`neverUsed`), `edit-token-new`(`data-submit-once`)이다. 나머지 15개는 변경이 없고 인용 줄이 그대로임을 확인했다.
- 다시 확인한 소스: `PasswordLoginFilter`, `PasswordLoginHandlers`, `PasswordConfirmation`, `LoginController`, `LoginFormController`, `SignupController`, `PasswordResetController`, `AccountController`, `AnonymousCheckStage`, `TwoFactorEnforcementInterceptor`, `AttemptRecord`, `TwoFactorAttempts`, `PasswordPolicy`, `LoginIdRules`, `user.SignUp.js`, `resetPassword.js`, `SubmitOnce.js`.
- 고친 행과 문장: 약 22곳(2.1 3번과 번호, 2.2 표 0행과 4행과 문단, 2.3 표 4행과 감사 문단과 실패 횟수 문단, 2.4 `AnonymousCheckStage`, 2.6 표 비밀번호 로그인 행과 2단계 행, 3.1 개요와 스크립트와 요소 표 2행, 3.3 비밀번호 행과 가입 단추 행과 서버 규칙과 스크립트 5곳, 3.5 개요와 요소 표 2행과 문단, 3.6 마지막 사용 문구와 강제 예외, 3.12 생성 폼, 4절 권한 행렬, 5절 키 개수).
- 줄 번호만 고친 곳: 약 24곳(`SignupController`, `AccountController`, `PasswordLoginHandlers`, `RedirectSupport`, `LoginFormController`, `user.SignUp.js`, `TwoFactorAttempts`).
- 더한 행: 2.2 표 0행, 2.3 표 `TooManyAttempts` 행, 2.6 표 기기 승인 코드 행, 2.6 `attempt_record` 문단, 발견 사항 2행.
- 없앤 행: 없음(옛 동작 문장은 현재 동작으로 바꿈).
- 새 발견 사항: `usersec-F17`(잠금 키가 입력 글자 단위), `usersec-F18`(가입 오류 알림 범위와 화면 검사 격차).
- 기존 발견 사항에 수정 표시를 더한 것: F1, F2, F3, F4, F5, F6, F7, F10, F11, F12, F13, F15, F16.
