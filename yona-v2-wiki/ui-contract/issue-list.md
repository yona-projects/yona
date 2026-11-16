---
title: 이슈 목록 화면 계약 (프로젝트 이슈 목록, 검색, 일괄 변경)
type: ui-contract
sources:
  - src/main/resources/templates/issue/list.html
  - src/main/resources/templates/issue/partial-list-wrap.html
  - src/main/resources/templates/issue/partial-list.html
  - src/main/resources/templates/issue/partial-list-quicksearch.html
  - src/main/resources/templates/issue/partial-searchform.html
  - src/main/resources/templates/issue/partial-massupdate.html
  - src/main/resources/templates/issue/partial-select-label.html
  - src/main/kotlin/dev/yona/web/issue/IssueListController.kt
  - src/main/kotlin/dev/yona/web/issue/IssueMassUpdateController.kt
  - src/main/kotlin/dev/yona/web/issue/IssueListAssembler.kt
  - src/main/kotlin/dev/yona/domain/issue/IssueSearchService.kt
  - src/main/kotlin/dev/yona/domain/issue/IssueMassUpdateService.kt
  - src/main/kotlin/dev/yona/web/access/ProjectCheckStage.kt
  - src/main/resources/static/assets/javascripts/esm/service/issue.List.js
  - src/main/resources/static/assets/javascripts/esm/service/issue.MassUpdate.js
updated: 2026-11-11
---

# 이슈 목록 화면 계약

영역 요약, 공통 계약(접근 검사 체인, CSRF, 응답 형태), 권한 행렬, 발견 사항(`issue-F1`부터)은 [issue.md](issue.md)에 있다. 이 글은 `GET /:user/:project/issues` 화면만 다룬다. 서버 동작의 설명은 이미 있는 [web/issue-list.md](../web/issue-list.md), [web/issue-mass-update.md](../web/issue-mass-update.md)를 가리키고, 여기서는 화면이 지켜야 할 계약과 그 글에 없거나 어긋난 부분만 적는다.

경로 약어: `T/` = `src/main/resources/templates/`, `K/` = `src/main/kotlin/dev/yona/`, `J/` = `src/main/resources/static/assets/javascripts/`, `S/` = `app/assets/stylesheets/scss/`(CSS 원본), `TEST/` = `src/test/kotlin/dev/yona/web/issue/`.

## 1. 화면 개요

| 항목 | 내용 |
|---|---|
| URL | `GET /:user/:project/issues` (`K/web/issue/IssueListController.kt:72`) |
| 권한 어노테이션 | 메서드 `@IsAllowed(READ)`(73), 클래스 `@AnonymousCheck`(`IssueListController.kt:54`). 프로젝트의 이슈 메뉴(`menuSetting.issue`)가 꺼져 있으면 읽기 검사 단계에서 `404`(`error/notfound`)이다. 일괄 변경 `POST`는 `@NullProjectCheck`라 이 검사를 거치지 않는다(`K/web/access/ProjectCheckStage.kt:91-114`, 시험 `src/test/kotlin/dev/yona/web/access/ProjectMenuOffAccessTest.kt:53`, `issue-F24`) |
| 템플릿 | 전체 화면 `T/issue/list.html`, 조각 `T/issue/partial-list-wrap.html :: listWrap`(`IssueListController.kt:94`) |
| 레이아웃 | `layout/project-layout :: projectLayout(제목, projectView, 'ISSUE', 본문)`(`list.html:11`), 본문 맨 위에 `project/menu :: projectMenu(..., 'main-menu-only')`(`list.html:13`) |
| 문서 제목 | `프로젝트이름 - ` + `menu.issue` 메시지(`list.html:11`, 모델 `title="menu.issue"`) |
| 모델 | `projectView`, `cond`(IssueSearchCondition), `items`, `drafts`, `showDrafts`, `pageIndex`, `totalPageCount`, `openCount`, `closedCount`, `quick`, `excelUrl`, `search`, `canMassUpdate`, `massUpdate`(구성원만)(`IssueListController.kt:134-159`) |
| 쓰는 조각 | `partial-list-quicksearch`, `partial-searchform`, `partial-massupdate`, `partial-list`, `partial-select-label`, `milestone/partial-status`, `common/two-column-mode-checkbox-area`, `common/show-subtasks-checkbox`, `common/comment-count`, `common/vote-count`, `common/sharer-count`, `help/keymap :: keymap('issueList', projectView)`, `common/select2`, `common/calendar` |
| 용도 | 프로젝트의 이슈 목록, 검색, 상태 탭, 정렬, 쪽 나누기, 엑셀 내려받기, 일괄 변경 |
| 마일스톤 보기 | 같은 `partial-list`, `partial-massupdate`를 쓴다(마일스톤 담당 글 참조) |

### 응답 형식 분기(`IssueListController.kt:84-101`)

| 요청 | 결과 |
|---|---|
| 기본 | 전체 화면, 로그인 사용자의 최근 방문 프로젝트에 기록(`recentVisits.visits`, 98행) |
| `HX-Request: true`(htmx) | `format=fragment`로 고정, 조각만, `Cache-Control: no-cache, no-store`(87, 93) |
| `format=fragment` | 위와 같음. 시험 `TEST/IssueListTest.kt:367`, `:379` |
| `X-Requested-With: XMLHttpRequest`만 | JSON(이슈 PK를 키로 `{id,title,state,createdDate,link}`), `exceptId`가 숫자 아니면 400 JSON `{}`(118-120) |
| `format=xls` | 엑셀(조건에 맞는 모든 이슈, 쪽 나누기 없음), 파일명 `프로젝트_issues_yyyyMMddHHmm.xls`(108-116) |
| 숫자나 날짜 형식이 틀린 조건 | `ErrorViews.badRequest()` 400 HTML(84, `IssueConditionBinder` 217-243) |

