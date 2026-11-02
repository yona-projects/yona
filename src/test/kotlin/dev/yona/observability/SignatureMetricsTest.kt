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
 * GPG 서명 검증의 지표(X-4 6단계, `decisions.md` D-65)를 검증한다. `yona.signature.verifications{target, reason, format}`는 판정마다 센다. `format`은 서명 형식(`gpg`, `ssh`)이라 GPG 서명과 SSH 서명(D-68)을 나누어 센다. 서명이 없는 커밋은 판정 대상이 아니라
 * 세지 않는다(`unsigned`는 서명이 없는 태그 응답처럼 호출한 쪽이 물어본 경우에만 센다). 태그에 로그인 ID, 키 ID, 이메일을 넣지 않는다.
 */
class SignatureMetricsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var verification: GpgVerificationService
    @Autowired private lateinit var registry: MeterRegistry

    private fun count(target: String, reason: String, format: String = "gpg") =
        registry.find("yona.signature.verifications").tags("target", target, "reason", reason, "format", format).counters().sumOf { it.count() }

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
    fun `GPG 서명과 SSH 서명을 형식별로 나누어 센다 (D-68)`() {
        val gpgUnknown = count("commit", "unknown_key", "gpg"); val sshUnknown = count("commit", "unknown_key", "ssh"); val sshMalformed = count("tag", "malformed_signature", "ssh")
        val key = TestGpgKey.generate()
        verification.verifyCommit(SignedPayload(data, key.sign(data).toByteArray()), "tester@example.com")
        assertEquals(gpgUnknown + 1, count("commit", "unknown_key", "gpg"))
        assertEquals(sshUnknown, count("commit", "unknown_key", "ssh"), "GPG 판정이 SSH 건수에 섞이지 않는다")
        val dir = kotlin.io.path.createTempDirectory("gm").toFile()
        try {
            val sshKey = dev.yona.domain.sshkey.TestSshSigner.newKey(dir)
            verification.verifyCommit(SignedPayload(data, dev.yona.domain.sshkey.TestSshSigner.sign(sshKey, data).toByteArray()), "tester@example.com")
            assertEquals(sshUnknown + 1, count("commit", "unknown_key", "ssh"))
            assertEquals(gpgUnknown + 1, count("commit", "unknown_key", "gpg"), "SSH 판정이 GPG 건수에 섞이지 않는다")
        } finally {
            dir.deleteRecursively()
        }
        verification.verifyTag(SignedPayload(data, "-----BEGIN SSH SIGNATURE-----\nAAAA\n-----END SSH SIGNATURE-----\n".toByteArray()), "tester@example.com")
        assertEquals(sshMalformed + 1, count("tag", "malformed_signature", "ssh"))
    }

    @Test
    fun `지표 태그에는 이유와 대상 종류와 서명 형식만 있고 형식 값은 gpg와 ssh뿐이다`() {
        verification.verifyCommit(null, null)
        val tags = registry.find("yona.signature.verifications").counters().flatMap { it.id.tags.map { t -> t.key } }.toSet()
        assertTrue(tags.all { it in setOf("target", "reason", "format") }, tags.toString())
        val formats = registry.find("yona.signature.verifications").counters().mapNotNull { it.id.getTag("format") }.toSet()
        assertTrue(formats.all { it in setOf("gpg", "ssh") }, formats.toString())
    }
}
