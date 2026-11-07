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
| marked | 오래된 0.x | 18.x | 마크다운 미리보기(`yobi.Markdown.js`) | **큼**(서버 렌더와 같아야 함) |
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

jQuery 플러그인(`pjax`, `browser`, `tmpl`, `form`, `validate`, `requestAs`, `search`, `placeholder`, `cookie`, `Jcrop`, `dynatree`, `event.drag`, `scrollTo`)은 대부분 유지가 끝난 것이라 **jQuery를 올릴 때 호환을 시험으로 확인해야** 한다. `jquery.zclip`과 `ZeroClipboard.swf`(Flash)는 이미 쓸모가 없다.

jQuery 4에서 영향을 받을 앱 코드의 단서: `$.trim` 21곳, `$.parseJSON` 6곳, `.bind`와 `.unbind` 4곳(정확한 제거 목록은 공식 4.0 업그레이드 가이드와 `jquery-migrate`로 확인한다).

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

크기는 작업량의 어림이다(S: 한나절 안, M: 하루 안팎, L: 이틀 이상).

### 단계 1. 같은 계열의 안전한 묶음 (S)

API가 바뀌지 않는 갱신이다: Popper 2.11.8, Tippy 6.3.7, clipboard 2.0.11, NProgress 0.2.0, autosize 6.0.1, Viewer.js 1.15.2, magnific-popup 1.2.0, underscore 1.13.8(1개 파일의 `_` 사용 확인), js-xss 1.0.17, Pikaday 1.8.2, video.js **7.21.2**(7계열 마지막, 8로는 가지 않는다). 각 라이브러리의 배포 파일과 CSS를 교체하고 `yona-lib.js` 번들에 들어가는 것은 번들을 다시 만든다.

### 단계 2. Ace 1.45.2 (S)

폴더 전체(174개 파일)를 `ace-builds`의 `src-min-noconflict`로 바꾼다. 모드와 테마는 `ace.js`가 기준 경로에서 동적으로 읽으므로 경로 설정이 그대로 맞는지, 코드 보기의 편집 화면이 열리는지 확인한다.

### 단계 3. jQuery 3.3.1 → 3.7.1 과 jQuery UI 효과 (M)

1. `build-tools/minify-js.sh`의 입력을 새 jQuery로 바꿔 `yona-layout.js`를 다시 만든다(Closure Compiler가 ES5인 3.7.1은 처리한다).
2. 마이그레이션과 첫 설정 화면이 쓰는 **1.9.0도 같은 버전으로 합친다**(jQuery가 두 벌이면 플러그인도 두 벌이 된다). 이 세 화면은 사용하는 플러그인이 적어 영향이 작다.
3. `jquery-migrate`를 일시적으로 붙여 크롤링하고, 경고가 나오는 플러그인과 앱 코드를 고친다.
4. jQuery UI는 효과만 쓰는 맞춤 빌드이므로 필요한 효과만 담아 1.14.2로 다시 받는다(쓰는 효과를 먼저 확인).

### 단계 4. moment 2.31 과 highlight.js 11 (M)

- **moment**: `moment-with-langs`는 `moment-with-locales`로 이름이 바뀌었다. 쓰는 곳 4개(`isValid`와 날짜 서식)를 시험한다. 날짜 문자열이 ISO 형식이 아니면 2.4.0과 동작이 달라질 수 있으므로 입력 형식을 확인한다.
- **highlight.js**: `hljs.highlight(lang, code)`는 `hljs.highlight(code, {language})`로, `highlightBlock`은 `highlightElement`로 바뀌었다. 쓰는 곳은 `yobi.Markdown.js` 하나이다. 지금의 맞춤 묶음(언어 목록)과 같은 언어를 담아 다시 만들고 CSS 테마 파일을 교체한다.

### 단계 5. select2 3.4.5 → 4.1.0 (L, 가장 위험)

4.x는 숨은 입력(`<input>`) 대신 `<select>`를 기대하고 옵션 이름과 이벤트가 다르다. 쓰는 곳은 `yobi.ui.Select2.js`와 7개 파일이다. 앞서 Bootstrap 5 전환에서 이 항목을 **보류**했던 것(`X-38` 메모)과 같은 일이므로, 선생님 결정이 있을 때 시작한다. 한국어와 일본어 로케일 파일(`select2_locale_*`)도 4.x 형식으로 바뀐다.

### 단계 6. 선택 항목 (결정 후)

- **jQuery 4.0.0**: 3.7.1에서 경고가 모두 없어진 뒤에만 한다. 플러그인 가운데 유지가 끝난 것은 대체나 수정이 필요하다.
- **marked**: 미리보기가 서버 렌더와 같아야 한다는 v1.6 동일성 때문에 위험하다(7절 결정 2).
- **`vendor.js`**: AngularJS는 업그레이드할 대상이 없다. 마이그레이션 화면을 다시 쓸지는 별개의 일이다.

### 단계 7. 정리 (선생님 확인 뒤)

쓰지 않는 파일의 목록을 만들어 **확인을 받은 뒤에** 지운다: `jquery.zclip`과 `ZeroClipboard.swf`, `jquery-1.7.2*`, 새 jQuery로 대체된 옛 파일, 참조가 없는 `public/`의 `less-1.3.0.min.js`. 참조 여부는 `TemplateScriptReferencesTest`가 근거가 된다.

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
| 마크다운 미리보기와 서버 렌더의 차이 | marked는 결정 전에는 올리지 않는다. 올린다면 같은 입력을 양쪽에 넣어 결과를 비교하는 시험을 먼저 만든다. |
| 모듈 경로나 파일 이름이 바뀌어 404가 난다 | `TemplateScriptReferencesTest`가 막는다. |
| 한 번에 많이 올려 원인을 못 찾는다 | 단계와 묶음을 나누고 묶음마다 커밋한다. |

## 7. 결정이 필요한 것

1. **jQuery를 어디까지 올릴지**: (A) 3.7.1에서 멈춘다(권장), (B) 4.0.0까지 간다. 권장 이유: 4.0은 제거된 API가 있고 유지가 끝난 플러그인이 많아 얻는 것에 비해 비용이 크며, 3.7.1은 같은 계열이라 위험이 작다.
2. **marked를 올릴지**: (A) 올리지 않는다(권장), (B) 최신으로 올리고 서버 렌더와 비교 시험을 만든다. 권장 이유: v1.6 동일성이 원칙이고 18.x는 API가 크게 달라 미리보기 결과가 달라질 위험이 크다.
3. **select2 4.x를 이번에 할지**: (A) 이번에 한다, (B) 보류를 유지한다(권장은 마지막에 단독 단계로 하는 것). 이유: 위험이 가장 크고 Bootstrap 5 전환에서도 따로 미뤘다.
4. **video.js**: (A) 7.21.2(권장), (B) 8.24.1. 이유: 8은 ES 모듈 중심으로 바뀌었고 이 사이트의 사용처가 하나라 얻는 것이 작다.
5. **쓰지 않는 파일 정리**(단계 7)를 할지와 대상 목록.
