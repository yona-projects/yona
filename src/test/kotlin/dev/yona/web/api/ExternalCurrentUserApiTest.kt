package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenRepository
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ApiTokenSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 토큰 주인 조회 REST(로드맵 X-37i, `decisions.md` D-52, GitHub의 `GET /user`)를 검증한다. CLI는 로그인 직후 이 경로로 토큰 주인의 로그인 ID를 알아낸다.
 * Git HTTP 인증은 사용자 이름이 토큰 주인의 로그인 ID와 같아야 하므로, Git 자격 증명 도우미가 이 값을 쓴다. 범위가 하나도 필요 없고 토큰만 있으면 된다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalCurrentUserApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var apiTokens: ApiTokenRepository

    private var sequence = 0

    private fun newUser(siteAdmin: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "cu$n", name = "cu$n", email = "cu$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
            .also { if (siteAdmin) siteAdmins.save(SiteAdmin(admin = it)) }
    }

    private fun json(token: String?) = mockMvc.perform(get("/-_-api/v1/user").apply { token?.let { header("Authorization", "token $it") } }.accept(MediaType.APPLICATION_JSON))

    @Test
    fun `토큰 주인의 로그인 ID와 이름과 이메일과 토큰의 범위를 준다`() {
        val user = newUser(); ApiTokenSupport.grant(apiTokens, user, "cut-token-1", listOf("read:org"))
        json("cut-token-1").andExpect(status().isOk).andExpect(jsonPath("$.loginId").value(user.loginId)).andExpect(jsonPath("$.name").value(user.name)).andExpect(jsonPath("$.email").value(user.email))
            .andExpect(jsonPath("$.siteAdmin").value(false)).andExpect(jsonPath("$.scopes[0]").value("read:org")).andExpect(jsonPath("$.scopes.length()").value(1))
    }

    @Test
    fun `사이트 관리자는 siteAdmin이 참이다`() {
        val user = newUser(siteAdmin = true); ApiTokenSupport.grant(apiTokens, user, "cut-token-2")
        json("cut-token-2").andExpect(status().isOk).andExpect(jsonPath("$.siteAdmin").value(true))
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        json(null).andExpect(status().isUnauthorized)
        json("not-a-token").andExpect(status().isUnauthorized)
    }
}
