---
title: Apache Magpie 연동 구현 계획
type: architecture
sources:
  - magpie-integration.md
  - https://magpie.apache.org/docs/tools/tracker/readme
  - https://magpie.apache.org/docs/tools/change-request/readme.md
  - https://magpie.apache.org/docs/tools/github/readme
  - https://magpie.apache.org/docs/tools/forgejo/readme
  - ../decisions.md
updated: 2026-11-03
---

# Apache Magpie 연동 구현 계획 (X-23)

> **상태: 결정 확정(2026-11-03), 구현 진행 중.** 선생님이 질문 1(도구)과 5(CLI 옵션)를 정하셨고, 나머지 질문 8개는 "진행해줘"로 **권장안대로 확정**되었다(7절 표). 단계 0(Magpie 저장소 확인)은 끝났고(아래 "단계 0 결과"), 단계 1(서버 REST)부터 TDD로 진행한다. 결정은 [decisions.md](../decisions.md) D-29에 적었다.

## 결정 (선생님, 2026-11-03)

- **어댑터는 `yona` CLI 계열만 쓴다. `yona api`(CLI가 인증해서 REST 경로를 직접 부르는 명령, `gh api`와 같다)를 포함한다.** `curl`은 쓰지 않는다. Magpie의 GitHub 어댑터가 `gh` 계열(`gh`, `gh api`)만 쓰고 Jira 어댑터도 한 가지 REST 방식만 쓰는 것처럼 한 어댑터가 한 도구 계열로 일관되게 간다.
- **Magpie 계약이 요구하는 동작은 빠짐없이 채운다**("Magpie가 요구하는 건 넣어야지"). 서버에 없는 조회는 서버에 더하고, 그 조회를 어댑터가 자주 부르면 CLI 옵션으로도 더한다(질문 5는 이 결정으로 정해진 것으로 본다, 아래 4.3).

## 단계 0 결과 (2026-11-03, Magpie 저장소 `main`을 읽었다)

선생님이 허락하신 대로 `apache/magpie`(Apache-2.0, 약 29MB)를 임시 폴더에 받아 `tools/github`, `tools/forgejo`, `tools/change-request`, `tools/tracker`와 검증기를 읽었다.

- **GitHub 어댑터는 `gh` 계열만 쓴다**(`gh issue`, `gh pr`, `gh api --jq`, GraphQL도 `gh api graphql`). `curl`과 다른 도구를 섞지 않는다. 선생님의 결정(`yona` CLI 계열만, `yona api` 포함)이 이 모양과 같다.
- **계약 동작의 명령 대응표는 `tools/github/operations.md`의 "Contributor activity" 표**(활동 동작)와 각 스킬의 `contract-binding.md`(PR 동작: `list_open`, `get`, `get_discussion`, `status`, `post_review`, `reject`)에 있다. 어댑터는 **동작마다 해당 호스트의 명령을 적은 표**를 쓰는 것이 본체이다.
- **어댑터 디렉터리의 구성**: `README.md`(머리 정보: `Capability`, `Coverage`, `Kind`, `Vendor`, `Prerequisites`, `Configuration`), `tool.md`, `operations.md`, `source-control.md`, `issue-template.md`, `labels.md`, `project-board.md`(보드가 있을 때만). 문서만 있는 어댑터는 `Runtime: Bash — doc-only adapter`로 적는다.
- **완전과 부분**: `Coverage: partial`인 어댑터(Forgejo, GitLab, Bitbucket)는 "완전한 계약을 채우지 못하므로 선택 가능한 백엔드로 광고하지 않는다"고 적혀 있다. **Yona 어댑터는 두 계약의 모든 동작을 채워 `Coverage` 줄이 없는(완전한) 어댑터를 목표로 한다**(선생님의 "요구하는 건 넣어야지"). 보드와 이슈 본문 양식 같은 계약 밖 기능은 1차 범위가 아니므로 해당 파일은 만들지 않고 `README.md`에 "범위 밖"으로 적는다.
- **검증기**(`skill-and-tool-validator`)는 스킬 정의(`SKILL.md`)의 머리 정보, 상대 링크, `<PROJECT>`, `<upstream>`, `<tracker>` 자리표시자 규칙을 검사한다. 어댑터 문서(`tools/*`)에는 적용되지 않으므로, 어댑터는 **링크 무결성과 자리표시자 규칙**을 우리 시험이 같은 방식으로 확인한다.
- **활동 동작의 정의**(GitHub 어댑터 기준): `list_triaged`는 "다른 사람이 연 이슈에 사람이 댓글을 단 것"이고 라벨 이벤트는 검색하지 않는다(GitHub는 댓글만 센다). `list_commented`는 이슈와 PR 모두이고 **창 안의 첫 댓글 날짜**로 날짜를 매긴다. 이 정의에 맞춰 Yona의 활동 조회는 **댓글을 기준**으로 하고 분류 변경(라벨, 상태, 담당자)은 선택 확장으로 둔다.

