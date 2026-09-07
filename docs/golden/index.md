# Golden Parity Ledger

정적 분석 기반 산출물 — 자동 분류 결과이며, 사람의 최종 검토 없이는 어떤 항목도 완료/공백으로 확정하지 않는다. 버킷 C 중 진짜 공백으로 확정된 것만 [[../parity/index|docs/parity/]]에 신규 티켓으로 승격한다. 방법론 전체는 [[methodology]] 참고.

## 문서 규칙 (2026-09-07 위키 이관)

`docs/yona-wiki/` 관례를 따라 이관했다. 이 문서는 원래 GOLDEN_PARITY_CHECK_PLAN.md(방법론) + GOLDEN_PARITY_LEDGER.md(결과, 버킷 C만 3,600줄) 두 개 파일이었다.

- **`methodology.md`**: 검증 방법론 전체(원 `GOLDEN_PARITY_CHECK_PLAN.md`).
- **`findings/*.md`**: 사람이 검토해 도출한 서술형 발견/결론(HIGH·NORMAL 우선순위 검토, K6 발견사항, 버킷 C 최종요약과 승격 목록, 방법론적 한계, Sanity Check 결과, 템플릿 상태).
- **`evidence/*.md`**: 버킷 A(확인됨, 표본)/B(티켓 불일치)/D(의도적 제외)/trivial 부록 — 기계적 매칭 원시 데이터.
- **`gl-symbol-map/*.md`**: 버킷 C(공백 후보, 3,625개 심볼) 전체를 GL-ID 접두어 기준 영역별로 분할한 원시 매핑 표. 서술형 발견이 아니라 기계적 데이터라 findings와 분리했다.
- CSV 산출물(`*.csv`)은 기존 위치 그대로 유지한다(기계 처리용, 이번 이관 대상 아님).

## 요약

- 레거시 GL 심볼 총계: 5202개 (백엔드 315파일+템플릿 242파일)
- yona 역참조 매치(정방향): 373건
- 버킷 A (CONFIRMED): 304건
- 버킷 B (TICKET_MISMATCH/고아티켓): 17건
- 버킷 C (GAP_CANDIDATE, trivial 제외): 3625개 심볼 (trivial 1414개는 부록에서 별도 집계만)
- 버킷 D (INTENTIONAL_EXCLUDED): 4건
- **버킷 A-보조(파일 단위, 라인범위 없음): 78건 중 69건 legacy 파일 존재 확인** — 아래 "알려진 방법론적 한계" 참고
- **5단계 완료(2026-08-26)**: 버킷 A 중 GL-ID가 매치된 183건(86개 파일) 전부에 `[GL-NNNNN]` 병기 완료 — 182건 실제 삽입 + 1건 동일 위치 중복행이라 스킵. 컴파일(`compileKotlin`+`compileTestKotlin`) 확인 통과.
- **버킷 B 사람 검토 완료(2026-08-26)**: 17건 전부 가짜 경보 확인 — 원인은 매칭 스크립트가 `PARITY_BACKLOG.md`의 `P0-P2` 티켓만 조회하고 `TEMPLATE_BACKLOG.md`의 `TASK-NNNN`/`그룹N #NNN` 형식 티켓은 조회하지 않았기 때문(TASK-0244/TASK-0263/그룹7 #119,125,127/그룹2 #39/그룹11 #168 전부 TEMPLATE_BACKLOG.md에서 `[x]` 완료 확인), 나머지 `P2-12`는 이후 `P1-86`으로 재분류된 옛 번호(완료). **실제 문제 0건.**
- **버킷 C 자동 2차 필터링 + HIGH 영역 사람 검토 완료(2026-08-26)**: 1차로 "파일 자체가 yona 어디에도 전혀 인용되지 않은" 429개 파일/2204개 심볼로 압축(부분 인용 파일의 나머지 1421개는 이미 그 클래스가 참조되고 있어 대부분 이식됐을 가능성이 높다고 보고 후순위로 미룸). 그중 계획 문서가 지정한 HIGH 우선순위(playRepository/validation/errors/service, 23개 파일 233개 심볼)를 3개 에이전트로 병렬 심볼 대조 검증. 결과는 아래 "HIGH 우선순위 검토 결과" 참고. 나머지 1934개(HIGH 외 영역)는 미검토.

## 방법론

- [[methodology]] — golden marker 삽입부터 5단계 대조 로직, Sanity Check 계획까지 전체 절차.

## 발견 (findings)

- [[findings/high-priority-review]]
- [[findings/normal-priority-review]]
- [[findings/k6-notable-findings]]
- [[findings/bucket-c-summary]]
- [[findings/methodological-limitations]]
- [[findings/sanity-check-results]]
- [[findings/template-status]]

## 근거 데이터 (evidence)

- [[evidence/bucket-a-sample]]
- [[evidence/bucket-b-ticket-mismatches]]
- [[evidence/bucket-d-exclusions]]
- [[evidence/trivial-appendix]]

## 버킷 C 심볼 맵 (영역별)

| 영역 | 심볼 수 | 링크 |
|---|---|---|
| models | 1479 | [[gl-symbol-map/models]] |
| controllers | 544 | [[gl-symbol-map/controllers]] |
| utils | 512 | [[gl-symbol-map/utils]] |
| data | 436 | [[gl-symbol-map/data]] |
| view | 242 | [[gl-symbol-map/view]] |
| playRepository | 241 | [[gl-symbol-map/playrepository]] |
| mailbox | 74 | [[gl-symbol-map/mailbox]] |
| actions | 24 | [[gl-symbol-map/actions]] |
| actors | 21 | [[gl-symbol-map/actors]] |
| Global | 20 | [[gl-symbol-map/global]] |
| notification | 19 | [[gl-symbol-map/notification]] |
| service | 8 | [[gl-symbol-map/service]] |
| validation | 3 | [[gl-symbol-map/validation]] |
| errors | 2 | [[gl-symbol-map/errors]] |
