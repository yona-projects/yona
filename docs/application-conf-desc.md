application.yml detail descriptions
===

Ported from legacy Yona's `docs/application-conf-desc.md`, rewritten against yona's
`src/main/resources/application.yml`. Legacy had one `application.conf` file; yona splits
settings between Spring Boot standard keys (`spring.*`) and yona-specific keys (`yona.*`). The
"Default" column below reflects what's actually in code (`@Value("${...:default}")`) or in
`application.yml` — only what was verified.

## Site basics

| Key | Default | legacy equivalent | Notes |
|---|---|---|---|
| `yona.site-name` | `Yona` | `application.siteName` | Site name |
| `yona.base-url` | `http://localhost:8080` | `application.scheme`+`hostname`+`port` | Absolute URL used in emails, etc. |
| `yona.hostname` | `localhost` | `application.hostname` | Hostname |
| `application.noreferrer` | `false` | `application.noreferrer` | Hide referer header when leaving via external links |
| `yona.feedback-url` | `https://github.com/yona-projects/yona/issues` | `application.feedback.url` | Top-menu feedback link |
| `yona.application.navbar.custom-link.name` / `.url` | empty | none (new) | Custom navbar link — not present in legacy |

## Access control / signup

| Key | Default | legacy equivalent | Notes |
|---|---|---|---|
| `yona.access.allows-anonymous-access` | `true` | `application.allowsAnonymousAccess` | Allow anonymous access |
| `yona.application.hide-project-listing` | `false` | `application.hide.project.listing` | Hide the public project listing |
| `yona.signup.require-admin-confirm` | `false` | `signup.require.admin.confirm` | Require admin approval after signup |
| `yona.signup.allowed-email-domains` | empty (no restriction) | `application.allowed.sending.mail.domains` | Allowlist of signup email domains |

No config key was found for legacy's `application.guest.user.login.id.prefix` (a generic guest
prefix), `application.displayPrivateRepositories`, `project.default.scope.when.create`,
`project.creation.default.menus`, or `application.use.email.verification` — these may be
hardcoded, or simply not yet ported as configurable. File a `docs/PARITY_BACKLOG.md` item if
needed.

## Social login / LDAP

Examples: [`yona-social-login-settings.md`](yona-social-login-settings.md).

| Key | Default | legacy equivalent |
|---|---|---|
| `spring.security.oauth2.client.registration.github.client-id`/`.client-secret` | `dummy-client-id`/`dummy-client-secret` | Github section of `social-login.conf` |
| `spring.security.oauth2.client.registration.google.client-id`/`.client-secret` | same | Google section of `social-login.conf` |
| `yona.application.use-social-login-only` | `false` | `application.use.social.login.only` |
| `yona.ldap.enabled` | `false` | `application.use.ldap.login.supoort` |
| `yona.ldap.host`/`.port`/`.protocol` | `127.0.0.1`/`389`/`ldap` | `ldap.host`/`.port`/`.protocol` |
| `yona.ldap.base-dn`/`.dn-postfix` | empty | `ldap.baseDN`/`distinguishedNamePostfix` |
| `yona.ldap.login-property`/`.display-name-property`/`.user-name-property`/`.email-property`/`.department-property`/`.english-name-property` | same defaults as legacy (`sAMAccountName`/`displayName`/`CN`/`mail`/`department`/empty) | `ldap.loginProperty` etc. |
| `yona.ldap.use-email-base-login`/`.fallback-to-local-login` | `false`/`false` | `ldap.options.useEmailBaseLogin`/`fallbackToLocalLogin` |
| `yona.ldap.guest-login-id-prefix` | empty | none (LDAP-specific, new — distinct from legacy's generic guest prefix) |

No config key was found yet for legacy's `application.use.social.login.name.sync`.

## Alert mail

Examples: [`yona-mail-settings.md`](yona-mail-settings.md).

| Key | Default | legacy equivalent |
|---|---|---|
| `yona.notification.bymail.enabled` | `true` | `notification.bymail.enabled` |
| `yona.notification.bymail.interval-ms` | `60000` (60s) | `application.notification.bymail.interval` |
| `yona.notification.bymail.delay-ms` | `180000` (180s) | `application.notification.bymail.delay` |
| `yona.notification.bymail.recipient-limit` | `0` (unlimited) | `application.notification.bymail.recipientLimit` |
| `yona.notification.bymail.hide-address` | `true` | `application.notification.bymail.hideAddress` |
| `yona.notification.bymail.allowed-domains` | empty | none (new — restricts which domains get notification mail) |
| `yona.notification.keep-days` | `-1` (keep forever) | `application.notification.keep-time` |

Legacy's `application.notification.draft-time` (the event-merging time window) exists as a code
constant (draft-time merge logic from the P1-xx items), but whether it's externalized as a
config key wasn't confirmed at the time of writing.

## Mailbox (IMAP)

`yona.mailbox.imap.*` — see [`yona-mail-settings.md`](yona-mail-settings.md#how-to-let-people-create-issuescomments-on-yona-by-email).

## Attachments / physical storage

| Key | Default | legacy equivalent |
|---|---|---|
| `yona.upload.base-dir` | `${yona.data:data}/uploads` | `YONA_DATA/uploads` |
| `yona.git.base-dir` | `/tmp/yona/git` | `YONA_DATA/repo` (Git) |
| `yona.svn.base-dir` | `/tmp/yona/svn` | `YONA_DATA/repo` (SVN) |
| `yona.lfs.base-dir` | `/tmp/yona/lfs` | none (LFS didn't exist in legacy) |
| `spring.servlet.multipart.max-file-size`/`.max-request-size` | **unset → Spring Boot default 1MB/10MB** | `application.maxFileSize` (default 2GB) |

`max-file-size` differs sharply from legacy's default — raise it before real use. See
[`trouble-shootings.md`](trouble-shootings.md#attachment-uploads-fail-413--maxuploadsizeexceededexception).

## GitHub Migration (Import)

| Key | Default | legacy equivalent |
|---|---|---|
| `github.client.id` | empty | `github.client.id` |
| `github.client.secret` | empty | `github.client.secret` |
| `github.allow.migration` | `false` | `github.allow.migration` |

Note: `github.*` lives at the top level, not under `yona.*` (matches the `@Value` declarations
in `MigrationService` verbatim). It requires a **separate GitHub OAuth App registration** from
the social-login `spring.security.oauth2.client.registration.github.*` above.

## Google Analytics / software update check

| Key | Default | legacy equivalent |
|---|---|---|
| `yona.analytics.send-usage` | `false` (legacy defaulted to `true` — opposite) | `application.send.yona.usage` |
| `yona.update.repository-url` | `https://github.com/yona-projects/yona.git` | `application.update.repositoryUrl` |
| `yona.update.current-version` | `1.15.0` | (still uses legacy's version string — may need updating to yona's own scheme) |
| `yona.update.interval-ms` | `21600000` (6h) | `application.update.notification.interval` |

## Logging

Legacy managed this separately via `conf/application-logger.xml` (Logback). yona uses Spring
Boot's standard approach — `logging.level.*` in `application.yml` (currently
`org.springframework.web: DEBUG`, `org.hibernate: WARN`), or add a `logback-spring.xml` to the
classpath for finer control (not present in this repository — plain Spring Boot logging
defaults apply).

## Note

This document was written by verifying actual `@Value("${...}")` declarations and
`application.yml`, but the codebase keeps changing — the ultimate source of truth is always
`src/main/resources/application.yml` and `grep -rn '@Value' src/main/kotlin`.
