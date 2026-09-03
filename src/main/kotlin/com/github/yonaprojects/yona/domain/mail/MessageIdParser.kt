package com.github.yonaprojects.yona.domain.mail

/**
 * yona의 mailbox/EmailHandler.java의 parseMessageIds(String)에 대응.
 * In-Reply-To / References 헤더 값에서 "<id@host>" 형태의 message-id들을 추출한다.
 */
object MessageIdParser {
    private val pattern = Regex("<[^>]*>")

    fun parse(headerValue: String?): List<String> {
        if (headerValue.isNullOrBlank()) {
            return emptyList()
        }
        return pattern.findAll(headerValue).map { it.value }.toList()
    }
}
