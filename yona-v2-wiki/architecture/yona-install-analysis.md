---
title: 옛 설치 도구 yona-install(Python) 분석
type: reference
updated: 2026-11-20
---

> 이 분석은 X-63 설치 프로그램([install-scripts.md](install-scripts.md))의 출발점이었다. 분석한 Python 코드(`~/yona-convert/yona-install`)는
> v2 설치 프로그램이 대신하므로 지웠다(2026-11-20). 원본은 `git clone git@github.com:yona-projects/yona-install.git`(최종 커밋 `0131390`)으로 다시 받을 수 있다.

# yona-install 코드 분석

- 분석 대상: `git@github.com:yona-projects/yona-install.git` (최종 커밋 `0131390`, 2018-12-17)
- 분석 방법: 소스를 읽기만 했고 어떤 코드도 실행하지 않았다. 아래 줄 번호는 `install.py` 기준이다.
- 분석일: 2026-11-20

## 1. 저장소 개요

- **정체**: Yona 1.x(Play 2.3) 설치 자동화 도구이다. 이름은 README 제목 "Yonafi", 패키지명 `yona`, 저장소명 `yona-install`로 서로 다르다.
- **이력**: 커밋 28개이고 2018-09-28부터 12-17까지 작성되었다. 마지막 변경은 GitHub 주소를 고친 PR #3(akageun)이며, 이후 7년간 변경이 없다.
- **구성**: `install.py`(225줄, 핵심), `properties.py`(21줄), `settings.yml`, `setup.py`, `Pipfile`, `README.md`, `MANIFEST.in`, `.gitignore`가 전부이다. 시험, CI, 라이선스 파일은 없다.
- **대상 환경**: Debian 8/9, Ubuntu, Python 3.5 이상, root 실행이다. 배포본은 MariaDB를 포함한 zip이다.
- **의존성**: `requests`, `lxml`, `beautifulsoup4`, `click`, `pyyaml`, `pexpect`가 쓰인다. `future`는 선언만 되어 있고 쓰이지 않는다.

## 2. 동작 흐름 (`main`)

| 단계 | 줄 | 하는 일 |
|---|---|---|
| 1. 버전 확인 | L20~41 | GitHub 릴리스 HTML을 스크래핑해 "최신 버전"을 보여 주고 설치 여부를 묻는다. |
| 2. 다운로드 | L43~66 | 링크 중 `mariadb`가 들어간 자산을 `~/.yona_install`에 받는다. 크기가 다르면 다시 받는다. |
| 3. 설치 경로 | L68~107 | 기본값은 `~/yona-latest`이다. 최상위 폴더를 벗기며 압축을 풀고 권한을 복원한다. |
| 4. 운영체제 검사 | L113~130 | `lsb_release`를 파싱해 Ubuntu, Debian의 배포본 코드명이 지원 목록에 있는지 본다. |
| 5. 패키지 설치 | L133~165 | `dirmngr`, MariaDB GPG 키, 제3자 apt 저장소, `openjdk-9-jre`, `mariadb-server` 순서로 설치한다. |
| 6. DB 준비 | L169~190 | `settings.yml`을 읽고 `pexpect`로 `mysql -u root`에 사용자, DB(utf8mb4/utf8mb4_bin), 권한을 만든다. |
| 7. 서버 설정 | L193~209 | `/etc/my.cnf`를 새로 쓰고(utf8mb4, `lower_case_table_names=1`) MariaDB를 재시작한다. |
| 8. Yona 설정 | L212~219 | `bin/yona`를 한 번 실행하고 3초 쉰 뒤, `properties.db_settings`가 `application.conf`의 `# MariaDB` 아래 4줄을 활성 설정으로 바꾼다. |

`properties.py`는 `# MariaDB` 제목 줄을 찾아 그 뒤 4줄을 지우고, 드라이버, URL(`useServerPrepStmts=true`), 사용자, 비밀번호 4줄을 끼워 넣는다.

## 3. 문제점

### 3.1 지금은 동작하지 않을 가능성이 큰 것

| 위치 | 내용 |
|---|---|
| L24~26 | 릴리스 HTML의 `div.release-entry`를 긁고 `[2]`(세 번째 항목)를 "최신"으로 가정한다. GitHub 화면이 바뀌어 `IndexError`가 날 가능성이 크다. |
| L47~54 | `mariadb` 자산을 못 찾으면 `None`으로 `https://github.com/None`을 요청한다. |
| L123~130 | 지원 코드명이 2018년 목록(artful, bionic, xenial, jessie, stretch 등)뿐이다. 지금의 Ubuntu와 Debian은 전부 "지원 안 됨"으로 끝나며, 그것도 파일을 풀어 놓은 뒤이다. |
| L139~144 | `apt-key adv`는 최신 배포본에서 제거되었고, 오류는 `PIPE`로 삼켜진다. |
| L146~150 | MariaDB 저장소가 개인 미러(`ftp.harukasan.org`)의 10.3.9로 고정되어 있고 HTTP이다. 미러가 사라졌을 수 있다. |
| L160 | `openjdk-9-jre`는 이미 저장소에서 사라졌다. |
| L169 | `yaml.load(open(...))`에 `Loader`가 없어 PyYAML 6에서는 `TypeError`이다. 상대 경로(`settings.yml`)라 저장소 폴더에서 실행해야만 읽힌다. |
| L179~181 | `innodb_file_format`, `innodb_large_prefix`는 최신 MariaDB에서 의미가 없거나 오류가 날 수 있다. |

