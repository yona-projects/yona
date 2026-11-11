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

## 시험
| 시험 | 지키는 것 |
|---|---|
| `ShellNavTest` | 모든 맥락의 항목, 순서, 조건, 활성 판정, 단축키 목록 |
| `SiteAdminShellTest`, `UserSettingsShellTest`, `OrganizationShellTest`, `ProjectShellTest`, `HomeShellTest`, `SearchShellTest` | 화면마다 사이드바가 그려지고 현재 항목만 활성이며 래퍼가 맞고 옛 메뉴가 없음 |
| `ContextPathLinksTest` | 새 화면의 링크가 컨텍스트 경로를 잃지 않음 |
| `MessageParityTest` | 새 메시지 키가 5개 번들에 있음 |
| `shellsidebar.test.mjs` | 숨기기 동작(실제 Chromium) |
옛 가로 메뉴나 설정 탭을 확인하던 기존 시험은 새 마크업(`nav.shell-sidebar`) 기준으로 고쳤다.

## 아직 남은 것
- 상단 바의 내 이슈 링크, 개발팀 문의, 업데이트 알림 띠, 프로젝트 머리 배경 배너는 옛 모양 그대로이다(선생님 결정 대기, `ui-removed-elements.md`).
- 이제 쓰이지 않는 옛 메뉴 조각과 스타일은 모든 작업이 끝난 뒤 지운다(같은 문서의 죽은 코드 후보).
- htmx 이동(D-136), `Page` 수명주기, 저장소(D-135)는 첫 사용처를 정한 뒤 진행한다.
