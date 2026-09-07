---
id: bucket-a-sample
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

## 버킷 A — 확인됨 (표본)

| yona 파일:라인 | 티켓 | yona 파일:범위 | 매치된 GL-ID |
|---|---|---|---|
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentCleanupScheduler.kt:21` | P2-26 | `Attachment.java:438-477` | GL-models_Attachment-035 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentService.kt:7` | P2-24 | `Attachment.java:537-582` | GL-models_Attachment-039;GL-models_Attachment-040 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentServiceImpl.kt:59` | P2-24 | `Attachment.java:537-582` | GL-models_Attachment-039;GL-models_Attachment-040 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentRepository.kt:20` | P2-26 | `Attachment.java:456-458` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentRepository.kt:25` | P2-24 | `Attachment.java:75-85` | GL-models_Attachment-013;GL-models_Attachment-014;GL-models_Attachment-015 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/board/PostingServiceImpl.kt:71` | P1-127 | `NotificationEvent.java:1380-1385` | GL-models_NotificationEvent-096 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/comment/CommentServiceImpl.kt:94` |  | `utils/JodaDateUtil.java:127-142` | GL-utils_JodaDateUtil-019;GL-utils_JodaDateUtil-020 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/comment/CommentServiceImpl.kt:239` | P1-126 | `NotificationEvent.java:1517-1528` | GL-models_NotificationEvent-107;GL-models_NotificationEvent-108 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/event/GitPostReceiveEventListener.kt:132` | P1-46 | `NotificationEvent.java:604-680` | GL-models_NotificationEvent-038;GL-models_NotificationEvent-039;GL-models_NotificationEvent-040;GL-models_NotificationEvent-041;GL-models_NotificationEvent-042;GL-models_NotificationEvent-043;GL-models_NotificationEvent-044;GL-models_NotificationEvent-045;GL-models_NotificationEvent-046 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueService.kt:50` | P1-101 | `IssueApi.java:1176-1210` | GL-controllers_api_IssueApi-064;GL-controllers_api_IssueApi-065 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueServiceImpl.kt:151` | P1-127 | `NotificationEvent.java:1380-1385` | GL-models_NotificationEvent-096 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueServiceImpl.kt:599` | P1-101 | `IssueApi.java:1176-1210` | GL-controllers_api_IssueApi-064;GL-controllers_api_IssueApi-065 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueRepository.kt:35` | P2-38 | `Issue.java:524-529` | GL-models_Issue-061 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/ImapMailboxPoller.kt:100` | P1-137 | `MailboxService.java:177-188` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/IncomingMailProcessingService.kt:384` | P2-34 | `IssueApp.java:1004-1011` | GL-controllers_IssueApp-049 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionService.kt:6` |  | `Mention.java:33-49` | GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionRepository.kt:6` |  | `Mention.java:33-49` | GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionServiceImpl.kt:14` |  | `Mention.java:33-49` | GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/milestone/MilestoneService.kt:6` |  | `MilestoneApp.java:52-53` | GL-controllers_MilestoneApp-002 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/milestone/MilestoneServiceImpl.kt:25` |  | `Milestone.java:188-230` | GL-models_Milestone-027;GL-models_Milestone-028;GL-models_Milestone-029 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt:25` | P2-19 | `FavoriteOrganization.java:38-46` | GL-models_FavoriteOrganization-007 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt:45` | P1-108 | `models/Organization.java:42` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt:97` | P2-19 | `FavoriteOrganization.java:38-46` | GL-models_FavoriteOrganization-007 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt:259` | P1-123 | `EnrollOrganizationApp.java:101-104` | GL-controllers_EnrollOrganizationApp-005 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectServiceImpl.kt:59` | P2-27 | `FavoriteProject.java:41-50` | GL-models_FavoriteProject-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectServiceImpl.kt:366` | P2-27 | `FavoriteProject.java:41-50` | GL-models_FavoriteProject-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/Project.kt:27` |  | `Project.java:131-133` | GL-models_Project-026 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectUserServiceImpl.kt:74` | P1-142 | `EnrollProjectApp.java:61-63` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectUserServiceImpl.kt:74` | P1-123 | `EnrollProjectApp.java:61-63` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectUserServiceImpl.kt:237` | P2-20 | `NotificationEvent.java:1468-1477` | GL-models_NotificationEvent-100 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CodeReviewServiceImpl.kt:212` | P1-116 | `AccessControl.java:205-301` | GL-utils_AccessControl-009 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CodeReviewServiceImpl.kt:292` | P1-116 | `AccessControl.java:205-301` | GL-utils_AccessControl-009 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CodeReviewServiceImpl.kt:319` | P1-79 | `CommentThreadApp.java:66-70` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestServiceImpl.kt:669` | P1-127 | `NotificationEvent.java:1425-1428` | GL-models_NotificationEvent-098 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestRepository.kt:44` | P2-38 | `PullRequest.java:219-225` | GL-models_PullRequest-036 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/SearchServiceImpl.kt:30` | P0-23 | `controllers/Application.java:35` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownService.kt:15` | P1-139 | `Markdown.java:346-356` | GL-utils_Markdown-017;GL-utils_Markdown-018 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownService.kt:21` | P2-02 | `Markdown.java:215-217` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/AutoLinkRenderer.kt:213` | P2-35 | `utils/AutoLinkRenderer.java:275` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/AutoLinkRenderer.kt:257` | P1-140 | `AutoLinkRenderer.java:322-327` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/DiagnosticService.kt:16` | P1-137 | `Config.java:26-39` | GL-utils_Config-004;GL-utils_Config-005 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/DiagnosticService.kt:72` | P1-137 | `MailboxService.java:176-188` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/DiagnosticService.kt:75` | P1-137 | `Config.java:26-39` | GL-utils_Config-004;GL-utils_Config-005 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/ChecksumUtils.kt:4` | P1-102 | `IssueApi.java:538-548` | GL-controllers_api_IssueApi-029 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:34` | P2-33 | `utils/Markdown.java:132-211` | GL-utils_Markdown-010;GL-utils_Markdown-011 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:41` | P2-32 | `utils/Markdown.java:104` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:79` | P1-139 | `Markdown.java:363` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:108` | P2-43 | `utils/Markdown.java:218-270` | GL-utils_Markdown-012;GL-utils_Markdown-013 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:146` | P2-32 | `utils/Markdown.java:103-130` | GL-utils_Markdown-009 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:169` | P2-33 | `utils/Markdown.java:132-159` | GL-utils_Markdown-010 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:199` | P2-33 | `utils/Markdown.java:161-211` | GL-utils_Markdown-011 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:282` |  | `Markdown.java:358-365` | GL-utils_Markdown-019 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:289` |  | `Markdown.java:367-377` | GL-utils_Markdown-021 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:301` | P2-02 | `Markdown.java:215-217` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownRenderCache.kt:5` | P2-43 | `utils/CacheStore.java:15-26` | GL-utils_CacheStore-002;GL-utils_CacheStore-003;GL-utils_CacheStore-004;GL-utils_CacheStore-005 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/YonaUpdateService.kt:30` |  | `YobiUpdate.java:40-41` | GL-models_YobiUpdate-002 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/FileUtil.kt:40` | P2-25 | `FileUtil.java:113-142` | GL-utils_FileUtil-007;GL-utils_FileUtil-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/user/LoginIdFormatValidator.kt:3` | P1-104 | `models/User.java:65-66,80` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/BareCommit.kt:83` |  | `BareCommit.java:249-286` | GL-playRepository_BareCommit-024;GL-playRepository_BareCommit-025;GL-playRepository_BareCommit-026;GL-playRepository_BareCommit-027 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/webhook/WebhookServiceImpl.kt:34` | P2-08 | `Webhook.java:178` | (매치없음-라인범위 표기 불일치) |

... 외 244건 (전체는 반정형 데이터로 별도 CSV 참고 필요)
