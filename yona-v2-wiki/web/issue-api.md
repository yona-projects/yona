---
title: 이슈 화면 JSON API
type: web
sources: [app/controllers/api/IssueApi.java:319-420, 470-640, 738-1000, 1085-1260, app/controllers/api/ProjectApi.java:277-440, public/javascripts/service/yona.issue.Assginee.js, yona.issue.Sharer.js, yona.detectChange.js, common/yona.Tasklist.js]
updated: 2026-10-23
---

# 이슈 화면 JSON API

구현은 `IssueApiController`, `ApiUserNodes`(사용자 후보 검색과 노드), `IssueJson`(외부 API용 이슈 JSON), `IssueSharerService`, `IssueService.adjustWeight/replaceBody`, `IssueCommentService.replaceContents`이다. 테스트 `IssueApiTest` 22건.

기준 경로는 `/-_-api/v1/owners/:owner/projects/:projectName`이다. JSON을 요구하는 요청에 JSON이 아닌 본문이 오면 `400` `{"message":"Expecting Json data"}`.

| 요청 | 동작 |
|---|---|
| `GET …/issues/:n/assignableUsers?query=&type=` | 검색어가 없으면 담당자가 있을 때 나에게 할당(담당자가 내가 아닐 때), 작성자(나도 담당자도 아닐 때), "담당자 없음"(`loginId` 빈 문자열), 현재 담당자, 담당 가능한 사용자 순. 담당자가 없으면 나에게 할당, 작성자(내가 아닐 때), 담당 가능한 사용자. 검색어가 있으면 활성 사용자 검색(최대 10명, 넘으면 `Content-Range`). JSON을 받지 않으면 `406`. |
| `POST …/issues/:n/assignees` | `{"assignees":["아이디"]}`. 수정 권한이 있을 때만 바꾼다(없으면 바꾸지 않고 `{"issue": 이슈 주소}`만). 빈 목록은 `400` `No assignee`. 없는 아이디나 빈 아이디는 담당자를 비우고 이름은 `None`. 응답 `{"assignee":{loginId,name},"issue":"/o/p/issue/n"}`. 담당자 변경 이벤트는 일괄 변경과 같은 서비스로 남긴다. |
| `GET …/issues/:n/findSharer?query=a,b` | 로그인 아이디가 일치하는 공유자를 추가한 날짜 순으로. |
| `GET …/issues/:n/sharableUsers?query=` | 활성 사용자와 공개 프로젝트(이름 검색). 합계가 10을 넘으면 각각 5개까지, `Content-Range`. 프로젝트 노드는 `loginId`가 프로젝트 번호, `name`이 `소유자/이름`, `type: project`. |
| `POST …/issues/:n/share` | `{"sharer":{"loginId","type":"user|project"},"action":"add|delete"}`. 수정 권한이 없으면 `403` `Permission denied`, 공유자가 없으면 `400` `No sharer`. 프로젝트이면 구성원을 모두 추가하거나 삭제한다. 응답 `{"action":"added|deleted","sharer":이름}`. `ISSUE_SHARER_CHANGED` 이벤트를 중간 값 합치기 없이 남긴다(추가는 `""→아이디`, 삭제는 `아이디→""`). |
| `POST …/issues/:n/upvoteWeight`, `downvoteWeight` | 수정 권한이 있어야 한다(없으면 `403` `Permission denied`). 가중치를 ±1하고 `{"weight"}`. 수정 시각은 바꾸지 않는다. |
| `POST …/issues/:n/detectChange` | 로그인해야 한다(`401` `unauthorized request`). `{issueBodyChecksum, numOfComments}`와 현재 값을 비교해 `{issueBodyChanged, numOfComments, issueBodyChecksum(본문 SHA-1), issueUpdateDate, result:"ok"}`. 받은 댓글 수가 현재보다 적으면 `commentAuthorName`(마지막 댓글 작성자)도 준다. |
| `PATCH …/issues/:n/content` | 체크리스트 갱신. `{content, original}`. 로그인(`401`), 수정 권한(`403` `Forbidden request`), `original`이 저장된 본문과 다르면 `409` `{message:"Already modified by someone.", storedContent}`(비교는 `\r`을 지우고 앞뒤 공백을 뗀 SHA-1). 본문만 바꾸고 이력, 이벤트, 수정 시각은 건드리지 않는다. 댓글 수를 다시 세고 멘션을 맞춘다. 응답은 `IssueJson`. |
| `PATCH /:user/:project/issue/:n/comments/:id` (`PUT …/issues/:n/comments/:id`) | 댓글 체크리스트 갱신. 충돌 검사가 권한 검사보다 먼저이다. 응답 `{"result":{id,contents,createdDate,author:{id,loginId,name}}}`(작성자는 현재 사용자). |

## `IssueJson`

v1.6 `ProjectApi.getResult` 형식이다: `number, id, title, type(ISSUE_POST), author{loginId,name,email}, createdAt/updatedAt(yyyy-MM-dd'T'HH:mm:ssZ), body, owner, projectName`, 담당자가 있으면 `assignees`, `state`, 라벨이 있으면 `labels[{labelName,labelColor,category}]`, 마일스톤이 있으면 `milestoneId, milestoneTitle`, 마감일이 있으면 `dueDate`(`yyyy-MM-dd a hh:mm:ss Z`, 영어), `refUrl`, 첨부가 있으면 `attachments`, 댓글이 있으면 `comments`(한 줄 댓글은 `childComments`).

## 미룬 것

- `POST …/issues/:n/commentNotiReceivers`(댓글 알림 수신자 미리 보기)는 알림 수신자 계산에 의존하므로 4단계에서 옮긴다.
- `POST /-_-api/v1/translation`은 외부 번역 서비스를 부르는 일이라 따로 다룬다.
- 외부 REST API(`getIssue`, `newIssues`, `imports`, `updateIssue`, `updateIssueState`, `updateIssueLabel`, `newIssueComment`)는 화면이 쓰지 않으므로 외부 API 단계에서 옮긴다. `IssueJson`은 그때 재사용한다.
