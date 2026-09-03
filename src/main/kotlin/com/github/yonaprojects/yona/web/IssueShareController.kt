package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.issue.IssueShareService
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class IssueShareController(
    private val issueShareService: IssueShareService,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val issueService: IssueService
) {

    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/assignableUsers")
    fun findAssignableUsersOfProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "") query: String,
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any>>> {
        val currentUser = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val list = issueShareService.findAssignableUsersOfProject(project, query, currentUser)
        return ResponseEntity.ok(list)
    }

    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/assignableUsers")
    fun findAssignableUsers(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestParam(required = false, defaultValue = "") query: String,
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any>>> {
        val currentUser = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val list = issueShareService.findAssignableUsers(issue, query, currentUser)
        return ResponseEntity.ok(list)
    }

    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/assignees")
    fun updateAssignees(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody body: Map<String, Any>,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val currentUser = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val assigneesList = body["assignees"] as? List<*> ?: return ResponseEntity.badRequest().build()
        if (assigneesList.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("message" to "No assignee"))
        }

        val assigneeLoginId = assigneesList[0].toString()
        val targetUser = userRepository.findByLoginId(assigneeLoginId).orElse(null)

        val updatedIssue = issueService.changeAssignee(issue.id!!, targetUser, currentUser.loginId)

        val result = mutableMapOf<String, Any>()
        val assigneeNode = mutableMapOf<String, Any>()
        assigneeNode["loginId"] = assigneeLoginId
        if (targetUser == null) {
            assigneeNode["name"] = "지정 안 됨"
        } else {
            assigneeNode["name"] = targetUser.getDisplayName()
        }
        result["assignee"] = assigneeNode
        result["issue"] = "/api/projects/${project.id}/issues/${updatedIssue.id}"

        return ResponseEntity.ok(result)
    }

    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/findSharer")
    fun findSharerByloginIds(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestParam query: String,
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any>>> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val list = issueShareService.findSharerByloginIds(issue, query)
        return ResponseEntity.ok(list)
    }

    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/sharableUsers")
    fun findSharableUsers(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestParam(required = false, defaultValue = "") query: String,
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any>>> {
        val list = issueShareService.findSharableUsers(query, null)
        return ResponseEntity.ok(list)
    }

    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/share")
    fun updateSharer(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestBody body: Map<String, Any>,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val currentUser = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val sharerNode = body["sharer"] as? Map<*, *> ?: return ResponseEntity.badRequest().build()
        val loginId = sharerNode["loginId"]?.toString() ?: return ResponseEntity.badRequest().build()
        val type = sharerNode["type"]?.toString() ?: "user"
        val action = body["action"]?.toString() ?: "add"

        val result = issueShareService.changeSharer(issue, loginId, type, action, currentUser)
        return ResponseEntity.ok(result)
    }

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }
}
