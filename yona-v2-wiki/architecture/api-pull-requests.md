---
title: 풀 리퀘스트 REST API
type: architecture
sources:
  - web/pull-request-review.md
  - architecture/api-token.md
  - architecture/api-auth-split.md
updated: 2026-10-29
---

# 풀 리퀘스트 REST API (X-15, D-39)

v1.6에 없는 추가 항목이다. 개인용 API 토큰으로 풀 리퀘스트를 읽고 바꾸는 외부 API이다. 결정은 [D-39](../decisions.md), 인증은 [api-token.md](api-token.md)를 본다.

## 규칙

- **경로**: `/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/...`. 풀 리퀘스트는 프로젝트 안의 번호(`number`)로 가리킨다.
- **인증과 범위**: `Authorization: token <값>`(또는 `Bearer`)뿐이다. 모든 경로가 `repo`(공개 프로젝트는 `public_repo`) 범위를 요구하고(`ApiScopeRules`), 그 위에 화면과 같은 **사용자 권한**(`AccessControl`)을 확인한다. 범위가 모자라면 403, 토큰이 없거나 틀리면 401이다.
- **응답 모양**: Yona 자체 JSON이며 이슈 API(`IssueJson`)와 같은 카멜 규칙이다. 사용자는 `{loginId, name}`, 프로젝트는 `{owner, name}`, 시각은 ISO(`yyyy-MM-dd'T'HH:mm:ssZ`)이고 없으면 `null`이다.
- **오류**: `{"message": 문장}`과 상태 코드이다. 없는 프로젝트(또는 읽을 수 없는 프로젝트)는 존재를 숨기려고 403(v1.6 `AbstractProjectCheckAction`과 같다), 없는 풀 리퀘스트 번호는 404, 입력 오류는 400, 상태가 맞지 않아 할 수 없는 일은 409이다.
- **쓰기 응답**: 풀 리퀘스트를 바꾸는 요청은 바뀐 풀 리퀘스트 전체(상세 모양)를 돌려준다.

## 조회

| 요청 | 설명 |
|---|---|
| `GET /pullrequests?state=open\|closed&page=1&pageSize=15` | 목록. `closed`는 닫힌 것과 병합된 것이다. 응답 `{items, page, pageSize, totalPages, totalCount}`. `pageSize`는 1~100 |
| `GET /pullrequests/{number}` | 상세 |
| `GET /pullrequests/{number}/commits` | 현재 커밋 목록(오래된 순서). `{commits}` |
| `GET /pullrequests/{number}/files` | 병합 기준 커밋 사이의 변경 파일. `{files: [{path, oldPath, status, additions, deletions, binary, patch, truncated}]}`. `patch`는 통합 diff(헝크를 이은 것)이고 이진 파일이거나 너무 크면 `null` |
| `GET /pullrequests/{number}/timeline` | 이벤트(`type`은 `EventType` 이름)를 만든 순서로. `{events: [{id, type, createdAt, sender, oldValue, newValue}]}` |
| `GET /pullrequests/{number}/reviews` | 리뷰 판정 이력. `{reviews: [{id, verdict, body, createdAt, author, dismissed, dismissedAt, dismissedBy, dismissedReason}]}` |
| `GET /pullrequests/{number}/comments` | 리뷰 댓글을 스레드별로. 공개 스레드와 **호출한 사용자 본인의 임시 댓글**(`pending: true`). `{threads: [{id, state, pending, commitId, path, startLine, ..., comments: [{id, body, createdAt, editedAt, pending, author}]}]}` |

상세 모양: `number`, `id`, `title`, `body`, `state`(`open`, `closed`, `merged`), `author`, `receiver`, `fromProject`, `toProject`, `fromBranch`, `toBranch`, `createdAt`, `updatedAt`(수정한 적이 없으면 `createdAt`), `mergedAt`, `isConflict`, `isMerging`, `mergeable`(`clean`, `conflicting`, `unknown`), `url`, `assignees`, `labels`(`id`, `labelName`, `labelColor`, `category`), `requestedReviewers`, `approvals`(`count`, `required`, `approvers`), `mergedCommitIdFrom`, `mergedCommitIdTo`.

## 쓰기

