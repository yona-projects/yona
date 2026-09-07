---
type: plan
id: P3-15
title: "PR 승인/변경요청(리뷰 판정) 워크플로 추가"
status: done
priority: 10
depends_on: []
blocks: []
source: docs/parity/tickets/p3-15.md
created: 2026-09-07
updated: 2026-09-07
tags: [plan, p3, git, pull-request, security]
---

# PR 승인/변경요청(리뷰 판정) 워크플로 추가

## 배경

[[p3-04-branch-protection]] 작업 중 `require_approvals`(보호된 브랜치는 승인 N건 이상 있어야
병합 가능)를 실제로 구현하려다 코드 검증 결과 yona에 PR 승인/반려 판정 개념 자체가 전혀 없음을
확인했다 — `domain/pullrequest/CommentThread.kt`의 `enum class ThreadState`는 `OPEN`/`CLOSED`
두 값뿐이고, `PullRequestServiceImpl.addReviewer()`(및 CLI `yona pr review`)도 "본인을 리뷰어로
자기등록"할 뿐 승인·반려를 표시하는 기능이 아니다(원본 legacy yona도 동일 — 완전 신규 기능).
이 갭 때문에 P3-04는 `require_approvals`를 "플래그 필드만 존재, 실제 판정 로직 없이 항상 통과
처리"로 축소해서 구현했다(`docs/yona-wiki/plans/p3-04-branch-protection.md` 1라운드 완료 로그).
원본: [`docs/parity/tickets/p3-15.md`](../../parity/index.md)

## 범위

### 포함
- PR 전체에 대한 판정(Approve/Request changes/Comment) 신규 엔티티 `PullRequestReview`
- REST API(`POST/GET /api/v1/projects/{owner}/{project}/pull-requests/{number}/reviews`)
- PR 상세 화면 UI(리뷰 이력 목록, 승인/변경요청 요약 뱃지, 판정 제출 폼)
- `require_approvals`를 실제 판정과 연결(`PullRequestServiceImpl.checkApprovalsForMerge()`)
- `yona-cli`의 `pr review` 확장(`--approve`/`--request-changes`/`--comment`)

### 제외 (비범위)
- stale 승인 자동 무효화(새 커밋 push 시 기존 승인 무효화) — GitHub도 기본값은 꺼져 있음(옵션으로만
  존재), 과도한 설계 방지
- CODEOWNERS, 승인 규칙 엔진 등 GitHub의 고급 리뷰 기능 — Approve/Request changes/Comment 세
  판정만 구현
- `CommentThread`(코드 라인별 리뷰 스레드) 자체의 변경 — PR 단위 판정은 완전히 별개 엔티티

## 의존성

- **선행 조건**: 없음(순수 신규 도메인 기능)
- **후속 파급**: [[p3-04-branch-protection]]의 `require_approvals`가 이 계획 완료로 실제 의미를
  갖게 됨(P3-04 문서 4라운드 완료 로그 참고)

## 설계 개요 (코디네이터 사전 확정 — 재검토 없이 채택)

1. **PR 단위 별도 엔티티** — `CommentThread`(코드 라인별 리뷰 스레드)에 얹지 않고, GitHub과
   동일하게 "PR 전체에 대한 판정" 개념을 새 엔티티(`PullRequestReview`)로 만든다. 기존 구조가
   이미 "스레드"(코드 리뷰 코멘트)와 "PR 전체 댓글"(`pr comment`)을 구분하고 있어 그 패턴과
   일관된다.
2. **재판정 시 이력 보존** — 같은 리뷰어가 여러 번 판정을 남기면 매번 새 레코드로 추가(타임라인에
   전부 표시, GitHub과 동일). `require_approvals` 등 정책 판단에는 리뷰어별 가장 최근 판정만
   유효하다.
3. **자기 자신의 PR은 자기가 승인할 수 없다** — GitHub 방식 기본값. PR 작성자가 자기 PR에
   Approve/Request changes를 시도하면 거부(에러 응답) — Comment는 자기 PR에도 허용(GitHub도
   코멘트 자체는 막지 않음).
