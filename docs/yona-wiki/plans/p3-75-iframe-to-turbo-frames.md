---
type: plan
id: P3-75
title: "2단 보기 iframe 잔존 소비자 Turbo Frames 전환 (사이드바 셸은 보류)"
status: in-progress
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

0. **Step 0 — 인벤토리 확정 + 특성화 (코드 변경 없음)** — ✅ PR 목록·사용자 화면 완료(아래 진행 로그), 나머지 4곳은 착수 시
   - 소비자 6곳의 현재 동작을 Playwright로 특성화(선택 전환, History, reload). 상세 화면별 mount/dispose
     대상(인라인 JS·위젯) 체크리스트 작성. 제외 목록(B 보류, C, 벤더) 고정
1. **Step 1 — 공용 어댑터 추출 (동작 불변 리팩터링)** — ✅ 완료(`5fe20dc08`)
   - `yona.turbo.TwoColumn.js` 추출, 이슈 목록을 그 위로 이전. `turbo-two-column.spec.ts` 6/6 GREEN 유지가 완료 조건
2. **Step 2 — 게시판 목록 전환** (첫 신규 소비자, 패턴 검증용) — ✅ 완료(`9c5a1b0a3`, `bbeb168e8`)
   - RED: `Turbo-Frame` 헤더 요청이 fragment만 반환하고 목록 조회 SQL을 생략하는지, 일반 요청은 기존과 동일한지
     (`BoardListTwoColumnModeTemplateRenderingSpec`, `IssueListTemplateRenderingSpec`의 P3-74 테스트를 모델로)
   - e2e: `e2e/specs/09-board/`에 `turbo-two-column.spec.ts` 추가(선택/Back·Forward/reload/no-JS/초안)
3. **Step 3 — 복합 선택 키 공용 처리 (신규, 4곳의 선행 조건)** — ✅ 완료(Step 4에서 `CrossProjectDetailResolver`로 권한·위임까지 고정). 기반: `TwoColumnSelection.parseKey()`/`Key`(형식 `type:owner/project/번호`)와 선택 파라미터명 설정화(서버 `addToModel(param=)`, 클라이언트 `setupTwoColumn({param})`), `TwoColumnSelectionSpec` GREEN. 원래 범위: 서버: `?<param>=<type>:<owner>/<project>/<번호>` 파싱→권한 확인→기존 상세 로직 위임(`Turbo-Frame` 헤더 시 목록 조회 생략). 클라이언트: 공용 어댑터의 선택 파라미터명·링크 생성 설정화. 기존 `TwoColumnSelection` 확장
   **Step 4~7 — 내 이슈 → 조직 이슈 → 조직 게시판 → PR 목록 → 사용자 화면** — ⏳ 미착수(순서는 진행 로그의 계획 영향 참고, 번호는 아래 Step 7 이후로 재정렬) (소비자당 1스텝, 각 스텝이 독립 커밋/푸시 가능 단위)
   - 각각 Step 2와 동일한 RED→GREEN, 화면 고유 상세 기능(PR의 diff/리뷰 위젯 등) 초기화·해제 검증 포함
4. **Step 7 — 2단 보기 레거시 삭제**
   - `yona.issue.List.js`의 죽은 코드도 함께 정리: `_init` 115행과 `_onLoadIssueList` 405행의 `_initTwoColumnMode()` 호출,
     그리고 `_initPjax`가 `#issue-list` 존재 시 즉시 return하므로 모든 페이지에서 도달하지 않는 pjax 블록(`_initPjax` 이후
     `_pjaxNavigate`, `_onLoadIssueList`)(2026-09-30 실측, 아래 진행 로그). 정리 전에 이 모듈을 쓰는 3개 템플릿이 모두
     `#issue-list`를 갖는지 다시 확인
   - `grep`으로 소비자 0 확인 후 `yona.twoColumnMode.js`, Vue page-slide 위젯(+빌드 산출물), `#pageslide` CSS,
     `TemplateEquivalenceSpec`/`TwoColumnModeCheckboxDuplicateIdTemplateRenderingSpec` 중 삭제 대상 기대값 정리
     (테스트 삭제는 "죽은 코드에 대한 단언"임을 근거로만, 기능 단언은 유지)
