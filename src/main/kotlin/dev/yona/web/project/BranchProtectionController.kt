package dev.yona.web.project

import dev.yona.web.access.IsAllowed
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.BranchProtectionService
import dev.yona.domain.project.ProjectService
import dev.yona.util.UrlPaths
import dev.yona.web.RedirectSupport
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * 프로젝트 설정의 브랜치 보호 규칙을 만들고 지운다(추가 개발 항목 X-32, `decisions.md` D-42). 설정을 고칠 수 있는 사람(프로젝트 관리자)만 쓴다.
 */
@Controller
class BranchProtectionController(private val projectService: ProjectService, private val service: BranchProtectionService) {

    /** 규칙을 만든다. 같은 패턴이면 고친다. 입력이 잘못되면 400이다. */
    @PostMapping("/{user}/{project}/setting/protections")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun save(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        val pattern = request.getParameter("pattern")
        val approvals = request.getParameter("requiredApprovals")
        if (!service.isValid(pattern, approvals)) return ErrorViews.badRequest()
        val enforceAdmins = request.getParameter("enforceAdmins")?.lowercase() in setOf("true", "on", "yes", "1")
        service.save(project, requireNotNull(pattern), requireNotNull(approvals.trim().toIntOrNull()), enforceAdmins)
        return RedirectSupport.see(request, response, "${UrlPaths.projectPath(project.owner, project.name)}/settingform")
    }

    /** 규칙을 지운다. 이 프로젝트의 규칙이 아니면 404이다. */
    @PostMapping("/{user}/{project}/setting/protections/{id}/delete")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun delete(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        if (!service.delete(project, id)) return ErrorViews.notFound("error.notfound")
        return RedirectSupport.see(request, response, "${UrlPaths.projectPath(project.owner, project.name)}/settingform")
    }
}
