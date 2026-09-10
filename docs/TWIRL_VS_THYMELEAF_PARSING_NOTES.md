---
id: twirl-vs-thymeleaf-parsing-notes
type: reference
created: 2026-09-11
status: active
relates_to: [parity-index]
---

# Twirl vs Thymeleaf 파싱/렌더링 모델 차이

`yona`(Play/Scala, Twirl 템플릿)를 `yona`(Spring Boot/Kotlin, Thymeleaf)로 포팅하는 과정에서,
"내용은 그대로 옮겼는데 두 엔진이 그 내용을 서로 다르게 파싱/렌더링해서 실제 브라우저 동작이
달라지는" 클래스의 버그가 반복해서 나왔다(P3-49 참고). 이 문서는 두 엔진의 실제 동작 차이를
정리해, 앞으로도 같은 함정을 밟지 않게 하는 참고 문서다. 각 항목은 이 저장소에서 실제로
발견·재현한 사례로 근거를 남긴다.

## 1. 실행 모델 자체가 다르다

**Twirl(Play)**: `@`로 시작하는 지시문(`@if(...){...}`, `@for(...){...}`, `@x`, `@Html(x)`)은
템플릿을 **컴파일 시점에 Scala 함수로 변환하는 순수 텍스트 치환**이다. HTML 파서가 전혀
관여하지 않는다 — 템플릿은 Twirl 입장에서는 그냥 "군데군데 Scala 표현식이 끼어 있는 문자열"이다.
그래서 `@if(){}`가 `<script>` 태그 안이든 밖이든, `<div>` 속성값 안이든 텍스트 노드든 위치와
무관하게 **항상 동일하게** 치환된다.

- `@x` — 기본적으로 HTML 이스케이프해서 출력한다(`play.twirl.api.HtmlFormat.escape`).
- `@Html(x)` — 명시적으로 이스케이프를 건너뛰고 raw HTML을 그대로 출력한다.

**Thymeleaf(Spring)**: 서버가 실제로 템플릿을 **HTML5(또는 XML) 파서로 파싱해 DOM과 유사한
이벤트 트리(모델)를 구성**한 뒤, 그 트리를 순회하며 `th:*` 속성 프로세서/pseudo-element
(`<th:block>`)를 노드 단위로 처리한다. 이게 핵심 차이다 — Thymeleaf에게 템플릿은 "파싱해야 할
진짜 (X)HTML 문서"다.

- `th:text` — 계산한 문자열을 HTML 이스케이프해서 태그 내용으로 넣는다(Twirl `@x`와 동급).
- `th:utext` — 이스케이프 없이 그대로 삽입한다(Twirl `@Html(x)`와 동급).

## 2. `<script>`/`<style>`는 HTML5 스펙상 "raw text" 요소다 — 이게 사고의 근원

HTML5 파싱 스펙은 `<script>`/`<style>`/`<textarea>`/`<title>` 등을 **raw text(또는
escapable raw text) 요소**로 정의한다: 여는 태그부터 정확히 일치하는 닫는 태그까지의 내용은
자식 엘리먼트로 파싱되지 않고 통째로 텍스트로 취급된다. Thymeleaf는 실제 HTML5 파서를 쓰므로
이 규칙을 그대로 따른다.

- **속성 프로세서가 여는 태그 자체에 있으면 동작한다.** `<script th:if="${x}">...</script>`처럼
  `th:if`가 `<script>` 태그 자신의 속성이면, 그 태그는 여전히 "엘리먼트"로 파싱되므로 속성
  프로세서가 정상 처리된다.
