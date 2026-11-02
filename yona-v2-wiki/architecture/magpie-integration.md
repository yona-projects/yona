---
title: Apache Magpie 연동 분석
type: architecture
sources:
  - https://magpie.apache.org/docs/tools/tracker/readme
  - https://magpie.apache.org/docs/tools/change-request/readme.md
  - https://magpie.apache.org/docs/tools/pr-management/readme.md
  - app/controllers/ReviewApp.java:40-52 (60a5ac406)
  - app/controllers/PullRequestApp.java:378-400 (60a5ac406)
  - app/controllers/api/IssueApi.java:187-205 (60a5ac406)
updated: 2026-10-28
---

# Apache Magpie 연동 분석 (2026-10-28)

Apache Magpie(ASF의 AI 기반 저장소 유지보수 도구)를 Yona에 붙이기 위해 Magpie가 요구하는 계약과 Yona가 이미 제공하는 것을 대조한다. 결정은 [D-29](../decisions.md)에서 한다. 이 문서는 근거 자료이다.

출처는 아래 Magpie 문서이다. 문서 본문은 읽은 시점(2026-10-28)의 것이며 Magpie 쪽이 바뀔 수 있다.

- 도구 목록: https://magpie.apache.org/docs/tools
- 이슈 트래커 계약: https://magpie.apache.org/docs/tools/tracker/readme
- 변경 요청(PR) 계약: https://magpie.apache.org/docs/tools/change-request/readme.md
- PR 관리 도구: https://magpie.apache.org/docs/tools/pr-management/readme.md

## 1. Magpie가 요구하는 것

Magpie는 코드 호스트 종류를 계약(contract)으로 추상화하고, 호스트마다 어댑터(`tools/<이름>/`)를 둔다. 프로젝트 설정(`project.md`)의 `change_request.backend`와 `tracker_type`으로 어댑터를 고른다. 인증은 계약이 정하지 않고 어댑터가 정한다.

### 1.1 `contract:tracker` (이슈, 읽기 전용)

| 동작 | 입력 | 반환 |
|---|---|---|
| `list_filed` | 사람, 기간 | `{id, permalink, created, closed, labels[]}` 목록 |
| `list_triaged` | 사람, 기간 | `{id, permalink, first_activity, labels[], pushback_candidate}` 목록 |
| `list_commented` | 사람, 기간(선택: 문구 목록, 메인테이너 명단) | 스레드 참조 목록(링크만, 첫 댓글 날짜 기준) |
| `list_created` | 기간 | `{id, permalink, kind, created, author, author_first_time}` 목록 |
| `first_reply` | 이슈 id | `{author, created, url, body}` 또는 `null` |

### 1.2 `contract:change-request` (PR, 읽기와 쓰기)

핵심 동작 일곱 가지:

| 동작 | 구분 | 입력 | 반환 |
|---|---|---|---|
| `list_open(filter)` | 읽기 | 작성자, 라벨, 나이, 내가 리뷰할 것, 오래된 것 | `proposal_summary` 목록(`id`, `title`, `author`, `created`, `updated`, `state`, `labels[]`, `permalink`), 최근 갱신 순 |
| `get(id)` | 읽기 | PR id | 요약과 `diff`, `base`, `commits[]`, `mergeable`, `checks` |
| `get_discussion(id)` | 읽기 | PR id | `comments[]`(`author`, `date`, `body`, `kind`: comment/review/approval/change-request), `participants[]`, `unresolved_count` |
| `status(id)` | 읽기 | PR id | `{state, checks(passing/failing/pending/none), mergeable(clean/conflicting/unknown)}` |
| `post_review(id, verdict, body)` | **쓰기** | verdict: comment/approve/request-changes, 본문 | 성공 표시, 실패는 예외 |
| `land(id, strategy)` | **쓰기** | merge/squash/rebase | 병합된 커밋 |
| `reject(id, reason)` | **쓰기** | 닫는 이유 | 성공 표시 |

기여자 활동 동작(모두 읽기 전용, 구현하지 않으면 `NotApplicable`): `list_authored`, `list_reviews_given`, `list_authored_commits`.

수명 상태: `open`, `under-review`, `changes-requested`, `approved`, `landed`, `rejected`, `superseded`, `unknown`. 어댑터가 호스트의 상태를 이 값으로 옮긴다. `checks`와 `mergeable`을 모르는 호스트는 `none`과 `unknown`을 돌려주면 되고, 그러면 도구는 사람 판단으로 넘어간다.

### 1.3 PR 관리 도구가 호스트에서 읽는 것

GitHub GraphQL과 REST 위에 만든 도구라서 일부는 GitHub 전용이다(워크플로 실행 승인 대기, 팀 명단, 체크 실행 목록). 어댑터가 계약을 채우면 본문 도구는 호스트를 가리지 않는다는 것이 Magpie의 설계 의도이다.

## 2. Yona가 제공하는 것

### 2.1 이슈 쪽

| Magpie 동작 | Yona REST | 판정 |
|---|---|---|
| `first_reply` | `GET /-_-api/v1/owners/:owner/projects/:project/issues/:number` (댓글 포함, 토큰 인증, `app/controllers/api/IssueApi.java:187-205`) | 가능. 응답에서 첫 메인테이너 댓글을 어댑터가 고른다 |
| `list_created`, `list_filed` | 기간·작성자 조건의 이슈 목록 REST 없음 | **빠짐** |
| `list_triaged`, `list_commented` | 이슈 목록과 이슈별 댓글 목록 REST 없음 | **빠짐** |

