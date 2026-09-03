# 라벨 자동완성(typeahead) API

legacy Yona의 `docs/technical/label-typeahead.md`를 옮김. 엔드포인트 경로와 기본 계약은
`LabelController`에 거의 그대로 남아 있지만, **`limit` 파라미터가 legacy와 달리 필수다**
(없으면 400) — 실제 코드로 확인하고 반영했다.

## 개요

프로젝트 라벨을 조회하는 HTTP API. 주로 자동완성에 쓰인다. `/labels`로 요청을 보내면 라벨
목록을 JSON으로 반환한다.

## 요청

```
GET /labels
```

### 쿼리 스트링

- **`category`** — 라벨이 속한 카테고리에 대한 대소문자 구분 없는 키워드. 카테고리 이름에
  이 키워드가 포함된 라벨만 반환한다.
- **`query`** — 라벨 이름에 대한 대소문자 구분 없는 키워드. 이름에 이 키워드가 포함된 라벨만
  반환한다.
- **`limit`** — 반환할 최대 개수. **legacy와 달리 yona에서는 필수 파라미터다** — 생략하면
  `400 Bad Request`(`"No limit"`)가 반환된다. 서버 쪽 상한(`maxFetchLabels`, 현재 `1000`)을
  넘는 값을 주면 서버가 상한값으로 낮춰서 처리한다.

## 응답

조건에 맞는 라벨 이름 목록을 JSON 배열로 반환한다.

### Content-Range 헤더

전체 결과가 반환된 개수보다 많으면(즉 `limit`에 의해 잘렸으면) `Content-Range` 헤더를
포함해서 전체 중 몇 개를 반환했는지 알려준다.

```
Content-Range     = items-unit SP number-of-items "/" complete-length
items-unit        = "items"
number-of-items   = 1*DIGIT
complete-length   = 1*DIGIT
SP                = <US-ASCII SP, space (32)>
```

예: 전체 10개 중 8개만 반환

```
Content-Range: items 8/10
```

전체 개수와 반환 개수가 같으면(잘리지 않았으면) 이 헤더는 응답에 포함되지 않는다.

## 예외

- `400 Bad Request` — `limit` 파라미터가 없을 때.

## 요청/응답 예시

요청:

```
GET /labels?query=a&category=Language&limit=3
```

응답:

```json
["@Formula","A# (Axiom)","A# .NET"]
```
