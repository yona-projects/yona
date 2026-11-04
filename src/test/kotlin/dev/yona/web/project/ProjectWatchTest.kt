package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.notification.UserProjectNotification
import dev.yona.domain.notification.UserProjectNotificationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 구독, 구독 해제, 이벤트 종류별 알림 토글, 구독자 화면, 알림 설정 탭을 검증한다.
 *
 * 원본: `WatchProjectApp.watch`, `unwatch`, `toggle`(`app/controllers/WatchProjectApp.java`), `ProjectApp.watchers`,
 * `app/views/project/watchers.scala.html`, `app/views/user/edit_notifications.scala.html`, `models.UserProjectNotification`.
 */
@AutoConfigureMockMvc
@Transactional
class ProjectWatchTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var unwatches: UnwatchRepository
    @Autowired private lateinit var settings: UserProjectNotificationRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pw$n", name = "pw$n", email = "pw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession {
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
        return session
    }

    private fun project(): Project = projects.save(Project(owner = "pwo${++sequence}", name = "pwp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC)).also {
        menuSettings.save(ProjectMenuSetting(project = it, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
    }

    private fun watching(u: User, p: Project) = watches.existsByUserIdAndResourceTypeAndResourceId(u.id!!, ResourceType.PROJECT, p.id.toString())

    private fun setting(u: User, p: Project, type: EventType) = settings.findByUserIdAndProjectIdAndNotificationType(u.id!!, p.id!!, type).singleOrNull()

    @Test
    fun `프로젝트를 구독하면 구독 행이 생기고 구독 해제 기록은 지워진다`() {
        val u = user(); val p = project(); val s = login(u)
        mockMvc.perform(post("/${p.owner}/${p.name}/watch").session(s)).andExpect(status().isOk)
        assertTrue(watching(u, p))
        mockMvc.perform(post("/${p.owner}/${p.name}/unwatch").session(s)).andExpect(status().isOk)
        assertFalse(watching(u, p))
        assertTrue(unwatches.existsByUserIdAndResourceTypeAndResourceId(u.id!!, ResourceType.PROJECT, p.id.toString()))
        mockMvc.perform(post("/${p.owner}/${p.name}/watch").session(s)).andExpect(status().isOk)
        assertFalse(unwatches.existsByUserIdAndResourceTypeAndResourceId(u.id!!, ResourceType.PROJECT, p.id.toString()))
    }

    @Test
    fun `구독을 해제하면 그 프로젝트의 알림 설정도 지운다`() {
        val u = user(); val p = project(); val s = login(u)
        mockMvc.perform(post("/${p.owner}/${p.name}/watch").session(s))
        mockMvc.perform(post("/noti/toggle/${p.id}/NEW_COMMENT").session(s)).andExpect(status().isOk)
        assertNotNull(setting(u, p, EventType.NEW_COMMENT))
        mockMvc.perform(post("/${p.owner}/${p.name}/unwatch").session(s))
        assertNull(setting(u, p, EventType.NEW_COMMENT))
    }

    @Test
    fun `로그인하지 않으면 구독할 수 없다`() {
        val p = project()
        val status = mockMvc.perform(post("/${p.owner}/${p.name}/watch")).andReturn().response.status
        assertTrue(status in listOf(302, 303, 401, 403), status.toString())
    }

    @Test
    fun `알 수 없는 알림 종류는 500이 아니라 400이다 (C096)`() {
        val u = user(); val p = project()
        mockMvc.perform(post("/noti/toggle/${p.id}/NO_SUCH_TYPE").session(login(u))).andExpect(status().isBadRequest)
    }

    @Test
    fun `구독하지 않은 프로젝트의 알림 종류는 바꿀 수 없다`() {
        val u = user(); val p = project()
        mockMvc.perform(post("/noti/toggle/${p.id}/NEW_ISSUE").session(login(u))).andExpect(status().isBadRequest)
    }

    @Test
    fun `없는 프로젝트의 알림 종류를 바꾸면 찾을 수 없다고 한다`() {
        val u = user()
        mockMvc.perform(post("/noti/toggle/99999999/NEW_ISSUE").session(login(u))).andExpect(status().isNotFound)
    }

    @Test
    fun `기본으로 받는 알림을 한 번 끄면 받지 않겠다는 설정이, 다시 켜면 설정이 지워진다`() {
        val u = user(); val p = project(); val s = login(u)
        watches.save(Watch(user = u, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        mockMvc.perform(post("/noti/toggle/${p.id}/NEW_ISSUE").session(s)).andExpect(status().isOk)
        assertEquals(false, setting(u, p, EventType.NEW_ISSUE)?.allowed)
        mockMvc.perform(post("/noti/toggle/${p.id}/NEW_ISSUE").session(s)).andExpect(status().isOk)
        assertNull(setting(u, p, EventType.NEW_ISSUE))
    }

    @Test
    fun `기본으로 받지 않는 새 댓글 알림은 한 번 켜면 받겠다는 설정이 생긴다`() {
        val u = user(); val p = project(); val s = login(u)
        watches.save(Watch(user = u, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        mockMvc.perform(post("/noti/toggle/${p.id}/NEW_COMMENT").session(s)).andExpect(status().isOk)
        assertEquals(true, setting(u, p, EventType.NEW_COMMENT)?.allowed)
        mockMvc.perform(post("/noti/toggle/${p.id}/NEW_COMMENT").session(s)).andExpect(status().isOk)
        assertNull(setting(u, p, EventType.NEW_COMMENT))
    }

    @Test
    fun `구독자 화면은 프로젝트 구독자를 보여 준다`() {
        val u = user(); val p = project()
        watches.save(Watch(user = u, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/watchers")).andExpect(status().isOk).andReturn().response.contentAsString)
        val member = doc.selectFirst("ul.members li.member")!!
        assertEquals("/${u.loginId}", member.selectFirst("a.avatar-wrap")?.attr("href"))
        assertEquals(u.name, member.selectFirst(".member-name")?.text())
        assertEquals("@${u.loginId}", member.selectFirst(".member-id")?.text())
    }

    @Test
    fun `알림 설정 탭은 구독한 프로젝트마다 이벤트 종류의 스위치를 보여 준다`() {
        val u = user(); val p = project(); val s = login(u)
        watches.save(Watch(user = u, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        settings.save(UserProjectNotification(user = u, project = p, notificationType = EventType.NEW_ISSUE, allowed = false))
        val doc = Jsoup.parse(mockMvc.perform(get("/user/editform/notifications").session(s)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("${p.owner} / ${p.name}", doc.selectFirst("ul#notification-projects li.active a")?.text())
        assertEquals("#${p.id}", doc.selectFirst("ul#notification-projects li.active a")?.attr("href"))
        val pane = doc.selectFirst("div.tab-pane.active#${p.id}")!!
        assertEquals(EventType.entries.size, pane.select("tr").size)
        fun switch(type: EventType) = pane.selectFirst("input.notiUpdate[data-href=/noti/toggle/${p.id}/${type.name}]")!!
        assertFalse(switch(EventType.NEW_ISSUE).hasAttr("checked"))
        assertFalse(switch(EventType.NEW_COMMENT).hasAttr("checked"))
        assertTrue(switch(EventType.ISSUE_STATE_CHANGED).hasAttr("checked"))
        assertEquals(EventType.NEW_ISSUE.name, pane.select("input.notiUpdate").first()!!.attr("data-href").substringAfterLast('/'))
    }

    @Test
    fun `구독한 프로젝트가 없어도 알림 설정 탭은 열린다`() {
        val u = user()
        val doc = Jsoup.parse(mockMvc.perform(get("/user/editform/notifications").session(login(u))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.select("ul#notification-projects li").isEmpty())
    }
}
