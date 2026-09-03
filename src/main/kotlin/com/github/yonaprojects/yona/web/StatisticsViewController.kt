package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class StatisticsViewController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val accessControl: AccessControl
) {

    @GetMapping("/{owner}/{projectName}/statistics")
    fun statistics(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        // yona StatisticsApp.java:30 @AnonymousCheck(기본값 requiresLogin=false) 대응 (P1-138).
        // 사이트 전역 로그인 강제 설정이 없는 한 익명 사용자를 무조건 막지 않는다 — 실제 접근 가능 여부는
        // 프로젝트 스코프(PUBLIC이면 익명도 허용)로만 판단한다. 프로젝트 스코프 확인 전에 무조건 403을
        // 반환하던 것을 제거.
        if (project.projectScope != ProjectScope.PUBLIC) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                model.addAttribute("project", project)
                return "error/forbidden"
            }
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        return "project/statistics"
    }
}
