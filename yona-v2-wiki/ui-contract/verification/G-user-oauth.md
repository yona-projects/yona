---
title: 계약 발견 사항 재현과 v1.6 대조 G (사용자 페이지, 로그인, 2단계 인증, 가입, 재설정, 키와 토큰, OAuth, 기기 승인)
type: ui-contract-verification
updated: 2026-11-11
---

# G 담당 검증 결과

v2 앱(포트 19416, 사본 `work/G`)과 공식 v1.16.0(포트 19516, 스냅샷 data2)을 Playwright와 스크립트 없는 직접 요청으로 같은 요청을 보내 비교했다. 2단계 인증은 화면의 비밀 키를 읽어 RFC 6238(SHA-1, 6자리, 30초)로 직접 계산해 실제 등록, 로그인, 오입력 잠금을 재현했다. 소스 읽기는 보조 근거이다.

## 요약

- 항목 수 32건. 전부 재현을 시도했고 전부 판정했다. 재현 불가 0건.
- 판정별: v2 신규 결함 1, v2 추가 기능의 결함 9, v1.6 결함 유지 18, 결함 아님 3, 문서 문제 1.
- **v2 신규 결함**: `user-F7`(익명이 `/user/files`를 열면 로그인 ID가 `anonymous`인 실제 사용자의 파일이 보인다. v1.6의 익명 로그인 ID는 빈 문자열이라 노출되지 않았다).
- **v2 추가 기능의 결함**: `oauth-F1`, `oauth-F2`, `oauth-F3`, `oauth-F4`, `oauth-F5`, `oauth-F6`, `usersec-F10`, `usersec-F12`, `usersec-F16`.
- 발견 사항 서술이 틀린 곳: `usersec-F9`(R-107 서술이 v1.6 실행 결과와 다르다), `usersec-F14`(사이트 이름은 관리자 입력이 아니라 설정 파일 값), `usersec-F15`(중복 id가 지금도 생긴다), `usersec-F10`(TOTP 행에는 빈 칸이 없고 보안 키 행만 빈 칸), `oauth-F9`(문제 없음).

## 사용한 도구와 데이터

- v2: `plainuser`, `admin`, 가입으로 만든 `tfuser`(2단계 인증 등록, 보안 키 등록은 CDP 가상 인증기), `pwchg1`, `delx`, `anonymous`(가입으로 만든 로그인 ID `anonymous` 사용자).
- v1.6: `tester`, `admin` 외에 같은 가입 요청으로 `pd0`, `delx`, `anonymous` 등을 만들었다. 재설정 주소는 v1.6 로그(`logs/root.log`)에 찍히는 `resetPassword?s=` 값을 썼다(`smtp.mock`).
- v2 재설정 토큰은 서버 메모리에만 있어 내 앱의 힙 덤프에서 40자 16진수 후보를 뽑아 시도해 한 건이 통과함을 확인했다(내 사본 전용).
- 스크립트는 `work/G/scripts/`.