5. **Step 8 — 마무리**
   - `grep -ri iframe src/main` 결과가 **허용 목록(B 보류분, C, 벤더)만** 남는지 확인,
     `docs/TURBO_THYMELEAF_POC.md`·`docs/TEMPLATE_BACKLOG.md`의 2단 보기 서술 갱신, index.md 상태 갱신, 완료 로그 작성

## 진행 로그

### 2026-09-29 — Step 0(부분)·1·2 완료, PR 목록/사용자 화면 조사

**브랜치** `feat/p3-75-iframe-to-turbo-frames` (origin에 푸시됨). 커밋: 계획서 `8b969df3e` → 공용 어댑터
`5fe20dc08` → 게시판 전환 `9c5a1b0a3` → 게시판 결함 수정 `bbeb168e8` → PR/사용자 화면 특성화 e2e `430e485ad`.

**Step 1 — 공용 어댑터**: `yona.turbo.TwoColumn.js`(`setupTwoColumn(config)`)로 화면 비의존 로직을 추출,
`yona.issue.Turbo.js`는 설정만 넘기는 8줄. 이슈 `turbo-two-column.spec.ts` 6/6 유지. 선택 URL 계산은
`TwoColumnSelection.addToModel()`(Kotlin)로 공용화.

**Step 2 — 게시판**: `board/list.html`에 `post-list`/`post-detail` turbo-frame, `?selected=<글번호>`,
`Turbo-Frame: post-detail` 헤더 시 목록 조회 생략(`board/list :: postDetailFrame`). 게시글 상세를
`board/view :: detail` fragment + `yona.board.Detail.js`(`yona.mountBoardDetail`, AbortController 기반
mount/dispose)로 이전, 인라인 스크립트 ~200줄 제거. 신규 `BoardListTurboFrameRenderingSpec`(RED 3건 확인 후
GREEN), e2e `09-board/turbo-two-column.spec.ts` 7건. 검증: 인증·사용자·프로젝트·이슈·게시판 e2e 111건 GREEN.
인라인 스크립트 문자열을 검사하던 기존 단언 3건은 삭제하지 않고 추출된 JS 파일 검사로 이전.

**Step 2 중 발견·수정한 기존 결함**
1. 익명 게시글 상세에서 `yona.board.View._initFileUploader`가 `null.getAttribute`로 예외(업로더 없음) — 가드 추가.
2. "지켜보기" 버튼 무동작: `sWatchUrl`을 읽는데 템플릿은 `urls.watch`로 넘겨 URL이 항상 `undefined` —
   `BOARD_POST` watch/unwatch URL을 Detail.js가 전달하도록 수정.

**Step 2 중 만든 뒤 고친 회귀(교훈, 이후 소비자에 그대로 적용)**
- **리스너 중복 등록**: Turbo는 스냅샷 시(`turbo:before-cache`) 상세를 dispose했다가 `turbo:load`에서 같은
  root를 다시 mount한다. mount가 붙인 리스너가 dispose에서 해제되지 않으면 재마운트마다 중복 등록된다
  (지켜보기 POST 2회 → Watch 행 중복). 모든 리스너는 `AbortController` signal로 붙이고 dispose에서 abort.
- **라이브러리 로드 차이**: 게시판 단독 상세는 Tom Select 라이브러리를 로드하지 않아 라벨 선택이 일반
  `<select>`다. mount에서 `yona.ui.TomSelect`를 무조건 호출하면 예외로 mount가 중단되고, 새로 로드하면 UI가
  바뀐다 — 라이브러리가 있는 페이지에서만 초기화(가드)해 원래 동작을 보존.
- e2e 시드 함정: 숨김 textarea에 `fill({force})`만 하면 에디터 위젯에 값이 안 들어가 본문이 빈 글이 된다
  (이슈 스펙처럼 `value` 설정 + `input` 이벤트 필요).

**미해결(범위 밖, 별도 항목 후보)**: `/watch`가 동일 사용자·자원에 멱등하지 않아 Watch 행이 중복되면 이후
상세 조회가 `IncorrectResultSizeDataAccessException`으로 500이 된다(클라이언트 중복은 위 수정으로 제거).