4. **stale 승인 자동 무효화는 만들지 않는다** — 새 커밋이 push돼도 기존 승인은 그대로 유효.
5. **CLI**: `gh pr review` 관례를 그대로 따라 `--approve`/`--request-changes`/`--comment` +
   `--body` 플래그를 추가한다. 기존 "플래그 없이 호출하면 자기 자신을 리뷰어로 자기등록"하던
   동작(`reviewers` 집합에 추가)은 그대로 유지하되, 세 플래그 중 하나가 주어지면 별도의 "판정
   남기기" 액션으로 처리한다(자기등록과 판정은 별개 개념).

## 단계별 작업 계획 (TDD)

1. **Step 1 — `PullRequestReview` 엔티티 + 서비스 계층 (완료)**
   - 실패 테스트: `submitReview()`가 새 판정을 저장하고 `getReviews()`로 조회 가능 → RED → 구현 → GREEN
   - 재판정 이력 보존, `getLatestReviewStates()`(리뷰어별 최신 APPROVE/REQUEST_CHANGES만 반영,
     COMMENT 제외) 검증
   - 자기 자신의 PR에 대한 APPROVE/REQUEST_CHANGES 거부(`SelfReviewException`), COMMENT는 허용
2. **Step 2 — REST API (완료)**
   - `web/PullRequestController.kt`에 `POST/GET .../reviews` 추가, `checkWritePermission`(기존
     `addReviewer`와 동일한 프로젝트 멤버/그룹멤버 권한 수준) 적용
   - `web/PullRequestApiController.kt`에 v1 어댑터 추가 — 기존 "pull-requests" 세그먼트 매핑을
     그대로 재사용해 별도 PAT 스코프 배선 없이 `PULL_REQUESTS` 그룹으로 인가됨을 확인
3. **Step 3 — `require_approvals` 연결 (완료)**
   - 실패 테스트: 승인 부족 시 병합 거부, 최신 판정에 REQUEST_CHANGES가 있으면 승인 개수와
     무관하게 병합 거부, 충분히 승인되면 성공 → RED → `checkApprovalsForMerge()` 구현 → GREEN
   - `admins_can_bypass` 처리는 기존 두 검사(`checkBranchProtectionForMerge`,
     `checkSignedCommitsForMerge`)와 동일한 패턴 적용
4. **Step 4 — UI (완료)**
   - `pullrequest/partial_info.html`: 승인/변경요청 요약 뱃지 + 리뷰어 아바타별 초록 체크/빨간 X
   - `pullrequest/view.html`: 리뷰 이력 목록(판정+본문+시각) + Approve/Request changes/Comment
     제출 폼(자기 PR에는 Approve/Request changes 버튼 숨김)
5. **Step 5 — CLI (완료)**
   - `yona-cli`의 `cmd/pr.go` `newPRReviewCmd()`에 `--approve`/`--request-changes`/`--comment`
     (+`--body`) 플래그 추가, `internal/api/pr.go`에 `SubmitPullRequestReview`/
     `GetPullRequestReviews` 추가

## 완료 기준 (Definition of Done)

- [x] `PullRequestReview` 엔티티 + 리포지토리 + 서비스(`submitReview`/`getReviews`/
      `getLatestReviewStates`) 테스트로 검증
- [x] 자기 자신의 PR APPROVE/REQUEST_CHANGES 거부, COMMENT는 허용 — 테스트로 검증
- [x] 재판정 시 이력 보존(update 아닌 insert), 정책 판단은 리뷰어별 최신 판정만 반영 — 테스트로 검증
- [x] REST API(`POST/GET .../reviews`) 권한/404/자기승인거부(400) 테스트로 검증
- [x] `require_approvals`가 실제 승인/변경요청 판정과 연결됨 — 승인 부족 거부/REQUEST_CHANGES
      무조건 거부/충분한 승인 시 성공 테스트로 검증
