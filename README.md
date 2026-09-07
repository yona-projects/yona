<a name="korean"></a>[[English]](#english)

# yona

<img src="src/main/resources/static/images/yona-logo.png" width="220px" alt="Yona logo">

##### 21세기 협업 개발 플랫폼

- Official Site: [http://yona.io](http://yona.io)
- 이 저장소는 [Yona](https://github.com/yona-projects/yona)(Play Framework/Java/Ebean
  기반의 설치형 프로젝트 협업 플랫폼)를 **Kotlin + Spring Boot + JPA(Hibernate)** 스택으로 새로 옮겨
  쓴 프로젝트입니다. 화면 구조·데이터 모델·동작 방식은 legacy Yona와 최대한 동일하게 유지하면서,
  런타임과 빌드 도구만 현재 JVM 생태계로 교체하는 것을 목표로 합니다.

## Yona란?

- Git/SVN 저장소가 내장된 설치형 이슈 트래커 + 게시판 + 코드 리뷰 플랫폼
- 네이버/네이버랩스를 비롯해 여러 기업·공공기관에서 수년간 실사용되며 다듬어진 애플리케이션

### 주요 기능

- 서비스 종료나 데이터 종속 걱정 없는 설치형
- 프로젝트 기반의 유연한 이슈 트래커와 게시판 — 프로젝트 간 이슈 이동, 서브 태스크, 본문 변경이력,
  이슈 템플릿
- 내장 코드 저장소 — Git/SVN 선택 가능, 온라인 수정·커밋, 프로젝트 멤버 전용 접근 제어
- 블록 기반 코드 리뷰 — 코드 블록 단위 리뷰 스레드, 리뷰 점수
- 그룹(조직) 기능 — 그룹 단위 이슈/게시글 통합 관리, 그룹 프로젝트·멤버
- 한글 기반 — 프로젝트 이름 및 그룹 이름에 한글 사용 가능
- LDAP 지원 및 소셜 로그인(OAuth2)
- 다른 서비스·다른 Yona 인스턴스로의 마이그레이션(GitHub 프로젝트 Import 등)

### 추가 읽을거리

- [왜 Yona를 써야 하나요? (Why Yona?)](https://repo.yona.io/yona-projects/yona/post/3)
- [기본 워크플로우](https://repo.yona.io/yona-projects/yona-help/post/2)

## Yona (New): 무엇이 바뀌었나

| | Old Yona | New Yona |
|---|---|---|
| 언어 | Java / Scala 템플릿 | Kotlin |
| 프레임워크 | Play Framework 2.x | Spring Boot |
| ORM | Ebean | JPA / Hibernate |
| 뷰 엔진 | Scala Template(`.scala.html`) | Thymeleaf |
| JDK | Java 8 | Java 21 |
| 지원 DB | MariaDB(기본) 또는 H2(내장형) | **MariaDB / PostgreSQL / MySQL / SQL Server / CUBRID / H2(내장형)** |

포팅 진행 상황과 legacy 대비 의도적으로 남겨둔 차이점은 `docs/parity/index.md`,
`docs/TEMPLATE_BACKLOG.md`, `docs/coverage/index.md`에 기록돼 있습니다.

## 요구 사항

- JDK 21
- 운영/테스트 DB 중 하나: MariaDB(기본), PostgreSQL, MySQL, SQL Server, CUBRID, H2(설치 없이 바로 써보기)

## 빌드 & 실행

```bash
# Linux / macOS
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

테스트:

```bash
./gradlew test        # Linux/macOS
gradlew.bat test       # Windows
```

## 데이터베이스 선택

기본 Spring 프로파일은 `mariadb`입니다. 다른 DB로 운영하려면 `spring.profiles.active`를
아래 중 하나로 지정하세요(`src/main/resources/application.yml`에 각 프로파일의 접속 설정이 있습니다).
로컬 개발용 MariaDB/PostgreSQL 컨테이너는 저장소 루트의 `docker-compose.yml`로 바로 띄울 수 있습니다.

| 프로파일 | DB |
|---|---|
| `mariadb` (기본값) | MariaDB |
| `postgres` | PostgreSQL |
| `mysql` | MySQL |
| `mssql` | Microsoft SQL Server |
| `cubrid` | CUBRID |
| `h2` | H2(내장형) — Docker/별도 서버 설치 없이 파일 기반으로 바로 실행(`./data/h2/yona`) |

```bash
java -jar yona.jar --spring.profiles.active=postgres

# 설치 없이 바로 써보기(H2)
java -jar yona.jar --spring.profiles.active=h2
```

통합 테스트는 실제 Docker 컨테이너(Testcontainers) 기준으로 5개 DB 전부 검증돼 있습니다.
특정 DB로만 테스트를 돌리려면(**동시에 두 개 이상 돌리면 gradle 빌드 출력 디렉터리가
꼬이니 항상 한 번에 하나씩만 실행하세요**):

```bash
./gradlew test -Dyona.it.db=postgres   # mariadb|postgres|mysql|mssql|cubrid
```

## 운영 환경 설정 (특히 Windows)

물리 저장소(git bare repo, svn repo, git-lfs 객체, 첨부파일 업로드)를 디스크의 어느 경로에 둘지는
아래 4개 설정으로 제어합니다. 기본값이 `/tmp/yona/...` 형태의 유닉스 절대경로이기 때문에,
**Windows에서 운영할 때는 반드시 아래 값들을 Windows 경로로 재설정해야 합니다.**

| 설정 키 | 기본값 | 용도 |
|---|---|---|
| `yona.git.base-dir` | `/tmp/yona/git` | Git bare 저장소 루트 |
| `yona.svn.base-dir` | `/tmp/yona/svn` | SVN 저장소 루트 |
| `yona.lfs.base-dir` | `/tmp/yona/lfs` | Git LFS 객체 저장 루트 |
| `yona.upload.base-dir` | `${yona.data:data}/uploads` (상대경로) | 첨부파일 업로드 루트 |

### 설정 변경 방법

1. **`application.yml`에 직접 지정** (가장 확실한 방법)

   ```yaml
   yona:
     git:
       base-dir: "D:/yona-data/git"
     svn:
       base-dir: "D:/yona-data/svn"
     lfs:
       base-dir: "D:/yona-data/lfs"
     upload:
       base-dir: "D:/yona-data/uploads"
   ```

   Windows 경로도 슬래시(`/`)로 적으면 됩니다(자바가 두 구분자를 모두 인식합니다). 백슬래시를 쓸
   경우 YAML 이스케이프 때문에 `\\`로 두 번 써야 하므로, 슬래시 표기를 권장합니다.

2. **실행 시 커맨드라인 인자로 지정** (`application.yml`을 건드리지 않고 배포별로 다르게 줄 때)

   ```powershell
   java -jar yona.jar --yona.git.base-dir=D:\yona-data\git --yona.svn.base-dir=D:\yona-data\svn --yona.lfs.base-dir=D:\yona-data\lfs --yona.upload.base-dir=D:\yona-data\uploads
   ```

   `-D`로 JVM 시스템 프로퍼티를 주는 방식(`java -Dyona.git.base-dir=D:\... -jar yona.jar`)도 동일하게 동작합니다.

3. **환경 변수** — Spring Boot의 relaxed binding 규칙상 `yona.git.base-dir`에 대응하는 환경 변수명은
   `YONA_GIT_BASEDIR`처럼 하이픈(`-`)이 빠진 형태입니다(다른 `YONA_*` 설정들처럼 밑줄로 치환되는 게
   아님). 헷갈리기 쉬우므로 **1번(yml) 또는 2번(커맨드라인 인자) 방식을 권장**합니다.

### Windows에서 Fork(하드링크 복제) 사용 시 전제 조건

프로젝트 Fork는 저장소를 실제로 복사하지 않고 파일시스템 하드링크로 복제합니다
(`ProjectServiceImpl.cloneHardLinkedRepository`). 이 방식이 정상 동작하려면:

- `yona.git.base-dir`(및 `yona.svn.base-dir`) 전체가 **하나의 NTFS 볼륨(드라이브)** 안에 있어야
  합니다. 서로 다른 드라이브 간에는 하드링크가 불가능해 Fork가 실패합니다(폴백 복사 없음, 의도적 설계).
- 저장 위치가 **NTFS**여야 합니다. FAT32/exFAT로 포맷된 외장 디스크나 일부 네트워크 드라이브는
  하드링크 자체를 지원하지 않아 Fork가 실패합니다.

## 서버 관련 설정

- LDAP: `application.yml`의 `ldap` 섹션
- 소셜 로그인(OAuth2): `application.yml`의 `spring.security.oauth2` 섹션

## Google Analytics

- legacy와 동일하게 Google Analytics 트래킹 스크립트가 실제로 구현되어 있습니다
  (`GlobalModelAttributeAdvice`가 `sendYonaUsage` 모델 속성을 채우면 `templates/site/layout.html`이
  그 값에 따라 GA 스크립트를 렌더링합니다).
- **다만 기본값은 legacy(`application.send.yona.usage = true`, 기본 켜짐)와 반대로 꺼짐(`false`)
  입니다.** 켜고 싶다면 `application.yml`에서 아래 항목을 `true`로 설정합니다.

  ```yaml
  yona:
    analytics:
      send-usage: true
  ```

## 마이그레이션

- GitHub 프로젝트를 이 저장소로 Import하는 기능을 제공합니다(`MigrationService`,
  `MigrationApiController`). `github.client.id` / `github.client.secret` / `github.allow.migration`
  설정으로 활성화합니다.
- Yona의 별도 백업/이관 도구인 [Yona Export](https://github.com/yona-projects/yona-export)는
  이 저장소(Kotlin/Spring 이식판) 대상으로는 아직 포팅되지 않았습니다 — 진행 상황은
  `docs/parity/index.md` 참고.

## Contribution

- 코드 기여의 기준이 되는 브랜치는 `main`입니다.
- 저장소를 fork한 다음 `main` 브랜치를 기준으로 작업하신 다음 `main` 브랜치로 pull request를
  보내주세요.

## 운영 가이드

legacy Yona의 설치/운영 문서를 yona 기준으로 다시 쓴 것들이다 — `docs/guide/`:

- [설치](docs/guide/install.md)
- [실행 및 재시작](docs/guide/run-and-restart.md)
- [실행 옵션](docs/guide/run-options.md)
- [업그레이드](docs/guide/upgrade.md)
- [백업 및 복구](docs/guide/backup-restore.md)
- [메일 알림 설정](docs/guide/mail-settings.md)
- [소셜 로그인 설정](docs/guide/social-login-settings.md)
- [트러블슈팅](docs/guide/troubleshooting.md)
- [설정 레퍼런스(application.yml)](docs/guide/settings-reference.md)
- [시스템 요구 사항](docs/guide/system-requirements.md)
- [사용자 가이드](docs/guide/user-manual/TOC.md) — 화면별 사용법
- [기술 문서](docs/guide/technical/README.md) — 권한 규칙, JS 모듈 구조, 첨부파일/웹훅/마크다운
  내부 동작 등

영문 버전은 legacy와 동일하게 별도 위치에 있다 — 운영 문서는 [`docs/*.md`](#operations-guide),
사용자 가이드는 [`docs/userManual/`](docs/userManual/TOC.md), 기술 문서는 영문 원본이
있던 5개(markdown/mailbox/watch/label-typeahead/name-validation)만 [`docs/technical/`](docs/technical/markdown.md)에
있다(나머지 11개는 legacy도 한글 전용이었다).

서비스로 상시 구동할 때 참고할 systemd 유닛 예시와 DB 튜닝 샘플은 [`support-script/`](support-script/README.md)에 있다.

legacy Yona(Yobi/nFORGE 포함)의 설계 스펙·비전 문서·릴리즈노트는 원문 그대로
[`docs/legacy-reference/`](docs/legacy-reference/README.md)에 보존되어 있다.

## 코드 구조 개요

`docs/parity/index.md`, `docs/TEMPLATE_BACKLOG.md`, `docs/coverage/index.md`에 legacy yona 대비
이식 진행 상황과 의도적으로 남겨둔 차이점들이 기록되어 있습니다.

**문서 작성 규칙**: `docs/parity/`, `docs/coverage/`, `docs/golden/`는 `docs/yona-wiki/` 방식(인덱스는
표만, 상세 서술은 개별 파일)을 따릅니다. 신규 티켓/배치/발견 사항을 기록할 때는 인덱스 표 아래에
서술을 이어 쓰지 말고, 해당 디렉터리의 새 파일(`tickets/<id>.md`, `batches/<slug>.md`,
`findings/<slug>.md` 등)을 만든 뒤 인덱스에 표 한 줄과 링크만 추가하세요.

## 라이선스

yona는 [Apache License 2.0](LICENSE)으로 제공됩니다.
서드파티 구성 요소 고지는 [NOTICE](NOTICE), 원 프로젝트 기여자 명단은 [AUTHORS](AUTHORS)를
참고하세요.

<br/>

<a name="english"></a>[[한국어]](#korean)

# yona

Yona is a web-based project hosting software.

- Official Site (original project): [http://yona.io](http://yona.io)
- This repository ([`search5/yona`](https://github.com/search5/yona)) is a rewrite of the original [Yona](https://github.com/yona-projects/yona)
  (a self-hosted project collaboration platform built on Play Framework/Java/Ebean) onto a
  **Kotlin + Spring Boot + JPA (Hibernate)** stack. The goal is to keep screen structure, data
  model, and behavior as close as possible to legacy Yona while replacing only the runtime and
  build tooling with the current JVM ecosystem.

## What is Yona?

- A self-hosted issue tracker + bulletin board + code review platform with an embedded Git/SVN
  repository
- An application battle-tested for years at NAVER, NAVER LABS, and various companies and public
  institutions

### Key features

- Self-hosted — no dependency on a third-party service that could shut down or lock in your data
- A flexible, project-based issue tracker and bulletin board — issue transfer between projects,
  sub-tasks, body change history, issue templates
- Embedded code repository — choose Git or SVN, online edit/commit, access restricted to project
  members
- Block-based code review — review threads per code block, review scores
- Group (organization) features — unified management of issues/posts across a group, group
  projects and members
- Korean-friendly — project and group names can use Korean characters
- LDAP support and social login (OAuth2)
- Migration to/from other services or Yona instances (GitHub project import, etc.)

### Further reading (original project resources)

- [Why Yona?](https://repo.yona.io/yona-projects/yona/post/3)
- [Basic workflow](https://repo.yona.io/yona-projects/yona-help/post/2)

## Yona (original) → yona (this port): what changed

| | legacy Yona | yona (this port) |
|---|---|---|
| Language | Java / Scala templates | Kotlin |
| Framework | Play Framework 2.x | Spring Boot |
| ORM | Ebean | JPA / Hibernate |
| View engine | Scala Template (`.scala.html`) | Thymeleaf |
| JDK | Java 8 | Java 21 |
| Supported DB | MariaDB (default) or embedded H2 | **MariaDB / PostgreSQL / MySQL / SQL Server / CUBRID / embedded H2** |

Porting progress and deliberate differences from legacy are tracked in `docs/parity/index.md`,
`docs/TEMPLATE_BACKLOG.md`, and `docs/coverage/index.md`.

## Requirements

- JDK 21
- One of the supported/tested DBs: MariaDB (default), PostgreSQL, MySQL, SQL Server, CUBRID, or embedded H2 (no install needed)

## Build & Run

```bash
# Linux / macOS
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

Tests:

```bash
./gradlew test        # Linux/macOS
gradlew.bat test       # Windows
```

## Choosing a database

The default Spring profile is `mariadb`. To run against a different DB, set
`spring.profiles.active` to one of the profiles below (connection settings for each profile live
in `src/main/resources/application.yml`). A local MariaDB/PostgreSQL container can be started
directly from the `docker-compose.yml` at the repository root.

| Profile | DB |
|---|---|
| `mariadb` (default) | MariaDB |
| `postgres` | PostgreSQL |
| `mysql` | MySQL |
| `mssql` | Microsoft SQL Server |
| `cubrid` | CUBRID |
| `h2` | Embedded H2 — runs immediately from a local file (`./data/h2/yona`), no Docker/server install |

```bash
java -jar yona.jar --spring.profiles.active=postgres

# Try it with zero setup (H2)
java -jar yona.jar --spring.profiles.active=h2
```

Integration tests are verified against all 5 server DBs using real Docker containers
(Testcontainers); H2 is embedded and needs no container. To run tests against a single DB
(**never run two or more at once — the gradle build output directory gets corrupted; always run
one at a time**):

```bash
./gradlew test -Dyona.it.db=postgres   # mariadb|postgres|mysql|mssql|cubrid|h2
```

## Deployment configuration (especially on Windows)

Where physical storage lives on disk (git bare repos, svn repos, git-lfs objects, attachment
uploads) is controlled by the 4 settings below. The defaults are Unix absolute paths in the form
`/tmp/yona/...`, so **when operating on Windows you must reconfigure these to Windows paths.**

| Setting key | Default | Purpose |
|---|---|---|
| `yona.git.base-dir` | `/tmp/yona/git` | Git bare repository root |
| `yona.svn.base-dir` | `/tmp/yona/svn` | SVN repository root |
| `yona.lfs.base-dir` | `/tmp/yona/lfs` | Git LFS object storage root |
| `yona.upload.base-dir` | `${yona.data:data}/uploads` (relative) | Attachment upload root |

### How to change these settings

1. **Set directly in `application.yml`** (the most reliable way)

   ```yaml
   yona:
     git:
       base-dir: "D:/yona-data/git"
     svn:
       base-dir: "D:/yona-data/svn"
     lfs:
       base-dir: "D:/yona-data/lfs"
     upload:
       base-dir: "D:/yona-data/uploads"
   ```

   Windows paths can be written with forward slashes (`/`) too — Java accepts both separators.
   If you use backslashes, YAML escaping requires doubling them (`\\`), so forward slashes are
   recommended.

2. **Pass as command-line arguments at runtime** (to vary per deployment without touching
   `application.yml`)

   ```powershell
   java -jar yona.jar --yona.git.base-dir=D:\yona-data\git --yona.svn.base-dir=D:\yona-data\svn --yona.lfs.base-dir=D:\yona-data\lfs --yona.upload.base-dir=D:\yona-data\uploads
   ```

   Passing JVM system properties with `-D` (`java -Dyona.git.base-dir=D:\... -jar yona.jar`) works
   the same way.

3. **Environment variables** — under Spring Boot's relaxed binding rules, the environment
   variable corresponding to `yona.git.base-dir` is `YONA_GIT_BASEDIR` (the hyphen is simply
   dropped, not replaced with an underscore like other `YONA_*` settings). This is easy to get
   wrong, so **option 1 (yml) or option 2 (command-line arguments) is recommended.**

### Prerequisites for using Fork (hard-link cloning) on Windows

Project Fork does not physically copy the repository — it clones via filesystem hard links
(`ProjectServiceImpl.cloneHardLinkedRepository`). For this to work correctly:

- `yona.git.base-dir` (and `yona.svn.base-dir`) must live entirely within **a single NTFS
  volume (drive)**. Hard links cannot cross drives, so Fork fails between different drives (no
  copy fallback — this is intentional).
- The storage location must be **NTFS**. External disks formatted as FAT32/exFAT, and some
  network drives, don't support hard links at all, so Fork fails there.

## Server settings

- LDAP: the `ldap` section of `application.yml`
- Social login (OAuth2): the `spring.security.oauth2` section of `application.yml`

## Google Analytics

- The Google Analytics tracking script is actually implemented, same as legacy
  (`GlobalModelAttributeAdvice` populates a `sendYonaUsage` model attribute, and
  `templates/site/layout.html` renders the GA script based on that value).
- **Unlike legacy (`application.send.yona.usage = true`, on by default), the default here is
  off (`false`).** To enable it, set the following to `true` in `application.yml`.

  ```yaml
  yona:
    analytics:
      send-usage: true
  ```

## Migration

- Provides GitHub project import into this repository (`MigrationService`,
  `MigrationApiController`). Enable it via the `github.client.id` / `github.client.secret` /
  `github.allow.migration` settings.
- The original Yona's separate backup/migration tool,
  [Yona Export](https://github.com/yona-projects/yona-export), has not yet been ported to this
  repository (the Kotlin/Spring port) — see `docs/parity/index.md` for status.

## Contribution

- The branch for contributions is `main`.
- Fork the repository, work on top of the `main` branch, then send a pull request to the `main`
  branch.

## Operations guide

legacy Yona kept English docs at `docs/*.md` and Korean docs at `docs/ko/*.md`. yona keeps that
same split: this section lists the English set (same filenames/locations as legacy), rewritten
for yona. The Korean set lives under `docs/guide/` — see the [운영 가이드](#운영-가이드) section
above (it's reorganized slightly differently, e.g. install docs merged into one file, so the two
sets aren't a strict 1:1 file mirror).

- [Install (MariaDB)](docs/install-mariadb.md)
- [Install (yona server)](docs/install-yona-server.md)
- [Run and restart](docs/yona-run-and-restart.md)
- [Run options](docs/yona-run-options.md)
- [Upgrade](docs/yona-upgrade.md)
- [Backup and restore](docs/yona-backup-restore.md)
- [Mail notification settings](docs/yona-mail-settings.md)
- [Social login settings](docs/yona-social-login-settings.md)
- [Troubleshooting](docs/trouble-shootings.md)
- [MariaDB 767 byte error](docs/db-error-767.md)
- [application.yml reference](docs/application-conf-desc.md)
- [System requirements](docs/system-requirements.md)
- [Logging](docs/logging.md)

User manual: legacy's `docs/userManual/` was English-only, so it's ported here at the same
location — [`docs/userManual/TOC.md`](docs/userManual/TOC.md). (The duplicate
`projectSetting`/`projectSettings` folders from a legacy typo were merged into one,
`projectSetting/`.)

Technical docs: legacy's `docs/technical/` (English) and `docs/ko/technical/` (Korean) were two
different, non-overlapping sets of files. Only the English set is mirrored here at the same
location — [`docs/technical/`](docs/technical/markdown.md) (markdown, mailbox, watch,
label-typeahead, name-validation). The Korean set's 11 additional topics (access control, JS
module conventions, uploader/webhook internals, etc.) only ever existed in Korean, both in
legacy and here — see [`docs/guide/technical/README.md`](docs/guide/technical/README.md).

For a systemd unit example and DB tuning samples for running this as a standing service, see
[`support-script/`](support-script/README.md).

Design specs, vision docs, and release notes from legacy Yona (including its Yobi/nFORGE
predecessors) are preserved verbatim under
[`docs/legacy-reference/`](docs/legacy-reference/README.md).

## Code structure overview

`docs/parity/index.md`, `docs/TEMPLATE_BACKLOG.md`, and `docs/coverage/index.md` record
porting progress against legacy yona and deliberately preserved differences.

**Documentation convention**: `docs/parity/`, `docs/coverage/`, and `docs/golden/` follow the
`docs/yona-wiki/` style (index = table only, details = individual files). When logging a new
ticket/batch/finding, don't append narrative under the index table — create a new file in the
relevant directory (`tickets/<id>.md`, `batches/<slug>.md`, `findings/<slug>.md`, etc.) and add
just a table row + link to the index.

## License

yona is provided under the [Apache License 2.0](LICENSE), the same license as the original
Yona/Yobi project. See [NOTICE](NOTICE) for third-party component notices and [AUTHORS](AUTHORS)
for the original project's contributor list.
