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

## [2026-10-22] ingest | 정적 자원, LESS 빌드, 다국어 메시지
- TDD: `StaticAssetsTest` 6건과 `MessagesTest` 6건 중 11건이 먼저 실패(RED)했고, `public/` 복사, 메시지 파일 복사와 설정, `WebConfig`로 통과했다. CSS 2건은 v1.6이 LESS를 빌드 시점에 컴파일한다는 사실을 발견해 Gradle `compileLess`를 추가하며 통과(GREEN)했다.
- 결정: LESS 빌드 방식을 선생님께 여쭈어 "Gradle 빌드에서 lessc 실행"으로 확정했다.
- 결과: `./gradlew test`가 H2, PostgreSQL, MariaDB 각각에서 37건 통과.
- 기록: [web/static-assets-and-i18n.md](web/static-assets-and-i18n.md)

## [2026-10-22] ingest | 로그인, 로그아웃, 현재 사용자 식별
- TDD: `HttpPreferenceTest` 7건과 `LoginControllerTest` 21건을 먼저 실패(RED, 컴파일)시킨 뒤 구현해 통과(GREEN)시켰다. 판정 결과에 `LockedAfterAuthentication`을 추가하면서 `LocalAuthenticatorTest`를 함께 고쳤다.
- v1.6 코드를 읽다 확인한 결함 5건을 [refactoring-backlog.md](refactoring-backlog.md)에 올렸다(R-02~R-06). R-03은 재현하지 않고 홈으로 보내므로 선생님 확인이 필요하다.
- 결정: 보안 기반(Spring Security)과 레이아웃 방식(Thymeleaf 기본 프래그먼트)을 선생님께 여쭈어 확정했다.
- 결과: `./gradlew test`가 H2, PostgreSQL, MariaDB 각각에서 65건 통과.
- 기록: [subsystems/authentication.md](subsystems/authentication.md)

## [2026-10-22] query | 초기 관리자 패스워드의 평문
- 질문: 솔트의 바이트 배열을 v1.6에서 찾을 수 있는가. 확인한 근거: 저장소 이력 `f4166b38c`(`conf/initial-data.yml`, `UserApp.java`).
- 결론: 평문은 `admin00`이고 문자열 솔트로 재현된다. 앞선 "재현 불가" 판단은 틀렸으며 [subsystems/authentication.md](subsystems/authentication.md)를 정정했다. R-03은 승인되었다.

## [2026-10-22] ingest | 레이아웃, 로그인 화면, 브라우저용 메시지
- TDD: `JsMessagesTest` 7건과 `LoginFormPageTest` 17건(총 24건 이상)을 먼저 실패(RED)시킨 뒤 구현했다. 구현 중 마주친 문제를 모두 [web/layout-and-login-page.md](web/layout-and-login-page.md)에 규칙으로 남겼다(`arraySplit`의 문자 집합 구분자, 중복 `class` 속성, JS 인라인의 `/` 이스케이프 등).
- 앱을 실제로 기동해 정적 자원 25개의 200 응답과 로그인/실패 흐름을 확인했다. `admin / admin00`이 동작한다.
- 결과: `./gradlew test`가 H2, PostgreSQL, MariaDB 각각에서 91건 통과.

## [2026-10-22] lint | 화면 캡처 도구 규칙
- 선생님 지시로 화면 캡처와 브라우저 화면 검증은 Playwright로만 하도록 `AGENTS.md`와 `yona-v2-wiki/CLAUDE.md`에 명시했다.

## [2026-10-22] ingest | 익명 접근 검사
- TDD: `AnonymousCheckTest` 6건과 `AnonymousCheckClosedSiteTest` 2건을 먼저 실패(RED, 컴파일)시킨 뒤 `@AnonymousCheck`와 인터셉터로 통과(GREEN)시켰다.
- 기록: [subsystems/access-control.md](subsystems/access-control.md)

## [2026-10-22] ingest | 홈 화면 (1단계)
- 선생님이 "의존 방향 순으로 진행, 결정이 필요하면 물어볼 것"이라고 하셨다. 순서: 1) 홈 소개와 틀, 2) AccessControl과 프로젝트·조직, 3) 이슈·게시판·마일스톤, 4) 알림과 로그인 사용자용 홈, 5) 코드 저장소와 풀 리퀘스트.
- TDD: `HomePageTest` 14건과 `NotificationEventQueryTest` 3건을 먼저 실패(RED, 컴파일)시킨 뒤 구현했다. 마지막 두 번의 실패는 테스트에 적은 영어 문구 추측(`Features`, `Unlimited Projects`)이 틀린 것이어서 메시지 파일의 실제 값으로 바로잡았다.
- 결과: `./gradlew test`가 H2, PostgreSQL, MariaDB 각각에서 116건 통과.
- 기록: [web/home-page.md](web/home-page.md)

## [2026-10-22] ingest | AccessControl (2단계 a)
- 선생님이 VCS 범위(Git 먼저, SVN은 마지막)와 비동기 처리(Spring 이벤트 + @Async/@Scheduled)를 정해 주셨다([architecture/technology-decisions.md](architecture/technology-decisions.md)).
- TDD: `AccessControlTest` 38건과 `AccessControlClosedSiteTest` 1건을 먼저 실패(RED, 컴파일)시킨 뒤 구현했다. 4건은 테스트가 v1.6에서도 오류가 나는 입력(존재하지 않는 이슈 번호, 전역 컨테이너)을 쓴 것이었다. v1.6 동작을 그대로 재현하도록 테스트를 바로잡고 결함으로 기록했다(R-08~R-10).
- 기록: [subsystems/access-control.md](subsystems/access-control.md)

## [2026-10-22] ingest | 오류 화면 (2단계 b의 기반)
- TDD: `ErrorPagesTest` 8건을 먼저 실패(RED, 컴파일)시킨 뒤 `ErrorViews`, `GlobalErrorHandler`, 템플릿 4개로 구현했다. 예외 처리기로 그린 화면에는 `@ControllerAdvice`의 `@ModelAttribute`가 적용되지 않아 공통 모델 생성을 `LayoutModelFactory`로 분리했다.
- 결과: `./gradlew test`가 H2, PostgreSQL, MariaDB 각각에서 163건 통과.
- 기록: [web/error-pages.md](web/error-pages.md)

## [2026-10-22] ingest | ProjectService (2단계 b-1)
- TDD: `ProjectServiceTest` 14건을 먼저 실패(RED, 컴파일)시킨 뒤 `ProjectService`와 저장소 쿼리로 통과(GREEN)시켰다.
- 기록: [domain/project-service.md](domain/project-service.md)

## [2026-10-22] lint | 로드맵 정리
- 지금까지의 진행과 남은 단계를 [roadmap.md](roadmap.md)에 정리했다. 앞 단계에서 일부러 비워 둔 연결 고리 목록도 포함한다.

## [2026-10-22] lint | 결정 기록 정비
- 선생님 지시로 기술 스택과 정책 결정을 추적할 수 있도록 [decisions.md](decisions.md)를 만들었다. 지금까지의 결정 14건(D-01~D-14), 정책 6건(P-01~P-06), 에이전트가 단독으로 정한 판단 11건(A-01~A-11)을 선택지와 권장 이유와 함께 정리했다.

## [2026-10-22] lint | v1.6 화면은 캡처하지 않는다
- 선생님 지시: v1.6 화면은 여러 문제로 캡처할 수 없으니 새 화면만 캡처한다. `AGENTS.md`, [decisions.md](decisions.md) P-04, [roadmap.md](roadmap.md), 메모리를 갱신했다.

## [2026-10-22] ingest | 프로젝트 레이아웃 (2단계 b-2)
- TDD: `ProjectLayoutTest` 29건과 `ProjectErrorPagesTest` 7건을 먼저 실패(RED, 컴파일)시킨 뒤 `ProjectViewContext`, `RepositoryUrls`, 템플릿 6개로 구현했다. 테스트에 적은 영어 문구 두 곳이 실제 메시지(`This Project`)와 달라 바로잡았다.
- v1.6 결함 1건(R-11, 단축키 Q)을 재현해 기록했다.
- 결과: `./gradlew test`가 H2, PostgreSQL, MariaDB 각각에서 213건 통과.
- 기록: [web/project-layout.md](web/project-layout.md)

