# 메일 알림 설정

legacy Yona의 `docs/ko/yona-mail-settings.md`를 yona 기준으로 갱신.

## 메일 발송(SMTP)

legacy는 `conf/application.conf`의 `smtp.*` 항목(그리고 테스트용 `smtp.mock=true`)으로
설정했다. yona는 Spring Boot 표준인 `spring.mail.*`을 쓴다 — 별도의 "mock" 플래그는 없고,
기본값(`application.yml`)이 로컬 포트 `1025`를 가리키고 있어 [MailHog](https://github.com/mailhog/MailHog)나
[Mailpit](https://github.com/axllent/mailpit) 같은 로컬 SMTP 캐처를 띄워두면 legacy의
`smtp.mock=true`와 비슷하게 실제 발송 없이 개발할 수 있다.

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 465
    username: yourGmailId
    password: yourGmailPassword
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

(Gmail은 최근 앱 비밀번호/OAuth2 없이 일반 비밀번호로는 SMTP 인증이 막혀 있는 경우가 많다 —
Google 계정의 앱 비밀번호를 발급받아 `password`에 쓴다.)

알림 메일 발송 자체를 끄려면(legacy의 `notification.bymail.enabled`에 대응):

```yaml
yona:
  notification:
    bymail:
      enabled: false
```

`yona.notification.bymail.*` 아래에는 이 외에도 발송 지연/배치 간격, 수신자 수 제한(bcc 처리),
보관 기간 등 legacy `application.conf`의 `application.notification.*` 항목들이 거의 1:1로
대응되어 있다 — 전체 목록은 [settings-reference.md](settings-reference.md#알림-메일) 참고.

## 메일로 이슈/댓글 등록 (IMAP Mailbox)

legacy의 `imap.*` 설정이 yona에서는 `yona.mailbox.imap.*`로 이름만 바뀌고 개념은 동일하다.

```yaml
yona:
  mailbox:
    imap:
      enabled: true
      host: imap.googlemail.com
      ssl: true
      user: "your-yona-email-address@gmail.com"
      address: "your-yona-email-address@gmail.com"
      password: yourGmailPassword
      folder: inbox
      polling-interval-ms: 300000   # 5분마다 폴링. legacy는 폴링 주기가 설정 불가였다
```

legacy 문서의 보안 경고는 yona에도 그대로 적용된다 — IMAP 서버가 발신자 위장(From 헤더 조작)
메일을 걸러주지 않으면, yona는 From 헤더의 주소를 그대로 신뢰해서 인증에 사용한다.
