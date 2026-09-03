# 자바스크립트 모듈 작성 가이드

legacy Yona의 `docs/ko/technical/javascript-module-guide.md`를 옮김. **이 문서가 설명하는
`yobi.*` 네임스페이스·모듈 패턴은 yona에도 그대로 남아 있다** — 화면(Thymeleaf 템플릿)을
legacy `.scala.html`과 최대한 동일하게 옮기는 원칙에 따라, 정적 자산인
`src/main/resources/static/javascripts/`의 jQuery 기반 `yobi.*` 모듈들도 대부분 그대로
이식됐다(파일명까지 legacy와 동일, 예: `common/yobi.Mention.js`, `service/yobi.project.New.js`).
프론트엔드 자체를 새 프레임워크(React/Vue 등)로 다시 짜지 않았다.

## 명명 규칙

[javascript-naming-convention.md](javascript-naming-convention.md) 참조.

## 폴더 구조

- **`javascripts/lib`** — jQuery를 포함한 프레임워크, 라이브러리 파일
- **`javascripts/common`** — 공통 코드, UI Components. 함수 또는 객체로 작성한다
- **`javascripts/service`** — 각 페이지에 사용되는 자바스크립트 모듈. 함수로 작성한다
- **`javascripts/deprecated`** — legacy에 있던 폴더. yona의 현재 `static/javascripts/`에는
  이 폴더가 없다(사용되지 않는 코드는 아예 옮기지 않았다).

## 코드 구조

- 초기화 함수는 `_init()`으로 통일하고, 이 함수 내에서 `_initVar`, `_initElement`,
  `_attachEvent`를 호출한다. 각 함수의 순서는 필요에 따라 배치할 수 있고, 필요치 않으면
  작성하지 않아도 무방하다.
- 모듈 내에서 사용되는 변수는 `htVar`의 멤버 변수로 할당하며, 초기화 단계를 수행하는 함수명은
  `_initVar()`로 한다.
- 모듈 내에서 사용되는 HTML Element 변수는 `htElement`의 멤버 변수로 할당하며, 초기화 함수명은
  `_initElement()`로 한다.
- 특별한 제약이 없다면 이벤트 핸들러 지정은 `_attachEvent`에서 일괄 수행한다.
- UI 컴포넌트 등 공통 코드는 명명 규칙만 지키면 코드 구조를 자유롭게 구성해도 무방하다.

## `$yobi.loadModule` 사용법

`yobi.module.Name`이라는 모듈을 정의했다면 아래처럼 쓴다.

- 첫 번째 인자: `yobi.`을 제외한 모듈 이름
- 두 번째 인자: 해당 모듈 초기화 함수에 넘길 옵션 객체(Object)

```js
$yobi.loadModule("module.Name", {
    "sOption": "Option Value"
});
```

현재 페이지에서 해당 모듈을 찾을 수 없으면(즉 `<script>` 태그로 명시적으로 포함하지 않았으면)
`javascripts/service`에서 자동으로 동적 로딩을 시도한다. 이미 `<script>` 태그로 포함되어
있으면 동적 로딩은 시도하지 않는다. 동적 로딩 시도 경로는 `javascripts/service/yobi.(module.Name).js`이다.

파일 로딩이 끝나 모듈 코드를 쓸 수 있게 되면 자동으로 초기화를 시도한다.

```js
new yobi.module.Name(htOption)
```

(`htOption`은 `$yobi.loadModule()`의 두 번째 인자와 동일하다.)

`$yobi.loadModule()`은 모듈 함수를 실행하는 역할만 한다 — 별도 인터페이스가 필요한 게 아니라서
모듈 내부 함수 구조가 로딩 자체에 영향을 주지는 않는다.

## 기타

- 최상위 객체에 대한 prototype 확장은 사용하지 않는다 — 모든 코드에 영향을 줄 수 있고
  유지보수를 어렵게 만든다.
- 전역 함수·전역 변수는 최소화한다. 네임스페이스로 유효 범위를 한정한다.
- HTML 템플릿은 되도록 자바스크립트 파일 내에 포함하지 않는다.
- HTML 템플릿은 정적 페이지에 `<script type="text/x-jquery-tmpl">` 형태로 두고, 자바스크립트는
  그 내용을 활용하는 형태로 작성한다. 문법은 [jquery-tmpl](https://github.com/BorisMoore/jquery-tmpl) 참고.
- 단순 문자열 치환만 필요하면 `$.tmpl` 대신 `$yobi.tmpl` 함수를 쓴다.

```html
<script type="text/x-jquery-tmpl" id="tplItem">
    <div>${name} ${email}</div>
</script>
<script>
var sTpl = document.getElementById("tplItem").text;
var htData = {"name": "John Doe", "email":"foo@bar.com"};

var welDiv = $.tmpl(sTpl, htData); // returns wrapped element
var sHTML = $yobi.tmpl(sTpl, htData); // returns plain string
</script>
```