## 판정 표

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| oauth-F1 | 재현됨 | `homeUrl=https://home.example.com/app`인 앱 `AppPub`을 만들고 `/oauth2/authorize`(PKCE)로 동의 화면을 열었다(`/oauth2/consent?scope=read:user&client_id=...&state=...`). 본문에 `home.example.com`이 없다(`AppPub이(가) 계정 접근을 요청합니다`, 만든 이 `plainuser`, 루프백 경고, 범위 `read:user`만 있음) | 실행 불가(v1.6에 OAuth 서버 없음). v2 추가 기능 | v2 추가 기능의 결함 | 고친다. `T/oauth2/consent.html`의 제목 아래에 `homeUrl`이 있으면 링크로 보인다(모델에는 이미 있음, `OAuthConsentController.kt:53`). 링크는 `http`/`https`일 때만, `rel="noopener nofollow"` |
| oauth-F2 | 재현됨 | 기밀 앱 `ConfApp`(id 3) 수정 화면의 라디오는 `public:disabled`, `confidential:checked:disabled`. 이름을 비우고 저장하면 `400`, 같은 화면의 라디오가 `public:checked:disabled`, `confidential:` 로 바뀐다(`work/G/o4_f2.png`). 비밀 값 다시 만들기 구역은 그대로 보인다(서버는 종류를 바꾸지 않음) | 실행 불가. 추가 기능 | v2 추가 기능의 결함 | 고친다(경미). `OAuthAppsSettingsController.kt:164-167`의 오류 재표시에서 수정이면 `Form.of(request).copy(confidential = client.confidential == true)` |
| oauth-F3 | 재현됨 | 같은 기밀 앱 만들기 `POST /user/oauth-apps`를 두 번 보내 둘 다 `200`(비밀 값 화면)이고 목록에 `ConfApp`이 2개가 되었다. `POST /user/oauth-apps/3/secret`을 두 번 보내 `yona_cs_FLUW7...`, `yona_cs_OxexN...`로 값이 매번 바뀐다 | 실행 불가. 추가 기능 | v2 추가 기능의 결함 | 고친다 또는 장부. 비밀 값을 세션에 한 번 읽기로 두고 `303` 뒤 `GET`으로 보여 주는 방식(2단계 백업 코드 `WebAuthnBackupCodes`와 같은 패턴). `OAuthAppsSettingsController.kt:78, :110` |
| oauth-F4 | 재현됨 | 동의 화면의 `state`로 `POST /oauth2/authorize`(`csrf:false`, 헤더 `X-XSRF-TOKEN`과 `_csrf` 없음) 요청에 `302 http://localhost:9999/cb?code=...&state=...`가 왔다. 일회용 `state`만으로 승인된다 | 실행 불가. 추가 기능 | v2 추가 기능의 결함(설계상 한계) | 장부에 올린다. `state`가 추측 불가이고 세션 소유자 확인이 있어 위험은 낮다. 막으려면 `AuthorizationServerConfig.kt:74`에서 이 경로만 CSRF 검사를 켜고 템플릿에 `_csrf` 입력을 둔다 |
| oauth-F5 | 재현됨 | 로그인한 `plainuser`로 틀린 사용자 코드 `GET /users/device?user_code=BCDF-xxxx` 150번을 3.2초에 보냈고 150번 모두 `400`(제한 없음, `429` 없음) | 실행 불가. 추가 기능 | v2 추가 기능의 결함 | 고친다. `DeviceAuthorizationController`의 코드 해석 실패에 사용자별 횟수 제한(예: `TwoFactorAttempts`와 같은 5회/15분) |
| oauth-F6 | 재현됨 | CLI 쪽(비로그인)이 `POST /-_-api/v1/oauth/device/code`로 `JLWB-HRDF`를 받고, 다른 세션의 `plainuser`가 `/users/device?user_code=JLWB-HRDF`에서 `#device-approve`를 눌렀다(`기기를 승인했습니다`). CLI의 `POST /-_-api/v1/oauth/token`이 `200 {"access_token":"yona_pat_...","scope":"read:user"}`를 받았다 | 실행 불가. 추가 기능 | v2 추가 기능의 결함(기기 인증 방식 자체의 한계) | 장부에 올린다. RFC 8628 방식의 알려진 한계이고 화면 경고(`deviceAuth.consent.warning`)가 완화책이다. 서버에서 확인할 방법은 없다 |
| oauth-F7 | 재현됨 | 비로그인 `GET /oauth2/consent?...` `303 /users/loginform?redirectUrl=/oauth2/consent`, `GET /users/device?user_code=ABCD-EFGH` `303 /users/loginform?redirectUrl=/users/device`, `POST /users/device/approve` `303 /users/loginform?redirectUrl=/users/device/approve`. 브라우저로 로그인한 뒤 `/users/device`에 도착해 코드 입력란은 빈 값이었다 | 같음(원인 공통). v1.6 비로그인 `POST /user/resetVisitedList`가 `303 /user/editform`이고 따라가면 `/users/loginform?redirectUrl=/user/editform`로 질의 문자열 없이 경로만 쓴다. `app/actions/AnonymousCheckAction.java:46`(`context.request().path()`) | v1.6 결함 유지(`/users/device` 화면 자체는 추가 기능이지만 원인은 v1.6 공통 코드) | 장부(`usersec-F6`, R-161)와 함께 처리. `AnonymousCheckStage.kt:29`에서 `requestURI`에 질의 문자열을 덧붙이는 정도 |
| oauth-F8 | 재현됨 | 관리자가 `POST /sites/security/oauth`(`userApps` 없음)로 끄고 `plainuser`로: `GET /user/editform/oauth-apps/new` `403`, `POST /user/oauth-apps` `403`, `GET`/`POST` 수정 `200`/`303`, 비밀 값 다시 만들기 `200`, 삭제 `303`. 목록에 `#user-apps-disabled`가 있고 `#oauth-app-new` 링크는 없다 | 실행 불가. 추가 기능 | 결함 아님(서술은 정확하나 의도된 동작) | 조치 없음. 계약 3.1절이 이미 "만들기만 막는다"를 정책으로 적고 있다. 바꾸려면 정책 결정이 필요하다 |
| oauth-F9 | 재현됨 | `scope=offline_access`, `read:user offline_access`(그리고 `openid`)를 요청하면 동의 화면 없이 `302 http://localhost:9999/cb?error=invalid_scope`. 소스 어디에도 `offline_access` 범위가 없다(`grep` 결과 0건) | 실행 불가 | 결함 아님 | 조치 없음. 범위 자체가 없으니 메시지가 없는 것이 맞다. 추측이던 서술은 확인되었다 |
| usersec-F2 | 재현됨 | 가입 `POST /users/signup`에 `password=a`, `retypedPassword=zzz` 로 `303 /`, 이어 `shortpw1`/`a`로 로그인 `303 /`. 로그인 상태에서 `POST /user/resetPassword`로 `x`/`y` `303 /users/loginform`, `x`로 로그인 성공. `POST /resetPassword`에 유효 토큰, `retypedPassword=zzz`, 비밀번호 4자 이상: `200`(로그인 화면)로 `retypedPassword`를 보지 않는다 | v1.6에 같은 요청: 가입 `a`/`zzz` `303 /`, 로그인 `a` `303 /`, `/user/resetPassword`로 `x`/`x`, `abcdef`/`zzz`, `abc`/`abc` 모두 `303 /users/loginform`, 새 비밀번호로 로그인 성공. `UserApp.java:1237-1258`(`validate`에 길이 검사 없음), `:582-607` | v1.6 결함 유지 | 장부에 올린다(R-). 참고: v1.6은 이 요청에 `loginId` 필드가 없으면 `400`(`@Required`)이었고 v2는 필요 없다(더 느슨함) |
| usersec-F3 | 재현됨 | 가입 화면에서 `한글아이디`를 입력하고 포커스를 옮기면 팝오버 "아이디는 알파벳 숫자 - _ . 를 포함할 수 있으나 ..."가 뜬다. 같은 이름으로 직접 `POST /users/signup`(`한글사용자`)하면 `303 /`, `GET /user/isUsed?name=한글사용자` `{"isExist":true,"isReserved":false}` | v1.6도 같은 팝오버, 같은 직접 가입 `303 /`, 같은 `isUsed` 응답. `yobi.user.SignUp.js:59`, `User.java:79`(서버 패턴에 한글) | v1.6 결함 유지 | 장부에 올린다 |
| usersec-F4 | 재현됨 | 가입 화면 이메일 `pl+us@example.com` 입력 후 포커스 아웃: 요청 `GET /user/isEmailExist?email=pl+us@example.com`(인코딩 없음). 직접 `email=shortpw1+tag@example.com`은 `{"isExist":false}`, `%2B`로 보내면 `{"isExist":true}` | v1.6도 같은 요청 주소와 같은 응답 둘(`false`, `true`). `yobi.user.SignUp.js:128-130` | v1.6 결함 유지 | 장부에 올린다. 고친다면 `user.SignUp.js`의 `doesExists`에서 `encodeURIComponent(welInput.val())` |
| usersec-F7 | 재현됨 | `GET /resetPassword?s=fakefake` `200`(폼이 뜸), `s` 없으면 `400`, `POST /resetPassword`에 가짜 토큰 `400`(`Wrong url to reset password.`). 유효 토큰으로 제출하면 `200` | 같음. `GET /resetPassword?s=fakefake` `200`, 없으면 `400`, 가짜 `POST` `400`. `PasswordResetApp.java:99-101` | v1.6 결함 유지 | 장부에 올린다 |
| usersec-F8 | 재현됨 | 유효 토큰으로 `POST /resetPassword` `200`, `Location` 없음, 본문은 로그인 화면(주소는 `/resetPassword`로 남음) | 같음. 브라우저로 제출 `POST` `200`, 주소 `/resetPassword`, 제목 `Yona 로그인`. `PasswordResetApp.java:115-116` | v1.6 결함 유지 | 장부에 올린다(v1.6 동작을 따른 것) |
| usersec-F9 | 재현됨 | 쿠키는 `yona.token=plainuser:<만료>:<서명>`(`yobi.token` 아님). 인증 없는 `POST`(토큰 없음)는 `403`이라 CSRF 보호가 켜져 있다. 사용자를 잠그고(`/sites/toggleAccountLock`) 쿠키만 가진 새 컨텍스트로 `/user/editform/profile`을 열면 `/users/loginform?redirectUrl=/user/editform/profile`로 간다(거절) | v1.6 쿠키 이름은 `yobi.token`(`yobi.token=tester:Ki+5i...`). 잠근 사용자의 쿠키로 열면 같은 곳으로 보낸다: 잠금 전에는 `/user/editform/profile` 열림(대조), 잠근 뒤에는 `/users/loginform?redirectUrl=/user/editform/profile`. 즉 R-107의 "잠긴 계정도 쿠키로 로그인된다"는 서술은 v1.6 실행 결과와 다르다(소스 `UserApp.java:626-697`을 읽어 그렇게 추론했으나 실행하면 거절) | 문서 문제 | 문서만 고친다. `subsystems/authentication.md`(쿠키 이름 `yona.token`, 구성 `ID:만료:서명`, CSRF 켜짐)와 `refactoring-backlog.md` R-107(v1.6도 잠긴 계정의 쿠키는 거절한다. v2도 같음) |
| usersec-F10 | 재현됨(보안 키 행만) | 2단계 수단 목록: TOTP 행은 등록(10:08:47)과 마지막 사용(10:08:47, 확인 때 기록됨, 로그인 뒤 10:10:48로 갱신)이 모두 값이 있다. 가상 인증기로 등록한 보안 키 `vkey`는 등록일 `10:10:49`, 마지막 사용이 빈 칸(`WebAuthnService.kt:97-101`에서 `lastUsedDate`를 설정하지 않음) | 실행 불가. 2단계 인증은 v2 추가 기능 | v2 추가 기능의 결함(경미, 서술은 보안 키 행으로 좁혀야 정확) | 고친다(경미). `TwoFactorSettingsController.kt:133`에서 `lastUsedDate`가 null이면 `twoFactor.neverUsed` 같은 문구(`apiToken.neverUsed` 선례)를 쓴다 |
| usersec-F11 | 재현됨 | 로그인 실패 12번(`wrongN`)을 `plainuser`로 연속해도 모두 `303 /users/loginform?redirectUrl=`, 뒤이은 맞는 비밀번호는 `303 /`(32ms). 지운 계정 `delx`: 틀린 비밀번호도 맞는 비밀번호도 `404 {"message":"user.deleted"}`, 없는 계정은 `403 user.login.invalid`. 잠금 계정에 틀린 비밀번호는 `403 user.login.invalid`(가입 승인제가 아니라 안내 없음) | 같음. 12번 실패 후 맞는 비밀번호 `303 /`. 지운 `delx`: 틀린 것과 맞는 것 모두 `404 user.deleted`. `UserApp.java:205-217` | v1.6 결함 유지 | 장부에 올린다 |
| usersec-F12 | 재현됨(2단계 카운터) | `tfuser`로 틀린 코드를 5번 넣으니 5번째에 `/users/loginform`으로 쫓겨나고 맞는 코드도 거절(`/users/loginform`)된다. 앱을 다시 시작하자 같은 맞는 코드로 로그인 성공(`/`): 잠금이 사라졌다. 재설정 링크도 서버 메모리(`PasswordResetTokens`) 한 건만 유효 | 재설정 링크의 메모리 보관은 v1.6도 같다(`PasswordReset.java`, v1.6 실행에서 가짜 토큰만 `400`). 2단계 카운터는 v1.6에 없다 | v2 추가 기능의 결함(`D-69`가 이미 인정한 한계) | 장부에 올린다. 여러 서버가 되면 `TwoFactorAttempts`를 DB나 공유 캐시로 |
| usersec-F13 | 재현됨 | 로그인 상태에서 토큰 없이 `GET /users/logout`(`Referer` 없음)이 `303`, `Location: null`(글자 그대로) | 같음. `GET /users/logout` `303`, `Location: null` | v1.6 결함 유지 | 장부(R-03) |
| usersec-F14 | 재현됨 | 사이트 이름을 `Y<b id=xss>BOLD</b><i>it</i>`로 해서 띄운(`--yona.site-name`) v2의 `/users/loginform`, `/users/signupform`, `/resetPassword?s=x`에서 제목이 `<span class="highlight">Y<b id="xss">BOLD</b><i>it</i></span>`로 이스케이프 없이 나온다. 서술과 달리 사이트 이름은 관리자 화면 입력이 아니라 설정 값(`yona.site-name`)이다 | 같음. `-Dapplication.siteName=...`로 띄운 v1.6도 세 화면 모두 같은 HTML. `login.scala.html:40`, `signup.scala.html:29`, `resetPassword.scala.html:14`(`@Html(...)`) | v1.6 결함 유지 | 장부에 올린다. 값을 넣는 사람이 운영자(설정 파일)뿐이라 위험은 낮다 |
| usersec-F15 | 재현됨 | `/users/loginform`에 `#loginIdOrEmailD`가 2개(로그인 폼 + 로그인 팝업), 그 밖의 화면(`/`, `/users/signupform`, `/lostPassword`)은 1개. 서술의 "충돌하지 않는다"는 틀림(지금 이미 로그인 화면에서 중복) | 같음. 같은 화면 2개, 나머지 1개. 자동 초점도 없음(`activeElement`가 `body`). `login.scala.html:57`, `loginDialog.scala.html:31` | v1.6 결함 유지 | 장부에 올린다(`usersec-F1`과 함께) |
| usersec-F16 | 재현됨 | `POST /user/token`(이름 `dup`) 같은 요청을 두 번 보내 둘 다 `200`이고 목록에 `dup`이 2개 생겼다. 화면에서 만든 `f16token`의 응답도 `200`(`newToken` 표시, 주소 `/user/token`) | 실행 불가(v1.6의 사용자 토큰은 `GET /user/editform/token_reset` 한 개 방식이라 해당 없음). 개인용 토큰은 v2 추가 기능 | v2 추가 기능의 결함 | `oauth-F3`과 같은 방식으로 고친다(`AccountController.kt:277-279`에서 새 값을 세션 한 번 읽기로 두고 `303 /user/editform/token`) |
| user-F1 | 재현됨 | `POST /user/editform/password` `200`, `GET`도 같은 화면 | 같음(`POST` `200`). `conf/routes:136-137` | v1.6 결함 유지 | 조치 없음(v1.6 라우트 재현) |
| user-F2 | 재현됨 | 로그인하지 않은(토큰만 가진) `POST /user/resetVisitedList` `303 /user/editform`, 따라가면 `/users/loginform?redirectUrl=/user/editform`. 토큰 없는 요청은 `403` | 같음. `303 /user/editform` 뒤 `/users/loginform?redirectUrl=/user/editform`. `UserApp.java:609-613` | v1.6 결함 유지 | 조치 없음 |
| user-F3 | 재현됨 | `Accept-Language: en-US`로 보조 이메일 추가 뒤 `POST /user/email/sendValidationEmail/1` `303`. 화면(`lang=en-US`)에 `확인 메일을 전송했습니다` 한국어 문구가 그대로 있다 | 같음. 같은 순서로 한국어 문구. `UserApp.java:1097` | v1.6 결함 유지 | 장부(메시지 키 도입은 v1.6과 다름) |
| user-F4 | 재현됨 | `/plainuser?selected=issues`에서 `#projects` 탭을 누르고(활성 탭이 `#projects`로 바뀜) `#daysAgoBtn`에 5를 쓰고 Enter: 주소가 `/plainuser?daysAgo=5&selected=issues`, 활성 탭은 `#issues`로 돌아감 | 같음. `/tester?daysAgo=5&selected=issues`. `yobi.user.View.js`와 같은 구조 | v1.6 결함 유지 | 장부 |
| user-F6 | 재현됨 | 보조 이메일 확인 링크(`/user/email/confirm/1/<토큰>`)를 쿠키 없는 새 컨텍스트로 열면 `/user/editform`로 가고 로그인 상태(메뉴 `프로필 페이지`). 2단계 인증을 켠 `tfuser`의 확인 링크는 `/users/login/2fa`로 간다(코드 확인 뒤 로그인) | 같음(2단계 없이 곧바로 로그인). `UserApp.java:1102-1115` | v1.6 결함 유지(2단계 쪽 방어는 v2 추가) | 장부 |
| user-F7 | 재현됨 | 가입으로 만든 로그인 ID `anonymous` 사용자가 `POST /files`로 `anonsecret.txt`를 올리고(`201`, id 85), 비로그인 세션의 `GET /user/files`가 `200`이고 목록에 `anonsecret.txt`가 보인다 | 같은 순서로 v1.6: `anonymous` 사용자가 `anonsecret.txt` 업로드 `201`, 비로그인 `GET /user/files` `200`, 목록에 **보이지 않음**. `NullUser.java`: 익명의 `loginId`가 `""`라 조회 조건이 `owner_login_id=""`이다 | v2 신규 결함 | 고친다. `UserFilesController.kt:66`의 `ANONYMOUS_LOGIN_ID`를 `"anonymous"`가 아니라 `""`로(주석의 "v1.6 `User.anonymous.loginId`"는 틀린 사실이었다). 또는 비로그인이면 빈 목록. 가입 불가 예약어에 `anonymous`를 넣는 방법도 있으나 v1.6과 달라진다 |
| user-F8 | 재현됨 | `/user/files`에서 `filter`에 `alpha`를 쳐 Enter: 주소 `/user/files?filter=alpha`, 입력란 값은 `""`, `beta.txt`는 숨겨짐(검색은 동작). 콘솔 `searche!`는 나오지 않는다(죽은 코드) | 같음. 입력란 `""`, 콘솔에 아무것도 없음. `userFiles.scala.html:38, :86-87` | v1.6 결함 유지 | 장부 |
| user-F9 | 재현됨 | 비로그인 `GET /plainuser`에 구독 단추(`a.watchBtn href=/plainuser/ownerproj/watch` 등 3개)가 보인다. 비로그인 `POST /plainuser/ownerproj/watch`는 `303 /users/loginform?redirectUrl=/plainuser/ownerproj/watch`(서버가 막음) | 같음. 비로그인 `/tester`에 `a.watchBtn` 3개, `POST /tester/proj1/watch` `303 /users/loginform?...`. `partial_projectlist.scala.html:38` | v1.6 결함 유지 | 장부(`requireNotNull` 걱정은 접근 검사 체인이 먼저 막아 없음) |
| user-F10 | 재현됨 | `plainuser/ownerproj`에 초안(`isDraft=true`) 이슈를 만들고 `/plainuser?selected=issues&daysAgo=3`: 이슈 배지 `이슈 3`, 하위 탭 `열림 1`, `닫힘 1`(합 2). 초안 제목은 목록에 없다. 프로젝트 쪽 `?state=draft`에는 보인다 | 같음. 브라우저 폼(`#draft-save-btn`)으로 만든 v1.6 초안: 배지 `이슈 2`, 하위 탭 `열림 1`, `닫힘 0`, 초안 제목 없음. `User` 이슈 목록 구성 | v1.6 결함 유지 | 장부 |
| user-F11 | 해당 없음(재현할 결함 서술이 없음) | 발견 사항이 "위키와 코드의 불일치는 발견하지 못했다"는 점검 결과 문장이다. 이 담당 범위 밖의 세부 대조는 하지 않았다 | 해당 없음 | 결함 아님 | 조치 없음 |

