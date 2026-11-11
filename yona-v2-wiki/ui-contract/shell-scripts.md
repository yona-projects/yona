---
title: 화면 공통부 계약 - 공통 스크립트와 전역 단축키
type: ui-contract
sources:
  - src/main/resources/static/assets/javascripts/yona-layout.js
  - src/main/resources/static/assets/javascripts/yona-common.js
  - build-tools/js-bundle/bundles.mjs
  - src/main/resources/static/assets/javascripts/common/yona.Common.js
  - src/main/resources/static/assets/javascripts/common/yona.Csrf.js
  - src/main/resources/static/assets/javascripts/common/yona.Htmx.js
  - src/main/resources/static/assets/javascripts/esm/*.js
  - src/main/resources/static/assets/javascripts/esm/ui/*.js
  - src/main/resources/templates/common/scripts.html
  - src/main/resources/templates/common/importmap.html
  - src/main/kotlin/dev/yona/web/JsMessagesController.kt
updated: 2026-11-11
---

# 공통 스크립트와 전역 단축키

[`shell.md`](shell.md)의 짝 문서이다. 화면별 스크립트(`esm/service/*`)는 각 영역 담당이 정리하고, 여기서는 **모든 화면이 쓰는 스크립트**와 **전역 단축키**를 정리한다. 발견 사항 번호는 `shell.md` 17절이다.

## 1. 번들과 로드 구조

| 파일 | 내용(`build-tools/js-bundle/bundles.mjs:11-21`) | 불러오는 곳 |
|---|---|---|
| `yona-layout.js`(161KB, 압축) | 입력 순서대로 `nprogress.js`(0.2.0), `jquery-3.7.1.js`, `htmx.js`(2.0.11), `common/yona.Common.js`, `common/yona.Htmx.js`를 이은 것 | `layout`, `layout-framed`의 `<head>`(`layout.html:41`, `layout-framed.html:39`). 마이그레이션 레이아웃은 쓰지 않고 낱개로 불러온다 |
| `yona-common.js`(22KB, 압축) | `mustache.js`, `xss.js`(옛 정규식 필터 `Filter`) | `common/scripts`(`scripts.html:43`) |
| `common/yona.Csrf.js` | CSRF 토큰 싣기. 번들에 없다 | `layout-framed`는 `<head>`, `layout`은 `common/scripts`(문서 끝). 아래 주의 |
| `/messages.js` | 전역 `Messages(키, 인자...)`(2.4절) | `common/scripts`(`scripts.html:42`) |
| `esm/index.js` + 모듈들 | 진입점(4.1절) | `common/scripts`(`scripts.html:54`) |
| `common/importmap.html` | import map: `@github/combobox-nav` -> `lib/text-expander/combobox-nav.js`, `yona/` -> `esm/`. 어떤 `<script type=module>`보다 앞에 있어야 한다(Firefox) | 세 레이아웃의 `<head>` 맨 앞(`layout.html:18`) |

**주의: 로드 순서**: `layout.html`에서 `yona.Csrf.js`는 `common/scripts`가 포함될 때 불러와진다(`scripts.html:41`, 본문 끝). 그 앞에 문서 분석 중 도는 스크립트가 같은 출처에 상태 변경 요청을 보내면 토큰이 안 실릴 수 있다. 현재 그런 요청은 없다(모든 요청이 문서 준비 이후, 확인함).

외부 라이브러리 이름과 버전(읽지 않음, 파일 머리 문구와 시험 `JsLibraryVersionsTest.kt` 기준): Bootstrap 5.3.8 번들(`bootstrap/js/bootstrap.bundle.min.js`) + `bootstrap-switch.js`, jQuery 3.7.1, jQuery UI 1.14.2 맞춤(로그인 대화상자만), htmx 2.0.11, NProgress 0.2.0, autosize, tippy + popper, select2 4.1.0, vanilla-calendar-pro 3.4.0, text-expander-element 2.9.4, markdown-toolbar-element 2.2.3, marked 4.3.0 + `marked-yona.js`, highlight.js 11.12.0, Viewer.js 1.15.2, mustache, vue 3.5.45(마이그레이션만), video.js, ace, cropperjs.

## 2. `common/scripts.html`이 하는 일

### 2.1 순서

`common/scripts`는 레이아웃 본문 끝에서 한 번 포함된다.

1. 확인 대화상자 틀 `#yobiDialog`(`.modal.yobiDialog`, `.msg`, `.desc`, 확인 단추 `button.confirm`)와 알림 틀 `#yobiToasts`, 템플릿 `script#tplYobiToast`(`scripts.html:10-35`).
2. 익명이면 `login-dialog` 포함(`:37-39`).
3. 스크립트: `yona.Csrf.js`, `/messages.js`, `yona-common.js`, Bootstrap 번들, `bootstrap.Dropdown.Default.display = "static"`(드롭다운을 CSS 위치로 둔다, X-38c), `bootstrap-switch.js`, `autosize`, 모듈 `Tabs.js`, `KeyControl.js`, `Clipboard.js`, `index.js`, `text-expander-element.js`, `markdown-toolbar-element.js`, tippy(`:41-59`).
4. 인라인 `$(document).ready`(`:61-203`)의 초기화, 아래 표.

| 줄 | 하는 일 |
|---|---|
| 62 | `$yona.setScriptPath('/assets/javascripts/')`(2.3절 `loadModule`이 쓴다) |
| 65-66, 75 | `window.yona.language`와 `window.yonaLanguage`에 요청 언어(`#locale.language`)를 넣는다(달력과 상대 시각이 쓴다) |
| 70-72 | `body`에 위임: `[data-bs-toggle=tooltip]`에 마우스가 들어오면 그때 `bootstrap.Tooltip`을 만들어 보인다 |
| 78-84 | `.btn-group[data-name]`마다 `new yona.ui.Dropdown({elContainer})`(`data-activate="manual"`이면 건너뜀) |
| 88-92 | 전역 단축키 `CTRL+ENTER`: 입력란(`INPUT`, `TEXTAREA`)에서 가장 가까운 `form`을 `$(form).submit()` |
| 93-97 | 로그인 세션 ID가 있으면 `U` -> `/<로그인 ID>`(`currentUser.sessionLoginId`, 토큰 인증이면 없음) |
| 99-101 | `A` -> `/user/issues` |
| 104-108 | `yona.Files.init({sListURL:'/files', sUploadURL:'/files', maxFileSize:"<site.maxFileSize>"})` |
| 111-123 | `.issueLink`마다 tippy 툴팁(내용 `decodeURI(href)`, 지연 1000/100ms, 위쪽 왼쪽) |
| 125-127 | `MSIE`면 `.unsupported` 보임 |
| 130-136 | flash: `flashMessages.notices` 각각 `$yona.notify(글, 5000)`, `alertTitle`이 있으면 `$yona.alert(제목, null, 설명)`(2.5절) |
| 138-141 | `[data-toggle=search-scope]` 클릭: 검색 폼 `action`과 범위 단추 글자 바꾸기(`shell.md` 3.1절) |
| 143 | `yona.OriginalMessage.hide($("[data-via-email]"))`: 메일 회신 본문에서 `---Original Message---` 구분선 이후를 접고 `...` 단추를 둔다 |
| 145-148 | `.logo, .title > a, .project-menu-nav > li > a, .show-progress-bar, .project-breadcrumb > span > a, .project-name > a, a[class="title"]` 클릭 때 `NProgress.set(0.7); start()` |
| 150-154 | `pageshow`가 `persisted`(뒤로 가기 복원)면 `NProgress.done()` |
| 157-166 | `.markdown-wrap a[href]` 중 `^[^./#]`로 시작하고(상대 경로와 `#`이 아닌) 현재 `protocol//host`로 시작하지 않는 링크에 `target=_blank`. `mailto:`나 `javascript:`도 이 규칙에 걸려 새 창 속성이 붙는다 |
| 168 | `autosize($('textarea'))` |
| 170-189 | `.add-task-list-button` 클릭: textarea 커서에 `\n- [ ] Todo A\n- [ ] Todo B\n- [ ] Todo C`(커서가 0이고 글이 있으면 맨 끝) |
| 191-201 | 프레임 안(`window.parent !== window`)이면 `a.ago, .head-anchor, .share-link` 클릭 때 부모 `history`에 `pushState`(`history.state`가 없을 때, `{startPath}`)나 `replaceState`로 주소를 맞춘다 |

### 2.2 `yona.Csrf.js`

요청 위조 방지 토큰을 같은 출처로 가는 **상태 변경 요청**에 싣는다(R-02, v1.6에는 없다). 안전한 메서드(`GET`, `HEAD`, `OPTIONS`, `TRACE`)에는 싣지 않는다.

| 대상 | 방식 | 근거 |
|---|---|---|
| `XMLHttpRequest` | `open`을 가로채 방식과 출처를 기억하고 `send` 직전에 `X-XSRF-TOKEN` 헤더 | `yona.Csrf.js:28-41` |
| `fetch` | 래퍼가 헤더를 더한다(이미 있으면 안 덮음) | `:43-58` |
| 폼 제출 | `submit` 캡처 단계 처리기와 `HTMLFormElement.prototype.submit` 래퍼가 숨은 `input[name=_csrf]`를 더한다. `method`가 GET이거나 `action`이 다른 출처면 안 한다 | `:60-83` |
| 토큰 원본 | 쿠키 `XSRF-TOKEN`(서버가 항상 심음, `HttpOnly` 아님). 쿠키가 없으면 아무것도 안 싣는다 | `:15-18`, `CsrfSupport.kt:33-39` |

서버가 거절하면 403이다(`shell.md` 11절). 시험 `src/test/js/csrf.test.mjs`, `CsrfProtectionTest.kt`.

### 2.3 `$yona`(`yona.Common`, `yona.Common.js`)

전역 `$yona`, `yona.Common`. 공개 API(`yona.Common.js:764-795`):

| 함수 | 동작 |
|---|---|
| `setScriptPath`, `createNamespace`, `loadModule`, `loadScript` | 화면 모듈 등록. `loadModule("issue.List")`는 `yona.issue.List`가 이미 등록되어 있으면 `new`나 `init()`로 실행하고, 없으면 `<scriptPath>service/yona.<이름>.js`를 비동기 로드 후 최대 3번 다시 시도한다(F14, 그 디렉터리는 비어 있음). 호출 때마다 `[data-bs-toggle=popover]`를 초기화한다 |
| `showAlert`, `alert` | `yona.ui.Dialog("#yobiDialog")`로 알림창. 메시지는 `nl2br` 후 **HTML로 넣는다**. 닫힌 뒤 콜백 |
| `confirm(메시지, 콜백, 설명, {aButtonLabels, aButtonStyles})` | 확인창. 기본 단추 `button.cancel`, `button.confirm`. 콜백이 `nButtonIndex`를 받고 `false`를 돌려주면 닫지 않는다 |
| `ajaxConfirm(메시지, ajax옵션)` | 확인(두 번째 단추)이면 `yona.http.ajax` |
| `notify(메시지, 밀리초, 제목)` | `yona.ui.Toast("#yobiToasts")`로 구석 알림. 제목이 있으면 `<strong>제목</strong><br/>메시지`. 시간이 없거나 0이면 눌러서 닫을 때까지 남는다 |
| `sendForm(옵션)` | 숨은 폼 없이 `fetch`로 작은 폼 전송(3.3절) |
| `render(mustache, 자료)`, `renderElement` | Mustache로 HTML 조각을 만들어 jQuery 객체로 돌려준다. `{{x}}`는 `< > " '`만 이스케이프(jquery.tmpl 호환) |
| `tmpl(템플릿, 자료)` | `${키}` 치환(이스케이프 없음) |
| `escapeHtml`, `htmlspecialchars`, `nl2br`, `getTrim`(모든 공백 제거) | 문자열 도우미 |
| `xssClean(html)` | 표준 `Sanitizer`/`setHTML`이 있으면 그것으로(금지 요소 목록 + 링크의 `data:` 제거 + 체크 상자만 남기는 `input` 정리), 없으면 `Filter().defence`(옛 필터). 마크다운 출력의 마지막 관문 |
| `getContrastColor`, `getHexColor`, `formatFileSize`, `isImageFile` | 색과 파일 크기(`1.50 Kb`처럼) 도우미 |
| `isComposingKey`, `isEnterKey`, `isEscapeKey`, `isBackspaceKey` | 키 판별. 한글 조합 중(`isComposing`, `keyCode 229`, `key "Process"`)은 제외 |
| `showPopover`, `hidePopover`, `showTab` | Bootstrap 5 `Popover`, `Tab` 감싸기 |

### 2.4 `Messages()`와 `/messages.js`

`GET /messages.js`(`JsMessagesController.kt:276`, 어노테이션 없음, 익명 가능)는 요청 언어의 메시지 전체를 JS 객체로 담은 `Messages=(function(){...})()` 스크립트이다. `Content-Type: application/javascript`, `Vary: Accept-Language`, `Cache-Control: no-cache`. 동작은 play-jsmessages와 같다: 키가 없으면 키 그대로, 키 배열이면 처음 있는 키, `{0}` `{1}`은 **처음 나오는 하나만** 치환, 메시지 파일의 `''`는 `'`, 끝 공백은 제거. 영어 기본 위에 요청 언어를 덮어쓰므로 `ja`, `ru`, `uz`에 없는 키는 영어로 나온다. 시험 `JsMessagesTest.kt`.

### 2.5 flash 메시지

서버가 리다이렉트 때 `RedirectSupport`로 심은 값을 `FlashMessageTranslator`가 번역(키가 아니면 글 그대로)하고 HTML 이스케이프한 뒤(`& < > " '`, 작은따옴표는 `&#x27;`), 다음 화면 `scripts.html:129-136`이 **JS 문자열 리터럴 안에 `[(...)]`로 그대로 넣는다**. 키 `title`이 있으면 확인창(제목, `description`을 설명으로), 나머지는 알림(5초)이다. 줄바꿈이나 역슬래시는 이스케이프되지 않는다(v1.6도 Twirl HTML 이스케이프만, 그런 값이 실제로 있는지는 확인하지 못함). 순서는 심은 순서(`RedirectSupport.ORDER`). 시험 `FlashMessageTranslatorTest.kt`, `FlashCarryOverTest.kt`.

## 3. 요청 도우미

### 3.1 `yona.http` (`esm/http.js`)

`$.ajax`, `$.get`, `$.post`, `$.getJSON`과 같은 호출 모양을 `fetch`로 구현. 같은 출처에는 `X-Requested-With: XMLHttpRequest`, `Accept`는 `dataType`에 따라(`json`은 `application/json, text/javascript, */*; q=0.01`, 없으면 `*/*`). 데이터는 `jQuery.param` 규칙. 2xx와 304가 성공. 결과 객체는 `done/fail/always/then/catch`를 가진 jqXHR 모양이고 `await`할 수 있다(거절 값은 `xhr`). 오류의 `xhr.readyState`는 **연결 실패에서도 4**이다(`http.js:76-77,170`, F2). 취소(`abort`), `async:false`, `jsonp`, `xhrFields`는 지원하지 않는다. 시험 `http.test.mjs`.

### 3.2 `yona.Request` (`esm/Request.js`)

`[data-request-method]` 요소(`data-request-uri`, 없으면 `href`)에 클릭과 **Enter 키(keydown)** 처리기를 단다(문서 준비 때 한 번, 나중에 들어온 요소는 `yona.Request.bind(루트)`). `GET`을 건 `<a>`는 보통 링크라 건너뛴다. 클릭이 기본 동작과 전파를 막고 `fetch(방식, 주소, {X-Requested-With, cache:no-store})`(CSRF는 `yona.Csrf.js`). 응답이 2xx면 `204`+`Location`이면 그곳으로 이동, 아니면 `location.reload()`. **실패(4xx, 5xx, 연결)는 사용자에게 알리지 않는다**(v1.6 그대로). 요소에 `request:before`(취소 가능), `request:load`(취소하면 이동 안 함), `request:error` 이벤트를 보낸다. 사용처: 업데이트 알림 숨기기, 댓글 삭제 확인 "예", 각 화면의 상태 변경 단추. 시험 `requestas.test.mjs`.

### 3.3 `yona.Htmx`(`common/yona.Htmx.js`)와 `sendForm`

htmx 전역 설정(jquery-pjax 대체, D-96): `historyCacheSize=0`과 `refreshOnHistoryMiss=true`(뒤로 가기는 서버에서 새로 읽음), `includeIndicatorStyles=false`, `allowEval=false`, `attributesToSettle=[]`(교체 뒤 속성 되돌림 끔). 요청 시점에 폼의 현재 `action`을 `detail.path`로 쓴다(풀 리퀘스트 목록 탭). 요청 시작과 끝에 `NProgress`. **응답에 문자열 `list-container`가 없으면 교체하지 않고** `location.assign(요청 경로)`로 일반 이동한다(로그인 화면 응답 등). `responseError`, `sendError`, `timeout`도 일반 이동. `yona.Htmx.submit(폼)`은 `requestSubmit()`(htmx가 가로채도록)이고 없으면 jQuery `.submit()`. 서버는 `HX-Request: true`로 목록 조각을 구분한다(`AjaxRequests.isHtmx`). 시험 `htmx.test.mjs`, `HtmxListSwapTest.kt`.

`$yona.sendForm({sURL, htOptForm:{method, enctype}, htData, fOnLoad, fOnError, sDataType})`: `&amp;` 같은 문자 참조를 풀고, 방식 기본 POST(옵션 객체가 있고 `method`가 없으면 GET), GET은 질의 문자열, POST는 urlencoded, `multipart/form-data`면 `FormData`. 헤더 `X-Requested-With`. 사용처: 파일 삭제(`_method=delete`), 관심 등록 등. 시험 `sendform.test.mjs`.

## 4. `esm/*` 공통 모듈

### 4.1 진입점 `esm/index.js`와 `namespace.js`

모든 레이아웃이 `common/scripts`로 `index.js` 하나만 모듈로 불러온다(`scripts.html:54`). 이 파일이 `yona.esm = {ready, register, ensureNamespace}`를 만들고 아래 모듈을 이 순서대로 `import`하여 전역 `yona.*`에도 등록한다: `ui/Elevator`, `ui/Favicon`, `ui/FormValidator`, `http`, `util`, `dates`, `service/temporarySaveHandler`, `Request`, `Attachments`, `Files`, `Mention`, `Markdown`, `Pagination`, `ShortcutKey`, `ui/Dropdown`, `ui/Typeahead`, `ui/Dialog`, `ui/Toast`, `ui/Tabs`, `OriginalMessage`. 마지막에 `yona.esm.ready=true`와 문서 이벤트 `yona:esm-ready`. 모듈 스크립트는 문서 분석 뒤 `DOMContentLoaded` 앞에 실행되므로 `$(function(){})` 안의 코드는 안전하고, **분석 중 바로 도는 인라인 스크립트는 `yona.esm.ready`나 이벤트를 기다려야 한다**. `register(path, value)`는 점으로 이은 이름의 없는 단계를 `{}`로 만들고 이미 있는 값은 바꾸되 상위는 지우지 않는다(시험 `esm.test.mjs`).

레이아웃과 `common/scripts`가 별도 `<script type=module>`로 부르는 모듈: `Tabs.js`(`.nav-tabs`의 `li`가 아닌 `ul.nav-subtab`, `ul.lst-stacked`에서 `shown.bs.tab`이면 눌린 `li`만 `active`), `KeyControl.js`(전역 `_tab`, `_untab`: textarea의 Tab 들여쓰기), `Clipboard.js`(`yona.Clipboard.copy(글, 모달?)`: 보안 연결이면 `navigator.clipboard.writeText`, 아니면 숨은 textarea + `execCommand("copy")`, 실패해도 거절하지 않고 `false`), `Usermenu.js`(레이아웃이 부름, `shell.md` 5절).

### 4.2 `esm/ui/*`

| 모듈 | 계약 |
|---|---|
| `Dialog` | `new yona.ui.Dialog("#yobiDialog")`: 원본 요소를 복제해 Bootstrap 모달로 쓰고, 단추는 `button.ybtn`. 사용자 지정 라벨이면 마지막 단추만 `ybtn-primary`(스타일이 없을 때). 표시 뒤 마지막 단추에 초점. 닫으면 메시지 비움. `Esc`와 바깥 클릭 닫기는 Bootstrap 모달 기본 |
| `Toast` | `push(메시지, 밀리초)`: 컨테이너 맨 위에 쌓고 불투명도 1, 시간이 있으면 그 뒤 투명해졌다 `transitionend`에 지움. 누르면 지워진다. `clear()` |
| `Tabs` | `.nav-tabs[id]`의 선택 탭 순서를 `localStorage["yobitab-<id>"]`에 저장하고 다음에 복원(주소에 `tab=`이 있으면 복원 안 함, 저장소를 못 쓰면 조용히). v1.6의 복원 결함과 목록 여럿의 id 혼동을 고쳤다(D-111) |
| `Dropdown` | `.btn-group[data-name]` 안 `li` 클릭으로 `.d-label`, 숨은 입력, `active`, 변경 콜백 처리. `data-value`가 없는 항목은 무시 |
| `Typeahead` | 입력란 자동 완성(`ul.typeahead.dropdown-menu`): `keyup`마다 서버(`sActionURL`)나 `source`로 후보, 최대 10개, 방향키, Enter(키를 뗄 때)와 Tab(누를 때) 선택, `Esc`는 닫기만, 한글 조합 중 Enter는 선택하지 않는다 |
| `Select2` | `data-toggle="select2"` 선택 상자를 4.x로 꾸미고 `data-format`별 모양 |
| `Calendar` | `data-toggle="calendar"` 입력란에 달력(`YYYY-MM-DD`). 처음 초기화는 `change`를 내지 않는다(R-146) |
| `FormValidator` | `required`, `min_length[n]`, `max_length[n]`, `matches[필드]`, `valid_email`, `callback_이름`. 한 필드에서 처음 실패한 규칙 하나만 오류. 양식의 `onsubmit`에 연결 |
| `Elevator` | 위/아래 스크롤 단추(`jq-top`, `jq-bottom`, 크기 클래스 `jq-sml`, `jq-mid`, `jq-big`) |
| `Favicon` | 파비콘에 글자 배지(기본 `#d00` 배경, 흰 글자)를 그린다 |

### 4.3 `Files`, `Attachments`, `Pagination`, `Mention`, `OriginalMessage`

- `yona.Files`(`Files.js`): 업로드와 삭제와 목록 조회 요청 계약은 `shell-common-fragments.md` 1.2절. `Files.init({sListURL, sUploadURL, maxFileSize})`는 `scripts.html:104-108`이 전역으로 부른다. 끌어놓기는 `File` API가, 붙여넣기는 `document.onpaste` 지원이 있으면 켜진다(둘 다 사실상 항상 참, R-140).
- `yona.Attachments`(`Attachments.js`): 목록과 textarea 링크 삽입, 임시 파일 id 모으기.
- `yona.Pagination.update(대상, 전체쪽수, {url, current, paramNameForPage:'pageNum', submit})`: `ul.page-nums`에 이전(`LEFT`), 쪽 번호 입력(`input[type=number][name=pageNum]`, 범위를 벗어나면 보정, Enter로 이동), `/ 전체쪽수`, 다음(`RIGHT`)을 그린다. **이전, 다음을 그릴 때마다 `yona.ShortcutKey.setKeymapLink`로 `LEFT`, `RIGHT`를 그 링크로 등록하고, 해당 방향 링크가 없으면(`href` 빈 값) 등록을 해제한다**(`Pagination.js:311-315`). htmx 목록 안에서는 `a[list-page]`에 `hx-boost`를 붙인다. 사용처(13개 템플릿과 3개 목록 스크립트)는 `site/post-list`, `site/user-list`, `site/issue-list`, `site/project-list`, `project/list`, `organization/list`, `organization/pullrequest-list`, `pullrequest/partial-search`, `search/partial-*`(4), `user/files`, `service/issue.List.js`, `board.List.js`, `review.List.js`.
- `yona.Mention({target, url})`: `textarea`를 `<text-expander keys="@ : #" data-mention-url=url>`로 감싼다. `@` 사용자와 `#` 이슈는 서버(`url?query=&mentionType=`)에서, `:` 이모지는 내장 목록(60여 개). 후보 10개, 질의 20자 제한, 사용자 검색은 마지막 입력 300ms 뒤. 이벤트는 문서 전체에서 캡처 단계 한 번(복제한 입력란도 동작).
- `yona.OriginalMessage.hide(요소들)`: 본문에서 `---...---` 구분선 줄 이후를 숨기고 `...` 단추로 토글.
- `findNotiReceiversHandler($textarea, url)`(`ReceiverList.js`): 입력 1초 뒤(디바운스) `POST url` JSON `{comment, parentCommentId}`로 알림 받을 사람을 묻고 `.notification-receiver-list`에 최대 10명 + `+N`을 그린다. 실패하면 `[상태] 메시지\n\nRefresh the page!` 알림창.
- `esm/dates.js`: `yona.dates.isValidDate`(ISO 8601과 RFC 2822 범위 검사, moment 호환), `fromNow`(상대 시각, en/ko/ja/ru/uz), `esm/util.js`: `yona.util.debounce`, `rafThrottle`.

### 4.4 `esm/LoginDialog.js`, `webauthn.js`

`shell-common-fragments.md` 1.7절. `YonaWebAuthn`(`webauthn.js`)은 서버 JSON의 base64url을 `ArrayBuffer`로 바꿔 `navigator.credentials`에 넘기고 결과를 JSON 글자로 돌려준다(`supported()`, `authenticate()`).

### 4.5 `temporarySaveHandler`, `twoColumnMode`, `showSubtask`

- `temporarySaveHandler($textarea, init=true)`: 입력(`keyup`) 마지막 5초 뒤 값을 `localStorage[<경로>]`에 저장(수정 댓글은 `<경로>-last-comment-update-draft-<댓글 id>`). 시각 표 `yona.draftTimes`로 마지막 저장 1시간이 지나면 지운다(페이지 열 때와 5분마다). 값이 비면 키를 지운다. 제출 때 `removeCurrentPageTemprarySavedContent()`, `cancelAllDraftTimers()`로 지운다. 저장되면 `.editor-notice-label`에 `Draft saved`(영문 고정).
- `twoColumnMode`, `showSubtask`: `shell-common-fragments.md` 1.10절.

### 4.6 `esm/Markdown.js`

`yona.Markdown.init({sMarkdownRendererUrl})`, `enableMarkdown(선택자)`, `render(대상, 본문)`. `marked` 옵션 `gfm`, `tables`, `smartLists`, `langPrefix:''`, 코드 강조는 `hljs.highlight`, 출력은 `$yona.xssClean`. 서버 변환(미리보기와 자동 링크)은 `POST /markdown/{owner}/{project}` JSON `{body, breaks}`로, 응답 HTML을 `welTarget.html()`로 넣고 `pre code`를 강조한다(`breaks`는 서버가 쓰지 않는다). 실패하면 아무 일도 하지 않는다. 접근 검사는 없다(F13).

## 5. 전역 단축키 전체표

### 5.1 엔진 `yona.ShortcutKey`(`esm/ShortcutKey.js`)

| 항목 | 계약 | 근거 |
|---|---|---|
| 이벤트 | `window`의 `keydown` 하나(jQuery). 키 이름 문자열로 핸들러 표를 찾는다 | `ShortcutKey.js:59-80` |
| 키 이름 | `ALT+CTRL+SHIFT+키` 순서로 만든다. `Meta`(⌘)는 `CTRL`로 센다. 키: `KeyboardEvent.key`가 영문자면 대문자, 라틴이 아닌 배열(한글, 러시아어)의 글자는 물리 키(`KeyF` -> F), 숫자와 기호는 물리 키(`Digit`, `Slash`), `Enter` `ArrowLeft`(`LEFT`) `ArrowRight`(`RIGHT`) `Escape`(`ESC`) 등은 이름, `F1~F12`. 숫자 패드는 제외. 입력기 조합 중(`isComposing`, `keyCode 229`, `Process`)은 무시 | `:162-224` |
| 등록 | `attach(키 또는 {키:함수})`, `detach(키)`, `setKeymapLink({키: 주소})`. **같은 키는 핸들러 하나이며 마지막 등록이 이긴다.** 등록 키는 정규화(대문자, 공백 제거, 수정키 정렬) | `:124-155,227-241` |
| `setKeymapLink` | 값이 있으면 핸들러를 달고(입력란이 아닐 때 `document.location.href = 주소`), **값이 비어 있으면 그 키의 등록을 해제**한다 | `:253-271` |
| 핸들러에 주는 정보 | `{weEvt, welTarget, sTagName, sKeyInput, bFormInput}`. 대상은 `composedPath()[0]`(Shadow DOM 안 `<title-input>`의 실제 입력란) | `:90-121` |
| 입력란 제외 | `INPUT`, `TEXTAREA`만(`bFormInput`). **`setKeymapLink` 핸들러만** 이 값을 보고 제외한다. `attach`로 단 핸들러는 직접 `bFormInput`을 검사해야 한다. `SELECT`, `contenteditable`은 제외되지 않는다 | `:42,99-121,253-263` |
| 예외 | 핸들러의 예외는 삼킨다 | `:110-117` |
| 수명 | `beforeunload`에서 스스로 `destroy`(처리기 해제, 표 null). 이탈 확인을 취소해도 되살아나지 않는다(F3) | `:59-70,273-277` |

### 5.2 전역과 맥락과 화면 키

"등록 순서"는 같은 키가 겹칠 때 누가 이기는지를 정한다. 본문의 `$(document).ready` 콜백은 `common/scripts`의 콜백보다 **먼저** 등록되므로(본문이 문서에서 앞) 같은 키를 나중에 등록하는 `common/scripts`가 이긴다. 프로젝트 맥락 키는 `project/menu`(`$(document).ready` 안, 본문 앞 조각)와 `project.Global`이 등록한다.

| 범위 | 키 | 동작 | 조건과 등록 위치 | 겹침 |
|---|---|---|---|---|
| 전역 | `A` | `/user/issues` | 모든 화면, `scripts.html:99-101`(마지막) | 코드 이력의 `A`(이전 쪽)를 **덮는다** |
| 전역 | `U` | `/<세션 로그인 ID>` | 로그인 세션이 있을 때, `scripts.html:93-97` | |
| 전역 | `F`, `ㄹ` | 사용자 패널 열기 + 검색란 초점 | 소문자 `f`/`ㄹ`, `metaKey` 없음, 초점 없음, 패널 목록이 읽힌 뒤, `keypress`(`ShortcutKey` 밖), `Usermenu.js:30-36` | 단축키 표와 별개 |
| 전역 | `CTRL+ENTER`(⌘) | 입력란에서 가장 가까운 폼 제출(jQuery `.submit()`) | `INPUT`, `TEXTAREA`일 때, `scripts.html:88-92` | 화면의 `CTRL+ENTER`는 없음 |
| 전역(브라우저) | `ALT+S`(Mac `CTRL+ALT+S`) | 검색 입력 초점 | HTML `accesskey="S"`, `navbar.html:69` | |
| 한 줄 댓글 | `CTRL|⌘+ENTER`, `Esc` | 제출, 닫기 | `keydown`/`keyup` 직접 처리, `SubComment.js` | `ShortcutKey` 밖 |
| 프로젝트 맥락 | `H` | 프로젝트 홈 | `project/menu.html:76`(항상) | |
| | `B` `C` `I` `M` | 게시판, 코드, 이슈(`state=open`), 마일스톤 | 해당 `menuSetting`이 켜졌을 때(`:77-86`) | |
| | `P` | 코드 주고받기(`view.pullRequestPath`) | `menuSetting.pullRequest and vcs.pullRequests`(`:87-88`) | |
| | `Q` | 프로젝트 설정 | `view.settingShortcutEnabled`(= 프로젝트 관리자, `ProjectViewContext.kt:161`)(`:90-91`) | |
| 화면 | `N` | 새 이슈(이슈 목록 `issue/list.html:33`, 이슈 보기 `:319`는 `menuSetting.issue`일 때), 새 글(`board/list:76`, `board/view:170`은 `menuSetting.board`일 때), 새 마일스톤(`milestone/list:156`) | | |
| | `L` | 목록(이슈 보기, 게시글 보기, `listUrl`) | | |
| | `E` | 수정(이슈 `issue.canUpdate`, 게시글 `post.canUpdate`일 때만 등록) | | |
| | `LEFT`, `RIGHT` | 이전, 다음 쪽 | `Pagination.update`가 그릴 때마다 등록/해제 | 쪽이 없으면 해제 |
| | `CTRL+A` | 일괄 변경 영역에서 전체 선택(`preventDefault`, 입력란이 아닐 때) | `issue.MassUpdate.js:124` | 브라우저의 전체 선택을 막음 |
| | `A`, `S` | 코드 이력의 새쪽(`newerUrl`), 옛쪽(`olderUrl`) | `code/history.html:118-128`, 해당 URL이 있을 때 | `A`는 전역에 덮인다 |
| | `CTRL+SHIFT+ENTER` | 이슈 댓글 입력에서 동적 댓글 단추 클릭 | `issue.View.js:492`, `welTarget`이 댓글 textarea일 때 | 동작한다(F4를 고쳤다. 여러 보조키 조합을 정규화한다, 2026-11-11) |

`P`와 `Q`의 도움말 조건이 실제와 다른 곳은 F5. `←`, `→`는 도움말에서 `List` 화면에만 나오지만 실제로는 `Pagination`을 쓰는 모든 목록에서 동작한다. 단축키가 시험으로 확인되는 범위: 엔진 `shortcutkey.test.mjs`, `usermenu.test.mjs`(`F`가 아닌 열기와 닫기), 프로젝트 `Q` 조건 `ProjectLayoutTest.kt:407-417`. `F` 키 자체와 `A`, `S` 덮어쓰기, 도움말 내용은 자동 시험이 없다(위키 `ui-shortcuts.md` 10절의 수동 확인).

## 6. 브라우저 저장소 키

| 저장소와 키 | 값 | 쓰는 곳 |
|---|---|---|
| `localStorage.shallWeOpenLeftNavigation` | `"true"`/`"false"` 핀 사이드바 | `layout.html:46,105,108`, `sidebar.html:79` |
| `localStorage.sidebarActiveMenu` | `myOrganizationList`, `myProjectList`, `myRecentIssueList` | `sidebar.html:50,64-72` |
| `localStorage["yobi-intro"]` | 홈 환영 표가 보이는지 `"true"`/`"false"` | `notifications.html:70,73` |
| `localStorage["yobitab-<탭목록 id>"]` | 선택한 탭 순서 | `ui/Tabs.js` |
| `localStorage.useTwoColumnMode` | 2단 보기 켬 | `twoColumnMode.js`, `analytics.html:63` |
| `localStorage.showSubtasksAlways` | 하위 이슈 항상 보임 | `showSubtask.js` |
| `localStorage[<location.pathname>]`, `<경로>-last-comment-update-draft-<id>`, `yona.draftTimes` | 편집기 초안과 저장 시각 | `temporarySaveHandler.js` |
| 쿠키 `XSRF-TOKEN`(서버가 심음) | CSRF 토큰 | `yona.Csrf.js:16` |

쿠키를 스크립트가 쓰는 곳은 이 담당에 없다. `sessionStorage`와 IndexedDB는 쓰지 않는다.

## 7. 폴링과 지연 로딩

| 대상 | 방식 |
|---|---|
| 사용자 패널 목록 | 문서 준비 때 한 번 `GET /user/usermenuTabContentList`(패널이 있을 때만). 열 때마다 `GET /user/favoriteProjects`로 별 동기화 |
| 알림 더 보기 | 클릭 때 `GET /notification` |
| 수신자 목록 | 입력 1초 뒤 `POST` |
| 임시 저장 초안 | 입력 5초 뒤, 만료 점검 5분마다 |
| 업데이트 확인(서버) | 요청 화면에 폴링은 없음(`SiteMaintenanceController.update`가 요청 안에서 확인) |

주기적 폴링은 이 담당 화면에 없다(알림 읽지 않은 수 폴링은 새 UI 설계 항목이며 지금은 없다).

## 8. 이 파일의 발견 사항

`shell.md` 17절의 F2, F3, F4, F5, F7, F8, F13, F14, F19, F20, F21과 관련이 있다. 이 파일에서 추가로 확인한 것은 다음이다.

| 번호 | 내용 | 근거 |
|---|---|---|
| shell-F25 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `ShortcutKey.setKeymapLink` 핸들러만 입력란 제외를 하고 `attach` 핸들러는 직접 검사해야 한다. `CTRL+A`(일괄 선택)는 `bFormInput`을 검사하지만 `CTRL+ENTER`는 반대로 입력란에서만 동작한다. 같은 엔진 안에서 규칙이 호출 방식에 따라 다르다 | `ShortcutKey.js:253-263`, `issue.MassUpdate.js:124-130`, `scripts.html:88-92` |
| shell-F26 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `common/scripts.html:157-166`의 외부 링크 판정이 `^[^./#]`라 `mailto:`, `tel:`, `javascript:` 링크에도 `target=_blank`가 붙는다. 같은 출처의 절대 주소(`http://같은호스트/...`)만 예외 | `scripts.html:160-165` |
| shell-F27 **[검증: 수정함(A-464, 2026-11-11)]** | `$yona.alert`와 `$yona.notify`는 메시지를 HTML로 삽입한다(`.html(nl2br(...))`, `innerHTML`). 호출부가 사용자 데이터를 넘기면 스스로 이스케이프해야 한다(`$yona.escapeHtml`이 따로 있음) | `Dialog.js`, `Toast.js`, `yona.Common.js:782` |
