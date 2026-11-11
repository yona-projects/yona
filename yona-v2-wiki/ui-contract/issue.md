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
| `issue/list.html` | `GET /:user/:project/issues` | `K/web/issue/IssueListController.kt:71` | 프로젝트 이슈 목록 전체 화면 |
| `issue/partial-list-wrap.html` | 같은 URL, `HX-Request` | 같음(`:93`) | htmx 교체 조각(`listWrap`) |
| `issue/partial-list.html` | 위 안 | | 이슈 줄(`list`), 초안 줄(`draftList`) |
| `issue/partial-list-quicksearch.html` | 위 안 | | 빠른 검색 4개 |
| `issue/partial-searchform.html` | 위 안 | | 상세 검색 폼 |
| `issue/partial-massupdate.html` | 위 안, 마일스톤 보기 | | 일괄 변경 막대 |
| `issue/partial-select-label.html` | 목록, 작성, 수정, 보기 | | 라벨 select2(`selectLabel`) |
| `issue/view.html` | `GET /:user/:project/issue/:number` | `IssueViewController.kt:59` | 이슈 보기 |
| `issue/partial-comments.html` | 보기 안, `GET .../timeline`(`:112`) | | 타임라인(댓글과 이벤트) |
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
- `@AnonymousCheck`(`K/web/access/AnonymousCheckStage.kt:25-29`): 사이트가 익명 접근을 막았거나(`allowsAnonymousAccess=false`) `requiresLogin=true`일 때 비로그인을 `303 /users/loginform?redirectUrl=<요청 경로>`로 보낸다. `displaysFlashMessage`이면 경고 `user.login.alert`.
- `@NullProjectCheck`: 프로젝트가 있는지만 본다(읽기 권한은 컨트롤러가 직접 확인)(`ProjectCheckStage.kt:60`).
- `@IsAllowed(연산, 리소스종류=PROJECT)`, `@IsCreatable(리소스종류)`: 프로젝트를 찾고(없으면 비로그인은 로그인 화면 403, 로그인 사용자는 오류 화면 403), 읽기 권한이 없으면 같은 형태 403, 그다음 자기 검사(`ProjectCheckStage.kt:155-170`). 사이트가 익명 접근을 막았으면 비로그인은 로그인 폼 303이 아니라 이 단계의 로그인 화면 403을 먼저 받는다(`AccessChainInterceptor.kt` 머리 주석).
- 오류 응답은 **모두 HTML 화면**이다(`K/web/error/ErrorViews.kt`): 404 `error/notfound`, 403 `error/forbidden`, 400 `error/badrequest`, 413 `error/request-text-entity-too-large`, 500 `error/internal-server-error-default`. JSON 응답을 내는 곳은 API 컨트롤러(`IssueApiController`, `IssueFormApiController`, 일괄 변경의 400, 즐겨찾기)뿐이다. 화면 스크립트가 `res.responseText`를 JSON으로 읽는 곳(`Tasklist.js`, `ReceiverList.js`, `Usermenu.js`)은 이 때문에 HTML 응답에서 예외가 날 수 있다.

### 2.2 CSRF

- 서버: `YonaCsrfMatcher`(`K/security/CsrfSupport.kt:24-29`)가 읽기 요청, 외부 API(`/-_-api`), MCP, SVN, Git, LFS, `Authorization` 또는 `Yona-Token` 헤더가 있는 요청을 뺀 나머지 상태 변경 요청에 토큰을 요구한다. 쿠키 `XSRF-TOKEN`을 매 요청에 심는다(`CsrfCookieFilter`).
- 화면: `J/common/yona.Csrf.js`가 같은 출처의 `fetch`, `XMLHttpRequest`에 `X-XSRF-TOKEN` 헤더를, 폼 제출(스크립트의 `form.submit()` 포함)에 `_csrf` 숨은 입력을 단다(`yona.Csrf.js:46, 61-77`).
- 이 영역에서 **GET으로 상태를 바꾸는 요청**이 있다: `GET .../issue/:n/nextstate`(`issue-F7`). 토큰 검사 대상이 아니다.

### 2.3 응답 형태를 가르는 입력

