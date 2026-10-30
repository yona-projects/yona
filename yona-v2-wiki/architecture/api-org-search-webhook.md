---
title: 조직, 웹훅, 검색, 포크 REST(X-30) 추적표
type: architecture
sources:
  - app/controllers/OrganizationApp.java (60a5ac406)
  - app/controllers/WebhookApp.java (60a5ac406)
  - app/controllers/SearchApp.java (60a5ac406)
  - app/controllers/PullRequestApp.java (60a5ac406)
  - https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps
  - https://docs.github.com/en/rest/orgs
  - https://docs.github.com/en/rest/repos/webhooks
updated: 2026-10-30
---

# 조직, 웹훅, 검색, 포크 REST (X-30) 추적표

결정은 [D-49](../decisions.md)이다. 이 표는 **빠지는 것이 없게** 엔드포인트마다 상태를 적는 추적표이고, 구현할 때마다 상태를 고친다. 경로 규칙은 X-29와 같다: 프로젝트에 묶인 것은 v1.6의 `/-_-api/v1/owners/{owner}/projects/{projectName}` 접두사 아래 GitHub 자원 경로를 쓰고, 프로젝트에 묶이지 않는 조직과 검색은 `/-_-api/v1/orgs/{org}`, `/-_-api/v1/search`처럼 GitHub 경로에서 `/repos` 같은 접두사만 없앤 형태를 쓴다. 모두 토큰 전용이며([api-auth-split.md](api-auth-split.md)), 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번).

상태: **완료**, **대기**.

## 이미 있는 것

| 메서드와 경로 | 설명 | 상태 |
|---|---|---|
| `GET /-_-api/v1/owners/{owner}/projects` | 소유자(사용자나 조직)의 프로젝트. 조직의 프로젝트 목록은 이것으로 충분하다 | 완료(X-19) |
| `GET /-_-api/v1/admin/users`, `PATCH /-_-api/v1/admin/users/{user}` | 사용자 상태 변경(사이트 관리자, `site_admin`). 로드맵의 "사용자 상태"는 이것이라 이번에 뺐다(A-267) | 완료(X-5) |

## 범위 대응

| 범위 | 허용하는 일 |
|---|---|
| `read:org` | 조직과 구성원 조회 |
| `write:org` | 구성원 더하기, 역할 변경, 빼기 |
| `admin:org` | 조직 만들기, 설정 변경, 삭제 (`write:org`와 `read:org`를 포함) |
| `read:repo_hook` | 웹훅 목록 |
| `write:repo_hook` | 웹훅 만들기 (삭제 제외) |
| `admin:repo_hook` | 웹훅 삭제 (`write:repo_hook`와 `read:repo_hook`를 포함) |
| `repo` | 웹훅 세 범위를 포함하고, 검색과 포크에는 `repo`나 `public_repo`를 쓴다 |

## a단계: 조직 (X-30a)

구현은 `ExternalOrganizationApiController`이다. 웹 화면과 같은 서비스(`OrganizationManagementService`, `OrganizationService`)와 판정을 쓴다. 역할 이름은 GitHub처럼 `admin`과 `member`이다.

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET /-_-api/v1/user/organizations` | 내가 속한 조직(`name`, `description`, `createdAt`, `projectCount`, `role`) | `read:org` | **완료**(2026-10-30) |
| `GET /-_-api/v1/orgs/{org}` | 조직 한 건. 누구나 읽고 `role`은 구성원일 때만 있다 | `read:org` | **완료**(2026-10-30) |
| `GET /-_-api/v1/orgs/{org}/members` | 구성원과 역할. 조직 관리자만 본다(웹과 같다) | `read:org` | **완료**(2026-10-30) |
| `POST /-_-api/v1/orgs/{org}/members` | 구성원 더하기(`loginId`, `role`은 `member` 기본이나 `admin`). 이미 구성원 409, 없는 사용자 404, 게스트 403 | `write:org` | **완료**(2026-10-30) |
| `PATCH /-_-api/v1/orgs/{org}/members/{loginId}` | 역할 바꾸기(`role`). 마지막 관리자를 내리면 409 | `write:org` | **완료**(2026-10-30) |
| `DELETE /-_-api/v1/orgs/{org}/members/{loginId}` | 구성원 빼기. 자신이면 나가기. 마지막 관리자는 409 | `write:org` | **완료**(2026-10-30) |
| `POST /-_-api/v1/orgs` | 조직 만들기(`name`, `description`). 만든 사람이 관리자. 형식과 예약어 400, 중복 409, 게스트 403 | `admin:org` | **완료**(2026-10-30) |
| `PATCH /-_-api/v1/orgs/{org}` | 이름과 설명 바꾸기(보낸 항목만). 이름이 바뀌면 프로젝트 소유자와 저장소도 옮겨진다 | `admin:org` | **완료**(2026-10-30) |
| `DELETE /-_-api/v1/orgs/{org}` | 조직 지우기. 프로젝트가 남아 있으면 409, 관리자가 아니면 403 | `admin:org` | **완료**(2026-10-30) |

## b단계: 웹훅 (X-30b)

웹 화면(`/{o}/{p}/webhooks`)에 있는 목록, 만들기, 삭제만 만든다. 수정은 v1.6에 없다.

| 메서드와 경로(`.../projects/{projectName}` 뒤) | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET hooks` | 웹훅 목록(비밀 값은 돌려주지 않는다) | `read:repo_hook` | 대기 |
| `POST hooks` | 웹훅 만들기(`payloadUrl`, `secret`, `webhookType`, `gitPush`) | `write:repo_hook` | 대기 |
| `DELETE hooks/{hookId}` | 웹훅 지우기 | `admin:repo_hook` | 대기 |

## c단계: 검색 (X-30c)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET /-_-api/v1/search` | 사이트 전체 검색(`keyword`, `searchType`, `pageNum`) | `repo`나 `public_repo` | 대기 |
| `GET /-_-api/v1/orgs/{org}/search` | 조직 안 검색 | `repo`나 `public_repo` | 대기 |
| `GET .../projects/{projectName}/search` | 프로젝트 안 검색 | `repo`나 `public_repo` | 대기 |

## d단계: 포크 (X-30d)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `POST .../projects/{projectName}/forks` | 포크 만들기(`owner`는 사용자나 조직, `name`, `projectScope`). 이름 중복이면 거절 | `repo` | 대기 |

## 다루지 않는 것

- 사용자 상태(이미 있다, A-267), 조직 로고 올리기(웹 화면 전용), OpenAPI 문서(X-31).
