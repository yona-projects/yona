---
title: UI 계약 - 조직 (목록, 만들기, 홈, 설정, 구성원, 삭제, 감사, 공통 틀)
type: ui-contract
sources:
  - src/main/resources/templates/organization/{list,create,view,header,menu,partial-settingmenu,setting,members,delete-form,audit}.html
  - src/main/resources/templates/layout/organization-layout.html
  - src/main/resources/templates/error/forbidden-organization.html
  - src/main/resources/templates/audit/list.html
  - src/main/kotlin/dev/yona/web/organization/{OrganizationController,OrganizationCreateController,OrganizationMemberController,OrganizationSettingController,OrganizationViewContext}.kt
  - src/main/kotlin/dev/yona/web/audit/{AuditControllers,AuditViewSupport}.kt
  - src/main/kotlin/dev/yona/web/EnrollController.kt
  - src/main/kotlin/dev/yona/web/user/UserApiController.kt
  - src/main/kotlin/dev/yona/web/error/ErrorViews.kt
  - src/main/resources/static/assets/javascripts/esm/ui/ItemSearch.js
  - src/main/kotlin/dev/yona/domain/project/{OrganizationService,OrganizationManagementService}.kt
  - src/main/kotlin/dev/yona/access/AccessControl.kt
  - src/main/resources/static/assets/javascripts/esm/service/organization.{Global,New,Setting,Member,View}.js
updated: 2026-11-11
---

# UI 계약: 조직

`ui-improvements` 브랜치의 템플릿을 기준으로, 지금 코드가 하는 일을 새 UI가 지켜야 할 계약으로 정리한다. 이 영역은 파일이 많아 셋으로 나눈다.

| 파일 | 내용 |
|---|---|
| 이 파일 | 조직 목록, 만들기, 홈, 공통 틀(머리, 메뉴, 설정 탭), 설정, 구성원, 삭제, 감사, 권한 행렬, 발견 사항, 범위 점검 |
| [organization-lists.md](organization-lists.md) | 조직의 이슈, 게시판, 풀 리퀘스트 목록 |
| [organization-oauth.md](organization-oauth.md) | 조직 설정의 OAuth 앱 목록, 만들기와 수정, 비밀 값, 앱 정책 |

이미 있는 글은 다시 쓰지 않고 가리킨다: 동작 요약은 [../web/organization.md](../web/organization.md), 목록 화면은 [../web/organization-lists.md](../web/organization-lists.md), 접근 검사는 [../subsystems/access-control.md](../subsystems/access-control.md), 감사 로그는 [../architecture/audit-log.md](../architecture/audit-log.md), OAuth 앱 정책은 [../architecture/oauth2-server.md](../architecture/oauth2-server.md). 위키 글과 코드가 다르면 코드가 맞고, 다른 곳은 발견 사항에 적었다.

## 1. 영역 요약 (화면 목록)

| 템플릿 | URL (메서드) | 컨트롤러 | 용도 |
|---|---|---|---|
| `organization/list.html` | `/orgs` (GET) | `OrganizationController.list` (OrganizationController.kt:65) | 조직 찾기 목록 |
| `organization/create.html` | `/organizations/new` (GET, POST) | `OrganizationCreateController` (OrganizationCreateController.kt:33, 38) | 조직 만들기 |
| `organization/view.html` | `/organizations/{name}` (GET) | `OrganizationController.home` (OrganizationController.kt:46) | 조직 홈(프로젝트 목록, 구성원) |
| `organization/header.html` | (조각) `header(view)` | 조직 화면 모두가 쓴다 | 로고 배경 머리글, 가입 신청 |
| `organization/menu.html` | (조각) `menu(view, requestPath)` | 조직 화면 모두가 쓴다 | 홈, 이슈, 게시판, 풀 리퀘스트, 설정 아이콘 |
| `organization/partial-settingmenu.html` | (조각) `settingmenu(view, requestPath)` | 설정 계열 화면 | 설정 탭 6개 |
| `organization/setting.html` | `/organizations/{name}/settingform` (GET), `/setting` (POST) | `OrganizationSettingController` (OrganizationSettingController.kt:50, 56) | 이름, 설명, 로고 |
| `organization/members.html` | `/organizations/{name}/members` (GET, POST) 외 | `OrganizationMemberController` (OrganizationMemberController.kt:53, 76) | 구성원 추가, 역할, 삭제, 가입 신청 수락 |
| `organization/delete-form.html` | `/organizations/{name}/deleteForm` (GET), `/organizations/{name}` (DELETE) | `OrganizationSettingController` (OrganizationSettingController.kt:102, 110) | 조직 삭제 |
| `organization/audit.html` | `/organizations/{name}/audit` (GET) | `OrganizationAuditController.list` (AuditControllers.kt:94) | 조직 범위 감사 로그 (v1.6에 없는 추가 화면, X-9 A6, D-62) |
| `organization/board-list.html` 외 3개 | | | [organization-lists.md](organization-lists.md), [organization-oauth.md](organization-oauth.md) |

## 2. 공통 틀

### 2.1 레이아웃

- 모든 조직 화면은 `layout/organization-layout`(홈, 이슈, 게시판, 풀 리퀘스트 목록, 403 화면) 또는 `layout/site-layout` 메뉴 `NONE`(설정 계열)을 쓴다. 두 틀 모두 본문 맨 위에 `organization/header`와 `organization/menu` 조각을 놓고, 그 아래 `div.page-wrap-outer > div.project-page-wrap`이 본문이다 (view.html:12-17, setting.html:12-17).
- 조직 틀(`organization-layout`)만 상단 `navbar`에 조직을 넘긴다 (layout/organization-layout.html:10). 설정 계열 화면(`site-layout`)은 조직을 넘기지 않는다.
- 설정 계열 화면(설정, 구성원, 삭제, 감사, OAuth 앱, OAuth 정책)은 본문 맨 위에 `partial-settingmenu`의 `ul.nav.nav-tabs.mb-3`를 둔다.
- 반응형: `hide-in-mobile`은 화면 폭 720px 이하에서 `display:none !important`이다 (app/assets/stylesheets/scss/_responsive.scss:1, 302). 조직 화면에서는 홈의 프로젝트 아바타(view.html:39)와 이슈, 풀 리퀘스트, 게시판 줄의 아바타와 오른쪽 열, 두 단 보기 체크박스(`common/two-column-mode-checkbox-area`)가 이 클래스를 쓴다. 폭은 Bootstrap 5 격자(`col-lg-*`)가 정하고, `lg` 아래에서는 열이 세로로 쌓인다.

### 2.2 머리글 조각 `organization/header.html`

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 배경 로고, 아바타 | 그림 | 항상 | 조직 로고(`view.logoUrl`). 로고가 없으면 `/assets/images/group_default.png`. 배경 이미지와 `img`의 주소는 `@{}`로 컨텍스트 경로를 붙인다 | `GET /files/{첨부번호}` | | 누구나 | header.html:6, 10; OrganizationViewContext.kt:60 (`logoUrl`), 112 (`DEFAULT_LOGO`) |
| 이름 링크 | 링크 | 항상 | 앞에 메시지 `organization.label.group`(`.group-title-head`, 영어 `group`, 한국어 `그룹`)이 붙은 조직 이름. 조직 홈으로 | `GET /organizations/{name}` | | 누구나 | header.html:14 |
| 가입 신청 드롭다운 단추 | 단추 | `view.guest` (로그인했고, 사이트 관리자가 아니고, 조직 구성원 행이 없는 사용자) | `data-bs-toggle="dropdown"`으로 안내 상자를 연다. 신청했으면 단추에 `ybtn-info` 클래스 | | | 화면에서만 숨김. 서버는 아래 `POST`를 별도로 막는다 | header.html:17-22; OrganizationViewContext.kt:74-79 (`guest`) |
| 안내 상자 | 팝업(드롭다운) | 위와 같다 | 단추와 상자는 `ul.project-util > li` 안에 있다. 신청 전: `organization.you.may.want.to.be.a.member(이름)` + `organization.member.enrollment.help.before`. 신청 후: `organization.you.want.to.be.a.member(이름)` + `...help.after` | | 바깥 클릭이나 Esc로 닫힌다(Bootstrap 드롭다운) | | header.html:23-26 |
| 가입 신청 / 신청 취소 링크 (`#enrollBtn`) | 링크(JS가 가로챈다) | 위와 같다 | 클릭하면 기본 이동을 막고 POST를 보낸다 | 신청 전 `POST /organizations/{name}/enroll`, 신청 후 `POST /organizations/{name}/cancel/enroll` (바디 없음) | 성공(202)이면 `document.location.reload()`. 실패(그 밖의 상태, 네트워크 오류)면 알림 `Server Error` | 로그인한 비구성원, 사이트 관리자 제외. 아니면 400(바디 없음) | header.html:27-33; organization.Global.js:52-66; EnrollController.kt:86-120 |

