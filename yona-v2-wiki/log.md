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
- 이름 바꾸기, 이전, 삭제(`life1`)를 대조해 커밋 이력 화면이 주소가 아니라 프로젝트의 실제 이름으로 링크를 만들도록 고쳤다. 이전 요청이 쿼리 `owner`를 읽는 점을 시나리오가 틀리게 보내고 있던 것도 바로잡았다. 남은 차이: JGit 버전 문자열(`agent=JGit/…`), v1.6의 프로젝트 이름 캐시가 새로 고침 전까지 중간 이름에 응답하는 일.

- 언어별 `/messages.js`를 공식 릴리스와 키와 값 단위로 대조하는 도구(`msgcompare.py`)로 일본어 `pullRequest.event.commit`(릴리스는 `commited`), 러시아어와 우즈베크어 메시지의 알 수 없는 역슬래시 이스케이프(Play는 역슬래시를 남긴다)를 맞췄다. 남은 한 키(`error.tooLargeText.admin`)는 설정 이름이 달라지는 의도한 차이이다.
- 웹훅 요청(`hook1`와 풀 리퀘스트, 푸시 웹훅)을 HTTP 수신기로 받아 대조해 `Content-Type`에 `; charset=utf-8`이 붙도록 맞췄다. 본문 33건은 같다. 남은 차이는 `Authorization: token <값> `의 끝 공백(JDK HttpClient가 지운다)뿐이다. Git과 Subversion 인증 행렬(`proto1`)로 `Authorization` 헤더를 v1.6처럼 방식 이름 없이 읽도록 고쳤다(R-119).
- 이슈 40여 개, 글 30여 개, 검색, 정렬, 쪽 나누기를 담은 `bulk1`로 대조해 공지 권한 검사, 글 목록 정렬 링크, 라벨 순서 세 곳을 고쳤다(A-165). v1.6(Ebean, H2)의 시퀀스 번호가 20개 묶음 단위로 뒤섞여 나오는 탓에 같은 값으로 정렬한 항목의 순서(`numOfComments` 동점 등)는 재현할 수 없다. 대조 도구의 사용자 번호 치환을 값이 번호 모양일 때로 좁혔다.


- 코드 리뷰 대조(`review1`: 범위 코멘트, 스레드 열기와 닫기, 목록 필터, 엑셀, 멘션)로 스레드 중복 렌더링, 풀 리퀘스트 변경 화면의 파일 단위 스레드 조각, 빈 커밋 코멘트 오류 메뉴를 고쳤다(A-166). 남은 차이는 v1.6 id 블록 순서에서 나오는 번호 정규화 차이, 엑셀 날짜 바이트, `reviews?state=` 잘못된 값의 500 대 400 정책(R-118)이다.

- 조직 소유 프로젝트와 한글 이름 프로젝트를 여러 역할로 대조(`org2`)해 이름이 인코딩되지 않고 나가는 곳을 템플릿 40여 곳과 코틀린 50여 곳에서 고쳤다(A-167). 한글 링크 점검 테스트(`KoreanNameLinksTest`), 개요가 없는 조직 프로젝트 항목의 검색 값(`null` 글자), 웹훅과 알림 메일의 한글 주소도 v1.6과 같다. 남은 차이는 잘못된 역할 번호 처리(R-120), 마일스톤 목록의 `labels.css` 위치(A-151)뿐이다.

- 첨부 파일 대조(`att1`: 올리기 30여 종, 받기, 조건부 요청, 목록, 이슈와 댓글과 글에 붙이기, 지우기)로 올리기 명의, 파일 이름, 날짜(0시), JSON 필드 순서, 내용 형식, 컨테이너 번호, 끝 슬래시, `OPTIONS`, 404의 `Cache-Control`을 고쳤다(A-168, A-31 철회). 남은 차이는 v1.6 번호 묶음 순서에서 나오는 번호, 내 파일 목록의 같은 날짜 정렬, 멀티파트가 아닌 요청의 500(R-121)이다.

- 모든 v1.6 라우트에 지원하지 않는 요청 방식과 HEAD, OPTIONS를 보내 대조하는 `methods1`(2800여 건)로 숫자가 아닌 경로 변수의 400, 404의 `Cache-Control`, HEAD의 끝 슬래시 이동을 고쳤다(A-169). 알림 순서는 규칙을 찾지 못해 이전 방식으로 두었다. 남은 차이 49건 중 32건은 `POST /:user`의 잘못된 입력(R-118), 나머지는 HEAD와 라우트 없는 방식의 알림 순서(R-122)이다.

- 실제 git과 svn 클라이언트 대조(`clients.py`, 53건)로 큰 푸시가 v2에서만 413이던 문제(A-120 철회), `Accept-Language: ko`가 영어로 풀리던 문제, svn 잠금이 400이던 문제를 고쳤다(A-170). 지금은 53건 모두 같다.

- 두 번째 실제 클라이언트 대조(`clients2.py`, 163건)로 svn switch가 클라이언트 단언 실패로 멈추던 위치 구간 경로를 고쳤다(A-171). 공개 범위 x 사용자 읽기와 쓰기 행렬은 모두 같다.

- 익명 요청 대조에서 남은 차이(사이드바 `hash`, 익명 즐겨찾기 이슈, 로그아웃과 `restricted`, 소셜 인증 머리글, 라벨 JSON 내용 형식, 익명 이름 언어)를 고쳤다(A-172). 이전에 "HEAD 차이"로 적은 것은 잘못으로 R-122를 정정했다.

- 메일 수신(IMAP) 대조와 조직 소유 한글 프로젝트의 풀 리퀘스트 대조를 했다. 사용자 화면의 포크 원본 링크 인코딩을 고쳤고(A-167 보강), 메일 수신의 남은 차이는 R-124에 기록했다.

- 권한·동작 차이(D) 네 가지를 v1.6대로 되돌렸다(A-173): 멘션 목록 권한 검사 제거, 프로젝트 조회 캐시, 구성원 역할 바꾸기 입력, 이슈 일괄 변경 입력. 해당 시나리오(forms1, org2, life1, pages1)에서 차이가 사라졌다.

- 남은 검증 축을 라우트 전수 요청(`coverage.py`), 정적 자원 전수(`assets.py`), 업데이트 확인, 최초 설치 흐름으로 넓혔다. 라우트는 306개 모두 한 번 이상 요청했고, 정적 자원은 755개의 본문이 모두 같다. 고친 것: `%2F` 주소(A-175), 커밋 코멘트 삭제의 머리글(A-176), 안내의 다음 요청 전달(A-177), 정적 자원 7개(A-178), 비밀 키 설정 흐름(A-179), 기본 설정값(A-174).

- 변칙 요청(`weird.py` 151건), 설정 24종(`cfg.sh`), LDAP(`ldap.sh`), 소셜 로그인(`social.sh`, 40건), 로그인 유지(`remember.py`), 풍부한 git·svn 저장소(`rich1`, `svnrich1`), 특수 문자(`specials1`)를 대조했다. 고친 것: 언어 한정(A-180), 변칙 요청 처리(A-181), 소셜 실패 응답(A-182), 검색 이스케이프(A-183), 코드 보기 이스케이프(A-184), 잠긴 로그인 이동 등(A-185). 남은 차이는 G-31 ~ G-37에 기록했다.

- 내보내기·가져오기 교차(`xport.sh`, `xport1.sh`)와 예약 작업(`sched.sh`)을 대조했다. v1.6의 내보내기 파일을 v2로 가져와도 화면이 대부분 같다. 릴리스 H2 번들의 기본 라벨 154개는 v2에 없다(G-32).

- 대조로 찾아 고친 불일치를 `decisions.md`와 이 로그에서 다시 세어 `v16-release-differential-test.md`에 집계했다(공식 v1.16.0 실행 대조 40건 결정, 번호를 붙인 수정 86건 이상). 앞서 건수를 추정으로 답했던 것을 기록으로 바로잡았다.

- 동등성 점검 단계를 종료했다(`v16-release-differential-test.md`의 "동등성 점검 종료 판정"). 이후는 회귀 점검만 한다. 다음 단계는 D-28 추가 개발 항목이며 D-29(Magpie 연동)의 선택이 필요하다.

## [2026-10-28] ingest | Apache Magpie 연동 분석
- Magpie 문서(tracker, change-request, pr-management 계약)를 읽고 Yona의 이슈·PR REST와 대조해 `architecture/magpie-integration.md`에 정리했다. D-29에 PR 쪽 현황을 덧붙였다.
- Magpie 연동을 선생님 지시로 구현 없이 보류했다(로드맵 X-23). 필요한 API가 X-5, X-12, X-15, X-19와 겹친다는 점을 `magpie-integration.md` 4절과 D-29에 덧붙였다.

## [2026-10-28] query | next와 next2의 v1.6 소스 근접도
- 라우트, 메시지, 정적 자원, 테이블을 v1.6과 소스 수준으로 대조해 `architecture/next-vs-next2-source-closeness.md`에 정리했다. 모든 항목에서 next2가 더 가깝다(라우트 95% 대 64%).

