---
title: 컨테이너 이미지(Dockerfile, X-64) 설계
type: plan
updated: 2026-11-20
---

# 컨테이너 이미지(Dockerfile, X-64) 설계

> **상태: 구현 완료(2026-11-20).** 구현은 `deploy/docker/Dockerfile`, 시험 `deploy/docker/test-image.sh`이고 시험 결과와 못 한 것은 [install-scripts.md](install-scripts.md)에 있다. 아래는 설계 당시의 계획이다. 기본 설치 방법은 [X-63 설치 스크립트](../roadmap.md)이고, 이 문서는 컨테이너로 배포하려는 사이트를 위한 **별도 방법**이다. [docker-compose](compose-deploy-plan.md)와 [Helm](helm-chart-plan.md)이 이 이미지를 쓴다. 구현 전에 `decisions.md`에 선택지와 권장안을 올려 선생님의 승인을 받는다.

## 1. 목적과 범위

- Yona v2 서버(실행 가능한 jar 하나)를 컨테이너 이미지로 만든다.
- 이미지는 **서버만** 담는다. DB는 담지 않는다(DB는 compose나 Helm이 따로 띄우거나 외부 DB를 쓴다).
- v1.6 이관은 이미지의 책임이 아니다. 이관은 X-63의 이관 모드로 한다.
- v1.6 동작과 계약은 바꾸지 않는다.

## 2. 이미 알고 있는 사실 (이 저장소에서 확인한 것)

| 항목 | 값 | 근거 |
|---|---|---|
| 실행 형태 | Spring Boot 실행 jar 하나 | [fresh-install-guide.md](fresh-install-guide.md) |
| Java | 21 이상 | 같은 문서 |
| 기본 포트 | 8080(`--server.port`) | 같은 문서 |
| 데이터 디렉터리 | `yona.data-dir`. 저장소, 첨부, 비밀 키, 로그, SSH 호스트 키가 모두 여기에 쌓인다 | 같은 문서 |
| 상태 확인 | `/-_-actuator/health`, `/-_-actuator/health/readiness`(DB와 데이터 디렉터리만 본다) | `application.yml` |
| SSH | 내장 서버(MINA)는 기본 꺼짐, 켜면 2222. 시스템 sshd 연동은 호스트의 sshd가 필요해 **컨테이너에서는 쓰지 않는다** | [ssh.md](ssh.md) |
| Mercurial | 순수 Java 구현(hg4j)이라 `hg` 실행 파일이 필요 없다 | [mercurial-plan.md](mercurial-plan.md) |
| Git | JGit이라 `git` 실행 파일이 필요 없다 | 같은 문서군 |

## 3. 설계 제안

| 항목 | 제안 | 이유 |
|---|---|---|
| 베이스 이미지 | Java 21 JRE 이미지(예: Eclipse Temurin 21 JRE) | JDK는 필요 없다. 다만 이관용 H2 변환기는 JDK가 필요하니 이관은 이미지가 아니라 X-63에서 한다 |
| 빌드 방식 | 릴리스에 올라간 jar를 복사(권장) 또는 멀티 스테이지로 `./gradlew bootJar` | 릴리스 jar와 이미지의 내용을 같게 하려면 앞의 것이 낫다 |
| 실행 사용자 | 비루트 전용 사용자 | 보안. 데이터 디렉터리의 소유권을 맞춰야 한다 |
| 볼륨 | `/data`를 `yona.data-dir`로 쓰고 볼륨으로 선언 | 지우면 비밀 키와 저장소를 잃는다 |
| 포트 | 8080(웹), 2222(내장 SSH, 선택) | |
| 상태 확인 | 이미지의 `HEALTHCHECK`는 `/-_-actuator/health` | 오케스트레이터가 따로 정하면 그쪽이 우선한다 |
| 메모리 | 컨테이너 한도에 맞춰 JVM 힙을 비율로 정한다(`-XX:MaxRAMPercentage`) | 고정 `-Xmx`는 한도가 바뀌면 어긋난다 |
| 시간대 | `TZ`로 정한다(기본 UTC) | v1.6 시각 표시와의 차이를 설치자가 알아야 한다 |
| 설정 전달 | `application.yml`을 볼륨에 두거나 환경 변수로 준다 | 환경 변수 전달은 아래 4절의 확인이 필요하다 |
| 아키텍처 | amd64와 arm64 | 이 컴퓨터에 `buildx` 다중 아키텍처 빌더가 있다 |
| 태그 | 릴리스 버전과 같은 태그, `latest`는 두지 않는 쪽을 권한다 | 운영에서 우연히 올라가는 것을 막는다 |

