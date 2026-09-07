---
id: batch-43-stale-audit
type: batch
status: done
created: 2026-08-25
updated: 2026-08-25
relates_to: []
source: docs/COVERAGE_BACKLOG.md:243-262 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-25, 43차 배치 이후 stale 감사)

- **사용자 지시("백로그 파일들 stale한 내용들이 있을거야. 수정해줘")로 43차 배치의 "전 항목 완료" 선언을 전체 클린 `./gradlew test jacocoTestReport` 결과와 재대조**한 결과, `[x]`로 표시돼 있던 항목 중 **42개**가 실제로는 95% 미달임을 발견 — 전부 `[~]`(재작업 필요)로 되돌리고 실측 LINE/BRANCH/METHOD/CLASS 수치와 함께 사유를 기록함(이전 완료 기록도 notes에 `[기존 기록: ...]`로 보존).
  - 되돌린 42개: `GitServletConfig`, `YonaAuthenticationSuccessHandler`, `ApiTokenAuthenticationFilter`, `GitAuthorizationFilter`, `OAuth2UserInfoFactory`, `SvnAuthorizationFilter`, `AttachmentServiceImpl`, `Attachment`, `PostingServiceImpl`, `Posting`, `PostingComment`, `CommentServiceImpl`, `GitPostReceiveEventListener`, `PullRequestMergeEventListener`, `IssueSpecification`, `IssueLabelServiceImpl`, `MailServiceImpl`, `MilestoneServiceImpl`, `OrganizationServiceImpl`, `ProjectUserServiceImpl`, `GitServiceImpl`, `PullRequestServiceImpl`, `CodeReviewServiceImpl`, `DataBackupServiceImpl`, `YonaUpdateService`, `MarkdownServiceImpl`, `DiagnosticService`, `ReviewThreadServiceImpl`, `PasswordResetServiceImpl`, `LdapService`, `LdapUserProvisioningService`, `UserDetailsServiceImpl`, `UserVerification`, `SvnRepository`, `RepositoryService`, `WatchServiceImpl`, `WebhookNotificationEventListener`, `AttachmentController`, `ProjectController`, `SiteApiController`, `CodeHistoryController`, `ImportApiController`
  - **추정 원인**: 완료 선언 이후(특히 TASK-0330 FQN→import 대규모 리팩터링, 58개 파일 변경) 재측정 없이 문서만 유지된 것으로 보임 — 배치별 완료 시점에 개별/부분 실행으로 확인한 수치가 이후 다른 배치의 회귀나 리팩터링으로 실제 값이 달라졌는데 전체 클린 재측정을 거치지 않아 반영되지 못한 것으로 추정(정확한 원인은 배치별 기록만으로는 특정 불가).
  - **잔여 `[ ]`/`[~]` 42개, 재작업 필요** — "백로그 전 항목 완료" 선언은 철회하고 배치를 재개해야 함.

### 재작업 진행 (2026-08-26)

