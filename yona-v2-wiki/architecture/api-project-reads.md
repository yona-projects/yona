---
title: 일반 REST API 1차 (프로젝트 단위 조회)
type: architecture
sources:
  - architecture/api-pull-requests.md
  - architecture/api-token.md
updated: 2026-10-29
---

# 일반 REST API 1차: 프로젝트 단위 조회 (X-19, D-40)

v1.6에 없는 추가 항목이다. 외부 도구가 프로젝트 정보, 구성원, 라벨, 마일스톤, 이슈 목록을 읽는 API이다. 결정은 [D-40](../decisions.md)이고 규칙은 [풀 리퀘스트 REST](api-pull-requests.md)와 같다.

## 규칙

- **경로**: `/-_-api/v1/owners/{owner}/projects/{projectName}` 아래와 `/-_-api/v1/user/projects`, `/-_-api/v1/owners/{owner}/projects`.
- **인증과 범위**: 개인용 API 토큰뿐이다. 프로젝트 단위 요청은 `repo`(공개 프로젝트는 `public_repo`)를, 프로젝트 목록은 `repo` 또는 `public_repo`를 요구한다. 목록은 `public_repo`만 가진 토큰에게 공개 프로젝트만 보여 준다. 그 위에 화면과 같은 읽기 권한을 확인하고, 읽을 수 없거나 없는 프로젝트는 403이다.
- **응답 모양**: Yona 자체 JSON(카멜 규칙). 사용자는 `{loginId, name}`, 시각은 ISO, 없으면 `null`이다. 오류는 `{"message": 문장}`이다.

## 요청

| 요청 | 설명 |
|---|---|
| `GET /owners/{owner}/projects/{projectName}` | 프로젝트 정보. `{id, owner, name, overview, vcs, scope, createdAt, url, originalProject, organization, menus{code, issue, pullRequest, review, milestone, board}}` |
| `GET /user/projects` | 내가 구성원인 프로젝트. `{projects: [...]}` |
| `GET /owners/{owner}/projects` | 소유자(사용자나 조직)의 프로젝트 중 내가 읽을 수 있는 것. 같은 모양 |
| `GET .../members` | 프로젝트 구성원과 역할(`manager`, `member`). 사이트 관리자 역할은 뺀다. `{members: [{loginId, name, role}]}` |
| `GET .../assignableusers?query=` | 담당자나 리뷰어로 정할 수 있는 사람(프로젝트 구성원과 소속 조직 구성원, 이슈 담당자 후보 규칙). `query`는 이름이나 로그인 ID의 일부이고 생략하면 전체(최대 100명). 풀 리퀘스트를 **만들기 전에** 담당자를 정할 때 쓴다. `{users: [{loginId, name}]}` |
| `GET .../labels` | 프로젝트의 라벨. `{labels: [{id, labelName, labelColor, category, categoryId, isExclusive}]}`. 풀 리퀘스트 REST가 라벨을 번호로 받으므로 여기서 번호를 알아낸다 |
| `GET .../milestones?state=open\|closed\|all` | 마일스톤(기본 열린 것, 마감일 순). `{milestones: [{id, title, state, description, dueDate}]}` |
| `GET .../issues` | 이슈 목록과 검색. 아래. Magpie 연동(X-23)으로 `createdSince`, `createdUntil`과 항목의 `closedAt`, `authorFirstTime`이 더해졌다 |
| `GET .../activity` | 사람과 기간별 댓글 활동(Magpie 연동, X-23). 아래 "Magpie 연동" 절. 범위 `read:repo` 이상 |

## 이슈 목록

조건(모두 선택): `state`(`open` 기본, `closed`, `all`), `q`(제목과 본문 검색어), `assignee`와 `author`(로그인 ID, 없는 사용자는 400), `labelIds`(쉼표나 반복, **모두 가진** 이슈), `milestoneId`, `sort`(`createdDate` 기본, `updatedDate`, `number`, `title`, `numOfComments`, `dueDate`), `order`(`desc` 기본, `asc`), `page`(1부터), `pageSize`(1~100, 기본 15).

응답: `{items: [...], page, pageSize, totalPages, totalCount}`. 항목은 `{number, id, title, state, author, assignees, labels[{id, labelName, labelColor, category}], milestone{id, title}, createdAt, updatedAt, dueDate, numOfComments, url}`이다. 이슈 화면의 검색과 같은 서비스(`IssueSearchService`)를 쓰므로 초안과 하위 이슈 처리가 화면과 같다.

이슈의 본문과 댓글(한 줄 댓글, 첨부 포함)은 v1.6의 `GET .../issues/{number}`가 이미 담고 있어 이슈 댓글 목록은 따로 만들지 않았다.

## 판단

- 코드 접근을 구성원으로 제한한 프로젝트(`isCodeAccessibleMemberOnly`)의 비구성원 차단은 코드와 풀 리퀘스트 요청에만 걸고 이 조회들에는 걸지 않는다(화면의 이슈와 라벨 화면도 같다).
- `sort`에는 위 값만 받는다. 정렬 필드를 문자열로 받아 질의에 넣는 일을 막기 위해서이다(400).