| 입력 | 쓰이는 곳 | 의미 |
|---|---|---|
| `HX-Request: true` | 이슈 목록, 내 이슈, 라벨 목록 | 영역 조각(`AjaxRequests.isHtmx`, `K/web/AjaxRequests.kt`) |
| `X-Requested-With: XMLHttpRequest` | 이슈 목록(JSON), 이슈 삭제, 댓글 삭제 | Ajax. 삭제는 `204 + Location`, 아니면 `303` |
| `Accept: application/json` 선호 | 일괄 변경, 이슈 보기, 라벨 | 일괄 변경은 `{}`, 아니면 `303 → Referer` |
| `format=fragment|xls|html|json` | 목록 | 위와 같음(htmx, Ajax가 우선) |

`yona.http.ajax`는 같은 출처에 `X-Requested-With`를 붙이고 `dataType`에 따라 `Accept`를 정한다(`J/esm/http.js:105, 128`). `yona.Request.send`(삭제, 추천 등 `data-request-method` 요소)는 `X-Requested-With`만 붙이고, 성공하면 상태가 `204`이고 `Location`이 있을 때 그곳으로 이동하고 아니면 새로고침한다. 실패는 사용자에게 알리지 않는다(`J/esm/Request.js:75-102`).

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
| 쪽당 이슈 15, `itemsPerPage` 최대 45 | `IssueListController.kt:210-211`, `MyIssuesController.kt:104-105` |
| 쪽당 리뷰 15 | `K/domain/comment/ReviewSearchService.kt:53` |
| 제목 최대 255(화면 250) | `IssueCreateController.kt:231`, `IssueEditController.kt:163`, `create.html:25` |
| 부모 이슈 후보 최대 300 | `IssueFormSupport.kt:72` |
| 사용자 검색 최대 10(`Content-Range`로 전체 수 알림) | `K/web/api/ApiUserNodes.kt` `MAX_FETCH_USERS` |
| 멘션 이슈 후보 최대 20 | `MentionListController.kt:163` |
| 폼(urlencoded) 본문 한도 102400바이트(멀티파트는 해당 없음) | `K/config/YonaProperties.kt:106`, `K/web/TextBodyLimitFilter.kt:34` |
| 변경 감지 간격 3.6초에서 시작, 1.2배씩, 상한 5분 | `J/esm/service/detectChange.js:34-44` |
| 댓글 색인 앞 60자 | `IssueViewAssembler.kt:242` |
| 제출 단추 재활성화 3초 | `create.html:141`, `edit.html:201` |
| htmx 시간 제한 3초 | `partial-list-wrap.html:10` |

## 3. 권한 행렬(영역 전체)

규칙 정의: `K/access/AccessControl.kt`. 읽기(`isAllowed` 기본 규칙, 214-217행)는 공개 프로젝트(게스트 계정 제외) 또는 구성원, 이슈 공유자, 그룹(조직) 구성원(공개와 보호 프로젝트)이다. 수정(`UPDATE`, 218행)은 구성원 또는 그룹 구성원 + (앞서 `privileged`인 작성자, 프로젝트 관리자, 담당자). **삭제는 이슈, 게시글, 댓글, 코드 리뷰 댓글, 커밋 댓글 모두 작성자와 프로젝트 관리자(조직 관리자, 사이트 관리자 포함)만**(`CONTENT_TYPES`, 195-196행, 추가 개발 항목 D-48). 댓글 생성(`isResourceCreatable`, 80행): 이슈의 작성자, 담당자, 공유자는 항상, 그 밖은 구성원 이상이거나 공개 프로젝트의 로그인 사용자.

