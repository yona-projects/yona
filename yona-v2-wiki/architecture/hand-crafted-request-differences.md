---
title: 직접 만든 요청에서만 닿는 v1.6과의 차이 (A-24 적용)
type: architecture
updated: 2026-10-26
---

# 직접 만든 요청에서만 닿는 v1.6과의 차이

화면과 스크립트가 보내는 요청으로는 닿지 않고, 값을 직접 만들어 보내야만 v1.6과 달라지는 항목이다. [A-24](../decisions.md)(화면을 쓰는 사용자 입장에서 정상 동작이면 요청 조작으로만 가능한 v1.6의 허점은 유지하지 않는다)와 선생님 지시(2026-10-26: v1.6이 오류를 내는 경우는 v2를 유지한다, 재현하지 않는 항목은 A-24에 따라 보완한다)에 따라 **v1.6을 재현하지 않고** 여기에 근거를 남긴다. v1.6 열은 v1.6 소스(`60a5ac406`) 기준이다.

| # | 대상 | v1.6 | v2 |
|---|---|---|---|
| U-06 | `UserApp.newUser` | `loginId`, `password`가 없으면 검증에서 NPE, 500 | 검증 오류 화면 |
| U-08 | `resetUserPassword` | 숨은 `loginId`가 없거나 형식이 틀리면 400 화면, `retypedPassword`는 검사하지 않음 | `loginId` 형식을 검사하지 않음 |
| U-09 | `resetUserPassword` | 비로그인이거나 `oldPassword`가 없으면 `IllegalArgumentException`, 500 | 로그인 이동 또는 400 |
| U-10 | 클래스 수준 `@AnonymousCheck` | `allowsAnonymousAccess=false`일 때 `resetUserPassword` 등 일부 액션은 애너테이션이 없어 그대로 진입(403/500) | 모두 로그인 화면으로 이동. 기본 설정에서는 같다 |
| U-19 | 사이트 관리자의 비밀번호 재설정 | `action` 쿼리가 없으면 NPE, 500. 값이 다르면 400 | 둘 다 400 |
| U-28 | `oAuthLogout`, `returnToReferer` | Referer가 없으면 URL이 아닌 `index().toString()`으로 이동 | 홈으로 이동(R-03과 같은 취지) |
| P-02 | `projectOverviewUpdate` | JSON이 아닌 본문이면 NPE, 500 | 400 |
| P-10 | `mentionList` | 알 수 없는 `resourceType`은 `IllegalArgumentException`, 500 | 무시하고 후보를 돌려줌 |
| P-15 | `mentionListAtPullRequest` | 없는 풀 리퀘스트는 NPE, 500 | 404(A-72와 같은 취지) |
| P-21 | 프로젝트 `newMember` | 폼 오류(`loginId` 필수, 형식, 예약어)는 `project.members.addMember`로 알림 | 형식이 틀린 `loginId`도 `project.member.notExist` |
| P-35 | 조직 `addMember` | `loginId` 제약을 어기면 `get()`에서 예외, 500 | 303과 경고 |
| P-40 | `updateOrganizationInfo` | 형식 오류와 권한 없음이 겹치면 `get()`이 권한 검사보다 앞서 500 | 403 |
| I-08 | `newIssueFormByComment` | 없는 `commentId`는 NPE, 500 | 404 |
| I-11 | `massUpdate` | Referer가 없으면 `redirect(null)` | 홈 또는 목록(R-03과 같은 취지) |
| I-12 | `newIssue` | `targetProjectId`, `milestoneId`, `parentIssueId`가 숫자가 아니면 `NumberFormatException`, 500 | 무시하거나 400 |
| I-20b | `editIssue` | 없는 이슈 NPE, 숫자가 아닌 `milestoneId` NFE, 500 | 404 또는 400 |
| B-01 | `organizationBoards` | `orderBy`를 덮어쓴 뒤 404, `HIDE_PROJECT_LISTING` 검사 없음, `orderBy` 무검증 | 정렬 기준 화이트리스트(A-37, A-40과 같은 취지) |
| B-03b | 게시글 `newPost` | `issueTemplate`이 없으면 NPE, 500. 권한 없는 사용자의 `notice`도 저장 | 오류 화면. 공지 권한이 없으면 무시(보안 보완) |
| B-08 | 게시판 `newComment` | `parentCommentId`가 비숫자면 NFE, 빈 문자열이고 기존 댓글이 있으면 NPE, 500 | 400 또는 부모 없음으로 처리 |
| P-03(4) | 프로젝트 홈 `tabId` | 알 수 없는 값은 `MatchError`, 500 ([R-68](../refactoring-backlog.md)(4)) | 내용 없는 탭 화면 |
| REST-10 | `updateIssue` | 상태 문자열을 `toString()`과 비교해 소문자 `open`이면 `State.valueOf`로 500, 담당자 이벤트 새 값은 JSON의 loginId | 대소문자 무시, 이메일로 찾은 사용자의 loginId |
| REST-29 | `newLabel` | `isExclusive` 키가 없으면 NPE, 500 | 키 없이도 성공 |
| REST-22 | `translate` | 없는 이슈나 댓글은 NPE 500, 본문 JSON 누락은 NPE | 404, 400 |
| REST-39 | `editPullRequest` | 폼 오류 검사 없이 `form.get()`, 오류면 500 | 제목이나 브랜치가 비면 400 |
| REST-47 | `CompareApp.compare` | 없는 리비전은 `MissingObjectException`, 500 | 404 |
| REST-48 | `BranchApp.branches` | 빈 저장소는 `parseCommit(null)` NPE, 500 (선생님 지시 2026-10-26: v1.6의 오류는 v2 유지) | 404 |
| S-7 | `getUserExpressionList` | 쿼리 `type`이 임의 컬럼 이름이어서 email, token 등 정확 일치 조회가 된다 | `loginId`, `name`, `email`, `englishName`만 받고 그 밖은 빈 목록 |
| S-8 | `updateAssginees`, `updateSharer`, 가중치 API | 없는 프로젝트나 이슈면 NPE, 500 | 404 |
| S-1 | `MarkdownApp.render` | JSON 본문이 없으면 NPE, 500 | 400 |
| S-2 | 이슈 번역 | 없는 프로젝트의 댓글도 번역됨 | 404 |
| D-7 | 즐겨찾기 토글 | 없는 프로젝트나 조직 id면 생성자에서 NPE, 500 | 400 `Wrong ... id` |
| M-8b | `Comment.updateMention` | 본문이 null이면 NPE | 건너뜀 |
| M-14 | `ProjectApp.mentionList`, `labels`, `IssueLabelApp.delete` | 없는 프로젝트, `_method` 없음, 없는 라벨이면 NPE, 500 | 403/404, 400/404 |
| M-15 | `CodeHistoryApp`, `SiteApp` | 숫자가 아닌 `page`, 알 수 없는 `state`는 예외, 500 | 400 |
| M-16 | `IssueApi.findAuthor` | `author`에 `email` 필드가 없으면 NPE, 500 | 익명 작성자(번호 -1) |

