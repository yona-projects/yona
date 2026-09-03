package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Controller
class BranchApiController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl
) {

    @PostMapping("/{owner}/{projectName}/code/{branch}/setAsDefault")
    fun setAsDefault(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        // yona BranchApp.java:47 @IsOnlyGitAvailable(클래스 레벨, IsOnlyGitAvailableAction.isGit())
        // 대응 (P2-29). Git이 아닌 프로젝트(SVN 등)에 브랜치 조작을 요청하면 400으로 명확히
        // 거부해야 한다 — 이전에는 이 가드가 없어 SVN 프로젝트에도 그대로 진행되다 실질적으로
        // 아무 일도 하지 않으면서(no-op) 302 리다이렉트로 성공 신호를 돌려주는 오탐이 있었다.
        // yona error/badrequest.scala.html 대응 (P-템플릿 #50) — 프로젝트는 이미 찾았으므로
        // 컨텍스트 인지형 400(프로젝트 코드 메뉴 활성화).
        if (project.vcs?.uppercase() != "GIT") {
            model.addAttribute("project", project)
            model.addAttribute("menuType", "code")
            return "error/badrequest"
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            // yona error/forbidden.scala.html 대응 (P-템플릿 #47).
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val repository = repositoryService.getRepository(project)
        val decodedBranchName = URLDecoder.decode(branch.trimStart('/'), StandardCharsets.UTF_8.name())

        repository.setDefaultBranch(decodedBranchName)

        return "redirect:/$owner/$projectName/branches"
    }

    @DeleteMapping("/{owner}/{projectName}/code/{branch}")
    fun deleteBranch(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        // yona BranchApp.java:47 @IsOnlyGitAvailable(클래스 레벨, IsOnlyGitAvailableAction.isGit())
        // 대응 (P2-29). Git이 아닌 프로젝트(SVN 등)에 브랜치 조작을 요청하면 400으로 명확히
        // 거부해야 한다 — 이전에는 이 가드가 없어 SVN 프로젝트에도 그대로 진행되다 실질적으로
        // 아무 일도 하지 않으면서(no-op) 302 리다이렉트로 성공 신호를 돌려주는 오탐이 있었다.
        // yona error/badrequest.scala.html 대응 (P-템플릿 #50).
        if (project.vcs?.uppercase() != "GIT") {
            model.addAttribute("project", project)
            model.addAttribute("menuType", "code")
            return "error/badrequest"
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        // yona BranchApp.java:71-72 @IsAllowed(Operation.DELETE)(resourceType 기본값 PROJECT) 대응
        // (P1-97) — PROJECT 리소스의 DELETE는 매니저/조직관리자 전용이다(isGlobalResourceAllowed()의
        // PROJECT 케이스, ProjectUser.isManager || OrganizationUser.isAdmin) — 일반 멤버 전원 허용이
        // 아니다. 기존엔 단순 멤버십만 확인해 일반 멤버도 브랜치를 삭제할 수 있던 과잉 허용 버그였다.
        // yona error/forbidden.scala.html 대응 (P-템플릿 #47).
        if (!accessControl.isAllowed(loginUser, project, Operation.DELETE)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val repository = repositoryService.getRepository(project)
        val decodedBranchName = URLDecoder.decode(branch.trimStart('/'), StandardCharsets.UTF_8.name())

        repository.deleteBranch(decodedBranchName)

        return "redirect:/$owner/$projectName/branches"
    }
}