| 기능 | 비로그인 | 로그인 비구성원(공개) | 이슈 공유자 | 이슈 담당자 | 구성원 | 이슈 작성자 | 프로젝트 관리자 | 그룹(조직) 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|---|---|---|
| 목록, 보기 | 허용(익명 접근 허용 시) | 허용 | 허용(비공개도) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 초안 보기 | 불가 | 불가 | 불가 | 불가 | 불가 | 허용 | 불가 | 불가 | 불가 |
| 이슈 작성 | 불가(로그인 303) | 허용(공개) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 작성 폼의 담당자, 마일스톤, 라벨 관리 칸 | 없음 | 담당자, 마일스톤 칸 없음 | 해당 없음 | 해당 없음 | 보임 | 보임 | 보임 | 보임 | 보임 |
| 이슈 수정(본문, 상태, 담당자, 마일스톤, 라벨, 마감일, 가중치, 공유자) | 불가 | 불가(작성자 아님) | 불가 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 일괄 변경 막대 보임 | 안 보임 | 안 보임 | 안 보임 | 구성원이면 보임 | 보임 | 구성원이면 | 보임 | 구성원 행이 없으면 안 보임(서버는 허용) | 같음 |
| 일괄 변경 서버 | 403 | 이슈별 `UPDATE` | 같음 | 같음 | 허용 | 같음 | 허용 | 허용 | 허용 |
| 댓글 쓰기 | 불가 | 허용(공개) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 댓글 수정 | 불가 | 본인 것 | 본인 것 | 본인 것 | 허용 | 본인 것 | 허용 | 허용 | 허용 |
| 댓글, 이슈 삭제 | 불가 | 본인 것(이슈는 작성자) | 불가 | 불가 | 본인 것 | 본인 것 | 허용 | 허용 | 허용 |
| 추천, 즐겨찾기 | 불가(로그인 필요) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 지켜보기 | 불가 | 허용(공개) | 비공개면 불가 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 리뷰 목록 | 로그인 303 | 허용 | 비공개면 불가 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 내 이슈 | 로그인 303 | 허용(내 것만) | | | | | | | |
| 라벨 관리 화면 | 불가 | 불가 | 불가 | 불가 | 서버 403(화면 링크는 보임, `issue-F12`) | 불가 | 허용 | 허용 | 허용 |

화면에서 숨기는 것과 서버가 막는 것이 다른 곳은 발견 사항에 따로 적었다(F3, F4, F5, F6, F12, F22).

## 4. 발견 사항 목록

확인한 사실만 적었고 추측은 "추측"이라고 표시했다. 근거 번호는 `파일:줄`이다.

