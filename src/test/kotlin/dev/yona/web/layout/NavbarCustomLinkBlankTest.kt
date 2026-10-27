package dev.yona.web.layout

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 내비게이션의 사용자 정의 링크 이름이 공백뿐이면 링크를 그리지 않는지 검증한다.
 *
 * 원본: `app/views/common/usermenu.scala.html:79`가 `StringUtils.isBlank`로 거른다(`application.navbar.custom.link.name`).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["yona.navbar-custom-link-name=\\u0020\\u0020", "yona.navbar-custom-link-url=/custom"])
class NavbarCustomLinkBlankTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var properties: dev.yona.config.YonaProperties

    @Test
    fun `이름이 공백뿐이면 로그인한 사용자에게도 사용자 정의 링크를 그리지 않는다`() {
        assertTrue(properties.navbarCustomLinkName.length == 2, "속성에 공백 두 칸이 전달되지 않았다: [${properties.navbarCustomLinkName}]")
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(User(loginId = "navb", name = "navb", email = "navb@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "navb").param("password", "1234"))
        val body = mockMvc.perform(get("/projects").session(session)).andReturn().response.contentAsString
        assertTrue(body.contains("gnb-usermenu"), "사용자 메뉴가 그려지지 않아 이 시험이 의미가 없다")
        assertFalse(body.contains("href=\"/custom\""), "공백뿐인 이름의 링크가 그려졌다")
    }
}
