How to set up sending notifications by email
===

Mail sending function
----
- Open the application.conf file in the conf directory to modify the contents of the Mailer entry.
- You must change `smtp.mock = true` to false, then the actual mail will be sent.


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

How to set up an issue or comment on Yona by mail
---
- Yona uses the MailBox function to reply directly to the notification mail and register the comment, or to register the issue by mail to a specific project.
- To use this function, you have to set up mailbox.

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
