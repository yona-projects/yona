What you should consider when validating names
==============================================

Ported from legacy Yona's `docs/technical/name-validation.md`. This is design guidance for
anyone writing or changing name-validation rules — framework-agnostic, so it's still relevant to
yona. (Whether the actual regexes/constraints in code match this document exactly wasn't
re-verified — check the relevant validator classes, e.g. `domain/*/EmailDomainValidator.kt`.)

Considerations for names to be used as a path segment in URL
-----------------------------------------------------------

We recommend names used as path segments (e.g. project name or user name) consist of
alphanumeric, `- . _ ~`, as follows, to avoid percent-encoding:

    name  = ALPHA / DIGIT / "-" / "." / "_" / "~"

Any name containing reserved characters like `/` or `?` is always percent-encoded when used in a
URL. For example, a URL to a project named "요비" is encoded as:

    http://www.foo.com/bar/%EC%9A%94%EB%B9%84

Percent-encoded URLs not only look ugly, they also make bugs easier to introduce.

### An exception

Any characters are allowed in attachment file names, since they're difficult to control.

Considerations for names used as file or directory names
------------------------------------------------------------------

The following are also used as file or directory names:

* a project name
* a user name

### Length limitation

Filename length is limited to 255 bytes on ext filesystems and 255 UTF-16 characters on NTFS.

### Characters not allowed

Filenames must not include `\0 /` on ext filesystems, or `\ / : * ? " < > |` on Windows.

### Case sensitivity

Display names case-sensitively; compare them case-insensitively.

To prevent duplicate names, comparisons should be case-insensitive so things work correctly on
filesystems like HFS+. But names should be shown to users case-sensitively to meet user
expectations — meaning names should be *stored* case-preservingly too.

### An exception

yona doesn't support 8.3 (short) filenames like "FILENAME.TXT" from MS-DOS/Windows 3.1/Windows
95. Correct behavior isn't guaranteed outside the
[Certified System Configurations](http://www.oracle.com/technetwork/java/javase/config-417990.html)
of the Oracle JRE.

### Notes

Any file whose name:

* starts with `.` may be recognized as a hidden file.
* starts with `-` may be misread as a shell command option.
* is `.` may be misread as the current-directory symbol.
* is `..` may be misread as the parent-directory symbol.

Considerations for names used in the local part of an email address
----------------------------------------------------------------------

Per RFC 5322, an email address consists of local-part and domain:

    addr-spec       =   local-part "@" domain

Per the `dot-atom-text` rule, the local-part cannot start or end with `.`. So
`foo.bar@mail.com` is allowed, but `.foo@mail.com` and `foo.@mail.com` are not.

Considerations for compatibility with other services
---------------------------------------------------

To make importing/exporting to and from yona easy, yona's naming convention should be as
compatible as possible with other services like Github.

### Github

Github's username validation is simple — usernames can contain dashes and alphanumerics, but
can't start with a dash.

Github doesn't document its repository-name validation rules, but through trial and error, we
found: alphanumeric plus `- _ .` are allowed; anything else is automatically converted to `-`.
Strings like `.`, `...`, and `.git` can't be used as repository names because they're reserved.

Considerations for Basic Authentication
---------------------------------------

Any name used as a userid for Basic Authentication (e.g. a user's login id) **must not** contain
a `:` character. If it does, authentication won't work at all, since the scheme uses `:` as the
separator between userid and password in credentials.

It's fine for a password to contain `:`.
