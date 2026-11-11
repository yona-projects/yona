---
title: 사용자 영역 화면 계약 3 - OAuth 동의 화면, OAuth 앱 설정, 기기 승인
type: ui-contract
sources:
  - src/main/resources/templates/oauth2/consent.html
  - src/main/resources/templates/oauth/partial-apps.html
  - src/main/resources/templates/user/edit-oauth-apps.html
  - src/main/resources/templates/user/edit-oauth-app-form.html
  - src/main/resources/templates/user/edit-oauth-app-secret.html
  - src/main/resources/templates/user/device.html
  - src/main/kotlin/dev/yona/oauth2server/OAuthConsentController.kt
  - src/main/kotlin/dev/yona/oauth2server/OAuthResumeController.kt
  - src/main/kotlin/dev/yona/oauth2server/AuthorizationServerConfig.kt
  - src/main/kotlin/dev/yona/web/user/OAuthAppsSettingsController.kt
  - src/main/kotlin/dev/yona/web/user/DeviceAuthorizationController.kt
  - src/main/kotlin/dev/yona/web/OAuthAppViewSupport.kt
  - src/main/kotlin/dev/yona/web/OAuthAppUrls.kt
  - src/main/kotlin/dev/yona/domain/oauth2server/OAuthClientService.kt
  - src/main/kotlin/dev/yona/domain/oauth2server/OAuthAuthorizedAppsService.kt
  - src/main/kotlin/dev/yona/domain/deviceauth/DeviceAuthorizationService.kt
updated: 2026-11-11
---

# 사용자 영역 화면 계약 3: OAuth 동의 화면, OAuth 앱 설정, 기기 승인

이 글은 `oauth2/consent.html`, `oauth/partial-apps.html`, `user/edit-oauth-*.html`, `user/device.html`을 다룬다. 이 기능들은 모두 v1.6에 없는 추가 개발 항목이다(X-1, X-37i, X-39; `decisions.md` D-71, D-52, D-78). 서버 구조는 `architecture/oauth2-server.md`, `architecture/api-token.md`가 이미 설명하므로 여기서는 화면이 서버와 맺는 계약만 적는다. 줄임말 `T/`=`src/main/resources/templates/`, `K/`=`src/main/kotlin/dev/yona/`, `M/`=`src/main/resources/`, `TT/`=`src/test/kotlin/dev/yona/`. 인증과 쿠키 규칙은 [user-security.md](user-security.md) 2절.

## 1. 영역 요약

| 템플릿 | URL | 컨트롤러 | 용도 |
|---|---|---|---|
| `oauth2/consent.html` | `GET /oauth2/consent?client_id=&scope=&state=` (라이브러리가 `GET /oauth2/authorize`에서 보냄) | `K/oauth2server/OAuthConsentController.kt:34-60` | 외부 앱의 계정 접근 승인 또는 거부 |
| `oauth/partial-apps.html` | (조각 `mine`, `appForm`, `secret`) | 사용자 설정과 조직 설정이 함께 사용(`T/user/edit-oauth-*.html`, 조직 화면) | OAuth 앱 목록, 만들기/수정 양식, 비밀 값 화면 |
| `user/edit-oauth-apps.html` | `GET /user/editform/oauth-apps` | `K/web/user/OAuthAppsSettingsController.kt:52-59` | 내가 만든 앱 + 내가 승인한 앱 |
| `user/edit-oauth-app-form.html` | `GET /user/editform/oauth-apps/new`, `GET /user/editform/oauth-apps/{id}`, 오류 시 `POST /user/oauth-apps`, `POST /user/oauth-apps/{id}`, `POST /user/oauth-apps/{id}/transfer` 400 | `OAuthAppsSettingsController.kt:61-124` | 앱 만들기, 수정, 소유자 이전 |
| `user/edit-oauth-app-secret.html` | `POST /user/oauth-apps`(기밀 앱 성공), `POST /user/oauth-apps/{id}/secret` | `OAuthAppsSettingsController.kt:69-81, :104-112` | 클라이언트 비밀 값 한 번 표시 |
| `user/device.html` | `GET /users/device?user_code=`, `POST /users/device/approve`, `POST /users/device/deny` | `K/web/user/DeviceAuthorizationController.kt:33-60` | 명령줄 도구(기기)의 접근 승인 |

