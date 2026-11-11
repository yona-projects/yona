---
title: 내 이슈와 리뷰 목록 화면 계약
type: ui-contract
sources:
  - src/main/resources/templates/issue/my-list.html
  - src/main/resources/templates/issue/my-partial-search.html
  - src/main/resources/templates/issue/my-partial-list.html
  - src/main/resources/templates/reviewthread/list.html
  - src/main/kotlin/dev/yona/web/issue/MyIssuesController.kt
  - src/main/kotlin/dev/yona/web/code/ReviewListController.kt
  - src/main/kotlin/dev/yona/domain/comment/ReviewSearchService.kt
  - src/main/resources/static/assets/javascripts/esm/service/review.List.js
updated: 2026-11-11
---

# 내 이슈, 리뷰 목록 계약

공통 계약, 권한 행렬, 발견 사항(`issue-F1`...)은 [issue.md](issue.md)에 있다. 서버 서술은 [web/my-issues.md](../web/my-issues.md), [web/review-list.md](../web/review-list.md)에 있다. 목록 스크립트, 쪽 나누기, 두 열 보기, 하위 이슈 보기, htmx 영역 교체의 계약은 프로젝트 이슈 목록과 같으므로 [issue-list.md](issue-list.md) 3.3, 4, 5절을 본다. 경로 약어: `T/` = `src/main/resources/templates/`, `K/` = `src/main/kotlin/dev/yona/`, `J/` = `src/main/resources/static/assets/javascripts/`, `TEST/` = `src/test/kotlin/dev/yona/web/issue/`.

# 가. 내 이슈 (`issue/my-list`, `my-partial-search`, `my-partial-list`)

## 1. 화면 개요

| 항목 | 내용 |
|---|---|
| URL | `GET /user/issues`(`K/web/issue/MyIssuesController.kt:46`) |
| 어노테이션 | 클래스 `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)`(34). 비로그인은 `303 → /users/loginform?redirectUrl=/user/issues`와 경고 `user.login.alert` |
| 템플릿 | 전체 `issue/my-list`(레이아웃 `layout/site-layout :: siteLayout(#{issue.myIssue}, 'SITE_HOME', myListPage)`, `my-list.html:11`), 조각 `issue/my-partial-search :: mySearch`(htmx 요청) |
| 모델 | `title="issue.myIssue"`, `menuTab="TAB_MY_ISSUES"`, `cond`, `items`(`UserIssueRow`), `pageIndex`, `totalPageCount`, `openCount`, `closedCount`, `meId`, `quickCounts{mentioned, shared, favorite}`(`MyIssuesController.kt:79-90`) |
| 응답 분기 | `HX-Request` 조각(`Cache-Control: no-cache, no-store`), `X-Requested-With`만이면 JSON(`link` 없음), 그 밖은 전체. `format` 쿼리는 무시(항상 html, 57-61). 엑셀 없음 |
| 기본값 | 조건(담당, 작성, 멘션, 댓글, 공유, 즐겨찾기)이 하나도 없으면 `assigneeId = 나`(51-52). `orderBy`가 비었으면 `updatedDate`(53). 쪽당 15개, `itemsPerPage` 최대 45(`:104-105`) |
| 읽기 권한 | 프로젝트별 읽기 권한을 따지지 않았던 v1.6과 달리 v2는 읽을 수 있는 프로젝트의 이슈만 찾는다(`IssueSearchService.kt:213-218`, 시험 `TEST/MyIssuesTest.kt:119`) |

## 2. 레이아웃 계약

