package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.ForkService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Mercurial 프로젝트의 풀 리퀘스트 화면 흐름(X-7 8d)을 실제 `hg` 저장소로 검증한다: 만들기, 보기, 변경 보기, 받아들이기. [PullRequestActionTest]의 Git 흐름과
 * 같은 모양이다. 브랜치는 이름표이다(D-86).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-pull-request-web"])
class HgPullRequestWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "hw$n-$stamp", name = "이름hw$n", email = "hw$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun dir(p: Project) = hgRepositories.directoryOf(p.owner!!, p.name!!)

    private class Setup(val origin: Project, val fork: Project, val contributor: User, val manager: User, val managerSession: MockHttpSession)

    private fun setup(): Setup {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        val origin = projects.save(Project(owner = "hwo$n-$stamp", name = "hwp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        HgTestSupport.commit(dir(origin), "기반", mapOf("a.txt" to "line1\nline2\nline3\n"))
        HgTestSupport.bookmark(dir(origin), "main")
        val manager = user()
        projectUsers.save(ProjectUser(user = manager, project = origin, role = roles.findById(RoleType.MANAGER.id).get()))
        val contributor = user()
        return Setup(origin, forks.fork(origin, contributor.loginId!!, "hwf$n", ProjectScope.PUBLIC, contributor), contributor, manager, login(manager))
    }

    /** 포크의 이름표 [bookmark]에 커밋을 더한다. */
    private fun forkCommit(s: Setup, bookmark: String, message: String, files: Map<String, String?>): String {
        fun hg(vararg args: String) = HgTestSupport.hg(dir(s.fork), *args)
        if (bookmark !in hg("bookmarks", "--template", "{bookmark}\n").lines()) hg("bookmark", "-r", "main", bookmark)
        hg("update", "-C", bookmark)
        return HgTestSupport.commit(dir(s.fork), message, files, "2026-11-02 10:00 +0900")
    }

    private fun open(s: Setup, fromBranch: String, title: String = "요청"): PullRequest {
        mockMvc.perform(
            post("/${s.fork.owner}/${s.fork.name}/pullRequests").session(login(s.contributor)).param("fromProjectId", s.fork.id.toString()).param("toProjectId", s.origin.id.toString())
                .param("fromBranch", fromBranch).param("toBranch", "refs/heads/main").param("title", title).param("body", "본문")
        ).andExpect(status().isSeeOther)
        return pullRequests.findAll().maxByOrNull { it.id!! }!!
    }

    private fun url(s: Setup, pr: PullRequest, tail: String) = "/${s.origin.owner}/${s.origin.name}/pullRequest/${pr.number}$tail"

    private fun node(p: Project, rev: String) = HgTestSupport.hg(dir(p), "log", "-r", rev, "--template", "{node}")

    @Test
    fun `새 풀 리퀘스트 화면이 열리고 이름표를 브랜치로 고를 수 있다`() {
        val s = setup(); forkCommit(s, "feature", "기능", mapOf("f.txt" to "f\n"))
        mockMvc.perform(get("/${s.fork.owner}/${s.fork.name}/newPullRequestForm").session(login(s.contributor))).andExpect(status().isOk)
    }

    @Test
    fun `풀 리퀘스트를 만들면 가져올 커밋이 기록되고 충돌이 없고 받는 저장소의 브랜치는 그대로이다`() {
        val s = setup()
        val head = forkCommit(s, "feature", "기능", mapOf("f.txt" to "f\n"))
        val mainBefore = node(s.origin, "main")
        val pr = open(s, "refs/heads/feature")
        assertEquals(State.OPEN, pr.state); assertFalse(pr.isConflict == true)
        assertEquals(mainBefore, node(s.origin, "main"))
        assertEquals(listOf("refs/heads/main"), repositories.refNames(s.origin))
        mockMvc.perform(get(url(s, pr, "")).session(s.managerSession)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("기능")))
        assertNotNull(head)
    }

    @Test
    fun `변경 화면이 병합 기준 사이의 변경을 보여 준다`() {
        val s = setup(); forkCommit(s, "feature", "기능", mapOf("f.txt" to "f\n"))
        val pr = open(s, "refs/heads/feature")
        mockMvc.perform(get(url(s, pr, "/changes")).session(s.managerSession)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("f.txt")))
    }

    @Test
    fun `받아들이면 병합 커밋으로 받는 이름표가 옮겨지고 병합됨으로 바뀐다`() {
        val s = setup()
        forkCommit(s, "feature", "기능", mapOf("f.txt" to "f\n"))
        val pr = open(s, "refs/heads/feature")
        val before = node(s.origin, "main")
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url(s, pr, "/accept")).session(s.managerSession)).andExpect(status().isSeeOther).andExpect(redirectedUrl(url(s, pr, "")))
        val merged = pullRequests.findById(pr.id!!).get()
        assertEquals(State.MERGED, merged.state); assertEquals(s.manager.id, merged.receiver?.id)
        val after = node(s.origin, "main")
        assertTrue(after != before)
        assertEquals(after, merged.mergedCommitIdTo); assertEquals(before, merged.mergedCommitIdFrom)
        assertEquals("public", HgTestSupport.hg(dir(s.origin), "log", "-r", after, "--template", "{phase}").trim())
        assertTrue(HgTestSupport.verifies(dir(s.origin)))
    }

    @Test
    fun `병합된 뒤에 보내는 브랜치를 지우고 되살릴 수 있다`() {
        val s = setup()
        val head = forkCommit(s, "feature", "기능", mapOf("f.txt" to "f\n"))
        val pr = open(s, "refs/heads/feature")
        mockMvc.perform(post(url(s, pr, "/accept")).session(s.managerSession)).andExpect(status().isSeeOther)
        val contributor = s.managerSession // 수정 권한은 받는 프로젝트의 관리자 기준이다(Git 시험과 같다)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url(s, pr, "/deletefrombranch")).session(contributor)).andExpect(status().isSeeOther)
        assertFalse("refs/heads/feature" in repositories.refNames(s.fork))
        assertEquals(head, pullRequests.findById(pr.id!!).get().lastCommitId)
        mockMvc.perform(post(url(s, pr, "/restorefrombranch")).session(contributor)).andExpect(status().isSeeOther)
        assertTrue("refs/heads/feature" in repositories.refNames(s.fork))
        assertEquals(head, node(s.fork, "feature"))
    }
}