## 진행 상황

| 단계 | 상태 |
|---|---|
| 0 Magpie 저장소 확인 | **완료**(2026-11-03) |
| 1 서버: 이슈 목록 조건과 필드, 활동 조회(`list_filed`, `list_triaged`, `list_commented`, `list_created`) | **완료**(2026-11-03, `ExternalTrackerActivityApiTest`, 문서는 [api-project-reads.md](api-project-reads.md)) |
| 2 서버: PR 목록 조건(`state=merged|all`, 기간, 오래됨, 정렬, `authorFirstTime`)과 토론 조회 | **완료**(2026-11-03, 문서는 [api-pull-requests.md](api-pull-requests.md)) |
| 3 서버: 리뷰 활동 조회(`list_reviews_given`)와 작성 PR 조건(`list_authored`) | **완료**(2026-11-03, 단계 2와 함께) |
| 3b CLI 옵션(`yona-cli`: `issue list`, `pr list`의 기간, 정렬, 상태 옵션과 `--json` 필드) | **완료**(2026-11-03, `yona-cli` 커밋 `c4e341d`, Go 시험 포함) |
| 4 어댑터 문서 | 대기 |
| 5 실제 확인 | 대기 |
| 6 문서와 결정 마무리 | 대기 |

## 1. 목표와 결론

Apache Magpie(ASF의 AI 기반 저장소 유지보수 도구)가 Yona 프로젝트의 이슈와 풀 리퀘스트(PR)를 다룰 수 있게 한다. 구체적으로 Magpie의 두 계약 `contract:tracker`(이슈, 읽기 전용)와 `contract:change-request`(PR, 읽기와 쓰기)를 Yona용 어댑터가 채우게 한다.

**결론 먼저.** 이 일은 두 갈래이다.

1. **서버(Yona) 쪽**: Magpie 계약이 요구하는데 아직 없는 조회 조건과 응답 필드를 REST에 더한다. 기존 경로와 응답 계약은 바꾸지 않는다(D-28). 지난번 분석(2026-10-28) 때와 달리 X-5(PAT), X-12(리뷰 판정), X-15(PR REST), X-19(일반 REST)가 **이미 구현되어 있어서**, 새로 필요한 것은 **기간과 사람 조건의 조회**와 몇 가지 필드뿐이다.
2. **어댑터 쪽**: Magpie의 GitHub와 Forgejo 어댑터는 **마크다운 문서**(`tools/<이름>/`: `readme.md`, `tool.md`, `operations.md` 등)이고, 에이전트가 문서의 명령 조리법(`gh pr merge`, `gh api … --jq` 같은)을 그대로 실행한다(Jira 어댑터는 Groovy와 Python 코드로 된 REST 브리지이다). Yona 어댑터는 GitHub 어댑터와 같은 문서 모양으로 쓰고, 명령은 **`yona` CLI**(`yona-cli`, `gh`와 사용법이 같다)만 쓴다.

