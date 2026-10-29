package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.project.Organization
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 조직의 풀 리퀘스트 목록, 프로젝트 홈의 대시보드, 사용자 화면의 풀 리퀘스트 탭에도 라벨 칩과 담당자 아바타가 나오는지 검증한다
 * (로드맵 X-14, `decisions.md` D-34 15번). 담당자 아바타는 받은 사람(`receiver`) 아바타와 따로 나온다.
 */
@AutoConfigureMockMvc
@Transactional
class PullRequestListsDisplayTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "ld${++sequence}", name = "이름ld$sequence", email = "ld$sequence@example.com", state = UserState.ACTIVE))

    private fun page(path: String): Document = Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().isOk).andReturn().response.contentAsString)

    private class Fixture(val project: Project, val org: Organization?, val author: User, val assignee: User, val receiver: User, val pr: PullRequest)

    /** 받는 프로젝트(조직 소유일 수도 있다)에 담당자, 라벨 `bug`, 받은 사람이 있는 풀 리퀘스트가 하나 있다. */
    private fun fixture(inOrganization: Boolean): Fixture {
        val org = if (inOrganization) Organization(name = "ldorg${++sequence}", descr = "설명", created = Date()).also { em.persist(it) } else null
        val owner = org?.name ?: user().loginId!!
        val project = Project(owner = owner, name = "ldp${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC, organization = org).also { em.persist(it) }
        em.persist(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        val author = user(); val assignee = user(); val receiver = user()
        val category = IssueLabelCategory(project = project, name = "분류").also { em.persist(it) }
        val bug = IssueLabel(category = category, color = "#d9534f", name = "bug", project = project).also { em.persist(it) }
        val created = Date(1_700_000_000_000L + sequence * 1000)
        val pr = PullRequest(
            title = "요청 제목", body = "본문", toProject = project, fromProject = project, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature", contributor = author,
            created = created, updated = created, state = State.OPEN, number = sequence.toLong(), receiver = receiver
        ).also { it.assignees.add(assignee); it.labels.add(bug); em.persist(it) }
        em.flush()
        return Fixture(project, org, author, assignee, receiver, pr)
    }

    @Test
    fun `조직 풀 리퀘스트 목록의 줄은 라벨 칩과 담당자 아바타를 받은 사람 아바타와 따로 보여 준다`() {
        val f = fixture(inOrganization = true)
        val row = page("/organizations/${f.org!!.name}/pullrequests").selectFirst("ul.post-list-wrap li.post-item")!!
        assertEquals("bug", row.selectFirst("a.issue-label")!!.text())
        assertEquals("/${f.assignee.loginId}", row.selectFirst("a.avatar-wrap.assignee")!!.attr("href"))
        assertEquals("/${f.receiver.loginId}", row.selectFirst("a.avatar-wrap.assinee")!!.attr("href"))
    }

    @Test
    fun `프로젝트 홈 대시보드의 풀 리퀘스트 줄은 작은 라벨 칩과 담당자 아바타를 보여 준다`() {
        val f = fixture(inOrganization = false)
        val row = page("/${f.project.owner}/${f.project.name}?tabId=dashboard").selectFirst(".overview-pullrequest .row-fluid")!!
        assertEquals("bug", row.selectFirst("a.issue-label")!!.text())
        val avatar = row.selectFirst(".avatar-wrap.assignee")!!
        assertTrue(avatar.attr("title").contains(f.assignee.name!!), avatar.outerHtml())
    }

    @Test
    fun `사용자 화면의 풀 리퀘스트 탭 줄은 라벨 칩과 담당자 아바타를 받은 사람 아바타와 따로 보여 준다`() {
        val f = fixture(inOrganization = false)
        // 사용자 화면은 그 사용자가 포크에서 보낸 풀 리퀘스트를 보인다
        f.pr.fromProject = Project(owner = f.author.loginId, name = "ldfork${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC, originalProject = f.project).also { em.persist(it) }
        em.flush()
        f.pr.updated = Date() // 사용자 화면은 최근에 갱신한 요청만 보인다
        em.flush()
        val row = page("/${f.author.loginId}").selectFirst("#pullRequests li.post-item")!!
        assertEquals("bug", row.selectFirst("a.issue-label")!!.text())
        assertEquals("/${f.assignee.loginId}", row.selectFirst("a.avatar-wrap.assignee")!!.attr("href"))
        assertEquals("/${f.receiver.loginId}", row.selectFirst("a.avatar-wrap.assinee")!!.attr("href"))
    }

    @Test
    fun `라벨과 담당자가 없는 줄은 칩이 없고 빈 아바타 칸만 있다`() {
        val f = fixture(inOrganization = true)
        f.pr.assignees.clear(); f.pr.labels.clear(); f.pr.receiver = null; em.flush(); em.clear()
        val row = page("/organizations/${f.org!!.name}/pullrequests").selectFirst("ul.post-list-wrap li.post-item")!!
        assertNull(row.selectFirst("a.issue-label")); assertNull(row.selectFirst("a.avatar-wrap.assignee")); assertNotNull(row.selectFirst(".empty-avatar-wrap"))
    }
}
