package dev.yona.web.user

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.RecentProject
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.user.Email
import dev.yona.domain.user.EmailRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 계정 설정 화면을 검증한다. 프로필(`GET /user/editform`, `POST /user/edit`), 비밀번호 변경(`POST /user/resetPassword`),
 * 토큰, 방문 목록 초기화.
 *
 * 원본: `app/controllers/UserApp.java:582-626, 877-990`, `app/views/user/edit.scala.html`, `edit_password.scala.html`,
 * `edit_token.scala.html`, `partial_edit_tabmenu.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-profile"])
class ProfileEditTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var sessionUsers: dev.yona.security.SessionUserCache
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var emails: EmailRepository
    @Autowired private lateinit var attachments: AttachmentService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var recentProjects: RecentProjectRepository

    private var sequence = 0
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @AfterEach
    fun clean() {
        File("build/tmp/it-profile").deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "pe$n", name = "이름$n", email = "pe$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("old-pass", salt)
            )
        )
    }

    private fun login(user: User, password: String = "old-pass"): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", password))
    }

    private fun page(path: String, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    // --- 프로필 화면 ---

    @Test
    fun `프로필 화면은 로그인해야 열 수 있다`() {
        mockMvc.perform(get("/user/editform"))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/users/loginform?redirectUrl=/user/editform"))
            .andExpect(flash().attribute("warning", "user.login.alert"))
    }

    @Test
    fun `프로필 화면의 구성`() {
        val user = newUser()
        val page = page("/user/editform", login(user))
        assertEquals(user.loginId, page.title())
        assertEquals("Account", page.selectFirst(".site-breadcrumb-inner h3")?.text())
        val basic = page.selectFirst("form#frmBasic")!!
        assertEquals("/user/edit", basic.attr("action"))
        assertEquals(user.loginId, basic.select("input")[0].attr("value"))
        assertTrue(basic.select("input")[0].hasAttr("readonly"))
        assertEquals(user.name, basic.selectFirst("input[name=name]")?.attr("value"))
        assertEquals(user.email, basic.selectFirst("input[name=email][type=email]")?.attr("value"))
        assertEquals("Edit profile", basic.selectFirst("button")?.text())
        val avatar = page.selectFirst("form#frmAvatar")!!
        assertEquals(user.name, avatar.selectFirst("input[type=hidden][name=name]")?.attr("value"))
        assertNotNull(avatar.selectFirst("input#avatarFile[type=file][name=filePath]"))
        assertEquals("/user/resetVisitedList", page.selectFirst(".reset-user-visited-list form")?.attr("action"))
        assertNotNull(page.selectFirst("#avatarCropWrap"))
        assertTrue(page.select("script").any { "user.Setting" in it.data() })
    }

    @Test
    fun `탭 메뉴는 다섯 개이고 현재 탭이 활성이다`() {
        val page = page("/user/editform", login(newUser()))
        val tabs = page.select(".page-wrap > ul.nav-tabs li")
        assertEquals(listOf("Edit profile", "Change password", "Notification settings", "Email settings", "User Token"), tabs.map { it.text().trim() })
        assertEquals(listOf("/user/editform", "/user/editform/password", "/user/editform/notifications", "/user/editform/emails", "/user/editform/token"), tabs.map { it.selectFirst("a")!!.attr("href") })
        assertTrue(tabs[0].hasClass("active"))
    }

    // --- 프로필 저장 ---

    private fun edit(session: MockHttpSession, name: String?, email: String?, avatarId: String? = null): MvcResult =
        mockMvc.perform(
            post("/user/edit").session(session).apply { name?.let { param("name", it) } }.apply { email?.let { param("email", it) } }
                .apply { avatarId?.let { param("avatarId", it) } }
        ).andReturn()

    @Test
    fun `이름과 이메일을 저장하고 사용자 페이지로 간다`() {
        val user = newUser()
        val result = edit(login(user), "새 이름", "changed@example.com")
        assertEquals(303, result.response.status)
        assertEquals("/${user.loginId}?daysAgo=14&selected=projects", result.response.getHeader("Location"))
        users.flush()
        val saved = users.findById(user.id!!).get()
        assertEquals("새 이름", saved.name)
        assertEquals("changed@example.com", saved.email)
    }

    @Test
    fun `이름의 위험한 태그는 정화한다`() {
        val user = newUser()
        edit(login(user), "<script>x</script><i>이탤릭</i>", user.email)
        users.flush()
        assertEquals("<i>이탤릭</i>", users.findById(user.id!!).get().name)
    }

    @Test
    fun `이메일이 비었으면 거부하고 안내한다`() {
        val user = newUser()
        val result = edit(login(user), "x", "")
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("Wrong email address."))
        assertEquals(user.email, users.findById(user.id!!).get().email)
    }

    @Test
    fun `이메일 형식이 틀리면 거부하고 안내한다 (v1_6 Constraints_Email)`() {
        val user = newUser()
        val result = edit(login(user), "x", "not-an-email")
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("Wrong email address."))
        assertEquals(user.email, users.findById(user.id!!).get().email)
    }

    @Test
    fun `프로필을 저장하면 서버 캐시 항목이 새것으로 바뀐다 (v1_6 editUserInfo의 yonaUsers_put)`() {
        val user = newUser()
        sessionUsers.remember(user.id!!)
        val before = sessionUsers.memoOf(user.id!!)
        assertEquals(303, edit(login(user), "새 이름", user.email).response.status)
        org.junit.jupiter.api.Assertions.assertNotSame(before, sessionUsers.memoOf(user.id!!))
    }

    @Test
    fun `다른 사용자가 쓰는 이메일로 바꿀 수 없다`() {
        val other = newUser()
        val user = newUser()
        val result = edit(login(user), "x", other.email)
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("Email address already exists"))
    }

    @Test
    fun `자기 이메일을 그대로 두는 것은 중복이 아니다`() {
        val user = newUser()
        assertEquals(303, edit(login(user), "x", user.email).response.status)
    }

    @Test
    fun `이메일을 바꾸면 같은 주소의 인증 전 보조 이메일을 지운다`() {
        val other = newUser()
        val user = newUser()
        emails.save(Email(user = other, email = "fresh@example.com", valid = false, token = "t"))
        edit(login(user), "x", "fresh@example.com")
        assertTrue(emails.findAll().none { it.email == "fresh@example.com" && it.valid != true })
    }

    @Test
    fun `올려 둔 이미지를 아바타로 옮긴다`() {
        val user = newUser()
        val file = File.createTempFile("avatar-up", ".png").apply { writeBytes(png) }
        val temp = attachments.store(file, "me.png", ResourceType.USER, user.id!!, user.loginId).attachment
        edit(login(user), "x", user.email, temp.id.toString())
        assertEquals(listOf("me.png"), attachments.findByContainer(ResourceType.USER_AVATAR, user.id!!).map { it.name })
        assertTrue(attachments.findByContainer(ResourceType.USER, user.id!!).isEmpty())
    }

    @Test
    fun `새 아바타는 이전 아바타를 대체한다`() {
        val user = newUser()
        val session = login(user)
        listOf("first.png" to png, "second.png" to png + 9).forEach { (name, bytes) ->
            val file = File.createTempFile("avatar-up", ".png").apply { writeBytes(bytes) }
            val temp = attachments.store(file, name, ResourceType.USER, user.id!!, user.loginId).attachment
            edit(session, "x", user.email, temp.id.toString())
        }
        assertEquals(listOf("second.png"), attachments.findByContainer(ResourceType.USER_AVATAR, user.id!!).map { it.name })
    }

    @Test
    fun `이미지가 아니거나 숫자가 아니면 아바타를 바꾸지 않는다`() {
        val user = newUser()
        val session = login(user)
        val text = File.createTempFile("avatar-tx", ".txt").apply { writeText("not an image") }
        val notImage = attachments.store(text, "a.txt", ResourceType.USER, user.id!!, user.loginId).attachment
        edit(session, "x", user.email, notImage.id.toString())
        edit(session, "x", user.email, "abc")
        assertTrue(attachments.findByContainer(ResourceType.USER_AVATAR, user.id!!).isEmpty())
    }

    @Test
    fun `남이 올린 이미지 첨부도 아바타로 옮긴다 (v1_6은 소유자를 검사하지 않는다)`() {
        val user = newUser()
        val other = newUser()
        val foreignFile = File.createTempFile("avatar-fo", ".png").apply { writeBytes(png + 5) }
        val foreign = attachments.store(foreignFile, "f.png", ResourceType.USER, other.id!!, other.loginId).attachment
        edit(login(user), "x", user.email, foreign.id.toString())
        assertEquals(listOf("f.png"), attachments.findByContainer(ResourceType.USER_AVATAR, user.id!!).map { it.name })
        assertTrue(attachments.findByContainer(ResourceType.USER, other.id!!).isEmpty())
    }

    @Test
    fun `프로필 저장은 로그인해야 한다`() {
        mockMvc.perform(post("/user/edit").param("name", "x").param("email", "x@example.com")).andExpect(status().isSeeOther)
    }

    // --- 비밀번호 ---

    private fun changePassword(session: MockHttpSession?, old: String?, new: String?): MvcResult =
        mockMvc.perform(
            post("/user/resetPassword").apply { session?.let { session(it) } }
                .apply { old?.let { param("oldPassword", it) } }.apply { new?.let { param("password", it) } }
        ).andReturn()

    @Test
    fun `비밀번호 탭의 구성`() {
        val page = page("/user/editform/password", login(newUser()))
        val form = page.selectFirst("form#frmPassword")!!
        assertEquals("/user/resetPassword", form.attr("action"))
        assertEquals(listOf("loginId", "oldPassword", "password", "retypedPassword"), form.select("input").map { it.attr("name") })
        assertEquals("/lostPassword", page.selectFirst("a.ybtn-fail")?.attr("href"))
        assertTrue(page.select(".page-wrap > ul.nav-tabs li")[1].hasClass("active"))
    }

    @Test
    fun `현재 비밀번호가 맞으면 바꾸고 로그아웃한 뒤 로그인 화면으로 보낸다`() {
        val user = newUser()
        val session = login(user)
        val result = changePassword(session, "old-pass", "new-pass")
        assertEquals(303, result.response.status)
        assertEquals("/users/loginform", result.response.getHeader("Location"))
        users.flush()
        val saved = users.findById(user.id!!).get()
        assertEquals(LegacyPasswordHasher.hash("new-pass", saved.passwordSalt!!), saved.password)
        mockMvc.perform(get("/user/editform").session(session)).andExpect(status().isSeeOther)
    }

    @Test
    fun `현재 비밀번호가 틀리면 안내와 함께 400으로 편집 화면을 보여 준다`() {
        val user = newUser()
        val result = changePassword(login(user), "wrong", "new-pass")
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("Wrong password!"))
        assertNotNull(Jsoup.parse(result.response.contentAsString).selectFirst("form#frmBasic"))
        users.flush()
        assertEquals(LegacyPasswordHasher.hash("old-pass", user.passwordSalt!!), users.findById(user.id!!).get().password)
    }

    @Test
    fun `비밀번호 변경은 로그인해야 한다`() {
        assertEquals(303, changePassword(null, "a", "b").response.status)
    }

    // --- 토큰 ---

    @Test
    fun `토큰 탭은 토큰이 없으면 만들어 보여 준다`() {
        val user = newUser()
        val page = page("/user/editform/token", login(user))
        val token = page.selectFirst("input[name=name][readonly]")!!.attr("value")
        assertEquals(44, token.length)
        users.flush()
        assertEquals(token, users.findById(user.id!!).get().token)
        assertEquals("/user/editform/token_reset", page.selectFirst("form#frmBasic")?.attr("action"))
        assertEquals("Recreate User Token", page.selectFirst("form#frmBasic button")?.text())
    }

    @Test
    fun `토큰은 다시 열어도 그대로이고 다시 만들면 바뀐다`() {
        val user = newUser()
        val session = login(user)
        val first = page("/user/editform/token", session).selectFirst("input[name=name]")!!.attr("value")
        assertEquals(first, page("/user/editform/token", session).selectFirst("input[name=name]")!!.attr("value"))
        val reset = Jsoup.parse(mockMvc.perform(post("/user/editform/token_reset").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertNotEquals(first, reset.selectFirst("input[name=name]")!!.attr("value"))
    }

    @Test
    fun `설정 화면의 토큰은 웹 경로 요청을 인증하지 못한다 (X-5, D-36, 토큰은 외부 API에서만 쓴다)`() {
        val user = newUser()
        val token = page("/user/editform/token", login(user)).selectFirst("input[name=name]")!!.attr("value")
        val status = mockMvc.perform(get("/user/editform").header("Yona-Token", token)).andReturn().response.status
        assertTrue(status in 300..403 && status != 200, "웹 경로는 토큰을 인정하지 않는다: $status")
    }

    @Test
    fun `알 수 없는 탭 이름은 오류이다`() {
        mockMvc.perform(get("/user/editform/nothing").session(login(newUser()))).andExpect(status().is5xxServerError)
    }

    @Test
    fun `탭 화면은 로그인해야 한다`() {
        mockMvc.perform(get("/user/editform/password")).andExpect(status().isSeeOther)
    }

    // --- 방문 목록 ---

    @Test
    fun `최근 방문 목록을 초기화한다`() {
        val user = newUser()
        val project = projects.save(Project(owner = "pe-o${++sequence}", name = "pp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        recentProjects.save(RecentProject(userId = user.id, owner = project.owner, project = project, projectName = project.name))
        mockMvc.perform(post("/user/resetVisitedList").session(login(user)))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/user/editform"))
            .andExpect(flash().attribute("info", "userinfo.reset.visited.project.list.done"))
        recentProjects.flush()
        assertTrue(recentProjects.findByUserIdOrderByIdDesc(user.id!!).isEmpty())
        assertNull(null)
    }
}
