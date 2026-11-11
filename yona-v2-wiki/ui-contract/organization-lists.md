---
title: UI 계약 - 조직의 이슈, 게시판, 풀 리퀘스트 목록
type: ui-contract
sources:
  - src/main/resources/templates/organization/{issue-list,board-list,pullrequest-list}.html
  - src/main/kotlin/dev/yona/web/organization/{OrganizationListController,OrganizationListAssembler}.kt
  - src/main/kotlin/dev/yona/web/board/OrganizationBoardController.kt
  - src/main/kotlin/dev/yona/domain/project/OrganizationService.kt
  - src/main/resources/static/assets/javascripts/esm/service/{issue.List,board.List,twoColumnMode}.js
  - src/main/resources/static/assets/javascripts/esm/Pagination.js
  - src/main/resources/static/assets/javascripts/common/yona.Htmx.js
updated: 2026-11-11
---

# UI 계약: 조직 목록 화면 셋

공통 틀(머리글, 메뉴), 권한 용어, CSRF, 오류 화면은 [organization.md](organization.md) 2절을 따른다. 동작 요약은 [../web/organization-lists.md](../web/organization-lists.md)에 있다.

## 1. 공통 규칙

- 세 화면 모두 로그인하지 않아도 볼 수 있다(`@AnonymousCheck(requiresLogin=false, displaysFlashMessage=true)`. 사이트가 익명 접근을 막으면 로그인 폼으로 303). 조직이 없으면 404 `error.notfound.organization` (OrganizationListController.kt:36, 51; OrganizationBoardController.kt:24, 37).
- 대상 프로젝트는 `OrganizationService.visibleProjects(조직, 보는 사람)`이다(규칙은 organization.md 5.1절). 프로젝트 선택기에서 이름을 고르면 그 가운데 보이는 것만 대상이 되고 보이지 않는 이름은 무시한다. 이름 비교는 대소문자를 무시한다 (OrganizationListController.kt:122-123).
- 프로젝트 선택 인자는 `projectNames[]`와 `projectNames` 둘 다 읽고 빈 값은 버린다.
- 쪽 번호 `pageNum`은 1부터. 숫자가 아니면 400. 쪽 번호 위젯은 `yona.Pagination.update`가 현재 주소(`document.URL`)의 쿼리를 유지한 채 `pageNum`만 바꿔 링크를 만든다 (Pagination.js).
- 두 단 보기(`#two-column-mode` 체크박스, `common/two-column-mode-checkbox-area`): 켜면 `localStorage.useTwoColumnMode='true'`를 저장하고, 제목 링크(`.title`, `.twoColumeModeTarget`) 클릭을 가로채 오른쪽 offcanvas `#pageslide`에 `iframe`으로 연다(300ms 지연). 같은 제목을 다시 누르면 닫는다. 끄면 `'false'`. 열려 있으면 `.left-menu`를 숨긴다. 이슈와 게시판 목록이 쓴다 (twoColumnMode.js).
- 메시지 키가 빠진 곳은 없다(정적 키 전수 검사).

## 2. 게시판 목록 `organization/board-list.html`

### 2.1 개요

`GET /organizations/{name}/boards` → `OrganizationBoardController.organizationBoards` (OrganizationBoardController.kt:34-65). 쿼리: `orderBy`, `orderDir`, `filter`, `pageNum`, `projectNames[]`. 쪽 크기 15 (:71). 모델: `title`(`menu.board`), `orgView`, `cond`, `orderBy`, `items`, `totalRows`, `totalPageCount`, `sortLinks`, `projectOptions`. 틀은 `organization-layout`. 공지도 일반 글과 섞여 나온다.

### 2.2 레이아웃

