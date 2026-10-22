package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 외부 API가 프로젝트를 만들 때 받는 값이다.
 *
 * @property scope 공개 범위. 정하지 않으면 비공개이다
 * @property members `(사용자, 역할 이름)` 쌍. 역할은 `member`, `manager`(대소문자 무시)만 인정한다
 */
data class ExternalProject(val owner: String, val name: String, val overview: String, val vcs: String, val scope: ProjectScope, val members: List<Pair<User, String>>)

/**
 * 외부 REST API가 쓰는 프로젝트 도메인 동작이다. v1.6 `ProjectApi.newProject`(`app/controllers/api/ProjectApi.java:178-275`)에 해당한다.
 * 권한 확인은 호출하는 쪽이 한다.
 *
 * 화면의 프로젝트 만들기([ProjectCreateService])와 달리 만든 사람을 관리자로 넣지 않고(사이트 관리자 역할만 둔다) 구독하지도, 방문 기록을
 * 남기지도 않는다. 전달받은 만든 날짜는 v1.6 `Project.create`가 지금으로 덮어써서 쓰이지 않는다([refactoring-backlog.md] R-36).
 */
@Service
class ExternalProjectService(
    private val projects: ProjectRepository,
    private val organizations: OrganizationRepository,
    private val projectUsers: ProjectUserRepository,
    private val menuSettings: ProjectMenuSettingRepository,
    private val roles: RoleRepository,
    private val users: UserRepository,
    private val repositories: RepositoryService,
    private val memberService: ProjectMemberService,
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 프로젝트를 만든다. 저장소를 만들고, 메뉴를 모두 켜고, 구성원을 역할대로 넣는다. 이미 구성원이면 역할만 바꾼다.
     * 소유자가 조직이면 조직 소속으로 만든다. 구성원이 된 사용자의 가입 신청은 정리하며, 그 승인 알림의 처리자는 호출한 [actor]이다
     * (v1.6 `cleanEnrolledUsers`가 현재 사용자를 쓴다).
     */
    @Transactional
    fun create(spec: ExternalProject, actor: User): Project {
        val project = Project(owner = spec.owner, name = spec.name, overview = spec.overview, vcs = spec.vcs, projectScope = spec.scope, organization = organizations.findByNameIgnoreCase(spec.owner))
        project.siteurl = "http://localhost:9000/${project.name}"
        project.createdDate = Date()
        projects.save(project)
        projectUsers.save(ProjectUser(user = users.getReferenceById(SITE_MANAGER_ID), project = project, role = roles.getReferenceById(RoleType.SITEMANAGER.id)))
        repositories.createRepository(project)
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (spec.members.isNotEmpty()) {
            spec.members.forEach { (user, role) ->
                when (role.lowercase()) {
                    "member" -> assignRole(user, project, RoleType.MEMBER)
                    "manager" -> assignRole(user, project, RoleType.MANAGER)
                    else -> log.warn("알 수 없는 역할이다: {} ({})", role, user.loginId)
                }
            }
            entityManager.flush()
            memberService.cleanEnrolledUsers(project, actor)
        }
        return project
    }

    /** 구성원의 역할을 정한다. 사이트 관리자 역할이 아닌 기존 행이 있으면 역할만 바꾼다 (v1.6 `ProjectUser.assignRole`). */
    private fun assignRole(user: User, project: Project, role: RoleType) {
        val existing = projectUsers.findFirstByUserIdAndProjectIdAndRoleIdNot(requireNotNull(user.id), requireNotNull(project.id), RoleType.SITEMANAGER.id)
        if (existing == null) {
            projectUsers.save(ProjectUser(user = user, project = project, role = roles.getReferenceById(role.id)))
        } else {
            existing.role = roles.getReferenceById(role.id)
            projectUsers.save(existing)
        }
    }

    private companion object {
        /** 사이트 관리자의 사용자 번호이다 (v1.6 `User.SITE_MANAGER_ID`). */
        const val SITE_MANAGER_ID = 1L
    }
}
