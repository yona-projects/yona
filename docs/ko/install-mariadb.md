MariaDB 설치
===

권장 MariaDB 버전은 10.2 10.3 입니다. (10.4는 현재 오류 확인 중입니다)

1. Linux 
   - 배포본의 저장소 및 설치 스크립트는 다음 선택 페이지의 설명을 참고하여 설치 합니다.
   - [Setting up MariaDB Repositories](https://downloads.mariadb.org/mariadb/repositories/)
  
2. Mac
   - brew install 을 이용해서 설치를 권장합니다.
   - https://mariadb.com/blog/installing-mariadb-10010-mac-os-x-homebrew

3. Windows
   - https://downloads.mariadb.org/mariadb/10.1.11/#os_group=windows


이하는 Linux/Unix 기반의 설명입니다. Windows OS 유저는 [MariaDB 설치 (Windows)](https://github.com/yona-projects/yona/wiki/MariaDB-%EC%84%A4%EC%B9%98-%28Windows%29) 를 참고해서 진행해주세요.

##### DB 설치후 유저 및 Database 생성 

기본 진행 내용은 MariaDB에 root 유저로 접속한 다음 yona 유저를 만들고 DB를 만들고 해당 DB의 모든 권한을 yona 유저에게 주는 작업입니다.

MariaDB root 유저로 접속
```
mysql -uroot 
```

만약 클라이언트로 접속 시도시에 아래와 같은 에러가 발생했다면
```
ERROR 2002 (HY000): Can't connect to local MySQL server through socket '/var/lib/mysql/mysql.sock' (2)
```
MariaDB 서버가 실행되지 않았기때문입니다.
이럴때는 OS에 맞게 systemd 등을 이용해 실행해 주세요

```
# CentOS 예
sudo systemctl enable mariadb
sudo systemctl start mariadb
```

yona 유저 생성. password는 IDENTIFIED BY 다음에 지정한 문자가 됩니다. 아래 예)에서는 yonadan
```
create user 'yona'@'localhost' IDENTIFIED BY 'yonadan';
```

DB 생성 UTF8 확장문자열을 저장할 수 있는 포맷으로 지정해서 생성합니다.

```
create database yona
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_bin
;
```

yona 유저에게 yona 데이터베이스 권한 부여

```
GRANT ALL ON yona.* to 'yona'@'localhost';
```

`exit`명령어로 쉘로 빠져 나온 다음 yona 유저로 정상 접속되고 yona DB가 사용 가능한지 확인해 봅니다.
참고로 -p 다음에 쓴 글자가 위에서 만든 패스워드입니다.

```
mysql -u yona -p'yonadan'
use yona
```

/etc/my.cnf 파일을 만들어서 아래 내용을 추가해 주세요. 
(mac os 유저의 경우에는 db 실행유저의 ~/.my.cnf에 아래 내용을 추가해 주세요)
샘플참고: https://github.com/yona-projects/yona/blob/next/support-script/mariadb/my.cnf

- Unicode 4Byte 확장 문자열을 지원하기 위해 utf8mb4를 사용하도록 되어 있습니다.
- lower_case_table_name=1 는 테이블명이나 컬럼명 조회시 대소문자를 구분하지 않도록 만드는 옵션입니다.
- collation-server 는 정렬시의 기준옵션을 설정하는 부분입니다.

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

꼭 /etc 아래가 아니더라도 [my.cnf 위치 탐색순서](https://mariadb.com/kb/en/mariadb/configuring-mariadb-with-mycnf/) 를 보고 적당한 곳에 my.cnf 파일을 만들어서 넣어도 무방하다고 알려져 있습니다. (Mac OS 유저는 우선은 위 설명대로 해주세요. 추가 확인이 필요합니다)

이어서 설정을 적용하기 위해 MariaDB 를 재시작해주세요.

```
service mysql restart
```

이제 Yona 를 설치합니다.

[Install Yona Server](install-yona-server.md)


-- 이하 내용은 참고용입니다 -- 

### 만약 DB관련 작업을 한 후 정상적으로 페이지가 뜨지 않을 경우 아래 항목을 확인해 주세요.

- application.conf 가 제대로 읽히는지
- application.conf 파일내의 application.secret 항목 적용여부
- db.default.url 확인 

### MariaDB 재시작 방법
DB를 설치한 유저로 DB를 재시작합니다. (root나 sudo 설치했을 경우 명령어 앞에 sudo를 붙여주세요)
```
service mysql restart

혹은

/etc/init.d/mysql restart

혹은

mysql.server restart
```
참고: http://coolestguidesontheplanet.com/start-stop-mysql-from-the-command-line-terminal-osx-linux/

