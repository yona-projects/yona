---
title: 소규모 lib 파일과 화면별 JS의 현대화 검토
type: architecture
updated: 2026-11-10
---

# 소규모 lib 파일과 화면별 JS의 현대화 검토 (2026-11-10, 선생님 요청)

조사 문서이다. 선생님이 1에서 4단계와 ES 모듈 뼈대를 승인해 구현했다(D-122, A-438, `esm-skeleton.md`). 아래 4절 권장 순서의 1에서 4번이 끝났다. 숫자는 정규식으로 센 근사값이다.

## 1. 현재 규모와 구조

| 항목 | 값 |
|---|---|
| 공통 JS(`common/`)와 화면별 JS(`service/`) | 81개, 17,715줄 (공통 34, 화면별 47) |
| jQuery 호출(`$(`, `.on`, `.css`, `.val`, `.data`, `$.ajax` 등) | 약 2,240곳 (`$(` 787, `.val/.text/.html/.attr` 546, `.addClass/.css/.show` 281, `.on/.click` 247, `.data` 206, `.ajax/.get/.post` 56) |
| 표준 API(`fetch`, `querySelector`) | 24곳. 이미 표준만 쓰는 파일: `Clipboard`, `Csrf`, `Htmx`, `KeyControl`, `Request`, `webauthn`, `ui.Toast` |
| 문법 | `var` 1,318, `let/const` 2, 화살표 함수 1, `class` 0. 전부 `(function(ns){ ... })` 즉시 실행 함수와 `$yona.createNamespace` 전역 이름 공간 |
| 불러오는 방식 | 번들 3개(`yona-layout.js`, `yona-common.js`, `yona-lib.js`, vite)와 템플릿의 `<script>` 태그, `$yona.loadModule("이름")` |
| 템플릿 안 스크립트 | 133블록 2,251줄(서버 값을 JS로 넘기는 `th:inline` 71개 파일) |
| JS 시험이 읽는 파일 | 81개 중 41개(10,827줄). **시험 없는 파일 40개에 jQuery 호출 892곳**: `Usermenu` 109, `issue.LabelEditor` 105, `issue.View` 67, `git.Write` 44, `CodeCommentBlock` 40, `Tasklist` 37, `LoginDialog` 35 등 |
| 이미 쓰는 현대 구조 | import map(D-106, 모듈 `text-expander`, `markdown-toolbar`, `title-input`은 `<script type="module">`), vite 번들 도구(D-92) |

## 2. 소규모 lib 파일

