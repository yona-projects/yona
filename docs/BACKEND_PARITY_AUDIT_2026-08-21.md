# yona → yona 백엔드 전수 감사 (2026-08-21)

## 감사 개요

- **목적**: yona(레거시, Java/Play Framework) 백엔드 전체를 yona(신규, Kotlin/Spring Boot)로 필드 단위·분기(비즈니스 로직) 단위까지 빠짐없이 1:1 대조 확인.
- **범위**: 백엔드 로직만. Play scala.html 템플릿, yona Thymeleaf 템플릿 등 UI 마크업은 감사 대상에서 제외(컨트롤러의 라우팅/권한체크/비즈니스 분기/필드 매핑은 백엔드로 간주해 포함).
- **방법**: 도메인 14개로 분할(실제 취합된 섹션은 13개)해 각 도메인을 전담하는 에이전트가 yona 원본 파일을 전부 직접 읽고, yona의 대응 파일을 찾아 필드·분기 단위로 대조. 아키텍처 변화(Play Form validation → Spring Bean/수동 검증, Ebean 액티브레코드 → Spring Data JPA, Play 라우팅 → Spring MVC 등)로 인한 "다른 방식으로 동등하게 구현됨"은 결손으로 세지 않음. 각 발견은 `docs/parity/index.md`와 대조해 이미 추적 중인 항목인지, 신규 발견인지 구분.
- **감사 대상 도메인**: 게시판(Board/Posting), PR/코드리뷰, 프로젝트, 사용자/인증, 조직, 알림/메일, 마일스톤, 첨부파일, 감시(Watch)/즐겨찾기, 웹훅, 코드/Git/SVN, 사이트관리/통계/검색, 접근제어/검증 핵심 유틸. (이슈 도메인은 이 감사 이전에 이미 세션 전체에 걸쳐 매우 상세히 별도 대조돼 있어 이번 통합 감사의 별도 섹션으로는 포함하지 않았다 — 아래 결과에 이슈 도메인 전용 섹션이 없는 것은 누락이 아니라 이 사유다.)
- **주의**: 아래 "신규 발견"은 이번 감사에서 코드를 읽고 사실 관계를 확인한 결과이며, 백로그 등록 여부·우선순위·착수 여부는 이 문서에서 결정하지 않았다. 제안 심각도(P0/P1/P2)는 각 도메인 보고서의 서술을 근거로 통합 과정에서 분류한 것으로 최종 판단이 아니다.

---

## 요약

이번 감사는 yona(레거시)→yona(신규) 백엔드 포팅 전수 감사 중 도메인별로 작성된 개별 보고서 13건을 정합·통합한 결과다(원 요청에서는 14건으로 표기되었으나, 실제로 취합된 도메인 섹션은 게시판/PR·코드리뷰/프로젝트/사용자·인증/조직/알림·메일/마일스톤/첨부파일/감시·즐겨찾기/웹훅/코드·Git·SVN/사이트관리·통계·검색/접근제어·검증 유틸의 13개다).

- **감사 도메인 수**: 13개
- **완전히 이식됨으로 확인된 파일(그룹) 수**: 약 80개(대략치 — 파일 단위가 아니라 밀접하게 묶인 파일군/유틸 단위를 1건으로 셀 때의 근사값이며, PR/코드리뷰 도메인은 보고서에 명시된 대로 18개 yona 파일이 대상이었다)
- **신규 발견 결손 총 개수**: 59건(치명도별 하위 분류는 아래 표 참고). 이 외에 "정식 결손으로 등록할 정도는 아니다/경미하여 참고로만 기록한다"고 각 도메인 보고서가 명시적으로 표시한 부수적 관찰 약 8건이 별도로 있음(제안 심각도 표에는 포함하되 별도 표기함).
  - **P0급(보안·데이터손실)**: 7건
  - **P1급(기능결손)**: 32건
  - **P2급(경미)**: 20건
- **이미 parity/index.md에 추적 중이던 항목과 겹치는(재확인된) 발견 개수**: 도메인별 "이미 추적 중인 관련 항목" 절에 나열된 P-번호를 단순 합산하면 약 156회 언급된다. 다만 P1-27(알림 파이프라인), P1-85(AccessControl 중앙화), P2-02(DiffUtil/History), P2-16(리소스 접근제어 전수조사), P0-09(프로젝트 이관) 등 여러 항목이 도메인 간 공유 인프라라는 이유로 2~3개 도메인 보고서에서 중복 인용되어, 고유 P-번호 기준으로는 대략 110~130개 내외로 추정된다(정확한 중복 제거는 이번 통합 작업 범위를 넘어서 수행하지 않았으며, 이는 근사치임을 명시한다). 이 항목들은 전부 "완료"로 표시되어 있었고, 이번 재대조에서도 실제 코드로 재확인되었다.

**특별히 주목할 사항**: 조직(Organization) 도메인의 신규 발견 8번(`OrganizationController.kt`의 REST API에 사이트매니저 우회 로직 부재)은 parity/index.md의 **P2-16 항목이 이미 "ORGANIZATION은 문제없음"으로 종결 처리한 판정과 정면으로 배치**된다. 이 건은 판단을 내리지 않고 사실만 병기했으므로, P2-16 재검토가 필요하다.

---

## 게시판(Board/Posting) 도메인

### 완전히 이식됨

- **`Posting.java` → `Posting.kt`/`AbstractPosting.kt`**: title/body/history/createdDate/updatedDate/authorId·authorLoginId·authorName/project/number/numOfComments/notice/readme/labels(ManyToMany)/parent(OneToOne) 필드 전부 확인. `computeNumOfComments()`(댓글수 파생 로직)는 `CommentServiceImpl`에서 `countByPostingId()`로 저장 시점마다 재계산(P1-19)하는 방식으로 동등 구현됨.
- **`PostingComment.java` → `PostingComment.kt`**: contents/createdDate/authorId·authorLoginId·authorName/projectId/posting/parentComment 필드 확인.
- **`BoardApp.java`/`BoardApi.java` → `BoardController.kt`/`BoardViewController.kt`**: 목록/상세/생성/수정/삭제/댓글 CRUD, 낙관적 충돌 감지(`isModifiedByOthers`, P1-107), 라벨 일괄 교체 API(P0-15), 알림 발행(신규글 P1-18, 수정 시 옵션 체크박스 연동 P1-44), TitleHead 색인, 본문 변경 시 history diff 누적(P2-02) 모두 실제 코드로 확인.
- **`CommentApp.java`**: 실제 로직은 `COMMIT_COMMENT`/`REVIEW_COMMENT` 삭제 전용(PR 도메인)이며 Board/Posting 댓글과 무관함을 확인. yona에서 PostingComment 삭제는 `CommentController.deletePostingComment`가 담당하고 있어 대응 관계가 올바름(중복 이식 불필요).

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경

- Play `Form<Posting>`/`Form<PostingComment>` 바인딩 → Kotlin data class(`PostingForm`, `CreatePostingRequest` 등) + 수동 검증 — 프레임워크 차이로 인한 정상적인 재구현.
- Ebean `Posting.finder`/`PostingComment.find` 정적 메서드 → `PostingRepository`/`PostingCommentRepository` — 아키텍처 전환, 문제없음.
- `diff_match_patch` 기반 이력(history) 로직 → `HistoryUtil.appendHistory` — 이미 P2-02로 완료, 원본 알고리즘 그대로 이식됨을 로그로 확인.
- `AbstractPosting.isPublish`(초안→발행 전환 시 history 초기화) — Board 게시글 폼에는 애초에 이 플래그를 세팅하는 경로가 없어(이슈 전용 기능) 미이식이 문제되지 않음. PARITY_BACKLOG에도 "적용 지점 없음"으로 명시적으로 범위 조정됨.
- 번호 채번 동시성 재시도(`AbstractPosting.save()`의 `PersistenceException` catch → `fixLastNumber()` 후 재시도)가 `PostingServiceImpl.createPosting()`에는 없고 단순 `project.lastPostingNumber + 1` 증가만 있음 — 트래픽이 몰릴 때만 드러나는 엣지케이스이며 JPA 전환 자체는 아키텍처 차이로 볼 수 있으나, 재시도 안전망 자체가 사라진 점은 가볍게만 언급.

### 결손 발견 (신규)

1. **README 게시글 중복 생성 방지 로직 누락** — `BoardApp.java:233-240`(`newPost`)는 `readme=true`로 글쓰기 시 `Posting.findREADMEPosting(project)`로 기존 README 게시글 존재 여부를 확인해, 있으면 새로 만들지 않고 수정 화면으로 리다이렉트한다. `BoardViewController.createPost`(yona)는 이 확인 없이 매번 새 `Posting`을 생성 → 반복 작성 시 README 게시글이 계속 중복 누적됨.
2. **ISSUE_TEMPLATE.md 온라인 커밋 write-path 누락** — `BoardApp.java:242-245`: `issueTemplate=="true"`면 `Posting`을 저장하지 않고 곧장 `ISSUE_TEMPLATE.md`에 커밋한다. yona `createPost`(POST)에는 이 분기 자체가 없어(`PostingForm`에 필드도 없음) 제출하면 그냥 평범한 `Posting`이 DB에 저장돼버려 실제 커밋이 전혀 일어나지 않는다.
3. **임의 텍스트 파일 온라인 커밋 write-path 누락** — `BoardApp.java:247-251`: 코드 브라우저 "편집" 링크가 게시판 새 글 폼을 재사용해 실제 git 커밋을 수행한다. yona `createPost`(POST)에는 이 커밋 로직이 전혀 없어 제출 시 일반 게시글로 저장된다(2, 3은 같은 뿌리의 결손).
4. **조직(Organization) 게시판 목록의 프로젝트 가시성 필터 누락** — `BoardApp.SearchCondition.asExpressionList(Organization)`은 `organization.getVisibleProjects(currentUser)`로 접근 가능한 프로젝트만 필터링한다. `OrganizationViewController.organizationBoards`(yona)는 가시성 필터 없이 `org.projects`를 그대로 사용해, 접근 권한이 없는 비공개 프로젝트 게시글까지 조직 게시판 목록에 노출될 수 있다(**접근제어 회귀**). `projectNames`/검색어(`filter`)/정렬(`orderBy`/`orderDir`) 파라미터도 전부 미이식.
5. **새 댓글 알림의 "인용 이전 내용"(oldValue) 로직 없음** — `BoardApp.AddPreviousContent()`/`getPrevious()`가 담던 인용 컨텍스트가 `CommentServiceImpl.createPostingComment`/`createIssueComment`에는 없어, 새 댓글 알림에서 인용 블록이 빠진다.
6. **댓글 대댓글(parentCommentId) 생성이 REST API로 노출 안 됨** — `PostingComment.parentComment`와 서비스 파라미터는 있으나 `CommentController.CommentRequest` DTO에 필드가 없어 항상 `null`로만 호출된다.
7. **공개 프로젝트 비멤버의 게시글 작성 권한이 yona보다 과도하게 제한됨** — yona `AccessControl.isProjectResourceCreatable()`은 공개 프로젝트 비멤버 로그인 사용자도 `BOARD_POST`/`NONISSUE_COMMENT` 작성을 허용한다(`BOARD_NOTICE`만 예외). yona `BoardController.checkWritePermission`/`BoardViewController`는 공지 여부와 무관하게 항상 "프로젝트 멤버이거나 그룹멤버"를 요구해 공개 프로젝트 비멤버는 일반 게시글조차 작성할 수 없다(권한이 좁아진 회귀). 댓글 작성 권한(`Operation.READ` 기준)은 이 문제가 없음.

