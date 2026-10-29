---
title: 풀 리퀘스트 리뷰 판정
type: web
sources: [app/controllers/ReviewApp.java:36-75, app/models/PullRequest.java:194, app/models/PullRequest.java:626-650, app/models/PullRequest.java:1032-1062, app/controllers/PullRequestApp.java:435, app/actors/PullRequestActor.java:36-52, app/models/enumeration/EventType.java:31, app/views/git/partial_info.scala.html:43-60, app/views/git/partial_list.scala.html:61-65]
updated: 2026-10-29
---

# 풀 리퀘스트 리뷰 판정 (X-12, D-35)

**상태: 구현 완료(2026-10-29).** 결정과 이유는 [decisions.md D-35](../decisions.md)에 있고 이 문서는 구현할 때 따를 상세이다. v1.6의 "리뷰 완료/취소"를 GitHub의 리뷰(Comment, Approve, Request changes) 하나로 합친다. 동치 점검이 끝났으므로 v1.6 동작을 의도해서 바꾸며 바뀐 점은 [differences-from-v16.md](../architecture/differences-from-v16.md)에 올린다.

## v1.6의 지금 동작 (바뀌는 대상)

| 항목 | v1.6 |
|---|---|
| 리뷰 완료 | `POST .../review`(`ReviewApp.review`, `ACCEPT` 권한)가 `pull_request_reviewers`에 본인을 넣고 알림과 이벤트(`PULL_REQUEST_REVIEW_STATE_CHANGED`, DONE)를 남긴다 |
| 리뷰 취소 | `POST .../unreview`가 본인을 빼고 이벤트(CANCEL)를 남긴다. 짧은 시간(`notificationDraftTime`) 안에 되돌리면 직전 이벤트를 지운다 |
| 병합 조건 | 프로젝트가 리뷰어 수를 켜면(`isUsingReviewerCount`) 리뷰어 수가 `defaultReviewerCount` 이상이어야 한다(`PullRequest.isReviewed`, `PullRequestApp.accept:435`). 우회 없음 |
| 새 커밋 | 새 커밋이 들어오면 리뷰어를 모두 비운다(`PullRequestActor:46`) |
| 병합 커밋 | 리뷰어마다 `Reviewed-by: 이름 <이메일>` 줄을 붙인다 |
| 화면 | 개요 상태 영역의 리뷰어 아바타와 버튼(`partial_info`), 목록의 리뷰어 수 표시(`partial_list`) |

## 바뀌는 점

