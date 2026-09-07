package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.*
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.internal.JGitText
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.text.MessageFormat
import java.util.Locale

@Controller
class ImportViewController(
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

    @GetMapping("/new/import")
    fun importForm(
        @RequestParam(required = false) owner: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = getLoginUser(authentication) ?: return "redirect:/users/loginform"
        val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
        val organizations = orgUserList.map { it.organization }

        val form = ImportForm().apply {
            this.owner = owner ?: loginUser.loginId
        }

        model.addAttribute("importForm", form)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("organizations", organizations)
        return "project/importing"
    }

    @PostMapping("/new/import")
    fun newProject(
        @ModelAttribute("importForm") @Valid form: ImportForm,
        bindingResult: BindingResult,
        authentication: Authentication?,
        model: Model,
        response: HttpServletResponse,
        locale: Locale
    ): String {
        val loginUser = getLoginUser(authentication) ?: return "redirect:/users/loginform"
        if (loginUser.isGuest) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Guest users cannot create projects.")
        }

        val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
        val organizations = orgUserList.map { it.organization }

        model.addAttribute("currentUser", loginUser)
        model.addAttribute("organizations", organizations)

        // 1. 기본 Validation
        validateImportForm(form, bindingResult, loginUser)

        if (bindingResult.hasErrors()) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return "project/importing"
        }

        val targetOwner = form.owner.trim()
        val targetName = form.name.trim()

        var clonedDir: File? = null
        try {
            // 2. Git Repository 복제
            clonedDir = gitService.cloneRepository(
                form.url.trim(),
                targetOwner,
                targetName,
                form.authId,
                form.authPw
            )

            // 3. Project 엔티티 준비
            val project = Project().apply {
                this.name = targetName
                this.owner = targetOwner
                this.overview = form.overview.trim()
                this.projectScope = form.projectScope
                this.vcs = "GIT"
                this.isCodeEnabled = form.code
                this.isIssueEnabled = form.issue
                this.isPullRequestEnabled = form.pullRequest
                this.isReviewEnabled = form.review
                this.isMilestoneEnabled = form.milestone
                this.isBoardEnabled = form.board
            }

            // 조직인 경우 연동
            val organization = organizationRepository.findByName(targetOwner).orElse(null)
            if (organization != null) {
                project.organization = organization
            }

            // 4. Project 생성
            val savedProject = projectService.createProject(project, loginUser)

            return "redirect:/${targetOwner.encodePathSegment()}/${targetName.encodePathSegment()}"
        } catch (e: InvalidRemoteException) {
            bindingResult.rejectValue("url", "project.import.error.wrong.url")
        } catch (e: JGitInternalException) {
            bindingResult.rejectValue("url", "project.import.error.wrong.url")
        } catch (e: TransportException) {
            addTransportError(form, bindingResult, e)
        } catch (e: Exception) {
            bindingResult.rejectValue("url", "project.import.error.transport", arrayOf("Unknown"), "Git Import Failed")
        }

        // 복제 실패 시 생성 중인 디렉터리 삭제
        clonedDir?.let {
            if (it.exists()) {
                it.deleteRecursively()
            }
        }
        val defaultRepoDir = gitService.getRepositoryPath(targetOwner, targetName)
        if (defaultRepoDir.exists()) {
            defaultRepoDir.deleteRecursively()
        }

        response.status = HttpServletResponse.SC_BAD_REQUEST
        return "project/importing"
    }

    private fun validateImportForm(form: ImportForm, bindingResult: BindingResult, loginUser: User) {
        val owner = form.owner.trim()
        val name = form.name.trim()

        val ownerIsUser = userRepository.findByLoginId(owner).isPresent
        val ownerOrg = organizationRepository.findByName(owner).orElse(null)
        val ownerIsOrg = ownerOrg != null

        if (!ownerIsUser && !ownerIsOrg) {
            bindingResult.rejectValue("owner", "project.owner.invalidate")
        }

        if (ownerIsUser && owner != loginUser.loginId) {
            bindingResult.rejectValue("owner", "project.owner.invalidate")
        }

        if (ownerIsOrg) {
            val isAdmin = organizationUserRepository.findByOrganizationIdAndUserId(ownerOrg!!.id!!, loginUser.id!!)
                .map { it.role.id == RoleType.ORG_ADMIN.roleType }
                .orElse(false)
            if (!isAdmin) {
                bindingResult.rejectValue("owner", "project.owner.invalidate")
            }
        }

        if (projectRepository.findByOwnerAndName(owner, name).isPresent) {
            bindingResult.rejectValue("name", "project.name.duplicate")
        }

        if (form.url.isBlank()) {
            bindingResult.rejectValue("url", "project.import.error.empty.url")
        }
    }

    private fun addTransportError(form: ImportForm, bindingResult: BindingResult, e: TransportException) {
        val errorMessage = e.message ?: ""
        val hasNoCredentials = form.authId.isNullOrEmpty() && form.authPw.isNullOrEmpty()

        if (errorMessage.contains(JGitText.get().notAuthorized)) {
            if (hasNoCredentials) {
                bindingResult.rejectValue("url", "project.import.error.transport.unauthorized")
                bindingResult.rejectValue("repoAuth", "required") // 레거시 뷰 호환용 더미 에러
            } else {
                bindingResult.rejectValue("authId", "project.import.error.transport.failedToAuth")
            }
        } else if (errorMessage.contains(MessageFormat.format(JGitText.get().serviceNotPermitted, ""))) {
            bindingResult.rejectValue("url", "project.import.error.transport.forbidden")
        } else {
            val parts = errorMessage.split(" ")
            val statusCode = if (parts.size > 1) parts[1] else "Unknown"
            bindingResult.rejectValue("url", "project.import.error.transport", arrayOf(statusCode), "Git Import Failed")
        }
    }
}

class ImportForm {
    var url: String = ""
    var owner: String = ""
    var name: String = ""
    var overview: String = ""
    var projectScope: ProjectScope = ProjectScope.PUBLIC
    var vcs: String = "GIT"
    var authId: String? = null
    var authPw: String? = null
    var code: Boolean = true
    var issue: Boolean = true
    var pullRequest: Boolean = true
    var review: Boolean = true
    var milestone: Boolean = true
    var board: Boolean = true
    var repoAuth: String? = null // 레거시 에러 바인딩 호환용
}
