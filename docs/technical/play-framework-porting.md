# Play Framework Porting Notes

## Target

This branch ports Yona from its original Play Framework 2.3-era stack to Play
Framework 3.0.11 with sbt 1.12.13, Scala 2.13.18, and Java 21 bytecode.

Play 3 uses the `org.playframework` artifact group and Apache Pekko instead of
Akka. The build and configuration have been moved to those names, including the
HTTP server, logging/config keys, actors, and scheduler usage.

## Main Changes

- Upgraded the build from the old sbt/Play plugin line to Play 3.0.11, Play
  Ebean 8.5.0, sbt-web/Less assets, and BuildInfo on sbt 1.x.
- Switched the Java compilation target to `--release 21`, matching the current
  modern LTS OpenJDK line supported by Play 3.
- Pinned the application Ebean runtime to 14.3.0 because Yona still uses
  single-table inheritance mappings that are no longer supported in newer
  Ebean runtime lines pulled by the Play Ebean plugin.
- Replaced the old Akka API/configuration surface with Apache Pekko.
- Migrated persistence annotations from `javax.persistence` to
  `jakarta.persistence` and moved static Ebean access behind a local
  compatibility facade.
- Replaced removed Play global/static APIs with local compatibility helpers for
  configuration, request/session/flash access, controller conveniences, cache,
  WS, messages, mail, and Play Authenticate integration points.
- Reworked controller actions, forms, cookies, raw requests, request headers,
  Java action creation, application loading, error handling, and Twirl
  compatibility for Play 3 APIs.

## Verification

Verified with the locally installed Temurin/OpenJDK 21 runtime:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) java -jar /tmp/codex-sbt/sbt-launch-1.12.13.jar clean compile
```

Result: production compile succeeds.

Runtime smoke testing was also performed with Play 3.0.11 on Java 21 using an
in-memory H2 database. Because the checked-in evolutions are MySQL-oriented, the
smoke run copied them to `/tmp/yona-smoke-evolutions` and removed MySQL-only
table options for H2. With that test database:

- `GET /` returns `200 OK` and renders the Yona home page.
- `GET /users/loginform` returns `200 OK` and renders the login form.
- Main CSS, JavaScript, and favicon assets return `200 OK`.
- Browser rendering shows the home page title/content and login form fields,
  with no browser console errors observed during the smoke check.

After clearing stale generated files from the old sbt-web/Less pipeline under
`project/target`, asset compilation also resolves the current Less 4.2.0
package instead of the historical Less 1.7.5 package.

## Remaining Work

- The old test suite still targets Play 2.3 test APIs such as
  `play.test.FakeApplication`, `play.test.FakeRequest`, `callAction`, and
  generated `controllers.routes.ref` helpers. `Test/compile` now reaches those
  test-source migration errors after the asset pipeline is fixed.
- Broader runtime testing is still needed for authenticated workflows, OAuth
  provider handshakes, mail, project import/export, Git/SVN smart HTTP, archive
  download, notifications, and production MySQL/MariaDB migrations.
- The local Play Authenticate compatibility layer is enough for compilation,
  but OAuth sign-in flows should be rewired and tested against maintained
  provider libraries before release.
- Old generated build artifacts should be removed once when switching an
  existing checkout from the previous sbt line: `project/target` can retain a
  stale Less 1.7.5 extraction that shadows Less 4.2.0.
