package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.RecentIssue
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Optional

// 슬라이드 사이드바(<yona-usermenu>)에 표시할 데이터를 한 번에 조립한다. 구성은 기존
// common/usermenu_tab_content_list.html(즐겨찾기/프로젝트/최근 이슈 탭)이 쓰던 모델과 동일해야 한다.
class UserMenuServiceSpec : DescribeSpec({
    val favoriteProjectRepository = mockk<FavoriteProjectRepository>()
    val favoriteOrganizationRepository = mockk<FavoriteOrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val watchRepository = mockk<WatchRepository>()
    val recentIssueService = mockk<RecentIssueService>()

    val service = UserMenuService(
        favoriteProjectRepository, favoriteOrganizationRepository, organizationUserRepository,
        projectUserRepository, projectRepository, watchRepository, recentIssueService
    )

    val me = User(id = 1L, loginId = "me", name = "나", email = "me@example.com")
    val org = Organization(id = 20L, name = "acme")
    val otherOrg = Organization(id = 21L, name = "beta")

    val mine = Project(id = 10L, name = "mine", owner = "me", overview = "내 프로젝트")
    val inAcme = Project(id = 11L, name = "inacme", owner = "acme", organization = org)
    val inBeta = Project(id = 12L, name = "inbeta", owner = "beta", organization = otherOrg)
    val loose = Project(id = 13L, name = "loose", owner = "someone")
    val watched = Project(id = 14L, name = "watched", owner = "someone")

    fun stubEmpty() {
        every { favoriteProjectRepository.findByUserId(1L) } returns emptyList()
        every { favoriteOrganizationRepository.findByUserId(1L) } returns emptyList()
        every { organizationUserRepository.findByUserId(1L) } returns emptyList()
        every { projectUserRepository.findByUserId(1L) } returns emptyList()
        every { projectRepository.findByOwner("me") } returns emptyList()
        every { watchRepository.findByUserAndResourceType(me, ResourceType.PROJECT) } returns emptyList()
        every { recentIssueService.getRecentIssues(me) } returns emptyList()
    }

    describe("UserMenuService.load") {
        it("데이터가 전혀 없어도 모든 목록이 비어 있어야 한다") {
            stubEmpty()
            val menu = service.load(me)
            menu.loginId shouldBe "me"
            menu.personal.shouldBeEmpty()
            menu.favoriteOrganizations.shouldBeEmpty()
            menu.organizations.shouldBeEmpty()
            menu.favoriteProjects.shouldBeEmpty()
            menu.recentlyVisited.shouldBeEmpty()
            menu.createdByMe.shouldBeEmpty()
            menu.watching.shouldBeEmpty()
            menu.joinmember.shouldBeEmpty()
            menu.visitedIssues.shouldBeEmpty()
        }

        it("프로젝트는 실제 하이퍼링크로 쓸 수 있는 href(/owner/name, 존재하는 프로젝트 홈)와 즐겨찾기 여부를 가져야 한다") {
            stubEmpty()
            every { projectRepository.findByOwner("me") } returns listOf(mine)
            every { favoriteProjectRepository.findByUserId(1L) } returns listOf(FavoriteProject(user = me, project = mine))

            val project = service.load(me).createdByMe.single()
            project.id shouldBe 10L
            project.name shouldBe "mine"
            project.owner shouldBe "me"
            project.overview shouldBe "내 프로젝트"
            project.href shouldBe "/me/mine"
            project.favorite shouldBe true
        }

        it("개인 영역(personal)은 내가 만든 프로젝트와 같고, 즐겨찾기가 아닌 프로젝트는 favorite=false여야 한다") {
            stubEmpty()
            every { projectRepository.findByOwner("me") } returns listOf(mine)

            val menu = service.load(me)
            menu.personal.map { it.name } shouldBe listOf("mine")
            menu.personal.single().favorite shouldBe false
        }

        it("즐겨찾기 조직과 그 외 가입 조직을 나누고, 조직별 프로젝트를 담아야 한다") {
            stubEmpty()
            org.projects = mutableListOf(inAcme)
            otherOrg.projects = mutableListOf(inBeta)
            every { favoriteOrganizationRepository.findByUserId(1L) } returns listOf(FavoriteOrganization(user = me, organization = org))
            every { organizationUserRepository.findByUserId(1L) } returns listOf(
                mockk<OrganizationUser> { every { organization } returns org },
                mockk<OrganizationUser> { every { organization } returns otherOrg }
            )

            val menu = service.load(me)
            menu.favoriteOrganizations.map { it.name } shouldBe listOf("acme")
            menu.favoriteOrganizations.single().favorite shouldBe true
            menu.favoriteOrganizations.single().projects.map { it.name } shouldBe listOf("inacme")
            menu.organizations.map { it.name } shouldBe listOf("beta")
            menu.organizations.single().favorite shouldBe false
        }

        it("조직이 없고 내 소유도 아닌 즐겨찾기 프로젝트만 favoriteProjects에 담아야 한다") {
            stubEmpty()
            every { favoriteProjectRepository.findByUserId(1L) } returns listOf(
                FavoriteProject(user = me, project = loose),
                FavoriteProject(user = me, project = inAcme),
                FavoriteProject(user = me, project = mine)
            )

            service.load(me).favoriteProjects.map { it.name } shouldBe listOf("loose")
        }

        it("참여 프로젝트는 최근 생성순 10개(recentlyVisited)와 내가 소유하지 않은 것(joinmember)으로 나눠야 한다") {
            stubEmpty()
            val projects = (1..12).map {
                Project(id = 100L + it, name = "p$it", owner = "someone", createdDate = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(it.toLong()))
            } + mine
            every { projectUserRepository.findByUserId(1L) } returns projects.map { p -> mockk<ProjectUser> { every { project } returns p } }

            val menu = service.load(me)
            menu.recentlyVisited.size shouldBe 10
            menu.recentlyVisited.first().name shouldBe "p12"
            menu.joinmember.size shouldBe 12
            menu.joinmember.none { it.owner == "me" } shouldBe true
        }

        it("지켜보는 프로젝트는 존재하는 숫자 ID만 조회해 담고, 잘못된 ID와 없는 프로젝트는 건너뛰어야 한다") {
            stubEmpty()
            every { watchRepository.findByUserAndResourceType(me, ResourceType.PROJECT) } returns listOf(
                Watch(user = me, resourceType = ResourceType.PROJECT, resourceId = "14"),
                Watch(user = me, resourceType = ResourceType.PROJECT, resourceId = "abc"),
                Watch(user = me, resourceType = ResourceType.PROJECT, resourceId = "999")
            )
            every { projectRepository.findById(14L) } returns Optional.of(watched)
            every { projectRepository.findById(999L) } returns Optional.empty()

            service.load(me).watching.map { it.name } shouldBe listOf("watched")
        }

        it("최근 방문 이슈는 제목과 이동 주소만 담아야 한다") {
            stubEmpty()
            every { recentIssueService.getRecentIssues(me) } returns listOf(
                RecentIssue(userId = 1L, title = "버그 수정", url = "/me/mine/issue/3")
            )

            val issue = service.load(me).visitedIssues.single()
            issue.title shouldBe "버그 수정"
            issue.href shouldBe "/me/mine/issue/3"
        }
    }
})