1. **판정**: Comment, Approve(승인), Request changes(변경 요청) 세 가지. 요약 본문과 함께 제출한다. 본문은 Comment와 Request changes에서 필수, Approve에서 선택(GitHub과 같다). 마크다운으로 그리고 길이 한도는 댓글과 같다. 이번에는 첨부와 임시 저장(PENDING)과 다시 리뷰 요청을 두지 않는다.
2. **이력과 최신 판정**: 제출한 리뷰는 모두 `pull_request_review`에 쌓는다. 정책(병합 조건, 표시)에는 리뷰어별 **최신 유효 판정**만 쓴다. 해제된 리뷰는 Comment로 취급한다. 댓글 판정도 최신 판정이 될 수 있어 승인 뒤의 댓글 리뷰는 승인을 거둔다(A-195). GitHub 동작은 문서로 확인되지 않았고 자료가 엇갈린다. 선생님이 이 동작을 **유지하기로 확정**하셨다(2026-10-29). 승인을 유지한 채 의견만 남기려면 의견을 승인과 함께 제출한다(승인 판정에는 본문을 쓸 수 있다).
3. **리뷰 완료는 Approve**: 버튼 이름과 위치는 판정 폼으로 대체한다. "리뷰 취소"는 없애고 다음 판정(Comment)으로 대체한다. 승인한 사람 집합은 v1.6 테이블 `pull_request_reviewers`를 그대로 쓴다(Approve하면 넣고 이후 다른 판정이나 해제가 오면 뺀다). 그래서 `Reviewed-by:`, 목록의 리뷰어 수, 병합 조건은 구조 변경 없이 같은 집합을 읽는다.
4. **작성자 본인**: Approve와 Request changes를 할 수 없다(서버 403, 폼에서 선택지 숨김). Comment는 가능하다. 승인 개수에도 세지 않는다.
5. **권한**: 읽기 권한이 있는 로그인 사용자는 Comment, 프로젝트 구성원(쓰기)은 Approve와 Request changes. 판정 제출은 열린 요청에만 Approve와 Request changes가 가능하고 Comment는 상태와 상관없다(v1.6 "리뷰 완료" 버튼도 열린 요청에만 있었다). 승인한 뒤 쓰기 권한을 잃은 사람의 승인은 집합에 남지만 새 판정은 못 한다(권한 변동 시 자동 정리는 하지 않는다).
6. **Request changes의 효과**: 프로젝트가 리뷰어 수를 켠 경우 리뷰어별 최신 유효 판정에 변경 요청이 하나라도 있으면 병합할 수 없다. 켜지 않은 프로젝트는 표시만 한다. 해제하거나 같은 리뷰어가 다시 Approve하면 풀린다.
7. **해제(dismiss)**: 쓰기 권한자가 사유를 적어 리뷰를 해제한다. 해제된 리뷰는 Comment로 바뀌고(승인 집합에서 빠진다) 타임라인에 이벤트가 남는다. 사유는 필수이다. GitHub 문서는 관리자와 쓰기 권한자만 해제할 수 있다고 하며 작성자나 리뷰어 본인(쓰기 권한이 없는 경우)은 해제할 수 없다.
8. **새 커밋**: 설정 "새 커밋이 올라오면 오래된 승인을 해제한다"(기본 켬, v1.6 동작)가 켜져 있으면 현재 승인들을 시스템이 해제 처리한다(`dismissed_date` 기록, 사유 "새 커밋", 해제한 사람 비움). 꺼져 있으면 승인이 남는다. Request changes와 Comment는 어느 쪽이든 남는다. 설정은 프로젝트 설정의 리뷰어 수 옆에 둔다(프로젝트 열 `is_dismissing_stale_approvals`, 기본 켬, 선생님 확정, D-35 5번).
9. **병합 우회**: 프로젝트 관리자(매니저)와 사이트 관리자는 리뷰어 수와 변경 요청에 막혀도 병합할 수 있다. 병합 화면에 요건 미달 안내를 보이고 `Reviewed-by:`는 실제 승인자만 담는다. "관리자에게도 적용" 옵션은 X-13에서 더한다.

## 저장 구조

프로젝트 테이블에 열 `is_dismissing_stale_approvals`(기본 켬)을 더한다(위 8번). 아래는 새 테이블이다.

새 테이블 `pull_request_review`. 이름과 열은 v1.6의 풀 리퀘스트 계열 테이블 규칙(소문자 스네이크, `pull_request_` 접두사, `<대상>_id`, 사용자 `author_id`와 `author_login_id` 쌍)을 따른다.

| 열 | 형식 | 설명 |
|---|---|---|
| `id` | `BIGINT` 자동 증가 | 기본 키 |
| `pull_request_id` | `BIGINT` | 풀 리퀘스트. 색인 |
| `author_id` | `BIGINT` | 리뷰어 사용자 번호. 색인 |
| `author_login_id` | `VARCHAR` | 리뷰어 로그인 ID 사본 |
| `verdict` | `VARCHAR(17)` | `APPROVED`, `CHANGES_REQUESTED`, `COMMENTED` |
| `contents` | `LONG_TEXT` | 요약 본문 |
| `created_date` | 시각 | 제출 시각 |
| `dismissed_date` | 시각, 없을 수 있음 | 해제 시각 |
| `dismissed_by_login_id` | `VARCHAR` | 해제한 사람. 시스템 해제면 비어 있음 |
| `dismissed_reason` | `LONG_TEXT` | 해제 사유 |

## 이벤트, 알림, 웹훅

- 타임라인 이벤트 종류 두 개를 새로 더한다: `PULL_REQUEST_REVIEW_SUBMITTED`(판정과 본문), `PULL_REQUEST_REVIEW_DISMISSED`. 이름은 열거형 이름 그대로 저장한다(`event_type`, 34자 이내). 옛 `PULL_REQUEST_REVIEW_STATE_CHANGED`는 그대로 남겨 기존 데이터를 표시한다. 새 판정에는 짧은 시간 되돌림 삭제 규칙을 쓰지 않는다.
- 알림은 작성자에게 보낸다(제출과 해제). 수신 규칙은 X-14와 같은 방식으로 `NotificationReceivers`에 더하고 사용자 알림 설정(`notiTypes`)에 새 종류를 올린다. 다섯 언어 메시지를 더하고 `MessageParityTest.addedKeys`에 올린다.
- 웹훅은 풀 리퀘스트 변경 이벤트로 판정 종류를 보낸다(`WebhookListener`, `WebhookPayloads`).