## 4. 구현 전에 확인한 것 (2026-11-20)

`yona-2.0.0-SNAPSHOT.jar`(2026-11-17 빌드)를 호스트(Temurin 21.0.11)와 `eclipse-temurin:21-jre` 컨테이너에서 H2 파일 DB로 시작해 확인했다.

| 항목 | 결과 | 설계에 주는 영향 |
|---|---|---|
| 환경 변수만으로 설정 | **된다.** `YONA_DATA_DIR`, `SERVER_PORT`, `SPRING_DATASOURCE_URL` 세 개만 주고 설정 파일 없이 시작했고, 데이터 디렉터리와 포트와 DB 접속 주소가 반영되었다 | 이미지는 스프링의 환경 변수 규칙(점을 밑줄로, 대문자)을 쓴다. **확인한 것은 이 세 개뿐이다.** `yona.secret`, `yona.ssh.mina.*`, DB 사용자와 비밀번호 등 다른 키는 시험하지 않았다 |
| 시작 시간 | 빈 H2에서 약 20초 만에 상태 확인이 통과했다(호스트와 컨테이너 모두) | 시작 프로브의 유예 시간은 이보다 넉넉히 잡는다. 외부 DB와 큰 데이터에서는 달라질 수 있다 |
| 정상 종료 | **된다.** SIGTERM을 보내면 `GracefulShutdown`이 동작하고(0.6초), 컨테이너에서도 `docker stop`이 1초 안에 끝났다 | 종료 코드는 **143**이다. systemd 단위에는 `SuccessExitStatus=143`이 필요하다(X-63) |
| 비루트 쓰기 | **데이터 볼륨이 root 소유이면 실패한다.** 사용자 10001로 root 소유 볼륨을 쓰자 `/data/logs`를 만들지 못해 로그 파일 생성 오류가 났다(시작이 끝까지 가는지는 보지 않았다). 소유자를 10001로 맞춘 볼륨에서는 정상이다 | **이미지가 `/data`를 만들어 실행 사용자 소유로 둔다**(Docker의 새 이름 있는 볼륨은 이미지 폴더의 소유권을 이어받는다). 쿠버네티스는 `fsGroup`이나 `runAsUser`를 맞춘다(X-66). bind mount는 사용자가 소유권을 맞춰야 한다고 안내한다 |
| 읽기 전용 루트 | **된다.** `--read-only --tmpfs /tmp`와 쓰기 가능한 `/data`만으로 시작했다. 루트 파일 시스템에 쓰면 거부된다 | 쓰는 곳은 **`/data`와 `/tmp`뿐**이었다. Tomcat이 `/tmp`에 `tomcat-docbase.*`와 `tomcat.*` 폴더를 만든다. 읽기 전용으로 돌릴 때 `/tmp`는 꼭 쓰기 가능하게 둔다(X-65, X-66) |

**확인하지 못한 것**
- 외부 PostgreSQL과 MariaDB로 시작하는 컨테이너, `yona.secret`과 SSH 키 같은 다른 환경 변수. 첫 시작만 보았고 재시작 뒤 데이터 유지는 보지 않았다.
- **`podman compose` 호환.** 이 컴퓨터에 Podman도 `docker compose` 플러그인도 없어 시험하지 못했다. [compose-deploy-plan.md](compose-deploy-plan.md)의 확인 항목으로 남긴다.
- 이미지 크기, `arm64` 빌드, 이미지 레지스트리 게시.

**결정됨(2026-11-20 선생님)**: 이미지는 **GitHub Container Registry**에 올리고, **처음에는 서명하지 않는다**(릴리스 jar의 체크섬 검증은 X-63에서 한다). 서명(Cosign)은 필요가 생길 때 같은 이미지에 나중에 더한다. 다른 레지스트리가 필요하면 같은 이미지를 복사한다.

## 5. 시험 기준 (구현 때)

- 이미지를 만들어 H2 파일 DB와 외부 PostgreSQL 두 경우에서 시작하고, 상태 확인이 통과한다.
- 컨테이너를 지웠다 다시 만들어도(볼륨 유지) 데이터와 비밀 키와 SSH 호스트 키가 그대로다.
- 비루트로 돈다(`id`로 확인).
- 폐쇄 환경: `docker save`와 `docker load`(또는 `podman save`와 `podman load`)로 옮겨 실행된다.
