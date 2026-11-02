package dev.yona.domain.sso

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * SAML SP 키쌍과 인증서 도구이다(추가 개발 항목 X-16, `decisions.md` D-80 19번). 앱 안에서 RSA 3072 키쌍과 유효 5년의 자체 서명 인증서를 만들고, 외부에서 올린 키와 인증서를
 * PEM으로 읽어 짝이 맞는지 확인한다. 비밀 키는 PKCS#8(`-----BEGIN PRIVATE KEY-----`)만 받는다.
 */
object Saml2Certificates {

    private const val KEY_BITS = 3072
    private const val VALID_YEARS = 5L

    /** 만든 키 자료이다. 둘 다 PEM 글이다. */
    data class KeyMaterial(val privateKeyPem: String, val certificatePem: String)

    /** 인증서의 요약이다(관리자 화면에 보인다). */
    data class CertificateInfo(val subject: String, val notBefore: Instant, val notAfter: Instant, val fingerprintSha256: String)

    /** [commonName]을 주체로 하는 키쌍과 자체 서명 인증서를 만든다. 서명용과 암호화용으로 같은 인증서를 쓴다. */
    fun generate(commonName: String, now: Instant = Instant.now()): KeyMaterial {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS, SecureRandom()) }.generateKeyPair()
        val name = X500Name("CN=${commonName.replace(Regex("[,+\"\\\\<>;=]"), " ").trim().ifEmpty { "yona" }}")
        val notAfter = java.time.ZonedDateTime.ofInstant(now, java.time.ZoneOffset.UTC).plusYears(VALID_YEARS).toInstant()
        val builder = JcaX509v3CertificateBuilder(name, BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE), Date.from(now), Date.from(notAfter), name, pair.public)
            .addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)))
        return KeyMaterial(pem("PRIVATE KEY", pair.private.encoded), pem("CERTIFICATE", certificate.encoded))
    }

    /** PEM 글의 인증서를 읽는다. 형식이 틀리면 [IllegalArgumentException]이다. */
    fun parseCertificate(pem: String): X509Certificate = try {
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(decode(pem, "CERTIFICATE"))) as X509Certificate
    } catch (e: java.security.GeneralSecurityException) {
        throw IllegalArgumentException("인증서를 읽지 못했다", e)
    }

    /** PEM(PKCS#8) 글의 RSA 비밀 키를 읽는다. 형식이 틀리면 [IllegalArgumentException]이다. */
    fun parsePrivateKey(pem: String): PrivateKey = try {
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(decode(pem, "PRIVATE KEY")))
    } catch (e: java.security.GeneralSecurityException) {
        throw IllegalArgumentException("비밀 키를 읽지 못했다(PKCS#8 RSA 키만 받는다)", e)
    }

    /** 비밀 키와 인증서의 공개 키가 같은 짝이면 `true`이다. */
    fun matches(key: PrivateKey, certificate: X509Certificate): Boolean {
        val private = key as? RSAPrivateCrtKey ?: return false
        val public = certificate.publicKey as? RSAPublicKey ?: return false
        return private.modulus == public.modulus && private.publicExponent == public.publicExponent
    }

    /** 인증서의 주체, 유효 기간, SHA-256 지문(16진수)이다. */
    fun describe(certificate: X509Certificate): CertificateInfo = CertificateInfo(
        certificate.subjectX500Principal.name, certificate.notBefore.toInstant(), certificate.notAfter.toInstant(),
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
    )

    private fun pem(type: String, der: ByteArray): String =
        "-----BEGIN $type-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END $type-----\n"

    private fun decode(pem: String, type: String): ByteArray {
        val begin = "-----BEGIN $type-----"; val end = "-----END $type-----"
        val trimmed = pem.trim()
        require(trimmed.startsWith(begin) && trimmed.endsWith(end)) { "PEM 형식이 아니다($type)" }
        return try {
            Base64.getMimeDecoder().decode(trimmed.removePrefix(begin).removeSuffix(end).trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("PEM 내용을 읽지 못했다", e)
        }
    }
}
