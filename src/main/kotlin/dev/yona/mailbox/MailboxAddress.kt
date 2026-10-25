package dev.yona.mailbox

import dev.yona.access.Resource
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ResourceType
import dev.yona.markdown.SiteHostname
import dev.yona.util.UrlPaths
import org.springframework.stereotype.Component

/**
 * 사이트의 메일 수신 주소와, 알림 메일에 답장할 때 쓰는 `Reply-To` 주소를 계산한다.
 *
 * 수신 주소는 v1.6 `Config.getEmailFromImap`(`app/utils/Config.java:251-270`), `Reply-To`는 `NotificationMail.getReplyTo`
 * (`app/models/NotificationMail.java:582-615`)에 해당한다. `Reply-To`의 세부 부분(`소유자/프로젝트/종류/번호`)은
 * [MailHandler]가 받는 주소에서 댓글 대상을 찾을 때 읽는 형식과 같다.
 */
@Component
class MailboxAddress(private val properties: YonaProperties, private val hostname: SiteHostname) {

    /**
     * 사이트의 수신 주소이다. 설정의 `address`가 있으면 그 값이고, 없으면 `user`와 `domain`으로 만든다(`user`에 `@`가 있으면 그 값이 곧 주소,
     * `domain`이 비면 사이트 호스트 이름). 수신 설정(`address`, `user`)이 모두 비어 있으면 `null`이다.
     */
    fun address(): String? {
        val imap = properties.imap
        if (imap.address.isNotEmpty()) return imap.address
        if (imap.user.isEmpty()) return null
        return if (imap.user.contains("@")) imap.user else imap.user + "@" + imap.domain.ifEmpty { hostname.name() }
    }

    /**
     * [resource]에 대한 알림 메일의 `Reply-To` 주소이다. 수신 주소가 없거나 답장을 받지 않는 종류이면 `null`이다.
     *
     * 이슈, 게시글, 커밋은 자기 자신을, 댓글(이슈 댓글, 게시글 댓글, 커밋 댓글, 리뷰 댓글)은 그 댓글이 달린 컨테이너를 대상으로 한다.
     * 세부 부분은 `소유자/프로젝트/종류/번호`이며 경로 조각마다 URL 인코딩한다.
     */
    fun replyTo(resource: Resource): String? {
        val base = address() ?: return null
        val target = when (resource.type) {
            ResourceType.ISSUE_COMMENT, ResourceType.NONISSUE_COMMENT, ResourceType.COMMIT_COMMENT, ResourceType.REVIEW_COMMENT -> resource.container
            ResourceType.ISSUE_POST, ResourceType.BOARD_POST, ResourceType.COMMIT -> resource
            else -> null
        } ?: return null
        val project = target.project ?: return null
        val detail = "${project.owner}/${project.name}/${target.type.resource}/${target.id}"
        return EmailAddressWithDetail(base).also { it.detail = UrlPaths.encodeEach(detail) }.toString()
    }
}
