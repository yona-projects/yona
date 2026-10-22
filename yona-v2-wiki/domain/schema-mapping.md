---
title: v1.6 스키마와 JPA 엔티티 매핑
type: domain
sources: [conf/evolutions/default/1.sql, conf/evolutions/default/32.sql, ../yona-v1.6-wiki/domain/db-schema-evolutions.md]
updated: 2026-10-22
---

# v1.6 스키마와 JPA 엔티티 매핑

## 기준 스키마

v1.6의 `conf/evolutions/default/1~32.sql`에서 `!Ups` 구간만 이어 붙여 MariaDB 10.11에 적용하고, `information_schema`에서 추출한 결과를 기준 파일로 보관한다.

| 파일 | 내용 |
|---|---|
| `src/test/resources/schema/v16-columns.tsv` | 테이블 61개, 컬럼 365개(타입, NULL 허용, 기본값, 키) |
| `src/test/resources/schema/v16-indexes.tsv` | 인덱스와 유니크 제약 |
| `src/test/resources/schema/v16-foreign-keys.tsv` | 외래 키와 삭제·갱신 규칙 |

## 검증 테스트

`dev.yona.schema.V16SchemaTest`가 엔티티로 생성한 스키마를 기준 파일과 비교한다. `-Dyona.it.db=h2|postgres|mariadb`로 DB를 바꿔 같은 검증을 반복하며, 세 DB 모두 통과한다.

| 비교 항목 | 비고 |
|---|---|
| 테이블 집합 | 누락도 추가도 없어야 한다 |
| 컬럼 이름, NULL 허용 여부 | |
| 컬럼 타입 종류와 `VARCHAR` 길이 | `bigint`, `int`, `tinyint`, 불린, 큰 문자열(`longtext`), 시각, 날짜로 종류를 맞춘다. `VARCHAR`는 길이까지 정확히 비교한다. MariaDB의 `tinyint(1)`은 불린과 1바이트 정수를 구별하지 않으므로 둘을 같은 종류로 본다. |
| 인덱스 | 이름, 컬럼 순서, 유니크 여부 (PK 제외) |
| 외래 키 | 참조 대상 테이블과 삭제 규칙(`CASCADE`, `SET NULL`, `RESTRICT`) |
| enum 값 제한 없음 | `property.name`, `issue.state`, `webhook.webhook_type`에 정의되지 않은 값을 저장할 수 있어야 한다 |

비교하지 않는 항목: 컬럼 기본값(엔티티 생성자가 채운다), 외래 키 제약의 이름, 갱신 규칙(`ON UPDATE`), 문자 집합과 정렬 규칙.

테스트 도구 `SchemaMetadata`는 JDBC 메타데이터만 쓰므로 DB를 추가해도 그대로 재사용한다. H2가 외래 키마다 자동으로 만드는 인덱스(`fk...`)와 유니크 인덱스 이름 접미사(`_index_N`)는 H2 고유 동작이라 비교에서 제외한다.

## 패키지 구성 (`dev.yona.domain`)

| 패키지 | 엔티티 |
|---|---|
| `user` | User(`n4user`), Email, UserCredential, LinkedAccount, UserSetting, UserVerification, SiteAdmin, Role |
| `project` | Project, Organization, OrganizationUser, ProjectUser, ProjectMenuSetting, ProjectTransfer, PushedBranch, RecentProject, TitleHead, Webhook, WebhookThread, RecentlyVisitedProjects, ProjectVisitation |
| `issue` | Issue, Assignee, Milestone, IssueEvent, IssueSharer, Label, IssueLabelCategory, IssueLabel |
| `board` | Posting, PostingComment |
| `comment` | IssueComment, CommentThread(단일 테이블 상속 3종), ReviewComment, CommitComment |
| `pullrequest` | PullRequest, PullRequestCommit, PullRequestEvent |
| `notification` | NotificationEvent, NotificationMail, UserProjectNotification, Mention, OriginalEmail |
| `misc` | Attachment, Watch, Unwatch, FavoriteIssue/Organization/Project, RecentIssue, Property |

