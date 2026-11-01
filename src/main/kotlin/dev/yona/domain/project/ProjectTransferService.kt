package dev.yona.domain.project

import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.notification.ProjectTransferRequestedEvent
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Date

/**
 * 프로젝트 이전 요청과 수락이다. v1.6 `ProjectApp.transferProject`, `acceptTransfer`와 `models.ProjectTransfer`에 해당한다.
 */
@Service
@Transactional
class ProjectTransferService(
    private val transfers: ProjectTransferRepository,
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val projectUsers: ProjectUserRepository,
    private val users: UserRepository,
    private val organizations: OrganizationRepository,
    private val roles: RoleRepository,
    private val repositories: RepositoryService,
    private val events: ApplicationEventPublisher,
    private val audit: dev.yona.domain.audit.AuditRecorder
) {

    /** 이전 요청을 처리한 결과이다. */
    sealed interface RequestResult {
        /** 받는 곳이 사용자도 조직도 아니다. */
        data object UnknownDestination : RequestResult

        /** 이미 그 사용자나 조직의 프로젝트이다. */
        data object SameOwner : RequestResult

        /** 요청을 저장했다. */
        data class Requested(val transfer: ProjectTransfer) : RequestResult
    }

    /** 수락을 처리한 결과이다. */
    sealed interface AcceptResult {
        data object NotFound : AcceptResult
        data object WrongKey : AcceptResult
        data object Forbidden : AcceptResult
        data class Done(val project: Project) : AcceptResult
    }

    /**
     * [destination](로그인 ID 또는 조직 이름)으로 이전을 요청한다. 같은 프로젝트, 보낸 사람, 받는 곳의 요청이 이미 있으면
     * 새로 만들지 않고 열쇠와 시각만 새로 정한다. 저장하면 요청 이벤트를 알린다.
     */
    fun request(project: Project, sender: User, destination: String?): RequestResult {
        val destUser = destination?.let { users.findByLoginId(it) }
        val destOrg = destination?.let { organizations.findByNameIgnoreCase(it) }
        if (destUser == null && destOrg == null) return RequestResult.UnknownDestination

        val projectOwnerUser = project.owner?.let { users.findByLoginId(it) }
        val projectOrg = project.owner?.let { organizations.findByNameIgnoreCase(it) }
        if ((destUser != null && destUser.id == projectOwnerUser?.id) || (projectOrg != null && projectOrg.id == destOrg?.id)) {
            return RequestResult.SameOwner
        }

        // v1.6는 조직이면 사용자 대상 요청 뒤에 조직 대상 요청으로 덮어쓴다.
        val target = destOrg?.name ?: requireNotNull(destUser?.loginId)
        val transfer = newOrRefreshed(project, sender, target)
        events.publishEvent(ProjectTransferRequestedEvent(transfer))
        return RequestResult.Requested(transfer)
    }

    /**
     * 이전 요청을 수락한다. 하루 안의 아직 수락하지 않은 요청이어야 하고, 열쇠가 맞아야 하며, 받는 쪽이어야 한다.
     * 수락하면 프로젝트의 소유자, 이름, 조직을 바꾸고 저장소를 옮기며 역할을 조정한다.
     * v1.6처럼 처리한 요청 행은 지운다.
     */
    fun accept(id: Long, confirmKey: String, acceptor: User?, isAllowed: (ProjectTransfer) -> Boolean): AcceptResult {
        val transfer = findValid(id) ?: return AcceptResult.NotFound
        if (transfer.confirmKey != confirmKey) return AcceptResult.WrongKey
        if (!isAllowed(transfer)) return AcceptResult.Forbidden

        val project = requireNotNull(transfer.project)
        val destination = requireNotNull(transfer.destination)
        val senderId = transfer.sender?.id
        val oldOwner = requireNotNull(project.owner)
        val oldName = requireNotNull(project.name)
        val newName = projectService.newProjectName(destination, oldName)

        // v1.6 `disableProjectTransferLink`: 같은 보낸 사람과 받는 곳의 요청을 지운다. 처리한 요청 자신도 여기서 지워진다.
        transfers.findByProjectIdAndSenderIdAndDestination(requireNotNull(project.id), requireNotNull(senderId), destination)
            .forEach { transfers.delete(it) }
        transfers.flush()

        if (!repositories.moveRepository(project, destination, newName)) {
            throw IllegalStateException("fail repository move to $destination/$newName")
        }

        val newOwnerUser = users.findByLoginId(destination)
        val newOwnerOrg = organizations.findByNameIgnoreCase(destination)

        project.recordPreviousPlaceIfLastChangePassed24Hours()
        project.owner = destination
        project.name = newName
        project.organization = newOwnerOrg
        projects.save(project)

        val projectId = requireNotNull(project.id)
        if (senderId != null && projectUsers.existsByUserIdAndProjectIdAndRoleId(senderId, projectId, RoleType.MANAGER.id)) {
            assignRole(senderId, project, RoleType.MEMBER)
        }
        if (newOwnerUser != null) assignRole(requireNotNull(newOwnerUser.id), project, RoleType.MANAGER)
        check(oldOwner.isNotEmpty() && oldName.isNotEmpty())
        audit.record(dev.yona.domain.audit.auditProject("project.transferred", project, mapOf("from" to oldOwner, "to" to destination, "oldName" to oldName, "newName" to newName)))
        return AcceptResult.Done(project)
    }

    private fun findValid(id: Long): ProjectTransfer? {
        val transfer = transfers.findById(id).orElse(null) ?: return null
        val now = System.currentTimeMillis()
        val requested = transfer.requested?.time ?: return null
        return transfer.takeIf { transfer.accepted != true && requested in (now - ONE_DAY_MILLIS)..now }
    }

    private fun newOrRefreshed(project: Project, sender: User, destination: String): ProjectTransfer {
        val existing = transfers.findByProjectIdAndSenderIdAndDestination(requireNotNull(project.id), requireNotNull(sender.id), destination).firstOrNull()
        if (existing != null) {
            existing.requested = Date()
            existing.confirmKey = randomKey()
            return transfers.save(existing)
        }
        return transfers.save(
            ProjectTransfer(
                project = project, sender = sender, destination = destination, requested = Date(), confirmKey = randomKey(),
                newProjectName = projectService.newProjectName(destination, requireNotNull(project.name))
            )
        )
    }

    private fun assignRole(userId: Long, project: Project, roleType: RoleType) {
        val role = roles.getReferenceById(roleType.id)
        val existing = projectUsers.findFirstByUserIdAndProjectIdAndRoleIdNot(userId, requireNotNull(project.id), RoleType.SITEMANAGER.id)
        if (existing == null) projectUsers.save(ProjectUser(user = users.getReferenceById(userId), project = project, role = role))
        else projectUsers.save(existing.also { it.role = role })
    }

    private fun randomKey(): String = (1..KEY_LENGTH).map { ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)] }.joinToString("")

    private companion object {
        const val ONE_DAY_MILLIS = 24L * 3_600_000
        const val KEY_LENGTH = 50
        const val ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
    }
}
