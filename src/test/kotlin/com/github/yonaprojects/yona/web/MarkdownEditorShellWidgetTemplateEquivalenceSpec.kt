package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

// P3-46 #8-1: 마크다운 에디터 셸 교체(textarea + 수제 Edit/Preview 탭 UI -> EasyMDE 인스턴스).
//
// 이 스펙은 실제 EasyMDE/CodeMirror의 렌더링·상호작용(브라우저 JS)은 검증하지 않는다 —
// MockMvc+Jsoup 하네스는 렌더링된 마크업과 로드되는 스크립트/CSS 경로까지만 볼 수 있다. 대신
// 아래 "마크업 계약"이 회귀 없이 유지되는지를 검증한다:
//   1) EasyMDE 리소스(자체 호스팅)가 로드되어야 한다.
//   2) 옛 탭 UI(ul.nav-tabs, data-mode=edit/preview)와 그 안의 체크리스트/임시저장 지우기/
//      알림수신자 마크업은 사라져야 한다(사용자 확정 결정사항 - 4단계에서 재구현 예정).
//   3) textarea의 name/id(id^=editor-)/data-editor-mode 속성 패턴은 그대로 유지되어야 한다 -
//      멘션(Tribute) 대상 셀렉터, 폼 제출 시 서버가 받는 필드명 등 다른 여러 곳이 의존한다.
//   4) help/markdown 프래그먼트(마크다운 도움말)는 에디터와 독립적이므로 변경 없이 그대로
//      렌더링되어야 한다.
//   5) .editor-notice-label(임시저장 "Draft saved" 표시 - 살아있는 기능)은 DOM에 남아있어야 한다.
class MarkdownEditorShellWidgetTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-46 #8-1 마크다운 에디터 셸(textarea+수제 탭 UI -> EasyMDE) 마크업 계약 회귀 검증") {
            val member = userRepository.findByLoginId("mdeditor-member").orElseGet {
                userRepository.save(User(loginId = "mdeditor-member", name = "에디터셸위젯멤버", email = "mdeditor-member@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val project = projectRepository.findAll().find { it.name == "mdeditor-proj" && it.owner == "mdeditor-member" }
                ?: projectRepository.save(
                    Project(
                        name = "mdeditor-proj",
                        owner = "mdeditor-member",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )

            if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, member.id!!)) {
                projectUserRepository.save(ProjectUser(project = project, user = member, role = roleMember))
            }

            val memberDetails = YonaUserDetails(
                id = member.id!!,
                loginId = member.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            fun assertEasyMdeResourcesLoaded(doc: Document) {
                doc.select("link[href*='/javascripts/lib/easymde/'][rel=stylesheet]").size shouldBe 1
                doc.select("script[src*='/javascripts/lib/easymde/']").size shouldBe 1
                doc.select("script[src*='yobi.ui.MarkdownEditor.js']").size shouldBe 1
            }

            fun assertOldTabUiGone(editorWrap: org.jsoup.select.Elements) {
                editorWrap.select("ul.nav-tabs").size shouldBe 0
                editorWrap.select("a[data-mode=edit]").size shouldBe 0
                editorWrap.select("a[data-mode=preview]").size shouldBe 0
                editorWrap.select(".task-list-button").size shouldBe 0
                editorWrap.select(".add-task-list-button").size shouldBe 0
                editorWrap.select(".editor-clear-temporary").size shouldBe 0
                editorWrap.select("#button-clear-temporary").size shouldBe 0
                editorWrap.select(".notification-receiver").size shouldBe 0
                editorWrap.select("div.markdown-preview").size shouldBe 0
            }

            fun assertTextareaContractPreserved(editorWrap: org.jsoup.select.Elements, expectedName: String, expectedEditorMode: String) {
                val textarea = editorWrap.select("textarea")
                textarea.size shouldBe 1
                textarea.attr("name") shouldBe expectedName
                textarea.attr("data-editor-mode") shouldBe expectedEditorMode
                textarea.attr("markdown") shouldBe "true"
                (textarea.attr("id").startsWith("editor-")) shouldBe true
            }

            fun assertHelpAndNoticeLabelPreserved(editorWrap: org.jsoup.select.Elements) {
                // help/markdown 프래그먼트 - 체크리스트 항목을 포함해 그대로 남아있어야 한다.
                editorWrap.select("div.markdown-help").size shouldBe 1
                (editorWrap.select("[data-toggle=markdown-help]").size > 0) shouldBe true
                // 임시저장 "Draft saved" 표시 위치 - 살아있는 기능이라 DOM은 유지되어야 한다.
                editorWrap.select(".editor-notice-label").size shouldBe 1
            }

            it("board/postform(게시글 작성) 화면은 EasyMDE 셸로 교체되어야 하고 name/id/data-editor-mode 계약과 도움말/임시저장 표시는 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/postform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertEasyMdeResourcesLoaded(doc)

                val editorWrap = doc.select("[data-toggle=markdown-editor]")
                editorWrap.size shouldBe 1

                assertOldTabUiGone(editorWrap)
                assertTextareaContractPreserved(editorWrap, "body", "content-body")
                assertHelpAndNoticeLabelPreserved(editorWrap)
            }

            it("issue/issueform(이슈 작성) 화면은 EasyMDE 셸로 교체되어야 하고 name/id/data-editor-mode 계약과 도움말/임시저장 표시는 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issueform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertEasyMdeResourcesLoaded(doc)

                val editorWrap = doc.select("[data-toggle=markdown-editor]")
                editorWrap.size shouldBe 1

                assertOldTabUiGone(editorWrap)
                assertTextareaContractPreserved(editorWrap, "body", "content-body")
                assertHelpAndNoticeLabelPreserved(editorWrap)
            }
        }
    }
}
