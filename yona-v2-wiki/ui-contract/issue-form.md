---
title: 이슈 작성과 수정 폼 화면 계약
type: ui-contract
sources:
  - src/main/resources/templates/issue/create.html
  - src/main/resources/templates/issue/edit.html
  - src/main/resources/templates/issue/partial-select-subtask.html
  - src/main/resources/templates/issue/partial-select-label.html
  - src/main/resources/templates/issue/partial-assignee.html
  - src/main/kotlin/dev/yona/web/issue/IssueCreateController.kt
  - src/main/kotlin/dev/yona/web/issue/IssueEditController.kt
  - src/main/kotlin/dev/yona/web/issue/IssueFormSupport.kt
  - src/main/kotlin/dev/yona/web/api/IssueFormApiController.kt
  - src/main/resources/static/assets/javascripts/esm/service/issue.Write.js
  - src/main/resources/static/assets/javascripts/esm/Subtask.js
updated: 2026-11-11
---

# 이슈 작성, 수정 폼 계약

공통 계약, 권한 행렬, 발견 사항(`issue-F1`...)은 [issue.md](issue.md)에 있다. 서버 서술은 [web/issue-create.md](../web/issue-create.md), [web/issue-edit.md](../web/issue-edit.md)에 있다. 경로 약어: `T/` = `src/main/resources/templates/`, `K/` = `src/main/kotlin/dev/yona/`, `J/` = `src/main/resources/static/assets/javascripts/`, `TEST/` = `src/test/kotlin/dev/yona/web/issue/`.

## 1. 화면 개요

| 화면 | URL | 컨트롤러 | 템플릿 | 어노테이션 |
|---|---|---|---|---|
| 작성 폼 | `GET /:user/:project/issueform?parentIssueId=&commentId=` | `IssueCreateController.newIssueForm`(`:62-75`) | `issue/create` | `@AnonymousCheck(requiresLogin, displaysFlashMessage)`, `@IsCreatable(ISSUE_POST)`(`:63-64`) |
| 작성 폼(최근 방문 프로젝트) | `GET /user/issues/new?commentId=` | `newDirectIssueForm`(`:78-91`) | `issue/create`(`fromGlobalMenu=true`) | 클래스 `@AnonymousCheck`만 |
| 작성 폼(내 프로젝트) | `GET /user/issues/new/mine` | `newDirectMyIssueForm`(`:94-106`) | 같음 | 같음 |
| 작성 | `POST /:user/:project/issues/latest`(멀티파트) | `newIssue`(`:108-159`) | 오류면 `issue/create`를 400으로 | `@IsCreatable(ISSUE_POST)`(109) |
| 수정 폼 | `GET /:user/:project/issue/:number/editform` | `IssueEditController.editIssueForm`(`:66-77`) | `issue/edit` | `@NullProjectCheck`(67) |
| 수정 | `POST /:user/:project/issue/:number/edit`(멀티파트) | `editIssue`(`:79-120`) | 오류면 `issue/edit`를 400으로 | `@NullProjectCheck`(80) |

