---
title: 마일스톤 진행 현황
type: domain
sources: [app/models/Milestone.java:92-279]
updated: 2026-10-22
---

# 마일스톤 진행 현황 (`MilestoneStatusService`)

| 값 | 규칙 (v1.6) |
|---|---|
| 열린/닫힌 이슈 수 | 마일스톤에 속한 이슈 중 상태가 OPEN/CLOSED인 수 |
| 완료율 | `(int)(닫힌 수 / 전체 이슈 수 * 100)`. **전체 이슈 수는 상태와 무관한 모든 이슈**이다. 0/0은 0 |
| 마감일 문자열 | `yyyy-MM-dd`(서버 시간대). 마감일이 없으면 없음 |
| 남은 기간 | 마감일이 오늘이면 `common.time.today`, 지났으면 `common.time.overday`(지난 날수), 아니면 `common.time.leftday`(남은 날수). 날수는 날짜(시각 제외) 차이 |
| 지연 여부 | 지금이 마감일 시각을 지났는지. 마감일은 보통 0시이므로 **마감일 당일도 지연으로 판정**한다 |
| 열린 마일스톤 조회 | 상태 OPEN, 마감일 오름차순. MariaDB(v1.6 운영 DB)처럼 마감일이 없는 것이 **먼저** 나오도록 `NULLS FIRST`를 명시한다 |

시각 기준은 주입된 `Clock`이다. 테스트 8건(`MilestoneStatusServiceTest`).
