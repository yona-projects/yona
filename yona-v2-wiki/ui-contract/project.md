---
title: 프로젝트 화면 계약 (공통, 헤더, 메뉴, 홈, 목록, 생성, 가져오기)
type: ui-contract
sources:
  - src/main/resources/templates/project/header.html
  - src/main/resources/templates/project/menu.html
  - src/main/resources/templates/project/home.html
  - src/main/resources/templates/project/partial-dashboard.html
  - src/main/resources/templates/project/partial-history.html
  - src/main/resources/templates/project/partial-readme.html
  - src/main/resources/templates/project/list.html
  - src/main/resources/templates/project/create.html
  - src/main/resources/templates/project/importing.html
  - src/main/resources/templates/project/statistics.html
  - src/main/resources/templates/project/watchers.html
  - src/main/kotlin/dev/yona/web/project/
  - src/main/kotlin/dev/yona/web/access/
  - src/main/kotlin/dev/yona/access/AccessControl.kt
  - src/main/resources/static/assets/javascripts/esm/service/project.*.js
updated: 2026-11-11
---

# 프로젝트 화면 계약 (1/2)

설정 계열 화면(설정, 구성원, 이전, 삭제, 저장소 종류 변경, 웹훅, 라벨, 배포 키, LFS, 규칙 묶음)은 [project-settings.md](project-settings.md), 마일스톤은 [milestone.md](milestone.md)에 있다. 이 파일은 영역 요약, 공통 계약, 프로젝트 머리와 메뉴, 홈, 목록, 생성, 가져오기, 통계, 구독자 목록, 맨 끝에 `project-F*` 발견 사항과 범위 점검을 둔다.

이미 있는 글: [web/project-home.md](../web/project-home.md), [web/project-layout.md](../web/project-layout.md), [web/project-list.md](../web/project-list.md), [web/project-create.md](../web/project-create.md), [web/project-checks.md](../web/project-checks.md), [web/watch-and-favorite.md](../web/watch-and-favorite.md), [subsystems/access-control.md](../subsystems/access-control.md), [architecture/ui-shortcuts.md](../architecture/ui-shortcuts.md). 같은 내용은 다시 쓰지 않고 새로 확인한 사실과 어긋난 곳만 적는다. 줄 번호는 `ui-improvements` 브랜치 기준이다.

## 0. 영역 요약 (화면 목록)

| 템플릿 | URL (메서드) | 컨트롤러 | 용도 |
|---|---|---|---|
| `project/header.html` (조각) | 모든 프로젝트 화면 | `layout/project-layout.html:18`이 끼움 | 로고, 경로, 즐겨찾기 별, 가입 신청, 구독 |
| `project/menu.html` (조각) | 프로젝트 홈, 설정, 마일스톤 화면 | 각 화면 템플릿이 끼움 | 탭 메뉴, 단축키 등록 |
| `project/home.html` | `GET /{user}/{project}` | `ProjectHomeController.home` (`web/project/ProjectHomeController.kt:55`) | 설명, 복제 주소, README, 이력, 대시보드 탭, 구성원, 마일스톤 현황 |
| `project/partial-readme.html` | 홈 `tabId=readme` | 같음 | README 본문 또는 안내 |
| `project/partial-history.html` | 홈 `tabId=history` | 같음 (`ProjectHomeTabsAssembler.historyRows`) | 최근 활동 |
| `project/partial-dashboard.html` | 홈 `tabId=dashboard` | 같음 (`ProjectHomeTabsAssembler.dashboard`) | 담당자, 마일스톤, 라벨, 풀 리퀘스트 집계 |
| `project/list.html` | `GET /projects` | `ProjectListController.projects` (`:47`) | 공개 프로젝트 목록과 검색 |
| `project/create.html` | `GET /projectform`, `POST /projects` | `ProjectCreateController.form` (`:53`), `.create` (`:60`) | 새 프로젝트 |
| `project/importing.html` | `GET /_import`, `POST /_import` | `ImportController.form` (`:54`), `.create` (`:58`) | 저장소 가져와 프로젝트 만들기 |
| `project/statistics.html` | `GET /{user}/{project}/statistics` | `web/help/StatisticsController.kt:27` | "Under Construction" 한 줄 |
| `project/watchers.html` | `GET /{user}/{project}/watchers` | `ProjectWatchersController.watchers` (`:34`) | 구독자 목록 |
| `project/partial-settingmenu.html` (조각) | 설정 계열 화면 | 각 화면이 끼움 | 설정 하위 탭 (project-settings.md) |
| `project/setting.html` 외 설정 계열 14개 | project-settings.md 참조 | | |

템플릿 없는 이동용 경로: `GET /{user}/{project}/go`(`ProjectGoController.kt:30`, 이슈 메뉴가 켜져 있으면 `forward:.../issues`, 아니면 게시판으로 303, 둘 다 꺼져 있으면 홈으로 303, 읽기 권한 필요).

## 1. 공통 계약

### 1.1 접근 검사 체인
- `AccessChainInterceptor.preHandle` (`web/access/AccessChainInterceptor.kt:29`)가 컨트롤러 메서드에 붙은 어노테이션을 **메서드에 쓴 순서대로 먼저, 그다음 클래스에 쓴 것**을 실행한다(`annotationsInPlayOrder`, `:47`). 하나라도 막으면 나머지는 실행하지 않는다.
- 이 영역의 모든 컨트롤러는 클래스에 `@AnonymousCheck`가 있다. 그래서 메서드의 `@IsAllowed`가 먼저 돈다. 사이트가 익명 접근을 막은 경우 익명은 로그인 폼 303이 아니라 프로젝트 검사의 **403 로그인 화면**을 먼저 받는다(`ProjectCheckStage.kt:171-203`, `ErrorViews.loginRequired`).
- `@IsAllowed(연산, 리소스종류)`: 프로젝트를 찾고(없으면 익명은 403 로그인 화면, 로그인 사용자는 403 `error.forbidden.or.notfound`), 읽기 권한이 없으면 403(익명은 로그인 화면), 그다음 대상 리소스를 경로 구간으로 찾아 연산을 검사한다. 대상이 없으면 404(`error.notfound`, 프로젝트 레이아웃), 연산이 거부되면 403(`error.forbidden`, 프로젝트 레이아웃) (`ProjectCheckStage.kt:207-216`).
- `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`: 익명이면 `303 /users/loginform?redirectUrl=<요청 경로>`와 경고 `user.login.alert` (`AnonymousCheckStage.kt:288-297`).
- `@RequiresVcs(능력)`: 저장소 종류가 능력을 지원하지 않으면 400 `error.badrequest.only.available.for.git` (`ProjectCheckStage.kt:121-123`).
- `@DefaultProjectCheck`: 프로젝트 존재와 읽기 권한만 본다. `@GuestProhibit`: 게스트 계정이면 홈 `/`로 303과 경고 `error.forbidden.or.not.allowed`.
- 권한 판정의 실제 규칙은 `access/AccessControl.kt`이다. 이 영역에서 쓰는 규칙만 요약한다.

