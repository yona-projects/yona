---
title: 화면 동작 계약, 코드 주고받기(풀 리퀘스트)
type: ui-contract
sources:
  - src/main/resources/templates/pullrequest/*.html
  - src/main/resources/templates/code/partial-comment-thread.html
  - src/main/resources/templates/common/review-form.html
  - src/main/resources/templates/common/comment-form.html
  - src/main/kotlin/dev/yona/web/pullrequest/*.kt
  - src/main/kotlin/dev/yona/web/code/ReviewCommentController.kt
  - src/main/kotlin/dev/yona/web/access/*.kt
  - src/main/kotlin/dev/yona/access/AccessControl.kt
  - src/main/resources/static/assets/javascripts/esm/service/git.View.js
  - src/main/resources/static/assets/javascripts/esm/service/git.Write.js
  - src/main/resources/static/assets/javascripts/esm/service/pullrequest.Meta.js
  - src/main/resources/static/assets/javascripts/esm/service/pullrequest.Reviewers.js
  - src/main/resources/static/assets/javascripts/esm/service/project.Fork.js
  - src/main/resources/static/assets/javascripts/esm/service/twoColumnMode.js
  - src/main/resources/static/assets/javascripts/esm/Request.js
updated: 2026-11-11
---

# 화면 동작 계약: 코드 주고받기(풀 리퀘스트)

`ui-improvements` 브랜치의 `templates/pullrequest/`(14개)를 기준으로 화면 하나하나의 요소, 요청, 권한, 스크립트를 정리한 계약이다. 새 UI는 이 표의 요청과 응답 계약, 권한 규칙, 조건을 그대로 지키면 기존 기능과 같다. **코드 비교(diff) 화면 안의 줄 댓글, 미니맵, 접기, 드래그 선택** 같은 스크립트 동작은 [code-diff-script.md](code-diff-script.md)에, 코드 쪽 화면은 [code.md](code.md)에, 저장소 종류별 차이와 프로토콜 서빙은 [code-vcs.md](code-vcs.md)에 있다. 기존 위키(`web/pull-request-*.md`, `web/fork.md`, `subsystems/pull-request-merge.md`, `subsystems/access-control.md`, `architecture/ui-shortcuts.md`)와 같은 내용은 다시 쓰지 않고 링크로 가리키며, 이 문서는 새로 확인한 사실, 빠진 부분, 어긋난 부분을 더한다.

## 근거 표기 규칙

| 표기 | 뜻 |
|---|---|
| `T:` | `src/main/resources/templates/` |
| `K:` | `src/main/kotlin/dev/yona/web/` |
| `D:` | `src/main/kotlin/dev/yona/domain/` |
| `S:` | `src/main/resources/static/assets/javascripts/` |
| `M:` | `src/main/resources/messages_ko_KR.properties`(기준 언어) |

줄 번호는 `ui-improvements` 브랜치의 지금 파일 기준이다.

## 0. 영역 요약

### 0.1 화면 목록

| 템플릿 | URL(메서드) | 컨트롤러 | 용도 |
|---|---|---|---|
| `pullrequest/list.html` + `partial-search.html` | `GET /:user/:project/pullRequests`, `closedPullRequests`, `sentPullRequests` | `K:pullrequest/PullRequestListController.kt:97-119`(공통 본문 `:121-169`) | 열림, 닫힘, 보냄 목록. htmx 요청이면 `partial-search :: search` 조각만 |
| `pullrequest/view.html` + `partial-info`, `partial-branch`, `partial-state`, `partial-timeline` | `GET /:user/:project/pullRequest/:id` | `K:pullrequest/PullRequestViewController.kt:97-137` | 개요 탭: 본문, 상태, 타임라인, 리뷰, 담당자와 라벨 |
| `pullrequest/changes.html` | `GET .../pullRequest/:id/changes`, `.../changes/:commitId` | `K:pullrequest/PullRequestViewController.kt:159-228` | 변경 탭: 병합 기준 차이(또는 커밋 하나)와 코드 리뷰 스레드 |
| `pullrequest/partial-timeline.html` | `GET .../pullRequest/:id/timeline` | `K:pullrequest/PullRequestViewController.kt:88-95` | 타임라인 `<li>`만. 담당자, 라벨, 리뷰 요청을 바꾼 뒤 Ajax로 다시 읽는다 |
| `pullrequest/partial-state-page.html` + `partial-state.html` | `GET .../pullRequest/:id/state` | `K:pullrequest/PullRequestViewController.kt:139-157` | 상태 조각. XHR이면 JSON(`html` 필드 포함), 아니면 조각 HTML |
| `pullrequest/create.html` + `partial-merge-result.html` | `GET /:user/:project/newPullRequestForm`, `GET .../newPullRequest/mergeResult`, `POST .../pullRequests` | `K:pullrequest/PullRequestCreateController.kt:95-206` | 새 요청 폼, 브랜치를 고르면 가져오는 커밋 표, 만들기 |
| `pullrequest/edit.html` | `GET .../pullRequest/:id/editform`, `POST .../:id/edit` | `K:pullrequest/PullRequestActionController.kt:102-160` | 제목과 본문 수정(브랜치는 고정) |
| `pullrequest/fork.html` | `GET /:user/:project/newFork`, `POST .../fork` | `K:pullrequest/ForkController.kt:57-116` | 포크 만들기 폼 |
| `pullrequest/clone.html` | `POST .../fork`의 응답(화면), 이어서 `POST .../clone`(JSON) | `K:pullrequest/ForkController.kt:83-137` | 포크 진행 안내 화면. 3초 뒤 복제 요청 |

### 0.2 이 화면이 부르는 동작 요청(화면 밖 컨트롤러)

| 요청 | 컨트롤러 | 권한(서버) | 호출하는 화면 |
|---|---|---|---|
| `POST .../pullRequest/:id/accept` | `K:pullrequest/PullRequestActionController.kt:59-78` | 로그인, `ACCEPT`(`PULL_REQUEST`) | 머리의 병합 단추 |
| `POST .../:id/close`, `POST .../:id/open` | 같은 파일 `:80-100` | 로그인, `CLOSE`, `REOPEN` | 개요 하단 단추 |
| `DELETE .../:id/deletefrombranch`, `POST .../:id/restorefrombranch` | 같은 파일 `:162-182` | 로그인, `UPDATE` | 병합된 요청의 상태 칸 |
| `POST .../:id/reviews`, `POST .../:id/reviews/:reviewId/dismissals` | `K:pullrequest/PullRequestReviewController.kt:50-89` | 로그인, `READ`(해제는 `ACCEPT`) | 리뷰 판정 폼, 해제 폼 |
| `POST .../:id/reviewRequests`, `.../reviewRequests/:reviewer/delete`, `GET .../reviewRequestCandidates` | `K:pullrequest/PullRequestReviewRequestController.kt:46-82` | 로그인, `UPDATE` | 리뷰어 상자 |
| `POST .../:id/assignees`, `POST .../:id/labels` | `K:pullrequest/PullRequestAssigneeLabelController.kt:53-86` | 로그인, `UPDATE` | 담당자와 라벨 상자 |
| `POST .../:id/pendingReview/comments/:commentId/delete`, `POST .../:id/pendingReview/discard` | `K:pullrequest/PendingReviewController.kt:34-56` | 로그인만(읽기 검사 없음) | 임시 리뷰 막대, 임시 댓글 삭제 |
| `POST .../pullRequest/:pullRequestId/comments` | `K:code/ReviewCommentController.kt:119-146` | 로그인, 프로젝트에 `REVIEW_COMMENT` 만들기 | 변경 탭의 댓글 입력 |
| `DELETE /:user/:project/pushedBranch/:id/delete` | `K:pullrequest/PullRequestListController.kt:172-180` | 로그인, 프로젝트 `DELETE` | 목록의 최근 푸시 알림 닫기 |
| `POST /watch`, `POST /unwatch` (쿼리 `resource.type=pull_request&resource.id=<DB번호>`) | `K:WatchController.kt:40-60` | `/watch`는 로그인, `READ` | 개요의 구독 단추 |
| `GET .../mentionListAtPullRequest?pullRequestId=&commitId=` | `K:project/MentionListController.kt:107` | `READ`(프로젝트) | 개요, 변경 탭의 `@` 멘션 |
| `GET .../mentionList` | `K:project/MentionListController.kt:43` | `READ` | 만들기, 수정 폼의 멘션 |
| `GET /:owner/:project/assignableUsers` | `K:api/IssueFormApiController.kt:69` | `READ`, `Accept: application/json` 필요(아니면 406) | 담당자 상자의 후보 검색 |
| `GET .../issue/labels.css` | 이슈 영역 | 읽기 | 라벨 색 CSS(`T:pullrequest/list.html:21`, `view.html:188`) |

### 0.3 모든 요청에 공통인 규칙

- **CSRF**: `S:common/yona.Csrf.js`가 쿠키 `XSRF-TOKEN`을 읽어 `fetch`와 `XMLHttpRequest`에는 헤더 `X-XSRF-TOKEN`을, 폼 제출에는 숨은 `_csrf` 입력을 더한다. 같은 사이트로 가는 안전하지 않은 메서드(POST, PUT, PATCH, DELETE)에만 붙는다. `multipart/form-data` 폼도 같다(`_csrf` 입력). 이 문서의 모든 POST와 DELETE에 적용된다.
- **`data-request-method` 요소**(`S:esm/Request.js:75-140`): 클릭이나 Enter에서 `fetch(주소, {method, headers:{X-Requested-With: XMLHttpRequest}})`를 보낸다. 2xx이면 `204`+`Location`일 때만 그 주소로 이동하고 **그 밖에는 모두 `location.reload()`**이다(303 리다이렉트는 fetch가 따라가 최종 `200`이 되므로 새로 고침이다). 실패(2xx 아님)는 화면에 아무것도 알리지 않는다(`request:error` 이벤트만 발생). `GET`을 건 `<a>`는 처리하지 않는다. `bind`는 문서 준비 때 한 번만 건다(나중에 들어온 요소는 `yona.Request.bind(루트)`를 따로 불러야 한다).
- **접근 검사 순서**(`K:access/AccessChainInterceptor.kt:44-70`): 메서드에 선언한 어노테이션을 소스 순서대로, 그다음 클래스 것을 실행한다. 이 영역의 변경 요청은 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`가 맨 앞이라 익명은 `303 /users/loginform?redirectUrl=<경로>`(경고 `user.login.alert`)이다. 읽기 화면은 `@IsAllowed`가 `@AnonymousCheck`보다 먼저라, 익명 접근을 막은 사이트에서는 익명이 `403`(로그인 화면 본문)을 받는다(`K:access/ProjectCheckStage.kt:118-151`).
- **프로젝트 검사 결과**: 프로젝트가 없거나 읽을 수 없으면 익명에게 로그인 화면 `403`, 로그인한 사용자에게 `403`(`error.forbidden.or.notfound`)이다. 대상 PR 번호가 없으면 `404`(`error.notfound`, 프로젝트 레이아웃). 권한이 없으면 `403`(`error.forbidden`). `@RequiresVcs(PULL_REQUESTS)`를 못 채우는 저장소(Subversion)는 `400`(`error.badrequest.only.available.for.git`)이다. 오류 응답은 `Accept`와 무관하게 HTML 오류 화면이다.
- **리다이렉트**: `RedirectSupport.see`는 `303 See Other`이다(`K:RedirectSupport.kt:97-`). flash 메시지는 세션 FlashMap으로 다음 요청 한 번만 보인다.
- **저장소 종류**: `PULL_REQUESTS`는 Git과 Mercurial이 지원한다(`D:vcs/VcsType.kt:23-50`). Subversion 프로젝트에서는 이 영역의 모든 화면이 `400`이다.

---

## 1. 코드 주고받기 목록

### 1.1 화면 개요

- 컨트롤러 `K:pullrequest/PullRequestListController.kt:97-169`. 권한은 `@IsAllowed(READ)`(프로젝트), `@RequiresVcs(PULL_REQUESTS)`. 코드 접근을 구성원으로 제한한 프로젝트(`isCodeAccessibleMemberOnly`)는 비구성원에게 `403`(`error.forbidden`)이다(`:124-126`). 이 검사는 **목록에만** 있다(발견 PR-F3).
- 모델: `title="menu.pullRequest"`, `projectView`, `categoryCode`(`open|closed|sent`), `filter`, `hasContributorFilter`(보냄 탭이 아니면 true), `searchAction`, `openAction`, `closedAction`, `sentAction`, `openCount`, `closedCount`, `sentLabel`(`합쳐진 수 / 보낸 수`), `forked`(`originalProject != null`), `newPullRequestUrl`, `contributors`, `assignees`, `reviewRequestedMe`(로그인 때만), `labelGroups`, `selectedLabelIds`, `hasLabels`, `pushedBranches`, `rows`, `totalPages`(`:142-168`).
- 레이아웃: `layout/project-layout`(메뉴 `PULL_REQUEST`) 안에 `project/menu`(`main-menu-only`)와 `div.page-wrap-outer > .project-page-wrap > partial-search`(`T:pullrequest/list.html:11-18`).
- 한 쪽 15개(`D:pullrequest/PullRequestSearch.kt:`의 `itemsPerPage`). 정렬은 열림 `number` 내림차순, 닫힘 `received` 내림차순, 보냄 `created` 내림차순, 같은 값은 `id` 내림차순([web/pull-request-list.md](../web/pull-request-list.md)).
- 응답 헤더: htmx 요청(`HX-Request: true`)이면 뷰가 `pullrequest/partial-search :: search`이고 `Cache-Control: no-cache, no-store`가 붙는다(`:142-143`). 같은 URL을 일반으로 열면 전체 페이지(`pullrequest/list`).

### 1.2 레이아웃 계약

- `div[list-container]`(htmx 교체 영역) 하나가 `row cb`로 좌측 검색 단(`col-lg-2 left-menu search-wrap hide-in-mobile`)과 본문 단(`col-lg-10 span-hard-wrap#span10`)을 갖는다(`T:pullrequest/partial-search.html:11-12,61`).
  - `hide-in-mobile`: 화면 너비 720px 이하에서 `display:none !important`(`app/assets/stylesheets/scss/_responsive.scss:1,302`). 따라서 모바일에서는 검색 단 전체(필터 입력, 보낸 사람, 담당자, 라벨)가 사라지고 탭과 목록만 남는다.
  - 목록 줄의 오른쪽 열(`col-lg-2 hide-in-mobile`: 담당자 또는 받은 사람 아바타, 상태 글자)도 모바일에서 사라진다(`:131-142`).
- 본문 단 위쪽: 최근 푸시 브랜치 알림(`h5` + `alert alert-info`), 오른쪽 위 `새 코드 요청` 단추(`float-end`), 탭(`nav nav-tabs.pullrequeset-tab-menu`: 열림, 닫힘, 포크일 때 보냄, 마지막 `li`에 두 단 보기 체크박스), 목록 `ul.post-list-wrap`.
- **빈 상태**: 행이 없으면 `div.error-wrap`(`i.ico.ico-err1` + `pullRequest.is.empty`)을 `ul` 안에 그리고 페이지 막대(`#pagination`)는 그리지 않는다(`:146-149`).
- 목록 줄에는 `th:attr="href=${r.href}"`로 `<li>`에 `href` 속성이 붙는다(링크가 아니라 두 단 보기 스크립트가 쓰는 표지).

### 1.3 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 필터 입력 `input[name=filter]` + 돋보기 단추 | 폼 제출 | 항상(모바일 숨김) | 제출 | `GET <현재 탭 주소>?filter=&contributorId=&assigneeId=&reviewRequestedId=&labelIds=`(htmx `hx-get`) | 응답에서 `div[list-container] > *`만 골라 `innerHTML`로 교체, 주소창 갱신(`hx-push-url`), 시간 초과 3000ms(`hx-request`) | 읽기 | `T:pullrequest/partial-search.html:11,13-18` |
| 탭 열림, 닫힘, 보냄 | 링크 `a[data-type=state]` | 보냄은 `forked`일 때만 | 클릭이면 폼 `action`을 `data-url`(열림, 닫힘, 보냄 주소)로 바꾸고 `yona.Htmx.submit('#search')`, `return false` | 위와 같음(경로만 바뀜) | 교체. 탭 숫자는 `openCount`, `closedCount`, `sentLabel` | 읽기 | `T:...:78-86`, 스크립트 `:169-173`, `S:common/yona.Htmx.js`(요청 시점에 폼의 `action`을 따름) |
| 보낸 사람 `#contributors` | select2(`data-format=user`) | `hasContributorFilter`(보냄 탭 아님) | 값이 바뀌면 `yona.Htmx.submit('#search')` | 위와 같음, `contributorId=<사용자 번호>` | 교체. 선택지: 전체(`common.order.all`), 내가 보낸 것(`pullRequest.sentByMe`, 로그인하고 보낸 적이 있을 때), 보낸 적이 있는 사용자(이름, 아바타) | 읽기 | `:20-32,175-177`, `K:...ListController.kt:188-194` |
| 담당자 `#assignees` | select2(`data-toggle=select2`, `data-format=user`) | `hasContributorFilter` | 선택지: 전체, 로그인하면 `내게 할당됨`(값=내 번호), `담당자 없음`(값 `none`), 담당 가능 사용자 | `assigneeId=<번호\|none>` | **이 화면에 change 처리기가 없다**: 값을 고르고 검색 단추나 Enter로 제출해야 한다(발견 PR-F10) | 읽기 | `:34-46`, `K:...ListController.kt:200-209` |
| 리뷰 요청 `#reviewRequested` | select | 로그인(`reviewRequestedMe != null`) | 전체 또는 `나에게 요청됨` | `reviewRequestedId=<내 번호>` | 위와 같음(change 처리기 없음) | 읽기 | `:48-56`, `:162` |
| 라벨 `#labelIds` | select2 다중 | `hasLabels`(프로젝트 라벨이 있음) | 라벨 고르기 | `labelIds=<번호>&labelIds=...`(라벨마다 하나씩 AND) | 위와 같음(change 처리기 없음). 라벨 색 CSS는 `list.html:21`이 불러온다 | 읽기 | `:57`, `T:issue/partial-select-label.html`, `D:pullrequest/PullRequestSearch.kt` |
| `새 코드 요청` | 링크 | 항상 | 이동 | `GET .../newPullRequestForm` | 만들기 폼(익명이면 로그인으로) | 로그인, FORK 만들기, 구성원 | `:74-76`, `K:...ListController.kt:158` |
| 최근 푸시 알림의 `pullRequest`(링크) | 링크 | 최근 한 시간 안에 푸시한 브랜치가 있을 때 | 이동 | `GET <포크 주소>/newPullRequestForm?fromBranch=<브랜치>&toBranch=<원본 기본 브랜치>` | 만들기 폼 | 위와 같음 | `:62-73`, `K:...:212-229` |
| 최근 푸시 알림 닫기 `a.btn-close` | `data-request-method=delete` | 같음 | 클릭이면 `DELETE`(주소는 `data-request-uri`) | `DELETE <포크 주소>/pushedBranch/:id/delete` | `200` 빈 응답이면 **페이지 새로 고침**(`Request.js`가 `stopPropagation`하므로 부트스트랩 `alert` 닫힘 애니메이션은 동작하지 않는다) | 서버는 프로젝트 `DELETE`(매니저, 조직 관리자, 사이트 관리자)와 로그인 필요. 화면은 조건 없이 보인다(발견 PR-F9) | `:70`, `K:...ListController.kt:172-180` |
| 목록 줄 | `li.post-item` 안의 링크들 | 행이 있을 때 | 제목 링크 `a.title`(충돌이면 `conflict` 클래스), 아바타(보낸 사람 `/로그인ID`), 라벨 배지(`r.href`로 이동), 리뷰 진행(`/changes`로), 리뷰어 수(`<PR>#reviewers`), 담당자 또는 받은 사람 아바타 | 모두 `GET` 이동 | 보낸 사람 아바타는 기본 아바타이면 Gravatar | 읽기 | `:92-143` |
| 두 단 보기 체크박스 `#two-column-mode` | 체크박스 | 항상 | 아래 1.4 | | | | `:87` |
| 페이지 막대 `#pagination` | `yona.Pagination.update` | 행이 있을 때 | `pageNum` 입력과 이전, 다음 링크. htmx 링크로 바꿔 교체 영역만 갱신 | `GET ...?pageNum=` | `totalPages`가 0 이하면 그리지 않음 | 읽기 | `:144,151-153`, `S:esm/Pagination.js:130-175` |
| `.title-prefix` 클릭 | 제목 머리말 | `headerWordsHtml`이 클래스를 가질 때 | 필터 입력에 그 글자를 넣고 폼 제출 | 필터 검색 | 교체 | 읽기 | `:155-161`, `T`가 아니라 `K:TemplateHelper.headerWordsHtml` 산출 |

### 1.4 스크립트 동작

- 불러오는 것: `common/select2`(select2 4.1.0), `S:esm/service/twoColumnMode.js`(모듈), 인라인 `$(document).ready(_initTwoColumnMode)`(`T:pullrequest/list.html:22-29`).
- **두 단 보기**(`S:esm/service/twoColumnMode.js`): `localStorage.useTwoColumnMode`가 `'true'`이면 체크박스를 켜고, 제목 링크(`.title, .twoColumeModeTarget`) 클릭을 가로채 `#pageslide`(부트스트랩 Offcanvas, 오른쪽, 배경 없음, `keyboard:false`, `scroll:true`)에 `iframe`으로 그 주소를 연다. 같은 링크를 다시 누르면 닫고, 다른 링크를 누르면 iframe을 지우고 300ms 뒤 새 주소를 읽는다. 바깥 클릭과 Esc로는 닫히지 않는다. 열려 있는 동안 `.left-menu`와 `.user-info-box`를 숨기고 닫으면 다시 보인다. 클릭 때 `history.pushState/replaceState`로 주소를 제목 링크 주소로 바꾼다. 체크박스를 켜고 끌 때마다 `localStorage`에 `true/false`를 쓴다. 체크박스에는 호버 팝오버(`bootstrap.Popover`)가 붙는다.
  - 처리기는 문서 준비 때 한 번 붙는다. htmx로 목록이 교체된 뒤 이 처리기를 다시 붙이는 코드는 이 화면에서 찾지 못했다(`S:common/yona.Htmx.js`에 `twoColumn` 참조 없음). 교체 뒤에도 두 단 보기가 계속 동작하는지는 코드 읽기로는 확정하지 못했다(추측: 새 제목 링크에는 처리기가 없다). 발견 PR-F11.
- htmx 설정(`S:common/yona.Htmx.js:36-77`): 이력 캐시 0, 속성 정착 끔, `htmx:configRequest`에서 폼의 현재 `action`을 요청 경로로 쓰고, 응답에 `list-container` 문자열이 없으면 교체하지 않고 같은 주소를 일반 이동으로 다시 연다. 오류, 전송 실패, 시간 초과도 일반 이동으로 대체한다. 뒤로 가기는 서버에서 다시 읽는다(`refreshOnHistoryMiss`).
- 단축키: 이 화면 범위 키 없음(전역 키는 `common` 담당).

### 1.5 요청과 응답 계약

| 상황 | 응답 |
|---|---|
| 정상 | `200` HTML(전체 또는 조각) |
| `pageNum`, `contributorId`, `assigneeId`가 숫자가 아님 | 무시하고 기본값(`toIntOrNull`, `toLongOrNull`). `pageNum` 0 이하는 첫 쪽(`coerceAtLeast(0)`) |
| 프로젝트 없음 | `404`(익명이면 로그인 화면 `403`) |
| 코드 접근 제한과 비구성원 | `403`(`error.forbidden`, 프로젝트 레이아웃) |
| Subversion | `400` |

### 1.6 빈 상태, 경계 값

- 한 쪽 15개. 보낸 사람 선택지는 `users.findPullRequestContributors`(이 프로젝트로 요청을 보낸 적이 있는 사용자), 담당자 선택지는 이슈의 담당 가능 사용자 규칙(`listService.assignableUsers`).
- 최근 푸시 기록은 **한 시간**(`RECENT_MILLIS`, `K:...ListController.kt:263`). 보여 주는 대상: 포크 프로젝트이면 그 프로젝트의 기록 전부(작성자 구분 없음), 아니면 현재 사용자가 `PULL_REQUEST`를 만들 수 있고 로그인했을 때 그 사용자가 이 프로젝트에서 포크한 프로젝트들의 기록(`:212-218`).

---

## 2. 풀 리퀘스트 보기(개요 탭)

### 2.1 화면 개요

- 컨트롤러 `K:pullrequest/PullRequestViewController.kt:97-137`. `@IsAllowed(READ, PULL_REQUEST)`, `@RequiresVcs(PULL_REQUESTS)`. 최근 방문 기록(`recentVisits.visits`)을 남긴다.
- 모델: `title`, `projectView`, `header`, `contributor`, `branchInfo`, `bodyHtml`, `attachments`, `state`, `canWatch`, `watching`, `watchUrl`, `unwatchUrl`, `stateUrl`, `canEdit`, `reviewArea`, `assigneeArea`, `labelArea`, `editUrl`, `canClose`, `closeUrl`, `canReopen`, `reopenUrl`, `timeline`, `commentable`, `mentionUrl`(`:111-136`).
- 레이아웃 `T:pullrequest/view.html:15-160`: 머리(`partial-info`: 제목 띠, 오른쪽 위 병합 영역, 탭) 다음 `div.board-body.row`가 왼쪽 `col-lg-9 span-left-pane`(보낸 사람과 브랜치, 본문, 첨부, `#state`, 하단 단추, `#comments`, 임시 리뷰 막대, 리뷰 판정 폼)과 오른쪽 `col-lg-3 span-right-pane`(`#review-area`, `#pr-meta`)으로 나뉜다. 맨 아래 오른쪽에 `title.help` 모달 링크(`:156-158`).
- 모바일: `hide-in-mobile`은 타임라인 커밋 줄의 아바타와 날짜(`partial-timeline.html:19,22,23`)에 쓰인다. `col-lg-*`는 부트스트랩 lg(992px) 미만에서 세로로 쌓인다.

### 2.2 머리(partial-info)

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| `#번호 제목`, 상태 배지, 시각 | 표시 | 항상 | 배지 클래스 `badge-issue-<상태>`(충돌이면 `badge-issue-conflict`), 글자 `pullRequest.state.*` | 없음 | | | `T:pullrequest/partial-info.html:10-18`, `K:pullrequest/PullRequestPageAssembler.kt:134-139` |
| 리뷰어 아바타 줄 `#reviewers` | 표시 | `canAccept`(받아들일 권한) | 승인 수 안내 `pullRequest.review.participants`와 작성자 제외 리뷰어 아바타 | 없음 | | 서버가 같은 판정(ACCEPT) | `:21-25`, `K:...Assembler.kt:115-122` |
| `리뷰` 단추 | 링크 | `canAccept`이고 열린 요청(`reviewUrl != null`) | `<개요 주소>#review-form`으로 이동(개요 하단 리뷰 판정 폼으로 스크롤). 승인 전이면 초록(`ybtn-success`), 승인이 있으면 기본색 | 이동 | | | `:26`, `K:...Assembler.kt:119-120` |
| **병합 상자** `#merge-box` | 분할 단추 | `canAccept`이고 병합 가능(`accept.enabled`) | 아래 2.3 | | | `ACCEPT` | `:37-89` |
| 병합 우회 안내 `#accept-bypass-note` | 글 | `accept.bypassNote != null`(관리자가 리뷰 조건을 우회하는 경우) | 표시만 | | | | `:90` |
| 막힌 병합 단추 | `button.ybtn-disabled` | `canAccept`이고 `!accept.enabled` | 툴팁(`data-bs-toggle=tooltip`)으로 이유(`disabledReason`) 표시, 누르면 아무 동작 없음 | 없음 | | | `:91`, `K:...Assembler.kt:124-131` |
| 탭 `개요`, `변경` | 링크 `.nav-link` | 항상 | 이동. 활성 탭 `activeTab`. `변경`에는 열린 스레드 수 배지(`num-badge`, 0이면 없음) | `GET <개요>`, `GET <개요>/changes` | | 읽기 | `:95-104` |

### 2.3 병합 상자(분할 단추와 확인 칸)

- 구성: `div#merge-box`(상대 위치) 안에 `btn-group`(주 단추 `#btnAccept`, 방식이 둘 이상이면 `#btnMergeOptions` 드롭다운 토글과 `ul#merge-method-menu`)와 숨은 폼 `form#merge-options`(`display:none`, 오른쪽 위치, 폭 420px, `z-index:1000`)이 있다(`T:pullrequest/partial-info.html:37-61`). 스크립트는 같은 파일 인라인 IIFE이다(`:62-88`).
- 동작:
  1. `#btnAccept` 클릭은 폼을 **보이기/숨기기 전환**만 한다. 요청을 바로 보내지 않는다.
  2. 방식 목록 항목(`li.merge-method-item`의 `a`) 클릭: `preventDefault`, 항목이 `disabled`이면 `stopPropagation`하고 끝. 아니면 폼의 `method`(숨은 입력), `commitTitle`, `commitMessage`를 항목의 `data-title`, `data-message`로 채우고, 주 단추와 제출 단추의 글자를 `data-label`로 바꾸고, `method === 'rebase'`이면 제목과 본문 칸(`#merge-message-fields`)을 숨기고(그 밖에는 보임), 방식 확인 표시(`.merge-method-check` ✓)를 선택한 항목에만 둔다.
  3. `취소`(`#btnMergeCancel`) 클릭: 폼 숨김. 바깥 클릭이나 Esc로는 닫히지 않는다.
  4. 제출 단추(`#btnMergeSubmit`, `type=submit`): 일반 폼 제출.
- 요청: `POST <개요 주소>/accept`(폼 `action`은 `header.accept.formAction`, 쿼리 `?method=`는 뺀 주소), 본문 `method`(`merge|squash|rebase`), `commitTitle`(최대 길이 255, `maxlength`), `commitMessage`. 방식 목록 항목의 `li`에는 `data-method`, `data-label`, `data-title`, `data-message`, `title`(불가 이유)이 있다. 불가능한 방식은 `disabled` 클래스와 이유 글자(`small.text-body-secondary`)를 보인다.
- 방식이 하나뿐이면 드롭다운 토글과 목록이 없다(`T:...:40-41`).
- 서버 응답(`K:pullrequest/PullRequestActionController.kt:59-78`): `method`가 비어 있지 않은데 알 수 없는 값이면 `400`(`error.badrequest`), 병합 조건 미충족 `400`(`pullRequest.not.enough.review.point`), 업데이트 제한 규칙 `400`(`pullRequest.not.acceptable.because.updates.restricted`), 허용하지 않은 방식 `400`(기본 메시지). 병합되거나 충돌이어도 **`303`으로 PR 개요로 돌아간다**(충돌은 `AcceptResult.CONFLICT`가 별도 응답 없이 같은 리다이렉트로 빠진다). 병합 중 표시를 먼저 켜고 예외가 나면 거둔다. 이 메서드는 `@Transactional`이 아니다(`:62` 주석, R-76). 서버는 열림 상태인지 따로 확인하지 않는다(`D:pullrequest/PullRequestWorkflow.kt:65-` `acceptInternal`에 `open` 검사 없음, 발견 PR-F6).

### 2.4 본문 영역

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 보낸 사람 `a.usf-group` | 링크 | 항상 | `/로그인ID`로 이동 | GET | | | `T:pullrequest/view.html:23-29` |
| 브랜치 줄 `code.from`, `code.to` | 링크 4개 | 항상 | `보낸소유자/보낸프로젝트: 보낸브랜치`, `받는소유자/받는프로젝트: 받는브랜치`. 각 `code`에 툴팁 `pullRequest.from`, `pullRequest.to`. 브랜치 링크는 `/code/<브랜치>`로 | GET | | 읽기 | `T:pullrequest/partial-branch.html:10-26` |
| 본문 `.markdown-wrap` | 표시 | 항상 | 마크다운을 서버가 렌더링한 HTML(`th:utext`) | | | | `view.html:32` |
| 첨부 `.attachments[data-attachments]` | 표시 | 항상 | `yona.Attachments`가 목록을 그림 | 첨부 내려받기 | | | `view.html:33`, `S:esm/service/git.View.js:143-151` |
| 상태 칸 `#state` | 표시 | 항상 | 아래 2.5 | | | | `:35-37` |
| 구독 `#watch-button` | 토글 단추 | `canWatch`(`WATCH` 권한) | 클릭이면 현재 `data-watching`에 따라 `watchUrl`/`unwatchUrl`로 `$yona.sendForm`(POST). 성공하면 `data-watching` 반전, `ybtn-watching` 토글, 글자를 `project.watch`/`project.unwatch`로, 토스트(`pullRequest.watch.start`/`unwatch.start`) 3초 | `POST /watch?resource.type=pull_request&resource.id=<PR DB번호>` 또는 `/unwatch` | `/watch` 성공 `200` 빈 본문. 익명 `403`(`Anonymous cannot watch it.`), 읽기 불가 `403`. 알 수 없는 대상은 `400`(바인딩 실패) | 화면은 `WATCH`, 서버는 `READ`만 검사(발견 PR-F8) | `:41-42`, `S:git.View.js:83-98`, `K:WatchController.kt:40-60` |
| `수정` | 링크 | `canEdit`(`UPDATE`) | `GET <PR>/editform` | | 수정 폼 | `UPDATE`(서버 동일, 로그인 필요) | `:46` |
| `닫기` | `a[data-request-method=post]` | `canClose` = 열린 요청이고 `CLOSE` 권한 | `POST <PR>/close` 후 새로 고침. `href`도 같은 주소라 JS 없이 열면 GET이 되어 `405` | `POST .../close` | `303`으로 개요. 이어서 새로 고침 | `CLOSE`. 서버는 상태 조건 없음(발견 PR-F7) | `:47`, `K:...ActionController.kt:80-89`, `D:pullrequest/PullRequestWorkflow.kt:114-118` |
| `다시 열기` | 같음 | `canReopen` = 닫힌 요청이고 `REOPEN` 권한 | `POST <PR>/open` | | 병합되었거나 이미 열려 있으면 `400`(`error.badrequest`), 아니면 `303` 개요 | `REOPEN` | `:48`, `:91-100` |
| 타임라인 `#comments` | 목록 | 이벤트가 하나라도 있을 때만 `ul` 그림 | 2.6 | | | | `:54-58` |
| 임시 리뷰 막대 `#pending-review` | 알림 | `reviewArea.pendingCount > 0` | `리뷰 보기` 링크(`/changes`), `버리기` 폼 제출(`confirm`으로 `pullRequest.review.pending.discard.confirm`을 먼저 확인) | `POST <PR>/pendingReview/discard` | `303` 개요 | 로그인만. 내 임시 댓글만 지움 | `:62-68`, `K:pullrequest/PendingReviewController.kt:48-56` |
| 리뷰 판정 폼 `#review-form` | 폼 | `reviewArea.formUrl != null` = 로그인한 사용자 | 제목(`h5`), `textarea[name=contents]`(4줄), 라디오 `name=verdict`(선택지 `reviewArea.verdictOptions`, 기본 `COMMENTED`), `리뷰 제출` | `POST <PR>/reviews` 본문 `contents`, `verdict` | 성공 `303` 개요. `verdict` 모름 `400`(`error.validation`). 승인, 변경 요청인데 `ACCEPT` 권한 없음 `403`. 작성자의 승인과 변경 요청 `403`. 열리지 않은 요청의 승인, 변경 요청 `400`. 승인이 아닌데 본문이 비고 내 임시 줄 댓글도 없으면 `400` | 화면: 선택지는 `canAccept && !작성자 && 열림`일 때만 승인, 변경 요청을 낸다. 서버는 위 응답 | `:69-79`, `K:...ReviewController.kt:50-69`, `D:pullrequest/PullRequestReviewService.kt:57-79` |
| 도움말 링크 `title.help` | 링크 | 항상 | 모달 `#helpMessage` 열기(`data-bs-toggle=modal`). 안에 이미지 `/assets/images/fork-pull/merge.jpg`, 설명 3문단, `확인`(`data-bs-dismiss=modal`) | 없음 | | | `:156-182` |

폼 `#review-form`의 `id`는 변경 탭의 줄 댓글 팝업(`common/review-form`의 `div#review-form`)과 같다(발견 PR-F12).

### 2.5 상태 칸(partial-state)

상태는 `PullRequestPageAssembler.state`(`K:pullrequest/PullRequestPageAssembler.kt:200-224`)가 `kind`로 정한다.

| `kind` | 조건(서버) | 표시 | 안의 동작 요소 |
|---|---|---|---|
| `merging` | `isMerging == true` | 경고 `pullRequest.is.merging` | 없음 |
| `merged` | 병합됨 | 받은 사람 링크와 아바타(`pullRequest.merged.the.pullrequest` 메시지) | **보낸 사람 본인**(`isContributor`)에게만 `branchActions`: `deleteUrl`(`branches.canDelete`가 참)이면 보낸 브랜치 이름 `code`와 `삭제`(`button[data-request-method=delete]`, `float-end`), `restoreUrl`(`canRestore`)이면 `복원`(`a[data-request-method=post]`) |
| `none` | 닫힘 | 아무것도 그리지 않음 | |
| `safe` | 충돌 없음(`isConflict == false`) | 초록 `pullRequest.is.safe` | |
| `conflict` | 그 밖(충돌) | 빨강 `pullRequest.is.not.safe` | 보낸 사람 본인에게만 충돌 해결 방법 10단계(`ol`)와 `button.page.refresh` 링크(`conflictHelp.refreshUrl` = PR 개요 주소). 복제 주소는 구성원이면 로그인 ID를 포함 |

- 요청: 삭제 `DELETE <PR>/deletefrombranch`(`@IsAllowed(UPDATE)`), 복원 `POST <PR>/restorefrombranch`(`UPDATE`). 둘 다 `303` 개요이고 `Request.js`가 새로 고침한다. **화면 조건은 `isContributor`와 브랜치 상태이고 서버 권한은 PR에 대한 `UPDATE`다**: 받는 프로젝트의 구성원이 아닌 보낸 사람은 단추를 보지만 서버는 `403`이다(PR 작성자는 `AUTHOR_TYPES`에 없다, `K:access`가 아닌 `D:`가 아니라 `src/main/kotlin/dev/yona/access/AccessControl.kt:307-313`). 발견 PR-F5.
- 변경 탭에서는 상태 칸을 `canDelete=false, canRestore=false`로 그려 삭제, 복원 단추가 없다(`K:...ViewController.kt:196`).
- **자동 갱신 폴링**: 2.7을 본다.

### 2.6 타임라인(partial-timeline)

`li.event#comment-<이벤트번호>`마다 `span.state.<stateClass>`(글자 `stateKey` 메시지), 메시지 HTML(`th:utext`, 사용자 링크와 아바타 포함), 날짜 링크(`#comment-<번호>`, 툴팁 정확한 시각), 새 커밋이면 `추가 변경` 링크(`compareUrl` = `/compare/<이전>..<새>`)와 커밋 목록(`ul.commit-list`)이 온다(`T:pullrequest/partial-timeline.html:10-29`).

- 이벤트 종류: 리뷰 상태 변경(이전 방식), 상태 변경(열림, 닫힘, 병합, 충돌 등), 병합 이벤트, 새 커밋, 담당자 지정과 해제, 라벨 추가와 삭제(라벨 색 상자), 리뷰 요청과 취소, 리뷰 제출(판정별 `review-<판정>`, 본문 HTML), 리뷰 해제(시스템 해제 포함), 리뷰 규칙 안내(`K:...Assembler.kt:226-296`). 새 PR 이벤트처럼 `null`을 돌려주는 종류는 그리지 않는다.
- 커밋 줄 `li.commit-info`(`outdated`이면 클래스 추가): 짧은 ID 링크(`/pullRequest/:번호/changes/<커밋>`), 작성자 아바타(`hide-in-mobile`, 툴팁), 날짜(`hide-in-mobile`), 첫 줄 링크(`code.commitMsg.empty`이면 대체 문구), 더 있으면 `…` 단추(`button.moreBtn`)와 `pre.commitMsg.desc.hidden`.
- `…` 단추: `view.html:217-219` 인라인 `$("button.moreBtn").on("click")`이 다음 `pre`의 `hidden`을 전환한다(문서 준비 때 한 번 붙으므로 Ajax로 새로 읽은 타임라인 줄에는 붙지 않는다, 발견 PR-F13).
- 타임라인 라벨 상자 글자색: 개요 인라인이 `#comments .event > .label`마다 `$yona.getContrastColor`로 `dimgray/white` 클래스를 바꾼다(`view.html:194-199`). Ajax 새로 읽기 뒤에는 `pullrequest.Meta.js`가 `.event > .badge`를 찾는데 서버가 그리는 라벨 상자는 `div.label.issue-label`이다(발견 PR-F14).
- `GET <PR>/timeline`: `@IsAllowed(READ, PULL_REQUEST)`. 응답은 항상 조각(`pullrequest/partial-timeline :: timeline`)이다.

### 2.7 오른쪽 열: 리뷰어와 담당자, 라벨

**리뷰어 영역 `#review-area`**(`T:pullrequest/view.html:84-120`, 모델 `K:...Assembler.kt:146-180`)

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 승인 수 `dd.review-count` | 표시 | 프로젝트가 필요한 승인 수를 켰을 때(`requiredReviewerCount>0`) | `승인 n/m` | 없음 | | | `:87` |
| 작성자 안내 | 표시 | 필요 승인 수가 있고 작성자가 리뷰어 목록에 있을 때 | `pullRequest.review.author.not.counted` | | | | `:88` |
| 빈 상태 | 표시 | 리뷰 줄도 대기 줄도 없을 때 | `pullRequest.review.area.empty` | | | | `:89` |
| 대기 중 줄 `dd.reviewer-row.pending` | 표시 | 리뷰 요청이 대기 중인 사람 | 아바타, 이름 링크, `리뷰 대기 중` 라벨. **`취소` 단추 `button.review-cancel`**(`data-url`)는 `canRequest`(수정 권한 + 열림)일 때만 | `POST <PR>/reviewRequests/<로그인ID>/delete`(본문 `{}`) | JSON `{"pending":[{loginId,name,avatarUrl}]}`. 성공하면 토스트(`pullRequest.review.request.cancelled`) + 리뷰 영역과 타임라인을 다시 읽음 | `UPDATE`. 서버는 열림 여부를 보지 않고 대기 중이 아니면 아무 일 없이 현재 목록을 돌려줌 | `:91-98`, `K:...ReviewRequestController.kt:61-72` |
| 리뷰어 줄 `dd.reviewer-row[data-verdict]` | 표시 | 리뷰어별 최신 판정(대기 중인 사람은 빠짐) | 아바타, 이름, 판정 글자(`verdict-<판정>`, 툴팁은 시각), 해제됨 표시(툴팁은 시스템 해제면 `pullRequest.review.dismissed.stale`, 아니면 사유) | | | | `:99-111` |
| 다시 요청 `button.review-rerequest` | 단추 | `canRequest`이고 작성자가 아닌 리뷰어 | 클릭이면 JSON `{reviewer:<로그인ID>}` | `POST <PR>/reviewRequests` | JSON 대기 목록. 후보가 아니거나 닫힌 요청은 `400`(`error.validation`). 성공이면 토스트 `pullRequest.review.request.done` | `UPDATE` | `:106`, `K:...ReviewRequestController.kt:46-59` |
| 해제 폼 `form.review-dismiss` | 폼 | `canAccept`이고 아직 해제되지 않았고 판정이 `COMMENTED`가 아닌 줄 | 사유 입력(`name=message`, 최대 255자) + `리뷰 해제` | `POST <PR>/reviews/<리뷰번호>/dismissals` | 성공 `303` 개요. 사유 없음, 이미 해제 `400`, 없는 리뷰 `404` | `ACCEPT` | `:107-110`, `K:...ReviewController.kt:72-89` |
| 리뷰 요청 상자 `select#review-request` | select2(원격 검색) | `canRequest`(수정 권한 + 열림) | 한 글자 이상 입력(`minimumInputLength:1`)해 후보를 고르면 즉시 요청. 아래 스크립트 | `GET <PR>/reviewRequestCandidates?query=` → `POST <PR>/reviewRequests` | 후보 응답은 최대 20명, 빈 검색어는 빈 목록 | `UPDATE` | `:113-118`, `K:...ReviewRequestController.kt:74-82` |

**스크립트 `pullrequest.Reviewers.js`**(`view.html:183-186`: 요청 상자가 있거나 리뷰 줄이 있을 때만 불러옴): 상자 값이 바뀌면 `POST <data-request-url>`(JSON `{reviewer}`)를 보내고 성공하면 토스트와 함께 `window.location.href`를 다시 읽어 `DOMParser`로 `#review-area`만 바꾸고(스크립트 제거) 요청 상자를 다시 묶는다. 타임라인은 `data-timeline-url`(담당자 영역이 있을 때만 값이 있고 없으면 빈 문자열)로 다시 읽는다. 취소와 다시 요청 단추는 문서에 위임한다. 실패하면 토스트 `error.failedTo`(`pullRequest.review.area.title`, 상태 코드, 상태 글자).

**담당자와 라벨 `#pr-meta`**(`T:pullrequest/view.html:122-152`, `assigneeArea != null`일 때만)

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 담당자 `select#assignee` | select2(원격) | `assigneeArea.canEdit`(= `UPDATE`) | 고르거나 지우면(`allowClear`) 즉시 JSON | `POST <PR>/assignees` 본문 `{"assignees":["로그인ID"]}`(지우면 `[]`). 후보는 `GET /:owner/:project/assignableUsers?query=`(JSON) | 성공이면 토스트 `이슈 담당자: <이름>` 3초 + 타임라인 다시 읽기. 응답 JSON `{assignee, assignees}`. 한도 1명 초과, 모르는 사용자, 후보 아님 `400` | `UPDATE` | `:127-131`, `S:pullrequest.Meta.js:79-110`, `K:...AssigneeLabelController.kt:53-70` |
| 담당자 읽기 전용 | 링크 | `!canEdit` | 담당자가 있으면 아바타, 표시 이름, `@로그인ID`, 없으면 `issue.noAssignee` | | | | `:132-139` |
| 라벨 `select#labelIds`(다중) | select2 | `labelArea.canEdit && labelArea.hasLabels` | 선택이 바뀌면 `yona.ui.Select2.selectionChanges`로 붙인 것과 뗀 것의 번호만 | `POST <PR>/labels` 폼 인코딩 `attachingLabelIds=..`, `detachingLabelIds=..`(`traditional`) | JSON `{"labels":[번호]}`. 없는 라벨, 다른 프로젝트 라벨 `400`(아무것도 바꾸지 않음). 성공이면 토스트 `issue.update.labelIds` + 타임라인 | `UPDATE` | `:142-144`, `S:Meta.js:112-129`, `K:...AssigneeLabelController.kt:72-86` |
| 라벨 읽기 전용 | 칩 `a.badge.issue-label` | `!canEdit`이고 라벨이 있을 때 | 목록 `<프로젝트>/pullRequests?labelIds=<번호>`로 이동 | GET | | | `:145-151` |

`Meta.js`는 `view.html:187-191`에서 `assigneeArea != null and (assigneeArea.canEdit or labelArea.canEdit)`일 때만 로드된다. 후보와 라벨 목록의 주소는 `#pr-meta`의 `data-assignees-url`, `data-labels-url`, `data-timeline-url`, `data-candidates-url`에 있다. `data-mode="form"`이면(만들기 폼) 요청을 보내지 않고 선택 상자만 꾸민다.

### 2.8 개요 화면의 스크립트

`view.html:183-225`가 불러오는 것과 호출: `S:esm/CodeCommentBox.js`, `S:esm/service/git.View.js`(`$yona.loadModule("git.View", {sWatchUrl, sUnwatchUrl, sStateUrl, bCommentable})`), `yona.Mention({target:'textarea[id^=editor-]', url: mentionUrl})`, 멘션 강조(`.comment-body:contains('<내 이름>')`에 `mentioned`, `.user-link`에 `me`).

- **상태 폴링**(`S:git.View.js:153-216`): 10초(`nStateUpdateInterval`)마다 `GET <PR>/state`(`yona.http.get`이 `X-Requested-With`를 보내므로 서버는 JSON `{id,isOpen,isClosed,isMerged,isMerging,isConflict,canDeleteBranch,canRestoreBranch,html}`을 줌, `K:...ViewController.kt:148-156`)를 보낸다. 마지막 사용자 입력(스크롤, 마우스 누름과 이동, 키 입력)이 **60초** 전이면 건너뛴다. 응답 `html`이 직전 값(처음에는 빈 문자열)과 다르면 `#state`를 통째로 바꾸고, 항상 `#btnAccept`의 `display`를 충돌이면 `none`, 아니면 `inline-block`으로 맞춘다. 실패는 조용히 무시한다.
  - 처음 폴링 때 `sStateHTML`이 빈 문자열이라 서버가 그린 상태 칸이 **항상 한 번 다시 그려진다**. 이때 `yona.Request.bind`를 다시 부르지 않아, 병합된 요청의 `삭제`, `복원`(`data-request-method`)이 **첫 폴링 이후에는 처리기가 없다**(발견 PR-F4).
  - 충돌이면 `#btnAccept`만 숨기고 드롭다운 토글(`#btnMergeOptions`)과 확인 폼은 그대로다(발견 PR-F15).
- 이 화면 범위 단축키 없음.

### 2.9 권한 행렬(개요)

`R`=보임/허용, `-`=안 보임/거부. "서버"는 요청을 막는 규칙이다.

| 기능 | 비로그인 | 로그인, 비구성원(공개) | 구성원 | 매니저 | 조직 구성원(공개, 보호) | 사이트 관리자 |
|---|---|---|---|---|---|---|
| 개요 읽기 | 공개 프로젝트면 R | 공개면 R | R | R | R | R |
| 구독(화면) | 공개면 보임(`WATCH`: 공개 ∧ 로그인이라 익명은 -) | R | R | R | R | R |
| 수정, 닫기, 다시 열기, 담당자, 라벨, 리뷰 요청 | - | -(PR 작성자도 구성원이 아니면 -) | R | R | R | R |
| 병합(`ACCEPT`), 리뷰 해제 | - | - | R | R | R | R |
| 리뷰 판정 `COMMENTED` | 로그인 필요 | R | R | R | R | R |
| 리뷰 판정 `APPROVED`, `CHANGES_REQUESTED` | - | - | R(작성자 제외, 열림만) | 같음 | 같음 | 같음 |
| 보낸 브랜치 삭제, 복원 | - | 보이지만(작성자) 서버 `403` | R(작성자만 보임) | 같음 | 같음 | 같음 |

근거: `src/main/kotlin/dev/yona/access/AccessControl.kt:181-244`(수정, 닫기, 병합은 멤버 또는 조직 구성원, `PULL_REQUEST`는 작성자 규칙에 없음 `:307-313`), 위 표들.

---

## 3. 변경 탭(changes)

### 3.1 화면 개요

- 컨트롤러 `K:pullrequest/PullRequestViewController.kt:159-228`. `@IsAllowed(READ, PULL_REQUEST)`, `@RequiresVcs(PULL_REQUESTS)`. `commitId`가 목록에 없으면 `404`(`error.notfound.commit`, 프로젝트 레이아웃).
- 모델: `title`, `commitInfo`(커밋 하나를 골랐고 그 커밋이 이 PR 것일 때), `projectView`, `header`(활성 탭 `changes`), `contributor`, `branchInfo`, `hasThreads`(공개 스레드나 내 임시 스레드가 하나라도 있음), `commitId`, `selectedLabel`, `choices`, `allUrl`, `state`, `diffHtml`, `nonRangedHtml`, `pendingCount`, `pendingDiscardUrl`, `overviewUrl`, `commentable`, `commentAction`, `openCards`, `closedCards`, `openCount`, `closedCount`, `mentionUrl`(`:200-227`).
- 레이아웃 `T:pullrequest/changes.html:15-150`: `.code-browse-wrap` 안에 머리, 보낸 사람과 브랜치 줄(`author-info right-txt`), 임시 리뷰 막대(`#pending-review-bar`), 그 아래 `div.codediff-wrap`(스레드가 없으면 `diffs-only` 클래스). 왼쪽 `div#changes.diffs-wrap`(`margin-right:282px`)에 커밋 선택, 커밋 정보, `.diff-body.diffs-wrap-scroll`(상태 칸 + diff + `.btnPop`), `.board-comment-wrap`(파일 단위 스레드, 댓글 폼, 줄 댓글 팝업 폼). 오른쪽 `div.review-wrap`(폭 260px, 절대 위치, 안쪽 `sticky`)에 리뷰 카드.
- 반응형: 720px 이하에서 `.codediff-wrap .diffs-wrap { margin-right: inherit }`, `.review-wrap { position: inherit }`로 카드가 아래로 쌓인다(`_responsive.scss:140-165`).

### 3.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 임시 리뷰 막대 `#pending-review-bar` | 알림 | `pendingCount > 0`(본인 것) | `리뷰 제출` 링크(`<개요>#pending-review`로 이동), `버리기` 폼(`confirm` 후 POST) | `POST <PR>/pendingReview/discard` | `303` 개요 | 로그인만 | `:34-40` |
| 커밋 선택 `#commits` 드롭다운 | 부트스트랩 dropdown | 항상 | 토글 글자는 `selectedLabel.kind`: `all`(`pullRequest.changes.all`), `prior`(짧은 ID와 메시지와 `(어긋남)`), `current`, `missing`(`전체 (어긋남 - <ID>)`). 목록 항목은 `전체 변경`(`allUrl`)과 현재 커밋마다(`choices`) 링크 | `GET <PR>/changes` 또는 `/changes/<커밋>` | 전체 페이지 이동 | 읽기 | `:47-76`, `K:...ViewController.kt:230-238`, `K:...Assembler.kt:342-347` |
| 커밋 정보 `p.commitInfo` | 표시 | 커밋을 골랐고 이 PR의 커밋(`commitInfo != null`) | 작성자(알려진 사용자면 링크), 시각, 서명 배지, 메시지 `pre` | | | | `:78-92` |
| 상태 칸(`partial-state`) | 표시 | 항상 `.diff-body` 안 | 삭제, 복원 단추 없음 | | | | `:96` |
| diff 본문 `th:utext=diffHtml` | 표시 | `diffable`일 때 서버가 그림 | 줄, 줄 번호, 코드 스레드. 동작은 [code-diff-script.md](code-diff-script.md) | | | | `:97`, `K:code/DiffHtmlRenderer.kt` |
| `.btnPop` | 팝업 단추 | 댓글을 달 수 있고 코드를 드래그 선택했을 때 JS가 표시 | 줄 댓글 팝업 열기 | | | | `:98` |
| 파일 단위 스레드 `.non-ranged-threads-wrap` | 표시 | 항상 | `nonRangedHtml`(스레드 조각, `full=true`이므로 코드 스레드와 같은 조각) | | | | `:101` |
| 댓글 폼 `#comment-form` | 폼 | `commentable` = 프로젝트에 `REVIEW_COMMENT` 만들기 가능. 아니면 비활성 상자(`data-login="required"`, 툴팁 `error.auth.unauthorized.comment`) | 전체 PR(파일 단위) 댓글. `textarea[name=contents]`, 첨부, `댓글 쓰기` | `POST <PR>/comments`(+`?commitId=`) multipart | `303`이 `…/changes[/<커밋>]#comment-<번호>`로(JS가 해시로 스크롤과 강조) | 로그인, 만들기 | `:102`, `T:common/comment-form.html`, `K:code/ReviewCommentController.kt:119-146` |
| 줄 댓글 팝업 폼 `#review-form` | 폼 | `commentable`일 때만 | 코드 줄 댓글 팝업. `리뷰 시작`/`리뷰에 추가`(`name=pending value=true`, `pendingCount != null`이므로 항상 이 단추가 있음)와 `단일 댓글 쓰기`. 글자는 `pendingCount>0`이면 `리뷰에 추가` 아니면 `리뷰 시작` | `POST <PR>/comments[?commitId=]` 폼 + 숨은 입력(`path`, `startSide`, ... [code-diff-script.md] 참조) | 위와 같음. 임시이면 알림 없이 임시 저장(리뷰를 제출할 때 한꺼번에 공개) | 로그인, 만들기 | `:106`, `T:common/review-form.html:11-38` |
| 리뷰 카드 영역 `.review-wrap` | 목록 | `hasThreads` | 탭 `열림 n`, `닫힘 n`(`data-bs-toggle=tab`). 카드 `a.review-card.<state>`(`outdated`이면 클래스 추가): 첫 댓글 내용, 댓글 수(`>0`), `어긋남` 표시, 시각, 아바타. 클릭은 [code-diff-script.md 5절] | `GET <스레드 주소>` 또는 같은 페이지의 `#thread-<id>` | | 읽기 | `:109-148`, `K:...Assembler.kt:328-339` |
| 카드 영역 접기 `.btn-hide-reviewcards` / 펴기 `.btn-show-reviewcards` | 단추 | `hasThreads` | 클릭이면 `.codediff-wrap`에 `diffs-only` 토글 | JS만 | | | `:43-45,111-113` |
| 파일 머리 `.diff-partial-meta` 클릭 | 영역 | diff가 있을 때 | `.diff-partial-code`를 `toggle`(파일 접기). 마우스 커서 `pointer` | JS만 | | | `:176-178`(이 파일에만 있음, 발견 code-F8) |
| `…`(커밋 메시지 더 보기) | 단추 | 타임라인이 아니라 diff 쪽에는 없음. 문서에 `button.moreBtn`이 있을 때 | `pre.commitMsg.desc` 토글 | | | | `:172-174` |

### 3.3 스크립트 동작

`changes.html:154-181`: `CodeCommentBox.js`, `CodeCommentBlock.js`, jQuery UI 1.14.2(강조 효과용), `service/code.Diff.js`, `service/review.CommentEdit.js`를 불러오고 `yona.Mention({target:'textarea[id^=editor-]', url: mentionUrl})`, `$yona.loadModule("code.Diff", {bCommentable: <commentable>})`을 호출한다. 자세한 이벤트와 DOM 계약은 [code-diff-script.md](code-diff-script.md).

- 댓글 삭제 모달: `common/comment-delete-modal`(컨테이너 `#changes`). 삭제 단추 `[data-toggle=comment-delete]` 클릭이 `#comment-delete-modal`을 열고, `예`(`#comment-delete-confirm`)가 `DELETE <data-request-uri>`를 `yona.Request.send`로 보낸 뒤 새로 고침한다(`S:esm/Comment.js:19-100`). 삭제 주소는 `/comments/review_comment/<번호>`(`K:code/ReviewCommentController.kt:181-193`, `DELETE` 권한 필요, 응답 `200` 빈 본문, 권한 없음 `403`, 종류 모름이나 없는 대상 `400`).
- 댓글 고치기: `review.CommentEdit.js`(2.10은 아래 3.5).
- 두 번째 `yona.Mention`은 없다(개요 화면은 하나).

### 3.4 요청과 응답 계약

| 요청 | 응답 |
|---|---|
| `GET <PR>/changes` | 정상 `200`. `diffable`이 아니면(충돌이거나 기준 커밋이 없음) diff를 그리지 않음(빈 본문) |
| `GET <PR>/changes/<커밋>` | 커밋이 이 PR 커밋 목록에 없으면 `404`(`error.notfound.commit`) |
| `POST <PR>/comments` | 내용 비어 있음 `400`(`error.validation`, 메뉴 `PROJECT_HOME`), 존재하지 않는 PR DB번호 `404`, `thread.id`가 다른 프로젝트 `400`(메뉴 `PULL_REQUEST`). 성공 `303` |
| 댓글 삭제, 고치기, 스레드 열기와 닫기 | [code.md](code.md) 7절(리뷰 댓글 공통) |

### 3.5 리뷰 댓글 고치기 스크립트

`.comment-edit`(스레드 조각의 연필 단추, `data-url`) 클릭이 본문 `.comment-body`를 숨기고 그 자리에 `textarea`(원문은 숨은 `textarea.comment-raw`의 값)와 `취소`, `저장`을 넣는다. 저장은 빈 값이면 토스트(`post.comment.empty`), 아니면 `POST /comments/review_comment/<번호>/edit`(본문 `contents`)을 보내고 `{id, edited:true, bodyHtml}` 응답으로 본문만 바꾸고 `(수정됨)` 표시를 더한다. 실패하면 토스트 `error.failedTo`. 서버는 로그인 필요, 공개 댓글은 `UPDATE` 권한(구성원이면 남의 것도 고칠 수 있음, 작성자도 가능), 임시 댓글은 작성자만(남에게는 `404`), 본문이 공백이면 `400`, 권한 없음 `403`이다(`K:code/ReviewCommentController.kt:161-179`, `S:esm/service/review.CommentEdit.js`).

### 3.6 권한 행렬(변경 탭)

| 기능 | 비로그인 | 로그인, 비구성원(공개) | 구성원 이상 |
|---|---|---|---|
| 읽기 | 공개면 R | R | R |
| 줄 댓글, 파일 댓글 만들기 | -(비활성 상자) | R(공개 프로젝트의 `REVIEW_COMMENT`는 로그인 사용자 모두) | R |
| 임시 리뷰 막대, 버리기 | - | 자기 것만 | 자기 것만 |
| 스레드 열기와 닫기 | - | 자기 스레드만(작성자 규칙) | R |

서버 근거: `K:code/ReviewCommentController.kt:119-157`, `src/main/kotlin/dev/yona/access/AccessControl.kt:290-326`.

---

## 4. 만들기 폼(create + partial-merge-result)

### 4.1 화면 개요

- `GET /:user/:project/newPullRequestForm`(`K:pullrequest/PullRequestCreateController.kt:95-132`): 어노테이션 순서 `@AnonymousCheck(requiresLogin)`, `@IsCreatable(FORK)`, `@Transactional`, `@RequiresVcs(PULL_REQUESTS)`. 현재 URL 프로젝트에서 **구성원이 아닌 로그인 사용자(게스트)는 `403`**(`guestError`, 메시지 `Guest is not allowed this request`, `:214-218`). 쿼리 `fromProjectId`, `toProjectId`, `fromBranch`, `toBranch`로 초기값을 정한다. 어느 쪽 저장소든 브랜치가 없으면 `400`(`error.pullRequest.empty.from.repository`, `...to.repository`, 메뉴 `PULL_REQUEST` 또는 `PROJECT_HOME`).
- 모델: `title="title.newPullRequest"`, `action=$base/pullRequests`, `mergeResultUrl=$base/newPullRequest/mergeResult`, `mentionUrl=$base/mentionList`, `fromProjects`(연관 프로젝트 중 내가 구성원인 것), `toProjects`(연관 프로젝트 전체), `fromBranches`, `toBranches`, `state="OPEN"`, `body=""`, `assigneeArea`, `labelArea`(받는 프로젝트에 대한 `UPDATE` 권한이 있을 때만, 없으면 `null`), `labelsCssUrl`.
- 레이아웃 `T:pullrequest/create.html:15-103`: `form.nm`(multipart) 안에 `.pull-request-wrap`(왼쪽 `float-start` 보내는 곳 + 화살표 + 오른쪽 `float-end` 받는 곳), 상태 칸 `#status`, `div.row`(왼쪽 `span-left-pane`: 제목, 편집기, 첨부, 단추. 오른쪽 `col-lg-3 right-menu`: `#pr-meta` 담당자와 라벨, 담당자 영역이 있을 때만, 없으면 왼쪽이 `col-lg-12`), `커밋` 탭 + `#__commits` 영역.

### 4.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 보내는 프로젝트 `#fromProjectId`, 받는 프로젝트 `#toProjectId` | select2 | 항상 | 값이 바뀌면 **페이지를 새로 읽음**: `location.search = "?fromProjectId=..&toProjectId=.."`(입력 중인 제목과 본문, 브랜치, 담당자, 라벨은 사라짐) | `GET .../newPullRequestForm?fromProjectId=&toProjectId=` | 전체 화면 | 같음 | `T:pullrequest/create.html:22,41`, `S:git.Write.js:97-101` |
| 보내는 브랜치 `#fromBranch`, 받는 브랜치 `#toBranch` | select2(`data-format=branch`) | 항상 | 값이 바뀌면 병합 결과를 Ajax로 읽음 | `GET .../newPullRequest/mergeResult?title=&fromProjectId=&toProjectId=&fromBranch=&toBranch=` | HTML 조각 `#mergeResult`(응답 헤더 `Cache-Control: no-cache, no-store`) | 로그인, FORK 만들기, 게스트 `403` | `:26,45`, `S:git.Write.js:107-133` |
| 상태 칸 `#status` | 알림 | 항상 | 처음 글자 `pullRequest.is.merging`(노랑). 병합 결과 뒤 `alert-info`(변경 없음 `pullRequest.diff.noChanges`), `alert-success`(`pullRequest.is.safe`), `alert-danger`(`pullRequest.is.not.safe`)로 바뀜. 실패하면 `alert-danger`와 `pullRequest.error.newPullRequestForm`(상태 코드, 글자) | | | | `:55`, `S:git.Write.js:143-170` |
| 제목 `#title` | 입력 | 항상 | 최대 255자(`maxlength`). 키를 처음 누르면(Enter 제외) `isUserHasTyped` 표시 후 그 처리기를 뗌 | | | | `:61`, `git.Write.js:78-85` |
| 본문 편집기 `body` | `common/editor` | 항상 | 입력 이름 `body`. 임시 저장(`temporarySaveHandler`)이 붙음 | | | | `:63` |
| 첨부 | `common/file-uploader('PULL_REQUEST', null)` | 항상 | 업로드, 본문에 삽입 | | | | `:65` |
| 담당자 `#assignee`, 라벨 `#labelIds` | select2 | `assigneeArea != null`(받는 프로젝트 `UPDATE`) | 고른 값은 폼과 함께 제출(`data-mode=form`이라 즉시 요청 없음) | 폼 필드 `assigneeLoginId`, `labelIds`(여러 개) | | `UPDATE`(받는 프로젝트) | `:73-83`, `S:pullrequest.Meta.js:52-60` |
| `보내기` | submit | 항상 | 제출 전 검사(아래) | `POST .../pullRequests` multipart: `title`, `fromProjectId`, `toProjectId`, `fromBranch`, `toBranch`, `body`, 첨부, `assigneeLoginId`, `labelIds` | 2.4 | | `:69` |
| `취소` | `href="javascript:history.back();"` | 항상 | 뒤로 | | | | `:70` |
| 커밋 탭, `#numOfCommits` | 표시 | 항상 | 커밋 수 배지(병합 결과가 채움) | | | | `:86-93` |
| 커밋 목록 `#__commits` | 영역 | 항상 | 병합 결과 조각의 커밋 표를 넣음. 메시지가 여러 줄이면 `…`(`button.moreBtn`)로 `pre.commitMsg.desc` 전환(`document.body` 위임이라 새로 넣은 줄에도 동작) | | | | `:96-98`, `git.Write.js:88-90` |

### 4.3 스크립트(`git.Write.js`)

- 문서 준비 때 `$yona.loadModule("git.Write", {mergeResultURL, fromProject, toProject, fromBranch, toBranch, state})`와 `yona.Mention({target, url: mentionUrl})`(`T:pullrequest/create.html:112-128`). `state === "OPEN"`이면 시작할 때 `_checkMergeResult()`를 한 번 부른다(`git.Write.js:92-94`).
- **병합 결과 읽기**: 브랜치 값이 둘 다 비면 아무 것도 하지 않는다. 그 외에는 상태 칸에 `pullRequest.is.merging`을 보이고 `NProgress`를 시작해 `yona.http.ajax(mergeResultURL, {data: {title, fromProjectId, toProjectId, fromBranch, toBranch}})`를 부른다. 성공이면 `#__commits`를 응답 HTML로 바꾸고 `#mergeResult`의 `data-commits`, `data-conflict`, `data-pullrequest-title`, `data-pullrequest-body`를 읽어 상태 칸과 커밋 수 배지를 갱신하고, 사용자가 제목 또는 본문을 한 번도 입력하지 않았을 때만(`isUserHasTyped` 둘 다 없음) 제목과 본문을 제안 값으로 채운다. 실패하면 상태 칸에 오류 문구, 제목과 본문을 빈 값으로 채운다.
- **제출 검사**(`:240-264`): (1) 커밋이 없으면(`numOfCommits`가 0) `$yona.alert(pullRequest.diff.noChanges)`하고 막는다. (2) 충돌이면 `$yona.confirm(pullRequest.ignore.conflict)`가 `확인`(`nButtonIndex === 1`)일 때만 `forceSubmit`하고 다시 제출. (3) `title`, `fromProjectId`, `toProjectId`, `fromBranch`, `toBranch` 중 비어 있는 첫 필드에 대해 `$yona.alert(Messages("pullRequest.<필드>.required"))`. **`pullRequest.fromProjectId.required`, `pullRequest.toProjectId.required` 메시지는 `messages_ko_KR.properties`에 없다**(`title`, `fromBranch`, `toBranch`, `body`만 있음, `M:796,829,889,891`, 발견 PR-F16).
- **제출 폼의 위치**: `form.nm`(`$("form.nm")`). `submit` 처리기가 `false`를 돌려주면 막는다.

### 4.4 병합 결과 조각 `GET .../newPullRequest/mergeResult`

- 컨트롤러 `K:pullrequest/PullRequestCreateController.kt:134-159`. 권한은 `@IsCreatable(FORK)`와 게스트 `403`. 번호가 없으면 보내는 곳은 현재 프로젝트, 받는 곳은 (포크이고 원본이 PR을 받으면) 원본이다(`selectedProject`). 브랜치가 없으면 첫 번째 브랜치.
- 응답 `T:pullrequest/partial-merge-result.html`: `div#mergeResult.code-browse-wrap`에 `data-commits`, `data-conflict`(커밋이 있을 때만), `data-pullrequest-title`, `data-pullrequest-body`. 커밋이 없으면 `h5`(`pullRequest.diff.noChanges`). 있으면 표(`@`, 메시지, 날짜, 작성자). 각 행: 짧은 ID 링크(보내는 프로젝트의 `/commit/<ID>`), 댓글 수(`yobicon-comments`, 0이면 없음), 메시지 첫 줄(비면 `code.commitMsg.empty`), `…` 단추와 숨은 본문, 날짜(툴팁은 정확한 시각), 작성자 아바타(가입 사용자면 링크, 아니면 Gravatar).
- 제안: 커밋이 하나이면 첫 줄이 제목, 나머지가 본문. 둘 이상이면 제목은 비우고 각 첫 줄을 본문으로 나열(`:249-256`).

### 4.5 만들기 요청 `POST .../pullRequests`

- 컨트롤러 `K:pullrequest/PullRequestCreateController.kt:161-206`: `title`, `fromBranch`, `toBranch`가 비었거나 `title`이 255자 초과이면 `400`(`error.validation`, 메뉴 `PULL_REQUEST`). 프로젝트 번호가 숫자가 아니거나 프로젝트가 없으면 `400`. `body` 파라미터가 없으면 `413`(`requestTextEntityTooLarge`). 받는 프로젝트에 `UPDATE` 권한이 있으면 `assigneeLoginId`(후보가 아니면 `400`)와 `labelIds`(받는 프로젝트의 라벨이 아니면 `400`)를 받고, 없으면 무시한다.
- 같은 두 프로젝트와 두 브랜치 사이에 열린 요청이 이미 있으면 새로 만들지 않고 그 요청으로 `303`. 만들면 첨부를 옮기고 담당자, 라벨을 적용하고 병합 검사를 백그라운드로 시작한 뒤 `303`으로 새 PR 개요(flash는 첨부 결과).
- **서버가 확인하지 않는 것**: 보내는 프로젝트의 구성원 여부, 두 프로젝트가 연관 프로젝트인지, 받는 프로젝트를 읽을 수 있는지. 권한 검사는 URL의 프로젝트에 대한 `FORK` 만들기와 게스트 여부뿐이다(발견 PR-F1).

### 4.6 권한과 경계

| 항목 | 값 | 정의 |
|---|---|---|
| 제목 최대 길이 | 255 | `K:...CreateController.kt:274`(`MAX_TITLE`), 입력 `maxlength=255` |
| 폼에 보이는 보내는 곳 | 내가 구성원인 연관 프로젝트 | `:122` |
| 폼에 보이는 받는 곳 | 연관 프로젝트 전체(현재, 그 포크, 포크이고 원본이 코드와 PR 메뉴를 켰으면 원본) | `:221-232` |
| 담당자, 라벨 선택 | 받는 프로젝트 `UPDATE` 권한자 | `:211-212` |

---

## 5. 수정 폼(edit)

- `GET <PR>/editform`: `@AnonymousCheck(requiresLogin)`, `@IsAllowed(UPDATE, PULL_REQUEST)`, `@RequiresVcs`. 모델 `title="title.editPullRequest"`, `action=<PR>/edit`, `pullRequestId`, `fromLabel`, `fromProjectId`, `fromBranch`, `toLabel`, `toProjectId`, `toBranch`, `fromBranches`, `toBranches`, `state`(대문자 `OPEN` 등), `open`, `prTitle`, `body`, `mergeResultUrl`(받는 프로젝트의 `mergeResult`), `mentionUrl`(받는 프로젝트의 `mentionList`), `markdownProject`(`K:...ActionController.kt:102-128`).
- 요소: 보내는 곳과 받는 곳의 프로젝트, 브랜치 선택은 **`disabled`**이고 같은 값을 숨은 입력(`fromProjectId`, `fromBranch`, `toProjectId`, `toBranch`)으로 보낸다(`T:pullrequest/edit.html:23-33,44-54`). 제목 `#title`(255, `data-is-user-has-typed=true`로 병합 결과가 덮어쓰지 못함), 편집기(`body`, `data-is-user-has-typed=true`), 첨부(`PULL_REQUEST`, PR 번호), `저장`(`button.save`), `취소`(history.back), `커밋` 탭 + `#__commits`. 열린 요청(`open`)이면 상태 칸 `#status`를 그린다.
- 스크립트는 만들기와 같은 `git.Write`와 `yona.Mention`(`:98-115`). `state`가 `OPEN`이면 병합 결과를 한 번 읽어 커밋 표를 채운다. 담당자와 라벨 영역은 이 폼에 없다(개요에서 고친다).
- `POST <PR>/edit`(`:130-160`): `title`, `fromBranch`, `toBranch` 비었거나 255자 초과 `400`(`error.validation`), `body` 파라미터 없음 `413`. 브랜치가 바뀌었는데(숨은 입력이라 화면에서는 안 바뀜) 같은 조건의 다른 열린 요청이 있으면 `303`으로 `<받는 프로젝트>/pullRequest/<번호>/editform`에 경고(`pullRequest.duplicated`). 성공은 `303`으로 개요(첨부 flash).
- 권한: `UPDATE`(화면의 `수정` 링크 조건과 같음).

---

## 6. 포크(fork)와 복제 안내(clone)

### 6.1 포크 폼

- `GET /:user/:project/newFork?forkOwner=`: `@AnonymousCheck(requiresLogin)`, `@IsCreatable(FORK)`, `@RequiresVcs(FORK)`(`K:pullrequest/ForkController.kt:57-81`). `forkOwner`가 내가 관리자(`ORG_ADMIN`)인 조직의 이름이면 그 조직, 아니면 내 로그인 ID가 대상(`D:pullrequest/ForkService.kt:49-53`). 모델: `original`(`소유자 / 이름`), `forkAction=$base/fork`, `cancelUrl=$base/pullRequests`, `forkOwner`, `forkName`(소유자에게 같은 이름이 있으면 `이름-1` 형태), `owners`(나 + 내가 관리자인 조직), `existing`(대상이 이미 포크한 프로젝트), `protectedAllowed`(대상이 조직일 때만).
- 요소(`T:pullrequest/fork.html:17-84`):

| 요소 | 조건 | 동작 | 요청 | 응답 |
|---|---|---|---|---|
| 소유자 select `#project-owner` | 항상 | 바꾸면 `document.location.href = <option data-url>`(`.../newFork?forkOwner=<값>`)로 이동. 첫 항목은 내 로그인 ID(`selected` 없음), 조직은 현재 대상이면 selected | `GET` | 폼 다시 그림 |
| 이름 `#inputName` `name=name` | 항상 | 입력. 옆에 `project.name.alert`. 레이블 `for="inputOwner"`는 존재하지 않는 id를 가리킴(select의 id는 `project-owner`, 발견 PR-F18) | | |
| 공개 범위 라디오 `name=projectScope` | `PUBLIC`(기본 checked), `PROTECTED`(`protectedAllowed`일 때만), `PRIVATE` | 선택 | | |
| 이미 포크함 안내 | `existing`이 있을 때 | `fork.already.exist`와 `원본 > 포크` 링크 목록. 없으면 도움말 이미지 `/assets/images/fork-pull/fork.jpg`와 `fork.help.*` 글 | | |
| `포크`(submit)와 `취소` | 항상 | | `POST <원본>/fork` 폼 인코딩: `owner`(숨은 입력과 select가 같은 이름), `name`, `projectScope` | 6.2 |

- 스크립트: `project.Fork.js`(`T:...:88-93`)는 소유자 select의 `change`만 묶는다.

### 6.2 `POST .../fork`와 clone 화면

- `K:pullrequest/ForkController.kt:83-116`: `bind`가 `ProjectNameRules.violations(name)`이 있거나 `projectScope`가 모르는 값이면 **`IllegalStateException`으로 `500`**(v1.6 `form.get()` 예외를 따름, `:130-134`, 발견 PR-F17). 대상 소유자에게 같은 이름이 이미 있으면 `303`으로 `.../newFork?forkOwner=<대상>`에 경고 `project.name.duplicate`. 아니면 **화면** `pullrequest/clone`(상태 200)을 그린다. 어노테이션 `@Transactional(readOnly = true)`.
- `T:pullrequest/clone.html`: `legend`(`fork.forking(원본 소유자, 원본 이름, 대상 소유자, 대상 이름)`)와 안내 2문단. 문서 준비 때 `NProgress.start()` 후 **3초 뒤** `yona.http.post(cloneUrl, {owner, name, projectScope}, cb)`를 보낸다. 응답 `{status, url}`: `status === "failed"`이면 `$yona.alert(fork.failed)` 확인 뒤 `location.replace(data.url)`, 아니면 곧바로 `location.replace(data.url)`.
- `POST .../clone`(`:118-137`): `@IsCreatable(FORK)`, `@RequiresVcs(FORK)`, `@ResponseBody`. 같은 이름이 있거나 원본이 없으면 `{"status":"failed","url":"<원본>/pullRequests"}`. 포크 중 예외도 같은 실패. 성공이면 `{"status":"success","url":"<새 프로젝트>"}`. 이 메서드에는 메서드 수준 `@AnonymousCheck(requiresLogin)`가 없고 클래스 수준만 있다(익명은 `IsCreatable`에서 `403`).

### 6.3 권한

`FORK` 만들기(공개 프로젝트면 로그인 사용자 모두, 비공개는 구성원과 조직). Git과 Mercurial이 지원한다(`VcsCapability.FORK`).

---

## 7. 메시지(화면 글자) 점검

- 템플릿이 쓰는 정적 키 178개 중 `messages_ko_KR.properties`에 없는 것은 없다(동적 접두어 `pullRequest.review.form.`, `pullRequest.review.state.`, `pullRequest.state.`는 값 `commented|approved|changes_requested|dismissed|notice|pending`, `open|closed|merged|conflict`로 모두 있음. 다만 `State.REJECTED`, `RESOLVED`, `DRAFT`에는 `pullRequest.state.*` 키가 없어 이 상태 값이 나오면 키 이름이 그대로 보인다, `D:enumeration/Enumerations.kt:9-12`).
- `messages_ja_JP.properties`에는 이 영역의 키 중 `pullRequest.merge`, `pullRequest.review`, `pullRequest.changes.all`, `pullRequest.additional.changes`, `pullRequest.review.closed`, `pullRequest.review.total`, `review.outdated`가 없다. 러시아어와 우즈베크어에는 `pullRequest.merge`가 없다. 없으면 기본 번들(영어 `messages.properties`, 이 키들은 모두 있음)로 대체된다(`MessageSourceConfig`의 `setFallbackToSystemLocale` 기본 true).

---

## 8. 발견 사항(pullrequest-F)

번호는 이 영역 안에서 부른다. "서버 확인"은 컨트롤러와 서비스 코드를 읽은 사실, "추측"은 실행해 보지 못한 것이다.

| 번호 | 구분 | 내용 | 근거 |
|---|---|---|---|
| pullrequest-F1 **[재현됨, 심각, 수정됨, A-459]** | 서버 허점(화면은 제한, 서버는 없음) | 만들기와 병합 결과 요청은 보내는 프로젝트 구성원 여부, 연관 프로젝트 여부, 받는 프로젝트의 읽기 권한을 확인하지 않는다. 화면의 선택지만 제한한다. `mergeResult`는 번호만 주면 임의 프로젝트의 커밋 목록(작성자, 메시지)을, `newPullRequestForm`은 임의 프로젝트의 브랜치 이름을 돌려준다. 만들기도 임의 두 프로젝트 사이에 PR을 만든다 | `K:pullrequest/PullRequestCreateController.kt:105-106,144-148,177-179,237-241`, `D:pullrequest/PullRequestService.kt:82-101` |
| pullrequest-F2 **[재현됨, 수정됨, A-459]** | 서버 허점 | 변경 탭의 댓글 등록(`POST /:user/:project/pullRequest/:pullRequestId/comments`)은 `:pullRequestId`를 DB 번호로 찾고 그 PR이 URL의 프로젝트에 속하는지 확인하지 않는다. 스레드는 URL의 프로젝트 소속으로 만들어진다. 위키도 "번호가 아니라 DB 번호"라고만 적었다 | `K:code/ReviewCommentController.kt:119-146`, `D:comment/ReviewCommentService.kt:68-93`, [web/review-comments.md](../web/review-comments.md) |
| pullrequest-F3 **[수정됨, R-159, A-459]** | 화면 일관성 | 코드 접근 구성원 제한은 목록에만 있고 개요, 변경(diff 내용), 타임라인, 상태에는 없다 | `K:pullrequest/PullRequestListController.kt:124-126`와 `PullRequestViewController.kt` 전체(검사 없음). v1.6 대조는 하지 않음 |
| pullrequest-F4 **[브라우저로 확인됨, v1.6 결함 유지, R-148]** | 스크립트 결함(코드 읽기, 실행 확인 안 함) | 상태 폴링이 `#state`를 다시 그린 뒤 `yona.Request.bind`를 부르지 않는다. 첫 폴링은 항상 다시 그리므로(`sStateHTML` 초기값 `""`) 병합된 요청의 `삭제`와 `복원`(`data-request-method`)은 첫 폴링(10초) 이후 클릭에 반응하지 않는다. `복원`은 `<a href>`라 GET으로 이동해 `405`가 된다. 이 동작을 확인하는 시험이 없다(`src/test/js/gitview.test.mjs`) | `S:esm/service/git.View.js:153-183`, `S:esm/Request.js:122-146` |
| pullrequest-F5 | 화면과 서버 불일치 | 보낸 브랜치 `삭제`, `복원` 단추는 보낸 사람(`isContributor`)에게 보이지만 서버는 PR `UPDATE`를 요구하고, PR 작성자는 작성자 권한 목록(`AUTHOR_TYPES`)에 없어 받는 프로젝트의 구성원이 아니면 `403`이다 | `K:pullrequest/PullRequestPageAssembler.kt:208-210`, `K:pullrequest/PullRequestActionController.kt:162-182`, `src/main/kotlin/dev/yona/access/AccessControl.kt:307-313` |
| pullrequest-F6 | 서버 허점 | 병합(`accept`)과 닫기(`close`)는 PR의 상태를 확인하지 않는다(화면은 열린 요청에서만 단추를 보임). 병합 충돌 결과(`CONFLICT`)는 별도 응답 없이 개요로 `303` | `D:pullrequest/PullRequestWorkflow.kt:65-114`, `K:...ActionController.kt:59-89` |
| pullrequest-F7 | 화면과 서버 | `닫기`, `다시 열기` 링크의 `href`가 POST 전용 주소라 JS 없이 열면 GET이 되어 `405`가 된다 | `T:pullrequest/view.html:47-48` |
| pullrequest-F8 | 화면과 서버 불일치 | 구독 단추는 `WATCH` 권한으로 보이지만 서버(`POST /watch`)는 로그인과 `READ`만 본다 | `K:pullrequest/PullRequestViewController.kt:120`, `K:WatchController.kt:40-47` |
| pullrequest-F9 | 화면과 서버 불일치 | 목록의 최근 푸시 알림 닫기(`DELETE .../pushedBranch/:id/delete`)는 서버가 프로젝트 `DELETE`(매니저, 조직 관리자, 사이트 관리자)를 요구하는데 화면은 포크 프로젝트를 읽을 수 있는 누구에게나 알림을 보이고, 닫기 단추는 조건이 없다 | `K:pullrequest/PullRequestListController.kt:172-180,212-229`, `T:pullrequest/partial-search.html:62-73` |
| pullrequest-F10 | 스크립트 누락(추측: 실행 안 함) | 목록에서 담당자 `#assignees`, 리뷰 요청 `#reviewRequested`, 라벨 `#labelIds`에는 이 화면에 change 처리기가 없다. 보낸 사람 상자만 자동 제출한다. 이슈 목록은 `[data-search]` change에서 제출하는데(`S:esm/service/issue.List.js:74`) 코드 주고받기 목록에는 같은 코드가 없다 | `T:pullrequest/partial-search.html:167-179` |
| pullrequest-F11 | 추측 | 두 단 보기 처리기를 htmx 교체 뒤 다시 붙이는 코드를 찾지 못했다 | `S:esm/service/twoColumnMode.js`, `S:common/yona.Htmx.js` |
| pullrequest-F12 | 마크업 | 개요의 리뷰 판정 폼 `form#review-form`과 변경 탭의 줄 댓글 팝업 `div#review-form`(`common/review-form`)이 같은 `id`를 쓴다. 개요의 머리 `리뷰` 링크(`#review-form`)는 개요에서만 의미가 있다. 개요에서 `yona.git.View`가 `CodeCommentBox.init`을 부르면(`bCommentable`일 때) `#review-form`을 팝업용으로 읽지만 그 안에 `form`, `textarea`가 없어 동작하지 않는다 | `T:pullrequest/view.html:69`, `T:common/review-form.html:11`, `S:esm/service/git.View.js:113-120`, `S:esm/CodeCommentBox.js:52-59` |
| pullrequest-F13 | 스크립트 | `button.moreBtn` 처리기는 개요에서 문서 준비 때 한 번만 붙어, Ajax로 다시 읽은 타임라인 줄에는 붙지 않는다(만들기 폼은 `document.body` 위임이라 영향 없음) | `T:pullrequest/view.html:217-219`, `S:pullrequest.Meta.js:60-73` |
| pullrequest-F14 | 스크립트 | 타임라인 라벨 상자 글자색을 개요 인라인은 `.event > .label`에, `pullrequest.Meta.js`는 `.event > .badge`에 맞춘다. 서버가 그리는 라벨 상자는 `div.label.issue-label`이라 Ajax 새로 읽기 뒤에는 글자색이 다시 맞춰지지 않는다 | `T:pullrequest/view.html:194-199`, `S:pullrequest.Meta.js:68-71`, `K:pullrequest/PullRequestPageAssembler.kt:306` |
| pullrequest-F15 | 스크립트 | 상태 폴링이 충돌이면 `#btnAccept`만 숨긴다. v1.6에는 없던 분할 단추의 드롭다운 토글과 확인 폼은 그대로 남는다. 폴링은 `#btnAccept`가 있다고 가정해 병합 상자가 없는 화면에서도 호출하나 `$()`가 비어 아무 일 없다 | `S:esm/service/git.View.js:179`, `T:pullrequest/partial-info.html:38-41` |
| pullrequest-F16 | 메시지 누락 | `pullRequest.fromProjectId.required`, `pullRequest.toProjectId.required`가 한국어 메시지에 없다 | `S:esm/service/git.Write.js:236-240`, `M:` |
| pullrequest-F17 | v1.6 결함 재현 | 포크 이름 규칙 위반, 공개 범위 값 오류는 `500`이다 | `K:pullrequest/ForkController.kt:130-134` |
| pullrequest-F18 | 마크업 | 포크 폼 `label for="inputOwner"`가 가리키는 id가 없다(select의 id는 `project-owner`) | `T:pullrequest/fork.html:49,51` |
| pullrequest-F19 | 서버 허점 | 임시 리뷰 삭제, 버리기 요청(`PendingReviewController`)에는 `@IsAllowed`가 없어 프로젝트 읽기 검사가 없다. 서비스가 내 임시 댓글만 다루므로 영향은 `404` 또는 무동작이다 | `K:pullrequest/PendingReviewController.kt:34-56` |
| pullrequest-F20 | 시험 | `code.Diff`와 병합 상자 인라인 스크립트, 상태 폴링 후 바인딩을 직접 확인하는 JS 시험이 없다(`gitview.test.mjs`는 폴링 요청 주소만, `gitwrite.test.mjs`는 `git.Write`를 확인) | `src/test/js/` |
| pullrequest-F21 | 마크업 | 병합 상자 `style` 규칙과 `<script>`가 `partial-info` 조각 안에 인라인으로 들어 있어, 조각을 불러오는 모든 페이지(개요, 변경)에서 매번 다시 실행된다 | `T:pullrequest/partial-info.html:31-36,62-88` |

---

## 9. 범위 점검(담당 템플릿 14개)

| 템플릿 | 다룬 절 |
|---|---|
| `pullrequest/list.html` | 1 |
| `pullrequest/partial-search.html` | 1 (목록 줄, 필터, 탭, 푸시 알림, 두 단 보기, 페이지 막대) |
| `pullrequest/view.html` | 2.1, 2.4, 2.7, 2.8, 2.9 |
| `pullrequest/partial-info.html` | 2.2, 2.3 |
| `pullrequest/partial-branch.html` | 2.4 |
| `pullrequest/partial-state.html` | 2.5 |
| `pullrequest/partial-state-page.html` | 0.1, 2.5(상태 조각 틀), 2.8(폴링) |
| `pullrequest/partial-timeline.html` | 2.6 |
| `pullrequest/changes.html` | 3 |
| `pullrequest/create.html` | 4 |
| `pullrequest/partial-merge-result.html` | 4.4 |
| `pullrequest/edit.html` | 5 |
| `pullrequest/fork.html` | 6.1 |
| `pullrequest/clone.html` | 6.2 |

함께 읽은 공용 조각: `common/review-form.html`, `common/comment-form.html`, `common/comment-delete-modal.html`, `common/file-uploader.html`, `issue/partial-select-label.html`, `code/partial-comment-thread.html`(스레드 조각, [code.md](code.md) 8절).
