package dev.yona.web.organization

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.user.UserRepository
import dev.yona.domain.enumeration.RoleType
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.GuestProhibit
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewContext
import dev.yona.web.project.ProjectViewFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 조직 홈과 조직 목록이다. v1.6 `OrganizationApp.organization`, `orgList`
 * (`app/controllers/OrganizationApp.java:117-124, 466-482`)에 해당한다.
 */
@Controller
@AnonymousCheck
class OrganizationController(
    private val organizationService: OrganizationService,
    private val orgViews: OrganizationViewFactory,
    private val projectViews: ProjectViewFactory,
    private val users: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val accessControl: AccessControl,
    private val avatars: dev.yona.security.AvatarService,
    private val properties: YonaProperties
) {

    /** 홈의 프로젝트 한 줄이다. */
    data class ProjectRow(val view: ProjectViewContext, val members: List<Person>, val labels: List<dev.yona.domain.project.ProjectListItem.LabelView>)

    /** 프로젝트 구성원 한 사람이다. */
    data class Person(val loginId: String, val name: String, val avatarUrl: String)

    @GetMapping("/organizations/{organizationName}")
    fun home(@PathVariable organizationName: String): ModelAndView {
        val organization = organizationService.findByName(organizationName) ?: return ErrorViews.notFound("error.notfound.organization")
        val viewer = currentUserProvider.get().user
        val visible = organizationService.visibleProjects(organization, viewer)
        val labels = organizationService.labelsOf(visible)
        val rows = visible.map { project ->
            ProjectRow(
                projectViews.of(project),
                users.findMembersOfProject(requireNotNull(project.id), RoleType.SITEMANAGER.id).map { Person(it.loginId.orEmpty(), it.name.orEmpty(), avatars.avatarUrl(it)) },
                labels[project.id].orEmpty()
            )
        }
        return ModelAndView("organization/view")
            .addObject("title", organization.name)
            .addObject("orgView", orgViews.of(organization))
            .addObject("rows", rows)
    }

    @GetMapping("/orgs")
    @GuestProhibit
    fun list(
        @RequestParam(name = "filter", defaultValue = "") query: String,
        @RequestParam(name = "pageNum", defaultValue = "1") pageNum: Int
    ): ModelAndView {
        if (properties.hideProjectListing) return ErrorViews.forbidden("error.auth.unauthorized.waringMessage")
        if (pageNum < 1) return ErrorViews.notFound("error.notfound")

        val page = organizationService.search(query, pageNum)
        val viewer = currentUserProvider.get().user
        val rows = page.content.map { org ->
            OrgRow(orgViews.of(org), properties.displayPrivateRepositories || accessControl.isAllowed(viewer, org.asResource(), Operation.READ))
        }
        return ModelAndView("organization/list")
            .addObject("title", "title.projectList")
            .addObject("rows", rows)
            .addObject("totalElements", page.totalElements)
            .addObject("totalPages", page.totalPages)
            .addObject("filter", query)
    }

    /** 목록의 조직 한 줄이다. [readable]이 거짓이면 접근 불가 안내 줄로 보여 준다. */
    data class OrgRow(val view: OrganizationViewContext, val readable: Boolean)
}
