package dev.yona.domain.gpgkey

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 실제 `gpg`와 `git`이 만든 키와 서명을 검증해 BouncyCastle로 만든 시험 키와 어긋나지 않는지 확인한다(X-4 1단계).
 * 자료는 `src/test/resources/gpg`에 있다.
 */
class RealGpgCompatibilityTest {

    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/gpg/$name")) { "$name 없음" }.readBytes()

    private fun key(name: String) = GpgKeyParser.parse(String(resource(name), Charsets.UTF_8))

    @Test
    fun `실제 gpg가 만든 Ed25519와 RSA 공개키를 읽는다`() {
        val ed = key("ed-pub.asc")
        assertEquals(listOf("real-ed@example.com"), ed.primary.emails)
        assertEquals("B08869028CD27B1BC8757D8F14EE5000FA267007", ed.primary.fingerprint)
        assertTrue(ed.hasSigningKey)
        val rsa = key("rsa-pub.asc")
        assertEquals(listOf("real-rsa@example.com"), rsa.primary.emails)
        assertEquals("2560B72FD43F0FE012E788FB0E240322B60F3864", rsa.primary.fingerprint)
    }

    @Test
    fun `실제 git이 서명한 Ed25519 커밋이 valid이다`() {
        val result = GpgSignatureVerifier.verify(GitSignatures.ofCommit(resource("ed-commit.bin")), listOf(key("ed-pub.asc"), key("rsa-pub.asc")))
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals("B08869028CD27B1BC8757D8F14EE5000FA267007", result.key?.primary?.fingerprint)
    }

    @Test
    fun `실제 git이 서명한 RSA 커밋이 valid이다`() {
        val result = GpgSignatureVerifier.verify(GitSignatures.ofCommit(resource("rsa-commit.bin")), listOf(key("ed-pub.asc"), key("rsa-pub.asc")))
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals("2560B72FD43F0FE012E788FB0E240322B60F3864", result.key?.primary?.fingerprint)
    }

    @Test
    fun `실제 git이 서명한 주석 태그가 valid이다`() {
        val result = GpgSignatureVerifier.verify(GitSignatures.ofTag(resource("ed-tag.bin")), listOf(key("ed-pub.asc")))
        assertEquals(GpgReason.VALID, result.reason)
    }

    @Test
    fun `커밋 원문의 한 글자만 바뀌어도 invalid이다`() {
        val raw = resource("ed-commit.bin")
        val tampered = String(raw, Charsets.UTF_8).replace("ed25519 signed", "ed25519 sIgned").toByteArray()
        assertEquals(GpgReason.INVALID, GpgSignatureVerifier.verify(assertNotNull(GitSignatures.ofCommit(tampered)), listOf(key("ed-pub.asc"))).reason)
    }

    @Test
    fun `키가 등록되지 않았으면 unknown_key이다`() {
        assertEquals(GpgReason.UNKNOWN_KEY, GpgSignatureVerifier.verify(GitSignatures.ofCommit(resource("ed-commit.bin")), listOf(key("rsa-pub.asc"))).reason)
    }
}
