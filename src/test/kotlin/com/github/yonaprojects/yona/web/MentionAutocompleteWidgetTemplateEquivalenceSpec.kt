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

// P3-46 #3: @/:/#/[ 자동완성 위젯(atjs -> Tribute.js) 교체.
//
// 이 스펙은 실시간 자동완성 드롭다운 동작(브라우저 JS 상호작용)은 검증하지 않는다 — MockMvc+Jsoup
// 하네스는 렌더링된 마크업과 로드되는 스크립트/CSS 경로까지만 볼 수 있다. 대신 아래 "마크업 계약"이
// 회귀 없이 유지되는지를 검증한다: (1) atjs 리소스가 완전히 사라졌는지, (2) Tribute.js 리소스가
// 로드되는지, (3) 자동완성 대상 textarea/input이 그대로 남아 있는지.
//
// 대상 11개 호출부(yobi.Mention 8곳 + yonaTitleHeadModule 3곳):
//   issue/create, issue/edit, board/create, board/edit, milestone/create, milestone/edit,
//   pullrequest/create, pullrequest/edit
//
// 메인 세션이 리뷰 중 발견한 회귀(TDD 에이전트가 놓쳤던 것): yobi.Mention을 호출하는 6개 화면
// (pullrequest 2개 제외) 어디에도 "<script src=.../common/yobi.Mention.js>" 태그가 없었다 — 실제
// 실행되는 yobi.Mention은 전역 레이아웃이 먼저 로드하는 yona-lib.js 번들에 박혀있던 구식 atjs
// 기반 사본뿐이었고, 이 티켓이 편집한 common/yobi.Mention.js 소스는 브라우저에 전혀 반영되지
// 않는 죽은 파일이었다. atjs 삭제로 그 번들 사본이 이제 항상 크래시하게 되어, 지금까지 실제로
// 동작하던 ":" 이모지 트리거까지 깨지는 회귀였다 — 6개 화면에 tribute.min.js 바로 뒤로 그 스크립트
// 태그를 추가해(실행 순서상 번들의 정의를 새 정의로 덮어씀) 해결했다. assertMentionScriptLoaded가
// 그 회귀 방지 테스트다. yona-lib.js 번들 자체에 남은 구식 사본은 이제 완전히 죽은 코드가 됐지만,
// 빌드 파이프라인 없이 수작업으로 만들어진 레거시 번들이라 정리는 별도 사안으로 남겨둔다.
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

            // pull/new, pull/{number}/edit 폼은 실제 브랜치 목록이 비어있지 않아야 렌더링되므로
            // 물리 bare git 저장소를 만들고 커밋을 하나 심어둔다(PullRequestListTemplateEquivalenceSpec의
            // 기존 패턴 재사용 — 그 스펙과 병렬로 다른 브랜치/시나리오가 개발될 수 있어 별도 파일로 분리).
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

            fun assertNoAtjs(doc: Document) {
                doc.select("script[src*=atjs]").size shouldBe 0
                doc.select("link[href*=atjs]").size shouldBe 0
                doc.select("script[src*=atwho]").size shouldBe 0
                doc.select("link[href*=atwho]").size shouldBe 0
            }

            fun assertTributeLoaded(doc: Document) {
                doc.select("script[src='/javascripts/lib/tribute/tribute.min.js']").size shouldBe 1
            }

            // 메인 세션이 리뷰 중 발견한 회귀(에이전트가 놓침): 이 6개 화면 어디에도
            // "<script src=.../common/yobi.Mention.js>" 태그가 없어, 실제로 실행되는 yobi.Mention은
            // 전역으로 먼저 로드되는 yona-lib.js 번들에 박혀있던 구식 atjs 기반 사본뿐이었다(이
            // 소스 파일을 아무리 Tribute로 재작성해도 브라우저에는 전혀 반영되지 않는 죽은 파일).
            // atjs 라이브러리 삭제로 그 번들 사본이 이제 항상 "atwho is not a function"을 던지게
            // 되어, 지금까지 실제로 동작하던 ":" 이모지 트리거까지 깨지는 회귀였다. 6개 화면에
            // "<script src=.../common/yobi.Mention.js>"를 tribute.min.js 바로 뒤에 추가해
            // (스크립트 실행 순서상 번들의 구식 정의를 새 정의로 덮어쓰게 됨) 해결 — 이 단언이
            // 그 수정의 회귀 방지 테스트다.
            fun assertMentionScriptLoaded(doc: Document) {
                doc.select("script[src='/javascripts/common/yobi.Mention.js']").size shouldBe 1
            }

            fun fetchDoc(url: String) = Jsoup.parse(
                mockMvc.perform(get(url).with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString
            )

            it("issue/create(issueform) 화면은 atjs 대신 Tribute.js를 로드하고 멘션 대상 textarea와 라벨 트리거 대상 title input을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issueform")
                assertNoAtjs(doc)
                assertTributeLoaded(doc)
                assertMentionScriptLoaded(doc)
                doc.select("script[src='/javascripts/common/yona.TitleHeadAutoCompletion.js']").size shouldBe 1
                doc.select("textarea[id^=editor-]").size shouldBe 1
                doc.select("input#title").size shouldBe 1
            }

            it("issue/edit(editform) 화면은 atjs 대신 Tribute.js를 로드하고 멘션 대상 textarea와 라벨 트리거 대상 title input을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}/editform")
                assertNoAtjs(doc)
                assertTributeLoaded(doc)
                assertMentionScriptLoaded(doc)
                doc.select("script[src='/javascripts/common/yona.TitleHeadAutoCompletion.js']").size shouldBe 1
                doc.select("textarea[id^=editor-]").size shouldBe 1
                doc.select("input#title").size shouldBe 1
            }

            it("board/create(post/new) 화면은 atjs 대신 Tribute.js를 로드하고 멘션 대상 textarea와 라벨 트리거 대상 title input을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/post/new")
                assertNoAtjs(doc)
                assertTributeLoaded(doc)
                assertMentionScriptLoaded(doc)
                doc.select("script[src='/javascripts/common/yona.TitleHeadAutoCompletion.js']").size shouldBe 1
                doc.select("textarea[id^=editor-]").size shouldBe 1
                doc.select("input#title").size shouldBe 1
            }

            it("board/edit(post/{number}/editform) 화면은 atjs 대신 Tribute.js를 로드하고 멘션 대상 textarea를 유지해야 한다(이 화면은 라벨 트리거[TitleHeadAutoCompletion] 호출부가 없다)") {
                val doc = fetchDoc("/${project.owner}/${project.name}/post/${posting.number}/editform")
                assertNoAtjs(doc)
                assertTributeLoaded(doc)
                assertMentionScriptLoaded(doc)
                doc.select("script[src='/javascripts/common/yona.TitleHeadAutoCompletion.js']").size shouldBe 0
                doc.select("textarea[id^=editor-]").size shouldBe 1
            }

            it("milestone/create(milestone/new) 화면은 atjs 대신 Tribute.js를 로드하고 멘션 대상 textarea를 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/milestone/new")
                assertNoAtjs(doc)
                assertTributeLoaded(doc)
                assertMentionScriptLoaded(doc)
                doc.select("textarea[id^=editor-]").size shouldBe 1
            }

            it("milestone/edit(editform) 화면은 atjs 대신 Tribute.js를 로드하고 멘션 대상 textarea를 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/milestone/${milestone.id}/editform")
                assertNoAtjs(doc)
                assertTributeLoaded(doc)
                assertMentionScriptLoaded(doc)
                doc.select("textarea[id^=editor-]").size shouldBe 1
            }

            it("pullrequest/create(pull/new) 화면은 atjs를 로드한 적이 없었고(범위 밖 발견 — 최종 보고 참고) 지금도 로드하지 않으며, 멘션 대상 textarea는 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/new")
                assertNoAtjs(doc)
                // 범위 밖 발견: pullrequest/create.html은 atjs 시절에도 atjs/tribute 스크립트를
                // 전혀 로드하지 않은 채 yobi.Mention()만 호출하고 있었다(기존 버그, 최종 보고
                // 참고) — 이번 교체로 새로 추가하지 않고 기존 상태를 그대로 보존한다.
                doc.select("script[src='/javascripts/lib/tribute/tribute.min.js']").size shouldBe 0
                doc.select("textarea[id^=editor-]").size shouldBe 1
            }

            it("pullrequest/edit(pull/{number}/edit) 화면은 atjs를 로드한 적이 없었고(범위 밖 발견 — 최종 보고 참고) 지금도 로드하지 않으며, 멘션 대상 textarea는 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/${openPr.number}/edit")
                assertNoAtjs(doc)
                doc.select("script[src='/javascripts/lib/tribute/tribute.min.js']").size shouldBe 0
                doc.select("textarea[id^=editor-]").size shouldBe 1
            }

            it("issue/view 화면은 atjs를 더 이상 로드하지 않아야 한다(범위 밖 발견 — yobi.Mention() 호출 자체가 없는 죽은 include였음, 최종 보고 참고)") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}")
                assertNoAtjs(doc)
            }
        }
    }
}
