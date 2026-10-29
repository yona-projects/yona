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

/** 개수에 세는 승인한 사람 수이다. 작성자 본인의 승인은 세지 않는다(추가 개발 항목 X-12, `decisions.md` D-35 12번. v1.6 시절 작성자가 해 둔 승인도 같다). */
val PullRequest.countedApprovals: Int get() = reviewers.count { it.id != contributor?.id }

/** 리뷰어가 요구하는 수 이상이면 `true`이다 (v1.6 `isReviewed`. 작성자의 승인은 세지 않는다). */
val PullRequest.reviewed: Boolean get() = countedApprovals >= requiredReviewerCount

/** 더 필요한 리뷰어 수이다. 이미 채웠으면 0 이하이다 (v1.6 `getLackingReviewerCount`). */
val PullRequest.lackingReviewerCount: Int get() = requiredReviewerCount - countedApprovals

/**
 * 리뷰어별 최신 판정이다(추가 개발 항목 X-12, D-35). 한 리뷰어가 낸 리뷰 중 가장 나중 것이며 해제된 리뷰는 댓글(`COMMENTED`)로 본다.
 * 키는 리뷰어 사용자 번호이다.
 */
val PullRequest.latestReviews: Map<Long?, PullRequestReview>
    get() = reviewHistory.groupBy { it.authorId }.mapValues { (_, list) -> list.maxWith(compareBy({ it.createdDate }, { it.id })) }

/** [PullRequestReview]의 유효한 판정이다. 해제된 리뷰는 댓글로 본다. */
val PullRequestReview.effectiveVerdict: dev.yona.domain.enumeration.ReviewVerdict?
    get() = if (dismissedDate != null) dev.yona.domain.enumeration.ReviewVerdict.COMMENTED else verdict

/** 변경 요청이 최신 판정으로 남아 있는 리뷰어의 로그인 ID이다. 표시에는 항상 쓰고 병합은 프로젝트가 리뷰어 수를 켠 경우에만 막는다. */
val PullRequest.changesRequestedBy: List<String?>
    get() = latestReviews.values.filter { it.effectiveVerdict == dev.yona.domain.enumeration.ReviewVerdict.CHANGES_REQUESTED }.sortedWith(compareBy({ it.createdDate }, { it.id })).map { it.authorLoginId }

/** [user]가 리뷰한 요청이면 `true`이다 (v1.6 `isReviewedBy`). */
fun PullRequest.isReviewedBy(user: User?): Boolean = user != null && reviewers.any { it.id == user.id }

/** 충돌이 아니고, 열려 있고, 병합 검사 중이 아니며, 리뷰 조건을 채웠으면 받아들일 수 있다 (v1.6 `isAcceptable`). */
val PullRequest.acceptable: Boolean
    get() = isConflict != true && open && isMerging != true && (toProject?.isUsingReviewerCount != true || (reviewed && changesRequestedBy.isEmpty()))

/** 변경 내용을 보여 줄 수 있으면 `true`이다. 충돌하지 않았고 병합 기준 커밋이 있어야 한다 (v1.6 `isDiffable`). */
val PullRequest.diffable: Boolean get() = isConflict == false && mergedCommitIdFrom != null && mergedCommitIdTo != null

/**
 * 받아들이기 단추가 막혀 있을 때 그 이유를 알리는 메시지 키와 인자이다 (v1.6 `getMessageForDisabledAcceptButton`).
 * 병합 검사 중, 충돌, 열려 있지 않음, 리뷰어 부족, 변경 요청 남음 순으로 본다.
 */
fun PullRequest.disabledAcceptMessage(): Pair<String, Array<Any>> = when {
    isMerging == true -> "pullRequest.not.acceptable.because.is.merging" to emptyArray()
    isConflict == true -> "pullRequest.not.acceptable.because.is.conflict" to emptyArray()
    !open -> "pullRequest.not.acceptable.because.is.not.open" to emptyArray()
    !reviewed -> "pullRequest.not.acceptable.because.is.not.enough.review.point" to arrayOf<Any>(lackingReviewerCount)
    else -> "pullRequest.not.acceptable.because.changes.requested" to emptyArray()
}
