package dev.yona.mailbox

/** 받은 메일을 처리하지 못한 이유이다. 메시지는 보낸 사람에게 회신으로 알린다. v1.6 `mailbox.exceptions.MailHandlerException`이다. */
open class MailHandlerException(message: String) : Exception(message)

/** 메일 보낸 사람에게 그 프로젝트에 리소스를 만들 권한이 없다 (v1.6 `PermissionDenied`). */
class PermissionDenied(message: String) : MailHandlerException(message)

/** 댓글을 달 이슈가 없다 (v1.6 `IssueNotFound`). */
class IssueNotFound(number: Long) : MailHandlerException("Issue $number not found")

/** 댓글을 달 게시글이 없다 (v1.6 `PostingNotFound`). */
class PostingNotFound(number: Long) : MailHandlerException("Posting $number not found")