## 기존 데이터 보정 (D-35 13번)

서버를 시작할 때 도는 멱등 보정(`PullRequestStartup`과 같은 `ApplicationRunner` 방식)과, 가져오기(`DataService.import`) 끝에 부르는 같은 보정을 둔다. DB를 그대로 쓰며 바이너리만 바꾸는 경우와 이관 두 경우 모두 같은 결과가 나온다.

1. `pull_request_reviewers`에 있고 `pull_request_review`에 그 사용자의 유효한 승인이 없는 행마다 `APPROVED` 리뷰를 만든다. 시각은 그 사용자의 가장 가까운 옛 리뷰 완료 이벤트 시각, 없으면 풀 리퀘스트 생성 시각이다. 본문은 비운다.
2. 이미 보정된 행은 건너뛰므로 몇 번을 돌려도 같다.
3. 가져오는 파일이 X-12 이전 것이라 새 테이블이 없어도 실패하지 않는다. 새 테이블이 든 파일은 중복을 만들지 않는다.
4. 작성자 본인의 옛 승인은 기록하되 개수에는 세지 않는다. 그 결과 병합 가능 여부가 바뀌는 열린 요청을 찾아(이전 규칙으로는 충족, 새 규칙으로는 미달) 작성자와 프로젝트 관리자에게 알림을 한 번만 보내고 타임라인에 시스템 이벤트를 남기며 시작 로그에 건수와 목록을 적는다. 병합은 9번의 관리자 우회나 다른 구성원의 승인으로 풀린다. 개요 승인 영역에 "작성자의 승인은 세지 않습니다"를 안내한다.

## 내보내기와 가져오기

새 테이블은 `DataTables`(49개에서 50개)에 풀 리퀘스트 다음 순서로 넣는다. 함께 챙길 것: `DataService.cleanOrphanLinks`, 프로젝트, 풀 리퀘스트, 사용자 삭제 연쇄(`ProjectDeletionService`, `SiteUserService`), `SchemaMetadata.INTENDED_ADDITIONS`, `AddedTablesSchemaTest`, `DataExchangeTest`, `DataTablesTest`.

## 경로 (D-35 14번, 선생님 확정)

GitHub 경로에서 `/repos/{owner}/{repo}/pulls/{n}` 접두사만 Yona의 `/:user/:project/pullRequest/:id`로 바꾼다. Yona도 만들기를 복수형으로 두므로(`POST .../pullRequest/:id/comments`) 모양이 어긋나지 않는다.

| 동작 | 경로 | 요청 | GitHub |
|---|---|---|---|
| 판정 제출 | `POST /:user/:project/pullRequest/:id/reviews` | `verdict`(`APPROVED`, `CHANGES_REQUESTED`, `COMMENTED`), `contents`(Comment와 Request changes는 필수, Approve는 선택) | `POST .../pulls/{n}/reviews` |
| 리뷰 해제 | `POST /:user/:project/pullRequest/:id/reviews/:reviewId/dismissals` | `message`(사유, 필수) | `PUT .../reviews/{id}/dismissals` (화면 폼 요청이라 POST) |

v1.6의 `POST .../review`와 `.../unreview`는 **없앤다**(둘 다 화면 버튼의 POST 폼 요청이라 북마크 대상이 아니고 풀 리퀘스트 REST도 v1.6에 없었다). GitHub에는 승인 철회 동작이 없다(제출한 리뷰는 지울 수 없고 해제나 새 판정만). 본인이 승인을 거두려면 자기 리뷰를 해제하거나 새 판정을 낸다. REST(X-15)는 같은 모양에 `/-_-api/v1/owners/{owner}/projects/{project}` 접두사를 붙여 얹는다. 대안은 옛 두 경로를 얇은 별칭으로 남기는 것이다.

## 화면

- 개요 오른쪽 열(`span3`)에 "리뷰어" 영역: 리뷰어별 최신 판정(승인, 변경 요청, 댓글)을 아바타와 함께 보이고 해제된 것은 흐리게 표시한다. 승인 개수와 필요한 개수(리뷰어 수를 켠 프로젝트)도 보인다.
- 판정 폼은 개요 하단의 댓글 영역 근처에 둔다(판정 선택, 본문, 제출). 작성자에게는 Comment만 보인다.
- 타임라인에 판정(본문 포함)과 해제 이벤트를 보인다.
- 목록의 리뷰어 수 표시는 현재 승인 집합을 그대로 읽는다.
- 병합 버튼 영역에 요건 미달 안내와 관리자 우회 표시를 보인다.

