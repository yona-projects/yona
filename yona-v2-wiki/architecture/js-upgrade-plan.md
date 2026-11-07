---
title: JS 라이브러리 최신 버전 업그레이드 계획
type: architecture
sources:
  - ../src/main/resources/static/assets/javascripts/lib/
  - ../src/main/resources/templates/common/scripts.html
  - ../build-tools/minify-js.sh
  - bs-migration-harness.md
  - decisions.md
updated: 2026-11-07
---

# JS 라이브러리 최신 버전 업그레이드 계획

선생님 요청(2026-11-07)으로 세운 **계획**이다. 코드는 아직 바꾸지 않았다. 결정이 필요한 곳은 7절에 모았고, 결정이 나면 `decisions.md`에 선택지, 권장안과 이유, 결정자를 적고 시작한다.

## 0. 구현 현황 (2026-11-07 갱신)

**첫 묶음 완료**(`D-91`, `A-392`): jQuery 3.7.1(주 화면 번들과 첫 설정, 마이그레이션 화면 3개), jQuery UI 1.14.2 효과 묶음, autosize 6.0.1, NProgress 0.2.0(JS), Popper 2.11.8, Tippy.js 6.3.7, video.js 7.21.2, moment 2.31.0. 확인: `JsLibraryVersionsTest`, 전 화면 순회 비교(`jq331`과 `jqall`, 새 문제 0), Playwright 직접 확인 22개 항목.

**Yona가 v1.6에서 고친 벤더 파일**(v1.6 `git log`와 공식 파일 대조): 아래는 새 버전으로 덮어쓰면 수정이 사라지므로 **패치 이식이 필요**해 이번에 올리지 않았다.

| 파일 | 고친 내용(v1.6 커밋 제목) |
|---|---|
| jquery.pageslide | 2단 보기 도입, 자연스러운 로딩 효과 |
| At.js(와 caret) | IME 버그 수정, 2단 보기용 monkey patch |
| jquery.form | FileUploader의 XHR 업로드(MSIE) 수정 |
| jquery.scrollTo | 최근 댓글로 스크롤 |
| Viewer.js | 렌더 목록 transform 이동 방지(공식과 2글자 차이) |
| Pikaday | 마감일 추가, import 경로 이동 |
| js-xss | XSS 방어 수정 5건 |
| select2 | 공식과 860자 차이 |
| marked | 사용자 지정 패치(37건) |
| mention.js | Yona 자체 코드(10건) |

jquery-pjax는 공식 2.0.1과 같고, jquery.event.drag는 어느 템플릿도 불러오지 않아 쓰이지 않는다(패치도 죽은 코드). clipboard.js는 `yona-common.js` 번들 안에 있고 그 번들(그리고 `yona-lib.js`)이 어느 JVM으로도 byte 단위로 재현되지 않아(크기는 같고 변수 이름만 다름) 올리지 않았다. `yona-layout.js`는 재현되어 다시 만들었다.

## 1. 현재 상태

화면이 실제로 읽는 위치는 `src/main/resources/static/assets/`이다(`public/`은 v1.6 원본 사본이라 서빙하지 않는다). 이 저장소에는 Node와 npm 파일이 없고(`components` 저장소 원칙) 라이브러리는 **파일을 직접 복사해 커밋**하는 방식이다. 번들 세 개(`yona-layout.js`, `yona-common.js`, `yona-lib.js`)는 `build-tools/minify-js.sh`가 Closure Compiler(`compiler.jar`)로 합쳐 만든다.