## [2026-10-22] ingest | 프로젝트 검사 애너테이션 (2단계 b-3)
- TDD: `ProjectChecksTest` 26건을 먼저 실패(RED, 컴파일)시킨 뒤 애너테이션 7종, `ProjectCheckInterceptor`, `ProjectResourceLocator`로 구현했다. 실패 두 건은 구현 문제가 아니었다. 하나는 테스트가 일반 멤버의 프로젝트 수정을 허용된다고 잘못 가정한 것(v1.6 규칙은 매니저만)이었고, 다른 하나는 예외 처리기가 화면이 채운 `flashMessages`를 덮어쓴 구현 결함이어서 고쳤다.
- v1.6 위키와 코드가 달라 코드를 따른 곳 1건(A-13)을 [decisions.md](decisions.md)에 기록했다.
- 기록: [web/project-checks.md](web/project-checks.md)

## [2026-10-22] query | Git 처리 방식과 데이터 디렉터리
- 선생님께 두 가지를 여쭈어 둘 다 권장안으로 확정하셨다: D-15(JGit 7.x 프로세스 내 사용), D-16(`yona.data-dir` + `-Dyona.data`). 권장 이유는 [decisions.md](decisions.md)에 기록했다.

## [2026-10-22] ingest | Git 저장소 서비스 (2단계 b-4)
- TDD: `GitRepositoryServiceTest` 9건과 `DataDirectoryTest` 4건을 먼저 실패(RED, 컴파일)시킨 뒤 구현해 한 번에 통과시켰다. 결정 D-15, D-16에 따라 JGit 7.8.0을 도입했다.
- 기록: [subsystems/git-repository.md](subsystems/git-repository.md)

## [2026-10-22] ingest | 프로젝트 목록 (2단계 b-5a)
- TDD: `TemplateHelperTest` 11건, `ProjectListTest` 20건 등을 먼저 실패(RED, 컴파일)시킨 뒤 `TemplateHelper`, `ProjectListService`, 컨트롤러, 템플릿으로 구현했다. 비공개 표시 테스트는 v1.6의 `||` 조건 때문에 흐린 항목이 실행되지 않는다는 점을 확인하고 실제 동작에 맞게 바로잡았다.
- 모든 화면 테스트가 하나의 트랜잭션 안에서 도는 탓에 지연 로딩 실수를 놓칠 수 있음을 발견해, 커밋된 데이터를 그리는 `CommittedDataRenderTest`를 추가했다.
- 기록: [web/project-list.md](web/project-list.md)

## [2026-10-22] ingest | 프로젝트 생성 (2단계 b-5b)
- TDD: `ProjectCreateTest` 20건을 먼저 실패(RED, 컴파일)시킨 뒤 구현했다. 마지막 두 실패 중 하나는 `vcs` 없이 제출한 테스트 입력 오류(v1.6도 500)였고, 다른 하나는 템플릿이 null 소유자를 `null` 문자열로 출력한 구현 결함이어서 고쳤다.
- 구현 중 판단한 것 3건(A-15~A-17)을 [decisions.md](decisions.md)에 기록했다. v1.6 결함 R-13을 재현했다.
- 기록: [web/project-create.md](web/project-create.md)

## [2026-10-22] query | 마크다운 라이브러리 조사
- 선생님이 다른 추천 라이브러리를 조사해 달라고 하셔서 commonmark-java, flexmark, JetBrains markdown, GraalJS+marked.js를 비교했다. 선생님이 권장안 (a)를 확정하셨다: D-17.

## [2026-10-22] ingest | 마크다운 렌더러
- TDD: 기준 구현(`MarkedReference`)을 먼저 만들고 차이 테스트로 RED를 확인한 뒤 `MarkdownRenderer`를 구현했다. 처음 4건이 달랐고(제목 앵커 3, 작업 목록 1) v1.6이 커스터마이즈된 marked.js를 쓴다는 사실을 발견해 후처리로 맞췄다. 기록: [web/markdown.md](web/markdown.md)

## [2026-10-22] ingest | 프로젝트 홈 (2단계 b-5c-1)
- 선생님이 렌더 결과 캐시는 두라고 정정하셔서 `RenderedMarkdownCache`(A-18)를 추가했다.
- TDD: README 서비스 7건, 마일스톤 현황 8건, 방문 기록 5건, 링크 변환 9건, 홈 통합 31건을 먼저 쓰고 구현했다. 홈 테스트는 처음에 31건 전부 실패(RED)했고 구현 뒤 6건이 남았는데 5건은 테스트 기대값 오류(레이아웃이 제목을 `|:|`로 나눈다는 점을 놓침)였고 1건은 마크다운 응답에 문자셋이 없는 실제 결함이었다.
- v1.6 결함 R-14(README 상대 경로 변환)를 재현했다. 기록: [web/project-home.md](web/project-home.md)

## [2026-10-22] ingest | 마크다운 프로젝트 문맥 (2단계 b-5c-2)
- TDD: 링크 후처리 7건, 자동 링크 16건, 이슈 링크 변환 8건, 파이프라인과 캐시 7건을 먼저 쓰고 구현했다.
- v1.6의 캐시 동작(적중 시 이전 결과 반환 후 갱신, 일반 렌더링과 키 공유)과 자동 링크 결함을 재현하고 R-15, R-16으로 기록했다. 기록: [web/markdown.md](web/markdown.md)

## [2026-10-22] ingest | 첨부 파일 핵심 (2단계 b-5c-3a)
- 프로젝트 설정의 로고 업로드에 필요해 첨부 저장과 내려받기를 먼저 이식했다. TDD: 서비스 12건, 컨트롤러 13건을 먼저 쓰고 구현했다. 기록: [web/attachments.md](web/attachments.md)

## [2026-10-22] ingest | 프로젝트 설정 (2단계 b-5c-3b)
- TDD: `ProjectSettingTest` 34건을 먼저 쓰고(컴파일 오류 후 RED) 구현했다. 구현 중 알게 된 점: `th:with`에 `${}` 없이 쓰면 문자열 그대로 들어간다, 보안 필터가 감싼 요청은 `MultipartHttpServletRequest`로 형변환할 수 없어 `@RequestParam MultipartFile`을 쓴다.
- v1.6의 mass assignment는 재현하지 않고 A-24로 기록했다. 기록: [web/project-setting.md](web/project-setting.md)

## [2026-10-22] ingest | 구성원 관리와 프로젝트 삭제 (2단계 b-5c-3c, 3d)
- TDD: 구성원 28건, 삭제 서비스 5건, 삭제 화면 8건을 먼저 쓰고 구현했다. 삭제 서비스는 v1.6 외래 키 그래프에서 프로젝트로부터 닿는 37개 테이블을 모두 처리하는지 구조 테스트로 확인한다.
- 알림 이벤트는 Spring 이벤트로 발행만 한다(A-25). 기록: [web/project-members.md](web/project-members.md), [web/project-delete.md](web/project-delete.md)

## [2026-10-22] decision | A-24 확정
- 설정 저장의 mass assignment는 재현하지 않기로 선생님이 확정하셨다. 원칙: 화면을 쓰는 사용자에게 정상인 동작은 v1.6과 같게, 요청 조작으로만 가능한 보안 허점은 유지하지 않는다.

## [2026-10-22] ingest | 프로젝트 이전과 저장소 종류 변경 (2단계 b-5c-3e)
- TDD: 이전 20건, 저장소 종류 변경 7건을 먼저 쓰고 구현했다. 이전 이력 기록은 설정과 공유하려고 `Project`로 옮겼다. 수락할 때 처리한 요청 행 자신이 지워지는 v1.6 동작을 재현했다. 기록: [web/project-transfer.md](web/project-transfer.md)

## [2026-10-22] ingest | 조직과 가입 신청 (2단계 c)
- TDD: 로그인 ID 규칙 5건, 조직 생성 8건, 홈과 목록 20건, 설정 13건, 구성원 삭제 탈퇴 27건, 가입 신청 9건을 먼저 쓰고 구현했다.
- v1.6 결함 R-19(탈퇴 조건이 거꾸로, 대소문자만 바꾸는 이름 변경 거부)를 재현했다. 요청 조작으로만 가능한 입력은 받지 않았다(A-29). 기록: [web/organization.md](web/organization.md)

## [2026-10-22] ingest | 가입과 비밀번호 재설정 (2단계 d-1, d-2)
- TDD: 가입 17건과 설정 변형 5건, 비밀번호 재설정 12건을 먼저 쓰고 구현했다. 메일은 이벤트로 발행만 한다(A-30).
- 구현 중 발견: `POST /{user}` 매핑이 생기며 `/favicon.ico` 자원 처리기가 405로 막혀 컨트롤러로 바꿨다. 기록: [web/signup-and-password.md](web/signup-and-password.md)

