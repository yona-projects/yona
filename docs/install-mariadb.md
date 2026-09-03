MariaDB Installation
===

Ported from legacy Yona's `docs/install-mariadb.md`, adapted for yona. Legacy recommended
MariaDB 10.2/10.3; yona's `docker-compose.yml` ships `mariadb:10.11`, so most of this is only
needed if you install MariaDB yourself instead of using Docker.

Fastest path (local development)
---

```bash
docker compose up -d mariadb
```

This creates the `yona` database/user/password (`yona`/`yona_password`) automatically — none of
the manual steps below are needed.

Installing MariaDB yourself
---

1. Linux
   - [Setting up MariaDB Repositories](https://downloads.mariadb.org/mariadb/repositories/)
2. Mac
   - `brew install mariadb@10.11` (or newer) recommended
   - https://mariadb.com/blog/installing-mariadb-10010-mac-os-x-homebrew
3. Windows
   - https://downloads.mariadb.org/mariadb/repositories/

##### Create user and database after installing DB

Connect to MariaDB as root:

```
mysql -uroot
```

Create the `yona` user and set a password ('yonadan' below is just an example — change it):

```sql
create user 'yona'@'localhost' IDENTIFIED BY 'yonadan';
```

Create the database with a format that supports UTF8 extended characters:

```sql
create database yona
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_bin
;
```

Grant privileges:

```sql
GRANT ALL ON yona.* to 'yona'@'localhost';
```

Exit and check that the `yona` user can connect and the `yona` database is available (the
letter after `-p` is the password created above):

```
mysql -u yona -p'yonadan'
use yona
```

Tuning: create a `/etc/my.cnf` (or `~/.my.cnf` on Mac) with the settings below. A ready-made
sample lives at [`support-script/mariadb/my.cnf`](../support-script/mariadb/my.cnf) —
**note it intentionally omits `innodb_file_format`/`innodb_large_prefix`, which legacy's sample
had.** Those two options are deprecated since MariaDB 10.2 and MariaDB 10.6+ refuses to start
at all if they're set. See [`db-error-767.md`](db-error-767.md) for the background.

```ini
[client]
default-character-set=utf8mb4

[mysql]
default-character-set=utf8mb4

[mysqld]
init-connect='SET NAMES utf8mb4'
lower_case_table_names=1
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

# skip client char-set
skip-character-set-client-handshake
```

Also see [configuring-mariadb-with-mycnf](https://mariadb.com/kb/en/mariadb/configuring-mariadb-with-mycnf/).

Then restart MariaDB to apply the settings:

```
service mysql restart
```

Now you're ready to install yona — see [`install-yona-server.md`](install-yona-server.md).

-- The following is for reference only --

### If the application doesn't come up properly after working on the DB

Check in `src/main/resources/application.yml`:
- the active DB profile's `spring.datasource.url`/`username`/`password`

### MariaDB Restart

```
service mysql restart

or

/etc/init.d/mysql restart

or

mysql.server restart
```

See: http://coolestguidesontheplanet.com/start-stop-mysql-from-the-command-line-terminal-osx-linux/
