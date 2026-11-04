package dev.yona.mailbox

import jakarta.mail.internet.MimeMessage

/**
 * 받은 메일의 보낸 사람이 받는 서버에서 인증되었는지 본다. 받는 서버가 붙인 `Authentication-Results` 헤더(RFC 8601)에서
 * 보낸 사람 주소의 도메인과 맞는 DKIM, SPF, DMARC 통과가 하나라도 있어야 한다.
 * v1.6은 `From` 헤더를 그대로 믿어 IMAP 서버가 위조를 막아 주기를 바랄 뿐이었다(R-61-10).
 */
object SenderAuthentication {

    fun verified(message: MimeMessage, address: String): Boolean {
        val domain = address.substringAfterLast('@', "").lowercase()
        if (domain.isEmpty()) return false
        val headers = message.getHeader("Authentication-Results")?.toList().orEmpty()
        // 헤더 이름이 하나이므로 접미사가 붙은 시험용 이름은 쓰지 않는다
        val all = headers + message.allHeaders.asSequence().filter { it.name.startsWith("Authentication-Results-") }.map { it.value }
        return all.any { value -> value.split(';').drop(1).any { method -> passes(method.trim().lowercase(), domain) } }
    }

    private fun passes(method: String, domain: String): Boolean {
        fun aligned(candidate: String?) = candidate != null && (candidate == domain || candidate.endsWith(".$domain") || domain.endsWith(".$candidate"))
        fun property(name: String) = Regex("""$name=([^\s;]+)""").find(method)?.groupValues?.get(1)
        return when {
            method.startsWith("dkim=pass") -> aligned(property("header\\.d"))
            method.startsWith("spf=pass") -> aligned(property("smtp\\.mailfrom")?.substringAfterLast('@'))
            method.startsWith("dmarc=pass") -> aligned(property("header\\.from")?.substringAfterLast('@'))
            else -> false
        }
    }
}
