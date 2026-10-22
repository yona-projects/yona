---
title: 오류 화면
type: web
sources: [app/utils/ErrorViews.java, app/Global.java:385-411, app/views/error/notfound_default.scala.html, app/views/error/forbidden_default.scala.html, app/views/error/badrequest_default.scala.html, app/views/error/internalServerError_default.scala.html]
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