**Step 0 — PR 목록·사용자 화면 특성화**: `e2e/specs/15-misc/legacy-iframe-two-column.spec.ts`(8건, 자체 시드).
현재 iframe 구조는 두 화면 모두 정상 동작함을 실측으로 확인. 고정한 동작: 행 클릭 → page-slide iframe에 PR/이슈
페이지 로드, 부모 URL이 항목 URL로 pushState, 행 `highlightBg`, 같은 행 재클릭 시 닫힘, 설정 off 시 일반
이동, **pushState된 URL을 reload하면 2단 목록이 아니라 단독 상세 페이지**(Turbo 전환 시 개선되는 지점 —
전환할 때 이 단언은 새 동작으로 교체), 사용자 화면의 `?selected=`는 탭 이름.

#### PR 목록 조사 결과 (Step 5 착수 전 참고)
- 목록 라우트 3개(`/pulls`, `/closedPullRequests`, `/sentPullRequests`)가 `partial_list`를 공유하고 탭 링크가
  검색 폼 `action`을 바꿔 제출한다 → 3곳 모두 `selected` 처리와 `Turbo-Frame` 분기 필요. 행 마크업은
  `li.post-item` + `a.title`이라 공용 어댑터 그대로 사용 가능.
- 상세 `pullrequest/view.html`(883줄, 인라인 JS ~570줄)이 난점: overview 탭은 `yona.pullrequest.View.js`의
  10초 상태 폴링(dispose 없음), `document` 위임 `#btnAccept` 클릭(재마운트 시 중복 등록 위험), 지켜보기·담당자·
  라벨·리뷰 버튼. changes 탭은 `window` scroll/resize/hashchange, 미니맵, affix, 코드 댓글 박스와 전체 폭
  레이아웃(`code-browse-wrap`).
- `viewPullRequest`는 조회마다 병합 시뮬레이션(`attemptMerge`, JGit)을 실행 — 프레임 응답에서도 필요해
  생략 불가(절약되는 것은 목록 조회뿐).
- **권장안(결정 대기)**: 프레임에는 overview 탭만, changes 탭 링크는 `target="_top"` 전체 페이지 이동.
  overview 스크립트만 `yona.pullrequest.Detail.js`로 이전(폴링 타이머·위임 핸들러 dispose에서 해제).
- e2e에는 PR 시드(git CLI push + PR 생성)가 필요 — 특성화 스펙의 시드 코드를 재사용.

#### 사용자 화면 조사 결과 (Step 6 착수 전 참고)
- 단일 목록이 아니다: 이슈(열림/닫힘)·PR·프로젝트 탭, 항목이 서로 다른 프로젝트·타입에서 온다.
- **`selected` 이름 충돌**: `/user/{loginId}`의 `?selected=`는 이미 탭 이름 → 선택 번호에 다른 파라미터명 필요,
  공용 어댑터의 `selected` 하드코딩을 설정으로 받게 해야 한다.
- 선택 키가 복합값이어야 한다(타입+owner+project+번호, 예: `?detail=issue:owner/project/N`). 상세 라우트가
  프로젝트별이라 사용자 화면 컨트롤러가 이슈/PR 상세 로직으로 위임해야 함. 대안(프레임이 상세 URL을 직접
  로드)은 reload 시 목록·2단 상태가 사라져 이슈 PoC 의미론에서 후퇴.
- 선행 관계: 이슈 상세 fragment는 있으나 PR 상세 fragment는 Step 5에서 생긴다 → **PR 목록 → 사용자 화면 순서**.
  `showSubtask.js`, 부트스트랩 탭 전환, 2단 시 좌측 정보 패널 숨김 동작도 함께 확인 필요.

#### 조직 게시판·조직 이슈·내 이슈 조사 결과 (2026-09-29, 기존 "상세 재사용, 게시판의 1/3 규모" 가정이 깨짐)
- 세 화면 모두 **여러 프로젝트에 걸친 목록**: 조직 게시판은 행이 `post.project`별로, 조직 이슈는
  `visibleProjects` 전체를 `IssueSpecification.filterOrganizationIssues`로 한 번에 조회, 내 이슈도 행 링크가
  `/{issue.project.owner}/{issue.project.name}/issue/{number}`. `?selected=<번호>`만으로는 프로젝트를 알 수 없다.
