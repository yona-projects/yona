package dev.yona.oauth2server.oidc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import dev.yona.config.YonaProperties
import org.springframework.stereotype.Component
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * OIDC 제공자 설정이다(추가 개발 항목 X-40, `decisions.md` D-77). 운영자가 정한 서명 키 파일(`yona.oauth2.oidc.signing-key-file`)이 있을 때만 [enabled]이다. 키를 정했는데 읽을 수 없으면
 * 잘못된 설정을 조용히 넘기지 않으려고 서버가 시작하지 않는다.
 */
@Component
class OidcSettings(properties: YonaProperties) {

    /** 공개키만 담은 서명 키이다(JWKS로 알린다). 꺼져 있으면 `null`이다. */
    val signingKey: RSAKey? = properties.oauth2.oidc.signingKeyFile.takeIf { it.isNotBlank() }?.let { load(File(it)) }

    val enabled: Boolean get() = signingKey != null

    companion object {
        /** ID 토큰의 서명 알고리즘이다. OIDC가 모든 제공자에게 요구하는 알고리즘이다. */
        val ALGORITHM: JWSAlgorithm = JWSAlgorithm.RS256

        /** PKCS#8 PEM의 RSA 개인키를 읽어 서명용 [RSAKey]를 만든다. 키 번호(`kid`)는 공개키의 SHA-256 지문이다. */
        fun load(file: File): RSAKey {
            check(file.isFile && file.canRead()) { "OIDC 서명 키 파일을 읽을 수 없다: ${file.path}" }
            val text = file.readText()
            check(text.contains("BEGIN PRIVATE KEY")) { "OIDC 서명 키는 PKCS#8 PEM(-----BEGIN PRIVATE KEY-----) 형식의 RSA 개인키여야 한다: ${file.path}" }
            val der = Base64.getMimeDecoder().decode(text.lineSequence().filter { !it.startsWith("-----") }.joinToString(""))
            val factory = KeyFactory.getInstance("RSA")
            val private = try { factory.generatePrivate(PKCS8EncodedKeySpec(der)) } catch (e: Exception) { throw IllegalStateException("OIDC 서명 키를 읽지 못했다(RSA 개인키가 아니다): ${file.path}", e) }
            check(private is RSAPrivateCrtKey) { "OIDC 서명 키는 RSA 개인키여야 한다: ${file.path}" }
            check(private.modulus.bitLength() >= 2048) { "OIDC 서명 키는 2048비트 이상이어야 한다: ${file.path}" }
            val public = factory.generatePublic(RSAPublicKeySpec(private.modulus, private.publicExponent)) as RSAPublicKey
            return RSAKey.Builder(public).privateKey(private).keyUse(KeyUse.SIGNATURE).algorithm(ALGORITHM).keyIDFromThumbprint().build()
        }
    }
}
