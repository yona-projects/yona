---
id: batch-26-35
type: batch
status: done
created: 2026-08-25
updated: 2026-08-25
relates_to: []
source: docs/COVERAGE_BACKLOG.md:172-181 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-25, 26~35차 배치 완료 후)

- 추가 완료([x], 44개): `ResourceType`, `Operation`, `RecentIssueService`, `IssueEventRecorderKt`, `IssueSharer`, `UserProjectNotification`, `NotificationEventRecorder`, `NotificationMailBodyProcessor`, `NotificationCleanupScheduler`, `TitleHeadServiceImpl`, `UpdateProjectParam`, `TitleHead`, `CommentThread`, `CommitComment`, `NonRangedCodeCommentThread`, `PullRequestEventRecorderKt`, `AbstractPosting`, `DatabaseInitializer`, `Email`, `YonaUserDetails`, `FavoriteIssue`, `Unwatch`, `Watch`, `BranchApiController`, `WebhookController`, `CommitResponse`, `PasswordResetController`, `HistoryDto`, `AuthController`, `BootstrapSetupController`, `GlobalExceptionHandler`, `StatisticsController`, `NotificationController`, `CommentThreadController`, `MarkdownController`, `CodeRangeRequest`, `AssigneeIdForm`, `MilestoneIdForm`, `SvnServletRequestWrapper`, `MigrationViewController`, `GlobalModelAttributeAdvice`, `MarkdownRenderRequest`
- 구조적 한계로 최대치 도달([i], 12개): `MentionServiceImpl`(BRANCH 90.5%), `NotificationMailRenderer`(75.0%), `Project`(94.4%), `RecentProjectRepository`(80.0%, 추가로 `$DefaultImpls` 미러 메서드 도달 불가 신규 확인), `PullRequestCommit`(80.0%), `FileUtil`(81.2%, Tika `tika-mimetypes.xml` 근거로 `audio/ogg`+`.ogv` 상호배타 확정), `DiffUtil`(85.7%), `LdapQueryBuilder`(94.4%), `FavoriteOrganization`(BRANCH 50.0%, METHOD 90.9%), `FavoriteProject`(BRANCH 75.0%, METHOD 92.3%), `WebhookRepository`(`$DefaultImpls`가 구버전 바이너리 호환용 미러 메서드로 일반 호출 문법상 도달 불가함을 `javap`로 확정), `MessagesController`(BRANCH 92.3%), `CompareViewController`(90.0%), `SvnController`(전체 스위트 실행 시 물리 저장소 없음 예외 테스트의 환경 의존적 플레이키니스 관측, 단독 실행 시엔 목표에 근접) — 각 행에 상세 근거 명시
- 작업 방식: 사용자 지시로 포크 에이전트에 "테스트 코드 작성만" 병렬 위임(gradle 실행은 메인 세션만 담당)하는 방식으로 5개씩 10개 배치 진행. 포크가 gradle을 실행하려는 시도가 재차 관측돼 `TaskStop`으로 강제 종료 후 `pkill -9 -f GradleWorkerMain`+`clean compileKotlin compileTestKotlin`로 복구한 사례 있었음(이후 프롬프트에 "완료 후 즉시 도구 호출 중단" 지시를 강화해 재발 억제)
- mockk 공통 함정 재확인: `beforeTest { clearMocks(...) }` 누락 시 `it{}` 블록 간 스텁/호출횟수가 누적돼 `MockKException`/`verify(exactly=N)` 실패 발생(`IssueEventRecorderKt`/`PullRequestEventRecorderKt`/`TitleHeadServiceImpl`/`NotificationCleanupScheduler`에서 재발·수정)
- MockMvc 관련 재발 패턴 확정: `redirect:` 뷰 반환 시 `status().isOk` 대신 `status().is3xxRedirection` 사용 필요(`BootstrapSetupController`/`MigrationViewController`), 매핑 경로와 뷰 이름이 같으면 "Circular view path" 발생(`BootstrapSetupController`, 커스텀 `ViewResolver`로 해결), `UsernamePasswordAuthenticationToken`은 2-인자 생성자가 `authenticated=false` 기본값이라 인증 성공 시나리오엔 3-인자(authorities 포함) 생성자 필요(`GlobalModelAttributeAdvice`)
- 이번 배치 신규 실버그/죽은코드 없음
- 전체 회귀(전 스위트) 재확인 통과(BUILD SUCCESSFUL, 5분 57초)
