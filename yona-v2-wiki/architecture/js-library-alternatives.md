---
title: JS 라이브러리 대체 후보 조사
type: architecture
sources:
  - js-upgrade-plan.md
  - decisions.md
updated: 2026-11-08
---

# JS 라이브러리 대체 후보 조사 (미결정, 구현 전)

`D-91`(JS 라이브러리 업그레이드)의 후속 조사이다. 선생님이 (1) 유틸리티성 라이브러리는 같은 기능의 경량 라이브러리를, (2) 없어지는 라이브러리는 대체 라이브러리를 찾아 달라고 하셨고(2026-11-07), **조사만 했으며 코드는 바꾸지 않았다.** 어느 항목을 계획에 넣을지는 선생님이 정하신다. 크기는 jsDelivr의 `+esm` 최소화 번들을 gzip으로 전송한 값이다(후보의 의존 포함, 비교용 어림). 유지 상태는 npm의 마지막 갱신일(2026-11-07 조회)이다.

## 1. 없어지는(새 버전이 없거나 정리할) 라이브러리의 대체

| 라이브러리 | 상태 | 앱의 사용 | 대체 후보 |
|---|---|---|---|
| **jquery.tmpl 1.0.0** | 공식 폐기 | 호출 20곳(자체 도우미 4곳 제외), 템플릿 18블록 | 3절. 문법이 단순 보간뿐이라 쉽다 |
| jquery.browser 0.0.6 | 폐기 | 2줄, 2개 파일 | 제거(기능 감지) |
| Jcrop 0.9.12 | 유지 끝. **Cropper.js 2.3.1로 대체 완료**(D-101, 2026-11-08) | 14줄, 2개 파일(프로필 이미지 자르기) | **Cropper.js 2**(2026-10 갱신, jQuery 불필요) |
| At.js 1.5.3(멘션 자동완성) | 유지 끝, jQuery 4 제외 선언 | 5줄, 2개 파일, 템플릿 14곳 | Tribute.js 5.1(8.1KB, 2022년 이후 갱신 없음), Tagify 4.39(19.9KB, 2026-09 갱신, 멘션 모드), `@textcomplete/core`(2023), CodeMirror 6 자동완성(`components` 에디터가 이미 사용) |
| jquery-pjax(2012년 사본, 목록 영역 교체) | 유지 끝(2022). **htmx로 대체 완료**(D-96, 2026-11-08) | 6줄, 2개 파일(사용처는 목록 화면 5곳) | **htmx** `hx-boost`(18.4KB), **Turbo Drive**(25.4KB), **swup**(7.6KB), 모두 2026년 갱신. 이동 방식이 바뀌는 큰 일이라 별도 프로젝트 |
| select2 3.4.5 | 4.1.0이 있음(결정: 마지막 단독 단계) | 7개 파일 | jQuery를 줄이려면 **Tom Select 2.6**(15.4KB, jQuery 불필요, 2026-07 갱신). select2 4.1(20.7KB)과 비교 |
| jquery-pageslide 2.0.1 | 유지 끝 | 12줄, 8개 파일 | **Bootstrap 5 Offcanvas**(이미 포함) |
| jquery.form 3.09 | 4.4.0이 있음 | 4줄, 2개 파일 | 올리거나 `fetch`와 `FormData` |
| jquery.scrollTo 1.4.6 | 2.1.3이 있음 | 4줄, 2개 파일 | 올리거나 네이티브 `scrollIntoView({behavior:'smooth'})` |
| AngularJS 1.5.8(`vendor.js`, 마이그레이션 화면 하나) | 지원 종료 | 마이그레이션 레이아웃 | Alpine.js 3.17(19.4KB), 바닐라 JS와 htmx |
| jquery.cookie, jquery.dynatree, jquery-validation, magnific-popup, jquery.placeholder, jquery.event.drag, jquery.zclip(Flash) | 앱 호출이 없거나 Flash 의존 | `$.cookie` 0줄, `.dynatree(` 0줄, `.validate(` 0줄 등 | **대체 없이 제거**(정리 단계에서 사용처를 한 번 더 확인하고 목록을 선생님께 확인받는다). 필요해지면 js-cookie 3(1.0KB), jquery.fancytree 2.38 |