서버 규칙 (EnrollController.kt:86-121):
- 조직이 없으면 400. 신청: 조직에 속하지 않은 로그인 사용자(`isOrganizationGuest`, 사이트 관리자와 익명은 아님)만 가능하고 아니면 400. 이미 신청한 사용자가 다시 신청해도 아무것도 하지 않고 성공이다.
- 취소: 신청 기록이 있으면 구성원이어도 취소할 수 있고(v1.6 결함 C097을 고침), 기록이 없으면 비구성원 로그인 사용자만 통과한다.
- 성공 응답은 `202 Accepted`, `Content-Type: application/json`(문자 집합 없음), 바디 `{"message":"You enrolled in 이름.","statusMonitorUrl":"http://호스트/organizations/이름"}` (취소는 `You canceled to enroll in 이름.`). 화면의 JS는 바디를 쓰지 않는다.
- 신청과 취소는 `OrganizationMemberRequestEvent`로 알린다(알림은 [../subsystems/notifications.md](../subsystems/notifications.md)).

스크립트: `organization.Global`(`esm/service/organization.Global.js`)을 `<script type="module">`로 불러 `$yona.loadModule("organization.Global")`로 시작한다 (header.html:42-47). 조직 화면마다 한 번 실행된다.

### 2.3 메뉴 조각 `organization/menu.html`

| 요소 | 종류 | 조건 | 동작 | 요청 | 권한 | 근거 |
|---|---|---|---|---|---|---|
| 홈 (`title.organizationHome`) | 탭 링크 | 항상. 풀어 쓴 `requestPath`(아래)가 조직 이름으로 끝나면 `active` | 조직 홈으로 | `GET /organizations/{name}` | 누구나 | menu.html:10-12 |
| 이슈 (`menu.issue`) | 탭 링크 | 항상. 풀어 쓴 `requestPath`에 `이름/issues`가 들어 있으면 `active` | 조직 이슈 목록 | `GET /organizations/{name}/issues` | 누구나 | menu.html:13-15 |
| 게시판 (`menu.board`) | 탭 링크 | 항상. `이름/boards` | 조직 게시판 목록 | `GET /organizations/{name}/boards` | 누구나 | menu.html:16-18 |
| 풀 리퀘스트 (`menu.pullRequest`) | 탭 링크 | 항상. `이름/pullrequests` | 조직 풀 리퀘스트 목록(열림) | `GET /organizations/{name}/pullrequests` | 누구나 | menu.html:19-21 |
| 설정 아이콘 (`.yobicon-cog`, 글자 `menu.admin`은 `.blind`) | 링크 | `view.canManage` (조직 관리자 또는 사이트 관리자) | 설정 폼으로 | `GET /organizations/{name}/settingform` | 서버도 같은 판정(`Operation.UPDATE`) | menu.html:23-33; OrganizationViewContext.kt:83 |

활성 표시는 `requestPath`(레이아웃이 모델에 넣는 현재 경로, layout/LayoutModelAdvice.kt:82)를 `#uris.unescapePath`로 퍼센트 인코딩을 풀고 소문자로 바꾼 값(`activePath`)과, 소문자로 바꾼 조직 이름(`activeName`)의 비교이다(menu.html:8-9). 그래서 주소를 다른 대소문자로 열거나 한글 이름이 인코딩돼도 활성 표시가 맞는다(F9). 설정 계열 화면에서는 위 네 탭 어느 것도 활성이 아니다. 홈 탭은 `endsWith`, 나머지 셋은 `이름/issues`, `이름/boards`, `이름/pullrequests`를 `contains`로 비교한다.

### 2.4 설정 탭 조각 `organization/partial-settingmenu.html`

`base = #paths.organization(이름)`. 링크는 모두 `@{${base + 경로}}`로 컨텍스트 경로를 붙인다. 활성은 `requestPath == base + 경로`이다(OAuth 앱 탭만 `startsWith`로 하위 화면까지 활성). 이 비교는 메뉴 조각과 달리 대소문자와 인코딩을 맞추지 않는다(F9).

| 탭 (메시지) | 링크 | 활성 조건 | 근거 |
|---|---|---|---|
| 설정 (`organization.settingFrom`) | `{base}/settingform` | 같음 | partial-settingmenu.html:9 |
| 구성원 (`organization.member`) | `{base}/members` | 같음 | :12 |
| 감사 (`organization.audit`) | `{base}/audit` | 같음 | :15 |
| OAuth 앱 (`organization.oauthApps`) | `{base}/oauth-apps` | `startsWith` | :18 |
| OAuth 앱 정책 (`organization.oauthPolicy`) | `{base}/oauth-policy` | 같음 | :21 |
| 삭제 (`organization.delete`) | `{base}/deleteForm` | 같음 | :24 |

탭은 조건 없이 모두 보인다. 이 조각을 쓰는 화면은 설정 권한이 있는 사용자만 열 수 있으므로 별도 숨김 조건이 없다.

### 2.5 요청과 응답 공통 계약

- **CSRF**: 서버는 쿠키 `XSRF-TOKEN`을 심고 POST, PUT, PATCH, DELETE에 같은 값을 헤더 `X-XSRF-TOKEN` 또는 인자 `_csrf`로 요구한다. 스크립트(`common/yona.Csrf.js`)가 같은 출처 `fetch`와 XHR에는 헤더로, 폼 제출에는 숨은 `_csrf` 입력을 더해 준다 (yona.Csrf.js:1-60). 이 영역의 모든 요청이 이 경로를 쓴다. 새 UI도 같은 쿠키와 헤더 규칙을 지켜야 한다. v1.6에는 없던 규칙이다.
- **리다이렉트**는 `303 See Other`이다(`RedirectSupport.see`, RedirectSupport.kt:114-124). 일회성 안내(flash)는 `warning`, `info`, `success`, `error` 키로 다음 요청 한 번에 레이아웃이 토스트(`$yona.notify`)나 대화상자로 보인다(common/scripts.html의 `flashMessages`).
- **Ajax 거부는 200 + 이동 주소**: 구성원 변경과 삭제가 거부되면 상태 `200`과 `{"location": "<구성원 화면 경로>"}`를 돌려주고 거부 이유는 flash에 남긴다(OrganizationMemberController.kt:129-133). 주소 앞에는 컨텍스트 경로가 붙고(`UrlPaths.withContext`) 화면의 JS가 그 주소로 이동한다. 새 UI는 이 규칙(오류인데 200)을 그대로 받아야 한다.
- **오류 화면**: 조직 화면 안의 403은 `error/forbidden-organization`(머리글과 메뉴 포함, 문구는 메시지 키), 404와 400은 `error/notfound-default`, `error/badrequest-default`이다 (web/error/ErrorViews.kt:20-77). JSON을 돌려주는 요청(탈퇴, 삭제)은 `{"errorMsg": "<메시지 키>"}` 형태이고 화면이 `Messages(키)`로 번역한다.
- **`Accept`, `HX-Request`에 따른 차이**: 이 파일의 화면은 모두 `Accept`와 무관하게 HTML(또는 위 JSON)이다. `HX-Request`로 조각을 돌려주는 곳은 이슈 목록뿐이다([organization-lists.md](organization-lists.md)).

### 2.6 권한 행렬 (조직 관리 계열)

"관리자"는 조직 관리자(`ORG_ADMIN` 역할), "구성원"은 `ORG_MEMBER` 역할, "사이트 관리자"는 `SiteAdmin` 행이 있는 사용자이다. 설정 권한 판정은 모두 `accessControl.isAllowed(사용자, 조직, Operation.UPDATE)`이고, 사이트 관리자는 항상 통과하며 그 밖에는 `isOrganizationAdmin`이다 (AccessControl.kt:106-117, 168, 290-294). 사이트가 익명 접근을 막고 있으면(`yona.allows-anonymous-access=false`) 익명은 항상 거부이다 (AccessControl.kt:109).