## 알림 설정과 문구 (A-203~A-205)

- 승인의 알림과 타임라인 문구는 v1.6 리뷰 완료 문구를 그대로 쓰고 변경 요청, 댓글, 해제는 새 문구를 쓴다.
- 옛 알림과 타임라인 기록은 그대로 둔다.
- **옛 알림 설정은 이어받지 않는다.** 옛 종류 "리뷰 상태 변경"을 꺼 둔 사용자도 새 종류(리뷰 제출, 리뷰 해제)는 기본이 켬이라 새 리뷰 알림을 받는다. 이어받는 보정은 필요할 때 더한다(A-205).

## 이 문서의 범위 밖 (이번에 하지 않는 것)

- **줄 단위 리뷰 댓글과의 묶음**(로드맵 X-26으로 올림): GitHub은 코드 줄에 단 댓글이 "리뷰 시작"으로 임시 저장되었다가 판정과 함께 한꺼번에 제출된다. Yona의 줄 댓글(`CommentThread`)은 지금도 즉시 올라가고 판정과 무관하다. 묶으려면 줄 댓글의 저장과 알림 구조를 바꿔야 해서 판정과 따로 정한다.
- **임시 저장 리뷰(PENDING)**: 위 묶음이 있어야 의미가 있다(판정 없이 임시로 쌓아 둘 것이 줄 댓글이다).
- **리뷰 요청과 다시 요청**(로드맵 X-27로 올림): GitHub은 쓰기 권한자가 특정 사람에게 리뷰를 요청한다. Yona에는 요청 대상 지정 개념이 없고 새 테이블과 화면이 더 필요하다.
- **첨부**: GitHub 문서도 리뷰 본문 첨부를 말하지 않는다.
- REST와 Magpie는 아래.
- REST(X-15)와 Magpie `post_review`(X-23)는 이 구조 위에 나중에 얹는다.

## 시험 계획 (TDD 순서)

1. **저장과 보정**: `pull_request_review` 저장, 삭제 연쇄, 스키마와 내보내기와 가져오기(옛 파일 포함), 보정의 멱등과 영향 점검(작성자만 승인, 작성자와 타인이 함께 승인, 타인만 승인, 닫힌 요청은 대상 아님, 통지 한 번).
2. **서비스 규칙**: 판정별 권한, 작성자 제한, 본문 필수 규칙, 최신 유효 판정 계산, 승인 집합 동기화, Request changes 병합 차단, 관리자 우회, 새 커밋 해제.
3. **웹 계약**: 판정 제출과 해제 요청의 상태 코드, 폼 표시 조건, 개요 영역과 타임라인.
4. **알림과 웹훅**: 수신자, 알림 설정 항목, 메시지 5개 언어, 웹훅 페이로드.
5. **브라우저 검증**: 마지막에 한 번 Playwright로 화면을 캡처한다.

## 구현 결과 (2026-10-29)

- 저장: `PullRequestReview`(테이블 `pull_request_review`), `Project.isDismissingStaleApprovals`, 내보내기와 가져오기(50개 테이블, 옛 파일의 프로젝트 행에 키가 없으면 켬), 프로젝트 삭제 연쇄, 가져온 뒤 정리(`cleanReviews`).
- 보정: `PullRequestReviewBackfill`(시작 때와 가져온 뒤, 멱등)이 승인 리뷰를 만들고 `noticeIgnoredAuthorApprovals`가 작성자 승인을 세지 않아 요건을 잃은 열린 요청을 한 번만 안내한다.
- 서비스: `PullRequestReviewService.submit`, `dismiss`, `dismissStaleApprovals`. 규칙은 `PullRequestRules`(`latestReviews`, `changesRequestedBy`, `countedApprovals`, `reviewBlocked`, `acceptable`).
- 웹: `PullRequestReviewController`(`POST .../reviews`, `.../reviews/:reviewId/dismissals`), 개요의 리뷰어 영역과 판정 폼, 타임라인 이벤트, 병합 우회(`AccessControl.canBypassReview`), 프로젝트 설정의 승인 해제 항목.
- 알림과 웹훅: 새 이벤트 종류 세 개(번호 31~33), 알림 설정 항목, 다섯 언어 메시지.
- 구현 중 정한 세부 판단은 `decisions.md` A-195~A-202에 있다.
