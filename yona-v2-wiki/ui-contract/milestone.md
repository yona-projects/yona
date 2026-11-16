---
title: 마일스톤 화면 계약
type: ui-contract
sources:
  - src/main/resources/templates/milestone/list.html
  - src/main/resources/templates/milestone/view.html
  - src/main/resources/templates/milestone/create.html
  - src/main/resources/templates/milestone/edit.html
  - src/main/resources/templates/milestone/partial-status.html
  - src/main/kotlin/dev/yona/web/issue/MilestoneController.kt
  - src/main/kotlin/dev/yona/domain/issue/MilestoneService.kt
  - src/main/resources/static/assets/javascripts/esm/service/milestone.Write.js
  - src/main/resources/static/assets/javascripts/esm/service/milestone.View.js
  - src/main/resources/static/assets/javascripts/esm/ui/Calendar.js
updated: 2026-11-11
---

# 마일스톤 화면 계약

공통 계약(접근 검사 체인, CSRF, 응답 규칙, 반응형, 단축키 구조)은 [project.md](project.md) 1절을 따른다. 이미 있는 글: [web/milestones.md](../web/milestones.md), [domain/milestone-status.md](../domain/milestone-status.md). 줄 번호는 `ui-improvements` 브랜치 기준이다.

## 0. 영역 요약

| 템플릿 | URL (메서드) | 컨트롤러 (`web/issue/MilestoneController.kt`) | 용도 |
|---|---|---|---|
| `milestone/list.html` | `GET /{u}/{p}/milestones?state=&orderBy=&orderDir=` | `milestones` (`:80`) | 목록, 상태 탭, 정렬, 검색 |
| `milestone/view.html` | `GET /{u}/{p}/milestone/{id}?state=` | `milestone` (`:222`) | 상세, 이슈 탭, 일괄 변경, 삭제 모달 |
| `milestone/create.html` | `GET /{u}/{p}/newMilestoneForm`, `POST /{u}/{p}/milestones` | `newMilestoneForm` (`:112`), `newMilestone` (`:121`) | 만들기 |
| `milestone/edit.html` | `GET /{u}/{p}/milestone/{id}/editform`, `POST /{u}/{p}/milestone/{id}/edit` | `editMilestoneForm` (`:142`), `editMilestone` (`:153`) | 고치기 |
| `milestone/partial-status.html` | (조각) 프로젝트 홈의 마일스톤 현황 | `ProjectHomeController` | 제목, 마감일, 진행 막대 |
| (템플릿 없음) | `DELETE /milestone/{id}/delete`, `POST /milestone/{id}/open`, `POST /milestone/{id}/close` | `deleteMilestone` (`:179`), `open` (`:200`), `close` (`:210`) | 삭제, 열기, 닫기 |

모든 화면은 `layout/project-layout`의 `MILESTONE`이고 맨 앞에 `project/menu`(마일스톤 탭 활성)를 끼운다. 클래스에 `@AnonymousCheck`(`:56`)가 있다. 화면의 링크와 폼 `action`, 단추의 `data-request-uri`, 인라인 스크립트의 주소(`N` 단축키, `mentionList`, `sURLLabels`)는 모두 `@{}`로 컨텍스트 경로를 가진다.

## 1. 접근 검사

| 요청 | 어노테이션 (메서드 → 클래스 순으로 실행) | 의미 |
|---|---|---|
| 목록 | `@IsAllowed(READ)`, 클래스 `@AnonymousCheck` | 프로젝트 읽기 |
| 상세 | `@IsAllowed(READ, MILESTONE)` | 마일스톤 READ(프로젝트 읽기 규칙, 마일스톤이 속한 프로젝트 기준) |
| 만들기 폼 | `@AnonymousCheck(requiresLogin, 안내)`, `@IsCreatable(MILESTONE)` | 로그인 필수(303 `/users/loginform?redirectUrl=`), 멤버 이상 |
| 만들기 제출 | `@IsCreatable(MILESTONE)` | 비로그인은 프로젝트 읽기 후 `isProjectResourceCreatable`가 거짓이라 403 `error.forbidden` |
| 고치기 폼 | `@AnonymousCheck(requiresLogin, 안내)`, `@IsAllowed(UPDATE, MILESTONE)` | |
| 고치기 제출, 열기, 닫기 | `@IsAllowed(UPDATE, MILESTONE)` | |
| 삭제 | `@IsAllowed(DELETE, MILESTONE)` | |

