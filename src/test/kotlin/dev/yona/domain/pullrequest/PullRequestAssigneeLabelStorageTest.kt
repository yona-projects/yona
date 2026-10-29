package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.site.SiteUserService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 풀 리퀘스트의 담당자와 라벨 저장을 검증한다(로드맵 X-14, `decisions.md` D-34). 연결 테이블 `pull_request_assignee`,
 * `pull_request_issue_label`에 저장하고, 풀 리퀘스트, 프로젝트, 사용자, 라벨이 지워질 때 연결 행이 남지 않아야 한다.
 */
@Transactional
class PullRequestAssigneeLabelStorageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projectDeletion: ProjectDeletionService
    @Autowired private lateinit var siteUsers: SiteUserService
    @Autowired private lateinit var labelService: IssueLabelService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "pas${++sequence}", name = "pas$sequence", email = "pas$sequence@example.com", state = UserState.ACTIVE))

    private fun project() = em.persistAndReturn(Project(owner = "paso${++sequence}", name = "pasp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))

    private fun <T : Any> EntityManager.persistAndReturn(entity: T): T = entity.also { persist(it) }

    private fun label(project: Project, name: String = "bug"): IssueLabel {
        val category = em.persistAndReturn(IssueLabelCategory(project = project, name = "cat${++sequence}"))
        return em.persistAndReturn(IssueLabel(category = category, color = "red", name = name, project = project))
    }

    private fun pullRequest(to: Project, from: Project = to, contributor: User = user()) =
        em.persistAndReturn(PullRequest(toProject = to, fromProject = from, number = (++sequence).toLong(), title = "요청", contributor = contributor, state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/topic"))

    private fun count(table: String, where: String = "1 = 1"): Long {
        em.flush()
        return (em.createNativeQuery("select count(*) from $table where $where").singleResult as Number).toLong()
    }

    @Test
    fun `담당자와 라벨을 저장하고 다시 읽는다`() {
        val p = project(); val assignee = user(); val l = label(p)
        val pr = pullRequest(p).also { it.assignees.add(assignee); it.labels.add(l) }
        em.flush(); em.clear()
        val loaded = em.find(PullRequest::class.java, pr.id)
        assertEquals(setOf(assignee.id), loaded.assignees.map { it.id }.toSet())
        assertEquals(setOf(l.id), loaded.labels.map { it.id }.toSet())
        assertEquals(1, count("pull_request_assignee", "pull_request_id = ${pr.id}"))
        assertEquals(1, count("pull_request_issue_label", "pull_request_id = ${pr.id}"))
    }

    @Test
    fun `프로젝트를 지우면 그 프로젝트가 받는 풀 리퀘스트의 연결 행도 지워진다`() {
        val p = project(); val other = project(); val assignee = user()
        val mine = pullRequest(p).also { it.assignees.add(assignee); it.labels.add(label(p)) }
        val others = pullRequest(other).also { it.assignees.add(assignee); it.labels.add(label(other)) }
        em.flush()
        projectDeletion.delete(p)
        em.flush(); em.clear()
        assertEquals(0, count("pull_request_assignee", "pull_request_id = ${mine.id}"))
        assertEquals(0, count("pull_request_issue_label", "pull_request_id = ${mine.id}"))
        assertEquals(1, count("pull_request_assignee", "pull_request_id = ${others.id}"), "다른 프로젝트의 풀 리퀘스트는 그대로이다")
        assertEquals(1, count("pull_request_issue_label", "pull_request_id = ${others.id}"))
    }

    @Test
    fun `이슈 라벨을 지우면 풀 리퀘스트에서도 떨어진다`() {
        val p = project(); val keep = label(p, "keep"); val drop = label(p, "drop")
        val pr = pullRequest(p).also { it.labels.addAll(listOf(keep, drop)) }
        em.flush()
        labelService.deleteLabel(drop)
        em.flush(); em.clear()
        assertEquals(setOf(keep.id), em.find(PullRequest::class.java, pr.id).labels.map { it.id }.toSet())
        assertEquals(0, count("pull_request_issue_label", "issue_label_id = ${drop.id}"))
    }

    @Test
    fun `사용자를 삭제 처리하면 풀 리퀘스트 담당자에서 빠진다`() {
        val p = project(); val gone = user(); val stay = user()
        val pr = pullRequest(p).also { it.assignees.addAll(listOf(gone, stay)) }
        em.flush()
        siteUsers.deleteUser(gone)
        em.flush(); em.clear()
        assertEquals(setOf(stay.id), em.find(PullRequest::class.java, pr.id).assignees.map { it.id }.toSet())
        assertEquals(0, count("pull_request_assignee", "user_id = ${gone.id}"))
    }
}
