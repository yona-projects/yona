package dev.yona.web.issue

/** 화면에 보일 사람 한 명이다. */
data class PersonView(
    val loginId: String?, val name: String?, val displayName: String, val pureName: String, val avatarUrl: String, val email: String?
)

/** 한 줄 댓글(부모 댓글 아래에 달린 댓글)이다. */
data class ChildCommentView(
    val id: Long, val contentsHtml: String, val authorLoginId: String?, val authorPureName: String, val createdTitle: String, val createdAgo: String, val canDelete: Boolean
)

/** 댓글 한 개이다. 화면이 필요한 값과 현재 사용자의 권한을 미리 계산해 담는다. */
data class CommentView(
    val id: Long,
    val author: PersonView,
    val createdTitle: String,
    val createdAgo: String,
    val contents: String,
    val contentsHtml: String,
    val own: Boolean,
    val canRead: Boolean,
    val canUpdate: Boolean,
    val canDelete: Boolean,
    val voters: List<PersonView>,
    val votedByMe: Boolean,
    val viewerAnonymous: Boolean,
    val attachmentsJson: String,
    val attachments: List<Map<String, String>>,
    val viaEmail: Boolean,
    val childAnchorIds: List<Long>,
    val children: List<ChildCommentView>,
    val canReply: Boolean
)

/** 타임라인 이벤트 한 줄이다. [html]은 `li` 요소 전체이다. */
data class EventView(val id: Long, val html: String)

/** 타임라인의 한 항목이다. 댓글이나 이벤트 중 하나이다. */
data class TimelineEntry(val comment: CommentView?, val event: EventView?)

/** 오른쪽 창 아래의 댓글 색인 한 줄이다. */
data class IndexCommentView(
    val id: Long, val ellipsis: String, val mentioned: Boolean, val mentionedInChild: Boolean, val childCount: Int,
    val authorLoginId: String?, val authorPureName: String, val createdTitle: String, val createdAgo: String
)

/** 부모 이슈와 하위 이슈를 한 덩어리로 보이는 목록이다 (v1.6 `partial_view_childIssueList`). */
data class SubtaskView(
    val parentOwner: String, val parentProject: String, val parentNumber: Long, val parentTitle: String, val parentAssigneeName: String?,
    val parentState: String, val parentIsCurrent: Boolean, val percent: Int, val done: Int, val total: Int,
    val children: List<ChildIssueItem>
)

/** 이슈 보기 화면의 모델이다. */
data class IssueDetail(
    val id: Long, val number: Long, val title: String, val body: String, val bodyHtml: String?, val history: String?, val historyHtml: String?,
    val state: String, val stateKey: String, val draft: Boolean, val hasParent: Boolean, val weight: Int,
    val createdTitle: String, val createdAgo: String, val updatedTime: Long, val updatedByName: String?, val updatedAgo: String,
    val me: PersonView?, val author: PersonView?, val favorite: Boolean, val watching: Boolean, val watchable: Boolean,
    val canUpdate: Boolean, val canDelete: Boolean, val deletable: Boolean, val canVoteOrComment: Boolean,
    val voters: List<PersonView>, val votedByMe: Boolean, val votersExceptMe: List<PersonView>,
    val sharers: List<PersonView>, val sharerLoginIds: String, val sharedWithMe: Boolean,
    val parentIssueId: Long, val assignee: PersonView?, val milestoneId: Long?, val milestoneTitle: String?,
    val hasMilestones: Boolean, val openMilestones: List<MilestoneOption>, val closedMilestones: List<MilestoneOption>, val projectLabels: List<LabelGroupOption>,
    val dueDateString: String?, val dueDateText: String?, val dueOver: Boolean, val labels: List<IssueLabelTag>, val selectedLabelIds: Set<Long>,
    val attachmentsJson: String, val bodyChecksum: String, val numOfComments: Int, val nextStateName: String,
    val subtasks: SubtaskView?, val timeline: List<TimelineEntry>, val commentCount: Int, val index: List<IndexCommentView>
)
