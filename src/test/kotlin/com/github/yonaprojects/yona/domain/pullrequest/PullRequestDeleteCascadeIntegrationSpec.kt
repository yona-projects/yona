package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

// 브랜치 삭제 push 후크(GitPushHooks/HgPushHooks)는 관련 열린 PR을 pullRequestRepository.deleteAll(related)로 지운다.
// PR을 FK로 참조하는 이벤트/커밋/리뷰가 정리되지 않으면 후크가 제약 위반으로 실패한다(v1.6 PullRequest.delete()는
// 이벤트를 먼저 정리했다).
class PullRequestDeleteCascadeIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val pullRequestReviewRepository: PullRequestReviewRepository
) : AbstractIntegrationTest() {
    init {
        describe("PullRequestRepository.deleteAll — 자식 레코드 정리") {
            afterEach {
                pullRequestReviewRepository.deleteAll()
                pullRequestCommitRepository.deleteAll()
                pullRequestEventRepository.deleteAll()
                pullRequestRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("이벤트·커밋·리뷰가 달린 PR을 삭제하면 자식 레코드도 함께 삭제되어야 한다") {
                val user = userRepository.save(User(loginId = "prdel", name = "PR삭제", email = "prdel@example.com"))
                val project = projectRepository.save(Project(name = "prdel-proj", owner = "prdel"))
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "t", toProject = project, fromProject = project, toBranch = "main",
                        fromBranch = "feature", contributor = user, created = Instant.now()
                    )
                )
                pullRequestEventRepository.save(PullRequestEvent(pullRequest = pr, senderLoginId = "prdel"))
                pullRequestCommitRepository.save(PullRequestCommit(pullRequest = pr, commitId = "abc123", commitShortId = "abc"))
                pullRequestReviewRepository.save(
                    PullRequestReview(pullRequest = pr, reviewer = user, state = PullRequestReview.ReviewState.COMMENT)
                )

                // 후크는 findRelatedPullRequests()로 DB에서 읽은 엔티티를 지운다 — 방금 만든 인스턴스는 자식 컬렉션이 비어 있으므로 다시 읽는다.
                val loaded = pullRequestRepository.findById(pr.id!!).orElseThrow()
                pullRequestRepository.deleteAll(listOf(loaded))
                pullRequestRepository.flush()

                pullRequestRepository.count() shouldBe 0L
                pullRequestEventRepository.count() shouldBe 0L
                pullRequestCommitRepository.count() shouldBe 0L
                pullRequestReviewRepository.count() shouldBe 0L
            }
        }
    }
}
