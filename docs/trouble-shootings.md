### MariaDB 767 byte Error

This was common on legacy — pre-Barracuda MariaDB rejected utf8mb4 indexes for exceeding the
767-byte key length limit:

```
[error] play - Specified key was too long; max key length is 767 bytes [ERROR:1071, SQLSTATE:42000]
```

**This does not reproduce on yona's default `mariadb:10.11`** (`docker-compose.yml`) — Barracuda
/ dynamic row format has been the default since MariaDB 10.2, which is effectively why the
problem went away. That's also why
[`support-script/mariadb/my.cnf`](../support-script/mariadb/my.cnf) leaves out legacy's
`innodb_file_format=barracuda` / `innodb_large_prefix=on` — MariaDB 10.6+ doesn't recognize
those variables at all and **refuses to start** if they're set. See
[`db-error-767.md`](db-error-767.md) for detail.

### Wrong data in SQL Server / CUBRID, or Korean text corrupted

Real DB-specific defects found and fixed during the port (see `docs/PARITY_BACKLOG.md` around
P1-6x):

- **SQL Server**: without `sendStringParametersAsUnicode=true` on the JDBC URL, string
  parameters are sent non-Unicode and Korean text corrupts. Hibernate also maps `String` to
  `varchar` (non-Unicode) by default — `use_nationalized_character_data: true` forces
  `nvarchar` mapping. Both are already set in the `mssql` profile block of `application.yml`.
- **CUBRID**: without `charSet=utf-8` on the JDBC URL, Korean text is corrupted on save. Also,
  the CUBRID broker sometimes drops idle connections server-side; forcing HikariCP's
  `connection-test-query: SELECT 1` avoids NPEs from handing out dead connections. Both already
  set in the `cubrid` profile block.

If you customize the connection string yourself, don't drop these.

### Attachment uploads fail (413 / MaxUploadSizeExceededException)

Legacy limited single-file uploads via `application.maxFileSize` (default 2GB). yona uses
Spring Boot's standard multipart settings, and **if you don't set
`spring.servlet.multipart.max-file-size` explicitly, Spring Boot's own default (1MB) applies** —
much lower than legacy's default. Raise it explicitly for real usage:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2GB
```

`GlobalExceptionHandler` catches the resulting exception and shows an info page (matches
legacy's `error/requestTextEntityTooLarge.scala.html`).

### OAuth login: client id/secret error

Corresponds to legacy's `RuntimeException: Provider 'google' missing needed setting 'clientId'`.
In yona, this happens when `spring.security.oauth2.client.registration.<provider>.client-id`/
`client-secret` in `application.yml` is empty or left at the development default
(`dummy-client-id`) — see [`yona-social-login-settings.md`](yona-social-login-settings.md).

### Fork (project cloning) fails on Windows

See [README's "Prerequisites for using Fork (hard-link cloning) on Windows"](../README.md#prerequisites-for-using-fork-hard-link-cloning-on-windows) —
it's an NTFS / single-drive constraint.
