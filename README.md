<a name="english"></a>
[[한국어]](#korean)
Yobi
=======
[![Build Status](https://travis-ci.org/naver/yobi.png?branch=master)](https://travis-ci.org/naver/yobi)

Yobi is a web-based project hosting software.


What is Yobi?
--

Yobi is a web-based project hosting software.
To increase productivity and quality of your software Yobi offers many features including

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

Download the latest version of Yobi from http://yobi.io/yobi.zip and unzip it.
If you have wget and unzip:

    wget http://yobi.io/yobi.zip
    unzip yobi.zip

#### Start

Go the directory and start Yobi. If the directory is yobi-1.0.0:

    cd yobi-1.0.0
    bin/yobi

**Note**: If you are using Windows, run "bin/yobi.bat" instead of "bin/yobi".

Open http://127.0.0.1:9000 with your web browser then you can see the welcome
page.

#### Upgrade

Download the latest version of Yobi and unzip it.

**Note: Don't overwrite or delete `yobi.h2.db` file, `repo` & `uploads` directory!**

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

    cd typesafe-activator-1.2.10-minimal

#### Download Yobi

Case1. using [git client](http://git-scm.com/) (recommended)
    
    git clone https://github.com/naver/yobi.git

or 

Case2. Just download latest stable release
If you want to download one of the stable releases, you can download a compressed file by clicking the URL below. And then name it a yobi and unzip it.

    https://github.com/naver/yobi/archive/master.zip
    
**Caution! in case2, You might come across troubles when you try to upgrade Yobi.**

> You can locate your own Yobi directory in any other place. Please note that you must add Play Activator home path to $PATH environment in that case.

#### Change directory to cloned Yobi directory (or cd to your unzipped file directory)

    cd yobi

#### Run Play Activator

    ../activator

or (for windows)

    ..\activator

Required files will be download automatically. In the first time, it may take about 10 min or more.


#### Type start command in console

    start -DapplyEvolutions.default=true -Dhttp.port=9000

It will downloaded addtional files and compile sources.

If you want to run Yobi in development mode, use **run**. You can see more detailed errors and can use dynamic compilation.

#### Connect with browser

    http://127.0.0.1:9000

If you want to change port, check your permission to use 80 port

See [http://www.playframework.com/documentation/2.3.6/Production](http://www.playframework.com/documentation/2.3.6/Production)

#### Upgrade Yobi

Case1. using git client (recommended)
In installed directory, just type git update command.

	git pull https://github.com/naver/yobi.git master

Case2. download zip file

In installed directory, download latest release file and unzip it.

	https://github.com/naver/yobi/archive/master.zip

** Be careful! Don't overwrite or delete `yobi.h2.db` file, `repo` & `uploads` directory! **

### Options

When start yobi, You can specify the home directory that data and configuration
to read from and store into. If you want to use `/home/user/.yobi` as the home
directory, set 'yobi.home' property as follows:

    bin/yobi -Dyobi.home=/home/user/.yobi

You can also specify Java options with `_JAVA_OPTIONS` environment variable. If
the memory of your system equals to or greater than 4GB, we recommend to start
Yobi as follows:

    _JAVA_OPTIONS="-Xmx2048m -Xms1024m" activator "start -DapplyEvolutions.default=true -Dhttp.port=9000"

### Backup

Copy the below file and directories to another place.

	file: yobi.h2.db
	directory: repo, uploads

<br/>
<br/>
<br/>

<a name="korean"></a>
# for korean
[[English]](#english)

Yobi
===========

협업 개발 플랫폼

Official Site: [http://yobi.io](http://yobi.io)

Yobi 소개
--
Yobi (구 nFORGE)는 협업 개발을 위한 프로젝트 호스팅 SW입니다. 

- 버그나 이슈을 관리할 수 있는 이슈 트래커
- 각종 문서와 정보를 간편하게 공유할 수 있는 게시판
- 소스코드의 변경내역을 편리하게 관리할 수 있는 형상관리 도구 git/svn 기본 내장
- 협업개발을 위한 코드 주고받기

등을 비롯하여 팀 개발을 위한 다양한 기능을 포함하고 있습니다.


라이선스
--
Yobi는 Apache 2.0 라이선스로 제공됩니다.

## 설치하기

### 다운받아 설치하기

#### 설치

Yobi 최신 버전(http://yobi.io/yobi.zip)을 다운받아서 압축을 풉니다. 예를 들어
wget으로 받아서 unzip으로 압축을 푼다면:

    wget http://yobi.io/yobi.zip
    unzip yobi.zip

#### 실행

압축이 풀린 디렉토리로 이동해서 yobi를 실행합니다. 디렉토리가 yobi-1.0.0 이라면:

    cd yobi-1.0.0
    bin/yobi

**주의**: 윈도우 사용자는 bin/yobi 대신 bin/yobi.bat을 실행해야합니다.

이제 웹 브라우저로 http://127.0.0.1:9000 에 접속하면 환영 페이지를 보실 수 있습니다.

#### 업그레이드

설치할 때와 똑같이, 최신 버전을 내려받아 Yobi가 설치된 디렉터리에 압축파일을
풉니다. **주의사항! `yobi.h2.db` 파일, `repo`와 `uploads` 디렉터리를 삭제하거나
덮어쓰지 않도록 주의하세요!**

### 소스 코드에서 빌드하기

#### JDK version 확인

    java -version
    javac -version

JDK 7(1.7) 혹은 8(1.8) 이어야 합니다.

#### Play Activator 내려 받기

    curl -O http://downloads.typesafe.com/typesafe-activator/1.2.10/typesafe-activator-1.2.10-minimal.zip

혹은

    wget http://downloads.typesafe.com/typesafe-activator/1.2.10/typesafe-activator-1.2.10-minimal.zip

웹 브라우저에서 (이를테면, MS윈도우즈 사용자일 경우)

    http://downloads.typesafe.com/typesafe-activator/1.2.10/typesafe-activator-1.2.10-minimal.zip

#### 압축풀기

    unzip typesafe-activator-1.2.10-minimal.zip

#### 압축을 푼 다음 하위 디렉터리로 이동

    cd typesafe-activator-1.2.10-minimal

#### Yobi 소스 내려 받기

case1. [git 클라이언트](http://git-scm.com)를 이용한 다운로드 (추천)
    
    git clone https://github.com/naver/yobi.git
    
case2. 단순히 최신 안정버전을 내려받고자 할 때는 아래 링크를 이용해서 압축파일을 내려받은 다음 yobi를 폴더이름으로해서 해제합니다.

	git pull https://github.com/naver/yobi.git master
    
주의! case2의 경우, 업그레이드를 할 때 문제가 생길 수 있습니다.

> 임의의 장소에 Yobi 디렉터리를 위치시킬 경우에는 activator 실행파일이 있는 Play Activator 디렉터리를 $PATH 환경변수에 추가해 주세요.

#### clone 받은 Yobi 디렉터리로 이동
(혹은 압축을 해제한 디렉터리로 이동)

    cd yobi

#### 상단에 있는 activator 실행파일 실행

    ../activator

혹은 (윈도우 사용자일 경우)

    ..\activator

실행하면 필요한 파일들을 web에서 내려받습니다. 첫 실행시 네트워크 상황에 따라 10여분 가까이 소요될 수 있습니다.

#### 콘솔이 뜨면 start 명령어로 기동

    start -DapplyEvolutions.default=true -Dhttp.port=9000

추가로 필요한 파일들을 web에서 내려받은 다음 소스 파일들을 컴파일 후 운영 모드(production mode)로 실행합니다.
개발 모드(development mode)로 실행하고자 할 경우에는 **start** 명령어 대신에 **run** 명령어로 실행합니다.

#### 브라우저로 접속

    http://127.0.0.1:9000

80 포트 등으로 포트를 변경하고 싶을 경우에는 해당 포트가 사용가능한지 확인 한 다음 80 포트를 사용할 수 있는 계정으로 실행합니다.
관련해서는 [http://www.playframework.com/documentation/2.3.6/Production](http://www.playframework.com/documentation/2.3.6/Production) 부분을 확인해 주세요.

#### 업그레이드 하기

case1. git 클라이언트를 이용 (추천)
설치된 디렉터리에서, 아래와 같은 git 명령어를 이용합니다

	git pull https://github.com/naver/yobi.git master

case2. 압축파일을 내려받을 경우

설치된 디렉터리에서, 최신 릴리즈의 압축파일을 내려받아 Yobi가 설치된 디렉터리에 압축파일을 풉니다.

	https://github.com/naver/yobi/archive/master.zip

**주의사항! `yobi.h2.db` 파일, `repo`와 `uploads` 디렉터리를 삭제하거나 덮어쓰지 않도록 주의하세요!**

### 옵션

Yobi가 파일을 불러오고 저장할 홈 디렉토리를 `yobi.home` 속성을 통해 지정할
수 있습니다. 예를 들어, /home/user/.yobi를 홈 디렉토리로 사용하려면 Yobi를
시작할 때 다음과 같이 yobi.home 프로퍼티를 지정합니다.

    bin/yobi -Dyobi.home=/home/user/.yobi

`_JAVA_OPTIONS` 환경변수를 이용해 자바 옵션을 지정할 수도 있습니다. 시스템
메모리가 4기가 이상이라면, 다음과 같은 옵션으로 실행하는걸 권장합니다.

    _JAVA_OPTIONS="-Xmx2048m -Xms1024m" activator "start -DapplyEvolutions.default=true -Dhttp.port=9000"

### 백업하기

특별히 외부 DB를 사용하지 않는다면 아래 내용을 잘 백업해서 보관해 주시면 됩니다.

	file: yobi.h2.db
	directory: repo, uploads
