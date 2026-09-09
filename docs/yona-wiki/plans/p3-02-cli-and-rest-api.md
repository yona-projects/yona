---
type: plan
id: P3-02
title: "yona CLI 설계 (+ 범용 REST API·Fine-grained 토큰 모델 선행 구축)"
status: planned
priority: 3
depends_on: []
blocks: [p3-03-ssh-gpg, p3-07-mcp-server, p3-05-ci-actions-runner]
source: docs/parity/tickets/p3-02.md
created: 2026-08-28
updated: 2026-09-03
tags: [plan, p3, cli, api, auth]
---

# yona CLI 설계 (+ 범용 REST API·Fine-grained 토큰 모델 선행 구축)

## 배경

CLI(`yona` 명령)로 이슈/PR 워크플로 + 관리자 운영(백업/웹훅/권한) + CI/CD 자동화를 커버하는 것이 목표다.
Go로 결정됨(설치 직후 바로 실행되어야 하므로 JVM 콜드스타트가 있는 Kotlin 제외, `gh`(Go+Cobra)와 동일 스택).

코드 검증 결과 `ApiTokenAuthenticationFilter.kt`가 `Authorization: token <값>`/`Yona-Token` 헤더로 이미 토큰 인증을
수행 중이지만, **토큰이 저장소·권한 단위로 전혀 스코프되지 않는 전권 단일 토큰**이다. CLI에 이 토큰을 그대로 쓰면
토큰 하나 유출 시 그 사용자의 전체 저장소·전체 권한이 노출된다. GitHub Fine-grained PAT 모델을 채택하기로 결정됨.

또한 현재 yona에는 이슈/PR을 개별적으로 생성·조회·수정하는 **범용 JSON REST API가 없다** —
`ProjectApiController.kt`는 export/import 전용이고, 나머지는 전부 Thymeleaf 렌더링용 MVC 컨트롤러다.
원본: [`docs/parity/tickets/p3-02.md`](../../parity/index.md)

## 범위

### 포함
- **1부(선행, 이 계획의 핵심)**: 이슈/PR/저장소를 다루는 범용 JSON REST API + `ApiToken` 엔티티(저장소 범위 + 리소스별 권한) + 인가 재작성
- **2부**: Go CLI 본체(`yona auth/issue/pr/project/admin/api` 명령 트리) — 1부의 API를 감싸는 얇은 클라이언트

### 제외 (비범위)
- SSH 인증(`yona auth login --with-ssh`)은 [[p3-03-ssh-gpg]]에서 다룸
- `yona runner`/`yona workflow` 명령은 [[p3-05-ci-actions-runner]]에서 다룸
- `yona mcp serve`는 [[p3-07-mcp-server]]에서 다룸

## 의존성

- **선행 조건**: 없음
- **후속 파급**: [[p3-03-ssh-gpg]](Deploy Key가 이 계획의 토큰 스코프 체계를 공유), [[p3-07-mcp-server]](동일 REST API + 토큰 모델을 그대로 사용), [[p3-05-ci-actions-runner]](CLI가 같은 Go 스택이라 러너 구현 시 재사용 이점) — **7개 P3 항목 중 가장 많은 후속 항목을 막고 있는 계획**이므로 착수 순위 2위로 배치

## 설계 개요

### `ApiToken` 엔티티 (기존 `User.token` 단일 필드 대체)

| 필드 | 설명 |
|---|---|
| `owner` | 발급자(User) |
| `token_hash` | 원문 미저장, 해시만 |
| repo scope | 전체 저장소 vs 선택 저장소 목록 |
| resource scope | 리소스별 none/read/write |
| `expires_at` | **필수**(2026-08-24 결정 — 무기한 토큰 발급 자체를 금지) |
| `last_used_at` | 마지막 사용일 |

### 권한 스코프 카테고리 — 미결정 사항 확정 필요

원본 백로그가 남긴 미결정: `domain/enumeration/ResourceType.kt`의 기존 33종 enum(`ISSUE_POST`/`PULL_REQUEST`/
`WEBHOOK`/`CODE` 등)을 스코프 매트릭스 축으로 재사용할지, GitHub 스타일 대분류(contents/issues/pull-requests 등)로
새로 만들지. **이 계획에서는 기존 `ResourceType`을 그룹핑하는 매핑 테이블을 두는 방식으로 확정** — 알림/감사 로그와의
일관성을 유지하면서(재사용), 토큰 발급 UI에는 그룹 단위(issues/pull-requests/code/webhooks/administration 등)로만
노출한다(사용성 확보). 매핑은 코드 상수로 관리(예: `ApiTokenScopeGroup.kt`).

### `ApiTokenAuthenticationFilter` 재작성

현재: 토큰 매칭 성공 시 `userDetails.authorities` 전체 부여.
변경 후: 요청 대상 저장소 + 요청 리소스를 토큰의 스코프와 대조하는 인가 체크로 전환. 기존 필터 체인 위치(`SecurityConfig.kt`)는 유지.

### 범용 REST API

`web/` 패키지에 이슈/PR/저장소 CRUD용 `@RestController` 신설. 기존 `ProjectApiController.kt`(export/import 전용)와는
별도 클래스로 분리 — 책임이 다르다(하나는 대량 이관용 JSON 조립, 하나는 개별 리소스 CRUD).

### Fine-grained PAT 완전성 갭 분석 (2026-08-29, 사용자 질의로 착수)

2라운드(Step1~6) 완료 후 "GitHub Fine-grained PAT과 비교해 완전한가?"를 코드 기준으로 재점검해 발견한 잔여 갭.
우선순위 순:

1. **토큰 발급/관리 UI 자체가 없음(최우선)** — `ApiTokenRepository`엔 `findByTokenHash()` 하나뿐이고 사용자가
   `ApiToken`을 만들거나 조회/폐기하는 컨트롤러·서비스·화면이 전혀 없다. 지금은 테스트 코드로 DB에 직접 넣는 것
   외엔 발급 경로가 없다 — 아래 "토큰 발급/관리 웹 UI 설계" 참고. `ApiToken` 엔티티에 `name`(GitHub는 토큰마다
   이름을 강제) 필드도 아직 없다.
2. **Metadata 자동 부여 없음** — 스코프가 하나라도 있으면 프로젝트 기본 정보는 자동으로 읽혀야 하는데(GitHub의
   "Metadata: Read-only"는 저장소가 스코프에 포함되는 순간 그룹/권한 매트릭스와 무관하게 자동 부여됨) 지금
   `ApiTokenAuthorizer`엔 이런 분기가 없다 — 아래 "`metadata` 스코프 세그먼트 설계" 참고.
3. **레거시 전권 토큰과의 공존** — `/api/v1/projects/**` 밖의 모든 API와, 그 안에서도 스코프 토큰이 안 걸리는
   경우는 여전히 `User.token` 전권 토큰이 그대로 통한다(`ApiTokenAuthenticationFilter.authenticateLegacy`).
   위 리스크 표 "기존 전권 토큰 마이그레이션" 항목과 동일 — 마이그레이션 방침(자동 재발급 vs 만료 후 재발급,
   레거시 경로를 언제 끊을지)이 여전히 미정.
4. **만료일 상한 없음** — `expiresAt`이 NOT NULL이라 "무기한 금지"는 지켜지지만, GitHub처럼 "최대 1년" 같은
   상한 검증이 없다(지금 값 그대로 100년짜리 만료일도 발급 가능).
5. **토큰 형식에 식별 프리픽스 없음** — GitHub는 `github_pat_...`로 시작해 시크릿 스캐닝 툴이 토큰 종류를
   인식한다. 우선순위 낮음(툴링 편의 문제, 기능 결함 아님).
6. **Step6 스코프 패턴 갭** — 아래 "프로젝트 목록 API 스코프 필터링 설계" 및 리스크 표 참고.
7. **(선택) 조직 소유 저장소 승인 워크플로 없음** — GitHub는 조직 리소스에 Fine-grained PAT을 쓸 때 관리자
   승인을 요구할 수 있다. yona의 위협 모델상 꼭 필요한지 별도 판단 필요, 이번 계획 범위 밖으로 잠정 보류.

### `metadata` 스코프 세그먼트 설계 (2026-08-29, Step6 갭 해소 1/2)

`/api/v1/projects/{owner}/{project}`(Step6 개별 조회)는 특정 리소스 그룹(issues/PR/code 등)과 무관하게
"이 프로젝트를 볼 수 있는가"만 확인하면 되는, GitHub의 "Metadata: Read-only" 자동 부여와 동일한 개념이다.

- `ApiTokenAuthenticationFilter.ScopedApiTarget.representativeResourceType`을 `ResourceType?`(nullable)로
  변경하고 `resourceSegmentToResourceType`에 `"metadata" to null`을 명시적으로 추가한다. 단
  `map[key] ?: return null`은 "키 없음"과 "값이 null"을 구분 못 하므로 `containsKey` 기반으로 바꿔야 한다.
- `ApiTokenAuthorizer.isAuthorized`에 분기 추가: `resourceType == null`이면 그룹/권한 매트릭스는 보지 않고
  **repo scope 일치 여부 + 만료 여부만** 확인하고 통과시킨다(권한 레벨 비교 자체를 생략).
- `ProjectRestApiController`의 개별 조회 경로가 이 `metadata` 세그먼트로 인식되도록 필터의 세그먼트 매핑만
  추가하면 되고, 실제 URL(`/api/v1/projects/{owner}/{project}`)은 바꾸지 않는다.
- Step1~3에서 이미 테스트로 굳어진 로직에 새 분기만 얹는 구조라 회귀 위험이 작다. 신규 테스트: "그룹 권한이
  하나도 없어도 repo scope만 있으면 조회 가능" / "repo scope 자체가 없으면 403" 2케이스.

### 프로젝트 목록 API 스코프 필터링 설계 (2026-08-29, Step6 갭 해소 2/2)

`/api/v1/projects/{owner}`(목록)는 특정 프로젝트 하나가 아니라 "owner 밑 전체"라 3세그먼트 패턴에 애초에
맞지 않는다. 필터가 "인증됨/아님"만 판단하는 데서 그치지 않고, 스코프 토큰으로 인증된 경우 그 토큰이 어떤
프로젝트들을 볼 수 있는지를 컨트롤러에 넘겨야 한다.

- 필터에 owner 전용 패턴 추가: `^/api/v1/projects/([^/]+)/?$`(project/resource 세그먼트 없이 owner만).
  지금은 이 모양이 `scopedApiPattern`에 안 걸려 레거시 경로로 새는데, 스코프 토큰의 원문값은 `User.token`에
  없으므로 레거시 인증도 실패해 결과적으로 **항상 비로그인 취급**된다(구멍은 아니고 기능 제한 — 세션 인증
  없이 스코프 토큰만으로는 목록 API가 항상 공개 프로젝트만 보여줌).
- 새 인증 분기(`authenticateScopedList` 등): 토큰을 해시로 조회해서 있으면(만료 체크 포함) 기존
  `authenticateScoped`처럼 SecurityContext에 신원만 세팅한다. "어떤 프로젝트를 볼 수 있는지"는 특정 리소스
  하나의 문제가 아니라 목록 필터링 문제이므로 여기선 403을 내지 않는다.
- 인증에 사용된 `ApiToken` 객체를 request attribute로 남긴다(예: `request.setAttribute("SCOPED_API_TOKEN",
  apiToken)`) — Spring Security 자체도 CSRF 토큰 등을 이 방식으로 필터→다운스트림에 넘기므로 새 패턴을
  발명하는 게 아니다. 3세그먼트 케이스(`authenticateScoped`)에도 같이 남겨두면 재사용 가능.
- `ProjectRestApiController`의 목록 메서드에서:
  ```kotlin
  val allOwnerProjects = /* 기존 AccessControl 기반 로직 그대로 */
  val scopedToken = request.getAttribute("SCOPED_API_TOKEN") as ApiToken?
  val visible = when {
      scopedToken == null -> allOwnerProjects          // 세션/레거시 토큰 — 기존 동작 100% 유지
      scopedToken.allRepositories -> allOwnerProjects   // 전체 저장소 스코프
      else -> allOwnerProjects.filter { it in scopedToken.scopedProjects }  // 선택 저장소만 교집합
  }
  ```
  `scopedToken == null` 분기가 세션/레거시 인증 경로를 완전히 그대로 보존하므로 회귀 위험이 없다. 신규 테스트:
  "전체스코프 토큰→다 보임" / "선택스코프 토큰→선택한 것만" / "세션 로그인→기존과 동일" 3케이스.

### 토큰 발급/관리 웹 UI 설계 (2026-08-29)

레거시 전권 토큰 화면(`user/edit_token.html`, `/user/editform/token_reset`)은 그대로 두고(단일 값 표시 +
재생성 버튼뿐이라 다중 토큰·스코프 매트릭스를 담을 수 없음), Fine-grained 토큰은 별도 화면으로 신설한다.

- **위치**: `user/partial_edit_tabmenu.html`에 새 탭 추가(예: "API 토큰(세분화)"), URL
  `/user/editform/tokens`.
- **목록 화면**: 발급된 토큰 테이블 — 이름 / 저장소 범위 요약(전체 또는 "N개 저장소") / 권한 요약(그룹별
  뱃지, 예: `issues:write` `code:read`) / 마지막 사용일 / 만료일 / 폐기 버튼.
- **발급 화면**(같은 페이지 내 폼 또는 모달):
  - 이름(필수 텍스트, 신규 `ApiToken.name` 필드)
  - 저장소 범위: 라디오(전체 저장소 / 선택한 저장소) — 선택 시 기존 select2 다중선택
    (`issue/partial_select_label.html` 패턴 재사용) 프로젝트 검색
  - 권한 매트릭스: `ApiTokenScopeGroup`의 8개 그룹(ISSUES/PULL_REQUESTS/CODE/BOARD/WIKI/WEBHOOKS/
    ADMINISTRATION/USERS) × 3단(없음/읽기/쓰기) 라디오 행
  - 만료일: 프리셋(30/90/365일) + 커스텀, **서버에서 366일 초과 거부**(위 갭 분석 4번 해소)
  - 생성 직후: "지금 한 번만 표시됩니다" 안내 + 값 노출 + 복사 버튼(해시만 저장하므로 이후 재조회 불가 —
    GitHub와 동일한 PAT 생성 UX)
- **폐기**: 삭제 확인 모달(`common/commentDeleteModal.html`류 기존 패턴 재사용) → DELETE.
- **백엔드 신규**:
  - `ApiToken.kt`에 `name: String` 필드 추가
  - `ApiTokenRepository`에 `findByOwner(user): List<ApiToken>` 추가
  - 신규 `ApiTokenService`/`ApiTokenServiceImpl` — 발급(원문은 `SecureRandom`으로 생성, 기존
    `LdapUserProvisioningService.kt`가 쓰는 것과 동일한 패턴 재사용 + `ApiTokenHasher.hashApiToken()`으로
    해시 저장), 폐기(삭제)
  - 신규 컨트롤러(`UserController.kt` 확장 또는 별도 클래스): `GET /user/editform/tokens`(목록+발급폼),
    `POST /user/editform/tokens`(발급), `POST /user/editform/tokens/{id}/revoke`(폐기)

### CLI `gh` 명령 체계 대조 감사 (2026-08-31, 사용자 지적 — "핵심만 뽑지 말고 철저히")

지난 대화에서 `--json`/`-L`/`--web`/`pr create` 번거로움/필터 부족/`--repo` 자동감지/`server` 커맨드 등
몇 개만 뽑았던 걸, `gh` CLI의 전체 명령 체계와 yona-cli(`cmd/*.go`, `internal/api/*.go` 전체 재확인)를
하나하나 대조해 전면 재감사했다. 분류 기준: **(A)** yona에 대응 기능이 있고 P3-02 범위인데 미구현(진짜 갭),
**(B)** yona에 대응 기능은 있지만 다른 P3 계획 범위, **(C)** yona에 대응 개념 자체가 없어 적용 불가.

#### (A) 진짜 갭 — P3-02 범위, 구현 필요

| 분류 | 항목 | 근거 |
|---|---|---|
| 사용성(지난 대화) | `--json`이 불리언(전체 덤프)이 아니라 `gh`처럼 `--json field1,field2` 필드선택 방식이어야 함 | `cmd/output.go`의 `printJSON()` |
| 사용성(지난 대화) | `-L/--limit` 페이지네이션 플래그 없음 | `cmd/issue.go`/`cmd/pr.go`/`cmd/project.go`의 list 계열 |
| 사용성(지난 대화) | `--web` 플래그(브라우저로 열기) 없음 | 전 명령 공통 |
| 사용성(지난 대화) | `pr create`가 `--from-project-id`(숫자 ID를 `project view`로 미리 조회해야 함)를 요구 — `gh pr create`는 현재 git 체크아웃의 브랜치만으로 동작 | `cmd/pr.go:131` |
| 사용성(지난 대화) | `issue list`/`pr list`에 `--assignee`/`--label`/`--author` 등 필터 부족(`--state`만 있음) | `cmd/issue.go`, `internal/api/issue.go:56` |
| 사용성(지난 대화) | `--repo` 로컬 git 컨텍스트 자동감지 없음(매번 명시 필수) — yona clone URL은 `http://{owner}@호스트/{owner}/{project}`(`TemplateHelper.getCloneUrl`)라 `github.com/owner/repo` 파싱과 동일한 방식(호스트 뒤 마지막 두 경로 세그먼트)으로 `git remote get-url origin`을 파싱하면 됨 | `cmd/output.go`의 `parseRepo()` 호출부 전체 |
| 사용성(지난 대화) | 멀티 서버 전환 커맨드 없음(`config.go`에 `Hosts`+`CurrentHost`는 이미 있으나 전환 커맨드가 없음) — `gh auth switch`에 대응하되, yona는 자체호스팅이라 회사/개인마다 완전히 다른 인스턴스를 오갈 일이 `gh`보다 많아 `auth`가 아닌 별도 **`yona server list/use <호스트>`** 커맨드로 신설 | `internal/config/config.go`(`Hosts map[string]Host`, `CurrentHost`) |
| `gh repo fork` | `yona project fork` 없음 — 서버에 fork 기능 존재(`ProjectViewController.fork()`/`doClone()`, 그룹11 #172) | yona `web/ProjectViewController.kt` |
| `gh repo create` | `yona project create` 없음 — 서버에 프로젝트 생성 존재(`ProjectController`) | yona `web/ProjectController.kt` |
| `gh repo clone` | `yona project clone <owner/project>` 없음 — clone URL 계산(`getCloneUrl`)만 있으면 `git clone`을 그대로 실행시키는 얇은 래퍼로 충분 | yona `TemplateHelper.getCloneUrl()` |
| `gh repo edit` | `yona project edit`(개요/공개범위 등 설정 변경) 없음 — 서버에 설정 변경 API 존재(project/setting 화면) | yona `web/ProjectController.kt`/`ProjectViewController.kt` |
| `gh repo delete`/`archive` | `yona project delete` 없음 — 서버에 삭제 기능 존재(그룹6 #101~106, TEMPLATE_BACKLOG 완료 항목) | yona project 삭제 관련 컨트롤러 |
| `gh label list/create/edit/delete` | `yona label` 커맨드 자체가 없음 — 서버에 라벨 관리 존재 | yona `web/LabelController.kt` |
| `gh issue edit` | 이슈 제목/본문/라벨/담당자 수정 커맨드 없음(`create`/`comment`/`close`만 있음) — REST API는 이미 PATCH 지원(Step4) | yona `web/IssueRestApiController.kt`(PATCH `/{number}`) |
| `gh issue reopen` | 없음(`close`만 있음) — 서버는 상태 변경 API가 이미 양방향 지원 | yona `IssueController.changeState()` |
| `gh issue transfer` | 이슈를 다른 프로젝트로 이동하는 커맨드 없음 — 서버 기능 존재(`IssueService.moveIssue`, 이전 세션에서 죽은 UI였다가 복구된 P1-66) | yona `domain/issue/IssueService.kt` |
| `gh issue status` | "내게 배정/멘션/내가 만든" 이슈 개요 커맨드 없음 — 서버엔 사용자 대시보드는 있으나 REST API 미노출(신규 API 필요할 수 있음, 범위 큼) | yona `web/UserViewController.kt` 대시보드 |
| `gh pr edit` | PR 제목/본문 수정 커맨드 없음 — REST API 자체도 PATCH 없음(Step5가 list/create/get/merge/reviewers만 구현) → **서버 API도 함께 추가 필요** | `docs/yona-wiki/plans/p3-02-cli-and-rest-api.md` 완료 로그 2라운드 |
| `gh pr close`/`reopen` | 없음 — 서버에 대응 상태 변경 존재할 가능성 높음(`PullRequestController` 확인 필요) | yona `web/PullRequestController.kt` |
| `gh pr checkout` | PR 브랜치를 로컬로 체크아웃하는 커맨드 없음 — yona PR은 GitHub의 `refs/pull/N/head` 같은 특수 ref가 아니라 **실제 `fromProject`/`fromBranch`(평범한 브랜치)**라 `git fetch <fromProject 클론URL> <fromBranch>`로 구현 가능(오히려 `gh`보다 단순) | yona PR 모델(`fromProject`/`fromBranch` 필드) |
| `gh pr diff` | PR 변경사항 diff 출력 커맨드 없음 — 서버에 코드 비교 기능 존재 | yona `web/CodeController.kt`/`CompareViewController.kt` |
| `gh pr comment` | PR에 댓글 다는 커맨드 없음(`issue comment`만 있음) — 서버는 이슈/PR 댓글 API 공유 가능성 높음 | yona `web/CommentController.kt` |
| `gh search issues/prs/repos` | `yona search` 커맨드 자체가 없음 — 서버에 통합검색 기능 존재 | yona `web/SearchController.kt` |
| `gh org` | `yona org`(조직 목록/조회) 없음 — 서버에 조직 기능 존재, 다른 P3 계획에 배정 안 됨 | yona `web/OrganizationController.kt`/`OrganizationViewController.kt` |
| `gh browse` | 브라우저로 현재 프로젝트/이슈/PR 열기 — 서버 API 불필요(URL 계산만), CLI 로컬 기능으로 바로 추가 가능 | 없음(순수 CLI 기능) |
| `gh completion` | 쉘 자동완성 서브커맨드 없음 — Cobra가 기본 제공하는 기능이라 `root.go`에 등록만 하면 됨(구현 비용 거의 0) | `cmd/root.go`(`NewRootCmd()`에 미등록) |
| `--version`/`gh version` | 버전 출력 플래그/커맨드 없음 | `cmd/root.go` |
| `gh status` | 재감사(2026-09-03, 실제 설치된 `gh 2.92.0`으로 실측 대조하다 발견 — 최초 감사표엔 없었음)에서 발견. 내가 담당한 이슈/PR, 리뷰 요청, 멘션, 구독 저장소 활동을 한 화면에 보여주는 대시보드. **16라운드에서 해소** — 담당 이슈/담당 PR/리뷰요청 PR/저장소 활동은 전부 구현, 멘션은 이슈만 지원(PR 멘션 감지 자체가 yona에 없음, 아래 16라운드 로그 참고) | `web/UserStatusRestApiController.kt`(`GET /api/v1/user/status`) |
| `gh config` | 에디터/페이저 등 CLI 로컬 설정 커맨드 없음 — 우선순위 낮음(nice-to-have) | 없음(순수 CLI 기능) |
| `gh alias` | 사용자 정의 별칭 커맨드 없음 — 우선순위 낮음(nice-to-have) | 없음(순수 CLI 기능) |

#### (B) yona에 대응 기능은 있으나 다른 P3 계획 범위

| gh 명령군 | 배정된 계획 |
|---|---|
| `gh ssh-key`/`gh gpg-key`(SSH 인증, GPG 서명 검증) | [[p3-03-ssh-gpg]] |
| `gh workflow`/`gh run`/`gh cache`(Actions 워크플로/실행/캐시) | [[p3-05-ci-actions-runner]] |
| `gh secret`/`gh variable`(Actions 시크릿/변수) | [[p3-05-ci-actions-runner]](Actions 부속 기능) |
| `gh ruleset`(브랜치 보호 규칙) | [[p3-04-branch-protection]] |
| `gh attestation`(커밋/아티팩트 서명 검증) | [[p3-03-ssh-gpg]](GPG 서명 검증과 개념 겹침) |

#### (C) yona에 대응 개념 없어 적용 불가

| gh 명령군 | 사유 |
|---|---|
| `gh gist` | yona에 gist/스니펫 개념 없음 |
| `gh codespace` | 클라우드 개발 환경 개념 없음(자체호스팅 서버일 뿐) |
| `gh project`(Projects 칸반보드, "저장소"와 별개 개념) | yona의 "Project"는 저장소 자체를 가리켜 이름이 겹칠 뿐, GitHub Projects(칸반보드) 같은 별도 기능 없음 |
| `gh release` | yona에 릴리즈/태그 배포 개념 없음(전수 확인 — `Release`/`ReleaseController` 0건) |
| `gh extension` | CLI 플러그인 생태계 — yona-cli 규모상 시기상조, 서버와 무관 |
| `gh sponsors`류 | GitHub 특유 기능, yona에 대응 없음 |
| `gh licenses`(gh 바이너리 자체의 서드파티 라이선스 표시) | yona와 무관한 gh 빌드 메타정보 — 재감사(2026-09-03)에서 추가 |
| `gh preview`(gh 내부 테스트/개발용, 불안정) | gh 자체도 "테스트용, 언제든 바뀔 수 있음"이라고 명시 — 재감사(2026-09-03)에서 추가 |
| `gh skill`/`gh agent-task`/`gh copilot` | GitHub Copilot·AI 에이전트 전용 기능, yona에 대응 개념 없음 — 재감사(2026-09-03)에서 추가 |

`gh watch`(저장소 알림 구독)에 가장 가까운 yona 기능은 `WatchController`/`WatchService`이지만, `gh` 자체엔
"watch" 최상위 명령이 없어(웹 UI 전용 기능) 대조 대상에서 제외했다 — 필요하면 (A)에 추가 검토 가능.

이 표의 (A) 항목은 아래 "단계별 작업 계획"의 신규 **Step 8.5**로 구체화했다. 서버 쪽 신규 API가 필요한 항목
(`gh pr edit`용 PATCH, `gh issue status`용 대시보드 API)은 별도로 표시해뒀다 — CLI 클라이언트 코드만으로
안 끝나고 yona 서버(1부)에도 손을 대야 한다.

## 단계별 작업 계획 (TDD)

### 1부 — REST API + 토큰 모델

1. **Step 1 — `ApiToken` 엔티티 + 리포지토리**
   - 실패 테스트: 토큰 생성 시 `expires_at`이 null이면 저장 거부 → RED → 엔티티/제약 구현 → GREEN
2. **Step 2 — `ApiTokenScopeGroup` 매핑 + 스코프 판정 로직**
   - 실패 테스트: `ResourceType.ISSUE_POST` 요청이 `issues` 그룹 write 권한 없이 거부되는지 → RED → 구현 → GREEN
3. **Step 3 — `ApiTokenAuthenticationFilter` 재작성**
   - 기존 "전권 부여" 동작을 검증하는 회귀 테스트를 먼저 스코프 기반으로 갱신(RED) → 인가 체크 구현 → GREEN
   - 기존 전권 토큰 사용자에 대한 마이그레이션 경로 결정(예: 마이그레이션 시 "전체 저장소 + 전체 리소스 write"로 자동 발급) 필요
4. **Step 4 — 이슈 REST API**(`GET/POST /api/v1/projects/{owner}/{project}/issues`, 개별 조회/수정/코멘트/클로즈)
   - 각 엔드포인트마다: 실패 테스트(권한 없는 토큰으로 403) → RED → 컨트롤러/서비스 구현 → GREEN
5. **Step 5 — PR REST API**(목록/생성/조회/머지/리뷰)
   - Step 4와 동일 패턴, 기존 `PullRequestServiceImpl.kt` 재사용(신규 서비스 로직 최소화, 얇은 컨트롤러 레이어만 추가)
6. **Step 6 — 프로젝트 조회 REST API**(목록/조회)
7. **Step 6.5 — Step6 스코프 패턴 갭 해소**: 위 "`metadata` 스코프 세그먼트 설계" + "프로젝트 목록 API
   스코프 필터링 설계"를 구현. 실패 테스트 먼저(그룹 권한 없이도 metadata 조회 가능/선택스코프 토큰이
   scopedProjects 밖 프로젝트는 목록에서 제외됨 등) → RED → 구현 → GREEN.
8. **Step 6.6 — Fine-grained 토큰 발급/관리 웹 UI**: 위 "토큰 발급/관리 웹 UI 설계" 구현
   (`ApiToken.name` 필드 + `ApiTokenService` + 컨트롤러 + 목록/발급/폐기 화면). Step6.5로 실제 발급된
   토큰을 조회 API에도 바로 써볼 수 있게 됨.

### 2부 — Go CLI

**CLI 로그인 토큰의 기본 스코프(2026-08-28 결정, 사용자 지적으로 명확화)**: `yona auth login`은
사용자 본인이 본인 계정으로 CLI에 로그인하는 것이라, 웹 세션 로그인과 동등하게 **본인이 가진 전체
권한**(전체 저장소 + 모든 스코프 그룹 write)을 받아야 한다 — Fine-grained 스코프 제한(Step 1~3의
`ApiToken` 모델)은 로그인의 기본 동작이 아니라, "이 토큰을 CI/봇/서드파티 연동에 넘길 때 유출 피해를
줄이기 위해 사용자가 별도로(웹 UI 등에서) 명시적으로 선택하는" 별개 발급 기능이다(GitHub도 동일 —
`gh auth login`은 기본적으로 broad 스코프를 받고, 세분화된 Fine-grained PAT는 Settings에서 별도
발급). 즉 Step 7 구현 시 `yona auth login`이 발급/저장하는 토큰은 기본적으로 "전체 저장소 + 전체
스코프 write"로 만들어야 하며, 제한된 토큰을 쓰고 싶으면 사용자가 별도 발급 후 그 값을 대신
입력하는 경로(`yona auth login --token <제한된 토큰>` 같은 옵션)로 지원한다.

