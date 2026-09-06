package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.GitTag
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Date
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
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

// yona-wiki P3-10 — BranchViewControllerSpec과 정확히 같은 패턴의 태그 목록 화면 테스트.
class TagViewControllerSpec : DescribeSpec({
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

    val tagViewController = TagViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(tagViewController).build()

    beforeTest {
        clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository)
    }

    describe("TagViewController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PUBLIC)
        val mockCommit = mockk<Commit>()
        every { mockCommit.getCommitterDate() } returns Date(0)
        val lightweightTag = GitTag(name = "refs/tags/v1.0", targetCommit = mockCommit, annotated = false)
        val annotatedTag = GitTag(name = "refs/tags/v2.0", targetCommit = mockCommit, message = "릴리즈 메모", annotated = true)

        describe("GET /{owner}/{projectName}/tags") {
            it("성공 시 200 OK와 올바른 뷰 이름, 모델 속성을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.getTags() } returns listOf(lightweightTag, annotatedTag)

                mockMvc.perform(get("/owner/TestProject/tags"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/tags"))
                    .andExpect(model().attributeExists("project", "tags"))

                verify { playRepository.getTags() }
            }

            it("존재하지 않는 프로젝트면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(get("/owner/nosuch/tags"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("isCodeAccessibleMemberOnly가 true이고 비멤버·비그룹멤버면 403이어야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "owner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/owner/memberonly-project/tags"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (BranchViewControllerSpec과 동일한 근거)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 1L, name = "org")
                val groupUser = User(id = 10L, loginId = "groupuser", name = "그룹멤버")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 1L, user = groupUser, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 5L, owner = "owner", name = "group-project", vcs = "git", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("groupuser", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("groupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(5L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepository
                every { playRepository.getTags() } returns listOf(lightweightTag)

                mockMvc.perform(get("/owner/group-project/tags").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/tags"))
            }

            it("isCodeAccessibleMemberOnly가 아니어도 READ 권한이 없으면(비공개 프로젝트+비로그인) 403이어야 한다") {
                val privateProject = Project(id = 8L, owner = "owner", name = "private-project", vcs = "git", projectScope = ProjectScope.PRIVATE)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "private-project") } returns Optional.of(privateProject)

                mockMvc.perform(get("/owner/private-project/tags"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("git이 아닌 저장소면 error/400 뷰를 반환해야 한다") {
                val svnProject = Project(id = 9L, owner = "owner", name = "svn-project", vcs = "svn", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "svn-project") } returns Optional.of(svnProject)

                mockMvc.perform(get("/owner/svn-project/tags"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/400"))
            }

            it("vcs가 null이면 기본값 GIT으로 취급해 정상적으로 태그 목록을 반환해야 한다") {
                val noVcsProject = Project(id = 12L, owner = "owner", name = "no-vcs-project", vcs = null, projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "no-vcs-project") } returns Optional.of(noVcsProject)
                every { repositoryService.getRepository(noVcsProject) } returns playRepository
                every { playRepository.getTags() } returns emptyList()

                mockMvc.perform(get("/owner/no-vcs-project/tags"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/tags"))
            }

            // BranchViewControllerSpec "매니저 권한이 있으면 canUpdate/canDelete/showActionsColumn이
            // 모두 true여야 한다"와 동일한 근거(P3-10 계획 문서 "권한" 절 — 태그 삭제도 브랜치 삭제와
            // 동일한 Operation.DELETE 권한 체계를 따른다).
            it("매니저 권한이 있으면 canDelete가 true여야 한다") {
                val managerProject = Project(id = 11L, owner = "owner", name = "manager-project", vcs = "git", projectScope = ProjectScope.PUBLIC)
                val managerUser = User(id = 22L, loginId = "manageruser", name = "매니저")
                managerUser.projectUsers.add(ProjectUser(id = 200L, user = managerUser, project = managerProject, role = Role(id = RoleType.MANAGER.roleType)))
                val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "manager-project") } returns Optional.of(managerProject)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { repositoryService.getRepository(managerProject) } returns playRepository
                every { playRepository.getTags() } returns listOf(lightweightTag)

                mockMvc.perform(get("/owner/manager-project/tags").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("canDelete", true))
            }

            it("일반 멤버는 canDelete가 false여야 한다") {
                val memberProject = Project(id = 13L, owner = "owner", name = "member-project", vcs = "git", projectScope = ProjectScope.PUBLIC)
                val memberUser = User(id = 23L, loginId = "memberuser2", name = "멤버")
                memberUser.projectUsers.add(ProjectUser(id = 201L, user = memberUser, project = memberProject, role = Role(id = RoleType.MEMBER.roleType)))
                val memberAuth = UsernamePasswordAuthenticationToken("memberuser2", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "member-project") } returns Optional.of(memberProject)
                every { userRepository.findByLoginId("memberuser2") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(13L, 23L) } returns true
                every { repositoryService.getRepository(memberProject) } returns playRepository
                every { playRepository.getTags() } returns listOf(lightweightTag)

                mockMvc.perform(get("/owner/member-project/tags").principal(memberAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("canDelete", false))
            }
        }
    }
})
