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
import io.kotest.matchers.string.shouldContain
import org.jsoup.Jsoup
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

// P3-52 항목1 — legacy issue/view.scala.html이 로드하던 common/yona.ReceiverList.js(댓글 작성 중
// 디바운스 AJAX로 "지금 등록하면 알림 받을 사람" 미리보기)가 소스는 있었지만 어느 템플릿에서도
// <script src>로 로드된 적이 없어 완전히 죽어있었다(대응 백엔드 API도 없었음, IssueController
// #commentNotiReceivers로 이번에 이식). 이 스펙은 issue/view.html이 그 스크립트/마크업을 실제로
// 배선하는지만 확인한다(디바운스 타이밍/실제 AJAX 응답 반영은 Playwright로 완료 시점에 확인).
@Transactional
class CommentNotiReceiverListWiringTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("이슈 상세 화면의 댓글 알림수신자 미리보기 배선") {
            it("로그인 사용자에게는 notification-receiver-list/ReceiverList.js/미리보기 API URL이 모두 렌더링돼야 한다") {
                val author = userRepository.save(User(loginId = "receiverlist-author", name = "수신자목록작성자", email = "receiverlist-author@yona.io"))
                val project = projectRepository.save(Project(name = "receiverlist-proj", owner = "receiverlist-owner", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(title = "수신자목록 테스트 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )
                val authorDetails = YonaUserDetails(
                    id = author.id!!,
                    loginId = author.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/${issue.number}").with(user(authorDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "/javascripts/common/yona.ReceiverList.js"
                body shouldContain "findNotiReceiversHandler("

                val doc = Jsoup.parse(body)
                doc.select("#comment-form .notification-receiver-list").size shouldBe 1
                doc.select("#comment-form").attr("data-noti-receivers-url") shouldBe
                    "/api/projects/${project.id}/issues/${issue.number}/commentNotiReceivers"
            }
        }
    }
}
