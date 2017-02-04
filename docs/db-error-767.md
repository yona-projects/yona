### MariaDB 767 byte Error

```
[info] play - database [default] connected at jdbc:mysql://127.0.0.1:3306/yona 
[error] play - Specified key was too long; max key length is 767 bytes [ERROR:1071, SQLSTATE:42000] 
Oops, cannot start the server. 
@6p6j2gap7: Database 'default' is in an inconsistent state!
```

If you see this error, check following two options are properly apllied.
```
innodb_file_format=barracuda
innodb_large_prefix=on
```

Add the above to my.cnf and start the DB.

After restarting, connect as root user
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
Make sure that the items above are on and that the file format is also barracuda.