## 2. 유틸리티성 라이브러리의 경량 대체

| 현재(앱의 사용) | 경량 후보 | 크기(KB) | 비고 |
|---|---|---|---|
| **underscore 1.4.4** | 제거 또는 `_.template` 용도(3절) | 8.4 | 앱이 `_.` 함수를 직접 호출하지 않고 어느 템플릿도 불러오지 않는다 |
| moment 2.4.0(전 로케일 109KB 원본) | **dayjs 1.11**(2026-08 갱신) / 네이티브 `Intl` | 3.3 / 0 | 쓰는 곳은 `isValid` 2곳과 날짜 서식 1곳. moment는 20.0 |
| js-xss(`xssClean` 5곳) | 유지(4.3) 또는 DOMPurify 3.4(2026-10 갱신) | 4.3 / 11.5 | 더 가벼운 대안 없음 |
| clipboard.js(7곳) | **clipboard-copy 4.0** / `navigator.clipboard` | 3.6 → 0.7 / 0 | http 접속 환경이면 clipboard-copy가 안전(네이티브는 https와 localhost만) |
| Tippy.js + Popper(템플릿 5곳) | **Bootstrap 5 Tooltip**(번들에 Popper 포함) | 8.6 → 0 | 두 파일을 뺄 수 있음 |
| humanize.js(16KB 원본: `filesize` 11, `pad` 10, `date` 6) | 네이티브 `Intl.NumberFormat`, `padStart`, `Intl.RelativeTimeFormat` / filesize 11 | 0 / 2.8 | |
| parseuri.js(1곳) | 네이티브 `URL` | 0 | |
| rgbcolor.js(2곳, 8.7KB 원본) | **colord 2.10**(2026-08 갱신) | 2.3 | 라벨 색 대비 계산 |
| canvas-to-blob(1곳) | 네이티브 `canvas.toBlob` | 0 | 폴리필이라 불필요 |
| diff.js(2곳, 12KB) | `diff`(jsdiff 9.0, 2026-04 갱신) | 8.4 | 같은 계열의 관리되는 후속 |
| NProgress(호출 80곳 이상) | 유지(1.9) | 1.9 | 이미 가벼움. 대안 BProgress 3.3 |
| autosize(호출 1곳) | 유지(1.3) / CSS `field-sizing: content` | 1.3 / 0 | Firefox 지원 확인 필요 |
| Pikaday | **vanilla-calendar-pro 3.4.0으로 대체 완료**(`D-107`, 2026-11-09) | 25(gzip) | Pikaday 저장소가 2025-08 보관됨. 이전 판단("더 가벼운 대안 없음")을 고쳤다 |
| video.js 7.8.2(486KB 원본, 첨부 동영상 재생 하나) | **네이티브 `<video controls>`** | 약 150 → 0 | 가장 큰 절감 |
| Viewer.js(이미지 보기) | 유지(15.1) | 15.1 | medium-zoom(3.6)은 확대만 되어 기능이 줄어듦 |
| highlight.js | 11.x에서 쓰는 언어만 담아 다시 만들기 | 가변 | 지금도 맞춤 묶음 |
| Ace(319KB + 174개 파일) | 유지 / CodeMirror 6 | 131(코어) | CM6 통일은 큰 작업 |
| jQuery UI 효과 | CSS 전환 / Web Animations | 0 | 효과 몇 가지만 사용 |

측정 한계: video.js, Cropper.js, highlight.js는 번들 방식 때문에 측정이 맞지 않아 원본 크기나 추정을 적었다. `jquery.elevator`(이슈와 게시글 보기)의 쓰임은 아직 확인하지 못했다.

## 3. jquery.tmpl 대체와 underscore (선생님 질문, 2026-11-07)

선생님이 "underscore를 쓰면 일부가 쉽게 바뀌지 않겠느냐"고 물으셔서 실제 템플릿을 조사했다.

