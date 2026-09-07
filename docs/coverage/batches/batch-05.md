---
id: batch-05
type: batch
status: done
created: 2026-08-24
updated: 2026-08-24
relates_to: []
source: docs/COVERAGE_BACKLOG.md:66-75 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-24 02:55, 5차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **243개**(-6)
- 라인: 88.3%, 분기: 76.7%, 메서드: 79.3%, 클래스: 94.7%
- 추가 완료([x]): `ProjectViewController`(BRANCH 95.3% — 3개 배치 걸쳐 완주), `NotificationMailDigestScheduler`(98.3%)
- 구조적 한계로 최대치 도달([i]): `BoardViewController`(94.1%), `ImapMailboxPoller`(LINE 94.4%, BRANCH 100%)
- 진행 중([~], 다음 배치 계속): `OrganizationViewController`(88.3%), `PullRequestViewController`(93.8%, 근소 미달)
- **실버그 수정 완료(2건)**: `ProjectViewController.projectLogo()`/`OrganizationViewController.organizationLogo()` 둘 다 기본 이미지 폴백이 특정 개발자의 로컬 macOS 절대경로(`/Users/mzc01-search5/...`)로 하드코딩돼 있어 어떤 배포 환경에서도 동작하지 않던 실제 결함 확인·수정(`ClassPathResource`로 교체, 전체 재검색으로 이 2곳 외에는 없음을 확인).
- **중대 발견(수정 보류, 사용자 판단 필요)**: `PullRequestViewController.closePattern`(PR/커밋 메시지 "fixes #123"으로 이슈 자동 닫기)이 legacy-yona에 전혀 없는 yona 독자 구현이었음을 확인 — 정규식 버그(fix/fixes/fixed 매치 안 됨)도 함께 발견했으나 "독자구현 금지" 원칙 위배 사안이라 임의로 고치지 않고 사용자에게 보고
