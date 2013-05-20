HIVE
=======
[![Build Status](https://travis-ci.org/nforge/hive.png?branch=master)](https://travis-ci.org/nforge/hive)

협업 개발 플랫폼 (현재 개발중이며 아직 공식 버전 넘버링이 안 된 상태입니다.)


HIVE 소개
--
HIVE (구 nFORGE)는 소프트웨어 개발에 필요한 기능들을 사용하기 편리하게 웹으로 묶은 협업 개발 플랫폼입니다. 버그나 이슈을 관리할 수 있는 버그 트래커, 각종 문서와 정보를 간편하게 공유할 수 있는 위키, 소스코드의 변경내역을 편리하게 관리할 수 있는 형상관리 툴, 일반적인 용도의 게시판, 그리고 최종 작업 결과물을 공유하기 위한 파일 릴리즈 기능 등을 포함하고 있습니다.

<img src="https://raw.github.com/nforge/hive/master/docs/hive_front.png">
*개발중인 화면입니다*

라이센스
--
HIVE는 GNU GPL v2로 배포되는 오픈소스입니다. (MIT 라이센스로 변경될 예정입니다)

## 설치하기

### java version 확인

    java -version

java 7(1.7) 이상이어야 합니다.

### playframework 내려 받기
    
    curl -O http://downloads.typesafe.com/play/2.1.0/play-2.1.0.zip

혹은

    wget http://downloads.typesafe.com/play/2.1.0/play-2.1.0.zip

### 압출풀기

    unzip play-2.1.0.zip

### 압축을 푼 다음 하위 디렉터리로 이동

    cd play-2.1.0

### hive 소스 내려 받기

    git clone https://github.com/nforge/hive.git

### clone 받은 hive 디렉터리로 이동

    cd hive

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
