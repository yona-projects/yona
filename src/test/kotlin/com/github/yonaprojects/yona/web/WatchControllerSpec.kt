package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.notification.UserProjectNotification
import com.github.yonaprojects.yona.domain.notification.UserProjectNotificationRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.Operation
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import org.springframework.security.core.Authentication
import com.github.yonaprojects.yona.domain.issue.Assignee
import io.mockk.slot
import org.hamcrest.Matchers

class WatchControllerSpec : DescribeSpec({
    val watchService = mockk<WatchService>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val userProjectNotificationRepository = mockk<UserProjectNotificationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val accessControl = mockk<AccessControl>()

    val watchController = WatchController(
        watchService = watchService,
        userRepository = userRepository,
        projectRepository = projectRepository,
        userProjectNotificationRepository = userProjectNotificationRepository,
        issueRepository = issueRepository,
        postingRepository = postingRepository,
        pullRequestRepository = pullRequestRepository,
        accessControl = accessControl
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(watchController).build()

    beforeTest {
        clearMocks(
            watchService,
            userRepository,
            projectRepository,
            userProjectNotificationRepository,
            issueRepository,
            postingRepository,
            pullRequestRepository,
            accessControl
        )
    }

    describe("WatchController API 및 뷰 렌더링 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val user1 = User(id = 10L, loginId = "user1", name = "유저1")
        val user2 = User(id = 20L, loginId = "user2", name = "유저2")
        val auth = mockk<Authentication>()

        describe("익명 사용자 및 비로그인 차단 검증") {
            it("로그인 정보(Authentication)가 없을 때 401 Unauthorized를 반환해야 한다") {
                mockMvc.perform(post("/watch")
                    .param("resource.type", "PROJECT")
                    .param("resource.id", "1"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("권한 체크 및 차단 검증") {
            it("프로젝트 읽기 권한이 없는 경우 403 Forbidden을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns false

                mockMvc.perform(post("/watch")
                    .principal(auth)
                    .param("resource.type", "PROJECT")
                    .param("resource.id", "1"))
                    .andExpect(status().isForbidden)
            }
        }

        describe("이슈/프로젝트 감시 등록/해제 API 동작 검증") {
            it("/watch 호출 시 정상적으로 프로젝트를 감시 등록해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.watch(user1, ResourceType.PROJECT, "1") } just Runs

                mockMvc.perform(post("/watch")
                    .principal(auth)
                    .param("resource.type", "PROJECT")
                    .param("resource.id", "1"))
                    .andExpect(status().isOk)

                verify(exactly = 1) { watchService.watch(user1, ResourceType.PROJECT, "1") }
            }

            it("/unwatch 호출 시 정상적으로 이슈 감시를 해제하고 302 Redirect 해야 한다") {
                val issue = Issue(id = 100L, number = 5L, title = "Test Issue", project = project)

                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { issueRepository.findById(100L) } returns Optional.of(issue)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.unwatch(user1, ResourceType.ISSUE_POST, "100") } just Runs

                mockMvc.perform(post("/unwatch")
                    .principal(auth)
                    .header("Referer", "/referred-page")
                    .param("resource.type", "ISSUE_POST")
                    .param("resource.id", "100"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/referred-page"))

                verify(exactly = 1) { watchService.unwatch(user1, ResourceType.ISSUE_POST, "100") }
            }

            // yona commit.asResource(project) 대응(P-템플릿 그룹10 #161 재검토) — resource.id는
            // "{project.id}:{commitId}" 합성 키. 이전에는 checkWatchPermission()에 COMMIT 케이스가
            // 없어 항상 400을 반환했다(TEMPLATE_BACKLOG.md 재감사로 발견).
            it("/watch에 resource.type=COMMIT을 넘기면 합성 키에서 프로젝트를 찾아 정상 감시 등록해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.watch(user1, ResourceType.COMMIT, "1:abcdef1234") } just Runs

                mockMvc.perform(post("/watch")
                    .principal(auth)
                    .param("resource.type", "COMMIT")
                    .param("resource.id", "1:abcdef1234"))
                    .andExpect(status().isOk)

                verify(exactly = 1) { watchService.watch(user1, ResourceType.COMMIT, "1:abcdef1234") }
            }

            it("/watch에 resource.type=COMMIT이지만 합성 키의 프로젝트가 없으면 404를 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/watch")
                    .principal(auth)
                    .param("resource.type", "COMMIT")
                    .param("resource.id", "999:abcdef1234"))
                    .andExpect(status().isNotFound)
            }

            it("/{owner}/{projectName}/watch 호출 시 해당 프로젝트를 감시 등록해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.watch(user1, ResourceType.PROJECT, "1") } just Runs

                mockMvc.perform(post("/owner/TestProj/watch")
                    .principal(auth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { watchService.watch(user1, ResourceType.PROJECT, "1") }
            }

            it("/{owner}/{projectName}/unwatch 호출 시 감시를 해제하고 연관 알림설정을 삭제해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.unwatch(user1, ResourceType.PROJECT, "1") } just Runs
                every { userProjectNotificationRepository.deleteByUserAndProject(user1, project) } just Runs

                mockMvc.perform(post("/owner/TestProj/unwatch")
                    .principal(auth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { watchService.unwatch(user1, ResourceType.PROJECT, "1") }
                verify(exactly = 1) { userProjectNotificationRepository.deleteByUserAndProject(user1, project) }
            }

            it("/watch/toggle/{projectId}/{notificationType} 호출 시 알림 설정을 정상 토글해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.isWatching(user1, ResourceType.PROJECT, "1") } returns true
                every { userProjectNotificationRepository.findByUserAndProjectAndNotificationType(user1, project, any()) } returns null
                every { userProjectNotificationRepository.save(any()) } returns mockk()

                mockMvc.perform(post("/watch/toggle/1/NEW_ISSUE")
                    .principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }

        describe("GET /-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/watchers") {
            it("type이 issues일 때 해당 이슈의 감시자 JSON 정보를 올바르게 반환해야 한다") {
                val issue = Issue(id = 100L, number = 5L, title = "Test Issue", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every {
                    watchService.findActualWatchers(any(), ResourceType.ISSUE_POST, "100", project.id)
                } returns setOf(user1, user2)

                mockMvc.perform(get("/-_-api/v1/owners/owner/projects/TestProj/posts/5/watchers")
                    .param("type", "issues"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.totalWatchers").value(2))
                    .andExpect(jsonPath("$.watchersInList").value(2))
                    .andExpect(jsonPath("$.watchers[0].name").value("유저1"))
                    .andExpect(jsonPath("$.watchers[0].url").value("/user/user1"))
            }

            // yona AbstractPosting.getWatchers()/Issue.getWatchers()의 Watch.findActualWatchers()
            // (작성자/담당자/투표자 + 명시적 Watch row + 프로젝트 감시자 합산) 대응 (P1-131). 명시적으로
            // Watch 행을 만든 적 없는 작성자/담당자/투표자도 baseWatchers로 넘겨져 감시자에 포함돼야 한다.
            it("명시적으로 감시 신청을 한 적 없는 작성자/담당자/투표자도 감시자 목록에 포함되어야 한다") {
                val author = User(id = 30L, loginId = "author1", name = "작성자1")
                val assigneeUser = User(id = 31L, loginId = "assignee1", name = "담당자1")
                val voter = User(id = 32L, loginId = "voter1", name = "투표자1")
                val issue = Issue(
                    id = 101L, number = 6L, title = "Test Issue 2", project = project,
                    authorId = 30L,
                    assignee = Assignee(id = 1L, user = assigneeUser, project = project),
                    voters = mutableSetOf(voter)
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns issue
                every { userRepository.findById(30L) } returns Optional.of(author)
                val baseWatchersSlot = slot<Set<User>>()
                every {
                    watchService.findActualWatchers(capture(baseWatchersSlot), ResourceType.ISSUE_POST, "101", project.id)
                } answers { baseWatchersSlot.captured }

                mockMvc.perform(get("/-_-api/v1/owners/owner/projects/TestProj/posts/6/watchers")
                    .param("type", "issues"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.totalWatchers").value(3))
                    .andExpect(jsonPath("$.watchers[*].name", Matchers.containsInAnyOrder("작성자1", "담당자1", "투표자1")))
            }

            it("type이 posts일 때 해당 게시글의 감시자 JSON 정보를 올바르게 반환해야 한다") {
                val posting = Posting(id = 200L, number = 3L, title = "Test Post", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { postingRepository.findByProjectAndNumber(project, 3L) } returns posting
                every {
                    watchService.findActualWatchers(any(), ResourceType.BOARD_POST, "200", project.id)
                } returns setOf(user2)

                mockMvc.perform(get("/-_-api/v1/owners/owner/projects/TestProj/posts/3/watchers")
                    .param("type", "posts"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.totalWatchers").value(1))
                    .andExpect(jsonPath("$.watchersInList").value(1))
                    .andExpect(jsonPath("$.watchers[0].name").value("유저2"))
                    .andExpect(jsonPath("$.watchers[0].url").value("/user/user2"))
            }
        }

        describe("GET /{owner}/{projectName}/watchers") {
            it("프로젝트 감시자 뷰와 감시자 목록을 모델에 전달하여 200 OK를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { watchService.findWatchers(ResourceType.PROJECT, "1") } returns setOf(user1)

                mockMvc.perform(get("/owner/TestProj/watchers"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("project"))
                    .andExpect(model().attributeExists("watchers"))
                    .andExpect(view().name("project/watchers"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuch/watchers")).andExpect(status().isNotFound)
            }
        }

        describe("getLoginUser 잔여 분기") {
            it("로그인 사용자를 찾을 수 없으면 401을 반환해야 한다") {
                every { auth.name } returns "ghost"
                every { userRepository.findByLoginId("ghost") } returns Optional.empty()

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "PROJECT").param("resource.id", "1")
                ).andExpect(status().isUnauthorized)
            }
        }

        describe("checkWatchPermission 리소스 타입별 잔여 분기") {
            it("PROJECT: 리소스 ID가 숫자가 아니면 400을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "PROJECT").param("resource.id", "notanumber")
                ).andExpect(status().isBadRequest)
            }

            it("PROJECT: 존재하지 않으면 404를 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "PROJECT").param("resource.id", "999")
                ).andExpect(status().isNotFound)
            }

            it("ISSUE_POST: 리소스 ID가 숫자가 아니면 400을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "ISSUE_POST").param("resource.id", "notanumber")
                ).andExpect(status().isBadRequest)
            }

            it("ISSUE_POST: 존재하지 않으면 404를 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { issueRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "ISSUE_POST").param("resource.id", "999")
                ).andExpect(status().isNotFound)
            }

            it("BOARD_POST: 정상적으로 게시글의 프로젝트를 찾아 감시 등록해야 한다") {
                val posting = Posting(id = 300L, number = 1L, title = "게시글", project = project)
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { postingRepository.findById(300L) } returns Optional.of(posting)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.watch(user1, ResourceType.BOARD_POST, "300") } just Runs

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "BOARD_POST").param("resource.id", "300")
                ).andExpect(status().isOk)
            }

            it("BOARD_POST: 리소스 ID가 숫자가 아니면 400을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "BOARD_POST").param("resource.id", "notanumber")
                ).andExpect(status().isBadRequest)
            }

            it("BOARD_POST: 존재하지 않으면 404를 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { postingRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "BOARD_POST").param("resource.id", "999")
                ).andExpect(status().isNotFound)
            }

            it("PULL_REQUEST: 정상적으로 toProject를 찾아 감시 등록해야 한다") {
                val pr = PullRequest(
                    id = 400L, number = 1L, toProject = project, fromProject = project,
                    contributor = user1
                )
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { pullRequestRepository.findById(400L) } returns Optional.of(pr)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.watch(user1, ResourceType.PULL_REQUEST, "400") } just Runs

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "PULL_REQUEST").param("resource.id", "400")
                ).andExpect(status().isOk)
            }

            it("PULL_REQUEST: 리소스 ID가 숫자가 아니면 400을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "PULL_REQUEST").param("resource.id", "notanumber")
                ).andExpect(status().isBadRequest)
            }

            it("PULL_REQUEST: 존재하지 않으면 404를 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { pullRequestRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "PULL_REQUEST").param("resource.id", "999")
                ).andExpect(status().isNotFound)
            }

            it("COMMIT: 합성 키에 콜론이 없으면 400을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "COMMIT").param("resource.id", "nocolonhere")
                ).andExpect(status().isBadRequest)
            }

            it("지원하지 않는 리소스 타입이면 400을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)

                mockMvc.perform(
                    post("/watch").principal(auth).param("resource.type", "COMMENT_THREAD").param("resource.id", "1")
                ).andExpect(status().isBadRequest)
            }
        }

        describe("watchProject/unwatchProject 잔여 분기") {
            it("watchProject: 존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                mockMvc.perform(post("/owner/NoSuch/watch").principal(auth)).andExpect(status().isNotFound)
            }

            it("watchProject: 권한이 없으면 403을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns false

                mockMvc.perform(post("/owner/TestProj/watch").principal(auth)).andExpect(status().isForbidden)
            }

            it("unwatchProject: 존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                mockMvc.perform(post("/owner/NoSuch/unwatch").principal(auth)).andExpect(status().isNotFound)
            }

            it("unwatchProject: 권한이 없으면 403을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns false

                mockMvc.perform(post("/owner/TestProj/unwatch").principal(auth)).andExpect(status().isForbidden)
            }
        }

        describe("toggleProjectNotification 잔여 분기") {
            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/watch/toggle/999/NEW_ISSUE").principal(auth)).andExpect(status().isNotFound)
            }

            it("권한이 없으면 403을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns false

                mockMvc.perform(post("/watch/toggle/1/NEW_ISSUE").principal(auth)).andExpect(status().isForbidden)
            }

            it("프로젝트를 감시하고 있지 않으면 400을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.isWatching(user1, ResourceType.PROJECT, "1") } returns false

                mockMvc.perform(post("/watch/toggle/1/NEW_ISSUE").principal(auth)).andExpect(status().isBadRequest)
            }

            it("잘못된 알림 타입이면 400을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.isWatching(user1, ResourceType.PROJECT, "1") } returns true

                mockMvc.perform(post("/watch/toggle/1/NOT_A_REAL_TYPE").principal(auth)).andExpect(status().isBadRequest)
            }

            it("NEW_COMMENT 타입은 기본적으로 알림이 꺼져있어야(isNotifiedByDefault=false) 새 설정 생성 시 allowed=true로 저장되어야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.isWatching(user1, ResourceType.PROJECT, "1") } returns true
                every {
                    userProjectNotificationRepository.findByUserAndProjectAndNotificationType(user1, project, EventType.NEW_COMMENT)
                } returns null
                val savedSlot = slot<UserProjectNotification>()
                every { userProjectNotificationRepository.save(capture(savedSlot)) } returns mockk()

                mockMvc.perform(post("/watch/toggle/1/NEW_COMMENT").principal(auth)).andExpect(status().isOk)

                savedSlot.captured.allowed shouldBe true
            }

            it("기존 알림 설정 토글 후 allowed가 기본값과 같아지면 설정을 삭제해야 한다") {
                val existing = UserProjectNotification(
                    id = 500L, user = user1, project = project,
                    notificationType = EventType.NEW_ISSUE, allowed = false
                )
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.isWatching(user1, ResourceType.PROJECT, "1") } returns true
                every {
                    userProjectNotificationRepository.findByUserAndProjectAndNotificationType(user1, project, EventType.NEW_ISSUE)
                } returns existing
                every { userProjectNotificationRepository.delete(existing) } just Runs

                mockMvc.perform(post("/watch/toggle/1/NEW_ISSUE").principal(auth)).andExpect(status().isOk)

                verify(exactly = 1) { userProjectNotificationRepository.delete(existing) }
            }

            it("기존 알림 설정 토글 후 allowed가 기본값과 달라지면 설정을 저장해야 한다") {
                val existing = UserProjectNotification(
                    id = 501L, user = user1, project = project,
                    notificationType = EventType.NEW_ISSUE, allowed = true
                )
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.isWatching(user1, ResourceType.PROJECT, "1") } returns true
                every {
                    userProjectNotificationRepository.findByUserAndProjectAndNotificationType(user1, project, EventType.NEW_ISSUE)
                } returns existing
                every { userProjectNotificationRepository.save(existing) } returns existing

                mockMvc.perform(post("/watch/toggle/1/NEW_ISSUE").principal(auth)).andExpect(status().isOk)

                verify(exactly = 1) { userProjectNotificationRepository.save(existing) }
                existing.allowed shouldBe false
            }
        }

        describe("getWatchers 잔여 분기") {
            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                mockMvc.perform(
                    get("/-_-api/v1/owners/owner/projects/NoSuch/posts/1/watchers").param("type", "issues")
                ).andExpect(status().isNotFound)
            }

            it("type이 issues인데 이슈가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(
                    get("/-_-api/v1/owners/owner/projects/TestProj/posts/999/watchers").param("type", "issues")
                ).andExpect(status().isNotFound)
            }

            it("type이 posts인데 게시글이 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { postingRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(
                    get("/-_-api/v1/owners/owner/projects/TestProj/posts/999/watchers").param("type", "posts")
                ).andExpect(status().isNotFound)
            }

            it("지원하지 않는 type이면 빈 감시자 목록을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(
                    get("/-_-api/v1/owners/owner/projects/TestProj/posts/1/watchers").param("type", "unknown")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.totalWatchers").value(0))
            }

            it("게시글 작성자가 있으면 감시자 baseWatchers에 포함되어야 한다") {
                val postAuthor = User(id = 40L, loginId = "postauthor1", name = "글쓴이1")
                val posting = Posting(id = 201L, number = 4L, title = "글", project = project, authorId = 40L)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { postingRepository.findByProjectAndNumber(project, 4L) } returns posting
                every { userRepository.findById(40L) } returns Optional.of(postAuthor)
                val baseSlot = slot<Set<User>>()
                every {
                    watchService.findActualWatchers(capture(baseSlot), ResourceType.BOARD_POST, "201", project.id)
                } answers { baseSlot.captured }

                mockMvc.perform(
                    get("/-_-api/v1/owners/owner/projects/TestProj/posts/4/watchers").param("type", "posts")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.watchers[0].name").value("글쓴이1"))
            }
        }
    }
})


