package com.github.yonaprojects.yona.domain.site

import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class SiteService(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val projectService: ProjectService,
    private val recentIssueService: RecentIssueService,
    private val attachmentRepository: AttachmentRepository
) {

    @Transactional
    fun toggleAccountLock(loginId: String) {
        val targetUser = userRepository.findByLoginId(loginId).orElse(null)
        if (targetUser != null) {
            targetUser.state = if (targetUser.state == UserState.LOCKED) UserState.ACTIVE else UserState.LOCKED
            targetUser.lastStateModifiedDate = Instant.now()
            userRepository.save(targetUser)
        }
    }

    @Transactional
    fun toggleGuestMode(loginId: String) {
        val targetUser = userRepository.findByLoginId(loginId).orElse(null)
        if (targetUser != null) {
            targetUser.isGuest = !targetUser.isGuest
            userRepository.save(targetUser)
        }
    }

    @Transactional
    fun toggleSiteAdminRole(loginId: String) {
        val targetUser = userRepository.findByLoginId(loginId).orElse(null)
        if (targetUser != null) {
            targetUser.state = if (targetUser.state == UserState.SITE_ADMIN) UserState.ACTIVE else UserState.SITE_ADMIN
            targetUser.lastStateModifiedDate = Instant.now()
            userRepository.save(targetUser)
        }
    }

    @Transactional
    fun resetUserPassword(loginId: String): String {
        val targetUser = userRepository.findByLoginId(loginId).orElse(null)
            ?: throw IllegalArgumentException("USER_NOT_FOUND")

        val newPassword = UUID.randomUUID().toString().substring(0, 6)
        val salt = UUID.randomUUID().toString().substring(0, 8)

        targetUser.passwordSalt = salt
        targetUser.password = hashPassword(newPassword, salt)
        userRepository.save(targetUser)

        return newPassword
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }

    @Transactional
    fun deleteUser(userId: Long) {
        if (isOnlyManager(userId)) {
            throw IllegalStateException("ONLY_MANAGER")
        }

        val targetUser = userRepository.findById(userId).orElse(null)
            ?: throw IllegalArgumentException("USER_NOT_FOUND")

        // 사용자의 프로젝트 멤버십 관계 수동 소거
        val projectUsers = projectUserRepository.findByUserId(userId)
        projectUserRepository.deleteAll(projectUsers)

        targetUser.state = UserState.DELETED
        targetUser.lastStateModifiedDate = Instant.now()
        userRepository.save(targetUser)

        // yona models/RecentIssue.java deleteAll(user) 대응 (P1-41).
        recentIssueService.deleteAll(targetUser)
    }

    @Transactional
    fun deleteProject(projectId: Long) {
        projectService.deleteProject(projectId)
    }

    fun isOnlyManager(userId: Long): Boolean {
        // 이 사용자가 MANAGER인 프로젝트들 중, 해당 프로젝트 내 MANAGER 수가 1명 이하(자기자신 뿐)인 프로젝트가 있는지 확인
        val projectUsers = projectUserRepository.findByUserId(userId)
        val managedProjects = projectUsers.filter { it.role.id == RoleType.MANAGER.roleType }.map { it.project }
        for (project in managedProjects) {
            val managers = projectUserRepository.findByProjectId(project.id!!)
                .filter { it.role.id == RoleType.MANAGER.roleType }
            if (managers.size <= 1) {
                return true
            }
        }
        return false
    }

    fun getMailList(all: Boolean, projectNames: List<String>): List<String> {
        val emails = mutableSetOf<String>()

        if (all) {
            val users = userRepository.findAll()
            for (user in users) {
                if (!user.email.isNullOrBlank()) {
                    emails.add(user.email)
                }
            }
        } else {
            for (projName in projectNames) {
                val parts = projName.split("/")
                if (parts.size == 2) {
                    val owner = parts[0]
                    val name = parts[1]
                    val project = projectRepository.findByOwnerAndName(owner, name).orElse(null)
                    if (project != null) {
                        val projectUsers = projectUserRepository.findByProjectId(project.id!!)
                        for (pu in projectUsers) {
                            if (!pu.user.email.isNullOrBlank()) {
                                emails.add(pu.user.email)
                            }
                        }
                    }
                }
            }
        }

        return emails.toList().sorted()
    }

    // yona SiteApp.noAvatarUsers() 대응 (P2-03). avatarId()가 null인(= USER_AVATAR 컨테이너에
    // 첨부파일이 없는) 사용자만 걸러낸다.
    fun getNoAvatarUsers(): List<Map<String, String>> {
        val activeUsers = userRepository.findAll().filter { it.state == UserState.ACTIVE || it.state == UserState.SITE_ADMIN }
        return activeUsers
            .filter { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, it.id.toString()).isEmpty() }
            .map {
                mapOf(
                    "loginId" to it.loginId,
                    "name" to it.name,
                    "email" to (it.email ?: "")
                )
            }
    }

    // yona SiteApp.setAttachmentToUserAvatar() 대응 (P2-03). 지정한 이미지 첨부파일을
    // 대상 사용자의 아바타(USER_AVATAR 컨테이너)로 옮기고, 기존 아바타 첨부파일은 제거한다.
    @Transactional
    fun setUserAvatar(avatarFileId: Long, email: String) {
        val attachment = attachmentRepository.findById(avatarFileId).orElse(null)
            ?: throw IllegalArgumentException("ATTACHMENT_NOT_FOUND")
        val mimeType = attachment.mimeType ?: ""
        if (!mimeType.startsWith("image", ignoreCase = true)) {
            throw IllegalArgumentException("NOT_AN_IMAGE")
        }

        val targetUser = userRepository.findByEmail(email).orElse(null)
            ?: throw IllegalArgumentException("USER_NOT_FOUND")

        val oldAvatars = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, targetUser.id.toString())
        attachmentRepository.deleteAll(oldAvatars)

        attachment.containerType = ResourceType.USER_AVATAR
        attachment.containerId = targetUser.id.toString()
        attachmentRepository.save(attachment)
    }
}