htmx 요청이 `X-Requested-With`도 함께 보내도 htmx가 우선이다(87행). 쪽 번호는 `pageNum`(1부터, 기본 1)이고 서버 안에서 0부터로 바꾼다(83-84행).

## 2. 레이아웃 계약

- 바깥: `.page-wrap-outer > .project-page-wrap`(`list.html:15-19`).
- 본문은 `div[list-container].row.issue-list-wrap`(`partial-list-wrap.html:10`). 이 요소가 htmx의 교체 영역이다.
  - `hx-target="this"`, `hx-select="div[list-container] > *"`, `hx-swap="innerHTML show:window:top"`(교체 뒤 창 맨 위로 스크롤), `hx-push-url="true"`(주소창을 요청 주소로 바꿈), `hx-request='{"timeout":3000}'`(3초 안에 응답이 없으면 일반 이동으로 대체, `J/common/yona.Htmx.js`의 `htmx:timeout` 처리).
  - 응답에 문자열 `list-container`가 없으면(로그인 화면 등) 조각 교체를 하지 않고 같은 주소를 일반 이동으로 다시 연다(`yona.Htmx.js` `htmx:beforeSwap`).
- 왼쪽 단: `div.left-menu.col-lg-2`(빠른 검색 + 검색 폼). 오른쪽 단: `div.col-lg-10#span10`(새 이슈 단추, 탭, 목록).
- 반응형(`S/_responsive.scss`의 `@media all and (max-width: 720px)`, 1행): `.hide-in-mobile`은 `display:none !important`(302행), `.show-in-mobile`은 `display:block !important`(290행). `col-lg-*`는 부트스트랩 lg(992px) 미만에서 세로로 쌓인다.
  - 모바일에서 숨는 것: 상세 검색 폼(`#advanced-search-form.hide-in-mobile`, `partial-searchform.html:23`)과 그 위의 `<hr>`(15), 일괄 변경 막대(`.mass-update-wrap.hide-in-mobile`, `partial-massupdate.html:26`), 줄의 선택 상자(`label.mass-update-check.hide-in-mobile`, `partial-list.html:16`), 줄 오른쪽 단(담당자 아바타와 마감일, `col-lg-3.hide-in-mobile`, 83행).
  - 두 열 보기 체크(`.two-column-icon.hide-in-mobile`)도 모바일에서 숨는다.
- 일괄 변경 막대가 있으면 `.filter-wrap.board.mass-update-sticky`로 스크롤 때 맨 위에 붙는다(`S/_page.scss:5482`, `position: sticky; top: 0; z-index: 900`). 붙은 동안 `stuck` 클래스는 `issue.MassUpdate.js:47-61`의 IntersectionObserver가 단다.
- 빈 상태와 목록 상태가 다르다(`partial-list-wrap.html:34-75`):
  - 일반 이슈가 0개: (초안 있으면 초안 목록) + `div.error-wrap`(`ico-err1` + `issue.is.empty` 한국어 "등록된 이슈가 없습니다.") + 단축키 도움말. 정렬 링크, 일괄 변경 막대, 엑셀 링크, 쪽 나누기가 모두 없다.
  - 1개 이상: 일괄 변경 막대(구성원), 정렬 링크(`items.size() > 1`), 초안 목록, 이슈 목록, 엑셀 링크, 단축키 도움말, `#pagination`.
  - 초안: `showDrafts`(첫 쪽이고, 담당자, 작성자, 멘션, 댓글, 공유, 즐겨찾기 조건이 없고, `state != closed`, `IssueListController.kt:138`)일 때만. 이슈가 있는 분기에서는 초안이 0개여도 빈 `ul.post-list-wrap`을 그린다(63행, 시험 `TEST/IssueListTest.kt:234`). 이슈가 없는 분기에서는 초안이 있을 때만 그린다(36-38행, 시험 `:243`).

## 3. 동작 요소 표

### 3.1 왼쪽 단: 빠른 검색(`partial-list-quicksearch.html`)

모든 링크는 `a[list-filter][href="#"]`이고 `J/esm/service/issue.List.js:178-188`(`_onClickSearchFilter`)이 처리한다. 동작은 공통이다: 링크의 `data-*`(camelCase 키: `assigneeId`, `authorId`, `commenterId`, `milestoneId`)마다 검색 폼의 `[data-search="키"]` 입력란 값을 덮어쓰고 `yona.Htmx.submit(#search)`로 제출한다. 요청은 아래 3.2의 폼 요청과 같다.

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| "열린 이슈 전체"(`issue.list.all.open`, 닫힘 탭이면 `issue.list.all.closed`) + 개수 | 링크 | 항상. `active`는 담당자, 작성자, 댓글 조건이 모두 없을 때 | 세 조건을 비운다(`data-assignee-id=""` 등) | `GET {base}/issues?...`(HX-Request) | 목록 조각 교체 | READ | `partial-list-quicksearch.html:11-16`, 개수 `IssueListController.kt:165-176` |
| "내게 할당" + 개수 | 링크 | `quick.meId != null`(로그인). `active`는 `cond.assigneeId == meId` | `assigneeId=내 번호`, 나머지 비움 | 위와 같음 | 위와 같음 | READ | `:17-23` |
| "내가 작성" + 개수 | 링크 | 로그인 | `authorId=내 번호` | 위와 같음 | 위와 같음 | READ | `:24-29` |
| "내가 댓글" + 개수 | 링크 | 로그인 | `commenterId=내 번호` | 위와 같음 | 위와 같음 | READ | `:30-35` |

