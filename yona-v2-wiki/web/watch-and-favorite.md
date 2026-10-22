---
title: 구독과 이슈 즐겨찾기
type: web
sources: [app/controllers/WatchApp.java, app/models/Watch.java, app/models/resource/ResourceParam.java, app/controllers/api/UserApi.java:92-126, app/models/User.java:1072-1100]
updated: 2026-10-23
---

# 구독과 이슈 즐겨찾기

구현은 `WatchController`, `WatchService`, `FavoriteApiController`이다. 테스트 `WatchAndFavoriteTest` 8건.

## 구독 (`POST /watch`, `POST|GET /unwatch`)

대상은 쿼리나 폼의 `resource.type`(예: `issue_post`)과 `resource.id`이다. 알 수 없는 종류나 없는 대상은 `400`(v1.6은 바인딩 실패 `400`).

- `watch`: 익명은 `403` 평문 `Anonymous cannot watch it.`, 읽을 수 없으면 `403` `You have no permission to watch it.`. 통과하면 구독 행을 만들고(있으면 그대로) 구독 해제 행을 지우며 `200`.
- `unwatch`: 익명이나 읽기 불가는 프로젝트 `403` 화면(`issue.error.unwatch.anonymous`, `issue.error.unwatch.permission`). 통과하면 해제 행을 만들고 구독 행을 지운다. JSON을 선호하면 `200`, 아니면 리소스 화면으로 `303`이며 처음 해제했을 때만 종류별 안내(`issue.unwatch.start` 등)를 성공 메시지로 남긴다.
- 구독 행과 해제 행은 배타적이다.

## 이슈 즐겨찾기

- `POST /-_-api/v1/favoriteIssues/:issueId`: 토글하고 `{issueId, favored, message}`를 돌려준다.
- `GET /-_-api/v1/favoriteIssues`: `{projectIds, projects:[{issueId, issueTitle, issueAuthorName}]}`.
- 익명은 `403`, 숫자가 아니거나 없는 이슈는 `400`이다(v1.6은 예외 `500`, 화면에서 닿지 않아 정리했다).
