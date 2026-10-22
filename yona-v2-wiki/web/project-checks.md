---
title: 프로젝트 검사 애너테이션 (IsAllowed, IsCreatable 등)
type: web
sources: [app/actions/AbstractProjectCheckAction.java, app/actions/IsAllowedAction.java, app/actions/IsCreatableAction.java, app/actions/IsOnlyGitAvailableAction.java, app/actions/CodeAccessCheckAction.java, app/actions/DefaultProjectCheckAction.java, app/actions/NullProjectCheckAction.java, app/actions/GuestProhibitAction.java, app/models/resource/Resource.java:226-247, app/utils/PathVariable.java, ../yona-v1.6-wiki/web/actions-and-annotations.md]
updated: 2026-10-22
---

# 프로젝트 검사 애너테이션

v1.6은 Play의 action composition(`@With(...Action.class)`)으로 컨트롤러 앞에서 권한을 검사했다. v2는 같은 이름의 애너테이션과 하나의 `HandlerInterceptor`(`ProjectCheckInterceptor`)로 옮겼다. `@AnonymousCheck`는 [../subsystems/access-control.md](../subsystems/access-control.md)에 있다.

## 애너테이션

| 애너테이션 | 속성 | 하는 일 | v1.6 |
|---|---|---|---|
| `@IsAllowed(연산, resourceType=PROJECT)` | | 프로젝트를 찾고 읽기 권한을 확인한 뒤, URL에서 대상 리소스를 찾아 연산이 허용되는지 검사 | `IsAllowedAction` |
| `@IsCreatable(리소스종류)` | | 프로젝트 안에 그 종류를 만들 권한 검사. 리소스를 식별하지 않아 API 경로에서도 쓴다 | `IsCreatableAction` |
| `@IsOnlyGitAvailable` | | Git 프로젝트가 아니면 400 `error.badrequest.only.available.for.git` | `IsOnlyGitAvailableAction` |
| `@DefaultProjectCheck` | | 프로젝트 존재와 읽기 권한만 검사 | `DefaultProjectCheckAction` |
| `@NullProjectCheck` | | 프로젝트 **존재만** 검사하고 읽기 권한은 보지 않는다 | `NullProjectCheckAction` |
| `@CodeAccessCheck` | | 코드 열람이 구성원 전용인 프로젝트에서 비구성원을 막는다 | `CodeAccessCheckAction` |
| `@GuestProhibit(displaysFlashMessage=true)` | | 게스트 계정을 홈으로 보내고 `error.forbidden.or.not.allowed` 안내를 남긴다 | `GuestProhibitAction` |

모두 메서드 또는 클래스에 붙일 수 있고, 메서드의 애너테이션이 클래스의 것보다 우선한다.

## 처리 흐름

1. `@GuestProhibit`: 게스트이면 `303`으로 `/`.
2. 프로젝트 검사가 필요한 애너테이션이 하나도 없으면 통과.
3. `@NullProjectCheck`만 있으면 프로젝트 존재만 확인한다.
4. 그 밖에는 프로젝트를 찾고 읽기 권한을 확인한 뒤 붙은 검사를 `@IsAllowed`, `@IsCreatable`, `@IsOnlyGitAvailable`, `@CodeAccessCheck` 순으로 실행한다.

### 프로젝트를 찾는 규칙

- 경로의 첫 두 구간이 소유자와 프로젝트 이름이다.
- API 경로(`/-_-api/v1/owners/<소유자>/projects/<이름>/...`)는 `owners`와 `projects` 다음 구간을 읽는다(`이름/값` 쌍으로 짝지음, v1.6 `PathVariable`).
- 이전 이름으로도 찾지만 **리다이렉트하지 않는다.** v1.6 위키(`actions-and-annotations.md`)는 "이전 위치면 `RedirectUtil.redirect`"라고 적었지만, 실제 코드에서는 `Project.findByOwnerAndProjectName`이 이미 이전 위치까지 찾으므로 검사 액션의 리다이렉트 분기에 도달하지 않는다(`Project.java:161-177`). v2는 코드의 실제 동작을 따른다.

