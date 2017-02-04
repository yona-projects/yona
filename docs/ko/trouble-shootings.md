### 설치시 오류 [1]입력 줄이 너무 깁니다. 
Windows OS에서는 명령줄 길이 한계로 너무 하위로 들어가지 않도록 \ 아래에 폴더를 만들어 주세요.
드라이브 아래에 바로 폴더를 만드는 걸 권장합니다.

```
Windows OS Yona 권장 폴더
C:\yona\yona-1.3.0 <- yona 폴더아래에 버전별로 압축해제 
C:\yona-data <- conf 파일, logs, uploads, repo 폴더가 생성되고 유지되는 곳. YONA_DATA 환경 변수로 지정
```

만약 부득이하게 Program Files 등의 하위 폴더에 설치해야 한다면 아래 링크를 참고해주세요.
https://github.com/yona-projects/yona/issues/130

### MariaDB 767 byte 에러가 발생했다면?
만약 콘솔에 DB 관련 에러 특히 
```
[error] play - Specified key was too long; max key length is 767 bytes [ERROR:1071, SQLSTATE:42000] 
```
가 발생할 경우 [MariaDB 767 byte 에러](db-error-767.md) 항목을 참고해 주세요


### Yona 버전 업그레이드시 옵션 조절

업그레이드를 하는 경우 다음과 같이 데이터베이스 스키마 마이그레이션이
필요하다는 경고 메시지와 함께 실행되지 않는 상황을 겪을 수 있습니다.

    [warn] play - Your production database [default] needs evolutions!

그런 경우에는 자동으로 마이그레이션이 되도록 다음과 같이
applyEvolutions.default 자바 프로퍼티를 true로 설정합니다.

    SET JAVA_OPTS=-DapplyEvolutions.default=true
    bin\yona

### RuntimeException: Provider 'google' missing needed setting 'clientId'

application.conf 파일에서 관련 설정을 읽어 들일 수 없을때 발상하는 오류.

application.conf 맨 하단에 아래 설정을 추가해 주세요.

```
include "social-login.conf"
```