| 연산, 리소스 | 허용 | 근거 |
|---|---|---|
| 프로젝트 READ | 사이트 관리자, 프로젝트 멤버(역할 무관), 그룹 관리자, 공개 프로젝트이고 게스트 계정이 아닌 사용자(익명 포함, 사이트가 익명 접근을 허용할 때), 그룹(조직) 프로젝트의 그룹 구성원(공개 또는 보호) | `AccessControl.kt:124-145` |
| 프로젝트 UPDATE, DELETE | 사이트 관리자, 프로젝트 `MANAGER` 역할, 그룹 관리자. 일반 멤버는 불가 | `:159-170` |
| 프로젝트 WATCH | 공개 프로젝트의 로그인 사용자, 멤버, 그룹 관리자, 그룹 구성원, 사이트 관리자 | `:147-152` |
| 프로젝트 LEAVE | 로그인했고 소유자가 아닌 멤버. 사이트 관리자는 멤버가 아니어도 항상 참(`:111`이 먼저 돈다) | `:154-157` |
| 마일스톤, 라벨 등 프로젝트 리소스 UPDATE | 사이트 관리자, 그룹 관리자, 프로젝트 관리자, 멤버, 그룹 구성원 | `:175-222` |
| 프로젝트 리소스 DELETE (마일스톤, 라벨) | 사이트 관리자, 그룹 관리자, 프로젝트 관리자, 멤버. 그룹 구성원만으로는 불가 | `:218` |
| 프로젝트 안 리소스 만들기 (`isProjectResourceCreatable`) | 로그인 필수. 사이트 관리자, 그룹 관리자, 멤버, 그룹 구성원은 모두 가능, 공개 프로젝트의 비멤버는 이슈, 게시글, 댓글, 포크, 코드 댓글, 리뷰 댓글만 | `:65-77` |

### 1.2 CSRF
읽기 요청이 아닌 모든 요청은 토큰이 필요하다. 서버는 `XSRF-TOKEN` 쿠키를 심고(`security/CsrfSupport.kt:33`), `common/yona.Csrf.js`가 XHR, `fetch`에는 `X-XSRF-TOKEN` 헤더를, 폼 제출에는 숨은 `_csrf` 필드를 실어 준다. `Authorization`, `Yona-Token` 헤더가 있는 요청과 API, Git, SVN, LFS 경로는 대상이 아니다(`CsrfSupport.kt:16-30`). 이 영역 템플릿의 폼은 토큰 필드를 직접 쓰지 않는다.

### 1.3 응답 규칙
- 성공한 폼 제출은 `RedirectSupport.see`로 **303 See Other**(필요하면 일회성 메시지 `warning`, `info`, `success`, `error`를 실음)이다 (`web/RedirectSupport.kt`).
- Ajax(`X-Requested-With: XMLHttpRequest`) 요청에는 삭제, 이전, 저장소 종류 변경이 **204 + `Location` 헤더**로 답하고 스크립트가 직접 이동한다(`AjaxRequests.isXhr`).
- `data-request-method` 요소(`esm/Request.js`)는 `fetch`로 보내고 응답이 `!ok`이면 `request:error` 이벤트만 낸다. 이 영역에는 그 이벤트 수신자가 없어 **화면에 아무 표시도 없다**(project-F9). 2xx이면 204+`Location`은 이동, 그 밖은 `reload`.
- 오류 화면: `ErrorViews.notFound/forbidden/badRequest` (`web/error/ErrorViews.kt`)가 프로젝트 레이아웃 안의 오류 페이지(HTML)를 해당 상태 코드로 돌려준다.

### 1.4 프로젝트 화면의 조립
`layout/project-layout.html:8-23`: 제목은 `title`이 비면 `이름 - 소유자`, 있으면 `(메시지 번역 또는 그대로) - 소유자/이름`이다. 본문은 `common/navbar`, `project/header`, 화면 내용, `common/footer` 순이다. 설정 계열과 마일스톤 화면은 내용 맨 앞에 `project/menu`를 끼운다. 모델 `projectView`(`ProjectViewContext`, `web/project/ProjectViewContext.kt`)가 템플릿이 쓰는 값(`canUpdate`, `canWatch`, `guest`, `menuSetting`, `vcs.*` 등)을 지연 계산으로 준다.

### 1.5 반응형 규칙
스타일은 `app/assets/stylesheets/scss/_responsive.scss`에서 빌드한다. 폭 720px 이하에서(`:1`): `.hide-in-mobile`은 숨김(`:302`), `.show-in-mobile`은 표시(`:290`), `.span-right-pane`은 숨김(`:261` 근처), `.span-left-pane`은 100%, 메뉴는 `.menu-name`을 숨기고 `.short-menu`(한 글자 H, C, I, P, R, M, B, W)를 보인다(`:273-279`), `.watch-btn`은 숨김(`:311`). 720px보다 넓으면 `.short-menu`는 숨김(`_page.scss:79`).

### 1.6 단축키 등록 구조
`esm/ShortcutKey.js`는 키 문자열 하나에 **핸들러 하나**만 둔다(`attachHandler`가 덮어씀). `setKeymapLink`가 만드는 핸들러는 입력란(`INPUT`, `TEXTAREA`)에 초점이 있으면 아무것도 하지 않고, 아니면 `document.location.href`를 바꾼다. 같은 키를 나중에 등록한 쪽이 이긴다. 등록 순서는 페이지에서 `$(document).ready` 블록이 나온 순서이다(레이아웃 `common/scripts.html`의 `U`, `A` 다음에 `project/menu`의 블록, 그다음 화면별 블록). 수정 키(`CTRL`, `ALT`, `SHIFT`)가 함께 눌리면 키 문자열이 달라져 일치하지 않는다.