- **결론: 복합 선택 키(owner/project/번호)가 4곳(조직 게시판, 조직 이슈, 내 이슈, 사용자 화면)에 필요**하다.
  서버에서 복합 키를 받아 기존 상세 로직(`viewIssue`/`viewPost`/PR 상세)으로 위임하는 **공용 처리를 먼저**
  만들어야 하며, 한 번 만들면 4곳이 함께 해결된다. 공용 어댑터의 `selected` 파라미터명 하드코딩도 설정화 필요.
- 검색 폼이 `href="#"` + 속성(`orderBy=…`) 기반 JS 제출이고 조직 이슈·내 이슈는 `issue.List` 모듈이
  제출/페이지네이션을 맡는다 → 필터·정렬 링크에 선택 키를 싣는 방식이 게시판/이슈 목록과 다르다.
- 조직 e2e: 기존 `03-organization` 스펙은 조직만 만들고 조직 소속 프로젝트는 만들지 않는다 → 조직 아래
  프로젝트·게시글·이슈 시드가 새로 필요(프로젝트 폼 owner 선택에 조직이 나오는지는 미확인).

#### 공통 조사 결과
- **레거시 부수 동작**: `yona.twoColumnMode.js`는 패널이 열리면 `.left-menu`(필터 사이드 메뉴)와
  `.user-info-box`(사용자 화면 좌측 정보 패널)를 숨긴다(`isLeftMenuHide`). 이슈·게시판 전환은 이 동작을
  옮기지 않고 2열 그리드만 썼다 → 상세가 열릴 때 좌측 패널을 CSS로 숨길지 결정 필요(이슈·게시판도 재확인).
- **사이드바 iframe 안 동작(실측)**: 사이드바 셸(`/user/sidebar?path=…`) 안에서 전환된 게시판은 프레임
  상세가 정상 동작하지만 **최상위 URL·제목이 바뀌지 않는다**(Turbo `advance`가 iframe 자신의 History에만
  기록; 레거시는 `layout.html`의 `window.parent.history` 코드가 부모 URL을 갱신). 사이드바는 보류 범위지만
  사이드바 모드에서는 URL 동기화가 약해진다. 사이드바 안 Back 이동은 시간 초과로 결론 못 냄(원인 미확인).
- **테스트 영향(Step 7)**: `twoColumn` 계열 단언은 `TwoColumnModeCheckboxDuplicateIdTemplateRenderingSpec`(8),
  `PullRequestListTemplateEquivalenceSpec`(1) 정도로 작다(게시판 관련은 처리 완료). page-slide 위젯 소스는 이
  저장소에 없고 삭제 대상은 빌드 산출물(`static/lib/yona-vue-widgets/yona-page-slide-element.js`)과
  `layout.html:683` 로드 태그.
- **v1.6 대조**: 이슈·내 이슈·게시판·조직 게시판·조직 이슈·사용자 화면·PR 목록 모두 v1.6에 2단 보기가
  있었고 모두 동일한 iframe(pageslide) 방식. v1.6의 PR 2단 보기는 iframe 안에서 개요/코드 리뷰 탭이
  모두 열렸다 → "PR은 개요 탭만 프레임" 안은 원본과 다른 동작이 된다(대안 (a): 프레임 안에서 코드 리뷰까지
  지원 — changes 탭의 전역 리스너를 모두 mount/dispose 구조로 바꾸고 반쪽 폭 레이아웃을 새로 설계해야 해
  비용 최대).

**계획 영향**: 복합 키 공용 처리를 별도 스텝으로 먼저 수행하고, 남은 순서는 복합 키 공용화 → 내 이슈 →
조직 이슈·게시판 → PR 목록 → 사용자 화면이 자연스럽다. 조직·내 이슈 3곳은 "화면당 1/3"이 아니라
"공용화 1회 + 화면당 소규모"로 재추정한다.