### 2.2 PR 쪽

Yona의 PR 경로는 거의 모두 화면용(HTML)이고 `/-_-api/v1` 아래에 PR 경로가 없다. 인증도 이슈 API와 달리 세션 쿠키 기준이다(토큰 인증은 `/-_-api` 컨트롤러에서만 확인했다).

| Magpie 동작 | Yona 현황 | 판정 |
|---|---|---|
| `list_open` | `GET /:owner/:project/pullRequests` (HTML 목록) | **빠짐**(JSON 없음) |
| `get` | `GET .../pullRequest/:id` (HTML), `.../changes`, `.../changes/:commitId` (HTML diff), `.../newPullRequest/mergeResult` | **빠짐**(diff 원문 JSON 없음). 병합 가능 여부는 `.../state`가 XHR 요청에 JSON(`app/controllers/PullRequestApp.java:390-399`; `isOpen`, `isClosed`, `isMerged`, `isMerging`, `isConflict`)을 준다 |
| `status` | `.../state`의 JSON | 일부 가능. CI 상태 개념이 Yona에 없어 `checks: none`으로 답한다 |
| `get_discussion` | PR 댓글(`POST .../comments`), 코드 리뷰 스레드(`GET /:user/:project/reviews`, 스레드 열기·닫기 `POST /threads/:id/open|close`) | **빠짐**(읽기 JSON 없음) |
| `post_review` | `POST .../pullRequest/:number/review`는 "리뷰어로 등록"만 한다(`addReviewer`, `app/controllers/ReviewApp.java:46`). 판정(approve/request-changes)이나 본문이 없다. 댓글은 `POST .../comments`(멀티파트 폼) | **개념이 다름**. approve에 해당하는 것이 리뷰어 등록이고, request-changes에 해당하는 것이 없다 |
| `land` | `POST .../pullRequest/:id/accept` | 있음(병합 전략 선택은 없음). 화면 폼 요청 |
| `reject` | `POST .../pullRequest/:id/close` | 있음(이유 입력 없음) |
| `list_reviews_given` 등 활동 동작 | 없음 | `NotApplicable`로 둘 수 있음 |

### 2.3 인증

- 이슈 API: `POST /-_-api/v1/users/token`으로 받은 토큰을 `Authorization: token <값>`으로 보낸다.
- PR 경로: 토큰으로 호출되는지 확인하지 못했다. 되지 않는다면 어댑터가 로그인 세션을 만들어야 해서 현실적이지 않다.
- D-28의 "개인용 API 토큰(PAT)"이 들어오면 인증 문제는 거기서 풀린다.

## 3. 요약

| 영역 | 지금 가능 | 새로 필요 |
|---|---|---|
| 이슈 | `first_reply` | 기간별 이슈 목록 JSON, 이슈 댓글 목록 JSON |
| PR 읽기 | 병합 상태(`state`) | PR 목록, PR 상세와 diff, 토론(댓글과 리뷰 스레드) JSON |
| PR 쓰기 | 닫기, 병합(폼 요청) | 판정이 있는 리뷰 등록, 이유가 있는 닫기, 전략 선택 병합을 REST로 |
| 인증 | 이슈 API 토큰 | PR 경로에서도 토큰 인증(또는 PAT) |

핵심은 두 가지이다. Yona에는 "읽을 수 있는 JSON API"가 PR 쪽에 없고, Yona의 "리뷰"는 승인 표시(리뷰어 등록)라서 Magpie의 `post_review`(판정과 본문)와 개념이 다르다. 후자는 v1.6 동작을 바꾸지 않고 새 API로만 풀 수 있다.

## 4. 로드맵(D-28)과의 겹침 (2026-10-28 보강)

`roadmap.md`의 추가 개발 항목이 위 빈칸 대부분을 이미 포함한다. 따라서 Magpie 전용 API를 새로 만들기보다 아래 항목을 먼저 진행한 뒤 남는 부분만 더하는 편이 중복이 적다.

| 필요한 것 | 로드맵 항목 |
|---|---|
| PR 경로의 토큰 인증 | X-5 개인용 API 토큰(PAT) |
| `post_review`(approve, request-changes, 본문) | X-12 PR 리뷰 판정 |
| PR 목록, 상세와 diff, 리뷰, 타임라인, 병합, 닫기, 댓글 REST | X-15 PR REST API |
| 이슈 조회와 댓글 REST | X-19 일반 REST API |
| 위를 채운 뒤에도 남는 것 | 기간·작성자 조건의 이슈 목록, 이슈·PR 활동 조회(`list_filed`, `list_triaged`, `list_commented`, `list_created`, `list_reviews_given`) |

연동 자체는 X-23으로 올려 두었고 **보류(쌓아 둠)** 상태이다.

## 5. 구현 결과 (2026-11-03)

이 문서가 정리한 빈칸은 X-23으로 채웠다. 결과와 결정은 [magpie-integration-plan.md](magpie-integration-plan.md)와 D-29, D-83에 있다. 이슈 쪽의 `list_filed`, `list_triaged`, `list_commented`, `list_created`, `first_reply`와 PR 쪽의 `list_open`, `get`, `get_discussion`, `status`, `post_review`, `land`, `reject`, 활동 동작 `list_authored`, `list_reviews_given`이 `docs/magpie/tools-yona/`의 어댑터 문서로 채워졌다(`list_authored_commits`는 `NotApplicable`).

