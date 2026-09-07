---
id: regression-2026-09-01-p3-02-step8-7-3
type: batch
status: done
created: 2026-09-01
updated: 2026-09-01
relates_to: []
source: docs/COVERAGE_BACKLOG.md:296-321 — 이관 전 버전은 git 히스토리 참고
---

## 진행 현황 갱신 (2026-09-01, P3-02 Step8.7 3번 — 순환 직렬화 버그 수정으로 인한 재측정)

- `p3-02-cli-and-rest-api.md` "9라운드"에서 `IssueRestApiController`/`PullRequestApiController`/
  `SearchRestApiController`가 JPA 엔티티(`Issue`/`PullRequest`/`Project`)를 그대로 반환해 생기던
  무한 순환 직렬화 버그를 응답 DTO 도입으로 수정하면서, 위 재회귀 항목의 세 클래스가 자연스럽게
  다시 측정됐다(엔드포인트 본문이 엔티티 위임 한 줄에서 DTO 변환 로직으로 늘어 커버 대상 라인/분기
  자체도 늘었다). `--tests "com.github.yonaprojects.yona.web.*" --tests "com.github.yonaprojects.yona.config.*"`
  스팟체크 기준(전체 스위트는 아래 "9라운드" 로그의 사전 존재 플레이키 실패 때문에 이번엔 스팟체크로
  대체):
  - `IssueRestApiController` — LINE 83.3%(30/36), BRANCH 70.0%(14/20), METHOD 100%(20/20) — 이전
    81.2/66.7/100에서 소폭 개선. 여전히 95% 미달 — 잔여 분기는 여전히 `mapBody`가 감싼 뒤의
    404(`projectRepository.findByOwnerAndName` 실패) 경로가 엔드포인트별로 다 테스트되지 않은
    정황(위 "다음 배치 진행 시 참고" 관찰과 동일한 성격) — 다음 배치로 이월.
  - `PullRequestApiController` — LINE 77.6%(38/49), BRANCH 63.3%(19/30), METHOD 100%(28/28) —
    이전 76.1/60.7/100에서 소폭 개선, 동일한 이유로 95% 미달 — 다음 배치로 이월.
  - `SearchRestApiController` — LINE 100%(17/17), BRANCH 100%(8/8), METHOD 100%(11/11) — 기존에도
    95% 이상이었고 DTO 변환 추가 후에도 유지.
  - 신규 `RestApiResponseDto.kt`(엔티티→DTO 변환 확장함수 모음) — LINE 76.7%(79/103), BRANCH
    50.0%(9/18), METHOD 63.6%(7/11). 골든패스 수동검증(이슈/PR 생성·조회·목록, 담당자/라벨 없는
    경로)만 실제 서버로 확인했고, `AssigneeResponse`/`GitCommitResponse`/`PullRequestCommitResponse`
    등 담당자·머지 결과가 있는 경로의 변환 함수는 이번 라운드 단위/통합 테스트로 직접 커버하지
    않았다 — 다음 배치로 이월.
  - 세 클래스 모두 95% 목표에는 아직 못 미치지만 이번 라운드의 목적(순환 직렬화 버그 수정)은
    이미 실서버 골든패스로 검증됐다 — 커버리지 목표 완전 달성은 전체 재감사와 함께 다음 배치로
    넘긴다(전체 478개+α 클래스 재감사는 여전히 미착수).
