package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.http.MediaType
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks
import java.util.Date

class CodeHistoryControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val repositoryService = mockk<RepositoryService>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
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

    val codeHistoryController = CodeHistoryController(
        projectRepository,
        repositoryService,
        commitCommentRepository,
        userRepository,
        projectUserRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(codeHistoryController).build()

    beforeTest {
        clearMocks(projectRepository, repositoryService, commitCommentRepository, userRepository, projectUserRepository)
    }

    describe("CodeHistoryController 커밋 댓글 API") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val user = User(id = 10L, loginId = "testuser", name = "테스터")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val commitId = "abc123def456"

        describe("POST /api/vcs/{owner}/{projectName}/commit/{commitId}/comments") {
            it("멤버가 존재하는 커밋에 댓글을 작성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val commit = mockk<Commit>(relaxed = true)
                val playRepo = mockk<PlayRepository>(relaxed = true)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getCommit(commitId) } returns commit
                every { commitCommentRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/api/vcs/owner/TestProj/commit/$commitId/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"contents": "좋은 커밋이네요"}""")
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)

                verify(exactly = 1) { commitCommentRepository.save(any()) }
            }

            it("존재하지 않는 커밋이면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val playRepo = mockk<PlayRepository>(relaxed = true)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getCommit(commitId) } returns null

                mockMvc.perform(
                    post("/api/vcs/owner/TestProj/commit/$commitId/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"contents": "댓글"}""")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }
        }

        describe("DELETE /api/vcs/{owner}/{projectName}/commit/{commitId}/comments/{id}") {
            it("작성자 본인이 삭제를 요청하면 200 OK를 반환해야 한다") {
                val comment = CommitComment(
                    id = 500L, project = project, commitId = commitId,
                    contents = "삭제될 댓글", author = UserIdent(user)
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { commitCommentRepository.findById(500L) } returns Optional.of(comment)
                every { commitCommentRepository.delete(comment) } returns Unit

                mockMvc.perform(
                    delete("/api/vcs/owner/TestProj/commit/$commitId/comments/500")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { commitCommentRepository.delete(comment) }
            }

            it("작성자도 매니저도 아니면 403 Forbidden을 반환해야 한다") {
                val otherAuthor = User(id = 99L, loginId = "someoneelse", name = "다른사람")
                val comment = CommitComment(
                    id = 501L, project = project, commitId = commitId,
                    contents = "타인의 댓글", author = UserIdent(otherAuthor)
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { commitCommentRepository.findById(501L) } returns Optional.of(comment)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()

                mockMvc.perform(
                    delete("/api/vcs/owner/TestProj/commit/$commitId/comments/501")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("작성자도 매니저도 아닌 일반 프로젝트 멤버가 삭제해도 200 OK를 반환해야 한다 (P1-93, legacy는 프로젝트 멤버 전원 허용)") {
                val otherAuthor = User(id = 99L, loginId = "someoneelse", name = "다른사람")
                val comment = CommitComment(
                    id = 502L, project = project, commitId = commitId,
                    contents = "타인의 댓글", author = UserIdent(otherAuthor)
                )
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스터")
                memberUser.projectUsers.add(
                    ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType))
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { commitCommentRepository.findById(502L) } returns Optional.of(comment)
                every { commitCommentRepository.delete(comment) } returns Unit

                mockMvc.perform(
                    delete("/api/vcs/owner/TestProj/commit/$commitId/comments/502")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { commitCommentRepository.delete(comment) }
            }
        }

        describe("GET /api/vcs/{owner}/{projectName}/commit/{commitId}/comments") {
            it("해당 커밋의 댓글 목록을 200 OK로 반환해야 한다") {
                val comment = CommitComment(id = 502L, project = project, commitId = commitId, contents = "댓글1")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every {
                    commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, commitId)
                } returns listOf(comment)

                mockMvc.perform(get("/api/vcs/owner/TestProj/commit/$commitId/comments"))
                    .andExpect(status().isOk)
            }
            it("프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NotFound") } returns Optional.empty()
                mockMvc.perform(get("/api/vcs/owner/NotFound/commit/$commitId/comments"))
                    .andExpect(status().isNotFound)
            }
        }

        describe("CodeHistoryController 커밋 내역 API") {
            describe("GET /api/vcs/{owner}/{projectName}/history") {
                it("히스토리를 성공적으로 조회해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                    val playRepo = mockk<PlayRepository>()
                    every { repositoryService.getRepository(project) } returns playRepo
                    val commit = mockk<Commit>(relaxed = true)
                    every { commit.getId() } returns commitId
                    every { commit.getShortId() } returns "abc123d"
                    every { commit.getAuthorDate() } returns Date()
                    every { commit.getCommitterDate() } returns Date()
                    every { playRepo.getHistory(0, 20, "HEAD", null) } returns listOf(commit)

                    mockMvc.perform(get("/api/vcs/owner/TestProj/history"))
                        .andExpect(status().isOk)
                }

                it("프로젝트가 없으면 404를 반환해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NotFound") } returns Optional.empty()
                    mockMvc.perform(get("/api/vcs/owner/NotFound/history"))
                        .andExpect(status().isNotFound)
                }

                it("authorDate/committerDate가 null이면 0L로 처리해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                    val playRepo = mockk<PlayRepository>()
                    every { repositoryService.getRepository(project) } returns playRepo
                    val commit = mockk<Commit>(relaxed = true)
                    every { commit.getId() } returns commitId
                    every { commit.getShortId() } returns "abc123d"
                    every { commit.getAuthorDate() } returns null
                    every { commit.getCommitterDate() } returns null
                    every { playRepo.getHistory(0, 20, "HEAD", null) } returns listOf(commit)

                    mockMvc.perform(get("/api/vcs/owner/TestProj/history"))
                        .andExpect(status().isOk)
                }
            }

            describe("GET /api/vcs/{owner}/{projectName}/commit/{commitId}") {
                it("커밋을 성공적으로 조회해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                    val playRepo = mockk<PlayRepository>()
                    every { repositoryService.getRepository(project) } returns playRepo
                    val commit = mockk<Commit>(relaxed = true)
                    every { commit.getId() } returns commitId
                    every { commit.getShortId() } returns "abc123d"
                    every { commit.getAuthorDate() } returns null
                    every { commit.getCommitterDate() } returns null
                    every { playRepo.getCommit(commitId) } returns commit

                    mockMvc.perform(get("/api/vcs/owner/TestProj/commit/$commitId"))
                        .andExpect(status().isOk)
                }

                it("프로젝트가 없으면 404를 반환해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NotFound") } returns Optional.empty()
                    mockMvc.perform(get("/api/vcs/owner/NotFound/commit/$commitId"))
                        .andExpect(status().isNotFound)
                }

                it("authorDate/committerDate가 존재하면 해당 시각을 그대로 반환해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                    val playRepo = mockk<PlayRepository>()
                    every { repositoryService.getRepository(project) } returns playRepo
                    val commit = mockk<Commit>(relaxed = true)
                    every { commit.getId() } returns commitId
                    every { commit.getShortId() } returns "abc123d"
                    every { commit.getAuthorDate() } returns Date()
                    every { commit.getCommitterDate() } returns Date()
                    every { playRepo.getCommit(commitId) } returns commit

                    mockMvc.perform(get("/api/vcs/owner/TestProj/commit/$commitId"))
                        .andExpect(status().isOk)
                }

                it("커밋이 없으면 404를 반환해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                    val playRepo = mockk<PlayRepository>()
                    every { repositoryService.getRepository(project) } returns playRepo
                    every { playRepo.getCommit(commitId) } returns null

                    mockMvc.perform(get("/api/vcs/owner/TestProj/commit/$commitId"))
                        .andExpect(status().isNotFound)
                }
            }
        }

        describe("추가 예외 케이스들") {
            it("POST /comments - 프로젝트가 없으면 404") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NotFound") } returns Optional.empty()
                mockMvc.perform(
                    post("/api/vcs/owner/NotFound/commit/$commitId/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"contents": "댓글"}""")
                ).andExpect(status().isNotFound)
            }

            it("POST /comments - 로그인 안하면 401") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                mockMvc.perform(
                    post("/api/vcs/owner/TestProj/commit/$commitId/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"contents": "댓글"}""")
                ).andExpect(status().isUnauthorized)
            }

            it("POST /comments - 권한 없으면 403") {
                val privateProject = Project(id = 2L, name = "PrivateProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateProj") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(2L, 10L) } returns Optional.empty()
                mockMvc.perform(
                    post("/api/vcs/owner/PrivateProj/commit/$commitId/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"contents": "댓글"}""")
                        .principal(userAuth)
                ).andExpect(status().isForbidden)
            }

            it("DELETE /comments - 프로젝트가 없으면 404") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NotFound") } returns Optional.empty()
                mockMvc.perform(delete("/api/vcs/owner/NotFound/commit/$commitId/comments/500"))
                    .andExpect(status().isNotFound)
            }

            it("DELETE /comments - 로그인 안하면 401") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                mockMvc.perform(delete("/api/vcs/owner/TestProj/commit/$commitId/comments/500"))
                    .andExpect(status().isUnauthorized)
            }

            it("DELETE /comments - 댓글이 없으면 404") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { commitCommentRepository.findById(999L) } returns Optional.empty()
                mockMvc.perform(
                    delete("/api/vcs/owner/TestProj/commit/$commitId/comments/999")
                        .principal(userAuth)
                ).andExpect(status().isNotFound)
            }
        }
    }
})
