### MariaDB 767 byte 에러

```
[info] play - database [default] connected at jdbc:mysql://127.0.0.1:3306/yona 
[error] play - Specified key was too long; max key length is 767 bytes [ERROR:1071, SQLSTATE:42000] 
Oops, cannot start the server. 
@6p6j2gap7: Database 'default' is in an inconsistent state!
```

이런 에러가 나오는 경우는 아래 두 가지 옵션이 정상적으로 반영되지 않아서 입니다.
```
innodb_file_format=barracuda
innodb_large_prefix=on
```

위 내용은 my.cnf 에 추가한다음 DB를 시작해 주세요.
재시작후 root 유저로 접속해서 
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
처럼 on 되어 있고 file format도 barracuda로 되어 있는지 확인해 보세요.

기타 관련해서는 [이슈 #924](https://github.com/naver/yobi/issues/924)을 참고해 주세요