| 기능 | 비로그인 | 로그인 비구성원 | 구성원 | 관리자 | 사이트 관리자 | 거부 응답 |
|---|---|---|---|---|---|---|
| 조직 홈, 목록 보기 | 허용(사이트가 열려 있을 때) | 허용 | 허용 | 허용 | 허용 | 사이트가 닫혀 있으면 로그인 폼 303 |
| 홈의 구성원 영역 | 숨김 | 숨김 | 보임 | 보임 | 보임 | (서버는 구성원 목록을 화면 모델에서 뺄 뿐 별도 거부는 없다) |
| 설정 아이콘, 설정 계열 화면(설정, 구성원, 삭제, 감사) | 숨김, 서버 403 | 숨김, 403 | 숨김, 403 | 보임, 허용 | 보임, 허용 | 조직이 있으면 403 `error/forbidden-organization`(문구 `error.forbidden`), 조직이 없으면 404 `organization.member.unknownOrganization` |
| OAuth 앱, OAuth 앱 정책 화면 | 로그인 폼 303 | 403 | 403 | 허용 | 허용 | 비로그인은 `/users/loginform?redirectUrl=...`로 303(`@AnonymousCheck(requiresLogin=true)`), 나머지는 위와 같다 |
| 가입 신청, 취소 | 400 | 허용 | 400 | 400 | 400 | 바디 없는 400 |
| 구성원 추가 (POST members) | flash + 303 | flash + 303 | flash + 303 | 허용 | 허용 | 거부 이유를 flash(`organization.member.needManagerRole`)로 남기고 구성원 화면으로 303 |
| 역할 변경, 구성원 삭제 | 200 + location | 200 + location | 200 + location (본인 삭제만 허용) | 허용 | 허용 | `200 {"location":".../members"}` + flash |
| 탈퇴 (DELETE member/leave) | 로그인 폼 303 | 허용(구성원 행이 없으면 아무것도 안 하고 200) | 허용 | 본인이 마지막 관리자면 403 | 본인의 구성원 행이 `ORG_ADMIN`이고 관리자가 한 명뿐일 때만 403. 일반 구성원이거나 구성원 행이 없는 사이트 관리자는 허용(F8) | `403 {"errorMsg":"organization.member.atLeastOneAdmin"}` |
| 조직 삭제 | 404 JSON | 404 JSON | 404 JSON | 허용(프로젝트가 없을 때) | 허용 | `404 {"errorMsg": 메시지 키}` |
| 조직 만들기 | 로그인 폼 303 | 허용(게스트 계정은 폼(GET)과 POST 모두 막힘) | 허용 | 허용 | 허용 | 게스트 계정은 `/`로 303 + `error.forbidden.or.not.allowed` |

화면에서 숨기는 것과 서버가 막는 것이 다른 곳: (1) 설정 아이콘이 `canManage`로 숨는 것은 서버 판정과 같다. (2) 설정 탭 6개는 조건 없이 보이지만 화면 자체가 서버에서 막힌다. (3) 가입 신청 단추는 `view.guest`로 숨기고 서버는 같은 규칙으로 400을 준다.

## 3. 조직 목록 `organization/list.html`

### 3.1 개요

- `GET /orgs?filter=&pageNum=` → `OrganizationController.list` (OrganizationController.kt:65-85). 모델: `title`(`title.projectList`), `rows`(`OrgRow(view, readable)`), `totalElements`, `totalPages`, `filter`. 틀은 `site-layout` 메뉴 `PROJECTS` (list.html:11).
- 접근: `@AnonymousCheck`(클래스) + `@GuestProhibit`(게스트 계정은 `/`로 303 + `error.forbidden.or.not.allowed`). 사이트 설정 `hideProjectListing`이 켜져 있으면 403(`error.auth.unauthorized.waringMessage`). `pageNum < 1`이면 404 (OrganizationController.kt:71-72).
- 검색: `OrganizationService.search`가 이름 또는 설명에 `filter`가 들어 있는 조직을 대소문자 구분 없이 `id` 내림차순으로 30개씩 돌려준다 (OrganizationService.kt:69-72, `PAGE_SIZE = 30`).

### 3.2 레이아웃

위에서부터: 제목줄 `.site-breadcrumb-outer`(탭 둘), 검색 `div.search-wrap`, 본문 `ul.all-projects` 또는 빈 상태, 쪽 번호 `#pagination`. 조직 한 줄은 `li.project > .info-wrap`: 아바타(`.owner-avatar-wrap`, `@{}`로 컨텍스트 경로를 붙인 로고 주소), 이름 링크(`.header a.black`), 설명(`.desc`), 메시지 `organization.label.created`(영어 `created`, 한국어 `생성`) + `<strong>` 시각. 빈 상태(`totalElements == 0`)는 `div.error-wrap`(`ico-err1` + `organization.is.empty`) (list.html:35-38). 접근 불가 줄은 투명도 0.3의 흐린 줄이다 (list.html:61-70).

### 3.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 탭 "공개 프로젝트" (`project.public` + `title.projectList`) | 링크 | 항상 | 프로젝트 목록으로 | `GET /projects` | | 누구나 | list.html:17 |
| 탭 "조직" (`title.organization.list`) | 링크 | 항상, 활성 | 현재 화면 | `GET /orgs` | | | list.html:18 |
| 검색 입력 `filter` | 입력란 | 항상 (`autofocus`) | 입력값은 `th:value="${filter}"`로 되살림 | | | | list.html:29 |
| 검색 돋보기 단추 | 제출 | 항상 | 폼 제출 | `GET /orgs?filter=...` (`pageNum` 없음, 1쪽) | 같은 화면 | | list.html:27-31 |
| 조직 아바타, 이름 링크 | 링크 | `row.readable` | 조직 홈으로 | `GET /organizations/{name}` | | | list.html:45-52 |
| 쪽 번호 `#pagination` | 스크립트가 채움 | `totalElements != 0` | `yona.Pagination.update($("#pagination"), totalPages)`. 이전, 쪽 입력, 다음 | `GET /orgs?...&pageNum=N` (현재 주소의 쿼리를 유지) | | | list.html:75, 79-82 |

- 조직 읽기 권한은 전역 리소스의 `READ`라 누구나 통과이므로 `readable`이 거짓인 줄은 사실상 나오지 않는다(익명 접근이 막힌 사이트에서 익명이 이 화면에 오면 먼저 로그인 폼으로 간다). `properties.displayPrivateRepositories`가 참이면 `isAllowed`를 아예 보지 않는다 (OrganizationController.kt:77).
- 스크립트: 인라인 `$(document).ready`로 `yona.Pagination.update`만 부른다. 단축키 없음. 로컬 저장소와 쿠키를 읽고 쓰는 값 없음.
- 접근 불가 줄의 문구는 메시지 `organization.label.noPermission`, `created` 글자는 `organization.label.created`이다(list.html:56, 67, F10).

## 4. 조직 만들기 `organization/create.html`

### 4.1 개요

- `GET /organizations/new` (OrganizationCreateController.kt:33-36), `POST /organizations/new` (:38-51). 모델: `title`(`title.newOrganization`), `name`, `descr`(되돌려 보이는 입력값), `warning`(번역이 끝난 문구). 틀은 `site-layout` 메뉴 `NONE`, 제목 `app.name` (create.html:11).
- 접근: `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)`로 비로그인은 `/users/loginform?redirectUrl=/organizations/new`로 303 + `user.login.alert`. 폼(GET)과 POST 모두 `@GuestProhibit`이 더해져 게스트 계정은 `/`로 303 + `error.forbidden.or.not.allowed` (F7).

### 4.2 레이아웃

`div.form-wrap.new-project > form.frm-wrap[name=new-org]` 한 단. `legend`(`title.newOrganization`), `dl`: 이름(`dt`에 경고 영역 `.n-alert[data-errType=name] .orange-txt`, 라벨, `dd`에 입력), 설명(textarea, `resize: vertical`). 아래 `.actions`에 만들기와 취소 (create.html:15-46).

### 4.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| `name` | 입력 (`maxlength=250`, 시작할 때 초점) | 항상 | 서버가 형식, 예약어, 중복을 검사한다 | 바디 필드 `name` | | | create.html:30; organization.New.js:48-52 |
| `descr` | textarea | 항상 | 선택 입력. 길이 제한 없음 | 바디 필드 `descr` | | | create.html:37 |
| `.warning` | 문구 | `warning != null` | **앞 요청이 남긴** flash `warning`을 번역해 보인다 | | | | create.html:23; OrganizationCreateController.kt:53-70 |
| `.msg.wrongName` | 문구(처음엔 숨김) | JS 검증 실패 때 `display:block` | 클라이언트 검증 문구 `organization.name.alert` | | | | create.html:24; organization.New.js:68-71 |
| 만들기 단추 (`ybtn-success`) | 제출 | 항상 | 폼 제출. 클라이언트 검증을 통과하면 `NProgress.start()` | `POST /organizations/new` (`application/x-www-form-urlencoded`: `name`, `descr`) | 성공 `303 Location: /organizations/{name}`. 검증 실패 `400` + 같은 화면(`name`, `descr` 되살림) | 로그인 필요, 게스트 계정 제외 | create.html:41-43; OrganizationCreateController.kt:38-51 |
| 취소 링크 | 링크 | 항상 | `/`로 이동(`@{/}`, 컨텍스트 경로를 붙인다) | `GET /` | | | create.html:44 |

서버 검증 순서와 메시지 (OrganizationService.kt:46-57): 이름 없음 `error.required`, 형식 `user.wrongloginId.alert`, 예약어 `validation.reservedWord`, 중복(사용자 로그인 ID 또는 다른 조직, 대소문자 무시) `organization.name.duplicate`. 형식은 `LoginIdRules.LOGIN_ID_PATTERN = [a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*` (LoginIdRules.kt:16). 구분 기호 `_`와 `.`는 연달아 쓸 수 없다. 성공하면 만든 사람이 `ORG_ADMIN`으로 등록되고 감사 이벤트 `org.created`가 남는다 (OrganizationService.kt:59-67).

**오류가 보이는 방식(v1.6과 같은 특이한 동작)**: 400 응답의 이번 오류는 flash로 심겨 같은 응답에서 토스트(`$yona.notify`)로 보이고, 폼 위 `.warning`에는 **앞 요청이** 남긴 flash가 보인다. 그래서 첫 실패에서는 `.warning`이 비어 있고, 다음 요청의 폼 위에 같은 문구가 나타난다 (OrganizationCreateController.kt:58-70).

