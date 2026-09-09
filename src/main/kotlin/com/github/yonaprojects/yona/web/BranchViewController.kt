package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class BranchViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl,
    private val pullRequestRepository: PullRequestRepository
) {

    @GetMapping("/{owner}/{projectName}/branches")
    fun branches(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                // yona CodeAccessCheckAction의 forbidden(ErrorViews.Forbidden.render(
                // "error.forbidden.or.notfound", context.request().path())) 대응 — 이 (String,String)
                // 오버로드는 project를 받지 않고
                // forbidden_default.render(messageKey)(제네릭, 헤더/메뉴 없음)로 귀결된다(비로그인이면
                // 로그인 화면으로 보내지만 그 부분은 이 P-템플릿 작업 범위 밖). 즉 project가 이미
                // resolve됐어도 legacy 자체가 프로젝트 컨텍스트를 보여주지 않으므로, 신규 컨텍스트 인지형
                // error/forbidden으로 과잉 변환하지 않고 제네릭 error/403을 유지한 채 messageKey만
                // legacy와 동일하게 맞춘다.
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            // legacy BranchApp.branches()에는 이 일반 READ 체크에 대응하는 어노테이션/액션이 없다
            // (클래스 레벨 @IsOnlyGitAvailable, 메서드 레벨 @With(CodeAccessCheckAction.class)뿐 —
            // CodeAccessCheckAction은 멤버 전용 케이스만 다룬다). 대응하는 legacy 렌더링이 없어
            // 추측으로 컨텍스트를 만들어 붙이지 않고 기존 제네릭 error/403 그대로 둔다.
            return "error/403"
        }

        val vcsType = project.vcs?.uppercase() ?: "GIT"
        if (vcsType != "GIT") {
            // yona IsOnlyGitAvailableAction의
            // badRequest(ErrorViews.BadRequest.render("error.badrequest.only.available.for.git"))
            // 대응 — BadRequest의 (String) 1-arg 오버로드는
            // badrequest_default.render(messageKey)(제네릭)로 귀결된다(컨텍스트 인지형
            // badrequest.render(messageKey, project, menuType)은 2-arg/3-arg Project 오버로드 전용).
            // 기존 코드는 이 경우를 error/403(403)으로 잘못 매핑하고 있었다 — legacy는 400이므로
            // error/400으로 바로잡되, project 헤더가 없는 legacy 실제 동작 그대로 제네릭 유지.
            model.addAttribute("messageKey", "error.badrequest.only.available.for.git")
            return "error/400"
        }

        val repository = repositoryService.getRepository(project)
        val allBranches = repository.getBranches()
        val headBranch = repository.getHeadBranch()

        val filteredBranches = if (headBranch != null) {
            allBranches.filter { it.name != headBranch.name }
        } else {
            allBranches
        }

        // yona GitRepository.setTheLatestPullRequest() 대응 — 브랜치별로 이 프로젝트로
        // 보낸 가장 최근 PR을 찾아 "보낸 코드" 컬럼에 링크로 보여준다.
        val pullRequestsByBranch = (filteredBranches + listOfNotNull(headBranch)).associate { branch ->
            branch.shortName to pullRequestRepository.findFirstByFromProjectAndFromBranchAndToProjectOrderByNumberDesc(
                project, branch.shortName, project
            )
        }

        // yona code/branches.scala.html 대응 — DELETE 또는 UPDATE 권한이 있을 때만 액션 컬럼(빈 th 포함) 자체를 렌더링한다.
        val showActionsColumn = accessControl.isAllowed(loginUser, project, Operation.DELETE) ||
            accessControl.isAllowed(loginUser, project, Operation.UPDATE)
        val canUpdate = accessControl.isAllowed(loginUser, project, Operation.UPDATE)
        val canDelete = accessControl.isAllowed(loginUser, project, Operation.DELETE)

        model.addAttribute("project", project)
        model.addAttribute("allBranches", filteredBranches)
        model.addAttribute("headBranch", headBranch)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("pullRequestsByBranch", pullRequestsByBranch)
        model.addAttribute("showActionsColumn", showActionsColumn)
        model.addAttribute("canUpdate", canUpdate)
        model.addAttribute("canDelete", canDelete)

        return "code/branches"
    }
}
