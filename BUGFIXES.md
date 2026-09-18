# yona product bug fixes — TDD tracking

## FINAL STATUS: all 9 bugs fixed and verified — full suite 187/187 passed, 0 failed, 0 fixme

All 9 documented product bugs (#1-#9) are fixed, each individually TDD-verified (red confirmed
against the actual pre-fix code, green confirmed after the fix, spec file + 1-2 neighboring specs
re-run clean). Final verification: server stopped, a full wipe of `data/h2/`, `e2e/.auth/`, and
`e2e/.seed/` was done for a true from-bootstrap run (justified given 9 bugs' worth of accumulated
test data across many sessions), server restarted fresh, and `cd e2e && npm test` run end to end
from a brand-new H2 DB. Result: **187 passed, 0 failed, 0 fixme** (186 pre-existing + 1 new
regression test added for #9, `e2e/specs/15-misc/favorites.spec.ts`'s `a notify() toast from the
favorite-issue toggle does not block a later real click elsewhere on the page`). Two things
surfaced during this final clean run that were *not* part of the 9 documented bugs and are noted
for completeness (both fixed, neither is a yona product bug):
- The sandbox had no `hg`/`svn` CLI installed at all (`spawnSync hg/svn ENOENT`), so
  `specs/05-code/01-hg-svn-code-setup.spec.ts` and its dependents failed outright on the first
  clean-wipe run. Installed `mercurial`/`subversion` via `apt-get` — an environment/tooling gap,
  not a code bug.
- Once `svn` was installed, `specs/05-code/01-hg-svn-code-setup.spec.ts`'s svn setup test still
  silently corrupted its own seed data: this sandbox's system locale is `ko_KR.UTF-8`, the `svn`
  CLI localizes `svn commit`'s output under that locale, the test's regex expects the English
  `"Committed revision N."` string, the failed match produced `Number(undefined)` = `NaN`, and
  `JSON.stringify(NaN)` serializes as `null` — so `svnRevOld`/`svnRevNew` were written as `null`
  and `requireSeed()` correctly treated them as missing in two downstream `svn-diff.spec.ts`
  tests. Fixed by pinning `LC_ALL=C`/`LANG=C` on the `svn` subprocess's env in that spec (a test
  harness fix, not a yona product bug — `hg`'s own output needed no such pin since the template
  format used isn't localized).

9 real product bugs found by the e2e Playwright audit (`e2e/matrix.md`, `e2e/specs/**/*.spec.ts`
`test.fixme(...)` entries and inline comments). Per instruction: fix each with TDD (make the
existing `test.fixme` a real `test(...)` — or add a new assertion where none exists yet — confirm
it fails against the *current* code demonstrating the bug, fix the code, confirm it passes), then
verify with Playwright. Work through this list one item (or a small tightly-related group) at a
time, checking off as you go.

## TDD protocol per item

1. Find the relevant `test.fixme(...)` in `e2e/specs/**` (or, for items with no existing test —
   check first — write a new spec following the surrounding file's conventions). Read the
   fixme's own comment block first; several already contain a precise root-cause diagnosis from
   the original audit — treat that as a strong lead, not gospel, and confirm against the actual
   current source before writing a fix.
2. **Start the server** (H2 profile, if not already running): `cd /home/search5/cl/yona && ./gradlew bootRun --args='--spring.profiles.active=h2'` (background it; first boot can take a minute). Do NOT delete `data/h2/` or `e2e/.auth`/`e2e/.seed` unless you need a truly clean slate — reusing the existing admin/seed state is fine and faster (see `e2e/README.md`'s "완전히 새로 시작하려면" section for when a wipe is actually needed).
3. Flip `test.fixme(...)` to `test(...)` (or write the new assertion), run just that spec file
   (`cd e2e && npx playwright test specs/<path> --headed=false`) and **confirm it currently
   fails** for the documented reason — this is the "red" step; don't skip it, it's what proves
   you're fixing the right thing.
4. Fix the source (Kotlin controller/service/repository under `src/main/kotlin` or
   `src/main/java` as applicable, Thymeleaf templates under `src/main/resources/templates`,
   JS under `src/main/resources/static/javascripts`). Keep the fix minimal and scoped to the
   documented bug — don't refactor unrelated code.
5. Restart the server if you touched Kotlin (template/JS changes are picked up live, Kotlin
   changes need a rebuild+restart — `./gradlew bootRun` again, or check if devtools/hot-reload
   is configured). Re-run the spec — **confirm it now passes** (the "green" step).
6. Run the FULL spec file it lives in (not just the one test) to catch any regression to sibling
   tests in the same file, and spot-check 1-2 neighboring spec files if your fix touches shared
   code (e.g. a shared dialog component, a shared form-binding pattern).
7. Check off the item below with a one-line note (what was actually wrong vs. the original
   diagnosis, what you changed, any deviation). Update `e2e/matrix.md`'s own note for that row if
   it references the bug (change "제품 버그 발견(수정 안 함...)" wording once actually fixed).
8. Leave the server running for the next item unless you have a strong reason to restart it.

## Bugs

- [x] **#1 — 게시글 삭제 중복 DELETE 요청(레이스 컨디션)**: 원 진단 그대로 확인됨 —
      `board/view.html`의 삭제 버튼이 두 곳에서 이벤트를 바인딩하고 있었음: (1) 사이트
      전역 `yona.Common.js`가 `DOMContentLoaded`에서 모든 `[data-request-method]`
      엘리먼트에 자동으로 `requestAs()`를 호출해 클릭 리스너를 붙이는데(이 버튼도
      `data-request-method="delete"`라 대상에 포함됨), (2) `board/view.html` 자신의
      인라인 스크립트(수정 전 278-287행)가 같은 버튼에 별도의
      `addEventListener("click", ...)`로 독립된 `fetch(uri, {method:"DELETE"})`를 또
      붙이고 있었음. 클릭 한 번에 DELETE가 두 번 나가서 먼저 도착한 요청이 실제 삭제(200),
      나중 요청은 404를 받는데, `board/view.html` 자신의 핸들러가 `response.ok` 게이트로
      리다이렉트를 결정하다 보니 그 핸들러의 fetch가 경합에서 진 경우(늦게 도착 + 404) 목록
      이동이 아예 안 일어나 UI가 멈췄음(서버 쪽 삭제 자체는 항상 성공 — 진짜 이미 삭제된
      글의 URL을 다시 GET해도 정상 200(`error/notfound.html`, 이 앱은 컨텍스트 인지형
      404를 HTTP 200으로 내려줌)으로 확인됨). **수정**: `board/view.html`이 별도 리스너를
      붙이는 대신, 엘리먼트당 한 번만 리스너를 붙이는 idempotent한
      `$yona.requestAs(elBtnDeletePost)`를 호출(이미 위 자동배선 때 캐시된 동일 인스턴스를
      그대로 반환받으므로 새 리스너가 추가되지 않음)해 그 인스턴스의 `"load"`(성공) 이벤트
      핸들러 안에서만 `/posts`로 이동시키도록 배선을 단일화(`return false`로 requestAs의
      기본 `document.location.reload()` 폴백은 건너뜀). TDD: `test.fixme('delete a
      post', ...)`를 `test(...)`로 전환하고 DELETE 요청 횟수를 직접 세는 카운터 단언
      (`expect(deleteRequestCount).toBe(1)`)과 삭제된 글 URL 재조회 확인을 추가 — red에서
      해당 테스트가 원 진단과 동일한 이유(목록 페이지로 못 감)로 타임아웃 실패함을 확인,
      수정 후 5회 연속 재실행 모두 통과(경합 조건이라 반복 검증 필수 — 단발성 통과가 아님을
      확인). 테스트: `e2e/specs/09-board/board-crud.spec.ts`의 `test('delete a post', ...)`.
- [x] **#2 — 게시글 라벨 선택이 저장 안 됨**: 원 진단 그대로 확인됨(서버는 정상, 클라이언트
      배선만 없음) — `BoardController.kt`에 `PUT /api/projects/{projectId}/posts/{postId}/
      labels`(195-217행, `updatePostLabels`)가 이미 존재하고 `posting.labels =
      issueLabelRepository.findAllById(labelIds).toMutableSet()` 후 저장까지 정상
      동작하지만(직접 호출로 확인), `board/view.html`이 렌더링하는 라벨
      `<select id="labelIds" data-toggle="tomselect">`(이슈 뷰와 동일한
      `issue/partial_select_label.html` 프래그먼트 재사용)의 `change` 이벤트를 이
      라우트로 보내는 배선이 `yona.board.View.js`에 전혀 없었음(정적 JS 전수 grep으로
      확인된 원 진단과 일치). **수정**: `yona.issue.View.js`가 쓰는 것과 동일한 패턴 —
      `.issue-info` 컨테이너에서 `[data-toggle=tomselect]`의 `change`를 델리게이트해
      선택된 라벨 id 배열을 `PUT urls.labels`로 전송 — 를 `yona.board.View.js`에 추가하고,
      `board/view.html`의 `$yona.loadModule("board.View", ...)` 호출에 `urls.labels`
      (`@{/api/projects/{projectId}/posts/{number}/labels(...)}`)를 새로 넘기도록 수정.
      TDD: 기존에 "저장 안 되는 게 정상"이라고 단언하던 테스트를 "리로드 후에도 라벨
      선택이 유지된다"는 정방향 단언으로 전환 — red에서 PUT 요청 자체가 전송되지 않아
      `page.waitForResponse` 타임아웃으로 실패함을 확인, 수정 후 PUT이 실제로 전송되고
      리로드 후에도 `#labelIds`에 `selected` 상태가 유지됨을 확인(green). 테스트:
      `e2e/specs/09-board/board-crud.spec.ts`의 `test('assigning a label on a post
      persists across reload', ...)`.
- [x] **#3 — 이슈 일괄수정(massUpdate) 라벨 첨부 무효**: 원 진단(폼 바인딩/컨트롤러 로직
      버그)은 틀렸음 — 직접 curl로 `POST .../issues/massupdate`에 `attachingLabelIds=<id>`를
      쏴서 확인해보니 `issue.labels.addAll(...)` + `issueRepository.save(issue)`(현재
      `IssueViewController.kt` 862-872행)는 실제로 정상 동작하고 라벨이 DB에 제대로
      붙는다(재로드한 페이지의 `#labelIds` `<select>`에 `selected` 옵션으로 확인됨). 진짜 원인은
      **#6과 완전히 동일한 프론트엔드 버그**: 이슈 상세 페이지에서 `#milestone` select가
      `data-state` 속성이 없는 옵션(예: "마일스톤 없음", 또는 이 파일이 만드는 것처럼 특정
      milestoneState 부재)을 렌더링할 때 `yona.ui.TomSelect.js`의 milestone 렌더러가
      `return data.text` (원본 텍스트, HTML 아님)를 반환 → Tom Select 라이브러리의
      `getDom()`(K 함수, `tom-select.complete.min.js`)이 "<" 없는 문자열을 CSS 셀렉터로 오인해
      `document.querySelector(text)` 호출 → 제목에 셀렉터로 파싱 불가능한 문자(괄호 등)가
      있으면 SyntaxError로 바로 크래시, 아니면(예: "No Milestone"처럼 평범한 문자열) 매치가
      없어 null 반환 → 그 다음 줄의 `setAttribute` 호출이 null 참조로 크래시("Cannot read
      properties of null (reading 'setAttribute')"). 이 크래시가
      `document.querySelectorAll('[data-toggle="tomselect"]').forEach(...)` 자동초기화 루프
      **도중** 터지면서(DOM 순서상 `#milestone`이 `#labelIds`보다 먼저 나옴) forEach가
      중단돼 `#labelIds`의 Tom Select 인스턴스 자체가 초기화되지 않고, 그 결과 라벨은 실제로
      DB에 붙었는데도 화면에 `.issue-label[data-label-id]` 뱃지(Tom Select가 렌더링하는 선택
      아이템)가 전혀 나타나지 않아 "라벨이 안 붙었다"처럼 보였던 것. **수정은 #6과 동일한 한
      군데** — `yona.ui.TomSelect.js`의 milestone 렌더러가 이제 `data.state` 유무와 무관하게
      항상 `<div>...</div>` HTML 마크업을 반환하도록 고쳐 K()가 절대 querySelector 경로를 타지
      않게 함(아래 #6 항목 참고). Kotlin 컨트롤러/서비스 코드는 변경 없음. TDD: red에서
      `.issue-label[data-label-id]` 뱃지를 못 찾아 타임아웃(`toBeVisible` 5s 실패, 원 진단과
      다른 실패 지점), 수정 후 라벨 뱃지가 정상 렌더링됨을 확인(green). 테스트:
      `e2e/specs/06-issue/issue-management.spec.ts:73`(`test('attach a label via the issue
      list mass-update widget', ...)`로 전환).
- [x] **#4 — 이슈 상태 배지 i18n 키 깨짐**: 진단 그대로 확인됨(`issue.state.` + 대문자 enum,
      messages*.properties엔 소문자 키만 존재). 28/38행 `#{'issue.state.' + issue.state}`를
      293행과 동일하게 `#{'issue.state.' + #strings.toLowerCase(issue.state)}`로 수정. 텍스트
      쪽만 깨져 있었다는 원 진단대로 CSS class 보간은 이미 정상이라 손대지 않음. TDD: red에서
      실제로 `??issue.state.CLOSED_en_US??`가 렌더링되는 걸 Playwright로 확인(정확히 원 진단과
      일치), 수정 후 배지 텍스트가 "Closed"/"Open"으로 정상 렌더링됨을 확인(green). 테스트:
      `e2e/specs/06-issue/issue-management.spec.ts`의 `toggle issue state closed then open via
      the mass-update widget`에 `toHaveText` 단언 추가.
- [x] **#5 — 추천취소 버튼이 항상 `/vote`를 가리킴**: 진단 그대로 확인됨 — 백엔드는
      `VoteController.kt`에 `/vote`(42행)와 `/unvote`(67행) 별도 라우트가 있는 진짜 별개
      엔드포인트(토글 아님; `voteIssue()`는 이미 투표한 상태에서 다시 호출하면
      `IllegalStateException`도 던짐), 템플릿만 `hasVoted`와 무관하게 `th:href`가 `/vote`로
      고정돼 있었음. 109-112행의 `th:href`를 Thymeleaf 전처리 표현식으로
      `.../issue/{issueNumber}/__${hasVoted ? 'unvote' : 'vote'}__`로 바꿔 `hasVoted`에 따라
      실제 라우트가 갈리도록 수정. TDD: red에서 투표 후 href가 여전히 `/vote`로 남아있음을
      확인(원 진단과 일치), 수정 후 실제 UI 클릭만으로 투표→추천취소 왕복이 되고 href/class가
      올바르게 전환됨을 확인(green) — 기존 테스트의 "직접 `/unvote` API 호출로 정리"하던
      우회 코드는 제거하고 실제 UI 클릭 기반 취소 흐름으로 교체.
- [x] **#6 — 특정 마일스톤 제목에서 `querySelector` 크래시 → 댓글수정 버튼 먹통**: 원 진단의
      증상 설명(괄호 있는 마일스톤 제목에서 `querySelector` 크래시 → 같은 인라인 스크립트의
      나머지 핸들러 등록 불발)은 정확했지만, 원인 후보로 지목한 `_toElement()`/
      `yona.issue.Assginee.js`/`yona.issue.Sharer.js`는 전부 틀렸음(모두 실제 DOM
      엘리먼트/jQuery 객체를 넘기지 문자열 셀렉터를 넘기지 않아 안전함). **실제 호출부**:
      `yona.ui.TomSelect.js`의 `renderers.milestone`(126-140행 부근) — `data.state`가
      falsy일 때 `return data.text;`로 가공 없는 원본 텍스트를 그대로 반환하고 있었음(HTML로
      감싸지 않음). Tom Select 라이브러리(`tom-select.complete.min.js`)의 내부 `getDom()`
      헬퍼(K 함수)는 render 결과 문자열에 "<"가 없으면 CSS 셀렉터로 간주해
      `document.querySelector(그_문자열)`을 직접 호출한다 — 괄호처럼 셀렉터로 파싱 불가능한
      문자가 있으면 바로 `SyntaxError`. 게다가 `issue/view.html`의 `#milestone` select는
      `<option>`에 애초에 `data-state` 속성 자체를 렌더링하지 않아, 이 buggy 분기가 사실상
      **모든** 마일스톤 옵션에서 항상 타는 경로였다(제목에 괄호가 없어도 매치 없는 셀렉터가
      `null`을 반환해 그 다음 줄 `setAttribute` 호출이 null 참조로 크래시하는 형태로 여전히
      깨짐 — #3 항목 참고, 완전히 동일한 근본 원인). `document.querySelectorAll('[data-toggle=
      "tomselect"]').forEach(...)` 자동초기화 루프 도중 이 크래시가 나면 forEach가 중단돼 뒤에
      순회 예정이던 다른 tomselect 인스턴스도 초기화가 안 됐고, 이슈 상세 페이지의 같은 인라인
      `<script>` 블록(`$yona.loadModule("issue.View", ...)` 이후 `[data-toggle="comment-edit"]`
      델리게이트 등록까지 포함)도 이 예외로 중간에 멈춰 댓글수정 버튼이 먹통이 됐음. **수정**:
      milestone 렌더러가 `data.state` 유무와 무관하게 항상 `<div>...</div>` HTML 마크업을
      반환하도록 변경(`src/main/resources/static/javascripts/common/yona.ui.TomSelect.js`) —
      K()가 "<" 포함 문자열을 보고 항상 `<template>` 파싱 경로를 타게 만들어 querySelector
      경로 자체를 없앰. 정적 JS라 서버 재시작 불필요(단, 이번 세션은 `bootRun`이 Gradle
      `build/resources/main`에서 서빙 중이라 소스 수정 후 그 디렉터리의 사본도 함께 갱신해야
      런타임에 반영됨 — 다음 `bootRun` 재시작 시엔 `processResources`가 알아서 동기화함).
      TDD: 별도 standalone Playwright repro 스크립트로 먼저
      `Failed to execute 'querySelector' on 'Document': "..." is not a valid selector`
      pageerror를 실측(red), 수정 후 동일 시나리오에서 pageerror 없음을 확인(green). 스펙
      테스트(`e2e/specs/06-issue/issue-management.spec.ts:280`, `test.fixme('post a comment,
      edit it, then delete it', ...)` → `test(...)`로 전환)에는 (1) 이 파일 전용 마일스톤을
      `10-milestone/milestone-crud.spec.ts`의 편집 흐름과 동일하게 제목에 괄호를 포함하도록
      리네임하는 단계와, (2) `page.on('pageerror', ...)` 리스너로 이슈 상세 페이지 로드 시
      에러가 없음을 직접 단언하는 코드를 추가(원래는 타임아웃으로만 간접 실패해 진단이
      모호했음). 테스트 작성 중 추가로 발견한 **별개의 테스트 버그**(제품 버그 아님): 댓글
      작성 성공 시 `issue/view.html`의 인라인 스크립트가 `window.location.reload()`를 즉시
      호출하는데, 그 직후 Playwright의 캡처된 POST 응답 객체에서 `.json()`/`.text()`를 읽으려
      하면 CDP 바디 버퍼링과 페이지 리로드가 경합해 이 환경에서는 영원히 끝나지 않는 hang이
      됨(별도 standalone repro로 60초 넘게 확인) — 댓글 id를 응답 바디에서 파싱하는 대신,
      이 테스트는 자기 전용 이슈를 쓰므로 항상 유일한 댓글이라는 점을 이용해
      `page.locator('[data-toggle="comment-edit"]').last()`로 단순화(기존 코드에 이미 있던
      폴백 경로). 추가로 마일스톤 리네임 왕복 때문에 기본 30s 테스트 타임아웃이 빠듯해져
      `test.setTimeout(60_000)`을 추가.
- [x] **#7 — 게스트모드 토글이 목록 필터와 분리**: 원 진단 그대로 확인됨 —
      `SiteService.toggleGuestMode()`(수정 전)가 `isGuest` 불리언만 뒤집고 `state` enum은
      안 건드리는데, "게스트 사용자" 탭 목록 쿼리(`UserRepository.findUsersForAdminQuery`/
      `countUsersForAdmin`, 둘 다 네이티브 SQL `WHERE state = :state`)는 `state` 컬럼만
      필터링 — 토글해도 GUEST/ACTIVE 탭 어디서도 반영 안 됨. `UserState` enum엔 `GUEST` 값이
      이미 존재하지만(`UserState.kt`), 코드베이스 전체에서 `state = UserState.GUEST`로 실제로
      설정하는 곳이 한 군데도 없어 사실상 죽은 값이었음(반면 `isGuest`는 `RepoAccessPolicy`/
      `AccessControl`/`WikiViewController`/`ImportApiController` 등 실제 게스트 권한 판단
      전체가 직접 읽는 진짜 동작 플래그). 형제 메소드인 `toggleAccountLock()`/
      `toggleSiteAdminRole()`이 둘 다 정확히 이 패턴(자기 전용 `state` 값과 `ACTIVE` 사이를
      토글 + `lastStateModifiedDate` 갱신)을 쓰고 있어, 목록 쿼리가 `state` 하나만 보는 게
      진짜 의도임을 확인 — **수정**: `SiteService.toggleGuestMode()`가 이제 `state`를
      ACTIVE↔GUEST로 함께 뒤집도록 변경(형제 메소드와 동일 패턴), `isGuest`는 실제 권한
      로직이 깨지지 않도록 `state == GUEST`와 계속 동기화 유지. Kotlin 변경이라 재시작 필요.
      TDD: `e2e/specs/13-admin/site-admin.spec.ts`의 게스트모드 토글 테스트가 기존엔 버튼
      라벨 재렌더까지만 확인했는데, red에서 실제 탭 재조회 단언(ACTIVE 탭에서 사라짐 +
      GUEST 탭에 나타남, 원복까지)을 추가해 문서화된 이유(탭 소속이 안 바뀜)로 정확히
      실패함을 확인, 수정 후 통과(green).
- [x] **#8 — 관리자의 유저 비밀번호 강제초기화 버튼 항상 404**: 원 진단(URL 불일치)은
      맞았지만 불완전했음 — **두 번째, 원 진단에 없던 버그**를 추가로 발견: `data-href`를
      실제 라우트(`POST /site/users/{loginId}/reset-password`, `SiteApiController.kt`의
      `resetUserPasswordBySiteManager`)로 고친 뒤에도 버튼이 여전히 아무 요청도 보내지
      않았음 — standalone Playwright repro로 `pageerror: $ is not defined`를 실측. 원인:
      `userList.html`의 인라인 스크립트가 대기/성공 알림을 그릴 때 jQuery의
      `$.tmpl(...).appendTo(...)`를 호출하는데, 저장소 전체가 fetch 기반으로 전환되며
      jQuery 코어 자체가 이미 제거되어(`site/layout.html`의 scripts fragment 주석 참고)
      `$`가 미정의 상태 — 클릭 시 `fetch()` 호출 전에 ReferenceError로 죽어 URL을 고쳐도
      요청 자체가 나가지 않았음(기존 인라인 주석은 "$.tmpl은 jquery.tmpl.js 플러그인 호출이라
      의도적으로 유지"라고 잘못 설명하고 있었음). **수정**: (1) 버튼 `data-href`를
      `@{/sites/users/{loginId}/reset-password(loginId=${user.loginId})}`로 교체, (2) 두
      `$.tmpl(...).appendTo(...)` 호출을 `yona.Attachments.js._getFileItem()`과 동일한
      패턴 — `$yona.tmpl(...)`(vanilla, `${var}` 치환만 하는 문자열 반환 헬퍼)로 HTML 문자열을
      만들고 `insertAdjacentHTML("beforeend", ...)`로 삽입 — 으로 교체. 템플릿 전용 변경이라
      캐시 때문에 재시작 필요(라이브 복사 불충분, 이전 배치에서 확인된 패턴). TDD:
      `e2e/specs/13-admin/site-admin.spec.ts:242`의 `test.fixme('force-reset the target
      account password', ...)`를 `test(...)`로 전환하고 실제 로그인 성공까지 확인하는
      단언으로 강화(응답 JSON에서 `newPassword` 추출 → 구 비밀번호 로그인 실패 확인 → 신규
      비밀번호 로그인 성공 확인) — red에서 URL 불일치 때문에 `/reset-password`로 가는 POST
      요청 자체가 안 잡혀 `waitForResponse` 타임아웃으로 실패함을 확인(원 진단과 일치하는
      이유), URL만 고친 중간 단계에서도 `$ is not defined`로 여전히 타임아웃 실패함을 별도
      repro 스크립트로 확인(원 진단에 없던 두 번째 원인), 두 수정 모두 적용 후 green.
- [x] **#9 — 알림 토스트(`yona-dialog`) 표시 후 영구 클릭차단 오버레이**: 원 진단("`$yona.notify()`
      토스트가 내부적으로 `yona-dialog`를 건드린다")은 **틀렸음** — 라이브로 직접 확인: 단독
      `$yona.notify()` 호출만으로는(`window.$yona.notify(...)`를 직접 evaluate) 다이얼로그가
      전혀 열리지 않고 이후 클릭도 전부 정상 동작함(`notify()`는 `yona.ui.Toast`만 건드리고
      `#yonaDialog`는 전혀 참조하지 않음 — 소스 확인). **실제 원인**: `yona.Usermenu.js`의
      `afterUsermenuLoaded()`가 페이지 로드마다 **두 번** 호출됨(1) 즉시(GNB usermenu AJAX
      파셜이 아직 안 실린 시점 - 페이지 자체에 서버렌더된 엘리먼트를 배선하기 위함), (2)
      `UsermenuUrl` fetch 완료 후(그 AJAX 파셜이 막 삽입된 시점 - 파셜 안의 엘리먼트를
      배선하기 위함). `.favorite-issue`(이슈 상세 페이지 자신의 star 아이콘)는 **이슈 상세
      페이지 자체에도 서버렌더**되고 **동시에 사이드바 AJAX 파셜(`my_partial_list_
      quicksearch.html`)에도** 존재해 두 호출 모두에서 매치되고, 매번
      `document.querySelectorAll(".favorite-issue").forEach(el => el.addEventListener(...))`로
      새 리스너가 추가돼(#1과 동일한 "중복 이벤트 바인딩" 패턴) 별 클릭 한 번에
      `POST /-_-api/v1/favoriteIssues/{id}`가 **두 번** 나감. 라이브 확인: 첫 요청은
      성공(`favored:true` insert), 거의 동시에 도착한 두 번째 요청은 같은 토글을 다시
      시도하다 `FAVORITE_ISSUE` 테이블의 unique index 위반으로 HTTP 500을 받음(H2 로그로
      직접 확인) — 그 catch 핸들러(`yona.Usermenu.js`의
      `$yona.alert("Update failed: " + JSON.parse(data.responseText).reason)`)가 공유
      `<yona-dialog id="yonaDialog">`를 실제로 `showModal()`로 엶. 네이티브 `<dialog>`가
      `showModal()`로 열리면 브라우저 top layer로 승격되고 뷰포트 전체를 덮는 `::backdrop`
      의사 엘리먼트가 생기는데(Shadow DOM 경계와 무관하게 항상 전체 뷰포트 기준), 이게
      이후 모든 클릭을 삼킴 — 정작 이 다이얼로그 자신의(커스텀 엘리먼트) host 엘리먼트
      `getBoundingClientRect()`는 0-width/화면 밖(문서 흐름상 떠밀린 위치)으로 나와서 디버깅
      시 "다이얼로그가 없는데 왜 막히지?"처럼 보였던 것(=원 진단이 가리킨 증상은 정확했지만
      원인 지목은 틀림). 아무것도 이 다이얼로그를 자동으로 닫아주지 않아 영구히 막힘.
      **수정**: `yona.Usermenu.js`에 `_bindOnce(el, sType, fHandler)` 헬퍼를 추가하고
      `afterUsermenuLoaded()` 안의 모든 `addEventListener` 호출(`.right-menu`,
      `.myOrganizationList`, `.search-input`의 keyup/keydown, `.star-project`,
      `.favorite-issue`, `.user-li`, `.star-org`, `.all-orgs`)을 이 헬퍼로 교체 —
      `el.dataset.usermenuBound_<type>` 플래그로 엘리먼트당 이벤트 타입별 리스너가 한 번만
      붙도록 idempotent하게 만듦(#1의 idempotent 배선 패턴과 동일한 접근). 정적 JS라 서버
      재시작은 필수는 아니지만(#6 참고) 일관성을 위해 재시작함. **TDD**: red에서
      standalone Playwright 리프로로 실제 dup POST(200 성공 + 500 unique violation) 및
      Playwright 자체의 액션어빌리티 트레이스(`<yona-dialog id="yonaDialog"> intercepts
      pointer events`)로 정확한 실패 지점을 실측 확인(원 진단의 "클릭이 안 먹는다" 증상과
      일치하되, 원인은 다름), 수정 후 5회 연속 재실행 모두 통과(경합 조건 성격이라 반복
      검증). 테스트: `e2e/specs/15-misc/favorites.spec.ts`의 `starring and unstarring an
      issue from the issue view page persists`(toggle-off를 `el.click()` 우회 대신 실제
      `locator.click()`으로 전환)와 새로 추가한 `a notify() toast from the favorite-issue
      toggle does not block a later real click elsewhere on the page`(토글 직후 완전히
      무관한 엘리먼트 `#sidebar-open-btn`에 대한 실제 클릭이 여전히 먹는지 직접 단언).
      **우회 코드 정리**: 전체 `e2e/specs/**`를 `el.click()`/`evaluate(...click())` 패턴으로
      grep한 결과 단 두 곳 —
      (1) `e2e/specs/15-misc/favorites.spec.ts:121`(위 테스트): #9가 원인이었음, 수정 후
      실제 `locator.click()`으로 교체 완료.
      (2) `e2e/specs/03-organization/organization-crud.spec.ts:76`: **#9와 무관한 별개
      이슈** — 라이브로 직접 확인(`getComputedStyle`): 이 페이지의 역할 변경 드롭다운은
      `.dropdown-menu`가 실제 Bootstrap 드롭다운 토글 없이 항상 `display:none`으로 남아있어
      `.role-apply-btn` 옵션이 (다이얼로그/오버레이와 무관하게) 애초에 클릭 가능한 상태가
      아님 — 수정 후에도 실제 `locator.click()`은 여전히 타임아웃되는 것을 확인. 이건 기존
      주석에도 이미 정확히 문서화돼 있던 별도의 테스트 인프라 이슈라 그대로 둠(제품 버그
      여부는 이번 배치 범위 밖).

## Verification checkpoints

- After every 2-3 items, run the full suite once (`cd e2e && npm test`) to catch cross-item
  regressions early rather than only at the very end.
- Before declaring all 9 done: one final full clean run — stop the server, optionally wipe
  `data/h2/`, `e2e/.auth`, `e2e/.seed` for a truly fresh bootstrap-through-admin-creation pass
  (see `e2e/README.md`), restart, run `npm test` end to end, report the final pass count.
