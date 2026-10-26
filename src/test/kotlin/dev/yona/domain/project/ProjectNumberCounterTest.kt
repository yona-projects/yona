package dev.yona.domain.project

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.board.PostingService
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 이슈와 게시글 번호 카운터의 자가 보정을 검증한다.
 *
 * 원본: `app/models/Project.java`의 `isLastIssueNumberCorrect`, `getLastIssueNumber`, `increaseLastIssueNumber`와 게시글의 같은 메서드.
 * 카운터가 이슈 수보다 작으면 그 프로젝트의 가장 큰 번호를 기준으로 다음 번호를 정한다.
 * 번호를 지정해 가져오는 REST API(`ExternalIssueApiController`)는 카운터를 올리지 않으므로 이 보정이 없으면 다음 새 이슈가 번호 충돌을 낸다.
 */
@Transactional
class ProjectNumberCounterTest : AbstractIntegrationTest() {

    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var issueService: IssueService
    @Autowired private lateinit var postingService: PostingService

    private var sequence = 0

    private fun project(last: Long): Project {
        val n = ++sequence
        return projects.save(Project(owner = "pnc$n", name = "pnc$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = last, lastPostingNumber = last))
    }

    private fun issue(p: Project, number: Long) =
        issues.save(Issue(project = p, number = number, title = "t$number", body = "b", state = State.OPEN, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))

    private fun posting(p: Project, number: Long) =
        postings.save(Posting(project = p, number = number, title = "t$number", body = "b", notice = false, readme = false, numOfComments = 0, createdDate = Date(), updatedDate = Date()))

    @Test
    fun `카운터가 이슈 수보다 작으면 가장 큰 번호 다음 번호를 준다`() {
        val p = project(0)
        (1L..5L).forEach { issue(p, it) }
        assertEquals(6L, issueService.nextIssueNumber(p))
    }

    @Test
    fun `카운터가 맞으면 하나만 올린다`() {
        val p = project(5)
        (1L..5L).forEach { issue(p, it) }
        assertEquals(6L, issueService.nextIssueNumber(p))
        assertEquals(7L, issueService.nextIssueNumber(p))
    }

    @Test
    fun `지워진 번호가 있어 카운터가 이슈 수보다 커도 카운터를 따른다`() {
        val p = project(9)
        issue(p, 1); issue(p, 9)
        assertEquals(10L, issueService.nextIssueNumber(p))
    }

    @Test
    fun `게시글 번호도 같은 방식으로 보정한다`() {
        val p = project(0)
        (1L..3L).forEach { posting(p, it) }
        assertEquals(4L, postingService.nextNumber(p))
        assertEquals(5L, postingService.nextNumber(p))
    }
}
