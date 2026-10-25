package dev.yona.mailbox

import jakarta.mail.BodyPart
import jakarta.mail.internet.ContentType
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimePart

/**
 * 세부 부분(detail)을 분해한 이메일 주소이다. v1.6 `mailbox.EmailAddressWithDetail`(`app/mailbox/EmailAddressWithDetail.java`)에 해당한다.
 *
 * 사용자 부분에서 더하기 기호 뒤가 세부 부분이다. 예: `yona+owner/project@mail.com`은 사용자 `yona`, 세부 `owner/project`, 도메인 `mail.com`.
 *
 * @throws IllegalArgumentException `@`가 없어 주소가 아닐 때 (v1.6은 문자열 범위 오류이다)
 */
class EmailAddressWithDetail(address: String) {

    /** 사용자 부분(더하기 기호 앞)이다. */
    val user: String

    /** 세부 부분(더하기 기호 뒤, `@` 앞)이다. 없으면 빈 문자열이다. */
    var detail: String

    /** 도메인 부분이다. */
    val domain: String

    init {
        val plus = address.indexOf('+')
        val at = address.indexOf('@')
        require(at >= 0) { "이메일 주소가 아닙니다: $address" }
        if (plus < 0 || plus > at) {
            user = address.substring(0, at)
            detail = ""
        } else {
            user = address.substring(0, plus)
            detail = address.substring(plus + 1, at)
        }
        domain = address.substring(at + 1)
    }

    /** 세부 부분을 뺀 사용자와 도메인이 [that]과 같으면 `true`이다. */
    fun equalsExceptDetails(that: EmailAddressWithDetail): Boolean = user == that.user && domain == that.domain

    /** [yonaAddress](설정의 `imap.address`, 없으면 `imap.user`)로 오는 메일이면 `true`이다. */
    fun isToYona(yonaAddress: String): Boolean = equalsExceptDetails(EmailAddressWithDetail(yonaAddress))

    /** v1.6처럼 세부 부분이 비어 있어도 더하기 기호를 붙인다. */
    override fun toString(): String = "$user+$detail@$domain"
}

/** 메시지 ID(`<id-left@id-right>`) 헤더를 읽는다. v1.6 `EmailHandler.parseMessageIds`, `IMAPMessageUtil.getIdLeftFromMessageId`이다. */
object MessageIds {

    // RFC 5322의 msg-id 앞뒤로 올 수 있는 주석(CFWS)을 건너뛴다.
    private val CFWS = "[^<]*(?:\\([^\\(]*\\))?[^<]*"
    private val PATTERN = Regex("$CFWS(<[^>]*>)$CFWS")

    /** `In-Reply-To`, `References` 헤더 값에서 꺾쇠로 감싼 메시지 ID를 모두 찾는다. */
    fun parse(headerValue: String): List<String> = PATTERN.findAll(headerValue).map { it.groupValues[1] }.toList()

    /**
     * 메시지 ID의 왼쪽 부분을 리소스 경로로 돌려준다. 맨 앞의 `/`는 뗀다. v1.6이 알림 메일의 메시지 ID를 `<경로@도메인>`으로 만든다.
     *
     * @throws IndexOutOfBoundsException `<` 뒤에 `@`가 없을 때
     */
    fun idLeft(messageId: String): String {
        val start = messageId.indexOf('<')
        val at = messageId.indexOf('@')
        return messageId.substring(start + 1, at).trim().replaceFirst(Regex("^/"), "")
    }
}

/**
 * 메일에서 읽은 본문과 첨부이다. v1.6 `mailbox.Content`와 `CreationViaEmail.extractContent`에 해당한다.
 *
 * @property body 본문
 * @property attachments 첨부로 볼 파트(파일 이름이 있는 파트, 관련 파트의 본문이 아닌 파트)
 * @property type 본문의 콘텐츠 형식. 본문이 없으면 `null`
 */
class MailContent(var body: String = "", val attachments: MutableList<MimePart> = mutableListOf(), var type: String? = null) {

    /** 본문이 HTML이면 `true`이다. */
    val isHtml: Boolean get() = type != null && ContentType(type).match("text/html")

    /** [that]의 본문을 뒤에 이어 붙이고 첨부를 더한다. 형식은 비어 있을 때만 [that]의 것을 쓴다. */
    fun merge(that: MailContent): MailContent {
        body += that.body
        if (type.isNullOrEmpty()) type = that.type
        attachments.addAll(that.attachments)
        return this
    }

    companion object {
        private const val RELATED = "multipart/related"
        private const val ALTERNATIVE = "multipart/alternative"
        private const val HTML = "text/html"
        private const val PLAIN = "text/plain"

        /** 메일(또는 파트)에서 본문과 첨부를 읽는다. */
        fun extract(part: MimePart): MailContent = process(part, null)

        private fun process(part: MimePart?, parent: MimePart?): MailContent {
            if (part == null) return MailContent()
            // 파일 이름이 있는 파트는 첨부로 본다.
            if (part.fileName != null) return MailContent(attachments = mutableListOf(part))
            if (part.isMimeType("text/*")) return MailContent(part.content as String, type = part.contentType)
            if (part.isMimeType("multipart/*")) {
                return when {
                    part.isMimeType(RELATED) -> withAttachments(part)
                    part.isMimeType(ALTERNATIVE) -> bestPart(part, parent)
                    else -> joined(part)
                }
            }
            return MailContent()
        }

        private fun joined(part: MimePart): MailContent {
            val result = MailContent()
            val multipart = part.content as MimeMultipart
            for (i in 0 until multipart.count) result.merge(process(multipart.getBodyPart(i) as MimeBodyPart, part))
            return result
        }

        private fun bestPart(part: MimePart, parent: MimePart?): MailContent {
            var best: MimeBodyPart? = null
            val multipart = part.content as MimeMultipart
            for (i in 0 until multipart.count) {
                // 부모가 관련 파트이면 인라인 이미지를 담을 수 있는 HTML을 우선한다.
                best = better(multipart.getBodyPart(i) as MimeBodyPart, best, parent != null && parent.isMimeType(RELATED))
            }
            return process(best, part)
        }

        private fun withAttachments(part: MimePart): MailContent {
            var result = MailContent()
            val rootId = ContentType(part.contentType).getParameter("start")
            val multipart = part.content as MimeMultipart
            for (i in 0 until multipart.count) {
                val child = multipart.getBodyPart(i) as MimePart
                if (isRoot(child, i, rootId)) result = result.merge(process(child, part)) else result.attachments.add(child)
            }
            return result
        }

        /** 첫 파트이고 `start`가 없거나, 콘텐츠 ID가 `start`와 같은 파트가 본문이다. */
        private fun isRoot(part: MimePart, index: Int, rootId: String?): Boolean = (rootId == null && index == 0) || part.contentID == rootId

        private fun point(part: BodyPart?, preferences: List<String>): Int {
            if (part == null) return 0
            for ((i, preference) in preferences.withIndex()) if (part.isMimeType(preference)) return preferences.size + 1 - i
            return 1
        }

        /** 관련 파트 > 텍스트 > 그 밖의 순이다(관련 파트 안에서는 관련 파트 > HTML > 텍스트). */
        private fun better(part: MimeBodyPart, best: MimeBodyPart?, htmlPreferred: Boolean): MimeBodyPart? {
            val preferences = if (htmlPreferred) listOf(RELATED, HTML, PLAIN) else listOf(RELATED, PLAIN, HTML)
            return if (point(part, preferences) > point(best, preferences)) part else best
        }
    }
}