- 레이아웃: `project-layout(제목, projectView, 'ISSUE', createPage|editPage)` + `project/menu :: projectMenu(..., 'main-menu-only')`(`create.html:12-14`, `edit.html:14-16`). 제목은 모델 `title`(메시지 키): 작성 `title.newIssue`, 검증 오류 `error.validation`, 수정 `title.editIssue`.
- 작성 폼 모델: `title`, `bodyValue`, `commentId`, `errors`, `fromGlobalMenu`, `projectView`, `canAssign`, `canMilestone`, `openMilestones`, `closedMilestones`, `hasMilestones`, `labelGroups`, `selectedLabelIds`, `movableProjects`, `parentOptions`, `selectedParent`, `showSubtask`, `logoUrl`(`IssueCreateController.kt:206-216`, `IssueFormSupport.kt:45-69`). 수정 폼은 그 위에 `issueNumber`, `issueId`, `draft`, `titleValue`, `bodyValue`, `authorId`, `dueDateValue`, `stateName`, `assigneeLoginId`, `milestoneId`, `hasChild`, `canUpdate`, `canUpdateState`, `canUpdateAssignee`, `canUpdateMilestone`, `authoredByViewer`를 더한다(`IssueEditController.kt:131-156`).
- 작성 폼의 본문 초기값: 저장소의 `ISSUE_TEMPLATE.md`(비어 있지 않으면). `parentIssueId`를 주면 템플릿을 쓰지 않고 빈 본문(`IssueCreateController.kt:212`). `commentId`를 주면 `{댓글 본문}\n\n_Originally posted by @작성자 in {댓글 주소}`(읽을 수 없는 프로젝트의 댓글이면 404, `:181-193`). 작성한 이슈가 초안이 아니면 원 댓글의 이슈에 `issue.derived: 새 이슈 주소` 한 줄 댓글을 단다(`:167-179`).
- `/user/issues/new*`: 방문한 프로젝트가 없으면(비로그인 포함) 경고 `project.is.empty`를 남기고 같은 주소에서 첫 화면(`forward:/`)을 그린다(`:199-204`). `fromGlobalMenu=true`이면 스크립트가 `.subtask-message`를 자동 클릭해 하위 이슈/프로젝트 선택을 펼친다(`create.html:123-125`).

## 2. 레이아웃 계약

- `.page-wrap-outer > .project-page-wrap > .content-wrap.frm-wrap > form#issue-form[multipart]`(`create.html:15-18`).
- 위 줄 `col-lg-12`: (수정은 `dt`에 번호 `#N` 또는 `Draft` 표시) 제목 `title-input`(`col-lg-11`) + "옵션"(`.subtask-message`, `issue.option`, `col-lg-1`), 제목 오류(`.message.w-100`), 하위 이슈 선택 `div.subtask-wrap`(`create.html:21-34`).
- 아래 줄: 왼쪽 `col-lg-9.span-left-pane`(에디터, 파일 올리기, 단추 줄 `.actrow.right-txt`), 오른쪽 `col-lg-3.span-hard-wrap.right-menu`(상태, 담당자, 마일스톤, 마감일, 라벨, 숨은 입력).
- 모바일(720px 이하)에서는 `col-lg`가 세로로 쌓이고 `.span-left-pane`은 100%지만(`S/_responsive.scss:261`), 이 폼에는 `hide-in-mobile`이 없으므로 모든 항목이 그대로 있다(`.span-right-pane`이 아니라 `right-menu`이기 때문이다).
- 상태별 차이:
  - 작성: 하위 이슈 선택은 `showSubtask`(= `parentIssueId != null`)일 때만 펼침. 아니면 접혀 있고 두 select는 `disabled`.
  - 수정: `showSubtask`는 항상 참(수정 중이므로 `currentIssueId != null`, `IssueFormSupport.kt:67`), 펼쳐져 있고 select 활성.
  - 담당자 `dl`: 작성은 `canAssign`(프로젝트 구성원 이상), 수정은 `canUpdateAssignee`.
  - 마일스톤 `dl`: 작성은 `canMilestone`(마일스톤 메뉴 켬 + 구성원 이상), 수정은 메뉴 켬 + `canUpdateMilestone`. 열린 마일스톤이 없으면(작성) 또는 마일스톤이 하나도 없으면(수정) "새 마일스톤" 링크.
  - 수정 단추 줄: 초안이면 `게시`와 `초안 저장`, 초안이 아니면 `저장`(둘 다 `canUpdate`일 때만), `취소`는 항상.

## 3. 동작 요소 표

### 3.1 작성 폼(`create.html`)

