package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.string.shouldContain
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

// P3-52 항목3 — issue/view.html과 동일한 이유로 board/view.html의 #comment-form에도 동일한 가드
// (빈값 제출 방지/Ctrl+Shift+Enter/beforeunload/임시저장)를 재구현했다.
// (CommentFormGuardsWiringTemplateRenderingSpec의 board 버전 — 같은 결함/같은 해법의 자매 화면)
@Transactional
class BoardCommentFormGuardsWiringTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val postingRepository: PostingRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("게시글 상세 화면 #comment-form의 빈값 제출 방지/단축 제출/이탈 경고/임시저장 가드") {
            it("가드 스크립트가 모두 렌더링돼야 한다") {
                val author = userRepository.save(User(loginId = "boardcfguard-author", name = "게시판가드작성자", email = "boardcfguard-author@yona.io"))
                val project = projectRepository.save(Project(name = "boardcfguard-proj", owner = "boardcfguard-owner", projectScope = ProjectScope.PUBLIC))
                val post = postingRepository.save(
                    Posting(title = "가드 테스트 게시글", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )
                val authorDetails = YonaUserDetails(
                    id = author.id!!,
                    loginId = author.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/post/${post.number}").with(user(authorDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "Comment should not be empty."
                body shouldContain "isSubmitCombo"
                body shouldContain "shiftKey"
                body shouldContain "Would you like to exit this page without submitting comment?"
                body shouldContain "temporarySaveHandler("
                body shouldContain "removeCurrentPageTemprarySavedContent("
            }
        }
    }
}
