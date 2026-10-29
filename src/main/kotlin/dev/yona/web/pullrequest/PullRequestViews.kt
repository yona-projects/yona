package dev.yona.web.pullrequest

/**
 * 풀 리퀘스트 머리 영역의 리뷰어 표시와 리뷰 단추이다. 승인한 사람의 아바타를 보이고 단추는 개요 화면의 리뷰 폼으로 이동한다
 * (v1.6의 "리뷰 완료/취소" 단추를 GitHub 방식의 리뷰 판정으로 바꿨다, 추가 개발 항목 X-12, `decisions.md` D-35).
 */
data class ReviewerArea(val labelHtml: String, val avatarsHtml: String, val reviewUrl: String?, val reviewButtonSuccess: Boolean)

/**
 * 받아들이기 단추이다. 막혀 있으면 [disabledReason]이 이유이다. 프로젝트 관리자와 사이트 관리자가 리뷰 조건에 막힌 요청을 우회해 병합할 수 있으면
 * 단추는 켜져 있고 [bypassNote]에 안내가 있다(D-35 11번).
 */
data class AcceptButton(
    val url: String, val enabled: Boolean, val disabledReason: String?, val bypassNote: String? = null,
    val label: String? = null, val methods: List<MergeMethodOption> = emptyList(), val defaultMethod: String = "merge"
) {
    /** 방식 선택 폼이 보낼 주소이다. 단추가 붙인 `?method=` 질의는 뺀다. */
    val formAction: String get() = url.substringBefore('?')
}

/**
 * 병합 방식 선택 목록의 한 항목이다(추가 개발 항목 X-28, `decisions.md` D-41). [available]이 아니면 고를 수 없고 [reason]에 이유를 보인다.
 */
data class MergeMethodOption(val code: String, val label: String, val available: Boolean, val reason: String?)

/**
 * 개요 오른쪽 열의 리뷰어 한 줄이다. [verdict]는 유효한 최신 판정 이름(`APPROVED`, `CHANGES_REQUESTED`, `COMMENTED`)이고 해제된 리뷰는 `COMMENTED`이다.
 * [dismissUrl]이 있으면 해제할 수 있는 사람에게 해제 폼을 보인다.
 */
data class ReviewerRow(
    val loginId: String, val name: String, val href: String, val avatarUrl: String, val verdict: String, val dismissed: Boolean, val dismissedReason: String?,
    val systemDismissed: Boolean, val dismissUrl: String?, val ago: String, val agoTitle: String,
    /** 편집 권한자가 이 사람에게 다시 요청하는 주소이다(열린 풀 리퀘스트). 없으면 단추를 보이지 않는다(추가 항목 X-27, D-37) */
    val rerequestUrl: String? = null
)

/** 리뷰를 요청받고 아직 리뷰하지 않은 사람 한 줄이다(추가 항목 X-27, `decisions.md` D-37). [cancelUrl]은 편집 권한자에게만 있다. */
data class PendingReviewRow(val loginId: String, val name: String, val href: String, val avatarUrl: String, val cancelUrl: String?)

/**
 * 개요 화면의 리뷰 영역이다. [formUrl]이 있으면(로그인한 사용자) 판정 폼을 보이며 [verdictOptions]는 고를 수 있는 판정 이름이다.
 * [countText]는 프로젝트가 리뷰어 수를 켰을 때 "승인 n/m"이고 [authorNote]는 작성자의 승인을 세지 않는다는 안내가 필요할 때 켠다.
 */
data class ReviewAreaView(
    val rows: List<ReviewerRow>, val formUrl: String?, val verdictOptions: List<String>, val countText: String?, val authorNote: Boolean,
    /** 리뷰 요청이 대기 중인 사람들(추가 항목 X-27). 같은 사람의 최신 판정 줄은 [rows]에서 빠진다 */
    val pending: List<PendingReviewRow> = emptyList(),
    /** 편집 권한자가 열린 풀 리퀘스트에서 리뷰를 요청할 수 있으면 요청 주소이다. 없으면 선택 상자를 보이지 않는다 */
    val requestUrl: String? = null, val candidatesUrl: String? = null,
    /** 내가 이 풀 리퀘스트에 쌓아 둔 임시 저장 리뷰의 줄 댓글 수이다(추가 항목 X-26, D-38). 0이면 임시 리뷰 칸을 보이지 않는다 */
    val pendingCount: Long = 0, val discardUrl: String? = null, val changesUrl: String? = null
)

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
    val compareUrl: String?, val commits: List<TimelineCommit>,
    /** 리뷰 판정의 본문이다(마크다운을 그린 HTML). 판정 이벤트에만 있다 */
    val bodyHtml: String? = null
)

/** 오른쪽 리뷰 카드 한 장이다 (v1.6 `partial_reviewlist`). */
data class PullRequestReviewCard(
    val href: String, val state: String, val outdated: Boolean, val content: String, val commentCount: Int, val ago: String, val agoTitle: String, val avatarUrl: String, val authorName: String? = null
)

/** 변경 내용의 커밋 선택 상자의 선택지이다. */
data class CommitChoice(val commitId: String, val shortId: String, val shortMessage: String, val url: String)