| 라이브러리 | 현재 | npm 최신 | 쓰는 곳 | 변화의 크기 |
|---|---|---|---|---|
| jQuery(주 화면, `yona-layout.js` 번들) | 3.3.1 | 4.0.0 (3.x는 3.7.1) | 모든 화면 | 3.7.1은 작음, 4.0은 큼 |
| jQuery(마이그레이션, 첫 설정 화면) | 1.9.0 | 위와 같음 | `migration-layout`, `welcome/secret`, `welcome/restart` | 세 화면뿐 |
| jQuery UI(효과만 담은 맞춤 빌드) | 1.10.4 | 1.14.2 | 효과 | 작음 |
| Bootstrap | 5.3.8 | 5.3.8 | 전체 | **이미 최신** |
| Popper(툴팁용 단독) | 2.4.2 | 2.11.8 | tippy | 작음 |
| Tippy.js | 6.x | 6.3.7 | 이슈 작성 | 작음 |
| select2 | 3.4.5 | 4.1.0 | 7개 파일 | **큼**(마크업과 API가 다름) |
| moment(`with-langs`) | 2.4.0 | 2.31.0 | 4개 파일 | 작음 |
| marked | 0.8대(2021년판) | 18.x | **서버 렌더(Nashorn)와 브라우저 미리보기**(`yobi.Markdown.js`) | **매우 큼**(아래 "marked는 세 벌이다") |
| highlight.js | 9.11.0 | 11.x | 코드 강조 1개 파일 | 중간(API 이름이 바뀜) |
| Ace | 1.x(파일 174개) | 1.45.2 | 코드 보기의 편집기 | 작음 |
| Pikaday | 1.x | 1.8.2 | 날짜 선택 2개 파일 | 작음 |
| underscore | 1.4.4 | 1.13.8 | 1개 파일 | 작음 |
| video.js | 7.8.2 | 7계열 7.21.2, 8계열 8.24.1 | 영상 보기 1개 템플릿 | 7.x는 작음, 8은 중간 |
| Viewer.js | 1.6.1 | 1.15.2 | 이미지 보기 | 작음 |
| autosize / NProgress / clipboard | 4.0.2 / 0.1.6 / 2.0.0 | 6.0.1 / 0.2.0 / 2.0.11 | 댓글 / 진행 표시 / 복사 | 작음 |
| js-xss | 1.0.x | 1.0.17 | 입력 정리 | 작음 |
| At.js(멘션) | 1.5.3 | 1.5.4(유지 중단) | 멘션 | 거의 없음 |
| `vendor.js`(Angular 1.x와 lodash 묶음) | 1.5.8 | 없음(AngularJS는 지원 종료) | **마이그레이션 화면 하나** | 업그레이드 불가 |

### jQuery 4 지원 여부 (2026-11-07 조사, 같은 날 정정)

**정정**: 계획서 초안은 `.bind`, `.unbind`를 jQuery 4의 제거 대상으로 세어 비용을 크게 잡았다. 공식 4.0 업그레이드 가이드(jquery.com/upgrade-guide/4.0)에는 `.bind`, `.unbind`, `.delegate`가 제거 목록에 없다(지원 중단 상태로 남음). 아래는 공식 제거 목록으로 다시 센 결과이다.

**공식 제거 목록**: `jQuery.camelCase`, `cssProps`, `isArray`, `isFunction`, `isNumeric`, `isWindow`, `fx.interval`, `nodeName`, `now`, `parseJSON`, `trim`, `type`, `unique`, 문서에 없던 `fn.push`, `fn.sort`, `fn.splice`, `.toggleClass(불리언)` 형식, `opacity` CSS 훅, 인자가 있는 옛 Sizzle 사용자 정의 의사 선택자. **동작 변경**: `dataType: "json"`의 JSONP 자동 승격 제거, 같은 도메인 스크립트의 자동 실행 제거, `focusin`과 `focusout` 보정 제거(`focus`와의 순서가 달라질 수 있음), 단위 없는 숫자의 `px` 자동 추가 범위 축소, `.css("opacity")`의 분리된 요소 값.

**앱 코드(13개 파일)**: `$.trim` 21곳, `$.parseJSON` 6곳, `$.isNumeric` 1곳이다. `String.prototype.trim`, `JSON.parse`, `Number.isFinite`로 치환하면 된다.

**남기는 현재 플러그인(옛 사본)의 제거 대상 API 사용**: pjax(`trim` 1, `isArray` 2, `isFunction` 2, `type` 1), jquery.tmpl(`trim` 1, `isArray` 4), dynatree(`trim` 3, `isArray` 2, `parseJSON` 1), event.drag(`isFunction` 2), At.js(`isArray` 2), jquery.form(2), jquery.cookie(2), jquery.search(1), jquery-viewer(1), jquery.elevator(1), select2 3.4.5(`isFunction` 11, `isArray` 3, `trim` 3). 모두 **치환 몇 줄**이다.

