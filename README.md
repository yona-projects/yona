nforge4
=======
[![Build Status](https://travis-ci.org/nforge/nforge4.png?branch=master)](https://travis-ci.org/nforge/nforge4)

협업 개발 플랫폼


nforge 소개
--
nFORGE는 소프트웨어 개발에 필요한 기능들을 사용하기 편리하게 웹으로 묶은 협업 개발 플랫폼입니다. 버그나 이슈을 관리할 수 있는 버그 트래커, 각종 문서와 정보를 간편하게 공유할 수 있는 위키, 소스코드의 변경내역을 편리하게 관리할 수 있는 형상관리 툴, 일반적인 용도의 게시판, 그리고 최종 작업 결과물을 공유하기 위한 파일 릴리즈 기능 등을 포함하고 있습니다.

라이센스
--
nFORGE는 GNU GPL v2로 배포되는 오픈소스입니다.

## 설치하기

### playframework 내려 받기
    
    curl -O http://downloads.typesafe.com/play/2.1.0/play-2.1.0.zip

혹은

    wget http://downloads.typesafe.com/play/2.1.0/play-2.1.0.zip

### 압출풀기

    unzip play-2.1.0.zip

### 압축을 푼 다음 하위 디렉터리로 이동

    cd play-2.1.0

### nforge 소스 내려 받기

    git clone https://github.com/nforge/nforge4.git

### clone 받은 nforge4 디렉터리로 이동

    cd nforge4

### 상단에 있는 play 실행파일 실행

    ../play

실행하면 필요한 파일들을 web에서 내려받는다. 

### 콘솔이 뜨면 run 명령어로 기동
    
    run

필요한 파일들을 web에서 내려받고 컴파일 한다.

### 브라우저로 접속

    http://127.0.0.1:9000
