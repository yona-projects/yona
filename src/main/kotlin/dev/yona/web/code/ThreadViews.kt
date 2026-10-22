package dev.yona.web.code

/**
 * 코드 리뷰 스레드의 댓글 하나이다.
 *
 * @property agoTitle 마우스를 올리면 보이는 정확한 시각
 * @property deleteUrl 지울 수 있을 때만 있다
 * @property bodyHtml 마크다운을 렌더링한 본문
 * @property attachments 첨부 목록의 JSON 문자열(`data-attachments`)
 */
data class ThreadCommentView(
    val id: Long, val loginId: String, val name: String, val avatarUrl: String, val userHref: String, val ago: String, val agoTitle: String,
    val deleteUrl: String?, val bodyHtml: String, val attachments: String
)

/**
 * 스레드 아래의 댓글 입력 영역이다. 댓글을 만들 수 없으면 `null`이고 상태 바꾸기 단추만 남는다.
 *
 * @property action 댓글 등록 주소
 * @property threadId 이어서 쓰는 스레드 번호
 * @property resourceType 올리는 첨부의 리소스 종류 이름
 */
data class ThreadFormView(val action: String, val threadId: Long, val resourceType: String, val avatarUrl: String, val userHref: String, val userName: String, val anonymous: Boolean)

/**
 * 코드 리뷰 스레드 하나의 화면 값이다.
 *
 * @property state `open` 또는 `closed`
 * @property range 코드 범위 스레드의 위치 속성. 파일 단위 스레드는 `null`이다
 * @property stateUrl 상태를 바꾸는 요청 주소(`/threads/:id/close` 또는 `open`). 바꿀 수 없으면 `null`
 * @property form 댓글 입력 영역. 댓글을 만들 수 없으면 `null`
 */
data class ThreadView(
    val id: Long, val state: String, val code: Boolean, val fold: Boolean, val range: Map<String, String>?, val comments: List<ThreadCommentView>,
    val stateUrl: String?, val form: ThreadFormView?
)