**올릴 새 버전의 실제 배포 파일을 같은 목록으로 검사한 결과** (npm 선언과 함께):

| 라이브러리(올릴 버전) | npm의 jQuery 선언 | 파일의 제거 API 사용 | jQuery 4 판단 |
|---|---|---|---|
| jQuery UI 1.14.2(효과) | `>=1.12 <5` | 없음 | 지원 |
| jquery-validation 1.22.1 | `^4.0` | 없음 | 지원(선언까지 있음, 앱은 쓰지 않는 것으로 보임) |
| jquery-form 4.4.0 | `>=1.7.2` | 없음 | 지원으로 보임 |
| select2 4.1.0 | 선언 없음 | 없음 | 지원으로 보임(올릴 때 확인) |
| Viewer.js 1.15.2(jquery 래퍼) | 선언 없음 | 없음 | 지원으로 보임 |
| jquery.scrollTo 2.1.3 | `>=1.8` | 없음 | 지원으로 보임 |
| jquery-placeholder 2.3.1 | `*` | 없음 | 지원으로 보임(제거 후보) |
| jquery-pageslide 2.0.2 | 선언 없음 | 없음 | 지원으로 보임 |
| **jquery-pjax 2.0.1** | 선언 없음 | `trim` 1, `isArray` 2, `isFunction` 2, `type` 1 | **미지원**(치환 6줄, 전 화면 이동 기능이라 직접 시험) |
| **At.js 1.5.4** | **`<4.0.0`(제외 선언)** | `isArray` 2 | **미지원**(치환 2줄과 시험, 2022년 이후 갱신 없음) |
| **jquery.event.drag 2.2.2** | 선언 없음 | `isFunction` 2 | **미지원**(치환 2줄) |
| **magnific-popup 1.2.0** | 선언 없음 | `trim` 1, `isArray` 1, `isFunction` 5, `type` 5, `parseJSON` 2 | **미지원**(앱 코드에서 호출이 없어 정리 후보) |
| Popper, Tippy, clipboard, NProgress, autosize, underscore, js-xss, Pikaday, Ace, video.js, moment, highlight.js, marked | - | jQuery를 쓰지 않음 | **jQuery 4와 무관** |

**한계**: 이 검사는 API **이름**만 본다. 위 동작 변경(`focusin` 순서, ajax의 `dataType` 처리, `toggleClass`)은 실제로 돌려 봐야 알 수 있어 `jquery-migrate` 4.0.2(`>=4 <5`)로 경고를 모으고 전 화면 순회로 확인한다.

**jQuery 4에 새 버전이 없어 옛 사본을 고쳐 쓰거나 정리해야 하는 것**: jquery.tmpl(후속 없음, 25줄/12파일에서 쓰고 `$.isArray` 4, `$.trim` 1만 치환하면 동작은 같음), jquery.dynatree와 jquery.cookie(앱 호출이 없어 정리 후보), Jcrop(14줄/2파일, 치환할 API 없음), jquery.browser(2줄/2파일, 사용처 제거).

### marked는 세 벌이다 (중요)

`marked.js`는 byte 단위로 같은 파일이 **세 곳**에 있다.

| 위치 | 쓰임 |
|---|---|
| `src/main/resources/markdown/marked.js` | **서버**가 Nashorn(독립 배포판)에서 실행해 README, 이슈, 게시글, 댓글, 알림 메일을 렌더한다(`MarkedEngine`). D-17 되돌림(A-151, A-154)으로 v1.6의 결과를 그대로 내려고 일부러 같은 파일을 쓴다. |
| `src/main/resources/static/assets/javascripts/lib/marked.js` | 브라우저의 `yobi.Markdown.js`(`marked(text, options)` 후 `$yobi.xssClean`) |
| `src/test/resources/marked.js` | `MarkdownRendererTest`의 v1.6 비교 기준 |

그러므로 marked를 올리는 일은 단순한 라이브러리 교체가 아니라 **서버 렌더 결과가 v1.6과 달라지는 일**이다. 또 브라우저만 올리면 같은 글이 미리보기와 저장 뒤 화면에서 다르게 보일 수 있다. 17년 사이에 바뀐 점: `sanitize`, `smartLists`, `tables`, `highlight` 옵션이 없어지거나 확장(`marked-highlight`)으로 옮겨졌고, 렌더러 사용자 정의가 토큰 객체를 받도록 바뀌었으며, 서버의 Nashorn 독립 배포판이 새 문법(ES2020 이상)을 읽는지도 확인해야 한다.

