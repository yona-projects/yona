package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
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
import java.time.Instant
import java.time.temporal.ChronoUnit

// @codemirror/autocomplete 기반 멘션 자동완성("@"/":"/"#" 3트리거)으로 atjs/yona.Mention.js를
// 대체했다. MockMvc+Jsoup 하네스는 서버 초기 마크업까지만 볼 수 있어 Shadow DOM 내부 동작은
// 검증하지 않는다. 검증하는 마크업 계약:
//   - atjs/atwho/yona.Mention.js 리소스는 전부 사라져야 한다.
//   - tribute.min.{js,css}는 멘션과 무관한 라벨 트리거(yona.TitleHeadAutoCompletion.js, `[`)가
//     여전히 전역 Tribute 생성자에 의존하므로, 그 스크립트를 로드하는 3개 화면(board/create,
//     issue/create, issue/edit)에서는 남아있어야 하고 나머지 5개 화면에서는 사라져야 한다 -
//     이 의존 관계를 놓치고 8곳 전부에서 걷어냈다가 `[` 트리거를 깨뜨릴 뻔한 적이 있다.
//   - markdownEditor 프래그먼트는 render-url과 동일한 게이트(project != null)로
//     data-mention-url을 노출해야 한다. 옛 yona.Mention()은 8개 화면에만 하드코딩 호출됐지만
//     이는 의도적 설계가 아니라 그 8개만 호출 코드가 있었을 뿐이었으므로(pullrequest 두 곳은
//     tribute.min.js조차 로드하지 않아 이미 깨져 있었다), project 컨텍스트가 있는 16개 화면
//     전부로 범위를 넓혔다.
//   - yona-lib.js에 남은 구식 atjs 기반 yona.Mention 사본은 이제 아무 곳에서도 호출되지 않는
//     죽은 코드다(번들 자체 정리는 별도 사안).
class MentionAutocompleteWidgetTemplateEquivalenceSpec @Autowired constructor(
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

        describe("P3-46 #3 자동완성 위젯(atjs -> Tribute.js) 마크업 계약 회귀 검증") {
            val owner = userRepository.findByLoginId("mention-owner").orElseGet {
                userRepository.save(User(loginId = "mention-owner", name = "멘션위젯소유자", email = "mention-owner@yona.io"))
            }
            val member = userRepository.findByLoginId("mention-member").orElseGet {
                userRepository.save(User(loginId = "mention-member", name = "멘션위젯멤버", email = "mention-member@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val project = projectRepository.findAll().find { it.name == "mention-proj" && it.owner == "mention-owner" }
                ?: projectRepository.save(
                    Project(
                        name = "mention-proj",
                        owner = "mention-owner",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )

            // pull/new, pull/{number}/edit 폼은 브랜치 목록이 비어있지 않아야 렌더링되므로
            // 물리 bare git 저장소를 만들고 커밋을 하나 심어둔다.
            run {
                val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
                if (!gitDir.exists()) {
                    repositoryService.getRepository(project).create()
                    val bareCommit = BareCommit(project, owner, gitBaseDir)
                    bareCommit.commitTextFile("README.md", "# mention-proj", "initial commit")
                }
            }

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

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "멘션위젯 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "멘션위젯 이슈",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId,
                        dueDate = Instant.now().plus(3, ChronoUnit.DAYS)
                    )
                )

            val milestone = milestoneRepository.findAll().find { it.project.id == project.id && it.title == "멘션위젯 마일스톤" }
                ?: milestoneRepository.save(
                    Milestone(
                        title = "멘션위젯 마일스톤",
                        project = project,
                        state = State.OPEN,
                        dueDate = Instant.now().plus(5, ChronoUnit.DAYS)
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "멘션위젯 게시글" }
                ?: postingRepository.save(
                    Posting(
                        title = "멘션위젯 게시글",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId
                    )
                )

            val openPr = pullRequestRepository.findAll().find { it.title == "멘션위젯 PR" } ?: pullRequestRepository.save(
                PullRequest(
                    title = "멘션위젯 PR",
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

            val expectedMentionUrl = "/api/${project.owner}/${project.name}/mentionList"

            fun assertNoLegacyMentionResources(doc: Document) {
                doc.select("script[src*=atjs]").size shouldBe 0
                doc.select("link[href*=atjs]").size shouldBe 0
                doc.select("script[src*=atwho]").size shouldBe 0
                doc.select("link[href*=atwho]").size shouldBe 0
                doc.select("script[src*='yona.Mention.js']").size shouldBe 0
            }

            fun assertTributePresence(doc: Document, expectedPresent: Boolean) {
                val expectedCount = if (expectedPresent) 1 else 0
                doc.select("script[src='/javascripts/lib/tribute/tribute.min.js']").size shouldBe expectedCount
            }

            fun assertMentionUrlExposed(doc: Document): org.jsoup.select.Elements {
                val editorWraps = doc.select("[data-toggle=markdown-editor]")
                (editorWraps.size > 0) shouldBe true
                editorWraps.forEach { it.attr("data-mention-url") shouldBe expectedMentionUrl }
                editorWraps.forEach { it.select("yona-markdown-editor").size shouldBe 1 }
                return editorWraps
            }

            fun fetchDoc(url: String) = Jsoup.parse(
                mockMvc.perform(get(url).with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString
            )

            it("issue/create(issueform) 화면은 atjs 없이 data-mention-url을 노출하고, tribute는 라벨 트리거(TitleHeadAutoCompletion) 때문에 유지되며 그 대상 title input도 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issueform")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = true)
                assertMentionUrlExposed(doc)
                doc.select("script[src='/javascripts/common/yona.TitleHeadAutoCompletion.js']").size shouldBe 1
                doc.select("input#title").size shouldBe 1
            }

            it("issue/edit(editform) 화면은 atjs 없이 data-mention-url을 노출하고, tribute는 라벨 트리거 때문에 유지되며 그 대상 title input도 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}/editform")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = true)
                assertMentionUrlExposed(doc)
                doc.select("script[src='/javascripts/common/yona.TitleHeadAutoCompletion.js']").size shouldBe 1
                doc.select("input#title").size shouldBe 1
            }

            it("board/create(post/new) 화면은 atjs 없이 data-mention-url을 노출하고, tribute는 라벨 트리거 때문에 유지되며 그 대상 title input도 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/post/new")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = true)
                assertMentionUrlExposed(doc)
                doc.select("script[src='/javascripts/common/yona.TitleHeadAutoCompletion.js']").size shouldBe 1
                doc.select("input#title").size shouldBe 1
            }

            it("board/edit(post/{number}/editform) 화면은 atjs/tribute 없이 data-mention-url을 노출해야 한다(이 화면은 라벨 트리거[TitleHeadAutoCompletion] 호출부가 없어 tribute도 필요 없다)") {
                val doc = fetchDoc("/${project.owner}/${project.name}/post/${posting.number}/editform")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = false)
                assertMentionUrlExposed(doc)
                doc.select("script[src='/javascripts/common/yona.TitleHeadAutoCompletion.js']").size shouldBe 0
            }

            it("milestone/create(milestone/new) 화면은 atjs/tribute 없이 data-mention-url을 노출해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/milestone/new")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = false)
                assertMentionUrlExposed(doc)
            }

            it("milestone/edit(editform) 화면은 atjs/tribute 없이 data-mention-url을 노출해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/milestone/${milestone.id}/editform")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = false)
                assertMentionUrlExposed(doc)
            }

            it("pullrequest/create(pull/new) 화면은 atjs/tribute를 로드한 적이 없었고(범위 밖 발견) 지금도 로드하지 않으며, data-mention-url을 노출해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/new")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = false)
                assertMentionUrlExposed(doc)
            }

            it("pullrequest/edit(pull/{number}/edit) 화면은 atjs/tribute를 로드한 적이 없었고(범위 밖 발견) 지금도 로드하지 않으며, data-mention-url을 노출해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/${openPr.number}/edit")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = false)
                assertMentionUrlExposed(doc)
            }

            it("issue/view(이슈 상세, 옛 CM5/Tribute 시절엔 yona.Mention() 호출 자체가 없던 화면) 화면도 이제 data-mention-url을 노출해야 한다 - 5단계 스코프 확장 판단(site/layout.html markdownEditor 프래그먼트 주석 참고)") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}")
                assertNoLegacyMentionResources(doc)
                assertTributePresence(doc, expectedPresent = false)
                assertMentionUrlExposed(doc)
            }
        }
    }
}