### 3.2 논리 결함

- **L117~120**: 우분투와 데비안이 아니면 메시지만 찍고 계속 진행한다(`exit`가 없고 TODO만 있다).
- **L136, L160, L164**: `apt install`에 `-y`가 없고 출력을 `PIPE`로 막아 프롬프트가 보이지 않는다. `communicate(b"y\n")`로 대신 응답하지만 반환 코드는 한 번도 확인하지 않는다. L136은 `communicate`도 없어 멈출 수 있다.
- **L176~190**: SQL을 `sendline`만 하고 결과를 기다리지 않은 채 바로 `c.close()`한다. `mysql`이 명령을 다 처리하기 전에 종료될 수 있다.
- **L212**: `shell=True`에 리스트를 넘기고 `bin/yona`를 foreground로 돌린다. 이 호출이 끝나지 않으면 이후 단계(L214~219)에 도달하지 못한다. 의도는 첫 실행으로 설정 파일을 만드는 것으로 보이나 확신할 수 없다.
- **`properties.py`**
  - `# MariaDB` 제목 줄이 없으면 아무 일도 하지 않고 성공으로 끝난다.
  - 뒤의 정확히 4줄을 지운다고 가정한다.
  - 사용자와 비밀번호를 HOCON에 따옴표 없이 쓴다. 비밀번호에 공백이나 `#`이 있으면 깨진다.
- **L193**: `/etc/my.cnf`를 통째로 덮어쓴다. 기존 설정이 사라지고, Debian은 `/etc/mysql/my.cnf`를 쓰므로 적용되지 않을 수도 있다.
- **L71~74**: 설치 폴더를 만들지 못해도 메시지만 찍고 계속 진행한다. `mkdir`에 `parents=True`도 없다.
- **L57~64**: 파일이 이미 있어도 크기를 비교하려고 다운로드 요청을 먼저 보낸다. 진행률은 청크마다 1MB씩 더해 마지막 조각에서 실제보다 커진다.
- **sudo 사용의 일관성**: 일부 명령은 `sudo`, 일부(`apt update`, `systemctl`)는 `sudo` 없이 실행한다. README는 root 실행을 전제하므로 `sudo`는 불필요하다.

### 3.3 보안

- `settings.yml`의 DB 비밀번호 `yonadan`이 공개 저장소에 고정되어 있고 바꿀 방법이 없다.
- 제3자 미러를 HTTP로 apt 저장소에 등록한다.
- `install_path`를 사용자 입력 그대로 `shell=True`로 넘긴다(L212). 로컬에서 root가 직접 입력하는 값이라 위험은 낮지만 명령 주입 형태이다.
- 내려받은 압축 파일의 체크섬이나 서명은 검증하지 않는다.
- 설치 프로그램이 `mysql -u root`를 비밀번호 없이 쓰므로 `unix_socket` 인증과 root 실행에 의존한다.

### 3.4 패키징

- `setup.py`의 `find_packages()`는 아무것도 찾지 못한다(평면 모듈). 그래서 `yona_install = install:main` 진입점은 `install`을 가져오지 못한다.
- `settings.yml`은 `MANIFEST.in`에만 있고 패키지 데이터 설정이 없다.
- `Pipfile`은 Python 3.6을 요구한다.

## 4. Yona v2 개발과의 관계

- **그대로 쓸 수 없다**
  - v2는 Spring Boot이고 JDK 21 이상이다.
  - `bin/yona`와 `application.conf`가 없다.
  - DB는 H2, PostgreSQL, MariaDB, Cubrid 등이다.
  - 설정은 `application.yaml`과 `yona.*` 키이다.
- **참고할 만한 것**
  - MariaDB 준비 요건: utf8mb4, `utf8mb4_bin`, `lower_case_table_names=1`, `GRANT ALL`. v2 MariaDB 설치 문서와 대조해 빠진 것이 없는지 확인할 수 있다.
  - "다운로드, 압축 풀기, DB 준비, 설정 반영"이라는 자동화 순서.
- **v2에서 새로 필요한 것**
  - systemd 등록(이 저장소 README의 Future 항목에도 있다)
  - SSH 설정 키(`yona.ssh.mina.*`)
  - 데이터 폴더
  - 키 설정 파일 생성
  - DB 선택

## 5. 후속 작업 후보

1. 이 분석을 `yona-v2-wiki`의 참고 문서로 옮기기
2. v2용 설치 스크립트 설계와 구현(Linux, DB 선택, systemd 포함)
