package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
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
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

// P3-46 #6+7: marked.js(v0.7 전후 구세대) -> v12+ 계열 메이저 버전업,
// highlight.js(v9.11.0) -> v11+ 계열 메이저 버전업을 함께 진행한다(교체가 아니라 같은 라이브러리의
// 버전업).
//
// 이 스펙은 실제 마크다운 렌더링 결과 HTML(코드블록에 hljs 클래스가 붙는지 등)은 검증하지 않는다
// -- MockMvc+Jsoup 하네스는 로드되는 스크립트 경로/렌더링된 정적 마크업까지만 볼 수 있고, 실제
// marked()/hljs 호출 결과는 클라이언트 JS 실행 결과라 서버 사이드 렌더링 검증으로는 단언할 수
// 없다. 대신 아래 "마크업/소스 계약"이 회귀 없이 유지되는지를 검증한다:
//   (1) marked.js/highlight.pack.js는 여전히 같은 경로(/javascripts/lib/marked.js,
//       /javascripts/lib/highlight/highlight.pack.js)에서 로드된다(파일 위치는 그대로, 내용만
//       최신 버전으로 교체).
//   (2) 항목3(atjs->Tribute)에서 발견된 것과 동일한 함정 -- marked()/hljs.*를 실제로 호출하는
//       common/yobi.Markdown.js가 이전까지 *어느 템플릿에서도* <script src>로 로드되지 않고,
//       전역 레거시 번들 yona-lib.js에 박혀있던 구버전 사본만 실행되고 있었다. 이번에 yobi.Markdown.js
//       를 실제로 로드하는 <script src> 태그를 추가했는지 검증한다.
//   (3) yobi.Markdown.js/site/layout.html의 소스 코드 자체가 신버전 API로 이식됐는지(구버전 API
//       호출이 남아있지 않은지) 직접 소스 텍스트로 검증한다 -- 실제 실행 결과는 볼 수 없지만,
//       "무엇을 호출하는 코드가 배포됐는지"는 정적으로 검증 가능하고, 이게 이번 버전업의 핵심
//       회귀 지점이다(구버전 API 호출부가 신버전 라이브러리와 만나면 런타임에서 깨진다).
//   (4) pullrequest/view.html은 site/layout.html :: scripts(yona-lib.js 포함)보다 site/layout.html
//       :: markdown(project) 프래그먼트를 *먼저* include하고 있어서, yobi.Markdown.js 스크립트
//       태그를 마크다운 프래그먼트 안에 추가하는 것만으로는 부족했다 -- 나중에 로드되는 yona-lib.js
//       번들이 전역 yobi.Markdown을 다시 구버전 사본으로 덮어써버리기 때문이다(항목3과 똑같은
//       함정의 변형). scripts 프래그먼트가 markdown 프래그먼트보다 먼저 오도록 순서를 바로잡았다
//       -- 이 스펙이 그 순서를 직접 검증한다.
class MarkdownRendererWidgetTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val milestoneRepository: MilestoneRepository,
    private val postingRepository: PostingRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val repositoryService: RepositoryService,
    @Value("\${yona.git.base-dir:/tmp/yona/git}") private val gitBaseDir: String
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-46 #6+7 마크다운 렌더러(marked.js/highlight.js) 버전업 마크업/소스 계약 회귀 검증") {
            val owner = userRepository.findByLoginId("mdver-owner").orElseGet {
                userRepository.save(User(loginId = "mdver-owner", name = "마크다운버전업소유자", email = "mdver-owner@yona.io"))
            }
            val member = userRepository.findByLoginId("mdver-member").orElseGet {
                userRepository.save(User(loginId = "mdver-member", name = "마크다운버전업멤버", email = "mdver-member@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val project = projectRepository.findAll().find { it.name == "mdver-proj" && it.owner == "mdver-owner" }
                ?: projectRepository.save(
                    Project(
                        name = "mdver-proj",
                        owner = "mdver-owner",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )

            if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, member.id!!)) {
                projectUserRepository.save(ProjectUser(project = project, user = member, role = roleMember))
            }

            // pull/new는 브랜치가 하나도 없으면 pullrequest/create.html이 아니라 error/badrequest로
            // 리다이렉트된다(PullRequestViewController.createPullRequestForm() 참고) -- 실제
            // 브랜치 목록이 비어있지 않도록 물리 bare git 저장소를 만들고 커밋을 하나 심어둔다
            // (MentionAutocompleteWidgetTemplateEquivalenceSpec의 기존 패턴 재사용).
            run {
                val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
                if (!gitDir.exists()) {
                    repositoryService.getRepository(project).create()
                    val bareCommit = BareCommit(project, owner, gitBaseDir)
                    bareCommit.commitTextFile("README.md", "# mdver-proj", "initial commit")
                }
            }

            val memberDetails = YonaUserDetails(
                id = member.id!!,
                loginId = member.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "마크다운버전업 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "마크다운버전업 이슈",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId
                    )
                )

            val milestone = milestoneRepository.findAll().find { it.project.id == project.id && it.title == "마크다운버전업 마일스톤" }
                ?: milestoneRepository.save(
                    Milestone(
                        title = "마크다운버전업 마일스톤",
                        project = project,
                        state = State.OPEN
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "마크다운버전업 게시글" }
                ?: postingRepository.save(
                    Posting(
                        title = "마크다운버전업 게시글",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId
                    )
                )

            val pr = pullRequestRepository.findAll().find { it.title == "마크다운버전업 PR" } ?: pullRequestRepository.save(
                PullRequest(
                    title = "마크다운버전업 PR",
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

            fun fetchRaw(url: String) =
                mockMvc.perform(get(url).with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString

            fun fetchDoc(url: String) = Jsoup.parse(fetchRaw(url))

            fun assertMarkedAndHljsLoaded(doc: Document) {
                doc.select("script[src='/javascripts/lib/marked.js']").size shouldBe 1
                doc.select("script[src='/javascripts/lib/highlight/highlight.pack.js']").size shouldBe 1
                doc.select("link[href='/javascripts/lib/highlight/styles/default.css']").size shouldBe 1
            }

            // 항목3(atjs->Tribute)과 동일한 함정 재발 방지 -- common/yobi.Markdown.js가 실제로
            // <script src>로 로드되는지(단순히 소스 파일을 고쳤다고 브라우저에 반영되는 게 아니다).
            fun assertMarkdownScriptLoaded(doc: Document) {
                doc.select("script[src='/javascripts/common/yobi.Markdown.js']").size shouldBe 1
            }

            it("issue/create(issueform) 화면은 marked.js/highlight.pack.js와 yobi.Markdown.js를 로드해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issueform")
                assertMarkedAndHljsLoaded(doc)
                assertMarkdownScriptLoaded(doc)
            }

            it("issue/edit(editform) 화면은 marked.js/highlight.pack.js와 yobi.Markdown.js를 로드해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}/editform")
                assertMarkedAndHljsLoaded(doc)
                assertMarkdownScriptLoaded(doc)
            }

            it("milestone/create(milestone/new) 화면은 marked.js/highlight.pack.js와 yobi.Markdown.js를 로드해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/milestone/new")
                assertMarkedAndHljsLoaded(doc)
                assertMarkdownScriptLoaded(doc)
            }

            it("milestone/edit(editform) 화면은 marked.js/highlight.pack.js와 yobi.Markdown.js를 로드해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/milestone/${milestone.id}/editform")
                assertMarkedAndHljsLoaded(doc)
                assertMarkdownScriptLoaded(doc)
            }

            it("milestone/view(milestone/{id}) 화면은 marked.js/highlight.pack.js와 yobi.Markdown.js를 로드해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/milestone/${milestone.id}")
                assertMarkedAndHljsLoaded(doc)
                assertMarkdownScriptLoaded(doc)
            }

            it("pullrequest/create(pull/new) 화면은 marked.js/highlight.pack.js와 yobi.Markdown.js를 로드해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/new")
                assertMarkedAndHljsLoaded(doc)
                assertMarkdownScriptLoaded(doc)
            }

            it("pullrequest/edit(pull/{number}/edit) 화면은 marked.js/highlight.pack.js와 yobi.Markdown.js를 로드해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/${pr.number}/edit")
                assertMarkedAndHljsLoaded(doc)
                assertMarkdownScriptLoaded(doc)
            }

            it("pullrequest/view(pull/{number}) 화면은 marked.js/highlight.pack.js와 yobi.Markdown.js를 로드하고, yona-lib.js(구버전 yobi.Markdown 사본이 박힌 전역 번들)보다 뒤에 yobi.Markdown.js가 와야 한다(그래야 새 정의가 번들의 구식 정의를 덮어쓴다)") {
                val raw = fetchRaw("/${project.owner}/${project.name}/pull/${pr.number}")
                val doc = Jsoup.parse(raw)
                assertMarkedAndHljsLoaded(doc)
                assertMarkdownScriptLoaded(doc)

                val yonaLibIdx = raw.indexOf("/javascripts/yona-lib.js")
                val markdownJsIdx = raw.indexOf("/javascripts/common/yobi.Markdown.js")
                (yonaLibIdx >= 0) shouldBe true
                (markdownJsIdx >= 0) shouldBe true
                (markdownJsIdx > yonaLibIdx) shouldBe true
            }

            // 범위 밖 발견(고치지 않고 보존) -- board/edit·create, board/view, issue/view, wiki/edit,
            // code/diff는 markdownEditor 프래그먼트(에디터 UI)는 쓰면서도 site/layout.html ::
            // markdown(project) 프래그먼트는 애초에 include하지 않는다. 즉 yobi.Markdown.init()이
            // 이 화면들에서는 한 번도 호출된 적이 없다(marked/highlight.js 버전과 무관한 기존
            // 구조적 공백 -- 최종 보고 참고). 이번 버전업으로 새로 생기거나 없어지는 문제가
            // 아니므로 현재 상태(스크립트 미로드)를 그대로 보존하는 회귀 가드만 남긴다.
            it("board/view 화면은 (기존과 마찬가지로) markdown 프래그먼트를 include하지 않아 marked.js/yobi.Markdown.js를 로드하지 않는다(범위 밖 기존 공백, 최종 보고 참고)") {
                val doc = fetchDoc("/${project.owner}/${project.name}/post/${posting.number}")
                doc.select("script[src='/javascripts/lib/marked.js']").size shouldBe 0
                doc.select("script[src='/javascripts/common/yobi.Markdown.js']").size shouldBe 0
            }

            it("issue/view 화면은 (기존과 마찬가지로) markdown 프래그먼트를 include하지 않아 marked.js/yobi.Markdown.js를 로드하지 않는다(범위 밖 기존 공백, 최종 보고 참고)") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}")
                doc.select("script[src='/javascripts/lib/marked.js']").size shouldBe 0
                doc.select("script[src='/javascripts/common/yobi.Markdown.js']").size shouldBe 0
            }

            // 소스 계약 검증 -- 실제 marked()/hljs 호출 결과(HTML)는 MockMvc로 볼 수 없으므로,
            // "신버전 API를 호출하는 코드가 배포됐는지"를 정적 텍스트로 직접 검증한다.
            it("static/javascripts/lib/marked.js는 v12+ 계열로 교체되고 구버전 deprecation 경고 문자열이 사라져야 한다") {
                val src = MarkdownRendererWidgetTemplateEquivalenceSpec::class.java
                    .getResourceAsStream("/static/javascripts/lib/marked.js")!!
                    .bufferedReader(Charsets.UTF_8).readText()

                // 구버전(0.7 전후) 특유의 sanitize/sanitizer deprecation 경고 문자열이 더 이상 없어야 한다.
                src shouldNotContain "sanitize and sanitizer parameters are deprecated since version 0.7.0"

                val versionMatch = Regex("""marked v(\d+)\.""").find(src)
                versionMatch shouldNotBe null
                val majorVersion = versionMatch!!.groupValues[1].toInt()
                (majorVersion >= 12) shouldBe true
            }

            it("static/javascripts/lib/highlight/highlight.pack.js는 v11+ 계열로 교체되어야 한다") {
                val src = MarkdownRendererWidgetTemplateEquivalenceSpec::class.java
                    .getResourceAsStream("/static/javascripts/lib/highlight/highlight.pack.js")!!
                    .bufferedReader(Charsets.UTF_8).readText()

                val versionMatch = Regex("""[Hh]ighlight\.js v(\d+)\.""").find(src)
                versionMatch shouldNotBe null
                val majorVersion = versionMatch!!.groupValues[1].toInt()
                (majorVersion >= 11) shouldBe true
            }

            it("common/yobi.Markdown.js는 marked/highlight.js 신버전 API로 이식되고 구버전 API 호출이 남아있지 않아야 한다") {
                val src = MarkdownRendererWidgetTemplateEquivalenceSpec::class.java
                    .getResourceAsStream("/static/javascripts/common/yobi.Markdown.js")!!
                    .bufferedReader(Charsets.UTF_8).readText()

                // 신버전 API
                src shouldContain "marked.parse("
                src shouldContain "hljs.highlightElement("
                // hljs.highlight()가 신버전 시그니처(코드가 첫 인자, 언어는 옵션 객체)로 호출돼야 한다.
                Regex("""hljs\.highlight\(\s*[A-Za-z_$][\w$]*\s*,\s*\{\s*language""").containsMatchIn(src) shouldBe true

                // 구버전 API가 더 이상 남아있지 않아야 한다.
                src shouldNotContain "hljs.highlightBlock("
                // 구버전 marked(text, options) 함수형 호출(옛 API)이 남아있지 않아야 한다.
                Regex("""[^.]marked\(\s*\w""").containsMatchIn(src) shouldBe false
                // 구버전 hljs.highlight(lang, code) 위치 인자 호출이 남아있지 않아야 한다.
                src shouldNotContain "hljs.highlight(sLang.toLowerCase(), sCode)"
            }

            it("site/layout.html의 markdown 프래그먼트는 hljs.highlightAll()을 쓰고 제거된 initHighlightingOnLoad()를 쓰지 않아야 한다") {
                val src = MarkdownRendererWidgetTemplateEquivalenceSpec::class.java
                    .getResourceAsStream("/templates/site/layout.html")!!
                    .bufferedReader(Charsets.UTF_8).readText()

                src shouldContain "hljs.highlightAll()"
                src shouldNotContain "hljs.initHighlightingOnLoad()"
                // yobi.Markdown.js를 실제로 로드하는 <script src> 태그가 markdown(project) 프래그먼트
                // 안에 있어야 한다(항목3과 동일한 함정 재발 방지).
                src shouldContain "src=\"/javascripts/common/yobi.Markdown.js\""
            }
        }
    }
}