### 실패했을 때의 응답

| 상황 | 익명 | 로그인한 사용자 |
|---|---|---|
| 프로젝트가 없음 | 로그인 화면을 `403`으로, 문서 제목은 `error.fobidden`(v1.6 오타 그대로), 로그인 뒤 돌아올 주소는 요청 경로, 안내 `failed`(`error.auth.unauthorized.waringMessage`) | 일반 404 화면을 `403`으로, 문구 `error.forbidden.or.notfound` |
| 읽기 권한 없음 | 위와 같음 | 일반 403 화면, 문구 `error.forbidden.or.notfound` |
| `@CodeAccessCheck` 실패 | 로그인 화면 `403`(안내 없음) | 일반 403 화면 |
| `@IsAllowed` 대상 없음 | 프로젝트 안의 404 화면(`error.notfound.<종류>`) | 같음 |
| `@IsAllowed`, `@IsCreatable` 거부 | 프로젝트 안의 403 화면 | 같음 |
| `@IsOnlyGitAvailable` 실패 | 일반 400 화면 | 같음 |

존재하지 않는 프로젝트와 접근할 수 없는 프로젝트를 구별하지 못하게 하려고 둘 다 403을 쓴다.

## URL로 리소스 찾기 (`ProjectResourceLocator`)

`@IsAllowed(resourceType=X)`가 대상을 찾는 위치다(0부터 센 경로 구간, 0=소유자, 1=프로젝트).

| 종류 | 위치 | 조회 |
|---|---|---|
| `PROJECT` | | 이미 찾은 프로젝트 |
| `MILESTONE` | 3 | 번호(`id`)로, 프로젝트와 무관하게 |
| `BOARD_POST` | 3 | 프로젝트 안의 번호 |
| `ISSUE_POST` | 3 | 프로젝트 안의 번호 |
| `PULL_REQUEST` | 3 | 받는 프로젝트와 번호 |
| `ISSUE_LABEL` | 4 | 번호(`id`)로, 프로젝트와 무관하게 |
| `COMMIT_COMMENT` | 5 | 번호(`id`)로, 프로젝트와 무관하게 |
| `ISSUE_LABEL_CATEGORY` | 5 | 번호(`id`)로, 프로젝트와 무관하게 |
| 그 밖 | | 지원하지 않음(오류, 500) |

따라서 종류에 따라 붙일 수 있는 URL 모양이 정해져 있다. 엔티티를 `Resource`로 바꾸는 함수는 `dev.yona.access.EntityResources.kt`에 있다. 커밋 코멘트의 컨테이너(커밋)는 Git 저장소 서비스를 옮길 때 추가한다.

## v1.6 결함을 그대로 재현한 부분

- R-12: 마일스톤, 이슈 라벨, 라벨 분류, 커밋 코멘트는 번호만으로 찾기 때문에 다른 프로젝트의 것도 이 프로젝트의 경로에서 찾아진다. 권한 검사는 찾은 리소스가 속한 프로젝트 기준으로 이루어지므로 권한을 우회하지는 못하지만, 경로의 프로젝트와 리소스의 프로젝트가 달라도 통과한다.
- 숫자가 아닌 식별자 구간은 `NumberFormatException`으로 500이 된다.

## 구현 방식의 차이

v1.6 액션은 컨트롤러를 부르지 않고 `Result`를 곧바로 돌려줬다. v2 인터셉터는 화면을 `ViewResultException`에 담아 던지고, `GlobalErrorHandler`가 공통 모델을 채워 그린다. 화면이 직접 채운 값(같은 응답에서 바로 보이는 `failed` 안내 등)은 덮어쓰지 않는다.

## 검증

`ProjectChecksTest` 26건. 테스트 전용 엔드포인트는 `src/test/kotlin/dev/yona/support/ProjectCheckTestControllers.kt`에 있다.
