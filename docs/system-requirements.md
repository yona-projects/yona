Server
------

Ported from legacy Yona's `docs/system-requirements.md`, adapted for yona.

* JDK 21 (not Java 8+ — legacy targeted Java SE 8, yona is built with the Java 21 toolchain,
  see `build.gradle.kts`)
* One of the supported/tested DBs: MariaDB (default), PostgreSQL, MySQL, SQL Server, CUBRID —
  see [README](../README.md#choosing-a-database)

Client
------

* A recent evergreen browser (Chrome/Firefox/Safari/Edge) is assumed. Unlike legacy, there's no
  documented minimum-version compatibility matrix (legacy pinned specific old versions like IE10+,
  Chrome 30+) — this just hasn't been tested/declared for the port yet.
* Git v1.6.6 or later (if using the Git repository feature)
