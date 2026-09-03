yona Install
===

Ported from legacy Yona's `docs/install-yona-server.md`, adapted for yona. Legacy shipped
pre-built zip distributions you unpacked and ran; yona is built from source with Gradle instead.

```
Prerequisite
---
JDK 21 (not Java 8 — legacy required Java 8, yona targets Java 21)
```

Clone or download this repository, then build and run it:

```bash
./gradlew bootJar
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar --spring.profiles.active=mariadb
```

### DB configuration

You need to point yona at the MariaDB you installed earlier (or another supported DB — see
[README](../README.md#choosing-a-database)).

The default connection settings live in `src/main/resources/application.yml`, under the
`mariadb` profile block:

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:23306/yona?...
    username: yona
    password: yona_password
```

Either edit that file directly, or override on the command line without touching it:

```bash
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=mariadb \
  --spring.datasource.url=jdbc:mariadb://127.0.0.1:3306/yona \
  --spring.datasource.username=yona \
  --spring.datasource.password=your_real_password
```

Run for the first page
----

Unlike legacy (which needed a separate `YONA_DATA` directory holding `conf`/`logs`/`uploads`/`repo`),
yona doesn't require a single data directory upfront — physical storage (git/svn repos, LFS
objects, uploads) is controlled by four independent settings
(`yona.git.base-dir`, `yona.svn.base-dir`, `yona.lfs.base-dir`, `yona.upload.base-dir`), each with
its own default. See [README's "Deployment configuration"](../README.md#deployment-configuration-especially-on-windows)
for how to change them.

Just run:

```bash
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar --spring.profiles.active=mariadb
```

Then continue with [`yona-run-and-restart.md`](yona-run-and-restart.md) for details.