| 요소 | 종류 | 조건 | 동작 | 요청(바디 필드) | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 제목 `title-input#title[name=title]` | 커스텀 요소(`lib/title-input/title-input.min.js`) | 항상 | 자동 초점. Enter를 누르면 본문 textarea로 초점 이동(전송하지 않음). 입력 중 제목 머리말 자동 완성: `src`로 `GET /{owner}/{name}/titleHeads?query=` 호출, 후보 중 라벨을 고르면 라벨 선택 상자에서 그 라벨을 선택(배타 분류면 같은 분류의 앞 선택 해제) 후 `Label: 이름` 알림 3초 | `title=`(`maxlength=250`) | | 제출 때 서버가 255자 초과를 `error.maxLength`로 거부(`issue-F11`) | `create.html:25`, `J/esm/service/issue.Write.js:25-32`, `J/esm/TitleHeadAutoCompletion.js:29-87`, `K/web/api/IssueFormApiController.kt:43-62` |
| "옵션" `.subtask-message` | 단추형 글자 | 항상 | 클릭하면 하위 이슈 영역 토글, 켜지면 `option-on` 클래스, 두 select의 `disabled`를 뒤집는다(접히면 비활성이라 폼에 실리지 않음) | | | | `create.html:27`, `J/esm/Subtask.js:5-17` |
| 대상 프로젝트 `select#targetProjectId[name=targetProjectId]` | select2(`data-format=projects`) | 하위 이슈 영역이 펼쳐졌을 때 활성. 옵션: 현재 프로젝트, 그 밖에 이슈를 만들 수 있는 프로젝트(`movableProjects`)(소유자, 로고) | 현재 프로젝트가 아닌 것을 고르면 부모 이슈 select를 첫 빈 값으로 되돌리고 비활성, select2 컨테이너를 숨기고 `Issue will be moved or written to '이름'` 알림 4초. 현재 프로젝트로 되돌리면 다시 활성 | `targetProjectId=`(활성일 때) | | 대상 프로젝트에 `ISSUE_POST` 생성 권한 필요, 없으면 403(`IssueCreateController.kt:138`) | `partial-select-subtask.html:14-19`, `Subtask.js:19-35` |
| 부모 이슈 `select#parentId[name=parentIssueId]` | select2(`data-format=issues`) | 위와 같음. 옵션: 빈 값(안내 `issue.subtask.select`), 미리 고른 부모(`selectedParent`), 그 프로젝트의 부모 없는 이슈 최대 300개(최신순)(`#번호. 제목`) | 선택 | `parentIssueId=` | 하위 이슈로 만들어진다 | 없음(서버가 존재하는 이슈면 받는다) | `partial-select-subtask.html:21-29`, `IssueFormSupport.kt:49-52, 72`, `IssueListService.kt:135` |
| 본문 에디터 `textarea[name=body]#editor-body-*` | `common/editor`(탭: 편집, 미리보기, 체크리스트 추가 단추, 서식 도구) | 항상. `tabindex=2` | 임시 저장(5초), `beforeunload` 경고(`issue.error.beforeunload`, 본문이 비어 있지 않을 때만, 한 번 초점을 받은 뒤부터), `@`와 `#` 자동 완성 | `body=` | | | `create.html:41`, `issue.Write.js:63-83`, `yona.Mention({url: {base}/mentionList})`(`create.html:117-121`) |
| 파일 올리기 | `common/file-uploader('ISSUE_POST', null)` | 로그인 사용자(`!currentUser.anonymous`) | 업로드 영역 `#upload`. 올린 파일은 임시 첨부 | `POST /files`, 폼의 `temporaryUploadFiles` | 작성 때 이슈 첨부로 옮겨진다 | 로그인 | `create.html:45-47` |
| 담당자 `select#assignee[name=assigneeLoginId]` | select2(`yonaAssgineeModule`, 업데이트 주소 없음) | `canAssign` | 열면 `GET /{owner}/{name}/assignableUsers`(검색어 없음: "나에게 할당" + 담당 가능 사용자들, 검색어 있음: 활성 사용자 10명). 선택해도 저장 요청은 없다 | `assigneeLoginId=` | | `@IsAllowed(READ)`, JSON이 아니면 406 | `create.html:56-61`, `partial-assignee.html`, `J/esm/service/issue.Assginee.js`, `K/web/api/IssueFormApiController.kt:69-96` |
| 마일스톤 `select#milestoneId[name=milestoneId]` | select2(`data-format=milestone`) | `canMilestone`이고 열린 마일스톤이 있을 때. 없으면 "새 마일스톤" 링크(`target=_blank`) | 선택 | `milestoneId=`(`-1`은 없음) | | 서버는 마일스톤이 이슈의 프로젝트 것인지 확인하지 않는다(`issue-F11`) | `create.html:63-72` |
| 마감일 `input#issueDueDate[name=dueDate][data-toggle=calendar]` | 달력 | 항상 | 날짜 선택. 제출 때 `isValidDate` 검사 | `dueDate=yyyy-MM-dd` | | | `create.html:74-82` |
| 라벨 `select#labelIds[multiple]` | select2(`data-format=issuelabel`, `data-close-on-select=false`) | 프로젝트에 라벨이 있을 때. `[편집]` 링크는 `view.manager`일 때 | 배타 분류의 라벨을 고르면 같은 분류의 앞 선택을 해제 | `labelIds=`(여러 번) | 대상 프로젝트가 다르면 서버가 라벨을 버린다(`IssueCreateController.kt:145-147`) | | `create.html:84`, `partial-select-label.html` |
| 숨은 `referCommentId` | 숨은 입력 | 항상(값 = `commentId`) | | `referCommentId=` | 초안이 아니면 원 댓글에 파생 이슈 댓글 | | `create.html:85` |
| 숨은 `isDraft` | 숨은 입력 | 항상, 초기값 `false` | "초안 저장"이 `true`로 바꾼다 | `isDraft=true|false` | | | `create.html:86` |
| "저장" `#button-save` | 제출 단추 | 항상 | 클릭하면 3초간 `disabled`, 폼 제출(`submit` 핸들러가 제목 비었는지(`issue.error.emptyTitle` 경고창), 마감일 형식(`issue.error.invalid.duedate`)을 검사하고 임시 저장 내용 삭제) | `POST {base}/issues/latest` 멀티파트: `title`, `body`, `parentIssueId`, `targetProjectId`, `assigneeLoginId`, `milestoneId`, `dueDate`, `labelIds`, `referCommentId`, `isDraft=false`, `filePath`, `temporaryUploadFiles` | 성공 `303 → {대상 프로젝트}/issue/{번호}`(첨부 처리 결과 flash). 검증 오류는 `400`으로 이 폼을 다시 그린다 | `@IsCreatable(ISSUE_POST)`: 비로그인 403, 공개 프로젝트의 로그인 사용자 허용, 비공개 프로젝트의 비구성원 403 | `create.html:50, 138-146`, `issue.Write.js:110-133`, `IssueCreateController.kt:108-159` |
| "초안으로 저장" `#draft-save-btn` | 단추 | 항상(툴팁 `button.draft.save.description`, 위쪽) | 3초 `disabled`, `isDraft=true`로 바꾸고 제출 | 위와 같음 | 초안 상태로 저장, 알림과 활동 이벤트를 만들지 않는다 | 같음 | `create.html:51, 148-163` |
| "취소" | 링크 | 항상 | `javascript:history.back()` | | | | `create.html:52` |

