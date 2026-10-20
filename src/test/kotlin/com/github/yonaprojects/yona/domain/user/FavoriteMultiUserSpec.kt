package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

// v1.6 FavoriteProject/FavoriteOrganization/FavoriteIssue는 (user, 대상) 쌍이 유일할 뿐 대상 하나를 여러 사용자가
// 즐겨찾기할 수 있다. next는 대상 쪽을 @OneToOne으로 매핑해 대상 FK에 UNIQUE가 생성되어 두 번째 사용자의
// 즐겨찾기가 제약 위반으로 실패한다.
class FavoriteMultiUserSpec @Autowired constructor(
    private val favoriteService: FavoriteService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository,
    private val issueRepository: IssueRepository,
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val favoriteOrganizationRepository: FavoriteOrganizationRepository,
    private val favoriteIssueRepository: FavoriteIssueRepository
) : AbstractIntegrationTest() {
    init {
        describe("여러 사용자가 같은 대상을 즐겨찾기") {
            afterEach {
                favoriteIssueRepository.deleteAll()
                favoriteProjectRepository.deleteAll()
                favoriteOrganizationRepository.deleteAll()
                issueRepository.deleteAll()
                projectRepository.deleteAll()
                organizationRepository.deleteAll()
                userRepository.deleteAll()
            }

            fun user(login: String) = userRepository.save(User(name = login, loginId = login, email = "$login@example.com", createdDate = Instant.now()))

            it("프로젝트를 두 사용자가 모두 즐겨찾기할 수 있어야 한다") {
                val a = user("fav-a"); val b = user("fav-b")
                val project = projectRepository.save(Project(name = "favproj", owner = "fav-a"))

                favoriteService.toggleFavoriteProject(a.id!!, project.id!!) shouldBe true
                favoriteService.toggleFavoriteProject(b.id!!, project.id!!) shouldBe true
                favoriteProjectRepository.count() shouldBe 2L
            }

            it("조직을 두 사용자가 모두 즐겨찾기할 수 있어야 한다") {
                val a = user("fav-c"); val b = user("fav-d")
                val org = organizationRepository.save(Organization(name = "favorg", created = Instant.now()))

                favoriteService.toggleFavoriteOrganization(a.id!!, org.id!!) shouldBe true
                favoriteService.toggleFavoriteOrganization(b.id!!, org.id!!) shouldBe true
                favoriteOrganizationRepository.count() shouldBe 2L
            }

            it("이슈를 두 사용자가 모두 즐겨찾기할 수 있어야 한다") {
                val a = user("fav-e"); val b = user("fav-f")
                val project = projectRepository.save(Project(name = "favproj2", owner = "fav-e"))
                val issue = issueRepository.save(Issue(title = "이슈", project = project, number = 1L))

                favoriteService.toggleFavoriteIssue(a.id!!, issue.id!!) shouldBe true
                favoriteService.toggleFavoriteIssue(b.id!!, issue.id!!) shouldBe true
                favoriteIssueRepository.count() shouldBe 2L
            }
        }
    }
}