### 4.4 스크립트 동작

`organization.New`(`esm/service/organization.New.js`)를 모듈로 불러 `$yona.loadModule("organization.New", {"sFormName": "new-org"})`로 시작한다 (create.html:50-57). 하는 일: `#name`에 초점, `yona.ui.FormValidator`로 제출 때 `^[a-zA-Z0-9-가-힣]+([_.][a-zA-Z0-9-가-힣]+)*$` 검사. 어긋나면 `span.warning`을 숨기고 `span.msg`에 `organization.name.alert`를 `display:block`으로 보이며 제출하지 않는다. 통과하면 `NProgress.start()`. 클라이언트 정규식은 서버 정규식과 같은 모양이다(`organization.New.js:42`, F5). 단축키는 이 화면 고유 키가 없다. 전역 `CTRL+ENTER`가 입력란과 textarea에서 폼을 제출한다(common/scripts.html).

## 5. 조직 홈 `organization/view.html`

### 5.1 개요

`GET /organizations/{name}` → `OrganizationController.home` (OrganizationController.kt:46-63). 모델: `title`(조직 이름), `orgView`(`OrganizationViewContext`), `rows`(`ProjectRow(view, members, labels)`). 틀은 `layout/organization-layout`. 조직이 없으면 404 `error.notfound.organization`. 접근 검사는 클래스의 `@AnonymousCheck`뿐이다.

표시하는 프로젝트는 `OrganizationService.visibleProjects`(OrganizationService.kt:84-106): 조직 관리자와 사이트 관리자는 모두, 조직 구성원은 비공개가 아니거나 자신이 멤버인 것, 그 밖의 사람은 `hideProjectListing`이 꺼져 있을 때 공개(게스트 계정 제외)이거나 멤버인 것. 이름 소문자 순.

### 5.2 레이아웃

`div.project-home-header.row`가 두 열이다. 왼쪽 `col-lg-9`: 조직 설명(`#project-description`, h3), 검색줄(`col-lg-7` 검색 + `col-auto ms-auto` 새 프로젝트 단추), `ul.all-projects`. 오른쪽 `col-lg-3`: 구성원 영역(`bubble-wrap.gray.project-home`) 둘(관리자, 구성원). 프로젝트 줄은 `li.project`: 아바타(`hide-in-mobile`), 이름, 포크 원본, 자물쇠(비공개), `G` 표지(보호), 라벨, 개요, `by 소유자 at 시각`(`by`, `at`은 메시지 `organization.label.by`, `organization.label.at`), 오른쪽 `stats-wrap`(구성원 아바타, 구성원 수, 지켜보는 수, 전구 아이콘) (view.html:18-127). 프로젝트가 하나도 없으면 목록이 비어 있고 별도 빈 상태 문구는 없다. 로고와 구성원 아바타 주소는 모두 `@{}`로 컨텍스트 경로를 붙인다.

### 5.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| `#mylist-filter` 검색 입력 | 입력 | 항상 | `data-toggle="item-search" data-items="project-item"`. 입력란에 처음 초점이 갈 때 `esm/ui/ItemSearch.js`가 연결한다. 키를 뗀 200ms 뒤에, 줄의 `data-value`(`이름 + ' ' + 개요`)를 소문자로 바꾼 글에 입력한 글(소문자)이 들어 있으면 줄을 보이고 아니면 `display:none`. 공백뿐이면 모두 보인다 | (클라이언트 전용) | 서버 요청 없음 (F1) | | view.html:26, 35-37; ItemSearch.js:50-68 |
| 돋보기 단추 | 단추(`type=button`) | 항상 | 동작 없음 | | | | view.html:27 |
| 새 프로젝트 (`button.newProject`) | 링크 | `org.canManage` | 소유자가 미리 정해진 프로젝트 만들기 | `GET /projectform?owner={조직 이름}` | 폼 화면. 소유자 선택에 조직이 들어 있다 | 서버는 `ProjectCreateController`가 따로 판정 | view.html:30-32 |
| 프로젝트 아바타, 이름 링크 | 링크 | 항상 | 프로젝트로 | `GET /{owner}/{name}` | | 프로젝트 읽기 권한 | view.html:40, 46 |
| 포크 원본 링크 | 링크 | `row.view.forked` | 원본 프로젝트로 | `GET /{owner}/{name}` | | | view.html:47-52 |
| 자물쇠 아이콘 | 표지 | 비공개 프로젝트 | | | | | view.html:54 |
| `G` 표지 (`title`은 메시지 `organization.label.groupProject`) | 표지 | 보호 프로젝트 | 툴팁 글자(영어 `Group Project`, 한국어 `그룹 프로젝트`) | | | | view.html:55 |
| 라벨 배지 | 링크 | 라벨이 있을 때, 라벨마다 | 프로젝트 목록을 그 라벨로 거른다. 분류 이름 소문자가 클래스로 붙는다 | `GET /projects?labelIds={id}` | | | view.html:56 |
| 소유자 링크 | 링크 | 항상 | 소유자(사용자나 조직) 화면 | `GET /{owner}` | | | view.html:60 |
| 구성원 아바타 | 링크 | 프로젝트 소유자와 다른 구성원마다 | 사용자 화면 | `GET /{loginId}` | | | view.html:68-74 |
| 전구 아이콘 (`ramp-on`, `ramp-off`) | 툴팁 | 항상 | `data-bs-toggle="tooltip"`. 보는 사람이 지켜보면 `project.default.group.watching`, 아니면 `project.you.are.not.watching('')`. 툴팁은 전역 `mouseenter` 위임이 연다 | | | | view.html:79-80; common/scripts.html |
| 관리자 영역 (`user.role.org_admin`) | 영역 | `org.canSeeMembers` | 관리자 아바타와 이름 링크(툴팁은 `loginId`) | `GET /{loginId}` | | 구성원 이상 | view.html:88-106 |
| 구성원 영역 (`user.role.org_member`) | 영역 | `org.canSeeMembers` | 같은 모양 | | | | view.html:108-125 |
| 탈퇴 단추 (`#groupLeaveBtn`) | 단추 | 관리자 영역: `!leaveBlocked and org.admin`. 구성원 영역: `!leaveBlocked and org.member` | `data-href="/organizations/{name}/member/leave"`. 클릭하면 확인 모달을 연다 | | 아래 모달 | 구성원 | view.html:93, 112 |
| 탈퇴 확인 모달 `#alertLeave` | 모달 | 항상 DOM에 있다 | 제목 `organization.member.leave`, 본문 `organization.member.leaveConfirm`, 예(`#leaveBtn`)와 아니요(`data-bs-dismiss`). 닫는 법: 아니요, 닫기 단추(`btn-close`), Esc, 바깥 클릭(Bootstrap 기본) | | | | view.html:132-144 |
| 예 (`#leaveBtn`) | 단추 | 모달 안 | `data-href`로 DELETE 요청 | `DELETE /organizations/{name}/member/leave` (`Accept: text/html`) | 성공 200 `{"location": "{컨텍스트 경로}/organizations/{name}"}` → 그 주소로 `location.replace`. 응답이 JSON이 아니면 `location.reload()`. 실패: 모달을 닫고 `$yona.notify(문구, 3000)`. 문구는 `Messages(응답의 errorMsg)`, 파싱에 실패하면 `organization.member.leave.unknownerror` | 로그인 필요(비로그인은 로그인 폼 303). 마지막 관리자는 `403 {"errorMsg":"organization.member.atLeastOneAdmin"}`, 없는 조직은 `404 {"errorMsg":"organization.member.unknownOrganization"}` | organization.View.js:57-90; OrganizationMemberController.kt:118-127 |

`leaveBlocked`는 "보는 사람이 조직 관리자(`ORG_ADMIN` 역할)이고 관리자가 한 명뿐"이다 (OrganizationViewContext.kt:92-95). 사이트 관리자라는 이유만으로는 막히지 않는다. 그래서 효과는 "마지막 관리자는 탈퇴 단추가 숨고 서버도 막는다, 일반 구성원(사이트 관리자 포함)은 보인다". v1.6은 조건이 거꾸로였다(C157, [../web/organization.md](../web/organization.md)의 "v1.6 결함").

스크립트: `organization.View`(`esm/service/organization.View.js`), `$yona.loadModule("organization.View")` (view.html:145-150). `#groupLeaveBtn`의 클릭, `#leaveBtn`의 클릭만 연결한다. 로컬 저장소, 쿠키, 폴링, 단축키 없음. 툴팁은 전역 위임이다.

## 6. 조직 설정 `organization/setting.html`

### 6.1 개요