서버 검사 순서(`IssueCreateController.kt:117-159`): 프로젝트 없음 404, 제목 비었음 `error.required`, 255자 초과 `error.maxLength`, 마감일 형식 `error.invalid.java.util.Date` → 오류가 있으면 400으로 폼을 다시 그린다(제목 오류만 화면에 나온다, `issue-F14`). 그다음 `targetProjectId`가 숫자가 아니거나 없는 프로젝트면 경고 `error.notfound.project`와 400 폼, 생성 권한이 없으면 403. `body` 파라미터가 없으면 `413` 화면(멀티파트에서는 본문 크기 한도가 적용되지 않아 파라미터 자체가 없을 때만 해당).

### 3.2 수정 폼(`edit.html`)

작성 폼과 같은 요소는 같다(제목, 하위 이슈, 에디터, 파일, 담당자, 마감일, 라벨). 다른 점:

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 숨은 `authorId`, `isDraft`(false), `isPublish`(false) | 숨은 입력 | 항상 | `isDraft`, `isPublish`는 아래 단추가 `true`로 바꾼다 | `authorId=`(서버는 읽지 않는다), `isDraft=`, `isPublish=` | | | `edit.html:21-23`, 서버 `IssueEditController.kt:122-130` |
| 상태 `#state[data-name=state]` | 드롭다운 | `canUpdateState`. 항목 `OPEN`, `CLOSED`(현재 상태에 `active`, `data-selected`) | **동작한다**(정정, 브라우저 확인). `common/scripts.html:77-82`가 `.btn-group[data-name]`(`data-activate="manual"`이 아닌 것)을 `yona.ui.Dropdown`으로 초기화해 숨은 입력 `state`와 라벨을 만든다. 항목을 누르면 `state=OPEN\|CLOSED`가 폼에 실린다 | `state=OPEN\|CLOSED` | 서버는 `state`가 없으면 현재 상태를 유지한다 | 이슈 `UPDATE` | `edit.html:74-87`, `common/scripts.html:77-82`, `IssueEditController.kt:122`, `K/domain/issue/IssueService.kt:285-290` |
| 마일스톤 | select | 메뉴 켬 + `canUpdateMilestone`. 열린/닫힌 optgroup 모두 | | `milestoneId=`(보내지 않으면 **마일스톤 없음**으로 지워진다) | | | `edit.html:96-110` |
| 마감일 | 달력 | 항상, 값 `dueDateValue` | | `dueDate=` | | | `edit.html:112-120` |
| "알림 메일" 체크 `#notificationMail[name=notificationMail][value=yes]` | 체크박스 | `!draft and authoredByViewer`(작성자 본인이 초안이 아닌 이슈를 고칠 때), 기본 체크 | 체크되면 작성자가 고쳐도 변경 이벤트와 알림을 남긴다 | `notificationMail=yes` | | | `edit.html:58-63`, `IssueEditController.kt:122-130` |
| "저장" `#button-save` | 제출 단추 | `canUpdate and !draft` | 3초 `disabled` 후 제출 | `POST {base}/issue/{n}/edit` | `303 → {이슈 위치}/issue/{번호}`(옮겼으면 새 위치) | 이슈 `UPDATE` | `edit.html:67-68, 198-206` |
| "초안 게시" `#button-draft-publish` | 제출 단추 | `canUpdate and draft` | 기본 제출을 막고 `confirm(button.draft.publish.description)`. 확인하면 3초 `disabled`, `isPublish=true`, 제출. 툴팁(위 왼쪽) | 위와 같음 + `isPublish=true` | 새 번호, 열림 상태, 이력 비움, 생성 활동 발행 | 같음 | `edit.html:64-66, 208-220` |
| "초안 저장" `#draft-save-btn` | 단추 | `canUpdate and draft` | `isDraft=true`로 제출 | 같음 + `isDraft=true` | 초안 상태 유지 | 같음 | `edit.html:66, 222-231` |
| 임시 저장 지우기 `.editor-clear-temporary-button` | 단추(`common/editor` 안 `#button-clear-temporary`) | `localStorage[location.pathname]`이 있을 때만 `.editor-clear-temporary`를 보인다(`edit.html:171-173`) | 저장된 초안을 지우고 페이지 새로고침, 툴팁 `button.clear.temporary.description` | 없음 | | | `edit.html:171-184` |
| "취소" | 링크 | 항상 | `history.back()` | | | | `edit.html:69` |