### 1.7 메시지 키 점검
이 영역 템플릿이 쓰는 고정 키 278개를 `messages.properties`와 `messages_ko_KR.properties`에 대조했다. 한국어 번들에 없는 키는 `project.webhook.includeGitPush` 하나이다(project-F1). 동적 키(`project.merge.*`, `user.role.*`, `milestone.state.*`, `issue.state.*`, `common.order.*`, `project.new.vcsType.*`)는 모두 한국어 번들에 있다. 번들은 `fallback-to-system-locale: false`이고 `use-code-as-default-message: true`이다(`application.yml:84-90`), 없는 키는 기본 번들(영어)로 떨어진다.

## 2. 프로젝트 머리 (`project/header.html`)

### 2.1 개요, 레이아웃
`ProjectViewContext`(`view`)를 받는 조각. 배경 이미지(`view.backgroundUrl`)가 깔린 바깥 상자 안에 아바타(로고), 경로 상자(소유자 / 이름, 별, 잠금, G 표시, 원본 프로젝트), 오른쪽 도구(가입 신청, 구독)가 있다. 로고와 배경은 프로젝트에 올린 첫 첨부(`/files/{id}`)이고 없으면 `/assets/images/project_default_logo.png`, `/assets/images/project_default.jpg` (`ProjectViewContext.kt:183-184`). 소유자와 `/` 구분은 `hide-in-mobile`이다(`header.html:14-15`). 구독 영역(`.watch-btn`)은 720px 이하에서 숨는다.

### 2.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답, 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 소유자 링크 | 링크 | 항상 | `/{owner}` 이동 | GET | 사용자 또는 조직 페이지 | 읽기 | `header.html:14` |
| 프로젝트 이름 링크 | 링크 | 항상 | `/{owner}/{name}` | GET | 홈 | 읽기 | `:16` |
| 별(즐겨찾기) | 아이콘 | 항상 보임(로그인 조건 없음) | 클릭하면 별 표시 전환 | `POST /user/favoriteProjects/{projectId}` (`data-project-id`) | `{projectId, favored}`로 `starred` 클래스 전환. 실패하면 `Update failed: <reason>` 경고 | 로그인(익명은 403 본문 없음) | `:17-19`, `esm/Usermenu.js:107-120`, `web/api/FavoriteApiController.kt:63-71` |
| 잠금 아이콘 | 표시 | 비공개 프로젝트 | 없음 | | | | `:20` |
| `G` 표시 | 표시 | 보호(그룹) 프로젝트, title "Group Project" | 없음 | | | | `:23` |
| 원본 링크 | 링크 | 포크한 프로젝트(`view.forked`) | 원본 홈으로 | GET | | 읽기 | `:27-32` |
| 가입 신청 드롭다운 | 단추+메뉴 | `view.guest`(로그인했고 이 프로젝트 역할이 없으며 사이트 관리자가 아님) | 메뉴 열기 | | | | `:36-69` |
| 가입 신청 (`#enrollBtn`) | 링크 | 게스트이고 아직 신청 안 함 | 클릭을 가로채 POST 후 새로고침 | `POST /{owner}/{name}/enroll` | 200이면 `reload`. 실패: 403 `error.forbidden`, 그 밖의 4xx `user.enroll.failed.client`, 5xx `…failed.server`, 네트워크 `…failed.network`(토스트 3초) | `@DefaultProjectCheck`(읽기) → 클래스 `@AnonymousCheck(requiresLogin)`. 게스트가 아니면 400 | `:62`, `project.Global.js:99-131`, `EnrollController.kt:46-62` |
| 신청 취소 (`#enrollBtn`) | 링크 | 게스트이고 이미 신청함 | 같은 방식 | `POST /{owner}/{name}/cancel/enroll` | 같음 | 같음 | `:46`, `EnrollController.kt:65-80` |
| 구독자 수 (`.watcher-count`) | 링크 | `view.canWatch` | 구독자 목록으로 이동. 구독 중이면 초록(`btn-success`), 아니면 회색. 툴팁 `project.watcher.number` | GET `/{owner}/{name}/watchers` | 구독자 목록 | 읽기 | `:72` |
| 알림 설정 변경 | 링크 | `canWatch` | `/user/editform/notifications#<projectId>` | GET | 사용자 알림 설정 | 로그인 | `:85` |
| 구독 / 구독 해제 (`.watchBtn`) | 링크 | `canWatch`, 구독 상태에 따라 하나 | 클릭을 가로채 POST 후 새로고침 | `POST /{owner}/{name}/watch` 또는 `/unwatch` | 200이면 `reload`, 실패하면 토스트 "Server Error". 구독 해제는 이 프로젝트의 알림 종류 설정도 지운다 | 서버는 **READ**만 검사한다(화면은 WATCH). 클래스 `@AnonymousCheck(requiresLogin)` | `:89-96`, `project.Global.js:79-94`, `WatchProjectController.kt:47-67` |
| 구독 드롭다운 단추 | 단추 | `canWatch` | 메뉴 열기. 글자는 상태에 따라 `project.watch`/`project.unwatch` | | | | `:99` |

조건 `canWatch`는 사이트 관리자에게 항상 참이다(`AccessControl.kt:111`).

### 2.3 스크립트
`project/menu.html:71`이 `esm/service/project.Global.js`를 모듈로 불러오고 `$yona.loadModule("project.Global", {htKeyMap, sRepoURL})`를 호출한다. `.watchBtn, #btnWatch`와 `#enrollBtn`의 클릭만 묶는다(`project.Global.js:61-73`). `#forkedFrom`, `.project-header-toggle-btn`은 찾지만 쓰지 않는다. `sRepoURL` 옵션은 받기만 하고 읽지 않는다(`project.Global.js:53-55`).

## 3. 프로젝트 메뉴 (`project/menu.html`)

조각 `projectMenu(view, menuType, menuCSS)`. `menuCSS`는 쓰지 않는다. 탭마다 `menuType`이 같으면 `active`.

