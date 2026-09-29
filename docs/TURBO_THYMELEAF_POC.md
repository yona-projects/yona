# Turbo + Thymeleaf scoped navigation

## 요약

**공용 helper 변경을 허용한 뒤 실제 전환을 구현했다.** 앞선 “중단 조건 6” 결론은 더 이상 현재 상태가 아니다. 이슈별 초안이라는 1.x 의미론을 유지하면서, 목록의 iframe/pageslide 탐색을 실제 Turbo Frames로 대체했다.

- 선택 이슈는 `?selected=<공개 이슈 번호>`로 표현하고 Spring MVC + Thymeleaf가 직접 GET 상태를 재구성한다.
- 2단 보기 설정은 기존 `localStorage["useTwoColumnMode"]`를 그대로 사용한다.
- 댓글 초안은 **기존 상세 경로**(`/owner/project/issue/N`)를 키로 유지한다. 목록 pathname으로 저장하지 않는다.
- 기존 초안의 실제 편집기 표시, 이슈 간 격리, 빠른 전환 시 저장, 복원 삭제 및 제출 후 제거를 브라우저에서 검증했다.
- 전체 클라이언트 JS가 사라진 것은 아니다. 탐색은 Turbo가 맡고, 기존 위젯과 필요한 lifecycle helper는 유지한다.

## 브랜치와 커밋

- 기준: 작업 시작 시 fetch한 `origin/next` `1d04fe7`.
- Worktree: `/Users/senghyunjo/github/yona-turbo-poc`.
- Branch: `poc/turbo-thymeleaf`.
- `38bf6a306`: 공식 Turbo 자산 빌드 파이프라인.
- `46d734d4c`: 기존 초안 키와 공용 mount/dispose helper.
- `37e4c593d`: 실제 controller/template/JS 전환.
- `7a7123409`: 실제 경로의 수용 테스트와 기존 회귀 테스트 갱신.

초기 특성화와 음성 실험은 `4825125d4`까지의 이력에 남아 있다. 현재 브랜치에서는 응답을 테스트 코드로 감싸던 probe와 legacy 전용 테스트를 제거했다. 현재 수용 테스트에는 HTTP 응답 재작성이나 가짜 상세 데이터가 없다. push/PR 생성은 하지 않았다.

## 유지한 의미론과 구현

| 관심사 | 구현 |
|---|---|
| 이슈 읽기 권한 | 기존 프로젝트 read gate와 정상 `viewIssue` 경로 재사용. 내부 DB id가 아닌 공개 번호로 조회 |
| 목록 검색·정렬·페이지 | 선택 링크는 기존 query의 인코딩·순서·반복 파라미터를 유지하면서 `selected`만 교체 |
| 목록과 상세 | `turbo-frame#issue-list`, `turbo-frame#issue-detail`; `issue/view :: detail`을 정상 상세 페이지와 공유 |
| 선택 탐색 | `data-turbo-frame` / `data-turbo-action="advance"`; 앱의 custom fetch/HTML swap/History API 없음 |
| 같은 이슈 재클릭 | 선택을 해제하는 목록 URL로 이동해 상세를 닫음 |
| Back/Forward | Turbo가 복원. 목록과 프로젝트 shell은 permanent DOM으로 유지하고 상세만 다시 mount |
| 목록 필터·pagination | 기존 컨트롤을 통한 정상 문서 탐색. 선택 변경 후 pagination도 현재 URL로 갱신하여 선택을 유지 |
| 초안 | 서버가 제공한 `data-draft-key`를 binding 시 캡처. 기존 상세 경로 키와 호환 |
| 편집기 표시 | 숨겨진 textarea만 수정하지 않고 기존 `<yona-markdown-editor>.value` 공개 API 사용 |
| 빠른 전환 | 5초 autosave를 기다리지 않아도 pending 초안을 dispose 시 해당 이슈 키에 저장 |
| 상세 수명 | 이벤트/폴링/알림수신자 요청 해제, 업로더 등록 및 위젯 참조 정리. 전역 DOMContentLoaded 재발행 없음 |
| mutation | Turbo forms 비활성화. 기존 일반 폼과 fetch/XHR의 Spring Security CSRF 계약 유지 |
| 모바일 | 직접 selected URL은 세로 배치로 읽을 수 있고, 목록 링크는 정상 전체 상세 탐색 사용 |

