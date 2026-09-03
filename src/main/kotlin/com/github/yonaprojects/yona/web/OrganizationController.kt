package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.organization.OrganizationService
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/organizations")
class OrganizationController(
    private val organizationService: OrganizationService,
    private val organizationUserRepository: OrganizationUserRepository,
    private val userRepository: UserRepository
) {

    // yona AccessControl.java:119-203 isGlobalResourceAllowed()의 ORGANIZATION 케이스
    // "user.isSiteManager() || isOrganizationAdmin" 대응 (P0-21). 사이트매니저 전역 우회가
    // 빠져 있어 조직 관리자가 아닌 사이트매니저는 이 REST API 전체에서 403을 받고 있었다.
    private fun isOrgAdmin(orgId: Long, userId: Long): Boolean {
        val isSiteManager = userRepository.findById(userId).map { it.isSiteManager }.orElse(false)
        if (isSiteManager) {
            return true
        }
        return organizationUserRepository.findByOrganizationIdAndUserId(orgId, userId)
            .map { it.role.id == RoleType.ORG_ADMIN.roleType }
            .orElse(false)
    }

    private fun getLoginUserId(authentication: Authentication?): Long {
        if (authentication == null) throw IllegalArgumentException("Unauthorized")
        val user = userRepository.findByLoginId(authentication.name)
            .orElseThrow { IllegalArgumentException("User not found") }
        return user.id!!
    }

    @PostMapping
    fun createOrganization(
        @RequestParam name: String,
        @RequestParam(required = false) descr: String?,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val currentUserId = getLoginUserId(authentication)
            val org = organizationService.createOrganization(name, descr, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success", "orgId" to org.id!!))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to create organization")))
        }
    }

    @PutMapping("/{orgId}/settings")
    fun updateOrganizationSettings(
        @PathVariable orgId: Long,
        @RequestParam name: String,
        @RequestParam(required = false) descr: String?,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isOrgAdmin(orgId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            organizationService.updateOrganizationSettings(orgId, name, descr, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to update organization")))
        }
    }

    @PostMapping("/{orgId}/members")
    fun addOrganizationMember(
        @PathVariable orgId: Long,
        @RequestParam userLoginId: String,
        @RequestParam roleId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isOrgAdmin(orgId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            organizationService.addOrganizationMember(orgId, userLoginId, roleId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to add organization member")))
        }
    }

    @PutMapping("/{orgId}/members/{userId}/role")
    fun updateOrganizationMemberRole(
        @PathVariable orgId: Long,
        @PathVariable userId: Long,
        @RequestParam roleId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isOrgAdmin(orgId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            organizationService.updateOrganizationMemberRole(orgId, userId, roleId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to update member role")))
        }
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    fun removeOrganizationMember(
        @PathVariable orgId: Long,
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        // 본인 탈퇴이거나 조직 관리자인 경우만 허용
        if (currentUserId != userId && !isOrgAdmin(orgId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            organizationService.removeOrganizationMember(orgId, userId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to remove member")))
        }
    }

    @DeleteMapping("/{orgId}")
    fun deleteOrganization(
        @PathVariable orgId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isOrgAdmin(orgId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            organizationService.deleteOrganization(orgId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to delete organization")))
        }
    }
}
