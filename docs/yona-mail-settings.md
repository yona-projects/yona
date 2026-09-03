How to set up sending notifications by email
===

Ported from legacy Yona's `docs/yona-mail-settings.md`, adapted for yona.

Mail sending function
----
Legacy configured `smtp.*` in `conf/application.conf` (including a `smtp.mock = true` test
switch). yona uses Spring Boot's standard `spring.mail.*` instead — there's no dedicated "mock"
flag; the shipped default points at local port `1025`, so running a local SMTP catcher like
[MailHog](https://github.com/mailhog/MailHog) or [Mailpit](https://github.com/axllent/mailpit)
gives you the same effect as legacy's `smtp.mock = true` during development.

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

(Gmail often blocks plain-password SMTP auth now — use an App Password.)

To disable notification emails entirely (maps to legacy's `notification.bymail.enabled`):

```yaml
yona:
  notification:
    bymail:
      enabled: false
```

`yona.notification.bymail.*` also covers send delay/batching interval, recipient limits (bcc),
and retention — nearly 1:1 with legacy's `application.notification.*` keys. Full list in
[`application-conf-desc.md`](application-conf-desc.md#alert-mail).

How to let people create issues/comments on yona by email
---
Legacy's `imap.*` settings became `yona.mailbox.imap.*` — same idea, new namespace.

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
      polling-interval-ms: 300000   # 5 min fallback poll; yona also tries IMAP IDLE push first
```

The same security warning from legacy still applies: yona trusts the sender address in the
`From` header for authentication without doubt. Your IMAP server must reject every email whose
`From` header is forged.
