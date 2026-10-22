package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 포크의 대상 선택, 이름 제안, 생성을 담당한다. v1.6 `PullRequestApp.newFork`, `fork`, `doClone`, `findDestination`
 * (`app/controllers/PullRequestApp.java:74-153`)과 `Project.copy`, `addFork`에 해당한다.
 *
 * 권한(포크 만들기)은 호출하는 쪽이 확인한다.
 */
@Service
class ForkService(
    private val projects: ProjectRepository,
    private val projectService: ProjectService,
    private val projectUsers: ProjectUserRepository,
    private val menuSettings: ProjectMenuSettingRepository,
    private val organizations: OrganizationRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val repositories: RepositoryService
) {

    /**
     * 포크가 놓일 소유자 이름이다. [forkOwner]가 사용자가 관리하는 조직 이름이면 그것이고, 아니면 사용자 자신의 로그인 ID이다
     * (v1.6 `findDestination`).
     */
    @Transactional(readOnly = true)
    fun destination(forkOwner: String?, user: User): String {
        val organization = forkOwner?.let { organizations.findByNameIgnoreCase(it) }
        val admin = organization != null && organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(requireNotNull(organization.id), requireNotNull(user.id), RoleType.ORG_ADMIN.id)
        return if (admin) requireNotNull(forkOwner) else requireNotNull(user.loginId)
    }

    /** 사용자가 관리자(ORG_ADMIN)인 조직의 이름이다. 포크 폼의 소유자 선택지이다 (v1.6 `OrganizationUser.findByAdmin`). */
    @Transactional(readOnly = true)
    fun adminOrganizationNames(user: User): List<String> =
        organizationUsers.findByUserIdAndRoleId(requireNotNull(user.id), RoleType.ORG_ADMIN.id).mapNotNull { it.organization?.name }

    /** 포크 폼이 처음 보여 줄 프로젝트 이름이다. 소유자에게 같은 이름이 있으면 `이름-1`, `이름-2` 순으로 비어 있는 번호를 붙인다 (v1.6 `Project.copy`). */
    @Transactional(readOnly = true)
    fun suggestName(original: Project, destination: String): String = projectService.newProjectName(destination, requireNotNull(original.name))

    /** [destination] 소유자가 [original]을 이미 포크한 프로젝트이다. */
    @Transactional(readOnly = true)
    fun existingForks(original: Project, destination: String): List<Project> = projects.findByOwnerAndOriginalProjectId(destination, requireNotNull(original.id))

    /** [destination] 소유자에게 같은 이름(대소문자 무시)의 프로젝트가 있으면 `true`이다 (v1.6 `Project.exists`). */
    @Transactional(readOnly = true)
    fun nameTaken(destination: String, name: String): Boolean = projects.existsByOwnerIgnoreCaseAndNameIgnoreCase(destination, name)

    /** 소유자 이름이 조직 이름이면 `true`이다. 포크 폼이 조직에는 "그룹 내 공개"를 더 보여 준다. */
    @Transactional(readOnly = true)
    fun isOrganization(owner: String): Boolean = organizations.existsByNameIgnoreCase(owner)

    /**
     * 원본을 복제해 포크 프로젝트를 만든다. 원본의 설명, VCS, 메뉴 설정을 물려받고 공개 범위와 이름은 입력을 따른다.
     * 소유자가 조직이면 조직에 연결한다. 만든 사람은 매니저, 사이트 관리자는 SITEMANAGER 역할을 받는다 (v1.6 `Project.create`).
     * v1.6처럼 구독과 방문 기록은 남기지 않는다.
     *
     * 저장소 복제가 실패하면 예외가 나가고 트랜잭션이 취소되어 프로젝트가 남지 않는다. v1.6은 복제 뒤에 프로젝트를 저장했으므로
     * 같은 결과이다. 복제하다 만 저장소 디렉터리는 지운다.
     */
    @Transactional
    fun fork(original: Project, destination: String, name: String, scope: ProjectScope, creator: User): Project {
        val fork = Project(
            owner = destination, name = name, overview = original.overview, vcs = original.vcs, projectScope = scope,
            organization = organizations.findByNameIgnoreCase(destination), originalProject = original
        )
        try {
            repositories.cloneRepository(original, fork)
            fork.siteurl = "http://localhost:9000/${fork.name}"
            fork.createdDate = Date()
            projects.save(fork)
            copyMenuSetting(original, fork)
            assignRole(SITE_MANAGER_ID, fork, RoleType.SITEMANAGER)
            assignRole(requireNotNull(creator.id), fork, RoleType.MANAGER)
            return fork
        } catch (e: Exception) {
            repositories.deleteRepository(fork)
            throw e
        }
    }

    private fun copyMenuSetting(original: Project, fork: Project) {
        val source = menuSettings.findFirstByProjectId(requireNotNull(original.id))
        menuSettings.save(
            ProjectMenuSetting(
                project = fork, code = source?.code ?: true, issue = source?.issue ?: true, pullRequest = source?.pullRequest ?: true,
                review = source?.review ?: true, milestone = source?.milestone ?: true, board = source?.board ?: true
            )
        )
    }

    private fun assignRole(userId: Long, project: Project, roleType: RoleType) {
        projectUsers.save(ProjectUser(user = users.getReferenceById(userId), project = project, role = roles.getReferenceById(roleType.id)))
    }

    private companion object {
        /** 사이트 관리자의 사용자 번호이다 (v1.6 `User.SITE_MANAGER_ID`). */
        const val SITE_MANAGER_ID = 1L
    }
}
