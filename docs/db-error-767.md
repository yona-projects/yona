### MariaDB 767 byte Error

```
[info] play - database [default] connected at jdbc:mysql://127.0.0.1:3306/yona
[error] play - Specified key was too long; max key length is 767 bytes [ERROR:1071, SQLSTATE:42000]
Oops, cannot start the server.
@6p6j2gap7: Database 'default' is in an inconsistent state!
```

Ported from legacy Yona's `docs/db-error-767.md`. If you see this error, check that these two
options are properly applied:

```ini
innodb_file_format=barracuda
innodb_large_prefix=on
```

Add them to `my.cnf` and restart the DB. After restarting, connect as root:

```
MariaDB [(none)]> SHOW VARIABLES LIKE 'innodb_lar%';
+---------------------+-------+
| Variable_name       | Value |
+---------------------+-------+
| innodb_large_prefix | ON    |
+---------------------+-------+

MariaDB [(none)]> SHOW VARIABLES LIKE 'innodb_file%';
+--------------------------+-----------+
| Variable_name            | Value     |
+--------------------------+-----------+
| innodb_file_format       | Barracuda |
| innodb_file_format_check | ON        |
| innodb_file_format_max   | Barracuda |
| innodb_file_per_table    | ON        |
+--------------------------+-----------+
```

Make sure both are on and the file format is Barracuda.

**This is legacy-only advice.** yona's shipped `docker-compose.yml` uses `mariadb:10.11`, where
Barracuda / dynamic row format has been the default since MariaDB 10.2 — this error doesn't
reproduce there, and setting `innodb_file_format`/`innodb_large_prefix` on MariaDB 10.6+ actually
**prevents the server from starting**, since those variables were removed. Only apply the
settings above if you're running a pre-10.2 MariaDB yourself; otherwise upgrading MariaDB is the
real fix. See [`support-script/mariadb/my.cnf`](../support-script/mariadb/my.cnf) for yona's
actual sample config (which omits both options).
