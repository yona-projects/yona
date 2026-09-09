package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

// BranchViewController와 정확히 같은 패턴의 git 태그 목록 화면
// (`/{owner}/{projectName}/tags`). yona에는 legacy 대응 화면이 없다(완전 신규 기능) —
// BranchViewController.branches()의 접근 제어/에러 처리 구조를 그대로 재사용한다.
@Controller
class TagViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl
) {

    @GetMapping("/{owner}/{projectName}/tags")
    fun tags(
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
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val vcsType = project.vcs?.uppercase() ?: "GIT"
        if (vcsType != "GIT") {
            model.addAttribute("messageKey", "error.badrequest.only.available.for.git")
            return "error/400"
        }

        val repository = repositoryService.getRepository(project)
        val tags = repository.getTags().sortedByDescending { it.targetCommit.getCommitterDate() }

        // 태그 생성/삭제 모두 브랜치 삭제와 동일한 권한 체계(Operation.DELETE, PROJECT 리소스 —
        // AccessControl.isAllowed()에서 매니저/조직관리자 전용)를 따른다(BranchApiController.
        // deleteBranch()와 동일한 근거).
        val canDelete = accessControl.isAllowed(loginUser, project, Operation.DELETE)

        model.addAttribute("project", project)
        model.addAttribute("tags", tags)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("canDelete", canDelete)

        return "code/tags"
    }
}