## 9~11차 대조에서 추가된 항목 (2026-10-26)

- 숨은 프로젝트 목록 설정(`HIDE_PROJECT_LISTING`)이 켜진 사이트에서 **없는 조직 이름**으로 조직 검색을 하면, v1.6은 본문 없는 `400`이고 v2는 오류 화면 `400`이다(`SearchApp.java:127-131`, `SearchController.kt:66`). 조직 검색 폼은 있는 조직의 화면에만 있고 이름을 만들어 내지 않으므로 주소를 직접 입력해야 닿는다.

## 위키 대조 장부에서 추가된 항목 (2026-10-27)

위키 줄 단위 대조에서 "직접 만든 요청에서만 닿는다"고 판정된 줄 가운데 위 표와 절에 아직 없던 것이다. 같은 원인의 줄은 한 항목으로 묶었다. v1.6 열은 `60a5ac406` 소스, v2 열은 `src/main/kotlin/dev/yona` 기준이다.

| # | 대상 | v1.6 | v2 | 직접 만든 요청이어야 하는 이유 |
|---|---|---|---|---|
| H-01 | 게시글 `editPost`의 검증 실패 재표시 | 다시 그리는 400 화면의 공지 체크박스가 제출된 폼 값(`Some("true")`)을 따른다(`app/views/board/edit.scala.html:50-64`) | 저장된 게시글 값으로 다시 그린다(`web/board/BoardWriteController.kt:149-150`) | 제목 검증은 클라이언트(`yobi.board.Write.js:75-80`)가 먼저 막는다 |
| H-02 | 커밋 코멘트 삭제 `deleteCommitComment` | `@With(DefaultProjectCheckAction)`이 먼저 프로젝트 존재와 읽기 권한을 확인한다. 없는 프로젝트는 403(익명은 로그인 화면), 읽기 권한이 없으면 읽기 거부 문구(`CodeHistoryApp.java:249-260`, `DefaultProjectCheckAction.java:37-41`) | 프로젝트 읽기 확인이 없고, 없는 프로젝트는 404, 읽기 권한을 잃은 작성자는 DELETE 거부 403 화면(`web/code/ReviewCommentController.kt:101-114`) | 삭제 단추가 보이는 사용자만 정상 호출하므로 DELETE 요청을 직접 보내야 한다 |
| H-03 | WebDAV 메서드의 경로 | `Global.onRouteRequest`가 경로와 무관하게 `SvnApp.service()`로 보낸다(`app/Global.java:369-378`) | `/svn/`로 시작하는 주소만 가로챈다. 그 밖은 일반 404나 405(`web/svn/SvnDavFilter.kt:21-22`) | svn 클라이언트는 항상 `/svn/` 아래만 쓴다 |
| H-04 | `/user/issues?milestoneId=` | 마일스톤 진행률 카드를 폼 아래에 낸다(`app/views/issue/my_partial_search.scala.html:48-55`) | 필터는 적용하지만(`domain/issue/IssueSearchService.kt:240`) 카드 블록이 없다(`templates/issue/my-partial-search.html`) | 어떤 화면도 이 주소를 만들지 않는다(마일스톤 화면은 프로젝트 이슈 목록으로 간다) |
| H-05 | Git 클라이언트의 사전 릴리스 버전(`2.1.0-rc1` 등) | 권한 거부 응답에 charset을 붙이지 않는다. 사전 릴리스는 `2.1.0`보다 작다(`GitApp.java:76-86`) | 주 버전과 부 버전만 비교해(`major == 2 && minor >= 1`) charset을 붙인다(`web/git/GitHttpController.kt:142-149`) | 사전 릴리스 Git 클라이언트로만 닿는다 |
| H-06 | SVN DAV 서블릿 초기화 값 | `SVNParentPath` 외의 값은 설정 파일의 `application.<이름>`에서 읽는다(`RepositoryService.java:145-177`). 예를 들어 `application.SVNAutoversioning`을 넣으면 반영된다 | `SVNParentPath`만 값을 주고 나머지는 `null`이다(`web/svn/JavaxServletBridge.kt:142-151`) | 기본 설정 파일과 v1.6 소스 어디에도 이 키가 없어 운영자가 문서에 없는 키를 직접 넣어야 닿는다 |
| H-07 | 커밋의 `encoding` 헤더와 작성자 줄 | 지원하지 않는 문자셋 이름이면 JVM 기본 문자셋으로 폴백하고, 형식이 잘못된 이름은 예외(`playRepository/GitCommit.java:247-253`, `276-314`) | JGit `RevCommit`과 `RawParseUtils`가 UTF-8로 폴백한다(`repository/CommitHistoryService.kt:92-96`) | 직접 만든 커밋 객체로만 닿는다. JVM 기본 문자셋이 UTF-8이면 같다 |
| H-08 | 첨부 올리기 `POST /files`의 업로더 | 폼의 `authorEmail`, 없으면 `authorLoginId`로 찾은 사용자를 로그인 여부와 무관하게 업로더로 삼는다(`AttachmentApp.java:43-59`) | 두 필드를 받지 않는다(`web/AttachmentController.kt` KDoc, [A-31](../decisions.md)) | 직접 만든 멀티파트 요청으로만 닿는다 |
| H-09 | 풀 리퀘스트 만들기의 빈 제목, 브랜치 | `validateForm`이 flash 경고를 남기고 같은 폼을 다시 그린다(`PullRequestApp.java:270-279,318-323`, `ValidationUtils.java:27-31`) | `error.validation` 400 화면(`web/pullrequest/PullRequestCreateController.kt:162-166`, [A-75](../decisions.md)) | 화면의 `yobi.git.Write.js:233-260`이 빈 값 제출을 막는다 |
| H-10 | JSON 본문 `attachUploadFilesToPost`의 일부 실패 | 옮긴 수가 요청 수와 다르면 flash 두 개를 남겨 응답에 `PLAY_FLASH` 쿠키가 실린다(`AbstractPostingApp.java:233-248`) | flash를 남기지 않는다(`web/PostAttachments.kt:46-52`) | 없거나 남의 임시 첨부 id를 직접 보낼 때만 닿는다. 응답 본문은 같다 |
| H-11 | 조직 풀 리퀘스트 목록의 `category` 쿼리 | `open`이 아닌 값은 닫힘 조건으로 검색하되 두 탭이 모두 비활성이다(`OrganizationApp.java:62-69`, `group_pullrequest_list.scala.html:46,52`) | `open`이 아닌 모든 값에서 닫힘 탭이 활성이다(`web/organization/OrganizationListController.kt:89`, `templates/organization/pullrequest-list.html:30,33`) | 화면의 탭 링크는 `category` 쿼리를 만들지 않는다 |
| H-12 | 서브버전 변경 비교 화면의 `?branch=` | 드롭다운 라벨에 `selectedBranch`를 찍는다(`app/views/code/svnDiff.scala.html:39`) | 항상 `HEAD`를 찍는다(`templates/code/svn-diff.html:20`) | SVN의 브랜치는 `HEAD` 하나뿐이라 정상 링크는 `HEAD`만 보낸다 |
| H-13 | 알 수 없거나 지원하지 않는 리소스 종류 | `ResourceType.getValue`와 `Resource.get`, `exists`가 `IllegalArgumentException`을 던지고 `COMMIT`은 저장소에서 커밋 존재까지 확인한다(`ResourceType.java:62-98`, `models/resource/Resource.java:80-108`) | 예외 없이 `null`이나 400으로 처리하고(`web/WatchController.kt:71`, `web/code/ReviewCommentController.kt:148`), 조회에서 `LABEL`, `ATTACHMENT`, `WEBHOOK`, `ISSUE_ASSIGNEE`는 지원하지 않으며 `exists(COMMIT)`는 프로젝트 존재만 본다(`access/ResourceLookup.kt:40-63,93-96`) | `unwatch`처럼 종류를 직접 쓴 요청이나 저장소에 없는 커밋 이벤트에서만 닿는다. P-10과 같은 부류이다 |
| H-14 | `deleteWebhook`의 다른 프로젝트 웹훅 id | URL의 프로젝트와 무관하게 id로 지운다(`ProjectApp.java:1281-1322`) | 그 프로젝트의 웹훅만 지우고 아니면 404(`domain/webhook/WebhookService.kt:48-54`, [A-82](../decisions.md)) | 화면은 그 프로젝트의 웹훅만 보여 준다 |
| H-15 | `notificationMail` 값 | 대소문자 무관하게 `yes`이면 참(`AbstractPostingApp.java:274-286`) | 정확히 `yes`만 참(`web/issue/IssueEditController.kt:122`, `web/issue/IssueCommentController.kt:71`, `web/board/BoardCommentController.kt:70`) | 체크박스 값이 소문자 `yes`라 `YES`를 직접 보낼 때만 다르다 |
| H-16 | 이슈 댓글 수정에서 권한 없는 사용자가 남의 댓글 id를 보낸 경우 | 수정 권한이 없으면 작성자를 현재 사용자로 바꿔 저장하므로 내용과 작성자가 덮어써진다(`IssueApp.java:1069-1093`, `AbstractPostingApp.java:53-66`) | 403(`web/issue/IssueCommentController.kt:68-71`) | 수정 단추는 권한 있는 댓글에만 그려진다(A-24) |
| H-17 | 없는 커밋이나 풀 리퀘스트로 댓글을 보낸 경우의 없음 화면 | `notfound.render("error.notfound", project, request().path())`라 메뉴, 돌아가기, 키 문자열이 다르다(`CodeHistoryApp.java:166-168,207-209`, `PullRequestApp.java:608-610`) | `targetType`을 `null`로 넘겨 일반 문구를 그린다(`web/code/ReviewCommentController.kt:76`, `web/code/CommitViewController.kt:98,101`) | 화면은 있는 커밋과 풀 리퀘스트에만 댓글 폼을 그린다 |
| H-18 | 이슈 즐겨찾기 토글의 없는 이슈 id | 이슈가 `null`인 행을 저장하고 `favored:true`를 돌려줄 수 있다(`UserApi.java:91-108`, `models/FavoriteIssue.java:32-35`) | 400 `Wrong issue id`(`web/api/FavoriteApiController.kt:42`, `domain/misc/FavoriteService.kt:33-38`) | 화면은 있는 이슈의 별만 누른다. D-7은 프로젝트와 조직만 적어 이슈를 여기서 보탠다 |
| H-19 | 같은 사용자와 같은 대상의 즐겨찾기 행이 둘 이상 있는 경우 | 끌 때 `removeFavoriteProject`, `removeFavoriteOrganization`, `removeFavoriteIssue`가 첫 행만 지운다(`User.java:1007-1016,1043-1052,1087-1096`) | 일치하는 행을 모두 지운다(`domain/misc/FavoriteService.kt:34-35,48-52,66-67`) | 중복 행은 동시 요청 경쟁으로만 생기고 화면 조작으로는 만들 수 없다 |
| H-20 | Basic 인증 헤더의 잘못된 Base64 문자 | commons-codec `decodeBase64`가 잘못된 문자를 무시하고 해석한다(`utils/BasicAuthAction.java:64`) | `java.util.Base64` 엄격 디코더라 400(`web/access/BasicCredentials.kt:28`) | git, svn 클라이언트는 올바른 Base64만 보낸다 |
| H-21 | `allowsAnonymousAccess=false` 사이트에서 익명이 `/user/sidebar`를 직접 연 경우 | `@AnonymousCheck`로 로그인 폼으로 보낸다(`Application.java:55-58`) | 애너테이션이 없어 프레임 화면을 보여 준다(`web/user/UserMenuController.kt:30-42`) | `layout.html`은 로그인 사용자만 이 주소로 보낸다 |
| H-22 | 폼 로그인의 `redirectUrl` 위치 | 요청 본문에서만 읽는다(`UserApp.java:331-346`) | `getParameter`라 쿼리 문자열의 값도 읽는다(`web/user/LoginController.kt:95,100-117`) | 로그인 화면은 본문으로 보낸다 |
| H-23 | `cleanEnrolledUsers`의 사이트 관리자 역할 행 | `members()`는 `SITEMANAGER` 행을 제외한다(`Project.java:641-656`) | `SITEMANAGER` 행까지 구성원으로 본다(`domain/project/ProjectMemberService.kt:59-67`) | `SITEMANAGER` 행은 기본 관리자에게만 있어([R-82](../refactoring-backlog.md)) 그 계정이 가입 신청 중인 특수 데이터에서만 다르다 |
| H-24 | 비XHR `GET /user/issues?format=json` | 라우트 124번 줄이 먼저 매칭되어 `format="html"`로 고정이다(`conf/routes:124-125`, `IssueApp.java:69-72`) | `format` 쿼리를 읽어 JSON을 돌려준다(`web/issue/MyIssuesController.kt:46-58`) | 화면과 XHR은 XHR 여부로 형식이 정해져 같다 |
| H-25 | 게시글을 이슈로 옮길 때 3단 중첩 댓글 | 부모가 최상위 맵에 없으면 `Long.valueOf(null)`이 `NumberFormatException`을 던져 400이 되고 트랜잭션이 커밋되어 일부만 저장될 수 있다(`IssueApi.java:122-160`) | `idMap.getValue`가 `NoSuchElementException`을 던져 전체가 롤백되고 500(`domain/issue/ExternalIssueService.kt:168-171`) | 화면은 3단 댓글을 만들지 않는다 |
| H-26 | 이슈 공유자 API의 없는 `loginId`와 지원하지 않는 `action` | 없는 `loginId`는 `User.findByLoginId`가 익명을 돌려줘 검증이 동작하지 않고, 지원하지 않는 `action`에도 알림 단계가 실행된다(`IssueSharer.java:46-50`, `IssueApi.java:1223-1286`) | 없는 사용자와 지원하지 않는 `action`은 400으로 먼저 거절한다(`domain/issue/IssueSharerService.kt:37`, `web/api/IssueApiController.kt:195-201`, [A-47](../decisions.md)) | 화면은 있는 사용자와 정해진 `action`만 보낸다 |
| H-27 | API 경로의 `PathVariable` 해석 | 없는 키는 `null`이고 API 접두를 `startsWith`로 판정하며 빈 구간을 쪼갠 그대로 쓴다(`utils/PathVariable.java:16-55`) | 없는 키는 빈 문자열이고 접두를 `contains`로 판정하며 빈 구간을 모두 버린다(`web/access/ProjectCheckStage.kt:171-178`) | `owners`, `projects` 쌍이 없거나 이중 슬래시가 있는 직접 만든 URL에서만 달라진다 |
| H-28 | 프로젝트 가져오기(`/_import`)의 조직 소유자 | 조직 관리자가 아니어도 이름 중복이나 URL 비어 있음이 겹치면 마지막 오류인 400 폼이 남는다(`ImportApp.java:52-130`) | 조직 관리자가 아니면 403(`domain/project/ProjectImportService.kt:82-83`, `web/project/ImportController.kt:68`) | 조직 관리자 목록에만 조직이 보여 직접 만든 요청에서만 닿는다 |
| H-29 | 풀 리퀘스트 이벤트 목록에서 사용자 행이 없는 `loginId` | `User.findByLoginId`가 `NullUser`(loginId `""`)를 돌려줘 문자열 분기를 타고 "존재하지 않는 사용자" 이름으로 링크된다(`partial_pull_request_event.scala.html:36-46`, `User.java:283-291`) | 사용자 행이 없으면 리터럴 `Anonymous`(`web/pullrequest/PullRequestPageAssembler.kt:240-241`) | 탈퇴는 `DELETED` 상태 변경이라 사용자 행이 지워지는 정상 경로가 없어 DB를 직접 고친 데이터에서만 닿는다 |
| H-30 | 포크 만들기 폼에 `projectScope`가 없는 요청 | v1.6 `PullRequestApp.fork`는 303으로 되돌린다 | v2 `ForkController.bind`가 500이다. 화면의 폼은 PUBLIC을 기본으로 골라 항상 값을 보낸다 |
| H-31 | 커밋 댓글 수정 요청(`POST .../comment/:commentId`, `.../comments/:commentId`) | v1.6 `updateComment`는 `newComment`를 일반 메서드로 불러 그 메서드의 `@IsAllowed`와 프로젝트 검사가 실행되지 않고 클래스 로그인 검사만 걸린다 | v2는 한 핸들러로 합쳐 두 경로에도 권한 검사가 붙는다. 이 POST를 쓰는 화면은 없다 |
| H-32 | 읽을 수 없는 프로젝트의 멘션 후보 목록 `GET .../mentionList` | v1.6은 `@AnonymousCheck`만 있어 후보를 돌려준다 | v2는 `@IsAllowed(READ)`가 더 붙어 403이다(v2 쪽 보강이며 유지) |
| H-33 | 비공개 프로젝트 글의 구독자 목록 `GET .../posts/:number/watchers` | v1.6은 권한 검사가 없어 200이다 | v2는 `@IsAllowed(READ)`가 더 붙어 403이다(v2 쪽 보강이며 유지) |
| H-34 | 프로젝트 구성원 추가 `POST .../members`의 검사 순서 | `@DefaultProjectCheck` 다음 `@IsAllowed(UPDATE)` | 반대 순서이나 둘 다 같은 프로젝트 읽기 단계를 먼저 거쳐 응답이 같다 |
| H-35 | 권한 검색 서비스를 읽기 권한 없는 사용자로 직접 호출 | v1.6 모델은 작성자 조건만 붙여 0건 또는 자기 글만 돌려준다 | v2 서비스는 권한 검사를 하지 않고 라우트의 `@IsAllowed(READ)`가 막는다. 요청으로는 어느 쪽도 이 분기에 닿지 않는다 |
| H-36 | 이슈 수정 폼에 `state`를 보내지 않거나 초안으로 저장 | v1.6은 폼 바인딩 객체의 기본값 `OPEN`이 저장될 수 있다(초안인데 `OPEN`). 실제 값은 v1.6을 실행하지 못해 확정하지 못했다 | v2는 초안이면 `DRAFT`, 상태를 보내지 않으면 현재 상태를 유지한다(A-45 유지) |
| H-37 | 포크 폼 열기(`newFork`)와 `fork` 제출 | v1.6 `Project.copy`가 소유 프로젝트가 없는 메뉴 설정 행을 저장할 수 있다(실제 DB 영향은 실행 없이 확정하지 못함) | v2는 행을 만들지 않는다(A-73 유지) |
| H-38 | 리뷰 댓글과 커밋 댓글을 지울 때의 구독 기록 | v1.6은 `ResourcePersistAdapter`가 해당 댓글 리소스의 `watch`, `unwatch` 행을 지운다. 화면에는 이 리소스를 구독하는 버튼이 없어 직접 만든 요청으로만 행이 생긴다 | v2는 지우지 않는다 |
| H-39 | 이슈 이동, 삭제, 댓글 번호 덮어쓰기에서 권한 검사 전에 일어나는 v1.6의 부수 효과 | v1.6은 이동 알림, 삭제 알림을 먼저 보낸 뒤 403이나 덮어쓰기를 한다 | v2는 권한을 먼저 확인해 아무것도 남기지 않는다(오류 쪽 방어이며 유지) |

## 재현하지 않고 기록만 하는 실패 경로

- (2026-10-26 선생님 결정 P-09로 이 절의 R-74, R-76, R-82는 v1.6 상태를 재현한다. 더 이상 "재현하지 않는 항목"이 아니다.) [R-74](../refactoring-backlog.md) `changeVCS` 실패 시 중간 상태, [R-76](../refactoring-backlog.md) `accept`의 병합 중 표시 고착은 v2도 같다.
- [R-73](../refactoring-backlog.md) `editIssue`, `deleteIssue`: 권한 확인 전에 먼저 반영하거나 발송하는 v1.6 결함을 v2는 고쳐 두었다(P-07).
