---
id: comment-trimming-progress
type: tracking
created: 2026-09-09
updated: 2026-09-09
status: in-progress
relates_to: [comment-trimming-guidelines]
---

# 주석 트리밍 진행 현황

코드 전반의 티켓ID/날짜/작업일지성 주석을 정리하는 작업의 진행 상황이다. 기준은
[[comment-trimming-guidelines]] 참고. 다른 세션에서 이어서 할 때는 아래 "잔여 파일"
표를 위(참조 밀도 높은 순)부터 진행하면 된다.

## 완료 (29개 kt 파일)

순수 주석 정리(로직 변경 없음). 마지막 몇 개 파일은 컴파일 검증을 생략하고 저장만 했다 —
다음 세션에서 `./gradlew compileKotlin -Dyona.it.db=h2`로 전체 재검증 먼저 할 것.
테스트를 통한 회귀 재검증도 아직 못 했다.
(참고: 이 저장소는 다른 세션과 동시에 `./gradlew test`를 돌리면 Gradle 결과파일이 충돌하는
경우가 있다 — 가이드라인 문서의 "알려진 인프라 이슈" 참고, 실제 코드 문제 아님.)

- [x] src/main/kotlin/com/github/yonaprojects/yona/config/AccessLogFilter.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/config/ApiTokenAuthenticationFilter.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/config/YonaCubridDialect.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/AuthorizationServerConfig.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/config/security/AccessControl.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/config/ssh/SshInternalController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/config/ssh/SshRelayServer.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/config/ssh/YonaMinaSshServer.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/event/HgPostReceiveEventListener.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueServiceImpl.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/mail/IncomingMailProcessingService.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/JpaRegisteredClientRepository.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectServiceImpl.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestService.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestServiceImpl.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/sshkey/SshAuthServiceImpl.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/GitRepository.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/HgCommit.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/HgRepository.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/CommentController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/IssueController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/ProjectApiController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/ProjectController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/PullRequestApiController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/PullRequestController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/PullRequestViewController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/RestApiResponseDto.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/UserViewController.kt
- [x] src/main/kotlin/com/github/yonaprojects/yona/web/WebhookController.kt

## 잔여 kt/java 파일 (참조 밀도 높은 순, 총      247개)

숫자는 티켓/날짜/GL-태그 등 정리 대상 참조 라인 수(대략적 지표, 실제 작업량과 항상 비례하진
않음).

