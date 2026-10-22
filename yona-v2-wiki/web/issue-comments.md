---
title: 이슈 댓글과 추천
type: web
sources: [app/controllers/IssueApp.java:955-1140, app/controllers/AbstractPostingApp.java:52-82, app/controllers/VoteApp.java, app/models/Comment.java, app/models/IssueComment.java]
updated: 2026-10-23
---

# 이슈 댓글과 추천

구현은 `IssueCommentController`(등록, 수정, 삭제), `IssueVoteController`(추천), `IssueCommentService`이다. 테스트 `IssueCommentTest` 22건.

## 등록과 수정 (`POST issue/:number/comments`, `POST issue/:number/comments/:commentId`)

멀티파트 폼이다. 두 주소는 같은 처리이고 폼의 `id`가 있으면 그 댓글을 고친다(v1.6은 `updateComment`가 `newComment`를 그대로 부른다).

1. 프로젝트의 이슈에 댓글을 쓸 권한이 없으면 `403`(공개 프로젝트는 로그인한 누구나, 비공개는 구성원).
2. `contents`가 비어 있으면 `400`과 `post.comment.empty`.
3. `id`가 있으면 댓글 수정 권한(작성자, 구성원)을 확인하고(없으면 `403`, v1.6은 이때 작성자를 가로채 저장했지만 v2는 막는다, A-24) 내용을 바꾼다. 작성자가 `notificationMail=yes`를 보냈거나 작성자가 아닌 사람이 고치면 수정 활동을 발행한다.
4. 새 댓글: `parentCommentId`가 있으면 한 줄 댓글. 댓글의 프로젝트 번호는 이슈의 프로젝트이다. 멘션을 기록하고 이슈의 댓글 수(`numOfComments`)를 실제 수에 맞춘다. 메일 본문에 덧붙일 "이전 내용"을 만들어 활동에 싣는다(첫 댓글은 `Original issue`, 한 줄 댓글은 앞의 한 줄 댓글이나 부모 댓글, 그 밖은 바로 앞의 댓글. 형식 `--- 제목 from @아이디  시각 ---` 뒤에 내용).
5. `withStateTransition`(멀티파트, 비어 있지 않음)이면 이슈를 다음 상태로 바꾼다(상태 이벤트 포함). 아니면 이슈의 수정 시각만 갱신한다.
6. 임시 첨부(`temporaryUploadFiles`)를 댓글로 옮긴다. `303`으로 `이슈 주소#comment-번호`에 보낸다.

## 삭제 (`DELETE issue/:number/comment/:commentId/delete`)

댓글 삭제 권한(작성자, 구성원)이 없으면 `403`, 없는 댓글은 `404`. 첨부와 알림 이벤트를 지우고 이 댓글에 달린 한 줄 댓글은 일반 댓글이 되며 이슈의 댓글 수를 맞춘다. Ajax이면 `204`와 이슈 주소의 `Location`, 아니면 `303`. 멘션 기록은 남는다(R-26).

## 추천

로그인해야 하고 프로젝트를 읽을 수 있어야 한다. 이슈 추천 `POST issue/:number/vote`와 `unvote`는 `303`으로 이슈에, 댓글 추천 `POST issue/:number/comment/:id/vote`와 `unvote`는 `303`으로 댓글 주소에 보낸다. 없는 댓글은 `404`와 본문 `issue.comment.error.vote`(또는 `.unvote`), 추천하지 않은 댓글을 취소하면 `404`와 `issue.comment.error.have.not.voted`.

## 알림 연결 고리

`IssueCommentActivity(comment, previousContents, created, actor)`를 발행한다. 알림 서브시스템(4단계)이 받는다.
