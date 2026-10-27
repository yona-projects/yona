package dev.yona.web.project

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.ProjectMemberService
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.HttpPreference
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.DefaultProjectCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 구성원 관리이다. v1.6 `ProjectApp.members`, `newMember`, `editMember`, `deleteMember`
 * (`app/controllers/ProjectApp.java:358-366, 937-1052`)에 해당한다.
 *
 * 목록, 추가, 역할 변경은 프로젝트를 고칠 수 있는 사용자(관리자)만 할 수 있다. 삭제는 본인 탈퇴도 허용하므로 권한을 안에서 확인한다.
 */
@Controller
@AnonymousCheck
class ProjectMemberController(
    private val projectService: ProjectService,
    private val memberService: ProjectMemberService,
    private val views: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider,
    private val accessControl: AccessControl,
    private val avatars: AvatarService,
    private val messages: MessageSource
) {

    @GetMapping("/{user}/{project}/members")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun members(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        val viewer = currentUserProvider.get().user
        memberService.cleanEnrolledUsers(project, viewer)

        val roles = memberService.projectRoles()
        val items = memberService.members(project).mapNotNull { projectUser ->
            val user = projectUser.user ?: return@mapNotNull null
            MemberItem(
                userId = requireNotNull(user.id), loginId = user.loginId.orEmpty(), displayName = UserDisplay.of(user, viewer?.lang),
                avatarUrl = avatars.avatarUrl(user), guest = user.isGuest == true, owner = project.isOwner(user),
                roleName = projectUser.role?.name.orEmpty()
            )
        }
        val enrolled = memberService.enrolledUsers(project).map {
            EnrolledItem(it.loginId.orEmpty(), UserDisplay.of(it, viewer?.lang), avatars.avatarUrl(it))
        }
        return ModelAndView("project/members")
            .addObject("title", "title.projectMembers")
            .addObject("projectView", views.of(project))
            .addObject("memberItems", items)
            .addObject("roles", roles.map { RoleItem(requireNotNull(it.id), it.name.orEmpty()) })
            .addObject("enrolled", enrolled)
    }

    @PostMapping("/{user}/{project}/members")
    @IsAllowed(Operation.UPDATE)
    @DefaultProjectCheck
    @Transactional
    fun addMember(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        val result = memberService.addMember(project, currentUserProvider.get().user, request.getParameter("loginId"))
        val json = HttpPreference.isJsonPreferred(request.getHeader(HttpHeaders.ACCEPT))

        val failure = result.failure
        if (failure != null) {
            if (json) {
                val text = messages.getMessage(failure.messageKey, null, failure.messageKey, LocaleContextHolder.getLocale()) ?: failure.messageKey
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf("loginId" to listOf(text)))
            }
            return RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/members", mapOf(RedirectSupport.WARNING to failure.messageKey))
        }

        if (json) {
            return ResponseEntity.ok().contentType(MediaType("text", "plain", Charsets.UTF_8)).body("{}")
        }
        memberService.watch(requireNotNull(result.newMember), project)
        return RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/members")
    }

    @PostMapping("/{user}/{project}/member/{id}/edit")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun editMember(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        @PathVariable("id") userId: Long,
        request: HttpServletRequest
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        if (project.isOwner(findUser(userId))) {
            return ErrorViews.forbidden("project.member.ownerMustBeAManager", views.of(project)).also { it.status = HttpStatus.BAD_REQUEST }
        }
        // 화면은 프로젝트 역할(관리자, 멤버)만 고르게 한다. 그 밖의 번호는 요청을 직접 만들어야만 보낼 수 있으므로 거부한다([decisions.md] A-24).
        val roleId = request.getParameter("id")?.toLongOrNull()
        if (roleId == null || roleId !in listOf(RoleType.MANAGER.id, RoleType.MEMBER.id)) return ErrorViews.badRequest()
        memberService.changeRole(project, userId, roleId)
        return ResponseEntity.noContent().build<Void>()
    }

    @DeleteMapping("/{user}/{project}/member/{id}/delete")
    @DefaultProjectCheck
    @Transactional
    fun deleteMember(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        @PathVariable("id") userId: Long, request: jakarta.servlet.http.HttpServletRequest
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        val viewer = currentUserProvider.get().user
        val target = findUser(userId)

        if (viewer?.id != userId && !accessControl.isAllowed(viewer, project.asResource(), Operation.UPDATE)) {
            return ErrorViews.forbidden("error.forbidden", views.of(project))
        }
        if (project.isOwner(target)) {
            return ErrorViews.forbidden("project.member.ownerCannotLeave", views.of(project))
        }

        memberService.removeMember(project, userId)

        val location = when {
            viewer?.id == userId ->
                if (accessControl.isAllowed(viewer, project.asResource(), Operation.READ)) dev.yona.util.UrlPaths.projectPath(project.owner, project.name) else "/"
            else -> "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/members"
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("location" to dev.yona.util.UrlPaths.withContext(request.contextPath, location)))
    }

    private fun findUser(userId: Long): User = memberService.user(userId)

    /** 구성원 목록의 한 줄이다. */
    data class MemberItem(
        val userId: Long, val loginId: String, val displayName: String, val avatarUrl: String,
        val guest: Boolean, val owner: Boolean, val roleName: String
    )

    /** 가입 신청 목록의 한 줄이다. */
    data class EnrolledItem(val loginId: String, val displayName: String, val avatarUrl: String)

    /** 역할 선택지이다. */
    data class RoleItem(val id: Long, val name: String)
}
