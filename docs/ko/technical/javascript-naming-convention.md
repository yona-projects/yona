# 공통 네이밍 규칙

----
## 네임스페이스, 모듈, 함수, 변수 등의 이름을 작성할 때

* 단어를 생략하거나 약어를 사용하지 않는다. 단, HTML,URL 등과 같은 범용적인 약어는 사용할 수 있다. 약어 사용시에는 모두 대문자로 작성한다.
* 한글 발음을 로마자로 그대로 표기하지 않는다
* 특수 문자는 가급적 사용하지 않는다. 단, 상수의 이름에서 단어를 구분하기 위한 용도와 Private 지시자 표시를 위하여 언더스코어(`_`)를 사용한다
* 2글자 이상 대문자를 연속해서 사용하지 않는다. 단, 상수 이름이나 약어는 예외로 한다
* 상수 이름이나 약어 이름을 대문자를 사용한다
* 이름을 통해 역할과 목적을 할 수 있도록 간결하고 명료하게 작성한다

## 네임스페이스, 모듈, 함수,변수 이름 규칙 요약
* 네임스페이스: 소문자로 언더스코어 표기법(Underscore Notation) 사용
* 모듈: 파스칼 표기법 ([PascalCase](http://c2.com/cgi/wiki?PascalCase))
* 함수: 카멜 표기법 ([CamelCase](http://en.wikipedia.org/wiki/CamelCase))
* 변수: 카멜 표기법 ([CamelCase](http://en.wikipedia.org/wiki/CamelCase))

## 네임스페이스 이름

* 네임스페이스 이름은 소문자를 사용한다

        yobi.namespace.Module = {};

* 네임스페이스 이름은 yobi 형식으로 시작한다. $yobi 를 사용하는 경우에는 이미 yobi 네임스페이스가 정의되어 있다.

        var oNS = $yobi.createNamespace("yobi.namespace.Module");
        oNS.container[oNS.name] = function() { };

* 네임스페이스의 이름을 정의할 때에는 언더스코어 표기법을 적용한다.

        yobi.project = {};
        yobi.project.todo_list = {};
        yobi.project.todo_list.Controller = function(){ };

## 모듈 이름

* 명사를 사용하여 작성한다

>나쁜 예
>
>        var Run = function(){ ... };
>        var Insert = (function(){  return {}; })();


>좋은 예
>
>        var Runner = function(){};
>        var Coin = (function(){  return {}; })();


* 이름은 반드시 영문으로 작성한다
* 파스칼 표기법을 준수한다. 복합어 이름은 각 단어의 첫 글자를 대문자로 작성하여 단어를 구분한다.


## 함수 이름

* 동사를 사용하여 작성한다

>나쁜 예
>
>        apple();
>        car();

>좋은 예
>
>        run();
>        getElement();
>        showLayer();

* Private 메소드 인 경우 메소드 이름 앞에 언더스코어(`_`)를 사용한다
* 카멜 표기법을 준수한다. 복합어 이름은 첫 번째 단어를 소문자로 작성하고, 두 번째 이상의 단어 첫 글자를 대문자로 작성하여 단어를 구분한다.
* 함수 이름의 첫 글자로 연속된 두 개의 언더스코어(`__`) 기호와 달러 기호($)는 사용하지 않는다.
* Getter, Setter 메소드는 반드시 'get + 멤버변수 이름', 'set + 멤버변수 이름' 형식으로 작성한다. 단, Getter 메소드의 반환값이 Boolean 인 경우 get 대신 is 조합을 사용한다

>        getElement();
>        isChecked();
>        setOption();

* 이벤트 핸들러 메소드는 `_on` + 이벤트명 으로 시작하도록 정의한다.
  브라우저에서 제공하는 이벤트가 아니라 특정 모듈이나 함수에서 비동기 콜백 함수(이벤트) 모델을 사용하는 경우에도 동일하다.

>        function _onLoadImage(){
>            console.log("image loaded");
>        }
>
>        document.getElementById("image").addEventListener("load", _onLoadImage);
>
>        $.ajax("/test", {
>            "success": _onSuccessRequest
>        });


## 변수 이름

* 변수 이름은 명사를 사용하여 작성한다
* 카멜 표기법을 준수한다. 복합어 이름은 첫 번째 단어를 소문자로 작성하고, 두 번째 이상의 단어 첫 글자를 대문자로 작성하여 단어를 구분한다.
 -  v0.5.4 이전에 작성된 코드는 헝가리안 표기법 ([Hungarian Notation](http://en.wikipedia.org/wiki/Hungarian_notation))을 사용했기 때문에
    부득이 기존 코드의 유지보수를 위해 통일성이 필요한 경우에는 헝가리안 표기법을 사용할 수 있으나 그 외에는 헝가리안 표기법을 사용하지 않는다. 
* 객체의 Private 프로퍼티일 경우 변수 이름 앞에 언더스코어(`_`)를 사용한다. 함수 내의 지역변수는 Private property 에 해당하지 않는다.
* 변수 이름의 첫 글자로 연속된 두 개의 언더스코어(`__`) 기호와 달러 기호($)는 사용하지 않는다.
* 변수 이름은 한 글자 이상으로 사용 의도를 충분히 알 수 있을 만큼 간결하고 명확하게 작성한다. 단, 임시 변수는 한 글자 이름을 사용할 수 있다.


## 파일 이름

* JavaScript 파일은 하나의 디렉토리 아래에 서브 디렉토리 단위로 나누어 작성한다.
* 프레임워크를 포함한 라이브러리는 {SCRIPT_ROOT}/lib , 프로젝트 내 공통 모듈은 {SCRIPT_ROOT}/common , 페이지 단위의 개별 모듈은 {SCRIPT_ROOT}/service 에 위치하는 것을 권장한다.
* JavaScript 파일은 네임스페이스를 포함하여 모듈 단위로 구성한다. 파일 이름 규칙은 각각 네임스페이스 + 모듈 이름 규칙을 따른다

        js/lib/jquery/jquery-1.9.0.js
        js/common/yobi.Label.js
        js/service/yobi.project.New.js
        js/service/yobi.board.List.js


----

## 코드 작성 규칙

* 들여쓰기는 1탭 간격을 사용한다. 1탭 간격은 공백 4자리이다.
* 선언 또는 제어문의 시작 중괄호는 명령문과 동일한 줄에 위치한다

>        var onLoadHandler = function(){
>            console.log("loaded");
>        };
>
>        if(true){
>            return;
>        }else if(false){
>            return;
>        }

* 대괄호([ ]), 종료 구분자( ; ) 앞에는 공백을 삽입하지 않는다
* 콤마( , )는 반드시 뒤에 공백을 삽입한다
* 콜론( : )을 사용하는 경우 반드시 앞과 뒤에 공백을 삽입한다
* 구분자 세미콜론( ; )은 뒤에 공백을 삽입한다
* 연산자의 앞과 뒤에는 공백을 삽입한다. 단, 단항 연산자, 전위/후위 연산자는 예외로 한다.
* 소괄호( ( ) )와 키워드(if, for, return)를 사용할 때 공백을 삽입하지 않는다
* 시작 중괄호({)는 앞에 공백을 삽입하지 않는다
* 함수 선언 직후에는 빈 줄을 삽입하지 않는다 (`function _method(){` 바로 다음 줄)
* 함수 선언 사이에는 빈 줄을 삽입한다 (함수와 함수 사이)
* 변수를 선언한 경우에는 다음에 빈 줄을 삽입하지 않는다.
* 명령문 간에는 빈 줄을 사용하지 않는다. 단, 소스 코드의 길이가 길어지는 경우 구분을 위해 삽입할 수 있다.
* 변수를 논리적으로 그룹화 한다. 그룹간에는 빈 줄을 사용하여 구분한다

>        // on request
>        var requestDetailId;
>        var requestDetail;
>
>        // on retry
>        var requestRetry;


## 주석 작성에 대해

* 각 함수나 모듈은 [jsdoc-tookit](http://code.google.com/p/jsdoc-toolkit/w/list) 의 형식을 참고하여 함수의 역할, 인자와 반환값에 대한 주석을 표기한다
* 소스 코드가 길어지는 경우 함수 중간에도 주석을 추가하여 협업하는 개발자들을 배려한다.
