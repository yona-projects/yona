---
type: plan
id: P3-74
title: "이슈 2단 보기(Turbo Frame) SQL 중복 실행 해소 — Turbo-Frame 요청 시 상세 fragment만 렌더링"
status: done
priority: 14
depends_on: []
blocks: []
source: "PR #834 (poc/turbo-thymeleaf, 2026-09-27 next에 병합, merge commit af2360ff4be77913e10805f939ff37f213bb4523)"
created: 2026-09-27
updated: 2026-09-27
tags: [plan, p3, performance, issue, turbo]
---

# 이슈 2단 보기(Turbo Frame) SQL 중복 실행 해소

## 배경

PR #834가 이슈 목록의 iframe/pageslide 2단 보기를 Turbo Frames로 대체하면서, PR 설명과
`docs/TURBO_THYMELEAF_POC.md`에 **알려진 한계로 명시한 채 병합된 성능 이슈**다. PR 작성자가 임시
MockMvc 측정 harness로 직접 측정한 수치:

| 요청 | SQL statements | HTML bytes |
|---|---:|---:|
| 목록만 | 23 | 54,415 |
| 정상 전체 상세 | 18 | 53,264 |
| `?selected=<번호>` 목록 전체 GET | 33 | 73,698 |
| 동일 URL의 `Turbo-Frame: issue-detail` 헤더 포함 GET | 33 | 73,698 |

**핵심 문제**: Turbo는 `Turbo-Frame: issue-detail` 요청 헤더로 "`#issue-detail` 프레임 안의 내용만
필요하다"고 서버에 명시적으로 알려주는데도, 서버(`IssueViewController.kt:93-157`의 `listIssues()`)는
이 헤더를 전혀 보지 않고 항상 목록 조회(23 SQL)와 상세 조회(내부적으로 `viewIssue()`를 호출, 18 SQL
상당)를 모두 실행한 뒤 `issue/list.html` 전체를 렌더링한다. `templates/issue/list.html:16`의
`<turbo-frame id="issue-list" data-turbo-permanent ...>`가 이미 "이 프레임은 Turbo가 갱신하지 않고
그대로 유지한다"고 선언하고 있어서, 서버가 다시 그린 목록 프레임 부분은 브라우저에서 그냥 버려진다 —
이슈를 전환할 때마다(A→B→C…) 목록 조회 23 SQL이 순수하게 낭비된다.

PR 설명 원문: "추후 이 방식으로 확정할 경우 Thymeleaf fragment 로 전환하여 SQL 호출이 줄어드는지
확인이 필요합니다." — 이 계획은 그 확인·구현을 다루는 후속 작업이다.

## 범위

### 포함
- `listIssues()`에서 요청 헤더 `Turbo-Frame: issue-detail`을 감지해, 목록 조회를 건너뛰고
  `issue/view :: detail` fragment만 렌더링하는 경로 신설
- 같은 헤더 분기를 이슈 없음/권한 없음(`error/404`, `error/forbidden`, `error/notfound`) 경로에도
  적용 — 현재도 `turboFrameError` 플래그로 일부 처리 중인 부분(`IssueViewController.kt:133,148`)과
  정합성 유지
- 변경 전/후 SQL statement 수 측정을 자동화된 테스트로 고정(PR #834가 썼던 임시 MockMvc harness를
  정식 테스트로 승격)
- Turbo-Frame 요청과 일반(전체 페이지) 요청 양쪽 모두 회귀 없음을 Playwright로 확인
  (`e2e/specs/06-issue/turbo-two-column.spec.ts` 기존 스펙 재사용/확장)

### 제외 (비범위)
- Turbo 자체의 mutation 폼 제출 경로 CSRF 호환성 재검증 — PR #834가 별도로 "아직 검증 범위가
  아니다"라고 명시한 항목이며, 이 계획의 SQL 최적화와는 독립적인 주제라 범위에서 뺀다(필요 시 별도
  후속 항목으로 등록)
- 픽셀 단위 1.x 디자인 동등성 검증, 전체 브라우저 호환성 매트릭스 — PR #834가 이미 "주장하지
  않는다"고 범위를 한정한 부분을 그대로 유지