**조사 결과**
- 템플릿은 `<script type="text/x-jquery-tmpl">` 18블록과 JS 문자열 템플릿이다. `${...}` 식 63개가 **모두 단순한 변수나 속성 참조**이고, `{{if}}`, `{{each}}`, `{{html}}` 같은 jquery.tmpl 전용 문법과 JS 식은 **하나도 없다.**
- 호출은 `$.tmpl(문자열, 데이터)` 13곳(`yobi.ui.Select2.js`에 7곳), 요소의 `.tmpl(데이터)`(`#id`의 템플릿) 7곳(`yobi.project.Home.js` 4곳, `yobi.code.SvnDiff.js` 3곳), 그리고 Yona 자체 도우미 `$yobi.tmpl`(= `processTpl`, 5줄 정규식 치환, **HTML 이스케이프 없음**, 중첩 속성 `a.b` 없음) 4곳이다. jquery.tmpl에 의존하는 호출은 모두 20곳이다.
- jquery.tmpl의 `${}`는 **HTML 이스케이프**를 하고 결과는 **jQuery 객체**이다.

**쉽게 바뀌는가: 그렇다.** 문법이 단순 보간뿐이라 템플릿 본문을 바꾸지 않고 교체할 수 있다. 방법 두 가지:

| 방법 | 내용 | 장점 | 주의 |
|---|---|---|---|
| A. underscore `_.template` | `_.templateSettings = { escape: /\$\{(.+?)\}/g }`로 `${x}`를 이스케이프 보간으로 쓰고 결과를 `$(...)`로 감싼다 | 템플릿 본문 그대로, 중첩 속성과 JS 식도 됨, 8.4KB | 없는 변수는 `ReferenceError`(jquery.tmpl은 빈 문자열일 수 있음). `variable` 옵션을 쓰면 본문을 `${d.name}`으로 바꿔야 함. 결과가 문자열이라 호출부에서 감싸야 함. **underscore가 이 용도 하나뿐이면 8KB 라이브러리를 새로 들이는 셈** |
| B. 자체 도우미 확장 | `$yobi.tmpl`에 HTML 이스케이프와 중첩 속성, 없는 값은 빈 문자열을 더하고 요소용 `.tmplEl`을 더한다(약 10줄) | 새 라이브러리 없음, jquery.tmpl 동작(이스케이프, 빈 값)을 그대로 흉내 냄, 본문 그대로 | 직접 만든 코드라 시험을 함께 써야 함 |
| (참고) Mustache, Eta, doT | `{{x}}` 문법으로 바뀜 | 검증된 라이브러리 | 본문 63곳을 고쳐야 함 |

두 방법 모두 `${...}` **문법을 유지**하므로 서버(Thymeleaf)와 템플릿 파일의 충돌 처리를 건드리지 않는다. 바뀌는 것은 호출부 20곳(`$.tmpl` 13곳과 요소 호출 7곳)뿐이다. 권장은 B이다(underscore의 다른 사용처가 없다). 다만 underscore를 다른 곳에도 쓸 계획이라면 A가 맞다.

underscore가 쉽게 바꿔 주는 다른 일은 확인되지 않았다(앱이 `_.` 함수를 쓰지 않는다).

## 4. 진행 순서 (선생님 지시, 2026-11-07)

jquery.tmpl 대체(3절)는 **라이브러리 업데이트(Yona가 고친 벤더 파일의 패치 이식 등, `js-upgrade-plan.md`)를 먼저 끝낸 뒤**에 한다. 템플릿 엔진은 **Mustache로 결정되어 교체를 마쳤다**(`decisions.md` D-94와 A-400, 2026-11-07, select2 4.1.0 업데이트 뒤). underscore는 이 앱에서 `_.` 호출이 없어 다른 용도가 없다. 이어서 jquery-pjax도 htmx로 대체했다(`decisions.md` D-96과 A-401, 2026-11-08). 선생님은 나중 UI 개편에서 Vue 컴포넌트 도입과 2단 보기 개선을 생각하고 계시며, 그때 이 영역은 다시 바뀔 수 있다. 지금 작업하라는 지시는 아니다. 참고 확인: jquery.tmpl 1.0.0은 jQuery 3.7.1에서 동작한다(문자열, 배열, 요소 템플릿, `{{if}}`, `appendTo`를 jsdom에서 실행해 확인). jQuery 4에서는 `jQuery.trim`(`jquery.tmpl.js` 325번째 줄) 한 줄이 걸린다.

