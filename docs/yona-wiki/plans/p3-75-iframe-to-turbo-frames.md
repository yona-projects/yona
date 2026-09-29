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
3. **Step 3~6 — 조직 목록 → 내 이슈 → PR 목록 → 사용자 화면** — ⏳ 미착수(PR 목록·사용자 화면은 조사 완료, 아래 진행 로그) (소비자당 1스텝, 각 스텝이 독립 커밋/푸시 가능 단위)
   - 각각 Step 2와 동일한 RED→GREEN, 화면 고유 상세 기능(PR의 diff/리뷰 위젯 등) 초기화·해제 검증 포함
4. **Step 7 — 2단 보기 레거시 삭제**
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

**결정 대기**: (1) PR 상세를 overview 탭만 프레임에 넣는 안, (2) 사용자 화면 복합 선택 키 방식.
**아직 조사하지 않은 소비자**: 조직 게시판 목록, 조직 이슈 목록, 내 이슈(기존 게시판/이슈 상세 재사용 가정).

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
| 삭제 대상 테스트 | 레거시 전용 단언은 삭제하지만 기능 단언은 삭제 금지 | 삭제 사유를 커밋 메시지에 명시, 기능 동등성은 Playwright로 대체 검증 |

## 관련

- 선행 작업: [[p3-74-issue-detail-fragment-sql-optimization]], PR [#834](https://github.com/yona-projects/yona/pull/834)(`af2360ff4`), #837(`d65101ae7`)
- 관련 문서: `docs/TURBO_THYMELEAF_POC.md`, `docs/TEMPLATE_BACKLOG.md`(`layout_framed`, `twoColumnModeCheckboxArea`), `docs/parity/tickets/p3-59.md`, `p3-70.md`(twoColumnMode vanilla 재구현 이력)
- 관련 소스: `static/javascripts/service/yona.twoColumnMode.js`, `yona.issue.Turbo.js`, `yona.issue.Detail.js`,
  `templates/site/layout.html`, `templates/site/layout_framed.html`, `UserViewController.kt`(`userSidebar`),
  `IssueViewController.kt`(`listIssues`)
- 관련 테스트: `e2e/specs/06-issue/turbo-two-column.spec.ts`, `BoardListTwoColumnModeTemplateRenderingSpec.kt`, `IssueListTemplateRenderingSpec.kt`, `TemplateEquivalenceSpec.kt`
