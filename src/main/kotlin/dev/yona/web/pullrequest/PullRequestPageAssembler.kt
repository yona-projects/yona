package dev.yona.web.pullrequest

import dev.yona.access.AccessControl
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.PullRequestReviewAction
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestChangesService
import dev.yona.domain.pullrequest.PullRequestCommit
import dev.yona.domain.pullrequest.PullRequestCommitRepository
import dev.yona.domain.pullrequest.PullRequestEvent
import dev.yona.domain.pullrequest.PullRequestEventRepository
import dev.yona.domain.pullrequest.PullRequestEventService
import dev.yona.domain.pullrequest.acceptable
import dev.yona.domain.pullrequest.acceptableIgnoringReview
import dev.yona.domain.pullrequest.countedApprovals
import dev.yona.domain.pullrequest.effectiveVerdict
import dev.yona.domain.pullrequest.latestReviews
import dev.yona.domain.pullrequest.requiredReviewerCount
import dev.yona.domain.pullrequest.reviewBlocked
import dev.yona.domain.pullrequest.closed
import dev.yona.domain.pullrequest.disabledAcceptMessage
import dev.yona.domain.pullrequest.isReviewedBy
import dev.yona.domain.pullrequest.merged
import dev.yona.domain.pullrequest.open
import dev.yona.domain.pullrequest.reviewed
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserLookup
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import dev.yona.web.TemplateHelper
import dev.yona.web.code.CodePageAssembler
import dev.yona.web.project.RepositoryUrls
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

/**
 * 풀 리퀘스트 보기와 변경 화면의 값을 만든다. v1.6 `app/views/git/partial_info`, `partial_state`, `partial_branch`, `partial_pull_request_event`,
 * `partial_reviewlist`의 값 계산에 해당한다.
 */
