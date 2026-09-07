> ⚠️ **이관됨(2026-09-07)**: 이 문서는 `docs/golden/`(index.md/findings/evidence/gl-symbol-map)로 전량 이관되었다. 새 SSOT는 [`docs/golden/index.md`](index.md)다. PARITY/COVERAGE와 함께 이 파일도 곧 삭제된다.

# Golden Parity Ledger (생성일 2026-08-26)

정적 분석 기반 산출물 — 자동 분류 결과이며, 사람의 최종 검토 없이는 어떤 항목도 완료/공백으로 확정하지 않는다. 버킷 C 중 진짜 공백으로 확정된 것만 `docs/PARITY_BACKLOG.md`에 신규 티켓으로 승격한다.

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

## NORMAL 우선순위 검토 결과 (진행 중, K1~K3 완료분)

템플릿 관련 194건은 TEMPLATE_BACKLOG.md가 이미 레거시-yona 줄 단위 대조로 더 엄밀하게 검증해뒀으므로 제외. 백엔드 1744건(201개 파일)을 K1~K7 7개 그룹으로 나눠 검토 중.

| 그룹 | 대상 | 결과 |
|---|---|---|
| K1 | actors/controllers/data 일부 (29개 파일) | **전부 해소(미대응 0건)**. `GlobalApi.hello()`는 Spring Boot Actuator `/actuator/health`로 표준 대체(P3-01). `DataService`/`DefaultExchanger`/`Exchanger`+DataExchanger 4개는 `domain/site/DataBackupServiceImpl.kt`로 통합(아래 참고) |
| K2 | data/exchangers 전체 패턴 검증 (29개 파일) | **전부 해소(미대응 0건)**. legacy는 엔티티별 전용 Exchanger 47개, yona는 `domain/site/DataBackupServiceImpl.kt` 하나가 `DatabaseMetaData.getTables()`로 전체 테이블을 자동 탐지해 export/import — 엔티티별 화이트리스트 없이 스키마의 모든 테이블을 포괄해 legacy보다 일반화된 형태로 이식됨(코드 주석에 "44개 Exchanger 대응" 명시) |
| K3 | data/exchangers+mailbox+models 일부 (29개 파일) | **거의 전부 해소, 경미 사항 1건**. DataExchanger 14개는 K2와 동일 패턴. mailbox 5개는 P0-02로 `domain/mail/*.kt`에 메서드 단위까지 확인. models 5개(AbstractPosting/Assignee/AuthInfo/CandidateUser/CodeComment)도 전부 대응 확인(AuthInfo/CandidateUser는 별도 클래스 없이 Spring Security formLogin/LdapUserProvisioningService에 흡수). **경미**: `mailbox/exceptions`의 커스텀 예외 5종(`IllegalDetailException`/`IssueNotFound`/`MailHandlerException`/`PermissionDenied`/`PostingNotFound`)이 yona에서는 `IncomingMailOutcome.Rejected(reason: String)` 하나로 통합됨 — 기능(거부 사유 판별)은 동등하나 예외 타입이 세분화되지 않아 호출부에서 타입 기반 분기가 불가능(현재 필요하지도 않아 실사용 영향은 낮음으로 판단) |

| K4 | models 소형 클래스 (29개 파일) | **전부 해소(미대응 0건)**. History→HistoryDto+컨트롤러 인라인, LabelOwner(인터페이스)→직접 프로퍼티, NullUser→`User?` nullable, PageParam→Spring `Pageable`, ProjectMenuSetting→Project의 `isXxxEnabled` 필드로 흡수, PostReceiveMessage/PullRequestEventMessage→Spring 이벤트 클래스로 대체. 나머지 22개는 동일/유사명 Kotlin 클래스로 직접 대응 |
| K5 | models 소형+enumeration+resource (29개 파일) | **26개 해소, 3개 불확실(낮은 신뢰도)**. 직접 포팅 19개+아키텍처 대체 6개(Direction→Sort.Direction, SiteAdmin→UserState.SITE_ADMIN, UserCredential→LinkedAccount+OAuth2, RequestState→분리된 EventType들, GlobalResource→AccessControl 메서드들, PullRequestReviewAction→문자열 값 — 전부 yona 코드 주석에 명시적으로 언급됨)+UserAction(레거시 자체 데드코드). **불확실 3건**: `IssueFilterType`(enum 없이 파라미터 기반으로 재구현된 것으로 보이나 확정 못함), `Matching.java`(Ebean 동적쿼리 enum, JPA 전환 후 대체 근거 문서화 안 됨), `Resource.java`(추상 클래스, `asResource()` 패턴이 통합 추상화 없이 각 서비스에 산재) — 구조 변경으로 설명 가능성 높으나 검증 강도가 다른 항목보다 낮음 |
| K6 | models/support+resource+utils 일부 (29개 파일) | **26개 해소, 3개 주목할 만한 발견**. 직접 포팅 20개+ORM/프레임워크 마이그레이션 6개(FinderTemplate/OrderParam(s)/SearchParam(s)/SearchCondition/IssueSearchCondition→JPA Repository/Specification, BasicAuthAction/ChunkedOutputStream/FastHttpDateFormat→Spring Security+JGit GitServlet)+데드코드 3개(Options/ModelLock/IssueLabelAggregate, 1회성 마이그레이션 도구 전용). **주목할 발견 3건**(아래 "K6에서 발견된 주목할 사항" 참고) |
| K7 | utils 나머지 (27개 파일) | **전부 해소(미대응 0건)**. LdapService/PasswordReset/PullRequestCommit/diff_match_patch(원본 그대로 포팅)/RouteUtil/MD5Util+SHA256Util→ChecksumUtils/SimpleDiagnostic/SiteManagerAuthAction→AccessControl 직접 대응. Play 서블릿 어댑터 4종(PlayServletContext/Request/Response/Session)은 가설대로 Spring Boot 표준 `jakarta.servlet` API 직접 사용으로 구조적으로 불필요해짐(SvnController.kt에서 확인). SecurityManager는 레거시 원본이 이미 빈 클래스. 나머지도 Spring 표준 관용구로 대체 확인. **주의(2026-08-26 사용자 지적)**: 담당 에이전트가 근거 중 하나로 `doc2/ULTIMATE_PARITY_SUPER_AUDIT.md`(사용자가 신뢰할 수 없다고 판단해 삭제한 문서)를 언급했음 — 재검토 결과 27개 항목 각각의 최종 결론은 전부 doc2와 별개로 구체적인 Serena 인용(SvnController.kt/NotificationUrlResolver.kt/ChecksumUtils.kt/DiagnosticService.kt/AccessControl.kt/SecurityConfig.kt 등)이 따로 붙어 있어 doc2 단독 의존 결론은 없는 것으로 확인. doc2는 이미 삭제되었고 향후 어떤 근거로도 재사용하지 않는다 |

## K6에서 발견된 주목할 사항 (2026-08-26)

| 항목 | 내용 | 심각도 |
|---|---|---|
| `ResourcePersistAdapter.java` | legacy는 Ebean `postDelete` 훅으로 이슈/게시글 등 리소스 삭제 시 관련 `Watch`/`Unwatch` row를 자동 정리한다. yona의 `IssueServiceImpl.deleteIssueCascade()`/`PostingServiceImpl.deletePostingCascade()`/`ProjectServiceImpl.deleteProject()` 어디에도 `watchRepository`/`unwatchRepository` 삭제 호출이 없음 — **리소스 삭제 후 Watch/Unwatch에 고아 row가 남는 실질적 회귀 가능성** | 중간(데이터 정합성) |
| `AccessLogger.java` | Apache Combined Log Format 방식 HTTP 접근 로그(사용자/referer/UA/응답시간)가 yona 전체에 없음(필터/인터셉터/logback-access 설정 전무) | 낮음(운영/관측성) |
| `AttachmentCache.java` | 첨부파일 목록 인메모리 캐싱이 yona에 없음(`@Cacheable` 등 캐시 계층 부재) | 낮음(성능 최적화만, 기능 문제 없음) |

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

## Sanity Check 결과

1. **제외 케이스 검증(통과)**: 버킷 D 4건 = 계획 문서가 사전 지정한 4개 파일(UserDetailsServiceImplSpec.kt/UserSpec.kt/IncomingMailProcessingService.kt/DataBackupServiceImpl.kt)과 정확히 일치.
2. **정밀도 검증(예상대로 높은 false-positive)**: 버킷 C의 `GL-playRepository_GitRepository-017`(생성자 `GitRepository(String, String)`)을 `find_referencing_symbols`로 표본 확인한 결과, yona `GitRepository.kt`에 `ownerName`/`projectName` 필드 기반 동등 로직이 실제로 존재함을 확인 — 버킷 C 항목이라고 미이식을 의미하지 않는다(단지 yona 쪽에 줄번호 인용 주석이 없을 뿐). playRepository처럼 "yona 대응" 주석이 드문 영역은 버킷 C가 대량으로 잡히는 게 정상이며, 개별 검토 없이 공백으로 단정하면 안 된다.
3. **커버리지 대조(통과)**: GL 인덱스 5202개 = 백엔드(315개 파일, 4960개 심볼) + 템플릿(242개 파일, 242개) 정확히 일치.
4. **마커 무결성(통과)**: 전역 중복 GL-ID 0건, 마커 삽입으로 인한 신규 구문 오류 0건(1a~1c 단계에서 이미 확인).
5. **정답 케이스 역검증**: P1-24(`PushedBranch.kt`)는 버킷 A가 아니라 "파일 단위 보조 확인"에서 발견됨(위 "알려진 방법론적 한계" 참고) — 계획 문서가 예로 든 케이스가 실제로는 방법론 한계를 드러내는 사례였다는 것 자체가 유의미한 발견.

## 버킷 A — 확인됨 (표본)

| yona 파일:라인 | 티켓 | yona 파일:범위 | 매치된 GL-ID |
|---|---|---|---|
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentCleanupScheduler.kt:21` | P2-26 | `Attachment.java:438-477` | GL-models_Attachment-035 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentService.kt:7` | P2-24 | `Attachment.java:537-582` | GL-models_Attachment-039;GL-models_Attachment-040 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentServiceImpl.kt:59` | P2-24 | `Attachment.java:537-582` | GL-models_Attachment-039;GL-models_Attachment-040 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentRepository.kt:20` | P2-26 | `Attachment.java:456-458` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/attachment/AttachmentRepository.kt:25` | P2-24 | `Attachment.java:75-85` | GL-models_Attachment-013;GL-models_Attachment-014;GL-models_Attachment-015 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/board/PostingServiceImpl.kt:71` | P1-127 | `NotificationEvent.java:1380-1385` | GL-models_NotificationEvent-096 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/comment/CommentServiceImpl.kt:94` |  | `utils/JodaDateUtil.java:127-142` | GL-utils_JodaDateUtil-019;GL-utils_JodaDateUtil-020 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/comment/CommentServiceImpl.kt:239` | P1-126 | `NotificationEvent.java:1517-1528` | GL-models_NotificationEvent-107;GL-models_NotificationEvent-108 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/event/GitPostReceiveEventListener.kt:132` | P1-46 | `NotificationEvent.java:604-680` | GL-models_NotificationEvent-038;GL-models_NotificationEvent-039;GL-models_NotificationEvent-040;GL-models_NotificationEvent-041;GL-models_NotificationEvent-042;GL-models_NotificationEvent-043;GL-models_NotificationEvent-044;GL-models_NotificationEvent-045;GL-models_NotificationEvent-046 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueService.kt:50` | P1-101 | `IssueApi.java:1176-1210` | GL-controllers_api_IssueApi-064;GL-controllers_api_IssueApi-065 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueServiceImpl.kt:151` | P1-127 | `NotificationEvent.java:1380-1385` | GL-models_NotificationEvent-096 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueServiceImpl.kt:599` | P1-101 | `IssueApi.java:1176-1210` | GL-controllers_api_IssueApi-064;GL-controllers_api_IssueApi-065 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueRepository.kt:35` | P2-38 | `Issue.java:524-529` | GL-models_Issue-061 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/ImapMailboxPoller.kt:100` | P1-137 | `MailboxService.java:177-188` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/IncomingMailProcessingService.kt:384` | P2-34 | `IssueApp.java:1004-1011` | GL-controllers_IssueApp-049 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionService.kt:6` |  | `Mention.java:33-49` | GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionRepository.kt:6` |  | `Mention.java:33-49` | GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mention/MentionServiceImpl.kt:14` |  | `Mention.java:33-49` | GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/milestone/MilestoneService.kt:6` |  | `MilestoneApp.java:52-53` | GL-controllers_MilestoneApp-002 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/milestone/MilestoneServiceImpl.kt:25` |  | `Milestone.java:188-230` | GL-models_Milestone-027;GL-models_Milestone-028;GL-models_Milestone-029 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt:25` | P2-19 | `FavoriteOrganization.java:38-46` | GL-models_FavoriteOrganization-007 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt:45` | P1-108 | `models/Organization.java:42` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt:97` | P2-19 | `FavoriteOrganization.java:38-46` | GL-models_FavoriteOrganization-007 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationServiceImpl.kt:259` | P1-123 | `EnrollOrganizationApp.java:101-104` | GL-controllers_EnrollOrganizationApp-005 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectServiceImpl.kt:59` | P2-27 | `FavoriteProject.java:41-50` | GL-models_FavoriteProject-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectServiceImpl.kt:366` | P2-27 | `FavoriteProject.java:41-50` | GL-models_FavoriteProject-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/Project.kt:27` |  | `Project.java:131-133` | GL-models_Project-026 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectUserServiceImpl.kt:74` | P1-142 | `EnrollProjectApp.java:61-63` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectUserServiceImpl.kt:74` | P1-123 | `EnrollProjectApp.java:61-63` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/ProjectUserServiceImpl.kt:237` | P2-20 | `NotificationEvent.java:1468-1477` | GL-models_NotificationEvent-100 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CodeReviewServiceImpl.kt:212` | P1-116 | `AccessControl.java:205-301` | GL-utils_AccessControl-009 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CodeReviewServiceImpl.kt:292` | P1-116 | `AccessControl.java:205-301` | GL-utils_AccessControl-009 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/CodeReviewServiceImpl.kt:319` | P1-79 | `CommentThreadApp.java:66-70` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestServiceImpl.kt:669` | P1-127 | `NotificationEvent.java:1425-1428` | GL-models_NotificationEvent-098 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestRepository.kt:44` | P2-38 | `PullRequest.java:219-225` | GL-models_PullRequest-036 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/SearchServiceImpl.kt:30` | P0-23 | `controllers/Application.java:35` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownService.kt:15` | P1-139 | `Markdown.java:346-356` | GL-utils_Markdown-017;GL-utils_Markdown-018 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownService.kt:21` | P2-02 | `Markdown.java:215-217` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/AutoLinkRenderer.kt:213` | P2-35 | `utils/AutoLinkRenderer.java:275` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/AutoLinkRenderer.kt:257` | P1-140 | `AutoLinkRenderer.java:322-327` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/DiagnosticService.kt:16` | P1-137 | `Config.java:26-39` | GL-utils_Config-004;GL-utils_Config-005 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/DiagnosticService.kt:72` | P1-137 | `MailboxService.java:176-188` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/DiagnosticService.kt:75` | P1-137 | `Config.java:26-39` | GL-utils_Config-004;GL-utils_Config-005 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/ChecksumUtils.kt:4` | P1-102 | `IssueApi.java:538-548` | GL-controllers_api_IssueApi-029 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:34` | P2-33 | `utils/Markdown.java:132-211` | GL-utils_Markdown-010;GL-utils_Markdown-011 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:41` | P2-32 | `utils/Markdown.java:104` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:79` | P1-139 | `Markdown.java:363` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:108` | P2-43 | `utils/Markdown.java:218-270` | GL-utils_Markdown-012;GL-utils_Markdown-013 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:146` | P2-32 | `utils/Markdown.java:103-130` | GL-utils_Markdown-009 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:169` | P2-33 | `utils/Markdown.java:132-159` | GL-utils_Markdown-010 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:199` | P2-33 | `utils/Markdown.java:161-211` | GL-utils_Markdown-011 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:282` |  | `Markdown.java:358-365` | GL-utils_Markdown-019 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:289` |  | `Markdown.java:367-377` | GL-utils_Markdown-021 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownServiceImpl.kt:301` | P2-02 | `Markdown.java:215-217` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/MarkdownRenderCache.kt:5` | P2-43 | `utils/CacheStore.java:15-26` | GL-utils_CacheStore-002;GL-utils_CacheStore-003;GL-utils_CacheStore-004;GL-utils_CacheStore-005 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/YonaUpdateService.kt:30` |  | `YobiUpdate.java:40-41` | GL-models_YobiUpdate-002 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/support/FileUtil.kt:40` | P2-25 | `FileUtil.java:113-142` | GL-utils_FileUtil-007;GL-utils_FileUtil-008 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/user/LoginIdFormatValidator.kt:3` | P1-104 | `models/User.java:65-66,80` | (매치없음-라인범위 표기 불일치) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/vcs/BareCommit.kt:83` |  | `BareCommit.java:249-286` | GL-playRepository_BareCommit-024;GL-playRepository_BareCommit-025;GL-playRepository_BareCommit-026;GL-playRepository_BareCommit-027 |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/webhook/WebhookServiceImpl.kt:34` | P2-08 | `Webhook.java:178` | (매치없음-라인범위 표기 불일치) |

... 외 244건 (전체는 반정형 데이터로 별도 CSV 참고 필요)

## 버킷 B — 티켓 상태 불일치 / 고아 티켓

| yona 파일:라인 | 티켓 | 티켓 상태 | yona 파일:범위 |
|---|---|---|---|
| `src/main/kotlin/com/github/yonaprojects/yona/domain/board/PostingRepository.kt:21` | TASK-0244 | ORPHAN | `organization/group_board_list.scala.html:65-71` |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueRepository.kt:27` | TASK-0244 | ORPHAN | `organization/group_issue_search_partial.scala.html:72` |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationService.kt:47` | TASK-0244 | ORPHAN | `OrganizationApp.java:287-311` |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/Project.kt:100` | 그룹11 #168 | ORPHAN | `Project.java:850` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/BoardViewController.kt:246` | TASK-0263 | ORPHAN | `board/create.scala.html:100-106` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/BoardViewController.kt:294` | TASK-0263 | ORPHAN | `board/edit.scala.html:59` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/CodeViewController.kt:492` | 그룹2 #39 | ORPHAN | `code/svnDiff.scala.html:37-50` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/IssueController.kt:72` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/IssueViewController.kt:223` | 그룹7 #119 | ORPHAN | `partial_list_wrap.scala.html:84-86` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/IssueViewController.kt:333` | 그룹7 #127 | ORPHAN | `issue/view.scala.html:329-381` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/IssueViewController.kt:523` | 그룹7 #125 | ORPHAN | `partial_select_subtask.scala.html:10` |
| `src/main/kotlin/com/github/yonaprojects/yona/config/TemplateHelper.kt:501` | TASK-0244 | ORPHAN | `models/OrganizationUser.java:62-68` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/IssueControllerSpec.kt:572` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/IssueControllerSpec.kt:775` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/IssueControllerSpec.kt:888` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/IssueControllerSpec.kt:937` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/BoardViewControllerSpec.kt:253` | TASK-0263 | ORPHAN | `board/create.scala.html:100-106` |

## 버킷 C — 공백 후보 (trivial 제외, 전체)

**중요**: 이 목록은 자동 분류 결과이며, 실제 공백인지는 사람이 `find_referencing_symbols`/`find_symbol`로 개별 확인해야 한다(자동 승격 금지). yona 주석이 라인범위/basename으로 이 GL 심볼을 정확히 인용하지 않았을 뿐, 다른 방식(변수명 일치, 클래스 단위 포괄 언급 등)으로 이미 이식됐을 가능성이 크다 — 특히 getter/setter를 자동 생성하는 Kotlin data/entity 클래스가 많아 대응 코드 자체가 존재하지 않을 수 있다.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-Global-001 | `app/Global.java:70` | `public class Global extends GlobalSettings {` |
| GL-Global-006 | `app/Global.java:81` | `private MailboxService mailboxService = new MailboxService();` |
| GL-Global-008 | `app/Global.java:86` | `private ConfigFile configFile = new ConfigFile("config", "application.conf");` |
| GL-Global-009 | `app/Global.java:88` | `private ConfigFile loggerConfigFile = new ConfigFile("logger", "application-logger.xml");` |
| GL-Global-010 | `app/Global.java:90` | `private ConfigFile oAuthProviderConfFile = new ConfigFile("conf", "social-login.conf");` |
| GL-Global-011 | `app/Global.java:93` | `@Override` |
| GL-Global-012 | `app/Global.java:101` | `/**` |
| GL-Global-013 | `app/Global.java:133` | `/**` |
| GL-Global-014 | `app/Global.java:153` | `/**` |
| GL-Global-015 | `app/Global.java:171` | `@Override` |
| GL-Global-016 | `app/Global.java:254` | `private boolean equalsDefaultSecret() {` |
| GL-Global-017 | `app/Global.java:259` | `private static void insertInitialData() {` |
| GL-Global-018 | `app/Global.java:266` | `@Override` |
| GL-Global-019 | `app/Global.java:280` | `@SuppressWarnings("rawtypes")` |
| GL-Global-022 | `app/Global.java:391` | `@Override` |
| GL-Global-023 | `app/Global.java:403` | `public void onStop(Application app) {` |
| GL-Global-024 | `app/Global.java:408` | `@Override` |
| GL-Global-025 | `app/Global.java:415` | `@Override` |
| GL-Global-026 | `app/Global.java:433` | `@Override` |
| GL-Global-027 | `app/Global.java:440` | `private static class ConfigFile {` |
| GL-controllers_IssueApp-001 | `app/controllers/IssueApp.java:45` | `@AnonymousCheck` |
| GL-controllers_IssueApp-004 | `app/controllers/IssueApp.java:53` | `@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-005 | `app/controllers/IssueApp.java:73` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-006 | `app/controllers/IssueApp.java:80` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-007 | `app/controllers/IssueApp.java:120` | `@Transactional` |
| GL-controllers_IssueApp-008 | `app/controllers/IssueApp.java:127` | `@IsAllowed(Operation.READ)` |
| GL-controllers_IssueApp-009 | `app/controllers/IssueApp.java:139` | `@Transactional` |
| GL-controllers_IssueApp-011 | `app/controllers/IssueApp.java:191` | `private static Result issuesAsHTML(Project project, Page<Issue> issues, models.support.SearchConditi` |
| GL-controllers_IssueApp-012 | `app/controllers/IssueApp.java:202` | `private static Result issuesAsExcel(Project project, ExpressionList<Issue> el) throws WriteException` |
| GL-controllers_IssueApp-013 | `app/controllers/IssueApp.java:214` | `private static Result issuesAsPjax(Project project, Page<Issue> issues, models.support.SearchConditi` |
| GL-controllers_IssueApp-014 | `app/controllers/IssueApp.java:225` | `private static Result issuesAsJson(Project project, Page<Issue> issues) {` |
| GL-controllers_IssueApp-015 | `app/controllers/IssueApp.java:263` | `@Transactional` |
| GL-controllers_IssueApp-016 | `app/controllers/IssueApp.java:315` | `@IsAllowed(resourceType = ResourceType.ISSUE_POST, value = Operation.READ)` |
| GL-controllers_IssueApp-017 | `app/controllers/IssueApp.java:328` | `public static Result newDirectIssueForm(Long commentId) {` |
| GL-controllers_IssueApp-018 | `app/controllers/IssueApp.java:351` | `public static Result newDirectMyIssueForm() {` |
| GL-controllers_IssueApp-019 | `app/controllers/IssueApp.java:385` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-020 | `app/controllers/IssueApp.java:394` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-021 | `app/controllers/IssueApp.java:410` | `@Transactional` |
| GL-controllers_IssueApp-022 | `app/controllers/IssueApp.java:484` | `private static void updateLabelIfChanged(List<Long> attachingLabelIds, List<Long> detachingLabelIds,` |
| GL-controllers_IssueApp-023 | `app/controllers/IssueApp.java:518` | `private static void updateMilestoneIfChanged(Milestone newMilestone, Issue issue) {` |
| GL-controllers_IssueApp-025 | `app/controllers/IssueApp.java:549` | `private static void updateStateIfChanged(State newState, Issue issue) {` |
| GL-controllers_IssueApp-026 | `app/controllers/IssueApp.java:564` | `private static void updateAssigneeIfChanged(User assignee, Project project, Issue issue) {` |
| GL-controllers_IssueApp-027 | `app/controllers/IssueApp.java:589` | `@Transactional` |
| GL-controllers_IssueApp-028 | `app/controllers/IssueApp.java:676` | `private static void removeAnonymousAssignee(Issue issue) {` |
| GL-controllers_IssueApp-030 | `app/controllers/IssueApp.java:688` | `private static boolean hasAssignee(Issue issue) {` |
| GL-controllers_IssueApp-031 | `app/controllers/IssueApp.java:693` | `@With(NullProjectCheckAction.class)` |
| GL-controllers_IssueApp-032 | `app/controllers/IssueApp.java:712` | `@Transactional` |
| GL-controllers_IssueApp-033 | `app/controllers/IssueApp.java:731` | `private static void addAssigneeChangedNotification(Issue modifiedIssue, Issue originalIssue) {` |
| GL-controllers_IssueApp-034 | `app/controllers/IssueApp.java:743` | `private static void addStateChangedNotification(Issue modifiedIssue, Issue originalIssue) {` |
| GL-controllers_IssueApp-035 | `app/controllers/IssueApp.java:751` | `private static void addBodyChangedNotification(Issue modifiedIssue, Issue originalIssue) {` |
| GL-controllers_IssueApp-036 | `app/controllers/IssueApp.java:759` | `private static void addIssueMovedNotification(Project previous, Issue originalIssue, Issue issue, Se` |
| GL-controllers_IssueApp-037 | `app/controllers/IssueApp.java:772` | `@With(NullProjectCheckAction.class)` |
| GL-controllers_IssueApp-038 | `app/controllers/IssueApp.java:879` | `private static boolean hasTargetProject(Issue issue) {` |
| GL-controllers_IssueApp-040 | `app/controllers/IssueApp.java:889` | `private static void moveIssueToOtherProject(Issue originalIssue, Project toOtherProject) {` |
| GL-controllers_IssueApp-041 | `app/controllers/IssueApp.java:896` | `private static void moveSubtaskToOtherProject(Issue originalIssue, Project toOtherProject) {` |
| GL-controllers_IssueApp-042 | `app/controllers/IssueApp.java:904` | `private static void updateIssueToOtherProject(Issue issue, Project toOtherProject) {` |
| GL-controllers_IssueApp-043 | `app/controllers/IssueApp.java:923` | `private static void transferLabels(Issue originalIssue, Project toProject) {` |
| GL-controllers_IssueApp-045 | `app/controllers/IssueApp.java:951` | `private static void updateSubtaskRelation(Issue issue, Issue originalIssue) {` |
| GL-controllers_IssueApp-048 | `app/controllers/IssueApp.java:983` | `/**` |
| GL-controllers_IssueApp-051 | `app/controllers/IssueApp.java:1072` | `private static void AddPreviousContent(Issue issue, IssueComment comment) {` |
| GL-controllers_IssueApp-053 | `app/controllers/IssueApp.java:1117` | `// Just made for compatibility. No meanings.` |
| GL-controllers_IssueApp-054 | `app/controllers/IssueApp.java:1123` | `private static Comment saveComment(Project project, Issue issue, IssueComment comment) {` |
| GL-controllers_IssueApp-056 | `app/controllers/IssueApp.java:1151` | `private static void toNextState(Long number, Project project) {` |
| GL-controllers_IssueApp-057 | `app/controllers/IssueApp.java:1157` | `private static boolean containsStateTransitionRequest() {` |
| GL-controllers_IssueApp-060 | `app/controllers/IssueApp.java:1177` | `private static Html commentFormValidationResult(Project project, Form<IssueComment> commentForm) {` |
| GL-controllers_IssueApp-061 | `app/controllers/IssueApp.java:1188` | `/**` |
| GL-controllers_IssueApp-062 | `app/controllers/IssueApp.java:1204` | `private static void addLabels(Issue issue, Http.Request request) {` |
| GL-controllers_UserApp-001 | `app/controllers/UserApp.java:58` | `public class UserApp extends Controller {` |
| GL-controllers_UserApp-010 | `app/controllers/UserApp.java:76` | `public static final String DEFAULT_AVATAR_URL` |
| GL-controllers_UserApp-012 | `app/controllers/UserApp.java:81` | `public static final int MAX_FETCH_USERS = 10;  //Match value to Typeahead deafult value at yobi.ui.T` |
| GL-controllers_UserApp-021 | `app/controllers/UserApp.java:100` | `public static final boolean useSocialLoginOnly = play.Configuration.root()` |
| GL-controllers_UserApp-024 | `app/controllers/UserApp.java:107` | `private static boolean usingEmailVerification = play.Configuration.root()` |
| GL-controllers_UserApp-025 | `app/controllers/UserApp.java:111` | `@AnonymousCheck` |
| GL-controllers_UserApp-026 | `app/controllers/UserApp.java:151` | `public static void noCache(final Http.Response response) {` |
| GL-controllers_UserApp-027 | `app/controllers/UserApp.java:159` | `public static Result loginForm() {` |
| GL-controllers_UserApp-028 | `app/controllers/UserApp.java:182` | `public static Result logout() {` |
| GL-controllers_UserApp-029 | `app/controllers/UserApp.java:190` | `public static Result login() {` |
| GL-controllers_UserApp-030 | `app/controllers/UserApp.java:205` | `/**` |
| GL-controllers_UserApp-031 | `app/controllers/UserApp.java:288` | `private static String encodedPath(String path){` |
| GL-controllers_UserApp-032 | `app/controllers/UserApp.java:301` | `/**` |
| GL-controllers_UserApp-033 | `app/controllers/UserApp.java:360` | `/**` |
| GL-controllers_UserApp-034 | `app/controllers/UserApp.java:370` | `/**` |
| GL-controllers_UserApp-035 | `app/controllers/UserApp.java:383` | `/**` |
| GL-controllers_UserApp-036 | `app/controllers/UserApp.java:397` | `public static User authenticateWithHashedPassword(String loginId, String password) {` |
| GL-controllers_UserApp-037 | `app/controllers/UserApp.java:402` | `public static User authenticateWithPlainPassword(String loginId, String password) {` |
| GL-controllers_UserApp-038 | `app/controllers/UserApp.java:407` | `public static Result signupForm() {` |
| GL-controllers_UserApp-039 | `app/controllers/UserApp.java:416` | `@Transactional` |
| GL-controllers_UserApp-040 | `app/controllers/UserApp.java:447` | `private static String newLoginIdWithoutDup(final String candidate, int num) {` |
| GL-controllers_UserApp-041 | `app/controllers/UserApp.java:458` | `public static User createLocalUserWithOAuth(UserCredential userCredential){` |
| GL-controllers_UserApp-042 | `app/controllers/UserApp.java:492` | `private static void forceOAuthLogout() {` |
| GL-controllers_UserApp-043 | `app/controllers/UserApp.java:497` | `private static User createUserDelegate(CandidateUser candidateUser) {` |
| GL-controllers_UserApp-044 | `app/controllers/UserApp.java:522` | `public static Result verifyUser(String loginId, String verificationCode){` |
| GL-controllers_UserApp-045 | `app/controllers/UserApp.java:541` | `private static void sendMailAfterUserCreation(User created) {` |
| GL-controllers_UserApp-050 | `app/controllers/UserApp.java:617` | `private static String generateLoginId(User user, String loginIdCandidate) {` |
| GL-controllers_UserApp-051 | `app/controllers/UserApp.java:632` | `@Transactional` |
| GL-controllers_UserApp-052 | `app/controllers/UserApp.java:661` | `public static Result resetUserVisitedList() {` |
| GL-controllers_UserApp-054 | `app/controllers/UserApp.java:674` | `@Transactional` |
| GL-controllers_UserApp-055 | `app/controllers/UserApp.java:681` | `@Transactional` |
| GL-controllers_UserApp-058 | `app/controllers/UserApp.java:733` | `public static void initTokenUser() {` |
| GL-controllers_UserApp-061 | `app/controllers/UserApp.java:765` | `private static User invalidToken() {` |
| GL-controllers_UserApp-062 | `app/controllers/UserApp.java:771` | `@AnonymousCheck` |
| GL-controllers_UserApp-063 | `app/controllers/UserApp.java:787` | `@AnonymousCheck` |
| GL-controllers_UserApp-067 | `app/controllers/UserApp.java:897` | `private static void sortIssues(List<Issue> issues) {` |
| GL-controllers_UserApp-068 | `app/controllers/UserApp.java:907` | `private static void sortPullRequests(List<PullRequest> pullRequests) {` |
| GL-controllers_UserApp-069 | `app/controllers/UserApp.java:917` | `private static List<Project> collectProjects(User user, Map<Long, Boolean> projectAcl) {` |
| GL-controllers_UserApp-070 | `app/controllers/UserApp.java:924` | `private static void addProjectNotDupped(List<Project> target, List<Project> foundProjects,` |
| GL-controllers_UserApp-071 | `app/controllers/UserApp.java:947` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_UserApp-072 | `app/controllers/UserApp.java:956` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_UserApp-074 | `app/controllers/UserApp.java:990` | `private enum UserInfoFormTabType {` |
| GL-controllers_UserApp-076 | `app/controllers/UserApp.java:1068` | `@Transactional` |
| GL-controllers_UserApp-077 | `app/controllers/UserApp.java:1075` | `/**` |
| GL-controllers_UserApp-078 | `app/controllers/UserApp.java:1093` | `@BodyParser.Of(BodyParser.Json.class)` |
| GL-controllers_UserApp-081 | `app/controllers/UserApp.java:1146` | `@Transactional` |
| GL-controllers_UserApp-082 | `app/controllers/UserApp.java:1164` | `@Transactional` |
| GL-controllers_UserApp-083 | `app/controllers/UserApp.java:1184` | `@Transactional` |
| GL-controllers_UserApp-084 | `app/controllers/UserApp.java:1201` | `@Transactional` |
| GL-controllers_UserApp-085 | `app/controllers/UserApp.java:1229` | `private static User authenticate(String loginId, String password, boolean hashed) {` |
| GL-controllers_UserApp-086 | `app/controllers/UserApp.java:1242` | `public static User authenticateWithLdap(String loginIdOrEmail, String password) {` |
| GL-controllers_UserApp-087 | `app/controllers/UserApp.java:1289` | `private static User createNewUser(String password, LdapUser ldapUser) {` |
| GL-controllers_UserApp-089 | `app/controllers/UserApp.java:1316` | `public static void setupRememberMe(User user) {` |
| GL-controllers_UserApp-090 | `app/controllers/UserApp.java:1322` | `private static void processLogout() {` |
| GL-controllers_UserApp-091 | `app/controllers/UserApp.java:1328` | `private static void validate(Form<User> newUserForm) {` |
| GL-controllers_UserApp-092 | `app/controllers/UserApp.java:1352` | `public static User createNewUser(User user) {` |
| GL-controllers_UserApp-093 | `app/controllers/UserApp.java:1371` | `public static void addUserInfoToSession(User user) {` |
| GL-controllers_UserApp-094 | `app/controllers/UserApp.java:1385` | `public static boolean linkWithExistedOrCreateLocalUser() {` |
| GL-controllers_UserApp-095 | `app/controllers/UserApp.java:1409` | `public static void updatePreferredLanguage() {` |
| GL-controllers_UserApp-096 | `app/controllers/UserApp.java:1434` | `public static Result resetUserPasswordBySiteManager(String loginId){` |
| GL-controllers_UserApp-098 | `app/controllers/UserApp.java:1470` | `@AnonymousCheck` |
| GL-controllers_UserApp-099 | `app/controllers/UserApp.java:1482` | `public static Result usermenuTabContentList(){` |
| GL-controllers_VoteApp-001 | `app/controllers/VoteApp.java:39` | `/**` |
| GL-controllers_VoteApp-002 | `app/controllers/VoteApp.java:46` | `/**` |
| GL-controllers_VoteApp-003 | `app/controllers/VoteApp.java:71` | `/**` |
| GL-controllers_VoteApp-004 | `app/controllers/VoteApp.java:95` | `@Transactional` |
| GL-controllers_VoteApp-005 | `app/controllers/VoteApp.java:109` | `@Transactional` |
| GL-controllers_VoteApp-009 | `app/controllers/VoteApp.java:143` | `/**` |
| GL-controllers_IssueLabelApp-001 | `app/controllers/IssueLabelApp.java:52` | `@AnonymousCheck` |
| GL-controllers_IssueLabelApp-002 | `app/controllers/IssueLabelApp.java:55` | `/**` |
| GL-controllers_IssueLabelApp-003 | `app/controllers/IssueLabelApp.java:78` | `/**` |
| GL-controllers_IssueLabelApp-004 | `app/controllers/IssueLabelApp.java:116` | `private static Result labelsAsPjax(String ownerName, String projectName){` |
| GL-controllers_IssueLabelApp-005 | `app/controllers/IssueLabelApp.java:126` | `@IsAllowed(Operation.UPDATE)` |
| GL-controllers_IssueLabelApp-006 | `app/controllers/IssueLabelApp.java:135` | `public static class NewLabel {` |
| GL-controllers_IssueLabelApp-007 | `app/controllers/IssueLabelApp.java:168` | `/**` |
| GL-controllers_IssueLabelApp-008 | `app/controllers/IssueLabelApp.java:238` | `/**` |
| GL-controllers_IssueLabelApp-009 | `app/controllers/IssueLabelApp.java:284` | `@IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.ISSUE_LABEL)` |
| GL-controllers_IssueLabelApp-010 | `app/controllers/IssueLabelApp.java:302` | `/**` |
| GL-controllers_IssueLabelApp-011 | `app/controllers/IssueLabelApp.java:332` | `/**` |
| GL-controllers_IssueLabelApp-012 | `app/controllers/IssueLabelApp.java:372` | `/**` |
| GL-controllers_IssueLabelApp-013 | `app/controllers/IssueLabelApp.java:401` | `@IsAllowed(value = Operation.UPDATE,` |
| GL-controllers_IssueLabelApp-014 | `app/controllers/IssueLabelApp.java:429` | `/**` |
| GL-controllers_IssueLabelApp-015 | `app/controllers/IssueLabelApp.java:484` | `@Transactional` |
| GL-controllers_IssueLabelApp-016 | `app/controllers/IssueLabelApp.java:492` | `private static Map<String, String> toMap(IssueLabelCategory category) {` |
| GL-controllers_IssueLabelApp-017 | `app/controllers/IssueLabelApp.java:501` | `@IsCreatable(ResourceType.ISSUE_LABEL)` |
| GL-controllers_PlayDAVConfig-001 | `app/controllers/PlayDAVConfig.java:28` | `public class PlayDAVConfig extends DAVConfig {` |
| GL-controllers_PlayDAVConfig-002 | `app/controllers/PlayDAVConfig.java:30` | `public PlayDAVConfig() {` |
| GL-controllers_CodeHistoryApp-001 | `app/controllers/CodeHistoryApp.java:61` | `@AnonymousCheck` |
| GL-controllers_CodeHistoryApp-003 | `app/controllers/CodeHistoryApp.java:69` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeHistoryApp-004 | `app/controllers/CodeHistoryApp.java:77` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeHistoryApp-005 | `app/controllers/CodeHistoryApp.java:106` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeHistoryApp-006 | `app/controllers/CodeHistoryApp.java:159` | `@With(NullProjectCheckAction.class)` |
| GL-controllers_CodeHistoryApp-007 | `app/controllers/CodeHistoryApp.java:197` | `@IsCreatable(ResourceType.COMMIT_COMMENT)` |
| GL-controllers_CodeHistoryApp-008 | `app/controllers/CodeHistoryApp.java:258` | `@With(DefaultProjectCheckAction.class)` |
| GL-controllers_NotificationApp-001 | `app/controllers/NotificationApp.java:28` | `@AnonymousCheck` |
| GL-controllers_NotificationApp-002 | `app/controllers/NotificationApp.java:31` | `public static Result notifications(int from, int size) {` |
| GL-controllers_EnrollProjectApp-001 | `app/controllers/EnrollProjectApp.java:36` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_EnrollProjectApp-002 | `app/controllers/EnrollProjectApp.java:40` | `@Transactional` |
| GL-controllers_WatchApp-001 | `app/controllers/WatchApp.java:39` | `public class WatchApp extends Controller {` |
| GL-controllers_WatchApp-002 | `app/controllers/WatchApp.java:41` | `public static Result watch(ResourceParam resourceParam) {` |
| GL-controllers_WatchApp-003 | `app/controllers/WatchApp.java:59` | `@Transactional` |
| GL-controllers_OrganizationApp-001 | `app/controllers/OrganizationApp.java:48` | `/**` |
| GL-controllers_OrganizationApp-002 | `app/controllers/OrganizationApp.java:55` | `@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)` |
| GL-controllers_OrganizationApp-003 | `app/controllers/OrganizationApp.java:75` | `@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)` |
| GL-controllers_OrganizationApp-004 | `app/controllers/OrganizationApp.java:81` | `/**` |
| GL-controllers_OrganizationApp-006 | `app/controllers/OrganizationApp.java:121` | `private static void validate(Form<Organization> newOrgForm) {` |
| GL-controllers_OrganizationApp-007 | `app/controllers/OrganizationApp.java:140` | `/**` |
| GL-controllers_OrganizationApp-008 | `app/controllers/OrganizationApp.java:154` | `@Transactional` |
| GL-controllers_OrganizationApp-009 | `app/controllers/OrganizationApp.java:172` | `private static Result validateForAddMember(Form<User> addMemberForm, String organizationName) {` |
| GL-controllers_OrganizationApp-010 | `app/controllers/OrganizationApp.java:207` | `@Transactional` |
| GL-controllers_OrganizationApp-011 | `app/controllers/OrganizationApp.java:225` | `private static Result validateForDeleteMember(String organizationName, Long userId) {` |
| GL-controllers_OrganizationApp-012 | `app/controllers/OrganizationApp.java:252` | `@Transactional` |
| GL-controllers_OrganizationApp-013 | `app/controllers/OrganizationApp.java:267` | `private static Result validateForEditMember(Form<Role> roleForm, String organizationName, Long userI` |
| GL-controllers_OrganizationApp-015 | `app/controllers/OrganizationApp.java:312` | `public static ValidationResult validateForLeave(String organizationName) {` |
| GL-controllers_OrganizationApp-017 | `app/controllers/OrganizationApp.java:336` | `public static Result members(String organizationName) {` |
| GL-controllers_OrganizationApp-018 | `app/controllers/OrganizationApp.java:348` | `private static Result validateForSetting(String organizationName) {` |
| GL-controllers_OrganizationApp-019 | `app/controllers/OrganizationApp.java:363` | `public static Result settingForm(String organizationName) {` |
| GL-controllers_OrganizationApp-020 | `app/controllers/OrganizationApp.java:375` | `private static Result okWithLocation(String location) {` |
| GL-controllers_OrganizationApp-021 | `app/controllers/OrganizationApp.java:383` | `/**` |
| GL-controllers_OrganizationApp-025 | `app/controllers/OrganizationApp.java:471` | `public static Result deleteForm(String organizationName) {` |
| GL-controllers_OrganizationApp-026 | `app/controllers/OrganizationApp.java:483` | `@Transactional` |
| GL-controllers_OrganizationApp-027 | `app/controllers/OrganizationApp.java:499` | `private static ValidationResult validateForDelete(Organization organization) {` |
| GL-controllers_OrganizationApp-028 | `app/controllers/OrganizationApp.java:514` | `@GuestProhibit` |
| GL-controllers_ProjectApp-001 | `app/controllers/ProjectApp.java:65` | `@AnonymousCheck` |
| GL-controllers_ProjectApp-012 | `app/controllers/ProjectApp.java:99` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ProjectApp-013 | `app/controllers/ProjectApp.java:116` | `@IsAllowed(Operation.READ)` |
| GL-controllers_ProjectApp-019 | `app/controllers/ProjectApp.java:220` | `private static boolean validateWhenNew(Form<Project> newProjectForm) {` |
| GL-controllers_ProjectApp-020 | `app/controllers/ProjectApp.java:255` | `@Transactional` |
| GL-controllers_ProjectApp-021 | `app/controllers/ProjectApp.java:301` | `public static void saveProjectMenuSetting(Project project) {` |
| GL-controllers_ProjectApp-022 | `app/controllers/ProjectApp.java:316` | `private static boolean validateWhenUpdate(String loginId, Form<Project> updateProjectForm) {` |
| GL-controllers_ProjectApp-023 | `app/controllers/ProjectApp.java:354` | `@IsAllowed(Operation.DELETE)` |
| GL-controllers_ProjectApp-024 | `app/controllers/ProjectApp.java:362` | `@Transactional` |
| GL-controllers_ProjectApp-025 | `app/controllers/ProjectApp.java:383` | `@Transactional` |
| GL-controllers_ProjectApp-026 | `app/controllers/ProjectApp.java:394` | `@Transactional` |
| GL-controllers_ProjectApp-027 | `app/controllers/ProjectApp.java:411` | `@AnonymousCheck` |
| GL-controllers_ProjectApp-030 | `app/controllers/ProjectApp.java:466` | `private static void addProjectNameToMentionList(List<Map<String, String>> users, Project project) {` |
| GL-controllers_ProjectApp-031 | `app/controllers/ProjectApp.java:487` | `private static void addOrganizationNameToMentionList(List<Map<String, String>> users, Project projec` |
| GL-controllers_ProjectApp-032 | `app/controllers/ProjectApp.java:503` | `private static void collectedIssuesToMap(List<Map<String, String>> mentionList,` |
| GL-controllers_ProjectApp-034 | `app/controllers/ProjectApp.java:533` | `@IsAllowed(Operation.READ)` |
| GL-controllers_ProjectApp-035 | `app/controllers/ProjectApp.java:576` | `@IsAllowed(Operation.READ)` |
| GL-controllers_ProjectApp-036 | `app/controllers/ProjectApp.java:619` | `private static void addCommentAuthors(Long pullRequestId, List<User> userList) {` |
| GL-controllers_ProjectApp-037 | `app/controllers/ProjectApp.java:634` | `@IsAllowed(Operation.DELETE)` |
| GL-controllers_ProjectApp-038 | `app/controllers/ProjectApp.java:643` | `@Transactional` |
| GL-controllers_ProjectApp-039 | `app/controllers/ProjectApp.java:686` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ProjectApp-040 | `app/controllers/ProjectApp.java:745` | `private static void disableProjectTransferLink(ProjectTransfer pt, Project project, String newProjec` |
| GL-controllers_ProjectApp-041 | `app/controllers/ProjectApp.java:755` | `@IsAllowed(Operation.UPDATE)` |
| GL-controllers_ProjectApp-042 | `app/controllers/ProjectApp.java:763` | `@IsAllowed(Operation.UPDATE)` |
| GL-controllers_ProjectApp-043 | `app/controllers/ProjectApp.java:785` | `private static void sendTransferRequestMail(ProjectTransfer pt) {` |
| GL-controllers_ProjectApp-044 | `app/controllers/ProjectApp.java:828` | `private static void addCodeCommenters(String commitId, Long projectId, List<User> userList) {` |
| GL-controllers_ProjectApp-045 | `app/controllers/ProjectApp.java:863` | `private static void addCommitAuthor(Commit commit, List<User> userList) {` |
| GL-controllers_ProjectApp-046 | `app/controllers/ProjectApp.java:878` | `private static void collectAuthorAndCommenter(Project project, Long number, List<User> userList, Str` |
| GL-controllers_ProjectApp-047 | `app/controllers/ProjectApp.java:908` | `private static void collectedUsersToMentionList(List<Map<String, String>> users, List<User> userList` |
| GL-controllers_ProjectApp-048 | `app/controllers/ProjectApp.java:922` | `private static void addSearchedUsers(String query, List<User> userList) {` |
| GL-controllers_ProjectApp-049 | `app/controllers/ProjectApp.java:931` | `private static void addProjectMemberList(Project project, List<User> userList) {` |
| GL-controllers_ProjectApp-050 | `app/controllers/ProjectApp.java:940` | `private static void addGroupMemberList(Project project, List<User> userList) {` |
| GL-controllers_ProjectApp-051 | `app/controllers/ProjectApp.java:953` | `private static void addProjectAuthorsAndWatchersList(Project project, List<User> userList) {` |
| GL-controllers_ProjectApp-052 | `app/controllers/ProjectApp.java:966` | `private static void addSharers(Project project, Long number, List<User> userList, String resourceTyp` |
| GL-controllers_ProjectApp-053 | `app/controllers/ProjectApp.java:990` | `@Transactional` |
| GL-controllers_ProjectApp-055 | `app/controllers/ProjectApp.java:1037` | `/**` |
| GL-controllers_ProjectApp-056 | `app/controllers/ProjectApp.java:1054` | `/**` |
| GL-controllers_ProjectApp-057 | `app/controllers/ProjectApp.java:1089` | `/**` |
| GL-controllers_ProjectApp-058 | `app/controllers/ProjectApp.java:1110` | `/**` |
| GL-controllers_ProjectApp-062 | `app/controllers/ProjectApp.java:1177` | `private static ExpressionList<Project> createProjectSearchExpressionList(String query) {` |
| GL-controllers_ProjectApp-063 | `app/controllers/ProjectApp.java:1201` | `/**` |
| GL-controllers_ProjectApp-064 | `app/controllers/ProjectApp.java:1223` | `/**` |
| GL-controllers_ProjectApp-065 | `app/controllers/ProjectApp.java:1236` | `/**` |
| GL-controllers_ProjectApp-066 | `app/controllers/ProjectApp.java:1297` | `/**` |
| GL-controllers_ProjectApp-067 | `app/controllers/ProjectApp.java:1331` | `/**` |
| GL-controllers_ProjectApp-068 | `app/controllers/ProjectApp.java:1351` | `@Transactional` |
| GL-controllers_ProjectApp-069 | `app/controllers/ProjectApp.java:1374` | `private static void createWebhook(Project project, Form<Webhook> forms) {` |
| GL-controllers_ProjectApp-070 | `app/controllers/ProjectApp.java:1383` | `@Transactional` |
| GL-controllers_ProjectApp-071 | `app/controllers/ProjectApp.java:1396` | `@Transactional` |
| GL-controllers_ProjectApp-072 | `app/controllers/ProjectApp.java:1408` | `@IsAllowed(Operation.READ)` |
| GL-controllers_ImportApp-001 | `app/controllers/ImportApp.java:50` | `@AnonymousCheck` |
| GL-controllers_ImportApp-002 | `app/controllers/ImportApp.java:54` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ImportApp-003 | `app/controllers/ImportApp.java:63` | `@Transactional` |
| GL-controllers_ImportApp-004 | `app/controllers/ImportApp.java:123` | `private static void saveProjectMenuSetting(Project project) {` |
| GL-controllers_ImportApp-005 | `app/controllers/ImportApp.java:139` | `/**` |
| GL-controllers_ImportApp-006 | `app/controllers/ImportApp.java:173` | `private static ValidationResult validateForm(Form<Project> newProjectForm, Organization organization` |
| GL-controllers_Secured-001 | `app/controllers/Secured.java:10` | `public class Secured extends Security.Authenticator {` |
| GL-controllers_Secured-002 | `app/controllers/Secured.java:13` | `@Override` |
| GL-controllers_Secured-003 | `app/controllers/Secured.java:25` | `@Override` |
| GL-controllers_SvnApp-001 | `app/controllers/SvnApp.java:40` | `public class SvnApp extends Controller {` |
| GL-controllers_SvnApp-002 | `app/controllers/SvnApp.java:42` | `private static final String[] WEBDAV_METHODS = {` |
| GL-controllers_SvnApp-004 | `app/controllers/SvnApp.java:71` | `@With(BasicAuthAction.class)` |
| GL-controllers_SvnApp-005 | `app/controllers/SvnApp.java:78` | `@With(BasicAuthAction.class)` |
| GL-controllers_SvnApp-006 | `app/controllers/SvnApp.java:151` | `private static PlayServletResponse startDavService(final String ownerName, String pathInfo) throws I` |
| GL-controllers_SvnApp-007 | `app/controllers/SvnApp.java:177` | `private static Result sendResponse(String requestMethod, int statusCode,` |
| GL-controllers_StatisticsApp-001 | `app/controllers/StatisticsApp.java:32` | `@AnonymousCheck` |
| GL-controllers_StatisticsApp-002 | `app/controllers/StatisticsApp.java:36` | `@With(DefaultProjectCheckAction.class)` |
| GL-controllers_CompareApp-001 | `app/controllers/CompareApp.java:41` | `@AnonymousCheck` |
| GL-controllers_CompareApp-002 | `app/controllers/CompareApp.java:44` | `@IsAllowed(Operation.READ)` |
| GL-controllers_MarkdownApp-001 | `app/controllers/MarkdownApp.java:30` | `public class MarkdownApp extends Controller {` |
| GL-controllers_MarkdownApp-002 | `app/controllers/MarkdownApp.java:32` | `public static Result render(String ownerName, String projectName) {` |
| GL-controllers_CodeApp-001 | `app/controllers/CodeApp.java:46` | `@AnonymousCheck` |
| GL-controllers_CodeApp-003 | `app/controllers/CodeApp.java:52` | `@IsAllowed(Operation.READ)` |
| GL-controllers_CodeApp-004 | `app/controllers/CodeApp.java:89` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-005 | `app/controllers/CodeApp.java:127` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-007 | `app/controllers/CodeApp.java:174` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-008 | `app/controllers/CodeApp.java:191` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-009 | `app/controllers/CodeApp.java:212` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-010 | `app/controllers/CodeApp.java:222` | `private static Tika tika = new Tika();` |
| GL-controllers_CodeApp-014 | `app/controllers/CodeApp.java:256` | `@IsAllowed(Operation.READ)` |
| GL-controllers_AbstractPostingApp-001 | `app/controllers/AbstractPostingApp.java:34` | `@AnonymousCheck` |
| GL-controllers_AbstractPostingApp-004 | `app/controllers/AbstractPostingApp.java:42` | `public static class SearchCondition {` |
| GL-controllers_AbstractPostingApp-005 | `app/controllers/AbstractPostingApp.java:57` | `public static Comment saveComment(final Comment comment, Runnable containerUpdater) {` |
| GL-controllers_AbstractPostingApp-006 | `app/controllers/AbstractPostingApp.java:74` | `protected static Result delete(Model target, Resource resource, Call redirectTo) {` |
| GL-controllers_AbstractPostingApp-007 | `app/controllers/AbstractPostingApp.java:90` | `protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extend` |
| GL-controllers_AbstractPostingApp-008 | `app/controllers/AbstractPostingApp.java:141` | `private static String addToHistory(AbstractPosting original, AbstractPosting posting) {` |
| GL-controllers_AbstractPostingApp-011 | `app/controllers/AbstractPostingApp.java:232` | `public static void attachUploadFilesToPost(Resource resource) {` |
| GL-controllers_AbstractPostingApp-012 | `app/controllers/AbstractPostingApp.java:245` | `public static void attachUploadFilesToPost(JsonNode files, Resource resource) {` |
| GL-controllers_WatchProjectApp-001 | `app/controllers/WatchProjectApp.java:28` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_WatchProjectApp-002 | `app/controllers/WatchProjectApp.java:32` | `@IsAllowed(Operation.READ)` |
| GL-controllers_WatchProjectApp-003 | `app/controllers/WatchProjectApp.java:41` | `@IsAllowed(Operation.READ)` |
| GL-controllers_WatchProjectApp-004 | `app/controllers/WatchProjectApp.java:53` | `public static Result toggle(Long projectId, String notificationType) {` |
| GL-controllers_HelpApp-001 | `app/controllers/HelpApp.java:29` | `@AnonymousCheck` |
| GL-controllers_HelpApp-002 | `app/controllers/HelpApp.java:32` | `public static Result help() {` |
| GL-controllers_BoardApp-001 | `app/controllers/BoardApp.java:48` | `public class BoardApp extends AbstractPostingApp {` |
| GL-controllers_BoardApp-002 | `app/controllers/BoardApp.java:50` | `public static class SearchCondition extends AbstractPostingApp.SearchCondition {` |
| GL-controllers_BoardApp-003 | `app/controllers/BoardApp.java:122` | `@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)` |
| GL-controllers_BoardApp-004 | `app/controllers/BoardApp.java:143` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PROJECT)` |
| GL-controllers_BoardApp-005 | `app/controllers/BoardApp.java:165` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_BoardApp-006 | `app/controllers/BoardApp.java:196` | `private static boolean projectHasReadme(Project project) {` |
| GL-controllers_BoardApp-007 | `app/controllers/BoardApp.java:201` | `private static boolean readmeEditRequested() {` |
| GL-controllers_BoardApp-008 | `app/controllers/BoardApp.java:206` | `private static boolean issueTemplateEditRequested() {` |
| GL-controllers_BoardApp-011 | `app/controllers/BoardApp.java:221` | `@Transactional` |
| GL-controllers_BoardApp-012 | `app/controllers/BoardApp.java:275` | `private static void commitReadmeFile(Project project, Posting post){` |
| GL-controllers_BoardApp-013 | `app/controllers/BoardApp.java:287` | `private static void commitIssueTemplateFile(Project project, Posting post){` |
| GL-controllers_BoardApp-014 | `app/controllers/BoardApp.java:299` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)` |
| GL-controllers_BoardApp-015 | `app/controllers/BoardApp.java:329` | `@With(NullProjectCheckAction.class)` |
| GL-controllers_BoardApp-016 | `app/controllers/BoardApp.java:348` | `/**` |
| GL-controllers_BoardApp-017 | `app/controllers/BoardApp.java:385` | `private static void unmarkAnotherReadmePostingIfExists(Project project, Long postingNumber) {` |
| GL-controllers_BoardApp-018 | `app/controllers/BoardApp.java:394` | `/**` |
| GL-controllers_BoardApp-020 | `app/controllers/BoardApp.java:449` | `private static void AddPreviousContent(Posting posting, PostingComment comment) {` |
| GL-controllers_BoardApp-022 | `app/controllers/BoardApp.java:474` | `// Just made for compatibility. No meanings.` |
| GL-controllers_BoardApp-023 | `app/controllers/BoardApp.java:480` | `private static Comment saveComment(Project project, Posting posting, PostingComment comment) {` |
| GL-controllers_BoardApp-025 | `app/controllers/BoardApp.java:508` | `/**` |
| GL-controllers_MigrationApp-001 | `app/controllers/MigrationApp.java:43` | `@AnonymousCheck` |
| GL-controllers_MigrationApp-002 | `app/controllers/MigrationApp.java:47` | `static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");` |
| GL-controllers_MigrationApp-004 | `app/controllers/MigrationApp.java:54` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-006 | `app/controllers/MigrationApp.java:99` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-007 | `app/controllers/MigrationApp.java:120` | `private static List<Project> sortProjectsByOwnerAndName(Set<Project> projects) {` |
| GL-controllers_MigrationApp-008 | `app/controllers/MigrationApp.java:129` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-011 | `app/controllers/MigrationApp.java:172` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-012 | `app/controllers/MigrationApp.java:179` | `public static ObjectNode composeIssueLabelPairJson(String owner, String projectName) {` |
| GL-controllers_MigrationApp-013 | `app/controllers/MigrationApp.java:198` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-014 | `app/controllers/MigrationApp.java:218` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-015 | `app/controllers/MigrationApp.java:231` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-016 | `app/controllers/MigrationApp.java:244` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-017 | `app/controllers/MigrationApp.java:257` | `public static ObjectNode composeMilestoneJson(Milestone m) {` |
| GL-controllers_MigrationApp-019 | `app/controllers/MigrationApp.java:277` | `private static String addOriginalAuthorName(String bodyText, String authorLoginId,` |
| GL-controllers_MigrationApp-020 | `app/controllers/MigrationApp.java:284` | `private static String relativeLinksToAbsolutePath(String text){` |
| GL-controllers_MigrationApp-021 | `app/controllers/MigrationApp.java:292` | `private static String relativeLinksToWikiCommitPath(String text){` |
| GL-controllers_MigrationApp-022 | `app/controllers/MigrationApp.java:300` | `private static StringBuilder addAttachmentsString(@NotNull StringBuilder sb, ResourceType type, Stri` |
| GL-controllers_MigrationApp-023 | `app/controllers/MigrationApp.java:316` | `private static void addListHeader(@NotNull StringBuilder sb) {` |
| GL-controllers_MigrationApp-024 | `app/controllers/MigrationApp.java:321` | `private static StringBuilder addAttachmentsStringUsingWikiCommit(@NotNull StringBuilder sb, Resource` |
| GL-controllers_MigrationApp-025 | `app/controllers/MigrationApp.java:338` | `private static ObjectNode composePostJson(Posting posting) {` |
| GL-controllers_MigrationApp-026 | `app/controllers/MigrationApp.java:370` | `private static boolean usingWikiCommitForAttachment() {` |
| GL-controllers_MigrationApp-027 | `app/controllers/MigrationApp.java:377` | `private static ObjectNode composeIssueJson(Issue issue) {` |
| GL-controllers_MigrationApp-028 | `app/controllers/MigrationApp.java:414` | `public static List<ObjectNode> composeCommentsJson(AbstractPosting posting, String orgLink, Resource` |
| GL-controllers_MigrationApp-029 | `app/controllers/MigrationApp.java:440` | `public static List<ObjectNode> composePlainCommentsJson(AbstractPosting posting, ResourceType type) ` |
| GL-controllers_MigrationApp-030 | `app/controllers/MigrationApp.java:461` | `private static void gatheringUserProjects(Set<Project> targetProjects) {` |
| GL-controllers_MigrationApp-031 | `app/controllers/MigrationApp.java:469` | `private static void getheringOrgProjects(Set<Project> targetProjects) {` |
| GL-controllers_GitApp-001 | `app/controllers/GitApp.java:43` | `public class GitApp extends Controller {` |
| GL-controllers_GitApp-004 | `app/controllers/GitApp.java:72` | `/**` |
| GL-controllers_GitApp-006 | `app/controllers/GitApp.java:150` | `@With(BasicAuthAction.class)` |
| GL-controllers_GitApp-007 | `app/controllers/GitApp.java:162` | `@With(BasicAuthAction.class)` |
| GL-controllers_PasswordResetApp-001 | `app/controllers/PasswordResetApp.java:47` | `public class PasswordResetApp extends Controller {` |
| GL-controllers_PasswordResetApp-002 | `app/controllers/PasswordResetApp.java:50` | `public static Result lostPassword(){` |
| GL-controllers_PasswordResetApp-003 | `app/controllers/PasswordResetApp.java:56` | `public static Result requestResetPasswordEmail(){` |
| GL-controllers_PasswordResetApp-004 | `app/controllers/PasswordResetApp.java:79` | `private static boolean sendPasswordResetMail(User user, String hashString) {` |
| GL-controllers_PasswordResetApp-006 | `app/controllers/PasswordResetApp.java:105` | `public static Result resetPasswordForm(String hashString){` |
| GL-controllers_PasswordResetApp-007 | `app/controllers/PasswordResetApp.java:110` | `public static Result resetPassword(){` |
| GL-controllers_PullRequestApp-001 | `app/controllers/PullRequestApp.java:69` | `@IsOnlyGitAvailable` |
| GL-controllers_PullRequestApp-002 | `app/controllers/PullRequestApp.java:74` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-003 | `app/controllers/PullRequestApp.java:86` | `private static String findDestination(String forkOwner) {` |
| GL-controllers_PullRequestApp-004 | `app/controllers/PullRequestApp.java:95` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-005 | `app/controllers/PullRequestApp.java:118` | `@Transactional` |
| GL-controllers_PullRequestApp-006 | `app/controllers/PullRequestApp.java:169` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-008 | `app/controllers/PullRequestApp.java:217` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-009 | `app/controllers/PullRequestApp.java:245` | `static class PullRequestCreationResult {` |
| GL-controllers_PullRequestApp-010 | `app/controllers/PullRequestApp.java:263` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-011 | `app/controllers/PullRequestApp.java:272` | `@Transactional` |
| GL-controllers_PullRequestApp-012 | `app/controllers/PullRequestApp.java:330` | `private static void validateForm(Form<PullRequest> form) {` |
| GL-controllers_PullRequestApp-013 | `app/controllers/PullRequestApp.java:338` | `@IsAllowed(Operation.READ)` |
| GL-controllers_PullRequestApp-014 | `app/controllers/PullRequestApp.java:344` | `@IsAllowed(Operation.READ)` |
| GL-controllers_PullRequestApp-015 | `app/controllers/PullRequestApp.java:350` | `@IsAllowed(Operation.READ)` |
| GL-controllers_PullRequestApp-016 | `app/controllers/PullRequestApp.java:356` | `@Transactional` |
| GL-controllers_PullRequestApp-017 | `app/controllers/PullRequestApp.java:377` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)` |
| GL-controllers_PullRequestApp-018 | `app/controllers/PullRequestApp.java:395` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)` |
| GL-controllers_PullRequestApp-019 | `app/controllers/PullRequestApp.java:425` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)` |
| GL-controllers_PullRequestApp-020 | `app/controllers/PullRequestApp.java:432` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)` |
| GL-controllers_PullRequestApp-021 | `app/controllers/PullRequestApp.java:441` | `@Transactional` |
| GL-controllers_PullRequestApp-022 | `app/controllers/PullRequestApp.java:483` | `private static void addNotification(PullRequest pullRequest, State from, State to) {` |
| GL-controllers_PullRequestApp-023 | `app/controllers/PullRequestApp.java:489` | `@Transactional` |
| GL-controllers_PullRequestApp-024 | `app/controllers/PullRequestApp.java:507` | `@Transactional` |
| GL-controllers_PullRequestApp-025 | `app/controllers/PullRequestApp.java:533` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-026 | `app/controllers/PullRequestApp.java:548` | `@Transactional` |
| GL-controllers_PullRequestApp-027 | `app/controllers/PullRequestApp.java:583` | `@Transactional` |
| GL-controllers_PullRequestApp-028 | `app/controllers/PullRequestApp.java:596` | `@Transactional` |
| GL-controllers_PullRequestApp-029 | `app/controllers/PullRequestApp.java:609` | `private static ValidationResult validateBeforePullRequest(Project project) {` |
| GL-controllers_PullRequestApp-030 | `app/controllers/PullRequestApp.java:622` | `@IsCreatable(ResourceType.REVIEW_COMMENT)` |
| GL-controllers_PullRequestApp-031 | `app/controllers/PullRequestApp.java:707` | `static class ValidationResult {` |
| GL-controllers_PullRequestApp-032 | `app/controllers/PullRequestApp.java:726` | `public static class SearchCondition implements Cloneable {` |
| GL-controllers_PullRequestApp-033 | `app/controllers/PullRequestApp.java:795` | `public enum Category {` |
| GL-controllers_ReviewThreadApp-001 | `app/controllers/ReviewThreadApp.java:36` | `@AnonymousCheck` |
| GL-controllers_ReviewThreadApp-003 | `app/controllers/ReviewThreadApp.java:43` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ReviewThreadApp-004 | `app/controllers/ReviewThreadApp.java:58` | `private static Result reviewThreadsDownload(Project project, ExpressionList<CommentThread> el) {` |
| GL-controllers_ReviewThreadApp-005 | `app/controllers/ReviewThreadApp.java:79` | `public static byte[] excelFrom(List<CommentThread> commentThreads) throws WriteException, IOExceptio` |
| GL-controllers_SiteApp-001 | `app/controllers/SiteApp.java:72` | `/**` |
| GL-controllers_SiteApp-005 | `app/controllers/SiteApp.java:87` | `/**` |
| GL-controllers_SiteApp-006 | `app/controllers/SiteApp.java:111` | `public static Result writeMail(String errorMessage, boolean sended) {` |
| GL-controllers_SiteApp-007 | `app/controllers/SiteApp.java:128` | `public static Result massMail() {` |
| GL-controllers_SiteApp-008 | `app/controllers/SiteApp.java:133` | `/**` |
| GL-controllers_SiteApp-009 | `app/controllers/SiteApp.java:147` | `/**` |
| GL-controllers_SiteApp-010 | `app/controllers/SiteApp.java:157` | `/**` |
| GL-controllers_SiteApp-011 | `app/controllers/SiteApp.java:169` | `/**` |
| GL-controllers_SiteApp-012 | `app/controllers/SiteApp.java:196` | `@Transactional` |
| GL-controllers_SiteApp-013 | `app/controllers/SiteApp.java:221` | `/**` |
| GL-controllers_SiteApp-014 | `app/controllers/SiteApp.java:233` | `/**` |
| GL-controllers_SiteApp-015 | `app/controllers/SiteApp.java:248` | `/**` |
| GL-controllers_SiteApp-016 | `app/controllers/SiteApp.java:275` | `public static Result toggleGuestMode(String loginId, String state, String query){` |
| GL-controllers_SiteApp-017 | `app/controllers/SiteApp.java:294` | `public static Result mailList() {` |
| GL-controllers_SiteApp-018 | `app/controllers/SiteApp.java:333` | `/**` |
| GL-controllers_SiteApp-019 | `app/controllers/SiteApp.java:342` | `/**` |
| GL-controllers_SiteApp-020 | `app/controllers/SiteApp.java:364` | `/**` |
| GL-controllers_SiteApp-021 | `app/controllers/SiteApp.java:373` | `public static Result data() {` |
| GL-controllers_SiteApp-022 | `app/controllers/SiteApp.java:378` | `public static Result exportData() throws JsonProcessingException {` |
| GL-controllers_SiteApp-023 | `app/controllers/SiteApp.java:391` | `public static Result importData() throws IOException {` |
| GL-controllers_SiteApp-024 | `app/controllers/SiteApp.java:408` | `public static Result noAvatarUsers() {` |
| GL-controllers_SiteApp-025 | `app/controllers/SiteApp.java:425` | `private static ObjectNode composeUserNode(User user) {` |
| GL-controllers_BranchApp-001 | `app/controllers/BranchApp.java:46` | `/**` |
| GL-controllers_BranchApp-002 | `app/controllers/BranchApp.java:54` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_BranchApp-003 | `app/controllers/BranchApp.java:74` | `@IsAllowed(Operation.DELETE)` |
| GL-controllers_BranchApp-004 | `app/controllers/BranchApp.java:84` | `@IsAllowed(Operation.UPDATE)` |
| GL-controllers_MilestoneApp-001 | `app/controllers/MilestoneApp.java:49` | `@AnonymousCheck` |
| GL-controllers_MilestoneApp-004 | `app/controllers/MilestoneApp.java:83` | `/**` |
| GL-controllers_MilestoneApp-005 | `app/controllers/MilestoneApp.java:94` | `/**` |
| GL-controllers_MilestoneApp-007 | `app/controllers/MilestoneApp.java:129` | `private static void validateDueDate(Form<Milestone> milestoneForm) {` |
| GL-controllers_MilestoneApp-008 | `app/controllers/MilestoneApp.java:136` | `/**` |
| GL-controllers_MilestoneApp-009 | `app/controllers/MilestoneApp.java:150` | `/**` |
| GL-controllers_MilestoneApp-010 | `app/controllers/MilestoneApp.java:183` | `/**` |
| GL-controllers_MilestoneApp-011 | `app/controllers/MilestoneApp.java:206` | `@Transactional` |
| GL-controllers_MilestoneApp-012 | `app/controllers/MilestoneApp.java:215` | `@Transactional` |
| GL-controllers_MilestoneApp-013 | `app/controllers/MilestoneApp.java:224` | `/**` |
| GL-controllers_CommentThreadApp-001 | `app/controllers/CommentThreadApp.java:36` | `@AnonymousCheck` |
| GL-controllers_CommentThreadApp-002 | `app/controllers/CommentThreadApp.java:40` | `@Transactional` |
| GL-controllers_CommentThreadApp-003 | `app/controllers/CommentThreadApp.java:80` | `public static Result open(Long id) {` |
| GL-controllers_CommentThreadApp-004 | `app/controllers/CommentThreadApp.java:85` | `public static Result close(Long id) {` |
| GL-controllers_ReviewApp-001 | `app/controllers/ReviewApp.java:38` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ReviewApp-002 | `app/controllers/ReviewApp.java:42` | `@Transactional` |
| GL-controllers_ReviewApp-003 | `app/controllers/ReviewApp.java:57` | `@Transactional` |
| GL-controllers_ReviewApp-004 | `app/controllers/ReviewApp.java:72` | `private static void addNotification(PullRequest pullRequest, PullRequestReviewAction reviewAction) {` |
| GL-controllers_LabelApp-001 | `app/controllers/LabelApp.java:43` | `@AnonymousCheck` |
| GL-controllers_LabelApp-003 | `app/controllers/LabelApp.java:49` | `/**` |
| GL-controllers_LabelApp-004 | `app/controllers/LabelApp.java:83` | `public static Result categories(String query, Integer limit) {` |
| GL-controllers_AttachmentApp-001 | `app/controllers/AttachmentApp.java:37` | `@AnonymousCheck` |
| GL-controllers_AttachmentApp-003 | `app/controllers/AttachmentApp.java:43` | `public static final long TEMPORARYFILES_KEEPUP_TIME_MILLIS = Configuration.root()` |
| GL-controllers_AttachmentApp-004 | `app/controllers/AttachmentApp.java:47` | `private static User findUploader(Map<String,String[]> formUrlEncoded) {` |
| GL-controllers_AttachmentApp-005 | `app/controllers/AttachmentApp.java:66` | `public static Result uploadFile() throws NoSuchAlgorithmException, IOException {` |
| GL-controllers_AttachmentApp-007 | `app/controllers/AttachmentApp.java:181` | `public static Result deleteFile(Long id) {` |
| GL-controllers_AttachmentApp-008 | `app/controllers/AttachmentApp.java:216` | `private static void logIfOriginFileIsNotValid(String hash) {` |
| GL-controllers_AttachmentApp-009 | `app/controllers/AttachmentApp.java:231` | `private static Map<String, String> extractFileMetaDataFromAttachementAsMap(Attachment attach) {` |
| GL-controllers_AttachmentApp-013 | `app/controllers/AttachmentApp.java:289` | `private static class PermissionDeniedException extends Exception {` |
| GL-controllers_SearchApp-001 | `app/controllers/SearchApp.java:35` | `@AnonymousCheck` |
| GL-controllers_SearchApp-002 | `app/controllers/SearchApp.java:39` | `private static final PageParam DEFAULT_PAGE = new PageParam(0, 20);` |
| GL-controllers_SearchApp-003 | `app/controllers/SearchApp.java:42` | `/**` |
| GL-controllers_SearchApp-005 | `app/controllers/SearchApp.java:112` | `/**` |
| GL-controllers_SearchApp-007 | `app/controllers/SearchApp.java:194` | `/**` |
| GL-controllers_CommentApp-001 | `app/controllers/CommentApp.java:35` | `@AnonymousCheck` |
| GL-controllers_CommentApp-002 | `app/controllers/CommentApp.java:38` | `@Transactional` |
| GL-controllers_Restricted-001 | `app/controllers/Restricted.java:10` | `@Security.Authenticated(Secured.class)` |
| GL-controllers_Restricted-002 | `app/controllers/Restricted.java:14` | `public static Result index() {` |
| GL-controllers_Application-001 | `app/controllers/Application.java:30` | `public class Application extends Controller {` |
| GL-controllers_Application-016 | `app/controllers/Application.java:61` | `@AnonymousCheck` |
| GL-controllers_Application-017 | `app/controllers/Application.java:72` | `@AnonymousCheck` |
| GL-controllers_Application-018 | `app/controllers/Application.java:78` | `@AnonymousCheck` |
| GL-controllers_Application-019 | `app/controllers/Application.java:84` | `public static Result oAuth(final String provider) {` |
| GL-controllers_Application-020 | `app/controllers/Application.java:89` | `public static Result oAuthLogout() {` |
| GL-controllers_Application-021 | `app/controllers/Application.java:96` | `public static Result oAuthDenied(final String providerKey) {` |
| GL-controllers_Application-024 | `app/controllers/Application.java:118` | `public static Result removeTrailer(String paths){` |
| GL-controllers_Application-025 | `app/controllers/Application.java:131` | `public static Result init() {` |
| GL-controllers_Application-026 | `app/controllers/Application.java:137` | `static final JsMessages messages = JsMessages.create(play.Play.application());` |
| GL-controllers_Application-027 | `app/controllers/Application.java:140` | `public static Result jsMessages() {` |
| GL-controllers_Application-028 | `app/controllers/Application.java:145` | `private static void makeTestRepository() {` |
| GL-controllers_Application-029 | `app/controllers/Application.java:157` | `public static Result navi() {` |
| GL-controllers_Application-030 | `app/controllers/Application.java:162` | `public static Result UIKit(){` |
| GL-controllers_Application-031 | `app/controllers/Application.java:167` | `public static Result fake() {` |
| GL-controllers_Application-032 | `app/controllers/Application.java:173` | `public static Result returnToReferer() {` |
| GL-controllers_EnrollOrganizationApp-001 | `app/controllers/EnrollOrganizationApp.java:38` | `@AnonymousCheck` |
| GL-controllers_EnrollOrganizationApp-002 | `app/controllers/EnrollOrganizationApp.java:42` | `@Transactional` |
| GL-controllers_EnrollOrganizationApp-003 | `app/controllers/EnrollOrganizationApp.java:64` | `private static ValidationResult validateForEnroll(String organizationName) {` |
| GL-controllers_EnrollOrganizationApp-004 | `app/controllers/EnrollOrganizationApp.java:79` | `@Transactional` |
| GL-controllers_api_UserApi-001 | `app/controllers/api/UserApi.java:55` | `public class UserApi extends Controller {` |
| GL-controllers_api_UserApi-006 | `app/controllers/api/UserApi.java:67` | `@Transactional` |
| GL-controllers_api_UserApi-007 | `app/controllers/api/UserApi.java:80` | `@Transactional` |
| GL-controllers_api_UserApi-008 | `app/controllers/api/UserApi.java:99` | `@Transactional` |
| GL-controllers_api_UserApi-009 | `app/controllers/api/UserApi.java:119` | `@Transactional` |
| GL-controllers_api_UserApi-010 | `app/controllers/api/UserApi.java:138` | `@Transactional` |
| GL-controllers_api_UserApi-011 | `app/controllers/api/UserApi.java:158` | `private static Result issuesAsJson(Page<Issue> issues) {` |
| GL-controllers_api_UserApi-012 | `app/controllers/api/UserApi.java:202` | `@Transactional` |
| GL-controllers_api_UserApi-013 | `app/controllers/api/UserApi.java:215` | `@Transactional` |
| GL-controllers_api_UserApi-016 | `app/controllers/api/UserApi.java:284` | `@AnonymousCheck(requiresLogin = true)` |
| GL-controllers_api_UserApi-022 | `app/controllers/api/UserApi.java:395` | `private static UserState findUserState(JsonNode json) {` |
| GL-controllers_api_UserApi-027 | `app/controllers/api/UserApi.java:464` | `private static JsonNode successfullyCreatedUserNode(User created) {` |
| GL-controllers_api_UserApi-028 | `app/controllers/api/UserApi.java:474` | `private static JsonNode notAllowedDomainEmailUser(JsonNode userNode) {` |
| GL-controllers_api_UserApi-029 | `app/controllers/api/UserApi.java:487` | `private static JsonNode alreadyExistedUser(JsonNode userNode) {` |
| GL-controllers_api_UserApi-030 | `app/controllers/api/UserApi.java:501` | `private static void loggingUser(JsonNode userNode, String message) {` |
| GL-controllers_api_BoardApi-001 | `app/controllers/api/BoardApi.java:40` | `public class BoardApi extends AbstractPostingApp {` |
| GL-controllers_api_BoardApi-002 | `app/controllers/api/BoardApi.java:43` | `@Transactional` |
| GL-controllers_api_BoardApi-003 | `app/controllers/api/BoardApi.java:68` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)` |
| GL-controllers_api_BoardApi-004 | `app/controllers/api/BoardApi.java:81` | `@Transactional` |
| GL-controllers_api_BoardApi-005 | `app/controllers/api/BoardApi.java:106` | `private static JsonNode createPostingNode(JsonNode json, Project project) {` |
| GL-controllers_api_BoardApi-007 | `app/controllers/api/BoardApi.java:169` | `@Transactional` |
| GL-controllers_api_GlobalApi-001 | `app/controllers/api/GlobalApi.java:16` | `public class GlobalApi extends Controller {` |
| GL-controllers_api_GlobalApi-002 | `app/controllers/api/GlobalApi.java:18` | `public static Result hello() {` |
| GL-controllers_api_WatcherApi-001 | `app/controllers/api/WatcherApi.java:25` | `public class WatcherApi extends Controller {` |
| GL-controllers_api_MilestoneApi-004 | `app/controllers/api/MilestoneApi.java:74` | `private static State parseMilestoneState(JsonNode json) {` |
| GL-controllers_api_MilestoneApi-005 | `app/controllers/api/MilestoneApi.java:86` | `private static Date parseDuedate(JsonNode json) {` |
| GL-controllers_api_MilestoneApi-006 | `app/controllers/api/MilestoneApi.java:96` | `private static String parseMilestoneTitle(JsonNode json) {` |
| GL-controllers_api_MilestoneApi-007 | `app/controllers/api/MilestoneApi.java:105` | `private static String parseMilestoneContents(JsonNode json) {` |
| GL-controllers_api_ProjectApi-001 | `app/controllers/api/ProjectApi.java:45` | `public class ProjectApi extends Controller {` |
| GL-controllers_api_ProjectApi-013 | `app/controllers/api/ProjectApi.java:244` | `private static void saveMenuSettingsToDefault(Project project) {` |
| GL-controllers_api_ProjectApi-014 | `app/controllers/api/ProjectApi.java:260` | `@IsAllowed(Operation.READ)` |
| GL-controllers_api_ProjectApi-015 | `app/controllers/api/ProjectApi.java:272` | `/**` |
| GL-controllers_api_ProjectApi-019 | `app/controllers/api/ProjectApi.java:347` | `private static JsonNode composeAuthorJson(User user) {` |
| GL-controllers_api_ProjectApi-027 | `app/controllers/api/ProjectApi.java:480` | `@Transactional` |
| GL-controllers_api_ProjectApi-028 | `app/controllers/api/ProjectApi.java:507` | `private static JsonNode createLabelNode(JsonNode json, Project project) {` |
| GL-controllers_api_ProjectApi-029 | `app/controllers/api/ProjectApi.java:544` | `private static JsonNode existedLabel(JsonNode labelNode) {` |
| GL-controllers_api_ProjectApi-030 | `app/controllers/api/ProjectApi.java:557` | `@Transactional` |
| GL-controllers_api_ProjectApi-031 | `app/controllers/api/ProjectApi.java:576` | `private static List<ObjectNode> getherProjectLabels(Project project) {` |
| GL-controllers_api_ProjectApi-032 | `app/controllers/api/ProjectApi.java:585` | `private static List<ObjectNode> getherTitleHeads(Project project, String query) {` |
| GL-controllers_api_IssueApi-001 | `app/controllers/api/IssueApi.java:51` | `public class IssueApi extends AbstractPostingApp {` |
| GL-controllers_api_IssueApi-007 | `app/controllers/api/IssueApi.java:64` | `@Transactional` |
| GL-controllers_api_IssueApi-008 | `app/controllers/api/IssueApi.java:99` | `private static void copyAttachmentsToIssue(Posting from, Issue to) {` |
| GL-controllers_api_IssueApi-009 | `app/controllers/api/IssueApi.java:111` | `private static void copyAttachmentsToIssueComments(Map<String, String> postingCommentIdToIssueCommen` |
| GL-controllers_api_IssueApi-010 | `app/controllers/api/IssueApi.java:128` | `private static void removePosting(Posting posting) {` |
| GL-controllers_api_IssueApi-011 | `app/controllers/api/IssueApi.java:133` | `private static Map<String, String> copyCommentsToIssue(Collection<PostingComment> postingComments, I` |
| GL-controllers_api_IssueApi-012 | `app/controllers/api/IssueApi.java:174` | `@Transactional` |
| GL-controllers_api_IssueApi-013 | `app/controllers/api/IssueApi.java:199` | `@Transactional` |
| GL-controllers_api_IssueApi-014 | `app/controllers/api/IssueApi.java:221` | `private static ObjectNode addIssueEvents(Issue issue, ObjectNode json) {` |
| GL-controllers_api_IssueApi-017 | `app/controllers/api/IssueApi.java:261` | `@Transactional` |
| GL-controllers_api_IssueApi-018 | `app/controllers/api/IssueApi.java:288` | `@Transactional` |
| GL-controllers_api_IssueApi-019 | `app/controllers/api/IssueApi.java:310` | `@Transactional` |
| GL-controllers_api_IssueApi-021 | `app/controllers/api/IssueApi.java:374` | `private static Result updateIssueNode(JsonNode json, Project project, Issue issue, User user) {` |
| GL-controllers_api_IssueApi-022 | `app/controllers/api/IssueApi.java:404` | `private static void addNewIssueEvent(Issue issue, User user, EventType eventType, String oldValue, S` |
| GL-controllers_api_IssueApi-023 | `app/controllers/api/IssueApi.java:416` | `private static JsonNode createIssuesNode(JsonNode json, Project project, boolean sendNotification) {` |
| GL-controllers_api_IssueApi-024 | `app/controllers/api/IssueApi.java:452` | `private static void updateLabels(JsonNode json, Issue issue, Project project) {` |
| GL-controllers_api_IssueApi-025 | `app/controllers/api/IssueApi.java:471` | `private static Milestone findMilestone(JsonNode milestoneTitle, Project project) {` |
| GL-controllers_api_IssueApi-026 | `app/controllers/api/IssueApi.java:479` | `private static Date findDueDate(JsonNode dueDateNode) {` |
| GL-controllers_api_IssueApi-027 | `app/controllers/api/IssueApi.java:492` | `private static State findIssueState(JsonNode json){` |
| GL-controllers_api_IssueApi-028 | `app/controllers/api/IssueApi.java:505` | `public static Result commentNotiRecivers(String ownerName, String projectName, Long number) {` |
| GL-controllers_api_IssueApi-034 | `app/controllers/api/IssueApi.java:671` | `private static Result createCommentByUser(Project project, Issue issue, JsonNode json) {` |
| GL-controllers_api_IssueApi-035 | `app/controllers/api/IssueApi.java:692` | `private static Result createCommentUsingToken(Issue issue, User user, String comment) {` |
| GL-controllers_api_IssueApi-036 | `app/controllers/api/IssueApi.java:699` | `private static IssueComment createComment(Issue issue, User user, String comment, JsonNode dateNode)` |
| GL-controllers_api_IssueApi-039 | `app/controllers/api/IssueApi.java:733` | `public static User findAuthor(JsonNode authorNode){` |
| GL-controllers_api_IssueApi-040 | `app/controllers/api/IssueApi.java:753` | `private static Assignee findAssginee(JsonNode assigneesNode, @Nonnull Project project) {` |
| GL-controllers_api_IssueApi-041 | `app/controllers/api/IssueApi.java:765` | `public static Date parseDateString(JsonNode dateStringNode){` |
| GL-controllers_api_IssueApi-042 | `app/controllers/api/IssueApi.java:780` | `@IsAllowed(Operation.READ)` |
| GL-controllers_api_IssueApi-043 | `app/controllers/api/IssueApi.java:813` | `private static void gatheringUsersFromExpressionList(Project project, List<ObjectNode> users, Expres` |
| GL-controllers_api_IssueApi-044 | `app/controllers/api/IssueApi.java:827` | `@IsAllowed(Operation.READ)` |
| GL-controllers_api_IssueApi-047 | `app/controllers/api/IssueApi.java:900` | `private static void addAuthorIfNotMe(Issue issue, List<ObjectNode> users, User issueAuthor) {` |
| GL-controllers_api_IssueApi-048 | `app/controllers/api/IssueApi.java:907` | `private static void addAuthorIfNotMeAndNotAssginee(Issue issue, List<ObjectNode> users, User issueAu` |
| GL-controllers_api_IssueApi-049 | `app/controllers/api/IssueApi.java:915` | `private static void addMyself(Issue issue, List<ObjectNode> users) {` |
| GL-controllers_api_IssueApi-050 | `app/controllers/api/IssueApi.java:922` | `static void addUserToUsers(User user, List<ObjectNode> users) {` |
| GL-controllers_api_IssueApi-051 | `app/controllers/api/IssueApi.java:936` | `static void addProjectToProjects(Project project, List<ObjectNode> projects) {` |
| GL-controllers_api_IssueApi-052 | `app/controllers/api/IssueApi.java:949` | `private static void addUserToUsersWithCustomName(User user, List<ObjectNode> users, String name) {` |
| GL-controllers_api_IssueApi-053 | `app/controllers/api/IssueApi.java:961` | `public static Result updateAssginees(String owner, String projectName, Long number){` |
| GL-controllers_api_IssueApi-054 | `app/controllers/api/IssueApi.java:1010` | `private static void composeResultJson(ObjectNode result, User assigneeUser) {` |
| GL-controllers_api_IssueApi-056 | `app/controllers/api/IssueApi.java:1033` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_api_IssueApi-057 | `app/controllers/api/IssueApi.java:1073` | `private static F.Promise<WSResponse> translate(String text, WSRequestHolder translator) {` |
| GL-controllers_api_IssueApi-058 | `app/controllers/api/IssueApi.java:1082` | `private static Supplier<WSRequestHolder> translatorWsRequestHolderSupplier = () -> WS.url(TRANSLATIO` |
| GL-controllers_api_IssueApi-059 | `app/controllers/api/IssueApi.java:1088` | `private static List<String> merge(List<String> texts) {` |
| GL-controllers_api_IssueApi-062 | `app/controllers/api/IssueApi.java:1147` | `@AnonymousCheck` |
| GL-controllers_api_IssueApi-063 | `app/controllers/api/IssueApi.java:1167` | `private static void sortListByAddedDate(List<IssueSharer> list) {` |
| GL-controllers_api_IssueApi-066 | `app/controllers/api/IssueApi.java:1217` | `public static Result updateSharer(String owner, String projectName, Long number){` |
| GL-controllers_api_IssueApi-067 | `app/controllers/api/IssueApi.java:1243` | `public static Result upvoteWeight(String owner, String projectName, Long number){` |
| GL-controllers_api_IssueApi-068 | `app/controllers/api/IssueApi.java:1262` | `public static Result downvoteWeight(String owner, String projectName, Long number){` |
| GL-controllers_api_IssueApi-069 | `app/controllers/api/IssueApi.java:1282` | `private static ObjectNode changeSharer(JsonNode sharer, Issue issue, String action) {` |
| GL-controllers_api_IssueApi-070 | `app/controllers/api/IssueApi.java:1297` | `private static void changeSharerByUser(String loginId, Issue issue, String action, ObjectNode result` |
| GL-controllers_api_IssueApi-071 | `app/controllers/api/IssueApi.java:1312` | `private static void changeSharerByProject(Long projectId, Issue issue, String action, ObjectNode res` |
| GL-controllers_api_IssueApi-073 | `app/controllers/api/IssueApi.java:1343` | `private static void sendNotification(List<String> users, Issue issue, String action) {` |
| GL-controllers_api_IssueApi-074 | `app/controllers/api/IssueApi.java:1357` | `private static void addSharerChangedNotification(Issue issue, String sharerLoginId, String action) {` |
| GL-controllers_api_IssueApi-075 | `app/controllers/api/IssueApi.java:1363` | `private static boolean noSharer(JsonNode sharers) {` |
| GL-controllers_api_IssueApi-076 | `app/controllers/api/IssueApi.java:1368` | `private static void addSharer(Issue issue, String loginId) {` |
| GL-controllers_api_IssueApi-077 | `app/controllers/api/IssueApi.java:1380` | `private static void removeSharer(Issue issue, String loginId) {` |
| GL-controllers_annotation_GuestProhibit-001 | `app/controllers/annotation/GuestProhibit.java:18` | `@With(GuestProhibitAction.class)` |
| GL-controllers_annotation_GuestProhibit-002 | `app/controllers/annotation/GuestProhibit.java:23` | `boolean displaysFlashMessage() default true;` |
| GL-controllers_annotation_IsAllowed-001 | `app/controllers/annotation/IsAllowed.java:34` | `/**` |
| GL-controllers_annotation_IsAllowed-002 | `app/controllers/annotation/IsAllowed.java:44` | `Operation value();` |
| GL-controllers_annotation_IsAllowed-003 | `app/controllers/annotation/IsAllowed.java:46` | `ResourceType resourceType() default ResourceType.PROJECT;` |
| GL-controllers_annotation_AnonymousCheck-001 | `app/controllers/annotation/AnonymousCheck.java:12` | `/**` |
| GL-controllers_annotation_AnonymousCheck-002 | `app/controllers/annotation/AnonymousCheck.java:20` | `boolean requiresLogin() default false;` |
| GL-controllers_annotation_AnonymousCheck-003 | `app/controllers/annotation/AnonymousCheck.java:22` | `boolean displaysFlashMessage() default false;` |
| GL-controllers_annotation_IsCreatable-001 | `app/controllers/annotation/IsCreatable.java:33` | `/**` |
| GL-controllers_annotation_IsCreatable-002 | `app/controllers/annotation/IsCreatable.java:43` | `ResourceType value();` |
| GL-controllers_annotation_IsOnlyGitAvailable-001 | `app/controllers/annotation/IsOnlyGitAvailable.java:32` | `/**` |
| GL-utils_GitUtil-001 | `app/utils/GitUtil.java:18` | `public class GitUtil {` |
| GL-utils_GitUtil-003 | `app/utils/GitUtil.java:31` | `public synchronized static void commitTextFile(Project project, String branchName, String path, Stri` |
| GL-utils_PasswordReset-001 | `app/utils/PasswordReset.java:33` | `public class PasswordReset {` |
| GL-utils_PasswordReset-002 | `app/utils/PasswordReset.java:35` | `/**` |
| GL-utils_PasswordReset-003 | `app/utils/PasswordReset.java:40` | `/**` |
| GL-utils_PasswordReset-004 | `app/utils/PasswordReset.java:45` | `/**` |
| GL-utils_PasswordReset-005 | `app/utils/PasswordReset.java:51` | `public static String generateResetHash(String loginId) {` |
| GL-utils_PasswordReset-006 | `app/utils/PasswordReset.java:56` | `public static void addHashToResetTable(String userId, String hashString) {` |
| GL-utils_PasswordReset-009 | `app/utils/PasswordReset.java:81` | `private static void removeResetHash(String hashString) {` |
| GL-utils_PasswordReset-011 | `app/utils/PasswordReset.java:98` | `public static boolean resetPassword(String hashString, String newPassword) {` |
| GL-utils_MalformedCredentialsException-001 | `app/utils/MalformedCredentialsException.java:24` | `public class MalformedCredentialsException extends Exception {` |
| GL-utils_MalformedCredentialsException-002 | `app/utils/MalformedCredentialsException.java:27` | `public MalformedCredentialsException() {` |
| GL-utils_MalformedCredentialsException-003 | `app/utils/MalformedCredentialsException.java:32` | `public MalformedCredentialsException(String message) {` |
| GL-utils_MalformedCredentialsException-004 | `app/utils/MalformedCredentialsException.java:37` | `public MalformedCredentialsException(String message, Exception cause) {` |
| GL-utils_MalformedCredentialsException-005 | `app/utils/MalformedCredentialsException.java:42` | `/**` |
| GL-utils_SimpleDiagnostic-001 | `app/utils/SimpleDiagnostic.java:28` | `abstract public class SimpleDiagnostic extends Diagnostic {` |
| GL-utils_SimpleDiagnostic-002 | `app/utils/SimpleDiagnostic.java:30` | `@Override` |
| GL-utils_SimpleDiagnostic-003 | `app/utils/SimpleDiagnostic.java:45` | `abstract public String checkOne();` |
| GL-utils_JodaDateUtil-001 | `app/utils/JodaDateUtil.java:17` | `public class JodaDateUtil {` |
| GL-utils_JodaDateUtil-005 | `app/utils/JodaDateUtil.java:37` | `public static Date today() {` |
| GL-utils_JodaDateUtil-006 | `app/utils/JodaDateUtil.java:42` | `public static Date now() {` |
| GL-utils_JodaDateUtil-007 | `app/utils/JodaDateUtil.java:47` | `public static Duration ago(DateTime time) {` |
| GL-utils_JodaDateUtil-008 | `app/utils/JodaDateUtil.java:52` | `public static Duration ago(Date time) {` |
| GL-utils_JodaDateUtil-009 | `app/utils/JodaDateUtil.java:57` | `public static Duration ago(Long time){` |
| GL-utils_JodaDateUtil-010 | `app/utils/JodaDateUtil.java:62` | `public static Date before(int days){` |
| GL-utils_JodaDateUtil-011 | `app/utils/JodaDateUtil.java:67` | `public static Date beforeByMillis(long millis){` |
| GL-utils_JodaDateUtil-012 | `app/utils/JodaDateUtil.java:72` | `public static String momentFromNow(Long time) {` |
| GL-utils_JodaDateUtil-013 | `app/utils/JodaDateUtil.java:77` | `public static String momentFromNow(Long time, String language) {` |
| GL-utils_JodaDateUtil-014 | `app/utils/JodaDateUtil.java:83` | `public static String momentFromNow(Date time) {` |
| GL-utils_JodaDateUtil-016 | `app/utils/JodaDateUtil.java:94` | `public static int localDaysBetween(Date from, Date to) {` |
| GL-utils_JodaDateUtil-017 | `app/utils/JodaDateUtil.java:99` | `/**` |
| GL-utils_JodaDateUtil-018 | `app/utils/JodaDateUtil.java:113` | `/**` |
| GL-utils_SHA256Util-001 | `app/utils/SHA256Util.java:7` | `public class SHA256Util {` |
| GL-utils_SHA256Util-002 | `app/utils/SHA256Util.java:9` | `public static String hashBasedNow() {` |
| GL-utils_RedirectUtil-001 | `app/utils/RedirectUtil.java:15` | `public class RedirectUtil {` |
| GL-utils_RedirectUtil-002 | `app/utils/RedirectUtil.java:17` | `public static Promise<Result> redirect(@Nonnull Project project) {` |
| GL-utils_diff_match_patch-001 | `app/utils/diff_match_patch.java:40` | `/*` |
| GL-utils_diff_match_patch-002 | `app/utils/diff_match_patch.java:55` | `// Defaults.` |
| GL-utils_diff_match_patch-003 | `app/utils/diff_match_patch.java:63` | `/**` |
| GL-utils_diff_match_patch-004 | `app/utils/diff_match_patch.java:68` | `/**` |
| GL-utils_diff_match_patch-005 | `app/utils/diff_match_patch.java:74` | `/**` |
| GL-utils_diff_match_patch-006 | `app/utils/diff_match_patch.java:79` | `/**` |
| GL-utils_diff_match_patch-007 | `app/utils/diff_match_patch.java:86` | `/**` |
| GL-utils_diff_match_patch-008 | `app/utils/diff_match_patch.java:94` | `/**` |
| GL-utils_diff_match_patch-009 | `app/utils/diff_match_patch.java:100` | `/**` |
| GL-utils_diff_match_patch-010 | `app/utils/diff_match_patch.java:106` | `/**` |
| GL-utils_diff_match_patch-011 | `app/utils/diff_match_patch.java:125` | `//  DIFF FUNCTIONS` |
| GL-utils_diff_match_patch-012 | `app/utils/diff_match_patch.java:140` | `/**` |
| GL-utils_diff_match_patch-013 | `app/utils/diff_match_patch.java:154` | `/**` |
| GL-utils_diff_match_patch-014 | `app/utils/diff_match_patch.java:203` | `/**` |
| GL-utils_diff_match_patch-015 | `app/utils/diff_match_patch.java:336` | `/**` |
| GL-utils_diff_match_patch-016 | `app/utils/diff_match_patch.java:362` | `/**` |
| GL-utils_diff_match_patch-017 | `app/utils/diff_match_patch.java:400` | `/**` |
| GL-utils_diff_match_patch-018 | `app/utils/diff_match_patch.java:420` | `/**` |
| GL-utils_diff_match_patch-019 | `app/utils/diff_match_patch.java:550` | `/**` |
| GL-utils_diff_match_patch-020 | `app/utils/diff_match_patch.java:604` | `/**` |
| GL-utils_diff_match_patch-021 | `app/utils/diff_match_patch.java:660` | `/**` |
| GL-utils_diff_match_patch-022 | `app/utils/diff_match_patch.java:678` | `/**` |
| GL-utils_diff_match_patch-023 | `app/utils/diff_match_patch.java:697` | `/**` |
| GL-utils_diff_match_patch-024 | `app/utils/diff_match_patch.java:718` | `/**` |
| GL-utils_diff_match_patch-025 | `app/utils/diff_match_patch.java:763` | `/**` |
| GL-utils_diff_match_patch-026 | `app/utils/diff_match_patch.java:804` | `/**` |
| GL-utils_diff_match_patch-027 | `app/utils/diff_match_patch.java:880` | `/**` |
| GL-utils_diff_match_patch-028 | `app/utils/diff_match_patch.java:967` | `/**` |
| GL-utils_diff_match_patch-029 | `app/utils/diff_match_patch.java:1012` | `private Pattern BLANKLINEEND` |
| GL-utils_diff_match_patch-030 | `app/utils/diff_match_patch.java:1015` | `private Pattern BLANKLINESTART` |
| GL-utils_diff_match_patch-031 | `app/utils/diff_match_patch.java:1020` | `/**` |
| GL-utils_diff_match_patch-032 | `app/utils/diff_match_patch.java:1129` | `/**` |
| GL-utils_diff_match_patch-033 | `app/utils/diff_match_patch.java:1281` | `/**` |
| GL-utils_diff_match_patch-034 | `app/utils/diff_match_patch.java:1322` | `/**` |
| GL-utils_diff_match_patch-035 | `app/utils/diff_match_patch.java:1356` | `/**` |
| GL-utils_diff_match_patch-036 | `app/utils/diff_match_patch.java:1373` | `/**` |
| GL-utils_diff_match_patch-037 | `app/utils/diff_match_patch.java:1390` | `/**` |
| GL-utils_diff_match_patch-038 | `app/utils/diff_match_patch.java:1422` | `/**` |
| GL-utils_diff_match_patch-039 | `app/utils/diff_match_patch.java:1462` | `/**` |
| GL-utils_diff_match_patch-040 | `app/utils/diff_match_patch.java:1542` | `//  MATCH FUNCTIONS` |
| GL-utils_diff_match_patch-041 | `app/utils/diff_match_patch.java:1573` | `/**` |
| GL-utils_diff_match_patch-042 | `app/utils/diff_match_patch.java:1678` | `/**` |
| GL-utils_diff_match_patch-043 | `app/utils/diff_match_patch.java:1698` | `/**` |
| GL-utils_diff_match_patch-044 | `app/utils/diff_match_patch.java:1719` | `//  PATCH FUNCTIONS` |
| GL-utils_diff_match_patch-045 | `app/utils/diff_match_patch.java:1769` | `/**` |
| GL-utils_diff_match_patch-046 | `app/utils/diff_match_patch.java:1788` | `/**` |
| GL-utils_diff_match_patch-047 | `app/utils/diff_match_patch.java:1802` | `/**` |
| GL-utils_diff_match_patch-048 | `app/utils/diff_match_patch.java:1819` | `/**` |
| GL-utils_diff_match_patch-049 | `app/utils/diff_match_patch.java:1904` | `/**` |
| GL-utils_diff_match_patch-050 | `app/utils/diff_match_patch.java:1928` | `/**` |
| GL-utils_diff_match_patch-051 | `app/utils/diff_match_patch.java:2041` | `/**` |
| GL-utils_diff_match_patch-052 | `app/utils/diff_match_patch.java:2104` | `/**` |
| GL-utils_diff_match_patch-053 | `app/utils/diff_match_patch.java:2210` | `/**` |
| GL-utils_diff_match_patch-054 | `app/utils/diff_match_patch.java:2225` | `/**` |
| GL-utils_diff_match_patch-055 | `app/utils/diff_match_patch.java:2322` | `/**` |
| GL-utils_diff_match_patch-056 | `app/utils/diff_match_patch.java:2379` | `/**` |
| GL-utils_diff_match_patch-057 | `app/utils/diff_match_patch.java:2450` | `/**` |
| GL-utils_HtmlUtil-001 | `app/utils/HtmlUtil.java:8` | `/**` |
| GL-utils_HtmlUtil-002 | `app/utils/HtmlUtil.java:30` | `/**` |
| GL-utils_HtmlUtil-003 | `app/utils/HtmlUtil.java:42` | `public static String boolToCheckedString(boolean bool){` |
| GL-utils_HtmlUtil-004 | `app/utils/HtmlUtil.java:51` | `public static String boolToCheckedString(String bool){` |
| GL-utils_EventConstants-001 | `app/utils/EventConstants.java:26` | `public class EventConstants {` |
| GL-utils_Diagnostic-001 | `app/utils/Diagnostic.java:31` | `abstract public class Diagnostic {` |
| GL-utils_Diagnostic-002 | `app/utils/Diagnostic.java:34` | `private final static List<Diagnostic> diagnostics = new CopyOnWriteArrayList<>();` |
| GL-utils_Diagnostic-003 | `app/utils/Diagnostic.java:37` | `/**` |
| GL-utils_Diagnostic-004 | `app/utils/Diagnostic.java:50` | `@Nonnull` |
| GL-utils_Diagnostic-005 | `app/utils/Diagnostic.java:67` | `@Nonnull` |
| GL-utils_AutoLinkRenderer-001 | `app/utils/AutoLinkRenderer.java:36` | `/**` |
| GL-utils_AutoLinkRenderer-005 | `app/utils/AutoLinkRenderer.java:62` | `private static final Pattern PATH_WITH_ISSUE_PATTERN = Pattern.compile("@?(" + PATH_PATTERN_STR + ")` |
| GL-utils_AutoLinkRenderer-006 | `app/utils/AutoLinkRenderer.java:64` | `private static final Pattern ISSUE_PATTERN = Pattern.compile("#(" + ISSUE_PATTERN_STR + ")");` |
| GL-utils_AutoLinkRenderer-007 | `app/utils/AutoLinkRenderer.java:67` | `private static final Pattern PATH_WITH_SHA_PATTERN = Pattern.compile("(" + PATH_PATTERN_STR + ")@?("` |
| GL-utils_AutoLinkRenderer-008 | `app/utils/AutoLinkRenderer.java:69` | `private static final Pattern SHA_PATTERN = Pattern.compile("@?(" + SHA_PATTERN_STR + ")");` |
| GL-utils_AutoLinkRenderer-009 | `app/utils/AutoLinkRenderer.java:72` | `private static final Pattern LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH_PATTERN = Pattern.compile("@(" + P` |
| GL-utils_AutoLinkRenderer-011 | `app/utils/AutoLinkRenderer.java:78` | `private static final Pattern WORD_PATTERN = Pattern.compile("\\w");` |
| GL-utils_AutoLinkRenderer-012 | `app/utils/AutoLinkRenderer.java:81` | `private static class Link {` |
| GL-utils_AutoLinkRenderer-013 | `app/utils/AutoLinkRenderer.java:116` | `private static interface ToLink {` |
| GL-utils_AutoLinkRenderer-016 | `app/utils/AutoLinkRenderer.java:126` | `public AutoLinkRenderer(String body, Project project) {` |
| GL-utils_AutoLinkRenderer-017 | `app/utils/AutoLinkRenderer.java:132` | `public String render(String lang) {` |
| GL-utils_AutoLinkRenderer-018 | `app/utils/AutoLinkRenderer.java:188` | `private AutoLinkRenderer parse(Pattern pattern, ToLink toLink) {` |
| GL-utils_AutoLinkRenderer-019 | `app/utils/AutoLinkRenderer.java:215` | `/**` |
| GL-utils_AutoLinkRenderer-020 | `app/utils/AutoLinkRenderer.java:243` | `/**` |
| GL-utils_AutoLinkRenderer-021 | `app/utils/AutoLinkRenderer.java:265` | `private Link toValidIssueLink(String prefix, Project project, String issueNumber) {` |
| GL-utils_AutoLinkRenderer-024 | `app/utils/AutoLinkRenderer.java:328` | `private static Link toValidUserLink(String userId, String lang) {` |
| GL-utils_AutoLinkRenderer-025 | `app/utils/AutoLinkRenderer.java:358` | `private static Link toValidProjectLink(String ownerName, String projectName) {` |
| GL-utils_AutoLinkRenderer-026 | `app/utils/AutoLinkRenderer.java:369` | `/**` |
| GL-utils_AutoLinkRenderer-027 | `app/utils/AutoLinkRenderer.java:379` | `/**` |
| GL-utils_ErrorViews-001 | `app/utils/ErrorViews.java:31` | `/**` |
| GL-utils_ErrorViews-002 | `app/utils/ErrorViews.java:36` | `Forbidden {` |
| GL-utils_ErrorViews-003 | `app/utils/ErrorViews.java:76` | `NotFound {` |
| GL-utils_ErrorViews-004 | `app/utils/ErrorViews.java:108` | `RequestTextEntityTooLarge {` |
| GL-utils_ErrorViews-005 | `app/utils/ErrorViews.java:138` | `BadRequest {` |
| GL-utils_ErrorViews-006 | `app/utils/ErrorViews.java:173` | `public abstract Html render();` |
| GL-utils_ErrorViews-007 | `app/utils/ErrorViews.java:176` | `public abstract Html render(String messageKey);` |
| GL-utils_ErrorViews-008 | `app/utils/ErrorViews.java:179` | `public abstract Html render(String messageKey, Project project);` |
| GL-utils_ErrorViews-009 | `app/utils/ErrorViews.java:182` | `public abstract Html render(String messageKey, Organization organization);` |
| GL-utils_ErrorViews-010 | `app/utils/ErrorViews.java:185` | `public abstract Html render(String messageKey, Project project, String target);` |
| GL-utils_ErrorViews-011 | `app/utils/ErrorViews.java:188` | `public abstract Html render(String messageKey, Project project, MenuType menuType);` |
| GL-utils_ErrorViews-012 | `app/utils/ErrorViews.java:191` | `public Html render(String messageKey, String returnUrl) {` |
| GL-utils_YamlUtil-001 | `app/utils/YamlUtil.java:31` | `public class YamlUtil {` |
| GL-utils_YamlUtil-002 | `app/utils/YamlUtil.java:33` | `public static void insertDataFromYaml(String yamlFileName, String[] entityNames) {` |
| GL-utils_MomentUtil-001 | `app/utils/MomentUtil.java:29` | `/**` |
| GL-utils_MomentUtil-002 | `app/utils/MomentUtil.java:39` | `private static ScriptEngine engine = buildEngine();` |
| GL-utils_MomentUtil-004 | `app/utils/MomentUtil.java:45` | `private static ScriptEngine buildEngine() {` |
| GL-utils_MomentUtil-005 | `app/utils/MomentUtil.java:70` | `public static JSInvocable newMoment(Long epoch) {` |
| GL-utils_MomentUtil-006 | `app/utils/MomentUtil.java:75` | `public static JSInvocable newMoment(Long epoch, String language) {` |
| GL-utils_JSInvocable-001 | `app/utils/JSInvocable.java:24` | `/**` |
| GL-utils_JSInvocable-004 | `app/utils/JSInvocable.java:41` | `public JSInvocable(Invocable invocable, Object object) {` |
| GL-utils_JSInvocable-005 | `app/utils/JSInvocable.java:47` | `public String invoke(String method, Object... args) {` |
| GL-utils_LineEnding-001 | `app/utils/LineEnding.java:12` | `public class LineEnding {` |
| GL-utils_LineEnding-003 | `app/utils/LineEnding.java:16` | `public enum EndingType {` |
| GL-utils_LineEnding-004 | `app/utils/LineEnding.java:26` | `public static String changeLineEnding(String contents, String to){` |
| GL-utils_LineEnding-005 | `app/utils/LineEnding.java:34` | `public static String changeLineEnding(String contents, EndingType to){` |
| GL-utils_LineEnding-006 | `app/utils/LineEnding.java:49` | `public static String addEOL(String contents){` |
| GL-utils_LineEnding-007 | `app/utils/LineEnding.java:63` | `public static EndingType findLineEnding(String contents){` |
| GL-utils_DiffUtil-001 | `app/utils/DiffUtil.java:16` | `public class DiffUtil {` |
| GL-utils_DiffUtil-007 | `app/utils/DiffUtil.java:113` | `private static String addHeadOfDiff(Diff diff) {` |
| GL-utils_DiffUtil-008 | `app/utils/DiffUtil.java:118` | `private static String addTailOfDiff(Diff diff) {` |
| GL-utils_DiffUtil-009 | `app/utils/DiffUtil.java:123` | `private static String addAllDiff(Diff diff) {` |
| GL-utils_DiffUtil-010 | `app/utils/DiffUtil.java:128` | `private static String addEllipsis() {` |
| GL-utils_DiffUtil-011 | `app/utils/DiffUtil.java:136` | `private static String addDiffStyle(Diff diff, String style) {` |
| GL-utils_DiffUtil-012 | `app/utils/DiffUtil.java:143` | `private static String addDiffText(Diff diff, String text) {` |
| GL-utils_DiffUtil-013 | `app/utils/DiffUtil.java:150` | `private static String addEllipsisText() {` |
| GL-utils_SecurityManager-001 | `app/utils/SecurityManager.java:4` | `/**` |
| GL-utils_AccessControl-001 | `app/utils/AccessControl.java:22` | `public class AccessControl {` |
| GL-utils_AccessControl-003 | `app/utils/AccessControl.java:27` | `/**` |
| GL-utils_AccessControl-004 | `app/utils/AccessControl.java:40` | `/**` |
| GL-utils_AccessControl-005 | `app/utils/AccessControl.java:88` | `/**` |
| GL-utils_AccessControl-010 | `app/utils/AccessControl.java:314` | `/**` |
| GL-utils_AccessControl-011 | `app/utils/AccessControl.java:347` | `public static void onStart() {` |
| GL-utils_AccessControl-012 | `app/utils/AccessControl.java:353` | `/**` |
| GL-utils_AccessControl-014 | `app/utils/AccessControl.java:400` | `/**` |
| GL-utils_PlayServletResponse-001 | `app/utils/PlayServletResponse.java:32` | `public class PlayServletResponse implements HttpServletResponse {` |
| GL-utils_PlayServletResponse-010 | `app/utils/PlayServletResponse.java:52` | `/**` |
| GL-utils_PlayServletResponse-012 | `app/utils/PlayServletResponse.java:73` | `class ChunkedOutputStream extends ServletOutputStream {` |
| GL-utils_PlayServletResponse-013 | `app/utils/PlayServletResponse.java:128` | `public PlayServletResponse(Response response) throws IOException {` |
| GL-utils_PlayServletResponse-014 | `app/utils/PlayServletResponse.java:139` | `@Override` |
| GL-utils_PlayServletResponse-015 | `app/utils/PlayServletResponse.java:145` | `@Override` |
| GL-utils_PlayServletResponse-016 | `app/utils/PlayServletResponse.java:151` | `@Override` |
| GL-utils_PlayServletResponse-017 | `app/utils/PlayServletResponse.java:162` | `@Override` |
| GL-utils_PlayServletResponse-018 | `app/utils/PlayServletResponse.java:168` | `@Override` |
| GL-utils_PlayServletResponse-020 | `app/utils/PlayServletResponse.java:179` | `@Override` |
| GL-utils_PlayServletResponse-021 | `app/utils/PlayServletResponse.java:185` | `@Override` |
| GL-utils_PlayServletResponse-022 | `app/utils/PlayServletResponse.java:191` | `@Override` |
| GL-utils_PlayServletResponse-023 | `app/utils/PlayServletResponse.java:197` | `@Override` |
| GL-utils_PlayServletResponse-024 | `app/utils/PlayServletResponse.java:203` | `@Override` |
| GL-utils_PlayServletResponse-025 | `app/utils/PlayServletResponse.java:215` | `@Override` |
| GL-utils_PlayServletResponse-026 | `app/utils/PlayServletResponse.java:221` | `@Override` |
| GL-utils_PlayServletResponse-027 | `app/utils/PlayServletResponse.java:227` | `@Override` |
| GL-utils_PlayServletResponse-029 | `app/utils/PlayServletResponse.java:238` | `@Override` |
| GL-utils_PlayServletResponse-030 | `app/utils/PlayServletResponse.java:245` | `@Override` |
| GL-utils_PlayServletResponse-031 | `app/utils/PlayServletResponse.java:251` | `@Override` |
| GL-utils_PlayServletResponse-032 | `app/utils/PlayServletResponse.java:257` | `@Override` |
| GL-utils_PlayServletResponse-033 | `app/utils/PlayServletResponse.java:263` | `@Override` |
| GL-utils_PlayServletResponse-034 | `app/utils/PlayServletResponse.java:276` | `@Override` |
| GL-utils_PlayServletResponse-035 | `app/utils/PlayServletResponse.java:282` | `@Override` |
| GL-utils_PlayServletResponse-036 | `app/utils/PlayServletResponse.java:288` | `@Override` |
| GL-utils_PlayServletResponse-037 | `app/utils/PlayServletResponse.java:294` | `/**` |
| GL-utils_PlayServletResponse-038 | `app/utils/PlayServletResponse.java:304` | `@Override` |
| GL-utils_PlayServletResponse-039 | `app/utils/PlayServletResponse.java:310` | `/**` |
| GL-utils_PlayServletResponse-040 | `app/utils/PlayServletResponse.java:320` | `@Override` |
| GL-utils_PlayServletResponse-041 | `app/utils/PlayServletResponse.java:332` | `@Override` |
| GL-utils_PlayServletResponse-042 | `app/utils/PlayServletResponse.java:338` | `@Override` |
| GL-utils_PlayServletResponse-043 | `app/utils/PlayServletResponse.java:344` | `@Override` |
| GL-utils_PlayServletResponse-044 | `app/utils/PlayServletResponse.java:350` | `@Override` |
| GL-utils_PlayServletResponse-045 | `app/utils/PlayServletResponse.java:357` | `@Override` |
| GL-utils_PlayServletResponse-046 | `app/utils/PlayServletResponse.java:373` | `@Override` |
| GL-utils_PlayServletResponse-047 | `app/utils/PlayServletResponse.java:381` | `@Override` |
| GL-utils_PlayServletResponse-048 | `app/utils/PlayServletResponse.java:387` | `@Override` |
| GL-utils_PlayServletResponse-049 | `app/utils/PlayServletResponse.java:401` | `@Override` |
| GL-utils_PlayServletResponse-050 | `app/utils/PlayServletResponse.java:407` | `@Override` |
| GL-utils_PlayServletResponse-051 | `app/utils/PlayServletResponse.java:413` | `/**` |
| GL-utils_PlayServletResponse-052 | `app/utils/PlayServletResponse.java:423` | `/**` |
| GL-utils_ValidationResult-001 | `app/utils/ValidationResult.java:26` | `public class ValidationResult {` |
| GL-utils_ValidationResult-004 | `app/utils/ValidationResult.java:33` | `public ValidationResult(Result result, boolean hasError) {` |
| GL-utils_ValidationResult-005 | `app/utils/ValidationResult.java:39` | `public boolean hasError(){` |
| GL-utils_PlayServletContext-001 | `app/utils/PlayServletContext.java:31` | `public class PlayServletContext implements ServletContext {` |
| GL-utils_PlayServletContext-002 | `app/utils/PlayServletContext.java:34` | `@Override` |
| GL-utils_PlayServletContext-003 | `app/utils/PlayServletContext.java:40` | `@Override` |
| GL-utils_PlayServletContext-004 | `app/utils/PlayServletContext.java:46` | `@Override` |
| GL-utils_PlayServletContext-005 | `app/utils/PlayServletContext.java:52` | `@Override` |
| GL-utils_PlayServletContext-006 | `app/utils/PlayServletContext.java:58` | `@Override` |
| GL-utils_PlayServletContext-007 | `app/utils/PlayServletContext.java:64` | `@Override` |
| GL-utils_PlayServletContext-008 | `app/utils/PlayServletContext.java:70` | `@Override` |
| GL-utils_PlayServletContext-009 | `app/utils/PlayServletContext.java:76` | `@Override` |
| GL-utils_PlayServletContext-010 | `app/utils/PlayServletContext.java:82` | `@Override` |
| GL-utils_PlayServletContext-011 | `app/utils/PlayServletContext.java:89` | `@Override` |
| GL-utils_PlayServletContext-012 | `app/utils/PlayServletContext.java:95` | `@Override` |
| GL-utils_PlayServletContext-013 | `app/utils/PlayServletContext.java:101` | `@Override` |
| GL-utils_PlayServletContext-014 | `app/utils/PlayServletContext.java:107` | `@Override` |
| GL-utils_PlayServletContext-015 | `app/utils/PlayServletContext.java:113` | `@Override` |
| GL-utils_PlayServletContext-016 | `app/utils/PlayServletContext.java:119` | `@Override` |
| GL-utils_PlayServletContext-017 | `app/utils/PlayServletContext.java:125` | `@Override` |
| GL-utils_PlayServletContext-018 | `app/utils/PlayServletContext.java:131` | `@Override` |
| GL-utils_PlayServletContext-019 | `app/utils/PlayServletContext.java:137` | `@Override` |
| GL-utils_PlayServletContext-020 | `app/utils/PlayServletContext.java:143` | `@Override` |
| GL-utils_PlayServletContext-021 | `app/utils/PlayServletContext.java:149` | `@Override` |
| GL-utils_PlayServletContext-022 | `app/utils/PlayServletContext.java:155` | `@Override` |
| GL-utils_PlayServletContext-023 | `app/utils/PlayServletContext.java:161` | `@Override` |
| GL-utils_PlayServletContext-024 | `app/utils/PlayServletContext.java:167` | `@Override` |
| GL-utils_PlayServletContext-025 | `app/utils/PlayServletContext.java:173` | `@Override` |
| GL-utils_PlayServletContext-026 | `app/utils/PlayServletContext.java:179` | `@Override` |
| GL-utils_PlayServletContext-027 | `app/utils/PlayServletContext.java:185` | `@Override` |
| GL-utils_PlayServletContext-028 | `app/utils/PlayServletContext.java:191` | `@Override` |
| GL-utils_PlayServletContext-029 | `app/utils/PlayServletContext.java:197` | `@Override` |
| GL-utils_PlayServletContext-030 | `app/utils/PlayServletContext.java:203` | `@Override` |
| GL-utils_PlayServletContext-031 | `app/utils/PlayServletContext.java:209` | `@Override` |
| GL-utils_PlayServletContext-032 | `app/utils/PlayServletContext.java:215` | `@Override` |
| GL-utils_PlayServletContext-033 | `app/utils/PlayServletContext.java:222` | `@Override` |
| GL-utils_PlayServletContext-034 | `app/utils/PlayServletContext.java:228` | `@Override` |
| GL-utils_PlayServletContext-035 | `app/utils/PlayServletContext.java:234` | `@Override` |
| GL-utils_PlayServletContext-036 | `app/utils/PlayServletContext.java:240` | `@Override` |
| GL-utils_PlayServletContext-037 | `app/utils/PlayServletContext.java:246` | `@Override` |
| GL-utils_PlayServletContext-038 | `app/utils/PlayServletContext.java:252` | `@Override` |
| GL-utils_PlayServletContext-039 | `app/utils/PlayServletContext.java:258` | `/**` |
| GL-utils_PlayServletContext-040 | `app/utils/PlayServletContext.java:268` | `@Override` |
| GL-utils_PlayServletContext-041 | `app/utils/PlayServletContext.java:274` | `/**` |
| GL-utils_PlayServletContext-042 | `app/utils/PlayServletContext.java:284` | `@Override` |
| GL-utils_PlayServletContext-043 | `app/utils/PlayServletContext.java:290` | `@Override` |
| GL-utils_PlayServletContext-044 | `app/utils/PlayServletContext.java:296` | `/**` |
| GL-utils_PlayServletContext-045 | `app/utils/PlayServletContext.java:306` | `@Override` |
| GL-utils_PlayServletContext-046 | `app/utils/PlayServletContext.java:312` | `@Override` |
| GL-utils_PlayServletContext-047 | `app/utils/PlayServletContext.java:318` | `/**` |
| GL-utils_PlayServletContext-048 | `app/utils/PlayServletContext.java:328` | `@Override` |
| GL-utils_PlayServletContext-049 | `app/utils/PlayServletContext.java:334` | `@Override` |
| GL-utils_PlayServletContext-050 | `app/utils/PlayServletContext.java:340` | `@Override` |
| GL-utils_PlayServletContext-051 | `app/utils/PlayServletContext.java:346` | `@Override` |
| GL-utils_PlayServletContext-052 | `app/utils/PlayServletContext.java:352` | `@Override` |
| GL-utils_MimeType-001 | `app/utils/MimeType.java:23` | `public class MimeType {` |
| GL-utils_ZipUtil-001 | `app/utils/ZipUtil.java:8` | `/**` |
| GL-utils_ZipUtil-002 | `app/utils/ZipUtil.java:15` | `public static byte[] compress(String text) {` |
| GL-utils_ZipUtil-003 | `app/utils/ZipUtil.java:28` | `public static String decompress(byte[] bytes) {` |
| GL-utils_FastHttpDateFormat-001 | `app/utils/FastHttpDateFormat.java:28` | `/**` |
| GL-utils_FastHttpDateFormat-002 | `app/utils/FastHttpDateFormat.java:37` | `// -------------------------------------------------------------- Variables` |
| GL-utils_FastHttpDateFormat-003 | `app/utils/FastHttpDateFormat.java:45` | `/**` |
| GL-utils_FastHttpDateFormat-004 | `app/utils/FastHttpDateFormat.java:52` | `private static final SimpleDateFormat format =` |
| GL-utils_FastHttpDateFormat-005 | `app/utils/FastHttpDateFormat.java:57` | `/**` |
| GL-utils_FastHttpDateFormat-007 | `app/utils/FastHttpDateFormat.java:86` | `/**` |
| GL-utils_FastHttpDateFormat-008 | `app/utils/FastHttpDateFormat.java:93` | `/**` |
| GL-utils_FastHttpDateFormat-009 | `app/utils/FastHttpDateFormat.java:100` | `/**` |
| GL-utils_FastHttpDateFormat-010 | `app/utils/FastHttpDateFormat.java:108` | `/**` |
| GL-utils_FastHttpDateFormat-011 | `app/utils/FastHttpDateFormat.java:116` | `// --------------------------------------------------------- Public Methods` |
| GL-utils_FastHttpDateFormat-012 | `app/utils/FastHttpDateFormat.java:139` | `/**` |
| GL-utils_FastHttpDateFormat-013 | `app/utils/FastHttpDateFormat.java:167` | `/**` |
| GL-utils_FastHttpDateFormat-014 | `app/utils/FastHttpDateFormat.java:195` | `/**` |
| GL-utils_FastHttpDateFormat-015 | `app/utils/FastHttpDateFormat.java:216` | `/**` |
| GL-utils_FastHttpDateFormat-016 | `app/utils/FastHttpDateFormat.java:231` | `/**` |
| GL-utils_PlayServletRequest-001 | `app/utils/PlayServletRequest.java:40` | `public class PlayServletRequest implements HttpServletRequest {` |
| GL-utils_PlayServletRequest-004 | `app/utils/PlayServletRequest.java:47` | `Map<String, Object> attributes = new HashMap<>();` |
| GL-utils_PlayServletRequest-007 | `app/utils/PlayServletRequest.java:54` | `public PlayServletRequest(Request request, String authenticatedUsername, String pathInfo) {` |
| GL-utils_PlayServletRequest-008 | `app/utils/PlayServletRequest.java:62` | `/**` |
| GL-utils_PlayServletRequest-010 | `app/utils/PlayServletRequest.java:76` | `@Override` |
| GL-utils_PlayServletRequest-011 | `app/utils/PlayServletRequest.java:82` | `@Override` |
| GL-utils_PlayServletRequest-012 | `app/utils/PlayServletRequest.java:88` | `@Override` |
| GL-utils_PlayServletRequest-013 | `app/utils/PlayServletRequest.java:94` | `@Override` |
| GL-utils_PlayServletRequest-014 | `app/utils/PlayServletRequest.java:101` | `@Override` |
| GL-utils_PlayServletRequest-016 | `app/utils/PlayServletRequest.java:124` | `@Override` |
| GL-utils_PlayServletRequest-017 | `app/utils/PlayServletRequest.java:130` | `@Override` |
| GL-utils_PlayServletRequest-018 | `app/utils/PlayServletRequest.java:136` | `@Override` |
| GL-utils_PlayServletRequest-019 | `app/utils/PlayServletRequest.java:200` | `@Override` |
| GL-utils_PlayServletRequest-020 | `app/utils/PlayServletRequest.java:206` | `@Override` |
| GL-utils_PlayServletRequest-021 | `app/utils/PlayServletRequest.java:212` | `@Override` |
| GL-utils_PlayServletRequest-022 | `app/utils/PlayServletRequest.java:218` | `@Override` |
| GL-utils_PlayServletRequest-023 | `app/utils/PlayServletRequest.java:229` | `@Override` |
| GL-utils_PlayServletRequest-024 | `app/utils/PlayServletRequest.java:239` | `@Override` |
| GL-utils_PlayServletRequest-025 | `app/utils/PlayServletRequest.java:252` | `@Override` |
| GL-utils_PlayServletRequest-026 | `app/utils/PlayServletRequest.java:258` | `@Override` |
| GL-utils_PlayServletRequest-027 | `app/utils/PlayServletRequest.java:264` | `@Override` |
| GL-utils_PlayServletRequest-028 | `app/utils/PlayServletRequest.java:270` | `@Override` |
| GL-utils_PlayServletRequest-029 | `app/utils/PlayServletRequest.java:276` | `@Override` |
| GL-utils_PlayServletRequest-030 | `app/utils/PlayServletRequest.java:282` | `/**` |
| GL-utils_PlayServletRequest-031 | `app/utils/PlayServletRequest.java:292` | `@Override` |
| GL-utils_PlayServletRequest-032 | `app/utils/PlayServletRequest.java:298` | `@Override` |
| GL-utils_PlayServletRequest-033 | `app/utils/PlayServletRequest.java:305` | `@Override` |
| GL-utils_PlayServletRequest-034 | `app/utils/PlayServletRequest.java:311` | `@Override` |
| GL-utils_PlayServletRequest-035 | `app/utils/PlayServletRequest.java:317` | `@Override` |
| GL-utils_PlayServletRequest-036 | `app/utils/PlayServletRequest.java:334` | `@Override` |
| GL-utils_PlayServletRequest-037 | `app/utils/PlayServletRequest.java:340` | `@Override` |
| GL-utils_PlayServletRequest-038 | `app/utils/PlayServletRequest.java:354` | `@Override` |
| GL-utils_PlayServletRequest-039 | `app/utils/PlayServletRequest.java:360` | `@Override` |
| GL-utils_PlayServletRequest-040 | `app/utils/PlayServletRequest.java:366` | `@Override` |
| GL-utils_PlayServletRequest-041 | `app/utils/PlayServletRequest.java:372` | `@Override` |
| GL-utils_PlayServletRequest-042 | `app/utils/PlayServletRequest.java:379` | `@Override` |
| GL-utils_PlayServletRequest-043 | `app/utils/PlayServletRequest.java:385` | `@Override` |
| GL-utils_PlayServletRequest-044 | `app/utils/PlayServletRequest.java:391` | `@Override` |
| GL-utils_PlayServletRequest-045 | `app/utils/PlayServletRequest.java:397` | `@Override` |
| GL-utils_PlayServletRequest-046 | `app/utils/PlayServletRequest.java:403` | `@Override` |
| GL-utils_PlayServletRequest-047 | `app/utils/PlayServletRequest.java:410` | `@Override` |
| GL-utils_PlayServletRequest-048 | `app/utils/PlayServletRequest.java:416` | `@Override` |
| GL-utils_PlayServletRequest-049 | `app/utils/PlayServletRequest.java:423` | `@Override` |
| GL-utils_PlayServletRequest-050 | `app/utils/PlayServletRequest.java:430` | `@Override` |
| GL-utils_PlayServletRequest-051 | `app/utils/PlayServletRequest.java:436` | `@Override` |
| GL-utils_PlayServletRequest-052 | `app/utils/PlayServletRequest.java:448` | `@Override` |
| GL-utils_PlayServletRequest-053 | `app/utils/PlayServletRequest.java:454` | `@Override` |
| GL-utils_PlayServletRequest-054 | `app/utils/PlayServletRequest.java:460` | `@Override` |
| GL-utils_PlayServletRequest-055 | `app/utils/PlayServletRequest.java:472` | `// same as org.apache.catalina.connector.Request.getHeaders` |
| GL-utils_PlayServletRequest-056 | `app/utils/PlayServletRequest.java:484` | `@Override` |
| GL-utils_PlayServletRequest-057 | `app/utils/PlayServletRequest.java:490` | `@Override` |
| GL-utils_PlayServletRequest-058 | `app/utils/PlayServletRequest.java:496` | `@Override` |
| GL-utils_PlayServletRequest-059 | `app/utils/PlayServletRequest.java:502` | `@Override` |
| GL-utils_PlayServletRequest-060 | `app/utils/PlayServletRequest.java:508` | `@Override` |
| GL-utils_PlayServletRequest-061 | `app/utils/PlayServletRequest.java:515` | `@Override` |
| GL-utils_PlayServletRequest-062 | `app/utils/PlayServletRequest.java:528` | `@Override` |
| GL-utils_PlayServletRequest-063 | `app/utils/PlayServletRequest.java:534` | `@Override` |
| GL-utils_PlayServletRequest-064 | `app/utils/PlayServletRequest.java:540` | `@Override` |
| GL-utils_PlayServletRequest-065 | `app/utils/PlayServletRequest.java:546` | `@Override` |
| GL-utils_PlayServletRequest-066 | `app/utils/PlayServletRequest.java:552` | `@Override` |
| GL-utils_PlayServletRequest-067 | `app/utils/PlayServletRequest.java:559` | `@Override` |
| GL-utils_PlayServletRequest-068 | `app/utils/PlayServletRequest.java:565` | `@Override` |
| GL-utils_PlayServletRequest-069 | `app/utils/PlayServletRequest.java:571` | `@Override` |
| GL-utils_PlayServletRequest-070 | `app/utils/PlayServletRequest.java:584` | `@Override` |
| GL-utils_PlayServletRequest-071 | `app/utils/PlayServletRequest.java:590` | `@Override` |
| GL-utils_PlayServletRequest-072 | `app/utils/PlayServletRequest.java:596` | `/**` |
| GL-utils_PlayServletRequest-073 | `app/utils/PlayServletRequest.java:606` | `@Override` |
| GL-utils_PlayServletRequest-074 | `app/utils/PlayServletRequest.java:612` | `@Override` |
| GL-utils_PlayServletRequest-075 | `app/utils/PlayServletRequest.java:618` | `@Override` |
| GL-utils_PlayServletRequest-076 | `app/utils/PlayServletRequest.java:624` | `@Override` |
| GL-utils_PlayServletRequest-077 | `app/utils/PlayServletRequest.java:630` | `public <T extends javax.servlet.http.HttpUpgradeHandler> T upgrade(java.lang.Class<T> httpUpgradeHan` |
| GL-utils_PlayServletRequest-078 | `app/utils/PlayServletRequest.java:637` | `public String changeSessionId() {` |
| GL-utils_Url-001 | `app/utils/Url.java:29` | `public class Url {` |
| GL-utils_Url-002 | `app/utils/Url.java:32` | `/**` |
| GL-utils_Url-003 | `app/utils/Url.java:44` | `public static String createWithContext(List<String> pathSegments) {` |
| GL-utils_Url-004 | `app/utils/Url.java:50` | `/**` |
| GL-utils_Url-005 | `app/utils/Url.java:66` | `/**` |
| GL-utils_Url-006 | `app/utils/Url.java:84` | `/**` |
| GL-utils_Url-007 | `app/utils/Url.java:96` | `/**` |
| GL-utils_Url-008 | `app/utils/Url.java:112` | `/**` |
| GL-utils_Url-009 | `app/utils/Url.java:132` | `private static String join(List<String> pathSegments) {` |
| GL-utils_Url-010 | `app/utils/Url.java:137` | `public static String removeFragment(String url) {` |
| GL-utils_AccessLogger-001 | `app/utils/AccessLogger.java:35` | `public class AccessLogger {` |
| GL-utils_AccessLogger-002 | `app/utils/AccessLogger.java:38` | `/**` |
| GL-utils_AccessLogger-003 | `app/utils/AccessLogger.java:53` | `/**` |
| GL-utils_AccessLogger-004 | `app/utils/AccessLogger.java:68` | `/**` |
| GL-utils_AccessLogger-005 | `app/utils/AccessLogger.java:106` | `/**` |
| GL-utils_AccessLogger-006 | `app/utils/AccessLogger.java:130` | `/**` |
| GL-utils_PathVariable-001 | `app/utils/PathVariable.java:15` | `public class PathVariable {` |
| GL-utils_PathVariable-005 | `app/utils/PathVariable.java:23` | `private Map<String, String> pathVariable = new HashMap<>();` |
| GL-utils_PathVariable-007 | `app/utils/PathVariable.java:28` | `public PathVariable(String url) {` |
| GL-utils_PathVariable-008 | `app/utils/PathVariable.java:41` | `/**` |
| GL-utils_PathVariable-010 | `app/utils/PathVariable.java:56` | `private void decomposeToPathVariable(String refinedUrl) {` |
| GL-utils_Timestamp-001 | `app/utils/Timestamp.java:11` | `public class Timestamp {` |
| GL-utils_Timestamp-003 | `app/utils/Timestamp.java:17` | `public Timestamp(String title) {` |
| GL-utils_Timestamp-004 | `app/utils/Timestamp.java:23` | `public void logElapsedTime(String message) {` |
| GL-utils_Config-001 | `app/utils/Config.java:21` | `public class Config {` |
| GL-utils_Config-003 | `app/utils/Config.java:25` | `private static final String YONA_DATA = "yona.data"; //property from java -Dyona.data option string` |
| GL-utils_Config-009 | `app/utils/Config.java:78` | `/**` |
| GL-utils_Config-010 | `app/utils/Config.java:98` | `/**` |
| GL-utils_Config-017 | `app/utils/Config.java:201` | `/**` |
| GL-utils_Config-018 | `app/utils/Config.java:223` | `/**` |
| GL-utils_Config-019 | `app/utils/Config.java:235` | `/**` |
| GL-utils_Config-020 | `app/utils/Config.java:262` | `/**` |
| GL-utils_Config-026 | `app/utils/Config.java:308` | `public static boolean displayPrivateRepositories() {` |
| GL-utils_ChunkedOutputStream-001 | `app/utils/ChunkedOutputStream.java:16` | `//` |
| GL-utils_ChunkedOutputStream-003 | `app/utils/ChunkedOutputStream.java:24` | `/**` |
| GL-utils_ChunkedOutputStream-004 | `app/utils/ChunkedOutputStream.java:30` | `/**` |
| GL-utils_ChunkedOutputStream-005 | `app/utils/ChunkedOutputStream.java:39` | `public ChunkedOutputStream(Chunks.Out<byte[]> out, int size) {` |
| GL-utils_ChunkedOutputStream-006 | `app/utils/ChunkedOutputStream.java:49` | `/**` |
| GL-utils_ChunkedOutputStream-007 | `app/utils/ChunkedOutputStream.java:64` | `public void write(byte b[]) throws IOException {` |
| GL-utils_ChunkedOutputStream-008 | `app/utils/ChunkedOutputStream.java:69` | `/**` |
| GL-utils_ChunkedOutputStream-009 | `app/utils/ChunkedOutputStream.java:103` | `private void flushBuffer() throws IOException {` |
| GL-utils_ChunkedOutputStream-010 | `app/utils/ChunkedOutputStream.java:110` | `@Override` |
| GL-utils_ChunkedOutputStream-011 | `app/utils/ChunkedOutputStream.java:119` | `private void chunkOut() {` |
| GL-utils_PullRequestCommit-001 | `app/utils/PullRequestCommit.java:24` | `public class PullRequestCommit {` |
| GL-utils_PullRequestCommit-006 | `app/utils/PullRequestCommit.java:39` | `public PullRequestCommit(String url) {` |
| GL-utils_MD5Util-001 | `app/utils/MD5Util.java:27` | `/**` |
| GL-utils_MD5Util-002 | `app/utils/MD5Util.java:32` | `public static String hex(byte[] array) {` |
| GL-utils_MD5Util-003 | `app/utils/MD5Util.java:41` | `public static String md5Hex (String message) {` |
| GL-utils_LogoUtil-001 | `app/utils/LogoUtil.java:26` | `public class LogoUtil {` |
| GL-utils_LogoUtil-002 | `app/utils/LogoUtil.java:28` | `public static final int LOGO_FILE_LIMIT_SIZE = 1024*1000*5; //5M` |
| GL-utils_CacheStore-001 | `app/utils/CacheStore.java:14` | `/**` |
| GL-utils_CacheStore-006 | `app/utils/CacheStore.java:36` | `public static Cache<Long, User> yonaUsers = CacheBuilder.newBuilder()` |
| GL-utils_CacheStore-008 | `app/utils/CacheStore.java:47` | `public static void refreshProjectMap(){` |
| GL-utils_BasicAuthAction-001 | `app/utils/BasicAuthAction.java:39` | `public class BasicAuthAction extends Action<Object> {` |
| GL-utils_BasicAuthAction-003 | `app/utils/BasicAuthAction.java:44` | `public static Result unauthorized(Response response) {` |
| GL-utils_BasicAuthAction-004 | `app/utils/BasicAuthAction.java:56` | `public static User parseCredentials(String credentials) throws MalformedCredentialsException, Unsupp` |
| GL-utils_BasicAuthAction-005 | `app/utils/BasicAuthAction.java:93` | `// !! Important !! For ldap, intentionally, user email is used for ldap authentication` |
| GL-utils_BasicAuthAction-006 | `app/utils/BasicAuthAction.java:119` | `@Override` |
| GL-utils_GravatarUtil-001 | `app/utils/GravatarUtil.java:16` | `public class GravatarUtil {` |
| GL-utils_PlayServletSession-001 | `app/utils/PlayServletSession.java:29` | `public class PlayServletSession implements HttpSession {` |
| GL-utils_PlayServletSession-003 | `app/utils/PlayServletSession.java:35` | `public PlayServletSession(ServletContext context) {` |
| GL-utils_PlayServletSession-004 | `app/utils/PlayServletSession.java:40` | `@Override` |
| GL-utils_PlayServletSession-005 | `app/utils/PlayServletSession.java:46` | `@Override` |
| GL-utils_PlayServletSession-006 | `app/utils/PlayServletSession.java:52` | `@Override` |
| GL-utils_PlayServletSession-007 | `app/utils/PlayServletSession.java:58` | `@Override` |
| GL-utils_PlayServletSession-008 | `app/utils/PlayServletSession.java:64` | `@Override` |
| GL-utils_PlayServletSession-009 | `app/utils/PlayServletSession.java:70` | `@Override` |
| GL-utils_PlayServletSession-010 | `app/utils/PlayServletSession.java:76` | `@Override` |
| GL-utils_PlayServletSession-011 | `app/utils/PlayServletSession.java:82` | `/**` |
| GL-utils_PlayServletSession-012 | `app/utils/PlayServletSession.java:92` | `/**` |
| GL-utils_PlayServletSession-013 | `app/utils/PlayServletSession.java:102` | `/**` |
| GL-utils_PlayServletSession-014 | `app/utils/PlayServletSession.java:112` | `@Override` |
| GL-utils_PlayServletSession-015 | `app/utils/PlayServletSession.java:118` | `@Override` |
| GL-utils_PlayServletSession-016 | `app/utils/PlayServletSession.java:124` | `/**` |
| GL-utils_PlayServletSession-017 | `app/utils/PlayServletSession.java:134` | `@Override` |
| GL-utils_PlayServletSession-018 | `app/utils/PlayServletSession.java:140` | `/**` |
| GL-utils_PlayServletSession-019 | `app/utils/PlayServletSession.java:150` | `@Override` |
| GL-utils_PlayServletSession-020 | `app/utils/PlayServletSession.java:156` | `@Override` |
| GL-utils_Markdown-001 | `app/utils/Markdown.java:37` | `public class Markdown {` |
| GL-utils_Markdown-005 | `app/utils/Markdown.java:46` | `private static ScriptEngine engine = buildEngine();` |
| GL-utils_Markdown-006 | `app/utils/Markdown.java:48` | `private static PolicyFactory sanitizerPolicy = Sanitizers.FORMATTING` |
| GL-utils_Markdown-007 | `app/utils/Markdown.java:66` | `private static ScriptEngine buildEngine() {` |
| GL-utils_Markdown-008 | `app/utils/Markdown.java:96` | `private static String removeJavascriptInHref(String source) {` |
| GL-utils_Markdown-014 | `app/utils/Markdown.java:287` | `/**` |
| GL-utils_Markdown-015 | `app/utils/Markdown.java:326` | `public static String render(@Nonnull String source) {` |
| GL-utils_Markdown-016 | `app/utils/Markdown.java:344` | `public static String render(@Nonnull String source, Project project, boolean breaks) {` |
| GL-utils_Markdown-020 | `app/utils/Markdown.java:366` | `public static String renderFileInCodeBrowser(@Nonnull String source, Project project) {` |
| GL-utils_Markdown-022 | `app/utils/Markdown.java:380` | `private static String replaceImageLinkPath(Project project, String text){` |
| GL-utils_Markdown-023 | `app/utils/Markdown.java:390` | `private static String replaceContentsLinkToCodeBrowerPath(Project project, String text){` |
| GL-utils_SiteManagerAuthAction-001 | `app/utils/SiteManagerAuthAction.java:30` | `/**` |
| GL-utils_SiteManagerAuthAction-002 | `app/utils/SiteManagerAuthAction.java:35` | `@Override` |
| GL-utils_HttpUtil-001 | `app/utils/HttpUtil.java:32` | `public class HttpUtil {` |
| GL-utils_HttpUtil-002 | `app/utils/HttpUtil.java:34` | `/**` |
| GL-utils_HttpUtil-003 | `app/utils/HttpUtil.java:58` | `/**` |
| GL-utils_HttpUtil-004 | `app/utils/HttpUtil.java:76` | `/**` |
| GL-utils_HttpUtil-005 | `app/utils/HttpUtil.java:96` | `/**` |
| GL-utils_HttpUtil-006 | `app/utils/HttpUtil.java:109` | `/**` |
| GL-utils_HttpUtil-007 | `app/utils/HttpUtil.java:131` | `/**` |
| GL-utils_HttpUtil-008 | `app/utils/HttpUtil.java:168` | `/**` |
| GL-utils_HttpUtil-009 | `app/utils/HttpUtil.java:184` | `/**` |
| GL-utils_HttpUtil-010 | `app/utils/HttpUtil.java:195` | `/**` |
| GL-utils_HttpUtil-011 | `app/utils/HttpUtil.java:214` | `public static String decodeUrlString(String str) {` |
| GL-utils_HttpUtil-012 | `app/utils/HttpUtil.java:226` | `public static String encodeUrlString(String str) {` |
| GL-utils_HttpUtil-013 | `app/utils/HttpUtil.java:238` | `// It is made for path which contains UTF8 chars` |
| GL-utils_RouteUtil-001 | `app/utils/RouteUtil.java:24` | `public class RouteUtil {` |
| GL-utils_RouteUtil-002 | `app/utils/RouteUtil.java:26` | `public static final DiffRenderer$ diffRenderer = new DiffRenderer$();` |
| GL-utils_LdapService-001 | `app/utils/LdapService.java:22` | `public class LdapService {` |
| GL-utils_LdapService-014 | `app/utils/LdapService.java:51` | `private static final String ENGLISH_NAME_PROPERTY = Play.application().configuration()` |
| GL-utils_LdapService-015 | `app/utils/LdapService.java:54` | `private static final int TIMEOUT = 5000; //ms` |
| GL-utils_LdapService-016 | `app/utils/LdapService.java:57` | `public LdapUser authenticate(String username, String password) throws NamingException {` |
| GL-utils_LdapService-017 | `app/utils/LdapService.java:81` | `private String guessedUser(String username) {` |
| GL-utils_LdapService-019 | `app/utils/LdapService.java:111` | `private String searchFilter(@Nonnull String username) {` |
| GL-utils_LdapService-021 | `app/utils/LdapService.java:129` | `private SearchResult findUser(DirContext ctx, String username, String filter) throws NamingException` |
| GL-utils_MenuType-001 | `app/utils/MenuType.java:24` | `public enum MenuType {` |
| GL-utils_MenuType-002 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-003 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-004 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-005 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-006 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-007 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-008 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-009 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-010 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-011 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-012 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-013 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-014 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-015 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-016 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-017 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-018 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-019 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-020 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-022 | `app/utils/MenuType.java:51` | `private MenuType(int type) {` |
| GL-utils_FileUtil-001 | `app/utils/FileUtil.java:35` | `public class FileUtil {` |
| GL-utils_FileUtil-002 | `app/utils/FileUtil.java:38` | `public static void rm_rf(File file) throws Exception {` |
| GL-utils_FileUtil-003 | `app/utils/FileUtil.java:54` | `static private String or(String a, String b) {` |
| GL-utils_FileUtil-004 | `app/utils/FileUtil.java:59` | `/**` |
| GL-utils_FileUtil-005 | `app/utils/FileUtil.java:86` | `/**` |
| GL-utils_FileUtil-006 | `app/utils/FileUtil.java:112` | `public static MediaType detectMediaType(File file, String name) throws IOException {` |
| GL-utils_FileUtil-010 | `app/utils/FileUtil.java:160` | `/**` |
| GL-utils_FileUtil-012 | `app/utils/FileUtil.java:183` | `/**` |
| GL-utils_Constants-001 | `app/utils/Constants.java:24` | `public class Constants {` |
| GL-utils_AttachmentCache-001 | `app/utils/AttachmentCache.java:11` | `/**` |
| GL-utils_AttachmentCache-002 | `app/utils/AttachmentCache.java:24` | `/**` |
| GL-utils_AttachmentCache-003 | `app/utils/AttachmentCache.java:32` | `/**` |
| GL-utils_AttachmentCache-004 | `app/utils/AttachmentCache.java:52` | `/**` |
| GL-utils_AttachmentCache-005 | `app/utils/AttachmentCache.java:63` | `/**` |
| GL-utils_AttachmentCache-006 | `app/utils/AttachmentCache.java:74` | `/**` |
| GL-utils_AttachmentCache-007 | `app/utils/AttachmentCache.java:92` | `private static String cacheKey(Resource container) {` |
| GL-utils_AttachmentCache-008 | `app/utils/AttachmentCache.java:97` | `/**` |
| GL-utils_AttachmentCache-009 | `app/utils/AttachmentCache.java:107` | `/**` |
| GL-utils_ReservedWordsValidator-001 | `app/utils/ReservedWordsValidator.java:39` | `/**` |
| GL-utils_ReservedWordsValidator-004 | `app/utils/ReservedWordsValidator.java:72` | `/**` |
| GL-utils_ReservedWordsValidator-005 | `app/utils/ReservedWordsValidator.java:84` | `/**` |
| GL-utils_ReservedWordsValidator-006 | `app/utils/ReservedWordsValidator.java:97` | `/**` |
| GL-utils_ValidationUtils-001 | `app/utils/ValidationUtils.java:26` | `public class ValidationUtils {` |
| GL-utils_ValidationUtils-002 | `app/utils/ValidationUtils.java:29` | `public static void rejectIfEmpty(Http.Flash flash, String value, String message) {` |
| GL-service_YonaUserServicePlugin-001 | `app/service/YonaUserServicePlugin.java:21` | `public class YonaUserServicePlugin extends UserServicePlugin {` |
| GL-service_YonaUserServicePlugin-002 | `app/service/YonaUserServicePlugin.java:24` | `public YonaUserServicePlugin(final Application app) {` |
| GL-service_YonaUserServicePlugin-003 | `app/service/YonaUserServicePlugin.java:29` | `@Override` |
| GL-service_YonaUserServicePlugin-004 | `app/service/YonaUserServicePlugin.java:50` | `@Override` |
| GL-service_YonaUserServicePlugin-006 | `app/service/YonaUserServicePlugin.java:102` | `private void updateLocalUserName(UserCredential u, BasicIdentity authUser) {` |
| GL-service_YonaUserServicePlugin-007 | `app/service/YonaUserServicePlugin.java:118` | `@Override` |
| GL-service_YonaUserServicePlugin-008 | `app/service/YonaUserServicePlugin.java:127` | `@Override` |
| GL-service_YonaUserServicePlugin-009 | `app/service/YonaUserServicePlugin.java:134` | `private static void forceOAuthLogout() {` |
| GL-notification_INotificationEvent-001 | `app/notification/INotificationEvent.java:19` | `public interface INotificationEvent {` |
| GL-notification_INotificationEvent-012 | `app/notification/INotificationEvent.java:51` | `boolean resourceExists();` |
| GL-notification_INotificationEvent-013 | `app/notification/INotificationEvent.java:54` | `Set<User> findReceivers();` |
| GL-notification_MergedNotificationEvent-001 | `app/notification/MergedNotificationEvent.java:20` | `public class MergedNotificationEvent implements INotificationEvent {` |
| GL-notification_MergedNotificationEvent-005 | `app/notification/MergedNotificationEvent.java:29` | `public MergedNotificationEvent(@Nonnull INotificationEvent main,` |
| GL-notification_MergedNotificationEvent-006 | `app/notification/MergedNotificationEvent.java:36` | `public MergedNotificationEvent(@Nonnull INotificationEvent main) {` |
| GL-notification_MergedNotificationEvent-007 | `app/notification/MergedNotificationEvent.java:41` | `@Override` |
| GL-notification_MergedNotificationEvent-008 | `app/notification/MergedNotificationEvent.java:47` | `@Override` |
| GL-notification_MergedNotificationEvent-009 | `app/notification/MergedNotificationEvent.java:53` | `@Override` |
| GL-notification_MergedNotificationEvent-010 | `app/notification/MergedNotificationEvent.java:63` | `@Override` |
| GL-notification_MergedNotificationEvent-011 | `app/notification/MergedNotificationEvent.java:73` | `@Override` |
| GL-notification_MergedNotificationEvent-012 | `app/notification/MergedNotificationEvent.java:79` | `@Override` |
| GL-notification_MergedNotificationEvent-013 | `app/notification/MergedNotificationEvent.java:85` | `@Override` |
| GL-notification_MergedNotificationEvent-014 | `app/notification/MergedNotificationEvent.java:91` | `@Override` |
| GL-notification_MergedNotificationEvent-015 | `app/notification/MergedNotificationEvent.java:97` | `@Override` |
| GL-notification_MergedNotificationEvent-016 | `app/notification/MergedNotificationEvent.java:103` | `@Override` |
| GL-notification_MergedNotificationEvent-017 | `app/notification/MergedNotificationEvent.java:109` | `@Override` |
| GL-notification_MergedNotificationEvent-018 | `app/notification/MergedNotificationEvent.java:115` | `@Override` |
| GL-notification_MergedNotificationEvent-019 | `app/notification/MergedNotificationEvent.java:125` | `@Override` |
| GL-mailbox_IMAPMessageUtil-001 | `app/mailbox/IMAPMessageUtil.java:31` | `public class IMAPMessageUtil {` |
| GL-mailbox_IMAPMessageUtil-002 | `app/mailbox/IMAPMessageUtil.java:33` | `public static User extractSender(Message msg) throws MessagingException {` |
| GL-mailbox_IMAPMessageUtil-003 | `app/mailbox/IMAPMessageUtil.java:45` | `public static String asString(IMAPMessage msg) throws MessagingException {` |
| GL-mailbox_EmailAddressWithDetail-001 | `app/mailbox/EmailAddressWithDetail.java:29` | `/**` |
| GL-mailbox_EmailAddressWithDetail-002 | `app/mailbox/EmailAddressWithDetail.java:34` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-003 | `app/mailbox/EmailAddressWithDetail.java:38` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-004 | `app/mailbox/EmailAddressWithDetail.java:42` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-005 | `app/mailbox/EmailAddressWithDetail.java:46` | `public EmailAddressWithDetail(@Nonnull String address) {` |
| GL-mailbox_EmailAddressWithDetail-006 | `app/mailbox/EmailAddressWithDetail.java:62` | `/**` |
| GL-mailbox_EmailAddressWithDetail-007 | `app/mailbox/EmailAddressWithDetail.java:72` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-008 | `app/mailbox/EmailAddressWithDetail.java:78` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-009 | `app/mailbox/EmailAddressWithDetail.java:84` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-010 | `app/mailbox/EmailAddressWithDetail.java:90` | `/**` |
| GL-mailbox_EmailAddressWithDetail-011 | `app/mailbox/EmailAddressWithDetail.java:103` | `/**` |
| GL-mailbox_EmailAddressWithDetail-012 | `app/mailbox/EmailAddressWithDetail.java:113` | `public String toString() {` |
| GL-mailbox_EmailHandler-001 | `app/mailbox/EmailHandler.java:58` | `/**` |
| GL-mailbox_EmailHandler-002 | `app/mailbox/EmailHandler.java:65` | `/**` |
| GL-mailbox_EmailHandler-003 | `app/mailbox/EmailHandler.java:91` | `/**` |
| GL-mailbox_EmailHandler-004 | `app/mailbox/EmailHandler.java:102` | `private EmailHandler() {` |
| GL-mailbox_EmailHandler-005 | `app/mailbox/EmailHandler.java:108` | `private static List<String> parseMessageIds(String headerValue) {` |
| GL-mailbox_EmailHandler-006 | `app/mailbox/EmailHandler.java:133` | `private static void handleMessages(final IMAPFolder folder, List<Message> messages) {` |
| GL-mailbox_EmailHandler-007 | `app/mailbox/EmailHandler.java:167` | `private static void handleMessage(@Nonnull IMAPMessage msg) {` |
| GL-mailbox_EmailHandler-008 | `app/mailbox/EmailHandler.java:271` | `private static class MailHeader {` |
| GL-mailbox_EmailHandler-011 | `app/mailbox/EmailHandler.java:278` | `public MailHeader(@Nonnull IMAPMessage message, @Nonnull String name) {` |
| GL-mailbox_EmailHandler-012 | `app/mailbox/EmailHandler.java:284` | `public boolean containsIgnoreCase(@Nonnull String expectedValue) throws MessagingException {` |
| GL-mailbox_EmailHandler-013 | `app/mailbox/EmailHandler.java:306` | `/**` |
| GL-mailbox_EmailHandler-014 | `app/mailbox/EmailHandler.java:317` | `private static void createResources(IMAPMessage msg, User sender, List<String> errors)` |
| GL-mailbox_EmailHandler-016 | `app/mailbox/EmailHandler.java:389` | `/**` |
| GL-mailbox_EmailHandler-022 | `app/mailbox/EmailHandler.java:515` | `private static void reply(IMAPMessage origin, String username, String emailAddress,` |
| GL-mailbox_EmailHandler-023 | `app/mailbox/EmailHandler.java:545` | `private static void reply(IMAPMessage origin, User to, String msg) {` |
| GL-mailbox_EmailHandler-025 | `app/mailbox/EmailHandler.java:566` | `/**` |
| GL-mailbox_EmailHandler-026 | `app/mailbox/EmailHandler.java:592` | `/**` |
| GL-mailbox_MailboxService-001 | `app/mailbox/MailboxService.java:38` | `/**` |
| GL-mailbox_MailboxService-015 | `app/mailbox/MailboxService.java:89` | `/**` |
| GL-mailbox_MailboxService-016 | `app/mailbox/MailboxService.java:104` | `/**` |
| GL-mailbox_MailboxService-017 | `app/mailbox/MailboxService.java:135` | `/**` |
| GL-mailbox_MailboxService-018 | `app/mailbox/MailboxService.java:158` | `/**` |
| GL-mailbox_MailboxService-019 | `app/mailbox/MailboxService.java:210` | `private void handleNewMessagesAndStartListener() {` |
| GL-mailbox_MailboxService-020 | `app/mailbox/MailboxService.java:232` | `/**` |
| GL-mailbox_MailboxService-021 | `app/mailbox/MailboxService.java:251` | `/**` |
| GL-mailbox_MailboxService-022 | `app/mailbox/MailboxService.java:295` | `/**` |
| GL-mailbox_MailboxService-023 | `app/mailbox/MailboxService.java:362` | `/**` |
| GL-mailbox_CreationViaEmail-001 | `app/mailbox/CreationViaEmail.java:58` | `/**` |
| GL-mailbox_CreationViaEmail-002 | `app/mailbox/CreationViaEmail.java:63` | `/**` |
| GL-mailbox_CreationViaEmail-003 | `app/mailbox/CreationViaEmail.java:109` | `/**` |
| GL-mailbox_CreationViaEmail-004 | `app/mailbox/CreationViaEmail.java:127` | `private static Comment makeNewComment(Resource target, User sender, String body) throws IssueNotFoun` |
| GL-mailbox_CreationViaEmail-005 | `app/mailbox/CreationViaEmail.java:154` | `/**` |
| GL-mailbox_CreationViaEmail-006 | `app/mailbox/CreationViaEmail.java:182` | `@Transactional` |
| GL-mailbox_CreationViaEmail-007 | `app/mailbox/CreationViaEmail.java:212` | `/**` |
| GL-mailbox_CreationViaEmail-008 | `app/mailbox/CreationViaEmail.java:241` | `@Transactional` |
| GL-mailbox_CreationViaEmail-009 | `app/mailbox/CreationViaEmail.java:301` | `// You don't need to instantiate this class because this class is just` |
| GL-mailbox_CreationViaEmail-010 | `app/mailbox/CreationViaEmail.java:306` | `@Nonnull` |
| GL-mailbox_CreationViaEmail-011 | `app/mailbox/CreationViaEmail.java:312` | `@Nonnull` |
| GL-mailbox_CreationViaEmail-016 | `app/mailbox/CreationViaEmail.java:391` | `/**` |
| GL-mailbox_CreationViaEmail-018 | `app/mailbox/CreationViaEmail.java:424` | `/**` |
| GL-mailbox_CreationViaEmail-019 | `app/mailbox/CreationViaEmail.java:446` | `private static String cannotCreateMessage(User user, Project project,` |
| GL-mailbox_CreationViaEmail-020 | `app/mailbox/CreationViaEmail.java:455` | `private static void addEvent(NotificationEvent event, Address[] recipients,` |
| GL-mailbox_CreationViaEmail-021 | `app/mailbox/CreationViaEmail.java:468` | `private static String replaceCidWithAttachments(String html,` |
| GL-mailbox_CreationViaEmail-022 | `app/mailbox/CreationViaEmail.java:504` | `private static Attachment saveAttachment(Part partToAttach, Resource container)` |
| GL-mailbox_CreationViaEmail-023 | `app/mailbox/CreationViaEmail.java:519` | `private static Map<String, Attachment> saveAttachments(` |
| GL-mailbox_Content-001 | `app/mailbox/Content.java:10` | `public class Content {` |
| GL-mailbox_Content-003 | `app/mailbox/Content.java:14` | `public final List<MimePart> attachments = new ArrayList<>();` |
| GL-mailbox_Content-005 | `app/mailbox/Content.java:19` | `public Content() { }` |
| GL-mailbox_Content-006 | `app/mailbox/Content.java:22` | `public Content(MimePart attachment) {` |
| GL-mailbox_Content-007 | `app/mailbox/Content.java:27` | `public Content merge(Content that) {` |
| GL-mailbox_exceptions_MailHandlerException-001 | `app/mailbox/exceptions/MailHandlerException.java:24` | `public class MailHandlerException extends Exception{` |
| GL-mailbox_exceptions_MailHandlerException-003 | `app/mailbox/exceptions/MailHandlerException.java:29` | `MailHandlerException(String s) {` |
| GL-mailbox_exceptions_PostingNotFound-001 | `app/mailbox/exceptions/PostingNotFound.java:24` | `public class PostingNotFound extends MailHandlerException {` |
| GL-mailbox_exceptions_PostingNotFound-003 | `app/mailbox/exceptions/PostingNotFound.java:29` | `public PostingNotFound(Long number) {` |
| GL-mailbox_exceptions_IllegalDetailException-001 | `app/mailbox/exceptions/IllegalDetailException.java:24` | `public class IllegalDetailException extends Exception {` |
| GL-mailbox_exceptions_IssueNotFound-001 | `app/mailbox/exceptions/IssueNotFound.java:24` | `public class IssueNotFound extends MailHandlerException {` |
| GL-mailbox_exceptions_IssueNotFound-003 | `app/mailbox/exceptions/IssueNotFound.java:29` | `public IssueNotFound(Long number) {` |
| GL-mailbox_exceptions_PermissionDenied-001 | `app/mailbox/exceptions/PermissionDenied.java:24` | `public class PermissionDenied extends MailHandlerException {` |
| GL-mailbox_exceptions_PermissionDenied-003 | `app/mailbox/exceptions/PermissionDenied.java:29` | `public PermissionDenied(String s) {` |
| GL-data_DataService-001 | `app/data/DataService.java:47` | `/**` |
| GL-data_DataService-003 | `app/data/DataService.java:57` | `private static final Comparator<Exchanger> COMPARATOR = new Comparator<Exchanger>() {` |
| GL-data_DataService-005 | `app/data/DataService.java:68` | `public DataService() {` |
| GL-data_DataService-006 | `app/data/DataService.java:122` | `public InputStream exportData() {` |
| GL-data_DataService-008 | `app/data/DataService.java:185` | `public void importData(File file) throws IOException {` |
| GL-data_DataService-010 | `app/data/DataService.java:238` | `private void enableReferentialIntegrity(String dbName, JdbcTemplate jdbcTemplate) {` |
| GL-data_DataService-011 | `app/data/DataService.java:248` | `private void disableReferentialIntegtiry(String dbName, JdbcTemplate jdbcTemplate) {` |
| GL-data_DefaultExchanger-001 | `app/data/DefaultExchanger.java:44` | `/**` |
| GL-data_DefaultExchanger-004 | `app/data/DefaultExchanger.java:55` | `protected Long timestamp(Timestamp timestamp) {` |
| GL-data_DefaultExchanger-005 | `app/data/DefaultExchanger.java:65` | `protected Long date(Date date) {` |
| GL-data_DefaultExchanger-006 | `app/data/DefaultExchanger.java:75` | `protected Timestamp timestamp(long time) {` |
| GL-data_DefaultExchanger-007 | `app/data/DefaultExchanger.java:84` | `protected Date date(long time) {` |
| GL-data_DefaultExchanger-009 | `app/data/DefaultExchanger.java:102` | `protected String clobString(@Nullable Clob clob) throws SQLException {` |
| GL-data_DefaultExchanger-011 | `app/data/DefaultExchanger.java:130` | `/**` |
| GL-data_DefaultExchanger-012 | `app/data/DefaultExchanger.java:146` | `protected void putLong(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws ` |
| GL-data_DefaultExchanger-013 | `app/data/DefaultExchanger.java:157` | `protected void putInt(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws S` |
| GL-data_DefaultExchanger-014 | `app/data/DefaultExchanger.java:168` | `protected void putString(JsonGenerator generator, String fieldName, ResultSet rs, short index) throw` |
| GL-data_DefaultExchanger-015 | `app/data/DefaultExchanger.java:179` | `protected void putBoolean(JsonGenerator generator, String fieldName, ResultSet rs, short index) thro` |
| GL-data_DefaultExchanger-016 | `app/data/DefaultExchanger.java:185` | `protected void putTimestamp(JsonGenerator generator, String fieldName, ResultSet rs, short index) th` |
| GL-data_DefaultExchanger-017 | `app/data/DefaultExchanger.java:196` | `protected void putDate(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws ` |
| GL-data_DefaultExchanger-018 | `app/data/DefaultExchanger.java:207` | `protected void putClob(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws ` |
| GL-data_DefaultExchanger-019 | `app/data/DefaultExchanger.java:218` | `public void exportData(String dbName, String catalogName, final JsonGenerator generator, JdbcTemplat` |
| GL-data_DefaultExchanger-020 | `app/data/DefaultExchanger.java:256` | `public void importData(String dbName, JsonParser parser, JdbcTemplate jdbcTemplate) throws IOExcepti` |
| GL-data_DefaultExchanger-021 | `app/data/DefaultExchanger.java:295` | `private void importSequence(String dbName, JsonParser parser, JdbcTemplate jdbcTemplate) throws IOEx` |
| GL-data_DefaultExchanger-022 | `app/data/DefaultExchanger.java:317` | `private void importDataFromArray(JsonParser parser, JdbcTemplate jdbcTemplate, int batchSize) throws` |
| GL-data_DefaultExchanger-023 | `app/data/DefaultExchanger.java:335` | `private void truncateTable(JdbcTemplate jdbcTemplate) {` |
| GL-data_DefaultExchanger-024 | `app/data/DefaultExchanger.java:342` | `private int[] batchUpdate(JdbcTemplate jdbcTemplate, final List<JsonNode> nodes) {` |
| GL-data_DefaultExchanger-025 | `app/data/DefaultExchanger.java:358` | `/**` |
| GL-data_DefaultExchanger-026 | `app/data/DefaultExchanger.java:370` | `/**` |
| GL-data_DefaultExchanger-027 | `app/data/DefaultExchanger.java:383` | `/**` |
| GL-data_DefaultExchanger-028 | `app/data/DefaultExchanger.java:391` | `/**` |
| GL-data_DefaultExchanger-029 | `app/data/DefaultExchanger.java:399` | `protected boolean hasSequence() {` |
| GL-data_DefaultExchanger-030 | `app/data/DefaultExchanger.java:404` | `protected String sequenceName() {` |
| GL-data_Exchanger-001 | `app/data/Exchanger.java:30` | `/**` |
| GL-data_Exchanger-002 | `app/data/Exchanger.java:36` | `/**` |
| GL-data_Exchanger-003 | `app/data/Exchanger.java:45` | `/**` |
| GL-data_Exchanger-004 | `app/data/Exchanger.java:64` | `/**` |
| GL-data_exchangers_MilestoneDataExchanger-001 | `app/data/exchangers/MilestoneDataExchanger.java:31` | `/**` |
| GL-data_exchangers_MilestoneDataExchanger-002 | `app/data/exchangers/MilestoneDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_MilestoneDataExchanger-003 | `app/data/exchangers/MilestoneDataExchanger.java:39` | `private static final String TITLE = "title"; // VARCHAR(255)` |
| GL-data_exchangers_MilestoneDataExchanger-004 | `app/data/exchangers/MilestoneDataExchanger.java:41` | `private static final String DUE_DATE = "due_date"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_MilestoneDataExchanger-005 | `app/data/exchangers/MilestoneDataExchanger.java:43` | `private static final String CONTENTS = "contents"; // CLOB(2147483647)` |
| GL-data_exchangers_MilestoneDataExchanger-006 | `app/data/exchangers/MilestoneDataExchanger.java:45` | `private static final String STATE = "state"; // INTEGER(10)` |
| GL-data_exchangers_MilestoneDataExchanger-007 | `app/data/exchangers/MilestoneDataExchanger.java:47` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19)` |
| GL-data_exchangers_MilestoneDataExchanger-008 | `app/data/exchangers/MilestoneDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_MilestoneDataExchanger-009 | `app/data/exchangers/MilestoneDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_MilestoneDataExchanger-010 | `app/data/exchangers/MilestoneDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_MilestoneDataExchanger-011 | `app/data/exchangers/MilestoneDataExchanger.java:80` | `@Override` |
| GL-data_exchangers_MilestoneDataExchanger-012 | `app/data/exchangers/MilestoneDataExchanger.java:87` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-001 | `app/data/exchangers/ProjectTransferDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectTransferDataExchanger-002 | `app/data/exchangers/ProjectTransferDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectTransferDataExchanger-003 | `app/data/exchangers/ProjectTransferDataExchanger.java:39` | `private static final String SENDER_ID = "sender_id"; // VARCHAR(255)` |
| GL-data_exchangers_ProjectTransferDataExchanger-004 | `app/data/exchangers/ProjectTransferDataExchanger.java:41` | `private static final String DESTINATION = "destination"; // VARCHAR(255)` |
| GL-data_exchangers_ProjectTransferDataExchanger-005 | `app/data/exchangers/ProjectTransferDataExchanger.java:43` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectTransferDataExchanger-006 | `app/data/exchangers/ProjectTransferDataExchanger.java:45` | `private static final String REQUESTED = "requested"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_ProjectTransferDataExchanger-007 | `app/data/exchangers/ProjectTransferDataExchanger.java:47` | `private static final String CONFIRM_KEY = "confirm_key"; // VARCHAR(50)` |
| GL-data_exchangers_ProjectTransferDataExchanger-008 | `app/data/exchangers/ProjectTransferDataExchanger.java:49` | `private static final String ACCEPTED = "accepted"; // BOOLEAN(1)` |
| GL-data_exchangers_ProjectTransferDataExchanger-009 | `app/data/exchangers/ProjectTransferDataExchanger.java:51` | `private static final String NEW_PROJECT_NAME = "new_project_name"; // VARCHAR(255)` |
| GL-data_exchangers_ProjectTransferDataExchanger-010 | `app/data/exchangers/ProjectTransferDataExchanger.java:54` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-011 | `app/data/exchangers/ProjectTransferDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-012 | `app/data/exchangers/ProjectTransferDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-013 | `app/data/exchangers/ProjectTransferDataExchanger.java:88` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-014 | `app/data/exchangers/ProjectTransferDataExchanger.java:95` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-001 | `app/data/exchangers/OrganizationUserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_OrganizationUserDataExchanger-006 | `app/data/exchangers/OrganizationUserDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-007 | `app/data/exchangers/OrganizationUserDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-008 | `app/data/exchangers/OrganizationUserDataExchanger.java:66` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-009 | `app/data/exchangers/OrganizationUserDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-010 | `app/data/exchangers/OrganizationUserDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-001 | `app/data/exchangers/OriginalEmailDataExchanger.java:31` | `/**` |
| GL-data_exchangers_OriginalEmailDataExchanger-007 | `app/data/exchangers/OriginalEmailDataExchanger.java:47` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-008 | `app/data/exchangers/OriginalEmailDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-009 | `app/data/exchangers/OriginalEmailDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-010 | `app/data/exchangers/OriginalEmailDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-011 | `app/data/exchangers/OriginalEmailDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-001 | `app/data/exchangers/IssueLabelDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueLabelDataExchanger-002 | `app/data/exchangers/IssueLabelDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueLabelDataExchanger-003 | `app/data/exchangers/IssueLabelDataExchanger.java:39` | `private static final String COLOR = "color"; // VARCHAR(255)` |
| GL-data_exchangers_IssueLabelDataExchanger-004 | `app/data/exchangers/IssueLabelDataExchanger.java:41` | `private static final String NAME = "name"; // VARCHAR(255)` |
| GL-data_exchangers_IssueLabelDataExchanger-005 | `app/data/exchangers/IssueLabelDataExchanger.java:43` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19)` |
| GL-data_exchangers_IssueLabelDataExchanger-006 | `app/data/exchangers/IssueLabelDataExchanger.java:45` | `private static final String CATEGORY_ID = "category_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueLabelDataExchanger-007 | `app/data/exchangers/IssueLabelDataExchanger.java:48` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-008 | `app/data/exchangers/IssueLabelDataExchanger.java:59` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-009 | `app/data/exchangers/IssueLabelDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-010 | `app/data/exchangers/IssueLabelDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-011 | `app/data/exchangers/IssueLabelDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-001 | `app/data/exchangers/MentionDataExchanger.java:31` | `/**` |
| GL-data_exchangers_MentionDataExchanger-006 | `app/data/exchangers/MentionDataExchanger.java:45` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-007 | `app/data/exchangers/MentionDataExchanger.java:55` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-008 | `app/data/exchangers/MentionDataExchanger.java:65` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-009 | `app/data/exchangers/MentionDataExchanger.java:71` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-010 | `app/data/exchangers/MentionDataExchanger.java:77` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-001 | `app/data/exchangers/AssigneeDataExchanger.java:31` | `/**` |
| GL-data_exchangers_AssigneeDataExchanger-005 | `app/data/exchangers/AssigneeDataExchanger.java:44` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-006 | `app/data/exchangers/AssigneeDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-007 | `app/data/exchangers/AssigneeDataExchanger.java:59` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-008 | `app/data/exchangers/AssigneeDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-009 | `app/data/exchangers/AssigneeDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-010 | `app/data/exchangers/AssigneeDataExchanger.java:80` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-001 | `app/data/exchangers/CommentThreadUserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_CommentThreadUserDataExchanger-004 | `app/data/exchangers/CommentThreadUserDataExchanger.java:41` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-005 | `app/data/exchangers/CommentThreadUserDataExchanger.java:49` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-006 | `app/data/exchangers/CommentThreadUserDataExchanger.java:57` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-007 | `app/data/exchangers/CommentThreadUserDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-008 | `app/data/exchangers/CommentThreadUserDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-009 | `app/data/exchangers/CommentThreadUserDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-001 | `app/data/exchangers/PostingDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PostingDataExchanger-002 | `app/data/exchangers/PostingDataExchanger.java:36` | `private static final String ID = "id";  //BIGINT  nullable? 0` |
| GL-data_exchangers_PostingDataExchanger-003 | `app/data/exchangers/PostingDataExchanger.java:38` | `private static final String TITLE = "title";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-004 | `app/data/exchangers/PostingDataExchanger.java:40` | `private static final String BODY = "body";  //CLOB  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-005 | `app/data/exchangers/PostingDataExchanger.java:42` | `private static final String CREATED_DATE = "created_date";  //TIMESTAMP  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-006 | `app/data/exchangers/PostingDataExchanger.java:44` | `private static final String NUM_OF_COMMENTS = "num_of_comments";  //INTEGER  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-007 | `app/data/exchangers/PostingDataExchanger.java:46` | `private static final String AUTHOR_ID = "author_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-008 | `app/data/exchangers/PostingDataExchanger.java:48` | `private static final String AUTHOR_LOGIN_ID = "author_login_id";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-009 | `app/data/exchangers/PostingDataExchanger.java:50` | `private static final String AUTHOR_NAME = "author_name";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-010 | `app/data/exchangers/PostingDataExchanger.java:52` | `private static final String PROJECT_ID = "project_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-011 | `app/data/exchangers/PostingDataExchanger.java:54` | `private static final String NUMBER = "number";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-012 | `app/data/exchangers/PostingDataExchanger.java:56` | `private static final String NOTICE = "notice";  //BOOLEAN  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-013 | `app/data/exchangers/PostingDataExchanger.java:58` | `private static final String UPDATED_DATE = "updated_date";  //TIMESTAMP  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-014 | `app/data/exchangers/PostingDataExchanger.java:60` | `private static final String README = "readme";  //BOOLEAN  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-015 | `app/data/exchangers/PostingDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-016 | `app/data/exchangers/PostingDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-017 | `app/data/exchangers/PostingDataExchanger.java:101` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-018 | `app/data/exchangers/PostingDataExchanger.java:107` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-019 | `app/data/exchangers/PostingDataExchanger.java:114` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-001 | `app/data/exchangers/ProjectUserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectUserDataExchanger-006 | `app/data/exchangers/ProjectUserDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-007 | `app/data/exchangers/ProjectUserDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-008 | `app/data/exchangers/ProjectUserDataExchanger.java:67` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-009 | `app/data/exchangers/ProjectUserDataExchanger.java:73` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-010 | `app/data/exchangers/ProjectUserDataExchanger.java:79` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-001 | `app/data/exchangers/LabelDataExchanger.java:31` | `/**` |
| GL-data_exchangers_LabelDataExchanger-005 | `app/data/exchangers/LabelDataExchanger.java:44` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-006 | `app/data/exchangers/LabelDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-007 | `app/data/exchangers/LabelDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-008 | `app/data/exchangers/LabelDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-009 | `app/data/exchangers/LabelDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-001 | `app/data/exchangers/IssueDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueDataExchanger-017 | `app/data/exchangers/IssueDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-018 | `app/data/exchangers/IssueDataExchanger.java:89` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-019 | `app/data/exchangers/IssueDataExchanger.java:110` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-020 | `app/data/exchangers/IssueDataExchanger.java:116` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-021 | `app/data/exchangers/IssueDataExchanger.java:124` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-001 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-002 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-003 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:39` | `private static final String USER_ID = "user_id"; // BIGINT(19)` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-004 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:41` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19)` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-005 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:43` | `private static final String NOTIFICATION_TYPE = "notification_type"; // VARCHAR(255)` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-006 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:45` | `private static final String ALLOWED = "allowed"; // BOOLEAN(1)` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-007 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:48` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-008 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:59` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-009 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-010 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-011 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:83` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-001 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-002 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-003 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:39` | `private static final String PUSHED_DATE = "pushed_date"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-004 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:41` | `private static final String NAME = "name"; // VARCHAR(255)` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-005 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:43` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-006 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-007 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:52` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-008 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-009 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-010 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-001 | `app/data/exchangers/IssueCommentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueCommentDataExchanger-009 | `app/data/exchangers/IssueCommentDataExchanger.java:52` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-010 | `app/data/exchangers/IssueCommentDataExchanger.java:65` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-011 | `app/data/exchangers/IssueCommentDataExchanger.java:79` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-012 | `app/data/exchangers/IssueCommentDataExchanger.java:85` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-013 | `app/data/exchangers/IssueCommentDataExchanger.java:92` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-001 | `app/data/exchangers/PropertyDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PropertyDataExchanger-005 | `app/data/exchangers/PropertyDataExchanger.java:43` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-006 | `app/data/exchangers/PropertyDataExchanger.java:52` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-007 | `app/data/exchangers/PropertyDataExchanger.java:61` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-008 | `app/data/exchangers/PropertyDataExchanger.java:67` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-009 | `app/data/exchangers/PropertyDataExchanger.java:73` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-001 | `app/data/exchangers/NotificationEventDataExchanger.java:31` | `/**` |
| GL-data_exchangers_NotificationEventDataExchanger-011 | `app/data/exchangers/NotificationEventDataExchanger.java:55` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-012 | `app/data/exchangers/NotificationEventDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-013 | `app/data/exchangers/NotificationEventDataExchanger.java:85` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-014 | `app/data/exchangers/NotificationEventDataExchanger.java:91` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-015 | `app/data/exchangers/NotificationEventDataExchanger.java:99` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-001 | `app/data/exchangers/AttachmentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_AttachmentDataExchanger-010 | `app/data/exchangers/AttachmentDataExchanger.java:54` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-011 | `app/data/exchangers/AttachmentDataExchanger.java:60` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-012 | `app/data/exchangers/AttachmentDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-013 | `app/data/exchangers/AttachmentDataExchanger.java:88` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-014 | `app/data/exchangers/AttachmentDataExchanger.java:94` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-015 | `app/data/exchangers/AttachmentDataExchanger.java:101` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-001 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-004 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-005 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-006 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-007 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-008 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-009 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-001 | `app/data/exchangers/SiteAdminDataExchanger.java:31` | `/**` |
| GL-data_exchangers_SiteAdminDataExchanger-002 | `app/data/exchangers/SiteAdminDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_SiteAdminDataExchanger-003 | `app/data/exchangers/SiteAdminDataExchanger.java:39` | `private static final String ADMIN_ID = "admin_id"; // BIGINT(19)` |
| GL-data_exchangers_SiteAdminDataExchanger-004 | `app/data/exchangers/SiteAdminDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-005 | `app/data/exchangers/SiteAdminDataExchanger.java:48` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-006 | `app/data/exchangers/SiteAdminDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-007 | `app/data/exchangers/SiteAdminDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-008 | `app/data/exchangers/SiteAdminDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-001 | `app/data/exchangers/CommentThreadDataExchanger.java:31` | `/**` |
| GL-data_exchangers_CommentThreadDataExchanger-020 | `app/data/exchangers/CommentThreadDataExchanger.java:73` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-021 | `app/data/exchangers/CommentThreadDataExchanger.java:97` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-022 | `app/data/exchangers/CommentThreadDataExchanger.java:121` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-023 | `app/data/exchangers/CommentThreadDataExchanger.java:127` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-024 | `app/data/exchangers/CommentThreadDataExchanger.java:136` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-001 | `app/data/exchangers/ProjectDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectDataExchanger-017 | `app/data/exchangers/ProjectDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-018 | `app/data/exchangers/ProjectDataExchanger.java:89` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-019 | `app/data/exchangers/ProjectDataExchanger.java:110` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-020 | `app/data/exchangers/ProjectDataExchanger.java:116` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-021 | `app/data/exchangers/ProjectDataExchanger.java:124` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-001 | `app/data/exchangers/ProjectLabelDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectLabelDataExchanger-004 | `app/data/exchangers/ProjectLabelDataExchanger.java:41` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-005 | `app/data/exchangers/ProjectLabelDataExchanger.java:49` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-006 | `app/data/exchangers/ProjectLabelDataExchanger.java:57` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-007 | `app/data/exchangers/ProjectLabelDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-008 | `app/data/exchangers/ProjectLabelDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-009 | `app/data/exchangers/ProjectLabelDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-001 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-006 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-007 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-008 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:66` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-009 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-010 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-001 | `app/data/exchangers/PullRequestCommitDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PullRequestCommitDataExchanger-002 | `app/data/exchangers/PullRequestCommitDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_PullRequestCommitDataExchanger-003 | `app/data/exchangers/PullRequestCommitDataExchanger.java:39` | `private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-004 | `app/data/exchangers/PullRequestCommitDataExchanger.java:41` | `private static final String COMMIT_ID = "commit_id"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-005 | `app/data/exchangers/PullRequestCommitDataExchanger.java:43` | `private static final String COMMIT_SHORT_ID = "commit_short_id"; // VARCHAR(7)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-006 | `app/data/exchangers/PullRequestCommitDataExchanger.java:45` | `private static final String COMMIT_MESSAGE = "commit_message"; // CLOB(2147483647)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-007 | `app/data/exchangers/PullRequestCommitDataExchanger.java:47` | `private static final String CREATED = "created"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-008 | `app/data/exchangers/PullRequestCommitDataExchanger.java:49` | `private static final String AUTHOR_DATE = "author_date"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-009 | `app/data/exchangers/PullRequestCommitDataExchanger.java:51` | `private static final String AUTHOR_EMAIL = "author_email"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-010 | `app/data/exchangers/PullRequestCommitDataExchanger.java:53` | `private static final String STATE = "state"; // VARCHAR(10)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-011 | `app/data/exchangers/PullRequestCommitDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-012 | `app/data/exchangers/PullRequestCommitDataExchanger.java:71` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-013 | `app/data/exchangers/PullRequestCommitDataExchanger.java:86` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-014 | `app/data/exchangers/PullRequestCommitDataExchanger.java:92` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-015 | `app/data/exchangers/PullRequestCommitDataExchanger.java:99` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-001 | `app/data/exchangers/EmailDataExchanger.java:31` | `/**` |
| GL-data_exchangers_EmailDataExchanger-002 | `app/data/exchangers/EmailDataExchanger.java:36` | `private static final String ID = "id";  //BIGINT  nullable? 0` |
| GL-data_exchangers_EmailDataExchanger-003 | `app/data/exchangers/EmailDataExchanger.java:38` | `private static final String USER_ID = "user_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_EmailDataExchanger-004 | `app/data/exchangers/EmailDataExchanger.java:40` | `private static final String EMAIL = "email";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_EmailDataExchanger-005 | `app/data/exchangers/EmailDataExchanger.java:42` | `private static final String TOKEN = "token";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_EmailDataExchanger-006 | `app/data/exchangers/EmailDataExchanger.java:44` | `private static final String VALID = "valid";  //BOOLEAN  nullable? 1` |
| GL-data_exchangers_EmailDataExchanger-007 | `app/data/exchangers/EmailDataExchanger.java:47` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-008 | `app/data/exchangers/EmailDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-009 | `app/data/exchangers/EmailDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-010 | `app/data/exchangers/EmailDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-011 | `app/data/exchangers/EmailDataExchanger.java:81` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-001 | `app/data/exchangers/ProjectVisitationDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectVisitationDataExchanger-002 | `app/data/exchangers/ProjectVisitationDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectVisitationDataExchanger-003 | `app/data/exchangers/ProjectVisitationDataExchanger.java:39` | `private static final String VISITED = "visited"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_ProjectVisitationDataExchanger-004 | `app/data/exchangers/ProjectVisitationDataExchanger.java:41` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectVisitationDataExchanger-005 | `app/data/exchangers/ProjectVisitationDataExchanger.java:43` | `private static final String RECENTLY_VISITED_PROJECTS_ID = "recently_visited_projects_id"; // BIGINT` |
| GL-data_exchangers_ProjectVisitationDataExchanger-006 | `app/data/exchangers/ProjectVisitationDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-007 | `app/data/exchangers/ProjectVisitationDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-008 | `app/data/exchangers/ProjectVisitationDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-009 | `app/data/exchangers/ProjectVisitationDataExchanger.java:73` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-010 | `app/data/exchangers/ProjectVisitationDataExchanger.java:79` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-001 | `app/data/exchangers/WatchDataExchanger.java:31` | `/**` |
| GL-data_exchangers_WatchDataExchanger-006 | `app/data/exchangers/WatchDataExchanger.java:45` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-007 | `app/data/exchangers/WatchDataExchanger.java:55` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-008 | `app/data/exchangers/WatchDataExchanger.java:66` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-009 | `app/data/exchangers/WatchDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-010 | `app/data/exchangers/WatchDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-001 | `app/data/exchangers/OrganizationDataExchanger.java:31` | `/**` |
| GL-data_exchangers_OrganizationDataExchanger-006 | `app/data/exchangers/OrganizationDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-007 | `app/data/exchangers/OrganizationDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-008 | `app/data/exchangers/OrganizationDataExchanger.java:66` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-009 | `app/data/exchangers/OrganizationDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-010 | `app/data/exchangers/OrganizationDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-001 | `app/data/exchangers/CommitCommentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_CommitCommentDataExchanger-013 | `app/data/exchangers/CommitCommentDataExchanger.java:59` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-014 | `app/data/exchangers/CommitCommentDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-015 | `app/data/exchangers/CommitCommentDataExchanger.java:93` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-016 | `app/data/exchangers/CommitCommentDataExchanger.java:99` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-017 | `app/data/exchangers/CommitCommentDataExchanger.java:107` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-001 | `app/data/exchangers/PostingCommentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PostingCommentDataExchanger-002 | `app/data/exchangers/PostingCommentDataExchanger.java:36` | `private static final String ID = "id";  //BIGINT  nullable? 0` |
| GL-data_exchangers_PostingCommentDataExchanger-003 | `app/data/exchangers/PostingCommentDataExchanger.java:38` | `private static final String CREATED_DATE = "created_date";  //TIMESTAMP  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-004 | `app/data/exchangers/PostingCommentDataExchanger.java:40` | `private static final String AUTHOR_ID = "author_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-005 | `app/data/exchangers/PostingCommentDataExchanger.java:42` | `private static final String AUTHOR_LOGIN_ID = "author_login_id";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-006 | `app/data/exchangers/PostingCommentDataExchanger.java:44` | `private static final String AUTHOR_NAME = "author_name";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-007 | `app/data/exchangers/PostingCommentDataExchanger.java:46` | `private static final String POSTING_ID = "posting_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-008 | `app/data/exchangers/PostingCommentDataExchanger.java:48` | `private static final String CONTENTS = "contents";  //CLOB  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-009 | `app/data/exchangers/PostingCommentDataExchanger.java:51` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-010 | `app/data/exchangers/PostingCommentDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-011 | `app/data/exchangers/PostingCommentDataExchanger.java:77` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-012 | `app/data/exchangers/PostingCommentDataExchanger.java:83` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-013 | `app/data/exchangers/PostingCommentDataExchanger.java:90` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-001 | `app/data/exchangers/IssueVoterDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueVoterDataExchanger-002 | `app/data/exchangers/IssueVoterDataExchanger.java:37` | `private static final String ISSUE_ID = "issue_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueVoterDataExchanger-003 | `app/data/exchangers/IssueVoterDataExchanger.java:39` | `private static final String USER_ID = "user_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueVoterDataExchanger-004 | `app/data/exchangers/IssueVoterDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-005 | `app/data/exchangers/IssueVoterDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-006 | `app/data/exchangers/IssueVoterDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-007 | `app/data/exchangers/IssueVoterDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-008 | `app/data/exchangers/IssueVoterDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-009 | `app/data/exchangers/IssueVoterDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-001 | `app/data/exchangers/NotificationEventUserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_NotificationEventUserDataExchanger-004 | `app/data/exchangers/NotificationEventUserDataExchanger.java:41` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-005 | `app/data/exchangers/NotificationEventUserDataExchanger.java:49` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-006 | `app/data/exchangers/NotificationEventUserDataExchanger.java:57` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-007 | `app/data/exchangers/NotificationEventUserDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-008 | `app/data/exchangers/NotificationEventUserDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-009 | `app/data/exchangers/NotificationEventUserDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-001 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-002 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:37` | `private static final String USER_ID = "user_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-003 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:39` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-004 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-005 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-006 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-007 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-008 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-009 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-001 | `app/data/exchangers/PullRequestDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PullRequestDataExchanger-002 | `app/data/exchangers/PullRequestDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_PullRequestDataExchanger-003 | `app/data/exchangers/PullRequestDataExchanger.java:39` | `private static final String TITLE = "title"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-004 | `app/data/exchangers/PullRequestDataExchanger.java:41` | `private static final String BODY = "body"; // CLOB(2147483647)` |
| GL-data_exchangers_PullRequestDataExchanger-005 | `app/data/exchangers/PullRequestDataExchanger.java:43` | `private static final String TO_PROJECT_ID = "to_project_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-006 | `app/data/exchangers/PullRequestDataExchanger.java:45` | `private static final String FROM_PROJECT_ID = "from_project_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-007 | `app/data/exchangers/PullRequestDataExchanger.java:47` | `private static final String TO_BRANCH = "to_branch"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-008 | `app/data/exchangers/PullRequestDataExchanger.java:49` | `private static final String FROM_BRANCH = "from_branch"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-009 | `app/data/exchangers/PullRequestDataExchanger.java:51` | `private static final String CONTRIBUTOR_ID = "contributor_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-010 | `app/data/exchangers/PullRequestDataExchanger.java:53` | `private static final String RECEIVER_ID = "receiver_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-011 | `app/data/exchangers/PullRequestDataExchanger.java:55` | `private static final String CREATED = "created"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestDataExchanger-012 | `app/data/exchangers/PullRequestDataExchanger.java:57` | `private static final String UPDATED = "updated"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestDataExchanger-013 | `app/data/exchangers/PullRequestDataExchanger.java:59` | `private static final String RECEIVED = "received"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestDataExchanger-014 | `app/data/exchangers/PullRequestDataExchanger.java:61` | `private static final String STATE = "state"; // INTEGER(10)` |
| GL-data_exchangers_PullRequestDataExchanger-015 | `app/data/exchangers/PullRequestDataExchanger.java:63` | `private static final String LAST_COMMIT_ID = "last_commit_id"; //VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-016 | `app/data/exchangers/PullRequestDataExchanger.java:65` | `private static final String MERGED_COMMIT_ID_FROM = "merged_commit_id_from"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-017 | `app/data/exchangers/PullRequestDataExchanger.java:67` | `private static final String MERGED_COMMIT_ID_TO = "merged_commit_id_to"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-018 | `app/data/exchangers/PullRequestDataExchanger.java:69` | `private static final String NUMBER = "number"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-019 | `app/data/exchangers/PullRequestDataExchanger.java:71` | `private static final String IS_CONFLICT = "is_conflict"; // BOOLEAN(1)` |
| GL-data_exchangers_PullRequestDataExchanger-020 | `app/data/exchangers/PullRequestDataExchanger.java:73` | `private static final String IS_MERGING = "is_merging"; // BOOLEAN(1)` |
| GL-data_exchangers_PullRequestDataExchanger-021 | `app/data/exchangers/PullRequestDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-022 | `app/data/exchangers/PullRequestDataExchanger.java:101` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-023 | `app/data/exchangers/PullRequestDataExchanger.java:126` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-024 | `app/data/exchangers/PullRequestDataExchanger.java:132` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-025 | `app/data/exchangers/PullRequestDataExchanger.java:141` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-001 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-002 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:37` | `private static final String USER_ID = "user_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-003 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:39` | `private static final String ORGANIZATION_ID = "organization_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-004 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-005 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-006 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-007 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-008 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-009 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-001 | `app/data/exchangers/NotificationMailDataExchanger.java:31` | `/**` |
| GL-data_exchangers_NotificationMailDataExchanger-004 | `app/data/exchangers/NotificationMailDataExchanger.java:41` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-005 | `app/data/exchangers/NotificationMailDataExchanger.java:49` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-006 | `app/data/exchangers/NotificationMailDataExchanger.java:57` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-007 | `app/data/exchangers/NotificationMailDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-008 | `app/data/exchangers/NotificationMailDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-001 | `app/data/exchangers/ProjectMenuDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectMenuDataExchanger-010 | `app/data/exchangers/ProjectMenuDataExchanger.java:54` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-011 | `app/data/exchangers/ProjectMenuDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-012 | `app/data/exchangers/ProjectMenuDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-013 | `app/data/exchangers/ProjectMenuDataExchanger.java:88` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-014 | `app/data/exchangers/ProjectMenuDataExchanger.java:95` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-001 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:31` | `/**` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-002 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-003 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:39` | `private static final String USER_ID = "user_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-004 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-005 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:48` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-006 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-007 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-008 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_UserDataExchanger-001 | `app/data/exchangers/UserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UserDataExchanger-014 | `app/data/exchangers/UserDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_UserDataExchanger-015 | `app/data/exchangers/UserDataExchanger.java:79` | `@Override` |
| GL-data_exchangers_UserDataExchanger-016 | `app/data/exchangers/UserDataExchanger.java:96` | `@Override` |
| GL-data_exchangers_UserDataExchanger-017 | `app/data/exchangers/UserDataExchanger.java:102` | `@Override` |
| GL-data_exchangers_UserDataExchanger-018 | `app/data/exchangers/UserDataExchanger.java:109` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-001 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-002 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:37` | `private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-003 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:39` | `private static final String USER_ID = "user_id"; // INTEGER(10) NOT NULL` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-004 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-005 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-006 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-007 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-008 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-009 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-001 | `app/data/exchangers/PullRequestEventDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PullRequestEventDataExchanger-002 | `app/data/exchangers/PullRequestEventDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_PullRequestEventDataExchanger-003 | `app/data/exchangers/PullRequestEventDataExchanger.java:39` | `private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestEventDataExchanger-004 | `app/data/exchangers/PullRequestEventDataExchanger.java:41` | `private static final String CREATED = "created"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestEventDataExchanger-005 | `app/data/exchangers/PullRequestEventDataExchanger.java:43` | `private static final String SENDER_LOGIN_ID = "sender_login_id"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestEventDataExchanger-006 | `app/data/exchangers/PullRequestEventDataExchanger.java:45` | `private static final String EVENT_TYPE = "event_type"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestEventDataExchanger-007 | `app/data/exchangers/PullRequestEventDataExchanger.java:47` | `private static final String NEW_VALUE = "new_value"; // CLOB(2147483647)` |
| GL-data_exchangers_PullRequestEventDataExchanger-008 | `app/data/exchangers/PullRequestEventDataExchanger.java:49` | `private static final String OLD_VALUE = "old_value"; // CLOB(2147483647)` |
| GL-data_exchangers_PullRequestEventDataExchanger-009 | `app/data/exchangers/PullRequestEventDataExchanger.java:52` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-010 | `app/data/exchangers/PullRequestEventDataExchanger.java:65` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-011 | `app/data/exchangers/PullRequestEventDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-012 | `app/data/exchangers/PullRequestEventDataExchanger.java:84` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-013 | `app/data/exchangers/PullRequestEventDataExchanger.java:91` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-001 | `app/data/exchangers/UnwatchDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UnwatchDataExchanger-006 | `app/data/exchangers/UnwatchDataExchanger.java:45` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-007 | `app/data/exchangers/UnwatchDataExchanger.java:55` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-008 | `app/data/exchangers/UnwatchDataExchanger.java:65` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-009 | `app/data/exchangers/UnwatchDataExchanger.java:71` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-010 | `app/data/exchangers/UnwatchDataExchanger.java:77` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-001 | `app/data/exchangers/RoleDataExchanger.java:31` | `/**` |
| GL-data_exchangers_RoleDataExchanger-005 | `app/data/exchangers/RoleDataExchanger.java:44` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-006 | `app/data/exchangers/RoleDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-007 | `app/data/exchangers/RoleDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-008 | `app/data/exchangers/RoleDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-009 | `app/data/exchangers/RoleDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-001 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-002 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:37` | `private static final String ISSUE_ID = "issue_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-003 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:39` | `private static final String ISSUE_LABEL_ID = "issue_label_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-004 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-005 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-006 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-007 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-008 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-009 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-001 | `app/data/exchangers/IssueEventDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueEventDataExchanger-010 | `app/data/exchangers/IssueEventDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-011 | `app/data/exchangers/IssueEventDataExchanger.java:67` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-012 | `app/data/exchangers/IssueEventDataExchanger.java:81` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-013 | `app/data/exchangers/IssueEventDataExchanger.java:87` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-014 | `app/data/exchangers/IssueEventDataExchanger.java:95` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-001 | `app/data/exchangers/ReviewCommentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ReviewCommentDataExchanger-009 | `app/data/exchangers/ReviewCommentDataExchanger.java:51` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-010 | `app/data/exchangers/ReviewCommentDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-011 | `app/data/exchangers/ReviewCommentDataExchanger.java:77` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-012 | `app/data/exchangers/ReviewCommentDataExchanger.java:83` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-013 | `app/data/exchangers/ReviewCommentDataExchanger.java:90` | `@Override` |
| GL-actions_GuestProhibitAction-001 | `app/actions/GuestProhibitAction.java:21` | `/**` |
| GL-actions_GuestProhibitAction-002 | `app/actions/GuestProhibitAction.java:31` | `@Override` |
| GL-actions_NullProjectCheckAction-001 | `app/actions/NullProjectCheckAction.java:39` | `/**` |
| GL-actions_NullProjectCheckAction-002 | `app/actions/NullProjectCheckAction.java:49` | `@Override` |
| GL-actions_IsOnlyGitAvailableAction-001 | `app/actions/IsOnlyGitAvailableAction.java:34` | `/**` |
| GL-actions_IsAllowedAction-001 | `app/actions/IsAllowedAction.java:39` | `/**` |
| GL-actions_IsAllowedAction-002 | `app/actions/IsAllowedAction.java:52` | `@Override` |
| GL-actions_AbstractProjectCheckAction-001 | `app/actions/AbstractProjectCheckAction.java:39` | `/**` |
| GL-actions_AbstractProjectCheckAction-002 | `app/actions/AbstractProjectCheckAction.java:50` | `@Override` |
| GL-actions_AbstractProjectCheckAction-003 | `app/actions/AbstractProjectCheckAction.java:99` | `protected abstract Promise<Result> call(Project project, Context context, PathParser parser)` |
| GL-actions_AnonymousCheckAction-001 | `app/actions/AnonymousCheckAction.java:36` | `/**` |
| GL-actions_AnonymousCheckAction-002 | `app/actions/AnonymousCheckAction.java:46` | `@Override` |
| GL-actions_IsCreatableAction-001 | `app/actions/IsCreatableAction.java:37` | `/**` |
| GL-actions_IsCreatableAction-002 | `app/actions/IsCreatableAction.java:47` | `@Override` |
| GL-actions_CodeAccessCheckAction-001 | `app/actions/CodeAccessCheckAction.java:18` | `public class CodeAccessCheckAction extends AbstractProjectCheckAction<Void> {` |
| GL-actions_CodeAccessCheckAction-002 | `app/actions/CodeAccessCheckAction.java:20` | `@Override` |
| GL-actions_DefaultProjectCheckAction-001 | `app/actions/DefaultProjectCheckAction.java:31` | `/**` |
| GL-actions_DefaultProjectCheckAction-002 | `app/actions/DefaultProjectCheckAction.java:40` | `@Override` |
| GL-actions_support_PathParser-001 | `app/actions/support/PathParser.java:30` | `/**` |
| GL-actions_support_PathParser-004 | `app/actions/support/PathParser.java:44` | `public PathParser(String path) {` |
| GL-actions_support_PathParser-005 | `app/actions/support/PathParser.java:52` | `public PathParser(String contextPath, String path) {` |
| GL-actions_support_PathParser-006 | `app/actions/support/PathParser.java:61` | `public PathParser(Http.Context context) {` |
| GL-actions_support_PathParser-010 | `app/actions/support/PathParser.java:81` | `public String toString() {` |
| GL-actions_support_PathParser-011 | `app/actions/support/PathParser.java:86` | `public String restOfPathExceptOwnerAndProjectName() {` |
| GL-actors_RelatedPullRequestMergingActor-001 | `app/actors/RelatedPullRequestMergingActor.java:29` | `public class RelatedPullRequestMergingActor extends PullRequestActor {` |
| GL-actors_RelatedPullRequestMergingActor-002 | `app/actors/RelatedPullRequestMergingActor.java:31` | `@Override` |
| GL-actors_RelatedPullRequestMergingActor-003 | `app/actors/RelatedPullRequestMergingActor.java:47` | `private void changeStateToMerging(List<PullRequest> pullRequests) {` |
| GL-actors_RelatedPullRequestMergingActor-004 | `app/actors/RelatedPullRequestMergingActor.java:55` | `private void processPullRequests(PullRequestEventMessage message, List<PullRequest> pullRequests) {` |
| GL-actors_PullRequestMergingActor-001 | `app/actors/PullRequestMergingActor.java:27` | `/**` |
| GL-actors_PullRequestMergingActor-002 | `app/actors/PullRequestMergingActor.java:33` | `@Override` |
| GL-actors_IssueReferredFromCommitEventActor-001 | `app/actors/IssueReferredFromCommitEventActor.java:35` | `/**` |
| GL-actors_IssueReferredFromCommitEventActor-002 | `app/actors/IssueReferredFromCommitEventActor.java:43` | `@Override` |
| GL-actors_IssueReferredFromCommitEventActor-003 | `app/actors/IssueReferredFromCommitEventActor.java:52` | `private void addIssueEvent(RevCommit commit, Project project, User user) {` |
| GL-actors_PostReceiveActor-001 | `app/actors/PostReceiveActor.java:39` | `/**` |
| GL-actors_PostReceiveActor-002 | `app/actors/PostReceiveActor.java:50` | `@Override` |
| GL-actors_PostReceiveActor-003 | `app/actors/PostReceiveActor.java:61` | `abstract void doReceive(PostReceiveMessage cap);` |
| GL-actors_PostReceiveActor-004 | `app/actors/PostReceiveActor.java:64` | `class CommitAndRefNames {` |
| GL-actors_PostReceiveActor-005 | `app/actors/PostReceiveActor.java:95` | `protected CommitAndRefNames commitAndRefNames(PostReceiveMessage message) {` |
| GL-actors_PostReceiveActor-007 | `app/actors/PostReceiveActor.java:116` | `protected Collection<? extends RevCommit> parseCommitsFrom(ReceiveCommand command, Project project) ` |
| GL-actors_CommitsNotificationActor-001 | `app/actors/CommitsNotificationActor.java:18` | `/**` |
| GL-actors_CommitsNotificationActor-002 | `app/actors/CommitsNotificationActor.java:24` | `@Override` |
| GL-actors_PullRequestActor-001 | `app/actors/PullRequestActor.java:29` | `public abstract class PullRequestActor extends UntypedActor {` |
| GL-actors_PullRequestActor-002 | `app/actors/PullRequestActor.java:32` | `protected void processPullRequestMerging(PullRequestEventMessage message, PullRequest pullRequest) {` |
| GL-actors_ValidationEmailSender-001 | `app/actors/ValidationEmailSender.java:33` | `/**` |
| GL-actors_ValidationEmailSender-002 | `app/actors/ValidationEmailSender.java:39` | `@Override` |
| GL-validation_ExConstraints-001 | `app/validation/ExConstraints.java:37` | `public class ExConstraints {` |
| GL-validation_ExConstraints-002 | `app/validation/ExConstraints.java:39` | `@Target({ElementType.FIELD})` |
| GL-validation_ExConstraints-003 | `app/validation/ExConstraints.java:52` | `/**` |
| GL-errors_PullRequestException-001 | `app/errors/PullRequestException.java:24` | `public class PullRequestException extends Exception {` |
| GL-errors_PullRequestException-003 | `app/errors/PullRequestException.java:30` | `public PullRequestException(String message) {` |
| GL-models_AuthInfo-001 | `app/models/AuthInfo.java:26` | `public class AuthInfo {` |
| GL-models_AuthInfo-002 | `app/models/AuthInfo.java:28` | `@Constraints.Required` |
| GL-models_AuthInfo-003 | `app/models/AuthInfo.java:31` | `@Constraints.Required` |
| GL-models_AuthInfo-004 | `app/models/AuthInfo.java:35` | `/**` |
| GL-models_Statistics-001 | `app/models/Statistics.java:4` | `public class Statistics {` |
| GL-models_Statistics-009 | `app/models/Statistics.java:21` | `private static final Statistics EMPTY = new Statistics();` |
| GL-models_Statistics-010 | `app/models/Statistics.java:24` | `public static Statistics empty() {` |
| GL-models_Statistics-011 | `app/models/Statistics.java:29` | `public Statistics() {` |
| GL-models_RecentProject-001 | `app/models/RecentProject.java:15` | `@Entity` |
| GL-models_RecentProject-004 | `app/models/RecentProject.java:24` | `public static Finder<Long, RecentProject> find = new Finder<>(Long.class, RecentProject.class);` |
| GL-models_RecentProject-005 | `app/models/RecentProject.java:27` | `@Id` |
| GL-models_RecentProject-010 | `app/models/RecentProject.java:40` | `public RecentProject(User user, Project project) {` |
| GL-models_RecentProject-012 | `app/models/RecentProject.java:66` | `public static void addNew(final User user, final Project project){` |
| GL-models_RecentProject-013 | `app/models/RecentProject.java:78` | `@Transactional` |
| GL-models_RecentProject-014 | `app/models/RecentProject.java:95` | `public static void deletePrevious(User user, Project project) {` |
| GL-models_RecentProject-015 | `app/models/RecentProject.java:106` | `private static void deleteOldestIfOverflow(User user) {` |
| GL-models_RecentProject-016 | `app/models/RecentProject.java:123` | `public static void deleteAll(User user) {` |
| GL-models_RecentProject-017 | `app/models/RecentProject.java:132` | `@Override` |
| GL-models_MailRecipient-001 | `app/models/MailRecipient.java:27` | `/**` |
| GL-models_MailRecipient-002 | `app/models/MailRecipient.java:32` | `@Nonnull` |
| GL-models_MailRecipient-003 | `app/models/MailRecipient.java:35` | `@Nullable` |
| GL-models_MailRecipient-004 | `app/models/MailRecipient.java:39` | `public MailRecipient(String email, String name) {` |
| GL-models_IssueLabel-001 | `app/models/IssueLabel.java:38` | `@Entity` |
| GL-models_IssueLabel-002 | `app/models/IssueLabel.java:42` | `static public class IssueLabelException extends Exception {` |
| GL-models_IssueLabel-004 | `app/models/IssueLabel.java:52` | `public static final Finder<Long, IssueLabel> finder = new Finder<>(Long.class, IssueLabel.class);` |
| GL-models_IssueLabel-005 | `app/models/IssueLabel.java:55` | `@Id` |
| GL-models_IssueLabel-006 | `app/models/IssueLabel.java:59` | `@Required` |
| GL-models_IssueLabel-007 | `app/models/IssueLabel.java:64` | `@Required(message="label.error.color.empty")` |
| GL-models_IssueLabel-008 | `app/models/IssueLabel.java:68` | `@Required(message="label.error.labelName.empty")` |
| GL-models_IssueLabel-009 | `app/models/IssueLabel.java:73` | `@ManyToOne` |
| GL-models_IssueLabel-010 | `app/models/IssueLabel.java:77` | `@ManyToMany(mappedBy="labels", fetch = FetchType.EAGER)` |
| GL-models_IssueLabel-011 | `app/models/IssueLabel.java:81` | `@ManyToMany(mappedBy="labels", fetch = FetchType.EAGER)` |
| GL-models_IssueLabel-012 | `app/models/IssueLabel.java:85` | `public static List<IssueLabel> findByProject(Project project) {` |
| GL-models_IssueLabel-013 | `app/models/IssueLabel.java:94` | `public static void copyIssueLabels(Project fromProject, Project toProject){` |
| GL-models_IssueLabel-014 | `app/models/IssueLabel.java:111` | `/**` |
| GL-models_IssueLabel-015 | `app/models/IssueLabel.java:129` | `/**` |
| GL-models_IssueLabel-016 | `app/models/IssueLabel.java:151` | `public String toString() {` |
| GL-models_IssueLabel-017 | `app/models/IssueLabel.java:156` | `@Transient` |
| GL-models_IssueLabel-018 | `app/models/IssueLabel.java:166` | `@Transient` |
| GL-models_IssueLabel-019 | `app/models/IssueLabel.java:181` | `@Transient` |
| GL-models_IssueLabel-020 | `app/models/IssueLabel.java:196` | `@Override` |
| GL-models_IssueLabel-021 | `app/models/IssueLabel.java:206` | `@Override` |
| GL-models_IssueLabel-022 | `app/models/IssueLabel.java:227` | `@Override` |
| GL-models_IssueLabel-023 | `app/models/IssueLabel.java:243` | `@Override` |
| GL-models_IssueMassUpdate-001 | `app/models/IssueMassUpdate.java:31` | `public class IssueMassUpdate {` |
| GL-models_IssueMassUpdate-006 | `app/models/IssueMassUpdate.java:41` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_IssueMassUpdate-008 | `app/models/IssueMassUpdate.java:47` | `@Constraints.Required` |
| GL-models_WebhookThread-001 | `app/models/WebhookThread.java:25` | `@Entity` |
| GL-models_WebhookThread-003 | `app/models/WebhookThread.java:30` | `public static Finder<Long, WebhookThread> find = new Finder<>(Long.class, WebhookThread.class);` |
| GL-models_WebhookThread-004 | `app/models/WebhookThread.java:33` | `@Id` |
| GL-models_WebhookThread-005 | `app/models/WebhookThread.java:37` | `@ManyToOne` |
| GL-models_WebhookThread-006 | `app/models/WebhookThread.java:41` | `@Required` |
| GL-models_WebhookThread-007 | `app/models/WebhookThread.java:46` | `@Required` |
| GL-models_WebhookThread-008 | `app/models/WebhookThread.java:50` | `@Required` |
| GL-models_WebhookThread-010 | `app/models/WebhookThread.java:58` | `public WebhookThread(Long webhookId, Resource resource, String threadId) {` |
| GL-models_WebhookThread-011 | `app/models/WebhookThread.java:67` | `public static WebhookThread create(Long webhookId, Resource resource, String threadId) {` |
| GL-models_WebhookThread-013 | `app/models/WebhookThread.java:84` | `@Override` |
| GL-models_PullRequestMergeResult-001 | `app/models/PullRequestMergeResult.java:30` | `public class PullRequestMergeResult {` |
| GL-models_PullRequestMergeResult-009 | `app/models/PullRequestMergeResult.java:55` | `public boolean hasDiffCommits() {` |
| GL-models_PullRequestMergeResult-010 | `app/models/PullRequestMergeResult.java:60` | `public boolean conflicts() {` |
| GL-models_PullRequestMergeResult-012 | `app/models/PullRequestMergeResult.java:70` | `public List<PullRequestCommit> findNewCommits() {` |
| GL-models_PullRequestMergeResult-013 | `app/models/PullRequestMergeResult.java:95` | `/**` |
| GL-models_PullRequestMergeResult-014 | `app/models/PullRequestMergeResult.java:106` | `public void saveCommits() {` |
| GL-models_PullRequestMergeResult-015 | `app/models/PullRequestMergeResult.java:113` | `public void saveNewCommits() {` |
| GL-models_PullRequestMergeResult-016 | `app/models/PullRequestMergeResult.java:120` | `public void updatePriorCommits() {` |
| GL-models_Search-001 | `app/models/Search.java:37` | `public class Search {` |
| GL-models_Search-004 | `app/models/Search.java:45` | `private static JunctionOperation<Issue> containsKeywordInIssue = new JunctionOperation<Issue>() {` |
| GL-models_Search-005 | `app/models/Search.java:53` | `private static JunctionOperation<Posting> containsKeywordInPosting = new JunctionOperation<Posting>(` |
| GL-models_Search-006 | `app/models/Search.java:61` | `private static JunctionOperation<Milestone> containsKeywordInMilestone = new JunctionOperation<Miles` |
| GL-models_Search-007 | `app/models/Search.java:69` | `private static JunctionOperation<IssueComment> containsKeywordInIssueComment = new JunctionOperation` |
| GL-models_Search-008 | `app/models/Search.java:77` | `private static JunctionOperation<PostingComment> containsKeywordInPostComment = new JunctionOperatio` |
| GL-models_Search-009 | `app/models/Search.java:85` | `private static JunctionOperation<ReviewComment> containsKeywordInReviewComment = new JunctionOperati` |
| GL-models_Search-010 | `app/models/Search.java:93` | `/**` |
| GL-models_Search-011 | `app/models/Search.java:113` | `/**` |
| GL-models_Search-012 | `app/models/Search.java:125` | `/**` |
| GL-models_Search-013 | `app/models/Search.java:144` | `/**` |
| GL-models_Search-014 | `app/models/Search.java:164` | `/**` |
| GL-models_Search-015 | `app/models/Search.java:178` | `private static ExpressionList<Issue> issuesEL(String keyword, User user, Project project) {` |
| GL-models_Search-016 | `app/models/Search.java:192` | `/**` |
| GL-models_Search-017 | `app/models/Search.java:211` | `/**` |
| GL-models_Search-018 | `app/models/Search.java:225` | `private static ExpressionList<Issue> issuesEL(String keyword, User user, Organization organization) ` |
| GL-models_Search-019 | `app/models/Search.java:238` | `/**` |
| GL-models_Search-020 | `app/models/Search.java:256` | `/**` |
| GL-models_Search-021 | `app/models/Search.java:268` | `private static ExpressionList<Posting> postsEL(String keyword, User user) {` |
| GL-models_Search-022 | `app/models/Search.java:279` | `/**` |
| GL-models_Search-023 | `app/models/Search.java:298` | `/**` |
| GL-models_Search-024 | `app/models/Search.java:311` | `private static ExpressionList<Posting> postsEL(String keyword, User user, Project project) {` |
| GL-models_Search-025 | `app/models/Search.java:323` | `/**` |
| GL-models_Search-026 | `app/models/Search.java:342` | `/**` |
| GL-models_Search-027 | `app/models/Search.java:356` | `private static ExpressionList<Posting> postsEL(String keyword, User user, Organization organization)` |
| GL-models_Search-028 | `app/models/Search.java:368` | `/**` |
| GL-models_Search-029 | `app/models/Search.java:380` | `/**` |
| GL-models_Search-030 | `app/models/Search.java:391` | `private static ExpressionList<User> usersEL(String keyword) {` |
| GL-models_Search-031 | `app/models/Search.java:403` | `/**` |
| GL-models_Search-032 | `app/models/Search.java:416` | `/**` |
| GL-models_Search-033 | `app/models/Search.java:429` | `private static ExpressionList<User> usersEL(String keyword, Project project) {` |
| GL-models_Search-034 | `app/models/Search.java:442` | `/**` |
| GL-models_Search-035 | `app/models/Search.java:455` | `/**` |
| GL-models_Search-036 | `app/models/Search.java:467` | `private static ExpressionList<User> usersEL(String keyword, Organization organization) {` |
| GL-models_Search-037 | `app/models/Search.java:480` | `/**` |
| GL-models_Search-038 | `app/models/Search.java:493` | `/**` |
| GL-models_Search-039 | `app/models/Search.java:511` | `/**` |
| GL-models_Search-040 | `app/models/Search.java:526` | `/**` |
| GL-models_Search-041 | `app/models/Search.java:539` | `private static ExpressionList<Project> projectsEL(String keyword, User user) {` |
| GL-models_Search-042 | `app/models/Search.java:570` | `public static Page<Milestone> findMilestones(String keyword, User user, PageParam pageParam) {` |
| GL-models_Search-043 | `app/models/Search.java:575` | `public static int countMilestones(String keyword, User user) {` |
| GL-models_Search-044 | `app/models/Search.java:580` | `private static ExpressionList<Milestone> milestonesEL(String keyword, User user) {` |
| GL-models_Search-045 | `app/models/Search.java:590` | `public static Page<Milestone> findMilestones(String keyword, User user, Project project, PageParam p` |
| GL-models_Search-047 | `app/models/Search.java:606` | `private static ExpressionList<Milestone> milestonesEL(String keyword, Project project) {` |
| GL-models_Search-048 | `app/models/Search.java:617` | `public static Page<Milestone> findMilestones(String keyword, User user, Organization organization, P` |
| GL-models_Search-049 | `app/models/Search.java:622` | `public static int countMilestones(String keyword, User user, Organization organization) {` |
| GL-models_Search-050 | `app/models/Search.java:627` | `private static ExpressionList<Milestone> milestonesEL(String keyword, User user, Organization organi` |
| GL-models_Search-051 | `app/models/Search.java:638` | `public static Page<IssueComment> findIssueComments(String keyword, User user, PageParam pageParam) {` |
| GL-models_Search-052 | `app/models/Search.java:643` | `public static int countIssueComments(String keyword, User user) {` |
| GL-models_Search-053 | `app/models/Search.java:648` | `private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user) {` |
| GL-models_Search-054 | `app/models/Search.java:659` | `public static Page<IssueComment> findIssueComments(String keyword, User user, Project project, PageP` |
| GL-models_Search-055 | `app/models/Search.java:664` | `public static int countIssueComments(String keyword, User user, Project project) {` |
| GL-models_Search-056 | `app/models/Search.java:669` | `private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user, Project proje` |
| GL-models_Search-057 | `app/models/Search.java:681` | `public static Page<IssueComment> findIssueComments(String keyword, User user, Organization organizat` |
| GL-models_Search-058 | `app/models/Search.java:686` | `public static int countIssueComments(String keyword, User user, Organization organization) {` |
| GL-models_Search-059 | `app/models/Search.java:691` | `private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user, Organization ` |
| GL-models_Search-060 | `app/models/Search.java:703` | `public static Page<PostingComment> findPostComments(String keyword, User user, PageParam pageParam) ` |
| GL-models_Search-063 | `app/models/Search.java:724` | `public static Page<PostingComment> findPostComments(String keyword, User user, Project project, Page` |
| GL-models_Search-064 | `app/models/Search.java:729` | `public static int countPostComments(String keyword, User user, Project project) {` |
| GL-models_Search-065 | `app/models/Search.java:734` | `private static ExpressionList<PostingComment> postCommentsEL(String keyword, User user, Project proj` |
| GL-models_Search-066 | `app/models/Search.java:747` | `public static Page<PostingComment> findPostComments(String keyword, User user, Organization organiza` |
| GL-models_Search-067 | `app/models/Search.java:752` | `public static int countPostComments(String keyword, User user, Organization organization) {` |
| GL-models_Search-068 | `app/models/Search.java:757` | `private static ExpressionList<PostingComment> postCommentsEL(String keyword, User user, Organization` |
| GL-models_Search-069 | `app/models/Search.java:769` | `public static Page<ReviewComment> findReviews(String keyword, User user, PageParam pageParam) {` |
| GL-models_Search-070 | `app/models/Search.java:774` | `public static int countReviews(String keyword, User user) {` |
| GL-models_Search-071 | `app/models/Search.java:779` | `private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user) {` |
| GL-models_Search-072 | `app/models/Search.java:790` | `public static Page<ReviewComment> findReviews(String keyword, User user, Project project, PageParam ` |
| GL-models_Search-073 | `app/models/Search.java:795` | `public static int countReviews(String keyword, User user, Project project) {` |
| GL-models_Search-074 | `app/models/Search.java:800` | `private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user, Project project) {` |
| GL-models_Search-075 | `app/models/Search.java:812` | `public static Page<ReviewComment> findReviews(String keyword, User user, Organization organization, ` |
| GL-models_Search-076 | `app/models/Search.java:817` | `public static int countReviews(String keyword, User user, Organization organization) {` |
| GL-models_Search-077 | `app/models/Search.java:822` | `private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user, Organization organ` |
| GL-models_Search-078 | `app/models/Search.java:834` | `interface JunctionOperation<T> {` |
| GL-models_Search-079 | `app/models/Search.java:839` | `private static <T> void containsKeywordIn(String keyword, Junction<T> junction, String[] fields) {` |
| GL-models_Search-080 | `app/models/Search.java:848` | `private static <T> void inProjectsTemplate(String keyword, User user, Organization organization, Jun` |
| GL-models_Search-081 | `app/models/Search.java:877` | `private static <T> void inProjectsTemplate(String keyword, User user, Junction<T> junction, String p` |
| GL-models_Search-082 | `app/models/Search.java:904` | `private static <T> void equalsUserTemplate(String keyword, User user, Junction<T> junction, String p` |
| GL-models_Search-083 | `app/models/Search.java:917` | `private static <T> Page<T> emptyPage() {` |
| GL-models_LabelOwner-001 | `app/models/LabelOwner.java:28` | `/**` |
| GL-models_Comment-001 | `app/models/Comment.java:28` | `@MappedSuperclass` |
| GL-models_Comment-003 | `app/models/Comment.java:34` | `@Id` |
| GL-models_Comment-004 | `app/models/Comment.java:38` | `@Lob @Constraints.Required` |
| GL-models_Comment-005 | `app/models/Comment.java:42` | `@Constraints.Required` |
| GL-models_Comment-010 | `app/models/Comment.java:55` | `@Transient` |
| GL-models_Comment-011 | `app/models/Comment.java:59` | `@Transient` |
| GL-models_Comment-012 | `app/models/Comment.java:63` | `public Comment() {` |
| GL-models_Comment-013 | `app/models/Comment.java:68` | `public Comment(User author, String contents) {` |
| GL-models_Comment-014 | `app/models/Comment.java:75` | `public Duration ago() {` |
| GL-models_Comment-015 | `app/models/Comment.java:80` | `@Override` |
| GL-models_Comment-017 | `app/models/Comment.java:87` | `@Transient` |
| GL-models_Comment-018 | `app/models/Comment.java:95` | `@Transactional` |
| GL-models_Comment-019 | `app/models/Comment.java:103` | `@Transactional` |
| GL-models_Comment-020 | `app/models/Comment.java:110` | `protected void updateMention() {` |
| GL-models_Comment-021 | `app/models/Comment.java:115` | `public void delete() {` |
| GL-models_Comment-022 | `app/models/Comment.java:123` | `public static Comparator<Comment> comparator(){` |
| GL-models_Comment-028 | `app/models/Comment.java:147` | `@Override` |
| GL-models_Comment-029 | `app/models/Comment.java:171` | `@Override` |
| GL-models_Comment-031 | `app/models/Comment.java:190` | `@Override` |
| GL-models_NullUser-001 | `app/models/NullUser.java:33` | `public class NullUser extends User {` |
| GL-models_NullUser-003 | `app/models/NullUser.java:38` | `public NullUser(){` |
| GL-models_NullUser-004 | `app/models/NullUser.java:47` | `public List<Project> myProjects(){` |
| GL-models_NullUser-006 | `app/models/NullUser.java:57` | `@Override` |
| GL-models_NullUser-008 | `app/models/NullUser.java:78` | `@Override` |
| GL-models_Mention-001 | `app/models/Mention.java:26` | `@Entity` |
| GL-models_Mention-003 | `app/models/Mention.java:32` | `public static final Finder<Long, Mention> find = new Finder<>(Long.class, Mention.class);` |
| GL-models_SimpleCommentThread-001 | `app/models/SimpleCommentThread.java:27` | `/**` |
| GL-models_Issue-001 | `app/models/Issue.java:71` | `@Entity` |
| GL-models_Issue-003 | `app/models/Issue.java:78` | `public static final Finder<Long, Issue> finder = new Finder<>(Long.class, Issue.class);` |
| GL-models_Issue-006 | `app/models/Issue.java:85` | `public static final Pattern ISSUE_PATTERN = Pattern.compile("#\\d+");` |
| GL-models_Issue-008 | `app/models/Issue.java:91` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_Issue-009 | `app/models/Issue.java:95` | `public static final List<State> availableStates =` |
| GL-models_Issue-010 | `app/models/Issue.java:99` | `@ManyToOne` |
| GL-models_Issue-011 | `app/models/Issue.java:103` | `@ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)` |
| GL-models_Issue-012 | `app/models/Issue.java:107` | `@ManyToOne` |
| GL-models_Issue-013 | `app/models/Issue.java:111` | `@OneToMany(cascade = CascadeType.ALL, mappedBy="issue")` |
| GL-models_Issue-014 | `app/models/Issue.java:115` | `@OneToMany(cascade = CascadeType.ALL, mappedBy="issue")` |
| GL-models_Issue-015 | `app/models/Issue.java:119` | `@OneToMany(cascade = CascadeType.ALL, mappedBy = "issue")` |
| GL-models_Issue-016 | `app/models/Issue.java:123` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_Issue-017 | `app/models/Issue.java:132` | `@Transient` |
| GL-models_Issue-018 | `app/models/Issue.java:137` | `@Transient` |
| GL-models_Issue-019 | `app/models/Issue.java:142` | `public Issue(Project project, User author, String title, String body) {` |
| GL-models_Issue-020 | `app/models/Issue.java:148` | `@Transient` |
| GL-models_Issue-021 | `app/models/Issue.java:152` | `@Transient` |
| GL-models_Issue-022 | `app/models/Issue.java:156` | `@Transient` |
| GL-models_Issue-023 | `app/models/Issue.java:160` | `@OneToOne` |
| GL-models_Issue-026 | `app/models/Issue.java:170` | `public Issue() {` |
| GL-models_Issue-027 | `app/models/Issue.java:175` | `/**` |
| GL-models_Issue-028 | `app/models/Issue.java:183` | `/**` |
| GL-models_Issue-029 | `app/models/Issue.java:192` | `protected void fixLastNumber() {` |
| GL-models_Issue-030 | `app/models/Issue.java:197` | `public String assigneeName() {` |
| GL-models_Issue-031 | `app/models/Issue.java:202` | `public Long milestoneId() {` |
| GL-models_Issue-032 | `app/models/Issue.java:210` | `public boolean hasAssignee() {` |
| GL-models_Issue-033 | `app/models/Issue.java:215` | `/**` |
| GL-models_Issue-034 | `app/models/Issue.java:225` | `/**` |
| GL-models_Issue-035 | `app/models/Issue.java:235` | `public void checkLabels() throws IssueLabel.IssueLabelException {` |
| GL-models_Issue-036 | `app/models/Issue.java:251` | `@Override` |
| GL-models_Issue-037 | `app/models/Issue.java:268` | `/**` |
| GL-models_Issue-038 | `app/models/Issue.java:278` | `public static int countAllAssignedBy(User user) {` |
| GL-models_Issue-039 | `app/models/Issue.java:288` | `public static int countVoterOf(User user) {` |
| GL-models_Issue-040 | `app/models/Issue.java:300` | `public static int countAllCreatedBy(User user) {` |
| GL-models_Issue-041 | `app/models/Issue.java:305` | `public static int countIssues(Long projectId, State state) {` |
| GL-models_Issue-042 | `app/models/Issue.java:314` | `public static int countIssuesBy(Long projectId, SearchCondition cond) {` |
| GL-models_Issue-043 | `app/models/Issue.java:319` | `public static int countIssuesBy(SearchCondition cond) {` |
| GL-models_Issue-044 | `app/models/Issue.java:324` | `public static int countIssuesBy(Long projectId, Map<String, String> paramMap) {` |
| GL-models_Issue-045 | `app/models/Issue.java:332` | `public static int countIssuesBy(Organization organization, SearchCondition cond) {` |
| GL-models_Issue-046 | `app/models/Issue.java:337` | `/**` |
| GL-models_Issue-055 | `app/models/Issue.java:470` | `@Override` |
| GL-models_Issue-056 | `app/models/Issue.java:476` | `public Resource fieldAsResource(final ResourceType resourceType) {` |
| GL-models_Issue-057 | `app/models/Issue.java:501` | `public Resource stateAsResource() {` |
| GL-models_Issue-058 | `app/models/Issue.java:506` | `public Resource milestoneAsResource() {` |
| GL-models_Issue-059 | `app/models/Issue.java:511` | `public Resource assigneeAsResource() {` |
| GL-models_Issue-060 | `app/models/Issue.java:516` | `public static List<Issue> findRecentlyCreated(Project project, int size) {` |
| GL-models_Issue-062 | `app/models/Issue.java:534` | `public static Issue findByNumber(Project project, Long number) {` |
| GL-models_Issue-063 | `app/models/Issue.java:539` | `public static List<Issue> findByMilestone(Milestone milestone) {` |
| GL-models_Issue-064 | `app/models/Issue.java:544` | `public static List<Issue> findClosedIssuesByMilestone(Milestone milestone) {` |
| GL-models_Issue-065 | `app/models/Issue.java:549` | `public static List<Issue> findOpenIssuesByMilestone(Milestone milestone) {` |
| GL-models_Issue-066 | `app/models/Issue.java:554` | `@Transient` |
| GL-models_Issue-067 | `app/models/Issue.java:560` | `/**` |
| GL-models_Issue-068 | `app/models/Issue.java:577` | `public boolean assignedUserEquals(Assignee otherAssignee) {` |
| GL-models_Issue-069 | `app/models/Issue.java:588` | `/**` |
| GL-models_Issue-070 | `app/models/Issue.java:601` | `public static List<Issue> findByProject(Project project, String filter) {` |
| GL-models_Issue-071 | `app/models/Issue.java:611` | `public static List<Issue> findByProject(Project project, String filter, int limit) {` |
| GL-models_Issue-072 | `app/models/Issue.java:621` | `public static List<Issue> findParentIssueByProject(Project project, String filter, int limit) {` |
| GL-models_Issue-073 | `app/models/Issue.java:632` | `public static Page<Issue> findIssuesByState(int size, int pageNum, State state) {` |
| GL-models_Issue-074 | `app/models/Issue.java:639` | `public State previousState() {` |
| GL-models_Issue-076 | `app/models/Issue.java:654` | `public State nextState() {` |
| GL-models_Issue-078 | `app/models/Issue.java:669` | `public State toNextState(){` |
| GL-models_Issue-079 | `app/models/Issue.java:677` | `@Override` |
| GL-models_Issue-082 | `app/models/Issue.java:703` | `public boolean canBeDeleted() {` |
| GL-models_Issue-083 | `app/models/Issue.java:718` | `/**` |
| GL-models_Issue-084 | `app/models/Issue.java:730` | `/**` |
| GL-models_Issue-085 | `app/models/Issue.java:742` | `/**` |
| GL-models_Issue-088 | `app/models/Issue.java:767` | `public String until(){` |
| GL-models_Issue-089 | `app/models/Issue.java:784` | `public static int countOpenIssuesByLabel(Project project, IssueLabel label) {` |
| GL-models_Issue-090 | `app/models/Issue.java:793` | `public static int countOpenIssuesByAssignee(Project project, Assignee assignee) {` |
| GL-models_Issue-091 | `app/models/Issue.java:802` | `public static int countOpenIssuesByMilestone(Project project, Milestone milestone) {` |
| GL-models_Issue-092 | `app/models/Issue.java:811` | `public static List<Issue> findByParentIssueId(Long parentIssueId){` |
| GL-models_Issue-093 | `app/models/Issue.java:818` | `public boolean hasChildIssue(){` |
| GL-models_Issue-094 | `app/models/Issue.java:827` | `public boolean hasParentIssue(){` |
| GL-models_Issue-095 | `app/models/Issue.java:834` | `public static List<Issue> findByParentIssueIdAndState(Long parentIssueId, State state){` |
| GL-models_Issue-096 | `app/models/Issue.java:843` | `public static int countByParentIssueIdAndState(Long parentIssueId, State state){` |
| GL-models_Issue-097 | `app/models/Issue.java:851` | `public static int countOpenIssuesByUser(User user) {` |
| GL-models_Issue-098 | `app/models/Issue.java:859` | `public IssueSharer findSharerByUserId(Long id){` |
| GL-models_Issue-099 | `app/models/Issue.java:869` | `public IssueComment findCommentByCommentId(Long id) {` |
| GL-models_Issue-102 | `app/models/Issue.java:892` | `public static Issue from(Posting posting) {` |
| GL-models_NonRangedCodeCommentThread-001 | `app/models/NonRangedCodeCommentThread.java:29` | `/**` |
| GL-models_OrganizationUser-001 | `app/models/OrganizationUser.java:32` | `@Entity` |
| GL-models_OrganizationUser-003 | `app/models/OrganizationUser.java:39` | `public static final Finder<Long, OrganizationUser> find = new Finder<>(Long.class, OrganizationUser.` |
| GL-models_OrganizationUser-004 | `app/models/OrganizationUser.java:42` | `@Id` |
| GL-models_OrganizationUser-005 | `app/models/OrganizationUser.java:46` | `@ManyToOne` |
| GL-models_OrganizationUser-006 | `app/models/OrganizationUser.java:50` | `@ManyToOne` |
| GL-models_OrganizationUser-007 | `app/models/OrganizationUser.java:54` | `@ManyToOne` |
| GL-models_OrganizationUser-008 | `app/models/OrganizationUser.java:58` | `public static List<OrganizationUser> findAdminsOf(Organization organization) {` |
| GL-models_OrganizationUser-015 | `app/models/OrganizationUser.java:97` | `public static String roleTypeOf(User user, Organization organization) {` |
| GL-models_OrganizationUser-016 | `app/models/OrganizationUser.java:115` | `private static boolean contains(Organization organization, User user, RoleType roleType) {` |
| GL-models_OrganizationUser-017 | `app/models/OrganizationUser.java:126` | `private static boolean contains(Long organizationId, Long userId, RoleType roleType) {` |
| GL-models_OrganizationUser-018 | `app/models/OrganizationUser.java:135` | `public static void assignRole(Long userId, Long organizationId, Long roleId) {` |
| GL-models_OrganizationUser-019 | `app/models/OrganizationUser.java:151` | `public static OrganizationUser findByOrganizationIdAndUserId(Long organizationId, Long userId) {` |
| GL-models_OrganizationUser-020 | `app/models/OrganizationUser.java:158` | `public static void create(Long userId, Long organizationId, Long roleId) {` |
| GL-models_OrganizationUser-021 | `app/models/OrganizationUser.java:167` | `public static void delete(Long organizationId, Long userId) {` |
| GL-models_OrganizationUser-022 | `app/models/OrganizationUser.java:176` | `public static boolean exist(Long organizationId, Long userId) {` |
| GL-models_OrganizationUser-023 | `app/models/OrganizationUser.java:181` | `public static List<OrganizationUser> findByUser(User user, int size) {` |
| GL-models_CandidateUser-001 | `app/models/CandidateUser.java:14` | `// Simple DTO for automatic user creation` |
| GL-models_CandidateUser-007 | `app/models/CandidateUser.java:28` | `public CandidateUser(String name, String email) {` |
| GL-models_CandidateUser-008 | `app/models/CandidateUser.java:34` | `public CandidateUser(String name, String email, String loginId, String password, boolean isGuest) {` |
| GL-models_CandidateUser-018 | `app/models/CandidateUser.java:91` | `@Override` |
| GL-models_UserIdent-001 | `app/models/UserIdent.java:26` | `/**` |
| GL-models_UserIdent-005 | `app/models/UserIdent.java:42` | `public UserIdent(User author) {` |
| GL-models_NotificationMail-001 | `app/models/NotificationMail.java:58` | `@Entity` |
| GL-models_NotificationMail-006 | `app/models/NotificationMail.java:70` | `@Id` |
| GL-models_NotificationMail-007 | `app/models/NotificationMail.java:74` | `@OneToOne` |
| GL-models_NotificationMail-008 | `app/models/NotificationMail.java:78` | `public static final Finder<Long, NotificationMail> find = new Finder<>(Long.class,` |
| GL-models_NotificationMail-009 | `app/models/NotificationMail.java:82` | `public static void onStart() {` |
| GL-models_NotificationMail-010 | `app/models/NotificationMail.java:95` | `private static boolean notificationEnabled() {` |
| GL-models_NotificationMail-011 | `app/models/NotificationMail.java:102` | `/**` |
| GL-models_NotificationMail-012 | `app/models/NotificationMail.java:203` | `/**` |
| GL-models_NotificationMail-013 | `app/models/NotificationMail.java:322` | `/**` |
| GL-models_NotificationMail-014 | `app/models/NotificationMail.java:382` | `/**` |
| GL-models_NotificationMail-023 | `app/models/NotificationMail.java:530` | `private static void sendMail(INotificationEvent event, Set<MailRecipient> toList, Set<MailRecipient>` |
| GL-models_NotificationMail-024 | `app/models/NotificationMail.java:601` | `private static String removeHeadAnchor(String htmlText) {` |
| GL-models_NotificationMail-025 | `app/models/NotificationMail.java:606` | `@Nullable` |
| GL-models_NotificationMail-028 | `app/models/NotificationMail.java:684` | `/**` |
| GL-models_NotificationMail-029 | `app/models/NotificationMail.java:727` | `private static void handleImages(Document doc){` |
| GL-models_YobiUpdate-001 | `app/models/YobiUpdate.java:38` | `public class YobiUpdate {` |
| GL-models_YobiUpdate-003 | `app/models/YobiUpdate.java:43` | `private static final Long UPDATE_NOTIFICATION_INTERVAL_IN_MILLIS = Configuration.root()` |
| GL-models_YobiUpdate-004 | `app/models/YobiUpdate.java:46` | `private static final String UPDATE_REPOSITORY_URL = Configuration.root()` |
| GL-models_YobiUpdate-005 | `app/models/YobiUpdate.java:49` | `private static final String RELEASE_URL_FORMAT = Configuration.root()` |
| GL-models_YobiUpdate-008 | `app/models/YobiUpdate.java:60` | `public static void onStart() {` |
| GL-models_YobiUpdate-011 | `app/models/YobiUpdate.java:92` | `public static void refreshVersionToUpdate() throws GitAPIException {` |
| GL-models_YobiUpdate-012 | `app/models/YobiUpdate.java:97` | `/**` |
| GL-models_Milestone-001 | `app/models/Milestone.java:46` | `@Entity` |
| GL-models_Milestone-003 | `app/models/Milestone.java:53` | `public static final Finder<Long, Milestone> find = new Finder<>(Long.class, Milestone.class);` |
| GL-models_Milestone-006 | `app/models/Milestone.java:62` | `@Id` |
| GL-models_Milestone-007 | `app/models/Milestone.java:66` | `@Constraints.Required` |
| GL-models_Milestone-008 | `app/models/Milestone.java:70` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_Milestone-009 | `app/models/Milestone.java:74` | `@Lob` |
| GL-models_Milestone-010 | `app/models/Milestone.java:78` | `@Constraints.Required` |
| GL-models_Milestone-011 | `app/models/Milestone.java:82` | `@ManyToOne` |
| GL-models_Milestone-012 | `app/models/Milestone.java:86` | `@OneToMany(mappedBy = "milestone")` |
| GL-models_Milestone-013 | `app/models/Milestone.java:90` | `public void delete() {` |
| GL-models_Milestone-017 | `app/models/Milestone.java:117` | `public List<Issue> sortedByNumberOfIssue(){` |
| GL-models_Milestone-018 | `app/models/Milestone.java:129` | `public List<Issue> sortedByNumberOfOpenIssue(){` |
| GL-models_Milestone-019 | `app/models/Milestone.java:140` | `public List<Issue> sortedByNumberOfClosedIssue(){` |
| GL-models_Milestone-022 | `app/models/Milestone.java:161` | `public static Milestone findById(Long id) {` |
| GL-models_Milestone-023 | `app/models/Milestone.java:166` | `public static List<Milestone> findByProjectId(Long projectId) {` |
| GL-models_Milestone-024 | `app/models/Milestone.java:171` | `public static List<Milestone> findClosedMilestones(Long projectId) {` |
| GL-models_Milestone-025 | `app/models/Milestone.java:176` | `public static List<Milestone> findOpenMilestones(Long projectId) {` |
| GL-models_Milestone-026 | `app/models/Milestone.java:181` | `public static Milestone findMilestoneByTitle(@Nonnull Project project, String title) {` |
| GL-models_Milestone-030 | `app/models/Milestone.java:263` | `public void updateWith(Milestone newMilestone) {` |
| GL-models_Milestone-031 | `app/models/Milestone.java:272` | `/**` |
| GL-models_Milestone-033 | `app/models/Milestone.java:294` | `public String until() {` |
| GL-models_Milestone-035 | `app/models/Milestone.java:316` | `@Override` |
| GL-models_Milestone-036 | `app/models/Milestone.java:337` | `public void open() {` |
| GL-models_Milestone-037 | `app/models/Milestone.java:343` | `public void close() {` |
| GL-models_Milestone-039 | `app/models/Milestone.java:354` | `public static int countOpened(Project project) {` |
| GL-models_CommitComment-001 | `app/models/CommitComment.java:34` | `@Entity` |
| GL-models_CommitComment-003 | `app/models/CommitComment.java:39` | `public static final Finder<Long, CommitComment> find = new Finder<>(Long.class, CommitComment.class)` |
| GL-models_CommitComment-004 | `app/models/CommitComment.java:42` | `@Transient` |
| GL-models_CommitComment-006 | `app/models/CommitComment.java:49` | `public CommitComment() {` |
| GL-models_CommitComment-007 | `app/models/CommitComment.java:54` | `@Override` |
| GL-models_CommitComment-008 | `app/models/CommitComment.java:89` | `public static int count(Project project, String commitId, String path){` |
| GL-models_CommitComment-009 | `app/models/CommitComment.java:105` | `public static int countByCommits(Project project, List<PullRequestCommit> commits) {` |
| GL-models_CommitComment-010 | `app/models/CommitComment.java:117` | `public static List<CommitComment> findByCommits(Project project, List<PullRequestCommit> commits) {` |
| GL-models_CommitComment-011 | `app/models/CommitComment.java:126` | `public String groupKey() {` |
| GL-models_CommitComment-012 | `app/models/CommitComment.java:132` | `public boolean threadEquals(CommitComment other) {` |
| GL-models_CommitComment-014 | `app/models/CommitComment.java:145` | `public boolean hasLocation() {` |
| GL-models_FavoriteProject-001 | `app/models/FavoriteProject.java:19` | `@Entity` |
| GL-models_FavoriteProject-002 | `app/models/FavoriteProject.java:22` | `public static Finder<Long, FavoriteProject> finder = new Finder<>(Long.class, FavoriteProject.class)` |
| GL-models_FavoriteProject-003 | `app/models/FavoriteProject.java:25` | `@Id` |
| GL-models_FavoriteProject-004 | `app/models/FavoriteProject.java:29` | `@ManyToOne` |
| GL-models_FavoriteProject-005 | `app/models/FavoriteProject.java:33` | `@OneToOne` |
| GL-models_FavoriteProject-009 | `app/models/FavoriteProject.java:51` | `public static void updateFavoriteProject(@Nonnull Project project){` |
| GL-models_FavoriteProject-010 | `app/models/FavoriteProject.java:63` | `public static FavoriteProject findByProjectId(Long userId, Long projectId){` |
| GL-models_LinkedAccount-001 | `app/models/LinkedAccount.java:11` | `@Entity` |
| GL-models_LinkedAccount-003 | `app/models/LinkedAccount.java:18` | `@Id` |
| GL-models_LinkedAccount-004 | `app/models/LinkedAccount.java:22` | `@ManyToOne` |
| GL-models_LinkedAccount-007 | `app/models/LinkedAccount.java:31` | `public static final Finder<Long, LinkedAccount> find = new Finder<Long, LinkedAccount>(` |
| GL-models_LinkedAccount-008 | `app/models/LinkedAccount.java:35` | `public static LinkedAccount findByProviderKey(final UserCredential userCredential, String key) {` |
| GL-models_LinkedAccount-009 | `app/models/LinkedAccount.java:41` | `public static LinkedAccount create(final AuthUser authUser) {` |
| GL-models_LinkedAccount-010 | `app/models/LinkedAccount.java:48` | `public void update(final AuthUser authUser) {` |
| GL-models_LinkedAccount-011 | `app/models/LinkedAccount.java:54` | `public static LinkedAccount create(final LinkedAccount acc) {` |
| GL-models_ProjectUser-001 | `app/models/ProjectUser.java:34` | `@Entity` |
| GL-models_ProjectUser-003 | `app/models/ProjectUser.java:41` | `private static Finder<Long, ProjectUser> find = new Finder<>(Long.class, ProjectUser.class);` |
| GL-models_ProjectUser-004 | `app/models/ProjectUser.java:44` | `@Id` |
| GL-models_ProjectUser-005 | `app/models/ProjectUser.java:48` | `@ManyToOne` |
| GL-models_ProjectUser-006 | `app/models/ProjectUser.java:52` | `@ManyToOne` |
| GL-models_ProjectUser-007 | `app/models/ProjectUser.java:56` | `@ManyToOne` |
| GL-models_ProjectUser-008 | `app/models/ProjectUser.java:60` | `public ProjectUser(Long userId, Long projectId, Long roleId) {` |
| GL-models_ProjectUser-009 | `app/models/ProjectUser.java:67` | `public static void create(Long userId, Long projectId, Long roleId) {` |
| GL-models_ProjectUser-010 | `app/models/ProjectUser.java:73` | `public static void delete(Long userId, Long projectId) {` |
| GL-models_ProjectUser-011 | `app/models/ProjectUser.java:81` | `public static void assignRole(Long userId, Long projectId, Long roleId) {` |
| GL-models_ProjectUser-012 | `app/models/ProjectUser.java:92` | `/**` |
| GL-models_ProjectUser-013 | `app/models/ProjectUser.java:105` | `public static ProjectUser findByIds(Long userId, Long projectId) {` |
| GL-models_ProjectUser-014 | `app/models/ProjectUser.java:115` | `public static List<ProjectUser> findMemberListByProject(Long projectId) {` |
| GL-models_ProjectUser-015 | `app/models/ProjectUser.java:123` | `public static boolean checkOneMangerPerOneProject(Long userId, Long projectId) {` |
| GL-models_ProjectUser-018 | `app/models/ProjectUser.java:149` | `/**` |
| GL-models_ProjectUser-019 | `app/models/ProjectUser.java:164` | `public static ProjectUser findById(Long id) {` |
| GL-models_ProjectUser-020 | `app/models/ProjectUser.java:169` | `public static List<ProjectUser> findAll(){` |
| GL-models_ProjectUser-022 | `app/models/ProjectUser.java:185` | `public static String roleOf(String loginId, Project project) {` |
| GL-models_ProjectUser-023 | `app/models/ProjectUser.java:191` | `public static String roleOf(User user, Project project) {` |
| GL-models_Role-001 | `app/models/Role.java:34` | `@Entity` |
| GL-models_Role-003 | `app/models/Role.java:39` | `public static final Finder<Long, Role> find = new Finder<>(Long.class,` |
| GL-models_Role-004 | `app/models/Role.java:43` | `@Id` |
| GL-models_Role-007 | `app/models/Role.java:52` | `@OneToMany(mappedBy = "role", cascade = CascadeType.ALL)` |
| GL-models_Role-008 | `app/models/Role.java:56` | `@OneToMany(mappedBy = "role", cascade = CascadeType.ALL)` |
| GL-models_Role-009 | `app/models/Role.java:60` | `public static Role findById(Long id) {` |
| GL-models_Role-010 | `app/models/Role.java:65` | `public static Role findByRoleType(RoleType roleType) {` |
| GL-models_Role-011 | `app/models/Role.java:70` | `public static Role findByName(String name) {` |
| GL-models_Role-012 | `app/models/Role.java:75` | `public static Role findOrganizationRoleByIds(Long userId, Long organizationId) {` |
| GL-models_Role-013 | `app/models/Role.java:82` | `public static Role findRoleByIds(Long userId, Long projectId) {` |
| GL-models_Role-014 | `app/models/Role.java:89` | `public static List<Role> findProjectRoles() {` |
| GL-models_Role-015 | `app/models/Role.java:100` | `public static List<Role> findOrganizationRoles() {` |
| GL-models_PostReceiveMessage-001 | `app/models/PostReceiveMessage.java:28` | `/**` |
| GL-models_PostReceiveMessage-005 | `app/models/PostReceiveMessage.java:43` | `public PostReceiveMessage(Collection<ReceiveCommand> commands, Project project, User user) {` |
| GL-models_PullRequestEvent-001 | `app/models/PullRequestEvent.java:38` | `@Entity` |
| GL-models_PullRequestEvent-003 | `app/models/PullRequestEvent.java:44` | `public static final Finder<Long, PullRequestEvent> finder = new Finder<>(Long.class, PullRequestEven` |
| GL-models_PullRequestEvent-004 | `app/models/PullRequestEvent.java:47` | `@Id` |
| GL-models_PullRequestEvent-006 | `app/models/PullRequestEvent.java:53` | `@ManyToOne` |
| GL-models_PullRequestEvent-007 | `app/models/PullRequestEvent.java:57` | `@Enumerated(EnumType.STRING)` |
| GL-models_PullRequestEvent-009 | `app/models/PullRequestEvent.java:64` | `@Lob` |
| GL-models_PullRequestEvent-010 | `app/models/PullRequestEvent.java:67` | `@Lob` |
| GL-models_PullRequestEvent-011 | `app/models/PullRequestEvent.java:71` | `@Override` |
| GL-models_PullRequestEvent-012 | `app/models/PullRequestEvent.java:77` | `public static void addFromNotificationEvent(NotificationEvent notiEvent, PullRequest pullRequest) {` |
| GL-models_PullRequestEvent-013 | `app/models/PullRequestEvent.java:90` | `private static void add(PullRequestEvent event) {` |
| GL-models_PullRequestEvent-015 | `app/models/PullRequestEvent.java:112` | `private static boolean needToDeleteEvent(PullRequestEvent lastEvent, PullRequestEvent currentEvent) ` |
| GL-models_PullRequestEvent-016 | `app/models/PullRequestEvent.java:120` | `public static void addStateEvent(User sender, PullRequest pullRequest, State state) {` |
| GL-models_PullRequestEvent-017 | `app/models/PullRequestEvent.java:132` | `public static void addMergeEvent(User sender, EventType eventType, State state, PullRequest pullRequ` |
| GL-models_PullRequestEvent-018 | `app/models/PullRequestEvent.java:144` | `public static void addCommitEvents(User sender, PullRequest pullRequest,` |
| GL-models_PullRequestEvent-019 | `app/models/PullRequestEvent.java:166` | `public static List<PullRequestEvent> findByPullRequest(PullRequest pullRequest) {` |
| GL-models_PullRequestEvent-020 | `app/models/PullRequestEvent.java:171` | `@Transient` |
| GL-models_Label-001 | `app/models/Label.java:34` | `/**` |
| GL-models_Label-003 | `app/models/Label.java:44` | `public static final Finder<Long, Label> find = new Finder<>(Long.class, Label.class);` |
| GL-models_Label-004 | `app/models/Label.java:47` | `@Id` |
| GL-models_Label-005 | `app/models/Label.java:51` | `@Required` |
| GL-models_Label-006 | `app/models/Label.java:55` | `@Required` |
| GL-models_Label-007 | `app/models/Label.java:59` | `@ManyToMany(mappedBy="labels")` |
| GL-models_Label-008 | `app/models/Label.java:63` | `/**` |
| GL-models_Label-009 | `app/models/Label.java:78` | `/**` |
| GL-models_Label-010 | `app/models/Label.java:94` | `/**` |
| GL-models_Label-011 | `app/models/Label.java:106` | `/**` |
| GL-models_Label-012 | `app/models/Label.java:130` | `/**` |
| GL-models_Assignee-001 | `app/models/Assignee.java:34` | `@Entity` |
| GL-models_Assignee-003 | `app/models/Assignee.java:41` | `@Id` |
| GL-models_Assignee-004 | `app/models/Assignee.java:45` | `@ManyToOne` |
| GL-models_Assignee-005 | `app/models/Assignee.java:50` | `@ManyToOne` |
| GL-models_Assignee-006 | `app/models/Assignee.java:55` | `@OneToMany(mappedBy = "assignee")` |
| GL-models_Assignee-007 | `app/models/Assignee.java:59` | `public static final Model.Finder<Long, Assignee> finder = new Finder<>(Long.class, Assignee.class);` |
| GL-models_Assignee-008 | `app/models/Assignee.java:62` | `public Assignee(Long userId, Long projectId) {` |
| GL-models_Assignee-009 | `app/models/Assignee.java:68` | `public static Assignee add(Long userId, Long projectId) {` |
| GL-models_Property-001 | `app/models/Property.java:37` | `@Entity` |
| GL-models_Property-002 | `app/models/Property.java:40` | `public static final Finder<Long, Property> find = new Finder<>(Long.class, Property.class);` |
| GL-models_Property-004 | `app/models/Property.java:46` | `@Id` |
| GL-models_Property-005 | `app/models/Property.java:50` | `@Enumerated(EnumType.STRING)` |
| GL-models_Property-006 | `app/models/Property.java:55` | `@Constraints.MaxLength(4000)` |
| GL-models_Property-007 | `app/models/Property.java:59` | `public static String get(Name name) {` |
| GL-models_Property-009 | `app/models/Property.java:77` | `public static void set(Name name, String value) {` |
| GL-models_Property-010 | `app/models/Property.java:90` | `public static void set(Name name, Long value) {` |
| GL-models_Property-011 | `app/models/Property.java:95` | `public static enum Name {` |
| GL-models_Property-012 | `app/models/Property.java:104` | `public static void onStart() {` |
| GL-models_Unwatch-001 | `app/models/Unwatch.java:29` | `@Entity` |
| GL-models_Unwatch-003 | `app/models/Unwatch.java:35` | `public static final Finder<Long, Unwatch> find = new Finder<>(Long.class, Unwatch.class);` |
| GL-models_Unwatch-004 | `app/models/Unwatch.java:38` | `public static List<Unwatch> findBy(ResourceType resourceType, String resourceId) {` |
| GL-models_Unwatch-005 | `app/models/Unwatch.java:43` | `public static Unwatch findBy(User watcher, ResourceType resourceType, String resourceId) {` |
| GL-models_Unwatch-006 | `app/models/Unwatch.java:48` | `public static List<Unwatch> findBy(User user, ResourceType resourceType) {` |
| GL-models_IssueComment-001 | `app/models/IssueComment.java:43` | `@Entity` |
| GL-models_IssueComment-003 | `app/models/IssueComment.java:48` | `public static final Finder<Long, IssueComment> find = new Finder<>(Long.class, IssueComment.class);` |
| GL-models_IssueComment-004 | `app/models/IssueComment.java:51` | `@ManyToOne` |
| GL-models_IssueComment-005 | `app/models/IssueComment.java:55` | `@OneToOne` |
| GL-models_IssueComment-006 | `app/models/IssueComment.java:59` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_IssueComment-007 | `app/models/IssueComment.java:68` | `public IssueComment(Issue issue, User author, String contents) {` |
| GL-models_IssueComment-008 | `app/models/IssueComment.java:75` | `/**` |
| GL-models_IssueComment-009 | `app/models/IssueComment.java:83` | `@Override` |
| GL-models_IssueComment-010 | `app/models/IssueComment.java:89` | `@Override` |
| GL-models_IssueComment-011 | `app/models/IssueComment.java:95` | `@Override` |
| GL-models_IssueComment-012 | `app/models/IssueComment.java:108` | `@Override` |
| GL-models_IssueComment-013 | `app/models/IssueComment.java:117` | `/**` |
| GL-models_IssueComment-014 | `app/models/IssueComment.java:151` | `public void addVoter(User user) {` |
| GL-models_IssueComment-015 | `app/models/IssueComment.java:158` | `public void removeVoter(User user) {` |
| GL-models_IssueComment-016 | `app/models/IssueComment.java:165` | `public static IssueComment from(PostingComment postingComment, Issue issue) {` |
| GL-models_IssueComment-017 | `app/models/IssueComment.java:183` | `public static List<IssueComment> from(Collection<PostingComment> postingComments, Issue issue) {` |
| GL-models_IssueComment-018 | `app/models/IssueComment.java:193` | `public static int countAllCreatedBy(User user) {` |
| GL-models_IssueComment-019 | `app/models/IssueComment.java:198` | `public static int countVoterOf(User user) {` |
| GL-models_IssueComment-020 | `app/models/IssueComment.java:210` | `@Override` |
| GL-models_UserCredential-001 | `app/models/UserCredential.java:22` | `@Entity` |
| GL-models_UserCredential-003 | `app/models/UserCredential.java:28` | `@Id` |
| GL-models_UserCredential-004 | `app/models/UserCredential.java:32` | `@OneToOne` |
| GL-models_UserCredential-006 | `app/models/UserCredential.java:39` | `@Constraints.Email` |
| GL-models_UserCredential-010 | `app/models/UserCredential.java:55` | `@OneToMany(cascade = CascadeType.ALL)` |
| GL-models_UserCredential-011 | `app/models/UserCredential.java:59` | `public static final Finder<Long, UserCredential> find = new Finder<Long, UserCredential>(` |
| GL-models_UserCredential-012 | `app/models/UserCredential.java:63` | `public static boolean existsByAuthUserIdentity(` |
| GL-models_UserCredential-014 | `app/models/UserCredential.java:78` | `public static UserCredential findByAuthUserIdentity(final AuthUserIdentity identity) {` |
| GL-models_UserCredential-015 | `app/models/UserCredential.java:86` | `public void merge(final UserCredential otherUser) {` |
| GL-models_UserCredential-016 | `app/models/UserCredential.java:98` | `public static UserCredential create(final AuthUser authUser) {` |
| GL-models_UserCredential-017 | `app/models/UserCredential.java:126` | `public static void merge(final AuthUser oldUser, final AuthUser newUser) {` |
| GL-models_UserCredential-019 | `app/models/UserCredential.java:142` | `public static void addLinkedAccount(final AuthUser oldUser,` |
| GL-models_UserCredential-020 | `app/models/UserCredential.java:150` | `public static UserCredential findByEmail(final String email) {` |
| GL-models_UserCredential-023 | `app/models/UserCredential.java:165` | `public static List<UserCredential> findByUserId(Long id){` |
| GL-models_UserCredential-024 | `app/models/UserCredential.java:170` | `@Override` |
| GL-models_PullRequestEventMessage-001 | `app/models/PullRequestEventMessage.java:27` | `public class PullRequestEventMessage {` |
| GL-models_PullRequestEventMessage-008 | `app/models/PullRequestEventMessage.java:42` | `public PullRequestEventMessage(User sender, Request request, Project project, String branch) {` |
| GL-models_PullRequestEventMessage-009 | `app/models/PullRequestEventMessage.java:50` | `public PullRequestEventMessage(User sender, Request request, PullRequest pullRequest) {` |
| GL-models_PullRequestEventMessage-010 | `app/models/PullRequestEventMessage.java:57` | `public PullRequestEventMessage(User sender, Request request, PullRequest pullRequest, EventType even` |
| GL-models_User-001 | `app/models/User.java:46` | `@Table(name = "n4user")` |
| GL-models_User-003 | `app/models/User.java:53` | `public static final Model.Finder<Long, User> find = new Finder<>(Long.class, User.class);` |
| GL-models_User-004 | `app/models/User.java:56` | `public static final Comparator<User> USER_NAME_COMPARATOR = new Comparator<User>() {` |
| GL-models_User-005 | `app/models/User.java:64` | `/**` |
| GL-models_User-007 | `app/models/User.java:73` | `public static final String LOGIN_ID_PATTERN = "[a-zA-Z0-9가-힣-]+([_.][a-z_.A-Z0-9가-힣-]+)*";` |
| GL-models_User-008 | `app/models/User.java:75` | `public static final String LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH = "[a-zA-Z0-9-/]+([_.][a-z_.A-Z0-9-/` |
| GL-models_User-009 | `app/models/User.java:78` | `public static final User anonymous = new NullUser();` |
| GL-models_User-010 | `app/models/User.java:81` | `@Id` |
| GL-models_User-011 | `app/models/User.java:85` | `/**` |
| GL-models_User-013 | `app/models/User.java:93` | `@Pattern(value = "^" + LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert")` |
| GL-models_User-014 | `app/models/User.java:99` | `/**` |
| GL-models_User-017 | `app/models/User.java:109` | `@Constraints.Email(message = "user.wrongEmail.alert")` |
| GL-models_User-019 | `app/models/User.java:115` | `@Transient` |
| GL-models_User-020 | `app/models/User.java:119` | `@Transient` |
| GL-models_User-021 | `app/models/User.java:123` | `@Transient` |
| GL-models_User-022 | `app/models/User.java:127` | `@Transient` |
| GL-models_User-023 | `app/models/User.java:131` | `/**` |
| GL-models_User-024 | `app/models/User.java:139` | `@Enumerated(EnumType.STRING)` |
| GL-models_User-025 | `app/models/User.java:143` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_User-026 | `app/models/User.java:147` | `/**` |
| GL-models_User-027 | `app/models/User.java:154` | `/**` |
| GL-models_User-028 | `app/models/User.java:163` | `@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)` |
| GL-models_User-029 | `app/models/User.java:167` | `@OneToMany(mappedBy = "user")` |
| GL-models_User-030 | `app/models/User.java:171` | `/**` |
| GL-models_User-031 | `app/models/User.java:179` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_User-032 | `app/models/User.java:184` | `@ManyToMany(mappedBy = "receivers")` |
| GL-models_User-033 | `app/models/User.java:189` | `/**` |
| GL-models_User-034 | `app/models/User.java:203` | `@OneToMany(mappedBy = "user")` |
| GL-models_User-035 | `app/models/User.java:207` | `@OneToMany(mappedBy = "user")` |
| GL-models_User-036 | `app/models/User.java:211` | `@OneToMany(mappedBy = "user")` |
| GL-models_User-037 | `app/models/User.java:215` | `/**` |
| GL-models_User-038 | `app/models/User.java:222` | `@Transient` |
| GL-models_User-039 | `app/models/User.java:226` | `@Transient` |
| GL-models_User-040 | `app/models/User.java:231` | `@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)` |
| GL-models_User-042 | `app/models/User.java:238` | `public User() {` |
| GL-models_User-043 | `app/models/User.java:242` | `public User(Long id) {` |
| GL-models_User-048 | `app/models/User.java:288` | `/**` |
| GL-models_User-049 | `app/models/User.java:301` | `public List<Project> myProjects(String orderString) {` |
| GL-models_User-050 | `app/models/User.java:306` | `public List<Project> ownProjects() {` |
| GL-models_User-051 | `app/models/User.java:311` | `/**` |
| GL-models_User-052 | `app/models/User.java:326` | `/**` |
| GL-models_User-053 | `app/models/User.java:346` | `public static User findByUserToken(String token){` |
| GL-models_User-054 | `app/models/User.java:360` | `public static User findUserIfTokenExist(User user){` |
| GL-models_User-055 | `app/models/User.java:373` | `public static String extractUserTokenFromRequestHeader(Http.Request request) {` |
| GL-models_User-056 | `app/models/User.java:383` | `/**` |
| GL-models_User-057 | `app/models/User.java:415` | `public static User findByLoginKey(String loginIdOrEmail) {` |
| GL-models_User-058 | `app/models/User.java:426` | `/**` |
| GL-models_User-059 | `app/models/User.java:438` | `/**` |
| GL-models_User-060 | `app/models/User.java:451` | `/**` |
| GL-models_User-062 | `app/models/User.java:497` | `/**` |
| GL-models_User-063 | `app/models/User.java:510` | `public static List<User> findUsersByProjectAndOrganization(Project project) {` |
| GL-models_User-064 | `app/models/User.java:544` | `@Transient` |
| GL-models_User-065 | `app/models/User.java:556` | `/**` |
| GL-models_User-066 | `app/models/User.java:570` | `/**` |
| GL-models_User-067 | `app/models/User.java:578` | `/**` |
| GL-models_User-070 | `app/models/User.java:605` | `@Override` |
| GL-models_User-071 | `app/models/User.java:621` | `public Resource avatarAsResource() {` |
| GL-models_User-077 | `app/models/User.java:676` | `@Transactional` |
| GL-models_User-082 | `app/models/User.java:711` | `/**` |
| GL-models_User-083 | `app/models/User.java:723` | `public void enroll(Organization organization) {` |
| GL-models_User-084 | `app/models/User.java:730` | `/**` |
| GL-models_User-085 | `app/models/User.java:742` | `public void cancelEnroll(Organization organization) {` |
| GL-models_User-086 | `app/models/User.java:749` | `/**` |
| GL-models_User-087 | `app/models/User.java:764` | `public static boolean enrolled(Organization organization) {` |
| GL-models_User-088 | `app/models/User.java:773` | `@Override` |
| GL-models_User-089 | `app/models/User.java:783` | `public void changeState(UserState state) {` |
| GL-models_User-090 | `app/models/User.java:812` | `public String avatarUrl() {` |
| GL-models_User-091 | `app/models/User.java:823` | `public String avatarUrl(int size) {` |
| GL-models_User-092 | `app/models/User.java:834` | `/**` |
| GL-models_User-093 | `app/models/User.java:860` | `/**` |
| GL-models_User-094 | `app/models/User.java:883` | `/**` |
| GL-models_User-095 | `app/models/User.java:899` | `/**` |
| GL-models_User-096 | `app/models/User.java:912` | `public static List<User> findUsersByOrganization(Long organizationId, RoleType roleType) {` |
| GL-models_User-097 | `app/models/User.java:918` | `/**` |
| GL-models_User-098 | `app/models/User.java:929` | `/**` |
| GL-models_User-099 | `app/models/User.java:945` | `/**` |
| GL-models_User-100 | `app/models/User.java:956` | `public void visits(Project project) {` |
| GL-models_User-101 | `app/models/User.java:965` | `public void visits(Issue issue) {` |
| GL-models_User-102 | `app/models/User.java:975` | `public void visits(Posting posting) {` |
| GL-models_User-106 | `app/models/User.java:1017` | `public void createOrganization(Organization organization) {` |
| GL-models_User-107 | `app/models/User.java:1031` | `private void add(OrganizationUser ou) {` |
| GL-models_User-108 | `app/models/User.java:1036` | `public String toString() {` |
| GL-models_User-109 | `app/models/User.java:1045` | `@Override` |
| GL-models_User-110 | `app/models/User.java:1056` | `@Override` |
| GL-models_User-112 | `app/models/User.java:1087` | `public void updateFavoriteProject(@Nonnull Project project){` |
| GL-models_User-113 | `app/models/User.java:1096` | `public void updateFavoriteOrganization(@Nonnull Organization organization){` |
| GL-models_User-114 | `app/models/User.java:1105` | `public boolean toggleFavoriteProject(Long projectId) {` |
| GL-models_User-115 | `app/models/User.java:1122` | `public void removeFavoriteProject(Long projectId) {` |
| GL-models_User-117 | `app/models/User.java:1145` | `public boolean toggleFavoriteOrganization(Long organizationId) {` |
| GL-models_User-118 | `app/models/User.java:1161` | `private void removeFavoriteOrganization(Long organizationId) {` |
| GL-models_User-120 | `app/models/User.java:1184` | `public void updateFavoriteIssue(@Nonnull Issue issue){` |
| GL-models_User-121 | `app/models/User.java:1193` | `public boolean toggleFavoriteIssue(Long issueId) {` |
| GL-models_User-122 | `app/models/User.java:1209` | `public void removeFavoriteIssue(Long issueId) {` |
| GL-models_User-127 | `app/models/User.java:1293` | `public String extractDepartmentPart(){` |
| GL-models_IssueEvent-001 | `app/models/IssueEvent.java:23` | `@Entity` |
| GL-models_IssueEvent-003 | `app/models/IssueEvent.java:29` | `@Id` |
| GL-models_IssueEvent-007 | `app/models/IssueEvent.java:41` | `@ManyToOne` |
| GL-models_IssueEvent-008 | `app/models/IssueEvent.java:45` | `@Enumerated(EnumType.STRING)` |
| GL-models_IssueEvent-009 | `app/models/IssueEvent.java:49` | `@Lob` |
| GL-models_IssueEvent-010 | `app/models/IssueEvent.java:53` | `@Lob` |
| GL-models_IssueEvent-011 | `app/models/IssueEvent.java:57` | `private static final int DRAFT_TIME_IN_MILLIS = Configuration.root()` |
| GL-models_IssueEvent-012 | `app/models/IssueEvent.java:61` | `public static final Finder<Long, IssueEvent> find = new Finder<>(Long.class,` |
| GL-models_IssueEvent-013 | `app/models/IssueEvent.java:65` | `/**` |
| GL-models_IssueEvent-014 | `app/models/IssueEvent.java:113` | `/**` |
| GL-models_IssueEvent-017 | `app/models/IssueEvent.java:154` | `/**` |
| GL-models_IssueEvent-018 | `app/models/IssueEvent.java:176` | `public static void addFromNotificationEventWithoutSkipEvent(NotificationEvent notiEvent, Issue updat` |
| GL-models_IssueEvent-019 | `app/models/IssueEvent.java:189` | `@Override` |
| GL-models_IssueEvent-020 | `app/models/IssueEvent.java:195` | `public static Set<Issue> findReferredIssue(String message, Project project) {` |
| GL-models_CodeComment-001 | `app/models/CodeComment.java:36` | `@MappedSuperclass` |
| GL-models_CodeComment-003 | `app/models/CodeComment.java:41` | `public static final Finder<Long, CodeComment> find = new Finder<>(Long.class, CodeComment.class);` |
| GL-models_CodeComment-004 | `app/models/CodeComment.java:44` | `@Id` |
| GL-models_CodeComment-005 | `app/models/CodeComment.java:47` | `@ManyToOne` |
| GL-models_CodeComment-008 | `app/models/CodeComment.java:54` | `@Enumerated(EnumType.STRING)` |
| GL-models_CodeComment-009 | `app/models/CodeComment.java:57` | `@Lob @Constraints.Required` |
| GL-models_CodeComment-010 | `app/models/CodeComment.java:60` | `@Constraints.Required` |
| GL-models_CodeComment-014 | `app/models/CodeComment.java:70` | `public CodeComment() {` |
| GL-models_CodeComment-015 | `app/models/CodeComment.java:76` | `@Transient` |
| GL-models_CodeComment-016 | `app/models/CodeComment.java:84` | `@Override` |
| GL-models_CodeComment-017 | `app/models/CodeComment.java:90` | `public Duration ago() {` |
| GL-models_CodeComment-018 | `app/models/CodeComment.java:95` | `abstract public Resource asResource();` |
| GL-models_UserVerification-001 | `app/models/UserVerification.java:19` | `@Entity` |
| GL-models_UserVerification-003 | `app/models/UserVerification.java:25` | `public static final Model.Finder<Long, UserVerification> find = new Finder<>(Long.class, UserVerific` |
| GL-models_UserVerification-004 | `app/models/UserVerification.java:28` | `@Id` |
| GL-models_UserVerification-005 | `app/models/UserVerification.java:32` | `@OneToOne` |
| GL-models_UserVerification-009 | `app/models/UserVerification.java:45` | `public static synchronized UserVerification newVerification(User user) {` |
| GL-models_UserVerification-010 | `app/models/UserVerification.java:56` | `public static UserVerification findbyUser(User user) {` |
| GL-models_UserVerification-011 | `app/models/UserVerification.java:66` | `public static UserVerification findbyLoginIdAndVerificationCode(String loginId, String verificationC` |
| GL-models_UserVerification-013 | `app/models/UserVerification.java:88` | `public void invalidate(){` |
| GL-models_UserVerification-014 | `app/models/UserVerification.java:93` | `@Override` |
| GL-models_PullRequestCommit-001 | `app/models/PullRequestCommit.java:34` | `@Entity` |
| GL-models_PullRequestCommit-003 | `app/models/PullRequestCommit.java:41` | `public static final Finder<Long, PullRequestCommit> find = new Finder<>(Long.class, PullRequestCommi` |
| GL-models_PullRequestCommit-004 | `app/models/PullRequestCommit.java:44` | `@Id` |
| GL-models_PullRequestCommit-005 | `app/models/PullRequestCommit.java:48` | `@ManyToOne` |
| GL-models_PullRequestCommit-009 | `app/models/PullRequestCommit.java:58` | `@Lob` |
| GL-models_PullRequestCommit-012 | `app/models/PullRequestCommit.java:66` | `@Enumerated(EnumType.STRING)` |
| GL-models_PullRequestCommit-019 | `app/models/PullRequestCommit.java:114` | `@Transient` |
| GL-models_PullRequestCommit-020 | `app/models/PullRequestCommit.java:121` | `/**` |
| GL-models_PullRequestCommit-022 | `app/models/PullRequestCommit.java:143` | `public static PullRequestCommit findById(String id) {` |
| GL-models_PullRequestCommit-025 | `app/models/PullRequestCommit.java:162` | `public static PullRequestCommit bindPullRequestCommit(GitCommit commit, PullRequest pullRequest) {` |
| GL-models_PullRequestCommit-026 | `app/models/PullRequestCommit.java:176` | `public enum State {` |
| GL-models_Email-001 | `app/models/Email.java:40` | `@Entity` |
| GL-models_Email-003 | `app/models/Email.java:47` | `public static final Finder<Long, Email> find = new Finder<>(Long.class, Email.class);` |
| GL-models_Email-004 | `app/models/Email.java:50` | `/**` |
| GL-models_Email-005 | `app/models/Email.java:57` | `/**` |
| GL-models_Email-006 | `app/models/Email.java:64` | `/**` |
| GL-models_Email-007 | `app/models/Email.java:72` | `/**` |
| GL-models_Email-009 | `app/models/Email.java:81` | `@Transient` |
| GL-models_Email-010 | `app/models/Email.java:85` | `public static boolean exists(String newEmail, boolean valid) {` |
| GL-models_Email-011 | `app/models/Email.java:98` | `public boolean validate(String token) {` |
| GL-models_Email-012 | `app/models/Email.java:110` | `public static void deleteOtherInvalidEmails(String emailAddress) {` |
| GL-models_Email-013 | `app/models/Email.java:118` | `public void sendValidationEmail() {` |
| GL-models_Email-014 | `app/models/Email.java:126` | `public static Email findByEmail(String email, boolean isValid) {` |
| GL-models_Email-015 | `app/models/Email.java:131` | `private static ExpressionList<Email> findByEmailAndIsValid(String email, boolean isValid) {` |
| GL-models_IssueSharer-001 | `app/models/IssueSharer.java:17` | `@Entity` |
| GL-models_IssueSharer-003 | `app/models/IssueSharer.java:23` | `@Id` |
| GL-models_IssueSharer-006 | `app/models/IssueSharer.java:33` | `@OneToOne` |
| GL-models_IssueSharer-007 | `app/models/IssueSharer.java:37` | `@OneToOne` |
| GL-models_IssueSharer-010 | `app/models/IssueSharer.java:46` | `public static final Finder<Long, IssueSharer> find = new Finder<>(Long.class,` |
| GL-models_IssueSharer-011 | `app/models/IssueSharer.java:50` | `public static IssueSharer createSharer(String loginId, Issue issue) {` |
| GL-models_ProjectTransfer-001 | `app/models/ProjectTransfer.java:37` | `@Entity` |
| GL-models_ProjectTransfer-003 | `app/models/ProjectTransfer.java:44` | `public static final Finder<Long, ProjectTransfer> find = new Finder<>(Long.class, ProjectTransfer.cl` |
| GL-models_ProjectTransfer-004 | `app/models/ProjectTransfer.java:47` | `@Id` |
| GL-models_ProjectTransfer-005 | `app/models/ProjectTransfer.java:51` | `// who requested this transfer.` |
| GL-models_ProjectTransfer-006 | `app/models/ProjectTransfer.java:56` | `/**` |
| GL-models_ProjectTransfer-007 | `app/models/ProjectTransfer.java:64` | `@ManyToOne` |
| GL-models_ProjectTransfer-008 | `app/models/ProjectTransfer.java:68` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_ProjectTransfer-012 | `app/models/ProjectTransfer.java:81` | `public static ProjectTransfer requestNewTransfer(Project project, User sender, String destination) {` |
| GL-models_ProjectTransfer-014 | `app/models/ProjectTransfer.java:112` | `public static ProjectTransfer findValidOne(Long id) {` |
| GL-models_ProjectTransfer-015 | `app/models/ProjectTransfer.java:124` | `public static void deleteExisting(Project project, User sender, String destination) {` |
| GL-models_ProjectTransfer-016 | `app/models/ProjectTransfer.java:137` | `public Resource asResource() {` |
| GL-models_ProjectTransfer-017 | `app/models/ProjectTransfer.java:157` | `public static List<ProjectTransfer> findByProject(Project project) {` |
| GL-models_TimelineItem-001 | `app/models/TimelineItem.java:27` | `public interface TimelineItem {` |
| GL-models_TimelineItem-002 | `app/models/TimelineItem.java:29` | `/**` |
| GL-models_TimelineItem-003 | `app/models/TimelineItem.java:40` | `/**` |
| GL-models_ProjectMenuSetting-001 | `app/models/ProjectMenuSetting.java:30` | `@Entity` |
| GL-models_ProjectMenuSetting-003 | `app/models/ProjectMenuSetting.java:35` | `public static Finder<Long, ProjectMenuSetting> finder = new Finder<>(Long.class, ProjectMenuSetting.` |
| GL-models_ProjectMenuSetting-004 | `app/models/ProjectMenuSetting.java:38` | `@Id` |
| GL-models_ProjectMenuSetting-005 | `app/models/ProjectMenuSetting.java:41` | `@OneToOne` |
| GL-models_ProjectMenuSetting-012 | `app/models/ProjectMenuSetting.java:57` | `public ProjectMenuSetting() {}` |
| GL-models_ProjectMenuSetting-013 | `app/models/ProjectMenuSetting.java:60` | `public ProjectMenuSetting(ProjectMenuSetting projectMenuSetting) {` |
| GL-models_ProjectMenuSetting-014 | `app/models/ProjectMenuSetting.java:70` | `public void updateMenuSetting(ProjectMenuSetting setting){` |
| GL-models_ProjectMenuSetting-015 | `app/models/ProjectMenuSetting.java:81` | `@Override` |
| GL-models_Organization-001 | `app/models/Organization.java:34` | `@Entity` |
| GL-models_Organization-003 | `app/models/Organization.java:41` | `public static final Finder<Long, Organization> find = new Finder<>(Long.class, Organization.class);` |
| GL-models_Organization-004 | `app/models/Organization.java:44` | `@Id` |
| GL-models_Organization-005 | `app/models/Organization.java:48` | `@Constraints.Pattern(value = "^" + User.LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert")` |
| GL-models_Organization-006 | `app/models/Organization.java:54` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_Organization-007 | `app/models/Organization.java:58` | `@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)` |
| GL-models_Organization-008 | `app/models/Organization.java:62` | `@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)` |
| GL-models_Organization-009 | `app/models/Organization.java:66` | `@ManyToMany(mappedBy = "enrolledOrganizations")` |
| GL-models_Organization-011 | `app/models/Organization.java:73` | `public void add(OrganizationUser ou) {` |
| GL-models_Organization-012 | `app/models/Organization.java:78` | `public static Organization findByName(String name) {` |
| GL-models_Organization-013 | `app/models/Organization.java:83` | `public static PagingList<Organization> findByNameLike(String name) {` |
| GL-models_Organization-017 | `app/models/Organization.java:113` | `@Transactional` |
| GL-models_Organization-019 | `app/models/Organization.java:162` | `/**` |
| GL-models_Organization-020 | `app/models/Organization.java:175` | `public static List<Organization> findAllOrganizations() {` |
| GL-models_Organization-021 | `app/models/Organization.java:187` | `public static List<Organization> findAllOrganizations(String loginId) {` |
| GL-models_Organization-022 | `app/models/Organization.java:214` | `/**` |
| GL-models_Organization-024 | `app/models/Organization.java:242` | `public void updateWith(Organization modifiedOrganization) throws IOException, ServletException {` |
| GL-models_Organization-025 | `app/models/Organization.java:250` | `private void updateProjects(String newOwner) throws IOException, ServletException {` |
| GL-models_SiteAdmin-001 | `app/models/SiteAdmin.java:21` | `@Entity` |
| GL-models_SiteAdmin-003 | `app/models/SiteAdmin.java:27` | `@Id` |
| GL-models_SiteAdmin-004 | `app/models/SiteAdmin.java:31` | `@OneToOne` |
| GL-models_SiteAdmin-006 | `app/models/SiteAdmin.java:37` | `public static final Model.Finder<Long, SiteAdmin> find = new Finder<>(Long.class, SiteAdmin.class);` |
| GL-models_SiteAdmin-007 | `app/models/SiteAdmin.java:40` | `public static boolean exists(User user) {` |
| GL-models_SiteAdmin-008 | `app/models/SiteAdmin.java:45` | `public static SiteAdmin findByUserLoginId(String userLoginId) {` |
| GL-models_SiteAdmin-009 | `app/models/SiteAdmin.java:50` | `public static User updateDefaultSiteAdmin(User user) {` |
| GL-models_UserAction-001 | `app/models/UserAction.java:16` | `@MappedSuperclass` |
| GL-models_UserAction-003 | `app/models/UserAction.java:21` | `@Id` |
| GL-models_UserAction-004 | `app/models/UserAction.java:25` | `@ManyToOne` |
| GL-models_UserAction-005 | `app/models/UserAction.java:29` | `@Enumerated(EnumType.STRING)` |
| GL-models_UserAction-007 | `app/models/UserAction.java:36` | `public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder,` |
| GL-models_UserAction-008 | `app/models/UserAction.java:44` | `public static <T extends UserAction> T findBy(Finder<Long, T> finder, User subject,` |
| GL-models_UserAction-009 | `app/models/UserAction.java:58` | `public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder, User subject,` |
| GL-models_UserAction-010 | `app/models/UserAction.java:66` | `public static <T extends UserAction> int countBy(Finder<Long, T> finder,` |
| GL-models_UserSetting-001 | `app/models/UserSetting.java:16` | `@Entity` |
| GL-models_UserSetting-003 | `app/models/UserSetting.java:22` | `public static final Model.Finder<Long, UserSetting> find = new Finder<>(Long.class, UserSetting.clas` |
| GL-models_UserSetting-004 | `app/models/UserSetting.java:25` | `@Id` |
| GL-models_UserSetting-005 | `app/models/UserSetting.java:29` | `@OneToOne` |
| GL-models_UserSetting-007 | `app/models/UserSetting.java:36` | `public UserSetting(User user) {` |
| GL-models_UserSetting-008 | `app/models/UserSetting.java:41` | `public static UserSetting findByUser(Long id){` |
| GL-models_AbstractPosting-001 | `app/models/AbstractPosting.java:29` | `@MappedSuperclass` |
| GL-models_AbstractPosting-002 | `app/models/AbstractPosting.java:32` | `public static final Finder<Long, AbstractPosting> finder = new Finder<>(Long.class, AbstractPosting.` |
| GL-models_AbstractPosting-006 | `app/models/AbstractPosting.java:42` | `@Id` |
| GL-models_AbstractPosting-007 | `app/models/AbstractPosting.java:46` | `@Constraints.Required` |
| GL-models_AbstractPosting-008 | `app/models/AbstractPosting.java:51` | `@Lob` |
| GL-models_AbstractPosting-009 | `app/models/AbstractPosting.java:55` | `@Lob` |
| GL-models_AbstractPosting-010 | `app/models/AbstractPosting.java:59` | `@Constraints.Required` |
| GL-models_AbstractPosting-011 | `app/models/AbstractPosting.java:64` | `@Constraints.Required` |
| GL-models_AbstractPosting-016 | `app/models/AbstractPosting.java:79` | `@Transient` |
| GL-models_AbstractPosting-017 | `app/models/AbstractPosting.java:83` | `@ManyToOne` |
| GL-models_AbstractPosting-019 | `app/models/AbstractPosting.java:90` | `// This field is only for ordering. This field should be persistent because` |
| GL-models_AbstractPosting-020 | `app/models/AbstractPosting.java:95` | `@Transient` |
| GL-models_AbstractPosting-021 | `app/models/AbstractPosting.java:99` | `abstract public int computeNumOfComments();` |
| GL-models_AbstractPosting-022 | `app/models/AbstractPosting.java:102` | `public AbstractPosting() {` |
| GL-models_AbstractPosting-023 | `app/models/AbstractPosting.java:108` | `public AbstractPosting(Project project, User author, String title, String body) {` |
| GL-models_AbstractPosting-024 | `app/models/AbstractPosting.java:117` | `/**` |
| GL-models_AbstractPosting-025 | `app/models/AbstractPosting.java:124` | `protected abstract void fixLastNumber();` |
| GL-models_AbstractPosting-028 | `app/models/AbstractPosting.java:137` | `/**` |
| GL-models_AbstractPosting-029 | `app/models/AbstractPosting.java:170` | `@Transactional` |
| GL-models_AbstractPosting-030 | `app/models/AbstractPosting.java:179` | `@Transactional` |
| GL-models_AbstractPosting-031 | `app/models/AbstractPosting.java:193` | `/**` |
| GL-models_AbstractPosting-032 | `app/models/AbstractPosting.java:203` | `public void updateNumber() {` |
| GL-models_AbstractPosting-033 | `app/models/AbstractPosting.java:209` | `public static <T> T findByNumber(Finder<Long, T> finder, Project project, Long number) {` |
| GL-models_AbstractPosting-034 | `app/models/AbstractPosting.java:214` | `public static <T> List<T> findByProject(Finder<Long, T> finder, Project project) {` |
| GL-models_AbstractPosting-035 | `app/models/AbstractPosting.java:219` | `public Duration ago() {` |
| GL-models_AbstractPosting-036 | `app/models/AbstractPosting.java:224` | `public Resource asResource(final ResourceType type) {` |
| GL-models_AbstractPosting-037 | `app/models/AbstractPosting.java:249` | `@Transient` |
| GL-models_AbstractPosting-038 | `app/models/AbstractPosting.java:257` | `@Transient` |
| GL-models_AbstractPosting-040 | `app/models/AbstractPosting.java:266` | `public void delete() {` |
| GL-models_AbstractPosting-041 | `app/models/AbstractPosting.java:277` | `public void deleteOnly() {` |
| GL-models_AbstractPosting-042 | `app/models/AbstractPosting.java:282` | `public void updateProperties() {` |
| GL-models_AbstractPosting-043 | `app/models/AbstractPosting.java:287` | `@Transient` |
| GL-models_AbstractPosting-044 | `app/models/AbstractPosting.java:293` | `/**` |
| GL-models_AbstractPosting-045 | `app/models/AbstractPosting.java:303` | `/**` |
| GL-models_AbstractPosting-046 | `app/models/AbstractPosting.java:319` | `protected void updateMention() {` |
| GL-models_AbstractPosting-047 | `app/models/AbstractPosting.java:326` | `public abstract void checkLabels() throws IssueLabel.IssueLabelException;` |
| GL-models_PageParam-001 | `app/models/PageParam.java:24` | `/**` |
| GL-models_PageParam-002 | `app/models/PageParam.java:33` | `// start from 0` |
| GL-models_PageParam-003 | `app/models/PageParam.java:37` | `// size of one page` |
| GL-models_PageParam-004 | `app/models/PageParam.java:41` | `public PageParam(int page, int size) {` |
| GL-models_IssueLabelCategory-001 | `app/models/IssueLabelCategory.java:35` | `@Entity` |
| GL-models_IssueLabelCategory-003 | `app/models/IssueLabelCategory.java:42` | `public static final Finder<Long, IssueLabelCategory> find = new Finder<>(Long.class, IssueLabelCateg` |
| GL-models_IssueLabelCategory-004 | `app/models/IssueLabelCategory.java:45` | `@Id` |
| GL-models_IssueLabelCategory-005 | `app/models/IssueLabelCategory.java:49` | `@Required` |
| GL-models_IssueLabelCategory-006 | `app/models/IssueLabelCategory.java:54` | `@Required(message="label.error.categoryName.empty")` |
| GL-models_IssueLabelCategory-007 | `app/models/IssueLabelCategory.java:59` | `@OneToMany(mappedBy="category", cascade = CascadeType.ALL)` |
| GL-models_IssueLabelCategory-008 | `app/models/IssueLabelCategory.java:63` | `/**` |
| GL-models_IssueLabelCategory-009 | `app/models/IssueLabelCategory.java:70` | `@Transient` |
| GL-models_IssueLabelCategory-010 | `app/models/IssueLabelCategory.java:79` | `public static IssueLabelCategory findByName(String name, Project project) {` |
| GL-models_IssueLabelCategory-011 | `app/models/IssueLabelCategory.java:87` | `public static IssueLabelCategory findBy(IssueLabelCategory instance) {` |
| GL-models_IssueLabelCategory-012 | `app/models/IssueLabelCategory.java:95` | `public static List<IssueLabelCategory> findByProject(Project project) {` |
| GL-models_IssueLabelCategory-013 | `app/models/IssueLabelCategory.java:103` | `@Override` |
| GL-models_IssueLabelCategory-014 | `app/models/IssueLabelCategory.java:124` | `@Override` |
| GL-models_UserProjectNotification-001 | `app/models/UserProjectNotification.java:16` | `/**` |
| GL-models_UserProjectNotification-003 | `app/models/UserProjectNotification.java:27` | `public static final Finder<Long, UserProjectNotification> find = new Finder<>(Long.class, UserProjec` |
| GL-models_UserProjectNotification-004 | `app/models/UserProjectNotification.java:30` | `@Id` |
| GL-models_UserProjectNotification-005 | `app/models/UserProjectNotification.java:34` | `@ManyToOne` |
| GL-models_UserProjectNotification-006 | `app/models/UserProjectNotification.java:38` | `@ManyToOne` |
| GL-models_UserProjectNotification-007 | `app/models/UserProjectNotification.java:42` | `@Enumerated(EnumType.STRING)` |
| GL-models_UserProjectNotification-010 | `app/models/UserProjectNotification.java:65` | `/**` |
| GL-models_UserProjectNotification-013 | `app/models/UserProjectNotification.java:102` | `public static UserProjectNotification findOne(User user, Project project, EventType notificationType` |
| GL-models_UserProjectNotification-014 | `app/models/UserProjectNotification.java:111` | `public void toggle(EventType notificationType) {` |
| GL-models_UserProjectNotification-015 | `app/models/UserProjectNotification.java:121` | `public static void unwatchExplictly(User user, Project project, EventType notiType) {` |
| GL-models_UserProjectNotification-016 | `app/models/UserProjectNotification.java:131` | `public static void watchExplictly(User user, Project project, EventType notiType) {` |
| GL-models_UserProjectNotification-017 | `app/models/UserProjectNotification.java:141` | `/**` |
| GL-models_UserProjectNotification-019 | `app/models/UserProjectNotification.java:167` | `public static Set<User> findEventWatchersByEventType(Long projectId, EventType eventType) {` |
| GL-models_UserProjectNotification-020 | `app/models/UserProjectNotification.java:172` | `public static Set<User> findEventUnwatchersByEventType(Long projectId, EventType eventType) {` |
| GL-models_UserProjectNotification-021 | `app/models/UserProjectNotification.java:177` | `private static Set<User> findByEventTypeAndOption(Long projectId, EventType eventType, boolean isAll` |
| GL-models_UserProjectNotification-022 | `app/models/UserProjectNotification.java:191` | `public static void deleteUnwatchedProjectNotifications(User user, Project project){` |
| GL-models_CodeRange-001 | `app/models/CodeRange.java:33` | `/**` |
| GL-models_CodeRange-003 | `app/models/CodeRange.java:54` | `public boolean endsWith(DiffLine line) {` |
| GL-models_CodeRange-004 | `app/models/CodeRange.java:60` | `public enum Side {` |
| GL-models_CodeRange-006 | `app/models/CodeRange.java:69` | `@Enumerated(EnumType.STRING)` |
| GL-models_CodeRange-007 | `app/models/CodeRange.java:73` | `@Constraints.Required` |
| GL-models_CodeRange-008 | `app/models/CodeRange.java:77` | `@Constraints.Required` |
| GL-models_CodeRange-009 | `app/models/CodeRange.java:81` | `@Enumerated(EnumType.STRING)` |
| GL-models_CodeRange-010 | `app/models/CodeRange.java:85` | `@Constraints.Required` |
| GL-models_CodeRange-011 | `app/models/CodeRange.java:89` | `@Constraints.Required` |
| GL-models_Attachment-001 | `app/models/Attachment.java:42` | `@Entity` |
| GL-models_Attachment-003 | `app/models/Attachment.java:47` | `public static final Finder<Long, Attachment> find = new Finder<>(Long.class, Attachment.class);` |
| GL-models_Attachment-006 | `app/models/Attachment.java:53` | `@Id` |
| GL-models_Attachment-007 | `app/models/Attachment.java:57` | `@Constraints.Required` |
| GL-models_Attachment-008 | `app/models/Attachment.java:61` | `@Constraints.Required` |
| GL-models_Attachment-009 | `app/models/Attachment.java:65` | `@Enumerated(EnumType.STRING)` |
| GL-models_Attachment-016 | `app/models/Attachment.java:104` | `/**` |
| GL-models_Attachment-017 | `app/models/Attachment.java:113` | `/**` |
| GL-models_Attachment-018 | `app/models/Attachment.java:135` | `/**` |
| GL-models_Attachment-019 | `app/models/Attachment.java:153` | `/**` |
| GL-models_Attachment-020 | `app/models/Attachment.java:164` | `/**` |
| GL-models_Attachment-021 | `app/models/Attachment.java:184` | `/**` |
| GL-models_Attachment-022 | `app/models/Attachment.java:210` | `/**` |
| GL-models_Attachment-023 | `app/models/Attachment.java:222` | `/**` |
| GL-models_Attachment-024 | `app/models/Attachment.java:249` | `private static File moveFileIntoUploadDirectory(File file, String hash)` |
| GL-models_Attachment-025 | `app/models/Attachment.java:264` | `/**` |
| GL-models_Attachment-026 | `app/models/Attachment.java:291` | `/**` |
| GL-models_Attachment-028 | `app/models/Attachment.java:309` | `/**` |
| GL-models_Attachment-029 | `app/models/Attachment.java:321` | `/**` |
| GL-models_Attachment-030 | `app/models/Attachment.java:334` | `/**` |
| GL-models_Attachment-031 | `app/models/Attachment.java:367` | `/**` |
| GL-models_Attachment-032 | `app/models/Attachment.java:379` | `/**` |
| GL-models_Attachment-033 | `app/models/Attachment.java:395` | `private String messageForLosingProject() {` |
| GL-models_Attachment-034 | `app/models/Attachment.java:400` | `/**` |
| GL-models_Attachment-036 | `app/models/Attachment.java:516` | `public static void onStart() {` |
| GL-models_Attachment-037 | `app/models/Attachment.java:521` | `@Override` |
| GL-models_Attachment-038 | `app/models/Attachment.java:536` | `public boolean store(InputStream inputStream, @Nullable String fileName,` |
| GL-models_Attachment-041 | `app/models/Attachment.java:626` | `private static String toHex(byte[] bytes) {` |
| GL-models_Attachment-042 | `app/models/Attachment.java:637` | `// Create the upload directory if it doesn't exist.` |
| GL-models_Attachment-043 | `app/models/Attachment.java:649` | `public static Attachment copyAs(Attachment other) {` |
| GL-models_Posting-001 | `app/models/Posting.java:28` | `@Entity` |
| GL-models_Posting-003 | `app/models/Posting.java:35` | `public static final Finder<Long, Posting> finder = new Finder<>(Long.class, Posting.class);` |
| GL-models_Posting-008 | `app/models/Posting.java:52` | `//ToDo: Sperate it from posting for online commit` |
| GL-models_Posting-009 | `app/models/Posting.java:57` | `//ToDo: Sperate it from posting for online commit` |
| GL-models_Posting-010 | `app/models/Posting.java:62` | `@OneToMany(cascade = CascadeType.ALL)` |
| GL-models_Posting-011 | `app/models/Posting.java:66` | `@ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)` |
| GL-models_Posting-013 | `app/models/Posting.java:81` | `public Posting(Project project, User author, String title, String body) {` |
| GL-models_Posting-014 | `app/models/Posting.java:86` | `/**` |
| GL-models_Posting-015 | `app/models/Posting.java:95` | `protected void fixLastNumber() {` |
| GL-models_Posting-016 | `app/models/Posting.java:100` | `/**` |
| GL-models_Posting-017 | `app/models/Posting.java:108` | `@OneToOne` |
| GL-models_Posting-018 | `app/models/Posting.java:112` | `public Posting() {` |
| GL-models_Posting-019 | `app/models/Posting.java:117` | `@Override` |
| GL-models_Posting-020 | `app/models/Posting.java:123` | `public static List<Posting> findNotices(Project project) {` |
| GL-models_Posting-021 | `app/models/Posting.java:132` | `public static List<Posting> findRecentlyCreated(Project project, int size) {` |
| GL-models_Posting-022 | `app/models/Posting.java:141` | `public static List<Posting> findRecentlyCreatedByDaysAgo(Project project, int days) {` |
| GL-models_Posting-023 | `app/models/Posting.java:148` | `/**` |
| GL-models_Posting-024 | `app/models/Posting.java:158` | `@Override` |
| GL-models_Posting-025 | `app/models/Posting.java:164` | `public static Posting findByNumber(Project project, long number) {` |
| GL-models_Posting-026 | `app/models/Posting.java:169` | `public static int countAllCreatedBy(User user) {` |
| GL-models_Posting-027 | `app/models/Posting.java:174` | `public static int countPostings(Project project) {` |
| GL-models_Posting-028 | `app/models/Posting.java:179` | `/**` |
| GL-models_Posting-029 | `app/models/Posting.java:188` | `public static Posting findREADMEPosting(Project project) {` |
| GL-models_Posting-030 | `app/models/Posting.java:196` | `public PostingComment findCommentByCommentId(Long id) {` |
| GL-models_Posting-031 | `app/models/Posting.java:206` | `public static Posting from(Issue issue) {` |
| GL-models_RecentIssue-001 | `app/models/RecentIssue.java:13` | `@Entity` |
| GL-models_RecentIssue-004 | `app/models/RecentIssue.java:21` | `public static Finder<Long, RecentIssue> find = new Finder<>(Long.class, RecentIssue.class);` |
| GL-models_RecentIssue-005 | `app/models/RecentIssue.java:24` | `@Id` |
| GL-models_RecentIssue-012 | `app/models/RecentIssue.java:41` | `public RecentIssue(User user, String title, Issue issue, Posting posting) {` |
| GL-models_RecentIssue-014 | `app/models/RecentIssue.java:61` | `public static void addNewIssue(final User user, final Issue issue){` |
| GL-models_RecentIssue-015 | `app/models/RecentIssue.java:73` | `public static void addNewPosting(final User user, final Posting posting){` |
| GL-models_RecentIssue-016 | `app/models/RecentIssue.java:86` | `@Transactional` |
| GL-models_RecentIssue-017 | `app/models/RecentIssue.java:101` | `@Transactional` |
| GL-models_RecentIssue-018 | `app/models/RecentIssue.java:116` | `public static void deletePreviousIssue(User user, Long issueId) {` |
| GL-models_RecentIssue-019 | `app/models/RecentIssue.java:134` | `public static void deletePreviousPosting(User user, Long postingId) {` |
| GL-models_RecentIssue-020 | `app/models/RecentIssue.java:145` | `private static void deleteOldestIfOverflow(User user) {` |
| GL-models_RecentIssue-021 | `app/models/RecentIssue.java:162` | `public static void deleteAll(User user) {` |
| GL-models_RecentIssue-023 | `app/models/RecentIssue.java:178` | `@Override` |
| GL-models_FavoriteIssue-001 | `app/models/FavoriteIssue.java:20` | `@Entity` |
| GL-models_FavoriteIssue-002 | `app/models/FavoriteIssue.java:23` | `public static Finder<Long, FavoriteIssue> find = new Finder<>(Long.class, FavoriteIssue.class);` |
| GL-models_FavoriteIssue-003 | `app/models/FavoriteIssue.java:26` | `@Id` |
| GL-models_FavoriteIssue-004 | `app/models/FavoriteIssue.java:30` | `@ManyToOne` |
| GL-models_FavoriteIssue-005 | `app/models/FavoriteIssue.java:34` | `@OneToOne` |
| GL-models_FavoriteIssue-006 | `app/models/FavoriteIssue.java:38` | `public FavoriteIssue(User user, Issue issue) {` |
| GL-models_FavoriteIssue-007 | `app/models/FavoriteIssue.java:44` | `public static void updateFavoriteIssue(@Nonnull Issue issue){` |
| GL-models_FavoriteIssue-008 | `app/models/FavoriteIssue.java:54` | `public static FavoriteIssue findByIssueId(Long userId, Long issueId){` |
| GL-models_Webhook-001 | `app/models/Webhook.java:51` | `/**` |
| GL-models_Webhook-003 | `app/models/Webhook.java:60` | `public static final Finder<Long, Webhook> find = new Finder<>(Long.class, Webhook.class);` |
| GL-models_Webhook-004 | `app/models/Webhook.java:63` | `/**` |
| GL-models_Webhook-005 | `app/models/Webhook.java:70` | `/**` |
| GL-models_Webhook-007 | `app/models/Webhook.java:85` | `/**` |
| GL-models_Webhook-008 | `app/models/Webhook.java:92` | `/**` |
| GL-models_Webhook-011 | `app/models/Webhook.java:105` | `/**` |
| GL-models_Webhook-012 | `app/models/Webhook.java:126` | `/**` |
| GL-models_Webhook-013 | `app/models/Webhook.java:150` | `public static List<Webhook> findByProject(Long projectId) {` |
| GL-models_Webhook-014 | `app/models/Webhook.java:155` | `public static void create(Long projectId, String payloadUrl, String secret, Boolean gitPush, Webhook` |
| GL-models_Webhook-015 | `app/models/Webhook.java:164` | `public static void delete(Long webhookId, Long projectId) {` |
| GL-models_Webhook-016 | `app/models/Webhook.java:169` | `/**` |
| GL-models_Webhook-020 | `app/models/Webhook.java:202` | `private String buildRequestMessage(String url, String message) {` |
| GL-models_Webhook-021 | `app/models/Webhook.java:215` | `// Issue` |
| GL-models_Webhook-022 | `app/models/Webhook.java:238` | `private String buildRequestBody(EventType eventType, User sender, Issue eventIssue) {` |
| GL-models_Webhook-023 | `app/models/Webhook.java:271` | `// Issue transfer` |
| GL-models_Webhook-026 | `app/models/Webhook.java:326` | `// Posting` |
| GL-models_Webhook-027 | `app/models/Webhook.java:346` | `private String buildRequestBody(EventType eventType, User sender, Posting eventPost) {` |
| GL-models_Webhook-028 | `app/models/Webhook.java:365` | `// Comment` |
| GL-models_Webhook-029 | `app/models/Webhook.java:388` | `private String buildRequestBody(EventType eventType, User sender, Comment eventComment) {` |
| GL-models_Webhook-030 | `app/models/Webhook.java:406` | `// Comment Detail (Slack)` |
| GL-models_Webhook-031 | `app/models/Webhook.java:417` | `// Pull Request` |
| GL-models_Webhook-032 | `app/models/Webhook.java:440` | `private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest) {` |
| GL-models_Webhook-033 | `app/models/Webhook.java:464` | `// Pull Request Review` |
| GL-models_Webhook-034 | `app/models/Webhook.java:487` | `private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest, Pull` |
| GL-models_Webhook-035 | `app/models/Webhook.java:506` | `// Pull Request Comment` |
| GL-models_Webhook-036 | `app/models/Webhook.java:529` | `private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest, Revi` |
| GL-models_Webhook-037 | `app/models/Webhook.java:539` | `// Pull Request Detail (Slack)` |
| GL-models_Webhook-038 | `app/models/Webhook.java:555` | `private String buildTextPropertyOnlyJSON(String requestMessage) {` |
| GL-models_Webhook-039 | `app/models/Webhook.java:562` | `private String buildRequestJsonWithAttachments(String requestMessage, ArrayNode attachments) {` |
| GL-models_Webhook-040 | `app/models/Webhook.java:570` | `private String buildRequestJsonWithThread(String requestMessage, ObjectNode thread) {` |
| GL-models_Webhook-041 | `app/models/Webhook.java:578` | `private ObjectNode buildTitleValueJSON(String title, String value, Boolean shorten) {` |
| GL-models_Webhook-042 | `app/models/Webhook.java:587` | `private ObjectNode buildAttachmentJSON(String text, ArrayNode detailFields, EventType eventType) {` |
| GL-models_Webhook-043 | `app/models/Webhook.java:597` | `private ObjectNode buildSenderJSON(User sender) {` |
| GL-models_Webhook-044 | `app/models/Webhook.java:608` | `private ObjectNode buildPusherJSON(User sender) {` |
| GL-models_Webhook-045 | `app/models/Webhook.java:616` | `private ObjectNode buildRepositoryJSON() {` |
| GL-models_Webhook-048 | `app/models/Webhook.java:670` | `private void sendRequest(String payload, Long webhookId, Resource resource) {` |
| GL-models_Webhook-049 | `app/models/Webhook.java:709` | `// Commit (message)` |
| GL-models_Webhook-050 | `app/models/Webhook.java:718` | `private String buildRequestBody(List<RevCommit> commits, List<String> refNames, User sender, String ` |
| GL-models_Webhook-051 | `app/models/Webhook.java:725` | `// Commit (json)` |
| GL-models_Webhook-052 | `app/models/Webhook.java:732` | `private String buildRequestBody(List<RevCommit> commits, List<String> refNames, User sender) {` |
| GL-models_Webhook-053 | `app/models/Webhook.java:758` | `private ObjectNode buildJSONFromCommit(Project project, RevCommit commit) {` |
| GL-models_Webhook-054 | `app/models/Webhook.java:785` | `@Override` |
| GL-models_OriginalEmail-001 | `app/models/OriginalEmail.java:32` | `@Entity` |
| GL-models_OriginalEmail-002 | `app/models/OriginalEmail.java:36` | `public static final Finder<Long, OriginalEmail> finder = new Finder<>(Long.class,` |
| GL-models_OriginalEmail-004 | `app/models/OriginalEmail.java:43` | `@Id` |
| GL-models_OriginalEmail-005 | `app/models/OriginalEmail.java:47` | `@Constraints.Required` |
| GL-models_OriginalEmail-006 | `app/models/OriginalEmail.java:52` | `@Constraints.Required` |
| GL-models_OriginalEmail-007 | `app/models/OriginalEmail.java:57` | `@Constraints.Required` |
| GL-models_OriginalEmail-008 | `app/models/OriginalEmail.java:61` | `@Constraints.Required` |
| GL-models_OriginalEmail-009 | `app/models/OriginalEmail.java:65` | `public static OriginalEmail findBy(Resource resource) {` |
| GL-models_OriginalEmail-010 | `app/models/OriginalEmail.java:73` | `public static boolean exists(Resource resource) {` |
| GL-models_OriginalEmail-011 | `app/models/OriginalEmail.java:78` | `public OriginalEmail(String messageId, Resource resource) {` |
| GL-models_OriginalEmail-012 | `app/models/OriginalEmail.java:85` | `@Override` |
| GL-models_PushedBranch-001 | `app/models/PushedBranch.java:36` | `/**` |
| GL-models_PushedBranch-003 | `app/models/PushedBranch.java:45` | `public static final Finder<Long, PushedBranch> find = new Finder<>(Long.class, PushedBranch.class);` |
| GL-models_PushedBranch-004 | `app/models/PushedBranch.java:47` | `public PushedBranch() {` |
| GL-models_PushedBranch-005 | `app/models/PushedBranch.java:51` | `public PushedBranch(Date pushedDate, String branch, Project project) {` |
| GL-models_PushedBranch-006 | `app/models/PushedBranch.java:58` | `@Id` |
| GL-models_PushedBranch-009 | `app/models/PushedBranch.java:66` | `@ManyToOne` |
| GL-models_PushedBranch-011 | `app/models/PushedBranch.java:75` | `public static void removeByPullRequestFrom(PullRequest pullRequest) {` |
| GL-models_PushedBranch-012 | `app/models/PushedBranch.java:83` | `public static List<PushedBranch> findByOwnerAndOriginalProject(User owner, Project originalProject) ` |
| GL-models_TitleHead-001 | `app/models/TitleHead.java:16` | `@Entity` |
| GL-models_TitleHead-003 | `app/models/TitleHead.java:23` | `public static final Finder<Long, TitleHead> finder = new Finder<>(Long.class, TitleHead.class);` |
| GL-models_TitleHead-004 | `app/models/TitleHead.java:26` | `@Id` |
| GL-models_TitleHead-005 | `app/models/TitleHead.java:30` | `@ManyToOne` |
| GL-models_TitleHead-008 | `app/models/TitleHead.java:40` | `public static List<TitleHead> findByProject(Project project, String query) {` |
| GL-models_TitleHead-009 | `app/models/TitleHead.java:48` | `public static TitleHead findByHeadKeyword(Project project, String headKeyword) {` |
| GL-models_TitleHead-010 | `app/models/TitleHead.java:60` | `public static void newHeadKeyword(Project project, String headKeyword) {` |
| GL-models_TitleHead-011 | `app/models/TitleHead.java:75` | `public static void reduceHeadKeyword(Project project, String headKeyword) {` |
| GL-models_TitleHead-012 | `app/models/TitleHead.java:88` | `public static void saveTitleHeadKeyword(Project project, String title) {` |
| GL-models_TitleHead-013 | `app/models/TitleHead.java:100` | `private static String removeBracket(String trimmed) {` |
| GL-models_TitleHead-015 | `app/models/TitleHead.java:112` | `public static void deleteTitleHeadKeyword(Project project, String title) {` |
| GL-models_CodeCommentThread-001 | `app/models/CodeCommentThread.java:39` | `/**` |
| GL-models_CodeCommentThread-003 | `app/models/CodeCommentThread.java:49` | `public static final Finder<Long, CodeCommentThread> find = new Finder<>(Long.class, CodeCommentThrea` |
| GL-models_CodeCommentThread-004 | `app/models/CodeCommentThread.java:52` | `@Embedded` |
| GL-models_CodeCommentThread-007 | `app/models/CodeCommentThread.java:61` | `@Transient` |
| GL-models_CodeCommentThread-008 | `app/models/CodeCommentThread.java:65` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_CodeCommentThread-010 | `app/models/CodeCommentThread.java:74` | `private String unexpectedSideMessage(Side side) {` |
| GL-models_SearchResult-001 | `app/models/SearchResult.java:32` | `public class SearchResult {` |
| GL-models_SearchResult-020 | `app/models/SearchResult.java:74` | `public List<String> makeSnippets(String contents, int threshold) {` |
| GL-models_SearchResult-021 | `app/models/SearchResult.java:111` | `private List<Integer> findIndexes(String contents, String keyword) {` |
| GL-models_SearchResult-022 | `app/models/SearchResult.java:122` | `private int beginIndex(int index, int threshold) {` |
| GL-models_SearchResult-023 | `app/models/SearchResult.java:127` | `private int endIndex(int keywordEndIndex, int contentLength, int threshold) {` |
| GL-models_SearchResult-024 | `app/models/SearchResult.java:133` | `public void updateSearchType() {` |
| GL-models_SearchResult-025 | `app/models/SearchResult.java:182` | `private class BeginAndEnd {` |
| GL-models_Watch-001 | `app/models/Watch.java:40` | `@Entity` |
| GL-models_Watch-003 | `app/models/Watch.java:46` | `public static final Finder<Long, Watch> find = new Finder<>(Long.class, Watch.class);` |
| GL-models_Watch-004 | `app/models/Watch.java:49` | `public static List<Watch> findBy(ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-005 | `app/models/Watch.java:54` | `public static Watch findBy(User watcher, ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-006 | `app/models/Watch.java:59` | `public static List<Watch> findBy(User user, ResourceType resourceType) {` |
| GL-models_Watch-007 | `app/models/Watch.java:64` | `public static int countBy(ResourceType type, String id) {` |
| GL-models_Watch-008 | `app/models/Watch.java:69` | `public static void watch(Resource resource) {` |
| GL-models_Watch-009 | `app/models/Watch.java:74` | `@Transactional` |
| GL-models_Watch-010 | `app/models/Watch.java:80` | `public static void watch(User user, ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-011 | `app/models/Watch.java:97` | `public static void unwatch(Resource resource) {` |
| GL-models_Watch-012 | `app/models/Watch.java:102` | `public static void unwatch(User user, Resource resource) {` |
| GL-models_Watch-013 | `app/models/Watch.java:107` | `public static void unwatch(User user, ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-014 | `app/models/Watch.java:124` | `public static Set<User> findWatchers(Resource target) {` |
| GL-models_Watch-015 | `app/models/Watch.java:129` | `public static Set<User> findWatchers(ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-016 | `app/models/Watch.java:138` | `public static Set<User> findUnwatchers(Resource target) {` |
| GL-models_Watch-017 | `app/models/Watch.java:143` | `public static Set<User> findUnwatchers(ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-018 | `app/models/Watch.java:152` | `public static List<String> findWatchedResourceIds(User user, ResourceType resourceType) {` |
| GL-models_Watch-022 | `app/models/Watch.java:182` | `public static Set<User> findActualWatchers(` |
| GL-models_ReviewComment-001 | `app/models/ReviewComment.java:34` | `/**` |
| GL-models_ReviewComment-003 | `app/models/ReviewComment.java:42` | `public static final Finder<Long, ReviewComment> find = new Finder<>(Long.class, ReviewComment.class)` |
| GL-models_ReviewComment-004 | `app/models/ReviewComment.java:45` | `@Id` |
| GL-models_ReviewComment-005 | `app/models/ReviewComment.java:49` | `@Lob` |
| GL-models_ReviewComment-006 | `app/models/ReviewComment.java:54` | `@Constraints.Required` |
| GL-models_ReviewComment-007 | `app/models/ReviewComment.java:58` | `@Embedded` |
| GL-models_ReviewComment-008 | `app/models/ReviewComment.java:67` | `@ManyToOne(cascade = CascadeType.ALL)` |
| GL-models_ReviewComment-011 | `app/models/ReviewComment.java:81` | `public ReviewComment() {` |
| GL-models_ReviewComment-012 | `app/models/ReviewComment.java:86` | `public static List<ReviewComment> findByThread(Long threadId) {` |
| GL-models_ReviewComment-013 | `app/models/ReviewComment.java:94` | `public Resource asResource() {` |
| GL-models_ReviewComment-014 | `app/models/ReviewComment.java:130` | `@Override` |
| GL-models_History-001 | `app/models/History.java:30` | `public class History {` |
| GL-models_History-029 | `app/models/History.java:149` | `public static List<History> makeHistory(String userName, Project project,` |
| GL-models_History-030 | `app/models/History.java:164` | `private static void buildPullRequestsHistory(String userName, Project project, List<PullRequest> pul` |
| GL-models_History-031 | `app/models/History.java:181` | `private static void sort(List<History> histories) {` |
| GL-models_History-032 | `app/models/History.java:191` | `private static void buildPostingHistory(String userName, Project project, List<Posting> postings, Li` |
| GL-models_History-033 | `app/models/History.java:208` | `private static void buildIssueHistory(String userName, Project project, List<Issue> issues, List<His` |
| GL-models_History-034 | `app/models/History.java:225` | `private static void buildCommitHistory(String userName, Project project, List<Commit> commits, List<` |
| GL-models_NotificationEvent-001 | `app/models/NotificationEvent.java:55` | `@Entity` |
| GL-models_NotificationEvent-003 | `app/models/NotificationEvent.java:61` | `@Id` |
| GL-models_NotificationEvent-004 | `app/models/NotificationEvent.java:65` | `public static final Finder<Long, NotificationEvent> find = new Finder<>(Long.class, NotificationEven` |
| GL-models_NotificationEvent-007 | `app/models/NotificationEvent.java:74` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_NotificationEvent-008 | `app/models/NotificationEvent.java:78` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_NotificationEvent-009 | `app/models/NotificationEvent.java:82` | `@Enumerated(EnumType.STRING)` |
| GL-models_NotificationEvent-011 | `app/models/NotificationEvent.java:89` | `@Enumerated(EnumType.STRING)` |
| GL-models_NotificationEvent-012 | `app/models/NotificationEvent.java:93` | `@Lob @Basic(fetch=FetchType.EAGER)` |
| GL-models_NotificationEvent-013 | `app/models/NotificationEvent.java:97` | `@Lob @Basic(fetch=FetchType.EAGER)` |
| GL-models_NotificationEvent-014 | `app/models/NotificationEvent.java:101` | `@OneToOne(mappedBy="notificationEvent", cascade = CascadeType.ALL)` |
| GL-models_NotificationEvent-015 | `app/models/NotificationEvent.java:105` | `/**` |
| GL-models_NotificationEvent-016 | `app/models/NotificationEvent.java:121` | `@Override` |
| GL-models_NotificationEvent-019 | `app/models/NotificationEvent.java:137` | `@Transient` |
| GL-models_NotificationEvent-020 | `app/models/NotificationEvent.java:143` | `@Transient` |
| GL-models_NotificationEvent-021 | `app/models/NotificationEvent.java:254` | `@Transient` |
| GL-models_NotificationEvent-022 | `app/models/NotificationEvent.java:260` | `@Transient` |
| GL-models_NotificationEvent-023 | `app/models/NotificationEvent.java:272` | `/**` |
| GL-models_NotificationEvent-028 | `app/models/NotificationEvent.java:424` | `public boolean resourceExists() {` |
| GL-models_NotificationEvent-029 | `app/models/NotificationEvent.java:429` | `public static void add(NotificationEvent event) {` |
| GL-models_NotificationEvent-030 | `app/models/NotificationEvent.java:467` | `public static void addWithoutSkipEvent(NotificationEvent event) {` |
| GL-models_NotificationEvent-034 | `app/models/NotificationEvent.java:520` | `private static void filterReceivers(final NotificationEvent event) {` |
| GL-models_NotificationEvent-035 | `app/models/NotificationEvent.java:548` | `public static void deleteBy(Resource resource) {` |
| GL-models_NotificationEvent-036 | `app/models/NotificationEvent.java:556` | `/**` |
| GL-models_NotificationEvent-047 | `app/models/NotificationEvent.java:688` | `private static void webhookRequest(EventType eventTypes, Posting post) {` |
| GL-models_NotificationEvent-048 | `app/models/NotificationEvent.java:699` | `private static void webhookRequest(EventType eventTypes, Comment comment) {` |
| GL-models_NotificationEvent-049 | `app/models/NotificationEvent.java:710` | `private static void webhookRequest(EventType eventTypes, PullRequest pullRequest, ReviewComment revi` |
| GL-models_NotificationEvent-050 | `app/models/NotificationEvent.java:721` | `private static void webhookRequest(Project project, List<RevCommit> commits, List<String> refNames, ` |
| GL-models_NotificationEvent-051 | `app/models/NotificationEvent.java:734` | `/**` |
| GL-models_NotificationEvent-052 | `app/models/NotificationEvent.java:755` | `public static NotificationEvent afterPullRequestCommitChanged(User sender, PullRequest pullRequest) ` |
| GL-models_NotificationEvent-053 | `app/models/NotificationEvent.java:770` | `private static String newPullRequestCommitChangedMessage(PullRequest pullRequest) {` |
| GL-models_NotificationEvent-054 | `app/models/NotificationEvent.java:788` | `/**` |
| GL-models_NotificationEvent-055 | `app/models/NotificationEvent.java:802` | `/**` |
| GL-models_NotificationEvent-056 | `app/models/NotificationEvent.java:811` | `public static NotificationEvent forNewComment(User sender, PullRequest pullRequest, ReviewComment ne` |
| GL-models_NotificationEvent-057 | `app/models/NotificationEvent.java:827` | `public static NotificationEvent afterNewPullRequest(PullRequest pullRequest) {` |
| GL-models_NotificationEvent-058 | `app/models/NotificationEvent.java:833` | `public static NotificationEvent afterPullRequestUpdated(PullRequest pullRequest, State oldState, Sta` |
| GL-models_NotificationEvent-059 | `app/models/NotificationEvent.java:838` | `public static void afterNewComment(Comment comment) {` |
| GL-models_NotificationEvent-060 | `app/models/NotificationEvent.java:844` | `public static NotificationEvent forComment(Comment comment, User author, EventType eventType) {` |
| GL-models_NotificationEvent-061 | `app/models/NotificationEvent.java:859` | `public static NotificationEvent forUpdatedComment(Comment comment, User author) {` |
| GL-models_NotificationEvent-062 | `app/models/NotificationEvent.java:864` | `public static NotificationEvent forNewComment(Comment comment, User author) {` |
| GL-models_NotificationEvent-063 | `app/models/NotificationEvent.java:869` | `public static void afterNewCommentWithState(Comment comment, State state) {` |
| GL-models_NotificationEvent-064 | `app/models/NotificationEvent.java:888` | `public static NotificationEvent afterStateChanged(State oldState, Issue issue) {` |
| GL-models_NotificationEvent-065 | `app/models/NotificationEvent.java:902` | `public static NotificationEvent afterStateChanged(` |
| GL-models_NotificationEvent-066 | `app/models/NotificationEvent.java:938` | `public static NotificationEvent afterAssigneeChanged(User oldAssignee, Issue issue) {` |
| GL-models_NotificationEvent-068 | `app/models/NotificationEvent.java:973` | `public static NotificationEvent afterNewIssue(Issue issue) {` |
| GL-models_NotificationEvent-069 | `app/models/NotificationEvent.java:981` | `public static NotificationEvent forNewIssue(Issue issue, User author) {` |
| GL-models_NotificationEvent-070 | `app/models/NotificationEvent.java:992` | `public static NotificationEvent afterResourceDeleted(AbstractPosting item, User reuqestedUser) {` |
| GL-models_NotificationEvent-071 | `app/models/NotificationEvent.java:1008` | `public static NotificationEvent afterIssueBodyChanged(String oldBody, Issue issue) {` |
| GL-models_NotificationEvent-072 | `app/models/NotificationEvent.java:1024` | `public static NotificationEvent afterIssueMoved(Project previous, Issue issue, Supplier<Set<User>> g` |
| GL-models_NotificationEvent-073 | `app/models/NotificationEvent.java:1040` | `public static NotificationEvent afterIssueSharerChanged(Issue issue, String sharerLoginId, String ac` |
| GL-models_NotificationEvent-074 | `app/models/NotificationEvent.java:1059` | `private static Set<User> findSharer(String sharerLoginId) {` |
| GL-models_NotificationEvent-075 | `app/models/NotificationEvent.java:1066` | `public static NotificationEvent afterIssueLabelChanged(String addedLabels, String deletedLabels, Iss` |
| GL-models_NotificationEvent-076 | `app/models/NotificationEvent.java:1079` | `public static NotificationEvent afterMilestoneChanged(Long oldMilestoneId, Issue issue) {` |
| GL-models_NotificationEvent-078 | `app/models/NotificationEvent.java:1123` | `private static User findCurrentUserToBeExcluded(Long authorId) {` |
| GL-models_NotificationEvent-081 | `app/models/NotificationEvent.java:1186` | `private static Set<User> filterInactiveUsers(Set<User> receivers) {` |
| GL-models_NotificationEvent-083 | `app/models/NotificationEvent.java:1208` | `private static Set<User> findMembersOnlyFromWatchers(Project project) {` |
| GL-models_NotificationEvent-084 | `app/models/NotificationEvent.java:1220` | `private static Set<User> extractMembers(Project project) {` |
| GL-models_NotificationEvent-086 | `app/models/NotificationEvent.java:1237` | `public static void afterNewPost(Posting post) {` |
| GL-models_NotificationEvent-087 | `app/models/NotificationEvent.java:1243` | `public static void afterUpdatePosting(String oldValue, Posting post) {` |
| GL-models_NotificationEvent-088 | `app/models/NotificationEvent.java:1248` | `public static NotificationEvent forNewPosting(Posting post, User author) {` |
| GL-models_NotificationEvent-089 | `app/models/NotificationEvent.java:1259` | `public static NotificationEvent forUpdatePosting(String oldValue, Posting post, User author) {` |
| GL-models_NotificationEvent-090 | `app/models/NotificationEvent.java:1270` | `public static void afterNewCommitComment(Project project, ReviewComment comment,` |
| GL-models_NotificationEvent-091 | `app/models/NotificationEvent.java:1278` | `public static NotificationEvent forNewCommitComment(` |
| GL-models_NotificationEvent-092 | `app/models/NotificationEvent.java:1296` | `public static void afterNewSVNCommitComment(Project project, CommitComment codeComment)` |
| GL-models_NotificationEvent-093 | `app/models/NotificationEvent.java:1302` | `private static NotificationEvent forNewSVNCommitComment(` |
| GL-models_NotificationEvent-094 | `app/models/NotificationEvent.java:1320` | `public static void afterMemberRequest(Project project, User user, RequestState state) {` |
| GL-models_NotificationEvent-095 | `app/models/NotificationEvent.java:1352` | `public static void afterOrganizationMemberRequest(Organization organization, User user, RequestState` |
| GL-models_NotificationEvent-097 | `app/models/NotificationEvent.java:1399` | `public static NotificationEvent afterReviewed(PullRequest pullRequest, PullRequestReviewAction revie` |
| GL-models_NotificationEvent-099 | `app/models/NotificationEvent.java:1459` | `private static NotificationEvent createFrom(User sender, ResourceConvertible rc) {` |
| GL-models_NotificationEvent-103 | `app/models/NotificationEvent.java:1491` | `private static void includeAssigneeIfExist(Comment comment, Set<User> receivers) {` |
| GL-models_NotificationEvent-105 | `app/models/NotificationEvent.java:1510` | `private static String formatReplyTitle(AbstractPosting posting) {` |
| GL-models_NotificationEvent-106 | `app/models/NotificationEvent.java:1516` | `private static String formatNewTitle(AbstractPosting posting) {` |
| GL-models_NotificationEvent-111 | `app/models/NotificationEvent.java:1570` | `private static String formatNewTitle(PullRequest pullRequest) {` |
| GL-models_NotificationEvent-112 | `app/models/NotificationEvent.java:1576` | `private static String formatReplyTitle(PullRequest pullRequest) {` |
| GL-models_NotificationEvent-115 | `app/models/NotificationEvent.java:1603` | `private static String formatMemberRequestTitle(Project project, User user) {` |
| GL-models_NotificationEvent-116 | `app/models/NotificationEvent.java:1608` | `private static String formatMemberRequestCancelTitle(Project project, User user) {` |
| GL-models_NotificationEvent-117 | `app/models/NotificationEvent.java:1613` | `private static String formatMemberRequestCancelTitle(Organization organization, User user) {` |
| GL-models_NotificationEvent-118 | `app/models/NotificationEvent.java:1618` | `private static String formatMemberRequestTitle(Organization organization, User user) {` |
| GL-models_NotificationEvent-119 | `app/models/NotificationEvent.java:1623` | `private static String formatMemberAcceptTitle(Project project, User user) {` |
| GL-models_NotificationEvent-120 | `app/models/NotificationEvent.java:1628` | `private static String formatMemberAcceptTitle(Organization organization, User user) {` |
| GL-models_NotificationEvent-121 | `app/models/NotificationEvent.java:1633` | `/**` |
| GL-models_NotificationEvent-122 | `app/models/NotificationEvent.java:1653` | `/**` |
| GL-models_NotificationEvent-123 | `app/models/NotificationEvent.java:1673` | `private static Set<User> findOrganizationMembers(String mentionWord) {` |
| GL-models_NotificationEvent-124 | `app/models/NotificationEvent.java:1685` | `private static Set<User> findProjectMembers(String mentionWord) {` |
| GL-models_NotificationEvent-125 | `app/models/NotificationEvent.java:1702` | `public static void scheduleDeleteOldNotifications() {` |
| GL-models_NotificationEvent-126 | `app/models/NotificationEvent.java:1725` | `public static void onStart() {` |
| GL-models_NotificationEvent-127 | `app/models/NotificationEvent.java:1730` | `/**` |
| GL-models_NotificationEvent-129 | `app/models/NotificationEvent.java:1768` | `public static void afterCommentUpdated(Comment comment) {` |
| GL-models_NotificationEvent-130 | `app/models/NotificationEvent.java:1774` | `@Override` |
| GL-models_FavoriteOrganization-001 | `app/models/FavoriteOrganization.java:18` | `@Entity` |
| GL-models_FavoriteOrganization-002 | `app/models/FavoriteOrganization.java:21` | `public static Finder<Long, FavoriteOrganization> finder = new Finder<>(Long.class, FavoriteOrganizat` |
| GL-models_FavoriteOrganization-003 | `app/models/FavoriteOrganization.java:24` | `@Id` |
| GL-models_FavoriteOrganization-004 | `app/models/FavoriteOrganization.java:28` | `@ManyToOne` |
| GL-models_FavoriteOrganization-005 | `app/models/FavoriteOrganization.java:32` | `@OneToOne` |
| GL-models_FavoriteOrganization-008 | `app/models/FavoriteOrganization.java:47` | `public static void updateFavoriteOrganization(Organization organization) {` |
| GL-models_CommentThread-001 | `app/models/CommentThread.java:38` | `/**` |
| GL-models_CommentThread-003 | `app/models/CommentThread.java:48` | `public static final Finder<Long, CommentThread> find = new Finder<>(Long.class, CommentThread.class)` |
| GL-models_CommentThread-004 | `app/models/CommentThread.java:51` | `@Id` |
| GL-models_CommentThread-005 | `app/models/CommentThread.java:55` | `@Embedded` |
| GL-models_CommentThread-006 | `app/models/CommentThread.java:64` | `@OneToMany(mappedBy = "thread", cascade = CascadeType.REMOVE)` |
| GL-models_CommentThread-007 | `app/models/CommentThread.java:68` | `@Enumerated(EnumType.STRING)` |
| GL-models_CommentThread-008 | `app/models/CommentThread.java:72` | `@Constraints.Required` |
| GL-models_CommentThread-009 | `app/models/CommentThread.java:77` | `@ManyToOne` |
| GL-models_CommentThread-011 | `app/models/CommentThread.java:86` | `public static List<CommentThread> findByCommitId(String commitId) {` |
| GL-models_CommentThread-012 | `app/models/CommentThread.java:94` | `public static <T extends CommentThread> List<T> findByCommitId(Finder<Long, T> find,` |
| GL-models_CommentThread-013 | `app/models/CommentThread.java:105` | `public static List<CommentThread> findByCommitIdAndState(String commitId, ThreadState state) {` |
| GL-models_CommentThread-014 | `app/models/CommentThread.java:114` | `@Override` |
| GL-models_CommentThread-015 | `app/models/CommentThread.java:127` | `@ManyToOne` |
| GL-models_CommentThread-016 | `app/models/CommentThread.java:131` | `public Resource asResource() {` |
| GL-models_CommentThread-017 | `app/models/CommentThread.java:156` | `public void removeComment(ReviewComment reviewComment) {` |
| GL-models_CommentThread-018 | `app/models/CommentThread.java:162` | `public enum ThreadState {` |
| GL-models_CommentThread-019 | `app/models/CommentThread.java:167` | `public void addComment(ReviewComment reviewComment) {` |
| GL-models_CommentThread-021 | `app/models/CommentThread.java:183` | `/**` |
| GL-models_CommentThread-022 | `app/models/CommentThread.java:201` | `public static int count(PullRequest pullRequest, String commitId, String path) {` |
| GL-models_CommentThread-023 | `app/models/CommentThread.java:221` | `public static int countOnCommit(Project project, String commitId, String path) {` |
| GL-models_CommentThread-025 | `app/models/CommentThread.java:253` | `public boolean hasChildComments(){` |
| GL-models_CommentThread-026 | `app/models/CommentThread.java:262` | `public static void deleteByPullRequest(PullRequest pullRequest) {` |
| GL-models_Project-001 | `app/models/Project.java:46` | `@Entity` |
| GL-models_Project-003 | `app/models/Project.java:51` | `public static final play.db.ebean.Model.Finder <Long, Project> find = new Finder<>(Long.class, Proje` |
| GL-models_Project-005 | `app/models/Project.java:57` | `@Id` |
| GL-models_Project-006 | `app/models/Project.java:61` | `@Constraints.Required` |
| GL-models_Project-012 | `app/models/Project.java:79` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-013 | `app/models/Project.java:83` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-014 | `app/models/Project.java:87` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-015 | `app/models/Project.java:91` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-016 | `app/models/Project.java:95` | `/** Project Notification */` |
| GL-models_Project-020 | `app/models/Project.java:109` | `@ManyToMany` |
| GL-models_Project-021 | `app/models/Project.java:113` | `@ManyToOne` |
| GL-models_Project-022 | `app/models/Project.java:117` | `@OneToMany(mappedBy = "originalProject")` |
| GL-models_Project-023 | `app/models/Project.java:121` | `@OneToMany(mappedBy = "project")` |
| GL-models_Project-024 | `app/models/Project.java:125` | `@OneToMany(mappedBy = "project")` |
| GL-models_Project-027 | `app/models/Project.java:136` | `@ManyToMany(mappedBy = "enrolledProjects")` |
| GL-models_Project-028 | `app/models/Project.java:140` | `@OneToMany(cascade = CascadeType.REMOVE)` |
| GL-models_Project-029 | `app/models/Project.java:144` | `@OneToMany(cascade = CascadeType.REMOVE)` |
| GL-models_Project-032 | `app/models/Project.java:154` | `@ManyToOne` |
| GL-models_Project-033 | `app/models/Project.java:158` | `@Enumerated(EnumType.STRING)` |
| GL-models_Project-034 | `app/models/Project.java:162` | `@OneToOne(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-038 | `app/models/Project.java:173` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-039 | `app/models/Project.java:177` | `/**` |
| GL-models_Project-042 | `app/models/Project.java:221` | `public static List<Project> findByOwner(String loginId) {` |
| GL-models_Project-043 | `app/models/Project.java:226` | `public Set<User> findAuthors() {` |
| GL-models_Project-044 | `app/models/Project.java:236` | `public Set<User> findAuthorsAndWatchers() {` |
| GL-models_Project-049 | `app/models/Project.java:269` | `public boolean hasMember(User user) {` |
| GL-models_Project-050 | `app/models/Project.java:280` | `public static boolean exists(String loginId, String projectName) {` |
| GL-models_Project-051 | `app/models/Project.java:287` | `public static boolean projectNameChangeable(Long id, String userName,` |
| GL-models_Project-052 | `app/models/Project.java:295` | `/**` |
| GL-models_Project-053 | `app/models/Project.java:313` | `public static List<Project> findProjectsByMember(Long userId) {` |
| GL-models_Project-054 | `app/models/Project.java:318` | `public static List<Project> findProjectsJustMemberAndNotOwner(User user) {` |
| GL-models_Project-055 | `app/models/Project.java:323` | `public static List<Project> findProjectsJustMemberAndNotOwner(User user, String orderString) {` |
| GL-models_Project-056 | `app/models/Project.java:336` | `public static List<Project> findProjectsByMemberWithFilter(Long userId, String orderString) {` |
| GL-models_Project-057 | `app/models/Project.java:346` | `public static List<Project> findProjectsCreatedByUser(String loginId, String orderString) {` |
| GL-models_Project-058 | `app/models/Project.java:356` | `public static List<Project> findProjectsCreatedByUserAndScope(String loginId, ProjectScope projectSc` |
| GL-models_Project-059 | `app/models/Project.java:363` | `public Date lastUpdateDate() {` |
| GL-models_Project-060 | `app/models/Project.java:390` | `public String defaultBranch() {` |
| GL-models_Project-061 | `app/models/Project.java:399` | `public Duration ago() {` |
| GL-models_Project-062 | `app/models/Project.java:404` | `public Duration lastPushedDateAgo(){` |
| GL-models_Project-063 | `app/models/Project.java:412` | `public String readme() {` |
| GL-models_Project-065 | `app/models/Project.java:434` | `/**` |
| GL-models_Project-072 | `app/models/Project.java:514` | `/**` |
| GL-models_Project-073 | `app/models/Project.java:530` | `public static void fixLastIssueNumber(Long projectId) {` |
| GL-models_Project-074 | `app/models/Project.java:538` | `public static Long increaseLastPostingNumber(Long projectId) {` |
| GL-models_Project-075 | `app/models/Project.java:547` | `public static void fixLastPostingNumber(Long projectId) {` |
| GL-models_Project-076 | `app/models/Project.java:555` | `public Resource labelsAsResource() {` |
| GL-models_Project-077 | `app/models/Project.java:577` | `@Override` |
| GL-models_Project-079 | `app/models/Project.java:605` | `public Boolean attachLabel(Label label) {` |
| GL-models_Project-080 | `app/models/Project.java:619` | `public void detachLabel(Label label) {` |
| GL-models_Project-082 | `app/models/Project.java:634` | `public String toString() {` |
| GL-models_Project-089 | `app/models/Project.java:682` | `public boolean hasForks() {` |
| GL-models_Project-091 | `app/models/Project.java:695` | `public void addFork(Project forkProject) {` |
| GL-models_Project-092 | `app/models/Project.java:701` | `public static List<Project> findByOwnerAndOriginalProject(String loginId, Project originalProject) {` |
| GL-models_Project-093 | `app/models/Project.java:709` | `public void deleteFork() {` |
| GL-models_Project-094 | `app/models/Project.java:716` | `private void deleteFork(Project project) {` |
| GL-models_Project-095 | `app/models/Project.java:722` | `public void fixInvalidForkData() {` |
| GL-models_Project-096 | `app/models/Project.java:734` | `/**` |
| GL-models_Project-097 | `app/models/Project.java:755` | `public void changeVCS() throws Exception {` |
| GL-models_Project-099 | `app/models/Project.java:775` | `/**` |
| GL-models_Project-101 | `app/models/Project.java:794` | `private static boolean hasPassed24hoursFrom(Long time) {` |
| GL-models_Project-102 | `app/models/Project.java:799` | `public enum State {` |
| GL-models_Project-103 | `app/models/Project.java:804` | `/**` |
| GL-models_Project-104 | `app/models/Project.java:859` | `private void deleteProjectTransfer() {` |
| GL-models_Project-105 | `app/models/Project.java:867` | `private void deleteOriginal() {` |
| GL-models_Project-106 | `app/models/Project.java:872` | `private void deletePullRequests() {` |
| GL-models_Project-107 | `app/models/Project.java:887` | `private void deleteCommentThreads() {` |
| GL-models_Project-108 | `app/models/Project.java:894` | `public static String newProjectName(String loginId, String projectName) {` |
| GL-models_Project-109 | `app/models/Project.java:910` | `/**` |
| GL-models_Project-110 | `app/models/Project.java:928` | `@Override` |
| GL-models_Project-111 | `app/models/Project.java:934` | `public static int countProjectsJustMemberAndNotOwner(String loginId) {` |
| GL-models_Project-112 | `app/models/Project.java:940` | `public static int countProjectsCreatedByUser(String loginId) {` |
| GL-models_Project-119 | `app/models/Project.java:994` | `public boolean hasGroup() {` |
| GL-models_Project-123 | `app/models/Project.java:1014` | `public String nextVCS() {` |
| GL-models_Project-124 | `app/models/Project.java:1023` | `/**` |
| GL-models_Project-125 | `app/models/Project.java:1049` | `public boolean hasOldPlace(){` |
| GL-models_PostingComment-001 | `app/models/PostingComment.java:20` | `@Entity` |
| GL-models_PostingComment-003 | `app/models/PostingComment.java:25` | `public static final Finder<Long, PostingComment> find = new Finder<>(Long.class, PostingComment.clas` |
| GL-models_PostingComment-004 | `app/models/PostingComment.java:28` | `@ManyToOne` |
| GL-models_PostingComment-005 | `app/models/PostingComment.java:32` | `@OneToOne` |
| GL-models_PostingComment-006 | `app/models/PostingComment.java:36` | `public PostingComment(Posting posting, User author, String contents) {` |
| GL-models_PostingComment-007 | `app/models/PostingComment.java:43` | `/**` |
| GL-models_PostingComment-008 | `app/models/PostingComment.java:51` | `@Override` |
| GL-models_PostingComment-009 | `app/models/PostingComment.java:57` | `@Override` |
| GL-models_PostingComment-010 | `app/models/PostingComment.java:63` | `@Override` |
| GL-models_PostingComment-011 | `app/models/PostingComment.java:76` | `@Override` |
| GL-models_PostingComment-012 | `app/models/PostingComment.java:85` | `/**` |
| GL-models_PostingComment-013 | `app/models/PostingComment.java:119` | `public static List<PostingComment> findAllBy(Posting posting) {` |
| GL-models_PostingComment-014 | `app/models/PostingComment.java:126` | `public static int countAllCreatedBy(User user) {` |
| GL-models_PullRequest-001 | `app/models/PullRequest.java:60` | `@Entity` |
| GL-models_PullRequest-004 | `app/models/PullRequest.java:69` | `public static final Finder<Long, PullRequest> finder = new Finder<>(Long.class, PullRequest.class);` |
| GL-models_PullRequest-006 | `app/models/PullRequest.java:75` | `@Id` |
| GL-models_PullRequest-007 | `app/models/PullRequest.java:79` | `@Constraints.Required` |
| GL-models_PullRequest-008 | `app/models/PullRequest.java:84` | `@Lob` |
| GL-models_PullRequest-009 | `app/models/PullRequest.java:88` | `@Transient` |
| GL-models_PullRequest-010 | `app/models/PullRequest.java:91` | `@Transient` |
| GL-models_PullRequest-011 | `app/models/PullRequest.java:95` | `@ManyToOne` |
| GL-models_PullRequest-012 | `app/models/PullRequest.java:99` | `@ManyToOne` |
| GL-models_PullRequest-013 | `app/models/PullRequest.java:103` | `@Constraints.Required` |
| GL-models_PullRequest-014 | `app/models/PullRequest.java:108` | `@Constraints.Required` |
| GL-models_PullRequest-015 | `app/models/PullRequest.java:113` | `@ManyToOne` |
| GL-models_PullRequest-016 | `app/models/PullRequest.java:117` | `@ManyToOne` |
| GL-models_PullRequest-017 | `app/models/PullRequest.java:121` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_PullRequest-018 | `app/models/PullRequest.java:125` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_PullRequest-019 | `app/models/PullRequest.java:129` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_PullRequest-023 | `app/models/PullRequest.java:141` | `@OneToMany(cascade = CascadeType.ALL)` |
| GL-models_PullRequest-024 | `app/models/PullRequest.java:145` | `@OneToMany(cascade = CascadeType.ALL)` |
| GL-models_PullRequest-029 | `app/models/PullRequest.java:162` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_PullRequest-030 | `app/models/PullRequest.java:172` | `@OneToMany(mappedBy = "pullRequest")` |
| GL-models_PullRequest-031 | `app/models/PullRequest.java:176` | `@Transient` |
| GL-models_PullRequest-032 | `app/models/PullRequest.java:180` | `public static PullRequest createNewPullRequest(Project fromProject, Project toProject, String fromBr` |
| GL-models_PullRequest-033 | `app/models/PullRequest.java:190` | `@Override` |
| GL-models_PullRequest-034 | `app/models/PullRequest.java:210` | `public static void onStart() {` |
| GL-models_PullRequest-035 | `app/models/PullRequest.java:216` | `public Duration createdAgo() {` |
| GL-models_PullRequest-039 | `app/models/PullRequest.java:236` | `public static PullRequest findById(long id) {` |
| GL-models_PullRequest-040 | `app/models/PullRequest.java:241` | `public static PullRequest findDuplicatedPullRequest(PullRequest pullRequest) {` |
| GL-models_PullRequest-041 | `app/models/PullRequest.java:252` | `public static List<PullRequest> findOpendPullRequests(Project project) {` |
| GL-models_PullRequest-042 | `app/models/PullRequest.java:261` | `public static List<PullRequest> findOpendPullRequestsByDaysAgo(User user, int days) {` |
| GL-models_PullRequest-043 | `app/models/PullRequest.java:270` | `public static List<PullRequest> findClosedPullRequests(Project project) {` |
| GL-models_PullRequest-044 | `app/models/PullRequest.java:279` | `public static List<PullRequest> findSentPullRequests(Project project) {` |
| GL-models_PullRequest-045 | `app/models/PullRequest.java:287` | `public static List<PullRequest> findAcceptedPullRequests(Project project) {` |
| GL-models_PullRequest-046 | `app/models/PullRequest.java:296` | `public static List<PullRequest> allReceivedRequests(Project project) {` |
| GL-models_PullRequest-047 | `app/models/PullRequest.java:304` | `public static List<PullRequest> findRecentlyReceived(Project project, int size) {` |
| GL-models_PullRequest-048 | `app/models/PullRequest.java:313` | `public static List<PullRequest> findRecentlyReceivedOpen(Project project, int size) {` |
| GL-models_PullRequest-049 | `app/models/PullRequest.java:323` | `public static int countOpenedPullRequests(Project project) {` |
| GL-models_PullRequest-050 | `app/models/PullRequest.java:331` | `public static List<PullRequest> findRelatedPullRequests(Project project, String branch) {` |
| GL-models_PullRequest-051 | `app/models/PullRequest.java:346` | `@Override` |
| GL-models_PullRequest-052 | `app/models/PullRequest.java:372` | `public void updateWith(PullRequest newPullRequest) {` |
| GL-models_PullRequest-053 | `app/models/PullRequest.java:385` | `public boolean hasSameBranchesWith(PullRequest pullRequest) {` |
| GL-models_PullRequest-056 | `app/models/PullRequest.java:400` | `/**` |
| GL-models_PullRequest-057 | `app/models/PullRequest.java:409` | `public void restoreFromBranch() {` |
| GL-models_PullRequest-058 | `app/models/PullRequest.java:414` | `public class Merger {` |
| GL-models_PullRequest-059 | `app/models/PullRequest.java:592` | `public void merge(final PullRequestEventMessage message) throws IOException, GitAPIException, PullRe` |
| GL-models_PullRequest-060 | `app/models/PullRequest.java:614` | `public String fetchSourceBranch() throws IOException, GitAPIException {` |
| GL-models_PullRequest-061 | `app/models/PullRequest.java:621` | `public void updateMergedCommitId(Merger.MergeResult mergeResult) {` |
| GL-models_PullRequest-065 | `app/models/PullRequest.java:656` | `/**` |
| GL-models_PullRequest-066 | `app/models/PullRequest.java:699` | `private void addReviewers(StringBuilder builder) {` |
| GL-models_PullRequest-068 | `app/models/PullRequest.java:717` | `private void addCommitMessages(List<GitCommit> commits, StringBuilder builder) {` |
| GL-models_PullRequest-069 | `app/models/PullRequest.java:726` | `private void changeState(State state) {` |
| GL-models_PullRequest-070 | `app/models/PullRequest.java:731` | `private void changeState(State state, User updater) {` |
| GL-models_PullRequest-071 | `app/models/PullRequest.java:738` | `public void reopen() {` |
| GL-models_PullRequest-072 | `app/models/PullRequest.java:744` | `public void close() {` |
| GL-models_PullRequest-073 | `app/models/PullRequest.java:749` | `public static List<PullRequest> findByToProject(Project project) {` |
| GL-models_PullRequest-074 | `app/models/PullRequest.java:754` | `public static List<PullRequest> findByFromProjectAndBranch(Project fromProject, String fromBranch) {` |
| GL-models_PullRequest-075 | `app/models/PullRequest.java:760` | `@Transactional` |
| GL-models_PullRequest-076 | `app/models/PullRequest.java:769` | `public static long nextPullRequestNumber(Project project) {` |
| GL-models_PullRequest-077 | `app/models/PullRequest.java:783` | `public static PullRequest findOne(Project toProject, long number) {` |
| GL-models_PullRequest-078 | `app/models/PullRequest.java:791` | `@Transactional` |
| GL-models_PullRequest-081 | `app/models/PullRequest.java:827` | `@Transient` |
| GL-models_PullRequest-082 | `app/models/PullRequest.java:834` | `public static Page<PullRequest> findPagingList(SearchCondition condition) {` |
| GL-models_PullRequest-083 | `app/models/PullRequest.java:842` | `public static int count(SearchCondition condition) {` |
| GL-models_PullRequest-084 | `app/models/PullRequest.java:847` | `private static ExpressionList<PullRequest> createSearchExpressionList(SearchCondition condition) {` |
| GL-models_PullRequest-085 | `app/models/PullRequest.java:890` | `private static Expression createStateSearchExpression(State[] states) {` |
| GL-models_PullRequest-086 | `app/models/PullRequest.java:903` | `private void addNewIssueEvents() {` |
| GL-models_PullRequest-087 | `app/models/PullRequest.java:918` | `public void deleteIssueEvents() {` |
| GL-models_PullRequest-088 | `app/models/PullRequest.java:933` | `@Override` |
| GL-models_PullRequest-089 | `app/models/PullRequest.java:940` | `@Transient` |
| GL-models_PullRequest-090 | `app/models/PullRequest.java:946` | `@Transient` |
| GL-models_PullRequest-091 | `app/models/PullRequest.java:952` | `private FetchResult fetchSourceBranchTo(String destination) throws IOException,` |
| GL-models_PullRequest-092 | `app/models/PullRequest.java:964` | `public PullRequestMergeResult updateMerge() throws IOException, GitAPIException, PullRequestExceptio` |
| GL-models_PullRequest-095 | `app/models/PullRequest.java:1008` | `public String fetchSourceTemporarilly() throws IOException, GitAPIException {` |
| GL-models_PullRequest-096 | `app/models/PullRequest.java:1017` | `// locking this repository is required because of fetch and update` |
| GL-models_PullRequest-097 | `app/models/PullRequest.java:1048` | `public void startMerge() {` |
| GL-models_PullRequest-098 | `app/models/PullRequest.java:1053` | `public void endMerge() {` |
| GL-models_PullRequest-101 | `app/models/PullRequest.java:1106` | `public static PullRequest findTheLatestOneFrom(Project fromProject, String fromBranch) {` |
| GL-models_PullRequest-102 | `app/models/PullRequest.java:1124` | `public static void changeStateToClosed() {` |
| GL-models_PullRequest-103 | `app/models/PullRequest.java:1135` | `public void clearReviewers() {` |
| GL-models_PullRequest-105 | `app/models/PullRequest.java:1146` | `public void addReviewer(User user) {` |
| GL-models_PullRequest-106 | `app/models/PullRequest.java:1153` | `public void removeReviewer(User user) {` |
| GL-models_PullRequest-112 | `app/models/PullRequest.java:1230` | `public int countCommentThreadsByState(CommentThread.ThreadState state){` |
| GL-models_PullRequest-114 | `app/models/PullRequest.java:1251` | `public void removeCommentThread(CommentThread commentThread) {` |
| GL-models_PullRequest-115 | `app/models/PullRequest.java:1257` | `public void addCommentThread(CommentThread thread) {` |
| GL-models_PullRequest-116 | `app/models/PullRequest.java:1263` | `static public boolean noChangesBetween(Repository repoA, String rev1,` |
| GL-models_resource_Resource-001 | `app/models/resource/Resource.java:34` | `public abstract class Resource {` |
| GL-models_resource_Resource-002 | `app/models/resource/Resource.java:36` | `public static boolean exists(ResourceType type, String id) {` |
| GL-models_resource_Resource-004 | `app/models/resource/Resource.java:114` | `public static Resource get(ResourceType resourceType, String resourceId) {` |
| GL-models_resource_Resource-005 | `app/models/resource/Resource.java:175` | `public ResourceParam asParameter() {` |
| GL-models_resource_Resource-012 | `app/models/resource/Resource.java:192` | `public void delete() { throw new UnsupportedOperationException(); }` |
| GL-models_resource_Resource-014 | `app/models/resource/Resource.java:207` | `@Override` |
| GL-models_resource_Resource-015 | `app/models/resource/Resource.java:221` | `@Override` |
| GL-models_resource_Resource-016 | `app/models/resource/Resource.java:230` | `@Override` |
| GL-models_resource_Resource-018 | `app/models/resource/Resource.java:241` | `/**` |
| GL-models_resource_Resource-019 | `app/models/resource/Resource.java:268` | `/**` |
| GL-models_resource_GlobalResource-001 | `app/models/resource/GlobalResource.java:26` | `abstract public class GlobalResource extends Resource {` |
| GL-models_resource_GlobalResource-002 | `app/models/resource/GlobalResource.java:28` | `@Override` |
| GL-models_resource_ResourceParam-001 | `app/models/resource/ResourceParam.java:30` | `public class ResourceParam implements QueryStringBindable<ResourceParam> {` |
| GL-models_resource_ResourceParam-003 | `app/models/resource/ResourceParam.java:36` | `public static ResourceParam get(Resource resource) {` |
| GL-models_resource_ResourceParam-004 | `app/models/resource/ResourceParam.java:43` | `@Override` |
| GL-models_resource_ResourceParam-005 | `app/models/resource/ResourceParam.java:56` | `@Override` |
| GL-models_resource_ResourceParam-006 | `app/models/resource/ResourceParam.java:63` | `@Override` |
| GL-models_resource_ResourcePersistAdapter-001 | `app/models/resource/ResourcePersistAdapter.java:32` | `/**` |
| GL-models_resource_ResourcePersistAdapter-002 | `app/models/resource/ResourcePersistAdapter.java:38` | `/**` |
| GL-models_resource_ResourcePersistAdapter-003 | `app/models/resource/ResourcePersistAdapter.java:47` | `/**` |
| GL-models_resource_ResourcePersistAdapter-004 | `app/models/resource/ResourcePersistAdapter.java:63` | `private void deleteRelatedWatch(Resource resource, EbeanServer server, Transaction transaction) {` |
| GL-models_resource_ResourcePersistAdapter-005 | `app/models/resource/ResourcePersistAdapter.java:70` | `private void deleteRelatedUnwatch(Resource resource, EbeanServer server, Transaction transaction) {` |
| GL-models_resource_ResourceConvertible-001 | `app/models/resource/ResourceConvertible.java:24` | `/**` |
| GL-models_resource_ResourceConvertible-002 | `app/models/resource/ResourceConvertible.java:29` | `/**` |
| GL-models_enumeration_Direction-001 | `app/models/enumeration/Direction.java:24` | `public enum Direction {` |
| GL-models_enumeration_Direction-002 | `app/models/enumeration/Direction.java:28` | `ASC("asc"), DESC("desc");` |
| GL-models_enumeration_Direction-003 | `app/models/enumeration/Direction.java:28` | `ASC("asc"), DESC("desc");` |
| GL-models_enumeration_Direction-005 | `app/models/enumeration/Direction.java:34` | `Direction(String direction) {` |
| GL-models_enumeration_Direction-006 | `app/models/enumeration/Direction.java:39` | `public String direction() {` |
| GL-models_enumeration_IssueFilterType-001 | `app/models/enumeration/IssueFilterType.java:4` | `public enum IssueFilterType {` |
| GL-models_enumeration_IssueFilterType-002 | `app/models/enumeration/IssueFilterType.java:6` | `ASSIGNED("assigned"),` |
| GL-models_enumeration_IssueFilterType-003 | `app/models/enumeration/IssueFilterType.java:8` | `CREATED("created"),` |
| GL-models_enumeration_IssueFilterType-004 | `app/models/enumeration/IssueFilterType.java:10` | `MENTIONED("mentioned"),` |
| GL-models_enumeration_IssueFilterType-005 | `app/models/enumeration/IssueFilterType.java:12` | `FAVORITE("favorite"),` |
| GL-models_enumeration_IssueFilterType-006 | `app/models/enumeration/IssueFilterType.java:14` | `ALL("all");` |
| GL-models_enumeration_IssueFilterType-008 | `app/models/enumeration/IssueFilterType.java:20` | `IssueFilterType(String issueFilter) {` |
| GL-models_enumeration_UserState-001 | `app/models/enumeration/UserState.java:24` | `public enum UserState {` |
| GL-models_enumeration_UserState-002 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-003 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-004 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-005 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-006 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-008 | `app/models/enumeration/UserState.java:36` | `UserState(String state) {` |
| GL-models_enumeration_UserState-009 | `app/models/enumeration/UserState.java:41` | `public String state() {` |
| GL-models_enumeration_UserState-010 | `app/models/enumeration/UserState.java:46` | `public static UserState of(String value) {` |
| GL-models_enumeration_ResourceType-001 | `app/models/enumeration/ResourceType.java:27` | `public enum ResourceType {` |
| GL-models_enumeration_ResourceType-002 | `app/models/enumeration/ResourceType.java:29` | `ISSUE_POST("issue_post"),` |
| GL-models_enumeration_ResourceType-003 | `app/models/enumeration/ResourceType.java:31` | `ISSUE_ASSIGNEE("issue_assignee"),` |
| GL-models_enumeration_ResourceType-004 | `app/models/enumeration/ResourceType.java:33` | `ISSUE_STATE("issue_state"),` |
| GL-models_enumeration_ResourceType-005 | `app/models/enumeration/ResourceType.java:35` | `ISSUE_CATEGORY("issue_category"),` |
| GL-models_enumeration_ResourceType-006 | `app/models/enumeration/ResourceType.java:37` | `ISSUE_MILESTONE("issue_milestone"),` |
| GL-models_enumeration_ResourceType-007 | `app/models/enumeration/ResourceType.java:40` | `ISSUE_LABEL("issue_label"),` |
| GL-models_enumeration_ResourceType-008 | `app/models/enumeration/ResourceType.java:42` | `BOARD_POST("board_post"),` |
| GL-models_enumeration_ResourceType-009 | `app/models/enumeration/ResourceType.java:44` | `BOARD_CATEGORY("board_category"),` |
| GL-models_enumeration_ResourceType-010 | `app/models/enumeration/ResourceType.java:46` | `BOARD_NOTICE("board_notice"),` |
| GL-models_enumeration_ResourceType-011 | `app/models/enumeration/ResourceType.java:48` | `CODE("code"),` |
| GL-models_enumeration_ResourceType-012 | `app/models/enumeration/ResourceType.java:50` | `MILESTONE("milestone"),` |
| GL-models_enumeration_ResourceType-013 | `app/models/enumeration/ResourceType.java:52` | `WIKI_PAGE("wiki_page"),` |
| GL-models_enumeration_ResourceType-014 | `app/models/enumeration/ResourceType.java:54` | `PROJECT_SETTING("project_setting"),` |
| GL-models_enumeration_ResourceType-015 | `app/models/enumeration/ResourceType.java:56` | `SITE_SETTING("site_setting"),` |
| GL-models_enumeration_ResourceType-016 | `app/models/enumeration/ResourceType.java:58` | `USER("user"),` |
| GL-models_enumeration_ResourceType-017 | `app/models/enumeration/ResourceType.java:60` | `USER_AVATAR("user_avatar"),` |
| GL-models_enumeration_ResourceType-018 | `app/models/enumeration/ResourceType.java:62` | `PROJECT("project"),` |
| GL-models_enumeration_ResourceType-019 | `app/models/enumeration/ResourceType.java:64` | `ATTACHMENT("attachment"),` |
| GL-models_enumeration_ResourceType-020 | `app/models/enumeration/ResourceType.java:66` | `ISSUE_COMMENT("issue_comment"),` |
| GL-models_enumeration_ResourceType-021 | `app/models/enumeration/ResourceType.java:68` | `NONISSUE_COMMENT("nonissue_comment"),` |
| GL-models_enumeration_ResourceType-022 | `app/models/enumeration/ResourceType.java:70` | `LABEL("label"),` |
| GL-models_enumeration_ResourceType-023 | `app/models/enumeration/ResourceType.java:72` | `PROJECT_LABELS("project_labels"),` |
| GL-models_enumeration_ResourceType-024 | `app/models/enumeration/ResourceType.java:74` | `FORK("fork"),` |
| GL-models_enumeration_ResourceType-025 | `app/models/enumeration/ResourceType.java:76` | `COMMIT_COMMENT("code_comment"),` |
| GL-models_enumeration_ResourceType-026 | `app/models/enumeration/ResourceType.java:78` | `PULL_REQUEST("pull_request"),` |
| GL-models_enumeration_ResourceType-027 | `app/models/enumeration/ResourceType.java:80` | `COMMIT("commit"),` |
| GL-models_enumeration_ResourceType-028 | `app/models/enumeration/ResourceType.java:82` | `COMMENT_THREAD("comment_thread"),` |
| GL-models_enumeration_ResourceType-029 | `app/models/enumeration/ResourceType.java:84` | `REVIEW_COMMENT("review_comment"),` |
| GL-models_enumeration_ResourceType-030 | `app/models/enumeration/ResourceType.java:86` | `ORGANIZATION("organization"),` |
| GL-models_enumeration_ResourceType-031 | `app/models/enumeration/ResourceType.java:88` | `PROJECT_TRANSFER("project_transfer"),` |
| GL-models_enumeration_ResourceType-032 | `app/models/enumeration/ResourceType.java:90` | `ISSUE_LABEL_CATEGORY("issue_label_category"),` |
| GL-models_enumeration_ResourceType-033 | `app/models/enumeration/ResourceType.java:92` | `WEBHOOK("webhook"),` |
| GL-models_enumeration_ResourceType-034 | `app/models/enumeration/ResourceType.java:94` | `NOT_A_RESOURCE("");` |
| GL-models_enumeration_ResourceType-036 | `app/models/enumeration/ResourceType.java:100` | `ResourceType(String resource) {` |
| GL-models_enumeration_ResourceType-037 | `app/models/enumeration/ResourceType.java:105` | `public String resource() {` |
| GL-models_enumeration_ResourceType-039 | `app/models/enumeration/ResourceType.java:120` | `public String asPathSegment() {` |
| GL-models_enumeration_RoleType-001 | `app/models/enumeration/RoleType.java:24` | `public enum RoleType {` |
| GL-models_enumeration_RoleType-002 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-003 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-004 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-005 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-006 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-007 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-008 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-010 | `app/models/enumeration/RoleType.java:38` | `RoleType(Long roleType) {` |
| GL-models_enumeration_RoleType-011 | `app/models/enumeration/RoleType.java:43` | `public Long roleType() {` |
| GL-models_enumeration_State-001 | `app/models/enumeration/State.java:25` | `public enum State {` |
| GL-models_enumeration_State-002 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-003 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-004 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-005 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-006 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-007 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-008 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-009 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-011 | `app/models/enumeration/State.java:39` | `State(String state) {` |
| GL-models_enumeration_State-012 | `app/models/enumeration/State.java:44` | `public String state() {` |
| GL-models_enumeration_EventType-001 | `app/models/enumeration/EventType.java:17` | `public enum EventType {` |
| GL-models_enumeration_EventType-002 | `app/models/enumeration/EventType.java:20` | `NEW_ISSUE("notification.type.new.issue", 1),` |
| GL-models_enumeration_EventType-003 | `app/models/enumeration/EventType.java:22` | `NEW_POSTING("notification.type.new.posting", 2),` |
| GL-models_enumeration_EventType-004 | `app/models/enumeration/EventType.java:24` | `NEW_PULL_REQUEST("notification.type.new.pullrequest", 3),` |
| GL-models_enumeration_EventType-005 | `app/models/enumeration/EventType.java:26` | `ISSUE_STATE_CHANGED("notification.type.issue.state.changed", 4),` |
| GL-models_enumeration_EventType-006 | `app/models/enumeration/EventType.java:28` | `ISSUE_ASSIGNEE_CHANGED("notification.type.issue.assignee.changed", 5),` |
| GL-models_enumeration_EventType-007 | `app/models/enumeration/EventType.java:30` | `PULL_REQUEST_STATE_CHANGED("notification.type.pullrequest.state.changed", 6),` |
| GL-models_enumeration_EventType-008 | `app/models/enumeration/EventType.java:32` | `NEW_COMMENT("notification.type.new.comment", 7),` |
| GL-models_enumeration_EventType-009 | `app/models/enumeration/EventType.java:34` | `NEW_REVIEW_COMMENT("notification.type.new.simple.comment", 8),` |
| GL-models_enumeration_EventType-010 | `app/models/enumeration/EventType.java:36` | `MEMBER_ENROLL_REQUEST("notification.type.member.enroll", 9),` |
| GL-models_enumeration_EventType-011 | `app/models/enumeration/EventType.java:38` | `PULL_REQUEST_MERGED("notification.type.pullrequest.merged", 10),` |
| GL-models_enumeration_EventType-012 | `app/models/enumeration/EventType.java:40` | `ISSUE_REFERRED_FROM_COMMIT("notification.type.issue.referred.from.commit", 11),` |
| GL-models_enumeration_EventType-013 | `app/models/enumeration/EventType.java:42` | `PULL_REQUEST_COMMIT_CHANGED("notification.type.pullrequest.commit.changed", 12),` |
| GL-models_enumeration_EventType-014 | `app/models/enumeration/EventType.java:44` | `NEW_COMMIT("notification.type.new.commit", 13),` |
| GL-models_enumeration_EventType-015 | `app/models/enumeration/EventType.java:46` | `PULL_REQUEST_REVIEW_STATE_CHANGED("notification.type.pullrequest.review.action.changed",14),` |
| GL-models_enumeration_EventType-016 | `app/models/enumeration/EventType.java:48` | `ISSUE_BODY_CHANGED("notification.type.issue.body.changed", 17),` |
| GL-models_enumeration_EventType-017 | `app/models/enumeration/EventType.java:50` | `ISSUE_REFERRED_FROM_PULL_REQUEST("notification.type.issue.referred.from.pullrequest", 16),` |
| GL-models_enumeration_EventType-018 | `app/models/enumeration/EventType.java:52` | `REVIEW_THREAD_STATE_CHANGED("notification.type.review.state.changed", 18),` |
| GL-models_enumeration_EventType-019 | `app/models/enumeration/EventType.java:54` | `ORGANIZATION_MEMBER_ENROLL_REQUEST("notification.organization.type.member.enroll",19),` |
| GL-models_enumeration_EventType-020 | `app/models/enumeration/EventType.java:56` | `COMMENT_UPDATED("notification.type.comment.updated", 20),` |
| GL-models_enumeration_EventType-021 | `app/models/enumeration/EventType.java:58` | `ISSUE_MOVED("notification.type.issue.is.moved", 21),` |
| GL-models_enumeration_EventType-022 | `app/models/enumeration/EventType.java:60` | `ISSUE_SHARER_CHANGED("notification.type.issue.sharer.changed", 22),` |
| GL-models_enumeration_EventType-023 | `app/models/enumeration/EventType.java:62` | `ISSUE_LABEL_CHANGED("notification.type.issue.label.changed", 23),` |
| GL-models_enumeration_EventType-024 | `app/models/enumeration/EventType.java:64` | `ISSUE_MILESTONE_CHANGED("notification.type.milestone.changed", 24),` |
| GL-models_enumeration_EventType-025 | `app/models/enumeration/EventType.java:66` | `POSTING_BODY_CHANGED("notification.type.posting.body.changed", 25),` |
| GL-models_enumeration_EventType-026 | `app/models/enumeration/EventType.java:68` | `RESOURCE_DELETED("notification.type.resource.deleted", 26),` |
| GL-models_enumeration_EventType-027 | `app/models/enumeration/EventType.java:70` | `MEMBER_ENROLL_ACCEPT("notification.member.enroll.accept", 27),` |
| GL-models_enumeration_EventType-028 | `app/models/enumeration/EventType.java:72` | `ORGANIZATION_MEMBER_ENROLL_ACCEPT("notification.member.enroll.accept", 28);` |
| GL-models_enumeration_EventType-032 | `app/models/enumeration/EventType.java:84` | `EventType(String messageKey, int order) {` |
| GL-models_enumeration_EventType-037 | `app/models/enumeration/EventType.java:128` | `@Override` |
| GL-models_enumeration_SearchType-001 | `app/models/enumeration/SearchType.java:4` | `/**` |
| GL-models_enumeration_SearchType-002 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-003 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-004 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-005 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-006 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-007 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-008 | `app/models/enumeration/SearchType.java:20` | `MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review` |
| GL-models_enumeration_SearchType-009 | `app/models/enumeration/SearchType.java:20` | `MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review` |
| GL-models_enumeration_SearchType-010 | `app/models/enumeration/SearchType.java:20` | `MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review` |
| GL-models_enumeration_SearchType-011 | `app/models/enumeration/SearchType.java:20` | `MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review` |
| GL-models_enumeration_SearchType-013 | `app/models/enumeration/SearchType.java:26` | `SearchType(String value) {` |
| GL-models_enumeration_Operation-001 | `app/models/enumeration/Operation.java:24` | `public enum Operation {` |
| GL-models_enumeration_Operation-002 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-003 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-004 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-005 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-006 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-007 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-008 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-009 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-010 | `app/models/enumeration/Operation.java:35` | `// this operation means an action which assign an issue to him or her self.` |
| GL-models_enumeration_Operation-012 | `app/models/enumeration/Operation.java:42` | `Operation(String operation) {` |
| GL-models_enumeration_Operation-013 | `app/models/enumeration/Operation.java:47` | `public String operation() {` |
| GL-models_enumeration_WebhookType-001 | `app/models/enumeration/WebhookType.java:10` | `public enum WebhookType {` |
| GL-models_enumeration_WebhookType-002 | `app/models/enumeration/WebhookType.java:15` | `SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2), JSON(3);` |
| GL-models_enumeration_WebhookType-003 | `app/models/enumeration/WebhookType.java:15` | `SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2), JSON(3);` |
| GL-models_enumeration_WebhookType-004 | `app/models/enumeration/WebhookType.java:15` | `SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2), JSON(3);` |
| GL-models_enumeration_WebhookType-005 | `app/models/enumeration/WebhookType.java:15` | `SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2), JSON(3);` |
| GL-models_enumeration_WebhookType-007 | `app/models/enumeration/WebhookType.java:21` | `WebhookType(int type) {` |
| GL-models_enumeration_RequestState-001 | `app/models/enumeration/RequestState.java:24` | `public enum RequestState {` |
| GL-models_enumeration_RequestState-002 | `app/models/enumeration/RequestState.java:29` | `REQUEST, CANCEL, ACCEPT, REJECT` |
| GL-models_enumeration_RequestState-003 | `app/models/enumeration/RequestState.java:29` | `REQUEST, CANCEL, ACCEPT, REJECT` |
| GL-models_enumeration_RequestState-004 | `app/models/enumeration/RequestState.java:29` | `REQUEST, CANCEL, ACCEPT, REJECT` |
| GL-models_enumeration_RequestState-005 | `app/models/enumeration/RequestState.java:29` | `REQUEST, CANCEL, ACCEPT, REJECT` |
| GL-models_enumeration_Matching-001 | `app/models/enumeration/Matching.java:24` | `public enum Matching {` |
| GL-models_enumeration_Matching-002 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-003 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-004 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-005 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-006 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-007 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-008 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-009 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_ProjectScope-001 | `app/models/enumeration/ProjectScope.java:24` | `public enum ProjectScope {` |
| GL-models_enumeration_PullRequestReviewAction-001 | `app/models/enumeration/PullRequestReviewAction.java:24` | `public enum PullRequestReviewAction {` |
| GL-models_support_ModelLock-001 | `app/models/support/ModelLock.java:29` | `public class ModelLock<T extends Model> {` |
| GL-models_support_ModelLock-002 | `app/models/support/ModelLock.java:31` | `private final Map<T, Object> locks = new MapMaker().weakValues().makeMap();` |
| GL-models_support_ModelLock-003 | `app/models/support/ModelLock.java:34` | `public Object get(T model) {` |
| GL-models_support_UserComparator-001 | `app/models/support/UserComparator.java:28` | `public class UserComparator implements Comparator<User> {` |
| GL-models_support_UserComparator-002 | `app/models/support/UserComparator.java:30` | `@Override` |
| GL-models_support_LdapUser-001 | `app/models/support/LdapUser.java:23` | `public class LdapUser {` |
| GL-models_support_LdapUser-007 | `app/models/support/LdapUser.java:36` | `public LdapUser(Attribute displayName, Attribute email, Attribute userLoginId,` |
| GL-models_support_LdapUser-016 | `app/models/support/LdapUser.java:117` | `@Override` |
| GL-models_support_ReviewSearchCondition-001 | `app/models/support/ReviewSearchCondition.java:36` | `/**` |
| GL-models_support_ReviewSearchCondition-005 | `app/models/support/ReviewSearchCondition.java:48` | `public ReviewSearchCondition() {` |
| GL-models_support_ReviewSearchCondition-006 | `app/models/support/ReviewSearchCondition.java:54` | `/**` |
| GL-models_support_ReviewSearchCondition-007 | `app/models/support/ReviewSearchCondition.java:94` | `public ReviewSearchCondition clone() {` |
| GL-models_support_FinderTemplate-001 | `app/models/support/FinderTemplate.java:31` | `public class FinderTemplate {` |
| GL-models_support_FinderTemplate-002 | `app/models/support/FinderTemplate.java:34` | `private static <K, T> ExpressionList<T> makeExpressionList(OrderParams mop,` |
| GL-models_support_FinderTemplate-003 | `app/models/support/FinderTemplate.java:97` | `public static <K, T> List<T> findBy(OrderParams mop,` |
| GL-models_support_OrderParam-001 | `app/models/support/OrderParam.java:26` | `public class OrderParam {` |
| GL-models_support_OrderParam-004 | `app/models/support/OrderParam.java:35` | `public OrderParam(String sort, Direction direction) {` |
| GL-models_support_SearchCondition-001 | `app/models/support/SearchCondition.java:25` | `public class SearchCondition extends AbstractPostingApp.SearchCondition implements Cloneable {` |
| GL-models_support_SearchCondition-005 | `app/models/support/SearchCondition.java:34` | `public Set<Long> labelIds = new HashSet<>();` |
| GL-models_support_SearchCondition-015 | `app/models/support/SearchCondition.java:59` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_support_SearchCondition-016 | `app/models/support/SearchCondition.java:63` | `private User byUser = UserApp.currentUser();` |
| GL-models_support_SearchCondition-017 | `app/models/support/SearchCondition.java:66` | `/**` |
| GL-models_support_SearchCondition-025 | `app/models/support/SearchCondition.java:134` | `public ExpressionList<Issue> asExpressionList(@Nonnull Organization organization) {` |
| GL-models_support_SearchCondition-034 | `app/models/support/SearchCondition.java:255` | `public SearchCondition() {` |
| GL-models_support_SearchCondition-035 | `app/models/support/SearchCondition.java:264` | `public ExpressionList<Issue> asExpressionList() {` |
| GL-models_support_SearchCondition-039 | `app/models/support/SearchCondition.java:323` | `private void updateElWhenIdsEmpty(ExpressionList<Issue> el, List<Long> ids) {` |
| GL-models_support_SearchCondition-044 | `app/models/support/SearchCondition.java:391` | `public ExpressionList<Issue> asExpressionList(Project project) {` |
| GL-models_support_SearchCondition-046 | `app/models/support/SearchCondition.java:484` | `private Set<Long> extractIssueIds(List<Issue> issues) {` |
| GL-models_support_SearchCondition-047 | `app/models/support/SearchCondition.java:493` | `private List<Issue> findIssueByLabel(List<Issue> issues, IssueLabel label) {` |
| GL-models_support_SearchCondition-049 | `app/models/support/SearchCondition.java:513` | `public boolean hasCondition(){` |
| GL-models_support_SearchCondition-050 | `app/models/support/SearchCondition.java:523` | `@Override` |
| GL-models_support_Options-001 | `app/models/support/Options.java:26` | `public class Options extends LinkedHashMap<String, String> {` |
| GL-models_support_Options-003 | `app/models/support/Options.java:31` | `public Options(String... args) {` |
| GL-models_support_SearchParams-001 | `app/models/support/SearchParams.java:29` | `public class SearchParams {` |
| GL-models_support_SearchParams-003 | `app/models/support/SearchParams.java:35` | `public SearchParams() {` |
| GL-models_support_SearchParams-004 | `app/models/support/SearchParams.java:40` | `public SearchParams add(String field, Object value, Matching matching) {` |
| GL-models_support_SearchParams-006 | `app/models/support/SearchParams.java:51` | `public List<SearchParam> clean() {` |
| GL-models_support_IssueLabelAggregate-001 | `app/models/support/IssueLabelAggregate.java:15` | `@Entity` |
| GL-models_support_OrderParams-001 | `app/models/support/OrderParams.java:30` | `public class OrderParams {` |
| GL-models_support_OrderParams-003 | `app/models/support/OrderParams.java:36` | `public OrderParams() {` |
| GL-models_support_OrderParams-004 | `app/models/support/OrderParams.java:41` | `public OrderParams add(String field, Direction direction) {` |
| GL-models_support_OrderParams-006 | `app/models/support/OrderParams.java:52` | `public List<OrderParam> clean() {` |
| GL-models_support_IssueSearchCondition-001 | `app/models/support/IssueSearchCondition.java:13` | `public class IssueSearchCondition  extends AbstractPostingApp.SearchCondition {` |
| GL-models_support_IssueSearchCondition-007 | `app/models/support/IssueSearchCondition.java:53` | `private ExpressionList<Issue> asExpressionList() {` |
| GL-models_support_IssueSearchCondition-008 | `app/models/support/IssueSearchCondition.java:66` | `private ExpressionList<Issue> asExpressionListForAll() {` |
| GL-models_support_IssueSearchCondition-013 | `app/models/support/IssueSearchCondition.java:113` | `private void updateElWhenIdsEmpty(ExpressionList<Issue> el, List<Long> ids) {` |
| GL-models_support_SearchParam-001 | `app/models/support/SearchParam.java:26` | `public class SearchParam {` |
| GL-models_support_SearchParam-005 | `app/models/support/SearchParam.java:38` | `public SearchParam(String field, Object value, Matching matching) {` |
| GL-playRepository_GitRepository-001 | `app/playRepository/GitRepository.java:84` | `public class GitRepository implements PlayRepository {` |
| GL-playRepository_GitRepository-002 | `app/playRepository/GitRepository.java:86` | `private static final ModelLock<Project> PROJECT_LOCK = new ModelLock<>();` |
| GL-playRepository_GitRepository-008 | `app/playRepository/GitRepository.java:100` | `/**` |
| GL-playRepository_GitRepository-009 | `app/playRepository/GitRepository.java:106` | `/**` |
| GL-playRepository_GitRepository-016 | `app/playRepository/GitRepository.java:134` | `/**` |
| GL-playRepository_GitRepository-017 | `app/playRepository/GitRepository.java:144` | `public GitRepository(String ownerName, String projectName) {` |
| GL-playRepository_GitRepository-018 | `app/playRepository/GitRepository.java:149` | `/**` |
| GL-playRepository_GitRepository-019 | `app/playRepository/GitRepository.java:157` | `public static Repository buildGitRepository(String ownerName, String projectName,` |
| GL-playRepository_GitRepository-020 | `app/playRepository/GitRepository.java:175` | `public static Repository buildGitRepository(String ownerName, String projectName) {` |
| GL-playRepository_GitRepository-021 | `app/playRepository/GitRepository.java:180` | `/**` |
| GL-playRepository_GitRepository-022 | `app/playRepository/GitRepository.java:188` | `public static Repository buildGitRepository(Project project, boolean alternatesMergeRepo) {` |
| GL-playRepository_GitRepository-023 | `app/playRepository/GitRepository.java:193` | `public static void cloneLocalRepository(Project originalProject, Project forkProject)` |
| GL-playRepository_GitRepository-024 | `app/playRepository/GitRepository.java:206` | `/**` |
| GL-playRepository_GitRepository-025 | `app/playRepository/GitRepository.java:215` | `/**` |
| GL-playRepository_GitRepository-026 | `app/playRepository/GitRepository.java:224` | `/**` |
| GL-playRepository_GitRepository-029 | `app/playRepository/GitRepository.java:303` | `@Override` |
| GL-playRepository_GitRepository-030 | `app/playRepository/GitRepository.java:344` | `/**` |
| GL-playRepository_GitRepository-032 | `app/playRepository/GitRepository.java:399` | `/**` |
| GL-playRepository_GitRepository-033 | `app/playRepository/GitRepository.java:424` | `public class ObjectFinder {` |
| GL-playRepository_GitRepository-034 | `app/playRepository/GitRepository.java:648` | `public static interface TreeWalkHandler {` |
| GL-playRepository_GitRepository-035 | `app/playRepository/GitRepository.java:653` | `/**` |
| GL-playRepository_GitRepository-036 | `app/playRepository/GitRepository.java:675` | `/**` |
| GL-playRepository_GitRepository-037 | `app/playRepository/GitRepository.java:691` | `/**` |
| GL-playRepository_GitRepository-038 | `app/playRepository/GitRepository.java:720` | `@Override` |
| GL-playRepository_GitRepository-039 | `app/playRepository/GitRepository.java:733` | `/*` |
| GL-playRepository_GitRepository-040 | `app/playRepository/GitRepository.java:751` | `private void addTree(TreeWalk treeWalk, RevCommit commit) throws IOException {` |
| GL-playRepository_GitRepository-041 | `app/playRepository/GitRepository.java:760` | `/**` |
| GL-playRepository_GitRepository-047 | `app/playRepository/GitRepository.java:826` | `/**` |
| GL-playRepository_GitRepository-048 | `app/playRepository/GitRepository.java:869` | `@Override` |
| GL-playRepository_GitRepository-049 | `app/playRepository/GitRepository.java:881` | `/**` |
| GL-playRepository_GitRepository-050 | `app/playRepository/GitRepository.java:895` | `/**` |
| GL-playRepository_GitRepository-053 | `app/playRepository/GitRepository.java:948` | `@Override` |
| GL-playRepository_GitRepository-054 | `app/playRepository/GitRepository.java:970` | `@Override` |
| GL-playRepository_GitRepository-055 | `app/playRepository/GitRepository.java:976` | `/**` |
| GL-playRepository_GitRepository-056 | `app/playRepository/GitRepository.java:991` | `/**` |
| GL-playRepository_GitRepository-057 | `app/playRepository/GitRepository.java:1006` | `/**` |
| GL-playRepository_GitRepository-060 | `app/playRepository/GitRepository.java:1031` | `/**` |
| GL-playRepository_GitRepository-061 | `app/playRepository/GitRepository.java:1053` | `public static void cloneRepository(String gitUrl, Project forkingProject, String authId, String auth` |
| GL-playRepository_GitRepository-062 | `app/playRepository/GitRepository.java:1064` | `/**` |
| GL-playRepository_GitRepository-063 | `app/playRepository/GitRepository.java:1079` | `/**` |
| GL-playRepository_GitRepository-065 | `app/playRepository/GitRepository.java:1098` | `@SuppressWarnings("unchecked")` |
| GL-playRepository_GitRepository-066 | `app/playRepository/GitRepository.java:1105` | `public static List<GitCommit> diffCommits(Repository repository, ObjectId from, ObjectId to) throws ` |
| GL-playRepository_GitRepository-067 | `app/playRepository/GitRepository.java:1110` | `public static List<GitCommit> wrapInGitCommits(List<RevCommit> revCommits) throws IOException, GitAP` |
| GL-playRepository_GitRepository-068 | `app/playRepository/GitRepository.java:1119` | `/**` |
| GL-playRepository_GitRepository-069 | `app/playRepository/GitRepository.java:1171` | `/**` |
| GL-playRepository_GitRepository-070 | `app/playRepository/GitRepository.java:1193` | `/**` |
| GL-playRepository_GitRepository-071 | `app/playRepository/GitRepository.java:1205` | `/**` |
| GL-playRepository_GitRepository-072 | `app/playRepository/GitRepository.java:1233` | `/**` |
| GL-playRepository_GitRepository-073 | `app/playRepository/GitRepository.java:1254` | `/**` |
| GL-playRepository_GitRepository-074 | `app/playRepository/GitRepository.java:1284` | `/**` |
| GL-playRepository_GitRepository-075 | `app/playRepository/GitRepository.java:1298` | `/**` |
| GL-playRepository_GitRepository-076 | `app/playRepository/GitRepository.java:1313` | `public static Repository buildMergingRepository(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-077 | `app/playRepository/GitRepository.java:1318` | `public static Repository buildMergingRepository(Project project) {` |
| GL-playRepository_GitRepository-078 | `app/playRepository/GitRepository.java:1334` | `private static Git cloneRepository(Project project, File workingDirectory) throws GitAPIException, I` |
| GL-playRepository_GitRepository-079 | `app/playRepository/GitRepository.java:1342` | `public static boolean canDeleteFromBranch(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-080 | `app/playRepository/GitRepository.java:1379` | `public static String deleteFromBranch(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-081 | `app/playRepository/GitRepository.java:1408` | `public static void restoreBranch(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-082 | `app/playRepository/GitRepository.java:1430` | `public static boolean canRestoreBranch(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-083 | `app/playRepository/GitRepository.java:1450` | `public static List<GitCommit> diffCommits(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-087 | `app/playRepository/GitRepository.java:1542` | `@Override` |
| GL-playRepository_GitRepository-090 | `app/playRepository/GitRepository.java:1820` | `/**` |
| GL-playRepository_GitRepository-092 | `app/playRepository/GitRepository.java:1889` | `public static class CloneAndFetch {` |
| GL-playRepository_GitRepository-093 | `app/playRepository/GitRepository.java:1924` | `public static interface AfterCloneAndFetchOperation {` |
| GL-playRepository_GitRepository-094 | `app/playRepository/GitRepository.java:1929` | `public void close() {` |
| GL-playRepository_GitRepository-095 | `app/playRepository/GitRepository.java:1934` | `/**` |
| GL-playRepository_GitRepository-096 | `app/playRepository/GitRepository.java:1943` | `@Override` |
| GL-playRepository_GitRepository-097 | `app/playRepository/GitRepository.java:1949` | `@Override` |
| GL-playRepository_GitRepository-098 | `app/playRepository/GitRepository.java:1963` | `@Override` |
| GL-playRepository_GitRepository-099 | `app/playRepository/GitRepository.java:1981` | `@Override` |
| GL-playRepository_GitRepository-100 | `app/playRepository/GitRepository.java:1987` | `/*` |
| GL-playRepository_GitRepository-101 | `app/playRepository/GitRepository.java:1999` | `/*` |
| GL-playRepository_GitRepository-102 | `app/playRepository/GitRepository.java:2008` | `/*` |
| GL-playRepository_GitRepository-103 | `app/playRepository/GitRepository.java:2020` | `public boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String d` |
| GL-playRepository_GitRepository-104 | `app/playRepository/GitRepository.java:2056` | `@Override` |
| GL-playRepository_RepositoryService-001 | `app/playRepository/RepositoryService.java:48` | `public class RepositoryService {` |
| GL-playRepository_RepositoryService-004 | `app/playRepository/RepositoryService.java:55` | `public static Map<String, String> vcsTypes() {` |
| GL-playRepository_RepositoryService-005 | `app/playRepository/RepositoryService.java:63` | `/**` |
| GL-playRepository_RepositoryService-006 | `app/playRepository/RepositoryService.java:73` | `/**` |
| GL-playRepository_RepositoryService-008 | `app/playRepository/RepositoryService.java:117` | `/**` |
| GL-playRepository_RepositoryService-012 | `app/playRepository/RepositoryService.java:157` | `public static DAVServlet createDavServlet(final String userName) throws ServletException {` |
| GL-playRepository_RepositoryService-013 | `app/playRepository/RepositoryService.java:192` | `/**` |
| GL-playRepository_RepositoryService-014 | `app/playRepository/RepositoryService.java:222` | `/**` |
| GL-playRepository_RepositoryService-015 | `app/playRepository/RepositoryService.java:276` | `private static PreReceiveHook createPreReceiveHook() {` |
| GL-playRepository_RepositoryService-016 | `app/playRepository/RepositoryService.java:283` | `private static PostReceiveHook createPostReceiveHook(` |
| GL-playRepository_RepositoryService-017 | `app/playRepository/RepositoryService.java:295` | `private static void receivePack(final InputStream input, Repository repository,` |
| GL-playRepository_RepositoryService-018 | `app/playRepository/RepositoryService.java:318` | `private static void uploadPack(final InputStream input, Repository repository,` |
| GL-playRepository_RepositoryService-019 | `app/playRepository/RepositoryService.java:337` | `private static void closeStreams(String serviceName, InputStream input, OutputStream output) {` |
| GL-playRepository_DiffLineType-001 | `app/playRepository/DiffLineType.java:24` | `public enum DiffLineType {` |
| GL-playRepository_DiffLineType-002 | `app/playRepository/DiffLineType.java:28` | `CONTEXT, ADD, REMOVE` |
| GL-playRepository_DiffLineType-003 | `app/playRepository/DiffLineType.java:28` | `CONTEXT, ADD, REMOVE` |
| GL-playRepository_DiffLineType-004 | `app/playRepository/DiffLineType.java:28` | `CONTEXT, ADD, REMOVE` |
| GL-playRepository_FileDiff-001 | `app/playRepository/FileDiff.java:34` | `/**` |
| GL-playRepository_FileDiff-004 | `app/playRepository/FileDiff.java:44` | `private Set<Error> errors = new HashSet<>();` |
| GL-playRepository_FileDiff-009 | `app/playRepository/FileDiff.java:69` | `public enum Error {A_SIZE_EXCEEDED, B_SIZE_EXCEEDED, DIFF_SIZE_EXCEEDED, OTHERS_SIZE_EXCEEDED }` |
| GL-playRepository_FileDiff-026 | `app/playRepository/FileDiff.java:104` | `public static class Hunks extends ArrayList<Hunk> {` |
| GL-playRepository_FileDiff-027 | `app/playRepository/FileDiff.java:111` | `public static class SizeExceededHunks extends Hunks {` |
| GL-playRepository_FileDiff-030 | `app/playRepository/FileDiff.java:130` | `/**` |
| GL-playRepository_FileDiff-031 | `app/playRepository/FileDiff.java:228` | `private int findCombinedEnd(final List<Edit> edits, final int i) {` |
| GL-playRepository_FileDiff-032 | `app/playRepository/FileDiff.java:237` | `private boolean combineA(final List<Edit> e, final int i) {` |
| GL-playRepository_FileDiff-033 | `app/playRepository/FileDiff.java:242` | `private boolean combineB(final List<Edit> e, final int i) {` |
| GL-playRepository_FileDiff-034 | `app/playRepository/FileDiff.java:247` | `private static boolean end(final Edit edit, final int a, final int b) {` |
| GL-playRepository_FileDiff-035 | `app/playRepository/FileDiff.java:252` | `private boolean checkEndOfLineMissing(final RawText text, final int line) {` |
| GL-playRepository_FileDiff-036 | `app/playRepository/FileDiff.java:257` | `/**` |
| GL-playRepository_FileDiff-038 | `app/playRepository/FileDiff.java:302` | `public void addError(Error error) {` |
| GL-playRepository_FileDiff-039 | `app/playRepository/FileDiff.java:307` | `public boolean hasAnyError(Error ... errors) {` |
| GL-playRepository_FileDiff-040 | `app/playRepository/FileDiff.java:320` | `private void refreshErrors() {` |
| GL-playRepository_FileDiff-041 | `app/playRepository/FileDiff.java:336` | `public boolean hasError(Error error) {` |
| GL-playRepository_FileDiff-042 | `app/playRepository/FileDiff.java:342` | `public boolean hasError() {` |
| GL-playRepository_FileDiff-043 | `app/playRepository/FileDiff.java:348` | `@Override` |
| GL-playRepository_FileDiff-044 | `app/playRepository/FileDiff.java:370` | `@Override` |
| GL-playRepository_FileDiff-045 | `app/playRepository/FileDiff.java:382` | `@Override` |
| GL-playRepository_GitRef-001 | `app/playRepository/GitRef.java:24` | `public class GitRef extends VCSRef {` |
| GL-playRepository_GitRef-002 | `app/playRepository/GitRef.java:26` | `public GitRef(String name) {` |
| GL-playRepository_GitRef-003 | `app/playRepository/GitRef.java:31` | `@Override` |
| GL-playRepository_SVNRepository-001 | `app/playRepository/SVNRepository.java:53` | `public class SVNRepository implements PlayRepository {` |
| GL-playRepository_SVNRepository-007 | `app/playRepository/SVNRepository.java:75` | `public SVNRepository(final String userName, String projectName) {` |
| GL-playRepository_SVNRepository-008 | `app/playRepository/SVNRepository.java:81` | `@Override` |
| GL-playRepository_SVNRepository-010 | `app/playRepository/SVNRepository.java:101` | `@Override` |
| GL-playRepository_SVNRepository-013 | `app/playRepository/SVNRepository.java:163` | `@Override` |
| GL-playRepository_SVNRepository-014 | `app/playRepository/SVNRepository.java:177` | `private ObjectNode fileAsJson(String path, org.tmatesoft.svn.core.io.SVNRepository repository) throw` |
| GL-playRepository_SVNRepository-015 | `app/playRepository/SVNRepository.java:225` | `@Override` |
| GL-playRepository_SVNRepository-016 | `app/playRepository/SVNRepository.java:231` | `@Override` |
| GL-playRepository_SVNRepository-017 | `app/playRepository/SVNRepository.java:237` | `@Override` |
| GL-playRepository_SVNRepository-018 | `app/playRepository/SVNRepository.java:244` | `@Override` |
| GL-playRepository_SVNRepository-020 | `app/playRepository/SVNRepository.java:268` | `@Override` |
| GL-playRepository_SVNRepository-021 | `app/playRepository/SVNRepository.java:274` | `@Override` |
| GL-playRepository_SVNRepository-022 | `app/playRepository/SVNRepository.java:280` | `@Override` |
| GL-playRepository_SVNRepository-023 | `app/playRepository/SVNRepository.java:315` | `@Override` |
| GL-playRepository_SVNRepository-024 | `app/playRepository/SVNRepository.java:330` | `@Override` |
| GL-playRepository_SVNRepository-025 | `app/playRepository/SVNRepository.java:338` | `@Override` |
| GL-playRepository_SVNRepository-028 | `app/playRepository/SVNRepository.java:372` | `@Override` |
| GL-playRepository_SVNRepository-029 | `app/playRepository/SVNRepository.java:378` | `@Override` |
| GL-playRepository_SVNRepository-030 | `app/playRepository/SVNRepository.java:385` | `@Override` |
| GL-playRepository_SVNRepository-031 | `app/playRepository/SVNRepository.java:391` | `@Override` |
| GL-playRepository_SVNRepository-032 | `app/playRepository/SVNRepository.java:397` | `@Override` |
| GL-playRepository_SVNRepository-033 | `app/playRepository/SVNRepository.java:402` | `@Override` |
| GL-playRepository_SVNRepository-034 | `app/playRepository/SVNRepository.java:413` | `@Override` |
| GL-playRepository_SVNRepository-035 | `app/playRepository/SVNRepository.java:431` | `public boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String d` |
| GL-playRepository_SVNRepository-036 | `app/playRepository/SVNRepository.java:448` | `@Override` |
| GL-playRepository_SVNRepository-037 | `app/playRepository/SVNRepository.java:454` | `@Override` |
| GL-playRepository_GitCommit-001 | `app/playRepository/GitCommit.java:35` | `public class GitCommit extends Commit {` |
| GL-playRepository_GitCommit-007 | `app/playRepository/GitCommit.java:52` | `public GitCommit(RevCommit revCommit) {` |
| GL-playRepository_GitCommit-008 | `app/playRepository/GitCommit.java:57` | `@Override` |
| GL-playRepository_GitCommit-009 | `app/playRepository/GitCommit.java:63` | `// Imported from getFullMessage of` |
| GL-playRepository_GitCommit-010 | `app/playRepository/GitCommit.java:91` | `@Override` |
| GL-playRepository_GitCommit-011 | `app/playRepository/GitCommit.java:97` | `@Override` |
| GL-playRepository_GitCommit-012 | `app/playRepository/GitCommit.java:103` | `// Imported from` |
| GL-playRepository_GitCommit-013 | `app/playRepository/GitCommit.java:141` | `@Override` |
| GL-playRepository_GitCommit-014 | `app/playRepository/GitCommit.java:147` | `@Override` |
| GL-playRepository_GitCommit-015 | `app/playRepository/GitCommit.java:153` | `@Override` |
| GL-playRepository_GitCommit-016 | `app/playRepository/GitCommit.java:159` | `@Override` |
| GL-playRepository_GitCommit-017 | `app/playRepository/GitCommit.java:165` | `@Override` |
| GL-playRepository_GitCommit-018 | `app/playRepository/GitCommit.java:171` | `@Override` |
| GL-playRepository_GitCommit-020 | `app/playRepository/GitCommit.java:182` | `@Override` |
| GL-playRepository_GitCommit-021 | `app/playRepository/GitCommit.java:188` | `@Override` |
| GL-playRepository_GitCommit-022 | `app/playRepository/GitCommit.java:194` | `@Override` |
| GL-playRepository_GitCommit-024 | `app/playRepository/GitCommit.java:205` | `// Imported from` |
| GL-playRepository_GitCommit-025 | `app/playRepository/GitCommit.java:239` | `// Imported from` |
| GL-playRepository_GitCommit-026 | `app/playRepository/GitCommit.java:273` | `public static Charset parseEncoding(final byte[] b, Charset fallback) {` |
| GL-playRepository_GitCommit-027 | `app/playRepository/GitCommit.java:282` | `// Imported from` |
| GL-playRepository_GitCommit-028 | `app/playRepository/GitCommit.java:344` | `// Imported from` |
| GL-playRepository_GitCommit-029 | `app/playRepository/GitCommit.java:354` | `// Imported from` |
| GL-playRepository_Commit-001 | `app/playRepository/Commit.java:30` | `public abstract class Commit {` |
| GL-playRepository_Commit-021 | `app/playRepository/Commit.java:136` | `public Resource asResource(final Project project) {` |
| GL-playRepository_GitBranch-001 | `app/playRepository/GitBranch.java:29` | `/**` |
| GL-playRepository_GitBranch-007 | `app/playRepository/GitBranch.java:50` | `public GitBranch(String name, GitCommit headCommit) {` |
| GL-playRepository_DiffLine-001 | `app/playRepository/DiffLine.java:24` | `public class DiffLine {` |
| GL-playRepository_DiffLine-007 | `app/playRepository/DiffLine.java:37` | `public DiffLine(FileDiff file, DiffLineType type, Integer lineNumA, Integer lineNumB,` |
| GL-playRepository_DiffLine-008 | `app/playRepository/DiffLine.java:47` | `@Override` |
| GL-playRepository_DiffLine-009 | `app/playRepository/DiffLine.java:65` | `@Override` |
| GL-playRepository_BareRepository-001 | `app/playRepository/BareRepository.java:42` | `public class BareRepository {` |
| GL-playRepository_BareRepository-002 | `app/playRepository/BareRepository.java:44` | `/**` |
| GL-playRepository_BareRepository-007 | `app/playRepository/BareRepository.java:121` | `private static TreeFilter[] READMEFileNameFilter() {` |
| GL-playRepository_BareRepository-008 | `app/playRepository/BareRepository.java:131` | `public static EndingType findFileLineEnding(Repository repository, String fileNameWithPath) throws I` |
| GL-playRepository_VCSRef-001 | `app/playRepository/VCSRef.java:24` | `public class VCSRef {` |
| GL-playRepository_VCSRef-003 | `app/playRepository/VCSRef.java:29` | `public VCSRef(String name) {` |
| GL-playRepository_VCSRef-004 | `app/playRepository/VCSRef.java:34` | `public String name() {` |
| GL-playRepository_VCSRef-005 | `app/playRepository/VCSRef.java:39` | `public String canonicalName() {` |
| GL-playRepository_VCSRef-006 | `app/playRepository/VCSRef.java:44` | `@Override` |
| GL-playRepository_SvnCommit-001 | `app/playRepository/SvnCommit.java:31` | `public class SvnCommit extends Commit {` |
| GL-playRepository_SvnCommit-003 | `app/playRepository/SvnCommit.java:36` | `public SvnCommit(SVNLogEntry entry) {` |
| GL-playRepository_SvnCommit-005 | `app/playRepository/SvnCommit.java:46` | `@Override` |
| GL-playRepository_SvnCommit-006 | `app/playRepository/SvnCommit.java:52` | `@Override` |
| GL-playRepository_SvnCommit-007 | `app/playRepository/SvnCommit.java:58` | `@Override` |
| GL-playRepository_SvnCommit-008 | `app/playRepository/SvnCommit.java:64` | `@Override` |
| GL-playRepository_SvnCommit-009 | `app/playRepository/SvnCommit.java:70` | `@Override` |
| GL-playRepository_SvnCommit-010 | `app/playRepository/SvnCommit.java:82` | `@Override` |
| GL-playRepository_SvnCommit-011 | `app/playRepository/SvnCommit.java:88` | `@Override` |
| GL-playRepository_SvnCommit-012 | `app/playRepository/SvnCommit.java:94` | `@Override` |
| GL-playRepository_SvnCommit-013 | `app/playRepository/SvnCommit.java:100` | `@Override` |
| GL-playRepository_SvnCommit-014 | `app/playRepository/SvnCommit.java:106` | `@Override` |
| GL-playRepository_SvnCommit-015 | `app/playRepository/SvnCommit.java:112` | `@Override` |
| GL-playRepository_SvnCommit-016 | `app/playRepository/SvnCommit.java:118` | `@Override` |
| GL-playRepository_SvnCommit-017 | `app/playRepository/SvnCommit.java:124` | `@Override` |
| GL-playRepository_SvnCommit-018 | `app/playRepository/SvnCommit.java:134` | `@Override` |
| GL-playRepository_Hunk-001 | `app/playRepository/Hunk.java:29` | `public class Hunk {` |
| GL-playRepository_Hunk-006 | `app/playRepository/Hunk.java:39` | `public List<DiffLine> lines = new ArrayList<>();` |
| GL-playRepository_Hunk-007 | `app/playRepository/Hunk.java:42` | `public int size() {` |
| GL-playRepository_Hunk-008 | `app/playRepository/Hunk.java:51` | `@Override` |
| GL-playRepository_Hunk-009 | `app/playRepository/Hunk.java:68` | `@Override` |
| GL-playRepository_BareCommit-001 | `app/playRepository/BareCommit.java:54` | `public class BareCommit {` |
| GL-playRepository_BareCommit-009 | `app/playRepository/BareCommit.java:72` | `/**` |
| GL-playRepository_BareCommit-010 | `app/playRepository/BareCommit.java:86` | `/**` |
| GL-playRepository_BareCommit-011 | `app/playRepository/BareCommit.java:119` | `private boolean noHeadRef() {` |
| GL-playRepository_BareCommit-012 | `app/playRepository/BareCommit.java:127` | `private ObjectId createCommitWithNewTree(ObjectId targetTextFileObjectId) throws IOException {` |
| GL-playRepository_BareCommit-013 | `app/playRepository/BareCommit.java:132` | `private CommitBuilder buildCommitWith(String fileName, ObjectId fileObjectId) throws IOException {` |
| GL-playRepository_BareCommit-014 | `app/playRepository/BareCommit.java:145` | `private ObjectId createTreeWith(String fileName, ObjectId fileObjectId) throws IOException {` |
| GL-playRepository_BareCommit-015 | `app/playRepository/BareCommit.java:154` | `private TreeFormatter newTreeWith(String fileName, ObjectId fileObjectId) {` |
| GL-playRepository_BareCommit-016 | `app/playRepository/BareCommit.java:161` | `private TreeFormatter rebuildExistingTreeWith(String fileName, ObjectId fileObjectId) throws IOExcep` |
| GL-playRepository_BareCommit-018 | `app/playRepository/BareCommit.java:204` | `private ObjectId createGitObjectWithText(String contents) throws IOException {` |
| GL-playRepository_BareCommit-019 | `app/playRepository/BareCommit.java:210` | `private RefUpdate.Result refUpdate(ObjectId commitId, String refName) throws IOException {` |
| GL-playRepository_BareCommit-020 | `app/playRepository/BareCommit.java:226` | `private boolean hasOldCommit(String refName) throws IOException {` |
| GL-playRepository_BareCommit-030 | `app/playRepository/BareCommit.java:347` | `private static DirCache createTemporaryIndex(final Git git, final ObjectId headId, final String path` |
| GL-playRepository_PlayRepository-001 | `app/playRepository/PlayRepository.java:34` | `public interface PlayRepository {` |
| GL-playRepository_PlayRepository-003 | `app/playRepository/PlayRepository.java:41` | `public abstract void create() throws IOException, SVNException;` |
| GL-playRepository_PlayRepository-007 | `app/playRepository/PlayRepository.java:53` | `public abstract void delete() throws Exception;` |
| GL-playRepository_PlayRepository-016 | `app/playRepository/PlayRepository.java:80` | `public abstract Resource asResource();` |
| GL-playRepository_PlayRepository-019 | `app/playRepository/PlayRepository.java:89` | `public abstract boolean renameTo(String projectName);` |
| GL-playRepository_PlayRepository-024 | `app/playRepository/PlayRepository.java:104` | `boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String destProj` |
| GL-playRepository_hooks_NotifyPushedCommits-001 | `app/playRepository/hooks/NotifyPushedCommits.java:39` | `public class NotifyPushedCommits implements PostReceiveHook {` |
| GL-playRepository_hooks_NotifyPushedCommits-004 | `app/playRepository/hooks/NotifyPushedCommits.java:46` | `public NotifyPushedCommits(Project project, User user) {` |
| GL-playRepository_hooks_NotifyPushedCommits-005 | `app/playRepository/hooks/NotifyPushedCommits.java:52` | `@Override` |
| GL-playRepository_hooks_UpdateLastPushedDate-001 | `app/playRepository/hooks/UpdateLastPushedDate.java:33` | `public class UpdateLastPushedDate implements PostReceiveHook {` |
| GL-playRepository_hooks_UpdateLastPushedDate-003 | `app/playRepository/hooks/UpdateLastPushedDate.java:38` | `public UpdateLastPushedDate(Project project) {` |
| GL-playRepository_hooks_UpdateLastPushedDate-004 | `app/playRepository/hooks/UpdateLastPushedDate.java:43` | `@Override` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-001 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:38` | `public class UpdateRecentlyPushedBranch implements PostReceiveHook {` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-003 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:43` | `public UpdateRecentlyPushedBranch(Project project) {` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-004 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:48` | `@Override` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-005 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:56` | `private void removeOldPushedBranches() {` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-006 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:64` | `private void saveRecentlyPushedBranch(Set<String> pushedBranches) {` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-009 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:92` | `private void deletePushedBranch(Set<String> deletedBranches) {` |
| GL-playRepository_hooks_IssueReferredFromCommitEvent-001 | `app/playRepository/hooks/IssueReferredFromCommitEvent.java:39` | `public class IssueReferredFromCommitEvent implements PostReceiveHook {` |
| GL-playRepository_hooks_IssueReferredFromCommitEvent-004 | `app/playRepository/hooks/IssueReferredFromCommitEvent.java:46` | `public IssueReferredFromCommitEvent(Project project, User user) {` |
| GL-playRepository_hooks_IssueReferredFromCommitEvent-005 | `app/playRepository/hooks/IssueReferredFromCommitEvent.java:52` | `@Override` |
| GL-playRepository_hooks_ReceiveCommandUtil-001 | `app/playRepository/hooks/ReceiveCommandUtil.java:32` | `public class ReceiveCommandUtil {` |
| GL-playRepository_hooks_PullRequestCheck-001 | `app/playRepository/hooks/PullRequestCheck.java:42` | `public class PullRequestCheck implements PostReceiveHook {` |
| GL-playRepository_hooks_PullRequestCheck-005 | `app/playRepository/hooks/PullRequestCheck.java:51` | `public PullRequestCheck(User user, Request request, Project project) {` |
| GL-playRepository_hooks_PullRequestCheck-006 | `app/playRepository/hooks/PullRequestCheck.java:58` | `@Override` |
| GL-playRepository_hooks_RejectPushToReservedRefs-001 | `app/playRepository/hooks/RejectPushToReservedRefs.java:10` | `public class RejectPushToReservedRefs implements PreReceiveHook {` |
| GL-playRepository_hooks_RejectPushToReservedRefs-002 | `app/playRepository/hooks/RejectPushToReservedRefs.java:12` | `public RejectPushToReservedRefs() {` |
| GL-playRepository_hooks_RejectPushToReservedRefs-003 | `app/playRepository/hooks/RejectPushToReservedRefs.java:16` | `@Override` |
| GL-view_partial_filediff-001 | `app/views/partial_filediff.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_layout_framed-001 | `app/views/layout_framed.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_projectLayout-001 | `app/views/projectLayout.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_partial_diff_comment_on_line-001 | `app/views/partial_diff_comment_on_line.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_partial_diff-001 | `app/views/partial_diff.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_projectMenu-001 | `app/views/projectMenu.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organizationLayout-001 | `app/views/organizationLayout.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_restricted-001 | `app/views/restricted.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_partial_diff_line-001 | `app/views/partial_diff_line.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_partial_update_notification-001 | `app/views/partial_update_notification.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_siteLayout-001 | `app/views/siteLayout.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_partial_comment_thread-001 | `app/views/partial_comment_thread.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_siteLayout_framed-001 | `app/views/siteLayout_framed.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_sidebar-001 | `app/views/sidebar.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_partial_comment_form_on_thread-001 | `app/views/partial_comment_form_on_thread.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_layout-001 | `app/views/layout.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_index-001 | `app/views/index/index.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_allOrganizationList-001 | `app/views/index/allOrganizationList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_allProjectList-001 | `app/views/index/allProjectList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_myOrganizationList_partial-001 | `app/views/index/myOrganizationList_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_myRecentIssueList-001 | `app/views/index/myRecentIssueList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_allOrganizationList_partial-001 | `app/views/index/allOrganizationList_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_allProjectList_partial-001 | `app/views/index/allProjectList_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_partial_intro-001 | `app/views/index/partial_intro.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_myProjectList_partial-001 | `app/views/index/myProjectList_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_myOrganizationList-001 | `app/views/index/myOrganizationList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_displayProjects-001 | `app/views/index/displayProjects.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_partial_notifications-001 | `app/views/index/partial_notifications.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_sidebar-001 | `app/views/index/sidebar.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_notifications-001 | `app/views/index/notifications.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_myProjectList-001 | `app/views/index/myProjectList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_index_myRecentIssueList_partial-001 | `app/views/index/myRecentIssueList_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_reviewthread_partial_list-001 | `app/views/reviewthread/partial_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_reviewthread_list-001 | `app/views/reviewthread/list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_state-001 | `app/views/git/partial_state.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_branch-001 | `app/views/git/partial_branch.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_edit-001 | `app/views/git/edit.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_view-001 | `app/views/git/view.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_fork-001 | `app/views/git/fork.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_list-001 | `app/views/git/partial_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_clone-001 | `app/views/git/clone.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_viewChanges-001 | `app/views/git/viewChanges.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_merge_result-001 | `app/views/git/partial_merge_result.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_forklist-001 | `app/views/git/partial_forklist.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_info-001 | `app/views/git/partial_info.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_reviewlist-001 | `app/views/git/partial_reviewlist.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_search-001 | `app/views/git/partial_search.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_recently_pushed_branches-001 | `app/views/git/partial_recently_pushed_branches.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_list-001 | `app/views/git/list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_create-001 | `app/views/git/create.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_git_partial_pull_request_event-001 | `app/views/git/partial_pull_request_event.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_requestTextEntityTooLarge-001 | `app/views/error/requestTextEntityTooLarge.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_badrequest_default-001 | `app/views/error/badrequest_default.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_badrequest-001 | `app/views/error/badrequest.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_forbidden_organization-001 | `app/views/error/forbidden_organization.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_forbidden-001 | `app/views/error/forbidden.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_internalServerError_default-001 | `app/views/error/internalServerError_default.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_notfound_default-001 | `app/views/error/notfound_default.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_forbidden_default-001 | `app/views/error/forbidden_default.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_error_notfound-001 | `app/views/error/notfound.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_dashboard-001 | `app/views/project/partial_dashboard.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_dashboard_issuesbymilestone-001 | `app/views/project/partial_dashboard_issuesbymilestone.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_webhooks_list-001 | `app/views/project/partial_webhooks_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_members-001 | `app/views/project/members.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_issuelabels-001 | `app/views/project/issuelabels.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_watchers-001 | `app/views/project/watchers.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_readme-001 | `app/views/project/partial_readme.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_dashboard_issuesbylabel-001 | `app/views/project/partial_dashboard_issuesbylabel.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_importing-001 | `app/views/project/importing.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_transfer-001 | `app/views/project/transfer.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_issuelabels_editlabel-001 | `app/views/project/partial_issuelabels_editlabel.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_statistics-001 | `app/views/project/statistics.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_issuelabels_editcategory-001 | `app/views/project/partial_issuelabels_editcategory.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_home-001 | `app/views/project/home.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_dashboard_issuesbyassignee-001 | `app/views/project/partial_dashboard_issuesbyassignee.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_setting-001 | `app/views/project/setting.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_webhooks-001 | `app/views/project/webhooks.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_dashboard_pullrequests-001 | `app/views/project/partial_dashboard_pullrequests.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_list-001 | `app/views/project/list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_settingmenu-001 | `app/views/project/partial_settingmenu.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_issuelabels_list-001 | `app/views/project/partial_issuelabels_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_partial_history-001 | `app/views/project/partial_history.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_create-001 | `app/views/project/create.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_change_vcs-001 | `app/views/project/change_vcs.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_delete-001 | `app/views/project/delete.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_project_header-001 | `app/views/project/header.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_milestones-001 | `app/views/search/partial_milestones.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_users-001 | `app/views/search/partial_users.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_posts-001 | `app/views/search/partial_posts.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_projects-001 | `app/views/search/partial_projects.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_issue_comments-001 | `app/views/search/partial_issue_comments.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_issues-001 | `app/views/search/partial_issues.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_result-001 | `app/views/search/result.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_search-001 | `app/views/search/partial_search.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_reviews-001 | `app/views/search/partial_reviews.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_search_partial_post_comments-001 | `app/views/search/partial_post_comments.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_show_selected_label-001 | `app/views/issue/partial_show_selected_label.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_edit-001 | `app/views/issue/edit.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_view-001 | `app/views/issue/view.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_comments-001 | `app/views/issue/partial_comments.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_list_subtask-001 | `app/views/issue/partial_list_subtask.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_voters-001 | `app/views/issue/partial_voters.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_searchform-001 | `app/views/issue/partial_searchform.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_massupdate-001 | `app/views/issue/partial_massupdate.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_list-001 | `app/views/issue/partial_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_select_label-001 | `app/views/issue/partial_select_label.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_my_partial_list_quicksearch-001 | `app/views/issue/my_partial_list_quicksearch.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_event_timeline-001 | `app/views/issue/partial_event_timeline.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_list_draft-001 | `app/views/issue/partial_list_draft.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_index_comment-001 | `app/views/issue/partial_index_comment.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_comment-001 | `app/views/issue/partial_comment.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_my_list-001 | `app/views/issue/my_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_list_quicksearch-001 | `app/views/issue/partial_list_quicksearch.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_index_event_timeline-001 | `app/views/issue/partial_index_event_timeline.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_view_child-001 | `app/views/issue/partial_view_child.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_my_partial_search-001 | `app/views/issue/my_partial_search.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_view_childIssueListOnly-001 | `app/views/issue/partial_view_childIssueListOnly.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_voter_list-001 | `app/views/issue/partial_voter_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_my_partial_list-001 | `app/views/issue/my_partial_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_assignee-001 | `app/views/issue/partial_assignee.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_select_subtask-001 | `app/views/issue/partial_select_subtask.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_list-001 | `app/views/issue/list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_view_childIssueList-001 | `app/views/issue/partial_view_childIssueList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_list_wrap-001 | `app/views/issue/partial_list_wrap.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_create-001 | `app/views/issue/create.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_issue_partial_index_comments-001 | `app/views/issue/partial_index_comments.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_data-001 | `app/views/site/data.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_projectList-001 | `app/views/site/projectList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_partial_paginationForUserList-001 | `app/views/site/partial_paginationForUserList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_postList-001 | `app/views/site/postList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_diagnostic-001 | `app/views/site/diagnostic.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_siteMngLayout-001 | `app/views/site/siteMngLayout.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_userList-001 | `app/views/site/userList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_mail-001 | `app/views/site/mail.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_massMail-001 | `app/views/site/massMail.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_update-001 | `app/views/site/update.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_setting-001 | `app/views/site/setting.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_issueList-001 | `app/views/site/issueList.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_lostPassword-001 | `app/views/site/lostPassword.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_site_partial_pagination-001 | `app/views/site/partial_pagination.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_history-001 | `app/views/code/history.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_view-001 | `app/views/code/view.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_compare_svn-001 | `app/views/code/compare_svn.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_partial_nonrange_codecomment_thread-001 | `app/views/code/partial_nonrange_codecomment_thread.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_branches-001 | `app/views/code/branches.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_diff-001 | `app/views/code/diff.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_nohead_svn-001 | `app/views/code/nohead_svn.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_partial_view_file-001 | `app/views/code/partial_view_file.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_svnDiff-001 | `app/views/code/svnDiff.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_partial_view_folder-001 | `app/views/code/partial_view_folder.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_compare-001 | `app/views/code/compare.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_nohead-001 | `app/views/code/nohead.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_code_partial_branchrow-001 | `app/views/code/partial_branchrow.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_group_pullrequest_list_partial-001 | `app/views/organization/group_pullrequest_list_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_view-001 | `app/views/organization/view.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_group_pullrequest_list-001 | `app/views/organization/group_pullrequest_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_members-001 | `app/views/organization/members.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_group_issue_list_partial-001 | `app/views/organization/group_issue_list_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_group_board_list-001 | `app/views/organization/group_board_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_group_issue_list-001 | `app/views/organization/group_issue_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_deleteForm-001 | `app/views/organization/deleteForm.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_menu-001 | `app/views/organization/menu.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_setting-001 | `app/views/organization/setting.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_group_board_list_partial-001 | `app/views/organization/group_board_list_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_list-001 | `app/views/organization/list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_partial_settingmenu-001 | `app/views/organization/partial_settingmenu.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_group_issue_list_quicksearch-001 | `app/views/organization/group_issue_list_quicksearch.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_create-001 | `app/views/organization/create.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_header-001 | `app/views/organization/header.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_organization_group_issue_search_partial-001 | `app/views/organization/group_issue_search_partial.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_help_keymap-001 | `app/views/help/keymap.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_help_experimental-001 | `app/views/help/experimental.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_help_UIKit-001 | `app/views/help/UIKit.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_help_toc-001 | `app/views/help/toc.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_help_markdown-001 | `app/views/help/markdown.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_board_edit-001 | `app/views/board/edit.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_board_view-001 | `app/views/board/view.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_board_partial_comments-001 | `app/views/board/partial_comments.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_board_partial_list-001 | `app/views/board/partial_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_board_list-001 | `app/views/board/list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_board_create-001 | `app/views/board/create.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_partial_milestones-001 | `app/views/user/partial_milestones.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_edit-001 | `app/views/user/edit.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_view-001 | `app/views/user/view.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_partial_edit_tabmenu-001 | `app/views/user/partial_edit_tabmenu.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_edit_notifications-001 | `app/views/user/edit_notifications.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_partial_issues-001 | `app/views/user/partial_issues.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_partial_projectlist-001 | `app/views/user/partial_projectlist.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_edit_password-001 | `app/views/user/edit_password.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_login-001 | `app/views/user/login.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_userFiles-001 | `app/views/user/userFiles.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_edit_token-001 | `app/views/user/edit_token.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_resetPassword-001 | `app/views/user/resetPassword.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_partial_postings-001 | `app/views/user/partial_postings.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_verified-001 | `app/views/user/verified.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_edit_emails-001 | `app/views/user/edit_emails.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_signup-001 | `app/views/user/signup.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_user_partial_pullRequests-001 | `app/views/user/partial_pullRequests.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_fileUploader-001 | `app/views/common/fileUploader.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_reviewForm-001 | `app/views/common/reviewForm.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_sharerCount-001 | `app/views/common/sharerCount.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_footer-001 | `app/views/common/footer.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_commentForm-001 | `app/views/common/commentForm.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_uservoice-001 | `app/views/common/uservoice.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_commitMsg-001 | `app/views/common/commitMsg.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_select2-001 | `app/views/common/select2.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_commentUpdateForm-001 | `app/views/common/commentUpdateForm.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_issueLabelColor-001 | `app/views/common/issueLabelColor.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_uploadForm-001 | `app/views/common/uploadForm.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_debug-001 | `app/views/common/debug.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_scripts-001 | `app/views/common/scripts.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_loginDialog-001 | `app/views/common/loginDialog.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_twoColumnModeCheckboxArea-001 | `app/views/common/twoColumnModeCheckboxArea.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_commentAndVoterPairDisplay-001 | `app/views/common/commentAndVoterPairDisplay.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_branchItem-001 | `app/views/common/branchItem.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_usermenu-001 | `app/views/common/usermenu.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_child_commentForm-001 | `app/views/common/child_commentForm.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_childComments-001 | `app/views/common/childComments.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_editor-001 | `app/views/common/editor.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_calendar-001 | `app/views/common/calendar.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_showSubtasksCheckbox-001 | `app/views/common/showSubtasksCheckbox.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_markdown-001 | `app/views/common/markdown.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_navbar-001 | `app/views/common/navbar.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_commentCount-001 | `app/views/common/commentCount.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_childCommentsAnchorDiv-001 | `app/views/common/childCommentsAnchorDiv.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_notificationMail-001 | `app/views/common/notificationMail.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_attachmentFile-001 | `app/views/common/attachmentFile.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_tasklistBar-001 | `app/views/common/tasklistBar.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_voteCount-001 | `app/views/common/voteCount.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_commentDeleteModal-001 | `app/views/common/commentDeleteModal.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_mySeriesMenuTab-001 | `app/views/common/mySeriesMenuTab.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_partial_history-001 | `app/views/common/partial_history.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_common_usermenu_tab_content_list-001 | `app/views/common/usermenu_tab_content_list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_migration_home-001 | `app/views/migration/home.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_migration_migrationPageLayout-001 | `app/views/migration/migrationPageLayout.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_milestone_edit-001 | `app/views/milestone/edit.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_milestone_view-001 | `app/views/milestone/view.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_milestone_partial_status-001 | `app/views/milestone/partial_status.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_milestone_list-001 | `app/views/milestone/list.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_milestone_create-001 | `app/views/milestone/create.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_welcome_restart-001 | `app/views/welcome/restart.scala.html:1` | `(template file, whole-file marker)` |
| GL-view_welcome_secret-001 | `app/views/welcome/secret.scala.html:1` | `(template file, whole-file marker)` |

## 버킷 D — 의도적 제외 (참고용, 4건)

| yona 파일:라인 | 매치 텍스트 근거(yona_file_ref 컬럼 비어있음이 정상) |
|---|---|
| `src/main/kotlin/com/github/yonaprojects/yona/domain/mail/IncomingMailProcessingService.kt:343` | (역방향 패턴 매치 — yona에 대응 없음/무관 명시) |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/site/DataBackupServiceImpl.kt:41` | (역방향 패턴 매치 — yona에 대응 없음/무관 명시) |
| `src/test/kotlin/com/github/yonaprojects/yona/domain/user/UserDetailsServiceImplSpec.kt:12` | (역방향 패턴 매치 — yona에 대응 없음/무관 명시) |
| `src/test/kotlin/com/github/yonaprojects/yona/domain/user/UserSpec.kt:15` | (역방향 패턴 매치 — yona에 대응 없음/무관 명시) |

## 부록 — trivial 심볼 집계 (1414개, 목록 생략)

getter/setter 메서드 및 단순 필드/상수/enum상수 선언으로 분류되어 버킷 C에서 제외한 심볼들. 대부분 Kotlin data class/JPA 엔티티의 자동생성 접근자에 대응하며, 개별 대응 주석 없이도 이식된 것으로 간주 가능하나 확정하지 않음.

**주의(2026-08-26 사용자 지적)**: 이 1,414개 분류는 이 세션 시점의 스냅샷 판정이지 영구 고정값이 아니다. `docs/PARITY_BACKLOG.md`에 신규 등록한 P1-145~147/P2-47~53(특히 P1-147 `ResourcePersistAdapter`의 Watch/Unwatch 정리 로직 추가, P1-145 `ExConstraints`의 프로젝트명 검증 추가)을 실제로 구현하면 관련 클래스(`IssueServiceImpl.kt`/`PostingServiceImpl.kt`/`ProjectServiceImpl.kt`/`ProjectViewController.kt` 등)에 새 코드가 추가되므로, 그 클래스에 속한 trivial 심볼의 실제 필요 여부·구현 상태가 바뀔 수 있다. 이 부록은 각 티켓 구현 후 관련 클래스만이라도 재확인하는 것을 권장하며, 자동으로 갱신되지 않는다는 점을 명시한다.

- `app/models/SearchResult.java`: 54개
- `app/models/Project.java`: 46개
- `app/models/support/SearchCondition.java`: 36개
- `app/models/User.java`: 33개
- `app/models/PullRequest.java`: 33개
- `app/playRepository/GitRepository.java`: 32개
- `app/controllers/UserApp.java`: 30개
- `app/models/History.java`: 28개
- `app/models/NotificationEvent.java`: 28개
- `app/playRepository/FileDiff.java`: 25개
- `app/models/Issue.java`: 22개
- `app/playRepository/PlayRepository.java`: 20개
- `app/controllers/ProjectApp.java`: 19개
- `app/playRepository/Commit.java`: 19개
- `app/data/exchangers/CommentThreadDataExchanger.java`: 18개
- `app/utils/Config.java`: 16개
- `app/controllers/api/IssueApi.java`: 15개
- `app/utils/RouteUtil.java`: 15개
- `app/data/exchangers/IssueDataExchanger.java`: 15개
- `app/data/exchangers/ProjectDataExchanger.java`: 15개
- `app/models/NotificationMail.java`: 15개
- `app/models/PullRequestCommit.java`: 15개
- `app/playRepository/GitBranch.java`: 15개
- `app/utils/LdapService.java`: 14개
- `app/models/CandidateUser.java`: 14개
- `app/mailbox/MailboxService.java`: 13개
- `app/models/support/LdapUser.java`: 13개
- `app/playRepository/BareCommit.java`: 13개
- `app/utils/PlayServletResponse.java`: 12개
- `app/data/exchangers/UserDataExchanger.java`: 12개

## 템플릿 — 상태

템플릿 242개는 파일 단위 마커만 부여(1-T단계, Twirl은 심볼 파싱 불가)했고, `docs/TEMPLATE_BACKLOG.md`가 이미 242개 전부에 대해 legacy와 줄 단위로 대조된 상세 완료 로그를 보유 — 2026-08-26 stale 감사(별도 fork)에서 낡은 내용 0건, 표본 검증 전부 정상으로 확인됨. 이 ledger의 버킷 체계는 템플릿에는 별도 적용하지 않는다(TEMPLATE_BACKLOG.md가 이미 사실상의 ledger 역할을 함).
