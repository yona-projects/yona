package dev.yona.web.user

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserCredentialRepository
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.ProviderLogos
import dev.yona.web.TemplateHelper
import dev.yona.web.issue.IssueListAssembler
import dev.yona.web.project.ProjectViewFactory
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * 사용자 페이지의 프로필 상자와 활동 탭(이슈, 풀 리퀘스트, 프로젝트)에 담을 값을 만든다. v1.6 `UserApp.userInfo`의 조회와
 * `app/views/user/view.scala.html`, `partial_issues`, `partial_pullRequests`, `partial_projectlist`가 템플릿 안에서 하던 조회를 묶은 것이다.
 *
 * 활동은 보는 사람이 프로젝트를 읽을 수 있을 때만 담는다. 같은 프로젝트의 권한 검사는 한 번만 한다.
 */
@Component
class UserPageAssembler(
    private val accessControl: AccessControl,
    private val issues: IssueRepository,
    private val pullRequests: PullRequestRepository,
    private val projects: ProjectRepository,
    private val projectUsers: ProjectUserRepository,
    private val users: UserRepository,
    private val credentials: UserCredentialRepository,
    private val linkedAccounts: LinkedAccountRepository,
    private val siteAdmins: SiteAdminRepository,
    private val commentThreads: CommentThreadRepository,
    private val issueItems: IssueListAssembler,
    private val projectViews: ProjectViewFactory,
    private val avatars: AvatarService,
    private val helper: TemplateHelper,
    private val clock: Clock,
    private val nullUser: dev.yona.web.NullUserDisplay
) {

    /** 프로필 상자의 값이다. [viewer]는 보는 사람이고 익명이면 `null`이다. */
    fun profile(user: User, viewer: User?, showEmail: Boolean): UserProfileView = UserProfileView(
        loginId = user.loginId.orEmpty(), name = user.name.orEmpty(), englishName = user.englishName,
        email = if (showEmail) user.email else null,
        avatarUrl = avatars.avatarUrl(user, AVATAR_SIZE), guest = user.isGuest == true,
        siteManager = siteAdmins.existsByAdminId(requireNotNull(user.id)), locked = user.state == UserState.LOCKED || user.state == UserState.DELETED,
        since = user.createdDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.US).format(it) }.orEmpty(),
        providers = providerLogos(user), ownPage = viewer != null && viewer.loginId == user.loginId
    )

    /** 연결된 소셜 로그인의 로고이다. 자격 증명마다 제공자를 한 번씩 담는다 (v1.6 `UserCredential.getProviders`). */
    private fun providerLogos(user: User): List<String> =
        credentials.findByUserId(requireNotNull(user.id)).flatMap { credential ->
            linkedAccounts.findByUserCredentialId(requireNotNull(credential.id)).mapNotNull { it.providerKey }.distinct()
        }.map { ProviderLogos.of(it) }.filter { it.isNotEmpty() }

    /**
     * 활동 탭의 값이다. 사이트가 프로젝트 목록을 숨기고(`hideProjectListing`) 보는 사람이 익명이면 모두 비어 있다.
     *
     * @param daysAgo 이슈와 풀 리퀘스트를 거슬러 올라갈 일수
     */
    fun activity(user: User, viewer: User?, daysAgo: Int, hidden: Boolean): UserActivity {
        if (hidden) return UserActivity(emptyList(), emptyList(), emptyList())
        val allowed = HashMap<Long, Boolean>()
        fun readable(project: Project?): Boolean = project != null &&
            allowed.getOrPut(requireNotNull(project.id)) { accessControl.isAllowed(viewer, project.asResource(), Operation.READ) }

        val userId = requireNotNull(user.id)
        // v1.6 `JodaDateUtil.before(days)`: 오늘 0시에서 달력으로 N일 전 0시이다(현재 시각에서 24시간 x N이 아니다)
        val since = Date.from(LocalDate.now(clock).minusDays(daysAgo.toLong()).atStartOfDay(clock.zone).toInstant())
        val memberProjects = projects.findProjectsByMember(userId).distinctBy { it.id }.filter { readable(it) }
            .sortedWith(PROJECT_ORDER)
        val recentIssues = issues.findRecentlyByUser(userId, since).filter { readable(it.project) }
        val recentPullRequests = pullRequests.findRecentlyByContributor(userId, since).filter { readable(it.toProject) }
        return UserActivity(issueRows(recentIssues, viewer), recentPullRequests.map { pullRequestRow(it, viewer) }, memberProjects.map { projectRow(it, user, viewer) })
    }

    /**
     * 이슈를 사용자 페이지와 내 이슈가 쓰는 줄로 만든다. 다른 사람의 초안도 담는다.
     *
     * @param viewer 보는 사람. 없으면 익명이다
     */
    fun issueRows(list: List<Issue>, viewer: User?): List<UserIssueRow> {
        val items = issueItems.assemble(null, list, viewer, includeOthersDrafts = true).associateBy { it.id }
        val language = viewer?.lang
        return list.mapNotNull { issue ->
            val item = items[issue.id] ?: return@mapNotNull null
            val project = requireNotNull(issue.project)
            val author = issue.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }
            val changed = issue.createdDate != issue.updatedDate
            UserIssueRow(
                item = item, owner = project.owner.orEmpty(), project = project.name.orEmpty(), open = issue.state == State.OPEN, closed = issue.state == State.CLOSED,
                authorLoginId = author?.loginId ?: dev.yona.web.NullUserDisplay.LOGIN_ID,
                authorPureName = if (author == null) nullUser.name() else author.takeIf { !it.name.isNullOrEmpty() }?.let { UserDisplay.pureName(it, language) },
                assigneePureName = issue.assignee?.user?.let { UserDisplay.pureName(it, language) },
                milestoneVisible = issue.milestone != null && projectViews.of(project).menuSetting.milestone == true,
                timeTitle = if (changed) "Last Updated ${helper.dateString(issue.updatedDate)}" else "Created at ${helper.dateString(issue.createdDate)}",
                timeText = helper.agoOrDateString(if (changed) issue.updatedDate else issue.createdDate),
                dueDateString = issue.dueDate?.let { SimpleDateFormat("yyyy-MM-dd").format(it) }
            )
        }
    }

    private fun pullRequestRow(request: PullRequest, viewer: User?): UserPullRequestRow {
        val project = requireNotNull(request.toProject)
        val contributor = request.contributor?.loginId?.let { users.findByLoginIdIgnoreCase(it) }
        val conflict = request.isConflict == true
        val state = request.state?.name?.lowercase().orEmpty()
        val receiver = request.receiver
        return UserPullRequestRow(
            projectOwner = project.owner.orEmpty(), projectName = project.name.orEmpty(), projectLogoUrl = projectViews.of(project).logoUrl,
            number = request.number ?: 0, title = request.title.orEmpty(), conflict = conflict,
            stateKey = if (conflict) "pullRequest.state.conflict" else "pullRequest.state.$state", stateClass = if (conflict) "conflict" else state,
            contributorLoginId = contributor?.loginId.orEmpty(),
            contributorName = contributor?.takeIf { !it.name.isNullOrEmpty() }?.let { UserDisplay.of(it, viewer?.lang) },
            createdTitle = helper.dateString(request.created), createdText = helper.agoOrDateString(request.created),
            threadCount = commentThreads.countByPullRequestId(requireNotNull(request.id)),
            receiverLoginId = receiver?.loginId, receiverName = receiver?.name, receiverAvatarUrl = receiver?.let { avatars.avatarUrl(it) }
        )
    }

    private fun projectRow(project: Project, pageUser: User, viewer: User?): UserProjectRow {
        val view = projectViews.of(project)
        val original = project.originalProject
        val projectId = requireNotNull(project.id)
        return UserProjectRow(
            owner = project.owner.orEmpty(), name = project.name.orEmpty(), logoUrl = view.logoUrl,
            private = project.isPrivate(), publicScope = project.isPublic(),
            originalOwner = original?.owner, originalName = original?.name, overview = project.overview,
            memberCount = projectUsers.findMembers(projectId, RoleType.SITEMANAGER.id).size,
            createdTitle = helper.dateString(project.createdDate), createdText = helper.agoOrDateString(project.createdDate),
            lastPushedTitle = project.lastPushedDate?.let { helper.dateString(it) }, lastPushedText = project.lastPushedDate?.let { helper.agoOrDateString(it) },
            showWatchButton = viewer?.loginId != project.owner, watching = view.watching, watchingCount = view.watchingCount,
            canLeave = pageUser.loginId != project.owner && pageUser.loginId == viewer?.loginId
        )
    }

    private companion object {
        /** 프로필 큰 사진의 크기이다 (v1.6 `user.avatarUrl(256)`). */
        const val AVATAR_SIZE = 256

        /** 마지막 푸시가 최근인 프로젝트가 앞이고, 푸시 기록이 없는 것은 뒤이다. 같으면 이름 순이다 (v1.6 `sortByLastPushedDateAndName`). */
        val PROJECT_ORDER = Comparator<Project> { a, b ->
            val left = a.lastPushedDate
            val right = b.lastPushedDate
            when {
                left == null && right == null -> a.name.orEmpty().compareTo(b.name.orEmpty())
                left == null -> 1
                right == null -> -1
                else -> right.compareTo(left).let { if (it == 0) a.name.orEmpty().compareTo(b.name.orEmpty()) else it }
            }
        }
    }
}