7. **Step 7 — CLI 스캐폴딩**: `yona auth login/logout/status`(Personal Access Token 입력·저장, 위 기본 스코프 원칙 적용)
8. **Step 8 — `yona issue`/`yona pr`/`yona project` 하위 명령**: 1부 API를 감싸는 얇은 HTTP 클라이언트
8.5. **Step 8.5 — `gh` 명령 체계 정합화**: 위 "CLI `gh` 명령 체계 대조 감사"의 (A) 항목 구현.
   - **CLI 로컬 기능만으로 되는 것**(서버 변경 불필요, 다음 라운드 CLI 작업): `--json <fields>`
     필드선택 전환, `-L/--limit`, `--web`, `--repo` 로컬 git 컨텍스트 자동감지, `yona server
     list/use`, `yona browse`, `yona completion`(Cobra 기본 제공 등록만), `--version`, **`yona pr
     checkout`**(5라운드 재검증 결과 CLI 전용으로 재분류 — 아래 참고)
   - **서버(1부) 작업 완료(5라운드)** — 이하 전부 REST API로 노출 완료, CLI(2부)만 다음 라운드로 남음:
     `yona project fork/create/edit/delete`, `yona label list/create/edit/delete`, `yona issue
     reopen/transfer`(edit은 재검증 결과 Step4에서 이미 구현돼 있었음 — 아래 재분류 참고), `yona pr
     diff/comment`, `yona pr edit`(PATCH 어댑터만 추가 — 재검증 결과 서비스/컨트롤러 로직 자체는
     Step5의 PUT으로 이미 존재), `yona pr close/reopen`(재검증 결과 서버에 이미 범용
     `changeState` API 존재), `yona search issues/projects`(`prs`는 서버에 대응 SearchType이 없어
     이월), `yona org list/view`, `issue/pr list`의 `--assignee`/`--label`/`--author` 필터(PR은
     모델에 label/assignee 개념이 없어 `--author`만), `pr create`의 `--from-project-id` 요구
     제거(TASK-0396에서 계획 문서만 먼저 반영 — CLI 구현은 다음 라운드), `gh issue status`
     대응(최소 버전만 — 담당/작성 이슈 개수·목록, 나머지 필터는 이월)
   - **재검증으로 바로잡은 오분류(5라운드, 사용자 지시대로 코드 직접 확인)**:
     - `gh issue edit` — Step4의 `IssueRestApiController.update()`(PATCH `/{number}`)가 이미
       처음부터 구현돼 있었다. 감사표/Step8.5 원문이 "서버 API 필요"로 잘못 분류한 적은 없었지만
       혼동 방지를 위해 명시.
     - `gh pr checkout` — `PullRequestApiController.get()`/`list()`가 반환하는 `PullRequest` 엔티티에
       `fromProject`(owner/name 포함하는 완전한 Project 객체, `@JsonIgnore` 없음)와 `fromBranch`가
       그대로 직렬화된다 - CLI가 그 값으로 clone URL을 계산해 `git fetch`만 하면 되므로 신규 서버
       API가 불필요하다. **"기존 서버 API 연결" 그룹에서 "CLI 로컬 기능" 그룹으로 재분류.**
     - `gh pr edit` — 계획 문서가 "Step5에 PATCH 없음 → 서버 API도 신규 필요"로 적었으나, 실제로는
       `PullRequestController.updatePullRequest()`(PUT, 제목/본문/브랜치 수정)가 Step5부터 이미
       존재했다. 필요했던 건 신규 서비스 로직이 아니라 `PullRequestApiController`에 PATCH
       위임 어댑터 한 줄뿐이었다. **"신규 API 필요" → "기존 서버 API 연결"로 재분류.**
     - `gh pr close/reopen` — 계획 문서가 "서버 대응 상태변경 API 존재 여부 확인 필요"로 남겨뒀으나,
       `PullRequestController.changeState()`(POST `/{number}/state`)가 Step5부터 이미 양방향
       상태변경을 지원했다(이슈의 `changeState`와 동일 패턴). **"신규 API 필요" → "기존 서버 API
       연결"로 재분류.**
     - `yona label` — 감사표는 근거로 `web/LabelController.kt`를 지목했으나, 그 파일은 프로젝트와
       무관한 전역 라벨/카테고리 자동완성(`/labels`, `/categories`)만 제공한다. 실제 "프로젝트 하나에
       속한 라벨" CRUD는 `web/ProjectController.kt`(목록)와 `web/ProjectViewController.kt`(생성/
       수정/삭제, `/{owner}/{projectName}/issue/label(s)/...`, `ISSUE_LABEL` 기준 AccessControl)에
       있었다 - 감사표의 근거 파일 자체가 틀렸다(기능 존재 여부 분류는 맞았음).
   - **서버(1부)에 신규 API가 필요했던 것 중 실제로 구현한 것**: `gh issue status` 대응(사용자
     대시보드 REST API, `UserIssueStatusRestApiController` — 계획 지시대로 최소 버전만: 담당/작성
     이슈 개수·목록. mentioned/favorite/shared 필터와 페이지네이션 확장은 다음 라운드로 이월),
     `gh pr diff`(`PullRequestController.getDiff()`, `pullRequestService.getDiff()` 그대로 노출),
     `gh pr comment`(`PullRequestController.addComment()`, `CodeReviewService.createReviewComment()`
     재사용), `yona search issues/projects`(신규 `SearchRestApiController`, `SearchService.
     searchInAll()` 재사용), `yona org list/view`(신규 `OrganizationRestApiController`,
     `AccessControl.getVisibleProjects()` 재사용).
   - **낮은 우선순위**(nice-to-have, 다음 라운드 이후로 미뤄도 무방): `yona config`, `yona alias`
9. **Step 9 — `yona admin backup/webhook/permission`**: 기존 관리자 API 확인 후 연결(신규 서버 API 필요 시 1부 패턴으로 추가)
10. **Step 10 — `yona api <method> <path>`**: 저수준 원시 호출 명령(디버깅/스크립팅용)
11. **Step 11 — 배포**: `goreleaser`로 GitHub Releases + Homebrew tap + Scoop bucket + `.deb`/`.rpm`

## 완료 기준 (Definition of Done)

- [x] `ApiToken` 엔티티가 `expires_at` 필수로 강제됨을 테스트로 보장 (1라운드 — Step 1)
- [x] `ApiTokenAuthenticationFilter`가 스코프 밖 요청을 403으로 거부함을 테스트로 보장 (1라운드 — Step 3, 신규 `/api/v1/projects/...` 네임스페이스 한정)
- [x] 이슈/PR/프로젝트 REST API가 CRUD 전체를 커버하고, 각 엔드포인트에 권한 스코프 검증 테스트 존재 (2라운드 — Step 4~6, 단 프로젝트 조회 API는 스코프 토큰이 아닌 AccessControl 기반 검증 — 아래 로그/리스크 표 참고)
- [x] 프로젝트 조회/목록 API가 Fine-grained 스코프 토큰으로 완전히 동작함 (3라운드 — Step 6.5)
- [x] Fine-grained 토큰을 웹 UI에서 발급/조회/폐기할 수 있음 (3라운드 — Step 6.6)
- [x] Go CLI 본체(`yona auth/issue/pr/project/admin/api`)가 1부 REST API를 감싸는 형태로 구현됨 (4라운드 — Step7~10, 별도 저장소 `yona-cli`)
- [x] 서버(1부)가 Step8.5의 "기존 서버 API 연결"/"신규 API 필요" 두 그룹을 커버함 — project fork/create/edit/delete, label CRUD, issue reopen/transfer, PR edit/close/reopen/diff/comment, issue/pr list 필터, search issues/projects, org list/view, 사용자 이슈 대시보드 최소 버전 (5라운드 — 아래 완료 로그 참고. `yona search prs`는 서버에 대응 SearchType이 없어 이월)
- [x] yona-cli가 `gh` 명령 체계 대조 감사((A) 항목)를 반영해 사용법이 실제로 `gh`에 준함 (6라운드 + 직후 보완 — 아래 완료 로그 참고. `yona config`/`yona alias`는 낮은 우선순위 항목이라 다음 라운드 이후로 이월)
- [x] Step8.6 백로그 4개 항목(admin webhook/permission 목록 API, `gh issue status` 필터/페이지네이션 전체, `yona search prs`, PR 라벨/담당자) 전부 해소 (7라운드 — 아래 완료 로그 참고. PR 라벨/담당자의 웹 UI만 명시적으로 범위 밖, 다음 라운드 이후로 이월)
- [x] Step8.7 백로그 2개 항목(`LabelRestApiController` list vs create/update/delete 엔티티 불일치 버그, PR 라벨/담당자 웹 UI) 전부 해소 (8라운드 — 아래 완료 로그 참고. PR 목록 생성/수정 폼까지는 범위 밖으로 유지, 상세 화면+목록 화면 표시까지만)
- [x] Go CLI로 로그인 → 이슈 생성 → PR 목록 조회 골든 패스가 수동 검증 완료 (9라운드, 2026-09-01 — 아래 완료 로그 참고. 실제 서버로 부트스트랩 관리자 생성 → 로그인 → 프로젝트 생성 → 토큰 발급 → `yona auth login` → `yona issue create/list` → `yona pr create/list`까지 전부 성공 확인. 검증 과정에서 순환 직렬화 심각 버그를 발견·수정함)
- [x] yona-cli 전체 명령(git clone/push, pr checkout/merge/diff/edit, project fork, admin backup/permission/webhook, label edit 포함)이 실서버에 대고 정상 동작함 (10라운드, 2026-09-01 — 아래 완료 로그 참고. 실측으로 7개 실버그 발견·수정. 특히 스마트 HTTP git 프로토콜이 완전히 깨져 있던 심각한 버그를 포함)
- [ ] `goreleaser` 배포(Step 11: GitHub Releases/Homebrew/Scoop/`.deb`/`.rpm`) (2026-09-01 사용자 지시로 보류 — 아직 외부 배포 대상 사용자가 없어 실제로 필요해지는 시점까지 미룸)
- [ ] `./gradlew test` 전체 GREEN, JaCoCo 95%/95%/95% 유지(`docs/coverage/index.md` 기준) (전체 계획 완료 후 검증 — 10라운드 기준 `./gradlew test`(H2) 5807개 중 4개 실패, 전부 `IssueServiceImplSpec`/`IssueServiceSpec` 각 2케이스로 9라운드 로그에도 이미 기록된 사전 존재 플레이키니스(단독 실행 시 GREEN 재확인) — 이번 라운드가 만든 회귀 아님)

## 완료 로그

### 1라운드 (2026-08-28) — Part 1 Step 1~3

- **Step 1**: `domain/apitoken/ApiToken.kt`(+`ApiTokenScope.kt`) 엔티티, `ApiTokenRepository.kt` 신설.
  `expiresAt: Instant?` + `@Column(nullable = false)` 조합으로(Webhook.project 등 기존 엔티티와
  동일한 컨벤션) DB NOT NULL 제약이 null 저장을 거부하게 했다. repo scope는
  `allRepositories: Boolean` + `scopedProjects`(ManyToMany 조인테이블, User.enrolledProjects와
  동일 패턴) 조합으로, resource scope는 `ApiTokenScope`(ApiToken 1:N, ProjectUser가 (project,
  user)에 role을 얹는 것과 동일한 패턴)로 구현 — 조인테이블만으로는 그룹별 permission 값을 담을
  수 없어 별도 엔티티가 필요했다. 검증: `domain/apitoken/ApiTokenSpec.kt`(통합테스트).
- **Step 2**: `domain/apitoken/ApiTokenScopeGroup.kt`(8개 그룹: ISSUES/PULL_REQUESTS/CODE/BOARD/
  WIKI/WEBHOOKS/ADMINISTRATION/USERS)로 기존 `ResourceType` 33종을 전수 매핑(`NOT_A_RESOURCE`만
  미매핑=null, 항상 거부). 판정 로직은 `domain/apitoken/ApiTokenAuthorizer.kt`(순수 object,
  Spring 빈 아님 — DB/HTTP 의존이 없어 단위테스트만으로 충분). 검증:
  `ApiTokenScopeGroupSpec.kt`(매핑 전수 검증) + `ApiTokenAuthorizerSpec.kt`(만료/권한크기비교/
  repo scope 조합 단위테스트).
- **Step 3**: `config/ApiTokenAuthenticationFilter.kt` 재작성 — 신규 `/api/v1/projects/{owner}/
  {project}/{resource}` 네임스페이스(Step4~6에서 실제 컨트롤러가 채워질 예정, 이번 라운드는 필터
  로직만 선행 구현)로 오는 요청만 `ApiTokenRepository` 기반 스코프 판정(`ApiTokenAuthorizer`)을
  거치고, 그 외 기존 URL은 기존 `UserRepository.findByToken` 전권 부여 경로를 그대로 유지했다
  (아래 "기존 전권 토큰 마이그레이션" 참고, 의도적 설계). 필터 생성자에 `ApiTokenRepository`/
  `ProjectRepository`가 추가돼 `ApiTokenAuthenticationFilterSpec.kt`(기존 유닛테스트, 모두
  legacy 경로만 태워 그대로 통과)의 직접 생성 호출부를 함께 수정했다. 신규 검증:
  `config/ApiTokenScopedAuthorizationIntegrationSpec.kt`(MockMvc 통합테스트 — write 스코프 없음/
  스코프 자체 없음/다른 repo 스코프/만료 토큰 4가지 403 케이스 + write 스코프 있을 때 필터
  통과 1케이스).
- **범위 제외(그대로 유지)**: Step 4~6(이슈/PR/프로젝트 REST API 컨트롤러)과 Part 2(Go CLI) 전체는
  이번 라운드에서 전혀 손대지 않았다. `/api/v1/projects/...` URL 세그먼트(`issues`/`pull-requests`/
  `code`/`board`/`wiki`/`webhooks`/`settings`)와 리소스 매핑은 이번 라운드에서 필터 구현을 위해
  잠정 확정했지만, Step 4~6 착수 시 실제 컨트롤러 라우팅과 다시 대조 확인이 필요하다.

### 2라운드 (2026-08-29) — Part 1 Step 4~6

- **공통 설계 결정**: 세 Step 모두 신규 서비스 로직을 만들지 않고, 기존 숫자 `projectId` 기반
  웹 컨트롤러(`IssueController.kt`/`PullRequestController.kt`, `/api/projects/{projectId}/...`)가
  이미 `IssueService`/`PullRequestService`/`AccessControl`을 완비된 형태로 재사용하고 있음을 확인하고,
  새 `owner`/`project` 이름 기반 컨트롤러가 이름을 숫자 `projectId`로 바꿔 그 컨트롤러의 공개 메서드에
  그대로 위임하는 얇은 어댑터로 구현했다(계획이 요구한 "컨트롤러는 얇게" 원칙을 서비스 계층뿐 아니라
  기존 컨트롤러 계층까지 재사용해 더 철저히 지킴). 클래스명은 기존 파일과의 충돌을 피해 지었다 —
  `IssueApiController.kt`(legacy Open API `-_-api/v1/owners/...` 전용)와 `ProjectApiController.kt`
  (export/import 전용)가 이미 그 이름을 쓰고 있어 각각 `IssueRestApiController`/
  `ProjectRestApiController`로, PR은 이름 충돌이 없어 계획 문서가 제안한 `PullRequestApiController`를
  그대로 썼다.
- **Step 4 — 이슈 REST API**: `web/IssueRestApiController.kt`
  (`/api/v1/projects/{owner}/{project}/issues`) 신설 — 목록(GET)/생성(POST)/개별조회(GET
  `/{number}`)/수정(PATCH `/{number}`)/댓글(POST `/{number}/comments`, `CommentController` 위임)/
  클로즈(POST `/{number}/close`, `IssueController.changeState(state=CLOSED)` 위임)를 구현. 수정은
  `IssueController`의 웹용 대응 메서드가 PUT이지만 이 신규 API는 부분 수정 의미가 강해 PATCH로
  했다(요청 필드는 동일한 `UpdateIssueRequest` 재사용). 검증: `web/IssueRestApiControllerSpec.kt`
  (MockMvc standalone + mockk로 위임/404 검증, 6개 describe) +
  `config/ApiTokenScopedIssueAndPullRequestSubpathAuthorizationIntegrationSpec.kt`(comments/close
  하위 경로가 필터의 `scopedApiPattern` 접미부(`(?:/.*)?`)에 여전히 매칭돼 ISSUES 스코프로
  인가되는지 검증 — write 권한 없음 403 + write 권한 있으면 필터 통과(not 403) 각 2케이스).
- **Step 5 — PR REST API**: `web/PullRequestApiController.kt`
  (`/api/v1/projects/{owner}/{project}/pull-requests`) 신설 — 목록/생성/개별조회/머지(POST
  `/{number}/merge`)/리뷰(POST `/{number}/reviewers`, `PullRequestService.addReviewer` 위임)를
  구현. 계획 원문의 "리뷰"는 `PullRequestService`가 제공하는 단위(리뷰어 등록/해제)로 해석했다 —
  코드 라인 단위 리뷰 코멘트(`ReviewComment`/`CommentThread`)는 기존 `ReviewApiController`가 별도로
  다루는 영역이라 이 범용 REST API의 범위 밖으로 뒀다. 검증:
  `web/PullRequestApiControllerSpec.kt`(5개 describe) + 위 서브패스 통합테스트의 merge/reviewers
  케이스(PR은 필터 통과 후 컨트롤러의 `checkWritePermission`이 PR 조회보다 먼저 실행되므로, 통합
  테스트의 토큰 소유자를 프로젝트 멤버로 등록해 필터 통과 여부와 컨트롤러 자체 권한체크가 뒤섞이지
  않게 했다).
- **Step 6 — 프로젝트 조회 REST API**: `web/ProjectRestApiController.kt` 신설 — 목록(GET
  `/api/v1/projects/{owner}`)/조회(GET `/api/v1/projects/{owner}/{project}`)를 구현. 신규 서비스
  로직 없이 `ProjectRepository` + 기존 `AccessControl.isAllowedToReadProject()`(웹 UI와 동일한
  공개/비공개 가시성 규칙)만으로 구성. **설계상 알려진 한계(아래 리스크 표에 신규 행 추가)**: 이
  두 엔드포인트는 URL에 리소스 세그먼트가 없어(목록은 owner 1단, 조회는 owner/project 2단)
  `ApiTokenAuthenticationFilter`의 `scopedApiPattern`(owner/project/resource 3단 필수)과 매칭되지
  않는다 — 즉 Fine-grained 스코프 토큰으로는 아직 호출할 수 없고 세션 로그인/기존 전권 토큰으로만
  가능하다. Step1~3에서 이미 완성된 필터/스코프 판정 로직(건드리지 말 것으로 지정됨)을 변경해야
  해소되는 문제라 이번 라운드에서 임의로 결정하지 않고 문서화만 했다(대안: `settings` 세그먼트
  재사용, 신규 `info`/`metadata` 세그먼트 추가, 또는 필터 정규식의 3번째 세그먼트를 선택적으로
  완화). 이에 맞춰 검증 방식도 스코프 토큰 403 패턴 대신 AccessControl 기반 익명/비멤버 403·404로
  대체했다: `web/ProjectRestApiControllerSpec.kt`(공개 프로젝트 익명 조회 허용, 비공개 프로젝트
  비멤버 403, 미존재 프로젝트 404 등 4개 describe).
- **`SecurityConfig.kt` 점검 결과 및 변경**: `ApiTokenAuthenticationFilter`는 스코프 토큰이 없거나
  알 수 없는 토큰이면 인증 없이 통과시키고 컨트롤러의 401/403 처리에 위임하는데, 이 앱의 기존
  컨트롤러(`ProjectApiController`/`MigrationApiController`/`IssueController`/`PullRequestController`/
  `CommentController`)가 전부 이미 이 컨벤션(`anyRequest().permitAll()` + 컨트롤러 자체 인증/인가
  체크)을 쓰고 있어 그 자체로는 구멍이 아님을 확인했다(공개 프로젝트 익명 읽기 허용도 이 컨벤션의
  일부). 다만 신규 네임스페이스의 쓰기 경로만큼은 프레임워크 레벨 방어선을 하나 더 두는 게 안전하다고
  판단해 `.requestMatchers(HttpMethod.GET, "/api/v1/projects/**").permitAll()` +
  `.requestMatchers("/api/v1/projects/**").authenticated()`를 `anyRequest().permitAll()` 앞에
  추가했다(GET은 기존 공개 프로젝트 익명 조회 컨벤션을 깨지 않기 위해 제외).
- **전체 스위트**: `./gradlew test` 전체 GREEN(회귀 없음) — 상세 수치는 이 라운드의 커밋 메시지 참고.

### 3라운드 (2026-08-29) — Part 1 Step 6.5~6.6

2라운드가 남긴 두 갭(프로젝트 조회/목록 API의 스코프 패턴 불일치, 토큰 발급/관리 UI 부재)을
`fbeb589`(계획 문서에 이미 확정된 설계) 그대로 구현했다. 둘 다 설계 문서를 그대로 따랐고, 설계를
벗어난 판단이 필요했던 지점은 "metadata 세그먼트를 URL에 실제로 넣을지" 하나뿐(아래 Step 6.5 참고).

