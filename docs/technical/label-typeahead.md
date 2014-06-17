Abstract
--------

Yobi provides the HTTP API to get project labels, mainly for autocomplete.
When a client send a request to a url `/labels`, Yobi returns a list of project
labels encoded in json.

Request
-------

A client requests project labels by using this HTTP request:

    GET /labels

### Query String

A client can include these fields in a query string to request for project
labels that satisfy a specific condition. The query string must be encoded
according to application/x-www-form-urlencoded [1].

#### category

A case-insensitive keyword for the category to which labels belong.

Yobi returns a list of project labels whose category name contains the keyword.

#### query

A case-insensitive keyword for label names.

Yobi returns a list of project labels whose name contains the keyword.

#### limit

A maximum number of items Yobi returns.

But Yobi may also have its own limit. In such a case, smaller one is applied.

Response
--------

Response is a list of project label names that match up with the given condition,
encoded in json.

### Content-Range header

Yobi returns only a part of project labels matched up with the given condition
in some cases as follows:

* When a client has set the max number of results returned by using `limit` field in query string.
* when Yobi limits the max number of items to return.

In that cases, Yobi includes Content-Range header in the response to tell the
number of returned items out of total number. Note that this behavior is
different from `bytes-range-spec` of HTTP/1.1.

Here is the syntax:

    Content-Range     = items-unit SP number-of-items "/" complete-length
    items-unit        = "items"
    number-of-items   = 1*DIGIT
    complete-length   = 1*DIGIT
    SP                = <US-ASCII SP, space (32)>

An example of a Content-Range header, saying 8 out of 10 items are returned.

    Content-Range: items 8/10

`complete-length` is identical to the number of items Yobi returns when neither
client nor Yobi does request or limit the number.

Note: The status code must not be 206 Partial Content because even though the
client didn't send a range request, its response includes Content-Range header.
When a server returns a response with 206 Partial Content, the request MUST have
included a Range header field [2].

Exceptions
----------

Yobi returns:

* 406 Not Acceptable if the client cannot accept `application/json` which is
  the only content type for this request.
* 400 bad Request if the request does not include a mandatory field or does
  include a malformed field.

An example of an HTTP transaction
---------------------------------

request

    GET /labels?query=a&category=Language&limit=3

response

    ["@Formula","A# (Axiom)","A# .NET"]

References
----------

[1]: http://www.w3.org/TR/REC-html40/interact/forms.html#form-content-type
[2]: https://tools.ietf.org/html/rfc2616#section-10.2.7
