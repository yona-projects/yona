package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
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

// P3-46 #5: Select2(v3) -> Tom Select 교체.
//
// 이 스펙은 드롭다운/자동완성의 실시간 상호작용(브라우저 JS)은 검증하지 않는다 - MockMvc+Jsoup
// 하네스는 렌더링된 마크업과 로드되는 스크립트/CSS 경로까지만 볼 수 있다. 대신 아래 "마크업 계약"이
// 회귀 없이 유지되는지를 검증한다: (1) select2 리소스가 완전히 사라지고 Tom Select 리소스가
// 로드되는지, (2) data-toggle="select2"/data-format 등 JS가 의존하는 속성이 그대로 남아있는지,
// (3) issuelabel 포맷의 optgroup(카테고리) 구조 - data-category-id/data-category-is-exclusive - 가
// 그대로 유지되는지.
//
// 자동화 커버리지 범위(8개 화면 - 6개 data-format 전부 + plain(no-format) + 원격 AJAX 담당자):
//   issue/create(projects/issues/milestone/issuelabel), issue/edit(동일 4종, 다른 상태),
//   issue/list(user x2/milestone/issuelabel 필터), issue/view(milestone 단일 + #assignee 원격
//   AJAX + #issueSharer 죽은 위젯 마크업), project/create(user + plain/select2-without-searchbox),
//   board/list(issue/partial_select_label 재사용 - issuelabel), pullrequest/create·edit(plain 셀렉트
//   4종).
//
// 커버 안 된 화면(최종 보고 "수동 브라우저 확인 필요" 참고): project/importing, project/issuelabels,
// organization/boardList, organization/issueList, pullrequest/list, pullrequest/view,
// code/view, code/history, code/diff - 전부 마크업 구조는 issue/create·edit·list·view나
// project/create와 동일한 패턴(같은 yobi.ui.Select2.js/common/select2.html 프래그먼트)이라
// 회귀 위험이 낮다고 판단해 시간 예산상 자동화 테스트에서는 제외했다.
class SelectWidgetTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val milestoneRepository: MilestoneRepository,
    private val postingRepository: PostingRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val issueLabelRepository: IssueLabelRepository,
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

        describe("P3-46 #5 드롭다운/멀티셀렉트 위젯(Select2 v3 -> Tom Select) 마크업 계약 회귀 검증") {
            val owner = userRepository.findByLoginId("selwidget-owner").orElseGet {
                userRepository.save(User(loginId = "selwidget-owner", name = "셀렉트위젯소유자", email = "selwidget-owner@yona.io"))
            }
            val member = userRepository.findByLoginId("selwidget-member").orElseGet {
                userRepository.save(User(loginId = "selwidget-member", name = "셀렉트위젯멤버", email = "selwidget-member@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val project = projectRepository.findAll().find { it.name == "selwidget-proj" && it.owner == "selwidget-owner" }
                ?: projectRepository.save(
                    Project(
                        name = "selwidget-proj",
                        owner = "selwidget-owner",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )

            run {
                val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
                if (!gitDir.exists()) {
                    repositoryService.getRepository(project).create()
                    val bareCommit = BareCommit(project, owner, gitBaseDir)
                    bareCommit.commitTextFile("README.md", "# selwidget-proj", "initial commit")
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

            // issuelabel 포맷: 배타 카테고리 1개(단일 선택) + 비배타 카테고리 1개(다중 선택) -
            // optgroup의 data-category-id/data-category-is-exclusive 렌더링을 확인하기 위한 최소 픽스처.
            val exclusiveCategory = issueLabelCategoryRepository.findAll()
                .find { it.name == "셀렉트위젯-배타카테고리" && it.project.id == project.id }
                ?: issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "셀렉트위젯-배타카테고리", isExclusive = true, project = project)
                )
            val nonExclusiveCategory = issueLabelCategoryRepository.findAll()
                .find { it.name == "셀렉트위젯-비배타카테고리" && it.project.id == project.id }
                ?: issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "셀렉트위젯-비배타카테고리", isExclusive = false, project = project)
                )
            val exclusiveLabel = issueLabelRepository.findAll()
                .find { it.name == "우선순위-높음" && it.project.id == project.id }
                ?: issueLabelRepository.save(
                    IssueLabel(category = exclusiveCategory, color = "#ff0000", name = "우선순위-높음", project = project)
                )
            issueLabelRepository.findAll()
                .find { it.name == "영역-백엔드" && it.project.id == project.id }
                ?: issueLabelRepository.save(
                    IssueLabel(category = nonExclusiveCategory, color = "#00ff00", name = "영역-백엔드", project = project)
                )

            val milestone = milestoneRepository.findAll().find { it.project.id == project.id && it.title == "셀렉트위젯 마일스톤" }
                ?: milestoneRepository.save(
                    Milestone(
                        title = "셀렉트위젯 마일스톤",
                        project = project,
                        state = State.OPEN,
                        dueDate = Instant.now().plus(5, ChronoUnit.DAYS)
                    )
                )

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "셀렉트위젯 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "셀렉트위젯 이슈",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId,
                        dueDate = Instant.now().plus(3, ChronoUnit.DAYS)
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "셀렉트위젯 게시글" }
                ?: postingRepository.save(
                    Posting(
                        title = "셀렉트위젯 게시글",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId
                    )
                )

            val openPr = pullRequestRepository.findAll().find { it.title == "셀렉트위젯 PR" } ?: pullRequestRepository.save(
                PullRequest(
                    title = "셀렉트위젯 PR",
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

            fun assertNoSelect2(doc: Document) {
                // yobi.ui.Select2.js(래퍼 파일명, P3-46 앞선 항목들과 동일한 이유로 의도적으로 유지)를
                // 오검출하지 않도록 select2 "라이브러리" 경로(lib/select2)만 정확히 검사한다.
                doc.select("script[src*='lib/select2']").size shouldBe 0
                doc.select("link[href*='lib/select2']").size shouldBe 0
                doc.select("script[src*=select2_locale]").size shouldBe 0
            }

            fun assertTomSelectLoaded(doc: Document) {
                doc.select("script[src='/javascripts/lib/tomselect/tom-select.complete.min.js']").size shouldBe 1
                doc.select("script[src='/javascripts/common/yobi.ui.Select2.js']").size shouldBe 1
            }

            fun assertIssueLabelOptgroupMarkup(doc: Document) {
                val select = doc.select("select#labelIds[data-toggle=select2][data-format=issuelabel]")
                select.size shouldBe 1
                val exclusiveGroup = select.select("optgroup[label=\"셀렉트위젯-배타카테고리\"]")
                exclusiveGroup.size shouldBe 1
                exclusiveGroup.attr("data-category-id") shouldBe exclusiveCategory.id.toString()
                exclusiveGroup.attr("data-category-is-exclusive") shouldBe "true"
                val nonExclusiveGroup = select.select("optgroup[label=\"셀렉트위젯-비배타카테고리\"]")
                nonExclusiveGroup.size shouldBe 1
                nonExclusiveGroup.attr("data-category-is-exclusive") shouldBe "false"
                val exclusiveOption = select.select("option[value=${exclusiveLabel.id}]")
                exclusiveOption.size shouldBe 1
                exclusiveOption.attr("data-category-is-exclusive") shouldBe "true"
            }

            fun fetchDoc(url: String) = Jsoup.parse(
                mockMvc.perform(get(url).with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString
            )

            it("issue/create(issueform) 화면은 Tom Select 리소스를 로드하고 projects/issues/milestone/issuelabel 4개 포맷의 select 마크업을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issueform")
                assertNoSelect2(doc)
                assertTomSelectLoaded(doc)

                doc.select("select#targetProjectId[data-toggle=select2][data-format=projects]").size shouldBe 1
                doc.select("select#parentId[data-toggle=select2][data-format=issues]").size shouldBe 1
                doc.select("select#milestoneId[data-toggle=select2][data-format=milestone]").size shouldBe 1
                assertIssueLabelOptgroupMarkup(doc)
            }

            it("issue/edit(editform) 화면은 Tom Select 리소스를 로드하고 projects/issues/milestone/issuelabel 4개 포맷의 select 마크업을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}/editform")
                assertNoSelect2(doc)
                assertTomSelectLoaded(doc)

                doc.select("select#targetProjectId[data-toggle=select2][data-format=projects]").size shouldBe 1
                doc.select("select#parentId[data-toggle=select2][data-format=issues]").size shouldBe 1
                doc.select("select#milestoneId[data-toggle=select2][data-format=milestone]").size shouldBe 1
                assertIssueLabelOptgroupMarkup(doc)
            }

            it("issue/list(issues) 화면은 Tom Select 리소스를 로드하고 authorId/assigneeId(user)·milestoneId(milestone)·labelIds(issuelabel) 필터 select 마크업을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issues")
                assertNoSelect2(doc)
                assertTomSelectLoaded(doc)

                doc.select("select#authorId[data-toggle=select2][data-format=user]").size shouldBe 1
                doc.select("select#assigneeId[data-toggle=select2][data-format=user]").size shouldBe 1
                doc.select("select#milestoneId[data-toggle=select2][data-format=milestone]").size shouldBe 1
                assertIssueLabelOptgroupMarkup(doc)
            }

            it("issue/view 화면은 Tom Select 리소스를 로드하고 milestone(단일) select와 원격 AJAX 담당자 input(#assignee)·공유자 input(#issueSharer) 마크업을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issue/${issue.number}")
                assertNoSelect2(doc)
                assertTomSelectLoaded(doc)

                doc.select("select#milestone[data-toggle=select2][data-format=milestone]").size shouldBe 1
                // #assignee는 data-toggle="select2"가 아니라 yona.issue.Assginee.js가 직접 TomSelect를
                // 생성한다 - 그 스크립트가 로드되고 hidden input이 그대로 남아있는지만 확인한다.
                doc.select("script[src='/javascripts/service/yona.issue.Assginee.js']").size shouldBe 1
                doc.select("input#assignee[type=hidden]").size shouldBe 1
                // P3-66: #issueSharer도 동일한 패턴으로 배선이 복원됐다(더 이상 죽은 코드가 아니다) -
                // hidden input + yona.issue.Sharer.js 로드 + yonaIssueSharerModule(...) 초기화 호출을
                // 확인한다(실제 검색/추가/제거 동작은 IssueSharerWidgetWiringTemplateRenderingSpec/
                // Playwright가 검증).
                doc.select("input#issueSharer[type=hidden]").size shouldBe 1
                doc.select("script[src='/javascripts/service/yona.issue.Sharer.js']").size shouldBe 1
            }

            it("project/create(projectform) 화면은 Tom Select 리소스를 로드하고 project-owner(user)·vcs(plain, select2-without-searchbox) select 마크업을 유지해야 한다") {
                val doc = fetchDoc("/projectform")
                assertNoSelect2(doc)
                assertTomSelectLoaded(doc)

                doc.select("select#project-owner[data-toggle=select2][data-format=user]").size shouldBe 1
                val vcsSelect = doc.select("select#vcs[data-toggle=select2]")
                vcsSelect.size shouldBe 1
                // data-dropdown-css-class 값은 여전히 "select2-without-searchbox" 그대로 남겨둔다 -
                // yobi.ui.Select2.js가 이 정확한 문자열을 보고 controlInput:null(검색 입력 자체를
                // 없앰)로 분기한다(최종 보고 "결정 필요 사항" 참고).
                vcsSelect.attr("data-dropdown-css-class") shouldBe "select2-without-searchbox"
            }

            it("board/list(posts) 화면은 Tom Select 리소스를 로드하고 issue/partial_select_label 재사용 issuelabel select 마크업을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/posts")
                assertNoSelect2(doc)
                assertTomSelectLoaded(doc)
                assertIssueLabelOptgroupMarkup(doc)
            }

            it("pullrequest/create(pull/new) 화면은 Tom Select 리소스를 로드하고 fromProjectId/fromBranch/toProjectId/toBranch(plain, format 없음) select 마크업을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/new")
                assertNoSelect2(doc)
                assertTomSelectLoaded(doc)

                doc.select("select#fromProjectId[data-toggle=select2]").size shouldBe 1
                doc.select("select#fromBranch[data-toggle=select2]").size shouldBe 1
                doc.select("select#toProjectId[data-toggle=select2]").size shouldBe 1
                doc.select("select#toBranch[data-toggle=select2]").size shouldBe 1
                // 이 4개는 data-format이 없다 - 기존에도 커스텀 포맷터/매처 없이 기본 렌더링이었다.
                doc.select("select#fromBranch[data-format]").size shouldBe 0
            }

            it("pullrequest/edit(pull/{number}/edit) 화면은 Tom Select 리소스를 로드하고 disabled 상태의 4개 select 마크업을 유지해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/pull/${openPr.number}/edit")
                assertNoSelect2(doc)
                assertTomSelectLoaded(doc)

                doc.select("select#fromProjectId[data-toggle=select2]").size shouldBe 1
                doc.select("select#fromBranch[data-toggle=select2]").size shouldBe 1
                doc.select("select#toProjectId[data-toggle=select2]").size shouldBe 1
                doc.select("select#toBranch[data-toggle=select2]").size shouldBe 1
            }

            it("site/layout 공통 헤더는 select2.css 대신 tom-select.min.css를 로드해야 한다") {
                val doc = fetchDoc("/${project.owner}/${project.name}/issues")
                doc.select("link[href*='lib/select2']").size shouldBe 0
                doc.select("link[href*='/javascripts/lib/tomselect/tom-select.min.css'][rel=stylesheet]").size shouldBe 1
            }
        }
    }
}
