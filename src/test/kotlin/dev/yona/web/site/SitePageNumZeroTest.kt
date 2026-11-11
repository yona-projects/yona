package dev.yona.web.site

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 사이트 사용자 목록과 내 이슈에 `pageNum=0`이 오면 첫 쪽을 보여 준다(500이 아니다).
 * 없는 사용자에 대한 조작이 `/sites/userList?pageNum=0`으로 되돌려 보내므로(`SiteUserController`) 이 값은 실제로 온다.
 * 사용자 목록과 내 이슈가 `pageNum - 1`을 그대로 넘겨 음수 쪽 번호가 되던 것을 막는다(프로젝트 목록과 게시판 목록은 이미 0 아래로 내리지 않고, 글 목록과 이슈 목록은 0이면 400이다).
 */
@AutoConfigureMockMvc
@Transactional
class SitePageNumZeroTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository

    private fun adminSession(): MockHttpSession {
        val salt = LegacyPasswordHasher.generateSalt()
        val admin: User = users.save(User(loginId = "pz1", name = "pz1", email = "pz1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        siteAdmins.save(SiteAdmin(admin = admin))
        return MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "pz1").param("password", "1234")) }
    }

    private fun assertOk(path: String) {
        mockMvc.perform(get(path).session(adminSession()).param("pageNum", "0")).andExpect(status().isOk)
    }

    @Test
    fun `사용자 목록은 pageNum 0이어도 열린다`() = assertOk("/sites/userList")

    @Test
    fun `글 목록과 이슈 목록은 pageNum 0이면 400이다 (v1_6과 같다, 아래로 내리지 않는다)`() {
        val session = adminSession()
        mockMvc.perform(get("/sites/postList").session(session).param("pageNum", "0")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/sites/issueList").session(session).param("pageNum", "0")).andExpect(status().isBadRequest)
    }

    @Test
    fun `내 이슈는 pageNum 0이어도 열린다`() = assertOk("/user/issues")
}