- `GET /organizations/{name}/settingform` (OrganizationSettingController.kt:50-54), `POST /organizations/{name}/setting` (:56-100). 모델: `title`, `orgView`, `nameWarning`(이름 입력란 아래 안내), `submittedName`, `submittedDescr`(검증 실패로 다시 그릴 때 제출한 값), 필요하면 `flashMessages`.
- 접근 (`guard`): 조직이 없으면 404 `organization.member.unknownOrganization`. `UPDATE` 권한이 없으면 403 `error/forbidden-organization`(`error.forbidden`). 로그인하지 않은 사용자는 사이트가 열려 있으면 이 403을 받고, 닫혀 있으면 로그인 폼으로 간다(`@AnonymousCheck`가 클래스에 있고 `requiresLogin`이 아니기 때문이다).

### 6.2 레이아웃

설정 탭 아래 `form#saveSetting.nm[enctype=multipart/form-data]`. `div.bubble-wrap.gray > div.box-wrap.top.clearfix.frm-wrap` 안에 왼쪽 `setting-box.left`(로고 미리보기 `.logo-wrap`에 배경 이미지, 안내 `ul.descs`: 로고, 형식 `bmp, jpg, gif, png`, 최대 `5MB`, 업로드 단추)와 오른쪽 `dl.setting-box.right`(이름, 설명). 아래 `.box-wrap.bottom`에 저장 단추 (setting.html:19-63). 로고 미리보기의 배경 주소는 `@{}`로 컨텍스트 경로를 붙인다(setting.html:24). 안내의 형식과 크기 글자는 템플릿에 고정이다(서버 상수와 따로 적혀 있다, F11).

### 6.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| `id` (숨김) | 숨김 입력 | 항상 | 조직 번호. **서버는 쓰지 않는다**(대상은 주소로 정한다) | 바디 `id` | | | setting.html:20; 위키 A-24 |
| 로고 업로드 (`#logoPath`, `accept="image/*"`) | 파일 입력(가짜 단추 `.fake-file-wrap`) | 항상 | 파일을 고르면 이미지인지 검사하고(`$yona.isImageFile`) 아니면 `$yona.showAlert(project.logo.alert)` 후 값을 비운다. 이미지면 **폼 전체를 곧바로 제출**한다 | `POST /organizations/{name}/setting` (multipart: `id`, `name`, `descr`, `logoPath`) | 아래 저장과 같다 | | setting.html:33; organization.Setting.js:63-73 |
| `name` (`#project-name`, `maxlength=250`) | 입력 | 항상 (값은 `submittedName`이 있으면 그것, 없으면 조직 이름) | | 바디 `name` | | | setting.html:45 |
| `.warning` | 문구 | `nameWarning != null` | 서버가 보낸 이름 오류(번역 끝남) | | | | setting.html:47 |
| `.msg.wrongName` | 문구(처음엔 숨김) | 클라이언트 검증 실패 | `organization.name.alert` | | | | setting.html:48; organization.Setting.js:102-107 |
| `descr` (`#project-desc`, `maxlength=250`) | textarea | 항상 (값은 `submittedDescr` 우선) | | 바디 `descr` | | | setting.html:55 |
| 저장 (`#save`, `button.save`) | 제출 | 항상 | 폼 제출 | `POST /organizations/{name}/setting` | 아래 | 관리자, 사이트 관리자 | setting.html:61 |

**저장 규칙과 응답** (OrganizationSettingController.kt:56-100; OrganizationManagementService.kt:178-219):

| 순서 | 검사 | 결과 |
|---|---|---|
| 1 | 조직 없음 | 404 `organization.member.unknownOrganization` |
| 2 | `UPDATE` 권한 없음 | 403 조직 403 화면 + 같은 응답의 flash `organization.member.needManagerRole` |
| 3 | 이름이 비었거나 형식이 틀리거나 예약어 | 400 + 설정 화면, 이름 아래 `organization.name.alert`, 제출한 값 되살림. flash는 만들지 않는다 (F4). 클라이언트 검증이 이 경우를 미리 막는다 |
| 4 | 중복 이름 (자기 이름과 대소문자만 다르면 중복 아님) | 400 + 설정 화면, 이름 아래 `organization.name.duplicate`, 제출한 값 되살림. flash는 만들지 않는다 |
| 5 | 로고가 이미지 확장자(`jpg, jpeg, png, gif, bmp`)가 아님 `project.logo.alert`, 크기가 `ProjectSettingService.LOGO_FILE_LIMIT_SIZE`(5MB)를 넘음 `project.logo.fileSizeAlert` | 400 + 설정 화면 + 같은 응답의 flash(토스트). 둘 다이면 크기 문구가 이긴다 |
| 6 | 성공 | `303 Location: /organizations/{새 이름}/settingform` (이름이 바뀌어도 새 이름 주소로) + 다음 요청의 flash `success` = `organization.setting.saved`(레이아웃이 토스트로 보인다) (F16) |

저장이 하는 일: 새 로고가 있으면 이전 로고를 지우고 새로 저장, 이름이 바뀌면 조직의 모든 프로젝트 소유자를 바꾸고 Git 저장소 디렉터리를 옮김(실패하면 예외), 즐겨찾기 조직 이름 갱신, 감사 이벤트 `org.settings_changed`(바뀐 이름, 설명, 로고) (OrganizationManagementService.kt:187-219).

스크립트: `organization.Setting`(`esm/service/organization.Setting.js`), `$yona.loadModule("organization.Setting")` (setting.html:66-71). 폼 이름은 `update-org`. 제출 검증 규칙은 만들기와 같은 정규식 `^[a-zA-Z0-9-가-힣]+([_.][a-zA-Z0-9-가-힣]+)*$`이다. 통과하면 `NProgress.start()`. 로고 `change`가 폼 `submit`을 호출하므로 같은 검증이 먼저 돈다(`orgmisc.test.mjs`가 확인).

## 7. 조직 구성원 `organization/members.html`

### 7.1 개요

- `GET /organizations/{name}/members` (OrganizationMemberController.kt:53-74). 모델: `title`, `orgView`, `rows`(`Row(userId, loginId, displayName, avatarUrl, roleName)`, 구성원 행 순서), `roles`(`RoleItem`: 조직 관리자 `ORG_ADMIN`, 조직 멤버 `ORG_MEMBER`, 번호순), `applicants`(가입 신청자).
- 접근: 조직이 없으면 404 `organization.member.unknownOrganization`, `UPDATE` 권한이 없으면 403 조직 403 화면(`error.forbidden`). 구성원이어도 관리자가 아니면 볼 수 없다.

### 7.2 레이아웃

설정 탭, 추가 폼 `div.inner-bubble > form#addNewMember.d-flex.gap-2`, 구성원 목록 `ul.members.project.row`(한 줄 `li.member.col-lg-6`: 아바타 64px, `.member-name`, `.member-id`, `.member-setting`에 역할 드롭다운과 삭제 단추), 삭제 확인 모달 `#alertDeletion`, 가입 신청 영역(신청자가 있을 때만 `legend` 제목과 건수, `div.row`에 신청자마다 `col-lg-2`) (members.html:19-82).

