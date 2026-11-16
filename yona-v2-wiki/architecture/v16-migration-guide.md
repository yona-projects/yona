# v1.6 데이터베이스를 이어받아 v2로 올리는 이관 가이드

v1.6(Play 2.3, MariaDB)이 만든 데이터베이스를 그대로 두고 v2 서버를 올릴 때 일어나는 변화와, 반드시 해야 하는 이관을 정리한다.
같은 데이터베이스 종류(MariaDB/MySQL)를 계속 쓰는 경우를 다룬다. 데이터베이스 종류를 바꾸는 경우는 사이트 데이터 내보내기와 가져오기(`/sites/data`)를 쓴다.

2026-11-16에 실제 MariaDB 10.11에서 이 절차를 한 번 수행했다(아래 "리허설 결과"). 운영 데이터가 아니라 v1.6 evolutions 32개로 만든 v1.6 스키마에 합성 데이터를 넣은 것이다.

## 한눈에 보기

| 순서 | 할 일 | 자동 여부 |
|---|---|---|
| 1 | v1.6 서버를 끄고 DB를 백업한다 | 수동 |
| 2 | 이관 스크립트를 `--v16-conf=<v1.6의 application.conf>`로 돌린다: 설정 변환, 스키마 추가, 데이터 이관이 한 번에 일어난다 | 수동(한 번, 다시 돌려도 안전) |
| 3 | 변환된 설정 파일의 보고를 읽고 확인할 키를 손본다 | 수동 |
| 4 | v2를 시작해 확인한다 | 수동 |

## 1. 준비

- **Java 21**이 필요하다. 시스템 기본 `java`가 낮은 버전이면 `UnsupportedClassVersionError`(클래스 파일 65)로 시작하지 못한다.
- v1.6을 끄고 DB를 백업한다(`mariadb-dump`). 이관은 데이터를 바꾸므로 백업 없이 시작하지 않는다.
- v1.6 저장소 디렉터리(`repo/git`, `repo/hg` 등)를 v2의 `yona.data-dir`이 가리키게 한다. 이관 3번이 저장소의 브랜치를 읽는다.
  저장소를 읽지 못하면 경고만 남기고 기본 브랜치 이름 하나로 규칙을 만든다(실패하지는 않지만 브랜치별 규칙이 하나로 줄어든다).

## 2. 설정 옮기기 (이관 스크립트가 변환)

`scripts/migrate-v16.sh`에 v1.6의 `conf/application.conf`를 주면 v2 속성 파일(기본 `./yona-v16.properties`)로 바꾸고, 그 파일을 설정으로 써서 이관까지 이어서 한다.
DB 접속, 비밀 키(`application.secret` -> `yona.secret`), SMTP, 알림, 프로젝트 기본값 등 대응표([configuration-keys.md](configuration-keys.md))에 있는 키를 옮긴다.

```bash
# 설정만 먼저 변환해 내용을 확인하고 싶을 때
scripts/migrate-v16.sh build/libs/yona-2.0.0-SNAPSHOT.jar --v16-conf=/opt/yona-1.6/conf/application.conf --v16-conf-only
# 변환과 이관을 한 번에
scripts/migrate-v16.sh build/libs/yona-2.0.0-SNAPSHOT.jar --v16-conf=/opt/yona-1.6/conf/application.conf --yona.data-dir=/var/yona
```

| 옵션 | 뜻 |
|---|---|
| `--v16-conf=FILE` | 변환할 v1.6 설정 파일 |
| `--v16-conf-out=FILE` | 출력 위치(기본 `./yona-v16.properties`). 비밀번호가 들어 있어 소유자만 읽도록(`rw-------`) 만든다 |
| `--v16-conf-force` | 출력 파일이 이미 있어도 덮어쓴다(없으면 중단한다) |
| `--v16-conf-only` | 변환만 하고 끝낸다 |

변환의 규칙과 한계:

