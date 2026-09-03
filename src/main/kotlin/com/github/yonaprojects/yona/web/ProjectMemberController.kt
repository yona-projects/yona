package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserService
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/projects/{projectId}")
class ProjectMemberController(
    private val projectUserService: ProjectUserService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val messageSource: MessageSource,
    private val accessControl: AccessControl,
    private val organizationUserRepository: OrganizationUserRepository
) {

    private fun isProjectManager(projectId: Long, userId: Long): Boolean {
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    // TASK-0417 — 인증 정보가 없거나(PAT 인식 실패 등으로 authentication이 null) 토큰 소유자를
    // DB에서 못 찾는 경우 IllegalArgumentException을 그대로 던지면 @RestController 기본
    // 예외 처리기가 500으로 응답한다("Unauthorized"라는 메시지와 전혀 안 맞는 상태 코드였다 —
    // yona-cli `admin permission add`를 실제 서버에 대고 재현해 발견). ResponseStatusException은
    // Spring MVC가 자동으로 지정된 상태 코드(401)로 변환해주므로 각 호출부에 try/catch를 추가할
    // 필요가 없다.
    private fun getLoginUserId(authentication: Authentication?): Long {
        if (authentication == null) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")
        val user = userRepository.findByLoginId(authentication.name)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }
        return user.id!!
    }

    @PostMapping("/members")
    fun addMember(
        @PathVariable projectId: Long,
        @RequestParam loginId: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.addMember(projectId, loginId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to add member")))
        }
    }

    @PutMapping("/members/{userId}/role")
    fun updateMemberRole(
        @PathVariable projectId: Long,
        @PathVariable userId: Long,
        @RequestParam roleId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.updateMemberRole(projectId, userId, roleId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to update role")))
        }
    }

    @DeleteMapping("/members/{userId}")
    fun removeMember(
        @PathVariable projectId: Long,
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        // 본인 탈퇴이거나 관리자인 경우만 허용
        if (currentUserId != userId && !isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.removeMember(projectId, userId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to remove member")))
        }
    }

    @PostMapping("/enroll")
    fun enroll(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        return try {
            projectUserService.enroll(projectId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to enroll")))
        }
    }

    @PostMapping("/enroll/cancel")
    fun cancelEnroll(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        return try {
            projectUserService.cancelEnroll(projectId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to cancel enroll")))
        }
    }

    @PostMapping("/members/{userId}/accept")
    fun acceptMemberRequest(
        @PathVariable projectId: Long,
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.acceptMemberRequest(projectId, userId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to accept member request")))
        }
    }

    @PostMapping("/members/{userId}/reject")
    fun rejectMemberRequest(
        @PathVariable projectId: Long,
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.rejectMemberRequest(projectId, userId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to reject member request")))
        }
    }


    // yona-wiki P3-02 Step8.6 항목1(2026-09-01) — `yona admin permission list`용 신규 JSON API
    // (`web/ProjectPermissionRestApiController.kt`, `/api/v1/projects/{owner}/{project}/permissions`)가
    // 위임하는 대상. 이 컨트롤러엔 멤버 추가/역할변경/삭제만 있고 "현재 멤버+역할 목록" 자체를
    // 내려주는 엔드포인트가 없었다(가장 가까운 `assignableUsers`는 배정 후보 목록이지 이미 배정된
    // 권한 매트릭스가 아니다) — 4라운드 완료 로그가 이 갭을 그대로 기록해뒀다.
    @GetMapping("/members")
    fun listMembers(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val members = projectUserRepository.findByProjectId(projectId).map { projectUser ->
            mapOf(
                "userId" to projectUser.user.id,
                "loginId" to projectUser.user.loginId,
                "name" to projectUser.user.getDisplayName(),
                "roleId" to projectUser.role.id,
                "roleName" to projectUser.role.name
            )
        }
        return ResponseEntity.ok(members)
    }

    @GetMapping("/assignableUsers")
    fun assignableUsers(
        @PathVariable projectId: Long,
        @RequestParam(required = false, defaultValue = "") query: String,
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any>>> {
        val currentUserId = getLoginUserId(authentication)
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val currentUser = userRepository.findById(currentUserId).orElse(null)

        // yona IssueApi.java:738 @IsAllowed(Operation.READ) 대응 (P1-117 부수 발견). 프로젝트
        // 멤버/그룹멤버로만 좁게 검사하던 것을, 사이트매니저/조직관리자 우회와 공개 프로젝트 비멤버
        // 열람까지 포함하는 AccessControl.isAllowed(user, project, Operation.READ)로 교체.
        if (!accessControl.isAllowed(currentUser, project, Operation.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // yona Project.java:566-568 getAssignableUsers() → User.java:446-478
        // findUsersByProjectAndOrganization() 대응 (P1-117). 프로젝트 멤버뿐 아니라, 조직 소속
        // 프로젝트라면(PRIVATE인 경우 조직 관리자만, 그 외에는 조직 멤버 전체를) 후보에 포함하고,
        // 사이트관리자 본인도 항상 후보에 포함한다.
        val memberIds = mutableSetOf<Long>()
        projectUserRepository.findByProjectId(projectId).forEach { pu -> pu.user.id?.let { memberIds.add(it) } }

        project.organization?.let { organization ->
            val orgUsers = if (project.isPrivate) {
                organizationUserRepository.findByOrganizationIdAndRoleId(organization.id!!, RoleType.ORG_ADMIN.roleType)
            } else {
                organizationUserRepository.findByOrganizationId(organization.id!!)
            }
            orgUsers.forEach { ou -> ou.user.id?.let { memberIds.add(it) } }
        }

        if (currentUser?.isSiteManager == true) {
            currentUser.id?.let { memberIds.add(it) }
        }

        val members = userRepository.findAllById(memberIds).sortedBy { it.name }

        val result = mutableListOf<Map<String, Any>>()

        if (query.isBlank() && currentUser != null) {
            val locale = LocaleContextHolder.getLocale()
            val assignToMeText: String = messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", locale) ?: "나에게 할당하기"
            val pureName: String = currentUser.getPureNameOnly() ?: ""
            val loginId: String = currentUser.loginId ?: ""

            result.add(mapOf(
                "userId" to (currentUser.id ?: 0L),
                "loginId" to loginId,
                "name" to assignToMeText,
                "pureNameOnly" to pureName,
                "avatarUrl" to "",
                "type" to "user"
            ))
        }

        members.forEach { user ->
            if (query.isBlank() ||
                user.loginId.contains(query, ignoreCase = true) ||
                user.name.contains(query, ignoreCase = true) ||
                (user.englishName?.contains(query, ignoreCase = true) == true)
            ) {
                result.add(mapOf(
                    // yona-wiki P3-02 13라운드(TASK-0430) — yona-cli가 `issue create/edit
                    // --assignee <loginId>`/`pr edit --assignee <loginId>`를 REST가 요구하는
                    // 숫자 assigneeId/userId로 변환하려면 이 엔드포인트가 userId를 내려줘야 하는데
                    // 원래 loginId만 있어 CLI가 로그인ID를 담당자로 지정할 방법이 없었다(웹 UI는
                    // select2가 loginId를 그대로 hidden input에 넣고 서버가 loginId->User를
                    // 별도 경로로 조회하는 방식이라 이 문제가 없었다). 기존 소비자(select2 "user"
                    // 포맷)는 알려지지 않은 필드를 무시하므로 추가만으로는 웹 UI를 깨뜨리지 않는다.
                    "userId" to (user.id ?: 0L),
                    "loginId" to user.loginId,
                    "name" to user.getDisplayName(),
                    "pureNameOnly" to user.getPureNameOnly(),
                    "avatarUrl" to user.avatarUrl,
                    "type" to "user"
                ))
            }
        }

        return ResponseEntity.ok(result)
    }
}
