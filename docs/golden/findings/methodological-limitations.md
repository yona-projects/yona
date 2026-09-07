---
id: methodological-limitations
type: finding
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

## 알려진 방법론적 한계 (2026-08-26 Sanity Check 중 발견)

2단계 정규식(`yona X.java:NNN ... 대응`)은 **줄번호가 명시된 인용만** 매치한다. 그런데 yona 주석 중 상당수는
`// yona playRepository/hooks/UpdateRecentlyPushedBranch.java 대응 (P1-24)`처럼 **파일명만 인용하고 줄번호가 없다** —
이런 경우 버킷 A/B의 정방향 매칭에서 전부 누락된다(예: `P1-24`가 대표 사례로, PARITY_BACKLOG.md의 "yona 근거" 컬럼 자체도
줄번호 없이 파일명만 기재돼 있었다 — 이는 계획 문서가 이미 예견한 "38개 무참조 티켓"과는 다른, 파일명은 있으나 라인이 없는
별도 실패 유형이다).

파일명만 인용된 케이스를 추가로 수동 표본 조사한 결과 78건 중 69건이 실제 legacy 파일(GL 인덱스에 존재)을 정확히 가리켰다
(9건은 조사 과정에서 텍스트를 축약 인용해 파일명 추출에 실패한 것으로, 방법론 결함이 아니라 표본 조사 자체의 한계) — 즉
**버킷 A(304건)는 실제 확인 가능한 매치 수를 과소집계한 하한선**이다. 정밀한 재측정을 원하면 2단계 정규식에서 `:\d+` 요구조건을
없앤 버전으로 재실행하고, 대신 3단계 매칭 우선순위를 "라인범위 overlap"에서 "같은 파일 내 아무 위치"로 완화해야 한다(현재는
미실행 — 버킷 A/C 재계산 비용이 크고, 라인 단위 정밀도를 잃는 트레이드오프가 있어 사용자 확인 후 진행 권장).
상세 데이터: `docs/golden/file_only_matches.csv`.
