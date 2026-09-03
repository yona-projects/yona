package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.support.DiagnosticService
import com.github.yonaprojects.yona.domain.support.YonaUpdateService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.core.env.Environment

@Controller
@RequestMapping(value = ["/site", "/sites"])
class SiteViewController(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val diagnosticService: DiagnosticService,
    private val yonaUpdateService: YonaUpdateService,
    private val environment: Environment
) {

    private fun checkAdmin(authentication: Authentication?): User {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !loginUser.isSiteManager) {
            throw IllegalArgumentException("Unauthorized access")
        }
        return loginUser
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleUnauthorized(e: IllegalArgumentException): String {
        return "error/403"
    }

    // 1. 사용자 관리 화면 (GET /site/users, GET /site/userList)
    @GetMapping(value = ["/userList", "/users"])
    fun userList(
        @RequestParam(value = "page", defaultValue = "1") pageNum: Int,
        @RequestParam(value = "query", defaultValue = "") query: String,
        @RequestParam(value = "state", defaultValue = "ACTIVE") stateStr: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val userState = UserState.of(stateStr) ?: UserState.ACTIVE

        val pageable = PageRequest.of(pageNum - 1, 25, Sort.by("name").ascending())
        val users = userRepository.findUsersForAdmin(userState, "%$query%", pageable)

        // 사이트 관리자의 총 수
        val adminCount = userRepository.countUsersForAdmin(UserState.SITE_ADMIN, "%%")

        model.addAttribute("users", users)
        model.addAttribute("userState", userState)
        model.addAttribute("query", query)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("adminCount", adminCount)
        model.addAttribute("message", "title.siteSetting")

        return "site/userList"
    }

    // 2. 프로젝트 관리 화면 (GET /site/projects, GET /site/projectList)
    @GetMapping(value = ["/projectList", "/projects"])
    fun projectList(
        @RequestParam(value = "page", defaultValue = "1") pageNum: Int,
        @RequestParam(value = "projectName", defaultValue = "") projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val pageable = PageRequest.of(pageNum - 1, 25, Sort.by("name").ascending())
        val projects = projectRepository.findProjectsForAdmin("%$projectName%", pageable)

        model.addAttribute("projects", projects)
        model.addAttribute("projectName", projectName)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("message", "title.projectList")

        return "site/projectList"
    }

    // 3. 이슈 관리 화면 (GET /site/issues, GET /site/issueList)
    @GetMapping(value = ["/issueList", "/issues"])
    fun issueList(
        @RequestParam(value = "page", defaultValue = "1") pageNum: Int,
        @RequestParam(value = "state", defaultValue = "open") stateStr: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val currentState = State.getValue(stateStr.lowercase())

        val pageable = PageRequest.of(pageNum - 1, 30, Sort.by("createdDate").descending())
        val issues = if (currentState == State.ALL) {
            issueRepository.findAll(pageable)
        } else {
            issueRepository.findByState(currentState, pageable)
        }

        model.addAttribute("issues", issues)
        model.addAttribute("currentState", currentState)
        model.addAttribute("state", stateStr)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("userRepository", userRepository)
        model.addAttribute("message", "title.siteSetting")

        return "site/issueList"
    }

    // 4. 자유게시글 관리 화면 (GET /site/posts, GET /site/postList)
    @GetMapping(value = ["/postList", "/posts"])
    fun postList(
        @RequestParam(value = "page", defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val pageable = PageRequest.of(pageNum - 1, 30, Sort.by("createdDate").descending())
        val posts = postingRepository.findAll(pageable)

        model.addAttribute("posts", posts)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("userRepository", userRepository)
        model.addAttribute("message", "title.siteSetting")

        return "site/postList"
    }

    // 5. 메일 작성 화면 (GET /site/mails, GET /site/mail)
    @GetMapping(value = ["/mail", "/mails"])
    fun writeMail(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)

        val notConfiguredItems = mutableListOf<String>()
        if (environment.getProperty("spring.mail.host").isNullOrBlank()) {
            notConfiguredItems.add("smtp.host")
        }
        if (environment.getProperty("spring.mail.username").isNullOrBlank()) {
            notConfiguredItems.add("smtp.user")
        }
        if (environment.getProperty("spring.mail.password").isNullOrBlank()) {
            notConfiguredItems.add("smtp.password")
        }

        val sender = environment.getProperty("spring.mail.properties.mail.smtp.from")
            ?: environment.getProperty("spring.mail.username")
            ?: "yona@yona.io"

        model.addAttribute("message", "title.sendMail")
        model.addAttribute("notConfiguredItems", notConfiguredItems)
        model.addAttribute("sender", sender)
        model.addAttribute("currentUser", currentUser)
        return "site/mail"
    }

    // 6. 대량 메일 발송 화면 (GET /site/massmails, GET /site/massmail)
    @GetMapping(value = ["/massmail", "/massmails"])
    fun massMail(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("message", "title.massMail")
        return "site/massMail"
    }

    // 7. 데이터 관리 진입 화면 (GET /site/data)
    @GetMapping("/data")
    fun data(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("message", "title.siteSetting")
        return "site/data"
    }

    // 8. 자가진단 분석 화면 (GET /site/diagnostic, GET /site/diagnose)
    @GetMapping(value = ["/diagnostic", "/diagnose"])
    fun diagnose(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val errors = diagnosticService.checkAll()

        model.addAttribute("errors", errors)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("message", "title.siteSetting")
        return "site/diagnostic"
    }

    // 9. 업데이트 확인 화면 (GET /site/update)
    @GetMapping("/update")
    fun updatePage(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        var exception: Exception? = null
        try {
            yonaUpdateService.refreshVersionToUpdate()
        } catch (e: Exception) {
            exception = e
        }

        val versionToUpdate = if (yonaUpdateService.isUpdateRequired()) yonaUpdateService.getLatestVersion() else null

        model.addAttribute("currentUser", currentUser)
        model.addAttribute("currentVersion", "1.15.0")
        model.addAttribute("latestVersion", yonaUpdateService.getLatestVersion())
        model.addAttribute("versionToUpdate", versionToUpdate)
        model.addAttribute("isUpdateRequired", yonaUpdateService.isUpdateRequired())
        model.addAttribute("releaseUrl", yonaUpdateService.getReleaseUrl())
        model.addAttribute("exception", exception)
        model.addAttribute("message", "title.siteSetting")
        return "site/update"
    }
}