`components` 저장소의 Vue 3와 CodeMirror 6 위젯은 아직 이 저장소에 들어오지 않았고 이미 최신 계열이라 이번 범위 밖이다.

## 2. 원칙

1. **v1.6의 동작과 계약을 바꾸지 않는다.** 마크다운 미리보기, 날짜 검증, 코드 강조의 결과가 달라지면 안 된다(AGENTS.md).
2. **한 번에 하나의 묶음**을 올리고, 묶음마다 시험과 화면 확인을 한 번 한다(Playwright, 작업 단위를 마칠 때).
3. **시험을 먼저 쓴다(TDD).** 아래 3절의 시험을 단계 0에서 만들어 현재 상태에서 통과시킨 뒤 올린다.
4. 내려받은 파일은 **빈 폴더에 따로 받아** 체크섬(npm의 integrity)을 확인하고, 필요한 배포 파일만 복사한다. 받은 폴더의 스크립트는 실행하지 않는다.
5. 지우는 일(쓰지 않는 파일 정리)은 **선생님 확인을 받는다**(정리 결정은 에이전트가 정하지 않는다).

## 3. 먼저 만들 시험과 확인 도구 (단계 0)

| 도구 | 하는 일 |
|---|---|
| `JsLibraryVersionsTest`(신규) | 라이브러리 파일 머리말의 버전을 읽어 **기준 표와 대조**한다. 올리지 않은 라이브러리가 몰래 바뀌거나 올리고 기준을 안 고치면 실패한다(`Hg4jDependencyTest`와 같은 방식). |
| `TemplateScriptReferencesTest`(신규) | 템플릿이 가리키는 `assets/javascripts/...` 파일이 **모두 실제로 있는지**, 서빙되는지 확인한다(파일 이름을 바꾸거나 지울 때 깨지는 것을 막는다). |
| `StaticAssetsTest`, `JsMessagesTest` | 이미 있다. 그대로 통과해야 한다. |
| 전 화면 순회 `e2e/crawl.mjs`(121개 화면) | **올리기 전 기준선**을 한 번 남기고, 묶음마다 콘솔 오류, 깨진 자원, 상태 코드를 기준선과 비교한다(`bs-migration-harness.md`의 `compare.mjs`). |
| 상호작용 점검 `crawl.mjs --interact` | 모달, 드롭다운, 탭, 툴팁이 실제로 열리는지 본다. |
| 일시 점검 `jquery-migrate`(단계 2 동안만) | 제거된 API를 쓰는 곳의 경고를 콘솔로 모은다. 끝나면 뺀다. |

## 4. 단계

크기는 작업량의 어림이다(S: 한나절 안, M: 하루 안팎, L: 이틀 이상). 순서는 위험이 작은 것부터이며 선생님 결정(2026-11-07)으로 **select2는 마지막에 단독 단계**이다.

### 단계 1. 같은 계열의 안전한 묶음 (S)

API가 바뀌지 않는 갱신이다: Popper 2.11.8, Tippy.js 6.3.7, clipboard 2.0.11, NProgress 0.2.0, autosize 6.0.1, Viewer.js 1.15.2, magnific-popup 1.2.0, underscore 1.13.8(1개 파일의 `_` 사용 확인), js-xss 1.0.17, Pikaday 1.8.2, video.js 7.21.2(7계열 마지막). 배포 파일과 CSS를 교체하고 번들(`yona-lib.js`)에 들어가는 것은 다시 만든다.

### 단계 2. Ace 1.45.2 (S)

폴더 전체(174개 파일)를 `ace-builds`의 `src-min-noconflict`로 바꾼다. 모드와 테마는 `ace.js`가 기준 경로에서 읽으므로 경로 설정이 맞는지, 코드 보기의 편집 화면이 열리는지 확인한다.

### 단계 3. jQuery 3.3.1 → 3.7.1 과 jQuery UI 효과 (M)

