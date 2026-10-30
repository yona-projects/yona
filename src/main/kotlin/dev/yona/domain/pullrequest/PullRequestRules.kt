package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.State
import dev.yona.domain.user.User

/** 열린 요청이면 `true`이다 (v1.6 `PullRequest.isOpen`). */
val PullRequest.open: Boolean get() = state == State.OPEN

/** 닫힌 요청이면 `true`이다 (v1.6 `PullRequest.isClosed`). 병합된 요청은 닫힌 것이 아니다. */
val PullRequest.closed: Boolean get() = state == State.CLOSED

/** 병합된 요청이면 `true`이다 (v1.6 `PullRequest.isMerged`). */
val PullRequest.merged: Boolean get() = state == State.MERGED

/**
 * 받는 브랜치에 적용되는 보호 규칙이다. 없으면 `null`이다(추가 개발 항목 X-32, X-33, `decisions.md` D-42, D-45). 규칙이 없으면 GitHub처럼 아무것도 막지 않는다.
 */
val PullRequest.protection: dev.yona.domain.project.BranchProtectionRule? get() = toProject?.protectionFor(toBranch)

/** 받는 브랜치의 보호 규칙이 요구하는 승인 수이다. 규칙이 없으면 0이다 (v1.6 `getRequiredReviewerCount`를 규칙 단위로 바꿨다). */
val PullRequest.requiredReviewerCount: Int get() = protection?.requiredApprovals ?: 0

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

/** 변경 요청이 최신 판정으로 남아 있는 리뷰어의 로그인 ID이다. 표시에는 항상 쓰고 병합은 적용되는 규칙이 있을 때만 막는다. */
val PullRequest.changesRequestedBy: List<String?>
    get() = latestReviews.values.filter { it.effectiveVerdict == dev.yona.domain.enumeration.ReviewVerdict.CHANGES_REQUESTED }.sortedWith(compareBy({ it.createdDate }, { it.id })).map { it.authorLoginId }

/** [user]가 리뷰한 요청이면 `true`이다 (v1.6 `isReviewedBy`). */
fun PullRequest.isReviewedBy(user: User?): Boolean = user != null && reviewers.any { it.id == user.id }

/**
 * 병합 조건이 병합을 막고 있으면 `true`이다. 받는 브랜치에 규칙이 있을 때만 따지며 규칙이 켠 조건만 본다(추가 개발 항목 X-12, X-32, X-33, `decisions.md` D-35, D-45).
 * 필요한 승인 수가 모자라거나, 변경 요청 해결을 요구했는데 남아 있거나, 대화 해결을 요구했는데 열린 스레드가 [openConversations]개 있을 때이다.
 * 프로젝트 관리자와 사이트 관리자는 규칙이 "관리자에게도 적용"이 아니면 이 막힘을 우회할 수 있다.
 */
fun PullRequest.reviewBlocked(openConversations: Int = 0): Boolean {
    val rule = protection ?: return false
    return !reviewed || (rule.requireNoChangesRequested == true && changesRequestedBy.isNotEmpty()) || (rule.requireConversationResolution == true && openConversations > 0)
}

/** 병합 조건을 보지 않고 충돌이 아니고, 열려 있고, 병합 검사 중이 아니면 `true`이다. */
val PullRequest.acceptableIgnoringReview: Boolean get() = isConflict != true && open && isMerging != true

/** 충돌이 아니고, 열려 있고, 병합 검사 중이 아니며, 병합 조건이 막고 있지 않으면 받아들일 수 있다 (v1.6 `isAcceptable`에 보호 규칙을 더했다). */
fun PullRequest.acceptable(openConversations: Int = 0): Boolean = acceptableIgnoringReview && !reviewBlocked(openConversations)

/**
 * 이 풀 리퀘스트에 쓸 수 있는 병합 방식이다. 프로젝트가 허용한 방식에서, 받는 브랜치의 규칙이 선형 이력을 요구하면 병합 커밋을 뺀다(X-33, D-45).
 */
fun PullRequest.allowedMergeMethods(): Set<MergeMethod> {
    val allowed = toProject?.allowedMergeMethods().orEmpty()
    return if (protection?.requireLinearHistory == true) allowed - MergeMethod.MERGE else allowed
}

/** 병합 화면이 처음 고르는 방식이다. 프로젝트의 기본 방식이 쓸 수 있으면 그것이고, 아니면 쓸 수 있는 방식 중 병합 커밋, 스쿼시, 리베이스 순서로 첫 번째이다. 쓸 방식이 없으면 병합 커밋이다. */
fun PullRequest.defaultMergeMethodOrFirst(): MergeMethod {
    val allowed = allowedMergeMethods()
    return MergeMethod.of(toProject?.defaultMergeMethod)?.takeIf { it in allowed } ?: allowed.firstOrNull() ?: MergeMethod.MERGE
}

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
    protection?.requireNoChangesRequested == true && changesRequestedBy.isNotEmpty() -> "pullRequest.not.acceptable.because.changes.requested" to emptyArray()
    else -> "pullRequest.not.acceptable.because.open.conversations" to emptyArray()
}