| 요청 | 권한 | 설명 |
|---|---|---|
| `POST /pullrequests` `{"title","body","fromProject":{"owner","name"},"fromBranch","toBranch","assignees","labelIds","temporaryUploadFiles"}` | 출발 프로젝트를 쓸 수 있는 사람 | 만들기. 경로의 프로젝트가 받는 프로젝트이고 `fromProject`를 생략하면 같은 프로젝트이다. 브랜치는 `master`나 `refs/heads/master`. 201과 풀 리퀘스트(`Location`은 화면 주소). 제목 필수 255자, 같은 브랜치 쌍의 열린 요청이 있으면 409 `{pullRequest: {number, url}}`, 게스트는 403, 출발 프로젝트가 자신이나 포크·원본이 아니거나 브랜치가 없으면 400. 담당자와 라벨은 받는 프로젝트를 고칠 수 있는 사람만(아니면 403) |
| `PATCH /{number}` `{"title","body","fromBranch","toBranch","temporaryUploadFiles"}` | 수정 | 주어진 것만 고친다. 제목은 비울 수 없고 255자까지, 브랜치 쌍이 다른 열린 요청과 겹치면 409, 아무것도 안 주면 400 |
| `POST /{number}/close` | 닫기 | 병합된 것은 409, 이미 닫힌 것은 그대로 성공 |
| `POST /{number}/reopen` | 다시 열기 | 열려 있거나 병합된 것은 409 |
| `POST /{number}/merge` `{"method":"merge"}` | 받아들이기 | `method`는 생략할 수 있고 **`merge`만 받는다**(그 밖의 값은 400 `Unsupported merge method: ...`, 스쿼시와 리베이스는 [로드맵 X-28](../roadmap.md)에서 연다). 열려 있지 않거나 병합 중이거나 충돌하거나 리뷰 조건을 채우지 못했으면 409. 프로젝트 관리자와 사이트 관리자는 화면처럼 리뷰 조건을 건너뛴다 |
| `DELETE /{number}/fromBranch`, `POST /{number}/fromBranch/restore` | 수정 | 병합된 풀 리퀘스트의 출발 브랜치 지우기와 되살리기 |
| `POST /{number}/assignees` `{"assignees":["로그인ID"]}` | 수정 | 1명까지, 빈 배열은 해제. 없는 사용자와 후보가 아닌 사람은 400 |
| `POST /{number}/labels` `{"attachingLabelIds":[...],"detachingLabelIds":[...]}` | 수정 | 받는 프로젝트의 라벨만. 없는 라벨과 다른 프로젝트 라벨은 400 |
| `POST /{number}/requestedreviewers` `{"reviewer":"로그인ID"}`, `DELETE /{number}/requestedreviewers/{로그인ID}` | 수정 | 리뷰 요청과 취소(D-37 규칙). 작성자와 프로젝트·조직에 속하지 않은 사람은 400 |
| `POST /{number}/reviews` `{"verdict","body"}` | 읽기 | 201 `{review, publishedComments}`. 승인과 변경 요청은 받아들일 권한이 필요하고 작성자는 403, 내 임시 댓글이 있으면 함께 공개되며 본문은 생략할 수 있다 |
| `POST /{number}/reviews/{reviewId}/dismissals` `{"message"}` | 받아들이기 | 사유 필수 |
| `POST /{number}/comments` `{"body","path","startSide","startLine","startColumn","endSide","endLine","endColumn","commitId","threadId","pending"}` | 리뷰 댓글 만들기 | 201 `{comment, threadId}`. `pending: true`는 임시 저장 |
| `PATCH /{number}/comments/{commentId}` `{"body"}` | 작성자, 지울 수 있는 사람 | 고치기. 임시 댓글은 작성자만(남에게는 404) |
| `DELETE /{number}/comments/{commentId}` | 작성자, 지울 수 있는 사람 | 지우기. 임시 댓글은 작성자만 |
| `DELETE /{number}/pendingreview` | 로그인 | 내 임시 리뷰 버리기. `{discarded}` |
| `POST /{number}/threads/{threadId}/close`, `.../open` | 화면과 같다 | 스레드 닫기와 열기. `{thread}` |

## 판단과 제한

- 이슈 API는 없는 이슈에 400을 돌려주지만(v1.6) 풀 리퀘스트 API는 없는 번호에 404를 돌려준다(새 API이므로 상태 코드의 뜻대로).
- 라벨은 지금 번호(`labels[].id`)로 붙이고 뗀다. 프로젝트의 라벨 목록 API는 X-19에서 만든다.
- 목록의 `state=all`은 없다(GitHub처럼 `closed`가 병합된 것을 포함한다).
- 임시 댓글(D-38)은 호출한 사용자의 조회에만 보이고 다른 사용자의 조회와 알림에는 나오지 않는다.
- 리뷰를 제출하면 알림은 리뷰 하나로 나가고 줄 댓글이 한꺼번에 공개된다.
- 첨부는 이슈 API처럼 `/-_-api/v1/files`로 먼저 올린 번호를 `temporaryUploadFiles`로 만들기, 고치기, 줄 댓글 달기에 붙인다.
- 만들 때 출발 프로젝트가 받는 프로젝트 자신이거나 그 포크(또는 원본)여야 하고 브랜치가 실제로 있어야 하는 검사는 화면보다 엄격하다(화면은 v1.6처럼 입력을 믿는다).
- 병합은 v1.6처럼 병합 중 표시를 먼저 저장하므로 트랜잭션을 걸지 않는다([refactoring-backlog.md] R-76).
