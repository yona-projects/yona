Additional options when running yona
===

Ported from legacy Yona's `docs/yona-run-options.md`, adapted for yona. Legacy's OS-specific
sections (`bin/yona` vs `bin/yona.bat`, Windows path-length issues) mostly don't apply anymore —
yona runs the same `java -jar` command on every OS. The Windows issues that do still apply
(base-dir path settings, NTFS-only Fork hard-links) are covered in
[README's "Deployment configuration"](../README.md#deployment-configuration-especially-on-windows).

### Memory allocation

Pass JVM options directly to `java` (legacy read a `JAVA_OPTS` environment variable through its
`bin/yona` wrapper script; yona has no such wrapper).

```bash
java -Xmx2048m -Xms2048m -jar build/libs/yona-0.0.1-SNAPSHOT.jar --spring.profiles.active=mariadb
```

### Changing the port

Legacy used `-Dhttp.port=80`; Spring Boot uses `server.port`.

```bash
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar --server.port=80
# or
java -Dserver.port=80 -jar build/libs/yona-0.0.1-SNAPSHOT.jar
```

### Choosing a DB profile

Not a legacy concept — yona is a single jar that supports 5 DBs (MariaDB/PostgreSQL/MySQL/SQL
Server/CUBRID), selected with `--spring.profiles.active=<profile>`. See
[README's "Choosing a database"](../README.md#choosing-a-database).

### DB schema migration

Legacy used Play's evolutions and, after upgrading, you might hit:

```
[warn] play - Your production database [default] needs evolutions!
```

which required setting `-DapplyEvolutions.default=true`. yona uses JPA/Hibernate's
`ddl-auto: update` (already configured per DB profile in `application.yml`), which applies
schema changes automatically on startup — there's no manual flag to flip.

### Physical storage path options

`--yona.git.base-dir=...` and the other 3 path settings are covered in
[README's "How to change these settings"](../README.md#how-to-change-these-settings).
