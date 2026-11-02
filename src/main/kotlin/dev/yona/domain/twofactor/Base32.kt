package dev.yona.domain.twofactor

/**
 * RFC 4648 base32이다(추가 개발 항목 X-2, `decisions.md` D-69). 인증 앱이 읽는 TOTP 비밀 값의 표기에 쓴다. 인코딩은 패딩(`=`) 없이 대문자로 쓰고, 디코딩은 소문자, 공백, 패딩을 받는다.
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    /** base32 글을 바이트로 되돌린다. 알파벳에 없는 글자가 있으면 [IllegalArgumentException]이다. */
    fun decode(text: String): ByteArray {
        val clean = text.filterNot { it.isWhitespace() || it == '=' }.uppercase()
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "base32에 없는 글자: $c" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
