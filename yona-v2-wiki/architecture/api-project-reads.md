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
| `GET .../issues` | 이슈 목록과 검색. 아래 |

## 이슈 목록

조건(모두 선택): `state`(`open` 기본, `closed`, `all`), `q`(제목과 본문 검색어), `assignee`와 `author`(로그인 ID, 없는 사용자는 400), `labelIds`(쉼표나 반복, **모두 가진** 이슈), `milestoneId`, `sort`(`createdDate` 기본, `updatedDate`, `number`, `title`, `numOfComments`, `dueDate`), `order`(`desc` 기본, `asc`), `page`(1부터), `pageSize`(1~100, 기본 15).

응답: `{items: [...], page, pageSize, totalPages, totalCount}`. 항목은 `{number, id, title, state, author, assignees, labels[{id, labelName, labelColor, category}], milestone{id, title}, createdAt, updatedAt, dueDate, numOfComments, url}`이다. 이슈 화면의 검색과 같은 서비스(`IssueSearchService`)를 쓰므로 초안과 하위 이슈 처리가 화면과 같다.

이슈의 본문과 댓글(한 줄 댓글, 첨부 포함)은 v1.6의 `GET .../issues/{number}`가 이미 담고 있어 이슈 댓글 목록은 따로 만들지 않았다.

## 판단

- 코드 접근을 구성원으로 제한한 프로젝트(`isCodeAccessibleMemberOnly`)의 비구성원 차단은 코드와 풀 리퀘스트 요청에만 걸고 이 조회들에는 걸지 않는다(화면의 이슈와 라벨 화면도 같다).
- `sort`에는 위 값만 받는다. 정렬 필드를 문자열로 받아 질의에 넣는 일을 막기 위해서이다(400).
