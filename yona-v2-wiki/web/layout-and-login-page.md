---
title: 레이아웃과 로그인 화면 (Thymeleaf 이식 규칙)
type: web
sources: [app/views/layout.scala.html, app/views/siteLayout.scala.html, app/views/common/navbar.scala.html, app/views/common/usermenu.scala.html, app/views/common/scripts.scala.html, app/views/common/loginDialog.scala.html, app/views/common/footer.scala.html, app/views/user/login.scala.html, app/controllers/UserApp.java:132-152, app/controllers/Application.java:105-108]
updated: 2026-10-22
---

# 레이아웃과 로그인 화면

242개 템플릿을 이식하는 모든 작업이 따를 규칙을 이 페이지에 모은다. 새 규칙이 생기면 여기에 추가한다.

## 파일 대응

| v1.6 | v2 (`src/main/resources/templates/`) | 비고 |
|---|---|---|
| `layout.scala.html` | `layout/layout.html` | 문서 전체(`<html>`부터). 매개변수 `title, theme, content` |
| `siteLayout.scala.html` | `layout/site-layout.html` | `siteLayout(title, menu, content)`와 내부용 `siteBody` |
| `common/navbar.scala.html` | `common/navbar.html` | 매개변수 `menu, project, org` |
| `common/usermenu.scala.html` | `common/usermenu.html` | 소셜 로그인 부분 제외 |
| `common/scripts.scala.html` | `common/scripts.html` | |
| `common/loginDialog.scala.html` | `common/login-dialog.html` | 소셜 로그인 버튼 영역 제외 |
| `common/footer.scala.html` | `common/footer.html` | |
| `user/login.scala.html` | `user/login.html` | 소셜 로그인 버튼 영역 제외 |

## 변환 규칙 (Scala 템플릿 -> Thymeleaf)

| v1.6 | v2 |
|---|---|
| `@siteLayout(title, MenuType.X) { ... }` | 페이지의 `<html>`에 `th:replace="~{layout/site-layout :: siteLayout('제목키', 'X', ~{::조각이름})}"`를 걸고 본문을 `<th:block th:fragment="조각이름">`에 둔다 |
| `@Messages("key")` / `@Messages("key", arg)` | `#{key}` / `#{key(${arg})}`. 변수 키는 `#{${title}}` |
| `@Html(Messages(...))` | `th:utext` |
| `@routes.Assets.at("a/b")` | `@{/assets/a/b}` |
| `@routes.X.y(...)` | 해당 URL을 `@{/...}`로 직접 쓴다 (`conf/routes`에서 찾는다) |
| `@UserApp.currentUser()` | 모델의 `currentUser`(`ViewerContext`): `anonymous`, `loginId`, `name`, `guest`, `siteManager`, `preferredLanguage`, `openIssueCount`, `avatarUrl(size)`, `memberOf(org)`, `adminOf(org)` |
| `Application.HIDE_PROJECT_LISTING` 등 설정 | 모델의 `site`(`YonaProperties`) |
| `Context.current().flash()` | 모델의 `flashMessages`(`notices`, `alertTitle`, `alertDescription`) |
| `request().path()` | 모델의 `requestPath` |
| `@if(...)` | `th:if` (같은 요소에 `th:replace`와 함께 쓰지 않는다. 우선순위상 `th:replace`가 먼저 처리된다. `<th:block th:if>`로 감싼다) |
| 스크립트 안의 `@expr` | `<script th:inline="javascript">`와 `[[...]]`(JS 리터럴로 이스케이프), 이스케이프하지 않을 때 `[(...)]`. 조건과 반복은 `/*[# th:if="..."]*/ ... /*[/]*/` |

### Thymeleaf 3.1에서 확인한 제약

