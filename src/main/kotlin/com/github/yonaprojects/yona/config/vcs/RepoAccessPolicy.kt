package com.github.yonaprojects.yona.config.vcs

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.stereotype.Component

// yona-wiki P3-03 Step6에서 GitAuthorizationFilter(HTTPS)가 쓰던 "로그인 사용자가 이 프로젝트에
// 저장소 접근이 가능한가"라는 순수 판정 로직을 SshAuthServiceImpl(SSH)도 재사용할 수 있도록
// GitAccessPolicy로 추출했었다. 2026-09-07 — SvnAuthorizationFilter가 이 로직을 그대로 복붙해두고
// 있던 것을 발견해(GitAuthorizationFilter/SvnAuthorizationFilter의 requiresAuth/isMember/
// isGuestUser가 완전히 동일한 코드였음) config/git 밖으로 옮기고 이름을 VCS 중립적으로 바꿔
// 세 곳(Git HTTP/SVN HTTP/SSH) 모두가 공유하도록 정리했다(순수 리팩터링, 동작 변화 없음 —
// GitAuthorizationFilterSpec/SvnAuthorizationFilterSpec/SvnAuthorizationFilterExtraSpec으로
// 회귀 여부 재검증). P3-12 2라운드에서 Mercurial용 `HgAuthorizationFilter`를 추가할 때도 이
// 클래스를 그대로 쓸 것을 염두에 두고 옮겼다.
@Component
class RepoAccessPolicy(
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
