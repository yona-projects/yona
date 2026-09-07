---
id: batch-06
type: batch
status: done
created: 2026-08-24
updated: 2026-08-24
relates_to: []
source: docs/COVERAGE_BACKLOG.md:80-87 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-24 03:35, 6차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **240개**(-3)
- 라인: 89.5%, 분기: 79.5%, 메서드: 80.3%, 클래스: 94.7%
- 추가 완료([x]): `IndexController`(전부 100%), `ProjectServiceImpl`(BRANCH 96.9%), `MentionController`(96.4%)
- 진행 중([~], 다음 배치 계속): `AttachmentController`(BRANCH 89.7%), `PullRequestServiceImpl`(BRANCH 85.5%, METHOD 75.4% — 둘 다 미달, 우선순위 높음)
- **잠재적 운영 이슈 발견(미수정, 별도 검토 필요)**: `PullRequestServiceImpl.createMergeCommitAndUpdateRef`가 동일 초 내 diff 없이 연속 병합체크 시 `RefUpdate.Result.NO_CHANGE`로 인한 `IOException` 실제 재현됨