`div.search-wrap.underline`(폼 `#option_form`: 프로젝트 다중 선택 `col-lg-7`, 검색 `col-lg-4`, 두 단 보기 체크박스) 아래, `totalRows == 0`이면 빈 상태 `error-wrap`(`post.is.empty`), 아니면 정렬줄(`totalRows > 1`일 때만)과 `ul.post-list-wrap`, 빈 `div.write-btn-wrap`, `#pagination` (board-list.html:17-79). 줄은 `li.post-item.title`: 작성자 아바타(`hide-in-mobile`), 제목, 작성자 이름, 프로젝트 이름 링크, `#번호`, 시각, 댓글 수(0이면 숨김).

### 2.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 프로젝트 선택 `#projects` (`select2`, 다중, `data-placeholder=organization.choose.projects`) | 선택 | 항상 | 값이 바뀌면 `#option_form`을 제출한다. 선택지는 보이는 프로젝트 전체, 이미 고른 것은 `selected`, 옵션마다 `data-avatar-url`(로고) | `GET .../boards?orderBy=&orderDir=&projectNames[]=...&filter=` | 같은 화면 전체 | 보이는 프로젝트만 | board-list.html:22-24, 89-91 |
| `orderBy`, `orderDir` | 숨김 입력 | 항상 | 현재 정렬을 검색에 실어 유지 | | | | board-list.html:19-20 |
| 검색 `filter` (`title.searchByKeyword`) | 입력 | 항상 | 제목과 본문 키워드 | | | | board-list.html:27 |
| 돋보기 | 제출 | 항상 | 폼 제출 | | | | board-list.html:28 |
| 정렬 링크 (`sortLinks`: `updatedDate`, `createdDate`, `numOfComments`) | 링크 | `totalRows > 1` | 현재 정렬이면 방향을 뒤집고(`active`, 화살표 `down`), 아니면 내림차순으로 | `GET .../boards?orderBy={필드}&orderDir={asc|desc}` (**`filter`와 `projectNames`는 싣지 않는다**) | 같은 화면 | | board-list.html:39-45; OrganizationBoardController.kt:51-54 |
| 작성자 아바타, 이름 링크 | 링크 | 작성자가 있을 때(이름이 비면 `issue.noAuthor` 글자) | 사용자 화면 | `GET /{loginId}` | | | board-list.html:48-58 |
| 제목, 프로젝트 링크, 댓글 수 | 링크 | 항상 | 글 `{프로젝트}/post/{번호}`, 프로젝트, `...#comments` | | | 글 읽기 권한 | board-list.html:59-67 |
| 줄 `li[href]` | 줄 | 항상 | 줄에 `href` 속성이 있다. 줄 전체 클릭 이동은 이 화면의 스크립트가 하지 않는다 | | | | board-list.html:47 |
| 쪽 번호 | 위젯 | 항상 (`nTotalPages = totalPageCount`) | 쪽 이동 | `GET ...?pageNum=N` | | | board-list.html:75, 84-87 |

스크립트: `twoColumnMode`(모듈), `board.List`(모듈) + 인라인에서 `$yona.loadModule("board.List", {elPagination, nTotalPages})` (board-list.html:80-93). 하는 일: 쪽 번호 그리기, 목록 줄 `hover` 배경(`#fafafa`/`#fff`), `.title-prefix` 클릭으로 검색어 채우고 제출, 두 단 보기 초기화. `common/select2`를 불러 select2를 연다. 단축키 없음.

## 3. 이슈 목록 `organization/issue-list.html`

### 3.1 개요

`GET /organizations/{name}/issues` → `OrganizationListController.issues` (OrganizationListController.kt:48-75). 쿼리: `pageNum`, `itemsPerPage`(기본 15, 최대 45), `projectNames[]`, `state`(기본 `open`), `orderBy`, `orderDir`, `filter`, `authorId`, `assigneeId`, `mentionId`, `milestoneId`, `labelIds`, `commenterId`, `sharerId`, `favoriteId`, `dueDate`(`yyyy-MM-dd`), `commentedCheck`. 숫자나 날짜 형식이 틀리면 400 (IssueListController.kt:222-242의 `IssueConditionBinder`). 모델: `title`, `orgView`, `cond`, `items`(`OrganizationIssueRow`), `pageIndex`, `totalPageCount`, `openCount`, `closedCount`, `meId`(비로그인이면 `null`), `projectOptions`.

