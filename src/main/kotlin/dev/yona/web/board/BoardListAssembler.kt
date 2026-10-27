package dev.yona.web.board

import dev.yona.domain.board.Posting
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.TemplateHelper
import org.springframework.stereotype.Component

/** 게시글 목록의 줄을 만든다. v1.6 `board/partial_list.scala.html`이 계산하던 값이다. */
@Component
class BoardListAssembler(
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val helper: TemplateHelper,
    private val nullUser: dev.yona.web.NullUserDisplay
) {

    /** [posts]를 줄로 바꾼다. [viewer]의 언어로 작성자 이름을 보인다. 조직 게시판은 [withProject]로 프로젝트 이름도 담는다. */
    fun assemble(posts: List<Posting>, viewer: User?, withProject: Boolean = false): List<PostingListItem> {
        val authors = HashMap<String, User?>()
        return posts.map { post ->
            val author = post.authorLoginId?.let { login -> authors.getOrPut(login) { users.findByLoginIdIgnoreCase(login) } }
            val title = post.title.orEmpty()
            PostingListItem(
                number = requireNotNull(post.number), titleHeadersHtml = helper.headerWordsHtml(title), titleWithoutHeaders = helper.removeHeaderWords(title),
                notice = post.notice == true, readme = post.readme == true,
                authorLoginId = author?.loginId ?: dev.yona.web.NullUserDisplay.LOGIN_ID, authorName = author?.name ?: nullUser.name(),
                authorDisplayName = author?.let { UserDisplay.of(it, viewer?.lang) } ?: nullUser.name(),
                authorAvatarUrl = author?.let { avatars.avatarUrl(it) } ?: avatars.gravatarUrl("", 64),
                createdTitle = helper.dateString(post.createdDate), createdAgo = helper.agoOrDateString(post.createdDate),
                commentCount = post.numOfComments ?: 0,
                labels = post.labels.sortedBy { it.id }.map { PostLabelTag(requireNotNull(it.id), it.category?.id, it.name.orEmpty()) },
                owner = if (withProject) post.project?.owner else null, projectName = if (withProject) post.project?.name else null,
                title = title
            )
        }
    }
}