## [2026-10-28] implement | 로드맵 X-21 사용 현황 집계, X-22 기본 브랜치 main
- X-21: `next`의 GA4 `gtag` 조각을 가져왔다(설치 ID는 선생님 지시로 뺐다). 선생님 지시로 별도 스위치 없이 v1.6의 `yona.send-yona-usage`를 유지하고 UA 태그를 GA4로 교체했다(A-186). 측정 ID는 `yona.analytics.measurement-id`이다.
- X-22: `next` 기준으로 새 Git 저장소의 `HEAD`를 `yona.git-default-branch`(기본 `main`)로 고정했다(A-187). 설정 키는 `architecture/configuration-keys.md`에 적었다.
- 로드맵의 X-23 번호가 Magpie와 CLI에 겹쳐 있어 CLI를 X-24, CI/Actions를 X-25로 바로잡았다.
- X-21 후속: 2단 보기 측정을 유지하기로 하고, 토글 이벤트에도 화면 종류(`screen`)를 싣도록 했다. `next`의 화면 분류(`org`, `pulls`)와 예약어가 v1.6 경로와 달라 v1.6 `conf/routes` 기준으로 고쳤다. 스크립트 문법과 경로별 전송값은 node로 모의 실행해 확인했다(소유자·프로젝트 이름은 전송되지 않는다).
- X-21 후속: 2단 보기를 켠 채 목록 제목을 눌러 오른쪽 창에 여는 경우를 `two_column_open`으로 집계하도록 했다(문서 수준 클릭 위임, `yona.twoColumnMode.js`는 v1.6 그대로). node 모의 실행으로 켜짐일 때만 한 건 나가는 것을 확인했다.

## [2026-10-29] implement | 로드맵 X-11 풀 리퀘스트 병합 때 이슈 자동 닫기
- `PullRequestWorkflow.accept`가 병합을 마친 뒤 `PullRequestIssueCloser`가 제목, 본문, 커밋 메시지에서 `closes/fixes/resolves #번호`(활용형 포함)를 찾아 받는 프로젝트의 열림 이슈를 닫는다(A-188). 타임라인 이벤트와 알림은 수동으로 닫을 때와 같다.
- `next`의 정규식은 `fixes`, `fixed`를 알아보지 못하는 결함이 있어 바로잡았다. 충돌로 병합하지 못하면 이슈는 닫지 않는다.

## [2026-10-29] decision | D-30 Git 태그 기능(X-18) 설계 결정
- 현재 화면을 캡처해 브랜치 화면이 태그를 섞어 보여 주고 삭제, 열기는 이미 되며 메시지와 종류는 안 보임을 확인했다. 슬래시가 든 태그 이름도 확인했다. 선생님이 태그 전용 화면, 브랜치 화면에서 태그 제외, 선택 목록 구역 나누기(브랜치, 태그, 원격)를 포함한 여섯 가지를 정하셨다(D-30). 구현은 아직 하지 않았다.
- 이어서 정한 것: 브랜치 목록 함수가 `refs/heads`만 돌려주게 해 풀 리퀘스트 목록의 태그와 원격 참조도 함께 뺀다(2번). 푸시로 `refs/remotes/*`를 만들 수 있음을 v2와 v1.6에서 확인했고, 원격 참조는 서버 개념이 아니라 선택 목록의 원격 구역도 만들지 않는다. 그 밖의 참조 푸시 거부는 X-13 후보로만 기록한다(7번).
- 로드맵 X-7(Mercurial)의 선행 정리로 `@IsOnlyGitAvailable`과 VCS 분기를 "지원 VCS 선언" 방식으로 바꾸는 일을 기록했다. X-18은 D-30 기준으로 범위를 다시 썼다.

## [2026-10-29] implement | 로드맵 X-18 Git 태그 화면 (D-30)
- 브랜치 목록 함수가 `refs/heads`만 돌려주게 했다(브랜치 화면, 풀 리퀘스트 출발·도착 목록, 커밋이 아닌 대상을 가리키는 태그의 500 제거). 코드 화면과 커밋 이력 선택 목록을 "브랜치", "태그" 구역으로 나눴다(원격 참조 제외). 전용 태그 화면(`GET /:user/:project/tags`)과 삭제(`DELETE /:user/:project/tags/:tag`, 확인창)를 추가하고 코드, 커밋, 브랜치 화면 탭에 "태그"를 더했다. 새 메시지 키는 `title.tags`, `code.tags.*`(5개 언어). 위키는 `web/tags.md`에 적었다.
- MockMvc는 `%252F`를 실서버처럼 한 번 풀어 주지 않아 시험은 서버가 한 번 푼 형태(`%2F`)로 요청한다. 실서버(v1.6, v2)에서는 `%252F` 형태의 슬래시 태그 주소가 200임을 확인했다.

## [2026-10-29] decision | D-31 브랜치 삭제 확인창
- 선생님 지시로 브랜치 화면의 삭제 단추에 태그 화면과 같은 확인창(`data-confirm`, 캡처 단계 `confirm()`)을 더했다. 새 메시지 키 `code.branches.deleteConfirm`(5개 언어)은 `MessageParityTest`의 추가 키 목록에 올렸다. 기존 화면의 확인 모달 방식(정적 모달, 모달 재사용, `$yobi.ajaxConfirm`)은 확인만 하고 통일하지 않았다.
- D-30 용어: `주석 태그`, `경량 태그` 표현을 유지하기로 했다(선생님). 주석 태그의 커밋 이력 500(P3-40)은 v2와 v1.6 모두 재현되지 않음을 실측했다. 커밋이 아닌 태그는 R-128로 기록했고 태그 화면은 그 이름을 링크 없이 보인다.

## [2026-10-29] decision | D-32 회귀 실행 방침과 의도된 차이 장부
- 선생님이 동등성 대조 도구(`regress.sh`)는 요청할 때만 돌리고, 추가 개발 항목의 차이는 장부로 관리하라고 정하셨다(D-32). 전체 회귀를 시작했다가 첫 시나리오(`anon_basic` 2 -> 36)에서 중단했고 보고서는 보관본으로 복구했다.
- `architecture/differences-from-v16.md`에 "추가 개발 항목으로 생긴 의도된 차이 (장부)" 표를 만들고 X-21(GA4), X-22(기본 브랜치), X-11(이슈 자동 닫기), X-18(태그 화면), 브랜치 삭제 확인창을 등록했다.

## [2026-10-29] cleanup | D-33 정리 항목 정리
- 선생님 지시로 마크다운 비교용 잔재(`MarkedReference`, `MarkedReferenceTest`, `MarkdownDifferenceTest`, commonmark와 GraalJS 의존성)를 삭제했다. `FaviconController`는 중복이 아니라 남겼다. `yona-v1.16-release` 폴더는 제외했다.
- 확인창 방식(브라우저 기본 `confirm()`) 유지(D-31), 로드맵 X-13 후보(밖의 참조 푸시 거부)는 보류(D-30 7번)로 기록했다.
- D-33 후속: "도우미 폴더"는 선생님도 모르신다고 하셔서 조사 없이 보류로 기록했다(삭제하지 않음).

## [2026-10-29] decision | D-34 풀 리퀘스트의 담당자와 라벨(X-14) 설계
- 담당자의 뜻을 GitHub과 비교해 정리하고(공식 문서: 최대 10명, 지정 대상), 1명에서 여러 명으로 키울 수 있게 처음부터 연결 테이블로 두기로 했다. 선생님이 스키마 시험 허용 목록, 내보내기와 가져오기에 새 테이블 포함, 삭제 연쇄를 정하셨다. 나머지는 권장안으로 기록하고 확인을 기다린다. 구현은 아직 하지 않았다.
- D-34 보강: 선생님이 새 담당자 알림, 화면 배치 X(나란히), 풀 리퀘스트 목록 필터(담당자, 라벨), 라벨은 받는 프로젝트의 것만을 정하셨다. "조직 구성원은 지정만 되고 지정하지는 못한다"는 제 설명이 확인하지 않은 추측이고 틀렸음을 v1.6, v2 권한 코드로 확인해 정정했다. 구현은 아직 하지 않았다.
- D-34 보강: 이슈 담당자 후보 규칙(`findUsersByProjectAndOrganization`)을 확인해 11번에 풀어 적었다. 라벨 변경은 v1.6 이슈도 `receivers = null`이고 웹훅이 없어 타임라인만으로 확정했고(12), 담당자 변경은 웹훅을 보내기로 했다(13). 제 앞선 설명 하나(라벨 변경 알림)를 정정했다. 구현은 아직 하지 않았다.

## [2026-10-29] implement | 로드맵 X-14 풀 리퀘스트의 담당자와 라벨 (D-34)
- 연결 테이블 2개(`pull_request_assignee`, `pull_request_issue_label`)로 담당자(1명까지, 한도 상수)와 라벨을 저장하고, 서비스, 타임라인 이벤트 2종, 담당자 변경의 알림과 웹훅(라벨 변경은 타임라인만), 보기 화면의 선택과 요청 2개, 목록의 칩과 아바타와 필터를 구현했다. 삭제 연쇄(풀 리퀘스트, 프로젝트, 사용자, 라벨), 내보내기와 가져오기 49개 테이블, 스키마 시험의 의도한 추가 목록을 반영했다.
- 구현 중 정한 세부 판단은 A-189~A-194에 적었다. 기록은 `web/pull-request-assignee-label.md`, 의도된 차이 장부는 `architecture/differences-from-v16.md`이다.

