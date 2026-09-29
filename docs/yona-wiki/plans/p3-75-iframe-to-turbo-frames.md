---
type: plan
id: P3-75
title: "2단 보기 iframe 잔존 소비자 Turbo Frames 전환 (사이드바 셸은 보류)"
status: planned
priority: 15
depends_on: []
blocks: []
source: "PR #834(이슈 2단 보기 Turbo Frames PoC) 후속 — docs/TURBO_THYMELEAF_POC.md의 '목록 밖의 iframe/sidebar 기능까지 제거하지 않았다' 항목"
created: 2026-09-29
updated: 2026-09-29
tags: [plan, p3, turbo, iframe, frontend]
scope_note: "2026-09-29 사용자 지시 — 2단 보기만 진행, 사이드바 셸(B)은 나중에 결정"
---

# 2단 보기 iframe 잔존 소비자 Turbo Frames 전환

> **범위 결정(2026-09-29, 사용자 지시)**: **2단 보기(A)만 진행**한다. 사이드바 셸 iframe(B)은 나중에 별도 논의하며, 이 계획에서는 조사 결과만 "보류" 절에 보존한다.

- **브랜치**: `feat/p3-75-iframe-to-turbo-frames` (기준: `next`, 관례상 `fix/p3-74-issue-detail-fragment-sql`와 같은 `<type>/p3-<번호>-<slug>` 형식)

## 배경

PR #834가 **이슈 목록**의 iframe/pageslide 2단 보기를 Turbo Frames로 대체했고(`issue/list.html`의
`turbo-frame#issue-list` / `#issue-detail`, `yona.issue.Turbo.js`), P3-74가 그 SQL 중복을 해소했다
([[p3-74-issue-detail-fragment-sql-optimization]]). 그러나 PoC 문서가 명시했듯 **목록 밖의 iframe은 그대로
남았다**. 이 계획은 남은 2단 보기 소비자를 같은 방식으로 정리한다.

### iframe 인벤토리 (2026-09-29 코드 조사 결과)

| # | 위치 | 용도 | 상태 |
|---|---|---|---|
| A | `static/javascripts/service/yona.twoColumnMode.js`(292줄) + Vue 위젯 `static/lib/yona-vue-widgets/yona-page-slide-element.js` | 목록 → 우측 `#pageslide` 패널 안 `<iframe>`으로 상세 표시 | 이슈 목록만 전환 완료. **나머지 소비자 6곳이 아직 사용 중** |
| B (보류) | `templates/site/layout_framed.html:315` `<iframe name="mainFrame" id="mainFrameId">` + `UserViewController.userSidebar()`(`/user/sidebar?path=&hash=`) | 좌측 사이드바 고정 + 본문 iframe 셸 | **이번 범위 아님(보류)**. `layout.html`이 `window.parent`로 부모 창과 결합 |
| C | `MarkdownServiceImpl.kt:63,75`의 sanitizer allowlist `iframe` | 사용자 마크다운 안의 외부 임베드(YouTube 등) | **전환 대상 아님** — 아래 제외 참고 |
| D | `static/javascripts/lib/jquery.pageslide.js`, `lib/vendor.js` 등 벤더 | 벤더 코드 내 문자열 | 이미 미로드/벤더 — 대상 아님 |

**A의 잔존 소비자** (`yona.twoColumnMode.js`를 로드하는 템플릿):
`board/list.html`, `organization/boardList.html`, `organization/issueList.html`, `pullrequest/list.html`,
`user/view.html`, `issue/my_list.html` (연관 JS: `yona.board.List.js`, `yona.user.View.js`, `yona.issue.List.js`).

**B의 부모-자식 결합 지점** (`site/layout.html`): 라인 41-42(`window.parent.document.title`/`history.replaceState`),
46(`window.parent === window` 분기 → `/user/sidebar?path=...` 리다이렉트), 830-852(핀 토글·부모 사이드바
표시 상태 조회), 936-942(`a.ago`, `.head-anchor`, `.share-link` 클릭 시 `window.parent.history.pushState`),
`localStorage['shallWeOpenLeftNavigation']`, `layout_framed.html:30,36`의 `target="mainFrame"` 링크.

