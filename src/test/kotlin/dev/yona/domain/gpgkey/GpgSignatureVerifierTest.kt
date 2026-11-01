package dev.yona.domain.gpgkey

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 서명 검증을 검증한다(X-4 1단계, `decisions.md` D-65). 판정 이유 코드는 GitHub와 같다.
 * 사용자와 이메일을 확인하는 이유(`unverified_email`, `bad_email`, `no_user`)는 4단계의 서비스가 더한다.
 */
class GpgSignatureVerifierTest {

    private val data = "tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904\nauthor A <a@example.com> 1700000000 +0900\ncommitter A <a@example.com> 1700000000 +0900\n\n메시지\n".toByteArray()

    private fun payload(key: TestGpgKey, signerKeyId: Long? = null, signed: ByteArray = data, body: ByteArray = data) = SignedPayload(body, key.sign(signed, signerKeyId).toByteArray())

    private fun verify(payload: SignedPayload?, vararg keys: TestGpgKey, now: Instant = Instant.now()) =
        GpgSignatureVerifier.verify(payload, keys.map { GpgKeyParser.parse(it.publicArmored) }, now)

    @Test
    fun `등록된 키로 서명하면 valid이다`() {
        val key = TestGpgKey.generate()
        val result = verify(payload(key), key)
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals(key.keyId, result.signingKeyId)
        assertEquals(key.fingerprint, result.key?.primary?.fingerprint)
    }

    @Test
    fun `서명 하위 키로 서명해도 valid이고 키 묶음은 주 키로 찾는다`() {
        val key = TestGpgKey.generate(signingSubkey = true)
        val result = verify(payload(key, signerKeyId = key.subkeyId(0)), key)
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals("%016X".format(key.subkeyId(0)), result.signingKeyId)
        assertEquals(key.fingerprint, result.key?.primary?.fingerprint)
    }

    @Test
    fun `서명이 없으면 unsigned이다`() {
        assertEquals(GpgReason.UNSIGNED, verify(null, TestGpgKey.generate()).reason)
    }

    @Test
    fun `등록되지 않은 키로 서명하면 unknown_key이고 서명한 키 ID를 알려 준다`() {
        val signer = TestGpgKey.generate()
        val result = verify(payload(signer), TestGpgKey.generate())
        assertEquals(GpgReason.UNKNOWN_KEY, result.reason)
        assertEquals(signer.keyId, result.signingKeyId)
        assertNull(result.key)
    }

    @Test
    fun `내용이 바뀌면 invalid이다`() {
        val key = TestGpgKey.generate()
        val tampered = payload(key, body = "다른 내용".toByteArray())
        assertEquals(GpgReason.INVALID, verify(tampered, key).reason)
    }

    @Test
    fun `서명 글이 깨졌으면 malformed_signature이다`() {
        val key = TestGpgKey.generate()
        assertEquals(GpgReason.MALFORMED_SIGNATURE, verify(SignedPayload(data, "-----BEGIN PGP SIGNATURE-----\n\n쓰레기\n-----END PGP SIGNATURE-----\n".toByteArray()), key).reason)
        assertEquals(GpgReason.MALFORMED_SIGNATURE, verify(SignedPayload(data, ByteArray(0)), key).reason)
    }

    @Test
    fun `검증하는 시점에 키가 만료되었으면 expired_key이다`() {
        val created = Date(System.currentTimeMillis() - 10 * 86_400_000L)
        val key = TestGpgKey.generate(created = created, validSeconds = 20 * 86_400L)
        val signed = payload(key)
        assertEquals(GpgReason.VALID, verify(signed, key).reason)
        assertEquals(GpgReason.EXPIRED_KEY, verify(signed, key, now = Instant.now().plusSeconds(30 * 86_400L)).reason)
    }

    @Test
    fun `폐기된 키는 이전 서명도 expired_key이다`() {
        val key = TestGpgKey.generate()
        val signed = payload(key)
        assertEquals(GpgReason.EXPIRED_KEY, GpgSignatureVerifier.verify(signed, listOf(GpgKeyParser.parseUnchecked(key.revoked().publicArmored)), Instant.now()).reason)
    }

    @Test
    fun `서명 용도가 없는 키로 한 서명은 not_signing_key이다`() {
        val key = TestGpgKey.generate(primaryCanSign = false, signingSubkey = true)
        val signed = payload(key, signerKeyId = key.secretRing.secretKey.keyID)
        assertEquals(GpgReason.NOT_SIGNING_KEY, verify(signed, key).reason)
    }

    @Test
    fun `여러 키 가운데 서명한 키를 찾는다`() {
        val a = TestGpgKey.generate(userId = "A <a@example.com>")
        val b = TestGpgKey.generate(userId = "B <b@example.com>")
        val result = verify(payload(b), a, b)
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals(b.fingerprint, result.key?.primary?.fingerprint)
    }
}
