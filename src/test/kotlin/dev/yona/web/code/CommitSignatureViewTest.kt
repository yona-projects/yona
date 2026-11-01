package dev.yona.web.code

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.gpgkey.GpgKeyAddition
import dev.yona.domain.gpgkey.GpgKeyService
import dev.yona.domain.gpgkey.SignedGitObjects
import dev.yona.domain.gpgkey.TestGpgKey
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.CommitHistoryService
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 커밋과 태그의 서명 표시를 검증한다(X-4 4단계, `decisions.md` D-65). 서명이 없으면 아무것도 보이지 않고, 서명이 있으면 Verified 또는 Unverified(이유 포함)가 보인다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class CommitSignatureViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var keyService: GpgKeyService
    @Autowired private lateinit var history: CommitHistoryService

    private var sequence = 0

    private fun project(): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "sgo$n", name = "sgp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun user(email: String, loginId: String): User = users.save(User(loginId = loginId, name = "이름$loginId", email = email, state = UserState.ACTIVE))

    private fun page(path: String): Document = Jsoup.parse(mockMvc.perform(get(java.net.URI.create(path))).andExpect(status().isOk).andReturn().response.contentAsString)

    /** [owner]의 키를 만들어 등록한다. */
    private fun registered(owner: User, userId: String): TestGpgKey = TestGpgKey.generate(userId = userId).also { assertTrue(keyService.add(owner, "k", it.publicArmored) is GpgKeyAddition.Added) }

    @Test
    fun `커밋 이력은 서명 여부에 따라 Verified와 Unverified를 보이고 서명이 없으면 아무것도 보이지 않는다`() {
        val p = project()
        val alice = user("alice@example.com", "alice")
        val key = registered(alice, "Alice <alice@example.com>")
        val unknown = TestGpgKey.generate(userId = "Eve <eve@example.com>")
        val c1 = SignedGitObjects.commit(repositories, p, key, "alice@example.com", "검증됨")
        val c2 = SignedGitObjects.commit(repositories, p, unknown, "eve@example.com", "모르는 키", parent = c1)
        val c3 = SignedGitObjects.commit(repositories, p, null, "alice@example.com", "서명 없음", parent = c2)
        SignedGitObjects.commit(repositories, p, key, "someone-else@example.com", "이메일 불일치", parent = c3)
        val rows = page("/${p.owner}/${p.name}/commits").select("tbody tr")
        assertEquals(listOf("이메일 불일치", "서명 없음", "모르는 키", "검증됨"), rows.map { it.selectFirst("a.commitMsg.short")!!.text() })
        val badges = rows.map { it.selectFirst(".gpg-badge") }
        assertEquals("bad_email", badges[0]!!.attr("data-reason"))
        assertTrue(badges[0]!!.hasClass("gpg-unverified") && badges[0]!!.text() == "Unverified")
        assertNull(badges[1], "서명이 없으면 배지가 없다")
        assertEquals("unknown_key", badges[2]!!.attr("data-reason"))
        assertTrue(badges[3]!!.hasClass("gpg-verified") && badges[3]!!.text() == "Verified")
        assertEquals("valid", badges[3]!!.attr("data-reason"))
        assertTrue(badges[0]!!.attr("title").isNotBlank() && !badges[0]!!.attr("title").startsWith("gpgKey."), "이유는 번역된 문구이다")
    }

    @Test
    fun `커밋 이력 조회가 서명 정보를 CommitInfo에 담는다`() {
        val p = project()
        val key = registered(user("alice@example.com", "alice"), "Alice <alice@example.com>")
        val c1 = SignedGitObjects.commit(repositories, p, key, "alice@example.com")
        val c2 = SignedGitObjects.commit(repositories, p, null, "alice@example.com", "서명 없음", parent = c1)
        val commits = history.history(p, 0, 10, null, null)!!
        assertNull(commits.first { it.id == c2 }.signature)
        assertNotNull(commits.first { it.id == c1 }.signature)
    }

    @Test
    fun `커밋 상세는 서명 상자에 판정과 서명한 사람과 키 ID를 보인다`() {
        val p = project()
        val alice = user("alice@example.com", "alice")
        val key = registered(alice, "Alice <alice@example.com>")
        val id = SignedGitObjects.commit(repositories, p, key, "alice@example.com")
        val box = page("/${p.owner}/${p.name}/commit/$id").selectFirst(".gpg-signature-box")!!
        assertTrue(box.selectFirst(".gpg-badge")!!.hasClass("gpg-verified"))
        assertEquals("alice", box.selectFirst(".gpg-signer")!!.text())
        assertEquals(key.keyId, box.selectFirst(".gpg-key-id")!!.text())
    }

    @Test
    fun `커밋 상세에서 서명이 없으면 서명 상자가 없다`() {
        val p = project()
        val id = SignedGitObjects.commit(repositories, p, null, "alice@example.com")
        assertNull(page("/${p.owner}/${p.name}/commit/$id").selectFirst(".gpg-signature-box"))
    }

    @Test
    fun `서명한 커밋을 바꾸면 Unverified invalid이다`() {
        val p = project()
        val key = registered(user("alice@example.com", "alice"), "Alice <alice@example.com>")
        val id = SignedGitObjects.commit(repositories, p, key, "alice@example.com", tamper = true)
        val badge = page("/${p.owner}/${p.name}/commit/$id").selectFirst(".gpg-signature-box .gpg-badge")!!
        assertEquals("invalid", badge.attr("data-reason"))
        assertTrue(badge.hasClass("gpg-unverified"))
    }

    @Test
    fun `태그 목록은 서명한 주석 태그에 배지를 보이고 서명이 없는 태그에는 보이지 않는다`() {
        val p = project()
        val alice = user("alice@example.com", "alice")
        val key = registered(alice, "Alice <alice@example.com>")
        val commit = SignedGitObjects.commit(repositories, p, null, "alice@example.com")
        SignedGitObjects.annotatedTag(repositories, p, key, "v-signed", "alice@example.com", commit)
        SignedGitObjects.annotatedTag(repositories, p, null, "v-plain", "alice@example.com", commit, time = 1_800_000_200_000L)
        SignedGitObjects.annotatedTag(repositories, p, key, "v-bad", "other@example.com", commit, time = 1_800_000_300_000L)
        val rows = page("/${p.owner}/${p.name}/tags").select("tbody tr").associateBy { it.selectFirst(".tag-name, a.tag-link, td.name a")?.text() ?: it.text() }
        fun rowOf(name: String) = rows.entries.first { it.key.contains(name) }.value
        assertEquals("valid", rowOf("v-signed").selectFirst(".gpg-badge")!!.attr("data-reason"))
        assertNull(rowOf("v-plain").selectFirst(".gpg-badge"))
        assertEquals("bad_email", rowOf("v-bad").selectFirst(".gpg-badge")!!.attr("data-reason"))
    }
}