## 2. Magpie 쪽 사실 (2026-11-03에 문서로 확인)

- **어댑터 선택**: 이슈는 `<project-config>/issue-tracker-config.md`의 `tracker_type`(`jira`, `github-issues` 등)과 `url`, `project_key`로, PR은 `projects/<project>/project.md`의 `change_request.backend`(`github`, `jira-patch`, `mail-patch`, `gitlab`, `gerrit`, `none`)로 고른다. 새 호스트는 값과 디렉터리 이름을 더하는 방식이다.
- **선례**: `tools/github/`(문서만 있는 어댑터, 도구는 `gh`, `git`, `jq`이고 `jq`는 `gh api --jq`로 쓴다, 출하), `tools/forgejo/`(Forgejo와 Gitea, **일부만 구현**, 토큰 헤더와 `tea` CLI와 `curl`), `tools/jira/`(코드로 된 REST 브리지: Groovy와 Python, `curl`은 쓰지 않는다). 자체 호스팅 포지를 붙인 Forgejo 어댑터가 가장 가깝다. 이 어댑터는 안전 설정(secure setup)이 `curl`을 막으면 REST 전용 동작은 사람이 실행해야 한다고 적는다. **그래서 Yona 어댑터는 `curl` 대신 `yona` CLI(`yona api` 포함)를 쓴다.** GitHub 어댑터의 동작별 명령이 적힌 `operations` 문서는 열리지 않아(404) 확인하지 못했다. 단계 0에서 저장소로 확인한다.
- **계약의 규칙**: 인증은 계약이 정하지 않고 어댑터가 정한다. 구현하지 않는 동작은 `NotApplicable`을 던지고 호출한 쪽은 "수집 안 함"으로 보고한다. CI와 병합 가능 신호가 없는 호스트는 `checks: none`, `mergeable: unknown`을 돌려주면 된다. 어댑터 출력은 데이터로만 다루며 지시로 읽지 않는다.
- **GitHub 어댑터는 계약의 모든 동작을 출하**하고, GitLab, Forgejo, Bitbucket은 활동 동작이 "후속"이다. 즉 **일부 동작을 비워 둔 채 시작하는 것이 Magpie 쪽 관례**이다.

## 3. 동작별 현황 (Yona의 지금 REST와 빈칸)

경로의 `$P`는 `/-_-api/v1/owners/{owner}/projects/{projectName}`이다. 모두 토큰 인증이다.

### 3.1 `contract:tracker` (이슈, 읽기 전용)

| 동작 | 입력과 반환 | Yona 현황 | 빈칸 |
|---|---|---|---|
| `list_filed(person, since, end)` | 사람이 연 이슈. `id, permalink, created, closed, labels[]` | `GET $P/issues`가 `author`, `state`, 라벨, 정렬, 쪽 조건을 받는다 | **기간 조건**(`createdSince`, `createdUntil`)과 응답의 **`closedAt`** |
| `list_triaged(person, since, end)` | 다른 사람이 연 이슈 가운데 사람이 분류(라벨, 상태, 담당자 변경 또는 댓글)한 것. `id, permalink, first_activity, labels[], pushback_candidate` | 이슈 이벤트(`issue_event`)와 댓글이 저장되어 있다. 조회 REST는 없다 | **사람과 기간별 활동 조회** |
| `list_commented(person, since, end)` | 사람이 댓글을 단 스레드(이슈와 PR). 같은 모양 | 댓글은 저장되어 있다 | 위와 같은 **활동 조회**(이슈와 PR 모두) |
| `list_created(since, until)` | 기간 안에 열린 모든 이슈. `id, permalink, kind, created, author, author_first_time` | 기간 조건이 없다 | **기간 조건**과 **`author_first_time`**(그 작성자의 이 프로젝트 첫 이슈나 PR인지) |
| `first_reply(id)` | 첫 메인테이너 댓글(봇 제외). `author, created, url, body` | `GET $P/issues/{n}/comments`와 구성원 목록 REST가 있다 | **없음**(어댑터가 댓글 가운데 첫 구성원 댓글을 고른다) |

