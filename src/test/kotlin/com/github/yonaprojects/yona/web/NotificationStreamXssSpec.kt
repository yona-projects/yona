package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// index/partial_notifications.html은 알림 메시지를 th:utext(비이스케이프)로 렌더링한다. v1.6은
// 같은 자리에서 @Html(HtmlUtil.defaultSanitize(...))로 반드시 새니타이즈를 거쳤는데
// (app/views/index/partial_notifications.scala.html), 포팅본은 새니타이즈 없이 이벤트의
// newValue(댓글 등 사용자가 직접 쓴 원문, NotificationEvent.newValue)를 그대로 utext에 넣는다.
// NEW_COMMENT 알림의 newValue는 댓글 작성자가 그대로 입력한 텍스트라 <script> 등을 포함할 수 있어
// 저장형 XSS로 이어진다.
@Transactional
class NotificationStreamXssSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    private fun seedMaliciousCommentNotification(): User {
        val receiver = userRepository.findByLoginId("noti-xss-receiver").orElseGet {
            userRepository.save(User(loginId = "noti-xss-receiver", name = "알림수신자", email = "noti-xss-receiver@yona.io"))
        }
        val payload = "<script>alert(document.cookie)</script>댓글내용"
        val event = NotificationEvent(
            title = "악성 댓글 알림",
            resourceType = ResourceType.ISSUE_COMMENT,
            resourceId = "1",
            eventType = EventType.NEW_COMMENT,
            newValue = payload,
            oldValue = null
        )
        event.receivers.add(receiver)
        notificationEventRepository.save(event)
        return receiver
    }

    private fun userDetailsOf(user: User) = YonaUserDetails(
        id = user.id!!,
        loginId = user.loginId,
        passwordVal = "hashed",
        passwordSalt = "salt",
        authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
    )

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("알림 스트림 XSS 방어") {
            it("댓글 원문에 스크립트 태그가 있어도 홈 알림 스트림에는 그대로 노출되면 안 된다") {
                val receiver = seedMaliciousCommentNotification()

                val body = mockMvc.perform(
                    get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetailsOf(receiver)))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body.contains("<script>alert(document.cookie)</script>") shouldBe false
                // 새니타이즈로 태그만 제거되고 본문 텍스트는 유지돼야 한다(legacy와 동일 동작).
                body.contains("댓글내용") shouldBe true
            }

            it("/notifications 페이지에도 동일하게 스크립트 태그가 노출되면 안 된다") {
                val receiver = seedMaliciousCommentNotification()

                val body = mockMvc.perform(
                    get("/notifications").with(SecurityMockMvcRequestPostProcessors.user(userDetailsOf(receiver)))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body.contains("<script>alert(document.cookie)</script>") shouldBe false
            }
        }
    }
}