### 이미 parity/index.md에 추적 중인 관련 항목

P0-15, P1-18, P1-19, P1-44, P1-83, P1-107, P2-02 — 모두 완료 확인. 위 신규 발견 1~7은 이 항목들 및 전체 백로그 문서 어디에도 등록돼 있지 않다.

---

## PR/코드리뷰 도메인

*(감사 대상 18개 yona 파일을 전부 Read로 대조. yona 쪽 대응 파일(`domain/pullrequest/*.kt`, `domain/support/*`, `web/PullRequest*.kt`, `web/Review*.kt`, `web/CommentThreadController.kt`)을 Read/Serena로 대조.)*

### 완전히 이식됨
- **PullRequest.java** ↔ `PullRequest.kt` + `PullRequestService(Impl).kt` + `PullRequestRepository.kt`: 필드, 생성/수정, 3분기 머지 구조, 리뷰어 추가·제거, 상태전이, 이슈 참조 이벤트 확인.
- **PullRequestCommit.java** ↔ `PullRequestCommit.kt`: `getCommitShortMessage`/`bindPullRequestCommit` 로직 동일.
- **PullRequestEvent.java** ↔ `PullRequestEvent.kt` + `PullRequestEventRecorder.kt`: draft-time 이벤트 병합/취소까지 확인.
- **PullRequestMergeResult.java** ↔ `PullRequestMergeResult.kt`: 필드 확인. saveCommits/findNewCommits/updatePriorCommits는 `PullRequestServiceImpl.updatePullRequestCommits()`로 이관.
- **CommentThread/CodeCommentThread/NonRangedCodeCommentThread/SimpleCommentThread**: 상속·판별자 구조, isOutdated(P1-20 완료) 확인.
- **CommitComment/ReviewComment/Comment(support)/CodeRange**: 필드, isFor/endsWith, hasLocation 등 확인.
- **PullRequestApp.java**의 대부분 액션은 `PullRequestController`/`PullRequestViewController`/`ReviewViewController`/`PullRequestServiceImpl`에 분산 대응됨을 확인.
- **ReviewApp.java** review/unreview ↔ `ReviewApiController` + `PullRequestService.addReviewer/removeReviewer`(P1-49/62).
- **ReviewThreadApp.java**의 목록·엑셀 다운로드 ↔ `ReviewThreadController`가 컬럼 구성까지 재현.
- **CommentThreadApp.java**의 알림 실패 try/catch(무시) ↔ `CodeReviewServiceImpl.updateThreadState()`(P1-79).

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- Ebean 액티브레코드 → JPA Repository + Service 계층 전환은 전반적으로 문제없음.
- `PullRequest.commentThreads` 양방향 컬렉션은 없고 `CommentThreadRepository` 조회로 대체(Spring Data 관용구 전환).
- `getWatchers()`(커밋 단위 감시자)는 `CodeReviewServiceImpl.getCommitWatchers()`로 정교하게 재구현.
- `isAcceptable()`류 UI 헬퍼는 결손 아님(yona `merge()`의 리뷰어 수 검증이 동등).

### 결손 발견 (신규)

1. **`PullRequest.getCodeCommentThreadsForChanges(commitId)` 필터링 로직 완전 누락** — yona는 commitId 일치, 머지베이스 일치, `noChangesBetween()`으로 표시할 스레드를 걸러낸다. yona `PullRequestViewController.viewPullRequest(tab=changes)`/`viewChangesInternal()`은 `commentThreadRepository.findByPullRequest(pullRequest)`로 PR의 **모든** 스레드를 무조건 노출한다 — 다른 커밋의 리뷰 스레드가 섞여 보이고 outdated 스레드도 항상 표시됨.
2. **`PullRequestRepository.findRelatedPullRequests()`의 JPQL 연산자 우선순위 버그** — `(fromMatch OR toMatch) AND 상태필터` 의도가 실제로는 `fromMatch OR (toMatch AND 상태필터)`로 평가되어 CLOSED/MERGED PR도 브랜치 삭제 시 연관 PR 처리 대상에 포함될 수 있다.
3. **`CodeReviewServiceImpl.hasPermission()`(리뷰/커밋 댓글 삭제)이 yona보다 과도하게 제한적** — yona는 프로젝트 멤버 전원에게 DELETE를 허용하나 yona는 "작성자 본인 또는 MANAGER"만 허용. 이미 완료된 P1-90~95와 동일 유형 버그이나 이 파일만 조사망에서 빠졌다.
4. **`CommentThreadController.open()`/`close()`에 권한 체크가 전혀 없음** — yona는 매니저·스레드 작성자·프로젝트(또는 그룹) 멤버만 허용하나(비멤버 403), yona는 로그인 여부만 확인하고 바로 상태변경 — 무관한 임의 사용자가 다른 프로젝트의 리뷰 스레드를 열고 닫을 수 있다.
5. **`PullRequest.getCommitComments()`(SVN 커밋 코멘트 ↔ PR 커밋 매핑) 대응 부재** — SVN+PR 조합에서만 커밋 코멘트가 PR 화면에 노출되지 않는 제한적 영향.

### 이미 parity/index.md에 추적 중인 관련 항목
P1-20, P1-24, P1-15, P1-27, P1-49, P1-62, P1-52, P1-53, P1-68, P1-71, P1-79, P1-90~P1-98, P1-105, P1-106 — 단 위 3·4번이 지적하는 COMMENT_THREAD/REVIEW_COMMENT/COMMIT_COMMENT(`CodeReviewServiceImpl` 경로)는 P2-15/16 전수조사 범위에 포함되지 않았음.

---

## 프로젝트(Project) 도메인

### 완전히 이식됨
- `app/models/ProjectUser.java` → `ProjectUser.kt` + `ProjectUserServiceImpl.kt`(enroll/cancelEnroll/accept/reject/addMember/updateMemberRole/removeMember, 오너 보호 가드 포함)
- `app/models/ProjectTransfer.java` → `ProjectTransfer.kt` + `ProjectServiceImpl.kt`(requestNewTransfer/acceptTransfer, P0-09/P1-72~75)
- `app/models/PushedBranch.java` → `domain/vcs/PushedBranch.kt`(짧은 브랜치명 저장 차이 주석 처리됨, P1-24)
- `app/models/RecentProject.java` → `RecentProject.kt` + `RecentProjectRepository.kt`(MAX 30건, delete-then-insert, 실패 무시까지 동일)
- `app/models/Property.java` → `domain/support/Property.kt`(P1-55 상세 검증됨)
- `app/controllers/EnrollProjectApp.java` → `ProjectUserServiceImpl.enroll/cancelEnroll`(P1-16)
- `app/controllers/ImportApp.java` → `ImportApiController.kt` + `ImportViewController.kt`(owner 검증, git clone 인증 실패 분기, 실패 시 정리까지 대조 확인)
- `app/controllers/MigrationApp.java` → `MigrationService.kt` + `MigrationApiController/ViewController.kt`(GitHub OAuth, 게이트, 이슈/게시글/마일스톤/라벨 export, 링크 치환까지 로직 일치)

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- `ProjectMenuSetting` OneToOne → `Project.kt`의 6개 boolean 인라인화(값 매핑은 `ProjectServiceImpl.updateProject()`에서 확인).
- `CacheStore` 기반 조회 캐시는 이식되지 않았으나 결과 정확성엔 영향 없음(순수 성능 이슈).
- `isOnlyManager(userId)` → `SiteService.isOnlyManager()`로 로직 동일 확인.

### 결손 발견 (신규)

1. **`Project.delete()`의 전체 계단식 삭제가 `ProjectServiceImpl.deleteProject()`에 이식되지 않음** — yona는 ProjectTransfer/포크 PR/CommentThread/PR/Issue/IssueLabelCategory/Assignee/Webhook/Posting/Label까지 명시적으로 순회 삭제한다. yona `deleteProject()`는 멤버 행만 지우고 `projectRepository.delete(project)`만 호출 — cascade/`@OnDelete` 선언이 전혀 없어(`Posting.kt`/`Issue.kt`/`Milestone.kt`/`Webhook.kt`/`vcs/PushedBranch.kt`/`Assignee.kt`/`IssueLabel.kt`/`CommentThread.kt` 전부 확인), `ddl-auto: update` 환경에서 콘텐츠가 있는 프로젝트 삭제 시 FK 위반 실패 또는 고아 행이 남는다. `SiteApiController`/`ProjectController`/`ProjectViewController` 3곳 모두 동일하게 얕은 삭제만 호출.
2. **`Project.getAssignableUsers()`(조직 그룹 기반 담당자 후보)가 `ProjectMemberController.assignableUsers()`에 이식되지 않음** — yona는 조직 소속 프로젝트에서 PRIVATE면 조직 관리자를, PROTECTED/PUBLIC이면 조직 멤버 전원을 후보에 추가하고 사이트매니저 자신도 포함한다. yona는 프로젝트 멤버만 후보로 써 조직 소속 프로젝트의 담당자 후보 검색이 축소됨.
3. **(경미, 참고)** `MigrationApp.gatheringUserProjects()`의 사이트매니저 바이패스 범위가 yona `MigrationService.getMigrationProjects()`에서 MANAGER 역할 프로젝트로 축소됨 — 극히 드문 케이스라 정식 결손 등록 대상은 아니라고 판단, 참고로만 기록.

