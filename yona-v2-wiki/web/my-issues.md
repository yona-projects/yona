---
title: 내 이슈
type: web
sources: [app/controllers/IssueApp.java:68-111, 180-188, 201-247, app/models/support/SearchCondition.java:229-320, app/views/issue/my_list.scala.html, my_partial_search.scala.html, my_partial_list.scala.html, my_partial_list_quicksearch.scala.html, app/models/Mention.java:70-100]
updated: 2026-10-25
---

# 내 이슈 (`GET /user/issues`)

코드: `web/issue/MyIssuesController`, `domain/issue/IssueSearchService.searchAll`·`countAll`·`countMentionedOpenIssues`, `web/user/UserPageAssembler.issueRows`. 템플릿은 `issue/my-list`, `my-partial-search`, `my-partial-list`.

- 로그인이 필요하다(`@AnonymousCheck(requiresLogin, displaysFlashMessage)`).
- 쿼리: `pageNum`(1부터), `state`(기본 `open`), `orderBy`(비었으면 `updatedDate`), `orderDir`, `filter`, `authorId`, `assigneeId`, `commenterId`, `mentionId`, `sharerId`, `favoriteId`, `itemsPerPage`(기본 15, 최대 45), `format`, `exceptId`.
- 조건(`authorId`, `assigneeId`, `commenterId`, `mentionId`, `sharerId`, `favoriteId`)이 하나도 없으면 `assigneeId`를 현재 사용자로 채운다(담당한 이슈).
- 응답 형식은 프로젝트 이슈 목록과 같다. `format=html`이면 화면(`my-list`), `X-Requested-With: XMLHttpRequest`이면 JSON(`X-PJAX: true`이면 조각 `my-partial-search`, `Cache-Control: no-cache, no-store`)이다. JSON에는 `link`가 없다. 엑셀은 없다.

## 검색 규칙 (프로젝트 목록과 다른 점)

- 프로젝트를 가리지 않고 **읽기 권한도 따지지 않는다**(R-49).
- 초안은 뺀다. 하위 이슈(부모가 있는 이슈)는 뺀다는 규칙이 **없다**. 라벨, 마일스톤 조건은 쓰지 않는다.
- 작성자, 담당자, 댓글, 멘션, 공유, 즐겨찾기 조건은 값에 관계없이 **현재 사용자** 기준이다. 담당자 값이 -1(익명)이면 담당자가 없는 이슈이다.
- 멘션은 본문에서 멘션된 이슈(`ISSUE_POST`)와 멘션된 댓글(`ISSUE_COMMENT`)이 달린 이슈이다. 없으면 아무것도 찾지 못한다.
- 검색어는 제목, 본문, 댓글에서 찾는다. 정렬은 가중치 내림차순, 초안 우선, 그다음 `orderBy`이다.

## 화면

- 위쪽 탭은 알림, 내 이슈, 내 파일이며 내 이슈가 활성이다.
- 왼쪽 빠른 검색 여섯 항목: 담당, 작성, 댓글, 멘션, 공유, 즐겨찾기. 각 항목의 `data-*-id`는 현재 사용자 번호이고 활성 표시는 조건 값이 내 번호일 때만 켜진다. 멘션, 공유, 즐겨찾기에는 검색어가 없을 때 열린 이슈 수가 `(N)`으로 붙는다.
- 오른쪽: 열림, 닫힘 탭과 개수(현재 조건에서 상태만 바꿔 센다), 칼럼 보기와 하위 이슈 보기 체크박스, 이슈가 둘 이상이면 정렬 링크(마감, 갱신, 등록, 댓글), 목록, 쪽 번호. 이슈가 없으면 `issue.is.empty`이다.
- 줄: 프로젝트 이름과 번호, 가중치 화살표, 제목, 댓글과 추천 수, 하위 이슈 진행과 부모 링크, 라벨, 접힌 하위 이슈, 작성자(작성자 탭에서는 비움), 시각, 마일스톤, 마감일, 담당자 아바타(담당 탭이 아니고 담당자가 있을 때).
- 기본 로그인 페이지 설정 버튼은 위쪽 탭 줄에 있고 `POST /-_-api/v1/user/defultLoginPage`를 부른다.