**마일스톤 메뉴 검사**: 프로젝트 설정에서 마일스톤 메뉴(`menuSetting.milestone`)를 끈 프로젝트에서는 이 표의 요청 전부(`/milestones`, `/milestone/...`, `/newMilestoneForm`, 곧 경로 셋째 구간이 `milestones`, `milestone`, `newMilestoneForm`인 것)가 위 어노테이션보다 먼저, 프로젝트 읽기를 확인한 직후 404(`error.notfound`, 프로젝트 레이아웃)이다(`ProjectCheckStage.checkMenuEnabled`, `ProjectCheckStage.kt:89-114`). 데이터는 지우지 않으므로 메뉴를 다시 켜면 열린다. **프로젝트 소속 확인**: 권한 검사는 마일스톤을 경로 4번째 구간의 번호로만 찾는다(`ProjectResourceLocator.kt:54`). 숫자가 아니면 404, 없으면 404(`error.notfound`, 프로젝트 레이아웃). 컨트롤러는 거기에 더해 마일스톤이 경로의 프로젝트 소속인지 확인해(`inProject`, `MilestoneController.kt:266`) 상세, 고치기 폼, 고치기 제출, 삭제, 열기, 닫기 여섯 요청 모두 다른 프로젝트의 마일스톤이면 404(`ErrorViews.notFound()`)를 돌려준다. 권한 규칙은 [project.md](project.md) 1.1: 만들기와 UPDATE는 사이트 관리자, 그룹 관리자, 프로젝트 관리자, 멤버, 그룹 구성원이고, DELETE는 그룹 구성원만으로는 안 된다(`AccessControl.kt:221`).

## 2. 목록 (`milestone/list.html`)

### 2.1 개요, 모델
`GET /{u}/{p}/milestones` (`@IsAllowed(READ)`). 파라미터: `state`(기본 `open`, 알 수 없는 값은 열림으로 본다), `orderBy`(기본 `dueDate`, 서비스는 `dueDate`, `title`, `completionRate`를 알고 그 밖은 마감일), `orderDir`(기본 `asc`, `desc`만 내림차순). 모델 `title`("이름 - milestone"), `projectView`, `rows`(각 `status`, `openIssues`, `closedIssues`), `state`, `orderBy`, `orderDir`, `canCreate`(`isProjectResourceCreatable(MILESTONE)`) (`:98-107`). 마감일이 없는 마일스톤은 오름차순이면 먼저, 내림차순이면 나중이다(`MilestoneService.kt:18-30`). 완료율 정렬은 메모리에서 하고 같은 완료율은 번호 순이다.

### 2.2 레이아웃
`project-page-wrap` 안: 위쪽 `tab-wrap`(오른쪽 "새 마일스톤" 단추, 왼쪽 상태 탭 열림/닫힘/전체), 목록이 비면 안내 상자, 아니면 `filter-wrap`(정렬 링크와 검색 상자)와 `ul.milestones`. 각 마일스톤 `li`: 정보줄(이름 링크, 닫힘/전체 건수, 상태 글자, 마감일, 완료 %), 진행 막대, 열린 이슈 목록, 닫힌 이슈 목록. 이슈 링크는 새 탭(`target="_blank"`).

