# Cubrid 11.4와 MSSQL 지원 (X-46)

> **상태: MSSQL 완료(전체 시험 4,375건 통과). Cubrid는 전체 연속 실행 4,379건 중 7건만 실패하고(모두 스키마 메타데이터 조회), 그 클래스들은 단독으로 통과한다(2026-11-04). 이전의 `this.con is null` 실패는 해결했다.** 결정은 [decisions.md](../decisions.md) D-85, 로드맵은 [roadmap.md](../roadmap.md) X-46이다.

## 1. 범위와 환경

- Cubrid는 **11.4만** 지원한다. 시험 이미지는 `cubrid/cubrid:11.4`, 드라이버는 Maven Central의 최신인 `cubrid-jdbc` 11.3.2.0053이다(11.4용 별도 드라이버는 공개되어 있지 않다).
- MSSQL은 `mcr.microsoft.com/mssql/server:2022-latest`, 드라이버는 `mssql-jdbc` 12.10.0.jre11이다.
- 시험은 `-Dyona.it.db=mssql` 또는 `cubrid`로 고른다([TestDatabase](../../src/test/kotlin/dev/yona/support/TestDatabase.kt)). 컨테이너는 다른 데이터베이스와 같이 Testcontainers `reuse`로 둔다.
- 엔티티가 스키마의 유일한 원천이라는 D-03 결정은 그대로다. 아래 차이는 모두 DDL을 만드는 Hibernate 단계에서 흡수한다.

## 2. 단계 0에서 알게 된 데이터베이스별 차이와 처리

| 영역 | 데이터베이스 | 문제 | 처리 |
|---|---|---|---|
| 불리언 기본값 | MSSQL, Cubrid | `@ColumnDefault("false")`의 리터럴 `false`를 받지 않는다 | `BooleanDefaultIntegrator`가 방언의 불리언 표기(`0`, `1`)로 바꾼다. 다른 데이터베이스의 DDL은 달라지지 않는다 |
| 불리언 열 형식 | Cubrid | 기본 방언이 쓰는 `bit(1)`은 숫자 기본값과 `= 1` 비교를 받지 않는다 | `YonaCubridDialect`가 불리언을 `smallint`로 저장한다 |
| 예약어 `size` | Cubrid | `attachment.size`가 예약어라 `create table`이 실패한다 | `YonaCubridDialect`에 예약어로 등록하고 `hibernate.auto_quote_keyword`로 따옴표를 붙인다 |
| `on update cascade` | Cubrid | 갱신 규칙은 `no action`, `restrict`, `set null`만 받는다 | `CubridDdlAdjuster`가 지운다(4곳). 기본 키를 바꾸는 코드가 없어 동작은 같다 |
| 중복 인덱스 | Cubrid | 같은 열 구성의 인덱스를 둘 만들 수 없다 | v1.6이 유니크 키와 따로 둔 `recent_issue` 인덱스 2개를 `CubridDdlAdjuster`가 지운다 |
| 자기 참조 `on delete set null` | MSSQL | `issue.parent_id`, `issue_comment.parent_comment_id`, `posting.parent_id`, `posting_comment.parent_comment_id` 외래 키를 만들 수 없다(cascade 경로 오류) | **미해결.** 부모를 지울 때 자식의 참조를 앱이 먼저 비우도록 해야 한다(단계 1) |
| 한글 | MSSQL | `varchar` 열은 데이터베이스 정렬 순서에 따라 한글이 `?`로 저장된다 | `hibernate.use_nationalized_character_data=true`로 문자열 열을 `nvarchar`로 만든다. v1.6 MariaDB는 utf8mb4 문자 수 기준이라 UTF-16 단위로 세는 `nvarchar`가 길이 의미도 가깝다. 운영 설정에도 필요하다 |
| 읽기 잠금 | MSSQL | 기본 격리(`READ COMMITTED`)는 읽는 쪽이 쓰는 쪽을 기다려, 다른 트랜잭션이 쓰는 중인 행을 읽으면 멈춘다. 시험 하나가 20분 넘게 멈췄다 | 데이터베이스에 `READ_COMMITTED_SNAPSHOT ON`을 켠다(PostgreSQL, MariaDB와 같은 읽기 동작). 시험 데이터베이스는 시작할 때 이를 켜고 JDBC `lockTimeout=20000`을 두어 멈춤 대신 실패로 드러나게 했다. 운영에서도 필요하다 |
| 감사 로그 끝 행 생성 | MSSQL, MariaDB | 끝 행이 없는 테이블을 잠가 읽으면 그 범위를 잠가, 별도 트랜잭션의 끝 행 생성이 막혀 서로 기다린다(첫 감사 기록이 멈춤) | `AuditRecorder.lockedHead`가 잠가 읽기 전에 별도 트랜잭션에서 행을 보장한다. 기존 데이터베이스의 동작은 같다 |
| 시험 도구 | 둘 다 | MSSQL `master`의 시스템 테이블, `((0))` 형태 기본값, Cubrid 드라이버가 `getSchema()`와 열 이름 `%` 조회를 지원하지 않음 | 시험 보조 코드(`SchemaMetadata`)에서 걸러 내거나 예외를 처리한다 |

