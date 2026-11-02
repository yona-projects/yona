package dev.yona.domain.sso

import org.junit.jupiter.api.Test
import java.security.interfaces.RSAPublicKey
import java.security.cert.X509Certificate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SAML SP 키쌍과 자체 서명 인증서(X-16 단계 3, `decisions.md` D-80 19번)를 검증한다: RSA 3072 키쌍과 유효 5년의 자체 서명 인증서를 만들고, PEM으로 내보내고 다시 읽으며,
 * 외부에서 올린 키와 인증서가 짝이 맞는지 확인한다.
 */
class Saml2CertificatesTest {

    @Test
    fun `RSA 3072 키쌍과 유효 5년 자체 서명 인증서를 만든다`() {
        val now = Instant.parse("2026-11-02T00:00:00Z")
        val pair = Saml2Certificates.generate("yona.example.com", now)
        val certificate = Saml2Certificates.parseCertificate(pair.certificatePem)
        assertEquals(3072, (certificate.publicKey as RSAPublicKey).modulus.bitLength())
        assertTrue(certificate.subjectX500Principal.name.contains("yona.example.com")); assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
        assertEquals(now, certificate.notBefore.toInstant()); assertTrue(certificate.notAfter.toInstant().isAfter(now.plusSeconds(5 * 365 * 24 * 3600L - 3 * 24 * 3600L)))
        certificate.verify(certificate.publicKey)
    }

    @Test
    fun `PEM으로 내보낸 비밀 키를 다시 읽으면 같은 키이다`() {
        val pair = Saml2Certificates.generate("yona.example.com")
        val key = Saml2Certificates.parsePrivateKey(pair.privateKeyPem)
        assertTrue(pair.privateKeyPem.startsWith("-----BEGIN PRIVATE KEY-----")); assertTrue(pair.certificatePem.startsWith("-----BEGIN CERTIFICATE-----"))
        assertTrue(Saml2Certificates.matches(key, Saml2Certificates.parseCertificate(pair.certificatePem)))
    }

    @Test
    fun `짝이 맞지 않는 키와 인증서는 맞지 않는다고 알린다`() {
        val a = Saml2Certificates.generate("a.example.com"); val b = Saml2Certificates.generate("b.example.com")
        assertTrue(!Saml2Certificates.matches(Saml2Certificates.parsePrivateKey(a.privateKeyPem), Saml2Certificates.parseCertificate(b.certificatePem)))
    }

    @Test
    fun `형식이 틀린 PEM은 거절한다`() {
        assertFailsWith<IllegalArgumentException> { Saml2Certificates.parseCertificate("not a pem") }
        assertFailsWith<IllegalArgumentException> { Saml2Certificates.parsePrivateKey("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----") }
        assertFailsWith<IllegalArgumentException> { Saml2Certificates.parseCertificate("") }
    }

    @Test
    fun `인증서 만료일과 지문을 알려 준다`() {
        val pair = Saml2Certificates.generate("yona.example.com", Instant.parse("2026-01-01T00:00:00Z"))
        val info = Saml2Certificates.describe(Saml2Certificates.parseCertificate(pair.certificatePem))
        assertNotNull(info.fingerprintSha256); assertEquals(64, info.fingerprintSha256.length); assertTrue(info.notAfter.isAfter(Instant.parse("2030-12-01T00:00:00Z")))
    }
}
