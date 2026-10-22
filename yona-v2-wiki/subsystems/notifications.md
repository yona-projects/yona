---
title: 알림 서브시스템
type: subsystem
sources: [app/models/NotificationEvent.java, app/models/NotificationMail.java, app/models/UserProjectNotification.java, app/models/Watch.java, app/utils/EventConstants.java]
updated: 2026-10-23
---

# 알림 서브시스템

4단계는 세 부분이다: (a) 알림 이벤트 만들기, (b) 메일 보내기(스케줄러), (c) 화면(홈의 알림 목록). 발송 방식은 v1.6처럼 비동기 스케줄러이다([decisions.md] D-18).

## (a) 알림 이벤트 만들기

서비스 코드는 v1.6이 `NotificationEvent.after…`를 부르던 자리에서 애플리케이션 활동(D-14)을 발행한다. `NotificationListener`가 활동을 받아 같은 요청, 같은 트랜잭션 안에서 `NotificationEvents`로 저장한다.

| 활동 | 알림 종류 | 제목 | 값 | 수신자(`NotificationReceivers`) |
|---|---|---|---|---|
| `IssueCreatedActivity` | `NEW_ISSUE` | `[프로젝트] 제목 (#번호)` | 새 값=본문 | 이슈의 실제 구독자 + 본문 멘션 − 작성자 |
| `IssueChangedActivity` 상태 | `ISSUE_STATE_CHANGED` | `Re: [프로젝트] 제목 (#번호)` | 이전, 새 상태 | 이슈 필수 수신자 |
| 〃 마일스톤 | `ISSUE_MILESTONE_CHANGED` | 〃 | 이전, 새 마일스톤 번호(없음은 `-1`) | 이슈 필수 수신자 |
| 〃 담당자 | `ISSUE_ASSIGNEE_CHANGED` | 〃 | 이전, 새 로그인 아이디 | 필수 수신자 + 이전 담당자 |
| 〃 본문 | `ISSUE_BODY_CHANGED` | 〃 | 이전, 새 본문 | 필수 수신자 + 새로 멘션된 사용자 |
| 〃 이동 | `ISSUE_MOVED` | 〃 | 이전, 새 `소유자/이름` | 활동이 가져온 이전 구독자(옮기기 전에 계산) |
| 〃 공유자 | `ISSUE_SHARER_CHANGED` | 〃 | 추가는 `"" → 아이디`, 삭제는 `아이디 → ""` | 그 공유자 한 명. 연달아 합치지 않는다 |
| 〃 라벨 | `ISSUE_LABEL_CHANGED` | 〃 | 이전, 새 라벨 설명 | 없음(저장하지 않는다) |
| `IssueDeletedActivity` | `RESOURCE_DELETED` | `[프로젝트] 제목 (#번호)`, 리소스는 프로젝트 | 이전=본문, 새 값=지운 사람 | 실제 구독자 + 본문 멘션 − 지운 사람 |
| `IssueCommentActivity` | `NEW_COMMENT`, `COMMENT_UPDATED` | `Re: …` | 이전=이전 내용, 새 값=댓글 | `NotificationReceivers.forIssueComment` |
| `PostingCreatedActivity` | `NEW_POSTING` | `[프로젝트] 제목 (번호)` | 새 값=본문 | 글의 실제 구독자 + 멘션 − 작성자 |
| `PostingUpdatedActivity` | `POSTING_BODY_CHANGED` | `[프로젝트] 제목 (번호)` | 이전, 새 본문 | 글 필수 수신자 |
| `PostingDeletedActivity` | `RESOURCE_DELETED` | 〃 | 〃 | 글의 실제 구독자 + 멘션 − 지운 사람 |
| `PostingCommentActivity` | `NEW_COMMENT`, `COMMENT_UPDATED` | `Re: …` | 〃 | `forPostingComment` |
| `ProjectMemberRequestEvent`, `OrganizationMemberRequestEvent` | `MEMBER_ENROLL_REQUEST`, `MEMBER_ENROLL_ACCEPT`, `ORGANIZATION_*` | 메시지 키 `notification.member.request.*` | 새 값=상태, 이전=반대 상태 | 프로젝트는 구독 중인 관리자, 조직은 조직 관리자. 수락이면 신청한 사용자 |

수신자 규칙(`NotificationReceivers`):
- **실제 구독자**: 프로젝트 구독자 + 리소스 직접 구독자 + 작성자 − 리소스 구독 해제자, 읽을 수 없는 사용자 제외.
- **필수 수신자**(이슈): 이슈 직접 구독자, 작성자, 공유자, 담당자, 프로젝트에서 이 이벤트를 받겠다고 한 사용자 − 구독 해제자 − 받지 않겠다고 한 사용자 − 바꾼 사람. 글은 담당자와 공유자가 없다.
- **댓글**: 위에 더해 한 줄 댓글이면 이슈 작성자 대신 부모 댓글 작성자와 부모 댓글 멘션 사용자(부모 작성자가 답하면 앞 한 줄 댓글 작성자도), 활성 상태가 아닌 사용자 제외, 댓글 멘션 사용자는 마지막에 더한다.

저장(`NotificationEvents`):
- `add`: 같은 리소스의 직전 이벤트가 `yona.notification-draft-time`(기본 30초) 안에 같은 사용자의 같은 종류이면 합친다(A→B, B→C ⇒ A→C. B→A이면 둘 다 지운다).
- `addWithoutSkip`: 직전 값을 정확히 되돌릴 때만 지운다. 수신자는 공유자 추가일 때만 거른다.
- 수신자 거르기: 아이디가 없거나, 리소스를 읽을 수 없거나, 프로젝트를 구독 중이면서 이 종류를 끈 사용자를 뺀다(프로젝트를 구독하지 않으면 남긴다). 거른 뒤 수신자가 없으면 저장하지 않는다.
- 저장하면 메일 대기열(`notification_mail`) 행도 만든다.

웹훅 호출은 5단계에서 더한다. 테스트: `NotificationEventsTest` 11, `NotificationListenerTest` 11, `NotificationMemberRequestTest` 4, `NotificationReceiversTest` 7.