### 2.3 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 새 마일스톤 | 링크 | `canCreate` | 만들기 폼으로 | `GET /newMilestoneForm` | | `list.html:18-20` |
| 상태 탭 열림/닫힘/전체 | 링크 | 항상 | `?state=open|closed|all` | GET | 선택 탭이 `active` | `:22-31` |
| 정렬 링크 (마감일, 완료율) | 링크 | 마일스톤이 2개 이상 | 현재 정렬이면 방향을 뒤집고(`asc`↔`desc`) 화살표 아이콘(`down`=내림차순), 아니면 `asc`로 시작. 링크에 `state` 유지 | GET `?orderBy=&orderDir=&state=` | | `:42-50` |
| 검색 상자 `.textbox` | 입력 | 마일스톤이 2개 이상 | 키를 뗄 때마다 화면의 모든 `.issue-link`를 이슈 줄 글자(번호, 제목, 담당자, 라벨 이름)에 입력 문자열이 들어 있는지로 보이고 숨김(대소문자 무시, 앞뒤 공백 제거). 서버 요청 없음. 돋보기 단추는 `form` 밖이라 동작 없음 | 없음 | | `:51-54`, `:150-155` |
| 마일스톤 이름 | 링크 | 행마다 | `/milestone/{id}` | GET | 상세 | `:65` |
| 이슈 줄 (열린, 닫힌) | 링크 | 이슈가 있을 때 | `/issue/{number}`, 새 탭 | GET | | `:111,125` |
| 이슈의 라벨 | 링크 `href="#"` | 라벨이 있을 때 | 이동 동작 없음(스크립트 없음), 스타일은 `/issue/labels.css`(페이지 위쪽 `<link>`) | | | `:118,134`, `:13` |

행에 보이는 정보: `닫힘수 / (열림수 + 닫힘수)`. 열린 마일스톤이면 `state == all`일 때 상태 글자(`milestone.state.open`), 마감일이 있으면 `마감일 yyyy-MM-dd (N일 남음 등)`이고 기한이 지나면 `over` 클래스. 닫힌 마일스톤은 상태 글자(전체 탭에서)와 마감일만(경과 표시 없음). 이슈가 하나라도 있으면 `completionRate %`. 진행 막대 너비는 `status.completionRate`.

### 2.4 단축키, 스크립트
`menuSetting.milestone`이 켜져 있으면(`:147`) 인라인 스크립트가 `yona.ShortcutKey.setKeymapLink({"N": "/{u}/{p}/newMilestoneForm"})`를 등록한다. 입력란에 초점이 있으면 동작하지 않는다. **`canCreate` 조건이 없어서** 만들 권한이 없는 사용자가 `N`을 누르면 서버가 로그인 303 또는 403을 돌려준다(milestone-F6). 메뉴의 `M` 단축키는 `project/menu`가 등록한다.

### 2.5 빈 상태, 경계
마일스톤이 없으면 `milestone.is.empty`. 마일스톤이 1개면 정렬 링크와 검색 상자가 없다. 이슈는 열림, 닫힘으로 나눠 번호순(`findByMilestoneIdAndStateOrderById`)이며 개수 제한이 없고 초안, 하위 이슈를 구분하지 않는다.

### 2.6 시험
`MilestoneTest` (`src/test/kotlin/dev/yona/web/issue/MilestoneTest.kt`).

## 3. 상세 (`milestone/view.html`)

### 3.1 개요, 모델
`GET /{u}/{p}/milestone/{id}?state=open|closed|all` (`:222`, `@IsAllowed(READ, MILESTONE)`, 다른 프로젝트의 마일스톤은 404). 방문 기록을 남긴다. 모델: `milestone`, `status`(`MilestoneStatus`: 제목, 마감일 글자, 남은 기간, 열림/닫힘 수, 완료율, 지남 여부), `issueState`(알 수 없으면 열림), `issueCounts`(`open`, `closed`, `all` 세 개, `IssueSearchService.count`), `items`(보일 이슈를 `IssueListAssembler`가 만든 것), `massUpdate`, `contentsHtml`(본문이 비면 `null`), `attachmentsJson`, `canDelete`, `canUpdate`. `state`가 `all`이거나 그 밖이면 모든 이슈, `open`/`closed`이면 그 상태만(`:238-243`). 이슈는 번호 내림차순.

