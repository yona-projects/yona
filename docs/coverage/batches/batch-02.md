---
id: batch-02
type: batch
status: done
created: 2026-08-24
updated: 2026-08-24
relates_to: []
source: docs/COVERAGE_BACKLOG.md:41-48 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-24 00:45, 2차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **265개**(-3, AccessControl은 근접했으나 아직 미달)
- 라인: 79.1%, 분기: 61.3%, 메서드: 74.8%, 클래스: 91.7%
- 추가 완료([x]): `NotificationMessageResolver`(BRANCH 96.8%), `WebhookServiceImpl`(95.2%)
- 구조적 한계로 최대치 도달([i]): `IssueShareServiceImpl`(BRANCH 93.9%, 도달 가능 분기 100% 커버 — `type` 파라미터 미사용 실버그 확정)
- 대폭 개선했으나 아직 미달([~]): `AccessControl`(BRANCH 38.4%→89.7%, 141개 남음, `isAllowed(...)` 오버로드 10종에 분산 — 다음 배치 최우선 마무리 대상)