| 탭 | 보이는 조건 | 링크 | 근거 |
|---|---|---|---|
| 홈 (H) | 항상 | `/{o}/{n}` | `menu.html:9-13` |
| 코드 (C) | `menuSetting.code` 그리고 `codeAccessible` | `/{o}/{n}/code` | `:14-20` |
| 이슈 (I) | `menuSetting.issue` | `/issues`. 열린 이슈 수 배지(0이면 숨김) | `:21-25` |
| 풀 리퀘스트 (P) | `menuSetting.pullRequest` 그리고 `vcs.pullRequests` 그리고 `codeAccessible` | `view.pullRequestPath`(포크한 프로젝트는 `/sentPullRequests`, 아니면 `/pullRequests`). 열린 개수 배지 | `:26-32`, `ProjectViewContext.kt:151` |
| 리뷰 (R) | `menuSetting.review` 그리고 `codeAccessible` | `/reviews`. 열린 스레드 수 배지 | `:33-40` |
| 마일스톤 (M) | `menuSetting.milestone` | `/milestones` | `:41-45` |
| 게시판 (B) | `menuSetting.board` | `/posts`. 글 수 배지 | `:46-51` |
| 위키 (W) | `menuSetting.wiki == true` | `/wiki` | `:52-56` |
| 설정(톱니) | `view.canUpdate`(프로젝트 관리자, 그룹 관리자, 사이트 관리자) | `/settingform`. 가입 신청 수 배지 | `:58-68` |

`codeAccessible`는 코드 열람을 구성원으로 제한한 프로젝트에서 비구성원에게 거짓이다(`ProjectViewContext.kt:143`).

단축키(`menu.html:72-100`, `$yona.loadModule("project.Global", {htKeyMap})` → `ShortcutKey.setKeymapLink`): `H` 홈, `B` 게시판(`menuSetting.board`), `C` 코드(`menuSetting.code`), `I` `/issues?state=open`(`menuSetting.issue`), `M` 마일스톤(`menuSetting.milestone`), `P` 풀 리퀘스트(`menuSetting.pullRequest and vcs.pullRequests`), `Q` 설정 화면(`view.settingShortcutEnabled`, 프로젝트 `MANAGER` 역할일 때만 켬, `ProjectViewContext.kt:161`). 입력란에 초점이 있으면 동작하지 않는다. `R`, `W`에는 단축키가 없다. 단축키 목록의 조건에는 `codeAccessible`이 없어서, 코드 열람 제한 프로젝트의 비구성원이 `C`나 `P`를 누르면 403 화면으로 간다(project-F6).

## 4. 프로젝트 홈 (`project/home.html`)

### 4.1 개요
`GET /{user}/{project}?tabId=readme|history|dashboard` (`ProjectHomeController.kt:55`, `@IsAllowed(READ)`). 모델: `title`(`이름 - 홈 |:| 설명`), `projectView`, `tabId`(공백이면 `readme`, 그 밖의 값은 그대로 받음), `overviewHtml`(설명을 마크다운으로 렌더링, 없으면 `null`), `readmeFileName`, `readmeHtml`, `members`(사이트 관리자 제외, 이름 오름차순), `milestone`(메뉴가 켜져 있고 마감일이 가장 이른 열린 마일스톤의 현황, 없으면 `null`), `history`(`tabId=history`일 때만), `dashboard`(`tabId=dashboard`일 때만). 방문 기록을 남긴다(`recentVisits.visits`). 화면은 `layout/project-layout`의 `PROJECT_HOME`.

### 4.2 레이아웃
`page-wrap-outer > project-page-wrap`. 폭이 좁을 때만 보이는 경로(`project-breadcrumb hide show-in-mobile`, `:16-23`). 위쪽 `project-home-header`: 설명(`col-lg-9`), 복제 상자(`col-lg-3 hide-in-mobile`). 아래 두 단: 왼쪽 `col-lg-9 span-left-pane`(탭과 탭 내용), 오른쪽 `col-lg-3 span-right-pane`(회색 상자: 새 이슈와 포크 단추, 마일스톤 현황, 구성원, 탈퇴). 720px 이하에서 오른쪽 단 전체가 숨는다. 알 수 없는 `tabId`는 어느 탭도 활성이 아니고 탭 내용이 비어 있다(탭 내용은 `readme`, `history`, `dashboard` 일치 때만 나옴, `:78-86`).

### 4.3 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답, 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 설명 편집 연필 | 단추 | `canUpdate` | 설명 보기와 편집 상자를 전환(`hidden` 토글)하고 입력란에 초점 | 없음 | | 화면만(서버는 저장 때 검사) | `home.html:28`, `project.Home.js:74-76` |
| 설명 입력란 | 입력 | 편집 상자 | 감싼 `<form onsubmit="return false">`이라 **Enter로는 저장되지 않는다** | | | | `:32-33` |
| 저장 (`#descriptionSaveBtn`) | 단추 | 편집 상자 | `PUT {sURLProject}` JSON `{"overview": 값}` | `PUT /{o}/{n}`, `Content-Type: application/json` | 200 `{"overview": 값}`. 값이 있으면 `yona.Markdown.render`로 설명을 다시 그리고, 비면 입력란 플레이스홀더를 그림. 상자를 닫음. **실패하면 콘솔에만 기록하고 화면 변화 없음** | `@AnonymousCheck(requiresLogin)` → `@IsAllowed(UPDATE)`. 서버는 `overview`가 문자열일 때만 저장하고 아니면 `null`로 저장, 길이 검사 없음 | `:34`, `project.Home.js:80-100`, `ProjectHomeController.kt:93-107` |
| 취소 | 단추 | 편집 상자 | 보기로 되돌림 | | | | `:34`, `project.Home.js:77` |
| 복제 주소 입력란 `#cloneURL` | 입력(읽기 전용) | `menuSetting.code` | 클릭하면 전체 선택 | | | | `:38-39`, `project.Home.js:69` |
| 복사 `#cloneURLBtn` | 단추 | `menuSetting.code` | `yona.Clipboard.copy(주소)`, 복사되면 토스트 `code.copyUrl.copied` 1초 | 없음 | | | `:40`, `project.Home.js:61-68` |
| HTTP/SSH 전환 | 단추 둘 | `sshCloneUrl != null`(내장 SSH 서버를 켰고 Git 프로젝트) | 선택 표시를 옮기고 입력란 값을 `data-clone-url`로 바꿈(인라인 스크립트) | 없음 | | | `:42-57` |
| README / 최근 이력 / 대시보드 탭 | 링크 | 항상 | 같은 URL에 `tabId`를 달아 전체 페이지 이동(README는 `tabId` 없음) | GET | | 읽기 | `:62-74` |
| README 만들기 | 링크 | README 없음, `vcs.readmePosting` 그리고 `canCreateCommit` | `/postform?readme=true` | GET | | 서버는 글쓰기 화면에서 검사 | `partial-readme.html:12` |
| README 수정 | 링크 | README 있음, `vcs.onlineEditing` 그리고 `canCreateCommit` | 같음 | GET | | | `partial-readme.html:21` |
| 새 이슈 | 링크 | `menuSetting.issue` | `/{o}/{n}/issueform` | GET | | 보임 조건에 로그인 검사 없음 | `:94-96` |
| 포크 | 링크 | `menuSetting.code` 그리고 `vcs.fork` | `/{o}/{n}/newFork` | GET | 포크 폼 (`web/pullrequest/ForkController.kt:57`) | | `:97-101` |
| 마일스톤 현황 | 조각 | `milestone != null` | 제목 링크 `/{o}/{n}/milestone/{id}`, 마감일(닫히지 않았으면 `(until)`, 지났으면 `over`), 진행 막대, 닫힘/전체 | GET | | | `home.html:103-105`, `milestone/partial-status.html` |
| 구성원 추가 | 링크 | `canUpdateLabels`(멤버 이상) | `/{o}/{n}/members` | GET | 서버의 구성원 화면은 **프로젝트 관리자만**(project-F7) | | `:109` |
| 구성원 한 줄 | 링크 둘 | 항상 | `/{loginId}` 사용자 페이지 | | | | `:113-120` |
| 프로젝트 탈퇴 `#projectLeaveBtn` | 단추 | `canLeave` | 확인 모달 `#alertLeave` 열기 | 없음 | | | `:124`, `project.Home.js:102-104` |
| 모달 "예" `#leaveBtn` | 단추 | 모달 | `DELETE {leaveUrl}` (`/{o}/{n}/member/{내 id}/delete`) | 성공 JSON `{location}`으로 `replace`. 오류: 403 `project.member.notExist`, 404 `project.is.empty`, 그 밖 `error.badrequest`를 `$yona.alert` | 읽기 권한과 (본인이거나 UPDATE). 소유자는 403 `project.member.ownerCannotLeave` | `:138`, `project.Home.js:107-125`, `ProjectMemberController.kt:132` |
| 모달 "아니오", 닫기 X | 단추 | 모달 | 닫기(`data-bs-dismiss`) | | | | `:131,139` |

