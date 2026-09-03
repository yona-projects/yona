package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

// yona Search.java:707-715 reviewsEL()의 equalsUserTemplate() 대응 (P1-83). ReviewComment는 [GL-models_Search-061;GL-models_Search-062]
// authorId 단일 필드가 아니라 author.id(임베디드 UserIdent)를 쓴다.
@Transactional
class ReviewCommentRepositorySpec @Autowired constructor(
    private val reviewCommentRepository: ReviewCommentRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("ReviewCommentRepository.searchReviewComments (P1-83, 프로젝트 접근권한과 무관한 본인 작성 코드리뷰댓글 노출)") {
            beforeEach {
                reviewCommentRepository.deleteAll()
                commentThreadRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("접근 불가능한 프로젝트의 코드리뷰댓글이라도 본인이 작성했으면 검색돼야 한다") {
                val author = userRepository.save(
                    User(loginId = "rc-search-author", name = "리뷰댓글검색작성자", email = "rc-search-author@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-rc-project", owner = "someone-else")
                )
                val thread = commentThreadRepository.save(
                    SimpleCommentThread(author = UserIdent(author), project = inaccessibleProject)
                )
                reviewCommentRepository.save(
                    ReviewComment(contents = "권한 없는 프로젝트의 내 코드리뷰댓글", author = UserIdent(author), thread = thread)
                )

                val result = reviewCommentRepository.searchReviewComments(emptyList(), "%권한%", author.id, PageRequest.of(0, 20))

                result.content.size shouldBe 1
                result.content.first().contents shouldBe "권한 없는 프로젝트의 내 코드리뷰댓글"
            }

            it("작성자가 아니고 접근 가능한 프로젝트도 아니면 검색되지 않아야 한다") {
                val author = userRepository.save(
                    User(loginId = "rc-search-author2", name = "리뷰댓글검색작성자2", email = "rc-search-author2@yona.io")
                )
                val stranger = userRepository.save(
                    User(loginId = "rc-search-stranger", name = "리뷰댓글제3자", email = "rc-search-stranger@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-rc-project2", owner = "someone-else2")
                )
                val thread = commentThreadRepository.save(
                    SimpleCommentThread(author = UserIdent(author), project = inaccessibleProject)
                )
                reviewCommentRepository.save(
                    ReviewComment(contents = "완전히 무관한 코드리뷰댓글", author = UserIdent(author), thread = thread)
                )

                val result = reviewCommentRepository.searchReviewComments(emptyList(), "%무관%", stranger.id, PageRequest.of(0, 20))

                result.content.size shouldBe 0
            }

            it("userId가 null(비로그인)이면 접근 가능한 프로젝트로만 필터링돼야 한다") {
                val author = userRepository.save(
                    User(loginId = "rc-search-author3", name = "리뷰댓글검색작성자3", email = "rc-search-author3@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-rc-project3", owner = "someone-else3")
                )
                val thread = commentThreadRepository.save(
                    SimpleCommentThread(author = UserIdent(author), project = inaccessibleProject)
                )
                reviewCommentRepository.save(
                    ReviewComment(contents = "비로그인 검색 테스트 코드리뷰댓글", author = UserIdent(author), thread = thread)
                )

                val result = reviewCommentRepository.searchReviewComments(emptyList(), "%비로그인%", null, PageRequest.of(0, 20))

                result.content.size shouldBe 0
            }
        }
    }
}
