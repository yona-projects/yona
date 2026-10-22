package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.State
import dev.yona.domain.user.User

/** 열린 요청이면 `true`이다 (v1.6 `PullRequest.isOpen`). */
val PullRequest.open: Boolean get() = state == State.OPEN

/** 닫힌 요청이면 `true`이다 (v1.6 `PullRequest.isClosed`). 병합된 요청은 닫힌 것이 아니다. */
val PullRequest.closed: Boolean get() = state == State.CLOSED

/** 병합된 요청이면 `true`이다 (v1.6 `PullRequest.isMerged`). */
val PullRequest.merged: Boolean get() = state == State.MERGED

/** 받는 프로젝트가 요구하는 리뷰어 수이다 (v1.6 `getRequiredReviewerCount`). */
val PullRequest.requiredReviewerCount: Int get() = toProject?.defaultReviewerCount ?: 0

/** 리뷰어가 요구하는 수 이상이면 `true`이다 (v1.6 `isReviewed`). */
val PullRequest.reviewed: Boolean get() = reviewers.size >= requiredReviewerCount

/** 더 필요한 리뷰어 수이다. 이미 채웠으면 0 이하이다 (v1.6 `getLackingReviewerCount`). */
val PullRequest.lackingReviewerCount: Int get() = requiredReviewerCount - reviewers.size

/** [user]가 리뷰한 요청이면 `true`이다 (v1.6 `isReviewedBy`). */
fun PullRequest.isReviewedBy(user: User?): Boolean = user != null && reviewers.any { it.id == user.id }

/** 충돌이 아니고, 열려 있고, 병합 검사 중이 아니며, 리뷰 조건을 채웠으면 받아들일 수 있다 (v1.6 `isAcceptable`). */
val PullRequest.acceptable: Boolean
    get() = isConflict != true && open && isMerging != true && (reviewed || toProject?.isUsingReviewerCount != true)

/** 변경 내용을 보여 줄 수 있으면 `true`이다. 충돌하지 않았고 병합 기준 커밋이 있어야 한다 (v1.6 `isDiffable`). */
val PullRequest.diffable: Boolean get() = isConflict == false && mergedCommitIdFrom != null && mergedCommitIdTo != null

/**
 * 받아들이기 단추가 막혀 있을 때 그 이유를 알리는 메시지 키와 인자이다 (v1.6 `getMessageForDisabledAcceptButton`).
 * 병합 검사 중, 충돌, 열려 있지 않음, 리뷰어 부족 순으로 본다.
 */
fun PullRequest.disabledAcceptMessage(): Pair<String, Array<Any>> = when {
    isMerging == true -> "pullRequest.not.acceptable.because.is.merging" to emptyArray()
    isConflict == true -> "pullRequest.not.acceptable.because.is.conflict" to emptyArray()
    !open -> "pullRequest.not.acceptable.because.is.not.open" to emptyArray()
    else -> "pullRequest.not.acceptable.because.is.not.enough.review.point" to arrayOf<Any>(lackingReviewerCount)
}
