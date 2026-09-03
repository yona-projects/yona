Abstract
--------

Ported from legacy Yona's `docs/technical/label-typeahead.md`. The endpoint path and basic
contract are still there in `LabelController`, verified in code — **but `limit` is now required**
(400 if missing), unlike legacy where it was optional.

yona provides the HTTP API to get project labels, mainly for autocomplete. When a client sends a
request to the url `/labels`, yona returns a list of project labels encoded in JSON.

Request
-------

A client requests project labels by using this HTTP request:

    GET /labels

### Query String

- **`category`** — a case-insensitive keyword for the category to which labels belong. Only
  labels whose category name contains this keyword are returned.
- **`query`** — a case-insensitive keyword for label names. Only labels whose name contains this
  keyword are returned.
- **`limit`** — the maximum number of items to return. **Required in yona** (unlike legacy) —
  omitting it returns `400 Bad Request` (`"No limit"`). yona also has its own server-side cap
  (`maxFetchLabels`, currently `1000`); if `limit` exceeds it, the cap is used instead.

Response
--------

A list of project label names matching the given condition, encoded in JSON.

### Content-Range header

If the result was truncated by `limit`, the response includes a `Content-Range` header stating
how many of the total matching items were returned.

    Content-Range     = items-unit SP number-of-items "/" complete-length
    items-unit        = "items"
    number-of-items   = 1*DIGIT
    complete-length   = 1*DIGIT
    SP                = <US-ASCII SP, space (32)>

Example, saying 8 out of 10 items are returned:

    Content-Range: items 8/10

If nothing was truncated, this header isn't included in the response.

Exceptions
----------

- `400 Bad Request` if `limit` is missing.

An example of an HTTP transaction
---------------------------------

request

    GET /labels?query=a&category=Language&limit=3

response

    ["@Formula","A# (Axiom)","A# .NET"]