1. `build-tools/minify-js.sh`의 입력을 새 jQuery로 바꿔 `yona-layout.js`를 다시 만든다(ES5인 3.7.1은 Closure Compiler가 처리한다).
2. 마이그레이션과 첫 설정 화면의 **1.9.0도 같은 버전으로 합친다**(jQuery가 두 벌이면 플러그인도 두 벌이 된다).
3. `jquery-migrate`를 일시적으로 붙여 크롤링하고 경고가 나오는 곳을 고친다.
4. jQuery UI는 효과만 쓰는 맞춤 빌드이므로 쓰는 효과를 먼저 확인하고 1.14.2로 다시 받는다.

### 단계 4. moment 2.31 과 highlight.js 11 (M)

- **moment**: `moment-with-langs`는 `moment-with-locales`로 이름이 바뀌었다. 쓰는 곳 4개(`isValid`와 날짜 서식)를 시험한다. ISO가 아닌 날짜 문자열의 동작 차이를 확인한다.
- **highlight.js**: `hljs.highlight(lang, code)`는 `hljs.highlight(code, {language})`로, `highlightBlock`은 `highlightElement`로 바뀌었다. 쓰는 곳은 `yobi.Markdown.js` 하나이다. 지금 맞춤 묶음의 언어 목록과 같게 다시 만들고 CSS 테마를 교체한다. 서버의 코드 강조는 별개이다.

### 단계 5. marked 18 (L, 선생님이 올리기로 하심, 범위 결정 대기)

1. **먼저 비교 시험을 만든다**: 같은 입력 모음(README, 이슈, 댓글 실제 모양, 표, 작업 목록, 코드 블록, 이미지와 자동 링크, 제목 앵커, 강조 겹침, 줄바꿈)을 옛 marked와 새 marked에 넣어 결과를 **항목별로 비교**한다(`MarkdownRendererTest`의 v1.6 비교 기준을 그대로 둔다).
2. 서버의 Nashorn 독립 배포판이 새 marked를 실행하는지 확인한다. 못 읽으면 서버는 올릴 수 없다.
3. 옵션과 `highlight`를 새 방식(`marked-highlight` 확장 또는 렌더 뒤 `hljs.highlightElement`)으로 옮기고 `smartLists`처럼 없어진 동작은 같은 결과가 나도록 보정한다.
4. 차이는 목록으로 만들어 선생님이 항목별로 승인한다(D-17의 방식과 같다).
범위(브라우저만, 서버까지, 올리지 않음)는 7절 결정 1이다.

### 단계 6. select2 3.4.5 → 4.1.0 (L, 단독 단계, 마지막)

4.x는 숨은 입력(`<input>`) 대신 `<select>`를 기대하고 옵션 이름과 이벤트가 다르다. 쓰는 곳은 `yobi.ui.Select2.js`와 7개 파일이다. 한국어와 일본어 로케일 파일(`select2_locale_*`)도 4.x 형식으로 바뀐다. 다른 변경과 섞지 않는다.

### 단계 7. jQuery 4.0.0 (M, 단계 3과 6 뒤에 이어서 가능)

공식 제거 목록이 유틸 함수 몇 개라서 생각보다 작은 일이다: (1) 앱 코드 28곳(`$.trim`, `$.parseJSON`, `$.isNumeric`)을 치환한다. (2) 남기는 옛 사본의 치환(pjax, At.js, event.drag, tmpl 등)과 `.toggleClass(불리언)` 사용을 확인한다. (3) 새 버전으로 올린 것(jQuery UI, form, scrollTo, select2 4.1.0 등)은 그대로 둔다. (4) `jquery-migrate` 4.0.2로 경고를 모으고 전 화면 순회와 상호작용 점검으로 `focusin` 순서, ajax `dataType`, 단위 없는 CSS 값을 확인한다. (5) Closure Compiler가 새 jQuery를 읽는지 확인하고 못 읽으면 이미 압축된 파일을 따로 불러오는 방식(Bootstrap 5 번들과 같은 방식)으로 한다. At.js는 jQuery 4를 제외 선언한 라이브러리라 2줄 치환 뒤에도 멘션 자동완성을 직접 시험한다.

### 단계 8. 정리 (선생님 확인 뒤)

