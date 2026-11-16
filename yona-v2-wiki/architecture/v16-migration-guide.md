# v1.6 데이터베이스를 이어받아 v2로 올리는 이관 가이드

v1.6(Play 2.3, MariaDB)이 만든 데이터베이스를 그대로 두고 v2 서버를 올릴 때 일어나는 변화와, 반드시 해야 하는 이관을 정리한다.
같은 데이터베이스 종류(MariaDB/MySQL)를 계속 쓰는 경우를 다룬다. 데이터베이스 종류를 바꾸는 경우는 사이트 데이터 내보내기와 가져오기(`/sites/data`)를 쓴다.

2026-11-16에 실제 MariaDB 10.11에서 이 절차를 한 번 수행했다(아래 "리허설 결과"). 운영 데이터가 아니라 v1.6 evolutions 32개로 만든 v1.6 스키마에 합성 데이터를 넣은 것이다.

## 한눈에 보기

| 순서 | 할 일 | 자동 여부 |
|---|---|---|
| 1 | v1.6 서버를 끄고 DB를 백업한다 | 수동 |
| 2 | 설정을 옮긴다(`application.secret` -> `yona.secret`, DB 접속, 저장소 디렉터리) | 수동 |
| 3 | v2를 처음 시작한다: 스키마가 자동으로 더해진다 | 자동(`ddl-auto: update`) |
| 4 | v2를 끄고 이관 스크립트를 돌린다 | 수동(한 번, 다시 돌려도 안전) |
| 5 | v2를 시작해 확인한다 | 수동 |

## 1. 준비

- **Java 21**이 필요하다. 시스템 기본 `java`가 낮은 버전이면 `UnsupportedClassVersionError`(클래스 파일 65)로 시작하지 못한다.
- v1.6을 끄고 DB를 백업한다(`mariadb-dump`). 이관은 데이터를 바꾸므로 백업 없이 시작하지 않는다.
- v1.6 저장소 디렉터리(`repo/git`, `repo/hg` 등)를 v2의 `yona.data-dir`이 가리키게 한다. 이관 3번이 저장소의 브랜치를 읽는다.
  저장소를 읽지 못하면 경고만 남기고 기본 브랜치 이름 하나로 규칙을 만든다(실패하지는 않지만 브랜치별 규칙이 하나로 줄어든다).

## 2. 설정 옮기기

- `application.secret`(v1.6의 `conf/application.conf`)을 `yona.secret`으로 옮긴다. 비어 있거나 기본값이면 v2는 처음 기동한 것으로 보고 **모든 요청에 기본 관리자 계정을 정하는 화면**만 보인다.
- DB 접속은 `spring.datasource.url`, `username`, `password`로 지정한다(MariaDB는 `jdbc:mariadb://...`).
- 그 밖의 대응은 [설정 키 대응표](configuration-keys.md)를 따른다.

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

서버를 끈 상태에서 실행한다. 서버와 같은 설정(DB 접속, `yona.data-dir`, 비밀 키)을 줘야 한다.

```bash
scripts/migrate-v16.sh build/libs/yona-2.0.0-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:mariadb://HOST:3306/yona --spring.datasource.username=... --spring.datasource.password=... \
  --yona.data-dir=/var/yona --yona.secret=...
```

- 스크립트가 `--server.port=0`을 붙이므로 **`--server.port`를 옵션에 주지 않는다**(`Invalid value '0,19700'`으로 시작하지 못한다).
- 하나가 실패하면 전부 되돌린다. 몇 번을 돌려도 결과가 같다. 종료 코드 0이 성공이다.
- 이관하지 않고 서버를 시작하면 `v1.6 data has not been migrated: ...` 경고가 남지만 시작은 된다.

| 단계 | 내용 | 이관하지 않으면 |
|---|---|---|
| 0 | 쉼표 문자열로 저장된 토큰과 기기 인증 범위를 범위 행으로 이동(이전 v2 빌드의 DB에만 해당) | 해당 토큰의 범위를 읽지 못함 |
| 1 | 번호가 빈 풀 리퀘스트에 번호를 붙이고 `REJECTED`를 `CLOSED`로 바꾼다 | 번호 없는 요청이 남는다 |
| 2 | `n4user.token`을 해시해 `api_token` 행으로 옮긴다(옛 컬럼은 비운다) | 옛 사용자 토큰이 동작하지 않는다 |
| 3 | 프로젝트의 리뷰어 수 설정을 브랜치별 규칙 묶음(`ruleset`, 브랜치마다 하나)으로 옮기고 설정을 끈다 | 리뷰어 수 요건이 적용되지 않는다 |
| 4 | 승인 집합에만 있는 승인에 리뷰 행을 만들고, 새 규칙으로 병합 요건을 잃은 열린 요청에 안내한다 | 승인이 리뷰 화면에 보이지 않는다 |

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

리허설에서 만난 함정은 위 가이드의 "준비"와 "설정 옮기기"에 반영했다: Java 21, `yona.secret`, 옵션의 `--server.port`, 저장소 디렉터리.

### 이 리허설이 보증하지 않는 것

- 합성 데이터라서 수만 건의 실제 데이터에서의 소요 시간과 이상 값(예: 깨진 날짜, 고아 행)은 확인하지 못했다.
- 저장소 디렉터리가 없는 상태로 돌렸으므로 3번 단계의 브랜치별 규칙 생성은 기본 브랜치 한 개 경로만 확인했다. 저장소가 있을 때의 동작은 `ReviewerSettingMigration`의 시험이 다룬다.
- 로그인 화면은 `curl`로 확인했고 브라우저 화면 확인은 하지 않았다.
- Mercurial과 SVN 저장소, 첨부 파일 디렉터리의 이전은 다루지 않았다.