## [2026-10-22] ingest | 계정 설정과 첨부 올리기 (2단계 d-3)
- TDD: 계정 설정 23건, 이메일 14건, 첨부 올리기 16건을 먼저 쓰고 구현했다. 알림 탭은 알림 서브시스템에서 만든다.
- v1.6 결함 R-20(임시 첨부 정리 조건), R-21(아바타 크기)를 재현했다. 기록: [web/account.md](web/account.md)

## [2026-10-22] ingest | 사용자 검색 API (2단계 d-4a)
- TDD: 6건. 구성원 화면의 자동 완성 주소가 `/users`가 아니라 `/-_-api/v1/users`여야 해서 두 템플릿을 고쳤다. 기본 로그인 페이지 지정은 홈 컨트롤러에 이미 있어 중복 매핑을 제거했다.
- 사용자 페이지는 이슈와 풀 리퀘스트 목록에 의존하므로 해당 단계 뒤로 미룬다(로드맵 2d-4b).

## [2026-10-22] ingest | 이슈 라벨과 분류 (3단계 a)
- TDD: `IssueLabelTest` 31건. 관리 화면, 라벨 JSON, PJAX 목록, 만들기, 수정, 삭제, 분류 CRUD, 복사, 색 스타일 시트와 ETag.
- v1.6 결함 R-22(3자리 색)를 재현했고 A-33(게시글 연결 정리), A-34(타 프로젝트 분류 거부)를 기록했다.

## [2026-10-22] ingest | 공용 편집기 부품과 마일스톤 (3단계 b)
- TDD: `MentionListTest` 13건, `TextBodyLimitFilterTest` 3건, `MilestoneTest` 30건. 편집기, 업로더, 마크다운 도움말 조각과 `AttachmentListing`, `PostAttachments`를 옮겼다.
- v1.6 결함 R-23(멀티파트가 아니면 임시 첨부가 옮겨지지 않음)을 재현했고 A-35(멘션 후보 읽기 권한), A-36(폼 본문 한도 설정), A-37(정렬 항목 제한)을 기록했다.
- 마일스톤 보기 화면의 이슈 탭은 이슈 화면(3c)을 옮길 때 채운다.

## [2026-10-23] ingest | 이슈 목록과 검색 (3단계 c-1)
- TDD: `IssueSearchServiceTest` 18건(세 DB), `IssueListTest` 20건, `IssueExcelTest` 3건, 마일스톤 보기의 이슈 영역 2건. 제목 머리말 도우미(`TemplateHelper.headerWordsHtml` 등)와 `UserDisplay.pureName`을 추가했다.
- A-38(머리말 이스케이프), A-39(jxl), A-40(정렬 기준 제한), A-41(잘못된 조건은 400)을 기록했다.

## [2026-10-23] ingest | 이슈 등록 (3단계 c-2a)
- TDD: `IssueSearchServiceTest` 18건 외에 `TitleHeadServiceTest` 4건, `MentionServiceTest` 6건, `IssueEventServiceTest` 6건, `IssueServiceTest` 9건, `IssueCreateTest` 21건, `IssueFormApiTest` 8건.
- MariaDB에서 이슈 댓글을 id로 읽을 때 조인 표가 61개를 넘는 문제가 드러나 `hibernate.max_fetch_depth: 2`를 적용했다(A-43). A-42를 기록했다.

## [2026-10-23] ingest | 이슈 보기, 다음 상태, 삭제 (3단계 c-2b)
- TDD: `IssueViewTest` 38건, `IssueActionsTest` 8건, `IssueServiceTest`에 상태 전환과 삭제 6건. 공용 댓글 템플릿 조각(`common/comment-*`, `child-comments`, `partial-history`, `attachment-file`)과 이벤트 HTML 생성기를 옮겼다.
- Thymeleaf에서 `th:replace`가 `th:if`보다 먼저 처리되는 문제를 발견해 3c-1의 목록 템플릿까지 포함해 17곳을 중첩 블록으로 고쳤다.
- R-24(삭제해도 멘션 기록 유지), R-25(추천자 대화상자에서 현재 사용자 제외)를 재현했다.

## [2026-10-23] ingest | 이슈 수정 (3단계 c-2c)
- TDD: `IssueEditTest` 17건, `IssueServiceTest`에 수정 12건. v1.6의 `diff_match_patch`를 그대로 가져와 변경 이력을 만든다(A-44). A-45, A-46을 기록했다.

## [2026-10-23] ingest | 이슈 댓글과 추천 (3단계 c-3a)
- TDD: `IssueCommentTest` 22건. R-26(댓글 삭제 시 멘션 기록 유지)을 재현했다.

## [2026-10-23] ingest | 이슈 일괄 변경 (3단계 c-3b)
- TDD: `IssueMassUpdateTest` 10건. 세 DB 1020건 통과.

## [2026-10-23] ingest | 구독과 이슈 즐겨찾기 (3단계 c-3c-1)
- TDD: `WatchAndFavoriteTest` 8건.

## [2026-10-23] ingest | 이슈 화면 JSON API (3단계 c-3c-2)
- TDD: `IssueApiTest` 22건. `ApiUserNodes` 분리(A-48), `IssueJson`. 세 DB 1050건 통과.

## [2026-10-23] ingest | 게시판과 온라인 커밋 (3단계 d)
- TDD: BoardListTest 14, BoardViewTest 15, BoardWriteTest 31, BoardCommentTest 17, OrganizationBoardTest 10, OnlineCommitTest 8, LineEndingTest 5. 세 DB 1150건 통과.
- R-27(줄바꿈 변환), R-28(정렬 링크 라벨), R-29(라벨 변경 시 머리말 개수), A-49~A-52. A-50은 선생님 확인 대기.

## [2026-10-23] ingest | 사용자 메뉴, 수신자 미리 보기, 화면 검증 (2d-5, 3단계 마무리)
- Playwright 화면 검증으로 이슈, 게시판, 마일스톤, 라벨 화면 11곳을 캡처하고 상호작용을 점검했다. 타임리프 JS 인라인 뒤 `+`가 지워지는 오류를 고치고 `currentUser.pureName`을 도입했다.
- TDD: `UserMenuTest` 16, `FavoriteApiTest` 5, `NotificationReceiversTest` 7, `IssueApiTest` +3. 세 DB 1183건 통과.
- R-30, R-31, A-53~A-55.

## [2026-10-23] ingest | 번역 API, 알림 비동기 결정 (D-18)
- 선생님이 A-50을 위키에 기록하라고 하셨고 외부 REST API 작업을 빼먹지 말라고 하셨다. 알림 메일은 v1.6처럼 비동기 스케줄러로 보낸다(D-18).

## [2026-10-23] ingest | 알림 이벤트 생성 (4단계 a)
- TDD: NotificationEventsTest 11, NotificationListenerTest 11, NotificationMemberRequestTest 4. A-56, A-57. 외부 REST API 작업은 4단계 뒤로 미루고 이슈 외부 API 테스트와 서비스는 보관했다.

## [2026-10-23] ingest | 알림 메일 발송, 홈 알림 목록, 프로젝트 구독과 알림 설정 (4단계 b~d)
- TDD: MailDiffTest 6, NotificationMessagesTest 9, NotificationMergerTest 4, MailRecipientsTest 5, MailHtmlTest 6, NotificationMailSenderTest 8, NotificationMailSchedulerTest 4, SystemMailsTest 5, NotificationCleanerTest 2, NotificationListTest 8, ProjectWatchTest 10. A-58~A-61, R-32~R-35.
- 프로젝트 구독(watch/unwatch/toggle)과 구독자 목록이 v2에 없던 것을 이 단계에서 채웠다.

## [2026-10-23] ingest | 외부 이슈 REST API (3e)
- TDD: ExternalIssueApiTest 16. A-63, A-64. EventType.descr를 descrKey로 바로잡았다(v1.6 getDescr는 번역 문구).

## [2026-10-23] ingest | 외부 프로젝트, 게시판 REST API (3e 마무리)
- TDD: ExternalProjectApiTest 12, ExternalBoardApiTest 4. A-65, R-36, R-37. 3e 완료. 마이그레이션 화면(MigrationApp)은 별도 항목으로 남았다.

## [2026-10-23] ingest | Git smart-HTTP 서비스 (5단계 5a-1)
- TDD: GitHttpTest 10(실제 JGit 클라이언트). A-66. 5단계를 5a~5f로 나눴다.

## [2026-10-23] ingest | 코드 브라우저 (5단계 5b-1)
- TDD: CodeBrowserServiceTest 11, CodeBrowserWebTest 14. A-67~A-70, R-38, R-39. jgit.archive 의존성 추가, MediaTypes와 UrlPaths 공용화.