## 3. 단계 0 스키마 대조 결과 (`V16SchemaTest` 9건)

- MSSQL: 6건 통과. 실패 3건은 자기 참조 외래 키 4개(위 표), 시스템 테이블(시험 보조로 해결함), 기본값 표기(해결함)이다.
- Cubrid: 5건 통과. 남은 4건은 의도한 차이(`recent_issue` 인덱스, `on update cascade`)와 시험 보조(드라이버가 내림차순 정보를 주지 않음, 열 조회)이다.

## 4. 전체 시험 결과

MSSQL 전체 시험을 두 번 돌렸다. 첫 번째는 감사 로그 잠금으로 멈췄고, 두 번째(한글과 잠금을 고친 뒤)는 2,360건을 실행한 시점에 사이트 데이터 가져오기 시험이 잠금으로 멈춰 중단했다. 그 시점까지 실패 65건이다.

| 원인 | 건수 | 비고 |
|---|---|---|
| 사이트 데이터 가져오기(`DataService`)가 MSSQL을 모른다 | 24건과 이어진 연쇄 실패 약 40건 | `DataExchangeTest`가 `IDENTITY_INSERT is set to OFF`로 실패하고, 실패한 가져오기가 테이블을 비운 채 두어 뒤따르는 시험(`CodeBrowserServiceTest`, `DiffServiceTest`, `PostReceiveJobsTest` 등)이 연쇄로 실패한다. 이 코드는 H2, PostgreSQL, MySQL/MariaDB만 분기한다 |
| 자기 참조 외래 키 `on delete set null` | 1건(스키마 시험) | 위 표의 미해결 항목 |

Cubrid는 스키마 생성까지만 확인했고 전체 시험은 아직 돌리지 않았다.

### 단계 1에서 할 일

1. **사이트 데이터 내보내기·가져오기(`DataService`)**: 두 데이터베이스에 맞춰 외래 키 일시 해제, 테이블 비우기(MSSQL은 외래 키가 걸린 테이블에 `truncate`를 못 쓴다), 식별 열 값 직접 넣기(`SET IDENTITY_INSERT`), 다음 번호 읽기와 복원, 식별자 따옴표를 추가한다.
2. **MSSQL 자기 참조 외래 키**: 부모를 지울 때 자식의 참조를 앱이 먼저 비우도록 한다(이슈, 이슈 댓글, 게시글, 게시글 댓글).
3. **Cubrid 전체 시험**을 돌려 남은 차이를 모은다. 스키마 시험에서 내림차순 인덱스 정보와 `getColumns` 조회가 드라이버에서 지원되지 않아 시험 보조 코드를 보완해야 한다.
4. **운영 설정 자동화**: 접속 주소가 `jdbc:sqlserver:`나 `jdbc:cubrid:`이면 드라이버, 방언(`YonaCubridDialect`), `use_nationalized_character_data`, `auto_quote_keyword`를 자동으로 정하고, 문서(`configuration-keys.md`)에 MSSQL `READ_COMMITTED_SNAPSHOT`과 Cubrid 설정을 적는다.

### 알아 둘 점

- MariaDB에서 감사 로그 시험(`AuditBoundaryTest` 등)이 실패한다. 이번 변경 전의 기준 상태에서도 시험이 멈춰(250초 초과) 이 변경이 원인이라고 확정하지 못했다. H2와 PostgreSQL은 통과한다. MariaDB는 이번 범위가 아니지만 별도로 확인이 필요하다.
- 한 번에 여러 시험 클래스를 MSSQL에서 돌리면 컨텍스트가 닫힐 때 `create-drop`이 다른 컨텍스트의 테이블을 지워 `Invalid object name`으로 실패하는 경우가 있다(클래스를 따로 돌리면 통과). 공유 데이터베이스를 쓰는 시험 구조의 한계이다.


## 5. 단계 1 결과 (2026-11-03)

### MSSQL: 전체 시험 4,375건 통과 (약 16분)

