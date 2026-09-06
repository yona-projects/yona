package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.stereotype.Component

// yona-wiki P3-03 Step6 — GitAuthorizationFilter(HTTPS)가 쓰던 "로그인 사용자가 이 프로젝트에
// git 접근 가능한가"라는 순수 판정 로직을 SshAuthServiceImpl(SSH)도 그대로 재사용할 수 있도록
// 추출했다. 로직 자체는 GitAuthorizationFilter가 하던 것과 완전히 동일하다(리팩터링만, 동작
// 변화 없음 — GitAuthorizationFilterIntegrationSpec으로 회귀 여부를 재검증했다).
@Component
class GitAccessPolicy(
    private val projectService: ProjectService,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    fun findProject(owner: String, projectName: String): Project? =
        projectService.findByOwnerAndName(owner, projectName)

    // yona AccessControl READ 규칙(P1-45) 대응: PROTECTED도 PUBLIC과 동일하게 인증 없이 clone
    // 가능했던 것을 PRIVATE와 같이 인증을 요구하도록 수정.
    fun requiresAuth(project: Project, isWriteRequest: Boolean): Boolean {
        return project.projectScope != ProjectScope.PUBLIC ||
            project.isCodeAccessibleMemberOnly ||
            isWriteRequest
    }

    // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-64). 직접 멤버가 아니어도 프로젝트가
    // 속한 조직의 구성원이면(PUBLIC/PROTECTED에 한해) 접근을 허용한다.
    fun isMember(project: Project, loginId: String): Boolean {
        val projectId = project.id ?: return false
        if (projectService.isMember(projectId, loginId)) {
            return true
        }
        val user = userRepository.findByLoginId(loginId).orElse(null) ?: return false
        return accessControl.isAllowedIfGroupMember(project, user)
    }

    // yona의 "!user.isGuest" 대응.
    fun isGuestUser(loginId: String): Boolean {
        return userRepository.findByLoginId(loginId).map { it.isGuest }.orElse(false)
    }
}
