---
title: 조직 이슈 목록과 풀 리퀘스트 목록
type: web
sources: [app/controllers/IssueApp.java:50-70, app/controllers/OrganizationApp.java:53-79, app/models/support/SearchCondition.java:100-230, app/models/Issue.java:287-289, app/models/PullRequest.java:752-818, app/models/Organization.java(getVisibleProjects), app/views/organization/group_issue_list*.scala.html, app/views/organization/group_issue_search_partial.scala.html, app/views/organization/group_pullrequest_list*.scala.html, conf/routes:99-103]
updated: 2026-10-25
---

# 조직 이슈 목록과 풀 리퀘스트 목록

조직 메뉴(`organization/menu`)의 "Issue", "Pull Request" 탭이 여는 화면이다. 조직에 속한 프로젝트 가운데 현재 사용자가 볼 수 있는 프로젝트의 이슈, 풀 리퀘스트를 한곳에 모아 보여 준다. 조직 게시판 목록([organization.md](organization.md))과 같은 틀(`organizationLayout`, 조직 머리말, 조직 메뉴)을 쓴다. 2026-10-25 동등성 점검(9a)에서 메뉴 링크는 있는데 화면이 없는 것으로 드러나 이식한다.

## 요청

| 요청 | 입력 | 응답 |
|---|---|---|
| `GET /organizations/:name/issues` | `state`(기본 `open`), `orderBy`, `orderDir`, `filter`, `projectNames[]`, `authorId`, `assigneeId`, `mentionId`, `commentedCheck`, `dueDate`, `itemsPerPage`(상한 45), `pageNum`(기본 1) | 이슈 목록 화면. PJAX, JSON 구분은 없고 항상 전체 화면이다(`format` 매개변수는 받지만 쓰지 않는다) |
| `GET /organizations/:name/pullrequests` | `category`(`open`이 아니면 닫힘), `filter`, `contributorId`, `pageNum` | 풀 리퀘스트 목록 화면 |
| `GET /organizations/:name/closedPullrequests` | 위와 같다 | `category=closed`와 같다 |

- 클래스에 `@AnonymousCheck`가 있고 두 액션은 `requiresLogin=false, displaysFlashMessage=true`이다. 로그인하지 않아도 볼 수 있다.
- 조직이 없으면 `404`와 조직 변형 오류 화면(`error.notfound.organization`)이다.

## 보이는 프로젝트

`Organization.getVisibleProjects(사용자)`로 정한다(`OrganizationService.visibleProjects`와 같다). 이름순이며 조직 관리자나 사이트 관리자는 모든 프로젝트, 조직 구성원은 비공개가 아니거나 구성원인 프로젝트, 그 밖은 `HIDE_PROJECT_LISTING`이 꺼져 있을 때 공개이면서 게스트가 아니거나 구성원인 프로젝트이다.

## 이슈 목록

검색 조건은 프로젝트 이슈 목록, 내 이슈와 같은 `SearchCondition`이고 조건 만들기는 `asExpressionList(Organization)`이다.

- 프로젝트: `projectNames[]`가 있으면 그 이름(대소문자 구별 없이)의 프로젝트 가운데 **보이는 것**만, 없으면 보이는 모든 프로젝트이다. 이름이 맞는 프로젝트가 없으면 아무것도 찾지 못한다.
- 초안(`isDraft`)은 뺀다. 하위 이슈는 뺀다는 규칙이 **없다**(프로젝트 목록과 다르다). 라벨, 마일스톤 조건은 쓰지 않는다.
- 담당자, 작성자, 멘션, 공유, 즐겨찾기 조건은 값에 관계없이 **현재 사용자** 기준이다(내 이슈와 같다, R-49). `assigneeId`가 `-1`(익명 사용자)이면 담당자가 없는 이슈이다. 조건이 없으면 담당자 조건을 자동으로 걸지 않는다(내 이슈와 다르다).
- 검색어(`filter`)는 제목, 본문, 댓글 내용에서 대소문자 없이 찾는다. `commentedCheck`는 댓글이 1개 이상, `dueDate`는 그날 포함 마감 이전이다.
- 상태는 `open`, `closed`만 거르고 그 밖의 값은 모든 상태이다. 정렬은 가중치 내림차순, 초안 내림차순 뒤에 `orderBy` 방향이며 마감일 정렬은 마감일이 없는 이슈를 뒤로 보낸다. 기본 정렬은 `createdDate` 내림차순이다.
- 한 쪽은 `itemsPerPage`(기본 15, 상한 45)개이다. 쪽 번호는 `pageNum`이다.
- 열림, 닫힘 탭의 숫자는 같은 조건에 상태만 바꿔 센다(`Issue.countIssuesBy(organization, 조건)`).