- `.page-wrap-outer > .page-wrap`(프로젝트 레이아웃이 아니라 사이트 레이아웃, `my-list.html:13-14`). 위에 `common/my-series-menu-tab :: mySeriesMenuTab('TAB_MY_ISSUES')`(알림, 내 이슈, 내 파일 탭 + 기본 로그인 페이지 설정 단추), 그 아래 `my-partial-search :: mySearch`.
- `div[list-container].row.issue-list-wrap`(htmx 교체 영역, 속성은 프로젝트 목록과 같음, `my-partial-search.html:10`): 왼쪽 `.left-menu.col-lg-2 > .inner.advanced`(빠른 검색 6개 + 검색어 폼), 오른쪽 `col-lg-10#span10`(탭, 정렬, 목록, 쪽 나누기).
- 모바일: 줄의 작성자 열은 `hide-in-mobile`로 숨고 대신 `meta` 안 `hide show-in-mobile` 자리에 작성자가 나온다(`my-partial-list.html:68-79`), 담당자 아바타 열도 숨는다(93행). 일괄 변경 막대가 없고 선택 상자도 없다.
- 상태: 이슈가 없으면 `div.error-wrap`(`issue.is.empty`)만(정렬, 쪽 나누기 없음, 단축키 도움말도 없음, `my-partial-search.html:98-101`). 있으면 정렬 줄(`.filter-wrap.small-heights`, 2개 이상일 때 정렬 링크), 목록, `#pagination`.

## 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 기본 로그인 페이지 설정 `#setDefaultLoginPage` | 단추 | `requestPath != '/'` 이고 `requestPath.substring(1) != currentUser.loginDefaultPage`(모바일 숨김, 팝오버) | 클릭하면 요청 후 `Set to default: 경로` 알림 3초와 단추 숨김, 실패는 `set Default page failed: ...` 경고 | `POST /user/defultLoginPage?path=user/issues`(주소를 문자열로 이어 만들고 인코딩하지 않는다) | 200 `{"defaultLoginPage": 경로}`. 외부로 가는 경로는 400 | 로그인(비로그인은 저장 없이 200) | `common/my-series-menu-tab.html:13-17`, `my-list.html:27-37`, `K/web/home/HomeController.kt:58-70` |
| 빠른 검색 6개: "내게 할당", "내가 작성", "내가 댓글", "내가 멘션됨", "내게 공유됨", "즐겨찾기" | 링크 `a[list-filter]` | 항상. `active`는 해당 조건 값이 `meId`일 때. 멘션, 공유, 즐겨찾기 옆 `(개수)`는 검색어가 비어 있을 때만 | 클릭하면 `data-*`로 `assigneeId`, `authorId`, `commenterId`, `mentionId`, `sharerId`, `favoriteId`를 하나만 내 번호로 채우고 나머지는 비운 뒤 제출 | `GET /user/issues?...`(HX-Request) | 조각 교체 | 로그인 | `my-partial-search.html:14-46` |
| 검색어 폼 `#search`(`hx-get=/user/issues`) | 폼 | 항상 | 숨은 입력 `orderBy`, `orderDir`, `state`, `authorId`, `commenterId`, `assigneeId`, `mentionId`, `sharerId`, `favoriteId`(`data-search`)와 `filter`(placeholder `issue.search`). 돋보기는 `type=submit` | `GET /user/issues?filter=...` | 조각 교체 | 로그인 | `:49-65` |
| 열림, 닫힘 탭 | 링크 `a[state]` | 항상 + 개수 | `state` 숨은 입력 | `state=open|closed` | 조각 교체 | | `:69-79` |
| 두 열 보기, 하위 이슈 보기 체크 | | 항상 | 프로젝트 목록과 같다 | | | | `:80-81` |
| 정렬 링크 | `a[orderBy]` | `items.size() > 1` | 프로젝트 목록과 같다 | `orderBy=&orderDir=` | | | `:85-92` |
| 프로젝트 이름 링크 | 링크 | 항상 | 프로젝트 홈 | `GET /{owner}/{project}` | | 읽기 | `my-partial-list.html:15` |
| 번호 `#n` | 글자(링크 아님) | 항상 | | | | | `:17` |
| 제목 링크 | 링크 | 항상(가중치 화살표, 댓글과 추천 수 링크, 하위 이슈 진행과 부모 링크, 라벨 링크 `{프로젝트}/issues?state=&labelIds=`, 접힌 하위 이슈 목록은 프로젝트 목록과 같다. 이 화면의 라벨 링크는 `href`가 실제 링크이고 `#` 아님) | 이슈 보기 | `GET /{owner}/{project}/issue/{n}` | | 읽기 | `:21-50` |
| 작성자 | 링크 | 작성 탭이 아닐 때(`!authoredTab`) 작성자 이름, 없으면 `issue.noAuthor` | 사용자 페이지 | `GET /{loginId}` | | | `:68-81` |
| 시각 | 글자 | 시각 기준은 `updatedDate`(기본 정렬)에 맞춘 `row.timeText`, 툴팁 `timeTitle` | | | | | `:82` |
| 마일스톤 태그 | 링크 | `row.milestoneVisible` | 마일스톤 보기 | `GET {프로젝트}/milestone/{id}` | | | `:83-86` |
| 마감일 | 아이콘과 글자 | 마감일이 있을 때. `row.overdue`면 `overdue` 클래스. 툴팁 `Due date: 날짜`(하드코딩 영어) | | | | | `:87-92` |
| 담당자 아바타 | 링크 | `!assignedTab and 담당자 있음`(모바일 숨김) | 사용자 페이지 | | | | `:93-100` |