## 2. OAuth 승인 화면 `oauth2/consent.html`

### 2.1 개요와 흐름

1. 외부 앱이 `GET /oauth2/authorize`로 사용자를 보낸다(스프링 인가 서버 필터 체인, `K/oauth2server/AuthorizationServerConfig.kt:55-92`). 앱은 `requireProofKey(true)`(PKCE)와 `requireAuthorizationConsent(true)`로 등록된다(`K/oauth2server/YonaRegisteredClientRepository.kt:44`).
2. 로그인하지 않았으면 `/users/loginform?redirectUrl=/oauth2/resume`로 보낸다(`AuthorizationServerConfig.kt:140-150`). 요청 자체는 세션에 보관되고 로그인(2단계 인증 포함)이 끝나면 `GET /oauth2/resume`이 보관한 요청(`/oauth2/authorize`로 시작하는 값만)으로 `303`한다. 보관한 요청이 없거나 형식이 다르면 `/`(`K/oauth2server/OAuthResumeController.kt:17-25`).
3. 승인이 필요하면 라이브러리가 `GET /oauth2/consent?client_id=&scope=<공백으로 이은 범위>&state=<일회용 값>`로 보낸다(`CONSENT_PAGE`, `AuthorizationServerConfig.kt:124`).

### 2.2 컨트롤러 검사

`OAuthConsentController.consent`(`:34-60`)는 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`. 응답 머리: `Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`, `Content-Security-Policy: frame-ancestors 'none'`, `X-Frame-Options: DENY`(다른 사이트가 끼워 넣지 못하게). 다음 중 하나면 `400` 오류 화면: 로그인 사용자 없음, `state` 없음 또는 비어 있음, `state`로 찾은 승인 요청이 없음, 그 승인 요청의 주체가 현재 사용자가 아님, 앱이 없음, 쿼리의 `client_id`가 앱과 다름. 승인 요청이 만료되었거나 모르는 요청도 같다. 시험 `TT/oauth2server/OAuthConsentPageTest.kt:99-144`.

모델: `clientName`, `clientId`, `homeUrl`(모델에 있지만 템플릿은 쓰지 않는다, `oauth-F1`), `ownerLoginId`(사용자 소유 앱), `ownerOrganization`(조직 소유 앱의 조직 이름), `restrictedOrganizations`(내가 속하고 이 앱을 승인하지 않은, 앱 제한 정책을 켠 조직의 이름), `redirectHost`(요청한 리디렉션 주소의 호스트), `loopback`(호스트가 `localhost`, `127.0.0.1`, `[::1]`, `::1` 중 하나), `state`, `cimdHost`(클라이언트 ID 메타데이터 문서로 등록된 앱이면 클라이언트 ID의 호스트), `scopes`(쿼리 `scope`를 공백으로 나눈 것).

### 2.3 레이아웃 계약

`layout/site-layout`을 `'NONE'`으로 부르고 본문은 `div.page.full` 안에 제목 영역(`div.center-wrap.tag-line-wrap.login`)과 폼 영역(`div.login-form-wrap.frm-wrap.oauth-consent.tf-challenge`)이다(`T/oauth2/consent.html:13-43`). 제목 `oauth.consent.heading(앱 이름)`, 부제 `oauth.consent.by(소유자 로그인 ID)` 또는 `oauth.consent.byOrganization(조직)`(둘 중 해당하는 쪽만).

위에서 아래 순서: 경고 상자(`#consent-warnings`, 제한 조직이 있거나 `loopback`일 때만), CIMD 안내(`#cimd-note`, `cimdHost != null`), "요청하는 권한" 문구, 범위 목록(`ul#oauth-scopes`: `code` 범위 이름 + 설명 `apiToken.scope.<범위>`), 돌아갈 주소 안내(`redirectHost`가 비어 있지 않을 때 `oauth.consent.returnsTo`), 허용 폼, 거부 폼, 취소 안내 `oauth.consent.revokeNote`.

경고: 제한 조직 경고 `#org-policy-warning`(`oauth.consent.orgRestricted(조직 이름을 ', '로 이음)`), 루프백 경고 `#loopback-warning`(`oauth.consent.loopback`). 둘 다 있으면 루프백 줄에 `mt-2`가 붙는다.

