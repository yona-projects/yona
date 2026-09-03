package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional

@Service
class IssueShareServiceImpl(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueSharerRepository: IssueSharerRepository,
    private val issueRepository: IssueRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val eventPublisher: ApplicationEventPublisher,
    private val issueEventRepository: IssueEventRepository,
    // yona-wiki P3-01(Observability) 계측 지점 2 대응 — IssueEventRepository.recordWithDraftMerge()에 그대로 전달한다.
    private val meterRegistry: MeterRegistry
) : IssueShareService {

    private val maxFetchUsers = 50

    override fun findAssignableUsersOfProject(project: Project, query: String, currentUser: User): List<Map<String, Any>> {
        val users = mutableListOf<Map<String, Any>>()

        if (query.isBlank()) {
            users.add(mapUser(currentUser, "나에게 지정"))
            for (u in getAssignableUsersOfProjectInternal(project, currentUser)) {
                users.add(mapUser(u))
            }
            return users
        }

        val processedQuery = "%${query.lowercase()}%"
        val el = userRepository.searchUsers(processedQuery, PageRequest.of(0, maxFetchUsers))
        for (u in el.content) {
            if (project.projectScope == ProjectScope.PUBLIC) {
                users.add(mapUser(u))
            } else {
                if (isMemberOfProject(u, project)) {
                    users.add(mapUser(u))
                }
            }
        }
        return users
    }

    override fun findAssignableUsers(issue: Issue, query: String, currentUser: User): List<Map<String, Any>> {
        val users = mutableListOf<Map<String, Any>>()
        val project = issue.project

        if (query.isBlank()) {
            val issueAuthor = issue.authorId?.let { userRepository.findById(it).orElse(null) }

            if (issue.assignee != null) {
                if (currentUser.id != issue.assignee?.user?.id) {
                    users.add(mapUser(currentUser, "나에게 지정"))
                }
                if (issueAuthor != null && issueAuthor.id != currentUser.id && issueAuthor.id != issue.assignee?.user?.id) {
                    users.add(mapUser(issueAuthor, "작성자에게 지정"))
                }
                users.add(mapUserAnonymous("담당자 해제"))
                users.add(mapUser(issue.assignee!!.user))
            } else {
                users.add(mapUser(currentUser, "나에게 지정"))
                if (issueAuthor != null && issueAuthor.id != currentUser.id) {
                    users.add(mapUser(issueAuthor, "작성자에게 지정"))
                }
            }

            val assignable = getAssignableUsersOfProjectInternal(project, currentUser)
            for (u in assignable) {
                users.add(mapUser(u))
            }
            return users
        }

        val processedQuery = "%${query.lowercase()}%"
        val el = userRepository.searchUsers(processedQuery, PageRequest.of(0, maxFetchUsers))
        for (u in el.content) {
            if (project.projectScope == ProjectScope.PUBLIC) {
                users.add(mapUser(u))
            } else {
                if (isMemberOfProject(u, project)) {
                    users.add(mapUser(u))
                }
            }
        }
        return users
    }

    override fun findSharerByloginIds(issue: Issue, commaSeperatedIds: String): List<Map<String, Any>> {
        val queryItems = commaSeperatedIds.split(",").filter { it.isNotBlank() }
        if (queryItems.isEmpty()) return emptyList()

        val list = issue.sharers.filter { it.loginId in queryItems }.sortedBy { it.created }
        return list.map { mapUser(it.user) }
    }

    override fun findSharableUsers(query: String, type: String?): List<Map<String, Any>> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<Map<String, Any>>()
        val processedQuery = "%${query.lowercase()}%"

        val userPage = userRepository.searchUsers(processedQuery, PageRequest.of(0, maxFetchUsers / 2))
        for (u in userPage.content) {
            results.add(mapUser(u))
        }

        val publicProjectIds = projectRepository.findPublicProjectIds()
        if (publicProjectIds.isNotEmpty()) {
            val projectPage = projectRepository.searchProjects(publicProjectIds, processedQuery, PageRequest.of(0, maxFetchUsers / 2))
            for (p in projectPage.content) {
                results.add(mapProject(p))
            }
        }

        return results
    }

    @Transactional
    override fun changeSharer(issue: Issue, targetLoginIdOrProjectId: String, type: String, action: String, currentUser: User): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val usersToNotify = mutableListOf<String>()

        if (type == "project") {
            val projectId = targetLoginIdOrProjectId.toLongOrNull() ?: throw IllegalArgumentException("Invalid project id")
            val projectUsers = projectUserRepository.findByProjectId(projectId)
            
            for (pu in projectUsers) {
                val loginId = pu.user.loginId
                if (action == "add") {
                    addSharerInternal(issue, loginId, pu.user)
                } else if (action == "delete") {
                    removeSharerInternal(issue, loginId)
                }
                usersToNotify.add(loginId)
            }
            
            val p = projectRepository.findById(projectId).orElseThrow { IllegalArgumentException("Project not found") }
            setShareActionToResponse(action, result)
            result["sharer"] = p.name
        } else {
            val loginId = targetLoginIdOrProjectId
            val targetUser = userRepository.findByLoginId(loginId).orElseThrow { IllegalArgumentException("User not found") }
            
            if (action == "add") {
                addSharerInternal(issue, loginId, targetUser)
            } else if (action == "delete") {
                removeSharerInternal(issue, loginId)
            }
            usersToNotify.add(loginId)
            
            setShareActionToResponse(action, result)
            result["sharer"] = targetUser.getDisplayName()
        }

        for (loginId in usersToNotify) {
            sendNotification(issue, loginId, action, currentUser)
        }

        return result
    }

    private fun addSharerInternal(issue: Issue, loginId: String, user: User) {
        val existing = issueSharerRepository.findByLoginIdAndIssueId(loginId, issue.id!!)
        if (existing.isEmpty) {
            val issueSharer = IssueSharer(
                loginId = loginId,
                user = user,
                issue = issue,
                created = Instant.now()
            )
            issueSharerRepository.save(issueSharer)
            issue.sharers.add(issueSharer)
        }
    }

    private fun removeSharerInternal(issue: Issue, loginId: String) {
        val existing = issueSharerRepository.findByLoginIdAndIssueId(loginId, issue.id!!).orElse(null)
        if (existing != null) {
            issueSharerRepository.delete(existing)
            issue.sharers.remove(existing)
        }
    }

    private fun setShareActionToResponse(action: String, result: MutableMap<String, Any>) {
        if (action == "add") {
            result["action"] = "added"
        } else if (action == "delete") {
            result["action"] = "deleted"
        } else {
            result["action"] = "Do nothing. Unsupported action: $action"
        }
    }

    private fun sendNotification(issue: Issue, sharerLoginId: String, action: String, currentUser: User) {
        val receiver = userRepository.findByLoginId(sharerLoginId).orElse(null)
        val receivers = if (receiver != null) mutableSetOf(receiver) else mutableSetOf()
        
        val notificationEvent = NotificationEvent(
            title = "[${issue.project.name}] 이슈 #${issue.number} 공유 설정 변경",
            senderId = currentUser.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            eventType = EventType.ISSUE_SHARER_CHANGED,
            oldValue = if (action == "delete") sharerLoginId else "",
            newValue = if (action == "add") sharerLoginId else ""
        )
        notificationEvent.receivers = receivers
        // yona NotificationEvent.afterIssueSharerChanged()는 addWithoutSkipEvent()(skipWaypoint=false)를
        // 쓴다 — 중간 지점은 남기고 정확히 원상복구된 경우만 상쇄한다.
        notificationEventRecorder.record(notificationEvent, skipWaypoint = false)?.let { eventPublisher.publishEvent(it) }

        // 이슈 타임라인(IssueEvent) 기록 (P1-37)
        val issueEvent = IssueEvent(
            issue = issue,
            senderLoginId = currentUser.loginId!!,
            senderEmail = currentUser.email,
            oldValue = notificationEvent.oldValue,
            newValue = notificationEvent.newValue,
            created = Instant.now(),
            eventType = EventType.ISSUE_SHARER_CHANGED
        )
        issueEventRepository.recordWithDraftMerge(issueEvent, skipWaypoint = true, meterRegistry = meterRegistry)
    }

    private fun getAssignableUsersOfProjectInternal(project: Project, currentUser: User): List<User> {
        val userIds = mutableSetOf<Long>()

        val pUsers = projectUserRepository.findByProjectId(project.id!!)
        for (pu in pUsers) {
            userIds.add(pu.user.id!!)
        }

        val org = project.organization
        if (org != null) {
            if (project.projectScope == ProjectScope.PRIVATE) {
                val orgAdmins = organizationUserRepository.findByOrganizationIdAndRoleId(org.id!!, RoleType.ORG_ADMIN.roleType)
                for (ou in orgAdmins) {
                    userIds.add(ou.user.id!!)
                }
            } else {
                val orgUsers = organizationUserRepository.findByOrganizationId(org.id!!)
                for (ou in orgUsers) {
                    userIds.add(ou.user.id!!)
                }
            }
        }

        if (currentUser.isSiteManager) {
            userIds.add(currentUser.id!!)
        }

        if (userIds.isEmpty()) return emptyList()

        return userRepository.findAllById(userIds).sortedBy { it.name }
    }

    private fun isMemberOfProject(user: User, project: Project): Boolean {
        val isProjectMember = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!).isPresent
        if (isProjectMember) return true

        val org = project.organization
        if (org != null) {
            val isOrgMember = organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, user.id!!).isPresent
            if (isOrgMember) return true
        }

        return false
    }

    private fun mapUser(user: User, customName: String? = null): Map<String, Any> {
        return mapOf(
            "loginId" to user.loginId,
            "name" to (customName ?: user.getDisplayName()),
            "pureNameOnly" to user.getPureNameOnly(),
            "avatarUrl" to (user.avatarUrl ?: ""),
            "type" to "user"
        )
    }

    private fun mapUserAnonymous(customName: String): Map<String, Any> {
        return mapOf(
            "loginId" to "anonymous",
            "name" to customName,
            "avatarUrl" to "",
            "type" to "user"
        )
    }

    private fun mapProject(project: Project): Map<String, Any> {
        return mapOf(
            "loginId" to project.id.toString(),
            "name" to "${project.owner}/${project.name}",
            "avatarUrl" to "",
            "type" to "project"
        )
    }
}
