package dev.yona.oauth2server.cimd

import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientRepository
import dev.yona.domain.site.SiteSettingService
import dev.yona.oauth2server.YonaRegisteredClientRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 시험용 가짜 전송이다. 주소마다 정해 둔 응답이나 실패를 돌려주고 가져온 횟수를 센다. */
class FakeCimdTransport : CimdTransport {
    val documents = mutableMapOf<String, CimdResponse>()
    val failures = mutableMapOf<String, String>()
    val fetched = mutableListOf<String>()

    override fun fetch(url: String): CimdResponse {
        fetched += url
        failures[url]?.let { throw CimdFetchException(it) }
        return documents[url] ?: throw CimdFetchException("문서가 없다")
    }

    fun reset() { documents.clear(); failures.clear(); fetched.clear() }
}

@TestConfiguration
class FakeCimdConfig {
    @Bean @Primary
    fun fakeCimdTransport(): FakeCimdTransport = FakeCimdTransport()
}

/**
 * CIMD 클라이언트 조회(X-1 5단계, `decisions.md` D-71)를 가짜 전송으로 검증한다: 가져와 저장하기, 캐시 시간 안에서는 다시 가져오지 않기, 만료 뒤 다시 가져오기,
 * 가져오기 실패는 캐시하지 않고 7일 안의 오래된 행만 쓰기, 검증 실패는 행을 지우기, 설정으로 끄기, 속도 제한.
 */