- [x] PR 상세 화면에 리뷰 이력/요약 뱃지/판정 제출 폼 존재 — 실제 Thymeleaf 렌더링 통합테스트로 검증
- [x] PAT 스코프 검증(`PULL_REQUESTS` 그룹) 통합테스트로 확인
- [x] `yona-cli`의 `pr review`가 `--approve`/`--request-changes`/`--comment`를 지원 — CLI 테스트로 검증
- [x] `./gradlew test`(이 계획이 만들거나 건드린 클래스)와 `yona-cli`의 `go build/test/vet/gofmt`
      전부 GREEN

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| COMMENT가 정책 판단에 미치는 영향 | GitHub은 Comment 리뷰가 기존 승인/변경요청 상태를 바꾸지 않음 — yona도 동일하게 할지 미정이었음 | **해소**: `getLatestReviewStates()`가 COMMENT 전용 판정을 완전히 제외하고 그 이전 APPROVE/REQUEST_CHANGES를 계속 유효하게 취급(GitHub 방식) — 테스트로 회귀 고정 |
| 판정 권한 범위 | 아무나 판정을 남길 수 있는지, 프로젝트 멤버만인지 미정 | **해소**: 기존 `addReviewer()`와 동일한 `checkWritePermission`(프로젝트 멤버 또는 그룹멤버)을 그대로 재사용 — 새 권한 레벨을 만들지 않음 |
| CLI 자기등록과 판정의 공존 방식 | `pr review`가 플래그 없이도 이미 자기등록 동작을 갖고 있어 새 플래그와 충돌 가능성 | **해소**: `--remove`와 판정 플래그(`--approve`/`--request-changes`/`--comment`)를 상호배타로 검증, 플래그 없으면 기존 자기등록 동작 그대로 유지 |

## 완료 로그

### 1라운드 (2026-09-07) — 전체 구현

- **엔티티**: `domain/pullrequest/PullRequestReview.kt` 신설 — `pullRequest`(`@ManyToOne`),
  `reviewer`(`User`, `@ManyToOne`), `state`(`ReviewState` enum: `APPROVE`/`REQUEST_CHANGES`/
  `COMMENT`), `body`(nullable), `createdDate`. `CommentThread`와 완전히 분리된 신규 테이블
  `pull_request_review`(`PullRequestReviewRepository.kt`).
- **서비스**: `PullRequestService`/`PullRequestServiceImpl`에 `submitReview()`/`getReviews()`/
  `getLatestReviewStates()` 추가.
  - `submitReview()`: 자기 자신의 PR에 대한 APPROVE/REQUEST_CHANGES 시도를 `SelfReviewException`
    (400)으로 거부, COMMENT는 허용. 매번 새 `PullRequestReview` 저장(update 없음). 알림/타임라인
    기록은 기존 `notifyReviewerChanged()`(자기등록/취소용)와 별개로 신규 `notifyReviewed()`를
    추가해 신규 `EventType.PULL_REQUEST_REVIEWED`로 기록(메시지 키
    `notification.type.pullrequest.reviewed`는 전 로케일 파일에 이미 있던 미사용 키를 재사용).
  - `getLatestReviewStates()`: `createdDate` 오름차순으로 순회하며 리뷰어별로 마지막 값을
    덮어써 "가장 최근 판정"을 구한다. COMMENT는 이 계산에서 완전히 제외(GitHub 방식 — Comment는
    기존 승인/변경요청 상태를 바꾸지 않음). 이 알고리즘은 정책 판단(`checkApprovalsForMerge`)과
    UI 표시(`PullRequestViewController`) 양쪽이 동일하게 공유한다.
- **require_approvals 연결**: `PullRequestServiceImpl.merge()`에 `checkBranchProtectionForMerge()`
  바로 다음 `checkApprovalsForMerge()`를 추가 — `rule.requireApprovals > 0`이면 (1) 최신 판정이
  REQUEST_CHANGES인 리뷰어가 하나라도 있으면 승인 개수와 무관하게 무조건 거부, (2) 그렇지 않으면
  APPROVE 개수가 `requireApprovals` 미만이면 거부. `admins_can_bypass`는 기존 두 검사
  (`checkBranchProtectionForMerge`/`checkSignedCommitsForMerge`)와 동일하게 적용. 이로써
  [[p3-04-branch-protection]] 1라운드가 "플래그만 존재, 항상 통과"로 남겨뒀던 갭이 해소됨(P3-04
  문서 4라운드 완료 로그에 상호 참조 추가).
