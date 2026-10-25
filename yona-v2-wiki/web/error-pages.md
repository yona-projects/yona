---
title: 오류 화면
type: web
sources: [conf/routes:402-403, app/controllers/Application.java:94-104, app/utils/ErrorViews.java, app/Global.java:385-411, app/views/error/notfound_default.scala.html, app/views/error/forbidden_default.scala.html, app/views/error/badrequest_default.scala.html, app/views/error/internalServerError_default.scala.html]
updated: 2026-10-22
---

# 오류 화면

## 도우미: `ErrorViews`

컨트롤러가 `return ErrorViews.notFound("error.notfound.project")`처럼 `ModelAndView`를 돌려주면 응답 상태와 화면이 함께 정해진다. v1.6 `utils.ErrorViews`는 상태(`notFound(...)`)와 화면(`ErrorViews.NotFound.render(...)`)을 따로 골랐다.

| 함수 | 상태 | 템플릿 | 레이아웃 | 아이콘 | 버튼 |
|---|---|---|---|---|---|
| `notFound(키)` | 404 | `error/notfound-default.html` | 기본 레이아웃 + 자체 상단 메뉴(로고, 프로젝트 목록, 도움말, 피드백) + 자체 푸터 | `ico ico-err2` | `ybtn-info` |
| `forbidden(키)` | 403 | `error/forbidden-default.html` | 사이트 레이아웃 | `ico ico-err2` | `ybtn-primary` |
| `badRequest(키)` | 400 | `error/badrequest-default.html` | 사이트 레이아웃 | `ico-404` | `ybtn-info` |
| `internalServerError(키)` | 500 | `error/internal-server-error-default.html` | 사이트 레이아웃 | `ico-404` | `ybtn-primary` |

기본 메시지 키는 `error.notfound`, `error.forbidden`, `error.badrequest`, `error.internalServerError`다. 각 화면은 메시지 한 줄과 "Home" 링크만 보여 준다.

## 예외 처리 (`GlobalErrorHandler`)

| 상황 | v1.6 | v2 |
|---|---|---|
| 일치하는 주소 없음 | `onHandlerNotFound` -> 404 화면 | 같다 (`NoHandlerFoundException`, `NoResourceFoundException`) |
| 허용하지 않는 HTTP 메서드 | Play 라우터가 404 | 같게 404 화면 |
| 처리하지 못한 예외 | 운영 모드에서만 500 화면 (`onError`). 개발 모드는 Play 개발용 화면 | 항상 500 화면, 원인은 로그에 남긴다 |
| 쿼리 제한 시간 초과 | 원인이 `PersistenceException`이고 메시지에 `timed out`이면 `error.timeout` | 같다. 원인의 메시지도 함께 본다(Play는 원인의 메시지를 감싸 전달했다) |
| 잘못된 요청(바인딩 실패 등) | `onBadRequest` -> 400 화면 | 컨트롤러가 직접 `ErrorViews.badRequest()`를 돌려준다. Spring의 바인딩 예외 처리는 해당 화면을 옮길 때 연결한다 |

## 공통 모델

`@ControllerAdvice`의 `@ModelAttribute`는 `@ExceptionHandler`로 그리는 화면에는 적용되지 않는다. 그래서 공통 값(`currentUser`, `site`, `flashMessages`, `requestPath`, `siteAdminLoggedIn`)을 만드는 일을 `LayoutModelFactory`로 분리하고, 컨트롤러 화면은 `LayoutModelAdvice`가, 오류 화면은 `GlobalErrorHandler`가 같은 팩토리를 호출한다.

## 아직 옮기지 않은 것

- 프로젝트 화면 안의 변형은 [project-layout.md](project-layout.md)에 옮겼다. 조직 화면 안의 변형은 조직 화면을 옮길 때 추가한다.
- 익명 사용자에게 로그인 화면을 보여 주는 `Forbidden.render(키, returnUrl)` 변형과 413(`requestTextEntityTooLarge`)도 사용하는 화면을 옮길 때 추가한다.

## 검증

`ErrorPagesTest` 8건(404 두 가지, 403 두 가지, 400, 500, 시간 초과). 테스트 전용 엔드포인트는 `src/test/kotlin/dev/yona/support/ErrorViewTestController.kt`에 있다.

## 끝 슬래시 제거 이동 (동등성 점검 9e에서 추가)

v1.6 `conf/routes`의 마지막 줄 `GET /*paths`(`Application.removeTrailer`)는 다른 어떤 라우트와도 맞지 않는 GET 요청을 받는다.

- 경로가 `/`로 끝나면 마지막 슬래시를 떼고 그 경로로 `303`(`redirect`) 이동한다. 쿼리 문자열은 이동 주소에 넣지 않는다(`request().path()`만 쓴다). 예: `/organizations/yona/` → `/organizations/yona`.
- 그렇지 않으면 오류를 기록하고 `404`(`notfound_default`)를 보인다.
- 이 라우트는 맨 아래라서 정적 자원(`/assets/…`)과 다른 라우트가 먼저 맞으면 거치지 않는다. 다른 메서드(POST 등)는 이 라우트가 없어 그대로 404이다.

v2는 포괄 매핑으로 만들 수 없다(요청 매핑이 정적 자원 처리기보다 앞서 정적 자원을 가린다). 대신 일치하는 처리기가 없을 때(`NoHandlerFoundException`, `NoResourceFoundException`)를 `GlobalErrorHandler`가 받는 자리에서 같은 규칙을 적용한다: GET이고 경로가 `/`로 끝나며 `/`뿐이 아니면 슬래시를 뗀 경로로 `303`, 아니면 404 화면. 이 방식은 v1.6의 "다른 모든 라우트 다음" 순서와 결과가 같다(`TrailingSlashRedirectTest`).