## [2026-10-29] decision | D-34 후속 14~16 (구현 후 검토)
- 선생님이 구현 결과를 보고 담당자와 라벨 선택을 이슈와 같은 Ajax 방식으로 바꾸고(14), 조직 목록, 대시보드, 사용자 화면의 풀 리퀘스트 줄에도 라벨과 담당자를 보이며(15), 선택 부분의 모양을 다듬으라고 하셨다(16, 방식 미정). A-190, A-193을 처음 판단으로 표시하고 A-192는 GitHub 문서에 제한이 없어 유지로 적었다. 구현은 하지 않았고 문서만 고쳤다.
- D-34 검토(17, 18): 밝은 라벨의 타임라인 글자색 결함 확인, 메일과 알림 설정과 권한과 좁은 화면 확인, 목록 쿼리 수 측정(94, 109건, 묶어 읽기 시 66, 81건). 16번은 이슈와 같은 2단(A)으로 확정, 범위 밖 항목은 REST 외 버림. 조직 이슈 목록은 칩과 아바타와 빠른 필터만 있고 고급 필터가 없음을 확인해 15번에 적었다. 문서만 수정했다.
- D-34 보강: 선생님 "깃허브 기준으로" 지시에 따라 GitHub 공식 문서(필터 문서, 풀 리퀘스트 만들기 문서)를 확인해 15번(필터는 프로젝트 목록만, 나머지 목록은 표시만), 15-1(내게 할당됨, 담당자 없음 선택지), 18번(생성 폼 지정은 버림 유지), 19번(`@BatchSize`)을 정리했다. 문서만 수정했다.

## [2026-10-29] build | X-14 후속(D-34 14~17, 15-1, 19, 20) 구현
풀 리퀘스트 담당자와 라벨 선택을 이슈 화면과 같은 Ajax와 2단 구성으로 바꾸고, 조직 목록, 대시보드, 사용자 탭에 라벨 칩과 담당자 아바타를 더했다. 담당자 필터에 "내게 할당됨", "담당자 없음"을 더하고 생성 폼에서도 지정하게 했다. `@BatchSize(50)`을 적용했다.

## [2026-10-29] query | X-12 리뷰 판정 설계 확정(D-35)
GitHub 공식 문서와 v1.6 동작을 대조해 D-35를 확정했다(작성자 승인 금지, 관리자 우회, 기존 데이터 보정과 영향 통지, 새 테이블과 내보내기). 검토 중 v1.6이 새 커밋에 리뷰어를 이미 비우는 것을 찾아 5번을 바로잡았다. 상세는 web/pull-request-review.md.

## [2026-10-29] ingest | 로드맵 X-26, X-27 추가
선생님 지시로 줄 단위 댓글 묶음과 임시 저장 리뷰(X-26), 리뷰 요청과 다시 요청(X-27)을 추가 개발 항목에 올렸다. 둘 다 X-12(D-35) 이후이다.

## [2026-10-29] build | X-12 풀 리퀘스트 리뷰 판정 구현(D-35)
저장, 보정, 서비스 규칙, 웹 계약과 화면, 프로젝트 설정, 알림과 웹훅을 TDD로 구현했다. 구현 중 정한 세부 판단은 A-195~A-202에 적었고 댓글 판정의 승인 거둠(A-195)은 선생님 확인이 필요하다.

## [2026-10-29] query | X-5 개인용 API 토큰 설계 제안(D-36)
선생님 방향(모든 토큰이 권한 확인, D-20 대체, Classic 설계 필요)에 맞춰 Classic 범위 토큰, 해시 저장, 레거시 토큰 이관, 인증과 권한 규칙, 화면을 설계안으로 정리했다. 구현 전이고 결정 필요 5건이 남았다.

## [2026-10-29] query | X-5 범위, 만료, 로그인 발급 재조사
GitHub 문서로 Classic 범위(굵은 묶음), 만료(권장, 기본 30일, 1년 미사용 삭제, 50개)를 확인하고 로그인 발급 API는 화면이 쓰지 않으며 GitHub이 없앤 기능임을 확인했다. D-36과 api-token.md를 갱신했다.

## [2026-10-29] query | X-5 로그인 토큰 재확인과 범위 대응표
로그인 발급 API의 정확한 동작(세션 로그인, 토큰 덮어쓰기, 호출처 없음), GitHub의 비밀번호 API 인증 제거 시점(2020-11-13, Git은 2021-08-13)을 확인했고, 이전 화면이 Yona 토큰을 쓴다는 앞선 서술을 바로잡았다. GitHub 범위를 Yona 자원, 연산, 공개 범위와 지금 있는 API에 대응시킨 표를 api-token.md에 더했다.

## [2026-10-29] query | X-5 로그인 토큰의 이상한 점
선생님 지적에 따라 로그인 발급 API를 다시 보고 토큰이 밀리초 시각의 SHA-256이라 추측 가능한 점, 응답 문구로 사용자 존재가 드러나는 점 등을 api-token.md에 기록했다. 처리 방침은 확인 대기.

## [2026-10-29] query | X-5 토큰의 Git, SVN 사용과 옛 토큰 이관 방침
선생님 방침에 따라 Classic 토큰을 Git, SVN HTTP 비밀번호 대신과 OpenAPI 인증 양쪽에 쓰고, 이관한 옛 토큰은 현재 그대로(API 전용), 새로 만드는 토큰은 임의 값으로 안전하게 만들며, 옛 URL은 호환용으로 남기고 나중에 제거하기로 설계를 갱신했다.

## [2026-10-29] query | X-5 구현 전 면밀 검토
비밀번호 인증을 (나)로 확정하고 인증 필터, 계정 서비스, 보안 설정을 다시 읽어 구현 전 점검 목록 15건과 정정(v2 설정 화면은 이미 임의 값 토큰을 만든다, 느슨한 헤더 분석, 잠긴 사용자의 토큰 인증, 프록시 뒤 실패 제한의 주소 판별)을 api-token.md에 적었다.

## [2026-10-29] query | X-5 토큰 허용 경로 명단 방침 확정
토큰이 모든 경로에서 인정되던 v1.6 동작을 명단 밖 거부(401)로 바꾸기로 확정하고 api-token.md, D-36, 의도된 차이 장부에 적었다.

## [2026-10-29] build | X-5 1단계 구현과 세션, 토큰 경로 분리
토큰 저장, 이관, 내보내기와 가져오기, 스키마를 구현했다. 구현 중 v1.6 사용자 내보내기에 token 열이 없음을 확인해 문서를 바로잡았다. 선생님 지시로 세션과 토큰을 둘 다 받는 경로를 없애고, /-_-api 접두사에 세션 전용 웹 AJAX가 섞여 있음을 확인해 허용 목록을 엔드포인트 단위로 정했다.

## [2026-10-29] query | 세션 경로와 API 경로 분리 설계
선생님 지시로 세션, 토큰 둘 다 받던 경로를 기존 주소 세션 전용 + /api/v1 토큰 전용으로 나누는 설계를 architecture/api-auth-split.md에 정리했다. v1.6과 달라지는 차이 목록과 결정 필요 3건을 적었다.

## [2026-10-29] query | 세션, API 이름 공간 확정
선생님 지시로 /-_-api/v1은 토큰 전용, 세션을 처리하던 24개 웹 AJAX와 세션 사본은 /-_-ajax/v1로 옮기고 /api/v1은 나중에 별칭으로 걸기로 설계를 바꿨다. 이전 /api/v1 안은 철회했다.

## [2026-10-29] query | 세션 AJAX는 일반 웹 경로 이름으로, 이중 경로는 토큰 전용으로
선생님 지시로 웹 화면 AJAX 24개를 일반 웹 경로 이름으로 옮기고, 세션과 토큰을 둘 다 받던 16개는 웹 화면이 부르지 않았으므로 세션 주소 없이 토큰 전용으로 두기로 했다. /-_-ajax/v1 안은 철회했다.

## [2026-10-29] build | X-5 개인용 API 토큰 구현
저장소와 이관, 네임스페이스 인증, 범위 표, 실패 차단, 웹 AJAX 이동, Git과 SVN의 토큰 비밀번호 인증, 설정의 토큰 화면(목록, 만들기, 달력 만료, 한 번만 보이는 값, 삭제)을 구현했다. 판단은 decisions.md A-206부터 A-211에 있다.

## [2026-10-29] build | X-5 토큰 화면 개선
목록, 만들기, 편집 화면으로 나누고 GitHub처럼 카드 형태로 다시 만들었다. 편집(이름, 범위)과 다시 만들기(새 값, 만료)를 추가했다(A-212).

## [2026-10-29] build | X-27 풀 리퀘스트 리뷰 요청 구현
연결 테이블, 서비스(요청, 취소, 다시 요청, 후보 검색), 알림과 웹훅, 개요의 리뷰어 칸, 목록 필터를 구현했다. 결정은 D-37, 세부 판단은 A-213부터 A-219이다.

