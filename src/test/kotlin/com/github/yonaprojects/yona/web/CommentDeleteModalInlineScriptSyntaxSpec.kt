package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// common/commentDeleteModal.html의 삭제 실패 alert()도 pullrequest/view.html·clone.html과 같은
// 클래스의 "[[...]]" 수동 따옴표 회귀였다(th:inline="javascript"가 String을 이미 자동 따옴표
// 처리) - 이 경우는 문법 자체는 안 깨지고 alert 문구에 불필요한 따옴표 문자가 겹쳐 보이는
// 경미한 결함이었다.
class CommentDeleteModalInlineScriptSyntaxSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("댓글 삭제 모달의 실패 alert 문구에 따옴표가 겹치면 안 된다") {
            it("이슈 화면에 포함된 삭제 모달 스크립트의 alert 문구가 깨끗해야 한다") {
                val owner = userRepository.findByLoginId("cmtdel-owner").orElseGet {
                    userRepository.save(User(loginId = "cmtdel-owner", name = "댓글삭제소유자", email = "cmtdel-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "cmtdel-proj" && it.owner == "cmtdel-owner" }
                    ?: projectRepository.save(Project(name = "cmtdel-proj", owner = "cmtdel-owner", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "댓글삭제 이슈" }
                    ?: issueRepository.save(
                        Issue(title = "댓글삭제 이슈", body = "본문", project = project, number = 1L, authorId = owner.id, authorLoginId = owner.loginId)
                    )

                val ownerDetails = YonaUserDetails(
                    id = owner.id!!,
                    loginId = owner.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issue/${issue.number}")
                        .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                val alertCall = body.substringAfter("alert(").substringBefore("+ ' failed');")
                alertCall.contains("\"\"") shouldBe false
                // Accept-Language 헤더 없는 요청은 P3-51 로케일 결정성 수정 이후 root(영어)
                // 번들로 렌더링된다(common.comment.delete의 root 값은 "Delete comment").
                body.contains("Delete comment") shouldBe true
            }
        }
    }
}
