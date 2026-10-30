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

구현은 `ExternalTagApiController`이고 `TagService`에 만들기를 더했다. Git 프로젝트만 된다.

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET $P/tags` | 태그 목록(최근 순서) | `repo`나 공개의 `public_repo` | **완료**(2026-10-30) |
| `POST $P/tags` | 태그 만들기(`name`, `target`, `message`). `message`가 있으면 주석 태그. 코드를 고칠 수 있는 구성원 | 같다 | **완료**(2026-10-30) |
| `DELETE $P/tags/{name}` | 태그 지우기(슬래시 이름 가능). 프로젝트 관리자 | 같다 | **완료**(2026-10-30) |

## d단계: 이슈 옮기기 (X-37d)

`ExternalIssueExtrasApiController`에 더했다. 웹의 이슈 수정(`targetProjectId`)과 같은 `IssueService.update`를 쓴다.

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `POST $P/issues/{n}/transfer` | 이슈를 다른 프로젝트로 옮긴다(`targetOwner`, `targetProject`). 새 번호, 마일스톤 지움, 라벨 이동, 이동 이벤트 | `repo`나 공개의 `public_repo` | **완료**(2026-10-30) |

## e단계: 사용자 현황 (X-37e)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET /user/status` | 담당 이슈, 담당 풀 리퀘스트, 리뷰 요청, 멘션된 이슈(구역마다 열림과 닫힘 수와 열린 항목), 최근 알림 10건. 쿼리 `limit`(1~100, 기본 10) | `repo` | **완료**(2026-10-30) |
| `GET /user/issues?filter=commented`, `shared` | 내가 댓글 단 이슈, 나에게 공유된 이슈(기존 필터에 더함) | `repo` | **완료**(2026-10-30) |

구현은 `ExternalUserStatusApiController`(현황)와 `ExternalUserApiController`(필터)이다.

## f단계: 풀 리퀘스트 검색 (X-37f)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET /search/pullrequests` | 사이트 전체 풀 리퀘스트 검색(`keyword`, `state`, `pageNum`). 읽을 수 있는 프로젝트의 것만. 한 쪽 20개 | `repo` | **완료**(2026-10-30) |
| `GET $P/pullrequests`의 `q`, `author`, `assignee`, `reviewRequested`, `labelIds` | 프로젝트의 풀 리퀘스트 목록 조건(기존 목록에 더함) | 기존과 같다 | **완료**(2026-10-30) |

웹 통합 검색(`/search`)은 그대로이다(풀 리퀘스트 종류가 없다).

## g단계: 사이트 백업 (X-37g)

| 메서드와 경로 | 설명 | 범위 | 상태 |
|---|---|---|---|
| `GET /admin/export` | DB 전체를 `yobi-data-<yyyyMMddHHmm>.json`으로 내려준다 | `site_admin` + 사이트 관리자 | **완료**(2026-10-30) |
| `POST /admin/import` | `data` 파트(multipart)로 DB를 교체하고 `{"imported","failed"}`를 준다. 파트가 없거나 JSON이 아니면 400 | `site_admin` + 사이트 관리자 | **완료**(2026-10-30) |

구현은 `ExternalSiteBackupApiController`이고 웹 `SiteDataController`와 같은 `DataService`를 쓴다. 웹은 올리기 뒤 처음 화면으로 이동하지만 REST는 결과 JSON을 준다.

## h단계: 리뷰어 자기 등록 (X-37h)

선생님 결정 대기. 선택지는 [D-52](../decisions.md)의 (가) 승인 판정 매핑, (나) 서버에 `POST/DELETE .../reviewers`, (다) 자기 리뷰 요청이다.

## i단계: 기기 인증 (X-37i)

GitHub의 `gh auth login`과 같은 Device Flow(RFC 8628)이다. CLI는 토큰 없이 코드를 요청하고, 사용자가 웹에서 승인하면 개인용 API 토큰을 받는다.

| 구성 | 설명 | 상태 |
|---|---|---|
| `POST /oauth/device/code` | 기기 코드와 사용자 코드(`BCDF-GHJK`), 확인 주소, 유효 시간(900초), 간격(5초). 인증 없음 | **완료**(2026-10-30) |
| `POST /oauth/token` | `grant_type=urn:ietf:params:oauth:grant-type:device_code`와 `device_code`로 토큰 교환. 오류는 400의 `error`(`authorization_pending`, `slow_down`, `access_denied`, `expired_token`, `invalid_grant`, `unsupported_grant_type`, `invalid_request`). 인증 없음 | **완료**(2026-10-30) |
| `GET /users/device`, `POST /users/device/approve`, `POST /users/device/deny` | 로그인한 사용자가 코드를 입력하고 기기와 범위를 확인해 승인하거나 거절하는 웹 화면 | **완료**(2026-10-30) |
| 테이블 `device_authorization` | 기기 코드 해시, 사용자 코드, 기기 이름, 범위, 상태, 승인한 사용자, 만료, 간격, 마지막 교환 시각 | **완료**(2026-10-30) |

구현은 `domain/deviceauth`(`DeviceAuthorization`, `DeviceAuthorizationService`), `ExternalDeviceAuthApiController`, `DeviceAuthorizationController`와 `templates/user/device.html`이다. 토큰은 설정 화면과 같은 `ApiTokenService.create`로 만든다.

## j단계: CLI (X-37j)

`../yona-cli`: 클라이언트를 `/-_-api/v1`에 맞춤, 새 명령, 기기 로그인, Git 자격 증명 도우미. 대기.
