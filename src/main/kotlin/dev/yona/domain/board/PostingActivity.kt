package dev.yona.domain.board

import dev.yona.domain.user.User

/**
 * 게시글이 새로 올라왔음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterNewPost`를 부르던 지점에서 발행한다.
 *
 * @property posting 저장된 게시글
 * @property actor 쓴 사용자
 */
data class PostingCreatedActivity(val posting: Posting, val actor: User)

/**
 * 게시글 본문이 수정되었음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterUpdatePosting`을 부르던 지점에서 발행한다.
 * 알림 메일을 고르지 않은 작성자의 수정에는 발행하지 않는다.
 *
 * @property oldBody 고치기 전의 본문
 */
data class PostingUpdatedActivity(val posting: Posting, val oldBody: String?, val actor: User)

/**
 * 게시글이 지워졌음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterResourceDeleted`를 부르던 지점에서 발행한다.
 * 지워진 뒤에는 게시글을 읽을 수 없으므로 알림에 필요한 값을 함께 담는다.
 */
data class PostingDeletedActivity(val projectId: Long, val number: Long, val title: String?, val body: String?, val actor: User)

/**
 * 게시글 댓글이 새로 달렸거나 수정되었음을 알리는 애플리케이션 이벤트이다.
 *
 * @property previousContents 새 댓글일 때 메일 본문에 덧붙이는 "이전 내용". 수정이면 `null`
 * @property created 새 댓글이면 `true`, 수정이면 `false`
 */
data class PostingCommentActivity(val comment: PostingComment, val previousContents: String?, val created: Boolean, val actor: User)
