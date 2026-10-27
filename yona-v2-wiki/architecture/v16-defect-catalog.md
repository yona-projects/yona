---
title: v1.6 소스 결함 후보 전체 목록 (5차 검증)
type: architecture
updated: 2026-10-26
---

# v1.6 소스 결함 후보 전체 목록 (5차 검증)

v1.6 위키 5차 재검증(2026-10-26)에서 모인 결함 후보 189건을 항목마다 v1.6 소스 줄을 다시 읽어 판정하고 v2와 대조한 기록이다. v2는 v1.6의 결함을 같게 재현하는 것이 원칙이고(P-01), 선생님 지시(2026-10-26)에 따라 v1.6이 오류(500, NPE)를 내는 경우는 v2가 오류 없이 처리하는 쪽을 유지한다. 요청을 손으로 만들어야만 닿는 항목은 A-24에 따라 재현하지 않는다([hand-crafted-request-differences.md](hand-crafted-request-differences.md)).

- **기록**: 이 목록 밖에 이미 R-nn, A-nn, 문서로 남아 있으면 그 위치. `없음`이면 이 목록이 유일한 기록이다.
- **문서화 표시 `★`**: 5차 검증 시점에 기록이 없던 실제 결함이다. 이 표가 그 기록이다. 선생님이 개선을 승인하기 전까지는 v2에서도 재현한다([refactoring-backlog.md](../refactoring-backlog.md)의 규칙).
- 번호 `C###`는 에이전트 작업 중의 일련번호이며 이 문서의 참조 키이다. 줄 번호는 v1.6 커밋 `60a5ac406` 기준이다.

합계 189건, 그중 ★(새로 문서화) 105건.

## 보안 (21건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C013★ | 기본 로그인 페이지의 `path`를 검증 없이 저장하고 `index()`가 그대로 `redirect`. 토큰이 없는 POST라 외부 요청으로 설정 가능(열린 리다이렉트) | UserApp.java:1373-1381, Application.java:49-50 | 실제 | 없음(R-07은 비로그인 500, R-02는 CSRF 일반, R-06은 로그인 redirectUrl) | 재현 | HomeController.kt:57-65 저장, :40-42 `RedirectSupport.see(...)` 303, SecurityConfig CSRF off(R-02) | 희귀(화면은 현재 경로만 보냄. 직접 만든 요청이나 CSRF) |
| C027 | 프로젝트 라벨 `'<span ...>' + sName + ' </span>'`에 입력값 이스케이프가 없음(자기 입력 XSS) | yobi.site.MassMail.js:155 | 실제 | R-54(4) "라벨에는 이스케이프가 없다" | 재현 | 동일 파일 | 희귀(사이트 관리자 본인 입력) |
| C056★ | 댓글 수정이 `isModifiedByOthers`를 권한 검사보다 먼저 해, 로그인만 하면 틀린 `original`로 409 응답에서 본문을 읽음 | IssueApi.java:615-621 | 실제 | 동작만 `web/issue-api.md:24`("충돌 검사가 권한 검사보다 먼저이다"). 결함/보안 표시와 R-nn 없음 | 재현 | `web/api/IssueApiController.kt:309-310`(`modifiedByOthers` 후 `isAllowed`) | 희귀(직접 만든 요청) |
| C057★ | `imports`(글을 이슈로 옮기고 원본 삭제), `updateIssueLabel`, `updatePostLabel`에 권한 검사가 없고 프로젝트가 없으면 NPE | IssueApi.java:58-89, 163-184; BoardApi.java:42-63 | 실제 | `updatePostLabel`만 A-51(+`web/board.md:57`). `imports`, `updateIssueLabel`은 `web/external-issue-api.md:19-20`에 권한 요구만 적혀 있고 v1.6과 다르다는 결정/표는 없음 | 해결 | `web/api/ExternalIssueApiController.kt:191`(UPDATE), `:210`(글 삭제+이슈 만들기), `web/api/BoardApiController.kt:55-61`(UPDATE), 없는 프로젝트는 404 | 희귀(직접 만든 요청, 정상 화면은 권한 있는 사용자에게만 노출) |
| C063 | `WatcherApi.getWatchers`에 인증/권한 검사가 없고 프로젝트/글이 없으면 NPE | WatcherApi.java:26-62 | 실제 | A-65 (+`web/external-project-api.md:19`) | 해결 | 읽기 권한 요구와 404 (A-65, decisions.md:403) | 희귀(외부 API, 화면은 안 부름) |
| C068★ | OAuth 액세스 토큰을 `Logger.error("token=" + accessToken)`으로 로그에 남김 | MigrationApp.java:88 | 실제 | 없음 | 해결 | GithubTokenClient.kt:21-32, web/migration 및 domain/migration 어디에도 로그 출력 없음 | 정상(이전 화면에서 토큰을 교환할 때마다) |
| C069 | deleteWebhook이 URL 프로젝트와 무관하게 id만으로 삭제 | ProjectApp.java:1316-1318 | 실제 | A-82 | 해결 | WebhookController.kt:delete -> WebhookService.kt:50-51 `findByIdAndProjectId` | 희귀 |
| C091 | MarkdownApp.render가 본문/프로젝트 null 검사와 프로젝트 READ 권한 검사가 없음 | MarkdownApp.java:30-37 | 실제 | R-80(권한), S-1(본문 없음) | 재현(권한 검사 없음) / 해결(NPE) | MarkdownController.kt:29-38 (권한 검사 없음, 프로젝트가 없으면 일반 렌더러, 본문 없으면 빈 문자열) | 희귀(미리보기 요청을 직접 만들 때). 권한 부분은 정상 화면에서도 호출은 가능 |
| C092 | 번역 API가 댓글을 id만으로 조회해 비공개 프로젝트 내용이 노출 | api/IssueApi.java:1003-1008 | 실제 | R-80 | 재현 | TranslationApiController.kt:56-57 (`findById(number)`로 프로젝트 확인·권한 검사 없음) | 희귀 |
| C093★ | `updateIssueComment`의 충돌 검사(원문 반환)가 UPDATE 권한 검사보다 먼저라 권한 없는 사용자도 원문을 받음 | api/IssueApi.java:615-621 | 실제 | 없음 | 재현 | IssueApiController.kt:309-310 (충돌 검사 후 권한 검사) | 희귀 |
| C095 | `getUserExpressionList`의 `type`이 임의 컬럼(`email`, `token` 등)으로 정확 일치 조회 | api/IssueApi.java:828-833 | 실제 | S-7 | 해결 | S-7이 `loginId/name/email/englishName`만 허용한다고 기록(코드 줄은 이번에 열람하지 않음, 문서 근거) | 희귀 |
| C105 | MigrationApp 데이터 내보내기에 프로젝트 접근 검사가 없음 | MigrationApp.java:121-238 | 실제 | decisions.md D-23, A-94(3) | 해결 | MigrationController.kt:94 `migration.canExport(...)` 아니면 403 | 정상(로그인 사용자의 임의 프로젝트 조회) |
| C106 | OAuth 토큰을 error 로그에 남김 | MigrationApp.java:88 | 실제 | decisions.md A-94(1) | 해결 | domain/migration과 web/migration에 로그 출력이 없음(grep 없음) | 정상(로그 열람 경로) |
| C117 | `mentionList`에 `@IsAllowed`가 없어 비공개 프로젝트 구성원 목록이 노출 | ProjectApp.java:385-420 | 실제 | decisions.md A-35, P-10, M-14 | 해결 | A-35에 따라 읽기 권한 요구 | 희귀(직접 호출) |
| C118★ | `deleteWebhook`, `deletePushedBranch`가 id만 보고 지워 다른 프로젝트 것도 삭제 | ProjectApp.java:1316-1318, 1329-1333 | 실제 | deleteWebhook만 A-82. deletePushedBranch는 기록 없음 | 해결(둘 다) | PullRequestListController.kt:149-153 `takeIf { it.project?.id == project?.id }` | 희귀(직접 만든 요청) |
| C135 | 내 이슈 목록에서 assigneeId=-1이면 프로젝트 가시성 필터 없이 전체 프로젝트의 미배정 이슈가 나옴 | SearchCondition.java:202-206,229-253, IssueApp.java:75-83 | 실제 | BK R-49(1)이 "내 이슈 검색은 읽기 권한을 따지지 않는다"로 포괄(-1 사례 명시는 없음) | 재현 | IssueSearchService.kt:217 `i.assignee is null`에 프로젝트 제한 없음(project==null), MyIssuesController.kt:60 | 희귀(직접 쿼리) |
| C145 | editIssue가 이슈 이동을 UPDATE 권한 검사(editPosting)보다 먼저 반영 | IssueApp.java:761-838 | 실제 | BK R-73, DC:306, HC:53(P-07) | 해결 | R-73 본문(v2는 권한을 먼저 확인) | 희귀(권한 없는 사용자의 직접 요청) |
| C147★ | newIssueFormByComment가 댓글의 프로젝트 읽기 권한을 검사하지 않아 다른(비공개) 프로젝트 댓글 본문이 폼에 실림 | IssueApp.java:374-387 | 실제 | 없음(HC I-08은 없는 id만, BK R-80은 다른 경로) | 재현 | IssueCreateController.kt:181-190 권한 검사 없음 | 희귀(타 프로젝트 댓글 id를 직접 지정) |
| C155 | deleteComment가 commitId/경로 프로젝트와 댓글의 일치를 검사하지 않음(번호만으로 조회). 후보가 말한 "없는 id면 NPE"는 결함 아님: IsAllowedAction이 null이면 404를 돌려줌 | CodeHistoryApp.java:250-260, Resource.java:240-241, IsAllowedAction.java:53-60 | 일치 미검사는 실제, NPE는 아님 | BK R-12 | 재현(일치 미검사)/없는 id는 404로 v1.6과 같음 | ReviewCommentController.kt:101-114 | 희귀 |
| C169 | `GET /user/editform/token_reset`이 토큰을 재생성하고 CSRF 방어가 없음 | UserApp.java:897-904, conf/routes:136 | 실제 | R-79(`refactoring-backlog.md:86`), R-02 | 재현 | `AccountController.kt:77,86-87` (GET과 POST 모두 `recreate`) | 정상(화면의 다시 만들기 링크) |
| C173 | `searchInAGroup`의 `!isMemberOf \ | \ | !isAdminOf`라 `HIDE_PROJECT_LISTING`이 켜지면 사실상 항상 400 | SearchApp.java:127-131 | 실제 | R-83(`refactoring-backlog.md:90`) | 재현 |

