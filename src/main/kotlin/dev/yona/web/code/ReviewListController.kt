package dev.yona.web.code

import dev.yona.web.QueryLinks
import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewExcelExporter
import dev.yona.domain.comment.ReviewLinks
import dev.yona.domain.comment.ReviewSearchCondition
import dev.yona.domain.comment.ReviewSearchService
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.ContentDispositions
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView
import java.text.SimpleDateFormat
import java.util.Date

/** 리뷰 목록의 한 줄이다. 첫 리뷰 댓글의 내용과 작성자, 스레드가 보이는 화면 주소를 담는다. */
data class ReviewRow(
    val id: Long, val href: String, val contents: String, val authorLoginId: String?, val authorName: String?, val authorDisplayName: String?,
    val authorAvatarUrl: String?, val createdTitle: String, val createdAgo: String, val commentCount: Int
)

/** 왼쪽 빠른 검색 세 개의 개수이다. */
data class ReviewQuickCounts(val everyone: Long, val participant: Long, val author: Long)

/**
 * 프로젝트의 리뷰 목록이다. v1.6 `ReviewThreadApp.reviewThreads`(`app/controllers/ReviewThreadApp.java:36-60`)에 해당한다.
 *
 * `format=xls`이면 조건에 맞는 모든 스레드를 엑셀로 내려준다. 로그인이 필요하고 프로젝트를 읽을 수 있어야 한다.
 * 동작은 [web/review-list.md]에 있다.
 */
@Controller
@AnonymousCheck
class ReviewListController(
    private val projectService: ProjectService,
    private val search: ReviewSearchService,
    private val exporter: ReviewExcelExporter,
    private val links: ReviewLinks,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val views: ProjectViewFactory,
    private val helper: TemplateHelper,
    private val currentUserProvider: CurrentUserProvider
) {

    @GetMapping("/{user}/{project}/reviews")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun reviews(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = requireNotNull(currentUserProvider.get().user) { "로그인이 필요합니다." }
        val condition = bind(request) ?: return ErrorViews.badRequest()
        if (condition.threadState == null || !condition.validDirection) return ErrorViews.badRequest()

        if (request.getParameter("format") == EXCEL_EXT) return excel(project, condition)

        val page = search.search(project, condition)
        val count = { c: ReviewSearchCondition -> search.count(project, c) }
        val everyone = condition.copy(authorId = null, participantId = null)
        return ModelAndView("reviewthread/list")
            .addObject("title", "menu.review")
            .addObject("projectView", views.of(project))
            .addObject("cond", condition)
            .addObject("rows", page.items.map { row(it) })
            .addObject("totalPageCount", page.totalPageCount)
            .addObject("meId", viewer.id)
            .addObject("quick", ReviewQuickCounts(count(everyone), count(everyone.copy(participantId = viewer.id)), count(everyone.copy(authorId = viewer.id))))
            .addObject("openCount", count(condition.copy(state = ThreadState.OPEN.name)))
            .addObject("closedCount", count(condition.copy(state = ThreadState.CLOSED.name)))
            .addObject("excelUrl", excelUrl(request))
    }

    /** 요청에서 조건을 읽는다. 숫자가 아닌 값이 있으면 `null`이다. */
    private fun bind(request: HttpServletRequest): ReviewSearchCondition? {
        fun long(name: String): Result<Long?> = runCatching { request.getParameter(name)?.takeIf { it.isNotEmpty() }?.toLong() }
        val author = long("authorId"); val participant = long("participantId")
        val pageNum = runCatching { request.getParameter("pageNum")?.takeIf { it.isNotEmpty() }?.toInt() ?: 1 }
        if (listOf(author, participant, pageNum).any { it.isFailure }) return null
        val defaults = ReviewSearchCondition()
        return ReviewSearchCondition(
            state = request.getParameter("state") ?: defaults.state, authorId = author.getOrNull(), participantId = participant.getOrNull(),
            orderBy = request.getParameter("orderBy")?.takeIf { it.isNotBlank() } ?: defaults.orderBy, orderDir = request.getParameter("orderDir") ?: defaults.orderDir,
            filter = request.getParameter("filter") ?: defaults.filter, pageNum = pageNum.getOrThrow()
        )
    }

    /** 조건에 맞는 모든 스레드를 쪽 나누기 없이 엑셀로 내려준다. */
    private fun excel(project: Project, condition: ReviewSearchCondition): Any {
        val data = exporter.export(search.findAll(project, condition))
        val filename = "${project.name}_reviews_${SimpleDateFormat("yyyyMMddHHmm").format(Date())}.$EXCEL_EXT"
        val encoded = ContentDispositions.encodeFilename(filename)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, dev.yona.io.MediaTypes.detectName(encoded))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; $encoded")
            .body(data)
    }

    /** 현재 주소에서 `pageNum`을 빼고 `format=xls`를 붙인다. v1.6 `addQueryString(removeQueryString(requestHeader.uri, "pageNum"), "format=xls")` ([QueryLinks]). */
    private fun excelUrl(request: HttpServletRequest): String =
        QueryLinks.excelLink(request.requestURI + (request.queryString?.let { "?$it" } ?: ""), EXCEL_EXT)

    private fun row(thread: CommentThread): ReviewRow {
        val first: ReviewComment? = thread.reviewComments.minWithOrNull(compareBy({ it.createdDate }, { it.id }))
        val author: User? = first?.authorId?.let { users.findById(it).orElse(null) }
        val avatar = author?.let { avatars.avatarUrl(it).let { url -> if (url == AvatarService.DEFAULT_AVATAR_URL) avatars.gravatarUrl(it.email, AVATAR_SIZE) else url } }
        return ReviewRow(
            id = requireNotNull(thread.id), href = links.thread(thread), contents = first?.contents.orEmpty(), authorLoginId = author?.loginId, authorName = author?.name,
            authorDisplayName = author?.let { UserDisplay.of(it, currentUserProvider.get().user?.lang) }, authorAvatarUrl = avatar,
            createdTitle = helper.dateString(thread.createdDate), createdAgo = helper.agoOrDateString(thread.createdDate), commentCount = thread.reviewComments.size - 1
        )
    }

    private companion object {
        const val EXCEL_EXT = "xls"
        const val AVATAR_SIZE = 32
    }
}