### 이미 parity/index.md에 추적 중인 관련 항목
P0-09, P1-13, P1-14, P1-15, P1-16, P1-42, P1-43, P1-57, P1-58, P1-72~76, P1-85, P1-87, P1-88, P1-97, P1-98, P1-100, P1-103 — 모두 실제 코드 위치까지 확인, 기록된 완료 상태와 일치.

---

## 사용자/인증 도메인

### 완전히 이식됨
- `UserVerification.java` → `domain/user/{UserVerification,UserVerificationRepository}.kt` + `UserServiceImpl.verifyUser/createVerification/sendVerificationEmail` — 24시간 만료·검증 후 삭제까지 확인.
- `UserSetting.java` → `domain/user/{UserSetting,UserSettingRepository}.kt`(P2-11) 확인.
- `UserProjectNotification.java` → `domain/notification/UserProjectNotification.kt` + `WatchServiceImpl`(P1-22) — `isNotifiedByDefault`, 뮤트 토글 확인.
- `PasswordResetApp.java`/`utils/PasswordReset.java` → `web/PasswordResetController.kt`/`domain/user/PasswordResetServiceImpl.kt` — 인메모리 해시맵, 1시간 만료 동일 확인.
- `LdapService.java` → `domain/user/{LdapService,LdapQueryBuilder,LdapUserProvisioningService}.kt`(P1-01) 확인.
- 이메일 관리(`UserApp.addEmail/deleteEmail/sendValidationEmail/setAsMainEmail`) → `UserServiceImpl` 동명 메서드로 확인(단, 아래 참고).

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- **`SiteAdmin.java`(별도 조인 엔티티) → `User.state == UserState.SITE_ADMIN`(같은 필드에 통합)**: yona는 계정 상태와 사이트관리자 여부가 직교하나, yona는 동일 `state` 필드로 결합해 "잠긴 사이트관리자" 상태를 표현할 수 없다. 의도적 재설계로 보이나 원본과 다른 상태공간이므로 기록.
- `UserCredential.java`/`LinkedAccount.java`: play-authenticate 전용 필드 제외하고 핵심 로직만 이식(P1-03/P1-56 완료 로그에 명시).
- `NullUser`/`CandidateUser`/`AuthInfo`/`UserIdent`: 언어/프레임워크 차이로 인한 등가 구현(단, `UserIdent`의 "삭제된 사용자 이름 스냅샷" 패턴이 각 엔티티에 개별 이식됐는지는 이번 감사 범위 밖).
- **Gravatar 아바타 폴백 제거**: yona는 이메일 MD5 기반 Gravatar 폴백, yona는 정적 기본 이미지만 반환. 외부망 호출 제거는 합리적이나 PARITY_BACKLOG에 별도 항목 없음(경미).
- **이메일 확인 시 자동 로그인 미이식**: yona `confirmEmail()`은 세션을 즉시 로그인 상태로 만들지만 yona는 boolean만 반환. 보안상 개선일 수 있으나 원본과 동작이 다름.
- **LDAP `USE_EMAIL_BASE_LOGIN` 적용 범위 확대**: yona는 Basic Auth 경로에만 적용하나 yona는 웹 로그인 폼에도 동일 적용 — 범위가 넓어진 일반화.

### 결손 발견 (신규)
1. **`UserApi.java`의 관리용 REST API 3종이 yona에 대응물이 없음**:
   - `newUser()`: 사이트관리자 전용 JSON 벌크 사용자 생성 API(도메인 allowlist, 중복 409, 도메인거부 403) — yona엔 단건 폼 가입만 있음.
   - `newToken()`: loginId/password로 세션 없이 access_token 발급하는 API 전용 로그인 — yona 토큰 재발급은 이미 인증된 세션이 있어야 호출 가능해 대체 불가.
   - `users()`/`updateUserState()`(사이트매니저 전용 사용자 전체 조회/상태변경, SITE_ADMIN 승격 방어 포함) — yona엔 목적별 엔드포인트만 있고 이 범용 JSON API는 없음.
2. **`isSiteManager` 하드코딩 `loginId == "admin"` 분기**(`domain/user/User.kt:111`, `UserDetailsServiceImpl.kt:19`) — yona엔 없는 로직. `loginId=="admin"`이면 `state`와 무관하게 항상 `isSiteManager=true`. "admin"이 예약어라 실질 위험은 낮으나 원본에 없는 로직이며 상태와 독립적으로 항상 관리자 권한을 부여한다는 점에서 기록.

### 이미 parity/index.md에 추적 중인 관련 항목
P0-13, P1-01, P1-02, P1-03, P1-04, P1-56, P1-77, P1-104, P1-108, P2-03, P2-09, P2-11 — 모두 실제 코드에서 구현 재확인.

---

## 조직(Organization) 도메인

### 완전히 이식됨
- `Organization.java` → `Organization.kt`: 필드 전체 확인.
- `OrganizationUser.java` → `OrganizationUser.kt` + `OrganizationUserRepository.kt`: `isAdmin`/`isMember`/`findAdminsOf`/`assignRole`/`exist` 등 대응 확인.
- `FavoriteOrganization.java` → `FavoriteOrganization.kt` + `FavoriteOrganizationRepository.kt`: 필드 및 토글 로직 확인.
- `OrganizationApp.java`의 멤버 추가/삭제/역할변경, 최소 1인 관리자 유지, 조직명 중복검사, 조직 삭제 시 하위 프로젝트 검사 — 대조 확인. 게스트 멤버추가 차단(P1-17), 조직명 형식검증(P1-108), 예약어 검증(P2-01)은 완료 재확인.

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- Play `Form<Organization>` 바인딩 → `OrganizationServiceImpl.createOrganization()`의 수동 예외 기반 검증으로 대체(REST/MVC 두 컨트롤러가 모두 이 서비스로 수렴).
- Ebean `Finder` → Spring Data JPA `OrganizationRepository`/`OrganizationUserRepository`로 대체, 1:1 대응.
- `Organization.findByName`의 대소문자 무시(ieq) 여부는 DB 콜레이션에 따라 갈릴 수 있어 코드만으로 단정 불가 — 참고로만 남김.

### 결손 발견 (신규)
1. **비공개 프로젝트 가시성 필터링 미이식** — yona `Organization.getVisibleProjects(User)`는 관리자/사이트매니저는 전체, 멤버는 공개+본인멤버, 그 외는 `HIDE_PROJECT_LISTING` 미설정 시 공개 프로젝트만 노출하도록 분기한다. yona `OrganizationViewController.organizationHome()`은 필터 없이 조직 전체 프로젝트(비공개 포함)를 그대로 모델에 담는다.
2. **`HIDE_PROJECT_LISTING` 및 `@GuestProhibit` 미이식(조직 목록)** — yona `orgList()`는 플래그 true면 403, 게스트 차단. yona `orgList()`엔 두 체크 모두 없고, `HIDE_PROJECT_LISTING` 개념 자체가 yona에 없음.
3. **조직 생성 시 `@GuestProhibit` 미이식** — yona `createOrganization()`은 로그인 여부만 확인하고 게스트 검사가 없음.
4. **`enroll()` 중복 신청 가드 누락(알림 중복 발행)** — yona는 이미 대기중 신청이면 재신청/재알림을 건너뛴다. yona `OrganizationServiceImpl.enroll()`은 실제 멤버 여부만 검사해 버튼을 여러 번 누르면 알림이 매번 중복 발행됨. **Project 도메인의 P1-16과 동일 유형이나 대칭 수정이 적용되지 않음.**
5. **`cancelEnroll()` 무조건 알림 발행** — 대기 신청이 있었는지 확인하지 않고 항상 취소 알림을 보냄. `isGuest`(비멤버) 가드도 없음.
6. **조직 로고 업로드 파일검증 미이식** — yona `isImageFile`/`LOGO_FILE_LIMIT_SIZE` 검증이 yona `updateOrganization()`/`AttachmentServiceImpl.store()` 어디에도 없음(전체 검색 0건).
7. **FavoriteOrganization 조직명 동기화 미이식** — 조직명 변경 시 `FavoriteOrganization.organizationName`(비정규화 필드) 갱신 로직이 없어, 즐겨찾기 레코드에 옛 이름이 남는다.
8. **REST API(`/api/organizations`)에 사이트매니저 우회 로직 부재** — yona `AccessControl.isAllowed()`는 최상단에서 사이트매니저 전역 우회를 적용하나, yona `OrganizationController.kt`(REST)의 `isOrgAdmin()`은 `ORG_ADMIN` 역할만 검사해 사이트매니저가 REST API로 설정변경/삭제 시 403이 반환될 것으로 보인다(MVC `OrganizationViewController.kt`는 각 엔드포인트마다 우회를 넣어 문제없음 — 두 컨트롤러 간 비대칭). **주의**: `parity/index.md` P2-16이 정확히 이 파일의 `isOrgAdmin`을 "문제없음"으로 종결 처리했으나 실제 코드와 배치되어 판단을 내리지 않고 사실만 보고함.

### 이미 parity/index.md에 추적 중인 관련 항목
P1-17(완료), P1-108(완료), P2-01(완료), P0-09(완료), P1-16(완료 — 위 신규 4·5번과 동일 유형·원인이나 Organization 쪽엔 대칭 적용 안 됨), P2-16(종결 "문제없음" — 위 신규 8번과 결론 상충, 재검토 필요).

---

## 알림/메일 도메인