## jquery.pageslide 대체 후보 조사 (2026-11-09, 선생님 요청)

### 지금 하는 일
- **쓰는 곳은 2단 보기 하나**다. `service/yona.twoColumnMode.js`(99줄)가 목록의 제목 링크(`.title`, `.twoColumeModeTarget`)에 `pageslide({direction: "left", speed: 0, modal: true})`를 건다. 템플릿 5곳(`issue/list`, `issue/my-list`, `user/view`, `organization/issue-list`, `board/list`)이 `lib/jquery.pageslide.js`와 이 스크립트를 불러오고, 모양은 `_page.scss`의 `#pageslide`(오른쪽에 고정, 폭 50%, 높이 100%, `z-index: 999999`, 그림자, 로딩 gif 배경)가 정한다. 시험은 없다.
- **실제 동작**: 제목 링크를 누르면 기본 이동을 막고 그 주소를 **iframe으로 오른쪽 패널에 연다.** 같은 링크를 다시 누르면 닫는다. 다른 링크를 누르면 iframe을 지우고(로딩 gif가 보인다) 300ms 뒤 새 주소를 읽는다. 열릴 때 왼쪽 메뉴와 사용자 정보 상자를 숨기고 닫을 때 보인다(`yona.twoColumnMode.js`와 플러그인의 `.left-menu.show(200)`). 누를 때 `history.pushState`로 주소를 바꾸고 `NProgress.done()`을 부른다. `speed: 0`이라 **애니메이션이 없고**, `modal: true`라 **바깥 클릭이나 Esc로 닫히지 않는다.**
- 플러그인의 나머지 기능(오른쪽 방향, `#id` 요소 읽기, ajax 읽기, 비모달 닫기, 슬라이드 애니메이션)은 **쓰이지 않는다.**

### 플러그인의 상태와 Yona의 패치
- 원본 tacitknowledge/jquery-pageslide는 마지막 push가 **2015-10**, 별 2개이다(npm 2.0.2는 2022-06에 올라왔으나 저장소 활동은 없다). 사실상 방치되어 있다.
- 공식 2.0.1과 대조한 Yona의 변경(2017-04 v1.6 커밋 `c794532c9` "Introduce two column view mode", `5fe12536a` "natural loading effect"): 두 번 초기화하지 않기, iframe의 `allowtransparency`, 왼쪽 방향의 본문 `margin-left` 움직임 제거, 클릭 이벤트에 이름 공간, **열 때 iframe을 지우고 300ms 뒤 읽기(로딩 효과)**, 닫을 때 `.left-menu` 보이기. 위 동작이 모두 Yona 쪽 변경이다.

