# Mailbox (IMAP 수신 메일 처리)

legacy Yona의 `docs/technical/mailbox.md`를 옮김. 핵심 알고리즘(UID 워터마크로 새 메일 판별,
발신자 이메일 신뢰, `+` 서브어드레싱으로 프로젝트 판별)은 `ImapMailboxPoller`,
`IncomingMailProcessingService`에 그대로 이식되어 있다 — 코드로 확인했다. 설정 키는
`imap.*`에서 `yona.mailbox.imap.*`로 이름만 바뀌었다([mail-settings.md](../mail-settings.md) 참고).

Mailbox는 `yona.mailbox.imap.*` 설정으로 지정된 IMAP 서버에서 메일을 가져와 처리하는
서비스다. yona가 시작되면 Mailbox용 스레드가 시작되어 IMAP 서버로부터 메일을 가져와 처리한다.

## 새 메일 가져오기

먼저 설정된 IMAP 폴더를 연다.

폴더가 이전에 쓰던 것과 같은지는, 폴더를 열 때마다 `uidvalidity` 값을
`MAILBOX_LAST_UID_VALIDITY` 프로퍼티에 저장해두고 비교하는 방식으로 판단한다 —
두 uidvalidity가 같으면 같은 폴더로 간주한다.

어떤 메일이 "새 메일"인지는, 메일을 가져올 때마다 가장 최근에 가져온 메일의 uid로
`MAILBOX_LAST_SEEN_UID` 프로퍼티를 갱신해두고, 그 값보다 큰 uid를 가진 메일을 새 메일로
간주하는 방식으로 판단한다.

가져온 메일은 즉시 처리한다 ("메일 처리" 참고).

## 이후 도착하는 메일 가져오기

**yona는 legacy보다 이 부분이 개선됐다** — legacy는 폴링(polling)만 지원했지만, yona는
IMAP `IDLE` 명령으로 서버 push를 우선 시도하고, IDLE을 지원하지 않는 서버에서만
`yona.mailbox.imap.polling-interval-ms`(기본 5분) 간격 폴링으로 대체한다.

가져온 메일은 이번에도 즉시 처리한다.

## 메일 처리

가져온 메일은 가능하면 이슈나 댓글로 등록된다.

작성자는 메일의 `From` 헤더에 있는 발신자 이메일 주소로 판단한다. yona 사용자가 아닌 발신자의
메일은 무시된다.

프로젝트는 수신자(`To` 헤더) 이메일 주소의 local part에서 `+` 기호 뒤에 오는 부분(예:
`yona+owner/project@mail.com`의 `owner/project`)으로 판단한다. `To` 헤더는 수신자가 여러
명일 수 있으므로, 메일이 게시될 프로젝트가 둘 이상일 수도 있다.

받은 메일이 다른 알림 메일에 대한 답장이면, 그 답장은 알림의 근거가 된 리소스의 댓글로
등록된다. 리소스는 `In-Reply-To`/`References` 헤더에 저장된 message-id와, local part에
포함된 리소스 경로(예: `owner/project/issue_post/123`의 `issue_post/123`)로 판단한다.

메일 게시에 실패하면, 발신자에게 실패 사유와 도움말이 담긴 메일로 답장한다.

## 보안 고려 사항

yona는 받은 메일의 `From` 헤더에 적힌 이메일 주소를 의심 없이 사실로 믿고 인증에 사용한다.
즉 악의적인 사용자가 다른 사람의 이메일 주소로 메일을 보내면, 접근 권한이 없는 비공개
프로젝트에도 이슈를 만들 수 있다는 뜻이다. 이 문제를 피하려면, IMAP 서버가 `From` 헤더가
위조된 모든 메일을 거부하도록 설정해야 한다.

(이 보안 특성은 yona의 `IncomingMailProcessingService`에서도 코드로 재확인했다 —
`userRepository.findByEmail(message.fromAddress)`로 발신자를 그대로 신뢰한다.)