#### 추가 확인 결과 (2026-09-29, 미확인 항목 점검)
**확인됨**
- `organization/pullRequestList.html`은 2단 보기 소비자가 **아니다**(체크박스·iframe 코드 없음, v1.6에도 조직 PR
  목록의 2단 보기 없음) → 범위 밖 확정.
- `yona.twoColumnMode.js`의 `mainWidth`는 파일 밖에서 쓰이는 곳이 없는 **죽은 코드** → 이식 불필요.
- `.left-menu`는 이슈 목록·조직 이슈 검색·내 이슈 검색에만 있고 **게시판 목록에는 없다**(게시판은 레거시에서도
  숨길 좌측 메뉴가 없었다). 사용자 화면은 `.user-info-box`만 해당.
- 사이드바 iframe 안 Back은 **동작한다**: 앞선 시간 초과는 Playwright `goBack`이 최상위 이동만 기다리는 도구
  문제였고, iframe 내부 History가 되돌려져 프레임이 `?selected=` 없는 상태로 복원됨. 최상위 URL·제목이
  갱신되지 않는 한계는 그대로.
- 접근 제어(코드 확인, 실행 검증은 아님): 조직 목록은 서버에서 `getVisibleProjects`로 조회 대상을 걸러내고,
  내 이슈는 로그인 필요. 상세는 기존 상세 핸들러의 프로젝트 읽기 게이트를 다시 거치는 설계.
- 조직 e2e 시드: 프로젝트 생성 폼 owner 선택에 조직 옵션이 들어가는 코드가 있어 조직 아래 프로젝트 생성은
  가능해 보임(실행 검증은 아님).

**새로 발견: 행 클릭 충돌 가능성(사용자 화면)** — `yona.user.View.js`가 `.post-item` 행 클릭에 "하위 이슈 목록
펼치기"를 붙이고 제목 링크에는 `stopPropagation`을 건다. 공용 어댑터는 "행의 빈 곳 클릭 → 제목 링크 클릭"으로
위임하므로 같은 클릭이 펼치기와 상세 선택을 동시에 일으킬 수 있다. 사용자 화면 전환 시 하나로 정해야 하며,
이슈 목록(`issue.List`)·내 이슈(`showSubtask.js`)에도 같은 종류의 충돌이 있는지 확인이 필요하다.

**아직 미확인**
1. 복합 키 처리에서 권한 없는 프로젝트의 키가 실제로 거부되는지 — Step 3에서 테스트로 고정.
2. 조직 프로젝트 e2e 시드를 실제로 만들어 돌려 본 적 없음.
3. v1.6과의 **동작 수준** 대조(파일 존재만 확인; 예: v1.6이 조직 목록에서 좌측 메뉴를 숨겼는지는 소스만 봄).
4. 조직·사용자 화면의 모바일(≤720px) 실제 렌더링(어댑터는 일반 이동으로 물러나지만 레이아웃은 미확인).
5. 사이드바 안 최상위 URL·제목 동기화 해결 방법 — 사이드바 결정과 함께 정해야 함.

**결정 대기**: (1) PR 상세를 overview 탭만 프레임에 넣는 안(v1.6과 다른 동작, 위 참고), (2) 복합 선택 키 방식 확정(4곳 공용), (3) 상세가 열릴 때 좌측 패널(`.left-menu`, `.user-info-box`)을 숨길지(게시판 제외), (4) 사용자 화면 행 클릭 시 하위 이슈 펼치기와 상세 선택의 충돌 처리.
**조사 완료**: 소비자 6곳 모두. 남은 것은 결정과 구현.

### 2026-09-29 — Step 4(내 이슈) 완료

- **서버**: `CrossProjectDetailResolver`(공용)가 `?detail=issue:<owner>/<project>/<번호>`를 `IssueViewController.viewIssue`에 위임한다.
  권한 확인·방문 기록·오류 뷰 선택은 단독 상세와 동일하다(이 클래스는 권한을 판단하지 않는다). `UserViewController.userIssues`가
  `Turbo-Frame: issue-detail` 헤더 시 목록 조회 없이 `issue/my_list :: issueDetailFrame`만 반환한다.
  파라미터명은 `detail`(사용자 화면은 `selected`가 탭 이름이라 겹치지 않게 공통으로 `detail`).