- **issue-F1. (정정, 2026-11-11 브라우저로 확인) 수정 폼의 상태 드롭다운은 동작한다.** 처음 조사는 `new yona.ui.Dropdown`의 호출처가 `issue.MassUpdate.js`와 `issue.Write.js`뿐이라 초기화가 없다고 보았으나, `common/scripts.html:77-82`가 문서의 `.btn-group[data-name]`(`data-activate="manual"`이 아닌 것)을 모두 `yona.ui.Dropdown`으로 초기화한다. Playwright로 `#state`의 "닫힘"을 누르면 숨은 입력 `state`가 `CLOSED`가 되고 라벨이 "닫힘"으로 바뀌는 것을 확인했다. 따라서 폼에 `state`가 실리고 서버가 상태를 바꾼다.
- **issue-F2. 작성, 수정 폼의 마일스톤 새로고침 코드가 도달 불가다.** `issue.Write.js:85-92`는 `.yobicon-refresh`(옵션 `elMilestoneRefresh`) 클릭과 `#options` 안 위임에 걸려 있는데 `create.html`, `edit.html`에 그 요소가 없다(`:113`, `:150`의 `$("#milestoneOption .yobicon-refresh")`는 빈 집합).
- **issue-F3. 가중치 단추와 즐겨찾기 별이 권한과 무관하게 보이고 서버가 거부해도 화면은 조용하다.** `view.html:91`(가중치)에는 `th:if`가 없다. 서버는 `UPDATE` 권한이 없으면 `403 {"message":"Permission denied"}`(`IssueApiController.kt:224`)이고, 클릭 처리기(`view.html:424-440`)에는 실패 처리기가 없다. 즐겨찾기 별(`view.html:26-28`)도 비로그인에게 보이고 서버는 `403` 빈 본문(`FavoriteApiController.kt:40`)이며 `Usermenu.js:129`의 `JSON.parse(failure.responseText)`가 빈 문자열에서 예외를 낸다.
- **[재현됨, R-156]** **issue-F4. 담당자 변경 API가 권한이 없을 때 성공처럼 답한다.** `POST .../issue/:n/assignees`는 `UPDATE`가 없으면 아무것도 바꾸지 않고 `200 {"issue": 경로}`만 돌려준다(`IssueApiController.kt:128-139`, 위키 주석에 v1.6과 같다고 적힘). 화면은 `response.assignee.name`을 읽어(`issue.Assginee.js:87`) 예외가 난다. 정상 화면은 수정 권한이 있을 때만 select를 그리므로 직접 호출에서만 드러난다. 이 메서드에는 `@IsAllowed(READ)`도 없다.
- **[재현됨, R-154]** **issue-F5. 읽기 권한을 검사하지 않는 보조 API.** `findSharer`(`IssueApiController.kt:145`), `detectChange`(231, 로그인만 확인), `commentNotiReceivers`(260, 코드 주석에 "v1.6은 권한을 확인하지 않는다")는 `@IsAllowed`도 읽기 검사도 없다. 비공개 프로젝트의 이슈에 대해 로그인한 아무나 본문 SHA-1, 댓글 수, 마지막 댓글 작성자 이름, 알림 수신자 목록을 받을 수 있다. 이를 거부하는 시험은 찾지 못했다(시험 없음).
- **[재현됨, R-155]** **issue-F6. 댓글 추천 경로가 소속을 검사하지 않는다.** `POST .../comment/{commentId}/vote|unvote`는 `@IsAllowed(READ)`를 경로의 프로젝트에 대해서만 평가하고, 댓글이 그 이슈와 프로젝트에 속하는지는 보지 않는다(`IssueVoteController.kt:48-76`). 읽을 수 있는 프로젝트 경로로 다른(비공개) 프로젝트의 댓글 번호에 추천할 수 있다. 댓글 수정 폼(`IssueCommentController.kt:66-70`)도 `id`로 찾은 댓글의 소속 이슈를 경로와 비교하지 않는다(권한은 그 댓글 기준으로 검사된다).
- **issue-F7. `nextstate`가 GET으로 상태를 바꾼다.** `GET .../issue/:n/nextstate`(`IssueViewController.kt:125-137`)는 열림과 닫힘을 뒤집고 이벤트와 알림을 만든다. v1.6 계약이고 `#dynamic-comment-btn`이 `location.href`로 부른다(`issue.View.js:478`). CSRF 대상이 아니다. 새 UI가 링크 미리 읽기나 프리페치를 쓰면 상태가 바뀐다.
- **issue-F8. 마감일을 지우면 화면에 `(null)`이 찍힌다.** 일괄 변경의 JSON `dueDateMsg`는 마감일이 없으면 `null`(`IssueMassUpdateController.kt:101-109`)이고 `issue.View.js:132`는 `"(" + res.dueDateMsg + ")"`로 그대로 넣는다. 닫힌 이슈의 지난 마감일은 부호 있는 날수(`-3`)다(코드 주석, v1.6과 같다고 적힘).
- **issue-F9. 일괄 변경의 서버 경로 중 화면에 없는 것과 서버 검사가 없는 것.** (a) 삭제 `delete=true`는 서버에 있으나(`IssueMassUpdateController.kt:90-94`) 막대에 삭제 단추가 없다. `issue.MassUpdate.js`의 `welDeleteButton: $("#delete")`는 없는 요소. (b) `issues[n].id`가 경로의 프로젝트에 속하는지 검사하지 않는다(`:85`, 권한은 이슈별). (c) `attachingLabelIds`, `detachingLabelIds`의 프로젝트 소속과 배타 분류를 서버가 검사하지 않는다(`IssueMassUpdateService.kt:73-80`). 배타 분류 해제는 화면 스크립트(`issue.MassUpdate.js:359-372`, `Select2.js` `issuelabel` 동작)가 맡는다. (d) `state`는 `State` 열거형의 어떤 이름이든 받는다(`DRAFT`, `MERGED` 포함, `:64-66`). 화면은 `OPEN`, `CLOSED`만 보낸다. 수정 폼의 `state`도 같다(`IssueEditController.kt:122`).
- **issue-F10. 기존 위키 글이 코드와 어긋난다.** (a) `web/issue-view.md:63-65`와 `web/issue-edit.md:35-37`의 "대기"(3c-3에서 옮긴다)는 낡았다. 구독, 추천, 공유자, 가중치, 일괄 변경, 댓글, 변경 감지, 번역의 서버 쪽은 모두 구현되어 있다. (b) `web/issue-list.md:61`, `web/my-issues.md:35`는 영역을 `div[pjax-container]`로 적었지만 실제 속성은 `div[list-container]`다(`partial-list-wrap.html:10`, `yona.Htmx.js`의 `CONTAINER_MARK`). (c) `web/issue-view.md`는 "멘션 기록은 지우지 않는다(R-24)"라고 적었지만 `IssueService.delete`는 이슈와 댓글의 멘션 기록을 지운다(`IssueService.kt:202, 211`, 주석 "v1.6은 남겨 고아 행이 되었다"). 코드가 맞다. (d) `web/issue-view.md`는 가중치 단추를 수정 권한이 있을 때의 항목으로 묶어 적지만 템플릿은 조건이 없다(F3). (e) `web/issue-edit.md`의 상태 선택 서술은 F1과 어긋난다.
- **issue-F11. 제목 길이와 오류 문구, 마일스톤 소속.** (a) 화면 `maxlength=250`, 서버 255(`MAX_TITLE`). (b) 서버 제목 오류 키 `error.maxLength`의 문구는 `messages-play.properties:12` `Maximum length is {0}`뿐이고(한국어 번역 없음) 템플릿은 인자 없이 `#{${error}}`로 그린다(`create.html:30`). `always-use-message-format: true`이므로 `{0}`가 그대로 보일 것이다(추측, 시험 없음). (c) 작성 시 `milestoneId`가 이슈가 만들어질 프로젝트의 마일스톤인지 서버가 확인하지 않는다(`IssueService.create`의 `milestones.findById`, `IssueService.kt:102`). 화면에서 마일스톤을 고른 뒤 대상 프로젝트를 다른 프로젝트로 바꾸면(`Subtask.js`는 마일스톤을 건드리지 않는다) 다른 프로젝트의 마일스톤이 붙을 수 있다(추측: 화면 경로로 도달 가능한지는 실행하지 않았다). 라벨은 대상 프로젝트가 다르면 버리고(`IssueCreateController.kt:145-147`) 수정에서 옮길 때 마일스톤을 지운다(`IssueService.kt:289`).
- **issue-F12. 라벨 관리 링크의 표시 조건과 서버 권한이 다르다.** 검색 폼의 톱니바퀴(`partial-searchform.html:84`)는 `search.canManageLabels`(프로젝트 구성원 이상, `isProjectResourceCreatable(ISSUE_LABEL)`, `IssueListController.kt:197`)일 때 보이지만 목적지 `GET .../issue/labelsform`은 `@IsAllowed(UPDATE)`(프로젝트 관리자, `IssueLabelController.kt:88-90`)다. 일반 구성원은 403을 받는다. `partial-select-label.html:12`의 `[편집]`은 `view.manager`로 맞게 걸려 있다.
- **issue-F13. 죽은 코드, 중복 id, 쓰이지 않는 속성.** (a) `partial-list.html:59, 149`의 `th:if="${!child.draft or true}"`는 항상 참. (b) `partial-comment.html:39`의 `c.voters.size() == 1 ? 단수 : 복수`는 `size() > 5` 안이라 단수 가지가 도달 불가. (c) `li.post-item`의 `href`, `data-item`, `data-value` 속성을 읽는 스크립트가 없다(`J`에서 `post-item`을 다루는 곳은 `twoColumnMode.js`, `showSubtask.js`, `issue.List.js`, `board.List.js`, `user.View.js`이고 `href`를 읽지 않는다). (d) 중복 id: `two-column-mode-checkbox`(`two-column-mode-checkbox-area.html:9`와 `show-subtasks-checkbox.html:9`), `comments`와 `timeline`(`view.html:146-147, 244-245`), `copyEmailBtn`(F20). (e) `issue.View.js`의 `_setTimelineUpdateTimer`(호출 주석 처리, 29행), `_isDockedInspectorOpened`, `_unsetTimelineUpdateTimer`는 쓰이지 않는다. (f) `Tasklist.js:18-26`의 `$this.closest()`는 인자가 없어 빈 집합이다. (g) `view.html`의 `.watcher-list`(141행)는 채우는 코드가 없다. (h) `my-list.html:44`의 `.title-cell > .label` 선택자는 이 화면 마크업과 맞지 않는다. (i) `review.List.js`는 목록 영역에 htmx 속성이 없어 `.issue-list-wrap` 클래스만 이슈 목록과 공유한다. (j) 작성, 수정 제출 단추는 `type=submit`인데 클릭 처리기가 다시 `form.submit()`을 부른다(`create.html:50, 138-146`). 중복 제출 여부는 확인하지 못했다(추측).
- **issue-F14. 마감일 검증 오류가 화면에 나오지 않는다.** 서버는 `errors["dueDate"]`를 채우지만(`IssueCreateController.kt:123`, `IssueEditController.kt:94`) 두 템플릿은 `errors['title']`만 그린다(`create.html:29-31`, `edit.html:38-40`). 400으로 폼을 다시 그리고 제목만 오류가 있으면 문구가 나온다. 화면의 `isValidDate`가 먼저 막으므로 직접 요청에서만 드러난다.
- **issue-F15. 하드코딩된 문구.** 한국어: `partial-select-subtask.html:22-23`의 `이미 부모 이슈입니다.`(메시지 키 없음). 영어: `view.html:91`의 `: Upvote`, `: Down vote`, `my-partial-list.html:88`의 `Due date: `, `J`의 `Set to default:`(`my-list.html:31`), `New comment by`, `Reload page`, `Issue updated!`(`detectChange.js:64-74`), `Issue will be moved or written to`(`Subtask.js:33`), `Label: `(`TitleHeadAutoCompletion.js:86`), `Draft saved`(`temporarySaveHandler.js:131`), `Update failed:`(`Usermenu.js`). 일본어, 러시아어, 우즈베크어 메시지 파일에는 이 영역이 쓰는 키 일부가 없어 기본 `messages.properties`로 대체된다(일본어, 러시아어, 우즈베크어에서 이 영역 템플릿이 쓰는 97개 키 중 각각 수십 개가 없다, `messages_ko_KR.properties`와 `messages.properties`에는 누락 없음).
- **issue-F16. 변경 감지 폴링.** 비로그인이 보기 화면을 열어도 폴링이 계속된다(서버 401을 조용히 무시, `detectChange.js:59-61`, `IssueApiController.kt:234`). 서버는 요청의 `lastUpdateDate`를 읽지 않고 `issueUpdateDate`만 돌려주며 화면은 쓰지 않는다.
- **issue-F17. 빠른 검색 "내게 할당"이 구성원이 아니면 필터가 비는 것으로 보인다(추측).** 링크는 로그인하면 항상 보이지만(`partial-list-quicksearch.html:17-23`) 검색 폼의 담당자 select에는 "내게 할당" 옵션이 구성원일 때만 있다(`partial-searchform.html:45`). 링크가 `assigneeId` select에 `.val(내 번호)`를 하는데 일치하는 옵션이 없으면 값이 비어 요청에서 조건이 빠질 수 있다. 실행해 보지 않았다.
- **issue-F18. 키보드 쪽 이동은 전체 이동이다.** `LEFT`/`RIGHT`는 `setKeymapLink`(`document.location.href = 주소`)로 이동하고(`Pagination.js:311-315`, `ShortcutKey.js` `setKeymapLink`) 링크 클릭은 `hx-boost`로 영역만 교체한다.
- **issue-F19. 부모 이슈 후보에 초안과 닫힌 이슈가 들어 있다.** `IssueListService.parentCandidates`(`IssueListService.kt:135-137`)는 `parent is null`인 이슈를 최신순 300개 가져오며 상태나 초안 조건이 없다. 다른 사람의 초안이 후보와 제목으로 노출될 수 있다(코드상 사실, 실행 확인 없음).
- **issue-F20. `#copyEmailBtn`이 여러 번 그려진다.** `partial-voter-list.html:29`는 추천자 모달마다 같은 id를 쓰고(이슈 모달과 5명 초과 댓글 모달), `view.html:402`는 `$("#copyEmailBtn")`로 첫 번째 요소에만 처리기를 단다. 댓글 추천자 모달의 이메일 복사 단추는 이슈 모달이 없으면 첫 댓글의 것만 동작한다(jQuery 아이디 선택자의 일반 동작에 따른 추론).
- **issue-F21. 비로그인 사용자의 번역, 즐겨찾기 단추.** 번역 단추(`view.html:109`)는 설정만 보고 그려지고 서버는 `@AnonymousCheck(requiresLogin)`(`TranslationApiController.kt:31`)이라 로그인 폼 303으로 응답한다. 화면은 JSON을 기대하므로 `.done`이 호출되지 않아 아무 일도 없고 단추는 `.one`으로 소모된다(추측). 즐겨찾기는 F3.
- **issue-F22. 댓글의 "이 댓글로 새 이슈" 링크는 비로그인에게도 보인다.** `partial-comment.html:33`. 누르면 `GET /user/issues/new?commentId=`가 최근 방문 프로젝트가 없어 경고 `project.is.empty`와 함께 첫 화면을 그린다(`IssueCreateController.kt:85-88, 199-204`). 로그인한 사용자라도 방문한 프로젝트가 없으면 같다. 그 프로젝트에 대한 생성 권한은 이 경로에서 확인하지 않는다(폼 화면이 아니라 `createView`만 그린다). 실제 제출 때 `@IsCreatable`이 막는다.
- **issue-F23. 일괄 변경이 `Referer` 헤더로 리다이렉트한다.** 브라우저 폼 제출의 응답은 `303 Location: <요청의 Referer>`이고 없으면 목록(`IssueMassUpdateController.kt:111`). 헤더 값을 검증하지 않는다(v1.6 동작).
- **issue-F24. 단축키 도움말과 등록이 어긋난다.** 도움말(`help/keymap.html`)은 `issueDetail`에서 `L`, `E`, `N`을 권한과 무관하게 보이지만 보기 화면은 `E`를 `canUpdate`, `N`을 `menuSetting.issue`일 때만 등록한다(`view.html:319-327`). 목록은 `menuSetting.issue` 검사 없이 `N`을 등록한다(`list.html:33-35`). 서버는 프로젝트 메뉴 설정(`menuSetting.issue`)을 이슈 컨트롤러에서 검사하지 않는다(`IssueListController`, `IssueCreateController`에 검사 없음). 메뉴를 끈 프로젝트에서도 URL로 접근할 수 있다.
- **issue-F25. 인라인 이벤트 속성.** `th:attr="onclick=..."`(인라인 `onclick`, `view.html:112-113, 233-234`)로 수정 이동을 구현한다. 새 UI가 CSP로 인라인 이벤트를 막으면 동작하지 않는다.

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
| `issue/view.html` | 467 | issue-view.md 전체 |
| `reviewthread/list.html` | 118 | issue-my.md 나 |

