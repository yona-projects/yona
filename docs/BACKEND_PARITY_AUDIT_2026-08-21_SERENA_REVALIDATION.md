# yona → yona 백엔드 감사 재검증 (Serena LSP 강제, 2026-08-21)

## 배경

`docs/BACKEND_PARITY_AUDIT_2026-08-21.md`의 13개 도메인 중 6개(게시판, 마일스톤, 첨부파일, 웹훅, 알림/메일, 접근제어/검증 핵심 유틸)는 Bash grep 위주로 조사됐음이 사후에 드러났다. grep은 심볼 참조 관계를 놓치기 쉬워, 이 6개 도메인만 별도로 "Bash grep 사용 금지, Serena MCP 도구(`find_symbol`/`get_symbols_overview`/`find_referencing_symbols`/`search_for_pattern`) 우선 사용"을 강제하는 규칙으로 재검증했다.

## 재검증 요약

grep 기반으로 조사됐던 6개 도메인(게시판, 마일스톤, 첨부파일, 웹훅, 알림/메일, 접근제어/검증 핵심 유틸) 전체를 Serena LSP(`find_symbol`, `find_referencing_symbols`, `search_for_pattern` 등)로 재검증했다.

- **핵심 결론(결손의 존재 여부) 자체가 뒤집힌 도메인**: 0/6 — 어제 보고서가 지목한 결손 항목들은 6개 도메인 전부 소스 대조 결과 실재하는 것으로 재확인됐다.
- **서술의 근거·범위·뉘앙스 등에서 정정이 필요했던 항목이 있는 도메인**: 5/6 (게시판, 마일스톤, 첨부파일, 웹훅, 접근제어/검증). 알림/메일 도메인만 정정 항목이 0건이었다.
- **정정이 필요했던 항목**: 총 8건 (게시판 2, 마일스톤 1, 첨부파일 2, 웹훅 1, 알림/메일 0, 접근제어 2)
- **새로 발견된 항목**: 총 5건 (게시판 2, 마일스톤 0, 첨부파일 0, 웹훅 0, 알림/메일 3, 접근제어 0 — 접근제어 도메인은 "결손으로서 신규 발견은 없음"이라 명시했고, 대신 방법론적 관찰 1건을 별도로 남겼다)
- **공통적으로 드러난 방법론적 한계**: 여러 도메인에서 yona(Kotlin) 언어서버가 세션 도중 초기화 실패/크래시를 반복해, 참조 관계 확인을 `find_referencing_symbols` 대신 `search_for_pattern`(텍스트 패턴 검색)으로 대체한 경우가 많았다. 또한 `find_referencing_symbols`가 Play/Twirl 템플릿(`.scala.html`)이나 익명 클래스를 통한 간접 호출을 놓쳐 "죽은 코드"를 오판할 위험이 여러 도메인(마일스톤, 접근제어)에서 확인됐다.

## 정정·신규 발견 통합 목록

| 도메인 | 내용 | 유형 |
|---|---|---|
| 게시판 | 결손5(인용 이전 내용 미이식) — `NotificationMessageResolver.kt:58-60`에 이미 인지된 상태로 null-safe 우회 처리가 돼 있음이 확인됨. 단순 누락이 아니라 "알고도 방치"임 | 정정 |
| 게시판 | 결손7(가시성 제어) 원인 재정의 — Board만의 실수가 아니라, yona `IssueViewController`에 이미 존재하는 `isProjectResourceCreatable` 정답 패턴을 Board가 재사용하지 않은 것 | 정정 |
| 게시판 | 동일한 `checkWritePermission` 패턴이 `PullRequestController`/`ReviewApiController`에도 복제돼 있어 동일 회귀가 PR/리뷰 도메인에도 있을 가능성 | 신규 |
| 게시판 | 이 환경의 yona(Java) 프로젝트에서 `find_referencing_symbols`가 확실히 참조되는 심볼(`Posting.findByNumber` 등)에도 빈 결과를 반환 — 구조적으로 신뢰 불가함을 발견 | 신규 |
| 마일스톤 | REST(`MilestoneController.kt`) 권한 체크는 "헬퍼형"이 아니라 혼합형(조회는 헬퍼, 생성/수정/삭제는 인라인 직접 호출) — 결론("결손 아님")은 유효하나 근거 서술이 부정확했음 | 정정 |
| 첨부파일 | 결손1(`moveOnlySelected` 우회) 근거 보강 — yona 쪽 6개 실제 호출부(`IssueApp`/`MilestoneApp`/`BoardApp`/`PullRequestApp`/`IssueApi`/`BoardApi`) 확인, yona `AttachmentServiceImpl.moveOnlySelected`는 정의부 외 참조 0건, 4개 컨트롤러에서 소유권 검증 없는 동일 패턴 반복 확인 | 정정 |
| 첨부파일 | 결손3(`deleteFile` 드리프트) 서술 정정 — yona에는 애초에 READ/DELETE가 분리된 별도 함수가 없고 단일 dispatcher 공유. 예시로 든 "조직 로고/타 조직 관리자" 시나리오는 사실과 반대(yona도 사이트매니저 아니면 삭제 불가). 실제 과잉 제한 재현 지점은 COMMIT_COMMENT/REVIEW_COMMENT | 정정 |
| 웹훅 | 결손3(스레드 키 오류) 범위 확대 — 이슈/게시글 댓글뿐 아니라 PR 리뷰 댓글(`REVIEW_COMMENT`)까지 yona는 부모 리소스를, yona는 댓글 자신을 스레드 키로 써 동일 패턴이 재발 | 정정 |
| 알림/메일 | `Mention.update()`/`getMentioningIssueIds()`는 yona에서 죽은 코드가 아니라 실제 활성 기능(멘션 인덱스 + "나를 멘션한 이슈" 검색)임을 확인 — 결손#1 판정을 더 단단히 뒷받침 | 신규 |
| 알림/메일 | yona(Kotlin) 언어서버가 세션 중 반복 크래시하여 yona 쪽은 진짜 심볼 참조 해석이 아닌 `search_for_pattern` 텍스트 매칭으로만 검증 가능했음 | 신규 |
| 알림/메일 | `activate_project`로 yona/yona 전환 시 조용히 이전 프로젝트로 되돌아가는 현상이 다수 발생 — 교차검증 없었다면 오탐(false negative) 보고 위험 있었음 | 신규 |
| 접근제어/검증 | 결손5(`isProjectResourceCreatable` 미이식) 범위 재정의 — 프로젝트 스코프 리소스 생성 권한은 이미 대부분 이식·배선 완료, 실제 미이식은 글로벌 리소스 케이스(`isGlobalResourceCreatable`/`isResourceCreatable`)에 한정 | 정정 |
| 접근제어/검증 | "`isAllowedIfAuthor`/`isAllowedIfAssignee` 1:1 대응" 문구 부정확 — yona에는 이 이름의 독립 함수가 없고 `isAllowed(...)` 오버로드 내부 인라인 지역변수로 존재(동작은 동등) | 정정 |

