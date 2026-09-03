package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class PostingRepositorySpec @Autowired constructor(
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("PostingRepository") {
            beforeEach {
                postingRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("게시판 포스팅을 정상적으로 저장하고 조회할 수 있어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "posting-author", name = "작성자", email = "author@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "board-project", owner = "board-owner")
                )

                val posting = Posting(
                    title = "프로젝트 첫 공지사항",
                    body = "팀원 여러분 반갑습니다.",
                    project = project,
                    authorId = author.id,
                    authorLoginId = author.loginId,
                    authorName = author.name,
                    createdDate = Instant.now(),
                    notice = true
                )

                // When
                val savedPosting = postingRepository.save(posting)

                // Then
                savedPosting.id shouldNotBe null
                
                val foundPosting = postingRepository.findById(savedPosting.id!!).orElse(null)
                foundPosting shouldNotBe null
                foundPosting.title shouldBe "프로젝트 첫 공지사항"
                foundPosting.notice shouldBe true
                foundPosting.project.name shouldBe "board-project"
            }

            // yona Search.java:246-254 postsEL()의 equalsUserTemplate() 대응 (P1-83).
            describe("searchPostings (P1-83, 프로젝트 접근권한과 무관한 본인 작성 게시글 노출)") {
                it("접근 불가능한 프로젝트의 게시글이라도 본인이 작성했으면 검색돼야 한다") {
                    val author = userRepository.save(
                        User(loginId = "posting-search-author", name = "게시글검색작성자", email = "posting-search-author@yona.io")
                    )
                    val inaccessibleProject = projectRepository.save(
                        Project(name = "inaccessible-posting-project", owner = "someone-else")
                    )
                    postingRepository.save(
                        Posting(
                            title = "권한 없는 프로젝트의 내 게시글", body = "본문", project = inaccessibleProject,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now()
                        )
                    )

                    val result = postingRepository.searchPostings(emptyList(), "%권한%", author.id, PageRequest.of(0, 20))

                    result.content.size shouldBe 1
                    result.content.first().title shouldBe "권한 없는 프로젝트의 내 게시글"
                }

                it("작성자가 아니고 접근 가능한 프로젝트도 아니면 검색되지 않아야 한다") {
                    val author = userRepository.save(
                        User(loginId = "posting-search-author2", name = "게시글검색작성자2", email = "posting-search-author2@yona.io")
                    )
                    val stranger = userRepository.save(
                        User(loginId = "posting-search-stranger", name = "게시글제3자", email = "posting-search-stranger@yona.io")
                    )
                    val inaccessibleProject = projectRepository.save(
                        Project(name = "inaccessible-posting-project2", owner = "someone-else2")
                    )
                    postingRepository.save(
                        Posting(
                            title = "완전히 무관한 게시글", body = "본문", project = inaccessibleProject,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now()
                        )
                    )

                    val result = postingRepository.searchPostings(emptyList(), "%무관%", stranger.id, PageRequest.of(0, 20))

                    result.content.size shouldBe 0
                }

                it("userId가 null(비로그인)이면 접근 가능한 프로젝트로만 필터링돼야 한다") {
                    val author = userRepository.save(
                        User(loginId = "posting-search-author3", name = "게시글검색작성자3", email = "posting-search-author3@yona.io")
                    )
                    val inaccessibleProject = projectRepository.save(
                        Project(name = "inaccessible-posting-project3", owner = "someone-else3")
                    )
                    postingRepository.save(
                        Posting(
                            title = "비로그인 검색 테스트 게시글", body = "본문", project = inaccessibleProject,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now()
                        )
                    )

                    val result = postingRepository.searchPostings(emptyList(), "%비로그인%", null, PageRequest.of(0, 20))

                    result.content.size shouldBe 0
                }
            }
        }
    }
}