| 영역 | 처리 |
|---|---|
| 사이트 데이터 가져오기 | `SET IDENTITY_INSERT` 켜기와 끄기, 테이블마다 `nocheck constraint all`로 외래 키 일시 해제, 가져온 뒤 `DBCC CHECKIDENT RESEED`로 다음 번호 복원, `IS_AUTOINCREMENT`로 식별 열 판단 |
| 자기 참조 `on delete set null` | `SqlServerSelfReferenceTrigger`가 네 테이블에 `after delete` 트리거를 만든다(직접 삭제, cascade, 가져오기 모두 같은 결과) |
| 문자열 | `use_nationalized_character_data`로 `nvarchar`. HQL의 `cast(x as string)`이 `varchar(max)`로 바뀌어 한글이 깨지므로 `YonaSqlServerDialect`가 `nvarchar(max)`로 바꾼다. `SchemaColumnWidener`도 `nvarchar`를 유지한다 |
| 운영 설정 자동화 | `DatabaseDefaults`(환경 후처리기)가 접속 주소 `jdbc:sqlserver:`, `jdbc:cubrid:`를 보고 방언과 문자열 설정을 기본값으로 정한다 |
| 시험 구조 | 컨텍스트마다 별도 데이터베이스(`model`에 `READ_COMMITTED_SNAPSHOT`을 켜 둠)를 만들고 실행이 끝나면 한 훅에서 `OFFLINE` 후 지운다. `lockTimeout=20000` |
| 건너뛰는 시험 | `@Transactional` 시험이 커밋하지 않은 행을 별도 연결의 사이트 데이터 올리기가 기다리는 시험 8건(`SiteDataTest` 6, `AuditSiteDataTest` 1, `ExternalSiteBackupApiTest` 1)은 MSSQL에서 건너뛴다. 같은 코드는 커밋하는 `DataExchangeTest`가 확인한다 |

### Cubrid 11.4: 알려진 차이와 처리

| 영역 | 처리 |
|---|---|
| 불리언 | `smallint`로 저장(`YonaCubridDialect`). 숫자 열에는 `setBoolean`/`setInt`를 못 넣어(`Cannot coerce host var to type short`) 가져오기는 `smallint` 열에 `setShort`를 쓴다 |
| 긴 문자열 | `clob`은 `where`에서 비교할 수 없어 `string`(`varchar(1073741823)`)으로 저장 |
| 예약어 | `size`, `role`, `action` 등. 엔티티 SQL은 `auto_quote_keyword`, 직접 쓴 SQL은 대괄호 |
| `on update cascade` | 4곳에서 뺌 |
| `recent_issue` 유니크 키 | Cubrid는 `{2, NULL}`을 같은 값으로 봐서 `NULL`이 한쪽에 있는 짝의 유니크 키 둘을 뺌. 기록 전에 같은 대상을 지우므로 중복되지 않음 |
| 외래 키 일시 해제 | 방법이 없어 가져오기 전에 모두 떼고 끝나면 다시 붙임. 부모가 없는 행(고아 행)이 있으면 참조를 비우거나 행을 지우고(경고 로그) 붙임. v1.6은 고아 행을 그대로 두므로 이를 확인하는 시험 5건은 Cubrid에서 건너뜀 |
| 다음 번호 복원 | `alter table ... auto_increment = n` |
| 건강 검사 | 드라이버의 `isValid(0)`이 거짓이라 연결 검사 질의 `select 1 from db_root`를 기본값으로 둠 |
| 데이터베이스 문자 집합 | 이미지 기본값(`en_US`)은 ISO-8859-1이라 한글이 깨진다. 데이터베이스를 `en_US.utf8`로 만들고 접속 주소에 `?charset=utf-8`을 붙여야 한다 |
| 브로커 한도 | 기본 `MAX_NUM_APPL_SERVER=40`으로는 시험이 연결을 거부당한다(300으로 올림). 운영도 사용자 수에 맞춰야 한다 |
| 시험 구조 | 컨텍스트마다 별도 사용자(스키마)를 만든다. 사용자와 테이블은 다음 실행 시작 때 정리한다 |
| 스키마 대조 시험 | 드라이버가 `COLUMN_DEF`, `IS_AUTOINCREMENT`, 인덱스 방향을 주지 않아 `db_attribute`, `db_serial` 카탈로그로 읽고 방향 비교는 건너뜀 |

### Cubrid 전체 연속 실행 (2026-11-04)