### 완전히 이식됨
- **`MailRecipient.java`** → `domain/mail/MailService.kt`의 `data class MailRecipient` — To/Bcc 구성은 `NotificationMailDigestScheduler.kt`에서 확인.
- **`OriginalEmail.java`** → `domain/mail/OriginalEmail.kt`: 필드·유니크 제약 1:1 일치.
- **`Email.java`**(보조 이메일 검증) → `domain/user/Email.kt` + `UserServiceImpl.confirmEmail()`: `validate()`/`deleteOtherInvalidEmails()` 순서까지 정확히 재현.
- **`NotificationApp.java`** → `web/NotificationController.kt` + `web/IndexController.kt`: REST API와 페이지 컨트롤러로 자연스럽게 재구성됨.
- **`NotificationEvent.java`/`NotificationMail.java`의 파이프라인 본체**(발송 스케줄링, 이벤트 병합, 메시지/URL 리졸버, 도메인 허용목록, Reply-To, 다국어 그룹핑, recipient-limit 분할) — P0-01/P1-27/P1-28/P1-50/P1-51 등에서 이미 상세 대조·구현 완료된 것을 코드로 재확인.

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- Ebean 정적 파인더 → Spring Data 리포지토리, Play `Messages`/라우트 리버스 → Spring `MessageSource` — 정상적인 프레임워크 차이.
- **`Mention.java` 기반 이슈 검색**: yona는 사전 계산된 `Mention` 테이블로 조회하나, yona `UserViewController.kt`는 `IssueRepository.findMentionedByState()`로 LIKE 검색을 즉석 수행. 본인 리터럴 멘션 검색은 동등하나 그룹 멘션까지는 못 커버(아래 결손 참고).

### 결손 발견 (신규)
1. **`Mention.java` 엔티티 자체가 yona에 전혀 없음** — 멘션된 사용자를 리소스별로 저장하는 인덱스 테이블 대응물이 없음. 아래 2, 3번 결손의 근본 원인.
2. **그룹 멘션(조직/프로젝트) 미지원** — yona `getMentionedUsers(body)`는 `@word`를 조직명/프로젝트명(`owner/project`)으로 확장해 그 그룹 전 멤버를 수신자에 포함시킨다. 유일한 대응인 `CommentServiceImpl.extractMentionedUsers()`는 `findByLoginId()` 한 줄뿐이며, 정규식도 `/`를 포함하지 않아 `@owner/project` 형식 자체를 매칭하지 못한다.
3. **신규 이슈/게시글/PR 생성 시 본문 `@멘션` 알림 수신자 계산 누락** — yona `getReceivers(AbstractPosting)`/`getDefaultReceivers(PullRequest)`는 워처 집합에 `getMentionedUsers(body)`를 추가하나, `IssueServiceImpl.createIssue()`/`PostingServiceImpl.createPosting()`/`PullRequestServiceImpl.createPullRequest()` 어디에도 이 로직이 없음(댓글 작성 시 멘션 처리는 존재해 부분 이식 상태).
4. **프로젝트 가입 요청/취소 알림(`MEMBER_ENROLL_REQUEST`) 수신자가 워치 여부를 무시함** — yona는 Watch 중인 매니저만 거르나, yona `ProjectUserServiceImpl.getProjectManagers()`는 매니저 전원을 조건 없이 반환 — 언워치해도 알림이 계속 감.
5. **(부수, 저위험) 조직 가입 신청의 oldValue 상태 페어가 yona와 어긋남** — yona는 `CANCEL`↔`REQUEST` 대칭 페어링으로 30초 드래프트 창 내 "신청 직후 취소"를 상쇄하나, yona `OrganizationServiceImpl`은 `oldValue`가 정확한 역전 쌍이 아니라 이 최적화가 조직 가입에서는 발동하지 않는다(프로젝트 가입 쪽은 문제없음).

### 이미 parity/index.md에 추적 중인 관련 항목
P0-01~04, P1-14, P1-22, P1-25~28, P1-39, P1-42~44, P1-46, P1-48, P1-50, P1-51, P1-58, P1-60, P1-63, P1-69~71, P1-79, P1-88.

---

## 마일스톤 도메인

### 완전히 이식됨
- `app/models/Milestone.java` → `domain/milestone/Milestone.kt` + `MilestoneServiceImpl.kt` + `MilestoneRepository.kt`: 필드/메서드 단위 대조 완료.
- `app/controllers/MilestoneApp.java` → `web/MilestoneViewController.kt`: 액션 단위 대조 완료.
- `app/controllers/api/MilestoneApi.java` → `web/MilestoneController.kt`, `web/MigrationApiController.kt`: 대조 완료(단, 아래 결손 참고 — 대응 기능이 부분적으로만 존재).

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- 엔티티 연관관계(`Milestone.issues`) → `IssueRepository.findByMilestone()`류 리포지토리 쿼리로 대체, 결과 동등.
- 삭제 시 이슈 마일스톤 해제가 개별 update → 벌크 쿼리(`removeMilestoneFromIssues()`)로 대체 — 결과 동등, 오히려 효율적. 첨부파일 정리(`attachmentService.deleteAll`)까지 추가된 것은 개선.
- `until()`/`getDueDateString()` 문자열 조립은 템플릿으로 위임(감사 범위 밖).
- 권한 체크 방식(REST는 헬퍼형, 웹은 인라인형)은 legacy 규칙과 동일해 결손 아님.
- `Milestone.countOpened(Project)`, `Milestone.options(Long)`은 yona에서도 어디서도 호출되지 않는 죽은 코드 — 이식 대상 아님.

### 결손 발견 (신규)
1. **마일스톤 목록 정렬 기능 전체 누락** — yona `MilestoneApp.milestones()`는 `orderBy`/`orderDir`(기본 `dueDate`/`asc`)을 지원하며 `completionRate` 정렬은 인메모리 커스텀 정렬로 처리한다. 실제 UI에서 사용되는 기능이다. `MilestoneViewController.listMilestones()`(yona)는 `state`만 받고 정렬 파라미터 자체가 없으며 리포지토리 쿼리에도 정렬이 없다.
2. **마일스톤 상세 화면의 이슈 목록 정렬 누락** — yona `sortedByNumberOfOpenIssue()`/`sortedByNumberOfClosedIssue()`는 이슈 번호 내림차순 정렬 후 분리하나, yona `toViewDto()`는 `state`로 필터만 하고 별도 정렬이 없다(쿼리에도 `ORDER BY` 없음).
3. **`MilestoneApi.java`(벌크 마일스톤 임포트 API) 전체가 이식되지 않음** — GitHub 등 외부 저장소 마이그레이션 시 마일스톤을 JSON 배열로 일괄 생성하는 엔드포인트(`-_-api/v1/...`, per-item 에러 핸들링 포함)에 대응이 없다. yona엔 export 전용 조회와 단건 생성 API만 있음.
4. **due date 형식 오류 시 사용자 피드백 누락** — yona는 파싱 실패 시 저장을 막고 에러를 알리나, yona `createMilestone()`/`editMilestone()`은 `catch (e: Exception) { null }`로 조용히 삼켜 `dueDate=null`로 저장을 진행한다.
5. **(참고, 시스템 전반 갭)** 텍스트 초과 시 413 응답 누락 — 마일스톤뿐 아니라 yona 전반(`PullRequestApp`/`BoardApp`/`IssueApp` 등)에 공통이며 yona엔 전역 핸들러도 없음. 마일스톤 국한 문제는 아니라 참고로만 기록.

### 이미 parity/index.md에 추적 중인 관련 항목
P1-95(완료), P1-88(완료), P2-15(P1-95로 승격 처리), P0-05(첨부 업로드 패턴 기준선), group-member OR 확장 완료 로그(DELETE 전용 엔드포인트는 의도적 제외로 명시).

**참고**: `Milestone.NULL_MILESTONE_ID`(-1 센티널) 처리는 `IssueViewController.kt`/`IssueSpecification.kt`(Issue 도메인)에 정상 이식되어 있음(결손 아님).

---

## 첨부파일 도메인

### 완전히 이식됨
- `app/models/Attachment.java` — 필드 전부가 `domain/attachment/Attachment.kt`에 대응. `findByContainer`/`countByContainer`/`exists`/`fileExists`/`getFile`/`delete` 계열도 `AttachmentRepository`/`AttachmentServiceImpl`에서 확인.
- `app/controllers/AttachmentApp.java`의 `getFile()`(ETag/If-None-Match, Cache-Control, Content-Disposition)과 `getFileList()`, P1-96의 `isAllowedAttachment()` 권한 위임이 `web/AttachmentController.kt`에 배선되어 있음을 확인.
- "내 파일" 목록(`UserApp.myFiles`)이 `UserViewController.kt`에서 필터 유무 분기까지 동일하게 이식.
- 메일 첨부(`CreationViaEmail.saveAttachments`)는 P1-29/P1-47/P1-59로 완료 확인.

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- Play `Model.save()`/`Finder` → JPA `AttachmentRepository`/`@Transactional` — 프레임워크 차이로 문제없음.
- `AttachmentCache`(Ebean 인메모리 캐시) 부재는 순수 성능 최적화 생략.
- `findBy(fileName==null)` 분기는 yona 자체에서도 죽은 코드임을 확인 — 미이식이 결손 아님.

