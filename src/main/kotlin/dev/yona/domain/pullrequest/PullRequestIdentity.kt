package dev.yona.domain.pullrequest

import dev.yona.config.YonaProperties
import dev.yona.domain.notification.NotificationMailSender
import org.eclipse.jgit.lib.PersonIdent
import org.springframework.stereotype.Component

/**
 * 시스템이 만드는 Git 커밋의 작성자이다. v1.6 `new PersonIdent(Config.getSiteName(), Config.getSystemEmailAddress())`.
 *
 * 메일 주소는 SMTP 보내는 주소가 있으면 그것이고, 없으면 `yona@yona.io`이다.
 *
 * v1.6 `Config.getSystemEmailAddress`는 SMTP 주소가 없을 때 `new SiteAdmin().admin`의 메일을 쓰려 하지만, 방금 만든 빈 객체의
 * `admin`은 항상 `null`이라 언제나 `yona@yona.io`가 된다. 사이트 관리자의 메일을 읽는 분기는 도달할 수 없는 코드이며 그대로 재현한다
 * ([refactoring-backlog.md] R-89).
 */
@Component
class PullRequestIdentity(
    private val properties: YonaProperties,
    private val mailSender: NotificationMailSender
) {

    /** 시스템 병합 커밋의 작성자이다. */
    fun system(): PersonIdent = PersonIdent(properties.siteName, systemEmail())

    private fun systemEmail(): String = mailSender.fromAddress() ?: DEFAULT_EMAIL

    private companion object {
        const val DEFAULT_EMAIL = "yona@yona.io"
    }
}
