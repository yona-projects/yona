---
title: 기술 스택 결정 기록
type: architecture
sources: [../AGENTS.md, ../build.gradle.kts]
updated: 2026-10-22
---

# 기술 스택 결정 기록

선생님께서 직접 정하신 항목은 "결정" 열에 날짜와 함께 적었다. 결정하지 않은 채 구현이 앞서가지 않도록, 새 라이브러리를 들이기 전에 이 표를 먼저 확인한다.

| 영역 | 결정 | 정한 사람 | 대안과 기각 이유 |
|---|---|---|---|
| 언어, 빌드 | Kotlin 2.4, Gradle Kotlin DSL, JDK 21 toolchain | AGENTS.md | |
| 프레임워크 | Spring Boot 4.1.1 (Spring Framework 7, Hibernate 7) | 선생님 (2026-10-22) | 처음에는 3.5를 가정했으나 4로 상향 |
| 영속성 | Spring Data JPA | AGENTS.md | |
| 쿼리 계층 | JPA Criteria(Specification)와 JPQL | 선생님 (2026-10-22) | QueryDSL: Kotlin 코드 생성(KSP)이 필요하고 Hibernate 7 지원이 불안정하다. jOOQ: JPA와 이중 모델이 된다. |
| 스키마 관리 | Hibernate가 엔티티에서 생성(`ddl-auto`) + v1.6 기준 스키마 대조 테스트 | 선생님 (2026-10-22) | Liquibase, Flyway: DB별 스크립트를 유지해야 하고 Cubrid는 공식 지원이 없다. |
| 검증 대상 DB | H2, PostgreSQL 16, MariaDB 10.11 | 선생님 (2026-10-22) | MySQL, Cubrid, MSSQL은 뒤로 미룬다. |
| 테스트 | JUnit 5, MockK, Testcontainers 2.0.5 | 선생님 (2026-10-22) | Kotest: Boot 4 호환 확인이 추가로 필요하다. |
| LESS 빌드 | Gradle 빌드에서 `lessc`(Less 1.7.5) 실행, 생성물은 저장소에 두지 않음 | 선생님 (2026-10-22) | 결과물을 저장소에 포함: LESS를 고칠 때마다 다시 컴파일해야 하고 소스와 어긋날 수 있다. less4j(JVM 전용): Less 1.x 문법 일부만 지원해 v1.6 결과와 달라질 위험이 있다. |
| 뷰 템플릿 | Thymeleaf | 선생님 (2026-10-22) | v1.6 `*.scala.html` 화면을 그대로 재현한다. |

## 테스트 DB 운영 방식

- 통합 테스트는 `-Dyona.it.db=h2|postgres|mariadb`로 DB를 고른다(기본값 H2).
- PostgreSQL과 MariaDB는 Testcontainers의 `reuse` 모드로 띄워, 테스트가 끝나도 컨테이너를 내리지 않는다. 다음 실행은 이미 떠 있는 컨테이너를 재사용하므로 기동 시간이 들지 않는다. 이 방식은 `~/.testcontainers.properties`의 `testcontainers.reuse.enable=true`에 의존한다.
- 스키마는 테스트 컨텍스트가 시작될 때 만들고(`create-drop`) 끝날 때 지우므로 컨테이너를 오래 두어도 데이터가 쌓이지 않는다.
- `docker-compose.yml`은 개발 중 애플리케이션을 직접 실행할 때 쓰는 별도 컨테이너(PostgreSQL 25432, mailpit)이며 테스트용 컨테이너와 분리되어 있다.
- 공유 컨테이너를 멈추지 않는다. 다른 작업이 같은 컨테이너를 쓸 수 있기 때문이다.

## enum 컬럼에 DB 제약을 두지 않는 이유

- v1.6의 enum 값은 요청 파라미터를 `State.getValue`, `ResourceType.getValue`, `UserState.of` 등으로 변환한 닫힌 집합이며 자유 입력값이 아니다.
- v1.6은 `1.sql`에서 이 집합을 `CHECK` 제약으로 만들었다가 `20.sql`에서 21개를 모두 제거했다. enum 값이 늘 때마다 DB를 고치지 않기 위해서다.
- 최종 스키마에는 `CHECK`도 DB 고유 `ENUM` 타입도 없으므로 v2도 같게 한다. 값의 유효성은 애플리케이션의 enum 변환기가 보장한다.
- DB 고유 `ENUM`은 PostgreSQL(`CREATE TYPE`), MSSQL, Cubrid에서 방식이 달라 이식성이 낮고, `ddl-auto=update`는 기존 `CHECK`를 갱신하지 않는다.
- DB에서 값을 강제하는 쪽으로 바꾸려면 v1.6과 달라지는 결정이므로 선생님의 승인이 필요하다.
