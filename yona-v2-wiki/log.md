# Yona v2 Wiki 로그

## [2026-10-22] build | 프로젝트 골격 생성
- 브랜치 `next2`(v1.6 기점)에 Gradle(Kotlin DSL), Spring Boot 4.1.1, Kotlin 2.4.10, JDK 21 toolchain 골격을 만들었다.
- TDD: `YonaApplicationTests`가 `@SpringBootConfiguration` 부재로 실패(RED)한 뒤 `YonaApplication` 추가로 통과(GREEN)했다.
- `docker-compose.yml`에 PostgreSQL 16(25432)과 mailpit(SMTP 21025, UI 28025)을 구성했다.

## [2026-10-22] build | Spring Boot 4.1.1로 상향
- 선생님 지시로 Spring Boot를 3.5.16에서 4.1.1로 올렸다. `starter-web`은 `starter-webmvc`로 바뀌었고, 테스트용으로 `starter-webmvc-test`, `starter-data-jpa-test`를 추가했다.

## [2026-10-22] ingest | v1.6 스키마와 엔티티 61개 매핑
- 기준 스키마를 추출해 `src/test/resources/schema/`에 저장했다(테이블 61, 컬럼 365).
- TDD: 테이블, 컬럼, NULL 허용, 유니크, 외래 키 삭제 규칙 테스트 6건을 먼저 실패(RED)시킨 뒤 엔티티로 통과(GREEN)시켰다.
- 기록: [domain/schema-mapping.md](domain/schema-mapping.md)

## [2026-10-22] ingest | 초기 데이터 적재
- TDD: `InitialDataLoaderTest` 3건이 컴파일 단계에서 실패(RED)한 뒤 `InitialDataLoader`로 통과(GREEN)했다.
- 처음에는 `ApplicationRunner`가 같은 클래스의 `@Transactional` 메서드를 직접 호출해 `TransactionRequiredException`이 발생했고, 별도 빈으로 분리해 해결했다.
- 전체 테스트: 10건 통과. 기록: [subsystems/initial-data.md](subsystems/initial-data.md)

## [2026-10-22] build | AGENTS.md를 저장소 루트로 이동
- 내려받은 `AGENTS.md`를 저장소 루트에 두고 `CLAUDE.md`(`@AGENTS.md`)로 불러오게 해 전역 지침으로 취급한다.

## [2026-10-22] ingest | 스키마 검증 강화와 PostgreSQL, MariaDB 검증
- 선생님 지시로 비교 항목에 컬럼 타입과 문자열 길이, 인덱스(이름, 컬럼 순서, 유니크)를 추가하고 PostgreSQL 16, MariaDB 10.11에서도 같은 테스트를 실행한다.
- TDD: H2에서 인덱스 228건, 타입 24건, enum 값 제한 위반이 먼저 실패(RED)했다. 인덱스 선언은 기준 파일에서 생성해 엔티티에 반영했다.
- 발견 사항: Hibernate 7은 enum에 CHECK 제약을 만들고 변환기로도 막을 수 없어, 스키마 생성 직전에 제거하는 통합기(`EnumCheckConstraintRemover`)를 추가했다. PostgreSQL과 H2에서는 인덱스 이름이 스키마 전체에서 유일해야 해서 댓글 테이블의 인덱스 두 개 이름을 바꿨다.
- 결과: `./gradlew test`가 H2, PostgreSQL, MariaDB 각각에서 10건 통과.
- 기술 스택 결정(쿼리 계층, 스키마 관리, 검증 DB, 테스트 스택)을 [architecture/technology-decisions.md](architecture/technology-decisions.md)에 기록했다.

## [2026-10-22] query | enum 컬럼의 DB 제약 여부
- 질문: enum 값이 자유 입력인지에 따라 DB 제약 여부가 갈리는가. 근거는 `1.sql:19,43-45`, `20.sql`, `MilestoneApp.java:72`, `ProjectApp.java:834`, `UserApi.java:379`.
- 결론: 닫힌 집합이지만 v1.6이 의도적으로 제약을 제거했으므로 유지한다. 템플릿 엔진은 Thymeleaf로 확정되었다. 기록: [architecture/technology-decisions.md](architecture/technology-decisions.md)

## [2026-10-22] ingest | 로컬 로그인 판정과 패스워드 해시
- TDD: `LegacyPasswordHasherTest` 5건, `LocalAuthenticatorTest` 9건을 먼저 실패(RED)시킨 뒤 `LegacyPasswordHasher`, `UserRepository`, `LocalAuthenticator`로 통과(GREEN)시켰다.
- 해시는 실제 Shiro 1.13.0과 교차 검증한다. 초기 관리자 해시는 평문을 재현할 수 없음을 확인했다(원인은 [subsystems/authentication.md](subsystems/authentication.md)).
- 결과: `./gradlew test`가 H2, PostgreSQL, MariaDB 각각에서 25건 통과.