@Import(FakeCimdConfig::class)
@Transactional
class CimdClientResolverTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: YonaRegisteredClientRepository
    @Autowired private lateinit var clients: OAuthClientRepository
    @Autowired private lateinit var transport: FakeCimdTransport
    @Autowired private lateinit var settings: SiteSettingService
    @Autowired private lateinit var limiter: CimdRateLimiter

    private val url = "https://claude.example/oauth/client.json"

    @BeforeEach
    fun clean() { transport.reset(); limiter.reset(); settings.setOauthCimdEnabled(true) }

    @AfterEach
    fun restore() { transport.reset(); limiter.reset() }

    private fun document(name: String = "Claude Code", redirects: String = """["http://localhost/callback","http://127.0.0.1/callback"]""", id: String = url) =
        """{"client_id":"$id","client_name":"$name","redirect_uris":$redirects,"token_endpoint_auth_method":"none"}"""

    private fun serve(body: String = document(), maxAge: Long? = 600) { transport.documents[url] = CimdResponse(body, maxAge) }

    @Test
    fun `처음 보는 주소는 문서를 가져와 저장하고 공개 앱으로 돌려준다`() {
        serve()
        val registered = assertNotNull(repository.findByClientId(url))
        assertEquals(url, registered.clientId); assertEquals("Claude Code", registered.clientName)
        assertEquals(setOf("http://localhost/callback", "http://127.0.0.1/callback"), registered.redirectUris)
        assertTrue(registered.clientAuthenticationMethods.map { it.value } == listOf("none") && registered.clientSettings.isRequireProofKey)
        val row = clients.findByClientId(url)!!
        assertEquals(OAuthClient.CIMD, row.registration); assertNull(row.ownerType); assertNull(row.ownerId); assertEquals(false, row.confidential); assertNull(row.secretHash)
        assertTrue(row.expiresDate!!.after(Date()) && row.fetchedDate != null)
        assertEquals(listOf(url), transport.fetched)
    }

    @Test
    fun `캐시 시간 안에서는 다시 가져오지 않는다`() {
        serve(); repository.findByClientId(url); repository.findByClientId(url); repository.findByClientId(url)
        assertEquals(1, transport.fetched.size)
    }

    @Test
    fun `만료되면 다시 가져와 바뀐 내용을 반영하고 행 번호는 그대로이다`() {
        serve(); val before = repository.findByClientId(url)!!
        clients.findByClientId(url)!!.let { it.expiresDate = Date.from(Instant.now().minusSeconds(5)); clients.save(it) }
        serve(document(name = "새 이름")); limiter.reset()
        val after = repository.findByClientId(url)!!
        assertEquals("새 이름", after.clientName); assertEquals(before.id, after.id); assertEquals(2, transport.fetched.size)
    }

    @Test
    fun `캐시 시간은 응답의 max-age를 따르되 1분에서 24시간 사이로 맞춘다`() {
        serve(maxAge = 5); repository.findByClientId(url)
        val short = Duration.between(Instant.now(), clients.findByClientId(url)!!.expiresDate!!.toInstant()).seconds
        assertTrue(short in 50..70, "최소 1분: $short")
        clients.deleteAll(); limiter.reset(); serve(maxAge = 10_000_000); repository.findByClientId(url)
        val long = Duration.between(Instant.now(), clients.findByClientId(url)!!.expiresDate!!.toInstant()).seconds
        assertTrue(long in 86_300..86_400, "최대 24시간: $long")
        clients.deleteAll(); limiter.reset(); serve(maxAge = null); repository.findByClientId(url)
        assertTrue(Duration.between(Instant.now(), clients.findByClientId(url)!!.expiresDate!!.toInstant()).seconds in 3_500..3_600, "없으면 1시간")
    }

    @Test
    fun `가져오기에 실패하면 캐시하지 않고 없는 앱으로 본다`() {
        transport.failures[url] = "시간 초과"
        assertNull(repository.findByClientId(url)); assertNull(clients.findByClientId(url))
    }

    @Test
    fun `만료된 행은 가져오기에 실패해도 7일 안이면 계속 쓰고 그 뒤에는 쓰지 않는다`() {
        serve(); repository.findByClientId(url)
        val row = clients.findByClientId(url)!!
        row.expiresDate = Date.from(Instant.now().minus(Duration.ofDays(2))); clients.save(row)
        transport.failures[url] = "내려가 있다"; limiter.reset()
        assertNotNull(repository.findByClientId(url), "7일 안")
        row.expiresDate = Date.from(Instant.now().minus(Duration.ofDays(8))); clients.save(row); limiter.reset()
        assertNull(repository.findByClientId(url), "7일 뒤")
    }

    @Test
    fun `문서가 검증에 실패하면 받지 않고 이전에 저장한 행도 지운다`() {
        serve(); repository.findByClientId(url)
        clients.findByClientId(url)!!.let { it.expiresDate = Date.from(Instant.now().minusSeconds(5)); clients.save(it) }
        serve(document(id = "https://evil.example/x.json")); limiter.reset()
        assertNull(repository.findByClientId(url)); assertNull(clients.findByClientId(url))
        serve("""{"client_id":"$url","redirect_uris":["http://example.com/cb"]}"""); limiter.reset()
        assertNull(repository.findByClientId(url))
    }

    @Test
    fun `설정으로 끄면 CIMD 클라이언트를 받지 않는다`() {
        serve(); repository.findByClientId(url)
        settings.setOauthCimdEnabled(false)
        assertNull(repository.findByClientId(url)); transport.fetched.clear()
        assertNull(repository.findByClientId("https://other.example/c.json")); assertTrue(transport.fetched.isEmpty(), "끄면 가져오지도 않는다")
    }

    @Test
    fun `https 주소가 아니거나 모양이 틀린 클라이언트 ID는 가져오지 않는다`() {
        for (bad in listOf("yona_app_nope", "http://claude.example/c.json", "https://claude.example", "https://claude.example/a#x", "https://user@claude.example/c.json")) {
            assertNull(repository.findByClientId(bad), bad)
        }
        assertTrue(transport.fetched.isEmpty())
    }

    @Test
    fun `가져오기가 너무 잦으면 속도 제한이 막는다`() {
        repeat(CimdRateLimiter.PER_MINUTE) { i -> transport.documents["https://c$i.example/c.json"] = CimdResponse(document(id = "https://c$i.example/c.json"), 600); repository.findByClientId("https://c$i.example/c.json") }
        transport.documents["https://over.example/c.json"] = CimdResponse(document(id = "https://over.example/c.json"), 600)
        val before = transport.fetched.size
        assertNull(repository.findByClientId("https://over.example/c.json")); assertEquals(before, transport.fetched.size, "한도를 넘으면 가져오지 않는다")
    }

    @Test
    fun `같은 주소를 연달아 다시 가져오지 않는다`() {
        transport.failures[url] = "실패"
        repository.findByClientId(url); repository.findByClientId(url); repository.findByClientId(url)
        assertEquals(1, transport.fetched.size, "실패한 주소도 10초 안에는 다시 가져오지 않는다")
    }
}