### 2.4 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 허용(`#consent-allow`) | 폼 submit(`#consent-form`) | 항상 | 제출 | `POST /oauth2/authorize` 필드 `client_id`, `state`, `scope`(범위마다 하나씩) | 라이브러리가 승인을 저장하고 인가 코드와 `state`를 앱의 리디렉션 주소로 `302`(라이브러리 동작) | 로그인 사용자 본인의 승인 요청(`state`가 그 사용자의 것) | `T/oauth2/consent.html:30-35`, `AuthorizationServerConfig.kt:55-92` |
| 거부(`#consent-deny`) | 폼 submit(`#consent-deny-form`) | 항상 | 제출 | `POST /oauth2/authorize` 필드 `client_id`, `state`(범위 없음) | 앱의 리디렉션 주소로 `access_denied` 오류를 돌려보냄 | 같음 | `:36-40`, 시험 `OAuthConsentPageTest.kt:144` |

폼에 `_csrf`를 넣는 서버 코드는 없다. 인가 서버 체인은 `csrf { it.disable() }`이므로 이 `POST`에는 요청 위조 방지 토큰 검사가 없고, 대신 추측할 수 없는 일회용 `state`가 위조를 막는다(`AuthorizationServerConfig.kt:74`). 다만 `yona.Csrf.js`가 로드되는 화면이면 `_csrf` 입력이 더해져도 무해하다(`common/scripts.html`로 로드되는 것은 확인하지 않았다, 이 템플릿에 스크립트 태그는 없다). 허용 폼은 **요청된 범위 전체**를 그대로 보내며 사용자가 범위를 골라 줄일 수 없다(시험 `OAuthConsentPageTest.kt:104`).

스크립트, 단축키, 모달, 툴팁 없음. 이 화면은 인라인 스크립트가 없다.

### 2.5 오류와 경계

- 요청한 `redirect_uri`가 등록된 주소와 맞지 않으면 사용자를 그 주소로 보내지 않고 오류 화면을 낸다(루프백은 포트를 무시, `RedirectUris.matches`). 조직 정책이 막은 앱의 경우 그 조직의 자원은 익명과 같게 본다(`K/access/AccessControl.kt:104-106`).
- 요청한 범위가 앱이 허용한 범위 밖이면 라이브러리 기본 검증이 거부한다(`AuthorizationServerConfig.kt:104-105`).
- 앱이 쓸 수 없는 범위(`site_admin`, SSH, GPG, 배포 키 범위)는 앱 등록 단계에서 막힌다(`K/domain/oauth2server/OAuthClientService.kt:50-62`).

시험: `OAuthConsentPageTest`, `OAuth2AuthorizationFlowTest`, `OAuthResumeAfterLoginTest`, `OAuthResumeAfterLdapLoginTest`, `OAuthOrgPolicyEnforcementTest`(모두 `TT/oauth2server/`).

## 3. OAuth 앱 설정 (사용자 탭 `oauth-apps`)

### 3.1 공통 조건

