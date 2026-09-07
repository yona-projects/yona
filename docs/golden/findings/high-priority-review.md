---
id: high-priority-review
type: finding
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

## HIGH 우선순위(playRepository/validation/errors/service) 사람 검토 결과 (233건 중 23개 클래스)

**결론: 진짜 조치 검토 가치가 있는 건 2~3건뿐, 나머지는 이미 다른 구조로 이식됐거나 레거시 자체의 죽은 코드.**

| Legacy | yona 대응 | 판정 | 비고 |
|---|---|---|---|
| `ExConstraints.java` | 없음 | **진짜 공백** | 프로젝트명에 `.`/`..`/`.git` 등 예약 패턴을 막는 검증이 yona에 전혀 없음(현재는 중복명 체크만 존재) — 파일시스템 경로 문제 소지 있어 실사용 영향 가능 |
| `PullRequestCheck.java` | `GitPushHooks.kt`(삭제)+`PullRequestMergeEventListener`(재병합) | **부분 공백** | 브랜치 삭제 시 PR 정리는 이식됐으나, "브랜치 갱신→관련 PR 재검사" 트리거(`RelatedPullRequestMergeEvent` 발행)가 `src/main` 전체에 0건 — 핸들러는 있지만 실제 git push 경로에서 절대 호출 안 됨(테스트만 직접 호출하는 죽은 트리거) |
| `BareRepository.java` | 없음(우회 구현만) | 공백(낮은 심각도) | README 탐색 전용 메서드들은 없지만 `getRawFile`로 기능적 우회 구현 존재 |
| `GitBranch.java`(pullRequest 필드) | `GitBranch.kt`에 pullRequest 연결 없음 | 확인 필요 | 다른 항목보다 신뢰도 낮음(추가 검증 권장) |
| `GitRepository.java`(35개 메서드) | **2026-08-26 사용자 요청으로 35개 전부 완전 재검증 완료** | **진짜 공백 0건** | 최초 판정("35개 미대응")과 그 후 첫 재검증("2개만 확인하고 전체 폐기")이 둘 다 부실했음을 인정 — 이번엔 35개 전부를 `search_for_pattern`(yona 전체 코드베이스)+`find_referencing_symbols`(legacy 자체)로 재조사. 분류: **(1) false positive 5개**(`deleteFromBranch`→`PullRequestServiceImpl.kt`, `cloneRepository`→`GitServiceImpl.kt`, `cloneHardLinkedRepository`→`ProjectServiceImpl.kt`, `canRestoreBranch`→인라인 로직, `setTheLatestPullRequest`→`PullRequestRepository`로 위임, 주석 명시), **(2) 머지 클러스터(`buildMergingRepository`/`buildGitRepository`/`getDirectoryForMerging` 등) — 아키텍처 대체**(`PullRequestServiceImpl.kt`가 물리적 디렉터리 클론 대신 임시 ref+fetch+`MergeStrategy.RECURSIVE`로 완전히 다른 방식 사용, "legacy fetchSourceTemporarilly() 대응" 주석 명시로 확인), **(3) legacy 자체에서도 죽은 코드 12개**(`getDiffEntries`/`getAuthorsFromDiffEntry`/`getAuthorsFromBlameResult`/`getAuthorFromFirstCommit`/`findAuthorByPersonIdent`/`diffRevCommits`/`wrapInGitCommits`/`cloneLocalRepository`/`canDeleteFromBranch`/`checkout`/`asResource`/**`getRelatedAuthors`** — `find_referencing_symbols`로 legacy 내 참조 0건 직접 확인, GitRef/VCSRef와 동일 패턴), **(4) legacy 내부 전용 헬퍼 5개**(`getDirectoryForMerging`/`getGitDirectory`/`getRootDirectory`/`extendPath`/`isWellKnownRef` — legacy의 `move()`/`getRefNames()` 등을 내부적으로만 지원, yona가 다른 방식으로 처리해 헬퍼 자체가 불필요해진 것으로 추정). **결론: "제품 기능인데 통째로 빠진 것"은 없음** — 가장 기능처럼 보였던 `getRelatedAuthors`(diff 저자 추적)조차 legacy 자체에서 미사용 확인 |
| `GitRef.java`/`VCSRef.java` | 없음 | 무시 가능 | legacy 자체에서도 정의 외 참조 0건(레거시 죽은 코드) — 이식 누락의 실무 영향 없음. **2026-08-26 사용자 요청으로 `find_referencing_symbols`+전체 텍스트 검색 2가지 방법으로 재검증**: `GitRef`는 자기 파일 외 참조 0건, `VCSRef`는 유일한 외부 참조가 `GitRef.java:23`의 상속 선언뿐(그 GitRef 자체가 미사용이므로 죽은 상속 체인) — 원 판정 확인됨. **사용자 결정: 티켓 미등록 유지** |
| `SVNRepository.java`,`RepositoryService.java`(분산: `GitService.kt`+`GitServletConfig.kt`),`PlayRepository.java`,`FileDiff.java`,`GitCommit.java`,`SvnCommit.java`,`Hunk.java`,`DiffLine.java`,`DiffLineType.java`,`IssueReferredFromCommitEvent.java`,`NotifyPushedCommits.java`,`ReceiveCommandUtil.java`,`RejectPushToReservedRefs.java`,`UpdateLastPushedDate.java`,`YonaUserServicePlugin.java`,`PullRequestException.java` | 각각 확인됨 | 완전/거의완전 대응 | 개별 상세 비고는 세션 기록 참고. `PullRequestException`은 전용 예외 타입 대신 `IOException`을 재사용(기능은 동일, 타입 구분만 약함 — 경미) |

**미검토(다음 배치 대상)**: 나머지 1934건(HIGH 외 영역 — models/controllers/utils/data/mailbox/actions 등), 아직 자동 2차 필터의 "파일 자체 미인용" 단계까지만 거쳤고 사람 검토 전.
