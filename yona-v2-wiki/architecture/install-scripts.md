---
title: 설치 스크립트(X-63)와 컨테이너 배포(X-64부터 X-66) 구현
type: architecture
updated: 2026-11-20
---

# 설치 스크립트(X-63)와 컨테이너 배포(X-64부터 X-66) 구현

> 결정은 [decisions.md](../decisions.md) D-146, D-147이고 세부 설계와 선생님의 지시는 [roadmap.md](../roadmap.md)의 X-63 구간에 있다.
> 이 문서는 **구현이 어떻게 생겼고 무엇을 시험했으며 무엇을 시험하지 못했는지**를 적는다. 사용법은 저장소의 `install/README.md`이다.

## 1. 구조

| 층 | 파일 | 역할 |
|---|---|---|
| 입력 | `lib/answers.sh`, `lib/ps/Core.ps1` | 옵션, 답 파일, 환경 변수를 우선순위대로 읽고 검증한다. **답 항목표는 셸과 PowerShell이 같고** `test/scenarios.bats`가 지킨다 |
| 메시지 | `messages/{ko,en}.properties` | 두 구현이 같은 파일을 읽는다. 시스템 언어가 한국어이면 한국어, 아니면 영어 |
| 릴리스 | `lib/releases.sh`, `Core.ps1` | GitHub 릴리스 JSON에서 v2 이상 최근 4건을 고른다(`jq` 없이 `sed`, `awk`). 사전 배포는 `(rc)`로 표시한다. sha256 digest로 검증한다 |
| 산출물 | `lib/render.sh`, `lib/ps/Render.ps1` | 설정, 비밀 값 파일, SQL, 서비스 설정, 결과 문서의 **내용만** 만든다(파일을 쓰지 않는다). 공통 골든 파일(`scenarios/`)과 바이트 단위로 같다 |
| 계획 | `lib/plan.sh`, `Flow.ps1` | 어떤 단계를 어떤 권한으로 하는지(`--dry-run`, 격상 동의 화면) |
| 점검 | `lib/preflight.sh`, `Flow.ps1` | JDK, 포트, 디스크, 메모리, 권한, 비밀번호 파일, 컨테이너 도구를 **한 번에 모아** 보인다 |
| 적용 | `lib/apply.sh`, `New-YiApplyScript` | 관리자 권한이 필요한 단계를 **한 스크립트로 모아** 한 번에 격상해 실행한다. 단계마다 되돌리는 명령을 기록(journal)에 남기고 실패하면 이번 실행이 만든 것만 되돌린다 |
| 업그레이드, 제거 | `lib/upgrade.sh` | jar만 바꾸고 상태 확인에 실패하면 옛 jar로 복구한다. 제거는 `--purge`가 아니면 데이터를 남긴다 |
| 흐름 | `lib/flow.sh`, `Flow.ps1` | 신규, 이관(v1.6), 업그레이드, 제거를 잇는다 |
| 배포 | `bootstrap-*.sh`, `bootstrap.ps1`, `build-release.sh` | 한 줄 설치. 고른 릴리스의 설치 묶음을 받아 검증한 뒤 실행한다 |

**구현하며 정한 설계**
- **비밀 값은 `conf/secrets.properties`(소유자만 읽기)에 둔다.** 설정 파일(`application.yml`)에는 넣지 않고 `spring.config.import`로 가져온다. 비밀번호는 보이는 ASCII만 받는다(개행과 한글은 설정 파일과 SQL에 안전하게 쓰기 어렵다).
- **컨테이너 DB는 포트를 `127.0.0.1`에만 공개하고**, 비밀번호는 `--env-file`로 넘긴다(명령줄과 `docker inspect`에 남지 않는다). 컨테이너 안의 접속은 호스트 `%`로 만든다.
- **MariaDB 컨테이너의 임시 서버를 피한다.** 이미지는 초기화 중에 소켓 전용 임시 서버를 먼저 띄워 `ping`이 너무 일찍 성공하므로, 대기와 SQL 실행은 TCP(`127.0.0.1`)로 한다(통합 시험이 잡은 경쟁 상태).
- **bash 3.2(macOS 기본)에서 돈다**: 연관 배열을 쓰지 않고, `$( )` 안의 `case` 패턴 괄호와 `${var//a/$b}`의 `&`(bash 5.2부터 특별한 문자)를 피했다. 둘 다 시험으로 잡았다.
- **systemd 유닛**: `ExecStartPre`의 `$`를 systemd가 변수로 읽으므로 대기 로직은 별도 스크립트(`wait-for-port.sh`)로 뺐다. `SuccessExitStatus=143`을 둔다.
- **sshd 연동은 드롭인 파일 하나**(`/etc/ssh/sshd_config.d/50-yona.conf`)를 두고 `sshd -t`로 검사한 뒤 reload한다. 서비스 이름은 `ssh`와 `sshd`를 모두 찾는다.
- **이관은 서버를 켜기 전에** 준비한 jar로 이관 스크립트를 부른다. MariaDB/MySQL은 제자리, H2는 새 DB로 옮긴다(옛 DB 접속 키는 지운다).

## 2. 시험과 결과 (2026-11-20, 이 컴퓨터는 Linux)

