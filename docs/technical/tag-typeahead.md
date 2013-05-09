소개
----

HIVE는 태그에 대한 자동완성 API를 제공한다. URL `/tags`로 요청하면 json 포맷으로 된 태그들의 목록을 돌려받는다.

요청
----

### 메소드

    GET

### URL

    /tags

### 파라메터

태그를 요청할 때 쿼리 스트링에 다음의 필드를 포함시킬 수 있다. 쿼리 스트링은 [application/x-www-form-urlencoded](http://www.w3.org/TR/REC-html40/interact/forms.html#form-content-type)에 따라 인코딩된다.

#### query

태그에 대한 검색어. 서버는 태그의 카테고리나 이름에 이 `query`의 값이 포함된 태그를 반환한다. 대소문자를 구분하지 않는다.

#### context

태그를 요청하는 상황에 대한 문맥. 예를 들어 `PROJECT_TAGGING_TYPEAHEAD` 라는 값을 넘겨준 경우, 서버는 현재 상황이 Project Overview 페이지에서 태그를 추가시 자동완성을 위해 태그의 목록을 요청한 것으로 이해하고, 상황에 알맞게(현재 프로젝트에 라이선스 태그가 하나도 없다면 라이선스 태그를 우선한다거나) 태그를 정렬하여 돌려준다.

#### project_id

위 `context`에 따라 `project_id` 필드가 필요한 경우가 있다.

#### limit

돌려받을 항목의 갯수를 제한한다. 그러나 서버 역시 별도로 넘겨줄 수 있는 항목 갯수의 상한값을 가지고 있을 수 있다. 그러한 경우에는 경우에는 작은 쪽이 선택된다.

응답
----

응답은 json 형식으로, 문자열의 배열로 반환된다.

배열의 각 요소는 태그의 카테고리와 이름이 ` - `을 구분자로 하여 조합된 문자열이다. 예를 들어 License 카테고리의 Public Domain이라면 다음과 같다.

    License - Public Domain

ABNF로 표현하면 다음과 같다.

    tag = tag-category SP "-" SP tag-name

`-`의 좌우에 SP가 있음에 주의하라. 이것은 보통 엄격하게 검사된다.

### Content-Range 헤더

서버는 항상 자동완성 후보의 목록을 남김없이 모두 둘려주는 것은 아니다. 다음의 경우 일부분만을 돌려줄 수 있다.

1. 클라이언트가 일부분만을 요청한 경우 (쿼리 스트링의 `limit` 필드를 이용했을 것이다)
2. 서버가 항목의 최대 갯수를 제한하고 있는 경우

이러한 경우 서버는 다음과 같은 형식의 Content-Range 헤더를 포함시켜서, 몇 개의 항목 중에서 몇 개의 항목을 돌려주었는지 알려줄 수 있다. (HTTP/1.1의 bytes-range-spec과 차이가 있음에 유의하라)

    Content-Range     = items-unit SP number-of-items "/" complete-length
    items-unit        = "items"
    number-of-items   = 1*DIGIT
    complete-length   = 1*DIGIT
    SP                = <US-ASCII SP, space (32)>

예를 들어 총 10개 중 8개만을 돌려주었다는 의미의 Content-Range 헤더는 다음과 같다.

    Content-Range: items 8/10

`complete-length`는, 서버의 제약이나 클라이언트의 제한 요청이 없었을 경우 돌려주었을 항목의 갯수와 같다.

주의: 클라이언트의 요청이 Range 요청이 아님에도 Content-Range 헤더를 포함하여 응답하는 것이므로, 응답의 상태 코드는 206 Partial Content 이어서는 안된다. 206으로 응답할 때는 반드시 요청에 Range 헤더가 포함되어 있어야 하기(MUST) 때문이다.

예외처리
--------

* 오로지 json으로만 응답이 가능하기 때문에, `application/json`을 받아들일 수 없는 요청이라면 406 Not Acceptable로 응답한다.
* 필요한 파라메터가 없거나, 잘못된 파라메터가 주어진 경우에는 400 Bad Request로 응답한다.

요청과 응답의 예
----------------

요청

    GET /tags?context=PROJECT_TAGGING_TYPEAHEAD&limit=8&project_id=1&query=a

응답

    ["License - Public Domain","Language - @Formula","Language - A# (Axiom)","Language - A# .NET","Language - A+","Language - A++","Language - A-0 System","Language - ABAP"]
