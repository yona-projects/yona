package dev.yona.domain.comment

import dev.yona.domain.enumeration.ThreadState
import org.springframework.data.jpa.repository.JpaRepository

/** 코드 리뷰 스레드 저장소이다. */
interface CommentThreadRepository : JpaRepository<CommentThread, Long> {

    /** 프로젝트에서 지정한 상태의 스레드 개수를 센다 (v1.6 `CommentThread.countReviewsBy` 기본 조건은 OPEN). */
    fun countByProjectIdAndState(projectId: Long, state: ThreadState): Long
}
