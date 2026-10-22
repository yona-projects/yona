---
title: 접근 제어 (익명 검사, AccessControl)
type: subsystem
sources: [app/actions/AnonymousCheckAction.java:42-62, app/controllers/annotation/AnonymousCheck.java, app/utils/AccessControl.java]
updated: 2026-10-22
---

# 접근 제어

v1.6의 접근 제어는 두 겹이다. (1) 컨트롤러 액션에 붙은 애너테이션이 익명 사용자 여부를 검사하고, (2) `AccessControl.isAllowed(user, resource, operation)`가 리소스별 권한을 판단한다. 분석은 `~/yona-convert/yona-v1.6-wiki/web/security-and-access-control.md`를 본다.

## 구현된 부분: `@AnonymousCheck`

| 클래스 | 역할 |
|---|---|
| `dev.yona.web.access.AnonymousCheck` | 애너테이션. `requiresLogin`(기본 `false`), `displaysFlashMessage`(기본 `false`) |
| `dev.yona.web.access.AnonymousCheckInterceptor` | 애너테이션이 붙은 핸들러를 가로채 익명 사용자를 로그인 폼으로 보낸다. 메서드의 애너테이션이 클래스의 것보다 우선한다 |

규칙(v1.6 `AnonymousCheckAction`과 같다):

1. 사이트가 익명 접근을 막고 있거나(`yona.allows-anonymous-access=false`, v1.6 `application.allowsAnonymousAccess`) 애너테이션이 `requiresLogin=true`이고,
2. 현재 사용자가 익명이면,
3. `303`으로 `/users/loginform?redirectUrl=<요청 경로>`에 보낸다. `displaysFlashMessage=true`이면 flash 경고 `user.login.alert`를 함께 남긴다.

`redirectUrl`에는 요청 경로만 담고 쿼리 문자열은 담지 않는다. v1.6도 `context.request().path()`를 썼다.

검증: `AnonymousCheckTest` 6건, `AnonymousCheckClosedSiteTest` 2건. 테스트 전용 엔드포인트는 `src/test/kotlin/dev/yona/support/AnonymousCheckTestControllers.kt`에 있다.

## 아직 구현하지 않은 부분

- `AccessControl`(리소스별 권한), `@IsAllowed`, `@GuestProhibit`, `AbstractProjectCheckAction` 등은 프로젝트와 이슈 화면을 이식할 때 함께 옮긴다.
- `SiteManagerAuthAction`(사이트 관리자 전용 화면)은 사이트 관리 화면을 이식할 때 옮긴다.