- **태그 "내용물" 안에 중첩된 pseudo-element나 속성은 전혀 처리되지 않는다.** `<script>` 내용
  안에 `<th:block th:if="...">...</th:block>`나 `<div th:if="...">`를 문자 그대로 적으면,
  Thymeleaf는 그 문자열을 스크립트의 raw text 일부로만 보고 **파싱하지 않은 채 그대로
  출력**한다. 결과 HTML에 `<th:block ...>` 리터럴이 그대로 남고, 브라우저가 그 지점에서
  `SyntaxError: Unexpected token '<'`를 던진다 — JS는 블록 전체를 먼저 파싱한 뒤 실행하므로
  이 문법 오류가 **스크립트 블록 전체의 실행을 막는다**(블록 앞부분 코드도 함께 죽는다).

  실사례: `issue/view.html`(P3-49, 커밋 `e894e8e82`)이 정확히 이 버그였다 — 담당자 지정
  스크립트 호출이 `<th:block th:if="${isAllowedUpdate}">` 안에 있어서, 권한이 있는 사용자의
  이슈 보기 화면에서 그 스크립트 블록 전체(watch/unwatch, 댓글 상태전환, 단축키 등)가 죽어
  있었다. 수정: 조건부 호출만 별도의 `<script th:if="${isAllowedUpdate}">` 태그로 분리 —
  `th:if`가 태그 자체 속성이라 raw text 제약을 받지 않는다.

  Twirl에는 이 문제 자체가 존재하지 않는다 — `@if(x){...}`는 HTML 파싱과 무관한 순수 텍스트
  치환이라 `<script>` 안팎을 가리지 않는다. **즉 이건 순수하게 Play→Thymeleaf 리라이트
  과정에서 `@if(...){...}`를 `<th:block th:if="...">...</th:block>`로 기계적으로 1:1
  치환하며 생긴 포팅 버그 클래스다.**

- **이번 세션 전수 재스캔(2026-09-11) 결과**: `src/main/resources/templates`
  (182개 파일) 전체의 `<script>`/`<style>` 블록 내부를 HTML 주석을 먼저 제거한 뒤(주석 안의
  예시 코드가 오탐을 일으켰다 — 아래 "스캔 방법론 교훈" 참고) 스캔했다. **중첩
  pseudo-element(`<th:block>` 등) 리터럴은 0건**(이미 수정됨). 안쪽 태그에 속성 프로세서가
  붙은 채 미처리로 남은 사례 2건을 새로 발견해 수정했다(`site/layout.html`의
  `tplAttachedFile`/`tplDropFilesHere` — 아래 4번 항목).

### 스캔 방법론 교훈

grep/정규식으로 "스크립트 안에 `<태그 th:*=...>` 문자열이 있는가"를 스캔할 때, HTML
주석(`<!-- ... -->`) 안에 예시로 적힌 마크업 문자열(예: 이 저장소의 `code/view.html` 상단
주석이 "`<style>`를 `<head>` 안에 두면..."이라고 설명하며 실제 `<style>` 문자열을 언급하는
경우)이 스크립트/스타일 블록의 시작처럼 오매칭될 수 있다 — 주석을 먼저 제거하고 스캔해야
오탐을 피한다.

## 3. `th:inline="javascript"`의 자동 JS 문자열 직렬화 — 두 번째 사고 지점

Thymeleaf는 `<script>` 요소에 대해 기본적으로 **AUTO 인라인 모드**를 적용한다(태그 이름이
`script`이면 `type` 속성과 무관하게 적용됨 — `type="text/x-jquery-tmpl"`처럼 실행되지 않는
스크립트 타입도 대상이다, 아래 4번 참고). AUTO 모드에서 `[[${expr}]]`/`[(${expr})]` 같은
"인라인 출력 표현식"을 만나면, 그 표현식이 위치한 컨텍스트(스크립트 안이면 JAVASCRIPT 인라인
모드)에 맞춰 **가공해서** 출력한다:

- 문자열(String) 결과 → **자동으로 큰따옴표로 감싸고 JSON 문자열 이스케이프까지 적용**한다
  (`/`는 `\/`로, 특수문자는 `\uXXXX` 등으로). 즉 `[[${name}]]`이 `"홍길동"`으로 나온다 —
  개발자가 직접 따옴표를 안 감싸도 이미 완전한 JS 문자열 리터럴이 나온다.
- 숫자/불리언 결과 → JS 리터럴 그대로(따옴표 없이) 출력한다.

