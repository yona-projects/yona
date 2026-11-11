---
title: 사이트 관리자 역할 횡단 조사 (v2 UI 계약)
type: ui-contract
sources:
  - src/main/kotlin/dev/yona/web/access/AccessChainInterceptor.kt
  - src/main/kotlin/dev/yona/web/access/SiteManagerOnly.kt
  - src/main/kotlin/dev/yona/web/access/ProjectCheckStage.kt
  - src/main/kotlin/dev/yona/web/access/ProjectCheckAnnotations.kt
  - src/main/kotlin/dev/yona/access/AccessControl.kt
  - src/main/kotlin/dev/yona/security/SessionPermissions.kt
  - src/main/kotlin/dev/yona/security/YonaAuthenticationFilter.kt
  - src/main/kotlin/dev/yona/security/CurrentUserProvider.kt
  - src/main/kotlin/dev/yona/security/TwoFactorEnforcementInterceptor.kt
  - src/main/kotlin/dev/yona/security/ApiScopeInterceptor.kt
  - src/main/kotlin/dev/yona/security/ApiScopeRules.kt
  - src/main/kotlin/dev/yona/security/SiteSecret.kt
  - src/main/kotlin/dev/yona/web/layout/LayoutModelAdvice.kt
  - src/main/kotlin/dev/yona/web/site/*.kt
  - src/main/kotlin/dev/yona/web/audit/AuditControllers.kt
  - src/main/kotlin/dev/yona/web/api/ExternalUserApiController.kt
  - src/main/kotlin/dev/yona/web/api/ExternalSiteBackupApiController.kt
  - src/main/kotlin/dev/yona/web/api/ExternalAuditApiController.kt
  - src/main/kotlin/dev/yona/web/user/PasswordResetController.kt
  - src/main/kotlin/dev/yona/web/welcome/SiteSecretInterceptor.kt
  - src/main/kotlin/dev/yona/bootstrap/InitialDataLoader.kt
  - src/main/kotlin/dev/yona/domain/site/SiteUserService.kt
  - src/main/kotlin/dev/yona/domain/site/SiteSettingService.kt
  - src/main/kotlin/dev/yona/domain/site/UpdateCheckService.kt
  - src/main/kotlin/dev/yona/domain/user/PasswordPolicy.kt
  - src/main/kotlin/dev/yona/web/AttachmentController.kt
  - src/main/kotlin/dev/yona/web/user/PasswordLoginFilter.kt
  - src/main/kotlin/dev/yona/web/user/PasswordLoginHandlers.kt
  - src/main/kotlin/dev/yona/security/YonaPasswordAuthenticationProvider.kt
  - src/main/kotlin/dev/yona/security/AttemptRecord.kt
  - src/main/kotlin/dev/yona/web/project/ProjectViewContext.kt
  - src/main/kotlin/dev/yona/web/organization/OrganizationViewContext.kt
  - src/main/resources/templates (230개 전체를 grep으로 훑음)
updated: 2026-11-11
---

# 사이트 관리자(site admin) 역할 횡단 조사

이 문서는 `/sites/*` 화면 하나하나가 아니라 **사이트 관리자라는 역할이 코드 전체에 걸쳐 어디서 달라지는지**를 정리한다. 화면별 상세는 다른 담당의 문서가 쓴다.

- 사이트 관리 화면(15개 `site/` 템플릿): [site.md](site.md), [site-settings.md](site-settings.md)
- 그룹(조직) 화면: [organization.md](organization.md)
- 기존 위키: [../subsystems/access-control.md](../subsystems/access-control.md), [user-security.md](user-security.md), [../architecture/two-factor.md](../architecture/two-factor.md), [../architecture/audit-log.md](../architecture/audit-log.md), [../architecture/enterprise-sso.md](../architecture/enterprise-sso.md), [../subsystems/initial-data.md](../subsystems/initial-data.md)

조사 방법은 읽기 전용이다. 템플릿 230개 전체를 `siteAdmin`, `siteManager`, `admin`, `manager`, `canUpdate` 같은 이름으로 grep하고, 걸린 템플릿과 연결된 컨트롤러, 접근 검사 체인, 서비스를 열어 읽었다. 앱과 Gradle은 실행하지 않았다.

## 영역 요약

사이트 관리자는 DB 표 `site_admin`(`SiteAdmin.admin_id`)에 행이 있는 사용자이다(`domain/user/User.kt:182`). 판정의 단일 진입점은 `SessionPermissions.siteManager(userId)`(`security/SessionPermissions.kt:31`)이고, `AccessControl.isSiteManager`(`access/AccessControl.kt:267`)와 `ViewerContext.siteManager`(`web/layout/ViewerContext.kt:61`)가 이것을 부른다. 판정은 요청 하나 안에서만 기억한다(`SessionPermissions.kt:56-66`). 그 밖에 `ProjectService.isGuest/hasMember`, `OrganizationViewContext.siteManager`, `EnrollController.isOrganizationGuest`, `PasswordResetController`, `OrganizationService.visibleProjects`, `ProjectListController`는 `SessionPermissions.siteManager`를 직접 부르고, `SiteUserService.isSiteManager`(`:62`)와 `YonaPasswordAuthenticationProvider`(`:62`)와 `NoheadViews.kt:39`와 `UserPageAssembler.kt:63`은 `site_admin` 표를 직접 조회한다(요청 안 기억 없음).

| 구분 | 위치 | 요약 |
|---|---|---|
| 서버가 관리자만 허용하는 경로 | `@SiteManagerOnly` 클래스 11개(`/sites/*` 컨트롤러 9개, `SiteAuditController`, `ExternalSiteBackupApiController`)와 메서드 2개(`ExternalUserApiController`), 인라인 검사 6곳(`POST /users`, `POST /owners/{owner}/projects`, `admin/audit` 3개, `resetPassword`) | 4절 목록 |
| 서버가 관리자에게 일반 규칙을 풀어 주는 곳 | `AccessControl.isAllowed` 맨 앞, 생성 권한, 비공개 프로젝트, 코드 열람, 공지, 첨부 이동과 업로더 선택, 규칙 묶음 우회 등 | 2절 표 |
| 화면에서 관리자에게만 보이는 것 | 사용자 메뉴의 렌치, 오류 화면 안내, 코드 화면 안내, 업데이트 알림, 관리자 로그인 띠(기본 `admin` 계정 한정) | 1절 표 |
| 설정에 따라 다른 화면이 바뀌는 것 | 파일 설정 `yona.*`와 DB 설정 `site_setting` | 6절 표 |
| 이 코드에 없는 것 | 관리자가 다른 사용자로 로그인하는 기능 | 3절 |

용어를 먼저 정리한다. 이 문서에서 "관리자 로그인 띠"는 3절의 붉은 배너이다. v1.6에도 임의 사용자로 로그인하는 기능은 없다(`yona-v1.6-src/src/controllers`에서 `impersonat`, `loginAs`, `switchUser` 검색 결과 없음).

## 1. 템플릿에서 관리자만 보이거나 달라지는 곳 전수

230개 템플릿을 훑은 결과이다. `site/` 아래 템플릿은 접근 자체가 관리자 전용이므로 이 표에 넣지 않고 맨 끝에 따로 적는다. 서버 권한 열은 "화면에서 숨겨도 서버가 막는지"를 뜻한다.

### 1-1. `siteManager`를 직접 보는 곳 (일반 사용자와 다른 점)

| 템플릿:줄 | 조건 | 보이는 요소 | 일반 사용자는 | 서버 권한 |
|---|---|---|---|---|
| `common/usermenu.html:47-55` | `currentUser.siteManager` | 상단 오른쪽의 렌치 아이콘 링크. `/sites/userList`로 간다. 툴팁 `menu.siteAdmin`(사이트 관리), 클래스 `show-progress-bar`, 뒤에 구분선 | 렌치와 구분선이 없다 | `/sites/*`는 `@SiteManagerOnly`가 403(4절) |
| `common/navbar.html:62` | `(!site.hideProjectListing and !currentUser.guest) or currentUser.siteManager` | 검색 범위 드롭다운의 "전체" 항목(`/search`) | 목록 숨김이 켜졌거나 게스트이면 없다. 관리자는 항상 있다 | 서버는 관리자를 넓혀 주지 않는다(발견 admin-role-F3) |
| `error/request-text-entity-too-large.html:19` | `currentUser.siteManager` | 안내 `error.tooLargeText.admin`: 설정 `yona.parsers-text-max-length`를 고치면 한도를 바꿀 수 있다 | 제목과 한도(`site.parsersTextMaxLength`)까지만 | 화면 안내일 뿐이다 |
| `code/view.html:133` | `f.siteManager` | 큰 파일 안내에 `code.looseFileSizeLimitForCodeBrowser`가 덧붙는다(설정 `application.codeBrowser.viewer.maxFileSize`를 고치라는 문구) | 그 문구가 없다 | 화면 안내일 뿐이다. 값은 `CodeBrowserController.kt:97`이 `accessControl.isSiteManager(viewer)`로 넘긴다 |
| `user/view.html:36` | `profile.siteManager` | 프로필의 배지 `SITE ADMIN` | **보는 사람이 누구든** 대상 사용자가 관리자이면 모두에게 보인다 | 값은 `UserPageAssembler.kt:63`이 `site_admin` 행 유무로 만든다 |
| `user/view.html:37` | `profile.locked` | 배지 `BLOCKED` (대상이 잠긴 상태) | 모두에게 보인다 | 삭제한 사용자에는 붙지 않는다(`UserPageAssembler.kt:63`) |
| `site/user-list.html:73-74` | `row.siteManager` | 단추(클래스 `site-admin-toggle`) 글자가 "관리자 권한 해제"(`button.user.revoke.site.admin.role`, `ybtn-info`) 또는 "관리자로 승격"(`button.user.upgrade.to.site.admin`). 같은 화면의 스크립트(`user-list.html:156-161`)가 이 단추의 `request:error`가 403이면 기본 처리를 막고 화면을 다시 읽어 flash 안내를 보인다 | 관리자 전용 화면 | `/sites/toggleSiteAdminRole`(4절, 5절) |

### 1-2. 관리자 로그인 띠와 업데이트 알림 (3절, 7절에서 상세)

| 템플릿:줄 | 조건 | 보이는 요소 |
|---|---|---|
| `layout/layout.html:59` | `siteAdminLoggedIn` | 붉은 띠 `div.admin-logged-in-affix`: `user.siteAdminLoggedInAffix` "관리자로 로그인 하였습니다!" + 작은 글씨 `user.siteAdminLoggedInAffix.maxim` "- 큰 힘에는 큰 책임이 따릅니다" |
| `layout/layout.html:60-62` | `updateNotice != null` | 가운데 정렬 안내: 새 버전 링크(`site.update.notification`)와 단추 "숨기기"(`site.update.notification.hide`, `POST /sites/unwatchUpdate`) |
| `layout/migration-layout.html:48-51` | 위와 같다 | 이전(Migration) 화면 레이아웃의 같은 두 요소 |
| `layout/site-mng-layout.html:33` | `versionToUpdate != null` | 관리 사이드바의 "업데이트" 항목에 배지 `1` |
| `index/notifications.html:118-120` | 스크립트가 `.admin-logged-in-affix` 개수가 1인지 봄 | 사이드 단 `.sidenav`의 `top`을 `84px`로 내린다(띠 높이만큼) |

### 1-3. 관리자 판정이 간접으로 스며든 곳

템플릿은 `canUpdate`, `canManage`, `canSeeMembers`, `canLeave`, `guest`, `member`, `manager` 같은 값을 본다. 이 값을 만드는 뷰 컨텍스트가 관리자를 어떻게 다루는지가 중요하다. "관리자 포함"은 `isAllowed`의 맨 앞 `isSiteManager` 분기 때문에 관리자가 true인 경우이다.

| 템플릿:줄 | 조건 값 | 값을 만드는 곳 | 관리자일 때 |
|---|---|---|---|
| `project/menu.html:58` | `view.canUpdate`(프로젝트 설정 톱니) | `ProjectViewContext.kt:117` `isAllowed(UPDATE)` | 항상 보임(관리자 포함) |
| `project/home.html:28` | `projectView.canUpdate` | `ProjectViewContext.kt:117` | 소개 편집 단추 보임 |
| `project/home.html:109` | `projectView.canUpdate` | `ProjectViewContext.kt:117` `isAllowed(UPDATE)` (project-F7: 일반 구성원에게는 숨긴다. `canUpdateLabels`(`:123`)는 이 링크에 더는 쓰이지 않는다) | 구성원 추가 링크 보임 |
| `project/home.html:124` | `projectView.canLeave` | `ProjectViewContext.kt:129` `projectMember && isAllowed(LEAVE)` (project-F8) | **프로젝트에 역할 행이 있을 때만 "탈퇴" 단추가 보인다.** 구성원이 아닌 관리자에게는 없다(발견 admin-role-F7) |
| `user/partial-projectlist.html:48` | `project.canLeave` | `UserPageAssembler.kt:151` `pageUser.loginId != project.owner && pageUser.loginId == viewer?.loginId` | 관리자 판정과 무관하다. 보는 사람이 그 프로필 본인이고 소유자가 아닐 때만 |
| `project/header.html:36` | `view.guest`(가입 신청 팝업) | `ProjectViewContext.kt:102-105` | 관리자는 게스트가 아니므로 **가입 신청 단추가 없다** |
| `project/header.html:70` | `view.canWatch` | `ProjectViewContext.kt:114` | 항상 보임 |
| `project/partial-readme.html:12,21` | `view.canCreateCommit` | `isProjectResourceCreatable(COMMIT)` | 보임 |
| `issue/partial-searchform.html:84` | `search.canManageLabels` | `IssueListController.kt:199` `isAllowed(project, UPDATE)` (issue-F12: 라벨 관리 화면이 `UPDATE`를 요구하므로 같은 조건) | 라벨 관리 링크 보임 |
| `issue/partial-select-label.html:12` | `view.manager` | `ProjectViewContext.kt:138` **프로젝트 매니저만** | **관리자에게는 라벨 편집 `[edit]` 링크가 없다**(서버는 허용, 발견 admin-role-F8) |
| `project/menu.html:92` (`"Q"` 키) | `view.settingShortcutEnabled` | `ProjectViewContext.kt:164` `= manager` | 관리자에게는 Q(설정) 단축키가 없다(설정 톱니는 보임) |
| `help/keymap.html:47` | `view != null and view.manager` | 위와 같다 | 키 안내표에 Q 줄이 없다 |
| `project/ruleset-form.html:56-57` | 항상 보임 | 체크박스 `bypassAdmins`, 글자 `project.ruleset.bypass.admins`("프로젝트 관리자와 사이트 관리자") | 우회 대상 안내 문구 |
| `organization/menu.html:25,31` | `view.canManage` | `OrganizationViewContext.kt:83` `admin || siteManager` | 조직 설정 톱니 보임 |
| `organization/view.html:30` | `org.canManage` | 위와 같다 | 단추 "새 프로젝트"(`/projectform?owner=조직`) 보임 |
| `organization/view.html:88` | `org.canSeeMembers` | `OrganizationViewContext.kt:86` `siteManager || member || admin` | 구성원 영역 보임 |
| `organization/view.html:93,112` | `!org.leaveBlocked and org.admin` / `org.member` | `OrganizationViewContext.kt:92`: `leaveBlocked = admin && 조직 관리자 수 == 1`(organization-F8: 사이트 관리자의 `isAllowed` 참은 기준이 되지 않는다) | 관리자가 조직 구성원이 아니면 탈퇴 단추가 없다 |
| `organization/header.html:17` | `view.guest` | `OrganizationViewContext.kt:74-77` 사이트 관리자 제외 | 가입 신청 단추가 없다 |
| `issue/view.html`, `issue/edit.html`, `issue/partial-comment.html`, `board/view.html`, `board/edit.html`, `board/partial-comments.html`, `milestone/view.html`, `project/partial-issue-labels-list.html`(줄 번호는 각 템플릿의 `canUpdate`, `canDelete` 조건 전부) | 이슈, 글, 댓글, 마일스톤, 라벨의 `canUpdate`, `canDelete`, `canRead` 등 | `IssueViewAssembler.kt:75,97`, `BoardViewAssembler.kt:71,94`, `IssueLabelController.kt:285,290`(모두 리소스 `accessControl.isAllowed(…)`) | 관리자는 `isAllowed` 맨 앞 분기로 항상 true라 수정과 삭제 단추가 모두 보인다(OAuth 조직 정책 마스킹 제외) |
| `code/nohead.html:19`, `code/nohead-hg.html:19`, `code/nohead-svn.html:19` | `canUpdate` | `NoheadViews.kt:51` `isAllowed(project, UPDATE)` | 관리자에게 보임 |
| `common/navbar.html:55-58` | `currentUser.memberOf(org) or currentUser.adminOf(org)` | `ViewerContext.kt:80,83` 조직 역할만 | 목록 숨김일 때 **관리자라도 조직 구성원이 아니면 조직 범위 검색 항목이 없다** |

### 1-4. 사이트 설정과 관련된 관리자 전용 문구

| 템플릿:줄 | 설명 |
|---|---|
| `user/signup.html:21-22` | `requiresConfirm`(가입 승인제)일 때 안내 두 줄. 둘째 줄에 **관리자(`admin` 계정)의 이메일을 거꾸로 쓴 글자**가 들어간다(`SignupController.kt:107-111` `users.findByLoginId("admin")?.email?.reversed()`). 비로그인 사용자도 본다. 스팸봇 방지용 역순 표기이다 |

### 1-5. `site/` 아래 템플릿

`site/audit, data, diagnostic, issue-list, lfs, mail, mass-mail, oauth-apps, post-list, project-list, security, sso, update, user-list`(14개)는 전부 관리자 전용이다. 15번째 `site/lost-password.html`은 관리자 화면이 아니라 비밀번호 분실 메일 요청 화면이다(`PasswordResetController.kt:36`, 비로그인용). 각 화면의 요소 표는 `site*.md`가 쓴다. 사이드바(`layout/site-mng-layout.html:25-41`)는 13개 링크(사용자 목록, 글 목록, 이슈 목록, 프로젝트 목록, 메일 보내기, 대량 메일, 업데이트, 진단, 감사, 보안, OAuth 앱, LFS, SSO)를 고정으로 가지며 관리자만 이 레이아웃에 들어온다. 데이터 화면 `/sites/data`로 가는 사이드바 링크와 템플릿 링크는 없다(주소로만 열린다). 이 레이아웃은 `layout/site-layout`을 거쳐 `layout/layout`을 쓰므로 3절의 띠와 업데이트 알림도 같이 나온다.

## 2. 서버에서 관리자가 일반 규칙을 우회하는 곳

### 2-1. 접근 검사 체인

`AccessChainInterceptor`(`web/access/AccessChainInterceptor.kt:29-40`)는 컨트롤러 메서드와 클래스에 붙은 어노테이션을 v1.6의 Play 순서(메서드가 바깥, 클래스 안쪽, 부모 클래스가 가장 안쪽)로 한 개씩 실행하고 하나가 막으면 멈춘다. 어노테이션별 단계:

| 어노테이션 | 단계 | 관리자 분기 | 거부 응답 |
|---|---|---|---|
| `@SiteManagerOnly` | `SiteManagerOnlyStage.run`(`web/access/SiteManagerOnly.kt:32-35`) | 관리자가 아니면 막는다(이것이 관리자 전용 문이다) | `ViewResultException(ErrorViews.forbidden("error.auth.unauthorized.waringMessage"))` = 403 오류 화면. 익명도 로그인 폼이 아니라 403 |
| `@AnonymousCheck` | `AnonymousCheckStage.run`(`AnonymousCheckStage.kt:25-37`) | 관리자 분기 없음. `!allowsAnonymousAccess || requiresLogin`이고 익명이면 `/users/loginform?redirectUrl=<uri>`로 303(GET 요청이면 질의 문자열을 `%3F`로 인코딩해 `redirectUrl` 값에 덧붙인다, oauth-F7) | 303 리다이렉트 + 필요하면 flash `user.login.alert` |
| `@GuestProhibit` | `ProjectCheckStage.guestPassed`(`ProjectCheckStage.kt:115-123`) | 관리자 분기 없음. `user.isGuest == true`이면 `/`로 리다이렉트 | 303 + flash `error.forbidden.or.not.allowed` |
| `@IsAllowed`, `@IsCreatable`, `@DefaultProjectCheck`, `@RequiresVcs`, `@CodeAccessCheck` | `ProjectCheckStage.run`(`:57-83`). 프로젝트를 찾고 읽을 수 있는지(`readableProject`, `:86`)를 한 번 확인한 뒤 `checkMenuEnabled`(`:98`)를 거친다 | 모두 `AccessControl`(2-2)나 `ProjectService.hasMember`(2-3)를 거쳐 관리자는 통과. **`checkMenuEnabled`는 관리자 분기가 없다**: 프로젝트 설정에서 이슈 메뉴를 끈 프로젝트의 `issues`, `issue`(라벨 관리 `issue/label*`은 제외), `issueform`, `copyLabels` 주소와 마일스톤 메뉴를 끈 프로젝트의 `milestones`, `milestone`, `newMilestoneForm` 주소는 **관리자에게도 404**이다(issue-F24, API 경로 `-_-api`는 제외) | 익명: 로그인 화면 403. 로그인: 오류 화면 403 (`error.forbidden.or.notfound` 또는 `error.forbidden`). 프로젝트 없음은 403(`projectNotFound`). 메뉴를 끈 주소는 404 오류 화면(`error.notfound`) |
| `@NullProjectCheck` | 같은 단계 | 프로젝트 존재만 본다. 권한 분기 없음 | 없으면 403 오류 화면(익명은 로그인 화면) |

`@SiteManagerOnly`가 `@AnonymousCheck`보다 **바깥**에 있는 컨트롤러가 대부분이다(`@SiteManagerOnly` 다음 `@AnonymousCheck`, 예: `SiteUserController.kt:35-36`). 그래서 사이트가 익명을 막아도 익명은 로그인 폼이 아니라 403을 받는다(`SiteManagerOnly.kt:22-24`). 순서는 `AccessChainOrderTest`가 지킨다.

`ExternalUserApiController`의 `@SiteManagerOnly`는 메서드에 붙어 있고(`:186`, `:202`) 클래스에는 `@AnonymousCheck`가 없다(`:43-45`).

**읽기 권한 검사 보강(A-459)에서 관리자.** 풀 리퀘스트 만들기와 보기, 리뷰 댓글, 멘션 목록, 이슈 보조 API, 댓글 투표, 게시글 쓰기에 더한 서버 검사는 모두 `accessControl.isAllowed(…, Operation.READ)`(`@IsAllowed(Operation.READ)` 포함)를 거치므로 관리자는 `isAllowed`의 맨 앞 분기(`:111`)로 통과한다. 예외는 OAuth 조직 정책 마스킹 하나이다(2-2). 이슈 API의 보조 검사 중 `accessControl.isAllowed(viewer, issue.asResource(), UPDATE)`(`IssueApiController`)도 같다.

### 2-2. `AccessControl`의 관리자 분기 (`access/AccessControl.kt`)

| 메서드:줄 | 관리자 분기 | 비관리자 규칙과의 차이 |
|---|---|---|
| `isAllowed`(`:106-117`) | `:111` `if (isSiteManager(user)) return true`. 모든 리소스, 모든 연산(READ, UPDATE, DELETE, WATCH, LEAVE, ACCEPT, CLOSE, REOPEN, ASSIGN_ISSUE)에서 맨 먼저 true | 앞의 두 줄만 예외: `orgPolicy.masks(resource)`이면 요청자를 익명(null)으로 바꾸므로 **관리자도 우회하지 못한다**(OAuth 조직 정책, X-39). 사이트가 익명 접근을 막고 `user == null`이면 false |
| `isProjectResourceAllowed`(`:175-176`) | `:176` 중복 검사. 위에서 이미 걸러졌다 | 프로젝트 이전 요청(PROJECT_TRANSFER)의 수락도 관리자는 통과한다(`:176` 이 `:179` 앞). 작성자 권한(`:195-198`, board-F14)은 그 프로젝트를 볼 수 있을 때(공개이거나 구성원이거나 조직 그룹 구성원)만 주는데 관리자는 이 줄에 닿기 전에 통과하므로 영향이 없다 |
| `isProjectResourceCreatable`(`:65-77`) | `:68` 관리자는 모든 종류를 만들 수 있다(비공개 프로젝트의 비멤버도) | 일반 로그인 사용자는 공개 프로젝트에서 `PUBLIC_CREATABLE_TYPES`(이슈, 게시글, 두 댓글, 포크, 커밋 댓글, 리뷰 댓글)만. **`orgPolicy.masks(project)`이면 관리자도 false**(`:66`) |
| `isResourceCreatable`(`:80-97`) | 컨테이너가 프로젝트 소속이면 위 메서드로 넘어가 관리자가 통과. 전역 컨테이너는 `isGlobalResourceCreatable`(로그인이면 누구나) | |
| `canBypass`(`:273-274`) | 규칙 묶음(ruleset) 우회: `isSiteManager || isProjectManager`이고, 막고 있는 규칙 묶음이 **모두** `bypassAdmins == true`일 때만(X-12, X-34) | 일반 사용자는 우회 불가. 사용처: `PushPolicy.kt:53,82`(푸시 거부), `PullRequestActionController.kt:72`, `PullRequestWorkflow.kt:73-78`(리뷰 조건과 변경 요청 무시 병합), `PullRequestPageAssembler.kt:127`(병합 단추 모양), `BranchController.kt:109`, `ExternalPullRequestWriteApiController.kt:269` |

### 2-3. 서비스와 컨트롤러의 개별 분기

| 위치 | 규칙 | 관리자일 때 |
|---|---|---|
| `ProjectService.hasMember`(`domain/project/ProjectService.kt:116-122`) | 구성원 여부. `project_user` 행이거나 매니저이거나 **사이트 관리자** | 모든 프로젝트의 구성원으로 취급. `@CodeAccessCheck`(코드 열람을 구성원으로 제한한 프로젝트, `ProjectCheckStage.kt:72-76`)를 통과 |
| `ProjectService.isGuest`(`:107-110`), `ProjectViewContext.guest`(`:102-105`) | 역할이 없는 로그인 사용자 | 관리자는 게스트가 아니다 (v1.6 `ProjectUser.isGuest`) |
| `OrganizationViewContext.guest`(`:74-77`), `EnrollController.isOrganizationGuest`(`web/EnrollController.kt:122-126`) | 조직에 속하지 않은 로그인 사용자 | 관리자는 조직 게스트가 아니다. 그래서 조직 가입 신청 취소의 400 검사를 통과하지 못하는 쪽이 된다(관리자는 신청 기록이 없고 게스트도 아니므로 `ResponseEntity.badRequest()`, `EnrollController.kt:111-112`) |
| `OrganizationService.visibleProjects`(`domain/project/OrganizationService.kt:99`) | 조직의 프로젝트 목록 | 조직 관리자 또는 **사이트 관리자**는 전부. `hideProjectListing`이 켜져도 관리자는 전부(첫 분기가 앞섬) |
| `ProjectListController.projects`(`web/project/ProjectListController.kt:59`) | `canSeeAll = siteManager`. `ProjectListService.specification`(`:72`)이 `!canSeeAll && !displayPrivateRepositories`일 때 공개만 | 비공개 프로젝트까지 목록에 나온다. 단 `hideProjectListing`이면 관리자도 403(`:56`) |
| `ProjectListController.pagedList`(`:85-86`) | 행의 `readable` | `displayPrivateRepositories || isAllowed(READ)`이므로 관리자는 항상 읽을 수 있음(흐리지 않음) |
| `UserMenuAssembler.organization`(`web/user/UserMenuAssembler.kt:66`) | 사용자 메뉴의 조직 항목에 프로젝트 개수를 붙일지 | `isSiteManager || ORG_ADMIN` |
| `BoardWriteController.allowedToNotice`(`web/board/BoardWriteController.kt:226-227`) | 공지 올리기(글쓰기 화면의 선택과 저장이 같은 판정, board-F4) | `isProjectResourceCreatable(BOARD_NOTICE)`라 관리자는 통과한다(조직 관리자, 구성원, 조직 그룹 구성원도). OAuth 조직 정책 마스킹이면 관리자도 거절 |
| `AttachmentService.moveOnlySelected`(`domain/misc/AttachmentService.kt:89-97`), `PostAttachments.kt:37,53` | 임시 첨부 이동 | 관리자는 자기 것이 아닌 첨부, 임시가 아닌 첨부도 옮길 수 있다 |
| `AttachmentController.findUploader`(`web/AttachmentController.kt:151`), `POST /files`(`:123`) | 업로더 선택(shell-F23, A-460). 폼에 본문 항목 `authorEmail`(못 찾으면 `authorLoginId`)이 있으면 그 사용자의 임시 첨부로 저장한다 | **사이트 관리자만 업로더를 고를 수 있다.** 요청자가 익명이거나 관리자가 아니면 이 항목을 무시하고 요청자 본인 명의로 올린다(본인 값을 보내도 같다). 익명 요청은 `requester == null`이라 업로더를 못 찾고 403으로 끝난다. 쿼리 문자열의 값은 본문 항목으로 세지 않는다. 관리자가 보낸 값으로 사용자를 못 찾으면 요청자 본인으로 올린다 |
| `IssueListService.assignableUsers`(`domain/issue/IssueListService.kt:45-61`) | 담당자 후보 | 관리자이면 자기 자신이 후보에 추가된다. 호출처: `IssueListController.kt:184`, `IssueFormOptions.kt:40`, `IssueApiController.kt:105`, `IssueFormApiController.kt:84`, `PullRequestListController.kt:206`, `PullRequestAssigneeService.kt:59`, `ExternalProjectReadApiController.kt:130` |
| `ExternalSearchApiController.searchPullRequests`(`:138`) | 풀 리퀘스트 검색의 가시 프로젝트 | 관리자는 제한 없음(`null`) |
| `ExternalSearchApiController.searchInOrganization`(`:96-97`) | 조직 검색, 목록 숨김 | `isAllowed(UPDATE)` 포함이라 관리자는 통과 |
| `ProjectMemberService.findMembersIncludingSiteManager`(`ProjectRepositories.kt:74`, `ProjectMemberService.kt:63`) | 구성원 표 | `role = SITEMANAGER(3)`인 `project_user` 행(v1.6 호환)을 구성원 집계에 포함. 반대로 `removeMember`, `assignRole`은 SITEMANAGER 행을 제외한다(`:112`, `:128`) |
| `UserMenuService`(`domain/user/UserMenuService.kt:88-90`), `ProjectListService.toItem`(`:79-80`) | 내 프로젝트, 공개 프로젝트 구성원 표시 | SITEMANAGER 역할 행은 제외 |
| `ApiScopes.selectable`(`domain/apitoken/ApiScopes.kt:43`) | 토큰에 고를 수 있는 범위 | `site_admin`은 관리자만 고를 수 있다. 호출처: `AccountController.kt:277,295,314,363,369`, `DeviceAuthorizationService.kt:126,184` |
| `OAuthClientService`(`domain/oauth2server/OAuthClientService.kt:49`) | OAuth 앱에 주는 범위 | `site_admin`은 외부 앱에 위임하지 않는다(D-71 11번) |
| `WebhookPayloads.kt:205` | 웹훅 `sender.site_admin` | 보낸 사람이 관리자인지 필드로 알린다 |
| `ExternalUserStatusApiController.kt:68` | `/-_-api/v1/user` 응답 | `siteAdmin` 필드 |
| `YonaPasswordAuthenticationProvider.kt:62` | SSO 전용 모드 | 관리자만 로컬(비밀번호) 로그인 허용. 비관리자는 비밀번호 확인 전에 `LoginResult.SsoOnly`로 거절(비상 경로) |
| `PasswordResetController.ssoBlocked`(`:145`) | SSO로 연결된 사용자의 비밀번호 재설정(메일 분실 흐름 `:69`, `:103`과 관리자 재설정 `:134`이 부른다) | 대상이 관리자이면 막지 않는다(SSO 전용 모드의 비상 로그인) |
| `NoheadViews.of`(`web/code/NoheadViews.kt:39-40`) | 저장소가 비었을 때 안내 화면의 복제 주소 | 사이트 관리자이면 주소에 `로그인ID@`를 넣지 않는다(`!siteManager`일 때만 매니저와 구성원의 로그인 ID가 들어간다). 이 판정은 `site_admin` 표를 직접 조회한다 |
| `TwoFactorEnforcementInterceptor`(`security/TwoFactorEnforcementInterceptor.kt:36`) | 2단계 인증 강제 | 강제 방식이 `ADMINS`이면 관리자만 대상(`SiteUserService.isSiteManager`로 직접 조회) |
| `PasswordLoginFilter.attemptAuthentication`(`web/user/PasswordLoginFilter.kt:56`), `AttemptStore`(`security/AttemptRecord.kt`) | 로그인 잠금(usersec-F11): 같은 아이디(또는 이메일, 소문자) 키 `login:<값>`으로 5번 연달아 틀리면 15분 잠근다(`TwoFactorAttempts.LIMIT=5`, `LOCK=15분`). 횟수는 DB 표 `attempt_record`에 있어 재시작해도 남는다 | **관리자 예외가 없다.** 관리자 아이디도 같은 잠금을 받는다. 잠금 검사가 SSO 전용 모드 검사(관리자만 통과)보다 앞이라, 잠긴 동안은 맞는 비밀번호도 받지 않고 폼은 `user.login.tooMany`, AJAX는 429이다 |

### 2-4. 사용자 상태가 관리자 판정에 주는 영향

| 위치 | 사실 |
|---|---|
| `CurrentUserProvider.resolve`(`security/CurrentUserProvider.kt:35`) | 상태가 LOCKED나 DELETED이고 쿠키(remember-me) 인증이 아니면 익명으로 본다 |
| `YonaAuthenticationFilter.hasValidSession`(`:100-111`) | 세션 사용자가 LOCKED나 DELETED이면 보안 컨텍스트를 비운다. 곧 **잠긴 관리자는 다음 요청부터 관리자 기능이 없다** |
| `SiteUserService.deleteUser`(`domain/site/SiteUserService.kt:88-98`) | `site_admin` 행을 지우지 않는다. 삭제된 관리자 행이 남는다(발견 admin-role-F4). 다만 자기 자신과 기본 `admin`은 컨트롤러가 먼저 거절한다(5-3) |

### 2-5. 화면은 숨기는데 서버는 허용하거나 그 반대인 곳

| 구분 | 위치 | 설명 |
|---|---|---|
| 화면 숨김, 서버 허용 | 라벨 편집 링크 `issue/partial-select-label.html:12` | `view.manager`는 프로젝트 매니저만이다. 서버는 관리자의 라벨 편집(`IssueLabelController`, `@IsAllowed` 계열)을 허용한다 |
| 화면 숨김, 서버 허용 | `Q` 단축키와 키 안내(`project/menu.html:92`, `help/keymap.html:47`) | `settingShortcutEnabled = manager`. 관리자는 설정 톱니는 보이나 단축키는 없다. 서버는 `/settingform`을 허용한다 |
| 화면 숨김, 서버 부분 허용 | 사용자 목록에서 기본 `admin`(번호 1) | `SiteUserService.findUsers`(`:40-41`)가 `u.id <> 1`로 뺀다. 직접 요청 중 삭제와 잠금은 서버가 403/flash로 막는다(`isProtectedFromLoss`, 5-3). 게스트 전환, 2단계 인증 해제, SSO 해제, 비밀번호 초기화는 막지 않는다(발견 admin-role-F5) |
| 화면 일부만 표시, 서버는 모두 허용 | 프로젝트의 "탈퇴" 단추 | 단추는 `projectMember && isAllowed(LEAVE)`라 역할 행이 있는 프로젝트에만 보이지만(project-F8), 서버 `UserLeaveController.kt:46`은 관리자에게 `LEAVE`가 항상 true라 모든 프로젝트에서 허용한다(발견 admin-role-F7) |
| 화면 표시, 서버가 넓혀 주지 않음 | 검색 범위 "전체"(`navbar.html:62`) | `web/search/SearchController.kt`의 전체 검색(`SearchService.visibleProjects`)에는 관리자 분기가 없다. 목록 숨김일 때도 항목은 보인다(발견 admin-role-F3) |
| 화면 표시, 서버 거부 | 없음 | 목록 숨김일 때 `/projects` 링크는 관리자에게도 숨겨져 있어(`navbar.html:26`) 서버의 403과 맞는다 |
| 화면과 서버 모두 관리자 전용 | `/sites/*`, 렌치 | 일치 |
| 화면 숨김, 서버 막음 | 메뉴를 끈 이슈, 마일스톤 주소 | 설정에서 메뉴를 끄면 탭이 사라지고 주소도 404이다. 관리자도 같다(`checkMenuEnabled`, 2-1) |

## 3. "관리자로 로그인" 흐름

### 3-1. 사실 요약

- 관리자가 **다른 사용자로 로그인하는 기능은 없다**. 시작 단추, 되돌아오기, 대리 세션, 그 감사 기록 모두 코드에 없다(전체 `.kt`에서 `impersonat|loginAs|switchUser|sudo|runAs` 검색 결과 없음). v1.6에도 없다.
- 상단의 붉은 "관리자로 로그인 하였습니다!" 띠는 **로그인한 사용자의 `loginId`가 `admin`이면** 그린다. 관리자 권한 행이 있는지는 보지 않는다.
  - 값: `LayoutModelFactory.attributes`의 `"siteAdminLoggedIn" to (currentUserProvider.get().user?.loginId == DEFAULT_ADMIN_LOGIN_ID)`(`web/layout/LayoutModelAdvice.kt:76`), 상수 `DEFAULT_ADMIN_LOGIN_ID = "admin"`(`:88-89`). v1.6의 `UserApp.isSiteAdminLoggedInSession()`이 세션의 loginId를 `SiteAdmin.SITEADMIN_DEFAULT_LOGINID`와 비교하던 것과 같다(`yona-v1.6-src/src/controllers/UserApp.java:1364-1370`).
  - 다른 관리자 계정으로 로그인하면 관리자 권한이 있어도 띠가 **없다**. 반대로 `admin` 계정이 관리자 권한을 잃어도(기본 admin은 해제 보호가 있어 어렵다) 이름이 `admin`이면 띠가 뜬다.
- 되돌아오기, 전환 같은 상태가 없으므로 세션에는 일반 로그인과 같은 정보만 있다. 띠는 모델 값 하나로 매 요청 계산된다.

### 3-2. 띠의 계약

| 항목 | 값 | 근거 |
|---|---|---|
| 그려지는 곳 | `layout/layout.html:59`(일반 레이아웃), `layout/migration-layout.html:48`(이전 화면) | |
| 사이트 화면 | `layout/site-layout.html:12`가 `layout/layout :: layout(...)`을 그대로 감싸므로 `/sites/*` 화면(`site-mng-layout` 경유)과 로그인 전 안내 화면(`site/lost-password`, 프로젝트 목록 등 `site-layout` 사용 화면)에서도 띠가 그려진다. 띠가 없는 곳은 `layout/layout`을 거치지 않는 `welcome/secret`, `welcome/restart` 등 독립 문서이다 | `layout/site-layout.html:12-13` |
| 오류 화면 | `GlobalErrorHandler`도 같은 `LayoutModelFactory`를 부르므로 오류 화면에도 값이 들어간다 | `web/error/GlobalErrorHandler.kt:31`, `web/error/error-pages.md` |
| 모양 | 가로 100%, 글자 흰색 1.25rem 굵게, 배경 `#ad0000`, 가운데 정렬, `position: sticky; top: 0; z-index: 1000` | `app/assets/stylesheets/scss/_page.scss:6810-6823` |
| 좁은 화면 | `width: auto !important` | `_responsive.scss:200-202` |
| 글자 | `user.siteAdminLoggedInAffix` = "관리자로 로그인 하였습니다!", `.maxim` = "- 큰 힘에는 큰 책임이 따릅니다" | `messages_ko_KR.properties:1128-1129` |
| 다른 요소와의 간섭 | 알림 화면(`index/notifications.html:118-120`)은 띠가 정확히 1개이면 `.sidenav`의 `top`을 `84px`로 내린다 | |
| 시험 | `StickyPanelsTest`가 SCSS에 `position: sticky`가 있는지와 `data-affix-top`이 템플릿에 없는지만 본다. 판정(`loginId == "admin"`)을 직접 검사하는 시험은 없다 | `src/test/kotlin/dev/yona/audit/StickyPanelsTest.kt:38-45` |

### 3-3. 세션, 감사 기록, 2단계 인증과의 관계

| 주제 | 사실 |
|---|---|
| 세션 | 일반 로그인과 같다(`LoginSessionService`). 관리자 전용 세션 종류는 없다. 판정은 요청마다 DB에서 읽는다 |
| 권한 부여와 해제가 세션에 미치는 영향 | `SiteUserService.toggleSiteAdmin`(`:168-176`)은 `sessionUsers.invalidate(id)`(`:175`)를 부르고 주석은 "다음 요청에서 무효가 되어 다시 로그인"이라 한다. 그러나 `YonaAuthenticationFilter.hasValidSession`(`:108`)은 캐시에 없으면 `remember`로 되살리므로 **세션은 끊기지 않는다**. 권한 변화는 요청 단위 판정(`SessionPermissions.kt:56-66`)으로 바로 반영된다(발견 admin-role-F6) |
| 잠금과 삭제 | 상태가 LOCKED나 DELETED이면 다음 요청에서 세션이 무효이다(2-4) |
| 로그인 잠금 | 관리자 예외가 없다. 같은 아이디(또는 이메일)로 5번 연달아 틀리면 15분 잠근다(키 `login:<소문자>`, DB 표 `attempt_record`). 잠겨 있으면 비밀번호를 확인하지 않고 거절하며, 이 검사는 SSO 전용 모드의 관리자 통과 검사보다 먼저이다. 사이트 관리자가 계정을 잠그는 것(`UserState.LOCKED`)과는 별개이다 |
| 감사 기록 | 로그인 자체에 관리자 구분은 없다. 관리자 행위는 `AuditRecorder`로 남는다(4절 표의 "감사" 열, 7절) |
| 2단계 인증 | 기본 `admin` 계정도 일반 사용자와 같은 2단계 인증 대상이다. 강제 방식 `ADMINS`이면 관리자(`site_admin` 행이 있는 사용자)가 2단계 인증을 켜지 않았을 때 로그인 직후부터 `/user/editform/two-factor`로 303 이동한다(`TwoFactorEnforcementInterceptor.kt:30-41`, 플래시 `twoFactor.enforced`). 이동하지 않는 경로: `/user/editform/two-factor`와 그 아래, **`/sites/security`**(강제를 되돌릴 수 있게), `/user/two-factor/**`, `/users/**`, `/assets/**`, `/messages.js`, `/favicon.ico`, `/-_-api/**`(`:44-49`). 그 밖의 `/sites/*`는 이동한다. 토큰 인증 요청은 대상이 아니다. 이동하지 않는 경로에는 `/user/editform/two-factor/` 아래 모든 주소(보안 키 추가 화면 등)도 들어간다(`isExempt`, `:46`) |
| 관리자가 남의 2단계 인증을 푸는 것 | `POST /sites/resetTwoFactor`(4절). 감사 `two_factor.disabled`, 세부 `by=admin`(`TwoFactorService.kt:183-188`) |
| SSO 전용 모드 | 관리자만 비밀번호 로그인(비상 경로). 비관리자는 아이디가 없거나 틀려도 비밀번호 확인 전에 `LoginResult.SsoOnly`(`YonaPasswordAuthenticationProvider.kt:62`)로 거절한다(폼은 `sso.error.ssoOnly`, AJAX는 403, 횟수에 세지 않는다). 로그인 화면에는 `user/login.html:44`, `common/login-dialog.html:57`의 작은 링크 `/users/loginform?local=true`(`sso.login.localLink`)가 있어 비밀번호 폼(`localLogin`)을 연다. 이 링크는 관리자 여부와 관계없이 누구에게나 보이고, 비관리자는 폼을 열어도 서버가 거절한다 |

## 4. 관리자 전용 엔드포인트 전체

공통: 모든 `/sites/*` 컨트롤러는 클래스에 `@SiteManagerOnly`와 `@AnonymousCheck`가 붙는다(관리자 검사가 바깥). 거부 응답은 `error.auth.unauthorized.waringMessage`를 담은 **403 HTML 오류 화면**이고 익명도 같다(2-1). CSRF는 `csrfProtection=true`(기본)일 때 `POST/DELETE`에 토큰을 요구한다(`YonaProperties.kt` `csrfProtection`, `CsrfSupport.kt`). "화면"은 GET으로 그리는 템플릿이 있는지이다. 목록의 "감사"는 코드에서 `audit.record`가 확인된 것만 적었다.

### 4-1. `/sites/*` 웹

| 경로 | 메서드 | 컨트롤러:줄 | 화면 | 응답과 거부 | 감사 |
|---|---|---|---|---|---|
| `/sites/userList` | GET | `SiteUserController.kt:58` | `site/user-list` | 200. 알 수 없는 `state`는 400 | |
| `/sites/user/delete{userId}` | DELETE | `:69` | 없음(요청만) | 대상이 요청한 관리자 자신이거나 기본 `admin`(번호 1)이면 403 + flash `site.userList.selfProtect`(site-F4). 프로젝트의 유일한 매니저이면 403 + flash `site.userList.deleteAlert`(자기 보호 검사가 먼저). 없는 번호는 404. 성공은 `/sites/userList`로 303. 화면 스크립트는 403이면 목록을 다시 읽어 flash를 보인다 | `site.user_deleted` |
| `/sites/toggleSiteAdminRole/{loginId}` | POST | `:87` | 없음 | 없는 사용자 400. 해제 보호에 걸리면 403 + flash `site.userList.adminProtect`. 성공은 `/sites/userList` 303 | `site.admin_granted` / `site.admin_revoked` |
| `/sites/toggleAccountLock` | POST | `:100` | 없음(목록 다시 그림) | `loginId`, `state`, `query`. 없는 사용자는 `/sites/userList?pageNum=0` 303 + `user.notExists.name`. 대상이 요청한 관리자 자신이거나 기본 `admin`이면 상태를 바꾸지 않고 목록 200 + flash `site.userList.selfProtect`(site-F4). 삭제된 사용자는 목록 200 + flash `site.userList.deletedLock`. 성공은 목록 200 | `site.user_state_changed` |
| `/sites/resetTwoFactor` | POST | `:124` | 없음 | 같은 모양으로 목록을 다시 그림 | `two_factor.disabled`(by=admin) |
| `/sites/unlinkSso` | POST | `:138` | 없음 | 같다. 소셜 로그인 연결은 두고 SSO만 해제 | `auth.sso_unlinked`(by=admin) |
| `/sites/toggleGuestMode` | POST | `:152` | 없음 | 같다 | `site.guest_mode_changed` |
| `/{loginId}?action=resetPassword` | POST | `PasswordResetController.kt:125-139` | 없음(user-list의 JS가 호출) | JSON. 관리자가 아니면 403 `{"isSuccess":false,"reason":"FORBIDDEN"}`, 대상 없음도 같다. SSO 사용자이고 대상이 관리자가 아니면 403 `SSO_USER`. 성공 200 `{loginId,name,newPassword,isSuccess}`(`newPassword`는 6자 임시 값이고 `PasswordPolicy`를 거치지 않는다, 5-1). `action`이 다르면 400 `BAD_REQUEST`. 화면 스크립트의 실패 알림은 `reason`을 `$yona.escapeHtml`로 감싸 보인다. 이 경로는 **`@SiteManagerOnly`가 아니라 인라인 검사**이다 | `account.password_changed`(via=admin_reset) |
| `/sites/projectList` | GET | `SiteContentController.kt:65` | `site/project-list` | `pageNum < 0`이면 400 | |
| `/sites/project/delete/{projectId}` | DELETE | `:78` | 없음 | 없는 번호 404. 성공은 저장소, 위키, LFS 디렉터리까지 지우고 `/sites/projectList` 303. 화면 스크립트(`project-list.html:75-91`, site-F5)는 `result.ok`가 아니면 확인창을 닫고, 404/403이면 화면을 다시 열어 목록을 맞추고, 그 밖의 실패는 `error.internalServerError` 알림을 띄운다 | `project.deleted` |
| `/sites/postList` | GET | `:90` | `site/post-list` | `pageNum < 1`이면 400 | |
| `/sites/issueList` | GET | `:101` | `site/issue-list` | `pageNum < 1` 또는 알 수 없는 `state`는 400 | |
| `/sites/data` | GET | `SiteDataController.kt:57` | `site/data` | 200 | |
| `/sites/export` | GET | `:64` | 없음(파일 내려받기) | `application/x-download`, 파일 `yona-data-yyyyMMddHHmm.json`, 스트리밍 | `site.data_exported`(via=web) |
| `/sites/import` | POST | `:87` | 없음(화면 `site/data`의 제출 단추 글자는 `site.data.import`, 제출 전 확인창이 있다) | `data` 파트 없으면 `/sites/data` 303. 일부 실패는 `/sites/data` 303 + 경고, 성공은 `/` 303, 첫 토큰이 JSON이 아니면 400 | `site.data_imported` |
| `/sites/noAvatarUsers` | GET | `:106` | 없음(JSON, 화면 스크립트가 쓴다고 봄: 추측) | `{"users":[{loginId,name,email}]}` | |
| `/sites/setAttachmentToUserAvatar` | POST | `:119` | 없음(JSON) | 본문 JSON `{avatarFileId,email}`, JSON이 아니면 400 `{"message":"Expecting Json data"}`, 그 밖에는 항상 200 | 없음 |
| `/sites/mail` | GET, POST | `SiteMailController.kt:32,35` | `site/mail` | POST: 주소 오류 400. 서버가 거절하면 같은 화면에 `site.mail.failReason`. 쿼리 `errorMessage`는 키 형식(영문, 숫자, 점, 밑줄, 하이픈)이고 실제 있는 메시지 키일 때만 화면에 풀려 나온다(아니면 무시, site-F8: 템플릿 표현식 실행을 막는 보안 보강) | 없음 |
| `/sites/massmail` | GET | `:50` | `site/mass-mail` | 200 | |
| `/sites/mailList` | POST | `:57` | 없음(JSON) | `Accept`가 JSON이 아니면 406. 잘못된 프로젝트 이름은 400. 200 `["a@b",...]` | |
| `/sites/update` | GET | `SiteMaintenanceController.kt:32` | `site/update` | 200. 확인 실패는 화면에 스택 트레이스. 화면에 알림 상태(`#update-notice-state`: `site.update.notice.on`/`off`)가 나오고, 꺼져 있으면 단추 `site.update.notice.enable`("새 버전 알림 켜기")의 폼(`#update-notice-form`)이 나온다(site-F6) | |
| `/sites/watchUpdate` | POST | `:55` | 없음 | 서버 전체의 알림을 다시 켠다(속성 `UPDATE_NOTICE_OFF=false`). 303 `/sites/update` | 없음 |
| `/sites/unwatchUpdate` | POST | `:62` | 없음 | 200 빈 본문. 서버 전체의 알림을 끈다(속성 `UPDATE_NOTICE_OFF=true`, 재시작해도 유지) | 없음 |
| `/sites/diagnostic` | GET | `:68` | `site/diagnostic` | 200 | |
| `/sites/audit` | GET | `AuditControllers.kt:59` | `site/audit` | 200. 본문은 `audit/list` 조각이고, 외부 스트리밍 대상이 있으면 상태 표(`audit.stream.*`: 대상, 대기, 마지막 전송, 실패 횟수, 마지막 오류)가 위에 나온다 | |
| `/sites/audit/export` | GET | `:71` | 없음(파일) | 형식이 틀리면 400 | 내보내기 기록 |
| `/sites/audit/verify` | GET | `:76` | 없음(JSON) | `{ok,checked,brokenId,reason}`. 화면 스크립트(`audit/list.html:73-88`)는 `r.ok`가 아니면(403, 서버 오류) 예외로 보고 `data-failed`(`audit.verify.failed`) 문구를 붉은 글씨로 보인다(audit-F4) | |
| `/sites/security` | GET, POST | `SiteSecurityController.kt:24,37` | `site/security` | POST: 알 수 없는 값이면 400. 성공 `/sites/security` 303 + `site.security.saved`. 화면의 저장 단추 세 개(2단계 인증, OAuth 앱, MCP 서버) 옆에 작은 글씨 `site.security.saveScope`("위의 \"{0}\" 설정만 저장됩니다. 다른 영역은 각자의 저장 단추로 저장하십시오.")가 붙는다(site-F19). 이 POST는 2단계 인증 강제 하나만 저장한다 | `site.two_factor_enforcement_changed` |
| `/sites/oauth-apps` | GET | `SiteOAuthAppsController.kt:41` | `site/oauth-apps` | 200 | |
| `/sites/oauth-apps/{id}/delete` | POST | `:45` | 없음 | 없는 앱 404, 성공 `/sites/oauth-apps` 303 | `oauth.client_deleted`(by=admin) |
| `/sites/security/oauth` | POST | `:53` | 없음 | 체크하지 않은 항목은 끈다. 303 `/sites/security` | `site.oauth_cimd_changed`, `site.oauth_user_apps_changed` |
| `/sites/security/mcp` | POST | `:61` | 없음 | 같다 | `site.mcp_enabled_changed`, `site.mcp_write_changed` |
| `/sites/lfs` | GET, POST | `SiteLfsController.kt:25,36` | `site/lfs` | POST: `maxObjectMb >= 1`, `quotaMb >= 0` 아니면 400. 성공 303 `/sites/lfs` + `site.lfs.saved` | `site.lfs_changed` |
| `/sites/sso` | GET | `SiteSsoController.kt:46` | `site/sso` | 200 | |
| `/sites/sso/mode` | POST | `:62` | 없음 | SSO 전용 모드 전환 | `site.sso_changed` |
| `/sites/sso/ldap`, `/ldap/check` | POST | `:68`, `:83` | 없음 | 저장, 연결 검사. 검사 단추 옆에 `site.sso.check.saved`("저장한 설정으로 확인합니다. 값을 바꿨다면 먼저 저장하십시오.")가 붙는다(site-F16, OIDC 검사도 같다) | `site.sso_changed`(저장) |
| `/sites/sso/oidc`, `/oidc/check` | POST | `:90`, `:103` | 없음 | 저장, 검사 | 같다 |
| `/sites/sso/saml2`, `/saml2/metadata` | POST | `:110`, `:124` | 없음 | 저장, 메타데이터 읽기 | 같다 |
| `/sites/sso/saml2/keys/generate`, `/upload`, `/remove-previous` | POST | `:133`, `:138`, `:143` | 없음 | SP 키 관리 | 같다 |
| `/sites/sso/saml2/sp-certificate.pem` | GET | `:150` | 없음(파일) | SP 인증서 | |

각 화면의 입력 필드와 단추 상세는 `site*.md`를 본다. 이 표는 경로와 권한과 응답만 센다.

### 4-2. 외부 API (`/-_-api/v1/...`)

API 경로는 먼저 `ApiScopeInterceptor`(`security/ApiScopeInterceptor.kt:21-38`)가 거른다. 규칙 표에 없으면 403, 범위가 필요한데 토큰 인증이 아니면 **401**(JSON `{"message":"unauthorized request"}`), 범위(`site_admin`)가 모자라면 **403** JSON이다. 이 단계를 통과한 뒤 아래 컨트롤러 검사가 이어진다. `site_admin` 범위는 관리자만 토큰에 고를 수 있다(`ApiScopes.selectable`).

| 경로 | 메서드 | 컨트롤러:줄 | 범위 규칙(`ApiScopeRules.kt`) | 컨트롤러의 관리자 검사 | 거부 응답 | 감사 |
|---|---|---|---|---|---|---|
| `/-_-api/v1/admin/users` | GET | `ExternalUserApiController.kt:185` | `:169` `site_admin` | `@SiteManagerOnly`(`:186`) | HTML 403 오류 화면(발견 admin-role-F9) | 없음(읽기) |
| `/-_-api/v1/admin/users/{user}` | PATCH | `:201` | `:173` | `@SiteManagerOnly`(`:202`) | 같다. 없는 사용자 401(v1.6 호환), 상태 못 읽으면 400, `SITE_ADMIN`이면 403 | **없음**(발견 admin-role-F10) |
| `/-_-api/v1/users` | POST | `:71` | `:168` | 인라인 `isSiteManager`(`:75`) | **400** `User creation with api is allowed by Site admin only.`(v1.6 호환 응답) | 이 컨트롤러에 없음 |
| `/-_-api/v1/owners/{owner}/projects` | POST | `ExternalProjectApiController.kt:116` | 프로젝트 쓰기 범위 | 인라인 `isSiteManager`(`:121`) | **400** (같은 문구). 소유자가 조직이면 조직 관리자여야 하고 아니면 403 | |
| `/-_-api/v1/admin/export` | GET | `ExternalSiteBackupApiController.kt:49` | `:171` | 클래스 `@SiteManagerOnly`(`:33`) | HTML 403 | `site.data_exported`(via=api) |
| `/-_-api/v1/admin/import` | POST | `:71` | `:172` | 같다 | HTML 403. `data` 파트가 없으면 400 | `site.data_imported`(via=api) |
| `/-_-api/v1/admin/audit` | GET | `ExternalAuditApiController.kt:72` | `:132` | 인라인 `isSiteManager`(`:76`) | JSON 403 `Forbidden request`. 토큰 사용자가 없으면 401 | 내보내기 기록 |
| `/-_-api/v1/admin/audit/verify` | GET | `:82` | `:133` | 인라인(`:85`) | JSON 403 | |
| `/-_-api/v1/admin/audit/streams` | GET | `:93` | `:134` | 인라인(`:96`) | JSON 403 | |
| `/-_-api/v1/orgs/{org}/audit` | GET | `:120` | `admin:org`(`ApiScopeRules.kt:135`) | 조직 관리자 | (관리자 전용은 아니다. 사이트 관리자는 `isAllowed(UPDATE)`로 통과) | |

### 4-3. 화면이 없는 관리자 요청 (정리)

화면 없이 요청만 있는 것: 4-1의 DELETE와 `toggle*`, `resetTwoFactor`, `unlinkSso`, `resetPassword`, `watchUpdate`, `unwatchUpdate`, `export`, `import`, `noAvatarUsers`, `setAttachmentToUserAvatar`, `mailList`, `audit/export`, `audit/verify`, OAuth 앱 삭제, 보안과 LFS와 SSO의 저장 POST, 4-2 전체. `noAvatarUsers`와 `setAttachmentToUserAvatar`를 부르는 템플릿과 스크립트는 없다(`src/main/resources` 전체 grep 결과 없음). 서버 경로만 있고 v1.6에서 외부 스크립트가 부르던 JSON 경로로 보인다(추측).

## 5. 관리자 계정 자체

### 5-1. 최초 관리자 생성과 초기화

| 단계 | 사실 | 근거 |
|---|---|---|
| 초기 데이터 | 사용자가 한 명도 없을 때만 `admin` 계정(번호 1, 이름 `Site Admin`, 이메일 `project.yona@gmail.com`, 고정 해시와 솔트)과 `site_admin` 행과 역할 7종을 적재한다. 적재 직후의 비밀번호는 코드에 박힌 고정 해시이다 | `bootstrap/InitialDataLoader.kt:29-47` |
| 최초 기동 화면 | 비밀 키(`yona.secret`)가 비었거나 기본값이면 모든 컨트롤러 요청이 `welcome/secret` 화면으로 간다(접근 검사 뒤에서). 입력: `loginId`(반드시 `admin`, 필드가 아예 없어도 500이 아니라 `error.required` 400, welcome-F1), `name`, `password`, `retypedPassword`, `email`. 비밀번호는 비어 있지 않아야 하고 `PasswordPolicy`(8자 이상 128자 이하, 흔한 비밀번호와 한 글자 반복, 아이디나 이메일(`@` 앞부분 포함)과 같은 값 거절, 메시지 키 `validation.tooShortPassword`, `validation.tooLongPassword`, `validation.commonPassword`, `validation.passwordSameAsId`, welcome-F3, A-465)을 지켜야 한다. 검증 실패는 400, 파일 쓰기 실패는 500, 성공하면 `welcome/restart` 안내만 나온다 | `web/welcome/SiteSecretInterceptor.kt:41-98` |
| 설정 반영 | 새 비밀 키를 `<데이터>/config/secret.properties`에 먼저 쓰고, 성공했을 때만 `admin` 계정의 이름, 이메일, 비밀번호를 바꾼다. 다시 시작해야 반영된다. `admin` 사용자가 없으면 계정은 건너뛴다 | `security/SiteSecret.kt:49-80` |
| 설정이 이미 있는 채 첫 기동 | `yona.secret`이 기본값이 아니면 이 화면을 거치지 않으므로 `admin`은 적재 직후 고정 비밀번호 그대로이다. 이를 알리는 진단이나 경고는 없다(7절) | |
| 분실 초기화 | 관리자 비밀번호를 재설정하는 전용 흐름은 없다. 일반 비밀번호 분실(`/users/lostpassword` 계열, `PasswordResetController`)이 관리자에게도 같이 적용된다. 다른 관리자가 `POST /{loginId}?action=resetPassword`로 임시 값(6자)을 줄 수 있다. 이 임시 값은 `PasswordPolicy`(8자 이상)를 거치지 않으므로 정책 최소 길이보다 짧다(`PasswordResetController.kt:136`, `TEMPORARY_PASSWORD_LENGTH = 6`). 관리자 본인의 비밀번호 변경(`AccountController.kt:144`)과 메일 재설정(`PasswordResetController.kt:108`)과 가입(`SignupController.kt:59`)은 정책을 거친다. 2단계 인증 긴급 해제는 설정 `yona.two-factor.reset-user`이다(`TwoFactorService.resetByEmergency`) | |

### 5-2. 관리자 권한 부여와 해제

- 위치: 사용자 목록의 단추 → `POST /sites/toggleSiteAdminRole/{loginId}` → `SiteUserService.toggleSiteAdmin`(`SiteUserService.kt:168`). 이름처럼 **토글**이다(관리자이면 해제, 아니면 부여).
- 부여에는 제한이 없다. 해제만 `mayRevokeSiteAdmin`(`:164-165`)으로 막는다: 대상이 **자기 자신**이거나, **번호 1(기본 admin)**이거나, `site_admin` 행 수가 1 이하이면 403 + flash `site.userList.adminProtect`(자기 자신, 기본 admin, 마지막 사이트 관리자는 해제할 수 없다). v1.6에는 이 보호가 없었다(R-52-2, `SiteApp.java:185-199`).
- 화면은 이 보호를 미리 숨기지 않는다. 해제 단추는 자기 자신과 마지막 관리자 행에도 그려지고, 눌러서 403을 받으면 단추의 `request:error` 처리(`user-list.html:156-161`, site-F3)가 기본 처리를 막고 화면을 다시 읽어 flash 안내를 보인다. 기본 `admin`(번호 1)은 목록에서 아예 빠진다(`SiteUserService.kt:40-41`, 사이트 관리자 탭에서도). 같은 탭의 배지 수는 기본 admin 행을 뺀 `site_admin` 행 수이다(`siteAdminBadgeCount`, `:66`, 모델 `siteAdminCount`).
- 시험: `SiteUserManagementTest.kt:249,258,267`이 승격과 해제, 자기 자신 해제 거부, 기본 admin 해제 거부를 검증한다.

### 5-3. 사용자 목록의 상태 변경 규칙

| 동작 | 규칙 | 보호 | 근거 |
|---|---|---|---|
| 잠금과 해제 | ACTIVE이면 LOCKED, 그 밖이면 ACTIVE. 감사 | **관리자도 잠글 수 있다.** 대상이 요청한 관리자 자신이거나 기본 `admin`(번호 1)이면 상태를 바꾸지 않고 목록을 다시 그리며 flash `site.userList.selfProtect`(`SiteUserService.isProtectedFromLoss`, `:76`, site-F4). 다른 관리자와 마지막 관리자는 잠글 수 있다. 삭제된 사용자는 전환하지 않고 경고(`site.userList.deletedLock`) | `SiteUserController.kt:100-121`, `SiteUserService.kt:102-106` |
| 삭제 | 구성원, 조직 구성원, 즐겨찾기, 구독, 알림, 담당 이슈, 풀 리퀘스트 담당과 리뷰 요청, API 토큰, GPG, SSH, OAuth 앱과 동의, LFS 잠금, 2단계 인증 데이터를 지우고 상태를 DELETED, 이름 앞에 `[DELETED]`, 이메일 `deleted-<로그인ID>@noreply.yona.io`, 비밀번호 비움 | **대상이 요청한 관리자 자신이거나 기본 `admin`(번호 1)이면 403 + flash `site.userList.selfProtect`**(`isProtectedFromLoss`, 먼저 검사, site-F4). 프로젝트의 **유일한 매니저**이면 403(`isOnlyManager`). 다른 관리자나 마지막 관리자를 막는 검사는 없다 | `SiteUserController.kt:69-86`, `SiteUserService.kt:76-148` |
| 게스트 전환 | `is_guest` 토글. 감사 | 없음. 게스트가 되면 `@GuestProhibit`와 읽기 규칙(`user.isGuest != true`)에서 일반 사용자와 달라진다 | `SiteUserService.kt:151-157` |
| 비밀번호 초기화 | 임시 6자 | 대상이 SSO 사용자이고 관리자가 아니면 거부 | `PasswordResetController.kt:125-139` |
| 2단계 인증 해제 | 대상의 2단계 인증 데이터를 지운다 | 없음. 단추는 `row.twoFactorEnabled`일 때만 보인다 | `user-list.html:71`, `TwoFactorService.kt:183` |
| SSO 해제 | 대상의 SSO 연결만 지운다 | 단추는 `row.ssoLinked`일 때만 | `user-list.html:72` |

### 5-4. 마지막 관리자 보호 정리

있는 것: 관리자 권한 해제의 3중 보호(자기 자신, 번호 1, 관리자가 1행 이하), 삭제와 잠금에서 자기 자신과 기본 `admin`(번호 1) 보호(`isProtectedFromLoss`, 잠금은 flash와 함께 목록 200, 삭제는 403), 프로젝트의 유일한 매니저 삭제 보호.

없는 것: 다른 관리자를 잠그거나 삭제하는 동작에 대한 보호, 활성 관리자 수 기준의 검사(`siteAdmins.count()`는 잠기거나 삭제된 계정의 행도 센다, 발견 admin-role-F4), 외부 API `PATCH /-_-api/v1/admin/users/{user}`의 보호(발견 admin-role-F10).

## 6. 사이트 설정이 다른 화면에 미치는 영향

설정은 두 곳에서 나온다. **파일 설정 `yona.*`**(`YonaProperties`, 관리자가 서버 설정 파일을 고치고 재시작한다. 화면에서 바꾸는 UI가 없다)와 **DB 설정 `site_setting`**(`SiteSettingService`, `/sites/*` 화면에서 바꾼다). 브리프가 예로 든 "가입 허용", "프로젝트 만들기 허용", "비공개 허용" 같은 켜고 끄는 설정은 **v2 코드에 없다**(`allowSignup`, `allowProjectCreate` 검색 결과 없음. 프로젝트와 조직 만들기는 로그인한 누구나, `isGlobalResourceCreatable`). 있는 설정은 아래 표뿐이다.

### 6-1. DB 설정 (`/sites/*`에서 바꾼다, `domain/site/SiteSettingService.kt`)

| 설정 키(화면) | 기본 | 영향받는 곳 | 달라지는 것 |
|---|---|---|---|
| `security.two-factor-enforcement` OFF, ADMINS, ALL (`/sites/security`) | OFF | `TwoFactorEnforcementInterceptor.kt:33-40` | 대상(ADMINS이면 관리자, ALL이면 전원)이 2단계 인증을 켜지 않으면 모든 화면 요청이 `/user/editform/two-factor`로 303 이동한다(예외 경로는 3-3) |
| `security.oauth-cimd-enabled` (`/sites/security`) | 켬 | `CimdClientResolver.kt:77`, `AuthorizationServerConfig.kt:67` | 끄면 URL 형태 클라이언트 ID(CIMD)를 받지 않고(`resolve`가 null), 인가 서버 메타데이터의 `client_id_metadata_document_supported`가 false |
| `security.oauth-user-apps-enabled` | 켬 | `OAuthAppsSettingsController.kt:58,65,73`, `OrganizationOAuthAppsController.kt:58,65,73`, 템플릿 `oauth/partial-apps.html:33-34` | 끄면 사용자와 조직의 "OAuth 앱 만들기" 단추(`#oauth-app-new`)가 사라지고 안내 `#user-apps-disabled`(`oauth.apps.disabled`)가 나온다. 만들기 요청은 403 |
| `security.mcp-enabled` | 켬 | `McpAccessFilter.kt:40` | 끄면 `/mcp` 요청 전부 404 JSON `not found` |
| `security.mcp-write-enabled` | 켬 | `McpRestBridge.kt:57` | 끄면 쓰기 도구가 `관리자가 MCP 쓰기 도구를 껐습니다.` 오류 |
| `security.sso-only` (`/sites/sso/mode`) | 꺼짐 | `YonaPasswordAuthenticationProvider.kt:62`, `SsoLoginOptions.kt:28`, `LinkedAccountService.kt:41`, 템플릿 `user/login.html:15,25,41,44`, `common/login-dialog.html:30,47,54,57,59` | 로그인 화면에서 비밀번호 입력과 가입, 소셜 로그인이 숨고 SSO 제공자 단추만 남는다. 작은 링크 `/users/loginform?local=true`(`sso.login.localLink`)로 로컬 로그인 폼을 열 수 있다. 비관리자의 비밀번호 로그인은 서버가 거절한다. 사용자의 SSO 연결 해제(`unlinkOwn`)는 `SSO_ONLY`로 막힌다 |
| SSO 제공자 설정(LDAP, OIDC, SAML2, `/sites/sso/*`) | 없음 | `SsoLoginOptions.view()`가 만드는 `ssoLogin.providers` | 로그인 폼 아래 제공자 단추(`common/login-dialog.html:53-57`, `user/login.html:40-42`)가 생긴다 |
| `lfs.enabled` (`/sites/lfs`) | 켬 | `LfsService.available`(`domain/lfs/LfsService.kt:36`), `HgHttpFilter.kt:116`, `CodePageAssembler.kt:135`, `LfsRequestSupport.kt:52,59`, `ProjectLfsController.kt:64` | 끄면 LFS 요청이 프로젝트를 못 찾아 거절되고, 코드 보기의 LFS 포인터 안내(`code/view.html:118`)가 없어지고, 프로젝트의 LFS 설정 화면의 `enabled`가 false |
| `lfs.max-object-size`(2GiB), `lfs.project-quota`(0 = 무제한) | | `LfsService.kt:64-65,89-90`, `ProjectLfsController.kt:61-65` | 업로드 거절 한도. 프로젝트 LFS 화면의 `quotaText`(`project/lfs.html:21`)가 한도를 보여 준다 |

### 6-2. 파일 설정 (`yona.*`, `config/YonaProperties.kt`)

| 설정 | 영향받는 화면과 요소 |
|---|---|
| `hideProjectListing` | `/projects`와 `/orgs`는 403(`ProjectListController.kt:56`, `OrganizationController.kt:71`). 상단 바의 "전체 목록" 링크와 구분선이 사라진다(`common/navbar.html:26`). `common/usermenu.html`에는 이 설정을 보는 조건이 없다. 검색 범위 "전체"는 관리자만 남는다(`navbar.html:62`). 익명의 사용자 활동 탭이 비고(`UserPageController.kt:53`), 검색 결과(`SearchService.kt:186,221`), 조직 프로젝트 목록(`OrganizationService.kt:102`)에서 공개 프로젝트가 구성원에게만 보인다 |
| `displayPrivateRepositories` | `/projects`에 비공개 프로젝트가 목록에 나오고 읽을 수 없으면 흐리게 표시(`ProjectListController.kt:85`, `ProjectListService.kt:72`), 조직 목록도 같다(`OrganizationController.kt:77`) |
| `allowsAnonymousAccess` | 꺼지면 `@AnonymousCheck` 화면이 모두 로그인 요구(`AnonymousCheckStage.kt:25`), `isAllowed`가 익명이면 false(`AccessControl.kt:109`) |
| `signup.require.admin.confirm` | 가입 화면에 승인제 안내와 역순 이메일(`user/signup.html:21-22`), 가입 계정이 LOCKED로 생성(`SignupService.kt:105-113`). 관리자가 `/sites/toggleAccountLock`으로 풀 때까지 로그인 불가(`YonaPasswordAuthenticationProvider.kt:66` `Locked`) |
| `useEmailVerification` | 로그인 화면의 안내(`user/login.html:22`), 가입 계정이 LOCKED로 생성되고 인증 메일 링크로 활성화 |
| `maxFileSize` | 첨부 업로드 한도: `common/scripts.html:107`가 화면 스크립트에 값을 내려 준다. 위키 업로드는 `min(maxFileSize, 기본)`(`WikiService.kt:67`) |
| `parsersTextMaxLength` | 폼 본문 한도. 넘으면 `error/request-text-entity-too-large.html`(관리자에게만 조절 안내가 추가, 1-1) |
| `codeBrowserMaxFileSize` | 코드 보기에서 텍스트 파일 최대 크기. 넘으면 원본 링크만(관리자에게는 변경 안내가 더 붙음) |
| `feedbackUrl` | 비우면 상단 바의 피드백 링크가 사라진다(`navbar.html:33`) |
| `navbarCustomLinkName`, `navbarCustomLinkUrl` | 사용자 메뉴 앞의 추가 링크(`common/usermenu.html:41`) |
| `showUserEmail` | 프로필과 투표자 목록의 이메일(`UserPageController.kt:55`, `issue/partial-voter-list.html:29`) |
| `translationApi` | 비우면 이슈와 글 화면의 번역 단추가 없다(`site.translationApiConfigured`, `issue/view.html:109`, `board/view.html:75`, 댓글 두 곳). 서버는 412(`TranslationApiController.kt:50`) |
| `sendYonaUsage` + `analytics.measurementId` | `common/analytics.html`이 GA4 스크립트를 넣을지(`AnalyticsSettings.kt:18`) |
| `projectDefaultScope`, `projectDefaultMenus` | 프로젝트 만들기와 가져오기 화면의 기본 선택(`ProjectCreateController.kt:119-120`, `ImportController.kt:91`), API 프로젝트 만들기 기본값 |
| `allowedSendingMailDomains` | 가입과 알림 메일 대상 도메인 제한(`SignupService.kt:65`, `NotificationMailSender.kt:82`) |
| `siteName` | 로그인, 가입, 비밀번호 분실, 알림 소개, 이전 화면, 코드 안내 화면, 404 화면의 제목 |
| `update.check` | 꺼지면 `/sites/update`가 현재 버전을 알리지 않는다(`SiteMaintenanceController.kt:36`), 스케줄러도 시작하지 않는다(`UpdateCheckScheduler.kt:36`) |
| `secret` | 기본값이면 최초 기동 화면(5-1) |

## 7. 관리자 알림과 배너

| 안내 | 대상 | 조건 | 위치와 모양 | 끄는 법 |
|---|---|---|---|---|
| 업데이트 알림 | 사이트 관리자만 | 새 버전을 찾았고(`versionToUpdate`), 알림이 켜져 있고(`UpdateCheckService.shouldNotify`), 보는 사람이 관리자(`LayoutModelFactory.updateNotice`, `LayoutModelAdvice.kt:58-64`) | 모든 일반 레이아웃 맨 위(`layout/layout.html:60-62`, 이전 레이아웃 `:49-52`). 링크 `site.update.notification(버전)`과 단추 `site.update.notification.hide(버전)` | 단추 → `POST /sites/unwatchUpdate`. 서버 전체에 적용되고 재시작 뒤에도 꺼진 채(`UpdateCheckService.kt:99-102`). 응답 200 빈 본문, `request:load`로 화면 새로 고침(`esm/Request.js` 기본 동작). 다시 켜는 곳은 `/sites/update`의 "새 버전 알림 켜기" 단추(`POST /sites/watchUpdate`, `UpdateCheckService.watch`, site-F6)이다 |
| 사이드바 배지 `1` | 관리 화면 안(관리자만) | `versionToUpdate != null`. **알림을 껐어도 남는다**(`shouldNotify`가 아니라 값만 본다) | `layout/site-mng-layout.html:33` | 새 버전이 없어질 때까지 |
| 진단 목록 | 관리자 화면 `/sites/diagnostic`에서만 | 점검 항목(`DiagnosticCheck`)이 문제를 돌려줄 때: 포트만 정하고 호스트가 없음(`ConfigDiagnostic`), 같은 속성이 중복 저장(`PropertyDiagnostic`), 메일함 연결(`MailboxDiagnostic`, `mailbox/MailboxLifecycle.kt:21`) | `site/diagnostic.html`. 문제가 없으면 `site.diagnostic.errorNotFound`. 점검이 예외를 던지면 `Failed to diagnose: ...` 스택 트레이스 | 설정을 고친다 |
| 관리자 로그인 띠 | 로그인 ID가 `admin`인 사용자 | 3절 | 모든 일반 레이아웃 맨 위 | 로그아웃 |
| 2단계 인증 강제 이동 | 강제 대상(관리자 또는 전원) | 3-3 | 303 + flash `twoFactor.enforced` | 2단계 인증을 켜거나 `/sites/security`에서 강제를 바꾼다 |
| 최초 기동 화면 | 첫 사용자(사실상 관리자) | `yona.secret`이 기본값 | `welcome/secret`, 이후 `welcome/restart` | 5-1 |
| 오류 화면의 설정 안내 | 관리자만 | 폼 본문 초과, 코드 큰 파일 | 1-1 | |
| 새 버전 알림 상태 | 관리자(`/sites/update`) | 항상. `site.update.notice.on`/`off`, 꺼져 있으면 "새 버전 알림 켜기" 단추 | `site/update.html`(`#update-notice-state`, `#update-notice-form`) | 단추를 누른다(`POST /sites/watchUpdate`) |
| 저장 범위 안내 | 관리자(`/sites/security`) | 항상. 저장 단추 세 개 각각 옆에 `site.security.saveScope`가 영역 이름과 함께 나온다 | `site/security.html` | |
| SSO 검사 안내 | 관리자(`/sites/sso`) | 항상. LDAP, OIDC 검사 단추 옆 `site.sso.check.saved` | `site/sso.html` | |
| 큰 위협이나 라이선스 경고 | **없음** | 라이선스 검사나 보안 경고(기본 비밀번호 사용 등)를 알리는 배너, 진단, 알림은 코드에서 찾지 못했다. 진단 점검은 위 3개뿐이다 | | |
| 관리자에게 가는 알림 메일 | 없음 | 가입 승인 대기를 관리자에게 알리는 메일이나 알림 이벤트는 찾지 못했다. 승인제에서는 사용자가 관리자 이메일(역순)을 보고 연락한다 | `user/signup.html:21-22` | |

## 8. 관리자 권한 행렬 요약

"보임"은 템플릿 조건, "서버"는 실제 허용이다. 관리자는 `isAllowed`의 `:111` 때문에 프로젝트와 조직 리소스 전체에서 서버가 허용한다(OAuth 조직 정책 마스킹 제외).

| 기능 | 일반 로그인 사용자 | 프로젝트 매니저 | 조직 관리자 | 사이트 관리자 |
|---|---|---|---|---|
| 렌치와 `/sites/*` | 없음, 403 | 같다 | 같다 | 보임, 허용 |
| 비공개 프로젝트 읽기 | 구성원만 | 구성원 | 조직 소유면 허용 | 허용 |
| 프로젝트 설정 톱니(`canUpdate`) | 매니저와 조직 관리자만 | 보임 | 보임 | 보임 |
| 설정 단축키 Q, 라벨 편집 링크 | 없음 | 보임 | 없음 | **없음, 서버는 허용** |
| 프로젝트 탈퇴 단추(프로젝트 홈) | 역할 행이 있고 `LEAVE`가 참이면 | 같다 | 같다 | **역할 행이 있는 프로젝트만 보임**(구성원이 아니면 없음). 서버는 모든 프로젝트에서 허용 |
| 가입 신청 단추(프로젝트) | 역할 없으면 보임 | 아니오 | 아니오 | **아니오(게스트가 아님)** |
| 코드 열람 제한 프로젝트 | 구성원만 | 구성원 | 구성원 아니면 불가 | 허용(`hasMember`) |
| 규칙 묶음 우회(푸시, 병합) | 불가 | `bypassAdmins` 규칙이면 | 불가 | `bypassAdmins` 규칙이면 |
| 목록 숨김일 때 `/projects` | 403 | 403 | 403 | **403** |
| 목록 숨김일 때 조직 프로젝트 목록 | 제한 | 제한 | 전체 | **전체** |
| 사용자 상태 변경, 삭제, 관리자 부여와 해제 | 불가 | 불가 | 불가 | 허용(해제는 3중 보호) |
| 임시가 아닌 첨부 이동 | 불가 | 불가 | 불가 | 허용 |
| API `site_admin` 범위 토큰 | 만들 수 없다 | 만들 수 없다 | 만들 수 없다 | 만들 수 있다 |
| 첨부 업로드 명의(`authorEmail`, `authorLoginId`) 지정 | 무시, 본인 명의 | 무시 | 무시 | **지정할 수 있다**(익명은 403) |
| 자기 자신과 기본 `admin`의 삭제와 잠금 | 해당 없음 | 해당 없음 | 해당 없음 | **불가**(삭제 403, 잠금은 flash와 목록 200). 관리자 권한 해제는 마지막 관리자까지 3중 보호 |
| 메뉴를 끈 이슈, 마일스톤 주소 | 404 | 404 | 404 | **404**(우회 없음) |
| 로그인 잠금(5번 실패, 15분) | 적용 | 적용 | 적용 | **적용**(예외 없음) |
| SSO 전용 모드의 비밀번호 로그인 | 거절 | 거절 | 거절 | **허용**(비상 경로, 잠금은 적용) |
| 비밀번호 정책(8자 이상) | 가입, 변경, 재설정에 적용 | 같다 | 같다 | 같다. 단 다른 관리자가 주는 임시 비밀번호(6자)는 정책 밖 |

## 발견 사항 목록

확인한 사실만 적는다. 추측은 "추측"이라고 썼다.

- **admin-role-F1. 붉은 띠는 "기본 admin 계정으로 로그인"이다.** `loginId == "admin"`만 본다(`web/layout/LayoutModelAdvice.kt:76`). 다른 관리자에게는 띠가 없다. `architecture/ui-ia-gitlab.md:180`은 이것을 "관리자 세션 알림"이라 부르고 일반 관리자도 본다고 읽힐 수 있다. 새 UI가 "관리자 세션" 안내로 바꾸면 계약이 달라진다. 관리자가 다른 사용자로 로그인하는 기능과 되돌아오기, 그 감사 기록은 v2에도 v1.6에도 없다.
- **admin-role-F2. 관리자 로그인 띠를 검사하는 시험이 없다.** `StickyPanelsTest.kt:38-45`는 CSS와 속성만 본다. 판정과 `layout.html:59` 표시는 시험 없음.
- **admin-role-F3. 검색 범위 "전체"는 관리자에게 항상 보이지만 서버는 관리자를 넓혀 주지 않는다.** `common/navbar.html:62`는 `hideProjectListing`이어도 관리자에게 항목을 보인다. 그러나 `SearchService.visibleProjects`와 `projectCondition`(`domain/search/SearchService.kt:181-230`)에는 관리자 분기가 없고(파일 안 `siteManager` 검색 결과 없음), 목록 숨김이면 공개 프로젝트도 구성원 것만 찾는다. 반면 API의 풀 리퀘스트 검색은 관리자에게 제한이 없다(`ExternalSearchApiController.kt:138`). 또 `/search`의 전체 검색은 `hideProjectListing` 검사를 하지 않는다(`web/search/SearchController.kt:50-58`).
- **admin-role-F4. "마지막 관리자" 보호는 행 수 기준이라 다른 관리자의 잠금과 삭제로 우회된다.** `mayRevokeSiteAdmin`은 `siteAdmins.count() > 1`을 보는데(`SiteUserService.kt:165`) `deleteUser`는 `site_admin` 행을 지우지 않고(`:88-97`) 다른 관리자의 잠금에는 검사가 없다. 한 관리자가 다른 관리자를 삭제해도 행이 남아 해제 보호가 풀린다. (2026-11-11 수정: 요청한 관리자 자신과 기본 admin(번호 1)의 삭제와 잠금은 `isProtectedFromLoss`(`SiteUserService.kt:76`)로 막았다(site-F4). 삭제는 403 + flash `site.userList.selfProtect`, 잠금은 목록 200 + 같은 flash. 위의 행 수 기준 우회는 남아 있다.)
- **admin-role-F5. 목록에서 숨긴 것과 서버가 막는 것이 일부 다르다.** 기본 admin은 목록에 없고 `DELETE /sites/user/delete1`, `POST /sites/toggleAccountLock?loginId=admin`은 서버가 막는다. 그러나 `POST /sites/toggleGuestMode`, `resetTwoFactor`, `unlinkSso`, `/{loginId}?action=resetPassword`는 기본 admin과 자기 자신에게도 막지 않는다. (2026-11-11 수정: 삭제와 잠금의 자기 자신과 기본 admin 보호를 더했다, site-F4.)
- **admin-role-F6. 관리자 권한 해제 시 세션이 끊긴다는 주석이 실제와 다르다.** `SiteUserService.kt:174`과 `SessionUserCache.kt` 문서는 `invalidate`로 다음 요청에서 재로그인이라 하지만, `YonaAuthenticationFilter.kt:108`이 캐시에 없으면 `remember`로 되살려 세션이 유지된다(R-106). 권한 변화는 요청 단위 판정으로 즉시 반영된다(`SessionPermissions.kt:11-24,56-66`). 주석과 `SessionUserCache` 문서가 낡았다.
- **admin-role-F7. 관리자에게 `LEAVE`가 모든 프로젝트에서 참이라 서버는 어느 프로젝트의 탈퇴도 허용하지만, 단추는 역할 행이 있을 때만 보인다.** `isAllowed`가 `LEAVE` 앞에서 관리자를 통과시키므로(`AccessControl.kt:111` 이 `:154` 앞) `UserLeaveController.kt:46`은 구성원이 아닌 프로젝트도 허용한다(소유자 검사와 구성원 검사도 건너뛴다. 구성원이 아니면 `removeMember`가 아무것도 하지 않고 프로필로 이동한다). v1.6과 같다(`AccessControl.java:56`이 `:176`보다 앞). (2026-11-11 수정: `project/home.html:124`의 단추 조건을 `projectMember && isAllowed(LEAVE)`(`ProjectViewContext.kt:129`)로 바꿔 구성원이 아닌 관리자에게는 단추를 숨긴다(project-F8). `user/partial-projectlist.html:48`은 관리자 판정과 무관하고 본인 프로필의 소유자가 아닌 프로젝트에만 보인다.)
- **admin-role-F8. 프로젝트 매니저 전용 화면 요소가 사이트 관리자에게는 없다.** 라벨 편집 링크(`issue/partial-select-label.html:12`), Q 단축키(`project/menu.html:92`), 키 안내표의 Q 줄(`help/keymap.html:47`)은 `view.manager`(프로젝트 매니저 역할 행만, `ProjectViewContext.kt:138`)를 본다. 같은 화면의 설정 톱니는 `canUpdate`라 관리자에게도 보이고, 서버는 관리자의 라벨 편집과 설정 화면을 허용한다.
- **admin-role-F9. `@SiteManagerOnly`가 붙은 API는 거부 시 HTML 오류 화면을 낸다.** `ExternalUserApiController.kt:186,202`와 `ExternalSiteBackupApiController.kt:33`은 `ViewResultException`을 던지고 `GlobalErrorHandler.kt:35-36`이 레이아웃을 입힌 화면으로 바꾼다. 같은 API 군의 `admin/audit*`는 JSON 403, `POST /users`와 `POST /owners/*/projects`는 **400**(v1.6 호환 문구)이다. 범위가 있는 토큰이지만 관리자에서 해제된 사용자는 HTML 403을 받는다. 거부 형태가 세 가지로 갈린다(시험으로 HTML 응답을 확인하지는 않았다, 코드 읽기 결과이다).
- **admin-role-F10. `PATCH /-_-api/v1/admin/users/{user}`는 감사 기록이 없고 삭제 정리를 하지 않는다.** `target.state = state; users.save`만 한다(`ExternalUserApiController.kt:209-210`). 웹의 `SiteUserService.changeState`(감사 `site.user_state_changed`, DELETED이면 이름, 토큰, 키 등 정리, 세션 캐시 갱신)를 쓰지 않는다. `state=GUEST`처럼 잠금도 활성도 아닌 값도 그대로 저장된다(`UserState`에 GUEST가 있다). v1.6 대조는 하지 않았다.
- **admin-role-F11. 관리자 행위 중 감사 기록이 없는 것.** 메일 발송(`SiteMailController.kt:35`), `setAttachmentToUserAvatar`(`SiteDataController.kt:119`), API 사용자 상태 변경(F10), API 프로젝트와 사용자 만들기는 이 컨트롤러들에서 `audit.record` 호출이 없다(grep 확인. 서비스 안쪽 기록은 `apiUsers.create` 등을 열어 보지 않았다).
- **admin-role-F12. 사용자 프로필의 `SITE ADMIN`과 `BLOCKED` 배지는 모두에게 보인다.** `user/view.html:36-37`은 보는 사람을 가리지 않는다. 누가 관리자인지 일반 사용자도 알 수 있다.
- **admin-role-F13. OAuth 조직 정책이 관리자 우회를 막는다.** `orgPolicy.masks`이면 `isAllowed`가 요청자를 익명으로 바꾸고(`AccessControl.kt:107-108`) `isProjectResourceCreatable`도 false(`:66`)이다. 관리자도 승인되지 않은 앱 토큰으로는 그 조직 자원에 접근하지 못한다. 접근 규칙 위키(`subsystems/access-control.md:40`)의 "관리자이면 허용" 설명에는 이 예외가 없다.
- **admin-role-F14. 보안 점검 경고가 없다.** 설치 직후 `admin`의 고정 비밀번호는 `yona.secret`을 이미 정해 둔 서버에서는 바뀌지 않고(5-1), 이를 알리는 진단이나 배너가 없다. 라이선스 경고도 없다. 진단은 세 가지뿐이다(7절).
- **admin-role-F15. 관리 사이드바 배지는 알림을 껐어도 남는다.** `layout/site-mng-layout.html:33`은 `versionToUpdate`만 보고 `shouldNotify`를 보지 않는다. 상단 알림은 꺼져도 관리 화면의 `업데이트` 항목 배지는 그대로이다.
- **admin-role-F16. 관리자 해제 단추가 보호 대상에도 그려진다.** `user-list.html:73`은 자기 자신과 마지막 관리자에게도 해제 단추를 그리고, 서버가 403 + flash로 거절한다(`SiteUserController.kt:90-93`). 화면 수준의 사전 비활성화가 없다. (2026-11-11 수정: 403을 받으면 단추의 `request:error` 처리가 화면을 다시 읽어 flash 안내를 보이도록 했다(`user-list.html:156-161`, site-F3). 단추를 미리 비활성화하지는 않는다.)
- **admin-role-F17. 관리자가 조직 구성원이 아닐 때 목록 숨김 검색.** `common/navbar.html:55-58`의 조직 범위 항목은 `memberOf or adminOf`(조직 역할 행)만 본다. 사이트 관리자는 포함되지 않아, 목록 숨김일 때 관리자도 조직 검색 항목이 없다. 서버 `web/search/SearchController.searchInAGroup`(`:63-72`)도 조직 역할만 보고 400을 낸다(관리자 분기 없음). 화면과 서버는 맞는다.
- **admin-role-F18. 브리프 예시 설정이 없다.** "가입 허용", "프로젝트 만들기 허용", "비공개 허용", "첨부 최대 크기를 화면에서 바꾸기", "SSO 켜기"(SSO 켜기와 전용 모드는 `/sites/sso`에 있다) 중 앞의 셋은 코드에 없다. 첨부 최대 크기는 파일 설정 `maxFileSize`뿐이고 화면에서 바꿀 수 없다.
- **admin-role-F19. 이 문서가 열어 보지 못했던 것을 확인했다.** `layout/site-layout.html:12`가 `layout/layout`을 감싸므로 `/sites/*` 화면에도 관리자 로그인 띠가 나온다(3-2). `noAvatarUsers`와 `setAttachmentToUserAvatar`를 부르는 템플릿과 스크립트는 없다. 외부 API `orgs/{org}/audit`의 줄은 `ExternalAuditApiController.kt:120`이다. (2026-11-11 수정: 열어 보지 못했다는 서술을 확인 결과로 바꿨다.)
- **admin-role-F20. 로그인 잠금은 관리자에게도 같은 규칙으로 적용된다.** `PasswordLoginFilter.kt:56`이 입력한 아이디(또는 이메일)를 소문자로 맞춘 키 `login:<값>`으로 5번 연달아 틀린 요청을 15분 거절하며(`TwoFactorAttempts.LIMIT=5`, `LOCK=15분`, 표 `attempt_record`) `admin` 같은 관리자 아이디도 예외가 아니다. 키가 계정 존재 여부와 무관하므로 누구나 관리자 아이디로 5번 틀리면 그 아이디의 로그인이 15분 막힌다(추측: 서비스 거부로 이어질 수 있다. 시험하지 않았다). 이 검사는 SSO 전용 모드의 관리자 통과 검사보다 앞에 있다. [재조사 2026-11-11]
- **admin-role-F21. 관리자가 주는 임시 비밀번호(6자)는 비밀번호 정책(8자 이상)을 거치지 않는다.** `PasswordResetController.kt:136`이 토큰 앞 6자를 임시 비밀번호로 쓰고 `passwords.set`만 부른다. 같은 컨트롤러의 메일 재설정(`:108`), 가입, 비밀번호 변경, 최초 관리자 설정(`SiteSecretInterceptor.kt:80`)은 `PasswordPolicy.check`를 거친다. 임시 비밀번호를 받은 사용자가 같은 값을 계속 쓰면 정책보다 짧은 비밀번호가 남는다(변경을 강제하는 흐름은 코드에 없다). [재조사 2026-11-11]
- **admin-role-F22. 데이터 화면 `/sites/data`로 가는 사이드바 링크가 없다.** `layout/site-mng-layout.html:25-41`의 13개 링크에 `/sites/data`가 없고 템플릿 어디에도 `/sites/data` 링크가 없다. 화면과 `/sites/export`, `/sites/import`는 주소로만 열린다. [재조사 2026-11-11]
- **admin-role-F23. 관리자 판정 경로가 둘이다.** `SessionPermissions.siteManager`는 요청 하나 안에서 판정을 기억하지만, `SiteUserService.isSiteManager`(`:62`), `YonaPasswordAuthenticationProvider.kt:62`, `NoheadViews.kt:39`, `UserPageAssembler.kt:63`은 `site_admin` 표를 매번 직접 조회한다. 같은 요청 안에서 권한이 바뀌는 경우가 아니면 결과가 같다. [재조사 2026-11-11]
- **admin-role-F24. 메뉴를 끈 이슈와 마일스톤 주소는 관리자에게도 404이다.** `ProjectCheckStage.checkMenuEnabled`(`:98`)에는 관리자 분기가 없다. 읽기 검사(관리자 통과)를 거친 뒤에 메뉴 설정만 본다. 라벨 관리 주소(`issue/label*`)와 API 경로는 막지 않는다. 데이터는 지우지 않아 메뉴를 다시 켜면 그대로 열린다. v1.6은 주소로 계속 열려 있었다(issue-F24). [재조사 2026-11-11]
- **admin-role-F25. 사이트 관리자의 업로더 지정은 화면에서 쓰는 요소가 없다.** `AttachmentController.findUploader`(`:151`)는 사이트 관리자만 폼 본문 `authorEmail`/`authorLoginId`로 다른 사용자 명의로 올리게 하지만(shell-F23, A-460) 템플릿과 스크립트에 이 항목을 보내는 곳이 없다. 외부 스크립트용 계약이다. [재조사 2026-11-11]

## 범위 점검

템플릿 230개를 이름 기준으로 grep해(`siteManager`, `siteAdmin`, `.manager`, `canManage`, `canUpdate`, `canLeave`, `admin`) 관리자 관련 조건이 있는 파일을 모두 열어 읽었다(2026-11-11 재조사에서 다시 수행). 나머지 파일에는 `siteManager`, `siteAdmin`, `admin`, `canUpdate`, `canManage`, `canLeave`, `manager` 어느 것도 걸리지 않았다(조건 없음).

| 템플릿 | 다룬 절 |
|---|---|
| `common/usermenu.html`, `common/navbar.html` | 1-1, 1-3, 6-2 |
| `layout/layout.html`, `layout/migration-layout.html`, `layout/site-mng-layout.html` | 1-2, 3, 7 |
| `index/notifications.html` | 1-2, 3-2 |
| `error/request-text-entity-too-large.html`, `code/view.html` | 1-1, 6-2 |
| `user/view.html`, `user/signup.html`, `user/login.html`, `common/login-dialog.html` | 1-1, 1-4, 6 |
| `project/menu.html`, `project/home.html`, `project/header.html`, `project/partial-readme.html`, `project/ruleset-form.html`, `project/partial-settingmenu.html`, `project/lfs.html` | 1-3, 6 |
| `issue/partial-select-label.html`, `issue/partial-searchform.html`, `help/keymap.html` | 1-3, 2-5 |
| `organization/menu.html`, `organization/view.html`, `organization/header.html`, `organization/oauth-apps.html` | 1-3, 6-1 |
| `user/partial-projectlist.html`, `user/edit-oauth-apps.html`, `oauth/partial-apps.html` | 1-3, 6-1 |
| `welcome/secret.html`, `welcome/restart.html` | 5-1 |
| `board/view.html`, `board/edit.html`, `board/partial-comments.html`, `issue/view.html`, `issue/edit.html`, `issue/partial-comment.html`, `milestone/view.html`, `project/partial-issue-labels-list.html`, `code/nohead.html`, `code/nohead-hg.html`, `code/nohead-svn.html` | 1-3(`canUpdate`, `canDelete` 계열) |
| `migration/home.html` | 관리자 판정이 아닌 이전 대상 계정의 조직 관리자 권한 경고(`warn-not-admin`)이며 사이트 관리자 조건은 아니다 |
| `site/*.html` 15개 | 1-5, 4 (상세는 `site*.md`) |
| 위에 없는 나머지 템플릿 | 관리자 조건 없음 (grep) |

## 갱신 기록 (2026-11-11 재조사)

- 기준: 커밋 `5ef425c98` 이후 HEAD(브랜치 ui-improvements)의 소스. `grep`으로 `siteManager`, `isSiteManager`, `SiteManagerOnly`를 `src/main` 전체에서 다시 찾아 표를 맞췄다.
- 다시 확인한 템플릿: 230개 전체를 `siteManager|siteAdmin|.manager|canManage|canUpdate|canLeave|admin`으로 grep. 열어서 대조한 것은 36개: `common/usermenu`, `common/navbar`, `layout/layout`, `layout/migration-layout`, `layout/site-layout`, `layout/site-mng-layout`, `index/notifications`, `error/request-text-entity-too-large`, `code/view`, `code/nohead`, `code/nohead-hg`, `code/nohead-svn`, `user/view`, `user/signup`, `user/login`, `user/partial-projectlist`, `common/login-dialog`, `project/menu`, `project/home`, `project/header`, `project/ruleset-form`, `issue/partial-select-label`, `issue/partial-searchform`, `help/keymap`, `organization/menu`, `organization/view`, `organization/header`, `welcome/secret`, `welcome/restart`, `site/user-list`, `site/project-list`, `site/update`, `site/security`, `site/sso`, `site/audit`(과 `audit/list`).
- 고친 행(내용이 바뀐 것): 약 40행. 요약: `@SiteManagerOnly` 개수(클래스 11, 메서드 2, 인라인 6), 사이드바 링크 수(14개가 아니라 13개)와 `/sites/data` 링크 없음, 메뉴 끈 기능의 404(`checkMenuEnabled`)와 접근 검사 체인 행, 읽기 권한 검사 보강에서의 관리자, 프로젝트 홈의 구성원 추가 링크 조건(`canUpdate`)과 탈퇴 단추 조건(`projectMember && LEAVE`), 라벨 관리 링크 조건(`isAllowed(UPDATE)`), 조직 탈퇴 차단 조건(`admin && 1명`), 공지 올리기 판정(`BOARD_NOTICE` 생성 권한), 업로더 선택, 로그인 잠금, SSO 전용 모드와 로컬 로그인 링크, 2단계 인증 강제의 예외 경로, 삭제와 잠금의 자기 자신, 기본 admin 거절, 삭제 계정 이메일 형식(`deleted-<로그인ID>`), 최초 관리자 설정의 비밀번호 정책, 임시 비밀번호(6자)와 정책, 사이트 관리 화면 변화(메일 `errorMessage` 검사, 업데이트 알림 상태와 켜기, 보안 화면 저장 범위 안내, SSO 검사 안내, 사용자 목록 403 새로 고침, 프로젝트 삭제 실패 표시, 감사 화면 스트림 표와 검증 실패 문구), 관리자 로그인 띠가 `/sites/*`에도 나옴, `usermenu`의 `hideProjectListing` 서술 삭제.
- 줄 번호만 고친 곳: 약 45곳(`AccessControl.kt`, `ProjectViewContext.kt`, `OrganizationViewContext.kt`, `SiteUserController.kt`, `SiteUserService.kt`, `PasswordResetController.kt`, `SiteMaintenanceController.kt`, `ProjectCheckStage.kt`, `AnonymousCheckStage.kt`, `help/keymap.html`, `organization/menu.html`, `user-list.html` 등).
- 더한 행: 접근 검사 체인의 읽기 권한 보강 문단, 2-3의 `findUploader`, `NoheadViews`, 로그인 잠금 3행, 1-3의 리소스 `canUpdate` 계열 2행, 4-1의 `/sites/watchUpdate`, 7절의 알림 상태, 저장 범위 안내, SSO 검사 안내 3행, 8절 행렬 7행, 3-3의 로그인 잠금 행, 범위 점검 2행.
- 없앤 행: 없음(옛 서술은 현재 동작으로 직접 고쳤다).
- 새로 더한 발견 사항: admin-role-F20(로그인 잠금이 관리자에게도 적용), F21(임시 비밀번호 6자와 정책), F22(`/sites/data` 사이드바 링크 없음), F23(관리자 판정 경로 둘), F24(메뉴 끈 주소는 관리자도 404), F25(업로더 지정을 보내는 화면 요소 없음). 고친 발견 사항: F4, F5, F7, F16(수정 표시), F19(확인 결과로 교체), F3, F6, F8, F10, F17(줄 번호).
