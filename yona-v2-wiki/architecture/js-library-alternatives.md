---
title: JS 라이브러리 대체 후보 조사
type: architecture
sources:
  - js-upgrade-plan.md
  - decisions.md
updated: 2026-11-07
---

# JS 라이브러리 대체 후보 조사 (미결정, 구현 전)

`D-91`(JS 라이브러리 업그레이드)의 후속 조사이다. 선생님이 (1) 유틸리티성 라이브러리는 같은 기능의 경량 라이브러리를, (2) 없어지는 라이브러리는 대체 라이브러리를 찾아 달라고 하셨고(2026-11-07), **조사만 했으며 코드는 바꾸지 않았다.** 어느 항목을 계획에 넣을지는 선생님이 정하신다. 크기는 jsDelivr의 `+esm` 최소화 번들을 gzip으로 전송한 값이다(후보의 의존 포함, 비교용 어림). 유지 상태는 npm의 마지막 갱신일(2026-11-07 조회)이다.

## 1. 없어지는(새 버전이 없거나 정리할) 라이브러리의 대체

| 라이브러리 | 상태 | 앱의 사용 | 대체 후보 |
|---|---|---|---|
| **jquery.tmpl 1.0.0** | 공식 폐기 | 호출 20곳(자체 도우미 4곳 제외), 템플릿 18블록 | 3절. 문법이 단순 보간뿐이라 쉽다 |
| jquery.browser 0.0.6 | 폐기 | 2줄, 2개 파일 | 제거(기능 감지) |
| Jcrop 0.9.12 | 유지 끝 | 14줄, 2개 파일(프로필 이미지 자르기) | **Cropper.js 2**(2026-10 갱신, jQuery 불필요) |
| At.js 1.5.3(멘션 자동완성) | 유지 끝, jQuery 4 제외 선언 | 5줄, 2개 파일, 템플릿 14곳 | Tribute.js 5.1(8.1KB, 2022년 이후 갱신 없음), Tagify 4.39(19.9KB, 2026-09 갱신, 멘션 모드), `@textcomplete/core`(2023), CodeMirror 6 자동완성(`components` 에디터가 이미 사용) |
| jquery-pjax(2012년 사본, 전 화면 이동) | 유지 끝(2022) | 6줄, 2개 파일 | **htmx** `hx-boost`(18.4KB), **Turbo Drive**(25.4KB), **swup**(7.6KB), 모두 2026년 갱신. 이동 방식이 바뀌는 큰 일이라 별도 프로젝트 |
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
| Pikaday | 유지(5.9) / 네이티브 `<input type=date>` | 5.9 / 0 | 더 가벼운 대안 없음 |
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

jquery.tmpl 대체(3절)는 **라이브러리 업데이트(Yona가 고친 벤더 파일의 패치 이식 등, `js-upgrade-plan.md`)를 먼저 끝낸 뒤**에 한다. 템플릿 엔진은 **Mustache로 결정**되었다(`decisions.md` D-94, 2026-11-07). underscore는 이 앱에서 `_.` 호출이 없어 다른 용도가 없다. 지금 작업하라는 지시는 아니다. 참고 확인: jquery.tmpl 1.0.0은 jQuery 3.7.1에서 동작한다(문자열, 배열, 요소 템플릿, `{{if}}`, `appendTo`를 jsdom에서 실행해 확인). jQuery 4에서는 `jQuery.trim`(`jquery.tmpl.js` 325번째 줄) 한 줄이 걸린다.
