package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
class ReviewViewController(
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val codeReviewService: CodeReviewService,
    private val accessControl: AccessControl
) {

    @PostMapping("/{owner}/{projectName}/pullRequest/{pullRequestId}/comments")
    fun newPullRequestComment(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable pullRequestId: Long,
        @RequestParam(required = false) commitId: String?,
        @RequestParam contents: String,
        @RequestParam(value = "thread.id", required = false) threadId: Long?,
        codeRangeReq: CodeRangeRequest,
        authentication: Authentication?,
        model: Model
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        // yona PullRequestApp.java:591 @IsCreatable(ResourceType.REVIEW_COMMENT) 대응 (P0-24).
        // 권한 체크가 전혀 없어 프로젝트 멤버십/READ 권한과 무관하게 로그인한 임의 사용자가
        // 비공개 프로젝트의 PR에도 리뷰 댓글을 달 수 있던 취약점.
        // yona IsCreatableAction.call()의 forbidden(ErrorViews.Forbidden.render("error.forbidden",
        // project)) 대응 (P-템플릿 #47) — 프로젝트는 이미 찾았으므로 컨텍스트 인지형 403.
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.REVIEW_COMMENT)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // yona PullRequestApp.java:610 notFound(notfound.render("error.notfound", project,
        // request().path())) 대응 (P-템플릿 #45) — request().path()를 targetType으로 넘기는 것은
        // "issue_post"/"board_post"/"milestone"/"code" 중 어느 것과도 매치될 수 없어 항상
        // case _(제네릭 문구/뒤로가기)로 빠지는 사실상의 legacy 버그다. 이를 그대로 재현해
        // error.notfound./some/literal/path 같은 미번역 원문을 찍는 대신, 실제로 도달하는
        // default 분기와 동일하게 targetType을 비워 둔다.
        val pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null) ?: run {
            model.addAttribute("project", project)
            return "error/notfound"
        }

        val codeRange = codeRangeReq.toCodeRange()

        val comment = codeReviewService.createReviewComment(
            project = project,
            pullRequest = pullRequest,
            commitId = commitId,
            contents = contents,
            codeRange = codeRange,
            threadId = threadId,
            currentUser = user
        )

        return if (commitId != null) {
            "redirect:/$owner/$projectName/pullRequest/${pullRequest.number}/commit/$commitId#comment-${comment.id}"
        } else {
            "redirect:/$owner/$projectName/pullRequest/${pullRequest.number}/changes#comment-${comment.id}"
        }
    }

    @PostMapping("/{owner}/{projectName}/commit/{commitId}/comments")
    fun newCommitComment(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String,
        @RequestParam contents: String,
        @RequestParam(value = "thread.id", required = false) threadId: Long?,
        codeRangeReq: CodeRangeRequest,
        authentication: Authentication?,
        model: Model
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        // yona CodeHistoryApp.java:189 @IsCreatable(ResourceType.COMMIT_COMMENT) 대응 (P0-24,
        // newPullRequestComment와 같은 파일에서 함께 발견). SVN/Git 분기와 무관하게 커밋 댓글
        // 생성 자체는 COMMIT_COMMENT 권한으로 게이트된다(CodeHistoryController.createComment의
        // JSON API 경로는 이미 이 체크가 있음 — 이 화면(폼 제출) 경로만 빠져 있었음).
        // yona IsCreatableAction.call()의 forbidden(ErrorViews.Forbidden.render("error.forbidden",
        // project)) 대응 (P-템플릿 #47) — 프로젝트는 이미 찾았으므로 컨텍스트 인지형 403.
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.COMMIT_COMMENT)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
        val commentId = if (isSvn) {
            val sideVal = codeRangeReq.startSide?.let { CodeRange.Side.valueOf(it.uppercase()) }
            val comment = codeReviewService.createCommitComment(
                project = project,
                commitId = commitId,
                contents = contents,
                path = codeRangeReq.path,
                line = codeRangeReq.startLine,
                side = sideVal,
                currentUser = user
            )
            comment.id
        } else {
            val codeRange = codeRangeReq.toCodeRange()
            val comment = codeReviewService.createReviewComment(
                project = project,
                pullRequest = null,
                commitId = commitId,
                contents = contents,
                codeRange = codeRange,
                threadId = threadId,
                currentUser = user
            )
            comment.id
        }

        return "redirect:/$owner/$projectName/commit/$commitId#comment-$commentId"
    }

    @DeleteMapping("/{owner}/{projectName}/commit/{commitId}/comments/{id}/delete")
    fun deleteCommitCommentRedirect(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String,
        @PathVariable id: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
        try {
            if (isSvn) {
                codeReviewService.deleteCommitComment(id, user)
            } else {
                codeReviewService.deleteReviewComment(id, user)
            }
        } catch (e: IllegalArgumentException) {
            if (e.message == "Permission denied") {
                // yona CodeHistoryApp.java:251 @IsAllowed(value = Operation.DELETE, resourceType =
                // ResourceType.COMMIT_COMMENT) 대응 (P-템플릿 #47) — 프로젝트는 이미 찾았으므로
                // 컨텍스트 인지형 403.
                model.addAttribute("project", project)
                return "error/forbidden"
            }
            throw e
        }
        return "redirect:/$owner/$projectName/commit/$commitId"
    }
}