### 4.4 탭 내용

**README** (`partial-readme.html`): README가 없으면 안내(`project.readme`, SVN이면 `project.svn.readme`)와 만들기 링크. 있으면 파일 이름 머리글과 본문(`readmeHtml`, `markdown-wrap`). 코드 메뉴가 꺼져 있고 README 게시글이 있으면 게시글 본문을, 아니면 저장소 파일을 렌더링한다(`ProjectHomeController.kt:113-117`).

**최근 이력** (`partial-history.html`): 커밋, 열린 이슈, 게시글, 풀 리퀘스트를 각각 최근 10건씩 모아 시각 내림차순으로 보인다(`ProjectHistoryService.kt:52-81`, `LIMIT=10`, 전체 상한 없음, 최대 40줄). 줄: 아바타(사용자 페이지 링크), 머리글 HTML(사용자, 종류 문구, 짧은 제목 링크와 제목 링크), 상대 시각(툴팁은 절대 시각). 목록이 비어도 별도 문구가 없다.

**대시보드** (`partial-dashboard.html`, `DashboardView`): 왼쪽 단에 `issueMenu`가 켜졌으면 "담당자별 열린 이슈"(담당자별 건수, 아바타, 비율 막대, "담당자 없음" 줄. 비면 "이슈 없음"과 새 이슈 링크), "마일스톤별 열린 이슈"(완료율 막대, "마일스톤 없음" 줄. 비면 안내와 새 마일스톤 링크), `pullRequestArea`(`menuSetting.pullRequest and vcs.pullRequests`)가 켜졌으면 최근 풀 리퀘스트 10건(`RECENT_PULL_REQUESTS=10`, 기여자 링크, 제목, 라벨, 담당자, 경과 시간, "더 보기 (열린 개수)" 링크 `/pullRequests`, 비면 안내와 새 풀 리퀘스트 링크). 오른쪽 단: "라벨별 열린 이슈"(분류별 라벨, 건수, 링크 `issues?state=open&labelIds=`). 링크는 `issueBase`(`/issues?state=open`)에 `&assigneeId=`(없음은 -1), `&milestoneId=`(없음은 -1), `&labelIds=`를 붙인다. 새 이슈, 새 마일스톤, 새 풀 리퀘스트 링크는 `target="_blank"`이다(`:19,62,114`).

### 4.5 스크립트, 팝업, 단축키
- 모듈 `project.Home`(`project.Home.js`), `common/markdown` 조각(마크다운 렌더러 설정). 옵션 `sURLProject`.
- 팝업은 탈퇴 확인 모달 하나. 닫기: 단추 `data-bs-dismiss`, 배경 클릭, Esc(부트스트랩 기본).
- 화면 범위 단축키는 없다(메뉴의 단축키만).
- `#leaveBtn` 클릭 핸들러를 `#projectLeaveBtn`을 누를 때마다 새로 붙인다(`project.Home.js:107`). 모달을 취소하고 다시 열어 "예"를 누르면 요청이 여러 번 나간다(project-F10).

### 4.6 권한 행렬 (홈)

| 기능 | 비로그인 | 로그인 비멤버 | 멤버 | 프로젝트 관리자 | 그룹 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|
| 홈 보기 | 공개 프로젝트만(사이트가 익명 허용 시) | 공개, 그룹 구성원이면 보호도 | 허용 | 허용 | 허용 | 허용 |
| 설명 편집 단추, 저장 | 숨김, 서버 303 로그인 | 숨김, 403 | 숨김, 403 | 보임, 허용 | 보임, 허용 | 보임, 허용 |
| 구성원 추가 링크 | 숨김 | 숨김 | 보임(눌러도 구성원 화면은 403) | 보임 | 보임 | 보임 |
| 탈퇴 단추 | 숨김 | 숨김 | 보임(소유자 제외) | 보임(소유자 제외) | 숨김(멤버가 아니면) | 항상 보임 |
| 가입 신청 | 숨김 | 보임(역할 없음) | 숨김 | 숨김 | 보임(역할이 없으면) | 숨김 |
| 구독 | 숨김 | 공개 프로젝트에서 보임 | 보임 | 보임 | 보임 | 보임 |

### 4.7 빈 상태, 경계값
설명이 없으면 `project.description.placeholder`. 이력 `LIMIT=10`, 대시보드 풀 리퀘스트 10건. 설명 입력란에 `maxlength`가 없고 서버도 길이를 검사하지 않는다(설정 화면 설명은 250자 제한이 클라이언트에만 있다).