`issue/` 22개 + `reviewthread/` 1개 = 23개 전체를 읽었다(22개 + 1개, 전체 대비 23/23). 함께 읽은 공통 조각(다른 담당의 글에 정리된다): `common/comment-form`, `comment-update-form`, `child-comments`, `editor`, `file-uploader`, `two-column-mode-checkbox-area`, `show-subtasks-checkbox`, `comment-delete-modal`, `tasklist-bar`, `my-series-menu-tab`, `help/keymap`, `common/select2`, `common/calendar`, `milestone/partial-status`. 읽은 스크립트: `issue.View.js`, `issue.List.js`, `issue.MassUpdate.js`, `issue.Write.js`, `issue.Assginee.js`, `issue.Sharer.js`, `detectChange.js`, `Tasklist.js`, `SubComment.js`, `ReceiverList.js`, `Subtask.js`, `TitleHeadAutoCompletion.js`, `showSubtask.js`, `twoColumnMode.js`, `review.List.js`, `Comment.js`, `CommentForm.js`, `CommentAttachmentsUpdate.js`(앞부분), `Request.js`, `ShortcutKey.js`, `Pagination.js`, `yona.Htmx.js`, `yona.Csrf.js`, `http.js`(앞부분), `Dropdown.js`, `Select2.js`(`issuelabel` 부분), `temporarySaveHandler.js`, `Usermenu.js`(즐겨찾기 부분).