`authoredTab`(= `meId == cond.authorId`)이면 작성자 열을 숨기고, `assignedTab`(= `meId == cond.assigneeId`)이면 담당자 열을 숨긴다(`my-partial-list.html:9`). 줄 id는 `issue-item-{id}`이고 선택 상자와 일괄 변경이 없다.

## 4. 스크립트 동작

- 모듈: `twoColumnMode.js`, `showSubtask.js`, `issue.List.js`(`my-list.html:20-22`), `$yona.loadModule("issue.List")`(25). 이벤트와 htmx 교체 뒤 재초기화는 프로젝트 목록과 같다.
- `htmx:afterSwap`마다 `.title-cell > .label`의 글자 색을 배경 밝기로 보정하는 `labelTextColorAdjust`를 부른다(`my-list.html:39-51`). 이 화면의 라벨은 `.badge.issue-label`이고 `.title-cell > .label`에 맞는 요소가 없어 아무 일도 하지 않는다(`issue-F13`).
- 단축키: 이 화면에 `setKeymapLink` 호출이 없다. 쪽 나누기를 그릴 때 `LEFT`/`RIGHT`만 등록된다(`Pagination.js`). `N`은 없다. 단축키 도움말 모달도 이 화면에는 없다.
- 로컬 저장소: `useTwoColumnMode`, `showSubtasksAlways`(프로젝트 목록과 같은 키를 공유한다).

## 5. 요청과 응답 계약

| 요청 | 응답 |
|---|---|
| `GET /user/issues` | 비로그인 303(`/users/loginform?redirectUrl=/user/issues`, 경고 `user.login.alert`). 숫자 아님 400. `pageNum`이 숫자 아니면 `toInt()` 예외로 500이다(`MyIssuesController.kt:50` `toInt()`는 검사 없이 던진다, 추측: 전역 오류 처리기가 500으로 응답) |
| 조건 의미(서버) | `authorId`, `assigneeId`, `commenterId`, `mentionId`, `sharerId`, `favoriteId`는 값이 무엇이든 **현재 로그인 사용자 기준**이다(`IssueSearchService.kt:220-250`). `-1`만 "없음". `milestoneId`와 `labelIds`는 쓰지 않는다(시험 `TEST/MyIssuesTest.kt:253-260`) |
| `POST /user/defultLoginPage?path=` | 위 표 |

## 6. 권한과 경계

로그인한 모든 사용자가 쓴다(구성원 여부 무관). 비로그인은 로그인 폼. 사이트 관리자도 같다(내 것만 보인다). 초안은 목록에 없고 하위 이슈는 목록에 있다(프로젝트 목록과 반대, `TEST/MyIssuesTest.kt:218`). 쪽당 15개, 최대 45개. 문구는 `issue.is.empty`.

시험: `TEST/MyIssuesTest.kt`(105-338).

