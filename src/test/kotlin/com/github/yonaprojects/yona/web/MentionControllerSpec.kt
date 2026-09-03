package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.issue.IssueSharer
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment

class MentionControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val userRepository = mockk<UserRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val watchRepository = mockk<WatchRepository>()
    val repositoryService = mockk<RepositoryService>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
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

    val mentionController = MentionController(
        projectRepository,
        projectUserRepository,
        organizationUserRepository,
        issueRepository,
        userRepository,
        issueCommentRepository,
        postingRepository,
        postingCommentRepository,
        pullRequestRepository,
        watchRepository,
        repositoryService,
        commentThreadRepository,
        reviewCommentRepository,
        commitCommentRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(mentionController).build()

    beforeTest {
        clearMocks(
            projectRepository, projectUserRepository, organizationUserRepository, issueRepository, userRepository,
            issueCommentRepository, postingRepository, postingCommentRepository, pullRequestRepository, watchRepository,
            repositoryService, commentThreadRepository, reviewCommentRepository, commitCommentRepository
        )
        // query가 비어있는(=멤버 후보 수집) 분기에서 P1-42가 추가한 후보 소스들은 기본적으로 비어있다고 스텁한다.
        every { issueRepository.findByProject(any()) } returns emptyList()
        every { postingRepository.findByProject(any()) } returns emptyList()
        every { pullRequestRepository.findByToProject(any<Project>()) } returns emptyList()
        every { watchRepository.findByResourceTypeAndResourceId(any(), any()) } returns emptyList()
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    }

    describe("GET /api/{owner}/{projectName}/mentionList (P1-14)") {
        val me = User(id = 1L, loginId = "me", name = "나")
        val meAuth = UsernamePasswordAuthenticationToken("me", "password")
        val memberRole = Role(id = RoleType.MEMBER.roleType)

        it("비공개 프로젝트는 멤버가 아니면 403을 반환해야 한다") {
            val project = Project(id = 10L, name = "priv", owner = "owner", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns false

            mockMvc.perform(
                get("/api/owner/priv/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            ).andExpect(status().isForbidden)
        }

        it("mentionType=user: query가 없으면 프로젝트 멤버+조직 그룹멤버를 후보로 삼고 나를 맨 뒤에 배치해야 한다") {
            val org = Organization(id = 100L, name = "org1")
            val project = Project(id = 11L, name = "p", owner = "owner", projectScope = ProjectScope.PRIVATE, organization = org)
            val other = User(id = 2L, loginId = "other", name = "다른사람")
            val groupMember = User(id = 3L, loginId = "groupie", name = "그룹멤버")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "p") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(11L, 1L) } returns true
            me.projectUsers.add(ProjectUser(id = 9001L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(11L) } returns listOf(
                ProjectUser(id = 900L, user = me, project = project, role = memberRole),
                ProjectUser(id = 901L, user = other, project = project, role = memberRole)
            )
            every { organizationUserRepository.findByOrganizationId(100L) } returns listOf(
                OrganizationUser(id = 950L, user = groupMember, organization = org, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/p/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 후보 3명 + "@project all:"/"@group all:" 특수 항목 2개(P1-42) = 5
                .andExpect(jsonPath("$.result.length()").value(5))
                .andExpect(jsonPath("$.result[0].loginid").value("other"))
                .andExpect(jsonPath("$.result[1].loginid").value("groupie"))
                .andExpect(jsonPath("$.result[2].loginid").value("me"))
                .andExpect(jsonPath("$.result[3].name").value("@project all:"))
                .andExpect(jsonPath("$.result[4].name").value("@group all: "))
        }

        // yona ProjectApp.collectedUsersToMentionList() 대응 (P1-58). name/searchText가 user.name이
        // 아니라 요청자(currentUser)의 언어 설정에 따른 getDisplayName()을 써야 한다.
        it("mentionType=user: name/searchText는 user.name이 아니라 요청자 언어에 맞는 getDisplayName()을 써야 한다") {
            val meEnglishSpeaker = User(id = 1L, loginId = "me", name = "나", lang = "en")
            val meEnglishSpeakerAuth = UsernamePasswordAuthenticationToken("me", "password")
            val project = Project(id = 12L, name = "p2", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val bilingual = User(
                id = 4L, loginId = "bilingual", name = "홍길동[개발팀]",
                englishName = "Gildong Hong", lang = "ko"
            )

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "p2") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(meEnglishSpeaker)
            every { projectUserRepository.existsByProjectIdAndUserId(12L, 1L) } returns true
            meEnglishSpeaker.projectUsers.add(ProjectUser(id = 9002L, user = meEnglishSpeaker, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(12L) } returns listOf(
                ProjectUser(id = 902L, user = bilingual, project = project, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/p2/mentionList")
                    .param("mentionType", "user")
                    .principal(meEnglishSpeakerAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("bilingual"))
                .andExpect(jsonPath("$.result[0].name").value("Gildong Hong [개발팀]"))
                .andExpect(jsonPath("$.result[0].searchText").value("홍길동[개발팀]Gildong Hong [개발팀]bilingual"))
        }

        it("mentionType=user: 공개 프로젝트에서 query가 있으면 전역 사용자 검색 결과를 써야 한다") {
            val project = Project(id = 12L, name = "pub", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val searched = User(id = 4L, loginId = "found", name = "검색됨")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pub") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { userRepository.searchUsers("sea", PageRequest.of(0, 20)) } returns PageImpl(listOf(searched))

            mockMvc.perform(
                get("/api/owner/pub/mentionList")
                    .param("mentionType", "user")
                    .param("query", "sea")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 검색 결과 1명 + 나 + "@project all:"(조직 없음, P1-42) = 3
                .andExpect(jsonPath("$.result.length()").value(3))
                .andExpect(jsonPath("$.result[0].loginid").value("found"))
                .andExpect(jsonPath("$.result[1].loginid").value("me"))
                .andExpect(jsonPath("$.result[2].name").value("@project all:"))
        }

        it("admin 로그인ID는 후보에서 제외해야 한다") {
            val project = Project(id = 13L, name = "p2", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val admin = User(id = 5L, loginId = "admin", name = "관리자")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "p2") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(13L, 1L) } returns true
            me.projectUsers.add(ProjectUser(id = 9003L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(13L) } returns listOf(
                ProjectUser(id = 902L, user = admin, project = project, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/p2/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // admin 제외 후 나만 남고 + "@project all:"(P1-42) = 2
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
                .andExpect(jsonPath("$.result[1].name").value("@project all:"))
        }

        it("number/resourceType이 주어지면 이슈 댓글 작성자를 최근 순으로, 이슈 작성자를 마지막에 포함해야 한다(P1-42)") {
            val project = Project(id = 20L, name = "p5", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val author = User(id = 6L, loginId = "author1", name = "작성자")
            val commenterA = User(id = 7L, loginId = "commenterA", name = "댓글A")
            val commenterB = User(id = 8L, loginId = "commenterB", name = "댓글B")
            val issue = Issue(id = 600L, title = "이슈", project = project, number = 3L, authorLoginId = "author1")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "p5") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(20L, 1L) } returns true
            me.projectUsers.add(ProjectUser(id = 9004L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(20L) } returns emptyList()
            every { issueRepository.findByProjectAndNumber(project, 3L) } returns issue
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(600L) } returns listOf(
                IssueComment(authorLoginId = "commenterA", issue = issue),
                IssueComment(authorLoginId = "commenterB", issue = issue)
            )
            every { userRepository.findByLoginId("commenterA") } returns Optional.of(commenterA)
            every { userRepository.findByLoginId("commenterB") } returns Optional.of(commenterB)
            every { userRepository.findByLoginId("author1") } returns Optional.of(author)

            mockMvc.perform(
                get("/api/owner/p5/mentionList")
                    .param("mentionType", "user")
                    .param("number", "3")
                    .param("resourceType", "ISSUE_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 최근 댓글자(B)가 먼저, 그다음 A, 그다음 작성자, 나, "@project all:"
                .andExpect(jsonPath("$.result[0].loginid").value("commenterB"))
                .andExpect(jsonPath("$.result[1].loginid").value("commenterA"))
                .andExpect(jsonPath("$.result[2].loginid").value("author1"))
                .andExpect(jsonPath("$.result[3].loginid").value("me"))
        }

        it("프로젝트의 이슈/게시글/PR 작성자와 프로젝트 워처를 후보에 포함해야 한다(P1-42)") {
            val project = Project(id = 21L, name = "p6", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val issueAuthor = User(id = 9L, loginId = "issueAuthor", name = "이슈작성자")
            val watcher = User(id = 11L, loginId = "watcher1", name = "워처")
            val issue = Issue(id = 700L, title = "이슈", project = project, authorId = 9L)
            val watch = Watch(
                user = watcher, resourceType = ResourceType.PROJECT, resourceId = "21"
            )

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "p6") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(21L, 1L) } returns true
            me.projectUsers.add(ProjectUser(id = 9005L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(21L) } returns emptyList()
            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { watchRepository.findByResourceTypeAndResourceId(ResourceType.PROJECT, "21") } returns listOf(watch)
            every { userRepository.findById(9L) } returns Optional.of(issueAuthor)
            every { userRepository.findById(11L) } returns Optional.of(watcher)

            mockMvc.perform(
                get("/api/owner/p6/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("issueAuthor"))
                .andExpect(jsonPath("$.result[1].loginid").value("watcher1"))
        }

        it("이슈 공유자를 후보에 포함해야 한다(P1-42)") {
            val project = Project(id = 22L, name = "p7", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val sharer = User(id = 12L, loginId = "sharer1", name = "공유대상")
            val issue = Issue(id = 800L, title = "이슈", project = project, number = 4L)
            val issueSharer = IssueSharer(loginId = "sharer1", user = sharer, issue = issue)
            issue.sharers.add(issueSharer)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "p7") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(22L, 1L) } returns true
            me.projectUsers.add(ProjectUser(id = 9006L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(22L) } returns emptyList()
            every { issueRepository.findByProjectAndNumber(project, 4L) } returns issue
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(800L) } returns emptyList()

            mockMvc.perform(
                get("/api/owner/p7/mentionList")
                    .param("mentionType", "user")
                    .param("number", "4")
                    .param("resourceType", "ISSUE_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("sharer1"))
        }

        it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "none") } returns Optional.empty()

            mockMvc.perform(
                get("/api/owner/none/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            ).andExpect(status().isNotFound)
        }

        it("비로그인 사용자가 공개 프로젝트에서 query로 검색하면 loginUser 없이 처리해야 한다") {
            val project = Project(id = 40L, name = "anon1", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val someone = User(id = 101L, loginId = "someone", name = "누군가")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "anon1") } returns Optional.of(project)
            every { userRepository.searchUsers("hello", PageRequest.of(0, 20)) } returns PageImpl(listOf(someone))

            mockMvc.perform(
                get("/api/owner/anon1/mentionList")
                    .param("mentionType", "user")
                    .param("query", "hello")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("someone"))
                .andExpect(jsonPath("$.result[0].name").value("누군가"))
        }

        it("공개 프로젝트라도 query가 비어있으면 검색이 아니라 후보 수집 경로를 타야 한다") {
            val project = Project(id = 41L, name = "pub2", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val member = User(id = 102L, loginId = "pubmember", name = "공개멤버")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pub2") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.findByProjectId(41L) } returns listOf(
                ProjectUser(id = 9100L, user = member, project = project, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/pub2/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("pubmember"))
        }

        it("비공개 프로젝트는 query가 있어도 검색이 아니라 후보 수집 경로를 타야 한다") {
            val project = Project(id = 42L, name = "priv2", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val member = User(id = 103L, loginId = "privmember", name = "비공개멤버")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv2") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9101L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(42L) } returns listOf(
                ProjectUser(id = 9102L, user = member, project = project, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/priv2/mentionList")
                    .param("mentionType", "user")
                    .param("query", "priv")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("privmember"))
        }

        it("loginId가 빈 사용자는 멘션 후보에서 제외해야 한다") {
            val project = Project(id = 43L, name = "blankid", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val blankIdUser = User(id = 104L, loginId = "", name = "빈아이디")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "blankid") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9103L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(43L) } returns listOf(
                ProjectUser(id = 9104L, user = blankIdUser, project = project, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/blankid/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 빈 loginId 사용자 제외 후 나만 남고 + "@project all:" = 2
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("작성자 조회 결과의 id가 없으면 후보에 추가하지 않아야 한다") {
            val project = Project(id = 56L, name = "noidauthor", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val issue = Issue(id = 902L, title = "이슈", project = project, number = 13L, authorLoginId = "ghostAuthor")
            val ghostAuthor = User(loginId = "ghostAuthor", name = "고스트")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "noidauthor") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9117L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(56L) } returns emptyList()
            every { issueRepository.findByProjectAndNumber(project, 13L) } returns issue
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(902L) } returns emptyList()
            every { userRepository.findByLoginId("ghostAuthor") } returns Optional.of(ghostAuthor)

            mockMvc.perform(
                get("/api/owner/noidauthor/mentionList")
                    .param("mentionType", "user")
                    .param("number", "13")
                    .param("resourceType", "ISSUE_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // id 없는 작성자는 candidates 맵에 못 들어가므로 나만 남는다
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("number만 있고 resourceType이 없으면 댓글 작성자 수집을 건너뛰어야 한다") {
            val project = Project(id = 44L, name = "norestype", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "norestype") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9105L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(44L) } returns emptyList()

            mockMvc.perform(
                get("/api/owner/norestype/mentionList")
                    .param("mentionType", "user")
                    .param("number", "1")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("resourceType=BOARD_POST면 게시글 댓글 작성자를 최근 순으로, 게시글 작성자를 마지막에 포함해야 한다") {
            val project = Project(id = 45L, name = "board1", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val postingAuthor = User(id = 105L, loginId = "postAuthor", name = "글쓴이")
            val commenter = User(id = 106L, loginId = "postCommenter", name = "댓글러")
            val posting = Posting(id = 700L, title = "글", project = project, number = 9L, authorLoginId = "postAuthor")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "board1") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9106L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(45L) } returns emptyList()
            every { postingRepository.findByProjectAndNumber(project, 9L) } returns posting
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(700L) } returns listOf(
                PostingComment(authorLoginId = "postCommenter", posting = posting)
            )
            every { userRepository.findByLoginId("postCommenter") } returns Optional.of(commenter)
            every { userRepository.findByLoginId("postAuthor") } returns Optional.of(postingAuthor)

            mockMvc.perform(
                get("/api/owner/board1/mentionList")
                    .param("mentionType", "user")
                    .param("number", "9")
                    .param("resourceType", "BOARD_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("postCommenter"))
                .andExpect(jsonPath("$.result[1].loginid").value("postAuthor"))
        }

        it("resourceType이 ISSUE_POST/BOARD_POST가 아니면 댓글 작성자 수집을 건너뛰어야 한다") {
            val project = Project(id = 46L, name = "badtype", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "badtype") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9107L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(46L) } returns emptyList()

            mockMvc.perform(
                get("/api/owner/badtype/mentionList")
                    .param("mentionType", "user")
                    .param("number", "1")
                    .param("resourceType", "PULL_REQUEST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("ISSUE_POST인데 이슈를 찾지 못하면 댓글 작성자·공유자 수집을 건너뛰어야 한다") {
            val project = Project(id = 47L, name = "noissue", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "noissue") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9108L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(47L) } returns emptyList()
            every { issueRepository.findByProjectAndNumber(project, 99L) } returns null

            mockMvc.perform(
                get("/api/owner/noissue/mentionList")
                    .param("mentionType", "user")
                    .param("number", "99")
                    .param("resourceType", "ISSUE_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("BOARD_POST인데 게시글을 찾지 못하면 댓글 작성자 수집을 건너뛰어야 한다") {
            val project = Project(id = 48L, name = "noposting", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "noposting") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9109L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(48L) } returns emptyList()
            every { postingRepository.findByProjectAndNumber(project, 88L) } returns null

            mockMvc.perform(
                get("/api/owner/noposting/mentionList")
                    .param("mentionType", "user")
                    .param("number", "88")
                    .param("resourceType", "BOARD_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("이슈 작성자 로그인ID가 없으면 작성자를 후보 끝에 추가하지 않아야 한다") {
            val project = Project(id = 49L, name = "noauthorid", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val commenter = User(id = 107L, loginId = "onlyCommenter", name = "댓글만")
            val issue = Issue(id = 900L, title = "이슈", project = project, number = 10L, authorLoginId = null)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "noauthorid") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9110L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(49L) } returns emptyList()
            every { issueRepository.findByProjectAndNumber(project, 10L) } returns issue
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(900L) } returns listOf(
                IssueComment(authorLoginId = "onlyCommenter", issue = issue)
            )
            every { userRepository.findByLoginId("onlyCommenter") } returns Optional.of(commenter)

            mockMvc.perform(
                get("/api/owner/noauthorid/mentionList")
                    .param("mentionType", "user")
                    .param("number", "10")
                    .param("resourceType", "ISSUE_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("onlyCommenter"))
                .andExpect(jsonPath("$.result[1].loginid").value("me"))
        }

        it("이슈 작성자가 이미 댓글도 남겼다면 후보 목록에 중복 추가하지 않아야 한다") {
            val project = Project(id = 50L, name = "dupauthor", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val authorAlsoCommenter = User(id = 108L, loginId = "authorCommenter", name = "본인댓글")
            val issue = Issue(id = 901L, title = "이슈", project = project, number = 11L, authorLoginId = "authorCommenter")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "dupauthor") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9111L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(50L) } returns emptyList()
            every { issueRepository.findByProjectAndNumber(project, 11L) } returns issue
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(901L) } returns listOf(
                IssueComment(authorLoginId = "authorCommenter", issue = issue)
            )
            every { userRepository.findByLoginId("authorCommenter") } returns Optional.of(authorAlsoCommenter)

            mockMvc.perform(
                get("/api/owner/dupauthor/mentionList")
                    .param("mentionType", "user")
                    .param("number", "11")
                    .param("resourceType", "ISSUE_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 본인이 댓글도 남겼으므로 중복 없이 1명(+나, +project all)
                .andExpect(jsonPath("$.result[0].loginid").value("authorCommenter"))
                .andExpect(jsonPath("$.result[1].loginid").value("me"))
        }

        it("게시글 작성자·PR contributor도 후보에 포함하고, 탈퇴 등으로 조회되지 않는 사용자는 건너뛰어야 한다") {
            val project = Project(id = 51L, name = "authors1", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val postingAuthor = User(id = 109L, loginId = "postAuthor2", name = "글쓴이2")
            val prContributor = User(id = 110L, loginId = "prContrib", name = "PR기여자")
            val posting = Posting(id = 701L, title = "글", project = project, authorId = 109L)
            val pullRequest = PullRequest(
                id = 951L, title = "PR", toProject = project, fromProject = project,
                toBranch = "master", fromBranch = "feature", contributor = prContributor, number = 12L
            )

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "authors1") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9112L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(51L) } returns emptyList()
            every { postingRepository.findByProject(project) } returns listOf(posting)
            every { pullRequestRepository.findByToProject(project) } returns listOf(pullRequest)
            every { userRepository.findById(109L) } returns Optional.of(postingAuthor)
            // 조회 안 되는 사용자(탈퇴 등)는 mapNotNull에서 걸러져야 한다
            every { userRepository.findById(110L) } returns Optional.empty()

            mockMvc.perform(
                get("/api/owner/authors1/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("postAuthor2"))
        }

        it("이슈 작성자ID가 없으면 프로젝트 작성자 후보에서 제외해야 한다") {
            val project = Project(id = 57L, name = "issuenoauthorid", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val issue = Issue(id = 903L, title = "이슈", project = project, authorId = null)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "issuenoauthorid") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9118L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(57L) } returns emptyList()
            every { issueRepository.findByProject(project) } returns listOf(issue)

            mockMvc.perform(
                get("/api/owner/issuenoauthorid/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("게시글 작성자ID가 없으면 프로젝트 작성자 후보에서 제외해야 한다") {
            val project = Project(id = 58L, name = "postingnoauthorid", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val posting = Posting(id = 702L, title = "글", project = project, authorId = null)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "postingnoauthorid") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9119L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(58L) } returns emptyList()
            every { postingRepository.findByProject(project) } returns listOf(posting)

            mockMvc.perform(
                get("/api/owner/postingnoauthorid/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("PR contributor의 id가 없으면 프로젝트 작성자 후보에서 제외해야 한다") {
            val project = Project(id = 59L, name = "prnoidcontrib", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val noIdContributor = User(loginId = "noidcontrib", name = "아이디없음기여자")
            val pullRequest = PullRequest(
                id = 961L, title = "PR", toProject = project, fromProject = project,
                toBranch = "master", fromBranch = "feature", contributor = noIdContributor, number = 40L
            )

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prnoidcontrib") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9120L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(59L) } returns emptyList()
            every { pullRequestRepository.findByToProject(project) } returns listOf(pullRequest)

            mockMvc.perform(
                get("/api/owner/prnoidcontrib/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("워처의 User.id가 없으면 후보에서 제외해야 한다") {
            val project = Project(id = 52L, name = "ghostwatch", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val ghostUser = User(loginId = "ghostwatcher", name = "유령워처")
            val watch = Watch(user = ghostUser, resourceType = ResourceType.PROJECT, resourceId = "52")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ghostwatch") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9113L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(52L) } returns emptyList()
            every { watchRepository.findByResourceTypeAndResourceId(ResourceType.PROJECT, "52") } returns listOf(watch)

            mockMvc.perform(
                get("/api/owner/ghostwatch/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 워처가 필터링되어 나만 남음
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("후보가 10명을 넘으면 @project all/@group all 항목을 목록 중간에 삽입해야 한다") {
            val org = Organization(id = 500L, name = "bigorg")
            val project = Project(id = 53L, name = "big1", owner = "owner", projectScope = ProjectScope.PRIVATE, organization = org)
            val members = (1..10).map { User(id = 110L + it, loginId = "member$it", name = "멤버$it") }

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "big1") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9114L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(53L) } returns members.map {
                ProjectUser(id = 9200L + it.id!!, user = it, project = project, role = memberRole)
            }
            every { organizationUserRepository.findByOrganizationId(500L) } returns emptyList()

            mockMvc.perform(
                get("/api/owner/big1/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 멤버 10명 + 나 + "@project all:" + "@group all: " = 13
                .andExpect(jsonPath("$.result.length()").value(13))
                .andExpect(jsonPath("$.result[8].name").value("@project all:"))
                .andExpect(jsonPath("$.result[9].name").value("@group all: "))
        }

        it("조직이 없는 프로젝트에서 후보가 10명을 넘으면 @project all 항목을 인덱스 9에 삽입해야 한다") {
            val project = Project(id = 54L, name = "big2", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val members = (1..10).map { User(id = 130L + it, loginId = "bmember$it", name = "비조직멤버$it") }

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "big2") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9115L, user = me, project = project, role = memberRole))
            every { projectUserRepository.findByProjectId(54L) } returns members.map {
                ProjectUser(id = 9300L + it.id!!, user = it, project = project, role = memberRole)
            }

            mockMvc.perform(
                get("/api/owner/big2/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 멤버 10명 + 나 + "@project all:" = 12 (조직 없어 @group all 없음)
                .andExpect(jsonPath("$.result.length()").value(12))
                .andExpect(jsonPath("$.result[9].name").value("@project all:"))
        }

        it("mentionType=issue: 번호가 없는 이슈는 issueNo를 빈 문자열로 반환해야 한다") {
            val project = Project(id = 55L, name = "issuenonum", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val issue = Issue(id = 501L, title = "번호없음", project = project, number = null)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "issuenonum") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9116L, user = me, project = project, role = memberRole))
            every { issueRepository.findForMention(project, "", PageRequest.of(0, 20)) } returns listOf(issue)

            mockMvc.perform(
                get("/api/owner/issuenonum/mentionList")
                    .param("mentionType", "issue")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].issueNo").value(""))
        }

        it("mentionType=issue: 검색 결과가 없으면 빈 배열을 반환해야 한다") {
            val project = Project(id = 96L, name = "issuenoresult", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "issuenoresult") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            me.projectUsers.add(ProjectUser(id = 9121L, user = me, project = project, role = memberRole))
            every { issueRepository.findForMention(project, "", PageRequest.of(0, 20)) } returns emptyList()

            mockMvc.perform(
                get("/api/owner/issuenoresult/mentionList")
                    .param("mentionType", "issue")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(content().json("{\"result\":[]}"))
        }

        describe("GET /api/{owner}/{projectName}/mentionListAtCommitDiff (P1-43)") {
            it("커밋 작성자와 코드 댓글 작성자를 후보에 포함해야 한다") {
                val project = Project(id = 30L, name = "cd1", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val commitAuthor = User(id = 20L, loginId = "committer1", name = "커미터")
                val codeCommenter = User(id = 21L, loginId = "codeCommenter1", name = "코드댓글러")
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()
                val thread = CodeCommentThread(
                    id = 900L, project = project, commitId = "abc123",
                    codeRange = CodeRange(
                        path = "a.kt", startSide = CodeRange.Side.B,
                        startLine = 1, startColumn = 0,
                        endSide = CodeRange.Side.B, endLine = 1, endColumn = 0
                    )
                )
                val reviewComment = ReviewComment(
                    contents = "댓글", thread = thread,
                    author = UserIdent(codeCommenter)
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cd1") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                every { projectUserRepository.existsByProjectIdAndUserId(30L, 1L) } returns true
                me.projectUsers.add(ProjectUser(id = 9007L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(30L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("abc123") } returns commit
                every { commit.getAuthor() } returns commitAuthor
                every { commit.getAuthorEmail() } returns "committer1@yona.io"
                every { userRepository.findByLoginId("committer1") } returns Optional.of(commitAuthor)
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "abc123")
                } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(900L) } returns listOf(reviewComment)
                every { userRepository.findByLoginId("codeCommenter1") } returns Optional.of(codeCommenter)

                mockMvc.perform(
                    get("/api/owner/cd1/mentionListAtCommitDiff")
                        .param("commitId", "abc123")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("committer1"))
                    .andExpect(jsonPath("$.result[1].loginid").value("codeCommenter1"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "none") } returns Optional.empty()

                mockMvc.perform(
                    get("/api/owner/none/mentionListAtCommitDiff")
                        .param("mentionType", "user")
                        .principal(meAuth)
                ).andExpect(status().isNotFound)
            }

            it("읽기 권한이 없으면 403을 반환해야 한다") {
                val project = Project(id = 60L, name = "cdforbidden", owner = "owner", projectScope = ProjectScope.PRIVATE)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdforbidden") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)

                mockMvc.perform(
                    get("/api/owner/cdforbidden/mentionListAtCommitDiff")
                        .param("mentionType", "user")
                        .principal(meAuth)
                ).andExpect(status().isForbidden)
            }

            it("pullRequestId가 주어지고 PR이 존재하면 fromProject 기준으로 후보를 모아야 한다") {
                val toProject = Project(id = 61L, name = "cdto", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val fromProject = Project(id = 62L, name = "cdfrom", owner = "owner2", projectScope = ProjectScope.PRIVATE)
                val fromCommitter = User(id = 111L, loginId = "fromcommitter", name = "포크커미터")
                val contributor = User(id = 112L, loginId = "prcontrib2", name = "기여자2")
                val pullRequest = PullRequest(
                    id = 952L, title = "PR", toProject = toProject, fromProject = fromProject,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 20L
                )
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdto") } returns Optional.of(toProject)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9200L, user = me, project = toProject, role = memberRole))
                every { pullRequestRepository.findById(952L) } returns Optional.of(pullRequest)
                every { projectUserRepository.findByProjectId(61L) } returns emptyList()
                every { repositoryService.getRepository(fromProject) } returns repo
                every { repo.getCommit("fc1") } returns commit
                every { commit.getAuthor() } returns fromCommitter
                every { commit.getAuthorEmail() } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(fromProject, "fc1")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdto/mentionListAtCommitDiff")
                        .param("commitId", "fc1")
                        .param("pullRequestId", "952")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("fromcommitter"))
            }

            it("pullRequestId가 주어졌지만 PR을 찾지 못하면 project 자체를 기준으로 사용해야 한다") {
                val project = Project(id = 63L, name = "cdprmissing", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val committer = User(id = 113L, loginId = "fallbackcommitter", name = "폴백커미터")
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdprmissing") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9201L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(8888L) } returns Optional.empty()
                every { projectUserRepository.findByProjectId(63L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("fbc1") } returns commit
                every { commit.getAuthor() } returns committer
                every { commit.getAuthorEmail() } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "fbc1")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdprmissing/mentionListAtCommitDiff")
                        .param("commitId", "fbc1")
                        .param("pullRequestId", "8888")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("fallbackcommitter"))
            }

            it("mentionType=issue면 이슈 목록을 반환해야 한다") {
                val project = Project(id = 64L, name = "cdissue", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val issue = Issue(id = 502L, title = "커밋뷰이슈", project = project, number = 21L)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdissue") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9202L, user = me, project = project, role = memberRole))
                every { issueRepository.findForMention(project, "", PageRequest.of(0, 20)) } returns listOf(issue)

                mockMvc.perform(
                    get("/api/owner/cdissue/mentionListAtCommitDiff")
                        .param("mentionType", "issue")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].issueNo").value("21"))
            }

            it("mentionType이 user/issue가 아니면 빈 결과를 반환해야 한다") {
                val project = Project(id = 65L, name = "cdnone", owner = "owner", projectScope = ProjectScope.PRIVATE)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnone") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9203L, user = me, project = project, role = memberRole))

                mockMvc.perform(
                    get("/api/owner/cdnone/mentionListAtCommitDiff")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().json("{}"))
            }

            it("query가 있으면 전역 사용자 검색 결과를 사용해야 한다") {
                val project = Project(id = 66L, name = "cdsearch", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val found = User(id = 114L, loginId = "cdfound", name = "찾음")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdsearch") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9204L, user = me, project = project, role = memberRole))
                every { userRepository.searchUsers("qq", PageRequest.of(0, 20)) } returns PageImpl(listOf(found))

                mockMvc.perform(
                    get("/api/owner/cdsearch/mentionListAtCommitDiff")
                        .param("mentionType", "user")
                        .param("query", "qq")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("cdfound"))
            }

            it("commitId가 비어있으면 커밋 작성자/코드 댓글 작성자 수집을 건너뛰어야 한다") {
                val project = Project(id = 67L, name = "cdnocommit", owner = "owner", projectScope = ProjectScope.PRIVATE)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnocommit") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9205L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(67L) } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdnocommit/mentionListAtCommitDiff")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }

            it("조직이 있는 프로젝트면 조직 멤버도 후보에 포함해야 한다") {
                val org = Organization(id = 501L, name = "cdorg")
                val project = Project(id = 68L, name = "cdwithorg", owner = "owner", projectScope = ProjectScope.PRIVATE, organization = org)
                val orgMember = User(id = 115L, loginId = "cdorgmember", name = "조직멤버")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdwithorg") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9206L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(68L) } returns emptyList()
                every { organizationUserRepository.findByOrganizationId(501L) } returns listOf(
                    OrganizationUser(id = 960L, user = orgMember, organization = org, role = memberRole)
                )

                mockMvc.perform(
                    get("/api/owner/cdwithorg/mentionListAtCommitDiff")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("cdorgmember"))
            }

            it("비로그인 사용자도 공개 프로젝트를 조회할 수 있어야 한다") {
                val project = Project(id = 69L, name = "cdanon", owner = "owner", projectScope = ProjectScope.PUBLIC)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdanon") } returns Optional.of(project)
                every { projectUserRepository.findByProjectId(69L) } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdanon/mentionListAtCommitDiff")
                        .param("mentionType", "user")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().json("{\"result\":[]}"))
            }

            it("커밋 조회 중 예외가 발생하면 커밋 작성자 없이 처리해야 한다") {
                val project = Project(id = 70L, name = "cdexc", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdexc") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9207L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(70L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("bad") } throws RuntimeException("commit not found")
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "bad")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdexc/mentionListAtCommitDiff")
                        .param("commitId", "bad")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }

            it("커밋 작성자를 User로 매칭하지 못해도 이메일 prefix로 폴백 조회해야 한다") {
                val project = Project(id = 71L, name = "cdnoauthoruser", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val emailUser = User(id = 116L, loginId = "emailprefix", name = "이메일폴백")
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnoauthoruser") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9208L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(71L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("noauthor") } returns commit
                every { commit.getAuthor() } returns null
                every { commit.getAuthorEmail() } returns "emailprefix@yona.io"
                every { userRepository.findByLoginId("emailprefix") } returns Optional.of(emailUser)
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "noauthor")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdnoauthoruser/mentionListAtCommitDiff")
                        .param("commitId", "noauthor")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("emailprefix"))
            }

            it("작성자 이메일이 없으면 로그인ID 폴백 조회를 하지 않아야 한다") {
                val project = Project(id = 72L, name = "cdnoemail", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val committer = User(id = 117L, loginId = "noemailcommitter", name = "이메일없음")
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnoemail") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9209L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(72L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("noemail") } returns commit
                every { commit.getAuthor() } returns committer
                every { commit.getAuthorEmail() } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "noemail")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdnoemail/mentionListAtCommitDiff")
                        .param("commitId", "noemail")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("noemailcommitter"))
            }

            it("작성자 이메일에 '@'이 없으면 로그인ID 폴백 조회를 하지 않아야 한다") {
                val project = Project(id = 73L, name = "cdbademail", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val committer = User(id = 118L, loginId = "bademailcommitter", name = "이상한이메일")
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdbademail") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9210L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(73L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("bademail") } returns commit
                every { commit.getAuthor() } returns committer
                every { commit.getAuthorEmail() } returns "not-an-email"
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "bademail")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdbademail/mentionListAtCommitDiff")
                        .param("commitId", "bademail")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("bademailcommitter"))
            }

            it("이메일 prefix로 사용자를 찾지 못하면 추가하지 않아야 한다") {
                val project = Project(id = 74L, name = "cdnoprefixuser", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val committer = User(id = 119L, loginId = "prefixcommitter", name = "프리픽스커미터")
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnoprefixuser") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9211L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(74L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("noprefix") } returns commit
                every { commit.getAuthor() } returns committer
                every { commit.getAuthorEmail() } returns "ghostprefix@yona.io"
                every { userRepository.findByLoginId("ghostprefix") } returns Optional.empty()
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "noprefix")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdnoprefixuser/mentionListAtCommitDiff")
                        .param("commitId", "noprefix")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    // getAuthor() 결과인 prefixcommitter만 남고 ghostprefix는 추가되지 않는다
                    .andExpect(jsonPath("$.result[0].loginid").value("prefixcommitter"))
                    .andExpect(jsonPath("$.result[1].loginid").value("me"))
            }

            it("이메일 prefix로 찾은 사용자가 getAuthor() 결과와 같으면 중복 추가하지 않아야 한다") {
                val project = Project(id = 75L, name = "cdsameuser", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val committer = User(id = 120L, loginId = "samecommitter", name = "동일커미터")
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdsameuser") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9212L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(75L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("same") } returns commit
                every { commit.getAuthor() } returns committer
                every { commit.getAuthorEmail() } returns "samecommitter@yona.io"
                every { userRepository.findByLoginId("samecommitter") } returns Optional.of(committer)
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "same")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdsameuser/mentionListAtCommitDiff")
                        .param("commitId", "same")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    // 중복 없이 커미터 1명만 나오고, 다음이 나(me)여야 한다
                    .andExpect(jsonPath("$.result[0].loginid").value("samecommitter"))
                    .andExpect(jsonPath("$.result[1].loginid").value("me"))
            }

            it("SVN 프로젝트는 commitCommentRepository로 코드 댓글 작성자를 조회해야 한다") {
                val project = Project(id = 76L, name = "cdsvn", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "SVN")
                val svnCommenter = User(id = 121L, loginId = "svncommenter", name = "SVN댓글러")
                val repo = mockk<PlayRepository>()
                val svnComment = CommitComment(contents = "svn댓글", project = project, commitId = "svn1", author = UserIdent(svnCommenter))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdsvn") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9213L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(76L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("svn1") } returns null
                every { commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, "svn1") } returns listOf(svnComment)
                every { userRepository.findByLoginId("svncommenter") } returns Optional.of(svnCommenter)

                mockMvc.perform(
                    get("/api/owner/cdsvn/mentionListAtCommitDiff")
                        .param("commitId", "svn1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("svncommenter"))
            }

            it("vcs가 SUBVERSION이어도 SVN과 동일하게 commitCommentRepository를 사용해야 한다") {
                val project = Project(id = 94L, name = "cdsubversion", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "SUBVERSION")
                val svnCommenter = User(id = 136L, loginId = "subversioncommenter", name = "서브버전댓글러")
                val repo = mockk<PlayRepository>()
                val svnComment = CommitComment(contents = "서브버전댓글", project = project, commitId = "sv1", author = UserIdent(svnCommenter))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdsubversion") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9228L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(94L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("sv1") } returns null
                every { commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, "sv1") } returns listOf(svnComment)
                every { userRepository.findByLoginId("subversioncommenter") } returns Optional.of(svnCommenter)

                mockMvc.perform(
                    get("/api/owner/cdsubversion/mentionListAtCommitDiff")
                        .param("commitId", "sv1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("subversioncommenter"))
            }

            it("vcs가 없는 프로젝트는 GIT과 동일하게 commentThreadRepository를 사용해야 한다") {
                val project = Project(id = 95L, name = "cdnovcs", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = null)
                val commenter = User(id = 137L, loginId = "novcscommenter", name = "vcs없음댓글러")
                val repo = mockk<PlayRepository>()
                val thread = CodeCommentThread(
                    id = 915L, project = project, commitId = "nv1",
                    codeRange = CodeRange(path = "f.kt", startSide = CodeRange.Side.B, startLine = 1, startColumn = 0, endSide = CodeRange.Side.B, endLine = 1, endColumn = 0)
                )
                val comment = ReviewComment(contents = "vcs없음댓글", thread = thread, author = UserIdent(commenter))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnovcs") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9229L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(95L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("nv1") } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "nv1")
                } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(915L) } returns listOf(comment)
                every { userRepository.findByLoginId("novcscommenter") } returns Optional.of(commenter)

                mockMvc.perform(
                    get("/api/owner/cdnovcs/mentionListAtCommitDiff")
                        .param("commitId", "nv1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("novcscommenter"))
            }

            it("SVN 코드 댓글 작성자가 없으면(author=null) 후보에서 제외해야 한다") {
                val project = Project(id = 97L, name = "cdsvnnoauthor", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "SVN")
                val repo = mockk<PlayRepository>()
                val svnCommentNoAuthor = CommitComment(contents = "작성자없음", project = project, commitId = "svnna1", author = null)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdsvnnoauthor") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9230L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(97L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("svnna1") } returns null
                every {
                    commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, "svnna1")
                } returns listOf(svnCommentNoAuthor)

                mockMvc.perform(
                    get("/api/owner/cdsvnnoauthor/mentionListAtCommitDiff")
                        .param("commitId", "svnna1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }

            it("SVN 코드 댓글 작성자의 loginId가 없으면 후보에서 제외해야 한다") {
                val project = Project(id = 98L, name = "cdsvnnologinid", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "SVN")
                val repo = mockk<PlayRepository>()
                val svnCommentNoLoginId = CommitComment(
                    contents = "로그인id없음",
                    project = project,
                    commitId = "svnnl1",
                    author = UserIdent(id = 995L, loginId = null, name = "이름만3")
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdsvnnologinid") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9231L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(98L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("svnnl1") } returns null
                every {
                    commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, "svnnl1")
                } returns listOf(svnCommentNoLoginId)

                mockMvc.perform(
                    get("/api/owner/cdsvnnologinid/mentionListAtCommitDiff")
                        .param("commitId", "svnnl1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }

            it("코드 댓글 작성자가 없으면(author=null) 후보에서 제외해야 한다") {
                val project = Project(id = 77L, name = "cdnoauthorcomment", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val repo = mockk<PlayRepository>()
                val thread = CodeCommentThread(
                    id = 910L, project = project, commitId = "nc1",
                    codeRange = CodeRange(
                        path = "a.kt", startSide = CodeRange.Side.B,
                        startLine = 1, startColumn = 0,
                        endSide = CodeRange.Side.B, endLine = 1, endColumn = 0
                    )
                )
                val commentNoAuthor = ReviewComment(contents = "무명 댓글", thread = thread, author = null)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnoauthorcomment") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9214L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(77L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("nc1") } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "nc1")
                } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(910L) } returns listOf(commentNoAuthor)

                mockMvc.perform(
                    get("/api/owner/cdnoauthorcomment/mentionListAtCommitDiff")
                        .param("commitId", "nc1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }

            it("코드 댓글 작성자의 loginId가 없으면 후보에서 제외해야 한다") {
                val project = Project(id = 78L, name = "cdnologinid", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val repo = mockk<PlayRepository>()
                val thread = CodeCommentThread(
                    id = 911L, project = project, commitId = "nl1",
                    codeRange = CodeRange(
                        path = "a.kt", startSide = CodeRange.Side.B,
                        startLine = 1, startColumn = 0,
                        endSide = CodeRange.Side.B, endLine = 1, endColumn = 0
                    )
                )
                val commentNoLoginId = ReviewComment(contents = "로그인id없음", thread = thread, author = UserIdent(id = 999L, loginId = null, name = "이름만"))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnologinid") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9215L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(78L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("nl1") } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "nl1")
                } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(911L) } returns listOf(commentNoLoginId)

                mockMvc.perform(
                    get("/api/owner/cdnologinid/mentionListAtCommitDiff")
                        .param("commitId", "nl1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }

            it("같은 사람이 여러 코드 댓글을 남기면 가장 최근 위치로 재배치해야 한다") {
                val project = Project(id = 79L, name = "cddupcommenter", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val dupUser = User(id = 122L, loginId = "dupcodecommenter", name = "중복댓글러")
                val otherUser = User(id = 123L, loginId = "othercodecommenter", name = "다른댓글러")
                val repo = mockk<PlayRepository>()
                val thread1 = CodeCommentThread(
                    id = 912L, project = project, commitId = "dup1",
                    codeRange = CodeRange(path = "a.kt", startSide = CodeRange.Side.B, startLine = 1, startColumn = 0, endSide = CodeRange.Side.B, endLine = 1, endColumn = 0)
                )
                val thread2 = CodeCommentThread(
                    id = 913L, project = project, commitId = "dup1",
                    codeRange = CodeRange(path = "b.kt", startSide = CodeRange.Side.B, startLine = 1, startColumn = 0, endSide = CodeRange.Side.B, endLine = 1, endColumn = 0)
                )
                val commentDupFirst = ReviewComment(contents = "첫댓글", thread = thread1, author = UserIdent(dupUser))
                val commentOther = ReviewComment(contents = "다른사람댓글", thread = thread1, author = UserIdent(otherUser))
                val commentDupAgain = ReviewComment(contents = "재댓글", thread = thread2, author = UserIdent(dupUser))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cddupcommenter") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9216L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(79L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("dup1") } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "dup1")
                } returns listOf(thread2, thread1)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(912L) } returns listOf(commentDupFirst, commentOther)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(913L) } returns listOf(commentDupAgain)
                every { userRepository.findByLoginId("dupcodecommenter") } returns Optional.of(dupUser)
                every { userRepository.findByLoginId("othercodecommenter") } returns Optional.of(otherUser)

                mockMvc.perform(
                    get("/api/owner/cddupcommenter/mentionListAtCommitDiff")
                        .param("commitId", "dup1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    // 재댓글을 남긴 dupcodecommenter가 가장 최근이라 맨 앞에 와야 한다
                    .andExpect(jsonPath("$.result[0].loginid").value("dupcodecommenter"))
                    .andExpect(jsonPath("$.result[1].loginid").value("othercodecommenter"))
            }

            it("loginId로 사용자를 찾지 못하면 코드 댓글 작성자 후보에서 제외해야 한다") {
                val project = Project(id = 80L, name = "cdghostcommenter", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val repo = mockk<PlayRepository>()
                val thread = CodeCommentThread(
                    id = 914L, project = project, commitId = "ghost1",
                    codeRange = CodeRange(path = "a.kt", startSide = CodeRange.Side.B, startLine = 1, startColumn = 0, endSide = CodeRange.Side.B, endLine = 1, endColumn = 0)
                )
                val ghostComment = ReviewComment(contents = "유령댓글", thread = thread, author = UserIdent(id = 998L, loginId = "ghostcodecommenter", name = "유령"))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdghostcommenter") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9217L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(80L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("ghost1") } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "ghost1")
                } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(914L) } returns listOf(ghostComment)
                every { userRepository.findByLoginId("ghostcodecommenter") } returns Optional.empty()

                mockMvc.perform(
                    get("/api/owner/cdghostcommenter/mentionListAtCommitDiff")
                        .param("commitId", "ghost1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }

            it("커밋 작성자로 매칭된 User의 id가 없으면 후보에 추가하지 않아야 한다") {
                val project = Project(id = 81L, name = "cdnoidauthor", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val noIdAuthor = User(loginId = "noidcommitter", name = "아이디없음")
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "cdnoidauthor") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9218L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(81L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("noid1") } returns commit
                every { commit.getAuthor() } returns noIdAuthor
                every { commit.getAuthorEmail() } returns null
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "noid1")
                } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/cdnoidauthor/mentionListAtCommitDiff")
                        .param("commitId", "noid1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }
        }

        describe("GET /api/{owner}/{projectName}/mentionListAtPullRequest (P1-43)") {
            it("PR 코드리뷰 댓글 작성자와 PR contributor를 후보에 포함해야 한다") {
                val project = Project(id = 31L, name = "pr1", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val contributor = User(id = 22L, loginId = "contributor1", name = "기여자")
                val reviewer = User(id = 23L, loginId = "reviewer1", name = "리뷰어")
                val pullRequest = PullRequest(
                    id = 950L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 5L
                )
                val thread = CodeCommentThread(
                    id = 901L, project = project, pullRequest = pullRequest, commitId = "def456",
                    codeRange = CodeRange(
                        path = "b.kt", startSide = CodeRange.Side.B,
                        startLine = 1, startColumn = 0,
                        endSide = CodeRange.Side.B, endLine = 1, endColumn = 0
                    )
                )
                val reviewComment = ReviewComment(
                    contents = "리뷰", thread = thread,
                    author = UserIdent(reviewer)
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pr1") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                every { projectUserRepository.existsByProjectIdAndUserId(31L, 1L) } returns true
                me.projectUsers.add(ProjectUser(id = 9008L, user = me, project = project, role = memberRole))
                every { projectUserRepository.findByProjectId(31L) } returns emptyList()
                every { pullRequestRepository.findById(950L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(901L) } returns listOf(reviewComment)
                every { userRepository.findByLoginId("reviewer1") } returns Optional.of(reviewer)

                mockMvc.perform(
                    get("/api/owner/pr1/mentionListAtPullRequest")
                        .param("pullRequestId", "950")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("reviewer1"))
                    .andExpect(jsonPath("$.result[1].loginid").value("contributor1"))
            }

            it("존재하지 않는 PR이면 404를 반환해야 한다") {
                val project = Project(id = 32L, name = "pr2", owner = "owner", projectScope = ProjectScope.PRIVATE)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pr2") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                every { projectUserRepository.existsByProjectIdAndUserId(32L, 1L) } returns true
                me.projectUsers.add(ProjectUser(id = 9009L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(9999L) } returns Optional.empty()

                mockMvc.perform(
                    get("/api/owner/pr2/mentionListAtPullRequest")
                        .param("pullRequestId", "9999")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "none") } returns Optional.empty()

                mockMvc.perform(
                    get("/api/owner/none/mentionListAtPullRequest")
                        .param("pullRequestId", "1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                ).andExpect(status().isNotFound)
            }

            it("읽기 권한이 없으면 403을 반환해야 한다") {
                val project = Project(id = 82L, name = "prforbidden", owner = "owner", projectScope = ProjectScope.PRIVATE)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prforbidden") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)

                mockMvc.perform(
                    get("/api/owner/prforbidden/mentionListAtPullRequest")
                        .param("pullRequestId", "1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                ).andExpect(status().isForbidden)
            }

            it("mentionType=issue면 이슈 목록을 반환해야 한다") {
                val project = Project(id = 83L, name = "prissue", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val pullRequest = PullRequest(
                    id = 953L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = User(id = 124L, loginId = "contrib3", name = "기여자3"), number = 30L
                )
                val issue = Issue(id = 503L, title = "PR용이슈", project = project, number = 31L)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prissue") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9219L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(953L) } returns Optional.of(pullRequest)
                every { issueRepository.findForMention(project, "", PageRequest.of(0, 20)) } returns listOf(issue)

                mockMvc.perform(
                    get("/api/owner/prissue/mentionListAtPullRequest")
                        .param("pullRequestId", "953")
                        .param("mentionType", "issue")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].issueNo").value("31"))
            }

            it("mentionType이 user/issue가 아니면 빈 결과를 반환해야 한다") {
                val project = Project(id = 84L, name = "prnone", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val pullRequest = PullRequest(
                    id = 954L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = User(id = 125L, loginId = "contrib4", name = "기여자4"), number = 32L
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prnone") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9220L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(954L) } returns Optional.of(pullRequest)

                mockMvc.perform(
                    get("/api/owner/prnone/mentionListAtPullRequest")
                        .param("pullRequestId", "954")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().json("{}"))
            }

            it("query가 있으면 전역 사용자 검색 결과를 사용하고 PR contributor는 항상 추가해야 한다") {
                val project = Project(id = 85L, name = "prsearch", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val contributor = User(id = 126L, loginId = "contrib5", name = "기여자5")
                val found = User(id = 127L, loginId = "prfound", name = "PR찾음")
                val pullRequest = PullRequest(
                    id = 955L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 33L
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prsearch") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9221L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(955L) } returns Optional.of(pullRequest)
                every { userRepository.searchUsers("pq", PageRequest.of(0, 20)) } returns PageImpl(listOf(found))

                mockMvc.perform(
                    get("/api/owner/prsearch/mentionListAtPullRequest")
                        .param("pullRequestId", "955")
                        .param("mentionType", "user")
                        .param("query", "pq")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("prfound"))
                    .andExpect(jsonPath("$.result[1].loginid").value("contrib5"))
                    .andExpect(jsonPath("$.result[2].loginid").value("me"))
            }

            it("commitId가 있으면 fromProject 기준으로 커밋 작성자를 포함해야 한다") {
                val toProject = Project(id = 86L, name = "prto", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val fromProject = Project(id = 87L, name = "prfrom", owner = "owner2", projectScope = ProjectScope.PRIVATE)
                val contributor = User(id = 128L, loginId = "contrib6", name = "기여자6")
                val commitAuthor = User(id = 129L, loginId = "prcommitauthor", name = "PR커밋작성자")
                val pullRequest = PullRequest(
                    id = 956L, title = "PR", toProject = toProject, fromProject = fromProject,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 34L
                )
                val commit = mockk<Commit>()
                val repo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prto") } returns Optional.of(toProject)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9222L, user = me, project = toProject, role = memberRole))
                every { pullRequestRepository.findById(956L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { projectUserRepository.findByProjectId(86L) } returns emptyList()
                every { repositoryService.getRepository(fromProject) } returns repo
                every { repo.getCommit("prc1") } returns commit
                every { commit.getAuthor() } returns commitAuthor
                every { commit.getAuthorEmail() } returns null

                mockMvc.perform(
                    get("/api/owner/prto/mentionListAtPullRequest")
                        .param("pullRequestId", "956")
                        .param("commitId", "prc1")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("prcommitauthor"))
                    .andExpect(jsonPath("$.result[1].loginid").value("contrib6"))
            }

            it("조직이 있는 프로젝트면 조직 멤버도 후보에 포함해야 한다") {
                val org = Organization(id = 502L, name = "prorg")
                val project = Project(id = 88L, name = "prwithorg", owner = "owner", projectScope = ProjectScope.PRIVATE, organization = org)
                val orgMember = User(id = 130L, loginId = "prorgmember", name = "PR조직멤버")
                val contributor = User(id = 131L, loginId = "contrib7", name = "기여자7")
                val pullRequest = PullRequest(
                    id = 957L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 35L
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prwithorg") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9223L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(957L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { projectUserRepository.findByProjectId(88L) } returns emptyList()
                every { organizationUserRepository.findByOrganizationId(502L) } returns listOf(
                    OrganizationUser(id = 961L, user = orgMember, organization = org, role = memberRole)
                )

                mockMvc.perform(
                    get("/api/owner/prwithorg/mentionListAtPullRequest")
                        .param("pullRequestId", "957")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("prorgmember"))
            }

            it("비로그인 사용자도 공개 프로젝트를 조회할 수 있어야 한다") {
                val project = Project(id = 89L, name = "pranon", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val contributor = User(id = 132L, loginId = "contrib8", name = "기여자8")
                val pullRequest = PullRequest(
                    id = 958L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 36L
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pranon") } returns Optional.of(project)
                every { pullRequestRepository.findById(958L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { projectUserRepository.findByProjectId(89L) } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/pranon/mentionListAtPullRequest")
                        .param("pullRequestId", "958")
                        .param("mentionType", "user")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("contrib8"))
            }

            it("PR 코드리뷰 댓글 작성자가 없으면(author=null) 후보에서 제외해야 한다") {
                val project = Project(id = 90L, name = "prnoauthor", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val contributor = User(id = 133L, loginId = "contrib9", name = "기여자9")
                val pullRequest = PullRequest(
                    id = 959L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 37L
                )
                val thread = CodeCommentThread(
                    id = 920L, project = project, pullRequest = pullRequest, commitId = "prc2",
                    codeRange = CodeRange(path = "c.kt", startSide = CodeRange.Side.B, startLine = 1, startColumn = 0, endSide = CodeRange.Side.B, endLine = 1, endColumn = 0)
                )
                val commentNoAuthor = ReviewComment(contents = "무명리뷰", thread = thread, author = null)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prnoauthor") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9224L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(959L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(920L) } returns listOf(commentNoAuthor)
                every { projectUserRepository.findByProjectId(90L) } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/prnoauthor/mentionListAtPullRequest")
                        .param("pullRequestId", "959")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("contrib9"))
            }

            it("PR 코드리뷰 댓글 작성자를 loginId로 찾지 못하면 후보에서 제외해야 한다") {
                val project = Project(id = 91L, name = "prghostreviewer", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val contributor = User(id = 134L, loginId = "contrib10", name = "기여자10")
                val pullRequest = PullRequest(
                    id = 960L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 38L
                )
                val thread = CodeCommentThread(
                    id = 921L, project = project, pullRequest = pullRequest, commitId = "prc3",
                    codeRange = CodeRange(path = "d.kt", startSide = CodeRange.Side.B, startLine = 1, startColumn = 0, endSide = CodeRange.Side.B, endLine = 1, endColumn = 0)
                )
                val ghostReview = ReviewComment(contents = "유령리뷰", thread = thread, author = UserIdent(id = 997L, loginId = "ghostreviewer", name = "유령리뷰어"))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prghostreviewer") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9225L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(960L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(921L) } returns listOf(ghostReview)
                every { userRepository.findByLoginId("ghostreviewer") } returns Optional.empty()
                every { projectUserRepository.findByProjectId(91L) } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/prghostreviewer/mentionListAtPullRequest")
                        .param("pullRequestId", "960")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("contrib10"))
            }

            it("PR 코드리뷰 댓글 작성자의 loginId가 없으면 후보에서 제외해야 한다") {
                val project = Project(id = 92L, name = "prnologinid", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val contributor = User(id = 135L, loginId = "contrib11", name = "기여자11")
                val pullRequest = PullRequest(
                    id = 962L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 41L
                )
                val thread = CodeCommentThread(
                    id = 922L, project = project, pullRequest = pullRequest, commitId = "prc4",
                    codeRange = CodeRange(path = "e.kt", startSide = CodeRange.Side.B, startLine = 1, startColumn = 0, endSide = CodeRange.Side.B, endLine = 1, endColumn = 0)
                )
                val noLoginIdReview = ReviewComment(contents = "로그인id없음리뷰", thread = thread, author = UserIdent(id = 996L, loginId = null, name = "이름만2"))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prnologinid") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9226L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(962L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(922L) } returns listOf(noLoginIdReview)
                every { projectUserRepository.findByProjectId(92L) } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/prnologinid/mentionListAtPullRequest")
                        .param("pullRequestId", "962")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("contrib11"))
            }

            it("PR contributor의 id가 없으면 mentionListAtPullRequest 후보에 추가하지 않아야 한다") {
                val project = Project(id = 93L, name = "prnoidcontrib2", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val noIdContributor = User(loginId = "noidcontrib2", name = "아이디없음2")
                val pullRequest = PullRequest(
                    id = 963L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = noIdContributor, number = 42L
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "prnoidcontrib2") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                me.projectUsers.add(ProjectUser(id = 9227L, user = me, project = project, role = memberRole))
                every { pullRequestRepository.findById(963L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { projectUserRepository.findByProjectId(93L) } returns emptyList()

                mockMvc.perform(
                    get("/api/owner/prnoidcontrib2/mentionListAtPullRequest")
                        .param("pullRequestId", "963")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    // id 없는 contributor는 후보에 추가되지 않으므로 나만 남는다
                    .andExpect(jsonPath("$.result[0].loginid").value("me"))
            }
        }

        it("mentionType=issue: 최근 이슈 목록을 name/issueNo/title로 반환해야 한다") {
            val project = Project(id = 14L, name = "p3", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val issue = Issue(id = 500L, title = "버그 수정", project = project, number = 7L)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "p3") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(14L, 1L) } returns true
            me.projectUsers.add(ProjectUser(id = 9010L, user = me, project = project, role = memberRole))
            every { issueRepository.findForMention(project, "", PageRequest.of(0, 20)) } returns listOf(issue)

            mockMvc.perform(
                get("/api/owner/p3/mentionList")
                    .param("mentionType", "issue")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].issueNo").value("7"))
                .andExpect(jsonPath("$.result[0].title").value("버그 수정"))
        }

        it("mentionType이 user/issue가 아니면 빈 결과를 반환해야 한다") {
            val project = Project(id = 15L, name = "p4", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "p4") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(15L, 1L) } returns true
            me.projectUsers.add(ProjectUser(id = 9011L, user = me, project = project, role = memberRole))

            mockMvc.perform(
                get("/api/owner/p4/mentionList")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(content().json("{}"))
        }
    }
})
