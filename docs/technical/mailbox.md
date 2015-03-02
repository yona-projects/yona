# Mailbox

Mailbox is a service which fetches and posts emails from the IMAP server
configured by imap.* configurations.

When Yobi starts, a thread for Mailbox starts to fetch emails from the IMAP
server and handle them.

## Fetches new emails

First of all, Mailbox opens the configured IMAP folder.

If the folder is the same with the one Mailbox has used, it fetches new emails
from the folder.

How Mailbox determine whether the folder is same with the used one? After
Mailbox opens a IMAP folder, it stores the uidvalidity in
MAILBOX_LAST_UIDVALIDITY property. Mailbox considers a folder is same with the
used one if their uidvalidity equals to each other.

And how Mailbox determine which emails in the folder are "new" emails? Whenever
Mailbox fetches an email, it updates MAILBOX_LAST_SEEN_UID property with the
uid of the most recently fetched email. Mailbox considers an email is "new" if
the uid is larger than the value of MAILBOX_LAST_SEEN_UID property.

Mailbox handles the fetched emails immediately. See "Handling the emails".

## Fetches upcoming emails

After that Mailbox listens or do polling the upcoming emails. Mailbox tries to
listen and fetch new emails immediately if possible.  But if listening is not
available, because the IMAP server does not support IDLE command, it fetches
new emails on the interval configured by `application.mailbox.polling.interval`.

Mailbox handles the fetched emails immediately. See "Handling the emails".

## Handling the emails

Yobi posts the fetched emails as an issue or a comment if possible.

Mailbox determines the author by the sender's email address from 'From' header of
the email. Emails from the sender who is not a user of Yobi are ignored.

Mailbox determines the projects by the detail parts, which comes after plus
sign in local part, e.g. owner/project from yobi+owner/project@mail.com, of the
recipient's email addresses from 'To' header of the email. Since 'To' header
can have multiple recipients, the projects to which the email will be posted
can be more than one.

If the received email is reply to another notification email, the received
email will be posted as a comment of the resources on which the notification is
based. Mailbox determines the resources by message-ids, which is stored in
'In-Reply-To' and/or 'References' header, and resource path, if the detail part
includes: e.g.  'issue_post/123' of 'owner/project/issue_post/123'.

If Yobi failed to post an email, it replies to the sender with an email which
contains the reason and help message.

## Security Consideration

Yobi believes the email address in From header of the received email is
truthful and use it for authentication without doubt. It means a malicious user
can send an email from another person's email address to create an issue to
a private project the user cannot access. To avoid this problem, your imap
server must deny every email whose From header is forged.
