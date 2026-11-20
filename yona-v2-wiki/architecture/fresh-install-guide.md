# 새로 설치하는 v2 설치 가이드

v1.6 데이터가 없는 상태에서 Yona v2 서버를 처음 설치하는 순서를 정리한다. v1.6 데이터베이스를 이어받는 경우는 [이관 가이드](v16-migration-guide.md)를 본다.

## 한눈에 보기

| 순서 | 할 일 | 비고 |
|---|---|---|
| 1 | Java 21과 데이터베이스를 준비한다 | MariaDB, PostgreSQL, H2 등 |
| 2 | 데이터베이스와 사용자를 만든다 | MariaDB는 utf8mb4와 utf8mb4_bin |
| 3 | 데이터 디렉터리를 정하고 서버를 시작한다 | 접속 주소를 직접 주지 않으면 메모리 DB로 뜬다 |
| 4 | 첫 화면에서 관리자 계정을 정한다 | 정한 뒤 서버를 다시 시작한다 |
| 5 | SSH 접근이 필요하면 켠다(선택) | 내장 서버와 시스템 sshd 연동은 플래그가 따로 있다 |
| 6 | 관리자로 로그인해 확인한다 | 상태 확인 주소로도 점검한다 |

## 1. 준비

- Java 21 이상이 필요하다. 서버는 Spring Boot 4.1이 만든 실행 가능한 jar 하나이다(`build/libs/yona-2.0.0-SNAPSHOT.jar`, `./gradlew bootJar`로 만든다).
- 데이터베이스는 H2, MariaDB/MySQL, PostgreSQL, MSSQL, Cubrid 중에서 고른다. 운영에서는 MariaDB나 PostgreSQL을 권장한다.
- 알림 메일을 보내려면 SMTP 서버가 필요하다. 없으면 `notification.bymail.enabled=false`로 끈다.

**지킬 것**
- 접속 주소(`spring.datasource.url`)를 주지 않으면 기본값인 메모리 H2로 뜬다. 서버를 끄면 데이터가 모두 사라지므로 운영에서는 반드시 정한다.
- 데이터 디렉터리(`yona.data-dir`)를 비우면 서버를 시작한 작업 디렉터리를 쓴다. 저장소, 첨부 파일, 비밀 키, 로그가 모두 여기에 쌓이므로 고정 경로를 정한다.

## 2. 데이터베이스 준비

스키마는 서버가 처음 시작할 때 엔티티에서 만든다(`ddl-auto: update`). 빈 데이터베이스와 사용자만 만들면 된다.

| DB | 준비 | 접속 주소 예 |
|---|---|---|
| MariaDB/MySQL | `create database yona DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin;` | `jdbc:mariadb://localhost:3306/yona` |
| PostgreSQL | 데이터베이스와 사용자를 만든다. 개발용은 `docker compose up -d postgres`(포트 25432) | `jdbc:postgresql://localhost:5432/yona` |
| MSSQL | 데이터베이스에 `READ_COMMITTED_SNAPSHOT ON`을 켠다. 이를 켜지 않으면 읽는 쪽이 쓰는 쪽을 기다려 멈출 수 있다 | `jdbc:sqlserver://호스트:1433;databaseName=yona` |
| Cubrid | 데이터베이스를 UTF-8 로케일(`en_US.utf8`, `ko_KR.utf8` 등)로 만든다. 브로커의 `SQL_LOG`는 `OFF`나 `ERROR`로 바꾼다(기본 `ON`은 모든 SQL을 파일에 기록해 읽기 19~28%, 쓰기 9~12%를 잃는다). 브로커 `MAX_NUM_APPL_SERVER`와 서버 `max_clients`는 동시 접속 수에 맞춘다 | `jdbc:cubrid:호스트:33000:yona:dba::?charset=utf-8` |
| H2 파일 | 따로 준비할 것이 없다. 소규모에 쓴다 | `jdbc:h2:file:/경로/db;DB_CLOSE_DELAY=-1` |

