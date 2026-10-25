package dev.yona.web.user

import dev.yona.domain.ldap.LdapDirectory
import dev.yona.domain.ldap.LdapUser
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import javax.naming.AuthenticationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * LDAP을 켠 사이트의 로그인 화면 흐름이다. 원본: `UserApp.loginByFormRequest`, `loginByAjaxRequest`(`app/controllers/UserApp.java:197-345`).
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.ldap.enabled=true"])
@AutoConfigureMockMvc
@Transactional
class LdapLoginTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @MockitoBean private lateinit var directory: LdapDirectory

    private fun isLoggedIn(session: MockHttpSession) = mockMvc.perform(get("/user/editform").session(session)).andReturn().response.status == 200

    @Test
    fun `로컬에 없는 LDAP 사용자도 처음 로그인하면 만들어 로그인시킨다`() {
        Mockito.`when`(directory.authenticate("firstldap", "pw")).thenReturn(LdapUser("첫 로그인", "firstldap@corp.test", "firstldap", "", ""))
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "firstldap").param("password", "pw"))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
        assertNotNull(users.findByLoginId("firstldap"))
        assertEquals(true, isLoggedIn(session))
    }

    @Test
    fun `LDAP 인증이 실패하면 로그인 폼으로 돌려보내고 안내를 남긴다`() {
        Mockito.`when`(directory.authenticate("badldap", "wrong")).thenThrow(AuthenticationException("bad"))
        val result = mockMvc.perform(post("/users/login").param("loginIdOrEmail", "badldap").param("password", "wrong").param("redirectUrl", "/back"))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/users/loginform?redirectUrl=/back")).andReturn()
        assertEquals("user.login.invalid", result.flashMap["warning"])
    }

    @Test
    fun `AJAX 로그인도 LDAP으로 인증한다`() {
        Mockito.`when`(directory.authenticate("ajaxldap", "pw")).thenReturn(LdapUser("에이잭스", "ajaxldap@corp.test", "ajaxldap", "", ""))
        mockMvc.perform(post("/users/login").header("Accept", "application/json").param("loginIdOrEmail", "ajaxldap").param("password", "pw")).andExpect(status().isOk)
        Mockito.`when`(directory.authenticate("ajaxbad", "pw")).thenThrow(AuthenticationException("bad"))
        mockMvc.perform(post("/users/login").header("Accept", "application/json").param("loginIdOrEmail", "ajaxbad").param("password", "pw"))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("user.login.invalid"))
    }
}
