package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.sshkey.DeployKey
import dev.yona.domain.sshkey.DeployKeyAddition
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 설정의 배포 키 화면이다(추가 개발 항목 X-3, `decisions.md` D-66). 사용자 설정의 SSH 키와 GPG 키 화면과 같은 구성으로, 목록 화면과 추가 화면 두 개로 나누고
 * 목록의 행 동작은 삭제 하나뿐이다. 설정을 고칠 수 있는 사용자(프로젝트 관리자)만 쓰며 Git 프로젝트에서만 동작한다. 다른 저장소의 키 삭제는 404이다.
 */
@Controller
@IsOnlyGitAvailable
@AnonymousCheck
class DeployKeyController(
    private val projectService: ProjectService,
    private val deployKeys: DeployKeyService,
    private val views: ProjectViewFactory,
    private val helper: TemplateHelper,
    private val currentUserProvider: dev.yona.security.CurrentUserProvider,
    private val users: dev.yona.domain.user.UserRepository
) {

    /** 목록의 한 줄이다. */
    data class DeployKeyRow(val id: Long, val name: String, val keyType: String, val fingerprint: String, val readOnly: Boolean, val createdText: String)

    @GetMapping("/{user}/{project}/setting/deploykeys")
    @IsAllowed(Operation.UPDATE)
    @Transactional(readOnly = true)
    fun list(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        return base("project/deploykeys", project).addObject("keys", deployKeys.list(project).map(::rowOf))
    }

    @GetMapping("/{user}/{project}/setting/deploykeys/new")
    @IsAllowed(Operation.UPDATE)
    @Transactional(readOnly = true)
    fun newForm(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        return newView(project, null, "", "", false)
    }

    /** 배포 키를 추가하고 목록으로 돌아간다. 받을 수 없는 키는 입력을 되돌려 주며 추가 화면에 400으로 이유를 보인다. 쓰기 허용을 고르지 않으면 읽기 전용이다. */
    @PostMapping("/{user}/{project}/setting/deploykeys")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun create(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        val keyName = request.getParameter("name").orEmpty()
        val publicKey = request.getParameter("publicKey").orEmpty()
        val writable = request.getParameter("writable") == "true"
        val creator = currentUserProvider.get().user?.id?.let { users.findById(it).orElse(null) }
        return when (val result = deployKeys.add(project, keyName, publicKey, writable, createdBy = creator)) {
            is DeployKeyAddition.Added -> RedirectSupport.see(request, response, listUrl(project))
            is DeployKeyAddition.Rejected -> newView(project, result.code, keyName, publicKey, writable).also { it.status = HttpStatus.BAD_REQUEST }
        }
    }

    /** 키를 지우고 목록으로 돌아간다. 이 저장소의 키가 아니거나 없는 키는 404이다. */
    @PostMapping("/{user}/{project}/setting/deploykeys/{id}/delete")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun delete(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        if (!deployKeys.delete(project, id)) return ErrorViews.notFound("error.notfound")
        return RedirectSupport.see(request, response, listUrl(project))
    }

    private fun listUrl(project: Project) = "/${project.owner}/${project.name}/setting/deploykeys"

    private fun base(template: String, project: Project) = ModelAndView(template).addObject("title", "project.deployKey").addObject("projectView", views.of(project))

    private fun newView(project: Project, error: String?, name: String, publicKey: String, writable: Boolean): ModelAndView =
        base("project/deploykey-new", project).addObject("deployKeyError", error).addObject("deployKeyName", name).addObject("deployKeyPublicKey", publicKey).addObject("deployKeyWritable", writable)

    private fun rowOf(key: DeployKey) = DeployKeyRow(requireNotNull(key.id), key.name.orEmpty(), key.keyType.orEmpty(), key.fingerprint.orEmpty(), key.readOnly != false, helper.dateString(key.createdDate))
}