MSSQL과 Cubrid는 접속 주소를 보고 드라이버, 방언, 한글 열 설정을 서버가 자동으로 정한다(`DatabaseDefaults`). Cubrid는 여기에 JDBC 준비된 문장 캐시 옵션과 대소문자 무시 검색(DB 문자 집합이 UTF-8일 때 정렬 규칙 `utf8_en_ci`)도 자동으로 적용한다. Cubrid는 MariaDB, PostgreSQL보다 처리량이 절반 수준이고 v1.6과 동작이 다른 곳이 있으니 [DB 성능 비교](db-performance-comparison.md)를 먼저 보세요.

## 3. 서버 시작

```bash
java -jar yona-2.0.0-SNAPSHOT.jar \
  --yona.data-dir=/var/lib/yona \
  --spring.datasource.url=jdbc:mariadb://localhost:3306/yona \
  --spring.datasource.username=yona \
  --spring.datasource.password=비밀번호
```

- 포트를 주지 않으면 8080이다(`--server.port`).
- 같은 설정을 `application.yml`이나 환경 변수로 줘도 된다. 위 방식으로 직접 확인했다.
- 첫 시작에서 테이블, 관리자 계정(`admin`), 역할 7종이 만들어진다.

## 4. 첫 화면: 관리자 계정 정하기

처음 접속하면 로그인 화면이 아니라 사이트 비밀 키와 관리자 계정을 정하는 환영 화면이 나온다. 여기서 관리자(`admin`)의 이름, 이메일, 비밀번호를 입력한다.

- 비밀번호는 8자 이상 128자 이하이고, 흔한 비밀번호, 같은 글자 반복, 아이디나 이메일과 같은 값은 거절된다(HTTP 400).
- 저장하면 `<데이터 디렉터리>/config/secret.properties`에 비밀 키가 기록되고, 서버를 다시 시작하라는 안내가 보인다.
- 다시 시작하면 환영 화면은 사라지고 로그인 화면이 나온다. 로그인 아이디는 `admin`이다.

**지킬 것**
- `yona.secret`을 미리 설정해 시작하면 환영 화면이 나오지 않는다. 이때 관리자의 초기 비밀번호는 v1.6 초기 데이터의 값이라 알 수 없으므로 신규 설치에서는 설정하지 않는다.
- `secret.properties`는 백업에 포함한다. 잃어버리면 기존 데이터의 서명 값 일부를 검증하지 못한다.

## 5. 운영 설정

| 설정 | 키 | 설명 |
|---|---|---|
| 사이트 주소 | `yona.hostname`, `yona.port`, `yona.scheme` | 메일과 웹훅에 들어가는 주소이다. 비우면 요청의 Host 헤더를 쓴다 |
| HTTPS 뒤 운영 | `yona.scheme=https`와 `server.forward-headers-strategy=framework` | 앞의 것은 메일 주소를 `https://`로 만들고, 뒤의 것은 프록시 뒤에서도 접속자 주소를 구분하게 한다 |
| 메일 | `spring.mail.host`, `spring.mail.port`, `yona.smtp.user`, `yona.smtp.domain` | 접속 정보와 보내는 주소 |
| SSH 접근 | `yona.ssh.mina.enabled`, `yona.ssh.relay.enabled` | 내장 서버와 시스템 sshd 연동을 각각 켠다. 아래 6절 |
| 상태 확인 | `/-_-actuator/health`, `/-_-actuator/health/readiness` | 준비 상태는 DB와 데이터 디렉터리만 본다 |

설정 키 전체는 [configuration-keys](configuration-keys.md)에 있다.

## 6. SSH 접근 설정 (선택)

Git, Mercurial, Subversion 저장소를 SSH로 받으려면 켠다. v1.6에는 SSH 서버가 없었으므로(추가 항목 X-3) 기본은 **꺼져 있고**, 이관해도 자동으로 켜지지 않는다. 두 방식은 **플래그가 따로** 있어서 하나만 켜도 되고 둘 다 켜도 된다. 둘 다 꺼 두면 포트도 소켓도 열지 않는다.