### 결손 발견 (신규)
1. **`Attachment.moveOnlySelected()`의 소유권 검증이 실사용 경로에서 완전히 우회됨(보안)** — yona에도 동일한 가드로 정확히 이식돼 있으나 코드베이스 전체에서 호출되지 않는 죽은 코드다. 대신 `IssueViewController.kt`/`MilestoneViewController.kt`/`BoardViewController.kt`가 각자 `temporaryUploadFiles`를 소유권/원 컨테이너 검증 없이 직접 재배선한다 — 로그인 사용자가 자신이 업로드하지 않은 임의 첨부파일 id를 자신이 새로 만드는 리소스로 강제 재배선시킬 수 있다.
2. **DB 행 단위 중복 방지(dedup) 미이식 + 응답 상태코드(201/200) 판정 로직 오류** — yona `save()`는 name+hash+containerType+containerId 동시 일치 시 기존 행을 재사용한다. yona `store()`는 dedup 없이 항상 새 행을 insert하며, `uploadFile()`의 `isNew` 판정은 이미 insert된 뒤 `existsByHash()`를 조회해 항상 `true`가 되어 `isNew`가 항상 `false` — 201 응답이 도달 불가능한 죽은 분기가 됨. 동일 파일 재업로드 시 매번 새 id의 행이 쌓임.
3. **`deleteFile()`이 `isAllowedAttachment()`를 재사용하지 않고 별도 구현 — ORGANIZATION/COMMIT_COMMENT/REVIEW_COMMENT/USER_AVATAR 컨테이너에서 원본 업로더 전용으로 과잉 제한** — READ에 쓰는 `isAllowedAttachment()`는 이 4개 타입의 컨테이너 위임 로직을 정확히 구현했으나 `deleteFile()`은 이를 쓰지 않아 로직이 drift됨. 예컨대 조직 로고를 올린 본인이 아닌 다른 조직 관리자는 yona라면 삭제 가능하지만 yona는 불가능(과잉 제한).
4. **MIME 타입 감지 방식이 콘텐츠 기반(Tika)에서 확장자 기반(JDK `probeContentType`)으로 바뀌어, 해시 파일명(확장자 없음)에 대해 사실상 항상 오탐 가능성** — Tika 의존성 자체가 코드베이스에 없음. 이미지/PDF가 인라인 대신 다운로드로 처리될 수 있음.
5. **임시 첨부 정리 스케줄러의 `createdDate` 비교 방향이 yona와 반대** — yona는 `createdDate >= (now - keepUpTime)`(최근 생성분)를, yona `AttachmentCleanupScheduler.kt`는 `createdDate < (now - keepAliveMillis)`(오래된 것)를 정리 대상으로 조회. 어느 쪽이 의도된 동작인지는 판단하지 않고 사실만 기록.

### 이미 parity/index.md에 추적 중인 관련 항목
P1-96(완료, 단 `deleteFile()` "문제없음" 판정 근거는 3개 타입만 검토한 것 — 위 결손 3번 참고), P1-85 1b(완료), P1-29/P1-47/P1-59(감사 범위 밖, 기 추적대로 완료만 확인).

---

## 감시(Watch)/즐겨찾기 도메인

### 완전히 이식됨
- **`Watch.java` / `Unwatch.java`** → `domain/watch/{Watch,Unwatch,WatchRepository,UnwatchRepository,WatchService,WatchServiceImpl}.kt`: 상호배타 갱신, `isWatching()`, `findWatchers()`/`findUnwatchers()`, `findActualWatchers()`(P1-21/P1-22 통합) 라인 단위 대조 완료.
- **`WatchProjectApp.toggle()`** → `WatchController.toggleProjectNotification()`: `isNotifiedByDefault`/`watchExplictly`/`unwatchExplictly`/`toggle()` 세 갈래 분기 완전 동일 확인.
- **`WatchApp.watch()/unwatch()`** → `WatchController.watchResource()/unwatchResource()`: 익명 차단, READ(→WATCH) 권한 실패 시 거부 확인.
- **`FavoriteIssue.java` / `FavoriteProject.java`** → `domain/user/{FavoriteIssue,FavoriteProject,...}.kt`, `FavoriteServiceImpl.kt`: 조회·toggle 확인.
- **`FavoriteIssue.getNumberOpenFavoriteIssues()`** → `FavoriteIssueRepository.countByUserIdAndIssueState()` 동등 포팅.
- **`RecentIssue.java`** → `domain/issue/{RecentIssue,RecentIssueRepository,RecentIssueService}.kt`: 중복 제거, `MAX_RECENT_LIST_PER_USER=100`, `deleteAll(user)`, 예외 방어 패턴(P1-09/P1-41) 모두 확인.

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- `Watch.watch(Resource)`가 내부에서 `currentUser()`를 조회하던 것을 컨트롤러에서 명시적으로 전달 — 정상적 변경.
- `FavoriteIssue`/`FavoriteProject`의 `.refresh()` 지연 캐시 → JPA lazy proxy로 대체, 결과 동일.
- `Operation.WATCH` 전용 도입은 전 구간 통일 적용으로 의도된 재설계로 판단(단, `Operation.WATCH`가 `Operation.READ`와 실제 동치인지는 이번 감사 범위 밖).
- `EventType.valueOf` 실패 시 예외 전파(yona, 사실상 500) → yona는 try/catch로 400 — 견고성 개선.

### 결손 발견 (신규)
1. **`/-_-api/v1/.../posts/{number}/watchers` 감시자 목록의 소스 범위 축소** — yona `WatcherApi.getWatchers()`는 작성자+담당자/투표자+프로젝트 감시자+명시적 감시자−비감시자를 합산하고 READ 권한 필터까지 적용한 "실제 감시자" 집합을 반환한다. yona `WatchController.getWatchers()`는 `watchService.findWatchers()`만 호출 — 명시적 Watch row만 반환하며 나머지는 전혀 포함하지 않고 권한 필터도 없다(`WatchControllerSpec.kt` 테스트도 이 단순화된 동작을 그대로 검증). 부수적으로 yona의 `LIMIT=100` 응답 자름이 없어 `watchersInList == totalWatchers`가 항상 성립.
2. **프로젝트 개명/소유자 이전 시 `FavoriteProject` 비정규화 필드 동기화 누락** — yona는 개명 시 즐겨찾기한 모든 사용자의 `FavoriteProject.owner`/`projectName`을 갱신하나, yona는 프로젝트 이전(`acceptTransfer()`) 로직에서 `favoriteProjectRepository`를 전혀 참조하지 않아, 즐겨찾기 응답이 재-즐겨찾기 전까지 옛 이름/소유자로 고정된다.

참고로 `FavoriteIssue.updateFavoriteIssue()`는 yona에서도 호출부가 없는 죽은 코드라 이식 누락 대상에서 제외했다.

### 이미 parity/index.md에 추적 중인 관련 항목
P1-09(완료), P1-21(완료), P1-22(완료), P1-41(완료), P1-50(완료), P1-67(코드 레벨 완료, UI 트랙 별도), P2-09(완료).

---

## 웹훅 도메인

### 완전히 이식됨
- **`app/models/Webhook.java`** → `domain/webhook/Webhook.kt` + `WebhookServiceImpl.kt` + `WebhookController.kt`: 743줄 전체 완독, 필드·메서드 대조.
- **`app/models/WebhookThread.java`** → `domain/webhook/WebhookThread.kt` + `WebhookThreadRepository.kt`: 82줄 완독, 필드 1:1 확인.
- **`app/models/PostReceiveMessage.java`** → `domain/event/GitPostReceiveEvent.kt`: `commands`/`project`/`user` 필드 일치. Akka 액터 메시지 → Spring `ApplicationEvent`로 대체된 정당한 형태 변경.

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- 모델→엔티티, `Date`→`Instant`, PK 생성 전략 차이는 프레임워크 차이.
- Play `WS` → `java.net.http.HttpClient(sendAsync)`, fire-and-forget 동작까지 동일하게 재현됨.
- `Webhook.create()`/`delete(Long,Long)`/`delete(Long projectId)`/`findByIds()`는 legacy 자체에서도 호출되지 않는 죽은 코드로 확인 — 이식 대상 아님. legacy `deleteWebhook`도 프로젝트 범위 검증이 없어 yona의 `deleteWebhook(id)`와 정확히 동치.
- `WebhookType` 파싱 실패 시 yona는 400, yona는 조용히 `SIMPLE`로 폴백(경미).
- `PULL_REQUEST_REVIEW_STATE_CHANGED`의 reviewed/unreviewed 구분은 yona 구조상 재현 불가능한 상태(경미, 별도 항목 미등록).

### 결손 발견 (신규)
1. **웹훅 텍스트 메시지에 리소스 링크가 전혀 없음** — yona `buildRequestMessage()`는 모든 이벤트 메시지에 클릭 가능 링크를 반드시 삽입한다. yona `buildTextMessage()`는 URL을 전혀 넣지 않는다(`NotificationUrlResolver`를 재사용하지 않은 것으로 보임).
2. **`DETAIL_SLACK` attachment의 상세 필드가 이슈/PR 모두 불완전** — yona는 이슈에 마일스톤+담당자+상태 3종을, PR에 sender/from-to 브랜치/본문을 채우나, yona는 이슈에 "State" 하나만, PR은 `when`-분기 자체가 없어 항상 빈 text/fields로 전송됨.
3. **`DETAIL_HANGOUT_CHAT` 스레드 키가 댓글 이벤트에서 부모 리소스가 아니라 댓글 자신으로 잘못 계산됨** — yona는 댓글이 달린 이슈/게시글 기준으로 스레드를 묶으나, yona는 댓글 자신을 resource로 사용해 매번 새 조회 키가 되어 스레드 그룹핑이 사실상 무력화됨.
4. **`payloadUrl`/`secret` 길이·필수 검증 미이식** — yona는 Form 바인딩으로 400+에러 메시지를 반환하나, yona `newWebhook()`엔 검증이 없어 DB 제약 위반 예외(비친화적 500)로 이어질 수 있다.

### 이미 parity/index.md에 추적 중인 관련 항목
P0-03(완료), P0-04(완료), P1-25(완료), P1-26(완료), P1-69(완료), P1-87(완료), P2-08(완료), P2-16(P1-87로 흡수, 종결).

---

## 코드/Git/SVN 도메인