**`HX-Request` 차이**: htmx 요청이면 `organization/issue-list :: listWrap` 조각만, 아니면 전체 화면. JSON 구분은 없다 (OrganizationListController.kt:62-63).

### 3.2 레이아웃

`div.page-wrap > [list-container] div.row.issue-list-wrap`. 왼쪽 `left-menu col-lg-2`(빠른 필터 `ul.lst-stacked`, 검색 폼 `#search`), 오른쪽 `col-lg-10#span10`(열림과 닫힘 탭 + 두 단 보기, 정렬줄, 목록, 쪽 번호). 목록이 비면 `error-wrap`(`issue.is.empty`). `list-container`에는 `hx-target="this" hx-select="div[list-container] > *" hx-swap="innerHTML show:window:top" hx-push-url="true"`와 `hx-request='{"timeout":3000}'`가 붙어, 폼 제출과 쪽 번호 링크가 이 영역만 교체하고 주소를 갱신한다 (issue-list.html:18-19).

### 3.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 빠른 필터 "전체" (`issue.list.all`) | 링크 `a[list-filter]` | 항상. 담당, 작성, 멘션이 모두 비면 `li.active` | 숨은 `assigneeId`, `authorId`, `mentionId` 입력을 비우고 폼을 htmx 제출 | `GET .../issues?...` (htmx) | 영역 교체 | | issue-list.html:23-25; issue.List.js `_onClickSearchFilter` |
| 빠른 필터 "나에게 할당" (`issue.list.assignedToMe`) | 링크 | `meId != null` | `assigneeId=meId` | 위와 같다 | | 로그인 | issue-list.html:26-28 |
| 빠른 필터 "내가 작성" (`issue.list.authoredByMe`) | 링크 | `meId != null` | `authorId=meId` | | | | issue-list.html:29-31 |
| 빠른 필터 "나를 멘션" (`issue.list.mentionedOfMe`) | 링크 | `meId != null` | `mentionId=meId` | | | | issue-list.html:32-34 |
| 프로젝트 선택 `#projects` | select2 다중 | 항상 | 바뀌면 `yona.Htmx.submit("#search")` (인라인과 영역 교체 뒤 재연결 두 군데에서 연결) | `GET .../issues?projectNames[]=...` | | 보이는 프로젝트만 | issue-list.html:38-40, 152-154; issue.List.js `_addEventAtOrganizationIssueSearchPage` |
| 숨김 입력 `orderBy`, `orderDir`, `state`, `authorId`, `assigneeId`, `mentionId` | 숨김 | 항상 | 필터 상태를 검색 폼이 들고 있다. `authorId`, `assigneeId`, `mentionId`에 `data-search` | | | | issue-list.html:43-48 |
| 검색 `filter` + 돋보기 | 입력, 제출 | 항상 | 폼 `#search`(`method=get`, `th:action`과 `hx-get` 같은 주소) 제출 | `GET /organizations/{name}/issues?filter=...` | | | issue-list.html:37, 52-53 |
| 탭 "열림", "닫힘" (`a[state]`) | 탭 링크 | 항상. 현재 `state`에 `active`. 건수 배지 `openCount`, `closedCount` | 숨은 `state`를 바꿔 제출 | | 건수는 같은 조건에서 상태만 바꿔 센다 | | issue-list.html:61-66; issue.List.js `_onClickStateTab`; OrganizationListController.kt:71-72 |
| 정렬 링크 (`dueDate`, `updatedDate`, `createdDate`, `numOfComments`, 글자 `common.order.*`) | 링크 `a[orderBy]` | 목록이 비어 있지 않고 `items.size() > 1` | 현재 정렬이면 방향을 뒤집고 `active`, 아니면 `orderDir=desc` | htmx 제출 | | | issue-list.html:70-78; issue.List.js `_onClickListOrder` |
| 이슈 줄 | `li.post-item[th:href]` | 항목마다 | 제목, 작성자, 시각, 마일스톤(`milestoneVisible`일 때), 댓글과 추천 수, 프로젝트 이름, `#번호`, 라벨, 담당자 아바타, 마감 표시(`overdue` 클래스) | | | | issue-list.html:81-131 |
| 제목 링크, 프로젝트 링크 | 링크 | 항상 | `{프로젝트}/issue/{번호}`, 프로젝트 | | | 읽기 권한 | issue-list.html:89, 113 |
| 라벨 배지 | 링크 | 라벨마다 | `{프로젝트}/issues?state={상태}&labelIds={id}` (**조직이 아닌 프로젝트의 이슈 목록**) | | | | issue-list.html:116 |
| 쪽 번호 `#pagination` | 위젯 | 목록이 비어 있지 않을 때 (`data-total`) | `a[list-page]`에 `hx-boost`를 붙여 영역 교체 | `GET ...?pageNum=N` | | | issue-list.html:135; Pagination.js |