---

## 게시판(Board/Posting) 도메인 — Serena 재검증

### 확인됨(어제 보고서 정확함)

- 결손 1~7 전부 소스 직접 대조로 재확인됨. 특히 1(README 중복), 2·3(ISSUE_TEMPLATE/임의 파일 온라인 커밋 write-path 누락), 6(parentCommentId 미노출)은 yona `BoardViewController.createPost`/`CommentController.CommentRequest` 본문을 직접 읽어 정확히 일치함을 확인. `PostingForm`에는 `title/body/notice/readme/temporaryUploadFiles/sendNotificationMail`만 있고 `issueTemplate`/`path`/`branch` 필드 자체가 없어, `createPostForm`(GET)에서는 기존 파일 내용을 미리보기용으로 읽어오면서도 `createPost`(POST)에는 그 값을 커밋할 경로가 전혀 없음을 확인.
- 4(조직 게시판 가시성 필터 누락)도 확인. yona `SearchCondition.asExpressionList(Organization)`은 `getVisibleProjectIds(organization)`(또는 필터 지정 시 `getFilteredProjectIds`)로 걸러내는 반면, yona `OrganizationViewController.organizationBoards`는 `val projects = org.projects`를 필터 없이 그대로 `postingRepository.findByProjectIn(projects, pageable)`에 넘김. `projectNames`/`filter`/`orderBy`/`orderDir` 파라미터도 실제로 없음을 재확인.
- "완전히 이식됨" 항목 중 `computeNumOfComments()`↔`countByPostingId()` 대응(P1-19)을 `CommentServiceImpl` 두 지점(생성/삭제 경로, 각각 주석에 "P1-19" 명시)에서 직접 확인. `CommentApp.java`가 `COMMIT_COMMENT`/`REVIEW_COMMENT` 전용이고 Board와 무관하다는 판단도 `delete()`의 switch문(default→badRequest)으로 재확인.
- "부분 이식" 항목의 번호 채번 재시도 누락도 확인: `PostingServiceImpl.createPosting()`은 `project.lastPostingNumber + 1` 증가 후 단순 저장뿐, 예외 캐치·재시도 로직 없음.
- 결손 1의 line 인용(`BoardApp.java:233-240`, `242-245`)까지 실제 1-based 라인과 정확히 일치함을 확인 — grep 기반이었음에도 이 부분은 정밀했음.

### 수정/정정 필요

- **결손 5(인용 이전 내용) 보강**: `CommentServiceImpl`에는 이 로직이 정말 없지만, `NotificationMessageResolver.kt:58-60`에 "`oldValue`(comment.previousContents)는 yona의 현재 NEW_COMMENT 생성 경로에서 항상 null이라 null-safe하게 이어붙인다"는 주석이 이미 존재함. 즉 이 결손은 실수로 놓친 게 아니라 개발자가 인지하고 우회 처리해둔 상태다. 어제 보고서는 "로직이 없다"까지만 적었는데, "이미 알려진 채로 방치됨"이라는 사실이 빠져 있었다.
- **결손 7 범위 재정의(중요)**: 어제 보고서는 이를 "BoardController/BoardViewController"의 문제로만 기술했으나, `AccessControl.isProjectResourceCreatable`의 실제 호출부를 전수 조사한 결과 **yona 자체에 이미 정답 구현이 존재**한다. `IssueViewController`의 이슈 생성 권한 체크(`createIssueForm`)는 `accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.ISSUE_POST)`를 정확히 호출해 공개 프로젝트 비멤버 작성을 허용한다. 반면 `BoardViewController.createPost`/`createPostForm`은 같은 헬퍼를 전혀 호출하지 않고 `projectUserRepository.existsByProjectIdAndUserId(...) || isAllowedIfGroupMember(...)`라는 별도의 좁은 검사만 쓴다. 즉 이것은 "프레임워크 전환 중 흔한 실수"가 아니라 "Issue 모듈은 맞게 이식됐는데 Board 모듈만 그 패턴을 재사용하지 않은 결손"이다 — 원인이 더 구체적이고, 고칠 때 참고할 정답 코드(IssueViewController)가 이미 같은 저장소에 있다는 뜻이므로 수정 난이도도 낮다.

