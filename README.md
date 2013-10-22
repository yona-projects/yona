<a name="english"></a>
[[한국어]](#korean)
Yobi
=======
[![Build Status](https://travis-ci.org/nforge/yobi.png?branch=master)](https://travis-ci.org/nforge/yobi)


Yobi, collaborative SW development platform.<br/>(Currently, 0.3 - work in progress)


What is Yobi?
--

Yobi, a brand new version of nFORGE, is a web-based collaborative platform for software development.
Yobi offers many features to increase productivity and quality of your software: a issue tracker to manage bugs and issue, a wiki style board to share documents, a configuration management tool to control software version and so on.

License
--
Copyright 2013 NAVER Corp, under the Apache 2.0 license.

## Installation

### check java version

    java -version

Required minimum java version is 7(1.7)

### download playframework

    curl -O http://downloads.typesafe.com/play/2.1.0/play-2.1.0.zip

or

    wget http://downloads.typesafe.com/play/2.1.0/play-2.1.0.zip

### unzip

    unzip play-2.1.0.zip

### cd to unzipped directory

    cd play-2.1.0

### download yobi

    git clone https://github.com/nforge/yobi.git

You can also make your own yobi directory in any other place. But in that case, you should add playframework home path to $PATH environment.


### cd to cloned yobi directory

    cd yobi


### run play framework

    ../play

Required files will be download automatically. In the first time, it may take about 10 min or more.


### type run command in console

    run

It will downloaded addtional files and compile sources.

If you want to run yobi in production mode, use **start** , not **run**.

### connect with browser

    http://127.0.0.1:9000

If you want to change port, check your permission to use 80 port

see [http://www.playframework.com/documentation/2.1.1/Production](http://www.playframework.com/documentation/2.1.1/Production)

<br/>
<br/>
<br/>

<a name="korean"></a>
# for korean
[[English]](#english)

Yobi
===========

협업 개발 플랫폼 (현재 개발중이며 아직 공식 버전 넘버링이 안 된 상태입니다.)

Official Site: [http://yobi.io](http://yobi.io)

Yobi 소개
--
Yobi (구 nFORGE)는 소프트웨어 개발에 필요한 기능들을 사용하기 편리하게 웹으로 묶은 협업 개발 플랫폼입니다. 버그나 이슈을 관리할 수 있는 이슈 트래커, 각종 문서와 정보를 간편하게 공유할 수 있는 게시판, 소스코드의 변경내역을 편리하게 관리할 수 있는 형상관리 툴을 비롯하여 팀 개발을 위한 다양한 기능을 포함하고 있습니다.


라이센스
--
Yobi는 Apache 2.0 라이선스로 제공됩니다.

## 설치하기

### java version 확인

    java -version

java 7(1.7) 이상이어야 합니다.

### playframework 내려 받기

    curl -O http://downloads.typesafe.com/play/2.1.0/play-2.1.0.zip

혹은

    wget http://downloads.typesafe.com/play/2.1.0/play-2.1.0.zip

### 압축풀기

    unzip play-2.1.0.zip

### 압축을 푼 다음 하위 디렉터리로 이동

    cd play-2.1.0

### yobi 소스 내려 받기

    git clone https://github.com/nforge/yobi.git

### clone 받은 yobi 디렉터리로 이동

    cd yobi

### 상단에 있는 play 실행파일 실행

    ../play

실행하면 필요한 파일들을 web에서 내려받습니다. 첫 실행시 네트워크 상황에 따라 10여분 가까이 소요될 수 있습니다.

### 콘솔이 뜨면 run 명령어로 기동

    run

추가로 필요한 파일들을 web에서 내려받은 다음 소스 파일들을 컴파일 후 개발 모드로 실행합니다.
운영 모드(production mode)로 실행하고자 할 경우에는 **run** 명령어 대신에 **start** 명령어로 실행합니다.

### 브라우저로 접속

    http://127.0.0.1:9000

80 포트 등으로 포트를 변경하고 싶을 경우에는 해당 포트가 사용가능한지 확인 한 다음 80 포트를 사용할 수 있는 계정으로 실행합니다.
관련해서는 [http://www.playframework.com/documentation/2.1.1/Production](http://www.playframework.com/documentation/2.1.1/Production) 부분을 확인해 주세요.
