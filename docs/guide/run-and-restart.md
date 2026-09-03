# 실행 및 재시작

legacy Yona의 `docs/ko/yona-run-and-restart.md`를 yona 기준으로 갱신.

## 실행

```bash
./gradlew bootRun
# 또는
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar --spring.profiles.active=mariadb
```

legacy처럼 `YONA_DATA` 환경변수로 conf/logs/uploads/repo를 한 디렉터리 아래 모아두는 개념은
없다 — 대신 물리 저장소 경로(git bare repo / svn repo / lfs / 업로드)는 각각 별도 설정 키
(`yona.git.base-dir` 등)로 지정한다. 자세한 내용은
[README의 "운영 환경 설정"](../../README.md#운영-환경-설정-특히-windows) 참고.

## 첫 화면 확인

기본 포트는 `8080`이다(legacy의 `9000`이 아님). 로컬 환경이면
[http://127.0.0.1:8080](http://127.0.0.1:8080) 에 접속한다. 가입된 유저가 없으면 자동으로
최초 관리자 생성 화면(`/bootstrap-setup`)으로 연결된다 — 자세한 내용은
[install.md](install.md#3-최초-관리자-계정-만들기) 참고.

## 재시작

- `java -jar ...`로 포그라운드 실행 중이면 `Ctrl-C`.
- 서비스로 상시 구동하려면 systemd 유닛을 쓴다 —
  [`support-script/systemd/yona.service`](../../support-script/systemd/yona.service) 예시 참고.

  ```bash
  sudo systemctl restart yona
  ```

- Docker Compose로 DB만 별도로 띄운 구성이라면, DB 재시작은 `docker compose restart mariadb`
  (또는 `postgres`)로 애플리케이션과 독립적으로 처리한다.