- 개수는 현재 검색 조건을 유지하되 담당자, 작성자, 댓글 조건만 뺀 값이다(`IssueListController.kt:167`).
- `milestoneId`는 `th:attrappend`로 `cond.milestoneId`를 실어 보낸다(`:12`). 그러므로 마일스톤 조건은 빠른 검색을 눌러도 유지된다.

### 3.2 왼쪽 단: 검색 폼(`partial-searchform.html`)

`form#search[name=search]` `th:action=th:hx-get={base}/issues` `method=get`(`:10`). 숨은 입력: `orderBy`, `orderDir`, `state`, `commenterId[data-search=commenterId]`(11-14). 제출은 htmx가 가로채 `div[list-container]`만 교체한다. 요청 시점의 `action` 값을 쓴다(`yona.Htmx.js` `htmx:configRequest`).

| 요소 | 종류 | 조건 | 동작 | 요청(쿼리) | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 검색어 `filter` | 텍스트 입력 | 항상 | Enter로 폼 제출(htmx). 제목, 본문, 댓글에서 대소문자 무시로 찾는다 | `filter=` | 조각 교체 | READ | `:18`, 검색 `K/domain/issue/IssueSearchService.kt`의 `filter` 절 |
| 돋보기 버튼 `[data-submit]` | 버튼 | 항상 | 클릭하면 폼 제출 | 위와 같음 | 위와 같음 | READ | `:19`, `issue.List.js:71` |
| 작성자 `authorId` | select2(`data-format=user`) | 항상. 옵션: 전체(`""`), "내가 작성"(값=내 번호, `search.showAuthoredByMe`), 작성자들(`search.authors`) | 값 바뀌면 제출(`[data-search]`의 change) | `authorId=` | 조각 교체 | READ | `:27-35`, `issue.List.js:74` |
| 담당자 `assigneeId` | select2(`data-format=user`) | 항상. 옵션: 전체, `-1` 담당자 없음, "내게 할당"(`projectView.projectMember`일 때만, 값=내 번호), 담당자들 | 위와 같음 | `assigneeId=` | 위와 같음 | READ | `:41-49` |
| 마일스톤 `milestoneId` | select2(`data-format=milestone`) | `search.hasMilestones`(프로젝트에 마일스톤이 하나라도 있을 때). 옵션: 전체, `-1` 마일스톤 없음, 열림, 닫힘 optgroup | 위와 같음 | `milestoneId=` | 위와 같음. 선택한 마일스톤이 있으면 그 아래에 `milestone/partial-status`(진행 막대, 마감일) + `<hr>` | READ | `:53-74` |
| 마감일 `dueDate` | 텍스트 + 달력(`data-toggle=calendar`) + `.btn-calendar` | 항상 | 날짜를 고르거나 값이 바뀌면 `change` → 제출. 제출 직전 `isValidDate`로 검사해 틀리면 `issue.error.invalid.duedate` 알림 후 중단 | `dueDate=yyyy-MM-dd`(그날까지, 그날 포함) | 조각 교체 | READ | `:76-80`, 검증 `issue.List.js:293-309`(`htmx:beforeRequest` 위임) |
| 라벨 `labelIds` | select2 다중(`data-format=issuelabel`, `data-allow-clear`) | `search.labels`가 비어 있지 않을 때만 `dl`을 그린다 | 값 바뀌면 제출. 고른 라벨을 모두 가진 이슈만 찾는다 | `labelIds=`(여러 번) | 조각 교체 | READ | `partial-select-label.html:9-28`, 서버 `IssueSearchService.addLabels` |
| 라벨 관리 톱니바퀴 링크 | 링크 | `search.canManageLabels`(프로젝트 `UPDATE` 권한: 프로젝트 관리자, 조직 관리자, 사이트 관리자. `accessControl.isAllowed(viewer, project.asResource(), UPDATE)`). 라벨이 없으면 "라벨 관리"(`label.manage`) 글자도 보인다 | 라벨 관리 화면으로 이동(같은 창) | `GET {base}/issue/labelsform` | 라벨 관리 화면 | 프로젝트 `UPDATE`(보이는 조건과 같다. 일반 구성원에게는 링크가 없다) | `partial-searchform.html:84-88`, `K/web/issue/IssueListController.kt:199`, `K/web/issue/IssueLabelController.kt:88-90` |
| 라벨 `[편집]` 링크 | 링크 | `partial-select-label`이 그린 `dt` 안, `view.manager`일 때만(`target=_blank`) | 새 탭으로 라벨 관리 | 위와 같음 | 위와 같음 | 프로젝트 `UPDATE` | `partial-select-label.html:12` |

- 입력란은 모두 폼 안에 있으며 선택 상자는 `data-search="필드명"` 속성으로 빠른 검색과 라벨 클릭이 값을 채울 수 있게 되어 있다.
- 마일스톤 `milestoneId`와 담당자 `assigneeId` 값 `-1`은 서버가 "없음"으로 해석한다(`IssueSearchCondition.NO_VALUE`, `IssueSearchService.kt:66`).

