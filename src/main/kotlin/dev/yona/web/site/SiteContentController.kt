package dev.yona.web.site

import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.site.SiteContentService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 사이트 관리의 프로젝트, 게시글, 이슈 목록과 프로젝트 삭제이다. v1.6 `SiteApp.projectList`, `postList`, `issueList`, `deleteProject`
 * (`app/controllers/SiteApp.java:142-156, 214-231`)에 해당한다. 사이트 관리자만 쓴다.
 *
 * 프로젝트 목록의 `pageNum`은 0부터이고 게시글, 이슈 목록은 1부터이다(v1.6의 어긋남을 그대로 둔다, R-53).
 */
@Controller
@AnonymousCheck
@SiteManagerOnly
class SiteContentController(
    private val service: SiteContentService,
    private val projects: ProjectRepository,
    private val deletion: ProjectDeletionService,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val projectViews: ProjectViewFactory,
    private val helper: TemplateHelper
) {

    /** 프로젝트 목록의 한 줄이다. */
    data class ProjectRow(val id: Long, val owner: String, val name: String, val logoUrl: String, val overview: String?, val createdText: String)

    /** 게시글과 이슈 목록의 한 줄이다. [authorName]은 작성자의 이름(없는 사용자는 `anonymous`)이다. */
    data class ContentRow(
        val projectOwner: String, val projectName: String, val logoUrl: String, val title: String, val url: String,
        val authorLoginId: String, val authorName: String, val authorAvatarUrl: String, val createdTitle: String, val createdText: String, val commentCount: Int
    )

    @GetMapping("/sites/projectList")
    @Transactional(readOnly = true)
    fun projectList(@RequestParam(name = "filter", defaultValue = "") filter: String, @RequestParam(defaultValue = "0") pageNum: Int): Any {
        if (pageNum < 0) return ErrorViews.badRequest()
        val page = service.projects(filter, pageNum)
        return ModelAndView("site/project-list")
            .addObject("title", "title.projectList")
            .addObject("rows", page.items.map { projectRow(it) })
            .addObject("filter", filter)
            .addObject("totalPageCount", page.totalPageCount)
    }

    @DeleteMapping("/sites/project/delete/{projectId}")
    @Transactional
    fun deleteProject(@PathVariable projectId: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projects.findById(projectId).orElse(null) ?: return ErrorViews.notFound()
        deletion.delete(project)
        return RedirectSupport.see(request, response, "/sites/projectList")
    }

    @GetMapping("/sites/postList")
    @Transactional(readOnly = true)
    fun postList(@RequestParam(defaultValue = "1") pageNum: Int): Any {
        if (pageNum < 1) return ErrorViews.badRequest()
        val page = service.postings(pageNum - 1)
        val rows = page.items.map { post ->
            contentRow(post.project, post.title, "/${post.project?.owner}/${post.project?.name}/post/${post.number}", post.authorLoginId, post.createdDate, post.numOfComments ?: 0)
        }
        return ModelAndView("site/post-list").addObject("title", "title.siteSetting").addObject("rows", rows).addObject("totalPageCount", page.totalPageCount)
    }

    @GetMapping("/sites/issueList")
    @Transactional(readOnly = true)
    fun issueList(@RequestParam(defaultValue = "1") pageNum: Int, @RequestParam(required = false) state: String?): Any {
        if (pageNum < 1) return ErrorViews.badRequest()
        val current = (if (state.isNullOrBlank()) State.OPEN else State.entries.firstOrNull { it.name == state.uppercase() }) ?: return ErrorViews.badRequest()
        val page = service.issues(current, pageNum - 1)
        val rows = page.items.map { issue ->
            contentRow(issue.project, issue.title, "/${issue.project?.owner}/${issue.project?.name}/issue/${issue.number}", issue.authorLoginId, issue.createdDate, issue.numOfComments ?: 0)
        }
        return ModelAndView("site/issue-list").addObject("title", "title.siteSetting").addObject("rows", rows)
            .addObject("currentState", current.name.lowercase()).addObject("totalPageCount", page.totalPageCount)
    }

    private fun projectRow(project: Project) = ProjectRow(
        id = requireNotNull(project.id), owner = project.owner.orEmpty(), name = project.name.orEmpty(), logoUrl = projectViews.of(project).logoUrl,
        overview = project.overview, createdText = project.createdDate?.let { SimpleDateFormat("yyyy-MM-dd").format(it) }.orEmpty()
    )

    private fun contentRow(project: Project?, title: String?, url: String, authorLoginId: String?, created: Date?, comments: Int): ContentRow {
        val author: User? = authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }
        return ContentRow(
            projectOwner = project?.owner.orEmpty(), projectName = project?.name.orEmpty(), logoUrl = project?.let { projectViews.of(it).logoUrl }.orEmpty(),
            title = title.orEmpty(), url = url,
            authorLoginId = author?.loginId ?: ANONYMOUS, authorName = author?.name ?: ANONYMOUS,
            authorAvatarUrl = author?.let { avatars.avatarUrl(it, AUTHOR_AVATAR_SIZE) }.orEmpty(),
            createdTitle = helper.dateString(created), createdText = helper.agoOrDateString(created), commentCount = comments
        )
    }

    private companion object {
        const val ANONYMOUS = "anonymous"
        const val AUTHOR_AVATAR_SIZE = 16
    }
}
