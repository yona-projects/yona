package dev.yona.web.home

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.notification.NotificationEvent
import dev.yona.domain.notification.NotificationMessages
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.domain.user.User
import dev.yona.security.AvatarService
import dev.yona.util.HtmlSanitizer
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.Locale

/**
 * 홈의 알림 목록 항목 하나이다.
 *
 * @property id 알림 번호. 본문 펼침 영역의 식별자로 쓴다
 * @property iconClass 종류 아이콘의 클래스 이름 (예: `list-alt closed`)
 * @property edit 본문이나 댓글 수정 알림이면 `true`. 아이콘 대신 "Edit" 글자를 보여 준다
 * @property title 제목
 * @property url 제목이 연결할 주소. 이동할 곳이 없으면 `null`이라 제목만 보여 준다
 * @property messageHtml 본문. 줄바꿈을 `<br/>`로 바꾸고 정화한 HTML이다
 * @property sender 보낸 사람. 지워졌으면 `null`이다
 * @property senderName 보낸 사람의 표시 이름
 * @property avatarUrl 보낸 사람의 아바타 주소
 * @property created 만든 시각
 */
data class NotificationItem(
    val id: Long, val iconClass: String, val edit: Boolean, val title: String?, val url: String?, val messageHtml: String,
    val sender: User?, val senderName: String?, val avatarUrl: String, val created: Date?
)

/**
 * 알림 이벤트를 홈의 알림 목록 항목으로 바꾼다. v1.6 `app/views/index/partial_notifications.scala.html`의 항목 계산에 해당한다.
 *
 * 본문은 받는 사람의 언어가 아니라 서버 기본 언어로 만든다(v1.6이 `Lang.defaultLang()`을 쓴다). 문구를 만들 수 없는 알림은 v1.6에서는
 * 화면 전체를 오류로 만들지만, v2는 빈 본문으로 보여 준다(A-59).
 */
@Component
class NotificationItemAssembler(
    private val messages: NotificationMessages,
    private val users: UserRepository,
    private val avatars: AvatarService
) {

    @Transactional(readOnly = true)
    fun assemble(events: List<NotificationEvent>, viewer: User?): List<NotificationItem> = events.map { event ->
        val sender = event.senderId?.let { users.findById(it).orElse(null) }
        val message = messages.message(event, Locale.getDefault().toLanguageTag()).orEmpty()
        NotificationItem(
            id = requireNotNull(event.id), iconClass = icon(requireNotNull(event.eventType), event.newValue),
            edit = event.eventType == EventType.ISSUE_BODY_CHANGED || event.eventType == EventType.COMMENT_UPDATED,
            title = event.title, url = messages.urlToView(event),
            messageHtml = HtmlSanitizer.defaultSanitize(message.replace(Regex("(?<!>)\n"), "<br/>\n")),
            sender = sender, senderName = sender?.let { UserDisplay.of(it, viewer?.lang) },
            avatarUrl = sender?.let { avatars.avatarUrl(it) } ?: DEFAULT_AVATAR, created = event.created
        )
    }

    /** 알림 종류와 새 값에 맞는 아이콘 클래스이다 (v1.6 `notifycationIcon`). */
    private fun icon(type: EventType, state: String?): String = when (type) {
        EventType.NEW_COMMENT, EventType.NEW_REVIEW_COMMENT, EventType.REVIEW_THREAD_STATE_CHANGED -> "comment2"
        EventType.NEW_ISSUE, EventType.ISSUE_STATE_CHANGED -> if (state == "closed") "list-alt closed" else "list-alt"
        EventType.ISSUE_ASSIGNEE_CHANGED -> "friends changed"
        EventType.NEW_POSTING -> "edit2"
        EventType.NEW_PULL_REQUEST, EventType.PULL_REQUEST_COMMIT_CHANGED, EventType.PULL_REQUEST_STATE_CHANGED ->
            if (state == "closed") "merge closed" else if (state == "merged") "merge merged" else "merge"
        EventType.MEMBER_ENROLL_REQUEST -> if (state == "ACCEPT") "addfriend closed" else if (state == "CANCEL") "addfriend rejected" else "addfriend"
        EventType.NEW_COMMIT -> "push"
        EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> "preview changed"
        EventType.ISSUE_BODY_CHANGED, EventType.COMMENT_UPDATED -> "ellipsis-horizontal"
        else -> "megaphone"
    }

    private companion object {
        const val DEFAULT_AVATAR = "/assets/images/default-avatar-64.png"
    }
}
