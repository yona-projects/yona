<a name="korean"></a>
[[English]](#english)

[![Build Status](https://travis-ci.org/yona-projects/yona.svg?branch=master)](https://travis-ci.org/yona-projects/yona)
Yona
====

21세기 협업 개발 플랫폼

DEMO: [http://yona.io](http://yona.io)

Yona?
--
Yona는 팀이 개발을 진행하는데 그 속도를 높이고 효율을 증가시키기 위해 만들어졌습니다.

- 이슈 트래커: 기간이나 마일스톤과 함께 업무를 관리할 수 있는 이슈 트래커
  - 특히 `내 이슈` 기능은 업무처리를 통합된 화면에서 보고, 해야 할 일 / 언급된 일 등에 집중해서 쉽고 편하게 관리할 수 있습니다.
- 게시판: 각종 문서나 파일을 쉽게 공유할 수 있는 게시판
- 코드 저장소: 코드 개발에 필요한 Git / SVN 저장소 기능
- 코드 주고받기(Pull request): 협업 개발을 위한 코드 주고받기(Pull request)
- 코드 리뷰: 블럭 단위로 코멘트를 남길 수 있으며 리뷰 진행 여부를 확인 가능한 강력한 코드 리뷰 기능
- 그룹(Organization): 일정 멤버들이 여러개의 프로젝트를 그룹으로 관리 수 있게 도와주는 강력한 그룹(Organization) 기능
- 메일 연동: 각종 이벤트들을 설정에 따라 메일로 받을 수 있으며 또한 메일로 이슈나 댓글을 등록할 수 있습니다.

등을 비롯하여 일상적인 업무에서 개발 전반에 이르는 다양한 기능을 포함하고 있습니다.

왜 Yona를 써야 하나? (Why Yona?)
---
[왜 Yona를 써야 하나? (Why Yona?)](https://repo.yona.io/yona-projects/yona/post/3)를 참고해 주세요

라이선스
--
Yona는 Apache 2.0 라이선스로 제공됩니다.


Yona 설치 방법
===

Yobi에서 Yona로 업그레이드 하려는 경우
---
1. 하단의 설치방법을 참고해서 Yona를 설치 합니다.
2. [Yobi 에서 Yona로 Migration 하는 방법](https://repo.yona.io/yona-projects/yona/post/1)을 참고해서 마이그레이션을 진행합니다.

Yona 설치
---

Yona는 기본적으로 다음 2단계로 설치됩니다.

- MariaDB설치
- Yona 설치

Yona는 앞으로 기본DB를 MariaDB를 사용할 예정입니다. 다만 Yona 1.0 기준으로 yona-h2.zip 을 따로 제공합니다.
[yona-1.0-h2.zip](https://github.com/yona-projects/yona/releases/tag/v1.0.0) MariaDB로 변경이 어렵거나 간편하게 설치해서 사용하실때는 이쪽을 이용해 주세요.

관련해서는 다음 링크를 참고해 주세요
[Yona가 MariaDB를 기본 DB로 사용하게 된 이유](https://repo.yona.io/yona-projects/yona/post/4)

#### MariaDB 설치

아래 설명은 진행 과정만 참고만 하시고 실제로는 MariaDB 10.1.10 이상을 설치해 주세요

1. Linux 
   - 배포본의 저장소 및 설치 스크립트는 다음 선택 페이지의 설명을 참고하여 설치 합니다.
   - [Setting up MariaDB Repositories](https://downloads.mariadb.org/mariadb/repositories/)
  
2. Mac
   - brew install 을 이용해서 설치를 권장합니다.
   - https://mariadb.com/blog/installing-mariadb-10010-mac-os-x-homebrew

3. Windows
   - https://downloads.mariadb.org/mariadb/10.1.11/#os_group=windows

##### DB 설치후 유저 및 Database 생성 

기본 진행 내용은 MariaDB에 root 유저로 접속한 다음 yona 유저를 만들고 DB를 만들고 해당 DB의 모든 권한을 yona 유저에게 주는 작업입니다.

MariaDB root 유저로 접속
```
mysql -uroot 
```

yona 유저 생성. password는 IDENTIFIED BY 다음에 지정한 문자가 됩니다. 아래 예)에서는 yonadan
```
create user 'yona'@'localhost' IDENTIFIED BY 'yonadan';
```

DB 생성 UTF8 확장문자열을 저장할 수 있는 포맷으로 지정해서 생성합니다.

```
set global innodb_file_format = BARRACUDA;
set global innodb_large_prefix = ON;

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
mysql -uyona -p'yonadan'
use yona
```

/etc/my.cnf 파일을 만들어서 아래 내용을 추가해 주세요. (windows 나 mac os 에서는 작업하지 않아도 무방합니다)

```
[mysqld]
collation-server = utf8mb4_unicode_ci
lower_case_table_names=1
```

꼭 /etc 아래가 아니더라도 [my.cnf 위치 탐색순서](https://mariadb.com/kb/en/mariadb/configuring-mariadb-with-mycnf/) 를 보고 적당한 곳에 my.cnf 파일을 만들어서 넣어도 무방합니다. 


DB를 설치한 유저로 DB를 재시작합니다. (root나 sudo 설치했을 경우 명령어 앞에 sudo를 붙여주세요)
```
service mysql restart

혹은

/etc/init.d/mysql restart

혹은

mysql.server restart
```
참고: http://coolestguidesontheplanet.com/start-stop-mysql-from-the-command-line-terminal-osx-linux/

DB가 정상적으로 재시작되었으면 이제 Yona 를 설치합니다. 

#### Yona 설치

Yona 최신 버전을 https://github.com/yona-projects/yona/releases 에서 다운받아 압축을 풉니다. 
wget으로 받아서 unzip으로 압축을 푼다면 미리 다운로드 링크 주소를 확인한 다음 내려받습니다.

예)

    wget https://github.com/yona-projects/yona/releases/download/v1.0.0/yona.zip
    unzip yona.zip

### application.conf 파일등 생성하기

압축이 풀린 곳으로 이동해서 bin/yona 을 실행합니다. (Java 8 이상이 필요합니다)
**주의**: 아래와 같이 yona 설치 폴더에서 실행하여야 합니다. 또한 윈도우 사용자는 bin/yona 대신 bin/yona.bat을 실행해야 합니다.

```
cd yona
bin/yona
```

실행하면 패스워드가 틀렸다는 에러와 함께 실행이 종료 될겁니다. 이제 압축을 풀었을때는 안 보였던 conf 디렉터리가 보일 겁니다. 

#### DB 설정 수정

앞서 설치한 MariaDB에 맞게 DB 연결 설정을 수정해야 합니다.

conf 폴더 아래의 application.conf 파일에서 아래 부분에서 password를 위에서 설정한 password로 수정해 주세요
```
...
db.default.driver=org.mariadb.jdbc.Driver
db.default.url="jdbc:mariadb://127.0.0.1:3306/yona?useServerPrepStmts=true"
db.default.user=yona
db.default.password="yonadan"
...
```

`yonadan`은 예시일뿐 그대로 사용하지 않는 걸 권장합니다. 

#### 실행

압축이 풀린 디렉터리로 이동해서 다시 yona를 실행합니다.

    cd yona
    bin/yona


이제 웹 브라우저로 http://127.0.0.1:9000 에 접속하면 환영 페이지를 보실 수 있습니다. 
어드민 설정을 마치고 다시 쉘을 시작합니다.


#### 업그레이드

설치할 때와 똑같이, 최신 버전을 내려받아 Yona가 설치된 디렉터리에 압축파일을
풉니다.

**주의사항! `repo`와 `uploads` 디렉터리를 삭제하거나
덮어쓰지 않도록 주의하세요!**


### 소스코드를 직접 내려 받아서 빌드 실행하기

자신의 입맛에 맛게 코드를 직접 수정해서 작업하거나 코드를 기여하고 싶을 경우에는 코드 저장소로부터 코드를 직접 내려받아서 빌드/실행하는 것도 가능합니다.

[소스코드를 직접 내려 받아서 실행하기](https://repo.yona.io/yona-projects/yona/post/5)를 참고해 주세요


### 옵션

[간단한 재시작 쉘 예제](https://github.com/yona-projects/yona/blob/next/restart.sh)

#### Linux, OSX의 경우

`YONA_HOME` 속성을 통해 Yona가 데이터, 데이터베이스, 설정파일을 불러오고 저장할
홈 디렉터리를 지정할 수 있습니다. 예를 들어, /home/user/.yona를 홈 디렉터리로
사용하려면 Yona를 시작할 때 다음과 같이 지정합니다.

    YONA_HOME=/home/user/.yona bin/yona

`_JAVA_OPTIONS` 환경변수를 이용해 자바 환경 변수를 지정할 수도 있습니다. 시스템
메모리가 4기가 이상이라면, 다음과 같은 옵션으로 실행하는걸 권장합니다.

    _JAVA_OPTIONS="-Xmx2048m -Xms2048m" bin/yona

기본적으로 9000번 포트를 사용하지만, 다른 포트를 사용하고 싶다면 http.port 자바
환경변수를 수정합니다.

    _JAVA_OPTIONS="-Dhttp.port=80" bin/yona

#### Windows의 경우

Yona를 시작할 때, 데이터 디렉터리, 설정파일, 로그 설정파일의 위치를 각각
yona.home, config.file, logger.file 자바 프로퍼티로 지정할 수 있습니다.

    SET JAVA_OPTS=-Dyona.home=/home/user/.yona -Dconfig.file=/home/user/.yona/conf/application.conf -Dlogger.file=/home/user/.yona/conf/application-logger.xml
    bin\yona.bat

`_JAVA_OPTIONS` 환경변수를 이용해 자바 환경 변수를 지정할 수도 있습니다. 시스템
메모리가 4기가 이상이라면, 다음과 같은 옵션으로 실행하는 걸 권장합니다.

    SET JAVA_OPTS=-Xmx2048m -Xms2048m
    bin\yona.bat

기본적으로 9000번 포트를 사용하지만, 다른 포트를 사용하고 싶다면 http.port 자바
환경변수를 수정합니다.

    _JAVA_OPTIONS=-Dhttp.port=80
    bin\yona.bat

업그레이드를 하는 경우 다음과 같이 데이터베이스 스키마 마이그레이션이
필요하다는 경고 메시지와 함께 실행되지 않는 상황을 겪을 수 있습니다.

    [warn] play - Your production database [default] needs evolutions!

그런 경우에는 자동으로 마이그레이션이 되도록 다음과 같이
applyEvolutions.default 자바 프로퍼티를 true로 설정합니다.

    SET JAVA_OPTS=-DapplyEvolutions.default=true
    bin\yona.bat

#### 옵션에 대한 더 자세한 설명

[http://www.playframework.com/documentation/2.3.6/Production](http://www.playframework.com/documentation/2.3.6/Production) 부분을 확인해 주세요.

### 백업하기

DB 백업은 https://mariadb.com/kb/en/mariadb/backup-and-restore-overview/ 를 참고해 주세요

코드 저장소의 파일과 업로드 파일은 아래 디렉터리에 저장됩니다. 

    directory: repo, uploads

주기적으로 두 디렉터리는 따로 잘 백업해 주세요. 


### DB관련 작업을 한 후 정상적으로 페이지가 뜨지 않을 경우 아래 항목을 확인해 주세요.

- application.conf 가 제대로 읽히는지
- application.secret 적용여부
- db.default.url 확인 

기타 관련해서는 [이슈 #924](https://github.com/naver/yobi/issues/924)을 참고해 주세요

<br/>
<br/>
<br/>

<a name="english"></a>
[[한국어]](#korean)
Yona
=======

Yona is a web-based project hosting software.


What is Yona?
--

Yona is a web-based project hosting software.
To increase productivity and quality of your software Yona offers many features including

- Issue tracker to manage bugs and issues
- Bulletin board to share documents
- Git/SVN support embedded
- Pull-request for collaborative coding

License
--
Copyright 2015 NAVER Corp. under the Apache License, Version 2.0

How to install
--------------

### Install from binary

#### Install

Download the latest version of Yona from http://yona.io/yona.zip and unzip it.
If you have wget and unzip:

    wget http://yona.io/yona.zip
    unzip yona.zip

#### Start

Go the directory and start Yona. If the directory is yona-1.0.0:

    cd yona-1.0.0
    bin/yona

**Note**: If you are using Windows, run "bin/yona.bat" instead of "bin/yona".

Open http://127.0.0.1:9000 with your web browser then you can see the welcome
page.

#### Upgrade

Download the latest version of Yona and unzip it.

**Note: Don't overwrite or delete `yona.h2.db` file, `repo` & `uploads` directory!**

### Build from source

#### Check JDK version

    java -version
    javac -version

JDK version 7(1.7) or 8(1.8) is required.

#### Download Play Activator

    curl -O http://downloads.typesafe.com/typesafe-activator/1.2.10/typesafe-activator-1.2.10-minimal.zip

or

    wget http://downloads.typesafe.com/typesafe-activator/1.2.10/typesafe-activator-1.2.10-minimal.zip

or using web browser (for windows)

    http://downloads.typesafe.com/typesafe-activator/1.2.10/typesafe-activator-1.2.10-minimal.zip

#### Unzip

    unzip typesafe-activator-1.2.10-minimal.zip

#### Change directory to unzipped directory

    cd activator-1.2.10-minimal

#### Download Yona

Case1. Using [git client](http://git-scm.com/) (recommended)
    
    git clone https://github.com/yona-projects/yona.git

or 

Case2. Just download the latest stable release
If you want to download one of the stable releases, you can download a compressed file by clicking the URL below. And then name it a yona and unzip it.

    https://github.com/yona-projects/yona/archive/master.zip
    
**Caution! In case2, You might come across troubles when you try to upgrade Yona.**

> You can locate your own Yona directory in any other place. Please note that you must add Play Activator home path to $PATH environment in that case.

#### Change directory to cloned Yona directory (or cd to your unzipped file directory)

    cd yona

#### Run Play Activator

    ../activator

or (for windows)

    ..\activator

Required files will be download automatically. In the first time, it may take about 10 min or more.


#### Type start command in console

    start

It will download additional files and compile sources.

If you want to run Yona in development mode, use **run**. You can see more detailed errors and can use dynamic compilation.

#### Connect with browser

    http://127.0.0.1:9000

If you want to change port, check your permission to use 80 port. See 'Options' section for more information.

#### Upgrade Yona

Case1. Using git client (recommended)
In installed directory, just type git update command.

    git pull https://github.com/yona-projects/yona.git master

Case2. Download zip file

In installed directory, download the latest release file and unzip it.

    https://github.com/yona-projects/yona/archive/master.zip

**Be careful! Don't overwrite or delete `yona.h2.db` file, `repo` & `uploads` directory!**

**If you are installing Yona in Windows system, you may need set `applyEvolutions.default` Java property to true. See 'Options' section for more information**

### Options

When start yona, You can specify the home directory to contain data for Yona.
If you want to use `/home/user/.yona` as the home directory, set 'yona.home'
property as follows:

    bin/yona -Dyona.home=/home/user/.yona

Note: Yona doesn't load the configuration files from the home directory. If you
want to do that, specify the path to the config files as follows:

    bin/yona -Dyona.home=/home/user/.yona -Dconfig.file=/home/user/.yona/conf/application.conf -Dlogger.file=/home/user/.yona/conf/application-logger.xml

You can also specify Java options with `_JAVA_OPTIONS` environment variable. If
the memory of your system equals to or greater than 4GB, we recommend to start
Yona as follows:

    _JAVA_OPTIONS="-Xmx2048m -Xms2048m" activator "start -DapplyEvolutions.default=true -Dhttp.port=9000"

#### Linux and OSX

When start yona, you can specify the home directory to contain data, the
database file and configuration files for Yona. For example, if you want to
use `/home/user/.yona` as the home directory, set YONA_HOME as follows:

    YONA_HOME=/home/user/.yona bin/yona

You can also specify Java options with `_JAVA_OPTIONS` environment variable. If
the memory of your system equals to or greater than 4GB, we recommend to start
Yona as follows:

    _JAVA_OPTIONS="-Xmx2048m -Xms2048m" bin/yona

Yona use 9000 port for HTTP connection by default. If you want to use another
port, set http.port Java property.

    _JAVA_OPTIONS="-Dhttp.port=80" bin/yona

#### Windows

When start yona, you can specify the data directory, the config file, the
config file for logger with yona.home, config.file and logger.file as follows:

    SET JAVA_OPTS=-Dyona.home=/home/user/.yona -Dconfig.file=/home/user/.yona/conf/application.conf -Dlogger.file=/home/user/.yona/conf/application-logger.xml
    bin\yona.bat

You can specify the database file in `application.conf` as follows:

    db.default.url="jdbc:h2:file:/home/nori/.yona/yona"

You can also specify Java options with `_JAVA_OPTIONS` environment variable. If
the memory of your system equals to or greater than 4GB, we recommend to start
Yona as follows:

    SET JAVA_OPTS=-Xmx2048m -Xms2048m
    bin\yona.bat

Yona use 9000 port for HTTP connection by default. If you want to use another
port, set http.port Java property.

    _JAVA_OPTIONS=-Dhttp.port=80
    bin\yona.bat

If you are upgrading Yona from the previous version, the upgrade may fail with
a warning message that says you need migration as follows:

    [warn] play - Your production database [default] needs evolutions!

In such case, set `applyEvolutions.default` Java property to true and restart
Yona.

    SET JAVA_OPTS=-DapplyEvolutions.default=true
    bin\yona.bat

#### For more information about options

See [http://www.playframework.com/documentation/2.3.6/Production](http://www.playframework.com/documentation/2.3.6/Production).

### Backup

Copy the below file and directories to another place.

    file: yona.h2.db
    directory: repo, uploads
