package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// yona Search.java:650-658 postCommentsEL()의 equalsUserTemplate() 대응 (P1-83).
@Transactional
class PostingCommentRepositorySpec @Autowired constructor(
    private val postingCommentRepository: PostingCommentRepository,
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("PostingCommentRepository.searchPostingComments (P1-83, 프로젝트 접근권한과 무관한 본인 작성 게시글댓글 노출)") {
            beforeEach {
                postingCommentRepository.deleteAll()
                postingRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("접근 불가능한 프로젝트의 게시글댓글이라도 본인이 작성했으면 검색돼야 한다") {
                val author = userRepository.save(
                    User(loginId = "pc-search-author", name = "게시글댓글검색작성자", email = "pc-search-author@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-pc-project", owner = "someone-else")
                )
                val posting = postingRepository.save(
                    Posting(
                        title = "게시글", body = "본문", project = inaccessibleProject,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        createdDate = Instant.now()
                    )
                )
                postingCommentRepository.save(
                    PostingComment(
                        contents = "권한 없는 프로젝트의 내 게시글댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        posting = posting
                    )
                )

                val result = postingCommentRepository.searchPostingComments(emptyList(), "%권한%", author.id, PageRequest.of(0, 20))

                result.content.size shouldBe 1
                result.content.first().contents shouldBe "권한 없는 프로젝트의 내 게시글댓글"
            }

            it("작성자가 아니고 접근 가능한 프로젝트도 아니면 검색되지 않아야 한다") {
                val author = userRepository.save(
                    User(loginId = "pc-search-author2", name = "게시글댓글검색작성자2", email = "pc-search-author2@yona.io")
                )
                val stranger = userRepository.save(
                    User(loginId = "pc-search-stranger", name = "게시글댓글제3자", email = "pc-search-stranger@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-pc-project2", owner = "someone-else2")
                )
                val posting = postingRepository.save(
                    Posting(
                        title = "게시글2", body = "본문", project = inaccessibleProject,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        createdDate = Instant.now()
                    )
                )
                postingCommentRepository.save(
                    PostingComment(
                        contents = "완전히 무관한 게시글댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        posting = posting
                    )
                )

                val result = postingCommentRepository.searchPostingComments(emptyList(), "%무관%", stranger.id, PageRequest.of(0, 20))

                result.content.size shouldBe 0
            }

            it("userId가 null(비로그인)이면 접근 가능한 프로젝트로만 필터링돼야 한다") {
                val author = userRepository.save(
                    User(loginId = "pc-search-author3", name = "게시글댓글검색작성자3", email = "pc-search-author3@yona.io")
                )
                val inaccessibleProject = projectRepository.save(
                    Project(name = "inaccessible-pc-project3", owner = "someone-else3")
                )
                val posting = postingRepository.save(
                    Posting(
                        title = "게시글3", body = "본문", project = inaccessibleProject,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        createdDate = Instant.now()
                    )
                )
                postingCommentRepository.save(
                    PostingComment(
                        contents = "비로그인 검색 테스트 게시글댓글", createdDate = Instant.now(),
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                        posting = posting
                    )
                )

                val result = postingCommentRepository.searchPostingComments(emptyList(), "%비로그인%", null, PageRequest.of(0, 20))

                result.content.size shouldBe 0
            }
        }
    }
}
