---
id: batch-01
type: batch
status: done
created: 2026-08-24
updated: 2026-08-24
relates_to: []
source: docs/COVERAGE_BACKLOG.md:33-40 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-24 00:00, 1차 배치 10개 클래스 완료 후)

- 전체 클래스: 478개, 95% 미만: **268개**(-11)
- 라인: 78.4%, 분기: 54.4%, 메서드: 74.8%, 클래스: 91.7%
- 완료([x]): `NotificationUrlResolver`, `FileDiff`, `diff_match_patch`, `MigrationService`, `SiteService`
- 구조적 한계로 목표 사실상 최대치 도달([i]): `IssueExcelService`(BRANCH 88.1%, 나머지는 Kotlin non-null 타입상 도달 불가)
- 진행 중([~], 다음 배치에서 계속): `AccessControl`(BRANCH 38.4%, 최우선), `NotificationMessageResolver`(82.8%), `WebhookServiceImpl`(79.8%), `IssueShareServiceImpl`(93.9%, 근소 미달)