모든 컨트롤러 메서드가 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`. 비로그인은 `303 /users/loginform?redirectUrl=<경로>`. 탭 줄은 `user/partial-edit-tabmenu`(탭 id `oauth-apps`). 설정 화면 모델 `title=userinfo.oauth`, `tabId=oauth-apps`, `urls=OAuthAppUrls.USER`(`K/web/OAuthAppUrls.kt:29-31`): 목록 `/user/editform/oauth-apps`, 새 앱 `/user/editform/oauth-apps/new`, 만들기 `POST /user/oauth-apps`, 수정 화면 `/user/editform/oauth-apps/{id}`, 동작 `POST /user/oauth-apps/{id}[/secret|/transfer|/delete]`. 조각 `oauth/partial-apps`는 조직 설정(`urls = OAuthAppUrls.organization(base)`)도 같은 양식으로 쓴다(조직 쪽 화면 계약은 조직 영역 담당).

사이트 설정 `oauthUserAppsEnabled`(기본 참, 값이 없으면 참, `K/domain/site/SiteSettingService.kt:45`)가 거짓이면 사용자의 앱 만들기가 막힌다(목록, 승인한 앱 보기와 수정, 삭제는 계속 가능).

### 3.2 목록 `user/edit-oauth-apps.html`

`GET /user/editform/oauth-apps`. 모델 `apps`(`AppRow`: id, name, clientId, confidential, redirectCount, createdText), `authorized`(`AuthorizedRow`), `userAppsEnabled`. 구성: 설명 `oauth.description`(`div.two-factor-page` 안), 조각 `mine(apps, urls, userAppsEnabled, 'oauth.apps.mine', 'oauth.apps.mine.empty')`, 승인한 앱 구역(`section.tf-section`).

**내가 만든 앱(`mine`, `T/oauth/partial-apps.html:12-37`).** 비면 `#my-apps-empty`(`oauth.apps.mine.empty`), 아니면 표 `#my-apps`(열: 이름(수정 링크), 클라이언트 ID(`code`), 종류(`oauth.type.confidential`/`public`), 만든 날, 삭제). 아래 `tf-actions`에 `userAppsEnabled`가 참이면 새 앱 링크(`#oauth-app-new`), 거짓이면 문구 `#user-apps-disabled`(`oauth.apps.disabled`).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 앱 이름 링크 | 링크 | 행마다 | 이동 | `GET /user/editform/oauth-apps/{id}` | 수정 화면. 내 앱이 아니면 `404` | 소유자 | `partial-apps.html:20`, `OAuthAppsSettingsController.kt:83-89` |
| 삭제 | 폼 submit | 행마다 | `confirm(oauth.apps.delete.confirm)` 뒤 제출 | `POST /user/oauth-apps/{id}/delete` | 성공 `303 /user/editform/oauth-apps`, 없거나 남의 앱이면 `404` 오류 화면. 앱의 승인과 토큰도 함께 지움(`removeClient`) | 소유자만(관리자는 사이트 관리 화면에서 따로) | `:25-27`, `OAuthAppsSettingsController.kt:126-132` |
| 새 앱 링크 | 링크 | `userAppsEnabled` | 이동 | `GET /user/editform/oauth-apps/new` | 사용 안 함이면 `403` | 로그인 | `:33`, `:61-67` |

**내가 승인한 앱(`edit-oauth-apps.html:26-46`).** 비면 `#authorized-empty`, 아니면 표 `#authorized-apps`(열: 이름, 만든 이(`ownerOrganization`이면 `oauth.apps.byOrganization(조직)`, 아니면 `ownerLoginId`, 둘 다 없으면 `host`=CIMD 호스트), 범위(`code`), 승인한 날, 취소). 취소 폼: `confirm(oauth.apps.authorized.revoke.confirm)` 뒤 `POST /user/oauth-apps/authorized/{clientRowId}/revoke`. 성공 `303` 목록(승인과 발급된 토큰을 함께 지우고 감사 `oauth.authorization_revoked`). 승인한 적이 없으면 `404`(`K/domain/oauth2server/OAuthAuthorizedAppsService.kt:54-67`). 목록은 최근 승인 순, 지워진 앱은 건너뛴다(`:38-52`).

### 3.3 만들기와 수정 양식 `user/edit-oauth-app-form.html` (조각 `appForm`)

`GET /user/editform/oauth-apps/new`(사용자 앱 사용 안 함이면 `403`), `GET /user/editform/oauth-apps/{id}`(내 앱 아니면 `404`). 양식 모델: `client`(수정이면 `id`, `clientId`, `confidential` 맵), `form`(`name`, `description`, `homeUrl`, `redirectUris`, `confidential`, `scopes`), `formError`, `scopeChoices`(`OAuthScopes.forApps(oidc.enabled)`: OIDC가 켜져 있으면 `openid`, `profile`, `email`이 앞에, 이어서 선택 가능한 API 범위에서 `site_admin`과 SSH, GPG, 배포 키 범위를 뺀 것), `oidcInfo`(OIDC가 켜져 있고 수정일 때 `issuer`, `discovery`=`<기준 주소>/.well-known/openid-configuration`), `transferTargets`.

