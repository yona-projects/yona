---
id: normal-priority-review
type: finding
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

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