## [2026-10-29] query | X-26 임시 저장 리뷰의 저장 방식
임시 댓글을 같은 테이블에 두고 기본으로 숨기는 (A)안으로 정했다. 동시 제출이 수천에서 수만 명 규모라 새 테이블에 복사하는 (B)안은 쓰기가 늘어나기 때문이다(D-38).

## [2026-10-29] build | X-26 임시 저장 리뷰 구현
임시 줄 댓글을 같은 테이블에 두고 기본으로 숨기는 방식으로 구현했다. 달기, 본인에게만 보이기, 지우기와 버리기, 리뷰 제출로 한꺼번에 공개하기, 알림 하나로 묶기를 포함한다. 결정은 D-38, 세부 판단은 A-220부터 A-226이다.

## [2026-10-29] build | X-26 후속: 리뷰 댓글 고치기와 파일 단위 임시 저장
선생님 지시(세 가지 모두 GitHub 기준)로 리뷰 댓글 고치기(제출 전후, 수정됨 표시)와 파일 단위 댓글의 임시 저장을 더하고, 임시 스레드는 열린 스레드 수에 세지 않기로 확인했다(D-38 7~9, A-227~A-229).

## [2026-10-29] fix | X-26 후속 정정
선생님 지적으로 변경 화면 아래 "변경 전체 댓글" 입력란의 임시 저장 단추를 되돌렸다(GitHub의 파일 댓글이 아니다). 고치기용 원문 입력란이 화면에 보이던 것을 숨겼다(D-38 9번 정정).

## [2026-10-29] query | X-15 풀 리퀘스트 REST의 방향과 병합 방식(X-28)
경로는 /-_-api/v1, 응답은 Yona 자체 모양, 병합 방식은 별도 항목 X-28(GitHub 세 가지, 프로젝트 설정)으로 올리고 REST에는 method 필드를 예약하기로 했다(D-39).

## [2026-10-29] build | X-15 풀 리퀘스트 REST API 구현
조회, 쓰기, 리뷰와 임시 댓글을 /-_-api/v1 토큰 전용 경로에 Yona 자체 JSON 모양으로 구현했다. 병합 방식 필드는 예약했다(D-39, A-230부터 A-233).

## [2026-10-29] build | X-15 후속: 풀 리퀘스트 만들기와 고치기, 첨부
선생님 확인으로 풀 리퀘스트 만들기(POST)와 고치기(PATCH)와 첨부(temporaryUploadFiles)를 REST에 더했다(A-234, A-235).

## [2026-10-29] build | X-15 후속: 리뷰어와 담당자 후보 조회
외부 도구가 로그인 ID를 알 수 있도록 풀 리퀘스트 단위의 후보 조회 두 가지를 더했다(A-236).

## [2026-10-29] query | X-19 일반 REST를 세 차례로 나눔
1차는 빠진 조회 API로 하고 이슈 쓰기와 게시판은 X-29, 조직과 검색과 웹훅은 X-30, OpenAPI는 X-31로 로드맵에 올렸다(D-40).

## [2026-10-29] build | X-19 일반 REST 1차 구현
프로젝트, 구성원, 담당자 후보, 라벨, 마일스톤, 이슈 목록과 검색 조회를 구현했다(D-40, A-237, A-238).

## [2026-10-29] query | X-28 병합 방식 설계 확정
GitHub의 세 가지 방식을 프로젝트 설정으로 허용하고 방식별 가능 여부를 병합 전에 보여 주기로 했다(D-41).

## [2026-10-29] build | X-28 풀 리퀘스트 병합 방식 구현
스쿼시와 리베이스 병합, 프로젝트 설정, 방식별 가능 여부(rebase_blocked), 화면 선택과 REST 확장을 구현했다(D-41, A-239부터 A-243).

## [2026-10-29] build | X-32 브랜치 보호 규칙 구현
리뷰어 수 강제를 빼고 브랜치별 보호 규칙으로 바꿨다(D-42, A-245부터 A-248).

## [2026-10-30] build | X-32 프로젝트 전체 기본 리뷰어 수 복원과 브랜치 보호 화면 개선
리뷰어 수 설정을 보호 규칙이 없는 브랜치의 기본 규칙으로 되살리고, 보호 규칙을 표 형태 카드로 바꿨다(D-43, A-249).

## [2026-10-30] build | X-32 브랜치별 규칙을 설정 폼의 리뷰어 영역으로 합침
별도 카드를 없애고 규칙 줄을 설정 폼 입력칸으로 바꿔 저장 단추 하나로 저장한다(D-44, A-250).

## [2026-10-30] build | X-33 GitHub 방식 브랜치 보호로 이행
병합 조건, 리뷰어 설정 이전, 규칙 편집 화면, 푸시 검사와 삭제 제한을 구현하고 리뷰어 사용과 리뷰어 수를 무력화했다(D-45, A-251, A-252).

## [2026-10-30] build | X-34 규칙 묶음 구조로 전환
브랜치 보호를 GitHub의 규칙 묶음(이름, 적용 상태, 바이패스, 대상, 규칙, 합산, 목록과 편집 화면)으로 바꾸고 ◐ 항목의 완성은 X-35로 미뤘다(D-46, A-253부터 A-255).

## [2026-10-30] query | X-29 범위 확정과 추적표
이슈와 게시판 쓰기 REST를 네 단계(a 이슈 수정, b 게시판, c 라벨과 마일스톤, d 이슈 고유 기능)로 나누고 엔드포인트 추적표를 만들었다(D-47).

## [2026-10-30] build | X-29a 이슈 쓰기 REST 1단계
이슈 부분 수정, 삭제, 댓글 목록과 수정과 삭제, 라벨, 담당자 REST를 구현하고 외부 이슈 API의 권한 확인 누락을 D-36 4번대로 보정했다(D-47, A-261).

## [2026-10-30] build | X-36 삭제 권한 제한
이슈, 게시글, 댓글의 삭제를 작성자와 관리자만 하도록 `AccessControl`에서 한 번에 바꾸고(웹, REST, 일괄 삭제, 화면 단추), 수정은 GitHub처럼 구성원에게 유지했다(D-48, A-262).

## [2026-10-30] build | X-29b 게시판 REST
게시글 목록과 한 건, 부분 수정, 삭제, 댓글 목록과 수정과 삭제, 라벨 더하기와 떼기를 토큰 전용 REST로 구현했다. 삭제는 작성자와 관리자만, 수정은 구성원도 한다(D-47, D-48, A-264).

## [2026-10-30] build | X-29c 라벨과 마일스톤 관리 REST
라벨과 마일스톤의 고치기와 지우기를 토큰 전용 REST로 구현했다. 테스트를 먼저 써 6개 실패를 확인한 뒤 구현했다(D-47, A-265).

## [2026-10-30] build | X-29d 이슈의 Yona 고유 쓰기 REST
공유자(조회, 더하기, 빼기), 가중치, 지켜보기와 해제를 토큰 전용 REST로 구현했다. 테스트를 먼저 써 5개 실패를 확인한 뒤 구현했고 X-29 상위 항목이 모두 끝났다(D-47, A-266).

## [2026-10-30] query | X-30 범위 확정과 추적표
조직, 웹훅, 검색, 포크를 네 단계로 나누고 쓰기까지 열며 GitHub 전용 범위를 쓰기로 했다. 사용자 상태는 이미 있어 뺐다(D-49, A-267).

## [2026-10-30] build | X-30a 조직 REST
내 조직과 조직 한 건, 구성원 목록과 더하기와 역할 변경과 빼기, 조직 만들기와 설정과 삭제를 `read:org`, `write:org`, `admin:org` 범위의 토큰 REST로 구현했다. 테스트를 먼저 써 8개 실패를 확인한 뒤 구현했다(D-49, A-268).

## [2026-10-30] build | X-30b 웹훅 REST
프로젝트 웹훅의 목록, 만들기, 삭제를 `read:repo_hook`, `write:repo_hook`, `admin:repo_hook` 범위의 토큰 REST로 구현했다. 테스트를 먼저 써 5개 실패를 확인한 뒤 구현했다(D-49, A-269).

## [2026-10-30] build | X-30c 검색 REST
사이트 전체, 조직, 프로젝트 범위의 검색을 토큰 REST로 구현했다. 테스트를 먼저 써 6개 실패를 확인한 뒤(구현 파일을 치워 확인) 구현했다(D-49, A-270).

## [2026-10-30] build | X-30d 포크 REST
`POST .../projects/{projectName}/forks`로 사용자나 조직을 대상으로 포크를 만든다. 테스트를 먼저 써 5개 실패를 확인한 뒤 구현했고 X-30 상위 항목이 모두 끝났다(D-49, A-271).

## [2026-10-30] query | 로드맵 갱신
X-30(조직, 웹훅, 검색, 포크 REST) 완료와 검증(H2 2964개, PostgreSQL 24개)을 반영하고, X-31을 착수 가능으로, 오래된 X-13(브랜치 보호)을 규칙 묶음(X-32부터 X-34)이 대체했음을 표시했다.

