---
title: 이슈 영역 화면 계약 (요약, 공통 계약, 권한 행렬, 발견 사항)
type: ui-contract
sources:
  - src/main/resources/templates/issue/*.html (22개)
  - src/main/resources/templates/reviewthread/list.html
  - src/main/kotlin/dev/yona/web/issue/*.kt
  - src/main/kotlin/dev/yona/web/api/IssueApiController.kt
  - src/main/kotlin/dev/yona/web/api/IssueFormApiController.kt
  - src/main/kotlin/dev/yona/web/code/ReviewListController.kt
  - src/main/kotlin/dev/yona/access/AccessControl.kt
  - src/main/kotlin/dev/yona/web/access/
  - src/main/kotlin/dev/yona/web/access/ProjectCheckStage.kt
  - src/main/kotlin/dev/yona/domain/issue/IssueMassUpdateService.kt
  - src/main/resources/static/assets/javascripts/esm/Usermenu.js
  - src/main/resources/static/assets/javascripts/esm/Request.js
  - src/main/resources/static/assets/javascripts/esm/service/issue.*.js
updated: 2026-11-11
---

# 이슈 영역 화면 계약

`ui-improvements` 브랜치의 템플릿 `issue/`(22개)와 `reviewthread/`(1개)를 기준으로 화면에 있는 모든 단추, 링크, 폼, 스크립트, 서버 규칙을 따라가 정리한 계약이다. 새 UI가 이 계약대로 동작하면 현재 기능과 같다. 코드는 바꾸지 않았다.

글은 다섯 개로 나뉜다.

| 글 | 내용 |
|---|---|
| 이 글(`issue.md`) | 영역 요약, 공통 계약, 권한 행렬, 발견 사항 목록, 범위 점검 |
| [issue-list.md](issue-list.md) | 프로젝트 이슈 목록, 검색 폼, 빠른 검색, 일괄 변경 막대 |
| [issue-view.md](issue-view.md) | 이슈 보기, 댓글, 추천, 공유자, 담당자, 가중치, 변경 감지 |
| [issue-form.md](issue-form.md) | 이슈 작성 폼, 수정 폼 |
| [issue-my.md](issue-my.md) | 내 이슈, 리뷰 목록 |

이미 있는 글: [web/issue-list.md](../web/issue-list.md), [web/issue-view.md](../web/issue-view.md), [web/issue-create.md](../web/issue-create.md), [web/issue-edit.md](../web/issue-edit.md), [web/issue-comments.md](../web/issue-comments.md), [web/issue-mass-update.md](../web/issue-mass-update.md), [web/issue-api.md](../web/issue-api.md), [web/issue-labels.md](../web/issue-labels.md), [web/my-issues.md](../web/my-issues.md), [web/review-list.md](../web/review-list.md). 같은 내용은 다시 쓰지 않았고 어긋난 점만 발견 사항에 적었다.

경로 약어: `T/` = `src/main/resources/templates/`, `K/` = `src/main/kotlin/dev/yona/`, `J/` = `src/main/resources/static/assets/javascripts/`, `S/` = `app/assets/stylesheets/scss/`(CSS 원본), `TEST/` = `src/test/kotlin/dev/yona/web/issue/`.

## 1. 영역 요약(화면 목록)

| 템플릿 | URL | 컨트롤러 | 용도 |
|---|---|---|---|
| `issue/list.html` | `GET /:user/:project/issues` | `K/web/issue/IssueListController.kt:72` | 프로젝트 이슈 목록 전체 화면 |
| `issue/partial-list-wrap.html` | 같은 URL, `HX-Request` | 같음(`:94`) | htmx 교체 조각(`listWrap`) |
| `issue/partial-list.html` | 위 안 | | 이슈 줄(`list`), 초안 줄(`draftList`) |
| `issue/partial-list-quicksearch.html` | 위 안 | | 빠른 검색 4개 |
| `issue/partial-searchform.html` | 위 안 | | 상세 검색 폼 |
| `issue/partial-massupdate.html` | 위 안, 마일스톤 보기 | | 일괄 변경 막대 |
| `issue/partial-select-label.html` | 목록, 작성, 수정, 보기 | | 라벨 select2(`selectLabel`) |
| `issue/view.html` | `GET /:user/:project/issue/:number` | `IssueViewController.kt:61` | 이슈 보기 |
| `issue/partial-comments.html` | 보기 안, `GET .../timeline`(`:114`) | | 타임라인(댓글과 이벤트) |
| `issue/partial-comment.html` | 위 안 | | 댓글 한 개 |
| `issue/partial-index-comments.html` | 보기 오른쪽 아래 | | 댓글 색인 |
| `issue/partial-subtasks.html` | 보기 안 | | 부모와 하위 이슈 목록 |
| `issue/partial-voters.html`, `partial-voter-list.html` | 보기 안, 댓글 안 | | 추천자 아바타, 추천자 모달 |
| `issue/partial-show-selected-label.html` | 보기 오른쪽(수정 권한 없음) | | 읽기 전용 라벨 목록 |
| `issue/partial-assignee.html` | 보기, 작성, 수정 | | 담당자 select(`assignee`) |
| `issue/create.html` | `GET /:user/:project/issueform`, `/user/issues/new`, `/user/issues/new/mine`, `POST .../issues/latest` | `IssueCreateController.kt:62, 78, 94, 108` | 이슈 작성 폼 |
| `issue/edit.html` | `GET .../issue/:number/editform`, `POST .../edit` | `IssueEditController.kt:66, 79` | 이슈 수정 폼 |
| `issue/partial-select-subtask.html` | 작성, 수정 안 | | 대상 프로젝트와 부모 이슈 선택 |
| `issue/my-list.html`, `my-partial-search.html`, `my-partial-list.html` | `GET /user/issues` | `MyIssuesController.kt:46` | 내 이슈 |
| `reviewthread/list.html` | `GET /:user/:project/reviews` | `K/web/code/ReviewListController.kt:65` | 리뷰 스레드 목록 |

화면이 부르는 서버 쪽(템플릿은 다른 영역이지만 이 영역 화면의 계약): `K/web/issue/IssueMassUpdateController.kt`, `IssueVoteController.kt`, `IssueCommentController.kt`, `K/web/api/IssueApiController.kt`(담당자, 공유자, 가중치, 변경 감지, 알림 수신자, 본문과 댓글 체크리스트), `K/web/api/IssueFormApiController.kt`(제목 머리말, 담당자 후보), `K/web/project/MentionListController.kt`, `K/web/WatchController.kt`, `K/web/api/FavoriteApiController.kt`, `K/web/api/TranslationApiController.kt`, `K/web/issue/IssueLabelController.kt`(`labels.css`, `labelsform`).

## 2. 공통 계약

### 2.1 접근 검사 체인

- 어노테이션 실행 순서(`K/web/access/AccessChainInterceptor.kt:29-50`): 메서드에 선언된 것을 **소스 순서대로**, 그다음 클래스(최상위 부모부터)의 것. `@AnonymousCheck`가 메서드 앞쪽에 있으면 로그인 검사가 먼저, 클래스에만 있으면 프로젝트 검사(`@IsAllowed`, `@IsCreatable`, `@NullProjectCheck`)가 먼저다.
- `@AnonymousCheck`(`K/web/access/AnonymousCheckStage.kt:25-37`): 사이트가 익명 접근을 막았거나(`allowsAnonymousAccess=false`) `requiresLogin=true`일 때 비로그인을 `303 /users/loginform?redirectUrl=<요청 경로>`로 보낸다. GET이면 질의 문자열도 `%3F`와 인코딩해 `redirectUrl`에 싣는다. `displaysFlashMessage`이면 경고 `user.login.alert`.
- `@NullProjectCheck`: 프로젝트가 있는지만 본다(읽기 권한은 컨트롤러가 직접 확인)(`ProjectCheckStage.kt:60-65`). 이 어노테이션은 아래 메뉴 꺼짐 검사를 거치지 않는다.
- `@IsAllowed(연산, 리소스종류=PROJECT)`, `@IsCreatable(리소스종류)`: 프로젝트를 찾고(없으면 비로그인은 로그인 화면 403, 로그인 사용자는 오류 화면 403), 읽기 권한이 없으면 같은 형태 403, 그다음 자기 검사(`ProjectCheckStage.kt:180-195`). 사이트가 익명 접근을 막았으면 비로그인은 로그인 폼 303이 아니라 이 단계의 로그인 화면 403을 먼저 받는다(`AccessChainInterceptor.kt` 머리 주석).
- **메뉴를 끈 프로젝트의 404**(`ProjectCheckStage.checkMenuEnabled`, `:88-111`): 프로젝트를 읽는 단계(`@IsAllowed`, `@IsCreatable`, `@RequiresVcs` 등)와 프로젝트가 있는지만 보는 단계(`@NullProjectCheck`) 모두에서 경로의 세 번째 조각이 `issues`, `issue`, `issueform`, `copyLabels`이고 이슈 메뉴(`menuSetting.issue`)가 꺼져 있으면, 또는 `milestones`, `milestone`, `newMilestoneForm`이고 마일스톤 메뉴가 꺼져 있으면 `404`(`error/notfound`)이다. `issue/label...`로 시작하는 경로(라벨 관리)와 `/-_-api/v1/` 경로는 막지 않는다. 이 영역에서는 목록, 새 이슈 폼, 이슈 보기(`GET .../issue/:n`), 삭제, 수정 폼과 수정, 댓글 쓰기와 삭제, 일괄 변경 `POST .../issues`, 타임라인 조각, 다음 상태, 추천, 담당자 후보, 공유자 검색, 변경 감지, 알림 수신자 미리 보기가 모두 걸린다(2026-11-11부터. `@NullProjectCheck` 경로도 막는다, `issue-F26`). 단 `IssueApiController`의 담당자 변경, 공유, 가중치, 본문 갱신 API는 `-_-api` 경로가 아니라 일반 경로이지만 세 번째 조각이 `issue`로 시작하므로 같이 막힌다. 시험 `src/test/kotlin/dev/yona/web/access/ProjectMenuOffAccessTest.kt`.
- 오류 응답은 **모두 HTML 화면**이다(`K/web/error/ErrorViews.kt`): 404 `error/notfound`, 403 `error/forbidden`, 400 `error/badrequest`, 413 `error/request-text-entity-too-large`, 500 `error/internal-server-error-default`. JSON 응답을 내는 곳은 API 컨트롤러(`IssueApiController`, `IssueFormApiController`, 일괄 변경의 400, 즐겨찾기)뿐이다. 화면 스크립트가 `res.responseText`를 JSON으로 읽는 곳(`Tasklist.js`, `ReceiverList.js`, `Usermenu.js`)은 이 때문에 HTML 응답에서 예외가 날 수 있다.

### 2.2 CSRF

- 서버: `YonaCsrfMatcher`(`K/security/CsrfSupport.kt:24-29`)가 읽기 요청, 외부 API(`/-_-api`), MCP, SVN, Git, LFS, `Authorization` 또는 `Yona-Token` 헤더가 있는 요청을 뺀 나머지 상태 변경 요청에 토큰을 요구한다. 쿠키 `XSRF-TOKEN`을 매 요청에 심는다(`CsrfCookieFilter`).
- 화면: `J/common/yona.Csrf.js`가 같은 출처의 `fetch`, `XMLHttpRequest`에 `X-XSRF-TOKEN` 헤더를, 폼 제출(스크립트의 `form.submit()` 포함)에 `_csrf` 숨은 입력을 단다(`yona.Csrf.js:46, 61-77`).
- 이 영역에서 상태를 바꾸는 `.../issue/:n/nextstate`는 화면이 POST로 보내므로 토큰을 싣는다. 서버는 v1.6 호환으로 GET도 받는데 GET은 토큰 검사 대상이 아니다(`issue-F7`).

### 2.3 응답 형태를 가르는 입력

| 입력 | 쓰이는 곳 | 의미 |
|---|---|---|
| `HX-Request: true` | 이슈 목록, 내 이슈, 라벨 목록 | 영역 조각(`AjaxRequests.isHtmx`, `K/web/AjaxRequests.kt`) |
| `X-Requested-With: XMLHttpRequest` | 이슈 목록(JSON), 이슈 삭제, 댓글 삭제 | Ajax. 삭제는 `204 + Location`, 아니면 `303` |
| `Accept: application/json` 선호 | 일괄 변경, 이슈 보기, 라벨 | 일괄 변경은 `{}`, 아니면 `303 → Referer` |
| `format=fragment|xls|html|json` | 목록 | 위와 같음(htmx, Ajax가 우선) |

`yona.http.ajax`는 같은 출처에 `X-Requested-With`를 붙이고 `dataType`에 따라 `Accept`를 정한다(`J/esm/http.js:105, 128`). `yona.Request.send`(삭제, 추천 등 `data-request-method` 요소)는 `X-Requested-With`만 붙이고, 성공하면 상태가 `204`이고 `Location`이 있을 때 그곳으로 이동하고 아니면 새로고침한다. 실패(2xx가 아님, 네트워크 오류)는 `request:error` 이벤트를 보내고, 그 이벤트를 취소하지 않았고 요소가 있으면 알림창(`$yona.alert`)을 띄운다: 403 `error.forbidden`, 404 `error.notfound`, 그 밖 `error.internalServerError`(`J/esm/Request.js:69-122`).

### 2.4 htmx 목록 교체(프로젝트 이슈 목록, 내 이슈)

`div[list-container]`가 `hx-target="this" hx-select="div[list-container] > *" hx-swap="innerHTML show:window:top" hx-push-url="true" hx-request='{"timeout":3000}'`를 선언한다(`partial-list-wrap.html:10`, `my-partial-search.html:10`). 공통 규칙은 `J/common/yona.Htmx.js`: 오류, 연결 실패, 3초 시간 초과, `list-container`가 없는 응답은 같은 주소를 일반 이동으로 다시 연다. 뒤로 가기는 서버에서 새로 불러온다. 쪽 나누기 링크는 `hx-boost="true"`(`J/esm/Pagination.js:163-178`).

### 2.5 단축키 기구

`J/esm/ShortcutKey.js`: 키 하나에 처리기 하나(`attach`가 덮어쓴다), 창 `keydown`에서 `INPUT`, `TEXTAREA` 대상이면 `bFormInput=true`로 알려 주고 `setKeymapLink`의 이동은 그 경우 하지 않는다. 한글 입력 중(`isComposing`, `keyCode 229`)은 무시한다. 화면 범위 키 목록: 목록 `N`, `LEFT`, `RIGHT`, `CTRL+A`(일괄 변경), 보기 `L`, `N`, `E`, `CTRL+SHIFT+ENTER`.

### 2.6 로컬 저장소와 쿠키

| 키 | 값 | 쓰는 곳 |
|---|---|---|
| `localStorage.useTwoColumnMode` | `'true'`/`'false'` | 목록 두 열 보기(`twoColumnMode.js`) |
| `localStorage.showSubtasksAlways` | `'true'`/`'false'` | 목록 하위 이슈 보기(`showSubtask.js`) |
| `localStorage[location.pathname]` | 본문 초안 | 작성, 수정, 댓글(`temporarySaveHandler.js`, 5초, 1시간 보관) |
| `localStorage[pathname + "-last-comment-update-draft-" + 댓글번호]` | 수정 댓글 초안 | 보기 |
| `localStorage["yona.draftTimes"]` | 초안별 저장 시각 JSON | 같음 |
| 쿠키 `XSRF-TOKEN` | CSRF 토큰 | `yona.Csrf.js` |

### 2.7 CSS 반응형 규칙

기준은 `S/_responsive.scss`의 `@media all and (max-width: 720px)`(1행). `.hide-in-mobile` = `display:none !important`, `.show-in-mobile` = `display:block !important`, `.show-in-mobile-inline` = `inline-block`, `.span-right-pane`은 숨김, `.span-left-pane`은 100%(`_responsive.scss:261-306`). `col-lg-*`는 부트스트랩 5의 lg(992px) 미만에서 쌓인다. `.hideFromDisplayOnly` = `display:none`(`S/_common.scss:299`).

### 2.8 상수

| 값 | 위치 |
|---|---|
| 쪽당 이슈 15, `itemsPerPage` 최대 45 | `IssueListController.kt:212-213`, `MyIssuesController.kt:104-105` |
| 쪽당 리뷰 15 | `K/domain/comment/ReviewSearchService.kt:53` |
| 제목 최대 255(화면 250) | `IssueCreateController.kt:231`, `IssueEditController.kt:163`, `create.html:25` |
| 부모 이슈 후보 최대 300 | `IssueFormSupport.kt:72` |
| 사용자 검색 최대 10(`Content-Range`로 전체 수 알림) | `K/web/api/ApiUserNodes.kt` `MAX_FETCH_USERS` |
| 멘션 이슈 후보 최대 20 | `MentionListController.kt:166` |
| 폼(urlencoded) 본문 한도 102400바이트(멀티파트는 해당 없음) | `K/config/YonaProperties.kt:106`, `K/web/TextBodyLimitFilter.kt:34` |
| 변경 감지 간격 3.6초에서 시작, 1.2배씩, 상한 5분 | `J/esm/service/detectChange.js:34-44` |
| 댓글 색인 앞 60자 | `IssueViewAssembler.kt:242` |
| 제출 단추 재활성화 3초 | `create.html:141`, `edit.html:201` |
| htmx 시간 제한 3초 | `partial-list-wrap.html:10` |

## 3. 권한 행렬(영역 전체)

규칙 정의: `K/access/AccessControl.kt`. 읽기(`isAllowed` 기본 규칙, 217-219행)는 공개 프로젝트(게스트 계정 제외) 또는 구성원, 이슈 공유자, 그룹(조직) 구성원(공개와 보호 프로젝트)이다. 수정(`UPDATE`, 220행)은 구성원 또는 그룹 구성원 + (앞서 `privileged`인 작성자, 프로젝트 관리자, 담당자). 작성자 권한은 그 프로젝트를 볼 수 있을 때(공개이거나 구성원이거나 그룹 구성원)만 준다(195-198행). **삭제는 이슈, 게시글, 댓글, 코드 리뷰 댓글, 커밋 댓글 모두 작성자와 프로젝트 관리자(조직 관리자, 사이트 관리자 포함)만**(`CONTENT_TYPES`, 198-199행과 318-325행, 추가 개발 항목 D-48). 댓글 생성(`isResourceCreatable`, 80행): 이슈의 작성자, 담당자, 공유자는 항상, 그 밖은 구성원 이상이거나 공개 프로젝트의 로그인 사용자.

| 기능 | 비로그인 | 로그인 비구성원(공개) | 이슈 공유자 | 이슈 담당자 | 구성원 | 이슈 작성자 | 프로젝트 관리자 | 그룹(조직) 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|---|---|---|
| 목록, 보기 | 허용(익명 접근 허용 시) | 허용 | 허용(비공개도) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 초안 보기 | 불가 | 불가 | 불가 | 불가 | 불가 | 허용 | 불가 | 불가 | 불가 |
| 이슈 작성 | 불가(로그인 303) | 허용(공개) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 작성 폼의 담당자, 마일스톤, 라벨 관리 칸 | 없음 | 담당자, 마일스톤 칸 없음 | 해당 없음 | 해당 없음 | 보임 | 보임 | 보임 | 보임 | 보임 |
| 이슈 수정(본문, 상태, 담당자, 마일스톤, 라벨, 마감일, 가중치, 공유자) | 불가 | 불가(작성자 아님) | 불가 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 일괄 변경 막대 보임 | 안 보임 | 안 보임 | 안 보임 | 구성원이면 보임 | 보임 | 구성원이면 | 보임 | 구성원 행이 없으면 안 보임(서버는 허용) | 같음 |
| 일괄 변경 서버 | 403 | 이슈별 `UPDATE`(경로의 프로젝트에 속하지 않은 이슈가 섞이면 404) | 같음 | 같음 | 허용 | 같음 | 허용 | 허용 | 허용 |
| 댓글 쓰기 | 불가 | 허용(공개) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 댓글 수정 | 불가 | 본인 것 | 본인 것 | 본인 것 | 허용 | 본인 것 | 허용 | 허용 | 허용 |
| 댓글, 이슈 삭제 | 불가 | 본인 것(이슈는 작성자) | 불가 | 불가 | 본인 것 | 본인 것 | 허용 | 허용 | 허용 |
| 추천, 즐겨찾기 | 불가(로그인 필요) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 지켜보기 | 불가 | 허용(공개) | 비공개면 불가 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 리뷰 목록 | 로그인 303 | 허용 | 비공개면 불가 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 내 이슈 | 로그인 303 | 허용(내 것만) | | | | | | | |
| 라벨 관리 화면 | 불가 | 불가 | 불가 | 불가 | 불가(서버 403, 화면 링크도 안 보임) | 불가 | 허용 | 허용 | 허용 |

화면에서 숨기는 것과 서버가 막는 것이 다른 곳은 발견 사항에 따로 적었다(F3, F26). 이슈와 마일스톤 메뉴를 끈 프로젝트의 404는 2.1을 본다.

## 4. 발견 사항 목록

확인한 사실만 적었고 추측은 "추측"이라고 표시했다. 근거 번호는 `파일:줄`이다.

- **issue-F1. (정정, 2026-11-11 브라우저로 확인) 수정 폼의 상태 드롭다운은 동작한다.** 처음 조사는 `new yona.ui.Dropdown`의 호출처가 `issue.MassUpdate.js`와 `issue.Write.js`뿐이라 초기화가 없다고 보았으나, `common/scripts.html:79-86`가 문서의 `.btn-group[data-name]`(`data-activate="manual"`이 아닌 것)을 모두 `yona.ui.Dropdown`으로 초기화한다. Playwright로 `#state`의 "닫힘"을 누르면 숨은 입력 `state`가 `CLOSED`가 되고 라벨이 "닫힘"으로 바뀌는 것을 확인했다. 따라서 폼에 `state`가 실리고 서버가 상태를 바꾼다.
- **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** **issue-F2. 작성, 수정 폼의 마일스톤 새로고침 코드가 도달 불가다.** `issue.Write.js:55, 65, 85-92`는 `.yobicon-refresh`(옵션 `elMilestoneRefresh`) 클릭과 `#options` 안 위임에 걸려 있는데 `create.html`, `edit.html`에 그 요소가 없다(`:113`, `:150`의 `$("#milestoneOption .yobicon-refresh")`는 빈 집합).
- **[검증: 수정함, 일부 유지]** **issue-F3. 가중치 단추와 즐겨찾기 별.** 가중치 올림, 내림 단추는 `issue.canUpdate`일 때만 그리고 숫자만 모두에게 보인다(`view.html:91`). 서버는 `UPDATE` 권한이 없으면 `403 {"message":"Permission denied"}`(`IssueApiController.kt:227`)이고 클릭 처리기(`view.html:424-440`)에는 실패 처리기가 없다. 즐겨찾기 별(`view.html:26-28`)은 비로그인에게도 보이고 서버는 `403` 빈 본문(`FavoriteApiController.kt:40`)이지만, `Usermenu.js`의 `failureReason`(196-212)이 JSON이 아닌 응답에서도 이유를 골라 알림창 `common.updateFailed`를 띄운다(131-134). (2026-11-11 수정: 가중치 단추는 수정 권한이 있을 때만 그리고, 즐겨찾기 실패는 예외 대신 알림으로 알린다. A-462)
- **[재현됨, 수정됨, A-459]** **issue-F4. 담당자 변경 API가 권한이 없을 때 성공처럼 답하던 것.** `POST .../issue/:n/assignees`는 `UPDATE`가 없거나 비로그인이면 `403 {"message":"Forbidden"}`이다(`IssueApiController.kt:127-128`). v1.6은 아무것도 바꾸지 않고 `200 {"issue": 경로}`만 돌려줘 화면의 `response.assignee.name`(`issue.Assginee.js:87`)이 예외를 냈다. 이 메서드에는 `@IsAllowed(READ)`가 없고 이슈 메뉴를 끈 프로젝트의 404 검사도 걸리지 않는다. (2026-11-11 수정: 권한이 없으면 403)
- **[재현됨, 수정됨, A-459]** **issue-F5. 읽기 권한을 검사하지 않던 보조 API.** `findSharer`(`IssueApiController.kt:147-148`), `detectChange`(234-235), `commentNotiReceivers`(264-265)에 `@IsAllowed(READ)`가 있다. v1.6은 이를 검사하지 않아 비공개 프로젝트의 이슈에 대해 로그인한 아무나 본문 SHA-1, 댓글 수, 마지막 댓글 작성자 이름, 알림 수신자 목록을 받을 수 있었다. `detectChange`는 그 밖에 로그인도 확인한다(401). (2026-11-11 수정: 읽기 권한 검사 추가. 시험 `IssueApiTest.kt:228`)
- **[재현됨, 수정됨, A-459]** **issue-F6. 댓글 추천 경로의 소속 검사.** `POST .../comment/{commentId}/vote|unvote`는 댓글이 경로의 이슈와 프로젝트에 속하는지 본다(`IssueVoteController.kt:55, 67, 78-81`). 속하지 않으면 404 평문 `issue.comment.error.vote|unvote`이다. v1.6은 읽을 수 있는 프로젝트 경로로 다른(비공개) 프로젝트의 댓글 번호에 추천할 수 있었다. 아직 사실인 것: 댓글 수정 폼(`IssueCommentController.kt:66-70`)은 `id`로 찾은 댓글의 소속 이슈를 경로와 비교하지 않는다(권한은 그 댓글 기준으로 검사된다). (2026-11-11 수정: 추천 경로에 소속 검사 추가. 시험 `IssueApiTest.kt:243`)
- **[검증: 수정함, GET 호환 유지, A-464]** **issue-F7. `nextstate`가 GET으로 상태를 바꾸던 것.** `.../issue/:n/nextstate`는 `GET`과 `POST`를 모두 받고(`IssueViewController.kt:130-142`) 열림과 닫힘을 뒤집고 이벤트와 알림을 만든다. 화면의 `#dynamic-comment-btn`은 이제 `yona.http.post`로 보내고 끝나면 `location.reload()`한다(`issue.View.js:480`). GET은 v1.6의 주소 호환으로 남아 CSRF 대상이 아니다. 새 UI가 링크 미리 읽기나 프리페치를 GET에 쓰면 상태가 바뀐다. (2026-11-11 수정: 화면 호출을 POST로 바꿈. 시험 `IssueActionsTest.kt:112`)
- **[검증: 수정함]** **issue-F8. 마감일을 지우면 화면에 `(null)`이 찍히던 것.** 일괄 변경의 JSON `dueDateMsg`는 마감일이 없으면 `null`(`IssueMassUpdateController.kt:111-116`)이고 `issue.View.js:133`은 `null`이면 상태 글자를 비운다. 닫힌 이슈의 지난 마감일은 부호 있는 날수(`-3`)다(코드 주석, v1.6과 같다고 적힘). (2026-11-11 수정: `"(" + null + ")"` 대신 빈 글자)
- **[검증: 일부 수정함, 일부 유지]** **issue-F9. 일괄 변경.** (a) 삭제 `delete=true`는 서버에 있으나(`IssueMassUpdateController.kt:88, 99-102`) 막대에 삭제 단추가 없다. `issue.MassUpdate.js`의 `welDeleteButton: $("#delete")`는 없는 요소(유지). (b) `issues[n].id`가 경로의 프로젝트에 속하지 않으면 404이고 아무것도 바꾸지 않는다(`:93`). (c) 다른 프로젝트의 라벨은 붙이지 않고(`IssueMassUpdateService.kt:80`) 다른 프로젝트의 마일스톤 번호는 없는 것으로 본다(`:71`). 같은 배타 분류 라벨을 둘 이상 붙이는 요청은 `400 {"attachingLabelIds":["Invalid value"]}`이다(`IssueMassUpdateController.kt:70-74`). 화면 스크립트의 배타 분류 해제(`issue.MassUpdate.js:359-372`, `Select2.js` `issuelabel` 동작)는 그대로 있다. (d) `state`는 `OPEN`, `CLOSED`만 받고 그 밖(`DRAFT`, `MERGED`, 소문자)은 400이다(`:65-69`). 아직 사실인 것: 수정 폼의 `state`는 `State` 열거형의 어떤 이름이든 받는다(`IssueEditController.kt:122`). (2026-11-11 수정: (b), (c), (d)와 배타 분류 검사. A-464)
- **[검증: 문서 문제]** **issue-F10. 기존 위키 글이 코드와 어긋난다.** (a) `web/issue-view.md:63-65`와 `web/issue-edit.md:35-37`의 "대기"(3c-3에서 옮긴다)는 낡았다. 구독, 추천, 공유자, 가중치, 일괄 변경, 댓글, 변경 감지, 번역의 서버 쪽은 모두 구현되어 있다. (b) `web/issue-list.md:61`, `web/my-issues.md:35`는 영역을 `div[pjax-container]`로 적었지만 실제 속성은 `div[list-container]`다(`partial-list-wrap.html:10`, `yona.Htmx.js`의 `CONTAINER_MARK`). (c) `web/issue-view.md`는 "멘션 기록은 지우지 않는다(R-24)"라고 적었지만 `IssueService.delete`는 이슈와 댓글의 멘션 기록을 지운다(`IssueService.kt:202, 211`, 주석 "v1.6은 남겨 고아 행이 되었다"). 코드가 맞다. (d) `web/issue-view.md`가 가중치 단추를 수정 권한이 있을 때의 항목으로 묶은 것은 현재 템플릿과 맞는다(F3 수정 뒤). (e) `web/issue-edit.md`의 상태 선택 서술은 F1과 어긋난다.
- **[검증: 일부 수정함, 일부 유지]** **issue-F11. 제목 길이와 오류 문구, 마일스톤 소속.** (a) 화면 `maxlength=250`, 서버 255(`MAX_TITLE`)(유지). (b) 서버 제목 오류 키 `error.maxLength`의 문구는 `messages-play.properties:12` `Maximum length is {0}`뿐이고(한국어 번역 없음) 템플릿은 인자 255를 넘겨 그린다(`create.html:30`, `edit.html:39`, `#{${error}(255)}`). (c) 이슈를 만들 때, 수정할 때, 일괄 변경할 때 `milestoneId`가 그 프로젝트의 마일스톤이 아니면 마일스톤 없음으로 처리한다(`IssueService.kt:103, 290`, 일괄 변경 `IssueMassUpdateService.kt:71`). 라벨은 대상 프로젝트가 다르면 버리고(`IssueCreateController.kt:145-147`) 수정에서 옮길 때 마일스톤을 지운다(`IssueService.kt:290`). (2026-11-11 수정: (c)의 다른 프로젝트 마일스톤 번호 거절)
- **[검증: 수정함]** **issue-F12. 라벨 관리 링크의 표시 조건.** 검색 폼의 톱니바퀴(`partial-searchform.html:84`)는 `search.canManageLabels`(프로젝트 `UPDATE` 권한, `IssueListController.kt:199`)일 때 보이고 목적지 `GET .../issue/labelsform`도 `@IsAllowed(UPDATE)`(`IssueLabelController.kt:88-90`)로 같다. `partial-select-label.html:12`의 `[편집]`은 `view.manager`로 걸려 있다. (2026-11-11 수정: v1.6은 구성원에게도 보였고 누르면 403이었다)
- **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** **issue-F13. 죽은 코드, 중복 id, 쓰이지 않는 속성.** (a) `partial-list.html:59, 150`의 `th:if="${!child.draft or true}"`는 항상 참. (b) `partial-comment.html:39`의 `c.voters.size() == 1 ? 단수 : 복수`는 `size() > 5` 안이라 단수 가지가 도달 불가. (c) `li.post-item`의 `href`, `data-item`, `data-value` 속성을 읽는 스크립트가 없다(`J`에서 `post-item`을 다루는 곳은 `twoColumnMode.js`, `showSubtask.js`, `issue.List.js`, `board.List.js`, `user.View.js`이고 `href`를 읽지 않는다). (d) 중복 id: `two-column-mode-checkbox`(`two-column-mode-checkbox-area.html:9`와 `show-subtasks-checkbox.html:9`), `comments`와 `timeline`(`view.html:146-147, 244-245`). (`copyEmailBtn`의 중복은 F20에서 없앴다.) (e) `issue.View.js`의 `_setTimelineUpdateTimer`(호출 주석 처리, 29행), `_isDockedInspectorOpened`, `_unsetTimelineUpdateTimer`는 쓰이지 않는다. (f) `Tasklist.js:18-26`의 `$this.closest()`는 인자가 없어 빈 집합이다. (g) `view.html`의 `.watcher-list`(141행)는 채우는 코드가 없다. (h) `my-list.html:44`의 `.title-cell > .label` 선택자는 이 화면 마크업과 맞지 않는다. (i) `review.List.js`는 목록 영역에 htmx 속성이 없어 `.issue-list-wrap` 클래스만 이슈 목록과 공유한다. (j) 작성, 수정 제출 단추는 `type=submit`인데 클릭 처리기가 다시 `form.submit()`을 부른다(`create.html:50, 138-146`). 중복 제출 여부는 확인하지 못했다(추측).
- **[브라우저로 확인됨 2026-11-11, R-164]** **issue-F14. 마감일 검증 오류가 화면에 나오지 않는다.** 서버는 `errors["dueDate"]`를 채우지만(`IssueCreateController.kt:123`, `IssueEditController.kt:97`) 두 템플릿은 `errors['title']`만 그린다(`create.html:29-31`, `edit.html:38-40`). 400으로 폼을 다시 그리고 제목만 오류가 있으면 문구가 나온다. 화면의 `isValidDate`가 먼저 막으므로 직접 요청에서만 드러난다.
- **[검증: 일부 수정함, 일부 유지]** **issue-F15. 하드코딩된 문구.** 한국어: `partial-select-subtask.html:22-23`의 `이미 부모 이슈입니다.`(메시지 키 없음). 영어: `view.html:91`의 `: Upvote`, `: Down vote`, `my-partial-list.html:87`의 `Due date: `, `J`의 `New comment by`, `Reload page`, `Issue updated!`(`detectChange.js:64-74`), `Issue will be moved or written to`(`Subtask.js:33`), `Label: `(`TitleHeadAutoCompletion.js:86`). 일본어, 러시아어, 우즈베크어 메시지 파일에는 이 영역이 쓰는 키 일부가 없어 기본 `messages.properties`로 대체된다(일본어, 러시아어, 우즈베크어에서 이 영역 템플릿이 쓰는 97개 키 중 각각 수십 개가 없다, `messages_ko_KR.properties`와 `messages.properties`에는 누락 없음). (2026-11-11 수정: `Set to default:`(`my-list.html:31`), `Draft saved`(`temporarySaveHandler.js:131`), `Update failed:`(`Usermenu.js`)는 메시지 키(`common.defaultPage.set`, `common.defaultPage.failed`, `draft.saved`, `common.updateFailed`)로 바꿨고 사용자 값은 `$yona.escapeHtml`로 감싼다. 위 남은 문구도 사용자 값 부분만 `$yona.escapeHtml`로 감쌌다.)
- **[검증: 수정함]** **issue-F16. 변경 감지 폴링.** 비로그인에게는 폴링을 시작하지 않는다(`view.html:463-466`의 `th:unless="${currentUser.anonymous}"`). 서버는 비로그인에게 401이다(`IssueApiController.kt:238`). 서버는 요청의 `lastUpdateDate`를 읽지 않고 `issueUpdateDate`만 돌려주며 화면은 쓰지 않는다(유지). (2026-11-11 수정: v1.6은 익명 화면에서도 3초마다 401을 받는 폴링을 계속했다)
- **[검증: 결함 아님]** **issue-F17. 빠른 검색 "내게 할당"이 구성원이 아니면 필터가 비는 것으로 보인다(추측).** 링크는 로그인하면 항상 보이지만(`partial-list-quicksearch.html:17-23`) 검색 폼의 담당자 select에는 "내게 할당" 옵션이 구성원일 때만 있다(`partial-searchform.html:45`). 링크가 `assigneeId` select에 `.val(내 번호)`를 하는데 일치하는 옵션이 없으면 값이 비어 요청에서 조건이 빠질 수 있다. 실행해 보지 않았다.
- **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** **issue-F18. 키보드 쪽 이동은 전체 이동이다.** `LEFT`/`RIGHT`는 `setKeymapLink`(`document.location.href = 주소`)로 이동하고(`Pagination.js:311-315`, `ShortcutKey.js` `setKeymapLink`) 링크 클릭은 `hx-boost`로 영역만 교체한다.
- **[검증: 수정함]** **issue-F19. 부모 이슈 후보에서 초안을 뺀다.** `IssueListService.parentCandidates`(`IssueListService.kt:135-139`)는 `parent is null`이고 초안이 아닌(`isDraft is null or false`) 이슈를 최신순 300개 가져온다. 닫힌 이슈는 후보에 들어 있다. (2026-11-11 수정: v1.6은 다른 사람의 초안이 후보와 제목으로 노출되었다)
- **[검증: v2 신규 결함, 수정됨 A-460]** **issue-F20. 추천자 모달의 이메일 복사 단추.** `partial-voter-list.html:29`의 단추는 id 없이 클래스 `.copy-email-btn`이고, `view.html:402`가 `document`에 위임한 한 처리기로 받아 모든 모달(이슈 모달과 5명 초과 댓글 모달)에서 동작한다. (2026-11-11 수정: 단추마다 같은 id `copyEmailBtn`을 쓰고 `$("#copyEmailBtn")`로 첫 요소에만 처리기를 달아 댓글 모달의 단추가 동작하지 않던 것을 클래스 위임으로 바꿈)
- **[검증: 수정함]** **issue-F21. 비로그인 사용자의 번역 단추, 즐겨찾기 단추.** 번역 단추(`view.html:109`, `partial-comment.html:62`)는 `site.translationApiConfigured and !currentUser.anonymous`일 때만 그린다. 서버는 `@AnonymousCheck(requiresLogin)`(`TranslationApiController.kt:31`)이다. 즐겨찾기 별은 비로그인에게도 보이고 실패는 알림으로 알린다(F3). (2026-11-11 수정: v1.6은 번역 단추가 비로그인에게도 보였고 눌러도 아무 일이 없이 단추만 소모되었다)
- **[검증: 수정함]** **issue-F22. 댓글의 "이 댓글로 새 이슈" 링크.** `partial-comment.html:32-34`의 `span.new-issue-by`는 `th:unless="${currentUser.anonymous}"`로 로그인한 사용자에게만 그린다. 로그인한 사용자라도 최근 방문 프로젝트가 없으면 `GET /user/issues/new?commentId=`는 경고 `project.is.empty`와 함께 첫 화면을 그린다(`IssueCreateController.kt:85-88, 199-204`). 그 프로젝트에 대한 생성 권한은 이 경로에서 확인하지 않는다(폼 화면이 아니라 `createView`만 그린다). 실제 제출 때 `@IsCreatable`이 막는다. (2026-11-11 수정: v1.6은 비로그인에게도 보였다)
- **[검증: 수정함]** **issue-F23. 일괄 변경의 `Referer` 리다이렉트.** 브라우저 폼 제출의 응답은 `303 Location: <요청의 Referer>`이되, Referer가 없거나 이 사이트의 주소가 아니면(다른 호스트, 제어 문자나 역슬래시가 든 값) 목록으로 보낸다(`IssueMassUpdateController.kt:119-120`, `RedirectSupport.localTarget`). (2026-11-11 수정: v1.6은 헤더 값을 검증하지 않고 그대로 따라가는 열린 리다이렉트였다)
- **[검증: 수정함, 일부 유지]** **issue-F24. 메뉴를 끈 프로젝트와 단축키 도움말.** 도움말(`help/keymap.html`)은 `issueDetail`에서 `L`, `E`, `N`을 권한과 무관하게 보이지만(`:63-72`) 보기 화면은 `E`를 `canUpdate`, `N`을 `menuSetting.issue`일 때만 등록한다(`view.html:321-326`). 목록은 `menuSetting.issue` 검사 없이 `N`을 등록한다(`list.html:33-35`). 이슈, 마일스톤 메뉴를 끈 프로젝트의 주소는 읽기 검사 단계에서 404이다(2.1, `ProjectCheckStage.kt:91-114`, 이슈 목록과 새 이슈 폼과 타임라인 조각 등). 그러나 이슈 보기 본문(`GET .../issue/:n`) 등 `@NullProjectCheck`만 있는 경로는 걸리지 않는다(F26). (2026-11-11 수정: v1.6은 메뉴를 꺼도 주소로 열렸다. 선생님 결정 A-464)
- **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** **issue-F25. 인라인 이벤트 속성.** `th:attr="onclick=..."`(인라인 `onclick`, `view.html:112-113, 233-234`)로 수정 이동을 구현한다. 새 UI가 CSP로 인라인 이벤트를 막으면 동작하지 않는다.
- **[검증: 수정함(A-466, 2026-11-11)]** **issue-F26. 이슈 메뉴를 끈 프로젝트에서도 이슈 보기가 열리던 것.** 메뉴 꺼짐 404(`ProjectCheckStage.checkMenuEnabled`)는 처음에 읽기 단계(`@IsAllowed`, `@IsCreatable`)에서만 실행되어 `@NullProjectCheck`만 가진 이슈 보기, 삭제, 수정 폼과 수정, 댓글 쓰기와 삭제, 일괄 변경이 메뉴를 꺼도 열렸다. 선생님 결정("메뉴가 꺼져 있으면 접근 허용 불가")과 어긋나 고쳤다: `@NullProjectCheck` 단계도 같은 검사를 한다(`ProjectCheckStage.run`). 시험 `ProjectMenuOffAccessTest`가 이미 있는 이슈와 마일스톤으로 보기, 수정, 삭제, 댓글, 일괄 변경의 404와 메뉴를 다시 켠 뒤 200을 확인한다.

## 5. 범위 점검(조사한 템플릿)

| 템플릿 | 줄 수 | 다룬 글과 절 |
|---|---|---|
| `issue/create.html` | 167 | issue-form.md 1, 2, 3.1, 4 |
| `issue/edit.html` | 235 | issue-form.md 1, 2, 3.2, 4 |
| `issue/list.html` | 49 | issue-list.md 1, 2, 4, 5 |
| `issue/my-list.html` | 55 | issue-my.md 가.1-4 |
| `issue/my-partial-list.html` | 104 | issue-my.md 가.3 |
| `issue/my-partial-search.html` | 104 | issue-my.md 가.2, 3 |
| `issue/partial-assignee.html` | 15 | issue-view.md 3.6, issue-form.md 3.1 |
| `issue/partial-comment.html` | 81 | issue-view.md 3.4 |
| `issue/partial-comments.html` | 20 | issue-view.md 1, 3.4 |
| `issue/partial-index-comments.html` | 32 | issue-view.md 3.6 |
| `issue/partial-list-quicksearch.html` | 38 | issue-list.md 3.1 |
| `issue/partial-list-wrap.html` | 78 | issue-list.md 1, 2, 3.3, 3.4, 3.7 |
| `issue/partial-list.html` | 190 | issue-list.md 3.6 |
| `issue/partial-massupdate.html` | 127 | issue-list.md 3.5, 7.1 |
| `issue/partial-searchform.html` | 93 | issue-list.md 3.2 |
| `issue/partial-select-label.html` | 29 | issue-list.md 3.2, issue-view.md 3.6, issue-form.md 3.1 |
| `issue/partial-select-subtask.html` | 32 | issue-form.md 3.1 |
| `issue/partial-show-selected-label.html` | 17 | issue-view.md 3.6 |
| `issue/partial-subtasks.html` | 45 | issue-view.md 3.3 |
| `issue/partial-voter-list.html` | 34 | issue-view.md 3.2, 3.4 |
| `issue/partial-voters.html` | 25 | issue-view.md 3.2 |
| `issue/view.html` | 470 | issue-view.md 전체 |
| `reviewthread/list.html` | 118 | issue-my.md 나 |

`issue/` 22개 + `reviewthread/` 1개 = 23개 전체를 2026-11-11 재조사에서 현재 코드와 다시 대조했다(담당 범위인 목록, 보기, 내 이슈, 공통 조각 19개 + `reviewthread/` 1개는 이 재조사에서 직접 읽었고, 작성, 수정 폼 `create.html`, `edit.html`과 `partial-select-subtask.html`은 다른 담당이 다시 확인한다). 함께 읽은 공통 조각(다른 담당의 글에 정리된다): `common/comment-form`, `comment-update-form`, `child-comments`, `editor`, `file-uploader`, `two-column-mode-checkbox-area`, `show-subtasks-checkbox`, `comment-delete-modal`, `tasklist-bar`, `my-series-menu-tab`, `help/keymap`, `common/select2`, `common/calendar`, `milestone/partial-status`. 읽은 스크립트: `issue.View.js`, `issue.List.js`, `issue.MassUpdate.js`, `issue.Write.js`, `issue.Assginee.js`, `issue.Sharer.js`, `detectChange.js`, `Tasklist.js`, `SubComment.js`, `ReceiverList.js`, `Subtask.js`, `TitleHeadAutoCompletion.js`, `showSubtask.js`, `twoColumnMode.js`, `review.List.js`, `Comment.js`, `CommentForm.js`, `CommentAttachmentsUpdate.js`(앞부분), `Request.js`, `ShortcutKey.js`, `Pagination.js`, `yona.Htmx.js`, `yona.Csrf.js`, `http.js`(앞부분), `Dropdown.js`, `Select2.js`(`issuelabel` 부분), `temporarySaveHandler.js`, `Usermenu.js`(즐겨찾기 부분).

## 갱신 기록 (2026-11-11 재조사)

- 다시 확인한 템플릿: `issue/list.html`, `partial-list-wrap.html`, `partial-list.html`, `partial-list-quicksearch.html`, `partial-searchform.html`, `partial-massupdate.html`, `partial-select-label.html`, `view.html`, `partial-comments.html`, `partial-comment.html`, `partial-index-comments.html`, `partial-subtasks.html`, `partial-voters.html`, `partial-voter-list.html`, `partial-assignee.html`, `partial-show-selected-label.html`, `my-list.html`, `my-partial-search.html`, `my-partial-list.html`, `reviewthread/list.html`(20개). 범위 점검 표의 템플릿 목록은 그대로이다(새로 생기거나 없어진 템플릿 없음, `view.html`만 467줄에서 470줄로 바뀌어 표를 고쳤다).
- 고친 행과 문장(내용): 발견 사항 F3, F4, F5, F6, F7, F8, F9, F11, F12, F15, F16, F19, F20, F21, F22, F23, F24, 2.1 메뉴 꺼짐 404 항목과 로그인 폼 질의 문자열, 2.2 CSRF, 2.3 `Request.js` 실패 알림, 권한 행렬 3개 행과 줄 번호, F10(d), F13(a, d), F14.
- 줄 번호만 고친 곳: 약 25곳(`IssueListController`, `IssueViewController`, `IssueApiController`, `AccessControl`, `ProjectCheckStage`, `AnonymousCheckStage`, `Request.js`, `scripts.html`, `MentionListController`).
- 더한 행: 2.1의 메뉴 꺼짐 404 항목, 발견 사항 F26.
- 없앤 행: 없음(해결된 발견 사항은 현재 동작으로 다시 썼다).
- 새 발견 사항: `issue-F26`(메뉴를 끈 프로젝트에서도 `@NullProjectCheck`만 가진 이슈 경로가 열리던 것. 같은 날 고쳤다, A-466).
