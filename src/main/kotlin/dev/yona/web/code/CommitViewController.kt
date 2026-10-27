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
data class ReviewCard(val id: Long, val state: String, val content: String, val commentCount: Int, val avatarUrl: String, val ago: String, val agoTitle: String, val authorName: String? = null)

/**
 * Subversion 커밋 보기의 코멘트 한 건의 화면 값이다. [agoTitle]은 v1.6처럼 코멘트가 아니라 커밋의 시각이다.
 *
 * @property side 코멘트를 단 쪽(`A`, `B`). 없으면 빈 값
 * @property viaEmail 메일로 만든 코멘트이면 `true`
 * @property attachmentsJson 첨부 목록의 JSON(`{"attachments": [...]}`)
 */
data class SvnCommentView(
    val id: Long, val path: String, val side: String, val line: String, val authorLoginId: String, val authorName: String, val authorHref: String, val avatarUrl: String,
    val ago: String, val agoTitle: String, val bodyHtml: String, val viaEmail: Boolean, val attachmentsJson: String, val deletable: Boolean, val deleteUrl: String
)

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
    private val messages: org.springframework.context.MessageSource,
    private val svn: dev.yona.repository.svn.SvnRepositories,
    private val commitComments: dev.yona.domain.comment.CommitCommentRepository,
    private val markdown: dev.yona.markdown.ProjectMarkdownRenderer,
    private val attachmentListing: dev.yona.web.AttachmentListing,
    private val originalEmails: dev.yona.domain.notification.OriginalEmailRepository
) {

    private val json = tools.jackson.databind.json.JsonMapper.builder().build()

    @GetMapping("/{user}/{project}/commit/{id}")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun show(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: String,
        @RequestParam(required = false) branch: String?, @RequestParam(required = false) path: String?
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val revision = UrlPaths.decodeSegmentOrSelf(id)
        val commit = history.commit(project, revision) ?: return ErrorViews.notFound("error.notfound.commit")
        val parent = history.parentOf(project, revision)
        if (project.vcs == "Subversion") return showSvn(project, commit, parent, branch, path)
        val fileDiffs = diffs.commitDiff(project, revision) ?: return ErrorViews.notFound("error.notfound")
        val viewer = currentUserProvider.get().user
        val onCommit = threads.onCommit(project, commit.id)
        val codeThreads = onCommit.filterIsInstance<CodeCommentThread>()
        val base = siteUrls.path(UrlPaths.projectPath(owner, name))
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
            .addObject("moreMessageHtml", moreMessageHtml(commit.message, project))
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

    /**
     * Subversion 커밋 보기이다 (v1.6 `CodeHistoryApp.show`의 Subversion 분기와 `svnDiff.scala.html`). 패치는 화면의 스크립트(`code.SvnDiff`)가
     * 그리고, 커밋 코멘트와 구독 단추, 코멘트 입력란을 함께 보여 준다.
     */
    private fun showSvn(project: dev.yona.domain.project.Project, commit: CommitInfo, parent: CommitInfo?, branch: String?, path: String?): Any {
        val owner = project.owner.orEmpty()
        val name = project.name.orEmpty()
        val projectId = requireNotNull(project.id)
        val patch = svn.patch(owner, name, commit.id)
        val viewer = currentUserProvider.get().user
        val base = siteUrls.path(UrlPaths.projectPath(owner, name))
        val author = commit.authorName.takeIf { it.isNotEmpty() }?.let { users.findByLoginId(it) }
        val selectedBranch = branch?.ifBlank { null } ?: "HEAD"
        val selectedPath = path.orEmpty()
        val commitUrl = "$base/commit/${commit.id}"
        val resource = ProjectResource("$projectId:${commit.id}", ResourceType.COMMIT, project)
        val comments = commitComments.findByProjectIdAndCommitIdOrderByCreatedDateAscIdAsc(projectId, commit.id).map { c ->
            val commentId = requireNotNull(c.id)
            val writer = c.authorLoginId?.let { users.findByLoginId(it) }
            SvnCommentView(
                id = commentId, path = c.path.orEmpty(), side = c.side?.name.orEmpty(), line = c.line?.toString().orEmpty(), authorLoginId = c.authorLoginId.orEmpty(), authorName = c.authorName.orEmpty(),
                authorHref = "/${c.authorLoginId}", avatarUrl = writer?.let { avatars.avatarUrl(it) }.orEmpty(), ago = helper.agoOrDateString(c.createdDate),
                agoTitle = helper.dateString(commit.authorDate), bodyHtml = markdown.render(c.contents.orEmpty(), project),
                viaEmail = originalEmails.findByResourceTypeAndResourceId(ResourceType.COMMIT_COMMENT, commentId.toString()).isNotEmpty(),
                attachmentsJson = json.writeValueAsString(attachmentListing.fileList(ResourceType.COMMIT_COMMENT, commentId)),
                deletable = accessControl.isAllowed(viewer, dev.yona.access.ProjectResource(commentId.toString(), ResourceType.COMMIT_COMMENT, project, container = resource, authorId = c.authorId), Operation.DELETE),
                deleteUrl = "$commitUrl/comments/$commentId/delete"
            )
        }
        val watching = viewer != null && viewer.id in receivers.svnCommitWatchers(project, commit.id, commit.authorName, false).mapNotNull { it.id }
        return ModelAndView("code/svn-diff")
            .addObject("title", "${messages.getMessage("code.commits", null, "code.commits", LocaleContextHolder.getLocale())} @${commit.id}")
            .addObject("projectView", views.of(project))
            .addObject("commit", linkedMapOf<String, Any?>(
                "id" to commit.id, "message" to commit.message, "authorHref" to author?.let { "/${it.loginId}" }, "authorName" to (author?.name ?: commit.authorName.takeIf { it.isNotEmpty() }),
                "avatarUrl" to author?.let { avatars.avatarUrl(it) }, "known" to (author != null), "ago" to helper.agoOrDateString(commit.authorDate), "agoTitle" to helper.dateString(commit.authorDate)
            ))
            .addObject("patch", patch)
            .addObject("comments", comments)
            .addObject("canComment", accessControl.isProjectResourceCreatable(viewer, project, ResourceType.COMMIT_COMMENT))
            .addObject("commentAction", "$commitUrl/comments")
            .addObject("watching", watching)
            .addObject("watchUrl", siteUrls.path("/watch?resource.type=${resource.type.resource}&resource.id=${resource.id}"))
            .addObject("unwatchUrl", siteUrls.path("/unwatch?resource.type=${resource.type.resource}&resource.id=${resource.id}"))
            .addObject("listUrl", "$base/commits/${UrlPaths.routeSegment(selectedBranch)}/" + UrlPaths.encodeEach(selectedPath))
            .addObject("mentionUrl", "$base/mentionListAtCommitDiff?commitId=${commit.id}")
            .addObject("filesUrl", "$base/code")
            .addObject("commitsUrl", "$base/commits")
            .addObject("branches", svn.refNames().map { BranchOption("$base/commits/${UrlPaths.routeSegment(it)}/", it, it == selectedBranch) })
            .addObject("parentId", parent?.id.orEmpty())
    }

    /** 두 커밋 사이의 차이를 보여 준다. */
    @GetMapping("/{user}/{project}/compare/{revA}..{revB}")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun compare(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable revA: String, @PathVariable revB: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val commitA = history.commit(project, revA)
        val commitB = history.commit(project, revB)
        if (commitA == null || commitB == null) return ErrorViews.notFound("error.notfound.commit")
        if (project.vcs == "Subversion") {
            val patch = svn.patch(owner, name, commitA.id.toLong(), commitB.id.toLong())
            return ModelAndView("code/compare-svn")
                .addObject("title", "${commitA.id}..${commitB.id}")
                .addObject("projectView", views.of(project))
                .addObject("commitA", commitA.id).addObject("commitB", commitB.id)
                .addObject("patch", patch)
                .addObject("tplFileUrl", siteUrls.path("${UrlPaths.projectPath(owner, name)}/code/") + "\$%7BcommitId%7D/\${path}")
                .addObject("tplRawUrl", siteUrls.path("${UrlPaths.projectPath(owner, name)}/rawcode/") + "\$%7BcommitId%7D/\${path}")
        }
        val fileDiffs = diffs.diff(project, revA, revB)
        return ModelAndView("code/compare")
            .addObject("title", "${commitA.id}..${commitB.id}")
            .addObject("projectView", views.of(project))
            .addObject("range", "${commitA.id}..${commitB.id}")
            .addObject("empty", fileDiffs.isEmpty())
            .addObject("diffHtml", diffHtml.render(fileDiffs, emptyList(), project, project) { "" })
    }

    private fun messageHtml(message: String, project: dev.yona.domain.project.Project): String = autoLinks.render(HtmlUtils.htmlEscape(message), project, null)

    /** 첫 줄을 뺀 나머지이다. 자동 링크를 건 전체 메시지에서 첫 줄과 그 줄바꿈을 모두 지운다 (v1.6 `commitMsg.scala.html`). 한 줄뿐이면 빈 문자열이다. */
    private fun moreMessageHtml(message: String, project: dev.yona.domain.project.Project): String {
        val rendered = messageHtml(message, project)
        val lines = rendered.split("\n").dropLastWhile { it.isEmpty() }
        return if (lines.size > 1) rendered.replace(lines[0] + "\n", "") else ""
    }

    private fun commitView(commit: CommitInfo, author: dev.yona.domain.user.User?): Map<String, Any?> = linkedMapOf(
        "id" to commit.id, "authorHref" to author?.let { "/${it.loginId}" }, "authorName" to (author?.name ?: commit.authorName),
        "avatarUrl" to (author?.let { avatars.avatarUrl(it) } ?: avatars.gravatarUrl(commit.authorEmail, 32)), "known" to (author != null), "hasEmail" to true,
        "ago" to helper.agoOrDateString(commit.authorDate), "agoTitle" to helper.dateString(commit.authorDate)
    )

    private fun card(thread: dev.yona.domain.comment.CommentThread): ReviewCard {
        val first = thread.reviewComments.minByOrNull { it.createdDate ?: java.util.Date(0) }
        val author = thread.authorLoginId?.let { users.findByLoginId(it) }
        return ReviewCard(
            id = requireNotNull(thread.id), state = (thread.state ?: ThreadState.OPEN).name.lowercase(), content = first?.contents.orEmpty(),
            commentCount = if (thread.reviewComments.size > 1) thread.reviewComments.size else 0, avatarUrl = author?.let { avatars.avatarUrl(it) }.orEmpty(),
            ago = helper.agoOrDateString(thread.createdDate), agoTitle = helper.dateString(thread.createdDate), authorName = thread.authorName
        )
    }
}