### 신규 발견(어제 놓친 것)

- 같은 `checkWritePermission`(멤버/그룹멤버만 허용, 공개프로젝트 비멤버 예외 없음) 패턴이 `PullRequestController`, `ReviewApiController`에도 동일 코드로 복제돼 있음을 `search_for_pattern`으로 확인. Board 도메인 범위 밖이라 이번 보고서에는 포함하지 않지만, 동일 회귀가 PR/리뷰 도메인에도 있을 가능성이 있어 별도 확인이 필요함을 남겨둔다.
- `BoardApp.unmarkAnotherReadmePostingIfExists`(private)에 대해 `find_referencing_symbols`가 빈 결과(`{}`)를 반환했으나, 실제로는 `editPost()` 351행에서 호출되고 있음을 직접 코드로 확인 — **이 환경의 yona(Java) 프로젝트에서 `find_referencing_symbols`가 구조적으로 신뢰 불가**함을 발견(아래 총평 참고). `Posting.findByNumber`, `BoardApp.newPost` 등 확실히 참조되는 심볼에도 동일하게 빈 결과가 나와, 죽은 코드 여부 판단에 이 도구를 쓸 수 없었다.

### 총평

어제 보고서의 핵심 판단(결손 1~7, 완전 이식 항목들)은 Serena로 소스를 직접 재대조한 결과 사실관계 자체는 정확했다 — grep만으로도 이 정도 정밀도가 나온 것은 이례적이다. 다만 결손 7의 원인 진단은 이번 재검증으로 더 정확해졌다(Board만의 실수가 아니라 "이미 존재하는 정답 패턴을 재사용하지 않은 것"). 한 가지 중요한 한계: 이번 세션에서 yona(Java) 프로젝트의 `find_referencing_symbols`가 언어서버 미초기화로 사실상 항상 빈 결과를 반환했고(대체로 `search_for_pattern`으로 우회), 세션 도중 여러 차례 Kotlin 언어서버 자체가 크래시했다(다른 동시 세션과 프로젝트 활성화 상태를 공유하는 것으로 보임) — 따라서 "죽은 코드 여부"에 대한 검증은 심볼 참조 그래프가 아니라 텍스트 패턴 검색 및 직접 코드 리딩으로 대체 확인한 것이며, 이 부분은 완전한 LSP 기반 검증보다 신뢰도가 다소 낮다.

---

## 마일스톤 도메인 — Serena 재검증

### 확인됨(어제 보고서 정확함)

**완전히 이식됨 1~3, 부분 이식/의도적 변경 항목 전체(엔티티 연관관계 → 리포지토리 쿼리 대체, 벌크 삭제 처리, `until()`/`getDueDateString()` 템플릿 위임, 권한 체크 방식, `countOpened`/`options` 죽은 코드 판정)** — 모두 소스 대조로 그대로 확인됩니다.

- `MilestoneServiceImpl.kt`를 직접 읽어 `deleteMilestone()`이 `issueRepository.removeMilestoneFromIssues(milestone)` 벌크 쿼리와 `attachmentService.deleteAll(ResourceType.MILESTONE, ...)`를 호출함을 확인 — 어제 서술과 일치.
- `Milestone.countOpened`/`Milestone.options`: `find_referencing_symbols`로 0건(코드), `search_for_pattern`으로 `app/**/*.java,*.scala.html` 전체를 훑어도 0건 — 템플릿 포함 완전한 죽은 코드임을 재확인.

**결손 1(정렬 파라미터 누락)** — `MilestoneApp.milestones()` 본문에 `mCondition.orderBy`, `Direction.getValue(mCondition.orderDir)`가 실제로 `Milestone.findMilestones()`에 전달됨을 확인. 대응하는 `MilestoneViewController.listMilestones()`를 전문 Read로 확인한 결과 파라미터는 `state` 하나뿐이고 `milestoneService.getMilestones(project.id!!, stateEnum)`에도 정렬 인자가 없음 — 결손 확정.

**결손 2(이슈 목록 정렬 누락)** — `find_referencing_symbols`로 `sortedByNumberOfIssue()`가 `sortedByNumberOfOpenIssue`/`sortedByNumberOfClosedIssue` 내부에서만 호출됨을 확인했고, 이 두 메서드 자체는 `search_for_pattern`으로 `app/views/milestone/view.scala.html` 100·102행에서 실사용됨을 확인(LSP 참조 검색으로는 안 잡히는 템플릿 호출). `MilestoneViewController.toViewDto()`는 `allIssues.filter { it.state == State.OPEN/CLOSED }`만 하고 정렬이 없으며, `IssueRepository.findByMilestone()`도 `@Query`/`OrderBy` 없는 단순 파생 쿼리 — 결손 확정.

**결손 3(벌크 임포트 API 누락)** — `conf/routes`에서 `POST /-_-api/v1/owners/:owner/projects/:projectName/milestones → MilestoneApi.newMilestone`이 실제로 배선된 엔드포인트임을 확인(라우팅 안 된 죽은 컨트롤러가 아님). `createMilestoneNode()`는 항목별 제목 중복 검사 후 성공/에러 메시지를 배열로 반환하는 진짜 "per-item 에러 핸들링"임을 본문으로 확인. yona `MilestoneController.kt`(REST)와 `MigrationApiController.kt`를 전문 확인한 결과 `getMilestones/getMilestone/createMilestone(단건)/updateMilestone/deleteMilestone`, `exportMilestones`(조회 전용)만 존재 — 결손 확정.

