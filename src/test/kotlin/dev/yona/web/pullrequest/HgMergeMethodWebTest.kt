package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.EventType
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
import dev.yona.domain.pullrequest.PullRequestEventRepository
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mercurial 프로젝트의 풀 리퀘스트 **병합 방식 세 가지**(병합 커밋, 스쿼시, 리베이스, X-28)를 [PullRequestMergeMethodWebTest]의 Git 흐름과 같은 모양으로 화면에서
 * 확인한다(X-7 9단계 보완): 방식별 결과 모양, 이벤트에 남는 방식, 프로젝트 설정(기본 방식과 허용 여부), 가능 여부(리베이스 불가 이유), 보기 화면의 선택 목록이다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-merge-method-web"])
class HgMergeMethodWebTest : AbstractIntegrationTest() {

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
    @Autowired private lateinit var pullRequestEvents: PullRequestEventRepository
    @Autowired private lateinit var tokens: dev.yona.domain.apitoken.ApiTokenService

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "hm$n-$stamp", name = "이름hm$n", email = "hm$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun dir(p: Project) = hgRepositories.directoryOf(p.owner!!, p.name!!)

    private fun hg(p: Project, vararg args: String) = HgTestSupport.hg(dir(p), *args).trim()

    private class Setup(val origin: Project, val fork: Project, val contributor: User, val manager: User, val managerSession: MockHttpSession)

    private fun setup(): Setup {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        val origin = projects.save(Project(owner = "hmo$n-$stamp", name = "hmp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        HgTestSupport.commit(dir(origin), "기반", mapOf("a.txt" to "line1\nline2\nline3\n"))
        HgTestSupport.bookmark(dir(origin), "main")
        val manager = user()
        projectUsers.save(ProjectUser(user = manager, project = origin, role = roles.findById(RoleType.MANAGER.id).get()))
        val contributor = user()
        return Setup(origin, forks.fork(origin, contributor.loginId!!, "hmf$n", ProjectScope.PUBLIC, contributor), contributor, manager, login(manager))
    }

    /** [project]에서 이름표 [bookmark]를 활성으로 두고 커밋을 더한다(이름표가 따라 움직인다). */
    private fun commit(project: Project, message: String, files: Map<String, String?>, bookmark: String = "main", date: String = "2026-11-02 10:00 +0900", user: String? = null): String {
        HgTestSupport.hg(dir(project), "update", "-C", bookmark)
        return HgTestSupport.commit(dir(project), message, files, date, user)
    }

    private fun open(s: Setup, title: String = "요청"): PullRequest {
        mockMvc.perform(
            post("/${s.fork.owner}/${s.fork.name}/pullRequests").session(login(s.contributor)).param("fromProjectId", s.fork.id.toString()).param("toProjectId", s.origin.id.toString())
                .param("fromBranch", "refs/heads/main").param("toBranch", "refs/heads/main").param("title", title).param("body", "본문")
        ).andExpect(status().isSeeOther)
        return pullRequests.findAll().maxByOrNull { it.id!! }!!
    }

    private fun url(s: Setup, pr: PullRequest, tail: String) = "/${s.origin.owner}/${s.origin.name}/pullRequest/${pr.number}$tail"

    private fun reload(pr: PullRequest) = pullRequests.findById(pr.id!!).get()

    private fun accept(s: Setup, pr: PullRequest, vararg params: Pair<String, String>) = mockMvc.perform(
        post(url(s, pr, "/accept")).session(s.managerSession).also { r -> params.forEach { (k, v) -> r.param(k, v) } }
    )

    private fun parents(p: Project, rev: String) = hg(p, "log", "-r", rev, "--template", "{p1node} {p2node}").split(' ').filter { it.isNotBlank() && !it.all { c -> c == '0' } }

    private fun mainHead(p: Project) = hg(p, "log", "-r", "main", "--template", "{node}")

    private fun page(s: Setup, pr: PullRequest) = Jsoup.parse(mockMvc.perform(get(url(s, pr, "")).session(s.managerSession).header("Accept-Language", "ko-KR")).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `스쿼시로 받아들이면 부모가 하나인 커밋이 생기고 이벤트에 방식이 남는다`() {
        val s = setup(); commit(s.fork, "첫째", mapOf("b.txt" to "b\n")); commit(s.fork, "둘째", mapOf("c.txt" to "c\n"), date = "2026-11-03 10:00 +0900")
        val pr = open(s, "제목")
        val before = mainHead(s.origin)
        accept(s, pr, "method" to "squash").andExpect(status().isSeeOther)
        val merged = reload(pr)
        assertEquals(State.MERGED, merged.state)
        val head = mainHead(s.origin)
        assertEquals(listOf(before), parents(s.origin, head)); assertEquals(head, merged.mergedCommitIdTo)
        assertTrue(hg(s.origin, "log", "-r", head, "--template", "{desc}").startsWith("제목 (#${pr.number})"), hg(s.origin, "log", "-r", head, "--template", "{desc}"))
        val event = pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).single { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED && it.newValue == "merged" }
        assertEquals("squash", event.oldValue)
        assertTrue(page(s, pr).select("#comments").text().contains("스쿼시로 병합했습니다"), page(s, pr).select("#comments").text())
        assertTrue(HgTestSupport.verifies(dir(s.origin)))
    }

    @Test
    fun `리베이스로 받아들이면 병합 커밋 없이 원래 커밋이 받는 이름표 위에 놓인다`() {
        val s = setup(); commit(s.fork, "첫째", mapOf("b.txt" to "b\n"), user = "포크 <fork@example.com>"); commit(s.fork, "둘째", mapOf("c.txt" to "c\n"), date = "2026-11-03 10:00 +0900", user = "포크 <fork@example.com>")
        val originTip = commit(s.origin, "원본 앞서감", mapOf("o.txt" to "o\n"))
        val pr = open(s)
        accept(s, pr, "method" to "rebase").andExpect(status().isSeeOther)
        assertEquals(State.MERGED, reload(pr).state)
        val tip = mainHead(s.origin); val first = parents(s.origin, tip).single()
        assertEquals("둘째", hg(s.origin, "log", "-r", tip, "--template", "{desc|firstline}")); assertEquals("첫째", hg(s.origin, "log", "-r", first, "--template", "{desc|firstline}"))
        assertEquals(listOf(originTip), parents(s.origin, first), "받는 쪽의 머리 위에 놓인다")
        assertEquals("포크 <fork@example.com>", hg(s.origin, "log", "-r", tip, "--template", "{author}"), "원래 작성자를 지킨다")
        assertEquals("rebase", pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).single { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED && it.newValue == "merged" }.oldValue)
        assertTrue(page(s, pr).select("#comments").text().contains("리베이스로 병합했습니다"))
        assertTrue(HgTestSupport.verifies(dir(s.origin)))
    }

    @Test
    fun `병합 커밋 방식은 이벤트를 Git과 같게 남기고 방식을 보내지 않으면 프로젝트의 기본 방식을 쓴다`() {
        val s = setup(); commit(s.fork, "새 파일", mapOf("b.txt" to "b\n"))
        val pr = open(s)
        accept(s, pr).andExpect(status().isSeeOther)
        assertEquals(2, parents(s.origin, mainHead(s.origin)).size)
        assertNull(pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).single { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED && it.newValue == "merged" }.oldValue)

        val s2 = setup(); s2.origin.defaultMergeMethod = "squash"; projects.save(s2.origin); commit(s2.fork, "새 파일", mapOf("b.txt" to "b\n"))
        val pr2 = open(s2)
        accept(s2, pr2).andExpect(status().isSeeOther)
        assertEquals(1, parents(s2.origin, mainHead(s2.origin)).size, "기본 방식이 스쿼시이다")
    }