| 방식 | 켜는 설정 | 접속 주소(Git) | 설치할 것 |
|---|---|---|---|
| 내장 SSH 서버(Apache MINA SSHD) | `yona.ssh.mina.enabled=true` | `ssh://git@호스트:2222/소유자/저장소.git` | 없다 |
| 시스템 sshd 연동 | `yona.ssh.relay.enabled=true` | `git@호스트:소유자/저장소.git` | `yona-ssh`(Go), 전용 계정, sshd 설정 |

사용자 이름은 저장소 종류별로 Git `git`(`yona.ssh.user`), Mercurial `hg`(`yona.ssh.hg-user`), Subversion `svn`(`yona.ssh.svn-user`)이고 Subversion 주소는 `svn+ssh://svn@호스트[:포트]/소유자/저장소`이다. 이름은 인증에 쓰이지 않고(사용자는 키로 정해진다) 시스템 sshd에서는 OS 계정 이름이 된다. 설정 값을 비우면 `git`으로 돌아간다. 어느 방식이든 사용자는 웹의 **내 정보의 SSH 키**에 공개키를 등록하고, 프로젝트는 **배포 키**를 등록한다. 접근 권한, 브랜치 보호, 감사 기록은 HTTP와 같다.

### 내장 SSH 서버

```yaml
yona:
  ssh:
    mina:
      enabled: true
      port: 2222          # 22를 쓰려면 root 권한이나 포트 전달(컨테이너 -p 22:2222)이 필요하다
      # bind-address: ""  # 비우면 모든 주소
    # public-port: 0      # 포트 전달 뒤라서 바깥 포트가 다르면 복제 주소에 보일 포트를 적는다(두 방식 공통)
```

- 호스트 키는 처음 시작할 때 `<데이터 디렉터리>/ssh/ssh_host_ed25519_key`로 만든다. 재시작해도 같아야 클라이언트가 "호스트 키가 바뀌었다"는 경고를 내지 않으므로 **데이터 디렉터리를 옮기거나 백업할 때 `ssh/`를 함께 둔다.**
- 공개키 인증만 받는다. 셸, SFTP, 포트 전달은 열지 않는다.
- 방화벽에서 이 포트를 열어야 한다.

### 시스템 sshd 연동

이미 22번 포트에서 도는 OpenSSH가 접속을 받고 Yona는 소켓으로 처리한다. 설정은 `yona.ssh.relay.enabled=true`, `yona.ssh.relay.internal-secret`(공유 비밀 값, 운영에서는 직접 정한다)이고, 이어서 호스트에서 다음을 한다.

1. 전용 계정 `git`, `hg`, `svn`을 만들고 각각 Yona 계정의 그룹에 넣는다(셸은 `/bin/bash`, `nologin`이면 안 된다). 계정을 하나만 두고 싶으면 `yona.ssh.hg-user: ""`, `yona.ssh.svn-user: ""`로 비워 모두 `git`으로 쓴다.
2. Go 도우미 `yona-ssh`를 빌드해 `/usr/local/bin`에 설치하고, 공유 비밀 값 파일(`/etc/yona/ssh-internal-secret`)과 `/etc/yona/ssh-auth.conf`를 둔다.
3. `sshd_config` 끝에 `Match User git,hg,svn`(계정을 하나만 둔 경우 `git`)과 `AuthorizedKeysCommand`를 더하고 `sshd -t`로 검사한 뒤 reload한다.

전체 절차와 문제 해결은 저장소의 `docs/yona-ssh-system-sshd.md`에 있다. Windows의 번들 OpenSSH는 `AuthorizedKeysCommand`를 지원하지 않으므로 Windows 서버는 내장 방식을 쓴다.

### 확인