### 후보
| 후보 | 내용 | 장점 | 걸림 |
|---|---|---|---|
| **A. 유지** | 그대로 둔다 | 비용 0. jQuery 4 준비는 이미 끝났다(D-103) | 방치된 jQuery 플러그인이 남는다. jQuery 제거(S-14) 때 어차피 손대야 한다 |
| **B. Bootstrap 5 Offcanvas** | `offcanvas-end` 패널에 iframe | 프로젝트가 쓰는 Bootstrap의 부품 | **이 앱의 컴파일된 `bootstrap.css`에는 offcanvas 스타일이 없다**(0건, 템플릿 사용처도 없다. JS는 번들에 있고 CSS만 빠져 있어, 앞선 조사 문서의 "이미 포함"은 JS만 해당했다). Sass 구성에 부품을 더해야 한다. 기본이 backdrop, 포커스 가두기, Esc 닫기, 본문 스크롤 잠금이라 `backdrop: false, scroll: true, keyboard: false`로 꺼야 하고 포커스 가두기가 목록 조작을 방해하는지 시험이 필요하다(간단한 시험은 CSS가 없어 결론을 내지 못했다). 열 때 iframe 지우기, 토글, 왼쪽 메뉴 숨기기는 어차피 직접 코드이다 |
| **C. 표준 팝오버 API** | `<div popover="manual">`에 iframe, `showPopover()`와 `hidePopover()` | 의존 없음, 최상위 층에 그려진다, **`manual`은 바깥 클릭과 Esc로 닫히지 않아 `modal: true`와 같다**, 포커스를 가두지 않고 애니메이션도 없다(`speed: 0`과 같다) | 브라우저 요건(Chrome 114, Firefox 125, Safari 17 이상) |
| **D. 직접 쓰는 고정 패널** | `#pageslide`(`display:none`)와 iframe을 보이고 숨기는 작은 함수(약 40줄)를 `yona.twoColumnMode.js` 안에 둔다. CSS는 지금 `#pageslide` 그대로 | 의존 없음, 동작 변화가 가장 적다, 모든 브라우저 | **직접 구현**이다(규모가 작아 유지 부담은 작다) |
| E. 그 밖의 슬라이드 패널 라이브러리 | 후보를 찾아 보았으나 쓰는 기능이 "고정 패널과 iframe"뿐이라 라이브러리를 들일 이유가 없다 | | 제외 |

### 지켜야 하는 동작(어느 후보든)
제목 링크 클릭으로 열기와 기본 이동 막기, 같은 링크 재클릭으로 닫기, 다른 링크는 iframe 교체와 로딩 gif 노출, 폭 50%와 우측 고정과 기존 모양, 바깥 클릭과 Esc로 안 닫힘, 열릴 때 왼쪽 메뉴와 사용자 정보 숨김과 닫을 때 복원, `history.pushState`와 `NProgress.done`, 2단 보기 확인 상자를 끄면 닫고 이벤트 해제, 목록 갱신(htmx) 뒤에도 새 제목 링크에 다시 연결. **시험이 없으므로 옛 플러그인에서 특성 시험을 먼저 만든다**(S-10의 방식).

### 판단
- 이 부품이 하는 일은 사실상 **"오른쪽에 고정된 iframe 패널을 켜고 끄는 것"**이다. 라이브러리는 필요 없다.
- **D(직접 쓰는 고정 패널)를 권한다.** 동작 변화가 가장 작고 의존이 없다. C(팝오버)는 거의 같은 코드에 브라우저 요건만 더한다. B는 CSS 추가와 기본 동작 끄기, 포커스 시험이 필요해 이득이 없다.
- 다만 **2단 보기는 나중 UI 개편(Vue 컴포넌트, 2단 보기 개선)이 예정**되어 있으므로(그전까지는 htmx, D-96), 그때까지 **A(유지)도 합리적**이다.