- `CommentServiceImpl`(batch4~6 대상, 아직 미검증): fork가 신규 `CommentServiceImplSpec.kt` 작성 시 Serena `create_text_file` 대신 `Write` 도구를 사용했다고 자진 보고(표준 작업 규칙 위반). 내용 자체는 다음 배치에서 gradle 검증 시 함께 확인 예정 — 파일 유효성엔 영향 없을 것으로 보이나 규칙 위반 사실은 기록.
- `MailServiceImpl` 완료([x], TASK-0343): `sendNotificationMail`의 replyTo/references 공백뿐 케이스 보강, 잔여 2건 구조적 도달 불가 확정.
- 1차 배치 5개 완료: `YonaAuthenticationSuccessHandler`([i], 코드 변경 없이 재검증만), `LdapService`([x], **환경 문제 해결** — Testcontainers가 Podman 로컬 소켓을 못 찾던 문제를 `build.gradle.kts`의 `DOCKER_HOST` 자동 감지로 해결, 다른 Testcontainers 기반 항목에도 영향 가능), `GitServletConfig`([x]), `GitServiceImpl`([x]), `PasswordResetServiceImpl`([i]).
- 2차 배치 5개 완료: `MilestoneServiceImpl`([x]), `GitPostReceiveEventListener`([x]), `RepositoryService`([x]), `ImportApiController`([x]), `CodeHistoryController`([x]) — 전부 목표 달성. **배치 방식 변경**: fork 5개가 동시에 `./gradlew test`를 실행해 Gradle 데몬이 심각하게 경합(EOFException 반복)하는 문제가 반복 확인되어, 이후 배치부터는 fork는 테스트 코드 작성까지만 하고 gradle 실행/검증은 메인 세션이 순차적으로 수행하는 방식으로 전환.
- 3차 배치 진행(개별 검증, gradle은 메인 세션 전담): `YonaUpdateService`([x]), `AttachmentController`([x]), `SiteApiController`([x]) — TASK-0346 커밋 완료. `IssueSpecification`([x]), `IssueLabelServiceImpl`([x], `IssueLabelServiceImplExtraSpec.kt`와 합산), `UserVerification`([x]), `ProjectController`([x]) — 검증 완료, 커밋 대기. `CodeReviewServiceImpl`은 BRANCH/METHOD 잔여 격차가 커서 `[~]` 유지(실제 bare git 저장소 커밋 인프라 필요, 추가 배치로 이연). `DiagnosticService`([i]) — `baseUrl` 기본값 분기 추가 테스트로 BRANCH/METHOD 100% 확보, 잔여 Git/SVN storage catch 2블록은 `File` API가 정상 호출에서 체크 예외를 던지지 않아 도달 불가로 확정.
- 3차 배치 마무리: `SvnRepository`([i]) — 실제 로컬 SVN 저장소(SVNKit 저수준 커밋 에디터) 기반 테스트를 대거 신규 추가해 METHOD 100% 확보, BRANCH 88.9%까지 끌어올린 뒤 잔여 8개 분기는 각각 `javap` 확인 및 실제 SVNKit 동작 검증을 거쳐 도달 불가로 확정. 이로써 배치3(`YonaUpdateService`/`AttachmentController`/`SiteApiController`/`IssueSpecification`/`IssueLabelServiceImpl`/`UserVerification`/`ProjectController`/`CodeReviewServiceImpl`(유예)/`DiagnosticService`/`SvnRepository`) 전체 처리 완료.
- **잔여 `[~]` 22개(+`CodeReviewServiceImpl` 유예 포함), 배치 계속 진행 중.**
- 4차 배치(나머지 전체) 완료: `MarkdownServiceImpl`/`CommentServiceImpl`(프로덕션 버그 발견·수정: `IssueComment`/`PostingComment`의 `parentComment` `@OneToOne`→`@ManyToOne`)/`PostingComment`/`ReviewThreadServiceImpl`/`ApiTokenAuthenticationFilter`/`GitAuthorizationFilter`/`SvnAuthorizationFilter`/`AttachmentServiceImpl`/`Attachment`/`PostingServiceImpl`/`Posting`/`PullRequestMergeEventListener`/`OrganizationServiceImpl`/`ProjectUserServiceImpl`/`CodeReviewServiceImpl`(사용자 제안대로 실제 bare git 저장소 기반으로 완료)/`OAuth2UserInfoFactory`/`DataBackupServiceImpl`(테스트 mock 버그 발견·수정)/`LdapUserProvisioningService`/`UserDetailsServiceImpl`/`WatchServiceImpl`/`WebhookNotificationEventListener` — [x] 또는 구조적 도달 불가 근거를 갖춘 [i]로 완료, 사용자 요청에 따라 각 [i] 판정 근거를 COVERAGE_BACKLOG.md뿐 아니라 실제 소스 코드 주석으로도 남김.
- **최종 프로젝트 전체 JaCoCo 커버리지(2026-08-26, 전체 스위트 클린 실행 기준): LINE 99.3%(17621/17753), BRANCH 95.7%(10710/11191), METHOD 98.4%(3390/3445), CLASS 96.0%(383/399).**
- **잔여 `[~]` 1개(`PullRequestServiceImpl`)만 남음** — METHOD는 96.7%까지 확보했으나 BRANCH(86.0%)는 diffCommits 등 git 병합 내부 로직 세부 분기가 남아 다음 배치로 이연.