# 나. 리뷰 목록 (`reviewthread/list`)

## 1. 화면 개요

| 항목 | 내용 |
|---|---|
| URL | `GET /:user/:project/reviews`(`K/web/code/ReviewListController.kt:65`) |
| 어노테이션 | 메서드 `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)`(66), `@IsAllowed(READ)`(67). 메서드 어노테이션이 소스 순서로 실행되므로 비로그인은 먼저 로그인 폼 303을 받는다 |
| 템플릿 | `reviewthread/list`, 레이아웃 `project-layout(프로젝트이름 - 메시지(menu.review), projectView, 'PROJECT_REVIEW', reviewPage)`, 메뉴 `project/menu :: projectMenu(..., 'PROJECT_REVIEW', '')`(`list.html:11-13`) |
| 모델 | `title="menu.review"`, `projectView`, `cond`, `rows`(`ReviewRow`), `totalPageCount`, `meId`, `quick{everyone, participant, author}`, `openCount`, `closedCount`, `excelUrl`(`ReviewListController.kt:76-88`) |
| 입력 | `state`(`OPEN` 기본, `CLOSED`, 대소문자 무시), `authorId`, `participantId`, `orderBy`(`createdDate` 기본, 허용 목록 밖은 `createdDate`), `orderDir`(`asc|desc`), `filter`, `pageNum`(1부터), `format=xls`. 숫자 아님, 모르는 `state`, 모르는 `orderDir`은 400(`:72-73`) |
| 쪽 | 15개(`ReviewSearchService.itemsPerPage`, `ReviewSearchService.kt:53`). `itemsPerPage` 파라미터는 없다 |
| 응답 | 항상 전체 화면(htmx 조각 없음). 폼 제출은 일반 GET이다 |

## 2. 레이아웃 계약

- `.project-page-wrap > .row.issue-list-wrap`(`page-wrap-outer` 없음, `list.html:15-16`). 왼쪽 `col-lg-2.search-wrap`(빠른 검색 3개 + 검색어 폼), 오른쪽 `col-lg-10`(정렬 링크 `float-end.filters`, 탭, 목록, 엑셀 링크, `#pagination`).
- 모바일: 구분선 `hr.hide-in-mobile`과 작성자 아바타(`a.avatar-wrap.mlarge.hide-in-mobile`)만 숨는다.
- 상태: 스레드가 없으면 `div.error-wrap`(`review.is.empty` "등록된 리뷰가 없습니다."), 있으면 `ul.post-list-wrap > li.post-item`. 정렬 링크와 엑셀 링크와 `#pagination`은 목록이 비어도 그려진다(프로젝트 이슈 목록과 다름). `#pagination`에는 `data-total`이 없고 `nTotalPages`는 인라인 스크립트가 넘긴다(`:103`, `:110-114`).

