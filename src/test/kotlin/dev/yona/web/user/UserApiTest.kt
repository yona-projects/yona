package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 구성원 추가 입력란의 자동 완성이 쓰는 사용자 검색(`GET /users`)을 검증한다.
 *
 * 원본: `app/controllers/UserApp.java:86-123`.
 */
@AutoConfigureMockMvc
@Transactional
class UserApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private var sequence = 0

    private fun newUser(loginId: String, name: String = loginId, state: UserState = UserState.ACTIVE): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = name, email = "${loginId}${++sequence}@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun search(query: String?, referer: String? = "http://localhost/o/p/members", accept: String? = "application/json") =
        mockMvc.perform(
            get("/users").apply { query?.let { param("query", it) } }
                .apply { referer?.let { header(HttpHeaders.REFERER, it) } }.apply { accept?.let { accept(it) } }
        )

    // --- 사용자 검색 ---

    @Test
    fun `이름이나 아이디에 검색어가 들어 있는 사용자를 돌려준다`() {
        newUser("searchable-one", "철수")
        newUser("other", "Searchable Two")
        newUser("unrelated", "영희")
        search("searchable")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
        search("철수").andExpect(jsonPath("$[0].loginId").value("searchable-one"))
    }

    @Test
    fun `각 항목은 아바타와 이름과 아이디를 담은 HTML 조각과 아이디이다`() {
        newUser("mention-me", "멘션")
        val body = search("mention-me").andReturn().response.contentAsString
        assertEquals(true, body.contains("\"loginId\":\"mention-me\""))
        assertEquals(true, body.contains("<img class='mention_image'"))
        assertEquals(true, body.contains("<b class='mention_name'>멘션</b>"))
        assertEquals(true, body.contains("<span class='mention_username'> @mention-me</span>"))
    }

    @Test
    fun `삭제된 사용자는 제외한다`() {
        newUser("gone-user", state = UserState.DELETED)
        search("gone-user").andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `검색어가 비었으면 빈 목록이다`() {
        search("").andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0))
        search(null).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `열 개를 넘으면 열 개만 돌려주고 전체 개수를 알린다`() {
        (1..12).forEach { newUser("bulk-user-$it") }
        search("bulk-user")
            .andExpect(jsonPath("$.length()").value(10))
            .andExpect(header().string("Content-Range", "items 10/12"))
    }

    @Test
    fun `구성원 화면에서 JSON으로 부르지 않으면 406이다`() {
        search("a", referer = "http://localhost/o/p/other").andExpect(status().isNotAcceptable)
        search("a", referer = null).andExpect(status().isNotAcceptable)
        search("a", accept = "text/html").andExpect(status().isNotAcceptable)
    }
}
