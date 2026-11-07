---
title: select2 3.4.5 → 4.1.0 업데이트 계획
type: architecture
sources:
  - ../decisions.md
  - js-upgrade-plan.md
  - js-library-alternatives.md
updated: 2026-11-07
---

# select2 3.4.5 → 4.1.0 업데이트 계획

`decisions.md` D-95로 이 작업은 jquery.tmpl의 Mustache 교체(D-94)보다 먼저 한다. 화면 동작과 요청, 응답 계약은 v1.6과 같아야 한다. 달라지는 것은 모두 `differences-from-v16.md`에 적는다.

## 1. 현재 상태 (조사 결과, 2026-11-07)

- 라이브러리: `lib/select2/select2.js` 3.4.5(3,255줄), `select2.css`, `select2-bootstrap.css`, 로케일 `select2_locale_ko.js`와 `select2_locale_ja.js`, 이미지 3개. 4.1.0은 jQuery 4에서 제거된 API(`isFunction`, `trim` 등)를 쓰지 않는다.
- 템플릿 27개가 `common/select2.html`로 불러온다. 이 조각이 `select2.js`, 컨테이너 클래스 복사 끄기(`adaptContainerCssClass`), `yona.ui.Select2.js`, 로케일, jquery.tmpl 템플릿 5개를 싣는다.
- 마크업은 두 방식이다.
  - **`<select data-toggle="select2" data-format="…">`** 대부분. 4.x에서도 같은 방식이다. `yona.ui.Select2.js`가 `data-format`(`projects`, `issues`, `user`, `milestone`, `issuelabel`, `branch`)을 읽어 모양과 동작을 붙인다. 속성 `data-container-css-class` 11곳, `data-dropdown-css-class` 11곳, `data-allow-clear` 1곳, `data-close-on-select` 2곳.
  - **`<input type="hidden">` 4곳**: 이슈 담당자 `#assignee`(`issue/partial-assignee.html`, `pullrequest/create.html`), 이슈 공유자 `#issueSharer`(`issue/view.html`), 풀 리퀘스트 담당자(`yona.pullrequest.Meta.js`), 리뷰 요청자(`yona.pullrequest.Reviewers.js`). 3.x의 `ajax`와 `initSelection`을 쓴다. **4.x는 `<select>`만 받는다.**
- 3.x 전용 API 사용: `formatResult` 7, `formatSelection` 5, `initSelection` 3, `query`/`ajax` 11, `.select2("val")` 4, `data("select2").val(...)` 6, 이벤트 `select2-selecting` 5, `select2-removing` 1, `select2-open` 1, `change`의 `evt.added`/`evt.removed` 3, `escapeMarkup` 4, 자체 `matcher` 2.
- 스타일: `app/assets/stylesheets/scss/_override.scss`가 select2 3.x 클래스(`.select2-choice`, `.select2-arrow`, `.select2-drop`, `.select2-result-label` 등) 약 350줄을 덮어쓴다. `_base.scss`, `_common.scss`, `_page.scss`에도 select2 규칙이 있다.
- 시험: `StaticAssetsTest`(CSS 경로), `BranchSelectWidthTest`, `CommitHistoryTest`, `CodeBrowserWebTest`, `PullRequestAssigneeLabelWebTest`, `LoginFormPageTest`, `src/test/js/layout.test.mjs`(3.x 마크업 `.select2-container`로 폭을 잰다).

## 2. 4.x에서 달라지는 점과 대응