## [2026-10-23] ingest | 브랜치 목록, 기본 브랜치, 삭제 (5단계 5b-2)
- TDD: BranchesTest 7.

## [2026-10-23] ingest | 커밋 이력 (5단계 5b-3)
- TDD: CommitHistoryTest 6. A-71, R-40. 커밋 보기와 비교는 diff 엔진과 함께 5c-0으로 옮겼다.

## [2026-10-23] ingest | diff 엔진 (5단계 5c-0a)
- TDD: DiffServiceTest 9.

## [2026-10-23] ingest | 커밋 보기와 비교 (5단계 5c-0b)
- TDD: DiffHtmlRendererTest 6, CommitViewTest 6. A-72, R-41. 스레드 표시는 읽기 전용이고 등록과 상태 변경은 5d에서 한다.

## [2026-10-23] ingest | 포크 (5단계 5c-1)
- TDD: ForkTest 12. A-73.

## [2026-10-23] ingest | 풀 리퀘스트 목록 (5단계 5c-2)
- TDD: PullRequestListTest 12. A-74.

## [2026-10-23] ingest | 풀 리퀘스트 만들기 (5단계 5c-3)
- TDD: PullRequestMergerTest 8, PullRequestCreateTest 14. A-75, R-42. `UserLookup`으로 커밋 작성자 조회를 v1.6 규칙(대표, 보조, 대소문자 무시)으로 바로잡았다.

## [2026-10-23] ingest | 풀 리퀘스트 보기와 동작 (5단계 5c-4, 5c-5)
- TDD: PullRequestViewTest 13, PullRequestActionTest 10. A-76~A-78, R-43. 풀 리퀘스트 댓글(5c-6)은 5d로 옮겼다.

## [2026-10-23] ingest | 코드 리뷰 댓글과 푸시 뒤 일 (5단계 5d)
- TDD: ReviewCommentTest 9, PostReceiveJobsTest 4. A-79, A-80, R-44. 실행기 이름을 `BackgroundJobRunner`, 설정 키를 `yona.background-async`로 일반화했다.

## [2026-10-23] ingest | 웹훅 (5단계 5e)
- TDD: WebhookSettingsTest 6, WebhookDispatchTest 7. A-81, A-82, R-45.

## [2026-10-23] ingest | 저장소 가져오기 (5단계 5f)
- TDD: ImportTest 7. A-83, R-46. 5단계 완료.

## [2026-10-23] lint | 5단계 화면 검증(Playwright)
- H2 인메모리 서버를 띄우고 로그인, 프로젝트 만들기, 실제 `git push`(HTTP), 코드 보기와 이력과 브랜치, 커밋 보기와 댓글, 비교, 포크, 풀 리퀘스트 만들기와 보기와 변경과 받아들이기, 웹훅 등록, 가져오기 폼을 새 v2 화면으로 확인했다.
- 발견해 고친 결함: 데이터 디렉터리를 지정하지 않으면 저장소가 `/repo/git`(파일 시스템 루트)에 만들어짐(`File(\"\")`를 부모로 쓴 탓, `.`로 수정), 저장소가 없는 프로젝트를 복제하려 하면 잘린 응답이 나가던 문제(404로 수정). 스크립트 오류는 R-47(v1.6 그대로).

## [2026-10-23] lint | 스키마 대조에 기본값, 자동 증가, ON UPDATE 추가
- `V16SchemaTest`에 세 항목을 추가해 세 DB에서 비교했다. 기본값 24개가 비어 있었고 외래 키 4개의 `ON UPDATE CASCADE`가 빠져 있어 엔티티를 고쳤다(A-85). 자동 증가 51개는 처음부터 일치했다.

## [2026-10-25] ingest | 사용자 페이지와 내 파일 (2단계 2d-4b)
- TDD: UserPageTest 17, UserFilesTest 8, UserLeaveTest 3, TemplateHelperTest 3 추가. `/user/issues`(내 이슈)가 로드맵에서 빠져 있음을 발견해 2d-4c로 추가했다. A-86, R-48.
- 알림 메일의 리소스 주소 변환(`resourceUrl`)을 `ResourceUrls`로 꺼내 내 파일의 위치 열과 함께 쓴다.
- Playwright로 새 화면 3곳을 확인했다.

## [2026-10-25] ingest | 내 이슈 (2단계 2d-4c)
- TDD: MyIssuesTest 20. `IssueSearchService`에 프로젝트를 가리지 않는 검색(`searchAll`, `countAll`, `countMentionedOpenIssues`)을 더했다. R-49.

## [2026-10-25] ingest | 검색 (6단계 6a)
- TDD: SearchTest 28, SearchSnippetsTest 7. `SearchService`(JPQL)와 `SearchRowAssembler`, 범위별 결과 화면 세 가지. A-87, R-50.

## [2026-10-25] ingest | 도움말, 통계, UI 견본 (6단계 6b)
- TDD: HelpAndStatisticsTest 6. A-88, R-51.

## [2026-10-25] ingest | 사이트 관리: 틀과 사용자 관리 (6단계 6c-1)
- TDD: SiteUserManagementTest 21, TemplateHelperTest 3 추가. `@SiteManagerOnly`, `SiteUserService`, `socialDate`. A-89, R-52. 사용자 페이지의 BLOCKED 배지가 삭제된 사용자에게도 붙도록 고쳤다(v1.6 `isLocked`).

## [2026-10-25] ingest | 사이트 관리: 프로젝트, 게시글, 이슈 목록 (6단계 6c-2)
- TDD: SiteContentListsTest 12. `SiteContentService`, `SiteContentController`. R-53.

## [2026-10-25] ingest | 사이트 관리: 메일 쓰기와 대량 메일 (6단계 6c-3)
- TDD: SiteMailTest 13. `SiteMailService`, `SiteMailController`. A-90, R-54.

## [2026-10-25] ingest | 사이트 관리: 업데이트 확인과 진단 (6단계 6c-4)
- TDD: SemverTest 5, UpdateCheckServiceTest 8, DiagnosticServiceTest 3, SiteUpdateAndDiagnosticTest 10. `build.gradle.kts`에 `buildInfo()` 추가. A-91, R-55. 이슈 목록 작성자 이름을 v1.6처럼 표시 이름으로 고쳤다.

## [2026-10-25] ingest | 데이터 내보내기와 가져오기 (6단계 6d)
- TDD: DataTablesTest 4, DataExchangeTest 13, SiteDataTest 13. `DataTables`(47개 테이블 명세), `DataService`, `SiteDataController`. A-92, R-56. H2, PostgreSQL, MariaDB에서 모두 확인.

## [2026-10-25] ingest | 최초 기동 화면 (6단계 6e)
- TDD: SiteSecretTest 11. `SiteSecret`, `SiteSecretInterceptor`, `FakeController`, 템플릿 `welcome/secret`, `welcome/restart`. D-22(선생님: B안), A-93, R-57. 새 서버로 기본 설정 기동, 입력, 재시작, 새 비밀번호 로그인까지 확인.


## [2026-10-25] ingest | 이전(Migration) 화면 (6단계 6f)
- TDD: MigrationExportTest 14, MigrationPageTest 5. `MigrationService`, `MigrationController`, `GithubTokenClient`, 템플릿 `migration/home`. D-23(접근 기준), A-94, R-58.

## [2026-10-25] ingest | 소셜 로그인 (7단계 7a)
- TDD: SocialAccountServiceTest 16, SocialAccountPolicyTest 3, SocialProviderTest 7, SocialLoginTest 26. `SocialAccountService`, `OAuth2SocialProvider`, `SocialProviders`, `SocialSession`, `SocialLoginController`, 템플릿 `restricted`. D-24, A-95, R-59.

## [2026-10-25] ingest | LDAP 인증, 소셜 전용 모드 (7단계 7b)
- TDD: JndiLdapDirectoryTest 8, LdapAuthServiceTest 11, LdapEmailLoginTest 4, LdapLoginTest 3, SocialLoginOnlyTest 2. `JndiLdapDirectory`, `LdapAuthService`, `LocalAuthenticator`(LDAP 분기, `authenticateBasic`), `LoginController`. D-24, A-96, R-60.

## [2026-10-25] ingest | 이메일 수신 (7단계 7c)
- TDD: MailParsingTest 14, MailResourceCreatorTest 11, MailHandlerTest 20, MailHandlerMultiProjectTest 1, MailboxServiceTest 13. `EmailAddressWithDetail`, `MessageIds`, `MailContent`, `MailResourceCreator`, `MailHandler`, `MailboxService`, `MailboxStarter`, `MailboxDiagnostic`, `NotificationExclusions`, `PropertyService`. D-25, A-97, R-61, T-4.