**결손 4(dueDate 파싱 실패 무응답)** — `MilestoneViewController.kt`의 `createMilestone()`/`editMilestone()` 양쪽에서 `catch (e: Exception) { null }`로 조용히 삼키는 코드를 그대로 확인 — 결손 확정.

**참고: `NULL_MILESTONE_ID`** — yona `search_for_pattern` 결과 `SearchCondition.java`, `Issue.java`, 다수 view에서 `-1L` 센티널로 사용됨을 확인했고, yona `IssueViewController.kt`(654행 부근), `IssueSpecification.kt`(47행)에서 `milestoneId == -1L` 분기로 동일 처리됨을 확인 — 결손 아니라는 판단 정확.

### 수정/정정 필요

내용상 틀린 부분은 발견되지 않았습니다. 다만 한 가지 뉘앙스 보정: 어제 보고서는 "REST는 헬퍼형" 권한 체크라고 서술했으나, `MilestoneController.kt` 전문을 확인하니 조회(GET)만 `checkReadPermission()` 헬퍼를 쓰고 생성/수정/삭제는 `accessControl.isProjectResourceCreatable(...)`/`isAllowed(...)`를 각 메서드에 인라인으로 직접 호출합니다. 즉 REST 컨트롤러도 순수 헬퍼형이 아니라 혼합형입니다. 결론("결손 아님")은 그대로 유효하지만, 근거 서술은 부정확했습니다.

### 신규 발견(어제 놓친 것)

없음. 재검증 과정에서 `MilestoneApp`의 `validateTitle`/`validateDueDate`가 라우트에 없어 처음엔 고아 메서드로 의심했으나, `newMilestone()`/`editMilestone()` 내부에서 폼 검증용으로 호출되는 private 헬퍼임을 확인해 기각했습니다(결손 아님, 어제 보고서도 이를 별도 문제로 다루지 않았으므로 누락이 아니라 애초에 문제가 아니었습니다).

### 총평

어제 보고서의 마일스톤 도메인 서술은 결손 항목 1~4, 부분 이식 판정, 죽은 코드 판정 모두 Serena 재검증(`find_referencing_symbols`로 yona 내부 호출 여부 확인, `search_for_pattern`으로 템플릿·라우트까지 포함한 실사용 여부 확인, yona 소스 전문 대조)을 거쳐도 그대로 성립했습니다. 신뢰할 수 있는 보고서입니다. 다만 조사 방법론 관점에서 한 가지 주의점이 확인됩니다 — `sortedByNumberOfOpenIssue`/`ClosedIssue`처럼 Java 소스 내 호출은 0건이지만 Twirl 템플릿(`.scala.html`)에서만 호출되는 경우가 실제로 존재하며, LSP 기반 `find_referencing_symbols`만으로는 이를 죽은 코드로 오판할 수 있습니다. 어제 보고서는 결과적으로 이 부분을 정확히 판단했지만, 그 판단이 grep이 아닌 다른 근거(템플릿까지 읽었거나 UI 지식)에 의존했을 가능성이 있어, 향후 "죽은 코드" 판정 시에는 코드 참조 검색과 템플릿/라우트 패턴 검색을 항상 병행할 필요가 있습니다.

**참고**: 이번 세션 중 yona 프로젝트의 Kotlin 언어 서버가 초기화에 실패해(`restart_language_server`로도 복구 불가) 이후 `get_symbols_overview`/`find_symbol`/`find_referencing_symbols`가 yona 쪽까지 포함해 전면 사용 불가 상태가 되었습니다. 이 시점부터는 Serena `search_for_pattern`과 `Read`(작업 지침상 허용된 전체 파일 확인)로 전환해 조사를 완료했으며, 위 결론들은 모두 이 두 방법으로 재확인된 내용입니다.

---

## 첨부파일 도메인 — Serena 재검증

**방법론 참고**: 세션 중 Kotlin 언어서버가 초기화 실패 상태로 굳어(`find_referencing_symbols`/`find_symbol`(yona) 호출 시 재현), 이후 참조 관계 확인은 `search_for_pattern`(Serena 네이티브, grep 아님)과 `find_symbol`(yona, LSP 정상)로 대체했다. 아래 근거는 모두 이 두 도구로 실제 확인한 것이다.

### 확인됨(어제 보고서 정확함)
- "완전히 이식됨" 4항목 — `getFile()`의 `isAllowedAttachment(READ)` 배선, `getFileList()`, `isAllowedAttachment()` 구조 자체는 코드로 재확인.
- 결손 2번(dedup 미이식/201-200 판정 오류) — 완전히 정확함. 오히려 yona `AttachmentController.kt:71-76`의 주석이 "store() 실행 후 existsByHash가 항상 true가 되어 isNew를 미리 산출해야 한다"고 버그 메커니즘을 스스로 인정하고 있음을 확인.
- 결손 4번(MIME 확장자 기반 감지) — `AttachmentServiceImpl.kt:59` `Files.probeContentType(targetFile.toPath())`를 해시 파일명(확장자 없음)에 직접 호출하는 코드를 확인.
- 결손 5번(스케줄러 방향 반대) — yona `Attachment.java:461` `.ge("createdDate", beforeByMillis(...))`(최근분 삭제) vs yona `AttachmentCleanupScheduler.kt:23` `findByContainerTypeAndCreatedDateBefore`(threshold 이전, 오래된 것 삭제) — 코드로 정확히 대조 확인.