레이아웃(`T/oauth/partial-apps.html:40-118`): 오류 경고, 제목(`oauth.form.title.new` 또는 `.edit`), 수정이면 `클라이언트 ID: <code#edit-client-id>`, OIDC 정보 구역(`#oidc-info`), 양식 `#oauth-app-form`(이름 `maxlength=100`, 설명 `maxlength=500`, 홈페이지 `maxlength=500` 자리표시자 `https://example.com`, 리디렉션 주소 텍스트에어리어 4줄, 종류 라디오, 범위 체크박스 목록, 저장 단추와 취소 링크), 기밀 앱 수정 때 비밀 값 다시 만들기 구역, 이전 구역(`#oauth-transfer`). 입력 폭 클래스 `tf-group-wide w-100`.

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 이름, 설명, 홈페이지, 리디렉션 주소 | 입력 | 항상 | | 필드 `name`, `description`, `homeUrl`, `redirectUris`(줄 또는 공백 구분 목록) | | | `partial-apps.html:55, :59, :63, :67` |
| 종류 라디오(`type`) | 라디오 2개(`public`, `confidential`) | 항상. 수정이면 `disabled` | 만들 때만 선택(기본은 `form.confidential`에 따름, 새 양식은 공개) | 필드 `type` | 수정 화면에서는 값이 전송되지 않으므로 서버는 종류를 바꾸지 않는다 | | `:74` |
| 범위 체크박스 | 체크박스 | `scopeChoices`마다 | `form.scopes`에 있으면 체크(새 양식 기본 `read:user`) | 필드 `scope`(여러 값) | | | `:84`, `OAuthAppsSettingsController.kt:161` |
| 만들기/저장 | submit | 항상 | 제출 | 만들기 `POST /user/oauth-apps`, 수정 `POST /user/oauth-apps/{id}` | 만들기: 기밀이면 비밀 값 화면(`200`), 공개면 `303 /user/editform/oauth-apps/{id}`. 수정 성공: `303` 수정 화면+flash 성공 `oauth.apps.saved`. 거절: `400`으로 양식 재표시 | 로그인 | `:90`, `OAuthAppsSettingsController.kt:69-101` |
| 취소 링크 | 링크 | 항상 | 이동 | `GET /user/editform/oauth-apps` | | | `:91` |
| 비밀 값 다시 만들기 | 폼 submit | 수정이고 `client.confidential` | `confirm(oauth.secret.regenerate.confirm)` 뒤 제출 | `POST /user/oauth-apps/{id}/secret` | 새 비밀 값을 `200`으로 한 번 보이는 화면(`oauth.secret.regenerated`), 앱이 없으면 `404` | 소유자 | `:94-100`, `OAuthAppsSettingsController.kt:104-112` |
| 소유자 이전 | 폼 submit(`#oauth-transfer-form`) | 수정이고 이전 대상이 있음 | 제출(확인창 없음, 앱 이름 직접 입력으로 확인) | `POST /user/oauth-apps/{id}/transfer` 필드 `target`(`user` 또는 `org:<조직이름>`), `confirm`(앱 이름) | 성공 `303 /user/editform/oauth-apps`+flash `oauth.transfer.done`. 거절 `400`+양식 재표시: 대상 불일치 `oauth.transfer.error.target`, 같은 소유자 `oauth.transfer.error.same`, 이름 불일치 `oauth.transfer.error.confirm`, 대상의 한도 `oauth.error.limit` | 소유자이면서 대상 조직의 관리자(`ORG_ADMIN`)이거나 대상이 본인 | `:101-117`, `OAuthAppsSettingsController.kt:114-124`, `K/web/OAuthAppViewSupport.kt:42-56`, `K/domain/oauth2server/OAuthClientService.kt:179-190` |

이전 대상 목록(`transferTargets`): 현재 소유자가 사용자이면 내가 `ORG_ADMIN`인 조직들(이름 순), 현재 소유자가 조직이면 "내 계정"(`oauth.transfer.myAccount`)과 다른 조직들. 사용자가 소유한 앱에서 대상이 비어 있으면(내가 관리하는 조직이 없음) 구역이 보이지 않는다.