- 읽는 문법은 v1.6 설정이 실제로 쓰는 HOCON의 부분집합이다(한 줄 키, 중괄호로 묶은 키, `#`와 `//` 주석, `include`). `%prod.` 키는 기본 키를 덮어쓰고 다른 모드는 건너뛴다. `%prod.http.port`는 `server.port`가 된다.
- `db.default.url`의 `jdbc:mysql:`은 `jdbc:mariadb:`로 바꾼다(v2는 MariaDB 드라이버를 쓴다).
- 기간 값(`60s`, `6h`)은 그대로 둔다(v2도 같은 형식을 받는다).
- 변환 뒤 보고가 나온다: **대응하는 v2 키를 모르는 키**(직접 확인), **v2에서 없어진 기능의 키**(소셜 로그인 등, 옮기지 않음), 비밀이 든 키.
  Play, Akka, Ebean의 내부 설정은 뜻이 없어 조용히 버린다.
- **`include`**: 변환기는 `include "파일"` 줄을 읽지 않고 파일 이름만 보고서의 "참고"에 알린다. v1.6 기본 설정 파일은 끝에 `include "social-login.conf"`를 두며, 이 파일은 소셜 로그인(GitHub, Google 등)의 클라이언트 ID와 비밀을 담는다. v2는 소셜 로그인 기능을 없앴으므로(D-127) 옮길 것이 없다. 이 밖의 파일을 `include`한 설치라면 그 파일의 키는 변환되지 않으니 다음 순서로 직접 처리한다.
  1. 보고서에 나온 포함 파일을 열어 키를 확인한다.
  2. 필요한 키를 `application.conf` 본문으로 옮겨 적은 임시 파일을 만든다(포함 파일 내용을 그대로 붙여 넣으면 된다).
  3. 그 임시 파일을 `--v16-conf`로 다시 준다. 같은 키가 본문과 포함 파일에 둘 다 있으면 Play처럼 나중에 적힌 값이 이기도록 뒤에 붙인다.
  포함 파일을 자동으로 따라가지 않는 까닭은 경로 해석(상대 경로, 클래스패스 자원)과 순환 포함을 신경 써야 하는데 v1.6 기본 설치에는 필요한 경우가 없기 때문이다(A-610).
- **LDAP**: v1.6은 설정 파일로만 정했고 v2는 사이트 설정(DB)이 설정 파일보다 우선한다(D-82). 변환기는 `ldap.*`를 `yona.ldap.*`로 옮기고, 이어지는 데이터 이관 5단계가 켜진 LDAP을 `ldap_settings` 행으로 옮긴다. 이관 뒤에는 사이트 관리 화면에서 고치고, 설정 파일의 LDAP 값은 지워도 된다.
- **비밀 키**: `application.secret`은 `yona.secret`이 된다. v2에서는 2단계 인증 비밀 값, 백업 코드, LDAP 조회 계정 비밀번호와 SSO 비밀 값의 암호화 키를 이 값에서 만든다(v1.6은 세션 서명에 썼다). 값이 기본 문자열이거나 비어 있으면 첫 기동 화면이 나온다. 운영 중에 바꾸면 이미 저장한 암호화 값을 읽지 못하므로 v1.6의 값을 그대로 옮기고 이후로는 바꾸지 않는다.
- 사용자가 직접 준 `--spring.config.additional-location`이 있으면 변환한 파일보다 우선한다.
- 스크립트에는 `--server.port`를 주지 않는다. 스크립트가 `--server.port=0`을 붙인다.

### 이름은 같아도 값의 의미가 달라지는 설정 (변환기가 처리한 것)

키 이름만 옮기면 동작이 바뀌는 설정을 v1.6 소스와 대조해 찾아 처리했다(A-610).

