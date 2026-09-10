package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// legacy common/partial_history.scala.html + issue/view.scala.html:169 / board/view.scala.html:78
// 대응 (#41, TASK-0257). history 필드 자체와 HistoryUtil.appendHistory()는 이미 P2-02에서 완비돼
// 있었고(재확인: AbstractPosting.kt에 이미 history: String? 필드 존재, IssueServiceImpl.updateIssue()/
// PostingServiceImpl.updatePosting()이 이미 본문 변경 시 HistoryUtil.appendHistory()로 갱신), 이번에
// 새로 만든 건 오직 뷰 레이어(common/partial_history.html 프래그먼트 + issue/view.html·board/view.html
// 배선)뿐이다 - 이 스펙은 실제 서비스 updateIssue()/updatePosting() 호출로 history를 실제로 채운 뒤,
// 그 값이 실제 Thymeleaf 렌더링(HTTP 응답 HTML)에 나타나는지 확인한다.
@Transactional
class PostingHistoryTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val issueService: IssueService,
    private val postingRepository: PostingRepository,
    private val postingService: PostingService
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("이슈/게시글 상세 화면의 변경 이력(history) 모달 렌더링") {
            // 이 스펙은 이름이 고유한(hist- 접두) 프로젝트/유저만 만들고, 클래스에 붙은 @Transactional이
            // 각 테스트 종료 시 롤백을 보장하므로 다른 스펙의 데이터를 건드리는 전역 deleteAll()은
            // 쓰지 않는다(공유 테스트 DB에서 무관한 프로젝트를 지우다 FK 위반이 날 수 있음).

            fun authOf(u: User) = user(
                YonaUserDetails(
                    id = u.id ?: 0L,
                    loginId = u.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
            )

            it("이슈를 수정해 history가 쌓이면, 로그인 사용자에게는 변경 이력 모달과 최종 수정자 정보가 렌더링되어야 한다") {
                val author = userRepository.save(User(loginId = "hist-issue-author", name = "이력이슈작성자", email = "hist-issue-author@yona.io"))
                val project = projectRepository.save(Project(name = "hist-issue-proj", owner = "hist-issue-owner", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(title = "이력 테스트 이슈", body = "최초 본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )

                issueService.updateIssue(
                    issueId = issue.id!!,
                    title = issue.title,
                    body = "수정된 본문",
                    updater = author
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issue/1").with(authOf(author))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "posting-history"
                body shouldContain "-yona-posting-history"
                // HistoryUtil.appendHistory()가 만드는 diff 헤더/본문 클래스 (legacy AbstractPostingApp.
                // getHistoryMadeBy()/getDiffText() 대응) - 실제로 렌더링된 HTML에 그대로 나타나야 한다.
                body shouldContain "history-made-by"
                body shouldContain author.name
                body shouldContain "Change history"
            }

            it("history가 없는 이슈는 변경 이력 링크를 렌더링하지 않아야 한다") {
                val author = userRepository.save(User(loginId = "hist-issue-none", name = "이력없는작성자", email = "hist-issue-none@yona.io"))
                val project = projectRepository.save(Project(name = "hist-issue-none-proj", owner = "hist-issue-none-owner", projectScope = ProjectScope.PUBLIC))
                issueRepository.save(
                    Issue(title = "이력 없는 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issue/1").with(authOf(author))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldNotContain "posting-history"
            }

            it("비로그인 사용자에게는 모달 대신 로그인 유도 링크만 렌더링되어야 한다") {
                val author = userRepository.save(User(loginId = "hist-issue-anon", name = "익명대상작성자", email = "hist-issue-anon@yona.io"))
                val project = projectRepository.save(Project(name = "hist-issue-anon-proj", owner = "hist-issue-anon-owner", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(title = "익명 이력 테스트 이슈", body = "최초 본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )
                issueService.updateIssue(issueId = issue.id!!, title = issue.title, body = "수정된 본문 2", updater = author)

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issue/1")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "posting-history"
                body shouldContain "/users/loginform"
                body shouldNotContain "-yona-posting-history"
            }

            it("게시글을 수정해 history가 쌓이면, 로그인 사용자에게는 변경 이력 모달이 렌더링되어야 한다") {
                val author = userRepository.save(User(loginId = "hist-post-author", name = "이력게시글작성자", email = "hist-post-author@yona.io"))
                val project = projectRepository.save(Project(name = "hist-post-proj", owner = "hist-post-owner", projectScope = ProjectScope.PUBLIC))
                val posting = postingRepository.save(
                    Posting(title = "이력 테스트 게시글", body = "최초 본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )

                postingService.updatePosting(
                    projectId = project.id!!,
                    number = posting.number!!,
                    title = posting.title,
                    body = "수정된 게시글 본문",
                    notice = false,
                    readme = false,
                    authorId = author.id!!
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/post/1").with(authOf(author))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "posting-history"
                body shouldContain "-yona-posting-history"
                body shouldContain "history-made-by"
            }
        }
    }
}
