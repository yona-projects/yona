---
title: 새 셸 사이드바의 구현
type: architecture
sources:
  - ../src/main/kotlin/dev/yona/web/shell/ShellNav.kt
  - ../src/main/kotlin/dev/yona/web/shell/ShellNavs.kt
  - ../src/main/resources/templates/shell/sidebar.html
  - ../src/main/resources/templates/shell/home-sidebar.html
  - ../app/assets/stylesheets/scss/_shell.scss
  - ../src/main/frontend/ts/shell/sidebar.ts
  - ui-ia-v2.md
  - ui-removed-elements.md
updated: 2026-11-11
---

# 새 셸 사이드바의 구현

설계는 `ui-ia-v2.md`(D-138~D-141), 없앤 요소와 이유는 `ui-removed-elements.md`, TypeScript 빌드는 `ui-ts-build.md`에 있다. 이 문서는 **코드가 어떻게 짜여 있는지**를 적는다.

## 한눈에
```
템플릿(화면)                    서버                         브라우저
  <div class="… shell">   ShellNavs (@Component "shellNavs")   Bootstrap offcanvas-lg
    sidebar 조각  ───────▶  ShellNav (순수 함수, DB 없음)  ──▶  992px 이상 고정, 미만 서랍
    <div class="shell-main">   NavMenu(items, back)             shell/sidebar.ts(숨기기, 기억)
      본문(옛 마크업 그대로)
```

## 서버: 항목을 한 곳에서 판정한다
- `ShellNav`(object): 맥락마다 `NavMenu`를 만드는 순수 함수이다. `project`, `projectSettings`, `home`, `organization`, `organizationSettings`, `userSettings`, `siteAdmin`. 항목의 보이는 조건(메뉴 설정, 코드 접근, 저장소 종류 능력, 권한, 사용자 앱 사용 설정)은 모두 여기에 있다. 서버의 접근 검사(`ProjectCheckStage.checkMenuEnabled`)와 같은 메뉴 설정 값을 쓰므로 항목이 보이는데 주소가 404인 어긋남이 없다.
- `NavItem`: `key`(활성 판정과 시험이 쓰는 이름), `labelKey`(**기존 v1.6 메시지 키**, 새 글자를 만들지 않는다), `path`(컨텍스트 경로가 붙기 전), `shortcut`, `badge`, `exact`.
- `NavMenu.activeKey(requestPath, contextPath)`: 요청 경로에서 가장 길게 맞는 항목을 활성으로 판정한다(하위 경로에서도 켜진다). 질의, 해시, 끝 빗금을 무시하고 퍼센트 인코딩과 대소문자를 구분하지 않는다. `exact` 항목(프로젝트 홈, 조직 홈, `/`, 내 설정 프로필)은 경로가 같을 때만 활성이다.
- `ShellNav.projectActiveKey(menuType)`: 프로젝트 화면 템플릿이 넘기는 메뉴 종류(`ISSUE` 등)를 항목 이름으로 바꾼다. 코드 주고받기 상세 주소(`pullRequest/12`)가 목록 주소(`pullRequests`)의 하위 경로가 아니라서 프로젝트 메인 사이드바만 이 방식을 쓴다.
- `NavMenu.shortcuts()`: 보이는 항목의 단축키(`H C I P M B Q`)와 경로이다. 프로젝트 단축키 스크립트가 이 목록으로 `htKeyMap`을 만들어 메뉴를 끄면 키도 사라진다.
- `ShellNavs`: 템플릿이 부르는 창구이다. 화면 문맥(`ProjectViewContext`)에서 입력을 만들거나 현재 사용자, 사이트 설정을 읽는다. 호출은 `th:with="shellMenu=${@shellNavs.xxx(...)}"`로 한다(**Thymeleaf 3.1은 조각 호출 인자 안의 빈 참조를 막는다**).
- Kotlin 기본 인자는 SpEL에서 보이지 않으므로 템플릿에서 부르는 `activeKey`에는 `@JvmOverloads`를 붙였다.