서버 검증(`OAuthClientService.invalid`, `:243-250`): 이름 공백 제거 후 1~100자(`oauth.error.name`), 설명 500자 이하(`oauth.error.description`), 홈페이지가 있으면 http 또는 https 주소이고 500자 이하(`oauth.error.homeUrl`), 리디렉션 주소 1~10개이고 각각 유효(https만, http는 localhost만, `oauth.error.redirect`), 범위가 비어 있지 않고 모두 허용된 것(`oauth.error.scope`), 소유자당 앱 20개(`oauth.error.limit`). 상수 정의 `OAuthClientService.kt:260-269`. 클라이언트 ID는 `yona_app_` + 20자, 비밀 값은 `yona_cs_` + 40자이며 비밀 값은 SHA-256만 저장한다.

이 양식은 수정 오류 시 `Form.of(request)`로 값을 되돌리는데 `type` 라디오는 비활성이라 전송되지 않으므로 기밀 앱의 오류 재표시에서 라디오가 `공개`로 체크되어 보인다(`oauth-F2`).

### 3.4 비밀 값 화면 `user/edit-oauth-app-secret.html` (조각 `secret`)

`POST /user/oauth-apps`(기밀 앱 성공)와 `POST /user/oauth-apps/{id}/secret`의 응답이 곧 이 화면이다(`GET`으로 열 수 없다). 캐시 금지 머리(`OAuthAppViewSupport.noCache`). 구성: 성공 안내(`oauth.secret.created` 또는 `oauth.secret.regenerated`), 앱 이름, 클라이언트 ID(`code#client-id`), 비밀 값(`code#client-secret`), `oauth.secret.once` 안내, "완료" 링크(`GET /user/editform/oauth-apps/{id}`). 비밀 값은 이 응답 이후 다시 볼 수 없다(해시만 저장). 새로고침(POST 재전송)은 새 앱을 중복 생성하거나 비밀 값을 다시 만든다(`oauth-F3`).

### 3.5 스크립트, 단축키, 팝업

이 영역의 OAuth 설정 템플릿 4개(`edit-oauth-apps`, `-form`, `-secret`, `partial-apps`)에는 인라인 스크립트와 전용 JS 모듈이 없다. 사용하는 상호작용은 모두 서버 `form`과 인라인 `onsubmit="return confirm(...)"`이다(메시지 안의 작은따옴표는 `th:attr`로 이스케이프). `yona.Csrf.js`가 폼 제출 때 `_csrf` 입력을 더한다. 단축키 없음. 모달 없음.

### 3.6 권한 행렬

| 기능 | 비로그인 | 로그인 사용자 | 사이트 관리자 |
|---|---|---|---|
| 앱 목록, 수정, 삭제, 비밀 값, 이전 | `303` 로그인 | 내가 소유한 앱만(`owned(owner, id)`, 아니면 404) | 사용자 설정에서는 일반 사용자와 같다. 전체 앱 목록과 삭제는 사이트 관리 화면(`SiteOAuthAppsTest`) |
| 앱 만들기 | `303` 로그인 | `oauthUserAppsEnabled`일 때만, 아니면 `403` | 같음 |
| 승인한 앱 취소 | `303` 로그인 | 내 승인만 | 같음 |

시험: `TT/web/user/OAuthAppsSettingsTest.kt`, `TT/domain/oauth2server/OAuthClientServiceTest.kt`, `OAuthAuthorizedAppsServiceTest.kt`, `TT/web/OAuthAppFormWidthTest.kt`.

## 4. 기기 승인 `user/device.html`

### 4.1 개요

명령줄 도구(`yona-cli` 등)가 기기 인증 흐름(RFC 8628형)을 시작하면 서버가 `device_code`(숨김, 40자 소문자 숫자)와 `user_code`(`XXXX-XXXX`, 알파벳 `BCDFGHJKLMNPQRSTVWXZ`)를 발급한다. 사용자는 이 화면에서 코드를 확인해 승인하거나 거부한다. 상수: 유효 900초(`EXPIRES_IN`), 폴링 간격 5초(`INTERVAL`), 너무 자주 폴링하면 5초씩 늘림, 만료된 코드는 하루 보관 뒤 삭제(`K/domain/deviceauth/DeviceAuthorizationService.kt:70-89`). 승인되면 CLI가 `Yona CLI: <이름>`이라는 이름의 개인용 토큰을 받는다(같은 이름의 기존 토큰은 삭제).

한 템플릿이 모양 세 개를 그린다(모델 `mode`). 세 컨트롤러 메서드 모두 `@AnonymousCheck(requiresLogin=true)`(flash 메시지 없음)이다.