## [2026-10-25] ingest | Subversion (8단계 8a~8d)
- TDD: SvnRepositoriesTest 11, SvnRepositoryServiceTest 6, SvnCodeBrowserTest 10, SvnCodeBrowserWebTest 9, SvnReadmeTest 5, SvnHistoryWebTest 6, SvnCommitViewWebTest 15, SvnDavTest 8(실제 SVNKit HTTP 클라이언트). `SvnRepositories`, `SvnCodeBrowser`, `CommitCommentService`, `JavaxServletBridge`, `SvnDavService`, `SvnDavFilter`. D-13, D-26, A-98, R-62.

## [2026-10-25] ingest | 동등성 점검 9a: 라우트 대조와 라벨 JSON API
- `RouteParityTest`로 v1.6 `conf/routes`와 v2 요청 매핑을 대조해 누락 17건을 찾았다(리뷰 목록, 조직 이슈와 PR 목록, 라벨 API 5건, 코드 메타데이터 AJAX 2건, 별칭 4건). 선생님 지시로 누락 항목은 위키에 v1.6 동작을 먼저 적고 구현한다.
- 라벨 API 5건을 먼저 이식했다: `LabelApiController`, `LabelService`, `LabelApiTest` 20건, [web/label-api.md](web/label-api.md). A-99, R-63.
- 조직 이슈 목록과 풀 리퀘스트 목록 3건을 이식했다(위키를 먼저 쓰고 테스트 22건을 먼저 쓴 뒤 구현). `OrganizationListController`, `OrganizationListAssembler`, `IssueSearchService.searchIn/countIn`, `PullRequestSearchCondition.organizationProjectIds`. [web/organization-lists.md](web/organization-lists.md). A-100, R-64.
- 리뷰 목록과 엑셀을 이식했다(위키 먼저, 테스트 15건 먼저). `ReviewListController`, `ReviewSearchService`, `ReviewExcelExporter`, 템플릿 `reviewthread/list`. [web/review-list.md](web/review-list.md). A-101, R-65.
- 코드 메타데이터 AJAX 두 경로는 `{*path}` 패턴이 끝 슬래시 형태를 이미 처리하고 있어 대조 규칙의 오탐으로 판명했다(테스트 추가). 홈 화면 별칭 3건을 이식했다([web/home-page.md](web/home-page.md)).
- `/_init`은 선생님이 v1.6 그대로 재현하라고 결정하셨다(D-27, R-66). `RepositoryInitController`, `RepositoryInitTest` 5건, [web/repository-init.md](web/repository-init.md). 라우트 대조(9a) 완료.

## [2026-10-25] lint | 동등성 점검 9b: 메시지 키 대조
- `MessageParityTest` 2건. 5개 언어 번들 키가 v1.6과 같고 템플릿이 참조하는 키가 모두 있다. 이슈 엑셀의 `common.comment.created`는 v1.6 번들에도 없어 머리글에 키 이름이 나온다(R-67).

## [2026-10-25] ingest | 프로젝트 홈의 이력, 대시보드 탭 (동등성 점검 9d에서 발견)
- 템플릿 대응 점검에서 `partial_history`, `partial_dashboard*`가 v2에 없음을 발견했다. 프로젝트 홈의 탭 링크는 있었지만 내용이 비어 있었다(같은 라우트에서 `tabId`로 갈리는 동작이라 9a 라우트 대조로는 드러나지 않는다).
- 위키([web/project-home.md](web/project-home.md))에 v1.6 동작을 먼저 적고 `ProjectHomeTabsTest` 16건을 먼저 쓴 뒤 구현했다. `ProjectHistoryService`, `ProjectDashboardService`, `ProjectHomeTabsAssembler`, 템플릿 `project/partial-history`, `partial-dashboard`. A-102, R-68.
- 컴파일된 v1.6 역방향 라우터(`legacy-yona/target/.../routes_reverseRouting.scala`)로 `issues(…, "open")`가 `?state=open`을 만든다는 것을 확인해, 이전에 "열리지 않는 주소"로 적은 R-49(4)를 바로잡고 내 이슈, 사용자 페이지, 조직 목록의 라벨 링크를 고쳤다.

## [2026-10-25] lint | 동등성 점검 9c~9e
- 9c: `V16SchemaTest`가 이미 스키마를 대조하고 있어 새 작업이 없다.
- 9d: v1.6 템플릿의 CSS 클래스와 id를 v2와 대조해 조직용 403 화면(`error/forbidden-organization`, `OrganizationSettingTest`, `OrganizationMemberTest`)과 로그인 화면의 이메일 인증 안내(`LoginEmailVerificationHelpTest`)를 이식했다. `public` 723개 파일과 LESS 20개는 v1.6과 같다. 호출이 없는 v1.6 템플릿 11개는 이식하지 않는다.
- 9e: 요청 매개변수와 액션 이름을 대조했고, 라우트 대조에 가려졌던 포괄 라우트(끝 슬래시 제거 이동)를 `GlobalErrorHandler`에 구현했다(`TrailingSlashRedirectTest` 6건, A-103).

## [2026-10-25] verify | 새 화면의 브라우저 조작 점검
- 선생님 지적: 지금까지는 목록 표시 위주로 검증했다. 서버 요청과 응답 계약은 상태 코드별로 여러 건 검증되고 있음을 라우트 대조로 확인했다. 비어 있던 곳은 화면의 자바스크립트 조작이었다.
- Playwright로 실제 조작을 확인했다(실제 git push 데이터 사용): 조직 이슈 목록(쪽 이동, 열림/닫힘 탭, 정렬, 검색어, 빠른 검색, 프로젝트 선택기, 2단 보기 저장/유지/패널), 라벨 편집기(추가, 새 범주 확인창, 중복 방지, 이름 수정, 범주 수정, 삭제 확인, 새로고침 후 유지, 이슈 폼 노출, labels.css), 리뷰 목록(탭, 검색, 빠른 검색, 정렬, 엑셀), 프로젝트 홈 이력/대시보드 탭, 조직 풀 리퀘스트 목록(검색, 닫힘 탭). 모두 정상이다.
- 발견: v1.6에서 물려받은 스크립트 오류 1건을 R-69로 기록했다.
- 이어서 이슈(댓글 수정·삭제, 추천, 체크리스트, 담당자, 마일스톤, 우선순위, 폼 작성·삭제, 독점 라벨), 마일스톤(수명 주기), 게시판(작성, 댓글, 체크리스트, 수정, 삭제), 풀 리퀘스트(수정, 닫기, 다시 열기, 병합 후 저장소 확인, 브랜치 삭제·복구)를 브라우저로 확인했다.
- 결함 1건: 변경 화면에서 줄에 단 리뷰 댓글이 모두 "어긋남"(outdated)으로 분류되고 줄 사이에 나오지 않았다. 폼이 본문에도 담아 보내는 `commitId`를 컨트롤러가 읽었기 때문이다. v1.6은 쿼리 문자열만 읽는다. 테스트가 `isOutdated` 경로를 전혀 다루지 않아 놓쳤다. 테스트 2건을 추가하고 고쳤다. v1.6 스크립트 오류 2건은 R-70, R-71로 기록했다.
- 남은 화면까지 브라우저로 확인했다(모두 정상): 풀 리퀘스트 변경 화면의 줄 댓글, 답글, 스레드 닫기, 병합 후 저장소 확인, 브랜치 삭제와 복구, 충돌 있는 풀 리퀘스트(확인창, 병합 버튼 비활성), 포크(비동기 대기 화면 후 자동 이동), 프로젝트 설정(개요, 메뉴 끄기, 비공개), 멤버 추가·역할·삭제, 프로젝트 이전 요청과 삭제, 조직 설정과 멤버 관리, 사이트 관리(사용자 게스트·잠금·관리자 승격·비밀번호 초기화·삭제, 프로젝트 삭제, 글·이슈 목록, 데이터 내보내기), 사용자 설정(프로필, 이메일, 비밀번호 오류, 토큰 재생성, 알림 스위치), 가입·로그아웃·로그인 실패, 이슈 일괄 수정(상태, 담당자, 전체 선택), 하위 이슈 진행률, 댓글 추천, 이슈 공유, 감시 토글, 첨부 올리기와 내려받기, 코드 탭(폴더, 파일, README, raw, zip, 이력, 커밋, 브랜치), 프로젝트 생성 폼, 사용자 홈 탭.
- 확인하면서 얻은 v1.6 동작(기대와 달랐던 것): 존재하지 않는 프로젝트는 모든 경로에서 403이다(`error.forbidden.or.notfound`). 풀 리퀘스트 개요에는 댓글 입력창이 없고 줄 댓글은 변경 화면에서만 단다. 충돌이 있는 풀 리퀘스트는 확인창을 거쳐 보낼 수 있으나 병합 버튼은 비활성이다. 새 범주를 만들 때는 복수/단일 선택 확인창이 먼저 뜬다. 마일스톤 상태 값은 `OPEN`, `CLOSED` 대문자이다.
- 관찰(분류하지 않음): 조직 멤버 역할을 한 번 바꿀 때 `member/:id/edit` 요청이 두 번 나갔다. 스크립트가 v1.6과 같고 바인딩 수도 프로젝트 화면과 같아 앱 코드 문제로 보지 않는다.