### 완전히 이식됨
- **`app/utils/DiffUtil.java`** → `domain/support/DiffUtil.kt`: 상수·함수·헬퍼 전부 실제 파일 대조. `diff_match_patch.java`도 패키지만 바뀐 채 그대로 복사됨(P2-02 완료 로그에 원본 테스트 10개 이식·통과 기록).
- **`app/controllers/CodeApp.java`** → `web/CodeController.kt`(REST) + `web/CodeViewController.kt`(브라우저/히스토리/커밋 뷰): VCS 타입 체크, 멤버 전용 접근 체크, 빈 저장소 분기, MIME 감지, zip 다운로드까지 확인.
- **`app/controllers/CodeHistoryApp.java`** → `web/CodeHistoryController.kt` + `web/CodeViewController.kt`: git/svn 분기, 404 처리까지 확인.
- **`app/controllers/BranchApp.java`** → `web/BranchViewController.kt` + `web/BranchApiController.kt`: head 브랜치 제외 필터링 확인.
- **`app/controllers/CompareApp.java`** → `web/CompareViewController.kt`: git/svn 분기, null 체크 확인.
- **`app/controllers/GitApp.java`** → `config/GitServletConfig.kt` + `config/git/{GitAuthorizationFilter,GitProjectVisitRecorder}.kt`: 서비스 판별, 권한 승격, 방문 기록 확인.
- **`app/controllers/SvnApp.java`** → `web/SvnController.kt` + `config/svn/SvnAuthorizationFilter.kt` + `web/SvnServletRequestWrapper.kt`: WebDAV/READ-UPDATE 판별, 게스트 차단 확인.
- **`app/utils/GitUtil.java`** → `domain/vcs/BareCommit.kt`(commitTextFile 부분): 커밋 로직 자체는 확인(단, 아래 결손 참고).

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- Play `Cache.get/set` 기반 메타데이터 캐싱 부재 — 성능만 영향, 결손 아님.
- `CodeApp.getURL`류는 yona에서도 뷰 전용 헬퍼로 확인되어 감사 대상 아님.
- 커밋 단위 코드 리뷰 댓글 아키텍처(git 이중 구조 → 단순 `CommitComment` 통일)는 **P0-16 완료 로그에서 이미 검토·승인된 단순화**이므로 신규 결손 아님.
- `GitApp.isCharsetAllowed`(구버전 git 클라이언트 호환)는 미이식이나 실질 영향 미미.
- `GitUtil.getReadTextFile`의 charset 자동 감지 → yona는 UTF-8 고정(참고용, 신규 백로그 항목화 보류).

### 결손 발견 (신규)
1. **`GitUtil.commitTextFile`의 브랜치 지정 기능 소실** — yona는 `post.branch`로 지정된 브랜치에 커밋하나, yona `domain/vcs/BareCommit.kt`는 `refName`이 `"refs/heads/master"`로 하드코딩돼 바꿀 수단이 없다. 기본 브랜치가 master가 아닌 프로젝트에서 README 온라인 편집 시 항상 master에 커밋됨.
2. **빈 저장소(커밋 0개) 히스토리 페이지의 `NoHeadException` 미처리** — yona는 이 예외를 잡아 우아하게 처리하나, yona엔 catch가 없고 전역 `@ExceptionHandler`도 없어 500 오류로 전파될 가능성이 높다.
3. **`BranchApiController`의 Git 전용 가드 누락** — yona는 클래스 레벨 `@IsOnlyGitAvailable`로 3개 액션 모두 SVN에서 차단되나, yona는 `BranchViewController.branches`에만 체크가 있고 `BranchApiController.setAsDefault`/`deleteBranch`엔 없다. `SvnRepository`가 no-op이라 크래시는 없으나 SVN 프로젝트에 호출 시 아무 동작 없이 성공 신호(302)를 준다.
4. **`download`(zip 아카이브)의 경로 존재 사전 검증 소실(경미)** — yona는 404를 반환 후 스트리밍하나, yona `ProjectViewController.downloadCode`는 `path` 파라미터 자체가 없고 사전 검증도 없다.

### 이미 parity/index.md에 추적 중인 관련 항목
P0-16, P1-23, P1-64, P1-45, P1-93, P1-97(이미 수정 완료), P2-02, P2-09.

---

## 사이트관리/통계/검색 도메인

### 완전히 이식됨
- **SiteApp.java → SiteViewController.kt / SiteApiController.kt**: userList/projectList/issueList/postList/mail/massMail/data/diagnose/update/deleteUser(단일 관리자 보호)/toggleSiteAdminRole/toggleAccountLock/toggleGuestMode/mailList/exportData/importData/noAvatarUsers/setAttachmentToUserAvatar/unwatchUpdate 전 액션 확인. `checkAdmin()`이 클래스 레벨 권한체크와 동등.
- **Statistics.java → UserStatisticsResponse.kt / StatisticsServiceImpl.kt**: 7개 필드 및 산출 쿼리 1:1 대응.
- **StatisticsApp.java(프로젝트 통계) → StatisticsController.kt**: 라우팅·조회 확인.
- **History.java → HistoryDto.kt + ProjectViewController.getProjectHistory()**: 4개 생성 로직과 정렬이 필드 단위로 대응 확인.
- **YobiUpdate.java → YonaUpdateService.kt**: 버전 비교/스케줄링/isWatched 이식(P2-10).
- **Label.java → Label.kt + LabelRepository.kt + LabelController.kt + ProjectServiceImpl.attachLabel/detachLabel**: 기본값·전역 삭제 캐스케이드까지 확인(P1-13).
- **HelpApp.java → HelpController.kt**, **MarkdownApp.java → MarkdownController.kt**: 확인.
- **Search.java/SearchResult.java의 "본인 작성/담당 예외 노출" 로직**: P1-81/P1-83 완료 재확인, Posting/IssueComment/PostComment/ReviewComment까지 반영됨.

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- Ebean DSL → Spring Data JPA 리포지토리 쿼리로 대체, 조건 동등.
- `History.setUrl()`의 context-path 보정은 Spring Boot 프레임워크 레벨에서 담당 — 결손 아님.
- `YobiUpdate.fetchVersionToUpdate()`(v접두사만 검사) vs `YonaUpdateService.checkForUpdate()`(v 없어도 시도) — 원본보다 관대한 쪽이라 결손 아님.
- `MarkdownApp.render()`(project null 허용) vs `MarkdownController.render()`(404 반환) — 더 안전한 방향의 변경.

### 결손 발견 (신규)
1. **`HIDE_PROJECT_LISTING` 플래그 및 이를 사용하는 분기가 yona에 전혀 없음** — yona `Search.projectsEL()`(익명 검색 시 PUBLIC 필터), `inProjectsTemplate()`(전역 검색 시 PUBLIC 노출 제어), `SearchApp.searchInAGroup()`의 게이트(조직 검색을 조직 멤버+관리자로 제한)가 모두 이 플래그에 의존하나, `SearchServiceImpl.kt`/`SearchController.kt` 전수 확인 결과 플래그 자체도, 대응 분기도 존재하지 않는다(같은 원인으로 별도 코드 위치인 `searchInAGroup()` 게이트 부재도 여기 포함, 중복 카운트하지 않음). 사이트를 "프로젝트 목록 비공개" 모드로 운영할 때 검색으로 우회 노출되는 것을 막던 로직이 완전히 빠져 있다. 부수적으로 `Application.java`의 다른 정적 설정 필드(`GITHUB_NAME`, `ALLOWED_SENDING_MAIL_DOMAINS`, `PRIVATE_IS_DEFAULT`, `SEND_YONA_USAGE`, `GUEST_USER_LOGIN_ID_PREFIX`, `SHOW_USER_EMAIL`)도 대응이 전혀 없으나 소비처는 감사 범위 밖이라 심층 대조는 하지 않음.
2. **Diagnostic.java / SimpleDiagnostic.java의 레지스트리 패턴과 실제 체크 내용이 통째로 대체됨** — yona가 실제 등록하는 진단은 정확히 3개(메일 수신 IDLE 스레드 생존, hostname 미설정 경고, 설정값 중복 저장 감지)뿐이나, `DiagnosticService.kt`는 레지스트리 메커니즘을 없애고 하드코딩된 별개의 3개 체크(DB 커넥션, git/svn 디렉터리 쓰기 권한, `JavaMailSender` 빈 존재)로 대체 — 겹치는 항목이 하나도 없다. "자가진단" 화면이 원래 잡던 문제는 검출하지 못하고 별개 점검만 수행한다.
3. **`StatisticsViewController.kt`가 익명 사용자를 무조건 차단** — yona `@AnonymousCheck`는 로그인 불필요를 의미하며 실제 접근 제어는 `AccessControl.isAllowed(..., READ)`가 담당해 PUBLIC 프로젝트의 익명 READ를 허용한다. `StatisticsViewController.statistics()`는 프로젝트 스코프 확인 전에 비로그인 사용자를 전부 403 처리 — 주석의 의도("AnonymousCheck")와 실제 구현이 반대인 회귀다.
4. **`SearchType.NA`/`SearchType.PROJECT`에 대한 명시적 400 처리 누락** — yona 세 검색 엔드포인트 모두 즉시 badRequest를 반환하나, `SearchController.kt`/`SearchServiceImpl.kt`엔 이 가드가 없어 조용히 빈 결과를 200 OK로 반환한다.
5. **(참고, 경미)** `Statistics.empty()`(익명에게 0값 200 응답)와 `StatisticsController.userStatistics()`의 404 응답 계약 차이 — 정식 결손으로는 세지 않고 기록만 함.

### 이미 parity/index.md에 추적 중인 관련 항목
P1-81(완료), P1-83(완료), P1-13(완료), P0-07(완료), P2-10(완료), P2-11(완료), P2-02(인접, HistoryUtil.kt는 이 항목 대응물이며 History.java 대응물이 아님을 확인), P1-99(유사 패턴 선례 — `allowsAnonymousAccess`는 완료이나 `HIDE_PROJECT_LISTING`은 별개 플래그로 미추적).

---

## 접근제어/검증 핵심 유틸

*(대조 대상 7개 yona 파일을 모두 Read로 전문 열람, yona 측(`config/security/AccessControl.kt`, `domain/user/{ReservedWordsValidator,LoginIdFormatValidator}.kt`, `domain/support/{MarkdownServiceImpl,AutoLinkRenderer,MarkdownService}.kt`, `web/{CodeViewController,MarkdownController}.kt`)도 전문 열람 후 대조. `docs/parity/index.md`도 관련 키워드로 전수 검색.)*

