package dev.yona.domain.user

/**
 * 새 비밀번호의 정책이다(NIST SP 800-63B 기준, 선생님 결정 2026-11-11, `decisions.md` A-465, welcome-F3). v1.6은 서버에서 아무 검사도 하지 않아 한 글자 비밀번호도 받았다.
 *
 * - 길이는 [MIN_LENGTH]자 이상 [MAX_LENGTH]자 이하이다. 공백과 한글을 포함해 모든 글자를 쓸 수 있고 글자 수는 유니코드 글자(코드 포인트) 단위로 센다.
 *   앞뒤 공백을 자르지 않는다.
 * - 대문자, 숫자, 기호 같은 구성은 강제하지 않는다. 예측하기 쉬운 패턴으로 몰리고 오히려 짧아지기 때문이다.
 * - 흔한 비밀번호(번들 목록 `password/common-passwords.txt`)와 같은 글자만 반복한 값, 아이디나 이메일과 같은 값은 거절한다.
 * - 주기적인 변경을 강제하지 않는다.
 *
 * 이미 저장된 비밀번호는 해시라서 길이를 알 수 없으므로 검사하지 않는다. 새로 정하거나 바꿀 때부터 적용된다.
 */
object PasswordPolicy {

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128

    /** 정책을 어긴 이유이다. [messageKey]는 화면에 보이는 메시지 키이다. */
    enum class Violation(val messageKey: String) {
        TOO_SHORT("validation.tooShortPassword"),
        TOO_LONG("validation.tooLongPassword"),
        COMMON("validation.commonPassword"),
        SAME_AS_ID("validation.passwordSameAsId")
    }

    private val common: Set<String> by lazy {
        PasswordPolicy::class.java.getResourceAsStream("/password/common-passwords.txt")!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        }
    }

    /**
     * [raw]가 정책을 지키는지 본다.
     *
     * @param raw 새 비밀번호. 비어 있거나 없으면 너무 짧은 것으로 본다
     * @param identifiers 같으면 안 되는 값(로그인 아이디, 이메일). 이메일은 `@` 앞부분과도 비교한다
     * @return 어긴 이유. 지키면 `null`
     */
    fun check(raw: String?, vararg identifiers: String?): Violation? {
        val password = raw.orEmpty()
        val length = password.codePointCount(0, password.length)
        if (length < MIN_LENGTH) return Violation.TOO_SHORT
        if (length > MAX_LENGTH) return Violation.TOO_LONG
        val lower = password.lowercase()
        if (lower in common || password.codePoints().distinct().count() == 1L) return Violation.COMMON
        val same = identifiers.filterNotNull().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.flatMap { listOf(it, it.substringBefore('@')) }
        if (lower in same) return Violation.SAME_AS_ID
        return null
    }
}