## [2026-10-25] verify | 메일, 나머지 화면, 사이트 크롤
- mailpit(`docker run axllent/mailpit`, SMTP 21025, 웹 28025)을 띄워 메일을 확인했다: 비밀번호 재설정(메일의 링크로 새 비밀번호를 정하고 로그인), 이메일 검증과 대표 이메일 지정, 사이트 메일, 알림 메일(새 이슈, 댓글, 새 글. 지연 180초 뒤 발송되며 받는 사람은 숨은참조). 사이트 메일 화면의 "Mailer has not been configured" 경고는 v1.6과 같이 host, user, password 셋이 모두 있어야 사라진다.
- 추가로 확인한 화면(모두 정상): 프로젝트 가입 요청과 수락, 탈퇴, 풀 리퀘스트 승인과 취소(승인 수에 따른 병합 버튼), 브랜치 목록·기본 지정·삭제, 웹훅(push 포함 여부, 이슈 이벤트, 헤더), 프로젝트 가져오기(git URL), 마크다운 미리보기, 멘션 자동완성, 개요 인라인 수정, 즐겨찾기 별, 통계(v1.6도 "Under Construction"), 비교 화면, 내 파일, 직접 이슈 만들기.
- 결함 1건(수정): 프로젝트가 있는 조직의 홈이 `LazyInitializationException`(`Project.labels`)으로 열리지 않았다. 템플릿이 트랜잭션 밖에서 지연 로딩 연관을 읽었기 때문이다. `CommittedDataRenderTest`에 조직 홈, 이슈, 풀 리퀘스트, 게시판 목록 테스트를 추가해 재현하고 `OrganizationService.labelsOf`로 고쳤다.
- 같은 유형이 더 없는지 풍부한 데이터(조직, 라벨, 마일스톤, 이슈, 게시글, 풀 리퀘스트, 리뷰)를 만들어 관리자, 프로젝트 멤버, 비로그인으로 링크를 따라 510페이지를 크롤했다. 500과 서버 예외는 없었다.
- v1.6과 같아서 결함이 아닌 것: 가입 수락 알림(`MEMBER_ENROLL_ACCEPT`)의 "알 수 없는 알림 이벤트" 경고는 v1.6 `getMessage`의 기본 분기와 같다. POST 전용 주소를 GET으로 열면 포괄 라우트 때문에 404이다.
- 브라우저로 확인하지 못한 것: 소셜 로그인(외부 IdP 필요), LDAP 인증, IMAP 메일 수신(mailpit은 SMTP만 받는다). 이 셋은 테스트로만 검증된다.

## [2026-10-25] verify | LDAP, IMAP (컨테이너)
- OpenLDAP(`osixia/openldap:1.5.0`, 포트 10389)와 GreenMail(`greenmail/standalone`, SMTP 3025, IMAP 3143)을 띄워 확인했다. 컨테이너는 `docker run`으로 띄웠다(이 환경에는 `docker compose`가 없다).
- LDAP: 바인드 주체는 `<userNameProperty>=<입력한 이름>,<postfix>`이고 검색은 `<loginProperty>=<입력한 이름>`이라 DN의 이름과 로그인 속성 값이 같아야 한다(예: `cn=hong`, `uid=hong`). 사용자 생성(이름, 이메일), 틀린 비밀번호 거부, LDAP를 켜면 로컬 관리자도 `fallbackToLocalLogin` 없이는 로그인할 수 없음을 확인했다. 이메일을 입력하면 이메일 자체를 바인드 주체로 쓰므로(AD용) OpenLDAP에서는 거부된다(v1.6과 같다). OpenLDAP 기본 접근 규칙은 일반 사용자가 검색 기준 항목을 읽지 못하게 해서 `No Such Object`가 되므로 사용자 읽기 권한이 필요하다.
- IMAP: 새 메일로 이슈 만들기, `In-Reply-To`로 댓글, 주소 세부(`소유자/프로젝트/종류/번호`, 종류는 `issue_post` 같은 `ResourceType.resource` 값)로 댓글, `help` 회신, 모르는 보낸 사람 무시, 없는 프로젝트 오류 회신, HTML 본문과 `cid:` 인라인 이미지(`/files/번호`)와 첨부 저장을 확인했다. 오류 회신의 "Hello, null"은 보낸 사람 표시 이름이 없을 때 v1.6도 같다.
- 결함 1건(수정): 알림 메일에 `Reply-To`가 없었다. v1.6은 수신 주소가 있으면 `수신주소+소유자/프로젝트/종류/번호@도메인`을 `Reply-To`로 달아 메일의 회신이 곧 댓글이 되고, 본문 안내도 회신 문구(`notification.replyOrLinkToViewHtml`)가 된다. v2는 KDoc에 "7단계에서 더한다"고만 적고 더하지 않아 알림 메일에 답장해도 `To`에 세부가 없어 댓글이 되지 않았다. `MailboxAddress`(수신 주소와 `Reply-To` 계산, `MailHandler`와 공용)와 테스트 8건을 더했다.
- `fallbackToLocalLogin`: LDAP가 켜져 있고 서버가 올라와 있으면 LDAP 사용자는 LDAP로, 로컬 관리자는 로컬 비밀번호로 로그인되고 틀린 비밀번호는 거부된다. LDAP 서버를 내려도 로컬 관리자가 로그인된다. LDAP 로그인에 성공하면 로컬 사용자의 비밀번호를 같은 값으로 맞추므로(v1.6 `authenticateWithLdap`) LDAP가 내려가도 마지막으로 쓴 LDAP 비밀번호로 로그인된다.
- 소셜 로그인: 지원 제공자는 GitHub와 Google 두 가지이다(`yona.social.login-support`). 가짜 OAuth 서버(인증, 토큰, 사용자 정보 주소를 설정으로 바꿔)로 인증 화면 이동, 코드를 토큰으로 교환(`Authorization: Bearer`로 사용자 정보 읽기), 가입(로그인 ID는 이메일 앞부분), 재로그인에 사용자 중복 없음, 로그아웃, 거절 안내를 확인했다. 이메일이 없는 계정은 `/logout`을 거쳐 홈에서 안내 알림(`app.warn.cannot.access.email.information`)을 보인다.

## [2026-10-25] verify | 남은 화면 2차 점검
- 이슈 수정 폼(제목, 본문, 마일스톤, 담당자, 라벨, 마감일), 다른 프로젝트로 이동(주소가 대상 프로젝트로 이동), 초안 저장, 아바타(업로드 후 자르기 대화상자에서 저장), 프로젝트 로고, 첨부 업로드·다운로드·삭제와 내 파일 목록, 알림 목록(항목 클릭 시 해당 댓글로 이동, `More`는 `/notification?from=&limit=`), 도움말 링크(`/info`는 v1.6에도 있는 죽은 링크), 감시자 목록, 비교 화면, 데이터 내보내기 후 가져오기 왕복(프로젝트 수와 관리자 로그인 유지), 단체 메일(v1.6은 주소 목록만 받아 `mailto:`로 메일 프로그램을 연다), 조직 삭제, 커밋별 변경 화면, 보낸 요청 목록, 저장소 종류 변경 화면, 최근 푸시한 브랜치 안내(포크 화면에서 보이고 닫으면 기록이 지워진다. 원본 화면에서는 원본에 풀 리퀘스트를 만들 권한이 있는 사용자에게만 보인다)를 확인했다. 모두 정상이다.
- 네 가지 언어와 영어로 37개 화면을 훑어 번역되지 않은 키를 찾았다. 노출된 키는 `notification.type.issue.sharer.changed`(v1.6 번들에 영어, 일본어, 우즈베크어 값이 없다)와 설정 이름(`spring.mail.username` 등, 의도된 표시)뿐이다.
- 결함 1건(v1.6과 같아 재현, 기록): 러시아어 `project.you.are.watching`의 `{-}` 때문에 러시아어로 보면 프로젝트 화면마다 500이 난다(R-72). 고칠지 선생님 결정을 기다린다.
- 조직 구성원이 탈퇴 버튼을 볼 수 없는 것은 R-19(관리자가 한 명뿐이면 일반 구성원은 탈퇴 불가)로 이미 기록된 v1.6 동작이다.

