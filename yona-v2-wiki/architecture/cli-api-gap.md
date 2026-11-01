---
title: yona CLI와 서버 REST의 대응표
type: architecture
sources:
  - ../yona-cli/internal/api/*.go
  - ../yona-cli/cmd/*.go
  - architecture/openapi-docs.md
updated: 2026-10-30
---

# yona CLI와 서버 REST의 대응표 (X-24)

[D-51](../decisions.md)에 따라 `../yona-cli`의 클라이언트(`internal/api`)를 이 서버의 `/-_-api/v1` 계약에 맞춘다. 이 문서는 CLI의 명령마다 서버에 대응하는 REST가 있는지 조사한 결과이다(2026-10-30, 서버 문서는 `/-_-api/docs`). 경로 접두사 `$P`는 `/-_-api/v1/owners/{owner}/projects/{projectName}`이다.

CLI는 이전 구현(`yuna`)의 `/api/v1/projects/{owner}/{name}/...`를 부르고, 요청 본문에 **번호(ID)**를 쓴다(`assigneeId`, `labelIds`, `milestoneId`, `fromProjectId`, `userId`). 이 서버는 **로그인 ID와 `{owner}/{name}`**을 쓰므로 번호를 쓰는 곳은 클라이언트가 조회로 바꿔 줘야 한다(CLI에는 이미 `ResolveLabelID`, `ResolveAssigneeID` 같은 도우미가 있다).

상태: ✅ 대응 있음(경로와 모양을 맞추면 된다), ⚠ 부분 대응(서버 REST는 있으나 필터, 권한, 의미가 다르다), ❌ 대응 없음.

## 이슈

| CLI 명령 | 서버 REST | 상태 | 메모 |
|---|---|---|---|
| `issue list` | `GET $P/issues` | ✅ | 조건 `state`, `assignee`, `author`는 같다. CLI의 `label`(이름)은 서버가 `labelIds`(번호)라 이름을 번호로 바꿔야 한다. `Limit`은 `pageSize` |
| `issue view` | `GET $P/issues/{n}` | ✅ | |
| `issue create` | `POST $P/issues` | ✅ | 서버는 `{"issues": [...]}` 배열 본문(v1.6 계약)이고 담당자는 이메일, 라벨은 이름과 분류, 마일스톤은 제목이다. `isDraft`는 서버 REST에 없다 |
| `issue edit`, `close`, `reopen` | `PATCH $P/issues/{n}` | ✅ | `title`, `body`, `state`, `milestone`(번호), `assignees`(로그인 ID), `labels`(번호) |
| `issue comment` | `POST $P/issues/{n}/comments` | ✅ | 본문 `{"comment": ...}` |
| `issue transfer` | 없음 | ❌ | 이슈를 다른 프로젝트로 옮기는 REST가 없다. 웹 화면에는 있다(이벤트 `ISSUE_MOVED`) |
| `issue status` | `GET /user/issues` | ⚠ | 서버 필터는 `assigned`, `created`, `mentioned`, `favorite`, `all`이다. CLI가 쓰는 `commented`, `shared`와 6개 섹션을 한 번에 주는 구조, 열림/닫힘 수 집계는 없다 |
| `status` | 없음 | ❌ | 담당 이슈, 담당 풀 리퀘스트, 리뷰 요청, 멘션, 저장소 활동을 한 번에 주는 REST가 없다. 조각은 있다(`/user/issues`, 풀 리퀘스트 목록) |

## 풀 리퀘스트

| CLI 명령 | 서버 REST | 상태 | 메모 |
|---|---|---|---|
| `pr list` | `GET $P/pullrequests` | ⚠ | `state`(open, closed)와 쪽 나누기만 있다. 작성자, 담당자, 라벨 같은 조건과 키워드 검색은 없다 |
| `pr view`, `checkout` | `GET $P/pullrequests/{n}` | ✅ | `checkout`은 응답의 `fromBranch`로 로컬 git을 쓴다 |
| `pr create` | `POST $P/pullrequests` | ✅ | CLI의 `fromProjectId`(번호)는 서버의 `fromProject: {owner, name}`으로 바꾼다. 브랜치는 `master`나 `refs/heads/master` 모두 된다 |
| `pr edit` | `PATCH $P/pullrequests/{n}` | ✅ | |
| `pr merge`, `close`, `reopen` | `POST $P/pullrequests/{n}/merge`, `close`, `reopen` | ✅ | 병합은 방식(`method`)과 제목, 메시지를 더 받는다 |
| `pr diff` | `GET $P/pullrequests/{n}/files` | ✅ | 응답이 파일별 `patch`(통합 diff)이다 |
| `pr comment` | `POST $P/pullrequests/{n}/comments` | ✅ | 줄 범위를 주지 않으면 변경 전체 스레드 댓글이다 |
| `pr review`(판정) | `POST $P/pullrequests/{n}/reviews` | ✅ | CLI의 `state`(`APPROVE`, `REQUEST_CHANGES`, `COMMENT`)를 서버의 `verdict`(`APPROVED`, `CHANGES_REQUESTED`, `COMMENTED`)로 바꾼다. 조회는 `GET .../reviews` |
| `pr reviewer add`, `remove`(본인을 리뷰어로 등록, 취소) | 없음 | ❌ | CLI는 "본인을 리뷰어로 자기 등록"하는 `POST .../reviewers`를 쓴다. 이 서버는 판정을 `reviews`로 남기고 **다른 사람에게** 리뷰를 요청(`POST .../requestedreviewers`)한다. 자기 등록 개념이 없다(D-35) |
| `pr assignee set`, `remove` | `POST $P/pullrequests/{n}/assignees` | ✅ | `userId` 대신 로그인 ID 배열(한 명까지), 빈 배열이면 해제 |
| `pr label add`, `remove` | `POST $P/pullrequests/{n}/labels` | ✅ | `attachingLabelIds`와 `detachingLabelIds` |

## 프로젝트

| CLI 명령 | 서버 REST | 상태 | 메모 |
|---|---|---|---|
| `project list <owner>` | `GET /owners/{owner}/projects` | ✅ | 응답이 `{"projects": [...]}` |
| `project view` | `GET $P` | ✅ | |
| `project create` | `POST /owners/{owner}/projects` | ⚠ | **사이트 관리자만** 쓸 수 있다(v1.6 계약). 일반 사용자가 CLI로 프로젝트를 만들 수 없다. 본문이 `projectName`, `projectScope`, `projectVcs`, `projectDescription` |
| `project fork` | `POST $P/forks` | ✅ | `owner`(사용자나 조직), `name`, `projectScope` |
| `project edit` | 없음 | ❌ | 이름, 설명, 공개 범위, 기본 브랜치, 메뉴 켜기와 끄기 등을 고치는 REST가 없다(웹 설정 화면만). CLI의 리뷰어 수 설정(`isUsingReviewerCount`, `defaultReviewerCount`)은 X-33에서 무력화되어 대응이 없다 |
| `project delete` | 없음 | ❌ | 프로젝트를 지우는 REST가 없다 |
| `tag list`, `create`, `delete` | 없음 | ❌ | Git 태그 REST가 없다(웹에는 태그 화면이 있다, X-18) |

## 라벨

| CLI 명령 | 서버 REST | 상태 | 메모 |
|---|---|---|---|
| `label list` | `GET $P/labels` | ✅ | |
| `label create` | `POST $P/labels` | ✅ | `{"labels": [{"labelName", "labelColor", "category"}]}` 배열 본문이고 `category`가 필수이다 |
| `label edit`, `delete` | `PATCH`, `DELETE $P/labels/{labelId}` | ✅ | |

## 조직

| CLI 명령 | 서버 REST | 상태 | 메모 |
|---|---|---|---|
| `org list` | `GET /user/organizations` | ⚠ | 서버는 **내가 속한** 조직만 준다. CLI는 사이트 전체 조직 목록(`filter`, `page`)을 기대한다. 웹의 `/orgs` 목록에 대응하는 REST가 없다. **결정(2026-10-30): 만들지 않고 내가 속한 조직만 보인다(A-283)** |
| `org view` | `GET /orgs/{org}` + `GET /owners/{org}/projects` | ✅ | CLI가 기대하는 `projects` 배열은 프로젝트 목록 REST로 채운다 |

## 검색

| CLI 명령 | 서버 REST | 상태 | 메모 |
|---|---|---|---|
| `search issues` | `GET /search?searchType=issue` | ✅ | `keyword`, `pageNum`. 응답 모양이 다르다 |
| `search projects` | `GET /search?searchType=project` | ✅ | |
| `search prs` | 없음 | ❌ | 검색 종류에 풀 리퀘스트가 없다(웹 검색에도 없다) |

## 위키

| CLI 명령 | 서버 REST | 상태 | 메모 |
|---|---|---|---|
| `wiki list`, `view`, `create`, `edit`, `delete`, `history`, `diff`, `compare` | `wiki/pages...`(X-17 W3) | ✅ | 서버에 위키 REST를 만들었고(2026-11-01, D-60) CLI를 그 계약에 맞췄다. 하위 경로(슬래시)는 지원하지 않는다 |

## 관리자

| CLI 명령 | 서버 REST | 상태 | 메모 |
|---|---|---|---|
| `admin webhook list`, `create`, `delete` | `GET`, `POST`, `DELETE $P/hooks` | ✅ | 본문 `payloadUrl`, `secret`, `webhookType`, `gitPush`. 비밀 값은 응답에 없다 |
| `admin permission list` | `GET $P/members` | ✅ | |
| `admin permission add`, `update-role`, `remove` | 없음 | ❌ | 프로젝트 구성원을 더하고 역할을 바꾸고 빼는 REST가 없다(조회만 있다). 조직 구성원은 있다(`/orgs/{org}/members`) |
| `admin backup export`, `import` | 없음 | ❌ | 사이트 내보내기와 가져오기(`/site/export`, `/site/import`)는 웹 세션 경로이고 토큰 REST가 없다. 프로젝트 단위 내보내기(`GET $P/exports`)는 있다 |
| (조직 구성원 관리, 사용자 만들기와 잠금) | `/orgs/{org}/members`, `/users`, `/admin/users` | 서버에 있고 CLI에 없음 | CLI에 명령을 더할 수 있다 |

## CLI 안에서 끝나는 명령(서버 REST 불필요)

`auth login`, `logout`, `status`(저장된 토큰만 다룬다), `config`, `alias`, `server list`, `server use`, `browse`, `api <path>`(원시 호출).

## 서버에는 있으나 CLI에 없는 기능

게시판(`posts`), 마일스톤 관리, 이슈의 라벨, 담당자, 공유자, 가중치, 지켜보기, 이슈 댓글 수정과 삭제, 코드 리뷰 스레드와 줄 댓글, 리뷰 해제, 임시 리뷰, 사용자 활동 통계, 프로젝트 내보내기, 첨부 올리기.

## 요약

- **그대로 맞출 수 있는 명령**: 이슈(조회, 만들기, 수정, 댓글), 풀 리퀘스트 대부분, 라벨, 프로젝트 조회와 포크, 웹훅, 검색(이슈, 프로젝트), 조직 조회.
- **모양이나 의미를 바꿔야 하는 명령**: `issue list`의 `label`, `issue status`, `pr list`의 조건, `project create`(사이트 관리자만), `org list`(내 조직만), `pr review`의 판정 이름, 번호를 쓰는 모든 요청.
- **서버에 대응이 없는 명령**: `issue transfer`, `status`, `project edit`, `project delete`, `tag *`, `pr reviewer add/remove`, `search prs`, `wiki *`(8개), `admin permission add/update-role/remove`, `admin backup export/import`.
