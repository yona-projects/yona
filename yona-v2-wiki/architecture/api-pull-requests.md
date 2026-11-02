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
| `GET /pullrequests/{number}/reviewercandidates?query=` | 리뷰를 요청할 수 있는 사람(이 프로젝트와 소속 조직의 구성원, 작성자와 이미 요청된 사람은 제외). `query`는 이름이나 로그인 ID의 일부이고 생략하면 전체(최대 100명). 풀 리퀘스트를 고칠 수 있는 사람만. `{users: [{loginId, name}]}` |
| `GET /pullrequests/{number}/assigneecandidates?query=` | 담당자로 지정할 수 있는 사람(받는 프로젝트의 이슈 담당자 후보 규칙). 같은 모양 |
| `GET /pullrequests/{number}/comments` | 리뷰 댓글을 스레드별로. 공개 스레드와 **호출한 사용자 본인의 임시 댓글**(`pending: true`). `{threads: [{id, state, pending, commitId, path, startLine, ..., comments: [{id, body, createdAt, editedAt, pending, author}]}]}` |

상세 모양: `number`, `id`, `title`, `body`, `state`(`open`, `closed`, `merged`), `author`, `receiver`, `fromProject`, `toProject`, `fromBranch`, `toBranch`, `createdAt`, `updatedAt`(수정한 적이 없으면 `createdAt`), `mergedAt`, `isConflict`, `isMerging`, `mergeable`(`clean`, `conflicting`, `unknown`), `url`, `assignees`, `labels`(`id`, `labelName`, `labelColor`, `category`), `requestedReviewers`, `approvals`(`count`, `required`(받는 브랜치의 보호 규칙이 요구하는 승인 수, 규칙이 없으면 0), `approvers`), `mergedCommitIdFrom`, `mergedCommitIdTo`, `mergeMethod`(병합된 요청만 `merge`, `squash`, `rebase`, 아니면 `null`), `mergeMethods`(방식 이름을 키로 `allowed`, `available`, `reason`; `reason`은 `conflict`, `merge-commits`, `not-open`, `null`).

## 쓰기

| 요청 | 권한 | 설명 |
|---|---|---|
| `POST /pullrequests` `{"title","body","fromProject":{"owner","name"},"fromBranch","toBranch","assignees","labelIds","temporaryUploadFiles"}` | 출발 프로젝트를 쓸 수 있는 사람 | 만들기. 경로의 프로젝트가 받는 프로젝트이고 `fromProject`를 생략하면 같은 프로젝트이다. 브랜치는 `master`나 `refs/heads/master`. 201과 풀 리퀘스트(`Location`은 화면 주소). 제목 필수 255자, 같은 브랜치 쌍의 열린 요청이 있으면 409 `{pullRequest: {number, url}}`, 게스트는 403, 출발 프로젝트가 자신이나 포크·원본이 아니거나 브랜치가 없으면 400. 담당자와 라벨은 받는 프로젝트를 고칠 수 있는 사람만(아니면 403) |
| `PATCH /{number}` `{"title","body","fromBranch","toBranch","temporaryUploadFiles"}` | 수정 | 주어진 것만 고친다. 제목은 비울 수 없고 255자까지, 브랜치 쌍이 다른 열린 요청과 겹치면 409, 아무것도 안 주면 400 |
| `POST /{number}/close` | 닫기 | 병합된 것은 409, 이미 닫힌 것은 그대로 성공 |
| `POST /{number}/reopen` | 다시 열기 | 열려 있거나 병합된 것은 409 |
| `POST /{number}/merge` `{"method":"merge"}` | 받아들이기 | `method`(`merge`, `squash`, `rebase`)와 `commitTitle`, `commitMessage`는 모두 생략할 수 있다. 방식을 생략하면 프로젝트의 기본 방식이고, 모르는 값은 400 `Unsupported merge method: ...`, 프로젝트가 허용하지 않은 방식은 400 `Merge method not allowed: ...`이다. 제목과 본문은 병합 커밋과 스쿼시에만 쓴다. 리베이스는 한 커밋에서라도 충돌하거나 병합 커밋이 섞여 있으면 409이다([병합 방식](pull-request-merge-methods.md), X-28). 열려 있지 않거나 병합 중이거나 충돌하거나 리뷰 조건을 채우지 못했으면 409. 프로젝트 관리자와 사이트 관리자는 화면처럼 리뷰 조건을 건너뛴다 |
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
- 병합은 병합하는 동안 병합 중 표시를 저장해 보이게 하므로 메서드에 트랜잭션을 걸지 않는다. 표시는 병합 조건을 확인한 뒤에 저장하고 병합기가 예외를 던지면 거둔다([refactoring-backlog.md] R-76, 선생님 승인 2026-11-02).