쓰지 않는 파일의 목록을 만들어 **목록을 먼저 보여 드리고 확인을 받은 뒤에** 지운다: `jquery.zclip`과 `ZeroClipboard.swf`, `jquery-1.7.2*`, 새 jQuery로 대체된 옛 파일, 참조가 없는 `public/`의 `less-1.3.0.min.js`. 참조 여부는 `TemplateScriptReferencesTest`가 근거가 된다.

## 5. 묶음마다 하는 일 (공통 절차)

1. 해당 라이브러리를 쓰는 곳을 다시 찾는다(`grep`).
2. **실패하는 시험을 먼저** 쓴다(버전 기준 표를 새 버전으로 고치면 실패한다).
3. 빈 폴더에 내려받고 체크섬을 확인해 배포 파일을 복사한다. 번들에 들어가면 `build-tools/minify-js.sh`로 다시 만든다.
4. 시험이 통과하는지 본다(관련 시험을 먼저, 마지막에 전체 H2).
5. 크롤링을 한 번 돌려 기준선과 비교하고, 바뀐 화면은 Playwright로 한 번 확인한다.
6. `decisions.md`(결정은 D, 에이전트 판단은 A), `log.md`(소요 시간), 이 문서의 표를 갱신하고 커밋한다.

## 6. 위험

| 위험 | 대응 |
|---|---|
| 오래된 jQuery 플러그인이 새 jQuery에서 깨진다 | 단계 3에서 `jquery-migrate` 경고와 전 화면 순회로 찾는다. 안 되면 해당 플러그인만 고치거나 대체한다. |
| Closure Compiler(`compiler.jar`)가 낡아 새 라이브러리의 문법을 못 읽는다 | 3.7.1은 ES5라 가능하다. 그 이상(jQuery 4 등)은 컴파일러를 올리거나 이미 압축된 파일을 따로 불러오는 방식(Bootstrap 5 번들과 같은 방식)으로 한다. |
| marked를 올리면 서버 렌더 결과가 v1.6과 달라진다(세 벌이 같은 파일) | 단계 5의 비교 시험을 먼저 만들고, 차이를 항목별로 승인받는다. 서버의 Nashorn이 새 문법을 읽는지 먼저 본다. 브라우저만 올리면 미리보기와 저장 뒤 화면이 달라질 수 있다. |
| 모듈 경로나 파일 이름이 바뀌어 404가 난다 | `TemplateScriptReferencesTest`가 막는다. |
| 한 번에 많이 올려 원인을 못 찾는다 | 단계와 묶음을 나누고 묶음마다 커밋한다. |

## 7. 결정

### 정해진 것 (선생님, 2026-11-07)

- **select2 4.x**: 마지막에 단독 단계로 한다(단계 6).
- **쓰지 않는 파일 정리**: 목록을 먼저 보여 드리고 확인을 받은 뒤에 지운다(단계 8).
- **marked**: 브라우저와 서버를 **함께** 올린다. 서버 렌더 결과가 v1.6과 달라지므로 단계 5의 비교 시험으로 차이를 목록으로 만들어 항목별로 승인받는다. 서버의 Nashorn이 새 문법을 읽지 못하면 그 사실을 보고하고 방법을 다시 정한다.
- **jQuery**: "jQuery에 의존하는 라이브러리의 상위 버전 검토"를 요청하셨고 위 표로 검토했다(정정 포함). 결론은 아래 대기 1번이다.

### 대기 중

1. **jQuery를 어디까지 올릴지**: (A) 3.7.1에서 멈춘다, (B) 3.7.1을 먼저 하고 이어서 4.0.0까지 간다(**권장**). 이유: 공식 제거 목록이 유틸 함수 몇 개라 치환으로 풀리고(앱 28곳과 옛 사본 몇 줄), 올릴 새 버전은 대부분 4.0을 지원하거나 지원하는 것으로 보인다. 미지원인 pjax, At.js, event.drag는 치환 2~6줄이다. 위험은 이름이 아닌 **동작 변경**이므로 `jquery-migrate`와 전 화면 순회로 확인한다. 3.7.1을 먼저 하면 4.0에서 문제가 나도 3.7.1로 되돌릴 수 있다.
2. **video.js**: 7.21.2(기본값으로 진행)와 8.24.1 중.
3. **구현 시작**: 단계 0(시험과 기준선)부터 시작해도 되는지.
