---
title: UI 계약 - 사이트 관리 (틀, 사용자, 목록, 메일, 업데이트, 진단, 데이터, 감사, 비밀번호 찾기)
type: ui-contract
sources:
  - src/main/resources/templates/layout/site-mng-layout.html
  - src/main/resources/templates/site/{user-list,project-list,post-list,issue-list,mail,mass-mail,update,diagnostic,data,audit,lost-password}.html
  - src/main/resources/templates/audit/list.html
  - src/main/kotlin/dev/yona/web/site/{SiteUserController,SiteContentController,SiteMailController,SiteMaintenanceController,SiteDataController}.kt
  - src/main/kotlin/dev/yona/web/audit/AuditControllers.kt
  - src/main/kotlin/dev/yona/web/user/PasswordResetController.kt
  - src/main/kotlin/dev/yona/web/access/{SiteManagerOnly,AnonymousCheck,AnonymousCheckStage,AccessChainInterceptor}.kt
  - src/main/kotlin/dev/yona/domain/site/*.kt
  - src/main/resources/static/assets/javascripts/esm/{Request,Pagination}.js
  - src/main/resources/static/assets/javascripts/esm/service/site.MassMail.js
updated: 2026-11-11
---

# UI 계약: 사이트 관리

이 영역은 두 파일이다. 이 파일은 사이트 관리 틀, 사용자 관리, 프로젝트와 게시글과 이슈 목록, 메일, 업데이트, 진단, 데이터, 감사, 비밀번호 찾기, 그리고 전체 발견 사항과 범위 점검이다. 보안, OAuth 앱, Git LFS, SSO 화면은 [site-settings.md](site-settings.md). 이미 있는 글은 [../web/site-admin.md](../web/site-admin.md), [../subsystems/data-exchange.md](../subsystems/data-exchange.md), [../architecture/audit-log.md](../architecture/audit-log.md), [../architecture/enterprise-sso.md](../architecture/enterprise-sso.md), [../architecture/git-lfs.md](../architecture/git-lfs.md). 위키 글과 코드가 다른 곳은 발견 사항에 적었다(코드가 맞다).

## 1. 영역 요약 (화면 목록)

| 템플릿 | URL (메서드) | 컨트롤러 | 용도 |
|---|---|---|---|
| `site/user-list.html` | `/sites/userList` (GET) | `SiteUserController.userList` (SiteUserController.kt:58) | 사용자 관리 |
| `site/project-list.html` | `/sites/projectList` (GET) | `SiteContentController.projectList` (SiteContentController.kt:65) | 프로젝트 목록과 삭제 |
| `site/post-list.html` | `/sites/postList` (GET) | `SiteContentController.postList` (:90) | 모든 게시글 |
| `site/issue-list.html` | `/sites/issueList` (GET) | `SiteContentController.issueList` (:101) | 모든 이슈 |
| `site/mail.html` | `/sites/mail` (GET, POST) | `SiteMailController` (SiteMailController.kt:32, 35) | 메일 보내기 |
| `site/mass-mail.html` | `/sites/massmail` (GET), `/sites/mailList` (POST) | `SiteMailController` (:50, 57) | 대량 메일 받는 사람 모으기 |
| `site/update.html` | `/sites/update` (GET), `/sites/unwatchUpdate` (POST) | `SiteMaintenanceController` (SiteMaintenanceController.kt:32, 54) | 업데이트 확인 |
| `site/diagnostic.html` | `/sites/diagnostic` (GET) | `SiteMaintenanceController.diagnose` (:60) | 진단 |
| `site/data.html` | `/sites/data` (GET), `/sites/export` (GET), `/sites/import` (POST) | `SiteDataController` (SiteDataController.kt:57, 64, 87) | 데이터 내보내기와 가져오기 |
| `site/audit.html` | `/sites/audit` (GET) | `SiteAuditController.list` (AuditControllers.kt:59) | 사이트 감사 로그 (v1.6에 없음, X-9 A6, D-62) |
| `site/lost-password.html` | `/lostPassword` (GET, POST) | `PasswordResetController` (PasswordResetController.kt:49, 52) | 비밀번호 찾기(사이트 관리자 화면이 아니라 공개 화면) |
| `site/security.html`, `oauth-apps.html`, `lfs.html`, `sso.html` | | | [site-settings.md](site-settings.md) |

## 2. 공통

### 2.1 관리자 권한 판정

- 모든 `/sites/**` 컨트롤러(감사 포함)는 클래스에 `@SiteManagerOnly`, 그 다음 `@AnonymousCheck`를 이 순서로 단다. 접근 검사 체인은 **소스에 쓴 순서대로** 바깥에서 안쪽으로 실행하므로 사이트 관리자 검사가 먼저이다 (AccessChainInterceptor.kt:118-139; SiteMaintenanceController.kt:19-21).
- `SiteManagerOnlyStage.run`: `accessControl.isSiteManager(현재 사용자)`가 거짓이면 `ViewResultException`으로 **403 화면**(`error/forbidden-default`, 문구 `error.auth.unauthorized.waringMessage`)을 낸다 (SiteManagerOnly.kt:32-35). 따라서 비로그인도 로그인 폼이 아니라 403을 받는다(사이트가 닫혀 있어도 같다. `ClosedSiteChainTest`).
- 사이트 관리자 판정은 `SessionPermissions.siteManager(userId)`이고 현재 세션 사용자의 값은 처음 계산한 값이 유지된다. 관리자 권한을 주고 거두면 대상 사용자의 세션 캐시를 무효로 해(`sessionUsers.invalidate`) 대상은 다시 로그인해야 한다 (SiteUserService.kt:159-168).
- 예외: `GET /sites/sso/saml2/sp-certificate.pem`과 모든 `POST`도 같은 클래스 검사를 받는다. `/lostPassword`와 `POST /{loginId}?action=resetPassword`는 `/sites`가 아니라 `SiteManagerOnly`가 없고, 후자는 메서드 안에서 직접 사이트 관리자를 판정한다(2.5절, 12절).
- 사이드바(아래)는 관리자만 볼 수 있는 화면 안에 있으므로 숨김 조건이 없다.

### 2.2 틀 `layout/site-mng-layout.html`

- `site-layout` 메뉴 `SITE_SETTING` 안에 제목줄(`.site-breadcrumb-outer`, `site.sidebar`), 그 아래 `div.site-setting-wrap > div.row`: 왼쪽 `col-lg-2`의 사이드바 `ul.site-setting-nav`, 오른쪽 `col-lg-10`의 본문 (site-mng-layout.html:15-48). 사이드바 항목은 `border-left 4px`, 활성은 굵은 글씨와 강조색 왼쪽 선 (app/assets/stylesheets/scss/_page.scss:5237-5270).
- 제목은 `siteMngLayout(#{__${title}__}, 본문)`이다. 컨트롤러가 메시지 **키**를 `title`로 넘기고 템플릿이 전처리(`__${title}__`)로 번역한다 (예: `title.siteSetting`, `site.sidebar.audit`).
- 사이드바 항목(활성은 `requestPath == 링크`):

| 항목 | 링크 | 비고 | 근거 |
|---|---|---|---|
| 사용자 (`site.sidebar.userList`) | `/sites/userList` | | site-mng-layout.html:25 |
| 게시글 (`...postList`) | `/sites/postList` | | :26 |
| 이슈 (`...issueList`) | `/sites/issueList` | | :27 |
| 프로젝트 (`...projectList`) | `/sites/projectList` | | :28 |
| 메일 보내기 (`...mailSend`) | `/sites/mail` | | :29 |
| 대량 메일 (`...massMail`) | `/sites/massmail` | | :30 |
| 업데이트 (`...update`) | `/sites/update` | `versionToUpdate`가 있으면 배지 `1` (`.notification-badge`) | :31-35 |
| 진단 (`...diagnostics`) | `/sites/diagnostic` | | :36 |
| 감사 (`...audit`) | `/sites/audit` | v1.6에 없음 | :37 |
| 보안 (`...security`) | `/sites/security` | v1.6에 없음 | :38 |
| OAuth 앱 (`...oauthApps`) | `/sites/oauth-apps` | v1.6에 없음 | :39 |
| Git LFS (`...lfs`) | `/sites/lfs` | v1.6에 없음 | :40 |
| SSO (`...sso`) | `/sites/sso` | v1.6에 없음 | :41 |

데이터(`/sites/data`) 화면은 사이드바에 없고 주소로만 들어간다(v1.6도 같다). `versionToUpdate`는 `LayoutModelAdvice`가 모든 화면 모델에 넣는다(새 버전이 있을 때) (LayoutModelAdvice.kt:60-67).

### 2.3 설정을 저장한 뒤 반영 방식(재시작 필요 여부)

| 설정 | 저장 위치 | 반영 시점 | 근거 |
|---|---|---|---|
| 2단계 인증 강제, OAuth 앱 설정(CIMD 허용, 사용자 앱 허용), MCP 사용과 쓰기, Git LFS 사용과 한도, SSO 전용 모드 | DB `site_setting`(키 `security.*`, `lfs.*`) | **즉시.** 요청마다 DB를 읽고 캐시가 없다. 재시작 불필요 | SiteSettingService.kt:20-147 |
| OIDC, SAML 2 로그인 설정, LDAP 설정 | DB(`SsoSettingsService`, `LdapSettingsService`) | **즉시.** LDAP은 인증할 때마다 지금 설정으로 연결을 만든다. OIDC 발견 결과는 저장할 때 확인해 캐시한다 | SsoSettingsService.kt; Ldap.kt:227 |
| OIDC 제공자(`/.well-known/openid-configuration`)의 서명 키 | 설정 파일(`oauth2.oidc.signing-key-file`) | 화면에서 바꿀 수 없다(읽기 전용 상태 표시). 키는 빈(bean)이 만들어질 때 한 번 읽으므로 바꾸려면 **재시작** | OidcSettings.kt:24-26 |
| 새 버전 알림 끄기(`unwatch`) | 메모리 + DB 속성 `UPDATE_NOTICE_OFF` | 즉시, 재시작 후에도 유지 | UpdateCheckService.kt:50-52, 85-88 |
| 업데이트 확인 `yona.update.*`, 메일 서버 `spring.mail.*` | 설정 파일 | 화면에서 바꾸지 않는다. 바꾸려면 재시작 | SiteMaintenanceController.kt:36; SiteMailService.kt:38 |
| 데이터 가져오기 | DB 전체 교체 | 즉시. 성공하면 `/`로 이동 | SiteDataController.kt:87-104 |

모든 설정 변경은 값이 바뀐 때만 감사 이벤트(`site.*_changed`)를 남긴다.

### 2.4 요청과 응답 공통

- CSRF, 303 리다이렉트, flash(토스트)는 [organization.md](organization.md) 2.5절과 같다. flash 키 `success`는 성공 토스트, `warning`은 경고이다.
- 오류 응답: 잘못된 매개변수는 `400 error/badrequest-default`, 없는 대상은 `404 error/notfound-default`.
- `data-request-method` 요소(사용자 목록의 조작 링크)는 전역 `yona.Request`(`esm/Request.js`)가 처리한다. 클릭과 Enter에서 `fetch(주소, {method, headers:{"X-Requested-With":"XMLHttpRequest"}, cache:"no-store"})`를 보내고, 응답이 성공(2xx)이면 `204`와 `Location`이 있을 때만 이동하고 그 밖에는 **현재 화면을 새로 고친다**. 실패는 사용자에게 알리지 않는다(`request:error` 이벤트만 낸다) (Request.js:76-95). 요청 전에 `cursor:pointer`를 준다.
- 목록의 쪽 번호는 모두 `yona.Pagination.update($("#pagination"), totalPageCount)`이다. 쿼리 `pageNum`(1부터)을 현재 주소에서 읽고 이전, 쪽 입력, 다음 위젯을 그린다. 쪽 수가 0이면 아무것도 그리지 않는다 (Pagination.js).

### 2.5 권한 행렬

| 기능 | 비로그인 | 로그인 일반 | 사이트 관리자 | 거부 응답 |
|---|---|---|---|---|
| `/sites/**` 모든 화면과 요청 | 403 | 403 | 허용 | `error/forbidden-default`, 문구 `error.auth.unauthorized.waringMessage` |
| `/lostPassword` (GET, POST) | 허용 | 허용 | 허용 | |
| `POST /{loginId}?action=resetPassword` (비밀번호 초기화) | 403 JSON | 403 JSON | 허용 | `{"isSuccess":false,"reason":"FORBIDDEN"}` |

## 3. 사용자 관리 `site/user-list.html`

### 3.1 개요

`GET /sites/userList?pageNum=&query=&state=` (SiteUserController.kt:58-67). `pageNum` 기본 1, `state` 기본 `ACTIVE`이고 모르는 값은 400 (SiteUserController.kt:135-137). 모델: `title`(`title.siteSetting`), `rows`(`UserRow`), `userState`, `query`, `totalPageCount`, `siteAdminCount`. 한 쪽 30명, 가입 최근 순. 기본 관리자(번호 1)와 로그인 ID가 빈 익명 사용자는 항상 뺀다. `GUEST`는 게스트 표시가 있는 사용자(상태 무관), `SITE_ADMIN`은 사이트 관리자 행이 있는 사용자, 그 밖에는 그 상태. `query`는 로그인 ID, 이름, 영문 이름, 이메일에서 대소문자 구분 없이 포함 검색 (SiteUserService.kt:38-58).

### 3.2 레이아웃

제목줄 `div.title_area`(제목 `h2.float-start` + 검색 폼 `float-end`), 탭 5개(`ul.nav.nav-tabs`: 잠기지 않음 `ACTIVE`, 잠김 `LOCKED`, 삭제됨 `DELETED`, 게스트 `GUEST`, 사이트 관리자 `SITE_ADMIN` + 건수 배지), 머리줄 `row.listhead`(이름 3, 이메일 3, 가입일 2, 단추열 4 또는 삭제된 탭의 `userinfo.leave` 4), `ul.user-list-wrap`의 `li.row.listitem`(이름 열 `col-lg-3`: 아바타 32px, 이름, `@ID`. 이메일 `col-lg-3`, 가입일 `col-lg-2`, 단추열 `col-lg-5 offset-lg-6`), 쪽 번호, 삭제 확인 모달 `#alertDeletionWrap` (user-list.html:13-96). 단추열에 `offset-lg-6`을 준 것은 v1.6의 합이 12를 넘던 격자를 같은 자리에 오게 하려는 것이다(주석, :64). 삭제된 탭에서는 조작 단추가 없고 마지막 상태 변경 시각만 보인다. 사용자가 없으면 목록이 비어 있고 빈 상태 문구는 없다. 기본 아바타는 `alt`, `width`, `height` 없이 그림만 낸다.

### 3.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 검색 `query` + 돋보기 | 입력, 제출 | 항상. 숨은 `state`로 현재 탭 유지 | | `GET /sites/userList?state=&query=` | 같은 화면 1쪽 | 사이트 관리자 | user-list.html:16-22 |
| 탭 5개 | 링크 | 항상. 현재 `state`에 `active` | 상태 전환(`query`는 유지되지 않는다) | `GET /sites/userList?state=ACTIVE|LOCKED|DELETED|GUEST|SITE_ADMIN` | | | user-list.html:25-29 |
| 사용자 아바타, 이름, `@ID` 링크 | 링크 | 줄마다 | 사용자 화면 | `GET /{loginId}` | | | user-list.html:51-56 |
| 게스트 전환 (`button.user.make.guest.mode` / `make.normal.mode`) | 링크(`a.ybtn.ybtn-small`, 게스트이면 `ybtn-success`) | `state != DELETED` | `data-request-method=post`로 요청 | `POST /sites/toggleGuestMode?loginId=&state=&query=` | 게스트 여부를 뒤집고 목록 1쪽을 200으로 그린다. 화면은 응답 본문을 쓰지 않고 새로 고친다. 없는 사용자는 `/sites/userList?pageNum=0`으로 303 + flash `user.notExists.name`. 모르는 `state`는 400. 감사 이벤트 `site.guest_mode_changed` | 사이트 관리자. 자기 자신 방지 없음 | user-list.html:66-67; SiteUserController.kt:142-153 |
| 잠금 전환 (`button.user.makeAccountUnlock.true`/`false`) | 링크 | `state != DELETED` | 활성이면 잠금, 그 밖에는 활성으로 | `POST /sites/toggleAccountLock?loginId=&state=&query=` | 목록 200 + 새로 고침. 삭제된 사용자는 전환하지 않고 flash `site.userList.deletedLock`와 함께 목록 | 사이트 관리자 | user-list.html:68-69; SiteUserController.kt:95-112 |
| 비밀번호 초기화 (`title.resetPassword`, `button#{loginId}`) | 단추 | `state != DELETED` | 이전 안내를 지우고 줄 아래에 `sending requestHeader...` 알림을 붙인 뒤 POST. 성공하면 `user.newPassword: {값}` 성공 알림(닫기 단추 포함)을 붙인다. 실패하면 `$yona.alert("password change failed: " + 이유)` | `POST /{loginId}?action=resetPassword` (`yona.http.post`) | 성공 `200 {"loginId","name","newPassword"(6글자),"isSuccess":true}`. 실패 `403 {"isSuccess":false,"reason":"FORBIDDEN"|"SSO_USER"}`(SSO로 연결된 일반 사용자는 거부, 사이트 관리자는 허용), `400 BAD_REQUEST`(action 불일치). 화면은 새로 고치지 않는다. 감사 이벤트 `account.password_changed`(`via=admin_reset`) | 사이트 관리자 | user-list.html:70, 123-138, 144-155; PasswordResetController.kt:112-131 |
| 2단계 인증 해제 (`button.user.resetTwoFactor`) | 링크 | `row.twoFactorEnabled` | | `POST /sites/resetTwoFactor?loginId=&state=&query=` | 목록 200 + 새로 고침. 없는 사용자 303 | 사이트 관리자 | user-list.html:71; SiteUserController.kt:114-126 |
| SSO 연결 해제 (`button.user.unlinkSso`) | 링크 | `row.ssoLinked` | 소셜 로그인 연결은 두고 SSO 연결만 모두 해제 | `POST /sites/unlinkSso?loginId=&state=&query=` | 목록 200 + 새로 고침 | 사이트 관리자 | user-list.html:72; SiteUserController.kt:128-140 |
| 관리자 권한 전환 (`button.user.upgrade.to.site.admin` / `revoke.site.admin.role`) | 링크(관리자이면 `ybtn-info`) | `state != DELETED` | | `POST /sites/toggleSiteAdminRole/{loginId}` | 성공 `303 → /sites/userList` 후 새로 고침. 없는 사용자 400. **거두기 보호**: 대상이 자기 자신, 기본 `admin`(번호 1), 마지막 사이트 관리자이면 `403`(바디 없음) + flash `site.userList.adminProtect`. 화면은 403에서 새로 고치지 않아 안내가 다음 요청에야 보인다(F3). 감사 `site.admin_granted`, `site.admin_revoked` | 사이트 관리자 | user-list.html:73-74; SiteUserController.kt:82-93; SiteUserService.kt:151-168 |
| 삭제 (`button.delete`, `data-toggle=account-delete`) | 단추(`ybtn-danger`) | `state != DELETED` | 모달 제목에 `이름(loginId)`을 넣고 `#alertDeletionWrap`를 연다. 누를 때마다 "예"가 보낼 주소를 바꾼다 | | | 사이트 관리자 | user-list.html:75, 118-122 |
| 모달 "예" (`#accountToggleBtn`) | 링크(`ybtn-danger`) | 모달 안 | `yona.Request.send('delete', 주소)` | `DELETE /sites/user/delete{id}` (`{id}`는 숫자, 앞에 `/` 없이 붙는다) | 성공 `303 → /sites/userList`(fetch가 따라가고) 화면이 새로 고쳐진다. **프로젝트의 유일한 매니저이면** `403` + flash `site.userList.deleteAlert`이고 화면이 `reload`하여 안내가 보인다. 없는 번호 404. 삭제 처리는 상태 `DELETED`, 이름 앞 `[DELETED]`, 이메일 `deleted-{loginId}@noreply.yona.io`, 비밀번호와 소금 비움, 프로젝트와 조직 구성원, 즐겨찾기, 구독, 토큰, 키, OAuth 앱과 동의, LFS 잠금, 2단계 인증과 SSH 키 삭제. 자기 자신과 기본 관리자 삭제는 서버가 막지 않는다(F4) | 사이트 관리자 | user-list.html:93, 105-116; SiteUserController.kt:69-80; SiteUserService.kt:80-140 |
| 모달 "아니요", 닫기 | 단추 | | 닫기(Esc, 바깥 클릭 포함) | | | | user-list.html:84-94 |
| 쪽 번호 | 위젯 | 항상 | | `GET /sites/userList?pageNum=N&...` | | | user-list.html:81, 141 |

- 조작 링크의 주소는 서버가 만든다. `toggle(경로)`는 `경로?loginId={인코딩}&state={사용자 상태}` 뒤에 `query`가 있으면 `&query=`를 붙인다. 목록은 현재 탭이 아닌 **사용자의 상태**(`state=row.state`)를 싣는다 (SiteUserController.kt:154-160).
- 스크립트는 템플릿 인라인이다: 모달과 단추 위임(`.user-list-wrap`에 `[data-toggle=account-delete]`, `[data-toggle=reset-password]` 클릭), 쪽 번호, mustache 템플릿 둘(`#dismissable-alerts`, `#dismissable-alerts-request`). `yona.Request`가 `data-request-method` 단추를 전역에서 연결한다. 단축키, 로컬 저장소, 폴링 없음.

## 4. 프로젝트 목록 `site/project-list.html`

`GET /sites/projectList?filter=&pageNum=` (SiteContentController.kt:65-76). `pageNum`은 1부터이고 `-1` 이하는 400, 0은 첫 쪽으로 본다. 한 쪽 25개 (`PROJECT_PAGE_SIZE`, SiteContentService.kt:56). `filter`가 비면 만든 최근 순, 아니면 이름에 대소문자 구분 없이 포함된 프로젝트를 번호 순으로(소유자와 개요는 찾지 않는다). 읽기 권한과 무관하게 비공개 프로젝트도 모두 나온다. 모델: `rows`(`id`, `owner`, `name`, `logoUrl`, `overview`, `createdText`(`yyyy-MM-dd`)), `filter`, `totalPageCount`.

레이아웃: 제목줄(제목 + 검색 폼), 머리줄(이름 5, 설명 4, 만든 날 2, 단추 1), `ul.project-list-wrap`의 줄(로고와 `소유자/이름` 링크 둘, 개요, 만든 날, 삭제 단추), 쪽 번호, 삭제 모달 `#alertDeletionWrap` (project-list.html:13-58).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 검색 `filter` | 입력, 제출 | 항상 | | `GET /sites/projectList?filter=` | | 사이트 관리자 | project-list.html:15-20 |
| 로고, 이름 링크 | 링크 | 줄마다 | 프로젝트 | `GET /{owner}/{name}` | | | project-list.html:31-35 |
| 삭제 (`button.delete`, `data-toggle=delete-project`) | 단추 | 줄마다 | 모달 제목에 `소유자/이름`을 넣고 열기. "예"가 보낼 주소는 `data-href`(`/sites/project/delete/{id}`) | | | | project-list.html:39, 65-73 |
| 모달 "예" (`#projectDeleteBtn`) | 단추 | 모달 안 | `yona.Request.send('delete', 주소)` | `DELETE /sites/project/delete/{projectId}` | 성공이면 DB 삭제, 저장소, 위키, LFS 디렉터리 삭제 후 `303 → /sites/projectList`, 화면 새로 고침. 없는 프로젝트 404. 실패 안내는 화면에 없다 | 사이트 관리자 | project-list.html:55, 75-79; SiteContentController.kt:78-88 |
| 모달 "아니요", 닫기 | 단추 | | | | | | project-list.html:46-58 |
| 쪽 번호 | 위젯 | | | `?pageNum=N` | | | project-list.html:44, 81 |

스크립트는 인라인이다(`console.log` 한 줄 남아 있다, :72). 없는 프로젝트 번호의 삭제는 404 화면이 아니라 `Request.send`의 실패라 화면에는 아무것도 나타나지 않는다.

## 5. 게시글 목록 `site/post-list.html`

`GET /sites/postList?pageNum=` (SiteContentController.kt:90-99). `pageNum < 1`이면 400. 한 쪽 30개, 만든 최근 순, 공지와 비공개 프로젝트의 글 포함. 모델: `rows`(`ContentRow`), `totalPageCount`. 줄: 프로젝트 로고와 `소유자/이름` 링크, `·`, 제목 링크(`{프로젝트}/post/{번호}`), 오른쪽에 작성자 아바타(16px, 기본 아바타는 그림만), 작성자 이름(이 화면은 `user.name`, 없는 사용자는 `anonymous`), 시각(`title`에 전체 시각), 댓글 수 링크(`...#comments`) (post-list.html:16-41). 쪽 번호는 `Pagination`. 클릭 요소는 모두 링크이고 요청은 GET뿐이다. 빈 상태 문구 없음. 스크립트와 단축키 없음.

## 6. 이슈 목록 `site/issue-list.html`

`GET /sites/issueList?pageNum=&state=` (SiteContentController.kt:101-113). `state`는 대문자로 바꿔 `State` 열거로 읽는다(`OPEN` 기본, `CLOSED`, `ALL`. 모르는 값은 400). `pageNum < 1`이면 400. 한 쪽 30개, 만든 최근 순. 작성자 이름은 보는 사람의 언어에 맞춘 표시 이름(`UserDisplay.of`). 탭 둘: 열림 `?state=open`, 닫힘 `?state=closed`(현재 `currentState`에 `active`) (issue-list.html:16-19). 줄 구조는 게시글 목록과 같고 링크는 `{프로젝트}/issue/{번호}`. "전체"(`all`) 탭은 없다. 쪽 번호의 링크는 현재 주소의 쿼리를 유지하므로 `state`가 유지된다.

## 7. 메일 보내기 `site/mail.html`

### 7.1 개요

`GET /sites/mail?errorMessage=&sended=` (SiteMailController.kt:32-33), `POST /sites/mail` (:35-48). 모델: `title`(`title.sendMail`), `notConfiguredItems`(설정되지 않은 `spring.mail.host`, `username`, `password`), `sender`(미리 채울 보내는 주소), `errorMessage`(메시지 키), `sended`.

### 7.2 레이아웃

제목, 오류 알림(`errorMessage`가 있을 때 `site.mail.fail` + 키 번역문, `th:utext`), 성공 알림(`sended`), 미설정 항목 알림(목록이 비어 있지 않을 때 `site.mail.notConfigured` + 항목 `ul`), 폼 `#mailForm`(보내는 사람 `from`, 받는 사람 `to`, 제목 `subject`, 본문 `body` 16행, 보내기 단추) (mail.html:13-63).

### 7.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| `from` | 입력 (`required`, 자리표시 `site.mail.fromPlaceholder`) | 항상, 값은 `sender` | | 바디 `from` | | | mail.html:33 |
| `to` | 입력 (`required`) | 항상 | 주소 하나 | 바디 `to` | | | mail.html:40 |
| `subject` | 입력 | 항상 | | 바디 `subject` | | | mail.html:47 |
| `body` | textarea (`rows=16`) | 항상 | 전역 `CTRL+ENTER`가 폼을 제출한다 | 바디 `body` | | | mail.html:54 |
| 보내기 (`site.mail.send`) | 제출 | 항상 | 폼 제출 | `POST /sites/mail` (`from`, `to`, `subject`, `body`) | **이동하지 않고 같은 응답으로 이 화면을 다시 그린다.** 보냈으면 `sended=true` 성공 알림. 메일 서버가 없거나 거절하면 `errorMessage=site.mail.failReason` 오류 알림. 본문이 비었거나 주소가 올바르지 않으면 `400` | 사이트 관리자 | mail.html:58-62; SiteMailController.kt:35-48; SiteMailService.kt:49-64 |

메일은 UTF-8 일반 텍스트 한 통이다. 스크립트와 단축키(전역 제외) 없음.

## 8. 대량 메일 `site/mass-mail.html`

`GET /sites/massmail` (SiteMailController.kt:50-51, 모델 `title.massMail`). 서버는 메일을 보내지 않는다. 받는 사람 목록을 JSON으로 알려 주면 화면의 스크립트가 사용자의 메일 프로그램(`mailto:`)을 연다.

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 라디오 "모두" (`#mailtoAll`, `value=all`, 기본 선택) | 라디오 | 항상 | 선택하면 선택한 프로젝트 목록을 비우고 프로젝트 영역(`#project-list-wrap`)을 숨긴다(`data-action=hide`) | | | mass-mail.html:17-20; site.MassMail.js `_clickMailTypeLabel` |
| 라디오 "프로젝트별" (`#mailtoPrj`, `value=projects`) | 라디오 | 항상 | 선택 목록을 비우고 프로젝트 영역을 보인다(`data-action=show`) | | | mass-mail.html:21-24 |
| 프로젝트 입력 `#input-project` | 입력 (자동 완성) | 프로젝트별일 때 | `yona.ui.Typeahead`가 `/projects`에서 `소유자/이름` 목록을 받아 제안. Enter 키는 추가와 같다 | `GET /projects?filter=...` (`Accept: application/json`, 이름 목록) | | mass-mail.html:27; site.MassMail.js:34-43, 125-131 |
| 추가 `#select-project` | 단추(`type=submit`이지만 폼 밖) | | 입력값을 `span.badge.bg-info`(이름 + 제거 링크 `x`)로 `#selected-projects`에 붙이고 입력을 비운다. 존재 확인은 하지 않는다 | | | mass-mail.html:28; site.MassMail.js:116-124, 132-135 |
| 선택 라벨의 `x` | 링크 | 선택마다 | 그 라벨을 지운다 | | | site.MassMail.js:108-114 |
| 메일 쓰기 `#write-email` (`site.mail.write`) | 단추 | 항상 | 단추를 `site.massMail.loading` 글자로 바꾸고 비활성화한 뒤 요청한다. 응답 목록으로 `mailto:a,b,...,` 주소의 숨은 폼(`method=POST`, `enctype=text/plain`)을 만들어 제출한다 | `POST /sites/mailList` : 모두이면 바디 `all=true`, 프로젝트별이면 `0=소유자/이름&1=...`(선택한 순서의 배열). `Accept: application/json` | 이메일 문자열 배열(JSON). 프로젝트가 없거나 `/`가 없는 이름이면 `400`. `Accept`가 JSON이 아니면 `406`. 바디가 비면(프로젝트 선택 없음) `[]` | mass-mail.html:36; SiteMailController.kt:57-72; site.MassMail.js:78-106 |

서버 규칙: `all=true`이면 활성 사용자의 이메일, `all`이 있으나 `true`가 아니면 `[]`, 그 밖에는 폼의 모든 값을 `소유자/이름`으로 보고 프로젝트 구성원의 이메일(사이트 관리자 역할 행 제외, 중복 없음). 순서는 해시 순서이다 (SiteMailService.kt:66-88). 선택 없이 보내면 수신자가 없는 `mailto:`가 열린다(F9). 스크립트 모듈: `site.MassMail`(`esm/service/site.MassMail.js`), 인라인에서 `{"sURLMailList": "/sites/mailList", "sURLProjects": "/projects"}`로 시작.

## 9. 업데이트 `site/update.html`

`GET /sites/update` (SiteMaintenanceController.kt:32-51). 모델: `currentVersion`, `versionToUpdate`, `releaseUrl`, `failure`(스택 트레이스 문자열). `yona.update.check`가 켜져 있으면 **요청 안에서** 저장소의 태그(JGit `ls-remote`, 시간 제한 10초)를 읽어 새 버전을 찾고(`updates.refresh()`), 읽지 못하면 `failure`에 스택 트레이스를 담는다. 꺼져 있으면 현재 버전도 알리지 않고 백그라운드가 마지막에 찾은 값만 쓴다.

표시(update.html:13-28): 제목, `versionToUpdate`가 있으면 `site.update.isAvailable(버전)` + 내려받기 링크(`releaseUrl`, `site.update.download`, 일반 링크), `currentVersion`이 있으면 `site.update.currentVersion`, 새 버전도 오류도 없으면 `site.update.isNotNecessary`, 오류면 `site.update.error` + `<pre>` 스택 트레이스.

`POST /sites/unwatchUpdate`는 이 화면이 아니라 전역 레이아웃의 알림줄(`layout/layout.html:62`, `data-request-method=post`)이 부른다: 응답 빈 `200`이고 서버 전체의 알림을 끈다(알림은 새 버전이 있고 끄지 않았으며 사이트 관리자일 때만 보인다). 끄기를 되돌리는 화면은 없다(F6). 백그라운드 확인은 `check`가 켜져 있고 `interval`이 0보다 클 때만 `init-delay` 뒤 `interval`마다 돈다 (UpdateCheckScheduler.kt:33-44). 스크립트 없음.

## 10. 진단 `site/diagnostic.html`

`GET /sites/diagnostic` (SiteMaintenanceController.kt:60-62). 등록된 점검을 모두 실행해 `errors`(문자열 목록)를 낸다. 오류가 없으면 `site.diagnostic.errorNotFound`, 있으면 `site.diagnostic.errorFound(건수)` + `ul > li > pre`. 상호작용 요소 없음 (diagnostic.html:17-23).

## 11. 데이터 `site/data.html`

`GET /sites/data` (SiteDataController.kt:57). 사이드바에 링크가 없다. 레이아웃: 제목, 경고 목록 4줄(`site.data.warning1..4`), 내보내기, 가져오기(data.html:13-41).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 내보내기 (`site.data.export`) | 링크(`ybtn-primary`) | 항상 | 파일 내려받기 | `GET /sites/export` | `Content-Type: application/x-download`, `Content-disposition: attachment; filename=yona-data-{yyyyMMddHHmm}.json`, 스트리밍. 쓰는 중 오류면 로그를 남기고 예외를 전해 응답이 중단된다. 감사 `site.data_exported` | 사이트 관리자 | data.html:29; SiteDataController.kt:64-79 |
| 파일 선택 `data` | 파일 입력 | 항상 | | multipart 파트 `data` | | | data.html:37 |
| 제출 (`<input type="submit">`, 라벨은 브라우저 기본) | 제출 | 항상 | 제출 전에 `confirm(site.data.import.confirm)`. 취소하면 보내지 않는다 | `POST /sites/import` (`multipart/form-data`) | 파일이 없으면 `303 → /sites/data`. 처리했으면 `303 → /`. 일부 테이블이 실패하면 `303 → /sites/data` + flash `warning`(`site.data.import.failed`, `failedTables`). JSON이 아니면 `400`. 업로드 크기 제한 없음(`multipart.max-file-size: -1`). 가져오기는 병합이 아니라 **교체**이다. 감사 `site.data_imported` | 사이트 관리자 | data.html:36-49; SiteDataController.kt:87-104 |

아바타 도구 두 개(`GET /sites/noAvatarUsers`, `POST /sites/setAttachmentToUserAvatar`)는 이 화면에서 부르지 않는다(v1.6도 같다). 전자는 아바타가 없는 활성 사용자의 `{"users":[{loginId,name,email}]}`, 후자는 본문 `{"avatarFileId","email"}`로 이미지 첨부를 아바타로 옮기고 어느 경우든 `{"status":200,"message":"OK"}`, JSON이 아니면 `400 {"message":"Expecting Json data"}`이다 (SiteDataController.kt:106-128).

## 12. 감사 로그 `site/audit.html`

`GET /sites/audit` (AuditControllers.kt:59-63). 모델: `title`(`site.sidebar.audit`), `streams`(외부 전송 대상 상태), `AuditViewSupport.model`. 레이아웃: 제목, 설명 `audit.description`, 외부 스트리밍 상태 표(`streams`가 비어 있지 않을 때, 열: 대상, 대기, 마지막 전송, 실패, 마지막 오류. 실패가 있는 줄은 `table-warning`), 그 아래 `audit/list :: auditList(true, true, true)`(내려받기 링크와 해시 검증 단추와 필터 모두 보임) (audit.html:11-33).

공통 요소(필터, 건수, 내려받기, 표, 이전과 다음)는 [organization.md](organization.md) 9.3절과 같고 범위만 사이트 전체이다. 이 화면에만 더 있는 것:

| 요소 | 종류 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|
| 내보내기 링크 3개 | 링크 | | `GET /sites/audit/export?format=csv|jsonl|json&{필터}` (`limit` 기본 10000, 최대 100000) | 첨부 `audit-{UTC 날짜}.{확장자}`. 형식이 틀리면 400. 감사 `audit.exported` | 사이트 관리자 | AuditControllers.kt:34-44, 71-73 |
| 무결성 검증 `.audit-verify` (`audit.verify`) | 단추 | 클릭하면 `fetch(data-url 또는 현재경로 + "/verify", {credentials:"same-origin", Accept: JSON})`로 요청하고 결과를 `.audit-verify-result`에 쓴다. 성공 `audit.verify.ok({건수})` 초록, 깨짐 `audit.verify.broken({id}, {이유})` 빨강. 요청 실패는 처리하지 않는다 | `GET /sites/audit/verify` | `{"ok","checked","brokenId","reason"}` JSON. 해시 사슬을 처음부터 다시 계산한다 | 사이트 관리자 | audit/list.html:35-38, 69-84; AuditControllers.kt:75-78 |

## 13. 비밀번호 찾기 `site/lost-password.html`

### 13.1 개요

`GET /lostPassword`, `POST /lostPassword` (PasswordResetController.kt:49-79). `@AnonymousCheck`와 `@SiteManagerOnly`가 없어 누구나 쓴다. 틀은 `site-layout` 메뉴 `NONE`, 제목 `site.resetPasswordEmail.title`. 모델: `isSent`, `errorKey`. 로그인 화면, 로그인 대화상자, 비밀번호 수정 화면의 "비밀번호 찾기" 링크가 이 주소로 온다.

### 13.2 레이아웃

`div.page.full`: 제목(`title.resetPasswordFor(사이트 이름)`, `th:utext`)과 `app.description`, 그 아래 `div.login-form-wrap.frm-wrap`: 성공 알림(`isSent`, `site.mail.sended`, 닫기 단추), 오류 알림(`errorKey`, `site.mail.fail` + 키 번역문 `th:utext`, 닫기 단추), 폼(로그인 ID, 이메일, 확인 단추) (lost-password.html:13-46).

### 13.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| `loginId` | 입력 (`required`) | 값은 로그인한 사용자이면 본인 `loginId` | | 바디 `loginId` | | 누구나 | lost-password.html:35 |
| `emailAddress` | 입력 (`required=""`) | 값은 로그인한 사용자이면 본인 이메일 | | 바디 `emailAddress` | | | :38 |
| 확인 (`button.confirm`) | 제출 | 항상 | | `POST /lostPassword` | 로그인 ID가 없거나 이메일이 대소문자까지 같지 않으면 `site.resetPasswordEmail.invalidRequest`(이유는 하나로 합쳐 존재 여부를 숨긴다). SSO로 연결된 일반 사용자는 `site.resetPasswordEmail.ssoUser`. 맞으면 토큰을 만들어 메일 발송 이벤트를 내고 `isSent`는 발송 성공 여부. 모든 경우 상태 200으로 같은 화면. 메일을 보내지 못하면 알림 없이 폼만 다시 보인다. 감사 `account.password_reset_requested`(실패 포함) | | :43; PasswordResetController.kt:52-79 |
| 알림 닫기 | 단추 | 알림이 있을 때 | `data-bs-dismiss=alert` | | | | :22, 27 |

메일의 링크가 여는 `GET /resetPassword?s=`와 `POST /resetPassword`는 `user/reset-password` 화면이며 이 영역 밖이다. 스크립트 없음.

## 14. 발견 사항

접두어 `site-F`는 사이트 두 파일 모두의 번호이다.

| 번호 | 내용 | 근거 |
|---|---|---|
| site-F1 | 사이트 관리자 검사가 `@AnonymousCheck`보다 먼저라 비로그인은 로그인 폼이 아니라 403을 받는다(v1.6의 `@With` 순서를 그대로 옮김). 위키 `web/site-admin.md`는 "로그인 검사가 먼저라 사이트가 익명 접근을 막으면 익명은 로그인 폼으로 간다"고 적어 코드와 반대이다 | SiteManagerOnly.kt:23-25; AccessChainInterceptor.kt:102-107; web/site-admin.md "접근" |
| site-F2 **[수정됨, A-457]** | 사용자 목록으로 이동하는 조작이 없는 사용자에게 `/sites/userList?pageNum=0`으로 303을 준다. 그런데 `userList`는 `pageNum - 1`을 쪽 번호(0부터)로 넘겨 `0`이면 `-1`이 되고 `setFirstResult(-30)`을 부른다. Hibernate가 음수 오프셋에 예외를 던지면 500이다(추측: Hibernate의 동작은 이 조사에서 실행해 확인하지 않았다). 시험은 303 주소까지만 확인한다 | SiteUserController.kt:58-67, 107, 120; SiteUserService.kt:56; SiteUserManagementTest.kt:225 |
| site-F3 | 관리자 권한 거두기가 보호 규칙으로 `403`이 되면 화면(`yona.Request`)은 새로 고치지 않고 알리지도 않는다. 안내(`site.userList.adminProtect`)는 flash에만 남아 사용자가 다음에 화면을 열 때 나타난다. 계정 삭제 모달만 403에서 `reload`한다 | Request.js:76-95; user-list.html:111-115 |
| site-F4 | 서버는 자기 자신, 기본 관리자의 **삭제**와 자기 자신의 **잠금**을 막지 않는다(삭제는 "프로젝트의 유일한 매니저" 검사만, 관리자 권한 거두기만 보호). 기본 관리자는 목록에 나오지 않지만 주소를 직접 호출하면 가능하다(추측: 호출해 확인하지 않았다) | SiteUserController.kt:69-80, 95-112 |
| site-F5 | 프로젝트 삭제 요청이 실패(404, 네트워크)해도 화면에는 아무 안내가 없다. 삭제 모달은 닫히지도 않는다. 템플릿에 `console.log`가 남아 있다 | project-list.html:72-79 |
| site-F6 | 새 버전 알림을 끄면 되돌리는 화면이 없다. 컨트롤러 KDoc은 "서버를 다시 시작하면 켜진다"고 하나 서비스는 DB 속성에 저장해 재시작 후에도 꺼져 있다(코드는 후자). 위키 `site-admin.md`도 같은 낡은 설명이다 | SiteMaintenanceController.kt:53; UpdateCheckService.kt:31-37, 85-88 |
| site-F7 | 업데이트 화면을 열 때마다 요청 안에서 원격 저장소를 읽어(최대 10초) 응답이 지연될 수 있다. 위키는 백그라운드 확인이 `check`와 관계없이 돈다고 하나 코드는 `check`가 꺼지면 시작하지 않는다(R-55-1) | SiteMaintenanceController.kt:36-43; UpdateCheckScheduler.kt:33-36 |
| site-F8 | 메일 화면의 `errorMessage` 쿼리 값이 메시지 키로 전처리(`#{__${errorMessage}__}`)되어 `th:utext`로 출력된다. 임의의 메시지 키 문구를 이스케이프 없이 보일 수 있다. 사이트 관리자만 열 수 있어 영향은 작다(추측: 템플릿 표현식 주입 가능성은 확인하지 않았다). 제목도 같은 방식으로 키를 전처리한다 | mail.html:14, 16-19; SiteMailController.kt:32-33 |
| site-F9 | 대량 메일에서 프로젝트별인데 선택 없이 "메일 쓰기"를 누르면 수신자가 없는 `mailto:`가 열린다. 받는 사람이 많으면 `mailto:` 주소 길이가 브라우저 한도를 넘을 수 있다(추측). 선택한 프로젝트 이름은 서버가 올 때까지 검증되지 않아 없는 이름이면 400이다 | site.MassMail.js:78-106; SiteMailService.kt:78-88 |
| site-F10 | 메일 보내기(`POST /sites/mail`)는 PRG가 아니라 같은 응답으로 화면을 다시 그려 새로고침하면 메일이 다시 나간다 | SiteMailController.kt:35-48 |
| site-F11 | 위키 `web/site-admin.md`가 코드와 어긋나는 곳(코드가 맞다): 프로젝트 목록 `pageNum`을 0부터라고 적었으나 지금은 1부터(R-53-1), 저장소 디렉터리를 지우지 않는다고 적었으나 지금은 지운다(R-53-3), 내보내기 파일 이름 `yobi-data-`(코드는 `yona-data-`), 메일을 보내지 못해도 성공이라고 적었으나 지금은 실패 안내(R-54-2)와 400, 관리자 권한 자기 보호가 없다고 적었으나 지금은 있다(R-52-2), 사이드바에 감사, 보안, OAuth 앱, Git LFS, SSO가 빠져 있다 | web/site-admin.md; SiteContentController.kt:70, 83-86; SiteMailController.kt:101-111 |
| site-F12 | 사이드바의 감사, 보안, OAuth 앱, Git LFS, SSO 5개 항목은 v1.6에 없는 추가 화면이다(D-62, D-69, D-71, D-79, D-80 등). v1.6 사이드바는 사용자, 게시글, 이슈, 프로젝트, 메일, 대량 메일, 업데이트, 진단 8개였다 | site-mng-layout.html:37-41 |
| site-F13 | `/sites/data`의 제출 단추가 라벨 없는 `<input type=submit>`이라 브라우저 기본 글자("제출")로 나온다. 가져오기 확인은 `form[action$='sites/import']` 선택자에 의존한다 | data.html:39, 45 |

설정 화면(보안, OAuth 앱, LFS, SSO)의 발견 사항은 [site-settings.md](site-settings.md)(site-F14 이후)에 이어진다.

## 15. 시험 근거

`src/test/kotlin/dev/yona/web/site/`의 `SiteUserManagementTest`(27), `SiteContentListsTest`(14), `SiteMailTest`(15), `SiteUpdateAndDiagnosticTest`(9), `SiteDataTest`(18), `SiteExportConsistencyTest`(3). 접근 체인은 `web/access/ClosedSiteChainTest`, `AccessChainOrderTest`. 감사 화면은 `domain/audit/AuditViewTest`, `AuditStreamViewTest`. 비밀번호 찾기는 `web/user/PasswordResetTest`, `PasswordResetSsoTest`. 스크립트는 `src/test/js/`에서 `requestas.test.mjs`(`yona.Request`)를 찾았고, `site.MassMail`, 사용자 목록 인라인 스크립트 전용 시험은 찾지 못했다.

## 16. 범위 점검

| 템플릿 | 다룬 곳 |
|---|---|
| `site/audit.html` | 이 파일 12절 |
| `site/data.html` | 이 파일 11절 |
| `site/diagnostic.html` | 이 파일 10절 |
| `site/issue-list.html` | 이 파일 6절 |
| `site/lfs.html` | [site-settings.md](site-settings.md) 4절 |
| `site/lost-password.html` | 이 파일 13절 |
| `site/mail.html` | 이 파일 7절 |
| `site/mass-mail.html` | 이 파일 8절 |
| `site/oauth-apps.html` | [site-settings.md](site-settings.md) 3절 |
| `site/post-list.html` | 이 파일 5절 |
| `site/project-list.html` | 이 파일 4절 |
| `site/security.html` | [site-settings.md](site-settings.md) 2절 |
| `site/sso.html` | [site-settings.md](site-settings.md) 5절 |
| `site/update.html` | 이 파일 9절 |
| `site/user-list.html` | 이 파일 3절 |

15개 중 15개를 읽었다. 함께 읽은 연결 파일: `layout/site-mng-layout.html`, `audit/list.html`, `layout/layout.html`의 업데이트 알림, `common/scripts.html`.
