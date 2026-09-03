package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class IssueServiceImplExtraSpec @Autowired constructor(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueCommentRepository: IssueCommentRepository
) : AbstractIntegrationTest() {

    init {
        describe("IssueServiceImpl 추가 커버리지 테스트") {
            beforeEach {
                issueCommentRepository.deleteAll()
                issueRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("voteIssue - 중복 투표 시 IllegalStateException 예외") {
                val author = userRepository.save(User(loginId = "voter1", name = "voter1"))
                val project = projectRepository.save(Project(name = "p1", owner = "voter1"))
                val issue = issueRepository.save(Issue(title = "t1", project = project, authorId = author.id))

                issueService.voteIssue(issue.id!!, author) // first vote
                shouldThrow<IllegalStateException> {
                    issueService.voteIssue(issue.id!!, author) // duplicate
                }
            }
            
            it("unvoteIssue - 투표하지 않은 상태에서 취소 시 예외") {
                val author = userRepository.save(User(loginId = "voter2", name = "voter2"))
                val project = projectRepository.save(Project(name = "p2", owner = "voter2"))
                val issue = issueRepository.save(Issue(title = "t2", project = project, authorId = author.id))

                shouldThrow<IllegalStateException> {
                    issueService.unvoteIssue(issue.id!!, author)
                }
            }

            it("voteComment - 중복 투표 시 예외") {
                val author = userRepository.save(User(loginId = "voter3", name = "voter3"))
                val project = projectRepository.save(Project(name = "p3", owner = "voter3"))
                val issue = issueRepository.save(Issue(title = "t3", project = project, authorId = author.id))
                val comment = issueCommentRepository.save(IssueComment(contents = "c1", issue = issue, authorId = author.id, projectId = project.id))

                issueService.voteComment(comment.id!!, author)
                shouldThrow<IllegalStateException> {
                    issueService.voteComment(comment.id!!, author)
                }
            }
            
            it("unvoteComment - 투표하지 않은 댓글 취소 시 예외") {
                val author = userRepository.save(User(loginId = "voter4", name = "voter4"))
                val project = projectRepository.save(Project(name = "p4", owner = "voter4"))
                val issue = issueRepository.save(Issue(title = "t4", project = project, authorId = author.id))
                val comment = issueCommentRepository.save(IssueComment(contents = "c2", issue = issue, authorId = author.id, projectId = project.id))

                shouldThrow<IllegalStateException> {
                    issueService.unvoteComment(comment.id!!, author)
                }
            }

            it("upvoteWeight / downvoteWeight") {
                val author = userRepository.save(User(loginId = "voter5", name = "voter5"))
                val project = projectRepository.save(Project(name = "p5", owner = "voter5"))
                val issue = issueRepository.save(Issue(title = "t5", project = project, authorId = author.id, weight = 10))

                val up = issueService.upvoteWeight(issue.id!!)
                up.weight shouldBe 11
                
                val down = issueService.downvoteWeight(issue.id!!)
                down.weight shouldBe 10
            }
            
            it("존재하지 않는 이슈에 투표 시 예외") {
                val author = userRepository.save(User(loginId = "voter6", name = "voter6"))
                shouldThrow<IllegalArgumentException> {
                    issueService.voteIssue(9999L, author)
                }
            }
            
            it("존재하지 않는 유저가 투표 시 예외") {
                val author = userRepository.save(User(loginId = "voter7", name = "voter7"))
                val project = projectRepository.save(Project(name = "p7", owner = "voter7"))
                val issue = issueRepository.save(Issue(title = "t7", project = project, authorId = author.id))
                
                val fakeUser = User(id = 9999L, loginId = "fake")
                shouldThrow<IllegalArgumentException> {
                    issueService.voteIssue(issue.id!!, fakeUser)
                }
            }
        }
    }
}
