#### How to upgrade

Ported from legacy Yona's `docs/yona-upgrade.md`, adapted for yona.

- Stop yona (`Ctrl-C`, or `sudo systemctl stop yona`).
- Pull the new source (`git pull`, etc.) and rebuild:
  ```bash
  ./gradlew bootJar
  ```
- Run it again (`java -jar build/libs/yona-0.0.1-SNAPSHOT.jar ...`, or
  `sudo systemctl start yona`).

The DB schema is migrated automatically on startup via `ddl-auto: update` — see
[`yona-run-options.md`'s "DB schema migration"](yona-run-options.md#db-schema-migration). There's
no legacy-style evolutions warning to react to.

#### Migrating from legacy Yona

This isn't a version upgrade — it's moving to a **different application with a different
architecture** (Play/Java/Ebean → Spring Boot/Kotlin/JPA; different DB driver and schema mapping
too). Pointing yona at a legacy Yona database directly and starting it up is not a tested path.
Keeping screens, data model, and behavior equivalent to legacy is this project's goal, but an
actual data-migration procedure (legacy DB → yona) isn't documented yet — check
`docs/parity/index.md` for porting status, and always back up and test in a non-production
environment before touching real data.