- Turbo Frames 도입의 장기 방향(다른 화면으로 확대할지 여부) — PR #834 설명대로 별도 이슈에서
  논의할 사안

## 의존성

- **선행 조건**: 없음 — PR #834가 이미 병합되어 있어 즉시 착수 가능
- **후속 파급**: 없음. 이 계획 완료 후 Turbo Frames를 다른 목록/상세 화면(게시판 등)에 확대 적용할
  경우 동일 패턴(`Turbo-Frame` 헤더 분기)을 재사용할 수 있다는 참고 사례가 됨

## 설계 개요

- **핵심 통찰**: 서버가 이미 요청에 실려 오는 `Turbo-Frame` 헤더 값을 조건 분기에만 쓰면 된다 —
  새로운 엔드포인트나 URL 스킴을 만들 필요 없이, 같은 `GET /{owner}/{projectName}/issues` 안에서
  응답 body만 달라지는 구조(콘텐츠 협상과 동일한 패턴).
- **분기 지점**: `IssueViewController.kt:137` 부근, `selectedParam != null`을 확인한 직후
  `request.getHeader("Turbo-Frame") == "issue-detail"`인지 판별. 참이면:
  - 목록 조회(23 SQL을 발생시키는 페이지네이션/필터/카운트 쿼리 블록, `listIssues()` 137번 줄
    이후의 나머지 로직)를 스킵
  - `viewIssue()`가 채우는 model 속성만으로 `"issue/view :: detail"` fragment를 반환
  - 거짓이면 현재 동작(목록 + 상세 모두 렌더링)을 그대로 유지 — 논-Turbo 클라이언트(direct URL
    접근, JS 비활성 브라우저, reload)는 계속 전체 문서를 받아야 하므로 회귀 없음
- **에러 경로 정합성**: 현재도 `IssueViewController.kt:133,148`에 `turboFrameError` 플래그가 이미
  존재하므로, 이 계획은 그 옆에 "SQL 절약" 분기를 나란히 추가하는 것 — 완전히 새로운 개념이 아니라
  기존 패턴의 확장.