### jquery.pageslide 대체 B(Bootstrap 오프캔버스) 시험 구현 결과 (2026-11-09, 브랜치 `trial/pageslide-offcanvas`, 선생님 요청)
- **정정**: 위 조사에서 플러그인을 부르는 템플릿을 5곳이라고 했으나 실제로는 **7곳**이다(`issue/list`, `issue/my-list`, `user/view`, `organization/issue-list`, `organization/board-list`, `board/list`, `pullrequest/list`).
- **방법**: 옛 플러그인에서 특성 시험 10개(`twocolumn.test.mjs`)를 먼저 통과시킨 뒤(옛 스크립트와 플러그인은 `TC_IMPL=old`로 임시 폴더에서 불러온다) 오프캔버스로 구현했고 같은 시험이 통과한다. `bootstrap.scss`에 `vendor/bootstrap/offcanvas`를 더하고(앱의 컴파일 CSS에 0건이던 것이 172건), `#pageslide`를 `offcanvas offcanvas-end` 패널로 만들었다. 옵션은 `backdrop: false, scroll: true, keyboard: false`(배경 없음, 본문 스크롤 유지, Esc로 닫히지 않음)이고 `transition: none`으로 애니메이션이 없다(v1.6의 `speed: 0`).
- **Bootstrap이 기본으로 하던 것 중 걸림이 된 것**: ① **그림자**: Bootstrap의 그림자 믹스인은 `$enable-shadows`가 꺼져 있으면 아무것도 내지 않아 `--bs-offcanvas-box-shadow` 변수만으로는 그림자가 안 나왔다(실제 앱 측정에서 발견, `box-shadow`를 직접 둠). ② **`$('#pageslide').is(':visible')`**: 오프캔버스는 `display`가 아니라 `visibility`로 숨기므로 jQuery `:visible`이 닫힌 패널도 참으로 본다. 열림 여부는 모듈 안의 상태 값으로 관리한다. ③ 포커스 가두기: `scroll: true`이고 배경이 없으면 켜지지 않아 목록 조작을 방해하지 않았다(시험으로 확인: 바깥 목록 클릭과 페이지 스크롤이 된다).
- **실제 앱 확인**(이슈 목록, Chromium과 Firefox): 열린 패널의 위치(오른쪽 고정, 폭 50% = 700px, 높이 100%), `z-index` 999999, 그림자, iframe 주소, 주소 표시줄 변경, 왼쪽 메뉴 숨김과 복원, Esc로 안 닫힘, 같은 링크 재클릭으로 닫힘이 변경 전(옛 플러그인을 되살려 같은 앱에서 측정)과 모두 같았다. Firefox는 화면이 픽셀 단위로 같고, Chromium은 왼쪽 목록의 배치 수치가 같음을 확인했다(스크린샷에는 상단 알림 줄의 나타나는 시점 차이로 보이는 얕은 차이가 있었다).
- **회귀 시험**: JS 전체 357개, 번들 8개, `web`, `audit`, `i18n` **9분 18초** 통과.
- **상태**: **채택하여 `next2`에 합쳤다**(D-115, A-432, 2026-11-09).

## jquery.form 확인 (2026-11-09, 선생님 요청)

### 쓰임
- 라이브러리는 jquery.form **3.09**(2012-04, 1,085줄)이다(공식 최신은 4.x). 번들 `yona-layout.js`에 들어 있고, **호출은 `yona.Common.js`의 `$yona.sendForm` 한 곳**(`welForm.ajaxForm({...})`과 `submit()`)뿐이다. 앱의 다른 곳은 `ajaxSubmit`, `ajaxForm`, `formSerialize` 등을 쓰지 않는다.
- `$yona.sendForm`의 하는 일: 숨은 `<form>`을 만들어 문서에 붙이고, `htData`의 키마다 `<input type="hidden">`을 만들어 넣은 뒤 `ajaxForm`으로 **XHR 요청 하나를 보내고** 끝나면 폼을 지운다. 파일 업로드가 아니라 **작은 폼 전송**(관심 등록, 역할 변경, 파일 삭제, 후보 조회)이다.
- 호출은 **11곳, 파일 9개**: `Files.js`(파일 삭제), `ui.Typeahead.js`(후보 조회), `site.MassMail.js`(메일 주소 조회), `git.View`, `board.View`, `code.Diff`, `code.SvnDiff`(보기 관심 등록과 해제), `project.Member`(2), `organization.Member`(2)(역할 변경과 후보 조회).
- 쓰는 옵션: `htOptForm`의 `method`(post 기본, get, POST)와 **`enctype: multipart/form-data`(`Files.js`의 파일 삭제 한 곳)**, `sDataType: "json"`(후보 조회 3곳과 메일 주소), `htData`, 콜백 `fOnLoad(data, textStatus, jqXHR)`와 `fOnError`.
- 콜백 인자를 실제로 쓰는 곳: `oXHR.getResponseHeader('Content-Range')`(Typeahead, 프로젝트와 조직 구성원의 후보 조회), `oXHR.responseText`(조직 구성원의 역할 변경), `data`(메일 주소 배열). 나머지는 인자를 쓰지 않는다.