### 완전히 이식됨
- **AccessControl.java** → `config/security/AccessControl.kt`: 모든 리소스 타입 분기(PROJECT/ORGANIZATION/ISSUE_POST/ISSUE_COMMENT/BOARD_POST/NONISSUE_COMMENT/PULL_REQUEST/COMMIT_COMMENT/COMMENT_THREAD/REVIEW_COMMENT/MILESTONE/WEBHOOK/PROJECT_TRANSFER/ATTACHMENT), `isAllowedIfAuthor`/`isAllowedIfAssignee`/`isAllowedIfSharer`/`isAllowedIfGroupMember`, `allowsAnonymousAccess` 게이트까지 1:1 대응(P1-85 완료).
- **ReservedWordsValidator.java** → `domain/user/ReservedWordsValidator.kt`: 정적 리스트 방식으로 이식 확인.
- **LoginIdFormatValidator.kt**: `LOGIN_ID_PATTERN` 정규식 동일(순서만 다름) 확인.
- **AutoLinkRenderer.java** → `domain/support/AutoLinkRenderer.kt`: 5단계 패턴 파이프라인 전체, 헬퍼까지 대응 확인.

### 부분 이식 / 아키텍처 차이로 인한 의도적 변경
- **ValidationUtils.java, ValidationResult.java**: Play 배관 코드로 검증 로직 자체가 없음 — 이식 대상 아님.
- **Markdown.java 렌더링 엔진**: `marked.js`(Nashorn) → `commonmark`로 교체, OWASP sanitizer 정책도 동일 이식(P0-08 완료), `file`/`zpl` 프로토콜 제외도 명시됨 — 아키텍처 차이로 정상.
- **RouteUtil.java**: `NotificationUrlResolver.kt`가 실제 경로를 직접 구성. "outdated diff 특정 커밋 앵커 세부 분기는 생략"으로 P1-27 완료 로그에 명시된 의도적 축소.
- **AutoLinkRenderer의 하드코딩 URL**: `UserViewController`/`OrganizationViewController`의 실제 `@GetMapping`과 일치해 정상 동작 확인.

### 결손 발견 (신규)
1. **`renderFileInCodeBrowser()`/`renderFileInReadme()` 미이식** — 코드 브라우저에서 `.md` 파일/README 열람 시 마크다운 렌더링 및 상대 경로 치환 로직. `web/CodeViewController.kt` 전체에 `markdownService` 호출이 0건.
2. **`checkReferrer()` 미이식** — `application.noreferrer` 설정 시 외부 링크에 `rel="noreferrer"`를 붙이는 로직. `NotificationMailBodyProcessor.kt`(알림 메일 경로 전용)에만 있고 일반 마크다운 렌더링 경로(`MarkdownServiceImpl.render()`)에는 적용되지 않음(범위 제한적 이식).
3. **`removeJavascriptInHref()`/`transformIssueLink()` 미이식** — 전자는 OWASP `allowUrlProtocols`로 실질 보안 결손은 아님. 후자(본문의 순수 이슈 URL을 `#번호.제목` 링크로 자동 승격 + READ 권한 체크)는 순수 기능 결손.
4. **`Markdown.render(source, project, breaks, lang)` 오버로드 및 렌더 캐시 미이식** — `MarkdownService.kt`에 `lang` 오버로드가 없어, 배치 스레드(`NotificationMailDigestScheduler.kt`)에서 수신자별 `locale`을 `markdownService.render()`에 전달할 방법이 없다. 요청 스코프 `LocaleContextHolder`가 배치 스레드엔 없어 JVM 기본 로케일로 떨어질 가능성이 높고, 다이제스트 메일의 `@멘션` 표시 이름이 수신자 언어를 따르지 않을 수 있음(실사용 영향 있음). 캐시 부재는 성능 이슈로 심각도 낮음.
5. **`isGlobalResourceCreatable(User)`/`isResourceCreatable(User, Resource, ResourceType)` 미이식** — 프로젝트 소속이 아닌 글로벌 리소스(임시 첨부 등) 생성 권한 판단 함수. `AccessControl.kt` grep 0건.
6. **`toValidSHALink`의 `project.isCodeAvailable()` 체크 부재** — yona는 프로젝트 메뉴에서 "코드" 탭이 꺼져 있으면 커밋 링크를 만들지 않으나, yona는 `project.vcs == "GIT"`만 검사한다(참고용, 프로젝트 메뉴 설정 기능 자체의 이식 여부에 종속된 문제).

### 이미 parity/index.md에 추적 중인 관련 항목
P1-85, P1-86~99, P1-101(모두 완료), P2-01(완료), P1-104, P1-108(완료), P0-08(완료), P1-27(완료, outdated diff 앵커 정밀도는 의도적 생략으로 명시), P1-47(간접 관련).

---

## 신규 발견 결손 전체 목록

아래는 이번 통합 감사에서 새로 발견되어 `docs/parity/index.md` 어디에도 등록되어 있지 않은 것으로 확인된 결손 59건이다. 사용자가 백로그 등록 여부를 판단하는 근거 자료이며, 제안 심각도는 각 도메인 보고서의 서술을 근거로 통합 작업 중 분류한 것으로 최종 판단은 아니다.

