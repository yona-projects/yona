What you should consider when validating names
==============================================

This is an informational document for people who want to make a rule for name
validation.

Considerations for names to be used as a path segment in URL
-----------------------------------------------------------

We recommend some names that can be used as path segments (e.g. project name or
user name) consist of alphanumeric, -, ., _ and ~, as follows:

    name  = ALPHA / DIGIT / "-" / "." / "_" / "~"

to avoid them being percent encoded [1].

Any name contains some reserved characters like `/` or `?` are always percent
encoded if it used in URL. For example, a url to a project whose name is "요비"
is encoded as follows:

    http://www.foo.com/bar/%EC%9A%94%EB%B9%84

Percent encoded URL not only looks ugly, but also causes a bug easily.

### An exception

Any characters are allowed for attached files because it is difficult for them
to be under the control.

Considerations in names that can be used as file or directory name
------------------------------------------------------------------

Some names can be used as file or directory names as follows:

* a name of a project
* a name of a user

### Limitation of length

Length of filename is limited to 255 bytes in ext file systems and 255 UTF-16
characters in NTFS.

### Characters not allowed

Filenames must not include `\0 /` in ext file systems and `\ / : * ? " < > |`
in Microsoft Windows.

### Case sensitivity

When displaying names, do it case-sensitively; however, when comparing names,
do it case-insensitively.

When Yobi compares names to prevent duplication, comparison should be case
insensitive to make it work correctly in some file systems (e.g. HFS+). But Yobi
should show names case-insensitively to meet users' needs. It also means that
Yobi should store names case-insensitively.

### An exception

Yobi does not support 8.3 filenames (short filenames), like "FILENAME.TXT",
which is used in MS-DOS, Windows 3.1 and Windows 95. Yobi does not guarantee to
work correctly in any system except the Certified System Configurations of
Oracle JRE [2].

### Notes

Any file whose name:

* starts with `.` may be recognized as a hidden file.
* starts with `-` may be misunderstood as a shell command option.
* is `.` may be misunderstood as a symbol to indicate the current directory.
* is `..` may be misunderstood as a symbol to indicate the parent directory.

Considerations for names to be used in the local part of email address
----------------------------------------------------------------------

According to RFC 5322 [3], a mail address consists of local-part and domain as
follows:

    addr-spec       =   local-part "@" domain

According to the rule of `dot-atom-text`[4], local-part cannot start or end with
`.`. It means that `foo.bar@mail.com` is allowed, but `.foo@mail.com` and
`foo.@mail.com` are not allowed.

Considerations for compatibility with other services
---------------------------------------------------

To make it easy to import or export things from or to Yobi, Yobi's naming
convention should be as compatible as possible with the other services like
Github.

### Github

Github has a very simple validation rule for usernames. Its usernames can
contain both dash and alphanumeric, but usernames starting with a dash is not
allowed.

However, for repository names, Github does not provide any description on its
validation rules. After many times of trying, these are what we found:

Github allows alphanumeric characters such as `-`, `_` and `.`; otherwise, it
automatically changes into `-`. Strings like `.`, `...` and `.git` can't be used
as repository names because they are reserved.

Considerations for Basic Authentication
---------------------------------------

Any name which can be used as a userid of Basic Authentication scheme [5], like
users' login id, MUST NOT contain a `:` character. If the userid contains it,
the authentication does not work at all because the scheme uses a `:` character
as a separator to split credential from a client into userid and password.

It is okay that password contains `:` characters.

References
----------

[1]: http://tools.ietf.org/html/rfc3986#section-2.1
[2]: http://www.oracle.com/technetwork/java/javase/config-417990.html
[3]: http://tools.ietf.org/html/rfc5322
[4]: http://tools.ietf.org/html/rfc5322#section-3.2.3
[5]: http://tools.ietf.org/html/rfc2617#section-2
