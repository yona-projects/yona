package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.sshkey.SshPublicKeyFingerprint
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

// yona-wiki P3-03 Step1/Step2 — GitHub "Settings > Deploy keys" 화면과 동일한 저장소별 Deploy Key
// 등록/조회/삭제 UI. WebhookController와 동일한 컨벤션(Operation.UPDATE = 프로젝트 매니저 이상만).
@Controller
class DeployKeyController(
    private val deployKeyService: DeployKeyService,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.UPDATE)
    }

    @GetMapping("/projects/{owner}/{projectName}/deploy-keys")
    fun deployKeys(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
        if (!checkPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        model.addAttribute("project", project)
        model.addAttribute("deployKeys", deployKeyService.listByProject(project.id ?: 0L))
        return "project/setting_deploykeys"
    }

    @PostMapping("/projects/{owner}/{projectName}/deploy-keys")
    fun newDeployKey(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @RequestParam("title") title: String,
        @RequestParam("publicKey") publicKey: String,
        @RequestParam(value = "readOnly", defaultValue = "true") readOnly: Boolean,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
        if (!checkPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        try {
            val issued = deployKeyService.create(project, title, publicKey, readOnly)
            model.addAttribute("issuedHttpsToken", issued.rawHttpsToken)
        } catch (e: IllegalArgumentException) {
            model.addAttribute("deployKeyError", e.message)
        } catch (e: SshPublicKeyFingerprint.InvalidPublicKeyException) {
            model.addAttribute("deployKeyError", e.message)
        }

        model.addAttribute("project", project)
        model.addAttribute("deployKeys", deployKeyService.listByProject(project.id ?: 0L))
        return "project/setting_deploykeys"
    }

    @PostMapping("/projects/{owner}/{projectName}/deploy-keys/{id}/delete")
    fun deleteDeployKey(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @PathVariable("id") id: Long,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
        if (!checkPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        deployKeyService.delete(project, id)
        return "redirect:/projects/$owner/$projectName/deploy-keys"
    }
}
