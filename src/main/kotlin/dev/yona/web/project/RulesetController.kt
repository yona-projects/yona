package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.Project
import dev.yona.domain.project.Ruleset
import dev.yona.domain.project.RulesetInput
import dev.yona.domain.project.RulesetService
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ReviewerSettingMigration
import dev.yona.util.UrlPaths
import dev.yona.web.RedirectSupport
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
 * 프로젝트 설정의 규칙 묶음 목록과 편집 화면이다(추가 개발 항목 X-34, `decisions.md` D-46). GitHub의 "Rules > Rulesets"처럼 목록 화면에서 규칙 묶음을 고르고, 편집 화면에서
 * 이름, 적용 상태, 바이패스, 대상 브랜치, 규칙을 정해 저장한다. 설정을 고칠 수 있는 사용자(프로젝트 관리자)만 쓰며 Git 프로젝트에서만 동작한다.
 */
@Controller
@IsOnlyGitAvailable
@AnonymousCheck
class RulesetController(private val projectService: ProjectService, private val service: RulesetService, private val views: ProjectViewFactory) {

    /** 규칙 묶음 목록이다. */
    @GetMapping("/{user}/{project}/setting/rulesets")
    @IsAllowed(Operation.UPDATE)
    @Transactional(readOnly = true)
    fun list(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        val rulesets = project.rulesets.sortedBy { it.name }
        return ModelAndView("project/rulesets")
            .addObject("title", "project.ruleset")
            .addObject("projectView", views.of(project))
            .addObject("rulesets", rulesets)
            .addObject("migrated", rulesets.any { it.name?.startsWith(ReviewerSettingMigration.MIGRATED_NAME) == true })
    }

    /** 새 규칙 묶음 화면이다. */
    @GetMapping("/{user}/{project}/setting/rulesets/new")
    @IsAllowed(Operation.UPDATE)
    @Transactional(readOnly = true)
    fun newForm(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        return formView(project, Ruleset(enforcement = Ruleset.ACTIVE), emptyList())
    }

    /** 규칙 묶음을 만든다. 입력이 잘못되면 400으로 편집 화면을 다시 보인다. */
    @PostMapping("/{user}/{project}/setting/rulesets")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun create(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        val input = bind(request)
        val errors = service.violations(project, input)
        if (errors.isNotEmpty()) return formView(project, draft(null, input), errors).also { it.status = HttpStatus.BAD_REQUEST }
        service.create(project, input)
        return RedirectSupport.see(request, response, listUrl(project))
    }

    /** 규칙 묶음 편집 화면이다. 이 프로젝트의 규칙 묶음이 아니면 404이다. */
    @GetMapping("/{user}/{project}/setting/rulesets/{id}")
    @IsAllowed(Operation.UPDATE)
    @Transactional(readOnly = true)
    fun edit(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        val ruleset = service.find(project, id) ?: return ErrorViews.notFound("error.notfound")
        return formView(project, ruleset, emptyList())
    }

    /** 규칙 묶음을 고친다. 입력이 잘못되면 400으로 편집 화면을 다시 보이고, 이 프로젝트의 규칙 묶음이 아니면 404이다. */
    @PostMapping("/{user}/{project}/setting/rulesets/{id}")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun update(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        service.find(project, id) ?: return ErrorViews.notFound("error.notfound")
        val input = bind(request)
        val errors = service.violations(project, input, id)
        if (errors.isNotEmpty()) return formView(project, draft(id, input), errors).also { it.status = HttpStatus.BAD_REQUEST }
        service.update(project, id, input)
        return RedirectSupport.see(request, response, listUrl(project))
    }

    /** 규칙 묶음을 지운다. 이 프로젝트의 규칙 묶음이 아니면 404이다. */
    @PostMapping("/{user}/{project}/setting/rulesets/{id}/delete")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun delete(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        if (!service.delete(project, id)) return ErrorViews.notFound("error.notfound")
        return RedirectSupport.see(request, response, listUrl(project))
    }

    /** 폼이 보내는 항목을 묶는다. 체크하지 않은 조건은 값이 오지 않으므로 꺼진 것이다. */
    private fun bind(request: HttpServletRequest): RulesetInput {
        fun on(field: String) = request.getParameter(field)?.lowercase() in setOf("true", "on", "yes", "1")
        return RulesetInput(
            name = request.getParameter("name"), enforcement = request.getParameter("enforcement") ?: Ruleset.ACTIVE, pattern = request.getParameter("pattern"),
            approvals = request.getParameter("approvals"), bypassAdmins = on("bypassAdmins"), restrictUpdates = on("restrictUpdates"), restrictDeletions = on("restrictDeletions"), blockForcePushes = on("blockForcePushes"),
            requirePullRequest = on("requirePullRequest"), requireLinearHistory = on("requireLinearHistory"), requireNoChangesRequested = on("requireNoChangesRequested"),
            requireConversationResolution = on("requireConversationResolution")
        )
    }

    /** 잘못된 입력을 편집 화면에 다시 채우기 위한 저장하지 않는 규칙 묶음이다. */
    private fun draft(id: Long?, input: RulesetInput) = Ruleset(
        id = id, name = input.name, enforcement = input.enforcement, pattern = input.pattern, requiredApprovals = input.approvals?.trim()?.toIntOrNull() ?: 0,
        bypassAdmins = input.bypassAdmins, restrictUpdates = input.restrictUpdates, restrictDeletions = input.restrictDeletions, blockForcePushes = input.blockForcePushes, requirePullRequest = input.requirePullRequest,
        requireLinearHistory = input.requireLinearHistory, requireNoChangesRequested = input.requireNoChangesRequested, requireConversationResolution = input.requireConversationResolution
    )

    private fun formView(project: Project, ruleset: Ruleset, errors: List<String>): ModelAndView =
        ModelAndView("project/ruleset-form")
            .addObject("title", "project.ruleset")
            .addObject("projectView", views.of(project))
            .addObject("ruleset", ruleset)
            .addObject("errors", errors)

    private fun listUrl(project: Project) = "${UrlPaths.projectPath(project.owner, project.name)}/setting/rulesets"
}
