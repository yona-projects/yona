---
title: 사용자 메뉴와 사이드바
type: web
sources: [app/controllers/UserApp.java:1383, app/controllers/Application.java:56, app/controllers/ProjectApp.java:1336-1352, app/views/index/*.scala.html, app/views/sidebar.scala.html, app/views/layout_framed.scala.html, app/controllers/api/UserApi.java:60-90]
updated: 2026-10-23
---

# 사용자 메뉴와 사이드바

구현은 `UserMenuController`, `UserMenuAssembler`, `UserMenuService`, `FavoriteService`, `FavoriteApiController`, `ProjectGoController`이다. 테스트 `UserMenuTest` 16건, `FavoriteApiTest` 5건.

## `GET /user/usermenuTabContentList`

화면 오른쪽 사용자 메뉴(`yona.Usermenu.js`)가 Ajax로 불러 넣는 세 탭의 내용이다. 레이아웃 없이 `div.tab-pane`만 돌려준다. 익명도 빈 내용을 받는다.

- **즐겨찾기 탭**: 내 프로젝트 묶음(로그인 아이디 이름, 이름 순서), 즐겨찾기 조직 묶음(최근 즐겨찾기가 먼저, 마지막 묶음은 `favored` 구분), 즐겨찾기한 프로젝트의 소유자 중 조직 묶음(이름 순서). 묶음 안에는 읽을 수 있는 프로젝트가 이름 순서로 있고 즐겨찾기한 것은 `show-always`, 나머지는 `hide`이다. 조직 묶음의 프로젝트 수는 조직 관리자와 사이트 관리자에게만 보인다. 조직 소속이 아니고 내 것도 아닌 즐겨찾기 프로젝트는 목록 끝에 소유자 링크와 함께 이어진다. 내 프로젝트도 조직도 없으면 전체가 "No results"이다(R-31).
- **프로젝트 탭**: 최근 방문, 내가 만든(만든 시각 내림차순), 구독 중(해제한 것 제외, 이름 순서), 구성원(소유자와 사이트 관리자 역할 제외, 이름 순서). 비면 "No results".
- **최근 본 이슈 탭**: 이슈와 게시글을 최근 순서로. 줄의 설명은 주소의 `프로젝트 #번호`.
- 프로젝트 줄은 `/소유자/이름/go`로 연결되고(`data-location`), 로고(없으면 빈 자리), 비공개 자물쇠, 별(`data-project-id`)이 있다.

## `GET /user/sidebar`

사이드바를 왼쪽에 고정한 화면(`layout/layout-framed.html`)이다. 왼쪽은 사용자 메뉴(로그인한 경우만), 오른쪽은 `path`(와 `hash`)로 지정한 주소의 `iframe`이다. 경로가 없으면 로그인 기본 화면(없으면 `/notifications`). 사이드바를 고정하는 단추를 누르면 각 화면 레이아웃의 스크립트가 이 주소로 이동한다.

## `GET /:user/:project/go`

프로젝트 바로가기이다. 이슈 메뉴가 켜져 있으면 주소를 바꾸지 않고 이슈 목록을 보여 주고(요청의 쿼리를 그대로 쓴다), 게시판 메뉴가 켜져 있으면 게시판 목록으로, 둘 다 꺼져 있으면 프로젝트 홈으로 `303` 이동한다. 프로젝트를 읽을 수 있어야 한다.

## 즐겨찾기 API (`FavoriteApiController`)

| 요청 | 응답 |
|---|---|
| `POST /-_-api/v1/favoriteProjects/:id` | `{projectId, favored}`. 켜면 그때의 소유자와 이름을 적고, 끄면 그 프로젝트의 최근 방문 기록도 지운다 |
| `GET /-_-api/v1/favoriteProjects` | `{projectIds, projects:[{projectId, projectName, owner}]}` |
| `POST /-_-api/v1/favoriteOrganizations/:id` | `{organizationId, favored}` |
| `GET /-_-api/v1/favoriteOrganizations` | `{organizationIds, organizations:[{organizationId, organizationName}]}` |

익명은 `403`, 숫자가 아니거나 없는 대상은 `400`이다(v1.6은 예외 `500`).
