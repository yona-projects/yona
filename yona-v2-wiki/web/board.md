---
title: 게시판
type: web
sources: [app/controllers/BoardApp.java, app/controllers/AbstractPostingApp.java, app/controllers/api/BoardApi.java, app/views/board/*.scala.html, app/views/organization/group_board_list*.scala.html, app/playRepository/BareCommit.java, app/utils/GitUtil.java, app/utils/LineEnding.java]
updated: 2026-10-23
---

# 게시판

구현은 아래와 같다. 테스트는 `BoardListTest` 14, `BoardViewTest` 15, `BoardWriteTest` 31, `BoardCommentTest` 17, `OrganizationBoardTest` 10, `OnlineCommitTest` 8, `LineEndingTest` 5건.

| 기능 | 구현 |
|---|---|
| 목록 `GET /:user/:project/posts` | `BoardListController`, `PostingSearchService`, `BoardListAssembler`, `board/list.html` |
| 보기 `GET /:user/:project/post/:number` | `BoardViewController`, `BoardViewAssembler`, `board/view.html`, `board/partial-comments.html` |
| 작성·수정·삭제 | `BoardWriteController`, `PostingService`, `board/create.html`, `board/edit.html` |
| 댓글 | `BoardCommentController`, `PostingCommentService` |
| JSON API | `BoardApiController` (라벨, 본문 갱신, 댓글 갱신), `IssueJson.ofPosting` |
| 조직 게시판 `GET /organizations/:name/boards` | `OrganizationBoardController`, `organization/board-list.html` |
| 온라인 커밋 | `OnlineCommit`(JGit), `LineEnding` |

## 목록

- 공지가 아닌 글을 15개씩, 기본 작성일 내림차순(`orderBy=id`는 `createdDate`)이다. 정렬 기준은 `updatedDate`, `createdDate`, `numOfComments`(그 밖은 작성일). 검색어는 제목과 본문에서 대소문자 없이 찾고 `%`, `_`도 글자로 찾는다. 라벨은 하나라도 붙은 글을 찾는다. 공지는 쪽과 관계없이 위에 모두 보인다.
- 글이 하나도 없으면 안내, 둘 이상이면 정렬 막대. 정렬 링크는 검색어와 고른 라벨을 이어 받는다(R-28).
- 줄: 공지(`Notice`)/README 표시 또는 번호, 제목 머리말, 작성자(없으면 "No author"), 시각, 댓글 수, 라벨.

## 보기

- 머리(번호, 제목, 시각), 작성자, 본문(마크다운. 비면 빈 내용 표시), 변경 이력(익명은 로그인 링크, 아니면 대화상자), 체크리스트 갱신용 숨은 폼, 첨부, 구독 단추(구독 권한이 있을 때), 번역 단추(번역 API 설정 시), 편집(수정 권한이 없으면 "See text"), 삭제 대화상자, 댓글 목록과 입력, 오른쪽 창(쓰기 단추, 라벨 선택기 또는 라벨 목록).
- README 글은 저장소 `HEAD` 최상위의 첫 README(`README.md`, `readme.md`, `README.markdown`, `readme.markdown`)를 본문으로 보여 준다. 없으면 빈 본문.
- `Accept: application/json`이면 `{title, type, author, authorName, created_at, body, attachments, comments}`를 돌려준다.
- 로그인한 사용자가 보면 최근 본 목록(`RecentIssue`의 `postingId`)에 남는다.

## 작성과 온라인 커밋

작성 폼은 글쓰기와 저장소 파일 편집에 함께 쓰인다.

- `?readme`: 저장소 README로 채우고 제목 `Update README.md`, "README로 만들기" 선택이 켜진다.
- `?issueTemplate=true`: `ISSUE_TEMPLATE.md`로 채우고 첨부 업로더가 없다.
- `?path=…&branch=…[&edit]`: 구성원만(커밋 권한). 지정한 파일의 내용으로 채우고 저장하면 그 경로에 커밋한다.

저장(`POST /posts`) 순서: 제목 필수와 255자(`400`과 폼 다시 보기, 값은 비워서 보인다), 본문이 없으면 `413`. `readme`가 켜져 있으면 README 글이 이미 있을 때 그 글을 수정하고, 없으면 `README.md`를 커밋한 뒤 글도 저장한다. `issueTemplate=true`이면 `ISSUE_TEMPLATE.md`를 커밋하고 글은 저장하지 않으며 프로젝트 홈으로 간다. `path`가 있고 구성원이면 브랜치의 경로에 커밋(줄바꿈은 `lineEnding`에 맞춘다. DOS 변환은 R-27)하고 코드 화면으로 간다. 그 밖에는 글을 저장한다(공지는 공지 권한이 있을 때만, 번호는 프로젝트의 다음 번호, 제목 머리말과 멘션 기록, 새 글 활동 발행).

## 수정과 삭제

- 수정 폼은 읽을 수 있으면 열린다(저장 단추는 수정 권한이 있을 때). 공지 선택은 구성원과 사이트 관리자에게, README 선택은 Git이고 커밋 권한이 있을 때, 알림 메일 선택은 작성자이고 README가 아닐 때 보인다.
- 수정(`POST /post/:n/edit`): 제목 오류는 `400`, 수정 권한이 없으면 `403`, 본문이 없으면 `413`. `readme`가 켜져 있으면 `README.md`를 커밋하고 다른 README 글의 표시를 푼다. 폼 값을 모두 반영한다(체크하지 않은 공지는 해제). 본문이 바뀌면 변경 이력을 앞에 쌓는다. 제목 머리말 개수를 새 제목으로 옮기고 멘션을 맞춘다. 알림 메일을 고르거나 작성자가 아니면 수정 활동을 발행한다.
- 삭제(`DELETE /post/:n/delete`): 댓글(첨부, 알림 이벤트 포함), 제목 머리말, 첨부, 알림 이벤트를 지운다. Ajax면 `204`와 목록 주소, 아니면 목록으로 `303`. 멘션 기록은 남는다(R-24와 같다).

## 댓글

이슈 댓글과 같은 흐름이다(`web/issue-comments.md`): 등록과 수정은 한 주소(`POST …/comment`, `…/comment/:id`)이고 폼의 `id`로 구분한다. 빈 댓글은 `400`, 쓸 수 없으면 `403`. 이전 내용은 `Original posting`/`Previous comment`. 글의 수정 시각은 새 댓글에서만 바뀐다. 삭제(`DELETE …/comment/:id/delete`)는 한 줄 댓글을 일반 댓글로 남기고 글의 댓글 수를 맞춘다.

## JSON API

`POST /-_-api/v1/owners/:o/projects/:p/postlabel/:n`(라벨 배열, 수정 권한 필요. 응답 `{id, labels}`. R-29), `PATCH …/posts/:n/content`(체크리스트 갱신. `409` 충돌), `PATCH /:user/:project/post/:n/comment/:id`(댓글 체크리스트 갱신. 권한을 충돌보다 먼저 본다). 응답 형식은 `web/issue-api.md`와 같다.

## 조직 게시판

조직의 보이는 프로젝트(또는 `projectNames[]`로 고른 것)의 글을 최근 작성 순으로 모은다. 공지를 따로 나누지 않고 모두 섞인다. 줄에는 프로젝트 이름 링크와 `#번호`가 있다. 조직이 없으면 `404`.
