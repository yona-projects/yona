---
title: UI 계약 - 조직 설정의 OAuth 앱과 앱 정책
type: ui-contract
sources:
  - src/main/resources/templates/organization/{oauth-apps,oauth-app-form,oauth-app-secret,oauth-policy}.html
  - src/main/resources/templates/oauth/partial-apps.html
  - src/main/kotlin/dev/yona/web/organization/{OrganizationOAuthAppsController,OrganizationOAuthPolicyController}.kt
  - src/main/kotlin/dev/yona/web/{OAuthAppViewSupport,OAuthAppUrls}.kt
  - src/main/kotlin/dev/yona/domain/oauth2server/{OAuthClientService,OAuthOrgPolicy}.kt
updated: 2026-11-11
---

# UI 계약: 조직 설정의 OAuth 앱 화면 넷

모두 v1.6에 없는 추가 화면이다(X-39, decisions.md D-78). 공통 틀, 권한 용어, CSRF는 [organization.md](organization.md) 2절. OAuth 서버 자체는 [../architecture/oauth2-server.md](../architecture/oauth2-server.md). 사용자 설정의 같은 화면은 `oauth/partial-apps.html`을 공유하며 소유자만 다르다.

## 1. 공통 규칙

- 두 컨트롤러 모두 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`: 비로그인은 `/users/loginform?redirectUrl=...`로 303 + `user.login.alert`.
- `guard`: 조직이 없으면 404 `organization.member.unknownOrganization`, 조직 `UPDATE` 권한(조직 관리자, 사이트 관리자)이 없으면 403 조직 403 화면(`error.forbidden`). 다른 조직의 앱은 `findFor(소유자, id)`가 null이라 404 (OrganizationOAuthAppsController.kt:135-142).
- 사이트 설정 `oauthUserAppsEnabled`가 꺼져 있으면(사이트 관리자의 "보안" 화면) 새 앱 만들기만 막힌다: 목록의 만들기 단추 대신 `oauth.apps.disabled` 문구, `GET .../new`와 `POST .../oauth-apps`는 403 조직 화면. 이미 있는 앱은 보고 고치고 지울 수 있다 (OrganizationOAuthAppsController.kt:61-67, 69-73).
- 앱은 소유자(조직)마다 20개까지(`MAX_PER_OWNER`), 이름 100자, 설명 500자, 홈 주소 500자, 리디렉션 주소 10개까지(`oauth.error.redirect`) (OAuthClientService.kt:264-268). 한도 초과는 `oauth.error.limit`.
- 주소 모음 `OAuthAppUrls.organization(base)`: 목록, 수정, 만들기 `POST`, 수정 `POST`가 각각 `base`, `base/{id}`, `base`, `base/{id}`이고 새 앱 화면 `base/new`, 비밀 값 `base/{id}/secret`, 이전 `base/{id}/transfer`, 삭제 `base/{id}/delete`이다. `base`는 `/organizations/{name}/oauth-apps` (OAuthAppUrls.kt:12-35).

## 2. 앱 목록 `organization/oauth-apps.html`

`GET /organizations/{name}/oauth-apps` (OrganizationOAuthAppsController.kt:54-59). 모델: `title`, `orgView`, `urls`, `apps`(`AppRow`: id, 이름, clientId, 기밀 여부, 리디렉션 수, 생성일), `userAppsEnabled`. 레이아웃: 설정 탭 아래 `div.two-factor-page`(폭 클래스 `tf-*`)에 설명 `oauth.org.description`, `mine` 조각 (oauth-apps.html:16-18; partial-apps.html:12-37).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 빈 상태 `#my-apps-empty` (`oauth.org.apps.empty`) | 문구 | 앱이 없음 | | | | | partial-apps.html:15 |
| 앱 표 `#my-apps` | 표 | 앱이 있음 | 열: 이름, clientId, 유형(`oauth.type.confidential`, `oauth.type.public`), 생성일, 삭제 | | | | partial-apps.html:16-31 |
| 이름 링크 | 링크 | 앱마다 | 수정 화면 | `GET .../oauth-apps/{id}` | 수정 양식 | 조직 `UPDATE` | partial-apps.html:20 |
| 삭제 단추 (`oauth.apps.delete`) | 제출(폼 `onsubmit`의 `confirm('oauth.apps.delete.confirm')`) | 앱마다 | 브라우저 확인창에서 취소하면 보내지 않는다 | `POST .../oauth-apps/{id}/delete` | `303 → .../oauth-apps`. 없는 앱 404 | 조직 `UPDATE` | partial-apps.html:25-27; OrganizationOAuthAppsController.kt:127-133 |
| 새 앱 `#oauth-app-new` | 링크 | `userAppsEnabled` | 만들기 양식 | `GET .../oauth-apps/new` | | | partial-apps.html:33 |
| 비활성 문구 `#user-apps-disabled` | 문구 | `!userAppsEnabled` | | | | | partial-apps.html:34 |

