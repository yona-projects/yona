---
title: 이메일 수신(Mailbox)
type: web
sources: [app/mailbox/MailboxService.java, app/mailbox/EmailHandler.java, app/mailbox/CreationViaEmail.java, app/mailbox/Content.java, app/mailbox/EmailAddressWithDetail.java, app/mailbox/IMAPMessageUtil.java, app/models/OriginalEmail.java, app/models/Property.java, docs/technical/mailbox.md]
updated: 2026-10-25
---

# 이메일 수신 (Mailbox)

받은 편지함(IMAP)의 새 메일로 프로젝트에 이슈를 만들거나 알림 메일에 대한 답장을 댓글로 단다. 코드는 `mailbox/`(`MailboxService`, `MailHandler`, `MailResourceCreator`, `MailParsing`, `MailboxLifecycle`)이다. 결정은 D-24(Jakarta Mail), D-25(결함 처리), A-97, 결함은 R-61, 리팩터링 후보는 T-3(Spring Integration), T-4(여러 프로젝트)이다.

## 설정 (`yona.imap.*`)
| 키 | v1.6 | 기본 |
|---|---|---|
| `host`, `user`, `password` | `imap.host`, `imap.user`, `imap.password` | 모두 있어야 연결한다. 호스트가 비면 시작하지 않는다 |
| `use` | `imap.use` | `true` |
| `domain`, `address` | `imap.domain`, `imap.address` | 수신 주소는 `address`, 없으면 `user@domain`(`user`에 `@`가 있으면 그 값, `domain`이 비면 사이트 호스트 이름) |
| `ssl`, `folder` | `imap.ssl`, `imap.folder` | `false`, `inbox` |
| `port` | 없음(A-97) | 비우면 프로토콜 기본 포트 |
| `yona.mailbox-polling-interval` | `application.mailbox.polling.interval` | 5분 |

## 새 메일 가져오기 (`MailboxService`)
1. 서버가 준비되면 시작한다(비밀 키를 정하기 전에는 시작하지 않는다). 폴더는 읽기 전용으로 연다. 열지 못하면 오류만 남기고 다시 시도하지 않는다.
2. 이전에 쓰던 폴더와 같으면(속성 `MAILBOX_LAST_UID_VALIDITY`가 같으면) 마지막으로 본 UID(`MAILBOX_LAST_SEEN_UID`) 다음부터 `UIDNEXT`까지의 메일을 UID 순서로 처리한다. 폴더가 바뀌었거나 기록이 없으면(처음 설치) 지난 메일은 처리하지 않고 UID 유효값과 지금까지의 마지막 UID(`uidNext - 1`)를 기록한다. 마지막 UID를 기록해야 폴링도 이후 도착한 메일을 처리한다(v1.6은 기록하지 않아 폴링이 영원히 처리하지 못했다, R-61-5, A-375).
3. 그 뒤로는 IDLE 명령으로 새 메일을 받는다. 서버가 IDLE을 지원하지 않으면 5분마다 폴더를 열어 새 메일을 가져온다. 수신 대기 스레드는 폴더가 닫히면 다시 열고, 다른 오류에는 멈춘다.
4. 처리한 메일의 UID가 지금까지의 마지막 UID보다 클 때만 기록한다.
5. 진단 화면은 수신 대기 스레드가 없거나 죽었으면 알린다(폴링일 때도 없다고 알린다, R-61).

## 메일 한 통 처리 (`MailHandler`)
1. `Auto-Submitted: auto-replied`, `X-Naver-Absent: yes` 메일과 이미 처리한 메시지 ID는 건너뛴다(마지막으로 본 UID도 갱신하지 않는다).
2. `From`의 주소마다 사용자를 찾아(없으면 건너뛴다) 처리한다. 처리 결과와 관계없이 처리 완료로 센다.
3. 받는 주소 중 수신 주소(세부 부분만 다른 것)의 세부 부분이 대상 프로젝트이다. 비어 있으면 무시, `help`이면 도움말 회신, `소유자/프로젝트`가 아니면 주소 오류, 없거나 읽을 수 없으면 오류이다.
4. 댓글 대상 후보는 `In-Reply-To`와 `References`의 메시지 ID(원본 메일로 기록된 것, 없으면 메시지 ID의 왼쪽 부분을 `종류/번호` 경로로 본 것)와 받는 주소의 세부 부분(`소유자/프로젝트/종류/번호`)이다. 같은 프로젝트의 후보에는 종류에 따라 댓글을 달고(리뷰 스레드, 이슈, 게시글), 후보가 없으면 이슈를 만든다.
5. 처리하지 못한 이유는 도움말과 함께 보낸 사람에게 회신한다(제목 `Re: 원래 제목`, `In-Reply-To`와 `References`에 원래 메시지 ID).