수정 서버 검사 순서(`IssueEditController.kt:79-120`): 프로젝트 없음 404(`@NullProjectCheck`), 이슈 없음 404 프로젝트 오류 화면, 제목, 마감일 오류 400 폼(제목, 본문은 고치기 전 값), 수정 권한 없음 403, `targetProjectId`가 없는 프로젝트면 400 폼과 `error.notfound.project` 경고, 생성 권한 없음 403, `body` 없음 413, 같은 분류의 라벨 둘 이상이면 `400 text/plain "This category does not allow an issue to have two or more labels of the category"`. 수정 폼 열기: 이슈가 없으면 **404 상태에 403 모양**의 화면(`:73-74`), 읽기 권한 없음 403(75).

### 3.3 `title-input`, 에디터 등 공통 부품

에디터(`common/editor`), 파일 올리기(`common/file-uploader`), 서식 도구, 임시 저장은 `common` 담당이 정리한다. 이 폼이 에디터에 주는 값: 이름 `body`, `tabindex=2`, 모드 `content-body`(`create.html:41`).

## 4. 스크립트 동작

- 불러오는 모듈: `Subtask.js`, `issue.Assginee.js`, `title-input.min.js`, `TitleHeadAutoCompletion.js`, `issue.Write.js`, 공통 `common/markdown`, `common/select2`, `common/calendar`, `labels.css`(`GET {base}/issue/labels.css`)(`create.html:95-104`).
- `$yona.loadModule("issue.Write", {sMode, sIssueFormURL, sIssueListURL, elTextarea, elMilestoneRefresh, elDueDate})`(`create.html:108-115`).
  - `$("form").submit(_onSubmitForm)`(`issue.Write.js:64`): 제목을 trim해서 비었으면 알림창 `issue.error.emptyTitle` 후 제출 중단, 마감일이 `isValidDate`가 아니면 `issue.error.invalid.duedate` 알림 3초 후 중단, 통과하면 `beforeunload` 해제와 임시 저장 삭제.
  - `_onReloadMilestone`(`:85-92`, 마일스톤 새로고침)은 `.yobicon-refresh`를 누를 때 도는데 두 템플릿에 그 요소가 없어 도달하지 않는다(`issue-F2`). `#options` 요소도 없다.
