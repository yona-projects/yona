package dev.yona.web.code

import dev.yona.domain.comment.ReviewComment
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 변경 화면에서 작성자 본인에게만 보이는 임시 저장 리뷰의 댓글이다(로드맵 X-26, `decisions.md` D-38). 엔티티 조회는 임시 행을 숨기므로 컨트롤러가
 * `PendingReviewReader`로 읽어 요청 속성으로 두고, [ThreadViewAssembler]가 스레드를 그릴 때 덧붙인다.
 *
 * @property repliesByThread 공개 스레드에 단 내 임시 답글(스레드 번호별)
 * @property commentsByPendingThread 내 임시 스레드의 댓글(스레드 번호별)
 * @property deleteBase 임시 댓글을 지우는 요청 주소의 앞부분이다(`<앞부분>/<댓글 번호>/delete`)
 */
class PendingOverlay(
    val repliesByThread: Map<Long, List<ReviewComment>>, val commentsByPendingThread: Map<Long, List<ReviewComment>>, val deleteBase: String
) {
    /** 내 임시 댓글이 하나라도 있는가 */
    val hasPending: Boolean get() = repliesByThread.isNotEmpty() || commentsByPendingThread.isNotEmpty()

    companion object {
        const val ATTRIBUTE = "dev.yona.web.code.PendingOverlay"

        /** 지금 요청의 임시 댓글 덧붙임이다. 없으면 `null`이다. */
        fun current(): PendingOverlay? =
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.getAttribute(ATTRIBUTE) as? PendingOverlay
    }
}
