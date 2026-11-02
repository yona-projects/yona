package dev.yona.oauth2server

import dev.yona.domain.oauth2server.OAuthAuthorizationRepository
import dev.yona.observability.JobMetrics
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * 쓸모없어진 OAuth 인가를 지운다(추가 개발 항목 X-1, `decisions.md` D-71). 하루에 한 번 실행한다. 승인 도중에 끝나 토큰이 없고 [PENDING_AFTER]보다 오래된 인가와, 액세스 토큰과 갱신 토큰이
 * 모두 [DEAD_AFTER] 전에 만료된 인가를 지운다. 살아 있는 인가는 건드리지 않는다.
 */
@Component
class OAuthAuthorizationPurger(private val authorizations: OAuthAuthorizationRepository, private val jobs: JobMetrics) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelay = 120_000, fixedRate = 86_400_000)
    fun scheduled() {
        try {
            val removed = jobs.run("oauth_authorization_purge") { purge() }
            if (removed > 0) log.info("쓸모없는 OAuth 인가를 {}건 지웠다", removed)
        } catch (e: Exception) {
            log.warn("OAuth 인가를 정리하지 못했다", e)
        }
    }

    /** 정리하고 지운 건수를 돌려준다. */
    @Transactional
    fun purge(now: Instant = Instant.now()): Int =
        authorizations.deletePendingBefore(Date.from(now.minus(PENDING_AFTER))) + authorizations.deleteExpiredBefore(Date.from(now.minus(DEAD_AFTER)))

    companion object {
        val PENDING_AFTER: Duration = Duration.ofHours(1)
        val DEAD_AFTER: Duration = Duration.ofDays(7)
    }
}
