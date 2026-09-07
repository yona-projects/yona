---
id: bucket-c-summary
type: finding
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

## 버킷 C 최종 요약 (2026-08-26, HIGH+NORMAL 사람 검토 완료)

원본 3625개 → 자동 필터로 429개 파일/2204개 심볼로 압축 → HIGH(233건)+NORMAL(1744건, 템플릿 제외) 총 1977개 심볼(201+23=224개 파일)에 대해 사람이 직접 심볼 대조 검증 완료. **최종적으로 조치를 검토할 가치가 있는 항목은 6~7건**:

1. `ExConstraints.java` — 프로젝트명 예약패턴(`.`/`..`/`.git`) 검증 전혀 없음 (HIGH영역) → **P1-145로 승격**
2. `PullRequestCheck.java` — 브랜치 갱신→PR 재검사 이벤트가 프로덕션 경로에서 미배선 (HIGH영역) → **P1-146으로 승격**
3. `ResourcePersistAdapter.java` — 리소스 삭제 시 Watch/Unwatch 고아 row 가능성 (NORMAL K6) → **P1-147로 승격**
4. `AccessLogger.java` — HTTP 접근 로깅 부재 (NORMAL K6, 낮은 우선순위) → **P2-48로 승격**
5. `AttachmentCache.java` — 캐싱 누락(성능만) (NORMAL K6, 낮은 우선순위) → **P2-49로 승격**
6. `BareRepository.java` — README 탐색 전용 대응 없음(우회 구현 존재, 낮은 심각도) (HIGH영역) → **P2-47로 승격**
7. (신뢰도 낮음, 필요시 재검증) `IssueFilterType`/`Matching`/`Resource.java` 구조 변경 여부 불확실 (NORMAL K5) → **P2-52로 승격**

**경미 사항도 사용자 지시("경미한 것도 전체 넣어줘")로 함께 승격**:
- `PullRequestException.java`(전용 예외 타입 대신 `IOException` 재사용) → **P2-50**
- `mailbox/exceptions` 5종 통합(`IncomingMailOutcome.Rejected`) → **P2-51**
- `GitBranch.pullRequest` 필드 연결 확인 필요 → **P2-53**

**2026-08-26 전체 10건 `docs/PARITY_BACKLOG.md`에 등록 완료(P1-145~147, P2-47~53, 전부 `[ ]` 대기 상태)**. 나머지(위 10건 외의 경미 사항 포함)는 전부 다른 구조로 이식됐거나 프레임워크 마이그레이션(Ebean→JPA, Play→Spring Boot)으로 구조적으로 설명되어 승격하지 않음.
