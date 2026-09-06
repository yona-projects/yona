package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// yona-wiki P3-10 — BranchApiController.deleteBranch()와 정확히 같은 패턴의 태그 삭제 액션(웹
// UI의 code/tags.html "삭제" 버튼이 호출). 태그는 브랜치와 달리 웹 UI에 생성 폼을 두지 않는다
// (브랜치도 웹 UI로 직접 만들 수 없다 — PullRequestServiceImpl이 내부적으로만 createBranch()를
// 호출한다, GitHub도 태그 생성은 보통 `git push --tags`/API로 하지 릴리즈 없이 독립된 "새 태그"
// 버튼을 web UI에 두지 않는다 — 계획 문서의 "범위 확정" 절 참고). 생성은 REST API/CLI 전용이다.
@Controller
class TagApiController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl
) {

    @DeleteMapping("/{owner}/{projectName}/tags/{tag}")
    fun deleteTag(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable tag: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        if (project.vcs?.uppercase() != "GIT") {
            model.addAttribute("project", project)
            model.addAttribute("menuType", "code")
            return "error/badrequest"
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        // BranchApiController.deleteBranch()와 동일한 권한(Operation.DELETE, 매니저/조직관리자
        // 전용) — 계획 문서 "권한" 절 참고.
        if (!accessControl.isAllowed(loginUser, project, Operation.DELETE)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val repository = repositoryService.getRepository(project)
        val decodedTagName = URLDecoder.decode(tag.trimStart('/'), StandardCharsets.UTF_8.name())

        repository.deleteTag(decodedTagName)

        return "redirect:/$owner/$projectName/tags"
    }
}
