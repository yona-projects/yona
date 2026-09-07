---
id: batch-41
type: batch
status: done
created: 2026-08-25
updated: 2026-08-25
relates_to: []
source: docs/COVERAGE_BACKLOG.md:216-222 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-25, 41차 배치 완료 후)

- 추가 완료([x], 5개): `Property`, `ProjectUser`, `NotificationMail`, `Label`, `IssueComment` — 전부 LINE/BRANCH/METHOD 100% 완전 달성
- 검증 방식: 배치40 전체 스위트 실행 시점에 배치41 신규 스펙 파일들이 이미 디스크에 존재해 같은 실행에 포함되어 검증됨(추가 전체 스위트 불필요)
- 사용자 지시로 42차 배치부터는 배치 크기를 5개→10개로 확대(잔여 항목 대부분이 단순 프로퍼티 접근자 패턴이라 처리 부담이 낮음)
- 이번 배치 신규 실버그/죽은코드 없음