- **REST API**: `web/PullRequestController.kt`에 `POST/GET /api/projects/{projectId}/pullrequests/
  {number}/reviews` 추가 — 권한은 기존 `addReviewer`/`removeReviewer`와 동일한
  `checkWritePermission`(프로젝트 멤버 또는 그룹멤버). `SubmitPullRequestReviewRequest(state,
  body)` DTO, 응답은 `RestApiResponseDto.kt`의 신규 `PullRequestReviewResponse`(엔티티 직접
  반환 시의 순환 직렬화 위험을 피하는 기존 관례 그대로). `web/PullRequestApiController.kt`에 v1
  어댑터(`/api/v1/projects/{owner}/{project}/pull-requests/{number}/reviews`) 추가 — 기존
  `resourceSegmentToResourceType["pull-requests"] = ResourceType.PULL_REQUEST` 매핑을 그대로
  재사용해(P3-02 인프라) 별도 PAT 필터 배선 없이 `PULL_REQUESTS` 스코프 그룹으로 이미 인가됨을
  통합테스트로 확인.
- **UI**: `templates/pullrequest/partial_info.html`에 리뷰어 아바타별 초록 체크(APPROVE)/빨간
  X(REQUEST_CHANGES) 오버레이 배지(`review-verdict-badge`)와 승인/변경요청 요약 뱃지
  (`review-verdict-summary`, 예: "승인 2", "변경 요청 1")를 추가 — 요약 뱃지는 기존
  `project.isUsingReviewerCount` 게이트 밖에 둬서 그 설정과 무관하게 항상 표시되도록 했다(첫
  구현에서 게이트 안에 뒀다가 통합테스트로 렌더링 누락을 발견해 수정 — 완료 로그에 기록해두는
  이유는 이 두 기능이 서로 다른 설정임을 명확히 하기 위함). `templates/pullrequest/view.html`에
  리뷰 이력 목록(`#pr-reviews`, 판정 라벨+본문 마크다운 렌더링+상대 시각)과 Approve/Request
  changes/Comment 제출 폼(`#pr-review-submit`) 추가 — `PullRequestViewController.
  addCommonPrAttributes()`가 `canApproveOrRequestChanges`(PR 작성자 본인이면 false)를 계산해
  자기 PR에는 Approve/Request changes 버튼을 숨기고 Comment만 남긴다. `stylesheets/yobi.css`에
  관련 클래스 추가.
- **CLI(`yona-cli`)**: `internal/api/pr.go`에 `SubmitPullRequestReviewRequest` +
  `SubmitPullRequestReview()`/`GetPullRequestReviews()` 추가. `cmd/pr.go`의
  `newPRReviewCmd()`에 `--approve`/`--request-changes`/`--comment`(+`--body`) 플래그 추가 —
  `gh pr review` 관례 그대로. 두 개 이상의 판정 플래그 동시 지정, `--remove`와 판정 플래그 동시
  지정은 각각 사용자 오류로 거부. Request changes/Comment는 `--body` 필수(GitHub API와 동일 —
  이유 없는 변경요청/코멘트는 의미가 없음), Approve는 선택. 플래그 없이 호출하면 기존 자기등록
  동작(`AddReviewer`) 그대로 유지.
- **테스트(TDD)**:
  - `PullRequestServiceSpec.kt` "5-3. PullRequestReview(승인/변경요청/코멘트 판정) 검증" describe
    신설 — `submitReview`/`getReviews`/재판정 이력 보존/`getLatestReviewStates`(COMMENT 제외
    확인)/자기승인 거부(APPROVE·REQUEST_CHANGES 각각)/자기 PR COMMENT 허용 + 중첩 describe
    "require_approvals 연결(checkApprovalsForMerge) 검증"(승인 부족 거부/승인 충족 성공/
    REQUEST_CHANGES 무조건 거부/admins_can_bypass 우회/requireApprovals=0 항상 통과) 전부 GREEN.
    기존 "5-1"의 "require_pull_request/require_approvals가 켜져 있어도 항상 통과" 테스트는
    `require_approvals`가 더 이상 no-op이 아니므로 그 필드를 빼고 "require_pull_request가..."로
    좁혔다(P3-03 연결 작업이 `require_signed_commits`에 대해 했던 것과 동일한 패턴).
  - `PullRequestControllerSpec.kt`에 `POST/GET .../reviews` describe 신설 — APPROVE 제출 성공
    (201), 자기승인 400, PUBLIC 프로젝트 비멤버 403(인가 우회 방지), 404(프로젝트/PR 없음),
    401(비로그인), 읽기 권한 있으면 이력 조회 가능.
  - `PullRequestApiControllerSpec.kt`에 v1 어댑터 위임 테스트 추가.
  - `ApiTokenScopedIssueAndPullRequestSubpathAuthorizationIntegrationSpec.kt`에 "PR 하위
    경로(reviews)의 스코프 기반 인가" describe 추가 — WRITE 스코프만 POST 통과, READ 스코프도
    GET은 통과, READ만으로는 POST 403.
  - `PullRequestReviewTemplateRenderingSpec.kt` 신규 — 실제 Spring 컨텍스트로 Thymeleaf 렌더링
    검증(승인 판정 시 요약 뱃지/이력 목록/버튼 노출, 자기 PR에는 Approve/Request changes 버튼
    숨김, 리뷰 없을 때 요약 뱃지 미노출).
  - `PullRequestViewControllerSpec.kt`/`PullRequestViewControllerMoreSpec.kt`는 `getReviews`/
    `getLatestReviewStates`가 `addCommonPrAttributes()`에서 항상 호출되므로 공통 `beforeTest`에
    기본값(빈 리스트/빈 맵) stub을 추가해 기존 테스트 전부 회귀 없이 통과하도록 함.
  - `yona-cli`: `cmd/pr_test.go`에 승인/변경요청/코멘트 제출, 본문 필수 검증, 플래그 상호배타
    검증, 자기승인 서버 오류 전달 테스트 추가. `internal/api/pr_test.go`에
    `SubmitPullRequestReview`/`GetPullRequestReviews` 단위 테스트 추가.
