package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.service.MigrationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/migration")
class MigrationApiController(
    private val userRepository: UserRepository,
    private val migrationService: MigrationService
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @GetMapping("/projects")
    fun getMigrationProjects(authentication: Authentication?): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val projects = migrationService.getMigrationProjects(user)
        return ResponseEntity.ok(projects)
    }

    @GetMapping("/{owner}/projects/{projectName}")
    fun getMigrationProjectDetail(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val detail = migrationService.getMigrationProjectDetail(owner, projectName)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(detail)
    }

    @GetMapping("/{owner}/projects/{projectName}/labels")
    fun exportLabels(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val labels = migrationService.exportLabels(owner, projectName)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(labels)
    }

    @GetMapping("/{owner}/projects/{projectName}/issuelabel")
    fun exportIssueLabelPairs(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pairs = migrationService.exportIssueLabelPairs(owner, projectName)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(pairs)
    }

    @GetMapping("/{owner}/projects/{projectName}/milestones")
    fun exportMilestones(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val milestones = migrationService.exportMilestones(owner, projectName)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(mapOf("milestones" to milestones))
    }

    @GetMapping("/{owner}/projects/{projectName}/issues")
    fun exportIssues(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(value = "withWikiCommit", defaultValue = "false") withWikiCommit: Boolean,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val issues = migrationService.exportIssues(owner, projectName, withWikiCommit)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(mapOf("issues" to issues))
    }

    @GetMapping("/{owner}/projects/{projectName}/posts")
    fun exportPosts(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(value = "withWikiCommit", defaultValue = "false") withWikiCommit: Boolean,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val posts = migrationService.exportPosts(owner, projectName, withWikiCommit)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(mapOf("issues" to posts))
    }
}
