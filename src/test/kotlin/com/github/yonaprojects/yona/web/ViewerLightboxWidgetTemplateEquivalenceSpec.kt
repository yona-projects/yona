package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

// P3-46 #4: 이미지 라이트박스(ViewerJS)의 jQuery 래퍼(jquery-viewer.js) 제거, 순정 Viewer.js API로 전환.
//
// 이 스펙은 실제 라이트박스 팝업 동작(브라우저 JS 상호작용)은 검증하지 않는다 — MockMvc+Jsoup
// 하네스는 렌더링된 마크업과 로드되는 스크립트/CSS 경로, 인라인 초기화 스크립트의 텍스트까지만
// 볼 수 있다. 대신 아래 "마크업 계약"이 회귀 없이 유지되는지를 검증한다:
//   (1) jquery-viewer.js는 더 이상 로드되지 않는다.
//   (2) viewer.js/viewer.css는 여전히 로드된다(site/layout.html이 전역으로 include).
//   (3) 초기화 스크립트가 jQuery 플러그인 호출($that.viewer()) 대신 순정 API(new Viewer(...))를
//       쓴다.
//   (4) .markdown-wrap 컨테이너가 여러 개 있는 화면(issue/view — 본문 + 댓글들)에서 각 컨테이너가
//       그대로 렌더링된다(각각에 독립 Viewer 인스턴스가 붙는 것은 브라우저에서만 확인 가능하지만,
//       "컨테이너마다 순회한다"는 초기화 스크립트의 $(".markdown-wrap").each(...) 구조는 유지돼야
//       한다).
//
// 레이아웃은 전역이므로 markdown-wrap이 전혀 없는 화면(issue/list)에서도 viewer.js/css는 항상
// 로드되고 jquery-viewer.js는 항상 로드되지 않아야 한다.
class ViewerLightboxWidgetTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-46 #4 이미지 라이트박스(ViewerJS jQuery 래퍼 제거) 마크업 계약 회귀 검증") {
            val owner = userRepository.findByLoginId("viewerwidget-owner").orElseGet {
                userRepository.save(User(loginId = "viewerwidget-owner", name = "라이트박스위젯소유자", email = "viewerwidget-owner@yona.io"))
            }
            val member = userRepository.findByLoginId("viewerwidget-member").orElseGet {
                userRepository.save(User(loginId = "viewerwidget-member", name = "라이트박스위젯멤버", email = "viewerwidget-member@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val project = projectRepository.findAll().find { it.name == "viewerwidget-proj" && it.owner == "viewerwidget-owner" }
                ?: projectRepository.save(
                    Project(
                        name = "viewerwidget-proj",
                        owner = "viewerwidget-owner",
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

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "라이트박스위젯 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "라이트박스위젯 이슈",
                        body = "본문 이미지 ![img](/uploads/a.png)",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId
                    )
                )

            // issue/view.html은 본문(1개) + 댓글마다(각 1개) .markdown-wrap을 렌더링한다 — 한 화면에
            // 여러 컨테이너가 동시에 존재하는 실제 사례(댓글 여러 개)를 재현하기 위해 댓글 2개를 심는다.
            if (issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!).isEmpty()) {
                issueCommentRepository.save(
                    IssueComment(
                        contents = "댓글1 이미지 ![img](/uploads/b.png)",
                        issue = issue,
                        authorId = member.id,
                        authorLoginId = member.loginId
                    )
                )
                issueCommentRepository.save(
                    IssueComment(
                        contents = "댓글2 이미지 ![img](/uploads/c.png)",
                        issue = issue,
                        authorId = owner.id,
                        authorLoginId = owner.loginId
                    )
                )
            }

            fun fetchDoc(url: String) = Jsoup.parse(
                mockMvc.perform(get(url).with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString
            )

            fun assertJqueryViewerWrapperGone(doc: Document) {
                doc.select("script[src*=jquery-viewer]").size shouldBe 0
            }

            fun assertViewerJsStillLoaded(doc: Document) {
                doc.select("script[src='/javascripts/lib/viewerjs/viewer.js']").size shouldBe 1
                doc.select("link[href='/javascripts/lib/viewerjs/viewer.css']").size shouldBe 1
            }

            fun assertInitScriptUsesPlainViewerApi(doc: Document) {
                val initScript = doc.select("script").map { it.html() }
                    .firstOrNull { it.contains(".markdown-wrap") && it.contains("Viewer") }
                initScript shouldNotBe null
                // 순정 API로 재작성됐는지 — 컨테이너마다 순회하는 구조($(".markdown-wrap").each)는
                // 유지한 채, jQuery 플러그인 호출(.viewer())이 아니라 new Viewer(...)를 써야 한다.
                initScript!! shouldContain ".markdown-wrap"
                initScript shouldContain "new Viewer("
                initScript shouldNotContain ".viewer()"
                // transition:false(전환 애니메이션 없음)는 그대로 유지돼야 한다.
                initScript shouldContain "transition"
                // mouseover 시 커서를 포인터로 바꾸는 기존 jQuery 핸들러 로직은 순정 Viewer.js에
                // 동등 기능이 없어(공식 옵션/CSS 어디에도 없음을 확인) 그대로 보존한다.
                initScript shouldContain "mouseover"
                initScript shouldContain "cursor"
            }

            it("issue/view 화면(본문+댓글 2개, .markdown-wrap 컨테이너 3개)은 jquery-viewer.js 없이 viewer.js/css를 로드하고 각 컨테이너를 그대로 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}")

                assertJqueryViewerWrapperGone(doc)
                assertViewerJsStillLoaded(doc)
                assertInitScriptUsesPlainViewerApi(doc)

                // .markdown-wrap 클래스는 실제 이슈/댓글 본문("content markdown-wrap") 외에도
                // markdownEditor 프래그먼트(각 댓글의 인라인 수정 폼 + 새 댓글 작성 폼)가 자체
                // 제공하는 마크다운 문법 도움말 예시 블록과 미리보기(div.markdown-preview.markdown-wrap)
                // 에도 붙어있어, 이 화면 전체의 .markdown-wrap 총 개수는 이 티켓과 무관한 다른
                // 요소 수에 좌우돼 불안정하다(이슈 뷰: 도움말 예시 블록만 인스턴스당 9개). 이
                // 스펙의 관심사인 "실제 렌더링된 이슈/댓글 본문 컨테이너"는 div.content.markdown-wrap
                // 으로 좁혀 식별할 수 있다 — 본문 1개 + 댓글 2개 = 3개.
                doc.select("div.content.markdown-wrap").size shouldBe 3
            }

            it("issue/list 화면(.markdown-wrap이 전혀 없는 화면)도 레이아웃이 전역이라 viewer.js/css는 로드하고 jquery-viewer.js는 로드하지 않아야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issues")

                assertJqueryViewerWrapperGone(doc)
                assertViewerJsStillLoaded(doc)
                doc.select("div.markdown-wrap").size shouldBe 0
            }
        }
    }
}