### 7.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| `#loginId` | 입력 (`required`, `autocomplete=off`, `pattern=^[a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*$`, `title=user.wrongloginId.alert`) | 항상 | 열릴 때 초점. 타이핑하면 자동 완성 목록(최소 1글자) | 자동 완성: `GET /users?query=...` (`Accept: application/json`) | 항목 `{info(HTML, 이름과 아이디는 HTML 이스케이프됨), loginId}` 최대 10건, 더 있으면 `Content-Range: items 10/전체`. 고르면 입력값이 `loginId`가 된다 | 아래 `/users` 규칙 | members.html:21-24; organization.Member.js:46-103 |
| 추가 (`button.add`) | 제출 | 항상 | 폼 제출 | `POST /organizations/{name}/members` (`loginId`) | 항상 `303 → /organizations/{name}/members`. 거부면 flash `warning` (사용자 없음 `organization.member.unknownUser`, 게스트 사용자 `error.forbidden.to.guest.user`, 조직 없음 `organization.member.unknownOrganization`, 권한 없음 `organization.member.needManagerRole`, 이미 구성원 `organization.member.alreadyMember`, 이 순서로 검사). 성공이면 안내 없이 구성원 목록에 나타남 | 관리자, 사이트 관리자 | members.html:25; OrganizationMemberController.kt:76-83; OrganizationManagementService.kt:71-84 |
| 구성원 아바타 링크 | 링크 | 구성원마다 | 사용자 화면. 아바타 `img`의 주소는 `@{}`로 컨텍스트 경로를 붙인다 | `GET /{loginId}` | | | members.html:31-32 |
| 역할 드롭다운 (`.btn-group[data-name="roleof-{loginId}"]`) | 드롭다운 단추 | 구성원마다 | 현재 역할(`user.role.{role}`)이 라벨. 항목은 `roles` 둘(`data-value`=역할 번호, 현재 역할에 `active`와 `data-selected=true`) | | 항목을 누르면 아래 "역할 변경" | | members.html:37-42 |
| 역할 항목 `a[data-action="apply"]` | 링크 | 항목마다 | 라벨과 숨은 입력 `roleof-{loginId}`가 항목 값으로 바뀐 뒤(전역 `yona.ui.Dropdown`), 요청을 보낸다 | `POST /organizations/{name}/member/{userId}/edit` (바디 `id`=역할 번호, XHR, `X-Requested-With`) | 성공 `204`(바디 없음)이면 아무 화면 변화도 없다. 거부는 `200 {"location": "{컨텍스트 경로}.../members"}`이고 화면이 그 주소로 `location.replace`(flash 안내가 다음 화면에 나옴). 네트워크 오류나 4xx, 5xx이면 `fOnError`가 `$yona.alert`로 안내하고(문구는 삭제 실패와 같은 규칙: 403은 `project.member.ownerCannotLeave`가 본문에 있으면 그것, 아니면 `error.forbidden`, 404는 `organization.member.unknownOrganization`, 그 밖은 `error.badrequest`) 드롭다운의 라벨, 활성 항목, 숨은 입력 `roleof-*`를 서버가 가진 원래 역할(`li[data-selected=true]`)로 되돌린다 | 관리자, 사이트 관리자. 검사 순서: 역할 번호가 숫자가 아님 `organization.member.unknownRole`, 구성원이 아님 `organization.member.isNotAMember`, 권한 없음 `organization.member.needManagerRole`, 마지막 관리자를 멤버로 낮춤 `organization.member.atLeastOneAdmin`, 번호 없음은 `unknownRole`. 존재하지 않는 역할 번호는 아무것도 바꾸지 않고 `204` | members.html:41; organization.Member.js:190-227, 239-267; OrganizationManagementService.kt:101-129 |
| 삭제 (`a[data-action="delete"]`, `button.delete`) | 링크 | 구성원마다 | 확인 모달 `#alertDeletion`을 연다. 누른 행의 `data-href`를 "예"에 묶는다(누를 때마다 이전 묶음을 지우고 다시 묶어 마지막 행만 삭제한다) | | | | members.html:43; organization.Member.js:153-169 |
| 모달 "예" (`#deleteBtn`) | 단추 | 모달 안 | 삭제 요청 | `DELETE /organizations/{name}/member/{userId}/delete` | 성공 `200 {"location": 이동 주소}`(앞에 컨텍스트 경로). 본인을 지웠으면 조직 홈, 다른 사람이면 구성원 화면. 화면이 `location.replace`. 거부는 같은 `200 {"location": ".../members"}` + flash. 실패 상태: 403이면 문구 `project.member.ownerCannotLeave`가 본문에 들어 있으면 그것, 아니면 `error.forbidden`. 404이면 `organization.member.unknownOrganization`, 그 밖에는 `error.badrequest`. 어느 경우든 `$yona.alert`로 알리고 모달을 닫는다 | 관리자, 사이트 관리자, 또는 본인(`actor.id == userId`). 검사 순서: 구성원이 아님 `isNotAMember`, 권한 없음 `needManagerRole`, 마지막 관리자 `atLeastOneAdmin` | members.html:59; organization.Member.js:159-209; OrganizationMemberController.kt:100-116; OrganizationManagementService.kt:136-149 |
| 모달 "아니요", 닫기 | 단추 | | 모달을 닫는다(Esc, 바깥 클릭 포함) | | | | members.html:50-62 |
| 가입 신청자 아바타, 이름 링크 | 링크 | `applicants`가 비어 있지 않을 때 | 사용자 화면 | `GET /{loginId}` | | | members.html:71, 76 |
| 신청자 "추가" (`.enrollAcceptBtn`) | 단추 | 위와 같다 | `data-loginId`를 `#loginId`에 넣고 추가 폼을 제출한다 | `POST /organizations/{name}/members` (`loginId`) | 위 "추가"와 같다. 성공하면 신청 기록이 정리되고 승인 이벤트가 나간다 | 관리자 | members.html:78; organization.Member.js:143-148; OrganizationManagementService.kt:80-81, 86-97 |

신청 거절 단추는 없다(v1.6과 같다). 신청자는 본인이 취소해야 사라진다.

`/users` 자동 완성 규칙 (UserApiController.kt:33-54): 요청 `Referer`에서 `#`와 `?` 뒤를 뗀 경로가 `members`로 끝나지 않거나 `Accept`가 JSON을 받지 않으면 `406`(바디 없음). `query`가 비면 `[]`. 최대 10건. 항목의 `info`는 아바타 `<img class='mention_image'>`, `<b class='mention_name'>이름</b>`, `<span class='mention_username'> @loginId</span>` HTML 문자열이다(이름과 아이디는 HTML 이스케이프해서 담는다, F12). 입력란 목록은 이 문자열을 HTML로 그린다(organization.Member.js:56-61).

스크립트: `organization.Member`(`esm/service/organization.Member.js`), `$yona.loadModule("organization.Member", {"sActionURL": [[@{/users}]]})`(컨텍스트 경로가 붙는다) (members.html:85-91).
- 이벤트: `.members`에 `[data-action=apply]`, `[data-action=delete]` 클릭 위임(organization.Member.js:131-138), `.enrollAcceptBtn` 클릭, 시작할 때 `#loginId.focus()`. 자동 완성은 `yona.ui.Typeahead("#loginId")`이고 결과를 `Content-Range`로 전체 여부를 판단해 같은 질의를 다시 보내지 않고 캐시한다.
- 역할 드롭다운의 라벨과 숨은 입력은 이 화면의 스크립트가 아니라 `common/scripts.html`이 `.btn-group[data-name]`마다 만든 `yona.ui.Dropdown`이 처리한다(`data-activate="manual"`이면 건너뜀) (common/scripts.html:78-84). 이 초기화가 없으면 `roleof-*` 입력이 없어 역할 변경은 동작하지 않는다.
- 폴링, 지연 로딩, 로컬 저장소, 쿠키 없음. 이 화면 고유 단축키 없음.

## 8. 조직 삭제 `organization/delete-form.html`

### 8.1 개요

`GET /organizations/{name}/deleteForm` (OrganizationSettingController.kt:102-108). 모델: `title`, `orgView`. 접근은 설정 화면과 같다(`guard`). 삭제 요청 자체는 `DELETE /organizations/{name}` (:110-118).

### 8.2 레이아웃

설정 탭 아래 `div.box-wrap.bottom`에 위험 단추 하나, 그 아래 확인 모달 `#alertDeletion` (delete-form.html:19-37).

### 8.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 삭제 단추 (`#btnDelete`, `organization.delete.this`) | 링크(`ybtn-danger`, `data-bs-toggle="modal"`, `href="#alertDeletion"`) | 항상 | 확인 모달을 연다 | | | 화면 접근은 설정 권한 | delete-form.html:20-21 |
| 모달 | 모달 | | 제목 `organization.delete.requestion`, 본문 `organization.delete.reaccept`, 예(`#btnDeleteExec`), 아니요. 닫는 법 Bootstrap 기본(아니요, 닫기, Esc, 바깥 클릭) | | | | delete-form.html:25-37 |
| 예 (`#btnDeleteExec`) | 단추 | 모달 안 | `yona.http.ajax({type:"delete", url: 조직 주소})` | `DELETE /organizations/{name}` | 성공(2xx, 서버는 `303 /`이고 fetch가 따라간다)이면 `location.href = [[@{/}]]`(컨텍스트 경로를 붙인다, organization-F26). 오류이면 모달을 닫고 `$yona.alert(Messages(응답 errorMsg))`, 파싱 실패 시 `organization.delete.error` | 서버 판정 `Operation.DELETE`(조직 관리자 또는 사이트 관리자). 실패 응답은 모두 `404 {"errorMsg": 키}`: 조직 없음 `organization.member.unknownOrganization`, 권한 없음 `organization.member.needManagerRole`, 프로젝트가 남음 `organization.delete.impossible.project.exist` | delete-form.html:41-61; OrganizationSettingController.kt:110-118; OrganizationManagementService.kt:229-251 |

삭제는 구성원 행, 가입 신청, 즐겨찾기, 조직이 소유한 OAuth 앱과 승인과 정책을 함께 지우고 감사 이벤트 `org.deleted`를 남긴다 (OrganizationManagementService.kt:238-250). 스크립트는 템플릿 인라인(`<script th:inline="javascript">`)이고 모듈 로딩이 없다. 단축키 없음.

## 9. 조직 감사 로그 `organization/audit.html`

### 9.1 개요

`GET /organizations/{name}/audit` → `OrganizationAuditController.list` (AuditControllers.kt:94-101). 모델: `title`(조직 이름), `orgView`, 그리고 `AuditViewSupport.model`의 값(`base`, `rows`, `page`, `pages`, `total`, `size`, `prevUrl`, `nextUrl`, `filter`, `outcomes`, `exportCsv`, `exportJsonl`, `exportJson`) (AuditViewSupport.kt:59-81). 틀은 `site-layout` 메뉴 `NONE`. 접근: 조직 없음 404 `organization.member.unknownOrganization`, `UPDATE` 권한 없음 403 조직 403 화면. v1.6에 없는 추가 화면이다.

### 9.2 레이아웃