## Magpie 연동 (X-23, 2026-11-03)

Magpie의 이슈 계약(`contract:tracker`)이 요구하는 기간과 사람 조건을 채운다. 기존 필드와 경로는 바꾸지 않고 더하기만 했다. 계획은 [magpie-integration-plan.md](magpie-integration-plan.md)이다.

**이슈 목록의 더해진 것** (`GET $P/issues`)
- 질의 `createdSince`(이상), `createdUntil`(미만). 값은 `2026-02-01`(UTC 0시)이나 ISO-8601(`2026-02-01T00:00:00Z`, `+09:00` 가능, 시간대가 없으면 UTC)이다. 읽을 수 없거나 `createdSince`가 `createdUntil`보다 늦으면 400이다. 날짜 표기는 서버 시간대가 아니라 UTC로 해석한다.
- 항목에 `closedAt`: 닫은 시각(UTC, 초 단위, `2026-01-20T09:00:00Z`). 상태 변경 이벤트가 있으면 마지막 닫힘 시각이고, 없으면(옮겨 온 데이터) 고친 시각이다. 열린 이슈는 `null`이다.
- 항목에 `authorFirstTime`: 이 작성자가 **이 프로젝트에서 처음 낸 이슈나 풀 리퀘스트**이면 `true`이다. 작성자를 모르면 `null`이다. 기존 `createdAt`은 서버 시간대 표기를 유지하므로 한 항목 안에서 표기가 다를 수 있다(둘 다 올바른 ISO-8601이다).

**활동 조회** (`GET $P/activity`)
- 질의: `actor`(로그인 ID, 필수), `since`(필수), `until`(생략하면 지금), `kind`(`commented` 기본 또는 `triaged`), `phrases`와 `maintainers`(쉼표로 나눔, 선택).
- 응답 `{"items": [{"number", "isPullRequest", "url", "firstActivity", "labels", "pushbackCandidate"}]}`. 항목은 그 사람이 창 안에서 **처음 댓글을 단 시각**의 순서이다. 창은 `since` 이상 `until` 미만이다.
- `commented`는 이슈와 풀 리퀘스트(리뷰 스레드 댓글) 모두이고 댓글만 센다(Magpie의 GitHub 어댑터가 `commenter:`로 찾는 것과 같다). `triaged`는 **다른 사람이 연 이슈**만(풀 리퀘스트와 자기 이슈는 뺀다) 대상이고, 댓글에 더해 **상태, 담당자, 라벨, 마일스톤을 바꾼 것**도 센다(이슈 이벤트, Magpie 계약의 분류 정의). 날짜는 둘 중 이른 쪽이다. Yona에는 구성 요소와 우선순위가 없다.
- `phrases`와 `maintainers`를 함께 주면 이슈마다 **구성원이 처음 남긴 문구 댓글**(문구는 대소문자 무시, 포함 검사)의 주소를 `pushbackCandidate`에 `…/issue/{n}#comment-{id}`로 담는다. 풀 리퀘스트 항목은 `null`이다. **댓글 본문은 응답에 없다**(Magpie 계약의 "본문은 어댑터 안에 둔다").
- 400: `actor`나 `since`가 없음, 없는 사용자, 읽을 수 없는 날짜, `since`가 `until`보다 늦음, 알 수 없는 `kind`. 프로젝트 읽기 권한과 토큰 범위를 따른다(`repo`, `read:repo`, 공개 프로젝트는 `public_repo`).
- **같은 시각에 낸 것의 처리**: 시각이 초 단위라 한 작성자의 이슈나 풀 리퀘스트가 같은 시각이면 이슈가 풀 리퀘스트보다, 번호가 작은 쪽이 먼저이다(`authorFirstTime`은 그 하나만 `true`이다).
- **REST로 이슈를 가져올 때의 시각(D-83, R-131)**: `createdAt`을 생략하거나 읽을 수 없으면 지금 시각으로 채우고 `updatedAt`을 생략하면 만든 시각으로 채운다. 시각은 v1.6 형식(`2026-11-03 PM 02:02:11 +0900`) 또는 ISO-8601이다. **이 수정 전에 시각 없이 가져온 이슈는 시각이 비어 기간 조건에 걸리지 않는다.**

**사람이 쓴 커밋** (`GET $P/activity/commits?author=&limit=`, Magpie `list_authored_commits`): `{"items": [{"sha", "authorName", "authorEmail", "message", "committedAt"}], "capped": false}`. 사람의 **대표 이메일과 인증한 추가 이메일**(대소문자 무시)로 기본 브랜치의 이력에서 찾고 최근 순으로 `limit`개(1 이상, 100을 넘으면 100, 기본 30)를 돌려준다. 최근 3000개까지만 훑고, 그 안에서 `limit`개를 다 채우지 못하고 이력이 더 있으면 `capped`가 `true`이다. 이메일은 찾는 열쇠이고 `authorEmail`은 커밋에 적힌 값이다. Git 프로젝트만이고 Subversion은 빈 목록이다. 코드 접근 권한과 `read:repo` 이상이 필요하다.