### 4.8 오류
읽기 거부: 403(익명은 로그인 화면). 없는 프로젝트: 익명 403 로그인 화면, 로그인 사용자 403 `error.forbidden.or.notfound`. 설명 저장: 익명은 303 로그인, 권한 없음 403 오류 페이지, 잘못된 JSON은 400(전역 처리기).

## 5. 프로젝트 목록 (`project/list.html`)

### 5.1 개요
`GET /projects?filter=&pageNum=&labelIds=` (`ProjectListController.kt:47`). 클래스 `@AnonymousCheck`, 메서드 `@GuestProhibit`(게스트 계정은 `/`로 303과 경고). `hide-project-listing` 설정이 켜지면 403(`error.auth.unauthorized.waringMessage`). `Accept`가 JSON을 선호하면(`HttpPreference.isJsonPreferred`) `소유자/이름` 문자열 배열(최대 1000개, 더 있으면 `Content-Range: items 1000/전체`), 아니면 HTML. 응답에 항상 `Vary: Accept`. 모델: `rows`, `totalElements`, `totalPages`, `filter`. 쪽 크기 `PAGE_SIZE=10`, 정렬은 만든 날짜 내림차순(`ProjectListService.kt:35-43,93-96`). `pageNum < 1`이면 404.

### 5.2 레이아웃
`layout/site-layout`의 `PROJECTS`. 위쪽 탭 둘(공개 프로젝트 목록(활성), 그룹 목록 `/orgs`). 검색 상자, 목록 `ul.all-projects`, 아래에 `#pagination`.

### 5.3 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 검색 상자, 돋보기 단추 | 폼 | 항상(`autofocus`) | `GET /projects?filter=값` 제출 | GET | 1쪽부터 다시 | `list.html:27-31` |
| 프로젝트 이름, 로고 | 링크 | 읽을 수 있는 행 | 프로젝트 홈 | GET | | `:45,51` |
| 원본 프로젝트 | 링크 | 포크 | 원본 홈 | GET | | `:52-57` |
| 소유자 | 링크 | 읽을 수 있는 행 | `/{owner}` | | | `:64` |
| 라벨 | 링크 | 라벨이 있을 때 | `/projects?labelIds={id}` | GET | 그 라벨 필터 | `:60` |
| 구성원 아바타 | 링크 | 공개 프로젝트 | `/{loginId}` | | | `:70-77` |
| 쪽 이동 | 스크립트 | `totalPages > 0` | `yona.Pagination.update($("#pagination"), totalPages)`가 이전, 쪽 번호 입력, `/ 전체`, 다음을 그림. 쿼리 `pageNum` | GET | | `:105`, `esm/Pagination.js` |

읽을 수 없는 행(`displayPrivateRepositories` 설정이 꺼져 있고 읽기 권한 없음)은 흐린 상자에 영어 고정 문구 "You do not have permission to view this project's information"과 기본 로고만 보인다(`:85-94`, project-F12). 설정이 켜진 사이트에서는 모든 행이 읽을 수 있는 것으로 그려진다(`ProjectListController.kt:77`). 목록이 비면 `project.is.empty`(`ico-err1`).

### 5.4 권한과 응답 차이
로그인한 일반 사용자는 공개와 자신이 읽을 수 있는 프로젝트를, 사이트 관리자는 전부(`canSeeAll`)를 목록 조건으로 받는다. 게스트 계정은 303 `/`. 익명은 사이트가 익명 접근을 막으면 `@IsAllowed`가 없으므로 클래스 `@AnonymousCheck`의 303 로그인 폼을 받는다.

## 6. 새 프로젝트 (`project/create.html`)

### 6.1 개요
`GET /projectform?owner=` (`@AnonymousCheck(requiresLogin, 안내)`, `ProjectCreateController.kt:53`), `POST /projects` (`:60`). 모델: `form`, `fieldErrors`(번역된 메시지), `errorsJson`, `ownerAvatarUrl`, `organizations`(내가 `ORG_ADMIN`인 조직), `ownerIsOrganization`, `privateIsDefault`(설정 `project.default.scope.when.create`가 `private`), `defaultMenus`(설정 `project.default.menus.when.create`, 기본값은 일곱 메뉴 전부), `vcsTypes`(GIT, Subversion, Mercurial). 레이아웃은 `site-layout`의 `NEW_PROJECT`.

### 6.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 근거 |
|---|---|---|---|---|
| 소유자 `#project-owner` | 선택 | 항상 | 나 + 관리하는 조직. 바꾸면 `_onChangeProjectOwner`가 조직이 아니면 "보호" 라디오를 감추고(선택돼 있었으면 공개로) 조직이면 보임 | `create.html:37-50`, `project.New.js:106-117` |
| 이름 `#project-name` | 입력(250자) | 항상 | 포커스를 잃으면 앞뒤 공백 제거와 공백을 `-`로 바꿈. 제출 때 형식 검사 | `:63`, `project.New.js:74-76,134-166` |
| 설명 | 텍스트상자 | 항상 | 길이 제한 없음 | `:73` |
| 공개 범위 | 라디오 셋 | 보호는 소유자가 조직일 때만 보임 | PUBLIC, PROTECTED, PRIVATE. 기본: `privateIsDefault`이면 PRIVATE, 아니면 PUBLIC | `:85-111` |
| 저장소 종류 `#vcs` | 선택 | 항상 | Subversion을 고르면 경고 `#svn`을 보이고 PR 체크를 켜고 감추려는 처리기가 있다 | `:123-134`, `project.New.js:84-104` |
| 메뉴 체크박스 일곱 | 체크박스 | 기본값은 `defaultMenus` | 코드 끄면 PR, 리뷰도 끔. PR이나 리뷰 켜면 코드도 켬 | `:142-169`, `project.New.js:193-223` |
| 만들기 | 제출 | | 클라이언트 검사 통과 시 `NProgress.start()` 후 제출 | `:176`, `project.New.js:134-166` |
| 취소 | 링크 | | `/` | `:178` |
| "Git에서 가져오기" | 링크 | | `/_import?owner=<선택>` | `:22` |

제출: `POST /projects` 폼 필드 `owner, name, overview, projectScope, vcs`와 켠 메뉴 체크박스의 `code, issue, pullRequest, review, milestone, board, wiki`(값 `true`, `on`, `yes`, `1`만 켜진 것으로 봄, `ProjectCreateController.kt:84-93`).

