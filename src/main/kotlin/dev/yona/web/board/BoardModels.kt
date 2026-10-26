package dev.yona.web.board

/** 목록의 한 줄에 붙는 라벨이다. */
data class PostLabelTag(val id: Long, val categoryId: Long?, val name: String)

/** 게시글 목록의 한 줄이다. 화면이 필요한 값을 미리 계산해 담는다. */
data class PostingListItem(
    val number: Long, val titleHeadersHtml: String, val titleWithoutHeaders: String, val notice: Boolean, val readme: Boolean,
    val authorLoginId: String?, val authorName: String?, val authorDisplayName: String?, val authorAvatarUrl: String,
    val createdTitle: String, val createdAgo: String, val commentCount: Int, val labels: List<PostLabelTag>,
    val owner: String? = null, val projectName: String? = null,
    /** 말머리를 떼지 않은 제목 전체이다. 조직 게시판 목록이 그대로 보인다 (v1.6 `group_board_list_partial`의 `@post.title`). */
    val title: String = ""
)

/** 정렬 막대의 링크 한 개이다. */
data class SortLink(val field: String, val href: String, val active: Boolean, val descending: Boolean, val labelKey: String)

/** 게시글 보기 화면의 모델이다. */
data class PostDetail(
    val id: Long, val number: Long, val title: String, val body: String, val bodyHtml: String?, val historyHtml: String?,
    val createdTitle: String, val createdAgo: String, val authorLoginId: String?, val authorName: String?, val authorAvatarUrl: String,
    val viewerAnonymous: Boolean, val canUpdate: Boolean, val canDelete: Boolean, val canWatch: Boolean, val watching: Boolean,
    val canComment: Boolean, val labels: List<dev.yona.web.issue.IssueLabelTag>, val labelGroups: List<dev.yona.web.issue.LabelGroupOption>,
    val selectedLabelIds: Set<Long>, val hasProjectLabels: Boolean, val attachmentsJson: String,
    val comments: List<dev.yona.web.issue.CommentView>, val commentCount: Int
)
