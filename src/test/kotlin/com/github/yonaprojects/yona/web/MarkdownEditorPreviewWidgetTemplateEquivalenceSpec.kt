package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
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

// P3-46 #8-2(미리보기 연동, 2단계 시점 갱신): site/layout.html::markdownEditor 프래그먼트가
// project 컨텍스트가 있는 화면에서 data-markdown-render-url="/markdown/{owner}/{name}" 속성을
// 노출해야 한다. 이 속성은 2단계 시점에서는 아직 아무도 읽지 않지만(CM6 컴포넌트에 미리보기
// 자체가 없음 - 서버 렌더링 미리보기 재연동은 4단계 범위), 4단계에서 컴포넌트가 이 속성을 읽기
// 시작할 수 있도록 마크업 계약 자체는 계속 유지·검증한다.
//
// 이 스펙은 실제 브라우저 JS 실행(CM6 마운트/hljs 하이라이팅 등)은 검증하지 않는다 -- MockMvc+
// Jsoup 하네스는 렌더링된 마크업까지만 볼 수 있다. 대신 아래 "마크업 계약"을 검증한다:
//   1) project 컨텍스트가 있는 대표 화면들(issue/view, board/view, wiki/edit(_new), milestone/create,
//      pullrequest/view-common/reviewForm)에서 data-markdown-render-url이 정확한 값으로
//      렌더링돼야 한다.
//   2) 2단계 depth로 중첩된 프래그먼트 호출(board/view -> common/commentUpdateForm ->
//      markdownEditor)에서도 project 컨텍스트가 끊기지 않고 전달돼야 한다 -- Thymeleaf 프래그먼트는
//      파라미터 시그니처가 있어도 호출 측 전체 모델 컨텍스트를 상속받는다는 것을 board/postform
//      직접 렌더링 테스트로 사전에 확인했다(1단계 깊이). 이 스펙은 2단계 깊이에서도 동일함을
//      검증한다.
//   3) P3-46 8번 항목 2단계(셸 교체, EasyMDE(CodeMirror5) -> CM6 Web Component): EasyMDE/
//      yobi.ui.MarkdownEditor.js 리소스 로드가 0개로 사라지고, yona-markdown-editor.min.js가
//      1개 로드되는지. highlight.js(markdown(project) fragment의 hljs.highlightAll()이 여전히
//      의존 - site/layout.html head::head/scripts 주석 참고)는 계속 1개 로드돼야 한다. 이
//      단계에서는 에디터-highlight.js 간 로드 순서 제약이 없다(그 제약은 previewRender 때문이었고
//      이제 previewRender 자체가 없음 - 4단계에서 재도입 시 다시 검토).
class MarkdownEditorPreviewWidgetTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val pullRequestRepository: PullRequestRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-46 #8-2 마크다운 에디터 미리보기 연동(data-markdown-render-url) 마크업 계약 회귀 검증") {
            val member = userRepository.findByLoginId("mdpreview-member").orElseGet {
                userRepository.save(User(loginId = "mdpreview-member", name = "미리보기위젯멤버", email = "mdpreview-member@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val project = projectRepository.findAll().find { it.name == "mdpreview-proj" && it.owner == "mdpreview-member" }
                ?: projectRepository.save(
                    Project(
                        name = "mdpreview-proj",
                        owner = "mdpreview-member",
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

            val expectedRenderUrl = "/markdown/${project.owner}/${project.name}"

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "미리보기위젯 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "미리보기위젯 이슈",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId
                    )
                )

            val issueComment = issueCommentRepository.findAll().find { it.issue.id == issue.id && it.contents == "미리보기위젯 이슈댓글" }
                ?: issueCommentRepository.save(
                    IssueComment(
                        contents = "미리보기위젯 이슈댓글",
                        issue = issue,
                        authorId = member.id,
                        authorLoginId = member.loginId,
                        projectId = project.id
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "미리보기위젯 게시글" }
                ?: postingRepository.save(
                    Posting(
                        title = "미리보기위젯 게시글",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId
                    )
                )

            val postingComment = postingCommentRepository.findAll().find { it.posting.id == posting.id && it.contents == "미리보기위젯 댓글" }
                ?: postingCommentRepository.save(
                    PostingComment(
                        contents = "미리보기위젯 댓글",
                        posting = posting,
                        authorId = member.id,
                        authorLoginId = member.loginId,
                        projectId = project.id
                    )
                )

            val pr = pullRequestRepository.findAll().find { it.title == "미리보기위젯 PR" } ?: pullRequestRepository.save(
                PullRequest(
                    title = "미리보기위젯 PR",
                    body = "본문",
                    toProject = project,
                    fromProject = project,
                    toBranch = "master",
                    fromBranch = "master",
                    contributor = member,
                    state = State.OPEN,
                    number = 1L
                )
            )

            fun fetchDoc(url: String): Document = Jsoup.parse(
                mockMvc.perform(
                    get(url).with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString
            )

            fun assertEditorAndHighlightResourcesLoaded(doc: Document) {
                // EasyMDE(CodeMirror5) 리소스는 2단계에서 완전히 사라져야 한다.
                doc.select("link[href*='/javascripts/lib/easymde/'][rel=stylesheet]").size shouldBe 0
                doc.select("script[src*='/javascripts/lib/easymde/']").size shouldBe 0
                doc.select("script[src*='yobi.ui.MarkdownEditor.js']").size shouldBe 0

                // CM6 기반 Web Component 번들이 그 자리를 대신한다.
                doc.select("script[src*='/javascripts/lib/yona-markdown-editor/']").size shouldBe 1

                // markdown(project) fragment의 hljs.highlightAll()(SSR 콘텐츠 하이라이팅)이 여전히
                // 의존하므로 highlight.js는 그대로 유지돼야 한다.
                doc.select("script[src='/javascripts/lib/highlight/highlight.pack.js']").size shouldBe 1
            }

            it("issue/view(이슈 상세, 새 댓글 폼 + 기존 댓글 수정 폼 2단계 중첩) 화면은 모든 markdownEditor에 올바른 data-markdown-render-url을 노출해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}")
                assertEditorAndHighlightResourcesLoaded(doc)

                val editorWraps = doc.select("[data-toggle=markdown-editor]")
                (editorWraps.size > 0) shouldBe true
                editorWraps.forEach { it.attr("data-markdown-render-url") shouldBe expectedRenderUrl }

                // 기존 댓글 인라인 수정 폼(issue/view.html -> common/commentUpdateForm ->
                // markdownEditor - board/view와 동일한 2단계 깊이 중첩 케이스).
                val updateFormEditor = doc.select("#comment-editform-${issueComment.id} [data-toggle=markdown-editor]")
                updateFormEditor.size shouldBe 1
                updateFormEditor.attr("data-markdown-render-url") shouldBe expectedRenderUrl
            }

            it("board/view(게시글 상세, 새 댓글 폼 + 기존 댓글 수정 폼 2단계 중첩) 화면은 두 markdownEditor 모두 올바른 data-markdown-render-url을 노출해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/post/${posting.number}")
                assertEditorAndHighlightResourcesLoaded(doc)

                // 새 댓글 작성 폼(board/view.html이 markdownEditor를 직접 호출 - 1단계 깊이).
                val newCommentEditor = doc.select("form#comment-form [data-toggle=markdown-editor]")
                newCommentEditor.size shouldBe 1
                newCommentEditor.attr("data-markdown-render-url") shouldBe expectedRenderUrl

                // 기존 댓글 인라인 수정 폼(board/view.html -> common/commentUpdateForm ->
                // markdownEditor - 2단계 깊이 중첩. commentUpdateForm은 project를 파라미터로
                // 넘기지 않으므로, 이 케이스가 실패한다면 컨텍스트 상속이 중첩 깊이에 따라 끊긴다는
                // 뜻이다).
                val updateFormEditor = doc.select("#comment-editform-${postingComment.id} [data-toggle=markdown-editor]")
                updateFormEditor.size shouldBe 1
                updateFormEditor.attr("data-markdown-render-url") shouldBe expectedRenderUrl
            }

            it("wiki/edit(_new, 새 위키 페이지 작성) 화면은 markdownEditor에 올바른 data-markdown-render-url을 노출해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/wiki/_new")
                assertEditorAndHighlightResourcesLoaded(doc)

                val editorWrap = doc.select("[data-toggle=markdown-editor]")
                editorWrap.size shouldBe 1
                editorWrap.attr("data-markdown-render-url") shouldBe expectedRenderUrl
            }

            it("milestone/create(milestone/new, 마일스톤 작성) 화면은 markdownEditor에 올바른 data-markdown-render-url을 노출해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/milestone/new")
                assertEditorAndHighlightResourcesLoaded(doc)

                val editorWrap = doc.select("[data-toggle=markdown-editor]")
                editorWrap.size shouldBe 1
                editorWrap.attr("data-markdown-render-url") shouldBe expectedRenderUrl
            }

            it("pullrequest/view(PR changes 탭, common/reviewForm을 통해 markdownEditor를 쓰는 화면) 화면은 올바른 data-markdown-render-url을 노출해야 한다") {
                // common/reviewForm은 pullrequest/view.html의 tab=='changes' 블록 안에서만
                // include되고, 이 블록은 /pull/{number}(overview 탭)가 아니라
                // /pull/{number}/changes 전용 라우트(viewChangesInternal)에서만 tab="changes"로
                // 채워진다(PullRequestViewController 확인).
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/${pr.number}/changes")
                assertEditorAndHighlightResourcesLoaded(doc)

                val editorWraps = doc.select("[data-toggle=markdown-editor]")
                (editorWraps.size > 0) shouldBe true
                editorWraps.forEach { it.attr("data-markdown-render-url") shouldBe expectedRenderUrl }
            }
        }
    }
}
