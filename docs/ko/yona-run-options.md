Yona 실행시 추가 가능한 옵션
===

Linux, OSX의 경우
----

### 메모리 할당 증가

`JAVA_OPTS` 환경변수를 이용해 자바 환경 변수를 지정할 수도 있습니다. 시스템
메모리가 4기가 이상이라면, 다음과 같은 옵션으로 사용가능한 메모리를 증가시켜서 실행하는걸 권장합니다.

    JAVA_OPTS="-Xmx2048m -Xms2048m" bin/yona

메모리 부족관련 에러가 발생할 경우 유용합니다.

```
예) yona-run.sh 로 만들어 본 실행 스크립트 작성 예


YONA_DATA=/yona-data;export YONA_DATA
JAVA_OPTS="-Xmx4096m -Xms4096m" bin/yona

```

### 기본 포트 변경

기본적으로 9000번 포트를 사용하지만, 다른 포트를 사용하고 싶다면 http.port 자바
환경변수를 수정합니다.


```
예) yona-run.sh 로 만들어 본 실행 스크립트 작성 예. 80포트를 사용하고 메모리 2G로 동작

YONA_DATA=/yona-data;export YONA_DATA
JAVA_OPTS="-Dhttp.port=80 -Xmx2048m -Xms2048m" bin/yona

```


Windows의 경우
---

Yona를 시작할 때, YONA_DATA 환경변수를 지정해서 아래 명령순서로 실행해주세요!
YONA_DATA의 지정 폴더는 내려받은 실행파일의 위치가 아니라 설정파일 및 첨부파일, 코드 저장소 등이 만들어질 위치를 지정합니다.
관련해서는 Yona 설치 가이드의 [install-yona-server.md#본격적인-첫-화면-보기-위한-실행](install-yona-server.md) 부분을 참고해주세요

```
Windows OS Yona 권장 폴더
C:\yona\yona-1.3.0 <- yona 폴더아래에 버전별로 압축해제 
C:\yona-data <- conf 파일, logs, uploads, repo 폴더가 생성되고 유지되는 곳. YONA_DATA 환경 변수로 지정
```

아래와 같은 내용이 들어가 있는 run.bat을 따로 만드셔도 좋습니다!

```
SET YONA_DATA=c:\yona-data
bin\yona.bat
```

### 메모리 할당 증가

`SET JAVA_OPTS` 환경변수 설정을 이용해 자바 환경 변수를 지정할 수도 있습니다. 시스템
메모리가 4기가 이상이라면, 다음과 같은 옵션으로 실행하는 걸 권장합니다.


```
예) yona-run.bat 파일로 만들어 본 실행 스크립트 작성 예. 메모리 2G 할당
    SET YONA_DATA=c:\yona-data
    SET JAVA_OPTS=-Xmx2048m -Xms2048m
    bin\yona.bat
```

기본적으로 9000번 포트를 사용하지만, 다른 포트를 사용하고 싶다면 http.port 자바
환경변수를 수정합니다.

```
예) yona-run.bat 파일로 만들어 본 실행 스크립트 작성 예. 80포트를 사용하고 메모리 2G로 동작

SET YONA_DATA=c:\yona-data
SET JAVA_OPTS=-Dhttp.port=80 -Xmx2048m -Xms2048m
bin\yona.bat
```

업그레이드를 하는 경우 다음과 같이 데이터베이스 스키마 마이그레이션이
필요하다는 경고 메시지와 함께 실행되지 않는 상황을 겪을 수 있습니다.

    [warn] play - Your production database [default] needs evolutions!

그런 경우에는 자동으로 마이그레이션이 되도록 다음과 같이
applyEvolutions.default 자바 프로퍼티를 true로 설정한 부분을 추가 합니다.

```
SET YONA_DATA=c:\yona-data
SET JAVA_OPTS=-DapplyEvolutions.default=true -Dhttp.port=80 -Xmx2048m -Xms2048m
bin\yona.bat
```

#### 옵션에 대한 더 자세한 설명

[http://www.playframework.com/documentation/2.3.6/Production](http://www.playframework.com/documentation/2.3.6/Production) 부분을 확인해 주세요.

