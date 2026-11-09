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

