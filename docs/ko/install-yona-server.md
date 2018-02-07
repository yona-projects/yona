Yona 설치
===
```
사전조건
---
Java 8 에서 동작합니다. Java 9는 아직 미지원
```

Yona 최신 버전을 https://github.com/yona-projects/yona/releases 에서 다운받아 압축을 풉니다. 
wget으로 받아서 unzip으로 압축을 푼다면 미리 다운로드 링크 주소를 확인한 다음 내려받습니다.

예)

    wget https://github.com/yona-projects/yona/releases/download/v1.3.0/yona-v1.3.0-bin.zip
    unzip yona.zip

### application.conf 파일 등 설정파일 생성하기

압축이 풀린 곳으로 이동해서 bin/yona 을 실행합니다.

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


본격적인 첫 화면 보기 위한 실행
----

yona v1.3 이상부터는 data 폴더만 지정해주면 어느 위치에서 yona를 실행시켜도 무방하게 변경되었습니다. 이후에 버전 업그레이드시에도 특별히 데이터 폴더들을 옮기거나 할 필요가 없습니다.

- conf 폴더를 비롯해 각종 데이터를 유지할 폴더를 하나 만듭니다.
```
예)

/yona-data
```
- conf 폴더를 위에서 만든 /yona-data로 복사합니다.
```
예) 현재 위치가 /Users/doortts/Download/yona-v1.3.0-bin 이라고 가정했을때 

cp -r conf /yona-data
```

- YONA_DATA 환경변수를 지정하고 Yona 실행
```
예) 현재 위치가 /Users/doortts/Download/yona-v1.3.0-bin 이라고 가정했을때 

YONA_DATA=/yona-data;export YONA_DATA
bin/yona
```

이어서 본격적인 실행 방법에 대해서는 [yona-run-and-restart.md](yona-run-and-restart.md)를 참고해주세요.

만약 사용버전이 v1.3 이전일 경우에는 [yona-run-under-v1.3](yona-run-under-v1.3.md) 를 참고해주세요.