@Component
class PullRequestPageAssembler(
    private val accessControl: AccessControl,
    private val projectUsers: ProjectUserRepository,
    private val threads: CommentThreadRepository,
    private val commits: PullRequestCommitRepository,
    private val eventRepository: PullRequestEventRepository,
    private val changes: PullRequestChangesService,
    private val reviewLinks: dev.yona.domain.comment.ReviewLinks,
    private val users: UserRepository,
    private val labelService: dev.yona.domain.issue.IssueLabelService,
    private val userLookup: UserLookup,
    private val avatars: AvatarService,
    private val repositoryUrls: RepositoryUrls,
    private val messages: MessageSource,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls,
    private val markdown: dev.yona.markdown.ProjectMarkdownRenderer,
    private val reviewRepository: dev.yona.domain.pullrequest.PullRequestReviewRepository
) {

    fun baseUrl(pullRequest: PullRequest): String {
        val project = requireNotNull(pullRequest.toProject)
        return siteUrls.path("${UrlPaths.projectPath(project.owner, project.name)}/pullRequest/${pullRequest.number}")
    }

    /**
     * 변경 화면에서 커밋 하나를 골랐을 때 보이는 작성자 정보이다 (v1.6 `viewChanges`의 `commitInfo`).
     * 작성자 이메일로 사용자를 찾으면 사용자 아바타와 이름, 못 찾으면 이메일의 Gravatar와 커밋의 이름이다. Git 커밋의 이메일은 비어 있어도 이메일 분기이다.
     */
    fun commitInfo(commit: dev.yona.repository.CommitInfo): Map<String, Any?> {
        val author = userLookup.findByEmail(commit.authorEmail)
        return linkedMapOf(
            "known" to (author != null), "authorHref" to author?.let { siteUrls.path("/${it.loginId}") }, "authorName" to (author?.name ?: commit.authorName),
            "avatarUrl" to (author?.let { avatars.avatarUrl(it) } ?: avatars.gravatarUrl(commit.authorEmail, AVATAR_SIZE)),
            "ago" to helper.agoOrDateString(commit.authorDate), "agoTitle" to helper.dateString(commit.authorDate), "message" to commit.message
        )
    }

    /** 공통 머리 영역이다. [activeTab]은 `overview` 또는 `changes`이다. */
    fun header(pullRequest: PullRequest, viewer: User?, activeTab: String): PullRequestHeader {
        val project = requireNotNull(pullRequest.toProject)
        val base = baseUrl(pullRequest)
        val resource = dev.yona.access.ProjectResource(requireNotNull(pullRequest.id).toString(), dev.yona.domain.enumeration.ResourceType.PULL_REQUEST, project, authorId = pullRequest.contributor?.id)
        val canAccept = accessControl.isAllowed(viewer, resource, Operation.ACCEPT)
        val conflict = pullRequest.isConflict == true
        val stateName = (pullRequest.state ?: dev.yona.domain.enumeration.State.OPEN).state

        val reviewers = if (project.isUsingReviewerCount == true && canAccept) {
            ReviewerArea(
                labelHtml = text("pullRequest.review.participants", pullRequest.countedApprovals),
                avatarsHtml = pullRequest.reviewers.filter { it.id != pullRequest.contributor?.id }.joinToString("") { avatarLink(it) },
                reviewUrl = if (pullRequest.open) "$base#review-form" else null,
                reviewButtonSuccess = !pullRequest.reviewed
            )
        } else null

        val accept = if (canAccept) {
            if (pullRequest.acceptable) AcceptButton("$base/accept", true, null)
            // 추가 항목(X-12, D-35 11번): 프로젝트 관리자와 사이트 관리자는 리뷰 조건에 막힌 요청도 병합할 수 있다
            else if (pullRequest.reviewBlocked && pullRequest.acceptableIgnoringReview && accessControl.canBypassReview(viewer, project)) AcceptButton("$base/accept", true, null, text("pullRequest.review.bypass.note"))
            else {
                val (key, args) = pullRequest.disabledAcceptMessage()
                AcceptButton("$base/accept", false, text(key, *args))
            }
        } else null

        return PullRequestHeader(
            number = requireNotNull(pullRequest.number), title = pullRequest.title.orEmpty(), ago = helper.agoOrDateString(pullRequest.created), agoTitle = helper.dateString(pullRequest.created),
            badgeClass = if (conflict) "badge-issue-conflict" else "badge-issue-$stateName", badgeKey = if (conflict) "pullRequest.state.conflict" else "pullRequest.state.$stateName",
            reviewers = reviewers, accept = accept, overviewUrl = base, changesUrl = "$base/changes",
            openThreadCount = threads.countByPullRequestIdAndState(requireNotNull(pullRequest.id), ThreadState.OPEN), activeTab = activeTab
        )
    }

    /**
     * 개요 화면의 리뷰 영역이다(추가 개발 항목 X-12, `decisions.md` D-35). 리뷰어별 최신 판정 줄과 판정 폼으로 이루어진다.
     * 폼은 로그인한 사용자에게만 보이고, 승인과 변경 요청은 받아들일 권한이 있는 작성자 아닌 사람이 열린 요청에만 고를 수 있다.
     */
    fun reviewArea(pullRequest: PullRequest, viewer: User?, canAccept: Boolean): ReviewAreaView {
        val project = requireNotNull(pullRequest.toProject)
        val base = baseUrl(pullRequest)
        val rows = pullRequest.latestReviews.values.sortedWith(compareBy({ it.createdDate }, { it.id })).map { review ->
            val reviewer = review.authorId?.let { id -> users.findById(id).orElse(null) }
            val verdict = review.effectiveVerdict ?: dev.yona.domain.enumeration.ReviewVerdict.COMMENTED
            ReviewerRow(
                loginId = review.authorLoginId.orEmpty(), name = reviewer?.name ?: review.authorLoginId.orEmpty(), href = siteUrls.path("/${review.authorLoginId}"),
                avatarUrl = reviewer?.let { avatars.avatarUrl(it) } ?: dev.yona.security.AvatarService.DEFAULT_AVATAR_URL, verdict = verdict.name, dismissed = review.dismissedDate != null,
                dismissedReason = review.dismissedReason, systemDismissed = review.dismissedDate != null && review.dismissedByLoginId == null,
                dismissUrl = if (canAccept && review.dismissedDate == null && review.verdict != dev.yona.domain.enumeration.ReviewVerdict.COMMENTED) "$base/reviews/${review.id}/dismissals" else null,
                ago = helper.agoOrDateString(review.createdDate), agoTitle = helper.dateString(review.createdDate)
            )
        }
        val author = viewer != null && viewer.id == pullRequest.contributor?.id
        val options = if (viewer == null) emptyList() else buildList {
            add(dev.yona.domain.enumeration.ReviewVerdict.COMMENTED.name)
            if (canAccept && !author && pullRequest.open) { add(dev.yona.domain.enumeration.ReviewVerdict.APPROVED.name); add(dev.yona.domain.enumeration.ReviewVerdict.CHANGES_REQUESTED.name) }
        }
        return ReviewAreaView(
            rows = rows, formUrl = if (viewer != null) "$base/reviews" else null, verdictOptions = options,
            countText = if (project.isUsingReviewerCount == true) text("pullRequest.review.count", pullRequest.countedApprovals, pullRequest.requiredReviewerCount) else null,
            authorNote = project.isUsingReviewerCount == true && pullRequest.reviewers.any { it.id == pullRequest.contributor?.id }
        )
    }

    fun contributor(pullRequest: PullRequest): ContributorView {
        val user = requireNotNull(pullRequest.contributor)
        return ContributorView(siteUrls.path("/${user.loginId}"), avatars.avatarUrl(user), user.name.orEmpty(), user.loginId.orEmpty())
    }

    fun branchInfo(pullRequest: PullRequest): BranchInfoView {
        val from = requireNotNull(pullRequest.fromProject)
        val to = requireNotNull(pullRequest.toProject)
        fun code(project: Project, branch: String) = siteUrls.path("${UrlPaths.projectPath(project.owner, project.name)}/code/${UrlPaths.routeSegment(branch)}")  // 역방향 라우트는 경로가 비면 끝에 `/`가 없다
        return BranchInfoView(
            siteUrls.path("/${from.owner}"), from.owner.orEmpty(), siteUrls.path(UrlPaths.projectPath(from.owner, from.name)), from.name.orEmpty(), code(from, pullRequest.fromBranch.orEmpty()),
            CodePageAssembler.branchItemName(pullRequest.fromBranch.orEmpty()),
            siteUrls.path("/${to.owner}"), to.owner.orEmpty(), siteUrls.path(UrlPaths.projectPath(to.owner, to.name)), to.name.orEmpty(), code(to, pullRequest.toBranch.orEmpty()),
            CodePageAssembler.branchItemName(pullRequest.toBranch.orEmpty())
        )
    }

    /** 상태 조각이다. [canDelete], [canRestore]는 병합된 요청의 보낸 브랜치 삭제, 복원 가능 여부이다. */
    fun state(pullRequest: PullRequest, viewer: User?, canDelete: Boolean, canRestore: Boolean, request: HttpServletRequest): PullRequestStateView {
        val base = baseUrl(pullRequest)
        val to = requireNotNull(pullRequest.toProject)
        val isContributor = viewer != null && viewer.id == pullRequest.contributor?.id
        if (pullRequest.isMerging == true) return PullRequestStateView("merging", null, null, null, null)
        if (pullRequest.merged) {
            val receiver = pullRequest.receiver
            val view = receiver?.let { ReceiverView(siteUrls.path("/${it.loginId}"), avatars.avatarUrl(it, RECEIVER_AVATAR), it.name.orEmpty(), it.loginId.orEmpty()) }
            val actions = if (isContributor) {
                BranchActions(if (canDelete) "$base/deletefrombranch" else null, if (canRestore) "$base/restorefrombranch" else null, pullRequest.fromBranch.orEmpty())
            } else null
            return PullRequestStateView("merged", null, view, receiver?.let { text("pullRequest.merged.the.pullrequest", it.name, siteUrls.path("/${it.loginId}")) }, actions)
        }
        if (pullRequest.closed) return PullRequestStateView("none", null, null, null, null)
        if (pullRequest.isConflict == false) return PullRequestStateView("safe", null, null, null, null)
        val help = if (isContributor) {
            // v1.6는 인수를 거꾸로 넘겨 (프로젝트 번호, 사용자 번호) 구성원 여부를 보므로 대개 로그인 ID가 주소에 들어가지 않는다 (R-43).
            val member = viewer?.id?.let { projectUsers.existsByUserIdAndProjectId(requireNotNull(to.id), it) } == true
            ConflictHelp(
                CodePageAssembler.branchItemName(pullRequest.fromBranch.orEmpty()), CodePageAssembler.branchItemName(pullRequest.toBranch.orEmpty()),
                repositoryUrls.urlFor(to, request, if (member) viewer?.loginId else null).orEmpty(), base
            )
        } else null
        return PullRequestStateView("conflict", help, null, null, null)
    }

    /** 타임라인이다. 새 풀 리퀘스트 이벤트처럼 화면에 나오지 않는 종류는 뺀다. */
    fun timeline(pullRequest: PullRequest): List<TimelineEvent> =
        eventRepository.findByPullRequestIdOrderByCreatedAscIdAsc(requireNotNull(pullRequest.id)).mapNotNull { event(pullRequest, it) }

    private fun event(pullRequest: PullRequest, event: PullRequestEvent): TimelineEvent? {
        val sender = event.senderLoginId?.let { users.findByLoginId(it) }
        val user = userHtml(sender, event.senderLoginId)
        val ago = helper.agoOrDateString(event.created)
        val agoTitle = helper.dateString(event.created)
        val id = requireNotNull(event.id)
        val to = requireNotNull(pullRequest.toProject)
        val new = event.newValue
        return when (event.eventType) {
            EventType.PULL_REQUEST_REVIEW_STATE_CHANGED ->
                if (new == PullRequestReviewAction.DONE.name) TimelineEvent(id, "changed", "pullRequest.review", text("notification.pullrequest.reviewed", user), ago, agoTitle, null, emptyList())
                else TimelineEvent(id, "changed", "pullRequest.unreview", text("notification.pullrequest.unreviewed", user), ago, agoTitle, null, emptyList())
            EventType.PULL_REQUEST_STATE_CHANGED ->
                if (new == "merged") {
                    val commit = pullRequest.mergedCommitIdTo.orEmpty()
                    val link = "<a class=\"link\" href=\"${siteUrls.path("${UrlPaths.projectPath(to.owner, to.name)}/commit/$commit")}\" title=\"${HtmlUtils.htmlEscape(text("code.showCommit"))}\">${commit.take(SHORT_ID)}</a>"
                    TimelineEvent(id, new, "pullRequest.event.$new", text("pullRequest.event.message.$new", user, link), ago, agoTitle, null, emptyList())
                } else TimelineEvent(id, new.orEmpty(), "pullRequest.event.$new", text("pullRequest.event.message.$new", user), ago, agoTitle, null, emptyList())
            EventType.PULL_REQUEST_MERGED ->
                TimelineEvent(id, new.orEmpty(), "pullRequest.event.$new", text("pullRequest.event.message.$new", user), ago, agoTitle, null, emptyList())
            EventType.PULL_REQUEST_COMMIT_CHANGED -> {
                val compare = event.oldValue?.split(PullRequestEventService.DELIMITER)?.let { siteUrls.path("${UrlPaths.projectPath(to.owner, to.name)}/compare/${it[0]}..${it[1]}") }
                TimelineEvent(id, "changed", "pullRequest.event.commit", text("pullRequest.event.message.commit", user), ago, agoTitle, compare, commitsOf(pullRequest, event))
            }
            // 추가 항목 X-14(D-34): 담당자와 라벨 변경
            EventType.PULL_REQUEST_ASSIGNEE_CHANGED -> {
                val assigned = event.newValue
                val target = (assigned ?: event.oldValue)?.let { login -> userHtml(users.findByLoginId(login), login) }.orEmpty()
                TimelineEvent(id, "changed", "pullRequest.event.assignee", text(if (assigned != null) "pullRequest.event.message.assigned" else "pullRequest.event.message.unassigned", user, target), ago, agoTitle, null, emptyList())
            }
            EventType.PULL_REQUEST_LABEL_CHANGED -> {
                val adding = event.oldValue.isNullOrBlank() && !event.newValue.isNullOrBlank()
                val box = labelBox(if (adding) event.newValue else event.oldValue, to)
                TimelineEvent(id, if (adding) "label-added" else "label-deleted", if (adding) "issue.event.label.added.title" else "issue.event.label.deleted.title", text(if (adding) "issue.event.label.added" else "issue.event.label.deleted", user, box), ago, agoTitle, null, emptyList())
            }
            // 추가 항목 X-12(D-35): 리뷰 판정을 제출했거나 해제했다
            EventType.PULL_REQUEST_REVIEW_SUBMITTED -> {
                val review = new?.toLongOrNull()?.let { reviewRepository.findById(it).orElse(null) }
                val verdict = (event.oldValue ?: review?.verdict?.name).orEmpty().lowercase()
                TimelineEvent(
                    id, "review-$verdict", "pullRequest.review.state.$verdict", text("pullRequest.event.message.review.$verdict", user), ago, agoTitle, null, emptyList(),
                    bodyHtml = review?.contents?.takeIf { it.isNotBlank() }?.let { markdown.render(it, to) }
                )
            }
            EventType.PULL_REQUEST_REVIEW_DISMISSED -> {
                val review = new?.toLongOrNull()?.let { reviewRepository.findById(it).orElse(null) }
                val target = event.oldValue?.let { login -> userHtml(users.findByLoginId(login), login) }.orEmpty()
                val system = event.senderLoginId == null
                TimelineEvent(
                    id, "review-dismissed", "pullRequest.review.state.dismissed",
                    if (system) text("pullRequest.event.message.review.dismissed.stale", target) else text("pullRequest.event.message.review.dismissed", user, target), ago, agoTitle, null, emptyList(),
                    bodyHtml = if (system) null else review?.dismissedReason?.takeIf { it.isNotBlank() }?.let { HtmlUtils.htmlEscape(it) }
                )
            }
            EventType.PULL_REQUEST_REVIEW_RULE_NOTICE ->
                TimelineEvent(id, "review-notice", "pullRequest.review.state.notice", text("pullRequest.review.rule.notice"), ago, agoTitle, null, emptyList())
            else -> null
        }
    }

    /** `분류 - 이름 색` 형태의 값을 색 상자로 바꾼다. 형식이 다르거나 라벨이 지워졌으면 이름(또는 값)만 보인다(이슈 라벨 이벤트와 같다). */
    private fun labelBox(value: String?, project: dev.yona.domain.project.Project): String {
        val text = value.orEmpty()
        val parts = text.split(" - ")
        if (parts.size != 2) return HtmlUtils.htmlEscape(text)
        val category = parts[0].trim()
        val name = parts[1].split(" #")[0].trim()
        val label = labelService.labels(project).firstOrNull { it.category?.name == category && it.name == name } ?: return HtmlUtils.htmlEscape(name)
        return "<div class='label issue-label' style='background-color: ${HtmlUtils.htmlEscape(label.color.orEmpty())}'>${HtmlUtils.htmlEscape(name)}</div>"
    }

    private fun commitsOf(pullRequest: PullRequest, event: PullRequestEvent): List<TimelineCommit> {
        val to = requireNotNull(pullRequest.toProject)
        return event.newValue.orEmpty().split(PullRequestEventService.DELIMITER).mapNotNull { it.toLongOrNull()?.let { id -> commits.findById(id).orElse(null) } }
            .sortedByDescending { it.created }.map { commit ->
                val author = userLookup.findByEmail(commit.authorEmail)
                val message = commit.commitMessage.orEmpty()
                val short = message.split("\n")[0]
                val url = siteUrls.path("${UrlPaths.projectPath(to.owner, to.name)}/pullRequest/${pullRequest.number}/changes/${commit.commitId}")
                TimelineCommit(
                    commitUrl = url, shortId = commit.commitShortId.orEmpty(), outdated = commit.state == PullRequestCommitState.PRIOR,
                    authorHref = author?.let { siteUrls.path("/${it.loginId}") }, authorName = author?.name,
                    avatarUrl = author?.let { avatars.avatarUrl(it) } ?: avatars.gravatarUrl(commit.authorEmail, AVATAR_SIZE), authorEmail = commit.authorEmail.orEmpty(),
                    date = helper.agoOrDateString(commit.authorDate), dateTitle = helper.dateString(commit.authorDate), shortMessageHtml = HtmlUtils.htmlEscape(short),
                    hasMore = message.split("\n").dropLastWhile { it.isEmpty() }.size > 1, moreHtml = HtmlUtils.htmlEscape(message.replace(short + "\n", ""))
                )
            }
    }

    /** 변경 화면 오른쪽의 리뷰 카드이다. 스레드의 첫 댓글을 내용으로 쓴다. */
    fun cards(threadList: List<CommentThread>): List<PullRequestReviewCard> = threadList.map { thread ->
        val first = thread.reviewComments.minByOrNull { it.createdDate ?: java.util.Date(0) }
        val author = thread.authorLoginId?.let { users.findByLoginId(it) }
        PullRequestReviewCard(
            href = threadUrl(thread), state = (thread.state ?: ThreadState.OPEN).name.lowercase(), outdated = (thread as? CodeCommentThread)?.let { changes.isOutdated(it) } == true,
            content = first?.contents.orEmpty(), commentCount = (thread.reviewComments.size - 1).coerceAtLeast(0), ago = helper.agoOrDateString(thread.createdDate),
            agoTitle = helper.dateString(thread.createdDate), avatarUrl = author?.let { avatars.avatarUrl(it) }.orEmpty(), authorName = thread.authorName
        )
    }

    /** 스레드가 보이는 화면 주소와 `#thread-번호`이다 (v1.6 `urlToCommentThread`). */
    fun threadUrl(thread: CommentThread): String = reviewLinks.thread(thread)

    /** 변경 내용의 커밋 선택 상자에 나올 현재 커밋 목록이다. */
    fun commitChoices(pullRequest: PullRequest): List<CommitChoice> {
        val to = requireNotNull(pullRequest.toProject)
        return commits.findByPullRequestId(requireNotNull(pullRequest.id)).filter { it.state == PullRequestCommitState.CURRENT }.map {
            CommitChoice(it.commitId.orEmpty(), it.commitShortId.orEmpty(), it.commitMessage.orEmpty().split("\n")[0], siteUrls.path("${UrlPaths.projectPath(to.owner, to.name)}/pullRequest/${pullRequest.number}/changes/${it.commitId}"))
        }
    }

    private fun avatarLink(user: User): String =
        "<a href=\"${siteUrls.path("/${user.loginId}")}\" class=\"usf-group\" data-toggle=\"tooltip\" data-placement=\"top\" title=\"${HtmlUtils.htmlEscape(user.name.orEmpty())}\">" +
            "<img src=\"${avatars.avatarUrl(user)}\" class=\"avatar-wrap small\"></a>"

    private fun userHtml(user: User?, loginId: String?): String {
        if (user == null) return "Anonymous"
        val id = HtmlUtils.htmlEscape(loginId.orEmpty())
        val href = siteUrls.path("/$loginId")
        val name = HtmlUtils.htmlEscape(UserDisplay.of(user, LocaleContextHolder.getLocale().toLanguageTag()))
        return "<a href=\"$href\" class=\"usf-group\" data-toggle=\"tooltip\" data-placement=\"top\" title=\"$id\"><img src=\"${avatars.avatarUrl(user)}\" class=\"avatar-wrap small\"></a>" +
            "\n        <a href=\"$href\" class=\"usf-group\" data-toggle=\"tooltip\" data-placement=\"top\" title=\"$id\">\n            <strong>$name</strong>\n        </a>"
    }

    private fun text(key: String, vararg args: Any?): String = messages.getMessage(key, args.map { it ?: "null" }.toTypedArray(), key, LocaleContextHolder.getLocale()).orEmpty()

    private companion object {
        const val SHORT_ID = 7
        const val AVATAR_SIZE = 32
        const val RECEIVER_AVATAR = 64
    }
}