## [2026-10-30] build | X-31 OpenAPI 문서
`/-_-api/v1` 100개 동작을 springdoc으로 문서화하고(화면 `/-_-api/docs`) 구조 시험과 모든 REST 시험에 거는 계약 시험을 더했다. 계약 시험이 문서 오류를 여럿 찾았고, springdoc이 끌어온 Jackson 2가 실제 서버의 화면을 끊는 회귀(타임리프 인라인 직렬화가 응답을 비워 커밋)를 일으켜 고쳤다(D-50, A-272, A-273).

## [2026-10-30] query | yona CLI(X-24) 현황 확인
`../yona-cli`에 약 9,300줄이 이미 있고 이전 구현의 `/api/v1`을 대상으로 한다. 클라이언트를 `/-_-api/v1`에 맞추기로 했다(D-51).

## [2026-10-30] query | CLI를 위한 서버 보강 계획
D-51의 대응표에서 서버에 REST가 없던 CLI 명령을 서버에 만들고, 기기 인증(Device Flow)과 Git 자격 증명 연동을 더하기로 했다. 단계를 X-37a~k로 나눴다(D-52).

## [2026-10-30] build | X-37a 프로젝트 만들기, 설정, 삭제 REST
일반 사용자가 토큰으로 프로젝트를 만들고(`POST /user/projects`, `POST /orgs/{org}/projects`), 관리자가 설정을 바꾸고(`PATCH`) 지우는(`DELETE`) REST를 구현했다. 테스트를 먼저 써 9개 실패를 확인한 뒤 구현했다(D-52, A-274).

## [2026-10-30] build | X-37b 프로젝트 구성원 REST
구성원 더하기, 역할 바꾸기, 빼기(본인이면 나가기)를 토큰 REST로 구현했다. 테스트를 먼저 써 5개 실패를 확인한 뒤 구현했다(D-52, A-275).

## [2026-10-30] build | X-37c Git 태그 REST
태그 목록, 만들기(경량과 주석), 지우기를 토큰 REST로 구현했다. 테스트를 먼저 써 6개 실패를 확인한 뒤 구현했다(D-52, A-276).

## [2026-10-30] build | X-37d 이슈 옮기기 REST
`POST .../issues/{n}/transfer`로 이슈를 다른 프로젝트로 옮긴다. 테스트를 먼저 써 3개 실패를 확인한 뒤 구현했다(D-52, A-277).

## [2026-10-30] build | X-37e 사용자 현황 REST
`GET /user/status`(담당 이슈와 풀 리퀘스트, 리뷰 요청, 멘션, 최근 알림)와 내 이슈의 `commented`, `shared` 필터를 구현했다. 테스트를 먼저 써 3개 실패를 확인한 뒤 구현했다(D-52, A-278).

## [2026-10-30] build | X-37f 풀 리퀘스트 검색 REST
사이트 전체 풀 리퀘스트 검색(`GET /search/pullrequests`)과 프로젝트 목록 조건(`q`, `author`, `assignee`, `reviewRequested`, `labelIds`)을 구현했다. 테스트를 먼저 써 3개 실패를 확인한 뒤 구현했다(D-52, A-279).

## [2026-10-30] build | X-37g 사이트 백업 REST
`GET /admin/export`와 `POST /admin/import`를 구현했다. 테스트를 먼저 써 4개 실패를 확인한 뒤 구현했고, 토큰 인증과 비동기 스트림의 충돌을 시험으로 발견해 동기 쓰기로 바꿨다(D-52, A-280).

## [2026-10-30] build | X-37i 기기 인증(Device Flow)
`device_authorization` 테이블, 코드 발급과 토큰 교환 REST, 웹 승인 화면(`/users/device`)을 구현했다. 시험(REST 9개, 화면 7개)을 먼저 쓰고 컴파일 실패와 404, 403으로 Red를 확인한 뒤 구현했다(D-52, A-281).

## [2026-10-30] verify | X-37 서버 단계와 기기 인증의 종단 검증
- 전체 H2 시험 3019개 중 스키마 시험 4개가 새 테이블 `device_authorization`을 의도한 추가 목록에 올리지 않아 실패했고, 목록과 컬럼 시험을 더해 고쳤다. X-37a~i의 시험 69개는 PostgreSQL에서도 통과했다.
- 실제 서버(부트 jar, H2 파일 DB, 격리한 포트)에서 종단 시험을 했다: CLI `auth login`(기기 인증) → Playwright로 승인 화면에서 승인 → 토큰이 `~/.config`에 로그인 ID와 함께 저장 → Git 전역 설정에 자격 증명 도우미 등록 → `POST /user/projects`로 비공개 저장소 만들기 → 토큰만으로 `git clone`과 `git push` 성공(익명 접근은 401). 승인 화면의 캡처(코드 입력, 확인)를 확인했다.

## [2026-10-30] build | X-37j CLI 전환 완료와 실서버 종단 시험에서 찾은 결함 둘
- `../yona-cli`의 모든 클라이언트와 명령을 `/-_-api/v1`에 맞췄다(커밋 4b4a601~9bd8ba0). `pr reviewer`는 GitHub 방식 `pr edit --add-reviewer/--remove-reviewer`로 바꿨다(서버 변경 없음, D-52). 위키 명령은 서버에 위키가 없어 옛 경로를 그대로 두었다(X-17 이후).
- 실서버(부트 jar)에서 CLI 명령을 돌려 서버 결함 둘을 찾아 고쳤다. (1) `POST .../merge`가 병합은 하고 응답 조립에서 지연 로딩 예외로 500: 병합 메서드에 트랜잭션이 없어서였고(R-76) 시험 트랜잭션이 가렸다. 읽기 전용 트랜잭션에서 응답을 만들고, 시험은 데이터를 확정해 트랜잭션 없이 호출한다. (2) 문서는 404를 JSON으로 약속하는데 X-37의 프로젝트 수정, 삭제, 구성원, 이슈 옮기기가 웹 오류 화면(HTML)을 돌려줬다. JSON `{"message"}`로 고쳤다.

## [2026-10-30] decision | D-53 Bootstrap 2에서 5로 전면 전환
선생님이 Bootstrap 5 전환을 요청하셨다. 직접 전환(권장했던 안)과 단계별 상향(2→3→4→5)을 비교했고, 안내서가 있는 만큼 안정적이라는 선생님의 지적을 받아들여 단계별로 정했다. 핵심 화면만 확인하면 놓친다는 지적에 따라 0단계로 전 화면 확인 도구를 먼저 만든다. 로드맵 X-38a~f(D-53).

## [2026-10-30] verify | X-37 전체 H2 시험
전체 H2 시험 3026개가 모두 통과했다(실패 0). 기준 태그 `pre-bs5`를 이 상태에 남긴다(D-53).

## [2026-10-30] build | X-38a 전 화면 확인 도구
구조 검사 시험(낡은 표기 20종의 기준 대조), 고정 데이터 심기, 화면 목록 121개(역할별), 전 화면 순회 캡처와 상호작용 점검, 전후 비교 도구를 만들었다. 기준 순회 `pre-bs5`는 121/121 통과이다(알려진 v1.6 JS 오류 1건은 `compare` 화면의 `reading 'top'`). 같은 조건으로 두 번 돌려 결과가 같음을 확인했고(113/121 두 번, 새 문제 0), 판정 기준을 고쳐(팝오버는 data-trigger에 맞춰 올려 두기, 툴팁은 재시도) 8건이 사라졌다. 기준 캡처 외의 시험 순회(try*)는 지웠다(D-53).

## [2026-10-30] build | X-38b Bootstrap 2.3.1에서 3.4.1로
공식 이전 안내서를 기준으로 템플릿의 그리드(`span*`, `row-fluid`, `offset*`), 모달 구조(`.modal-dialog`/`.modal-content`, `.hide` 제거), 알림, 라벨, 진행 막대, 폼, 목록, 표 이름을 바꾸고 BS3 CSS와 JS를 들였다. JS 번들 재생성 스크립트(`build-tools/minify-js.sh`)가 기존 `yona-layout.js`와 `yona-lib.js`를 바이트 단위로 똑같이 만드는 것을 확인한 뒤 `yona-common.js`에 BS3 JS와 BS2 typeahead(A-284)를 넣었다.
마크업만으로는 BS3의 기본 스타일 차이(박스 모델, 제목, 내비게이션, 표, 그리드 간격)로 화면이 달라져 호환 층(A-285)을 만들었고, 두 빌드의 계산된 스타일을 대조하는 도구(`style-diff`)로 원인을 하나씩 찾아 고쳤다(요소 위치가 바뀐 비율 34%에서 5% 아래로). 새 버전 알림 띠가 순회마다 28px씩 달라지는 비결정 요인을 찾아 서버를 `--yona.update.interval=0`으로 띄우게 했다(A-286). v1.6 구조를 확인하는 화면 시험 약 20개의 선택자를 새 클래스 이름에 맞췄다.
결과: 전 화면 순회 121/121, 상호작용 점검(모달, 탭, 드롭다운, 팝오버) 기준과 같음, 기준 대비 새 문제 0, 캡처 평균 차이 0.7%.
전체 H2 시험 3027개 통과(실패 0). 태그 `bs3`을 남긴다.

