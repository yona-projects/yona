package dev.yona.oauth2server.cimd

import dev.yona.config.YonaProperties
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientRepository
import dev.yona.domain.oauth2server.RedirectUris
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.apitoken.ApiScopes
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 실제 전송을 만든다. 시험은 같은 형식의 가짜 전송을 우선으로 내놓아 바꾼다. */
@Configuration(proxyBeanMethods = false)
class CimdTransportConfig {
    @Bean
    fun cimdTransport(properties: YonaProperties): CimdTransport = SocketCimdTransport(allowPrivateHosts = properties.oauth2.cimdAllowPrivateHosts)
}

/**
 * CIMD 문서를 가져오는 속도 제한이다(추가 개발 항목 X-1, `decisions.md` D-71). 서버 전체로 분당 [PER_MINUTE]번까지만 가져오고, 같은 주소는 [SAME_URL_SECONDS]초 안에 다시 가져오지 않는다
 * (실패한 주소도 마찬가지이다). 서버 메모리 기준이다.
 */
@Component
class CimdRateLimiter {

    private val window = AtomicInteger()
    @Volatile private var windowStart = 0L
    private val lastAttempt = ConcurrentHashMap<String, Long>()

    /** 지금 [url]을 가져와도 되면 `true`이고 가져오는 것으로 센다. */
    @Synchronized
    fun tryAcquire(url: String, now: Long = System.currentTimeMillis()): Boolean {
        if (now - windowStart >= 60_000) { windowStart = now; window.set(0) }
        if (lastAttempt[url]?.let { now - it < SAME_URL_SECONDS * 1000 } == true) return false
        if (window.get() >= PER_MINUTE) return false
        window.incrementAndGet(); lastAttempt[url] = now
        if (lastAttempt.size > MAX_TRACKED) lastAttempt.entries.removeIf { now - it.value > 60_000 }
        return true
    }

    fun reset() { window.set(0); windowStart = 0; lastAttempt.clear() }

    companion object {
        const val PER_MINUTE = 60
        const val SAME_URL_SECONDS = 10L
        private const val MAX_TRACKED = 2000
    }
}

/**
 * CIMD 클라이언트를 찾는다(추가 개발 항목 X-1, `decisions.md` D-71). `https://` 주소 클라이언트 ID이면 저장된 캐시 행을 쓰고, 없거나 만료되었으면 문서를 가져와 검증하고 저장한다.
 * 가져오기 실패는 캐시하지 않는다. 만료된 행은 가져오기에 실패했을 때만 [STALE_GRACE] 안에서 계속 쓴다(이미 발급한 갱신 토큰이 문서 서버의 일시적 장애로 막히지 않게 한다).
 * **문서가 검증에 실패하면 이전 행도 지운다**(앱이 일부러 바꾼 내용일 수 있다). 관리자 설정으로 끄면 어떤 CIMD 클라이언트도 받지 않는다.
 */
@Component
class CimdClientResolver(
    private val clients: OAuthClientRepository,
    private val transport: CimdTransport,
    private val settings: SiteSettingService,
    private val limiter: CimdRateLimiter,
    private val transactions: TransactionTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** [clientId]에 해당하는 CIMD 앱 행이다. 받을 수 없으면 `null`이다. */
    fun resolve(clientId: String, now: Instant = Instant.now()): OAuthClient? {
        if (!CimdDocument.isClientIdUrl(clientId) || CimdDocument.urlProblem(clientId) != null) return null
        if (!settings.oauthCimdEnabled()) return null
        val cached = clients.findByClientId(clientId)?.takeIf { it.registration == OAuthClient.CIMD }
        if (cached != null && cached.expiresDate?.toInstant()?.isAfter(now) == true) return cached
        if (!limiter.tryAcquire(clientId)) return cached?.takeIf { withinGrace(it, now) }
        val response = try {
            transport.fetch(clientId)
        } catch (e: CimdFetchException) {
            log.info("CIMD 문서를 가져오지 못했다: {} ({})", clientId, e.message)
            return cached?.takeIf { withinGrace(it, now) }
        }
        return when (val parsed = CimdDocument.parse(clientId, response.body)) {
            is CimdDocument.Result.Invalid -> {
                log.info("CIMD 문서가 올바르지 않다: {} ({})", clientId, parsed.reason)
                cached?.let { stale -> transactions.executeWithoutResult { clients.deleteById(requireNotNull(stale.id)) } }
                null
            }
            is CimdDocument.Result.Valid -> transactions.execute { save(cached, clientId, parsed, response.maxAgeSeconds, now) }
        }
    }

    private fun withinGrace(row: OAuthClient, now: Instant) = row.expiresDate?.toInstant()?.plus(STALE_GRACE)?.isAfter(now) == true

    private fun save(existing: OAuthClient?, clientId: String, doc: CimdDocument.Result.Valid, maxAge: Long?, now: Instant): OAuthClient {
        val ttl = (maxAge ?: DEFAULT_TTL.seconds).coerceIn(MIN_TTL.seconds, MAX_TTL.seconds)
        val row = existing ?: OAuthClient(clientId = clientId, createdDate = Date.from(now))
        row.name = doc.name; row.description = ""; row.homeUrl = doc.homeUrl; row.redirectUris = RedirectUris.format(doc.redirectUris); row.confidential = false; row.secretHash = null
        row.allowedScopes = ApiScopes.format(doc.scopes); row.registration = OAuthClient.CIMD; row.ownerType = null; row.ownerId = null
        row.fetchedDate = Date.from(now); row.expiresDate = Date.from(now.plusSeconds(ttl)); row.updatedDate = Date.from(now)
        return clients.save(row)
    }

    companion object {
        val MIN_TTL: Duration = Duration.ofMinutes(1)
        val MAX_TTL: Duration = Duration.ofHours(24)
        val DEFAULT_TTL: Duration = Duration.ofHours(1)
        val STALE_GRACE: Duration = Duration.ofDays(7)
    }
}
