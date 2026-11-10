package dev.yona.web.user

import dev.yona.config.YonaProperties
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 개인 소셜 로그인(GitHub, Google)은 일체 제거했다(로드맵 X-48, `decisions.md` D-127). 사내 IdP(OIDC, SAML2, LDAP) 로그인은 그대로이다.
 * 클래스, 설정, 경로, 화면 어디에도 소셜 로그인이 남아 있지 않아야 한다. 예전 설정 키를 줘도 로그인 화면에 소셜 단추가 생기지 않는다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["yona.social.login-support=github", "yona.social.github.client-id=gh-id", "yona.social.github.client-secret=gh-secret"])
class SocialLoginRemovedTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `소셜 로그인 클래스는 없다`() {
        listOf(
            "dev.yona.domain.social.SocialAccountService", "dev.yona.domain.social.SocialProviders", "dev.yona.domain.social.OAuth2SocialProvider",
            "dev.yona.web.social.SocialLoginController", "dev.yona.web.social.SocialSession", "dev.yona.web.ProviderLogos"
        ).forEach { name -> assertFailsWith<ClassNotFoundException>(name) { Class.forName(name) } }
    }

    @Test
    fun `소셜 로그인 설정은 없다`() {
        val fields = YonaProperties::class.java.declaredFields.map { it.name }
        assertTrue("social" !in fields, "social 설정 묶음이 남아 있다")
        assertTrue("socialLoginOnly" !in fields, "소셜 로그인만 허용 옵션이 남아 있다")
        assertTrue(YonaProperties::class.java.declaredClasses.none { it.simpleName in setOf("Social", "OAuthProvider") }, "소셜 설정 클래스가 남아 있다")
    }

    @Test
    fun `소셜 로그인 경로는 열려 있지 않다`() {
        listOf("/authenticate/github", "/authenticate/google", "/authenticate/github/denied", "/restricted", "/logout").forEach { path ->
            val status = mockMvc.perform(get(path)).andReturn().response.status
            assertTrue(status in setOf(401, 403, 404, 405), "$path 가 아직 응답한다: $status")
        }
    }

    @Test
    fun `옛 설정 키를 줘도 로그인 화면에 소셜 단추가 없다`() {
        val html = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString
        val page = Jsoup.parse(html)
        assertEquals(0, page.select("a.oauth-login-btn:not(.sso-login-btn)").size, "소셜 단추")
        assertTrue(page.select("a[href*=/authenticate/]").none { !it.hasClass("sso-login-btn") }, "소셜 인증 링크")
    }

    @Test
    fun `화면 틀은 소셜 로그인 모델을 쓰지 않는다`() {
        val used = File("src/main/resources/templates").walkTopDown().filter { it.isFile && it.extension == "html" }
            .filter { Regex("""socialProviders|socialLoggedIn|socialLoginSupported|socialLoginOnly|socialSession""").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals(emptyList(), used, "소셜 로그인 모델을 아직 쓰는 화면 틀")
    }
}
