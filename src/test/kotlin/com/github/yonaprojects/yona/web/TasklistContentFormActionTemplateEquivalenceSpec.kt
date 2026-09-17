package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// yona.Tasklist.js는 마크다운 본문 앞 숨겨진 <form action="...">의 URL로 PATCH를 보내
// 체크박스 토글을 저장한다. th:action이 owner/projectName 경로 변수로
// "/api/{owner}/{projectName}/issues/{number}/content"를 만들었지만, 실제 컨트롤러
// (IssueController/BoardController)는 project.id 숫자를 받는 매핑이라 항상 404였다 —
// project.id 기반 경로로 수정했다.
class TasklistContentFormActionTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-48: 이슈/게시글 본문 tasklist 토글 폼의 action URL이 실제 PATCH 매핑과 일치해야 한다") {
            val owner = userRepository.findByLoginId("tasklist-owner").orElseGet {
                userRepository.save(User(loginId = "tasklist-owner", name = "태스크리스트소유자", email = "tasklist-owner@yona.io"))
            }
            val ownerDetails = YonaUserDetails(
                id = owner.id!!,
                loginId = owner.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val project = projectRepository.findAll().find { it.name == "tasklist-proj" && it.owner == "tasklist-owner" }
                ?: projectRepository.save(
                    Project(
                        name = "tasklist-proj",
                        owner = "tasklist-owner",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "태스크리스트 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "태스크리스트 이슈", body = "- [ ] task one", project = project, number = 1L,
                        authorId = owner.id, authorLoginId = owner.loginId
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "태스크리스트 게시글" }
                ?: postingRepository.save(
                    Posting(
                        title = "태스크리스트 게시글", body = "- [ ] task one", project = project, number = 1L,
                        authorId = owner.id, authorLoginId = owner.loginId
                    )
                )

            it("issue/view의 tasklist form action은 /api/projects/{projectId}/issues/{number}/content 여야 하고 실제로 PATCH가 통과해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val action = doc.select("div#issue-${issue.number} form").attr("action")
                action shouldBe "/api/projects/${project.id}/issues/${issue.number}/content"

                mockMvc.perform(
                    patch(action)
                        .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"- [x] task one","original":"- [ ] task one"}""")
                ).andExpect(status().isOk)
            }

            it("board/view의 tasklist form action은 /api/projects/{projectId}/posts/{postId}/content 여야 하고 실제로 PATCH가 통과해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/post/${posting.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                // 주의: BoardController.updatePostingContent()의 @PathVariable postId는 이름과 달리
                // PostingServiceImpl.getPosting(projectId, number)에 전달돼 "게시글 번호"(number)로
                // 조회된다 — DB 기본키(id)가 아니다. id/number가 다른 프로젝트의 두 번째 이상
                // 게시글은 전부 tasklist 저장이 깨지는 실제 운영 버그다.
                val action = doc.select("div#post-${posting.number} form").attr("action")
                action shouldBe "/api/projects/${project.id}/posts/${posting.number}/content"

                mockMvc.perform(
                    patch(action)
                        .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"- [x] task one","original":"- [ ] task one"}""")
                ).andExpect(status().isOk)
            }
        }
    }
}