### 수정/정정 필요
**결손 1번(moveOnlySelected 우회)**: 결론은 정확하지만 근거가 더 강하게 뒷받침됨. yona `Attachment/moveOnlySelected`는 `find_symbol`로 봤을 때 죽은 코드가 아니라, `AbstractPostingApp.attachUploadFilesToPost()`를 통해 `IssueApp`/`MilestoneApp`/`BoardApp`/`PullRequestApp`/`IssueApi`/`BoardApi` 전 경로에서 호출되는 핵심 보안 게이트다(`search_for_pattern`으로 6개 호출부 확인). 반면 yona 쪽은 `AttachmentServiceImpl.moveOnlySelected`가 전체 kt 코드베이스에서 정의부(`AttachmentService.kt:28`, `AttachmentServiceImpl.kt:116`) 외에 단 한 곳도 참조되지 않음을 확인했고, `IssueViewController.kt:494`, `MilestoneViewController.kt:277`·`365`(생성/수정 각각), `BoardViewController.kt:338` 4곳에서 소유권 검증 없이 `attachmentRepository.findById(fileId).ifPresent { attachment.containerType = ...; attachment.containerId = ...; save }` 패턴이 동일하게 반복됨을 직접 확인했다. 어제 보고서보다 재현 가능성이 더 명확해짐.

**결손 3번(deleteFile 로직 drift)**: 핵심 진단(드리프트 존재)은 맞지만 서술이 부정확하다. 먼저 yona에는 "READ용 isAllowedAttachment()"라는 별도 함수가 없다 — `search_for_pattern`으로 전체 yona 코드베이스에서 0건 확인. yona `getFile()`/`deleteFile()`은 **동일한 단일 함수** `AccessControl.isAllowed(user, attachment.asResource(), Operation)`을 Operation만 바꿔 공유한다(둘 다 `find_symbol`로 본문 확인). 즉 yona 자체에는 "READ와 DELETE가 분리된 구조"가 없다. yona의 `isAllowedAttachment(user, attachment, operation)`이 오히려 이 단일 dispatcher를 operation-aware하게 정확히 복제했고(`AccessControl.kt:601-676`), `deleteFile()`만 이를 재사용하지 않고 별도 `when` 분기를 쓰는 것이 진짜 문제다.
더 중요한 정정: 예시로 든 "조직 로고, 다른 조직 관리자"는 사실과 다르다. `AccessControl.isGlobalResourceAllowed()`를 끝까지 추적하면(`Resource.get`→`Organization.asResource`/`User.avatarAsResource`가 GlobalResource 반환 확인), ORGANIZATION/USER_AVATAR 컨테이너의 ATTACHMENT는 DELETE/UPDATE 시 switch가 `resource.getType()==ATTACHMENT`라 어떤 case에도 안 걸리고 `default: return false`로 떨어진다 — **yona에서도 사이트매니저가 아니면 조직 관리자조차 삭제 불가**다. 반대로 yona의 catch-all(`ownerLoginId==loginUser.loginId || isSiteManager`)은 원 업로더에게는 허용하므로 이 구간에서는 yona가 오히려 더 관대하다. 실제로 "과잉 제한"이 재현되는 지점은 COMMIT_COMMENT/REVIEW_COMMENT다 — 여기는 컨테이너가 project-scoped라 yona `isProjectResourceAllowed()`의 ATTACHMENT case(`isAllowed(user, container, UPDATE)`)를 타고, 일반 UPDATE 규칙상 프로젝트 멤버 누구나 가능하지만, yona `deleteFile()`은 이 두 타입을 명시 케이스 없이 catch-all(업로더 전용)로 처리해 프로젝트 멤버 전반이 차단된다.

### 신규 발견(어제 놓친 것)
없음 — Serena로 확인한 결손은 어제 5개 항목의 범위 안에 있었고, 근거의 정확도만 재조정되었다.

### 총평
결함의 존재 여부(5개 결손)에 대한 판정 자체는 grep 기반 조사에서도 모두 정확했다. 다만 결손 3번은 근거로 든 구체 시나리오가 코드 추적 결과와 반대여서, 그대로 신뢰하면 오도될 소지가 있다. 결손 1·2번은 오히려 grep보다 강한 근거(호출부 목록, 자기고백 주석)로 보강됐다. 전반적으로 "무엇이 깨졌는가"는 신뢰할 수 있으나 "왜 깨졌는가"의 세부 설명, 특히 3번은 정정 후 사용해야 한다.

---

## 웹훅 도메인 — Serena 재검증

### 확인됨(어제 보고서 정확함)

- **완전히 이식됨 3건** — `Webhook.java`(743줄) 전체를 Read로 재확인, 필드(`id/project/payloadUrl/secret/gitPush/webhookType/createdAt`)와 `WebhookThread.kt`/`GitPostReceiveEvent.kt` 필드가 1:1 일치함을 직접 대조로 재확인.
- **`create()`/`delete(Long,Long)`/`delete(Long projectId)`/`findByIds()` 죽은 코드** — `find_referencing_symbols`로 넷 다 참조 0건 확인. 추가로 `ProjectApp.java:1315-1322`를 Read해보니 실제 삭제 경로(`webhook.delete()`)는 이 커스텀 오버로드조차 아니라 `Model`에서 상속한 무인자 `delete()`를 호출하는 것이라 어제 판단보다 더 확실하게 죽은 코드임을 확인. `findByProject`는 반대로 `find_referencing_symbols`가 0건을 반환했지만 `search_for_pattern`으로 `NotificationEvent.java`(8곳)와 `ProjectApp.java:1278`에서 실사용을 확인 — 어제 보고서가 이 메서드를 죽은 코드로 분류하지 않은 것은 옳았음.
- **결손 1~4 전부 코드 근거로 재확인**: `WebhookServiceImpl.buildTextMessage()`는 URL을 전혀 조립하지 않음(코드 273-286행). `DETAIL_SLACK` 분기(89-173행)는 Issue에만 `state` 필드를 추가하고, PR은 `bodyText`의 `when`에 케이스가 없어 `else -> ""`로 빠지며 `fields`도 채워지지 않음. `WebhookController.newWebhook()`(66-98행)은 `payloadUrl`/`secret`에 길이·필수 검증이 전혀 없고 `webhookType` 파싱 실패만 `try/catch`로 `SIMPLE` 폴백.
- **parity/index.md 완료 항목 목록** — P0-03/04, P1-25/26/69/87, P2-08/16 전부 `[x]` 완료로 재확인.
- **legacy `deleteWebhook`도 프로젝트 범위 검증 없음** — `ProjectApp.java:1315`가 `Webhook.find.byId(id)`만으로 삭제, `yona WebhookServiceImpl.deleteWebhook(id)`(48-53행)와 정확히 동치.

