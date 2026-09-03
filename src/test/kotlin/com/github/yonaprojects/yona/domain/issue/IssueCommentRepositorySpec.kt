package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// yona Search.java:594-602 issueCommentsEL()의 equalsUserTemplate() 대응 (P1-83). [GL-models_Search-046]
@Transactional
class IssueCommentRepositorySpec @Autowired constructor(
    private val issueCommentRepository: IssueCommentRepository,
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("IssueCommentRepository.searchIssueComments (P1-83, 프로젝트 접근권한과 무관한 본인 작성 이슈댓글 노출)") {
            beforeEach {
                issueCommentRepository.deleteAll()
                issueRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("접근 불가능한 프로젝트의 이슈댓글이라도 본인이 작성했으면 검색돼야 한다") {
                val author = userRepository.save(
                    User(loginId = "ic-search-author", name = "댓글검색작성자", email = "ic-search-author@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-ic-project", owner = "someone-else")
                )
                val issue = issueRepository.save(
                    Issue(title = "이슈", body = "본문", project = inaccessibleProject, state = State.OPEN)
                )
                issueCommentRepository.save(
                    IssueComment(
                        contents = "권한 없는 프로젝트의 내 이슈댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        issue = issue
                    )
                )

                val result = issueCommentRepository.searchIssueComments(emptyList(), "%권한%", author.id, PageRequest.of(0, 20))

                result.content.size shouldBe 1
                result.content.first().contents shouldBe "권한 없는 프로젝트의 내 이슈댓글"
            }

            it("작성자가 아니고 접근 가능한 프로젝트도 아니면 검색되지 않아야 한다") {
                val author = userRepository.save(
                    User(loginId = "ic-search-author2", name = "댓글검색작성자2", email = "ic-search-author2@yona.io")
                )
                val stranger = userRepository.save(
                    User(loginId = "ic-search-stranger", name = "댓글제3자", email = "ic-search-stranger@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-ic-project2", owner = "someone-else2")
                )
                val issue = issueRepository.save(
                    Issue(title = "이슈2", body = "본문", project = inaccessibleProject, state = State.OPEN)
                )
                issueCommentRepository.save(
                    IssueComment(
                        contents = "완전히 무관한 이슈댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        issue = issue
                    )
                )

                val result = issueCommentRepository.searchIssueComments(emptyList(), "%무관%", stranger.id, PageRequest.of(0, 20))

                result.content.size shouldBe 0
            }

            it("userId가 null(비로그인)이면 접근 가능한 프로젝트로만 필터링돼야 한다") {
                val author = userRepository.save(
                    User(loginId = "ic-search-author3", name = "댓글검색작성자3", email = "ic-search-author3@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-ic-project3", owner = "someone-else3")
                )
                val issue = issueRepository.save(
                    Issue(title = "이슈3", body = "본문", project = inaccessibleProject, state = State.OPEN)
                )
                issueCommentRepository.save(
                    IssueComment(
                        contents = "비로그인 검색 테스트 이슈댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        issue = issue
                    )
                )

                val result = issueCommentRepository.searchIssueComments(emptyList(), "%비로그인%", null, PageRequest.of(0, 20))

                result.content.size shouldBe 0
            }
        }
    }
}
