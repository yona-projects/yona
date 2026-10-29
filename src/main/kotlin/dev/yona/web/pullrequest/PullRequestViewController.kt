package dev.yona.web.pullrequest

import dev.yona.web.AjaxRequests
import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestBranchService
import dev.yona.domain.pullrequest.PullRequestChangesService
import dev.yona.domain.pullrequest.PullRequestCommitRepository
import dev.yona.domain.pullrequest.PullRequestReviewService
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.pullrequest.closed
import dev.yona.domain.pullrequest.diffable
import dev.yona.domain.pullrequest.merged
import dev.yona.domain.pullrequest.open
import dev.yona.domain.notification.NotificationReceivers
import dev.yona.domain.user.User
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.FragmentRenderer
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.code.DiffHtmlRenderer
import dev.yona.web.code.ThreadHtmlRenderer
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.web.AttachmentJson
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import tools.jackson.databind.json.JsonMapper

/**
 * 풀 리퀘스트 보기, 상태 조각, 변경 내용, 리뷰이다. v1.6 `PullRequestApp.pullRequest`, `pullRequestState`, `pullRequestChanges`, `specificChange`
 * (`app/controllers/PullRequestApp.java:369-420`)와 `ReviewApp`에 해당한다. 모두 풀 리퀘스트 읽기 권한이 필요하고, 리뷰는 받아들일 권한이 필요하다.
 *
 * 보기 화면은 본문, 상태, 타임라인, 구독과 닫기 단추를 보여 준다. 상태 조각은 화면이 주기적으로 다시 불러 병합 검사 결과를 반영하며, Ajax 요청에는
 * 상태 값을 담은 JSON을 돌려준다. 변경 화면은 병합 기준 커밋 사이의 전체 변경이나 커밋 하나의 변경을 코드 리뷰 스레드와 함께 보여 준다.
 */
