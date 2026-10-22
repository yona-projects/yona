package dev.yona.domain.pullrequest

import dev.yona.config.YonaProperties
import dev.yona.domain.notification.NotificationMailSender
import dev.yona.domain.user.UserRepository
import org.eclipse.jgit.lib.PersonIdent
import org.springframework.stereotype.Component

/**
 * 시스템이 만드는 Git 커밋의 작성자이다. v1.6 `new PersonIdent(Config.getSiteName(), Config.getSystemEmailAddress())`.
 *
 * 메일 주소는 SMTP 보내는 주소가 있으면 그것이고, 없으면 사이트 관리자(`admin`)의 메일, 그것도 없으면 `yona@yona.io`이다.
 */
@Component
class PullRequestIdentity(
    private val properties: YonaProperties,
    private val mailSender: NotificationMailSender,
    private val users: UserRepository
) {

    /** 시스템 병합 커밋의 작성자이다. */
    fun system(): PersonIdent = PersonIdent(properties.siteName, systemEmail())

    private fun systemEmail(): String =
        mailSender.fromAddress() ?: users.findByLoginIdIgnoreCase(ADMIN_LOGIN_ID)?.email?.takeIf { it.isNotEmpty() } ?: DEFAULT_EMAIL

    private companion object {
        const val ADMIN_LOGIN_ID = "admin"
        const val DEFAULT_EMAIL = "yona@yona.io"
    }
}
