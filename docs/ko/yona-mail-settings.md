각종 알림을 메일로 발송하도록 설정하는 방법
===

메일 발송 기능
----
- conf 디렉터리에 있는 application.conf 파일을 열어 Mailer 항목내용을 수정합니다. 
- 주의할 부분!!
   - `smtp.mock = true`로 되어 있는 부분을 false로 바꿔야 실제 메일이 발송됩니다.


```
# Mailer
# ~~~~~~
# You have to configure SMTP to send mails.
# Example settings, it assume that you use gmail smtp
smtp.host = smtp.gmail.com
smtp.port = 465
smtp.ssl = true
smtp.user = yourGmailId
# Be careful!!! Not to leak password
smtp.password = yourGmailPassword
smtp.domain = gmail.com
#true to use mock mailer for testing, false for using real mail server
smtp.mock = true
# optional, size of mail archive for tests, default: 5
smtp.archive.size = 5
```

메일로 Yona에 이슈나 댓글이 등록될 수 있게 설정하는 방법
---
- Yona는 MailBox 기능을 이용해서 알림메일에 직접 답장을 해서 댓글을 등록하거나 특정 프로젝트로 메일을 이용해 이슈를 등록하는 기능을 제공하고 있습니다.
- 이 기능을 사용하려면 메일박스 설정을 해야 합니다.

```
# Mailbox Service
# ~~~~~~~~~~~~~~~
#
# Mailbox Service fetches and process mails from IMAP server. For example, if
# the service fetches an email to a project, it posts the email as an issue of
# the project.
#
# If you want to use this feature, your IMAP server has to be configured to
# support the address alias using '+' sign, also known as 'subaddressing' or
# 'detailed addressing'. For example, emails to 'yona+issue@yourmail.com' have
# to be delivered to 'yona@yourmail.com'.
#
# SECURITY WARNING: Yona believes the email address in From header of the
# received email is truthful and use it for authentication without doubt. To
# avoid this problem, your imap server must deny every email whose From header
# is forged.
#
# Here is an example if you use Gmail.
#
# Set imap.use to true if you want to use this feature.
imap.use = false
imap.host = imap.googlemail.com
imap.ssl = true
imap.user = "your-yona-email-address@gmail.com"
# The email address of Yona. Mailbox Service processes an email only if its
# address is as follows.
imap.address = "your-yona-email-address@gmail.com"
# Be careful!!!
imap.password = yourGmailPassword
imap.folder = inbox
```
