package dev.yona.domain.project

import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.misc.WatchService
import dev.yona.domain.notification.ProjectMemberRequestEvent
import dev.yona.domain.user.Role
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트 구성원 관리이다. v1.6 `ProjectApp`의 `members`, `newMember`, `editMember`, `deleteMember`와
 * `ProjectUser`, `Project.cleanEnrolledUsers`에 해당한다.
 */
@Service
@Transactional
class ProjectMemberService(
    private val projectUsers: ProjectUserRepository,
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val watchService: WatchService,
    private val events: ApplicationEventPublisher,
    private val sessionUsers: dev.yona.security.SessionUserCache
) {

    /** 구성원 추가가 실패한 이유이다. [messageKey]는 사용자에게 보여 줄 메시지 키이다. */
    enum class AddFailure(val messageKey: String) {
        NOT_EXIST("project.member.notExist"),
        ALREADY_MEMBER("project.member.alreadyMember")
    }

    /** 구성원 추가 결과이다. 실패하면 [failure]가 있다. */
    data class AddResult(val newMember: User?, val failure: AddFailure?)

    /** 프로젝트 구성원이다. 사이트 관리자 역할은 빼고 사용자 이름 오름차순이다. */
    @Transactional(readOnly = true)
    fun members(project: Project): List<ProjectUser> = projectUsers.findMembers(requireNotNull(project.id), RoleType.SITEMANAGER.id)

    /** 프로젝트 역할로 고를 수 있는 역할이다(관리자, 멤버) (v1.6 `Role.findProjectRoles`). */
    @Transactional(readOnly = true)
    fun projectRoles(): List<Role> = roles.findAllById(listOf(RoleType.MANAGER.id, RoleType.MEMBER.id)).sortedBy { it.id }

    /** 번호로 사용자를 찾는다. 없으면 v1.6의 `NullPointerException`처럼 오류이다. */
    @Transactional(readOnly = true)
    fun user(userId: Long): User = users.findById(userId).orElseThrow { NullPointerException("사용자가 없습니다: $userId") }

    /** 가입을 신청한 사용자이다. */
    @Transactional(readOnly = true)
    fun enrolledUsers(project: Project): List<User> = users.findEnrolledUsers(requireNotNull(project.id))

    /**
     * 이미 구성원인데 가입 신청이 남아 있는 사용자의 신청을 정리하고 승인 이벤트를 알린다
     * (v1.6 `Project.cleanEnrolledUsers`).
     */
    fun cleanEnrolledUsers(project: Project, actor: User?) {
        val enrolled = users.findEnrolledUsers(requireNotNull(project.id))
        val memberIds = projectUsers.findMembersIncludingSiteManager(project.id!!).mapNotNull { it.user?.id }.toSet()
        enrolled.filter { it.id in memberIds }.forEach { user ->
            user.enrolledProjects.removeIf { it.id == project.id }
            users.save(user)
            // v1.6 User.cancelEnroll(Project): 새로 읽은 사용자 인스턴스를 서버 캐시에 넣는다
            sessionUsers.remember(requireNotNull(user.id))
            events.publishEvent(ProjectMemberRequestEvent(project, user, RequestState.ACCEPT, actor))
        }
    }

    /**
     * [loginId]의 사용자를 구성원(역할 멤버)으로 추가한다. 추가하면 그 사용자의 가입 신청을 정리하고 승인 이벤트를 알린다.
     * 사용자가 없거나 이미 구성원이면 추가하지 않고 이유를 돌려준다.
     */
    fun addMember(project: Project, actor: User?, loginId: String?): AddResult {
        val user = loginId?.let { users.findByLoginId(it) }
            ?: return AddResult(null, AddFailure.NOT_EXIST)
        if (isMember(user, project)) return AddResult(null, AddFailure.ALREADY_MEMBER)

        assignRole(requireNotNull(user.id), project, requireNotNull(roles.findById(RoleType.MEMBER.id).orElse(null)))
        cleanEnrolledUsers(project, actor)
        events.publishEvent(ProjectMemberRequestEvent(project, user, RequestState.ACCEPT, actor))
        return AddResult(user, null)
    }

    /** 새 구성원이 프로젝트를 구독하게 한다 (v1.6 `Watch.watch`). */
    fun watch(user: User, project: Project) {
        watchService.watch(user, ResourceType.PROJECT, requireNotNull(project.id).toString())
    }

    /** 사용자의 역할을 [roleId]로 정한다. 구성원이 아니면 새로 만든다 (v1.6 `ProjectUser.assignRole`). */
    fun changeRole(project: Project, userId: Long, roleId: Long) {
        // v1.6 `ProjectUser`는 `Role.findById(roleId)`로 역할을 채우므로 없는 번호이면 역할이 `null`이 된다
        assignRole(userId, project, roles.findById(roleId).orElse(null))
    }

    /** 구성원에서 뺀다. 구성원이 아니면 아무것도 하지 않는다 (v1.6 `ProjectUser.delete`). */
    fun removeMember(project: Project, userId: Long) {
        projectUsers.findFirstByUserIdAndProjectIdAndRoleIdNot(userId, requireNotNull(project.id), RoleType.SITEMANAGER.id)
            ?.let { projectUsers.delete(it) }
    }

    /** v1.6 `user.isMemberOf(project)`는 `ProjectUser.isMember`이고 역할을 가리지 않고 행이 있으면 참이다. 사이트 관리자 행도 센다. */
    private fun isMember(user: User, project: Project): Boolean =
        projectUsers.existsByUserIdAndProjectId(requireNotNull(user.id), requireNotNull(project.id))

    private fun assignRole(userId: Long, project: Project, role: Role?) {
        val existing = projectUsers.findFirstByUserIdAndProjectIdAndRoleIdNot(userId, requireNotNull(project.id), RoleType.SITEMANAGER.id)
        if (existing == null) {
            projectUsers.save(ProjectUser(user = users.getReferenceById(userId), project = project, role = role))
        } else {
            existing.role = role
            projectUsers.save(existing)
        }
    }
}
