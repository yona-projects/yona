---
title: Bootstrap 3에서 4로의 이전 점검표
type: architecture
sources:
  - https://getbootstrap.com/docs/4.6/migration/
  - decisions.md
updated: 2026-10-31
---

# Bootstrap 3 → 4 이전 점검표 (X-38c)

[D-53](../decisions.md)에 따라 이 단계는 **공식 이전 안내서**(<https://getbootstrap.com/docs/4.6/migration/>, "Migrating to v4", 2026-10-30에 읽음)를 기준으로 한다. 안내서의 항목마다 이 프로젝트의 발생 수를 세고 대응한 뒤 상태를 적는다. 안내서에 없는 자체 변경은 맨 아래에 적는다.

## 진행 구간

1. **1구간 완료(태그 `scss`)**: 자체 스타일 LESS를 SCSS로 재작성. dart-sass로 컴파일하고 LESS의 CSS와 규칙 단위로 같음을 확인했다(A-287, A-288). 전 화면 순회에서 BS3 빌드와 픽셀 차이 0.
2. **2구간**: Bootstrap 4 SCSS 소스를 들이고 자체 변수로 v1.6 모양에 맞춘다(호환 층 `_bs3-compat.scss`의 설정을 변수와 믹스인으로 흡수).
3. **3구간**: 템플릿과 JS를 안내서에 맞춰 바꾼다.
4. **4구간**: 전 화면 순회와 상호작용 점검, 호환 층 정리, 태그 `bs4`.

## 전역 변경 (안내서 "Global changes")

| 변경 | 이 사이트의 대응 | 상태 |
|---|---|---|
| Flexbox 기본, Less에서 Sass로 | 자체 스타일은 SCSS로 옮김. 그리드는 호환 층의 float 열을 flex 열로 | 1구간 완료, 나머지 대기 |
| 단위 `px`에서 `rem`, 전역 글꼴 14px에서 16px | 이 사이트는 본문 14px(BS2 기준)이므로 `html`/`body` 기준과 변수로 맞춘다 | 대기 |
| 그리드: 다섯째 구간 추가, `-xs` 접두사 제거, **모든 구간이 한 단계씩 위로**(v3 `col-md-6` = v4 `col-lg-6`) | `col-md-*` 약 240곳을 `col-lg-*`로. 이 사이트는 반응형 CSS를 쓰지 않으므로 열 폭은 호환 층이 고정한다 | 대기 |
| 비반응형 사용 불가 | 호환 층으로 좁은 화면에서도 열이 쌓이지 않게 유지하는 방식을 다시 정한다 | 대기 |
| 온라인 커스터마이저 폐지, 변수로 설정 | SCSS 변수 파일로 설정 | 대기 |

## 구성 요소 (안내서 "By component")

| 2→3 이후 항목 | 4 | 상태 |
|---|---|---|
| Reboot(요소 선택자 기본 규칙) | `box-sizing: border-box`, `em`에서 `rem`, 링크와 폼 기본 | 대기(호환 층의 BS2 기본 규칙과 맞춘다) |
| 타이포: `.page-header` 폐지, `.dl-horizontal` 폐지, `.blockquote`, `.list-inline-item` | 사용 수 확인 후 대응 | 대기 |
| 이미지 `.img-responsive`→`.img-fluid`, `.img-rounded`→`.rounded`, `.img-circle`→`.rounded-circle` | 해당 없음 가능 | 대기 |
| 표: `.table-condensed`→`.table-sm`, 문맥 클래스 `.table-*` 접두사 | 점검 | 대기 |
| 폼: `.control-label`→`.col-form-label`, `.input-lg/sm`→`.form-control-lg/sm`, `.help-block`→`.form-text`, `.radio-inline`/`.checkbox-inline`, `.checkbox`/`.radio`→`.form-check`, `.form-group.row`, `.has-*`→`:valid`/`:invalid` | `.form-group`은 BS5까지 유지되나 구조 변경 | 대기 |
| 단추: `.btn-default`→`.btn-secondary`, `.btn-xs` 폐지, **stateful 단추(`$().button('loading')` 등) 폐지** | `.btn`을 쓰는 곳은 소수(자체 `ybtn`) | 대기 |
| 버튼 그룹: flexbox, `.btn-group-justified` 폐지 | 점검 | 대기 |
| 드롭다운: `.dropdown-item`, `.dropdown-divider`, 캐럿 `::after`(`<span class="caret">` 불필요) | 점검 | 대기 |
| 그리드 클래스 접두사 변경, push/pull을 `order`로 | 사용 없음 가능 | 대기 |
| 목록 그룹: `.list-group-item-action` | 점검 | 대기 |
| 모달: flexbox, **`remote` 옵션과 `loaded.bs.modal` 제거** | 사용 여부 확인 | 대기 |
| 내비게이션: `.nav`, `.nav-item`, `.nav-link` | `.nav > li > a`를 쓰는 곳 약 32템플릿 | 대기 |
| 내비바: `.navbar-expand-*`, `.navbar-toggler`, `.navbar-light` 등 | 이 사이트는 BS 내비바를 쓰지 않음(자체 GNB) | 대기 |
| 페이지 번호: `.page-item`, `.page-link`, `.pager` 폐지 | 자체 `yobi.Pagination.js`와 맞춘다 | 대기 |
| 빵부스러기: `.breadcrumb-item` | 1곳 | 대기 |
| 라벨과 배지 통합: `.label`, `.badge`→`.badge`, `.badge-pill`, `.badge-default`→`.badge-secondary` | 라벨 약 50, 배지 8 | 대기 |
| 패널, 썸네일, well → `.card` | `.well` 5곳, 패널 사용 없음 | 대기 |
| 진행 막대: `.progress-bar-*`→`.bg-*`, `.active`→`.progress-bar-animated` | 6곳 | 대기 |
| 캐러셀 | 사용 없음 가능 | 대기 |
| 유틸리티: 표시(`.d-*`), `.pull-left/right`→`.float-left/right`, `.hidden-*`/`.visible-*` 폐지, `.center-block`→`.mx-auto`, `.hidden`/`.show` 폐지 | `.pull-*` 158곳, `.hide` 사용 점검 | 대기 |
| **Glyphicons 폐지** | 이 사이트의 아이콘은 자체 `yobicon`이라 영향이 작다 | 대기 |
| **Affix 플러그인 폐지**(`position: sticky`) | 5곳 | 대기 |
| 벤더 접두사 믹스인 폐지(Autoprefixer) | 자체 믹스인(`border-radius` 등)은 유지하되 접두사는 남겨 둔다 | 대기 |
| jQuery 1.9.1 이상, 3.x 권장 | 이미 3.3.1 | 완료 |

## 안내서 밖의 변경

작업하면서 채운다.

## 호환 층 의존 장부 (2026-10-31)

호환 층 `app/assets/stylesheets/scss/_bs3-compat.scss`는 BS2 규칙을 SCSS로 옮겨 놓은 것이다. 선생님 지적("BS2/3 CSS에 우연히 기대는 규칙은 걷어낼 때 더 문제")에 따라, 마크업을 안내서대로 바꾼 뒤 대응하는 규칙을 지우고 남는 것만 이유와 함께 적는다([A-293](../decisions.md)).

| 영역 | 상태 | 비고 |
|---|---|---|
| 탭·내비게이션(`.nav*`) | **호환 층에서 삭제**, BS4 + 변수(`$nav-*`) + `_yobiUI.scss` | A-290 |
| 드롭다운(`.dropdown*`, `.caret`, `.open`) | **삭제**, BS4 + 변수(`$dropdown-*`) + 자체 `.caret` | A-290 |
| 체크박스·라디오 | **마크업 전환**(`form-check`), 호환 규칙 없음. 자체 `.form-check` 규칙(`_page.scss`) | A-292 |
| 진행 막대 | **마크업 전환**(`bg-*`), 모양은 `scss/_progress.scss` | A-292 |
| 배지(`label`→`badge`) | 마크업 전환. 색은 호환 층의 `.badge-*`가 BS2 값으로 입힌다 | 변수화 대기 |
| `affix` | 자체 JS `yona.Affix.js`로 대체 | A-291 |
| 단추 그룹(`.btn-group*`, `.btn-*`) | 호환 층에 남음(56개 규칙). 사이트 단추는 자체 `ybtn`이고 BS 단추는 일부 | 대기 |
| 그리드(`.row`, `col-lg-*`) | 호환 층에 남음(44개 규칙). BS4 flex 열을 끄고 BS2 유동 폭을 쓴다 | A-289, BS5에서 결정 |
| 표(`.table*`) | BS3 단계 보정은 삭제. BS2 값은 호환 층에 남음(43개 규칙) | 변수화 대기 |
| 페이지네이션·페이저·브레드크럼 | 호환 층에 남음 | 대기 |
| `form-horizontal`, `control-group`, `help-inline` | 호환 층에 남음(2개 템플릿, `help-inline` 24곳) | 마크업 전환 대기 |
| `.hide`(62곳 + JS 19곳) | 마크업 전환 대기(`d-none`) | 대기 |
| 요소 선택자 기본값(`body`, `a`, `h1`…, 입력 요소) | 호환 층에 남음 | BS5에서 결정 |

호환 층 크기: BS4 도입 직후 2,199줄, 현재 1,746줄.
