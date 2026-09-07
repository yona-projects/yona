---
id: trivial-appendix
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

## 부록 — trivial 심볼 집계 (1414개, 목록 생략)

getter/setter 메서드 및 단순 필드/상수/enum상수 선언으로 분류되어 버킷 C에서 제외한 심볼들. 대부분 Kotlin data class/JPA 엔티티의 자동생성 접근자에 대응하며, 개별 대응 주석 없이도 이식된 것으로 간주 가능하나 확정하지 않음.

**주의(2026-08-26 사용자 지적)**: 이 1,414개 분류는 이 세션 시점의 스냅샷 판정이지 영구 고정값이 아니다. `docs/PARITY_BACKLOG.md`에 신규 등록한 P1-145~147/P2-47~53(특히 P1-147 `ResourcePersistAdapter`의 Watch/Unwatch 정리 로직 추가, P1-145 `ExConstraints`의 프로젝트명 검증 추가)을 실제로 구현하면 관련 클래스(`IssueServiceImpl.kt`/`PostingServiceImpl.kt`/`ProjectServiceImpl.kt`/`ProjectViewController.kt` 등)에 새 코드가 추가되므로, 그 클래스에 속한 trivial 심볼의 실제 필요 여부·구현 상태가 바뀔 수 있다. 이 부록은 각 티켓 구현 후 관련 클래스만이라도 재확인하는 것을 권장하며, 자동으로 갱신되지 않는다는 점을 명시한다.

- `app/models/SearchResult.java`: 54개
- `app/models/Project.java`: 46개
- `app/models/support/SearchCondition.java`: 36개
- `app/models/User.java`: 33개
- `app/models/PullRequest.java`: 33개
- `app/playRepository/GitRepository.java`: 32개
- `app/controllers/UserApp.java`: 30개
- `app/models/History.java`: 28개
- `app/models/NotificationEvent.java`: 28개
- `app/playRepository/FileDiff.java`: 25개
- `app/models/Issue.java`: 22개
- `app/playRepository/PlayRepository.java`: 20개
- `app/controllers/ProjectApp.java`: 19개
- `app/playRepository/Commit.java`: 19개
- `app/data/exchangers/CommentThreadDataExchanger.java`: 18개
- `app/utils/Config.java`: 16개
- `app/controllers/api/IssueApi.java`: 15개
- `app/utils/RouteUtil.java`: 15개
- `app/data/exchangers/IssueDataExchanger.java`: 15개
- `app/data/exchangers/ProjectDataExchanger.java`: 15개
- `app/models/NotificationMail.java`: 15개
- `app/models/PullRequestCommit.java`: 15개
- `app/playRepository/GitBranch.java`: 15개
- `app/utils/LdapService.java`: 14개
- `app/models/CandidateUser.java`: 14개
- `app/mailbox/MailboxService.java`: 13개
- `app/models/support/LdapUser.java`: 13개
- `app/playRepository/BareCommit.java`: 13개
- `app/utils/PlayServletResponse.java`: 12개
- `app/data/exchangers/UserDataExchanger.java`: 12개
