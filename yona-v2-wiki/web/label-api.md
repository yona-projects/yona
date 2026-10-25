---
title: 라벨 JSON API
type: web
sources: [app/controllers/LabelApp.java:44-124, app/controllers/ProjectApp.java:1138-1264, app/models/Label.java, app/models/Project.java:526-545, conf/routes:205-209]
updated: 2026-10-25
---

# 라벨 JSON API

프로젝트에 붙이는 전역 라벨(분류와 이름, 예: 언어, 라이선스)의 검색과 붙이기, 떼기 요청이다. 화면(템플릿과 JS)이 직접 부르는 곳은 없고 요청과 응답 계약만 v1.6과 같게 옮겼다. 이슈 라벨은 [issue-labels.md](issue-labels.md)가 다룬다. 구현은 `LabelApiController`, `LabelService`, 테스트는 `LabelApiTest` 20건이다.

## 요청

| 요청 | 입력 | 응답 |
|---|---|---|
| `GET /labels` | `query`(이름), `category`, `limit`(필수) | JSON 이름 배열. 분류와 이름은 대소문자 없는 부분 일치. `limit`이 없으면 `400`과 본문 `No limit`, JSON을 받지 못하는 `Accept`는 `406`. 전체가 한도보다 많으면 한도만큼만 주고 `Content-Range: items 한도/전체` |
| `GET /categories` | `query`, `limit`(필수) | JSON 분류 배열(겹치지 않게). 계약은 위와 같고 한도는 1000을 넘지 못한다 |
| `GET /:user/:project/labels` | | JSON `{라벨id: {category, name}}`. JSON을 받지 못하면 `406` |
| `POST /:user/:project/labels` | `category`, `name`(필수) | `category`를 보내지 않으면 v1.6 `HttpUtil.getFirstValueFromQuery`처럼 빈 문자열이라 분류가 `""`인 라벨을 만들고(2026-10-26 정정), 같은 요청을 다시 보내면 그 라벨을 붙인 채 `204`이다. 라벨이 없어 새로 만들면 `201`, 있는 라벨을 붙이면 `200`(둘 다 위와 같은 맵), 이미 붙어 있으면 `204`. `name`이 비면 `400`(`Label name is missing.`) |
| `POST /:user/:project/labels/:id` | `_method=delete` | `204`. `_method`가 `delete`가 아니면 `400`, 라벨이 없으면 `404`. 프로젝트에서 떼고 그 라벨을 쓰는 프로젝트가 없어지면 라벨도 지운다 |

## 권한

- `GET /labels`, `GET /categories`: 익명 검사만 받는다(`@AnonymousCheck`).
- 프로젝트 `GET`: 프로젝트 읽기.
- 붙이기와 떼기: 프로젝트 기본 검사 후 프로젝트 라벨 리소스(`labelsAsResource`)의 수정 권한. 프로젝트 리소스의 수정은 구성원이면 허용되므로 구성원은 붙이고 뗄 수 있고, 구성원이 아니면 `403`이다.

## 동작 메모

- 분류를 보내지 않으면 `분류가 비어 있는 라벨`을 찾기 때문에 기존 라벨과 맞지 않고 새 라벨의 분류는 `Label`이 된다. 같은 이름으로 한 번 더 보내면 유일 제약에 걸려 `500`이다(R-63, v1.6과 같다).
- 정렬 지정이 없어 v1.6은 저장소의 기본 순서를 따랐다. v2는 라벨을 아이디순, 분류를 이름순으로 돌려준다(A-99).
- 프로젝트가 없는 경로의 `GET`, `POST`는 v1.6에서 `NullPointerException`이 나지만 v2는 `404`이다(손으로 만든 요청에서만 닿는 경로, A-24).