### 수정/정정 필요

- **결손 3(스레드 키 오류)의 실제 범위가 어제 서술보다 넓음.** 어제 보고서는 "댓글이 달린 이슈/게시글 기준"이라고만 적었으나, `Webhook.java:480`(`sendRequestToPayloadUrl(..., PullRequest, ReviewComment)`)을 보면 yona는 **PR 리뷰 댓글(REVIEW_COMMENT)도 부모인 PullRequest를 스레드 키로 사용**한다(`buildThreadJSON(eventPullRequest.asResource())`, `reviewComment` 자신이 아님). 반면 yona `WebhookNotificationEventListener.resolveResource()`(76-84행)는 `REVIEW_COMMENT`/`COMMIT_COMMENT` 둘 다 부모가 아니라 **댓글 엔티티 자신**을 반환하고, `WebhookServiceImpl.getResourceType/getResourceId`도 그 자신 기준으로 계산한다. 즉 결손 3은 이슈/게시글 댓글뿐 아니라 PR 리뷰 댓글·커밋 댓글까지 동일 패턴으로 재발하는, 더 넓은 범위의 결손이다. (다만 CommitComment는 yona `Webhook.java`에 해당 오버로드 자체가 없어 — `CommitComment`가 `Comment`가 아닌 `CodeComment`를 상속 — yona 쪽에 참조할 원본 동작이 있는지는 별도 확인이 필요하며, 이 부분만은 "회귀"라기보다 "yona 신규 기능의 자기 불일치"에 가까울 수 있다.)

### 신규 발견(어제 놓친 것)

없음. 위 스레드 키 범위 확대건은 어제 항목의 정정·구체화에 해당하며, 완전히 새로운 결손 카테고리는 발견되지 않았다.

### 총평

어제 보고서의 핵심 판단(완전 이식 3건, 죽은 코드 4개, 결손 4건, 백로그 완료 상태)은 Serena 심볼 조회·참조 추적으로 전부 재확인되어 신뢰할 수 있다. 다만 결손 3의 서술 범위가 실제보다 좁게 적혀 있었는데, 이는 `find_referencing_symbols`와 `Webhook.java`의 오버로드별 `buildThreadJSON` 호출 인자를 대조해야만 드러나는 차이라 grep 기반 조사에서 놓치기 쉬운 지점이었다. 전체적으로 어제 보고서는 방법론상 결함(grep 위주)에도 불구하고 결론은 견고했다고 평가한다.

---

## 알림/메일 도메인 — Serena 재검증

### 확인됨(어제 보고서 정확함)

**완전히 이식됨 항목**
- **`MailRecipient.java`**: yona `MailService.kt`에 `data class MailRecipient(val email: String, val name: String)`로 필드 1:1 확인(Serena `search_for_pattern`). `NotificationMailDigestScheduler.kt`의 `getToList()`/`getBccList()`가 `hideAddress` 값에 따라 To/Bcc를 뒤바꾸는 로직도 yona `NotificationMail.java`의 `getToList()`/`getBccList()`(hideAddress 삼항 분기)와 구조까지 일치.
- **`NotificationApp.java` → `NotificationController.kt`/`IndexController.kt`**: yona `NotificationApp`은 실제로는 `notifications(int from, int size)` 단 하나의 메서드(파셜 뷰 렌더링)뿐이었다(Serena `find_symbol` body 확인). yona `IndexController.kt`의 `partialNotifications()`(`/_notifications`, `from`/`size` 파라미터, `index/partial_notifications` 뷰 반환)가 이와 정확히 대응하며, `@GetMapping`으로 Spring이 자동 라우팅하므로 별도 라우트 파일 없이도 배선 완료 상태 확인.
- **결손 #1 (Mention 미이식)**: yona에 `Mention.kt`/`MentionRepository` 류가 전혀 없음을 `find_file`로 재확인.
- **결손 #2 (그룹 멘션 미지원)**: yona `NotificationEvent.getMentionedUsers()`는 `User.LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH`로 매칭 후 `findOrganizationMembers()`/`findProjectMembers()`까지 확장(코드 본문 확인). yona `CommentServiceImpl.kt`의 `mentionPattern = Pattern.compile("@([a-zA-Z0-9_\\-\\.]+) ")`에는 `/`가 빠져 있어 `@owner/project` 자체를 못 잡음 — 문자 단위로 재확인.
- **결손 #3 (신규 생성 시 멘션 수신자 누락)**: `IssueServiceImpl.createIssue()`(→`publishNewIssueNotification`), `PostingServiceImpl.createPosting()`(→`publishNotification`), `PullRequestServiceImpl.createPullRequest()` 세 곳 모두 `watchService.findActualWatchers(baseWatchers = setOf(작성자/기여자))`만으로 receivers를 구성하고 `extractMentionedUsers()` 호출이 전혀 없음을 세 파일 모두에서 실제 함수 본문으로 확인. 반면 yona는 `getReceivers(AbstractPosting, User)`/`getDefaultReceivers(PullRequest)`가 `abstractPosting.getWatchers()` + `getMentionedUsers(body)`를 합산함을 본문으로 재확인.
- **결손 #4 (MEMBER_ENROLL_REQUEST 워치 무시)**: yona `getReceivers(Project)`는 매니저 목록을 순회하며 `Watch.isWatching(manager, project.asResource())`인 사람만 추가. yona `ProjectUserServiceImpl.getProjectManagers()`는 `findByProjectId().filter{role==MANAGER}.map{user}`로 워치 여부 검사 자체가 없으며, 이 함수가 가입신청(`enroll`)·취소(`cancelEnroll`) 양쪽 모두에서 그대로 receivers로 쓰임을 호출부까지 확인.

