package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
class ReviewApiController(
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val codeReviewService: CodeReviewService,
    private val accessControl: AccessControl
) {
    // yona AccessControl.isProjectResourceAllowed()의 PULL_REQUEST Operation.ACCEPT 분기
    // (user.isMemberOf(project) || isAllowedIfGroupMember(project, user)) 대응 (P1-78).
    // 리뷰어 등록/해제(review/unreview)는 이 ACCEPT 권한을 요구한다.
    private fun checkWritePermission(project: Project, user: User?): Boolean {
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
            accessControl.isAllowedIfGroupMember(project, user)
    }

    @DeleteMapping("/comments/{type}/{id}")
    fun deleteComment(
        @PathVariable type: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(401).build()

        try {
            if (type.uppercase() == "COMMIT_COMMENT") {
                codeReviewService.deleteCommitComment(id, user)
            } else if (type.uppercase() == "REVIEW_COMMENT") {
                codeReviewService.deleteReviewComment(id, user)
            }
        } catch (e: IllegalArgumentException) {
            if (e.message == "Permission denied") {
                return ResponseEntity.status(403).build()
            }
            throw e
        }
        return ResponseEntity.ok().build()
    }

    @PostMapping("/api/{owner}/{projectName}/pullRequest/{pullRequestId}/review")
    fun review(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable pullRequestId: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        // yona ReviewApp.java:41 @IsAllowed(value = Operation.ACCEPT, resourceType =
        // ResourceType.PULL_REQUEST) 대응 (P-템플릿 #47) — IsAllowedAction.call()이 접근 거부 시
        // forbidden(ErrorViews.Forbidden.render("error.forbidden", project))를 돌려준다. 프로젝트는
        // 이미 찾았으므로 컨텍스트 인지형 403.
        if (!checkWritePermission(project, user)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // yona IsAllowedAction.call()의 resourceObject == null 분기 notFound(ErrorViews.NotFound
        // .render("error.notfound", project, resourceType.resource())) 대응 (P-템플릿 #45).
        // resourceType.resource()는 PULL_REQUEST일 때 "pull_request"이고 이는 notfound.scala.html의
        // "issue_post"/"board_post"/"milestone"/"code" 4가지 case 중 어느 것과도 매치되지 않아
        // 항상 case _(제네릭 문구/뒤로가기)로 빠진다 — 그 실제 도달 분기와 동일하게 targetType을
        // 비워 둔다.
        val pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null) ?: run {
            model.addAttribute("project", project)
            return "error/notfound"
        }

        codeReviewService.addReviewer(pullRequestId, user.id!!)

        return "redirect:/$owner/$projectName/pullRequest/${pullRequest.number}"
    }

    @PostMapping("/api/{owner}/{projectName}/pullRequest/{pullRequestId}/unreview")
    fun unreview(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable pullRequestId: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        // yona ReviewApp.java:55 @IsAllowed(value = Operation.ACCEPT, resourceType =
        // ResourceType.PULL_REQUEST) 대응 (P-템플릿 #47). review()와 동일한 근거.
        if (!checkWritePermission(project, user)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // yona IsAllowedAction.call()의 resourceObject == null 분기 대응 (P-템플릿 #45). review()와
        // 동일한 근거로 targetType을 비워 둔다.
        val pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null) ?: run {
            model.addAttribute("project", project)
            return "error/notfound"
        }

        codeReviewService.removeReviewer(pullRequestId, user.id!!)

        return "redirect:/$owner/$projectName/pullRequest/${pullRequest.number}"
    }
}
