Introduction
============

게시물의 목록과 같이 컬렉션인 리소스의 경우, 그 리소스의 전체가 아닌 일부만 보고 싶은 경우가 있다. 이러한 요구를 만족시키기 위해 Yobi는 pagination을 지원한다. pagination을 지원하는 리소스는 페이지들의 list로 간주되며, 페이지는 1개 이상의 element의 list이다. 클라이언트는 리소스의 특정 페이지만을 요청할 수 있으며, 서버 역시 특정 페이지만을 돌려줄 수 있다.

pagination.js
=============

Yobi는 pagination 레이아웃을 그려주는 자바스크립트 라이브러리인 pagination.js를 제공한다. 이 파일은 public/javascript/paginatin.js 에 위치한다.

Pagination.update(target, totalPages, options)
----------------------------------------------

pagination을 그린다.

### target

Type: String, jQuery object

pagination을 그릴 HTML Element. 그리기 전 이 HTML Element의 모든 children은 삭제된다.

### totalPages

Type: Number

총 페이지 수

### options

Type: PlainObject

#### options.url (default: document.URL)

Type: String

특정 페이지를 얻어오기 위한 요청을 보낼 url. options.current가 정의되지 않은 경우, 이 값 역시 이 url을 통해 얻는다.

비동기 모드에서는 사용되지 않는다.

#### options.paramNameForPage (default: 'pageNum')

Type: String

비동기 모드에서는 사용되지 않는다.

#### options.current (default: options.url을 통해 얻음)

Type: Number

현재 페이지의 번호

#### options.firstPage (default: 1)

Type: Number

첫번째 페이지

#### options.hasPrev (default: options.current와 options.firstPage로 계산)

Type: Boolean

현재 페이지 기준으로, 이전 페이지가 존재하는지의 여부

#### options.hasNext (default: options.current와 totalPages로 계산)

Type: Boolean

현재 페이지 기준으로, 다음 페이지가 존재하는지의 여부

#### options.submit (default: null)

Type: Function(Number pageNum)

다음 페이지 혹은 이전 페이지 링크를 클릭하거나, 이동할 페이지를 입력하고 엔터를 눌렀을 때 실행될 자바스크립트 함수. pagination.js는 이 함수에게 사용자가 이동하고자 하는 페이지의 번호를 pageNum 매개변수로 넘겨준다. 예를 들어, 사용자가 다음 페이지 링크를 클릭했다면 pageNum은 현재 페이지 번호에 1을 더한 것이 된다.

이 값이 설정되면, 이전/다음 페이지 링크의 href 값은 "javascript: void(0)"이 되며, 페이지 입력창에서 페이지를 임력했을 때 해당 페이지로 이동하는 기능도 동작하지 않게 된다. 즉 모든 페이지 이동 기능을 사용자가 직접 구현해야 한다.

Sync
----

options.submit에 자바스크립트 함수를 설정하지 않았다면 동기로 동작한다.

사용 예:

    <script src="@routes.Assets.at("javascripts/common/yobo.Pagination.js")" type="text/javascript"></script>
    <script type="text/javascript">
    var pagination = new Pagination();
    pagination.init(function() {
      pagination.update($('#pagination'), @page.getTotalPageCount);
    });
    </script>

Async
-----

options.submit에 자바스크립트 함수를 설정했다면 비동기로 동작한다.

이 기능의 구현을 위해 HTTP/1.1의 Range 요청 헤더와 Accept-Ranges, Content-Range 응답 헤더를 이용한다. pagination을 지원하는 리소스에 클라이언트가 GET 요청을 보낸 경우, 서버는 Accept-Ranges 헤더를 이용해 pagination을 지원함을 클라이언트에게 알려준다. 클라이언트는 Range 헤더를 이용해 특정 페이지를 요청하고, 서버는 요청받은 페이지의 내용과 함께 전체 몇 페이지 중 어떤 페이지를 반환하는지에 대한 정보를 Content-Range 헤더에 담아 응답해준다.