| 시험 | 방법 | 결과 |
|---|---|---|
| 셸 단위와 흐름 | bats | 214건 통과 |
| 정적 검사 | shellcheck | 경고 없음(스크립트를 만드는 코드의 `SC2016`은 의도해서 끈 곳이 있다) |
| bash 3.2 호환 | `bash:3.2` 컨테이너에서 같은 라이브러리와 골든 파일 | 통과 |
| PowerShell | Pester, Linux의 pwsh 컨테이너 | 57건 통과(셸과 같은 골든 파일, 릴리스 읽기, 검증, 계획, dry-run, 질문, 적용 스크립트 문법) |
| 적용 스크립트 | 컨테이너(root)에서 실제 실행: 계정, 소유권, 권한, 유닛 파일, 기록, **실패 시 되돌리기**(이미 있던 파일은 보존) | 통과 |
| 일반 사용자 설치 | 컨테이너(비루트)에서 **진짜 Yona jar**로 같은 폴더 jar(폐쇄 환경 방식), H2: 설치, 시작, 상태 확인 UP, 같은 버전 감지, 중지, 제거 | 통과(약 27초) |
| 컨테이너 DB | 실제 docker로 PostgreSQL 16, MariaDB 12.3.3: 127.0.0.1 공개, 관리자 비밀번호로 DB와 사용자 생성, 사용자 접속, 인코딩과 정렬 규칙, 되돌리면 컨테이너와 볼륨 삭제 | 통과(두 번 반복) |
| 한 줄 설치 | 가짜 릴리스(file://)로 받기, 검증, 풀기, 인자 전달, **변조하면 중단** | 통과 |
| 이관 | 가짜 이관 스크립트로 호출 인자(`--v16-conf`, 데이터 폴더, `V16_TIMEZONE`), 변환된 속성 파일 설치, 실패 시 설치하지 않음 | 통과 |
| Dockerfile(X-64) | 진짜 jar로 빌드: 비루트(10001), 읽기 전용 루트, 새 볼륨 소유권, HEALTHCHECK healthy, `docker stop` 종료 코드 143, 재생성 뒤 데이터 유지 | 통과 |
| compose(X-65) | 구조 검증(YAML, latest 금지, 비밀번호 파일, 프로파일, 상태 확인). **같은 구성을 `docker run`으로**: PostgreSQL 16과 MariaDB 12.3.3에서 시작해 스키마(테이블 50개 초과) 생성, configtree로 비밀번호 파일 읽기(틀린 값이면 시작 실패), 환경 변수로 내장 SSH 켜기, MariaDB `utf8mb4_bin` | 통과 |
| Helm(X-66) | `helm lint`, 값 조합별 `helm template`, 렌더링 규칙(복제본 1, 비루트, 읽기 전용 루트와 `/tmp`, 프로브, Secret을 파일로, 볼륨 보존), `kubectl --dry-run=client` | 통과 |

시험이 잡은 실제 결함(고쳤다): `msg`의 `&` 치환, `$( )` 안의 `case`(bash 3.2), systemd의 `$` 해석, MariaDB 초기화 경쟁 상태, 컨테이너 DB의 바깥 포트가 안쪽 포트를 바꾸던 문제, 이관의 `application.properties`가 설치되지 않던 문제, PowerShell StrictMode의 초기화되지 않은 변수, compose YAML의 콜론.

## 3. 시험하지 못한 것 (구현 전에 알고 있어야 한다)

- **Windows 실기와 macOS 실기**: 관리자 확인, UAC 격상, WinSW 서비스 등록, `LocalService` 계정, PowerShell 5.1, Mac의 `launchctl`, `SUDO_USER`, SDKMAN. Windows 전용 코드는 문법과 렌더링까지만 시험했다. `irm | iex`의 UAC 재실행도 확인하지 못했다.
- **시스템 패키지로 DB 설치**(apt, dnf): 명령은 만들지만 systemd가 있는 환경에서 실행하지 못했다. MariaDB의 `root` 비밀번호 인증 전환 SQL도 확인하지 못했다.
- **systemd, `systemctl reload`와 부팅 자동 시작, 재부팅 시험**: 컨테이너에는 systemd가 없어 `systemctl`은 가짜로 시험했다. 시스템 sshd 연동의 실제 reload와 접속도 확인하지 못했다(드롭인과 `sshd -t`는 네 배포본에서 확인했다).
- **시스템 sshd 연동에 필요한 `yona-ssh` 실행 파일과 Windows의 WinSW**는 아직 릴리스 자산이 아니다. 각각 `--ssh-helper`, 같은 폴더의 파일로 준다.
- **v1.6 이관의 실제 실행**: 이관 스크립트는 가짜로 시험했다. 진짜 v1.6 데이터로 처음부터 끝까지는 돌리지 못했다(`v16-migration-guide.md`의 리허설은 이관 스크립트 자체의 시험이다).
- **SQL Server와 Cubrid의 컨테이너 설치**: 환경 파일과 SQL은 만들고 렌더링을 시험했지만 실제 컨테이너로 끝까지 돌리지 못했다. Cubrid의 데이터베이스 생성은 이미지의 환경 변수(`CUBRID_DB`)에 기댄 가정이다.
- **`podman compose` 호환, 실제 docker compose 실행, 쿠버네티스 클러스터 설치**: 도구가 없어 하지 못했다.
- **외부 접속이 필요한 부분**: GitHub 릴리스 API와 `ghcr.io` 게시는 실제로 해 보지 않았다(가짜 릴리스와 로컬 이미지로 시험). 지금 릴리스에는 jar만 있어 릴리스 절차가 설치 묶음(`build-release.sh`의 산출물), 이관 스크립트, H2 변환기를 자산으로 올려야 한다.
