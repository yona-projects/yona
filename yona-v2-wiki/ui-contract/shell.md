---
title: 화면 공통부 계약 - 레이아웃, 상단 바, 사용자 패널, 핀 사이드바, 홈과 알림, 오류, 도움말
type: ui-contract
sources:
  - src/main/resources/templates/layout/*.html
  - src/main/resources/templates/common/navbar.html
  - src/main/resources/templates/common/usermenu.html
  - src/main/resources/templates/common/sidebar.html
  - src/main/resources/templates/common/footer.html
  - src/main/resources/templates/common/scripts.html
  - src/main/resources/templates/index/*.html
  - src/main/resources/templates/error/*.html
  - src/main/resources/templates/help/*.html
  - src/main/kotlin/dev/yona/web/layout/LayoutModelAdvice.kt
  - src/main/kotlin/dev/yona/web/home/HomeController.kt
  - src/main/kotlin/dev/yona/web/home/NotificationController.kt
  - src/main/kotlin/dev/yona/web/user/UserMenuController.kt
  - src/main/kotlin/dev/yona/web/user/UserMenuAssembler.kt
  - src/main/kotlin/dev/yona/web/api/FavoriteApiController.kt
  - src/main/kotlin/dev/yona/web/error/ErrorViews.kt
  - src/main/kotlin/dev/yona/web/error/GlobalErrorHandler.kt
  - src/main/kotlin/dev/yona/web/help/HelpController.kt
  - src/main/resources/static/assets/javascripts/esm/Usermenu.js
  - src/main/resources/static/assets/javascripts/esm/ShortcutKey.js
  - src/main/resources/static/assets/javascripts/esm/Request.js
  - src/main/resources/templates/common/my-series-menu-tab.html
  - src/main/kotlin/dev/yona/web/site/SiteMaintenanceController.kt
  - src/main/resources/messages.properties
  - app/assets/stylesheets/scss/_page.scss
  - app/assets/stylesheets/scss/_responsive.scss
  - app/assets/stylesheets/scss/_usermenu.scss
updated: 2026-11-11
---

# 화면 공통부 계약 (shell)

브랜치 `ui-improvements`의 템플릿을 기준으로 정리한 **지금 코드가 하는 일**이다. 새 UI는 이 계약대로만 동작하면 기존과 같다. 코드를 바꾸는 제안은 없다.

이 담당의 산출물은 세 파일이다.

| 파일 | 내용 |
|---|---|
| `shell.md`(이 파일) | 레이아웃 7개, 상단 바, 검색, `+` 메뉴, 사용자 패널, 핀 사이드바, 관리자 띠, 푸터, 홈과 알림(`index/`), 오류 화면(`error/`), 도움말(`help/`), 권한 행렬, 발견 사항 전체, 범위 점검 |
| [`shell-common-fragments.md`](shell-common-fragments.md) | `common/` 조각 30개 중 위 두 줄에 없는 것(편집기, 첨부, 댓글, 페이징 표시, 선택 상자 등)과 **사용처 표** |
| [`shell-scripts.md`](shell-scripts.md) | `yona-layout.js`, `yona-common.js`, `common/*.js`, `esm/*` 공통 스크립트, **전역 단축키 전체표**, 저장소 키, 요청 계약 |

이미 있는 글과 겹치는 부분은 링크만 건다: [`architecture/ui-ia-gitlab.md`](../architecture/ui-ia-gitlab.md)(새 UI의 정보 구조), [`architecture/ui-shortcuts.md`](../architecture/ui-shortcuts.md)(단축키 설계). 두 글과 코드가 어긋난 곳은 이 글 16절과 발견 사항에 적었다.

## 1. 영역 요약

### 1.1 화면과 조각 목록

URL 칸이 `-`이면 다른 템플릿이 포함하는 조각이다. "근거"는 `파일:줄`이다.

| 템플릿 | URL, 메서드 | 그리는 곳 | 용도 |
|---|---|---|---|
| `layout/layout.html` | 모든 일반 화면의 뼈대 | 다른 레이아웃 4개와 `error/notfound-default.html`이 포함 | `<html>` 문서 틀, 관리자 띠, 업데이트 알림, 사용자 패널 스크립트, 핀 로직 |
| `layout/project-layout.html` | - | `project/`, `issue/`, `board/`, `code/`, `pullrequest/`, `milestone/`, `wiki/`, `reviewthread/`, `search/`, `error/`의 프로젝트 안 화면(56개 파일) | 상단 바 + 프로젝트 머리(`project/header`) + 본문 + 푸터 |
| `layout/organization-layout.html` | - | `organization/` 4, `error/forbidden-organization`, `search/` 1 | 상단 바(조직 맥락) + 본문 + 푸터 |
| `layout/site-layout.html` | - | `user/` 28, `organization/` 10, `project/` 3, `site/` 1, `oauth2/` 1, `help/toc`, `index/notifications`, `error/` 4, `issue/` 1, `search/` 1 | 상단 바 + 본문 + 푸터 |
| `layout/site-mng-layout.html` | `/sites/*` 사이트 관리 14개 파일 | `site-layout`에 `SITE_SETTING`으로 올라탄다 | 왼쪽 관리 메뉴 13항목 + 본문 |
| `layout/layout-framed.html` | `GET /user/sidebar` | `UserMenuController.kt:32-47` | 핀 사이드바(왼쪽) + 본문 iframe |
| `layout/migration-layout.html` | 마이그레이션 화면 1개 | `migration/` | 푸터 없는 별도 문서 틀(관리자 띠, 업데이트 알림, 상단 바, `common/scripts`는 가진다) |
| `common/navbar.html` | - | 위 레이아웃들 | 상단 바 |
| `common/usermenu.html` | - | `navbar`, `error/notfound-default` | 사용자 영역(내 이슈, 관리자 렌치, 아바타, `+` 메뉴, 로그인, 가입)과 오른쪽 사용자 패널 |
| `common/sidebar.html` | - | `layout-framed` | 핀 사이드바 내용 |
| `common/footer.html` | - | project, organization, site 레이아웃 | 푸터 |
| `common/scripts.html` | - | `layout`, `layout-framed`, `migration-layout` | 대화상자와 알림 틀, 스크립트 로드, 문서 준비 때 도는 공통 초기화 |
| `index/notifications.html` | `GET /`, `GET /users/login`, `GET /-_-api`, `GET /-_-api/v1/`, `GET /notifications` | `HomeController.kt:37,47` | 익명이면 소개, 로그인이면 환영 표와 알림 목록 |
| `index/partial-notifications.html` | `GET /notification?from&limit` | `NotificationController.kt:24-31` | 알림 목록 조각과 More 단추 |
| `index/partial-intro.html` | - | `index/notifications` | 익명 홈의 사이트 소개 |
| `index/usermenu-tab-content-list.html` | `GET /user/usermenuTabContentList` | `UserMenuController.kt:27-30` | 사용자 패널의 세 탭 내용(Ajax 조각) |
| `index/my-organization-list.html`, `my-project-list.html`, `my-recent-issue-list.html`, `menu-project.html` | - | 위 조각과 `common/sidebar` | 즐겨찾기, 프로젝트, 최근 읽은 이슈 목록 |
| `error/*.html` 9개 | 오류가 난 모든 주소 | `ErrorViews.kt` | 400, 403, 404, 413, 500 화면 |
| `help/toc.html` | `GET /_help` | `HelpController.kt:17-18` | 도움말 질문과 답변 |
| `help/keymap.html`, `help/markdown.html` | - | 화면 4개, 편집기 | 단축키 안내 모달, 마크다운 문법 도움말 |

### 1.2 레이아웃을 정하는 컨트롤러 쪽 값(`LayoutModelFactory`)

`@ControllerAdvice(annotations=[Controller])`가 모든 컨트롤러 화면에 아래 모델 값을 채운다(`LayoutModelAdvice.kt:94-101`). 예외 처리기(`GlobalErrorHandler`)가 그리는 오류 화면에는 어드바이스가 적용되지 않아 `withLayout`이 같은 값을 직접 채운다(`GlobalErrorHandler.kt:95-99`).

| 모델 이름 | 값 | 근거 |
|---|---|---|
| `currentUser` | `ViewerContext`: `anonymous`, `guest`, `siteManager`, `loginId`, `sessionLoginId`(토큰 인증이면 null), `pureName`, `preferredLanguage`, `openIssueCount`, `loginDefaultPage`, `memberOf(org)`, `adminOf(org)`, `avatarUrl(size)` | `ViewerContext.kt:22-93` |
| `site` | `YonaProperties`(사이트 설정 전체): `siteName`, `feedbackUrl`, `hideProjectListing`, `navbarCustomLinkName/Url`, `maxFileSize`, `parsersTextMaxLength` | `LayoutModelAdvice.kt:75` |
| `siteAdminLoggedIn` | 로그인 ID가 문자열 `admin`인지 | `LayoutModelAdvice.kt:76,89` |
| `flashMessages` | 앞 요청이 남긴 값과 이 요청이 심은 값, 번역과 HTML 이스케이프가 끝난 `notices`(목록), `alertTitle`, `alertDescription` | `LayoutModelAdvice.kt:78,103-145`, `FlashDisplayInterceptor.kt` |
| `helper` | `TemplateHelper`(상대 시각, 날짜, 파일 크기 등) | `LayoutModelAdvice.kt:79` |
| `ssoLogin` | 로그인 대화상자의 SSO 단추 목록(`providers`), 전용 모드(`only`) | `LayoutModelAdvice.kt:80`, `SsoLoginOptions.kt` |
| `requestPath` | 요청 경로. 안쪽으로 넘긴(forward) 요청은 처음 경로 | `LayoutModelAdvice.kt:82` |
| `requestUri` | 쿼리 문자열을 포함한 요청 주소 | `LayoutModelAdvice.kt:84` |
| `isMac` | `User-Agent`에 `Macintosh`가 있는지(단축키 안내와 댓글 입력란 안내 글자) | `LayoutModelAdvice.kt:83` |
| `versionToUpdate` | 새 버전이 있으면 그 버전(관리 메뉴 배지) | `LayoutModelAdvice.kt:67` |
| `updateNotice` | 새 버전이 있고 알림을 끄지 않았으며 **사이트 관리자일 때만** `{version, url}` | `LayoutModelAdvice.kt:59-64` |

## 2. 레이아웃 계약

### 2.1 `layout/layout.html` (일반 뼈대)

호출 형식은 `th:replace="~{layout/layout :: layout(제목키, 테마, 본문프래그먼트)}"`이다(`layout.html:9,13`). 래퍼 4개가 이 조각을 감싼다(2.2절).

| 요소 | 계약 | 근거 |
|---|---|---|
| `<html lang>` | `currentUser.preferredLanguage`(저장 언어가 없으면 서버 기본 언어, 익명도 같다) | `layout.html:15`, `ViewerContext.kt:67` |
| 제목 | `#messages.msgOrNull(title) ?: title`(키가 없으면 문자열 그대로). 구분자 `" \|:\| "`(정규식 `' [|]:[|] '`)로 쪼개 `titleArray[0]`이 `<title>`, `og:title`, `twitter:title`이고 마지막 원소가 `og:description`, `twitter:description` | `layout.html:14,19,23-30` |
| `og:url`, `twitter:url` | `requestPath` | `layout.html:24,29` |
| `<head>` 순서 | import map(`common/importmap`)이 모듈 태그보다 앞에 있어야 한다(Firefox는 모듈 뒤의 import map을 무시) -> CSS 9개(bootstrap, yobicon, select2 2개, vanilla-calendar-pro, usermenu, yobi, nprogress, viewerjs) -> `yona-layout.js` -> 인라인 스크립트(핀 로직) | `layout.html:18,32-41`, `importmap.html:8-9` |
| `<body class>` | 테마 인자. 프로젝트 화면은 `prj`, 그 밖은 빈 문자열 | `layout.html:57`, `project-layout.html:12` |
| `div#main.main` | 본문 래퍼. 관리자 띠, 업데이트 알림, 본문, `common/scripts`를 담는다. 사용자 패널은 `#main` 바깥 클릭으로 닫으므로 이 id에 의존한다 | `layout.html:58-66`, `Usermenu.js:40-44` |
| 관리자 띠 | `siteAdminLoggedIn`이면 `.admin-logged-in-affix`에 `user.siteAdminLoggedInAffix` + `.small-font`에 `.maxim`. **로그인 ID가 `admin`이면 보이고 사이트 관리자 권한과는 별개** | `layout.html:59`, `LayoutModelAdvice.kt:76` |
| 업데이트 알림 | `updateNotice != null`이면 `p.center-txt`에 릴리스 링크(`site.update.notification`)와 `button.ybtn.ybtn-small[data-request-method=post][data-request-uri=/sites/unwatchUpdate]`("숨기기") | `layout.html:60-63` |
| 하단 스크립트 | `common/scripts` -> NProgress 최소 0.7 -> 사용자 패널 전역값 6개 -> `esm/Usermenu.js`(모듈) -> viewerjs 2개 -> 인라인(이미지 확대, 핀) -> `common/analytics :: gtag('normal')` | `layout.html:65-119` |

**컨텍스트 경로 전역값**: `common/scripts.html:43`이 `window.yonaContextPath`를 심는다. `@{/}`의 끝 `/`를 뗀 값이라 루트 배포이면 빈 글자이고 `https://서버/yona/` 배포이면 `/yona`이다. 스크립트가 주소를 직접 만들 때 앞에 붙이는 값이다(`Typeahead.js:326`, `CommentAttachmentsUpdate.js:54,122`, `Migration.js:20`). `yona.http`나 `fetch`가 알아서 붙이지는 않는다. 템플릿은 링크마다 `@{...}`나 `@{|/${값}|}`로 접두어를 붙이고, 서버가 만드는 주소(아바타, 로고)도 접두어를 가진다(남은 예외는 F28).

**사용자 패널 전역값 6개**(`layout.html:75-80`, `layout-framed.html:71-76`)는 `Usermenu.js`와 화면 스크립트가 읽는다: `UsermenuToggleFavoriteProjectUrl=/user/favoriteProjects/`, `UsermenuToggleFoveriteOrganizationUrl=/user/favoriteOrganizations/`, `UsermenuGetFoveriteProjectsUrl=/user/favoriteProjects`, `UsermenuToggleFavoriteIssueUrl=/user/favoriteIssues/`, `UsermenuGetFoveriteIssuesUrl=/user/favoriteIssues`(`Usermenu.js`가 부르지 않는다, F19), `UsermenuUrl=/user/usermenuTabContentList`. 이름의 `Foverite` 철자는 v1.6 그대로이다.

**이미지 확대**(`layout.html:86-97`): `.markdown-wrap`마다 `Viewer.setDefaults({transition:false})` 뒤 `$(wrap).viewer()`를 붙이고 `img` 마우스 오버에 `cursor:pointer`를 준다. 마크다운이 문서가 열린 뒤에 그려진 영역은 이 시점 뒤라 대상이 아닐 수 있다(추측, 순서만 읽음).

**핀 로직**은 6절에서 상태표로 정리한다.

#### 2.1.1 반응형과 폭

| 규칙 | 값 | 근거 |
|---|---|---|
| 좁은 화면 기준 | `max-width: 720px` 한 단계. `body`가 세로 배치, `.sidenav`와 `.sidemenu-width`가 `100vw`, `.main-stream` 100%, `#mainFrame`이 `100vh` | `_responsive.scss:1-30` |
| `.hide-in-mobile` | 좁은 화면에서 `display:none !important`. 단 `.sidebar.hide-in-mobile`은 `display:block; position:absolute`로 되살린다 | `_responsive.scss:302-308` |
| `.show-in-mobile-100vh` | 좁은 화면에서 `height:100vh` | `_responsive.scss:298` |
| `.admin-logged-in-affix` | `position:sticky; top:0; z-index:1000`, 좁은 화면에서 `width:auto !important` | `_page.scss:6810-6822`, `_responsive.scss:200` |
| `.gnb-outer` | 높이 40px. 프로젝트와 조직 화면(`project-header`)은 `position:absolute`, 반투명 검정, `z-index:1000` | `_page.scss:109-125` |
| 핀 사이드바 폭 | `.sidebar .nav {width:270px}` + 오른쪽 테두리 1px = 271px, 어두운 배경 `rgb(51,51,51)`, `position:sticky` | `_page.scss:10-22` |
| 사용자 패널 폭 | JS가 `360px`(좁으면 `100vw`)로 연다. CSS의 `.right-menu`는 `flex:0 0 350px`. 초기 `#mySidenav{width:0; position:absolute; top:40px; right:0; z-index:999}` | `Usermenu.js:10,225-234`, `_usermenu.scss:854-896` |

### 2.2 래퍼 레이아웃 4개

| 래퍼 | 인자 | 구성(위에서 아래) | 제목 규칙 | 근거 |
|---|---|---|---|---|
| `projectLayout(title, view, menu, content)` | `view`=ProjectViewContext, `menu`=메뉴 종류 이름 | `common/navbar(menu, view.project, null)` -> `project/header(view)` -> 본문 -> `common/footer` | 제목이 비면 `프로젝트이름 - 소유자`, 있으면 `번역한 제목 - 소유자/프로젝트이름`. 테마 `prj` | `project-layout.html:8-23` |
| `organizationLayout(title, menu, view, content)` | `view`=OrganizationViewContext | `navbar(menu, null, view.organization)` -> 본문 -> `footer` | 인자 그대로 | `organization-layout.html:5-15` |
| `siteLayout(title, menu, content)` | `menu`=MenuType 이름 | `navbar(menu, null, null)` -> 본문 -> `footer` | 인자 그대로 | `site-layout.html:13-23` |
| `siteMngLayout(title, content)` | - | `siteLayout(title, 'SITE_SETTING', ...)` 안에 `.site-breadcrumb-outer`(제목 `site.sidebar`) + `.page-wrap-outer > .site-setting-wrap > .row > col-lg-2(메뉴) + col-lg-10(본문)` | 인자 그대로 | `site-mng-layout.html:10-50` |

### 2.3 사이트 관리 메뉴(`site-mng-layout`)

`ul.site-setting-nav`의 항목 13개. 활성 표시는 `requestPath == 항목 경로`로 **정확히 같을 때만** `li.active`이다(`site-mng-layout.html:25-41`). 하위 경로(예: 상세 화면)에서는 활성이 꺼진다.

| 항목 | 링크 | 메시지 키 | 비고 |
|---|---|---|---|
| 사용자 | `/sites/userList` | `site.sidebar.userList` | |
| 게시물 | `/sites/postList` | `.postList` | |
| 이슈 | `/sites/issueList` | `.issueList` | |
| 프로젝트 | `/sites/projectList` | `.projectList` | |
| 메일 발송 | `/sites/mail` | `.mailSend` | |
| 대량 메일 발송 | `/sites/massmail` | `.massMail` | |
| 업데이트 | `/sites/update` | `.update` | `versionToUpdate != null`이면 `span.notification-badge`에 `1` |
| 시스템 진단 | `/sites/diagnostic` | `.diagnostics` | |
| 감사 로그 | `/sites/audit` | `.audit` | v1.6에 없음 |
| 보안 | `/sites/security` | `.security` | v1.6에 없음 |
| OAuth 앱 | `/sites/oauth-apps` | `.oauthApps` | v1.6에 없음 |
| LFS | `/sites/lfs` | `.lfs` | v1.6에 없음 |
| SSO | `/sites/sso` | `.sso` | v1.6에 없음 |

v1.6 메뉴는 앞 8개이다(`app/views/site/siteMngLayout.scala.html:44-68`). 13개 경로 모두 컨트롤러가 있고 `@SiteManagerOnly`를 받는다(`SiteUserController`, `SiteContentController`, `SiteMailController`, `SiteMaintenanceController`, `SiteSecurityController`, `SiteOAuthAppsController`, `SiteLfsController`, `SiteSsoController`, `AuditControllers.kt:59`). 비관리자는 `error.auth.unauthorized.waringMessage`의 **403 화면**(`SiteManagerOnly.kt:32-35`)이고 **익명도 로그인 폼이 아니라 403**이다(관리자 검사가 `@AnonymousCheck`보다 먼저, `SiteMaintenanceController.kt:19-21`).

### 2.4 `layout/layout-framed.html` (핀 사이드바와 iframe)

`GET /user/sidebar?path=&hash=`(`UserMenuController.kt:32-35`, `@AnonymousCheck`)가 그린다. 모델은 `title`(사이트 이름), `menu`(UserMenuModel), `iframePath`(`hash`가 비어 있지 않으면 `path#hash`, 아니면 `path`)이다(`UserMenuController.kt:43-46`).

| 요소 | 계약 | 근거 |
|---|---|---|
| 문서 | `<body class="framed-body" id="html-body">`, `overflow:hidden`. `<head>`에 `yona.Csrf.js`와 `yona-layout.js` | `layout-framed.html:38-42`, `_page.scss:3-5` |
| `div#sidebar.sidebar.hide-in-mobile` | 익명이 아니면 `common/sidebar`를 넣는다. 맨 아래 `#sidebar-bottom`에 `layout.madeBy`(영어 "Yona, made with love", 한국어 "Yona, 사랑으로 만들었습니다") + 빨간 하트 아이콘(오른쪽 아래 `bottom:8px; right:15px` 고정) | `layout-framed.html:43-56` |
| `div#mainFrame.show-in-mobile-100vh` | `iframe#mainFrameId[name=mainFrame]`의 `src`가 `iframePath`. 같은 div 안에 `common/scripts`가 들어간다 | `layout-framed.html:57-61` |
| 하단 | NProgress 0.7, 툴팁(popover) 초기화, 전역값 6개, `Usermenu.js`, `analytics :: gtag('left_sidebar')` | `layout-framed.html:62-80` |
| 응답 헤더 | `X-Frame-Options: SAMEORIGIN`(iframe이 열리려면 필요). `/oauth2/consent`만 `DENY` | `SecurityConfig.kt:72-74` |
| `path`, `hash` | `hash` 파라미터가 **없으면** `NoSuchElementException`으로 500(v1.6 `None.get` 재현). 빈 값(`hash=`)은 괜찮다. `path`가 없으면 사용자의 `loginDefaultPage`, 없으면 `/notifications` | `UserMenuController.kt:39-42`, 시험 `UserMenuTest.kt:303,311` |
| 익명 | 사이드바 목록 없이 프레임만 | `layout-framed.html:44-46`, 시험 `UserMenuTest.kt:317` |

### 2.5 `layout/migration-layout.html`

`migrationLayout(title, content)`. 마이그레이션 화면 하나(`migration/`)만 쓴다. 일반 레이아웃과 다른 점:

- 푸터와 분석 태그가 없다. 제목은 `th:text="${title}"` 그대로이고 `og`, `twitter` 메타가 없다(`migration-layout.html:13-19`).
- 본문은 `div#main.main` 안에 있고 일반 레이아웃처럼 관리자 띠(`:48`), 업데이트 알림(`:49-52`, 같은 "숨기기" 단추), 상단 바(`navbar('SITE_HOME', null, null)`, `:53`), `common/scripts`(`:57`)를 가진다(`:47-58`).
- **구글 글꼴 3종**(Montserrat, Indie Flower, Muli)을 외부 주소에서 불러온다(`:21-23`). 폐쇄망에서는 실패해도 화면은 열린다(추측).
- 머리 주석(`:7`)은 "jquery 1.9.0"이라 하지만 실제로는 `jquery-3.7.1.js`를 불러온다(`:39`). `vue.global.prod.js`와 `esm/service/Migration.js`(모듈)를 head에서 불러온다(`:41-42`).
- `yona-layout.js` 번들을 쓰지 않고 NProgress, jQuery, `yona.Common.js`를 낱개로 불러온다(`:33-40`). `yona.Htmx.js`와 htmx는 없다.
- **`Usermenu.js`와 전역값 6개가 없다.** 대신 인라인 스크립트(`:60-186`)가 패널 열기와 닫기, 별 토글을 자체 구현한다(패널 폭 **420px**, `Usermenu.js`는 360px). 사용자 패널 본문(`#usermenu-tab-content-list`)을 채우는 코드가 없어 **패널은 항상 "Loading..."**이다(F9). 별 토글 실패 알림은 `"Update failed: " + $yona.escapeHtml(JSON.parse(data.responseText).reason)`로 영문 고정이고 `JSON.parse`를 감싸지 않는다(`:151,167`, `Usermenu.js`의 `failureReason`과 다르다). v1.6도 `Usermenu.js`를 이 화면에서 부르지 않았다(`app/views/migration/migrationPageLayout.scala.html` 전체에 `Usermenu` 없음).

### 2.6 레이아웃을 쓰지 않는 문서

`welcome/secret.html`, `welcome/restart.html`(첫 설정과 재시작 안내)은 `layout/`을 쓰지 않는 독립 문서이다(담당 밖, 확인만).

## 3. 상단 바 (`common/navbar.html`)

`navbar(menu, project, org)`: `menu`는 MenuType 이름, `project`와 `org`는 맥락이다. 둘 중 하나가 있으면 `header.gnb-outer.project-header`(반투명, 프로젝트 머리 위에 겹침)가 된다(`navbar.html:19`). 검색 주소 `searchLink`는 프로젝트 맥락이면 `/{owner}/{project}/search`, 조직이면 `/organizations/{name}/search`, 아니면 `/search`이다(`navbar.html:10-11`).

IE 안내 상자(`.unsupported.hidden`)는 항상 그려지지만 내용 주석이 처리되어 있고, `common/scripts`가 `MSIE` 문자열을 만나면 `.unsupported`를 보인다(`navbar.html:12-18`, `scripts.html:127-129`).

### 3.1 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| `.pin`(파란 화살표, 툴팁 `layout.sidebar.tooltip`: 영어 "Sidebar", 한국어 "사이드바") | 단추(div) | 항상 그려지나 화살표 아이콘은 CSS로 숨김. 최상위 창에서만 JS가 **오른쪽 화살표**를 보인다 | 누르면 핀 켜기(6절) | 없음(주소 이동만) | `GET /user/sidebar?path=<현재 경로와 쿼리 문자열을 encodeURIComponent로 인코딩한 값>&hash=<해시>`로 이동 | 로그인(익명이면 서버가 프레임만 그림) | `navbar.html:21`, `layout.html:99-111` |
| 로고 `Y` | 링크 | 항상 | `/` 이동. 진행 막대 시작 | `GET /` | 홈(로그인 기본 페이지가 있으면 303) | 없음 | `navbar.html:24`, `scripts.html:147-150` |
| "전체 목록" | 링크 `.show-progress-bar` | `!site.hideProjectListing and !currentUser.guest`. 활성은 `menu == 'PROJECTS'` | `/projects` 이동 | `GET /projects` | 목록 화면 | 서버는 `hideProjectListing`이면 **누구에게나 403**(사이트 관리자 포함), 게스트는 홈으로(`@GuestProhibit`) | `navbar.html:26-31`, `ProjectListController.kt:47-56` |
| "개발팀에게 문의하기" | 링크(새 탭) | `site.feedbackUrl`이 비어 있지 않을 때. 기본값 `https://github.com/yona-projects/yona/issues` | 외부 이동 | 없음 | | 없음 | `navbar.html:32-34`, `YonaProperties.kt:80` |
| 검색 범위 단추 `#gnb-search-scope-title` | 드롭다운 | `project != null or org != null` | 범위 목록 열기. 현재 범위 이름이 글자로 보인다(프로젝트면 "현재 프로젝트", 조직이면 "현재 그룹") | 없음 | | 없음 | `navbar.html:38-42` |
| 범위 항목 | 링크 `[data-toggle=search-scope][data-action]` | 아래 표 | 누르면 폼 `action`을 `data-action`으로 바꾸고 단추 글자를 항목 글자로 바꾼다. 이동은 하지 않는다 | 없음 | 폼 제출 때 그 주소로 간다 | 없음 | `navbar.html:43-66`, `scripts.html:140-143` |
| 검색 폼 `form[name=gnb-search-form]` | 폼(GET) | 항상 | 제출하면 `action?searchType=auto&keyword=` | `GET {searchLink}?searchType=auto&keyword=` | 검색 결과 화면 | `/search`와 `/{owner}/{project}/search`, `/organizations/{org}/search` 모두 `@AnonymousCheck`(`SearchController.kt:35,48,61,81`). 조직 검색은 `hideProjectListing`이면 조직 구성원이나 관리자만(`SearchController.kt:69`) | `navbar.html:36-72` |
| 검색 입력 `input[name=keyword]` | 입력 | 항상 | `accesskey="S"`: 브라우저 단축키(Windows와 Linux의 `ALT+S`, Mac은 `CTRL+ALT+S`)로 초점. JS가 아니다 | 없음 | | 없음 | `navbar.html:69`, [`ui-shortcuts.md`](../architecture/ui-shortcuts.md) 10절 |
| 검색 단추 | 제출 | 항상 | 폼 제출 | 위와 같음 | | 없음 | `navbar.html:70` |

**범위 항목 표시 조건**(`navbar.html:44-65`)

| 항목 | 글자 | 보이는 조건 | `data-action` |
|---|---|---|---|
| 프로젝트 | 현재 프로젝트 | `project != null` | `{projectBase}/search` |
| 그룹 | 현재 그룹 | `project != null and project.hasGroup()`. 조직 맥락이면 `site.hideProjectListing or currentUser.guest`이고 `memberOf(org) or adminOf(org)`일 때 | 프로젝트면 `/organizations/{owner}/search`, 조직이면 `{orgBase}/search` |
| 전체 | 모든 프로젝트 | `(!site.hideProjectListing and !currentUser.guest) or currentUser.siteManager` | `/search` |

조직 맥락에서 평소 사용자(목록 공개, 게스트 아님)는 목록에 "전체"만 있고 "현재 그룹"이 없다(기본 `action`이 이미 조직 검색이라서, v1.6 그대로, `navbar.scala.html` 대조). 프로젝트도 조직도 아니면 범위 단추 자체가 없고 입력란에 `select` 클래스가 붙지 않는다(`navbar.html:68`).

## 4. 사용자 영역 (`common/usermenu.html`)

`usermenu`는 **두 덩어리**를 그린다: 오른쪽 사용자 패널 `#mySidenav`(5절)와 상단 바 오른쪽 `ul.gnb-usermenu`(`usermenu.html:9-36,38-102`). `error/notfound-default`도 이 조각을 쓴다.

### 4.1 `ul.gnb-usermenu` 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 사용자 정의 링크 | 링크 | 로그인, `site.navbarCustomLinkName`이 비어 있지 않음(공백뿐이면 안 그림) | `site.navbarCustomLinkUrl`로 이동 | 없음 | | 없음 | `usermenu.html:40-42`, 시험 `NavbarCustomLinkBlankTest.kt:33` |
| "내 이슈"(+ 열린 이슈 수 배지) | 링크, 툴팁 `title.shortcut (A)` | 로그인. 배지는 `openIssueCount > 0` | `/user/issues` 이동 | `GET /user/issues` | 내 이슈 화면 | `@AnonymousCheck(requiresLogin=true)`: 익명이면 로그인 폼으로 | `usermenu.html:43-45`, `MyIssuesController.kt:34,46` |
| 사이트 관리 렌치 | 링크 `.usermenu-icon-button.show-progress-bar`, 툴팁 `menu.siteAdmin` | `currentUser.siteManager` | `/sites/userList` 이동 | `GET /sites/userList` | 사이트 관리 | `@SiteManagerOnly`: 아니면 403 화면 | `usermenu.html:47-54`, `SiteUserController.kt:58` |
| 아바타 `#sidebar-open-btn` | 단추(`li.gnb-usermenu-dropdown`), 툴팁 "사용자 메뉴, 단축키 (F)" | 로그인 | **사용자 패널 열기/닫기**(5절) | `GET /user/usermenuTabContentList`(처음 한 번) | 패널 | 없음(익명이면 패널 목록이 비어 있음) | `usermenu.html:55-62`, `Usermenu.js:46-54` |
| `+` 드롭다운 `.dropdwon-box-btn` | 드롭다운 | 로그인 | 메뉴 열기(Bootstrap `data-bs-toggle=dropdown`, 위치는 정적 배치) | 없음 | | 없음 | `usermenu.html:63-89`, `scripts.html:47` |
| `+` 안 "새 이슈" | 링크 | 로그인 | 이동 | `GET /user/issues/new` | 최근 방문 프로젝트에 새 이슈 폼. 방문한 프로젝트가 없으면 안내와 함께 첫 화면 | `@AnonymousCheck`(클래스) | `usermenu.html:69-72`, `IssueCreateController.kt:78` |
| `+` 안 "새 이슈 - 개인 inbox" | 링크 | 로그인 | 이동 | `GET /user/issues/new/mine` | 내 `inbox`, 없으면 `_private` 등 | 위와 같음 | `usermenu.html:73-76`, `IssueCreateController.kt:94` |
| `+` 안 구분선 | `hr` | | | | | | `usermenu.html:77-79` |
| `+` 안 "새 프로젝트 만들기" | 링크 | 로그인 | 이동 | `GET /projectform` | 프로젝트 만들기 폼 | `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)` | `usermenu.html:80-83`, `ProjectCreateController.kt:53-54` |
| `+` 안 "새 그룹 만들기" | 링크 | 로그인 and `!guest` | 이동 | `GET /organizations/new` | 그룹 만들기 폼 | `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`와 `@GuestProhibit`(폼 화면부터 막는다. POST도 같다. v1.6은 POST만 막았다, organization-F7) | `usermenu.html:84-87`, `OrganizationCreateController.kt:33-36` |
| "로그인" `#required-logged-in a[data-login=required]` | 링크 | 익명 | 로그인 대화상자를 연다(기본 이동은 막는다). 대화상자 스크립트가 없는 화면(`/users/` 아래)에서는 링크 그대로 `/users/loginform` 이동 | `GET /users/loginform` | 대화상자 또는 로그인 폼 | 없음 | `usermenu.html:92-95`, `LoginDialog.js:33-35,62-78` |
| "멤버 가입" | 링크 `.ybtn.ybtn-success` | 익명 | 이동 | `GET /users/signupform` | 가입 폼 | 없음 | `usermenu.html:97-100`, `SignupController.kt:38` |

**익명에게 보이지 않는 것**: 내 이슈, 렌치, 아바타, `+`. `+` 메뉴 항목 4개는 `th:if`가 `!anonymous`인 블록 안에 있어 익명에게는 통째로 없다(`usermenu.html:39,90`). 서버도 같은 주소를 익명에게 로그인 폼으로 보낸다.

**게스트(`isGuest`)**: 상단 "전체 목록"이 숨고 `+`의 "새 그룹 만들기"가 숨는다. 렌치와 아바타는 일반 로그인 사용자와 같다.

## 5. 사용자 팝업 패널 `#mySidenav` (+ 최근 읽은 이슈 등 목록)

### 5.1 구성

`div#mySidenav.sidenav.row` 안에 `.right-menu`가 있고(`usermenu.html:9-10`) 위에서 아래로:

1. `.user-menu-wrap`: "프로필 페이지"(`/` + 로그인 ID, 익명이면 `/`), "사용자 설정"(`/user/editform`), "로그아웃"(`/users/logout`, 배지 `bg-secondary`)(`usermenu.html:11-15`).
2. 탭 `ul.nav.nav-tabs`: "즐겨찾기"(`#myOrganizationList`, 기본 활성), "프로젝트"(`#myProjectList`), "최근 읽은 이슈"(`#myRecentIssueList`)(`:16-29`).
3. `#usermenu-tab-content-list`: 처음에는 글자 `Loading...`. 스크립트가 `GET /user/usermenuTabContentList`를 읽어 통째로 바꾼다(`:30-34`, `Usermenu.js:12-27`). 읽지 못하면 `Loading...`이 그대로 남는다.

### 5.2 열고 닫기와 동작

| 요소/이벤트 | 조건 | 동작 | 요청 | 근거 |
|---|---|---|---|---|
| `#sidebar-open-btn` 클릭 | 목록 읽기가 **끝난 뒤**(성공이든 실패든) | 폭이 0이 아니면 닫고, 0이면 연다(`viewSize > 720`이면 360px, 아니면 `100vw`, 테두리 `1px solid #ccc`). 열 때 `.main-stream`을 `col-lg-12 -> col-lg-8`로 바꾸고 `.search-input`에 초점을 주고 별 표시를 다시 맞춘다 | 열 때 `GET /user/favoriteProjects` | `Usermenu.js:46-54,220-248` |
| 목록 읽기(`GET /user/usermenuTabContentList`) 실패 | `.gnb-usermenu-dropdown`이 있을 때 | 콘솔에 `Usermenu loading failed: ...`만 남기고 `iniNaviUserMenu()`는 그대로 부른다. 아바타 단추, 바깥 클릭 닫기, `F` 키가 걸리고 패널 본문은 `Loading...`으로 남는다 | `GET /user/usermenuTabContentList` | `Usermenu.js:12-22` |
| `#main` 클릭 | 패널 폭이 0이 아니고 클릭 대상이 `#mySidenav` 밖 | 패널 닫기 | 없음 | `Usermenu.js:40-44` |
| `F`, `ㄹ` (`keypress`) | 포커스가 어디에도 없고(`$(':focus').length === 0`), `metaKey`가 없고, `key`가 소문자 `f` 또는 `ㄹ` | 패널 열기와 `preventDefault` | `GET /user/favoriteProjects` | `Usermenu.js:32-38,214-218` |
| 검색란 `.search-input` `keyup` | 값이 비지 않았거나 Backspace | `.user-li`와 `.org-li`를 값이 글자에 포함된(대소문자 무시) 것만 보인다 | 없음 | `Usermenu.js:88-101` |
| 검색란 `Esc` | | 프로젝트 검색란 blur, 패널 닫기 | 없음 | `Usermenu.js:102-107` |
| 즐겨찾기 탭 클릭 | | 200ms 뒤 조직 검색란에 초점(넓은 화면), 프로젝트 검색란 값을 조직 검색란으로 옮기고 비운다 | 없음 | `Usermenu.js:75-85` |
| 프로젝트 탭과 하위 탭(`#recentlyVisited`, `#createdByMe`, `#watching`, `#joinmember`) 클릭 | | 별 표시 다시 맞춤, 200ms 뒤 프로젝트 검색란에 초점, 조직 검색란 값을 옮긴다 | `GET /user/favoriteProjects` | `Usermenu.js:59-73` |
| `.project-list > .star-project` 클릭 | 로그인 | 별 토글. `starred` 클래스를 응답 `favored`에 맞춘다. 실패하면 알림창 `common.updateFailed`(영어 "Update failed: {0}", 한국어 "변경하지 못했습니다: {0}")에 이유를 `$yona.escapeHtml`로 감싸 보인다. 이유는 `failureReason`이 정한다: 응답 본문이 JSON이면 `reason`, JSON이 아니고 200자 이하이며 `<`가 없으면 본문 글자(400 `Wrong project id` 등), 그 밖(HTML, 긴 본문, 본문 없는 403)이면 `statusText`나 상태 코드 | `POST /user/favoriteProjects/{projectId}` | `Usermenu.js:109-122,196-212`, `FavoriteApiController.kt:63-71` |
| `.org-list > .star-org` 클릭 | 로그인 | 조직 별 토글 | `POST /user/favoriteOrganizations/{organizationId}` | `Usermenu.js:166-179`, `FavoriteApiController.kt:86-94` |
| `.favorite-issue` 클릭 | 이슈 보기 화면의 별(`issue/view.html:26`) | 이슈 즐겨찾기 토글, 알림으로 서버 메시지(`issue.favorite.added/deleted`). 실패 알림은 별 토글과 같다 | `POST /user/favoriteIssues/{issueId}` | `Usermenu.js:124-139`, `FavoriteApiController.kt:36-46` |
| `.user-ul > .user-li`, `.project-ul > .user-li` 클릭 | | `data-location`(프로젝트 `/{owner}/{name}/go`, 이슈 주소)로 이동. `ctrl`, `meta`, `shift`가 눌리면 **같은 창**. 아니면 이름이 `mainFrame`이 아닌 창에서 `#mainFrame`이 있으면 `mainFrame`(핀 켠 상태), **없으면 새 탭(`_blank`)**. 클릭한 항목에 `selected`를 옮긴다 | `GET .../go` 등 | `Usermenu.js:141-164` |
| `.all-orgs` 클릭 | | 같은 `li` 안의 `.hide` 항목(즐겨찾기가 아닌 프로젝트)을 `toggle("fast")` | 없음 | `Usermenu.js:182-186` |
| `.sub-project-counter` | 목록 읽은 뒤 | 조직 묶음 안 `.project-ul > .user-li`의 개수를 글자로 쓴다 | 없음 | `Usermenu.js:188-193` |

`.project-breadcrumb > .user-project-list`(프로젝트 머리의 별, `project/header.html:17`)도 같은 별 처리기가 받는다(`Usermenu.js:109`). 이 때문에 프로젝트 화면의 별 단추는 `Usermenu.js`와 **레이아웃이 심는 전역값**에 의존한다.

중복 바인딩은 `off(...).on(...)`로 막고(v1.6의 누적 결함 수정, 시험 `usermenu.test.mjs:114`), `afterUsermenuLoaded()`를 문서 준비 때 한 번(`Usermenu.js:29`), 목록을 읽은 뒤 한 번 더(`:25`) 부른다.

### 5.3 목록 조각 계약 (`index/usermenu-tab-content-list`, `my-*-list`)

`GET /user/usermenuTabContentList`(`UserMenuController.kt:143`): 레이아웃 없이 세 `div.tab-pane`(`#myOrganizationList`(`active`), `#myProjectList`, `#myRecentIssueList`)을 돌려준다(`usermenu-tab-content-list.html:9-19`). 익명도 오류 없이 빈 내용을 받는다(시험 `UserMenuTest.kt:281`, 어노테이션 없음).

| 탭 | 조각 | 내용 | 빈 상태 | 근거 |
|---|---|---|---|---|
| 즐겨찾기 | `my-organization-list` | 검색란(`input.search-input.org-search`, 자리표시 `title.type.name`), 내 프로젝트 묶음(`menu.loginId`로 이름을 단 `li.org-li`, 읽을 수 있는 내 프로젝트), 조직 묶음(`menu.organizations`: 조직 즐겨찾기가 먼저, 즐겨찾기한 프로젝트가 속한 다른 조직이 뒤. 조직 관리자와 사이트 관리자에게만 프로젝트 수 표시), 그 밖 즐겨찾기 프로젝트(`etcFavorites`, 소유자 링크 포함) | `menu.noOrganizations`면 `div.no-result`에 "결과 없음"(`title.no.results`) | `my-organization-list.html:10-57`, `UserMenuAssembler.kt:29-62` |
| 프로젝트 | `my-project-list` | 검색란 `.project-search`(`id="query"`), 하위 탭 4개(최근 방문(기본), 내가 만든, 지켜보는, 참여 중인), 각 `projectPane` | 목록이 비면 `div.no-result`("결과 없음") | `my-project-list.html:9-41` |
| 최근 읽은 이슈 | `my-recent-issue-list` | 검색란 `.project-search`(`id="query"`, 위와 중복 id, F11), `ul#recentlyVisitedIssues`의 `li.user-li[data-location=이슈 주소]` + 제목 + 팝오버(`프로젝트 #번호`) | `div.no-result`("결과 없음") | `my-recent-issue-list.html:9-34` |
| 프로젝트 한 줄 `allProject`, `myProject` | `menu-project` | `li.user-li[data-location=/{owner}/{name}/go]`: 로고(없으면 25px 빈칸), 이름, 비공개 자물쇠, 별(`.star-project[data-project-id]`, 글자 `star`는 Material Icons 글리프). `allProject`는 즐겨찾기가 아니면 `hide`(검색하면 나타남)이고 소유자 링크가 없고 팝오버로 프로젝트 설명, `myProject`는 소유자 링크(`'/' + owner`) | | `menu-project.html:10-46` |

읽기 권한이 없는 프로젝트는 즐겨찾기 묶음에 나오지 않는다(`UserMenuAssembler.kt:44,69,73`, 시험 `UserMenuTest.kt:271`). 최근 읽은 이슈의 표시 이름은 주소 끝에서 `프로젝트/종류/번호`를 읽어 `프로젝트 #번호`로 만들고(컨텍스트 경로가 붙어도 어긋나지 않는다) 주소가 짧으면 주소 그대로이다(`UserMenuAssembler.kt:53-60`). 프로젝트 로고 주소는 `/files/<id>`에 접두어를 붙여 그린다(`menu-project.html:15,34`).

### 5.4 즐겨찾기 API 계약 (`FavoriteApiController`)

| 메서드 경로 | 바디 | 응답 | 오류 | 화면이 부르는지 |
|---|---|---|---|---|
| `POST /user/favoriteProjects/{projectId}` | 없음 | `{projectId, favored}` | 익명 403(본문 없음), 숫자가 아니면 400 `Wrong project id`, 없는 프로젝트 400 | 예(`Usermenu.js:116`) |
| `GET /user/favoriteProjects` | | `{projectIds:[], projects:[{projectId, projectName, owner}]}`. 익명은 빈 목록 | | 예(`Usermenu.js:240`, `projectIds`만 읽음. 실패하면 별 표시를 그대로 둔다) |
| `POST /user/favoriteOrganizations/{organizationId}` | 없음 | `{organizationId, favored}` | 익명 403, 400 `Wrong organization id` | 예 |
| `GET /user/favoriteOrganizations` | | `{organizationIds, organizations}`. 익명은 빈 목록 | | **아니오**(F19) |
| `POST /user/favoriteIssues/{issueId}` | 없음 | `{issueId, favored, message}`(메시지는 요청 언어) | 익명 403, 400 `Wrong issue id` | 예(`Usermenu.js:131`) |
| `GET /user/favoriteIssues` | | `{projectIds:[이슈 id], projects:[{issueId, issueTitle, issueAuthorName}]}` | **익명은 NPE로 500**(v1.6을 공식 v1.16.0으로 확인해 재현) | **아니오**(F19) |

모두 `@ResponseBody`이고 CSRF는 `X-XSRF-TOKEN` 헤더(스크립트가 `yona.Csrf.js`로 자동)이다. 응답 실패 때 화면은 `failureReason`으로 본문 글자나 상태 문구를 골라 알림창에 보인다(5.2절, F20). 서버가 주는 오류 본문(400 `Wrong ... id`)은 JSON이 아닌 평문이다.

## 6. 핀 사이드바(`common/sidebar`)와 iframe

### 6.1 사이드바 내용 (`common/sidebar.html`)

| 요소 | 조건 | 동작 | 요청 | 근거 |
|---|---|---|---|---|
| 사용자 링크 `a[target=mainFrame]`(아바타 + 이름, 좁으면 이름 숨김) | 로그인 | iframe에 `/` + 로그인 ID(`@{\|/${loginId}\|}`, 접두어 포함). 아바타 주소도 `@{...}`로 접두어를 붙인다 | `GET /{loginId}` | `sidebar.html:11-16` |
| "사용자 설정" `a[target=mainFrame]` | | iframe에 열기 | `GET /user/editform` | `sidebar.html:17` |
| "로그아웃" | | **target이 없어 최상위 창**이 이동 | `GET /users/logout` | `sidebar.html:18` |
| `.pin-in-sidebar`(왼쪽 화살표, 툴팁 `layout.sidebar.tooltip`) | | `localStorage.shallWeOpenLeftNavigation="false"` 저장 후 `window.location = window.location.href`(`/user/sidebar?...` 자신을 다시 연다) | 없음 | `sidebar.html:19,77-81` |
| 탭 3개 | | 누르면 `localStorage.sidebarActiveMenu`에 `myOrganizationList`, `myProjectList`, `myRecentIssueList` 저장. 첫 두 탭은 `.sidebar-bottom`(`layout.madeBy` 문구)을 숨기고 세 번째는 보인다 | 없음 | `sidebar.html:21-30,48-75` |
| 새로고침 `.refresh-button` | | `window.location.reload()` | `GET /user/sidebar?...` | `sidebar.html:31-33,59-61` |
| 목록 3개 | | 서버가 처음부터 그려 둔다(5.3절 조각 재사용). 활성 탭은 `sidebarActiveMenu`(없으면 즐겨찾기) | 없음 | `sidebar.html:35-47,49-57` |

핀 사이드바 안의 목록 클릭, 별, 검색 필터는 **같은 `Usermenu.js`**(5.2절)가 처리한다. 이 문서에는 `#mySidenav`가 없어 사용자 패널 코드는 목록을 읽지 않고(`.gnb-usermenu-dropdown`이 없음, `Usermenu.js:12`) 처리기만 한 번 붙는다(`Usermenu.js:29`). 항목 클릭은 `#mainFrame`이 있으므로 `window.open(location, 'mainFrame')`이다.

### 6.2 핀 상태 전이

`localStorage.shallWeOpenLeftNavigation`(문자열 `"true"` 또는 `"false"`)과 창 구조(`window.parent === window`)가 상태이다. 근거는 `layout.html:42-55,86-117`.

| 현재 | 사건 | 결과 |
|---|---|---|
| 최상위 창(핀 없음), 저장값이 `"true"`, 로그인 | 일반 화면을 열 때(`<head>` 인라인 스크립트) | 곧바로 `/user/sidebar?path=<pathname + 쿼리 문자열을 encodeURIComponent로 인코딩한 값>&hash=<해시>`로 이동한다. **모든 화면 이동이 이중 로드**가 된다(F6) |
| 최상위 창, `.pin`이 보임 | `.pin` 클릭 | 저장값 `"true"`, 같은 이동 |
| 프레임 안의 화면 | `window.parent.document.title`과 `history.replaceState`로 부모 제목과 주소를 현재 화면에 맞춘다 | 주소 표시줄이 프레임 안 주소를 따라간다 |
| 프레임 안의 화면 | `.pin` 클릭(프레임 안에서는 `.pin`이 CSS로 숨어 있어 사용자가 누를 수 없음) | 저장값 `"false"`, `window.parent.location = window.location.href`(프레임을 풀어 일반 화면으로) |
| 핀 사이드바 문서 | `.pin-in-sidebar` 클릭 | 저장값 `"false"` 후 `/user/sidebar?...`를 다시 연다. 다음 일반 화면부터 핀이 풀리지만 이 문서는 그대로 남는다 |
| 프레임 안의 화면 | 부모의 `#sidebar` 표시가 `block`이면 | `.pin > i`의 화살표 클래스를 right에서 left로 바꾼다(그러나 CSS가 두 화살표를 숨겨 보이지 않는다) |
| 프레임 안의 화면 | `a.ago`, `.head-anchor`, `.share-link` 클릭 | 부모 `history`에 `pushState`나 `replaceState`로 이 링크 주소를 남겨 주소를 맞춘다(`scripts.html:194-204`) |

`pin` 이동 주소의 `path`는 `window.location.pathname + window.location.search`를 `encodeURIComponent`로 인코딩한 값이라 **쿼리 문자열이 유지된다**(`layout.html:50,106`; v1.6은 `pathname`만 써서 쿼리가 사라졌다 `layout.scala.html:47,102`, F6). `hash`는 `#`만 뗀 값이다. 로그인하지 않은 사용자는 자동 이동을 하지 않는다(`![(${currentUser.anonymous})]`, 시험 `LayoutParityTest.kt:57`).

## 7. 관리자 띠, 업데이트 알림, 푸터

| 요소 | 조건 | 동작 | 요청 | 권한 | 근거 |
|---|---|---|---|---|---|
| 관리자 띠 "관리자로 로그인 하였습니다! - 큰 힘에는 큰 책임이 따릅니다" | 로그인 ID가 `admin` | 표시만. 스크롤해도 화면 위에 붙어 있다. 홈 화면 스크립트는 띠가 하나 있으면 `.sidenav`의 `top`을 `84px`로 내린다 | 없음 | 없음 | `layout.html:59`, `notifications.html:118-120` |
| "업데이트 알림: Yona {0} 버전이 나왔습니다" 링크 | 사이트 관리자이고 새 버전이 있고 알림을 끄지 않음 | 릴리스 페이지로 이동 | 없음 | | `layout.html:60-61`, `LayoutModelAdvice.kt:59-64` |
| "숨기기" 단추 | 위와 같음 | `yona.Request`가 POST를 보내고 성공(200)이면 **화면을 새로 고친다**. 실패하면(2xx가 아님, 연결 실패) `request:error`를 막은 처리기가 없으므로 기본 알림창을 띄운다: 403 `error.forbidden`, 404 `error.notfound`, 그 밖과 연결 실패 `error.internalServerError`의 번역(`Request.js:69-81,100-120`) | `POST /sites/unwatchUpdate`(바디 없음, CSRF 헤더 자동) | `@SiteManagerOnly`: 비관리자 403. 서버 전체에 적용되고 서버를 다시 시작해도 꺼진 채이다. 사이트 관리의 업데이트 화면이 `POST /sites/watchUpdate`(303, `/sites/update`)로 다시 켠다(site-F6) | `layout.html:62`, `Request.js`, `SiteMaintenanceController.kt:54-66`, 시험 `SiteUpdateAndDiagnosticTest.kt` |
| 푸터 | project, organization, site 레이아웃만 | `footer.page-footer-outer > .page-footer > span.provider`: "Copyright [Yona authors](github AUTHORS) & © [NAVER Corp.] & [NAVER LABS] Supported by [NAVER CLOUD PLATFORM]". 모두 새 탭 외부 링크, 영문 고정(메시지 키 없음, v1.6 그대로) | 없음 | 없음 | `footer.html:8-15` |

푸터가 **없는** 문서: `layout-framed`(내용은 iframe 안 화면의 푸터), `migration-layout`, `error/notfound-default`(자체 푸터, 11절).

## 8. 홈과 알림 (`index/notifications.html`, `partial-notifications.html`, `partial-intro.html`)

### 8.1 화면 개요

| 항목 | 내용 |
|---|---|
| 컨트롤러 | `HomeController.index`(`GET /`, `/users/login`, `/-_-api`, `/-_-api/v1/`, `HomeController.kt:37`), `notifications`(`GET /notifications`, `:47`). 클래스에 `@AnonymousCheck` |
| 홈 전용 분기 | `loginDefaultPage`가 비어 있지 않으면 `RedirectSupport.see(...)`로 이동(303). `/notifications`는 이동하지 않는다. 다른 주소로 들어와도 기본 로그인 페이지가 있으면 그리로 보낸다(시험 `HomePageTest.kt:161,244,254`) |
| 모델 | `notifications`(최대 20개 `NotificationItem`), `menuTab="TAB_NOTI"` |
| 레이아웃 | `siteLayout(site.siteName, 'SITE_HOME', ::notificationsPage)` |
| 익명 | `index/partial-intro`(소개)만. 알림 영역과 스크립트 대상이 없다 |
| 로그인 | `.page-wrap-outer > .page-wrap`: ①`.site-guide-outer`(환영 표, 기본 `hide`) ②`#toggleIntro` 접기 단추 ③`.page.on-fold-intro > .row.content-container`의 `col-lg-8.main-stream`(내 활동 탭 + 알림 목록)과 비어 있는 `col-lg-4.index-menu.right-menu` |

### 8.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 환영 표의 "새 프로젝트 만들기", "새 그룹 만들기", "전체 목록" | 링크 `.ybtn-success` | 로그인, 환영 표가 펼쳐진 때 | 이동 | `GET /projectform`, `/organizations/new`, `/projects` | | 4.1절과 같음. "전체 목록" 링크는 `hideProjectListing`이어도 그려진다(서버가 403) | `notifications.html:25-45` |
| `#toggleIntro`(`yobicon-resizev`) | 단추 | 로그인 | `.site-guide-outer`의 `hide`를 토글하고 `localStorage["yobi-intro"]`에 "보이는지"(`true`/`false`)를 쓴다. 문서가 열릴 때 저장값이 `"false"`가 **아니면** 펼친다(처음에는 펼침) | 없음 | | 없음 | `notifications.html:47-49,66-75` |
| 내 활동 탭 3개(`common/my-series-menu-tab`) | 링크 | 로그인 | "알림"(`/notifications`, 활성), "내 이슈"(`/user/issues`), "내 파일"(`/user/files`) | `GET` | | `/user/files`는 클래스에 `@AnonymousCheck`(로그인을 요구하지 않는다). 익명은 오류 없이 빈 목록을 본다 | `my-series-menu-tab.html:10-12`, `UserFilesController.kt:23,40` |
| `#setDefaultLoginPage` "기본 페이지로 지정" | 단추(`hide-in-mobile`), 팝오버 `button.setDefaultLoginPage.desc` | `viewType`이 알림 또는 내 이슈이고 `requestPath != '/'`이고 `requestPath.substring(1) != currentUser.loginDefaultPage` | 클릭하면 POST. 성공이면 알림 `common.defaultPage.set`(영어 "Set to default: {0}", 한국어 "기본 화면으로 설정했습니다: {0}", 3초)에 응답 `defaultLoginPage`를 `$yona.escapeHtml`로 감싸 넣고 단추를 숨긴다. 실패면 알림창 `common.defaultPage.failed`(영어 "Failed to set the default page: {0}", 한국어 "기본 화면을 설정하지 못했습니다: {0}")에 `responseText`(없으면 `statusText`, 없으면 빈 글자)를 `$yona.escapeHtml`로 감싸 넣는다(F10). `issue/my-list.html:29-37`의 같은 단추도 같다 | `POST /user/defultLoginPage?path=<경로, 앞 / 없음>`(주소의 `defult` 철자는 v1.6 그대로) | `{defaultLoginPage}` JSON | 로그인하지 않아도 200으로 받은 `path`를 되돌려 주고 **저장하지 않는다**. 값이 외부로 가는 주소(`RedirectSupport.isSafeRelativePath`가 아님)면 **400** + `{defaultLoginPage:null}`. `path`는 **쿼리 문자열에서만** 읽는다(폼 본문은 무시, 시험 `HomePageTest.kt:196-233`) | `my-series-menu-tab.html:13-17`, `notifications.html:122-133`, `HomeController.kt:58-70` |
| 알림 항목 `li.notification-stream` | 목록 | 알림이 있을 때 | 아이콘(`noti.edit`이면 글자 `Edit`, 아니면 `yobicon-<iconClass>`), 제목 링크(`noti.url`이 있을 때만 링크), 본문(`messageHtml` 이스케이프 없이 출력), 보낸 사람(아바타(`@{${noti.avatarUrl}}`), 이름, `@로그인`), 상대 시각(`.ago.float-end`, 제목 속성에 절대 시각) | 링크 `GET {url}` | | 본인의 알림만 보인다(시험 `NotificationListTest.kt:184`) | `partial-notifications.html:11-39` |
| `.stream-desc[data-toggle=learnmore]` | 영역 클릭 | 항상 | 링크(`a`)나 이미지를 누른 것이 아니면 `.more`(말줄임 `...`)를 토글하고 `#message-<id>`의 `nowrap`을 토글한 뒤 `min-height`를 본문 높이로 맞춘다 | 없음 | | 없음 | `notifications.html:77-95` |
| `.message-wrap` 넘침 | 로드 때 | | 줄임 상태에서 내용이 넘치면 뒤에 `div.more`(`...`)를 붙인다 | 없음 | | 없음 | `notifications.html:97-116` |
| `#notification-more` "더 보기" | 링크(`javascript:void(0)`), 글자는 메시지 키 `notification.more`(영어 "More", 한국어 "더 보기") | 알림이 하나라도 있을 때 | 단추를 지우고 다음 묶음을 읽어 `.activity-streams`에 이어 붙이고 `.more`를 다시 계산 | `GET /notification?from={from+size}&limit={size}` | 목록 조각(`<li>`들과 새 More 단추) | 익명도 호출 가능, 빈 목록 반환(시험 `NotificationListTest.kt:191`). `from`, `limit`는 필수 정수(없거나 숫자 아니면 400 오류 화면) | `partial-notifications.html:41-74`, `NotificationController.kt:24-31` |
| 알림이 없을 때 | 안내 | 목록이 비었을 때 | `div.warning-none`에 "알림 메시지가 없습니다."(`notification.none`), More 단추 없음 | | | | `partial-notifications.html:7-9` |

**More 요청 형식**: 최근 수정(`03467901b`)이 "유효한 URL을 요청하도록" 고쳤다. 지금 스크립트는 `yona.http.get("/notification?from=[(...)]&limit=[(...)]", function(data){...})` 형태로 값을 인라인으로 박는다(`partial-notifications.html:61`). 컨텍스트 경로를 붙이지 않는다(shell-F28).

알림 목록은 최신순이고 첫 화면은 20개(`HomeController.kt:82`, 템플릿에서 `list(${notifications}, 0, 20)` `notifications.html:55`), 이어 붙일 때의 개수는 직전 `size`이다(시험 `NotificationListTest.kt:155`). 알림 종류는 30개가 넘고 시험이 모두 한국어와 영어로 그려 본다(`NotificationEveryTypeTest.kt`).

### 8.3 `index/partial-intro` (익명 홈)

`.siteintro-bg > .siteintro`(제목 `h1.site-heading`은 메시지 키 `intro.heading`(영어 "21st Century Software Development Platform", 한국어 "21세기 소프트웨어 개발 플랫폼"), 한 줄 소개 `li`는 `intro.tagline`(영어 "Just focus on what you have to do", 한국어 "해야 할 일에만 집중하십시오"))와 가입 단추(`a.ybtn-success` `button.signup(사이트 이름)` -> `GET /users/signupform`), 그 아래 `.feature` 6칸(제목과 설명은 `title.*`, `site.features.*` 메시지)(`partial-intro.html:5-67`, 제목과 소개 `:9,11`, 시험 `HomePageTest.kt:72-97`).

## 9. 오류 화면 (`error/*.html`)

### 9.1 만드는 방법

컨트롤러가 `return ErrorViews.notFound("error.notfound.project")`처럼 돌려주면 **상태 코드와 화면**이 함께 정해진다(`ErrorViews.kt:17-79`). 예외 경로는 `GlobalErrorHandler`(`@ControllerAdvice @Order(LOWEST)`)가 맡는다.

| 상황 | 상태 | 템플릿 | 근거 |
|---|---|---|---|
| 일치하는 처리기가 없음, 방식이 맞지 않음 | 404(`GET`이고 경로가 `/`로 끝나며 `/`뿐이 아니면 마지막 `/`를 뗀 주소로 **303**, 쿼리 미포함. 비밀 키를 정하기 전의 GET과 HEAD는 이동 없이 설정 화면이나 재시작 안내가 먼저 나간다) | `notfound-default` | `GlobalErrorHandler.kt:38-70`, 시험 `TrailingSlashRedirectTest.kt` |
| 필수 파라미터 없음, 타입 불일치, 바인딩 실패 | 400 | `badrequest-default` | `GlobalErrorHandler.kt:72-79` |
| 그 밖의 예외 | 500(`error.timeout`은 쿼리 시간 초과 때) | `internal-server-error-default` | `GlobalErrorHandler.kt:81-93`, 시험 `ErrorPagesTest.kt:112,120` |
| 접근 검사 실패 등 `ViewResultException` | 던진 화면 그대로 | | `GlobalErrorHandler.kt:35-36` |
| 폼 본문 한도 초과로 `body`가 없음 | 413 | `request-text-entity-too-large` | `IssueCreateController.kt:144` 외 6곳(`BoardWriteController` 2곳, `IssueEditController`, `MilestoneController`, `PullRequestCreateController`, `PullRequestActionController`) |
| 익명 로그인 필요 | 403 + 로그인 폼 화면, 제목 키 `error.fobidden`(오타 그대로) | `LoginFormController.loginPage` | `ErrorViews.kt:74-75` |

응답 헤더: 화면 응답은 `Cache-Control: no-cache`(`DefaultHeadersInterceptor.kt:17`)이고, 예외 처리기가 그리는 400과 500은 이 헤더를 **뗀다**(`GlobalErrorHandler.kt:77,85`, v1.6 `Global.onError`와 같음, 시험 `ErrorPagesTest.kt:106`). 접근 검사가 막은 응답에도 붙지 않는다(`LayoutParityTest.kt:73`).

**오류 화면은 항상 HTML이다.** `Accept`나 `HX-Request`, `X-Requested-With`에 따라 JSON이나 조각으로 바꾸지 않는다. 그래서 `yona.Request`, `yona.http`로 부른 비동기 요청이 오류를 받으면 오류 화면 전체 HTML이 본문으로 온다. htmx 목록 요청이 500이나 오류를 받으면 `yona.Htmx.js`가 일반 이동으로 다시 연다(`shell-scripts.md` 3.3절).

### 9.2 화면별 계약

공통 구조는 `.page-wrap-outer > .project-page-wrap > .error-wrap`(아이콘 `i.ico-404` 또는 `i.ico.ico-err2`, 문구 `p`, 단추)이다. 문구는 `#messages.msgOrNull(messageKey) ?: messageKey`로 **키가 없으면 키를 그대로** 보인다(v1.6 `Messages()`와 같음).

| 템플릿 | 레이아웃 | 모델 | 단추 | 문구 | 근거 |
|---|---|---|---|---|---|
| `notfound-default` | **`layout/layout`**(site-layout이 아님) + 자체 상단 바와 푸터 | `messageKey` | "홈"(`menu.home`, `/`) | 기본 `error.notfound` "페이지를 찾을 수 없습니다" | `notfound-default.html:7-41` |
| `forbidden-default` | `site-layout(SITE_HOME)` | `messageKey` | "홈"(`ybtn-primary`) | 기본 `error.forbidden`, 사이트 관리자 검사 실패는 `error.auth.unauthorized.waringMessage` | `forbidden-default.html:6-18` |
| `badrequest-default` | `site-layout` | `messageKey` | "홈"(`ybtn-info`) | 기본 `error.badrequest`. 아이콘이 404용 `ico-404` | `badrequest-default.html:6-18` |
| `internal-server-error-default` | `site-layout` | `messageKey` | "홈"(`ybtn-primary`) | `error.internalServerError`, `error.timeout`. 아이콘 `ico-404` | `internal-server-error-default.html:6-18` |
| `request-text-entity-too-large` | `site-layout` | `site.parsersTextMaxLength` | 없음 | 제목 `error.tooLargeText.title`, 한도 `error.tooLargeText.limit({0}바이트)`, 사이트 관리자에게만 `error.tooLargeText.admin`(`yona.parsers-text-max-length` 안내) | `request-text-entity-too-large.html:10-24` |
| `forbidden` | `project-layout`(메뉴 `NONE`) + `project/menu(PROJECT_HOME)` | `projectView`, `messageKey` | 익명이면 "로그인"(`a[data-login=required]`, 주소 `/users/loginform?redirectUrl=<requestUri>`) | 키 번역 | `forbidden.html:6-19` |
| `notfound` | `project-layout`(`NONE`) + `project/menu(menuType)` | `projectView`, `messageKey`, `targetType` | "목록"(`button.list`) 링크 `backUrl` | 종류가 없으면 `error.notfound`, 있으면 `error.notfound.<종류>`에 `messageKey`를 `{0}` 인자로. 모르는 종류는 키 그대로 | `notfound.html:6-25` |
| `badrequest` | `project-layout(menuType)` + `project/menu` | `projectView`, `messageKey`, `menuType` | 없음 | 키 | `badrequest.html:6-18` |
| `forbidden-organization` | `organization-layout(NONE)` + `organization/header`, `organization/menu` | `orgView`, `messageKey` | 없음 | 키 | `forbidden-organization.html:6-19` |

`notfound`의 종류별 메뉴와 돌아갈 목록(`notfound.html:9-14`):

| `targetType` | 활성 메뉴 | `backUrl` |
|---|---|---|
| `issue_post` | `ISSUE` | `/{owner}/{name}/issues?state=all` |
| `board_post` | `BOARD` | `/{owner}/{name}/posts` |
| `milestone` | `MILESTONE` | `/{owner}/{name}/milestones` |
| `code` | `CODE` | `/{owner}/{name}/settingform` |
| 그 밖, null | `PROJECT_HOME` | `javascript:history.back();` |

시험: `ErrorPagesTest.kt`(404, 403, 400, 500), `ProjectErrorPagesTest.kt`(프로젝트 안 403, 404, 400), `TrailingSlashRedirectTest.kt`. 413 화면의 단위 시험은 확인하지 못했다(`requestTextEntityTooLarge`를 부르는 컨트롤러 시험에서 간접 확인 가능성, 추측).

`notfound-default`의 자체 상단 바는 다음과 같다(`notfound-default.html:10-26`): `a.logo`(`h1.blind`에 사이트 이름), "프로젝트 목록"(`/projects`, 키 `title.projectList`), "도움말"(`/_help`), 피드백(**`https://github.com/nforge/yobi/issues?state=open` 하드코딩**, `site.feedbackUrl`을 쓰지 않음), `common/usermenu`. 푸터는 "Copyright © NAVER Corp. Supported by D2 Program"(`:36-40`). 일반 상단 바와 달리 `hideProjectListing`, 게스트 조건, 검색 폼, `.pin`이 없다(F17).

## 10. 도움말 (`help/*`)

### 10.1 `help/toc.html` (`GET /_help`)

`HelpController.kt:17-18`(클래스에 `@AnonymousCheck`)이 `title="title.help"`로 그린다. 레이아웃 `siteLayout(title.help, 'HELP', ::tocPage)`. `ul.qas > li.qa` 6개(설치, 프로젝트 만들기, 참여 프로젝트 보기, 탈퇴, 게시판 기능, 버그 신고). 질문과 답변은 **언어와 상관없이 한국어 하드코딩**이다(`toc.html:8,21-128`, 시험 `HelpAndStatisticsTest.kt:54`).

| 요소 | 동작 | 근거 |
|---|---|---|
| `li.qa` 클릭 | `open` 클래스 토글(답변 펼침/접힘, CSS). 인라인 스크립트가 `$(".qas > .qa").click(...)` | `toc.html:132-138`, 시험 `HelpAndStatisticsTest.kt:93` |
| `a.question[href="#!/toggle"]` | 이동하지 않는 자리 링크(해시만 바뀜) | `toc.html:24` |
| "메인화면", "정보 페이지" 링크 | `/`와 로그인했으면 `/` + 로그인 ID. 메인화면 링크는 `@{/}`(`:73`)이고 정보 페이지 링크는 `'/' + 로그인 ID` 문자열이라 접두어를 붙이지 않는다(`:76,89`, F28) | `toc.html:73,76,89`, 시험 `HelpAndStatisticsTest.kt:69` |

### 10.2 `help/keymap.html` (단축키 안내 모달)

`keymap(section, view)`. `section`은 `issueList`, `boardList`, `issueDetail`, `boardDetail` 네 값만 쓰인다. 단추 "단축키 안내"(`a.ybtn.ybtn-inverse.ybtn-mini[href=#helpKeys][data-bs-toggle=modal]`)가 `div#helpKeys.modal.fade.keymap-help`를 연다. 모달 안 `.actrow`의 "확인"이 `data-bs-dismiss`로 닫는다. 여는 단축키는 없다. `ctrl` 표기는 `isMac ? '⌘' : 'CTRL'`이다(`keymap.html:10,12-14,124-126`).

**화면 4곳**: `issue/partial-list-wrap.html:44,72`(`issueList`), `issue/view.html:261`(`issueDetail`), `board/list.html:63`(`boardList`), `board/view.html:128`(`boardDetail`). 시험은 `#helpKeys`의 존재만 확인한다(`IssueListTest.kt:146`, `IssueViewTest.kt:640`). 안내 내용 자체의 시험은 없다.

표시 규칙(`keymap.html`). 프로젝트 칸의 조건은 `project/menu.html:75-94`의 실제 등록 조건과 같다. `view`가 null이면(지금 부르는 4곳은 모두 프로젝트 안 화면이라 일어나지 않는다) `H B I C M`만 보이고 `P Q`는 숨는다.

| 칸 | 항목 | 조건 | 실제 등록과 맞는지 |
|---|---|---|---|
| 프로젝트(`project.projects`) | `H` 홈 | 항상(`keymap.html:19-20`) | 같음(`menu.html:76`) |
| | `B` 게시판 | `view == null or view.menuSetting.board`(`:22-25`) | 같음(`menu.html:77-79`) |
| | `I` 이슈 | `view == null or view.menuSetting.issue`(`:27-30`) | 같음(`menu.html:83-85`) |
| | `C` 코드 | `view == null or (menuSetting.code and view.codeAccessible)`(`:32-35`) | 같음(`menu.html:80-82`) |
| | `M` 마일스톤 | `view == null or view.menuSetting.milestone`(`:37-40`) | 같음(`menu.html:86-88`) |
| | `P` 코드 주고받기 | `view != null and menuSetting.pullRequest and vcs.pullRequests and codeAccessible`(`:42-45`) | 같음(`menu.html:89-91`) |
| | `Q` 설정 | `view != null and view.manager`(`:47-50`) | 실제 `view.settingShortcutEnabled`(= `manager`)와 같다(`menu.html:92-94`) |
| 화면(`title.<section>`) | `N` 새 글 | `section`에 `board` 포함(`:58-61`) | 게시판 목록은 항상 등록(메뉴를 끈 프로젝트의 게시판 화면은 404라 일어나지 않음), 게시글 보기는 `menuSetting.board`일 때(`board/view.html:172-174`) |
| | `N` 새 이슈 | `section`에 `issue` 포함(`:63-66`) | 이슈 목록은 항상(`issue/list.html:33`), 이슈 보기는 `menuSetting.issue`일 때(`issue/view.html:321-323`) |
| | `L` 목록, `E` 수정 | `section`에 `Detail` 포함(`:68-74`) | `E`는 수정 권한이 있을 때만 등록(`issue/view.html:324-326`, `board/view.html:175-177`). 안내는 항상 나온다 |
| | `←` 이전, `→` 다음 | `section`에 `List` 포함(`:76-82`) | 이전, 다음 쪽이 있을 때만 등록(`Pagination.js:311-315`) |
| | `CTRL(⌘)+A` 전체 선택 | `section == 'issueList'`(`:84-87`) | 일괄 변경 영역이 있을 때만(`issue.MassUpdate.js:124`) |
| 사이트(`site`) | `A` 내 이슈, `U` 프로필, `F` 사용자 메뉴, `(CTRL+)ALT+S` 사이트 검색(Mac이면 `CTRL`을 덧붙임), `CTRL(⌘)+ENTER` 폼 전송 | 항상 | `U`는 로그인했을 때만 등록(`scripts.html:95-99`). `F`는 5.2절 조건 |
| 이슈 댓글(`search.menu.issue.comments`) | `SHIFT + CTRL(⌘) + ENTER` "댓글 입력하고 이슈 닫기"(`:112-118`) | `section`에 `issue`와 `Detail` 모두 | 등록 키 `CTRL+SHIFT+ENTER`(`issue.View.js:494`)가 동작한다. 엔진이 보조키를 `ALT, CTRL, SHIFT` 순으로 정규화한다(F4) |

`title.boardDetail` 메시지 키가 어느 언어 파일에도 없어 `boardDetail` 안내의 칸 제목이 빠진다(F1).

### 10.3 `help/markdown.html` (마크다운 문법 도움말)

`common/editor`가 포함한다(조각 `markdown`, 인자 없음, `th:inline="none"`). `div.markdown-help`: 위의 배지(`title.markdown.help`)와 10개 메뉴 `li.help-nav[data-toggle=markdown-help][data-target=...]`(Header, Text Style, Link, List, Checklist, Image, Blockquote, Code, Table, Short Link), 아래 `ul.markdown-help-wrap > li.markdown-help-item.<target>`(입력 예 `pre`와 출력 `div.markdown-wrap[markdown=true]`). 제목과 메뉴 글자는 영문 하드코딩이다(`markdown.html:11-26`, 배지만 `title.markdown.help`).

| 요소 | 동작 | 근거 |
|---|---|---|
| `.markdown-help-nav` 항목 클릭 | 눌린 항목에 `active`를 토글하고 대응하는 `.markdown-help-item`에 `active`를 토글. 같은 항목을 다시 누르면 닫힘 | `markdown.html:277-292` |
| `[markdown=true]` 출력 칸 | `esm/Markdown.js`가 글자를 읽어 `marked`로 그리고 `xssClean`한다(`shell-scripts.md` 4.6절) | `Markdown.js:64-66,108-114` |

바인딩은 전역 변수 `helpNavEventSetted`로 한 번만 한다. 처리기는 **그때 있던** `.markdown-help-nav`에만 붙으므로 한 화면에 편집기가 여러 개일 때 나중에 복제되는 편집기(코드 diff 댓글)에는 붙지 않을 수 있다(추측: 순서만 읽음).

## 11. 요청과 응답 계약 (공통)

| 주제 | 계약 | 근거 |
|---|---|---|
| CSRF | 상태를 바꾸는 요청(POST, PUT, PATCH, DELETE)은 쿠키 `XSRF-TOKEN`과 같은 값을 헤더 `X-XSRF-TOKEN`이나 폼 인자 `_csrf`로 보내야 한다. 읽기 요청과 `/-_-api`, `/mcp`, `/svn`, Git 스마트 HTTP, LFS, `/internal/ssh/`, `Authorization`이나 `Yona-Token` 헤더가 있는 요청은 제외. 토큰이 없거나 틀리면 **403**(Spring Security 기본 거절, 본문 형태는 확인하지 못함). `yona.csrf-protection=false`로 끌 수 있다 | `SecurityConfig.kt:60-63`, `CsrfSupport.kt:16-30`, `YonaProperties.kt:83`, 시험 `CsrfProtectionTest.kt:36-68` |
| 토큰 싣기 | `yona.Csrf.js`가 XHR, `fetch`, 폼 제출을 가로채 같은 출처 요청에만 싣는다. 스크립트가 부르는 `form.submit()`도 포함 | `shell-scripts.md` 2.2절 |
| 로그아웃 | **GET** `/users/logout`(CSRF 대상 아님). 303으로 `Referer`로 이동하고, `Referer`가 없으면 홈(`/`)으로 이동한다(v1.6은 `Location: null`이었다, usersec-F13) | `LoginController.kt:44-51`(R-03) |
| `Accept` | 이 담당의 화면은 `Accept`를 보지 않는다. 예외는 `/projects`(JSON이면 이름 목록)와 업로드(`/files`)로 담당 밖 | |
| `HX-Request` | 이 담당 화면은 조각 응답을 나누지 않는다. `/notification`과 `/user/usermenuTabContentList`는 항상 조각이다 | |
| 오류 응답 | 9.1절. 즐겨찾기 API는 상태 코드만(403 본문 없음, 400 본문 글자) | 5.4절 |
| 리다이렉트 | 익명이 `@AnonymousCheck` 화면에 오면 `303`으로 `/users/loginform?redirectUrl=<요청 경로>`. GET이면 질의 문자열이 있을 때 `%3F` + 인코딩한 질의 문자열이 `redirectUrl` 뒤에 붙는다(v1.6은 경로만 실어 질의가 사라졌다, oauth-F7). 안내를 켠 화면은 flash `user.login.alert` | `AnonymousCheckStage.kt:25-37` |
| 사이트가 익명을 막은 경우 | `yona.allows-anonymous-access=false`이면 `@AnonymousCheck` 화면이 모두 로그인을 요구한다. 어노테이션이 없는 `/user/usermenuTabContentList`, 즐겨찾기 GET, `/messages.js`, `/markdown/*`은 그대로 열린다 | `AnonymousCheckStage.kt:26`, `YonaProperties.kt:148` |
| 일회성 메시지(flash) | 서버 리다이렉트가 심고 다음 화면이 `common/scripts`에서 `$yona.notify(메시지, 5000)`나 `$yona.alert(제목, null, 설명)`로 보인다(`shell-scripts.md` 2.5절) | `scripts.html:131-138` |

## 12. 권한 행렬

"보임"은 템플릿 조건, "서버"는 실제로 막는 규칙이다. 두 칸이 다르면 굵게 적었다.

| 기능 | 비로그인 | 로그인(일반) | 게스트 | 그룹 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|
| 상단 바 "전체 목록" | 보임(`hideProjectListing`이 꺼짐 and 게스트 아님이면 익명도 보임). 서버: **`/projects`는 `@AnonymousCheck`만이라 열린 사이트에서 익명 허용**(`hideProjectListing`이면 모두 403) | 보임 | **숨김**, 서버는 `@GuestProhibit`로 홈 이동 | 같음 | 같음 |
| 상단 검색 범위 "모든 프로젝트" | 조건 충족 시 보임 | 보임 | 숨김(조직 맥락이면 구성원일 때 "현재 그룹") | 조직 구성원 | **항상 보임**(`siteManager`) |
| 내 이슈, 아바타, `+` | 숨김. 서버는 로그인 폼 이동 | 보임 | 보임(새 그룹만 숨김. 서버도 `GET /organizations/new`를 `@GuestProhibit`로 막아 홈으로 보낸다) | 보임 | 보임 |
| 렌치(사이트 관리) | 숨김. 서버 403 | 숨김. 서버 **403** | 숨김, 403 | 숨김, 403 | 보임, 허용 |
| 관리자 띠 | 숨김 | 로그인 ID가 `admin`이면 보임(권한과 무관) | 같음 | 같음 | `admin` ID일 때 |
| 업데이트 알림 | 숨김 | 숨김 | 숨김 | 숨김 | 보임. 숨기기 POST와 다시 켜기 POST(`/sites/watchUpdate`)도 이 역할만(`@SiteManagerOnly`) |
| 사용자 패널 목록 | 빈 내용 | 보임 | 보임 | 조직 프로젝트 수를 봄(`ORG_ADMIN`) | 조직 프로젝트 수를 봄 |
| 즐겨찾기 토글 POST | 별이 안 그려짐. 서버 403 | 허용 | 허용 | 허용 | 허용 |
| 핀 사이드바 | 자동 이동 없음. `/user/sidebar` 직접 열면 프레임만 | 보임 | 보임 | 보임 | 보임 |
| 알림 목록 | 소개만. `/notification`은 빈 목록 | 본인 것만 | 본인 것만 | 같음 | 같음 |
| 기본 로그인 페이지 저장 | 버튼 없음. 서버: 200을 주되 저장 안 함 | 허용 | 허용 | 허용 | 허용 |
| 도움말 | 보임 | 보임 | 보임 | 보임 | 보임 |

"프로젝트 멤버", "프로젝트 관리자", "소유자" 열은 이 담당 화면에 고유한 기능이 없어 두지 않았다(프로젝트 안 오류 화면은 모든 역할에게 같은 문구).

## 13. 빈 상태, 오류 상태, 경계 값

| 값 | 정의 위치 | 내용 |
|---|---|---|
| 홈 첫 화면 알림 수 | `HomeController.kt:82` | 20 |
| 알림 더 보기 개수 | `NotificationController.kt:26` | 요청의 `limit`(화면은 직전 `size`=20을 그대로 쓴다) |
| 사용자 패널 폭, 작은 화면 기준 | `Usermenu.js:9-10` | 360px, 720px(넘으면 360, 이하는 `100vw`) |
| `.gnb-outer` 높이 | `_page.scss:110` | 40px |
| 첨부 한도 | `YonaProperties.kt:149`, `scripts.html:109` | 기본 `2147483454`바이트. 화면 스크립트(`Files.init`)가 올리기 전에 검사하고, 서버도 `POST /files`에서 `file.size > maxFileSize`이면 413 `text/plain` "The file is too large."로 거절한다(`AttachmentController.kt:118-120`). 서버 `multipart max-file-size`는 `-1`이다(`application.yml:78-79`, F12) |
| 폼 본문 한도 | `YonaProperties.kt:106` | `parsers.text.maxLength` 기본 102400 바이트. 넘으면 폼 값이 비고 413 화면 |
| 단축키 입력 제외 | `ShortcutKey.js:42` | `INPUT`, `TEXTAREA`만 |
| 이슈 `+` 메뉴 빈 상태 | `IssueCreateController.kt:78-106` | 최근 방문 프로젝트가 없으면 안내와 함께 첫 화면 |
| 목록 "결과 없음" | `title.no.results` | 즐겨찾기, 프로젝트 4개 하위 탭, 최근 읽은 이슈가 각각 비면 |
| 알림 없음 | `notification.none` | "알림 메시지가 없습니다." |

## 14. 레이아웃 계약의 v1.6 대조 요약

| 항목 | v1.6 | v2 | 같은가 |
|---|---|---|---|
| 상단 바 조건, 검색 범위, 단축키 `accesskey` | `navbar.scala.html` | 위 3절 | 같음(대조) |
| 핀 로직과 쿼리 | `layout.scala.html:43-109` | `layout.html:46-115` | 다름: v1.6은 `pathname`만 넘겨 쿼리가 사라졌고 v2는 쿼리를 함께 인코딩해 넘긴다(F6) |
| `layout_framed`의 `hash` 필수(500) | `request.getQueryString("hash").get` | `UserMenuController.kt:39` | 같게 재현 |
| 사용자 패널 `window.open` 규칙 | `yona.Usermenu.js:150-157` | `Usermenu.js:147-160` | 같음 |
| 사용자 패널 목록 읽기 실패 | 아바타 단추 처리기가 걸리지 않음 | `Usermenu.js:15-22` | 다름: 실패해도 단추와 바깥 클릭 닫기와 `F` 키를 건다(F7) |
| 즐겨찾기 토글 실패 알림 | `JSON.parse` 예외로 알림이 뜨지 않음 | `Usermenu.js:196-212` | 다름: 본문 글자나 상태 문구로 알림창을 띄운다(F20) |
| 관리 메뉴 | 8항목 | 13항목 | 추가 5(감사, 보안, OAuth 앱, LFS, SSO) |
| `<head>`의 import map, text-expander, markdown-toolbar | 없음 | 있음 | 추가(D-105, D-106, D-122) |
| 분석 | UA 태그 | GA4 `gtag`, 화면 종류만 전송 | 대체(X-21) |
| CSRF | 없음 | 있음 | 추가(R-02) |
| 로그인 대화상자 2단계 인증, SSO 단추 | 없음 | 있음(`common/login-dialog`) | 추가 |
| 푸터 | `common/footer.scala.html` | `common/footer.html` | 같음 |

## 15. 다른 영역이 이 담당의 조각에 의존하는 방식(요약)

자세한 사용처 표는 [`shell-common-fragments.md`](shell-common-fragments.md) 2절에 있다. 레이아웃은 1.1절의 표와 같다. 레이아웃이 심는 **전역값**(사용자 패널 6개, `site.maxFileSize` 등)과 `Usermenu.js`는 프로젝트 머리의 별(`.project-breadcrumb > .user-project-list`)과 이슈 보기의 즐겨찾기(`.favorite-issue`)가 직접 쓴다.

## 16. 기존 위키 글과 어긋난 곳, 확인한 곳

### 16.1 `architecture/ui-ia-gitlab.md`

| 위키의 말 | 코드 | 판정 |
|---|---|---|
| 사용자 패널 `#mySidenav` 폭 360px(39행) | JS가 360px(`Usermenu.js:10`), CSS의 `.right-menu`는 350px | 맞음(JS 기준). CSS 350px과 다름을 덧붙임 |
| 핀 사이드바 폭 271px, 아래 "Yona, made by"(42행) | `.sidebar .nav` 270px + 테두리 1px, `#sidebar-bottom` | 맞음 |
| 사용자 패널과 핀 사이드바가 **같은 세 목록**을 중복(43, 56행) | 패널은 Ajax 조각, 핀은 서버 렌더. 같은 `my-*-list` 조각을 재사용 | 맞음 |
| 핀 사이드바가 iframe, 켠 상태는 `shallWeOpenLeftNavigation`에 기억(57행) | 같음. 단 **켜고 나서 모든 화면 이동이 이중 로드**이고 쿼리를 잃는다 | 빠진 사실 추가(F6) |
| 소개 블록 접힘을 `yobi-intro`로 기억(40행) | 저장값은 "보이는지"(`true`/`false`)이고 `"false"`가 아니면 펼침. 처음은 펼침 | 맞음, 의미를 정확히 적음 |
| 알림 "More" 단추 영문 표기(178행) | 메시지 키 `notification.more`(`partial-notifications.html:42`). 영어는 "More", 한국어는 "더 보기" | 지금은 언어를 따른다(F10) |
| 사이트 관리 사이드바 항목 13개(49행) | `site-mng-layout.html:25-39` 13개 | 맞음 |
| "알림 아이콘"을 새로 둘 수 있다(104행) | 지금 상단 바에 알림 링크가 **없다**(홈의 탭으로만) | 현재 사실 확인(설계 제안과 충돌 없음) |
| 사용자 설정 탭 11개 | 담당 밖 | 확인 안 함 |

### 16.2 `architecture/ui-shortcuts.md`

| 위키의 말 | 코드 | 판정 |
|---|---|---|
| `F`는 `ShortcutKey`가 아닌 `keypress`(`Usermenu.js`), 초점이 없을 때만 | `Usermenu.js:32-38,214-218` | 맞음. **추가**: 소문자 `f`와 `ㄹ`만이다(대문자 `F`는 안 됨). 목록 읽기가 끝나면(실패해도) 등록된다(F7) |
| `ALT+S`는 `accesskey` | `navbar.html:69` | 맞음 |
| 입력 제외는 `INPUT`, `TEXTAREA`만 | `ShortcutKey.js:42` | 맞음 |
| 도움말에는 `U`, `A`가 항상 있다 | `help/keymap.html:93-97` | 맞음. `U`는 로그인했을 때만 등록되는 점이 빠짐 |
| 도움말 표가 "실제 등록된 키와 어긋날 수 있다"(2.1) | 프로젝트 칸은 지금 실제 등록 조건과 같고, `N`, `E`, `←`, `→`, `CTRL+A`는 화면 조건에 달렸다 | 10.2절과 F5 |
| 구조: 키마다 핸들러 하나, 마지막 등록이 이긴다 | `ShortcutKey.js:136-139` | 맞음. **추가**: `beforeunload`에서 모듈이 스스로 `destroy`하여 이탈을 취소해도 단축키가 죽는다(F3) |
| `CTRL+SHIFT+ENTER`가 동작하지 않는다 | 등록 `issue.View.js:494`, 안내 `keymap.html:112-118`, 정규화 `ShortcutKey.js:227-238` | 지금은 동작한다(F4) |
| 단축키 표(전역, 맥락, 화면) | `shell-scripts.md` 5절에 등록 위치와 순서까지 확정 | 표와 일치. 5절이 더 자세함(등록 줄, 중복 순서) |

## 17. 발견 사항

확인한 사실을 우선하고 추측은 "추측"으로 표시했다. v1.6 대조는 적은 곳만 했다. 번호는 `shell-F1` 등으로 부른다(`shell-common-fragments.md`와 `shell-scripts.md`도 같은 번호를 쓴다).

| 번호 | 내용 | 근거 | v1.6 |
|---|---|---|---|
| shell-F1 **[브라우저로 확인됨, v1.6 결함 유지, R-151]** | `title.boardDetail` 메시지 키가 `messages.properties`, `messages_ko_KR`, `_ja_JP`, `_ru_RU`, `_uz_UZ` 모두에 없다. `help/keymap`의 `#{'title.' + ${section}}`는 `boardDetail`에서 키를 못 찾는다. Thymeleaf 기본 동작으로는 `??title.boardDetail_ko_KR??` 모양이 나올 것이다(추측, 이 환경에서 렌더링하지 않음) | `keymap.html:56`, `board/view.html:128`, `MessageSourceConfig.kt:25`(`use-code-as-default-message=false`) | v1.6도 키가 없고 `Messages()`가 키를 그대로 보였다(`conf/messages*`에 없음) |
| shell-F2 **[수정됨, A-457]** | 로그인 대화상자의 **네트워크 오류 분기가 도달하지 못한다**. `yona.http`는 연결 실패에서도 `readyState: 4`인 객체를 만들고(수정 전 `http.js`), 대화상자는 `readyState == 0`일 때만 `user.login.failed.network`를 쓴다(`LoginDialog.js:150`). 시험이 `readyState: 0` 스텁을 써서 잡지 못한다(`logindraft.test.mjs:48-49`). 결과: 연결 실패도 `user.login.failed`(일반 문구)로 나온다. 같은 패턴이 `project.Global.js:108`에도 있다 (2026-11-11 수정: `http.js`의 `createXhr`가 응답이 없으면(연결 실패) `readyState`를 0으로 만든다(`http.js:77-78`). 로그인 대화상자의 네트워크 오류 분기(`LoginDialog.js:149-151`)와 `project.Global.js:108`이 닿는다. 시험 `logindraft.test.mjs:48-49`가 `readyState: 0`을 쓴다.) | 위 | jQuery 시절에는 연결 실패가 `readyState 0`이었다(추측: 이식 중에 생긴 어긋남) |
| shell-F3 **[브라우저로 확인됨, v1.6 결함 유지, R-149]** | `ShortcutKey`가 `beforeunload`에서 `destroy()`를 불러 `keydown` 처리기를 떼고 `htHandlers`를 null로 만든다. 댓글 입력 중 이탈 확인창(`CommentForm.js`, `common.comment.beforeunload.confirm`)에서 **취소**해도 이후 모든 단축키(`CTRL+ENTER` 포함)가 그 화면에서 죽는다. 코드로만 확인, 실행해 보지 않음 | `ShortcutKey.js:59-71,273-279`, `CommentForm.js` `onBeforeUnloadWindow` | 같음(`yobi.ShortcutKey.js:58,214`) |
| shell-F4 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `CTRL+SHIFT+ENTER`(이슈 댓글 제출)는 도움말에 있으나 동작하지 않는다(위키 `ui-shortcuts.md` 10절이 Playwright로 확인). 원인 후보는 키 이름 정규화가 **인자 하나뿐인 비교 함수**로 정렬해(수정 전 `ShortcutKey.js`) `CTRL+SHIFT+ENTER`가 `SHIFT+CTRL+ENTER`가 되고 이벤트가 만드는 `CTRL+SHIFT+ENTER`와 다르다(추측, 정렬 구현에 따라 다름) (2026-11-11 수정: `_normalizeKeyString`이 보조키를 `ALT`, `CTRL`, `SHIFT` 순으로 앞에 두고 주 키를 맨 뒤에 둔다 `ShortcutKey.js:227-238`. 이벤트가 만드는 문자열과 같아져 등록 키 `CTRL+SHIFT+ENTER`가 동작한다.) | `issue.View.js:494`, `keymap.html:112-118` | R-147과 같은 계열 |
| shell-F5 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 단축키 안내와 실제 등록이 다르다: `H B I C M`(프로젝트)와 `N`(새 글, 새 이슈)은 메뉴가 꺼져도 안내에 나오고, `E`는 권한이 없어도 나오고, `P`는 `menuSetting.pullRequest`를 보지 않는다. 안내가 있는 화면이 이슈 목록과 보기, 게시판 목록과 보기 4곳뿐이라 마일스톤 목록의 `N`, 코드 이력의 `A`와 `S`, 풀 리퀘스트 목록 등은 안내가 없다. 안내 시험은 `#helpKeys` 존재뿐 (2026-11-11 수정: 도움말의 프로젝트 칸이 실제 등록 조건과 같아졌다. `B I M`은 해당 메뉴가 켜졌을 때, `C`는 메뉴와 `codeAccessible`, `P`는 `menuSetting.pullRequest`와 `vcs.pullRequests`와 `codeAccessible`일 때만 나온다 `keymap.html:22-45`. 화면 칸의 `N`, `E` 안내와 안내가 있는 화면이 4곳뿐인 것은 그대로이다.) | `keymap.html:19-87`, `project/menu.html:76-94`, 시험 `IssueListTest.kt:146` | 구조는 같음 |
| shell-F6 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 핀 켜기와 자동 이동의 `path`가 `window.location.pathname`만이라 **쿼리 문자열이 사라진다**(예: `?state=closed&pageNum=2`). 저장값이 `"true"`이면 로그인 사용자의 일반 화면은 열릴 때마다 `/user/sidebar`로 한 번 더 이동한다 (2026-11-11 수정: 이동 주소의 `path`가 `window.location.pathname + window.location.search`를 `encodeURIComponent`로 인코딩한 값이라 쿼리 문자열이 유지된다 `layout.html:50,106`. 이중 로드는 그대로이다.) | `layout.html:50,106` | 같음(`layout.scala.html:47,102`) |
| shell-F7 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 사용자 패널은 `GET /user/usermenuTabContentList`가 **실패하면 `iniNaviUserMenu()`가 불리지 않아** 아바타 단추, 바깥 클릭 닫기, `F`키가 모두 안 걸린다(콘솔 로그만). `F`는 소문자 `f`와 `ㄹ`만 받고(Shift나 CapsLock이면 안 열림), 사용하는 `keypress`는 폐기 예정 이벤트이다 (2026-11-11 수정: 목록 읽기가 실패해도 `iniNaviUserMenu()`를 불러 아바타 단추와 바깥 클릭 닫기와 `F`키를 건다 `Usermenu.js:15-22`. `F`는 소문자 `f`와 `ㄹ`만 받고 `keypress`를 쓰는 것은 그대로이다.) | `Usermenu.js:12-27,31-55,214-218` | 같음(`yona.Usermenu.js:24`) |
| shell-F8 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 핀을 켜지 않은 화면에서 패널의 프로젝트와 이슈를 누르면 **새 탭**으로 열린다(`ctrl`, `meta`, `shift`가 눌릴 때만 같은 창). 핀 사이드바에서는 iframe에 열린다 | `Usermenu.js:147-160` | 같음 |
| shell-F9 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 마이그레이션 레이아웃의 사용자 패널은 `Usermenu.js`를 불러오지 않아 **항상 "Loading..."**이고 패널 코드를 자체 인라인으로 따로 가진다(폭 420px). 머리 주석의 "jquery 1.9.0"은 실제 3.7.1과 다르다. 구글 글꼴 3종을 외부에서 불러온다 별 토글 실패 알림도 `"Update failed: " + $yona.escapeHtml(JSON.parse(data.responseText).reason)`로 영문 고정이고 `JSON.parse`를 감싸지 않는다(`migration-layout.html:151,167`). | `migration-layout.html:7,21-23,39,60-186` | 패널 결함은 v1.6도 같음(`migrationPageLayout.scala.html`에 `Usermenu` 없음) |
| shell-F10 **[검증: 수정함(A-464, 2026-11-11)]** | 번역 키가 아닌 하드코딩 글자: "More"(`partial-notifications.html:42`), "Set to default: "(`notifications.html:127`), "set Default page failed: " + `data`(객체라 `[object Object]`가 된다, `notifications.html:131`, `issue/my-list.html`도 같음), "Update failed: "(`Usermenu.js:116,125,173`), `.pin`의 툴팁 "Sidebar"(`navbar.html:21`, `sidebar.html:19`), "Yona, made by"(`layout-framed.html:52`), `Draft saved`(`temporarySaveHandler.js`), 소개 문구(`partial-intro.html:9,11`), 푸터, 도움말 목차와 마크다운 도움말 본문. 모두 언어와 상관없이 고정이다 (2026-11-11 수정(A-464): 메시지 키로 바꿨다. `notification.more`, `common.defaultPage.set`, `common.defaultPage.failed`, `common.updateFailed`, `layout.sidebar.tooltip`, `layout.madeBy`("Yona, made with love"), `draft.saved`, `intro.heading`, `intro.tagline`이 `messages*.properties`에 있다. 지금도 고정인 곳은 푸터(`footer.html`, `notfound-default`의 푸터), 도움말 목차와 마크다운 도움말 본문, 알림 항목의 `Edit` 글자(`partial-notifications.html:13`), 마이그레이션 레이아웃의 `Update failed:`이다. 알림에 값을 넣는 호출부는 `$yona.escapeHtml`로 감싼다(F27).) | 위 | 대부분 v1.6도 같을 것(추측, `notifications.scala.html`은 대조하지 않음) |
| shell-F11 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 중복 `id`: `comment-update-form`의 입력 `upload-{id}`와 영역 `upload-{id}`(`comment-update-form.html:24,43`), `my-project-list`와 `my-recent-issue-list`의 `id="query"`(`:21`, `:14`), `two-column-mode-checkbox-area`와 `show-subtasks-checkbox`의 `id="two-column-mode-checkbox"`가 **한 화면에 함께 그려진다**(`issue/partial-list-wrap.html:30-31`, `issue/my-partial-search.html:80-81`, `user/view.html:66,84`) | 위 | `showSubtasksCheckbox`와 `twoColumnModeCheckboxArea`의 id 중복은 v1.6 템플릿에서 확인하지 않음 |
| shell-F12 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `site.maxFileSize`는 화면 스크립트(`Files.init`의 `maxFileSize`)만 검사한다. `POST /files`는 크기를 보지 않고 서버 멀티파트 한도도 `-1`(무제한)이다 (2026-11-11 수정: 서버도 `POST /files`에서 `file.size > properties.maxFileSize`이면 413 `text/plain` "The file is too large."로 거절한다 `AttachmentController.kt:118-120`. 서버 멀티파트 한도는 `-1` 그대로이다.) | `scripts.html:109`, `AttachmentController.kt:111-141`, `application.yml:76-79` | 같음(v1.6도 화면만 사용, `scripts.scala.html:105`) |
| shell-F13 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `POST /markdown/{user}/{project}`에는 접근 검사 어노테이션이 없다. 익명이 닫힌 사이트에서도 부를 수 있다. 프로젝트 문맥 후처리는 보는 사람의 읽기 권한으로 이슈 링크 변환을 달리한다(R-16) | `MarkdownController.kt:27-37`, `ProjectMarkdownRenderer.kt:45-48` | 같음(`MarkdownApp.render`에 검사 없음) |
| shell-F14 **[검증: 문서 문제, 정리함 A-460]** | `$yona.loadModule`의 대체 경로가 `javascripts/service/yona.<이름>.js`인데 `static/assets/javascripts/service/`는 **빈 디렉터리**이다. 모든 화면 모듈은 `esm/service/*.js`가 이미 등록하므로 대체 경로는 닿지 않는다(등록이 늦으면 존재하지 않는 파일을 3번 시도하고 콘솔에 실패 로그). `javascripts/template/yona.CodeTemplate.js`는 어디서도 참조하지 않는다(죽은 파일 후보, 정리는 선생님 확인 필요) (2026-11-11 확인: 빈 `service/` 디렉터리와 `template/yona.CodeTemplate.js`는 지금 없다. 대체 경로를 시도하면 존재하지 않는 파일을 3번 요청하는 동작은 그대로이다.) | `yona.Common.js:77-94`, 확인: `grep CodeTemplate` 결과 파일 자신뿐 | |
| shell-F15 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `layout.html`의 관리자 띠는 **로그인 ID가 `admin`**이면 보인다. 사이트 관리자 권한과 무관하다(`siteAdminLoggedIn`). `admin` ID를 가진 일반 사용자가 있으면 그에게 띠가 보인다 | `LayoutModelAdvice.kt:76,89` | v1.6 `isSiteAdminLoggedInSession`과 같다고 주석이 말함(대조 안 함) |
| shell-F16 **[검증: 수정함(A-464, 2026-11-11)]** | `/`로 시작하는 절대 경로를 `@{}` 없이 쓰는 링크가 있다(컨텍스트 경로가 있는 배포에서 어긋날 수 있음, 추측: 영향 확인 안 함): `/notification?...`(`partial-notifications.html:61`), `'/' + loginId`(`sidebar.html:11`, `child-comments.html:18`, `menu-project.html:38`, `partial-notifications.html:28,34`, `help/toc.html:73,76,89`), `/files/{id}`(`attachment-file.html:10`) (2026-11-11 수정: `sidebar.html:11`, `child-comments.html:18`, `menu-project.html:38`, `partial-notifications.html:28,34`, `help/toc.html:73`은 `@{...}`로 바뀌었고 스크립트 전역 `window.yonaContextPath`가 생겼다(`scripts.html:43`). 남은 곳은 F28.) | 위 | R-104가 컨텍스트 경로를 다뤘다 |
| shell-F17 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `error/notfound-default`의 자체 상단 바는 일반 상단 바와 다르다: `hideProjectListing`과 게스트 조건 없이 "프로젝트 목록"이 항상 나오고, 피드백 링크가 `https://github.com/nforge/yobi/issues?state=open`으로 고정이며 `site.feedbackUrl`을 무시한다. 사이트 설정을 바꾼 사이트에서 404 화면만 다른 링크를 보인다 | `notfound-default.html:15-22` | v1.6 `notfound_default`와 같게 이식했다고 주석이 말함(대조 안 함) |
| shell-F18 **[검증: 결함 아님]** | 사이트 관리 메뉴의 활성 표시가 경로 **정확 일치**라(`requestPath == ...`) 하위 경로(상세, 새로 만들기)에서는 활성이 꺼진다. v1.6은 라우트 비교(`isActiveMenu(routes...)`)였다 | `site-mng-layout.html:25-41` | 비교 방식 다름(대조) |
| shell-F19 **[브라우저로 확인됨 2026-11-11, R-163]** | 화면이 부르지 않는 즐겨찾기 GET이 있다: `GET /user/favoriteOrganizations`, `GET /user/favoriteIssues`(익명이면 NPE로 500). 레이아웃은 `UsermenuGetFoveriteIssuesUrl`을 심지만 `Usermenu.js`는 읽지 않는다. 조직 GET의 전역값은 아예 없다 | `layout.html:79`, `FavoriteApiController.kt:48-60,97-105` | 같음 |
| shell-F20 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 즐겨찾기 토글 실패 때 `Usermenu.js`가 `JSON.parse(failure.responseText).reason`을 읽는데 서버가 주는 400 본문은 `Wrong project id` 같은 평문이라 파싱에서 스크립트 오류가 난다. 알림창이 뜨지 않는다(실제로 닿는 경우는 직접 호출뿐이라 영향은 작다) (2026-11-11 수정: `failureReason`이 JSON이면 `reason`, 짧은 평문이면 그 글자, 그 밖이면 상태 문구를 고른다 `Usermenu.js:196-212`. 알림은 `common.updateFailed`이다.) | `Usermenu.js:118,133,175`, `FavoriteApiController.kt:41,68` | |
| shell-F21 **[브라우저로 확인됨, v1.6 결함 유지, R-150]** | **내 이슈 화면의 빠른 검색 "즐겨찾기"** 라벨(`span.favorite-issue`)이 `Usermenu.js`의 `.favorite-issue` 처리기에 잡혀, 라벨 글자를 누르면 `e.stopPropagation()` 뒤에 `POST /user/favoriteIssues/undefined`(`data-issue-id`가 없음)가 나가 400이 되고 필터 링크 클릭이 위임 처리기에 닿지 않을 수 있다(코드로 따라감, 실행 확인 안 함) | `issue/my-partial-search.html:43`, `Usermenu.js:124-139`, `issue.List.js:68` | 같은 마크업과 클래스(`my_partial_list_quicksearch.scala.html:108`) |
| shell-F22 **[검증: 결함 아님]** | `layout.html`은 `window.parent.document.title`과 `window.parent.history.replaceState`를 **무조건** 부른다(`parent`가 자기 자신이면 무해). 다른 출처가 이 문서를 iframe으로 끼우면 `X-Frame-Options: SAMEORIGIN`이 막지만, 같은 출처 부모가 있을 때만 의미가 있다 | `layout.html:43-44` | 같음 |
| shell-F28 **[재조사 2026-11-11]** | 컨텍스트 경로를 붙이지 않는 주소가 남아 있다(하위 경로 배포에서 어긋남): 알림 더 보기가 부르는 `/notification?from=...&limit=...`(`partial-notifications.html:61`, 스크립트 문자열이라 `@{}`가 없고 `yona.http`도 접두어를 붙이지 않는다), 도움말 목차의 정보 페이지 링크 `'/' + 로그인 ID`(`help/toc.html:76,89`), 첨부 항목의 `data-href='/files/' + id`(`common/attachment-file.html:10`, 첨부 삭제 요청 주소로 쓰인다). `requestPath`는 `request.requestURI`라 컨텍스트 경로를 포함하므로 하위 경로 배포에서 `requestPath == '/sites/...'`, `requestPath != '/'`, `requestPath.substring(1)` 비교(`site-mng-layout.html:25-41`, `my-series-menu-tab.html:14-15`)도 어긋날 수 있다(추측: 실행해 보지 않았다) | `partial-notifications.html:61`, `help/toc.html:76,89`, `attachment-file.html:10`, `LayoutModelAdvice.kt:82` | v1.6은 역방향 라우트로 접두어를 반영했다 |

## 18. 범위 점검

| 템플릿 | 이 파일에서 | 다른 파일에서 |
|---|---|---|
| `layout/layout.html` | 2.1, 2.1.1 | |
| `layout/layout-framed.html` | 2.4, 6 | |
| `layout/migration-layout.html` | 2.5 | |
| `layout/organization-layout.html` | 2.2 | |
| `layout/project-layout.html` | 2.2 | |
| `layout/site-layout.html` | 2.2 | |
| `layout/site-mng-layout.html` | 2.2, 2.3 | |
| `common/navbar.html` | 3 | |
| `common/usermenu.html` | 4, 5 | |
| `common/sidebar.html` | 6 | |
| `common/footer.html` | 7 | |
| `common/scripts.html` | 1.1 | `shell-scripts.md` 2 |
| `common/importmap.html` | 2.1 | `shell-scripts.md` 1 |
| `common/analytics.html` | | `shell-common-fragments.md` 1.6 |
| `common/login-dialog.html` | 4.1(로그인 링크) | `shell-common-fragments.md` 1.7 |
| `common/attachment-file.html`, `calendar`, `child-comment-form`, `child-comments`, `comment-count`, `comment-delete-modal`, `comment-form`, `comment-update-form`, `editor`, `file-uploader`, `markdown`, `my-series-menu-tab`(8.2도), `notification-mail`, `partial-history`, `review-form`, `select2`, `sharer-count`, `show-subtasks-checkbox`, `tasklist-bar`, `two-column-mode-checkbox-area`, `upload-form`, `vote-count` | | `shell-common-fragments.md` 1, 2 |
| `index/notifications.html` | 8 | |
| `index/partial-notifications.html` | 8 | |
| `index/partial-intro.html` | 8.3 | |
| `index/usermenu-tab-content-list.html` | 5.3 | |
| `index/my-organization-list.html`, `my-project-list.html`, `my-recent-issue-list.html`, `menu-project.html` | 5.3, 6.1 | |
| `error/*.html` 9개 | 9 | |
| `help/toc.html` | 10.1 | |
| `help/keymap.html` | 10.2 | `shell-scripts.md` 5 |
| `help/markdown.html` | 10.3 | `shell-common-fragments.md` 1.8 |

담당 템플릿 57개(layout 7, common 30, index 8, error 9, help 3)를 모두 읽었다.

## 갱신 기록 (2026-11-11 재조사)

기준은 현재 `ui-improvements`의 코드이다(`5ef425c98` 이후 변경 포함). 세 파일(`shell.md`, `shell-common-fragments.md`, `shell-scripts.md`)의 합계가 아니라 이 파일의 기록이다.

| 항목 | 내용 |
|---|---|
| 다시 확인한 템플릿 | 57개: `layout/` 7(layout, layout-framed, migration-layout, organization-layout, project-layout, site-layout, site-mng-layout), `common/` 30(이 파일은 navbar, usermenu, sidebar, footer, scripts, importmap, my-series-menu-tab. 나머지는 `shell-common-fragments.md`), `index/` 8, `error/` 9, `help/` 3. 함께 본 코드: `Usermenu.js`, `ShortcutKey.js`, `Request.js`, `esm/index.js`, `http.js`, `LoginDialog.js`, `UserMenuController`, `HomeController`, `NotificationController`, `HelpController`, `LayoutModelAdvice`, `GlobalErrorHandler`, `ErrorViews`, `FavoriteApiController`, `UserMenuAssembler`, `SiteMaintenanceController`, `AnonymousCheckStage`, `LoginController` |
| 내용을 고친 행과 문장 | 약 75곳. 컨텍스트 경로(전역값 `window.yonaContextPath`, 핀 이동 주소의 쿼리), 사용자 패널 목록 읽기 실패 때 단추 연결(`Usermenu.js:15-22`)과 `failureReason`, 즐겨찾기 실패 알림 문구 키 `common.updateFailed`, 기본 화면 설정 알림 키(`common.defaultPage.*`), `More` 단추 키(`notification.more`), 소개 문구(`intro.*`), 사이드바 툴팁(`layout.sidebar.tooltip`)과 푸터 문구(`layout.madeBy`), `help/keymap.html` 프로젝트 칸 조건, 업데이트 알림 숨기기 실패 알림과 다시 켜기, 로그아웃과 로그인 이동 주소, 새 그룹 폼의 게스트 거절, 첨부 한도의 서버 검사, 마이그레이션 레이아웃 구성, 키맵 `CTRL+SHIFT+ENTER`, 발견 사항 표시 F2, F4, F5, F6, F7, F9, F10, F12, F14, F16, F20 |
| 줄 번호만 고친 곳 | 약 54곳 (`UserMenuController`, `NotificationController`, `HelpController`, `GlobalErrorHandler`, `ErrorViews`, `Usermenu.js`, `scripts.html`, `layout-framed.html`, `site-mng-layout.html`, `keymap.html`, `LoginDialog.js` 등. 문서가 인용하던 컨트롤러 줄은 대부분 실제와 크게 달랐다) |
| 더한 행 | 사용자 패널 목록 읽기 실패 행(5.2), 키맵 프로젝트 칸을 `B I C M P Q` 행으로 나눈 6행(10.2), 마이그레이션 레이아웃의 본문 구성 한 줄(2.5), 컨텍스트 경로 전역값 문단(2.1), 14절 대조 2행, 발견 사항 F28 |
| 없앤 행 | 없음 (옛 서술은 현재 동작으로 바꿨다) |
| 새 발견 사항 | `shell-F28` 컨텍스트 경로 접두어가 남지 않은 곳(`partial-notifications.html:61`, `help/toc.html:76,89`, `attachment-file.html:10`)과 `requestPath` 비교(추측). `shell-scripts.md`에 `shell-F29`(`Request.js` 기본 알림과 마일스톤 보기 자체 알림이 겹칠 수 있음, 추측) |
| 범위 점검 표 | 현재 템플릿 목록과 같다(변경 없음). 레이아웃 사용처 개수는 `project-layout` 56개(전에 57개로 적혀 있었다)로 바로잡았다 |
