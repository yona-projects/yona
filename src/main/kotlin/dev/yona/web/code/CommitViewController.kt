package dev.yona.web.code

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewThreadService
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.notification.NotificationReceivers
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.UserRepository
import dev.yona.markdown.AutoLinkRenderer
import dev.yona.repository.CommitHistoryService
import dev.yona.repository.CommitInfo
import dev.yona.repository.diff.DiffService
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.CodeAccessCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.HtmlUtils

/** 커밋 보기 오른쪽의 리뷰 카드 한 장이다. */
data class ReviewCard(val id: Long, val state: String, val content: String, val commentCount: Int, val avatarUrl: String, val ago: String, val agoTitle: String)

/**
 * 커밋 보기와 두 커밋 비교 화면이다. v1.6 `CodeHistoryApp.show`(`app/controllers/CodeHistoryApp.java:107-157`), `CompareApp.compare`와
 * `app/views/code/diff.scala.html`, `compare.scala.html`에 해당한다.
 *
 * 커밋 보기는 커밋 정보, 첫 부모와 비교한 파일별 차이(코드 리뷰 스레드를 줄 사이에 끼워 넣음), 파일 단위 스레드, 댓글 입력, 리뷰 카드 목록, 구독 단추를
 * 보여 준다. 커밋 메시지는 이스케이프한 뒤 자동 링크를 만든다([decisions.md] A-71).
 */