### 3.2 `contract:change-request` (PR)

| 동작 | Yona 현황 | 빈칸 |
|---|---|---|
| `list_open(filter)` (`author`, `label`, `age`, `review-for-me`, `stale`) | `GET $P/pullrequests`는 `state`와 쪽만 받는다 | **조건 확장**: `author`, `label`, `reviewer`(나에게 요청된 것), `createdBefore`(나이), `updatedBefore`(오래됨), 최근 갱신 순 정렬 |
| `get(id)` (`diff`, `base`, `commits[]`, `mergeable`, `checks`) | `GET …/pullrequests/{n}`, `/commits`, `/files`가 있고 `mergeable`(`clean`, `conflicting`)이 이미 나온다 | 거의 **없음**(`diff`를 `/files`의 패치로 조립한다. 매우 큰 diff는 계약이 URL을 허용한다) |
| `get_discussion(id)` (`comments[]`의 `kind`, `participants[]`, `unresolved_count`) | `GET …/comments`, `/reviews`, `/timeline`이 있다 | **`unresolved_count`**(열린 코드 리뷰 스레드 수)와 `participants[]`가 응답에 없을 수 있다. 확인해서 더한다 |
| `post_review(id, verdict, body)` | `POST …/reviews`가 `verdict`(`APPROVED`, `CHANGES_REQUESTED`, `COMMENTED`)와 본문을 받는다(X-12) | **없음**(`approve`↔`APPROVED`, `request-changes`↔`CHANGES_REQUESTED`, `comment`↔`COMMENTED`로 옮긴다) |
| `land(id, strategy)` | `POST …/merge`가 `method`(`merge`, `squash`, `rebase`)를 받는다(X-28). 응답은 갱신된 PR이다 | **반환 `landed_ref`**: 병합 커밋 번호가 응답에 있는지 확인해 없으면 더한다. 프로젝트가 허용하지 않은 방식은 400이므로 어댑터가 "대신 쓴 방식"을 보고한다 |
| `reject(id, reason)` | `POST …/close`가 있다(이유 입력은 없다) | **선택**: 어댑터가 이유를 댓글로 남기고 닫는다(두 번 호출) |
| `status(id)` | PR 상태와 `mergeable`이 나온다 | **없음**(`checks`는 Yona에 CI가 없어 `none`) |
| `list_authored(person, since, end, state)` | `GET /-_-api/v1/search/pullrequests`가 있다 | **작성자와 기간, 병합 시각 조건**과 `landed_at`, `author_first_time` |
| `list_reviews_given(person, since, end)` | 리뷰는 `pull_request_review`에 저장되어 있다 | **사람과 기간별 리뷰 조회**(`first_review_at`, `body_length`, `inline_comment_count`) |
| `list_authored_commits(person, limit)` | 커밋 이력 REST는 있으나 작성자 이메일로 찾는 조회는 확인하지 못했다 | 1차에서는 `NotApplicable`(선택 동작) |

### 3.3 인증

- **토큰**: `Authorization: token <값>`(PAT, `yona auth login`으로 저장). 이슈 읽기는 `read:repo`, PR 쓰기(리뷰, 병합, 닫기)는 `repo` 범위이다.
- **권장 운영**: Magpie 전용 봇 계정을 만들어 프로젝트의 구성원(필요한 만큼의 권한)으로 두고 그 계정의 토큰만 준다. 읽기 전용으로 쓰려면 `read:repo` 토큰만 주면 쓰기 동작은 서버가 403으로 거절한다.

## 4. 설계

