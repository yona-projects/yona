---
title: 이슈 등록
type: web
sources: [app/controllers/IssueApp.java:311-387, 562-660, app/views/issue/create.scala.html, app/views/issue/partial_assignee.scala.html, app/views/issue/partial_select_subtask.scala.html, app/controllers/api/ProjectApi.java:527-582, app/controllers/api/IssueApi.java:738-800, app/models/TitleHead.java, app/models/Mention.java, app/models/IssueEvent.java]
updated: 2026-10-23
---

# 이슈 등록

구현은 `IssueCreateController`(폼, 등록), `IssueService`(만들기), `IssueFormSupport`(선택지), `IssueFormApiController`(자동 완성), `TitleHeadService`, `MentionService`, `IssueEventService`이다. 화면은 `issue/{create,partial-assignee,partial-select-subtask}.html`이다. 테스트는 `IssueCreateTest` 21건, `IssueServiceTest` 9건, `IssueEventServiceTest` 6건, `TitleHeadServiceTest` 4건, `MentionServiceTest` 6건, `IssueFormApiTest` 8건이다.

## 폼 (`GET /:user/:project/issueform`)

로그인해야 하고(안내와 함께 로그인 화면) 프로젝트에 이슈를 만들 권한이 있어야 한다(공개 프로젝트는 로그인한 누구나, 비공개는 구성원). 쿼리 `parentIssueId`는 부모 이슈를 미리 고르고(이때 본문 템플릿을 쓰지 않는다), `commentId`는 숨은 값 `referCommentId`로 넘어간다.

- 본문의 처음 내용은 저장소 `HEAD`의 `ISSUE_TEMPLATE.md`이다(없거나 읽을 수 없으면 빈 본문).
- 제목은 항상 빈 값으로 시작한다. 폼은 멀티파트(`enctype=multipart/form-data`)이다.
- 하위 이슈 선택(`.subtask-wrap`): 부모나 수정 대상이 있을 때만 펼쳐지고 접혀 있으면 선택 상자는 비활성이다. 프로젝트 선택지는 이 프로젝트 + 즐겨찾기, 최근 방문, 속한 프로젝트(소유자, 이름 순)이고 부모 선택지는 부모가 없는 이슈 최근 300개(미리 고른 부모는 따로 선택된 채 맨 앞)이다.
- 오른쪽: 담당자(담당자를 만들 권한이 있을 때), 마일스톤(마일스톤 메뉴가 켜져 있고 마일스톤을 만들 권한이 있을 때: 열린 마일스톤 선택, 없으면 새 마일스톤 링크), 마감일, 라벨, 숨은 값 `isDraft=false`와 `referCommentId`. 아래 단추는 저장과 초안 저장이다.
- 스크립트: `issue.Write`, `yobi.Mention`, `yonaAssgineeModule`, `yonaTitleHeadModule`. 전역 메뉴로 열었으면 하위 이슈 선택을 자동으로 펼친다.

### 전역 메뉴

- `GET /user/issues/new`: **최근 방문한 프로젝트**의 폼. `commentId`를 주면 댓글 내용 뒤에 `_Originally posted by @작성자 in 주소#comment-번호`를 붙여 본문으로 쓴다(주소는 설정의 스킴 + 요청 호스트 + 컨텍스트). 없는 댓글은 404.
- `GET /user/issues/new/mine`: `inbox`, `_private`, 가장 최근 만든 비공개, 가장 최근 만든 공개 프로젝트 순.
- 갈 프로젝트가 없거나 로그인하지 않았으면 안내 `project.is.empty`와 함께 첫 화면으로 보낸다. v1.6은 같은 주소에서 첫 화면을 그렸지만 v2는 `303` 이동이다(A-42).

## 등록 (`POST /:user/:project/issues/latest`)

| 입력 | 처리 |
|---|---|
| `title` | 필수, 255자까지. 틀리면 `400`으로 폼을 오류와 함께 다시 보여 준다 |
| `dueDate` | `yyyy-MM-dd`. 틀리면 `400`. 그날의 마지막 초로 저장 |
| `targetProjectId` | 다른 프로젝트에 만들 때. 없는 프로젝트면 안내 `error.notfound.project`와 `400`, 만들 수 없으면 `403`. 다른 프로젝트에 만들면 라벨은 붙이지 않는다 |
| `body` | 오지 않으면(폼 본문이 한도를 넘어 사라진 경우) `413` 화면 |
| `parentIssueId`, `assigneeLoginId`, `milestoneId`, `labelIds`, `isDraft` | 부모, 담당자(없는 아이디는 담당자 없음), 마일스톤(-1이면 없음), 이 프로젝트의 라벨만, 초안 여부 |
| `temporaryUploadFiles` | 임시 첨부를 이슈로 옮긴다([editor-components.md](editor-components.md)) |

번호는 프로젝트의 마지막 이슈 번호 + 1이다. 초안도 번호를 쓰고 상태는 `DRAFT`이다. 저장하면 제목 머리말 개수(`title_head`)와 본문 멘션(`mention`)을 기록하고, 초안이 아니면 `IssueCreatedActivity`를 발행한다(알림 서브시스템이 받는다, D-14). 성공하면 `303`으로 `/소유자/프로젝트/issue/번호`(다른 프로젝트에 만들었으면 그 프로젝트)로 보낸다.

**대기**: `referCommentId`로 원 댓글에 "파생 이슈" 댓글을 다는 일은 댓글 등록(3c-3)과 함께 옮긴다.

## 자동 완성 API

- `GET /-_-api/v1/owners/:owner/projects/:name/titleHeads?query=`: 검색어가 들어 있는 제목 머리말 키워드(이름, 빈도)와 프로젝트의 모든 라벨을 `{"result": [...]}`로. JSON을 받지 못하면 `406`.
- `GET /-_-api/v1/owners/:owner/projects/:name/assignableUsers?query=&type=`: 검색어가 없으면 "나에게 할당"(로그인 ID, 이름 `Assign to me`)과 담당할 수 있는 사용자(이름 순), 있으면 활성 사용자 검색 결과(로그인 ID, 이름, 영문 이름에 포함. 최대 10명이고 더 있으면 `Content-Range: items 10/전체`). 비공개 프로젝트의 검색 결과는 프로젝트나 조직의 구성원으로 좁힌다. `type=loginId` 등을 주면 그 항목이 같은 사용자(A-24에 따라 `loginId`, `name`, `email`, `englishName`만).

## 이슈 타임라인 이벤트

`IssueEventService.record`가 `issue_event`를 쓴다. 같은 이슈에서 같은 사용자가 같은 종류를 30초(`yona.issue-event-draft-time`) 안에 연달아 바꾸면 `A→B`, `B→C`를 `A→C`로 합치고 `A→B`, `B→A`는 둘 다 지운다. 라벨, 공유자처럼 중간 값이 의미 있는 이벤트는 되돌릴 때만 지운다(`skipWaypoints=false`).