## 템플릿: 래퍼 패턴
1. 화면의 본문 래퍼(`.page-wrap` 또는 `.project-page-wrap`)에 `shell` 클래스를 더한다(조직과 프로젝트는 `shell-below-banner`도, 배너 로고가 튀어나온 만큼의 위쪽 여백이다).
2. 래퍼 맨 앞에 사이드바 조각을 넣고, 나머지 본문을 `<div class="shell-main">`으로 감싼다. **본문을 격자의 자식으로 풀면 옛 float 배치가 깨진다**(내 설정 프로필 화면에서 겪었다). 그래서 격자 방식은 버리고 진짜 래퍼를 쓴다.
3. 조각 종류
   - `shell/sidebar :: sidebar(menu, title, id, active)`: 공통 사이드바. `active`가 `null`이면 요청 경로에서 판정한다.
   - `shell/home-sidebar :: sidebar(active)`: 프로젝트 밖.
   - `project/menu :: sidebar(view, menuType)`와 `settingsSidebar(view)`: 프로젝트. 단축키 스크립트(`shortcuts`)도 여기서 같이 그린다.
   - `organization/menu :: sidebar(view, kind)`: 조직(`main` 또는 `settings`).
   - `user/partial-edit-tabmenu :: tabmenu(tabId)`: 내 설정(`tabId`가 활성 키와 같다).
   - 사이트 관리는 `layout/site-mng-layout`이 감싼다.
   - 검색 결과는 `search/partial-search :: search(sidebar)`가 선택 인자를 받는다(전역 검색은 `null`).
4. 변환은 div 짝을 직접 세는 스크립트로 했고 변환 뒤 div 수 균형을 확인했다. 새 화면을 더할 때는 이 패턴을 그대로 쓴다.

## 스타일: SCSS 번들
`app/assets/stylesheets/scss/_shell.scss`를 `yobi.scss`가 가져와 `yobi.css`에 번들링한다(새 CSS 파일을 따로 두지 않는다). 항목 모양은 **옛 사이트 관리 사이드바**와 같다: 왼쪽 4px 회색 막대, 활성은 주황 막대와 굵은 글자, 호버는 회색 배경, 배지는 요나 주황 알약. 폭은 200px, 간격 14px, 구간은 Bootstrap 기본 `lg`(992px)이다. 숨기기 단추와 보이기 단추는 같은 파일 끝에 있다.

## 브라우저: 숨기기
`shell/sidebar.ts`(`ui-ts-build.md`). 사이드바 조각이 import map 이름(`yona/shell/sidebar.js`)으로 불러 컨텍스트 경로에 안전하다.

