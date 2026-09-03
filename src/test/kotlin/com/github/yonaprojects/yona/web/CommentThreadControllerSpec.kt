package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread.ThreadState
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.security.core.Authentication
import java.util.Optional
import io.mockk.clearMocks

// yona CommentThreadApp.updateState()의 AccessControl.isAllowed(..., REOPEN/CLOSE) 체크 대응 (P0-18).
// open()/close()에 권한 체크가 전혀 없어 무관한 사용자가 임의 프로젝트의 리뷰 스레드를 열고/닫을 수
// 있던 회귀를 검증한다.
class CommentThreadControllerSpec : DescribeSpec({
    val codeReviewService = mockk<CodeReviewService>()
    val userRepository = mockk<UserRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val accessControl = mockk<AccessControl>()

    val commentThreadController = CommentThreadController(
        codeReviewService,
        userRepository,
        commentThreadRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(commentThreadController).build()

    beforeTest {
        clearMocks(codeReviewService, userRepository, commentThreadRepository, accessControl)
    }

    describe("CommentThreadController TDD 검증") {
        val user = mockk<User>(relaxed = true)
        val project = mockk<Project>(relaxed = true)
        val thread = mockk<CommentThread>(relaxed = true)
        val authentication = mockk<Authentication>()

        it("스레드 오픈 API 호출 시 REOPEN 권한이 있으면 200 OK와 상태 변경 영속화가 일어나야 한다") {
            every { authentication.name } returns "mockuser"
            every { userRepository.findByLoginId("mockuser") } returns Optional.of(user)
            every { commentThreadRepository.findById(100L) } returns Optional.of(thread)
            every { thread.project } returns project
            every { accessControl.isAllowed(user, project, thread, Operation.REOPEN) } returns true
            every { codeReviewService.updateThreadState(100L, ThreadState.OPEN, user) } returns thread

            mockMvc.perform(
                post("/threads/100/open").principal(authentication)
            )
                .andExpect(status().isOk)

            verify(exactly = 1) {
                codeReviewService.updateThreadState(100L, ThreadState.OPEN, user)
            }
        }

        it("스레드 닫기 API 호출 시 CLOSE 권한이 있으면 200 OK와 상태 변경 영속화가 일어나야 한다") {
            every { authentication.name } returns "mockuser"
            every { userRepository.findByLoginId("mockuser") } returns Optional.of(user)
            every { commentThreadRepository.findById(100L) } returns Optional.of(thread)
            every { thread.project } returns project
            every { accessControl.isAllowed(user, project, thread, Operation.CLOSE) } returns true
            every { codeReviewService.updateThreadState(100L, ThreadState.CLOSED, user) } returns thread

            mockMvc.perform(
                post("/threads/100/close").principal(authentication)
            )
                .andExpect(status().isOk)

            verify(exactly = 1) {
                codeReviewService.updateThreadState(100L, ThreadState.CLOSED, user)
            }
        }

        it("REOPEN 권한이 없으면 403을 반환하고 상태 변경을 수행하지 않아야 한다") {
            every { authentication.name } returns "mockuser"
            every { userRepository.findByLoginId("mockuser") } returns Optional.of(user)
            every { commentThreadRepository.findById(100L) } returns Optional.of(thread)
            every { thread.project } returns project
            every { accessControl.isAllowed(user, project, thread, Operation.REOPEN) } returns false

            mockMvc.perform(
                post("/threads/100/open").principal(authentication)
            )
                .andExpect(status().isForbidden)

            verify(exactly = 0) {
                codeReviewService.updateThreadState(any(), any(), any())
            }
        }

        it("CLOSE 권한이 없으면 403을 반환하고 상태 변경을 수행하지 않아야 한다") {
            every { authentication.name } returns "mockuser"
            every { userRepository.findByLoginId("mockuser") } returns Optional.of(user)
            every { commentThreadRepository.findById(100L) } returns Optional.of(thread)
            every { thread.project } returns project
            every { accessControl.isAllowed(user, project, thread, Operation.CLOSE) } returns false

            mockMvc.perform(
                post("/threads/100/close").principal(authentication)
            )
                .andExpect(status().isForbidden)

            verify(exactly = 0) {
                codeReviewService.updateThreadState(any(), any(), any())
            }
        }

        it("존재하지 않는 스레드 id면 404를 반환해야 한다") {
            every { authentication.name } returns "mockuser"
            every { userRepository.findByLoginId("mockuser") } returns Optional.of(user)
            every { commentThreadRepository.findById(999L) } returns Optional.empty()

            mockMvc.perform(
                post("/threads/999/open").principal(authentication)
            )
                .andExpect(status().isNotFound)

            verify(exactly = 0) {
                codeReviewService.updateThreadState(any(), any(), any())
            }
        }

        it("비로그인 사용자는 401을 반환해야 한다") {
            mockMvc.perform(post("/threads/100/open"))
                .andExpect(status().isUnauthorized)

            verify(exactly = 0) {
                codeReviewService.updateThreadState(any(), any(), any())
            }
        }

        // authentication?.let{}?.orElse(null) ?: return 401 — 인증 정보는 있지만 DB에서 사용자를
        // 찾지 못하는 경우(탈퇴 등)도 401이어야 한다. 위쪽 "비로그인" 테스트는 authentication 자체가
        // 없는 경우만 다뤘다.
        it("인증 정보는 있지만 DB에 사용자가 없으면 401을 반환해야 한다") {
            every { authentication.name } returns "ghost"
            every { userRepository.findByLoginId("ghost") } returns Optional.empty()

            mockMvc.perform(
                post("/threads/100/open").principal(authentication)
            )
                .andExpect(status().isUnauthorized)

            verify(exactly = 0) {
                codeReviewService.updateThreadState(any(), any(), any())
            }
        }

        // thread.project ?: return 404 — 스레드는 존재하지만 project가 없으면(연관관계 끊김 등) 404여야 한다.
        it("스레드는 존재하지만 project가 없으면 404를 반환해야 한다") {
            every { authentication.name } returns "mockuser"
            every { userRepository.findByLoginId("mockuser") } returns Optional.of(user)
            every { commentThreadRepository.findById(100L) } returns Optional.of(thread)
            every { thread.project } returns null

            mockMvc.perform(
                post("/threads/100/open").principal(authentication)
            )
                .andExpect(status().isNotFound)

            verify(exactly = 0) {
                codeReviewService.updateThreadState(any(), any(), any())
            }
        }
    }
})