**함정**: 레거시 코드(또는 초기 포팅 코드)가 "표현식 결과는 그냥 텍스트가 치환될 뿐이다"라고
가정하고 `"[[${x}]]"`처럼 **수동으로 따옴표를 감싸 쓰면**, 실제로는 `""가공된값""`처럼
따옴표가 중복되거나(URL/메시지 문자열처럼 공백이 있는 값이면 `""a b""` → 토큰이 이어 붙어
**SyntaxError**), 슬래시가 `\/`로 이스케이프된 채 노출된다.

실사례(이번 세션에 총 4곳에서 발견, 전부 동일 패턴 — 전수조사는 아래 참고):

- `issue/view.html`(P3-49): 1차 시도 때 `th:inline="javascript"` + 블록주석 문법으로 고치며
  겪은 회귀 — 같은 스크립트 블록의 기존 `"issueId": "[[${issue.id}]]"`류가 `""OPEN""`,
  `"\/admin\/..."`로 깨짐. 최종적으로 조건부 호출을 별도 태그로 분리해 `th:inline` 자체를
  그 좁은 범위에만 적용하는 방식으로 우회했다(전역 파일에 `th:inline="javascript"`를 켜지
  않는 한 이 함정 자체가 발동하지 않는다).
- `pullrequest/view.html`(이번 세션 신규 발견·수정): `confirm("[[#{pullRequest.merge}]]?")`,
  `alert("[[#{pullRequest.merge}]] failed: " + msg)`, `$.post("[[@{...review}]]", ...)`,
  `$.post("[[@{...unreview}]]", ...)` 4곳 전부 이 패턴. `pullRequest.merge` 메시지 값
  "코드 병합"(공백 포함)이 치환되면 `""코드 병합"?"`가 되어 **SyntaxError** — 이 스크립트
  블록 하나가 watch 토글/담당자·라벨 변경/리뷰 등록/승인/merge 버튼 전부를 담당해서, 깨지면
  PR 상세화면의 핵심 상호작용이 전부 죽는다. **가장 심각한 사례.**
- `pullrequest/clone.html`(이번 세션 신규 발견·수정): 포크 완료 인터스티셜 화면의
  `cloneUrl`/`cloneParam`/실패 `alert` 전부 이 패턴 — 3초 뒤 `doClone()`을 호출해야 실제
  포크가 완료되는데, 스크립트가 깨지면 "복제 중입니다" 화면에서 영원히 멈춘다.
- `common/commentDeleteModal.html`(이번 세션 신규 발견·수정): `alert('[[#{common.comment.delete}]]
  failed')` — 이 경우는 작은따옴표로 감쌌고 값에 공백이 있어도 SyntaxError는 아니었다(작은
  따옴표 문자열 안에 리터럴 큰따옴표가 섞여도 문법상 유효) — 대신 `'"댓글 삭제" failed'`처럼
  따옴표 문자가 겹쳐 보이는 경미한 표시 결함이었다.

**안전한 수정 패턴 2가지**(상황에 따라 선택):

1. **수동 따옴표를 제거하고 인라인 표현식이 스스로 JS 리터럴을 만들게 둔다** —
   `"[[${x}]]"` → `[[${x}]]`. 가장 간단하고, 파일 전체 `th:inline="javascript"`를 그대로
   둬도 안전하다(위 3개 사례에서 채택). 문자열 결합이 필요하면 `[[${x}]] + '...'`처럼 JS
   연산자로 이어붙인다.
2. **조건부 블록처럼 "그 부분만 인라인 모드가 필요한" 경우, 그 부분만 별도
   `<script th:if="...">`/`<script th:inline="javascript">` 태그로 분리**해 원본 스크립트의
   다른 `[[...]]` 표현식(이미 수동 따옴표 관례로 작성됐을 수 있는)에 영향을 주지 않는다
   (issue/view.html에서 채택).

**전수조사 방법과 결과(2026-09-11)**: `grep -rln 'th:inline="javascript"'` 로 대상 파일을
추리고(46개), 각 파일에서 `"[[`/`'[[` (수동 따옴표 바로 뒤에 인라인 토큰) 패턴이 있는지
확인했다. 3개 파일에서 발견해 전부 수정했다(위 목록). 나머지 43개 파일은 이 패턴이 없거나,
있어도 숫자/불리언 타입(따옴표가 필요 없는 케이스)이라 안전했다.