### 3.2 레이아웃
`milesion-wrap`(철자 그대로): 제목 줄(이름 링크, 마감일과 남은 기간, 상태 배지), 진행 막대, 본문(`milestone-desc`의 `markdown-wrap`과 첨부 `.attachments`, 본문이 없으면 `empty-content` 빈 상자), 단추 줄, `#issues`(이슈 상태 탭, 일괄 변경 막대와 검색 상자, 이슈 목록). 아래에 삭제 확인 모달 `#deleteConfirm`.

### 3.3 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 제목 링크 | 링크 | 항상 | 자기 자신 | GET | | | `view.html:19` |
| 상태 배지 | 표시 | 항상 | `badge-issue-{open|closed}`, 글자 `milestone.state.*` | | | | `:25` |
| 마감일, 남은 기간 | 표시 | 마감일이 있을 때. 남은 기간은 닫히지 않았을 때만 | | | | | `:21-24` |
| 목록 | 링크 | 항상 | `/milestones` | GET | | | `:39` |
| 삭제 | 링크 | `canDelete` | 모달 `#deleteConfirm` 열기 | 없음 | | DELETE | `:41` |
| 수정 | 링크 | `canUpdate` | `@{/milestone/{id}/editform}` | GET | 수정 폼 | UPDATE | `:44` |
| 닫기 | 단추 | `canUpdate`이고 열림 | `data-request-method="post"`, `data-request-uri=@{/milestone/{id}/close}` → `yona.Request`가 `fetch POST` | `POST /{u}/{p}/milestone/{id}/close` | 서버는 303 상세로 보내고 `fetch`가 따라가 200이므로 스크립트가 `reload`. 실패(403, 404 등)하면 알림을 띄운다(아래 3.4) | UPDATE | `:46`, `MilestoneController.kt:210-218` |
| 다시 열기 | 단추 | `canUpdate`이고 닫힘 | 같은 방식 | `POST /milestone/{id}/open` | 같음 | UPDATE | `:47`, `MilestoneController.kt:200-208` |
| 이슈 탭 열림/닫힘/전체 | 링크 | 항상 | `@{${base}/milestone/{id}?state=…#issues}`(컨텍스트 경로를 가짐), 각 탭에 건수 배지 | GET | 활성은 `issueState.state == tab` | | `:52-58` |
| 이슈 일괄 변경 막대 | 조각 `issue/partial-massupdate :: massupdate` | 항상(구성원 여부 무관, `hide-in-mobile`) | 체크한 이슈의 상태, 담당자, 마일스톤, 라벨 일괄 변경. 모듈 `issue.MassUpdate`(상세는 이슈 계약) | `POST {base}/issues` 계열 | | 이슈 쪽 규칙 | `:63`, `issue/partial-massupdate.html:26-114` |
| 이슈 검색 상자 | 입력 | 항상 | `data-toggle="item-search" data-items="issue-item"`로 표시돼 있으나 **이를 처리하는 스크립트가 없다** | 없음 | | | `:65`, milestone-F2 |
| 이슈 목록 | 조각 `issue/partial-list :: list(items)` | | 이슈 줄(체크박스, 제목, 담당자 등) | | | | `:69` |
| 삭제 모달 "예" | 단추 | 모달 | `data-request-method="delete"`, `data-request-uri=/milestone/{id}/delete` | `DELETE /{u}/{p}/milestone/{id}/delete` | 성공은 204 + `Location: /{u}/{p}/milestones`(`X-Requested-With`가 있을 때)이고 `yona.Request`가 그리로 이동. 마일스톤이 이 프로젝트의 것이 아니면 404. 실패하면 알림을 띄운다(아래 3.4) | DELETE | `:86`, `MilestoneController.kt:179-198` |
| 삭제 모달 "아니오", X | 단추 | 모달 | 닫기(`data-bs-dismiss`) | | | | `:79,87` |