## 데이터 (12건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C017★ | `CommentThread.count`가 `thread.pullRequest != pullRequest` 참조 비교이고, `codeRange.path`가 null이면 NPE | CommentThread.java:183, 188, 210 | 실제(코드상). 단 호출처는 `count(null, ...)` 하나뿐(history.scala.html:147)이라 참조 비교는 실행되지 않고, `path`는 필수 컬럼이라 NPE도 정상 데이터에서는 나지 않음 | 없음(R-41은 `countOnCommit`의 `"/"+path`) | 해결 | ReviewThreadService.kt:25-27 `it.path == path` null 안전 | 희귀(도달 불가에 가까움) |
| C019 | `Comment.updateMention`이 `contents == null`이면 NPE(AbstractPosting과 달리 가드 없음) | Comment.java:90-92, 비교 AbstractPosting.java:273-277 | 실제 | M-8b (hand-crafted-request-differences.md) | 해결 | M-8b "본문이 null이면 건너뜀" | 희귀(댓글 본문은 필수) |
| C075★ | `PostingComment.findAllBy`가 `id = posting.id`로 조회 | PostingComment.java:106-110 | 실제(호출처 없음, 죽은 코드) | 없음 | 해당 없음 | v2에 `findAllBy` 없음 | 희귀(호출처 없음) |
| C079★ | `addEOL("")`이 `"\n"`을 붙여 빈 내용이 개행 한 줄 파일이 됨 | LineEnding.java:43-54 | 실제 | 없음(R-27은 DOS 변환만) | 재현 | LineEnding.kt:37-41 (`""`는 UNDEFINED -> UNIX, endsWith 아님 -> `"\n"`), OnlineCommit.kt:46 | 정상(온라인 편집에서 내용을 비울 때) |
| C081★ | `PullRequest.save()`가 호출자의 number를 무시하고 최댓값+1을 매겨 동시 생성에 약함 | PullRequest.java:687-704 | 실제 | 없음 | 재현 | PullRequestService.kt:57-65(`nextNumber` 최댓값+1), :86 | 희귀(동시 생성) |
| C082★ | `Comment.update()`는 부모를 재저장하지 않음(`save/delete`는 함) | Comment.java:77-99 | 실제 | 없음 | 재현 | IssueCommentService.kt:57-65 `update`는 댓글 수를 다시 세지 않고(`syncCommentCount`는 `replaceContents`/`delete`만) 갱신일만 건드림 | 정상(댓글 수는 수정으로 바뀌지 않아 영향 거의 없음) |
| C083★ | `AbstractPosting.update()`가 모든 예외를 로그만 남기고 삼킨 뒤 멘션만 갱신 | AbstractPosting.java:149-160 | 실제 | 없음 | 해당 없음 | v2는 예외를 삼키는 경로가 없고 `@Version`도 없어 충돌 자체가 발생하지 않음(PostingService.kt:73-80). 위키에 PersistenceException 삼킴 서술 없음 | 희귀 |
| C111★ | `sortListByAddedDate`가 `created`가 null이면 NPE | IssueApi.java:1104-1110 | 실제(`createSharer`는 항상 created를 채우므로 created가 null인 옛 데이터에서만) | 없음 | 해결 | IssueApiController.kt:154 + IssueRepositories.kt:40 DB 정렬(`OrderByCreatedAscIdAsc`) | 희귀(데이터 의존) |
| C129 | 10.sql이 USER/USER_AVATAR 첨부의 owner_login_id를 첨부 id=사용자 id로 비교해 채움(container_id여야 함) | conf/evolutions/default/10.sql:24-26 | 실제 | BK R-81 | 해당 없음 | v2는 evolutions를 실행하지 않음(R-81 본문) | 희귀(기존 DB 업그레이드 시 1회) |
| C130 | 외부 API 프로젝트 생성에서 SITEMANAGER 역할 행이 Project.create 안과 밖에서 2번 INSERT됨(findByIds가 SITEMANAGER 제외) | ProjectApi.java:152, Project.java:146-147, ProjectUser.java:70-78,94 | 실제 | BK R-82(본문이 낡음), DC:317, log.md:407 | 재현 | ExternalProjectService.kt:56-58 `repeat(2)`(작업 트리 미커밋 변경). R-82 본문은 아직 "재현하지 않음(확인 대기), v2는 1행"이라고 적혀 있어 DC:317과 어긋남 | 희귀(외부 REST 프로젝트 생성) |
| C162 | 임시 업로드 정리가 `ge("createdDate", 기준)`이라 오래된 것이 아니라 최근 USER 첨부를 지움 | Attachment.java:457-461 | 실제 | R-20(`refactoring-backlog.md:26`), `web/account.md:58` | 재현 | `TemporaryFileCleaner.kt:48` (`startOfDay(now) - keepUp` 이후 생성분 삭제) | 정상(주기 작업) |
| C172 | import에서 테이블 이름 일치 시 TRUNCATE 먼저 하고, 값이 배열이 아니면 건너뜀(테이블만 비워짐) | DefaultExchanger.java:251-260 | 실제 | R-84(`refactoring-backlog.md:91`), A-92 | 해결 | `DataService.kt:132-133`이 배열일 때만 테이블을 비우고 적재, 아니면 `skipChildren` | 희귀(관리자 작업, 손상된 파일) |

