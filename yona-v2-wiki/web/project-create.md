---
title: 프로젝트 생성 (/projectform, POST /projects)
type: web
sources: [app/controllers/ProjectApp.java:152-292, app/views/project/create.scala.html, app/models/Project.java:55-58, app/models/Project.java:142-148, app/models/RecentProject.java, app/validation/ExConstraints.java:40-80, app/models/User.java:1166-1197, app/utils/TemplateHelper.scala:647-651]
updated: 2026-10-22
---

# 프로젝트 생성

## 폼 (`GET /projectform`)

- 로그인해야 열 수 있다. 익명이면 `303`으로 `/users/loginform?redirectUrl=/projectform`에 보내고 flash `user.login.alert`를 남긴다(`@AnonymousCheck(requiresLogin, displaysFlashMessage)`).
- `owner` 쿼리로 소유자를 미리 고를 수 있다. 오류는 버린다(v1.6 `bindFromRequest("owner")` 후 `discardErrors`).
- 소유자 선택지: 본인(로그인 ID)과 **관리자 역할인** 조직(일반 구성원인 조직은 나오지 않는다). 항목마다 아바타 주소와 종류(`user`/`group`)를 `data-*`로 가진다. 조직 로고는 `ORGANIZATION` 첨부, 없으면 `/assets/images/group_default.png`.
- 공개 범위 라디오: `PUBLIC`은 폼 값이 비었거나 `PUBLIC`일 때 선택. `PRIVATE`은 폼 값이 `PRIVATE`이거나 설정 `yona.project-default-scope`가 `private`일 때 선택. 둘 다 조건이 맞으면 둘 다 선택 속성이 붙는다(v1.6과 같다, 브라우저는 마지막 것을 고른다). `PROTECTED`는 소유자가 조직일 때만 보인다.
- 저장소 종류: `GIT`(Git), `Subversion`. Subversion을 고르면 "풀 리퀘스트를 쓸 수 없다" 안내가 보인다.
- 메뉴 설정 체크박스: 설정 `yona.project-default-menus`(기본 `code, issue, pullRequest, review, milestone, board`)에 든 것만 선택된다. 검증에 실패해 폼을 다시 그릴 때도 제출한 값이 아니라 기본 설정을 쓴다(v1.6과 같다).
- 화면 아래 스크립트가 `project.New` 모듈에 `htError`(오류 JSON)를 넘긴다.

## 생성 (`POST /projects`)

| 단계 | 규칙 |
|---|---|
| 권한 | 로그인한 사용자만. 소유자가 조직 이름이면 그 조직의 **관리자**여야 한다. 아니면 `403`, 문구는 `'<사용자 표시 이름>' has no permission`(메시지 키가 아니라 문장 자체, 번역 없이 그대로 나온다). 익명의 표시 이름은 `user.notExists.name` |
| 입력 검사 | 이름 필수(`error.required`), 이름 문자 규칙 `^[a-zA-Z0-9-_.가-힣]+$`(위반하면 `project.name.alert`), 예약어 `.`, `..`, `.git`(`project.name.reserved.alert`), 공개 범위는 알려진 값(`error.invalid`) |
| 폼 검사 | 소유자가 사용자도 조직도 아님 -> `project.owner.invalidate`. 소유자가 다른 사용자 -> `project.owner.invalidate`. 같은 소유자에 같은 이름(대소문자 무시) -> `project.name.duplicate` |
| 실패 | `400`과 함께 같은 폼을 다시 그린다. 입력값 유지, 소유자 오류는 폼 안에 바로, 나머지는 오류 JSON(`{"필드":["번역된 메시지", ...]}`)으로 JS가 보여 준다 |
| 저장 | 프로젝트 저장(`siteurl`은 `http://localhost:9000/<이름>`으로 고정, 만든 날짜는 지금), 사이트 관리자(사용자 1)를 `SITEMANAGER` 역할로, 만든 사람을 `MANAGER` 역할로 등록, Git 저장소 생성, 메뉴 설정 저장(체크한 항목만 켜짐), 만든 사람이 프로젝트 구독, 최근 방문 기록 |
| 성공 | `303`으로 `/<소유자>/<이름>` |

모든 저장은 한 트랜잭션이다. 저장소 생성이 실패하면 프로젝트와 역할, 메뉴 설정이 모두 취소된다. (저장소 디렉터리는 DB 트랜잭션 밖이라 실패 시 남을 수 있다. 같은 이름으로 다시 만들 때 먼저 지우므로 문제는 되지 않는다.)

메뉴 체크박스는 `true`, `on`, `yes`, `1`이면 켠 것으로 본다(브라우저는 `value`가 없는 체크박스를 `on`으로 보낸다).

### 최근 방문 기록 (`ProjectCreateService.recordVisit`)

같은 프로젝트의 이전 기록을 지우고 새로 남기고, 사용자당 30개를 넘으면 가장 오래된 것부터 지운다(`RecentProject.addVisitHistory`). v1.6은 이 작업을 비동기로 했지만 v2는 요청 트랜잭션 안에서 처리한다([../decisions.md](../decisions.md) A-15).

## 표시 이름 (`UserDisplay`)

v1.6 `User.getDisplayName(User)`: 대상에게 영문 이름이 있고 언어가 설정되어 있으며 **보는 사람의 언어가 영어**이면 `영문이름 + " " + 부서`, 아니면 이름. 부서는 이름에서 `[`, `(` 순서로 찾아 그 위치부터 끝까지이고 둘 다 있으면 나중의 `(`가 이긴다. **보는 사람**의 언어가 없을 때 v1.6은 `NullPointerException`이지만(대상의 언어가 없으면 이름을 돌려준다) v2는 영문 이름을 쓰지 않는다. `UserDisplayTest` 7건.

## 메시지 기본값

v1.6의 `error.required`, `error.pattern`, `error.invalid` 같은 검증 메시지는 `conf/messages`에 없고 Play 2.3의 기본 메시지에 있었다. `messages-play.properties`에 같은 문구를 두고(`spring.messages.basename: messages,messages-play`), 앞의 파일이 우선한다.

## v1.6 결함을 그대로 재현한 부분

- R-13: 예약어 비교의 `ignoreCase` 분기가 거꾸로 되어 기본 설정(`ignoreCase=false`)에서 오히려 대소문자를 무시한다. 그래서 `.GIT`도 거부된다. 원래 의도와 달리 더 엄격한 쪽으로 틀렸다.
- `vcs` 값이 없는 요청은 저장소 생성 단계에서 `UnsupportedOperationException`(500)이다. 실제 폼은 항상 `vcs`를 보낸다.

## 검증

`ProjectCreateTest` 20건(폼 7건, 생성 성공과 조직 소유, 권한 거부 2건, 방문 기록, 검증 8건), `UserDisplayTest` 7건.
