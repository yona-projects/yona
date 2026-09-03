package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class PullRequestRepositorySpec @Autowired constructor(
    private val pullRequestRepository: PullRequestRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("PullRequestRepository") {
            beforeEach {
                pullRequestRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("풀 리퀘스트를 정상적으로 생성하고 조회할 수 있어야 한다") {
                // Given
                val contributor = userRepository.save(
                    User(loginId = "contrib", name = "기여자", email = "contrib@yona.io")
                )
                val receiver = userRepository.save(
                    User(loginId = "receive", name = "수신자", email = "receive@yona.io")
                )

                val projectA = projectRepository.save(
                    Project(name = "repo-a", owner = "owner-a")
                )
                val projectB = projectRepository.save(
                    Project(name = "repo-b", owner = "owner-b")
                )

                val pr = PullRequest(
                    title = "기능 개선 PR",
                    body = "새로운 기능을 추가했습니다.",
                    toProject = projectA,
                    fromProject = projectB,
                    toBranch = "main",
                    fromBranch = "feature-x",
                    contributor = contributor,
                    receiver = receiver,
                    created = Instant.now(),
                    state = State.OPEN
                )

                // When
                val savedPr = pullRequestRepository.save(pr)

                // Then
                savedPr.id shouldNotBe null
                
                val foundPr = pullRequestRepository.findById(savedPr.id!!).orElse(null)
                foundPr shouldNotBe null
                foundPr.title shouldBe "기능 개선 PR"
                foundPr.toProject.name shouldBe "repo-a"
                foundPr.fromProject.name shouldBe "repo-b"
                foundPr.contributor.loginId shouldBe "contrib"
                foundPr.state shouldBe State.OPEN
            }

            // yona 자체 버그(P1-115): JPQL `A OR B AND C`는 `A OR (B AND C)`로 파싱되어 AND가 OR의
            // 두 번째 항에만 걸린다. `pr.state NOT IN (...)`가 toProject/toBranch 쪽에만 적용되고
            // fromProject/fromBranch 쪽은 상태와 무관하게 매칭돼, CLOSED/MERGED PR도 브랜치 삭제
            // 처리 대상(cleanupPullRequestsForDeletedBranches)에 잘못 포함될 수 있었다.
            it("findRelatedPullRequests는 fromProject/fromBranch로 매칭되는 CLOSED/MERGED PR을 제외해야 한다") {
                val contributor = userRepository.save(
                    User(loginId = "contrib2", name = "기여자2", email = "contrib2@yona.io")
                )
                val receiver = userRepository.save(
                    User(loginId = "receive2", name = "수신자2", email = "receive2@yona.io")
                )
                val fromProject = projectRepository.save(Project(name = "from-repo", owner = "owner-from"))
                val toProject = projectRepository.save(Project(name = "to-repo", owner = "owner-to"))

                val closedFromBranchPr = pullRequestRepository.save(
                    PullRequest(
                        title = "닫힌 PR(from 매칭)",
                        body = "closed",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "main",
                        fromBranch = "feature-closed",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.CLOSED
                    )
                )
                val openFromBranchPr = pullRequestRepository.save(
                    PullRequest(
                        title = "열린 PR(from 매칭)",
                        body = "open",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "main",
                        fromBranch = "feature-closed",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                val related = pullRequestRepository.findRelatedPullRequests(fromProject, "feature-closed")

                related.map { it.id } shouldBe listOf(openFromBranchPr.id)
                related.map { it.id } shouldNotContain closedFromBranchPr.id
            }

            // yona-wiki P3-02 Step8.6 항목3(2026-09-01, 우선순위 3위) — `yona search prs` 대응.
            // IssueRepository.searchIssues() 주석이 지적한 Postgres Hibernate 7.2.x 네이티브 쿼리
            // 버그(LIKE 2개 이상 시 실패)를 동일하게 회피하는 쿼리라 실제 DB로 검증한다.
            it("searchPullRequests는 title/body가 매칭되는 프로젝트 소속 PR 또는 내가 contributor인 PR을 찾아야 한다") {
                val contributor = userRepository.save(User(loginId = "search-contrib", name = "검색기여자", email = "search-contrib@yona.io"))
                val otherContributor = userRepository.save(User(loginId = "other-contrib", name = "다른기여자", email = "other-contrib@yona.io"))
                val allowedProject = projectRepository.save(Project(name = "allowed-repo", owner = "owner-allowed"))
                val otherProject = projectRepository.save(Project(name = "other-repo", owner = "owner-other"))

                val matchedByProject = pullRequestRepository.save(
                    PullRequest(title = "버그 수정", toProject = allowedProject, fromProject = allowedProject, contributor = otherContributor)
                )
                val matchedByContributor = pullRequestRepository.save(
                    PullRequest(title = "버그 개선", toProject = otherProject, fromProject = otherProject, contributor = contributor)
                )
                pullRequestRepository.save(
                    PullRequest(title = "무관한 PR", toProject = otherProject, fromProject = otherProject, contributor = otherContributor)
                )

                val projectIds = listOf(allowedProject.id!!)
                val keyword = "%버그%"
                val page = pullRequestRepository.searchPullRequests(projectIds, keyword, contributor.id, org.springframework.data.domain.PageRequest.of(0, 20))
                val count = pullRequestRepository.countSearchPullRequests(projectIds, keyword, contributor.id)

                count shouldBe 2
                page.content.map { it.id }.toSet() shouldBe setOf(matchedByProject.id, matchedByContributor.id)
            }

            it("searchPullRequestsInProject는 해당 프로젝트(toProject) 소속 PR만 title/body로 검색해야 한다") {
                val contributor = userRepository.save(User(loginId = "inproj-contrib", name = "프로젝트내검색", email = "inproj-contrib@yona.io"))
                val project = projectRepository.save(Project(name = "inproj-repo", owner = "owner-inproj"))
                val otherProject = projectRepository.save(Project(name = "other-inproj-repo", owner = "owner-other-inproj"))

                val matched = pullRequestRepository.save(
                    PullRequest(title = "문서 개선", toProject = project, fromProject = project, contributor = contributor)
                )
                pullRequestRepository.save(
                    PullRequest(title = "문서 개선 다른 프로젝트", toProject = otherProject, fromProject = otherProject, contributor = contributor)
                )

                val keyword = "%문서%"
                val page = pullRequestRepository.searchPullRequestsInProject(project, keyword, org.springframework.data.domain.PageRequest.of(0, 20))
                val count = pullRequestRepository.countSearchPullRequestsInProject(project, keyword)

                count shouldBe 1
                page.content.map { it.id } shouldBe listOf(matched.id)
            }

            // yona-wiki P3-02 16라운드(TASK-0440) — `gh status`의 "Assigned Pull Requests" 대응
            // 신규 쿼리. IssueRepository.countByAssigneeAndState와 동일한 패턴(assignee.user.id)임을
            // 실제 DB로 검증한다.
            it("findByAssigneeUserIdAndState/countByAssigneeUserIdAndState는 담당자 기준으로 프로젝트 무관하게 조회해야 한다") {
                val assignee = userRepository.save(User(loginId = "assignee-cross-proj", name = "담당자", email = "assignee-cross-proj@yona.io"))
                val other = userRepository.save(User(loginId = "other-assignee", name = "다른담당자", email = "other-assignee@yona.io"))
                val projectA = projectRepository.save(Project(name = "assignee-repo-a", owner = "owner-assignee-a"))
                val projectB = projectRepository.save(Project(name = "assignee-repo-b", owner = "owner-assignee-b"))

                val assignedOpenA = pullRequestRepository.save(
                    PullRequest(
                        title = "A 프로젝트 담당 PR", toProject = projectA, fromProject = projectA, contributor = other,
                        state = State.OPEN, assignee = com.github.yonaprojects.yona.domain.issue.Assignee(user = assignee, project = projectA)
                    )
                )
                pullRequestRepository.save(
                    PullRequest(
                        title = "B 프로젝트 담당 PR(닫힘)", toProject = projectB, fromProject = projectB, contributor = other,
                        state = State.CLOSED, assignee = com.github.yonaprojects.yona.domain.issue.Assignee(user = assignee, project = projectB)
                    )
                )
                pullRequestRepository.save(
                    PullRequest(
                        title = "무관한 PR", toProject = projectA, fromProject = projectA, contributor = other, state = State.OPEN
                    )
                )

                val openPage = pullRequestRepository.findByAssigneeUserIdAndState(assignee.id!!, State.OPEN, org.springframework.data.domain.PageRequest.of(0, 20))
                val openCount = pullRequestRepository.countByAssigneeUserIdAndState(assignee.id!!, State.OPEN)
                val closedCount = pullRequestRepository.countByAssigneeUserIdAndState(assignee.id!!, State.CLOSED)

                openPage.content.map { it.id } shouldBe listOf(assignedOpenA.id)
                openCount shouldBe 1L
                closedCount shouldBe 1L
            }

            // yona-wiki P3-02 16라운드(TASK-0440) — `gh status`의 "Review Requests" 대응 신규 쿼리.
            it("findByReviewerIdAndState/countByReviewerIdAndState는 리뷰어 기준으로 프로젝트 무관하게 조회해야 한다") {
                val reviewer = userRepository.save(User(loginId = "reviewer-cross-proj", name = "리뷰어", email = "reviewer-cross-proj@yona.io"))
                val contributor = userRepository.save(User(loginId = "reviewer-target-contrib", name = "기여자", email = "reviewer-target-contrib@yona.io"))
                val project = projectRepository.save(Project(name = "reviewer-repo", owner = "owner-reviewer"))

                val reviewedOpen = pullRequestRepository.save(
                    PullRequest(
                        title = "리뷰 요청된 PR", toProject = project, fromProject = project, contributor = contributor,
                        state = State.OPEN, reviewers = mutableSetOf(reviewer)
                    )
                )
                pullRequestRepository.save(
                    PullRequest(
                        title = "무관한 PR", toProject = project, fromProject = project, contributor = contributor, state = State.OPEN
                    )
                )

                val openPage = pullRequestRepository.findByReviewerIdAndState(reviewer.id!!, State.OPEN, org.springframework.data.domain.PageRequest.of(0, 20))
                val openCount = pullRequestRepository.countByReviewerIdAndState(reviewer.id!!, State.OPEN)

                openPage.content.map { it.id } shouldBe listOf(reviewedOpen.id)
                openCount shouldBe 1L
            }
        }
    }
}
