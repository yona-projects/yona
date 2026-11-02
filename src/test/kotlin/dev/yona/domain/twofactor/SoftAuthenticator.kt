package dev.yona.domain.twofactor

import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * 시험용 소프트웨어 인증기이다(X-2, `decisions.md` D-69). 브라우저와 보안 키가 하는 일(등록 응답과 인증 응답 만들기)을 ES256 키 하나로 흉내 내어,
 * WebAuthn 서버 쪽 검증을 실제 암호 연산과 함께 시험한다. 응답은 브라우저가 `toJSON()`으로 내보내는 JSON 모양이다.
 */
class SoftAuthenticator(private val origin: String, private val rpId: String) {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val credentialId: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    var counter = 0

    private val url = Base64.getUrlEncoder().withoutPadding()
    private val sha256 = { data: ByteArray -> MessageDigest.getInstance("SHA-256").digest(data) }

    private fun challengeOf(optionsJson: String): String = Regex("\"challenge\"\\s*:\\s*\"([^\"]+)\"").find(optionsJson)!!.groupValues[1]

    private fun clientData(type: String, challenge: String, origin: String = this.origin) = """{"type":"$type","challenge":"$challenge","origin":"$origin","crossOrigin":false}""".toByteArray()

    /** 등록 요청 JSON(`toCredentialsCreateJson`)에 대한 응답 JSON을 만든다. [tamperChallenge]가 참이면 다른 도전 값에 답한 것처럼 보낸다. */
    fun create(optionsJson: String, tamperChallenge: Boolean = false, origin: String = this.origin): String {
        val challenge = if (tamperChallenge) url.encodeToString(ByteArray(32)) else challengeOf(optionsJson)
        val point = (keyPair.public as ECPublicKey).w
        fun fixed(v: java.math.BigInteger) = v.toByteArray().let { b -> ByteArray(32).also { out -> val src = b.takeLast(32).toByteArray(); System.arraycopy(src, 0, out, 32 - src.size, src.size) } }
        val cose = ByteArrayOutputStream().apply {
            write(byteArrayOf(0xa5.toByte(), 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x21, 0x58, 0x20)); write(fixed(point.affineX))
            write(byteArrayOf(0x22, 0x58, 0x20)); write(fixed(point.affineY))
        }.toByteArray()
        val authData = ByteArrayOutputStream().apply {
            write(sha256(rpId.toByteArray())); write(0x41); write(counterBytes(counter)); write(ByteArray(16))
            write(byteArrayOf((credentialId.size shr 8).toByte(), credentialId.size.toByte())); write(credentialId); write(cose)
        }.toByteArray()
        val attestation = ByteArrayOutputStream().apply {
            write(byteArrayOf(0xa3.toByte(), 0x63)); write("fmt".toByteArray()); write(byteArrayOf(0x64)); write("none".toByteArray())
            write(byteArrayOf(0x67)); write("attStmt".toByteArray()); write(byteArrayOf(0xa0.toByte()))
            write(byteArrayOf(0x68)); write("authData".toByteArray()); write(byteArrayOf(0x58, authData.size.toByte())); write(authData)
        }.toByteArray()
        val id = url.encodeToString(credentialId)
        return """{"type":"public-key","id":"$id","rawId":"$id","authenticatorAttachment":"cross-platform","response":{"clientDataJSON":"${url.encodeToString(clientData("webauthn.create", challenge, origin))}","attestationObject":"${url.encodeToString(attestation)}","transports":["usb"]},"clientExtensionResults":{}}"""
    }

    /** 인증 요청 JSON(`toCredentialsGetJson`)에 대한 응답 JSON을 만든다. 서명 횟수는 한 번 올린다. */
    fun get(requestJson: String, userHandle: ByteArray? = null, nextCounter: Int? = null): String {
        counter = nextCounter ?: (counter + 1)
        val data = clientData("webauthn.get", challengeOf(requestJson))
        val authData = ByteArrayOutputStream().apply { write(sha256(rpId.toByteArray())); write(0x01); write(counterBytes(counter)) }.toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(keyPair.private); update(authData + sha256(data)) }.sign()
        val id = url.encodeToString(credentialId)
        val handle = userHandle?.let { "\"${url.encodeToString(it)}\"" } ?: "null"
        return """{"type":"public-key","id":"$id","rawId":"$id","authenticatorAttachment":"cross-platform","response":{"clientDataJSON":"${url.encodeToString(data)}","authenticatorData":"${url.encodeToString(authData)}","signature":"${url.encodeToString(signature)}","userHandle":$handle},"clientExtensionResults":{}}"""
    }

    private fun counterBytes(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
}