- **검증 결과**: `PullRequestServiceSpec`(105), `PullRequestControllerSpec`(91),
  `PullRequestApiControllerSpec`(23), `PullRequestViewControllerSpec`(87),
  `PullRequestViewControllerMoreSpec`(9), `PullRequestReviewTemplateRenderingSpec`(3),
  `PullRequestAssigneeAndLabelTemplateRenderingSpec`(3), `PullRequestMergeResultTemplateRenderingSpec`
  (6), `ApiTokenScopedIssueAndPullRequestSubpathAuthorizationIntegrationSpec`(25) — 전부 단독/
  조합 실행에서 GREEN. `yona-cli`는 `go build ./...`/`go vet ./...`/`gofmt -l .`(무출력)/
  `go test ./...` 전부 클린. 전체 `./gradlew test`(5,971 tests) 1회 실행에서 이 계획과 무관한
  다수 클래스(LdapServiceSpec/UserRepositorySpec/WatchServiceSpec 등, PR과 전혀 관계없는 광범위한
  클래스 포함)에서 "Table 'yona.n4user' doesn't exist" 등 공유 MariaDB 테스트 DB 경합으로 인한
  간헐 실패 139건이 재현됐으나, 이 계획이 만들거나 건드린 클래스는 그 실행에서도 전부(위 목록)
  실패 0건이었고 별도 단독 실행에서도 항상 GREEN임을 교차검증함(`docs/coverage/index.md`에
  이미 기록된 선례와 동일한 공유 인프라 경합 패턴 — 이 계획의 변경과 무관).

## 관련

- 백로그 원본: [`docs/parity/index.md`](../../parity/tickets/p3-15.md)
- 관련 계획: [[p3-04-branch-protection]](이 계획이 신설한 판정 데이터를 `require_approvals`가 소비)
- 관련 소스(서버): `domain/pullrequest/PullRequestReview.kt`,
  `domain/pullrequest/PullRequestReviewRepository.kt`,
  `domain/pullrequest/PullRequestServiceImpl.kt`(`submitReview`/`getReviews`/
  `getLatestReviewStates`/`checkApprovalsForMerge`/`latestDecisiveReviewByReviewer`),
  `domain/pullrequest/PullRequestService.kt`(`SelfReviewException`),
  `domain/enumeration/EventType.kt`(`PULL_REQUEST_REVIEWED`), `web/PullRequestController.kt`
  (`submitReview`/`getReviews`), `web/PullRequestApiController.kt`(v1 어댑터),
  `web/RestApiResponseDto.kt`(`PullRequestReviewResponse`), `web/PullRequestViewController.kt`
  (`addCommonPrAttributes`의 리뷰 관련 모델 속성), `templates/pullrequest/partial_info.html`,
  `templates/pullrequest/view.html`, `static/stylesheets/yobi.css`
- 관련 소스(`yona-cli`): `internal/api/pr.go`(`SubmitPullRequestReview`/`GetPullRequestReviews`),
  `cmd/pr.go`(`newPRReviewCmd`)
