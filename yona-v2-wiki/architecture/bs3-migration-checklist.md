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
| `.row-fluid` | `.row` | 대기 |
| `.span*` | `.col-md-*` | 대기 |
| `.offset*` | `.col-md-offset-*` | 대기 |
| `.brand`, `.navbar .nav`, `.nav-collapse`, `.nav-toggle`, `.btn-navbar` | `.navbar-brand`, `.navbar-nav`, `.navbar-collapse`, `.navbar-toggle`, `.navbar-btn` | 대기 |
| `.hero-unit` | `.jumbotron` | 대기 |
| `.icon-*` | `.glyphicon .glyphicon-*` | 대기 |
| `.btn` | `.btn .btn-default` | 대기 |
| `.btn-mini`, `.btn-small`, `.btn-large` | `.btn-xs`, `.btn-sm`, `.btn-lg` | 대기 |
| `.alert` | `.alert .alert-warning` | 대기 |
| `.alert-error` | `.alert-danger` | 대기 |
| `.visible-phone/tablet/desktop`, `.hidden-phone/tablet/desktop` | `.visible-xs/sm/md lg`, `.hidden-xs/sm/md lg` | 대기 |
| `.input-block-level` | `.form-control` | 대기 |
| `.control-group`(+ `.warning`, `.error`, `.success`) | `.form-group`(+ `.has-*`) | 대기 |
| `.checkbox.inline`, `.radio.inline` | `.checkbox-inline`, `.radio-inline` | 대기 |
| `.input-prepend`, `.input-append`, `.add-on` | `.input-group`, `.input-group-addon` | 대기 |
| `.img-polaroid` | `.img-thumbnail` | 대기 |
| `ul.unstyled`, `ul.inline` | `.list-unstyled`, `.list-inline` | 대기 |
| `.muted` | `.text-muted` | 대기 |
| `.label` | `.label .label-default` | 대기 |
| `.label-important`, `.text-error`, `.table .error` | `.label-danger`, `.text-danger`, `.table .danger` | 대기 |
| `.bar`, `.bar-*` | `.progress-bar`, `.progress-bar-*` | 대기 |
| `.accordion`, `-group`, `-heading`, `-body`, `-inner` | `.panel-group`, `.panel .panel-default`, `.panel-heading`, `.panel-collapse`, `.panel-body` | 대기 |

## 사라진 것 (안내서 "What's removed")

`.form-actions`, `.form-search`, `.control-group.info`, 고정 폭 입력(`.input-mini/small/medium/large/xlarge/xxlarge`), `.input-block-level`, `.btn-inverse`, `.controls`, `.controls-row`, `.navbar-inner`, `.navbar .divider-vertical`, `.dropdown-submenu`, `.tabs-left/right/below`, `.pill-content`/`.pill-pane`(→ `.tab-content`/`.tab-pane`), `.nav-list`/`.nav-header`, `.help-inline`(→ `.help-block`과 비슷), `.progress-info/success/warning/danger`(→ `.progress-bar-*`). 상태: 대기.

## 추가 안내 (안내서 "Additional notes")

- 텍스트 입력은 `.form-control`을 써야 포커스 색과 둥근 모서리가 나온다. `.form-control`은 폭이 100%이므로 `col-*`로 감싸 폭을 정한다.
- `.badge`에는 문맥 클래스(`-success` 등)가 없다.
- `.btn`은 `.btn-default`와 함께 써야 기본 단추가 된다. `.row`는 이제 유동 폭이다. 이미지는 기본이 반응형이 아니다(`.img-responsive`).
- 아이콘은 글꼴 기반 `.glyphicon .glyphicon-*`(이 사이트의 아이콘은 자체 글꼴 `yobicon`이 대부분이다).
- **Typeahead는 번들에서 빠졌다**(Twitter typeahead.js로 대체하라고 안내한다).
- **모달**: 머리, 본문, 바닥을 `.modal-content`와 `.modal-dialog`로 감싸야 하고 `.modal`에 `.hide`를 쓰지 않는다.
- 단추 토글은 `data-toggle="buttons"`로 통일, JS 이벤트는 이름 공간을 쓴다(`show.bs.modal`, `shown.bs.tab` 등).

## 안내서 밖의 변경 (이 프로젝트의 자체 사정)

작업하면서 채운다.