### 3.3 오른쪽 단 머리

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| "새 이슈"(`issue.menu.new`) | 링크 `ybtn-success` | 항상(비로그인에게도 보인다) | 이동 | `GET {base}/issueform` | 로그인하지 않으면 로그인 폼으로 303, 만들 수 없으면 403 | 서버 `@AnonymousCheck(requiresLogin)` + `@IsCreatable(ISSUE_POST)` | `partial-list-wrap.html:17`, `K/web/issue/IssueCreateController.kt:62-64` |
| 열림 탭 `a[state=open]` + `openCount` | 링크 | 항상. `active`는 `cond.state == 'open'` | `state` 숨은 입력을 `open`으로 두고 제출 | `state=open` | 조각 교체 | READ | `:21-24`, `issue.List.js:122-127` |
| 닫힘 탭 `a[state=closed]` + `closedCount` | 링크 | 항상 | `state=closed` | `state=closed` | 조각 교체(닫힘이면 초안 목록은 사라진다) | READ | `:25-28` |
| 두 열 보기 체크 `#two-column-mode` | 체크박스 | 항상(모바일 숨김) | 켜면 `localStorage.useTwoColumnMode=true`, 제목과 `.twoColumeModeTarget` 링크를 눌렀을 때 새로 가지 않고 오른쪽 오프캔버스(`#pageslide`, iframe)에 연다 | 없음(iframe이 이슈 보기를 읽는다) | 오프캔버스가 열린 동안 `.left-menu`를 숨긴다 | 없음 | `common/two-column-mode-checkbox-area.html:9-14`, `J/esm/service/twoColumnMode.js:66-107` |
| 하위 이슈 보기 체크 `#toggle-show-subtasks` | 체크박스 | 항상 | 켜면 `localStorage.showSubtasksAlways=true`, 모든 `.child-issue-list`를 펼친다. 끄면 접는다. 페이지를 열 때 저장값이 `true`면 자동으로 켠다 | 없음 | 없음 | 없음 | `common/show-subtasks-checkbox.html:9-14`, `J/esm/service/showSubtask.js:10-30` |

### 3.4 정렬 줄과 일괄 변경 막대(`.filter-wrap.board`)

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 정렬 링크 4개(마감일 `dueDate`, 수정일 `updatedDate`, 날짜 `createdDate`, 댓글 `numOfComments`) | 링크 `a[orderBy]` | `items.size() > 1`. 현재 정렬이면 `active`와 방향 화살표(`down`), 누르면 방향이 뒤집힌다(`orderDir`을 반대로). 현재가 아니면 `orderDir=desc`로 시작 | `orderBy`, `orderDir` 숨은 입력을 바꾸고 제출 | `orderBy=&orderDir=` | 조각 교체 | READ | `partial-list-wrap.html:52-58`, `issue.List.js:105-113` |
| 일괄 변경 막대 | 아래 3.5 | `canMassUpdate`(= `projectView.projectMember`, 역할 무관 구성원) | | | | | `IssueListController.kt:156`, `partial-list-wrap.html:48-50` |

서버는 정렬 기준이 `ORDERABLE`(`id, number, title, state, weight, createdDate, updatedDate, dueDate, numOfComments`)에 없으면 `createdDate`로 바꾸고, 정렬은 항상 `weight desc, isDraft desc, 기준, id`다(`IssueSearchService.kt:188-193`, 307). 마감일 정렬은 마감일 없는 이슈가 항상 뒤다.

### 3.5 일괄 변경 막대(`partial-massupdate.html`)

`form#mass-update-form[method=post][action={base}/issues]`(`:27`)는 **일반 폼 제출**이다(ajax 아님). 처음에는 5개 드롭다운 버튼이 모두 `disabled`이고, 선택 상자가 하나 이상 체크되면 켜진다(`issue.MassUpdate.js:148-159`).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 전체 선택 `#check-all` | 체크박스 | 막대가 있을 때 | 모든 `[name=checked-issue]`를 같은 값으로 체크, 변경 이벤트 발생. 단축키 `CTRL+A`(폼 입력란 밖일 때)도 같은 일을 한다 | 없음 | 체크된 줄에 `active` 클래스 | 없음 | `partial-massupdate.html:28-32`, `issue.MassUpdate.js:120-130` |
| 상태 드롭다운 `#state`(`data-name=state`) | 드롭다운 | 항상. 항목 `OPEN`, `CLOSED` | 항목 선택 즉시 폼 제출. 숨은 입력 `state`를 만들어 값을 넣는다(`yona.ui.Dropdown`) | `POST {base}/issues` 본문 `state=OPEN|CLOSED` + `issues[n].id` | Accept에 JSON이 없으므로 `303 Location: Referer`. Referer가 없거나 이 사이트의 주소가 아니면 목록 주소(`IssueMassUpdateController.kt:119-120`) | 이슈마다 `UPDATE` | `:33-41`, `issue.MassUpdate.js:329-347` |
| 담당자 `#assignee`(`data-name=assignee.id`) | 드롭다운 | 항상. 항목: `-1` 담당자 없음, "내게 할당"(`projectView.projectMember`), 구분선, 담당 가능한 사용자들(아바타, 이름, @로그인) | 위와 같음 | `assignee.id=번호|-1` | 위와 같음 | 위와 같음 | `:43-61` |
| 마일스톤 `#milestone`(`data-name=milestone.id`) | 드롭다운 | `menuSetting.milestone`이고 **열린** 마일스톤이 있을 때 | 위와 같음 | `milestone.id=번호|-1` | 위와 같음 | 위와 같음 | `:63-74` |
| 라벨 추가 `#attaching-label`(`data-name=attachingLabelIds`) | 드롭다운 | `massUpdate.hasLabels` | 라벨 선택 시, 같은 배타 분류(`exclusive`)에서 이미 달린 다른 라벨을 `detachingLabelIds[]`로 함께 보내고 제출 | `attachingLabelIds=번호`, `detachingLabelIds[]=번호...` | 위와 같음 | 위와 같음 | `:76-85`, `issue.MassUpdate.js:359-372` |
| 라벨 제거 `#detaching-label`(`data-name=detachingLabelIds`) | 드롭다운 | `massUpdate.hasLabels` | 체크된 이슈들이 가진 라벨로 목록을 다시 만들어 보이고(분류 이름과 라벨 이름은 이스케이프), 선택하면 제출 | `detachingLabelIds=번호` | 위와 같음 | 위와 같음 | `:86-93`, `issue.MassUpdate.js:254-282` |