## 범위 점검

| 항목 | 있음 |
|---|---|
| oauth-F1 | 예 |
| oauth-F2 | 예 |
| oauth-F3 | 예 |
| oauth-F4 | 예 |
| oauth-F5 | 예 |
| oauth-F6 | 예 |
| oauth-F7 | 예 |
| oauth-F8 | 예 |
| oauth-F9 | 예 |
| usersec-F2 | 예 |
| usersec-F3 | 예 |
| usersec-F4 | 예 |
| usersec-F7 | 예 |
| usersec-F8 | 예 |
| usersec-F9 | 예 |
| usersec-F10 | 예 |
| usersec-F11 | 예 |
| usersec-F12 | 예 |
| usersec-F13 | 예 |
| usersec-F14 | 예 |
| usersec-F15 | 예 |
| usersec-F16 | 예 |
| user-F1 | 예 |
| user-F2 | 예 |
| user-F3 | 예 |
| user-F4 | 예 |
| user-F6 | 예 |
| user-F7 | 예 |
| user-F8 | 예 |
| user-F9 | 예 |
| user-F10 | 예 |
| user-F11 | 예 |

## 부수 관찰

- v2의 `POST /user/resetPassword`는 폼에 `loginId`가 없어도 동작한다. v1.6은 `loginId`가 필수여서 `400`이었다(`@Required`). 화면 폼은 `loginId`를 보내므로 화면에서는 차이가 없다.
- 위 `usersec-F5`(수정됨), `usersec-F6`, `usersec-F1`은 이 담당 목록에 없어 다시 확인하지 않았다. 다만 이번 실행에서 보안 키 추가 화면(`/user/editform/two-factor/security-key`)은 2단계 사용자로 정상 열렸다.