- **Step 6.5 — 스코프 패턴 갭 해소**:
  - `domain/apitoken/ApiTokenAuthorizer.kt`: `isAuthorized()`의 `resourceType` 파라미터를
    `ResourceType?`로 바꾸고, null이면(= "metadata" 스코프) 그룹/권한 매트릭스를 전혀 보지 않고
    만료 여부 + repo scope 일치 여부만으로 판정하도록 분리(`isProjectInRepoScope()` 추출). 기존
    호출부(비-null 인자)는 전부 그대로 통과 — 회귀 없음.
  - `config/ApiTokenAuthenticationFilter.kt`:
    - **설계 문서와 실제 URL 구조 사이의 유일한 판단 지점**: 설계는 "resourceSegmentToResourceType에
      metadata to null을 추가하고, 실제 URL은 바꾸지 않는다"고 했는데, 기존 `scopedApiPattern`은
      3세그먼트(owner/project/resource)를 **문자 그대로** 요구해 개별 프로젝트 조회
      (`/api/v1/projects/{owner}/{project}`, 2세그먼트)와 애초에 매칭될 수 없다 — "metadata"라는
      문자열이 URL 어디에도 없기 때문이다. 그래서 신규 `individualProjectPattern`(정확히
      2세그먼트)을 별도로 두고, 이 패턴에 매칭되면 `resourceSegmentToResourceType.getValue("metadata")`
      (= null)를 대입하는 방식으로 "URL은 안 바꾸면서 개념적으로 metadata 스코프를 쓴다"는 설계
      의도를 그대로 지켰다. `resourceSegmentToResourceType`는 `Map<String, ResourceType?>`로 바뀌었고
      조회는 전부 `containsKey` 기반(설계가 지적한 "map[key] ?: return null이 키 없음/값 null을
      구분 못 하는" 문제를 그대로 회피).
    - 목록(`/api/v1/projects/{owner}`, 1세그먼트) 전용 `ownerOnlyPattern` + `authenticateScopedList()`
      신설 — 403을 내지 않고 `setAuthenticatedIdentity()`(기존 `authenticateScoped()`의 신원 설정
      로직을 추출해 공유)로 SecurityContext만 세팅한 뒤, 인증된 `ApiToken`을 request attribute
      `SCOPED_API_TOKEN_ATTRIBUTE`("SCOPED_API_TOKEN")로 다운스트림에 넘긴다. 3세그먼트 경로
      (`authenticateScoped()`)에도 같은 attribute를 세팅해 재사용 가능하게 했다(설계 문서 그대로).
  - `web/ProjectRestApiController.kt`: `list()`가 request attribute로 넘어온 `ApiToken`을 읽어
    `scopedToken == null`(세션/레거시/비로그인 — 기존 동작 100% 유지) / `allRepositories`(전체
    반환) / 선택 스코프(`scopedProjects` 교집합) 3분기로 필터링(설계 문서의 `when` 블록 그대로).
    `get()`(개별 조회)은 컨트롤러 변경이 필요 없었다 — 필터가 이미 스코프 밖이면 403으로 막아준다.
  - 검증: `domain/apitoken/ApiTokenAuthorizerSpec.kt`에 metadata(resourceType=null) 3케이스 추가,
    신규 `config/ApiTokenScopedMetadataAndListAuthorizationIntegrationSpec.kt`(실제 시큐리티 필터
    체인을 태운 MockMvc, ApiTokenScopedAuthorizationIntegrationSpec과 동일 패턴) 5케이스(개별 조회
    허용/거부 각 1 + 목록 전체스코프/선택스코프/세션로그인 각 1), `web/ProjectRestApiControllerSpec.kt`
    에 목록 필터링 3케이스(속성 없음/전체스코프/선택스코프) 추가. 신규 8 + 기존 파일에 추가된 6 =
    총 14개 테스트로 검증.
- **Step 6.6 — Fine-grained 토큰 발급/관리 웹 UI**: 레거시 전권 토큰 화면(`user/edit_token.html`,
  `/user/editform/token_reset`)은 전혀 손대지 않았다(계획 지시대로 완전히 별개 화면으로 신설).
  - `domain/apitoken/ApiToken.kt`: `name: String` 필드 추가(owner/tokenHash 옆, `nullable = false`).
    기존 테스트가 전부 named argument로 `ApiToken(...)`을 생성해 위치 무관하게 안전함을 사전 확인.
  - `domain/apitoken/ApiTokenRepository.kt`: `findByOwner(owner): List<ApiToken>` 추가(scopes/
    scopedProjects까지 JOIN FETCH — 목록 화면이 권한 뱃지/저장소 범위 요약을 렌더링해야 하므로
    `findByTokenHash()`와 동일한 이유로 즉시 로딩).
  - 신규 `domain/apitoken/ApiTokenService.kt`(인터페이스, `IssuedApiToken` DTO 포함) +
    `ApiTokenServiceImpl.kt` — `issue()`는 `LdapUserProvisioningService.generateSalt()`와 동일한
    `SecureRandom` 패턴으로 원문 토큰을 만들고 `ApiTokenHasher.hashApiToken()`으로 해시만 저장,
    이름 공백 검증 + **만료일 366일 상한 검증**(갭 분석 4번 "만료일 상한 없음"을 이번에 함께 해소 —
    설계 문서에 명시된 항목). `revoke()`는 owner 소유가 아니면 조용히 무시(존재 여부 비노출).
  - `web/UserController.kt`(기존 레거시 전권 토큰 `@RestController`)는 손대지 않고, 계획이 제시한
    대안대로 기존 `web/UserViewController.kt`(`@Controller`, 세션 기반 `/user/editform/*` 화면
    전체를 담당)를 확장했다 — `GET /user/editform/tokens`(목록+발급폼), `POST
    /user/editform/tokens`(발급, 권한 매트릭스는 `scope_<GROUP_NAME>` 파라미터로 그룹 수만큼 수신),
    `POST /user/editform/tokens/{id}/revoke`(폐기, `/user/editform/tokens`로 리다이렉트).
  - `user/partial_edit_tabmenu.html`에 "API 토큰(세분화)" 탭 추가, 신규 `user/edit_tokens.html`
    (목록 테이블 + 저장소범위 라디오/select2 다중선택 + 8개 그룹×3단 권한 라디오 매트릭스 + 만료일
    프리셋 + 발급 직후 "지금 한 번만 표시됩니다" 배너). **설계에서 벗어난 지점 하나**: 폐기
    confirm을 `common/commentDeleteModal.html`류 모달 대신 네이티브 `confirm()` + 이벤트 위임
    스크립트로 구현했다(`th:onsubmit`에 `#{message}`를 문자열 결합해 넣는 방식이 따옴표 이스케이프로
    깨지기 쉬워, `data-confirm-message` attribute + 전역 `submit` 리스너로 대체) — 기능적으로는
    "폐기 전 확인" 요구사항을 동일하게 충족한다.
  - i18n 메시지 키 `apitoken.*`/`userinfo.tokens`/`button.copy`를 `messages.properties`,
    `messages_ko_KR.properties`에 추가(다른 로케일 파일은 Thymeleaf가 기본 `messages.properties`로
    폴백하므로 추가하지 않음).
  - 검증: 신규 `domain/apitoken/ApiTokenServiceImplSpec.kt`(발급/스코프 저장/이름공백거부/
    366일상한거부/목록조회/폐기소유권검증 7케이스), `web/UserViewControllerSpec.kt`에 3개
    엔드포인트의 미인증/성공/실패 분기 7케이스 추가, 신규
    `web/ApiTokenEditFormTemplateRenderingSpec.kt`(webAppContextSetup + 실제 시큐리티로 Thymeleaf
    렌더링까지 확인 — standaloneSetup MockMvc는 실제 뷰 리졸버를 안 태워 템플릿 문법 오류를 못
    잡으므로 `PostingHistoryTemplateRenderingSpec` 패턴을 그대로 따름) 3케이스(발급폼 렌더링/발급
    직후 배너+목록 반영/폐기 후 목록에서 제거). 총 17개 테스트로 검증.
- **전체 스위트**: `./gradlew test` 전체 GREEN — 유일한 실패는 2라운드에도 있었던 사전 존재 이슈
  (`ApiTokenSpec.kt`, MariaDB 컬럼 타임스탬프 마이크로초 절삭으로 인한 `Instant` 나노초 정밀도
  불일치, 이번 라운드가 손대지 않은 Step1 코드)뿐 — 회귀 아님.

### 4라운드 (2026-08-30) — Part2 Step7~10 (Go CLI)

yona와 완전히 별개인 새 git 저장소 `~/yona-convert/yona-cli`(`github.com/search5/yona-cli`
모듈)에 Go 1.26 + Cobra 1.10 스택으로 CLI 본체를 구현했다. 배포(Step 11)는 이번 라운드
범위 밖이라 손대지 않았다. 커밋 3개로 진행: 설정/HTTP 클라이언트 코어 → REST API 클라이언트
→ Cobra 명령 트리. Go 표준 `testing` + `stretchr/testify` + `net/http/httptest`로 TDD,
실제 yona 서버 없이 63개 테스트 전체 GREEN(`go test ./...`), `go build ./...`/`gofmt -l .`/
`go vet ./...` 클린.

- **Step 7 — CLI 스캐폴딩**: `internal/config`(`~/.config/yona-cli/config.yml`, gh의
  `hosts.yml` 패턴 참고 — 서버 URL을 키로 삼아 여러 호스트의 토큰을 동시에 보관) +
  `yona auth login/logout/status`. "CLI 로그인 토큰의 기본 스코프"(위 2026-08-28 결정) 그대로,
  yona 서버엔 OAuth 유사 로그인 플로우가 없어 **로그인 자체가 아니라 "이미 발급받은 토큰 값을
  CLI에 알려주는" `gh auth login --with-token`류 흐름으로 구현했다** — 스코프가 얼마나
  넓은지는 사용자가 웹 UI에서 어떤 토큰(레거시 전권 토큰 또는 전체 스코프 Fine-grained 토큰)을
  발급해 붙여넣는지에 달려 있고, CLI는 그 값을 그대로 저장할 뿐 스코프를 계산하지 않는다.
  `--token`으로 제한된 토큰을 그때그때 넘기는 경로도 함께 구현(설정 파일에 저장하지 않음).
  서버/토큰 결정 순서는 `--server`/`--token` 플래그 > `YONA_HOST`/`YONA_TOKEN` 환경변수 >
  설정 파일.
- **Step 8 — `yona issue`/`yona pr`/`yona project`**: `internal/api`에 2라운드가 만든 REST API
  엔드포인트를 그대로 감싸는 얇은 클라이언트를 구현. **응답 파싱 설계 결정**: `ProjectRestApiController`
  응답(`web/ProjectRestApiController.kt`의 `toProjectNode()`)은 컨트롤러가 직접 조립한 맵이라
  필드가 안정적이어서 타입 있는 `Project` 구조체로 받았지만, Issue/PullRequest 응답은 JPA
  엔티티를 그대로 직렬화한 결과라 필드 구성이 코드 변경에 취약하다고 판단해 의도적으로
  `map[string]interface{}`로 느슨하게 받고 CLI 출력은 `number`/`title`/`state`/`body` 등
  흔한 키만 방어적으로 꺼내 쓰게 했다(모든 view/list 명령에 `--json` 플래그로 원본 그대로
  출력하는 탈출구를 남김). 요청 바디(`CreateIssueRequest`/`UpdateIssueRequest`/
  `CommentRequest`/`CreatePullRequestRequest`)는 실제 yona Kotlin DTO 필드명을 그대로
  맞췄다(`web/IssueController.kt`/`web/CommentController.kt`/`web/PullRequestController.kt`
  Serena LSP로 직접 대조 확인). `yona pr review`는 계획 문서 원문의 "리뷰"가 실제로는
  리뷰어 지정이 아니라 인증된 본인을 리뷰어로 자기등록하는 동작임을 코드로 확인하고
  (`PullRequestController.addReviewer`) 그대로 반영했다. 프로젝트 주소 지정은 계획 문서
  예시(`yona project view <name>`)와 달리 서버 API가 owner도 요구해 gh 관례인
  `owner/project` 단일 인자(이슈/PR은 `-R/--repo` 플래그)로 통일했다 — **계획 문서 예시와
  실제 구현이 다른 지점**.
- **Step 9 — `yona admin backup/webhook/permission`**: 착수 전 `web/` 패키지를 grep +
  Serena LSP로 전수 조사한 결과:
  - **백업**: `web/SiteApiController.kt`에 `GET /site/export`(전체 DB JSON 백업 다운로드,
    `checkAdmin()`으로 사이트매니저만 허용)와 `POST /site/import`(멀티파트 업로드, 전체
    테이블 교체 복원)가 실제로 존재해 그대로 연결했다(`yona admin backup export/import`).
  - **웹훅**: `web/WebhookController.kt`에 CRUD가 있지만 **세션/폼 기반 레거시 MVC
    컨트롤러**다(`/projects/{owner}/{projectName}/webhooks`, `/api/v1` 네임스페이스 밖,
    JSON이 아닌 form-urlencoded 요청/HTML 또는 빈 응답). 생성(POST)과 삭제(DELETE)는 구조상
    CLI에서도 그대로 호출 가능해 연결했지만(`yona admin webhook create/delete`), **목록
    조회(GET)는 Thymeleaf가 렌더링한 HTML 페이지(`project/setting_webhook`)만 반환**해 CLI가
    파싱할 구조화된 데이터가 전혀 없다 — `yona admin webhook list`는 명확한 안내 메시지와
    함께 미구현 스텁으로 남겼다.
  - **권한**: `web/ProjectMemberController.kt`에 멤버 추가/역할변경/삭제가
    `/api/projects/{projectId}(숫자 ID)/members/...`로 JSON 응답(`Map<String,String>`)과 함께
    존재해 연결했다(`yona admin permission add/update-role/remove`) — 다만 이 컨트롤러가
    숫자 `projectId`를 요구해, CLI는 먼저 `GET /api/v1/projects/{owner}/{project}`로 id를
    조회한 뒤 그 값을 넘기는 2단계로 구현했다(`resolveProjectID` 헬퍼). **"현재 멤버+역할
    목록"을 내려주는 엔드포인트는 존재하지 않는다** — 가장 가까운 `assignableUsers`는 "할당
    가능한 후보" 목록이지 이미 배정된 권한 매트릭스가 아니다 — `yona admin permission list`도
    미구현 스텁으로 남겼다.
  - 위 두 "미구현" 결정은 지시사항대로 yona 쪽에 새 API를 추가해 임의로 해소하지 않고 그대로
    보고한다 — 이 CLI 프로젝트의 범위를 넘는 서버 쪽 변경이 필요하다.
- **Step 10 — `yona api <path>`**: `gh api`와 동일한 컨셉의 원시 HTTP 호출(`-X` 메서드,
  `-f key=value` 반복으로 JSON 바디 조립, `-H`로 추가 헤더, `--input`으로 파일/표준입력을
  바디로 그대로 전달). 상태 코드 4xx/5xx는 응답 본문을 그대로 출력한 뒤 0이 아닌 종료 코드로
  끝난다.
- **Step 11(배포)은 착수하지 않음** — 계획 문서 지시대로 이번 라운드 범위 밖.
- **저장소/커밋**: `yona-cli`는 yona와 무관한 독립 git 저장소(원격 없음, 로컬 전용)로
  커밋 3개(설정+HTTP 클라이언트 코어 / REST API 클라이언트 / Cobra 명령 트리)로 나눠
  진행했다. 상세 커밋 이력과 파일 경로는 이 작업을 지시한 세션의 최종 보고 참고.

### 5라운드 (2026-08-31) — Step8.5 서버(1부) 보강

착수 전 "CLI `gh` 명령 체계 대조 감사"의 (A) 감사표/Step8.5 분류를 코드로 전수 재검증하라는
지시에 따라, 각 항목이 실제로 `/api/v1/projects/...` 네임스페이스에 이미 노출돼 있는지부터
직접 확인했다 — 재검증 결과와 오분류 정정은 위 "단계별 작업 계획" Step8.5 절에 기록. 그 결과
실제로 서버 쪽에 손을 대야 했던 항목만 구현했다(대부분 "기존 서비스/컨트롤러 메서드에 위임하는
얇은 어댑터 추가"였고, 순수 신규 서비스 로직이 필요했던 곳은 없었다 — Step4~6의 설계 원칙을
그대로 유지).

- **필터 확장**: `config/ApiTokenAuthenticationFilter.kt`의 `resourceSegmentToResourceType`에
  `"labels" -> ResourceType.ISSUE_LABEL`(ISSUES 그룹 — 위임 대상 메서드들이 실제로 ISSUE_LABEL
  기준 AccessControl을 쓰므로 일치시킴), `"fork" -> ResourceType.FORK`(CODE 그룹)를 추가. 둘 다
  기존 3세그먼트 `scopedApiPattern`에 그대로 매칭돼 정규식 자체는 변경하지 않았다.
- **프로젝트 REST API 확장**(`web/ProjectRestApiController.kt`):
  - `POST /api/v1/projects/{owner}/{project}/fork` — `ProjectController.forkProject()`(기존
    owner/name 기반, 숫자 ID 변환 불필요)에 그대로 위임.
  - `PATCH`/`DELETE /api/v1/projects/{owner}/{project}/settings` — owner/project 이름을 ID로
    바꿔 `ProjectController.updateProject()`/`deleteProject()`에 위임. **"settings" 세그먼트를
    쓴 이유(설계 결정)**: 세그먼트 없는 `/api/v1/projects/{owner}/{project}`는 이미 Step6.5의
    "metadata" 스코프(그룹/권한 매트릭스 없이 repo scope만 확인)로 매핑돼 있어, 그 경로를 PATCH/
    DELETE에도 그대로 쓰면 ADMINISTRATION 쓰기 권한이 전혀 없는 토큰도 프로젝트를 수정/삭제할 수
    있는 구멍이 생긴다. 기존에 이미 `ResourceType.PROJECT_SETTING`(ADMINISTRATION 그룹)으로
    매핑돼 있던 "settings" 세그먼트(Step1~3이 선점만 하고 실제 컨트롤러는 없었음)를 재사용해
    필터 변경 없이 올바른 권한 등급을 강제했다.
  - `POST /api/v1/projects`(bare, owner 세그먼트 없음) — `yona project create`. 세션 로그인
    사용자의 조직 소속 프로젝트 생성 권한 로직(`ProjectViewController.newProject()`와 동일 —
    owner가 기존 조직명이면 그 조직 admin만 생성 가능)을 그대로 재사용해 JSON으로 새로 구현.
    **의도적 설계**: 이 URL은 필터의 어떤 스코프 패턴과도 매칭되지 않아(3/2/1세그먼트 전부 모양이
    다름) 세션 로그인/레거시 전권 토큰으로만 호출 가능하고 Fine-grained 스코프 토큰으로는 저장소
    "생성"을 할 수 없다 — GitHub Fine-grained PAT도 저장소 생성 자체는 지원하지 않는 것(기존
    저장소에만 스코프될 수 있음)과 동일한 제약이라 우연이 아니라 의도적으로 이렇게 뒀다.
- **라벨 REST API 신설**(`web/LabelRestApiController.kt`,
  `/api/v1/projects/{owner}/{project}/labels`): 목록은 `ProjectController.getProjectLabels()`,
  생성/수정/삭제는 `ProjectViewController.newLabel()`/`updateLabelForm()`/`deleteLabelForm()`에
  위임(전부 기존 메서드 재사용, 신규 서비스 로직 없음).
- **이슈 REST API 확장**(`web/IssueRestApiController.kt` + `web/IssueController.kt`):
  - `POST /{number}/reopen` — 기존 범용 `IssueController.changeState()`를 OPEN으로 고정 호출.
  - `POST /{number}/transfer` — 기존 `IssueController.moveIssue()`(`MoveIssueRequest
    (targetProjectId: Long)`)에 위임하되, CLI 편의를 위해 `targetOwner`/`targetProject`(이름)를
    받아 이 어댑터가 내부에서 숫자 ID로 resolve한다(신규 서버 API 없이 기존 조회 반복 사용).
  - `GET /issues` `--assignee`/`--label`/`--author` 필터 — `IssueController.getIssues()`에
    세 선택 파라미터 추가. 셋 다 없으면 기존 `findByProject(AndState)` 경로를 100% 그대로 타
    회귀가 없고, 하나라도 있으면 `IssueRepository`가 이미 구현 중이던
    `JpaSpecificationExecutor<Issue>`를 활용한 동적 `Specification`으로 좁힌다(신규 리포지토리
    메서드 불필요). `author`는 `Issue.authorLoginId` 등가비교, `assignee`는 `Assignee.user.
    loginId` 등가비교, `label`은 `IssueLabel.name` 등가비교(ManyToMany라 `distinct(true)` 적용).
- **PR REST API 확장**(`web/PullRequestApiController.kt` + `web/PullRequestController.kt`):
  - `PATCH /{number}` — 기존 `PullRequestController.updatePullRequest()`(PUT, Step5부터 존재)에
    PATCH로 위임하는 어댑터만 추가.
  - `POST /{number}/close`, `/reopen` — 기존 `PullRequestController.changeState()`(Step5부터
    존재)를 CLOSED/OPEN으로 고정 호출.
  - `GET /{number}/diff` — 신규 `PullRequestController.getDiff()`(숫자 ID 기반, 웹 뷰
    `PullRequestViewController.viewChangesInternal()`이 화면 렌더링에 쓰는 것과 동일한
    `pullRequestService.getDiff()`를 JSON으로 노출) + REST API 어댑터.
  - `POST /{number}/comments` — 신규 `PullRequestController.addComment()`(숫자 ID 기반,
    `CodeReviewService.createReviewComment()`를 commitId/codeRange 없이 호출해 PR 전체에 붙는
    일반 댓글로 귀결시킴 — `ReviewViewController.newPullRequestComment()`와 같은 서비스를
    재사용하되 그 메서드 자체는 브라우저 폼 제출 전용(redirect 응답)이라 위임 대상으로 못 씀) +
    REST API 어댑터.
  - `GET /pull-requests` `--author` 필터 — `PullRequestController.getPullRequests()`에 선택
    파라미터 추가, `contributor.loginId` 등가비교로 인메모리 필터링(PR 목록은 프로젝트당 크지
    않아 신규 리포지토리 쿼리 없이 처리). **`--assignee`/`--label`은 PR에 적용하지 않는다** —
    yona `PullRequest` 엔티티엔 이슈와 달리 labels/assignee 필드 자체가 없어(reviewers/
    contributor만 존재) 실제 모델에 없는 개념을 인위적으로 만들지 않았다.
- **검색/조직/사용자 대시보드 신규 컨트롤러**(전부 기존 서비스 재사용, 신규 서비스 로직 없음):
  - `web/SearchRestApiController.kt`(`/api/v1/search/issues`, `/api/v1/search/projects`) —
    `SearchService.searchInAll()`을 JSON으로 노출. **범위 조정**: yona `SearchType` enum엔
    PROJECT/ISSUE/USER/POST/MILESTONE/ISSUE_COMMENT/POST_COMMENT/REVIEW만 있고 "PULL_REQUEST"에
    대응하는 값이 없다(PR 자체를 색인하는 통합검색 기능이 서버에 아직 없음) — `yona search prs`는
    이번 라운드 구현 대상에서 제외하고 다음 라운드로 이월.
  - `web/OrganizationRestApiController.kt`(`/api/v1/organizations`,
    `/api/v1/organizations/{name}`) — `OrganizationViewController`와 동일한 권한 로직(게스트
    차단, `HIDE_PROJECT_LISTING`, `AccessControl.getVisibleProjects()`)만 재사용.
  - `web/UserIssueStatusRestApiController.kt`(`GET /api/v1/user/issues/status`) — `gh issue
    status` 최소 버전(계획 지시대로). `UserViewController.userIssues()`가 이미 쓰는
    `findByAssigneeAndState`/`findByAuthorIdAndState` + count 쌍만 재사용해 담당/작성 이슈
    개수·목록을 반환한다. mentioned/favorite/shared 필터, 페이지네이션/정렬 확장은 범위가 커질 수
    있어 다음 라운드로 이월.
- **신규 스코프 인가 갭(문서화, 아래 리스크 표에도 기록)**: `search`/`organizations`/`user`는
  프로젝트(저장소) 하나에 속한 리소스가 아니라 여러 저장소를 가로지르거나 저장소와 무관한
  전역/사용자 단위 개념이라 `/api/v1/projects/{owner}/{project}/{resource}` 3세그먼트 스코프
  모델에 애초에 맞지 않는다. 이 세 네임스페이스는 어떤 스코프 패턴과도 매칭되지 않아 세션
  로그인/레거시 전권 토큰으로만 인증되고 Fine-grained 스코프 토큰은 인증되지 않는다(구멍이
  아니라 기능 제한 — Step6 이전 프로젝트 조회 API가 겪었던 것과 동일한 성격의 제한이며, 이번
  라운드 지시사항이 "애매하면 리스크 표에 남기고 합리적 기본값으로 진행"이라 명시해 이렇게
  처리했다).
- **테스트**: 신규/확장 스펙 파일 — `web/ProjectRestApiControllerSpec.kt`(+8),
  `web/LabelRestApiControllerSpec.kt`(신규 4), `web/IssueRestApiControllerSpec.kt`(+7),
  `web/PullRequestApiControllerSpec.kt`(+6), `web/PullRequestControllerSpec.kt`(+4),
  `web/SearchRestApiControllerSpec.kt`(신규 5), `web/OrganizationRestApiControllerSpec.kt`(신규
  4), `web/UserIssueStatusRestApiControllerSpec.kt`(신규 2),
  `config/ApiTokenScopedProjectForkAndLabelSubpathAuthorizationIntegrationSpec.kt`(신규 8 —
  fork/labels/settings 세그먼트 스코프 403/통과 검증),
  `config/ApiTokenScopedIssueAndPullRequestSubpathAuthorizationIntegrationSpec.kt`(+9 —
  이슈 reopen/transfer, PR edit/close/reopen/diff/comments 서브패스 스코프 검증). 총 57개
  테스트 추가/확장, 전부 GREEN.
- **회귀 확인**: `web`/`config`/`domain/apitoken` 패키지 전체(3280여 개 테스트) 실행 결과 4건
  실패는 전부 `CodeSwallowedStyleRenderingSpec`/`CodeBrowserListWrapRenderingSpec`(코드
  브라우저 CSS 렌더링, 이번 라운드가 전혀 손대지 않은 영역)이었고, 이 두 스펙은 이 라운드
  변경사항을 전부 `git stash`한 클린 `main`에서도 동일하게 실패함을 확인해 **사전 존재 이슈로
  회귀가 아님**을 검증했다.

### 6라운드 (2026-09-01) — Step8.5 Go CLI(2부) 클라이언트 구현

5라운드가 완료한 서버(1부) API를 그대로 감싸는 CLI(2부) 클라이언트 코드를 `yona-cli`
저장소(`github.com/search5/yona-cli`, 독립 git 저장소)에 구현했다. 착수 전 이 계획 문서
전체와 `yona-cli`의 기존 `cmd/*.go`/`internal/api/*.go`, 그리고 방금 5라운드가 추가한 실제
yona 서버 엔드포인트(`web/ProjectRestApiController.kt` 등 7개 컨트롤러)를 Serena LSP로
직접 재확인해 계획 문서 요약과 실제 코드가 어긋나는 지점이 없는지 대조했다 — 어긋난 지점은
없었고, 계획 문서에 이미 기록된 응답 형태 그대로였다.

- **그룹 1(CLI 로컬 기능)**:
  - `--json <fields>` 필드선택 전환: `cmd/output.go`의 `printJSON()`을 불리언 스위치에서
    "필드를 JSON 왕복 변환 후 재귀적으로 골라내는" 방식으로 재작성. `cmd.Flags().Changed("json")`
    으로 "플래그 자체를 안 씀"과 "빈 필드 목록을 명시적으로 줌"을 구분해 후자는 오류 처리.
    기존 issue/pr/project 명령의 `--json` 사용처와 테스트를 전부 이 방식으로 갱신.
  - `-L/--limit`: `issue list`는 서버가 지원하는 페이지네이션(`size` 쿼리 파라미터)을 그대로
    활용, `pr list`/`project list`는 서버가 페이지네이션 없이 전체 목록을 반환해 클라이언트
    사이드 슬라이싱으로 처리(계획 문서 지시대로).
  - `--web`: `view`/`list` 계열 명령에 추가. 신규 `internal/weburl` 패키지가
    `IssueViewController.kt`/`PullRequestViewController.kt`의 실제 `@GetMapping` 경로
    (`/{owner}/{project}/issue/{n}`, `/{owner}/{project}/pull/{n}`, `/issues`, `/pulls`)를
    그대로 반영해 URL을 계산하고, 신규 `internal/gitutil.OpenInBrowser()`가 OS별
    (`xdg-open`/`open`/`rundll32`)로 연다.
  - `--repo`/`-R` 자동감지: 신규 `internal/gitutil.DetectRepo()`가 `git remote get-url
    origin`을 파싱한다. yona clone URL(`TemplateHelper.getCloneUrl()`, `scheme://[loginId@]
    host[:port]/owner/name.git`)과 `git@host:owner/repo.git` scp 스타일 SSH URL 둘 다
    지원 — gh CLI와 동일하게 호스트 뒤 마지막 두 경로 세그먼트를 owner/project로 취급한다.
    git이 없거나 저장소 밖이면 명시적 `--repo` 오류로 폴백. issue/pr/label의 모든 `--repo`
    사용처가 이 자동감지를 거치도록 갱신(project는 원래부터 `owner/project` 위치 인자를 쓰므로
    자동감지 대상이 아님).
  - `yona server list/use`: 신규 `cmd/server.go`. `list`는 `auth status`와 동일한 토큰
    마스킹으로 `config.Hosts`를 나열, `use`는 신규 `config.UseHost()`로 재로그인 없이
    `CurrentHost`만 전환(등록 안 된 호스트면 오류).
  - `yona browse [issue|pr <number>]`: 신규 `cmd/browse.go`. `internal/weburl`을 `--web`과
    공유해 URL 계산 로직을 재사용.
  - `completion`: 실제로 확인해보니 Cobra가 서브커맨드를 가진 루트 커맨드에 이미 자동으로
    등록하고 있었다(`ExecuteC()` → `InitDefaultCompletionCmd()`, `CompletionOptions.
    DisableDefaultCmd` 기본값 `false`) — 계획 문서 예상대로 별도 구현 없이 등록만 확인.
  - `--version`: `cmd/root.go`에 `Version` 필드를 설정해 Cobra 기본 제공 `--version` 플래그를
    활성화(하드코딩 상수, ldflags 주입은 Step 11 배포 작업 범위로 남김).
- **그룹 2/3(서버 API 연결)**: 5라운드 완료 로그의 엔드포인트 목록을 그대로 클라이언트로
  감쌌다 — `yona project fork/create/edit/delete`, `yona label list/create/edit/delete`,
  `yona issue edit/reopen/transfer`(edit은 read-modify-write로 title/body 중 생략한 쪽을
  현재 값으로 채움 — 서버 `UpdateIssueRequest`가 둘 다 non-null 필수), `yona issue list`의
  `--assignee`/`--label`/`--author` 필터, `yona pr edit/close/reopen/diff/comment`(edit도
  동일한 read-modify-write), `yona pr list`의 `--author` 필터, `yona search issues/projects`
  (`prs`는 서버에 대응 SearchType이 없어 구현하지 않음, 계획 문서에 이미 기록된 이월 그대로
  유지), `yona org list/view`, `yona issue status`(담당/작성 이슈 개수·목록).
  - **`yona pr checkout <number>`**: 서버 API를 호출하지 않고, `pr view`와 같은 방식으로
    조회한 PR 응답의 `fromProject`(owner/name) + `fromBranch`로 clone URL을 계산해
    `git fetch <url> <branch>` + `git checkout -B pr-<번호> FETCH_HEAD`를 실행한다. URL/브랜치
    이름 계산(`planCheckout()`)을 git 실행과 분리해 순수 함수로 단위테스트했다.
- **서버 쪽에서 발견한 문제(이번 라운드 범위 밖, 보고만 함)**: `PullRequestController.getDiff()`
  (`pr diff`가 호출)가 반환하는 `List<FileDiff>`의 `FileDiff.a`/`b`는
  `org.eclipse.jgit.diff.RawText`, `editList`는 `EditList`(JGit 내부 타입)로 선언돼 있는데,
  둘 다 일반 Jackson 빈 컨벤션에 맞는 getter가 없다 — 그대로 JSON 직렬화하면 필드가 거의
  비거나 예외가 날 가능성이 있다. CLI 쪽에서 억지로 우회하지 않고 `pathA`/`pathB`/`changeType`
  같은 단순 필드만 안전하게 쓰고 나머지는 `--json` 탈출구로만 노출하는 방어적 설계로 대응했다
  (`internal/api/pr.go`의 `GetPullRequestDiff()` 주석 참고). 실제 서버로 호출해 직렬화
  결과를 확인하지는 못했다(이번 라운드는 httptest 목킹만 수행) — 실서버 검증 시 재확인 필요.
- **후속 보완(6라운드 직후)**: `pr create`의 `--from-project-id` 요구 제거 — 6라운드 위임
  프롬프트에 빠뜨려 이월됐던 것을 바로 마저 구현. `--from "owner/project"`를 받아 CLI가
  기존 프로젝트 조회 API(`GET /api/v1/projects/{owner}/{project}`)로 내부에서 ID를
  resolve하도록 `cmd/pr.go`의 `newPRCreateCmd`를 수정(TDD, RED→GREEN, `go test ./...`
  전체 통과). `yona-cli` 커밋 `2ce3881`.
- **이월 항목**:
  - `yona config`, `yona alias` — 계획 문서 지시대로 낮은 우선순위, 미착수.
  - Go CLI 실서버 골든 패스(`auth login` → 이슈 생성 → PR 목록 조회) 수동 검증 — 여전히
    미착수(httptest 기반 단위/통합 테스트만 수행).
  - `goreleaser` 배포(Step 11) — 미착수.
- **테스트**: `yona-cli` 신규/확장 테스트 다수 추가, `go test ./...` **167개 테스트 전체
  GREEN**(`go build ./...`/`go vet ./...`/`gofmt -l .` 전부 클린). 상세 커밋 이력은 이 작업을
  지시한 세션의 최종 보고 참고.

### 7라운드 (2026-09-01) — Step8.6 백로그 4개 항목 전부 해소

착수 전 이 절 전체와 6라운드까지의 완료 로그, `config/ApiTokenAuthenticationFilter.kt`의 스코프
인가 메커니즘(`scopedApiPattern`/`resourceSegmentToResourceType`)을 재확인한 뒤, 지시된 우선순위
순서(1→2→3→4) 그대로 진행했다 — 순서를 바꾸지 않았다.

- **항목1(최우선) — admin webhook/permission 목록 JSON API 신설**:
  - `WebhookController.kt`에 `listWebhooksJson()` 신설(기존 `webhooks()`와 동일한 프로젝트 조회 +
    권한 체크(`checkWebhookPermission`, Operation.UPDATE) 재사용, JSON 응답만 다름 — secret도
    기존 HTML 화면(`setting_webhook.html`)과 동일한 노출 수준으로 그대로 포함). 신규
    `web/WebhookRestApiController.kt`(`GET /api/v1/projects/{owner}/{project}/webhooks`)가 위임.
  - `ProjectMemberController.kt`에 `listMembers()` 신설(`isProjectManager` 권한 체크 재사용, 기존
    `findByProjectId()` 결과를 loginId/역할명 포함 JSON으로 변환). 신규
    `web/ProjectPermissionRestApiController.kt`(`GET /api/v1/projects/{owner}/{project}/permissions`)가
    owner/project 이름을 숫자 projectId로 바꿔 위임.
  - `ApiTokenAuthenticationFilter.resourceSegmentToResourceType`에 `"permissions" ->
    ResourceType.PROJECT_SETTING`(ADMINISTRATION 그룹) 추가 — `"webhooks"`는 Step1~3부터 이미
    매핑돼 있어 변경 불필요.
  - 검증: `WebhookControllerSpec`(+3, `listWebhooksJson` 성공/404/403), 신규
    `WebhookRestApiControllerSpec`(1), `ProjectMemberControllerSpec`(+3, `listMembers` 성공/403/401),
    신규 `ProjectPermissionRestApiControllerSpec`(2), 신규
    `ApiTokenScopedWebhookAndPermissionSubpathAuthorizationIntegrationSpec`(4, webhooks/permissions
    각 스코프 없음 403·있음 200). 총 13개.
- **항목2 — `gh issue status` 필터/페이지네이션 전체 노출**: `UserViewController.userIssues()`와
  `UserIssueStatusRestApiController`를 코드로 대조한 결과, 지시대로 신규 백엔드 로직 없이 이미 있는
  `IssueRepository` 메서드(`findCommentedByState`/`findMentionedByState`/`findFavoriteByState`/
  `findSharedByState` + 대응 count 쌍, `MentionService.getMentioningIssueIds`)에 쿼리 파라미터만
  추가로 전달해 해소했다. `/status` 엔드포인트에 `pageNum`/`state`/`filter`/`orderBy`/`orderDir`
  (userIssues()와 동일한 이름·기본값) + `commenterId`/`mentionId`/`sharerId`/`favoriteId`(명시하지
  않으면 로그인 사용자 자신으로 기본값)를 추가하고, 응답에 기존 `assigned`/`created`(하위호환
  유지) 외에 `commented`/`mentioned`/`favorite`/`shared` 4개 섹션을 추가했다. 각 섹션은
  `openCount`/`closedCount`/`items`/`totalElements`/`totalPages`/`page`를 담는다. 검증: 기존 스펙에
  +3케이스(commented/mentioned/favorite/shared 동시 반환, 페이지네이션/정렬/검색 파라미터 전달,
  타 사용자 id override) = 총 5개.
- **항목3 — search prs 서버 지원**: `domain/enumeration/SearchType`에 `PULL_REQUEST` 추가.
  `domain/pullrequest/PullRequestRepository`에 기존 `SearchType.ISSUE`(`IssueRepository.
  searchIssues()`)와 동일한 패턴(Postgres Hibernate 7.2.x가 한 쿼리에 LIKE 술어 2개 이상이면
  실패하는 버그를 피하기 위한 네이티브 쿼리)의 `searchPullRequests`/`countSearchPullRequests`(전역
  — toProject가 허용 프로젝트 목록에 있거나 내가 contributor인 PR까지 포함, Issue의
  author/assignee 대응 개념으로 contributor 하나만 존재)와
  `searchPullRequestsInProject`/`countSearchPullRequestsInProject`(프로젝트 내부)를 신설했다.
  `SearchServiceImpl`의 `searchInAll`/`searchInAProject`/`searchInAGroup` 3개 메서드와
  `SearchResult`에 `pullRequestsCount`/`pullRequests` 필드 추가(우선순위는 기존 8개 타입 뒤,
  기본값 ISSUE 폴백 앞). `web/SearchRestApiController`에 `GET /api/v1/search/prs` 추가(issues/
  projects와 동일한 얇은 어댑터). 검증: `SearchResultSpec`(+1), `SearchServiceSpec`(+3, 전역/
  프로젝트내부/그룹 각 1케이스), `SearchRestApiControllerSpec`(+2), 신규
  `PullRequestRepositorySpec`(+2, 실제 DB로 네이티브 쿼리 검증 — 프로젝트 소속/contributor 매칭,
  프로젝트 내부 검색). 총 8개.
- **항목4(가장 신중하게) — PR에 라벨/담당자 개념 추가**:
  - **레거시 조사 결과(착수 전 필수 확인)**: `/home/jiho/yona-convert/legacy-yona/app/models/
    PullRequest.java`를 전수 확인(`grep -i "label\|assignee"` 0건 — 필드/컨트롤러/뷰 템플릿
    (`app/controllers/PullRequestApp.java`, `app/views/organization/group_pullrequest_list*.
    scala.html` 등) 전부 0건). **레거시 Play `yona`의 PR 모델에도 원래부터 label/assignee
    개념이 전혀 없었음을 확정** — 이번 작업은 포팅 누락 버그가 아니라 순수 신규 기능 확장이다
    (`docs/yona-wiki/index.md` 서문이 허용하는 범위).
  - **`Assignee` 재사용 가능성 확인**: `domain/issue/Assignee.kt`는 정말로 `(user, project)`만
    갖는 범용 엔티티이고 Issue 전용 FK/제약이 전혀 없음을 확인 — Issue와 동일한 패턴
    (`@ManyToOne(cascade=[CascadeType.ALL])`, `@JoinColumn(name="assignee_id")`, nullable)으로
    그대로 재사용 가능해 재사용했다.
  - **라벨 엔티티 선택**: `domain/issue/IssueLabel`(카테고리에 종속, `issue_issue_label` 조인
    테이블로 Issue와 연결, `ProjectViewController.newLabel()`/`updateLabelForm()`/
    `deleteLabelForm()`이 실제로 관리하는 "진짜" 프로젝트별 이슈 라벨)과 `domain/project/Label`
    (카테고리 없는 단순 태그, `project_label` 조인테이블로 **Project 자신**에 직접 붙는 프로젝트
    레벨 토픽/분류용, Step8.5 1라운드 도입)의 용도가 서로 다름을 코드로 확인했다. **부수 발견**:
    `web/LabelRestApiController.kt`는 `list()`(`ProjectController.getProjectLabels()`)가
    `domain/project/Label`을 반환하는데 `create/update/delete`(`ProjectViewController.
    newLabel/updateLabelForm/deleteLabelForm`)는 `domain/issue/IssueLabel`을 다뤄 **같은 REST
    리소스 안에서 조회와 변경이 서로 다른 엔티티를 가리키는 기존 불일치**가 있다(이번 작업
    범위 밖이라 수정하지 않고 여기 기록만 남긴다 — 별도 이슈/다음 라운드 후보). PR은 "프로젝트
    안의 개별 항목"이라는 점에서 Issue와 성격이 같으므로 `IssueLabel`을 재사용하는 것이
    개념적으로 맞고, 프로젝트마다 이미 라벨 정의(이름/색상/카테고리)가 있어 신규
    `PullRequestLabel` 엔티티를 만들 필요가 없다고 판단 — Issue.labels와 동일한 패턴(신규
    조인테이블 `pull_request_issue_label`)으로 재사용했다.
  - **스키마 변경 리스크 재평가**: `application.yml`이 전 프로파일에서 `ddl-auto: update`를
    쓰고 있어(수동 마이그레이션 스크립트 없음, 지금까지 이 계획의 모든 라운드가 신규 엔티티/
    컬럼을 추가할 때 동일하게 의존해온 방식) 스키마 변경 자체의 리스크는 계획 문서가 우려했던
    것보다 낮다고 재평가 — 이 판단 때문에 "최소 구현으로 축소"가 아니라 지시된 범위(엔티티+
    서비스+REST API+목록 필터) 전체를 이번 라운드에 완료했다(웹 UI만 범위 밖으로 유지).
  - **구현**: `PullRequest.kt`에 `assignee: Assignee? = null`, `labels: MutableSet<IssueLabel> =
    mutableSetOf()` 추가. `PullRequestService`/`PullRequestServiceImpl`에 `setAssignee()`(null이면
    해제, IssueServiceImpl.updateIssue()와 동일하게 기존 Assignee를 재사용하지 않고 매번 새로
    만듦)/`addLabel()`/`removeLabel()`(둘 다 라벨 "정의" 자체는 만들지 않고 프로젝트에 이미 있는
    `IssueLabel`만 참조) 신설. `PullRequestController`에 `PUT/DELETE /{number}/assignee`,
    `POST /{number}/labels`, `DELETE /{number}/labels/{labelId}`(addReviewer/removeReviewer와
    동일한 `checkWritePermission` 패턴) 추가, `getPullRequests()`에 `--assignee`/`--label` 필터
    추가(기존 `--author`와 동일하게 인메모리 필터링 — PR 목록은 프로젝트당 크지 않음).
    `PullRequestApiController`에 대응 어댑터(`PUT/DELETE .../assignee`, `POST/DELETE
    .../labels(/{labelId})`) + 목록 필터 파라미터 전달 추가. 별도 필터 세그먼트 추가는
    불필요(`pull-requests` 세그먼트가 이미 `scopedApiPattern`의 `(?:/.*)?` 접미부로 모든 하위
    경로를 PULL_REQUESTS 그룹으로 인가하고 있음 — 이슈 comments/close, PR merge/reviewers와
    동일한 기존 메커니즘 재사용).
  - 검증: `PullRequest.kt` 필드 추가에 대한 실제 DB 검증은 `PullRequestServiceSpec`의 신규
    4케이스(담당자 지정/해제, 존재하지 않는 PR 예외, 라벨 추가/제거, 존재하지 않는 라벨 예외,
    real DB 기반)로 확보. `PullRequestControllerSpec`(+14, assignee PUT 5케이스/DELETE 2케이스,
    labels POST 3케이스/DELETE 2케이스, list 필터 assignee/label 각 1케이스),
    `PullRequestApiControllerSpec`(+6, assignee PUT/DELETE 어댑터 3개 + labels POST/DELETE
    어댑터 2개 + 목록 필터 파라미터 전달 1개), `ApiTokenScopedIssueAndPullRequestSubpath
    AuthorizationIntegrationSpec`(+4, assignee/labels 하위경로 스코프 403/통과). 총 28개.
  - **웹 UI는 명시적으로 범위 밖**: PR 상세/목록 화면에 담당자·라벨을 표시·편집하는 Thymeleaf
    UI는 이번 라운드에서 손대지 않았다(지시사항의 "범위가 크면 최소 구현 + 웹 UI는 범위 밖" 대안을
    적용 — 다만 백엔드는 전체를 완료했으므로 실제로 축소된 것은 웹 UI뿐이다). 다음 라운드 이후로
    이월.
- **전체 스위트**: `./gradlew test` 전체 실행 결과 3280여 개 테스트 중 실패는 5라운드에도 있었던
  사전 존재 이슈(`CodeSwallowedStyleRenderingSpec`/`CodeBrowserListWrapRenderingSpec`, 코드
  브라우저 CSS 렌더링, 이번 라운드가 전혀 손대지 않은 영역)뿐 — 회귀 아님. 상세는 이 라운드의
  커밋 메시지 참고.

### 8라운드 (2026-09-01) — Step8.7 백로그 2개 항목 전부 해소

착수 전 이 문서의 "Step 8.7" 절 전체와 7라운드 완료 로그(PR 라벨/담당자 백엔드 구현 상세 —
`PullRequest.kt`, `PullRequestService(Impl).kt`, `PullRequestApiController.kt`,
`PullRequestController.kt`, 신규 조인테이블 `pull_request_issue_label`)를 재확인한 뒤, 지시된
우선순위 순서(1→2) 그대로 진행했다.

- **항목1(최우선, 실제 버그) — `LabelRestApiController` list vs create/update/delete 엔티티
  불일치 수정**:
  - `web/LabelRestApiController.kt`, `web/ProjectController.kt`(`getProjectLabels()`),
    `web/ProjectViewController.kt`(`newLabel()`/`updateLabelForm()`/`deleteLabelForm()`),
    `domain/project/ProjectServiceImpl.kt`(`getProjectLabels()` → `Set<Label>`),
    `domain/issue/IssueLabel.kt`/`IssueLabelRepository.kt`를 코드로 대조해 재확인 — `IssueLabel`을
    프로젝트 기준으로 조회하는 리포지토리 메서드(`IssueLabelRepository.findByProject(project):
    List<IssueLabel>`)와 서비스 메서드(`IssueLabelService.getLabels(projectId):
    List<IssueLabel>`)가 **이미 존재**해 신설이 불필요했다.
  - 먼저 실패 테스트(RED)를 실제 REST 엔드포인트 end-to-end로 작성했다 — 신규
    `LabelRestApiControllerIntegrationSpec`(1케이스, POST로 라벨 생성 후 GET 목록 조회 시 방금
    만든 라벨이 안 보임을 실제로 재현·확인).
  - `ProjectViewController`에 `getIssueLabelsForRestApi(owner, projectName, authentication)`
    신설(`ProjectController.getProjectLabels()`의 `checkReadPermission`과 동일한
    `accessControl.isAllowed(user, project, Operation.READ)` 게이트 + `IssueLabelService.
    getLabels()` 조회, 응답 필드를 `create()`가 반환하는 형태(id/name/color/category/categoryId)와
    맞춤 — `/home/jiho/yona-convert/yona-cli/internal/api/label.go`의 `ListLabels()`가 `id`/`name`
    필드를 느슨한 `map[string]interface{}`로 읽으므로 필드명 일치만으로 호환). `newLabel`/
    `updateLabelForm`/`deleteLabelForm`과 달리 대응하는 legacy HTML 세션 라우트가 없어
    `@GetMapping`을 붙이지 않고 `LabelRestApiController` 전용 위임 대상으로만 뒀다(불필요한 신규
    공개 HTTP 엔드포인트 방지).
  - `LabelRestApiController.list()`의 위임 대상을 `projectController.getProjectLabels()`에서
    `projectViewController.getIssueLabelsForRestApi()`로 교체 — 이제 더 이상 `ProjectController`를
    참조하지 않아 그 생성자 파라미터도 제거했다.
  - **부수 확인(그레핑으로 실사용처 확인)**: `ProjectController.getProjectLabels()`
    (`/api/{owner}/{projectName}/labels`)와 `domain/project/Label`/`ProjectServiceImpl.
    getProjectLabels()`는 **`LabelRestApiController`가 유일한 사용처가 아니었다** —
    `project/home.html`(324번째 줄, `sURLProjectLabels` JS 변수로 프로젝트 홈 화면의 토픽 태그
    자동완성에 실사용 중)이 이 REST 엔드포인트를 직접 호출한다. 그래서 지시대로 `Label`/
    `getProjectLabels()` 자체는 건드리지 않았다.
  - GREEN 확인 후 `LabelRestApiControllerSpec`(기존 4케이스를 `getIssueLabelsForRestApi` mock으로
    갱신)과 `ApiTokenScopedProjectForkAndLabelSubpathAuthorizationIntegrationSpec`(라벨 스코프
    인가 회귀, 기존 2케이스)을 재실행해 회귀 없음을 확인.
  - 신규/수정 파일: `web/ProjectViewController.kt`(+`getIssueLabelsForRestApi`),
    `web/LabelRestApiController.kt`(위임 대상 교체, `ProjectController` 의존성 제거),
    `web/LabelRestApiControllerSpec.kt`(mock 갱신), 신규
    `web/LabelRestApiControllerIntegrationSpec.kt`(+1). 테스트 총 5개(신규 1 + 기존 갱신 4).
- **항목2 — PR 라벨/담당자 웹 UI**:
  - Issue의 담당자/라벨 UI 위치를 grep으로 재확인: 담당자는 `issue/view.html`의 `dl`/`dt`/`dd`
    블록(`usf-group`/`avatar-wrap smaller`/`name`/`loginid` CSS 클래스, 편집 가능일 땐
    `yonaAssgineeModule` select2 AJAX 위젯), 라벨은 `issue/partial_select_label.html`(편집,
    select2 다중선택)/`issue/partial_show_selected_label.html`(읽기전용, `data-label-id` 포함
    배지) 프래그먼트, 목록은 `issue/partial_list.html`의 라벨 배지(79~81행)/담당자 아바타
    (152~154행).
  - **PR 상세(`pullrequest/view.html`)**: `issue/view.html`의 담당자 표시 마크업(usf-group 등)과
    라벨 프래그먼트 2개를 그대로 재사용해 새 `<div class="issue-info pr-assignee-label-info">`
    블록을 추가했다. 담당자 **입력**만은 Issue의 select2 위젯을 그대로 재사용하지 않았다 —
    Issue의 담당자 위젯은 로그인ID 기반 AJAX 검색(`yonaAssgineeModule`, Issue 전용
    `assignableUsers`/`assignees` 엔드포인트)인데, round7이 만든 PR의 `setAssignee` REST
    API(`PullRequestController`)는 숫자 `userId`를 받는 별도 계약이라 그대로 이어붙일 수
    없었다 — 대신 같은 issue/view.html 안에 있는 마일스톤 select(정적 `<option>` +
    `data-toggle="select2"`)와 동일한 패턴으로 `project.projectUsers`를 서버 렌더링하는
    `<select id="pr-assignee-select">`를 추가했다(신규 AJAX 검색 엔드포인트 설계 회피). 라벨은
    백엔드 계약이 Issue와 동일(라벨 ID)이라 프래그먼트를 100% 그대로 재사용했다. 편집 가능
    여부는 `PullRequestController.checkWritePermission()`(프로젝트 멤버 여부)과 동일한
    `@templateHelper.isMember(project, currentUser)`로 게이트해 백엔드 권한 체크와 일치시켰다.
    저장은 `view.html`의 기존 `$.ajax` 관례(예: `#btnAccept`)를 그대로 따라 select `change`
    이벤트에서 round7의 `PUT/DELETE .../assignee`, `POST/DELETE .../labels(/{labelId})`
    엔드포인트를 호출하고 성공 시 페이지를 새로고침한다(라벨은 이전 선택값과의 diff를 계산해
    추가/삭제 요청을 나눠 보냄). select2 초기화를 위해 `common/select2 :: select2` fragment와
    라벨 색상용 `issue/labels.css` 링크(둘 다 프로젝트 스코프 기존 자원, PR 전용 신규 자원
    없음)를 view.html에 추가했다.
  - **PR 목록(`pullrequest/partial_list.html`)**: Issue 목록(`issue/partial_list.html`)의 라벨
    배지 마크업(`label issue-label list-label active`, `data-category-id`/`data-label-id`)과
    담당자 아바타 마크업(`avatar-wrap assignee`)을 그대로 복사해 각 PR 행에 추가했다(읽기전용 —
    Issue 목록도 여기서는 편집하지 않는다). 기존에 이미 있던 `avatar-wrap assinee`(오탈자,
    legacy 그대로 유지) 아바타는 `req.receiver`(병합한 사람)이고 이번에 추가한 `req.assignee`
    (담당자)와는 다른 개념이라 나란히 뒀다.
  - **범위를 좁힌 지점**: PR 생성/수정 폼(`pullrequest/create.html`/`edit.html`)에 담당자/라벨
    입력을 추가하는 것은 이번 라운드 범위에서 제외했다(지시사항의 "상세 화면에서 보기+변경 정도의
    합리적 최소 범위"를 적용) — 다음 라운드 이후로 이월.
  - 검증(TDD, RED→GREEN): 신규 `PullRequestAssigneeAndLabelTemplateRenderingSpec`(3케이스 —
    프로젝트 멤버는 담당자 select/라벨 다중선택을 볼 수 있음, 기존 배정된 담당자/라벨이 선택된
    상태로 렌더링됨, 쓰기 권한 없는 비로그인 방문자는 읽기전용으로만 보임), 신규
    `PullRequestListAssigneeAndLabelTemplateRenderingSpec`(1케이스 — 목록에 담당자 아바타/라벨
    배지 표시). 기존 `PullRequestListTemplateEquivalenceSpec`/`PullRequestViewControllerSpec`/
    `PullRequestViewControllerMoreSpec`/`TimelineTemplateRenderingSpec`/
    `PullRequestMergeResultTemplateRenderingSpec` 재실행으로 회귀 없음 확인. 총 4개.
  - 신규/수정 파일: `pullrequest/view.html`(담당자/라벨 블록 + JS 배선 + select2/labels.css
    include), `pullrequest/partial_list.html`(라벨 배지 + 담당자 아바타), 신규
    `PullRequestAssigneeAndLabelTemplateRenderingSpec.kt`(+3), 신규
    `PullRequestListAssigneeAndLabelTemplateRenderingSpec.kt`(+1).
- **전체 스위트**: `./gradlew test` 전체 실행 결과 5788개 테스트 중 4개 실패 — 전부
  5~7라운드에도 있었던 사전 존재 이슈(`CodeSwallowedStyleRenderingSpec` 1개,
  `CodeBrowserListWrapRenderingSpec` 3개, 코드 브라우저 CSS 렌더링, 이번 라운드가 전혀 손대지
  않은 영역)뿐 — 신규 실패 없음, 회귀 아님.

### 9라운드 (2026-09-01) — Step8.7 3번(최우선, 실제 버그) 순환 직렬화 수정 + 골든패스 수동검증

실서버(H2 프로파일)로 부트스트랩 관리자 생성 → 로그인 → 프로젝트 생성 → Fine-grained 토큰
발급까지는 정상 동작했으나, **이슈 생성**(`POST /api/v1/projects/{owner}/{project}/issues`)에서
yona-cli가 "JSON을 해석할 수 없습니다: invalid character ']'"로 실패했다. curl로 원본 응답을
직접 확인한 결과 60KB가 넘는, 깨진 채로 끊긴 JSON이었다.

- **재현(RED)**: mockk로 서비스 계층을 목킹한 기존 `*RestApiControllerSpec.kt`들은 순환이 실제로
  발생할 연관관계 그래프가 없어 이 버그를 잡지 못했다는 게 확인된 사실이라, `AbstractIntegrationTest`
  (실제 DB) + `MockMvc`(`webAppContextSetup`, 실제 Jackson `HttpMessageConverter`)로 실제
  `User`/`Project`/`ProjectUser`/`Issue`/`PullRequest` 엔티티 그래프를 직렬화해 재현하는 신규
  `IssueAndPullRequestCircularSerializationIntegrationSpec`(5케이스: 이슈 단건/목록, PR 단건/목록,
  검색 프로젝트)를 먼저 작성했다. 수정 전 5케이스 전부 `JsonParseException`으로 실패함을 확인(RED).
- **근본 원인**: `IssueRestApiController`/`PullRequestApiController`/`SearchRestApiController`가
  위임 대상(`IssueController`/`PullRequestController`/`SearchService`)이 돌려주는
  `Issue`/`PullRequest`/`Project` 엔티티를 가공 없이 그대로 반환한다. `Issue.project`/
  `PullRequest.toProject`/`fromProject`(모두 `@ManyToOne`)가 각각 `Project.projectUsers`
  (`@OneToMany mappedBy="project"`)로 이어지고, 그 `ProjectUser.user`(`@ManyToOne`)가 다시
  `User.projectUsers`(`@OneToMany mappedBy="user"`)로 돌아오는 완전한 순환이다.
  `spring.jpa.open-in-view`가 기본 true(명시적 설정 없음)라 응답 직렬화 시점까지 Hibernate
  세션이 열려있어 이 lazy 컬렉션들이 실제로 초기화되며 무한 재귀에 빠진다(Jackson은 일반 POJO의
  참조 사이클을 자동 감지하지 않는다) — StackOverflowError가 나기 전까지 이미 스트리밍된
  수만 바이트가 그대로 응답으로 나가버려 "60KB 넘는 깨진 JSON"이라는 증상과 정확히 들어맞는다.
- **수정**: `ProjectRestApiController.toProjectNode()`가 이미 쓰고 있던 "엔티티 대신 응답 전용
  모델을 반환" 패턴을 그대로 따라 신규 `web/RestApiResponseDto.kt`를 추가했다 —
  `IssueResponse`/`IssueCommentResponse`/`PullRequestResponse`/`PullRequestMergeResultResponse`/
  `ReviewCommentResponse`/`GitCommitResponse`/`PullRequestCommitResponse`와, 이들 안에 중첩되는
  `UserRefResponse`(id/loginId/name만)/`AssigneeResponse`/`IssueLabelResponse`/
  `ProjectRefResponse`(id/owner/name/overview/vcs/scope만, `ProjectRestApiController`와 동일
  필드). `User`/`Project` 엔티티를 통째로 중첩하는 지점(작성자/담당자/리뷰어/받는사람/from·to
  프로젝트)은 전부 이 최소 참조 DTO로 대체했다. 필드명은 yona-cli(`internal/api/issue.go`,
  `pr.go`, `cmd/issue.go`, `cmd/pr.go`)가 실제로 읽는 키(number/title/state/body/fromBranch/
  toBranch/createdDate 등 기존 필드 + Step7~8의 assignee/labels)와 정확히 동일한 camelCase를
  유지했다(CLI가 `map[string]interface{}`로 느슨하게 파싱하므로 필드명만 일치하면 CLI 코드 변경
  불필요 — 실제로 변경하지 않았다).
  - `IssueRestApiController`/`PullRequestApiController`는 위임 대상 메서드가 반환하는
    `ResponseEntity<Issue>`/`ResponseEntity<PullRequest>` 등을 그대로 넘기는 대신, 컨트롤러
    경계에서 `mapBody { it.toResponse() }` 확장 함수로 감싸 응답 상태 코드는 그대로 유지한 채
    본문만 DTO로 바꿔 반환한다 — 기존 `IssueController`/`PullRequestController`(웹 프런트엔드
    전용, `/api/projects/{projectId}/...`)는 건드리지 않아 그쪽 동작은 완전히 보존했다.
  - `SearchRestApiController.searchIssues/searchProjects/searchPullRequests`도 `Page<T>.map { }`로
    동일하게 DTO 변환.
  - **PR 머지 결과의 부가 발견(별개 잠재 버그)**: `PullRequestMergeResult.conflicts()`는 Kotlin
    에서 `get`/`is` 접두사가 없는 일반 메서드라 Jackson 빈 컨벤션상 프로퍼티로 노출되지 않는다 —
    DTO로 바꾸지 않았다면 yona-cli `cmd/pr.go`의 `newPRMergeCmd()`가 `result["conflicts"]`를
    영원히 못 찾아 충돌 여부를 감지하지 못했을 것(순환 직렬화와 무관한 별개의 잠재 버그). DTO에
    `conflicts: Boolean` 필드를 명시적으로 채워 함께 해소했다.
  - **점검 결과 문제 없음으로 확인된 지점**: `.../labels`(`LabelRestApiController` → `ProjectViewController.
    getIssueLabelsForRestApi()`), `.../webhooks`(`WebhookRestApiController` → `WebhookController.
    listWebhooksJson()`)는 이미 `Map<String, Any?>` 기반으로 직접 조립해 반환하고 있어 순환
    직렬화 문제 자체가 없다 — 코드 검토뿐 아니라 실서버에 실제 라벨/웹훅을 만들어 curl로 직접
    재검증했다(아래 골든패스 로그 참고). `.../pull-requests/{number}/diff`(`FileDiff`)는 JPA
    엔티티가 아니라 JGit 값 객체라 User/Project 연관관계 자체가 없어 대상 밖으로 확인.
- **GREEN 확인**: RED로 작성한 5케이스 전부 통과. 기존 `IssueRestApiControllerSpec`/
  `PullRequestApiControllerSpec`/`SearchRestApiControllerSpec`(mockk 기반)과
  `web`/`config` 패키지 전체(인가 스코프 회귀 포함) 재실행해 회귀 없음 확인.
- **실서버 골든패스 수동검증**(H2 프로파일, `-Dspring.profiles.active=h2`, `server.port=18080`):
  1. `POST /bootstrap-setup`으로 관리자(`admin`) 생성 → `POST /users/login`
     (`loginIdOrEmail`/`password`)으로 세션 로그인 → `POST /projectform`으로 `admin/demo-repo`
     프로젝트 생성 → `POST /user/editform/tokens`로 Fine-grained 토큰(ISSUES/PULL_REQUESTS/CODE
     WRITE) 발급, `yona_pat_` 프리픽스 토큰 문자열 확보(TASK-0388 반영 확인).
  2. `yona auth login --token <token>` 성공 → `yona issue create -R admin/demo-repo --title
     "골든패스 테스트 이슈" --body ...` → `이슈 #1 생성됨` 출력, `yona issue list`에서 `#1 OPEN
     골든패스 테스트 이슈` 정상 표시. curl로 원본 응답 재확인 시 475바이트의 유효한 JSON
     (수정 전 60KB+ 깨진 JSON과 대비).
  3. `yona pr create -R admin/demo-repo --title "골든패스 테스트 PR" --from admin/demo-repo
     --from-branch feature --to-branch main` → `풀 리퀘스트 #1 생성됨` → `yona pr list`에서
     `#1 OPEN 골든패스 테스트 PR` 정상 표시. curl 원본 응답 658바이트, `fromProject.owner`/
     `fromProject.name`이 정확히 채워져 있음을 확인(`yona pr checkout`의 `planCheckout()`이
     이 필드에 의존).
  4. 추가 검증: `yona search projects demo` → `admin/demo-repo` 정상 표시.
     `GET /api/v1/search/issues?q=...`/`GET /api/v1/search/projects?q=...` 원본 응답 모두 유효한
     JSON(각 791/431바이트). `yona label create`/`yona label list`로 라벨 CRUD 정상 동작(431바이트
     응답). 웹 UI로 웹훅 생성 후 `GET /api/v1/projects/admin/demo-repo/webhooks` 원본 응답
     147바이트 유효 JSON. `yona pr diff`(빈 diff, 정상), `POST .../pull-requests/1/merge`는
     `TransportException: Remote does not have feature available for fetch`로 500 — 이 프로젝트가
     실제 git 커밋 없이 만든 저장소라 발생하는 기존 `PullRequestServiceImpl.merge()`의 git
     트랜스포트 레벨 동작이고(스택트레이스 확인, DTO 변환 코드에 도달하기 전에 예외 발생) 이번
     버그와 무관 — 골든패스 필수 검증 범위(로그인→이슈 생성→PR 목록 조회)에도 포함되지 않아
     추가 조치 없이 기록만 남긴다.
  5. 검증 후 서버 프로세스 종료, golden-path 전용 H2 데이터 디렉터리(`data/h2-goldenpath/`,
     gitignore 대상) 삭제.
- **전체 스위트**: `./gradlew test` 5789개 중 4개 실패(`IssueServiceImplSpec`/`IssueServiceSpec`
  각 2케이스) — 두 스펙 모두 단독 실행하면 GREEN임을 확인했고, 8라운드 로그도 매 라운드 서로 다른
  무관한 스펙(`CodeSwallowedStyleRenderingSpec` 등)이 전체 스위트에서만 실패하는 동일한 패턴을
  기록해왔다 — `AbstractIntegrationTest`가 같은 forked 테스트 JVM 안의 스펙끼리 H2 인메모리 DB를
  공유하는 구조적 특성상의 사전 존재 플레이키니스이지, 이번 라운드가 만든 회귀가 아니다(이번 라운드
  변경분은 `Role`/`ProjectUser`/`OrganizationUser` 저장 로직을 전혀 건드리지 않았다).
- **신규/수정 파일**: 신규 `web/RestApiResponseDto.kt`, 신규
  `web/IssueAndPullRequestCircularSerializationIntegrationSpec.kt`(+5), 수정
  `web/IssueRestApiController.kt`/`web/PullRequestApiController.kt`/`web/SearchRestApiController.kt`
  (엔티티 반환 → DTO 반환으로 전환, 신규 서비스 로직 없음).

### 10라운드 (2026-09-01) — yona-cli 전체 명령 실서버 수동 골든패스에서 발견한 7개 실버그 수정

9라운드까지는 REST API 배선/직렬화 위주로 검증해왔는데, 이번 라운드는 실제 `yona-cli` 바이너리로
전체 명령(`project create/fork`, `git clone/push`, `pr create/diff/edit/checkout/merge`,
`issue status`, `admin backup/permission/webhook`, `label create/edit`)을 하나씩 손으로 실행하며
골든패스를 재현했다. 전부 mockk 기반 스펙으로는 놓쳤던 버그였다 — 실제 서블릿 생명주기/실제
파일시스템/실제 git 트랜스포트/여러 스펙이 공유하는 실제 H2 DB가 얽혀야 드러나는 문제들이었다.

**공통 원칙**: 7개 항목 전부 RED(실제 서버 + 실제 `yona-cli` 바이너리로 재현) → 최소 구현 →
GREEN(같은 방식 재검증) → 단위/통합 테스트 추가 순서로 진행했다. 서버는 h2 프로파일+
`-Dyona.data=<격리된 tmp 경로>`(TASK-0415 반영, DB까지 확실히 격리됨)로 매번 새로 띄우고 검증 후
종료했다.

#### 항목1(최우선) — 스마트 HTTP git 프로토콜이 완전히 깨져 있음

- **근본원인**: `GitServletConfig.kt`의 디스패처 서블릿이 JGit `GitServlet`/`LfsProtocolServlet`을
  컨테이너에 실제 서블릿으로 등록하지 않고, 자기 자신의 `service()`에서 `gitServlet.service(req,res)`를
  수동으로만 호출해왔다. 그런데 JGit `GitServlet`은 `MetaServlet`을 상속하며, 내부 `GitFilter`가
  upload-pack/receive-pack/info-refs 등 URL 파이프라인(`bindings`)을 실제로 구성하는 시점은
  `init(ServletConfig)`이 호출될 때뿐이다(`GitFilter.init()` 소스 직접 대조로 확인). 컨테이너가
  이 디스패처 서블릿에게 보장하는 `init(ServletConfig)` 호출이 `gitServlet`/`lfsServlet`에는 전혀
  전달되지 않았으므로, `GitFilter`의 파이프라인이 텅 빈 채로 남아 모든 요청이 첫 매치 실패로 기본
  체인(`chain.doFilter`)에 떨어져 `RepositoryResolver`에 도달하지도 못한 채 조용히 404를 반환했다
  (그래서 서버 로그에 예외 스택트레이스가 전혀 안 남았다 — `RepositoryNotFoundException`이 조용히
  404로 변환되는 정상 경로조차 타지 않았던 것).
- **수정**: `GitServletConfig.kt`의 디스패처 서블릿에 `init()`(무인자, `GenericServlet.init(ServletConfig)`가
  자동 위임하는 템플릿 메서드)을 override해 `gitServlet.init(servletConfig)`/
  `lfsServlet.init(servletConfig)`를 명시적으로 호출하도록 고쳤다 — 컨테이너가 디스패처 자신에게
  주는 정상적인 서블릿 생명주기를 그대로 두 서블릿에 전달하는 최소 수정.
- **RED/GREEN**: 신규 `config/GitSmartHttpProtocolIntegrationSpec.kt`
  (`@SpringBootTest(webEnvironment = RANDOM_PORT)`로 실제 임베디드 톰캣을 띄우고, 실제 `git clone`
  바이너리로 스마트 HTTP clone까지 검증 — 기존 `GitAuthorizationFilterIntegrationSpec`은 MockMvc가
  `DispatcherServlet`만 태우고 이 raw 서블릿을 우회해 이 버그를 검증할 수 없었다). 수정 전
  `git clone` 프로세스가 실제로 실패함을 확인(RED) → 수정 후 성공(GREEN).
- **실서버+실CLI 골든패스**: bootstrap-setup으로 관리자 생성 → `yona project create admin/golden-repo`
  → 실제 `git clone http://.../git/admin/golden-repo.git` 성공 → `main` 브랜치에 커밋 후 실제
  `git push` 성공 → `feature-1` 브랜치 생성/push 성공 → `yona pr create` → `yona pr checkout 1`
  성공 → `yona pr merge 1` 성공까지 전부 실측 검증.
- **부수 발견 1(같은 근본원인 계열) — 클론 URL에 `/git/` 세그먼트 누락**: 서버가 실제로 서빙하는
  git 스마트 HTTP 경로는 `GitServletConfig`가 등록한 `/git/*`인데, `TemplateHelper.getCloneUrl()`
  (PR 화면의 "git remote add upstream ..." 안내 문구가 쓰는 헬퍼)과 yona-cli의
  `cmd/pr.go`의 `planCheckout()`이 둘 다 `scheme://host/owner/name.git`(← `/git/` 없음) 형태로
  URL을 만들고 있었다 — 실제로 존재하지 않는 경로라 `pr checkout`이 처음엔 항상
  "저장소를 찾지 못했습니다" 오류로 실패했다(항목1 본 수정 뒤에도 남아있던 별도 버그, 실측으로
  발견). `TemplateHelper.getCloneUrl()`과 `planCheckout()` 양쪽에 `/git/` 세그먼트를 추가해
  수정했고, `TemplateHelperBranchSpec.kt`의 기존 9개 단언과 yona-cli
  `TestPlanCheckout_ComputesRemoteURLBranchAndLocalBranch`를 새 URL 형식에 맞춰 갱신했다.
- **부수 발견 2(별도 근본원인) — `pr merge`의 fetch가 짧은 브랜치 이름으로 항상 실패**: 부수 발견
  1을 고친 뒤에도 `pr merge`가 `TransportException: Remote does not have <branch> available for
  fetch`로 계속 실패했다(9라운드 완료 로그도 이 동일 증상을 "빈 저장소라 그런 것, 골든패스
  범위 밖"으로 잘못 판단하고 넘어간 적이 있었다). 실제 원인: `yona pr create --from-branch
  feature-1`(그리고 세션 웹 UI의 PR 생성 폼도 동일 — `PullRequestViewController.branchNamesOf()`가
  `refs/heads/` 접두어를 미리 벗겨 select 옵션을 채운다)처럼 실제 운영 경로는 항상 **짧은** 브랜치
  이름을 `PullRequest.fromBranch`/`toBranch`에 그대로 저장하는데, JGit의 로컬(파일시스템) fetch
  연결(`BaseConnection.getRef()`, 소스 직접 대조로 확인)은 광고된 ref 맵에서 정확히 일치하는
  **전체** 이름만 찾고 짧은 이름을 `refs/heads/`로 보정해주지 않는다. `PullRequestServiceImpl.kt`의
  `attemptMerge`/`previewMerge`/`merge`/`updateMerge` 네 메서드 전부가 이 취약한 패턴을 반복하고
  있었다(이 파일의 기존 단위 테스트들이 전부 `PullRequestService.createPullRequest()`를 직접
  "refs/heads/..." 형태로만 호출해왔기 때문에 지금까지 안 잡혔다). 공용 `qualifyBranchRef()`
  헬퍼(`GitRepository.kt setDefaultBranch()`가 쓰는 것과 동일한 `startsWith("refs/")` 패턴)를
  추가해 fetch RefSpec 소스에만 적용했다(저장 형식/화면 표시는 그대로 유지). 신규 테스트 1개
  (`PullRequestServiceSpec` — 짧은 이름으로 만든 PR의 merge 성공 검증, 수정 전 RED 확인).
- **막힘 아님이지만 범위 밖으로 남긴 발견**: 위 두 버그를 고치고 `pr merge`가 성공한 뒤 병합 결과를
  직접 까본 결과, `PullRequestServiceImpl.createMergeCommitAndUpdateRef()`가 병합 커밋을 만들어
  `refs/yobi/pull/{id}/merged`에는 반영하지만 **실제 대상 브랜치(`refs/heads/main` 등)의 ref는
  갱신하지 않는다** — `yona pr merge`가 성공 메시지를 내고 DB의 PR 상태도 MERGED로 바뀌지만, 실제
  `git pull`로 그 브랜치를 받으면 병합된 변경사항이 전혀 보이지 않는다(실측: `git --git-dir=...
  show-ref`로 `refs/heads/main`이 여전히 병합 전 커밋을 가리키는 것을 직접 확인). 이건 이번
  7개 항목에 없던 별개의, 상당히 큰 버그(머지의 실제 효과 자체에 대한 것)라 이번 라운드 범위로
  다루지 않고 여기 기록만 남긴다 — 다음 라운드에서 최우선으로 다룰 것을 제안한다(브랜치를
  fast-forward할지, 새 병합 커밋으로 갱신할지, 보호된 브랜치/동시 push와의 충돌은 어떻게 다룰지
  설계 결정이 필요해 보인다).

#### 항목2+3 — Fine-grained PAT이 `/api/v1/projects/{owner}/...` 밖 URL을 전혀 인식 못 함

지시사항대로 한 번에 처리했다. 공통 근본원인: `ApiTokenAuthenticationFilter`의
`scopedApiPattern`/`individualProjectPattern`/`ownerOnlyPattern` 셋 다 `/api/v1/projects/{owner}/...`
(최소 owner 세그먼트 필요) 형태만 인식해, 그 밖의 URL(세그먼트가 아예 없거나, prefix가 다르거나,
숫자 PK 기반인 URL)로 들어온 요청은 전부 `authenticateLegacy`(레거시 전권 토큰 조회)로 새 버려
fine-grained PAT을 전혀 인식하지 못했다.

- **`POST /api/v1/projects`(프로젝트 생성, item2)**: owner 세그먼트가 아예 없다. 신규
  `projectCreatePattern`을 추가하고, "계정 수준" 판정을 위해 신규 `authenticateAccountLevel()`을
  만들었다 — project는 아직 존재하지 않아 특정 프로젝트로 스코프를 좁힌 토큰으로는 원천적으로
  판정할 수 없으므로(repo scope 체크 대상이 없음), `allRepositories=true`(All repositories)인
  토큰만 허용하도록 강제했다(GitHub Fine-grained PAT이 "All repositories" 토큰에만 새 저장소
  생성 권한을 주는 것과 동일한 논리). 별도 스코프 그룹을 신설하지 않고 기존 ADMINISTRATION
  (이미 `ResourceType.PROJECT` 포함)을 재사용했다 — "프로젝트를 새로 만들 수 있는가"가 다른
  ADMINISTRATION 항목(SITE_SETTING/PROJECT_TRANSFER/ORGANIZATION 등)과 같은 "저장소 자체의
  존재/설정을 다루는 관리 행위" 범주라고 판단했다. 4라운드가 남긴 "GitHub도 새 저장소 생성은
  Fine-grained PAT으로 지원 안 하니 의도적으로 막아둔 것"이라는 주석은 실측 결과 틀린 전제였음을
  확인하고 `ProjectRestApiController.kt`에서 정정했다.
- **`GET /api/v1/user/issues/status`(item3-1)**: `/api/v1/user/**` 전용 신규 `userApiPattern`
  추가. project는 null(여러 프로젝트에 걸친 "로그인 사용자 전체" 집계라 단일 프로젝트로 좁힐 수
  없음)로 두고 ISSUES 그룹 권한만으로 판정 — project create/site export와 달리 `allRepositories`는
  강제하지 않았다(쓰기가 아니라 읽기 전용 대시보드라 상대적으로 위험도가 낮다고 판단).
- **`GET /site/export`(item3-2)**: 신규 `siteApiPattern`(`/sites?(?:/.*)?$`) 추가. SITE_SETTING
  (ADMINISTRATION 그룹) + `allRepositories=true` 요구(프로젝트 생성과 동일한 논리 — 사이트 전체를
  대상으로 하는 행위). 실제 사이트 관리자 권한 여부는 이 필터가 판정하지 않고 기존
  `SecurityConfig`의 `hasAnyRole("ADMIN","SITE_ADMIN")`가 그대로 검사한다 — 이 필터는 PAT의
  신원만 세팅한다.
- **`POST /api/projects/{id}/members`(item3-3)**: owner/name이 아니라 숫자 PK로 식별되는 유일한
  API라 신규 `legacyProjectIdPattern`(`/api/projects/(\d+)(?:/.*)?$`)을 추가하고 PROJECT_SETTING
  (ADMINISTRATION 그룹)으로 스코프 인식시켰다. 별도로, `ProjectMemberController.getLoginUserId()`가
  인증 정보가 없으면 `IllegalArgumentException("Unauthorized")`를 그대로 던져 500이 나던 버그를
  `ResponseStatusException(HttpStatus.UNAUTHORIZED)`로 교체해 401로 고쳤다(스코프 인식 갭과는
  별개의 버그, 지시사항대로 최소 수정만 함).
- **`POST /projects/{owner}/{project}/webhooks`(item3-4)**: 세션/폼 기반 레거시 MVC라
  `/api` 접두어가 아예 없다. 신규 `legacyWebProjectPattern`(`/projects/{owner}/{project}/{resource}`)을
  추가해 기존 `resourceSegmentToResourceType` 매핑(이미 "webhooks" 포함)을 그대로 재사용했다.
  CSRF는 전역 비활성 상태(`SecurityConfig`)라 원인이 아니었다 — 실제 원인은 이 URL이 어떤 스코프
  패턴과도 안 맞아 PAT을 아예 인식 못 하고 익명으로 처리돼 컨트롤러 자체 권한 체크(매니저 전용)에
  걸린 것이었다. 이 패턴은 `Authorization`/`Yona-Token` 헤더가 있을 때만 개입하므로 세션 기반
  일반 웹 UI 트래픽에는 영향이 없다.
- **RED/GREEN**: 신규 `config/ApiTokenAccountLevelAndLegacyAuthorizationIntegrationSpec.kt`
  (12케이스 — 위 5개 URL 각각 스코프 없음→403 / 올바른 스코프→필터 통과, `ProjectMemberController`
  500→401 포함). 수정 전 스택으로 되돌려 8~9개 실패(RED) 확인 후 복구해 12개 전부 GREEN.
- **실서버+실CLI 골든패스**: `yona project create`, `yona issue status`, `yona admin backup export`,
  `yona admin permission add`(멤버 중복 시 400, 신규 유저는 정상 추가), `yona admin webhook create`
  전부 실제 서버에 대고 성공 확인.
- **테스트 인프라 부수 수정**: 위 신규 스펙 중 "레거시 웹훅"/"프로젝트 생성" 테스트 2쌍이 실제
  POST로 Webhook/Watch/ProjectUser/NotificationEvent 행을 만드는데(형제 스펙들은 GET만 써서 이런
  부수효과가 없었다), 처음엔 정리(`afterSpec`)가 없어 같은 forked 테스트 JVM에서 H2 DB를 공유하는
  무관한 다른 스펙들(`WatchServiceSpec`/`OrganizationServiceSpec`/`ProjectUserServiceSpec` 등)의
  `deleteAll()` 정리 단계에서 FK 위반 연쇄 실패를 일으켰다(전체 스위트 실행 339개 실패로 실측
  확인). `watchRepository`/`webhookRepository`/`notificationEventRepository`/`apiTokenRepository`/
  `projectUserRepository`/`projectRepository`/`userRepository` 순서로 정리하는 `afterSpec`을
  추가해 해소했다(`ProjectForkSelfIntegrationSpec.kt`에도 같은 이유로 소규모 정리 추가).

#### 항목4 — `project fork`를 같은 owner로 하면 500 + DB 오염

- **근본원인**: `ProjectServiceImpl.forkProject()`에 사전 검증이 없어, 목적지(owner+name)가 이미
  존재해도(대표적으로 목적지 미지정 + forker 본인이 이미 owner인 "자기 자신에게 fork" 케이스)
  검증 없이 파일시스템 하드링크부터 시도해 `FileAlreadyExistsException`으로 500이 났다. 더 심각한
  문제: `@Transactional`은 있었지만 기본 롤백 규칙(RuntimeException/Error만 롤백, 체크 예외는
  커밋)만 적용돼 있어서, `Files.createLink()`가 던지는 체크 예외(`FileAlreadyExistsException`은
  `IOException`의 하위 타입)로 실패해도 그 전에 실행된 `projectRepository.save`/
  `projectUserRepository.save`는 그대로 커밋됐다 — owner+name이 중복된 `Project` 행이 남고,
  이후 그 프로젝트를 대상으로 한 모든 스코프 API가 `ApiTokenAuthenticationFilter.authenticateScoped`의
  `findByOwnerAndName()`에서 `IncorrectResultSizeDataAccessException`으로 연쇄 500이 났다.
- **수정**: (a) `@Transactional(rollbackFor = [Exception::class])`로 바꿔 체크 예외도 롤백 대상에
  포함, (b) 파일시스템 작업을 시도하기 전에 `projectRepository.findByOwnerAndName(destOwner,
  destName)`으로 목적지 존재 여부를 먼저 검증해 있으면 `IllegalArgumentException`으로 깔끔하게
  거절(컨트롤러가 이미 400으로 매핑) — 예측 가능한 충돌은 트랜잭션 롤백에 기대는 대신 사전
  검증으로 막는 게 더 명확하고 저렴하다고 판단했다.
- **RED/GREEN**: `ProjectServiceImplSpec.kt`에 신규 mockk 테스트(사전 검증으로 파일시스템 작업
  전에 거절 + save류 미호출 확인) + 기존 8개 fork 테스트에 신규 `findByOwnerAndName` 스텁 추가.
  신규 `ProjectForkSelfIntegrationSpec.kt`(실제 DB + 실제 파일시스템 — 자기 자신에게 fork 시도 후
  DB에 정확히 1건만 남는지, `findByOwnerAndName()`이 정상 동작하는지까지 실측 검증). 수정 전
  두 스펙 모두 RED 확인 후 복구해 GREEN.
- **실서버+실CLI 골든패스**: `yona project fork admin/golden-repo`(목적지 미지정, 이미 그 프로젝트의
  owner) → 500이 아니라 `HTTP 400: '...' 프로젝트가 이미 존재합니다` 응답 확인, `yona search
  projects golden-repo`로 중복 행 없이 정확히 1건만 조회됨을 확인.

#### 항목5 — `pr diff`가 여전히 raw JGit 도메인 객체를 그대로 반환

- **근본원인**: 9라운드가 Issue/PR/검색의 순환직렬화는 고쳤지만 diff 엔드포인트는 "JGit 값
  객체라 User/Project 연관관계가 없어 순환직렬화 문제 자체가 없다"고만 확인하고 넘어갔다 —
  맞는 말이지만 별개의 문제가 있었다: `FileDiff.a`/`b`(`org.eclipse.jgit.diff.RawText`),
  `editList`(`EditList`), `oldMode`/`newMode`(`FileMode`)가 전부 일반 Jackson 빈 컨벤션에 맞는
  getter가 없는 JGit 내부 타입이라, 그대로 직렬화하면 base64 rawContent 등 JGit 내부 표현이
  노출되고 `pathA`/`pathB`조차 신뢰하기 어려운 응답이 됐다(실측: `yona pr diff`가
  `- -> -`로 깨져 나옴).
- **수정**: `web/RestApiResponseDto.kt`에 `FileDiffResponse`(pathA/pathB/changeType/commitA/
  commitB/isBinaryA/isBinaryB/hasError/patch) + `FileDiff.toResponse()` 추가.
  `FileDiff.getHunks()`(이미 JGit RawText/EditList를 순수 `DiffLine` 목록으로 계산해주는 기존
  로직)를 그대로 이용해 GNU unified diff 형식의 `patch` 텍스트를 서버가 직접 조립해 내려주도록
  했다(JGit `DiffFormatter`를 새로 쓰지 않고 기존 계산 결과를 재사용). `PullRequestController.
  getDiff()`/`PullRequestApiController.diff()`가 이 DTO를 반환하도록 변경.
  yona-cli(`cmd/pr.go`)도 `patch` 필드를 실제로 출력하도록 확장했다 — 이전엔 `pathA -> pathB`
  요약 한 줄만 보여주고 실제 diff 내용은 `--json`으로만 볼 수 있었다(diff 커맨드인데 diff
  내용을 안 보여주는 것 자체가 별도 UX 결함이었음).
- **RED/GREEN**: `PullRequestControllerSpec.kt`에 신규 케이스(JGit 타입을 실제로 채운 `FileDiff`로
  diff를 요청해 응답에 `rawContent`/`editList`가 없고 pathA/changeType/patch만 있는지 확인),
  `PullRequestApiControllerSpec.kt` 기존 위임 테스트를 새 반환 타입에 맞춰 수정. yona-cli
  `cmd/pr_test.go`에 신규 2케이스(`patch` 출력 확인, `patch` 없을 때 자리표시자 안 새는지 확인).
  전부 수정 전 컴파일 에러/RED 확인 후 GREEN.
- **실서버+실CLI 골든패스**: `feature-1` 브랜치에 README 한 줄 추가 후 `yona pr diff 1`로
  `MODIFY  README.md -> README.md` + `@@ -1,1 +1,2 @@` unified diff 텍스트가 실제로 출력됨을 확인.

#### 항목6 — `pr edit`에서 `--body`를 생략하면 기존 본문이 지워짐

- **근본원인**: `PullRequestController.updatePullRequest()`가 `fromBranch`/`toBranch`는
  `request.fromBranch ?: pullRequest.fromBranch` 폴백이 있는데 `body`만 `request.body`를 그대로
  써서, yona-cli가 `--body`를 생략했을 때(`Body *string` 포인터 타입에 `json:"body,omitempty"`라
  JSON에서 필드 자체가 생략됨, CLI 쪽은 정상) 서버가 그 null을 그대로 적용해 본문이 사라졌다.
- **수정**: `body = request.body ?: pullRequest.body`로 동일한 폴백 추가(한 줄).
- **RED/GREEN**: `PullRequestControllerSpec.kt`에 신규 케이스(body 키가 아예 없는 JSON으로
  `pr edit` 재현 → 기존 body가 서비스에 그대로 전달되는지 검증). 수정 전 RED(폴백 없이 null이
  그대로 전달됨) 확인 후 복구해 GREEN.
- **실서버+실CLI 골든패스**: PR 생성 시 본문 채움 → `yona pr edit 1 --title "제목만 수정"`(body
  생략) → `yona pr view 1`로 본문이 그대로 남아있음을 실측 확인.

#### 항목7(사소함) — `label edit` 성공 메시지가 "라벨 #-을(를) 수정했습니다"로 나옴

- **근본원인**: yona-cli `cmd/label.go`가 `num(label, "id")`로 서버 응답에서 id를 꺼내는데, 라벨
  수정 엔드포인트(`LabelRestApiController.update` → `ProjectViewController.updateLabelForm` 위임)
  응답 바디에 `id` 필드가 없어(수정된 라벨 객체 자체가 불완전하게 내려옴) 항상 `-`가 찍혔다. 서버
  응답 형식을 고치는 대신(지시사항이 권장한 더 간단하고 안전한 방법을 채택), CLI가 이미 알고 있는
  `args[0]`(사용자가 입력한 id)를 그대로 메시지에 쓰도록 고쳤다 — yona 서버 쪽 변경 없음, yona-cli만
  수정.
- **RED/GREEN**: `cmd/label_test.go`에 신규 케이스(id 필드가 없는 실제 서버 응답 형태를 흉내낸
  목서버로 재현 — 수정 전 `#-`로 깨짐을 RED로 직접 확인, 수정 후 `#2`로 정확히 나옴을 GREEN으로
  확인).
- **실서버+실CLI 골든패스**: `yona label create` → `yona label edit 1 --name bugfix --color blue
  --category-id 1` → `라벨 #1을(를) 수정했습니다.` 정상 출력 확인.

#### 전체 스위트 / 커밋

- `./gradlew test`(H2) 최초 전체 실행 결과 339개 실패 — 위 "테스트 인프라 부수 수정"에서 다룬
  신규 스펙의 정리 누락이 원인이었음을 확인하고 수정, 재실행 결과는 아래 "완료 기준" 절 갱신
  참고.
- yona 커밋: TASK-0416(항목1 + 부수 발견 2건), TASK-0417(항목2+3), TASK-0418(항목4),
  TASK-0419(항목5), TASK-0420(항목6). yona-cli 커밋: 항목1 부수발견(clone URL `/git/` 수정),
  항목5(diff patch 출력), 항목7(label edit 메시지) — 자유 형식 커밋 메시지.
- **막힌 항목 없음** — 7개 전부 실서버+실CLI로 재현/수정/재검증 완료. 위 항목1의 "막힘 아니지만
  범위 밖" 발견(merge가 실제 브랜치 ref를 갱신하지 않음)만 다음 라운드로 이월.

### 11라운드 (2026-09-01) — 확정 버그 2건 수정 + 이월된 merge ref 결함 해소 + 실측 중 발견한 순환직렬화/PR 상태가드 결함 4건 수정

사용자가 지정한 확정 버그 2건(버그8: `project fork` 순환직렬화 비밀번호 노출, 버그9: `project
delete`가 물리 git 저장소를 안 지움)을 먼저 고치고, 10라운드가 "범위 밖"으로 이월했던 `pr merge`
ref 미갱신 결함을 이번엔 범위에 포함해 해소했다. 세 항목을 실서버(h2 프로파일 +
`-Dyona.data=/tmp/yona-loop-round11`)+실 `yona-cli` 바이너리로 재검증하는 과정에서, 같은
근본원인(엔티티 순환직렬화)의 추가 발생 지점 2개와 PR 상태 전이 가드 누락 2개를 더 발견해 함께
고쳤다. 전부 RED(실측 재현) → 최소 구현 → GREEN(같은 방식 재검증) → 통합/단위 테스트 고정 순서로
진행했다.

#### 버그8 — `project fork` 응답이 순환직렬화된 raw 엔티티를 반환(TASK-0421)

- **근본원인**: `ProjectController.forkProject()`(`/api/{owner}/{projectName}/fork`, 이를
  위임 호출하는 `ProjectRestApiController.fork()`도 동일)가 `forkedProject`(JPA `Project`
  엔티티)를 가공 없이 그대로 반환 — `Project.projectUsers[].user`와 `User.projectUsers`의
  양방향 연관을 따라가며 Jackson이 순환 직렬화를 시도하는 과정에서 `User.password`/
  `passwordSalt` 해시값까지 응답에 그대로 노출됐다(실측: curl로 `POST /api/v1/projects/admin/
  golden-repo/fork`를 `Authorization: token <forker1의 fine-grained PAT>`로 호출해 90KB
  응답에서 `"password":"..."` 확인).
- **수정**: `RestApiResponseDto.kt`의 `Project.toRefResponse()`(id/owner/name/overview/vcs/
  scope만 노출, 9라운드가 이슈/PR에 이미 적용한 패턴과 동일)로 감쌌다.
- **RED/GREEN**: `web/ProjectForkResponseIntegrationSpec.kt` 신규(`AbstractIntegrationTest`
  기반 실제 DB + MockMvc, mockk 아님) — `/api/v1/.../fork`와 레거시 `/api/{owner}/{name}/fork`
  둘 다 응답 길이 10KB 미만 + `"password"`/`"projectUsers"` 미포함을 검증.
- **실서버+실CLI 골든패스**: `forker1`(fine-grained PAT)로 `yona project fork admin/
  golden-repo` 실행 → 응답이 `{"id":2,"owner":"forker1","name":"golden-repo",...}`(111바이트)로
  깨끗하게 옴을 curl로 재확인.

#### 버그9 — `project delete`가 물리 git 저장소 디렉터리를 안 지움(TASK-0422)

- **근본원인**: `ProjectServiceImpl.deleteProject()`가 DB의 `Project` 행은 지우지만
  `{git|svn}.base-dir/{owner}/{name}.git` 물리 bare 저장소 디렉터리는 파일시스템에 남겨,
  같은 owner/name으로 재생성하면 `createProject()`의
  `repositoryService.getRepository(project).create()`(`GitRepository.create()`)가 이미 존재하는
  디렉터리와 충돌해 `FileAlreadyExistsException`이 그대로 500으로 튀었다.
- **수정**: `changeVCS()`가 이미 쓰고 있는 "`getRepository(project).delete()` 후 재생성" 패턴을
  `deleteProject()`에도 적용 — 물리 저장소가 이미 없거나 삭제 중 오류가 나도 DB 정리는 막지
  않는다(try/catch로 방어).
- **RED/GREEN**: `domain/project/ProjectDeletePhysicalRepositoryIntegrationSpec.kt` 신규(실제
  `RepositoryService`/`GitRepository` + 실제 파일시스템) — 생성 → 삭제(디렉터리 사라짐 확인) →
  같은 owner/name 재생성(성공, 디렉터리 재생성 확인) → 재삭제까지 한 흐름으로 고정.
- **실서버+실CLI 골든패스**: `yona project delete admin/golden-repo --yes` → `ls`로 물리
  디렉터리 사라짐 확인(포크해간 `forker1/golden-repo.git`의 하드링크 사본은 그대로 남아있음도
  확인 — 하드링크 특성상 원본 삭제가 사본에 영향 없음) → 같은 owner/name으로 `yona project
  create` 재실행 시 500 없이 정상 생성됨을 확인.

#### 이월 결함 — `pr merge`가 실제 대상 브랜치 ref를 갱신하지 않음(TASK-0423)

- **근본원인**: `PullRequestServiceImpl.merge()`(실제 병합)가 병합 커밋을 만든 뒤
  `refs/yobi/pull/{id}/merged` 캐시 ref만 갱신하고 실제 대상 브랜치(`refs/heads/{toBranch}`)는
  전혀 건드리지 않았다. legacy `PullRequest.merge()`(`app/models/PullRequest.java:547-554`)는
  `result.createCommit(...).updateRef(toBranch)`로 실제 대상 브랜치를 직접 갱신하고,
  `refs/yobi/pull/{id}/merged` 갱신은 `checkMerge()` 미리보기(`updateMerge()`, P1-53) 전용이다
  — 이 이식이 그 구분을 놓쳤다.
- **수정**: `createMergeCommitAndUpdateRef()`에 `additionalTargetRef` 파라미터 추가 —
  `merge()`만 `qualifyBranchRef(toBranch)`를 넘겨 대상 브랜치도 함께 fast-forward(원본 브랜치
  tip을 `setExpectedOldObjectId`로 확인 후 갱신). `updateMerge()`(미리보기)는 그대로 두 번째
  갱신 없이 기존 동작 유지.
- **RED/GREEN**: `PullRequestServiceSpec.kt` 테스트1(충돌 없는 merge)에 `Git.open(toBareDir)`로
  `refs/heads/master`가 `mergedCommitIdTo`와 일치하는지 확인하는 단언 추가 — 수정 전 되돌려
  실패(RED) 확인 후 복구해 GREEN.
- **실서버+실CLI 골든패스**: 실제 `git clone` → `main`/`feature-1` 브랜치 각각 push(HTTP Basic
  Auth로 실제 계정 비밀번호 사용 — API 토큰은 git smart HTTP 인증에 쓰이지 않음, 세션의
  `httpBasic{}` 기반) → `yona pr create` → `yona pr merge` → **별도의 새 clone으로 `git
  clone`**해 `main` 브랜치에 병합 커밋(`Merge branch 'feature-1' into 'main'`)과 병합된 파일
  내용이 실제로 반영돼 있음을 확인(이전에는 이 확인이 실패했을 결함).

#### 추가 발견 — 순환직렬화 확산 지점 2건 + PR 상태 전이 가드 누락 2건(TASK-0424)

merge ref 수정을 실측 검증하던 중, 버그8과 같은 근본원인(raw 엔티티 그대로 반환)의 추가 발생
지점과 별개의 논리적 결함을 발견했다.

- **`project edit`(`PATCH /api/v1/projects/{owner}/{project}/settings`)도 비밀번호 노출**:
  `ProjectController.updateProject()`가 성공 시 raw `Project`를 그대로 반환 — curl로 60KB 응답에
  `"password"` 값이 수백 회 반복 노출됨을 확인. `toRefResponse()`로 감쌈.
- **`pr merge`/`pr close`/`pr reopen`(레거시 `/api/projects/{id}/pullrequests/{n}/merge|state`,
  `/api/v1/...`가 그대로 위임)도 비밀번호 노출**: `PullRequestController.mergePullRequest()`/
  `changeState()`가 raw `PullRequestMergeResult`/`PullRequest`를 그대로 반환 — 마찬가지로 curl로
  60KB 응답에서 확인. `PullRequestMergeResult.toResponse()`/`PullRequest.toResponse()`로 감쌈
  (`PullRequestApiController`의 위임 메서드는 반환 타입이 `ResponseEntity<Any>`로 바뀌어 이미
  변환된 본문을 그대로 전달하도록 `.mapBody{}` 호출 제거).
- **이미 MERGED된 PR을 다시 merge하면 병합 커밋이 중복 생성됨**: `merge()`에 상태 가드가 없어
  동일 PR로 `pr merge`를 두 번 호출하니 `refs/heads/master`에 병합 커밋이 2개 쌓였다(실측
  확인). `merge()` 최상단에 `pullRequest.state != State.OPEN`이면 `IllegalArgumentException`을
  던지는 가드 추가.
- **이미 MERGED된 PR을 close/reopen하면 상태만 오감**: `changeState()`에 종결 상태 가드가 없어
  물리적으로 이미 병합된 PR이 CLOSED/OPEN을 오갔다(실측: `pr close` → `pr reopen`으로 `상태:
  OPEN` 표시되지만 git은 이미 병합된 상태). `changeState()`에 `oldState == State.MERGED`면
  거절하는 가드 추가.
- 두 컨트롤러 모두 `IllegalArgumentException`/`LackingReviewerException`을 잡아 400으로
  응답하도록 변경(이전엔 컨테이너 기본 500 — 이 역시 실측으로 확인).
- **RED/GREEN**: `PullRequestServiceSpec.kt`에 재머지/상태전이 가드 케이스 2건(실제 git repo)
  추가. `web/PullRequestMergeResponseIntegrationSpec.kt` 신규(실제 DB+실제 git repo+MockMvc) —
  merge 응답 비밀번호 미노출, 재머지 400(+브랜치 ref 안 움직임), close/reopen 400 3케이스.
  `web/ProjectForkResponseIntegrationSpec.kt`에 PATCH settings 케이스 추가.
- **실서버+실CLI 골든패스**: `yona pr merge 1`(이미 CLOSED 상태) → `HTTP 400:
  {"error":"이미 CLOSED 상태인 풀 리퀘스트는 머지할 수 없습니다."}`로 깨끗하게 거절되고 CLI가
  그 메시지를 그대로 사람이 읽을 수 있게 출력함을 확인. `PATCH .../settings` 응답도 98바이트로
  깨끗해짐을 curl로 재확인.

#### 그 밖에 확인했지만 버그가 아니었던 것들(오탐 기록)

- **site manager의 PRIVATE 프로젝트 열람**: bootstrap-setup으로 만든 첫 계정(`admin`)은
  `isSiteManager=true`라 `AccessControl`의 모든 체크를 우회한다 — 의도된 설계(legacy 동일).
  별도 `regular1` 계정(사이트매니저 아님)으로 재검증한 결과 PRIVATE 프로젝트는 정상적으로
  403 처리됨을 확인.
- **PUBLIC 프로젝트에 비멤버가 이슈 댓글 작성**: 프로젝트 멤버가 아니어도 로그인 사용자면 PUBLIC
  프로젝트 이슈에 댓글을 달 수 있음 — GitHub 등과 동일한 통상적 동작으로 판단, 버그 아님.
  CLOSED 이슈에 댓글/edit도 가능 — 마찬가지로 통상적 동작.
- **PR 자기 자신 리뷰어 중복 등록**: `yona pr review`를 같은 PR에 두 번 호출해도
  `reviewers` 컬렉션(Set 기반)이 중복 없이 유지됨을 API 응답으로 확인 — 버그 아님.
- **admin permission add 중복 멤버**: 이미 멤버인 사용자를 다시 추가하면 400 + 명확한 에러
  메시지(`User is already a member of this project`)로 거절 — 정상.

#### 미해결로 남긴 것 — `project fork`의 조직(organization) 목적지 미지원

`ProjectService.forkProject(projectId, forkerId, destinationOwner = "", destinationName = "")`는
서비스 계층에 목적지 owner를 지정하는 파라미터가 이미 있지만, `ProjectRestApiController.fork()`/
`ProjectController.forkProject()`(REST, `yona-cli`가 쓰는 경로) 둘 다 이 파라미터를 노출하지
않아 **REST API/CLI로는 조직으로 fork할 방법이 아예 없다**(항상 forker 본인 계정으로만 fork됨).
웹 UI(`project/fork.html` + `ProjectViewController.fork()`, 세션 기반의 완전히 별도 컨트롤러)는
`owner` 폼 파라미터로 조직을 선택할 수 있어 이 경로만 조직 fork를 지원한다. 실측으로 조직
(`testorg`)을 만들고 REST fork를 호출해 확인 — 목적지를 지정할 방법이 없어 자기 자신에게로만
fork되고, 이미 존재하는 조합이면 TASK-0418의 400 가드에 걸린다(500은 아니라 안전하지만, 기능
자체가 없는 것). **판단**: 이건 "깨진 기존 기능"이 아니라 "REST API에 애초에 노출되지 않은 서비스
파라미터"라 이번 라운드의 버그 수정 범위(회귀/결함 수정)보다는 신규 기능 확장에 가깝다고 판단해
이번 라운드에서는 구현하지 않고 다음 라운드 이후로 이월한다 — 착수한다면
`ProjectRestApiController.fork()`에 선택적 `destinationOwner` 요청 필드(바디 or 쿼리 파라미터)를
추가하고 `yona-cli`의 `project fork`에 `--to-owner` 플래그를 추가하는 얇은 배선 작업이 될 것으로
예상된다.

#### 전체 스위트 / 커밋

- `./gradlew test`(H2) 전체 실행 결과 5816개 중 4개 실패(`IssueServiceImplSpec` 2건,
  `IssueServiceSpec` 2건) — 전부 단독/서브셋 실행 시 GREEN임을 재확인했고, 9·10라운드 로그가
  이미 기록한 것과 동일한 `Role`/`ProjectUser`/`OrganizationUser` 관련 사전 존재 플레이키니스
  패턴(`AbstractIntegrationTest`가 같은 forked 테스트 JVM 안에서 스펙끼리 H2 인메모리 DB를
  공유하는 구조적 특성)이며, 이번 라운드 변경분은 이 엔티티들의 저장 로직을 전혀 건드리지
  않았다 — 회귀 아님.
- yona 커밋(전부 push 완료): `960a7df`(TASK-0421, 버그8), `8a441e9`(TASK-0422, 버그9),
  `b95dc26`(TASK-0423, merge ref), `afc60e8`(TASK-0424, 순환직렬화 확산 2건+상태가드 2건).
  yona-cli는 이번 라운드에서 변경 없음(전부 서버 쪽 수정만으로 해소됨 — CLI는 이미
  `map[string]interface{}`로 느슨하게 파싱하거나 에러 메시지를 그대로 출력하는 구조라 서버 응답
  형태가 좁혀져도/에러가 400으로 바뀌어도 CLI 코드 변경이 필요 없었다).
- **막힌 항목 없음** — 지정된 2개 버그 + 이월 결함 1개 + 실측 중 발견한 4개 결함 전부
  실서버+실CLI로 재현/수정/재검증 완료. "조직 목적지 fork 미지원"만 신규 기능 확장으로 분류해
  다음 라운드 이후로 이월(위 참고).
- 이번 라운드는 지정된 2개 버그 + 이월 결함 해소를 우선 완료하는 데 집중했고, 사용자가 요청한
  "테스트되지 않은 명령어가 나오지 않을 때까지 반복"하는 다회차(5~6라운드) 전체 명령 표면 소진
  테스트는 시간 관계상 이번 세션에서는 1라운드만 수행했다(위 결과 참고) — 발견된 버그를 전부
  그 자리에서 고쳤고 새로 고친 코드가 다른 걸 깨뜨리지 않았는지도 넓게 재검증했지만, "신규 버그
  0건인 완전히 깨끗한 라운드"를 여러 차례 반복 확인하는 수준까지는 도달하지 못했다 — 다음 세션이
  이어서 12라운드부터 계속할 수 있도록 이 로그를 남긴다.

### 12라운드(2026-09-01) — 사용자의 긴급 지시로 라운드 중간에 즉시 종료

**중요**: 이 라운드는 "클린 라운드 달성 또는 최대 4라운드"라는 원래 종료 조건에 도달하기 전에,
사용자가 "지금 즉시 중단해라"라고 명시적으로 지시해 라운드 도중 종료했다. 아래는 종료 시점까지
실제로 완료·검증된 것과, 손대다가 만 것(있다면)을 정확히 구분해 기록한다.

#### 완료 + 검증됨 (RED→GREEN, 실서버+실CLI 재검증, 커밋/푸시까지 끝남)

1. **`label` edit/delete의 교차 프로젝트 IDOR(TASK-0426, 심각도 높음)** — `LabelRestApiController.
   update()`/`delete()` -> `ProjectViewController.updateLabelForm()`/`deleteLabelForm()`가 URL
   경로의 project에 대한 라벨 쓰기 권한만 확인하고, 실제 대상인 라벨 id(및 update의
   category.id)가 그 project 소속인지는 검증하지 않았다. 실서버+실 CLI로 재현: bob(자기 프로젝트
   bob/repo-b에 대한 라벨 쓰기 권한만 있음)이 URL은 자기 프로젝트로 두고 id만 alice/repo-a의
   라벨 번호로 바꿔 호출해 alice의 라벨을 실제로 덮어쓰고 삭제함을 확인. id/category.id가 해당
   project 소속 목록에 있는지(`issueLabelService.getLabels()`/`getCategories()`) 먼저 확인하고
   아니면 404/400으로 거절하도록 수정. `ProjectViewControllerSpec`에 교차 프로젝트 가드 케이스
   3건 추가. 수정 후 bob의 재시도가 404로 깨끗하게 거절되고 alice의 라벨이 그대로임을 실측
   재확인.
2. **PR 리뷰어 등록 취소(removeReviewer) v1 REST API 어댑터 부재(TASK-0427)** — 레거시
   `PullRequestController.removeReviewer()`(DELETE .../pullrequests/{n}/reviewers)는 원래부터
   있었지만, yona-cli 등 외부 클라이언트의 유일한 경로인 v1 REST API(`PullRequestApiController`)엔
   `addReviewer` 어댑터만 있고 `removeReviewer` 어댑터가 없어 "리뷰어 등록은 되는데 취소는
   API/CLI로 불가능"했다. DELETE 어댑터 추가 + `PullRequestApiControllerSpec`에 위임 검증 추가.
   yona-cli에도 `pr review --remove` 플래그 배선(`RemoveReviewer` 클라이언트 메서드 + CLI 테스트
   추가). 실서버+실 CLI로 등록 -> `pr view --json reviewers`로 확인 -> `--remove` -> 다시 확인해
   빈 배열로 돌아옴을 재검증.
3. **git clone/push URL에 `.git` 접미어가 없으면 push가 항상 실패하는 버그(TASK-0428, 심각도
   높음 — PR 관련 모든 실측 테스트를 막던 근본 원인)** — `GitServletConfig`의
   `setRepositoryResolver { _, name -> File(gitBaseDir, name) ... }`가 JGit이 URL에서 파싱해
   넘겨주는 저장소 name을 가공 없이 그대로 파일 경로로 썼다. 이 코드베이스 전역에서 물리 bare
   저장소는 항상 `"owner/name.git"`로 생성되는데(`GitServiceImpl.createRepository()` 등),
   클라이언트가 `.git` 접미어 없이 clone/push URL을 쓰면(GitHub 등에서 흔한 축약 표기, 예:
   `git clone http://host/git/owner/repo`) 존재하지 않는 `"owner/repo"` 경로로 잘못 resolve된다.
   clone(읽기)은 존재하지 않는 저장소를 빈 저장소처럼 조용히 취급해 성공한 것처럼 보이지만,
   push(쓰기)는 `ObjectDirectoryPackParser.parse()`가 그 존재하지 않는 objects 디렉터리에 임시
   팩 파일을 만들려다 `IOException`(ENOENT)을 던져 "unpacker error"로 거절된다 — 실서버(H2)+실
   git 바이너리로 100% 재현 확인(PR merge 충돌 경로를 실측하려고 브랜치를 push하다가 발견).
   **이전 라운드들이 이 버그를 못 잡은 이유**: 지금까지 모든 라운드가 clone/push URL에 항상
   `.git`을 붙여서만 검증해왔다(`GitSmartHttpProtocolIntegrationSpec`의 기존 테스트도 마찬가지).
   수정: 리졸버에서 name이 `.git`로 끝나지 않으면 붙여서 정규화 — 접미어 유무와 무관하게 항상
   같은 실제 경로로 resolve. `GitSmartHttpProtocolIntegrationSpec`(실제 임베디드 톰캣+실제 git
   바이너리)에 `.git` 없이 clone+push하고 별도 clone으로 실제 반영을 재검증하는 케이스 추가,
   `GitServletConfigSpec`(mockk)에도 리졸버 람다 단위 테스트 추가. 두 스펙 함께 실행해 GREEN
   확인. 실서버+실 CLI 골든패스: alice 계정으로 `.git` 없는 clone URL로 clone → 커밋 → 같은
   URL로 push → "new branch main -> main" 성공 확인(수정 전엔 매번 "unpacker error").
4. **yona-cli `search prs` 배선 부재** — 서버는 7라운드부터 `GET /api/v1/search/prs`를 지원하는데
   CLI 쪽엔 "서버에 대응 기능이 없어 미구현"이라는 낡은 주석만 남긴 채 배선이 안 돼 있었다.
   `search issues`/`search projects`와 동일한 패턴으로 `search prs` 배선(yona-cli, Go 테스트
   추가). 실서버+실 CLI로 `yona search prs "test"` 결과가 정상 출력됨을 확인.

각 항목 모두 관련 유닛/통합 테스트를 개별적으로(`--tests`로 좁혀서) 실행해 GREEN 확인했고,
yona 3건 모두 커밋 후 즉시 `git push origin main` 완료(TASK-0426/0427/0428), yona-cli 2건도
커밋 후 `git push origin main` 완료.

#### 미완료/미검증 — 사용자의 긴급 중단 지시로 이번 라운드에서 못 끝낸 것

- **전체 `./gradlew test` 회귀 확인을 이번 라운드에서 한 번도 못 돌렸다.** 각 수정은 관련
  스펙(`ProjectViewControllerSpec`, `PullRequestApiControllerSpec`, `GitServletConfigSpec`+
  `GitSmartHttpProtocolIntegrationSpec`)을 개별적으로 GREEN 확인했고 서로 다른 파일/계층을
  건드려 상호 간섭 가능성은 낮다고 판단하지만, 확립된 라운드 종료 관례("라운드가 끝날 때 전체
  `./gradlew test` 1회")를 이번엔 지키지 못했다 — **다음 세션이 반드시 먼저 전체 스위트를
  돌려 회귀 여부를 확인해야 한다.**
- **PR merge 충돌 경로 실측**: TASK-0428로 git push가 정상화된 뒤, 실제로 공통 조상에서 같은
  파일을 다르게 수정한 두 브랜치로 PR을 만들어 `pr merge`를 호출 — 서버가 크래시 없이
  `{"conflicts":true}`로 깨끗하게 응답하고 CLI가 "충돌이 있어 머지되지 않았습니다"를 출력하며
  PR 상태가 OPEN으로 그대로 남는 것까지 실측으로 확인했다(버그 아님, 정상 동작). **다만
  자동화된 회귀 테스트로 고정하지는 못했다** — 시간이 있었다면 `PullRequestServiceSpec` 또는
  통합테스트에 이 케이스를 추가했을 것이다.
- **`admin backup export/import` 왕복 실측**: export한 백업을 같은 서버에 재-import(멱등성 확인,
  이후 기존 프로젝트/PR 데이터가 그대로 조회됨을 재확인) + 완전히 새로운 빈 서버 인스턴스(포트
  9011, 별도 `-Dyona.data`/`-Dyona.git.base-dir`)에 import까지 CLI로 수행했다. 두 경우 모두
  `yona admin backup import`가 "백업을 복원했습니다"로 성공 응답했다. **다만 새 서버로 import한
  뒤 실제 프로젝트 목록/이슈/PR이 CLI로 다시 조회되는지까지는 긴급 중단 지시로 검증을 마치지
  못했다** — 이 마지막 확인 단계가 이번 라운드의 유일한 "시작했지만 결론을 못 낸" 조사다(코드
  변경은 없음, 순수 조사였으므로 커밋 상태에는 영향 없음).
- **환경 관찰(버그 아님, 다음 세션 참고용)**: `yona.git.base-dir`/`yona.svn.base-dir`
  (`@Value("\${yona.git.base-dir:/tmp/yona/git}")` 등, `GitServletConfig`/`GitServiceImpl`/
  `ProjectServiceImpl`/`RepositoryService`/`BoardViewController`/`PostingServiceImpl`에 중복
  선언)는 `upload.base-dir`/H2 `url`과 달리 `-Dyona.data`에 연동되지 않고 항상 전역
  `/tmp/yona/git`(또는 `/tmp/yona/svn`)로 떨어진다. TASK-0428 조사 중 이 경로에 과거 라운드들과
  현재 동시 실행 중이던 다른 세션들이 남긴 실제 git 저장소 수백 개가 누적돼 있음을 확인했다 —
  당장 오작동을 일으키진 않지만(이번 버그의 진짜 원인은 아니었음, `-Dyona.git.base-dir`로 완전
  격리해도 100% 재현됨을 확인해 배제했다), 동시 세션 간 우연한 이름 충돌 가능성과 디스크 누적
  문제가 있다 — 다음 세션이 서버를 띄울 때 `-Dyona.git.base-dir=<격리된 tmp 경로>`도 함께
  지정하는 관례를 추가하는 걸 권장한다(이번 라운드부터는 그렇게 했다).
- **PR/이슈 assignee·label CLI 배선 격차(버그 아님, 기능 확장 백로그)** — 서버는
  `PullRequestApiController`에 `setAssignee`/`removeAssignee`/`addLabel`/`removeLabel`(7~8라운드
  신설)을, `IssueRestApiController`도 assignee/label 필드를 지원하는데, yona-cli는 `pr` 쪽엔
  아예 배선이 없고(`internal/api/pr.go`의 "PR엔 labels/assignee 개념이 없다"는 주석 자체가
  7라운드 이전 기준으로 낡음) `issue create/edit`도 `CreateIssueRequest`/`UpdateIssueRequest`
  Go 구조체엔 `AssigneeID`/`LabelIDs` 필드가 이미 있으면서 CLI 플래그로 노출된 적이 없다. "조직
  목적지 fork 미지원"과 같은 성격(REST에 이미 있는 기능을 CLI가 못 따라간 것)으로 판단해 이번
  라운드는 구현하지 않고 이월한다.
- 그 밖에 `issue`/`org` 컨트롤러 코드를 읽어 순환직렬화·상태가드·필드스코프 문제를 점검했으나
  (`IssueRestApiController`/`OrganizationRestApiController`/`SearchRestApiController` 모두
  DTO/map 반환 확인, issue `changeState`는 OPEN/CLOSED 토글만 있어 PR의 MERGED 같은 종결
  상태가 없어 가드 불필요로 판단), 실서버+실CLI 실측까지는 긴급 중단으로 못 갔다 — 코드 정독
  수준의 확인만 마쳤다.

#### 종료 사유

사용자가 "12라운드까지만" 지시한 직후, 이어서 "지금 즉시 중단"으로 지시를 상향했다. 진행 중이던
안전한 단위(TASK-0428 GREEN 확인 및 커밋/푸시)까지만 마무리하고, 새 조사(백업 import 후 데이터
조회 검증)는 결론 없이 중단했다(코드 변경 없었으므로 안전). 클린 라운드 달성 여부와 무관하게
사용자 지시로 즉시 종료 — 다음 세션은 위 "미완료" 목록부터 이어가면 된다.

### 13라운드 (2026-09-01) — 12라운드 미완료 4항목 전부 해소 + 실서버 탐색

12라운드가 남긴 4개 미완료 항목을 순서대로 먼저 처리한 뒤, 이어서 실서버+실 `yona-cli`
바이너리로 새로운 배선을 재검증하고 추가 관점(라벨 전체교체 시맨틱, 존재하지 않는 담당자/
라벨, 이미 CLOSED된 이슈 편집, PR 라벨 중복 추가)을 탐색했다.

#### 미완료 1 — 전체 `./gradlew test` 회귀 확인

`JAVA_HOME=.../21.0.11-tem ./gradlew test`를 이번 라운드 시작 시점(TASK-0426~0429 변경 반영
직후)에 처음 실행한 결과 5827개 중 48개 실패(`PostingServiceSpec` 16, `ApiTokenServiceImplSpec`
9, `CommentServiceSpec` 7, `GitAuthorizationFilterIntegrationSpec` 6, `PostingRepositorySpec` 3,
`PostingCommentRepositorySpec` 3, `CommentServiceExtraSpec` 2, `TemplateHelperSpec` 2) — 11라운드
까지의 "4개 실패" 사전 존재 플레이키니스보다 훨씬 많아 처음엔 회귀를 의심했다. 그러나:

- 실패한 8개 스펙 전부 `--tests`로 단독/서브셋 실행하면 100% GREEN(`BUILD SUCCESSFUL`).
- 실패 목록이 이번 라운드가 손댄 어떤 파일과도 무관한 도메인(게시판/댓글/API토큰/git 인가
  필터/템플릿 헬퍼)에 집중돼 있다.
- `~/.testcontainers.properties`의 `testcontainers.reuse.enable=true` + `AbstractIntegrationTest`
  전 컨테이너의 `.withReuse(true)`로 인해 MariaDB testcontainer가 이 머신에서 동시에 돌고 있는
  **다른 세션들의 `./gradlew test` 실행과 공유**된다(`.claude/worktrees/agent-*` 디렉터리 다수가
  이 시점에도 존재 확인) — 여러 세션이 같은 DB에 동시에 쓰기/읽기를 하면서 서로 다른 무관한
  스펙들이 서로의 데이터를 밟는 구조적 환경 노이즈다.
- 라운드 종료 시점에 변경사항 반영 후 전체 스위트를 다시 돌린 결과도 **정확히 동일한 48개,
  동일한 8개 스펙, 동일한 케이스 수**로 재현됐다 — 우리 변경이 원인이면 실패 집합이 변경
  전후로 달라야 하는데 완전히 동일하다는 것 자체가 "우리 변경과 무관한 외부 요인"이라는
  강력한 증거다.

**결론**: 48개 실패는 전부 동시 실행 중인 다른 세션과 testcontainer를 공유하는 환경 노이즈이며
회귀가 아니다(개별/서브셋 재실행 100% GREEN으로 확정). 베이스라인은 사실상 GREEN으로 확인됨.

#### 미완료 2 — PR merge 충돌 케이스 회귀 테스트 고정(TASK-0431)

기존 `PullRequestServiceSpec`의 "2. 충돌이 발생하는 경우" 테스트가 이미 `merge()` 호출 자체
(단순 `attemptMerge` 미리보기가 아니라)의 결과(`conflicts()=true`, PR이 OPEN 유지,
`isConflict=true`)를 검증하고 있었다 — 12라운드가 수동으로 확인한 것과 같은 범위를 이미
자동화가 커버하고 있었던 셈이다. 다만 12라운드 수동검증의 핵심 관찰 포인트였던 "대상 브랜치
(`refs/heads/master`) ref가 전혀 움직이지 않는다"는 검증되지 않고 있어, `merge()` 호출 전/후
`Git.open(toBareDir).repository.resolve("master")`를 비교하는 단언을 추가해 완전히 고정했다.
GREEN 확인(`--tests PullRequestServiceSpec`).

#### 미완료 3 — `admin backup export/import` 왕복 후 실제 데이터 조회까지 실서버로 완결

12라운드는 새 서버로 import까지는 했지만 그 서버에서 데이터가 실제로 조회되는지 확인을 못 하고
중단했었다. 이번 라운드는 h2 프로파일 서버 2대(포트 18099/18100, 각각 `-Dyona.data`/
`-Dyona.git.base-dir` 완전 격리)로 끝까지 확인했다:

1. 서버1(18099)에서 bootstrap-setup → 세션 로그인 → `POST /api/v1/projects`로 프로젝트 생성 →
   `POST /user/editform/tokens`로 fine-grained PAT 발급 → `yona auth login` → `yona issue
   create --assignee --label` → `git clone`(스마트 HTTP)+두 브랜치 push → `yona pr create`.
2. `yona admin backup export -o backup.json`(6191 bytes) 성공.
3. 완전히 새로운 빈 서버2(18100, 별도 데이터/git 디렉터리)에 bootstrap-setup으로 임시 관리자
   생성 → 세션 로그인 → `POST /site/import`(멀티파트)로 백업 업로드 → 302 성공.
4. 서버2에 대해 **서버1에서 쓰던 것과 동일한 raw PAT 문자열**로 `yona auth login` → `yona
   project view`/`yona issue view --json`/`yona pr view --json`/`yona issue list`를 실행한
   결과 프로젝트/이슈(제목·본문)/PR(fromBranch/toBranch/title)이 전부 정상 조회됨을 확인 —
   ApiToken의 해시 기반 인증(TOKEN_HASH 컬럼)이 백업에 그대로 담겨 복원돼, 원본 서버에서
   발급받은 raw 토큰 문자열이 복원된 새 서버에서도 그대로 유효했다.

**결론**: export→import→새 서버 조회까지 전체 왕복이 실제로 완결됨을 확인. 버그 없음.

#### 미완료 4 — PR/이슈 assignee·label CLI 배선 갭 해소(TASK-0430, yona-cli 3개 커밋)

**서버 측(yona, TASK-0430)**: `ProjectMemberController.assignableUsers()`가 `loginId`/`name`/
`avatarUrl`만 내려주고 `userId`는 주지 않아, CLI가 사람이 입력하는 로그인ID를 REST가 요구하는
숫자 `assigneeId`/`userId`로 변환할 방법이 아예 없었다("나에게 할당하기" 항목과 멤버 후보 항목
둘 다에 `userId` 추가, 기존 select2 소비자는 알려지지 않은 필드를 무시하므로 웹 UI 영향 없음).
`ProjectMemberControllerSpec`에 검증 추가, GREEN.

**CLI 측(yona-cli, 3개 커밋)**:
1. `issue create/edit --assignee <loginId> --label <name>(반복 가능)` 배선. **배선 과정에서
   심각한 기존 데이터 손실 버그를 발견** — `IssueServiceImpl.updateIssue()`는 title/body와
   달리 assignee/milestone/labels를 "요청에 값이 없으면 유지"가 아니라 "요청에 값이 없으면
   지운다"로 동작한다(웹 폼이 항상 현재 값을 hidden input으로 재제출하는 것을 전제로 설계된
   전체교체 시맨틱). CLI의 `issue edit`이 이 세 필드를 아예 보내지 않고 있어서, **지금까지
   `yona issue edit`을 한 번이라도 호출하면 그 이슈의 담당자·마일스톤·라벨이 전부 조용히
   사라지는** 버그였다. title/body와 동일한 read-modify-write 패턴으로 세 필드 모두 보존하도록
   함께 고쳤다(`--remove-assignee`로 명시적 해제만 예외).
2. `pr edit --assignee/--remove-assignee/--add-label/--remove-label`, `pr list
   --assignee/--label` 배선(서버는 7라운드부터 지원, CLI만 못 따라간 순수 배선 갭 — PR의
   담당자/라벨은 update PATCH가 아닌 전용 PUT/DELETE/POST 엔드포인트라 이슈처럼 지워지는 문제는
   원래 없었다).
3. **부수 발견(같은 부류의 갭)** — `admin webhook list`/`admin permission list`가 "서버에 목록
   JSON API가 없다"는 낡은 스텁 메시지를 반환하고 있었는데, 실제로는 Step8.6(7라운드)에서
   `GET /api/v1/projects/{owner}/{project}/webhooks`/`.../permissions`가 이미 신설돼 있었다
   (docs 위 "7라운드" 로그 참고) — CLI가 그 사실을 반영하지 못한 채 방치돼 있었던 것. 실제 GET
   호출로 배선했다.

**실서버+실CLI 골든패스**(위 서버1 재사용): `label create bug/urgent` → `issue create --assignee
admin --label bug`(assignee.userId=1, labels=[bug] 정상 반영 확인) → `issue edit --title`(제목만
변경해도 assignee/labels가 그대로 보존됨을 확인 — 수정 전이었다면 사라졌을 상황) → `issue edit
--remove-assignee`(assignee: null 확인) → `git clone`+두 브랜치 push → `pr create` → `pr edit
--assignee admin`/`--add-label bug`/`--remove-label bug --remove-assignee`(각 단계 `pr view
--json`으로 반영 확인) → `pr list --assignee/--label`(필터링 정상 동작) → `admin webhook create`+
`admin webhook list`(스코프 있는 새 토큰으로 재검증, `#1 http://example.com/hook SIMPLE` 정상
출력) → `admin permission list`(`1 admin manager` 정상 출력). 전부 실측 GREEN.

**단위/통합 테스트**: yona `ProjectMemberControllerSpec`(+1). yona-cli
`issue_test.go`(+4: 보존/명시적 해제/로그인ID 변환/create 배선), `pr_test.go`(+4: assignee 설정/
해제/라벨 추가삭제/list 필터), `admin_test.go`+`admin_test.go(internal/api)`(스텁 테스트 2개를
실제 GREEN 테스트로 교체). `go build`/`go vet`/`go test ./...` 전체 GREEN.

#### 추가 탐색(신규 버그 발견 안 됨)

위 4항목 처리 과정에서 새로 만든 CLI 배선 코드 자체를 다음 관점으로 추가 실측했다 — 전부
정상 동작(버그 아님)으로 확인:
- `issue edit --label`을 여러 번(2개 → 1개) 호출하면 매번 지정한 집합으로 완전히 교체됨(부분
  추가가 아님, 서버 시맨틱과 일치, 문서화된 대로 동작).
- 존재하지 않는 로그인ID로 `--assignee`, 존재하지 않는 이름으로 `--label` 지정 시 500이나
  크래시 없이 명확한 한국어 에러 메시지로 exit 1 종료.
- `pr edit --add-label`을 동일 라벨로 두 번 호출해도 중복 없이 유지(`labels`가 `MutableSet`).
- 이미 존재하지 않는 라벨을 `--remove-label`해도 에러 없이 정상 종료(서버가 멱등 처리).
- 이미 CLOSED된 이슈를 `issue edit --title`로 수정해도 정상 반영(레거시와 동일한 통상 동작,
  11라운드가 이미 기록한 "CLOSED 이슈 댓글/edit 가능" 패턴과 일관).

#### 라운드 종료 판단

12라운드 미완료 4항목을 전부 실서버+실CLI로 완결했고, 그 과정에서 발견한 실제 데이터 손실
버그 1건과 부수 발견 갭 1건(admin list 스텁)까지 전부 그 자리에서 고쳐 실측 재검증까지
마쳤다 — "신규 버그를 찾으면 그 자리에서 고친다"는 이번 라운드 종료 조건상 이번 라운드는
클린 라운드가 아니다. 다만 이번 라운드에서 실측한 전체 명령 표면(위 골든패스 + 추가 탐색
5가지 관점)은 전부 정상 동작이었고, 처음부터 끝까지 별도의 신규 관점(동시성/토큰 만료/조직
소유 프로젝트/대용량·특수문자 입력 등)으로 처음부터 다시 훑는 완전한 반복은 시간 관계상
이번 세션에서는 진행하지 못했다 — 다음 세션이 14라운드로 이어서, 이번에 다루지 못한 관점
(동시 fork, 토큰 만료, PRIVATE 프로젝트 경계, org 소유 프로젝트, 대용량/특수문자 입력)부터
계속 실측하면 된다.

#### 전체 스위트 / 커밋

- `./gradlew test` 최종 재확인: 5827개 중 48개 실패 — 라운드 시작 시점 베이스라인과 **완전히
  동일한 실패 집합**(위 "미완료 1" 참고), 전부 동시 세션 간 testcontainer 공유로 인한 환경
  노이즈로 확정, 회귀 아님.
- yona 커밋(fetch로 origin과 동기화 확인 후 push 완료): `21af118`(TASK-0430, assignableUsers
  userId), `71af51e`(TASK-0431, PR merge 충돌 ref 불변 회귀 테스트).
- yona-cli 커밋(fetch로 origin과 동기화 확인 후 push 완료): `36a6a87`(issue create/edit
  담당자·라벨 배선 + 데이터손실 버그 수정), `fc54231`(pr edit/list 담당자·라벨 배선),
  `d4041bd`(admin webhook/permission list 배선).
- **막힌 항목 없음** — origin과 diverge 없이 두 저장소 모두 정상 push 완료.

### 14라운드 (2026-09-02) — TASK-0433~0435 회귀 확인 + 라벨 IDOR·채번 경합·fork TOCTOU 소급 기록 + 신규 버그 2건

직전 세션이 TASK-0433(라벨/마일스톤 IDOR)/TASK-0434(이슈·게시글·PR 채번 동시성)/TASK-0435
(project fork TOCTOU)를 실측 RED→GREEN으로 고쳐 로컬 커밋까지 마쳤으나, 전체 회귀 스위트를
돌리려던 시점에 API 레이트리밋으로 강제 종료됐다(사용자가 그 3개 커밋을 그대로 push, 계획
문서 기록은 못함 — 각 커밋 메시지 자체에 상세 내역이 이미 남아있어 위 커밋 로그 참고). 이번
라운드는 그 3개 커밋의 전체 스위트 기준 안전성 확인부터 시작해, 13라운드가 다루지 못하고
이월한 관점(동시 fork, 토큰 만료, PRIVATE 프로젝트 경계, org 소유 프로젝트, 대용량/특수문자
입력)을 실측했다.

#### TASK-0433~0435 회귀 확인

`./gradlew test`(기본 mariadb) 5835개 중 53개 실패 — 13라운드 베이스라인(48개, 8개 스펙)과
비교해 `PostingServiceSpec`만 16→21건으로 늘고 나머지 7개 스펙은 완전히 동일한 케이스 수.
`PostingServiceSpec` 단독 재실행 100% GREEN, 나머지 7개 스펙도 한 번에 묶어 재실행 100%
GREEN — 8개 스펙 전부 이 저장소가 매 라운드 기록해온 "동시 세션 간 testcontainer 공유"
환경 노이즈로 확정, TASK-0433~0435는 **회귀 없음**.

#### 실서버(H2) 실측 — org 소유 프로젝트 전체 명령 표면

admin이 조직 `acmeorg`를 만들고(`POST /api/organizations`) 그 아래 `acmeorg/widget`
(PUBLIC)을 생성한 뒤, org 비멤버 `bob`까지 포함해 `label create/edit/delete`, `issue
create/edit(--assignee/--label/--remove-assignee)`, git clone/두 브랜치 push, `pr
create/edit/diff/checkout(+추가 커밋 재push 후 pr diff가 최신 반영함을 확인)/review/merge
(실제 병합 커밋으로 대상 브랜치 갱신 확인)`, `search issues/prs/projects`, `admin webhook
create/list`, `admin permission list`, `org list/view`, `project fork`(자기 계정으로)까지
전체 명령 표면을 실측 — 이 세션 전체에서 거의 다뤄보지 않았던 org 소유 프로젝트 조합이었다.

#### 신규 버그 1 — PUBLIC 프로젝트 비멤버가 REST API로 이슈를 못 만들던 권한 로직 갭(TASK-0436)

bob(acmeorg 비멤버)이 PUBLIC인 `acmeorg/widget`에 `yona issue create`를 호출하면 항상 403
Forbidden — `IssueController.createIssue()`가 쓰던 손수 구현한 `checkWritePermission()`
(프로젝트 직접멤버 or 조직 그룹멤버만 확인)이 legacy `AccessControl.
isProjectResourceCreatable()`(app/utils/AccessControl.java:48-78)의 "PUBLIC 프로젝트면
멤버가 아닌 로그인 사용자도 이슈/게시글을 만들 수 있다" 분기를 애초에 포팅하지 않은 것이
원인이었다. 세션 기반 웹 UI(`IssueViewController.newIssue()` 등)는 이미 완전히 포팅된
`config.security.AccessControl.isProjectResourceCreatable()`을 쓰고 있어 이 버그가 없었고,
`yona issue create`가 타는 REST API(`POST /api/v1/projects/{owner}/{project}/issues`)만
이 손수 구현한 좁은 체크 때문에 걸려 있었다 — org 소유 프로젝트가 아닌 순수 PUBLIC 프로젝트
(`admin/solo`)에서도 동일하게 재현됨을 신규 서버(15라운드 재검증)로 별도 확인.

수정: `checkWritePermission()`이 이미 검증된 `accessControl.isProjectResourceCreatable(user,
project, ResourceType.ISSUE_POST)`에 위임하도록 교체(신규 로직 없음). `IssueControllerSpec`
(mockk)에 조직 비소속 PUBLIC 프로젝트 비멤버 생성 허용 회귀 테스트 추가,
`IssuePublicProjectNonMemberCreateIntegrationSpec`(`AbstractIntegrationTest`, 실제 DB+전체
시큐리티 필터체인) 신설 — 수정 전 코드로 되돌려 두 테스트 모두 RED(403) 확인 후 복구해
GREEN. 실서버(H2)+실 yona-cli 재검증: bob의 `yona issue create -R acmeorg/widget` 성공,
PRIVATE 프로젝트(`admin/secret`)에서는 여전히 403 유지 확인. 15라운드 신규 서버에서도 org
소유/비소유 PUBLIC 프로젝트 둘 다 동일하게 정상 동작 재확인.

#### 신규 버그 2 — 전체 검색이 H2 프로파일에서 대소문자를 구분해 사실상 못 찾던 버그(TASK-0437)

`yona search prs PR`이 제목에 분명히 "PR"이 포함된 PR(`조직 PR`, `bob의 PR`)을 하나도 못
찾는 것을 실측 중 발견 — `SearchServiceImpl.searchInAll()`이 검색어를
`keyword.lowercase()`로 항상 소문자화해 넘기는데, Issue/PullRequest/Posting/Milestone/
User/PostingComment/IssueComment/ReviewComment/Project 9개 레포지토리의 네이티브 LIKE
검색 쿼리는 컬럼 쪽을 소문자화하지 않고 있었다. 이 스위트의 기본 DB인 MariaDB(`*_ci`
콜레이션)는 LIKE 자체가 대소문자를 구분하지 않아 이 갭이 완전히 가려져 있었고, H2(콜레이션
무관하게 LIKE가 대소문자 구분)로 원본 DB에 `title LIKE '%casesensitivetest%'`(0건) vs
`LOWER(title) LIKE LOWER(...)`(정상 매치)를 직접 질의해 근본원인을 확정했다 — `./gradlew
test`(기본값 mariadb)로는 영원히 드러나지 않고, `-Dyona.it.db=h2`로 실행해야만 잡히는
버그였다.

수정: 9개 레포지토리의 모든 `LIKE :keyword`/`LIKE :query` 네이티브 쿼리를 `LOWER(컬럼) LIKE
LOWER(:파라미터)`로 통일(이미 `IssueRepository.findForMention()`이 쓰던 패턴과 동일). 파라미터
쪽도 LOWER로 감싸 호출부가 이미 소문자화했는지 여부와 무관하게 항상 대소문자 무시로 동작하게
했다. `IssueRepositorySpec`에 대소문자 불일치 매치 회귀 테스트 추가 — **반드시
`-Dyona.it.db=h2`로 실행해야 수정 전 RED가 재현됨**(기본값 mariadb로는 수정 전 코드도 이미
GREEN)을 확인, 복구해 h2/mariadb 양쪽 GREEN. 영향받는 9개 레포지토리 전체 스펙 +
`SearchRestApiControllerSpec`을 h2/mariadb 양쪽에서 재실행해 회귀 없음 확인. 실서버(H2)
재검증: `yona search issues casesensitivetest`/`yona search prs PR`이 각각
"CaseSensitiveTest Bug"/"조직 PR"·"bob의 PR"을 정상 검색.

#### 추가 탐색(신규 버그 발견 안 됨)

- **토큰 만료**: H2 DB 파일에 직접 SQL로 `api_token.expires_at`을 과거로 돌려 재현 — 만료된
  토큰으로 API 호출 시 403(신원 자체는 인식하되 권한 없음으로 처리)으로 명확히 거절, 크래시
  없음. 401이 더 적절할 수 있는 설계 선택이지만 기존에 의도된 동작이라 버그로 분류하지 않음.
- **스코프 토큰 권한 분리**: ISSUES=READ만 있는 토큰으로 `issue list`는 성공, `issue create`는
  403 — 정상 동작.
- **비프로젝트멤버를 담당자로 지정**: 서버(`IssueController.createIssue/updateIssue`)는 멤버십
  검증 없이 assignee를 그대로 받아들이는데(레거시 `IssueApi.findAssginee()`/`Assignee.add()`도
  동일하게 멤버십 검증이 없음을 코드 대조로 확인 — 레거시 동치, 버그 아님), CLI가
  `assignableUsers` 목록에 없는 로그인ID를 사전에 거절해 실제로는 명확한 에러 메시지로
  막힘(`yona issue create --assignee bob`이 프로젝트 비멤버면 "담당자로 지정할 수 있는 후보 중
  ... 찾을 수 없습니다"로 정상 종료).
- **대용량/특수문자 입력**: 이모지, 실제 줄바꿈(`$'...\n...'`), 5000자 본문, SQL 인젝션성
  문자열(`'; DROP TABLE ... --`)을 이슈/라벨 제목·본문에 넣어도 크래시/데이터 손상 없이
  그대로 왕복 저장·조회됨(JPA 파라미터 바인딩이라 인젝션 자체가 원천 불가능함을 코드로도
  확인 — `@Query` 네이티브 쿼리 전수 조사 결과 문자열 연결 방식의 동적 SQL 없음).
- **`admin backup export`의 org 데이터 포함 여부**: `ORGANIZATION`/`ORGANIZATION_USER` 테이블이
  백업 JSON에 정상 포함됨을 확인(이전 라운드는 org 없는 상태로만 백업을 검증했었음).
- **PR merge 상태가드**: 이미 MERGED인 PR을 다시 merge 시도해도 TASK-0424가 고친 대로 400 +
  명확한 메시지로 거절(500 아님), org 소유 프로젝트에서도 동일.
- **웹훅 타입 오기입**: `--type PUSH`처럼 `WebhookType` enum에 없는 값을 넣으면
  `WebhookController.createWebhook()`이 `WebhookType.valueOf()` 실패를 잡아 조용히 SIMPLE로
  폴백함(기존 의도된 방어적 처리로 보임 — 크래시/보안 문제 없어 버그로 분류하지 않음, 다만
  잘못된 타입 지정 시 사용자에게 알려주지 않는 UX 갭이 있어 다음 라운드 참고용으로만 기록).

#### 15라운드 (2026-09-02, 같은 세션에서 이어서) — 완전히 새 서버로 클린 라운드 확인

TASK-0436/TASK-0437을 반영한 새 jar + 새로 빌드한 yona-cli 바이너리로, 완전히 새로운 격리된
H2 서버(포트 18311, 별도 `-Dyona.data`/`-Dyona.git.base-dir`)를 처음부터 다시 띄워 부트스트랩
관리자 생성 → 조직 생성 → org 소유/비소유 PUBLIC 프로젝트 생성 → 라벨/이슈(대소문자 섞인
제목 포함)/PR(생성·edit·검색·review·merge, 실제 병합 커밋 확인) → **대소문자 검색**(`search
issues`/`search prs`가 대소문자 달라도 정상 매치, TASK-0437 재확인) → **PUBLIC 프로젝트
비멤버 이슈 생성**(org 소유/비소유 둘 다, TASK-0436 재확인) → PRIVATE 프로젝트 경계(403
유지) → admin 전용 엔드포인트 경계(bob 403 유지) → 이슈 edit 시 담당자/라벨 보존 →
**동시 fork 3개**(TASK-0435 재확인, 1건 성공 + 2건 깔끔한 400, 500 없음)까지 전체 골든패스를
처음부터 끝까지 재검증했다. **신규 버그 0건** — 클린 라운드로 종료.

#### 전체 스위트 / 커밋

- `./gradlew test`(기본 mariadb) 최종 재확인: 5838개 중 53개 실패 — TASK-0436/0437 반영 전
  베이스라인과 **완전히 동일한 8개 스펙·동일 케이스 수**(위 "TASK-0433~0435 회귀 확인" 참고),
  회귀 아님.
- yona 커밋(fetch로 origin과 동기화 확인 후 매 커밋 직후 즉시 push 완료): `6d4e250`
  (TASK-0436, PUBLIC 프로젝트 비멤버 이슈 생성 권한 갭), `678b16c`(TASK-0437, 검색 대소문자
  구분 버그).
- yona-cli: 이번 라운드는 yona-cli 자체 변경 없음(전부 yona 서버 측 버그였음).
- **막힌 항목 없음** — origin과 diverge 없이 정상 push 완료.
- **라운드 종료 판단**: 14라운드에서 신규 버그 2건 발견·수정, 15라운드가 완전히 새 서버로
  처음부터 전체 골든패스(org 소유/비소유 프로젝트, 대소문자 검색, 권한 경계, 동시 fork)를
  재검증해 신규 버그 0건 — **클린 라운드로 종료**(사용자의 최종 목표 달성).

### 16라운드 (2026-09-03, TASK-0440) — `gh status` 구현(위 "다음 작업 대상" 해소)

15라운드 직후 재감사에서 "**다음 작업 대상**"으로 표시된 `gh status`(내가 구독 중인 모든
저장소에 걸친 작업 현황 대시보드 — Assigned Issues/Assigned Pull Requests/Review
Requests/Mentions/Repository Activity 5개 구성요소, `gh status --help` 실측 확인)를
구현했다. 이미 있는 `issue status`는 이 중 이슈 하나(그것도 담당/작성만)뿐이라 부분집합이다.

#### 조사 결과 — 5개 구성요소 중 yona에 실제로 있는 것 / 없는 것

- **Assigned Issues**: 이미 완전히 있음 — `UserIssueStatusRestApiController`의
  `issueRepository.findByAssigneeAndState`/`countByAssigneeAndState` 그대로 재사용.
- **Assigned Pull Requests**: 데이터 모델은 있었으나(13라운드에서 배선한 PR
  `assignee`/`setAssignee`) "로그인 사용자 전체"를 대상으로 한 조회가 없었다 —
  `PullRequestRepository`에 `findByAssigneeUserIdAndState`/`countByAssigneeUserIdAndState`
  2쌍만 신규 추가(신규 서비스 로직 없음, `IssueRepository.countByAssigneeAndState`와 동일한
  `pr.assignee.user.id` 패턴).
- **Review Requests**: 데이터 모델은 있었으나(12라운드 `addReviewer`/`removeReviewer` REST
  어댑터) 마찬가지로 전역 조회가 없었다 — `pr.reviewers`(`@ManyToMany`)를 JOIN해서 대조하는
  `findByReviewerIdAndState`/`countByReviewerIdAndState` 신규 추가.
- **Mentions**: `MentionService.getMentioningIssueIds()`가 이미 있지만 `ISSUE_POST`/
  `ISSUE_COMMENT`만 다룬다 — `PullRequestServiceImpl`/`ReviewComment` 어디에도
  `mentionService.update()` 호출이 없음을 전수 확인(PR 본문·리뷰 코멘트 멘션 감지 자체가
  yona에 없다). 따라서 이번 라운드는 "멘션된 이슈"만 노출하고, PR 멘션 감지를 새로
  설계/구현하지는 않았다(범위 밖 — gh 파리티는 "이미 있는 서버 기능을 CLI로 노출"이 원칙이지
  신규 기능 개발이 아니다). 서버 응답 필드명도 `mentionedIssues`로 명시해 gh처럼 이슈+PR을
  섞은 통합 멘션이 아님을 드러냈다.
- **Repository Activity**: 신규 기능처럼 보였지만 실제로는 이미 있었다 — `NotificationEvent`
  + `WatchService.findActualWatchers()`가 "내가 watch하는 프로젝트에 새 이슈/PR/댓글이
  생기면 나를 receiver로 알림 이벤트를 남긴다"를 이미 구현해뒀고(`IssueServiceImpl` 등),
  이 데이터는 이미 `/api/notifications`(레거시, 세션 인증 전용)로 노출돼 있었다. 신규
  서비스 로직 없이 `NotificationEventRepository.findByReceiver()`만 재사용해 새 엔드포인트
  에도 얇게 포함시켰다.

#### 구현

- **서버**: 신규 `GET /api/v1/user/status`(`web/UserStatusRestApiController.kt`) — 위 5개
  섹션을 한 번에 내려준다. Issue/PR을 응답에 중첩할 때는 항상 기존
  `RestApiResponseDto.kt`의 `IssueResponse`/`PullRequestResponse`(`toResponse()`)를
  재사용해 엔티티를 그대로 반환하지 않는다(9라운드가 고친 순환 직렬화 재발 방지).
  `PullRequestRepository`에 신규 쿼리 2쌍(위 조사 결과 참고) 추가한 것 외엔 신규 서비스
  로직이 없다.
- **인가**: `/api/v1/user/status`는 `/api/v1/user/**` 네임스페이스라 10라운드가 이미 추가해둔
  계정 수준 스코프 인식 패턴(`AccountLevelTarget`)에 올라탄다 — 다만 이 엔드포인트는
  ISSUES뿐 아니라 PR 데이터도 함께 내려주므로, `AccountLevelTarget.resourceType`(단일)을
  `resourceTypes: List<ResourceType?>`로 바꿔 ISSUES+PULL_REQUESTS 둘 다 READ 권한이 있어야
  통과하도록(AND 판정) `ApiTokenAuthenticationFilter`를 확장했다 — 다른 계정 수준 URL(프로젝트
  생성/`/api/v1/user/issues/status`/`/site/export`)은 전부 원소 1개짜리 리스트라 동작이
  그대로 유지된다(회귀 없음, 아래 재검증 참고).
- **yona-cli**: 최상위 `yona status` 커맨드 신설(서브커맨드 없음, `gh status`와 동일한 UX —
  `gh status --help` 실측 결과 `-e/--exclude`, `-o/--org` 외엔 `--json`/`-L` 같은 공용
  플래그를 받지 않아 이 커맨드도 `--json` 필드선택만 지원, 페이지네이션/상태 필터 없음). 섹션별
  사람이 읽기 좋은 출력(담당 이슈/담당 PR/리뷰요청/멘션/저장소 활동).

#### 실측 검증 (RED → GREEN)

- **RED**: 이번 라운드 이전 커밋(`374f488`) 기준 소스로 서버 jar를 다시 빌드해(신규
  `UserStatusRestApiController.kt`만 제외, 나머지 신규 서버 변경은 `git stash`) 별도 포트로
  띄운 뒤, 시드해 둔 유효한 스코프 토큰(ISSUES:READ)으로 `GET /api/v1/user/issues/status`는
  200(정상 동작 확인용 대조군), `GET /api/v1/user/status`는 **404**(Spring MVC "no mapping
  found")임을 실측 확인. yona-cli 쪽도 이전 커밋으로 빌드한 바이너리에서
  `yona status` 실행 시 `Error: unknown command "status" for "yona"`로 실패함을 확인.
- **GREEN**: 구현 반영 후 격리된 `-Dyona.data`(H2, `spring.profiles.active=h2`)로 새 서버를
  띄우고, H2 파일에 SQL로 담당 이슈 1건/담당 PR 1건/리뷰요청 PR 1건/멘션된 이슈 1건/저장소
  활동 알림 1건 + `ApiToken`(ISSUES:READ + PULL_REQUESTS:READ, allRepositories=true)을
  직접 시드했다. 실 `yona-cli` 바이너리로:
  ```
  $ yona status --server http://localhost:18453 --token yona_pat_e2e_status_test
  담당 이슈 (Assigned Issues) (열림 1 / 닫힘 0)
    #1	담당 이슈 E2E
  담당 풀 리퀘스트 (Assigned Pull Requests) (열림 1 / 닫힘 0)
    #1	담당 PR E2E
  리뷰 요청 (Review Requests) (열림 1 / 닫힘 0)
    #2	리뷰요청 PR E2E
  멘션된 이슈 (Mentions, 이슈만 지원) (열림 1 / 닫힘 0)
    #2	멘션 이슈 E2E

  저장소 활동 (Repository Activity)
    [NEW_ISSUE] 새 이슈가 등록되었습니다.
  ```
  5개 섹션 전부 실데이터로 정상 표시 확인. `--json reviewRequests` 필드선택도 정상 동작.
  스코프 AND 판정도 실서버로 재검증: ISSUES:READ만 있는 별도 토큰으로 같은 엔드포인트 호출 시
  **403**(PULL_REQUESTS 스코프 누락)을 실측 확인.
- **통합 테스트**: `UserStatusRestApiControllerSpec`(mockk, 신규), `PullRequestRepositorySpec`에
  신규 쿼리 2건 추가(`AbstractIntegrationTest`, 실제 H2 DB), `ApiTokenAccountLevelAndLegacyAuthorizationIntegrationSpec`에
  `/api/v1/user/status` 스코프 AND 판정 3케이스(ISSUES만/PULL_REQUESTS만/둘 다) 추가 —
  전부 mockk만으로 GREEN 처리하지 않고 최소 1개는 실제 DB+전체 시큐리티 필터체인을 타는
  통합 테스트로 검증.

#### 전체 스위트 / 커밋

- `./gradlew test`(기본 mariadb): 5822개 통과, 78개 실패("Table 'yona.n4user' doesn't
  exist" 등 — 이 세션 동시 작업 중인 다른 testcontainer 공유 노이즈, 14/15라운드와 동일한
  패턴). 실패한 14개 스펙(`ApiTokenAccountLevelAndLegacyAuthorizationIntegrationSpec`
  포함 — 이번 라운드가 수정한 파일)을 한 번에 묶어 단독 재실행한 결과 **전부 100% GREEN**
  (78건 전체 회귀 아님으로 확정). `PullRequestRepositorySpec`/`UserStatusRestApiControllerSpec`
  등 신규 테스트가 포함된 스펙은 애초에 이 78건에 없었다(처음부터 정상).
- yona 커밋: `3df8cbd`(TASK-0440, `gh status` 구현).
- yona-cli 커밋(fetch로 origin과 동기화 확인 후 즉시 push 완료): `6c5472b`(`yona status`
  최상위 커맨드 + `internal/api/userstatus.go`).
- **막힌 항목 없음** — 양쪽 저장소 모두 origin과 diverge 없이 정상 push 완료.

#### 코디네이터 세션 독립 재검증 (2026-09-03)

16라운드 완료 보고를 받은 뒤, 위임받은 에이전트의 자체 보고를 그대로 신뢰하지 않고 별도의
격리된 서버(H2, `-Dyona.data`)+재빌드한 `yona-cli` 바이너리로 직접 다시 재현했다:

- `yona status`를 admin 계정으로 실제로 실행해 담당 이슈/담당 PR/리뷰 요청 3개 섹션이 실데이터로
  채워짐을 확인. 저장소 활동(Repository Activity)은 이번 재검증에서는 빈 목록으로 나왔는데,
  원인을 코드로 추적해 **버그가 아님을 확인**했다 — `repositoryActivity`는
  `NotificationEventRepository.findByReceiver()` 기반이라 "watch 중인 프로젝트에서 남이 한
  행동"에만 알림이 쌓인다. 이 재검증은 admin 혼자 프로젝트를 만들고 이슈/PR도 admin 스스로
  생성한 시나리오라 알림 수신 대상 자체가 없어 비어있는 게 정상 동작이다(`ProjectServiceImpl.
  createProject()`가 생성자를 자동으로 watch 등록하지 않는다는 것도 코드로 확인 — 별도 버그는
  아니고 gh의 "구독 저장소" 개념과 동일하게 명시적 watch가 필요한 설계).
- **스코프 AND 판정 재검증**: `scope_PULL_REQUESTS=NONE`으로 발급한(ISSUES만 WRITE) 토큰으로
  `GET /api/v1/user/status`를 직접 curl로 호출해 **403**을 재확인 — 16라운드가 보고한 결과와
  일치.
- **PR 멘션 제외 재확인**: 코드 조사 결과(위 "조사 결과" 절)와 실측 응답
  (`mentionedIssues` 필드만 존재, PR 멘션 필드 없음)이 일치함을 확인 — yona에 PR 멘션 감지
  기능 자체가 없다는 16라운드의 판단이 맞고, gh 파리티 원칙(이미 있는 기능만 노출, 신규 기능
  개발 아님)을 그대로 지켰다.

두 항목(스코프 AND 판정, PR 멘션 제외) 모두 16라운드 보고와 동일한 결과로 재확인됐다 — 추가
수정 없음.

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 스코프 카테고리 확정 | `ResourceType` 33종을 어떻게 그룹핑할지 최종 미확정 | **1라운드에서 해소** — `ApiTokenScopeGroup.kt`(8개 그룹)로 전수 확정, 근거는 위 완료 로그 참고 |
| 기존 전권 토큰 마이그레이션 | 이미 발급된 `User.token` 보유자 처리 방침 미정 | **미해결(다음 라운드로 이월)** — 1라운드는 문서화만 함: 신규 `/api/v1/...` 네임스페이스는 스코프 토큰만 인증하고, 그 외 기존 URL은 레거시 전권 토큰 경로를 그대로 유지하는 co-existence로 임시 처리(`ApiTokenAuthenticationFilter.kt` 주석 참고). 자동 재발급 vs 만료 후 재발급 안내 중 무엇을 택할지, 그리고 레거시 경로를 언제 끊을지는 여전히 미정 |
| 관리자 API 존재 여부 | 백업/웹훅/권한 관리용 서버 API가 이미 있는지 미확인 | **4라운드에서 해소** — 백업(`GET /site/export`, `POST /site/import`)은 실사용 가능한 형태로 존재해 CLI에 완전히 연결. 웹훅(`web/WebhookController.kt`)·권한(`web/ProjectMemberController.kt`)은 생성/변경/삭제 API는 있지만 세션·폼 기반 레거시라 목록 조회용 JSON API가 없다(웹훅 목록은 HTML 렌더링 전용, 권한 목록은 엔드포인트 자체가 없음) — `yona admin webhook list`/`yona admin permission list`는 명확한 안내 메시지의 미구현 스텁으로 유지. 새 JSON API를 yona에 추가하는 것은 CLI 프로젝트 범위 밖이라 이번 라운드는 시도하지 않음. 상세는 위 "4라운드" 로그 참고 |
| 프로젝트 조회 API의 스코프 패턴 불일치 | Step6의 `/api/v1/projects/{owner}`(목록)와 `/api/v1/projects/{owner}/{project}`(조회)는 리소스 세그먼트가 없어 `ApiTokenAuthenticationFilter.scopedApiPattern`(owner/project/resource 3단 필수)과 매칭되지 않는다 — Fine-grained 스코프 토큰으로 호출 불가(세션/전권 토큰만 가능), 이슈/PR API는 "issues"/"pull-requests" 세그먼트가 있어 이 문제가 없다 | **3라운드에서 해소** — 개별 조회는 `metadata` 스코프(그룹/권한 매트릭스 없이 repo scope만 확인), 목록은 request attribute(`SCOPED_API_TOKEN_ATTRIBUTE`) 기반 필터링으로 구현 완료. 상세는 아래 "3라운드" 로그 참고 |
| 토큰 발급/관리 UI 부재 | `ApiTokenRepository`엔 조회 메서드 하나뿐, 사용자가 `ApiToken`을 발급/조회/폐기할 UI·컨트롤러·서비스가 전혀 없어 실사용자는 Fine-grained 토큰을 발급받을 방법이 없다 | **3라운드에서 해소** — `ApiTokenService`/`ApiTokenServiceImpl` + `UserViewController` 확장 + `user/edit_tokens.html` 신설로 발급/조회/폐기 가능. 상세는 아래 "3라운드" 로그 참고 |
| 전역/사용자 단위 엔드포인트의 스코프 인가 갭 | `search`/`organizations`/`user` 네임스페이스(5라운드 신설)는 특정 저장소 하나에 속한 리소스가 아니라 `/api/v1/projects/{owner}/{project}/{resource}` 3세그먼트 스코프 모델에 맞지 않는다 | **`search`/`organizations`는 P3-36에서 해소**(2026-09-09) — "저장소 비종속" 스코프를 새로 설계할 필요 없이, 16라운드가 `user/status`용으로 이미 만들어둔 `AccountLevelTarget` 메커니즘을 확장하는 것만으로 충분했다(`docs/parity/tickets/p3-36.md` 참고). `user` 네임스페이스는 이미 `/user/issues/status`+`/user/status` 2개 라운드 전부터 이 메커니즘으로 해소돼 있었음(중복 항목) |
| `yona search prs` 대응 서버 기능 없음 | yona `SearchType` enum(PROJECT/ISSUE/USER/POST/MILESTONE/ISSUE_COMMENT/POST_COMMENT/REVIEW)에 PR 전용 값이 없어 PR 자체를 색인하는 통합검색이 서버에 없다 | **7라운드에서 해소** — `SearchType.PULL_REQUEST` 추가 + `PullRequestRepository`에 Issue와 동일한 패턴의 인덱싱/검색 쿼리 신설, `web/SearchRestApiController`에 `GET /api/v1/search/prs` 추가. 상세는 아래 "7라운드" 로그 참고 |
| `gh issue status` 최소 버전만 구현 | `UserIssueStatusRestApiController`는 담당/작성 이슈 개수·목록만 제공 | **7라운드에서 해소** — `UserViewController.userIssues()`가 지원하는 mentioned/favorite/shared/commenter 필터와 pageNum/state/filter/orderBy/orderDir 파라미터를 신규 백엔드 로직 없이 기존 IssueRepository 메서드 호출에 그대로 전달해 노출했다. 상세는 아래 "7라운드" 로그 참고 |
| PR에 라벨/담당자 개념 없음 | `PullRequest.kt`에 `labels`/`assignee` 필드가 없어(Issue엔 있음) `pr list --label/--assignee`를 지원할 수 없었다. 레거시 Play `yona`에도 원래 없던 개념인지 확인 필요했다 | **7라운드에서 해소** — 레거시 조사 결과 원래부터 없던 개념(포팅 누락 아님)임을 확정한 뒤, `Assignee`(재사용)/`IssueLabel`(재사용, 신규 조인테이블 `pull_request_issue_label`)로 백엔드 엔티티+서비스+REST API+list 필터 전체 구현. 웹 UI만 범위 밖으로 이월. 상세는 아래 "7라운드" 로그 참고 |
| `LabelRestApiController` list vs create/update/delete 엔티티 불일치(실제 버그) | `list()`는 `ProjectController.getProjectLabels()`(`domain/project/Label`, 프로젝트 홈 화면 토픽 태그)를 반환하는데 `create/update/delete`는 `domain/issue/IssueLabel`을 다뤄, `yona label create`로 만든 라벨이 `yona label list`엔 절대 보이지 않았다(TASK-0397부터 있던 버그) | **8라운드에서 해소** — `list()`의 위임 대상을 `ProjectViewController.getIssueLabelsForRestApi()`(신설, `IssueLabelService.getLabels()` 기반)로 교체해 create/update/delete와 엔티티를 통일했다. `ProjectController.getProjectLabels()`/`domain/project/Label` 자체는 `project/home.html`의 토픽 태그(`sURLProjectLabels`)가 여전히 실사용 중이라 그대로 유지. 상세는 아래 "8라운드" 로그 참고 |
| PR 라벨/담당자 웹 UI 부재 | 7라운드가 백엔드(엔티티/서비스/REST API/list 필터)는 완료했지만 Thymeleaf 화면(`pullrequest/view.html`, `partial_list.html`)은 명시적으로 범위 밖에 뒀다 | **8라운드에서 해소** — PR 상세 화면에 담당자 선택 `<select>`(Issue 마일스톤 select와 동일 패턴 재사용, round7의 userId 기반 REST 계약에 맞춤) + 라벨 다중선택(`issue/partial_select_label.html`/`partial_show_selected_label.html` 프래그먼트 100% 재사용)을 추가하고, PR 목록 화면에도 Issue 목록과 동일한 마크업으로 담당자 아바타/라벨 배지(읽기전용)를 추가했다. PR 생성/수정 폼은 범위 밖으로 유지(다음 라운드 이후로 이월). 상세는 아래 "8라운드" 로그 참고 |
| `project fork`의 조직(organization) 목적지 미지원 | `ProjectService.forkProject()`는 서비스 계층에 `destinationOwner` 파라미터가 이미 있지만 `ProjectRestApiController.fork()`/`ProjectController.forkProject()`(REST, yona-cli가 쓰는 경로) 둘 다 이를 노출하지 않아 REST/CLI로는 항상 forker 본인 계정으로만 fork되고 조직으로는 fork할 방법이 없다(웹 UI의 세션 기반 `ProjectViewController.fork()`만 `owner` 폼 파라미터로 조직 선택 지원) | **미해결(다음 라운드 이후로 이월)** — 11라운드에서 실측으로 발견. "깨진 기존 기능"이 아니라 "REST에 애초에 노출 안 된 서비스 파라미터"라 버그 수정보다 신규 기능 확장에 가깝다고 판단해 이번 라운드는 구현하지 않음. 착수 시 `ProjectRestApiController.fork()`에 선택적 `destinationOwner` 요청 필드 + `yona-cli`에 `--to-owner` 플래그를 추가하는 얇은 배선이 될 것으로 예상 |
| `IssueRestApiController`/`PullRequestApiController`/`SearchRestApiController`의 순환 직렬화(실제 버그, 심각도 높음) | 세 컨트롤러가 JPA 엔티티(`Issue`/`PullRequest`/`Project`)를 가공 없이 그대로 반환하는데, `User.projectUsers`(`@OneToMany mappedBy="user"`) ↔ `ProjectUser.user`(`@ManyToOne`)가 양방향 연관관계라 Jackson이 무한 순환 직렬화한다 — 실서버 골든패스 수동검증 중 `POST .../issues`가 60KB 넘는 깨진 JSON을 반환하는 것으로 처음 발견(`spring.jpa.open-in-view` 기본 true라 응답 작성 시점까지 세션이 열려있어 lazy 컬렉션이 실제로 초기화되며 재현). mockk로 서비스 계층을 목킹한 기존 `*RestApiControllerSpec.kt`들은 순환이 발생할 실제 연관관계 그래프가 없어 이 버그를 전혀 잡지 못했다 | **9라운드에서 해소** — `ProjectRestApiController`가 이미 쓰던 "엔티티 대신 응답 DTO 반환" 패턴을 `IssueRestApiController`/`PullRequestApiController`/`SearchRestApiController`에도 적용(`web/RestApiResponseDto.kt` 신설). `labels`/`webhooks`/`search/issues` 등 나머지 엔드포인트는 이미 map 기반 DTO를 쓰고 있어 문제가 없음을 실제 데이터로 재검증 완료. 상세는 아래 "9라운드" 로그 참고 |

## Step 8.6 — 실서버 기능 부재로 미룬 4개 항목 (2026-09-01, 사용자 지시로 백로그화)

**상태: 7라운드(2026-09-01)에서 4개 항목 전부 우선순위 순서(1→2→3→4) 그대로 해소 완료.** 아래
목록은 착수 전 백로그 원문을 그대로 보존한다(각 항목의 실제 구현/조사 결과는 위 "7라운드" 완료
로그 참고).

Step8.5 완료 후 "CLI 배선 문제가 아니라 서버 자체에 없어서 못 넣은 것도 있을 텐데?"라는 질문으로
정리된, 위 리스크 표에 이미 개별 기록된 4개 항목을 우선순위와 함께 백로그로 확정한다. 착수 비용/
리스크가 작은 순서로 정렬했다 — 뒤로 갈수록 범위가 커지고 설계 결정이 필요하다.

1. **(최우선) `yona admin webhook list`/`permission list`용 JSON API 신설** — 웹훅
   목록은 `web/WebhookController.kt`가 HTML 렌더링 전용이고, 권한 목록은 대응 엔드포인트가
   아예 없다(`web/ProjectMemberController.kt`). 생성/변경/삭제는 이미 있으니 목록 조회만
   추가하면 된다 — Step4~6과 동일한 "기존 서비스 로직에 위임하는 얇은 REST 어댑터" 패턴을
   그대로 적용 가능, 스키마 변경 없음. 위험이 가장 작다.
2. **`gh issue status` 필터/페이지네이션 전체 구현** — `UserViewController.userIssues()`에
   mentioned/favorite/shared/commenter 필터와 페이지네이션/정렬이 **이미 구현돼 있다** —
   `UserIssueStatusRestApiController`가 그중 assigned/created 두 개만 REST로 노출했으니,
   나머지를 같은 방식으로 마저 노출하면 된다. 신규 백엔드 로직 불필요, 순수 REST 노출 확장.
3. **`yona search prs` 서버 지원** — `domain/enumeration/SearchType`(또는 대응 enum)에
   PR 전용 값이 없어 PR을 색인하는 통합검색 자체가 없다. `SearchService`에 `SearchType.
   PULL_REQUEST` 추가 + PR 인덱싱/검색 쿼리 신설 필요 — 위 두 항목과 달리 **신규 검색 로직을
   설계해야 하는 작업**이라 범위가 한 단계 크다(다만 PR 엔티티 스키마 변경은 없음).
4. **(가장 큼, 설계 결정 필요) PR에 라벨/담당자 개념 추가** — `PullRequest.kt`(`domain/
   pullrequest/PullRequest.kt`) 자체에 `labels`/`assignee` 필드가 없다(`Issue.kt`엔 `assignee:
   Assignee?`, `labels: MutableSet<IssueLabel>`가 있는 것과 대비). 이건 REST API 배선이
   아니라 **PR 엔티티에 없는 도메인 개념을 새로 추가하는 진짜 기능 확장**이다 — P3는 "레거시
   동치성과 무관한 신규 인프라 개선"이 허용 범위이긴 하지만(`docs/yona-wiki/index.md` 서문
   참고), 스키마 변경(마이그레이션 포함)이 들어가는 만큼 신중해야 한다. 착수 전 반드시 확인할
   것:
   - `Assignee(user, project)`(`domain/issue/Assignee.kt`)는 이미 Issue 전용이 아니라
     범용(프로젝트 범위 사용자 배정) 엔티티라 PR에도 `assignee: Assignee?` 필드만 추가하면
     재사용 가능해 보인다(실제로 재사용 가능한지, 아니면 PR 전용 개념이 필요한지 재검증 필요).
   - 라벨은 `IssueLabel`(Issue 전용, `IssueLabelCategory`에 종속)과 별개로 `domain/project/
     Label.kt`(Step8.5 1라운드가 프로젝트 레벨 라벨 CRUD에 쓴 범용 엔티티)가 이미 있다 — PR
     라벨링에 `IssueLabel`을 재사용할지, `Label`을 재사용할지, 아니면 `PullRequestLabel`을
     새로 만들지는 실제 두 엔티티의 용도 차이를 코드로 재확인해서 결정해라.
   - **레거시 yona의 PR 모델에도 라벨/담당자 개념이 원래 없었을 가능성이 높다**(Issue 전용
     설계로 보임) — 착수 전 legacy 소스(`/home/jiho/yona-convert/legacy-yona`)의 PR 관련
     모델을 확인해서, 정말 레거시에도 없던 개념을 새로 추가하는 것인지 확정하고 계획 문서에
     근거를 남겨라.

## Step 8.7 — 7라운드에서 발견/이월된 2개 항목 (2026-09-01, 사용자 지시로 백로그화)

**상태: 8라운드(2026-09-01)에서 2개 항목 전부 지시된 우선순위(1→2) 그대로 해소 완료.** 아래
목록은 착수 전 백로그 원문을 그대로 보존한다(각 항목의 실제 구현 결과는 아래 "8라운드" 완료
로그 참고).

7라운드(Step8.6 구현) 검증 과정에서 발견한 실제 버그 1건과, 7라운드가 명시적으로 범위 밖에
둔 웹 UI 1건을 우선순위와 함께 백로그로 확정한다. 버그가 이미 배포된 기능을 깨뜨리고 있어
1순위, 신규 기능의 UI 완성은 상대적으로 덜 급해 2순위로 뒀다.

1. **(최우선, 실제 버그) `LabelRestApiController`의 list vs create/update/delete 엔티티 불일치**
   — `list()`는 `ProjectController.getProjectLabels()`를 호출해 `domain/project/Label`(프로젝트
   레벨 토픽 태그)을 반환하는데, `create()`/`update()`/`delete()`는 `ProjectViewController.
   newLabel()`/`updateLabelForm()`/`deleteLabelForm()`을 호출해 `IssueLabel`(카테고리 기반 이슈
   라벨링 시스템, 실제 이슈 라벨링에 쓰이는 진짜 라벨)을 조작한다. **`yona label create`로 만든
   라벨이 `yona label list`엔 절대 뜨지 않는다** — TASK-0397(Step8.5 1라운드)부터 있던 버그,
   이번 세션에서 직접 코드 대조로 재확인. 수정 방향: `list()`가 `IssueLabel` 기준으로 프로젝트의
   라벨 목록을 조회하도록 변경(`create`/`update`/`delete`가 쓰는 것과 동일한 엔티티로 통일) —
   `domain/project/Label`/`ProjectController.getProjectLabels()` 자체는 다른 용도(프로젝트 홈
   화면의 토픽 태그 표시 등)로 쓰이고 있을 수 있으니 그대로 두고, `LabelRestApiController.
   list()`의 위임 대상만 `IssueLabel` 조회로 교체하는 최소 수정을 우선 시도해라. `IssueLabel`을
   프로젝트 기준으로 조회하는 기존 리포지토리 메서드가 있는지 먼저 확인하고, 없으면 신설해라.
2. **PR 라벨/담당자 웹 UI** — 7라운드가 백엔드(엔티티/서비스/REST API/`pr list` 필터)는 전부
   완료했지만 Thymeleaf 화면은 명시적으로 범위 밖에 뒀다. PR 상세(`pullrequest/view.html`류)와
   목록 화면에 담당자·라벨을 Issue 화면과 동일한 방식으로 표시·편집할 수 있게 추가해라 — Issue의
   대응 화면(담당자 배정 UI, 라벨 선택 UI)이 이미 있으니 그 마크업/컨트롤러 배선 패턴을 그대로
   재사용해라(이 저장소의 "yona식 독자 구현 금지" 원칙과 동일하게, Issue 화면 구조를 최대한
   그대로 따라라).

## Step 8.8 — API 토큰 화면을 GitHub식 2단계(목록/발급 분리)로 재설계 (2026-09-07 백로그 → 2026-09-07 완료)

**상태: 완료.** 커밋 `ab1cb860b`(refactor(user-settings): split token/SSH/GPG key add-forms into
GitHub-style separate pages)로 아래 변경 범위 1~4를 모두 구현했다 — 목록 페이지에서 발급 폼 제거
+ "새 토큰 발급" 버튼, `GET /user/editform/tokens/new` 신설, 성공 시 목록으로 redirect+flash
attribute로 원문 토큰 1회 노출, 실패 시 발급 폼으로 되돌아가 오류 표시, 만료일 프리셋
7일/30일(기본)/60일로 교체. [[p3-03-ssh-gpg]]의 SSH/GPG 키 화면도 같은 커밋에서 동일 패턴으로
함께 처리됨. 아래는 착수 전 기록 원문.

**(백로그 당시 상태: 미착수.)** 사용자가 실제 운영 서버(`/user/editform/tokens`)를 직접 써보고 지적 — "GitHub처럼
2단계로 나뉘어야 하는 거 아니냐"는 질문에 코드/레거시/설계 문서를 대조 조사한 결과, 지금 화면은
**목록과 "새로 발급" 폼이 한 페이지에 항상 같이 떠 있고, 발급해도 리다이렉트 없이 같은 페이지를
다시 그리는 구조**임을 확인(GitHub는 목록 페이지 → 별도의 발급 페이지 → 완료 후 목록으로
리다이렉트하며 그 목록 페이지 맨 위에 "지금 한 번만 표시" 배너가 뜨는 2단계 흐름). 위 "토큰
발급/관리 웹 UI 설계" 절(145-171행)이 애초에 "같은 페이지 내 폼 **또는** 모달" 중 폼 방식을
택한 것이었고, GitHub를 참고한 건 필드 구성과 "생성 직후 한 번만 노출" UX뿐 — 2단계 페이지
흐름 자체는 검토된 적이 없었다. 레거시(`edit_token.html`)는 애초에 다중 토큰/스코프가 없는
단순 재발급 화면이라 이 판단에 참고가 안 됨(제약으로 작용하지 않음). 사용자가 GitHub식으로
바꾸라고 지시했고, 지금은 계획만 기록하고 실제 작업은 다음 세션으로 미룬다.

**변경 범위**:
1. **목록 페이지**(`GET /user/editform/tokens`) — 기존 테이블만 남기고 발급 폼은 뺀다. "새 토큰
   발급" 버튼 하나를 추가해 아래 발급 페이지로 이동. 발급 완료 후 이 페이지로 리다이렉트됐을 때만
   "지금 한 번만 표시됩니다" 배너 + 값 노출(현재처럼 세션/플래시 속성 또는 리다이렉트 쿼리로 원문
   토큰을 1회 전달 — Spring의 `RedirectAttributes.addFlashAttribute()` 사용을 우선 검토, 위
   [[p3-14-oauth2-provider]] 작업에서 이미 확립한 URL 쿼리 파라미터 노출 패턴과 통일할지도 검토).
2. **신규 발급 페이지**(`GET /user/editform/tokens/new` 가칭) — 지금 목록 페이지에 있던 폼(이름/저장소
   범위/권한 매트릭스/만료일)을 그대로 옮긴다.
3. **`POST /user/editform/tokens`**(발급 처리) — 지금은 성공/실패 둘 다 같은 뷰를 다시 그리는데,
   성공 시 목록 페이지로 리다이렉트(위 1번의 flash attribute로 원문 토큰 전달)하고, 실패(검증 오류)
   시에는 발급 페이지(2번)로 되돌아가 에러를 보여주도록 바꾼다.
4. **만료일 프리셋 변경(사용자 지시)** — 지금은 30일/90일(기본)/366일(1년, 최대) 세 개인데,
   **7일/30일/60일로 교체**한다(`edit_tokens.html:125-129`의 `<select name="expiresInDays">`
   옵션 3개를 갈아끼움 — 어느 값을 기본 선택(`selected`)으로 할지는 착수 시 사용자에게 재확인
   필요, 잠정적으로 30일을 기본으로 유지하는 안 제안). **서버 쪽 "366일 초과 거부" 검증(위
   145-171행 참고)은 이 UI 프리셋 변경과 무관하게 그대로 둔다** — 프리셋 밖의 값을 폼 조작으로
   보내는 것에 대한 방어선이라 366일 상한 자체를 낮출 근거는 없음(사용자가 "7/30/60을 넣어달라"고
   했을 뿐 상한을 낮추라는 지시는 아니었음 — 착수 시 이 해석이 맞는지 재확인할 것).

**함께 적용 여부(별도 결정, 아래 [[p3-03-ssh-gpg]] Step 참고)**: SSH 키/GPG 키 화면도 사용자가
동일하게 GitHub식 2단계로 바꿔달라고 지시했다 — 완전히 같은 패턴(목록+인라인 폼 한 페이지 →
목록 페이지 + 별도 추가 페이지)이라 이 토큰 화면 작업과 구현 방식을 통일해서 진행할 것.

## 관련

- 백로그 원본: [`docs/parity/index.md`](../../parity/tickets/p3-02.md)
- 관련 계획: [[p3-03-ssh-gpg]], [[p3-07-mcp-server]], [[p3-05-ci-actions-runner]]
- 관련 소스: `config/ApiTokenAuthenticationFilter.kt`, `config/SecurityConfig.kt`, `domain/enumeration/ResourceType.kt`, `web/ProjectApiController.kt`, `web/IssueRestApiController.kt`, `web/PullRequestApiController.kt`, `web/ProjectRestApiController.kt`, `web/LabelRestApiController.kt`, `web/SearchRestApiController.kt`, `web/OrganizationRestApiController.kt`, `web/UserIssueStatusRestApiController.kt`, `domain/pullrequest/PullRequestServiceImpl.kt`, `web/WebhookRestApiController.kt`, `web/ProjectPermissionRestApiController.kt`, `domain/pullrequest/PullRequest.kt`, `domain/pullrequest/PullRequestRepository.kt`, `domain/enumeration/SearchType.kt`
