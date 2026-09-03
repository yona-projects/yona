# P1-85(구 P2-05): 접근제어 중앙화

## Context

yona(레거시)는 `app/utils/AccessControl.java` 단일 클래스가 `isAllowed(User, Resource, Operation)`이라는 하나의 진입점으로 모든 리소스(이슈/게시글/PR/댓글/프로젝트/마일스톤/조직/웹훅 등)의 READ/UPDATE/DELETE/ACCEPT/CLOSE/REOPEN/WATCH/LEAVE/ASSIGN_ISSUE 권한을 판단한다. yona(포팅 대상)는 이 규칙이 ~40개 컨트롤러에 각자 다른 이름(`checkReadPermission`, `isManagerOrAuthor`, `isProjectManager` 등)의 인라인 헬퍼로 흩어져 있어, 규칙이 일관되게 적용되는지 보장되지 않는다.

이번 세션 동안 이미 이 산발적 구조 때문에 발생한 구체적 버그를 여러 건 고쳤다(P1-78 PR 리뷰어 권한 누락, P1-82 이슈 공유자 READ 미연결, P1-86(구 P2-12) 담당자 권한 오버라이드 누락). 사용자가 이 근본 원인(P1-85, 등록 당시 번호는 P2-05)을 직접 지적하며 중앙화를 명시적으로 지시했다: *"접근제어가 컨트롤러별 산발적 인라인 체크가 아니라 레거시 요나처럼 중앙화된 방식으로. Spring/Kotlin 문법에 맞게, 레거시 기능이 축약되지 않도록."*

**2026-08-20 재분류**: 이 항목은 "경미/확인 필요"인 P2가 아니라 "기능 결손/권한 로직 오류"인 P1에 해당한다는 사용자 지적으로 P2-05 → P1-85로 재번호됐다(P1-82와 같은 yona `AccessControl.java:244-248` 규칙에서 파생된 두 갈래인데 섹션이 갈려 있던 것이 발단).

전수조사 결과 이 작업은 27개 파일(컨트롤러 24 + 인프라 3), 6개 인라인 패턴 그룹(~40회 반복), 서비스 계층의 독자적 권한판단 2곳, 그리고 **WebhookController의 인증 자체 부재**라는 별도 심각도의 결함까지 포함하는 대규모 리팩터링임이 확인됐다. 한 세션에 전부 끝내는 것은 현실적이지 않아, 안전하게 끊어갈 수 있는 첫 단계(인프라 전환 + 중앙 서비스 구축)를 이번 세션의 범위로 잡는다.

## 설계 결정

**yona의 `Resource` 다형성(엔티티가 스스로를 `Resource`로 변환하는 액티브레코드 패턴)은 이식하지 않는다.** JPA 엔티티는 리포지토리를 주입받을 수 없어 `Attachment`처럼 컨테이너를 동적으로 조회해야 하는 경우를 처리할 수 없고, 지연 로딩 프록시 문제도 생긴다. 대신 **리소스 타입별 오버로드 함수를 가진 중앙 서비스**로 설계한다 — 컨트롤러가 이미 로드해둔 엔티티를 그대로 넘기면 재조회 없이 판정된다. 규칙 자체(멤버십/그룹멤버/매니저/작성자/담당자/sharer/PUBLIC-PROTECTED-PRIVATE 분기/siteManager·조직관리자 우회 등)는 yona `AccessControl.java`에서 하나도 축약하지 않고 그대로 옮긴다.

기존 `object AccessControl`(`config/security/AccessControl.kt`)은 `@Component class AccessControl`로 전환한다. 이유: 현재 이 파일은 `projectUserRepository.existsByProjectIdAndUserId(...)`(DB 조회, 컨트롤러 쪽 패턴)와 `project.organization.organizationUsers`(엔티티 컬렉션 순회, 이 파일 자체의 기존 패턴)를 뒤섞어 쓰고 있어 잠재 결함이 있다 — 리포지토리 주입으로 하나로 통일해야 한다. 이 전환은 78개 호출부(`AccessControl.foo(` → `accessControl.foo(`)의 기계적 치환이며 **로직 변경이 전혀 없다**(Kotlin 컴파일러가 누락된 호출부를 빌드 실패로 강제하므로 오히려 안전).

## 1단계 범위: 1a + 1b (인프라 전환 + 중앙 서비스 구축)

컨트롤러 마이그레이션(그룹 A~E, 6단계 이상)은 이 단계에 포함하지 않는다 — 각 단계가 "yona 대비 실제로 다른지" 판단에 사용자 확인이 필요할 수 있는 독립된 작업이기 때문이다. 이 단계 종료 시점에는 "컨트롤러를 하나씩 새 서비스로 갈아끼우기만 하면 되는" 안전한 착지점을 만든다.