- 제출 단추는 `click`에서 3초간 `disabled`로 만들고 `form.submit()`을 부른다(`create.html:138-146`). 그래서 `submit` 핸들러의 검증이 실행되고, `<button type="submit">`의 기본 제출과 함께 두 번 제출될 수 있는 구조다(`type="submit"`이고 핸들러가 `preventDefault`를 하지 않는다). 실제 중복 여부는 확인하지 못했다(추측).
- 담당자 모듈 `yonaAssgineeModule(/{owner}/{name}/assignableUsers, "", 메시지)`(`create.html:127-131`): 갱신 주소가 비어 있어 선택해도 저장 호출이 없다.
- 로컬 저장소: `localStorage[location.pathname]`에 본문 초안(5초 입력 멈춤 뒤), 수정 폼은 같은 키를 읽어 `textarea`를 채운다(`temporaryUploadFiles`와 별개).
- 툴팁: `tippy`(`#draft-save-btn` 등)로 `button.draft.save.description`, `button.draft.publish.description`을 `maxWidth: 'none'`으로 보인다.

## 5. 단축키

이 두 화면에 화면 범위 단축키 등록은 없다(`setKeymapLink` 호출 없음). 에디터 안의 입력 단축키와 전역 키는 `common` 담당이다. 폼 입력란에 초점이 있으면 전역 이동 키는 동작하지 않는다(`ShortcutKey.js`의 `bFormInput`). 제목 입력란의 Enter는 본문으로 초점을 옮긴다.

## 6. 팝업과 확인창

- `confirm()`(브라우저 기본): 초안 게시 확인(`edit.html:210`). 취소하면 `false` 반환(제출 안 함).
- `$yona.alert`: 제목 비었음. `$yona.notify`: 날짜 형식, 하위 이슈 프로젝트 이동 안내, 라벨 자동 선택 안내.
- 툴팁(`tippy`): 초안 저장, 게시, 임시 저장 지우기.
- 모달은 없다.

## 7. 요청과 응답 계약

| 요청 | 형태 | CSRF | 성공 | 오류 |
|---|---|---|---|---|
| `POST .../issues/latest` | 멀티파트 폼(브라우저 폼 제출) | 제출 직전 `_csrf` 숨은 입력(`yona.Csrf.js`) | `303 Location` | 400 폼(검증), 403(권한, 대상 프로젝트), 404(프로젝트), 413(본문 없음) |
| `POST .../issue/{n}/edit` | 멀티파트 | 같음 | `303` | 400 폼, 400 평문(라벨 분류), 403, 404, 413 |
| `GET .../titleHeads?query=` | `title-input`이 fetch | | `{"result":[{name,frequency,category,searchText,...},...]}`: 머리말 후보 + 프로젝트의 모든 라벨(`id`, `labelColor`, `isExclusive`, `categoryId`) | JSON 아니면 406 |
| `GET .../assignableUsers?query=` | select2 | | 사용자 노드 배열 | 406 |
| `GET .../mentionList?...` | 멘션 | | `{result:[...]}` | 403, 404 |
| `POST /files` | 업로더 | 헤더 토큰 | 임시 첨부 JSON | 첨부 담당 |

