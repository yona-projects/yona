---
title: 홈 화면 (소개, 알림 틀, 기본 로그인 페이지)
type: web
sources: [app/controllers/Application.java:45-60, app/views/index/index.scala.html, app/views/index/notifications.scala.html, app/views/index/partial_intro.scala.html, app/views/index/partial_notifications.scala.html, app/views/common/mySeriesMenuTab.scala.html, app/controllers/UserApp.java, app/models/UserSetting.java, ../yona-v1.6-wiki/web/templates/home-index.md]
updated: 2026-10-22
---

# 홈 화면

v1.6 `GET /`(`Application.index`)와 `GET /notifications`(`Application.notifications`)를 옮겼다. 위키 `web/templates/home-index.md`의 호출 관계를 따라 `index.scala.html`은 본문 없이 `notifications.scala.html`을 부르는 구조이므로 v2도 템플릿 하나(`index/notifications.html`)로 합쳤다.

## 동작

| 요청 | 동작 |
|---|---|
| `GET /` | 로그인했고 `UserSetting.loginDefaultPage`가 비어 있지 않으면 그 값으로 `303`(`Location`은 저장된 값 그대로, 예: `notifications`). 그 외에는 아래 화면 |
| `GET /notifications` | 기본 로그인 페이지로 보내지 않고 같은 화면을 보여 준다 |
| 화면(익명) | `siteLayout(사이트 이름, SITE_HOME)` 안에 사이트 소개(`partial_intro`): 제목, 가입 버튼, 기능 6가지 |
| 화면(로그인) | 환영 표 3행(새 프로젝트, 새 조직, 프로젝트 목록), 소개 접기 버튼, 내 활동 탭, 알림 목록 |
| `POST /-_-api/v1/user/defultLoginPage?path=` | 현재 화면을 기본 로그인 페이지로 저장하고 `{"defaultLoginPage": "<path>"}`를 돌려준다 (`defult` 오타는 v1.6 URL 그대로) |

두 화면 요청 모두 `@AnonymousCheck`이다. 제목은 메시지 키가 아니라 사이트 이름 문자열이며, 메시지에 없는 키는 그 문자열을 그대로 쓴다(`layout.html`이 `#messages.msgOrNull(title) ?: title`로 처리).

## 내 활동 탭 (`common/my-series-menu-tab.html`)

알림, 내 이슈, 내 파일 탭을 보여 주고 현재 탭에 `active`를 붙인다. 알림 탭 또는 내 이슈 탭에서 현재 경로가 `/`가 아니고 `경로의 앞 / 제거`가 저장된 기본 로그인 페이지와 다르면 "Set to default page" 버튼을 보여 준다. 버튼 클릭은 위 `POST`를 호출한다. 저장된 값에는 앞의 `/`가 없다(`notifications`, `user/issues`).

## 알림 목록

`NotificationEventRepository.findByReceiver(user, from, size)`가 `notification_event_n4user`로 연결된 알림을 최신순(`created DESC`, 같은 시각이면 번호 내림차순)으로 `from`번째부터 `size`개 돌려준다. 첫 화면은 `(0, 20)`이다. v1.6은 같은 조회를 원시 SQL로 했다.

알림 항목의 표시(유형 아이콘, 제목 링크, 본문, 보낸 사람, 시각)와 "More" 버튼, `GET /notification?from&limit`은 알림 서브시스템에서 구현했다([../subsystems/notifications.md](../subsystems/notifications.md)).

## 사이드바

`GET /user/sidebar`와 사이드바 목록(즐겨찾기, 프로젝트, 최근 이슈)은 구현했다(로드맵 2d-5).

## 검증

`HomePageTest` 14건(소개, 환영 표, 알림 없음 안내, 탭, 기본 로그인 페이지 설정과 이동, 저장 API)과 `NotificationEventQueryTest` 3건(받는 사람별, 최신순, 시작 위치와 개수)이 H2, PostgreSQL, MariaDB에서 통과한다.

## 다른 주소 (동등성 점검 9a)

v1.6 `conf/routes`는 `GET /users/login`, `GET /-_-api`, `GET /-_-api/v1/`도 같은 액션(`Application.index`)에 연결했다. 로그인 폼은 `/users/loginform`이므로 `/users/login`의 GET은 로그인 화면이 아니라 홈 화면(소개 또는 기본 로그인 페이지로 이동)이다. v2도 `HomeController.index`에 네 주소를 모두 매핑했다(`HomePageTest`).
