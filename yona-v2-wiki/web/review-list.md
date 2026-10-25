---
title: 프로젝트 리뷰 목록
type: web
sources: [app/controllers/ReviewThreadApp.java:36-190, app/models/support/ReviewSearchCondition.java, app/models/CommentThread.java:153-160,172-177, app/views/reviewthread/list.scala.html, app/views/reviewthread/partial_list.scala.html, public/javascripts/service/yobi.review.List.js, conf/routes:248]
updated: 2026-10-25
---

# 프로젝트 리뷰 목록

프로젝트 메뉴의 "Review" 탭(`GET /:user/:project/reviews`)이 여는 화면이다. 프로젝트의 코드 리뷰 스레드(커밋, 풀 리퀘스트에 달린 리뷰)를 모아 보여 주고 엑셀로 내려받게 한다. 2026-10-25 동등성 점검(9a)에서 메뉴 링크와 `yobi.review.List.js`는 있는데 화면이 없는 것으로 드러나 이식한다.

## 요청

| 요청 | 입력 | 응답 |
|---|---|---|
| `GET /:user/:project/reviews` | `state`(기본 `OPEN`), `authorId`, `participantId`, `orderBy`(기본 `createdDate`), `orderDir`(기본 `desc`), `filter`, `pageNum`(기본 1), `format` | 리뷰 목록 화면. `format=xls`이면 엑셀 |

- 클래스는 `@AnonymousCheck`, 액션은 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`와 `@IsAllowed(READ)`이다. 로그인하지 않으면 로그인 화면으로 보내고, 프로젝트를 읽을 수 없으면 `403`이다.
- 프로젝트 메뉴의 리뷰 탭이 활성(`PROJECT_REVIEW`)이다. 문서 제목은 `프로젝트 이름 - Review`이다.

## 검색 조건 (`ReviewSearchCondition.asExpressionList(project)`)

- 프로젝트의 스레드만 찾는다.
- `authorId`가 있으면 스레드를 만든 사람이 그 번호인 것(**현재 사용자가 아니라 받은 값**을 쓴다).
- `participantId`가 있으면 그 번호의 사용자가 쓴 리뷰 댓글이 달린 스레드.
- 상태는 `state`를 대문자로 바꾼 값(`OPEN`, `CLOSED`)이다. 모르는 값은 v1.6에서 `IllegalArgumentException`(500)이다.
- 정렬은 `orderBy 방향`을 그대로 질의에 붙인다. 방향은 `orderDir`를 대문자로 바꾼 값이다.
- 검색어는 리뷰 댓글 내용, 커밋 번호, 파일 경로 가운데 하나에 대소문자 없이 들어 있으면 맞는다.
- 한 쪽은 15개(`REVIEWS_PER_PAGE`)이고 쪽 번호는 `pageNum`이다.

## 화면

- 왼쪽: 빠른 검색 세 개(`review.allReview`, `review.involvingYou`, `review.createdByYou`). 각각 개수 배지가 있고, 개수는 지금 조건에서 `authorId`, `participantId`를 바꿔 센다(전체는 둘 다 비움, 내가 참여는 `participantId`만 현재 사용자, 내가 만든 것은 `authorId`만 현재 사용자). 지금 조건에 맞는 항목이 `active`이다.
- 왼쪽 폼 `#search`: 숨은 입력(`authorId`, `participantId`, `orderDir`, `orderBy`, `state`), 검색어 입력.
- 오른쪽: 정렬 링크는 날짜(`createdDate`) 하나(`data-toggle="order"`, 같은 필드면 방향을 뒤집는다). 열림, 닫힘 탭(`data-type="state"`, `data-value`는 `OPEN`, `CLOSED`, 숫자는 상태만 바꿔 센 값). 탭의 활성 판정은 대소문자를 구별해 `param.state == "OPEN"`일 때만 열림이 활성이다.
- 목록의 한 줄: 첫 리뷰 댓글 작성자의 아바타(기본 아바타이면 gravatar 32px), 스레드 번호, 첫 리뷰 댓글 내용(스레드가 있는 화면으로 가는 링크, 커밋이나 풀 리퀘스트의 변경 화면 `#thread-번호`), 작성자 표시 이름(이름이 없으면 `issue.noAuthor`), 만든 시각, 댓글 수(`리뷰 댓글 수 - 1`이 0보다 클 때, 링크는 같은 주소). 결과가 없으면 `review.is.empty` 안내이다.
- 왼쪽 아래: 엑셀 내려받기(`issue.downloadAsExcel`) 링크. 현재 주소에서 `pageNum`을 빼고 `format=xls`를 붙인다.
- 스크립트: `$yobi.loadModule("review.List", {elPagination, nTotalPages, welSearchForm})`.

## 엑셀 (`format=xls`)

현재 조건에 맞는 모든 스레드를 쪽 나누기 없이 내려준다. 파일 이름은 `프로젝트이름_reviews_yyyyMMddHHmm.xls`, 시트 이름은 오늘 시각의 밀리초이다. 열은 `No`, `COMMIT ID`, `REVIEW ID`, `REVIEW TITLE`, `Thread Author`, `Response Text`, `Response`, `REVIEW STATE`, `is PullRequest?`, `Date`이다. 스레드의 리뷰 댓글마다 한 줄이다.

- `COMMIT ID`는 커밋 번호 앞 7자리이다.
- 댓글 내용이 스레드의 첫 댓글 내용과 같으면 그 줄은 "응답"이 아니라 제목 줄로 보고(`REVIEW TITLE`, `Thread Author`를 채운다), 다르면 응답 줄이다(`Response Text`, `Response`(댓글 작성자)를 채운다). 첫 댓글과 같은 글을 단 응답도 제목 줄이 된다.
- `REVIEW STATE`는 스레드 상태, `is PullRequest?`는 풀 리퀘스트에 달린 스레드인지, `Date`는 댓글을 쓴 시각이다.

## 알려진 v1.6 동작(R-65)

1. 상태, 정렬 방향이 잘못되면 `500`이다. 정렬 기준은 검증 없이 질의에 붙는다. v2는 A-40과 같이 정렬 기준을 정해진 필드로 제한하고, 잘못된 상태, 방향은 `400`이다.
2. 엑셀의 `COMMIT ID`는 커밋 번호를 7자로 자른다. 코드 범위나 파일 단위가 아닌 스레드(`SimpleCommentThread`)는 v1.6 어디서도 만들지 않으므로 이 경로는 화면에서 닿지 않는다(v2는 빈 칸으로 둔다).
3. 열림 탭의 활성 판정이 대소문자를 구별해, `state=open`처럼 소문자로 들어오면 어느 탭도 활성이 아니다.
4. 첫 리뷰 댓글의 작성자를 번호로 다시 조회해서 사용자가 없으면 `500`이다.