### 4.2 모양 `form`: 코드 입력

`GET /users/device`에 `user_code`가 없거나 공백이면 코드 입력 폼(`#device-code-form`, `method=get`, `action=/users/device`)을 그린다(`DeviceAuthorizationController.kt:36-37`). 입력 `#device-user-code` 필드 `user_code`(`maxlength=9`, `autofocus`, 폭 100%), 단추 `deviceAuth.code.submit`(`ybtn-success`). 코드가 없거나 만료되었거나 이미 처리되었으면 `400`으로 같은 모양에 경고(`deviceAuth.error.notFound`, `.alert.alert-danger`)(`:38`).

코드 해석(`findByUserCode`, `DeviceAuthorizationService.kt:117-122`): 입력에서 글자(`isLetter`)만 남겨 대문자로 바꾸고 정확히 8글자가 아니면 못 찾은 것, 4글자씩 `XXXX-XXXX`로 찾고, 만료 전이며 상태 `PENDING`인 것만 인정한다. 그래서 하이픈, 공백, 소문자 입력이 모두 통한다. 시도 횟수 제한이 없다(`oauth-F5`).

### 4.3 모양 `consent`: 요청 확인과 승인

코드가 유효하면 같은 `GET`이 확인 화면(`#device-consent`)을 그린다. 모델 `request`(`userCode`, `clientName`, `scopes`), `account`(로그인한 사용자의 로그인 ID). 제목 `deviceAuth.consent.title(clientName)`, 설명 `deviceAuth.consent.description(account)`, 범위 목록(`ul#device-scopes`, 항목마다 `code` 범위 + 설명 `apiToken.scope.<범위>`), 경고 `deviceAuth.consent.warning(userCode)`(`T/user/device.html:42-65`). 범위 목록은 요청한 범위 중 **이 사용자가 실제로 줄 수 있는 것만**(`grantableScopes`: 일반 사용자는 `site_admin` 제외) 요청 순서대로 보인다(`DeviceAuthorizationController.kt:67, :73`).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 승인(`#device-approve`) | 폼 submit | `mode == consent` | 제출 | `POST /users/device/approve` 필드 `user_code`(숨김) | 사용자와 줄 수 있는 범위를 저장하고 상태를 승인으로, 감사 `token.device_approved`. 화면 `done`(`deviceAuth.done.approved`). 코드를 못 찾으면 `400` 코드 입력 모양 | 로그인한 누구나(코드를 아는 사람이 승인) | `device.html:55-58`, `DeviceAuthorizationController.kt:43-50`, `DeviceAuthorizationService.kt:129-137` |
| 거부(`#device-deny`) | 폼 submit | `mode == consent` | 제출 | `POST /users/device/deny` 필드 `user_code` | 상태를 거부로, 감사 `token.device_denied`. 화면 `done`(`deviceAuth.done.denied`) | 같음 | `:59-62`, `:53-60`, `:140-146` |

CLI 쪽 `exchange`는 승인이면 토큰을 발급하고(범위는 승인 때 확정), 거부면 `access_denied`, 만료면 `expired_token`, 아직이면 `authorization_pending`(`slow_down`은 간격 5초 증가)을 돌려준다. 이 부분은 외부 API(`ExternalDeviceAuthApiTest`) 계약이고 화면 계약이 아니다.

### 4.4 모양 `done`: 처리 결과

`#device-done`에 `doneMessage`의 메시지만 보인다. 링크나 단추가 없다.

### 4.5 레이아웃과 스크립트

`siteLayout(${title}, 'USER', ...)`, 머리 `deviceAuth.title`, 본문은 `.token-card`(`user/partial-token-form :: tokenStyle`의 스타일 재사용). 이 화면에는 탭 줄이 없다. 스크립트와 단축키 없음. 오류가 있으면 입력 폼 위에 `alert-danger`(`T/user/device.html:24`).

### 4.6 권한과 보안