## 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| "모든 리뷰"(`review.allReview`) + 개수 | 링크 `data-toggle=filter` | 항상. `active`는 `participantId`, `authorId`가 모두 없을 때 | `authorId`, `participantId` 숨은 입력을 둘 다 비우고 폼 제출 | `GET {base}/reviews?...` | 전체 화면 | READ, 로그인 | `list.html:20-24`, `review.List.js:60-75` |
| "내가 참여"(`review.involvingYou`) | 링크 | 항상 | `participantId=meId`, `authorId=''` | | | | `:25-29` |
| "내가 작성"(`review.createdByYou`) | 링크 | 항상 | `authorId=meId`, `participantId=''` | | | | `:30-34` |
| 검색 폼 `form[name=search]`(`action=/{owner}/{name}/reviews`, GET) | 폼 | 항상 | 숨은 입력 `authorId`, `participantId`, `orderDir`, `orderBy`, `state`. `filter` 텍스트와 돋보기(`submit`). 쪽 번호는 보내지 않아 첫 쪽부터 | `filter=` | 전체 화면 | | `:36-47` |
| 정렬 링크(날짜 `createdDate` 하나뿐) | 링크 `data-toggle=order` | 항상. 현재가 `createdDate`면 방향 화살표와 반대 방향, 아니면 `desc` | `orderBy`, `orderDir` 숨은 입력을 바꾸고 제출 | `orderBy=createdDate&orderDir=` | | | `:51-58`, `review.List.js:77-87` |
| 열림, 닫힘 탭 | 링크 `data-type=state` `data-value=OPEN|CLOSED` | 항상 + 개수. `active`는 대소문자 무시 비교 | `state` 입력을 바꾸고 제출 | `state=OPEN|CLOSED` | | | `:59-70` |
| 작성자 아바타 | 링크 | `thread.authorLoginId != null`(모바일 숨김) | 사용자 페이지. 기본 아바타가 아니면 `alt`와 32x32 | `GET /{loginId}` | | | `:74-77` |
| 스레드 번호와 제목 링크 | 글자, 링크 | 항상. 글자는 첫 리뷰 댓글의 내용(`th:text`) | 스레드 위치로 이동(커밋 또는 풀 리퀘스트 화면의 `#thread-{id}`) | `GET {thread.href}` | | | `:78-81`, `K/domain/comment/ReviewLinks.kt:24` |
| 작성자 이름, 시각, 댓글 수 | 링크, 글자, 링크 | 이름이 있을 때. 댓글 수는 `commentCount > 0`(= 댓글 수 - 1, 첫 댓글 제외) | 댓글 수 링크는 스레드 위치 | | | | `:83-90`, `ReviewListController.kt:140` |
| "엑셀로 내려받기" | 링크 | 항상 | 파일 내려받기 | `GET {base}/reviews?...&format=xls`(쪽 번호 제외) | `attachment`, `프로젝트_reviews_yyyyMMddHHmm.xls`, 모든 스레드 | READ, 로그인 | `:98-102`, `ReviewListController.kt:105-120` |
| 쪽 나누기 `#pagination` | 스크립트가 그림 | 항상(총 쪽 수가 0이면 그리지 않음) | 이전/다음 링크는 일반 링크(htmx 아님, `list-container`가 없다), 쪽 번호 입력 Enter | `GET ...?pageNum=N` | | | `:103`, `review.List.js:93-95`, `Pagination.js` |

## 4. 스크립트, 단축키

- 모듈: `review.List.js`(`list.html:107`), `$yona.loadModule("review.List", {elPagination, nTotalPages, welSearchForm})`. 위임 이벤트 `[data-toggle="filter"]`, `[data-toggle="order"]`를 `.issue-list-wrap`에서 받는다. 요청 헤더나 htmx 속성은 없다.
- 단축키: `LEFT`/`RIGHT`(쪽 나누기). `setKeymapLink` 호출과 단축키 도움말 모달은 이 화면에 없다.
- 로컬 저장소, 폴링: 없음.

## 5. 요청과 응답 계약

| 조건 | 응답 |
|---|---|
| 비로그인 | `303 → /users/loginform?redirectUrl=...`, 경고 `user.login.alert` |
| 프로젝트 없음 | 로그인이면 403 오류 화면(`error.forbidden.or.notfound`), 비로그인이면 로그인 화면 403(`ProjectCheckStage`) |
| 읽기 권한 없음 | 403(로그인은 오류 화면) |
| 숫자 아닌 `authorId`, `participantId`, `pageNum`, 모르는 `state`, 모르는 `orderDir` | `400` HTML |
| `format=xls` | 엑셀 |

CSRF 대상 요청은 없다(전부 GET).

## 6. 권한과 경계

로그인한 사용자 중 프로젝트를 읽을 수 있는 사람만. 구성원 여부, 역할에 따른 차이는 없다. 스레드 작성자는 첫 리뷰 댓글의 작성자다. 문구 `review.is.empty`(`messages_ko_KR.properties:908`). 시험: `src/test/kotlin/dev/yona/web/code/ReviewListTest.kt`(내용은 이 조사에서 열어 보지 않았다). `review.List.js`의 단위 시험은 찾지 못했다.