## [2026-10-25] verify | 기존 v1.6 DB 이전과 이전 화면
- v1.6 evolutions로 만든 MariaDB(컨테이너 `yona-v16db`, 포트 35500)에 v2를 붙여 읽기, 쓰기, 디스크(저장소, 첨부), 이전 화면을 확인했다. 요약은 [architecture/differences-from-v16.md](architecture/differences-from-v16.md)의 "기존 v1.6 설치 위에서 쓰기"에 있다.
- 새로 기록한 것: A-104(중복 인덱스 2개, `webhook_type` 폭), R-58(7)(첨부 링크 `//files/번호`). 새 테스트: `EnumOrdinalParityTest`.
- 결정 대기: R-58(1)(토큰 교환이 폼만 해석)을 고치면 이전 화면이 실제 GitHub에서 동작한다. v1.6은 같은 요청 헤더로 JSON 응답을 받아 이 화면이 동작하지 않았을 가능성이 크다.


## [2026-10-26] fix | v1.6 불일치 점검 결과 1차 반영
- P-07(선생님): 외부 API 작성자 대체를 익명 사용자로, 권한 확인 순서는 v2 유지(R-73), 댓글 상태 전환은 부모를 열지 않도록 v1.6에 맞췄다. 테스트 추가: `ExternalIssueApiTest`, `IssueCommentTest`.

## [2026-10-26] fix | 화면에서 닿는 v1.6 불일치 반영 (P-08)
- 반영: 재설정 메일 실패 안내(U-24), 멘션 후보 순서(P-13, P-14), attachLabel category(P-27, R-63 정정), newWebhook 오류 화면(P-30), massUpdate 삭제 알림(I-09), referCommentId 파생 이슈 댓글(I-15, I-26), getFileList 403 Content-Type(B-17).
- 기록만: 직접 만든 요청 항목 20건은 [hand-crafted-request-differences.md](architecture/hand-crafted-request-differences.md), changeVCS 롤백(P-20)은 R-74.
- 이번에 다루지 않음: REST 쪽 항목(updateIssue* 부수 효과, issueWatchers 기본 구독자, findAssignableUsers, translate, newLabel isExclusive, accept/isMerging, editPullRequest, CompareApp, BranchApp 빈 저장소).

## [2026-10-26] fix | REST 쪽 v1.6 불일치 반영
- 반영: 이슈 구독자 기본값(담당자, 추천자), 외부 이슈 API 수정, 상태, 라벨의 save() 부수 효과(R-77), 비로그인 assignableUsers, 번역 응답 없음 "null"(R-75).
- 기록만: newLabel, translate 오류, editPullRequest, CompareApp, BranchApp은 hand-crafted-request-differences.md, accept 고착은 R-76.

## [2026-10-26] fix | 5차 검증 결과의 v2 대조와 반영
- 대조: 보안 13, 데이터 10, 풀 리퀘스트·알림 14, 기타 18개 항목을 v2 소스와 대조했다.
- 코드 반영: 프로젝트 삭제 시 이슈, 게시글, 댓글의 첨부와 알림 이벤트 삭제(R-18 정정), 풀 리퀘스트 재오픈 시 최근 푸시 브랜치 기록 삭제, 지속적 통합 웹훅의 빈 커밋 푸시 전송, 임시 업로드 정리 기준을 오늘 0시로(R-20 정정).
- 기록: R-78~R-85, 직접 만든 요청 항목 9건 추가, A-16 정정, R-55(4) 정정, project-delete.md 정정.
- 브라우저 확인: `yobi.project.New.js` TypeError가 v2 화면에서 재현됨(R-78).

## [2026-10-26] fix | R-74, R-76, R-82를 v1.6 상태로 (P-09)
- 선생님 결정에 따라 `changeVCS` 실패 시 중간 상태, `accept`의 병합 중 표시 고착, REST 프로젝트 생성의 SITEMANAGER 행 2개를 재현했다. A-76은 폐기했다.

## [2026-10-26] lint | 결함 후보 189건 문서화
- 5차 검증 보고서에 흩어져 있던 v1.6 결함 후보를 항목별로 다시 판정해 `architecture/v16-defect-catalog.md`에 모았다(★ 105건이 새 기록). R-86(첨부 MIME 감지), R-87(목록 포인터)을 올렸고 R-10 줄 인용을 정정했다.

## [2026-10-26] fix | v1.6 위키 9~11차 갱신분의 v2 대조와 반영
- 배경: 로컬 v1.6 위키를 GitHub(`search5/yona-v1.6-wiki`)의 최신으로 갱신했다(갱신 전 상태는 `wiki-backup-20261026-2235.tar.gz`). 5차 검증 전 백업과 현재 위키의 차이 127개 페이지를 에이전트 18개가 v1.6 소스를 직접 읽고 v2와 대조했고, 후보를 다른 에이전트 6개가 다시 확정했다.
- 코드 반영: 29줄([architecture/v16-sync-9-11.md](architecture/v16-sync-9-11.md)). 사용자 언어 요청별 갱신, 삭제된 사용자의 세션 무효화, 번호 카운터 보정, 내보내기 `authors`, 웹훅 `site_admin`, 마일스톤 `due_on`, 검색 마일스톤 정렬, 초안 게시와 이동, 일괄 마일스톤 없음, 조직 게시판 제목, 최근 방문 번호, 리뷰 메일 `References`, 시스템 메일 주소, 브랜치 단추 권한, 로그인 사용자의 403 알림, 커밋 이력 더 보기, 코드 리뷰 화면 3건, SVN 코드 화면, 내 이슈 하위 이슈, 빠른 검색, `L` 단축키, 라벨 선택, 가져오기 화면, 사이트 목록 아바타, 이슈 색인 상자, 서버 시작 시 풀 리퀘스트 정리.
- 기록: A-105~A-110, R-88~R-90, P-10(결정 대기 2건), hand-crafted-request-differences.md.
- 한계: JS 서술, 스키마 표, 내부 구현 서술은 대조하지 않았고 그룹마다 점검한 주장 수가 달라 놓친 불일치가 있을 수 있다. 판정하지 못한 항목은 같은 문서에 적었다.


## [2026-10-26] fix | 결정 대기 두 항목을 v1.6으로 반영
- 선생님이 두 결정을 모두 v1.6으로 정하셨다. Git/SVN Basic 인증은 `BasicAuthResolver`로 세션을 만들고 틀린 자격이면 쿠키 사용자로 되돌아가며, 가져오기 API는 시각을 생략하면 NULL로 저장한다. 근거는 P-10. 두 경우 모두 화면은 오류 없이 그려지는 것을 테스트로 확인했다.


## [2026-10-27] fix | 위키 갱신 후 v1.6 대조의 S2·S3 항목 반영
- 접근 로그(`AccessLogFilter`, `logback-spring.xml`), 엑셀 링크 재조립(`QueryLinks`), `X-Requested-With` 비교(`AjaxRequests`), 경로 끝 슬래시(`UrlPaths.encodeEach`), 사이트 주소 스킴(`SiteUrls.scheme`), 상대 시각(`MomentRelativeTime`), 최근 N일의 자정 기준, 닫힌 이슈의 마감 문구, 메일 diff의 `'`를 v1.6에 맞췄다.
- JGit diff 위치와 병합 판정은 `V16DiffAlgorithm`, `V16RecursiveMerger`로, 파일 종류 감지는 Tika 1.2 표와 `V16MimeDetector`로 맞췄다. 근거와 판단은 A-111~A-115, R-91~R-95. R-17의 8192는 65536으로 바로잡았고 포크 문서의 하드 링크 서술은 bare 복제로 고쳤다.

## [2026-10-27] fix | 127쪽 전수 줄 대조와 불일치 92줄 처리
- 장부 2,267줄에 모두 판정을 붙였다(누락 0). 불일치 92줄은 원인별로 모두 v1.6에 맞췄고, v1.6 위키 오류 36줄은 정정했으며, 직접 만든 요청 차이 51줄은 hand-crafted 문서에 보강했다. 결과는 [v16-sync-ledger.md](architecture/v16-sync-ledger.md).
- 결정 A-116~A-124, 결함 R-96~R-104, P-10 결정 8~10(정정 알림 1건, 확인 대기 2건)을 적었다. R-28의 전제는 Twirl 바이트코드 확인으로 바로잡았다.

