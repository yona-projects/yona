---
title: 기술 스택 결정 기록
type: architecture
sources: [../AGENTS.md, ../build.gradle.kts]
updated: 2026-10-22
---

# 기술 스택 결정 기록

> 결정의 전체 이력(선택지, 권장안과 권장 이유, 결정자, 상태)은 [../decisions.md](../decisions.md)에 있다. 이 페이지는 현재 유효한 결정만 요약한다.

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
| 보안 기반 | Spring Security 필터 체인(모든 URL 허용, 인가는 애플리케이션 계층) | 선생님 (2026-10-22) | 자체 필터 + HttpSession: 세션 고정 방어와 보안 헤더를 직접 책임져야 한다. |
| 레이아웃 방식 | Thymeleaf 기본 프래그먼트(`th:replace`에 본문을 인자로 전달) | 선생님 (2026-10-22) | thymeleaf-layout-dialect: 서드파티 의존성이고 Thymeleaf 3.1/Boot 4 호환 확인이 필요하다. |
| 저장소(VCS) 범위 | Git(JGit) 먼저, Subversion(SVNKit)은 마지막 단계. `project.vcs` 필드는 처음부터 `GIT`/`Subversion`을 모두 담는다 | 선생님 (2026-10-22) | Git만 지원: v1.6과 기능이 달라지므로 명시 승인 필요. 둘을 동시에: 단계마다 작업량이 늘어난다. |
| 비동기 처리 | Spring 이벤트(`ApplicationEventPublisher`) + `@Async`(Java 21 가상 스레드) + `@Scheduled` | 선생님 (2026-10-22) | Kotlin 코루틴: JPA 블로킹 트랜잭션과 섞기 위험하다. Akka(Pekko) 유지: 대형 의존성이 추가되고 Spring 트랜잭션 경계를 따로 관리해야 한다. |
| Git 처리 | JGit 7.x(7.8.0)를 프로세스 안에서 사용 (D-15) | 선생님 (2026-10-22) | git CLI와 `git http-backend`: 서버에 git 설치가 필수이고 push 훅 연동 구조가 크게 달라진다. |
| 데이터 디렉터리 | `yona.data-dir` 설정, `-Dyona.data`도 인식 (D-16) | 선생님 (2026-10-22) | `-Dyona.data`만 인식: Spring 설정에서 바꿀 수 없다. |
| 마크다운 렌더링 | 운영: v1.6의 `marked.js`를 Nashorn 독립 배포판에서 실행(A-154가 D-17 선택을 되돌림). commonmark-java와 GraalJS 비교 테스트는 2026-10-29에 삭제(D-33) | 선생님 (2026-10-22) | GraalJS로 marked.js를 운영에서 실행: 결과는 동일하지만 의존성과 시작 시간이 크다. JetBrains markdown: 사례가 적다. flexmark: 갱신이 멈췄다. |
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