- **클라이언트**: `yona.issue.CrossProjectTurbo.js`(내 이슈·조직 이슈 공용)(`setupTwoColumn({param: 'detail'})`). `my_list.html`에서 `yona.twoColumnMode.js` 로드를 제거했다.
- **`issue/view.html`**: 프로젝트별 `labels.css` `<link>`를 `detailAssets`에서 `detail` 조각 안으로 옮겼다. 여러 프로젝트에 걸친 목록은
  페이지 로드 시점에 프로젝트를 알 수 없고 프레임으로 불러오는 상세마다 프로젝트가 다르기 때문이다.
- **검증**: `MyIssuesTurboFrameRenderingSpec` 6건(프레임 fragment, SQL 상한, 비공개 프로젝트 키 거부, 잘못된 키, 비로그인),
  `TwoColumnSelectionSpec`, `UserViewControllerSpec`, 기존 `IssueListTemplateRenderingSpec` GREEN.
  e2e `06-issue/turbo-two-column-my-issues.spec.ts` 5건 GREEN(A/B 전환, Back/Forward, reload, no-JS, 클릭당 요청 1회, 선택 해제, 검색 폼 선택 유지, 잘못된 키).
- **기존 e2e 불안정 발견·해결(이번 변경과 무관했음)**: `06-issue`/`09-board` Turbo 스펙이 신선한 H2 서버에서 간헐 실패했다(전체 스펙 8회 중 5회).
  `labels.css` 이동만, 어댑터만 각각 되돌려 봐도 같은 비율로 실패해 이번 변경이 원인이 아님을 확인했고, 실패한 실행의 요청 로그를 비교해 원인을 찾았다.
  **원인은 하이드레이션 경합**: 서버가 그린 목록/상세는 스크립트 초기화 전에도 화면에 보이는데, 테스트가 텍스트/URL이 보이자마자 조작했다.
  (1) 페이지 이동 중 검색 버튼 클릭 → 핸들러 없음, (2) 선택된 행 재클릭 → 어댑터 초기화 전이라 프레임 이동 대신 문서 이동(서버가 낸 링크는 선택 URL),
  (3) 댓글 등록 후 `location.reload()` 직후 태스크 체크 → 태스크리스트 초기화 전이라 PATCH 없음.
  **수정**: `setupTwoColumn`이 초기화를 마치면 레이아웃에 `data-ready="true"`를 남기고, e2e는 `support/two-column.ts`의 `twoColumnReady(page)`로
  새 문서가 뜰 때마다 이를 기다린다(이슈·게시판·내 이슈 스펙). 수정 후 세 스펙 16건을 10회 연속 실행해 전부 통과.
  **남은 관찰(제품 측)**: 초기화 전에 선택된 행을 누르면 토글이 아니라 같은 선택 URL로 문서 이동한다(서버가 내는 href가 선택 URL이기 때문).
  실사용에서는 ms 단위 창이라 지금은 두었다.
- **남은 작업**: Step 5~ 조직 이슈 → 조직 게시판 → PR 목록 → 사용자 화면. 좌측 패널(`.left-menu`) 숨김 여부는 내 이슈에서도 아직 미이식(결정 대기 (3)).

### 2026-09-30 — Step 5(조직 이슈) 완료

- **서버**: `OrganizationViewController.organizationIssues`가 `CrossProjectDetailResolver.handleIssueSelection`을 조직 조회 직후(목록 조회 전)에 호출한다.
  내 이슈와 공유하는 로직을 이 메서드로 모았고(`UserViewController`도 이를 사용), `allowedOwner = org.name`으로 **조직 밖 프로젝트의 키는 읽을 수 있어도 거부**한다.
  `Turbo-Frame: issue-detail` 요청은 `organization/issueList :: issueDetailFrame`만 반환한다.
- **클라이언트**: 내 이슈·조직 이슈가 같은 설정이라 모듈을 `yona.issue.CrossProjectTurbo.js` 하나로 합쳤다. 조직 화면에서 `common/tomselect`를 따로 로드하던 것은
  `issue/view :: detailAssets`(tomselect 포함)로 대체했다.
