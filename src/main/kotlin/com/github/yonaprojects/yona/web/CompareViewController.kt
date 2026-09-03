package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class CompareViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val commentThreadRepository: CommentThreadRepository,
    private val accessControl: AccessControl
) {

    @GetMapping("/{owner}/{projectName}/compare/{revA:.+}..{revB:.+}")
    fun compare(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable revA: String,
        @PathVariable revB: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        // 권한 검증. yona CompareApp.compare() @IsAllowed(Operation.READ) (resourceType 기본값
        // PROJECT) -> IsAllowedAction의 forbidden 분기 ErrorViews.Forbidden.render("error.forbidden",
        // project) 대응 (P-템플릿 #47) — Forbidden.render(messageKey, project) 2-arg 오버로드는
        // NotFound와 달리 project를 그대로 렌더링에 쓰는 컨텍스트 인지형 forbidden.scala.html로
        // 직결된다(utils/ErrorViews.java 확인).
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                model.addAttribute("project", project)
                return "error/forbidden"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val repository = repositoryService.getRepository(project)
        val commitA = repository.getCommit(revA)
        val commitB = repository.getCommit(revB)

        // yona CompareApp.compare()의 "if (commitA == null || commitB == null) { return notFound(
        // ErrorViews.NotFound.render("error.notfound.commit", project)); }" 대응. 여기서 legacy가 쓰는
        // NotFound.render(messageKey, project) 2-arg 오버로드는(3-arg render(messageKey, project, type)과
        // 달리) 실제로는 render(messageKey, project, MenuType.PROJECT_HOME)로 위임되고, 그 오버로드는
        // project/menuType을 완전히 무시한 채 notfound_default.render(messageKey)만 그린다(utils/
        // ErrorViews.java 확인) — 즉 legacy 자체가 이 지점에서 프로젝트 컨텍스트 없는 제네릭 404를
        // 그린다. error/notfound(컨텍스트 인지형, targetType 필요)로 바꾸면 legacy에 없던 프로젝트
        // 메뉴/헤더가 새로 생기므로 과잉이식이다 — 제네릭 error/404를 그대로 둔다.
        if (commitA == null || commitB == null) {
            return "error/404"
        }

        val commentThreads = commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, revB)

        model.addAttribute("project", project)
        model.addAttribute("commitA", commitA)
        model.addAttribute("commitB", commitB)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("commentThreads", commentThreads)

        val vcsType = project.vcs?.uppercase() ?: "GIT"
        return if (vcsType == "SUBVERSION" || vcsType == "SVN") {
            // yona CompareApp.compare()의 patch==null 분기도 ErrorViews.NotFound.render("error.notfound",
            // project) 2-arg -> notfound_default(제네릭, project 무시) 대응 — 위 commitA/commitB와 동일한
            // 이유로 제네릭 error/404 유지.
            val patch = repository.getPatch(revA, revB) ?: return "error/404"
            model.addAttribute("patch", patch)
            "code/compare_svn"
        } else {
            // yona CompareApp.compare()의 diffs==null 분기도 마찬가지로 제네릭 error/404 유지.
            val diffs = repository.getDiff(revA, revB) ?: return "error/404"
            model.addAttribute("diffs", diffs)
            "code/compare"
        }
    }
}