### 3.4 스크립트
- 모듈 `milestone.View`(`milestone.View.js`): `.attachments`마다 `yona.Attachments`로 첨부 목록을 그림(`data-attachments`의 JSON). 열기, 닫기, 삭제 단추(`[data-request-method]` 전부, 삭제 모달의 "예" 포함)마다 `request:error` 수신자를 붙여 실패하면 `$yona.alert(Messages("error.failedTo", 단추 글자, 상태 코드, ""))`를 띄운다(`milestone.View.js:54-61`, 예 `닫기에 실패했습니다.<br>(404 )`). 이 수신자는 `preventDefault()`를 하지 않아 `Request.js`의 기본 알림(403 `error.forbidden`, 404 `error.notfound`, 그 밖 `error.internalServerError`)도 함께 호출된다(milestone-F10). `a.issue-label[data-color]` 클릭을 가로채 `{base}/issues?milestoneId={id}&labelIds={라벨 id}`로 이동하려 하지만(`milestone.View.js:62-65`), 이 화면의 라벨 링크는 `data-color` 속성이 없으면 대상이 아니다(milestone-F5). 이동 주소 `sURLLabels`는 `@{${base + '/issues'}}`로 컨텍스트 경로를 가진다.
- 인라인(`view.html:95-110`): `.title-prefix` 클릭 시 `.textbox` 값을 채우고 `.post-item`을 숨긴 뒤 `li[data-value*='값']`만 보이게 한다(검색 상자 보조). 내 멘션이 든 `.user-link`에 `me` 클래스를 붙인다(`$(".user-link:contains('이름')")`).
- 화면 범위 단축키 없음(전역 메뉴의 키만).

### 3.5 권한 행렬

| 기능 | 비로그인 | 로그인 비멤버 | 멤버 | 프로젝트 관리자 | 그룹 구성원(멤버 아님) | 그룹 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|---|
| 상세 보기 | 공개 프로젝트만 | 공개만 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 수정, 열기, 닫기 단추와 서버 | 숨김, 로그인 필요 | 숨김, 403 | 보임, 허용 | 보임, 허용 | 보임, 허용 | 보임, 허용 | 보임, 허용 |
| 삭제 단추와 서버 | 숨김 | 숨김, 403 | 보임, 허용 | 보임, 허용 | **숨김, 403** | 보임, 허용 | 보임, 허용 |

### 3.6 빈 상태
본문이 없으면 빈 `div.content.empty-content`. 이슈가 없으면 이슈 줄이 없고 탭 건수만 0. 첨부는 본문이 있을 때만 그려진다(`attachmentsJson`이 `null`이면 속성이 비어 JS가 아무것도 하지 않음).

## 4. 만들기, 고치기 (`milestone/create.html`, `milestone/edit.html`)

### 4.1 개요, 모델
`GET /newMilestoneForm` → 입력 빈 값, 상태 열림 기본(`openChecked`). `GET /milestone/{id}/editform` → 현재 값(제목, 본문, 마감일 `yyyy-MM-dd`, 상태). 공통 모델: `title`(`title.newMilestone`/`title.editMilestone`), `projectView`, `input`, `errors`(필드 → 메시지 키 목록), `titleValue`, `contentsValue`, `signedIn`, 고치기는 `milestone`. 폼 `id="milestone-form"`: 만들기는 `POST /{u}/{p}/milestones`(`enctype="multipart/form-data"`), 고치기는 `POST /{u}/{p}/milestone/{id}/edit`(enctype 없음).

### 4.2 레이아웃
`content-wrap frm-wrap` 안: 위 한 줄(제목 입력 `#title`, 250자, `tabindex=1`, 오류면 `error` 클래스와 메시지), 아래 두 단: 왼쪽 `col-lg-9 span-left-pane`(편집기 `common/editor`, 로그인했으면 파일 올리기 `common/file-uploader`, 저장/취소 단추), 오른쪽 `col-lg-3`(상태 라디오, 마감일 입력과 항상 보이는 달력 `#datepicker`).

