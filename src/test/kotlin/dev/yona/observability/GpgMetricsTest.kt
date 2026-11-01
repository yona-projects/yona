package dev.yona.observability

import dev.yona.domain.gpgkey.GpgVerificationService
import dev.yona.domain.gpgkey.SignedPayload
import dev.yona.domain.gpgkey.TestGpgKey
import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GPG 서명 검증의 지표(X-4 6단계, `decisions.md` D-65)를 검증한다. `yona.gpg.verifications{target, reason}`는 판정마다 센다. 서명이 없는 커밋은 판정 대상이 아니라
 * 세지 않는다(`unsigned`는 서명이 없는 태그 응답처럼 호출한 쪽이 물어본 경우에만 센다). 태그에 로그인 ID, 키 ID, 이메일을 넣지 않는다.
 */
class GpgMetricsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var verification: GpgVerificationService
    @Autowired private lateinit var registry: MeterRegistry

    private fun count(target: String, reason: String) = registry.find("yona.gpg.verifications").tags("target", target, "reason", reason).counters().sumOf { it.count() }

    private val data = "서명 대상".toByteArray()

    @Test
    fun `커밋과 태그의 판정을 이유별로 센다`() {
        val unknown = count("commit", "unknown_key"); val unsigned = count("tag", "unsigned"); val invalid = count("commit", "invalid")
        val key = TestGpgKey.generate()
        verification.verifyCommit(SignedPayload(data, key.sign(data).toByteArray()), "tester@example.com")
        verification.verifyTag(null, "tester@example.com")
        assertEquals(unknown + 1, count("commit", "unknown_key"))
        assertEquals(unsigned + 1, count("tag", "unsigned"))
        assertEquals(invalid, count("commit", "invalid"))
    }

    @Test
    fun `지표 태그에는 이유와 대상 종류만 있다`() {
        verification.verifyCommit(null, null)
        val tags = registry.find("yona.gpg.verifications").counters().flatMap { it.id.tags.map { t -> t.key } }.toSet()
        assertTrue(tags.all { it in setOf("target", "reason") }, tags.toString())
    }
}
