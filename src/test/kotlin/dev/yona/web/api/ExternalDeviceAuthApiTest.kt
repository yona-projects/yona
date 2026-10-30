package dev.yona.web.api

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.ApiTokenRepository
import dev.yona.domain.deviceauth.DeviceAuthorizationRepository
import dev.yona.domain.deviceauth.DeviceAuthorizationService
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 기기 인증(Device Flow) REST(로드맵 X-37i, `decisions.md` D-52, RFC 8628)를 검증한다. CLI가 `POST /oauth/device/code`로 기기 코드와 사용자 코드를 받고, 사용자가 웹에서 승인하면
 * `POST /oauth/token`으로 개인용 API 토큰을 받는다. 두 경로 모두 토큰 없이 부른다. 오류는 RFC 8628의 `error` 값으로 400에 담긴다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalDeviceAuthApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var service: DeviceAuthorizationService
    @Autowired private lateinit var devices: DeviceAuthorizationRepository
    @Autowired private lateinit var apiTokens: ApiTokenRepository

    private val mapper = JsonMapper.builder().build()
    private var sequence = 0

    private fun newUser(siteAdmin: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "dv$n", name = "dv$n", email = "dv$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (siteAdmin) siteAdmins.save(SiteAdmin(admin = user))
        return user
    }

    private fun requestCode(vararg params: Pair<String, String>): ResultActions =
        mockMvc.perform(post("/-_-api/v1/oauth/device/code").apply { params.forEach { (k, v) -> param(k, v) } }.accept(MediaType.APPLICATION_JSON))

    private fun poll(deviceCode: String?, grantType: String? = DeviceAuthorizationService.GRANT_TYPE): ResultActions =
        mockMvc.perform(post("/-_-api/v1/oauth/token").apply { grantType?.let { param("grant_type", it) }; deviceCode?.let { param("device_code", it) } }.accept(MediaType.APPLICATION_JSON))

    private fun issue(vararg params: Pair<String, String>): Pair<String, String> {
        val body = mapper.readTree(requestCode(*params).andExpect(status().isOk).andReturn().response.contentAsString)
        return body.get("device_code").asString() to body.get("user_code").asString()
    }

    // --- 코드 발급 ---

    @Test
    fun `기기 코드를 토큰 없이 발급하고 사용자 코드와 확인 주소와 만료와 간격을 준다`() {
        requestCode("client_name" to "yona-cli on laptop").andExpect(status().isOk)
            .andExpect(jsonPath("$.device_code").isString).andExpect(jsonPath("$.user_code").value(org.hamcrest.Matchers.matchesPattern("[A-Z]{4}-[A-Z]{4}")))
            .andExpect(jsonPath("$.verification_uri").value(org.hamcrest.Matchers.endsWith("/users/device")))
            .andExpect(jsonPath("$.verification_uri_complete").value(org.hamcrest.Matchers.containsString("/users/device?user_code=")))
            .andExpect(jsonPath("$.expires_in").value(900)).andExpect(jsonPath("$.interval").value(5))
        assertTrue(devices.count() >= 1)
    }

    @Test
    fun `scope를 주지 않으면 사이트 관리자 범위를 뺀 모든 일반 범위를 요청한 것으로 본다`() {
        val (device, _) = issue()
        val stored = devices.findAll().single { true }
        assertEquals(ApiScopes.selectable(false).toSet(), ApiScopes.parse(stored.scopes))
        assertTrue(device.length >= 32)
    }

    @Test
    fun `모르는 범위는 invalid_scope이다`() {
        requestCode("scope" to "repo nothing").andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_scope"))
    }

    // --- 토큰 교환 ---

    @Test
    fun `승인 전에는 authorization_pending이고 너무 빨리 다시 부르면 slow_down이다`() {
        val (device, _) = issue()
        poll(device).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("authorization_pending"))
        poll(device).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("slow_down"))
    }

    @Test
    fun `승인하면 토큰을 한 번만 주고 그 토큰으로 REST를 쓸 수 있다`() {
        val user = newUser()
        val (device, userCode) = issue("client_name" to "yona-cli on laptop", "scope" to "repo read:org")
        service.approve(requireNotNull(service.findByUserCode(userCode)), user)
        val body = mapper.readTree(poll(device).andExpect(status().isOk).andExpect(jsonPath("$.token_type").value("bearer")).andReturn().response.contentAsString)
        val token = body.get("access_token").asString()
        assertTrue(token.startsWith("yona_pat_"))
        assertEquals(setOf("repo", "read:org"), body.get("scope").asString().split(" ").toSet())
        assertEquals("Yona CLI: yona-cli on laptop", apiTokens.findByUserIdOrderByCreatedDateDescIdDesc(user.id!!).single().name)
        mockMvc.perform(get("/-_-api/v1/user/status").header("Authorization", "token $token")).andExpect(status().isOk)
        poll(device).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
    }

    @Test
    fun `사이트 관리자 범위는 사이트 관리자가 승인할 때만 토큰에 들어간다`() {
        val plain = newUser(); val manager = newUser(siteAdmin = true)
        val (deviceA, codeA) = issue("scope" to "repo site_admin")
        service.approve(requireNotNull(service.findByUserCode(codeA)), plain)
        assertEquals("repo", poll(deviceA).andExpect(status().isOk).andReturn().response.contentAsString.let { mapper.readTree(it).get("scope").asString() })
        val (deviceB, codeB) = issue("scope" to "repo site_admin")
        service.approve(requireNotNull(service.findByUserCode(codeB)), manager)
        assertEquals(setOf("repo", "site_admin"), poll(deviceB).andExpect(status().isOk).andReturn().response.contentAsString.let { mapper.readTree(it).get("scope").asString().split(" ").toSet() })
    }

    @Test
    fun `거절하면 access_denied이고 만료되면 expired_token이다`() {
        val user = newUser()
        val (deviceA, codeA) = issue()
        service.deny(requireNotNull(service.findByUserCode(codeA)))
        poll(deviceA).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("access_denied"))
        val (deviceB, codeB) = issue()
        val auth = requireNotNull(service.findByUserCode(codeB)); auth.expiresDate = Date(System.currentTimeMillis() - 1000); devices.save(auth)
        poll(deviceB).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("expired_token"))
        assertTrue(user.id != null)
    }

    @Test
    fun `같은 기기 이름으로 다시 로그인하면 이전 토큰을 바꾼다`() {
        val user = newUser()
        repeat(2) {
            val (device, code) = issue("client_name" to "yona-cli on laptop")
            service.approve(requireNotNull(service.findByUserCode(code)), user)
            poll(device).andExpect(status().isOk)
        }
        assertEquals(1, apiTokens.findByUserIdOrderByCreatedDateDescIdDesc(user.id!!).size)
    }

    @Test
    fun `틀린 요청은 RFC 8628의 오류로 거절한다`() {
        poll("nothing").andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
        poll(null).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_request"))
        poll("x", grantType = "password").andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("unsupported_grant_type"))
        poll("x", grantType = null).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_request"))
    }
}
