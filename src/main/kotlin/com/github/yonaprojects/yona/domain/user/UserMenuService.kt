package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MenuProject(
    val id: Long,
    val name: String,
    val owner: String,
    val overview: String?,
    // 사이드바 항목이 실제 하이퍼링크(<a href>)가 되도록 서버가 이동 주소(프로젝트 홈)를 확정해 내려준다.
    // 옛 부분 뷰가 쓰던 /{owner}/{project}/go는 이 앱에 매핑이 없어 404라서 쓰지 않는다.
    val href: String,
    val favorite: Boolean
)

data class MenuOrganization(val id: Long, val name: String, val favorite: Boolean, val projects: List<MenuProject>)

data class MenuIssue(val title: String, val href: String)

data class UserMenu(
    val loginId: String,
    // 즐겨찾기 탭: 내가 만든 프로젝트(가상 개인 조직) → 즐겨찾기 조직 → 그 외 가입 조직 → 조직 없는 즐겨찾기 프로젝트
    val personal: List<MenuProject>,
    val favoriteOrganizations: List<MenuOrganization>,
    val organizations: List<MenuOrganization>,
    val favoriteProjects: List<MenuProject>,
    // 프로젝트 탭의 하위 탭 4개
    val recentlyVisited: List<MenuProject>,
    val createdByMe: List<MenuProject>,
    val watching: List<MenuProject>,
    val joinmember: List<MenuProject>,
    // 최근 이슈 탭
    val visitedIssues: List<MenuIssue>
)

// 슬라이드 사이드바 데이터 조립. 기존 UserViewController.usermenuTabContentList가 Thymeleaf 모델로 채우던 것과
// 같은 구성을 JSON 응답용 값 객체로 만든다(그 컨트롤러는 2단 보기와 기존 테스트가 쓰므로 건드리지 않는다).
@Service
@Transactional(readOnly = true)
class UserMenuService(
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val favoriteOrganizationRepository: FavoriteOrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val projectRepository: ProjectRepository,
    private val watchRepository: WatchRepository,
    private val recentIssueService: RecentIssueService
) {
    fun load(user: User): UserMenu {
        val userId = user.id!!
        val loginId = user.loginId!!

        val favoriteProjects = favoriteProjectRepository.findByUserId(userId).map { it.project }
        val favoriteProjectIds = favoriteProjects.mapNotNull { it.id }.toSet()
        val favoriteOrganizations = favoriteOrganizationRepository.findByUserId(userId).map { it.organization }
        val favoriteOrganizationIds = favoriteOrganizations.mapNotNull { it.id }.toSet()
        val organizations = organizationUserRepository.findByUserId(userId).map { it.organization }
        val allUserProjects = projectUserRepository.findByUserId(userId).map { it.project }
        val createdByMe = projectRepository.findByOwner(loginId)
        // 항목마다 findById를 부르면 지켜보는 프로젝트 수만큼 쿼리가 나가므로 ID를 모아 한 번에 조회하고, 감시한 순서를 유지한다.
        val watchedIds = watchRepository.findByUserAndResourceType(user, ResourceType.PROJECT)
            .mapNotNull { it.resourceId.toLongOrNull() }
            .distinct()
        val watching = if (watchedIds.isEmpty()) {
            emptyList()
        } else {
            val byId = projectRepository.findAllById(watchedIds).associateBy { it.id }
            watchedIds.mapNotNull { byId[it] }
        }

        fun project(p: Project) = MenuProject(
            id = p.id!!, name = p.name, owner = p.owner.orEmpty(), overview = p.overview,
            href = "/${p.owner.orEmpty()}/${p.name}", favorite = p.id in favoriteProjectIds
        )

        fun organization(o: Organization, favorite: Boolean) =
            MenuOrganization(o.id!!, o.name, favorite, o.projects.map(::project))

        return UserMenu(
            loginId = loginId,
            personal = createdByMe.map(::project),
            favoriteOrganizations = favoriteOrganizations.map { organization(it, true) },
            organizations = organizations.filter { it.id !in favoriteOrganizationIds }.map { organization(it, false) },
            favoriteProjects = favoriteProjects.filter { it.organization == null && it.owner != loginId }.map(::project),
            recentlyVisited = allUserProjects.sortedByDescending { it.createdDate }.take(10).map(::project),
            createdByMe = createdByMe.map(::project),
            watching = watching.map(::project),
            joinmember = allUserProjects.filter { it.owner != loginId }.map(::project),
            visitedIssues = recentIssueService.getRecentIssues(user).map { MenuIssue(it.title, it.url) }
        )
    }
}
