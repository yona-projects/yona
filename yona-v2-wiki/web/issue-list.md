---
title: 이슈 목록과 검색
type: web
sources: [app/controllers/IssueApp.java:113-247, app/models/support/SearchCondition.java, app/models/Issue.java:290-400, app/views/issue/list.scala.html, app/views/issue/partial_list_wrap.scala.html, app/views/issue/partial_list.scala.html, app/views/issue/partial_searchform.scala.html, app/views/issue/partial_massupdate.scala.html]
updated: 2026-10-23
---

# 이슈 목록과 검색

`GET /:user/:project/issues`이다. 구현은 `IssueListController`, `IssueSearchService`(조건과 질의), `IssueListService`(보조 조회), `IssueListAssembler`(한 줄 조립), `IssueFormOptions`(선택지), `IssueExcelExporter`이고 화면은 `issue/{list,partial-list-wrap,partial-list,partial-searchform,partial-list-quicksearch,partial-select-label,partial-massupdate}.html`이다. 테스트는 `IssueSearchServiceTest` 18건, `IssueListTest` 20건, `IssueExcelTest` 3건이다.

프로젝트를 읽을 수 있어야 한다(`READ`). 목록을 보면 로그인한 사용자의 최근 방문에 기록된다.

## 응답 형식

| 조건 | 응답 |
|---|---|
| `format=html`(기본) | 전체 화면 |
| `format=pjax` 또는 `X-Requested-With`와 `X-PJAX: true` | 목록 조각(`div[pjax-container].issue-list-wrap`), `Cache-Control: no-cache, no-store` |
| `X-Requested-With`만 | JSON. 이슈 번호(PK)를 키로 `{id, title, state, createdDate, link}`. `exceptId`와 같은 번호는 뺀다. `exceptId`가 숫자가 아니면 `400` |
| `format=xls` | 엑셀 파일 |

한 쪽은 15개이고 `itemsPerPage`로 45개까지 바꿀 수 있다. `pageNum`은 1부터 센다.

## 검색 조건 (`IssueSearchCondition`)

| 입력 | 뜻 |
|---|---|
| `state` | `open`(기본), `closed`만 거른다. 그 밖의 값은 모든 상태 |
| `orderBy`, `orderDir` | 기본 `createdDate`, 내림차순. 가중치(`weight`)가 큰 이슈가 항상 먼저이고 그 뒤에 기준. 마감일 정렬은 마감일이 없는 이슈를 항상 뒤에 둔다. 허용하지 않는 기준은 `createdDate`(A-40) |
| `filter` | 제목, 본문, 댓글에서 대소문자 없이 찾는다. `%`, `_`는 그대로 찾는다 |
| `authorId`, `assigneeId` | `-1`이면 없음. 담당자는 이 프로젝트에서 등록된 담당자 |
| `milestoneId` | `-1`이면 마일스톤 없음 |
| `labelIds` | 고른 라벨을 모두 가진 이슈. 존재하는 라벨이 하나도 없으면 결과가 없다 |
| `commentedCheck` | 댓글이 있는 이슈만 |
| `commenterId`, `sharerId`, `favoriteId` | 지정하면 **현재 사용자**가 댓글을 썼거나 공유받았거나 즐겨찾는 이슈(번호 자체는 보지 않는다). 로그인하지 않았으면 댓글은 결과 없음, 공유와 즐겨찾기는 조건을 쓰지 않는다 |
| `dueDate` | 그날까지(포함) 마감인 이슈 |

초안은 항상 뺀다. 작성자, 검색어, 라벨, 담당자 조건이 모두 없으면 하위 이슈(부모가 있는 이슈)도 뺀다. 숫자가 아니거나 날짜 형식이 틀린 입력은 `400`이다(A-41).

## 화면

- 문서 제목은 `프로젝트 - Issue - 소유자/프로젝트`.
- 왼쪽: 빠른 검색(전체, 로그인하면 내게 할당/내가 작성/내가 댓글 + 각 개수, 현재 조건과 맞으면 `active`), 상세 검색 폼(검색어, 작성자, 담당자, 마일스톤(있을 때), 마감일, 라벨).
- 오른쪽: 새 이슈 버튼, 열림/닫힘 탭(개수는 현재 조건을 유지한 채 상태만 바꾼 개수), 두 열 보기와 하위 이슈 보기 체크, 목록.
- 목록이 비면 `issue.is.empty`와 단축키 도움말만 보인다.
- 목록이 있으면: 프로젝트에 역할이 있는 사용자(`projectMember`)에게는 선택 상자와 일괄 변경 막대, 결과가 둘 이상이면 정렬 링크(마감일, 수정일, 날짜, 댓글), 첫 쪽이고 조건(담당자, 작성자 등)이 없고 닫힘 탭이 아니면 **내 초안**, 이슈 줄, 엑셀 링크(쪽 번호를 빼고 `format=xls`), 단축키 도움말, 쪽 나누기 영역(`data-total`).
- 이슈 줄: 선택 상자, 번호(초안은 `#Draft`), 가중치 화살표, 제목 앞 `[머리말]`은 링크(`title-prefix`)로 따로 보이고 본문은 머리말을 뺀 제목(머리말은 이스케이프한다, A-38), 작성자, 날짜, 하위 이슈 진행 막대(`닫힌/전체`)나 부모 링크, 마일스톤 태그(마일스톤 메뉴를 켠 프로젝트만), 댓글/추천/공유자 수, 라벨, 접어 둔 하위 이슈 목록, 담당자 아바타, 마감일(열림은 지났으면 `overdue`와 `issue.dueDate.overdue`, 아니면 `N days`/`Today`, 닫힘은 날짜).
- 다른 사람의 초안은 줄에서 뺀다.

## 엑셀 (`format=xls`)

v1.6과 같은 라이브러리(JExcelAPI `jxl` 2.6.12, A-39)로 만든다. 조건에 맞는 **모든** 이슈를 쪽 나누기 없이 담는다. 열: `No`, 상태, 제목, 담당자, 본문, 라벨(분류, 이름 순), 만든 날짜(`yyyy-MM-dd HH:mm`), 마감일, 마일스톤, 주소, 댓글, 댓글 작성자, 댓글 작성 일시(메시지 키가 없어 키 이름 그대로). 이슈의 댓글은 이슈 줄부터 아래로 한 줄씩 이어 쓴다. 파일 이름은 `프로젝트_issues_yyyyMMddHHmm.xls`, 형식은 `application/vnd.ms-excel`.

## 마일스톤 보기와의 관계

마일스톤 보기 화면의 이슈 영역은 같은 `partial-list`와 `partial-massupdate`를 쓴다([milestones.md](milestones.md)).
