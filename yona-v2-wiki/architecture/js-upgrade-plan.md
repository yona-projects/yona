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

### jQuery 플러그인과 jQuery 4의 거리 (2026-11-07 조사)

jQuery 4에서 제거되었다고 알려진 API를 플러그인 파일에서 세었다. **앱 코드**(`common`, `service`)는 `$.trim` 21곳, `$.parseJSON` 6곳, `.bind`와 `.unbind` 4곳으로 적다. 반면 **플러그인**이 많다.

| 플러그인(현재 버전) | 제거 대상 API 사용 | 상태 |
|---|---|---|
| select2 3.4.5 | `.bind` 32, `$.isFunction` 11, `$.trim` 3, `$.isArray` 3 | 4.x로 올려야 함(단계 6) |
| jquery.validate 1.9.0(2011) | `$.trim` 7, `$.isFunction` 2, `.bind` 3 | 최신 1.20대 |
| jquery.dynatree 1.2.4 | `$.trim` 3, `$.isArray` 2, `$.browser` 4, `.bind` 5 | **유지 끝**(후속은 fancytree) |
| jquery.Jcrop 0.9.12 | `.bind` 11, `.unbind` 4 | 유지 끝 |
| jquery.form 3.09 | `$.trim` 1, `$.isFunction` 1 | 최신 4.3 |
| jquery.pjax | `$.isArray` 2, `$.isFunction` 2, `$.trim` 1 | 최신 2.0 |
| jquery.tmpl 1.0.0, jquery.browser 0.0.6 | `$.isArray` 4, `$.browser` | **유지 끝**(앱이 쓰는 곳 확인 필요) |
| jquery.cookie 1.4.0 | `$.isFunction` 2 | 후속은 js-cookie |
| magnific-popup, event.drag 2.2, At.js, placeholder 2.0.7 | `$.isFunction` 등 소수 | 각각 확인 |

합계로 `$.trim` 17, `$.isArray` 15, `$.isFunction` 23, `.bind` 63, `.unbind` 10, `$.browser` 6곳이다. **jQuery 3.7.1에서는 모두 그대로 동작한다**(지원 중단 경고만 있다). jQuery 4는 위 플러그인 약 열 개를 올리거나 대체해야 하므로 select2 4.x와 묶인 별도 큰 일이다.

### jQuery 의존 라이브러리의 상위 버전 검토 (선생님 요청, 2026-11-07)

선생님이 "jQuery에 의존하는 라이브러리의 상위 버전을 검토"하라고 하셨다. npm의 최신 버전과 **jQuery 4 지원 선언**(`peerDependencies`와 `dependencies`)을 조사했다. 선언이 있다고 실제 4.0 동작이 보장되는 것은 아니므로 올릴 때 시험으로 확인한다. 사용 횟수는 앱 코드(`common`, `service`, `template`, 템플릿)에서 센 것이다.

