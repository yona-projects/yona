package dev.yona.web.pullrequest

/** 풀 리퀘스트 머리 영역의 리뷰어 표시와 리뷰 단추이다. */
data class ReviewerArea(
    val labelHtml: String, val avatarsHtml: String, val reviewUrl: String?, val unreviewUrl: String?, val reviewButtonSuccess: Boolean
)

/** 받아들이기 단추이다. 막혀 있으면 [disabledReason]이 이유이다. */
data class AcceptButton(val url: String, val enabled: Boolean, val disabledReason: String?)

/** 풀 리퀘스트 보기와 변경 화면 위쪽의 공통 머리 영역이다 (v1.6 `partial_info`). */
data class PullRequestHeader(
    val number: Long, val title: String, val ago: String, val agoTitle: String, val badgeClass: String, val badgeKey: String,
    val reviewers: ReviewerArea?, val accept: AcceptButton?, val overviewUrl: String, val changesUrl: String, val openThreadCount: Long, val activeTab: String
)

/** 보내는 곳과 받는 곳의 프로젝트와 브랜치 링크이다 (v1.6 `partial_branch`). */
data class BranchInfoView(
    val fromOwnerUrl: String, val fromOwner: String, val fromProjectUrl: String, val fromName: String, val fromBranchUrl: String, val fromBranch: String,
    val toOwnerUrl: String, val toOwner: String, val toProjectUrl: String, val toName: String, val toBranchUrl: String, val toBranch: String
)

/** 풀 리퀘스트를 보낸 사람이다. */
data class ContributorView(val href: String, val avatarUrl: String, val name: String, val loginId: String)

/** 충돌을 푸는 방법 안내에 쓰는 값이다. */
data class ConflictHelp(val fromBranch: String, val toBranch: String, val codeUrl: String, val refreshUrl: String)

/** 병합된 요청 아래의 보낸 브랜치 삭제, 복원 단추이다. */
data class BranchActions(val deleteUrl: String?, val restoreUrl: String?, val fromBranchFull: String)

/** 병합을 한 사람이다. */
data class ReceiverView(val href: String, val avatarUrl: String, val name: String, val loginId: String)

/**
 * 풀 리퀘스트의 상태 조각이다 (v1.6 `partial_state`). 상태에 따라 한 가지만 채워진다.
 *
 * @property kind `safe`(병합해도 안전), `conflict`, `merging`, `merged`, `none`
 */
data class PullRequestStateView(
    val kind: String, val conflictHelp: ConflictHelp?, val receiver: ReceiverView?, val receiverMessageHtml: String?, val branchActions: BranchActions?
)

/** 타임라인의 커밋 한 줄이다. */
data class TimelineCommit(
    val commitUrl: String, val shortId: String, val outdated: Boolean, val authorHref: String?, val authorName: String?, val avatarUrl: String, val authorEmail: String,
    val date: String, val dateTitle: String, val shortMessageHtml: String, val hasMore: Boolean, val moreHtml: String
)

/**
 * 타임라인의 이벤트 하나이다 (v1.6 `partial_pull_request_event`).
 *
 * @property stateClass 상태 라벨의 CSS 클래스(`changed`, 또는 새 값)
 */
data class TimelineEvent(
    val id: Long, val stateClass: String, val stateKey: String, val messageHtml: String, val ago: String, val agoTitle: String,
    val compareUrl: String?, val commits: List<TimelineCommit>
)

/** 오른쪽 리뷰 카드 한 장이다 (v1.6 `partial_reviewlist`). */
data class PullRequestReviewCard(
    val href: String, val state: String, val outdated: Boolean, val content: String, val commentCount: Int, val ago: String, val agoTitle: String, val avatarUrl: String, val authorName: String? = null
)

/** 변경 내용의 커밋 선택 상자의 선택지이다. */
data class CommitChoice(val commitId: String, val shortId: String, val shortMessage: String, val url: String)
