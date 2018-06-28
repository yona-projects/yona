
**본 가이드는 YONA_HOME 환경변수를 사용하는 v1.3 이전 버전 사용자를 대상으로 하는 가이드입니다. v1.3 이상 사용자에게는 행당되지 않습니다. **

#### 실행시 추가 가능한 옵션: Linux, OSX의 경우

`YONA_HOME` 속성을 통해 Yona가 데이터, 데이터베이스, 설정파일을 불러오고 저장할
홈 디렉터리를 지정할 수 있습니다. 예를 들어, /home/user/.yona를 홈 디렉터리로
사용하려면 Yona를 시작할 때 다음과 같이 지정합니다.

    YONA_HOME=/home/user/.yona bin/yona

`JAVA_OPTS` 환경변수를 이용해 자바 환경 변수를 지정할 수도 있습니다. 시스템
메모리가 4기가 이상이라면, 다음과 같은 옵션으로 실행하는걸 권장합니다.

    JAVA_OPTS="-Xmx2048m -Xms2048m" bin/yona

기본적으로 9000번 포트를 사용하지만, 다른 포트를 사용하고 싶다면 http.port 자바
환경변수를 수정합니다.

    JAVA_OPTS="-Dhttp.port=80" bin/yona

#### Windows의 경우

Yona를 시작할 때, YONA_HOME 폴더의 위치를 지정해서 아래 명령순서로 실행해주세요!
아래 내용이 들어가 있는 run.bat을 따로 만드셔도 좋습니다!

```
SET YONA_HOME=c:\yona\yona-1.0.4
SET JAVA_OPTS=-Dyona.home=%YONA_HOME% -Dconfig.file=%YONA_HOME%\conf\application.conf -Dlogger.file=%YONA_HOME%\conf\application-logger.xml
bin\yona.bat
```

`JAVA_OPTS` 환경변수를 이용해 자바 환경 변수를 지정할 수도 있습니다. 시스템
메모리가 4기가 이상이라면, 다음과 같은 옵션으로 실행하는 걸 권장합니다.

    SET JAVA_OPTS=-Xmx2048m -Xms2048m
    bin\yona.bat

기본적으로 9000번 포트를 사용하지만, 다른 포트를 사용하고 싶다면 http.port 자바
환경변수를 수정합니다.

    JAVA_OPTS=-Dhttp.port=80
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

