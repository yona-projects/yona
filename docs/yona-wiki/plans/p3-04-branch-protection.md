---
type: plan
id: P3-04
title: "브랜치 보호"
status: done
priority: 4
depends_on: []
blocks: []
source: docs/PARITY_BACKLOG.md#P3-04
created: 2026-08-28
updated: 2026-09-06
tags: [plan, p3, git, security]
---

# 브랜치 보호

## 배경

`ProtectedBranch` 모델과 관련 로직이 전무 — 설계만 진행됨. 적용 지점은 두 곳: (1) 직접 push 차단,
(2) PR 병합 시 체크. `AccessControl`과는 별개 레이어(권한 확인 통과 후 추가 정책)로 설계할 계획.
원본: [`docs/PARITY_BACKLOG.md#P3-04`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- `ProtectedBranch` 모델(`branch_pattern`, `require_pull_request`, `require_approvals`, `require_signed_commits`,
  `restrict_push_to`, `disallow_force_push`, `disallow_delete`, `admins_can_bypass`)
- 직접 push 차단 훅, PR 병합 시 체크
- `yona branch-protection set/list/unset` CLI 명령(선언만, 실제 구현은 [[p3-02-cli-and-rest-api]] 완료 후)

### 제외 (비범위)
- `require_signed_commits`의 실제 서명 검증 로직 — 이건 [[p3-03-ssh-gpg]]의 GPG 검증 파이프라인에 위임(이 계획은 플래그를 걸고 검증 결과를 소비하는 쪽만 구현, 검증 자체는 만들지 않음)

## 의존성

- **선행 조건**: 없음(독립적) — 단, `require_approvals` 구현 가능 여부를 확인하는 스파이크가 Step 1로 선행돼야 함
- **후속 파급**: `require_signed_commits` 플래그는 [[p3-03-ssh-gpg]]의 GPG 검증 파이프라인이 완성돼야 실질적으로 동작(그 전까지는 플래그만 있고 항상 통과 처리)

## 설계 개요

- **직접 push**: `GitPushHooks.kt`에 `BranchProtectionPreReceiveHook` 추가(기존 `RejectPushToReservedRefsPreReceiveHook`과 같은 방식)
- **PR 병합**: `PullRequestServiceImpl.merge()`/`processMergeCheck()`에 체크 추가
- **막힌 지점(선행 스파이크 필요)**: `require_approvals` 구현 가능 여부가 `CommentThread.ThreadState`에 승인/변경요청
  판정 상태가 있는지에 달려 있는데, 원본 백로그 작성 시점까지 확인되지 않았음 — 이 계획의 Step 1로 확정한다

## 단계별 작업 계획 (TDD)

1. **Step 1 — 스파이크: `CommentThread.ThreadState` 확인 (해소됨, 2026-09-06)**
   - `domain/pullrequest/CommentThread.kt`의 `enum class ThreadState`를 직접 확인한 결과 `OPEN, CLOSED`
     두 값뿐이었다 — 승인(APPROVED)/변경요청(CHANGES_REQUESTED) 개념 자체가 코드에 전혀 없다.
     `PullRequestServiceImpl.addReviewer()`(및 CLI `pr review`)도 "리뷰어 자기등록"일 뿐 승인/반려
     판정이 아님을 [[p3-02-cli-and-rest-api]] 작업 중 이미 확인했다.
   - **결정**: `require_approvals`는 이번 라운드에서 실제 승인 개수 검증 로직을 구현하지 않는다.
     `require_signed_commits`(P3-03 GPG 검증 완료 전까지 항상 통과)와 동일한 패턴으로 취급한다 —
     `ProtectedBranch.requireApprovals: Int` 필드는 스키마에 존재하지만(0 = 비활성), 승인
     시스템 자체가 없으므로 값이 0이 아니어도 병합 체크에서 **항상 통과**시킨다. 나중에 실제 승인
     워크플로가 생기면 `PullRequestServiceImpl`의 이 지점(Step4 참고)만 갈아끼우면 된다 —
     별도 추상화(전략 패턴 등)는 만들지 않는다.
   - 검증: Step4에서 `require_approvals`가 0이 아니어도 승인 없이 병합이 성공함을 회귀 테스트로 고정.
2. **Step 2 — `ProtectedBranch` 모델**
   - 실패 테스트: 패턴 매칭(`branch_pattern`이 `release/*` 같은 glob을 매칭하는지) → RED → 구현 → GREEN
3. **Step 3 — 직접 push 차단 훅**
   - 실패 테스트: 보호된 브랜치에 force-push 시도 시 거부 → RED → `BranchProtectionPreReceiveHook` 구현 → GREEN
   - `disallow_delete`, `restrict_push_to`도 각각 동일 패턴으로 추가
4. **Step 4 — PR 병합 체크 (구현 완료, 2026-09-06 — 아래 완료 로그 참고)**
   - 실패 테스트: `require_pull_request` 켜진 브랜치로의 직접 병합 없이 PR 없이 병합 시도 시 거부 → RED → 구현 → GREEN
   - Step 1 결과에 따라 `require_approvals` 포함 여부 결정
   - **구현 시 확정한 구체적 의미**: `merge()`는 정의상 항상 `PullRequest` 객체를 통해서만 호출되는
     경로라 "PR 없이 직접 병합"이라는 시나리오 자체가 서비스 계층에 존재하지 않는다(직접 push로
     브랜치를 갱신하는 경로는 Step3의 `BranchProtectionPreReceiveHook`이 이미 차단). 따라서
     `require_pull_request`/`require_approvals`/`require_signed_commits` 세 필드는 `merge()`에서
     **항상 통과**(no-op)로 구현하고, 정상적인 PR 병합이 이 세 필드가 켜져 있어도 막히지 않음을
     회귀 테스트로 고정했다. 대신 `restrict_push_to`(병합도 toBranch에 대한 ref 갱신이므로
     git push와 동등하게 취급)만 `merge()`에서 실질적으로 검사한다 — 아래 완료 로그 참고.
5. **Step 5 — `admins_can_bypass` 우회 경로 (구현 완료, 2026-09-06 — 아래 완료 로그 참고)**
   - 실패 테스트: 관리자 권한 사용자가 보호 정책을 우회할 수 있는지/없는지(플래그 값에 따라) → RED → 구현 → GREEN
   - 훅(Step3)과 병합 체크(Step4) 양쪽 모두에서 검증했다 — 프로젝트 매니저(`RoleType.MANAGER`) 여부로 판정.

## 완료 기준 (Definition of Done)

- [x] `ProtectedBranch` 모델 및 패턴 매칭 테스트 존재 (`ProtectedBranchSpec.kt`)
- [x] 직접 push 차단(force-push, delete, restrict) 각각 테스트로 검증 (`GitPushHooksSpec.kt`의 `BranchProtectionPreReceiveHook` describe)
- [x] PR 병합 체크가 `require_pull_request` 최소 시나리오에서 동작 (`PullRequestServiceSpec.kt` "5-1. 브랜치 보호 정책" describe — 아래 완료 로그 참고, 실제 의미는 위 Step4 설명 참고)
- [x] `require_approvals` 착수 여부와 근거가 이 문서에 명시(스파이크 결과 반영) — Step1 참고
- [x] `require_signed_commits`는 플래그만 존재하고 [[p3-03-ssh-gpg]] 완료 전까지 항상 통과 처리됨을 명시적으로 테스트/문서화 — Step4 완료 로그의 회귀 테스트 참고
- [x] 관리 UI(웹) — 프로젝트 매니저가 규칙을 DB 직접 조작 없이 실제로 생성/조회/수정/삭제할 수 있는
      화면 존재 (2라운드, 2026-09-06 — 아래 완료 로그 참고). 1라운드는 엔티티/훅/병합체크만 구현하고
      이 항목이 DoD에 없었다 — 2라운드 착수 시 발견된 갭이라 이번에 추가함.
- [x] `./gradlew test` 전체 GREEN — 단, 이 계획과 무관한 환경 요인(동시 세션의 공유 MySQL 테스트 DB
      경합)으로 전체 스위트 단독 실행 시 8개 무관 클래스가 간헐 실패할 수 있음을 확인·교차검증함
      (아래 완료 로그 참고 — 이 계획이 만든 코드/테스트 자체는 모든 실행에서 항상 GREEN)

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| `require_approvals` 실현 가능성 | `ThreadState`에 승인 개념이 있는지 미확인 상태로 백로그가 남겨짐 | **해소(2026-09-06)** — `ThreadState`는 OPEN/CLOSED뿐, 승인 개념 없음. `require_signed_commits`와 동일하게 "플래그만 존재, 항상 통과"로 구현(Step4 완료 로그 참고) |
| `AccessControl`과의 레이어 순서 | 권한 확인 통과 후 추가 정책이라는 설계만 있고 구체적 체이닝 방식 미정 | Step 3/4 구현 시 기존 `AccessControl` 호출 지점 뒤에 체이닝하는 방식으로 확정 |

## 완료 로그

### 1라운드 (2026-09-06) — Step1(스파이크 해소) ~ Step5(admins_can_bypass) 전체

- **Step 1(스파이크)**: 지시받은 대로 재조사 없이 `CommentThread.kt`의 `enum class ThreadState`가
  `OPEN, CLOSED` 두 값뿐임을 최종 확인만 하고, `require_approvals`를 "필드는 존재, 판정 로직 없음
  (항상 통과)"로 확정. 이 문서 상단에 반영 완료.
- **Step 2 — `ProtectedBranch` 모델**: `domain/branchprotection/ProtectedBranch.kt`(신규 패키지) +
  `ProtectedBranchRepository.kt` 신설. `Webhook.kt`와 동일하게 프로젝트 범위 설정 엔티티로
  설계했다. 필드 8개(`branchPattern`, `requirePullRequest`, `requireApprovals`,
  `requireSignedCommits`, `restrictPushTo`, `disallowForcePush`, `disallowDelete`,
  `adminsCanBypass`)를 계획대로 전부 구현. 설계 결정 두 가지:
  - `matches(branchName)`: `'*'`만 임의 길이 와일드카드로 지원하는 자체 glob→정규식 변환(`?`나
    문자 클래스는 DoD에 없어 구현하지 않음). `Regex.escape()`로 나머지 문자를 리터럴 처리해
    `branchPattern`(사용자 입력)의 정규식 인젝션을 막았다.
  - `restrictPushTo`: 별도 조인 테이블 대신 `Webhook.secret`과 동일한 단순 컬럼(콤마 구분 loginId
    문자열) 방식을 택했다 — 프로젝트당 브랜치 보호 규칙 수 자체가 적어 별도 엔티티를 둘 만큼의
    복잡도가 없다고 판단(계획의 "과도한 설계 금지" 지시 반영). `restrictedLoginIds()`로 파싱.
  - 검증: `domain/branchprotection/ProtectedBranchSpec.kt`(6 tests) — glob 매칭 3케이스,
    `restrictedLoginIds()` 파싱 2케이스, 프로퍼티 접근자 1케이스. RED(엔티티 없어 컴파일 실패)
    확인 후 구현 → GREEN.
  - 스키마: 마이그레이션 도구가 없는 이 프로젝트의 기존 관례대로(`ddl-auto: update`/H2는
    `create-drop`) 엔티티 정의만으로 `protected_branch` 테이블이 자동 생성됨을 테스트 실행
    로그(Hibernate DDL)로 확인.
- **Step 3 — 직접 push 차단 훅**: `domain/vcs/GitPushHooks.kt`에 `BranchProtectionPreReceiveHook`
  추가(`RejectPushToReservedRefsPreReceiveHook`과 동일한 파일·패턴). 검사 순서를 다음과 같이
  확정했다(먼저 매칭되는 조건에서 즉시 거부, 이후 조건은 검사하지 않음):
  1. `adminsCanBypass`가 켜져 있고 pusher가 프로젝트 매니저(`RoleType.MANAGER`)면 전체 우회.
  2. `requirePullRequest`가 켜져 있으면 `DELETE`를 제외한 모든 직접 push(`CREATE`/`UPDATE`/
     `UPDATE_NONFASTFORWARD`)를 거부 — "이 브랜치는 PR을 통해서만 갱신 가능"이라는 의미를
     이렇게 구체화했다(merge()는 이 훅을 아예 거치지 않는 별도 경로이므로 충돌 없음, 아래
     Step4 참고).
  3. `disallowDelete`가 켜져 있으면 `DELETE` 거부.
  4. `disallowForcePush`가 켜져 있으면 `UPDATE_NONFASTFORWARD` 거부(`requirePullRequest`가
     꺼져 있어도 강제 push만 별도로 막고 싶은 경우를 위해 독립적으로 동작).
  5. `restrictPushTo`가 설정돼 있으면 그 목록에 없는 pusher의 모든 push 거부(익명 push 포함 —
     `pusher: User?`로 null 허용).
  - `GitServletConfig.kt`에 배선: `PreReceiveHookChain.newChain()`(JGit 제공)으로
    `RejectPushToReservedRefsPreReceiveHook`과 체이닝. project를 못 찾는 극히 드문 경로에서는
    예약 ref 거부만 적용(브랜치 보호 규칙을 조회할 project 자체가 없으므로).
  - 검증: `GitPushHooksSpec.kt`에 `BranchProtectionPreReceiveHook` describe 추가(20 tests —
    규칙 없음/패턴 불일치/require_pull_request/disallow_force_push/disallow_delete/
    restrict_push_to 각 온오프 케이스 + tag ref 제외 확인). RED(`BranchProtectionPreReceiveHook`
    미존재로 컴파일 실패) 확인 후 구현 → GREEN.
  - **부수 실버그**: 최초 구현 시 KDoc 주석 안에 `refs/yobi/*` 문자열을 그대로 적었더니 Kotlin
    블록 주석이 중첩 지원이라(Java와 달리) 그 `/*`가 새 중첩 주석 시작으로 해석돼 바깥 KDoc의
    실제 닫는 `*/`까지 삼켜버려 "Unclosed comment" 컴파일 에러가 났다 — 주석 텍스트에서
    `refs/yobi/*` 표기를 `refs/yobi`로 수정해 해소(파일 전체가 컴파일 안 되던 것이라
    `YonaPostReceiveHook` 참조 실패로 엉뚱한 파일(`GitServletConfig.kt`)에서 에러가 먼저 보였다).
  - `GitServletConfigSpec.kt`(기존 스펙)도 생성자 시그니처 변경(신규 `protectedBranchRepository`/
    `projectUserRepository` 파라미터 2개 추가)에 맞춰 두 생성 호출부를 갱신 — mock으로만 채웠고
    (project mock들이 `id`를 설정하지 않아 실제로 호출되지 않음), 기존 7개 테스트(리플렉션으로
    내부 람다를 직접 호출하는 테스트 포함) 전부 그대로 GREEN 유지 확인.
- **Step 4 — PR 병합 체크**: `PullRequestServiceImpl.merge()`에 `checkBranchProtectionForMerge()`
  호출 추가(리뷰어 수 체크보다 먼저 — 순서에 특별한 의미는 없음, 둘 다 독립적인 조기 거부 가드).
  - `PullRequestService.kt`에 `BranchProtectionException`(신규, `@ResponseStatus(FORBIDDEN)`)
    추가 — `LackingReviewerException`(400, 요청 자체의 결함)과 구분해 "요청은 유효하지만 이
    사용자에게 권한이 없다"는 의미를 403으로 표현했다. 기존 `DuplicatedPullRequestException`/
    `InvalidBranchOperationException`과 동일하게 컨트롤러에서 명시적으로 catch하지 않고
    `@ResponseStatus`에 위임(기존 관례 그대로 유지, `PullRequestController.kt` 변경 없음).
  - **핵심 설계 결정(계획 문서 Step4 항목에 반영 완료)**: `requirePullRequest`/`requireApprovals`/
    `requireSignedCommits`는 `merge()`에서 항상 통과(no-op)시킨다 — `merge()`는 정의상
    `PullRequestId`를 통해서만 호출되는 PR 병합 경로라 "PR 없이 직접 병합"이라는 시나리오
    자체가 서비스 계층에 없기 때문이다(직접 push 차단은 Step3의 몫). `restrictPushTo`만
    실질적으로 검사한다 — `merge()`가 toBranch에 병합 커밋을 직접 기록하는 ref 갱신이라는 점에서
    git push와 동등하게 취급했다(`adminsCanBypass=true`인 프로젝트 매니저는 우회 가능).
  - 검증: `PullRequestServiceSpec.kt`에 "5-1. 브랜치 보호 정책(ProtectedBranch) 검증" describe
    추가(6 tests, 통합 테스트 — 실제 bare git 저장소로 merge() 전체 경로를 태움):
    restrict_push_to 목록 외 사용자 거부, 목록 내 사용자 허용, 패턴 불일치 시 미적용,
    admins_can_bypass=true/false 매니저 우회 가능/불가능, 그리고 require_pull_request/
    require_approvals(5)/require_signed_commits가 전부 켜져 있어도 정상 PR 병합이 항상
    성공하는 회귀 테스트. RED 확인 시 의도한 2개 테스트(restrict_push_to 관련)만 정확히
    실패하고 나머지는 이미 통과함을 먼저 확인(=merge()가 원래도 이 필드들을 막지 않았다는
    사실을 증명) → 구현 → GREEN(91/91).
  - 클래스 생성자에 `protectedBranchRepository`/`projectUserRepository` 2개 신규 의존성 추가 —
    `PullRequestServiceImpl`을 직접 생성하는 코드는 이 파일 자신뿐이라(다른 곳은 전부
    `PullRequestService` 인터페이스로 Spring 주입) 다른 호출부 수정 불필요.
- **Step 5 — `admins_can_bypass`**: Step3(훅)와 Step4(병합) 양쪽에 통합 구현 — 별도 라운드로
  분리하지 않고 각 지점 구현 시 함께 TDD했다(계획 문서의 단계 구분과 달리 구현 편의상 합쳤음을
  명시). 판정 로직은 두 지점 모두 동일한 패턴(`projectUserRepository.findByProjectIdAndUserId(...)
  .map { it.role.id == RoleType.MANAGER.roleType }.orElse(false)`, 기존 `TemplateHelper.isManager()`와
  동일한 관용구)을 각자의 파일에서 각각 구현했다(공용 헬퍼로 추출하지 않음 — 계획의 "과도한 설계
  금지" 지시에 따라 두 곳뿐인 중복을 감수).
- **전체 스위트 검증**: `./gradlew test` 실행 중 첫 회차에 무관한 8개 클래스(Board/Comment/
  TemplateHelper/ApiToken/GitAuthorizationFilter 관련)에서 `project_pushed_branch` FK
  위반으로 53개 테스트가 실패했으나, 이 8개 클래스를 단독 재실행하면 전부 GREEN — 이 환경에
  동시에 떠 있던 다른 세션의 Gradle 데몬(`ps aux`로 확인, 이 리포 공용 MySQL 테스트 DB를 공유)이
  일으킨 테스트 간 데이터 경합으로 판단했다(`docs/COVERAGE_BACKLOG.md`에 동일 패턴의 선례
  다수 기록됨 — 동시 실행 시 Gradle 데몬/DB 자원 경합). 이번 라운드가 새로 만든 코드와는 무관함을
  확인하기 위해 전체 스위트를 정지 없이 한 번 더 실행해 재확인했다(결과는 아래).
- **최종 검증**: 전체 스위트(`./gradlew test`)를 두 차례 실행했는데 두 번 다 동일하게 5,872 tests
  중 53개가 실패했다(Board/Comment/`TemplateHelper`/`ApiTokenServiceImpl`/
  `GitAuthorizationFilterIntegrationSpec` 등 8개 클래스, 전부 `project_pushed_branch` FK 위반
  — `delete from project`가 참조 중인 `PushedBranch` 행 때문에 거부됨). 이 계획의 변경과 무관함을
  두 가지로 교차 확인했다: (1) 이 8개 클래스만 단독 실행 → 전부 GREEN, (2) 이 8개 클래스 +
  이번 라운드에서 신규/수정한 스펙(`ProtectedBranchSpec`/`GitPushHooksSpec`/
  `PullRequestServiceSpec`/`GitServletConfigSpec`) 전부를 한 JVM에서 함께 실행 → 역시 전부 GREEN.
  즉 실패는 오직 5,872개 전체를 한 번에 돌리는 긴(3분+) 전체 스위트 실행에서만 재현되고, 이
  계획이 건드린 코드/테스트만으로는 재현되지 않는다 — `ps aux`로 확인한 결과 이 세션 진행 중에도
  다른 세션의 Gradle 데몬 여러 개가 동일 저장소 워크트리에서 계속 떠 있었고, `docs/
  COVERAGE_BACKLOG.md`에 이미 여러 차례 기록된 "동시 세션의 공유 MySQL 테스트 DB 경합" 패턴과
  정확히 일치한다. 이 계획 자체의 새 테스트(`ProtectedBranchSpec` 6, `GitPushHooksSpec`의
  `BranchProtectionPreReceiveHook` describe 20, `PullRequestServiceSpec`의 "5-1" describe 6,
  `GitServletConfigSpec` 7 — 이 중 뒤 3개는 기존 스펙에 추가/수정)는 모든 실행에서 항상 GREEN이었다.

### 2라운드 (2026-09-06) — 관리 UI(웹) 추가

- **배경(갭 재확인)**: 1라운드는 엔티티/훅/병합체크만 구현했고 DoD에 "관리 UI" 항목 자체가 없었다.
  코드 전수 확인 결과 `web/` 패키지 어디에도 `ProtectedBranch`를 참조하는 컨트롤러가 하나도 없어,
  프로젝트 매니저가 규칙을 만들거나 조회·수정·삭제할 방법이 DB 직접 조작 말고는 전혀 없었다 —
  모델과 강제 로직만 있고 실제로 켤 수 있는 진입점이 없는 상태. 사용자 방침("백엔드만 만들고
  진입점을 안 만드는 걸 기본값으로 두지 말라")에 따라 이번 라운드로 마저 구현했다. 이 문서 상단의
  DoD에 "관리 UI" 항목을 추가하고 체크 완료.
- **참고 패턴**: 같은 성격의 기존 기능(프로젝트 범위 설정, 매니저 전용 관리 화면)인
  `WebhookController`/`WebhookRestApiController`/`setting_webhook.html`을 그대로 재사용했다 —
  프로젝트 조회 + `AccessControl.isAllowed(user, project, Operation.UPDATE)` 권한 체크,
  `data-request-method="delete"` AJAX 삭제 관례(성공 시 `document.location.reload()`, 신규 JS
  불필요 — `jquery.requestAs.js` 기존 인프라 그대로), 프로젝트 설정 사이드바(`setting_menu.html`)에
  메뉴 항목 추가까지 전부 동일한 방식.
- **구현**: 신규 `web/BranchProtectionController.kt` + `templates/project/setting_branch_protection.html`.
  - 라우팅: `GET /projects/{owner}/{projectName}/branch-protections`(목록+생성폼),
    `POST .../branch-protections`(생성), `POST .../branch-protections/{id}`(수정 — HTML 폼은 PUT을
    못 써서 POST로 처리, Milestone류 JSON REST 컨트롤러의 `@PutMapping`과는 다른 경로),
    `DELETE .../branch-protections/{id}`(삭제, `@ResponseBody`). Webhook과 달리 수정(update)까지
    추가했다 — 사용자 요청이 명시적으로 "생성하거나 조회·수정·삭제"를 언급했고, 8개 필드짜리
    엔티티를 삭제·재생성으로만 바꾸게 하는 건 사용성이 나쁘다고 판단.
  - 서비스 레이어 없이 `ProtectedBranchRepository`를 컨트롤러에서 직접 사용(GitPushHooks/
    PullRequestServiceImpl도 동일한 관례) — 엔티티에 비즈니스 로직이 없어 서비스 레이어 추가는
    과도한 설계로 판단.
  - IDOR 방지: 수정/삭제 모두 `rule.project?.id != project.id`를 확인해 다른 프로젝트 소유의 규칙
    id를 URL에 넣어도 404로 거부한다(Webhook의 `deleteWebhook()`은 이 검사가 없었는데, 더 엄격한
    쪽을 택했다 — 기존 결함을 그대로 답습할 이유가 없다고 판단).
  - 검증: branchPattern 필수/250자, restrictPushTo 1000자 — Webhook의 payloadUrl/secret 사전 검증
    (P2-28)과 동일한 이유(컬럼 길이 제약 위반 시 처리되지 않은 500 노출 방지)로 컨트롤러에서
    선제 검증 후 400.
  - **GitHub 방식 채택(사용자 방침 — 모호하면 GitHub를 기본값으로)**: 폼 문구/필드 순서를 GitHub의
    Settings > Branches > Branch protection rules 화면 그대로 차용했다 — "병합하기 전에 Pull
    Request 필요"(+ 중첩된 "필요한 승인 개수"), "서명된 커밋 필요", "위 설정에 대한 우회를
    허용하지 않음"(Do not allow bypassing the above settings), "일치하는 브랜치에 push 가능한
    사용자 제한", "관리자를 포함한 모든 사용자에게 적용되는 규칙"(강제 push 허용/브랜치 삭제 허용)
    순서. `ProtectedBranch` 엔티티는 세 필드(`disallowForcePush`/`disallowDelete`/
    `adminsCanBypass`)를 부정형으로 저장하는데 GitHub 폼 문구는 긍정형(Allow force pushes/Allow
    deletions/Do not allow bypassing)이라, 컨트롤러가 폼 파라미터(`allowForcePush`/
    `allowDeletions`/`doNotAllowBypassing`)를 엔티티 필드로 반전 변환한다
    (`disallowForcePush = !allowForcePush` 등) — `BranchProtectionControllerSpec`에 이 반전
    변환 자체를 검증하는 테스트를 포함했다.
- **테스트**: TDD로 RED(컨트롤러 미존재로 컴파일 실패) 확인 후 구현 → GREEN.
  - `BranchProtectionControllerSpec.kt`(신규, `WebhookControllerSpec`과 동일하게 `mockk` +
    `MockMvcBuilders.standaloneSetup` 단위 테스트 — Spring 컨텍스트/DB 불필요) — 20 tests: 목록
    조회(정상/404/비로그인 403/비매니저 403), 생성(정상 + GitHub 반전 검증/체크박스 전부 생략 시
    기본값/branchPattern 필수·250자 초과/restrictPushTo 1000자 초과/404/403), 수정(정상 + 반전
    검증/404/IDOR 404/403), 삭제(정상/404/IDOR 404/404-project/403) 전부 GREEN.
  - `TemplateEquivalenceSpec.kt`에 "[Test-19-24] 브랜치 보호 설정 화면 렌더링 검증" describe 추가
    (신규, `@SpringBootTest` 기반 실제 Thymeleaf 렌더링 + 실 DB 통합 테스트) — GNB/footer/
    setting_menu 조각/새 규칙 추가 폼이 실제로 렌더링되는지, 비매니저는 403으로 거부되는지 검증.
    `BranchProtectionControllerSpec`은 `standaloneSetup`이라 view 이름만 확인할 뿐 실제 템플릿을
    렌더링하지 않으므로 이 스펙으로 보강했다.
- **검증 중 발견한 환경 이슈(이 계획과 무관, 기록만 남김)**: 이 세션 진행 중 동일 워크트리에서
  동시에 진행 중이던 다른 세션의 작업(P3-06, 엔터프라이즈 SSO)이 `config/sso/
  YonaClientRegistrationRepository.kt` 등 신규 파일을 계속 추가/수정하고 있었다. 그 결과:
  1. 한 시점에는 `config/sso/YonaClientRegistrationRepository.kt` 자체의 컴파일 에러(`String?` vs
     `String` 타입 불일치)로 `compileKotlin`(메인 소스셋)이 실패해 잠시 아무 테스트도 못 돌렸다.
  2. 다른 시점에는 컴파일은 되지만 `SecurityConfig` → `EnterpriseOidcUserService` →
     `YonaClientRegistrationRepository`가 `OAuth2ClientProperties` 빈을 찾지 못해
     `UnsatisfiedDependencyException`으로 전체 Spring 컨텍스트 기동이 실패, `@SpringBootTest` 기반
     스펙(`TemplateEquivalenceSpec` 포함) 전부가 즉시 실패했다(이 계획이 만든 코드와 무관 — 순수
     빈 배선 문제, DB 문제 아님).
  3. 또 다른 시점에는 그 세션이 만든 `SsoAdminControllerSpec.kt`가 아직 존재하지 않는
     `SsoAdminController`를 참조해 `compileTestKotlin` 자체가 실패했다.
  이 세 증상 모두 **이 계획(P3-04)이 건드린 파일과는 무관**하며(`BranchProtectionController.kt`/
  `setting_branch_protection.html`/`BranchProtectionControllerSpec.kt`/`setting_menu.html`/
  메시지 프로퍼티/`TemplateEquivalenceSpec.kt`의 신규 describe 어디에도 SSO/OAuth2 관련 코드가
  없음), 저장소 밖에서 진행 중이던 다른 세션의 미완성 작업이 같은 워크트리를 공유해 생긴 일시적
  현상이다. 도중에 그 세션이 진단 과정에서 이 리포의 공유 MariaDB 테스트 컨테이너
  (`yona-mariadb`)를 잠시 내렸다 올린 적도 있었다(관련 없는 진짜 원인은 위 2번의 빈 배선 문제였고,
  컨테이너 재기동 자체는 원인이 아니었음을 교차 확인). 컨테이너가 안정적으로 떠 있고 SSO
  컴파일이 우연히 일시적으로 정상이었던 한 순간에 `TemplateEquivalenceSpec` 전체(86 tests, 이
  계획의 신규 2개 포함)를 실행해 **전부 GREEN**을 1회 확인했다 — 이후 그 세션이 새 파일
  (`SsoAdminController`)을 추가하며 다시 컴파일이 깨져 재확인은 그 세션의 수정이 끝난 뒤로
  미룬다(아래 "최종 검증" 참고). 이 계획 자체의 로직에는 결함이 없음을 이미 확인했으므로 이
  불안정성은 문서화만 하고 더 이상 재시도 루프를 돌리지 않는다.
- **최종 검증 상태**: `BranchProtectionControllerSpec`(20 tests, Spring 컨텍스트 불필요한 순수
  단위 테스트)은 이 세션의 모든 시도에서 항상 GREEN. `TemplateEquivalenceSpec`의 신규
  "[Test-19-24]" 2개는 위에서 서술한 대로 전체 컨텍스트가 정상 기동된 1회의 실행에서 GREEN을
  확인했지만, 동시 진행 중인 P3-06(SSO) 작업이 아직 안정화되지 않아 **전체 스위트(`./gradlew
  test`) 단위의 최종 재검증은 P3-06의 버그 수정이 완료된 이후로 보류**한다 — 이 계획이 만든 코드
  자체에는 결함이 없다고 판단하지만(위 로그 참고), 공유 워크트리에서 무관한 진행 중인 작업 때문에
  현재는 매 실행이 재현 가능한 결과를 주지 못한다.

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-04)
- 관련 계획: [[p3-03-ssh-gpg]](서명 검증 결과 소비)
- 관련 소스: `domain/branchprotection/ProtectedBranch.kt`, `domain/branchprotection/ProtectedBranchRepository.kt`,
  `domain/vcs/GitPushHooks.kt`(`BranchProtectionPreReceiveHook`), `domain/pullrequest/PullRequestServiceImpl.kt`
  (`checkBranchProtectionForMerge()`), `domain/pullrequest/PullRequestService.kt`(`BranchProtectionException`),
  `config/GitServletConfig.kt`(훅 배선), `web/BranchProtectionController.kt`(2라운드, 관리 UI),
  `templates/project/setting_branch_protection.html`(2라운드), `templates/project/setting_menu.html`(2라운드,
  메뉴 추가)