@Controller
@AnonymousCheck
class PullRequestViewController(
    private val projectService: ProjectService,
    private val pullRequestService: PullRequestService,
    private val branches: PullRequestBranchService,
    private val changes: PullRequestChangesService,
    private val reviews: PullRequestReviewService,
    private val commits: PullRequestCommitRepository,
    private val receivers: NotificationReceivers,
    private val assembler: PullRequestPageAssembler,
    private val meta: PullRequestMetaAssembler,
    private val diffHtml: DiffHtmlRenderer,
    private val threadHtml: ThreadHtmlRenderer,
    private val fragments: FragmentRenderer,
    private val markdown: ProjectMarkdownRenderer,
    private val attachmentListing: dev.yona.web.AttachmentListing,
    private val recentVisits: RecentVisitService,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val views: ProjectViewFactory,
    private val siteUrls: SiteUrls,
    private val history: dev.yona.repository.CommitHistoryService,
    private val pendingReader: dev.yona.domain.comment.PendingReviewReader
) {

    private val json = JsonMapper.builder().build()

    /**
     * 타임라인의 줄만 그린 조각이다. 담당자나 라벨을 바꾼 뒤 화면이 Ajax로 다시 불러온다(추가 항목 X-14, 이슈 화면의 타임라인 갱신과 같다). 읽기 권한이 필요하다.
     */
    @GetMapping("/{user}/{project}/pullRequest/{id}/timeline")
    @IsAllowed(Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun timeline(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long): Any {
        val (_, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        return ModelAndView("pullrequest/partial-timeline :: timeline").addObject("timeline", assembler.timeline(pullRequest))
    }

    @GetMapping("/{user}/{project}/pullRequest/{id}")
    @IsAllowed(Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun view(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest): Any {
        val (project, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val (canDelete, canRestore) = branchFlags(pullRequest)
        recentVisits.visits(viewer, project)

        val resource = ProjectResource(requireNotNull(pullRequest.id).toString(), ResourceType.PULL_REQUEST, project, authorId = pullRequest.contributor?.id)
        val watching = viewer != null && receivers.pullRequestWatchers(pullRequest, false).any { it.id == viewer.id }
        val base = assembler.baseUrl(pullRequest)
        val current = commits.findByPullRequestIdAndStateOrderByCreatedDesc(requireNotNull(pullRequest.id), dev.yona.domain.enumeration.PullRequestCommitState.CURRENT)
        return ModelAndView("pullrequest/view")
            .addObject("title", "menu.pullRequest")
            .addObject("projectView", views.of(project))
            .addObject("header", assembler.header(pullRequest, viewer, "overview"))
            .addObject("contributor", assembler.contributor(pullRequest))
            .addObject("branchInfo", assembler.branchInfo(pullRequest))
            .addObject("bodyHtml", markdown.render(pullRequest.body.orEmpty(), project))
            .addObject("attachments", attachmentListing.fileListJson(ResourceType.PULL_REQUEST, requireNotNull(pullRequest.id)))
            .addObject("state", assembler.state(pullRequest, viewer, canDelete, canRestore, request))
            .addObject("canWatch", accessControl.isAllowed(viewer, resource, Operation.WATCH))
            .addObject("watching", watching)
            .addObject("watchUrl", siteUrls.path("/watch?resource.type=${ResourceType.PULL_REQUEST.resource}&resource.id=${pullRequest.id}"))
            .addObject("unwatchUrl", siteUrls.path("/unwatch?resource.type=${ResourceType.PULL_REQUEST.resource}&resource.id=${pullRequest.id}"))
            .addObject("stateUrl", "$base/state")
            .addObject("canEdit", accessControl.isAllowed(viewer, resource, Operation.UPDATE))
            .addObject("reviewArea", assembler.reviewArea(pullRequest, viewer, accessControl.isAllowed(viewer, resource, Operation.ACCEPT), accessControl.isAllowed(viewer, resource, Operation.UPDATE)))
            .addObject("assigneeArea", meta.assignee(pullRequest, viewer, accessControl.isAllowed(viewer, resource, Operation.UPDATE)))
            .addObject("labelArea", meta.labels(pullRequest, accessControl.isAllowed(viewer, resource, Operation.UPDATE)))
            .addObject("editUrl", "$base/editform")
            .addObject("canClose", pullRequest.open && accessControl.isAllowed(viewer, resource, Operation.CLOSE))
            .addObject("closeUrl", "$base/close")
            .addObject("canReopen", pullRequest.closed && accessControl.isAllowed(viewer, resource, Operation.REOPEN))
            .addObject("reopenUrl", "$base/open")
            .addObject("timeline", assembler.timeline(pullRequest))
            .addObject("commentable", accessControl.isProjectResourceCreatable(viewer, project, ResourceType.REVIEW_COMMENT))
            .addObject("mentionUrl", mentionUrl(project, current.firstOrNull()?.commitId, pullRequest.id))
    }

    @GetMapping("/{user}/{project}/pullRequest/{id}/state")
    @IsAllowed(Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun state(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest): Any {
        val (_, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val (canDelete, canRestore) = branchFlags(pullRequest)
        val state = assembler.state(pullRequest, viewer, canDelete, canRestore, request)
        val isXhr = AjaxRequests.isXhr(request) && !AjaxRequests.isPjax(request)
        if (!isXhr) return ModelAndView("pullrequest/partial-state-page").addObject("state", state)

        val body = linkedMapOf<String, Any?>(
            "id" to id, "isOpen" to pullRequest.open, "isClosed" to pullRequest.closed, "isMerged" to pullRequest.merged, "isMerging" to pullRequest.isMerging,
            "isConflict" to pullRequest.isConflict, "canDeleteBranch" to canDelete, "canRestoreBranch" to canRestore,
            "html" to fragments.render("pullrequest/partial-state-page", mapOf("state" to state))
        )
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)
    }

    @GetMapping("/{user}/{project}/pullRequest/{id}/changes")
    @IsAllowed(Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun changes(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest): Any =
        changesView(owner, name, id, null, request)

    @GetMapping("/{user}/{project}/pullRequest/{id}/changes/{commitId}")
    @IsAllowed(Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun specificChange(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, @PathVariable commitId: String, request: HttpServletRequest
    ): Any = changesView(owner, name, id, commitId, request)

    private fun changesView(owner: String, name: String, id: Long, commitId: String?, request: HttpServletRequest): Any {
        val (project, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val diffs = if (pullRequest.diffable) changes.diff(pullRequest, commitId) ?: return ErrorViews.notFound("error.notfound.commit", views.of(project), null) else null
        // 내 임시 저장 리뷰(X-26, D-38)의 임시 스레드와 임시 답글을 읽어 변경 화면에 본인에게만 덧붙인다
        val pendingThreads = viewer?.let { pendingReader.threads(pullRequest, it) }.orEmpty()
        val pendingReplies = viewer?.let { pendingReader.replies(pullRequest, it) }.orEmpty()
        request.setAttribute(
            dev.yona.web.code.PendingOverlay.ATTRIBUTE,
            dev.yona.web.code.PendingOverlay(
                pendingReplies.groupBy { requireNotNull(requireNotNull(it.thread).id) }, pendingThreads.associate { requireNotNull(it.id) to pendingReader.threadComments(it) },
                assembler.baseUrl(pullRequest) + "/pendingReview/comments"
            )
        )
        val pendingCount = viewer?.let { pendingReader.count(pullRequest, it) } ?: 0L
        val codeThreads = if (pullRequest.diffable) changes.codeThreads(pullRequest, commitId, pendingThreads) else emptyList()
        val allThreads = pullRequestThreads(pullRequest)
        val resource = ProjectResource(requireNotNull(pullRequest.id).toString(), ResourceType.PULL_REQUEST, project, authorId = pullRequest.contributor?.id)
        val current = commits.findByPullRequestIdAndStateOrderByCreatedDesc(requireNotNull(pullRequest.id), dev.yona.domain.enumeration.PullRequestCommitState.CURRENT)
        val selected = commitId?.let { c -> commits.findByPullRequestId(requireNotNull(pullRequest.id)).filter { it.commitId == c }.maxByOrNull { it.created ?: java.util.Date(0) } }
        val base = assembler.baseUrl(pullRequest)
        val creatable = accessControl.isProjectResourceCreatable(viewer, project, ResourceType.REVIEW_COMMENT)
        val (canDelete, canRestore) = false to false
        // v1.6 viewChanges: 커밋을 골랐고 그 커밋이 이 요청의 커밋일 때만 작성자와 메시지를 보여 준다
        val commitInfo = commitId?.takeIf { c -> commits.findByPullRequestId(requireNotNull(pullRequest.id)).any { it.commitId == c } }
            ?.let { c -> history.commit(requireNotNull(pullRequest.toProject), c) }?.let { assembler.commitInfo(it) }
        return ModelAndView("pullrequest/changes")
            .addObject("title", "menu.pullRequest")
            .addObject("commitInfo", commitInfo)
            .addObject("projectView", views.of(project))
            .addObject("header", assembler.header(pullRequest, viewer, "changes"))
            .addObject("contributor", assembler.contributor(pullRequest))
            .addObject("branchInfo", assembler.branchInfo(pullRequest))
            .addObject("hasThreads", allThreads.isNotEmpty())
            .addObject("commitId", commitId)
            .addObject("selectedLabel", selectedLabel(commitId, selected))
            .addObject("choices", assembler.commitChoices(pullRequest))
            .addObject("allUrl", "$base/changes")
            .addObject("state", assembler.state(pullRequest, viewer, canDelete, canRestore, request))
            .addObject("diffHtml", diffs?.let { diffHtml.render(it, codeThreads, requireNotNull(pullRequest.toProject), requireNotNull(pullRequest.toProject), threadHtml) }.orEmpty())
            .addObject("nonRangedHtml", threadHtml.renderAll(changes.nonRangedThreads(pullRequest, commitId, pendingThreads), full = true))
            .addObject("pendingCount", pendingCount)
            .addObject("pendingDiscardUrl", "$base/pendingReview/discard")
            .addObject("overviewUrl", base)
            .addObject("commentable", creatable)
            .addObject("commentAction", siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/pullRequest/${pullRequest.id}/comments") + (commitId?.let { "?commitId=$it" } ?: ""))
            .addObject("openCards", assembler.cards(allThreads.filter { it.state == dev.yona.domain.enumeration.ThreadState.OPEN }))
            .addObject("closedCards", assembler.cards(allThreads.filter { it.state == dev.yona.domain.enumeration.ThreadState.CLOSED }))
            .addObject("openCount", allThreads.count { it.state == dev.yona.domain.enumeration.ThreadState.OPEN })
            .addObject("closedCount", allThreads.count { it.state == dev.yona.domain.enumeration.ThreadState.CLOSED })
            .addObject("mentionUrl", mentionUrl(project, current.firstOrNull()?.commitId, pullRequest.id))
            .addObject("canComment", creatable)
            .addObject("resourceAllowed", accessControl.isAllowed(viewer, resource, Operation.READ))
    }

    /** 커밋 선택 상자의 제목이다. 커밋이 현재 목록에 없으면(오래된 커밋 링크) 어긋났다고 알린다 (v1.6 `viewChanges`). */
    private fun selectedLabel(commitId: String?, selected: dev.yona.domain.pullrequest.PullRequestCommit?): Map<String, Any?> = when {
        commitId == null -> mapOf("kind" to "all")
        selected == null -> mapOf("kind" to "missing", "shortId" to commitId.take(SHORT_ID))
        else -> mapOf(
            "kind" to if (selected.state == dev.yona.domain.enumeration.PullRequestCommitState.PRIOR) "prior" else "current", "shortId" to commitId.take(SHORT_ID),
            "message" to selected.commitMessage.orEmpty().split("\n")[0]
        )
    }

    private fun pullRequestThreads(pullRequest: PullRequest) = pullRequestService.threads(pullRequest)

    private fun find(owner: String, name: String, id: Long): Pair<Project, PullRequest>? {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return null
        val pullRequest = pullRequestService.findOne(project, id) ?: return null
        return project to pullRequest
    }

    private fun branchFlags(pullRequest: PullRequest): Pair<Boolean, Boolean> =
        if (pullRequest.merged) branches.canDelete(pullRequest) to branches.canRestore(pullRequest) else false to false

    private companion object {
        const val SHORT_ID = 7
    }

    /** 멘션 후보 주소이다. 역방향 라우트는 기본값(빈 `commitId`)인 질의 값을 빼므로 커밋이 없으면 `?pullRequestId=번호`만 남는다. */
    private fun mentionUrl(project: Project, commitId: String?, pullRequestId: Long?): String =
        siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/mentionListAtPullRequest?" + (if (commitId.isNullOrEmpty()) "" else "commitId=$commitId&") + "pullRequestId=$pullRequestId")
}
