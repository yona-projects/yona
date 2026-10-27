package dev.yona.web.access

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

/**
 * 익명 접근을 막은 사이트(`yona.allows-anonymous-access=false`)에서 실제 컨트롤러의 접근 검사 사슬이 v1.6과 같은지 검증한다.
 *
 * v1.6의 어노테이션 위치와 순서를 컨트롤러마다 대조한 결과(2026-10-27)에서 나온 차이를 고정한다.
 * - `SiteApp`: 클래스에 `@With(SiteManagerAuthAction)` 다음 `@AnonymousCheck` 순서라 익명은 관리자 검사의 403을 먼저 받는다.
 * - `ProjectApi`, `UserApi` 등 API: 클래스 `@AnonymousCheck`가 없다. 토큰 발급과 인사 API는 익명으로 호출한다.
 * - `UserApp`의 이메일 관련 메서드: `@AnonymousCheck`가 없어 본문이 익명을 처리한다.
 * - `Application.sidebar`: 메서드 `@AnonymousCheck`가 있다.
 * - `AttachmentApp`: 클래스 `@AnonymousCheck`가 있다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["yona.allows-anonymous-access=false"])
class ClosedSiteChainTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun isLoginForm(location: String?) = location?.startsWith("/users/loginform") == true

    @Test
    fun `사이트 관리자 화면은 익명에게 관리자 검사의 403이 먼저다`() {
        listOf("/sites/userList", "/sites/projectList", "/sites/mail", "/sites/data", "/sites/diagnostic", "/sites/postList").forEach {
            mockMvc.perform(get(it)).andExpect(status().isForbidden)
        }
    }

    @Test
    fun `API는 익명 접근을 막은 사이트에서도 로그인 폼으로 보내지 않는다`() {
        mockMvc.perform(get("/-_-api/v1/hello")).andExpect(status().isOk)
        // 토큰 발급은 호출 시점에 항상 익명이다. 본문이 없으면 400이며 303이 아니다
        val token = mockMvc.perform(post("/-_-api/v1/users/token")).andReturn().response
        assertTrue(token.status != 303, "토큰 발급이 로그인 폼으로 갔다: ${token.status}")
        mockMvc.perform(get("/-_-api/v1/admin/users")).andExpect(status().isForbidden)
    }

    @Test
    fun `이메일 관련 사용자 주소는 익명에게 로그인 폼이 아니라 본문의 응답이다`() {
        listOf(post("/user/email"), delete("/user/email/delete/1")).forEach {
            val r = mockMvc.perform(it).andReturn().response
            assertTrue(r.status != 303 || !isLoginForm(r.getHeader("Location")), "${r.status} ${r.getHeader("Location")}")
        }
        val confirm = mockMvc.perform(get("/user/email/confirm/1/nothing")).andReturn().response
        assertTrue(confirm.status != 303 || !isLoginForm(confirm.getHeader("Location")), "이메일 확인 링크가 로그인 폼으로 갔다")
    }

    @Test
    fun `사이드바와 첨부 파일은 익명을 로그인 폼으로 보낸다`() {
        mockMvc.perform(get("/user/sidebar")).andExpect(status().isSeeOther).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/users/loginform")))
        listOf("/files", "/files/1").forEach {
            mockMvc.perform(get(it)).andExpect(status().isSeeOther).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/users/loginform")))
        }
    }
}
