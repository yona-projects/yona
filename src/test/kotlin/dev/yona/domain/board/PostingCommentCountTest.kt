package dev.yona.domain.board

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/** 글에 댓글을 달 때 저장하는 댓글 수를 검증한다(R-116). v1.6은 두 번째 댓글부터 실제보다 하나 적게 저장했다. */
@Transactional
class PostingCommentCountTest : AbstractIntegrationTest() {

    @Autowired private lateinit var commentService: PostingCommentService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var postings: PostingRepository

    @Test
    fun `댓글을 달 때마다 글의 댓글 수가 실제 개수로 늘어난다 (R-116)`() {
        val author = users.save(User(loginId = "pccount", name = "pccount", email = "pccount@example.com", state = UserState.ACTIVE))
        val project = projects.save(Project(owner = author.loginId, name = "pccountp", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        val posting = postings.save(Posting(project = project, number = 1, title = "t", body = "b", authorId = author.id, authorLoginId = author.loginId, authorName = author.name, createdDate = Date(), updatedDate = Date()))
        for (expected in 1..4) {
            commentService.add(posting, author, "댓글 $expected", null)
            assertEquals(expected, postings.findById(posting.id!!).get().numOfComments, "$expected 번째 댓글 뒤")
        }
    }
}
