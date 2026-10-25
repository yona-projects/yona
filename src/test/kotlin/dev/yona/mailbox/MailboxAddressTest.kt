package dev.yona.mailbox

import dev.yona.access.ProjectResource
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.markdown.SiteHostname
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 수신 주소와 알림 메일의 `Reply-To` 계산을 검증한다.
 *
 * 원본: `Config.getEmailFromImap`(`app/utils/Config.java:251-270`), `NotificationMail.getReplyTo`(`app/models/NotificationMail.java:582-615`).
 */
class MailboxAddressTest {

    private fun address(imap: YonaProperties.Imap, hostname: String = "site.example") =
        YonaProperties(hostname = hostname, imap = imap).let { MailboxAddress(it, SiteHostname(it)) }

    private val project = Project(owner = "owner", name = "proj name", vcs = "GIT")

    private fun resource(type: ResourceType, id: String, container: ProjectResource? = null) = ProjectResource(id, type, project, container)

    @Test
    fun `수신 주소는 address가 우선이고 없으면 user와 domain으로 만든다`() {
        assertEquals("box@x.org", address(YonaProperties.Imap(address = "box@x.org", user = "ignored")).address())
        assertEquals("yona@mail.org", address(YonaProperties.Imap(user = "yona", domain = "mail.org")).address())
        assertEquals("yona@site.example", address(YonaProperties.Imap(user = "yona")).address())
        assertEquals("full@mail.org", address(YonaProperties.Imap(user = "full@mail.org", domain = "other.org")).address())
    }

    @Test
    fun `수신 설정이 없으면 주소도 Reply-To도 없다`() {
        val none = address(YonaProperties.Imap())
        assertNull(none.address())
        assertNull(none.replyTo(resource(ResourceType.ISSUE_POST, "3")))
    }

    @Test
    fun `이슈와 게시글과 커밋은 자기 세부를 쓰고 경로 조각마다 URL 인코딩한다`() {
        val box = address(YonaProperties.Imap(user = "yona", domain = "mail.org"))
        assertEquals("yona+owner/proj%20name/issue_post/3@mail.org", box.replyTo(resource(ResourceType.ISSUE_POST, "3")))
        assertEquals("yona+owner/proj%20name/board_post/7@mail.org", box.replyTo(resource(ResourceType.BOARD_POST, "7")))
        assertEquals("yona+owner/proj%20name/commit/1:abc@mail.org".replace(":", "%3A"), box.replyTo(resource(ResourceType.COMMIT, "1:abc")))
    }

    @Test
    fun `댓글은 컨테이너의 세부를 쓴다`() {
        val box = address(YonaProperties.Imap(user = "yona", domain = "mail.org"))
        val issue = resource(ResourceType.ISSUE_POST, "3")
        assertEquals("yona+owner/proj%20name/issue_post/3@mail.org", box.replyTo(resource(ResourceType.ISSUE_COMMENT, "9", issue)))
        val post = resource(ResourceType.BOARD_POST, "7")
        assertEquals("yona+owner/proj%20name/board_post/7@mail.org", box.replyTo(resource(ResourceType.NONISSUE_COMMENT, "2", post)))
        val thread = resource(ResourceType.COMMENT_THREAD, "5")
        assertEquals("yona+owner/proj%20name/comment_thread/5@mail.org", box.replyTo(resource(ResourceType.REVIEW_COMMENT, "4", thread)))
    }

    @Test
    fun `답장을 받지 않는 종류는 Reply-To가 없다`() {
        val box = address(YonaProperties.Imap(user = "yona", domain = "mail.org"))
        assertNull(box.replyTo(resource(ResourceType.MILESTONE, "1")))
        assertNull(box.replyTo(resource(ResourceType.PULL_REQUEST, "2")))
    }
}
