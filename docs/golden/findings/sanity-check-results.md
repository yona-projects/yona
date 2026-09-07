---
id: sanity-check-results
type: finding
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

## Sanity Check 결과

1. **제외 케이스 검증(통과)**: 버킷 D 4건 = 계획 문서가 사전 지정한 4개 파일(UserDetailsServiceImplSpec.kt/UserSpec.kt/IncomingMailProcessingService.kt/DataBackupServiceImpl.kt)과 정확히 일치.
2. **정밀도 검증(예상대로 높은 false-positive)**: 버킷 C의 `GL-playRepository_GitRepository-017`(생성자 `GitRepository(String, String)`)을 `find_referencing_symbols`로 표본 확인한 결과, yona `GitRepository.kt`에 `ownerName`/`projectName` 필드 기반 동등 로직이 실제로 존재함을 확인 — 버킷 C 항목이라고 미이식을 의미하지 않는다(단지 yona 쪽에 줄번호 인용 주석이 없을 뿐). playRepository처럼 "yona 대응" 주석이 드문 영역은 버킷 C가 대량으로 잡히는 게 정상이며, 개별 검토 없이 공백으로 단정하면 안 된다.
3. **커버리지 대조(통과)**: GL 인덱스 5202개 = 백엔드(315개 파일, 4960개 심볼) + 템플릿(242개 파일, 242개) 정확히 일치.
4. **마커 무결성(통과)**: 전역 중복 GL-ID 0건, 마커 삽입으로 인한 신규 구문 오류 0건(1a~1c 단계에서 이미 확인).
5. **정답 케이스 역검증**: P1-24(`PushedBranch.kt`)는 버킷 A가 아니라 "파일 단위 보조 확인"에서 발견됨(위 "알려진 방법론적 한계" 참고) — 계획 문서가 예로 든 케이스가 실제로는 방법론 한계를 드러내는 사례였다는 것 자체가 유의미한 발견.