## 알림 메일에 답장하기
알림 메일의 `Reply-To`는 `수신주소+소유자/프로젝트/종류/번호@도메인`이라 메일 프로그램의 "회신"이 곧 댓글이 된다([subsystems/notifications.md](../subsystems/notifications.md)). 종류는 `ResourceType.resource` 값(`issue_post`, `board_post`, `comment_thread` 등), 번호는 리소스 번호(DB 번호)이다. 수신 주소의 계산은 `MailboxAddress`가 맡고 `MailHandler`와 알림 메일 발송이 함께 쓴다.

### 커밋 알림에 대한 답장

커밋 알림 메일의 `Reply-To`는 `수신주소+소유자/프로젝트/commit/프로젝트번호:해시@도메인`이다. 커밋 리소스의 번호는 `프로젝트번호:커밋해시`이다. 사용자가 주소를 손으로 `commit/해시`처럼 해시만 적어 보내면 v1.6은 번호를 읽다가 `NumberFormatException`이 되어 처리하지 못했다(R-124-4).

v2의 `MailHandler.commitPathWithProject`는 해시만 있는 `commit/해시` 경로를 주소의 `소유자/프로젝트`로 찾은 프로젝트의 번호를 붙여 `commit/프로젝트번호:해시`로 바꾼 뒤 처리한다. 결과는 완전한 주소와 같다.

| 받는 주소의 세부 부분 | 처리 |
|---|---|
| `소유자/프로젝트/commit/프로젝트번호:해시` | 커밋 리소스로 인식한다. 커밋에는 댓글을 달 수 없으므로 새 이슈를 만들지 않고, "댓글을 달 수 없다"(`viaEmail.error.unsupported.resource`)고 발신자에게 회신한다(R-61-11). v1.6은 아무것도 하지 않고 회신도 없었다. |
| `소유자/프로젝트/commit/해시` | 위와 같은 주소로 바꿔 같게 처리한다(v1.6은 오류). |
| 주소의 프로젝트를 찾을 수 없는 `commit/해시` | 경로를 바꾸지 않아 없는 리소스를 가리킨 다른 주소처럼 "없는 리소스" 회신이 나간다(아래). |

### 댓글을 달 수 없는 주소와 없는 리소스 (R-61-11, R-61-12)

| 받는 주소가 가리키는 것 | 처리 |
|---|---|
| 이슈, 게시글, 코드 리뷰 스레드 | 그 리소스에 댓글을 단다 |
| 그 밖의 종류(커밋, 사용자, 풀 리퀘스트 등) | 이슈도 댓글도 만들지 않고 `viaEmail.error.unsupported.resource`로 회신한다 |
| 없는 번호(`issue_post/999999`) | 이슈도 댓글도 만들지 않고 `viaEmail.error.notfound.resource`("주소의 리소스 <종류/번호>가 없다")로 회신한다. v1.6은 모호한 `Unexpected error occurs`였다 |

오류를 회신할 때는 새 이슈를 만들지 않는다. 답장을 달려던 메일이 엉뚱한 새 이슈가 되지 않게 하기 위해서이다(A-366).

## 리소스 만들기 (`MailResourceCreator`)
- 보낸 사람이 프로젝트에 그 리소스를 만들 권한이 있어야 한다. 이슈는 제목이 메일 제목, 본문이 메일 본문이다.
- 본문 읽기(`MailContent`): 파일 이름이 있는 파트는 첨부, `multipart/related`는 `start` 또는 첫 파트가 본문이고 나머지는 첨부, `multipart/alternative`는 텍스트를 우선(관련 파트 안에서는 HTML 우선), 그 밖의 `multipart`는 이어 붙인다.
- HTML이면 `cid:`를 `/files/첨부번호`로 바꾸고(이슈와 댓글은 `HtmlCompressor`로 개행도 줄인다) 알림 본문도 같게 고친다. 첨부는 컨테이너에 저장한다(D-25).
- **한 메일을 여러 프로젝트 주소로 보내는 사용은 허용하지 않는다(R-61-2, A-375).** `original_email`은 메시지 ID당 한 행(유일 제약, v1.6과 같다)이라 두 번째 프로젝트부터 기록이 실패하고 오류 회신이 간다. 프로젝트마다 따로 보낸다.
- 만든 리소스는 메시지 ID와 함께 `original_email`에 기록한다. 이미 받은 사람(보낸 사람, 받는 사람, 참조)은 알림 수신자에서 뺀다.
