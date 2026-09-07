---
id: batch-03
type: batch
status: done
created: 2026-08-24
updated: 2026-08-24
relates_to: []
source: docs/COVERAGE_BACKLOG.md:49-57 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-24 01:20, 3차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **260개**(-5)
- 라인: 83.6%, 분기: 67.7%, 메서드: 77.5%, 클래스: 92.7%
- 추가 완료([x]): `AccessControl`(BRANCH 95.3% — 이 저장소 최대 미커버 클래스 완주, 4개 파일 431 tests), `TemplateHelper`(96.2%)
- 구조적 한계로 최대치 도달([i]): `GitRepository`(BRANCH 88.3%, 실제 JGit 저장소 기반 95 tests)
- 진행 중([~], 다음 배치 계속): `ProjectViewController`(78.5%), `UserViewController`(87.9%, 근소 미달)
- 누적 실버그 발견(전부 미수정, 별도 검토 필요): FileDiff.updateRange 중복추가, MigrationService 3건, diff_match_patch 2건(vendored, 도달불가), TemplateHelper.getVotersForName 클램프 오류(미트리거), GitRepository.getParentCommitOf NPE 위험(미트리거), ProjectViewController.projectLogo 하드코딩 개발자 로컬경로(배포결함 추정), IssueShareServiceImpl.findSharableUsers의 type 파라미터 미사용, PullRequest.contributor/title 관련 죽은 코드 2건