- 라벨 추가 목록에서는 체크된 **모든** 이슈가 이미 가진 라벨을 숨기고, 보일 라벨이 없는 분류의 머리글도 숨긴다. 보일 라벨이 하나도 없으면 "라벨 추가" 버튼을 비활성으로 둔다(`issue.MassUpdate.js:189-245`). 체크가 모두 풀리면 두 목록을 원래대로 되돌린다(164-167).
- 제출 직전 현재 스크롤 위치에서 가장 가까운 `.post-item`의 앞 줄 id를 폼 `action`의 `#fragment`로 붙인다(`issue.MassUpdate.js:329-356`). 응답 303이 목록으로 돌아올 때 그 줄로 이동하기 위한 것이다.
- 막대는 htmx 교체 영역 안에 있고 인라인 `<script type="module">`과 `$(document).ready` 초기화를 함께 그린다(`partial-massupdate.html:97-114`). 교체될 때마다 이 스크립트가 다시 실행되어 막대가 다시 초기화된다(추측: htmx가 교체된 영역의 스크립트를 다시 실행하는 기본 동작에 기댄다. 시험 없음).
- 막대에는 삭제 단추가 없다. `welDeleteButton: $("#delete")`는 어디에도 없는 요소다. 서버의 `delete=true` 경로는 화면에서 부르지 않는다(`issue-F9`).

### 3.6 이슈 줄(`partial-list.html`)

각 이슈는 `li.post-item#issue-item-{id}`(`:13`)이다. 줄에는 `th:href`로 `href` 속성이 있으나 이를 읽는 스크립트는 없다(`issue-F13`). 초안(`draftList`, 101행~)은 번호 표시만 다르고 나머지는 같다(`#Draft` 글자 앞의 `#` 위치 차이, 22행과 113행).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 선택 상자 `input[name=checked-issue][data-toggle=issue-checkbox]` | 체크박스 | `projectView.projectMember`(모바일 숨김). `data-issue-id`, `data-issue-labels`(`분류이름,라벨번호,라벨이름,분류번호,단일여부|` 반복) | 변경 시 줄 `li`에 `active` 클래스 토글. 줄 클릭 전파를 막는다(`.mass-update-check` click의 `stopPropagation`) | 없음 | 일괄 변경 막대의 버튼 활성화 | 구성원 | `:16-18`, `issue.List.js:86-95`, `twoColumnMode.js:84-86`, 데이터 문자열 `K/web/issue/IssueListAssembler.kt:121` |
| 번호 링크 `a.title > .post-id` | 링크 | 항상. 초안은 `Draft` 글자 | 이슈 보기로 이동. 두 열 보기가 켜져 있으면 오프캔버스에 연다 | `GET {base}/issue/{number}` | 이슈 보기 | READ | `:21-23` |
| 가중치 화살표 | 아이콘 | `weight > 0`(위), `< 0`(아래) | 툴팁 "가중치 N" | 없음 | 없음 | 없음 | `:24-25` |
| 제목 머리말 `a.title-prefix` | 링크(`javascript:void(0)`) | 제목이 `[머리말]`로 시작할 때(머리말만으로 된 제목이면 없음) | 클릭하면 검색어 입력란에 머리말 글자를 넣고 폼 제출. 마우스를 올리면 같은 글자의 `.title-prefix`에 `title-prefix-hover` | `filter=[머리말]` | 조각 교체 | READ | `:26`, `K/web/TemplateHelper.kt:151-157`, `issue.List.js:30-36`, `list.html:37-46` |
| 제목 링크 `a.title` | 링크 | 항상 | 이슈 보기로 이동(머리말 뺀 제목, 이스케이프) | `GET {base}/issue/{number}` | 이슈 보기 | READ | `:27` |
| 작성자 이름 | 링크 | 작성자 이름이 있을 때. 없으면 `issue.noAuthor` 글자 | 사용자 페이지 | `GET /{loginId}` | 사용자 페이지 | 공개 | `:30-31` |
| 만든 시각 | 글자 | 항상. 툴팁에 전체 시각 | 없음 | 없음 | 없음 | 없음 | `:33` |
| 하위 이슈 진행 막대와 `닫힌/전체` | 막대 | 이 이슈가 부모일 때(`issue.subtask != null`) | 없음 | 없음 | 모두 닫혔으면 `done-outline`, `txt-green`, 아니면 `red-outline` | 없음 | `:35-40` |
| 부모 링크 | 링크 | 이 이슈가 하위 이슈일 때. 글자는 `#번호 제목 앞 10자...` | 부모 이슈로 이동 | `GET /{owner}/{project}/issue/{n}` | 이슈 보기 | READ | `:41-43`, `IssueListAssembler.kt:87-92` |
| 마일스톤 태그 | 링크 | `menuSetting.milestone`이고 마일스톤이 있을 때 | 마일스톤 화면으로 이동 | `GET {base}/milestone/{id}` | 마일스톤 보기 | READ | `:45-47` |
| 댓글 수, 추천 수, 공유자 수 | 링크, 링크, 아이콘 | 각 수가 0보다 클 때만(셋 중 하나라도 있으면 묶음 `span`을 그린다) | 댓글과 추천은 이슈 보기의 `#comments`, `#vote`로 이동 | `GET {base}/issue/{n}#comments` | 이슈 보기 | READ | `:49-53` |
| 라벨 배지 `a.issue-label[data-category-id][data-label-id]` | 링크(`href="#"`) | 라벨이 있을 때 | 검색 폼의 라벨 선택 상자 값에 그 라벨을 더하고 `change`를 내서 제출(이미 고른 라벨이면 중복해서 더한다) | `labelIds=` | 조각 교체 | READ | `:55`, `issue.List.js:136-154` |
| 하위 이슈 목록 `.child-issue-list.hide` | 접힌 영역 | `issue.children`이 있을 때만 내용. 줄 `.post-item`을 누르면 펼친다(`.show()`), 제목 링크를 누르면 전파를 막아 펼치지 않는다. 하위 이슈 보기 체크가 켜져 있으면 항상 펼쳐져 있다 | 안의 항목: 상태 점(`state-label`, 닫힘이면 체크 아이콘), 제목 링크(`.twoColumeModeTarget`), 담당자 이름, 댓글, 추천 수 링크, 라벨(링크 `{base}/issues?state=&labelIds=`), 날짜 | 이슈 보기, 목록 | 이동 | READ | `:56-80`, `issue.List.js:217-225`, `showSubtask.js` |
| 담당자 아바타 | 링크 | 담당자가 있을 때(모바일 숨김). 없으면 빈 `.empty-avatar-wrap` | 담당자 사용자 페이지 | `GET /{loginId}` | 사용자 페이지 | 공개 | `:84-88` |
| 마감일 | 아이콘과 글자 | 마감일이 있을 때(모바일 숨김). 열림이고 지났으면 `overdue`와 `issue.dueDate.overdue`("기한지남"), 아니면 `N days`/오늘, 닫힘은 날짜와 `darkgray-txt`. 툴팁은 열림일 때만 날짜 | 없음 | 없음 | 없음 | 없음 | `:90-94`, `IssueListAssembler.kt:151-170` |

