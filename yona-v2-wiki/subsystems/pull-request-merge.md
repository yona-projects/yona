---
title: 풀 리퀘스트 병합
type: subsystem
sources: [app/models/PullRequest.java:356-570, app/models/PullRequest.java:872-1010, app/actors/PullRequestActor.java, app/actors/PullRequestMergingActor.java, app/models/PullRequestMergeResult.java, app/models/PullRequestCommit.java, app/models/PullRequestEvent.java, app/models/NotificationEvent.java:1420-1470, app/playRepository/GitRepository.java:1065-1221]
updated: 2026-10-23
---

# 풀 리퀘스트 병합

## 구성

| 클래스 | 역할 | v1.6 |
|---|---|---|
| `PullRequestMerger` | 받는 프로젝트의 저장소에서 가져오기(fetch)와 RECURSIVE 병합. `attempt`(시험), `update`(요청이 있는 동안의 결과), `merge`(받아들이기) | `PullRequest.Merger`, `attemptMerge`, `updateMerge`, `merge` |
| `PullRequestMergeProcessor` | 병합 검사 결과를 풀 리퀘스트에 반영하는 상태 흐름(충돌, 새 커밋, 병합됨) | `PullRequestActor.processPullRequestMerging` |
| `PullRequestMergeRunner` | 위 처리를 별개의 스레드에서 단계별 트랜잭션으로 실행 (`yona.pull-request-merge-async`) | Akka 액터 |
| `PullRequestService` | 번호 매기기, 만들기, 이슈 언급 이벤트 | `PullRequest.save`, `createPullRequest` |
| `PullRequestEventService` | 타임라인 이벤트 기록(리뷰 상태 중복 제거 포함) | `PullRequestEvent.add…` |
| `RelatedAuthorsService` | 바뀐 줄의 마지막 작성자(blame) | `GitRepository.getRelatedAuthors` |
| `NotificationReceivers` | 풀 리퀘스트 구독자, 변경 수신자, 관련 작성자 포함 수신자 | `PullRequest.getWatchers`, `NotificationEvent.getReceivers…` |

## 저장소 참조

- `refs/yobi/pull-check/<소유자>/<이름>/<브랜치>`: 새 요청을 만들기 전 충돌 확인용 임시 참조. 확인 뒤 지운다.
- `refs/yobi/pull/<번호>/head`: 보내는 브랜치의 가져온 사본.
- `refs/yobi/pull/<번호>/merged`: 사이트 이름과 시스템 메일 주소로 만든 시험 병합 커밋.
- 받아들이면(5c-5) 받는 브랜치를 보낸 사람 이름의 병합 커밋으로 갱신한다. 병합 커밋 메시지는 `Merge branch '<브랜치>' [of <소유자>/<이름>] [into '<받는 브랜치>']`, `from pull-request <번호>`, 커밋 목록, `Reviewed-by` 줄이다.
- 푸시로 `refs/yobi/*`를 만들 수 없다(Git 훅).
- 같은 저장소에 대한 가져오기와 참조 갱신은 프로젝트 단위 잠금으로 한 번에 하나씩 실행한다.

## 병합 검사 흐름

`start`(병합 중 표시, 별도 트랜잭션) → `process`: `update` → 충돌 여부와 병합 기준 커밋 저장 → 커밋 동기화 → 이벤트와 알림 → 병합 중 표시 해제. 자세한 단계와 v1.6 결함은 `PullRequestMergeProcessor` 주석과 R-42를 본다.

## 알림 이벤트

| 활동 | 알림 종류 | 받는 사람 |
|---|---|---|
| `PullRequestCreatedActivity` | `NEW_PULL_REQUEST` | 구독자 + 본문 멘션 + 관련 작성자(병합 커밋이 있을 때) − 보낸 사람 |
| `PullRequestChangedActivity` 상태 | `PULL_REQUEST_STATE_CHANGED` | 구독자 + 본문 멘션 − 보낸 사람 |
| 〃 커밋 | `PULL_REQUEST_COMMIT_CHANGED` | 같음. 본문은 현재 커밋 목록 |
| 〃 병합 상태 | `PULL_REQUEST_MERGED` | `merged`이면 관련 작성자 포함, 아니면 구독자만 |

웹훅 호출은 5e에서 더한다.
