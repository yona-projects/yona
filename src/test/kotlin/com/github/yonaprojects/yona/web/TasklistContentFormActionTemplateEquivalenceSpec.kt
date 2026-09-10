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

// P3-48 화면별 재현 세션에서 발견: yona.Tasklist.js는 마크다운 본문 바로 앞의 (숨겨진) <form
// action="...">의 action 속성 URL로 PATCH를 보내 체크박스 토글을 저장한다(issue/view.html:65,
// board/view.html:51). 그런데 그 th:action이 owner/projectName 경로 변수를 써서
// "/api/{owner}/{projectName}/issues/{number}/content" 문자열을 그대로 만들어냈는데, 실제
// 백엔드(IssueController: @RequestMapping("/api/projects/{projectId}/issues"),
// BoardController: @RequestMapping("/api/projects/{projectId}/posts"))는 owner/projectName이
// 아니라 project.id 숫자를 받는다 — 렌더링된 URL이 어느 컨트롤러 매핑과도 맞지 않아 항상 404였다.
// CSRF와는 무관한 순수 URL 불일치 버그로, 실제 Playwright 재현(체크박스 클릭)에서 404 응답으로
// 발견했다. project.id 기반 경로로 수정했다.
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

                // 주의: BoardController.updatePostingContent()의 @PathVariable postId는 이름과
                // 달리 실제로는 PostingServiceImpl.getPosting(projectId, number)에 그대로 전달돼
                // "게시글 번호"(number)로 조회된다 — DB 기본키(id)가 아니다. 처음엔 post.id로
                // 잘못 만들었다가, 다른 스펙과 함께 전체 스위트를 돌릴 때만(=posting.id가
                // posting.number와 우연히 같지 않게 되는 시점부터) 404로 재현되는 것을 발견해
                // 정정했다 — 순서 의존적인 테스트 오염이 아니라 실제 운영에서도 재현되는 버그였다
                // (id/number가 다른 프로젝트의 두 번째 이상 게시글은 전부 tasklist 저장이 깨짐).
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