잘못되거나 없는 선택 번호는 기존 프로젝트 `error/notfound` 뷰를 사용한다. 이 뷰의 현재 HTTP 200 동작까지 이번 PoC에서 바꾸지는 않았다. 프로젝트 접근 거부는 기존 forbidden gate를 따른다. 선택 frame의 오류 응답에는 Turbo의 declarative reload meta를 사용해 일반 오류 화면으로 전환한다.

목록은 더 이상 `yona.twoColumnMode.js`를 로드하지 않는다. 해당 파일은 게시판 등 다른 소비자를 위해 남겼다. 목록 밖의 iframe/sidebar 기능까지 제거하지 않았다. Hover prefetch는 이 영역에서 꺼서, hover만으로 서버의 이슈 방문 기록이 생기지 않도록 했다.

후속 sidebar 전환은 아래 독립 PR 절을 따른다. 위 항목은 최초 이슈 2단 보기 PoC의 범위 기록이다.

## 검증 결과

### 실제 브라우저

기존 Playwright 기반으로 다음을 실행했다.

- **45/45 통과**: 프로젝트 생성·홈·멤버, 전체 이슈 스펙, 게시글 CRUD 및 신규 Turbo 수용 테스트.
- **2/2 통과**: 별도 실행한 사용자별 “내 이슈” 소비자 회귀 + setup.
- 신규 Turbo 스펙 단독 **6/6 통과**: setup + 다섯 개의 동작 시나리오.

신규 시나리오는 A/B 선택, 목록 DOM 유지, 실제 Turbo 요청 수, Back/Forward, reload, 복사한 URL, JS 비활성화, 익명 읽기, 기존 초안 키, **화면에 보이는 편집기** 복원, 빠른 전환, 초안 삭제, 빈 댓글 방지, 단축키 제출, 중복 제출 방지, CSRF 거부, 이전 이슈 폴링 종료, 즐겨찾기·사이드바 유지, 구독, 첨부 업로드/삭제, task 저장, **선택을 바꾼 뒤 pagination**, 필터, 같은 이슈 닫기, preference off 및 모바일을 포함한다.

초기 통합에서 실제로 발견하고 수정한 것은 다음과 같다.

- DOM ready에 묶인 상세 초기화와 dispose 누락.
- 숨겨진 textarea에는 초안이 있지만 실제 편집기에는 보이지 않는 불일치.
- 상세 전환 시 남는 업로더 registry 참조.
- favorite 이벤트 위임 때문에 사이드바가 닫히는 변화.
- document 위임 tooltip의 비-Element target 처리.
- Shadow DOM pagination의 링크가 이전 선택 이슈를 유지하던 문제.

기존 일부 E2E는 이미 같은 URL에 있는 상태에서 `waitForURL` 또는 `networkidle`로 폼 제출을 기다리고 있었다. 실제 mutation 완료 전에 다음 탐색이 시작되는 race를 재현했으며, 새 문서 navigation을 기다리도록 바꿨다. 기능 단언을 삭제하거나 실패를 무시하지 않았다.

데스크톱 1440×1000 및 모바일 390×844의 실제 렌더링도 캡처·확인했다. 픽셀 단위 1.x 디자인 동등성이나 모든 브라우저 호환성까지 주장하지는 않는다.

### Spring 및 production build

- 선택 번호/누락/잘못된 값/권한/기존 query 보존 controller 테스트.
- 열림 목록에서 제외된 닫힌 이슈도 선택 상세로 렌더링하는 실제 Thymeleaf 테스트.
- 전체 실행: `DOCKER_HOST=unix:///Users/senghyunjo/.orbstack/run/docker.sock ./gradlew test bootJar --continue`.
- **6,723개 중 6,722 통과, 1 실패. 전체 green 아님.**
- 실패는 전환 전 baseline에서도 관찰한 `LegacyIssueResponseIntegrationSpec:142`의 빈 attachments 배열 `.single()`이다. 해당 기능은 변경하지 않았으며 이 실패를 이번 작업에서 해결했다고 주장하지 않는다.
- `./gradlew bootJar` 별도 실행 성공.

외부 파일로 옮긴 JS의 함수 호출 문자열을 HTML 안에서 찾던 테스트는 새 문자열로 다시 고정하지 않고 제거했다. 초안·댓글·폴링·첨부 등은 실제 브라우저 동작으로 검증한다. 무관한 게시글 테스트는 보존했다. 이 checkout에는 WTR manifest/config가 없어 새 테스트 프레임워크를 추가하지 않았다.

