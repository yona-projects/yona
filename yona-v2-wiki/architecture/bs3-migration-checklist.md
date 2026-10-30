---
title: Bootstrap 2.x에서 3.0으로의 이전 점검표
type: architecture
sources:
  - https://getbootstrap.com/docs/3.4/migration/
  - decisions.md
updated: 2026-10-30
---

# Bootstrap 2.x → 3.0 이전 점검표 (X-38b)

[D-53](../decisions.md)에 따라 이 단계는 **공식 이전 안내서**(<https://getbootstrap.com/docs/3.4/migration/>, "Migrating from v2.x to v3.0", 2026-10-30에 읽음)를 기준으로 한다. 안내서의 항목을 하나씩 내려가며 이 프로젝트의 발생 수를 세고(템플릿 `class` 값, 자체 JS, 자체 LESS), 대응한 뒤 상태를 적는다. 안내서에 없는 이 프로젝트의 자체 변경은 맨 아래 "안내서 밖의 변경"에 적는다.

Bootstrap 3.4.1 배포본: GitHub 릴리스 `twbs/bootstrap` v3.4.1 `bootstrap-3.4.1-dist.zip`, SHA-256 `d49793cf773cbd393ac2cf340c3b4ddab5365fa7c292098ac07e12eab3efd92e`.

## 주요 클래스 변경 (안내서 "Major class changes")

| 2.x | 3.0 | 상태 |
|---|---|---|
| `.row-fluid` | `.row` | 완료 |
| `.span*` | `.col-md-*` | 완료 |
| `.offset*` | `.col-md-offset-*` | 완료 |
| `.brand`, `.navbar .nav`, `.nav-collapse`, `.nav-toggle`, `.btn-navbar` | `.navbar-brand`, `.navbar-nav`, `.navbar-collapse`, `.navbar-toggle`, `.navbar-btn` | 완료 |
| `.hero-unit` | `.jumbotron` | 완료 |
| `.icon-*` | `.glyphicon .glyphicon-*` | 완료 |
| `.btn` | `.btn .btn-default` | 완료 |
| `.btn-mini`, `.btn-small`, `.btn-large` | `.btn-xs`, `.btn-sm`, `.btn-lg` | 완료 |
| `.alert` | `.alert .alert-warning` | 완료 |
| `.alert-error` | `.alert-danger` | 완료 |
| `.visible-phone/tablet/desktop`, `.hidden-phone/tablet/desktop` | `.visible-xs/sm/md lg`, `.hidden-xs/sm/md lg` | 완료 |
| `.input-block-level` | `.form-control` | 완료 |
| `.control-group`(+ `.warning`, `.error`, `.success`) | `.form-group`(+ `.has-*`) | 완료 |
| `.checkbox.inline`, `.radio.inline` | `.checkbox-inline`, `.radio-inline` | 완료 |
| `.input-prepend`, `.input-append`, `.add-on` | `.input-group`, `.input-group-addon` | 완료 |
| `.img-polaroid` | `.img-thumbnail` | 완료 |
| `ul.unstyled`, `ul.inline` | `.list-unstyled`, `.list-inline` | 완료 |
| `.muted` | `.text-muted` | 완료 |
| `.label` | `.label .label-default` | 완료 |
| `.label-important`, `.text-error`, `.table .error` | `.label-danger`, `.text-danger`, `.table .danger` | 완료 |
| `.bar`, `.bar-*` | `.progress-bar`, `.progress-bar-*` | 완료 |
| `.accordion`, `-group`, `-heading`, `-body`, `-inner` | `.panel-group`, `.panel .panel-default`, `.panel-heading`, `.panel-collapse`, `.panel-body` | 완료 |

## 사라진 것 (안내서 "What's removed")

`.form-actions`, `.form-search`, `.control-group.info`, 고정 폭 입력(`.input-mini/small/medium/large/xlarge/xxlarge`), `.input-block-level`, `.btn-inverse`, `.controls`, `.controls-row`, `.navbar-inner`, `.navbar .divider-vertical`, `.dropdown-submenu`, `.tabs-left/right/below`, `.pill-content`/`.pill-pane`(→ `.tab-content`/`.tab-pane`), `.nav-list`/`.nav-header`, `.help-inline`(→ `.help-block`과 비슷), `.progress-info/success/warning/danger`(→ `.progress-bar-*`). 상태: 완료(적용 대상 없는 항목 포함).

## 추가 안내 (안내서 "Additional notes")

- 텍스트 입력은 `.form-control`을 써야 포커스 색과 둥근 모서리가 나온다. `.form-control`은 폭이 100%이므로 `col-*`로 감싸 폭을 정한다.
- `.badge`에는 문맥 클래스(`-success` 등)가 없다.
- `.btn`은 `.btn-default`와 함께 써야 기본 단추가 된다. `.row`는 이제 유동 폭이다. 이미지는 기본이 반응형이 아니다(`.img-responsive`).
- 아이콘은 글꼴 기반 `.glyphicon .glyphicon-*`(이 사이트의 아이콘은 자체 글꼴 `yobicon`이 대부분이다).
- **Typeahead는 번들에서 빠졌다**(Twitter typeahead.js로 대체하라고 안내한다).
- **모달**: 머리, 본문, 바닥을 `.modal-content`와 `.modal-dialog`로 감싸야 하고 `.modal`에 `.hide`를 쓰지 않는다.
- 단추 토글은 `data-toggle="buttons"`로 통일, JS 이벤트는 이름 공간을 쓴다(`show.bs.modal`, `shown.bs.tab` 등).

## 안내서 밖의 변경 (이 프로젝트의 자체 사정)

- **적용 대상이 없던 항목**(템플릿에 없었다): `.brand`와 `.navbar` 계열, `.hero-unit`, `.icon-*`(이 사이트의 아이콘은 자체 글꼴 `yobicon`), `.btn-mini/small/large`(단추는 자체 `ybtn`), 가시성 `.visible-*`/`.hidden-*`, `.accordion*`, 고정 폭 입력 크기, `.btn-inverse`, `.nav-list`, `.tabs-left` 등.
- **`.help-inline`**: 안내서는 `.help-block`이 비슷하다고 하나 블록이라 줄바꿈이 생긴다. 24곳을 `.help-block`으로 바꿨다가 화면이 달라져(제목 글자 크기 연쇄) 원래대로 되돌리고 `.help-inline` 규칙을 호환 층에 두었다(A-285).
- **`.input-prepend`/`.input-append`**: GNB 검색 상자와 첫 설정 폼에서 클래스 이름만 쓰던 것이라 안내서의 `.input-group`으로 바꾸지 않고 자체 클래스(`.gnb-search-form`, `.secret-form`)에 BS2 규칙을 옮겼다(A-285).
- **진행 막대**: 안내서의 `.bar`→`.progress-bar`는 BS2 `.progress` 안의 막대에만 적용했다(6곳). 이 사이트의 자체 `.upload-progress .bar`(자체 CSS)는 그대로이다.
- **typeahead**: 2.3.1 플러그인을 떼어 번들에 유지(A-284).
- **죽은 클래스**: `textarea.span4`(v1.6의 잘못된 클래스 이름, 어떤 규칙에도 걸리지 않았다) 3곳을 지웠다.
- **JS**: 모달 이벤트에 이름 공간을 붙였다(`shown.bs.modal`, `hidden.bs.modal`), `alert-error`→`alert-danger`(`yobi.git.Write.js`), `span8/span12`→`col-md-8/col-md-12`(`yona.Usermenu.js`).
- **jQuery**: BS3 JS는 jQuery 1.9.1 이상을 요구하므로 UI 키트 화면(`help/uikit.html`)의 jQuery 1.9.0을 3.3.1로 올렸다. 이주 화면은 BS JS를 쓰지 않아 그대로이다.
- **JS 번들 재생성**: `build-tools/minify-js.sh`(v1.6 `minify-js.sh`를 옮긴 것). 기존 `yona-layout.js`, `yona-lib.js`를 바이트 단위로 똑같이 만드는 것을 확인했고, `yona-common.js`만 BS3 JS와 typeahead가 들어가 달라진다.