## [2026-10-30] build | X-38c 1구간: 자체 스타일 LESS에서 SCSS로
변환 스크립트로 LESS 21개 파일(약 1만 6천 줄)을 SCSS로 옮기고, LESS 컴파일 결과와 SCSS 컴파일 결과를 규칙 단위로 대조해 같음을 확인했다(A-287). 빌드는 dart-sass로 바꾸고 `build-tools/less`를 지웠다(쓰이지 않는 LESS 도구, 이력에 남아 있다). 스타일 소스 주소는 `scss/font`로 바뀌었다(A-288). 전 화면 순회 121/121, BS3 빌드와 픽셀 차이 0. 체크포인트 태그 `scss`.

## [2026-10-31] build | X-38c 2~3구간: Bootstrap 4 도입과 안내서 대응 (진행 중)
BS4.6.2 SCSS를 들이고(`vendor/bootstrap`) 변수로 BS2 값을 맞췄다(`_bs-variables.scss`). 안내서대로 라벨→배지, 탭→`nav-item`/`nav-link`, 드롭다운→`dropdown-item`, `help-block`→`form-text`, `well`→`card`, `.checkbox`/`.radio`→`form-check`, 진행 막대→`bg-*`, `col-md`→`col-lg`, `pull-*`→`float-*`로 템플릿과 JS를 바꿨다. `affix`는 자체 `yona.Affix.js`로 대체했다(A-291). 두 빌드의 계산된 스타일을 대조해 이동 요소를 1,901개에서 150개로 줄였다.
중간에 호환 층에 규칙을 더해 수치만 맞추고 있다는 선생님 지적을 받아(A-293), 탭과 드롭다운의 BS2 규칙 72개를 지우고 BS4 변수와 자체 SCSS로 되돌렸다(A-290). 호환 층은 2,199줄에서 1,746줄이 되었다. (정정: 이 줄에 적었던 "전체 시험 통과"는 시험을 실제로 다시 돌린 결과가 아니었다. 결과 요약 도구가 4시간 전의 결과 파일을 합산했다. 다시 돌린 실제 결과는 3027개 중 51개 실패였고, 아래 "시험과 순회 점검" 항목에서 다룬다.) 남은 일: 폼 가로 배치(`form-horizontal`), `help-inline`, `hide`, 단추 그룹, 표, 그리드 호환 규칙의 마크업 전환, 상호작용 순회와 태그 `bs4`.

## [2026-10-31] test | X-38c 점검에서 찾은 문제와 고친 것
시험을 다시 돌려 51개 실패를 얻었고(3027개 중), 순회 점검으로 드롭다운 100건이 열리지 않는 문제를 찾았다.
- **실제 버그**: 라벨→배지 변환 스크립트가 `th:class`의 타임리프 표현식 안 변수 이름(`label.category`)까지 바꿔서 프로젝트 목록과 조직 화면이 오류로 그려지지 않았다(2개 템플릿). 표현식 부분을 변환 전 커밋과 대조하는 확인으로 다른 곳에 없음을 확인했다.
- 자체 SCSS가 옛 이름에 기대어 조용히 죽은 규칙: `.open > .flat`(드롭다운 열림), `.alert-error`, `.control-label`, `.img-rounded`, `.popover-content`/`.popover-title`, `.tooltip.in`, `.modal-backdrop.fade.in`. 새 이름(`.show`, `.alert-danger`, `.col-form-label`, `.avatar-wrap.rounded`, `.popover-body`/`.popover-header`)으로 고쳤다.
- BS4 탭은 `.nav` 밖의 목록에서 활성 표시를 옮기지 못한다. 자체 `yona.Tabs.js`로 `ul.nav-subtab`과 `ul.lst-stacked`의 `li`에 `active`를 옮긴다. 사이드바 스크립트는 `li`가 아니라 링크에 `active`를 준다. `typeahead` 메뉴 항목에는 `dropdown-item`을 붙였다.
- 시험은 새 마크업 기준으로 고쳤다(`li.active a`→`a.nav-link.active` 등). `LegacyBootstrapLintTest`에 BS4 단계 패턴(`label`, `col-md`, `help-block`/`help-inline`/`control-label`, `radio`/`checkbox-inline`, `progress-bar-*`)을 더하고 완료 단계를 4로 올렸으며, 표현식(`${...}`) 안의 낱말은 세지 않게 했다.

## [2026-10-31] build | X-38c 완료: Bootstrap 4 (태그 bs4)
Bootstrap 4.6.2로 전환을 마쳤다. 안내서대로 마크업을 바꾼 항목: 라벨→배지, 탭→`nav-item`/`nav-link`, 드롭다운→`dropdown-item`, `help-block`→`form-text`, `help-inline`→유틸리티, `well`→`card`, 체크박스·라디오→`form-check`, 진행 막대→`bg-*`, 브레드크럼→`breadcrumb-item`, `control-label`→`col-form-label`, `col-md`→`col-lg`, `pull-*`→`float-*`, `.in`→`.show`. `affix`는 자체 `yona.Affix.js`로, `.nav` 밖의 탭 목록은 `yona.Tabs.js`로 대체했다. 탭과 드롭다운과 브레드크럼은 BS4 CSS와 변수(`_bs-variables.scss`)를 쓴다.
확인: 상호작용 순회 121/121(모달, 탭, 드롭다운, 툴팁, 팝오버), 기준(`pre-bs5`) 대비 새 문제 0, 캡처 평균 차이 0.3%, 계산된 스타일로 본 이동 요소 1,901개에서 29개로(남은 것은 flex 표기와 1px 폭). 전체 시험 3027개 통과(결과 파일 316개가 모두 이 실행에서 만들어진 것임을 시각으로 확인했다).
호환 층은 2,199줄에서 1,129줄로 줄었다. 남은 규칙: 단추 그룹, 표, 알림, 그리드(BS4 flex 열을 끄고 BS2 유동 폭을 쓴다), 요소 기본값. 이 부분은 의존 장부에 이유와 함께 적었고 BS5에서 정한다.

## [2026-10-31] build | X-38d, X-38e 완료: Bootstrap 5 (태그 bs5)
Bootstrap 5.3.8로 전환했다(A-295). 안내서 항목을 마크업과 JS에 적용했다: `data-toggle`→`data-bs-toggle` 등(BS 위젯에 한정, 서버가 만드는 HTML 문자열 포함), `float-*`→`float-start/end`, `pl-*`→`ps-*`, `badge-*`→`bg-*`, `badge-pill`→`rounded-pill`, `text-muted`→`text-body-secondary`, `close`→`btn-close`, `form-group` 폐지(자체 `form-field`), 위젯 호출(`modal`, `popover`, `tab`, `tooltip`)을 `bootstrap.*` API로, 반복되는 팝오버와 탭은 `$yobi.showPopover`, `hidePopover`, `showTab`로 모았다. BS5의 새 기본 모양(모든 `.row` 자식의 폭 100%, 직접 그리는 체크박스, 표 칸 배경과 아래 선, 단추 그룹 겹침, `hr` 투명도, `legend` 떠다님, `.form-text` 크기)은 변수와 자체 규칙으로 BS2 모양에 맞췄다.
확인: 상호작용 순회 121/121(기준 대비 새 문제 0), 캡처 평균 차이 0.1%(최대 1.7%), 계산된 스타일로 본 이동 요소 24개, 전체 시험 3027개 통과(결과 파일 316개가 모두 이 실행에서 만들어진 것임을 시각으로 확인). `LegacyBootstrapLintTest`는 5단계 패턴이 모두 0이고 완료 단계를 5로 올렸다.
순회에서 찾은 것: BS5의 jQuery 플러그인은 `DOMContentLoaded`에서 등록되어 사이트의 `$(function(){...})`가 먼저 실행되면 `.popover()`가 없다(초기화는 `bootstrap.*`를 직접 부른다), 드롭다운 열림 표시가 부모가 아니라 메뉴와 토글의 `.show`다, 서버가 만드는 마크다운과 이벤트 HTML에 옛 속성이 남아 있었다.
남은 일(X-38f): 호환 층(약 1090줄)을 걷어낼지 정하는 일과 옛 BS 자원 정리. 선생님 확인 후 진행한다.

## [2026-10-31] build | X-38f 1차: 옛 자원 정리와 affix 일부 sticky 전환
선생님 확인을 받아 `bootstrap/fonts`, `bootstrap/images`, `less-1.3.0.min.js`를 지웠다(참조 없음 확인). `bootstrap-switch.js`와 `bootstrap-better-typeahead.js`는 알림 설정 화면과 자동완성에서 쓰여 남겼다. 이슈·게시글 우측 정보창과 관리자 막대는 `position: sticky`로 옮겼다(A-296). 스크롤 위치별 측정(`e2e/scrollcmp.mjs`)에서 위치와 크기가 기준과 같았고 정보창 고정 폭 9px 차이만 있다. 일괄 수정 막대와 코드 리뷰 상자는 `yona.affix`를 유지했다.
확인: 순회 121/121, 기준 대비 새 문제 0, 캡처 최대 차이 1.7%(가장 큰 곳 `project-fork-new`), 전체 시험 3031개 통과(결과 파일 317개 모두 이번 실행에서 생성). 호환 층 단계 이관은 D-54로 결정했다.

