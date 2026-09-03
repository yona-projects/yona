package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.Optional
import io.mockk.clearMocks

class NotificationControllerSpec : DescribeSpec({
    val notificationEventRepository = mockk<NotificationEventRepository>()
    val userRepository = mockk<UserRepository>()
    val notificationController = NotificationController(notificationEventRepository, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build()

    beforeTest {
        clearMocks(notificationEventRepository, userRepository)
    }

    describe("NotificationController 웹 API 테스트") {
        val testUser = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")
        val auth = UsernamePasswordAuthenticationToken("gildong", "password")

        describe("GET /api/notifications") {
            it("로그인한 수신 유저에 속한 알림 이벤트 리스트를 DTO 형태로 반환해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                
                val event = NotificationEvent(
                    id = 100L,
                    title = "새 이슈 등록알림",
                    senderId = 2L,
                    created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST,
                    resourceId = "1",
                    eventType = EventType.NEW_ISSUE
                )
                val pageable = PageRequest.of(0, 10)
                every { notificationEventRepository.findByReceiver(testUser, pageable) } returns PageImpl(listOf(event))

                // When & Then
                mockMvc.perform(
                    get("/api/notifications")
                        .param("from", "0")
                        .param("size", "10")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].id").value(100))
                    .andExpect(jsonPath("$[0].title").value("새 이슈 등록알림"))
                    .andExpect(jsonPath("$[0].resourceType").value("ISSUE_POST"))
                    .andExpect(jsonPath("$[0].eventType").value("NEW_ISSUE"))
            }

            it("인증 정보가 없을 경우 401 권한 없음 에러를 리턴해야 한다") {
                // When & Then
                mockMvc.perform(
                    get("/api/notifications")
                        .param("from", "0")
                        .param("size", "10")
                )
                    .andExpect(status().isUnauthorized)
            }

            // getLoginUser()의 orElseThrow — 인증 정보는 있지만 DB에 해당 로그인ID의 유저가 없는 경우.
            it("인증은 됐지만 DB에 사용자가 없으면 401을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.empty()

                mockMvc.perform(
                    get("/api/notifications")
                        .param("from", "0")
                        .param("size", "10")
                        .principal(auth)
                )
                    .andExpect(status().isUnauthorized)
            }

            // size<=0이면 pageIndex=0, pageSize=10 기본값으로 폴백해야 한다.
            it("size가 0 이하이면 기본 페이지 크기(10)로 조회해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                val pageable = PageRequest.of(0, 10)
                every { notificationEventRepository.findByReceiver(testUser, pageable) } returns PageImpl(emptyList())

                mockMvc.perform(
                    get("/api/notifications")
                        .param("from", "5")
                        .param("size", "0")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
            }

            // event.id가 null이면 응답 DTO의 id는 기본값 0L이어야 한다.
            it("이벤트 id가 없으면 응답 id는 0이어야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                val event = NotificationEvent(
                    id = null,
                    title = "제목", senderId = 2L, created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST, resourceId = "1", eventType = EventType.NEW_ISSUE
                )
                val pageable = PageRequest.of(0, 10)
                every { notificationEventRepository.findByReceiver(testUser, pageable) } returns PageImpl(listOf(event))

                mockMvc.perform(
                    get("/api/notifications")
                        .param("from", "0")
                        .param("size", "10")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].id").value(0))
            }
        }
    }
})
