package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
class FavoriteServiceSpec @Autowired constructor(
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
        describe("FavoriteService 통합 테스트") {
            lateinit var user: User
            lateinit var project: Project
            lateinit var organization: Organization
            lateinit var issue: Issue

            beforeEach {
                favoriteProjectRepository.deleteAll()
                favoriteOrganizationRepository.deleteAll()
                favoriteIssueRepository.deleteAll()
                issueRepository.deleteAll()
                projectRepository.deleteAll()
                organizationRepository.deleteAll()
                userRepository.deleteAll()

                user = userRepository.save(User(loginId = "tester", name = "tester", email = "test@yona.io"))
                organization = organizationRepository.save(Organization(name = "test-org", descr = "desc"))
                project = projectRepository.save(Project(name = "test-project", owner = "tester", organization = organization))
                issue = issueRepository.save(Issue(title = "test-issue", body = "body", project = project, number = 1L))
            }

            describe("Project 즐겨찾기") {
                it("추가 및 해제") {
                    favoriteService.toggleFavoriteProject(user.id!!, project.id!!) shouldBe true
                    favoriteService.getFavoriteProjects(user.id!!).size shouldBe 1

                    favoriteService.toggleFavoriteProject(user.id!!, project.id!!) shouldBe false
                    favoriteService.getFavoriteProjects(user.id!!).size shouldBe 0
                }

                it("사용자/프로젝트 없음 예외") {
                    shouldThrow<IllegalArgumentException> { favoriteService.toggleFavoriteProject(999L, project.id!!) }
                    shouldThrow<IllegalArgumentException> { favoriteService.toggleFavoriteProject(user.id!!, 999L) }
                }
            }

            describe("Organization 즐겨찾기") {
                it("추가 및 해제") {
                    favoriteService.toggleFavoriteOrganization(user.id!!, organization.id!!) shouldBe true
                    favoriteService.getFavoriteOrganizations(user.id!!).size shouldBe 1

                    favoriteService.toggleFavoriteOrganization(user.id!!, organization.id!!) shouldBe false
                    favoriteService.getFavoriteOrganizations(user.id!!).size shouldBe 0
                }

                it("사용자/조직 없음 예외") {
                    shouldThrow<IllegalArgumentException> { favoriteService.toggleFavoriteOrganization(999L, organization.id!!) }
                    shouldThrow<IllegalArgumentException> { favoriteService.toggleFavoriteOrganization(user.id!!, 999L) }
                }
            }

            describe("Issue 즐겨찾기") {
                it("추가 및 해제") {
                    favoriteService.toggleFavoriteIssue(user.id!!, issue.id!!) shouldBe true
                    favoriteService.getFavoriteIssues(user.id!!).size shouldBe 1

                    favoriteService.toggleFavoriteIssue(user.id!!, issue.id!!) shouldBe false
                    favoriteService.getFavoriteIssues(user.id!!).size shouldBe 0
                }

                it("사용자/이슈 없음 예외") {
                    shouldThrow<IllegalArgumentException> { favoriteService.toggleFavoriteIssue(999L, issue.id!!) }
                    shouldThrow<IllegalArgumentException> { favoriteService.toggleFavoriteIssue(user.id!!, 999L) }
                }
            }
        }
    }
}
