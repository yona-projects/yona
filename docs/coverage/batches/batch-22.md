---
id: batch-22
type: batch
status: done
created: 2026-08-25
updated: 2026-08-25
relates_to: []
source: docs/COVERAGE_BACKLOG.md:154-158 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-25, 22차 배치 완료 후)

- 추가 완료([x]): `ImportViewController`(BRANCH 98.4%), `OrganizationController`(BRANCH 96.9%), `ReviewThreadController`(BRANCH 96.7%), `WatchController`(BRANCH 96.3%), `MilestoneController`(BRANCH 95.7%) — 21차에 이어 "무거운 작업 위주" 방침 계속(미실행 라인+분기 합계 상위 5개)
- 이번 배치 신규 실버그/죽은코드 없음. 대부분의 컨트롤러가 성공 케이스만 테스트돼 있고 404/400/401/403 등 실패 분기가 광범위하게 미검증 상태였음(특히 `OrganizationController`는 6개 엔드포인트 중 절반이 아예 테스트 자체가 없었음)
