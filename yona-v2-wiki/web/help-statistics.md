---
title: 도움말, 통계, UI 견본
type: web
sources: [app/controllers/HelpApp.java, app/views/help/toc.scala.html, app/views/help/UIKit.scala.html, app/controllers/StatisticsApp.java, app/views/project/statistics.scala.html, app/controllers/Application.java:132-134]
updated: 2026-10-25
---

# 도움말, 통계, UI 견본

코드: `web/help/HelpController`, `StatisticsController`, `UiKitController`. 템플릿은 `help/toc`, `project/statistics`, `help/uikit`.

## 도움말 (`GET /_help`)

- `@AnonymousCheck`(사이트가 익명 접근을 막으면 로그인 폼). 제목은 `title.help`(영어 `Help`)이고 `siteLayout`의 `HELP` 메뉴이다.
- `ul.qas`에 질문 여섯 개(`li.qa`)가 있다. 질문과 답변은 언어와 관계없이 한국어이고 `app.name`(`Yona`)만 메시지에서 온다. 질문을 누르면 `.open` 클래스가 토글되어 답변이 펼쳐진다.
  1. Yona를 설치하고 싶어요(설치 안내 링크), 2. 프로젝트를 새로 생성하고 싶어요, 3. 내가 참여하는 프로젝트들은 어디서 볼수 있나요(링크 `/`, `/info`), 4. 프로젝트 탈퇴는 어떻게 하나요(`/info`), 5. 게시판에서는 어떠한 것들을 할수 있나요(여섯 항목 목록), 6. Yona의 버그를 발견했어요(이슈트래커 링크).
- `/info` 링크는 전용 라우트가 없어 사용자 페이지 `/:user`로 해석되어 404이다(R-51).

## 프로젝트 통계 (`GET /:user/:project/statistics`)

- `@AnonymousCheck`와 `@DefaultProjectCheck`(프로젝트가 없거나 읽을 수 없으면 403, 이전 이름으로 접근해도 v1.6의 `findByOwnerAndProjectName`이 이전 이름까지 찾아 그냥 열린다(A-13)).
- 프로젝트 레이아웃 안에 `<h1>Under Construction</h1>` 한 줄뿐이다. 프로젝트 메뉴도 그리지 않는다. 제목은 `statistics - <소유자>/<이름>`이다(`statistics` 키가 메시지 파일에 없어 키 이름이 그대로 나온다).
- 이 화면으로 가는 링크는 어디에도 없다.

## UI 견본 (`GET /_UIKit`)

개발자용 독립 HTML 문서(`<title>Yobi UI</title>`)이다. Buttons, Select, Search Form, Labels, Avatar, Tabs, Switches 견본을 보인다. 로그인 검사가 없다.

## 옮기지 않은 것

`GET /restricted`는 소셜 로그인으로 만들어지는 세션이 있어야 열리는 데모 페이지라 7단계에서 옮긴다(A-88).
