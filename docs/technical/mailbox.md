# Mailbox

Ported from legacy Yona's `docs/technical/mailbox.md`. The core algorithm (UID watermark to
detect new mail, trusting the sender's `From` header, `+`-subaddressing to determine the
project) is preserved 1:1 in `ImapMailboxPoller`/`IncomingMailProcessingService` — verified in
code. Config keys moved from `imap.*` to `yona.mailbox.imap.*` (see
[`docs/yona-mail-settings.md`](../yona-mail-settings.md)).

Mailbox is a service which fetches and posts emails from the IMAP server configured by
`yona.mailbox.imap.*` settings. When yona starts, a thread for Mailbox starts to fetch emails
from the IMAP server and handle them.

## Fetches new emails

First of all, Mailbox opens the configured IMAP folder.

If the folder is the same as the one Mailbox has used, it fetches new emails from the folder.

How does Mailbox determine whether the folder is the same as the used one? After Mailbox opens
an IMAP folder, it stores the uidvalidity in a `MAILBOX_LAST_UID_VALIDITY` property. Mailbox
considers a folder the same as the used one if their uidvalidity is equal.

And how does Mailbox determine which emails in the folder are "new"? Whenever Mailbox fetches an
email, it updates a `MAILBOX_LAST_SEEN_UID` property with the uid of the most recently fetched
email. Mailbox considers an email "new" if its uid is larger than the value of
`MAILBOX_LAST_SEEN_UID`.

Mailbox handles fetched emails immediately. See "Handling the emails".

## Fetches upcoming emails

**This is improved over legacy** — legacy only polled. yona first tries IMAP `IDLE` for
real-time server push, and only falls back to polling
(`yona.mailbox.imap.polling-interval-ms`, default 5 minutes) when the IMAP server doesn't
support `IDLE`.

Mailbox handles fetched emails immediately, same as above.

## Handling the emails

yona posts fetched emails as an issue or a comment if possible.

Mailbox determines the author by the sender's email address in the `From` header. Emails from a
sender who isn't a yona user are ignored.

Mailbox determines the project(s) from the detail part after the plus sign in the local part of
the recipient's address in the `To` header — e.g. `owner/project` from
`yona+owner/project@mail.com`. Since `To` can have multiple recipients, the email can be posted
to more than one project.

If the received email is a reply to another notification email, it's posted as a comment on the
resource the notification was about. Mailbox determines the resource from the message-ids stored
in `In-Reply-To`/`References` headers, plus the resource path in the detail part if present
(e.g. `issue_post/123` from `owner/project/issue_post/123`).

If yona fails to post an email, it replies to the sender with the reason and a help message.

## Security Consideration

yona believes the email address in the `From` header of a received email is truthful and uses it
for authentication without doubt. This means a malicious user can send an email from another
person's email address to create an issue in a private project they can't otherwise access. To
avoid this, your IMAP server must deny every email whose `From` header is forged.

(Re-verified in code: `IncomingMailProcessingService` calls
`userRepository.findByEmail(message.fromAddress)` and trusts it directly.)
