package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.*
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.validation.Valid
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.internal.JGitText
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.io.File
import java.text.MessageFormat
import java.util.Locale

@RestController
class ImportApiController(
    private val projectService: ProjectService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val organizationRepository: OrganizationRepository,
    private val gitService: GitService,
    private val messageSource: MessageSource
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @PostMapping("/api/new/import")
    fun importProject(
        @RequestBody @Valid request: ImportApiRequest,
        authentication: Authentication?,
        locale: Locale
    ): ResponseEntity<Any> {
        val loginUser = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (loginUser.isGuest) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Guest users cannot create projects."))
        }

        // 1. Validation
        val targetOwner = request.owner.trim()
        val targetName = request.name.trim()

        val ownerIsUser = userRepository.findByLoginId(targetOwner).isPresent
        val ownerOrg = organizationRepository.findByName(targetOwner).orElse(null)
        val ownerIsOrg = ownerOrg != null

        if (!ownerIsUser && !ownerIsOrg) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid owner"))
        }

        if (ownerIsUser && targetOwner != loginUser.loginId) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid owner"))
        }

        if (ownerIsOrg) {
            val isAdmin = organizationUserRepository.findByOrganizationIdAndUserId(ownerOrg!!.id!!, loginUser.id!!)
                .map { it.role.id == RoleType.ORG_ADMIN.roleType }
                .orElse(false)
            if (!isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "No permission for this organization"))
            }
        }

        if (projectRepository.findByOwnerAndName(targetOwner, targetName).isPresent) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Project name already exists"))
        }

        if (request.url.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "URL cannot be empty"))
        }

        var clonedDir: File? = null
        try {
            // JGit 복제
            clonedDir = gitService.cloneRepository(
                request.url.trim(),
                targetOwner,
                targetName,
                request.authId,
                request.authPw
            )

            val project = Project().apply {
                this.name = targetName
                this.owner = targetOwner
                this.overview = request.overview.trim()
                this.projectScope = request.projectScope
                this.vcs = "GIT"
                this.isCodeEnabled = request.code
                this.isIssueEnabled = request.issue
                this.isPullRequestEnabled = request.pullRequest
                this.isReviewEnabled = request.review
                this.isMilestoneEnabled = request.milestone
                this.isBoardEnabled = request.board
                this.isWikiEnabled = request.wiki
            }

            if (ownerOrg != null) {
                project.organization = ownerOrg
            }

            val savedProject = projectService.createProject(project, loginUser)

            return ResponseEntity.ok(
                ImportApiResponse(
                    id = savedProject.id!!,
                    name = savedProject.name,
                    owner = savedProject.owner!!,
                    overview = savedProject.overview ?: ""
                )
            )
        } catch (e: InvalidRemoteException) {
            return ResponseEntity.badRequest().body(mapOf("error" to messageSource.getMessage("project.import.error.wrong.url", null, locale)))
        } catch (e: JGitInternalException) {
            return ResponseEntity.badRequest().body(mapOf("error" to messageSource.getMessage("project.import.error.wrong.url", null, locale)))
        } catch (e: TransportException) {
            val errorMessage = e.message ?: ""
            val hasNoCredentials = request.authId.isNullOrEmpty() && request.authPw.isNullOrEmpty()
            val errorMsg = if (errorMessage.contains(JGitText.get().notAuthorized)) {
                if (hasNoCredentials) {
                    messageSource.getMessage("project.import.error.transport.unauthorized", null, locale)
                } else {
                    messageSource.getMessage("project.import.error.transport.failedToAuth", null, locale)
                }
            } else if (errorMessage.contains(MessageFormat.format(JGitText.get().serviceNotPermitted, ""))) {
                messageSource.getMessage("project.import.error.transport.forbidden", null, locale)
            } else {
                val parts = errorMessage.split(" ")
                val statusCode = if (parts.size > 1) parts[1] else "Unknown"
                messageSource.getMessage("project.import.error.transport", arrayOf(statusCode), locale)
            }
            return ResponseEntity.badRequest().body(mapOf("error" to errorMsg))
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Git Import Failed")))
        } finally {
            clonedDir?.let {
                if (it.exists()) {
                    it.deleteRecursively()
                }
            }
            val defaultRepoDir = gitService.getRepositoryPath(targetOwner, targetName)
            if (defaultRepoDir.exists()) {
                defaultRepoDir.deleteRecursively()
            }
        }
    }
}

data class ImportApiRequest(
    val url: String,
    val owner: String,
    val name: String,
    val overview: String = "",
    val projectScope: ProjectScope = ProjectScope.PUBLIC,
    val authId: String? = null,
    val authPw: String? = null,
    val code: Boolean = true,
    val issue: Boolean = true,
    val pullRequest: Boolean = true,
    val review: Boolean = true,
    val milestone: Boolean = true,
    val board: Boolean = true,
    val wiki: Boolean = true
)

data class ImportApiResponse(
    val id: Long,
    val name: String,
    val owner: String,
    val overview: String
)
