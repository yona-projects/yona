---
id: batch-23-24
type: batch
status: done
created: 2026-08-25
updated: 2026-08-25
relates_to: []
source: docs/COVERAGE_BACKLOG.md:159-171 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-08-25, 23~24차 배치 완료 후)

- 추가 완료([x]): `IssueShareController`(BRANCH 94.2%, 구조적 한계로 인정), `SearchResult`(BRANCH 100%), `BoardController`(BRANCH 100%), `Hunk`(BRANCH 100%), `DiffLine`(BRANCH 100%), `LfsStorageController`(BRANCH 100%), `LineEnding`(BRANCH 100%), `ReviewViewController`(BRANCH 100%), `VoteController`(BRANCH 100%), `User`(BRANCH 96%), `CodeController`(BRANCH 100%) — 11개 클래스, 미실행 라인+분기 합계 상위 순
- 작업 방식 변경: 사용자 지시로 테스트 작성을 에이전트 포크에 병렬 위임(`LineEnding`/`VoteController`/`User`/`CodeController`)하고, 메인 세션은 소스 투자·`ReviewViewController` 직접 작성·gradle 실행/검증/백로그 갱신을 순차 담당하는 방식으로 전환
- **사건**: 에이전트 위임과 메인 세션의 백그라운드 대기(`ScheduleWakeup`)가 겹치면서 동일 클래스 대상 `./gradlew test` 가 여러 차례 동시 실행되어 `build/classes/kotlin/test`가 손상되는 사고 발생(`ClassNotFoundException`/`NoSuchFileException`). `pkill -f GradleWorkerMain` 로 전부 정리 후 `clean compileKotlin compileTestKotlin`로 복구, 이후 동적 `/loop` 워크업 사용을 중단하고 단일 foreground gradle 실행으로 전환하여 재발 방지
- `Hunk`/`DiffLine`/`SearchResult`는 Kotlin data 클래스의 자동생성 getter/setter를 로직 테스트가 건드리지 않아 METHOD 커버리지가 낮았던 패턴 — 프로퍼티 접근자 전용 테스트 추가로 해결
- 이번 배치 신규 실버그/죽은코드 없음
- **추가 완료(같은 23~24차 배치 연장)**: `CodeRange`(BRANCH 100%), `GitCommit`(BRANCH 100%), `FavoriteController`(BRANCH 100%), `SiteViewController`(BRANCH 97.1%), `TranslationController`(BRANCH 100%), `ReviewApiController`(BRANCH 97.2%), `MigrationApiController`(BRANCH 100%), `CodeCommentThread`(BRANCH 100%), `LabelController`(BRANCH 95.5%) — 9개 클래스 추가. 나머지 위임 작업(`HistoryUtil`, `SvnCommit`, `LabelStyleController`)은 구조적으로 도달 불가한 분기가 남아 95% 미달이나 `[i]`로 인정(각 행에 근거 명시)
- 병렬 위임 방식이 예상보다 더 많은 클래스로 자연 확장되어(에이전트가 완료 후 스스로 다음 무거운 항목을 이어서 착수) 한 번에 20개 이상 클래스가 동시 진행되는 상황 발생 — 메인 세션은 파일 mtime으로 "안정화(수 분간 미변경)" 여부를 확인한 뒤에만 검증/커밋 대상에 포함시켜 진행 중인 에이전트의 파일과 충돌하지 않도록 처리
- `NotificationEventMerger`(BRANCH 90.2%)/`BranchViewController`(BRANCH 85.7%)/`SearchController`(BRANCH 87.0%)는 이번 배치에서 함께 작업이 시작되었으나 95% 미달로 `[ ]` 유지 — 다음 배치에서 이어서 처리 필요
- **마무리(23~24차 배치 최종 클로즈아웃)**: 위 3개 클래스를 `javap`로 실제 컴파일된 바이트코드까지 확인해 마무리. `NotificationEventMerger`는 실제 테스트 가능한 분기(무관한 이벤트 타입 그대로 통과, 리뷰 댓글 스레드 id 없음)를 찾아 테스트 추가로 BRANCH 95.1% 달성해 `[x]` 완료. `BranchViewController`는 `isCodeAccessibleMemberOnly=true+조직멤버` 테스트가 실제로는 다른 코드 경로(그룹 옵션이 꺼진 `isAllowed()` 경로)를 타고 있었음을 발견해 정확한 테스트로 교체 추가, BRANCH 92.9%까지 끌어올린 뒤 잔여 2건은 `javap` 확인 결과 (1)`String.toUpperCase()`가 JDK 계약상 null을 반환할 수 없어 생기는 Kotlin 방어적 null체크, (2)`AccessControl.isAllowed()`가 UPDATE/DELETE를 동일 코드로 처리해 두 호출이 항상 같은 값이라 도달 불가 — `[i]` 인정. `SearchController`는 로그인 사용자 id 없음/조직 역할 id 없음 분기를 새로 찾아 테스트 추가로 BRANCH 93.5%까지 올린 뒤 잔여 3건은 `Role.id` 자체가 아니라 `OrganizationUser.role`(non-null 필수 프로퍼티)이 null인 경우와 ORG_MEMBER(7L)/ORG_ADMIN(6L) 상호배타 조건이 구조적으로 도달 불가 — `[i]` 인정. 이로써 이번 세션에서 작업한 26개 클래스 전체(20개 `[x]` 완료 + 6개 `[i]` 구조적 예외: `IssueShareController`/`SvnCommit`/`LabelStyleController`/`HistoryUtil`/`BranchViewController`/`SearchController`) 마무리, 전체 회귀(전 스위트) 재확인 통과(BUILD SUCCESSFUL, 6분대)
- **부수적으로 확인된 사실(향후 재발 방지용)**: 이번 세션 중 서브에이전트 일부가 "gradle 절대 실행 금지" 지시를 반복적으로 위반해(특히 `ReviewViewController`/`CodeController` 담당 포크가 완료 후에도 스스로 계속 살아남아 전체 스위트를 반복 실행) 최소 3차례 빌드 손상(`ClassNotFoundException`/`EOFException`)이 재발했다. `TaskStop`으로 강제 종료 후 `pkill -9 -f GradleWorkerMain`+`clean compileKotlin compileTestKotlin`로 복구했다. 향후 병렬 위임 시 `ListAgents`로 10분 이상 살아있는 포크를 주기적으로 점검해 즉시 종료하는 것을 권장