## [2026-10-31] build | X-38f 완료: 호환 층 이관 (D-54)
`_bs3-compat.scss`(2,199줄)를 영역별로 이관해 지웠다: `_search-form`, `_alerts`(+알림·배지 변수), `_tables`, `_buttons`, `_grid`(열 폭을 변수와 `@for`로), `_bs-overrides`, `_base`(요소 기본값), 본문과 제목은 BS5 변수와 `--bs-body-line-height`. 이관마다 직전 커밋과 전 화면 `style-diff`가 같을 때만 커밋했고, 같지 않으면 규칙을 되살렸다(`legend small`, `blockquote p/small`, `h* small`, `li` 줄 높이, `strong` bolder). 이관 중 `sticky` 변경이 모든 `.issue-info`에 `overflow`를 주어 104개 요소가 밀리는 회귀를 만들었던 것을 찾아 `sticky-top` 클래스 기준으로 고쳤다.
확인: 전체 시험 3031개 통과(결과 파일 317개 모두 이번 실행 것), 상호작용 순회 121/121, 기준(`pre-bs5`) 대비 새 문제 0, 캡처 평균 차이 0.2%(최대 1.7%, `project-fork-new`), 계산된 스타일 비교 이동 요소 34개(모두 이전부터 있던 `flex` 표기와 1px 폭).
후속: X-38g(글자 크기와 줄 높이의 상대 단위화), X-38h(`border-box`와 flex 그리드), X-38i(디자인 현대화)를 D-55로 승인받았다(X-38i는 컴포넌트별로 다시 여쭙는다).

## [2026-10-31] build | X-38g, X-38h-1 완료: rem 글자 크기와 줄 높이, 전역 border-box (D-55, A-298, A-299)
X-38g: `font-size` 334곳과 `line-height` 141곳, 변수 9개를 `px/16`의 `rem`으로 옮겼다. 처음 계획한 비율 방식은 상위 요소가 정한 줄 높이(30, 34, 40px)를 하위 요소가 상속받아 쓰던 곳을 덮어써 5,092개 요소가 달라져 버렸다(A-298). 기본 설정에서 `style-diff` 차이 0, 루트 20px에서 13px 본문이 16.25px, 줄 높이 25px로 함께 커진다(`e2e/scalecheck.mjs`). `RelativeUnitsTest`로 px 재도입을 막는다.
X-38h-1: 전역 `border-box`만 바꾸면 요소 24,411개 중 11,489개(47%)가 달라졌다(큰 차이의 예: 풀 리퀘스트 화면 8.3%). `e2e/bbpatch.mjs`로 84건을 `calc(원래 값 + 여백)`으로 고치고, 요소마다 여백이 달라 값을 정할 수 없는 규칙과 외부 위젯(select2, pikaday, jquery.elevator)은 그 규칙에만 `content-box`를 두었다(A-299). 11,489 → 5,752 → 3,248 → 1,915 → 1,535 → 881 → 336 → 76 → 34로 줄었다(두 번 잘못된 측정을 했다: 이전 상태의 jar로 잰 34개, 보정이 역효과를 낸 diff 모드).
확인: 기준(`pre-bs5`) 대비 계산된 스타일 이동 요소 34개(직전 커밋과 화면별로 같음), 전체 시험 3033개 통과(결과 파일 318개 모두 이번 실행 것), 상호작용 순회 121/121(약 27분)에서 새 문제 0, 캡처 평균 차이 0.2%(최대 1.7%).
다음: X-38h-2(float 그리드를 flex 그리드로).

## [2026-10-31] build | X-38h-2 완료: float 그리드를 flex로 (D-55, A-300)
열을 가진 `.row`(열 174개)는 `:has()`로 flex 컨테이너가 되었고 열의 폭과 간격은 BS2 값 그대로이다. `.row`가 아닌 부모 아래의 고정 그리드 열 43개는 일괄 CSS 규칙이 실패해(자식이 섞여 폭이 줄고 줄이 바뀜) 부모 29곳의 마크업에 BS5 유틸리티를 붙였다. JS가 보이고 숨기는 `subtask-wrap`은 `!important`인 d-flex가 숨김을 덮어 안쪽 flex 래퍼를 두었다. 열 폭의 합이 12를 넘는 사용자 목록 행은 `row-float` 표식으로 float을 유지했다.
선생님 지시("레이아웃 부작용 고쳐")로 v1.6 float의 부작용(float만 가진 `dd`가 높이 0으로 접혀 이슈 작성과 수정 화면의 하위 이슈 줄이 겹쳐 흐름)은 되돌리지 않고 고쳤다. 그 아래 내용이 89px 내려가는 것이 의도한 차이이다.
확인: 계산된 스타일 이동 요소 54개(기준 34개 + 의도한 차이와 2px 이내 차이), 전체 시험 3035개 통과(결과 파일 319개 모두 이번 실행 것), 상호작용 순회 121/121(14:29~14:56)에서 새 문제 0, 캡처 평균 차이 0.2%(최대 1.7%).
다음: X-38i(BS2 고유 모양의 현대화)는 컴포넌트별로 전후 캡처를 보여 드리고 선생님이 고르신다.


## [2026-10-31] X-38 후속: yona.Affix.js 제거(A-301)
선생님 지시("sticky만")로 남아 있던 `yona.affix` 사용처 두 곳(코드 리뷰 상자, 일괄 수정 막대 줄)을 CSS `position: sticky`로 옮기고 `yona.Affix.js`와 `.affix` 규칙을 지웠다. 막대는 높이 0인 float 묶음이라 감싼 `.filter-wrap`에 붙였다. 스크롤 위치별 비교에서 정지 위치와 정지 전 크기가 기준과 같았다. 자체 SCSS 삭제 후보와 X-23은 선생님 확인을 기다린다.

선생님 요청으로 A-301의 일괄 수정 막대 줄이 붙은 동안 v1.6처럼 위아래 여백 15px과 아래 선을 갖게 했다(`stuck` 클래스). 스크롤 확인에서 아래 목록 위치가 밀리지 않았다.

## [2026-10-31] X-38i-1 단추 현대화(D-56, A-302)
선생님이 "BS2의 고유 효과를 유지하라는 뜻이 아니라 새 버전의 고유 효과를 쓰라는 뜻"이라고 바로잡으셔서 D-56을 기록하고, 단추부터 BS2 모양(그라데이션, 글자 그림자, 직접 만든 드롭다운 단추)을 지우고 BS5 `btn-outline-secondary`, `btn-light`, `btn-success`를 쓰게 했다. 전후 캡처는 `e2e/shots/x38i-*-before|after.png`.

## [2026-10-31] X-38i-2 폼 컨트롤 현대화(D-56, A-303, A-304)
선생님이 "나머지도 BS5 요소 기본 규칙으로"라고 하셔서 입력창, 글상자, 선택 상자, 체크박스, 라디오 220곳에 BS5 클래스를 달고 `_base.scss`의 BS2 폼 요소 기본값과 글자 입력창 `content-box` 예외를 지웠다. 전체 순회 121개 화면에서 새 JS 오류가 없었고, 줄지어 있던 입력창 몇 곳은 `d-flex gap-2`로 고쳤다.

## [2026-10-31] X-38i-3 알림, 배지, 표, 탭 현대화(D-56, A-305)
`_alerts.scss`, `_tables.scss`와 BS2 컴포넌트 변수, 탭 규칙을 지우고 BS5 기본 모양을 쓰게 했다. 탭 아래 여백은 `mb-3`으로 옮겼다. 테마 색은 그대로 두었다.

## [2026-10-31] X-38i-5 BS5 반응형 그리드 전환(D-57, A-308)
선생님이 "그리드까지 BS5 반응형으로"를 고르셔서 자체 그리드 SCSS와 940px 컨테이너를 지우고 BS5의 `.row`, `.col-lg-N`을 쓰게 했다. 열 233개의 부모를 정리하고, 열 폭을 덮어쓰던 자체 규칙을 지웠다. 열이 줄바꿈되는 곳을 찾는 `gridcheck.mjs`를 만들었다.

## [2026-10-31] X-38i 마무리 정리(A-309)
선생님 지시로 입력창의 퍼센트 폭 보정을 지우고, 사용처가 0인 BS2 잔재 SCSS 7종을 지웠다. 처음 "쓰이지 않는다"고 보고했던 `.card`와 `.nbtn`은 실제로 쓰여 남겼고 그 오보는 A-309에 정정해 기록했다. select2 4.x는 보류, 테마 색은 결정 대기이다.

## [2026-10-31] X-38i 테마 색 BS5 기본 전환(D-58, A-310)
선생님이 BS2 색과 BS5 색의 전후 캡처를 보시고 "BS5 기본 색으로 모두 변경"을 고르셨다. 테마 색 변수와 자체 SCSS의 직접 색 값을 BS5 팔레트로 옮겼다.

## [2026-11-01] X-38i 입력창 폭 조정(D-59)
비밀번호 변경과 포크 만들기의 입력창을 BS5 폭 클래스로 줄였다. 로그인과 가입의 퍼센트 폭은 그대로 둔다.