| 참조수 | 파일 |
|---|---|
| 18 | `src/main/kotlin/com/github/yonaprojects/yona/web/IssueApiController.kt` |
| 18 | `src/main/kotlin/com/github/yonaprojects/yona/web/BoardViewController.kt` |
| 18 | `src/main/kotlin/com/github/yonaprojects/yona/domain/webhook/WebhookServiceImpl.kt` |
| 17 | `src/main/kotlin/com/github/yonaprojects/yona/domain/board/PostingServiceImpl.kt` |
| 16 | `src/main/kotlin/com/github/yonaprojects/yona/web/ProjectViewController.kt` |
| 15 | `src/main/kotlin/com/github/yonaprojects/yona/domain/site/DataBackupServiceImpl.kt` |
| 14 | `src/main/kotlin/com/github/yonaprojects/yona/config/SecurityConfig.kt` |
| 13 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt` |
| 13 | `src/main/kotlin/com/github/yonaprojects/yona/domain/comment/CommentServiceImpl.kt` |
| 11 | `src/main/kotlin/com/github/yonaprojects/yona/web/BoardController.kt` |
| 10 | `src/main/kotlin/com/github/yonaprojects/yona/web/IssueViewController.kt` |
| 9 | `src/main/kotlin/com/github/yonaprojects/yona/web/UserController.kt` |
| 9 | `src/main/kotlin/com/github/yonaprojects/yona/mcp/PullRequestMcpTools.kt` |
| 9 | `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/ImapMailboxPoller.kt` |
| 8 | `src/main/kotlin/com/github/yonaprojects/yona/web/ProjectRestApiController.kt` |
| 8 | `src/main/kotlin/com/github/yonaprojects/yona/web/IssueRestApiController.kt` |
| 8 | `src/main/kotlin/com/github/yonaprojects/yona/web/CodeViewController.kt` |
| 8 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/HgPushHooks.kt` |
| 8 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestRepository.kt` |
| 8 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueService.kt` |
| 8 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueRepository.kt` |
| 8 | `src/main/kotlin/com/github/yonaprojects/yona/config/GitServletConfig.kt` |
| 7 | `src/main/kotlin/com/github/yonaprojects/yona/web/OrganizationViewController.kt` |
| 7 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectRepository.kt` |
| 7 | `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt` |
| 7 | `src/main/kotlin/com/github/yonaprojects/yona/config/TemplateHelper.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/web/SearchController.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/web/ProjectMemberController.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/web/MilestoneViewController.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/web/MilestoneController.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/web/BoardApiController.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/mcp/IssueMcpTools.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/GitPushHooks.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/DiagnosticService.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CodeReviewServiceImpl.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthRegisteredClient.kt` |
| 6 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/ResourceServerConfig.kt` |
| 5 | `src/main/kotlin/com/github/yonaprojects/yona/web/UserIssueStatusRestApiController.kt` |
| 5 | `src/main/kotlin/com/github/yonaprojects/yona/mcp/McpScopeGuard.kt` |
| 5 | `src/main/kotlin/com/github/yonaprojects/yona/domain/sshkey/SshAuthService.kt` |
| 5 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationMailDigestScheduler.kt` |
| 5 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueLabelServiceImpl.kt` |
| 5 | `src/main/kotlin/com/github/yonaprojects/yona/domain/branchprotection/ProtectedBranch.kt` |
| 5 | `src/main/kotlin/com/github/yonaprojects/yona/config/vcs/RepoAccessPolicy.kt` |
| 5 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/ResourceIndicatorTokenCustomizer.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/web/SearchRestApiController.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/web/MentionController.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/web/AuthController.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/web/AttachmentController.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/SvnRepository.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/PlayRepository.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectUserServiceImpl.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthObjectMapper.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthAuthorization.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthAppRegistrationService.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationMessageResolver.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationEventMerger.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentRepository.kt` |
| 4 | `src/main/kotlin/com/github/yonaprojects/yona/config/ssh/YonaSshGitCommand.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/web/TagViewController.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/web/ReviewViewController.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/web/GlobalModelAttributeAdvice.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/web/BranchApiController.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/mcp/McpToolSupport.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/webhook/WebhookNotificationEventListener.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/watch/WatchServiceImpl.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/YonaUpdateService.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/SearchServiceImpl.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownService.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/site/SiteService.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestEventRecorder.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequest.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/Project.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthAuthorizationRepository.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/JpaOAuth2AuthorizationService.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationEventRecorder.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/milestone/MilestoneServiceImpl.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/MailService.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/InboundEmailMessage.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/event/PullRequestMergeEventListener.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/event/GitPostReceiveEventListener.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/board/PostingRepository.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentServiceImpl.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/domain/apitoken/ApiToken.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/config/svn/SvnAuthorizationFilter.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/config/ssh/SshInternalSecretProvider.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/config/ssh/HgSshProtocolHandler.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/config/hg/HgAuthorizationFilter.kt` |
| 3 | `src/main/kotlin/com/github/yonaprojects/yona/config/git/GitAuthorizationFilter.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/WatchController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/UserStatusRestApiController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/TagRestApiController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/SiteApiController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/RedirectUrlEncoding.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/OrganizationRestApiController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/OAuthConsentController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/LabelRestApiController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/IndexController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/HgController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/DeployKeyController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/web/BranchProtectionController.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/watch/WatchService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/RepositoryService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/PushedBranch.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/BareCommit.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/user/YonaUserDetails.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/SearchResult.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/LineEnding.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/Comment.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/ChecksumUtils.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/AutoLinkRenderer.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestTimelineItem.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestReview.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/TitleHeadServiceImpl.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectNameValidator.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthAuthorizedAppsService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthAuthorizationConsent.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationUrlResolver.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationEventRepository.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/INotificationEvent.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/milestone/MilestoneService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/IncomingMailOutcome.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/RecentIssueService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueShareServiceImpl.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueEventRepository.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueEventRecorder.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueEvent.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/gpgkey/GpgSignatureVerifier.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/deploykey/DeployKey.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/board/PostingService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentCleanupScheduler.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/domain/apitoken/ApiTokenAuthorizer.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/sso/YonaSaml2AuthenticatedPrincipal.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/sso/YonaRelyingPartyRegistrationRepository.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/sso/YonaClientRegistrationRepository.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/sso/EnterpriseOidcUserService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/ProtectedResource.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/JwkKeyPairProvider.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/AudienceValidator.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2/OAuth2AccountMergeService.kt` |
| 2 | `src/main/kotlin/com/github/yonaprojects/yona/config/git/GitProjectVisitRecorder.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/WebhookRestApiController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/TagApiController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/StatisticsViewController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/StatisticsController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/SsoAdminController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/ReviewApiController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/ProjectPermissionRestApiController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/OrganizationController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/OAuthProtectedResourceMetadataController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/OAuthAppsAdminController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/MilestoneApiController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/HgServletRequestWrapper.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/GlobalApiController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/FavoriteController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/web/BranchViewController.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/mcp/McpToolsConfig.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/webhook/WebhookThreadRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/webhook/WebhookThreadRecorder.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/webhook/PushedHgCommits.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/webhook/PushedCommits.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/VcsType.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/GitTag.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/GitCommit.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/Commit.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/user/UserSetting.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/user/Saml2UserProvisioningService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/user/ReservedWordsValidator.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/user/OidcUserProvisioningService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/user/LoginIdFormatValidator.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/user/LdapService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/ZipUtil.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/PropertyService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/Property.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownRenderCache.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/HistoryUtil.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/FileUtil.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/DiffUtil.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/support/AbstractPosting.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/sso/SsoSettingsService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/sso/Saml2SsoSettings.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/sso/OidcSsoSettings.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/sshkey/SshPublicKeyFingerprint.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/sshkey/SshKey.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestMergeResult.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestEvent.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestCommit.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CommentThreadRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CodeReviewService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/TitleHeadService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/TitleHeadRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/TitleHead.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/project/RecentProjectRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthScopeAuthorizer.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthRegisteredClientRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/OAuthAuthorizationConsentRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/McpOAuthScopes.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/oauth2server/JpaOAuth2AuthorizationConsentService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationMailRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationMailRenderer.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationMailBodyProcessor.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/NotificationCleanupScheduler.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/notification/MergedNotificationEvent.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionServiceImpl.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/Mention.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/RecentIssue.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueTimelineItem.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueSpecification.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueSharer.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueLabelService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueLabelRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueFilterType.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueCommentRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/Issue.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/AssigneeRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/gpgkey/GpgVerificationStatus.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/gpgkey/GpgPublicKeyParser.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/gpgkey/GpgKey.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/event/HgPostReceiveEvent.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/enumeration/SearchType.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/enumeration/Operation.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/enumeration/EventType.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/deploykey/DeployKeyServiceImpl.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/deploykey/DeployKeyService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/board/PostingCommentRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/LogoValidator.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/apitoken/ApiTokenService.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/apitoken/ApiTokenScopeGroup.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/apitoken/ApiTokenRepository.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/domain/apitoken/ApiTokenHasher.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/YonaCubridNamingStrategy.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/sso/YonaOidcUser.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/sso/EnterpriseSaml2ResponseAuthenticationConverter.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/ssh/GitSshProtocolHandler.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/OAuthApiScopeAuthorizationFilter.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/McpAuthenticationEntryPoint.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/DcrRateLimitFilter.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/git/DeployKeyAuthenticationToken.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/git/DeployKeyAuthenticationProvider.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/CacheConfig.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/BootstrapSetupInterceptor.kt` |
| 1 | `src/main/kotlin/com/github/yonaprojects/yona/config/AsyncConfig.kt` |