| 3.x | 4.1.0 | 대응 |
|---|---|---|
| 숨은 `<input>` + `initSelection` | `<select>`만 지원. 선택된 값은 `<option selected>` | 서버 템플릿이 선택된 `<option>`을 그리고 이름, 아바타는 `data-*`로 싣는다 |
| `ajax.data(term, page)`, `ajax.results(data)`, `quietMillis` | `ajax.data(params)`(`params.term`), `processResults`, `delay` | 응답 JSON(사용자 배열)은 그대로 두고 `processResults`가 `id = loginId`를 붙인다. 서버 응답 계약은 바꾸지 않는다 |
| `id: function(obj)` | 결과 객체의 `id` 속성 | `processResults`에서 지정 |
| `formatResult`, `formatSelection` | `templateResult`, `templateSelection` | 6개 형식을 다시 쓴다. 로딩 줄(`state.loading`)과 optgroup도 받으므로 처리한다 |
| `matcher(term, text, element)` | `matcher(params, data)` | 사용자/프로젝트 두 matcher를 다시 쓴다 |
| `escapeMarkup: m => m` | 같은 이름 유지 | 유지(HTML 문자열을 결과에 쓰는 곳) |
| `containerCssClass` | `selectionCssClass` | `data-container-css-class` → `data-selection-css-class` 11곳 |
| `dropdownCssClass` | 같은 이름 유지 | 유지 |
| `adaptContainerCssClass`(원본 클래스 복사) | 없음. 4.x는 `:all:`을 줄 때만 복사 | `common/select2.html`의 해당 줄 삭제 |
| `.select2("val")`, `.select2("val", v)` | `.val()`, `.val(v).trigger("change")` | 4곳 |
| `$el.data("select2").val(v, true)`, `.data().text` | 없음 | `.val(v).trigger("change")`, `.select2("data")[0].text` |
| `select2-selecting`(`e.val`, `e.object`) | `select2:selecting`(`e.params.args.data`) | 5곳 |
| `select2-removing`(`e.choice`) | `select2:unselecting` | 1곳 |
| `select2-open` | `select2:open` | 1곳 |
| `change`의 `evt.added`, `evt.removed` | 없음 | `select2:select`와 `select2:unselect`로 모은다(라벨 변경 3곳) |
| `allowClear` | `placeholder` 필수 | `data-placeholder`가 없는 곳은 빈 placeholder를 준다 |
| `$el.trigger("change.select2")` | `trigger("change")` | `Subtask.js` |
| 라벨 선택 시 `select2Object.data(data, true)`, `.results`, `data("select2-data")` 내부 접근 | 없음 | `issuelabel` 동작은 `<option>`의 `selected` 속성을 직접 바꾸고 `trigger("change")`하는 방식으로 다시 쓴다 |
| 로케일 `select2_locale_ko.js` | `i18n/ko.js`(4.x AMD 형식) | 공식 파일로 교체. v1.6의 문구가 공식 번역과 다르면 차이를 기록한다 |
| 스타일 3.x 클래스 | 4.x 클래스(`.select2-selection`, `.select2-dropdown`, `.select2-results__option`) | 공식 Bootstrap 5 테마(`select2-bootstrap-5-theme` 1.3.0)를 바탕으로 `_override.scss`를 다시 쓴다. `theme: "bootstrap-5"` |

## 3. 단계

모든 단계는 **시험을 먼저 쓰고 실패를 확인**한다. 화면 확인(Playwright)과 전 화면 순회는 마지막에 한 번 한다.

**S0. 파일 확보와 기준표**
- 공식 npm 배포 파일 `select2@4.1.0`의 `dist/js/select2.js`, `dist/css/select2.css`, `dist/js/i18n/{ko,ja}.js`와 `select2-bootstrap-5-theme@1.3.0`의 `dist/select2-bootstrap-5-theme.css`를 받는다. 압축 해제 `select2.full.js`(호환 모듈 포함)는 쓰지 않는다.
- `lib/select2/`를 정리한다: 새 파일을 넣고 `select2-bootstrap.css`, `select2.png`, `select2x2.png`, `select2-spinner.gif`, `select2.jquery.json`, `select2_locale_*.js`를 지운다(4.x CSS는 이미지가 필요 없다).
- `js-vendor-manifest.tsv`에 sha256을 적는다.

**S1. 시험 먼저**
1. `JsLibraryVersionsTest`: select2 머리말이 4.1.0이다. 앱 코드에 3.x 전용 표현(`formatResult`, `formatSelection`, `initSelection`, `select2-selecting`, `.select2("val"`, `data("select2")`, `adaptContainerCssClass`, `data-container-css-class`)이 없다. `type="hidden"`으로 select2를 붙이는 곳이 없다.
2. 새 Playwright 시험 `src/test/js/select2.test.mjs`: 실제 4.1.0과 `yona.ui.Select2.js`를 불러 (a) 6개 형식의 목록 항목과 선택 항목이 어떻게 그려지는지, (b) `user`, `projects` matcher가 로그인 ID와 소유자로 찾는지, (c) 라벨이 같은 배타 카테고리의 선택을 바꾸고 비배타는 쌓이는지, (d) 담당자/공유자/리뷰어 `<select>`가 ajax 결과를 받고 선택된 값을 `change`로 알리는지, (e) 선택된 `<option>`이 미리 보이는지를 검증한다.
3. 서버 시험: 담당자 입력을 `<select>`로 그리는지, 선택된 `<option>`에 값과 `data-name`, `data-avatar-url`이 있는지(`issue/partial-assignee`, `pullrequest/create`, `issue/view`의 공유자, `pullrequest/view`의 담당자와 리뷰어).
4. `layout.test.mjs`의 브랜치 선택 폭 시험은 4.x 마크업(`.select2-container`)으로 바꾼다(폭 220이 그대로 지켜지는지).