| 라이브러리 | 현재 | npm 최신(갱신일) | jQuery 4 선언 | 앱의 사용 | 판단 |
|---|---|---|---|---|---|
| jquery-ui(효과만) | 1.10.4 | 1.14.2 (2026-01) | `>=1.12 <5` 지원 | 효과 | 올릴 수 있음 |
| jquery-validation | 1.9.0 | 1.22.1 (2026-02) | `^4.0` 지원 | `.validate(` 0줄 | 쓰이지 않는 것으로 보임(정리 후보) |
| jquery-form | 3.09 | 4.4.0 (2026-11) | `>=1.7.2` | 4줄 / 2파일 | 올릴 수 있음 |
| select2 | 3.4.5 | 4.1.0 (2026-05) | 선언 없음 | 7파일 | 단계 6, 4.0 호환은 올릴 때 확인 |
| magnific-popup | 1.1.0 | 1.2.0 (2024) | 선언 없음 | 앱 코드에서 호출 없음 | 단계 1에서 올리거나 정리 후보 |
| jquery.cookie | 1.4.0 | 1.4.1 / 후속 js-cookie 3.0.8 | 해당 없음 | `$.cookie` 0줄 | 쓰이지 않는 것으로 보임(정리 후보) |
| jquery-placeholder | 2.0.7 | 2.3.1 (2022) | `*` | 1줄 | 브라우저가 기본 지원, 제거 후보 |
| jquery.scrollTo | 1.4.6 | 2.1.3 (2022) | `>=1.8` | 4줄 / 2파일 | 올릴 수 있음 |
| jquery.event.drag | 2.2 | 2.2.2 (2022) | 선언 없음 | 확인 필요 | 유지 끝, 쓰는 곳 확인 |
| jquery-pageslide | 2.0.1 | 2.0.2 (2022) | 선언 없음 | 12줄 / 8파일 | 유지 끝, 올려도 변화 작음 |
| jquery-pjax(Yona 사본, 2012년판) | 2012 | 2.0.1 (2022) | 선언 없음 | 6줄 / 2파일(전 화면 이동) | 유지 끝, **핵심 기능**이라 4.0 호환을 직접 시험해야 함 |
| **At.js**(멘션 자동완성) | 1.5.3 | 1.5.4 (2022) | **`<4.0.0`(4.0 제외)** | 5줄 / 2파일, 템플릿 14곳 | **jQuery 4의 걸림돌**, 대체해야 함(Tribute.js 5.1.3은 2022년 이후 갱신 없음, `components` 저장소의 CodeMirror 6 위젯이 후속 후보) |
| jquery.dynatree | 1.2.4 | 후속 jquery.fancytree 2.38.5 (2025-04) | 해당 없음 | `.dynatree(` 0줄 | 쓰이지 않는 것으로 보임(정리 후보, 코드 보기 템플릿의 참조 확인) |
| Jcrop | 0.9.12 | 0.9.15 재포장 (2022) / 후속 cropperjs 2.3.1 (2026-10, jQuery 불필요) | 해당 없음 | 14줄 / 2파일(프로필 이미지 자르기) | 유지 끝, jQuery 4를 하려면 cropperjs 등으로 대체 |
| jquery.tmpl | 1.0.0 | 후속 없음(공식 폐기) | 해당 없음 | **25줄 / 12파일** | 유지 끝, jQuery 4를 하려면 템플릿 문자열 함수 등으로 12파일 교체 |
| jquery.browser | 0.0.6 | 0.1.0 (2022) | 해당 없음 | 2줄 / 2파일 | 폐기 대상, 사용 두 곳을 제거 |
| jquery-migrate | - | 4.0.2 (2026-01) | `>=4 <5` | - | 이행 점검용 도구 |

**결론**: jQuery 4로 가려면 At.js(대체), jquery.tmpl(12파일 교체), Jcrop(대체), jquery-pjax(호환 시험), select2 4.x가 필요하다. 이 중 둘 이상은 단순한 버전 상승이 아니라 **다른 라이브러리로 바꾸는 일**이다. 반면 jQuery 3.7.1은 지금 있는 플러그인이 모두 그대로 동작한다. 따라서 **3.7.1에서 멈추고 jQuery 4는 단계 7에서 따로** 다루는 것을 권장한다. `.validate(`, `$.cookie`, `.dynatree(`의 호출이 없어 보이므로 단계 8의 정리 목록에 후보로 올린다(사용처는 한 번 더 확인한다).

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

### 단계 7. (선택) jQuery 4.0.0 과 플러그인 교체 (L)

단계 6이 끝난 뒤에만 한다. 1절의 플러그인 표에서 유지가 끝난 것(dynatree, Jcrop, tmpl, browser)은 대체나 수정이 필요하고, validate, form, pjax, cookie는 새 버전으로 올린다. Closure Compiler가 새 jQuery를 읽을 수 있는지도 확인한다. 얻는 것이 작아 보이므로 3.7.1에 머무는 것이 권장이다.

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
- **jQuery**: "jQuery에 의존하는 라이브러리의 상위 버전 검토"를 요청하셨고 위 표로 검토했다. 결론은 3.7.1 권장이다.

### 대기 중

1. **jQuery를 어디까지 올릴지**: (A) 3.7.1에서 멈추고 jQuery 4는 단계 7로 남긴다(**권장**), (B) 4.0.0까지 간다. 위 검토 결과를 보시고 정해 주시면 된다.
2. **video.js**: 7.21.2(기본값으로 진행)와 8.24.1 중.
3. **구현 시작**: 단계 0(시험과 기준선)부터 시작해도 되는지.
