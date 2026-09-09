package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

// MilestoneApi.newMilestone() 대응. legacy Open API 네임스페이스
// (`-_-api/v1/owners/{owner}/projects/{projectName}/...`)를 그대로 유지하는 컨트롤러 — 로직은
// MilestoneController.kt 파일의 top-level 함수(createMilestoneNode/parseDueOn)를 그대로 재사용한다.
@RestController
class MilestoneApiController(
    private val projectRepository: ProjectRepository,
    private val milestoneRepository: MilestoneRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/milestones")
    fun newMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestBody request: MilestoneController.BulkCreateMilestonesRequest,
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any?>>> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.MILESTONE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val createdMilestones = request.milestones.map { createMilestoneNode(it, project, milestoneRepository) }
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMilestones)
    }
}