### 1a. `object` → `class` 전환 (동작 불변)

- `config/security/AccessControl.kt`: `object AccessControl` → `@Component class AccessControl(...)`, 생성자에 `ProjectUserRepository`, `OrganizationUserRepository` 주입.
- 기존 export된 함수 시그니처(`isAllowedToReadProject`, `isProjectResourceCreatable`, `isAllowedToUpdateIssue/Posting/Milestone`, `isAllowedIfGroupMember`, `isAllowedIfSharer`)는 **그대로 유지** — 3개 비-컨트롤러 소비자(`SvnAuthorizationFilter`, `GitAuthorizationFilter`, `IncomingMailProcessingService`)를 건드리지 않기 위함.
- `isOrganizationAdmin`(내부 헬퍼)이 쓰던 `organization.organizationUsers` 엔티티 순회를 `organizationUserRepository` 조회로 통일.
- 78개 호출부(`web/*.kt` 24개 파일 + 위 3개 인프라 파일)를 전부 생성자 주입 + `AccessControl.` → `accessControl.` 치환.
- **검증**: 새 테스트 불필요 — 로직 변경이 없으므로 기존 전체 스펙(`./gradlew test`)이 그대로 그린이면 성공. 이게 characterization test 역할을 한다.

### 1b. `Operation` enum + 신규 `isAllowed(...)` 오버로드 (addition-only)

- `domain/enumeration/Operation.kt` 신규: `READ, UPDATE, DELETE, ACCEPT, REOPEN, CLOSE, WATCH, LEAVE, ASSIGN_ISSUE` (yona `models/enumeration/Operation.java`와 동일).
- `AccessControl.kt`에 리소스 타입별 오버로드 함수를 TDD로 추가한다(yona `AccessControl.java` 라인별 이식, 아래는 핵심 시그니처):
  ```kotlin
  fun isAllowed(user: User?, project: Project, operation: Operation): Boolean          // PROJECT 자신
  fun isAllowed(user: User?, organization: Organization, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, issue: Issue, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, issueComment: IssueComment, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, posting: Posting, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, postingComment: PostingComment, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, pullRequest: PullRequest, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, commitComment: CommitComment, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, commentThread: CommentThread, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, reviewComment: ReviewComment, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, milestone: Milestone, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, webhook: Webhook, operation: Operation): Boolean
  fun isAllowed(user: User?, project: Project, resourceType: ResourceType, operation: Operation): Boolean  // CODE 등 (DELETE=항상 false 포함)
  fun isAllowed(user: User?, projectTransfer: ProjectTransfer, operation: Operation): Boolean
  fun isAllowedAttachment(user: User?, attachment: Attachment, operation: Operation): Boolean  // 유일하게 container 동적 해석, 여러 repo 사용
  ```
  각 함수 본문은 yona `isProjectResourceAllowed`/`isGlobalResourceAllowed`의 공용 규칙(siteManager 우회 → 조직관리자 우회 → `isManagerOf || isAllowedIfAuthor || isAllowedIfAssignee`면 operation 무관 즉시 허용 → 리소스별 특수 분기 → 일반 operation switch)을 그대로 반영한다. `isAllowed(..., issue, Operation.READ)`는 반드시 `isAllowedIfSharer`를 포함해야 한다(현재 `IssueController`에 있는 P1-82 로직을 흡수).
- `AccessControlSpec.kt` 신규(`test/.../config/security/`) — Kotest `DescribeSpec` + `mockk`로 Spring 컨텍스트 없이 유닛 테스트. yona 조항별 최소 1건씩, TDD(RED 먼저):
  - PUBLIC 프로젝트 + 게스트 → 이슈 READ **false** (yona `:276`, 현재 컨트롤러 전체가 놓치고 있는 부분)
  - 위 조건에서 issue의 sharer면 → **true** (parent 이슈의 sharer 케이스 포함)
  - CODE 리소스는 멤버여도 DELETE **false** 항상
  - PROJECT_TRANSFER ACCEPT: destination이 로그인ID 일치 / 조직명이고 유저가 ORG_ADMIN, 두 케이스
  - WATCH: 게스트(비-익명)도 PUBLIC이면 **true** (READ과 다름을 명시적으로 검증 — 현재 `WatchController`가 READ 규칙을 잘못 재사용 중임을 확인했음)
- **이 단계에서 어떤 컨트롤러도 새 함수를 호출하지 않는다** — 순수 추가라 실제 API 응답은 전혀 안 바뀐다.
- **검증**: `AccessControlSpec.kt` 그린 + 전체 `./gradlew test` 그린(기존 스펙 전부 무영향 확인).