- 줄 위에 마우스를 올리면 배경이 `#fafafa`, 벗어나면 `#fff`(`issue.List.js:243-249`).
- `th:if="${!child.draft or true}"`(`partial-list.html:59`, 149)는 항상 참이다. 초안인 하위 이슈도 목록에 나온다. 표시 조건으로 읽으면 안 된다(`issue-F13`).

### 3.7 목록 바닥

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| "엑셀로 내려받기"(`issue.downloadAsExcel`) | 링크 `a.ybtn.small` | 이슈가 있을 때 | 파일 내려받기. 주소는 현재 요청 주소에서 `pageNum`을 빼고 `format=xls`를 붙인 것 | `GET {base}/issues?...&format=xls` | `Content-Disposition: attachment`, `application/vnd.ms-excel` | READ | `partial-list-wrap.html:66-70`, `K/web/QueryLinks.kt:78-84` |
| 단축키 도움말 단추 | 링크 + 모달 `#helpKeys` | 항상(빈 목록에도) | 모달 열기 | 없음 | 도움말 모달(도움말 담당) | 없음 | `partial-list-wrap.html:44`, `:72`, `T/help/keymap.html` |
| 쪽 나누기 `#pagination[data-total]` | 스크립트가 그림 | 이슈가 있을 때 | 이전/다음 링크, 쪽 번호 입력 `input[name=pageNum]`(Enter로 이동). 링크는 `a[list-page]`에 `hx-boost="true"`를 달아 htmx로 교체 | `GET 현재주소?pageNum=N` | 조각 교체 | READ | `partial-list-wrap.html:74`, `J/esm/Pagination.js:163-178`, `:214-226` |

## 4. 스크립트 동작

- 불러오는 모듈: `twoColumnMode.js`, `showSubtask.js`, `issue.List.js`(`list.html:24-26`), 일괄 변경 막대는 `issue.MassUpdate.js`(`partial-massupdate.html:97`). 공통으로 `common/select2`(select2 4.x와 `esm/ui/Select2.js`), `common/calendar`(vanilla-calendar-pro 3.4.0, `esm/ui/Calendar.js`)를 싣는다(`list.html:21-22`).
- 시작: `$yona.loadModule("issue.List")`(`list.html:30`). `issue.List.js:41-52`가 이벤트를 `.issue-list-wrap`에 위임하고, 쪽 나누기, htmx 초기화, 머리말 클릭, 호버, 두 열 보기, 하위 이슈 펼침을 한 번 한다.
- 위임 이벤트(`issue.List.js:66-77`): `a[data-label-id][data-category-id]` 클릭, `a[list-filter]` 클릭, `a[orderBy]` 클릭, `a[state]` 클릭, `[data-submit]` 클릭, `[data-toggle=issue-checkbox]` change, `[data-search]` change, `[data-toggle=calendar]` change, `form[name=search]`의 `htmx:beforeRequest`.
- htmx 교체 뒤(`htmx:afterSwap`에서 대상이 `[list-container]`일 때) 다시 하는 일(`issue.List.js:227-241`): 요소 재조회, 쪽 나누기 재그림, select2와 달력 재초기화, 머리말 클릭, `#projects` change(조직 이슈 목록용), 호버, 두 열 보기, 하위 이슈 펼침, `_initShowSubtasks`(있을 때만, 238행).
- 요청 헤더: htmx가 `HX-Request: true`를 붙인다. `X-Requested-With`와 `X-PJAX`는 보내지 않는다(`yona.Htmx.js` 머리 주석). 진행 막대(NProgress)는 요청 시작과 끝에 켜고 끈다.
- 이동 기록: 교체마다 주소를 push(`hx-push-url`). 뒤로 가기는 서버에서 새로 불러온다(`historyCacheSize=0`, `refreshOnHistoryMiss=true`).
- 로컬 저장소: `useTwoColumnMode`(`'true'`/`'false'`, `twoColumnMode.js:88-107`), `showSubtasksAlways`(`'true'`/`'false'`, `showSubtask.js:11-26`).
- 지연 로딩과 폴링은 없다.

## 5. 단축키(화면 범위만)