```bash
# 내장 서버: 등록하지 않은 키는 거절되어야 한다
ssh -p 2222 git@호스트 -T          # Permission denied (publickey).
# 공개키를 웹에 등록한 뒤
ssh -T git@호스트                   # (시스템 sshd) Hi! Yona SSH authentication succeeded. Interactive shell access is not provided.
git clone ssh://git@호스트:2222/소유자/저장소.git
```

**검증한 결과** (H2 파일 DB, 2026-11-17)
- 두 플래그가 모두 꺼져 있으면 2222 포트가 열리지 않았고 `ssh/` 폴더도 만들어지지 않았다.
- `yona.ssh.mina.enabled=true`로 시작하면 지정한 포트가 열렸고 `ssh/ssh_host_ed25519_key`(권한 `rw-------`)가 생겼다. 등록하지 않은 키는 `Permission denied (publickey)`로 거절되었다.
- `yona.ssh.relay.enabled=true`만 켜면 TCP 포트는 열리지 않고 `ssh/relay.sock`(`srw-rw----`)만 생겼다.
- 진짜 OpenSSH 8.0 sshd(AlmaLinux 8 컨테이너)에 `git`, `hg`, `svn` 계정과 `Match User git,hg,svn`을 두고 확인했다. Git 복제와 푸시, Mercurial 복제와 푸시(`hg@`와 `git@` 주소), Subversion 목록과 커밋(`svn@`와 `git@` 주소)이 모두 통했고, 등록하지 않은 키는 세 종류 모두 거부되었다. 계정이 없는 이름(`nobody@`)은 sshd가 거부했다.

**확인하지 못한 것**
- 내장 서버(MINA)로 Mercurial과 Subversion을 쓰는 실제 클라이언트 확인은 이번에 따로 돌리지 않았다. 자동 시험(`HgSshServerTest`, `SvnSshServerTest`)에 근거한다.
- 이관(v1.6 DB를 이어받은 서버)에서 SSH를 켠 경우는 따로 돌려 보지 않았다. SSH 관련 테이블(`ssh_key`, `deploy_key`)은 서버가 처음 시작할 때 새로 만들어진다.

## 7. 설치 확인

```bash
curl http://localhost:8080/-_-actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

관리자로 로그인한 뒤 `/admin`(사이트 관리)이 열리면 설치가 끝난 것이다.

**검증한 결과** (H2 파일 DB, 빈 데이터 디렉터리, 2026-11-17)
- 포트를 주지 않으면 8080으로 떴고, 상태 확인과 준비 확인이 모두 UP이었다.
- 첫 화면은 환영 화면이었다. 약한 비밀번호(`pass1234`)는 400으로 거절되었고, 올바른 입력은 받아들여져 `config/secret.properties`(39바이트)가 만들어졌다.
- 다시 시작한 뒤 로그인 화면이 열렸고, 틀린 비밀번호는 로그인 화면으로 돌려보냈으며, 설정한 비밀번호로는 로그인되어 `/admin`이 200이었다.
- 데이터 디렉터리에는 `audit/`, `config/`, `logs/`가 생겼다.
- `yona.secret`을 미리 준 경우에는 환영 화면이 나오지 않았고, `admin` 계정에 흔한 비밀번호 3개(`admin`, `Admin1234`, `wrong`)로는 로그인되지 않았다.

**확인하지 못한 것**
- MariaDB, PostgreSQL, MSSQL, Cubrid에서 빈 데이터베이스로 처음 시작하는 과정은 이 문서를 쓰면서 따로 돌려 보지 않았다(전체 시험은 각 DB에서 통과한 기록이 있다). 위 접속 주소 예는 시험 설정과 기존 문서에서 가져왔다.
- 환경 변수만으로 설정하는 방식과, `yona.secret`을 미리 준 경우의 초기 비밀번호는 확인하지 못했다.
- `bootJar`를 이 문서를 쓰면서 새로 빌드하지는 않았다. 이미 빌드된 jar로 확인했다.
