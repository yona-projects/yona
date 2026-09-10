package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
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
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
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
import java.time.Instant
import java.time.temporal.ChronoUnit

// P3-46 #1: Pikaday -> Flatpickr 날짜 선택 위젯 교체.
//
// 이 스펙은 실제 달력 팝업의 동작(브라우저 JS 상호작용)은 검증하지 않는다 — MockMvc+Jsoup 하네스는
// 렌더링된 마크업과 로드되는 스크립트/CSS 경로까지만 볼 수 있다. 대신 아래 "마크업 계약"이 회귀
// 없이 유지되는지를 검증한다.
//
// 대상 6개 화면(모두 common/calendar :: calendar 프래그먼트를 include):
//   issue/create, issue/edit, issue/list, issue/view, milestone/create, milestone/edit
class CalendarWidgetTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val milestoneRepository: MilestoneRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-46 #1 날짜 선택 위젯(Pikaday -> Flatpickr) 마크업 계약 회귀 검증") {
            val owner = userRepository.findByLoginId("calwidget-owner").orElseGet {
                userRepository.save(User(loginId = "calwidget-owner", name = "달력위젯소유자", email = "calwidget-owner@yona.io"))
            }
            val member = userRepository.findByLoginId("calwidget-member").orElseGet {
                userRepository.save(User(loginId = "calwidget-member", name = "달력위젯멤버", email = "calwidget-member@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            val project = projectRepository.findAll().find { it.name == "calwidget-proj" && it.owner == "calwidget-owner" }
                ?: projectRepository.save(
                    Project(
                        name = "calwidget-proj",
                        owner = "calwidget-owner",
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

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "달력위젯 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "달력위젯 이슈",
                        body = "본문",
                        project = project,
                        number = 1L,
                        authorId = member.id,
                        authorLoginId = member.loginId,
                        dueDate = Instant.now().plus(3, ChronoUnit.DAYS)
                    )
                )

            val milestone = milestoneRepository.findAll().find { it.project.id == project.id && it.title == "달력위젯 마일스톤" }
                ?: milestoneRepository.save(
                    Milestone(
                        title = "달력위젯 마일스톤",
                        project = project,
                        state = State.OPEN,
                        dueDate = Instant.now().plus(5, ChronoUnit.DAYS)
                    )
                )

            fun assertCalendarFragmentScriptsUseFlatpickr(doc: org.jsoup.nodes.Document) {
                // moment.js는 이 작업 범위 밖(다른 화면에서도 널리 쓰임) — 그대로 유지되어야 한다.
                doc.select("script[src*=moment-with-langs]").size shouldBe 1
                // Pikaday 스크립트는 완전히 사라지고 Flatpickr로 대체되어야 한다.
                doc.select("script[src*=pikaday]").size shouldBe 0
                doc.select("script[src*='/javascripts/lib/flatpickr/']").size shouldBe 1
                // 래퍼(yobi.ui.Calendar.js)는 공개 API(getDate/setDate)를 유지한 채 그대로 로드되어야 한다.
                doc.select("script[src*='yobi.ui.Calendar.js']").size shouldBe 1
            }

            fun assertDueDateTriggerMarkup(doc: org.jsoup.nodes.Document, inputSelector: String) {
                val input = doc.select(inputSelector)
                input.size shouldBe 1
                input.attr("data-toggle") shouldBe "calendar"
                // 트리거 버튼(.btn-calendar)이 input 바로 다음 형제로 남아 있어야 한다
                // (yobi.ui.Calendar.js가 targetElement.next(".btn-calendar")로 찾는 구조).
                val next = input.first()!!.nextElementSibling()
                next?.hasClass("btn-calendar") shouldBe true
            }

            it("issue/create(issueform) 화면은 Flatpickr 리소스를 로드하고 마감일 입력/트리거 마크업을 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issueform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertCalendarFragmentScriptsUseFlatpickr(doc)
                assertDueDateTriggerMarkup(doc, "input#issueDueDate[data-toggle=calendar]")
            }

            it("issue/edit(editform) 화면은 Flatpickr 리소스를 로드하고 마감일 입력/트리거 마크업을 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}/editform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertCalendarFragmentScriptsUseFlatpickr(doc)
                assertDueDateTriggerMarkup(doc, "input#issueDueDate[data-toggle=calendar]")
            }

            it("issue/view 화면은 Flatpickr 리소스를 로드하고 마감일 입력/트리거 마크업을 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertCalendarFragmentScriptsUseFlatpickr(doc)
                // 주의(범위 밖 발견): issue/view.html의 마감일 input은 issue/create·edit·list와
                // 달리 id="issueDueDate"가 없다(name=dueDate + data-toggle=calendar만 있음).
                // yobi.issue.View.js:50의 $("#issueDueDate")는 이 화면에서 항상 빈 셀렉션이라
                // 사실상 죽은 코드다 — Pikaday->Flatpickr 교체와 무관한 기존 상태이므로 테스트도
                // 실제 마크업(name 기준)에 맞춰 검증한다.
                assertDueDateTriggerMarkup(doc, "input[name=dueDate][data-toggle=calendar]")
            }

            it("issue/list 화면은 Flatpickr 리소스를 로드하고 마감일 필터 입력/트리거 마크업을 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issues")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertCalendarFragmentScriptsUseFlatpickr(doc)
                assertDueDateTriggerMarkup(doc, "input#issueDueDate[data-toggle=calendar]")
            }

            it("milestone/create(milestone/new) 화면은 Flatpickr 리소스를 로드하고 인라인 달력 컨테이너를 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/milestone/new")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertCalendarFragmentScriptsUseFlatpickr(doc)
                doc.select("input#dueDate").size shouldBe 1
                doc.select("div#datepicker.date-picker").size shouldBe 1
            }

            it("milestone/edit(editform) 화면은 Flatpickr 리소스를 로드하고 인라인 달력 컨테이너를 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/milestone/${milestone.id}/editform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                assertCalendarFragmentScriptsUseFlatpickr(doc)
                doc.select("input#dueDate").size shouldBe 1
                doc.select("div#datepicker.date-picker").size shouldBe 1
            }

            it("site/layout 공통 헤더는 pikaday.css 대신 flatpickr.min.css를 로드해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issues")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                doc.select("link[href*=pikaday]").size shouldBe 0
                doc.select("link[href*='/javascripts/lib/flatpickr/'][rel=stylesheet]").size shouldBe 1
            }
        }
    }
}