- **검증**: `OrganizationIssuesTurboFrameRenderingSpec` 7건(두 프로젝트 행 링크, 프레임 fragment, SQL, 비공개 프로젝트 키 거부, 조직 밖 키 거부, 잘못된 키),
  e2e `06-issue/turbo-two-column-org-issues.spec.ts` 4건(조직 생성→프로젝트 2개→이슈 시드, A/B 전환·Back/Forward·reload·no-JS·클릭당 요청 1회·선택 해제, 검색 폼 선택 유지, 잘못된 키).
  이슈·내 이슈·조직 이슈·게시판 Turbo e2e 20건을 5회 연속 실행해 전부 통과.
- **Step 2에서 생긴 결함 정리**: `TemplateEquivalenceSpec`의 `labels.css` 링크 검증이 게시판 목록에서 2개로 실패했다. 게시판 목록이 머리말과 `detailAssets`로 같은 링크를
  두 번 냈기 때문이며 Step 2 이후 이 스펙을 돌리지 않아 숨어 있었다. 머리말 쪽을 제거했다.
- **다음**: 조직 게시판(`post` 타입 위임을 리졸버에 추가, 게시글 상세 조각에도 프로젝트별 `labels.css`를 상세와 함께 싣기).

### 2026-09-30 — Step 6(조직 게시판) 완료

- **서버**: `CrossProjectDetailResolver`를 종류별(`Kind.ISSUE`/`Kind.POST`)로 일반화했다(`handleSelection`, 상세 프레임 id와 성공 뷰 이름을 종류가 결정).
  `OrganizationViewController.organizationBoards`가 `?detail=post:<owner>/<project>/<번호>`를 `BoardViewController.viewPost`에 위임하고,
  `Turbo-Frame: post-detail` 요청에는 `organization/boardList :: postDetailFrame`만 반환한다. 조직 밖 프로젝트의 키는 거부한다.
- **클라이언트**: `yona.board.CrossProjectTurbo.js`(`param: 'detail'`, `searchField: '#option_form input[name="detail"]'`). `boardList.html`에서 `yona.twoColumnMode.js` 로드 제거.
- **`labels.css` 배치**: 이슈에서 했던 것처럼 게시글 상세 조각(`board/view :: detail`) 안으로 옮기고 `detailAssets`에서는 뺐다. 프로젝트 게시판 목록은 선택이 없을 때만
  머리말에서 링크한다(`th:unless="${selected != null}"`). 그래야 목록·단독 상세 어느 경우에도 링크가 정확히 1개다(`TemplateEquivalenceSpec`이 고정).
- **발견·수정한 레이아웃 결함**: 조직 게시판의 2단 보기에서 검색 버튼이 클릭되지 않았다. 목록 열이 반으로 좁아지면 검색 폼(프로젝트 선택 + 검색창)이 두 줄이 되는데
  `.search-wrap`이 한 줄 기준 `height: 30px` 고정이라 폼이 넘쳐 정렬 링크(`.filter-wrap`)와 겹쳤다(Playwright가 "intercepts pointer events"로 검출).
  `issue-columns.css`에 `.board-columns.has-detail .search-wrap { height: auto }`와 float 정리를 추가했다(2단 보기 상태에서만 적용).
- **검증**: `OrganizationBoardsTurboFrameRenderingSpec` 7건, e2e `09-board/turbo-two-column-org-boards.spec.ts` 4건, 이슈·내 이슈·조직 이슈·게시판·조직 게시판 Turbo e2e 24건을 5회 연속 실행해 전부 통과.
- **다음**: PR 목록(결정 대기: PR 상세를 개요 탭만 프레임에 넣는 안) → 사용자 화면 → 레거시 삭제.

### 2026-09-30 — 전환된 화면의 옛 iframe 코드 잔존 여부 실측