### 화면

- 왼쪽: 빠른 검색(전체, 나에게 할당, 내가 작성, 내가 멘션됨. 로그인한 사용자만 뒤의 세 개), 프로젝트 선택기(`select2`, 보이는 프로젝트, `data-avatar-url`은 프로젝트 로고), 숨은 입력(`orderBy`, `orderDir`, `state`, `authorId`, `assigneeId`, `mentionId`), 검색어 입력.
- 오른쪽: 열림/닫힘 탭과 2단 보기 체크박스, 정렬 링크(마감일, 수정일, 날짜, 댓글. 목록이 2건 이상일 때), 이슈 목록, 쪽 이동(`#pagination`, `data-total`). 결과가 없으면 `issue.is.empty` 안내이다.
- 목록의 한 줄: 작성자 아바타(기본 아바타이면 gravatar 32px), 제목 링크, 작성자 이름(없으면 `issue.noAuthor`), 작성 시각(`agoOrDateString`), 마일스톤(프로젝트 마일스톤 메뉴가 켜져 있을 때), 댓글 수와 추천 수, 프로젝트 이름 링크, `#번호`, 라벨(분류 이름, 라벨 이름순. 링크는 `프로젝트 주소/issues?state=open&labelIds=ID`이다), 담당자 아바타, 마감일(지났으면 `overdue`, 열린 이슈는 남은 기간, 닫힌 이슈는 날짜).
- 스크립트: `jquery.pageslide.js`, `yona.twoColumnMode.js`, `$yobi.loadModule("issue.List")`, 프로젝트 선택이 바뀌면 `#search`를 제출, `select2`.

## 풀 리퀘스트 목록

- 조건: 보이는 프로젝트를 `to_project_id`와 `from_project_id`가 **모두** 만족해야 한다. 상태는 `open`이면 열림, 그 밖이면 닫힘과 병합이다. 보낸 사람(`contributorId`)과 검색어(제목, 본문, 병합 커밋, 리뷰 댓글, 현재 커밋 메시지와 해시)는 프로젝트 목록과 같다. 열림은 `number`, 닫힘은 `received` 내림차순이고 한 쪽은 15개이다.
- 화면: 왼쪽에 검색어 입력(모바일에서는 숨김), 열림/닫힘 탭(숫자는 각 분류의 개수, 탭을 누르면 검색 폼의 `action`을 바꿔 제출), 목록. 결과가 없으면 `pullRequest.is.empty`이다.
- 목록의 한 줄: 보낸 사람 아바타, `번호`, 제목 앞 머리말(`[ ]`)과 제목(충돌이면 `conflict` 클래스), 보낸 사람 이름, 작성 시각, 대상 프로젝트 이름 링크, 리뷰 진행(닫힌 스레드 수 / 전체, 진행 막대, 변경 화면 링크), 담당자 아바타, 상태(충돌이면 `pullRequest.state.conflict`).
- 스크립트: `yobi.Pagination.update($("#pagination"), 총 쪽 수)`, 제목 머리말을 누르면 검색어에 넣고 제출.

## 알려진 v1.6 동작(R-64)

1. 이슈 조건의 작성자, 담당자, 멘션은 값이 아니라 현재 사용자를 쓴다. 로그인하지 않으면 이 조건은 아무것도 걸지 않거나 익명 사용자 기준이다.
2. 풀 리퀘스트는 보내는 쪽 프로젝트도 보이는 목록에 있어야 해서 보이지 않는 프로젝트에서 온 요청은 빠진다.
3. 이슈 목록은 `format`, PJAX를 구분하지 않는다.
4. 이슈 목록은 `yona.showSubtask.js`를 불러오지 않는데 `yobi.issue.List.js`가 `_initShowSubtasks()`를 호출해 열 때마다 스크립트 오류가 난다. 마지막 호출이라 동작에는 영향이 없다(R-69).
