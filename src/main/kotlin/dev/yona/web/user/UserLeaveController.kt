package dev.yona.web.user

import dev.yona.domain.project.ProjectMemberService
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * 사용자 페이지의 프로젝트 목록에서 누르는 프로젝트 탈퇴이다(POST). v1.6 `UserApp.leave`(`app/controllers/UserApp.java:992-996`)에 해당한다.
 *
 * v1.6은 `ProjectApp.deleteMember`를 불러 현재 사용자를 구성원에서 빼고, 그 결과(소유자라서 거부됨 등)는 보지 않은 채
 * 항상 내 사용자 페이지의 프로젝트 탭(`/<로그인 ID>?daysAgo=14&selected=projects`)으로 보낸다. 같은 동작이다.
 * 소유자는 구성원에서 빠지지 않는다.
 */
@Controller
class UserLeaveController(
    private val projectService: ProjectService,
    private val memberService: ProjectMemberService,
    private val currentUserProvider: CurrentUserProvider
) {

    // v1.6은 GET이라 링크나 이미지 태그만으로 로그인한 사용자를 프로젝트에서 탈퇴시킬 수 있었다. POST로 받는다(R-48-2)
    @PostMapping("/info/leave/{user}/{project}")
    @Transactional
    fun leave(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        val me = currentUserProvider.get().user
        if (me?.id != null && !project.isOwner(me)) memberService.removeMember(project, me.id!!)
        return RedirectSupport.see(request, response, "/${me?.loginId ?: "anonymous"}?daysAgo=14&selected=projects")
    }
}