### Yona의 패치와 지금
- v1.6 커밋 제목은 "FileUploader의 XHR 업로드(MSIE) 수정"이다. 그 업로드 경로는 S-06에서 네이티브 XHR로 바꿔 **이 패치가 쓰이던 길은 사라졌다.** 남은 용도 중 `multipart` 삭제 한 곳은 파일 입력이 없어도 jquery.form이 `FormData` 경로로 보낸다.

### 발견한 결함(v1.6부터)
- `sendForm`은 값을 `'<input type="hidden" name="' + sKey + '" value="' + htData[sKey] + '">'`처럼 **문자열로 이어 붙인다.** 값에 `"`가 있으면 속성이 끊기고, `<` 등은 HTML로 읽힌다. 지금 호출처의 값은 숫자와 짧은 글자(후보 질의 `query`는 사용자 입력)라 드러나지 않았지만 **검색어에 따옴표를 넣으면 요청 값이 잘린다**(확인하지는 않았다).

### 후보
| 후보 | 내용 | 비고 |
|---|---|---|
| **A. 유지** | jquery.form 3.09를 그대로 | 방치된 2012년 판이 남는다(jQuery 4 준비는 이미 끝남, D-103) |
| **B. `fetch`로 다시 쓰기(권장)** | `sendForm`을 숨은 폼 없이 `fetch`로 보낸다. `GET`은 질의 문자열, `POST`는 `URLSearchParams`(urlencoded), `enctype`이 multipart면 `FormData`. `X-Requested-With`와 `Accept`를 넣고(`yona.Request`와 같은 방식), 토큰은 `yona.Csrf.js`가 `fetch`에 이미 싣는다. 콜백은 같은 모양으로 `fOnLoad(data, "success", xhr모양 객체)`를 준다(`getResponseHeader`, `responseText`, `status`) | 값 이어 붙이기 결함이 사라진다. jQuery 의존이 줄어 S-14의 한 조각이다. 콜백 모양을 맞추는 코드가 필요하다 |
| **C. `$.ajax`로 다시 쓰기** | 같은 모양을 `$.ajax`로. 콜백이 진짜 `jqXHR`라 호환이 가장 쉽다 | jquery.form은 사라지지만 jQuery `$.ajax`는 S-14에서 다시 바꿔야 한다 |

### 동작 차이로 볼 것(B나 C의 값 비교 대상)
`dataType: "json"`일 때 빈 응답은 jQuery가 오류로 보지만 `fetch`는 직접 처리해야 한다. `dataType`이 없을 때 jQuery는 응답 `Content-Type`으로 알아서 읽는다. HTTP 4xx, 5xx와 네트워크 오류의 콜백 인자 모양. 콜백 안의 `this`. 값에 따옴표가 든 경우(결함이 고쳐진다). `multipart` 삭제 요청의 본문 형식이 같은지(서버가 `_method=delete`를 읽는 방식).

### 판단
- **B를 권한다.** 쓰이는 곳이 한 함수뿐이고 호출 11곳이 쓰는 인자가 세 가지뿐이라 호환 객체로 감쌀 수 있다. 옛 jquery.form에서 특성 시험을 먼저 만들면(S-10, pageslide와 같은 방식) 안전하게 바꿀 수 있다.
- 직접 쓰는 코드는 `sendForm` 안의 `fetch` 호출과 응답 모양 맞추기(약 50줄)이다.

**상태**: B안으로 구현했다(D-116, A-433, 2026-11-09). 실제 앱 확인에서 주소의 `&amp;` 회귀를 찾아 고쳤다.

## Viewer.js와 관련 모듈 확인 (2026-11-10, 선생님 요청)