### 4.1 서버 REST (모두 읽기 전용 추가, 기존 응답 계약 유지)

D-29의 선택지 (B)에 해당한다. 새 경로 이름은 X-29, X-30의 규칙(`/-_-api/v1/owners/{owner}/projects/{projectName}`)을 따르고 OpenAPI 문서(구조 시험)에 올린다.

| 추가 | 내용 |
|---|---|
| 이슈 목록 조건 | `GET $P/issues`에 `createdSince`, `createdUntil`(ISO-8601)을 더하고 항목에 `closedAt`, `authorFirstTime`을 더한다 |
| 이슈 활동 조회 | `GET $P/activity?actor={loginId}&since=&until=&kinds=comment,triage,filed` : 이슈와 PR의 활동(댓글, 라벨, 상태, 담당자 변경)을 `{kind, issueNumber, isPullRequest, permalink, createdAt, labels[]}`로 돌려준다. `list_triaged`와 `list_commented`가 쓴다 |
| PR 목록 조건 | `GET $P/pullrequests`에 `author`, `label`, `reviewer`, `createdBefore`, `updatedBefore`, `sort=updated`를 더한다 |
| 작성 PR 조회 | 목록 조건에 `createdSince`, `createdUntil`, `mergedSince`, `mergedUntil`을 더해 `list_authored`를 채운다 |
| 리뷰 활동 조회 | `GET $P/reviews?reviewer=&since=&until=` : `{pullRequestNumber, permalink, reviewer, firstReviewAt, bodyLength, inlineCommentCount, labels[]}` |
| PR 상세 보강 | `unresolvedCount`, `participants[]`, 병합 응답의 병합 커밋 번호(없다면) |

### 4.2 어댑터 (마크다운, `tools/yona/`)

Magpie의 `tools/github/`와 같은 구성으로 쓴다.

```
tools/yona/
├── readme.md        어댑터 개요, 요구 도구(yona CLI, jq), 인증 확인(`yona auth status`)
├── tool.md          동작 목록(계약의 12개 동작이 어느 명령으로 풀리는지)
├── operations.md    조리법: 동작별 `yona pr …`, `yona issue …`, `yona api <경로> --jq …`
└── source-control.md  (Yona는 Git 저장소이므로 git 조리법은 github 어댑터를 참고해 짧게)
```

- **명령은 `yona` CLI 계열만 쓴다.** 서브명령이 있는 동작(`yona issue list`, `yona pr view`, `yona pr review` 등)은 서브명령으로, 없는 조회는 `yona api <경로> --jq …`로 푼다(`gh api`와 같다). `curl`을 쓰지 않으므로 Magpie의 안전 설정에서도 동작한다.
- 설정 키: `project.md`에 `change_request.backend: yona`, `issue-tracker-config.md`에 `tracker_type: yona`, `url`(서버 주소), `project_key: <owner>/<project>`.
- 어댑터는 서버가 돌려주는 값을 계약의 이름(`id, permalink, created, closed, labels[]` 등)으로 옮기는 `jq` 식을 포함한다.

### 4.3 CLI

`yona-cli`는 이 저장소 밖(`../yona-cli`, Go)에 있고 서버와 따로 시험하고 배포한다. 선생님의 결정에 따라 다음을 한다.

- **`yona api`는 이미 있다.** 새 서버 경로는 `yona api`로 바로 부를 수 있으므로 어댑터는 서버 단계가 끝나는 즉시 동작한다.
- **자주 쓰는 목록 조건은 서브명령 옵션으로도 더한다**: `yona issue list --since --until --json …,closedAt`, `yona pr list --reviewer --label --updated-before --created-before --sort updated`. 활동 조회(`activity`)와 리뷰 활동 조회는 어댑터 전용 성격이 커서 1차는 `yona api`로 부른다.
- 어댑터 문서에 **필요한 CLI 최소 버전**을 적는다(`yona --version`을 확인하는 첫 단계 포함).

