# Pagination

legacy Yona의 `docs/ko/technical/pagination.md`를 옮김. 프로토콜 자체(HTTP Range 헤더 기반
페이지네이션)와 클라이언트 JS(`yobi.Pagination.js`, 파일명만 legacy의
`common/yobi.Pagination.js`에서 안 바뀌었다)는 yona에도 그대로 남아 있다 — `LabelController`,
`ProjectViewController` 등이 실제로 `Content-Range: items X/Y` 헤더를 응답하는 것을 코드로
확인했다. 아래 사용 예시의 Play 템플릿 문법(`@routes...`)만 Thymeleaf 문법(`@{...}`,
`th:*`)으로 바꿨다.

## Introduction

게시물 목록처럼 컬렉션인 리소스는, 전체가 아니라 일부만 보고 싶은 경우가 있다. 이 요구를
만족시키기 위해 pagination을 지원한다. pagination을 지원하는 리소스는 페이지들의 list로
간주되며, 페이지는 1개 이상의 element list다. 클라이언트는 특정 페이지만 요청할 수 있고,
서버도 특정 페이지만 돌려줄 수 있다.

## pagination.js

pagination 레이아웃을 그려주는 자바스크립트 라이브러리. 위치는
`src/main/resources/static/javascripts/common/yobi.Pagination.js`.

### `Pagination.update(target, totalPages, options)`

pagination을 그린다.

- **target** (String, jQuery object) — pagination을 그릴 HTML Element. 그리기 전 이 Element의
  모든 children은 삭제된다.
- **totalPages** (Number) — 총 페이지 수
- **options** (PlainObject)
  - `options.url` (기본값: `document.URL`) — 특정 페이지를 얻기 위한 요청을 보낼 url.
    `options.current`가 정의되지 않았으면 이 값도 이 url을 통해 얻는다. 비동기 모드에서는
    쓰이지 않는다.
  - `options.paramNameForPage` (기본값: `'pageNum'`) — 비동기 모드에서는 쓰이지 않는다.
  - `options.current` (기본값: `options.url`을 통해 얻음) — 현재 페이지 번호
  - `options.firstPage` (기본값: `1`) — 첫 번째 페이지
  - `options.hasPrev` (기본값: `options.current`와 `options.firstPage`로 계산) — 이전 페이지
    존재 여부
  - `options.hasNext` (기본값: `options.current`와 `totalPages`로 계산) — 다음 페이지 존재 여부
  - `options.submit` (기본값: `null`, `Function(Number pageNum)`) — 이전/다음 페이지 링크
    클릭, 또는 페이지 번호 입력 후 엔터 시 실행될 함수. `pageNum`으로 이동하려는 페이지 번호를
    받는다. 이 값을 설정하면 이전/다음 링크의 `href`는 `"javascript: void(0)"`이 되고, 페이지
    번호 입력창의 자체 이동 기능도 비활성화된다 — 즉 페이지 이동을 전부 직접 구현해야 한다.

## Sync

`options.submit`을 설정하지 않으면 동기로 동작한다.

```html
<script th:src="@{/javascripts/common/yobi.Pagination.js}"></script>
<script>
var pagination = new Pagination();
pagination.init(function() {
  pagination.update($('#pagination'), [[${page.totalPageCount}]]);
});
</script>
```

## Async

`options.submit`을 설정하면 비동기로 동작한다. HTTP/1.1의 Range 요청 헤더와
Accept-Ranges/Content-Range 응답 헤더를 이용한다. pagination을 지원하는 리소스에 GET 요청을
보내면, 서버는 Accept-Ranges 헤더로 pagination 지원 여부를 알린다. 클라이언트는 Range
헤더로 특정 페이지를 요청하고, 서버는 요청받은 페이지 내용과 함께 전체 중 몇 번째 페이지인지를
Content-Range 헤더에 담아 응답한다.

```html
<script th:src="@{/javascripts/common/yobi.Pagination.js}"></script>
<script th:inline="javascript">
var pagination = new Pagination();

var createUpdater = function(type, targetBody, paginationDiv) {
  var submit = function(pageNum) {
    $.ajax({
      url: /*[[@{/{owner}/{project}/search(owner=${project.owner},project=${project.name})}]]*/ '',
      type: 'GET',
      data: {
        filter: /*[[${filter}]]*/ '', // XSS 방지를 위해 escape되어 있어야 한다.
        type: type
      },
      dataType: 'html',
      headers: { 'Range': 'pages=' + pageNum },
      success: function(data, status, xhr) {
        var pattern = /(.*?)\s+(.*?)\/(.*)/;
        var contentRange = pattern.exec(xhr.getResponseHeader('Content-Range'));
        var totalPages = parseInt(contentRange[3]);

        $(targetBody).html(data);

        pagination.update($(paginationDiv), totalPages, {
          current: parseInt(contentRange[2]),
          submit: submit
        });
      }
    });
  };
  return submit;
};

pagination.init(function() {
  pagination.update(
    $('#pagination-post'),
    /*[[${resultPosts.totalPageCount}]]*/ 0,
    { current: /*[[${resultPosts.pageIndex + 1}]]*/ 1,
      submit: createUpdater('post', $('.post-tbody'), $('#pagination-post'))}
  );
});
</script>
```

### Accept-Ranges

어떤 리소스가 pagination을 지원하면, 서버는 그 리소스에 대한 일반 GET 요청 응답에
`"pages"` 값을 갖는 Accept-Ranges 헤더를 포함시킨다.

```
Accept-Ranges: pages
```

### Range

클라이언트는 아래 형식의 Range 헤더로 특정 페이지만 요청할 수 있다.

```
Range             = pages-unit "=" page-number
pages-unit        = "pages"
page-number       = 1*DIGIT
DIGIT             = <any US-ASCII digit "0".."9">
```

예: 첫 페이지만 요청

```
Range: pages=1
```

### Content-Range

서버는 Range 요청에 대해 아래 형식의 Content-Range 헤더로 206 Partial Content 응답한다
(HTTP/1.1의 `bytes-range-spec`과는 다름에 유의).

```
Content-Range     = pages-unit SP page-number "/" complete-length
pages-unit        = "pages"
page-number       = 1*DIGIT
complete-length   = 1*DIGIT
SP                = <US-ASCII SP, space (32)>
```

예: 총 두 페이지 중 첫 페이지만 반환

```
Content-Range: pages 1/2
```

서버는 상황에 따라 클라이언트가 요청한 것과 다른 페이지를 돌려줄 수도 있다 — 이에 대한
예외처리 책임은 클라이언트에게 있다.

주의: 클라이언트 요청이 Range 요청이 아니어도(Range 헤더가 없어도), 서버는 스스로 판단해
Content-Range 헤더와 그 헤더에 정의된 페이지만 응답할 수 있다. 다만 이 경우 상태 코드는
206이면 안 된다 — 206으로 응답하려면 요청에 Range 헤더가 반드시 있어야 한다.

**yona의 실제 구현(`LabelController`, `ProjectViewController` 등)은 `items` 단위를 쓴다**
(예: `Content-Range: items 8/10`) — 위는 legacy 문서가 설명하는 `pages` 단위 프로토콜
원문이고, 실제 리소스별로 단위(`pages`/`items`)가 다를 수 있으니 각 엔드포인트의 실제 응답
헤더를 확인하는 것을 권장한다.

## References

Fielding, R., Ed., Y. Lafon, Ed and J. Reschke, Ed., "Hypertext Transfer Protocol (HTTP/1.1):
Range Requests", Internet-Draft draft-ietf-httpbis-p5-range-latest (work in progress), January
2013.