스크립트 없음. 삭제 확인은 인라인 `onsubmit` 속성이다(메시지 안의 작은따옴표는 시험 `MessageApostropheTest`가 지킨다).

## 3. 앱 만들기와 수정 `organization/oauth-app-form.html`

`GET .../oauth-apps/new`(만들기), `GET .../oauth-apps/{id}`(수정). 모델: `title`, `orgView`, `urls`, `client`(수정일 때 `id`, `clientId`, `confidential`), `form`, `formError`, `scopeChoices`, `oidcInfo`, `transferTargets` (OrganizationOAuthAppsController.kt:151-154). 양식은 `appForm` 조각 (partial-apps.html:40-118).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 오류 알림 | `div.alert-danger` | `formError != null` | 메시지 키 번역 | | | | partial-apps.html:41 |
| 제목, clientId (`#edit-client-id`) | 문구 | 수정이면 clientId도 | | | | | partial-apps.html:42-43 |
| OIDC 안내 `#oidc-info` | 영역 | `oidcInfo != null` (OIDC 켜짐이고 수정 화면) | 발급자와 발견 문서 주소 | | | | partial-apps.html:44-51; OAuthAppViewSupport.kt:78-83 |
| 이름 `name`(100), 설명 `description`(500), 홈 주소 `homeUrl`(500), 리디렉션 주소 `redirectUris`(textarea, 줄마다 하나) | 입력 | 항상 | | 바디 필드 같은 이름 | | | partial-apps.html:53-69 |
| 유형 라디오 `type` (`public`, `confidential`) | 라디오 | 항상. **수정이면 `disabled`**(값이 전송되지 않는다) | 만들 때만 고른다 | 바디 `type` | 수정에서는 서버가 유형을 받지 않는다 | | partial-apps.html:74; OAuthAppsSettingsController.kt Form.of |
| 범위 체크박스 `scope`(여러 값) | 체크박스 | `scopeChoices`(`OAuthScopes.forApps(OIDC 켜짐 여부)`)마다. 기본 선택 `read:user` | | 바디 `scope` 반복 | | | partial-apps.html:83-86 |
| 만들기 / 저장 | 제출 | 항상 | 새 앱 `POST .../oauth-apps`, 수정 `POST .../oauth-apps/{id}` | | 거부(검증, 한도)는 `400` + 같은 양식(입력 되살림, `formError`). 만들기 성공: 기밀 앱이면 **그 자리에서** 비밀 값 화면(`200`, 캐시 금지 머리글), 공개 앱이면 `303 → .../oauth-apps/{id}`. 수정 성공 `303 → .../oauth-apps/{id}` + flash `success` `oauth.apps.saved` | 조직 `UPDATE` | partial-apps.html:90; OrganizationOAuthAppsController.kt:69-101 |
| 취소 | 링크 | 항상 | 목록으로 | `GET .../oauth-apps` | | | partial-apps.html:91 |
| 비밀 값 다시 만들기 (`oauth.secret.regenerate`) | 제출 (`confirm('oauth.secret.regenerate.confirm')`) | 수정이고 `client.confidential` | | `POST .../oauth-apps/{id}/secret` | `200` 비밀 값 화면 + flash 문구 `oauth.secret.regenerated`, 캐시 금지. 공개 앱이거나 없는 앱은 404 | | partial-apps.html:94-100; OrganizationOAuthAppsController.kt:104-111 |
| 소유자 이전 `#oauth-transfer-form` | 폼 | 수정이고 `transferTargets`가 비어 있지 않음 | 대상 선택 `target`(`org:이름` 또는 `user`)과 확인용 앱 이름 `confirm` 입력 | `POST .../oauth-apps/{id}/transfer` | 성공 `303 → .../oauth-apps` + flash `oauth.transfer.done`. 허용되지 않은 대상 `400 oauth.transfer.error.target`, 이름 불일치 `oauth.transfer.error.confirm`, 같은 소유자 `oauth.transfer.error.same`, 대상 한도 `oauth.error.limit` | 이전 대상은 현재 사용자가 **관리자인** 다른 조직과(조직 소유 앱이므로) 자기 계정 | partial-apps.html:101-117; OAuthAppViewSupport.kt:42-56; OAuthClientService.kt:175-183 |

