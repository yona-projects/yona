package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.GitBranch
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.project.ProjectUser
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class BranchViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )
    val pullRequestRepository = mockk<PullRequestRepository>()
    every {
        pullRequestRepository.findFirstByFromProjectAndFromBranchAndToProjectOrderByNumberDesc(any(), any(), any())
    } returns null

    val branchViewController = BranchViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        accessControl,
        pullRequestRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(branchViewController).build()

    beforeTest {
        clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository)
    }

    describe("BranchViewController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PUBLIC)
        val mockCommit = mockk<Commit>()
        val headBranch = GitBranch(name = "refs/heads/master", headCommit = mockCommit)
        val otherBranch = GitBranch(name = "refs/heads/feature-a", headCommit = mockCommit)

        describe("GET /{owner}/{projectName}/branches") {
            it("성공 시 200 OK와 올바른 뷰 이름, 모델 속성을 반환해야 한다 (HEAD 브랜치 제외 확인)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.getBranches() } returns listOf(headBranch, otherBranch)
                every { playRepository.getHeadBranch() } returns headBranch

                mockMvc.perform(
                    get("/owner/TestProject/branches")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
                    .andExpect(model().attributeExists("project", "allBranches", "headBranch"))
                    .andExpect(model().attribute("allBranches", listOf(otherBranch))) // HEAD인 refs/heads/master 가 필터링되었는지 검증

                verify { playRepository.getBranches() }
                verify { playRepository.getHeadBranch() }
            }

            it("[Test-12-4] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 브랜치 조회를 403 Forbidden 차단해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "owner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(
                    get("/owner/memberonly-project/branches")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 1L, name = "org")
                val groupUser = User(id = 10L, loginId = "groupuser", name = "그룹멤버")
                groupOrg.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = groupUser, organization = groupOrg,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 5L, owner = "owner", name = "group-project", vcs = "git", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("groupuser", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("groupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(5L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepository
                every { playRepository.getBranches() } returns listOf(headBranch, otherBranch)
                every { playRepository.getHeadBranch() } returns headBranch

                mockMvc.perform(get("/owner/group-project/branches").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
            }

            it("존재하지 않는 프로젝트면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(get("/owner/nosuch/branches"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("isCodeAccessibleMemberOnly가 true이고 로그인 사용자이지만 멤버도 그룹멤버도 아니면 403 Forbidden이어야 한다") {
                val memberOnlyProject = Project(id = 6L, owner = "owner", name = "memberonly-loggedin", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "git")
                val outsider = User(id = 20L, loginId = "outsider", name = "외부인")
                val outsiderAuth = UsernamePasswordAuthenticationToken("outsider", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-loggedin") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsider)
                every { projectUserRepository.existsByProjectIdAndUserId(6L, 20L) } returns false

                mockMvc.perform(get("/owner/memberonly-loggedin/branches").principal(outsiderAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            // isCodeAccessibleMemberOnly=true일 때 line 38의 `isAllowedIfGroupMember` 호출 자체가
            // true를 반환하는 경로 — 위쪽 "직접 멤버가 아니어도... 200 OK" 테스트는 이 옵션이 꺼진 채로
            // 50번째 줄의 별도 isAllowed() 경로를 타므로 이 분기와는 다르다.
            it("isCodeAccessibleMemberOnly가 true이고 직접 멤버는 아니어도 조직 멤버면 200 OK를 반환해야 한다") {
                val groupOrg2 = Organization(id = 2L, name = "org2")
                val groupUser2 = User(id = 22L, loginId = "groupuser2", name = "그룹멤버2")
                groupOrg2.organizationUsers.add(
                    OrganizationUser(
                        id = 2L, user = groupUser2, organization = groupOrg2,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val memberOnlyGroupProject = Project(
                    id = 108L, owner = "owner", name = "memberonly-group", vcs = "git",
                    projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, organization = groupOrg2
                )
                val groupAuth2 = UsernamePasswordAuthenticationToken("groupuser2", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-group") } returns Optional.of(memberOnlyGroupProject)
                every { userRepository.findByLoginId("groupuser2") } returns Optional.of(groupUser2)
                every { projectUserRepository.existsByProjectIdAndUserId(108L, 22L) } returns false
                every { repositoryService.getRepository(memberOnlyGroupProject) } returns playRepository
                every { playRepository.getBranches() } returns listOf(otherBranch)
                every { playRepository.getHeadBranch() } returns null

                mockMvc.perform(get("/owner/memberonly-group/branches").principal(groupAuth2))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
            }

            it("isCodeAccessibleMemberOnly가 true여도 프로젝트 직접 멤버면 200 OK를 반환해야 한다") {
                val memberOnlyProject = Project(id = 7L, owner = "owner", name = "memberonly-member", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "git")
                val memberUser = User(id = 21L, loginId = "memberuser", name = "멤버")
                val memberAuth = UsernamePasswordAuthenticationToken("memberuser", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-member") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(7L, 21L) } returns true
                every { repositoryService.getRepository(memberOnlyProject) } returns playRepository
                every { playRepository.getBranches() } returns listOf(otherBranch)
                every { playRepository.getHeadBranch() } returns null

                mockMvc.perform(get("/owner/memberonly-member/branches").principal(memberAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
            }

            it("isCodeAccessibleMemberOnly가 아니어도 READ 권한이 없으면(비공개 프로젝트+비로그인) 403 Forbidden이어야 한다") {
                val privateProject = Project(id = 8L, owner = "owner", name = "private-project", vcs = "git", projectScope = ProjectScope.PRIVATE)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "private-project") } returns Optional.of(privateProject)

                mockMvc.perform(get("/owner/private-project/branches"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            // vcs가 null이면 project.vcs?.uppercase() ?: "GIT" 엘비스가 기본값 GIT으로 처리해
            // git 저장소로 취급되어야 한다(400이 아니어야 한다).
            it("vcs가 null이면 기본값 GIT으로 취급해 정상적으로 브랜치 목록을 반환해야 한다") {
                val noVcsProject = Project(id = 12L, owner = "owner", name = "no-vcs-project", vcs = null, projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "no-vcs-project") } returns Optional.of(noVcsProject)
                every { repositoryService.getRepository(noVcsProject) } returns playRepository
                every { playRepository.getBranches() } returns listOf(otherBranch)
                every { playRepository.getHeadBranch() } returns null

                mockMvc.perform(get("/owner/no-vcs-project/branches"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
            }

            it("git이 아닌 저장소면 error/400 뷰를 반환해야 한다") {
                val svnProject = Project(id = 9L, owner = "owner", name = "svn-project", vcs = "svn", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "svn-project") } returns Optional.of(svnProject)

                mockMvc.perform(get("/owner/svn-project/branches"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/400"))
            }

            it("HEAD 브랜치가 없으면 브랜치 목록을 필터링하지 않고 그대로 반환해야 한다") {
                val noHeadProject = Project(id = 10L, owner = "owner", name = "no-head-project", vcs = "git", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "no-head-project") } returns Optional.of(noHeadProject)
                every { repositoryService.getRepository(noHeadProject) } returns playRepository
                every { playRepository.getBranches() } returns listOf(otherBranch)
                every { playRepository.getHeadBranch() } returns null

                mockMvc.perform(get("/owner/no-head-project/branches"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
                    .andExpect(model().attribute("allBranches", listOf(otherBranch)))
                    .andExpect(model().attribute("headBranch", null as Any?))
            }

            // yona code/branches.scala.html:59-62 대응 — 매니저는 UPDATE/DELETE 권한이 모두 있어
            // showActionsColumn/canUpdate/canDelete가 전부 true여야 한다(다른 성공 케이스는 전부
            // 익명/비매니저라 false였음).
            it("매니저 권한이 있으면 canUpdate/canDelete/showActionsColumn이 모두 true여야 한다") {
                val managerProject = Project(id = 11L, owner = "owner", name = "manager-project", vcs = "git", projectScope = ProjectScope.PUBLIC)
                val managerUser = User(id = 22L, loginId = "manageruser", name = "매니저")
                val managerRole = Role(id = RoleType.MANAGER.roleType)
                managerUser.projectUsers.add(ProjectUser(id = 200L, user = managerUser, project = managerProject, role = managerRole))
                val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "manager-project") } returns Optional.of(managerProject)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { repositoryService.getRepository(managerProject) } returns playRepository
                every { playRepository.getBranches() } returns listOf(otherBranch)
                every { playRepository.getHeadBranch() } returns null

                mockMvc.perform(get("/owner/manager-project/branches").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("showActionsColumn", true))
                    .andExpect(model().attribute("canUpdate", true))
                    .andExpect(model().attribute("canDelete", true))
            }
        }
    }
})