## 브라우저: 사이드바 이동(htmx, D-136)
`shell/navigation.ts`. 사이드바 조각(`nav.shell-sidebar`)은 `hx-target=".shell-main"`, `hx-select=".shell-main"`, `hx-select-oob="#shellSidebar, #pageTitle"`, `hx-swap="outerHTML show:window:top"`를 선언한다. 서버는 조각 응답을 따로 만들지 않는다(전체 문서를 그대로 내리고 htmx가 필요한 부분만 고른다). 그래서 `Vary: HX-Request`도 필요 없다.
- **옵트인**: 본문 안에 `<span data-swap-safe hidden></span>`이 있는 화면만 swap-safe이다. 지금 화면에 표지가 있을 때만 모듈이 사이드바에 `hx-boost`를 켠다. 문서 수준 핸들러나 타이머를 쌓는 옛 스크립트가 있는 화면에는 표지를 달지 않는다(일반 이동).
- **응답 쪽 물러남**: 응답에 표지(또는 목록 표지 `list-container`)가 없으면 `yona.Htmx.js`가 일반 이동으로 같은 주소를 다시 연다(요청이 한 번 더 나가지만 안전하다). 번들(`yona-layout.js`)을 다시 만들어 커밋했다.
- **교체되는 것**: 본문, 사이드바(활성 항목 갱신), 제목 띠(`id="pageTitle"`, 내 설정 20개 화면). `hx-select-oob`는 같은 `id`의 요소끼리 맞바꾼다. 문서 제목은 `hx-select`가 응답의 `<title>`을 쓰지 않아 모듈이 직접 옮긴다.
- **지금 표지가 있는 화면**: 사이트 관리 7개(`audit`, `diagnostic`, `lfs`, `mail`, `security`, `sso`, `update`), 내 설정 8개(`edit-audit`, `edit-gpg-new`, `edit-ssh-new`, `edit-token-new`, `edit-two-factor-codes`, `edit-two-factor-setup`, `edit-oauth-app-form`, `edit-oauth-app-secret`). 판정 기준은 템플릿에 `<script>`, `th:inline`, `loadModule`, `onsubmit`, `onclick`이 없는 것이다(예비 판정이며 화면 시험으로 확정한다).
- 조직 설정 5개(`audit`, `oauth-apps`, `oauth-app-form`, `oauth-app-secret`, `oauth-policy`)와 프로젝트 4개(`watchers`, `rulesets`, `lfs-hg`, `deploykey-new`)도 같은 기준으로 표지를 달았다(2026-11-11). 홈, 이슈, 위키처럼 스크립트가 많은 화면은 일반 이동이다.
- **`loadModule`을 쓰는 화면**(프로젝트 `members`, `changeVCS`, `deleteform`, `transfer`, 조직 `members`, `settingform`)도 옵트인했다. 조건은 둘이다. (1) 초기화 스크립트가 본문 밖에 있으면 `hx-select`에서 빠지므로 `.shell-main` 안으로 옮긴다. (2) 본문을 교체하면 `<script type="module" src>`가 늦게 실행되어 `loadModule`이 먼저 돌고 옛 적재 경로(`service/yona.*.js`, 404)로 물러나므로, 초기화 스크립트를 `type="module"`로 바꾸고 첫 줄에서 `await import(모듈)`을 한 뒤 초기화한다. 직접 열 때와 교체할 때 모두 오류와 404가 없는 것을 브라우저에서 확인했다. 이 모듈들에는 `document`나 `window`에 거는 처리기가 없다(`user.Setting`, `user.View`는 있어 제외).
- 내 설정의 `user.Setting` 모듈 화면 4개(`password`, `emails`, `notifications`, `token`)도 같은 방식으로 옵트인했다. 이 모듈의 처리기는 요소에 걸려 있어(`document`, `window` 전역 없음) 교체해도 쌓이지 않는다. 프로필(`edit`)은 `cropperjs`를 쓰므로 일반 이동으로 둔다. `user.View`(프로필 보기)는 사이드바 화면이 아니라 해당하지 않는다.
- 확인창(`onsubmit`)만 쓰는 내 설정 목록 6개(`ssh`, `gpg`, `oauth-apps`, `linked-accounts`, `token-edit`, `two-factor`)도 옵트인했다. 인라인 속성 처리기는 요소에 붙어 있어 교체로 사라지고 새로 생긴다. `two-factor`의 보조 스크립트는 본문 안으로 옮겼다. 내 설정 22개 중 `edit`(프로필, cropper)와 `two-factor-key`(webauthn 모듈)만 일반 이동이다.
- 사이트 관리의 데이터 화면 6개(`userList`, `projectList`, `postList`, `issueList`, `data`, `oauth-apps`)도 옵트인했다. 스크립트가 조각 안에 있어 htmx가 교체 때 다시 실행하고, 처리기는 목록 래퍼와 요소에 걸려 있다. 이동 뒤에도 삭제 모달이 열리는 것을 확인했다. 대량 메일(`massmail`)은 모듈이 `document.body`에 폼을 덧붙여 일반 이동으로 둔다.
- 프로젝트 설정의 `deploykeys`, `lfs`, `webhooks`도 옵트인했다(웹훅은 초기화 스크립트를 본문 안으로 옮김). 프로젝트 설정 본 화면(`setting`)은 select2 때문에 미뤘다가 A-476 뒤에 켰다(브라우저 확인: 웹훅에서 이동, 오류 없음, select2 상자 1개). 프로젝트 `members`, `changeVCS`, `deleteform`, `transfer`, `watchers`, `rulesets`, `deploykey-new`, `lfs-hg`를 합쳐 프로젝트 설정 사이드바 10개가 모두 같은 문서 안에서 바뀐다(`setting`은 시험 환경에 저장소가 필요해 화면 시험은 브라우저로 확인).
- 조직 홈(`organization.View`), 조직 풀 리퀘스트 목록(스크립트가 이미 본문 안), 프로젝트 이슈 라벨(`issue.LabelEditor`)도 옵트인했다. 두 모듈 모두 `document`, `window`에 거는 처리기가 없다. 라벨 편집기는 직접 열 때와 교체 뒤가 같게 동작하는 것을 브라우저에서 확인했다(이름 입력에 초점을 주면 색 단추가 나타나고, 색 단추를 누르면 같은 색 값이 채워진다).
- **화면 수명주기(`core/page.ts`, A-475)**: 전역(`document`, 단축키, 타이머)에 무언가를 거는 화면은 `onPageLeave(정리 함수)`를 등록하고, 셸이 본문을 바꾸기 직전(`htmx:beforeSwap`, 실제로 바꿀 때만)에 `runPageLeave()`로 한 번씩 부른다. 일반 이동에서는 부르지 않는다(문서가 새로 열린다). 첫 소비처는 마일스톤 목록(`N` 단축키를 떠날 때 푼다). select2 때문에 막혔던 목록 화면은 A-476으로 풀었다(라이브러리를 레이아웃 head에서 한 번만 싣는다). 게시판 목록을 처음 켰고(스크립트를 본문 안으로, `N` 단축키 정리 등록), 마일스톤과 게시판 사이를 오가며 오류 없이, 단축키가 남지 않는 것을 브라우저에서 확인했다.
- 이슈 목록(프로젝트, 조직)과 조직 게시판 목록도 켰다(A-476, A-477). 목록 화면은 `HX-Request`에 조각을 돌려주므로 서버가 `HX-Boosted`가 함께 있으면 전체 화면을 돌려주게 했다. 브라우저에서 마일스톤, 게시판, 이슈 사이를 오가며 확인한 것: 오류와 404 없음, `N` 단축키가 현재 화면의 것이고, 문서의 `htmx:afterSwap` 처리기는 왕복 뒤에도 하나뿐이다.
- 코드 주고받기 목록과 위키 화면 4개(보기, 이력, 비교, 빈 위키)도 켰다. 본문 교체 뒤 `navigation.ts`가 하는 일: (1) 마크다운 영역에 이미지 보기(`viewer`)를 붙인다(레이아웃은 문서가 준비될 때 한 번만 한다). (2) `data-toggle="select2"`, `data-toggle="calendar"` 입력란을 초기화한다(두 모듈도 문서가 준비될 때 한 번만 훑는다). 둘 다 교체한 본문에 대해서만, 교체 때 한 번만 한다. 브라우저에서 PR 목록, 위키, 이슈, 게시판, 마일스톤을 오가며 오류 없이, select2 상자 수가 직접 열 때와 같은 것을 확인했다.
- 이동 이력은 `yona.Htmx.js`의 설정대로 서버 재로드이다(`historyCacheSize=0`).