### 6.3 서버 검증과 응답
순서: (1) 만들 권한(`checkPermission`): 비로그인이면 403 `project.error.noPermission`(이름 자리에 "존재하지 않는 사용자" 문구), 소유자가 조직이면 그 조직의 관리자여야 함, 아니면 403 (`ProjectCreateService.kt:49-54`). (2) 검증(`:68-92`): 이름 필수, 문자 규칙 `^[a-zA-Z0-9-_.가-힣]+$`, 예약어 `.`, `..`, `.git`와 `.wiki`로 끝나는 이름(대소문자 무시), 공개 범위 값, 소유자가 사용자도 조직도 아니거나 사용자인데 본인이 아니면 `project.owner.invalidate`, 같은 소유자에 같은 이름(대소문자 무시)이면 `project.name.duplicate`. 이름 오류는 안내 `project.name.alert`, `project.name.reserved.alert`로 바꿔 준다. 실패하면 **400**으로 같은 폼을 다시 그리고(입력값 유지) `errorsJson`을 스크립트가 받아 필드마다 팝오버로 보인다(`project.New.js:174-191`, 위쪽 첫 메시지만). 성공하면 **303 `/{owner}/{name}`**. 클라이언트 검사(`project.New.js:134-166`)는 이름 형식과 예약어만 보고 `.wiki` 접미사는 모른다(서버 검사로만 걸림, project-F13).

### 6.4 권한
익명은 폼 접근이 303 로그인(안내 `user.login.alert`), 직접 POST는 403. 로그인 사용자는 누구나 자기 이름으로, 조직 관리자는 조직 이름으로 만들 수 있다. 만든 프로젝트에는 만든 사람이 `MANAGER`, 사이트 관리자 계정이 `SITEMANAGER` 역할로 들어가고 만든 사람이 구독자가 된다(`ProjectCreateService.kt:116-123`).

## 7. 저장소 가져오기 (`project/importing.html`)

### 7.1 개요
`GET /_import?owner=` (로그인 필수), `POST /_import`. 모델은 생성 화면과 같고 `repoAuthChecked`(인증 입력을 펼칠지)와 `vcsTypes`(GIT, Mercurial만, Subversion 제외)가 더해진다(`ImportController.kt:78-96,106-110`). 폼 필드: `owner, name, overview, projectScope, vcs, url, authId, authPw`, 메뉴 `code, issue, pullRequest, review, milestone, board`.

### 7.2 동작 요소 표

| 요소 | 동작 | 근거 |
|---|---|---|
| 저장소 종류 `#vcs` | 바꾸면 인라인 스크립트가 제목(`#importTitle`), 주소 칸 이름(`#importUrlLabel`), 입력란 플레이스홀더를 Git/Mercurial 문구로 교체(`data-git`, `data-hg`). 같은 선택에 `project.New.js`의 처리기도 붙는다 | `importing.html:215-228` |
| 주소 `#url` | 필수. 제출 때 비어 있으면 `project.import.error.empty.url` 팝오버 | `:38`, `project.New.js:153-157` |
| "인증이 필요합니다" 체크 `#useRepoAuth` | 켜면 `#repoAuth`가 슬라이드로 펼쳐지고 입력(`authId`, `authPw`)이 활성, 끄면 비활성 | `:41-60`, `project.New.js:78-82` |
| 소유자, 이름, 설명, 공개 범위, 메뉴 | 생성 화면과 같음 | `:63-200` |
| 위키 체크박스 | 항상 체크된 채 그려지고(`checked="checked"`) 서버가 읽지 않는다(project-F3) | `:195-198` |
| 만들기 | 제출 | `:204-206` |
| "새 프로젝트" 링크 | `/projectform?owner=` | `:23` |

### 7.3 서버 규칙과 응답
`ImportController.create` (`:58-90`): 전역 리소스 만들기 권한이 없으면 403 본문 `'이름' has no permission`(영어 고정 문장). 서비스(`ProjectImportService.import`)는 이름 규칙, 공개 범위, 저장소 종류(비면 Git, Git/Mercurial이 아니면 `error.invalid`), 소유자(사용자이면 본인, 조직이면 조직 관리자가 아니면 403 `project.error.noPermission`), 이름 중복, 주소(비면 `project.import.error.empty.url`, 허용 외 `project.import.error.wrong.url`)를 본다. 주소는 `http`, `https`, `ssh`, `git` 또는 `사용자@호스트:경로`만 받고 `file:`, 경로, 서버 안 주소(루프백, 링크 로컬, 사설망)는 거절한다(`importAllowLocalSources` 설정이 참이면 허용). 가져오기 실패(전송 오류, 인증 오류)는 `url`/`authId`/`repoAuth` 오류로 400 폼 재표시. 성공하면 **303 프로젝트 홈**. 메뉴 설정 저장은 `menus`에 든 것만 켠다.

### 7.4 시험
`ImportTest`, `ImportSourceRestrictionTest` (`src/test/kotlin/dev/yona/web/project/`). 위키 체크박스를 켠 가져오기를 검증하는 시험은 없다(`ImportTest.kt:110`은 위키가 꺼진 결과만 확인).

## 8. 통계 (`project/statistics.html`)
`GET /{user}/{project}/statistics`, `@DefaultProjectCheck`(`StatisticsController.kt:27-31`), 메뉴 종류 `ISSUE`, 본문은 `<h1>Under Construction</h1>` 하나(영어 고정, 메시지 키 없음). 이 영역 템플릿 어디에도 이 화면으로 가는 링크가 없다(project-F11). 제목은 메시지 키 `statistics`.

## 9. 구독자 목록 (`project/watchers.html`)
`GET /{user}/{project}/watchers` (`@IsAllowed(READ)`, `ProjectWatchersController.kt:34`). 모델 `watchers`: 프로젝트 구독자 중 프로젝트를 읽을 수 있는 사용자를 id 순으로(`NotificationReceivers.actualWatchers`). 화면: 제목 `project.watcher.title`, 설명 `project.watcher.description`, 구성원 카드(`col-lg-6`, 64px 아바타, 이름, `@로그인ID`, 각각 `/{loginId}` 링크). 메뉴 조각은 `NONE`으로 끼워 어느 탭도 활성이 아니다(`watchers.html:13`). 빈 목록 문구 없음. 동작 요소는 사용자 링크뿐이다. 익명도 공개 프로젝트이면 볼 수 있다.

## 10. 발견 사항 (project-F1 ~ F15)