- `#httpServletRequest`, `#request` 같은 서블릿 객체는 쓸 수 없다. 필요한 값은 `LayoutModelAdvice`가 모델에 넣는다.
- `#strings.arraySplit`의 구분자는 문자열 전체가 아니라 문자 집합이다. 구분 문자열이 필요하면 `titleText.split('정규식')`을 쓴다.
- 같은 속성이 두 번 있는 마크업(`class="a" class="b"`)은 파서가 거부한다. v1.6의 `loginDialog.scala.html`에 있던 중복 `class`는 브라우저가 쓰던 첫 번째 값만 남겼다.
- JS 인라인의 `[[@{/path}]]`는 `"\/path"`처럼 `/`를 이스케이프한다. JS로는 같은 값이다. 테스트는 이스케이프를 풀어서 비교한다.

## 공통 모델 (`LayoutModelAdvice`)

모든 `@Controller`에 `currentUser`, `site`, `siteAdminLoggedIn`, `flashMessages`, `requestPath`를 채운다. DB가 필요한 값(`siteManager`, `openIssueCount`)은 템플릿이 쓸 때만 읽는다.

일회성(flash) 메시지는 v1.6처럼 메시지 **키**를 담고, 화면 맨 아래 스크립트가 번역해 `$yobi.notify`(`title` 키가 있으면 `$yobi.alert`)로 보여 준다. 번역한 문구는 v1.6처럼 HTML 이스케이프한다.

## 로그인 화면 (`GET /users/loginform`)

- 이미 로그인했으면 `303`으로 `/`에 보낸다.
- `redirectUrl` 파라미터가 없고 Referer가 로그인 폼 자신이 아니면 Referer를 `redirectUrl`로 쓴다.
- 캐시를 막는 세 헤더를 붙인다.
- 입력이 빠진 `POST /users/login`은 같은 화면을 `400`으로 다시 그리고 `user.login.required` 알림을 같은 응답에서 바로 보여 준다.

## 브라우저용 메시지 (`GET /messages.js`)

전역 함수 `Messages(키, 인자...)`를 만든다. 영어 기본 메시지에 요청 언어 번역을 덮어쓴 전체를 담고, 없는 키는 키를 그대로 돌려주며, `{0}` 자리표시자를 치환한다. 메시지 파일의 `''`는 `'`로 바꾼다. 응답은 `Vary: Accept-Language`와 `Cache-Control: no-cache`를 붙인다.

## 언어 결정

`Accept-Language`와 지원 언어(`en-US, ko-KR, ja-JP, ru-RU, uz-UZ`)를 맞추고, 맞는 것이 없으면 `en-US`를 쓴다. Play의 언어 쿠키(`PLAY_LANG`)는 아직 구현하지 않았다. `<html lang>`은 v1.6처럼 사용자의 저장된 언어이고, 없으면 서버 기본 언어다.

## 실제 기동으로 확인한 것

앱을 실제로 띄워 로그인 화면이 참조하는 정적 자원 25개가 모두 200으로 응답함을 확인했다. 아직 구현하지 않은 화면으로 가는 링크(`/projects`, `/lostPassword`, `/users/signupform`, `/user/editform`)는 404다. 익명 사용자의 사용자 메뉴에 `/null` 링크가 있는 것은 v1.6도 같다(`userInfo(currentUser.loginId)`가 `null` 문자열이 된다).
`admin / admin00`으로 로그인하면 `/`로 이동하며, 틀린 패스워드는 로그인 폼으로 돌아가 `Your log in ID, E-mail or password is not valid.` 알림이 나타난다. 화면 캡처 비교는 아직 하지 않았다. 캡처는 Playwright로만 진행한다(AGENTS.md).

## 아직 하지 않은 것

- 소셜 로그인 버튼 영역(`currentAuth`, `forProviders`), 사이트 업데이트 알림(`partial_update_notification`, `YobiUpdate`).
- 홈(`GET /`), 사이드바(`/user/sidebar`), 사용자 메뉴 탭(`/user/usermenuTabContentList`).
- v1.6 화면과의 시각 비교(`llm-wiki/ui-diff` 방식).