로그인한 사용자 누구나 코드를 알면 승인할 수 있다. 서버는 코드의 주인(CLI를 실행한 사람)과 승인하는 사람이 같은지 확인하지 않는다(그래서 화면에 클라이언트 이름과 코드를 보여 주고 `deviceAuth.consent.warning`으로 확인을 요구한다). 비로그인이 이 세 주소에 오면 `303 /users/loginform?redirectUrl=/users/device`(질의 문자열 없음, `user_code`가 사라진다)이고 `POST` 두 곳은 로그인 뒤 `GET`으로 가서 404 화면이 된다([user-security.md](user-security.md) `usersec-F6`). 2단계 인증 강제 예외 경로(`/users/`)에 포함된다. 시험 `TT/web/user/DeviceAuthorizationPageTest.kt`, `TT/web/api/ExternalDeviceAuthApiTest.kt`.

## 5. 발견 사항

| 번호 | 내용 | 근거 |
|---|---|---|
| oauth-F1 | 승인 화면 모델의 `homeUrl`(앱 홈페이지)이 템플릿에서 쓰이지 않는다. 사용자는 앱의 홈페이지 주소를 볼 수 없다 | `K/oauth2server/OAuthConsentController.kt:53`, `T/oauth2/consent.html` |
| oauth-F2 | 기밀 앱 수정 양식이 오류로 재표시될 때 종류 라디오가 `공개`로 체크되어 보인다(라디오가 비활성이라 값이 전송되지 않고 `Form.of(request)`가 `confidential=false`가 됨). 비활성이라 동작에는 영향이 없다 | `OAuthAppsSettingsController.kt:164-167`, `T/oauth/partial-apps.html:74` |
| oauth-F3 | 앱 만들기(기밀)와 비밀 값 다시 만들기의 성공 응답이 리다이렉트 없는 `200` 화면이라 새로고침하면 앱을 중복 만들거나 비밀 값을 또 바꾼다 | `OAuthAppsSettingsController.kt:78, :110` |
| oauth-F4 | 승인 화면 `POST /oauth2/authorize`에는 요청 위조 방지 토큰 검사가 없고(인가 서버 체인 `csrf.disable`), 일회용 `state`가 대신한다 | `K/oauth2server/AuthorizationServerConfig.kt:74` |
| oauth-F5 | 기기 승인 코드(`XXXX-XXXX`, 20글자 알파벳 8자리 = 약 2.56e10 조합) 입력에 시도 횟수 제한이 없다. 로그인한 사용자만 시도할 수 있고 유효 시간은 15분이다 | `DeviceAuthorizationService.kt:70-87, :117-122` |
| oauth-F6 | 기기 승인에서 승인하는 사람이 CLI를 실행한 사람인지 서버가 확인하지 않는다(화면의 경고 문구에 의존) | `DeviceAuthorizationController.kt:43-50` |
| oauth-F7 | 로그인 필요 이동에서 질의 문자열이 사라져 `GET /users/device?user_code=...`로 들어온 비로그인 사용자는 로그인 뒤 코드를 다시 입력해야 한다. `GET /oauth2/consent`도 같다(승인 요청은 `/oauth2/resume`으로 별도 보관되므로 승인 흐름에는 영향이 없다) | `K/web/access/AnonymousCheckStage.kt:29`, `K/oauth2server/AuthorizationServerConfig.kt:145` |
| oauth-F8 | 사용자 앱 만들기를 막아도(`oauthUserAppsEnabled=false`) 기존 앱의 수정, 비밀 값 다시 만들기, 이전, 삭제와 승인 취소는 계속 가능하다. 앱을 새로 받는 `POST /user/oauth-apps`와 `GET .../new`만 `403` | `OAuthAppsSettingsController.kt:65, :73` |
| oauth-F9 | `apiToken.scope.offline_access` 메시지가 없다. 앱이 허용 범위 밖의 이 범위를 요청하면 승인 요청 단계에서 거부되어 화면에 도달하지 않는다고 본다(추측, 시험 없음) | `M/messages_ko_KR.properties:1213-1228` |

## 6. 범위 점검

| 템플릿 | 다룬 절 |
|---|---|
| `oauth2/consent.html` | 2 |
| `oauth/partial-apps.html` | 3.2(`mine`), 3.3(`appForm`), 3.4(`secret`) |
| `user/edit-oauth-apps.html` | 3.2 |
| `user/edit-oauth-app-form.html` | 3.3 |
| `user/edit-oauth-app-secret.html` | 3.4 |
| `user/device.html` | 4 |
