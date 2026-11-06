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
| `GET …/issues/:n/assignableUsers?query=&type=` | 로그인하지 않은 사용자도 "나에게 할당" 항목이 `loginId` 빈 문자열로 들어간다(v1.6 `UserApp.currentUser()`가 익명을 돌려준다, 2026-10-26 정정). 검색어가 없으면 담당자가 있을 때 나에게 할당(담당자가 내가 아닐 때), 작성자(나도 담당자도 아닐 때), "담당자 없음"(`loginId` 빈 문자열), 현재 담당자, 담당 가능한 사용자 순. 담당자가 없으면 나에게 할당, 작성자(내가 아닐 때), 담당 가능한 사용자. 검색어가 있으면 활성 사용자 검색(최대 10명, 넘으면 `Content-Range`). JSON을 받지 않으면 `406`. |
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

## 다른 도구에서 글을 가져올 때 (작성자 보존, R-98)

가져오기 API(`POST …/projects/{프로젝트}/issues`, 이슈와 게시글의 댓글 만들기)는 토큰을 가진 한 사람이 다른 사람의 이름으로 글을 올릴 수 있다. 본문의 `author.email`로 작성자를 찾는다. 대표 이메일, 보조 이메일, 대소문자를 무시한 대표 이메일 순서이다.

- **이메일이 Yona의 사용자와 맞으면** 그 사용자가 작성자이다.
- **맞는 사용자가 없으면** 작성자를 "존재하지 않는 사용자"라는 익명 사용자(번호 -1)로 기록한다. 사용자를 새로 만들지 않고 글은 정상으로 만들어진다. 원래 작성자의 이메일은 어디에도 남지 않는다. v1.6도 같고 그대로 유지하기로 했다(A-376).
- **`author`를 주지 않으면** 요청한 토큰의 사용자가 작성자이다.

작성자를 보존하려면 글을 올리기 **전에** 사이트 관리자의 토큰으로 사용자를 만든다. `POST /-_-api/v1/users`에 `{"users": [{"loginId": "hong", "name": "홍길동", "email": "hong@corp.com"}]}`를 보내면 사용자마다 `201`, 이미 있는 이메일이나 아이디는 `409`, 허용하지 않는 메일 도메인은 `403`으로 알려 준다. 비밀번호는 무작위이므로 그 사용자는 비밀번호 찾기로 들어온다. 사용자를 만든 뒤 같은 이메일을 `author.email`로 주고 글을 올리면 작성자가 이어진다.

## 미룬 것

- `POST …/issues/:n/commentNotiReceivers`(댓글 알림 수신자 미리 보기)는 알림 수신자 계산에 의존하므로 4단계에서 옮긴다.
- `POST /-_-api/v1/translation`은 외부 번역 서비스를 부르는 일이라 따로 다룬다.
- 외부 REST API(`getIssue`, `newIssues`, `imports`, `updateIssue`, `updateIssueState`, `updateIssueLabel`, `newIssueComment`)는 화면이 쓰지 않으므로 외부 API 단계에서 옮긴다. `IssueJson`은 그때 재사용한다.

## 댓글 알림 수신자 미리 보기

`POST …/issues/:n/commentNotiReceivers`는 `{comment, parentCommentId}`로 `{"receivers": [사용자 노드]}`를 돌려준다(권한 확인 없음, JSON이 아니면 `400`). 계산은 `NotificationReceivers.forIssueComment`이다([decisions.md] A-54): 이슈 구독자, 작성자, 담당자와 프로젝트에서 이 이벤트를 받겠다고 한 사용자가 받고, 한 줄 댓글이면 이슈 작성자 대신 부모 댓글 작성자와 부모 댓글에서 멘션된 사용자가 받으며 부모 댓글 작성자가 답할 때는 앞의 한 줄 댓글 작성자도 받는다. 구독을 해제했거나 이 이벤트를 받지 않겠다고 했거나 활성이 아닌 사용자, 댓글을 쓰는 사람(로그인하지 않았으면 작성자)은 빠지고, 댓글에서 멘션된 사용자는 마지막에 더해진다. 테스트 `NotificationReceiversTest` 7건과 `IssueApiTest` 3건.

## 번역 API

`POST /-_-api/v1/translation`(`TranslationApiController`, `TranslationClient`). 로그인해야 한다. 본문 `{owner, projectName, type, number}`의 `type`은 `issue`, `posting`(글 번호), `issue-comment`, `post-comment`(댓글 번호). `yona.translation-api`가 비었으면 `412` `Precondition Failed`, 없는 대상은 `404`. 글은 `Title: 제목\r\n\r\n본문`, 댓글은 내용을 쓴다. `&`를 `%26`으로 바꿔 `\r\n`으로 줄을 나누고 4500자 안쪽 덩어리로 묶어 번역 서비스에 `source=ko&target=en&text=…`(인코딩하지 않는다)로 차례로 보낸다. 설정한 헤더(`yona.translation-header-key`, `-value`)로 인증하고 응답의 `result.translatedText`를 줄바꿈으로 이어 마크다운으로 그려 `{"translated": HTML}`로 돌려준다. 번역문이 없는 덩어리는 줄바꿈 하나이다. 테스트 `TranslationClientTest` 5건, `TranslationApiTest` 2건, `IssueApiTest` 1건.
