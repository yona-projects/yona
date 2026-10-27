package dev.yona.web.search

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewLinks
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneStatusService
import dev.yona.domain.project.Project
import dev.yona.domain.search.SearchSnippets
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.TemplateHelper
import dev.yona.web.project.ProjectViewFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 검색 결과 엔티티를 화면 행으로 만든다. v1.6 `app/views/search/partial_*.scala.html`이 템플릿 안에서 하던 조회와 계산을 묶은 것이다.
 *
 * 본문 발췌는 키워드 앞뒤 40자이다([SearchSnippets]).
 */
@Component
class SearchRowAssembler(
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val helper: TemplateHelper,
    private val projectViews: ProjectViewFactory,
    private val milestoneStatuses: MilestoneStatusService,
    private val reviewLinks: ReviewLinks,
    private val messages: MessageSource
) {

    /**
     * 종류에 맞는 행 목록이다.
     *
     * @param projectScoped 프로젝트 범위이면 `true`이다. 행에서 프로젝트 링크를 뺀다
     */
    fun rows(items: List<Any>, keyword: String, projectScoped: Boolean, viewer: User?): List<Any> =
        items.map { item ->
            when (item) {
                is Issue -> issue(item, keyword, projectScoped, viewer)
                is Posting -> posting(item, keyword, projectScoped, viewer)
                is IssueComment -> issueComment(item, keyword, projectScoped, viewer)
                is PostingComment -> postingComment(item, keyword, projectScoped, viewer)
                is ReviewComment -> review(item, keyword, projectScoped, viewer)
                is Milestone -> milestone(item, keyword, projectScoped)
                is User -> user(item)
                is Project -> project(item)
                else -> throw IllegalArgumentException("지원하지 않는 검색 결과입니다: ${item.javaClass}")
            }
        }

    private fun snippets(text: String?, keyword: String): List<SearchSnippet> =
        SearchSnippets.make(text, keyword, THRESHOLD).map { SearchSnippet(it, it.length < text.orEmpty().length) }

    private fun label(project: Project?, scoped: Boolean) = if (scoped || project == null) null else "${project.owner}/${project.name}"

    private fun url(project: Project?, path: String) = "${dev.yona.util.UrlPaths.projectPath(project?.owner, project?.name)}$path"

    private fun message(key: String): String = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key

    /**
     * 작성자의 로그인 ID와 표시 이름이다. v1.6 `User.findByLoginId`는 사용자가 없으면 `NullUser`(이름 `user.notExists.name`, 로그인 ID 빈 문자열)를
     * 돌려주므로 그 이름과 빈 로그인 ID로 링크를 그린다. 사용자는 있는데 이름이 비어 있으면 표시 이름이 없다(`noAuthor` 문구).
     */
    private fun author(loginId: String?, viewer: User?): Pair<String, String?> {
        val found = loginId?.let { users.findByLoginIdIgnoreCase(it) } ?: return "" to (messages.getMessage("user.notExists.name", null, "user.notExists.name", java.util.Locale.getDefault()) ?: "user.notExists.name")
        return found.loginId.orEmpty() to found.takeIf { !it.name.isNullOrEmpty() }?.let { UserDisplay.of(it, viewer?.lang) }
    }

    private fun row(
        postId: String?, titleUrl: String?, titleText: String?, titleClass: Boolean, contents: String?, keyword: String, snippetsUrl: String?,
        project: Project?, scoped: Boolean, authorLoginId: String?, created: java.util.Date?, viewer: User?, noAuthorKey: String = "issue.noAuthor"
    ): SearchItemRow {
        val (userLoginId, name) = author(authorLoginId, viewer)
        return SearchItemRow(
            postId = postId, titleUrl = titleUrl, titleText = titleText, titleClass = titleClass, snippets = snippets(contents, keyword), snippetsUrl = snippetsUrl,
            projectLabel = label(project, scoped), projectUrl = project?.let { url(it, "") },
            authorLoginId = userLoginId, authorName = name, noAuthorText = if (noAuthorKey == "posting.noAuthor") noAuthorKey else message(noAuthorKey),
            createdTitle = helper.dateString(created), createdText = helper.agoOrDateString(created)
        )
    }

    private fun issue(issue: Issue, keyword: String, scoped: Boolean, viewer: User?) = row(
        "#${issue.number}", url(issue.project, "/issue/${issue.number}"), issue.title, true, issue.body, keyword, null, issue.project, scoped,
        issue.authorLoginId, issue.createdDate, viewer
    )

    private fun posting(post: Posting, keyword: String, scoped: Boolean, viewer: User?) = row(
        "#${post.number}", url(post.project, "/post/${post.number}"), post.title, true, post.body, keyword, null, post.project, scoped,
        post.authorLoginId, post.createdDate, viewer
    )

    private fun issueComment(comment: IssueComment, keyword: String, scoped: Boolean, viewer: User?): SearchItemRow {
        val issue = requireNotNull(comment.issue)
        return row(
            "#${issue.number}", url(issue.project, "/issue/${issue.number}") + "#comment-${comment.id}", "Re) ${issue.title}", false, comment.contents, keyword, null,
            issue.project, scoped, comment.authorLoginId, comment.createdDate, viewer
        )
    }

    /** v1.6은 이 줄에서만 `posting.noAuthor`를 쓰는데 메시지 파일에 없어 키 이름이 그대로 보인다. */
    private fun postingComment(comment: PostingComment, keyword: String, scoped: Boolean, viewer: User?): SearchItemRow {
        val post = requireNotNull(comment.posting)
        return row(
            "#${post.number}", url(post.project, "/post/${post.number}") + "#comment-${comment.id}", "Re) ${post.title}", false, comment.contents, keyword, null,
            post.project, scoped, comment.authorLoginId, comment.createdDate, viewer, noAuthorKey = "posting.noAuthor"
        )
    }

    private fun review(review: ReviewComment, keyword: String, scoped: Boolean, viewer: User?): SearchItemRow {
        val thread = requireNotNull(review.thread)
        val link = reviewLinks.relativeThread(thread)
        val pullRequest = thread.pullRequest
        return row(
            pullRequest?.let { "#${it.number}" }, if (pullRequest != null) link else null, pullRequest?.let { "Re) ${it.title}" }, false,
            review.contents, keyword, if (pullRequest == null) link else null, thread.project, scoped, review.authorLoginId, review.createdDate, viewer
        )
    }

    private fun milestone(milestone: Milestone, keyword: String, scoped: Boolean): SearchMilestoneRow {
        val status = milestoneStatuses.statusOf(milestone)
        return SearchMilestoneRow(
            url = url(milestone.project, "/milestone/${milestone.id}"), title = milestone.title.orEmpty(), snippets = snippets(milestone.contents, keyword),
            projectLabel = label(milestone.project, scoped), projectUrl = milestone.project?.let { url(it, "") },
            dueDateString = status.dueDateString, until = status.until
        )
    }

    private fun user(user: User) = SearchUserRow(
        loginId = user.loginId.orEmpty(), name = user.name.orEmpty(), avatarUrl = avatars.avatarUrl(user, AVATAR_SIZE),
        since = user.createdDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.US).format(it) }.orEmpty()
    )

    private fun project(project: Project): SearchProjectRow {
        val original = project.originalProject
        return SearchProjectRow(
            owner = project.owner.orEmpty(), name = project.name.orEmpty(), logoUrl = projectViews.of(project).logoUrl,
            originalOwner = original?.owner, originalName = original?.name, overview = project.overview,
            createdTitle = helper.dateString(project.createdDate), createdText = helper.agoOrDateString(project.createdDate),
            lastPushedTitle = project.lastPushedDate?.let { helper.dateString(it) }, lastPushedText = project.lastPushedDate?.let { helper.agoOrDateString(it) }
        )
    }

    private companion object {
        const val THRESHOLD = 40
        const val AVATAR_SIZE = 64
    }
}
