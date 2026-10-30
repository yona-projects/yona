---
title: CLI를 위한 서버 REST 보강(X-37) 추적표
type: architecture
sources:
  - architecture/cli-api-gap.md
  - ../yona-cli
updated: 2026-10-30
---

# CLI를 위한 서버 REST 보강 (X-37) 추적표

결정은 [D-52](../decisions.md)이고 근거는 [cli-api-gap.md](cli-api-gap.md)이다. 경로 규칙은 X-29, X-30과 같다(`/-_-api/v1/owners/{owner}/projects/{projectName}`는 `$P`). 모두 토큰 전용이며 범위와 사용자 권한의 교집합이다. 단계마다 [OpenAPI 문서](openapi-docs.md)에 올려야 구조 시험이 통과한다.

상태: **완료**, **대기**, **결정 대기**.

## a단계: 프로젝트 만들기, 설정, 삭제 (X-37a)

구현은 `ExternalProjectManageApiController`이다. 웹의 `ProjectCreateService`, `ProjectSettingService`, `ProjectDeletionService`를 쓴다.

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `POST /user/projects` | 내 계정에 프로젝트 만들기(`name`, `description`, `scope`, `vcs`, `menus`) | `repo` | **완료**(2026-10-30) |
| `POST /orgs/{org}/projects` | 조직에 프로젝트 만들기(조직 관리자) | `repo` | **완료**(2026-10-30) |
| `PATCH $P` | 설정 바꾸기(이름, 설명, 공개 범위, 코드 접근 제한, 병합 방식, 기본 브랜치, 메뉴) | `repo`나 공개의 `public_repo` | **완료**(2026-10-30) |
| `DELETE $P` | 프로젝트 지우기(저장소 포함) | `repo` | **완료**(2026-10-30) |

## b단계: 프로젝트 구성원 (X-37b)

구현은 `ExternalProjectMemberApiController`이다. 웹의 `ProjectMemberService`를 쓴다. 역할 이름은 `manager`, `member`이다.

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `POST $P/members` | 구성원 더하기(`loginId`, `role`). 없는 사용자 404, 이미 구성원 409 | `repo`나 공개의 `public_repo` | **완료**(2026-10-30) |
| `PATCH $P/members/{loginId}` | 역할 바꾸기. 소유자를 내리면 400, 구성원이 아니면 404 | 같다 | **완료**(2026-10-30) |
| `DELETE $P/members/{loginId}` | 구성원 빼기. 본인이면 나가기. 소유자는 403 | 같다 | **완료**(2026-10-30) |

## c단계: Git 태그 (X-37c)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET $P/tags` | 태그 목록 | | 대기 |
| `POST $P/tags` | 태그 만들기 | | 대기 |
| `DELETE $P/tags/{name}` | 태그 지우기 | | 대기 |

## d단계: 이슈 옮기기 (X-37d)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `POST $P/issues/{n}/transfer` | 이슈를 다른 프로젝트로 옮긴다 | | 대기 |

## e단계: 사용자 현황 (X-37e)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET /user/status` | 담당 이슈, 담당 풀 리퀘스트, 리뷰 요청, 멘션을 한 번에 | | 대기 |
| `GET /user/issues`의 `commented`, `shared` 필터 | 내가 댓글 단 이슈, 나에게 공유된 이슈 | | 대기 |

## f단계: 풀 리퀘스트 검색 (X-37f)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET /search?searchType=pullrequest` | 풀 리퀘스트 검색(웹 검색은 그대로) | | 대기 |
| `GET $P/pullrequests`의 조건 | 작성자, 담당자, 라벨, 키워드 | | 대기 |

## g단계: 사이트 내보내기와 가져오기 (X-37g)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET /admin/export` | 사이트 전체 내보내기 | `site_admin` | 대기 |
| `POST /admin/import` | 사이트 전체 가져오기 | `site_admin` | 대기 |

## h단계: 리뷰어 자기 등록 (X-37h)

선생님 결정 대기. 선택지는 [D-52](../decisions.md)의 (가) 승인 판정 매핑, (나) 서버에 `POST/DELETE .../reviewers`, (다) 자기 리뷰 요청이다.

## i단계: 기기 인증 (X-37i)

| 구성 | 설명 | 상태 |
|---|---|---|
| 기기 코드 발급, 토큰 교환, 승인 화면 | GitHub의 Device Flow(RFC 8628) | 대기 |

## j단계: CLI (X-37j)

`../yona-cli`: 클라이언트를 `/-_-api/v1`에 맞춤, 새 명령, 기기 로그인, Git 자격 증명 도우미. 대기.
