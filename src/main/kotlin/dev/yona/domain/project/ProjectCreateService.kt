package dev.yona.domain.project

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.misc.WatchService
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** 프로젝트 생성이 거부된 이유이다. */
enum class ProjectCreateDenied {
    /** 로그인하지 않았거나 전역 리소스를 만들 권한이 없다. */
    NOT_CREATABLE,

    /** 소유자가 조직인데 그 조직의 관리자가 아니다. */
    NOT_ORGANIZATION_ADMIN
}

/**
 * 프로젝트 생성의 검증과 저장을 담당한다. v1.6 `ProjectApp.newProject`, `validateWhenNew`,
 * `saveProjectMenuSetting`, `Project.create`(`app/controllers/ProjectApp.java:169-231,280-292`)에 해당한다.
 */
@Service
class ProjectCreateService(
    private val accessControl: AccessControl,
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val projectUsers: ProjectUserRepository,
    private val organizations: OrganizationRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val roles: RoleRepository,
    private val menuSettings: ProjectMenuSettingRepository,
    private val watchService: WatchService,
    private val recentVisits: RecentVisitService,
    private val repositories: RepositoryService
) {

    /**
     * 만들 권한이 있는지 확인한다. 권한이 없으면 이유를 돌려주고, 있으면 `null`이다.
     * 조직 이름과 같은 소유자는 그 조직의 관리자여야 한다.
     */
    fun checkPermission(creator: User?, ownerName: String?): ProjectCreateDenied? {
        if (!accessControl.isGlobalResourceCreatable(creator)) return ProjectCreateDenied.NOT_CREATABLE
        val organization = ownerName?.let { organizations.findByNameIgnoreCase(it) }
        if (organization != null && !isOrganizationAdmin(organization, creator)) return ProjectCreateDenied.NOT_ORGANIZATION_ADMIN
        return null
    }

    /**
     * 입력값을 검증하고 오류를 모아 돌려준다. 순서와 메시지는 v1.6과 같다.
     *
     * 1. 이름 필수, 이름 문자 규칙(`^[a-zA-Z0-9-_.가-힣]+$`), 예약어(`.`, `..`, `.git`)는 입력 묶기 단계의 검사이다.
     *    규칙 위반은 사용자에게 보여 줄 메시지(`project.name.alert`, `project.name.reserved.alert`)로 바꾼다.
     * 2. 소유자가 사용자도 조직도 아니면 `project.owner.invalidate`.
     * 3. 소유자가 사용자인데 만드는 사람 본인이 아니면 `project.owner.invalidate`.
     * 4. 같은 소유자에 같은 이름이 있으면(대소문자 무시) `project.name.duplicate`.
     *
     * 예약어는 대소문자를 가리지 않고 거부한다. v1.6 구현이 `ignoreCase` 분기를 거꾸로 써서 기본 설정에서
     * 오히려 대소문자를 무시하기 때문이다([refactoring-backlog.md] R-13).
     */
    fun validate(form: ProjectForm, creator: User?): FormErrors {
        val errors = FormErrors()
        val name = form.name

        // 입력 묶기 단계의 검사 (Required, Pattern, Restricted)
        ProjectNameRules.violations(name).forEach { errors.reject("name", it) }
        if (form.projectScope != null && form.scope() == null) errors.reject("projectScope", "error.invalid")

        // 폼 검증 단계 (validateWhenNew)
        val owner = form.owner
        val ownerUser = owner?.let { users.findByLoginIdIgnoreCase(it) }
        val ownerIsOrganization = owner != null && organizations.existsByNameIgnoreCase(owner)
        if (ownerUser == null && !ownerIsOrganization) errors.reject("owner", "project.owner.invalidate")
        if (ownerUser != null && ownerUser.id != creator?.id) errors.reject("owner", "project.owner.invalidate")
        if (owner != null && name != null && projects.existsByOwnerIgnoreCaseAndNameIgnoreCase(owner, name)) {
            errors.reject("name", "project.name.duplicate")
        }

        // 이름 규칙 위반은 안내 문구로 바꾼다.
        errors.first("name")?.let { first ->
            val message = ProjectNameRules.toUserMessageKey(first)
            if (message != first) { errors.clear("name"); errors.reject("name", message) }
        }
        return errors
    }

    /**
     * 프로젝트를 만든다. 검증을 통과한 입력만 받는다.
     *
     * 프로젝트 저장, 사이트 관리자(SITEMANAGER)와 만든 사람(MANAGER)의 역할, 저장소, 메뉴 설정, 구독, 방문 기록을
     * 한 트랜잭션에서 처리한다. 저장소 생성이 실패하면 모두 취소된다.
     */
    @Transactional
    fun create(form: ProjectForm, creator: User): Project {
        val owner = requireNotNull(form.owner)
        val project = Project(
            owner = owner,
            name = form.name,
            overview = form.overview,
            vcs = form.vcs,
            projectScope = form.scope(),
            organization = organizations.findByNameIgnoreCase(owner)
        )
        // v1.6 `Project.create`: siteurl은 고정 주소로, 만든 날짜는 지금으로 정한다.
        project.siteurl = "http://localhost:9000/${project.name}"
        project.createdDate = Date()
        projects.save(project)
        assignRole(SITE_MANAGER_ID, project, RoleType.SITEMANAGER)

        assignRole(requireNotNull(creator.id), project, RoleType.MANAGER)
        repositories.createRepository(project)

        saveMenuSetting(project, form.menus)
        watchService.watch(creator, ResourceType.PROJECT, requireNotNull(project.id).toString())
        recentVisits.record(creator, project)
        return project
    }

    private fun isOrganizationAdmin(organization: Organization, user: User?): Boolean {
        val userId = user?.id ?: return false
        return organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(
            requireNotNull(organization.id), userId, RoleType.ORG_ADMIN.id
        )
    }

    private fun assignRole(userId: Long, project: Project, roleType: RoleType) {
        projectUsers.save(
            ProjectUser(user = users.getReferenceById(userId), project = project, role = roles.getReferenceById(roleType.id))
        )
    }

    private fun saveMenuSetting(project: Project, menus: Set<String>) {
        menuSettings.save(
            ProjectMenuSetting(
                project = project,
                code = "code" in menus, issue = "issue" in menus, pullRequest = "pullRequest" in menus,
                review = "review" in menus, milestone = "milestone" in menus, board = "board" in menus
            )
        )
    }

    companion object {
        /** 사이트 관리자의 사용자 번호이다. 모든 프로젝트에 `SITEMANAGER` 역할로 들어간다 (v1.6 `User.SITE_MANAGER_ID`). */
        const val SITE_MANAGER_ID = 1L
    }
}
