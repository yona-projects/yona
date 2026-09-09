package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueSpecification
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import org.springframework.beans.factory.annotation.Value
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.organization.OrganizationService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.attachment.LogoValidator
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.http.ResponseEntity
import org.springframework.core.io.Resource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import java.text.Normalizer

@Controller
class OrganizationViewController(
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val organizationService: OrganizationService,
    private val attachmentRepository: AttachmentRepository,
    private val attachmentService: AttachmentService,
    private val accessControl: AccessControl,
    private val mentionService: MentionService,
    private val roleRepository: RoleRepository,
    // yona controllers/Application.java의 HIDE_PROJECT_LISTING 대응.
    @Value("\${yona.application.hide-project-listing:false}")
    private val hideProjectListing: Boolean = false
) {

    @GetMapping(value = ["/org/{orgName}", "/organizations/{orgName}"])
    fun organizationHome(
        @PathVariable orgName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        val isGuest = if (loginUser != null) {
            org.organizationUsers.none { it.user.id == loginUser.id }
        } else {
            false
        }

        val isEnrolled = if (loginUser != null) {
            org.enrolledUsers.any { it.id == loginUser.id }
        } else {
            false
        }

        model.addAttribute("org", org)
        model.addAttribute("projects", accessControl.getVisibleProjects(org, loginUser))
        model.addAttribute("orgUsers", org.organizationUsers)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isGuest", isGuest)
        model.addAttribute("isEnrolled", isEnrolled)

        return "organization/view"
    }

    @GetMapping(value = ["/org/{orgName}/members", "/organizations/{orgName}/members"])
    fun organizationMembers(
        @PathVariable orgName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        // 관리 권한 검증 (조직 Admin 권한이 요구됨)
        val isOrgAdmin = org.organizationUsers.any {
            it.user.id == loginUser?.id && (it.role.id == 6L) // ORG_ADMIN = 6L
        }

        if (!isOrgAdmin && loginUser?.isSiteManager != true) {
            // yona error/forbidden_organization.scala.html 대응 — 조직은 이미 찾았으므로 조직
            // 헤더/메뉴가 붙는 컨텍스트 인지형 403.
            model.addAttribute("org", org)
            return "error/forbidden_organization"
        }

        model.addAttribute("org", org)
        model.addAttribute("orgUsers", org.organizationUsers)
        // yona organization/members.scala.html:21 roles: List[Role](Role.findOrganizationRoles()) 대응 —
        // ORG_ADMIN/ORG_MEMBER 역할 드롭다운(memberRole 매크로)에 필요.
        model.addAttribute("roles", roleRepository.findAllById(listOf(RoleType.ORG_ADMIN.roleType, RoleType.ORG_MEMBER.roleType)))
        model.addAttribute("currentUser", loginUser)

        return "organization/members"
    }

    // yona organization/group_issue_list.scala.html + group_issue_search_partial.scala.html 대응.
    @GetMapping(value = ["/org/{orgName}/issues", "/organizations/{orgName}/issues"])
    fun organizationIssues(
        @PathVariable orgName: String,
        @RequestParam(required = false, defaultValue = "open") state: String,
        @RequestParam(required = false, defaultValue = "") filter: String,
        @RequestParam(required = false, defaultValue = "createdDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "desc") orderDir: String,
        @RequestParam(required = false) authorId: Long?,
        @RequestParam(required = false) assigneeId: Long?,
        @RequestParam(required = false) mentionId: Long?,
        @RequestParam(value = "projectNames[]", required = false) projectNames: List<String>?,
        @PageableDefault(size = 25) pageable: Pageable,
        authentication: Authentication?,
        model: Model
    ): String {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        val visibleProjects = accessControl.getVisibleProjects(org, loginUser)
        // yona group_issue_search_partial.scala.html:42-46 projectNames[] 다중선택 대응 — 선택되면
        // 조직에서 보이는 프로젝트 중 선택분으로만 좁혀서 검색·카운트한다.
        val projects = if (!projectNames.isNullOrEmpty()) {
            visibleProjects.filter { projectNames.contains(it.name) }
        } else {
            visibleProjects
        }

        val issueState = if (state.lowercase() == "closed") State.CLOSED else State.OPEN
        val mentionedIssueIds = mentionId?.let { mentionService.getMentioningIssueIds(it) }

        val sort = if (orderDir.equals("asc", ignoreCase = true)) {
            Sort.by(Sort.Direction.ASC, orderBy)
        } else {
            Sort.by(Sort.Direction.DESC, orderBy)
        }
        val sortedPageable = PageRequest.of(pageable.pageNumber, pageable.pageSize, sort)

        val spec = IssueSpecification.filterOrganizationIssues(
            projects = projects,
            state = issueState,
            filter = filter,
            authorId = authorId,
            assigneeId = assigneeId,
            mentionedIssueIds = mentionedIssueIds
        )
        val page = if (projects.isEmpty()) Page.empty() else issueRepository.findAll(spec, sortedPageable)

        model.addAttribute("org", org)
        model.addAttribute("currentPage", page)
        model.addAttribute("state", issueState)
        model.addAttribute("currentState", state)
        model.addAttribute("filter", filter)
        model.addAttribute("orderBy", orderBy)
        model.addAttribute("orderDir", orderDir)
        model.addAttribute("authorId", authorId)
        model.addAttribute("assigneeId", assigneeId)
        model.addAttribute("mentionId", mentionId)
        model.addAttribute("visibleProjects", visibleProjects)
        model.addAttribute("selectedProjectNames", projectNames ?: mutableListOf<String>())
        model.addAttribute("openCount", issueRepository.countByProjectInAndState(projects, State.OPEN))
        model.addAttribute("closedCount", issueRepository.countByProjectInAndState(projects, State.CLOSED))
        model.addAttribute("currentUser", loginUser)

        return "organization/issueList"
    }

    // yona organization/group_board_list.scala.html 대응.
    @GetMapping(value = ["/org/{orgName}/boards", "/organizations/{orgName}/boards"])
    fun organizationBoards(
        @PathVariable orgName: String,
        @RequestParam(required = false, defaultValue = "") filter: String,
        @RequestParam(required = false, defaultValue = "updatedDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "desc") orderDir: String,
        @RequestParam(value = "projectNames[]", required = false) projectNames: List<String>?,
        @PageableDefault(size = 25) pageable: Pageable,
        authentication: Authentication?,
        model: Model
    ): String {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        val visibleProjects = accessControl.getVisibleProjects(org, loginUser)
        val projects = if (!projectNames.isNullOrEmpty()) {
            visibleProjects.filter { projectNames.contains(it.name) }
        } else {
            visibleProjects
        }

        val sort = if (orderDir.equals("asc", ignoreCase = true)) {
            Sort.by(Sort.Direction.ASC, orderBy)
        } else {
            Sort.by(Sort.Direction.DESC, orderBy)
        }
        val sortedPageable = PageRequest.of(pageable.pageNumber, pageable.pageSize, sort)

        val page = if (projects.isEmpty()) {
            Page.empty()
        } else {
            postingRepository.findByProjectInAndKeyword(projects, filter, sortedPageable)
        }

        // yona group_board_list.scala.html:65 notices — 1페이지에서만 상단에 노출.
        val notices = if (projects.isEmpty() || page.number != 0) {
            emptyList()
        } else {
            postingRepository.findByProjectInAndNotice(projects, true)
        }

        model.addAttribute("org", org)
        model.addAttribute("currentPage", page)
        model.addAttribute("notices", notices)
        model.addAttribute("filter", filter)
        model.addAttribute("orderBy", orderBy)
        model.addAttribute("orderDir", orderDir)
        model.addAttribute("visibleProjects", visibleProjects)
        model.addAttribute("selectedProjectNames", projectNames ?: mutableListOf<String>())
        model.addAttribute("currentUser", loginUser)

        return "organization/boardList"
    }

    // yona organization/group_pullrequest_list.scala.html + group_pullrequest_list_partial.scala.html 대응.
    @GetMapping(value = [
        "/org/{orgName}/pullrequests", "/organizations/{orgName}/pullrequests",
        "/org/{orgName}/closedPullrequests", "/organizations/{orgName}/closedPullrequests"
    ])
    fun organizationPullRequests(
        @PathVariable orgName: String,
        @RequestParam(required = false, defaultValue = "open") category: String,
        @RequestParam(required = false, defaultValue = "") filter: String,
        @PageableDefault(size = 25) pageable: Pageable,
        request: HttpServletRequest,
        authentication: Authentication?,
        model: Model
    ): String {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        val isClosed = category.lowercase() == "closed" || request.requestURI.contains("closed")
        val prState = if (isClosed) State.CLOSED else State.OPEN

        val projects = accessControl.getVisibleProjects(org, loginUser)
        val page = if (projects.isEmpty()) {
            Page.empty()
        } else {
            pullRequestRepository.searchByToProjectInAndState(projects, prState, filter, pageable)
        }

        model.addAttribute("org", org)
        model.addAttribute("currentPage", page)
        model.addAttribute("filter", filter)
        model.addAttribute("requestType", if (isClosed) "closed" else "open")
        model.addAttribute("category", if (isClosed) "closed" else "open")
        model.addAttribute("openCount", pullRequestRepository.countByToProjectInAndState(projects, State.OPEN))
        model.addAttribute("closedCount", pullRequestRepository.countByToProjectInAndState(projects, State.CLOSED))
        model.addAttribute("currentUser", loginUser)

        return "organization/pullRequestList"
    }

    // yona OrganizationApp의 leave()/validateForLeave() 대응.
    @ResponseBody
    @DeleteMapping(value = ["/org/{orgName}/leave", "/organizations/{orgName}/leave"])
    fun leave(
        @PathVariable orgName: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("errorMsg" to "organization.member.unknownOrganization"))

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("errorMsg" to "unauthorized"))

        return try {
            organizationService.leaveOrganization(org.id!!, loginUser.id!!)
            ResponseEntity.ok(mapOf("location" to "/organizations/${org.name}"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("errorMsg" to (e.message ?: "organization.member.leave.unknownerror")))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("errorMsg" to (e.message ?: "organization.member.leave.unknownerror")))
        }
    }

    // 1. 조직 생성 폼 화면 (GET /organizations/new)
    @GetMapping("/organizations/new")
    fun createForm(authentication: Authentication?, model: Model): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"
        model.addAttribute("currentUser", loginUser)
        return "organization/create"
    }

    // 2. 조직 생성 처리 (POST /organizations/new)
    @PostMapping("/organizations/new")
    fun createOrganization(
        @RequestParam name: String,
        @RequestParam(required = false) descr: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        // yona OrganizationApp의 @GuestProhibit 대응.
        if (loginUser.isGuest) {
            return "redirect:/"
        }

        return try {
            val org = organizationService.createOrganization(name, descr, loginUser.id!!)
            "redirect:/organizations/${org.name}"
        } catch (e: Exception) {
            // yona organization/create.scala.html:35-40 requestHeader.flash.get("warning") 대응 —
            // 검증 실패 시에도 입력값(name/descr)을 보존한 채로 폼을 재표시한다.
            model.addAttribute("currentUser", loginUser)
            model.addAttribute("error", e.message ?: "Failed to create organization")
            model.addAttribute("name", name)
            model.addAttribute("descr", descr)
            "organization/create"
        }
    }

    // 3. 조직 로고 이미지 조회 (GET /organizations/{orgId}/logo)
    @GetMapping("/organizations/{orgId}/logo")
    fun organizationLogo(
        @PathVariable orgId: Long
    ): ResponseEntity<Resource> {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(
            ResourceType.ORGANIZATION,
            orgId.toString()
        )
        val attachment = attachments.firstOrNull()

        if (attachment == null) {
            // 디폴트 그룹 이미지 반환 — 예전에는 하드코딩된 개발자 로컬 절대경로를 썼던 실버그가
            // 있었다. static 리소스는 classpath에서 로드해야 배포 환경에서도 동작한다.
            val defaultImage = ClassPathResource("static/images/group_default.png")
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

    // 4. 조직 설정 화면 (GET /organizations/{orgName}/settingform)
    @GetMapping(value = ["/org/{orgName}/settingform", "/organizations/{orgName}/settingform"])
    fun settingForm(
        @PathVariable orgName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        // 관리 권한 검증 (조직 Admin 권한이 요구됨)
        val isOrgAdmin = org.organizationUsers.any {
            it.user.id == loginUser.id && (it.role.id == RoleType.ORG_ADMIN.roleType)
        }

        if (!isOrgAdmin && !loginUser.isSiteManager) {
            // yona error/forbidden_organization.scala.html 대응 — 조직은 이미 찾았음.
            model.addAttribute("org", org)
            return "error/forbidden_organization"
        }

        model.addAttribute("org", org)
        model.addAttribute("currentUser", loginUser)

        return "organization/setting"
    }

    // 5. 조직 설정 변경 처리 (POST /organizations/{orgName}/setting)
    @PostMapping(value = ["/org/{orgName}/setting", "/organizations/{orgName}/setting"])
    fun updateOrganization(
        @PathVariable orgName: String,
        @RequestParam("name") name: String,
        @RequestParam(value = "descr", required = false) descr: String?,
        @RequestParam(value = "logoPath", required = false) logoFile: MultipartFile?,
        authentication: Authentication?,
        model: Model,
        redirectAttributes: RedirectAttributes
    ): String {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val isOrgAdmin = org.organizationUsers.any {
            it.user.id == loginUser.id && (it.role.id == RoleType.ORG_ADMIN.roleType)
        }

        if (!isOrgAdmin && !loginUser.isSiteManager) {
            // yona error/forbidden_organization.scala.html 대응.
            model.addAttribute("org", org)
            return "error/forbidden_organization"
        }

        // yona OrganizationApp의 validateForUpdate()가 하는 LogoUtil.isImageFile()/
        // LOGO_FILE_LIMIT_SIZE 검증 대응. 로고가 유효하지 않으면 이름/설명 변경을 포함해 갱신
        // 자체를 아무 것도 반영하지 않는다(legacy가 badRequest(setting.render(...))로 응답하는
        // 것과 동일).
        if (logoFile != null && !logoFile.isEmpty) {
            val filename = logoFile.originalFilename ?: ""
            if (!LogoValidator.isImageFile(filename)) {
                model.addAttribute("org", org)
                model.addAttribute("currentUser", loginUser)
                model.addAttribute("error", "지원하지 않는 이미지 형식입니다.")
                return "organization/setting"
            }
            if (logoFile.size > LogoValidator.LOGO_FILE_LIMIT_SIZE) {
                model.addAttribute("org", org)
                model.addAttribute("currentUser", loginUser)
                model.addAttribute("error", "이미지 파일 크기가 너무 큽니다.")
                return "organization/setting"
            }
        }

        try {
            // 조직 설정 변경 호출
            organizationService.updateOrganizationSettings(org.id!!, name, descr ?: "", loginUser.id!!)

            // 로고 이미지 처리
            if (logoFile != null && !logoFile.isEmpty) {
                // 기존 로고가 있다면 삭제
                val existingLogos = attachmentRepository.findByContainerTypeAndContainerId(
                    ResourceType.ORGANIZATION,
                    org.id.toString()
                )
                existingLogos.forEach {
                    attachmentService.delete(it)
                }

                // 신규 로고 저장
                val normalizedFilename = Normalizer.normalize(logoFile.originalFilename ?: "logo", Normalizer.Form.NFC)
                attachmentService.store(
                    inputStream = logoFile.inputStream,
                    name = normalizedFilename,
                    containerType = ResourceType.ORGANIZATION,
                    containerId = org.id.toString(),
                    ownerLoginId = loginUser.loginId ?: "anonymous"
                )
            }

            return "redirect:/organizations/$name"
        } catch (e: Exception) {
            model.addAttribute("org", org)
            model.addAttribute("currentUser", loginUser)
            model.addAttribute("error", e.message ?: "Failed to update organization settings")
            return "organization/setting"
        }
    }

    // 6. 조직 삭제 폼 화면 (GET /organizations/{orgName}/deleteForm)
    @GetMapping(value = ["/org/{orgName}/deleteForm", "/organizations/{orgName}/deleteForm"])
    fun deleteForm(
        @PathVariable orgName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val isOrgAdmin = org.organizationUsers.any {
            it.user.id == loginUser.id && (it.role.id == RoleType.ORG_ADMIN.roleType)
        }

        if (!isOrgAdmin && !loginUser.isSiteManager) {
            // yona error/forbidden_organization.scala.html 대응.
            model.addAttribute("org", org)
            return "error/forbidden_organization"
        }

        model.addAttribute("org", org)
        model.addAttribute("currentUser", loginUser)

        return "organization/delete"
    }

    // 7. 조직 삭제 처리 (DELETE /organizations/{orgName})
    @ResponseBody
    @DeleteMapping(value = ["/org/{orgName}", "/organizations/{orgName}"])
    fun deleteOrganization(
        @PathVariable orgName: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val org = organizationRepository.findByName(orgName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("errorMsg" to "Organization not found"))

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("errorMsg" to "Unauthorized"))

        val isOrgAdmin = org.organizationUsers.any {
            it.user.id == loginUser.id && (it.role.id == RoleType.ORG_ADMIN.roleType)
        }

        if (!isOrgAdmin && !loginUser.isSiteManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("errorMsg" to "Forbidden"))
        }

        return try {
            organizationService.deleteOrganization(org.id!!, loginUser.id!!)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("errorMsg" to (e.message ?: "Failed to delete organization")))
        }
    }

    @ResponseBody
    @PostMapping(value = ["/org/{orgName}/enroll", "/organizations/{orgName}/enroll"])
    fun enroll(
        @PathVariable orgName: String,
        authentication: Authentication?,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        return try {
            val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Unauthorized"))

            organizationService.enroll(orgName, loginUser.id!!)

            val host = request.getHeader("Host") ?: "localhost:8080"
            val statusMonitorUrl = "http://$host/organizations/$orgName"

            ResponseEntity.status(HttpStatus.ACCEPTED).body(mapOf(
                "message" to "You enrolled in $orgName.",
                "statusMonitorUrl" to statusMonitorUrl
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to enroll")))
        }
    }

    @ResponseBody
    @PostMapping(value = ["/org/{orgName}/cancel/enroll", "/organizations/{orgName}/cancel/enroll"])
    fun cancelEnroll(
        @PathVariable orgName: String,
        authentication: Authentication?,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        return try {
            val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Unauthorized"))

            organizationService.cancelEnroll(orgName, loginUser.id!!)

            val host = request.getHeader("Host") ?: "localhost:8080"
            val statusMonitorUrl = "http://$host/organizations/$orgName"

            ResponseEntity.status(HttpStatus.ACCEPTED).body(mapOf(
                "message" to "You canceled to enroll in $orgName.",
                "statusMonitorUrl" to statusMonitorUrl
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to cancel enroll")))
        }
    }

    @GetMapping("/orgs")
    fun orgList(
        @RequestParam(value = "filter", defaultValue = "") filter: String,
        @RequestParam(value = "pageNum", defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        // yona OrganizationApp의 @GuestProhibit 대응. 게스트 계정(User.isGuest)은 인덱스로
        // 리다이렉트한다 — 로그인하지 않은 익명 사용자는 isGuest가 아니므로 그대로 통과한다.
        if (loginUser?.isGuest == true) {
            return "redirect:/"
        }

        // HIDE_PROJECT_LISTING이 켜져 있으면 누구도 전체 조직 목록을 볼 수 없다.
        if (hideProjectListing) {
            return "error/403"
        }

        if (pageNum < 1) {
            return "error/404"
        }

        val pageable = PageRequest.of(pageNum - 1, 30, Sort.by("id").descending())
        val orgPage = organizationRepository.findByNameContainingIgnoreCaseOrDescrContainingIgnoreCase(filter, filter, pageable)

        model.addAttribute("orgs", orgPage)
        model.addAttribute("filter", filter)
        model.addAttribute("currentUser", loginUser)

        return "organization/list"
    }
}