| 번호 | 내용 | 확인 방법 | 비고 |
|---|---|---|---|
| project-F1 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 웹훅 화면이 쓰는 `project.webhook.includeGitPush`가 `messages_ko_KR.properties`에 없고 대문자 I인 `project.webhook.IncludeGitPush`만 있다. 한국어 화면에 영어 "Include git push events"가 나온다. v1.6 번들도 같은 철자 불일치(`conf/messages.ko-KR:741`) | 키 대조 (`messages_ko_KR.properties:785`, `messages.properties:786`, `webhooks.html:41`) | v1.6 결함 이월 |
| project-F2 **[수정됨, A-457]** | 마일스톤 보기와 조직 보기의 검색 상자가 `data-toggle="item-search"`로 표시돼 있지만 이를 묶는 스크립트가 정적 자원에 없다. `jquery.search.js`가 "참조 없는 파일" 정리 때 지워졌다(커밋 `0fd8fcd58` 메시지). 시험은 속성의 존재만 확인한다(`MilestoneTest.kt:519`) | `grep item-search` 전 범위 | milestone.md의 milestone-F2와 같은 사실 |
| project-F3 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 가져오기 폼의 위키 체크박스는 늘 체크된 채로 그려지지만 `ImportController`의 메뉴 목록(`ALL_MENUS`, `:434`)에 `wiki`가 없어 값이 버려지고 위키 메뉴가 꺼진 프로젝트가 만들어진다. v1.6은 폼 전체를 묶었다(`ImportApp.java:119-130`) | 코드 대조, 시험 없음 | |
| project-F4 **[검증: 문서 문제]** | 위키 `web/project-layout.md:78`과 `refactoring-backlog.md` R-11은 `Q` 단축키가 "로그인 ID가 manager일 때"만 켜진다고 쓰지만 코드는 프로젝트 `MANAGER` 역할일 때 켠다(`ProjectViewContext.kt:161`, `decisions.md` A-353). 코드가 맞다. 또 설정 메뉴(톱니)는 `canUpdate`(그룹 관리자, 사이트 관리자 포함)라 두 조건이 같지 않다 | 코드와 결정 기록 대조 | 위키가 낡음 |
| project-F5 **[검증: 결함 아님]** | 구독 단추는 화면 조건이 WATCH인데 서버는 READ만 검사한다(`WatchProjectController.kt:47-59`). 읽을 수 있는 비공개 프로젝트의 멤버가 아닌 그룹 구성원 같은 경우를 제외하면 둘이 같아 보이나 연산이 다르다 | 코드 대조 | 추측: 실제 차이가 드러나는 조합은 확인 못 함 |
| project-F6 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 단축키 `C`, `P`는 `codeAccessible`를 보지 않고 등록된다. 코드 열람 제한 프로젝트의 비구성원이 누르면 403이다(탭은 숨겨짐). 입력란에서는 동작하지 않음 | `menu.html:14,26,79-90` | |
| project-F7 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 홈의 "구성원 추가" 링크는 `canUpdateLabels`(멤버 이상)로 보이지만 대상 화면은 프로젝트 관리자만 연다. 일반 멤버는 링크를 눌러 403을 본다. v1.6도 같은 조건(`home.scala.html:123`) | 코드 대조 | v1.6 동일 |
| project-F8 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 사이트 관리자는 `isAllowed`가 항상 참이라 모든 프로젝트 홈에서 탈퇴 단추가 보이고, 소유자가 아닌 비구성원 관리자가 눌러도 아무 일 없이 홈으로 간다. 소유자인 사이트 관리자는 403 `project.member.ownerCannotLeave` | `AccessControl.kt:111`, `ProjectMemberController.kt:132-160` | |
| project-F9 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `yona.Request`(`data-request-method`) 실패는 화면에 아무것도 나타나지 않는다(웹훅 삭제, 마일스톤 열기/닫기/삭제). 프로젝트 삭제만 `Request.send`의 결과를 보고 알림을 띄운다 | `esm/Request.js`, `project.Delete.js:54-59` | |
| project-F10 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 홈의 `#leaveBtn` 클릭 처리기가 탈퇴 단추를 누를 때마다 중복 등록된다. 이전 모달을 닫고 다시 열어 "예"를 누르면 같은 삭제 요청이 여러 번 나간다 | `project.Home.js:102-125` | 추측: 두 번째 이후 요청은 서버가 멤버 부재로 무시하나 이동은 첫 응답이 결정 |
| project-F11 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `statistics.html`로 가는 링크가 이 영역 템플릿에 없고 본문은 "Under Construction"뿐이다 | grep | v1.6 동일 |
| project-F12 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 목록의 읽을 수 없는 행 문구는 영어 고정이고 `readable`은 `displayPrivateRepositories`가 켜져 있으면 모든 행에 참이다 | `list.html:91`, `ProjectListController.kt:77` | |
| project-F13 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 생성, 가져오기의 클라이언트 이름 검사는 `.wiki` 접미사를 모르고 서버만 거절한다(`ProjectNameRules`). 설정 화면도 같다 | `project.New.js:28-29`, `ProjectNameRules.kt` | |
| project-F14 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 홈 설명 입력란은 Enter로 저장되지 않는다(`<form onsubmit="return false">`). 저장 실패는 화면에 표시가 없다(콘솔만) | `home.html:32`, `project.Home.js:88-90` | |
| project-F15 **[수정됨, A-457]** | 프로젝트 생성의 VCS 선택 처리기가 `evt.val`을 읽는데(`project.New.js:85`) 보통의 `change` 이벤트 객체에는 `val`이 없다. 그러면 Subversion 경고와 PR 체크 토글이 일어나지 않고 오류로 끝날 것이다 | 코드 읽기만(실행하지 않음) | **추측** |

## 11. 범위 점검 (이 파일이 다룬 템플릿)

| 템플릿 | 다룬 절 |
|---|---|
| `project/header.html` | 2 |
| `project/menu.html` | 3 |
| `project/home.html` | 4 |
| `project/partial-readme.html` | 4.3, 4.4 |
| `project/partial-history.html` | 4.4 |
| `project/partial-dashboard.html` | 4.4 |
| `project/list.html` | 5 |
| `project/create.html` | 6 |
| `project/importing.html` | 7 |
| `project/statistics.html` | 8 |
| `project/watchers.html` | 9 |
| `project/partial-settingmenu.html` 외 설정 계열 | [project-settings.md](project-settings.md) |