## 측정

### JavaScript / 네트워크

| 항목 | 결과 |
|---|---|
| 대상 경로가 사용하던 navigation 파일 | 292줄 legacy 모듈 → 104줄 Turbo adapter |
| legacy 파일의 물리적 삭제 | 없음: 다른 화면의 소비자가 남아 있음 |
| authored `.js` diff | +636 / −148줄, generated `yona-lib.js` 제외 |
| 상세 inline JS | 277줄 → 0줄; 동작은 재사용 가능한 `yona.issue.Detail.js` 246줄로 추출 |
| 공용 layout inline JS | 360줄 → 352줄 |
| 합산 authored JS 순증 | **+203줄**. comments/blank lines 포함; 의존성·generated bundle·테스트 제외 |
| 선택 시 custom History / custom detail fetch | 각각 0. 공용 shell의 기존 초기화 History 코드는 별개로 유지 |
| A→B 상세 요청 | **2회**, 각각 `Turbo-Frame: issue-detail`, 서버 HTML 응답 |
| iframe / page-slide 생성 | 없음 |
| 최종 브라우저 HTML body | A 99,244 bytes / B 99,236 bytes, 인증 사용자·이슈 3개 fixture |
| 공식 Turbo asset | 203,701 bytes, uncompressed |

HTML 수치는 decoded body bytes이며 압축 wire bytes가 아니다. 앞선 baseline의 143개 browser request events에는 캐시·blob·기존 analytics 등이 섞여 있으므로 이를 그대로 서버 round-trip 감소율로 비교하지 않는다. 이 결과는 **탐색 책임의 이전**을 입증하지, “전체 custom JS 대부분 삭제”를 입증하지는 않는다. 목록에서 상세 의존성을 미리 로드하는 비용도 있다.

### SQL

임시 MockMvc 측정에서 공개 프로젝트·이슈 2개·익명 사용자 fixture를 만들고, 요청 전 persistence context와 Hibernate statistics를 초기화하여 `prepareStatementCount`를 기록했다. 측정 harness는 제거했다.

| 요청 | SQL statements | HTML bytes |
|---|---:|---:|
| 목록 | 23 | 54,415 |
| 정상 전체 상세 | 18 | 53,264 |
| selected 목록 전체 GET | 33 | 73,698 |
| 동일 selected URL의 frame GET | 33 | 73,698 |

전체 문서 응답을 사용하는 현재 PoC에서는 frame 선택 한 번이 정상 상세보다 **15 statements 더 수행**했다. 이 측정은 해당 fixture의 관찰값이며 운영 성능 보장이 아니다. 측정 후의 후속 최적화로 Thymeleaf fragment/`FragmentsRendering`을 검토할 근거는 생겼지만, 이번 구현에 별도 transport나 JSON 표현을 추가하지 않았다.

## 빌드·보안 범위

공식 `@hotwired/turbo` **8.0.23**과 lockfile을 사용한다. `npmCi → copyTurbo → processResources → bootJar`가 production assembly를 소유한다. output은 `build/generated/turbo/`, jar 내부 경로는 `static/javascripts/turbo/turbo.es2017-esm.js`다. Node 22+/npm 10+는 README에 명시한 빌드 전제이며 런타임 Node/CDN은 필요 없다. Spring MVC/Thymeleaf 공식 starter는 그대로다.

`Turbo.session.drive = false`, `Turbo.config.forms.mode = 'off'`로 전역 Drive/form 전환을 하지 않는다. 정상 폼/댓글 제출과 토큰 없는 POST의 **403**을 확인했다. Turbo 자체가 mutation 폼을 제출하는 경로의 CSRF 호환성은 아직 검증 범위가 아니며, 보호를 약화시키지 않았다.

## 재현

```sh
./gradlew bootRun --args='--spring.profiles.active=h2 --server.port=18080'
```

별도 터미널:

```sh
cd e2e
npm ci
npx playwright install chromium
YONA_BASE_URL=http://localhost:18080 npx playwright test turbo-two-column.spec.ts
```

넓은 기존 스위트를 함께 실행하려면 기존 signup seed를 먼저 만든다.