설정 탭, 설명 문단(`audit.description.organization`), 그 아래 `audit/list :: auditList(true, false, true)` 조각: 필터 폼, 건수와 내려받기 줄, 표, 이전과 다음 (audit.html:15-17; audit/list.html:8-68). 매개변수 `exportable=true`(내려받기 링크 보임), `verifiable=false`(해시 사슬 검증 단추 없음), `showFilter=true`.

### 9.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 필터 폼 | 폼(GET, `action`은 `@{${base}}`로 컨텍스트 경로를 붙인다) | 항상 | 입력: `actor`(행위자), `action`(동작), `outcome`(`SUCCESS`, `FAILURE`, `DENIED` 또는 전체), `target`, `ip`, `from`, `to`(날짜) | `GET /organizations/{name}/audit?actor=&action=&outcome=&target=&ip=&from=&to=` | 같은 화면. 모르는 결과 이름과 날짜 형식은 무시한다. 기간은 UTC 날짜이고 `to`는 그 날 끝까지 | 설정 권한 | audit/list.html:9-26; AuditViewSupport.kt:31-40 |
| 적용 단추, 초기화 링크 | 제출, 링크 | 항상 | 제출, `base`로 이동 | | | | audit/list.html:23-24 |
| 건수 (`audit.pager.info`) | 문구 | 항상 | 전체 건수, 현재 쪽, 전체 쪽 | | | | audit/list.html:29 |
| CSV, JSON Lines, JSON 링크 | 링크 | `exportable` | 현재 필터 그대로 내려받기 | `GET /organizations/{name}/audit/export?format=csv|jsonl|json&{필터}` (`limit` 기본 10000, 최대 100000, 화면 링크는 `limit`을 싣지 않는다) | 첨부 파일 `audit-{UTC 날짜}.{확장자}`. 형식이 틀리면 400. 내려받기도 감사 이벤트 `audit.exported`로 남는다 | 설정 권한. 조직 없음 404, 권한 없음 403(조직 머리글 없는 기본 403 화면) | audit/list.html:30-34; AuditControllers.kt:34-44, 103-109 |
| 표 | 표 | 항상 | 시각, 행위자와 종류, 동작, 결과 배지(성공 초록, 거부 빨강, 그 밖 노랑), 대상, IP, 상세(120자까지 보이고 `title`에 전체) | | 비면 한 줄 `audit.empty` | | audit/list.html:40-63; AuditViewSupport.kt:47-53 |
| 이전(`audit.pager.prev`), 다음(`audit.pager.next`) | 링크 | `prevUrl`, `nextUrl`이 있을 때 | `page`와 `size`를 바꿔 이동. 필터는 유지 | `GET .../audit?{필터}&page=N&size=S` | | | audit/list.html:65-68; AuditViewSupport.kt:64-66 |

- 쪽 크기: 기본 50, 1에서 200 사이로 맞춘다(`size`, 화면에는 입력란이 없다). `page`는 1부터이고 1 미만은 1 (AuditQueryService.kt:117-118, AuditViewSupport.kt:61-62).
- 조직 범위: `AuditScope.Organization(id)`가 조직 번호 조건을 항상 덧붙여 다른 조직의 기록은 필터로도 볼 수 없다 (AuditQueryService.kt:102).
- 필터에 없는 매개변수 `projectId`, `targetType`도 요청에 있으면 `AuditFilter`에 반영된다(화면은 보내지 않는다).
- 스크립트: 이 화면은 해시 검증 스크립트(`verifiable`)를 싣지 않는다. 단축키, 저장소, 폴링 없음.

## 10. 발견 사항

확인한 사실은 그대로 적고, 추측은 "추측"이라고 표시했다. 접두어 `organization-F`는 이 영역(조직 세 파일 모두)의 번호이다.

| 번호 | 내용 | 근거 |
|---|---|---|
| organization-F1 **[수정됨, A-457]** | 조직 홈의 프로젝트 거르기 입력 `#mylist-filter`는 `data-toggle="item-search" data-items="project-item"`와 줄마다 `data-item="project-item" data-value="이름 개요"`로 동작한다. v1.6은 `jquery.search.js`가 속성으로 자동 연결했고 D-103 정리(A-410)가 "`.search(` 호출이 없다"는 기준으로 이 파일을 지워 한동안 동작하지 않았다. (2026-11-11 수정: `esm/ui/ItemSearch.js`를 새로 만들어 `focusin`으로 연결한다. `esm/index.js`가 불러온다.) 같은 입력이 `milestone/view.html`에도 있다 | view.html:26, 35-37; ItemSearch.js:50-68; decisions.md A-410; `ItemSearchModuleTest` |
| organization-F2 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 역할 변경(`POST .../edit`)은 성공 `204`에 화면 변화가 라벨 갱신뿐이고, 요청 실패(네트워크, 4xx, 5xx)는 `sendForm`에 `fOnError`가 없어 아무 안내가 없었고 라벨이 바뀐 채로 남아 실제와 달랐다. 거부는 `200 + location`이라 이동한다. (2026-11-11 수정: `fOnError`가 `$yona.alert`로 안내하고 라벨과 값을 `data-selected` 역할로 되돌린다.) | organization.Member.js:190-227, 249-258; common/yona.Common.js:309-343 |
| organization-F3 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `/users` 자동 완성은 `Referer` 전체가 `members`로 끝나야 답해서 구성원 화면 주소에 쿼리나 해시가 붙으면 406이었다. (2026-11-11 수정: `Referer`에서 `?`와 `#` 뒤를 떼고 판정한다.) | UserApiController.kt:35-39 |
| organization-F4 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 설정 저장에서 이름이 비었거나 형식이 틀리거나 예약어이면 400이 아니라 500이었다(`check` 실패). (2026-11-11 수정: 이름 입력란 아래 `organization.name.alert`와 함께 400으로 설정 화면을 다시 그린다.) | OrganizationSettingController.kt:73-77 |
| organization-F5 **[검증: 수정함(A-464, 2026-11-11)]** | 이름 규칙이 세 군데에서 달랐다: 서버 `a..b` 허용, 클라이언트 거부, 구성원 추가 입력의 `pattern`은 한글 거부. (2026-11-11 수정: 서버 `LOGIN_ID_PATTERN = [a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*`로 구분 기호 연달아 쓰기를 막아 클라이언트와 같게 했고, 구성원 추가 입력의 `pattern`도 `^[a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*$`로 한글을 받는다. 이미 만들어진 이름은 그대로 둔다.) v1.6은 `members.scala.html:42`가 한글을 거부했다 | LoginIdRules.kt:16; organization.New.js:42; members.html:24; `yona-v1.6-run/app/views/organization/members.scala.html:42` |
| organization-F6 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 가입 신청 드롭다운은 `ul.project-util` 안에 `button`과 `div`를 바로 넣어 HTML 구조가 올바르지 않았다. (2026-11-11 수정: `li`로 감쌌다.) | header.html:18-36 |
| organization-F7 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `GET /organizations/new`(폼)는 게스트 계정에도 열리고 `POST`만 `@GuestProhibit`이 막아, 게스트가 폼을 채우고 제출해야 `/`로 쫓겨났다. (2026-11-11 수정: 폼에도 `@GuestProhibit`을 붙였다.) | OrganizationCreateController.kt:33-40 |
| organization-F8 **[검증: v2 신규 결함, 수정됨 A-460]** | 탈퇴를 막는 기준이 `Operation.LEAVE`(조직 리소스에서는 `UPDATE`와 같은 분기라 사이트 관리자도 "허용")여서, 사이트 관리자이면서 일반 구성원인 사람이 관리자가 한 명뿐인 조직에서 탈퇴하지 못했다. (2026-11-11 수정: 막는 기준을 본인의 구성원 행이 `ORG_ADMIN` 역할인 것으로 바꿨다. 화면의 `leaveBlocked`와 서버 `leave`가 같은 기준이다.) 시험 `OrganizationMemberTest`에 이 조합이 있다 | OrganizationManagementService.kt:157-169; OrganizationViewContext.kt:92-95; OrganizationMemberTest.kt:352 |
| organization-F9 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 메뉴의 활성 표시가 `requestPath` 문자열 비교라 주소의 대소문자가 조직 이름과 다르면(조직 조회는 대소문자 무시) 탭이 활성이 되지 않았다. (2026-11-11 수정: 메뉴 조각은 `requestPath`를 풀고 소문자로 맞춰 비교한다.) 설정 탭 조각(`partial-settingmenu`)의 `==` 비교는 그대로여서 같은 불일치가 남아 있다. `#paths.organization(이름)`의 인코딩과 `requestPath`(인코딩된 요청 경로)가 어긋나는 이름(한글)에서도 같다(추측: 시험 없음) | menu.html:8-21; partial-settingmenu.html:9-24 |
| organization-F10 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 번역되지 않는 고정 문구가 있었다: 조직 목록의 `created`와 접근 불가 문구, 조직 홈의 `by`, `at`, `Group Project` 툴팁, 머리글의 `group`. (2026-11-11 수정: 메시지 `organization.label.created`, `noPermission`, `by`, `at`, `groupProject`, `group`으로 옮겼다. 영어는 이전 글자 그대로, 한국어는 `생성`, `이 프로젝트의 정보를 볼 권한이 없습니다`, `만든이`, `생성`, `그룹 프로젝트`, `그룹`.) | list.html:56, 67; view.html:55, 60-61; header.html:14; messages_ko_KR.properties:2014-2019 |
| organization-F11 **[검증: 결함 아님]** | 설정 화면의 로고 안내 `bmp, jpg, gif, png`와 `5MB`는 템플릿에 고정이고, 서버가 받는 확장자는 `jpg, jpeg, png, gif, bmp`, 한도는 `LOGO_FILE_LIMIT_SIZE`이다. 값은 지금 맞다. 상수가 한 곳에 있지 않다 | setting.html:28-29; OrganizationSettingController.kt:86-87, 149 |
| organization-F12 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `/users` 응답의 `info`는 사용자 이름을 이스케이프하지 않고 HTML 문자열에 넣었고, 입력란 목록(`_render`)은 그 문자열을 HTML로 그리므로 이름에 든 마크업이 요소로 삽입되었다. (2026-11-11 수정: 이름과 아이디를 `HtmlUtils.htmlEscape`한다.) | UserApiController.kt:45-47; organization.Member.js:56-61 |
| organization-F13 **[검증: v2 추가 기능의 결함, 미수정(시험이 의도로 고정, 조치 없음)]** | 조직 설정의 OAuth 앱 탭과 앱 정책 탭은 `requiresLogin=true`라 비로그인이 로그인 폼으로 가지만, 설정, 구성원, 삭제, 감사 화면은 비로그인이 (사이트가 열려 있으면) 403 조직 화면을 받는다. 같은 설정 탭 안에서 비로그인 처리가 다르다 | OrganizationOAuthAppsController.kt:41; OrganizationOAuthPolicyController.kt:31; OrganizationSettingController.kt:39 |
| organization-F14 **[검증: 문서 문제]** | 위키 `web/organization.md`가 코드와 어긋난다: "하위 메뉴는 설정, 구성원, 삭제"(코드는 설정, 구성원, 감사, OAuth 앱, OAuth 앱 정책, 삭제 6개), "아직 없는 부분"(이슈, 게시판, 풀 리퀘스트 목록은 이미 있다, 위키 `organization-lists.md`에 있음), 검증 건수. 코드가 맞다 | web/organization.md; partial-settingmenu.html |
| organization-F15 **[검증: 결함 아님]** | 구성원 홈의 구성원 영역은 서버가 거부하는 것이 아니라 화면 모델이 `canSeeMembers`일 때만 템플릿이 그린다. 모델(`org.admins`, `org.members`)은 지연 계산이라 숨겨진 경우 질의도 일어나지 않는다. API로 구성원 목록을 읽는 경로는 이 조사 범위 밖이다 | view.html:88; OrganizationViewContext.kt:86, 98-101 |
| organization-F16 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 설정 저장 성공 후 안내(flash)가 없어 사용자가 값으로만 저장 여부를 확인했다. (2026-11-11 수정: 303에 flash `success` = `organization.setting.saved`를 남긴다.) | OrganizationSettingController.kt:98-99 |
| organization-F26 **[검증: 수정함(A-466, 2026-11-11)]** | 조직 삭제 화면의 인라인 스크립트는 삭제 요청 주소를 `#paths.organization(이름)`(컨텍스트 경로가 없는 경로)으로, 성공 뒤 이동을 `location.href = "/"`로 쓴다. 이 화면의 다른 링크와 달리 컨텍스트 경로를 붙이지 않는다(컨텍스트 경로를 쓰는 배포에서 어긋난다, 추측: 시험 없음) | delete-form.html:45, 47 |
| organization-F27 **[검증: 수정함(A-466, 2026-11-11)]** | 구성원 화면의 역할 항목과 삭제 링크의 `data-href`는 `${#paths.organization(org.name)} + '/member/...'`로 `@{}`를 거치지 않아 컨텍스트 경로가 붙지 않는다. 같은 화면의 폼 `action`, 아바타 `img`, `/users` 자동 완성 주소는 붙는다(추측: 시험 없음) | members.html:41, 43; 비교 members.html:20, 32, 85-91 |

