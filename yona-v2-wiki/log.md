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