- **의문**: `yona.issue.List.js`의 `_initTwoColumnMode()` 호출 2곳(115행 `!#issue-list` 조건부, 405행 pjax 갱신 콜백)이 전환 완료된
  화면에서 실행되는가. 이 호출은 이제 로드되지 않는 `yona.twoColumnMode.js`의 함수라 실행되면 ReferenceError이며, pjax 경로는
  실패 시 조용히 전체 페이지 이동으로 폴백해 오류를 가릴 수도 있다.
- **결과(Playwright 실측, 이슈 목록·게시판 목록·내 이슈·조직 이슈·조직 게시판 5화면)**: 옛 진입점 `_initTwoColumnMode` 미정의,
  `yona.twoColumnMode.js` 미로드, `yona-page-slide`/`#pageslide`/`iframe` 0개(행 선택 후에도), 페이지·콘솔 오류 없음.
  115행은 이 모듈을 쓰는 3개 템플릿(이슈 목록·내 이슈·조직 이슈)이 모두 `#issue-list`를 가져 실행되지 않고, 405행은
  `_initPjax`가 `#issue-list` 존재 시 즉시 return해 pjax가 설치되지 않으므로 도달하지 않는다(필터 링크는 문서 전체 이동, `window`
  마커가 유지되지 않음으로 확인). → pjax 블록은 현재 모든 페이지에서 사실상 죽은 코드(Step 7에서 정리).
- **회귀 방지**: `e2e/specs/15-misc/converted-screens-no-legacy-iframe.spec.ts` 7건(위 5화면 단언).

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
| dispose 누락으로 인한 중복 리스너 | 재마운트마다 핸들러가 늘어 요청이 중복됨(게시판에서 실제 발생) | 모든 상세 모듈의 리스너를 AbortController로 붙이고 dispose에서 abort, 소비자별 e2e에 '반복 마운트 후 클릭당 요청 1회' 포함 |
| 복합 선택 키 | 4곳이 프로젝트를 넘나드는 목록이라 `?selected=<번호>` 불가 | Step 3에서 공용 처리, 프로젝트 읽기 게이트를 프레임 응답에서도 동일하게 거치는지 소비자별 테스트로 고정 |
| 좌측 패널 숨김 | 레거시는 패널 오픈 시 `.left-menu`/`.user-info-box`를 숨김, Turbo 전환은 미이식(게시판은 좌측 메뉴 자체가 없음) | 결정 후 `.has-detail` CSS로 처리, 이슈 목록은 재확인 |
| 행 클릭 충돌 | 사용자 화면 `.post-item` 클릭의 하위 이슈 펼치기 vs 어댑터의 행 클릭 위임 | 화면별로 동작을 하나로 정하고 e2e로 고정, 이슈 목록·내 이슈도 점검 |
| 사이드바 모드 URL 동기화 | 사이드바 iframe 안에서는 Turbo `advance`가 최상위 URL을 못 바꿈 | 사이드바 보류 범위와 함께 재검토(사이드바 결정 시 같이) |
| 삭제 대상 테스트 | 레거시 전용 단언은 삭제하지만 기능 단언은 삭제 금지 | 삭제 사유를 커밋 메시지에 명시, 기능 동등성은 Playwright로 대체 검증 |

## 관련

- 선행 작업: [[p3-74-issue-detail-fragment-sql-optimization]], PR [#834](https://github.com/yona-projects/yona/pull/834)(`af2360ff4`), #837(`d65101ae7`)
- 관련 문서: `docs/TURBO_THYMELEAF_POC.md`, `docs/TEMPLATE_BACKLOG.md`(`layout_framed`, `twoColumnModeCheckboxArea`), `docs/parity/tickets/p3-59.md`, `p3-70.md`(twoColumnMode vanilla 재구현 이력)
- 관련 소스: `static/javascripts/service/yona.twoColumnMode.js`, `yona.issue.Turbo.js`, `yona.issue.Detail.js`,
  `templates/site/layout.html`, `templates/site/layout_framed.html`, `UserViewController.kt`(`userSidebar`),
  `IssueViewController.kt`(`listIssues`)
- 관련 테스트: `e2e/specs/06-issue/turbo-two-column.spec.ts`, `BoardListTwoColumnModeTemplateRenderingSpec.kt`, `IssueListTemplateRenderingSpec.kt`, `TemplateEquivalenceSpec.kt`