| v1.6 설정 | v1.6 동작 | v2에서 그대로 옮기면 | 변환기의 처리 |
|---|---|---|---|
| `smtp.mock = true` (기본 설정 파일에 들어 있다) | 모의 발송기라 메일을 **실제로 보내지 않는다** | v2에는 모의 발송기가 없어 접속 정보가 있으면 실제로 보낸다 | 메일 서버 접속 정보(`smtp.host`, `port`, `ssl`, `password`)를 옮기지 않고 알린다. v2는 `spring.mail.host`가 없으면 메일을 보내지 않고 경고만 남긴다. 보내려면 `smtp.mock=false`로 바꿔 다시 변환하거나 `spring.mail.*`를 직접 정한다 |
| `ldap.protocol` | **읽지 않는다**(v1.6은 최상위 `protocol`을 읽는다, `LdapService.java:27`) | v1.6에서 무시되던 `ldaps`가 갑자기 적용되어 로그인이 끊길 수 있다 | `ldap.protocol`은 옮기지 않고 알린다. 최상위 `protocol`은 `yona.protocol`로 옮긴다 |
| LDAP을 켰는데 `ldap.*` 키가 없음 | v1.6 기본값(`127.0.0.1`, 포트 389, 기준 DN 빈 값 등) | v2 기본값은 예시 서버(`ldap.forumsys.com`)라 엉뚱한 곳에 접속한다 | LDAP이 켜져 있으면 없는 키를 v1.6 기본값으로 채운다 |
| `application.noreferrer` 없음 | 꺼짐(`false`) | v2 기본값은 켬(`true`) | 키가 없으면 `false`로 고정하고 알린다 |
| `application.notification.bymail.recipientLimit` 없음 | 제한 없음(`0`) | v2 기본값은 `100` | 키가 없으면 `0`으로 고정하고 알린다 |
| `application.update.notification.interval` 없음 | 1시간 | v2 기본값은 6시간 | 키가 없으면 `1h`로 고정하고 알린다 |
| `application.context` | 라우터와 링크의 접두어(`/`나 빈 값) | v2는 앞뒤 슬래시를 떼고 링크 접두어와 서블릿 컨텍스트 경로로 쓴다 | 값은 그대로 옮긴다(앞뒤 슬래시 차이는 v2가 정리한다, `SiteUrls`) |
| 기간 설정(`60s`, `6h` 등) | `getMilliseconds`가 문자열과 숫자(밀리초)를 받는다 | v2의 `Duration` 바인딩이 같은 형식을 받는다 | 값 그대로 옮긴다 |
| `application.secret` | 세션 서명 | 2단계 인증, SSO, LDAP 조회 계정의 암호화 키 | 값 그대로 옮긴다(위 "비밀 키") |

위 표에 없는 키는 이름과 값의 형식이 같다는 것을 v1.6 소스의 읽는 방식과 v2 `YonaProperties`의 설명으로 확인했다. 확인 범위와 방법은 `V16ConfigConverterTest`가 지킨다: v2 설정 주석에 v1.6 키로 적힌 이름은 모두 변환기가 알아야 하고, 변환 결과의 모든 `yona.*` 키는 `YonaProperties`에 있어야 한다.
v2 쪽 사용 여부도 코드에서 확인했다: 변환기가 만드는 `yona.*` 키는 모두 소스에서 읽힌다(`yona.signup.*`는 `Signup.required()`로, `yona.ldap.*`는 `LdapSettingsService.effective()`로, `yona.imap.*`는 `MailboxService`로).

## 3. 스키마 변화 (서버를 처음 시작할 때 자동)

`ddl-auto: update`가 더하기만 한다. v1.6의 테이블과 컬럼은 지우거나 바꾸지 않는다(리허설에서 365개 컬럼 모두 이름과 NULL 허용 여부가 그대로였다).

**새 테이블 29개**

- 풀 리퀘스트: `pull_request_assignee`, `pull_request_issue_label`, `pull_request_review`, `pull_request_review_request`
- 조직과 SSO: `organization_oauth_policy`, `organization_oauth_approval`, `oidc_sso_settings`, `saml2_sso_settings`, `ldap_settings`
- 인증과 키: `api_token`, `api_token_scope`, `device_authorization`, `device_authorization_scope`, `oauth_client`, `oauth_authorization`, `oauth_consent`, `two_factor_credential`, `two_factor_backup_code`, `known_device`, `attempt_record`, `gpg_key`, `ssh_key`, `deploy_key`
- 그 밖: `lfs_lock`, `ruleset`, `audit_log`, `audit_chain_head`, `audit_stream_cursor`, `site_setting`

