package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class ProjectUserServiceImpl(
    private val projectUserRepository: ProjectUserRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val watchService: WatchService
) : ProjectUserService {

    override fun getProjectMembers(projectId: Long): List<ProjectUser> {
        return projectUserRepository.findByProjectId(projectId)
    }

    @Transactional
    override fun enroll(projectId: Long, userId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        // legacy ProjectUser.isGuest() 가드 대응: 이미 프로젝트 멤버라면 가입 신청 자체를 거부한다.
        if (projectUserRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw IllegalArgumentException("이미 프로젝트 멤버입니다.")
        }

        // legacy User.enrolled() 가드 대응: 이미 대기 중인 가입 신청이 있으면 조용히 무시하고
        // 중복 알림을 발생시키지 않는다(legacy도 이 경우 badRequest가 아니라 그냥 ok()를 반환).
        if (user.enrolledProjects.any { it.id == project.id }) {
            return
        }

        user.enroll(project)
        userRepository.save(user)

        // 가입 신청 알림 생성
        val managers = getProjectManagers(projectId)
        val notiEvent = NotificationEvent(
            title = "${project.name} 프로젝트에 ${user.loginId}님이 가입 신청을 보냈습니다.",
            senderId = userId,
            receivers = managers.toMutableSet(),
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = projectId.toString(),
            eventType = EventType.MEMBER_ENROLL_REQUEST,
            newValue = "REQUEST",
            oldValue = "CANCEL"
        )
        notificationEventRecorder.record(notiEvent)
    }

    @Transactional
    override fun cancelEnroll(projectId: Long, userId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        // legacy EnrollProjectApp.java의 ProjectUser.isGuest() 가드 대응(가입 신청 대응과 대칭).
        // 이미 프로젝트 정식 멤버라면 가입 신청 취소 자체가 성립하지 않는다.
        if (projectUserRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw IllegalArgumentException("이미 프로젝트 멤버입니다.")
        }

        // legacy EnrollProjectApp.java의 User.enrolled(project) 가드 대응. 실제 대기 중인 가입
        // 신청이 없으면 취소할 것도, 알릴 것도 없다(조용히 무시).
        if (user.enrolledProjects.none { it.id == project.id }) {
            return
        }

        user.cancelEnroll(project)
        userRepository.save(user)

        // 가입 취소 알림 생성
        val managers = getProjectManagers(projectId)
        val notiEvent = NotificationEvent(
            title = "${project.name} 프로젝트에 ${user.loginId}님의 가입 신청이 취소되었습니다.",
            senderId = userId,
            receivers = managers.toMutableSet(),
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = projectId.toString(),
            eventType = EventType.MEMBER_ENROLL_REQUEST,
            newValue = "CANCEL",
            oldValue = "REQUEST"
        )
        notificationEventRecorder.record(notiEvent)
    }

    @Transactional
    override fun acceptMemberRequest(projectId: Long, userId: Long, approverId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        if (projectUserRepository.existsByProjectIdAndUserId(projectId, userId)) {
            // 이미 가입이 완료된 유저라면 대기 신청 목록에서만 소거
            user.cancelEnroll(project)
            userRepository.save(user)
            return
        }

        user.cancelEnroll(project)
        userRepository.save(user)

        val memberRole = roleRepository.findById(RoleType.MEMBER.roleType)
            .orElseThrow { IllegalStateException("MEMBER role not found") }

        val projectUser = ProjectUser(
            project = project,
            user = user,
            role = memberRole
        )
        projectUserRepository.save(projectUser)

        // 승인 완료 알림 발송
        val notiEvent = NotificationEvent(
            title = "${project.name} 프로젝트 가입 신청이 승인되었습니다.",
            senderId = approverId,
            receivers = mutableSetOf(user),
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = projectId.toString(),
            eventType = EventType.MEMBER_ENROLL_ACCEPT,
            newValue = "ACCEPT",
            oldValue = "REQUEST"
        )
        notificationEventRecorder.record(notiEvent)
    }

    @Transactional
    override fun rejectMemberRequest(projectId: Long, userId: Long, rejecterId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        user.cancelEnroll(project)
        userRepository.save(user)
    }

    @Transactional
    override fun addMember(projectId: Long, loginId: String, updaterId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val targetUser = userRepository.findByLoginId(loginId)
            .orElseThrow { IllegalArgumentException("User with LoginID $loginId not found") }

        if (projectUserRepository.existsByProjectIdAndUserId(projectId, targetUser.id!!)) {
            throw IllegalArgumentException("User is already a member of this project")
        }

        // 대기 신청에 있던 유저라면 대기 목록에서 소거
        if (targetUser.enrolledProjects.contains(project)) {
            targetUser.cancelEnroll(project)
            userRepository.save(targetUser)
        }

        val memberRole = roleRepository.findById(RoleType.MEMBER.roleType)
            .orElseThrow { IllegalStateException("MEMBER role not found") }

        val projectUser = ProjectUser(
            project = project,
            user = targetUser,
            role = memberRole
        )
        projectUserRepository.save(projectUser)

        // 추가 알림 발송 (가입 완료)
        val notiEvent = NotificationEvent(
            title = "${project.name} 프로젝트에 멤버로 추가되었습니다.",
            senderId = updaterId,
            receivers = mutableSetOf(targetUser),
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = projectId.toString(),
            eventType = EventType.MEMBER_ENROLL_ACCEPT,
            newValue = "ACCEPT",
            oldValue = "NONE"
        )
        notificationEventRecorder.record(notiEvent)
    }

    @Transactional
    override fun updateMemberRole(projectId: Long, userId: Long, newRoleId: Long, updaterId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        // 소유자는 강등 불가
        if (project.owner == user.loginId) {
            throw IllegalArgumentException("Owner's role cannot be modified or degraded")
        }

        val projectUser = projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow { IllegalArgumentException("User is not a member of this project") }

        val newRole = roleRepository.findById(newRoleId)
            .orElseThrow { IllegalArgumentException("Role with ID $newRoleId not found") }

        projectUser.role = newRole
        projectUserRepository.save(projectUser)
    }

    @Transactional
    override fun removeMember(projectId: Long, userId: Long, removerId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        // 소유자는 탈퇴 불가
        if (project.owner == user.loginId) {
            throw IllegalArgumentException("Owner cannot leave or be removed from the project")
        }

        projectUserRepository.deleteByProjectIdAndUserId(projectId, userId)
    }

    // legacy NotificationEvent.java의 getReceivers(Project) 대응 — 프로젝트 매니저 전원이 아니라
    // 그중 실제로 이 프로젝트를 감시(Watch) 중인 매니저만 가입요청/취소 알림을 받는다.
    private fun getProjectManagers(projectId: Long): List<User> {
        // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md 참고): role.id는 타입상 Long?이지만 Role은
        // 코드베이스 전역에서 RoleType 고정 ID로만 생성·영속화돼 실제로는 결코 null이 아니다.
        return projectUserRepository.findByProjectId(projectId)
            .filter { it.role.id == RoleType.MANAGER.roleType }
            .map { it.user }
            .filter { watchService.isWatching(it, ResourceType.PROJECT, projectId.toString()) }
    }
}
