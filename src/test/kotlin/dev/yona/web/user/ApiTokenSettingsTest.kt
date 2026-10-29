package dev.yona.web.user

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.ApiTokenRepository
import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ApiTokenSupport
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 설정의 토큰 화면(`/user/editform/token`)을 검증한다(로드맵 X-5, `decisions.md` D-36). 토큰 목록, 만들기(이름, 만료, 범위), 한 번만 보이는 새 토큰 값, 삭제,
 * 이관한 옛 토큰의 경고를 다룬다. 만료의 \"사용자 지정\"은 달력으로 날짜를 고른다.
 */
@AutoConfigureMockMvc
@Transactional
class ApiTokenSettingsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var tokenRepository: ApiTokenRepository

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ts$n", name = "ts$n", email = "ts$n@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun page(session: MockHttpSession): Document = Jsoup.parse(mockMvc.perform(get("/user/editform/token").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun create(session: MockHttpSession, vararg params: Pair<String, String>, scopes: List<String> = emptyList()) =
        post("/user/token").session(session).apply { params.forEach { (k, v) -> param(k, v) }; if (scopes.isNotEmpty()) param("scopes", *scopes.toTypedArray()) }

    private val day = SimpleDateFormat("yyyy-MM-dd")

    @Test
    fun `로그인해야 하고 토큰 목록과 만들기 폼이 보인다`() {
        mockMvc.perform(get("/user/editform/token")).andExpect(status().is3xxRedirection)
        val u = user(); val doc = page(login(u))
        assertNotNull(doc.selectFirst("form#token-create[action=/user/token][method=post]"))
        assertEquals(listOf("7", "30", "60", "90", "none", "custom"), doc.select("form#token-create select[name=expiration] option").map { it.attr("value") })
        assertEquals("30", doc.selectFirst("form#token-create select[name=expiration] option[selected]")!!.attr("value"), "기본은 30일")
        assertNotNull(doc.selectFirst("form#token-create input[name=expiresDate]"))
        assertNotNull(doc.selectFirst("#token-expires-picker"), "사용자 지정 만료일은 달력으로 고른다")
        assertTrue(doc.select("script").any { it.attr("src").contains("pikaday") }, "달력 라이브러리(Pikaday)를 쓴다")
        assertEquals(true, doc.select("#token-list tr.token-row").isEmpty())
    }

    @Test
    fun `범위 체크 상자에는 GitHub 범위가 있고 site admin은 사이트 관리자에게만 보인다`() {
        val u = user(); val doc = page(login(u))
        val scopes = doc.select("form#token-create input[name=scopes]").map { it.attr("value") }
        assertEquals(ApiScopes.selectable(siteManager = false), scopes)
        assertTrue("site_admin" !in scopes)
    }

    @Test
    fun `토큰을 만들면 값을 한 번만 보여 주고 목록에는 접두사만 남는다`() {
        val u = user(); val s = login(u)
        val result = mockMvc.perform(create(s, "name" to "내 노트북", "expiration" to "30", scopes = listOf("repo", "user"))).andExpect(status().isOk).andReturn().response.contentAsString
        val doc = Jsoup.parse(result)
        val value = doc.selectFirst("#new-token-value")!!.attr("value")
        assertTrue(Regex("yona_pat_[0-9A-Za-z]{40}").matches(value), value)
        val saved = tokens.list(u).single()
        assertEquals("내 노트북", saved.name); assertEquals(setOf("repo", "user"), ApiScopes.parse(saved.scopes))
        assertNotNull(saved.expiresDate); assertTrue(saved.expiresDate!!.time > System.currentTimeMillis() + 29 * 86_400_000L)
        // 다시 열면 값은 없고 접두사만 있다
        val again = page(s)
        assertNull(again.selectFirst("#new-token-value"))
        val row = again.selectFirst("#token-list tr.token-row[data-id=${saved.id}]")!!
        assertEquals(value.take(13), row.selectFirst(".token-prefix")!!.text())
        assertEquals(false, again.html().contains(value), "원문은 다시 보이지 않는다")
        assertEquals(listOf("repo", "user"), row.select(".token-scope").map { it.text() })
    }

    @Test
    fun `만료는 없음과 사용자 지정 날짜도 고를 수 있다`() {
        val u = user(); val s = login(u)
        mockMvc.perform(create(s, "name" to "영구", "expiration" to "none")).andExpect(status().isOk)
        val date = day.format(Date(System.currentTimeMillis() + 10 * 86_400_000L))
        mockMvc.perform(create(s, "name" to "날짜", "expiration" to "custom", "expiresDate" to date)).andExpect(status().isOk)
        val byName = tokens.list(u).associateBy { it.name }
        assertNull(byName.getValue("영구").expiresDate)
        assertEquals(date, day.format(byName.getValue("날짜").expiresDate!!))
    }

    @Test
    fun `잘못된 입력은 400이고 토큰을 만들지 않는다`() {
        val u = user(); val s = login(u)
        mockMvc.perform(create(s, "name" to " ", "expiration" to "30")).andExpect(status().isBadRequest)
        mockMvc.perform(create(s, "name" to "과거", "expiration" to "custom", "expiresDate" to "2020-01-01")).andExpect(status().isBadRequest)
        mockMvc.perform(create(s, "name" to "형식", "expiration" to "custom", "expiresDate" to "내일")).andExpect(status().isBadRequest)
        mockMvc.perform(create(s, "name" to "만료", "expiration" to "9999")).andExpect(status().isBadRequest)
        mockMvc.perform(create(s, "name" to "범위", "expiration" to "30", scopes = listOf("nothing"))).andExpect(status().isBadRequest)
        mockMvc.perform(create(s, "name" to "관리", "expiration" to "30", scopes = listOf("site_admin"))).andExpect(status().isBadRequest)
        assertTrue(tokens.list(u).isEmpty())
    }

    @Test
    fun `자기 토큰만 지울 수 있고 지운 뒤 목록으로 돌아간다`() {
        val u = user(); val other = user(); val s = login(u)
        val mine = (tokens.create(u, "내것", emptySet(), null) as TokenCreation.Created).token
        val theirs = (tokens.create(other, "남의것", emptySet(), null) as TokenCreation.Created).token
        mockMvc.perform(post("/user/token/${theirs.id}/delete").session(s)).andExpect(status().isNotFound)
        mockMvc.perform(post("/user/token/${mine.id}/delete").session(s)).andExpect(status().isSeeOther).andExpect(redirectedUrl("/user/editform/token"))
        assertTrue(tokens.list(u).isEmpty()); assertEquals(1, tokens.list(other).size)
        mockMvc.perform(post("/user/token/${mine.id}/delete")).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `이관한 옛 토큰은 경고와 함께 보이고 삭제할 수 있다`() {
        val u = user(); val s = login(u)
        val legacy = ApiTokenSupport.grant(tokenRepository, u, "legacy-value-0123456789012345678901234567890=")
        legacy.name = ApiTokenService.LEGACY_TOKEN_NAME; tokenRepository.save(legacy)
        val row = page(s).selectFirst("#token-list tr.token-row[data-id=${legacy.id}]")!!
        assertNotNull(row.selectFirst(".legacy-warning"))
        assertNotNull(row.selectFirst("form[action=/user/token/${legacy.id}/delete]"))
    }

    @Test
    fun `이전 화면의 토큰 다시 만들기 주소는 없다`() {
        val s = login(user())
        val status = mockMvc.perform(get("/user/editform/token_reset").session(s)).andReturn().response.status
        assertTrue(status >= 400, "토큰 다시 만들기는 새 토큰 만들기로 대체되었다: $status")
    }
}