스크립트: `twoColumnMode`, `issue.List`(모듈) + `$yona.loadModule("issue.List")` (issue-list.html:146-155). `issue.List`는 `htmx:afterSwap`이 `[list-container]`에 일어나면 쪽 번호, select2, 달력, 두 단 보기를 다시 초기화한다. 검색 폼의 `htmx:beforeRequest`에서 `[data-toggle=calendar]`의 마감일이 올바른 날짜가 아니면 `issue.error.invalid.duedate`를 알리고 취소한다(이 화면에는 달력 입력이 없다). htmx 공통(`yona.Htmx`): 영역 교체 응답에 `list-container` 문자열이 없으면 교체하지 않고 전체 화면으로 이동, 응답 오류, 전송 오류, 3초 시간 초과도 전체 화면 이동(`window.location.assign`), 진행 표시 `NProgress`. 단축키 없음.

## 4. 풀 리퀘스트 목록 `organization/pullrequest-list.html`

### 4.1 개요

`GET /organizations/{name}/pullrequests?category=open|closed&filter=&contributorId=&pageNum=`, `GET /organizations/{name}/closedPullrequests` (`category=closed`과 같다) → `pullRequestList` (OrganizationListController.kt:78-112). `category`가 `open`이 아니면 모두 닫힌 목록이다. 쪽 크기 15 (PullRequestSearch.kt:89). 모델: `title`, `orgView`, `filter`, `open`, `openAction`, `closedAction`, `searchAction`, `openCount`, `closedCount`, `rows`, `totalPages`.

### 4.2 레이아웃

`div[list-container].row.cb`: 왼쪽 `left-menu col-lg-2 search-wrap hide-in-mobile`(검색 폼 `#search`), 오른쪽 `col-lg-10`에 탭 둘(`ul.pullrequeset-tab-menu`)과 `#list`. 줄 `li.post-item.title.row`: 보낸 사람 아바타, 번호, 제목 머리말(`headerWordsHtml`, 이스케이프 없이 출력되는 HTML), 제목(충돌이면 `conflict` 클래스), 라벨, 보낸 사람 이름, 시각, 프로젝트, 리뷰 진행 막대(`progress`가 있을 때), 오른쪽 `hide-in-mobile` 열(담당자, 받는 사람 아바타, 상태 글자). 비면 `error-wrap`(`pullRequest.is.empty`) (pullrequest-list.html:17-109).