## [2026-10-27] fix | 검증, 오류 응답, 메일, 부수 효과, 라이브러리 축 전수 대조의 불일치 처리
- 입력 검증 6개 조각, 오류 응답, 메일 본문, 부수 효과 6개 조각, JGit·SVNKit·jsoup·humanize 등 라이브러리 실행 비교에서 나온 불일치를 v1.6에 맞췄다. 한글 이름의 `Location` 헤더와 JSON 이동 주소 인코딩(A-139), 와일드카드 경로 이중 디코딩(A-140), `/code` 이동 303(A-138), SVN 한글 경로 WebDAV(A-137), 압축 파일 시각(A-141), jsoup 줄바꿈(A-142), 버전 파서와 Basic Base64(A-143, A-144), 공백 판정(A-145).
- 이슈를 `update()`로 저장하는 경로(추천, 가중치, 상태, 댓글, 라벨 삭제)의 댓글 수 재계산과 멘션 맞춤(R-111), 제목 머리말 순서(R-112, A-46 되돌림), 게시와 이동의 웹훅 두 번(R-113, A-105 되돌림), 외부 API 알림 보낸 사람, 가져오기 시 알림 이벤트 보존, 리뷰 댓글의 선택 첨부 이동, 게시판 첨부 누락 안내, 프로젝트 삭제 시 구독 기록, README 해제 저장, 사이트 관리자 행의 구성원 판정, 프로필 이메일 형식과 서버 캐시 갱신, `isEmailExist`의 JSON 본문 요구(R-110), 마일스톤 경고 우선순위, 조직 목록 와일드카드, 알림 링크(커밋 이력, 한글 인코딩)와 승인 알림, 보조 이메일 확인 메일의 서버 기본 언어.
- 실행으로 확정하지 못한 항목은 현재 판단을 유지하고 A-146, H-36~H-39에 적었다.

## [2026-10-27] decision | 추가 개발 항목 12개 확정 (D-28)
- `next` 브랜치에만 있는 OAuth2 Authorization Server, 2단계 인증, SSH 공개키, GPG 서명 검증, 개인용 API 토큰, MCP 서버, Mercurial, Git LFS, 감사 로그, OpenTelemetry와 병합 시 이슈 자동 닫기를 `roadmap.md`의 추가 개발 항목(X-1~X-11)으로 올렸다. 착수 순서는 미정이다.

## [2026-10-27] decision | 추가 개발 항목에 풀 리퀘스트 관련 4개 보충 (D-28)
- `next`에만 있는 풀 리퀘스트 기능을 X-12(리뷰 판정), X-13(브랜치 보호), X-14(담당자, 라벨), X-15(REST API)로 올렸다. 총 15개이다.

## [2026-10-27] decision | `next` 고유 기능 전수 조사 후 추가 개발 항목을 X-24까지 보충 (D-28)
- `next`의 P3 티켓 73건, 새 테이블 17개, v1.6에 없는 라우트, 의존성, README를 대조해 고유 기능을 모았다. X-16~X-24(SSO, 위키, 태그, REST API와 Swagger, 프런트엔드 현대화, 사용 현황 집계, 기본 브랜치 main, CLI, CI/Actions와 SPA 분리)를 추가하고 X-2, X-7, X-10의 범위를 보충했다.

## [2026-10-27] audit | v1.6 라우트 306개를 실제 스프링 매핑으로 다시 대조 (`RouteCoverageTest`)
- 소스에서 어노테이션 경로를 정규식으로 뽑으면 `{*path}`, 선택적 끝 슬래시, 필터가 받는 `/svn`, 클래스 단위 경로 때문에 누락이 크게 보인다(289 대 302). 정규식 대신 실제로 뜬 스프링의 핸들러 매핑에 v1.6 요청 306개를 하나씩 넣어 어떤 핸들러가 받는지 확인했다(`src/test/kotlin/dev/yona/web/RouteCoverageTest.kt`, 입력은 `src/test/resources/v16/routes.tsv`).
- 결과: 304개는 의도한 컨트롤러가 받는다(표를 눈으로 대조했고 `build/route-coverage.tsv`에 남는다). 매핑이 없는 2개는 `GET /*paths`(`GlobalErrorHandler`가 끝 슬래시를 떼어 이동)와 `POST /!svn-fake/sevice/`(v1.6도 404)이며 시험으로 고정했다. 정적 자원 핸들러는 모든 경로에 맞으므로 `/assets/`가 아니면 일치로 세지 않는다.
- 기존 `audit/RouteParityTest`는 같은 방식으로 `RequestMappingHandlerMapping`을 읽는다. 이번 시험은 요청을 실제로 넣어 `consumes`, `{*path}`까지 확인한다.

## [2026-10-27] verify | 실제 git·svn 클라이언트로 한글 경로, 큰 저장소, 인증 흐름 검증
- v2를 실제로 띄워 `git`, `svn`으로 확인했다([architecture/real-client-verification.md](architecture/real-client-verification.md)). 한글 프로젝트의 SVN 커밋 거부(A-148), 확장자에 따른 HTML 내용 형식 오류(A-150), 50MB 초과 Git 파일 500(A-149, R-115), 압축 파일 이름 헤더 소실(A-147)을 고쳤다.

## [2026-10-27] build | 공식 v1.16.0 실행 비교로 찾은 불일치를 고침
- 공식 릴리스를 띄워 같은 요청을 v2에 보내 비교하는 도구(`tools/v16-diff/`)를 만들고, 익명, 데이터 생성, 모든 사용자 화면 1200건, git 저장소 화면 366건, 마크다운, 폼과 권한 행렬 시나리오를 돌렸다([architecture/v16-release-differential-test.md](architecture/v16-release-differential-test.md)).
- 마크다운 변환기를 `marked.js` + Nashorn으로 바꾸고(A-154), jsoup, OWASP를 v1.6 버전에 맞췄다(A-155). 소스만 읽고 적었던 R-48, R-110, A-72, A-146의 설명을 정정했다.

## [2026-10-28] fix | 풀 리퀘스트 화면을 공식 v1.16.0과 대조해 고침
- `pr1` 시나리오(포크에서 원본으로 풀 리퀘스트 만들기, 보기, 댓글, 리뷰, 닫기, 병합)를 돌려 불일치를 줄였다. 받아들이기가 500이던 `reviewers` 지연 로딩, 병합 검사가 가끔 건너뛰어지던 커밋 전 시작, 댓글 뒤 이동 주소, 병합 결과 조각의 빈 속성, 익명 사용자의 "내 이름" 스크립트, 빈 댓글 오류 화면의 활성 메뉴, 받은 사람 아바타를 고쳤다(A-160).
- 남은 차이: 모델 식별자(포크 프로젝트 번호), 병합 커밋 해시(시각 포함), 홈 스트림 번호, 제목 없는 요청의 v1.6 500.
- 토큰 API(`api1`)를 대조해 익명 `defultLoginPage`(R-07 정정), 토큰만으로 요청할 때 "U" 단축키가 없는 점, 관리자 API 403의 Cache-Control을 맞췄다(A-161). 즐겨찾기 이슈 500(R-117)과 잘못된 입력의 500(R-118)을 기록했다.
- Subversion 화면(`svn1`)을 대조해 폴더 항목 순서, 끝 슬래시 이동, 폴더 안 링크 인코딩, 비교 화면 템플릿 주소, 복제 주소, `/svn` 응답 헤더를 고쳤다(A-162). 오래된 `BranchesTest` 기대값도 바로잡았다.
- 사이트 관리(`site1`)를 대조해 폼 오류와 대량 메일 대상의 해시 순서를 같게 했다(A-163).
- 정적 자원(`/assets`, `/favicon.ico`)을 공식 v1.16.0과 맞췄다. 캐시 헤더, ETag, 304, 404 본문, 확장자별 내용 형식, LESS 소스 맵과 `.less` 원본 제공(A-164).
- 알림 메일 비교 도구(`smtpsink.py`, `mailcompare.py`)를 만들어 두 서버가 보내는 메일을 짝지어 대조한다. 메일의 MIME 구조가 `mixed > alternative`가 되도록 `multipart/related` 겹을 없앴다. 이슈, 글, 댓글, 풀 리퀘스트 메일은 같아졌고 남은 차이는 v1.6의 번호 할당 순서(Ebean 시퀀스 블록)가 "이전 댓글"을 바꾸는 경우뿐이다. 시나리오가 라벨을 만들 때 폼 필드 이름(`categoryName`, `labelName`, `labelColor`)이 틀려 라벨이 한 번도 만들어지지 않았던 점도 바로잡아 데이터 상태를 다시 만들었다.