| 파일 | 크기와 버전 | 쓰이는 곳 | 현대적 대체 | 판단 |
|---|---|---|---|---|
| `lib/diff.js`(JsDiff) | 12KB, 표기 없음 | `code/svn-diff.html`, `pullrequest/view.html`이 불러옴 | **호출이 없다**(`JsDiff`를 부르는 코드가 템플릿과 JS 어디에도 없음) | 지울 수 있다. 정리 대상은 확인을 받은 뒤 지운다 |
| `lib/jquery/jquery.browser.js` | 3KB, 0.0.6 | `migration-layout.html`과 번들 | **호출이 없다**(`$.browser` 없음) | 지울 수 있다(확인 후) |
| `lib/magnific-popup/magnific-popup.css` | 7KB, 0.8 | `layout-framed.html`이 CSS만 불러옴 | **`.mfp` 클래스를 쓰는 곳이 없다** | 지울 수 있다(확인 후) |
| `lib/parseuri.js` | 1KB, 1.2.2 | `user/view.html`이 불러오고 `user.View.js`가 한 번 호출 | `new URL(location.href)` | 쉽다. 표준으로 5줄 |
| `lib/rgbcolor.js` | 9KB | `getContrastColor`(`Common.js`, `SubComment.js`), `issue.LabelEditor.js` | 색 글자를 읽는 부분만 필요: 캔버스 한 칸이나 `getComputedStyle`로 `rgb(...)`를 얻어 밝기 계산 | 쉽다. 단 색 이름(`red`)과 `#abc` 형태를 같이 받아야 한다 |
| `lib/humanize.js` | 17KB | `humanize.filesize`만 4곳(`Files`, `Attachments`, `CommentAttachmentsUpdate`, `code.Browser`) | `Intl.NumberFormat`의 `unit`(byte, kilobyte, megabyte) 또는 직접 쓴 `formatBytes` | 쉽다. **글자 모양이 같은지**(`1.5 MB`, `512 bytes`) 먼저 시험으로 고정 |
| `lib/favico/favico.min.js` | 9KB, 0.3.10(2016, 유지 중단) | `yona.detectChange.js`가 `badge('N')`만 부른다 | 캔버스로 파비콘 위에 점을 그리는 직접 구현 약 30줄 | 쉬운 편. **직접 구현이라 미리 알리고 선택**(Favico를 그대로 두는 것도 가능) |
| `lib/elevator/jquery.elevator.js`와 `.css` | 25KB+11KB, 1.0.6(2015) | `issue/view.html`, `board/view.html`이 `$.elevator({shape:'rounded', tooltips:true})` 한 번 | `window.scrollTo({top:0, behavior:'smooth'})`와 CSS | 쉽다. 모양(둥근 단추, 툴팁)을 같게 하는 CSS 작업이 있다 |
| `lib/validate.js`(FormValidator) | 13KB, 1.1(2011) | 가입, 비밀번호 재설정, 사용자 설정, 조직 만들기와 설정 등 **5개 화면** | HTML5 제약 검증(`required`, `minlength`, `pattern`, `setCustomValidity`)과 `form.reportValidity()` | **중간.** 규칙은 `required`, `min_length`, `matches` 등이라 대응되지만, 오류를 **Bootstrap popover로 필드마다 표시하는 동작**과 메시지가 같아야 한다. 시험이 먼저다 |
| `lib/filetype.js` | 66KB, Yona 자체 | `user/files.html` | 데이터 표라 JSON이나 모듈로 바꿀 수 있다 | 해도 얻는 것이 적다. 우선순위 낮음 |
| `lib/humanize.js` 외 `rgbcolor.js`, `parseuri.js`, `validate.js`, `elevator`, `favico`, `diff.js` | | | | 6개는 라이선스 표기가 없거나 낡은 제3자 파일이라 없애면 `NOTICE`도 가벼워진다 |

요약: **3개는 이미 죽은 코드**(`diff.js`, `jquery.browser.js`, `magnific-popup.css`), **4개는 표준 API로 바로 바뀌고**(`parseuri`, `rgbcolor`, `humanize`, `elevator`), `favico`는 직접 구현이 필요하며, `validate.js`만 화면 동작이 걸린 중간 규모다.

## 3. 화면별(공통 포함) JS의 현대화

"현대적인 JS"를 세 층으로 나눠 볼 수 있고, 층마다 가능성과 위험이 다르다.

### (가) 문법: `var`를 `let/const`로, 화살표 함수, 템플릿 문자열
- **가능, 기계적.** ESLint의 `no-var`, `prefer-const`, `prefer-arrow-callback` 자동 고침이나 jscodeshift로 한꺼번에 바꿀 수 있다.
- 위험은 `this`와 호이스팅(`var`를 블록 밖에서 쓰는 곳), 화살표 함수의 `this` 차이다. 자동 도구가 위험한 곳은 건너뛴다.
- 이득: 읽기 쉬워지지만 **동작은 바뀌지 않는다.** 헝가리안 표기(`sURL`, `htOptions`, `welTarget`)는 그대로 둘 수도, 바꿀 수도 있다. 이름만 바꾸면 소스 대조가 어려워지므로 권하지 않는다.

