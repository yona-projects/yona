package dev.yona.domain.twofactor

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 2단계 인증 비밀 값(TOTP)의 암호화 저장이다(추가 개발 항목 X-2, `decisions.md` D-69). 인증할 때 되돌려 읽어야 하므로 해시가 아니라 AES-256-GCM으로 암호화한다. 키는 사이트 비밀 키(`yona.secret`)에서
 * HKDF-SHA256으로 만든다. 데이터베이스 백업이 새어도 사이트 비밀 키 없이는 읽을 수 없다. 저장 글은 `v1.<IV>.<암호문과 태그>`(base64url)이다. 같은 값도 매번 다른 IV로 다르게 암호화한다.
 */
class SecretCipher(siteSecret: String) {

    private val key = SecretKeySpec(Hkdf.derive(siteSecret, "yona.two-factor.secret"), "AES")
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        val url = Base64.getUrlEncoder().withoutPadding()
        return "v1.${url.encodeToString(iv)}.${url.encodeToString(cipher.doFinal(plain))}"
    }

    /** 저장 글을 되돌린다. 형식이 틀리거나 사이트 비밀 키가 다르거나 내용이 바뀌었으면 [IllegalArgumentException]이다. */
    fun decrypt(sealed: String): ByteArray {
        val parts = sealed.split('.')
        require(parts.size == 3 && parts[0] == "v1") { "저장 형식이 맞지 않는다" }
        return try {
            val url = Base64.getUrlDecoder()
            val iv = url.decode(parts[1])
            require(iv.size == IV_BYTES) { "IV 길이가 맞지 않는다" }
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }.doFinal(url.decode(parts[2]))
        } catch (e: AEADBadTagException) {
            throw IllegalArgumentException("복호화하지 못했다(키가 다르거나 내용이 바뀌었다)", e)
        } catch (e: java.security.GeneralSecurityException) {
            throw IllegalArgumentException("복호화하지 못했다", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("복호화하지 못했다", e)
        }
    }

    private companion object {
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** 사이트 비밀 키에서 용도별 키를 만든다(HKDF-SHA256, RFC 5869, 키 하나 길이 32바이트). */
internal object Hkdf {

    fun derive(secret: String, info: String): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("yona-two-factor".toByteArray(), "HmacSHA256")) }
        val pseudoRandomKey = extract.doFinal(secret.toByteArray())
        val expand = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(pseudoRandomKey, "HmacSHA256")) }
        return expand.doFinal(info.toByteArray() + byteArrayOf(1))
    }
}
