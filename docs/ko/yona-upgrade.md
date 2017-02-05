#### 업그레이드

v1.3 이상

- 기존 운영중인 Yona를 중단합니다.
- 새로 내려받은 Yona 파일의 압축을 해제합니다.
- 실행시 사용했던 YONA_DATA 환경변수를 지정합니다.
- bin/yona 를 실행합니다.

참고: [Yona 실행 및 재시작 방법](yona-run-and-restart.md)


v1.3 이전 버전에서 v1.3 이상으로 업그레이드를 할 경우
---
이번에는 조금 손이 갑니다만 이후 업그레이드를 더 편하게 만들었습니다. 번거로우시겠지만 차분히 읽고 설정부탁드려요.

- 다음 3가지를 수정해 주셔야 합니다.
   - logger 설정파일내의 변수 설정
       - conf 폴더의 application-logger.xml 파일을 열어서 `${application.home}` 로 되어 있는 부분을 `${yona.data}` 로 변경합니다. (총 8부분)
       - 참고 샘플: https://github.com/yona-projects/yona/blob/master/conf/application-logger.xml.default 
   - social login 설정을 위해 application.conf 파일 맨 아래에 다음 두 항목을 추가. [소셜로그인 설정](yona-social-login-settings.md) 참고
       - application.social.login.support = "github, google"
       - include "social-login.conf" 
   - 보관이 필요한 파일들을 특정 폴더로 옮겨 놓은 다음 YONA_HOME대신 YONA_DATA로 지정해서 실행

### 상세설명

- 1.3부터 업그레이드가 더 편해지도록 DATA와 실행파일 부분이 분리되었습니다. 
- 더 이상 YONA_HOME 변수를 사용하지 않습니다.
- conf, logs, uploads, repo를 원하는 폴더로 이동한 다음 해당 폴더를 YONA_DATA 환경 변수로 지정합니다.

예를 들어
```
/yona-data
    /conf
    /logs
    /uploads
    /repo
```
로 만들었다면 
```
YONA_DATA=/yona-data;export YONA_DATA
bin/yona
```
와 같은 식으로 실행합니다. 알아서 conf 폴더의 설정파일들을 참고하게 되어 있습니다.

좀더 자세한 실행 옵션들은 [Yona 실행시 추가 가능한 옵션](yona-run-options.md) 파일을 참고해서 설정해주세요.


Yobi에서 Yona로 업그레이드 하려는 경우
---
1. 위의 설치방법을 참고해서 Yona를 설치 합니다.
2. [Yobi 에서 Yona로 Migration 하는 방법](https://repo.yona.io/yona-projects/yona/post/1)을 참고해서 마이그레이션을 진행합니다.


