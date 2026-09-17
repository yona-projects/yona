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

// 마크다운 에디터 셸을 CodeMirror5 기반 구현에서 CM6 기반 Web Component(<yona-markdown-editor>)로
// 교체했다. MockMvc+Jsoup 하네스는 서버가 내려주는 초기 마크업/스크립트 경로까지만 볼 수 있어
// Shadow DOM 내부 동작(브라우저 JS)은 검증하지 않는다. 검증하는 마크업 계약:
//   - 옛 에디터 리소스는 0개, yona-markdown-editor 번들 로드는 1개.
//   - 옛 탭 UI(nav-tabs, data-mode)와 체크리스트/알림수신자 마크업은 사라져야 한다.
//   - name/editor-mode 속성은 기존 textarea의 name/data-editor-mode 계약과 동일해야 한다 -
//     폼 제출 필드명 등 여러 곳이 이 값에 의존한다. 실제 <textarea>는 브라우저에서
//     connectedCallback이 만들므로 서버 렌더링 마크업에는 없어야 한다.
//   - help/markdown, .editor-notice-label(임시저장 표시)은 그대로 유지되어야 한다.
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

        describe("P3-46 #8 2단계 마크다운 에디터 셸(CodeMirror5 기반 구현 -> CM6 Web Component) 마크업 계약 회귀 검증") {
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

            fun assertEditorResourcesLoaded(doc: Document) {
                doc.select("link[href*='/javascripts/lib/easymde/'][rel=stylesheet]").size shouldBe 0
                doc.select("script[src*='/javascripts/lib/easymde/']").size shouldBe 0
                doc.select("script[src*='yona.ui.MarkdownEditor.js']").size shouldBe 0
                doc.select("script[src*='/javascripts/lib/yona-markdown-editor/']").size shouldBe 1
            }

            fun assertOldTabUiGone(editorWrap: org.jsoup.select.Elements) {
                editorWrap.select("ul.nav-tabs").size shouldBe 0
                editorWrap.select("a[data-mode=edit]").size shouldBe 0
                editorWrap.select("a[data-mode=preview]").size shouldBe 0
                editorWrap.select(".task-list-button").size shouldBe 0
                editorWrap.select(".add-task-list-button").size shouldBe 0
                editorWrap.select(".notification-receiver").size shouldBe 0
                editorWrap.select("div.markdown-preview").size shouldBe 0
            }

            // legacy는 페이지당 에디터가 하나뿐이라 고정 id(#button-clear-temporary)를 썼지만,
            // 이 포팅본은 한 페이지에 에디터가 여럿일 수 있어(PR changes 탭) 중복 id를 피해
            // class 기반으로 만들었다. .editor-notice-label은 형제 요소여야 한다 -
            // temporarySaveHandler의 noticePanel.html(...)이 그 안의 내용을 통째로 갈아치운다.
            fun assertClearTemporaryButtonReimplemented(editorWrap: org.jsoup.select.Elements) {
                editorWrap.select("#button-clear-temporary").size shouldBe 0

                val clearWrap = editorWrap.select(".editor-clear-temporary")
                clearWrap.size shouldBe 1
                clearWrap.select(".editor-clear-temporary-button button").size shouldBe 1

                val noticeLabel = editorWrap.select(".editor-notice-label").first()
                val clearWrapEl = clearWrap.first()
                (noticeLabel != null && clearWrapEl != null &&
                    noticeLabel.parent() == clearWrapEl.parent()) shouldBe true
                clearWrapEl!!.select(".editor-notice-label").size shouldBe 0
            }

            fun assertMarkdownEditorElementContractPreserved(editorWrap: org.jsoup.select.Elements, expectedName: String, expectedEditorMode: String) {
                val editorElement = editorWrap.select("yona-markdown-editor")
                editorElement.size shouldBe 1
                editorElement.attr("name") shouldBe expectedName
                editorElement.attr("editor-mode") shouldBe expectedEditorMode
                // 실제 <textarea>는 브라우저 connectedCallback이 만드므로 서버 마크업엔 없어야 한다.
                editorWrap.select("textarea").size shouldBe 0
            }

            fun assertHelpAndNoticeLabelPreserved(editorWrap: org.jsoup.select.Elements) {
                // help/markdown.html이 Vue 3 SFC(<yona-help-markdown>)로 교체되면서 아코디언
                // 마크업은 클라이언트 마운트 시점에 Shadow DOM 안에 그려지므로 서버 렌더링에는
                // 없다 - 커스텀 엘리먼트 태그의 존재만 확인한다.
                editorWrap.select("yona-help-markdown").size shouldBe 1
                editorWrap.select(".editor-notice-label").size shouldBe 1
            }

            it("board/postform(게시글 작성) 화면은 CM6 Web Component 셸로 교체되어야 하고 name/editor-mode 계약과 도움말/임시저장 표시는 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/postform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertEditorResourcesLoaded(doc)

                val editorWrap = doc.select("[data-toggle=markdown-editor]")
                editorWrap.size shouldBe 1

                assertOldTabUiGone(editorWrap)
                assertMarkdownEditorElementContractPreserved(editorWrap, "body", "content-body")
                assertHelpAndNoticeLabelPreserved(editorWrap)
                assertClearTemporaryButtonReimplemented(editorWrap)
            }

            it("issue/issueform(이슈 작성) 화면은 CM6 Web Component 셸로 교체되어야 하고 name/editor-mode 계약과 도움말/임시저장 표시는 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issueform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertEditorResourcesLoaded(doc)

                val editorWrap = doc.select("[data-toggle=markdown-editor]")
                editorWrap.size shouldBe 1

                assertOldTabUiGone(editorWrap)
                assertMarkdownEditorElementContractPreserved(editorWrap, "body", "content-body")
                assertHelpAndNoticeLabelPreserved(editorWrap)
                assertClearTemporaryButtonReimplemented(editorWrap)
            }
        }
    }
}