| 키 | 동작 | 등록 위치 | 조건 |
|---|---|---|---|
| `N` | 새 이슈 폼으로 이동(`document.location.href = {base}/issueform`) | `list.html:33-35`(`yona.ShortcutKey.setKeymapLink`) | 폼 입력란(`INPUT`, `TEXTAREA`)에 초점이 없을 때(`ShortcutKey.js` `setKeymapLink`의 `bFormInput` 검사). `menuSetting.issue` 검사는 이 화면에는 없다 |
| `LEFT`, `RIGHT` | 이전, 다음 쪽 주소로 이동(전체 이동이다. 링크 클릭은 htmx 교체인 것과 다르다, `issue-F18`) | `J/esm/Pagination.js:248`, `:274`, `:311-315`(쪽 나누기를 그릴 때마다 `setKeymapLink`) | 이전, 다음 쪽이 있을 때만 주소가 있고, 없으면 등록이 해제된다 |
| `CTRL+A` | 모든 줄 선택 토글(`#check-all` 클릭) | `issue.MassUpdate.js:124-130` | 일괄 변경 막대가 있고 초점이 폼 입력란이 아닐 때. 기본 동작(`preventDefault`) |

- `ShortcutKey`는 키 하나에 처리기를 **하나만** 둔다(`attachHandler`가 `htHandlers[키]`를 덮어씀, `J/esm/ShortcutKey.js` `_setHandler`). 나중에 등록한 쪽이 이긴다. 이 화면에서는 `N`(스크립트 인라인), `LEFT`/`RIGHT`(쪽 나누기), `CTRL+A`(일괄 변경)가 서로 겹치지 않는다. 전역 키(`H`, `B`, `I`, `C`, `M`, `P`, `Q`, `A`, `U`, `F` 등)는 `common` 담당이다.
- `help/keymap`의 도움말 모달은 전역 키 안내 `B`, `I`, `C`, `M`, `P`를 프로젝트 메뉴 설정이 켜져 있을 때만 보이고(`T/help/keymap.html:22-46`), `N`은 `section`에 `issue`가 들어 있으면 항상 보이고, 쪽 이동 키는 `List`가 들어 있을 때, `CTRL+A`는 `issueList`일 때만 보인다(`T/help/keymap.html`).

## 6. 팝업, 모달, 툴팁

- `#helpKeys` 단축키 도움말 모달(도움말 담당). 닫기는 확인 버튼(`data-bs-dismiss`).
- 가중치 화살표, 작성자 아바타 등의 툴팁은 `data-bs-toggle="tooltip"`이다(부트스트랩 5).
- `#two-column-mode-checkbox`와 `.show-subtasks`는 hover 팝오버(`data-bs-content`)다. 두 요소가 같은 id를 쓴다(`issue-F13`).
- 두 열 보기 오프캔버스 `#pageslide`: `bootstrap.Offcanvas`(`backdrop:false`, `scroll:true`, `keyboard:false`), 안에 iframe. 같은 링크를 다시 누르면 닫는다(`twoColumnMode.js:19-64`). 닫는 키는 없다(Esc 닫기 비활성).
- 확인창(confirm)은 이 화면에 없다.

## 7. 요청과 응답 계약

| 요청 | CSRF | 헤더로 갈리는 응답 | 오류와 화면 |
|---|---|---|---|
| `GET {base}/issues` | 읽기라 토큰 없음 | 위 1절 표 | 숫자 아님 400, 프로젝트 없음(익명이면 로그인 화면 403, 로그인이면 오류 화면 403), 읽기 권한 없음 403(익명은 로그인 화면 403), 이슈 목록 자체는 404가 없다 |
| `POST {base}/issues`(일괄 변경 폼) | 폼 제출 직전에 `yona.Csrf.js`가 `_csrf` 숨은 입력을 더한다(`J/common/yona.Csrf.js:61-77`) | 아래 7.1 | 7.1 |

### 7.1 `POST /:user/:project/issues`(일괄 변경, 이슈 보기 오른쪽 창도 같은 주소)

`K/web/issue/IssueMassUpdateController.kt:58-121`. 권한 어노테이션은 `@NullProjectCheck`(프로젝트가 있는지와 메뉴를 끈 프로젝트의 404만 본다)이고 권한은 이슈마다 안에서 검사한다.

| 조건 | 응답 |
|---|---|
| `issues[n].id`가 하나도 없다 | `400` JSON `{"issues":["This field is required"]}`(64) |
| `state`가 `OPEN`, `CLOSED`가 아니다(소문자, `DRAFT`, `MERGED` 등. 빈 값은 통과) | `400` JSON `{"state":["Invalid value"]}`(65-69) |
| `attachingLabelIds`에 같은 배타 분류(`exclusive`)의 라벨이 둘 이상 있다 | `400` JSON `{"attachingLabelIds":["Invalid value"]}`(70-74) |
| `dueDate`가 `yyyy-MM-dd`가 아니다 | `400` JSON `{"dueDate":["Invalid value"]}`(75-81) |
| 없는 이슈 번호가 하나라도 있거나, 경로의 프로젝트에 속하지 않은 이슈가 하나라도 있다 | `404` HTML. 아무것도 바꾸지 않는다(91-93) |
| 초안 이슈 | 건너뛴다(97) |
| 하나도 바꾸지 못했고 거부된 이슈가 있다 | `403` HTML 프로젝트 오류 화면(107) |
| 일부만 허용 | 허용된 것만 바꾸고 정상 응답 |
| `Accept`가 JSON을 선호 | `{}`. `isDueDateChanged=true`이면 `{"isOverDue":bool,"dueDateMsg":문자열|null}`(109-117). 마감일을 지우거나 닫힌 이슈이면 `dueDateMsg`는 `null`이다 |
| 그 밖(브라우저 폼) | `303 Location: Referer`. Referer가 없거나 이 사이트의 주소가 아니면(다른 호스트, 형식이 틀린 값) `{base}/issues`(119-120, `RedirectSupport.localTarget`) |