### 4.3 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 근거 |
|---|---|---|---|---|
| 제목 `#title` | 입력 | 항상 | 열면 초점. Enter를 누르면 기본 동작을 막고 본문 편집기로 초점 이동 | `milestone.Write.js:18-24` |
| 본문 편집기 | 텍스트상자 `contents` | 항상 | `tabindex=2`, `data-editor-mode="content-body"`. `@` 멘션 자동완성(`yona.Mention`, 후보 `GET /{u}/{p}/mentionList`). 임시 저장(`temporarySaveHandler`) | `create.html:36`, `:91-95`, `milestone.Write.js:52-55` |
| 파일 올리기 | 조각 `fileUploader('MILESTONE', id)` | `signedIn` | 첨부를 올리고 본문에 끼움(`yona.Files`, `yona.Attachments`). 만들기는 `null`, 고치기는 마일스톤 id | `create.html:40-42`, `milestone.Write.js:111-122` |
| 저장 | 제출 | 항상 | 제출 전에 `removeCurrentPageTemprarySavedContent()`로 임시 저장을 지우고 클라이언트 검사 | `:67-86` |
| 취소 | 링크 | 항상 | `/milestones` | `create.html:46` |
| 상태 라디오 | 라디오 `state` | 항상 | `OPEN`, `CLOSED`. 만들기는 기본 열림, 고치기는 현재 값(제출한 값으로 되돌려 그림) | `:54-55` |
| 마감일 `#dueDate` | 입력 | 항상 | 직접 입력하거나 달력에서 고름. 입력란을 벗어나면(`blur`) 달력에 반영하고 값을 `YYYY-MM-DD`로 바로잡음(`2027-2-3`, `/` 또는 `.` 구분도 받음) | `milestone.Write.js:100-105`, `esm/ui/Calendar.js` `inline` |
| 달력 `#datepicker` | 항상 보이는 달력(vanilla-calendar-pro) | 항상 | 날짜를 고르면 입력란에 `YYYY-MM-DD` 채움(`change`는 내지 않음). 월, 요일 이름은 `yona.language`를 따름 | `Calendar.js` `inline` |

### 4.4 클라이언트 검사 (`milestone.Write.js:72-86`)
제목이 비면 `$yona.showAlert(milestone.error.title)`, **본문이 비면** `milestone.error.content`, 마감일이 있는데 `\d{4}-\d{2}-\d{2}$` 형식이 아니면 `milestone.error.duedateFormat`. 하나라도 걸리면 제출하지 않는다. 서버는 본문이 비어도 받는다(milestone-F3).

### 4.5 서버 검증과 응답
`MilestoneController.validate`(`:273-296`): 제목 비어 있음 → `title`에 `error.required`; 상태가 비면 `error.required`, 모르는 값이면 `error.invalid`; 마감일이 있는데 `yyyy-MM-dd`(엄격)로 읽히지 않으면 `dueDate`에 `error.invalid.java.util.Date`와 일회성 경고 `milestone.error.duedateFormat`; 제목 중복(만들기는 항상, 고치기는 제목을 바꿨을 때만, 같은 프로젝트에서 정확히 같은 제목)이면 `title`에 `milestone.title.duplicated`와 경고(마감일 경고가 이미 있으면 그쪽이 이긴다). 오류가 있으면 **같은 폼을 200으로 다시 그린다**(상태 코드는 바뀌지 않음, 만들기는 입력값 유지, 고치기는 제목과 본문은 고치기 전의 값이고 상태와 마감일은 보낸 값). 제목 아래에 오류 메시지를 보이고 위쪽에 경고를 일회성으로 보인다. 고치기에서 본문 값을 읽지 못했으면(본문 크기 한도 초과) 413(`requestTextEntityTooLarge`).

성공: 만들기는 `milestoneService.create`(마감일은 그날 23:59:59로 맞춤), 임시 첨부를 이 마일스톤으로 옮기고(`postAttachments.attach`, 안내 포함) **303 `/{u}/{p}/milestone/{id}`**. 고치기는 `update`와 첨부 이동 후 303 상세. 열기, 닫기는 상태만 바꾸고 303 상세.

### 4.6 권한
만들기: 로그인 필수와 `isProjectResourceCreatable`. 고치기: 마일스톤 UPDATE. 폼에서 서버로 가는 값은 `title, contents, dueDate, state` 넷만 읽는다(`MilestoneInput.of`, `:325`).

