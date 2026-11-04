package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 요청 위조 방지(CSRF)를 검증한다(R-02). v1.6은 폼과 스크립트가 토큰을 보내지 않아 다른 사이트의 페이지가 로그인한 사용자의 브라우저로 상태를 바꾸는 요청을 보낼 수 있었다.
 * 상태를 바꾸는 요청은 쿠키 `XSRF-TOKEN`과 같은 값을 헤더 `X-XSRF-TOKEN`이나 인자 `_csrf`로 보내야 한다. 토큰 전용 API, Git, Subversion, LFS처럼 브라우저가 아닌
 * 클라이언트가 쓰는 주소와 `Authorization`이나 `Yona-Token` 헤더가 있는 요청은 대상이 아니다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.csrf-protection=true"])
@AutoConfigureMockMvc
class CsrfProtectionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    private val token = "csrf-test-token"

    @Test
    fun `화면을 열면 스크립트가 읽을 수 있는 토큰 쿠키를 받는다`() {
        mockMvc.perform(get("/users/loginform")).andExpect(cookie().exists("XSRF-TOKEN")).andExpect(cookie().httpOnly("XSRF-TOKEN", false))
    }

    @Test
    fun `토큰 없이 상태를 바꾸는 요청을 보내면 거절한다`() {
        for (path in listOf("/users/login", "/user/defultLoginPage", "/translation", "/info/leave/a/b")) {
            mockMvc.perform(post(path).param("loginIdOrEmail", "x")).andExpect(status().isForbidden)
        }
    }

    @Test
    fun `쿠키와 같은 값을 헤더나 인자로 보내면 통과한다`() {
        val viaHeader = mockMvc.perform(post("/users/login").cookie(Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token).param("loginIdOrEmail", "nobody").param("password", "x")).andReturn().response.status
        val viaParam = mockMvc.perform(post("/users/login").cookie(Cookie("XSRF-TOKEN", token)).param("_csrf", token).param("loginIdOrEmail", "nobody").param("password", "x")).andReturn().response.status
        assertNotEquals(403, viaHeader); assertNotEquals(403, viaParam)
        // 값이 다르면 거절한다
        mockMvc.perform(post("/users/login").cookie(Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", "other")).andExpect(status().isForbidden)
    }

    @Test
    fun `읽기 요청은 토큰이 필요 없다`() {
        assertEquals(200, mockMvc.perform(get("/users/loginform")).andReturn().response.status)
    }

    @Test
    fun `브라우저가 아닌 클라이언트가 쓰는 주소는 토큰을 요구하지 않는다`() {
        val exempt = listOf("/nobody/nothing/git-upload-pack", "/nobody/nothing/git-receive-pack", "/nobody/nothing/info/lfs/objects/batch", "/svn/nobody/nothing", "/-_-api/v1/user/issues", "/internal/ssh/authenticate")
        for (path in exempt) {
            val status = mockMvc.perform(post(path)).andReturn().response.status
            assertNotEquals(403, status, "$path 가 토큰을 요구한다")
        }
    }

    @Test
    fun `Authorization이나 Yona-Token 헤더가 있는 요청은 토큰을 요구하지 않는다`() {
        assertNotEquals(403, mockMvc.perform(post("/translation").header("Authorization", "Basic eDp5")).andReturn().response.status)
        assertNotEquals(403, mockMvc.perform(post("/translation").header("Yona-Token", "abc")).andReturn().response.status)
    }
}
