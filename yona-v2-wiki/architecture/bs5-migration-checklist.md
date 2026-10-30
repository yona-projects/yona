---
title: Bootstrap 4에서 5로의 이전 점검표
type: architecture
sources:
  - https://getbootstrap.com/docs/5.3/migration/
  - decisions.md
updated: 2026-10-31
---

# Bootstrap 4 → 5 이전 점검표 (X-38d, X-38e)

[D-53](../decisions.md)에 따라 이 단계는 **공식 이전 안내서**(<https://getbootstrap.com/docs/5.3/migration/>, 2026-10-31에 읽음)를 기준으로 한다. 사용 버전은 **5.3.8**(2025-08-26, 최신 5.x 안정판. 6.0은 알파라서 쓰지 않는다, [A-295](../decisions.md)). 소스는 `app/assets/stylesheets/vendor/bootstrap/`(npm `bootstrap@5.3.8`의 scss, SHA-256 `8b02d4a7482e0f0a2b1da58f1c9c86830e96cc2f77f094f89e4681b19886bc38`), JS는 `bootstrap.bundle.min.js`(Popper 2 포함)이다.

방침은 BS4 단계에서 정한 대로다([A-293](../decisions.md)): 안내서의 항목은 마크업에 적용하고, 모양 차이는 BS5 변수(`scss/_bs-variables.scss`) 또는 해당 영역의 자체 SCSS로 맞춘다. 호환 층(`_bs3-compat.scss`)에는 BS5가 새로 입히는 기본 모양 중 BS2와 다른 것을 되돌리는 규칙만 더한다.

## 의존성과 Sass

| 안내서 항목 | 이 사이트의 대응 | 상태 |
|---|---|---|
| jQuery 의존 제거 | BS 자체는 jQuery 없이 동작한다. 사이트의 자체 코드는 jQuery를 계속 쓰고, BS 위젯 호출만 `bootstrap.*` API로 바꿨다 | 완료 |
| Popper 1→2 | 번들(`bootstrap.bundle.min.js`)에 포함. 드롭다운은 `bootstrap.Dropdown.Default.display = "static"`로 CSS 위치를 쓴다 | 완료 |
| LibSass→Dart Sass | 이미 dart-sass 1.69.7(A-287) | 해당 없음 |
| Sass 맵 병합 제거 | `$spacers`는 전체를 적는다(1단계 = 5px). `$theme-colors`는 개별 변수(`$primary`~`$dark`)로 BS2 색을 준다 | 완료 |
| `$enable-print-styles` 등 옵션 이름 | 쓰지 않음. `$enable-rfs`, `$enable-dark-mode` 등을 꺼서 v1.6 크기를 유지 | 완료 |
| 표 셀 여백 변수 `-y`/`-x` | `$table-cell-padding-y/x` | 완료 |

## JavaScript

| 안내서 항목 | 이 사이트의 대응 | 상태 |
|---|---|---|
| `data-toggle`→`data-bs-toggle` 등 속성 이름공간 | BS 위젯(`tooltip`, `popover`, `tab`, `dropdown`, `modal`, `collapse`)에 붙은 속성만 템플릿과 자체 JS의 HTML 문자열·선택자에서 바꿨다. 자체 위젯용 `data-toggle`(select2, calendar 등)은 그대로 둔다 | 완료 |
| 플러그인 호출은 선택자 또는 요소 | `$().modal()`, `.popover()`, `.tab()`, `.tooltip()` 호출을 `bootstrap.Modal/Popover/Tab/Tooltip`으로 옮겼다. 반복되는 팝오버와 탭은 `$yobi.showPopover`, `$yobi.hidePopover`, `$yobi.showTab`(`yobi.Common.js`)로 모았다 | 완료 |
| `_getInstance()`→`getInstance()` | `getOrCreateInstance`, `getInstance`를 쓴다 | 완료 |
| 드롭다운 `flip` 옵션 제거, 이벤트가 토글에서 발생 | 쓰는 곳 없음 | 해당 없음 |
| `whiteList`→`allowList` | 쓰는 곳 없음 | 해당 없음 |
| 상태 단추(`button('loading')`)는 BS4에서 폐지 | `yobi.site.MassMail.js`가 같은 동작을 직접 한다 | 완료 |
| `popover("destroy")`→`dispose` | `$yobi.hidePopover`로 | 완료 |
| 탭은 `.nav`, `.list-group`, `[role="tablist"]` 안에서만 동작 | 자체 목록 `ul.nav-subtab`, `ul#notification-projects`에 `role="tablist"`를 달고, 활성 표시는 `yona.Tabs.js`가 `li`에 옮긴다 | 완료 |

## 유틸리티와 컴포넌트

| v4 | v5 | 이 사이트 | 상태 |
|---|---|---|---|
| `.float-left/right` | `.float-start/end` | 템플릿 약 156곳 | 완료 |
| `.pl-*`, `.pr-*`, `.ml-*`, `.mr-*` | `.ps-*`, `.pe-*`, `.ms-*`, `.me-*` | `ps-1`만 사용(`help-inline` 대체) | 완료 |
| `.text-left/right` | `.text-start/end` | 템플릿에 없음 | 해당 없음 |
| `.text-muted` | `.text-body-secondary`(5.3) | 26곳, 색은 `$body-secondary-color` | 완료 |
| `.badge-pill`, `.badge-*` | `.rounded-pill`, `.bg-*` | 배지 약 50곳. 색은 `$primary`~`$dark`로 BS2 값 | 완료 |
| `.close` | `.btn-close`(글자 없음, `aria-label`) | 닫기 단추 21곳 | 완료 |
| 아이콘 단추의 `.close` | 해당 없음 | 코드 댓글의 아이콘 단추 5곳은 자체 `btn-icon-close` | 완료 |
| `.form-group` | 폐지 | 자체 `form-field`(아래 여백 20px) | 완료 |
| `.form-text` | `display`를 정하지 않음 | 자체 규칙이 `display: block` | 완료 |
| 체크박스·라디오(`.form-check`) | 입력 요소를 직접 그린다(`appearance: none`) | 네이티브 모양으로 되돌림(`_page.scss`) | 완료 |
| 표 | CSS 변수로 재설계, 칸 아래 선, 칸 배경 | `$table-border-width: 0`, `$table-bg: transparent`, `.table-bordered`의 행 선 제거 | 완료 |
| 그리드 `.row > *` | 모든 자식에 폭 100%와 안쪽 여백 | 열이 아닌 자식은 되돌림(`:where()`로 명시도를 낮춰 자체 규칙이 이긴다) | 완료 |
| 단추 그룹 | 이웃 단추를 -1px 겹침 | 호환 층에서 되돌림 | 완료 |
| `hr` | `opacity: .25` | 호환 층에서 되돌림 | 완료 |
| `legend` | `float: left` | 호환 층에서 되돌림 | 완료 |
| 모달 | 안내서 범위 밖. 구조는 같고 닫기 단추만 `btn-close` | 순회로 확인 | 완료 |
| 드롭다운 열림 표시 | 부모가 아니라 메뉴와 토글 단추의 `.show` | 자체 규칙을 `.dropdown-menu.flat.show`, `.btn.dropdown-toggle.show`로 | 완료 |

## 안내서 밖의 변경

- BS5 번들은 ES2015 이상이라 이 저장소의 Closure 컴파일러로 `yona-common.js`에 합칠 수 없어, 압축된 번들을 템플릿이 따로 불러온다(`common/scripts.html`).
- `$(function(){…})`는 BS5의 jQuery 플러그인 등록(`DOMContentLoaded`)보다 먼저 실행될 수 있다. 그래서 초기화 코드는 jQuery 플러그인(`$().popover()`)이 아니라 `bootstrap.*`를 직접 부른다.
- `.jq-elevator.align-bottom`: 플러그인이 쓰는 클래스가 BS5 유틸리티와 같은 이름이라 호환 층에서 되돌린다.