```sh
YONA_BASE_URL=http://localhost:18080 npx playwright test specs/01-auth/auth.spec.ts --grep 'every required field'
YONA_BASE_URL=http://localhost:18080 npx playwright test specs/04-project/00-project-create.spec.ts specs/04-project/project-home.spec.ts specs/04-project/project-members.spec.ts specs/06-issue specs/09-board/board-crud.spec.ts
```

네트워크 JSON과 screenshot은 Playwright report에 첨부된다. 테스트는 폐기 가능한 로컬 H2 데이터로 실행해야 한다.

## Draft PR body

**Title:** `PoC: replace issue two-column iframe navigation with Turbo Frames`

> Thymeleaf remains the canonical renderer; this is not a SPA, visual redesign, or site-wide Turbo rollout. Issue selection uses a public issue number in the list URL. Real controller/template responses now support direct URLs, reload and Turbo history without a custom HTML swap or History API implementation.
>
> Shared helpers preserve the existing per-issue draft keys, synchronize the visible editor, and clean up detail resources when the frame changes. Existing mutation forms stay outside Turbo. The issue-list legacy iframe entry point is retired, while unrelated consumers remain untouched.
>
> The scoped browser suite passes 45 tests. The full JVM suite retains one baseline attachment-response failure; this is not a full-green claim. Full-document frame rendering also incurs measured extra SQL, and authored JS increases slightly because safe widget lifecycle integration is explicit.
>
> This PoC supplies evidence for a later architecture discussion, not an approved project-wide frontend direction. It may be revised or closed after maintainer feedback.

## Copyable architecture evidence

```text
### PoC result
- target: issue-list two-column mode, next 1d04fe7
- branch/commit: poc/turbo-thymeleaf; helpers 46d734d4c, integration 37e4c593d, tests 7a7123409
- custom JS delta: navigation path 292 → 104 lines; net authored JS +203 including extracted inline code
- iframe removed from target path: yes; shared legacy file retained for other consumers
- manual history code: none added for selection; Turbo/browser owns selected navigation
- server/template changes: optional public selected number; shared normal detail authorization/model; reusable detail fragment
- request behavior: A→B is two Turbo-Frame requests, complete server HTML, no custom detail swap
- direct URL/reload: passed, including JS-disabled reads
- back/forward: passed with list/shell preservation and detail remount
- draft semantics: legacy issue-path keys retained; visible restore, isolation, fast-switch save, clear and post-submit removal passed
- E2E result: 45/45 scoped; additional cross-project consumer 2/2 including setup
- full regression result: 6,722/6,723, one baseline LegacyIssueResponseIntegrationSpec failure at line 142
- CSRF: existing forms/fetch preserved; tokenless POST 403; Turbo mutation forms not enabled
- performance: authenticated three-issue fixture ~99 KB per frame response; anonymous two-issue fixture 33 SQL statements vs normal detail 18
- limitations: no site-wide rollout, no whole-client-JS deletion claim, no production performance or pixel-parity claim
```

Compared with the current imperative-JS approach, this removes custom navigation ownership but not widget behavior. Unlike a full React SPA, it adds no JSON screen model or client domain store. It is consistent with server-rendered progressive enhancement, but no benchmark or code comparison of `yona-bun-temp`, Gitea or Forgejo was performed. The final architecture issue itself was not created.