## [2026-11-01] R-130 가져오기에서 이슈 댓글 테이블 실패 개선
선생님 승인으로 `ISSUE_COMMENT`를 가져올 때 `project_id`를 이슈에서 채우게 했다. 내보내기는 v1.6과 같다. 시험은 `DataExchangeTest`(가져오기, 내보내기 모양).

## [2026-11-01] X-17 프로젝트 위키 보류
구현 순서, git clone과 push 허용 여부, 읽기와 쓰기 권한을 여쭈었고 선생님이 세 질문 모두 "일단 중지"로 답하셨다. 착수하지 않는다. 다시 시작할 때 이 세 가지를 먼저 정한다(권장안: REST 먼저 화면은 이어서, 웹과 REST만, 읽기는 프로젝트 읽기 권한과 쓰기는 프로젝트 쓰기 권한).

## [2026-11-01] X-17 프로젝트 위키 재개와 범위 결정(D-60)
선생님이 계획을 요청하셨다. GitHub 문서, Gitea 소스, Yona 위키 화면을 조사해 범위(Forgejo 수준 기능, GitHub 모양 화면, 하위 경로 제외, 두 리비전 비교 포함, 백업 별도)를 확정했다. 단계: W0 결정 기록, W1 저장소 계층, W2 서비스, W3 REST, W4 화면, W5 CLI, W6 검증과 문서, W7 git clone과 push.

## [2026-11-01] X-17 W1 위키 저장소 계층
선생님이 저장소 위치를 코드 저장소 옆 `<이름>.wiki.git`, 주소를 Forgejo 방식으로 정하셨다(D-60 8, 9번). `WikiRepositories`, `RepositoryService`(이동, 삭제 연동), `ProjectNameRules`의 `.wiki` 끝맺음 금지를 시험 먼저 만들었다. 시험: `WikiRepositoriesTest` 14건, `ProjectNameRulesTest` 3건, 저장소 패키지 전체 118건 통과(전체 시험은 W2 뒤에 돌린다). 결정 A-311.

## [2026-11-01] X-17 W2 위키 서비스
`WikiService`(목록, 검색, 조회, 만들기, 고치기와 이름 바꾸기, 지우기, 이력, diff, 비교, 사이드바와 푸터)를 구현했다. 시험 `WikiServiceTest` 26건 통과. 문서는 [architecture/project-wiki.md](architecture/project-wiki.md), 판단은 A-312. 다음은 W3(REST).

## [2026-11-01] X-17 W3 위키 REST
`ExternalWikiApiController`(목록과 검색, 한 건, 만들기, 고치기와 이름 바꾸기, 지우기, 이력, diff와 비교), 범위 표, OpenAPI 태그 "위키"와 스키마를 시험 먼저 만들었다. 시험: `ExternalWikiApiTest` 17건, OpenAPI 5건, API와 보안과 감사 패키지 342건 통과. 쓰기는 `WIKI_PAGE`의 `UPDATE`(구성원)이다. 계약은 [architecture/project-wiki.md](architecture/project-wiki.md). 다음은 W4(화면).

## [2026-11-01] X-17 W4 위키 화면
`WikiViewController`와 템플릿 6장(`wiki/shell`, `view`, `empty`, `edit`, `history`, `compare`), 프로젝트 메뉴의 `Wiki` 탭, 메시지 31개(5개 언어), `_wiki.scss`, 확인 도구의 위키 화면과 고정 데이터를 시험 먼저 만들었다. 시험: `WikiViewTest` 20건, 메뉴 시험 3건 갱신, 전체 3,136건 통과. Playwright로 홈, 편집, 이력, 비교 화면을 확인했다(선생님께 캡처 전달). 문서는 [architecture/project-wiki.md](architecture/project-wiki.md). 다음은 W5(CLI).

## [2026-11-01] X-17 W5 위키 CLI
`../yona-cli`의 위키 명령 8개를 서버 위키 REST에 맞췄다(경로, 응답 모양, `PATCH`, `--revision`, 하위 경로 제거). 시험은 CLI 저장소에서 먼저 새 계약으로 고쳐 쓰고 구현했다(`go test ./...` 통과, `gofmt`와 `vet` 깨끗). README 갱신. 다음은 W6(검증과 문서)와 W7(git clone과 push).

## [2026-11-01] X-17 W7 위키 git clone과 push
`GitHttpController.handleWiki`, `GitHttpService.advertiseWiki`와 `rpcWiki`, `WikiRepositories.openForHttp`, `RepositoryUrls.wikiUrlFor`, 사이드바의 `Clone this wiki locally` 상자를 시험 먼저 만들었다. 시험: `WikiGitHttpTest` 12건(JGit 복제와 푸시), `WikiViewTest`의 복제 주소, 메시지 2개(5개 언어). 결정 A-313. W6(전 화면 순회 비교와 전체 시험)이 남았다.

## [2026-11-01] X-17 W6 검증과 마무리
전체 시험 3,149건 통과. 전 화면 순회 `wiki-menu` 132/132 통과, 이전 기준 대비 새 문제 0, 새 화면 11. 복제 주소 상자의 글자를 줄였다. X-17 완료.

## [2026-11-01] 위키 보강 결정(D-61)
선생님이 `[[Page]]` 링크, 첨부 파일, 이름 바꾸기를 따라가는 이력, 위키 켜기와 끄기를 요청하셨다. 켜기와 끄기는 프로젝트 설정(새 프로젝트 켜짐, 기존 프로젝트 꺼짐, 끄면 내용 보존), 첨부는 위키 저장소의 `uploads/`로 정하셨다. 순서: 켜기와 끄기, 이력, `[[Page]]`, 첨부.

## [2026-11-01] 위키 켜기와 끄기(D-61 1단계)
`project_menu_setting.wiki` 컬럼, 설정과 생성과 가져오기 폼의 체크 상자, 메뉴 탭 조건, 화면과 REST와 git의 404 차단, 포크와 REST 생성의 기본값, 사이트 내보내기와 가져오기를 시험 먼저 만들었다. 새 프로젝트는 켜짐, 기존은 꺼짐. 시험: `WikiToggleTest` 5건 등.

## [2026-11-01] 위키 이름 바꾸기를 따라가는 이력(D-61 2단계)
`WikiService.history`, `diff`, `compare`가 이름 바꾸기를 따라간다(`FollowFilter`와 `RenameCallback`). 시험 6건을 먼저 더했고 결정 A-314에 반복자의 미리 읽기를 기록했다.

## [2026-11-01] 위키 `[[Page]]` 링크(D-61 3단계)
`WikiLinks`(`expand`, `rewriteHtml`)를 시험 먼저 만들고 본문, 사이드바, 푸터 렌더링에 연결했다. 시험 `WikiLinksTest` 14건, `WikiViewTest` 3건. 상대 링크가 위키 첫 화면에서 깨지던 문제도 함께 고쳤다.

## [2026-11-01] 위키 첨부 파일(D-61 4단계)
서비스(`upload`, `file`), 웹(`_upload`, `_uploads`, 편집 화면의 파일 선택), REST(`wiki/files`), OpenAPI와 범위 표, 메시지 5개(5개 언어), CLI(`wiki attach`, `wiki file`)를 시험 먼저 만들었다. 결정 A-315. 다음은 전체 시험과 화면 확인이다.

## [2026-11-01] 위키 보강(D-61) 마무리
전체 시험 3,199건 통과(스키마 시험의 의도한 추가 컬럼에 `project_menu_setting.wiki` 등록). 확인 도구의 시드가 위키를 켜고 `[[링크]]`와 이미지 첨부를 심는다. Playwright로 홈(링크와 이미지), 편집(파일 첨부), 복제 주소 상자를 확인했다.

## [2026-11-01] X-9 감사 로그 착수(D-62)
부착 지점을 조사하고(경계: 인증과 인가 거부, 서비스: 도메인 변경, 사이트 관리) 여섯 가지 권장안을 선생님이 수용하셨다. 단계: A1 결정과 설계, A2 핵심(기록기, 해시 사슬), A3 경계 이벤트, A4 계정과 토큰, A5 도메인 변경과 사이트 관리, A6 조회와 내보내기, A7 보존, A8 구조 시험과 마무리.

## [2026-11-01] X-9 A2 감사 로그 핵심
`AuditLog`, `AuditChainHead`, `AuditHash`, `AuditDetails`, `AuditRecorder`, `AuditVerifier`, `AuditFileSink`를 시험 먼저 만들었다(20건). 결정 A-316. 테이블 2개를 스키마 시험의 의도한 추가 목록에 올렸다.

## [2026-11-01] X-9 A3 경계 이벤트
`AuditBoundary`(로그인 성공과 실패, 로그아웃, 토큰과 git 인증 실패, 차단), `AccessDeniedAuditFilter`(403), `ApiScopeInterceptor`의 범위 거부를 시험 먼저 연결했다(14건). 같은 요청의 상태 변경 기록이 사슬을 쥔 채 경계 이벤트를 남기면 교착하는 문제를 시험으로 재현하고 바깥이 끝난 뒤로 미루는 방식으로 고쳤다.