## 잔여 템플릿 파일 (총       39개, 아직 손대지 않음)

| 참조수 | 파일 |
|---|---|
| 5 | `src/main/resources/templates/issue/view.html` |
| 5 | `src/main/resources/templates/code/view.html` |
| 4 | `src/main/resources/templates/pullrequest/view.html` |
| 4 | `src/main/resources/templates/code/history.html` |
| 3 | `src/main/resources/templates/reviewthread/partial_list.html` |
| 3 | `src/main/resources/templates/pullrequest/edit.html` |
| 3 | `src/main/resources/templates/pullrequest/create.html` |
| 2 | `src/main/resources/templates/user/edit_tokens.html` |
| 2 | `src/main/resources/templates/user/edit_ssh_keys.html` |
| 2 | `src/main/resources/templates/user/edit_oauth_apps_owned_new.html` |
| 2 | `src/main/resources/templates/user/edit_gpg_keys.html` |
| 2 | `src/main/resources/templates/pullrequest/partial_list.html` |
| 2 | `src/main/resources/templates/pullrequest/partial_info.html` |
| 2 | `src/main/resources/templates/oauth2/consent.html` |
| 2 | `src/main/resources/templates/milestone/list.html` |
| 2 | `src/main/resources/templates/board/view.html` |
| 1 | `src/main/resources/templates/user/edit_tokens_new.html` |
| 1 | `src/main/resources/templates/user/edit_ssh_keys_new.html` |
| 1 | `src/main/resources/templates/user/edit_oauth_apps.html` |
| 1 | `src/main/resources/templates/user/edit_oauth_apps_owned.html` |
| 1 | `src/main/resources/templates/user/edit_gpg_keys_new.html` |
| 1 | `src/main/resources/templates/site/userList.html` |
| 1 | `src/main/resources/templates/search/list.html` |
| 1 | `src/main/resources/templates/reviewthread/list.html` |
| 1 | `src/main/resources/templates/pullrequest/partial_pull_request_event.html` |
| 1 | `src/main/resources/templates/pullrequest/partial_merge_result.html` |
| 1 | `src/main/resources/templates/project/setting_deploykeys.html` |
| 1 | `src/main/resources/templates/project/fork.html` |
| 1 | `src/main/resources/templates/milestone/view.html` |
| 1 | `src/main/resources/templates/milestone/partial_status.html` |
| 1 | `src/main/resources/templates/login.html` |
| 1 | `src/main/resources/templates/issue/edit.html` |
| 1 | `src/main/resources/templates/common/partial_history.html` |
| 1 | `src/main/resources/templates/common/child_commentForm.html` |
| 1 | `src/main/resources/templates/common/attachmentFile.html` |
| 1 | `src/main/resources/templates/code/diff.html` |
| 1 | `src/main/resources/templates/code/compare.html` |
| 1 | `src/main/resources/templates/code/compare_svn.html` |
| 1 | `src/main/resources/templates/board/create.html` |

## 재확인 방법

```bash
# kt/java
find src/main/kotlin src/main/java -type f \( -name "*.kt" -o -name "*.java" \) | while IFS= read -r f; do
  c=$(grep -cE "yona-wiki|P[0-9]-[0-9][0-9]*|20[0-9]{2}-[0-9]{2}-[0-9]{2}|TASK-[0-9]|실측|재현|코디네이터|GL-" "$f" 2>/dev/null)
  [ "$c" -gt 0 ] && echo -e "$c	$f"
done | sort -rn

# 템플릿
find src/main/resources/templates -type f -name "*.html" | while IFS= read -r f; do
  c=$(grep -cE "yona-wiki|P[0-9]-[0-9][0-9]*|20[0-9]{2}-[0-9]{2}-[0-9]{2}|TASK-[0-9]|실측|재현|코디네이터|GL-" "$f" 2>/dev/null)
  [ "$c" -gt 0 ] && echo -e "$c	$f"
done | sort -rn
```