### 4.7 시험
`MilestoneTest`.

## 5. 프로젝트 홈의 마일스톤 현황 (`milestone/partial-status.html`)

조각 `status(status, project)`. 프로젝트 홈 오른쪽 단에서 `menuSetting.milestone`이 켜져 있고 열린 마일스톤이 있을 때 그려진다(마감일이 가장 이른 열린 마일스톤, `ProjectHomeService.firstOpenMilestone`). 제목 링크(`/{o}/{n}/milestone/{id}`), 마감일(닫히지 않았고 지났으면 `over`, 닫히지 않았을 때만 `(N일 남음 등)`), 진행 막대, `닫힘수 / 전체수`. 동작 요소는 제목 링크뿐이다. 이 조각은 `status.state`를 `?.name()`으로 읽어 널을 허용한다.

## 6. 요청과 응답 계약 요약

| 요청 | 성공 | 실패 |
|---|---|---|
| `GET /milestones` | 200 HTML | 403, 404(마일스톤 메뉴가 꺼진 프로젝트 포함) |
| `GET /milestone/{id}` | 200 HTML | 403, 404(다른 프로젝트의 마일스톤, 메뉴가 꺼진 프로젝트 포함) |
| `POST /milestones` | 303 상세 | 입력 오류 200 폼 재표시, 403 |
| `POST /milestone/{id}/edit` | 303 상세 | 200 폼 재표시, 413, 403, 404(다른 프로젝트의 마일스톤 포함) |
| `POST .../open`, `POST .../close` | 303 상세 | 403, 404(다른 프로젝트의 마일스톤 포함) |
| `DELETE .../delete` | Ajax이면 204 + `Location`, 아니면 303 목록 | 403, 404(다른 프로젝트의 마일스톤 포함) |

모든 비읽기 요청은 CSRF 토큰이 필요하다. `fetch`/폼에는 `yona.Csrf.js`가 싣는다.

## 7. 발견 사항 (milestone-F1 ~ F10)

