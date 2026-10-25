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

## 구현된 부분: `AccessControl` (리소스별 권한)

`dev.yona.access` 패키지: `AccessControl`(서비스), `Resource`/`GlobalResource`/`ProjectResource`(검사 대상). 사용자 매개변수가 `null`이면 익명 사용자다(v1.6의 `User.anonymous`).

### 리소스 모델

`Resource(id, type, container, authorId)`와 `project`. 프로젝트에 속하지 않는 리소스(`GlobalResource`: 사용자, 조직, 프로젝트 자체, 라벨, 임시 첨부)는 프로젝트를 물으면 `UnsupportedOperationException`이다. 권한 검사는 엔티티를 직접 받지 않고 `(종류, 번호)`로 필요한 엔티티를 다시 조회한다(v1.6과 같다). 엔티티를 `Resource`로 바꾸는 `asResource()`는 각 엔티티의 화면을 옮길 때 추가한다.

### `isAllowed(user, resource, operation)` 평가 순서

1. 익명 접근을 막는 사이트(`yona.allows-anonymous-access=false`)에서 익명이면 거부.
2. 사이트 관리자(`site_admin`에 있는 사용자)이면 허용.
3. `GlobalResource`이면 전역 규칙, 아니면 프로젝트 규칙. 프로젝트가 없으면 `IllegalStateException`.

### 전역 리소스 규칙

| 대상 | 연산 | 조건 |
|---|---|---|
| PROJECT | ASSIGN_ISSUE | 프로젝트 멤버 또는 (비공개가 아니고 프로젝트 조직의 ORG_MEMBER) |
| ATTACHMENT(컨테이너가 USER) | 모두 | 컨테이너 사용자 본인 |
| PROJECT | READ | (공개 ∧ 게스트 아님) ∨ 멤버 ∨ 조직 관리자 ∨ 조직 구성원(공개/보호) |
| 프로젝트가 아닌 전역 리소스 | READ | 누구나 |
| PROJECT | WATCH | (공개 ∧ 비익명) ∨ 멤버 ∨ 조직 관리자 ∨ 조직 구성원(공개/보호) |
| PROJECT | LEAVE | 소유자가 아닌 멤버 |
| USER, USER_AVATAR | UPDATE, DELETE | 본인 |
| PROJECT | UPDATE, DELETE | 프로젝트 매니저 또는 조직 관리자 |
| ORGANIZATION | UPDATE, DELETE | 조직 관리자 |
| 그 밖 | | 거부 |

### 프로젝트 리소스 규칙 (위에서부터 먼저 맞는 것이 이긴다)

1. PROJECT_TRANSFER는 ACCEPT만 허용한다. 받는 곳이 사용자면 그 본인, 조직이면 그 조직 관리자.
2. 프로젝트 조직의 관리자는 허용.
3. 프로젝트 매니저, 작성자(ISSUE_POST, ISSUE_COMMENT, NONISSUE_COMMENT, BOARD_POST, COMMIT_COMMENT, COMMENT_THREAD, REVIEW_COMMENT), 이슈 담당자(ISSUE_POST)는 허용.
4. ISSUE_STATE, ISSUE_ASSIGNEE, ISSUE_MILESTONE, ATTACHMENT는 컨테이너를 따른다: READ는 컨테이너 READ 또는 공유자, UPDATE/DELETE는 컨테이너 UPDATE.
5. 연산별 기본 규칙:

| 연산 | 조건 |
|---|---|
| READ | (공개 ∧ 게스트 아님) ∨ 멤버 ∨ 이슈 공유자 ∨ 조직 구성원(공개/보호) |
| UPDATE | 멤버 ∨ 조직 구성원(공개/보호) |
| DELETE | 멤버, 단 CODE는 거부 |
| ACCEPT, CLOSE, REOPEN | 멤버 ∨ 조직 구성원(공개/보호) |
| WATCH | (공개 ∧ 비익명) ∨ 멤버 ∨ 조직 구성원(공개/보호) |
| 그 밖 | 거부 |

"멤버"는 역할과 무관하게 `project_user`에 행이 있는 경우다. 이슈 공유자는 부모 이슈의 공유자도 포함하며, 이슈 댓글은 그 댓글의 이슈로 판단한다.

### 생성 권한

- `isGlobalResourceCreatable`: 로그인한 사용자.
- `isProjectResourceCreatable`: 익명 불가. 사이트 관리자, 조직 관리자, 멤버, 조직 구성원(공개/보호)은 무엇이든. 비공개 프로젝트의 비멤버는 불가. 공개 프로젝트의 로그인 사용자는 ISSUE_POST, BOARD_POST, ISSUE_COMMENT, NONISSUE_COMMENT, FORK, COMMIT_COMMENT, REVIEW_COMMENT만.
- `isResourceCreatable(user, container, type)`: 컨테이너의 작성자, 담당자, 공유자는 허용. 아니면 컨테이너가 PROJECT이면 그 프로젝트, 아니면 `container.project`로 판단한다.

### v1.6 결함을 그대로 재현한 부분

[refactoring-backlog.md](../refactoring-backlog.md)에 올렸다.

- R-08: 컨테이너가 프로젝트가 아닌 전역 리소스(예: USER)이면 `container.getProject()`가 예외를 던진다.
- R-09: 프로젝트 매니저는 "코드는 삭제할 수 없다" 규칙보다 먼저 평가되어 코드도 삭제할 수 있다.
- R-10: 존재하지 않는 이슈를 ISSUE_POST로 검사하면 담당자 조회에서 NullPointerException(v2는 `NoSuchElementException`)이다.

검증: `AccessControlTest` 38건, `AccessControlClosedSiteTest` 1건. 규칙 한 줄이 테스트 하나에 대응한다.

## 이후 구현된 부분

컨트롤러 쪽 애너테이션(`@IsAllowed`, `@IsCreatable`, `@GuestProhibit`, 프로젝트 검사 인터셉터), `@SiteManagerOnly`, 엔티티별 `asResource()`는 각 화면 단계에서 구현했다.