## 4. `type="text/x-jquery-tmpl"` 같은 "실행되지 않는" `<script>`도 raw text 취급은 동일하다

`<script type="text/x-jquery-tmpl">`는 브라우저가 JS로 실행하지 않고 클라이언트 템플릿
엔진(jQuery.tmpl)이 `.html()`로 읽어가는 순수 마크업 컨테이너다. 그래도 **HTML5 파서 입장에서는
태그 이름이 `script`라는 사실만으로 raw text 요소로 취급**하므로, 1번 항목의 제약이 동일하게
적용된다 — `<script>` 내부에 있는 `<span th:text="#{...}">` 같은 속성 프로세서는 전혀
처리되지 않는다(문법은 안 깨지지만 — `th:text`는 pseudo-element가 아니라 존재하는 태그의
속성이라 브라우저 파싱 자체는 깨지지 않는다 — 값이 항상 템플릿에 적힌 정적 기본값(fallback
텍스트)으로만 남는다).

실사례(이번 세션 신규 발견·수정): `site/layout.html`의 `tplAttachedFile`/`tplDropFilesHere`
(전역 첨부파일 업로드 jQuery 템플릿)가 `<span th:text="#{common.attach.clickToPost}">본문에
넣기</span>` 형태였다 — `th:text`가 스크립트 raw text 안에서 전혀 처리되지 않아 **로케일과
무관하게 항상 한국어 기본 문구만 노출**되는 i18n 버그(기능은 안 죽지만 다국어 사용자에게
항상 한국어가 보임). Twirl(`app/views/common/fileUploader.scala.html`)은 같은 위치에서
`@Messages("common.attach.clickToPost")`를 썼는데, 이건 순수 텍스트 치환이라 스크립트
안에서도 로케일별로 정상 동작했다.

**수정**: `<script th:inline="text">`로 그 태그만 텍스트 인라인 모드를 켜고, 내용을
`th:text` 속성 대신 `[[#{...}]]` 인라인 토큰으로 바꿨다(이 저장소 `site/layout.html`의
다른 곳 — 로그인 유저 메뉴의 "내 이슈" 뱃지 — 가 이미 같은 `th:inline="text"` +
`[[#{...}]]` 패턴을 쓰고 있어 기존 관례와 일치한다). `th:inline="text"`는 `th:inline=
"javascript"`와 달리 JSON 문자열 자동 이스케이프(3번 항목의 함정)를 적용하지 않고
HTML 이스케이프만 적용한다 — `th:text`와 동급의 안전한 대응이다.

## 5. `th:xxx="${possiblyNull}"`의 속성 자동 생략 — 버그는 아니지만 포팅 시 함정의 근원

Thymeleaf의 속성 프로세서(`th:attr`, `th:class`, `th:value`, 커스텀 `th:xxx` 등)는 표현식
결과가 `null`이면 **그 속성 자체를 렌더링하지 않는다**(빈 문자열 `""`을 렌더링하는 것과
다르다 — 속성이 아예 생략된다). Twirl에는 이런 자동 동작이 없다 — 같은 효과를 내려면
`@if(x != null){attr="..."}`처럼 조건문을 직접 써야 한다.

이 차이 자체는 버그가 아니다. 하지만 **"레거시의 공용 헬퍼 함수가 항상 기본값을 채워주던
지점을, raw fragment를 직접 호출하며 그 인자를 `null`로 넘기면 자동 생략 효과 때문에
그 속성이 조용히 사라지는" 포팅 버그 패턴**의 근원이 된다.

실사례(P3-49): `common/fileUploader.scala.html`(legacy)은 `common.uploadForm(type, id,
"upload")`를 항상 `formId="upload"`로 하드코딩해서 호출했다. 포팅본이 이 헬퍼 대신
`common/uploadForm :: uploadForm(type, id, formId)` 프래그먼트를 **직접** `formId=null`로
호출하면서, `th:id="${formId}"` 같은 속성이 `formId`가 null일 때 자동 생략돼 `id="upload"`가
렌더링되지 않았다 — 결과적으로 전역 `$("#upload")` 셀렉터가 아무것도 못 찾아 파일
업로드(클릭/드래그드롭/붙여넣기) 전체가 죽어 있었다. 수정: `formId` 인자를 legacy와 동일하게
`'upload'`로 명시.