사용 예:

    <script src="@routes.Assets.at("javascripts/common/yobo.Pagination.js")" type="text/javascript"></script>
    <script type="text/javascript">
    var pagination = new Pagination();

    var createUpdater = function(type, targetBody, paginationDiv) {
      var submit = function(pageNum) {
        $.ajax({
          url: '@routes.SearchApp.contentsSearch(project.owner, project.name)',
          type: 'GET',
          data: {
            filter: '@filter', // @filter should have been escaped to avoid xss.
            type: type
          },
          dataType: 'html',
          headers: { 'Range': 'pages=' + pageNum },
          success: function(data, status, xhr) {
            var pattern = /(.*?)\s+(.*?)\/(.*)/;
            var contentRange, totalPages;

            contentRange = pattern.exec(xhr.getResponseHeader('Content-Range'));
            totalPages = parseInt(contentRange[3]);

            // update the list
            $(targetBody).html(data);

            // Update pagination in $(paginationDiv)
            pagination.update($(paginationDiv), totalPages, {
              current: parseInt(contentRange[2]),
              submit: submit
            });
          }
        });
      }

      return submit;
    }

    pagination.init(function() {
      // Update pagination in #pagination-post.
      pagination.update(
        $('#pagination-post'),
        @resultPosts.getTotalPageCount,
        { current: @resultPosts.getPageIndex + 1,
          submit: createUpdater('post', $('.post-tbody'), $('#pagination-post'))}
      );
    });
    </script>

다음은 각 헤더에 대한 설명이다.

#### Accept-Ranges

어떤 리소스가 pagination을 지원한다면, 서버는 그 사실을 알리기 위해 해당 리소스에 대한 일반적인 GET 요청에 대한 응답에 "pages" 값을 갖는 Accept-Ranges 헤더를 포함시킨다.

예:

    Accept-Ranges: pages

#### Range

클라이언트는 다음과 같은 형식의 Range 헤더를 이용해 이 리소스의 특정 페이지만을 요청할 수 있다.

    Range             = pages-unit "=" page-number
    pages-unit        = "pages"
    page-number       = 1*DIGIT
    DIGIT             = <any US-ASCII digit "0".."9">

예를 들어 첫번째 페이지만 요청하는 Range 헤더는 다음과 같다.

    Range: pages=1

#### Content-Range

서버는 Range 요청에 대해 다음과 같은 형식의 Content-Range 헤더를 통해 206 Partial Content로 응답한다. (HTTP/1.1의 bytes-range-spec과 차이가 있음에 유의하라)

    Content-Range     = pages-unit SP page-number "/" complete-length
    pages-unit        = "pages"
    page-number       = 1*DIGIT
    complete-length   = 1*DIGIT
    SP                = <US-ASCII SP, space (32)>

예를 들어 총 두 페이지 중 첫번째 페이지만을 반환하는 응답에서의 Content-Range 헤더는 다음과 같다.

    Content-Range: pages 1/2

서버는 상황에 따라 클라이언트가 요청한 것과 다른 페이지를 돌려줄 수도 있다. 이러한 상황에 대한 예외처리의 책임은 클라이언트에게 있다.

주의: 클라이언트의 요청이 Range 요청이 아니더라도(다시말해 요청에 Range 헤더가 포함되어 있지 않더라도), 서버는 스스로의 판단으로 Content-Range 헤더와 그 헤더에 정의된 페이지만을 응답에 담아 반환할 수 있다. 그러나 이 경우 응답의 상태 코드는 206이어서는 안된다. 206으로 응답할 때는 반드시 요청에 Range 헤더가 포함되어 있어야 하기(MUST) 때문이다.

References
==========

Fielding, R., Ed., Y. Lafon, Ed and J. Reschke, Ed., "Hypertext Transfer Protocol (HTTP/1.1): Range Requests", Internet-Draft draft-ietf-httpbis-p5-range-latest (work in progress), January 2013.

ABNF
====

`*` rule

    The character "*" preceding an element indicates repetition. The
    full form is "<n>*<m>element" indicating at least <n> and at most
    <m> occurrences of element. Default values are 0 and infinity so
    that "*(element)" allows any number, including zero; "1*element"
    requires at least one; and "1*2element" allows one or two.
