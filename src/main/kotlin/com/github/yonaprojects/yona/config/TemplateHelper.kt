package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.support.ReviewThreadService
import com.github.yonaprojects.yona.domain.support.ReviewSearchCondition
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository

@Component("templateHelper")
class TemplateHelper(
    private val messageSource: MessageSource,
    private val watchRepository: WatchRepository,
    private val attachmentRepository: AttachmentRepository,
    private val issueRepository: IssueRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val postingRepository: PostingRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val milestoneRepository: MilestoneRepository,
    private val reviewThreadService: ReviewThreadService,
    private val organizationUserRepository: OrganizationUserRepository,
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val commentThreadRepository: CommentThreadRepository
) {

    fun agoOrDateString(instant: Instant?): String {
        if (instant == null) return ""
        val now = Instant.now()
        val duration = Duration.between(instant, now)
        val days = duration.toDays()

        val zone = ZoneId.systemDefault()
        val dateYear = instant.atZone(zone).year
        val thisYear = now.atZone(zone).year

        return if (days < 8) {
            agoString(duration)
        } else if (dateYear == thisYear) {
            val formatter = DateTimeFormatter.ofPattern("MM-dd").withZone(zone)
            formatter.format(instant)
        } else {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
            formatter.format(instant)
        }
    }

    fun getDateString(instant: Instant?): String {
        if (instant == null) return ""
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss a").withZone(zone)
        return formatter.format(instant)
    }

    fun agoString(duration: Duration): String {
        val sec = duration.seconds
        val locale = LocaleContextHolder.getLocale()

        return when {
            sec >= 86400 -> {
                val days = duration.toDays()
                plural("common.time.day", days, locale)
            }
            sec >= 3600 -> {
                val hours = duration.toHours()
                plural("common.time.hour", hours, locale)
            }
            sec >= 60 -> {
                val minutes = duration.toMinutes()
                plural("common.time.minute", minutes, locale)
            }
            sec > 0 -> {
                val seconds = sec
                plural("common.time.second", seconds, locale)
            }
            else -> {
                messageSource.getMessage("common.time.just", null, locale)
            }
        }
    }

    fun getWatchingCount(projectId: Long?): Long {
        if (projectId == null) return 0L
        return watchRepository.countByResourceTypeAndResourceId(ResourceType.PROJECT, projectId.toString())
    }

    fun hasProjectLogo(projectId: Long?): Boolean {
        if (projectId == null) return false
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(
            ResourceType.PROJECT,
            projectId.toString()
        )
        return attachments.isNotEmpty()
    }

    private fun plural(key: String, count: Long, locale: Locale): String {
        val resolvedKey = if (count != 1L) {
            "${key}s"
        } else {
            key
        }
        return messageSource.getMessage(resolvedKey, arrayOf(count.toString()), locale)
    }

    fun showHeaderWordsInBracketsIfExist(title: String?): String {
        if (title.isNullOrEmpty()) return ""
        val regex = "^\\[.*?\\]".toRegex()
        val match = regex.find(title)
        return match?.value ?: ""
    }

    fun removeHeaderWords(title: String?): String {
        if (title.isNullOrEmpty()) return ""
        val regex = "^\\[.*?\\]\\s*".toRegex()
        return title.replace(regex, "")
    }

    // yona layout.scala.html:8 titleArray = title.split(" |:| ") 대응 — 페이지 제목에
    // " |:| "로 og:description/twitter:description용 부가 설명이 덧붙는 컨벤션 이식.
    fun titleMain(title: String?): String {
        if (title.isNullOrEmpty()) return ""
        return title.split(" |:| ").first()
    }

    fun titleOgDescription(title: String?): String {
        if (title.isNullOrEmpty()) return ""
        return title.split(" |:| ").last()
    }

    // yona issue/view.scala.html:53, board/view.scala.html:26 대응 —
    // 본문 앞부분 200자를 og:description/twitter:description 미리보기로 사용.
    @JvmOverloads
    fun ogDescriptionPreview(body: String?, maxLen: Int = 200): String {
        if (body.isNullOrEmpty()) return ""
        return body.substring(0, minOf(body.length, maxLen))
    }

    fun hasChildIssue(issue: Issue): Boolean {
        val issueId = issue.id ?: return false
        return issueRepository.countByParentId(issueId) > 0
    }

    // yona error/notfound.scala.html의 로컬 함수 getMenuType/getReturnURL/getMessage 대응
    // (P-템플릿 #45). 이슈/게시글/마일스톤/코드 등 프로젝트 내 서브 리소스를 찾지 못했을 때
    // 쓰는 컨텍스트 인지형 404 화면이 여러 컨트롤러에서 공통으로 참조하는 로직이라, 호출부마다
    // 중복 구현하지 않도록 TemplateHelper에 한 곳으로 모았다.
    fun notFoundActiveMenu(targetType: String?): String {
        return when (targetType) {
            "issue_post" -> "issue"
            "board_post" -> "board"
            "milestone" -> "milestone"
            "code" -> "code"
            else -> ""
        }
    }

    fun notFoundReturnUrl(project: Project, targetType: String?): String {
        val owner = project.owner
        val name = project.name
        return when (targetType) {
            "issue_post" -> "/$owner/$name/issues?state=all"
            "board_post" -> "/$owner/$name/posts"
            "milestone" -> "/$owner/$name/milestones"
            // yona notfound.scala.html: case "code" => routes.ProjectApp.settingForm(...) 그대로 —
            // 코드/브랜치를 못 찾았을 때는 코드 목록이 아니라 프로젝트 설정(기본 브랜치 조정)으로 보낸다.
            "code" -> "/$owner/$name/setting"
            else -> "javascript:history.back();"
        }
    }

    fun notFoundMessage(title: String?, targetType: String?): String {
        val locale = LocaleContextHolder.getLocale()
        if (targetType.isNullOrEmpty()) {
            return messageSource.getMessage("error.notfound", null, locale)
        }
        val fallback = messageSource.getMessage("error.notfound", null, locale)
        // yona error.notfound.code처럼 {0} 자리표시자를 쓰는 메시지가 있어(예: 브랜치 이름) title을
        // 인자로 넘기되, title이 없는 대다수 targetType(issue_post/board_post/milestone 등은
        // 플레이스홀더가 없다)에서는 빈 문자열을 넘겨 MessageFormat이 "null" 문자열을 찍는 사고를
        // 막는다. targetType이 알려지지 않은 값이면(legacy가 messages.<targetType> 키가 아예 없는
        // 경우 조회에 실패하는 것과 달리 예외로 500이 나는 걸 막기 위해) 제네릭 문구로 대체한다.
        return messageSource.getMessage("error.notfound.$targetType", arrayOf(title ?: ""), fallback, locale) ?: fallback
    }

    fun countByParentIssueIdAndState(parentId: Long, state: State): Long {
        return issueRepository.countByParentIdAndState(parentId, state)
    }

    // issue/partial_list.html의 2단 보기 자식 이슈 목록(child-issue-list)에서 사용.
    fun findByParentId(parentId: Long): List<Issue> {
        return issueRepository.findByParentId(parentId)
    }

    // yona Issue.findByParentIssueIdAndState() 대응 (issue/partial_view_childIssueList.html, 그룹7 #135).
    fun findByParentIdAndState(parentId: Long, state: State): List<Issue> {
        return issueRepository.findByParentIdAndState(parentId, state)
    }

    fun getPercent(numerator: Double, denominator: Double): Double {
        if (denominator == 0.0) return 0.0
        return (numerator / denominator) * 100.0
    }

    // yona TemplateHelper.scala의 getPercent(unit, total) = ((unit/total)*100).toInt 대응.
    // Scala의 Double.toInt는 반올림이 아니라 0쪽으로 절삭(truncate)한다 — 그룹7 #135 검증 중
    // String.format("%.0f", ...)로 반올림하던 이전 구현이 legacy와 다른 값(예: 66.66% → legacy
    // 66, 반올림 67)을 낼 수 있음을 확인해 legacy와 동일한 절삭 방식으로 맞춘다.
    fun getPercentFormatted(numerator: Long, denominator: Long): String {
        val pct = getPercent(numerator.toDouble(), (numerator + denominator).toDouble())
        return pct.toInt().toString()
    }

    fun isOverDueDate(issue: Issue): Boolean {
        val due = issue.dueDate ?: return false
        return issue.state == State.OPEN && Instant.now().isAfter(due)
    }

    fun until(issue: Issue): String {
        val due = issue.dueDate ?: return ""
        val zone = ZoneId.systemDefault()
        val nowDate = Instant.now().atZone(zone).toLocalDate()
        val dueDate = due.atZone(zone).toLocalDate()

        val locale = LocaleContextHolder.getLocale()

        if (nowDate.isEqual(dueDate)) {
            return messageSource.getMessage("common.time.today", null, locale)
        }

        val days = ChronoUnit.DAYS.between(nowDate, dueDate)
        return if (days < 0) {
            messageSource.getMessage("common.time.default.day", arrayOf((-days).toString()), locale)
        } else {
            messageSource.getMessage("common.time.default.day", arrayOf(days.toString()), locale)
        }
    }

    fun getDueDateString(instant: Instant?): String {
        if (instant == null) return ""
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
        return formatter.format(instant)
    }

    // yona models/User.java:247-250 getDateString() 대응(search/partial_users.scala.html의 [GL-models_User-044]
    // "userinfo.since" 가입일 표시에서 사용). "MMM dd, yyyy" 포맷을 Locale.US로 고정하는 legacy
    // 원본을 그대로 재현 — TemplateHelper.getDateString(instant)(yyyy-MM-dd h:mm:ss a, 로컬 로케일)와는
    // 포맷이 달라 별도 메서드로 분리했다.
    fun getUserSinceDateString(instant: Instant?): String {
        if (instant == null) return ""
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US).withZone(zone)
        return formatter.format(instant)
    }

    // yona models/Milestone.java:261 until() 대응 (search/partial_milestones.scala.html에서 사용).
    // 오늘/기한초과/남은일수 3분기 — issue용 until(issue: Issue)와는 메시지 키가 다르다(legacy 원본이
    // Issue.until()과 Milestone.until()에서 서로 다른 메시지 키 세트를 쓰기 때문에 그대로 분리 재현).
    fun until(milestone: Milestone): String {
        val due = milestone.dueDate ?: return ""
        val zone = ZoneId.systemDefault()
        val nowDate = Instant.now().atZone(zone).toLocalDate()
        val dueDate = due.atZone(zone).toLocalDate()
        val locale = LocaleContextHolder.getLocale()

        if (nowDate.isEqual(dueDate)) {
            return messageSource.getMessage("common.time.today", null, locale)
        }

        val days = ChronoUnit.DAYS.between(nowDate, dueDate)
        return if (days < 0) {
            messageSource.getMessage("common.time.overday", arrayOf((-days).toString()), locale)
        } else {
            messageSource.getMessage("common.time.leftday", arrayOf(days.toString()), locale)
        }
    }

    // yona utils/TemplateHelper.scala:428-445 urlToCommentThread()/urlToContainer() 대응
    // (search/partial_reviews.scala.html에서 사용). PR 리뷰 스레드면 PR 화면, 커밋 리뷰 스레드면
    // 커밋 화면으로 링크한다 — outdated diff의 특정 커밋 앵커(specificChange) 세부 분기는
    // NotificationUrlResolver.urlToContainer()에서 이미 동일하게 생략해둔 전례를 따라 여기서도
    // 생략한다(기능 누락이 아니라 "어느 diff 특정 커밋을 보여줄지"의 UI 라우팅 미세조정).
    fun urlToCommentThread(thread: CommentThread): String {
        val pullRequest = thread.pullRequest
        val container = if (pullRequest != null) {
            val project = pullRequest.toProject
            "/${project.owner}/${project.name}/pull/${pullRequest.number}"
        } else {
            val project = thread.project
            if (project == null) "" else "/${project.owner}/${project.name}/commit/${thread.commitId}"
        }
        return "$container#thread-${thread.id}"
    }

    fun getIssueLabelsString(labels: Set<IssueLabel>?): String {
        if (labels == null || labels.isEmpty()) return ""
        return labels.sortedWith(compareBy({ it.category.name }, { it.name }))
            .joinToString("|") { label ->
                val categoryName = label.category.name
                val id = label.id ?: ""
                val name = label.name
                val categoryId = label.category.id ?: ""
                val isExclusive = label.category.isExclusive
                "$categoryName,$id,$name,$categoryId,$isExclusive"
            }
    }

    fun countIssues(project: Project): Long {
        return issueRepository.countByProjectAndState(project, State.OPEN)
    }

    fun countPullRequests(project: Project): Long {
        return pullRequestRepository.countByToProjectAndState(project, State.OPEN)
    }

    fun countBoardPosts(project: Project): Long {
        return postingRepository.countByProject(project)
    }

    // yona projectMenu.scala.html:40-42 CommentThread.countReviewsBy(project.id, null) 대응.
    fun countReviews(project: Project): Long {
        return reviewThreadService.countReviewThreads(project, ReviewSearchCondition(state = "OPEN"))
    }

    // yona User.isMemberOf(org)/isAdminOf(org) 대응 — common/navbar.scala.html:84 검색범위 노출 조건.
    fun isOrganizationMemberOrAdmin(org: Organization?, user: User?): Boolean {
        if (org == null || user == null) return false
        return organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, user.id!!)
    }

    // yona project/header.scala.html:48-50 FavoriteProject.findByProjectId(userId, projectId) != null 대응.
    fun isFavoriteProject(project: Project?, user: User?): Boolean {
        if (project == null || user == null) return false
        return favoriteProjectRepository.findByUserIdAndProjectId(user.id!!, project.id!!).isPresent
    }

    fun getVotersExceptCurrentUser(voters: Collection<User>, currentUser: User?): List<User> {
        if (currentUser == null) return voters.toList()
        return voters.filter { it.id != currentUser.id }
    }

    fun getVotersForAvatar(voters: Collection<User>, size: Int): List<User> {
        return voters.take(size)
    }

    fun getVotersForName(voters: Collection<User>, fromIndex: Int, size: Int): List<User> {
        val list = voters.toList()
        val start = Math.max(0, fromIndex)
        val end = Math.min(list.size, fromIndex + size)
        if (start >= list.size) return emptyList()
        return list.subList(start, end)
    }

    fun getVotersTooltip(voters: Collection<User>, fromIndex: Int, size: Int): String {
        val list = getVotersForName(voters, fromIndex, size)
        val names = list.joinToString("<br>") { it.name ?: it.loginId }
        val hasMore = voters.size > fromIndex + size
        return if (hasMore) "$names<br>&hellip;" else names
    }

    fun isMember(project: Project?, user: User?): Boolean {
        if (project == null || user == null) return false
        if (project.id == null || user.id == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!)
    }

    fun isManager(project: Project?, user: User?): Boolean {
        if (project == null || user == null) return false
        if (project.id == null || user.id == null) return false
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    // yona User.enrolled(project) 대응 — 프로젝트에 가입 신청(대기)한 상태인지 확인.
    fun isEnrolled(project: Project?, user: User?): Boolean {
        if (project == null || user == null) return false
        return user.enrolledProjects.any { it.id == project.id }
    }

    fun getLabelCategories(project: Project?): List<IssueLabelCategory> {
        if (project == null) return emptyList()
        return issueLabelCategoryRepository.findByProject(project)
    }

    fun getLabelsByCategory(category: IssueLabelCategory?): List<IssueLabel> {
        if (category == null) return emptyList()
        return issueLabelRepository.findByCategory(category)
    }

    fun getProjectLabels(project: Project?): List<IssueLabel> {
        if (project == null) return emptyList()
        return issueLabelRepository.findByProject(project)
    }

    fun canBeDeleted(issue: Issue?, comments: List<IssueComment>?): Boolean {
        if (issue == null) return false
        if (comments.isNullOrEmpty()) return true
        return comments.all { it.authorLoginId == issue.authorLoginId }
    }

    fun getAssignableUsers(project: Project?): List<User> {
        if (project == null || project.id == null) return emptyList()
        return projectUserRepository.findByProjectId(project.id!!).map { it.user }
    }

    fun getOpenMilestones(project: Project?): List<Milestone> {
        if (project == null) return emptyList()
        return milestoneRepository.findByProjectAndState(project, State.OPEN)
    }

    data class MilestoneProgress(
        val openCount: Int,
        val closedCount: Int,
        val completionRate: Int,
        val isOverdue: Boolean
    )

    // yona models/Milestone.java:92-98,135-137,277-279 getNumOpenIssues()/getNumClosedIssues()/
    // getCompletionRate()/isOverDueDate() 대응 (milestone/partial_status.html에서 사용).
    fun getMilestoneProgress(milestone: Milestone): MilestoneProgress {
        val allIssues = issueRepository.findByMilestone(milestone)
        val openCount = allIssues.count { it.state == State.OPEN }
        val closedCount = allIssues.count { it.state == State.CLOSED }
        val total = openCount + closedCount
        val completionRate = if (total > 0) (closedCount * 100) / total else 0
        val isOverdue = milestone.dueDate?.isBefore(Instant.now()) ?: false
        return MilestoneProgress(openCount, closedCount, completionRate, isOverdue)
    }

    fun isMac(): Boolean {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val userAgent = request?.getHeader("User-Agent") ?: return false
        return userAgent.contains("Macintosh", ignoreCase = true)
    }

    // yona utils/TemplateHelper.scala:227-234 Branches.itemName() 대응 — RepositoryService.getRefNames()가
    // 돌려주는 "refs/heads/master" 같은 전체 ref 이름에서 표시/URL용 브랜치 이름("master")만 뽑아낸다.
    fun branchItemName(branch: String): String {
        val parts = branch.split("/", limit = 3)
        return if (parts.size == 3 && parts[0] == "refs") parts[2] else branch
    }

    // yona utils/TemplateHelper.scala:216-225 Branches.itemType() 대응.
    fun branchItemType(branch: String): String {
        val parts = branch.split("/")
        return when {
            parts.size >= 2 && parts[0] == "refs" && parts[1] == "heads" -> "branch"
            parts.size >= 2 && parts[0] == "refs" && parts[1] == "tags" -> "tag"
            parts.size >= 2 && parts[0] == "refs" -> parts[1]
            else -> branch
        }
    }

    // yona utils/TemplateHelper.scala:236-246 Branches.branchInHTML() 대응 — "refs/heads/..." 같은
    // 전체 ref 이름이면 타입 라벨(<span class="label branch">branch</span>)을 붙이고, 그렇지 않으면(이미
    // 짧은 이름이면) 그대로 반환한다.
    fun branchInHtml(branch: String): String {
        val parts = branch.split("/")
        return if (parts.isNotEmpty() && parts[0] == "refs" && parts.size >= 3) {
            val branchType = branchItemType(branch)
            "<span class=\"label $branchType\">$branchType</span>" + branchItemName(branch)
        } else {
            branch
        }
    }

    // yona models/OrganizationUser.java:62-68 isAdmin(Organization, User) 대응 (조직 그룹, TASK-0244).
    // organization.organizationUsers는 컨트롤러에서 이미 로드해 모델에 넘기는 컬렉션이라 여기서는
    // 추가 조회 없이 그 컬렉션을 순회한다(project 쪽 isManager()가 별도 repository 조회를 쓰는 것과
    // 달리, organization/header·menu 프래그먼트가 매 페이지에서 반복 호출하므로 N+1을 피하기 위함).
    fun isOrganizationAdmin(organization: Organization?, user: User?): Boolean {
        if (organization == null || user == null) return false
        return organization.organizationUsers.any { it.user.id == user.id && it.role.id == RoleType.ORG_ADMIN.roleType }
    }

    // yona models/OrganizationUser.java:74-76 isMember(Organization, User) 대응.
    fun isOrganizationMember(organization: Organization?, user: User?): Boolean {
        if (organization == null || user == null) return false
        return organization.organizationUsers.any { it.user.id == user.id && it.role.id == RoleType.ORG_MEMBER.roleType }
    }

    // yona models/OrganizationUser.java:70-72 isGuest(Organization, User) 대응. 사이트매니저와 [GL-models_OrganizationUser-010]
    // 조직 내 역할(관리자/멤버)이 있는 사용자는 게스트가 아니다 — 비로그인 사용자도 게스트가 아니다
    // (legacy roleTypeOf()가 비로그인이면 ANONYMOUS를 반환하지 GUEST를 반환하지 않음).
    fun isOrganizationGuest(organization: Organization?, user: User?): Boolean {
        if (organization == null || user == null || user.isSiteManager) return false
        return organization.organizationUsers.none { it.user.id == user.id }
    }

    // yona models/User.java:677-683 enrolled(Organization) 대응. [GL-models_User-078]
    fun isEnrolledOrganization(organization: Organization?, user: User?): Boolean {
        if (organization == null || user == null) return false
        return user.enrolledOrganizations.any { it.id == organization.id }
    }

    // yona organization/group_pullrequest_list_partial.scala.html:49,55 countCommentThreadsByState/
    // req.commentThreads.size 대응. PullRequest 엔티티에 commentThreads 연관관계가 직접 매핑돼 있지
    // 않아(다른 화면에서도 CommentThreadRepository.findByPullRequest()로 조회하는 기존 관례를 따름)
    // 여기서 조회한다.
    fun getCommentThreads(pullRequest: PullRequest): List<CommentThread> {
        return commentThreadRepository.findByPullRequest(pullRequest)
    }

    // yona group_pullrequest_list_partial.scala.html:53 getPercent(countClosed.toDouble,
    // req.commentThreads.size.toDouble) 대응. 템플릿에서 SpEL로 getPercent(Double, Double) 오버로드를
    // 직접 호출하면 Long/Int 인자와의 타입 매칭이 불안정하므로, PullRequest 하나를 받아 내부에서
    // closed/전체 스레드 수를 모두 계산하는 전용 메서드로 둔다.
    fun getReviewProgressPercent(pullRequest: PullRequest): Double {
        val threads = commentThreadRepository.findByPullRequest(pullRequest)
        if (threads.isEmpty()) return 0.0
        val closed = threads.count { it.state == CommentThread.ThreadState.CLOSED }
        return getPercent(closed.toDouble(), threads.size.toDouble())
    }

    fun countCommentThreadsByState(pullRequest: PullRequest, state: CommentThread.ThreadState): Long {
        return commentThreadRepository.findByPullRequest(pullRequest).count { it.state == state }.toLong()
    }

    // yona models/User.java isWatching(Project) 대응 — organization/view.scala.html의 프로젝트
    // 목록 각 항목에서 현재 사용자의 관심(watch) 여부를 표시하는 데 쓰인다.
    fun isWatchingProject(project: Project?, user: User?): Boolean {
        if (project == null || user == null || project.id == null) return false
        return watchRepository.findByUserAndResourceTypeAndResourceId(user, ResourceType.PROJECT, project.id.toString()) != null
    }

    // yona git/partial_state.scala.html의 getCodeURL(project) 대응 (그룹11 #183). 프로젝트 멤버면
    // "scheme://loginId@host[:port]/owner/name.git" 형태로 사용자 계정을 끼워넣은 clone URL을
    // 돌려주고(로그인 인증 git push용), 아니면 계정 없이 그대로 돌려준다. legacy는
    // CodeApp.getURL()이 별도로 존재했지만 yona에는 clone URL 헬퍼가 아직 없어 여기에 신설한다.
    fun getCloneUrl(project: Project, user: User?): String {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val scheme = request?.scheme ?: "http"
        val host = request?.serverName ?: "localhost"
        val port = request?.serverPort ?: 80
        val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        val hostPart = if (isDefaultPort) host else "$host:$port"
        val isMember = user != null && project.id != null && user.id != null &&
            projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!)
        val authority = if (isMember && user != null) "${user.loginId}@$hostPart" else hostPart
        // TASK-0416 부수 발견(P3-02 10라운드) — 실제 git 스마트 HTTP 서블릿은 GitServletConfig가
        // "/git/*" 경로에 등록돼 있는데(GitServletConfig.kt), 이 헬퍼는 "/git/" 세그먼트 없이
        // "scheme://host/owner/name.git" 형태로 URL을 만들어왔다. 이 URL은 실제로 존재하지 않는
        // 경로라 partial_state.html("git remote add upstream ...")이 화면에 보여주는 안내
        // 커맨드를 그대로 실행하면 항상 404가 난다 — yona-cli의 planCheckout()도 동일한 착오로
        // 같은 형태의 URL을 만들고 있었다(둘 다 이번에 함께 수정).
        return "$scheme://$authority/git/${project.owner}/${project.name}.git"
    }
}