**기존 테이블에 더해지는 컬럼 10개** (기존 행은 v1.6과 같게 동작한다)

| 테이블 | 컬럼 | 기존 행의 값과 동작 |
|---|---|---|
| `project` | `is_dismissing_stale_approvals`, `allow_merge_commit` | 값이 `NULL`이고 코드가 켬으로 본다 |
| `project` | `allow_squash_merge`, `allow_rebase_merge` | 끔 |
| `project` | `default_merge_method` | `merge` (병합 커밋만 허용하는 v1.6 동작) |
| `project_menu_setting` | `wiki` | 끔 (프로젝트 위키 메뉴는 새로 켜야 보인다) |
| `pull_request` | `rebase_blocked` | `NULL` |
| `comment_thread` | `pending_date` | `NULL` |
| `review_comment` | `pending_date`, `edited_date` | `NULL` |

## 4. 데이터 이관

서버를 끈 상태에서 실행한다. 서버와 같은 설정(DB 접속, `yona.data-dir`, 비밀 키)이 필요하다. 2번의 `--v16-conf`를 쓰면 변환한 파일이 이 설정을 채운다.
이미 v2 설정이 있다면 `--spring.config.additional-location=file:/etc/yona/` 같은 서버 옵션을 그대로 준다. v2를 한 번도 시작하지 않은 v1.6 DB에서도 이 스크립트 하나로 스키마 추가(3번)와 데이터 이관이 이어서 일어난다.

- **`--server.port`를 옵션에 주지 않는다**(스크립트가 `--server.port=0`을 붙여 `Invalid value '0,19700'`으로 시작하지 못한다).
- 하나가 실패하면 전부 되돌린다. 몇 번을 돌려도 결과가 같다. 종료 코드 0이 성공이다.
- 이관하지 않고 서버를 시작하면 `v1.6 data has not been migrated: ...` 경고가 남지만 시작은 된다.

| 단계 | 내용 | 이관하지 않으면 |
|---|---|---|
| 0 | 쉼표 문자열로 저장된 토큰과 기기 인증 범위를 범위 행으로 이동(이전 v2 빌드의 DB에만 해당) | 해당 토큰의 범위를 읽지 못함 |
| 1 | 번호가 빈 풀 리퀘스트에 번호를 붙이고 `REJECTED`를 `CLOSED`로 바꾼다 | 번호 없는 요청이 남는다 |
| 2 | `n4user.token`을 해시해 `api_token` 행으로 옮긴다(옛 컬럼은 비운다) | 옛 사용자 토큰이 동작하지 않는다 |
| 3 | 프로젝트의 리뷰어 수 설정을 브랜치별 규칙 묶음(`ruleset`, 브랜치마다 하나)으로 옮기고 설정을 끈다 | 리뷰어 수 요건이 적용되지 않는다 |
| 4 | 승인 집합에만 있는 승인에 리뷰 행을 만들고, 새 규칙으로 병합 요건을 잃은 열린 요청에 안내한다 | 승인이 리뷰 화면에 보이지 않는다 |
| 5 | 설정 파일로 켜 둔 LDAP을 사이트 설정(`ldap_settings`)으로 옮긴다. LDAP이 꺼져 있으면 만들지 않고, 화면에서 이미 저장한 값은 덮어쓰지 않는다 | 설정 파일 값이 계속 쓰이지만 화면에서 고치기 전까지 DB에 행이 없다 |

순서를 바꾸면 안 된다(뒤 단계가 앞 단계의 결과를 쓴다).

## 5. 로그인 때 자동으로 일어나는 일

- v1.6 비밀번호 해시(SHA-256 1024회 + 솔트)는 그대로 검증된다. 로그인에 성공하면 argon2id로 바뀐다. 로그인하지 않은 사용자의 해시는 v1.6 형식으로 남는다.

## 6. 확인