## 후속 단계 (미착수, 백로그 별도 항목으로 등록 완료 — 2026-08-20, 그룹 C/D/E 확정 조사까지 반영)

등록 당시 P2-13~17로 임시 번호를 매겼으나, "경미/확인 필요"인 P2가 아니라 대부분 "확정된 권한 로직 오류"임이 밝혀져 P1로 재분류했다(P1-85와 마찬가지 사유). 이어서 "확인 필요"로 남겨뒀던 P2-15/16도 yona와 실제로 한 줄씩 대조하는 확정 조사를 진행해 종결했다(더 이상 "확인 필요" 상태가 아님). `docs/PARITY_BACKLOG.md`의 최신 번호가 정본이며, 아래는 매핑 기록이다.

- **P1-87(구 P2-13, 최우선)**: `WebhookController`의 인증 자체 부재. 확정 조사로 수정 범위 구체화 — 단순 로그인 체크가 아니라 조회(`webhooks()`)를 포함한 3개 엔드포인트 전부 "프로젝트 멤버(또는 그룹멤버)" 권한이 필요함(yona `ProjectApp.java:1268,1282,1314` 전부 `@IsAllowed(UPDATE)`).
- **P1-88(구 P2-14)**: 그룹 A/A'(`checkReadPermission` 계열, 17곳) 실제 교체 — PUBLIC+게스트, sharer 두 결함이 실제로 고쳐지는 단계.
- **P1-89(P2-16에서 분리)**: `WatchController.checkWatchPermission`이 WATCH 연산에 READ 규칙을 잘못 재사용 중(게스트 워치 부당 차단) — 이미 확정된 회귀 버그라 P1로 분리.
- **P1-90(구 P2-17)**: `CommentServiceImpl.hasPermission()`이 yona보다 엄격(현재 작성자/MANAGER만 허용, yona는 일반 멤버 전원 허용) — 확정된 권한 축소 버그.
- **P2-15 확정 조사 결과 (종결, 5건이 P1-91~95로 이동)**: 그룹 C/D(`isManagerOrAuthor`/`isManagerOrContributor`/`isAuthorOrManager`/`isProjectManager`)를 yona와 전수 대조. **P1-91** `BoardController.isManagerOrAuthor`(게시글 UPDATE/DELETE), **P1-92** `PullRequestController.updatePullRequest`(PR 수정), **P1-93** `CodeHistoryController.isAuthorOrManager`(커밋댓글 삭제), **P1-94** `IssueLabelController.isProjectManager`(라벨/카테고리 7곳), **P1-95** `MilestoneController.isProjectManager`(마일스톤 3곳, 이미 정확한 `AccessControl.isAllowedToUpdateMilestone()` 존재하나 미사용) — 5건 전부 "yona는 일반 멤버 전원 허용인데 yona는 author/manager로 좁혀놓은" 동일 패턴의 확정된 권한 축소 버그. `ProjectController`/`ProjectMemberController`의 `isProjectManager`(PROJECT 자체 UPDATE/DELETE)와 PR의 `checkWritePermission` 기반 액션들은 yona와 일치해 문제없음. 부수 발견(P1-98, 오너 제거 방지 가드 누락)도 이 조사에서 나옴.
- **P2-16 확정 조사 결과 (종결)**: 그룹 E 잔여(`checkCodeAccessibility`, `isOrgAdmin`) + CODE/ORGANIZATION/WEBHOOK/ATTACHMENT/PROJECT_TRANSFER를 yona와 전수 대조. CODE(코드 브라우저), ORGANIZATION, PROJECT_TRANSFER는 일치해 문제없음(`ProjectServiceImpl.isAuthorizedToAcceptTransfer()` 포함, 중앙 서비스 이관 실익 낮음). WEBHOOK은 P1-87에 흡수. 범위 밖에서 신규 발견한 **P1-96**(첨부파일 다운로드/목록 무인가, 보안 이슈 — `AttachmentController.getFile/getFileList`에 권한 체크 자체 없음)과 **P1-97**(브랜치 삭제 매니저 제한 누락, 과잉 허용 — `BranchApiController.deleteBranch`)을 P1로 등록.

## 검증 (착수 시)

- 1a 완료 시점: `./gradlew test` 전체 그린 (동작 불변 확인).
- 1b 완료 시점: `./gradlew test --tests "com.github.yonaprojects.yona.config.security.AccessControlSpec"` 그린 + 전체 `./gradlew test` 그린.
- `docs/PARITY_BACKLOG.md`: P1-85 행을 진행 상태에 맞게 갱신 + 완료 로그에 실제로 한 일과 다음 단계 명시.
- git: 1a와 1b를 별도 커밋으로 분리(전자는 순수 리팩터링, 후자는 addition-only).