## 서버 (92건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C001 | LDAP 바인드는 성공했지만 검색 결과가 없거나 둘 이상이면 `authenticate`가 null이고 `ldapUser.getEmail()`에서 NPE(500) | UserApp.java:1159-1162 | 실제 | R-60(4) | 재현(예외 유지) | LdapAuthService.kt:58 `?: error(...)`(IllegalStateException), KDoc :53-54. 2026-10-26 지시("v1.6의 오류는 오류 없이 처리")와 어긋날 수 있어 선생님 확인 필요 | 희귀(LDAP 서버/디렉터리 이상) |
| C014 | 코드 브라우저 캐시 키가 `lastUpdateDate`(= HEAD 최신 커밋 시각)에만 의존해 HEAD가 아닌 브랜치 푸시 때 낡은 목록 | CodeApp.java:102, Project.java:304-328 | 실제 | A-67 | 해결(캐시 없음) | CodeBrowserService.kt가 매번 읽음. A-67 "캐시하지 않는다" | 정상(해당 브랜치를 보고 있을 때) |
| C015 | 프로젝트 개명 때 `refreshProjectMap()`이 `update()`보다 앞이라 DB에 옛 이름이 남은 시점에 점검해 옛 키가 남음 | ProjectApp.java:268-271, CacheStore.java:39-48 | 실제 | A-07, project-service.md:21("갱신을 빠뜨리기 쉬웠다") | 해결(캐시 미이식) | ProjectService.kt:20 KDoc, A-07 | 정상(프로젝트 이름 변경 시. 옛 이름 URL이 캐시로 바로 풀림) |
| C016 | `UserApp.leave`가 `deleteMember`의 403을 버리고 항상 프로필로 redirect하며 직접 호출이라 `@With(DefaultProjectCheckAction)`도 적용 안 됨 | UserApp.java:993-996, ProjectApp.java:1006-1017 | 실제 | R-48(2)(403 무시). `@With` 미적용 부분은 미기록 | 재현 | UserLeaveController.kt:30-45(소유자는 제거 안 하고 같은 곳으로 이동) | 정상(소유자가 탈퇴 링크 클릭) |
| C018★ | `CodeRange.isFor`가 `diff.pathB`/`diff.pathA`가 null일 때 NPE. `FileDiff.pathA`는 추가 파일에서, `pathB`는 삭제 파일에서 null(GitRepository.java:1642-1667)이므로 같은 커밋에 삭제 파일이 있으면 B쪽 코멘트 알림 본문 만들기가 순회 중 NPE | CodeRange.java:39-47, 호출 NotificationEvent.java:305 | 실제 | 없음(R-44(1)은 차이 조각 없음만) | 해결 | NotificationMessages.kt:108-112 `file.pathB == thread.path` 널 안전 비교 | 정상(삭제 또는 추가된 파일이 함께 든 커밋/PR에 코드 리뷰 댓글을 단 경우) |
| C030★ | 조직용 `inProjectsTemplate`이 `projectAndKeyword.endJunction()` 뒤에 `junction.endJunction()`을 한 번 더 호출(호출자도 호출). 동작 영향은 확인 못 함(Ebean 구현 의존) | Search.java:792-793 | 실제(중복 호출 확인). 영향은 미확인 | 없음(v1.6 위키에만 기록) | 해당 없음 | v2 검색은 Ebean `Junction`이 없는 JPA 질의 구조 | 정상(조직 검색)이나 영향 미확인 |
| C031★ | `findIndexes`가 빈 키워드에서 `index + 0`으로 무한 루프 | SearchResult.java:92-96 (`makeSnippets` :54-) | 실제 | 없음 | 재현(루프 코드 동일, 사전 차단도 동일) | SearchSnippets.kt:28-31 동일 구조. SearchController.kt:50, :63, :85가 빈 키워드를 400으로 막음 | 희귀(빈 문자열은 차단됨. 공백만 있는 키워드는 비어 있지 않아 무한 루프가 아님) |
| C033★ | 최초 기동 폼 검증이 `loginId`/`password` 필드가 없으면 `.equals`에서 NPE | Global.java:344,352 | 실제 | 없음(`web/welcome.md:19`는 검증 항목만 적고 NPE는 안 적음. R-57은 중복 오류만) | 해결 | `web/welcome/SiteSecretInterceptor.kt:46,49-50` null 안전 비교, 오류 화면 | 희귀(폼은 항상 필드를 보냄) |
| C034★ | `admin` 사용자가 없으면 `updateDefaultSiteAdmin`에서 NPE | Global.java:301 -> SiteAdmin.java:45-46 | 실제 | 없음 | 재현(예외 종류만 다름) | `security/SiteSecret.kt:63` `requireNotNull(...){"기본 관리자 계정이 없습니다."}`가 `IllegalArgumentException`(500) | 희귀(첫 기동 화면에서 admin 행이 없을 때만) |
| C036★ | `Accept` 헤더가 없으면 `getHeader("Accept").contains`에서 NPE | BoardApp.java:294 | 실제 | 없음 | 해결 | `web/board/BoardViewController.kt:56` `.orEmpty().contains` | 희귀(브라우저는 항상 Accept를 보냄) |
| C037 | `post.issueTemplate`이 null이면 `.equals("true")`에서 NPE | BoardApp.java:242 | 실제 | A-24 문서 `architecture/hand-crafted-request-differences.md` B-03b | 해결 | `web/board/BoardWriteController.kt:104` `getParameter("issueTemplate") == "true"` | 희귀 |
| C038 | `setMergedStateOfPullRequest`가 상태를 MERGED로 바꾼 직후 `state != MERGED`를 검사해 알림/이벤트가 안 쌓임 | PullRequestActor.java:49-54, PullRequestMergeResult.java:129-133 | 실제 | R-42(1) | 재현 | `domain/pullrequest/PullRequestMergeProcessor.kt:83-88` 상태만 바꾸고 이벤트 없음(주석에 이유 기재) | 정상(푸시로 보낼 커밋이 모두 반영된 풀 리퀘스트) |
| C039★ | `afterMerge(.., State.MERGED)` 분기(관련 작성자 수신자)는 호출처가 CONFLICT/RESOLVED뿐이라 도달 불가(죽은 분기) | NotificationEvent.java:740 (호출: PullRequestActor.java:59,66) | 실제(동작 영향 없음) | 없음 | 해당 없음 | `PullRequestMergeProcessor.kt:93-105` `publishMerge`도 CONFLICT/RESOLVED뿐, MERGED 수신자 분기는 이식 안 함(동작 동일) | 도달 불가 |
| C040 | accept가 `isMerging=true`를 먼저 저장해 리뷰어 부족 400이나 병합 예외 시 표시가 남음 | PullRequestApp.java:429-438 | 실제 | R-76 (참고: `architecture/hand-crafted-request-differences.md`의 "재현하지 않고 기록만 하는 실패 경로" 두 줄은 R-74, R-76이 "재현함"으로 바뀐 것과 어긋남. 아래 메모) | 재현 | `domain/pullrequest/PullRequestWorkflow.kt:52-55`, `web/pullrequest/PullRequestActionController.kt:53-57`(트랜잭션 없음) | 정상(리뷰어 수 부족 400은 화면에서 닿음) |
| C041★ | `cap.getUser()`가 null이면 `user.loginId`에서 NPE | IssueReferredFromCommitEventActor.java:58-59 | 결함 아님 | 해당 없음 | 해당 없음 | v1.6 `UserApp.currentUser()`(UserApp.java:627-638)는 null이 아니라 익명 객체를 돌려주고 `GitApp.isAllowed`(GitApp.java:51)는 receive-pack에 UPDATE를 요구함. v2는 `repository/PostReceiveJobs.kt:97` null이면 종료 | - |
| C046★ | 일괄 수정에서 삭제 성공이 `updatedItems`에 안 잡혀, 삭제만 성공하고 일부 거부면 403 | IssueApp.java:408-417, 440-442 | 실제 | 동작만 `web/issue-mass-update.md:21-22`(R-nn 없음) | 재현 | `web/issue/IssueMassUpdateController.kt:84-94` | 정상(목록에서 여러 건 선택 삭제, 일부는 삭제 권한 없음) |
| C047 | 조직/전역 검색의 author/assignee가 요청 값을 무시하고 현재 사용자 사용 | SearchCondition.java:184-188, 202-210 | 실제 | R-49(2), R-64(2), `web/my-issues.md:21`, `web/organization-lists.md:33` | 재현 | `domain/issue/IssueSearchService.kt:211-220`(`project == null`이면 `viewerId`) | 정상 |
| C048★ | 멘션/공유/즐겨찾기 조건이 익명이면 조용히 무시되어 전체 반환 | SearchCondition.java:265,276,295 | 실제 | 문서만 `web/organization-lists.md:55`("조건을 안 건다" 한 줄). R-nn 없음 | 재현 | `domain/issue/IssueSearchService.kt:225,229,233`(`viewer != null`일 때만 조건 추가) | 희귀(익명이 조직 이슈 목록에 이 쿼리를 직접 붙일 때) |
| C049★ | 프로젝트 오버로드는 `mentionId`로 필터하지 않고 서브태스크 숨김 판정에만 사용 | SearchCondition.java:347-420 (415행) | 실제 | 없음 | 재현 | `IssueSearchService.kt:225`(`project == null`만 멘션 필터), `:246`(mentionId가 숨김 판정에만) | 희귀(프로젝트 이슈 목록에 `mentionId`를 직접 붙일 때) |
| C050★ | `hasParentIssue()`가 `this`와 무관한 쿼리 | Issue.java:733-737 | 결함 아님(`parent != null`이면 그 자신이 행이라 결과가 항상 `parent != null`과 같음. 군더더기 쿼리일 뿐) | 없음 | 해당 없음 | `domain/issue/IssueService.kt:146-148`은 `parent != null`만 검사(결과 동일) | - |
| C051★ | `eq("author_id", ...)`로 프로퍼티명이 아닌 컬럼명 사용 | Issue.java:261, IssueComment.java:176 (Posting.java:143, PostingComment.java:112도 같음. 호출: UserApi.java:275-279) | 미확인(Ebean이 컬럼명을 그대로 통과시켜 동작했을 가능성이 크나 확인 못 함) | 없음 | 해당 없음 | `web/api/ExternalUserApiController.kt:144,146`은 JPQL `authorId` 프로퍼티 사용 | 정상(통계 API 호출) |
| C052★ | `comment == null` 400 분기는 도달 불가, 행이 없으면 `Resource.get`에서 NPE(500) | CommentApp.java:40-42, Resource.java:121-160 | 실제 | 없음(`web/review-comments.md:15`는 종류 오류 400만) | 해결 | `web/code/ReviewCommentController.kt:145-146` 조회 예외를 400으로 처리(`access/ResourceLookup.kt:46-65`는 없는 행에 예외) | 희귀(없는 id를 직접 지정) |
| C054★ | SVN 오류 경로의 `notifyAll()`이 `synchronized` 밖이라 `IllegalMonitorStateException` | SvnApp.java:161 | 실제 | 없음(v1.6 위키 gap3만) | 해당 없음 | v2는 서블릿 필터 구조(`web/svn/SvnDavFilter.kt`)라 상태 잠금/`notifyAll` 코드가 없음 | 희귀(DAV 서비스 예외 시) |
| C055★ | xls 생성 오류를 삼켜 `filename`이 null인 채 `Tika.detect`/`assert`로 진행 | ReviewThreadApp.java:57-71 | 실제 | 없음 | 해결 | `web/code/ReviewListController.kt:108-112`에 try/catch 없이 예외가 그대로 전파되고 파일 이름은 항상 정해짐 | 희귀(엑셀 쓰기 IOException) |
| C058 | `isExclusive = ...isBoolean()`을 계산해 놓고 쓰지 않으며, 응답은 새 카테고리 기본값 | ProjectApi.java:483,510 | 실제 | R-37 (키가 없을 때 NPE는 REST-29) | 재현 | `web/api/ExternalProjectApiController.kt:161` 응답이 `label.category?.isExclusive` | 희귀(외부 API, 화면은 안 부름) |
| C059★ | 부모가 대댓글(최상위가 아님)이면 `commentMap.get(key)`가 null이라 `.set`에서 NPE | ProjectApi.java:416-419 | 실제 | 없음(`web/issue-api.md:28`은 `childComments` 형식만) | 해결 | `web/api/IssueJson.kt:79-83` `nodes[parentId]?.set(...)` | 희귀(대댓글의 대댓글 데이터가 있고 외부 API로 조회) |
| C060★ | 필수 키 누락 시 `findValue(..)`가 null이라 NPE 여러 곳. `BoardApi.newPostingComment`는 댓글 저장 뒤에 NPE | UserApi.java:252-253; IssueApi.java:355-367; ProjectApi.java:125; BoardApi.java:187-189 | 실제 | 이슈 쪽만 A-64. 사용자 로그인, 프로젝트 만들기(`web/external-project-api.md:25`이 A-64를 가리킴), 글 댓글 쪽은 별도 기록 없음 | 해결 | `web/api/ExternalUserApiController.kt:79-80`(`orEmpty`), `ExternalProjectApiController.kt:112`(400), `ExternalBoardApiController.kt:103`(`ids()`가 null을 빈 목록으로) | 희귀 |
| C061 | 상태 파싱이 느슨함: `findIssueState`는 대소문자 무시, `updateIssueNode`는 `State.valueOf(state)`라 소문자 `open`이면 `IllegalArgumentException`(500) | IssueApi.java:361-365, 465-475 | 실제 | A-24 문서 REST-10 | 해결 | v2는 대소문자를 무시한다(REST-10에 기재) | 희귀 |
| C062★ | 오류 문구를 번역 키가 아니라 문장(`'이름' has no permission`)으로 만들어 번역되지 않음 | ProjectApi.java:137, ImportApp.java:192, ProjectApp.java:178 | 실제 | `web/project-create.md:24`만(웹 생성). 외부 API/가져오기는 없음. R-nn 없음 | 재현 | `web/project/ProjectCreateController.kt:68`, `web/api/ExternalProjectApiController.kt:118`, `web/project/ImportController.kt:63,73` | 정상(조직 관리자가 아닌 사용자가 조직 소유로 만들 때) |
| C065 | returnToReferer가 referer가 있어도 `index().toString()`을 평가하고 기본값이 URL이 아님 | Application.java:140-142 | 실제 | U-28 (hand-crafted 문서, Referer 없음 부분만) | 해당 없음 | v2에 `returnToReferer` 자체가 없음(grep 0건). 로그아웃은 R-03/U-28이 홈으로 처리 | 희귀 |
| C066 | findAuthor에서 `email` 필드가 없으면 `findValue("email")`가 null이라 NPE | api/IssueApi.java:695-696 | 실제 | M-16, decisions.md:305 | 해결 | ExternalIssueApiController.kt:276-279, ExternalBoardApiController.kt:106-109(email 없으면 익명 -1) | 희귀 |
| C067★ | 원문 body가 null이고 새 본문이 비어 있지 않으면 `diff_main(null, ..)`(diff_match_patch.java:155 `text1.equals`)에서 NPE | AbstractPostingApp.java:109,136 | 실제 | 없음 | 해결 | PostingService.kt:73,77, IssueService.kt:237,273이 `body.orEmpty()`로 비교해 NPE 없음 | 희귀(본문이 null인 글만) |
| C070 | `_method` 파라미터가 없으면 `get("_method").toLowerCase()`에서 NPE(500) (라벨이 없을 때도 NPE) | IssueLabelApp.java:256-258,263 | 실제 | M-14 | 해결 | IssueLabelController.kt:134 (`_method`가 아니면 400) | 희귀 |
| C072★ | 검증은 `trim`한 URL, 클론은 원문(공백 포함) URL을 씀 | ImportApp.java:75,88,201 | 실제 | 없음 | 재현 | ProjectImportService.kt:86(`isNullOrBlank` 검사), :97(`form.url` 원문으로 `importRepository`), RepositoryService.kt:68-69도 trim 없음 | 정상(주소 뒤 공백을 붙여 넣을 때). 클론 성공 여부는 JGit 동작이라 미확인 |
| C073 | 404 분기에서 `Promise.pure(notFound)` 결과를 버려 return 누락, 이후 "null" 포함 경로로 리다이렉트 | RedirectUtil.java:17-24 | 실제 | A-13(이전 이름 리다이렉트 자체를 하지 않음) | 해당 없음 | v2에 RedirectUtil/301 리다이렉트 없음(A-13). 호출처 두 곳은 v1.6에서도 도달하지 않는다고 A-13이 설명 | 희귀(v1.6에서도 도달 불가에 가까움) |
| C080 | SVN WebDAV `HEAD`가 읽기 목록에 없어 UPDATE 권한 요구 | SvnApp.java:188-197 | 실제 | R-62 (4) | 재현 | SvnDavService.kt:79 `READ_METHODS`에 HEAD 없음 | 정상(읽기 전용 사용자가 HEAD를 보낼 때) |
| C084 | 리뷰 수 부족이면 `isMerging=true` 저장 후 400을 반환해 이후 수락이 막힘 | PullRequestApp.java:428-437 | 실제 | R-76 (R-42 (2)) | 재현 | R-76이 "v2도 같은 상태가 남는다"고 기록(PullRequestActionController 쪽 코드는 이번에 열람하지 않음, 기록 문서 근거) | 정상 |
| C085★ | `newValue`가 비면 `split` 결과 `[""]`을 `Long.parseLong("")`에 넘겨 NumberFormatException | PullRequestEvent.java:151-158, PullRequestCommit.java:121-122 | 실제 | 없음 | 해결 | PullRequestPageAssembler.kt:185 (`toLongOrNull` + `mapNotNull`) | 희귀(커밋 없는 NEW_COMMIT 이벤트만) |
| C088 | 이슈 링크 문구에 `linkText += prefix + linkText`와 `prefix + linkText`로 접두와 문구가 중복 | AutoLinkRenderer.java:253-261 | 실제 | R-15 (1) | 재현 | AutoLinkRenderer.kt:128,132 | 정상(타 프로젝트 이슈 참조) |
| C089 | `appendReplacement`에 제목이 든 문자열을 그대로 써 `$`, `\`가 있으면 예외 | AutoLinkRenderer.java:215 | 실제 | R-15 (2) | 재현 | AutoLinkRenderer.kt:110 | 정상(제목에 `$`, `\`가 든 이슈 참조) |
| C090 | `extractIssueLink`가 권한 거부 시 true를 돌려 호출부가 `break`로 이후 링크 변환을 중단 | Markdown.java:152,187-189 | 실제 | R-15 (3) | 재현 | IssueLinkTransformer.kt:49,73 | 정상 |
| C094 | `updateAssginees`, `updateSharer`, 가중치 API는 프로젝트가 없으면 NPE | api/IssueApi.java:915-918,1157-1159,1177-1180 | 실제 | S-8 | 해결 | IssueApiController.kt 각 핸들러가 `find(...) ?: notFound` 사용(S-8 근거) | 희귀 |
| C096★ | `EventType.valueOf(notificationType)`가 잘못된 값이면 IllegalArgumentException(500) | WatchProjectApp.java:49-50 | 실제 | 없음 | 재현 | WatchProjectController.kt:73-74 `EventType.valueOf(notificationType)`가 프로젝트 확인과 권한 검사보다 먼저 실행됨 | 희귀 |
| C097 | 조직 신청 취소 검증이 `isGuest`만 봐서 이미 멤버인 사용자는 400 | EnrollOrganizationApp.java:96-107 | 결함 아님(멤버는 취소할 신청이 없으므로 400이 의도된 거절, 신청 중인 사용자는 정상) | 해당 없음 | 재현(동일 동작) | EnrollController.kt:100-106 (`isOrganizationGuest`) | 정상 |
| C098★ | 직전 이벤트 병합에서 `event.senderId.equals(...)`가 senderId null이면 NPE | NotificationEvent.java:470-473 (호출 :436-441) | 실제 | 없음 | 해결 | NotificationEvents.kt:107 `last.senderId == note.sender.id` (null 안전 비교) | 희귀(발신자 id가 null인 이벤트, 즉 익명 발신) |
| C099★ | 예외 경로에서 `synchronized` 없이 `notifyAll()` -> IllegalMonitorStateException | SvnApp.java:159-163 | 실제 | 없음(v1.6 위키에만 있음) | 해당 없음 | v2는 PlayServletResponse/별도 스레드 구조가 없고 SVNKit DAVServlet을 요청 스레드에서 직접 호출(SvnDavService.kt:60-62, JavaxServletBridge.kt) | 희귀(DAV 처리 중 예외) |
| C100★ | `wait()`만 쓰고 조건 변수가 없어 신호가 먼저 오면 영구 대기 | PlayServletResponse.java:49-55 | 실제(신호 유실 경쟁) | 없음 | 해당 없음 | 위와 같음, 대기 로직 자체가 없음 | 희귀(경쟁 조건) |
| C103 | 이슈 편집 폼 오류 시 `issueForm.error("name")`이 null이라 NPE(500) | IssueApp.java:741-744 | 실제 | decisions.md A-45 | 해결 | IssueEditController.kt가 400+폼 재표시로 처리(A-45) | 정상(제목을 비우고 저장) |
| C104★ | 빈 댓글 flash 분기가 앞선 400 처리 때문에 도달 불가 | IssueApp.java:970-971 vs 977-980, BoardApp.java:402-404 vs 413-416 | 실제(죽은 코드) | 없음 | 해당 없음 | v2에 `common.comment.empty` 분기가 없음(IssueCommentController.kt:64 한 곳에서만 처리) | 도달 불가 |
| C107 | `isMerging=true`를 저장한 뒤 리뷰 수 검사로 400을 돌려 병합 중 상태가 고착 | PullRequestApp.java:428-438 | 실제 | R-76, R-42(2), hand-crafted-request-differences.md:52 | 재현 | PullRequestWorkflow.kt:53-55 (표시 저장 후 NOT_REVIEWED 반환, 되돌리지 않음) | 정상(리뷰어 수 사용 프로젝트에서 리뷰 부족 상태로 병합 시도) |
| C108 | `Label.delete()`에서 projects가 null이면 NPE | Label.java:75-81 | 결함 아님(유일한 호출이 Project.java:539-546 `detachLabel`이고 직전에 `label.projects.remove`를 호출하므로 null일 수 없음) | 해당 없음 | 해당 없음 | - | 도달 불가 |
| C109★ | `editIssue`가 멀티파트 본문 또는 `assigneeLoginId` 필드가 없으면 NPE(500) | IssueApp.java:748-749 | 실제 | 부분(I-20b는 없는 이슈, milestoneId NFE만 적음. 이 항목은 없음) | 해결 | IssueEditController.kt:124 `request.getParameter("assigneeLoginId")`(null 허용), IssueService.kt:259 | 희귀(편집 폼은 항상 필드를 보냄) |
| C110 | `getAuthorizationToken`이 헤더 없으면 NPE | UserApi.java:311-315 | 결함 아님(호출처 IssueApi.java:283, 304는 앞서 `isAuthored`가 헤더 없음을 거르고, :531은 헤더 null 검사 뒤에 호출) | 해당 없음 | 해결(어차피 null 안전) | ApiRequests.kt:30-35 | 도달 불가 |
| C112★ | C099와 같은 `notifyAll()` 무동기화 | SvnApp.java:159-163 | 실제(C099와 중복) | 없음 | 해당 없음 | C099와 같음 | 희귀 |
| C115 | 이메일에 `@`가 없으면 `substring(0,-1)`로 StringIndexOutOfBounds | ProjectApp.java:824-825 | 실제 | R-85 | 해결(선생님 지시 2026-10-26) | MentionListService.kt:78, 133 `contains("@")` 가드 | 정상(커밋 작성자 이메일에 @가 없는 저장소) |
| C120 | `HIDE_PROJECT_LISTING`에서 `!isMemberOf |  | !isAdminOf`라 항상 400 | SearchApp.java:127-131 | 실제 | R-83, R-50(7) | 재현 |
| C126★ | `countProjects`가 `findList().size()`로 전체 행 로딩 | Search.java:468-470, 494-496 | 실제(성능) | 없음 | 해결 | SearchService.kt:141 `select count(...)` 쿼리 | 정상(검색 시) |
| C127 | `pageNum` 파싱 예외 미처리(NumberFormatException 500)이며 키워드/타입 검증보다 먼저 실행 | SearchApp.java:258-266, 호출 :116 | 실제 | decisions.md A-87 | 해결 | SearchController.kt:88, 95 숫자가 아니거나 1 미만이면 400 | 희귀(직접 만든 요청) |
| C128★ | 조직 검색의 일부 400이 본문 없는 `badRequest()` | SearchApp.java:118-122, 127-131 | 실제 | SearchController 주석 외 문서 기록 없음(R-50/R-83은 본문 없음을 적지 않음) | 재현 | SearchController.kt:63, 68 `ResponseEntity.badRequest().build()` | 희귀(직접 만든 요청, 숨김 설정 켜면 정상 사용도 해당) |
| C131★ | 없는 이슈 id로 즐겨찾기 토글하면 null issue 행이 저장되고 이후 목록/토글이 NPE | FavoriteIssue.java:32-35, User.java:1081-1083, UserApi.java:115-121 | 실제 | 없음(HC D-7은 프로젝트/조직만, DC A-47은 익명만) | 해결 | FavoriteService.kt:33-37 `orElseThrow`, FavoriteApiController.kt:42 400 `Wrong issue id`, 행 저장 안 됨 | 희귀(직접 요청) |
| C133★ | Attachment.save가 인자 fileName이 아니라 아직 비어 있는 필드 name으로 MIME 판정(업로드 시 항상 null, 내용만으로 판정). 이름이 null이고 Tika가 audio/ogg로 보면 ogv 검사에서 NPE도 가능 | Attachment.java:558-560, 498-521, AttachmentApp.java:84-87, FileUtil.java:127-140 | 실제 | 없음(BK R-17은 문자셋 건만, web/attachments.md:19는 "이름과 내용으로 감지") | 미재현(v2가 다름) | AttachmentService.kt:131,169 가 실제 파일명을 Tika 힌트로 넘김(확장자로 text/css 등 달라질 수 있음) | 정상(모든 업로드) |
| C134 | commentedCheck가 Boolean 래퍼라 null이면 `if (commentedCheck)`에서 NPE | SearchCondition.java:26,127,226,244,407 | 코드는 사실, 도달은 불확실(생성자가 false로 초기화. 빈 값 바인딩이 null이 되는지 미확인. 화면은 이 파라미터를 보내지 않음) | 없음(v1.6 위키 search-condition.md:140만) | 해결 | IssueListController.kt:237 문자열을 Boolean으로 파싱(null 불가), IssueSearchService.kt:244 | 희귀 |
| C136 | 임시 첨부 정리 쿼리가 `ge`라 최근 보관 기간 안의 것을 지움 | Attachment.java:458-461 | 실제 | BK R-20 | 재현 | TemporaryFileCleaner.kt:16,48 | 정상(스케줄러가 자동 실행) |
| C137★ | `Authorization.split("token")[1]`이라 토큰 값 안에 "token" 문자열이 있으면 잘림(Base64 SHA-256 토큰이라 확률 약 4e-8) | UserApi.java:57,136,300,313 | 실제(실질 도달 거의 없음) | 없음 | 재현 | ApiRequests.kt:32 `header.split("token")`(YonaAuthenticationFilter.kt:72는 `"token "`로 다름) | 희귀 |
| C138 | getFavoriteProjects 비교자가 내 프로젝트가 끼면 항상 MIN_VALUE를 반환해 계약 위반(정렬 불안정, 32개 이상이면 TimSort 예외 가능) | User.java:951-964 | 실제 | DC A-53(:391) | 해결(의도한 차이) | UserMenuService.kt:50 `sortedWith(compareBy(내 것 먼저, owner, name))` | 정상(사용자 메뉴) |
| C139★ | getDisplayName이 `this.lang`만 null 검사하고 현재 사용자의 `lang.startsWith("en")`는 null 검사 없이 호출해 NPE | User.java:1179-1197 | 실제 | 없음 | 해결 | UserDisplay.kt:19 `viewerLang?.startsWith("en")` | 희귀(lang이 null인 로그인 계정) |
| C140★ | extractDepartmentPart가 `[`, `(` 순으로 이미 잘린 문자열에서 다시 찾아, `(`가 `[`보다 앞서면 `(` 부분이 버려짐 | User.java:1166-1177 | 실제 | 없음(UserDisplay.kt KDoc에만 있음) | 재현 | UserDisplay.kt:28-31 | 정상(이름에 둘 다 있을 때) |
| C141 | NullUser.myProjects()가 부모 myProjects(String)과 시그니처가 달라 오버라이드가 아님(호출처 없음) | NullUser.java:43, User.java:252, user/view.scala.html:13 | 실제(무해, 죽은 메서드) | 없음 | 해당 없음 | v2에 NullUser 클래스 없음 | 해당 없음 |
| C142★ | 로그인 폼이 절대 URL인 Referer를 경로 문자열 `/users/loginform`과 비교해 항상 다름. 로그인 화면 자신이 redirectUrl이 됨 | UserApp.java:138-143 | 실제 | 없음(web/layout-and-login-page.md:57는 정상 동작처럼 서술) | 재현 | LoginFormController.kt:37-39, 56(`LOGIN_FORM_PATH`와 비교) | 정상(실패한 로그인 뒤 등) |
| C143 | 비밀번호 재설정 요청에서 targetUser.email이 null이면 `.equals`에서 NPE | PasswordResetApp.java:60-64 | 코드는 사실, email null 계정이 있어야 닿음(Email 제약만 있고 필수 아님) | 없음 | 해결 | PasswordResetController.kt:52 `target.email != email`(null 안전) | 희귀 |
| C144★ | 마감일 null인 열린 이슈에서 `JodaDateUtil.ago(null)`이 현재 시각으로 계산돼 isOverDue가 비결정적(대부분 false) | Issue.java:675-677, JodaDateUtil.java:44-46, IssueApp.java:446-451 | 실제 | 없음 | 해결 | IssueMassUpdateController.kt:99 `dueDate?.before(now) == true`(null이면 false) | 정상(목록에서 마감일 해제 후 JSON 응답) 결과는 거의 같음 |
| C146★ | 검증 오류 때 `issueForm.error("name")`이 null(Issue에 name 필드 없음)이라 NPE 500 | IssueApp.java:741-744 | 실제 | 없음(HC I-20b는 다른 항목) | 해결 | IssueEditController.kt:99 400 편집 화면 | 희귀(빈 제목 등, 클라이언트 검증 우회) |
| C149 | roleTypeOf가 enum 이름(대문자)과 DB role.name을 섞어 반환, isGuest는 `GUEST`와만 비교 | OrganizationUser.java:70-97, EnrollOrganizationApp.java:68,103 | 실제(관찰 가능한 영향 없음: 비구성원만 guest, 사이트 관리자는 제외) | 없음 | 해당 없음(동작 동일) | EnrollController.kt:117-118, OrganizationViewContext.kt:71-73 | 해당 없음 |
| C150 | FileDiff.updateRange에서 lineA와 lineB가 같은 Edit에 맞으면 Edit이 두 번 추가됨 | playRepository/FileDiff.java:230-252 (후보의 경로 FileDiff.java는 app/playRepository) | 실제 | 없음 | 재현 | FileDiff.kt:188-195 동일. v1.6과 v2 모두 호출처 없음(죽은 코드) | 해당 없음(죽은 코드) |
| C151 | 없는 이슈에서 담당자 검사 시 `byId(...).assignee` NPE | AccessControl.java:398-405(후보의 :401이 해당 줄) | 실제 | BK R-10(줄 인용은 :403-405로 어긋남, 실제 :401-402) | 재현(NoSuchElementException) | R-10 본문 | 희귀 |
| C154 | C136과 같은 항목(`ge`로 최근 파일 삭제) | Attachment.java:458-461 | 실제 | BK R-20 | 재현 | TemporaryFileCleaner.kt:48 | 정상(스케줄러) |
| C156 | SvnApp이 synchronized 밖에서 `statusLock.notifyAll()`을 호출해 IllegalMonitorStateException, 요청이 대기에서 풀리지 않음 | SvnApp.java:159-163, PlayServletResponse.java:49-55 | 실제 | DC A-98(2),(5)이 스레드 제거와 오류 경로를 부분 서술(결함 자체 명시는 없음) | 해당 없음(구조 변경) | SvnDavService.kt:62-64 스레드/파이프 없이 서블릿 응답에 바로 씀 | 희귀(DAV 서블릿 예외 시) |
| C157 | validateForLeave 조건이 거꾸로(관리자는 혼자여도 탈퇴, 일반 구성원은 관리자 1명이면 탈퇴 불가) | OrganizationApp.java:297-311 | 실제 | BK R-19 | 재현 | OrganizationManagementService.kt:131-145 | 정상 |
| C158 | validateForAddMember가 hasErrors() 확인 전에 `get()`을 호출해 폼 오류 시 예외 | OrganizationApp.java:163-167 | 실제 | HC P-35 | 해결 | P-35 본문(v2는 303과 경고) | 희귀(HC 분류. 입력란 직접 입력으로도 닿을 수 있음, 미확인) |
| C161 | 조직 게시판 목록이 `orderBy.equals("id")`로 비교해 `orderBy`가 null이면 NPE | BoardApp.java:125 (기본값은 AbstractPostingApp.java:46 `"id"`) | 미확인(기본값이 있어 정상 요청은 null이 아니다. 빈 `orderBy=`가 null로 묶이는지는 실행 없이 확인 못 함) | B-01(`hand-crafted-request-differences.md:29`)이 `orderBy` 덮어쓰기와 무검증만 적고 NPE는 적지 않음 | 해결 | `OrganizationBoardController.kt:41-42`가 빈 값이면 기본값을 쓴다(오류 없음) | 희귀 |
| C164★ | `PullRequestMergingActor`가 `findById` 결과 null 검사 없이 `startMerge()`를 불러 NPE | PullRequestMergingActor.java:38-40 | 실제 | 없음 | 해결 | `PullRequestMergeProcessor.kt:44,59-60`이 `orElse(null) ?: return`으로 조용히 끝낸다(오류 유지 안 함 지침에 부합) | 희귀(병합 검사 전에 요청이 삭제된 경우) |
| C165 | `afterMilestoneChanged`에서 `oldMilestoneId`/`milestoneId()`가 null이면 `toString` NPE | NotificationEvent.java:1016-1017 | 결함 아님: `Issue.milestoneId()`는 마일스톤이 없으면 `NULL_MILESTONE_ID`를 돌려주고(Issue.java:171-176), 호출처 IssueApp.java:497,508도 이 값을 넘겨 null이 되지 않는다 | 해당 없음 | 해당 없음 | `IssueMassUpdateService.kt:74`도 null이면 `NONE` 상수를 쓴다 | 닿지 않음 |
| C166★ | `getMessage`의 `ISSUE_SHARER_CHANGED`/`ISSUE_LABEL_CHANGED`에 break가 없어 값이 둘 다 비면 다음 case로 떨어지고 `RESOURCE_DELETED`에서 사용자 조회 NPE. 라벨 case는 라벨 문자열을 로그인 ID로 조회하는 오류도 있음 | NotificationEvent.java:208-224 | 실제(잠재: 라벨 이벤트는 수신자가 없고 공유자 이벤트는 값이 항상 있어 정상 흐름에서는 호출되지 않음) | 없음 (`subsystems/notifications.md:24-25`은 이벤트 표만 있음) | 해결 | `NotificationMessages.kt:69-72`가 `else null`로 끝내고 라벨 case가 없어 폴스루가 없다 | 희귀 |
| C167 | 가입 거절(REJECT) 이벤트의 title이 null이 되는 switch 누락 | NotificationEvent.java:1236-1250 | 결함 아님(도달 불가): `afterMemberRequest`는 REQUEST(EnrollProjectApp.java:50), CANCEL(:68), ACCEPT(ProjectApp.java:958)로만 불리고 REJECT 호출처가 없다 | 없음 | 재현(코드만) | `NotificationListener.kt:351,357-358`이 REJECT를 같은 모양(제목 없음)으로 처리하되 호출처는 없다 | 닿지 않음 |
| C175 | `CodeHistoryApp`의 `page`를 `Integer.parseInt`, 숫자가 아니면 500 | CodeHistoryApp.java:82-86 | 실제 | M-15(`hand-crafted-request-differences.md:46`) | 해결 | 문서상 400(M-15 v2 처리). 코드 줄은 확인하지 않음 | 희귀 |
| C176 | `SiteApp.userList`/`issueList`의 `state`를 `valueOf`, 모르는 값이면 500 | SiteApp.java:132-133,152-153 | 실제 | M-15 (같은 줄) | 해결 | M-15 v2 처리가 400. 코드 줄은 확인하지 않음 | 희귀 |
| C177 | `getBoolean("application.update.check.use")` 언박싱으로 키가 없으면 NPE, 화면에 스택트레이스 | SiteApp.java:331 | 실제 | R-55(4)(`refactoring-backlog.md:62`) | 해결 | A-91(3): `check.use` 기본값이 켬 (`decisions.md:429`) | 희귀(설정 파일에서 키를 뺀 경우) |
| C183 | PR 재오픈이 리뷰어를 초기화하지 않고 병합 액터가 새 커밋을 찾을 때만 초기화 | PullRequest.java:667-670; PullRequestActor.java:39-47 | 동작은 사실이나 결함인지 의도인지 판정할 근거 없음 | v1.6 위키 `web/pull-request-app.md:144`에만 있고 v2 문서에는 따로 없음 | 재현 | `PullRequestWorkflow.kt:88-98`(reopen이 리뷰어를 건드리지 않음), `PullRequestMergeProcessor.kt:81`(새 커밋 때만 `reviewers.clear()`) | 정상 |
| C186★ | `webhook.gitPush == true` 언박싱으로 `gitPush`가 null이면 NPE | NotificationEvent.java:677 | 실제(조건부, 등록 시 false가 들어가 DB를 직접 고친 경우만) | 없음 | 해결 | `WebhookService.kt:148` `hook.gitPush == true`(null 안전), `:46`은 `gitPush ?: false` | 희귀 |
| C187★ | multipart/related에서 `start`도 Content-ID도 없는 자식이 `StringUtils.equals(null,null)`로 모두 루트가 되어 병합됨 | CreationViaEmail.java:388-390 | 실제 | 없음 (`web/mailbox.md:41`은 규칙만 적음) | 재현 | `MailParsing.kt:146` `part.contentID == rootId`(null 둘이면 참) | 정상(Content-ID 없는 related 메일) |
| C188 | `resetUserPasswordBySiteManager`가 `action` 쿼리가 없으면 NPE | UserApp.java:1339 | 실제 | U-19(`hand-crafted-request-differences.md:17`) | 해결 | 문서상 400(코드 줄은 확인하지 않음) | 희귀 |
| C189★ | `RecentProject.deleteOldestIfOverflow`가 목록을 줄이지 않고 같은 최소 원소를 반복 삭제해, 루프 종료를 이미 지운 엔티티의 `refresh()` 예외에 의존(예외 동작은 실행 없이 확인 못 함) | RecentProject.java:91-105 | 구조는 실제(목록 크기가 줄지 않음) | 없음 (`domain/recent-visits.md`는 "30개 초과 시 오래된 것 삭제"만 적음) | 해결 | `RecentVisitService.kt:45` 초과분을 `drop(30)`으로 한 번에 삭제(루프 없음) | 정상(내부 처리, 증상은 드묾) |

## 화면 (7건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C035 | `<strong>`이 `@if` 안에서 열리고 `</strong>`은 밖이라, 푸시 기록 없는 프로젝트는 짝 없는 `</strong>`만 출력 | views/project/list.scala.html:76 | 실제 | 없음 | 해결(다름) | `templates/project/list.html:65` 태그가 `th:block th:if` 안에서 열리고 닫힘 | 정상(프로젝트 목록 화면, 브라우저는 짝 없는 닫는 태그를 무시해 눈에 보이는 차이 없음) |
| C045★ | `Q` 단축키가 로그인 ID `manager`일 때만 켜지고(R-11), 이때 출력이 앞 쉼표 없이 `{ "Q": ... }`라 객체 리터럴이 구문 오류가 되어 해당 사용자는 프로젝트 화면 단축키 스크립트 전체가 깨짐 | projectMenu.scala.html:145-151 | 실제 | R-11 일부(`web/project-layout.md:41,78`도 같음). 쉼표 누락은 어디에도 없음 | 일부 재현(로그인 ID 조건은 재현, 쉼표 누락은 해결/다름) | 조건: `web/project/ProjectViewContext.kt:160`. 쉼표 있음: `templates/project/menu.html:84-86` `,"Q"` | 희귀(로그인 ID가 `manager`인 계정이 프로젝트 화면을 볼 때) |
| C053 | `fileType.eq(" folder")`(앞 공백, 참조 비교)로 항상 거짓이라 앵커가 안 붙음 | views/code/partial_view_folder.scala.html:31 | 실제 | R-38 | 재현 | `web/code/CodePageAssembler.kt:18-19,84` 앵커 안 붙임(주석에 R-38) | 정상 |
| C148★ | Organization.getVisibleProjects는 대소문자 구분 정렬, getSortedByProjectName은 무시 정렬이라 불일치 | Organization.java:82-90,133-138, myOrganizationList_partial.scala.html:31 | 실제 | 없음 | 재현 | OrganizationService.kt:102 `sortedBy { it.name }`(구분), UserMenuAssembler.kt:61 `lowercase()`(무시) | 정상(표시 순서) |
| C152★ | `"error.fobidden"` 오타 키가 어느 messages 파일에도 없어 로그인 화면 제목에 키가 그대로 나옴(5개 번들 모두 0건) | ErrorViews.java:47 | 실제 | 없음(web/project-checks.md:43이 오타만 서술, BK 없음) | 재현 | ErrorViews.kt:72-75 `loginPage(returnUrl,"error.fobidden")`, src/main에 해당 키 없음 | 정상(로그아웃 상태로 없는/비공개 프로젝트 접근) |
| C159★ | `-ms-sbox-izing` 접두 오타 | app/assets/stylesheets/less/_mixins.less:176 | 실제 | 없음 | 재현 | v2 less 소스가 v1.6과 동일(git diff 없음), 빌드 산출 yobi.css:11461 | 정상(무해, 브라우저가 무시) |
| C160★ | `fonts/material-icon..ttf` 점 두 개 오타(woff2/woff가 먼저 선택돼 ttf 폴백만 영향) | app/assets/stylesheets/usermenu.less:25 (후보의 _usermenu.less는 less/ 아래 별도 파일) | 실제 | 없음 | 재현 | usermenu.less 동일(git diff 없음), 빌드 산출 usermenu.css:7 | 정상(구형 브라우저 ttf 폴백만) |

## JS (27건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C003★ | `requestAs`의 `get` 분기가 선언하지 않은 `el`을 읽어 ReferenceError | jquery.requestAs.js:49 | 실제(`sMethod==="get"`일 때만) | 없음 | 재현 | 번들 yona-common.js:172(압축본에 `"get"!==sMethod\ | \ |
| C004★ | `_onSuccessSubmit`이 응답 검증 실패 때 `_onErrorSubmit(nSubmitId, oRes)`로 네임스페이스를 빠뜨려 네임스페이스 핸들러가 `errorUpload`를 못 받음 | yobi.Files.js:225-227 (`_onErrorSubmit` 시그니처 :247) | 실제 | 없음 | 재현 | 동일 파일. yona-lib.js 번들(`function k(a,b,c){A("errorUpload",...,c)}`)과 scripts.html:94-95가 사용 | 정상(업로드 응답에 name/url이 없을 때) |
| C005★ | 업로드 실패 시 임시 주석을 `nSubmitId + ".png"`로 지우는데 삽입은 `nSubmitId`(접미 없음)라 주석이 남는다 | yobi.Attachments.js:350 vs :533 (`_getTempLinkText` :460) | 실제 | 없음 | 재현 | yona-lib.js 번들에 `B(y(a.nSubmitId+".png"))` 존재 | 정상(붙여넣기 업로드 실패) |
| C006★ | `_onSuccessUpload`가 응답 검증 전에 `oRes.id`를 목록에 등록하고, `_onErrorUpload(nSubmitId, oRes)`로 호출하지만 함수는 `htData` 하나를 받아 `htData.nSubmitId`가 undefined | yobi.Attachments.js:282-285 (`_onErrorUpload` :341) | 실제 | 없음 | 재현 | 동일 파일, 번들 포함 | 정상(업로드 응답 이상 시) |
| C007★ | 업로드마다 `.on("click", ".btn-delete", deleteAttachment)`를 다시 걸어 삭제 핸들러가 누적 | yona.CommentAttachmentsUpdate.js:136-137 | 실제 | 없음 | 재현 | 동일 파일. issue/view.html, board/view.html이 불러 씀 | 정상(한 댓글 폼에서 두 번 이상 업로드 후 삭제) |
| C008★ | `splitIds.includes(fileId)`가 문자열 배열과 숫자 id를 비교해 항상 거짓, 같은 id가 중복으로 붙을 수 있음 | yona.CommentAttachmentsUpdate.js:154 | 실제 | 없음 | 재현 | 동일 파일 | 정상(같은 파일 id가 다시 오는 경우만 영향, 영향 작음) |
| C009★ | `if (!data && !data.receivers)`가 `&&`라 data가 null이면 TypeError, data가 있고 receivers만 없으면 통과해 `sortByName(undefined)` | yona.ReceiverList.js:33 | 실제 | 없음 | 재현 | 동일 파일. issue/view.html이 불러 씀 | 희귀(서버가 항상 객체를 돌려줌) |
| C010★ | `getPlainText`가 인자 대신 전역 `window.event`를 씀 | yobi.Files.js:588 | 실제 | 없음 | 재현 | 동일 파일. yona-lib.js 번들에도 `event.preventDefault()`/`clipboardData.getData` | 정상(표 붙여넣기. 전역 event가 있는 브라우저에서는 동작) |
| C011★ | `htLabels.hasOwnProperty()`에 인자가 없어 항상 false, 라벨 버튼 비활성화 의도가 동작하지 않음 | yobi.issue.MassUpdate.js:190 | 실제 | 없음 | 재현 | 동일 파일. issue/partial-massupdate.html:105 `loadModule("issue.MassUpdate")` | 정상(버튼이 항상 활성) |
| C022★ | `_beautifyFileSize`가 `welShowfile`(소문자 f)을 읽어 TypeError. 요소는 `welShowFile` | yobi.code.Browser.js:359 (정의 :72) | 실제 | 없음(R-39는 크기 문자열이 비는 다른 결함) | 재현 | 동일 파일. code/view.html:111 `#showFile`(이진 파일일 때), :151 `loadModule("code.Browser")` | 정상(이진 파일 보기) |
| C023 | `_initPagination(htOptions)`가 옵션 없이 호출되면 `htOptions.nTotalPages`에서 TypeError | yobi.board.List.js:25, 99 | 코드상 잠재 결함. 현재 호출부는 항상 옵션을 넘김 | 없음 | 해당 없음(도달 안 함) | board/list.html:70, organization/board-list.html:83 모두 `{elPagination, nTotalPages}` 전달 | 도달 불가 |
| C024★ | 삭제 링크를 누를 때마다 `#deleteBtn` click 핸들러를 추가해 취소 후 다른 구성원을 지우면 이전 구성원 URL로도 DELETE | yobi.organization.Member.js:165-172 | 실제 | 없음 | 재현 | 동일 파일. organization/members.html:44(`data-action="delete"`), :60(`#deleteBtn`), :89 | 정상(삭제 확인 창을 닫고 다른 구성원 삭제. 의도하지 않은 구성원 삭제 가능) |
| C025★ | 비밀번호/확인 keyup이 `htVar.oDelay` 하나를 공유해 한쪽이 다른 쪽 검사를 취소 | yobi.user.SignUp.js:99-117 | 실제 | 없음 | 재현 | 동일 파일. user/signup.html:81 | 정상(영향 작음) |
| C026★ | 문서에 붙이지 않은 `$('<form>')`에 `submit()`을 호출해 최신 브라우저는 제출하지 않으므로 `mailto:` 메일 창이 안 열림 | yobi.site.MassMail.js:107-116 | 실제(브라우저 의존) | 없음(R-54(6)은 mailto 길이 한계) | 재현 | 동일 파일. site/mass-mail.html:42 | 희귀(사이트 관리자 화면) |
| C032 | 코드/PR 체크 해제 때 초기화되지 않은 `welReviewerCountSettingPanel`을 호출해 TypeError | yobi.project.New.js:198-200, 210 | 실제(대입이 파일 어디에도 없음) | R-78(코드 체크 해제 기준. :210 PR 체크 해제 경로도 같은 원인) | 재현 | 동일 파일. project/create.html:184, importing.html:210 | 정상(프로젝트 생성/가져오기 화면) |
| C042★ | `--`/`++`로 시작하는 줄(예: 내용이 `-`/`+`로 시작하는 삭제/추가 줄)을 파일 헤더 분기로 보내 `return`해 렌더링 안 되고 줄 번호도 어긋남 | yobi.code.SvnDiff.js:350-360 | 실제 | 없음(`web/subversion.md:20`은 스크립트 사용만 적음) | 재현 | `static/assets/javascripts/service/yobi.code.SvnDiff.js` v1.6과 바이트 동일, `templates/code/svn-diff.html:117`, `compare-svn.html:27`이 불러 씀 | 정상(Subversion 프로젝트 커밋/비교 화면) |
| C043★ | 헝크 헤더 파싱 실패 시 경고만 하고 `aHunkRange[1]`로 TypeError | yobi.code.SvnDiff.js:383-395 | 실제 | 없음 | 재현 | 위와 같음(바이트 동일, 같은 템플릿이 사용) | 희귀(형식이 어긋난 헝크 헤더) |
| C044★ | 선택 없을 때 mousedown이 코멘트 상자를 닫음 | yobi.code.Diff.js:416-429 | 결함 아님(의도된 동작) | 해당 없음 | 해당 없음 | `yobi.code.Diff.js` 바이트 동일 | - |
| C064★ | 번역 `.done` 콜백의 `$(this)`가 버튼이 아니라 ajax 설정 객체여서 버튼이 비활성화되지 않음 | views/board/view.scala.html:271,290 (issue/view.scala.html:574,593 등도 같은 패턴) | 실제 | 없음(R-30은 `.error`만 다룸) | 재현 | `templates/board/view.html:223,242`, `templates/issue/view.html:379,398`이 같은 코드 | 정상(번역 단추. `.one('click')`이라 재클릭은 막혀 해는 작음) |
| C074★ | 검증 실패 분기가 `_onErrorUpload(nSubmitId, oRes)`로 호출하지만 수신 함수는 `htData` 하나라 `htData.oRes.status`에서 TypeError | yobi.Attachments.js:285,341 | 실제 | 없음 | 재현 | static/assets/javascripts/common/yobi.Attachments.js:285,341(원본과 diff 동일). 템플릿은 common/scripts.html:45의 `yona-lib.js`를 불러 쓰고 그 안의 최소화된 Attachments도 같은 결함(`return e(b,a)` 대 `function e(a){...a.nSubmitId`) | 희귀(업로드가 200이지만 응답에 name/url이 없을 때) |
| C077★ | 알림 `.more` 토글이 링크/이미지 클릭 판정보다 먼저 실행돼 링크를 눌러도 토글됨 | notifications.scala.html:82-91 | 실제 | 없음(R-35는 다른 항목) | 재현 | templates/index/notifications.html:77-89 (코드 동일) | 정상 |
| C101★ | 첨부 이전 위임 서비스 함수가 undefined를 반환하는데 `.then` 호출 -> TypeError | yona.Migration.js:123, 128, 685-687 | 실제 | 없음(R-58 (1)~(7)에 없음) | 재현 | yona.Migration.js가 v1.6과 바이트 동일이고 migration/home.html:15가 불러 씀 | 정상(이전 화면 사용 중 해당 경로 진입) |
| C102★ | `endLoadingBar(counter++)` 후위 증가라 counter가 늘지 않아 타임아웃 불가 | yona.Migration.js:489 | 실제 | 없음 | 재현 | 동일 JS, migration/home.html:15 | 정상(가져오기 수가 기대값과 어긋날 때) |
| C113★ | (a) Browser.js:359 `welShowfile` 오타(정의는 `welShowFile`, :72) (b) MassUpdate.js:174 `htLabel` 전역 누출(:166 선언에 없음, :230에만 있음), :190 `hasOwnProperty()` 인자 없음 (c) Member.js:161-172 삭제 클릭마다 `#deleteBtn`에 핸들러 누적 (d) SvnDiff.js:395 `aHunkRange`가 null이면 TypeError | 열거한 파일:줄 | 실제(4건 모두 소스에서 확인) | 없음(refactoring-backlog.md에 해당 JS 항목 없음) | 재현 | 5개 JS 모두 v1.6과 바이트 동일, 템플릿이 로드: code/view.html:151, partial-massupdate.html:105, organization/members.html:89, code/svn-diff.html:117, code/compare-svn.html:27 | (a)(c) 정상, (b) 정상(일괄 수정 사용), (d) 희귀(형식 틀린 헝크 헤더) |
| C174★ | `[data-action=delete]` 클릭마다 `#deleteBtn` click 핸들러를 새로 등록해 누적, 확인 한 번에 DELETE가 여러 번 나감 | yobi.organization.Member.js:161-175 | 실제 | 없음 (v1.6 위키에만 있음) | 재현 | `static/assets/javascripts/service/yobi.organization.Member.js`가 v1.6과 동일(diff 없음), `templates/organization/members.html:89`가 `loadModule("organization.Member")`로 불러 쓰고 같은 파일 44,60줄에 `data-action="delete"`와 `#deleteBtn`이 있음. 메모리의 "organization.Member 죽은 코드" 목록과 어긋나므로 확인 필요 | 정상 |
| C181★ | `_onSuccessUpload`가 `_onErrorUpload(nSubmitId, oRes)`로 위치 인자를 넘기지만 함수는 객체 하나를 받아 `htData.nSubmitId`가 undefined, 진행 항목이 안 지워지고 오류 안내가 깨짐 | yobi.Attachments.js:285,341-342 | 실제 | 없음 (v1.6 위키에만 있음) | 재현 | `static/assets/javascripts/common/yobi.Attachments.js`가 v1.6과 동일, `yona-lib.js`(압축본)에도 `return e(b,a)`와 단일 인자 `e(a)`가 있고 업로더가 화면에서 쓰임 | 희귀(서버 응답에 name/url이 없을 때) |
| C182★ | `_fireEvent`가 `bResult = bResult \ | \ | fHandler(oData)`라 한 핸들러가 참을 돌려주면 이후 핸들러가 호출되지 않음 | yobi.Files.js:760-763 | 실제 | 없음 | 재현 |

## 설정·빌드 (2건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C002 | 게스트 접두어 설정 키 `application.guest.user.login.id.prefix ` 끝에 공백이 있으나 Typesafe Config 1.2.1이 앞뒤 공백을 무시해 **결함이 아니다**(2026-10-27 실행으로 확인, 이전 판정은 틀렸다) | Application.java:38 | 오탐 | R-60(3) | 해당 없음 | LdapAuthService.kt:92-93 `local.isGuest = false`. v2에는 `GUEST_USER_LOGIN_ID_PREFIX` 상수가 없다 | 정상(LDAP 사용 시 설정으로 발현) |
| C163★ | `application.temporaryfiles.keep-up.time` 주석 예시 `86400`(초 의도)을 `getMilliseconds`로 읽어 86.4초가 됨 | application.conf.default:228-229, AttachmentApp.java:39-40 | 실제 | 없음 (`configuration-keys.md`에 이 키 행도 없음) | 해결 | `YonaProperties.kt:27,106`이 밀리초 `Long`(기본 86_400_000)이고 문서 주석에 단위를 적었다. v2에 `86400` 예시 파일은 없다 | 정상(설정을 예시대로 쓴 경우) |

## 문서 (1건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C078 | "기존 위키 기재 결함 다수"라는 집계 항목으로 특정 결함이 아님 | - | 판정 불가(개별 결함 없음) | - | 해당 없음 | - | - |

## 보안/서버 (1건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C153★ | parseCredentials가 `credentials.substring(6)`을 길이/접두어 검사 없이 호출(짧거나 Basic이 아닌 헤더면 예외 500) | BasicAuthAction.java:60-64, 92 | 실제 | 없음 | 해결 | BasicCredentials.kt:25 `startsWith("Basic ")` 아니면 null | 희귀 |

## 서버(죽은 코드) (2건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C170★ | `FavoriteIssue.updateFavoriteIssue`/`User.updateFavoriteIssue` 호출처 없음, 루프 변수명 복사 오류 | FavoriteIssue.java:37-44, User.java:1064-1070 | 실제(죽은 코드) | 없음 (죽은 코드 후보 정리는 선생님 확인 사항) | 해당 없음 | v2에 `updateFavoriteIssue`가 없다(`src/main` grep 0건) | 닿지 않음 |
| C185★ | `Webhook.findByIds`가 없는 속성 경로 `webhook.id`로 조회 | Webhook.java:165-170 (호출은 정적 `delete(Long,Long)` 한 곳, :150이고 그 메서드에도 호출처 없음. 실제 삭제는 ProjectApp.java:1318 `webhook.delete()`) | 실제(죽은 코드) | 없음 (A-82는 삭제 범위 설명, 이 결함 아님) | 해당 없음 | v2에 같은 메서드 없음 | 닿지 않음 |

## 서버/보안 (1건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C132 | 구독자 API가 없는 프로젝트/게시물이면 NPE 500, 권한 검사도 없음 | WatcherApi.java:31-42, AbstractPosting.java:176-178 | 실제 | DC A-65(:403) | 해결 | ExternalProjectApiController.kt:252-262 `@IsAllowed(READ)`, 없으면 404 | 희귀 |

## 화면(메시지) (1건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C184 | 영어/러시아어 `viaEmail.error.cannotCreate`가 `{1}~{3}`을 쓰지만 인자는 `{0}`부터 3개라 어긋남, `{3}` 미치환 | conf/messages:1105; messages.ru-RU:998; CreationViaEmail.java:431 (ko/uz는 `{0}~{2}`로 정상) | 실제 | R-61(3)(`refactoring-backlog.md:68`)이 영어 예를 적음(러시아어 명시는 없음, 같은 메시지 형태) | 재현 | `messages.properties:1105`, `messages_ru_RU.properties:998` 동일, `MailResourceCreator.kt:174`가 `who, typeName, project` 3개를 `{0}`부터 넘김 | 정상(메일 수신 권한 오류 회신) |

## 화면(죽은 템플릿) (1건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C178 | `partial_pagination`이 호출처 없고 창 계산이 범위 밖 번호를 만듦 | partial_pagination.scala.html:28-32,48 | 실제(죽은 코드) | `roadmap.md:95`(9d)에 호출 없는 죽은 템플릿으로 이식 제외 기록(백로그 R번호는 없음) | 해당 없음 | v2 템플릿에 없음 | 닿지 않음 |

## 화면(템플릿) (21건)

| 번호 | 결함 요약 | v1.6 소스:줄 | 실제 여부 | 기록 | v2 판정 | v2 근거 | 닿는 경로 |
|---|---|---|---|---|---|---|---|
| C012★ | 이슈 본문이 null이면 상세 화면이 `issue.body.substring`, `sha1Hex(issue.body)`에서 NPE | issue/view.scala.html:53, :450 (빈 본문 처리 :174) | 실제(코드상. 본문이 null일 때) | 없음(A-64는 외부 API의 body 누락만) | 해결 | IssueViewAssembler.kt:77 `issue.body.orEmpty()`, :112 `sha1Hex(body)`. v2 view.html:253 | 희귀(웹 폼은 빈 문자열을 저장. 외부 API/메일 수신 이슈에서 null 가능. v1.6 `newIssue`는 body null이면 413) |
| C020★ | `urlToList(project, state)`가 `state` 인자를 무시하고 항상 `"open"`을 써서 닫힌 이슈/하위 이슈의 라벨 링크도 열림 목록으로 감 | my_partial_list.scala.html:42,74, partial_view_child.scala.html:11,25 | 실제 | R-49(4)·R-64(1)은 링크 모양(`?state=open&labelIds=`)만 "정상 주소"로 적음. `state` 인자 무시는 미기록 | 재현 | my-partial-list.html:40, partial-subtasks.html:38, partial-list.html:74,165 모두 `state=open` 고정 | 정상(닫힌 이슈의 라벨 클릭) |
| C021★ | 일반 목록이 비면 `else` 안에서만 그려지는 초안 목록이 안 그려져, 초안만 있는 프로젝트의 이슈 화면은 초안이 안 보임 | partial_list_wrap.scala.html:61-86 | 실제 | 없음 | 재현 | partial-list-wrap.html:34(빈 목록 분기), :42-58(비어 있지 않을 때만 `draftList`) | 정상(열린 이슈 없이 초안만 있는 프로젝트) |
| C028 | 게시글 댓글 검색 결과의 작성자 없음 문구 키 `posting.noAuthor`가 메시지 파일에 없음 | partial_post_comments.scala.html:45 (메시지는 `common.noAuthor`, `issue.noAuthor`만 conf/messages:189,333) | 실제 | R-50(2) | 재현 | SearchRowAssembler.kt:83 `if (noAuthorKey == "posting.noAuthor") noAuthorKey`(키 이름 그대로 출력), messages.properties에 키 없음 | 희귀(작성자 이름이 비어 있는 게시글 댓글) |
| C029 (2026-10-27 확대: 검색 행, 게시판 목록, 사용자 페이지, 사이트 목록, REST JSON까지 `NullUser` 표시로 맞춤, A-117) | 후보 서술은 "`findByLoginId`가 NullUser를 돌려 noAuthor 분기 미도달"이지만, NullUser는 `name`이 비어 있지 않으므로(`user.notExists.name`) 존재하지 않는 ID는 "없는 사용자" 링크로 나오고, 이름이 비워진 삭제 사용자는 noAuthor 분기에 닿는다 | partial_issue_comments.scala.html:54-60 등, User.java:283-291, NullUser.java:37 | 후보 서술은 부분적으로만 맞음(결함이라기보다 분기 도달 조건). 결함으로 보지 않음 | 없음 | 부분 상이 | SearchRowAssembler.kt:67-70 `author()`는 로그인 ID가 DB에 없으면 `authorName=null`이라 noAuthor 문구를 그림(v1.6은 NullUser 이름 링크). 삭제 사용자(이름 빈 값) 처리는 같음 | 희귀(작성자 로그인 ID가 DB에 없는 데이터) |
| C071★ | 길이 7인 비16진 색(`#zzzzzz`)이면 `Integer.parseInt`가 NumberFormatException이라 labels.css 전체가 500 | issueLabelColor.scala.html:44-48 | 실제 | 없음(R-22는 3자리 색만) | 해결 | IssueLabelStyles.kt:`HEX_LIKE` 검사와 `catch (RuntimeException)`이 흰색으로 처리 | 희귀(색 입력은 선택기) |
| C076★ | 즐겨찾기 조직이 비고 조직이 있으면 `favoredOrganizations.last`에서 예외 | allOrganizationList.scala.html:16 | 실제(단, 이 템플릿을 부르는 곳이 없음) | 없음 | 해당 없음 | v2 templates에 allOrganizationList 없음(index/my-organization-list.html만 있음) | 희귀(죽은 템플릿) |
| C086 | 상대 링크 치환에서 `application.context`가 있으면 `"/" + root + owner`가 `//ctxowner`가 됨 | Markdown.java:359-375 | 실제 | R-14 | 재현 | RelativeLinkRewriter.kt:25,36,43 (`"/$context$owner/..."`) | 정상(컨텍스트 경로를 쓰는 배포) |
| C087★ | 정규식 `(.*)\)`가 탐욕이라 한 줄에 상대 링크가 둘이면 합쳐짐 | Markdown.java:363,372-373 | 실제 | 없음(R-14는 맨 앞 링크와 컨텍스트 경로만) | 재현 | RelativeLinkRewriter.kt:40-41 `(.*)\\)` 동일 | 정상 |
| C114 | 알 수 없는 `tabId`는 `match`에 default가 없어 MatchError | project/home.scala.html:80-90 | 실제 | R-68(4), hand-crafted-request-differences.md P-03(4) | 해결(A-24, 내용 없는 탭 화면) | 문서 근거 | 희귀 |
| C116★ | `htKeyMap`에서 Q 항목 앞 쉼표가 없어 loginId가 manager일 때 스크립트 문법 오류 | projectMenu.scala.html:145-151 (`,"P"` 뒤에 쉼표 없이 `"Q"`) | 실제(쉼표 누락은 소스에서 확인) | R-11은 manager 조건만 기록, 쉼표 누락은 기록 없음 | 해결(v2가 다름) | project/menu.html:85-86 `,"Q": ...`(쉼표 있음), ProjectViewContext.kt:160 조건은 v1.6 그대로 | 희귀(loginId가 정확히 manager인 사용자). 정상 사용자 중 이 계정이면 v1.6에서 단축키 스크립트 전체가 깨짐 |
| C119★ | `getItemHTML`이 `data-value="x"`와 `data-selected` 사이 공백을 안 넣음 | TemplateHelper.scala:257-262 (`equalsThen`:139-143) | 실제이나 v1.6 안에서 호출처가 없음(git grep에 정의만 있음) | 없음 | 해당 없음(죽은 코드) | v2에 대응 코드 없음 | 도달 불가 |
| C121★ | 새 PR 폼 검증 실패 시 projects/fromProject/toProject/pullRequest를 null로 렌더해 NPE(500) | PullRequestApp.java:272-277, git/create.scala.html:43,44,68,69 | 실제(title/fromBranch/toBranch가 `@Required`라 비면 `form.hasErrors()`; `validateForm`은 flash만 넣음) | 없음 | 해결(오류 화면으로 바꿈) | PullRequestCreateController.kt:158-162 비어 있으면 `ErrorViews.badRequest`(400) | 정상(제목이나 브랜치를 비우고 제출. JS 검증을 거치면 닿지 않을 수 있음, 미확인) |
| C122 | `ProjectUser.isMember(userId, projectId)`에 인수 순서를 뒤바꿔 전달 | partial_state.scala.html:30, ProjectUser.java:122-128 | 실제 | R-43(1) | 재현 | PullRequestPageAssembler.kt:141-142 (`existsByUserIdAndProjectId(to.id, viewer.id)`) | 정상 |
| C123★ | 이벤트 li는 `id="comment-N"`인데 날짜 링크는 `#event-N` | partial_pull_request_event.scala.html:56,60 | 실제 | 없음 | 재현 | pullrequest/partial-timeline.html:10, 13 | 정상 |
| C124 | 프로젝트 검색 결과에 페이지 이동 요소가 없어 21건째부터 UI로 접근 불가 | search/partial_projects.scala.html (전체, 쪽 이동 없음) | 실제 | R-50(1) | 재현 | search/partial-projects.html에 쪽 이동 요소 없음(`page` 문자열 0건) | 정상 |
| C125 | `posting.noAuthor` 키가 메시지 파일에 없음 | search/partial_post_comments.scala.html:45 | 실제 | R-50(2) | 재현 | SearchRowAssembler.kt:83, 106, 111 (키 이름 그대로 출력) | 정상(작성자 없는 게시글 댓글) |
| C168★ | 사용자 페이지의 닫힘 이슈 탭 빈 메시지 조건이 전체 `issues.isEmpty`라 닫힌 이슈만 없어도(또는 열림 탭에서 열린 이슈만 없어도) 메시지가 안 나옴. 파일은 `user/view.scala.html`(candidate의 view.scala.html:143) | user/view.scala.html:131-135,142-147 | 실제(표시 불일치) | 없음 (`my-issues.md:29`는 다른 화면) | 재현 | `templates/user/view.html:96,106`이 `activity.issues.isEmpty()`로 두 탭 모두 같은 조건 | 정상 |
| C171★ | 조직 설정 폼이 검증 실패(이름 중복) 시 제출 값이 아니라 DB 값을 다시 표시. candidate의 setting.scala.html:58,68은 `organization/setting.scala.html`(project/setting도 같은 모양, 단 project의 오류 재표시는 따로 확인 안 함) | organization/setting.scala.html:58,68; OrganizationApp.java:404-407 | 실제 | 없음 | 재현 | `OrganizationSettingController.kt:79,129-130`이 DB 조직으로 `settingView`를 그리고 `organization/setting.html:45,55`가 `org.name`, `org.descr`를 그대로 씀 | 정상 |
| C179★ | `Q` 단축키가 로그인 ID `manager`일 때만 켜지고, 그때 출력되는 `"Q": …`는 앞 쉼표가 없어 단축키 맵이 문법 오류 | projectMenu.scala.html:148-151 (다른 항목은 `,"M"` 식으로 쉼표가 앞에 있음) | 실제(둘 다) | R-11(`refactoring-backlog.md:21` 부근)은 역할/로그인 ID 혼동만 적고 쉼표 누락은 안 적음 | 재현(조건)·해결(쉼표) | 조건은 `ProjectViewContext.kt:160`이 재현. 쉼표는 `templates/project/menu.html:85-87`이 `,"Q"`로 앞 쉼표를 붙여 v1.6과 다름(결함 비재현) | 희귀(로그인 ID가 `manager`인 사용자) |
| C180 | 폴더 판정 `eq(" folder")`(앞 공백)라 항상 거짓, 폴더 링크에 `#cb-` 앵커가 안 붙음 | partial_view_folder.scala.html:31 | 실제 | R-38(`refactoring-backlog.md:45`) | 재현 | `CodePageAssembler.kt:18` 주석과 `code/view.html:52`에 앵커 없음 | 정상 |