- 서버를 시작할 때 `v1.6 data has not been migrated` 경고가 없어야 한다.
- 옛 비밀번호로 로그인되고 틀린 비밀번호는 거부되는지 본다.
- 옛 토큰으로 `Authorization: token <값>`이나 `Yona-Token: <값>`을 보내 보호된 API(예: `/-_-api/v1/user/ssh_keys`)가 200인지 본다.
- 풀 리퀘스트 목록에서 번호가 비었던 요청에 번호가 있고 `REJECTED`가 `CLOSED`로 보이는지 본다.

## 리허설 결과 (2026-11-16, MariaDB 10.11)

환경: v1.6 evolutions 1~32의 `Ups`로 만든 스키마(컬럼 365개가 `src/test/resources/schema/v16-columns.tsv`와 일치), 합성 데이터는 `tools/v16-migration-rehearsal/seed.sql`(사용자 3명, 프로젝트 1개, 풀 리퀘스트 4건, 옛 토큰 1개, 리뷰어 설정 1개, 리뷰어 2건).

| 확인 | 결과 |
|---|---|
| 처음 시작 | 정상 시작. 테이블 61개에서 90개로 늘고 컬럼 10개 추가, v1.6 컬럼은 변경 없음. 경고: 번호 없음·`REJECTED` 2건, 옛 토큰 1건, 리뷰어 설정 1건, 승인 누락 2건 |
| 이관 1회차(13초) | 토큰 1건, 리뷰어 설정 1건, 승인 리뷰 2건 이관. 번호 없던 요청에 번호 5, `REJECTED`가 `CLOSED`로, 규칙 1개. 이관 후 미적용 0 |
| 이관 2회차 | 모든 건수 0, DB 상태 동일(멱등) |
| 이관 뒤 시작 | 경고 없음 |
| 로그인 | 틀린 비밀번호 거부, v1.6 해시로 로그인 성공, 해시가 argon2id로 바뀜, 이후 로그인 성공, 미로그인 사용자의 해시는 그대로 |
| 옛 토큰 | `Authorization: token`과 `Yona-Token` 모두 200, 틀린 토큰과 헤더 없음은 401 |

### 설정 변환까지 포함한 재리허설 (같은 날)

v1.6 기본 설정 파일에서 DB 주소(`jdbc:mysql:`), 사용자, 비밀번호, 비밀 키만 바꾼 설정을 `--v16-conf`로 주어, **v2를 한 번도 시작하지 않은 v1.6 DB**에 스크립트 한 번을 돌렸다.
63개 키가 변환됐고(`jdbc:mysql`을 `jdbc:mariadb`로 바꿨다는 참고, 알 수 없는 키 `smtp.archive.size`, `smtp.mock`, 없어진 기능 키 3개 보고), 스키마가 90개 테이블로 만들어지고 데이터 이관 건수는 첫 리허설과 같았다.
이어 `--spring.config.additional-location=file:<변환 파일>`만으로 서버를 시작해 첫 기동 화면이 아니라 로그인 화면이 나오고(비밀 키 반영), 사이트 이름이 설정대로이며, v1.6 비밀번호 로그인(argon2id로 승급)과 옛 토큰이 동작했다.

리허설에서 만난 함정은 위 가이드의 "준비"와 "설정 옮기기"에 반영했다: Java 21, `yona.secret`, 옵션의 `--server.port`, 저장소 디렉터리.

### 이 리허설이 보증하지 않는 것

- 합성 데이터라서 수만 건의 실제 데이터에서의 소요 시간과 이상 값(예: 깨진 날짜, 고아 행)은 확인하지 못했다.
- 저장소 디렉터리가 없는 상태로 돌렸으므로 3번 단계의 브랜치별 규칙 생성은 기본 브랜치 한 개 경로만 확인했다. 저장소가 있을 때의 동작은 `ReviewerSettingMigration`의 시험이 다룬다.
- 로그인 화면은 `curl`로 확인했고 브라우저 화면 확인은 하지 않았다.
- Mercurial과 SVN 저장소, 첨부 파일 디렉터리의 이전은 다루지 않았다.
