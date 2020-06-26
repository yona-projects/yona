MariaDB Installation
===

Recommended MariaDB version is 10.2 or 10.3. (MariaDB 10.4 is under investigation about the error.)

1. Linux 
   - [Setting up MariaDB Repositories](https://downloads.mariadb.org/mariadb/repositories/)
  
2. Mac
   - Recommed to use `brew install mariadb@10.3`
   - https://mariadb.com/blog/installing-mariadb-10010-mac-os-x-homebrew

3. Windows
   - https://downloads.mariadb.org/mariadb/10.1.11/#os_group=windows

##### Create user and database after installing DB

The basic procedure is to connect to MariaDB as root user, create yona user, create DB, and give all permissions of yona user to DB.

Connect to MariaDB with root
```
mysql -uroot 
```

Create user `yona` and set password. 'yonadan' is just example, so change it.
```
create user 'yona'@'localhost' IDENTIFIED BY 'yonadan';
```

To use UTF8 extended chars, set file format to BARACUDA.

```
create database yona
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_bin
;
```

Grant privileges

```
GRANT ALL ON yona.* to 'yona'@'localhost';
```

Exit to the shell with `exit` command and check that yona DB is available and yona user is connected normally.
Note that the letter after the -p is the password created above.

```
mysql -u yona -p'yonadan'
use yona
```

Please make a /etc/my.cnf file and add the following.

(If you are a mac os user, add the following line to ~/.my.cnf file)
Example: https://github.com/yona-projects/yona/blob/next/support-script/mariadb/my.cnf

- It is supposed to use utf8mb4 to support Unicode 4Byte extension strings.
- `lower_case_table_name=1` is option makes the case of table or column names case insensitive.
- collation-server is criteria options when sorting.

```
# [client]
# default-character-set=utf8mb4

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

Also, see [configuring-mariadb-with-mycnf](https://mariadb.com/kb/en/mariadb/configuring-mariadb-with-mycnf/) 

Then restart MariaDB to apply the settings.

```
service mysql restart
```

Now, let's start to install Yona!


-- The following is for reference only -- 

### If the page does not open properly after working on the DB, check the items below.

application.conf file
- application.secret 
- db.default.url  

### MariaDB Restart
```
service mysql restart

or

/etc/init.d/mysql restart

or

mysql.server restart
```
See: http://coolestguidesontheplanet.com/start-stop-mysql-from-the-command-line-terminal-osx-linux/
