# 설치

legacy Yona의 `docs/ko/install-mariadb.md` + `docs/ko/install-yona-server.md`를 yona 기준으로
합치고 다시 쓴 문서. legacy는 "MariaDB를 먼저 손으로 설치 → 압축 배포판을 받아 실행"하는
2단계였지만, yona는 Gradle 빌드 + (로컬 개발이면) Docker Compose로 훨씬 짧아졌다.

## 1. DB 준비

### 로컬 개발 — Docker Compose (권장)

저장소 루트의 `docker-compose.yml`로 MariaDB/PostgreSQL을 바로 띄울 수 있다.

```bash
docker compose up -d mariadb   # 또는 postgres
```

이미 `yona` 데이터베이스/유저/비밀번호(`yona`/`yona_password`)가 컨테이너 기동 시 자동으로
만들어진다 — legacy처럼 `mysql -uroot`로 접속해 유저·DB를 직접 만드는 과정이 필요 없다.

### 직접 설치한 DB를 쓰는 경우

기존 legacy 가이드처럼 MariaDB를 직접 설치했다면(또는 PostgreSQL/MySQL/SQL Server/CUBRID),
아래 절차는 여전히 유효하다.

```bash
mysql -uroot
create user 'yona'@'localhost' IDENTIFIED BY '원하는_비밀번호';
create database yona
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_bin;
GRANT ALL ON yona.* to 'yona'@'localhost';
```

my.cnf 튜닝은 [`support-script/mariadb/my.cnf`](../../support-script/mariadb/my.cnf) 참고.
**단, legacy 샘플에 있던 `innodb_file_format`/`innodb_large_prefix`는 MariaDB 10.6부터
서버가 인식하지 못해 기동이 실패하므로 뺐다** — 자세한 내용은
[troubleshooting.md](troubleshooting.md#mariadb-767-byte-에러) 참고.

## 2. 애플리케이션 빌드 & 실행

legacy처럼 배포 zip을 받아 푸는 게 아니라, 소스에서 바로 빌드해서 실행한다.

```bash
# 개발 중 바로 띄우기
./gradlew bootRun

# 배포용 실행 가능 jar 생성 후 실행
./gradlew bootJar
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar --spring.profiles.active=mariadb
```

기본 프로파일은 `mariadb`다. PostgreSQL/MySQL/SQL Server/CUBRID로 쓰려면
`--spring.profiles.active=postgres` 등으로 바꾼다 — 프로파일별 접속 정보는
[README의 "데이터베이스 선택"](../../README.md#데이터베이스-선택) 참고.

DB 접속 정보(호스트/포트/유저/비밀번호)를 바꾸려면 `src/main/resources/application.yml`의
해당 프로파일 블록(`spring.datasource.*`)을 직접 고치거나, 커맨드라인 인자로 덮어쓴다.

```bash
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=mariadb \
  --spring.datasource.url=jdbc:mariadb://127.0.0.1:3306/yona \
  --spring.datasource.username=yona \
  --spring.datasource.password=실제_비밀번호
```

## 3. 최초 관리자 계정 만들기

legacy는 `application.secret`이 기본값일 때 "패스워드가 틀렸다"는 에러로 죽으면서
`welcome/secret` 화면으로 유도하는 다소 우회적인 흐름이었다. yona는 훨씬 직접적이다 —
**가입된 유저가 0명이면** 첫 접속 시 자동으로 최초 관리자 생성 화면(`/bootstrap-setup`)으로
연결된다(`BootstrapSetupController`). 별도의 secret 파일 재기록 절차가 없다.

기본 포트는 `8080`이다(legacy의 `9000`이 아님). 로컬이면
[http://127.0.0.1:8080](http://127.0.0.1:8080) 으로 접속해서 관리자 계정을 만들면 된다.

이어서 실행/재시작 방법은 [run-and-restart.md](run-and-restart.md), 물리 저장소 경로 등 운영
설정은 [README의 "운영 환경 설정"](../../README.md#운영-환경-설정-특히-windows) 절을 참고.