이 영역에 속하는 나머지 발견 사항은 [organization-lists.md](organization-lists.md)(F17 이후)와 [organization-oauth.md](organization-oauth.md)(F20 이후)에 이어서 적었다.

## 11. 시험 근거

조직 화면의 시험은 `src/test/kotlin/dev/yona/web/organization/`에 있다: `OrganizationCreateTest`(9), `OrganizationViewTest`(25), `OrganizationSettingTest`(20), `OrganizationMemberTest`(31), `OrganizationListsTest`(27), `OrganizationBoardTest`(13), `OrganizationOAuthAppsTest`(10), `OrganizationOAuthPolicyTest`(5). 가입 신청은 `EnrollTest`. 스크립트 시험은 `src/test/js/orgmisc.test.mjs`(New, Setting, View, Global)와 `member.test.mjs`(Member). 조직 감사 화면은 `domain/audit/AuditViewTest`. `item-search` 모듈은 `ItemSearchModuleTest`, F8의 사이트 관리자 구성원 조합은 `OrganizationMemberTest`가 확인한다.

## 12. 범위 점검

| 템플릿 | 다룬 곳 |
|---|---|
| `organization/audit.html` | 이 파일 9절 |
| `organization/board-list.html` | [organization-lists.md](organization-lists.md) 2절 |
| `organization/create.html` | 이 파일 4절 |
| `organization/delete-form.html` | 이 파일 8절 |
| `organization/header.html` | 이 파일 2.2절 |
| `organization/issue-list.html` | [organization-lists.md](organization-lists.md) 3절 |
| `organization/list.html` | 이 파일 3절 |
| `organization/members.html` | 이 파일 7절 |
| `organization/menu.html` | 이 파일 2.3절 |
| `organization/oauth-app-form.html` | [organization-oauth.md](organization-oauth.md) 3절 |
| `organization/oauth-app-secret.html` | [organization-oauth.md](organization-oauth.md) 4절 |
| `organization/oauth-apps.html` | [organization-oauth.md](organization-oauth.md) 2절 |
| `organization/oauth-policy.html` | [organization-oauth.md](organization-oauth.md) 5절 |
| `organization/partial-settingmenu.html` | 이 파일 2.4절 |
| `organization/pullrequest-list.html` | [organization-lists.md](organization-lists.md) 4절 |
| `organization/setting.html` | 이 파일 6절 |
| `organization/view.html` | 이 파일 5절 |

17개 중 17개를 읽었다. 함께 읽은 연결 파일: `layout/organization-layout.html`, `error/forbidden-organization.html`, `audit/list.html`, `oauth/partial-apps.html`, `common/scripts.html`, `common/two-column-mode-checkbox-area.html`, `common/select2.html`.

## 갱신 기록 (2026-11-11 재조사)

- 다시 확인한 템플릿 10개(`organization/list`, `create`, `view`, `header`, `menu`, `partial-settingmenu`, `setting`, `members`, `delete-form`, `audit`)와 `layout/organization-layout`, `error/forbidden-organization`, `audit/list`의 인용. 컨트롤러 `OrganizationController`, `OrganizationCreateController`, `OrganizationSettingController`, `OrganizationMemberController`, `OrganizationViewContext`, `OrganizationManagementService`, `UserApiController`, `AuditControllers`, 스크립트 `organization.{Global,New,Setting,Member,View}.js`, `ItemSearch.js`.
- 고친 행과 문장(동작이 바뀐 것): 약 27곳(머리글 이름 링크와 안내 상자, 메뉴 활성 비교와 설명, 설정 탭 링크, 탈퇴 권한 행렬과 `leaveBlocked` 설명, 조직 만들기 권한 행렬과 접근 문장, 만들기 취소 링크, 목록과 홈의 메시지 키 문구, 홈 거르기 입력 행, `G` 표지 행, 설정 저장 규칙 3번과 6번, 구성원 `pattern`과 자동 완성 `info` 이스케이프, 역할 변경 실패 처리, 삭제와 이동 주소의 컨텍스트 경로, `/users` 판정 규칙, `sActionURL`).
- 줄 번호만 고친 곳: 약 45곳(템플릿 header, menu, 컨트롤러 3개, `organization.*.js`, `OrganizationService`, `AccessControl`, `ErrorViews`, `LayoutModelAdvice` 경로 포함).
- 발견 사항: F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F12, F15, F16의 설명을 현재 상태로 고쳤고 `(2026-11-11 수정: ...)`를 붙였다. F11은 줄 번호만. 새 발견 사항: organization-F26(조직 삭제 화면의 컨텍스트 경로), organization-F27(구성원 화면 `data-href`의 컨텍스트 경로). organization-lists.md의 F25는 그 파일에 있다.
- 더한 행: 없음(기존 행의 내용 확장). 없앤 행: 없음.