### 수정/정정 필요

없음. 어제 보고서가 "결손"으로 지목한 항목 1~4는 이번 Serena 기반 재검증(심볼 본문 직접 대조 + 호출부 추적)에서 문구와 근본 원인까지 정확히 일치했다. 결손 #5(조직 가입 oldValue 페어링)는 yona `OrganizationServiceImpl.kt` 접근 도중 아래에 서술한 프로젝트 컨텍스트 전환 오류로 인해 이번 세션에서 직접 재확인하지 못했다 — 오류로 판정하는 것이 아니라 "미검증"으로 남긴다.

### 신규 발견(어제 놓친 것)

1. **`Mention.update()`/`getMentioningIssueIds()`는 yona 자체에서 죽은 코드가 아님**: `find_referencing_symbols`가 (아래 서술할 도구 결함으로) 신뢰할 수 없어 `search_for_pattern`으로 대체 확인한 결과, `Mention.update()`는 `AbstractPosting.updateMention()`(→모든 `save()`/`update()`/`saveWithNumber()`/`directSave()`에서 자동 호출)과 `Comment.java:90`에서, `Mention.getMentioningIssueIds()`는 `IssueSearchCondition.java`(2곳)·`SearchCondition.java`·`Issue.java`에서 실사용됨을 확인했다. 즉 결손 #1은 "이식 안 해도 되는 죽은 코드"가 아니라 실제 활성 기능(멘션 인덱스 + "나를 멘션한 이슈" 검색)의 결손이 맞다 — 어제 보고서의 결론을 더 단단하게 뒷받침하는 근거.
2. **도구 자체의 한계**: 이번 세션에서 Serena의 Kotlin 언어서버(yona용)가 `find_symbol`/`get_symbols_overview`/`find_referencing_symbols` 호출 시 반복적으로 `Multilspy Kotlin Client` 초기화 실패로 크래시했다(재시도해도 동일). 따라서 yona(Kotlin) 쪽은 진짜 심볼 참조 해석이 아니라 `search_for_pattern`(Serena의 grep 대체 도구) 기반 텍스트 매칭으로만 검증할 수 있었다 — 어제 지적된 "grep 기반 조사의 한계"를 이 환경에서는 yona 쪽에 한해 완전히 벗어나지 못했다는 뜻이다. 다만 yona(Java) 쪽은 언어서버가 정상 동작해 실제 심볼 본문·호출부를 확인할 수 있었다.
3. **프로젝트 컨텍스트 전환 불안정**: `activate_project`로 yona/yona를 오갈 때, 성공 메시지가 나와도 다음 호출이 조용히 이전 프로젝트로 되돌아가는 현상이 다수 발생했다(파일 없음 에러 또는 엉뚱한 프로젝트의 빈 검색 결과로 나타남). 매 호출 전 재활성화 및 `list_dir` 등으로 교차 검증하지 않았다면 오탐(false negative, 예: "Mention.update 참조 0건")을 사실로 보고할 뻔했다 — 이번 재검증 프로세스 자체의 신뢰도에 영향을 준 요인으로 기록해 둔다.

### 총평

어제 보고서의 이 도메인 서술은 이번 Serena 기반 재검증(심볼 본문 대조 + 호출부 추적)에서 결손 1~4 모두 문구·근본 원인 수준까지 정확했고, "완전히 이식됨" 항목들도 실제 코드로 재확인됐다 — grep으로 작성됐음에도 결과 자체는 신뢰할 만하다. 다만 yona 쪽은 이번 세션에서도 Kotlin 언어서버 결함으로 진짜 심볼 참조 해석은 불가능했고 패턴 매칭으로 대체했다는 한계가 있으므로, 결손 #5(저위험으로 표시된 조직 가입 항목)는 이번에 독립적으로 재확인되지 않은 채 남아 있다.

---

## 접근제어/검증 핵심 유틸 — Serena 재검증