수정할 수 있는 항목은 이름, 설명, 홈 주소, 리디렉션 주소, 범위이다. 스크립트 없음.

## 4. 비밀 값 `organization/oauth-app-secret.html`

만들기(기밀 앱) 또는 다시 만들기의 응답으로만 그려진다(GET 경로 없음, 새로고침하면 POST 재전송). 모델: `clientName`, `clientId`, `secret`, `noticeKey`(`oauth.secret.created` 또는 `oauth.secret.regenerated`), `appId`, `urls`. 응답 머리글 `Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`, `Expires: 0` (OAuthAppViewSupport.kt:86-90). 요소: 성공 알림, 앱 이름, clientId(`#client-id`), 비밀 값(`#client-secret`), 한 번만 보인다는 안내 `oauth.secret.once`, 완료 링크 `oauth.secret.done` → `GET .../oauth-apps/{id}` (partial-apps.html:121-132). 권한은 앱을 만들거나 고칠 수 있는 사용자와 같다.

## 5. 앱 정책 `organization/oauth-policy.html`

`GET /organizations/{name}/oauth-policy` (OrganizationOAuthPolicyController.kt:45-52). 모델: `title`, `orgView`, `restricted`, `rows`(`Row`: clientRowId, 이름, clientId, 소유자 표시, 소유자가 조직인지, 승인한 구성원 수, 상태 `APPROVED`, `DENIED`, `PENDING`). 목록은 조직 구성원이 승인한 적 있는 앱이다(`policy.candidates`).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 설명 | 문구 | 항상 | `oauth.policy.description` | | | | oauth-policy.html:17 |
| 라디오 `restricted` (`false`, `true`) | 라디오 | 항상. 현재 값에 `checked` | 제한 끔과 켬. 문구와 도움말 `oauth.policy.{false|true}`, `...help` | 바디 `restricted` | | | oauth-policy.html:19-24 |
| 저장 (`oauth.policy.save`) | 제출 | 항상 | `#oauth-policy-form` 제출 | `POST /organizations/{name}/oauth-policy` (`restricted=true`이면 켬, 그 밖의 값은 끔) | `303 → /organizations/{name}/oauth-policy` + flash `success` `oauth.policy.saved` | 조직 `UPDATE` | oauth-policy.html:25; OrganizationOAuthPolicyController.kt:54-60 |
| 빈 상태 `#policy-apps-empty` | 문구 | 행이 없음 | `oauth.policy.apps.empty` | | | | oauth-policy.html:30 |
| 표 `#policy-apps` | 표 | 행이 있음 | 열: 이름, 소유자(조직 소유면 `oauth.apps.byOrganization(이름)`), 승인 구성원 수, 상태 배지(`.policy-state`), 단추 | | | | oauth-policy.html:31-48 |
| 승인 (`oauth.policy.approve`), 거부 (`oauth.policy.deny`), 초기화 (`oauth.policy.reset`) | 제출 단추 (`name=state`, 값 `APPROVED`, `DENIED`, `PENDING`) | 행마다. 현재 상태와 같은 단추는 `disabled` | 앱에 결정을 내린다 | `POST .../oauth-policy/apps/{clientRowId}` (`state`) | `303 → .../oauth-policy` + flash `oauth.policy.decided`. `PENDING`은 결정을 지운다. 알 수 없는 `state`는 400, 후보가 아닌 앱은 404 | 조직 `UPDATE` | oauth-policy.html:40-44; OrganizationOAuthPolicyController.kt:62-71 |