| 번호 | 내용 | 확인 방법 | 비고 |
|---|---|---|---|
| milestone-F1 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 마일스톤을 번호만으로 찾아 경로의 프로젝트와 달라도 상세, 수정, 열기, 닫기가 통과한다(권한은 그 마일스톤이 속한 프로젝트 기준). 삭제만 프로젝트가 다르면 본문 없는 500이다 | `ProjectResourceLocator.kt:54`, `MilestoneController.kt:179-198` | v1.6 결함 R-12 (2026-11-11 수정: 컨트롤러가 `inProject`로 소속을 확인해 상세, 고치기 폼, 고치기 제출, 삭제, 열기, 닫기 모두 다른 프로젝트의 마일스톤이면 404이다, `MilestoneController.kt:148,164,191,205,215,232,266`) |
| milestone-F2 **[수정됨, A-457]** | 상세 화면의 이슈 검색 상자(`data-toggle="item-search"`)를 처리하는 스크립트가 없다. 처리기였던 `jquery.search.js`가 커밋 `0fd8fcd58`("unreferenced" 정리)에서 지워졌다. 시험은 속성의 존재만 본다(`MilestoneTest.kt:519`). 조직 화면의 같은 상자도 마찬가지(project-F2). 목록 화면의 검색은 인라인 스크립트로 동작한다 | 정적 자원 전체 grep, `git show 0fd8fcd58` | v1.6에서는 동작(`public/javascripts/yona-common.js:174`) |
| milestone-F3 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 클라이언트는 본문이 비면 제출을 막지만 서버는 본문을 필수로 보지 않는다(제목, 상태만 필수) | `milestone.Write.js:75-78`, `MilestoneController.kt:273-278` | |
| milestone-F4 **[검증: 결함 아님]** | 만들기 폼만 `enctype="multipart/form-data"`이고 고치기 폼은 아니다. 마감일 입력란의 `autocomplete="off"`도 만들기에만 있다 | `create.html:19,64`, `edit.html:19,64` | |
| milestone-F5 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `milestone.View.js`의 라벨 클릭 처리기(`a.issue-label[data-color]`)가 붙는 요소가 없다. 템플릿 전체에 `data-color` 속성이 하나도 없어 죽은 코드이다. 목록 화면의 라벨은 `href="#"`이고 처리기가 없다 | `milestone.View.js:44,62-65`, `grep data-color templates` 결과 없음, `list.html:118` | 확인된 사실 |
| milestone-F6 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 목록의 `N` 단축키는 `canCreate`를 보지 않고 등록된다. 권한이 없으면 로그인 303 또는 403으로 간다 | `list.html:147-159` | |
| milestone-F7 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 열기, 닫기, 삭제의 실패(403, 404, 500)는 화면에 아무 표시가 없다(`yona.Request`는 `request:error` 이벤트만 낸다) | `esm/Request.js` | (2026-11-11 수정: `milestone.View.js:54-61`이 단추마다 `request:error`를 듣고 `error.failedTo` 알림을 띄운다. `Request.js`도 기본 알림을 띄운다) |
| milestone-F8 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 검증 오류 재표시는 상태 코드 200이다(프로젝트 설정 등은 400). 제목 중복 검사는 제목을 대소문자까지 정확히 비교한다 | `MilestoneController.kt:133,168`, `MilestoneRepository.existsByProjectIdAndTitle` | |
| milestone-F9 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 목록의 서버 정렬은 `title`도 받지만 화면에는 마감일과 완료율 링크만 있다 | `MilestoneService.kt:31`, `list.html:44` | 서버에만 있는 경로 |
| milestone-F10 [재조사 2026-11-11] | 상세 화면의 열기, 닫기, 삭제 단추는 실패하면 알림이 두 군데에서 나간다. `milestone.View.js:56-61`의 단추별 `request:error` 수신자가 `error.failedTo` 알림(단추 글자와 상태 코드를 넣음, 삭제 모달의 "예"는 글자가 "예"라 "예에 실패했습니다" 꼴)을 띄우고, 이 수신자가 `preventDefault()`를 하지 않아 `Request.js:102-104`도 `error.forbidden`, `error.notfound`, `error.internalServerError` 중 하나를 띄운다. 두 알림이 겹쳐 보이는지 하나로 보이는지는 `$yona.alert`의 구현에 달려 있어 확인하지 못했다 | `milestone.View.js:54-61`, `Request.js:26-28,75-83,102-104` | 추측: 두 번째 알림이 첫째를 덮거나 이어 붙임 |

## 8. 범위 점검

| 템플릿 | 다룬 절 |
|---|---|
| `milestone/list.html` | 2 |
| `milestone/view.html` | 3 |
| `milestone/create.html` | 4 |
| `milestone/edit.html` | 4 |
| `milestone/partial-status.html` | 5 |

## 갱신 기록 (2026-11-11 재조사)

- 다시 확인한 템플릿 5개: `milestone/list.html`, `view.html`, `create.html`, `edit.html`, `partial-status.html`. 스크립트 `milestone.View.js`, `milestone.Write.js`, `esm/Request.js`, 컨트롤러 `MilestoneController`, 접근 검사 `ProjectCheckStage`, `ProjectResourceLocator`도 대조했다.
- 내용을 고친 행: 약 14개(메뉴 꺼짐 404와 프로젝트 소속 404를 접근 검사 절에 더함, 삭제 응답의 500을 404로, 열기, 닫기, 삭제 실패 알림, 요청과 응답 요약표 다섯 줄, 링크의 컨텍스트 경로, 발견 사항 F1, F7).
- 줄 번호만 고친 곳: 약 10개(`MilestoneController`의 `close`, `milestone`, `validate`, `MilestoneInput.of`, `AccessControl.kt`, `ProjectResourceLocator.kt`, `milestone.View.js`).
- 더한 행: 없음(발견 사항 아래). 없앤 행: 없음.
- 새 발견 사항: milestone-F10(열기, 닫기, 삭제 실패에 알림 두 개).
