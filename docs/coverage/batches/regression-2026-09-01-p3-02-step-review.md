---
id: regression-2026-09-01-p3-02-step-review
type: batch
status: done
created: 2026-09-01
updated: 2026-09-01
relates_to: []
source: docs/COVERAGE_BACKLOG.md:263-295 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-09-01, P3-02 작업으로 인한 재회귀 발견 — 재작업 필요)

- **2026-08-26 이후(P3-02, TASK-0367~0411) 신규/대폭 수정된 클래스들이 이 문서의 재측정 없이 누적**됐다 —
  이번에도 43차 배치 때와 같은 패턴("완료 선언 이후 재측정 없이 문서만 유지")이 반복됨. 사용자 지시("전체
  스위트 회귀 확인 후 3번[커버리지] 확인해줘")로 `./gradlew test`(5788개 전체 GREEN) 직후
  `jacocoTestReport`를 재실행해 P3-02가 새로 만든 클래스들만 스팟체크한 결과, 아래 항목이 95% 미달로
  확인됨(전체 프로젝트 집계 자체는 LINE 98.33%/BRANCH 94.88%/METHOD 96.80%로 준수해 보이지만, 이 문서의
  기준은 **클래스 단위**이므로 집계 평균에 가려져 있었다). **전체 478개+α 클래스에 대한 정식 재감사는
  아직 안 했고, P3-02가 건드린 클래스만 스팟체크한 결과다** — 다음 배치에서 전체 클린 재측정부터
  다시 해야 한다.
  - `PullRequestApiController` — LINE 76.1%, BRANCH 60.7%, METHOD 100%
  - `IssueRestApiController` — LINE 81.2%, BRANCH 66.7%, METHOD 100%
  - `OrganizationRestApiController` — LINE 90.6%, BRANCH 83.3%, METHOD 87.5%
  - `ProjectRestApiController` — LINE 94.2%, BRANCH 85.7%, METHOD 100%
  - `ApiTokenAuthenticationFilter` — LINE 98.6%, BRANCH 81.6%, METHOD 100% (**재회귀**: 2026-08-26
    4차 배치에서 이미 `[x]` 완료 처리됐던 클래스인데, P3-02가 Step6.5/Step8.x에서 `metadata`
    세그먼트·목록 스코프 필터링·서브패스 매칭 로직을 추가하면서 새 분기가 늘어 다시 미달로 떨어짐)
  - `ApiTokenAuthorizer` — LINE 100%, BRANCH 82.4%, METHOD 100%
  - `ApiTokenServiceImpl` — LINE 100%, BRANCH 85.0%, METHOD 100%
  - `ApiToken` — LINE 100%, METHOD 54.5% (데이터 클래스 보일러플레이트로 추정, 구조적 예외 여부 미확인)
  - `ApiTokenScope` — LINE 100%, METHOD 40.0% (위와 동일 추정, 미확인)
  - `ApiTokenAuthenticationFilter$Companion` — BRANCH 85.0%
  - `PullRequestServiceImpl` — 2026-08-26 시점부터 이미 `[~]`였던 항목(BRANCH 86.0%)이 P3-02의
    라벨/담당자 로직(Step8.6 항목4) 추가로 더 커졌을 가능성 있음, 이번 스팟체크에선 별도 재측정 안 함.
  - `LabelRestApiController`/`SearchRestApiController`/`WebhookRestApiController`/
    `ProjectPermissionRestApiController`/`UserIssueStatusRestApiController` 등 나머지 P3-02
    신규 클래스는 스팟체크 결과 이미 95% 이상(대부분 100%) — 목록에서 제외.
- **다음 배치 진행 시 참고**: 이 문서 도입부의 "자의적 판단 하지마" 원칙 그대로 적용 — 특히
  `PullRequestApiController`/`IssueRestApiController`의 낮은 BRANCH는 인가 실패(403)/404/부분 필드
  누락 등 아직 테스트 안 된 실제 에러 경로일 가능성이 높아(단순 보일러플레이트가 아님) 우선순위를
  높게 잡는 게 합리적으로 보인다(자의적 판단 아님, 관찰 근거: 두 컨트롤러 다 위임 대상 메서드 수는
  적은데 LINE 자체가 낮아 성공 경로 이외 코드가 통째로 안 커버된 정황).
