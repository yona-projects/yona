---
title: 프로젝트 조회, 소속 판정, 메뉴 개수 (ProjectService)
type: domain
sources: [app/models/Project.java:161-177, app/models/Project.java:220-228, app/models/Project.java:905-922, app/models/Issue.java:264-270, app/models/PullRequest.java:274-279, app/models/Posting.java:147-149, app/models/CommentThread.java:172-177, app/utils/HttpUtil.java:203-211]
updated: 2026-10-22
---

# ProjectService

`dev.yona.domain.project.ProjectService`는 v1.6 `Project`의 정적 메서드와 각 모델의 개수 조회를 한곳에 모은 서비스다. 프로젝트 화면과 접근 검사가 공통으로 쓴다. 엔티티 설명은 [schema-mapping.md](schema-mapping.md), v1.6 분석은 `~/yona-convert/yona-v1.6-wiki/domain/project.md`를 본다.

## 조회

| 메서드 | 규칙 | v1.6 |
|---|---|---|
| `findByOwnerAndProjectName(owner, name)` | 소유자와 이름을 URL 디코딩(`+`는 공백, `%XX`와 한글 지원)한 뒤 대소문자 구별 없이 일치하는 프로젝트. 없으면 `findByPreviousPlaceOf` | `Project.findByOwnerAndProjectName` (`ieq` 두 번) |
| `findByPreviousPlaceOf(owner, name)` | `previous_owner_login_id`와 `previous_name`이 일치하는 프로젝트 중 `previous_name_changed_time`이 가장 큰 것 | `Project.findByPreviousPlaceOf` |

현재 이름이 이전 이름보다 우선한다. 디코딩에 실패하면 원래 값을 쓴다(v1.6 `HttpUtil.decodeUrlString`과 같다).

v1.6은 조회 결과를 `CacheStore.projectMap`(`소유자/이름 -> 프로젝트 번호`)에 캐시했다. v2는 아직 캐시하지 않는다. v1.6 캐시는 이름이 바뀔 때 갱신해야 해서(`CacheStore.refreshProjectMap`) 실수 여지가 컸다. 성능 문제가 확인되면 도입한다.

## 소속 판정 `hasMember(project, user)`

`project_user`에 행이 있거나(역할 무관) 매니저이거나 사이트 관리자이면 `true`. 익명(`null`)은 `false`. 코드 열람 제한(`is_code_accessible_member_only`)과 프로젝트 메뉴가 이 판정을 쓴다.

## 프로젝트 메뉴의 개수

| 메서드 | 규칙 | v1.6 |
|---|---|---|
| `countIssues(project, state)` | 부모가 없는 이슈 중 해당 상태. `State.ALL`이면 초안(`DRAFT`)을 뺀 전체 | `Issue.countIssues` |
| `countPostings(project)` | 프로젝트의 게시글 수 | `Posting.countPostings` |
| `countOpenPullRequests(project)` | 이 프로젝트가 받는(to) 열린 풀 리퀘스트 수 | `PullRequest.countOpenedPullRequests` |
| `countOpenReviewThreads(project)` | 열린 리뷰 스레드 수. v1.6의 기본 검색 조건(`state=OPEN`)과 같다 | `CommentThread.countReviewsBy(projectId, null)` |

## 검증

`ProjectServiceTest` 14건: 대소문자, URL 인코딩, 한글, 이전 위치 폴백과 최신 선택, 현재 이름 우선, 소속 판정, 개수 규칙.
