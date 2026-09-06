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
import org.springframework.web.servlet.mvc.support.RedirectAttributes
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

    // yona-wiki P3-03 Step2 — Post/Redirect/Get 패턴을 쓴다(발급 직후 화면에 바로 렌더링하지 않음).
    // 처음엔 ApiToken 발급 화면(edit_tokens.html)처럼 같은 POST 응답에서 바로 렌더링했으나, 이
    // 컨트롤러가 렌더링하는 project/setting_deploykeys.html은 project/menu 프래그먼트가
    // project.enrolledUsers(지연 로딩 컬렉션)를 참조한다 — deployKeyService.create()가
    // @Transactional 메서드 호출로 별도 트랜잭션을 열고 커밋하는 과정에서 OSIV(Open Session In
    // View)가 요청에 바인딩해둔 영속성 컨텍스트가 갱신되어, 컨트롤러가 미리 들고 있던 project
    // 참조의 enrolledUsers 프록시가 그 세션에서 분리돼(LazyInitializationException) 뷰 렌더링이
    // 500으로 실패하는 문제를 실제로 겪었다(DeployKeyEditFormTemplateRenderingSpec). 리다이렉트로
    // 새 GET 요청을 발생시키면 project를 그 요청에서 새로 조회하므로 이 문제가 없다 — 발급된
    // HTTPS 토큰은 RedirectAttributes 플래시 속성으로 다음 GET 한 번에만 노출한다.
    @PostMapping("/projects/{owner}/{projectName}/deploy-keys")
    fun newDeployKey(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @RequestParam("title") title: String,
        @RequestParam("publicKey") publicKey: String,
        @RequestParam(value = "readOnly", defaultValue = "true") readOnly: Boolean,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
        if (!checkPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        try {
            val issued = deployKeyService.create(project, title, publicKey, readOnly)
            redirectAttributes.addFlashAttribute("issuedHttpsToken", issued.rawHttpsToken)
        } catch (e: IllegalArgumentException) {
            redirectAttributes.addFlashAttribute("deployKeyError", e.message)
        } catch (e: SshPublicKeyFingerprint.InvalidPublicKeyException) {
            redirectAttributes.addFlashAttribute("deployKeyError", e.message)
        }

        return "redirect:/projects/$owner/$projectName/deploy-keys"
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