## 시험
| 시험 | 지키는 것 |
|---|---|
| `ShellNavTest` | 모든 맥락의 항목, 순서, 조건, 활성 판정, 단축키 목록 |
| `SiteAdminShellTest`, `UserSettingsShellTest`, `OrganizationShellTest`, `ProjectShellTest`, `HomeShellTest`, `SearchShellTest` | 화면마다 사이드바가 그려지고 현재 항목만 활성이며 래퍼가 맞고 옛 메뉴가 없음 |
| `ContextPathLinksTest` | 새 화면의 링크가 컨텍스트 경로를 잃지 않음 |
| `MessageParityTest` | 새 메시지 키가 5개 번들에 있음 |
| `shellsidebar.test.mjs` | 숨기기 동작(실제 Chromium) |
| `shellnav.test.mjs` | 표지가 있는 화면끼리 본문과 사이드바만 바꾸고 문서를 다시 불러오지 않음, 표지가 없으면 일반 이동 |
옛 가로 메뉴나 설정 탭을 확인하던 기존 시험은 새 마크업(`nav.shell-sidebar`) 기준으로 고쳤다.

## 아직 남은 것
- 상단 바의 내 이슈 링크, 개발팀 문의, 업데이트 알림 띠, 프로젝트 머리 배경 배너는 옛 모양 그대로이다(선생님 결정 대기, `ui-removed-elements.md`).
- 이제 쓰이지 않는 옛 메뉴 조각과 스타일은 모든 작업이 끝난 뒤 지운다(같은 문서의 죽은 코드 후보).
- 조직, 프로젝트, 프로젝트 밖 화면의 swap-safe 판정과 표지(스크립트가 있는 화면은 모듈을 `Page` 규약으로 옮기면서 표지를 단다). `Page` 수명주기, 저장소(D-135)는 첫 사용처가 생기면 만든다.
