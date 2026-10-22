package dev.yona.domain.notification

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import java.util.Locale

/**
 * 알림 메일의 받는 사람을 거르고 나누는 규칙이다. v1.6 `NotificationMail.sendNotification`,
 * `getUsersUsingNoAcceptableEmails`, `isAllowedEmailDomains`, `getPartialRecipientSize`(`app/models/NotificationMail.java:350-460`)에 해당한다.
 */
object MailRecipients {

    /**
     * 메일을 받을 수 있는 사용자만 남긴다. 활성 상태가 아닌 사용자는 빼고, [allowedDomains]를 정했으면
     * 이메일 도메인이 그 목록에 없는 사용자도 뺀다.
     */
    fun eligible(users: Collection<User>, allowedDomains: String): List<User> =
        users.filter { it.state == UserState.ACTIVE && isAllowedDomain(it.email, allowedDomains) }

    /**
     * 이메일 도메인이 허용 목록(쉼표로 이은 도메인, 대소문자와 앞뒤 공백 무시)에 있으면 `true`이다.
     * 목록이 비어 있으면 모두 허용한다.
     */
    fun isAllowedDomain(email: String?, allowedDomains: String): Boolean {
        if (allowedDomains.isBlank()) return true
        val domain = domainOf(email) ?: return false
        return allowedDomains.split(",").map { it.trim().lowercase() }.contains(domain.lowercase())
    }

    private fun domainOf(email: String?): String? {
        if (email == null) return null
        val at = email.lastIndexOf('@')
        return if (at < 0 || at + 1 >= email.length) null else email.substring(at + 1)
    }

    /**
     * 메일 한 통에 넣을 받는 사람 수이다. 제한([limit])이 0이면 [count] 전체이고, 주소를 숨기면(받는 사람 칸에 사이트 주소 한 개가 들어가므로)
     * 제한에서 1을 뺀다. 0 이하이면 보낼 수 없다는 뜻이다.
     */
    fun partialSize(count: Int, limit: Int, hideAddress: Boolean): Int = when {
        limit == 0 -> count
        hideAddress -> limit - 1
        else -> limit
    }

    /** 선호 언어별로 묶는다. 언어를 정하지 않은 사용자는 서버 기본 언어이다 (v1.6 `User.getPreferredLanguage`). */
    fun byLanguage(users: Collection<User>): Map<String, List<User>> =
        users.groupByTo(LinkedHashMap()) { it.lang ?: Locale.getDefault().language }
}