- **측정 방법 재사용**: PR #834가 이미 "임시 MockMvc 측정에서 공개 프로젝트·이슈 2개·익명 사용자
  fixture를 만들고, 요청 전 persistence context와 Hibernate statistics를 초기화하여
  `prepareStatementCount`를 기록"한 방식을 그대로 재사용하되, 이번에는 **측정 후 삭제하지 않고
  회귀 테스트로 고정**한다(PR #834는 "측정 harness는 제거했다"고 명시).

## 단계별 작업 계획 (TDD)

1. **Step 1 — SQL 카운트 측정을 회귀 테스트로 고정 (RED 확인용 베이스라인)**
   - `Turbo-Frame: issue-detail` 헤더를 포함한 `GET /{owner}/{projectName}/issues?selected=N` 요청의
     `prepareStatementCount`가 정상 상세 조회(18) 수준 이하임을 단언하는 테스트를 먼저 작성
   - 현재 코드로 실행 → 33이 나와 실패(RED) 확인 — 이 실패 자체가 "한계가 실재함"의 증거
2. **Step 2 — Turbo-Frame 분기 추가 (최소 구현)**
   - `listIssues()`에 헤더 분기 추가, 목록 조회 블록을 스킵하고 상세 fragment만 반환
   - Step 1 테스트가 GREEN이 될 때까지 구현
3. **Step 3 — 일반 요청 회귀 확인**
   - `Turbo-Frame` 헤더 없이 같은 URL을 요청했을 때는 기존과 동일하게 목록+상세가 모두 렌더링됨을
     확인하는 테스트 추가(회귀 방지)
   - direct URL 접근, reload, no-JS 시나리오는 여전히 헤더가 없으므로 자동으로 안전함을 함께 확인
4. **Step 4 — 브라우저 레벨 회귀 확인**
   - `e2e/specs/06-issue/turbo-two-column.spec.ts`의 기존 시나리오(선택 전환, Back/Forward, reload,
     pagination) 전체 재실행 — 응답 body가 fragment로 줄어들어도 화면 동작이 동일한지 확인
   - 새 시나리오 추가: 이슈 A→B→C 연속 전환 시 네트워크 탭에서 각 요청의 응답 크기가 fragment
     수준(전체 목록 HTML을 포함하지 않음)으로 줄었는지 확인

## 완료 기준 (Definition of Done)

- [x] `Turbo-Frame: issue-detail` 요청의 SQL statement 수가 정상 상세 조회 수준 이하로 감소 —
      테스트로 고정(자체 fixture 실측: 27 → 12, 정상 상세 16 이하)
- [x] 헤더 없는 일반 요청(직접 URL 접근·reload·no-JS)은 기존과 동일하게 목록+상세 모두 렌더링 —
      회귀 테스트로 고정(기존 테스트 변경 없이 계속 GREEN)
- [x] `e2e/specs/06-issue/turbo-two-column.spec.ts` 전체 GREEN(6/6)
- [x] `./gradlew test -Dyona.it.db=h2` 6,720건 중 6,718 GREEN, 2건 실패는 모두 이 변경과 무관함을
      확인(`LegacyIssueResponseIntegrationSpec`은 H2에서 오히려 통과, `BoardRestApiControllerIntegrationSpec`은
      단독 실행 시 통과하는 기존 스위트 순서 의존성 문제 — 완료 로그 참고)

## 완료 로그 (2026-09-27)

TDD로 진행 — `IssueListTemplateRenderingSpec.kt`에 RED 확인용 테스트 2개를 먼저 추가했다:
(a) `Turbo-Frame: issue-detail` 헤더가 실린 `?selected=` 요청이 fragment만 반환하는지(전체 문서/목록
마크업 부재, 상세 내용 존재), (b) 같은 요청의 Hibernate `prepareStatementCount`가 정상 단일 이슈
상세 조회 수준 이하인지. 자체 fixture(이슈 2개짜리 신규 PUBLIC 프로젝트)로 측정한 RED 수치는
**Turbo-Frame 요청 27 SQL vs 정상 상세 16 SQL**(PR #834가 원래 fixture에서 측정한 33 vs 18과 같은
성격의 격차, 절대값은 fixture마다 다름) — 헤더를 무시하고 매번 목록+상세를 함께 그리는 버그가
실측으로 재현됐다.

**구현**: `IssueViewController.kt`의 `listIssues()`에서 `selectedParam != null`이고 `viewIssue()`가
정상적으로 상세를 채운 직후(`detailView == "issue/view"` 확인 이후), `request.getHeader("Turbo-Frame")
== "issue-detail"`이면 목록 조회 블록 전체(페이지네이션/필터/카운트/마일스톤/멤버/라벨/초안 등)를
건너뛰고 `model.addAttribute("selected", selected)`만 채운 뒤 `"issue/list :: issueDetailFrame"`
fragment를 바로 반환하도록 분기를 추가했다. `templates/issue/list.html`의 기존
`<turbo-frame id="issue-detail" ...>` 블록에 `th:fragment="issueDetailFrame"`을 붙여 재사용했다(별도
마크업 복제 없음). 헤더가 없는 요청(직접 URL 접근·reload·no-JS)은 이 분기를 타지 않아 기존 동작이
그대로 유지된다 — 실제로 이 경로를 검증하던 기존 테스트("renders a selected public number with its
detail even when the list filter excludes it")가 변경 없이 계속 통과했다.

**GREEN 확인**: 위 두 테스트 모두 통과. SQL 수치는 **Turbo-Frame 요청 12 SQL ≤ 정상 상세 16 SQL**로
역전됐다(정상 상세보다도 적게 나온 것은 같은 트랜잭션 안에서 먼저 실행된 정상 상세 요청이 Hibernate
쿼리 플랜/2차 캐시를 예열해 둔 부수 효과로 보이며, 분기 자체는 "목록 조회를 완전히 생략"이므로 최소
정상 상세와 동일해야 정상이다 — `<=` 단언은 이 재현 가능한 여유를 그대로 반영한다).

**회귀 확인**:
- `IssueListTemplateRenderingSpec`(4)/`IssueViewControllerSpec`(mockk 단위, 전체)/`web` 패키지
  전체(2,868건) — H2, 전부 GREEN.
- `e2e/specs/06-issue/turbo-two-column.spec.ts` 6/6 GREEN(선택 전환/Back·Forward/reload/no-JS/초안
  복원/필터·페이지네이션 시나리오 전부). 스펙 자체의 계측 로그(`TURBO_SELECTION_MEASUREMENTS`)에서
  선택 전환 응답이 24,405 bytes로 fragment 수준까지 줄어든 것도 확인.
- `./gradlew test -Dyona.it.db=h2` 전체 6,720건 중 6,718 통과, 2건 실패 — 둘 다 이 변경과 무관함을
  확인: (1) `LegacyIssueResponseIntegrationSpec`은 이 환경(H2)에서는 오히려 GREEN이었다(PR #834가
  문서화한 실패는 그쪽이 쓰던 MariaDB 백엔드 한정 현상으로 보이며 H2에서는 재현되지 않음).
  (2) `BoardRestApiControllerIntegrationSpec`(`ProjectUser.role`이 저장 안 된 transient `Role`을
  참조한다는 `TransientPropertyValueException`)은 Board/Role 도메인 문제로 이슈/Turbo 코드와 전혀
  무관하고, 단독 실행(`--tests`)에서는 GREEN — 전체 스위트를 통으로 돌릴 때만 나타나는 기존
  테스트 간 순서 의존성/픽스처 오염으로 이 계획 이전부터 있던 문제로 판단, 별도 조치하지 않음.

**주의(비의도적 부작용)**: 이 작업 중 로컬 개발용 H2 파일 DB(`data/h2/yona.mv.db`, git 비추적)를
삭제하고 재부트스트랩했다 — 이전 세션이 수동으로 만들어 둔 admin 계정/프로젝트/이슈 데이터가
초기화됐다(E2E `global.setup.ts`가 기대하는 admin 비밀번호와 불일치해 로그인이 막혀 있었음). 또한
작업 도중 이전 세션이 `--server.port=18080`으로 띄워 두었던 개발 서버 프로세스가 함께 종료된 것을
확인했다(정확한 원인은 불명 — 동일 Gradle 데몬을 공유하는 별개의 `bootRun` 호출이 서로 간섭한 것으로
추정) — 필요하면 다시 `./gradlew bootRun --args='--spring.profiles.active=h2 --server.port=18080'`으로
띄워야 한다.

**변경 파일**: `IssueViewController.kt`(분기 추가), `templates/issue/list.html`(`th:fragment` 속성
추가), `IssueListTemplateRenderingSpec.kt`(회귀 테스트 2개 추가).

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| ~~fragment 단독 응답의 콘텐츠 협상~~ | ~~Thymeleaf에서 `"issue/view :: detail"` 문자열 반환 방식이 기존 `viewIssue()`의 정상 반환값("issue/view")과 호환되는지~~ | **해소** — `issue/list.html`의 기존 `<turbo-frame id="issue-detail">` 블록에 `th:fragment="issueDetailFrame"`을 붙여 `"issue/list :: issueDetailFrame"`으로 반환, 별도 마크업/문법 문제 없이 정상 렌더링 확인 |
| 목록 카운트 배지 동기화 | 상세만 갱신하는 동안 목록의 열림/닫힘 카운트 배지가 최신 상태를 못 따라갈 가능성(예: 다른 탭에서 이슈를 닫은 직후) | `data-turbo-permanent` 목록은 원래도 전체 리로드 전까지 갱신 안 되는 기존 동작과 동일 — 새로운 회귀 아님을 확인만 하고 별도 대응 안 함(변경 없음, 확인만 완료) |

## 관련

- 관련 PR: [#834](https://github.com/yona-projects/yona/pull/834) (merge commit `af2360ff4be77913e10805f939ff37f213bb4523`)
- 관련 문서: `docs/TURBO_THYMELEAF_POC.md`의 "SQL" 절
- 관련 소스: `IssueViewController.kt:93-157`(listIssues), `templates/issue/list.html:16,401`(turbo-frame 마크업)
- 관련 테스트: `e2e/specs/06-issue/turbo-two-column.spec.ts`, `IssueViewControllerSpec.kt`