## 5. 단계

| 단계 | 내용 | 시험 |
|---|---|---|
| 0 | Magpie 저장소의 어댑터 검증기와 `tools/github`, `tools/forgejo`의 실제 파일을 읽어 문서 구성을 확정한다(내려받기 전에 크기와 출처를 알리고 허락을 구한다) | 읽기만 |
| 1 | 서버: 이슈 목록 조건과 필드, 활동 조회 REST(`list_filed`, `list_triaged`, `list_commented`, `list_created`) | 통합 시험, OpenAPI 구조 시험 |
| 2 | 서버: PR 목록 조건, 상세 보강, 병합 커밋 번호(`list_open`, `get`, `get_discussion`, `status`, `land`) | 통합 시험 |
| 3 | 서버: 작성 PR 조건과 리뷰 활동 조회(`list_authored`, `list_reviews_given`) | 통합 시험 |
| 3b | CLI(`yona-cli`): 목록 조건 옵션(`--since`, `--until`, `--reviewer`, `--label`, `--updated-before`, `--created-before`, `--sort`)과 `--json` 필드(`closedAt` 등)를 서브명령에 더한다. 서버 단계 1, 2가 끝난 뒤 짝을 맞춘다 | Go 시험, 실제 서버 대상 확인 |
| 4 | 어댑터 문서 `tools/yona/` 작성(계약의 모든 동작을 `yona` 서브명령과 `yona api --jq`로) | 계약 모양 시험(아래) |
| 5 | 실제 확인: 실행 중인 Yona에 대해 어댑터 조리법을 그대로 실행하고 각 동작의 출력이 계약의 필드와 형식과 같은지 확인한다 | e2e 스크립트 |
| 6 | 문서: `magpie-integration.md` 갱신, D-29 확정, 로드맵 X-23 완료 | |

**계약 모양 시험**: 조리법의 `yona` 명령을 실제로 실행하는 스크립트가 Yona를 띄우고 이슈와 PR을 만들어 놓은 뒤 동작마다 반환 필드(이름, 형식, 날짜 표기, `null` 처리)를 단정한다. Magpie의 에이전트 런타임까지 돌리지는 않는다(LLM과 별도 환경이 필요하다).

## 6. 위험과 한계

- **프로젝트 단위**: Magpie의 설정은 저장소(프로젝트) 하나를 가리킨다(`upstream_repo`, `project_key`). 사이트 전체에 걸친 사람별 활동은 1차 범위가 아니다.
- **분류 활동의 정의**: `list_triaged`는 "라벨, 상태, 구성 요소, 우선순위, 담당자, 해결 변경 또는 댓글"이다. Yona에는 구성 요소와 우선순위가 없다(상태, 라벨, 담당자, 마일스톤, 댓글만). 있는 것만 센다.
- **CI 없음**: `checks`는 항상 `none`이고 `mergeable`만 의미가 있다. Magpie의 게이트는 "참고용"이 된다.
- **Magpie 쪽 변경**: 문서는 읽은 시점(2026-11-03)의 것이고 계약이 바뀔 수 있다. 어댑터는 Magpie의 저장소에 기여해야 널리 쓰이는데 그것은 ASF 프로젝트의 절차(기여 규칙)를 따른다.

## 7. 질문과 권장안