`Accept`와 `HX-Request`에 따라 달라지는 응답은 이 화면에 없다(htmx를 쓰지 않는다).

## 8. 권한 행렬

| 기능 | 비로그인 | 로그인 비구성원(공개) | 구성원 | 프로젝트 관리자 | 조직 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|
| 작성 폼 열기 | 로그인으로 303 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 작성 폼에서 담당자, 마일스톤 칸 | 없음 | 안 보임(`ISSUE_ASSIGNEE`, `ISSUE_MILESTONE`은 공개 프로젝트 비구성원 생성 대상이 아님, `AccessControl.kt:304-307`) | 보임 | 보임 | 보임 | 보임 |
| 작성 | 403 | 허용(공개), 비공개는 403 | 허용 | 허용 | 허용 | 허용 |
| 수정 폼 열기 | 읽기 권한이면 열림("원본 보기") | 열림(저장 단추 없음) | 열림, 저장 가능 | 가능 | 가능 | 가능 |
| 수정 저장 | 403 | 작성자 또는 담당자만 | 가능 | 가능 | 가능 | 가능 |
| 초안 게시 | 403 | 작성자만(작성자 권한) | 작성자만 | 가능 | 가능 | 가능 |
| 다른 프로젝트로 이동(대상 프로젝트 선택) | 불가 | 대상 프로젝트에 이슈 생성 권한이 있을 때 | 같음 | 같음 | 같음 | 같음 |

초안은 작성자만 볼 수 있다(`IssueViewController.kt:82`). 수정 폼 열기에는 초안 검사가 없고 읽기 권한만 본다(`IssueEditController.kt:75`). 다른 사람의 초안 수정 폼을 읽기 권한만으로 열 수 있는지는 `isAllowed(READ)`가 초안을 따로 걸러내지 않는 한 열린다(코드상 사실: `editIssueForm`에 초안 검사 없음. 시험 없음).

## 9. 경계 값

| 값 | 화면 | 서버 |
|---|---|---|
| 제목 길이 | `maxlength=250`(`create.html:25`, `edit.html:34`) | 255 초과 거부(`IssueCreateController.kt:231`, `IssueEditController.kt:163`) |
| 부모 이슈 후보 | | 최대 300개, 부모가 없는 이슈, 상태와 초안 구분 없음(`IssueFormSupport.kt:72`, `IssueListService.kt:135-137`) |
| 마감일 형식 | `YYYY-MM-DD` | `yyyy-MM-dd` 엄격(비관용), 틀리면 `error.invalid.java.util.Date` |
| 마감일 저장 | | 그날의 마지막 초(`MilestoneService.lastSecondOfDay`) |
| 버튼 재활성화 | 3초 | |
| 임시 저장 | 입력 멈춤 5초, 보관 1시간 | |
| 본문 크기 | | 멀티파트에는 한도 없음(`TextBodyLimitFilter`는 urlencoded만) |

## 10. 시험 근거

`TEST/IssueCreateTest.kt`(폼 116-240, 등록 277-412, 직접 만들기 412-423), `TEST/IssueEditTest.kt`(폼 128-190, 수정 203-362), `src/test/kotlin/dev/yona/web/api/IssueFormApiTest.kt`(제목 머리말 79-116, 담당자 후보 126-186). `issue.Write.js`, `Subtask.js`의 단위 시험은 찾지 못했다. 상태 드롭다운 동작을 확인하는 시험은 없다(`IssueEditTest.kt:149`는 현재 값이 채워지는지만 본다).
