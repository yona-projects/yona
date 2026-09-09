package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import com.github.yonaprojects.yona.domain.project.UpdateProjectParam
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.PushedBranch
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import java.time.Duration
import java.time.Instant
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class ProjectController(
    private val projectService: ProjectService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val accessControl: AccessControl,
    private val titleHeadService: TitleHeadService,
    private val issueLabelRepository: IssueLabelRepository
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun isProjectManager(projectId: Long, userId: Long): Boolean {
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    private fun isProjectMember(projectId: Long, userId: Long): Boolean {
        return projectUserRepository.existsByProjectIdAndUserId(projectId, userId)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    @GetMapping("/api/projects/search")
    fun searchProjects(
        @RequestParam(value = "query", defaultValue = "") query: String,
        authentication: Authentication?
    ): ResponseEntity<List<String>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pageable = PageRequest.of(0, 100)
        val projectNames = if (user.isSiteManager) {
            projectRepository.findProjectsForAdmin(query, pageable).content.map { "${it.owner}/${it.name}" }
        } else {
            val allowedIds = projectRepository.findAllowedProjectIdsForUser(user.id!!)
            if (allowedIds.isEmpty()) {
                val publicIds = projectRepository.findPublicProjectIds()
                if (publicIds.isEmpty()) {
                    emptyList()
                } else {
                    projectRepository.searchProjects(publicIds, query, pageable).content.map { "${it.owner}/${it.name}" }
                }
            } else {
                projectRepository.searchProjects(allowedIds, query, pageable).content.map { "${it.owner}/${it.name}" }
            }
        }
        return ResponseEntity.ok(projectNames)
    }

    @PutMapping("/api/projects/{projectId}")
    fun updateProject(
        @PathVariable projectId: Long,
        @RequestBody request: UpdateProjectRequest,
        authentication: Authentication?
    ): ResponseEntity<*> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Project>()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Project>()
        }

        val updated = try {
            projectService.updateProject(
                projectId = projectId,
                param = UpdateProjectParam(
                    name = request.name,
                    overview = request.overview,
                    projectScope = request.projectScope,
                    isCodeAccessibleMemberOnly = request.isCodeAccessibleMemberOnly,
                    isUsingReviewerCount = request.isUsingReviewerCount,
                    defaultReviewerCount = request.defaultReviewerCount,
                    defaultBranch = request.defaultBranch,
                    isCodeEnabled = request.isCodeEnabled,
                    isIssueEnabled = request.isIssueEnabled,
                    isPullRequestEnabled = request.isPullRequestEnabled,
                    isReviewEnabled = request.isReviewEnabled,
                    isMilestoneEnabled = request.isMilestoneEnabled,
                    isBoardEnabled = request.isBoardEnabled,
                    isWikiEnabled = request.isWikiEnabled
                )
            )
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to e.message))
        }
        // raw Project 엔티티를 그대로 반환하면 fork()와 동일한 순환 직렬화 경로로
        // User.password/passwordSalt가 노출된다 — fork()와 동일하게 toRefResponse()로 감싼다.
        return ResponseEntity.ok(updated.toRefResponse())
    }

    @DeleteMapping("/api/projects/{projectId}")
    fun deleteProject(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        
        // 소유자(owner) 본인이거나 MANAGER여야 삭제 가능
        val isOwner = project.owner == user.loginId
        if (!isOwner && !isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        projectService.deleteProject(projectId)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    @PostMapping("/api/{owner}/{projectName}/transfer")
    fun requestTransfer(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam destination: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (!isProjectManager(project.id!!, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            val transfer = projectService.requestNewTransfer(project.id!!, user.id!!, destination)
            ResponseEntity.ok(transfer)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/api/projects/transfer/{transferId}/accept")
    fun acceptTransfer(
        @PathVariable transferId: Long,
        @RequestParam confirmKey: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            projectService.acceptTransfer(transferId, confirmKey, user.id!!)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    // forkedProject(JPA Project 엔티티)를 가공 없이 그대로 반환하면 Project.projectUsers[].user
    // (User.projectUsers와의 양방향 연관)를 따라가며 Jackson이 순환 직렬화를 시도하다
    // User.password/passwordSalt 해시값까지 노출한다 — Project.toRefResponse()(id/owner/name/
    // overview/vcs/scope만 노출)로 감싼다.
    // destinationOwner: 비워두면(기존 동작) forker 본인 계정으로 fork된다. 조직 이름을 지정하면
    // 그 조직으로 fork되는데, ProjectServiceImpl.forkProject()가 forker가 그 조직의 ORG_ADMIN인지
    // 실제로 검증하므로(그렇지 않으면 IllegalArgumentException) 여기서 별도 권한 검사를 하지 않는다.
    // 세션 기반 웹 UI(ProjectViewController.doClone())가 이미 지원하던 조직 목적지 fork를 REST API/
    // yona-cli에도 동일하게 노출한다(GitHub의 `gh repo fork --org` 대응).
    @PostMapping("/api/{owner}/{projectName}/fork")
    fun forkProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestBody(required = false) request: ForkProjectRequest?,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        return try {
            val forkedProject = projectService.forkProject(
                project.id!!, user.id!!,
                destinationOwner = request?.destinationOwner ?: "",
                destinationName = request?.destinationName ?: ""
            )
            ResponseEntity.ok(forkedProject.toRefResponse())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    data class ForkProjectRequest(val destinationOwner: String? = null, val destinationName: String? = null)

    @GetMapping("/api/{owner}/{projectName}/labels")
    fun getProjectLabels(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(projectService.getProjectLabels(project.id!!))
    }

    // AccessControl은 PROJECT_LABELS를 별도 케이스로 다루지 않아 일반 프로젝트 리소스 UPDATE
    // 규칙(user.isMemberOf(project))을 그대로 따른다 — MANAGER가 아니어도 프로젝트 멤버라면
    // 라벨을 붙이고 뗄 수 있다.
    @PostMapping(value = ["/api/{owner}/{projectName}/labels", "/-_-api/v1/owners/{owner}/projects/{projectName}/labels"])
    fun attachLabel(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) category: String?,
        @RequestParam name: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectMember(project.id!!, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = projectService.attachLabel(project.id!!, category, name)
        if (!result.isAttached) {
            // 이미 붙어있던 라벨: yona는 204 No Content를 반환한다.
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        }

        return if (result.isCreated) {
            ResponseEntity.status(HttpStatus.CREATED).body(result.label)
        } else {
            ResponseEntity.ok(result.label)
        }
    }

    @DeleteMapping("/api/{owner}/{projectName}/labels/{labelId}")
    fun detachLabel(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable labelId: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectMember(project.id!!, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val detached = projectService.detachLabel(project.id!!, labelId)
        if (!detached) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    // 이슈/게시글 제목 자동완성에 쓰는 "이전에 쓰인 대괄호 머리말 사용 빈도"와 "프로젝트 이슈 라벨
    // 목록"을 하나의 배열로 합쳐 반환한다(머리말 먼저, 라벨 나중).
    @GetMapping("/api/{owner}/{projectName}/titleHeads")
    fun titleHeads(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "") query: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val titleHeadNodes = titleHeadService.search(project, query).map {
            mapOf(
                "name" to it.headKeyword,
                "frequency" to it.frequency,
                "category" to "",
                "searchText" to it.headKeyword
            )
        }
        val labelNodes = issueLabelRepository.findByProject(project).map { label ->
            mapOf(
                "name" to label.name,
                "frequency" to 0,
                "category" to label.category.name,
                "categoryId" to label.category.id,
                "id" to label.id,
                "labelColor" to label.color,
                "isExclusive" to label.category.isExclusive,
                "searchText" to "${label.name}/${label.category.name}"
            )
        }

        return ResponseEntity.ok(mapOf("result" to (titleHeadNodes + labelNodes)))
    }

    // 삭제 API 단독으로는 사용할 방법이 없어 같은 데이터를 노출하는 조회용 엔드포인트를 함께 둔다.
    @GetMapping("/api/{owner}/{projectName}/pushedBranches")
    fun getPushedBranches(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // 최근 1시간 이내에 push된 것만 노출한다.
        val cutoff = Instant.now().minus(Duration.ofHours(1))
        val branches = pushedBranchRepository.findByProjectAndPushedDateAfter(project, cutoff)
        // 엔티티(PushedBranch -> project -> projectUsers -> user -> ...)를 그대로 직렬화하면
        // User<->ProjectUser 양방향 관계가 순환 참조되며 password/passwordSalt까지 노출된다 —
        // DTO로 변환해 필요한 필드만 반환한다.
        return ResponseEntity.ok(branches.map { PushedBranchDto(it) })
    }

    data class PushedBranchDto(
        val id: Long?,
        val name: String,
        val pushedDate: Instant?
    ) {
        constructor(pushedBranch: PushedBranch) : this(
            id = pushedBranch.id,
            name = pushedBranch.name,
            pushedDate = pushedBranch.pushedDate
        )
    }

    // id가 이 프로젝트 소속인지는 별도로 검증하지 않는다 — 존재하면 삭제, 존재하지 않아도 200 OK.
    @DeleteMapping("/api/{owner}/{projectName}/pushedBranches/{id}")
    fun deletePushedBranch(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectMember(project.id!!, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        pushedBranchRepository.findById(id).ifPresent { pushedBranchRepository.delete(it) }
        return ResponseEntity.ok().build()
    }

    data class UpdateProjectRequest(
        // 값이 없거나 현재 이름과 같으면 서비스 계층에서 무시된다.
        val name: String? = null,
        val overview: String,
        val projectScope: ProjectScope,
        val isCodeAccessibleMemberOnly: Boolean = false,
        val isUsingReviewerCount: Boolean = false,
        val defaultReviewerCount: Int = 1,
        val defaultBranch: String? = null,
        val isCodeEnabled: Boolean = true,
        val isIssueEnabled: Boolean = true,
        val isPullRequestEnabled: Boolean = true,
        val isReviewEnabled: Boolean = true,
        val isMilestoneEnabled: Boolean = true,
        val isBoardEnabled: Boolean = true,
        val isWikiEnabled: Boolean = true
    )
}