### 쓰임
- 파일은 `lib/viewerjs/`의 `viewer.js`(1.6.1, 2020-06, 109KB), `viewer.css`, `jquery-viewer.js`(jQuery 래퍼 1.0.1) 세 개이다. 공식 최신은 viewerjs **1.15.2**(2026-10)이고 `jquery-viewer`는 1.0.1이 최신이라 래퍼는 그대로다.
- 불러오는 곳은 `layout/layout.html` 한 곳(CSS 1줄, 스크립트 2줄)이고 호출도 그 안의 한 곳뿐이다: `Viewer.setDefaults({transition: false})` 후 `.markdown-wrap` 안의 `img`마다 `$(".markdown-wrap").viewer()`. 마크다운 본문의 이미지를 누르면 확대 보기(확대, 회전, 이동 등)가 열린다. 다른 JS는 `Viewer`를 직접 부르지 않는다(`yona.Markdown.js`의 `_setViewer`는 이름만 비슷하고 무관하다).
- 번들(`yona-*.js`)에 들어 있지 않고 템플릿에서 따로 불러온다. 시험은 `jquery4.test.mjs`의 래퍼 한 건뿐이고 화면 동작 시험은 없다.

### Yona의 패치와 지금
- v1.6 커밋 `7b4798bc0`(2020-08, "Prevent shifting renderList transform") 한 줄이다. `renderList`(아래쪽 썸네일 목록)의 `translateX: (viewerData.width - width) / 2 - outerWidth * i`를 `... - outerWidth`로 바꿔 목록이 항목 번호만큼 더 밀리지 않게 했다. 래퍼는 jQuery 4 준비(A-410) 때 `isFunction`을 표준으로 바꾼 것만 다르다.
- **공식 1.15.2에는 이 코드가 없다.** `renderList`가 다시 쓰여 항목의 `offsetLeft`(세로 썸네일이면 `offsetTop`)와 `gutter`로 가운데 맞춤을 계산한다. 그래서 Yona의 한 줄을 그대로 옮길 수 없고, **새 판에서 같은 문제가 남아 있는지는 시험해 봐야 안다**(확인하지 않았다).
- CSS도 공식 판과 크게 다르다(1.6.1과 1.15.2의 차이가 658줄 추가, 235줄 삭제). 클래스 이름이 바뀐 곳이 있어 Yona의 `_page.scss` 등에서 `viewer-` 클래스를 덮어쓴 곳이 있는지 같이 확인해야 한다.

### 후보
| 후보 | 내용 | 비고 |
|---|---|---|
| A. 유지 | 1.6.1에 패치 한 줄 | 6년 된 판. 수정이 계속된 1.15.2의 개선(접근성, 터치, 버그 수정)을 못 받는다 |
| **B. 1.15.2로 올리기(권장)** | 공식 배포 파일(`viewer.js`, `viewer.css`)을 들이고 래퍼 1.0.1은 유지(jQuery 4 대응 한 줄 포함) | 패치를 옮기지 않고, 새 판에서 썸네일 목록 위치 문제를 재현해 본 뒤 남아 있을 때만 고친다 |
| C. 표준 `<dialog>`로 직접 만들기 | 확대, 회전, 이동까지 직접 구현 | 기능이 많아 직접 쓰는 코드가 크다(수백 줄). `medium-zoom`은 확대만 되어 기능이 줄어든다(기존 검토) |

### 판단
- B를 권한다. 쓰이는 곳이 한 곳이고 Yona의 패치는 한 줄이며, 새 판에서 그 코드가 사라졌다.
- 진행한다면 순서는 이렇다: 옛 판으로 이미지 클릭 열기, 썸네일 목록 가운데 맞춤, 닫기, 다음과 이전 이동의 특성 시험을 만들고 → 1.15.2를 들여 같은 시험을 통과시키고 → 썸네일 위치는 실제 화면(Playwright)에서 옛 판과 대조한다. 스타일 차이는 일부 허용된다는 기준(S-10)을 적용한다.

**상태**: B안으로 올렸다(D-117, A-434, 2026-11-10). 공식 1.15.2는 처음 열 때 활성 썸네일을 가운데에 맞춘다. 배경 투명도가 0.85에서 0.5로 바뀐 것은 덮어써 v1.6과 같게 했다.