## 범위

### 포함
- A: 나머지 6개 소비자의 2단 보기를 Turbo Frames로 전환하고, 소비자가 0이 되면 `yona.twoColumnMode.js`,
  `yona-page-slide-element` 위젯, `#pageslide` 관련 CSS(`yona.css`)를 삭제
- PoC가 확립한 공통 패턴의 **재사용 가능 모듈화**(아래 설계 개요)
- 전환된 화면마다 기존 동작(직접 URL, Back/Forward, reload, no-JS, 익명 읽기, CSRF, 초안)의 회귀 없음 검증

### 제외 (비범위)
- **C(마크다운 내 사용자 임베드 iframe)**: 사용자가 작성한 외부 콘텐츠(동영상 등)를 보여주는 것이라
  Turbo Frame으로 대체할 수 없다(Turbo Frame은 동일 출처의 우리 서버 HTML 조각용). sanitizer allowlist는
  그대로 유지하고, 보안 회귀 방지를 위해 테스트만 확인한다.
- **B: 사이드바 셸 iframe 제거** — 사용자 지시로 보류(아래 "보류" 절). `layout.html`의 `window.parent` 결합 코드는 그대로 둔다
- Turbo Drive 전역 활성화(`Turbo.session.drive = true`)와 Turbo form 제출(`forms.mode`) 활성화 — PoC가
  "CSRF 호환성 미검증"으로 남긴 영역
- 픽셀 단위 디자인 동등성, 전체 브라우저 호환성 매트릭스(PoC와 동일한 범위 한정)
- 벤더 파일(`lib/`) 수정 — 프로젝트 원칙상 미수정, 로드 참조만 제거

## 의존성

