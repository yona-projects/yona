---
title: docker-compose 배포(X-65) 설계
type: plan
updated: 2026-11-20
---

# docker-compose 배포(X-65) 설계

> **상태: 구현 완료(2026-11-20).** 구현은 `deploy/compose/`이고 시험 결과와 못 한 것은 [install-scripts.md](install-scripts.md)에 있다. 아래는 설계 당시의 계획이다. 기본 설치 방법은 [X-63 설치 스크립트](../roadmap.md)이고, 이 문서는 컨테이너로 한 번에 띄우려는 사이트를 위한 **별도 방법**이다. 이미지는 [container-image-plan.md](container-image-plan.md)(X-64)에 의존한다.

## 1. 목적과 범위

- Yona 서버와 DB를 `docker compose`(또는 `podman compose`) 한 번으로 띄우는 **운영용** 파일 묶음을 제공한다.
- 저장소 루트의 `docker-compose.yml`은 **개발용**(PostgreSQL 25432 포트와 mailpit)이라 그대로 두고, 운영용은 별도 위치(예: `deploy/compose/`)에 둔다.
- 신규 설치만 다룬다. v1.6 이관은 X-63의 이관 모드로 한다.

## 2. 구성 제안

| 서비스 | 내용 |
|---|---|
| `yona` | X-64 이미지. `/data` 볼륨, 8080(웹), 선택으로 2222(내장 SSH). `restart: unless-stopped` |
| `db` | **프로파일로 고른다**: `postgres`(권장), `mariadb`(기본 12.3.3), 외부 DB를 쓰면 띄우지 않는다. H2 파일 DB는 DB 서비스 없이 `yona`만 쓴다 |
| `mailpit`(선택) | 메일 확인용. 운영에서는 쓰지 않고 SMTP 서버를 가리킨다 |

DB 종류 선택은 X-63의 선택(PostgreSQL 권장, MariaDB, Cubrid, SQL Server, H2)과 같은 기준을 따르되, **compose에서 지원하는 것은 PostgreSQL, MariaDB, H2로 시작**하고 Cubrid와 SQL Server는 외부 DB 접속 정보 입력으로 안내한다(공식 이미지와 설정이 따로 필요해서. 필요하면 후속으로 넣는다).

## 3. 설계 규칙

- `yona`는 `depends_on`에 DB의 `condition: service_healthy`를 걸어 DB가 준비된 뒤에 시작한다. DB 서비스에는 상태 확인(PostgreSQL은 `pg_isready`, MariaDB는 공식 이미지의 상태 확인 스크립트)을 둔다.
- 비밀번호와 `yona.secret`은 파일에 직접 쓰지 않고 `.env`(권한 `0600`) 또는 compose의 `secrets`로 준다. 샘플은 `.env.example`로 제공하고 **기본 비밀번호를 고정하지 않는다**(X-63과 같은 원칙).
- 데이터는 이름 있는 볼륨으로 둔다. 정리할 때는 `docker compose down -v`가 데이터를 지운다고 문서에 분명히 적는다.
- DB 데이터베이스는 MariaDB는 `utf8mb4`와 `utf8mb4_bin`으로 만든다(공식 이미지의 환경 변수와 서버 옵션으로 지정하며, 서버 설정 파일은 덮어쓰지 않는다).
- 부팅 때 자동 시작: `restart: unless-stopped`와 호스트의 `docker.service` `enable`이 짝이다. Podman은 Quadlet이나 systemd 단위로 감싸야 한다(X-63 "부팅 때 자동 시작"과 같은 확인이 필요하다).
- 폐쇄 환경: 이미지를 `save`와 `load`로 옮기고 `pull_policy: never`를 쓰는 변형을 안내한다.
- 시스템 sshd 연동은 컨테이너에서 쓰지 않으므로 SSH는 내장 서버(MINA)만 켠다. 포트는 호스트의 22를 내장 서버로 보내는 포트 전달(`yona.ssh.public-port` 설정)을 안내한다.

## 4. 구현 전에 확인할 것 (아직 확인하지 않았다)

1. X-64 이미지가 먼저 있어야 한다. 이 문서의 시험은 모두 그 이미지에 의존한다.
2. `podman compose`로도 같은 파일이 그대로 도는지(프로파일, `depends_on` 조건 지원 여부). **2026-11-20에 시도했으나 이 컴퓨터에 Podman과 `docker compose` 플러그인이 모두 없어 확인하지 못했다.** 두 도구를 설치한 환경에서 구현 때 확인한다. 읽기 전용 루트로 돌릴 때는 `/tmp`를 쓰기 가능하게 두고, 데이터 볼륨의 소유권은 이미지가 맞춘다([container-image-plan.md](container-image-plan.md) 4절).
3. MariaDB 12.3.3 공식 이미지의 문자 집합과 정렬 규칙 지정 방법(환경 변수와 명령 인자). 시험에서 서버 기본 정렬 규칙이 `utf8mb4_uca1400_ai_ci`였고, 데이터베이스를 `utf8mb4_bin`으로 만들면 통과했다([X-63 MariaDB 12.3.3 확인 기록](../roadmap.md)).
4. 첫 시작(스키마 생성)이 길 때 `yona`의 상태 확인 유예 시간.
5. 환영 화면에서 관리자 계정을 만든 뒤 **서버를 다시 시작**해야 한다([fresh-install-guide.md](fresh-install-guide.md)). 컨테이너 재시작으로 처리되는지와 안내 문구.

## 5. 시험 기준 (구현 때)

- 세 구성(PostgreSQL, MariaDB, H2)에서 `docker compose up -d` 한 번으로 상태 확인이 통과한다.
- `docker compose down` 뒤 다시 `up`을 해도(볼륨 유지) 데이터가 그대로다.
- 호스트를 재부팅한 뒤에도 자동으로 올라온다.
- 같은 파일이 `podman compose`에서도 도는지 한 번 확인한다.