### (나) jQuery 제거(S-14의 본체)
- **가능하지만 가장 크다.** 약 2,240곳. 표준으로 옮기는 문장 대응은 정해져 있다(`$(sel)` → `querySelector(All)`, `.on` → `addEventListener`와 위임, `.data` → `dataset`, `.css/.addClass` → `classList`와 `style`, `$.ajax` → `fetch`(`yona.Request`가 이미 있다)).
- **jQuery를 못 버리게 하는 것**: ① **select2 4.1.0**(jQuery 필수, `ui.Select2.js` 490줄과 7개 파일이 씀), ② jQuery UI 효과(`highlight`, `shake`, 2곳), ③ 우리가 직접 쓴 `$.fn` 플러그인과 `.data("typeahead")` 같은 메서드 체인. select2를 같은 동작의 비jQuery 라이브러리(Tom Select 등)로 바꾸는 큰 결정이 먼저 필요하다. **select2가 그대로면 jQuery는 남는다.**
- 시험 없는 40개 파일(jQuery 892곳)은 **바꾸기 전에 특성 시험부터** 만들어야 안전하다(S-10, pageslide, jquery.form에서 쓴 방식).
- 현실적 순서: 이미 표준인 파일은 그대로, 호출 수가 적은 파일부터 하나씩(예: `project.Fork`, `milestone.*`, `board.*`), 가장 큰 `code.SvnDiff`(182), `Usermenu`(109), `issue.LabelEditor`(105), `code.Diff`(104)는 마지막.

### (다) ES 모듈화 (`import`와 `export`, 전역 이름 공간 제거)
- **번들러 없이 가능하다.** 이미 import map과 `<script type="module">`이 있다(D-106). 방법은 `(function(ns){...})`를 `export`로 바꾸고 템플릿이 `import`하게 하는 것이다.
- **걸림 셋**: ① 모듈은 지연 실행이라 `$(function(){ ... })`와 인라인 `th:inline` 스크립트가 **같은 전역을 기대하는 순서**가 바뀐다. 133개 인라인 블록(2,251줄)을 `data-*` 속성과 모듈 초기화로 옮겨야 한다(이전 화면을 Vue로 옮길 때 쓴 방식이 본보기다). ② 번들 3개(`yona-*.js`, vite)의 이어 붙이기 방식과 겹치므로 **번들을 모듈 그래프 기준 vite 빌드로 바꾸거나** 번들 없이 개별 로드로 갈지 정해야 한다. ③ 브라우저 지원: import map은 지금 쓰는 주요 브라우저(Chrome, Firefox, Safari 16.4 이상)에서 동작한다. 더 오래된 Safari는 불가.
- 이득이 큰 것은 (나)와 함께 할 때다. 모듈만 바꿔도 jQuery 전역은 남는다.

## 4. 권장 순서

1. **죽은 코드 3개 정리**(`diff.js`, `jquery.browser.js`, `magnific-popup.css`)는 확인을 받아 바로 지운다. 위험 없음.
2. **표준으로 바로 바뀌는 4개**(`parseuri`, `rgbcolor`, `humanize`, `elevator`)를 특성 시험을 먼저 만들어 교체한다. 한 번에 끝나는 작은 일이다.
3. `favico`는 직접 구현이 필요하므로 선택을 받는다(그대로 둠, 직접 구현, 없앰).
4. `validate.js`는 5개 화면의 오류 표시 동작을 시험으로 고정한 뒤 HTML5 제약 검증으로 옮긴다.
5. (가) 문법 현대화는 (나), (다)와 별개로 언제든 기계적으로 할 수 있다. **JS 시험 390개가 이미 있어** 회귀를 잡기 좋다.
6. (나)와 (다)는 select2 대체 결정이 선행되어야 의미가 있다. 이 결정과 별도 단계(S-14)로 다룬다.

## 5. 선생님께 받을 결정
- 2번 교체와 1번 정리를 진행해도 되는가.
- `favico`: 직접 구현(권장), 그대로, 없앰.
- 문법 현대화(가)를 기계적으로 한꺼번에 해도 되는가(동작 불변, 시험 390개로 확인).
- (나), (다)를 어느 수준까지 갈 것인가: 문법만, jQuery 제거(select2 유지로 일부 남김), 완전한 ES 모듈과 jQuery 제거(select2 대체 포함).