- **해결한 것**: 전체를 연속으로 돌리면 실행마다 다른 클래스 41~99건이 컨텍스트 로딩 단계에서 `NullPointerException: ... "this.con" is null`(`CUBRIDResultSet.findColumn`, 초기 데이터 적재의 `getGeneratedKeys` 읽기)로 실패하던 문제이다. `next` 브랜치의 Cubrid 설정이 같은 증상을 "대규모 시험에서 실측"했다고 적고 있어 그 대응을 따랐다: `hibernate.jdbc.use_get_generated_keys=false`(Hibernate가 `db_serial`의 현재 값을 읽는다)와 Hikari `max-lifetime=300000`(브로커가 먼저 끊은 오래된 연결을 풀이 내주지 않게 한다). `DatabaseDefaults`가 Cubrid 주소일 때 정하고, 시험은 속성을 직접 등록하므로 `TestDatabase`에도 같은 값을 넣었다. 적용 뒤 전체 실행(4,379건, 30분 46초)에서 이 오류는 0건이다.
- **같은 때 더한 것**: 방언에 `TimeZoneSupport.NONE`(드라이버의 `setObject`가 `datetimetz`와 `OffsetDateTime` 바인딩을 거부한다)과, 제약 위반을 오류 코드로 분류하는 변환기(NOT NULL은 -631, 유니크는 -670)를 더했다. 드라이버가 SQLState를 주지 않아 기본 방언으로는 `GenericJDBCException`이 되어 다른 데이터베이스와 달리 `DataIntegrityViolationException`이 되지 않았다.
- **남은 것**: 7건은 모두 `CUBRIDException: Has been interrupted`이고 `getSchemaInfo`(메타데이터 조회)에서 서버가 중단시킨 응답이다. `DataTablesTest` 1건, `AddedTablesSchemaTest` 4건, `V16SchemaTest` 2건이며 세 클래스는 단독으로 통과한다(53초). 실행이 길어질수록 컨텍스트마다 만든 사용자와 테이블이 쌓여 메타데이터 조회가 느려지는(한 시험이 62초) 부하 문제로 보이나 확정하지 못했다. `DataTablesTest`는 스키마를 지정하지 않고 모든 사용자의 테이블을 훑던 것을 현재 스키마로 좁혔다.
- 따라서 **Cubrid는 "지원하며 전체 시험이 거의 통과한다(7건은 부하 때 메타데이터 조회 중단)"**로 본다. 컨텍스트가 닫힐 때 그 사용자를 지우면 줄어들 것으로 보이나, 종료 훅 경합을 겪은 영역이어서 별도 작업으로 둔다.

### 시험 환경 교훈

- **디스크**: 호스트 디스크가 99~100%까지 찼다. 원인은 시험용 Cubrid 컨테이너를 `docker rm -f`로 지워 익명 볼륨 26GB가 고아로 남은 것과, Cubrid가 16KB 페이지에서 테이블과 인덱스마다 1MB씩 잡아 컨텍스트 하나가 수백 MB를 쓴 것이다. 컨테이너를 `docker rm -fv`로 지우고, 데이터베이스를 4KB 페이지, 64MB 볼륨으로 만들고, Cubrid 시험의 컨텍스트 캐시를 8개로 줄였다(`build.gradle.kts`).
- **ObservabilityActuatorTest**: 데이터 디렉터리 여유 5% 검사 때문에 디스크가 가득 차면 어느 데이터베이스에서든 실패한다.
- **PostgreSQL 시험 스키마(2026-11-04)**: 재사용하는 공용 컨테이너에서 모든 컨텍스트가 `public` 스키마를 나눠 쓰면, 캐시에서 밀려나 닫히는 컨텍스트의 `create-drop`이 다른 컨텍스트의 테이블까지 지워 `relation "project" does not exist`로 33건이 실패했다. 컨텍스트마다 스키마를 만들고(`currentSchema`) 시작할 때 남은 `it_` 스키마를 지운다(`TestDatabase`). 이 과정에서 `SchemaColumnWidener`가 스키마를 지정하지 않고 열 크기를 읽는 운영 코드의 결함도 찾아 현재 스키마로 한정했다. 결과: PostgreSQL 전체 4,380건 중 실패 1건(`QrCodeTest`, 데이터베이스와 무관한 불안정한 시험이라 여백을 두어 고침).
- **MariaDB 시험 데이터베이스(2026-11-04)**: PostgreSQL과 같은 구조라 컨텍스트마다 데이터베이스를 만들고(시작할 때 남은 `it_` 데이터베이스를 지움) 연결을 READ COMMITTED로 맞췄다. 공용 데이터베이스를 나누던 때 감사 기록 시험이 멈추던 증상이 사라졌다. 시험이 `@Transactional`로 바깥 트랜잭션을 열어 둔 채 별도 트랜잭션이 커밋한 행을 읽는데, MariaDB 기본 REPEATABLE READ는 그 행을 보지 못해 11건이 `List is empty`로 실패했기 때문이다(운영 설정은 바꾸지 않았다). 결과: MariaDB 전체 4,380건 중 실패 1건(시험의 MariaDB용 열 좁히기 SQL 누락, 고침), 건너뜀 3건. H2 전체 4,380건 실패 0.
- **MSSQL 시험 데이터베이스**: 컨텍스트마다 만들면 100개가 넘어 약 4GB를 쓰므로 실행이 끝날 때 한 훅에서 지운다.