| # | 도메인 | 파일(yona → yona) | 구체적 내용 | 제안 심각도 |
|---|---|---|---|---|
| 1 | 게시판 | `BoardApp.java` → `BoardViewController.createPost` | README 게시글 중복 생성 방지 로직 없음, 반복 작성 시 README 게시글 계속 누적 | P1 |
| 2 | 게시판 | `BoardApp.java` → `BoardViewController.createPost` | `issueTemplate=true` write-path 분기 없음, 제출 시 실제 커밋 대신 일반 게시글로 저장 | P1 |
| 3 | 게시판 | `BoardApp.java` → `BoardViewController.createPost` | 코드브라우저 "편집"의 임의 텍스트 파일 온라인 커밋 write-path 없음, 일반 게시글로 저장 | P1 |
| 4 | 게시판 | `BoardApp.java` → `OrganizationViewController.organizationBoards` | 조직 게시판 목록에 프로젝트 가시성 필터 없어 비공개 프로젝트 게시글 노출(접근제어 회귀) | P0 |
| 5 | 게시판 | `BoardApp.java` → `CommentServiceImpl.createPostingComment/createIssueComment` | 새 댓글 알림의 "인용 이전 내용"(oldValue) 미채움 | P2 |
| 6 | 게시판 | `PostingComment.java` → `CommentController.CommentRequest` | `parentCommentId` DTO 필드 없어 대댓글 생성이 API로 노출 안 됨 | P1 |
| 7 | 게시판 | `AccessControl.java` → `BoardController/BoardViewController` | 공개 프로젝트 비멤버의 게시글 작성 권한이 yona보다 과도하게 제한(회귀) | P1 |
| 8 | PR/코드리뷰 | `PullRequest.java` → `PullRequestViewController.viewPullRequest/viewChangesInternal` | `getCodeCommentThreadsForChanges` 필터링 전무, 다른 커밋/outdated 스레드가 항상 섞여 노출 | P1 |
| 9 | PR/코드리뷰 | — → `PullRequestRepository.findRelatedPullRequests()` | JPQL 연산자 우선순위 버그로 CLOSED/MERGED PR도 브랜치 삭제 처리 대상에 포함 가능 | P1 |
| 10 | PR/코드리뷰 | `AccessControl.java` → `CodeReviewServiceImpl.hasPermission()` | 리뷰/커밋 댓글 삭제 권한이 "작성자 또는 MANAGER"로 과도 제한(P1-90~95와 동일 유형이나 조사 누락) | P1 |
| 11 | PR/코드리뷰 | `CommentThreadApp.java` → `CommentThreadController.open()/close()` | 리뷰 스레드 열기/닫기에 권한 체크 전무, 무관한 사용자가 임의 프로젝트 스레드 조작 가능 | P0 |
| 12 | PR/코드리뷰 | `PullRequest.java` → (대응 없음) | `getCommitComments()`(SVN 커밋코멘트 ↔ PR 매핑) 대응 부재 | P2 |
| 13 | 프로젝트 | `Project.java` → `ProjectServiceImpl.deleteProject()` | 계단식 삭제(PR/이슈/게시글/라벨/웹훅 등) 미이식, cascade 선언 없어 삭제 실패 또는 고아 행 발생 | P0 |
| 14 | 프로젝트 | `User.java` → `ProjectMemberController.assignableUsers()` | 조직 그룹 기반 담당자 후보(조직 관리자/멤버/사이트매니저) 확장 로직 없음 | P1 |
| 15 | 사용자/인증 | `UserApi.java` → (대응 없음) | 사이트관리자 전용 벌크 사용자 생성(`newUser`)/API 전용 토큰 로그인(`newToken`)/사용자 전체 조회·상태변경(`users`/`updateUserState`) API 부재 | P1 |
| 16 | 사용자/인증 | — → `User.kt`, `UserDetailsServiceImpl.kt` | `loginId=="admin"`이면 상태 무관 항상 `isSiteManager=true`로 판정하는 yona에 없는 하드코딩 분기 | P1 |
| 17 | 조직 | `Organization.java` → `OrganizationViewController.organizationHome()` | `getVisibleProjects` 필터 없이 비공개 포함 전체 프로젝트 노출 | P0 |
| 18 | 조직 | `OrganizationApp.java` → `OrganizationViewController.orgList()` | `HIDE_PROJECT_LISTING` 403 체크 및 `@GuestProhibit` 미이식 | P1 |
| 19 | 조직 | `OrganizationApp.java` → `OrganizationViewController.createOrganization()` | 게스트 계정 조직 생성 차단(`@GuestProhibit`) 미이식 | P1 |
| 20 | 조직 | `EnrollOrganizationApp.java` → `OrganizationServiceImpl.enroll()` | 중복 가입 신청 가드 없어 재신청 시 알림 중복 발행(Project P1-16과 동일 유형, 대칭 미적용) | P1 |
| 21 | 조직 | `EnrollOrganizationApp.java` → `OrganizationServiceImpl.cancelEnroll()` | 대기 신청 여부 확인 없이 무조건 취소 알림 발행, isGuest 가드도 없음 | P1 |
| 22 | 조직 | `OrganizationApp.java` → `OrganizationViewController.updateOrganization()` | 조직 로고 업로드 시 이미지 타입/크기(`LOGO_FILE_LIMIT_SIZE`) 검증 미이식 | P1 |
| 23 | 조직 | `FavoriteOrganization.java` → `OrganizationServiceImpl.updateOrganizationSettings()` | 조직명 변경 시 `FavoriteOrganization.organizationName` 비정규화 필드 동기화 누락 | P2 |
| 24 | 조직 | `AccessControl.java` → `OrganizationController.kt`(REST) `isOrgAdmin()` | 사이트매니저 전역 우회 로직 부재, REST API에서 설정변경/삭제 시 403 가능성(P2-16 "문제없음" 판정과 배치) | P0 |
| 25 | 알림/메일 | `Mention.java` → (대응 없음) | 멘션 인덱스 엔티티 자체가 yona에 없음(2, 3번의 근본 원인) | P1 |
| 26 | 알림/메일 | `NotificationEvent.getMentionedUsers()` → `CommentServiceImpl.extractMentionedUsers()` | 조직/프로젝트 그룹 멘션 확장 없음, `@owner/project` 정규식 매칭도 불가 | P1 |
| 27 | 알림/메일 | `NotificationEvent.java` → `IssueServiceImpl/PostingServiceImpl/PullRequestServiceImpl` 생성 로직 | 신규 이슈/게시글/PR 생성 시 본문 `@멘션` 알림 수신자 계산 자체가 없음 | P1 |
| 28 | 알림/메일 | `NotificationEvent.getReceivers(Project)` → `ProjectUserServiceImpl.getProjectManagers()` | 가입요청/취소 알림 수신자 계산이 Watch 여부를 무시 | P2 |
| 29 | 알림/메일 | `NotificationEvent.java` → `OrganizationServiceImpl.enroll/cancelEnroll` | 조직 가입 신청 oldValue/newValue 페어링이 비대칭이라 드래프트 상쇄 최적화 미작동 | P2 |
| 30 | 마일스톤 | `MilestoneApp.java` → `MilestoneViewController.listMilestones()` | orderBy/orderDir 정렬 파라미터 및 완료율 정렬 로직 전체 없음 | P1 |
| 31 | 마일스톤 | `Milestone.java` → `MilestoneViewController.toViewDto()` | 마일스톤 상세의 이슈 목록 정렬(번호 내림차순) 없음, 쿼리에도 ORDER BY 없음 | P2 |
| 32 | 마일스톤 | `MilestoneApi.java` → (대응 없음) | 벌크 마일스톤 임포트 API 전체 미이식(단건 생성만 지원) | P1 |
| 33 | 마일스톤 | `MilestoneApp.validateDueDate()` → `MilestoneViewController.createMilestone/editMilestone` | dueDate 파싱 실패 시 조용히 null로 저장(에러 알림 없음) | P2 |
| 34 | 첨부파일 | `Attachment.moveOnlySelected()` → `IssueViewController/MilestoneViewController/BoardViewController` | 소유권/원컨테이너 검증 우회, 임의 첨부파일 강제 재배선 가능(보안) | P0 |
| 35 | 첨부파일 | `Attachment.save()` → `AttachmentServiceImpl.store()`/`AttachmentController.uploadFile()` | DB dedup 미이식 + isNew 판정 오류로 201 응답 도달 불가, 재업로드마다 중복 행 생성 | P2 |
| 36 | 첨부파일 | `AccessControl.java` → `AttachmentController.deleteFile()` | ORGANIZATION/COMMIT_COMMENT/REVIEW_COMMENT/USER_AVATAR에서 `isAllowedAttachment()` 미재사용, 원본 업로더 전용으로 과잉 제한 | P1 |
| 37 | 첨부파일 | `FileUtil.detectMediaType` → `AttachmentServiceImpl.kt` | MIME 감지가 Tika(콘텐츠기반)→JDK probeContentType(확장자기반)으로 바뀌어 해시 파일명에서 오탐 가능 | P2 |
| 38 | 첨부파일 | `Attachment.java` → `AttachmentCleanupScheduler.kt` | 임시 첨부 정리 스케줄러의 createdDate 비교 방향이 yona와 반대(사실만 기록) | P2 |
| 39 | 감시/즐겨찾기 | `WatcherApi.getWatchers()` → `WatchController.getWatchers()` | 감시자 목록이 명시적 Watch row만 반환, 작성자/담당자/투표자/프로젝트감시자 합산 및 권한 필터 없음 | P1 |
| 40 | 감시/즐겨찾기 | `FavoriteProject.updateFavoriteProject()` → (대응 없음) | 프로젝트 개명/이전 시 `FavoriteProject.owner/projectName` 동기화 코드 없음 | P2 |
| 41 | 웹훅 | `Webhook.buildRequestMessage()` → `WebhookServiceImpl.buildTextMessage()` | 모든 이벤트 텍스트 메시지에 리소스 링크 전혀 없음 | P1 |
| 42 | 웹훅 | `Webhook.buildIssueDetails/buildJsonWithPullReqtuestDetails` → `WebhookServiceImpl.buildPayload()` | DETAIL_SLACK attachment의 이슈 필드 축소, PR attachment는 완전 미지원 | P1 |
| 43 | 웹훅 | `Webhook.java`(threadJSON) → `WebhookNotificationEventListener.resolveResource()` | Hangout Chat 스레드 키가 댓글 이벤트에서 부모 리소스가 아닌 댓글 자신으로 계산돼 스레드 그룹핑 무력화 | P1 |
| 44 | 웹훅 | `Webhook.java`(@Size 검증) → `WebhookController.newWebhook()` | payloadUrl/secret 길이·필수 검증 미이식, DB 제약 위반 500 노출 가능 | P2 |
| 45 | 코드/Git/SVN | `GitUtil.commitTextFile` → `domain/vcs/BareCommit.kt` | refName이 "refs/heads/master" 하드코딩, 브랜치 지정 커밋 기능 소실 | P1 |
| 46 | 코드/Git/SVN | `CodeHistoryApp.history` → `CodeViewController.history/historyUntilHead` | 빈 저장소 NoHeadException 미처리, 전역 핸들러도 없어 500 전파 가능 | P1 |
| 47 | 코드/Git/SVN | `BranchApp.java`(`@IsOnlyGitAvailable`) → `BranchApiController.setAsDefault/deleteBranch` | Git 전용 가드 누락, SVN 프로젝트에 호출 시 no-op이나 성공 신호 반환 | P2 |
| 48 | 코드/Git/SVN | `CodeApp.download` → `ProjectViewController.downloadCode` | zip 다운로드 시 경로 사전 존재 검증 및 path 파라미터 자체 소실 | P2 |
| 49 | 사이트관리/통계/검색 | `Search.java`, `SearchApp.java` → `SearchServiceImpl.kt`/`SearchController.kt` | `HIDE_PROJECT_LISTING` 플래그 및 관련 분기 전무(익명 검색 PUBLIC 필터, 조직검색 게이트 포함), 비공개 모드 우회 노출 | P0 |
| 50 | 사이트관리/통계/검색 | `Diagnostic.java`/`SimpleDiagnostic.java` → `DiagnosticService.kt` | 진단 체크 내용이 원본 3개(메일수신/hostname/중복설정)와 전혀 다른 3개로 대체돼 원래 문제를 검출 못 함 | P1 |
| 51 | 사이트관리/통계/검색 | `StatisticsApp.java`(`@AnonymousCheck`) → `StatisticsViewController.statistics()` | 익명 사용자를 프로젝트 스코프 확인 전에 무조건 403 — PUBLIC 프로젝트 통계도 로그인 요구(회귀) | P1 |
| 52 | 사이트관리/통계/검색 | `SearchApp.java` → `SearchController.kt`/`SearchServiceImpl.kt` | SearchType.NA/PROJECT 400 처리 없이 조용히 빈 결과 200 반환 | P2 |
| 53 | 접근제어/검증 유틸 | `CodeApp.java`(renderFileInCodeBrowser/renderFileInReadme) → `CodeViewController.kt` | 코드 브라우저에서 `.md`/README 마크다운 렌더링 자체가 없음(markdownService 호출 0건) | P1 |
| 54 | 접근제어/검증 유틸 | `Markdown.java`(checkReferrer) → `MarkdownServiceImpl.kt` | noreferrer 로직이 알림메일 경로에만 있고 일반 마크다운 렌더링엔 미적용 | P2 |
| 55 | 접근제어/검증 유틸 | `Markdown.java`(transformIssueLink) → (대응 없음) | 본문 순수 이슈URL 자동 링크화(+권한체크) 미이식 | P2 |
| 56 | 접근제어/검증 유틸 | `Markdown.render(...,lang)` → `MarkdownService.kt` | lang 오버로드/렌더 캐시 없음, 다이제스트 메일 배치 스레드에서 로케일 미반영 가능성 | P1 |
| 57 | 접근제어/검증 유틸 | `AccessControl.isGlobalResourceCreatable/isResourceCreatable` → (대응 없음) | 글로벌 리소스 생성 권한 판단 함수 미이식(영향 제한적) | P2 |
| 58 | 접근제어/검증 유틸 | `AutoLinkRenderer.toValidSHALink` → `AutoLinkRenderer.kt` | `project.isCodeAvailable()` 체크 없이 vcs=="GIT"만 검사 | P2 |
| 59 | (요약 집계상 미표시, 감사 시 사실 기록만) | — | 위 49번과 동일 원인인 `SearchController.searchInAGroup()`의 조직검색 게이트 부재는 49번에 병합되어 별도 행으로 세지 않음(원 보고서 지시에 따름) | — |

**추가로 각 도메인 보고서가 "정식 결손으로 등록할 정도는 아니다/참고로만 기록한다"고 명시적으로 판단해 위 표에서 제외한 경미 관찰 사항**(백로그 등록 여부는 판단하지 않음):
- 프로젝트 도메인: `MigrationApp` 사이트매니저 바이패스 범위 축소(경미).
- 사용자/인증 도메인: Gravatar 아바타 폴백 제거, 이메일 확인 시 자동 로그인 미이식, LDAP `USE_EMAIL_BASE_LOGIN` 적용 범위 확대.
- 마일스톤 도메인: 413(REQUEST_ENTITY_TOO_LARGE) 방어 로직 부재(마일스톤 국한 아닌 시스템 전반 갭).
- 사이트관리/통계/검색 도메인: `Statistics.empty()` vs `StatisticsController.userStatistics()` 응답 계약 차이.