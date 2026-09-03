package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType

class CompareViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
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

    val compareViewController = CompareViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        commentThreadRepository,
        accessControl
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(compareViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository, commentThreadRepository)
    }

    describe("CompareViewController 템플릿 연동 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        val publicProject = Project(id = 1L, owner = "testowner", name = "public-project", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
        val privateProject = Project(id = 2L, owner = "testowner", name = "private-project", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
        val svnProject = Project(id = 3L, owner = "testowner", name = "svn-project", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")

        val commitA = mockk<Commit>()
        val commitB = mockk<Commit>()

        every { commitA.getId() } returns "aaaaaaa"
        every { commitB.getId() } returns "bbbbbbb"

        describe("GET /{owner}/{projectName}/compare/{revA}..{revB}") {
            it("프로젝트가 존재하지 않으면 404 응답을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "nonexistent") } returns Optional.empty()

                mockMvc.perform(get("/testowner/nonexistent/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("비공개 프로젝트일 때 프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "private-project") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 10L) } returns false

                mockMvc.perform(get("/testowner/private-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    // yona CompareApp.compare() @IsAllowed(READ) -> IsAllowedAction forbidden 분기
                    // ErrorViews.Forbidden.render("error.forbidden", project) 대응 (P-템플릿 #47).
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val org = Organization(id = 1L, name = "org")
                org.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = user, organization = org,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 6L, owner = "testowner", name = "group-project", projectScope = ProjectScope.PROTECTED, vcs = "GIT", organization = org)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(6L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getDiff("aaaaaaa", "bbbbbbb") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(groupProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/group-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare"))
            }

            it("[Test-12-1-1] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비로그인 익명 유저가 접근 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/compare/aaaaaaa..bbbbbbb"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            it("[Test-12-1-2] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 프로젝트 비멤버가 로그인 상태로 접근 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns false

                mockMvc.perform(get("/testowner/memberonly-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            it("[Test-12-1-3] 공개 프로젝트이며 isCodeAccessibleMemberOnly가 true이고 프로젝트 멤버가 접근 시 정상 200 OK를 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns true
                every { repositoryService.getRepository(memberOnlyProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getDiff("aaaaaaa", "bbbbbbb") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(memberOnlyProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/memberonly-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare"))
            }

            // isCodeAccessibleMemberOnly=true일 때 line 46의 isAllowedIfGroupMember 호출 자체가
            // true를 반환하는 경로 — 위쪽 "직접 멤버가 아니어도... 200 OK" 테스트는 이 옵션이 꺼진 채
            // else 분기(line 50의 별도 isAllowed())를 타므로 이 분기와는 다르다.
            it("isCodeAccessibleMemberOnly가 true이고 직접 멤버는 아니어도 조직 멤버면 200 OK를 반환해야 한다") {
                val groupOrg2 = Organization(id = 2L, name = "org2")
                groupOrg2.organizationUsers.add(
                    OrganizationUser(
                        id = 2L, user = user, organization = groupOrg2,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val memberOnlyGroupProject = Project(
                    id = 8L, owner = "testowner", name = "memberonly-group", vcs = "GIT",
                    projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, organization = groupOrg2
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-group") } returns Optional.of(memberOnlyGroupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(8L, 10L) } returns false
                every { repositoryService.getRepository(memberOnlyGroupProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getDiff("aaaaaaa", "bbbbbbb") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(memberOnlyGroupProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/memberonly-group/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare"))
            }

            it("공개 프로젝트이며 Git 저장소일 때 200 OK와 code/compare 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "public-project") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(publicProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getDiff("aaaaaaa", "bbbbbbb") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(publicProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/public-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare"))
                    .andExpect(model().attributeExists("project", "commitA", "commitB", "diffs"))
            }

            it("공개 프로젝트이며 SVN 저장소일 때 200 OK와 code/compare_svn 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-project") } returns Optional.of(svnProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(svnProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getPatch("aaaaaaa", "bbbbbbb") } returns "svn-patch-diff-content"
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(svnProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/svn-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare_svn"))
                    .andExpect(model().attributeExists("project", "commitA", "commitB", "patch"))
            }

            it("커밋이 존재하지 않는 리비전일 경우 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "public-project") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(publicProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns null
                every { playRepository.getCommit("bbbbbbb") } returns commitB

                mockMvc.perform(get("/testowner/public-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            // commitA는 있지만 commitB가 없는 경우 — `commitA == null || commitB == null`의 우변만
            // 위 테스트와 별도로 태운다.
            it("revB에 해당하는 커밋이 존재하지 않으면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "public-project") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(publicProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns null

                mockMvc.perform(get("/testowner/public-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            // vcsType == "SVN"(대문자 리터럴, "SUBVERSION"과는 다른 두 번째 OR 피연산자)도 SVN
            // 뷰로 처리돼야 한다.
            it("vcs 값이 정확히 SVN이어도 code/compare_svn 뷰를 반환해야 한다") {
                val svnLiteralProject = Project(id = 7L, owner = "testowner", name = "svn-literal-project", projectScope = ProjectScope.PUBLIC, vcs = "SVN")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-literal-project") } returns Optional.of(svnLiteralProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(svnLiteralProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getPatch("aaaaaaa", "bbbbbbb") } returns "svn-patch-diff-content"
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(svnLiteralProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/svn-literal-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare_svn"))
            }

            // repository.getPatch(revA, revB)/getDiff(revA, revB)는 PlayRepository 인터페이스상
            // 반환 타입이 각각 String/List<Any>(둘 다 non-null)이라, 컨트롤러의
            // `getPatch(...) ?: return "error/404"`/`getDiff(...) ?: return "error/404"` 엘비스는
            // 구조적으로 도달 불가능한 방어 코드다(mockk로 null을 반환시키려 해도 non-null 타입
            // 시그니처라 컴파일 자체가 되지 않음 — 실제로 시도해 확인함).

            // vcs가 null이면 project.vcs?.uppercase() ?: "GIT" 엘비스가 기본값 GIT으로 처리해
            // code/compare(GIT) 뷰로 처리돼야 한다.
            it("vcs가 null이면 기본값 GIT으로 취급해 code/compare 뷰를 반환해야 한다") {
                val noVcsProject = Project(id = 8L, owner = "testowner", name = "no-vcs-project", projectScope = ProjectScope.PUBLIC, vcs = null)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "no-vcs-project") } returns Optional.of(noVcsProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(noVcsProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getDiff("aaaaaaa", "bbbbbbb") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(noVcsProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/no-vcs-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare"))
            }
        }
    }
})