References: [Turbo Frames](https://turbo.hotwired.dev/handbook/frames), [Turbo lifecycle and caching](https://turbo.hotwired.dev/handbook/building), [Spring MVC HTML fragments](https://docs.spring.io/spring-framework/reference/web/webmvc-view/mvc-fragments.html).

## 독립 PR — left sidebar Turbo Frame

- 기준: `upstream/next` `a71722f`; branch `feat/turbo-sidebar`.
- Markdown client renderer/editor 변경을 포함하지 않는다. 기존 서버 Markdown과 편집기를 그대로 사용한다.
- 범위는 sidebar 열기·닫기·내부 목록/새로고침이다. 프로젝트·이슈 **본문 링크는 일반 페이지 탐색**이며 사이트 전체 Turbo Drive나 SPA 전환이 아니다.

### 요청과 DOM 경계

`site/layout :: sidebarHost`가 인증된 페이지에 숨겨진 `turbo-frame#sidebar`를 둔다.
첫 열기에서만 `src=/user/sidebar`를 설정하고 `Turbo-Frame: sidebar` 요청으로 서버 HTML을 받는다.
닫기는 iframe 탈출이나 문서 reload 대신 frame을 숨기며, 재열기는 기존 내용을 사용한다.
새로고침 아이콘은 `frame.reload()`를 호출한다.

Sidebar는 본문을 재배치하지 않는 overlay다. 현재 form DOM, 입력값, URL/query/hash, 본문 너비를 유지한다.
이는 기존 페이지들의 `DOMContentLoaded` 초기화나 responsive viewport 가정을 바꾸지 않기 위한 범위 제한이다.
`shallWeOpenLeftNavigation`은 일반 페이지 이동/명시적 reload 후의 열림 선호로 유지한다.

`UserViewController.userSidebar`는 frame 요청에 `site/sidebar :: content`, 직접 요청에 standalone 문서를 반환한다.
공용 `common/usermenu_tab_content_list.html`에 `sidebar-` ID prefix를 적용해 오른쪽 `#mySidenav`와 충돌하지 않게 한다.
직접 방문한 sidebar는 JavaScript 없이도 프로젝트 링크를 읽고 이동할 수 있다.

### 수명주기와 UI

- 공식 기존 Turbo dependency만 사용하고 `Turbo.session.drive=false`, `Turbo.config.forms.mode='off'`를 유지한다.
- `yona.sidebar.Turbo.js`는 열기/닫기/refresh/오류 상태만 소유한다. `yona.Usermenu.js`는 DOM root별 검색·탭·펼치기·즐겨찾기·popover를 초기화한다.
- `turbo:before-frame-render`/`turbo:frame-load`로 준비·초기화하며 전역 `DOMContentLoaded`를 재발행하지 않는다.
- Favorite 이벤트는 중복 등록하지 않으며, 한 메뉴의 검색은 다른 메뉴나 본문 목록을 변경하지 않는다.
- 기존 파란색 오른쪽-edge 닫기 화살표를 유지한다. Header는 내부 overflow에서도 닫기 컨트롤이 보이도록 sticky 처리한다.
- 일반 본문 링크는 native anchor다. 수정 키/새 탭 동작을 유지하며 `mainFrame` 이름으로 탐색하지 않는다.
- 지연 응답은 이미 닫은 sidebar를 다시 열지 않는다. 실패/세션 만료는 현재 본문을 유지하고 Retry/Sign in을 제공한다.

이 cutover에서 `layout_framed.html`, navigation iframe, `path/hash → iframePath` 모델,
부모 window의 title/history 동기화와 iframe 전용 CSS를 제거했다.

### 검증 경로

```sh
./gradlew processResources bootJar test -Dyona.it.db=h2 \
  --tests 'com.github.yonaprojects.yona.web.UserViewControllerSpec' \
  --tests 'com.github.yonaprojects.yona.web.TemplateEquivalenceSpec'

cd e2e
YONA_BASE_URL=http://localhost:8080 npx playwright test \
  specs/04-project/00-project-create.spec.ts \
  specs/15-misc/sidebar-turbo.spec.ts \
  specs/15-misc/sidebar-menus.spec.ts
```

Browser 계약은 모바일/데스크탑 무탐색 toggle, 본문 input identity/값/너비 보존,
첫 로드 1회·재열기 추가 요청 0회, pending close, 오류/로그인 redirect, overflow dismiss,
명시적 reload 선호 복구, 두 메뉴의 검색/즐겨찾기 격리, native navigation, no-JS 링크다.

### 이 브랜치의 실행 결과

- `UserViewControllerSpec` 145개 + `TemplateEquivalenceSpec` 85개: 230개 통과.
- Chromium 11개, Firefox/WebKit 21개: 합계 32개 통과(각 브라우저의 기능 시나리오 10개씩과 setup 2회 포함).
- `processResources`/`bootJar` 통과. 별도 Markdown frontend dependency나 생성 bundle을 포함하지 않는다.
- 실제 LAN 화면에서 desktop/mobile 열기·닫기, 입력값/동일 input node/URL 유지, 오른쪽-edge 화살표와 원래 plain menu label을 확인했다.
- 이 결과는 sidebar 범위다. 기존 이슈 2단 보기 history/filter 회귀나 사이트 전체 Turbo navigation을 해결했다고 주장하지 않는다.