## 6. `th:utext`/`th:text` vs `@Html`/`@x` — 보안(XSS) 관련 대응표

| Twirl | 의미 | Thymeleaf 대응 |
|---|---|---|
| `@x` (기본) | HTML 이스케이프 | `th:text` |
| `@Html(x)` | 이스케이프 없이 raw 삽입 | `th:utext` |

포팅 규칙: legacy가 `@Html(...)`을 쓴 자리는 대부분 (1) 서버가 이미 새니타이즈한 마크다운
렌더링 결과(`Markdown.render(...)` → 포팅본 `markdownService.render(...)`, 내부에서
OWASP Java HTML Sanitizer 정책을 거친다), 또는 (2) 서버가 관리하는 `messages.properties`의
신뢰된 HTML 조각(`Messages("...")`에 `<b>`/`<a>` 등이 박혀 있는 i18n 메시지), 또는 (3)
서버가 직접 조립하는 안전한 마크업(아바타 `<img>`, 브랜치 라벨 `<span>` 등)이다 — 이 세
경우는 `th:utext`로 옮기는 게 legacy와 동등하고 올바르다.

**감사 결과 확인한 예외 케이스**: `index/partial_notifications.html`의 알림 메시지
(`th:utext="${noti.message}"`)는 legacy가 `@Html(HtmlUtil.defaultSanitize(noti.getMessage
...))`로 **반드시 새니타이즈를 거쳐** 렌더링했는데, 포팅본은 새니타이즈 없이 그대로
`th:utext`에 넣고 있었다 — `noti.message`(예: `NEW_COMMENT` 이벤트의 경우 댓글 작성자가
그대로 입력한 원문)가 사용자 입력을 그대로 담을 수 있어 저장형 XSS로 이어지는 실제 회귀였다
(이번 세션에 발견·즉시 수정 — `IndexController.mapNotificationsToView()`에서
`markdownService.sanitize(...)`를 거치도록 복구, 아래 감사 보고서 참고).

반대로 legacy가 `@Html(commitMsg...)`/`@Html(Branches.branchInHTML(branch))`처럼
**새니타이즈 없이** raw HTML을 넣던 자리(커밋 메시지, 브랜치명 — 둘 다 git 자체가 제약하는
문자셋 안에서 나마 이론상 위험 문자가 섞일 수 있는 값)는 포팅본도 동일하게 새니타이즈 없이
옮겨져 있다 — **legacy와 동일한 기존 위험이 그대로 유지되는 것**이라 "포팅 과정에서 새로
생긴 회귀"는 아니다(다만 legacy 자체의 사전 취약점이므로 이 감사 범위 밖으로 분류했다 — 별도
검토가 필요하면 새 티켓으로 다뤄야 한다).

## 7. boolean 속성(checked/selected/disabled/readonly/multiple)

Thymeleaf의 `th:checked`/`th:selected`/`th:disabled`/`th:readonly`/`th:multiple`은 HTML5의
boolean 속성 규칙을 그대로 따른다 — 표현식이 `true`면 속성을 (값 없이, 혹은 `="checked"`
형태로) 렌더링하고, `false`거나 `null`이면 속성 자체를 아예 생략한다. Twirl은 이런 전용
지시문이 없어 `@if(x){checked="checked"}`처럼 직접 조건문으로 짜야 한다 — 결과적으로 같은
효과를 내지만, 포팅 시 조건식 자체(예: 널 처리, 반대 조건 사용)를 잘못 옮기면 값이 뒤집힐
위험은 있다. 이번 세션에서는 낮은 우선순위로 지정돼 전수 대조까지는 하지 못했다(101곳,
`docs/TWIRL_THYMELEAF_RENDERING_AUDIT_2026-09-11.md`의 "남겨둔 항목" 참고).
