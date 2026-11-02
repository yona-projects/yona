package dev.yona.domain.twofactor

import java.net.URLEncoder
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 시간 기반 일회용 비밀번호(TOTP, RFC 6238)이다(추가 개발 항목 X-2, `decisions.md` D-69). 인증 앱이 널리 지원하는 기본값(HMAC-SHA1, 여섯 자리, 30초 구간)을 쓴다.
 * 구간 번호(`step`)는 유닉스 시각(초)을 30으로 나눈 값이다.
 */
object Totp {

    const val PERIOD_SECONDS = 30L
    const val DIGITS = 6
    private val CODE = Regex("\\d{$DIGITS}")
    private val RANDOM = SecureRandom()

    /** 새 비밀 값이다. RFC 4226이 권하는 20바이트(160비트)이다. */
    fun newSecret(): ByteArray = ByteArray(20).also { RANDOM.nextBytes(it) }

    /** [step] 구간의 여섯 자리 코드이다(RFC 4226의 동적 잘라내기). */
    fun code(secret: ByteArray, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or ((hash[offset + 1].toInt() and 0xFF) shl 16) or ((hash[offset + 2].toInt() and 0xFF) shl 8) or (hash[offset + 3].toInt() and 0xFF)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    /**
     * 사용자가 입력한 [input]이 [nowSeconds] 기준 현재 구간과 앞뒤 한 구간(시계가 조금 어긋난 기기) 가운데 하나의 코드이면 그 구간 번호를 돌려준다. 아니면 `null`이다.
     * 입력의 공백은 무시한다. **이미 쓴 구간([lastUsedStep]) 이하의 구간은 받지 않는다**(같은 코드를 다시 쓰는 재생 공격을 막는다). 코드 비교는 일정한 시간에 한다.
     */
    fun verify(secret: ByteArray, input: String, nowSeconds: Long, lastUsedStep: Long?): Long? {
        val code = input.filterNot { it.isWhitespace() }
        if (!CODE.matches(code)) return null
        val current = nowSeconds / PERIOD_SECONDS
        var matched: Long? = null
        for (step in (current - 1)..(current + 1)) {
            // 모든 구간을 같은 방식으로 비교해 어느 구간에서 맞았는지가 시간으로 드러나지 않게 한다
            if (java.security.MessageDigest.isEqual(code(secret, step).toByteArray(), code.toByteArray()) && (lastUsedStep == null || step > lastUsedStep) && matched == null) matched = step
        }
        return matched
    }

    /** 인증 앱에 등록할 `otpauth://` 주소이다(QR 코드의 내용). 발급자와 계정은 주소에 맞게 인코딩한다. */
    fun otpauthUri(secret: ByteArray, issuer: String, account: String): String {
        fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
        return "otpauth://totp/${enc(issuer)}:${enc(account)}?secret=${Base32.encode(secret)}&issuer=${enc(issuer)}&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
    }
}