### 확인됨(어제 보고서 정확함)
- 완전히 이식됨 목록 전체(AccessControl, ReservedWordsValidator, LoginIdFormatValidator, AutoLinkRenderer 5단계 파이프라인) — yona `AccessControl.kt`/`AutoLinkRenderer.kt`/`ReservedWordsValidator.kt`/`LoginIdFormatValidator.kt`를 직접 Read로 전문 대조한 결과 구조·정규식·5단계 패턴(Path+Issue → Issue only → Path+SHA → SHA only → User/Org/Project)이 그대로 확인됨.
- 결손 1(`renderFileInCodeBrowser`/`renderFileInReadme` 미이식): yona `CodeViewController.kt` 전체를 Read했으나 `markdownService` 필드/임포트 자체가 없음. yona 쪽은 `search_for_pattern`으로 `app/views/code/partial_view_file.scala.html:111`, `app/views/project/partial_readme.scala.html:40`에서 실제 호출됨을 확인 — 죽은 코드 아님, 실사용 경로.
- 결손 2(`checkReferrer` 범위 제한적 이식): `noreferrer`/`checkReferrer` 검색 결과 `NotificationMailBodyProcessor.kt`(19-20, 41, 60줄)에만 있고 `MarkdownServiceImpl.kt`에는 전혀 없음(해당 파일은 `sanitize()` → `autoLinkRenderer.render()`만 호출).
- 결손 3(`transformIssueLink` 미이식): `MarkdownServiceImpl.kt` 전문 확인 결과 이슈 URL 자동 승격 단계 없음. `domain/issue/IssueReferenceParser.kt`가 존재하지만 이는 커밋 메시지의 `#번호` 파싱용(별개 기능)이라 대체물 아님.
- 결손 4(`lang` 오버로드/배치 로케일 미전파): `MarkdownService.kt` 인터페이스에 `render(body)`/`render(body,breaks)`/`render(body,breaks,project)` 3종뿐, lang 파라미터 없음. `NotificationMailDigestScheduler.kt:208`에서 `markdownService.render(message, true, projectOf(main))`을 호출하는데, 정작 `sendMail(...)`이 수신자별 `Locale`(167줄)을 만들어도 markdown 렌더 경로로는 전달할 방법이 없음을 코드로 직접 확인. `AutoLinkRenderer.kt:254-255`의 `LocaleContextHolder.getLocale()`도 스케줄러 스레드에선 요청 스코프 로케일이 아니므로 실사용 영향 주장은 근거 있음.
- 결손 6(`toValidSHALink`의 `isCodeAvailable()` 부재): yona `AutoLinkRenderer.kt:214-217`에서 `project.vcs?.uppercase() != "GIT"`만 검사, `isCodeAvailable` 문자열은 yona `src/` 전체에서 0건. yona `AutoLinkRenderer.java:274`의 `!project.isCodeAvailable() || !project.isGit()`와 대조되어 정확.

### 수정/정정 필요
1. **결손 5의 심각도 표기는 정확했지만 근거가 더 필요함**: `isProjectResourceCreatable(User, Project, ResourceType)`은 실제로 yona `AccessControl.kt:87`에 존재하며, `IssueLabelController.kt`(3곳)·`CodeHistoryController.kt`·`IssueViewController.kt`(2곳)·`MilestoneController.kt`·`IssueController.kt`·`IncomingMailProcessingService.kt`에 배선까지 완료돼 있음(`docs/parity/index.md` P1-94/95 로그로 교차 확인). 즉 "리소스 생성 권한 판단" 로직의 대부분(프로젝트 스코프)은 이미 이식·배선됨 — 어제 보고서가 미이식이라 지목한 것은 그중 프로젝트에 속하지 않는 글로벌 리소스(임시 첨부용) 케이스인 `isGlobalResourceCreatable`/`isResourceCreatable` 뿐이며, 이 두 함수는 yona에 정말 0건. 결론적으로 P2/"영향 제한적" 표기는 정확하나, 독자가 "리소스 생성 권한 전체"가 빠진 것으로 오해하지 않도록 범위를 명시할 필요가 있음.
2. **"`isAllowedIfAuthor`/`isAllowedIfAssignee`... 1:1 대응"은 문구가 부정확**: yona에는 이 두 이름의 독립 함수가 없음. `AccessControl.kt`의 리소스별 `isAllowed(...)` 오버로드(295-546줄) 내부에 `val isAuthor = ...`/`val isAssignee = ...` 지역 변수로 인라인돼 있음. 반면 `isAllowedIfSharer`(204·213줄)와 `isAllowedIfGroupMember`(185줄)는 실제로 이름 그대로 존재. 동작 결과는 동등하지만 "1:1 대응"이라는 표현은 구조적으로 부정확.

### 신규 발견(어제 놓친 것)
결손 항목으로서 신규 발견은 없음. 다만 방법론상 특기할 점: `find_referencing_symbols`(LSP 기반)는 yona `Markdown.renderFileInCodeBrowser/renderFileInReadme`(Play `.scala.html` 템플릿에서 호출)와 `AutoLinkRenderer.toValidSHALink` 두 오버로드(같은 클래스 내부 익명 `ToLink` 구현체를 통한 간접 호출)에 대해 모두 참조 0건으로 보고했으나, 실제로는 둘 다 살아있는 호출부가 있었다(`search_for_pattern`/Read로 확인). 즉 이번 케이스에서는 심볼릭 도구가 오히려 grep보다 더 많이 놓쳤을 가능성이 있어(Scala 템플릿 호출·익명 클래스 호출은 텍스트 검색이 더 잘 잡음), Serena 사용 시에도 `find_referencing_symbols` 단독 결과로 "죽은 코드"를 단정하지 말고 `search_for_pattern`을 병행해야 함.

### 총평
어제 보고서의 6개 결손 항목은 모두 Serena/Read로 재확인해도 사실관계가 정확했고, "완전히 이식됨" 판정 4건도 구조 대조상 타당했다. 다만 결손 5의 서술은 좀 더 명확한 범위 한정이, 완전 이식 항목의 "1:1 대응" 문구는 소폭 정정이 필요하다. 전반적으로 이 도메인에 대한 어제 보고서는 신뢰할 수 있는 수준이며, grep 기반 조사였음에도 실질적인 오탐/누락은 발견되지 않았다.