**S2. 라이브러리와 공통 층**
- `common/select2.html`: 4.1.0과 테마 CSS를 싣고 `adaptContainerCssClass` 줄을 지운다. 로케일은 `i18n/ko.js`, `i18n/ja.js`로 바꾼다. `layout.html`, `layout-framed.html`, `migration-layout.html`의 CSS 링크도 바꾼다.
- `yona.ui.Select2.js`를 다시 쓴다: 옵션 이름 변환, `templateResult/templateSelection`, matcher, `issuelabel` 동작, 휠 스크롤 방지(`.select2-results__options`). `$.tmpl` 호출 7곳은 이 단계에서 그대로 둔다(jquery.tmpl은 jQuery 3.7.1에서 동작하며, Mustache 교체는 별도 단계 S6).
- 스타일: 공식 BS5 테마 + `_override.scss` 재작성(라벨 목록 `.issue-labels`, 브랜치 목록 `.branches`, 검색창 없음 `select2-without-searchbox`, `.branch-label`, `.avatar-wrap`).

**S3. `<select>` 화면 호출부**
- `issue.View.js`(`change`, `select2-selecting`, `data("select2").val`, `evt.added/removed`), `issue.List.js`, `board.List.js`, `issue.LabelEditor.js`, `issue.Write.js`, `TitleHeadAutoCompletion.js`, `Subtask.js`, `pullrequest/partial-search.html`.

**S4. 숨은 input 4곳**
- 서버 템플릿을 `<select>`로 바꾸고 `<option selected>`을 그린다. 폼 제출 이름과 값(`assigneeLoginId` 등)은 바꾸지 않는다. `yona.issue.Assginee.js`, `yona.issue.Sharer.js`, `yona.pullrequest.Meta.js`, `yona.pullrequest.Reviewers.js`를 4.x ajax 형식으로 다시 쓴다. 서버 JSON 응답은 그대로다.

**S5. 검증과 기록**
- 전체 H2 시험, JS 시험 전체(`node src/test/js/run.mjs`), 전 화면 순회 비교(select2 DOM이 달라지므로 이전 기준과 비교), Playwright로 담당자, 라벨, 마일스톤, 브랜치, 프로젝트 선택과 다국어(ko/ja) 직접 확인을 한 번 한다.
- 위키: `decisions.md` D-95 구현 완료와 A-nnn(에이전트 판단), `differences-from-v16.md`(내부 마크업, 클래스 이름, 로케일 문구), `js-upgrade-plan.md` 단계 6 완료, `log.md` 소요 시간.
- 커밋은 S0~S2(라이브러리와 공통 층), S3~S4(화면 호출부)로 나누거나 한 번에 낸다. 중간 상태는 화면이 깨지므로 시험을 통과한 상태로만 커밋한다.

**S6. (별도, 이 계획의 범위 밖) Mustache 교체**
- D-94. S5 뒤에 `yona.ui.Select2.js`의 `$.tmpl` 7곳과 나머지 호출을 바꾼다.

## 4. 위험과 확인할 것

- 라벨 선택(`issuelabel`)의 배타 카테고리 처리는 3.x 내부 구조에 기대고 있어 가장 위험하다. 시험 (c)로 먼저 동작을 고정한다.
- 서버가 숨은 input에 값을 채우던 곳이 `<select>`가 되면서 폼 값이 비었을 때(담당자 없음)의 요청 모양이 달라지지 않는지 확인한다(`assigneeLoginId=` 빈 값 전송).
- `allowClear`와 placeholder: 4.x는 placeholder가 있어야 지우기 단추가 보인다.
- 4.x는 선택 상자를 `body` 끝에 드롭다운으로 붙인다. 모달 안에서 쓰는 곳(`dropdownParent`)이 있는지 확인한다.
- 접근성 속성이 4.x에서 늘어 `LegacyBootstrapLintTest` 같은 정적 검사에 걸리는 것이 있는지 본다.
- 공식 번역 문구가 v1.6 로케일 파일 문구와 다를 수 있다(예: "검색 결과가 없습니다"). 다르면 v1.6 문구를 `language` 옵션으로 넘겨 유지한다.
