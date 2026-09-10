package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
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
import io.kotest.matchers.shouldNotBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.springframework.beans.factory.annotation.Value
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import java.io.File

// 그룹11 서브셋(#167/#168/#169/#175/#177/#180/#182) 전용 회귀 스펙. 공용
// TemplateEquivalenceSpec.kt와 병렬로 다른 에이전트가 작업 중이라 충돌을 피하기 위해 별도 파일로
// 분리했다 — 동일한 AbstractIntegrationTest 픽스처 패턴(owner/member/nonmember, publicProj)을 재사용한다.
class PullRequestListTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val templateEngine: SpringTemplateEngine,
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

        describe("풀 리퀘스트 목록/생성/수정 화면(git/list,create,edit.scala.html) 동치성 회귀 검증") {
            val owner = userRepository.findByLoginId("pr-owner").orElseGet {
                userRepository.save(User(loginId = "pr-owner", name = "PR소유자", email = "pr-owner@yona.io"))
            }
            val member = userRepository.findByLoginId("pr-member").orElseGet {
                userRepository.save(User(loginId = "pr-member", name = "PR멤버", email = "pr-member@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val prProj = projectRepository.findAll().find { it.name == "pr-list-proj" } ?: projectRepository.save(
                Project(
                    name = "pr-list-proj",
                    owner = "pr-owner",
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = false,
                    vcs = "GIT"
                )
            )
            // create/edit 폼은 실제 브랜치 목록(repository.getRefNames())이 비어있지 않아야 렌더링되므로
            // 물리 bare git 저장소를 만들고 커밋을 하나 심어둔다(PullRequestServiceSpec의 기존 패턴 재사용).
            run {
                val repo = repositoryService.getRepository(prProj)
                val gitDir = File(File(gitBaseDir), "${prProj.owner}/${prProj.name}.git")
                if (!gitDir.exists()) {
                    repo.create()
                    val bareCommit = BareCommit(prProj, owner, gitBaseDir)
                    bareCommit.commitTextFile("README.md", "# pr-list-proj", "initial commit")
                }
            }
            if (!projectUserRepository.existsByProjectIdAndUserId(prProj.id!!, member.id!!)) {
                projectUserRepository.save(ProjectUser(project = prProj, user = member, role = roleMember))
            }

            val memberDetails = YonaUserDetails(
                id = member.id!!,
                loginId = member.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val openPr = pullRequestRepository.findAll().find { it.title == "오픈PR제목" } ?: pullRequestRepository.save(
                PullRequest(
                    title = "[feature] 오픈PR제목",
                    body = "본문",
                    toProject = prProj,
                    fromProject = prProj,
                    toBranch = "master",
                    fromBranch = "feature-a",
                    contributor = member,
                    state = State.OPEN,
                    number = 1L
                )
            )

            describe("[#167/#182] 목록 화면(pullrequest/list.html + partial_search)") {
                it("공용 GNB/헤더/메뉴 프래그먼트를 사용해야 하고, 가짜 .gnb-wrap이나 중복 jQuery CDN 로드가 없어야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pulls").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select(".project-menu-nav").size shouldNotBe 0
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select(".gnb-wrap").size shouldBe 0
                    doc.select("script[src*='code.jquery.com']").size shouldBe 0
                }

                it("legacy git/partial_list.scala.html처럼 <table>이 아니라 post-list-wrap/li 구조로 PR 목록을 렌더링해야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pulls").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("table.table").size shouldBe 0
                    doc.select("ul.post-list-wrap").size shouldNotBe 0
                    val item = doc.select("li.post-item.title").first()
                    item shouldNotBe null
                    item?.select(".title-prefix")?.text() shouldBe "[feature]"
                    item?.select("a.title")?.text() shouldBe "오픈PR제목"
                }

                it("탭(열림/닫힘) 뱃지와 새 PR 버튼, 상세검색 보낸이 셀렉트가 legacy처럼 렌더링되어야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pulls").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("ul.pullrequeset-tab-menu li").size shouldNotBe 0
                    doc.select("ul.pullrequeset-tab-menu .num-badge").size shouldNotBe 0
                    doc.select("a.ybtn-success").text() shouldBe "pull request"
                    doc.select("#advanced-search-form select#contributors").size shouldBe 1
                    doc.select("#two-column-mode-checkbox").size shouldBe 1
                }

                it("검색 필터(filter)를 넣어 재조회하면 제목에 필터 문자열이 없는 PR은 목록에서 제외되어야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pulls").param("filter", "존재하지않는검색어")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("li.post-item.title").size shouldBe 0
                    doc.select(".error-wrap").size shouldBe 1
                }

                it("닫힌 PR 탭(closedPullRequests)도 동일한 partial_search/partial_list 구조를 공유해야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/closedPullRequests").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("ul.post-list-wrap").size shouldNotBe 0
                    doc.select("li.pullrequeset-tab-menu, ul.pullrequeset-tab-menu li.active").size shouldNotBe 0
                }
            }

            describe("[#168] 생성 화면(pullrequest/create.html)") {
                it("공용 GNB를 쓰고 가짜 .gnb-wrap/중복 jQuery 로드가 없어야 하며, legacy DOM id 구조(pull-request-wrap/pull-left/pull-right/arrow)를 가져야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pull/new").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select(".gnb-wrap").size shouldBe 0
                    doc.select("script[src*='code.jquery.com']").size shouldBe 0
                    doc.select(".project-header-outer").size shouldBe 1

                    doc.select("#pull-request-wrap").size shouldBe 1
                    doc.select("#pull-request-wrap .pull-left").size shouldBe 1
                    doc.select("#pull-request-wrap .pull-right").size shouldBe 1
                    doc.select("#pull-request-wrap .arrow").size shouldBe 1
                    doc.select("#pullRequestState").size shouldBe 1
                    doc.select("#status").size shouldBe 1
                    doc.select("#title").size shouldBe 1
                    doc.select("#__commits").size shouldBe 1
                }

                it("select2/마크다운 에디터/파일 업로더 드롭존이 issue/create.html과 동일한 방식으로 이식되어야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pull/new").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    // P3-46 #5에서 Select2(v3)가 Tom Select로 교체돼(common/select2.html 주석 참고)
                    // 이제 lib/select2/select2.js가 아니라 tom-select.complete.min.js가 로드된다 —
                    // 이 테스트는 그 교체 이전의 스크립트 경로를 그대로 확인하고 있던 낡은 단언이었다
                    // (P3-51 로케일 작업 중 발견한 무관한 스테일 테스트, 즉시 수정).
                    doc.select("script[src*='tom-select.complete.min.js']").size shouldBe 1
                    doc.select(".upload-wrap[data-resource-type=PULL_REQUEST]").size shouldBe 1
                    doc.select("input[name='filePath']").size shouldBe 1
                    doc.select("[data-toggle=markdown-editor]").size shouldNotBe 0
                }
            }

            describe("[#169] 수정 화면(pullrequest/edit.html)") {
                it("legacy처럼 from/to 프로젝트·브랜치 셀렉트가 모두 disabled이고, hidden input으로 실제 값을 유지해야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pull/${openPr.number}/edit").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("#fromBranch[disabled]").size shouldBe 1
                    doc.select("#toBranch[disabled]").size shouldBe 1
                    doc.select("input[name=fromBranch]").attr("value") shouldBe "feature-a"
                    doc.select("input[name=toBranch]").attr("value") shouldBe "master"
                    doc.select("button.ybtn-success").text() shouldBe "Save"
                }

                it("PR이 열림(OPEN) 상태면 #status 병합 알림이 노출되어야 한다(legacy pullRequest.isOpen 조건)") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pull/${openPr.number}/edit").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("#status").size shouldBe 1
                }

                it("가짜 GNB/중복 jQuery 로드가 없어야 한다") {
                    val result = mockMvc.perform(
                        get("/pr-owner/pr-list-proj/pull/${openPr.number}/edit").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".gnb-wrap").size shouldBe 0
                    doc.select("script[src*='code.jquery.com']").size shouldBe 0
                }
            }

            describe("[#175/#180] partial_forklist.html / partial_recently_pushed_branches.html 단독 렌더 스모크 테스트") {
                it("partial_forklist 프래그먼트는 fork가 없는 프로젝트에 대해 예외 없이 아무것도 렌더링하지 않아야 한다") {
                    val context = Context()
                    context.setVariable("project", prProj)

                    val rendered = templateEngine.process("pullrequest/partial_forklist", setOf("forklist"), context)
                    rendered.trim() shouldBe ""
                }

                it("partial_recently_pushed_branches 프래그먼트는 빈 목록에 대해 예외 없이 아무것도 렌더링하지 않아야 한다") {
                    val context = Context()
                    context.setVariable("pushedBranches", emptyList<Any>())
                    context.setVariable("defaultBranches", emptyMap<Long, String>())

                    val rendered = templateEngine.process("pullrequest/partial_recently_pushed_branches", setOf("recentlyPushedBranches"), context)
                    rendered.trim() shouldBe ""
                }
            }
        }
    }
}