## 매핑 규칙

- enum은 `@Enumerated`가 아니라 `AttributeConverter`(`EnumConverters.kt`)로 저장한다. 문자열 enum은 이름으로, `State`와 `WebhookType`은 서수로 저장한다. 자세한 이유는 아래 "v1.6과 달라진 점"을 본다.
- 불린 컬럼은 v1.6이 NULL을 허용하므로 `Boolean?`(기본값 `false`)으로 둔다. 원시 타입은 Hibernate가 NOT NULL로 만들기 때문이다.
- `longtext`는 `@Column(length = LONG_TEXT)`(Hibernate `Length.LONG32`)로 표현해 DB별로 가장 큰 문자열 타입이 선택되게 한다.
- `Issue.state`, `Milestone.state`, `PullRequest.state`, `Webhook.webhookType`은 서수로 저장한다. enum 선언 순서를 바꾸면 안 된다.
- 삭제 규칙은 `@OnDelete`로 기준 스키마와 같게 맞춘다.
- `spring.jpa.hibernate.ddl-auto=update`로 기동 시 엔티티에서 스키마를 만든다.
- `hibernate.auto_quote_keyword=true`로 `property.value` 같은 예약어 컬럼을 보호한다.

## v1.6과 달라진 점과 이유

| 항목 | v1.6 | v2 | 이유 |
|---|---|---|---|
| 스키마 생성 | Play evolutions(MariaDB 방언 SQL) | 엔티티 기반 스키마 생성 + 기준 파일 대조 테스트 | 다중 DB(H2, PostgreSQL 등)에서 같은 구조를 얻기 위해 |
| `recently_visited_projects`, `project_visitation` | 엔티티 없음(고아 테이블) | 엔티티 매핑만 둠 | 테이블 구조를 v1.6과 같게 유지하라는 요구사항 |
| `project_menu_setting.project_id` | 일대일이지만 유니크 제약 없음 | `@ManyToOne` | v1.6 DDL에 유니크 제약이 없다 |
| H2 모드 | 해당 없음 | 기본 모드 | PostgreSQL 모드가 `TINYINT`를 지원하지 않는다 |
| enum 컬럼 | `varchar`에 이름 저장, CHECK 제약은 20.sql이 제거 | 변환기로 `varchar`에 저장하고, 스키마 생성 직전 `EnumCheckConstraintRemover`로 CHECK 제약을 모두 지운다 | Hibernate 6.2 이후는 enum 속성에 DB 고유 `ENUM` 타입이나 `check (col in (...))`을 만든다. 변환기와 `@JdbcTypeCode`로도 막을 수 없었다. 새 enum 값을 DB 변경 없이 추가하려면 제거가 필요하다. 이 프로젝트의 CHECK 제약은 모두 enum에서 나온다. |
| 인덱스 이름 | `ix_issue_parent_id`를 `issue`와 `issue_comment`에, `ix_posting_parent_id`를 `posting`과 `posting_comment`에 같은 이름으로 생성 | 댓글 테이블 쪽을 `ix_issue_comment_parent_id`, `ix_posting_comment_parent_id`로 변경 | MariaDB는 인덱스 이름이 테이블 안에서만 유일하면 되지만 PostgreSQL과 H2는 스키마 전체에서 유일해야 한다. 컬럼 구성은 같다. |
| 시각 컬럼 정밀도 | `datetime` | `timestamp(6)` 등 DB 기본 | JPA가 DB별로 고른다. 타입 종류는 같다. |
| `issue.weight` | `tinyint(2)` | `@JdbcTypeCode(TINYINT)`로 1바이트 정수 | PostgreSQL에는 `tinyint`가 없어 `smallint`가 된다. |
