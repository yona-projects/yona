package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectNameRules
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.ForkService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsCreatable
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

/** 포크 폼의 소유자 선택지 하나이다. */
data class ForkOwnerOption(val value: String, val url: String, val selected: Boolean)

/** 이미 포크한 프로젝트 한 줄이다. */
data class ExistingFork(val label: String, val href: String)

/**
 * 포크 폼, 포크 시작 화면, 복제 요청이다. v1.6 `PullRequestApp.newFork`, `fork`, `doClone`
 * (`app/controllers/PullRequestApp.java:72-153`)에 해당한다. 모두 로그인해야 하고 프로젝트에 포크를 만들 권한이 있어야 한다.
 *
 * 포크는 두 단계이다. `POST /fork`가 복제 안내 화면을 보여 주면 그 화면의 스크립트가 3초 뒤 `POST /clone`을 Ajax로 불러
 * 실제로 복제하고 결과 JSON(`{"status": "success"|"failed", "url": ...}`)의 주소로 이동한다.
 *
 * 이름이 규칙에 맞지 않거나 공개 범위가 올바르지 않으면 v1.6은 입력 묶기 오류를 `get()`으로 읽다 예외가 나 500이었다. 그대로 따른다.
 */
@Controller
@AnonymousCheck
class ForkController(
    private val projectService: ProjectService,
    private val forks: ForkService,
    private val views: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider,
    private val siteUrls: SiteUrls
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{user}/{project}/newFork")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun newFork(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @RequestParam(required = false) forkOwner: String?
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val user = requireNotNull(currentUserProvider.get().user)
        val destination = forks.destination(forkOwner, user)
        val base = siteUrls.path("/${project.owner}/${project.name}")
        val organizations = adminOrganizations(user)
        return ModelAndView("pullrequest/fork")
            .addObject("title", "fork")
            .addObject("projectView", views.of(project))
            .addObject("original", "${project.owner} / ${project.name}")
            .addObject("forkAction", "$base/fork")
            .addObject("cancelUrl", "$base/pullRequests")
            .addObject("forkOwner", destination)
            .addObject("forkName", forks.suggestName(project, destination))
            .addObject("owners", ownerOptions(project, user.loginId.orEmpty(), organizations, destination))
            .addObject("existing", forks.existingForks(project, destination).map { ExistingFork("${it.owner} / ${it.name}", siteUrls.path("/${it.owner}/${it.name}")) })
            .addObject("protectedAllowed", forks.isOrganization(destination))
    }

    @PostMapping("/{user}/{project}/fork")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun fork(
        @PathVariable("user") owner: String, @PathVariable("project") name: String,
        @RequestParam(name = "name", required = false) forkName: String?,
        @RequestParam(required = false) projectScope: String?, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val user = requireNotNull(currentUserProvider.get().user)
        val scope = bind(forkName, projectScope)
        val destination = forks.destination(request.getParameter("owner"), user)
        val base = siteUrls.path("/${project.owner}/${project.name}")

        if (forks.nameTaken(destination, forkName.orEmpty())) {
            return RedirectSupport.see(request, response, "$base/newFork?forkOwner=$destination", mapOf(RedirectSupport.WARNING to "project.name.duplicate"))
        }

        return ModelAndView("pullrequest/clone")
            .addObject("title", "fork")
            .addObject("projectView", views.of(project))
            .addObject("originalOwner", project.owner)
            .addObject("originalName", project.name)
            .addObject("forkOwner", destination)
            .addObject("forkName", forkName)
            .addObject("forkScope", scope.name)
            .addObject("cloneUrl", "$base/clone")
    }

    /**
     * 포크를 실제로 만든다. 성공하면 새 프로젝트 주소를, 실패하면 원본의 풀 리퀘스트 목록 주소를 돌려준다.
     * 같은 이름의 프로젝트가 이미 있으면 저장소를 건드리기 전에 실패로 답한다([decisions.md] A-73).
     */
    @PostMapping("/{user}/{project}/clone")
    @IsCreatable(ResourceType.FORK)
    @ResponseBody
    @IsOnlyGitAvailable
    fun doClone(
        @PathVariable("user") owner: String, @PathVariable("project") name: String,
        @RequestParam(name = "name", required = false) forkName: String?, @RequestParam(required = false) projectScope: String?, request: HttpServletRequest
    ): Map<String, String> {
        val original = projectService.findByOwnerAndProjectName(owner, name)
        val user = requireNotNull(currentUserProvider.get().user)
        val scope = bind(forkName, projectScope)
        val destination = forks.destination(request.getParameter("owner"), user)
        val failed = linkedMapOf("status" to "failed", "url" to siteUrls.path("/${original?.owner}/${original?.name}/pullRequests"))
        if (original == null || forks.nameTaken(destination, forkName.orEmpty())) return failed
        return try {
            val fork = forks.fork(original, destination, requireNotNull(forkName), scope, user)
            linkedMapOf("status" to "success", "url" to siteUrls.path("/${fork.owner}/${fork.name}"))
        } catch (e: Exception) {
            log.error("Failed to fork \"{}/{}\"", original.owner, original.name, e)
            failed
        }
    }

    /** 폼 값이 프로젝트 규칙에 맞는지 보고 공개 범위를 돌려준다. 맞지 않으면 v1.6의 `form.get()` 예외처럼 500이 난다. */
    private fun bind(name: String?, projectScope: String?): ProjectScope {
        check(ProjectNameRules.violations(name).isEmpty()) { "Cannot get value if form contains errors" }
        return projectScope?.let { value -> ProjectScope.entries.firstOrNull { it.name == value } }
            ?: throw IllegalStateException("Cannot get value if form contains errors")
    }

    private fun adminOrganizations(user: dev.yona.domain.user.User) = forks.adminOrganizationNames(user)

    private fun ownerOptions(project: Project, loginId: String, organizations: List<String>, destination: String): List<ForkOwnerOption> {
        val base = siteUrls.path("/${project.owner}/${project.name}")
        return listOf(ForkOwnerOption(loginId, "$base/newFork?forkOwner=$loginId", false)) +
            organizations.map { ForkOwnerOption(it, "$base/newFork?forkOwner=$it", it == destination) }
    }
}