### 4.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 검색 `filter` + 돋보기 | 입력, 제출 | 항상 (모바일에서 숨김) | 폼의 `action`은 현재 탭의 주소(`searchAction`) | `GET /organizations/{name}/pullrequests?filter=` 또는 `closedPullrequests` | 같은 화면 전체 | | pullrequest-list.html:19-26 |
| 탭 "열림", "닫힘" (`a[data-type=state]`, `data-url`) | 링크 | 항상. 현재 탭에 `active`, 배지 `openCount`, `closedCount` | 클릭하면 `#search`의 `action`을 `data-url`로 바꿔 제출한다(`filter` 유지, `pageNum` 초기화) | 위와 같다 | | | pullrequest-list.html:29-36, 110-116 |
| 줄, 제목 링크 | 링크 | 항목마다 | `{프로젝트}/pullRequest/{번호}` | | | 읽기 권한 | pullrequest-list.html:41, 50 |
| 라벨 배지 | 링크 | 라벨마다 | 풀 리퀘스트로 이동(라벨 필터는 아님) | | | | pullrequest-list.html:51 |
| 리뷰 진행 | 링크 | `progress != null` | `{풀 리퀘스트}/changes`. 툴팁은 `pullRequest.review.closed / pullRequest.review.total` | | | | pullrequest-list.html:59-69 |
| 담당자, 받는 사람 아바타 | 링크 | 있을 때 | 사용자 화면. 둘 다 없으면 빈 아바타 | | | | pullrequest-list.html:73-81 |
| 상태 글자 | 문구 | 항상 | 충돌이면 `pullRequest.state.conflict`, 아니면 `pullRequest.state.{open|closed|merged}` 등 | | | | pullrequest-list.html:82 |
| `.title-prefix` | 클릭 | 머리말 HTML 안에 있을 때 | 그 글자를 `filter` 입력에 넣고 폼 제출 | | | | pullrequest-list.html:97-103 |
| 쪽 번호 | 위젯 | 줄이 있을 때 | `yona.Pagination.update($("#pagination"), totalPages)` | `GET ...?pageNum=N` | | | pullrequest-list.html:85, 96 |

스크립트는 모두 템플릿 인라인이다. 모듈 로딩과 단축키, 로컬 저장소는 없다. `contributorId` 쿼리는 서버가 받지만 화면에 입력이 없다.

## 5. 발견 사항 (이어서, organization-F17 이후)

| 번호 | 내용 | 근거 |
|---|---|---|
| organization-F17 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 게시판 목록의 정렬 링크는 `orderBy`, `orderDir`만 싣고 `filter`와 `projectNames[]`를 버린다. 정렬을 바꾸면 검색어와 프로젝트 선택이 풀린다. `board.List.js`는 `#pagination a` 클릭과 `input[name=pageNum]`을 쓰지만 템플릿에 그 입력이 없고, 쪽 번호가 그려지기 전에 `#pagination a`를 찾아 연결이 비어 있다(죽은 코드). 쪽 이동은 `Pagination` 위젯의 URL 링크가 한다 | board-list.html:41; OrganizationBoardController.kt:51-54; board.List.js `_initElement`, `_onClickPage` |
| organization-F18 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 조직 이슈 목록의 빠른 필터 링크는 `data-project-names`, `data-milestone-id`를 싣지만 폼에 `[data-search=projectNames]`, `[data-search=milestoneId]` 입력이 없어 무시된다. 라벨 배지는 조직 목록이 아니라 프로젝트의 이슈 목록으로 간다. 상태 탭에 "전체"가 없다(서버는 `state=all`을 받는다). 빠른 필터가 누르는 순간 선택된 프로젝트는 폼의 select가 그대로 들고 있어 유지된다 | issue-list.html:24, 38, 116; issue.List.js `_onClickSearchFilter` |
| organization-F19 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 풀 리퀘스트 목록은 줄이 있을 때만 `<div id="pagination">`을 `<ul>` 안에 둔다(구조상 올바르지 않음, v1.6 마크업). 탭 전환은 `pageNum`을 초기화한다. 이슈 목록만 htmx로 부분 교체하고 게시판과 풀 리퀘스트는 전체 화면 이동이다 | pullrequest-list.html:85; issue-list.html:19 |

## 6. 시험 근거

`OrganizationListsTest`(26), `OrganizationBoardTest`(12). 스크립트는 `listviews.test.mjs`, `twocolumn.test.mjs`, `htmx.test.mjs`. `contributorId`, 두 단 보기와 조직 목록의 조합 시험은 찾지 못했다.