| # | 질문 | 선택지 | 권장 |
|---|---|---|---|
| 1 | 어댑터가 쓰는 도구 | (가) `yona` CLI와 `yona api` (나) `curl`과 `jq` (다) 둘 다 | **결정(선생님): (가)**, `yona api` 포함, `curl` 불사용 |
| 2 | 어댑터 문서를 둘 곳 | (가) 이 저장소(`docs/magpie/tools-yona/`)에 쓰고, Magpie에 기여하는 것은 선생님 결정 뒤 별도로 한다 (나) 바로 Magpie 포크에 쓴다 | **확정(권장 수용)** **(가)**: 우리 쪽 버전과 서버 변경을 함께 관리할 수 있고, 기여는 ASF 절차(CLA, 메일링 리스트 논의)가 필요해 선생님이 판단하실 일이다 |
| 3 | 1차 범위 | (가) 이슈 5개 동작과 PR 7개 동작만 (나) 활동 동작 `list_authored`, `list_reviews_given`까지 (다) 활동 3개 모두 | **확정(권장 수용)** **(나)**: 기여자 지표에 쓰이는 두 가지는 Yona가 줄 수 있고, `list_authored_commits`는 `NotApplicable`로 두는 것이 Magpie의 관례이다 |
| 4 | 서버 REST의 모양 | (가) 4.1의 새 조건과 활동 조회 경로(기존 응답 계약 유지) (나) 어댑터가 화면용 JSON을 읽어 직접 거른다 (다) 활동 조회는 이슈 목록 조건으로만 풀고 새 경로를 만들지 않는다 | **확정(권장 수용)** **(가)**: (나)는 느리고 화면이 바뀌면 깨지며(D-29), (다)는 분류 활동(라벨, 상태, 담당자 변경)을 이슈 목록 조건으로 표현할 수 없다 |
| 5 | CLI에 새 조회 옵션을 더할지 | (가) 더하지 않는다. 어댑터가 `yona api`로 부른다 (나) 자주 쓰는 것을 더한다 | **결정(선생님, "Magpie가 요구하는 건 넣어야지")으로 (나)로 본다**: 자주 쓰는 목록 조건은 CLI 옵션으로 더하고, 활동 조회는 1차에 `yona api`로 부른다. 범위를 다르게 보셨다면 알려 주십시오 |
| 6 | `reject(id, reason)`의 이유 | (가) 어댑터가 이유를 댓글로 남기고 닫는다(호출 두 번) (나) 서버 `close`에 `reason`을 더해 한 번에 한다 | **확정(권장 수용)** **(가)**: 서버를 바꾸지 않고 계약을 채운다 |
| 7 | 메인테이너의 정의(`first_reply`) | (가) 프로젝트 구성원 가운데 역할이 manager와 member인 사람(게스트와 봇 제외) (나) manager만 | **확정(권장 수용)** **(가)**: GitHub 어댑터가 `OWNER`, `MEMBER`, `COLLABORATOR`를 모두 메인테이너로 보는 것과 같다 |
| 8 | 봇 계정과 토큰 범위 | (가) 읽기 전용(`read:repo`)과 쓰기(`repo`)를 문서에 구분해 안내한다 (나) 안내하지 않는다 | **확정(권장 수용)** **(가)**: `post_review`, `land`, `reject`는 쓰기이고 Magpie는 사람 확인 뒤에만 부르므로 운영자가 범위를 정할 수 있어야 한다 |
| 9 | 단계 0의 Magpie 저장소 내려받기 | (가) 저장소를 내려받아(크기와 출처를 알리고 허락을 구한 뒤) 검증기와 실제 `tools/github`, `tools/forgejo` 문서를 읽는다 (나) 웹 문서만 읽는다 | **확정(권장 수용)** **(가)**: 어댑터 문서의 정확한 구성과 검증 규칙을 보고 쓰는 것이 안전하다. 허락은 따로 구하겠다 |
| 10 | 사이트 전체 사람별 활동 | (가) 1차는 프로젝트 단위만 (나) 사이트 전체 활동도 이번에 만든다 | **확정(권장 수용)** **(가)**: Magpie의 설정이 저장소 하나를 가리키고, 사이트 전체는 권한 계산이 커진다 |

결정이 정해지면 단계 0부터 TDD로 진행한다. 서버 REST는 일반 규칙(시험 먼저, 한글 API 주석, 위키 문서)을 따르고, 어댑터 문서는 계약 모양 시험으로 확인한다.