## Magpie 연동 (X-23, 2026-11-03)

Magpie의 변경 요청 계약(`contract:change-request`)이 요구하는 조회를 채운다. 기존 필드와 경로는 바꾸지 않고 더하기만 했다. 계획은 [magpie-integration-plan.md](magpie-integration-plan.md)이다. 기간은 `since` 이상, `until` 미만이고 날짜만 쓰면 UTC 0시이다(이슈 쪽과 같다, [api-project-reads.md](api-project-reads.md)).

**목록의 더해진 것** (`GET $P/pullrequests`)
- `state`에 `merged`(병합된 것만)와 `all`(전부)이 더해졌다. `closed`는 닫힌 것과 병합된 것이다(기존).
- 질의 `createdSince`, `createdUntil`(만든 기간), `updatedBefore`(마지막 갱신이 이 시각 미만인 오래된 것), `mergedSince`, `mergedUntil`(병합 기간), `sort=updated|created`(내림차순, 생략하면 분류의 기본). 읽을 수 없는 날짜, 거꾸로 된 기간, 알 수 없는 `sort`는 400이다. `author`, `assignee`, `reviewRequested`, `labelIds`는 X-37f에 이미 있다.
- 항목에 `authorFirstTime`: 작성자가 이 프로젝트에서 처음 낸 이슈나 풀 리퀘스트이면 `true`이다.
- Magpie의 `list_authored(person, since, end, state)`는 `author`, `createdSince`, `createdUntil`, `state=all` 또는 `merged`(`mergedSince`, `mergedUntil`)와 항목의 `mergedAt`, `authorFirstTime`으로 채운다.

**토론** (`GET $P/pullrequests/{n}/discussion`): `{"comments": [{"author", "date", "body", "kind"}], "participants": [...], "unresolvedCount": N}`. 리뷰 스레드의 댓글은 `comment`, 리뷰는 판정에 따라 `approval`(`APPROVED`), `change-request`(`CHANGES_REQUESTED`), `review`(`COMMENTED`)이다. 시각 순이다. 해제한 리뷰와 임시 저장 댓글은 담지 않는다. `unresolvedCount`는 열린 리뷰 스레드 수이고 `participants`는 댓글이나 리뷰를 남긴 로그인 ID이다. 범위는 `repo`이다.

**리뷰 활동** (`GET $P/activity/reviews?reviewer=&since=&until=&membersOnly=`, `reviewer`를 생략하면 프로젝트 전체이고 (풀 리퀘스트, 리뷰한 사람)마다 한 줄, `membersOnly=true`이면 구성원(manager, member)의 리뷰만): `{"items": [{"pullRequestNumber", "url", "reviewer", "firstReviewAt", "bodyLength", "inlineCommentCount", "labels"}]}`. 사람이 창 안에서 리뷰한 풀 리퀘스트이고 처음 리뷰한 시각 순이다. `bodyLength`는 창 안의 리뷰 본문 길이의 합, `inlineCommentCount`는 리뷰 스레드에 단 댓글 수이다. 리뷰 본문은 응답에 없다. 범위는 `read:repo` 이상이다. Magpie의 `list_reviews_given`이 쓴다.

**이미 있어 바꾸지 않은 것**: `get`(`GET $P/pullrequests/{n}`, `/commits`, `/files`)와 `status`(상태와 `mergeable`: `clean`, `conflicting`, `unknown`), `post_review`(`POST …/reviews`, `verdict`는 `APPROVED`, `CHANGES_REQUESTED`, `COMMENTED`), `land`(`POST …/merge`의 `method`, 응답의 `mergedCommitIdTo`), `reject`(`POST …/close`, 이유는 어댑터가 댓글로 남긴다). `list_authored_commits`는 `GET $P/activity/commits`가 채운다([api-project-reads.md](api-project-reads.md)).

