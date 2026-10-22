---
title: 푸시 뒤 일
type: subsystem
sources: [app/playRepository/RepositoryService.java:253-290, app/playRepository/hooks/PullRequestCheck.java, app/playRepository/hooks/IssueReferredFromCommitEvent.java, app/playRepository/hooks/NotifyPushedCommits.java, app/actors/PostReceiveActor.java, app/actors/IssueReferredFromCommitEventActor.java, app/actors/CommitsNotificationActor.java, app/models/NotificationEvent.java:1116-1135, app/models/NotificationEvent.java:1288-1330]
updated: 2026-10-23
---

# 푸시 뒤 일

`GitHooks.postReceive(project, sender)`가 푸시가 끝난 뒤 차례로 건다(훅 하나가 실패해도 다음을 계속 건다).

1. 프로젝트의 마지막 푸시 시각 갱신
2. 최근 푸시한 브랜치 기록
3. `PostReceiveJobs.afterPush`: 요청과 별개로(`BackgroundJobRunner`) 아래 세 가지를 실행

| 일 | v1.6 | 내용 |
|---|---|---|
| 이슈 언급 | `IssueReferredFromCommitEvent` | 새로 들어온 커밋 메시지의 `#번호`가 가리키는 이슈에 `ISSUE_REFERRED_FROM_COMMIT` 이벤트(보낸 사람 로그인 ID와 이메일, 새 값은 커밋 해시) |
| 풀 리퀘스트 확인 | `PullRequestCheck` | 갱신한 브랜치와 관련된 열린 요청의 병합 검사를 다시 한다. 지운 브랜치와 관련된 요청은 지운다(리뷰 스레드가 달려 있으면 외래 키 때문에 지워지지 않고 로그만 남는다) |
| 새 커밋 알림 | `NotifyPushedCommits` | 프로젝트를 구독한 구성원(구독 해제자와 `NEW_COMMIT` 알림을 끈 사람, 보낸 사람 제외)에게 `NEW_COMMIT` |

새로 들어온 커밋은 명령마다 `새 id`까지의 이력에서 `옛 id`까지를 뺀 것이고, 옛 id가 없으면 새 id 하나뿐이다. 알림 제목은 푸시한 ref가 하나이면 `[이름] N개의 커밋이 브랜치 X로 푸시…`, 여럿이면 브랜치 없이 개수만 보여 준다. 본문은 "새 커밋"(짧은 해시와 메시지를 코드 블록으로)과 "브랜치"(이력 화면 링크) 목록이다.

웹훅 호출은 5e에서 더한다.
