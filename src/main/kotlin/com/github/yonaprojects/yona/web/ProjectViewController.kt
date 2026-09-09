package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.vcs.nextVcsInCycle
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelService
import com.github.yonaprojects.yona.domain.issue.DuplicateLabelCategoryNameException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Page
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.springframework.core.io.Resource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.context.MessageSource
import org.springframework.beans.factory.annotation.Value
import java.net.URLDecoder
import java.util.Locale
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.server.ResponseStatusException
import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.ProjectTransfer
import com.github.yonaprojects.yona.domain.project.ProjectTransferRepository
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.watch.WatchService
import java.time.Instant
import com.github.yonaprojects.yona.domain.project.RecentProjectRepository
import com.github.yonaprojects.yona.domain.user.User

@Controller
class ProjectViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val projectService: ProjectService,
    private val organizationUserRepository: OrganizationUserRepository,
    private val attachmentRepository: AttachmentRepository,
    private val attachmentService: AttachmentService,
    private val organizationRepository: OrganizationRepository,
    private val messageSource: MessageSource,
    private val mailService: MailService,
    private val markdownService: MarkdownService,
    private val roleRepository: RoleRepository,
    private val projectTransferRepository: ProjectTransferRepository,
    private val issueLabelService: IssueLabelService,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val milestoneRepository: MilestoneRepository,
    private val watchService: WatchService,
    private val recentProjectRepository: RecentProjectRepository,
    private val accessControl: AccessControl,
    // legacy Application.java의 HIDE_PROJECT_LISTING 설정과 대응.
    @Value("\${yona.application.hide-project-listing:false}")
    private val hideProjectListing: Boolean = false
) {


    @GetMapping("/{owner:^(?!stylesheets|javascripts|images|bootstrap|assets|webjars)[a-zA-Z0-9_.-]+}/{projectName}")
    fun projectHome(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(value = "tabId", defaultValue = "readme") tabId: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        if (loginUser != null) {
            addVisitHistory(loginUser, project)
        }

        val projectUsers = projectUserRepository.findByProjectId(project.id!!)

        val histories = if (tabId != "readme" && tabId != "dashboard") {
            getProjectHistory(owner, project)
        } else {
            emptyList()
        }

        if (tabId == "dashboard") {
            getProjectDashboardData(project, model, projectUsers)
        }

        val readmeFileName = getReadmeFileName(project)
        // 코드브라우저 메뉴가 꺼진(project.isCodeEnabled == false) 프로젝트는 게시판에서 작성한
        // README 글(readme=true인 Posting)의 본문을 우선 사용하고, 그 외에는 기존처럼 git 저장소
        // 파일을 renderFileInReadme()로 렌더링한다.
        val readmeHtml = if (tabId == "readme" && readmeFileName != null) {
            val readmePosting = if (!project.isCodeEnabled) {
                postingRepository.findByProjectAndReadme(project, true).firstOrNull()
            } else {
                null
            }
            if (readmePosting != null) {
                markdownService.render(readmePosting.body ?: "", true, project)
            } else {
                val content = getReadmeContent(project, readmeFileName)
                if (content != null) markdownService.renderFileInReadme(content, project) else null
            }
        } else {
            null
        }

        val isWatching = loginUser?.let {
            watchService.isWatching(it, ResourceType.PROJECT, project.id.toString())
        } ?: false
        val watcherCount = watchService.findWatchers(ResourceType.PROJECT, project.id.toString()).size

        // 사이드바에 가장 기한이 임박한 열린 마일스톤의 진행 상황 카드(milestone/partial_status)를 보여준다.
        val sidebarMilestone = if (project.isMilestoneEnabled) {
            milestoneRepository.findByProjectAndState(project, State.OPEN, Sort.by(Sort.Direction.ASC, "dueDate")).firstOrNull()
        } else {
            null
        }

        model.addAttribute("project", project)
        model.addAttribute("projectUsers", projectUsers)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("tabId", tabId)
        model.addAttribute("histories", histories)
        model.addAttribute("readmeFileName", readmeFileName)
        model.addAttribute("readmeHtml", readmeHtml)
        model.addAttribute("isWatching", isWatching)
        model.addAttribute("watcherCount", watcherCount)
        model.addAttribute("sidebarMilestone", sidebarMilestone)

        return "project/home"
    }

    // GitServletConfig와 공용으로 쓰도록 RecentProjectRepository.recordVisit()으로 위임한다.
    private fun addVisitHistory(user: User, project: Project) {
        recentProjectRepository.recordVisit(user, project)
    }

    private fun getProjectHistory(ownerId: String, project: Project): List<HistoryDto> {
        val histories = mutableListOf<HistoryDto>()

        // 1. Commits
        if (project.isCodeEnabled) {
            try {
                val repository = repositoryService.getRepository(project)
                val commits = repository.getHistory(0, 10, null, null)
                for (commit in commits) {
                    val authorEmail = commit.getAuthorEmail()
                    val user = if (authorEmail != null) {
                        userRepository.findByEmail(authorEmail).orElse(null)
                    } else {
                        null
                    }
                    
                    val history = HistoryDto().apply {
                        this.who = user?.name ?: (commit.getAuthorName() ?: "Unknown")
                        this.userPageUrl = user?.let { "/user/${it.loginId}" } ?: "#"
                        this.userAvatarUrl = user?.avatarUrl ?: "/images/default-avatar-34.png"
                        this.whenInstant = commit.getCommitterDate()?.toInstant() ?: Instant.now()
                        this.where = project.name
                        this.what = "commit"
                        this.shortTitle = commit.getShortId()
                        this.how = commit.getShortMessage()
                        this.url = "/$ownerId/${project.name}/commit/${commit.getId()}"
                    }
                    histories.add(history)
                }
            } catch (e: Exception) {
                // NOOP
            }
        }

        // 2. Issues
        if (project.isIssueEnabled) {
            val pageable = PageRequest.of(0, 10, Sort.by("createdDate").descending())
            val issues = issueRepository.findByProject(project, pageable).content
            for (issue in issues) {
                val authorLoginId = issue.authorLoginId
                val author = if (authorLoginId != null) {
                    userRepository.findByLoginId(authorLoginId).orElse(null)
                } else {
                    null
                }
                val history = HistoryDto().apply {
                    this.who = issue.authorName ?: "Unknown"
                    this.userPageUrl = author?.let { "/user/${it.loginId}" } ?: "#"
                    this.userAvatarUrl = author?.avatarUrl ?: "/images/default-avatar-34.png"
                    this.whenInstant = issue.createdDate ?: Instant.now()
                    this.where = project.name
                    this.what = "issue"
                    this.shortTitle = "#${issue.number}"
                    this.how = issue.title
                    this.url = "/$ownerId/${project.name}/issue/${issue.number}"
                }
                histories.add(history)
            }
        }

        // 3. Postings
        if (project.isBoardEnabled) {
            val pageable = PageRequest.of(0, 10, Sort.by("createdDate").descending())
            val postings = postingRepository.findByProject(project, pageable).content
            for (posting in postings) {
                val authorLoginId = posting.authorLoginId
                val author = if (authorLoginId != null) {
                    userRepository.findByLoginId(authorLoginId).orElse(null)
                } else {
                    null
                }
                val history = HistoryDto().apply {
                    this.who = posting.authorName ?: "Unknown"
                    this.userPageUrl = author?.let { "/user/${it.loginId}" } ?: "#"
                    this.userAvatarUrl = author?.avatarUrl ?: "/images/default-avatar-34.png"
                    this.whenInstant = posting.createdDate ?: Instant.now()
                    this.where = project.name
                    this.what = "post"
                    this.shortTitle = "#${posting.number}"
                    this.how = posting.title
                    this.url = "/$ownerId/${project.name}/post/${posting.number}"
                }
                histories.add(history)
            }
        }

        // 4. PullRequests
        if (project.isPullRequestEnabled) {
            val pageable = PageRequest.of(0, 10, Sort.by("created").descending())
            val pullRequests = pullRequestRepository.findByToProject(project, pageable).content
            for (pull in pullRequests) {
                val contributor = pull.contributor
                val history = HistoryDto().apply {
                    this.who = contributor?.name ?: "Unknown"
                    this.userPageUrl = contributor?.let { "/user/${it.loginId}" } ?: "#"
                    this.userAvatarUrl = contributor?.avatarUrl ?: "/images/default-avatar-34.png"
                    this.whenInstant = pull.created ?: Instant.now()
                    this.where = project.name
                    this.what = "pullrequest"
                    this.shortTitle = "#${pull.number}"
                    this.how = pull.title ?: ""
                    this.url = "/$ownerId/${project.name}/pullRequest/${pull.number}"
                }
                histories.add(history)
            }
        }

        histories.sortByDescending { it.whenInstant }
        return histories
    }

    @GetMapping("/{owner}/{projectName}/members")
    fun projectMembers(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val projectUsers = projectUserRepository.findByProjectId(project.id!!)

        model.addAttribute("project", project)
        model.addAttribute("projectUsers", projectUsers)
        model.addAttribute("currentUser", loginUser)

        return "project/members"
    }

    @GetMapping("/{owner}/{projectName}/setting")
    fun projectSetting(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // 설정 권한 검사 (MANAGER인지 여부)
        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)


        if (!isManager) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val repository = repositoryService.getRepository(project)
        val branches = try {
            repository.getRefNames().map { it.substringAfter("refs/heads/") }
        } catch (e: Exception) {
            emptyList()
        }
        val defaultBranch = try {
            repository.getDefaultBranch().substringAfter("refs/heads/")
        } catch (e: Exception) {
            "master"
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("branches", branches)
        model.addAttribute("defaultBranch", defaultBranch)

        return "project/setting"
    }

    @GetMapping("/{owner}/{projectName}/changeVCS")
    fun projectChangeVCSForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val nextVcs = nextVcsInCycle(project.vcs)

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("nextVcs", nextVcs)

        return "project/change_vcs"
    }

    @PostMapping("/{owner}/{projectName}/changeVCS")
    @ResponseBody
    fun changeVCS(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        projectService.changeVCS(project.id!!)

        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{owner}/{projectName}/code/{branch}/download")
    fun downloadCode(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        @RequestParam(value = "path", required = false, defaultValue = "") path: String,
        authentication: Authentication?,
        response: HttpServletResponse
    ) {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }

        val repository = repositoryService.getRepository(project)
        val decodedBranch = URLDecoder.decode(branch, "UTF-8")
        val decodedPath = URLDecoder.decode(path, "UTF-8")

        // 응답 헤더를 쓰고 스트리밍을 시작하기 전에 브랜치/경로가 실제로 존재하는지 먼저 확인해,
        // 존재하지 않는 브랜치를 요청했을 때 스트리밍 도중 예외가 나는 대신 깔끔한 404를 반환한다.
        // 이 조회 결과의 path는 getArchive()에 전달하지 않고 항상 브랜치 전체를 아카이브한다(UI의
        // "Download ZIP" 버튼도 path를 절대 넘기지 않는다).
        repositoryService.getMetaDataFromAncestorDirectories(repository, decodedBranch, decodedPath)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found")

        response.contentType = "application/zip"
        response.setHeader("Content-Disposition", "attachment; filename=\"$projectName-$branch.zip\"")

        repository.getArchive(response.outputStream, decodedBranch)
    }

    @GetMapping("/projectform")
    fun newProjectForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        // 사용자가 관리자로 속한 조직 목록 조회
        val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
        val organizations = orgUserList.map { it.organization }

        val form = NewProjectForm().apply { this.owner = loginUser.loginId }
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("organizations", organizations)
        model.addAttribute("form", form)
        model.addAttribute("isOwnerOrganization", organizations.any { it.name == form.owner })

        return "project/create"
    }

    @PostMapping(value = ["/projectform", "/projects"])
    fun newProject(
        @RequestParam("owner") owner: String,
        @RequestParam("name") name: String,
        @RequestParam("overview") overview: String,
        @RequestParam("projectScope") projectScope: String,
        @RequestParam("vcs") vcs: String,
        @RequestParam(value = "code", defaultValue = "false") code: Boolean,
        @RequestParam(value = "issue", defaultValue = "false") issue: Boolean,
        @RequestParam(value = "pullRequest", defaultValue = "false") pullRequest: Boolean,
        @RequestParam(value = "review", defaultValue = "false") review: Boolean,
        @RequestParam(value = "milestone", defaultValue = "false") milestone: Boolean,
        @RequestParam(value = "board", defaultValue = "false") board: Boolean,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        // 로그인 여부와 별개로, owner가 기존 조직명과 같으면 그 조직의 admin만 그 조직 아래
        // 프로젝트를 생성할 수 있다.
        val trimmedOwner = owner.trim()
        val organization = organizationRepository.findByName(trimmedOwner).orElse(null)
        if (organization != null && !accessControl.isOrganizationAdmin(organization, loginUser)) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN, "'${loginUser.name}' has no permission"
            )
        }

        try {
            val project = Project().apply {
                this.owner = trimmedOwner
                this.name = name.trim()
                this.overview = overview.trim()
                this.projectScope = ProjectScope.valueOf(projectScope.uppercase())
                this.vcs = vcs.uppercase()
                this.isCodeEnabled = code
                this.isIssueEnabled = issue
                this.isPullRequestEnabled = pullRequest
                this.isReviewEnabled = review
                this.isMilestoneEnabled = milestone
                this.isBoardEnabled = board
                // owner가 조직명이면 project.organization도 채워야 한다 — 그렇지 않으면 조직 소속
                // 프로젝트인데도 조직 관리자 권한/조직 프로젝트 목록에서 누락된다.
                if (organization != null) {
                    this.organization = organization
                }
            }

            val saved = projectService.createProject(project, loginUser)
            watchService.watch(loginUser, ResourceType.PROJECT, saved.id.toString())
            addVisitHistory(loginUser, saved)
            return "redirect:/${saved.owner!!.encodePathSegment()}/${saved.name.encodePathSegment()}"
        } catch (e: Exception) {
            val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
            val organizations = orgUserList.map { it.organization }

            val redisplayForm = NewProjectForm().apply {
                this.owner = trimmedOwner
                this.name = name
                this.overview = overview
                this.projectScope = try {
                    ProjectScope.valueOf(projectScope.uppercase())
                } catch (ex: IllegalArgumentException) {
                    ProjectScope.PUBLIC
                }
                this.vcs = vcs
                this.code = code
                this.issue = issue
                this.pullRequest = pullRequest
                this.review = review
                this.milestone = milestone
                this.board = board
            }
            model.addAttribute("currentUser", loginUser)
            model.addAttribute("organizations", organizations)
            model.addAttribute("error", e.message ?: "프로젝트 생성 도중 오류가 발생했습니다.")
            model.addAttribute("form", redisplayForm)
            model.addAttribute("isOwnerOrganization", organizations.any { it.name == redisplayForm.owner })
            return "project/create"
        }
    }

    // 3. 프로젝트 전체 목록 화면 (GET /projects)
    @GetMapping("/projects", produces = [MediaType.TEXT_HTML_VALUE])
    fun projects(
        @RequestParam(value = "filter", defaultValue = "") filter: String,
        @RequestParam(value = "pageNum", defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        // HIDE_PROJECT_LISTING이 켜져 있으면 사이트매니저를 포함해 누구도 전체 프로젝트 목록을 볼 수 없다.
        if (hideProjectListing) {
            return "error/403"
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        // 사용자가 조회할 수 있는 프로젝트 ID 목록 추출
        val projectIds = if (loginUser != null) {
            projectRepository.findAllowedProjectIdsForUser(loginUser.id!!)
        } else {
            projectRepository.findPublicProjectIds()
        }

        if (projectIds.isEmpty()) {
            model.addAttribute("projects", Page.empty<Project>())
            model.addAttribute("filter", filter)
            model.addAttribute("currentUser", loginUser)
            return "project/list"
        }

        val pageable = PageRequest.of(pageNum - 1, 25, Sort.by("createdDate").descending())
        val keyword = "%$filter%"
        val projectPage = projectRepository.searchProjects(projectIds, keyword, pageable)

        model.addAttribute("projects", projectPage)
        model.addAttribute("filter", filter)
        model.addAttribute("currentUser", loginUser)

        return "project/list"
    }

    // 3-1. 프로젝트 전체 목록 JSON API (GET /projects) - 레거시 호환 및 Typeahead 지원
    @GetMapping("/projects", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun projectsJson(
        @RequestParam(value = "query", defaultValue = "") query: String,
        @RequestParam(value = "filter", defaultValue = "") filter: String,
        authentication: Authentication?
    ): ResponseEntity<List<String>> {
        // HIDE_PROJECT_LISTING이 켜져 있으면 사이트매니저를 포함해 누구도 전체 프로젝트 목록을 볼 수 없다.
        if (hideProjectListing) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val searchKeyword = if (query.isNotBlank()) query else filter
        val pageable = PageRequest.of(0, 1000)

        val projectPage = if (user.isSiteManager) {
            projectRepository.findProjectsForAdmin(searchKeyword, pageable)
        } else {
            val allowedIds = projectRepository.findAllowedProjectIdsForUser(user.id!!)
            if (allowedIds.isEmpty()) {
                val publicIds = projectRepository.findPublicProjectIds()
                if (publicIds.isEmpty()) {
                    Page.empty()
                } else {
                    projectRepository.searchProjects(publicIds, searchKeyword, pageable)
                }
            } else {
                projectRepository.searchProjects(allowedIds, searchKeyword, pageable)
            }
        }

        val projectNames = projectPage.content.map { "${it.owner}/${it.name}" }
        val total = projectPage.totalElements

        val headers = HttpHeaders()
        headers.add("Content-Range", "items ${projectNames.size}/$total")

        return ResponseEntity.ok().headers(headers).body(projectNames)
    }

    // 4. 프로젝트 로고 이미지 조회 (GET /projects/{projectId}/logo)
    @GetMapping("/projects/{projectId}/logo")
    fun projectLogo(
        @PathVariable projectId: Long
    ): ResponseEntity<Resource> {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(
            ResourceType.PROJECT,
            projectId.toString()
        )
        val attachment = attachments.firstOrNull()

        if (attachment == null) {
            // 디폴트 프로젝트 이미지 반환 (하드코딩된 개발자 로컬 절대경로였던 실버그 수정 —
            // 커버리지 감사 중 발견, static 리소스는 classpath에서 로드해야 배포 환경에서도 동작한다)
            val defaultImage = ClassPathResource("static/images/project_default_logo.png")
            return if (defaultImage.exists()) {
                ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(defaultImage)
            } else {
                ResponseEntity.notFound().build()
            }
        }

        val file = attachmentService.getFile(attachment)
        if (!file.exists()) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(attachment.mimeType ?: "image/png"))
            .body(FileSystemResource(file))
    }

    // 5. 프로젝트 이관 설정 화면 (GET /{owner}/{projectName}/transfer)
    @GetMapping("/{owner}/{projectName}/transfer")
    fun transferForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        return "project/transfer"
    }

    // 6. 프로젝트 이관 실행 API (PUT /{owner}/{projectName}/transfer)
    @PutMapping("/{owner}/{projectName}/transfer")
    @ResponseBody
    fun transferProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam("owner") destination: String,
        request: HttpServletRequest,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // 대상 목적지(destination)가 유효한지 검증 (사용자 또는 조직)
        val destUser = userRepository.findByLoginId(destination).orElse(null)
        val destOrg = organizationRepository.findByName(destination).orElse(null)
        if (destUser == null && destOrg == null) {
            return ResponseEntity.badRequest().build()
        }

        // 자기 자신에게 이관 요청하는 것 차단
        if ((destUser != null && destUser.loginId == project.owner) || (destOrg != null && destOrg.name == project.owner)) {
            return ResponseEntity.badRequest().build()
        }

        val pt = projectService.requestNewTransfer(project.id!!, loginUser.id!!, destination)
        
        // 이관 요청 메일 발송
        sendTransferRequestMail(pt, request)

        val projectUrl = "/${project.owner}/${project.name}"
        return ResponseEntity.noContent()
            .header("Location", projectUrl)
            .build()
    }

    private fun sendTransferRequestMail(pt: ProjectTransfer, request: HttpServletRequest) {
        try {
            val serverUrl = getServerUrl(request)
            val acceptUrl = "$serverUrl/project/transfer/${pt.id}/${pt.confirmKey}"
            
            // 다국어 메시지 조립
            val locale = Locale.getDefault()
            val hello = messageSource.getMessage("transfer.message.hello", arrayOf<Any>(pt.destination), locale)
            val detail = messageSource.getMessage("transfer.message.detail", arrayOf<Any>(pt.project.name, pt.newProjectName, pt.project.owner ?: "", pt.destination), locale)
            val link = messageSource.getMessage("transfer.message.link", null, locale)
            val deadline = messageSource.getMessage("transfer.message.deadline", null, locale)
            val thank = messageSource.getMessage("transfer.message.thank", null, locale)

            val markdownMessage = """
                $hello
                
                $detail
                $link
                
                $acceptUrl
                
                $deadline
                
                $thank
            """.trimIndent()

            val htmlContent = markdownService.render(markdownMessage, true, pt.project)
            val subject = "[${pt.project.name}] @${pt.sender.loginId} wants to transfer project"

            // 메일 전송 대상
            val bccEmails = mutableListOf<String>()
            
            val toUser = userRepository.findByLoginId(pt.destination).orElse(null)
            if (toUser != null && !toUser.email.isNullOrBlank()) {
                bccEmails.add(toUser.email)
            }

            val toOrg = organizationRepository.findByName(pt.destination).orElse(null)
            if (toOrg != null) {
                val orgUsers = organizationUserRepository.findByOrganizationId(toOrg.id!!)
                val admins = orgUsers.filter { it.role.id == RoleType.ORG_ADMIN.roleType }
                admins.forEach {
                    if (!it.user.email.isNullOrBlank()) {
                        bccEmails.add(it.user.email)
                    }
                }
            }

            // 개별적으로 HTML 이메일 발송
            bccEmails.distinct().forEach { email ->
                mailService.sendHtmlMail(email, "Yona", subject, htmlContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getServerUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort
        return if (serverPort == 80 || serverPort == 443) {
            "$scheme://$serverName"
        } else {
            "$scheme://$serverName:$serverPort"
        }
    }

    // 7. 프로젝트 이관 승인 처리 (GET /project/transfer/{transferId}/{confirmKey})
    @GetMapping("/project/transfer/{transferId}/{confirmKey}")
    fun acceptTransfer(
        @PathVariable transferId: Long,
        @PathVariable confirmKey: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val ptOpt = projectTransferRepository.findById(transferId)
        if (!ptOpt.isPresent) {
            model.addAttribute("errorMessage", "존재하지 않는 이관 요청입니다.")
            return "error/404"
        }
        val pt = ptOpt.get()

        return try {
            val destination = pt.destination
            val newProjectName = pt.newProjectName
            
            projectService.acceptTransfer(transferId, confirmKey, loginUser.id!!)
            
            "redirect:/${destination.encodePathSegment()}/${newProjectName.encodePathSegment()}"
        } catch (e: Exception) {
            model.addAttribute("errorMessage", "이관 승인에 실패했습니다: ${e.message}")
            return "error/500"
        }
    }

    // 8. 프로젝트 삭제 설정 화면 (GET /{owner}/{projectName}/deleteform)
    @GetMapping("/{owner}/{projectName}/deleteform")
    fun deleteForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        return "project/delete"
    }

    // 9. 프로젝트 삭제 실행 API (DELETE /{owner}/{projectName}/delete)
    @DeleteMapping("/{owner}/{projectName}/delete")
    @ResponseBody
    fun deleteProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        projectService.deleteProject(project.id!!)

        return ResponseEntity.noContent()
            .header("Location", "/")
            .build()
    }

    // 10. 이슈 라벨 설정 화면 (GET /{owner}/{projectName}/issue/labelsform)
    @GetMapping("/{owner}/{projectName}/issue/labelsform")
    fun labelsForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager && !loginUser.isSiteManager) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val labels = issueLabelService.getLabels(project.id!!)
        // yona partial_issuelabels_list.scala.html의 labels.groupBy(_.category) 대응.
        val labelsByCategory = labels.groupBy { it.category }

        model.addAttribute("project", project)
        model.addAttribute("labels", labels)
        model.addAttribute("labelsByCategory", labelsByCategory)
        model.addAttribute("currentUser", loginUser)
        return "project/issuelabels"
    }


    // LabelRestApiController.list()가 domain/project/Label(프로젝트 홈 화면의 토픽 태그)을
    // 응답해왔는데, create()/update()/delete()는 IssueLabel(카테고리 기반 이슈 라벨링, 실제
    // CLI/이슈 화면이 쓰는 진짜 라벨) 기준이라 `yona label create`로 만든 라벨이 `yona label list`엔
    // 절대 뜨지 않는 버그가 있었다. list도 동일한 IssueLabel 기준으로 통일한다. newLabel/
    // updateLabelForm/deleteLabelForm과 달리 대응하는 legacy HTML 세션 라우트가 없어 @GetMapping을
    // 붙이지 않고 LabelRestApiController 전용 위임 대상으로만 둔다.
    fun getIssueLabelsForRestApi(
        owner: String,
        projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val labels = issueLabelService.getLabels(project.id!!).map { label ->
            mapOf(
                "id" to label.id,
                "name" to label.name,
                "color" to label.color,
                "category" to label.category.name,
                "categoryId" to label.category.id
            )
        }
        return ResponseEntity.ok(labels)
    }

    // 신규 라벨 추가. categoryName으로 카테고리를 찾거나 새로 만든다. ISSUE_LABEL 생성 권한은
    // 프로젝트 멤버 전원에게 있다(매니저 전용 아님).
    @PostMapping("/{owner}/{projectName}/issue/labels")
    @ResponseBody
    fun newLabel(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam labelName: String,
        @RequestParam labelColor: String,
        @RequestParam categoryName: String,
        @RequestParam(defaultValue = "false") categoryIsExclusive: Boolean,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        if (!accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.ISSUE_LABEL)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val label = issueLabelService.newLabelByCategoryName(project.id!!, categoryName, categoryIsExclusive, labelName, labelColor)
            ?: return ResponseEntity.noContent().build()

        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "id" to label.id,
                "name" to label.name,
                "color" to label.color,
                "category" to label.category.name,
                "categoryId" to label.category.id
            )
        )
    }

    // 라벨 삭제. HTML Form이 DELETE 메소드를 못 써서 _method=delete 파라미터로 오버라이드하는 legacy
    // 관례를 따른다(AttachmentController의 기존 _method 처리 패턴과 동일).
    @PostMapping("/{owner}/{projectName}/issue/label/{id}/delete")
    @ResponseBody
    fun deleteLabelForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        @RequestParam("_method") method: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        if (!method.equals("delete", ignoreCase = true)) {
            return ResponseEntity.badRequest().body("_method must be 'delete'.")
        }

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        if (!accessControl.isAllowed(loginUser, project, ResourceType.ISSUE_LABEL, Operation.DELETE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        // accessControl.isAllowed()는 URL 경로의 project(owner/projectName)에 대한 권한만 확인하고,
        // 실제 삭제 대상인 id(라벨 PK)가 그 project 소속인지는 검증하지 않는다. 이 때문에 자기
        // 프로젝트에 대한 라벨 삭제 권한만 있으면 URL의 project는 자기 것으로 두고 id만 다른
        // 프로젝트의 라벨 번호로 바꿔 호출하는 것으로 남의 프로젝트 라벨을 삭제할 수 있었다(IDOR).
        // id가 project 소속 라벨 목록에 있는지 먼저 확인한다.
        if (issueLabelService.getLabels(project.id!!).none { it.id == id }) {
            return ResponseEntity.notFound().build()
        }

        issueLabelService.deleteLabel(id)
        return ResponseEntity.ok().build()
    }

    // yona IssueLabelApp.update() 대응 (PUT /{owner}/{projectName}/issue/label/{id}) — 라벨 수정.
    @PutMapping("/{owner}/{projectName}/issue/label/{id}")
    @ResponseBody
    fun updateLabelForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        @RequestParam name: String,
        @RequestParam color: String,
        @RequestParam("category.id") categoryId: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        if (!accessControl.isAllowed(loginUser, project, ResourceType.ISSUE_LABEL, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        // deleteLabelForm()과 동일한 근본원인(위 주석 참고). id뿐 아니라 category.id로 넘어온 값도
        // 다른 프로젝트의 카테고리로 바꿔치기하면 라벨이 남의 프로젝트 카테고리로 재배정될 수 있어
        // 둘 다 project 소속인지 확인한다.
        if (issueLabelService.getLabels(project.id!!).none { it.id == id }) {
            return ResponseEntity.notFound().build()
        }
        if (issueLabelService.getCategories(project.id!!).none { it.id == categoryId }) {
            return ResponseEntity.badRequest().body("category.id가 이 프로젝트에 속하지 않습니다.")
        }

        issueLabelService.updateLabel(id, name, color, categoryId)
        return ResponseEntity.ok().build()
    }

    // yona IssueLabelApp.updateCategory() 대응 (PUT /{owner}/{projectName}/issue/label/category/{id}).
    @PutMapping("/{owner}/{projectName}/issue/label/category/{id}")
    @ResponseBody
    fun updateCategoryForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        @RequestParam name: String,
        @RequestParam(defaultValue = "false") isExclusive: Boolean,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        if (!accessControl.isAllowed(loginUser, project, ResourceType.ISSUE_LABEL_CATEGORY, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            issueLabelService.updateCategory(id, name, isExclusive)
            ResponseEntity.ok().build()
        } catch (e: DuplicateLabelCategoryNameException) {
            ResponseEntity.badRequest().build()
        }
    }

    // yona IssueLabelApp.copyLabels() 대응 (POST /{owner}/{projectName}/copyLabels) — 다른 프로젝트의
    // 라벨을 복사. 순수 HTML form 제출(AJAX 아님)이라 legacy와 동일하게 실패를 조용히 무시하고 항상
    // labelsForm으로 리다이렉트한다.
    @PostMapping("/{owner}/{projectName}/copyLabels")
    fun copyLabelsForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam("owner") fromOwner: String,
        @RequestParam("projectName") fromProjectName: String,
        authentication: Authentication?
    ): String {
        val toProject = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val fromProject = projectRepository.findByOwnerAndNameOrPreviousPlace(fromOwner, fromProjectName).orElse(null)
        if (fromProject != null &&
            accessControl.isAllowed(loginUser, fromProject, Operation.READ) &&
            accessControl.isProjectResourceCreatable(loginUser, toProject, ResourceType.ISSUE_LABEL)
        ) {
            issueLabelService.copyLabels(fromProject.id!!, toProject.id!!)
        }

        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/issue/labelsform"
    }

    // 11. 프로젝트 포크 화면 (GET /{ownerName}/{projectName}/newFork)
    // legacy conf/routes:297 forkOwner:String ?= null 대응 — 지정한 조직을 현재 사용자가 관리하면 그
    // 조직을, 아니면(파라미터가 없거나 관리 권한이 없으면) 현재 사용자 본인을 fork 대상으로 본다.
    @GetMapping("/{ownerName}/{projectName}/newFork")
    fun newFork(
        @PathVariable ownerName: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) forkOwner: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val originalProject = projectRepository.findByOwnerAndNameOrPreviousPlace(ownerName, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        // 사용자가 관리하는 조직 목록 조회
        val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
        val organizations = orgUserList.map { it.organization }

        // legacy PullRequestApp.findDestination(forkOwner) 대응.
        val destination = organizations.find { it.name == forkOwner }?.name ?: loginUser.loginId

        // legacy PullRequestApp.newFork()의 Project.findByOwnerAndOriginalProject(destination, project)
        // 대응(project/fork.html의 "이미 동일한 원본을 복사한 프로젝트가 있습니다" 경고에 사용) — 이전엔
        // 이 GET 진입점에서 아예 계산되지 않아 forkedProjects가 항상 비어 있었다(TODO로 남아있던 항목).
        val forkedProjects = projectRepository.findByOwnerAndOriginalProject(destination, originalProject)

        model.addAttribute("project", originalProject)
        model.addAttribute("organizations", organizations)
        model.addAttribute("forkedProjects", forkedProjects)
        model.addAttribute("currentUser", loginUser)

        return "project/fork"
    }

    // 12. 프로젝트 포크 실행 (POST /{ownerName}/{projectName}/fork) — legacy PullRequestApp.fork() 대응.
    // legacy는 이 액션에서 실제 git clone을 바로 하지 않고, 이름 중복만 검사한 뒤 "복제 중입니다"
    // 인터스티셜 화면(git/clone.scala.html)을 먼저 보여주고, 그 화면의 JS가 잠시(3초) 후 doClone()을
    // 호출해 실제 git clone + 프로젝트 생성을 수행한다. 이 2단계 구조를 그대로 따른다.
    @PostMapping("/{ownerName}/{projectName}/fork")
    fun fork(
        @PathVariable ownerName: String,
        @PathVariable projectName: String,
        @RequestParam("owner") owner: String,
        @RequestParam("name") name: String,
        @RequestParam(value = "projectScope", required = false, defaultValue = "PUBLIC") projectScope: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val originalProject = projectRepository.findByOwnerAndNameOrPreviousPlace(ownerName, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val destination = owner.trim()
        val forkedProjectName = name.trim()

        // 동일한 소유자 밑에 같은 이름의 프로젝트가 이미 있는지 검사
        if (projectRepository.existsByOwnerAndName(destination, forkedProjectName)) {
            val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
            val organizations = orgUserList.map { it.organization }
            val forkedProjects = projectRepository.findByOwnerAndOriginalProject(destination, originalProject)

            model.addAttribute("project", originalProject)
            model.addAttribute("organizations", organizations)
            model.addAttribute("forkedProjects", forkedProjects)
            model.addAttribute("currentUser", loginUser)
            model.addAttribute("error", "이미 동일한 소유자 밑에 같은 이름의 프로젝트가 존재합니다.")
            return "project/fork"
        }

        model.addAttribute("project", originalProject)
        model.addAttribute("forkOwner", destination)
        model.addAttribute("forkName", forkedProjectName)
        model.addAttribute("forkProjectScope", projectScope)
        model.addAttribute("currentUser", loginUser)
        return "pullrequest/clone"
    }

    // legacy PullRequestApp.doClone() 대응 — pullrequest/clone.html이 로드 3초 후
    // AJAX로 호출하는 실제 git clone + 프로젝트 생성 엔드포인트. legacy와 동일한 응답 형태
    // ({"status":"success"|"failed","url":"..."})를 돌려준다.
    @PostMapping("/api/{ownerName}/{projectName}/doClone")
    @ResponseBody
    fun doClone(
        @PathVariable ownerName: String,
        @PathVariable projectName: String,
        @RequestParam("owner") owner: String,
        @RequestParam("name") name: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val originalProject = projectRepository.findByOwnerAndNameOrPreviousPlace(ownerName, projectName).orElse(null)
            ?: return ResponseEntity.ok(mapOf("status" to "failed", "url" to "/"))

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.ok(mapOf("status" to "failed", "url" to "/users/loginform"))

        val destination = owner.trim()
        val forkedProjectName = name.trim()

        return try {
            projectService.forkProject(
                projectId = originalProject.id!!,
                forkerId = loginUser.id!!,
                destinationOwner = destination,
                destinationName = forkedProjectName
            )
            ResponseEntity.ok(mapOf("status" to "success", "url" to "/$destination/$forkedProjectName"))
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf("status" to "failed", "url" to "/$ownerName/$projectName/pulls"))
        }
    }

    private fun getProjectDashboardData(
        project: Project,
        model: Model,
        projectUsers: List<ProjectUser>
    ) {
        val openIssues = issueRepository.findByProjectAndState(project, State.OPEN)
        val allIssues = issueRepository.findByProject(project)
        val totalOpenIssuesCount = openIssues.size.toDouble()

        // 1. Assignees
        val memberUsers = projectUsers.map { it.user }.toMutableSet()
        openIssues.forEach { issue ->
            issue.assignee?.user?.let { memberUsers.add(it) }
        }
        val assigneeList = memberUsers.map { user ->
            val count = openIssues.count { it.assignee?.user?.id == user.id }
            val percent = if (totalOpenIssuesCount > 0) (count / totalOpenIssuesCount * 100).toInt() else 0
            AssigneeDashboardDto(user, count, percent)
        }.filter { it.count > 0 }.sortedByDescending { it.count }

        val notAssignedIssuesCount = openIssues.count { it.assignee == null }
        val notAssignedIssuesPercent = if (totalOpenIssuesCount > 0) (notAssignedIssuesCount / totalOpenIssuesCount * 100).toInt() else 0

        // 2. Milestones
        val openMilestones = milestoneRepository.findByProjectAndState(project, State.OPEN)
        val milestoneList = openMilestones.map { milestone ->
            val openCount = openIssues.count { it.milestone?.id == milestone.id }
            val totalInMilestone = allIssues.count { it.milestone?.id == milestone.id }
            val closedInMilestone = allIssues.count { it.milestone?.id == milestone.id && it.state == State.CLOSED }
            val completionRate = if (totalInMilestone > 0) (closedInMilestone.toDouble() / totalInMilestone * 100).toInt() else 0
            MilestoneDashboardDto(milestone.id!!, milestone.title, openCount, completionRate)
        }.filter { it.openCount > 0 }.sortedByDescending { it.openCount }

        val noMilestoneIssuesCount = openIssues.count { it.milestone == null }

        // 3. PullRequests
        val openPullRequests = pullRequestRepository.findByToProjectAndState(project, State.OPEN, PageRequest.of(0, 10, Sort.by("created").descending())).content
        val totalOpenPullRequestsCount = pullRequestRepository.findByToProjectAndState(project, State.OPEN).size

        // 4. Labels
        val projectLabels = issueLabelService.getLabels(project.id!!)
        val labelCategories = projectLabels.groupBy { it.category }.map { (category, labels) ->
            val labelDtos = labels.map { label ->
                val count = openIssues.count { it.labels.any { l -> l.id == label.id } }
                LabelDashboardDto(label.id!!, label.name, count)
            }
            LabelCategoryDashboardDto(category.name, labelDtos)
        }

        model.addAttribute("openIssuesCount", openIssues.size)
        model.addAttribute("assigneeList", assigneeList)
        model.addAttribute("notAssignedIssuesCount", notAssignedIssuesCount)
        model.addAttribute("notAssignedIssuesPercent", notAssignedIssuesPercent)
        model.addAttribute("milestoneList", milestoneList)
        model.addAttribute("noMilestoneIssuesCount", noMilestoneIssuesCount)
        model.addAttribute("openPullRequests", openPullRequests)
        model.addAttribute("totalOpenPullRequestsCount", totalOpenPullRequestsCount)
        model.addAttribute("labelCategories", labelCategories)
    }

    data class AssigneeDashboardDto(
        val user: User,
        val count: Int,
        val percent: Int
    )

    data class MilestoneDashboardDto(
        val id: Long,
        val title: String,
        val openCount: Int,
        val completionRate: Int
    )

    data class LabelDashboardDto(
        val id: Long,
        val name: String,
        val count: Int
    )

    data class LabelCategoryDashboardDto(
        val name: String,
        val labels: List<LabelDashboardDto>
    )

    private fun getReadmeFileName(project: Project): String? {
        try {
            val repo = repositoryService.getRepository(project)
            val baseFileName = "README.md"
            if (repo.isFile(baseFileName)) {
                return baseFileName
            }
            if (repo.isFile(baseFileName.lowercase())) {
                return baseFileName.lowercase()
            }
            if (repo.javaClass.simpleName.contains("Svn", ignoreCase = true)) {
                val svnPath = "/trunk/$baseFileName"
                if (repo.isFile(svnPath)) {
                    return svnPath
                }
                if (repo.isFile(svnPath.lowercase())) {
                    return svnPath.lowercase()
                }
            }
        } catch (e: Exception) {
            // NOOP
        }
        return null
    }

    private fun getReadmeContent(project: Project, fileName: String): String? {
        return try {
            val repo = repositoryService.getRepository(project)
            val bytes = repo.getRawFile("HEAD", fileName)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}

// yona ProjectApp.newProjectForm()/newProject() — Play Form의 값 자동 재바인딩(validation
// 실패 시 입력값 보존) 대응. 실패 후 폼을 다시 그릴 때도 사용자가 입력했던 값을 그대로 유지한다.
class NewProjectForm {
    var owner: String = ""
    var name: String = ""
    var overview: String = ""
    var projectScope: ProjectScope = ProjectScope.PUBLIC
    var vcs: String = "GIT"
    var code: Boolean = true
    var issue: Boolean = true
    var pullRequest: Boolean = true
    var review: Boolean = true
    var milestone: Boolean = true
    var board: Boolean = true
}