- 체크한 이슈가 경로의 프로젝트에 속하는지 확인한다(`:93`). 권한은 이슈마다 `UPDATE`로 검사한다(`:103`).
- 다른 프로젝트의 라벨은 `attachingLabelIds`에서 걸러져 붙지 않고, 다른 프로젝트의 마일스톤 번호는 없는 것으로 보아 바꾸지 않는다(`K/domain/issue/IssueMassUpdateService.kt:71, 80`). 같은 배타 분류 라벨을 둘 이상 붙이는 요청은 서버가 400으로 막는다. 화면 스크립트는 여전히 이미 달린 같은 분류 라벨을 `detachingLabelIds`로 함께 보낸다(`issue-F9`).
- 시험: `TEST/IssueMassUpdateTest.kt:127-340`(상태, 담당자, 마일스톤, 라벨, 마감일, 초안, 삭제, 403, 400, 404, JSON 아님, 열림과 닫힘만 266, 배타 라벨 278, 외부 Referer 315, 다른 프로젝트 이슈 325, 다른 프로젝트 라벨 333).

## 8. 권한 행렬(이 화면)

`READ`는 `AccessControl.isAllowed`(`K/access/AccessControl.kt:106`)의 프로젝트 리소스 기본 규칙이다. 규칙 정리는 [issue.md](issue.md)의 권한 행렬을 본다.

| 기능 | 비로그인 | 로그인(비구성원, 공개 프로젝트) | 구성원 | 프로젝트 관리자 | 그룹(조직) 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|
| 목록 보기 | 보인다/허용(사이트가 익명 접근을 막으면 로그인 화면 403) | 허용 | 허용 | 허용 | 허용 | 허용 |
| 새 이슈 단추 | 보인다, 누르면 로그인으로 303 | 보인다, 허용 | 허용 | 허용 | 허용 | 허용 |
| 선택 상자, 일괄 변경 막대 | 안 보임 | 안 보임 | 보임, 이슈별 `UPDATE`면 허용 | 보임, 허용 | 구성원이 아니면 안 보임(`projectMember`는 역할 행 기준), 서버는 허용 | 구성원이 아니면 안 보임, 서버는 허용 |
| 라벨 관리 톱니바퀴 | 안 보임 | 안 보임 | 안 보임(프로젝트 `UPDATE` 권한이 없다) | 보임, 허용 | 보임, 허용 | 보임, 허용 |
| 엑셀 내려받기 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |

## 9. 빈 상태, 오류 상태, 경계 값

| 값 | 정의 위치 |
|---|---|
| 한 쪽 15개 | `IssueListController.kt:212` |
| `itemsPerPage` 최대 45(숫자가 아니면 15) | `IssueListController.kt:213`, `:105-106` |
| `exceptId` 숫자가 아니면 400 | `IssueListController.kt:118-120` |
| htmx 요청 제한 시간 3초 | `partial-list-wrap.html:10` |
| 초안은 목록(`i.isDraft = false`)에 나오지 않고, 첫 쪽에서만 `drafts`가 따로 | `IssueSearchService.kt:199`, `IssueListController.kt:138-139` |
| 작성자, 검색어, 라벨, 담당자, 멘션 조건이 모두 없으면 하위 이슈는 목록에서 뺀다 | `IssueSearchService.kt:267-269` |
| 라벨이 존재하지 않는 번호면 결과 없음 | `IssueSearchService.kt:281-290` |
| 마감일 필터는 그날 23:59:59까지 포함(다음 날 0시 미만) | `IssueSearchService.kt:264`, `nextDay` 301 |
| 빈 상태 문구 `issue.is.empty` | `messages_ko_KR.properties:327` |
| 쪽 번호 입력 상자는 1과 전체 쪽 수 사이로 고쳐진다 | `Pagination.js:`(`isValidInputNum`) |

## 10. 시험 근거

화면별 주요 시험: `TEST/IssueListTest.kt`(제목과 탭 120, 빈 목록 142, 줄 구성 166, 초안 234-255, 빠른 검색 268, 검색 폼 284-301, 구성원에게만 막대 333, 라벨 관리 톱니바퀴 321, 15개씩 354, 엑셀 링크 366, 조각 377-389, 400 423). 클라이언트 스크립트의 시험은 `src/test/js/massupdate.test.mjs`(`issue.MassUpdate.js`의 라벨 목록 이스케이프), `htmx.test.mjs`(영역 교체와 `issue.List.js`), `twocolumn.test.mjs`, `shortcutkey.test.mjs`에 있다.

## 갱신 기록 (2026-11-11 재조사)

- 다시 확인한 템플릿: `list.html`, `partial-list-wrap.html`, `partial-list.html`, `partial-list-quicksearch.html`, `partial-searchform.html`, `partial-massupdate.html`, `partial-select-label.html`(7개). 범위 점검 표(`issue.md` 5절)와 같다.
- 고친 행: 약 14곳(라벨 관리 톱니바퀴 행과 권한 행렬 행, 일괄 변경 응답 표의 상태, 외부 Referer, 경로 밖 이슈 404, 다른 프로젝트 라벨과 마일스톤, 303 이동 행, 시험 근거, 메뉴 꺼짐 404 표기, 단축키 도움말 조건).
- 줄 번호만 고친 곳: 약 20곳(`IssueListController.kt` +1, `IssueMassUpdateController.kt`, `IssueSearchService.kt`의 틀렸던 인용 4곳, 시험 줄 번호).
- 더한 행: 일괄 변경 응답 표의 배타 라벨 400 행, 1절 접근 행의 메뉴 꺼짐 404.
- 없앤 행: 없음.
- 새 발견 사항: 없음(`issue.md`의 F26이 이 화면의 일괄 변경 `POST`에도 해당한다).