- **선행 조건**: 없음 (PR #834, P3-74 모두 `next`에 병합됨)
- **후속 파급**: 없음. 완료 시 `docs/TURBO_THYMELEAF_POC.md`의 "iframe 제거 범위" 서술을 갱신

## 설계 개요

### 재사용할 PoC 패턴 (이미 `next`에 존재)
- 선택 상태를 `?selected=<공개번호>` 쿼리로 표현 → Spring MVC + Thymeleaf가 GET으로 상태 재구성
  (`IssueViewController.listIssues()`)
- `turbo-frame#<목록>`(`data-turbo-permanent`) + `turbo-frame#<상세>`(`data-turbo-action="advance"`)
- `Turbo-Frame: <상세 id>` 요청 헤더 분기로 **목록 조회를 건너뛰고 상세 fragment만 렌더링**
  (P3-74: `"issue/list :: issueDetailFrame"`) — 새 화면마다 처음부터 이 분기를 포함시킨다(SQL 중복 재발 방지)
- 2단 보기 설정은 기존 `localStorage["useTwoColumnMode"]` 유지, 모바일(≤720px)은 정상 전체 상세 탐색
- Turbo forms off + Spring Security CSRF 계약 유지, hover prefetch off(방문 기록 부작용 방지)
- 상세 수명 관리: mount/dispose helper(`yona.issue.Detail.js`), 초안 키는 기존 상세 경로 유지

### Phase A — 2단 보기 공용화 및 소비자 전환
1. `yona.issue.Turbo.js`(104줄 어댑터)에서 **화면 비의존 부분**(선택 링크 갱신, 체크박스 동기화, 하이라이트,
   pagination/필터 쿼리 보존)을 `yona.turbo.TwoColumn.js`로 추출하고 프레임 id·선택 셀렉터를 설정으로 받게 한다.
   이슈 목록은 추출본을 쓰도록 되돌려 **동작 불변**을 e2e 6/6으로 먼저 확인.
2. 소비자별 전환 — 위험도 낮은 순:
   게시판 목록(`board/list.html`) → 조직 게시판/이슈 목록 → 내 이슈(`issue/my_list.html`) →
   PR 목록(`pullrequest/list.html`) → 사용자 화면(`user/view.html`).
   각 소비자는 (a) 상세 fragment 분리·`Turbo-Frame` 분기, (b) 목록 템플릿에 frame 마크업,
   (c) 상세 mount/dispose(댓글 초안·폴링·첨부 업로더 등 해당 화면이 상세에서 쓰는 초기화), (d) 전용 e2e를 포함.
3. 소비자 0 확인(`grep twoColumnMode`) 후 `yona.twoColumnMode.js`, `yona-page-slide-element` 위젯,
   `#pageslide` CSS, 잔존 `_initTwoColumnMode()` 호출을 삭제.

### 보류 — 사이드바 셸 iframe (B)

이번 계획에서 다루지 않는다. 재개 시 참고용으로 조사 결과만 남긴다.

- 구조: `/user/sidebar?path=X` → 사이드바 + `<iframe src=X>`. 자식(`layout.html`)이 `window.parent`로 제목/History/사이드바 상태 조작
- 부모-자식 결합 지점(`site/layout.html`): 41-42, 46, 830-852, 936-942, `localStorage['shallWeOpenLeftNavigation']`, `layout_framed.html:30,36`의 `target="mainFrame"`
- 1:1 turbo-frame 전환 시 난점: 본문 페이지 응답이 같은 id frame을 포함해야 함 / frame 탐색은 `<head>` 미병합 / 네이티브 폼·리다이렉트 시 셸 소실 / frame 없는 리다이렉트 응답의 "Content missing"
- 대안 후보: 셸 페이지 폐지 + 사이드바를 일반 레이아웃의 `data-turbo-permanent` 요소로 이동(Turbo Frame이 아니므로 재개 시 사용자 확인 필요)

## 단계별 작업 계획 (TDD)

프로젝트 관행에 따라 각 단계는 "실패하는 테스트 먼저 → RED → 최소 구현 → GREEN". Gradle 테스트는
`-Dyona.it.db=h2`로 타겟 스펙만 하나씩 `--tests` 실행(전체 스위트 반복 금지), Playwright는 실제 브라우저로 재현 확인.

0. **Step 0 — 인벤토리 확정 + 특성화 (코드 변경 없음)**
   - 소비자 6곳의 현재 동작을 Playwright로 특성화(선택 전환, History, reload). 상세 화면별 mount/dispose
     대상(인라인 JS·위젯) 체크리스트 작성. 제외 목록(B 보류, C, 벤더) 고정
1. **Step 1 — 공용 어댑터 추출 (동작 불변 리팩터링)**
   - `yona.turbo.TwoColumn.js` 추출, 이슈 목록을 그 위로 이전. `turbo-two-column.spec.ts` 6/6 GREEN 유지가 완료 조건
2. **Step 2 — 게시판 목록 전환** (첫 신규 소비자, 패턴 검증용)
   - RED: `Turbo-Frame` 헤더 요청이 fragment만 반환하고 목록 조회 SQL을 생략하는지, 일반 요청은 기존과 동일한지
     (`BoardListTwoColumnModeTemplateRenderingSpec`, `IssueListTemplateRenderingSpec`의 P3-74 테스트를 모델로)
   - e2e: `e2e/specs/09-board/`에 `turbo-two-column.spec.ts` 추가(선택/Back·Forward/reload/no-JS/초안)
3. **Step 3~6 — 조직 목록 → 내 이슈 → PR 목록 → 사용자 화면** (소비자당 1스텝, 각 스텝이 독립 커밋/푸시 가능 단위)
   - 각각 Step 2와 동일한 RED→GREEN, 화면 고유 상세 기능(PR의 diff/리뷰 위젯 등) 초기화·해제 검증 포함
4. **Step 7 — 2단 보기 레거시 삭제**
   - `grep`으로 소비자 0 확인 후 `yona.twoColumnMode.js`, Vue page-slide 위젯(+빌드 산출물), `#pageslide` CSS,
     `TemplateEquivalenceSpec`/`TwoColumnModeCheckboxDuplicateIdTemplateRenderingSpec` 중 삭제 대상 기대값 정리
     (테스트 삭제는 "죽은 코드에 대한 단언"임을 근거로만, 기능 단언은 유지)
5. **Step 8 — 마무리**
   - `grep -ri iframe src/main` 결과가 **허용 목록(B 보류분, C, 벤더)만** 남는지 확인,
     `docs/TURBO_THYMELEAF_POC.md`·`docs/TEMPLATE_BACKLOG.md`의 2단 보기 서술 갱신, index.md 상태 갱신, 완료 로그 작성

## 완료 기준 (Definition of Done)

- [ ] 2단 보기 관련 iframe 생성/참조 0 (`yona.twoColumnMode.js` 계열). 남는 것은 B(보류), C(sanitizer allowlist), 벤더뿐
- [ ] 6개 소비자 각각: 직접 URL, Back/Forward, reload, no-JS, 익명 읽기, 초안(해당 시), CSRF 거부가
      Playwright로 GREEN, 신규 화면의 `Turbo-Frame` 요청은 목록 조회를 생략(SQL 카운트 테스트로 고정)
- [ ] `yona.twoColumnMode.js`, page-slide 위젯, `#pageslide` CSS 삭제, 빌드(`bootJar`) 성공
- [ ] 기존 e2e(`06-issue`, `07-pull-request`, `09-board` 등) 전체 회귀 GREEN,
      `./gradlew test -Dyona.it.db=h2`는 무관한 사전 존재 실패를 구분해 보고
- [ ] JaCoCo 커버리지 목표(라인/분기/메서드 95%, `docs/coverage/index.md` 기준과 동일)

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 사이드바 셸 잔존 | 셸 iframe이 남아 있는 동안 2단 보기 패널과 이중 iframe 구조가 될 수 있음 | 사이드바 안에서도 2단 보기가 동작하는지 소비자별 e2e에 포함, 문제 시 별도 보고 |
| 소비자별 상세 기능 차이 | 게시글/PR/사용자 화면 상세는 이슈 상세와 다른 인라인 JS·위젯(diff, 리뷰 코멘트 등)을 가질 수 있음 | 소비자마다 mount/dispose 대상을 먼저 조사(Step 시작 시 체크리스트화). 어려운 화면은 분해해서 우회로부터 검토 |
| 초안 의미론 | 게시글 댓글 등 이슈 외 화면의 초안 키 체계가 다를 수 있음 | 화면별로 기존 키 유지, 이슈 간 격리 e2e 재사용 |
| 방문 기록 부작용 | prefetch로 이슈/게시글 방문 기록이 hover만으로 생성 | 신규 frame 전부 `data-turbo-prefetch="false"` |
| 에러 응답 | 선택 번호 오류/권한 없음이 frame 안에서 "Content missing" | PoC의 declarative reload meta 재사용, 화면별 오류 e2e |
| 삭제 대상 테스트 | 레거시 전용 단언은 삭제하지만 기능 단언은 삭제 금지 | 삭제 사유를 커밋 메시지에 명시, 기능 동등성은 Playwright로 대체 검증 |

## 관련

- 선행 작업: [[p3-74-issue-detail-fragment-sql-optimization]], PR [#834](https://github.com/yona-projects/yona/pull/834)(`af2360ff4`), #837(`d65101ae7`)
- 관련 문서: `docs/TURBO_THYMELEAF_POC.md`, `docs/TEMPLATE_BACKLOG.md`(`layout_framed`, `twoColumnModeCheckboxArea`), `docs/parity/tickets/p3-59.md`, `p3-70.md`(twoColumnMode vanilla 재구현 이력)
- 관련 소스: `static/javascripts/service/yona.twoColumnMode.js`, `yona.issue.Turbo.js`, `yona.issue.Detail.js`,
  `templates/site/layout.html`, `templates/site/layout_framed.html`, `UserViewController.kt`(`userSidebar`),
  `IssueViewController.kt`(`listIssues`)
- 관련 테스트: `e2e/specs/06-issue/turbo-two-column.spec.ts`, `BoardListTwoColumnModeTemplateRenderingSpec.kt`, `IssueListTemplateRenderingSpec.kt`, `TemplateEquivalenceSpec.kt`
