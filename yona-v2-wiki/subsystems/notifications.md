---
title: 알림 서브시스템
type: subsystem
sources: [app/models/NotificationEvent.java, app/models/NotificationMail.java, app/models/UserProjectNotification.java, app/models/Watch.java, app/controllers/WatchProjectApp.java, app/controllers/NotificationApp.java, app/utils/DiffUtil.java, app/views/common/notificationMail.scala.html, app/views/index/partial_notifications.scala.html, app/views/user/edit_notifications.scala.html]
updated: 2026-10-23
---

# 알림 서브시스템

4단계는 네 부분이다: (a) 알림 이벤트 만들기, (b) 메일 보내기(스케줄러와 시스템 메일), (c) 화면(홈의 알림 목록), (d) 보관 기간 정리와 프로젝트별 알림 설정. 발송 방식은 v1.6처럼 비동기 스케줄러이다([decisions.md] D-18).

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

## (b) 메일 보내기

### 알림 메일 (대기열 → 스케줄러)

알림 이벤트를 저장하면 `notification_mail` 대기열 행도 만든다. `NotificationMailScheduler`가 기동 5초 뒤부터 60초마다 대기열을 비운다(`yona.notification-mail-init-delay`, `-interval`, 끄려면 `-enabled=false`).

1. 만든 지 `yona.notification-mail-delay`(기본 180초)가 지난 알림의 대기열 행을 지우고 알림을 모은다. 보냈는지와 관계없이 지운다(같은 알림을 다시 보내지 않기 위해서이며 v1.6도 그렇다).
2. `NotificationMerger`가 같은 사용자의 **상태 변경 + 새 댓글**을 한 통으로 묶는다(댓글 문구가 앞). 두 알림의 수신자가 다르면 셋으로 나눈다(둘 다 받는 사람, 댓글만, 상태 변경만). 묶는 중 오류가 나면 묶지 않고 보낸다.
3. 대상 리소스가 아직 있는 것만 `NotificationMailSender`가 보낸다. 한 건이 실패해도 다음 건을 계속 보낸다.

`NotificationMailSender`의 동작:
- 받는 사람: 활성 사용자 중 `yona.allowed-sending-mail-domains`에 드는 도메인(`MailRecipients`). 선호 언어별로 나누고 `yona.notification-mail-recipient-limit`씩 끊어 따로 보낸다.
- 주소 숨김(`yona.notification-mail-hide-address`, 기본 켬): To에 사이트 보내는 주소, 사용자는 Bcc.
- 보내는 주소: `yona.smtp.user`(`@`가 없으면 `@yona.smtp.domain`, 이것도 없으면 사이트 호스트 이름). SMTP 접속은 `spring.mail.*`.
- 제목: 알림 제목. 본문: `NotificationMessages`가 만든 문구를 마크다운으로 렌더링(본문 변경만 변경 차이 HTML 그대로)하고 `common/notification-mail.html` 틀(보기 링크, 구독 해제 링크, 설정 링크)로 감싼다. `MailHtml`이 모든 링크를 절대 주소로 바꾸고 외부 링크에 `rel="noreferrer"`(설정 시), 이미지는 최대 너비와 원본 링크로 감싼다.
- 일반 텍스트 본문: 문구 + 고정 문장("Yona 에서 자세히 보거나…"). 본문 변경은 `---`/`+++` 접두어 형식(`MailDiff`).
- `Message-ID`: 새로 만들어지는 알림(새 이슈, 새 글, 새 댓글)은 리소스로 만든 `<리소스종류/번호@호스트>`. `References`: 컨테이너(댓글의 이슈 등)의 식별자. 메일 프로그램이 한 대화로 묶는다.
- `Reply-To`(메일로 답글)는 7단계(IMAP)에서 더한다.
- 기록: 로거 `mail.out`.

### 시스템 메일 (대기열 없이 즉시)

`SystemMailListener`가 요청 이벤트를 받아 `SystemMails`가 언어, 사이트 주소, 받는 사람처럼 요청 안에서만 구할 수 있는 값을 모으고, 메일 본문 만들기와 발송은 `MailDispatcher`의 전용 스레드가 비동기로 한다(D-19). 요청한 사용자의 언어로 문구를 만든다.

| 이벤트 | 메일 | v1.6 |
|---|---|---|
| `PasswordResetRequestedEvent` | `[사이트] 제목`, 본문 = 안내 + 재설정 주소(일반 텍스트) | `PasswordResetApp.sendPasswordResetMail` |
| `UserVerificationRequestedEvent` | 인증 링크 + 계정 안내(HTML과 텍스트). 보내는 사람은 `yona.easymail`. 허용하지 않는 도메인은 보내지 않음 | `UserApp.sendMailAfterUserCreation` |
| `EmailValidationRequestedEvent` | 보조 이메일 확인 링크(HTML) | `actors.ValidationEmailSender` |
| `ProjectTransferRequestedEvent` | 받는 사용자 또는 조직 관리자에게 Bcc로 수락 링크(마크다운 HTML + 텍스트), `References: <수락주소@호스트>` | `ProjectApp.sendTransferRequestMail` |

## (c) 홈의 알림 목록

`GET /notifications`(홈)은 로그인 사용자의 알림 20개를 최신순으로 보여 주고, 목록이 비어 있지 않으면 More 단추를 붙인다. More는 `GET /notification?from=&limit=`로 다음 조각을 받아 이어 붙인다(`NotificationController`). 로그인하지 않아도 오류 없이 빈 조각을 돌려준다(v1.6의 `@AnonymousCheck` 기본값).

항목(`NotificationItemAssembler`): 종류별 아이콘(새 값에 따라 닫힘/병합 등 변형), 본문·댓글 수정은 "Edit" 표시, 제목 링크(이동할 곳이 없으면 제목만), 본문(서버 기본 언어, 줄바꿈 `<br/>`, 정화), 보낸 사람 아바타와 이름, 경과 시간.

## (d) 보관 기간 정리와 프로젝트별 알림 설정

- `NotificationCleaner`: `yona.notification-keep-time`(일, 기본 -1)이 0보다 크면 기동 1분 뒤부터 하루에 한 번 오래된 알림 이벤트를 지운다.
- 프로젝트 구독(`WatchProjectController`): `POST /{user}/{project}/watch`, `/unwatch`(해제하면 그 프로젝트의 알림 설정도 지움), `POST /noti/toggle/{projectId}/{type}`(구독 중인 프로젝트에서 이벤트 종류별로 받을지 뒤집음. 기본은 전부 켬이고 새 댓글만 꺼짐. 기본값과 같아지면 설정 행을 지움). 구독자 목록 `GET /{user}/{project}/watchers`(로그인 없이 가능).
- 알림 설정 탭 `GET /user/editform/notifications`: 구독한 프로젝트마다 이벤트 종류(`order` 순, 27개)의 On/Off 스위치.

## 테스트

`NotificationEventsTest` 11, `NotificationListenerTest` 11, `NotificationMemberRequestTest` 4, `NotificationReceiversTest` 7, `MailDiffTest` 6, `NotificationMessagesTest` 9, `NotificationMergerTest` 4, `MailRecipientsTest` 5, `MailHtmlTest` 6, `NotificationMailSenderTest` 8, `NotificationMailSchedulerTest` 4, `SystemMailsTest` 5, `NotificationCleanerTest` 2, `NotificationListTest` 8, `ProjectWatchTest` 10. 웹훅 호출과 풀 리퀘스트, 코드 리뷰, 커밋 알림은 5단계에서 더한다.
