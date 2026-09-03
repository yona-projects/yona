# 자바스크립트 공통 네이밍 규칙

legacy Yona의 `docs/ko/technical/javascript-naming-convention.md`를 옮김. `yobi.*` 네임스페이스가
[javascript-module-guide.md](javascript-module-guide.md)에서 설명한 대로 yona에도 그대로 남아
있으므로, 이 명명 규칙도 그대로 유효하다.

## 네임스페이스, 모듈, 함수, 변수 이름을 지을 때

- 단어를 생략하거나 약어를 사용하지 않는다. 단, HTML/URL 등 범용적인 약어는 예외로 하고, 쓸 때는
  모두 대문자로 작성한다.
- 한글 발음을 로마자로 그대로 표기하지 않는다.
- 특수 문자는 가급적 쓰지 않는다. 다만 상수 이름에서 단어 구분, Private 표시를 위한
  언더스코어(`_`)는 예외.
- 2글자 이상 대문자를 연속으로 쓰지 않는다. 단, 상수 이름이나 약어는 예외.
- 상수 이름·약어는 대문자를 쓴다.
- 이름만으로 역할과 목적을 알 수 있도록 간결하고 명료하게 작성한다.

## 요약

- 네임스페이스: 소문자 + 언더스코어 표기법
- 모듈: 파스칼 표기법(PascalCase)
- 함수: 카멜 표기법(camelCase)
- 변수: 카멜 표기법(camelCase)

## 네임스페이스 이름

- 소문자를 사용한다.

  ```js
  yobi.namespace.Module = {};
  ```

- `yobi`로 시작한다. `$yobi`를 쓰는 경우엔 이미 `yobi` 네임스페이스가 정의되어 있다.

  ```js
  var oNS = $yobi.createNamespace("yobi.namespace.Module");
  oNS.container[oNS.name] = function() { };
  ```

- 네임스페이스 이름 자체는 언더스코어 표기법을 적용한다.

  ```js
  yobi.project = {};
  yobi.project.todo_list = {};
  yobi.project.todo_list.Controller = function(){ };
  ```

## 모듈 이름

- 명사를 사용한다.

  나쁜 예: `var Run = function(){ ... };` / `var Insert = (function(){ return {}; })();`

  좋은 예: `var Runner = function(){};` / `var Coin = (function(){ return {}; })();`

- 반드시 영문으로 작성한다.
- 파스칼 표기법을 따른다 — 복합어는 각 단어 첫 글자를 대문자로.

## 함수 이름

- 동사를 사용한다.

  나쁜 예: `apple();` / `car();`
  좋은 예: `run();` / `getElement();` / `showLayer();`

- Private 메서드는 이름 앞에 언더스코어(`_`)를 붙인다.
- 카멜 표기법을 따른다 — 첫 단어는 소문자, 이후 단어는 첫 글자 대문자.
- 함수 이름 첫 글자로 연속된 두 개의 언더스코어(`__`)나 달러 기호(`$`)는 쓰지 않는다.
- Getter/Setter는 `get + 멤버변수 이름`, `set + 멤버변수 이름` 형식을 따른다. 단, Getter의
  반환값이 Boolean이면 `get` 대신 `is`를 쓴다.

  ```js
  getElement();
  isChecked();
  setOption();
  ```

- 이벤트 핸들러는 `_on` + 이벤트명으로 시작한다. 브라우저 기본 이벤트뿐 아니라, 특정 모듈이나
  함수의 비동기 콜백(이벤트) 모델을 쓸 때도 동일하다.

  ```js
  function _onLoadImage(){
      console.log("image loaded");
  }
  document.getElementById("image").addEventListener("load", _onLoadImage);
  $.ajax("/test", { "success": _onSuccessRequest });
  ```

## 변수 이름

- 명사를 사용한다.
- 카멜 표기법을 따른다 — 첫 단어 소문자, 이후 단어 첫 글자 대문자.
  - v0.5.4 이전 코드는 헝가리안 표기법을 썼기 때문에, 그 코드를 유지보수할 때 일관성이
    필요하면 예외적으로 쓸 수 있다. 그 외에는 쓰지 않는다.
- 객체의 Private 프로퍼티는 이름 앞에 언더스코어(`_`)를 붙인다. 함수 내 지역 변수는 여기 해당하지
  않는다.
- 변수 이름 첫 글자로 연속된 두 개의 언더스코어(`__`)나 달러 기호(`$`)는 쓰지 않는다.
- 의도를 알 수 있을 만큼 간결하고 명확하게 짓는다. 단, 임시 변수는 한 글자도 허용한다.

## 파일 이름

- 자바스크립트 파일은 한 디렉터리 아래 서브 디렉터리 단위로 나눠 작성한다.
- 프레임워크·라이브러리는 `{SCRIPT_ROOT}/lib`, 프로젝트 공통 모듈은 `{SCRIPT_ROOT}/common`,
  페이지 단위 개별 모듈은 `{SCRIPT_ROOT}/service`에 두는 것을 권장한다(yona의 실제 경로는
  `src/main/resources/static/javascripts/{lib,common,service}`).
- 파일 이름은 네임스페이스를 포함해 모듈 단위로 구성한다 — 네임스페이스 + 모듈 이름 규칙을
  따른다.

  ```
  js/lib/jquery/jquery-1.9.0.js
  js/common/yobi.Label.js
  js/service/yobi.project.New.js
  js/service/yobi.board.List.js
  ```

## 코드 작성 규칙

- 들여쓰기는 1탭(공백 4자리) 간격.
- 선언/제어문의 시작 중괄호는 명령문과 같은 줄에 둔다.

  ```js
  var onLoadHandler = function(){
      console.log("loaded");
  };
  if(true){
      return;
  }else if(false){
      return;
  }
  ```

- 대괄호(`[ ]`), 종료 구분자(`;`) 앞에는 공백을 넣지 않는다.
- 콤마(`,`) 뒤에는 반드시 공백을 넣는다.
- 콜론(`:`) 앞뒤에는 반드시 공백을 넣는다.
- 구분자 세미콜론(`;`) 뒤에는 공백을 넣는다.
- 연산자 앞뒤에는 공백을 넣는다. 단항 연산자, 전위/후위 연산자는 예외.
- 소괄호와 키워드(`if`, `for`, `return`) 사이에는 공백을 넣지 않는다.
- 시작 중괄호(`{`) 앞에는 공백을 넣지 않는다.
- 함수 선언 직후에는 빈 줄을 넣지 않는다.
- 함수 선언 사이에는 빈 줄을 넣는다.
- 변수 선언 다음에는 빈 줄을 넣지 않는다.
- 명령문 사이에는 빈 줄을 쓰지 않는다. 단, 코드가 길어지면 구분을 위해 넣을 수 있다.
- 변수를 논리적으로 그룹화하고, 그룹 사이는 빈 줄로 구분한다.

  ```js
  // on request
  var requestDetailId;
  var requestDetail;

  // on retry
  var requestRetry;
  ```

## 주석

- 각 함수·모듈은 [jsdoc-toolkit](http://code.google.com/p/jsdoc-toolkit/w/list) 형식을 참고해
  역할, 인자, 반환값에 대한 주석을 남긴다.
- 코드가 길어지면 함수 중간에도 주석을 추가해 협업하는 개발자를 배려한다.
