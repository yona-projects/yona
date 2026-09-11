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

// P3-46 #8 2단계: 마크다운 에디터 셸 교체(EasyMDE(CodeMirror5) 인스턴스 -> CM6 기반 Web
// Component <yona-markdown-editor>, javascripts/lib/yona-markdown-editor/에 vendoring).
//
// 이 스펙은 실제 CM6/Shadow DOM의 렌더링·상호작용(브라우저 JS)은 검증하지 않는다 — MockMvc+
// Jsoup 하네스는 서버가 내려주는 초기 마크업과 로드되는 스크립트/CSS 경로까지만 볼 수 있다(Shadow
// DOM 내부 동작은 Playwright가 담당 - stateless-launching-ripple.md 테스트 전략 전환 참고). 대신
// 아래 "마크업 계약"이 회귀 없이 유지되는지를 검증한다:
//   1) <yona-markdown-editor> 커스텀 엘리먼트가 렌더링되고, EasyMDE 리소스(CSS/JS)는 이제 0개,
//      yona-markdown-editor.min.js 스크립트 로드는 1개여야 한다.
//   2) 옛 탭 UI(ul.nav-tabs, data-mode=edit/preview)와 그 안의 체크리스트/알림수신자 마크업은
//      사라져야 한다(1단계부터 이어진 사용자 확정 결정사항). "임시저장 지우기"는 애초에
//      "4단계에서 재구현 예정"이었고 실제로 P3-55와 함께 재구현됐다(아래
//      assertClearTemporaryButtonReimplemented 참고 - 더는 "사라져야 하는" 목록에 없다).
//      서버가 렌더링하는 시점에는 <yona-markdown-editor> 안에 아직 실제 <textarea>가 없다(그건
//      브라우저에서 connectedCallback이 만든다) - 이 스펙은 그 대신 <yona-markdown-editor>
//      자체의 name/editor-mode 속성과 슬롯 콘텐츠(초기값)를 검증한다.
//   3) name/editor-mode 속성값은 기존 textarea의 name/data-editor-mode 계약과 동일한 값이어야
//      한다 - 폼 제출 시 서버가 받는 필드명, 컴포넌트가 재현할 data-editor-mode 등 다른 여러
//      곳이 의존한다. textarea의 id(id^=editor-) 유일성 생성은 2단계부터 서버가 아니라
//      컴포넌트(클라이언트) 책임으로 넘어갔으므로 이 스펙(서버 렌더링만 봄)에서는 검증하지 않는다.
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

        describe("P3-46 #8 2단계 마크다운 에디터 셸(EasyMDE -> CM6 Web Component) 마크업 계약 회귀 검증") {
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
                doc.select("script[src*='yobi.ui.MarkdownEditor.js']").size shouldBe 0
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

            // P3-55와 함께 처리: .editor-clear-temporary("임시저장 지우기")가 재구현됐는지 검증한다.
            // legacy는 고정 id="button-clear-temporary"(페이지당 에디터가 하나뿐이라 문제없었음)를
            // 썼지만, 이 포팅본은 한 페이지에 markdownEditor 인스턴스가 여럿일 수 있어(예: PR
            // changes 탭의 여러 스레드 답글 폼) 고정 id를 재사용하면 중복 id 회귀가 생긴다 - 그래서
            // class 기반(.editor-clear-temporary-button)으로만 다시 만들었다. .editor-notice-label
            // "안"이 아니라 형제 요소여야 한다(temporarySaveHandler의 noticePanel.html(...)가 그
            // 안의 내용을 통째로 갈아치우므로).
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
                // 서버가 렌더링하는 시점에는 <yona-markdown-editor> 하나만 있고, 그 안의 실제
                // <textarea>는 브라우저에서 connectedCallback이 만든다(컴포넌트 소스
                // components/editor/src/YonaMarkdownEditor.ts 참고) - id(editor- 접두어) 유일성
                // 생성도 그때 컴포넌트가 담당하므로 서버 렌더링 마크업에서는 검증할 대상이 없다.
                val editorElement = editorWrap.select("yona-markdown-editor")
                editorElement.size shouldBe 1
                editorElement.attr("name") shouldBe expectedName
                editorElement.attr("editor-mode") shouldBe expectedEditorMode
                // 옛 textarea 계약 중 markdown="true"/textarea 자체는 이제 서버 마크업이 아니라
                // 컴포넌트 책임이므로, 서버 쪽에서는 더 이상 <textarea>가 존재하지 않아야 한다.
                editorWrap.select("textarea").size shouldBe 0
            }

            fun assertHelpAndNoticeLabelPreserved(editorWrap: org.jsoup.select.Elements) {
                // help/markdown 프래그먼트 - 체크리스트 항목을 포함해 그대로 남아있어야 한다.
                editorWrap.select("div.markdown-help").size shouldBe 1
                (editorWrap.select("[data-toggle=markdown-help]").size > 0) shouldBe true
                // 임시저장 "Draft saved" 표시 위치 - 살아있는 기능이라 DOM은 유지되어야 한다.
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