정책의 효과: 제한을 켠 조직에서는 그 조직이 승인하지 않은 앱(조직이 소유한 앱은 예외)의 OAuth 토큰이 조직의 비공개 자원에 닿지 못하고 익명처럼 취급된다 (AccessControl.kt:104-108; OAuthOrgPolicy.kt `denied`). 설정은 DB에 저장되고 요청마다 읽으므로 재시작이 필요 없다. 스크립트 없음.

## 6. 발견 사항 (이어서, organization-F20 이후)

| 번호 | 내용 | 근거 |
|---|---|---|
| organization-F20 | 비밀 값 화면은 POST 응답으로만 존재해 새로고침이나 뒤로 가기에서 재전송 확인창이 뜬다. 다시 만들기를 재전송하면 비밀 값이 또 바뀐다. 의도된 한 번 보이기이다 | OrganizationOAuthAppsController.kt:78, 110 |
| organization-F21 | 수정 양식에서 유형 라디오가 `disabled`라 `type`이 전송되지 않고, 서버의 `Form.of(request)`는 `confidential=false`로 읽지만 `updateFor`가 유형을 받지 않아 값이 바뀌지 않는다. 오류로 양식을 다시 그릴 때는 기존 앱의 값으로 되살린다 | partial-apps.html:74; OrganizationOAuthAppsController.kt:93-100 |
| organization-F22 | 정책 저장과 결정의 이동 주소는 `UrlPaths.organizationPath`를 쓰지 않고 조직 이름을 그대로 문자열에 넣는다(다른 컨트롤러는 `basePath`로 인코딩). 이름 규칙(한글 허용) 때문에 한글 이름이면 `Location`의 인코딩은 `RedirectSupport.see`가 맡는다(`encodeLocation`) | OrganizationOAuthPolicyController.kt:57, 70; RedirectSupport.kt:123 |
| organization-F23 | 앱 만들기 비활성 설정은 화면에서 만들기 단추를 숨기고 서버가 403으로 막는다(둘이 같다). 이미 있는 앱의 수정, 삭제, 이전은 막지 않는다 | partial-apps.html:33-34; OrganizationOAuthAppsController.kt:65, 73 |
| organization-F24 | 앱 삭제는 `POST .../delete`(HTML 폼, JS 없음)이고 사용자 설정과 같은 조각을 쓴다. 이전 대상 선택에서 현재 소유자와 같은 조직은 빠진다. 위키의 OAuth 문서(`oauth2-server.md`)에 이 화면의 요청 계약 표는 없다 | OAuthAppViewSupport.kt:42-48 |

## 7. 시험 근거

`OrganizationOAuthAppsTest`(10), `OrganizationOAuthPolicyTest`(5). 정책이 토큰 접근에 미치는 효과는 `oauth2server` 시험 묶음에서 다룬다(이 조사에서 개별 확인하지 않았다).
