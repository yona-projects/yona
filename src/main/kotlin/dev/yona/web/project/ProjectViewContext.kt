package dev.yona.web.project

import dev.yona.security.SessionPermissions
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.access.labelsAsResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest

/**
 * 프로젝트 화면 템플릿이 쓰는 프로젝트 정보 묶음이다. v1.6 템플릿이 `Project`의 메서드와 정적 도우미
 * (`urlToProjectLogo`, `Issue.countIssues`, `User.isWatching`, `ProjectUser.isGuest` 등)로 구하던 값들을
 * 한곳에서 제공한다. DB 조회가 필요한 값은 템플릿이 쓸 때 한 번만 읽는다.
 *
 * 템플릿에서는 모델 이름 `projectView`로 접근한다.
 *
 * @property project 대상 프로젝트
 * @property viewer 화면을 보는 사용자. 익명이면 `null`
 */
class ProjectViewContext(
    val project: Project,
    private val viewer: User?,
    private val request: HttpServletRequest,
    private val deps: Dependencies
) {

    /** [ProjectViewContext]가 필요로 하는 서비스와 저장소 묶음이다. */
    class Dependencies(
        val projectService: ProjectService,
        val accessControl: AccessControl,
        val menuSettings: ProjectMenuSettingRepository,
        val attachments: AttachmentRepository,
        val watches: WatchRepository,
        val unwatches: UnwatchRepository,
        val favorites: FavoriteProjectRepository,
        val projectUsers: ProjectUserRepository,
        val users: UserRepository,
        val permissions: SessionPermissions,
        val repositoryUrls: RepositoryUrls
    )

    private val projectId: Long get() = requireNotNull(project.id)
    private val viewerId: Long? get() = viewer?.id

    /** 소유자와 이름을 `소유자/이름`으로 이은 표시용 이름이다 (v1.6 `Project.toString`). */
    val displayName: String get() = "${project.owner}/${project.name}"

    /** 프로젝트 메뉴 설정이다. 프로젝트 생성 때 항상 함께 만들어지므로 없으면 오류이다. */
    val menuSetting: ProjectMenuSetting by lazy {
        deps.menuSettings.findFirstByProjectId(projectId) ?: error("프로젝트 메뉴 설정이 없습니다: $displayName")
    }

    // --- 로고와 배경 ---

    private val projectAttachmentId: Long? by lazy {
        deps.attachments.findByContainerTypeAndContainerIdOrderById(ResourceType.PROJECT, projectId).firstOrNull()?.id
    }

    /** 프로젝트에 올린 첨부(로고)가 있으면 `true`이다 (v1.6 `hasProjectLogo`). */
    val hasLogo: Boolean get() = projectAttachmentId != null

    /** 프로젝트에 올린 첨부가 있으면 로고 이미지로 쓴다 (v1.6 `urlToProjectLogo`). */
    val logoUrl: String get() = projectAttachmentId?.let { "/files/$it" } ?: DEFAULT_LOGO

    /** 프로젝트 헤더의 배경 이미지이다. 로고와 같은 첨부를 쓴다 (v1.6 `urlToProjectBG`). */
    val backgroundUrl: String get() = projectAttachmentId?.let { "/files/$it" } ?: DEFAULT_BACKGROUND

    // --- 사용자와 프로젝트의 관계 ---

    /** 보는 사용자가 이 프로젝트를 즐겨찾기했으면 `true`이다. */
    val favorite: Boolean by lazy { viewerId?.let { deps.favorites.existsByUserIdAndProjectId(it, projectId) } ?: false }

    /** 구독 중이면 `true`이다. 구독 행이 있고 구독 해제 행이 없어야 한다 (v1.6 `Watch.isWatching`). */
    val watching: Boolean by lazy {
        val userId = viewerId ?: return@lazy false
        val id = projectId.toString()
        deps.watches.existsByUserIdAndResourceTypeAndResourceId(userId, ResourceType.PROJECT, id) &&
            !deps.unwatches.existsByUserIdAndResourceTypeAndResourceId(userId, ResourceType.PROJECT, id)
    }

    /** 프로젝트를 구독한 행의 수이다 (v1.6 `Project.getWatchingCount`). */
    val watchingCount: Long by lazy { deps.watches.countByResourceTypeAndResourceId(ResourceType.PROJECT, projectId.toString()) }

    /**
     * 로그인했지만 이 프로젝트의 역할이 없는 사용자(게스트)이면 `true`이다.
     * 사이트 관리자와 익명은 게스트가 아니다 (v1.6 `ProjectUser.isGuest`, `roleOf`).
     */
    val guest: Boolean by lazy {
        val userId = viewerId ?: return@lazy false
        !deps.permissions.siteManager(userId) && deps.projectUsers.findFirstByUserIdAndProjectId(userId, projectId) == null
    }

    /** 가입을 신청한 상태이면 `true`이다 (v1.6 `User.enrolled(project)`). */
    val enrolled: Boolean by lazy { viewerId?.let { deps.users.hasEnrolled(it, projectId) } ?: false }

    /** 가입을 신청한 사용자 수이다. 설정 메뉴의 배지에 쓴다. */
    val enrolledUserCount: Long by lazy { deps.users.countEnrolledUsers(projectId) }

    /** 구독 버튼을 보여도 되면 `true`이다 (`WATCH` 권한). */
    val canWatch: Boolean by lazy { deps.accessControl.isAllowed(viewer, project.asResource(), Operation.WATCH) }

    /** 설정 메뉴를 보여도 되면 `true`이다 (`UPDATE` 권한). */
    val canUpdate: Boolean by lazy { deps.accessControl.isAllowed(viewer, project.asResource(), Operation.UPDATE) }

    /** 저장소에 커밋을 만들 수 있으면 `true`이다. README를 만들고 고치는 링크를 보여 줄지 정한다 (v1.6 `isProjectResourceCreatable(COMMIT)`). */
    val canCreateCommit: Boolean by lazy { deps.accessControl.isProjectResourceCreatable(viewer, project, ResourceType.COMMIT) }

    /** 프로젝트의 라벨을 바꿀 수 있으면 `true`이다. 구성원 추가 링크를 보여 줄지 정한다 (v1.6 `labelsAsResource` `UPDATE`). */
    val canUpdateLabels: Boolean by lazy { deps.accessControl.isAllowed(viewer, project.labelsAsResource(), Operation.UPDATE) }

    /** 프로젝트를 탈퇴할 수 있으면 `true`이다 (`LEAVE` 권한). */
    val canLeave: Boolean by lazy { deps.accessControl.isAllowed(viewer, project.asResource(), Operation.LEAVE) }

    /** 보는 사용자가 프로젝트를 탈퇴할 때 호출하는 주소이다. 익명이면 `null`이다. */
    val leaveUrl: String? get() = viewerId?.let { "/${project.owner}/${project.name}/member/$it/delete" }

    /** 사용자가 프로젝트에 역할을 가지고 있으면(역할 무관) `true`이다 (v1.6 `ProjectUser.isMember`). 일괄 처리 선택 상자를 보일지 정한다. */
    val projectMember: Boolean by lazy { viewerId?.let { deps.projectUsers.existsByUserIdAndProjectId(it, projectId) } ?: false }

    /** 사용자가 프로젝트 관리자이면 `true`이다 (v1.6 `User.isManagerOf`). */
    val manager: Boolean by lazy {
        deps.permissions.projectManager(viewerId, projectId)
    }

    /** 사용자가 프로젝트 구성원이면 `true`이다 (v1.6 `Project.hasMember`). */
    val member: Boolean by lazy { deps.projectService.hasMember(project, viewer) }

    /** 코드를 볼 수 있으면 `true`이다. 코드 열람을 구성원으로 제한한 프로젝트는 구성원만 볼 수 있다. */
    val codeAccessible: Boolean get() = project.isCodeAccessibleMemberOnly != true || member

    /** 다른 프로젝트를 포크한 프로젝트이면 `true`이다 (v1.6 `Project.isForkedFromOrigin`). */
    val forked: Boolean get() = project.originalProject != null

    /**
     * v1.6 `routes.PullRequestApp`의 풀 리퀘스트 목록 주소이다. 포크한 프로젝트는 보낸 요청 목록으로 간다.
     */
    val pullRequestPath: String
        get() = "/${project.owner}/${project.name}/" + if (forked) "sentPullRequests" else "pullRequests"

    /** Git 프로젝트이면 `true`이다. */
    val git: Boolean get() = project.vcs == "GIT"

    /**
     * 키보드 단축키 `Q`(설정 화면)를 켤지 여부이다. v1.6은 세션의 로그인 ID가 문자열 `manager`와 같을 때만 켠다.
     * 역할 이름과 로그인 ID를 혼동한 v1.6의 결함이며 그대로 따른다([refactoring-backlog.md] R-11).
     */
    val settingShortcutEnabled: Boolean get() = viewer?.loginId == "manager"

    /** 저장소 복제 주소이다. 로그인했으면 `사용자@`가 들어간다 (v1.6 `CodeApp.getURLWithLoginId`). */
    val repositoryUrl: String? get() = deps.repositoryUrls.urlFor(project, request, viewer?.loginId)

    // --- 메뉴의 개수 ---

    val openIssueCount: Long by lazy { deps.projectService.countIssues(project, State.OPEN) }
    val openPullRequestCount: Long by lazy { deps.projectService.countOpenPullRequests(project) }
    val openReviewCount: Long by lazy { deps.projectService.countOpenReviewThreads(project) }
    val postingCount: Long by lazy { deps.projectService.countPostings(project) }

    companion object {
        const val DEFAULT_LOGO = "/assets/images/project_default_logo.png"
        const val DEFAULT_BACKGROUND = "/assets/images/project_default.jpg"
    }
}
