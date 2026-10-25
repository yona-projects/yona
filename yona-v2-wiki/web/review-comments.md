---
title: 코드 리뷰 댓글
type: web
sources: [app/controllers/CodeHistoryApp.java, app/controllers/PullRequestApp.java:566-640, app/controllers/CommentThreadApp.java, app/controllers/CommentApp.java, app/models/ReviewComment.java, app/models/CommentThread.java, app/models/CodeCommentThread.java, app/models/NotificationEvent.java:275-340, app/models/NotificationEvent.java:837-880, app/models/NotificationEvent.java:1180-1202, app/views/partial_comment_form_on_thread.scala.html]
updated: 2026-10-23
---

# 코드 리뷰 댓글

| 주소 | 권한 | 설명 |
|---|---|---|
| `POST /:user/:project/commit/:commitId/comments` | 커밋 코멘트 만들기 | 커밋에 단다. 완료하면 `…/commit/:id#comment-번호`로 303 |
| `POST /:user/:project/pullRequest/:pullRequestId/comments?commitId=` | 리뷰 댓글 만들기 | 풀 리퀘스트에 단다. `pullRequestId`는 번호가 아니라 DB 번호이다 |
| `POST /threads/:id/open`, `/close` | 다시 열기, 닫기(스레드) | 상태 변경. 성공하면 빈 200, 없으면 404, 권한이 없으면 403 |
| `DELETE /comments/:type/:id` | 지우기 | `review_comment`, `commit_comment`만. 지울 수 없는 종류면 400 |

## 만들기

- 내용(`contents`)이 비면 400. `thread.id`가 있으면 그 스레드(같은 프로젝트여야 함, A-80)에 이어 달고, 없으면 새 스레드를 연다.
- 코드 범위(`path`, `startSide`, `startLine`, `startColumn`, `endSide`, `endLine`, `endColumn`)에서 줄과 열 네 값이 모두 숫자로 있으면 코드 범위 스레드, 아니면 파일 단위 스레드이다. 스레드는 열린 상태이고 커밋 작성자가 가입한 사용자이면 코드 작성자(`codeAuthors`)에 넣는다.
- 커밋 댓글은 `prevCommitId`가 `null`이다. 풀 리퀘스트 댓글은 `commitId`가 없으면 병합 기준 커밋 쌍(`mergedCommitIdTo`, `mergedCommitIdFrom`)에, 있으면 그 커밋 하나(`prevCommitId`는 빈 문자열)에 단 것이다. 새 스레드는 풀 리퀘스트에 연결한다. `commitId`는 v1.6 라우트가 `commitId: String ?= null`로 받는 **쿼리 문자열** 인자라서, 전체 변경 화면이 폼 본문에 숨겨 보내는 같은 이름의 값은 읽지 않는다(2026-10-25 브라우저 점검에서 본문 값까지 읽어 병합 기준 댓글이 모두 "어긋남"으로 분류되던 결함을 고쳤다. `ReviewCommentTest`).
- 작성자의 임시 첨부 전부를 댓글로 옮긴다. 존재하지 않는 커밋은 404.
- Subversion 프로젝트의 커밋 코멘트(`newSVNComment`)는 [web/subversion.md](subversion.md)에 있다.

## 알림

| 종류 | 받는 사람 |
|---|---|
| `NEW_REVIEW_COMMENT`(커밋) | 커밋 구독자(작성자, 이 커밋의 리뷰 댓글 작성자, 프로젝트와 커밋 구독자 − 구독 해제자) + 댓글에서 멘션된 사용자 − 댓글 작성자 |
| `NEW_REVIEW_COMMENT`(풀 리퀘스트) | 풀 리퀘스트 수신자(구독자 + 본문 멘션 − 댓글 작성자) + 댓글에서 멘션된 사용자 |
| `REVIEW_THREAD_STATE_CHANGED` | 풀 리퀘스트 구독자 또는 커밋 구독자 − 바꾼 사람 |

본문(메일, 홈 알림)은 코드 범위 스레드의 첫 댓글이면 댓글이 달린 줄까지의 차이(diff) 조각 사이에 댓글을 끼운 것이다. 파일을 새로 추가했거나 지웠으면 차이 조각이 없다(R-44).

## 삭제

스레드의 마지막 댓글을 지우면 스레드도 지운다. 풀 리퀘스트와의 연결도 함께 푼다.

## 화면

스레드 조각(`code/partial-comment-thread.html`)은 답글 입력 영역을 스레드를 만들 권한이 있을 때 보이고, 열기, 닫기 단추는 스레드를 고칠 수 있을 때 보인다. 커밋 보기와 풀 리퀘스트 변경 화면이 같은 조각을 쓴다. 스레드 주소는 `ReviewLinks`가 만든다.