@Controller
@AnonymousCheck
class CommitViewController(
    private val projectService: ProjectService,
    private val history: CommitHistoryService,
    private val diffs: DiffService,
    private val threads: ReviewThreadService,
    private val diffHtml: DiffHtmlRenderer,
    private val threadHtml: ThreadHtmlRenderer,
    private val threadViews: ThreadViewAssembler,
    private val autoLinks: AutoLinkRenderer,
    private val users: UserRepository,
    private val userLookup: dev.yona.domain.user.UserLookup,
    private val avatars: AvatarService,
    private val receivers: NotificationReceivers,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val views: ProjectViewFactory,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls,
    private val messages: org.springframework.context.MessageSource
) {

    @GetMapping("/{user}/{project}/commit/{id}")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun show(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: String,
        @RequestParam(required = false) branch: String?, @RequestParam(required = false) path: String?
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val revision = UrlPaths.decodeSegmentOrSelf(id)
        val commit = history.commit(project, revision) ?: return ErrorViews.notFound("error.notfound.commit", views.of(project), null)
        val parent = history.parentOf(project, revision)
        val fileDiffs = diffs.commitDiff(project, revision) ?: return ErrorViews.notFound("error.notfound", views.of(project), null)
        val viewer = currentUserProvider.get().user
        val onCommit = threads.onCommit(project, commit.id)
        val codeThreads = onCommit.filterIsInstance<CodeCommentThread>()
        val base = siteUrls.path("/$owner/$name")
        val resource = ProjectResource("${project.id}:${commit.id}", ResourceType.COMMIT, project)
        val creatable = accessControl.isProjectResourceCreatable(viewer, project, ResourceType.COMMIT_COMMENT)
        val selectedBranch = branch?.ifBlank { null } ?: "HEAD"
        val selectedPath = path.orEmpty()
        val author = userLookup.findByEmail(commit.authorEmail)
        val watching = viewer != null && viewer.id in receivers.commitWatchers(project, commit.id, commit.authorEmail, false).mapNotNull { it.id }

        val commitUrl = "$base/commit/${commit.id}"
        return ModelAndView("code/diff")
            .addObject("title", "${messages.getMessage("code.commits", null, "code.commits", LocaleContextHolder.getLocale())} @${commit.id}")
            .addObject("projectView", views.of(project))
            .addObject("commit", commitView(commit, author))
            .addObject("shortMessageHtml", messageHtml(commit.shortMessage, project))
            .addObject("moreMessageHtml", commit.message.let { full -> if (full.split("\n").size > 1) messageHtml(full.removePrefix(full.substringBefore('\n') + "\n"), project) else "" })
            .addObject("diffHtml", diffHtml.render(fileDiffs, codeThreads, project, project, threadHtml))
            .addObject("nonRangedHtml", threadHtml.renderAll(onCommit.filterIsInstance<NonRangedCodeCommentThread>().sortedBy { it.createdDate }))
            .addObject("canComment", creatable)
            .addObject("commentAction", "$commitUrl/comments")
            .addObject("cards", onCommit.map { card(it) })
            .addObject("openCount", onCommit.count { it.state == ThreadState.OPEN })
            .addObject("closedCount", onCommit.count { it.state == ThreadState.CLOSED })
            .addObject("watching", watching)
            .addObject("watchUrl", siteUrls.path("/watch?resource.type=${resource.type.resource}&resource.id=${resource.id}"))
            .addObject("unwatchUrl", siteUrls.path("/unwatch?resource.type=${resource.type.resource}&resource.id=${resource.id}"))
            .addObject("listUrl", "$base/commits/${UrlPaths.routeSegment(selectedBranch)}/" + UrlPaths.encodeEach(selectedPath))
            .addObject("mentionUrl", "$base/mentionListAtCommitDiff?commitId=${commit.id}")
            .addObject("filesUrl", "$base/code")
            .addObject("commitsUrl", "$base/commits")
            .addObject("branchesUrl", "$base/branches")
            .addObject("parent", parent != null)
    }

    /** 두 커밋 사이의 차이를 보여 준다. */
    @GetMapping("/{user}/{project}/compare/{revA}..{revB}")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun compare(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable revA: String, @PathVariable revB: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val commitA = history.commit(project, revA)
        val commitB = history.commit(project, revB)
        if (commitA == null || commitB == null) return ErrorViews.notFound("error.notfound.commit", views.of(project), null)
        val fileDiffs = diffs.diff(project, revA, revB)
        return ModelAndView("code/compare")
            .addObject("title", "${commitA.id}..${commitB.id}")
            .addObject("projectView", views.of(project))
            .addObject("range", "${commitA.id}..${commitB.id}")
            .addObject("empty", fileDiffs.isEmpty())
            .addObject("diffHtml", diffHtml.render(fileDiffs, emptyList(), project, project) { "" })
    }

    private fun messageHtml(message: String, project: dev.yona.domain.project.Project): String = autoLinks.render(HtmlUtils.htmlEscape(message), project, null)

    private fun commitView(commit: CommitInfo, author: dev.yona.domain.user.User?): Map<String, Any?> = linkedMapOf(
        "id" to commit.id, "authorHref" to author?.let { "/${it.loginId}" }, "authorName" to (author?.name ?: commit.authorName),
        "avatarUrl" to (author?.let { avatars.avatarUrl(it, 32) } ?: avatars.gravatarUrl(commit.authorEmail, 32)), "known" to (author != null), "hasEmail" to commit.authorEmail.isNotEmpty(),
        "ago" to helper.agoOrDateString(commit.authorDate), "agoTitle" to helper.dateString(commit.authorDate)
    )

    private fun card(thread: dev.yona.domain.comment.CommentThread): ReviewCard {
        val first = thread.reviewComments.minByOrNull { it.createdDate ?: java.util.Date(0) }
        val author = thread.authorLoginId?.let { users.findByLoginId(it) }
        return ReviewCard(
            id = requireNotNull(thread.id), state = (thread.state ?: ThreadState.OPEN).name.lowercase(), content = first?.contents.orEmpty(),
            commentCount = if (thread.reviewComments.size > 1) thread.reviewComments.size else 0, avatarUrl = author?.let { avatars.avatarUrl(it, 32) }.orEmpty(),
            ago = helper.agoOrDateString(thread.createdDate), agoTitle = helper.dateString(thread.createdDate)
        )
    }
}