    @Test
    fun `허용하지 않았거나 모르는 방식이면 400이고 아무것도 바뀌지 않는다`() {
        val s = setup(); s.origin.allowSquashMerge = false; projects.save(s.origin); commit(s.fork, "새 파일", mapOf("b.txt" to "b\n"))
        val pr = open(s); val before = mainHead(s.origin)
        accept(s, pr, "method" to "squash").andExpect(status().isBadRequest)
        accept(s, pr, "method" to "fast-forward").andExpect(status().isBadRequest)
        assertEquals(State.OPEN, reload(pr).state); assertEquals(before, mainHead(s.origin)); assertEquals(false, reload(pr).isMerging)
    }

    @Test
    fun `스쿼시에 제목과 본문을 직접 줄 수 있다`() {
        val s = setup(); commit(s.fork, "새 파일", mapOf("b.txt" to "b\n"))
        val pr = open(s)
        accept(s, pr, "method" to "squash", "commitTitle" to "직접 제목", "commitMessage" to "직접 본문").andExpect(status().isSeeOther)
        val message = hg(s.origin, "log", "-r", mainHead(s.origin), "--template", "{desc}")
        assertTrue(message.startsWith("직접 제목\n\n직접 본문"), message)
    }

    @Test
    fun `리베이스할 수 없는 요청은 병합 검사가 이유를 저장하고 리베이스 요청은 아무것도 바꾸지 않는다`() {
        val s = setup()
        commit(s.origin, "원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n"))
        commit(s.fork, "충돌 없는", mapOf("b.txt" to "b\n")); commit(s.fork, "충돌하는", mapOf("a.txt" to "line1\nFORK\nline3\n"), date = "2026-11-03 10:00 +0900")
        val pr = open(s)
        assertEquals("conflict", reload(pr).rebaseBlocked)
        val before = mainHead(s.origin)
        accept(s, pr, "method" to "rebase").andExpect(status().isSeeOther)
        assertEquals(State.OPEN, reload(pr).state); assertEquals(before, mainHead(s.origin))
    }

    @Test
    fun `충돌이 없는 요청은 리베이스 불가 이유가 비어 있다`() {
        val s = setup(); commit(s.fork, "새 파일", mapOf("b.txt" to "b\n"))
        assertNull(reload(open(s)).rebaseBlocked)
    }

    @Test
    fun `보기 화면은 허용한 방식을 보이고 가능하지 않은 방식은 이유와 함께 막는다`() {
        val s = setup(); commit(s.fork, "새 파일", mapOf("b.txt" to "b\n"))
        val pr = open(s)
        reload(pr).also { it.rebaseBlocked = "merge-commits"; pullRequests.save(it) }
        val doc = page(s, pr)
        assertEquals(listOf("merge", "squash", "rebase"), doc.select("#merge-method-menu li").map { it.attr("data-method") })
        assertEquals("요청 (#${pr.number})", doc.select("#merge-method-menu li[data-method=squash]").single().attr("data-title"))
        assertTrue(doc.select("#merge-method-menu li[data-method=squash]").single().attr("data-message").contains("* 새 파일"))
        val rebase = doc.select("#merge-method-menu li[data-method=rebase]").single()
        assertTrue(rebase.hasClass("disabled")); assertTrue(rebase.text().contains("병합 커밋"), rebase.text())
        assertEquals("merge", doc.selectFirst("#merge-options input[name=method]")!!.attr("value"))
        assertEquals(url(s, pr, "/accept"), doc.selectFirst("form#merge-options")!!.attr("action"))
    }

    @Test
    fun `병합 방식을 병합 커밋만 허용한 프로젝트의 보기 화면은 선택 항목이 하나이다`() {
        val s = setup(); s.origin.allowSquashMerge = false; s.origin.allowRebaseMerge = false; projects.save(s.origin); commit(s.fork, "새 파일", mapOf("b.txt" to "b\n"))
        val doc = page(s, open(s))
        assertTrue(doc.select("#merge-method-menu").isEmpty(), "방식이 하나면 선택 목록과 화살표가 없다")
        assertEquals("merge", doc.selectFirst("#merge-options input[name=method]")!!.attr("value"))
    }

    @Test
    fun `프로젝트 설정 화면에 병합 방식 세 가지의 허용 여부와 기본 방식이 Mercurial에서도 나온다`() {
        val s = setup()
        val doc = Jsoup.parse(mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/settingform").session(s.managerSession)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(setOf("allowMergeCommit", "allowSquashMerge", "allowRebaseMerge"), doc.select("input[name^=allow][name\$=Merge], input[name=allowMergeCommit]").map { it.attr("name") }.toSet())
        assertEquals(listOf("merge", "squash", "rebase"), doc.select("input[name=defaultMergeMethod]").map { it.attr("value") })
    }

    @Test
    fun `REST 상세가 방식별 허용 여부와 가능 여부와 이유를 Mercurial에서도 담는다`() {
        val s = setup(); commit(s.fork, "새 파일", mapOf("b.txt" to "b\n"))
        val pr = open(s)
        s.origin.allowSquashMerge = false; projects.save(s.origin)
        reload(pr).also { it.rebaseBlocked = "merge-commits"; pullRequests.save(it) }
        val token = (tokens.create(s.manager, "t", setOf("repo"), null) as dev.yona.domain.apitoken.TokenCreation.Created).value
        val api = "/-_-api/v1/owners/${s.origin.owner}/projects/${s.origin.name}/pullrequests/${pr.number}"
        mockMvc.perform(get(api).header("Authorization", "token $token")).andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mergeMethods.merge.allowed").value(true))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mergeMethods.squash.allowed").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mergeMethods.rebase.available").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mergeMethods.rebase.reason").value("merge-commits"))
        // REST로도 세 방식이 동작한다
        mockMvc.perform(post("$api/merge").header("Authorization", "token $token").contentType("application/json").content("""{"method":"squash"}""")).andExpect(status().isBadRequest)
    }
}
