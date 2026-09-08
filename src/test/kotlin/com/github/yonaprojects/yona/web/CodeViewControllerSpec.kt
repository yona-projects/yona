package com.github.yonaprojects.yona.web

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.GitBranch
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.mockk.clearMocks
import io.mockk.just
import io.mockk.Runs
import org.hamcrest.Matchers.nullValue
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.eclipse.jgit.api.errors.NoHeadException
import com.github.yonaprojects.yona.domain.vcs.Commit

class CodeViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val markdownService = mockk<MarkdownService>()
    val watchService = mockk<WatchService>()
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

    val controller = CodeViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        commentThreadRepository,
        commitCommentRepository,
        accessControl,
        markdownService,
        watchService,
        "Yona"
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(
            projectRepository,
            projectUserRepository,
            userRepository,
            repositoryService,
            commentThreadRepository,
            commitCommentRepository,
            watchService
        )
    }

    describe("CodeViewController 단위 테스트") {
        val project = Project(id = 1L, owner = "testowner", name = "testproject", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
        val playRepo = mockk<PlayRepository>()

        describe("GET /{owner}/{projectName}/code") {
            it("저장소가 비어 있는 경우 code/nohead 뷰로 가야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.isEmpty() } returns true

                mockMvc.perform(get("/testowner/testproject/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead"))
                    .andExpect(model().attribute("project", project))
            }

            it("저장소가 비어 있지 않은 경우 기본 브랜치로 리다이렉트되어야 한다") {
                val gitBranch = mockk<GitBranch>()
                every { gitBranch.shortName } returns "main"
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.isEmpty() } returns false
                every { playRepo.getHeadBranch() } returns gitBranch

                mockMvc.perform(get("/testowner/testproject/code"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testowner/testproject/code/main"))
            }

            it("[Test-12-2] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                // yona CodeApp.java:60-62 forbidden(ErrorViews.Forbidden.render("error.forbidden", project))
                // 대응 (P-템플릿 #47) — Forbidden의 (String,Project) 2-arg 오버로드는 컨텍스트 인지형
                // forbidden.render(messageKey, project)로 귀결되므로 error/forbidden으로 바로잡았다
                // (기존 assertion은 제네릭 error/403이었음).
                mockMvc.perform(get("/testowner/memberonly-project/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attribute("project", memberOnlyProject))
            }
        }

        describe("GET /{owner}/{projectName}/code/{branch}/{*path}") {
            it("지정된 브랜치와 경로의 메타데이터를 담아 code/view 템플릿을 호출해야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main", "refs/heads/dev")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Main.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attribute("project", project))
                    .andExpect(model().attribute("branches", listOf("refs/heads/main", "refs/heads/dev")))
                    .andExpect(model().attribute("branch", "main"))
                    .andExpect(model().attribute("path", "src/Main.kt"))
            }

            // yona views/code/partial_view_file.scala.html:109-114 isMarkdownExtension() 분기 대응 (P1-139).
            it(".md 파일이면 렌더링된 마크다운 HTML을 markdownHtml 모델 속성으로 담아야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "# 제목")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "README.md") } returns listOf(mockNode)
                every { markdownService.renderFileInCodeBrowser("# 제목", project) } returns "<h1>제목</h1>"

                mockMvc.perform(get("/testowner/testproject/code/main/README.md"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attribute("markdownHtml", "<h1>제목</h1>"))
            }

            it(".md가 아닌 일반 파일이면 markdownHtml 모델 속성을 담지 않아야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "fun main() {}")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Main.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attributeDoesNotExist("markdownHtml"))
            }

            it("[Test-12-2-1] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 상세 경로 접근 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/code/main/src/Main.kt"))
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
                val groupProject = Project(id = 5L, owner = "testowner", name = "group-project", vcs = "GIT", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("groupuser", "password")
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("groupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(5L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Main.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/group-project/code/main/src/Main.kt").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
            }
        }

        // yona CodeHistoryApp.history()의 "catch (NoHeadException e) { return notFound(nohead.render(project)); }"
        // 대응 (P1-136) — 빈 저장소(커밋이 하나도 없는 상태)에서 커밋 히스토리를 조회하면 JGit이
        // NoHeadException을 던지는데, yona는 이를 잡지 않아 500으로 전파되고 있었다.
        describe("GET /{owner}/{projectName}/commits/{branch} — 빈 저장소 NoHeadException (P1-136)") {
            it("빈 저장소면 500 대신 code/nohead 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { playRepo.getHistory(0, 25, "HEAD", null) } throws
                    NoHeadException("no HEAD")

                mockMvc.perform(get("/testowner/testproject/commits/HEAD"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead"))
                    .andExpect(model().attribute("project", project))
            }
        }

        describe("GET /{owner}/{projectName}/commit/{commitId} — 커밋 감시(watch) 버튼 배선 (P-템플릿 그룹10 #161 재검토)") {
            it("로그인 사용자에게 commitResourceId와 isWatching 모델 속성을 전달해야 한다") {
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "abcdef1234"
                val watcherAuth = UsernamePasswordAuthenticationToken("watcher", "password")
                val watcher = User(id = 10L, loginId = "watcher", name = "감시자")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { userRepository.findByLoginId("watcher") } returns Optional.of(watcher)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getCommit("abcdef1234") } returns commit
                every { playRepo.getParentCommitOf("abcdef1234") } returns null
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getDiff("abcdef1234") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "abcdef1234") } returns emptyList()
                every { watchService.isWatching(watcher, ResourceType.COMMIT, "1:abcdef1234") } returns true

                mockMvc.perform(get("/testowner/testproject/commit/abcdef1234").principal(watcherAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/diff"))
                    .andExpect(model().attribute("commitResourceId", "1:abcdef1234"))
                    .andExpect(model().attribute("isWatching", true))
            }
        }

        describe("GET /{owner}/{projectName}/rawcode/{rev}/{*path}") {
            it("바이너리/텍스트 데이터를 응답 헤더와 함께 올바르게 스트리밍해야 한다") {
                val rawBytes = "println('Hello')".toByteArray()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getFileAsRaw("testowner", "testproject", "main", "src/Main.kt") } returns rawBytes

                mockMvc.perform(get("/testowner/testproject/rawcode/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Disposition", "inline; filename=\"Main.kt\""))
                    .andExpect(content().bytes(rawBytes))
            }

            it("[Test-12-3] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 rawcode 다운로드 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/rawcode/main/src/Main.kt"))
                    .andExpect(status().isForbidden)
            }

            it("프로젝트를 찾을 수 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "noexist") } returns Optional.empty()

                mockMvc.perform(get("/testowner/noexist/rawcode/main/src/Main.kt"))
                    .andExpect(status().isNotFound)
            }

            it("멤버 전용 아님 + READ 권한 없는 비공개(PRIVATE) 프로젝트면 403을 반환해야 한다") {
                val privateProject = Project(id = 20L, owner = "testowner", name = "private-raw", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "private-raw") } returns Optional.of(privateProject)

                mockMvc.perform(get("/testowner/private-raw/rawcode/main/src/Main.kt"))
                    .andExpect(status().isForbidden)
            }

            it("멤버 전용 프로젝트라도 프로젝트 멤버라면 200을 반환해야 한다") {
                val memberProject = Project(id = 21L, owner = "testowner", name = "member-raw", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val memberUser = User(id = 30L, loginId = "rawmember", name = "rawmember")
                val memberAuth = UsernamePasswordAuthenticationToken("rawmember", "password")
                val rawBytes = "print(1)".toByteArray()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "member-raw") } returns Optional.of(memberProject)
                every { userRepository.findByLoginId("rawmember") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(21L, 30L) } returns true
                every { repositoryService.getFileAsRaw("testowner", "member-raw", "main", "a.kt") } returns rawBytes

                mockMvc.perform(get("/testowner/member-raw/rawcode/main/a.kt").principal(memberAuth))
                    .andExpect(status().isOk)
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) — 멤버 전용 프로젝트라도 소속
            // 조직의 멤버라면 rawcode 다운로드가 허용돼야 한다.
            it("멤버 전용 프로젝트라도 조직 멤버라면 200을 반환해야 한다") {
                val groupOrg = Organization(id = 2L, name = "raworg")
                val groupUser = User(id = 31L, loginId = "rawgroupuser", name = "rawgroupuser")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 2L, user = groupUser, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 22L, owner = "testowner", name = "group-raw", vcs = "GIT", projectScope = ProjectScope.PROTECTED, isCodeAccessibleMemberOnly = true, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("rawgroupuser", "password")
                val rawBytes = "print(2)".toByteArray()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "group-raw") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("rawgroupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(22L, 31L) } returns false
                every { repositoryService.getFileAsRaw("testowner", "group-raw", "main", "b.kt") } returns rawBytes

                mockMvc.perform(get("/testowner/group-raw/rawcode/main/b.kt").principal(groupAuth))
                    .andExpect(status().isOk)
            }

            it("원본 파일을 찾을 수 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getFileAsRaw("testowner", "testproject", "main", "nofile.kt") } returns null

                mockMvc.perform(get("/testowner/testproject/rawcode/main/nofile.kt"))
                    .andExpect(status().isNotFound)
            }
        }

        // yona CodeApp.download() 대응 — ZIP 다운로드 엔드포인트 (기존 세션에서 완전 미실행 상태였음).
        describe("GET /{owner}/{projectName}/code/download/{branch}") {
            it("프로젝트를 찾을 수 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "noexist") } returns Optional.empty()

                mockMvc.perform(get("/testowner/noexist/code/download/main"))
                    .andExpect(status().isNotFound)
            }

            it("[다운로드] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 403을 반환해야 한다") {
                val memberOnlyProject = Project(id = 23L, owner = "testowner", name = "memberonly-dl", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-dl") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-dl/code/download/main"))
                    .andExpect(status().isForbidden)
            }

            it("멤버 전용 아님 + READ 권한 없는 비공개(PRIVATE) 프로젝트면 403을 반환해야 한다") {
                val privateProject = Project(id = 24L, owner = "testowner", name = "private-dl", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "private-dl") } returns Optional.of(privateProject)

                mockMvc.perform(get("/testowner/private-dl/code/download/main"))
                    .andExpect(status().isForbidden)
            }

            it("멤버 전용 프로젝트에서 멤버도 그룹멤버도 아닌 로그인 사용자는 403을 반환해야 한다") {
                val memberOnlyProject = Project(id = 29L, owner = "testowner", name = "memberonly-dl2", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val strangerUser = User(id = 36L, loginId = "dlstranger", name = "dlstranger")
                val strangerAuth = UsernamePasswordAuthenticationToken("dlstranger", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-dl2") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("dlstranger") } returns Optional.of(strangerUser)
                every { projectUserRepository.existsByProjectIdAndUserId(29L, 36L) } returns false

                mockMvc.perform(get("/testowner/memberonly-dl2/code/download/main").principal(strangerAuth))
                    .andExpect(status().isForbidden)
            }

            it("멤버 전용 프로젝트라도 프로젝트 멤버라면 ZIP 아카이브를 200으로 반환해야 한다") {
                val memberProject = Project(id = 30L, owner = "testowner", name = "member-dl", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val memberUser = User(id = 37L, loginId = "dlmember", name = "dlmember")
                val memberAuth = UsernamePasswordAuthenticationToken("dlmember", "password")
                val dlPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "member-dl") } returns Optional.of(memberProject)
                every { userRepository.findByLoginId("dlmember") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(30L, 37L) } returns true
                every { repositoryService.getRepository(memberProject) } returns dlPlayRepo
                every { dlPlayRepo.getArchive(any(), "main") } just Runs

                mockMvc.perform(get("/testowner/member-dl/code/download/main").principal(memberAuth))
                    .andExpect(status().isOk)
            }

            it("멤버 전용 프로젝트라도 조직 멤버라면 ZIP 아카이브를 200으로 반환해야 한다") {
                val groupOrg = Organization(id = 3L, name = "dlorg")
                val groupUser = User(id = 32L, loginId = "dlgroupuser", name = "dlgroupuser")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 3L, user = groupUser, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 25L, owner = "testowner", name = "group-dl", vcs = "GIT", projectScope = ProjectScope.PROTECTED, isCodeAccessibleMemberOnly = true, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("dlgroupuser", "password")
                val dlPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "group-dl") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("dlgroupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(25L, 32L) } returns false
                every { repositoryService.getRepository(groupProject) } returns dlPlayRepo
                every { dlPlayRepo.getArchive(any(), "main") } just Runs

                mockMvc.perform(get("/testowner/group-dl/code/download/main").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("application/zip"))
                    .andExpect(header().string("Content-Disposition", "attachment; filename=group-dl-main.zip"))
            }

            it("공개 프로젝트면 지정 브랜치의 ZIP 아카이브를 생성해 200으로 반환해야 한다") {
                val dlPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns dlPlayRepo
                every { dlPlayRepo.getArchive(any(), "dev") } just Runs

                mockMvc.perform(get("/testowner/testproject/code/download/dev"))
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("application/zip"))
                    .andExpect(header().string("Content-Disposition", "attachment; filename=testproject-dev.zip"))
            }
        }

        // showImageFile/openFile은 showRawFile을 그대로 위임 호출하는 얇은 래퍼 — 메서드 커버리지 확보를
        // 위해 실제 라우팅으로 각각 최소 1회씩 호출을 확인한다.
        describe("GET /{owner}/{projectName}/image/{rev}/{*path} — showRawFile 위임") {
            it("이미지 라우트로 요청해도 showRawFile과 동일하게 바이너리를 반환해야 한다") {
                val rawBytes = byteArrayOf(1, 2, 3)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getFileAsRaw("testowner", "testproject", "main", "logo.png") } returns rawBytes

                mockMvc.perform(get("/testowner/testproject/image/main/logo.png"))
                    .andExpect(status().isOk)
                    .andExpect(content().bytes(rawBytes))
            }
        }

        describe("GET /{owner}/{projectName}/files/{rev}/{*path} — showRawFile 위임") {
            it("파일 라우트로 요청해도 showRawFile과 동일하게 바이너리를 반환해야 한다") {
                val rawBytes = byteArrayOf(4, 5, 6)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getFileAsRaw("testowner", "testproject", "main", "data.bin") } returns rawBytes

                mockMvc.perform(get("/testowner/testproject/files/main/data.bin"))
                    .andExpect(status().isOk)
                    .andExpect(content().bytes(rawBytes))
            }
        }

        describe("GET /{owner}/{projectName}/code — 추가 분기") {
            it("프로젝트를 찾을 수 없으면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "noexist") } returns Optional.empty()

                mockMvc.perform(get("/testowner/noexist/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("멤버 전용 프로젝트라도 프로젝트 멤버라면 정상 진행되어야 한다") {
                val memberProject = Project(id = 26L, owner = "testowner", name = "member-code", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val memberUser = User(id = 33L, loginId = "codemember", name = "codemember")
                val memberAuth = UsernamePasswordAuthenticationToken("codemember", "password")
                val cbPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "member-code") } returns Optional.of(memberProject)
                every { userRepository.findByLoginId("codemember") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(26L, 33L) } returns true
                every { repositoryService.getRepository(memberProject) } returns cbPlayRepo
                every { cbPlayRepo.isEmpty() } returns true

                mockMvc.perform(get("/testowner/member-code/code").principal(memberAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead"))
            }

            it("멤버 전용 아님 + READ 권한 없는 비공개(PRIVATE) 프로젝트면 error/forbidden을 반환해야 한다") {
                val privateProject = Project(id = 27L, owner = "testowner", name = "private-code", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "private-code") } returns Optional.of(privateProject)

                mockMvc.perform(get("/testowner/private-code/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("멤버 전용 프로젝트라도 조직 멤버라면 정상 진행되어야 한다") {
                val groupOrg = Organization(id = 6L, name = "codegrouporg")
                val groupUser = User(id = 34L, loginId = "codegroupuser", name = "codegroupuser")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 6L, user = groupUser, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 29L, owner = "testowner", name = "group-code", vcs = "GIT", projectScope = ProjectScope.PROTECTED, isCodeAccessibleMemberOnly = true, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("codegroupuser", "password")
                val cbPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "group-code") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("codegroupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(29L, 34L) } returns false
                every { repositoryService.getRepository(groupProject) } returns cbPlayRepo
                every { cbPlayRepo.isEmpty() } returns true

                mockMvc.perform(get("/testowner/group-code/code").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead"))
            }

            it("멤버 전용 프로젝트에서 멤버도 그룹멤버도 아닌 로그인 사용자는 error/forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 30L, owner = "testowner", name = "memberonly-code2", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val strangerUser = User(id = 35L, loginId = "codestranger", name = "codestranger")
                val strangerAuth = UsernamePasswordAuthenticationToken("codestranger", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-code2") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("codestranger") } returns Optional.of(strangerUser)
                every { projectUserRepository.existsByProjectIdAndUserId(30L, 35L) } returns false

                mockMvc.perform(get("/testowner/memberonly-code2/code").principal(strangerAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))
            }

            it("빈 SVN 저장소면 code/nohead_svn 뷰를 반환해야 한다") {
                val svnProject = Project(id = 28L, owner = "testowner", name = "svn-code", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                val svnPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-code") } returns Optional.of(svnProject)
                every { repositoryService.getRepository(svnProject) } returns svnPlayRepo
                every { svnPlayRepo.isEmpty() } returns true

                mockMvc.perform(get("/testowner/svn-code/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead_svn"))
            }

            it("헤드 브랜치가 없으면 기본 브랜치 master로 리다이렉트되어야 한다") {
                val cbPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns cbPlayRepo
                every { cbPlayRepo.isEmpty() } returns false
                every { cbPlayRepo.getHeadBranch() } returns null

                mockMvc.perform(get("/testowner/testproject/code"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testowner/testproject/code/master"))
            }
        }

        describe("GET /{owner}/{projectName}/code/{branch} — 루트 위임(codeBrowserWithBranchRoot) 및 추가 분기") {
            it("경로 없이 브랜치만 지정해도 code/view를 반환해야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "folder")
                val rootPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns rootPlayRepo
                every { rootPlayRepo.getRefNames() } returns listOf("refs/heads/main")
                every { rootPlayRepo.getNamedBranchNames() } returns emptyList()
                every { rootPlayRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(rootPlayRepo, "main", "") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attribute("path", ""))
                    .andExpect(model().attribute("breadcrumbs", emptyList<Any>()))
            }

            it("프로젝트를 찾을 수 없으면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "noexist") } returns Optional.empty()

                mockMvc.perform(get("/testowner/noexist/code/main/a.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("멤버 전용 프로젝트라도 프로젝트 멤버라면 code/view를 반환해야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                val memberProject = Project(id = 40L, owner = "testowner", name = "member-view", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val memberUser = User(id = 41L, loginId = "viewmember", name = "viewmember")
                val memberAuth = UsernamePasswordAuthenticationToken("viewmember", "password")
                val cwPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "member-view") } returns Optional.of(memberProject)
                every { userRepository.findByLoginId("viewmember") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(40L, 41L) } returns true
                every { repositoryService.getRepository(memberProject) } returns cwPlayRepo
                every { cwPlayRepo.getRefNames() } returns listOf("refs/heads/main")
                every { cwPlayRepo.getNamedBranchNames() } returns emptyList()
                every { cwPlayRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(cwPlayRepo, "main", "a.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/member-view/code/main/a.kt").principal(memberAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
            }

            it("멤버 전용 프로젝트에서 멤버도 그룹멤버도 아닌 로그인 사용자는 error/403을 반환해야 한다") {
                val memberOnlyProject = Project(id = 41L, owner = "testowner", name = "memberonly-view2", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val strangerUser = User(id = 42L, loginId = "stranger", name = "stranger")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-view2") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(strangerUser)
                every { projectUserRepository.existsByProjectIdAndUserId(41L, 42L) } returns false

                mockMvc.perform(get("/testowner/memberonly-view2/code/main/a.kt").principal(strangerAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("멤버 전용 아님 + READ 권한 없는 비공개(PRIVATE) 프로젝트면 error/403을 반환해야 한다") {
                val privateProject = Project(id = 42L, owner = "testowner", name = "private-view", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "private-view") } returns Optional.of(privateProject)

                mockMvc.perform(get("/testowner/private-view/code/main/a.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
                    .andExpect(model().attributeDoesNotExist("messageKey"))
            }

            it("존재하지 않는 브랜치/경로면 error/notfound 뷰와 관련 모델 속성을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "nobranch", "nopath") } returns null

                mockMvc.perform(get("/testowner/testproject/code/nobranch/nopath"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/notfound"))
                    .andExpect(model().attribute("project", project))
                    .andExpect(model().attribute("targetType", "code"))
                    .andExpect(model().attribute("title", "nobranch"))
            }

            it("디렉터리(folder) 항목이면 markdownHtml/fileCommentCount를 담지 않고 currentDir에 슬래시를 붙여야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "folder")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/src"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attribute("currentDir", "src/"))
                    .andExpect(model().attributeDoesNotExist("markdownHtml"))
                    .andExpect(model().attributeDoesNotExist("fileCommentCount"))
            }

            it("파일이지만 data 필드가 없으면 markdownHtml을 담지 않아야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "README.md") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/README.md"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeDoesNotExist("markdownHtml"))
            }

            it("파일이지만 revisionNo가 없으면 fileCommentCount를 담지 않아야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "fun main() {}")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Norev.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/src/Norev.kt"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeDoesNotExist("fileCommentCount"))
            }

            it("GIT 프로젝트 파일에 revisionNo가 있으면 commentThreadRepository로 댓글 수를 조회해야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "fun main() {}")
                mockNode.put("revisionNo", "abc123")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Rev.kt") } returns listOf(mockNode)
                every { commentThreadRepository.countByProjectAndCommitIdAndCodeRangePath(project, "abc123", "src/Rev.kt") } returns 2L

                mockMvc.perform(get("/testowner/testproject/code/main/src/Rev.kt"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("fileCommentCount", 2L))
            }

            it("SVN 프로젝트 파일에 revisionNo가 있으면 commitCommentRepository로 댓글 수를 조회해야 한다") {
                val svnProject = Project(id = 43L, owner = "testowner", name = "svn-view", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "fun main() {}")
                mockNode.put("revisionNo", "5")
                val svnPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-view") } returns Optional.of(svnProject)
                every { repositoryService.getRepository(svnProject) } returns svnPlayRepo
                every { svnPlayRepo.getRefNames() } returns listOf("trunk")
                every { svnPlayRepo.getNamedBranchNames() } returns emptyList()
                every { svnPlayRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(svnPlayRepo, "trunk", "file.txt") } returns listOf(mockNode)
                every { commitCommentRepository.countByProjectAndCommitIdAndPath(svnProject, "5", "file.txt") } returns 3L

                mockMvc.perform(get("/testowner/svn-view/code/trunk/file.txt"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("fileCommentCount", 3L))
            }

            // recursiveData가 null이 아니라 빈 리스트인 경우 — lastOrNull()이 null을 반환해
            // lastEntry가 null이 되는 분기(마크다운/댓글수 블록을 건너뜀)를 별도로 검증한다.
            it("메타데이터가 빈 리스트이면 lastEntry가 없어 마크다운/댓글수 블록을 건너뛰어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "empty") } returns emptyList()

                mockMvc.perform(get("/testowner/testproject/code/main/empty"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attributeDoesNotExist("markdownHtml"))
                    .andExpect(model().attributeDoesNotExist("fileCommentCount"))
            }

            // lastEntry는 존재하지만 "type" 필드 자체가 없는 노드 — recursiveData.lastOrNull()?.get("type")
            // 체인에서 get("type")이 null을 반환하는 분기(lastIsFolder 계산, 파일 여부 판정 모두)를 검증한다.
            it("메타데이터 노드에 type 필드가 없으면 folder도 file도 아닌 것으로 처리해야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("size", 0)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "notype") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/notype"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attributeDoesNotExist("markdownHtml"))
                    .andExpect(model().attributeDoesNotExist("fileCommentCount"))
            }

            // isSvn 계산에서 project.vcs 자체가 null인(VCS 미설정) 케이스 — 두 uppercase() 호출 모두
            // 안전 호출(?.)의 null 분기를 타야 하며, 결과적으로 GIT과 동일하게 commentThreadRepository를 써야 한다.
            it("vcs가 null이면 SVN이 아닌 것으로 판단해 commentThreadRepository를 사용해야 한다") {
                val novcsProject = Project(id = 45L, owner = "testowner", name = "novcs-view", projectScope = ProjectScope.PUBLIC, vcs = null)
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "fun main() {}")
                mockNode.put("revisionNo", "9")
                val novcsPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "novcs-view") } returns Optional.of(novcsProject)
                every { repositoryService.getRepository(novcsProject) } returns novcsPlayRepo
                every { novcsPlayRepo.getRefNames() } returns listOf("refs/heads/main")
                every { novcsPlayRepo.getNamedBranchNames() } returns emptyList()
                every { novcsPlayRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(novcsPlayRepo, "main", "a.kt") } returns listOf(mockNode)
                every { commentThreadRepository.countByProjectAndCommitIdAndCodeRangePath(novcsProject, "9", "a.kt") } returns 6L

                mockMvc.perform(get("/testowner/novcs-view/code/main/a.kt"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("fileCommentCount", 6L))
            }

            // isSvn = (vcs.uppercase() == "SUBVERSION" || vcs.uppercase() == "SVN") 판단에서
            // "SVN" 리터럴로만 저장된 프로젝트(두 번째 OR 절)도 SVN으로 인식돼야 한다.
            it("vcs 값이 'SVN' 리터럴이어도 SVN 프로젝트로 인식해 commitCommentRepository를 사용해야 한다") {
                val svnLiteralProject = Project(id = 44L, owner = "testowner", name = "svn-literal-view", projectScope = ProjectScope.PUBLIC, vcs = "SVN")
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "fun main() {}")
                mockNode.put("revisionNo", "7")
                val svnPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-literal-view") } returns Optional.of(svnLiteralProject)
                every { repositoryService.getRepository(svnLiteralProject) } returns svnPlayRepo
                every { svnPlayRepo.getRefNames() } returns listOf("trunk")
                every { svnPlayRepo.getNamedBranchNames() } returns emptyList()
                every { svnPlayRepo.getTagNames() } returns emptyList()
                every { repositoryService.getMetaDataFromAncestorDirectories(svnPlayRepo, "trunk", "file.txt") } returns listOf(mockNode)
                every { commitCommentRepository.countByProjectAndCommitIdAndPath(svnLiteralProject, "7", "file.txt") } returns 1L

                mockMvc.perform(get("/testowner/svn-literal-view/code/trunk/file.txt"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("fileCommentCount", 1L))
            }
        }

        // yona CodeHistoryApp.history()/historyUntilHead() 대응 — 페이징/브랜치/경로/VCS별 분기.
        describe("GET /{owner}/{projectName}/commits/{branch} — 추가 분기") {
            it("프로젝트를 찾을 수 없으면 error/404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "noexist") } returns Optional.empty()

                mockMvc.perform(get("/testowner/noexist/commits/main"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("멤버 전용 + 비멤버·비그룹멤버 로그인 사용자는 error/403(messageKey 포함)을 반환해야 한다") {
                val memberOnlyProject = Project(id = 50L, owner = "testowner", name = "memberonly-hist", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val strangerUser = User(id = 51L, loginId = "histstranger", name = "histstranger")
                val strangerAuth = UsernamePasswordAuthenticationToken("histstranger", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-hist") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("histstranger") } returns Optional.of(strangerUser)
                every { projectUserRepository.existsByProjectIdAndUserId(50L, 51L) } returns false

                mockMvc.perform(get("/testowner/memberonly-hist/commits/main").principal(strangerAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
                    .andExpect(model().attribute("messageKey", "error.forbidden.or.notfound"))
            }

            it("멤버 전용 프로젝트라도 프로젝트 멤버라면 정상적으로 code/history를 반환해야 한다") {
                val memberProject = Project(id = 52L, owner = "testowner", name = "member-hist", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val memberUser = User(id = 53L, loginId = "histmember", name = "histmember")
                val memberAuth = UsernamePasswordAuthenticationToken("histmember", "password")
                val hPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "member-hist") } returns Optional.of(memberProject)
                every { userRepository.findByLoginId("histmember") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(52L, 53L) } returns true
                every { repositoryService.getRepository(memberProject) } returns hPlayRepo
                every { hPlayRepo.getRefNames() } returns emptyList()
                every { hPlayRepo.getNamedBranchNames() } returns emptyList()
                every { hPlayRepo.getTagNames() } returns emptyList()
                every { hPlayRepo.getHistory(0, 25, "main", null) } returns emptyList()

                mockMvc.perform(get("/testowner/member-hist/commits/main").principal(memberAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/history"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("멤버 전용 프로젝트라도 조직 멤버라면 정상적으로 code/history를 반환해야 한다") {
                val groupOrg = Organization(id = 4L, name = "histgrouporg")
                val groupUser = User(id = 54L, loginId = "histgroupuser", name = "histgroupuser")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 4L, user = groupUser, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 55L, owner = "testowner", name = "group-hist", vcs = "GIT", projectScope = ProjectScope.PROTECTED, isCodeAccessibleMemberOnly = true, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("histgroupuser", "password")
                val hPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "group-hist") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("histgroupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(55L, 54L) } returns false
                every { repositoryService.getRepository(groupProject) } returns hPlayRepo
                every { hPlayRepo.getRefNames() } returns emptyList()
                every { hPlayRepo.getNamedBranchNames() } returns emptyList()
                every { hPlayRepo.getTagNames() } returns emptyList()
                every { hPlayRepo.getHistory(0, 25, "main", null) } returns emptyList()

                mockMvc.perform(get("/testowner/group-hist/commits/main").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/history"))
            }

            it("멤버 전용 아님 + READ 권한 없는 비공개(PRIVATE) 프로젝트면 messageKey 없이 error/403을 반환해야 한다") {
                val privateProject = Project(id = 56L, owner = "testowner", name = "private-hist", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "private-hist") } returns Optional.of(privateProject)

                mockMvc.perform(get("/testowner/private-hist/commits/main"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
                    .andExpect(model().attributeDoesNotExist("messageKey"))
            }

            it("SVN 프로젝트의 빈 저장소면 code/nohead_svn 뷰를 반환해야 한다") {
                val svnProject = Project(id = 57L, owner = "testowner", name = "svn-hist", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                val svnPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-hist") } returns Optional.of(svnProject)
                every { repositoryService.getRepository(svnProject) } returns svnPlayRepo
                every { svnPlayRepo.getRefNames() } returns emptyList()
                every { svnPlayRepo.getNamedBranchNames() } returns emptyList()
                every { svnPlayRepo.getTagNames() } returns emptyList()
                every { svnPlayRepo.getHistory(0, 25, "trunk", null) } throws NoHeadException("no HEAD")

                mockMvc.perform(get("/testowner/svn-hist/commits/trunk"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead_svn"))
            }

            // 경로를 2세그먼트(src/main.kt)로 줘서 breadcrumbs 누적 로직의 cumulative.isEmpty()
            // true(첫 세그먼트)/false(이후 세그먼트) 분기를 한 테스트에서 모두 실행시킨다.
            it("경로가 있으면 breadcrumbs를 담고 commentThreadRepository로 경로별 댓글 수를 조회해야 한다") {
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "c1"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { playRepo.getHistory(0, 25, "main", "src/main.kt") } returns listOf(commit)
                every { commentThreadRepository.countByProjectAndCommitIdAndCodeRangePath(project, "c1", "src/main.kt") } returns 4L

                mockMvc.perform(get("/testowner/testproject/commits/main/src/main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/history"))
                    .andExpect(model().attribute("breadcrumbs", listOf("src" to "src", "main.kt" to "src/main.kt")))
                    .andExpect(model().attribute("commentCounts", mapOf("c1" to 4L)))
            }

            it("경로가 없으면(GIT) commentThreadRepository로 커밋별 댓글 수를 조회해야 한다") {
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "c2"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { playRepo.getHistory(0, 25, "main", null) } returns listOf(commit)
                every { commentThreadRepository.findByCommitIdOrderByCreatedDateDesc("c2") } returns emptyList()

                mockMvc.perform(get("/testowner/testproject/commits/main"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/history"))
                    .andExpect(model().attribute("commentCounts", mapOf("c2" to 0L)))
            }

            it("SVN 프로젝트면 commitCommentRepository로 커밋별 댓글 수를 조회해야 한다") {
                val svnProject = Project(id = 58L, owner = "testowner", name = "svn-hist2", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                val svnPlayRepo = mockk<PlayRepository>()
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "c3"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-hist2") } returns Optional.of(svnProject)
                every { repositoryService.getRepository(svnProject) } returns svnPlayRepo
                every { svnPlayRepo.getRefNames() } returns emptyList()
                every { svnPlayRepo.getNamedBranchNames() } returns emptyList()
                every { svnPlayRepo.getTagNames() } returns emptyList()
                every { svnPlayRepo.getHistory(0, 25, "trunk", null) } returns listOf(commit)
                every { commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(svnProject, "c3") } returns emptyList()

                mockMvc.perform(get("/testowner/svn-hist2/commits/trunk"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/history"))
                    .andExpect(model().attribute("commentCounts", mapOf("c3" to 0L)))
            }

            // isSvn = (vcs.uppercase() == "SUBVERSION" || vcs.uppercase() == "SVN") 판단에서
            // "SVN" 리터럴로만 저장된 프로젝트(두 번째 OR 절)도 SVN으로 인식돼야 한다.
            it("vcs 값이 'SVN' 리터럴이어도 SVN 프로젝트로 인식해 commitCommentRepository를 사용해야 한다") {
                val svnLiteralProject = Project(id = 59L, owner = "testowner", name = "svn-literal-hist", projectScope = ProjectScope.PUBLIC, vcs = "SVN")
                val svnPlayRepo = mockk<PlayRepository>()
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "c4"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-literal-hist") } returns Optional.of(svnLiteralProject)
                every { repositoryService.getRepository(svnLiteralProject) } returns svnPlayRepo
                every { svnPlayRepo.getRefNames() } returns emptyList()
                every { svnPlayRepo.getNamedBranchNames() } returns emptyList()
                every { svnPlayRepo.getTagNames() } returns emptyList()
                every { svnPlayRepo.getHistory(0, 25, "trunk", null) } returns listOf(commit)
                every { commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(svnLiteralProject, "c4") } returns emptyList()

                mockMvc.perform(get("/testowner/svn-literal-hist/commits/trunk"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/history"))
                    .andExpect(model().attribute("commentCounts", mapOf("c4" to 0L)))
            }

            // isSvn 계산에서 project.vcs 자체가 null인(VCS 미설정) 케이스 — GIT과 동일하게 처리돼야 한다.
            it("vcs가 null이면 SVN이 아닌 것으로 판단해 commentThreadRepository를 사용해야 한다") {
                val novcsProject = Project(id = 60L, owner = "testowner", name = "novcs-hist", projectScope = ProjectScope.PUBLIC, vcs = null)
                val novcsPlayRepo = mockk<PlayRepository>()
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "c5"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "novcs-hist") } returns Optional.of(novcsProject)
                every { repositoryService.getRepository(novcsProject) } returns novcsPlayRepo
                every { novcsPlayRepo.getRefNames() } returns emptyList()
                every { novcsPlayRepo.getNamedBranchNames() } returns emptyList()
                every { novcsPlayRepo.getTagNames() } returns emptyList()
                every { novcsPlayRepo.getHistory(0, 25, "main", null) } returns listOf(commit)
                every { commentThreadRepository.findByCommitIdOrderByCreatedDateDesc("c5") } returns emptyList()

                mockMvc.perform(get("/testowner/novcs-hist/commits/main"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/history"))
                    .andExpect(model().attribute("commentCounts", mapOf("c5" to 0L)))
            }

            it("page 파라미터가 전달되면 해당 페이지로 히스토리를 조회해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { playRepo.getHistory(2, 25, "main", null) } returns emptyList()

                mockMvc.perform(get("/testowner/testproject/commits/main").param("page", "2"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("page", 2))
            }

            it("/commits 루트로 요청하면 historyUntilHead를 통해 HEAD 브랜치 히스토리를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getTagNames() } returns emptyList()
                every { playRepo.getHistory(0, 25, "HEAD", null) } returns emptyList()

                mockMvc.perform(get("/testowner/testproject/commits"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/history"))
                    .andExpect(model().attribute("branch", "HEAD"))
            }
        }

        describe("GET /{owner}/{projectName}/commit/{commitId} — 추가 분기") {
            it("프로젝트를 찾을 수 없으면 error/404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "noexist") } returns Optional.empty()

                mockMvc.perform(get("/testowner/noexist/commit/abc"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("멤버 전용 + 비멤버·비그룹멤버 로그인 사용자는 error/403(messageKey 포함)을 반환해야 한다") {
                val memberOnlyProject = Project(id = 60L, owner = "testowner", name = "memberonly-commit", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val strangerUser = User(id = 61L, loginId = "commitstranger", name = "commitstranger")
                val strangerAuth = UsernamePasswordAuthenticationToken("commitstranger", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-commit") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("commitstranger") } returns Optional.of(strangerUser)
                every { projectUserRepository.existsByProjectIdAndUserId(60L, 61L) } returns false

                mockMvc.perform(get("/testowner/memberonly-commit/commit/abc").principal(strangerAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
                    .andExpect(model().attribute("messageKey", "error.forbidden.or.notfound"))
            }

            it("멤버 전용 프로젝트라도 프로젝트 멤버라면 정상적으로 code/diff를 반환해야 한다") {
                val memberProject = Project(id = 62L, owner = "testowner", name = "member-commit", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                val memberUser = User(id = 63L, loginId = "commitmember", name = "commitmember")
                val memberAuth = UsernamePasswordAuthenticationToken("commitmember", "password")
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "cm1"
                val cmPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "member-commit") } returns Optional.of(memberProject)
                every { userRepository.findByLoginId("commitmember") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(62L, 63L) } returns true
                every { repositoryService.getRepository(memberProject) } returns cmPlayRepo
                every { cmPlayRepo.getCommit("cm1") } returns commit
                every { cmPlayRepo.getParentCommitOf("cm1") } returns null
                every { cmPlayRepo.getRefNames() } returns emptyList()
                every { cmPlayRepo.getNamedBranchNames() } returns emptyList()
                every { cmPlayRepo.getDiff("cm1") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(memberProject, "cm1") } returns emptyList()
                every { watchService.isWatching(memberUser, ResourceType.COMMIT, "62:cm1") } returns false

                mockMvc.perform(get("/testowner/member-commit/commit/cm1").principal(memberAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/diff"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("멤버 전용 프로젝트라도 조직 멤버라면 정상적으로 code/diff를 반환해야 한다") {
                val groupOrg = Organization(id = 5L, name = "commitgrouporg")
                val groupUser = User(id = 64L, loginId = "commitgroupuser", name = "commitgroupuser")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 5L, user = groupUser, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 65L, owner = "testowner", name = "group-commit", vcs = "GIT", projectScope = ProjectScope.PROTECTED, isCodeAccessibleMemberOnly = true, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("commitgroupuser", "password")
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "cm2"
                val cmPlayRepo = mockk<PlayRepository>()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "group-commit") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("commitgroupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(65L, 64L) } returns false
                every { repositoryService.getRepository(groupProject) } returns cmPlayRepo
                every { cmPlayRepo.getCommit("cm2") } returns commit
                every { cmPlayRepo.getParentCommitOf("cm2") } returns null
                every { cmPlayRepo.getRefNames() } returns emptyList()
                every { cmPlayRepo.getNamedBranchNames() } returns emptyList()
                every { cmPlayRepo.getDiff("cm2") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(groupProject, "cm2") } returns emptyList()
                every { watchService.isWatching(groupUser, ResourceType.COMMIT, "65:cm2") } returns false

                mockMvc.perform(get("/testowner/group-commit/commit/cm2").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/diff"))
            }

            it("멤버 전용 아님 + READ 권한 없는 비공개(PRIVATE) 프로젝트면 messageKey 없이 error/403을 반환해야 한다") {
                val privateProject = Project(id = 66L, owner = "testowner", name = "private-commit", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "private-commit") } returns Optional.of(privateProject)

                mockMvc.perform(get("/testowner/private-commit/commit/abc"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
                    .andExpect(model().attributeDoesNotExist("messageKey"))
            }

            it("getCommit이 예외를 던지면 error.notfound.commit 메시지와 함께 error/404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getCommit("badcommit") } throws RuntimeException("boom")

                mockMvc.perform(get("/testowner/testproject/commit/badcommit"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
                    .andExpect(model().attribute("messageKey", "error.notfound.commit"))
            }

            it("getCommit이 예외 없이 null을 반환해도 error.notfound.commit 메시지와 함께 error/404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getCommit("nullcommit") } returns null

                mockMvc.perform(get("/testowner/testproject/commit/nullcommit"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
                    .andExpect(model().attribute("messageKey", "error.notfound.commit"))
            }

            it("getParentCommitOf가 예외를 던져도 parentCommit을 null로 두고 정상 진행해야 한다") {
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "cm3"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getCommit("cm3") } returns commit
                every { playRepo.getParentCommitOf("cm3") } throws RuntimeException("no parent")
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getDiff("cm3") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "cm3") } returns emptyList()

                mockMvc.perform(get("/testowner/testproject/commit/cm3"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/diff"))
                    .andExpect(model().attribute("parentCommit", nullValue()))
            }

            it("익명 사용자가 조회하고 부모 커밋도 정상 조회되면 isWatching은 false, parentCommit은 값이 있어야 한다") {
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "cm4"
                val parentCommit = mockk<Commit>(relaxed = true)
                every { parentCommit.getId() } returns "cm3"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getCommit("cm4") } returns commit
                every { playRepo.getParentCommitOf("cm4") } returns parentCommit
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getDiff("cm4") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "cm4") } returns emptyList()

                mockMvc.perform(get("/testowner/testproject/commit/cm4"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/diff"))
                    .andExpect(model().attribute("isWatching", false))
            }

            it("SVN 커밋이면 getPatch 결과를 patch 모델 속성에 담아 code/svnDiff를 반환해야 한다") {
                val svnProject = Project(id = 67L, owner = "testowner", name = "svn-commit", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                val svnPlayRepo = mockk<PlayRepository>()
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "5"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-commit") } returns Optional.of(svnProject)
                every { repositoryService.getRepository(svnProject) } returns svnPlayRepo
                every { svnPlayRepo.getCommit("5") } returns commit
                every { svnPlayRepo.getParentCommitOf("5") } returns null
                every { svnPlayRepo.getRefNames() } returns emptyList()
                every { svnPlayRepo.getNamedBranchNames() } returns emptyList()
                every { svnPlayRepo.getPatch("5") } returns "--- diff patch ---"
                every { commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(svnProject, "5") } returns emptyList()

                mockMvc.perform(get("/testowner/svn-commit/commit/5"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/svnDiff"))
                    .andExpect(model().attribute("patch", "--- diff patch ---"))
            }

            it("SVN 커밋에서 getPatch가 예외를 던지면 빈 문자열 patch로 대체해야 한다") {
                val svnProject = Project(id = 68L, owner = "testowner", name = "svn-commit2", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                val svnPlayRepo = mockk<PlayRepository>()
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "6"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-commit2") } returns Optional.of(svnProject)
                every { repositoryService.getRepository(svnProject) } returns svnPlayRepo
                every { svnPlayRepo.getCommit("6") } returns commit
                every { svnPlayRepo.getParentCommitOf("6") } returns null
                every { svnPlayRepo.getRefNames() } returns emptyList()
                every { svnPlayRepo.getNamedBranchNames() } returns emptyList()
                every { svnPlayRepo.getPatch("6") } throws RuntimeException("patch failed")
                every { commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(svnProject, "6") } returns emptyList()

                mockMvc.perform(get("/testowner/svn-commit2/commit/6"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/svnDiff"))
                    .andExpect(model().attribute("patch", ""))
            }

            // isSvn = (vcs.uppercase() == "SUBVERSION" || vcs.uppercase() == "SVN") 판단에서
            // "SVN" 리터럴로만 저장된 프로젝트(두 번째 OR 절)도 SVN으로 인식돼야 한다.
            it("vcs 값이 'SVN' 리터럴이어도 SVN 커밋으로 인식해 code/svnDiff를 반환해야 한다") {
                val svnLiteralProject = Project(id = 69L, owner = "testowner", name = "svn-literal-commit", projectScope = ProjectScope.PUBLIC, vcs = "SVN")
                val svnPlayRepo = mockk<PlayRepository>()
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "7"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "svn-literal-commit") } returns Optional.of(svnLiteralProject)
                every { repositoryService.getRepository(svnLiteralProject) } returns svnPlayRepo
                every { svnPlayRepo.getCommit("7") } returns commit
                every { svnPlayRepo.getParentCommitOf("7") } returns null
                every { svnPlayRepo.getRefNames() } returns emptyList()
                every { svnPlayRepo.getNamedBranchNames() } returns emptyList()
                every { svnPlayRepo.getPatch("7") } returns "patch-content"
                every { commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(svnLiteralProject, "7") } returns emptyList()

                mockMvc.perform(get("/testowner/svn-literal-commit/commit/7"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/svnDiff"))
                    .andExpect(model().attribute("patch", "patch-content"))
            }

            // isSvn 계산에서 project.vcs 자체가 null인(VCS 미설정) 케이스 — GIT과 동일하게 code/diff로 가야 한다.
            it("vcs가 null이면 SVN이 아닌 것으로 판단해 code/diff를 반환해야 한다") {
                val novcsProject = Project(id = 70L, owner = "testowner", name = "novcs-commit", projectScope = ProjectScope.PUBLIC, vcs = null)
                val novcsPlayRepo = mockk<PlayRepository>()
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "cm6"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "novcs-commit") } returns Optional.of(novcsProject)
                every { repositoryService.getRepository(novcsProject) } returns novcsPlayRepo
                every { novcsPlayRepo.getCommit("cm6") } returns commit
                every { novcsPlayRepo.getParentCommitOf("cm6") } returns null
                every { novcsPlayRepo.getRefNames() } returns emptyList()
                every { novcsPlayRepo.getNamedBranchNames() } returns emptyList()
                every { novcsPlayRepo.getDiff("cm6") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(novcsProject, "cm6") } returns emptyList()

                mockMvc.perform(get("/testowner/novcs-commit/commit/cm6"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/diff"))
            }

            it("GIT 커밋에서 getDiff가 예외를 던지면 error/404를 반환해야 한다") {
                val commit = mockk<Commit>(relaxed = true)
                every { commit.getId() } returns "cm5"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getCommit("cm5") } returns commit
                every { playRepo.getParentCommitOf("cm5") } returns null
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getNamedBranchNames() } returns emptyList()
                every { playRepo.getDiff("cm5") } throws RuntimeException("diff failed")
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "cm5") } returns emptyList()

                mockMvc.perform(get("/testowner/testproject/commit/cm5"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }
        }
    }
})
