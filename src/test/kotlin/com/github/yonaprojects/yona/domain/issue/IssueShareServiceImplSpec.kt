package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.Optional

class IssueShareServiceImplSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueSharerRepository = mockk<IssueSharerRepository>()
    val issueRepository = mockk<IssueRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val notificationEventRecorder = mockk<NotificationEventRecorder>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val issueEventRepository = mockk<IssueEventRepository>()
    val meterRegistry = io.micrometer.core.instrument.simple.SimpleMeterRegistry()

    val service = IssueShareServiceImpl(
        userRepository,
        projectRepository,
        issueSharerRepository,
        issueRepository,
        projectUserRepository,
        organizationUserRepository,
        notificationEventRecorder,
        eventPublisher,
        issueEventRepository,
        meterRegistry
    )

    val project = Project(id = 1L, name = "TestProject", owner = "gildong", projectScope = ProjectScope.PRIVATE)
    val currentUser = User(id = 10L, loginId = "gildong", name = "홍길동")
    val sharerUser = User(id = 20L, loginId = "sharer1", name = "공유대상")
    val issue = Issue(id = 100L, title = "이슈", body = "본문", project = project, number = 1L)

    // changeSharer 계열 테스트에서 IssueEvent/NotificationEvent 기록 경로를 공통으로 스텁한다.
    fun stubEventRecording() {
        every { issueEventRepository.findFirstByIssueAndCreatedAfterOrderByIdDesc(any(), any()) } returns null
        every { issueEventRepository.save(any()) } answers { firstArg() }
        every { notificationEventRecorder.record(any(), any()) } answers { firstArg() }
    }

    beforeTest {
        clearMocks(
            userRepository, projectRepository, issueSharerRepository, issueRepository,
            projectUserRepository, organizationUserRepository, notificationEventRecorder,
            issueEventRepository, answers = false
        )
    }

    describe("IssueShareServiceImpl.findAssignableUsersOfProject") {
        it("검색어가 공백이면 '나에게 지정' 항목과 프로젝트 배정 가능 사용자 목록을 반환한다") {
            val member = User(id = 30L, loginId = "member1", name = "멤버1")
            val projectUser = ProjectUser(id = 1L, user = member, project = project, role = Role(id = 2L, name = "MEMBER"))
            every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)
            every { userRepository.findAllById(setOf(30L)) } returns listOf(member)

            val result = service.findAssignableUsersOfProject(project, "", currentUser)

            result shouldHaveSize 2
            result[0]["name"] shouldBe "나에게 지정"
            result[1]["loginId"] shouldBe "member1"
        }

        it("검색어가 있고 프로젝트가 PUBLIC이면 멤버 여부 확인 없이 검색 결과를 모두 포함한다") {
            val publicProject = Project(id = 2L, name = "PublicProject", owner = "gildong", projectScope = ProjectScope.PUBLIC)
            val found = User(id = 40L, loginId = "found1", name = "찾은사용자")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))

            val result = service.findAssignableUsersOfProject(publicProject, "찾", currentUser)

            result shouldHaveSize 1
            result[0]["loginId"] shouldBe "found1"
            verify(exactly = 0) { projectUserRepository.findByProjectIdAndUserId(any(), any()) }
        }

        it("검색어가 있고 PUBLIC이 아니며 프로젝트 멤버이면 포함한다") {
            val found = User(id = 40L, loginId = "found1", name = "찾은사용자")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))
            every { projectUserRepository.findByProjectIdAndUserId(1L, 40L) } returns
                Optional.of(ProjectUser(id = 1L, user = found, project = project, role = Role(id = 2L, name = "MEMBER")))

            val result = service.findAssignableUsersOfProject(project, "찾", currentUser)

            result shouldHaveSize 1
        }

        it("검색어가 있고 PUBLIC이 아니며 프로젝트/조직 멤버가 아니면 제외한다") {
            val found = User(id = 40L, loginId = "found1", name = "찾은사용자")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))
            every { projectUserRepository.findByProjectIdAndUserId(1L, 40L) } returns Optional.empty()

            val result = service.findAssignableUsersOfProject(project, "찾", currentUser)

            result.shouldBeEmpty()
        }

        it("검색 결과가 없으면 빈 목록을 반환한다") {
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(emptyList())

            val result = service.findAssignableUsersOfProject(project, "없음", currentUser)

            result.shouldBeEmpty()
        }
    }

    describe("IssueShareServiceImpl.isMemberOfProject 분기 (findAssignableUsersOfProject 경유)") {
        it("조직이 없고 프로젝트 멤버도 아니면 제외한다") {
            val found = User(id = 41L, loginId = "found2", name = "찾은사용자2")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))
            every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns Optional.empty()
            // project.organization == null

            val result = service.findAssignableUsersOfProject(project, "찾", currentUser)

            result.shouldBeEmpty()
        }

        it("프로젝트 멤버는 아니지만 조직 멤버이면 포함한다") {
            val org = Organization(id = 5L, name = "조직1")
            val orgProject = Project(id = 3L, name = "OrgProject", owner = "gildong", projectScope = ProjectScope.PRIVATE, organization = org)
            val found = User(id = 42L, loginId = "found3", name = "찾은사용자3")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))
            every { projectUserRepository.findByProjectIdAndUserId(3L, 42L) } returns Optional.empty()
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 42L) } returns
                Optional.of(OrganizationUser(id = 1L, user = found, organization = org, role = Role(id = 7L, name = "ORG_MEMBER")))

            val result = service.findAssignableUsersOfProject(orgProject, "찾", currentUser)

            result shouldHaveSize 1
        }

        it("프로젝트 멤버도, 조직 멤버도 아니면 제외한다") {
            val org = Organization(id = 5L, name = "조직1")
            val orgProject = Project(id = 3L, name = "OrgProject", owner = "gildong", projectScope = ProjectScope.PRIVATE, organization = org)
            val found = User(id = 43L, loginId = "found4", name = "찾은사용자4")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))
            every { projectUserRepository.findByProjectIdAndUserId(3L, 43L) } returns Optional.empty()
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 43L) } returns Optional.empty()

            val result = service.findAssignableUsersOfProject(orgProject, "찾", currentUser)

            result.shouldBeEmpty()
        }
    }

    describe("IssueShareServiceImpl.getAssignableUsersOfProjectInternal 분기 (blank query 경유)") {
        it("조직이 없고 사이트관리자도 아니며 프로젝트 멤버도 없으면 빈 목록을 반환한다(findAllById 호출 안 함)") {
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsersOfProject(project, "", currentUser)

            // "나에게 지정" 한 건만 존재해야 한다.
            result shouldHaveSize 1
            verify(exactly = 0) { userRepository.findAllById(any()) }
        }

        it("PRIVATE 프로젝트의 조직이 있으면 조직 관리자만 후보에 포함한다") {
            val org = Organization(id = 5L, name = "조직1")
            val orgProject = Project(id = 3L, name = "OrgProject", owner = "gildong", projectScope = ProjectScope.PRIVATE, organization = org)
            val orgAdmin = User(id = 50L, loginId = "admin1", name = "조직관리자")
            every { projectUserRepository.findByProjectId(3L) } returns emptyList()
            every { organizationUserRepository.findByOrganizationIdAndRoleId(5L, RoleType.ORG_ADMIN.roleType) } returns
                listOf(OrganizationUser(id = 2L, user = orgAdmin, organization = org, role = Role(id = 6L, name = "ORG_ADMIN")))
            every { userRepository.findAllById(setOf(50L)) } returns listOf(orgAdmin)

            val result = service.findAssignableUsersOfProject(orgProject, "", currentUser)

            result shouldHaveSize 2
            result[1]["loginId"] shouldBe "admin1"
            verify(exactly = 0) { organizationUserRepository.findByOrganizationId(any()) }
        }

        it("PUBLIC/PROTECTED 프로젝트의 조직이 있으면 조직 멤버 전체를 후보에 포함한다") {
            val org = Organization(id = 6L, name = "조직2")
            val orgProject = Project(id = 4L, name = "OrgProject2", owner = "gildong", projectScope = ProjectScope.PUBLIC, organization = org)
            val orgMember = User(id = 51L, loginId = "orgmember1", name = "조직원1")
            every { projectUserRepository.findByProjectId(4L) } returns emptyList()
            every { organizationUserRepository.findByOrganizationId(6L) } returns
                listOf(OrganizationUser(id = 3L, user = orgMember, organization = org, role = Role(id = 7L, name = "ORG_MEMBER")))
            every { userRepository.findAllById(setOf(51L)) } returns listOf(orgMember)

            val result = service.findAssignableUsersOfProject(orgProject, "", currentUser)

            result shouldHaveSize 2
            result[1]["loginId"] shouldBe "orgmember1"
            verify(exactly = 0) { organizationUserRepository.findByOrganizationIdAndRoleId(any(), any()) }
        }

        it("사이트관리자이면 조직/멤버가 없어도 스스로를 후보에 포함한다") {
            val siteManager = User(id = 60L, loginId = "siteadmin", name = "사이트관리자", state = UserState.SITE_ADMIN)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()
            every { userRepository.findAllById(setOf(60L)) } returns listOf(siteManager)

            val result = service.findAssignableUsersOfProject(project, "", siteManager)

            result shouldHaveSize 2
            result[1]["loginId"] shouldBe "siteadmin"
        }
    }

    describe("IssueShareServiceImpl.findAssignableUsers") {
        it("담당자가 있고 현재 사용자가 담당자 본인이면 '나에게 지정'을 추가하지 않는다(작성자 없음)") {
            val assigneeUser = User(id = 10L, loginId = "gildong", name = "홍길동")
            val assignee = Assignee(id = 1L, user = assigneeUser, project = project)
            val i = Issue(id = 101L, title = "이슈2", body = null, project = project, number = 2L, authorId = null, assignee = assignee)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsers(i, "", currentUser)

            // "나에게 지정"이 없으므로 담당자 해제 + 현재 담당자만 남는다.
            result.map { it["name"] } shouldContainExactly listOf("담당자 해제", "홍길동")
        }

        it("담당자가 있고 작성자가 현재 사용자와 동일하면 작성자 항목을 추가하지 않는다") {
            val assigneeUser = User(id = 20L, loginId = "sharer1", name = "공유대상")
            val assignee = Assignee(id = 1L, user = assigneeUser, project = project)
            val i = Issue(id = 102L, title = "이슈3", body = null, project = project, number = 3L, authorId = 10L, assignee = assignee)
            every { userRepository.findById(10L) } returns Optional.of(currentUser)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsers(i, "", currentUser)

            result.map { it["name"] } shouldContainExactly listOf("나에게 지정", "담당자 해제", "공유대상")
        }

        it("담당자가 있고 작성자가 담당자와 동일하면 작성자 항목을 추가하지 않는다") {
            val assigneeUser = User(id = 20L, loginId = "sharer1", name = "공유대상")
            val assignee = Assignee(id = 1L, user = assigneeUser, project = project)
            val i = Issue(id = 103L, title = "이슈4", body = null, project = project, number = 4L, authorId = 20L, assignee = assignee)
            every { userRepository.findById(20L) } returns Optional.of(assigneeUser)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsers(i, "", currentUser)

            result.map { it["name"] } shouldContainExactly listOf("나에게 지정", "담당자 해제", "공유대상")
        }

        it("담당자가 있고 작성자가 현재 사용자/담당자 모두와 다르면 작성자 지정 항목을 추가한다") {
            val assigneeUser = User(id = 20L, loginId = "sharer1", name = "공유대상")
            val authorUser = User(id = 70L, loginId = "author1", name = "작성자")
            val assignee = Assignee(id = 1L, user = assigneeUser, project = project)
            val i = Issue(id = 104L, title = "이슈5", body = null, project = project, number = 5L, authorId = 70L, assignee = assignee)
            every { userRepository.findById(70L) } returns Optional.of(authorUser)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsers(i, "", currentUser)

            result.map { it["name"] } shouldContainExactly listOf("나에게 지정", "작성자에게 지정", "담당자 해제", "공유대상")
        }

        it("담당자가 없고 작성자 정보가 없으면 '나에게 지정'만 추가한다(authorId=null)") {
            val i = Issue(id = 105L, title = "이슈6", body = null, project = project, number = 6L, authorId = null, assignee = null)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsers(i, "", currentUser)

            result.map { it["name"] } shouldContainExactly listOf("나에게 지정")
            verify(exactly = 0) { userRepository.findById(any()) }
        }

        it("담당자가 없고 authorId는 있지만 사용자를 찾지 못하면 작성자 항목을 추가하지 않는다") {
            val i = Issue(id = 106L, title = "이슈7", body = null, project = project, number = 7L, authorId = 999L, assignee = null)
            every { userRepository.findById(999L) } returns Optional.empty()
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsers(i, "", currentUser)

            result.map { it["name"] } shouldContainExactly listOf("나에게 지정")
        }

        it("담당자가 없고 작성자가 현재 사용자와 다르면 작성자 지정 항목을 추가한다") {
            val authorUser = User(id = 71L, loginId = "author2", name = "작성자2")
            val i = Issue(id = 107L, title = "이슈8", body = null, project = project, number = 8L, authorId = 71L, assignee = null)
            every { userRepository.findById(71L) } returns Optional.of(authorUser)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsers(i, "", currentUser)

            result.map { it["name"] } shouldContainExactly listOf("나에게 지정", "작성자에게 지정")
        }

        it("검색어가 있고 프로젝트가 PUBLIC이면 검색 결과를 모두 포함한다") {
            val publicProject = Project(id = 2L, name = "PublicProject", owner = "gildong", projectScope = ProjectScope.PUBLIC)
            val i = Issue(id = 108L, title = "이슈9", body = null, project = publicProject, number = 9L)
            val found = User(id = 80L, loginId = "found5", name = "찾은사용자5")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))

            val result = service.findAssignableUsers(i, "찾", currentUser)

            result shouldHaveSize 1
        }

        it("검색어가 있고 PUBLIC이 아니며 프로젝트 멤버이면 포함한다") {
            val i = Issue(id = 109L, title = "이슈10", body = null, project = project, number = 10L)
            val found = User(id = 81L, loginId = "found6", name = "찾은사용자6")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))
            every { projectUserRepository.findByProjectIdAndUserId(1L, 81L) } returns
                Optional.of(ProjectUser(id = 1L, user = found, project = project, role = Role(id = 2L, name = "MEMBER")))

            val result = service.findAssignableUsers(i, "찾", currentUser)

            result shouldHaveSize 1
        }

        it("검색어가 있고 PUBLIC이 아니며 멤버가 아니면 제외한다") {
            val i = Issue(id = 110L, title = "이슈11", body = null, project = project, number = 11L)
            val found = User(id = 82L, loginId = "found7", name = "찾은사용자7")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))
            every { projectUserRepository.findByProjectIdAndUserId(1L, 82L) } returns Optional.empty()

            val result = service.findAssignableUsers(i, "찾", currentUser)

            result.shouldBeEmpty()
        }

        it("담당자가 없고 작성자가 현재 사용자와 동일하면 작성자 지정 항목을 추가하지 않는다") {
            val i = Issue(id = 120L, title = "이슈20", body = null, project = project, number = 20L, authorId = 10L, assignee = null)
            every { userRepository.findById(10L) } returns Optional.of(currentUser)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()

            val result = service.findAssignableUsers(i, "", currentUser)

            result.map { it["name"] } shouldContainExactly listOf("나에게 지정")
        }

        it("사이트관리자가 조회하면 배정 가능 후보 목록 반복문이 자기 자신을 순회한다") {
            val siteManager = User(id = 65L, loginId = "siteadmin2", name = "사이트관리자2", state = UserState.SITE_ADMIN)
            val i = Issue(id = 121L, title = "이슈21", body = null, project = project, number = 21L, authorId = null, assignee = null)
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()
            every { userRepository.findAllById(setOf(65L)) } returns listOf(siteManager)

            val result = service.findAssignableUsers(i, "", siteManager)

            result shouldHaveSize 2
            result[1]["loginId"] shouldBe "siteadmin2"
        }

        it("검색어가 있고 검색 결과가 없으면 반복문을 순회하지 않고 빈 목록을 반환한다") {
            val i = Issue(id = 122L, title = "이슈22", body = null, project = project, number = 22L)
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(emptyList())

            val result = service.findAssignableUsers(i, "없음", currentUser)

            result.shouldBeEmpty()
        }
    }

    describe("IssueShareServiceImpl.findSharerByloginIds") {
        it("콤마로 분리한 로그인ID가 모두 공백이면 빈 목록을 반환한다") {
            val result = service.findSharerByloginIds(issue, " , ,")
            result.shouldBeEmpty()
        }

        it("일치하는 공유자가 있으면 생성일 순으로 정렬해 반환한다") {
            val sharer = IssueSharer(id = 1L, loginId = "sharer1", user = sharerUser, issue = issue)
            val i = Issue(id = 111L, title = "이슈12", body = null, project = project, number = 12L, sharers = mutableSetOf(sharer))

            val result = service.findSharerByloginIds(i, "sharer1,other")

            result shouldHaveSize 1
            result[0]["loginId"] shouldBe "sharer1"
        }

        it("일치하는 공유자가 없으면 빈 목록을 반환한다") {
            val sharer = IssueSharer(id = 1L, loginId = "sharer1", user = sharerUser, issue = issue)
            val i = Issue(id = 112L, title = "이슈13", body = null, project = project, number = 13L, sharers = mutableSetOf(sharer))

            val result = service.findSharerByloginIds(i, "notfound")

            result.shouldBeEmpty()
        }
    }

    describe("IssueShareServiceImpl.findSharableUsers") {
        it("검색어가 공백이면 빈 목록을 반환하고 어떤 저장소도 호출하지 않는다") {
            val result = service.findSharableUsers("", null)
            result.shouldBeEmpty()
            verify(exactly = 0) { userRepository.searchUsers(any(), any()) }
        }

        it("검색어가 있고 공개 프로젝트가 없으면 사용자만 결과에 포함한다") {
            val found = User(id = 90L, loginId = "found8", name = "찾은사용자8")
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(listOf(found))
            every { projectRepository.findPublicProjectIds() } returns emptyList()

            val result = service.findSharableUsers("찾", null)

            result shouldHaveSize 1
            result[0]["type"] shouldBe "user"
            verify(exactly = 0) { projectRepository.searchProjects(any(), any(), any()) }
        }

        it("사용자 검색 결과가 없고 공개 프로젝트도 없으면 완전히 빈 목록을 반환한다") {
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(emptyList())
            every { projectRepository.findPublicProjectIds() } returns emptyList()

            val result = service.findSharableUsers("없음", "user")

            result.shouldBeEmpty()
        }

        it("공개 프로젝트가 있으면 프로젝트도 결과에 포함한다") {
            val publicProject = Project(id = 9L, name = "PublicSearch", owner = "owner1", projectScope = ProjectScope.PUBLIC)
            every { userRepository.searchUsers(any(), any()) } returns PageImpl(emptyList())
            every { projectRepository.findPublicProjectIds() } returns listOf(9L)
            every { projectRepository.searchProjects(any(), any(), any()) } returns PageImpl(listOf(publicProject))

            val result = service.findSharableUsers("Public", "project")

            result shouldHaveSize 1
            result[0]["type"] shouldBe "project"
            result[0]["name"] shouldBe "owner1/PublicSearch"
        }
    }

    describe("IssueShareServiceImpl.changeSharer - project 타입") {
        it("프로젝트 공유 추가 시 소속 멤버 전원에게 공유자를 추가하고 IssueEvent를 기록한다 (P1-37)") {
            val member1 = User(id = 200L, loginId = "pmember1", name = "P멤버1")
            val member2 = User(id = 201L, loginId = "pmember2", name = "P멤버2")
            val pu1 = ProjectUser(id = 1L, user = member1, project = project, role = Role(id = 2L, name = "MEMBER"))
            val pu2 = ProjectUser(id = 2L, user = member2, project = project, role = Role(id = 2L, name = "MEMBER"))
            every { projectUserRepository.findByProjectId(1L) } returns listOf(pu1, pu2)
            every { issueSharerRepository.findByLoginIdAndIssueId(any(), 100L) } returns Optional.empty()
            every { issueSharerRepository.save(any()) } answers { firstArg() }
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { userRepository.findByLoginId("pmember1") } returns Optional.of(member1)
            every { userRepository.findByLoginId("pmember2") } returns Optional.of(member2)
            stubEventRecording()

            val captured = slot<IssueEvent>()
            every { issueEventRepository.save(capture(captured)) } answers { firstArg() }

            val result = service.changeSharer(issue, "1", "project", "add", currentUser)

            result["action"] shouldBe "added"
            result["sharer"] shouldBe "TestProject"
            captured.captured.eventType shouldBe EventType.ISSUE_SHARER_CHANGED
            verify(exactly = 1) { issueSharerRepository.save(match { it.loginId == "pmember1" }) }
            verify(exactly = 1) { issueSharerRepository.save(match { it.loginId == "pmember2" }) }
        }

        it("프로젝트 멤버가 없으면 반복문을 돌지 않고 프로젝트 이름만 응답에 담는다") {
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()
            every { projectRepository.findById(1L) } returns Optional.of(project)
            stubEventRecording()

            val result = service.changeSharer(issue, "1", "project", "add", currentUser)

            result["action"] shouldBe "added"
            result["sharer"] shouldBe "TestProject"
            verify(exactly = 0) { issueSharerRepository.save(any()) }
        }

        it("프로젝트 공유 해제 시 이미 존재하는 공유자를 제거한다") {
            val member1 = User(id = 200L, loginId = "pmember1", name = "P멤버1")
            val pu1 = ProjectUser(id = 1L, user = member1, project = project, role = Role(id = 2L, name = "MEMBER"))
            val existingSharer = IssueSharer(id = 5L, loginId = "pmember1", user = member1, issue = issue)
            every { projectUserRepository.findByProjectId(1L) } returns listOf(pu1)
            every { issueSharerRepository.findByLoginIdAndIssueId("pmember1", 100L) } returns Optional.of(existingSharer)
            every { issueSharerRepository.delete(existingSharer) } returns Unit
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { userRepository.findByLoginId("pmember1") } returns Optional.of(member1)
            stubEventRecording()

            val result = service.changeSharer(issue, "1", "project", "delete", currentUser)

            result["action"] shouldBe "deleted"
            verify(exactly = 1) { issueSharerRepository.delete(existingSharer) }
        }

        it("지원하지 않는 action이면 공유자 추가/삭제 없이 응답 action에 안내 문구를 담는다") {
            val member1 = User(id = 200L, loginId = "pmember1", name = "P멤버1")
            val pu1 = ProjectUser(id = 1L, user = member1, project = project, role = Role(id = 2L, name = "MEMBER"))
            every { projectUserRepository.findByProjectId(1L) } returns listOf(pu1)
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { userRepository.findByLoginId("pmember1") } returns Optional.of(member1)
            stubEventRecording()

            val result = service.changeSharer(issue, "1", "project", "noop", currentUser)

            result["action"] shouldBe "Do nothing. Unsupported action: noop"
            verify(exactly = 0) { issueSharerRepository.save(any()) }
            verify(exactly = 0) { issueSharerRepository.delete(any()) }
        }

        it("프로젝트 id가 숫자로 변환되지 않으면 IllegalArgumentException을 던진다") {
            shouldThrow<IllegalArgumentException> {
                service.changeSharer(issue, "not-a-number", "project", "add", currentUser)
            }
        }

        it("존재하지 않는 프로젝트면 IllegalArgumentException을 던진다") {
            every { projectUserRepository.findByProjectId(999L) } returns emptyList()
            every { projectRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.changeSharer(issue, "999", "project", "add", currentUser)
            }
        }

        it("공유 대상 사용자를 찾지 못하면 sendNotification에서 receiver 없이 알림을 기록한다(receiver=null)") {
            val member1 = User(id = 200L, loginId = "pmember1", name = "P멤버1")
            val pu1 = ProjectUser(id = 1L, user = member1, project = project, role = Role(id = 2L, name = "MEMBER"))
            every { projectUserRepository.findByProjectId(1L) } returns listOf(pu1)
            every { issueSharerRepository.findByLoginIdAndIssueId("pmember1", 100L) } returns Optional.empty()
            every { issueSharerRepository.save(any()) } answers { firstArg() }
            every { projectRepository.findById(1L) } returns Optional.of(project)
            // sendNotification 내부에서 다시 조회하는 시점엔 탈퇴 등으로 사용자를 찾지 못하는 상황을 재현한다.
            every { userRepository.findByLoginId("pmember1") } returns Optional.empty()
            stubEventRecording()

            val notifSlot = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(notifSlot), any()) } answers { firstArg() }

            service.changeSharer(issue, "1", "project", "add", currentUser)

            notifSlot.captured.receivers.shouldBeEmpty()
        }

        it("notificationEventRecorder.record가 null을 반환하면 이벤트를 발행하지 않는다") {
            val member1 = User(id = 200L, loginId = "pmember1", name = "P멤버1")
            val pu1 = ProjectUser(id = 1L, user = member1, project = project, role = Role(id = 2L, name = "MEMBER"))
            every { projectUserRepository.findByProjectId(1L) } returns listOf(pu1)
            every { issueSharerRepository.findByLoginIdAndIssueId("pmember1", 100L) } returns Optional.empty()
            every { issueSharerRepository.save(any()) } answers { firstArg() }
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { userRepository.findByLoginId("pmember1") } returns Optional.of(member1)
            stubEventRecording()
            every { notificationEventRecorder.record(any(), any()) } returns null

            service.changeSharer(issue, "1", "project", "add", currentUser)

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }
    }

    describe("IssueShareServiceImpl.changeSharer - user 타입") {
        it("공유자를 추가하면 IssueEvent 타임라인 항목(ISSUE_SHARER_CHANGED)이 생성되어야 한다") {
            every { issueSharerRepository.findByLoginIdAndIssueId("sharer1", 100L) } returns Optional.empty()
            every { issueSharerRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByLoginId("sharer1") } returns Optional.of(sharerUser)
            stubEventRecording()

            val captured = slot<IssueEvent>()
            every { issueEventRepository.save(capture(captured)) } answers { firstArg() }

            service.changeSharer(issue, "sharer1", "user", "add", currentUser)

            captured.captured.eventType shouldBe EventType.ISSUE_SHARER_CHANGED
            captured.captured.newValue shouldBe "sharer1"
            captured.captured.senderLoginId shouldBe "gildong"
        }

        it("이미 공유 중인 사용자를 다시 추가하면 새로 저장하지 않는다") {
            val existingSharer = IssueSharer(id = 9L, loginId = "sharer1", user = sharerUser, issue = issue)
            every { issueSharerRepository.findByLoginIdAndIssueId("sharer1", 100L) } returns Optional.of(existingSharer)
            every { userRepository.findByLoginId("sharer1") } returns Optional.of(sharerUser)
            stubEventRecording()

            service.changeSharer(issue, "sharer1", "user", "add", currentUser)

            verify(exactly = 0) { issueSharerRepository.save(any()) }
        }

        it("공유되어 있지 않은 사용자를 삭제하면 delete를 호출하지 않는다") {
            every { issueSharerRepository.findByLoginIdAndIssueId("sharer1", 100L) } returns Optional.empty()
            every { userRepository.findByLoginId("sharer1") } returns Optional.of(sharerUser)
            stubEventRecording()

            val result = service.changeSharer(issue, "sharer1", "user", "delete", currentUser)

            result["action"] shouldBe "deleted"
            verify(exactly = 0) { issueSharerRepository.delete(any()) }
        }

        it("공유 중인 사용자를 삭제하면 issueSharerRepository.delete를 호출한다") {
            val existingSharer = IssueSharer(id = 9L, loginId = "sharer1", user = sharerUser, issue = issue)
            every { issueSharerRepository.findByLoginIdAndIssueId("sharer1", 100L) } returns Optional.of(existingSharer)
            every { issueSharerRepository.delete(existingSharer) } returns Unit
            every { userRepository.findByLoginId("sharer1") } returns Optional.of(sharerUser)
            stubEventRecording()

            service.changeSharer(issue, "sharer1", "user", "delete", currentUser)

            verify(exactly = 1) { issueSharerRepository.delete(existingSharer) }
        }

        it("지원하지 않는 action이면 응답 action에 안내 문구를 담고 추가/삭제하지 않는다") {
            every { userRepository.findByLoginId("sharer1") } returns Optional.of(sharerUser)
            stubEventRecording()

            val result = service.changeSharer(issue, "sharer1", "user", "noop", currentUser)

            result["action"] shouldBe "Do nothing. Unsupported action: noop"
            verify(exactly = 0) { issueSharerRepository.save(any()) }
            verify(exactly = 0) { issueSharerRepository.delete(any()) }
        }

        it("대상 사용자를 찾지 못하면 IllegalArgumentException을 던진다") {
            every { userRepository.findByLoginId("nouser") } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.changeSharer(issue, "nouser", "user", "add", currentUser)
            }
        }
    }
})
