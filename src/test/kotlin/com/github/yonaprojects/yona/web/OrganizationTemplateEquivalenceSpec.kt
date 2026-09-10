package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectRepository
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.Locale

// 그룹12 organization/* (#193~209, TASK-0244) 동치성 검증. 별도 스펙 파일로 분리한 이유는 조직 도메인
// 픽스처(Organization/OrganizationUser/조직 소속 Project)가 TemplateEquivalenceSpec.kt의 기존 픽스처와
// 겹치지 않고 그룹 규모가 커서(17개 파일) 같은 하네스 패턴을 재사용하는 새 파일로 분리하는 편이 낫다는
// 백로그 지침(작업 원칙 2번 항목)을 따른다.
class OrganizationTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val projectRepository: ProjectRepository,
    private val postingRepository: PostingRepository,
    private val issueRepository: IssueRepository,
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

        describe("organization/* 템플릿 동치성 검증 (그룹12, TASK-0244)") {
            val orgAdminRole = roleRepository.findById(RoleType.ORG_ADMIN.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN"))
            }
            val orgMemberRole = roleRepository.findById(RoleType.ORG_MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.ORG_MEMBER.roleType, name = "ORG_MEMBER"))
            }

            val orgAdminUser = userRepository.findByLoginId("org-admin").orElseGet {
                userRepository.save(User(loginId = "org-admin", name = "조직관리자", email = "org-admin@yona.io"))
            }
            val orgMemberUser = userRepository.findByLoginId("org-member").orElseGet {
                userRepository.save(User(loginId = "org-member", name = "조직멤버", email = "org-member@yona.io"))
            }
            val guestUser = userRepository.findByLoginId("org-guest").orElseGet {
                userRepository.save(User(loginId = "org-guest", name = "게스트", email = "org-guest@yona.io"))
            }

            val org = organizationRepository.findAll().find { it.name == "acme-org" }
                ?: organizationRepository.save(Organization(name = "acme-org", descr = "테스트 조직 설명", created = Instant.now()))

            if (organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, orgAdminUser.id!!).isEmpty) {
                organizationUserRepository.save(OrganizationUser(user = orgAdminUser, organization = org, role = orgAdminRole))
            }
            if (organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, orgMemberUser.id!!).isEmpty) {
                organizationUserRepository.save(OrganizationUser(user = orgMemberUser, organization = org, role = orgMemberRole))
            }

            val orgProject = projectRepository.findAll().find { it.owner == org.name && it.name == "acme-proj" }
                ?: projectRepository.save(
                    Project(
                        name = "acme-proj",
                        owner = org.name,
                        organization = org,
                        projectScope = ProjectScope.PUBLIC,
                        vcs = "GIT",
                        createdDate = Instant.now()
                    )
                )

            val adminDetails = YonaUserDetails(
                id = orgAdminUser.id!!,
                loginId = orgAdminUser.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
            val memberDetails = YonaUserDetails(
                id = orgMemberUser.id!!,
                loginId = orgMemberUser.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
            val guestDetails = YonaUserDetails(
                id = guestUser.id!!,
                loginId = guestUser.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            describe("[Org-1] 조직 홈(organization/view.scala.html) 동치성 검증") {
                it("사이트 공용 GNB/footer/scripts 조각과 조직 header/menu 조각이 모두 렌더링되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/organizations/${org.name}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("header.gnb-outer, .gnb-wrap").isEmpty() shouldBe false
                    doc.select("footer.page-footer-outer, footer").isEmpty() shouldBe false
                    doc.select("script[src*='code.jquery.com']").size shouldBe 0
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select(".project-menu-outer").size shouldBe 1
                    doc.select(".group-title-head").text() shouldBe "group"
                }

                it("게스트(비회원)에게는 header에 가입 요청 버튼이 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/organizations/${org.name}").with(SecurityMockMvcRequestPostProcessors.user(guestDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("#enrollBtn").size shouldBe 1
                }

                it("조직 관리자/멤버에게는 header에 가입 요청 버튼이 노출되지 않아야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/organizations/${org.name}").with(SecurityMockMvcRequestPostProcessors.user(adminDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("#enrollBtn").size shouldBe 0
                }

                it("조직 관리자/멤버 목록이 역할별로 분리되어 노출되고, 관리자에게는 탈퇴 버튼이 노출되어야 한다") {
                    // P3-51: 이 테스트는 legacy와의 한국어 UI 문구 동치성 자체를 검증하는 목적이라
                    // (h3 라벨 문구 확인), Accept-Language 헤더 없는 요청이 이제 root(영어)로
                    // 결정적으로 폴백하는 것과 무관하게 한국어 로케일을 명시적으로 요청한다.
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/organizations/${org.name}")
                                .with(SecurityMockMvcRequestPostProcessors.user(adminDetails))
                                .locale(Locale.KOREAN)
                        ).andReturn().response.contentAsString
                    )
                    doc.select("h3:contains(그룹 관리자)").size shouldBe 1
                    doc.select("h3:contains(그룹 구성원)").size shouldBe 1
                    doc.select("#groupLeaveBtn").isEmpty() shouldBe false
                }

                it("소속 프로젝트 목록에 프로젝트명/설명/멤버수가 legacy와 동일한 구조로 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/organizations/${org.name}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("ul.all-projects li.project").isEmpty() shouldBe false
                    doc.select("ul.all-projects li.project .header a:contains(${orgProject.name})").size shouldBe 1
                }
            }

            describe("[Org-2] 조직 설정 서브메뉴(partial_settingmenu) + 설정/멤버/삭제 화면 동치성 검증") {
                it("설정 화면에 설정/멤버/삭제 탭과 조직 header/menu가 모두 렌더링되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/organizations/${org.name}/settingform").with(SecurityMockMvcRequestPostProcessors.user(adminDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select(".project-menu-outer").size shouldBe 1
                    doc.select("ul.nav.nav-tabs li a[href*=settingform], ul.nav.nav-tabs li a[href*='/members'], ul.nav.nav-tabs li a[href*=deleteForm]").size shouldBe 3
                    doc.select("input#project-name").attr("value") shouldBe org.name
                    doc.select(".gnb-wrap").isEmpty() shouldBe true
                }

                it("멤버 관리 화면에 조직원 목록과 역할 드롭다운, 가입 신청자 목록이 렌더링되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/organizations/${org.name}/members").with(SecurityMockMvcRequestPostProcessors.user(adminDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("ul.members li.member").size shouldBe 2
                    doc.select(".btn-group .dropdown-menu li").isEmpty() shouldBe false
                }

                it("삭제 화면에 삭제 확인 모달과 삭제 버튼이 legacy와 동일하게 렌더링되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/organizations/${org.name}/deleteForm").with(SecurityMockMvcRequestPostProcessors.user(adminDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("#btnDelete").size shouldBe 1
                    doc.select("#alertDeletion #btnDeleteExec").size shouldBe 1
                }

                it("조직 관리자가 아닌 멤버는 설정 화면에서 조직 관리 폼(#saveSetting)을 볼 수 없어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/organizations/${org.name}/settingform").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("#saveSetting").size shouldBe 0
                }
            }

            describe("[Org-3] 조직 게시판 목록(group_board_list) 동치성 검증") {
                val notice = postingRepository.findAll().find { it.project.id == orgProject.id && it.number == 900L }
                    ?: postingRepository.save(
                        Posting(
                            title = "[공지] 조직 공지",
                            body = "내용",
                            project = orgProject,
                            authorId = orgAdminUser.id!!,
                            authorLoginId = orgAdminUser.loginId,
                            authorName = orgAdminUser.name,
                            number = 900L,
                            notice = true,
                            createdDate = Instant.now()
                        )
                    )
                val post = postingRepository.findAll().find { it.project.id == orgProject.id && it.number == 901L }
                    ?: postingRepository.save(
                        Posting(
                            title = "일반 게시글",
                            body = "내용",
                            project = orgProject,
                            authorId = orgMemberUser.id!!,
                            authorLoginId = orgMemberUser.loginId,
                            authorName = orgMemberUser.name,
                            number = 901L,
                            createdDate = Instant.now()
                        )
                    )

                it("공지글은 notice-wrap에, 일반글은 별도 목록에 legacy와 동일하게 분리 렌더링되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/org/${org.name}/boards").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("ul.notice-wrap li.post-item .title:contains(${notice.title})").size shouldBe 1
                    doc.select("ul.post-list-wrap:not(.notice-wrap) li.post-item .title:contains(${post.title})").size shouldBe 1
                    doc.select(".project-selects select#projects").size shouldBe 1
                }
            }

            describe("[Org-4] 조직 이슈 목록(group_issue_list/group_issue_search_partial) 동치성 검증") {
                val openIssue = issueRepository.findAll().find { it.project.id == orgProject.id && it.number == 950L }
                    ?: issueRepository.save(
                        Issue(
                            title = "열린 이슈",
                            project = orgProject,
                            authorId = orgAdminUser.id!!,
                            authorLoginId = orgAdminUser.loginId,
                            authorName = orgAdminUser.name,
                            number = 950L,
                            state = State.OPEN,
                            createdDate = Instant.now()
                        )
                    )
                val closedIssue = issueRepository.findAll().find { it.project.id == orgProject.id && it.number == 951L }
                    ?: issueRepository.save(
                        Issue(
                            title = "닫힌 이슈",
                            project = orgProject,
                            authorId = orgMemberUser.id!!,
                            authorLoginId = orgMemberUser.loginId,
                            authorName = orgMemberUser.name,
                            number = 951L,
                            state = State.CLOSED,
                            createdDate = Instant.now()
                        )
                    )

                it("열림 탭 조회 시 열린 이슈만 노출되고 상태탭 배지 카운트가 정확해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/org/${org.name}/issues").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("li.post-item .title:contains(${openIssue.title})").size shouldBe 1
                    doc.select("li.post-item .title:contains(${closedIssue.title})").size shouldBe 0
                    doc.select(".nav-tabs.nm li.active a span.num-badge").text() shouldBe "1"
                }

                it("닫힘 탭(state=closed) 조회 시 닫힌 이슈만 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/org/${org.name}/issues?state=closed").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("li.post-item .title:contains(${closedIssue.title})").size shouldBe 1
                    doc.select("li.post-item .title:contains(${openIssue.title})").size shouldBe 0
                }

                it("퀵서치(전체/할당된/작성한/멘션된)가 legacy와 동일하게 4개 항목으로 렌더링되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/org/${org.name}/issues").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("ul.lst-stacked.unstyled li").size shouldBe 4
                }

                it("작성자(authorId) 필터를 적용하면 해당 작성자의 이슈만 남아야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/org/${org.name}/issues?authorId=${orgAdminUser.id}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    doc.select("li.post-item .title:contains(${openIssue.title})").size shouldBe 1
                }
            }

            describe("[Org-5] 조직 풀 리퀘스트 목록(group_pullrequest_list) 동치성 검증") {
                val openPr = pullRequestRepository.findAll().find { it.toProject.id == orgProject.id && it.number == 970L }
                    ?: pullRequestRepository.save(
                        PullRequest(
                            title = "열린 PR",
                            toProject = orgProject,
                            fromProject = orgProject,
                            toBranch = "master",
                            fromBranch = "feature",
                            contributor = orgMemberUser,
                            number = 970L,
                            state = State.OPEN,
                            created = Instant.now()
                        )
                    )
                val closedPr = pullRequestRepository.findAll().find { it.toProject.id == orgProject.id && it.number == 971L }
                    ?: pullRequestRepository.save(
                        PullRequest(
                            title = "닫힌 PR",
                            toProject = orgProject,
                            fromProject = orgProject,
                            toBranch = "master",
                            fromBranch = "feature2",
                            contributor = orgMemberUser,
                            number = 971L,
                            state = State.CLOSED,
                            created = Instant.now()
                        )
                    )

                it("열림 탭에는 열린 PR만, 상태 탭 배지에는 열림/닫힘 카운트가 각각 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/org/${org.name}/pullrequests").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("li.post-item .title:contains(${openPr.title})").size shouldBe 1
                    doc.select("li.post-item .title:contains(${closedPr.title})").size shouldBe 0
                    doc.select("ul.pullrequeset-tab-menu li").size shouldBe 2
                }

                it("닫힘 탭(closedPullrequests)에는 닫힌 PR만 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/org/${org.name}/closedPullrequests").with(SecurityMockMvcRequestPostProcessors.user(memberDetails)))
                            .andReturn().response.contentAsString
                    )
                    doc.select("li.post-item .title:contains(${closedPr.title})").size shouldBe 1
                    doc.select("li.post-item .title:contains(${openPr.title})").size shouldBe 0
                }
            }

            describe("[Org-6] 조직 탈퇴(leave) 백엔드 검증 (yona OrganizationApp.leave() 대응)") {
                it("일반 멤버는 조직 관리자가 1명 이상 남아있으면 정상적으로 탈퇴할 수 있어야 한다") {
                    // acme-org(공용 픽스처 org)는 관리자가 orgAdminUser 1명뿐이라 이 시나리오(관리자
                    // 1명 이상 남아있는 상태에서 탈퇴)를 검증할 수 없다 — 관리자 2명짜리 전용 org 사용.
                    val twoAdminOrg = organizationRepository.save(Organization(name = "two-admin-org-${System.nanoTime()}", created = Instant.now()))
                    val firstAdmin = userRepository.save(User(loginId = "first-admin-${System.nanoTime()}", name = "관리자1", email = "first-admin-${System.nanoTime()}@yona.io"))
                    organizationUserRepository.save(OrganizationUser(user = firstAdmin, organization = twoAdminOrg, role = orgAdminRole))
                    val secondAdmin = userRepository.save(User(loginId = "second-admin-${System.nanoTime()}", name = "관리자2", email = "second-admin-${System.nanoTime()}@yona.io"))
                    organizationUserRepository.save(OrganizationUser(user = secondAdmin, organization = twoAdminOrg, role = orgAdminRole))

                    val leaver = userRepository.save(User(loginId = "leave-member-${System.nanoTime()}", name = "탈퇴할멤버", email = "leave-member-${System.nanoTime()}@yona.io"))
                    organizationUserRepository.save(OrganizationUser(user = leaver, organization = twoAdminOrg, role = orgMemberRole))
                    val leaverDetails = YonaUserDetails(
                        id = leaver.id!!,
                        loginId = leaver.loginId,
                        passwordVal = "hashed",
                        passwordSalt = "salt",
                        authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                    )

                    val result = mockMvc.perform(
                        delete("/organizations/${twoAdminOrg.name}/leave")
                            .with(SecurityMockMvcRequestPostProcessors.user(leaverDetails))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                    ).andReturn()

                    result.response.status shouldBe 200
                    organizationUserRepository.findByOrganizationIdAndUserId(twoAdminOrg.id!!, leaver.id!!).isPresent shouldBe false
                }

                it("조직 관리자가 단 1명일 때 일반 멤버는 탈퇴가 거부되어야 한다(legacy validateForLeave 버그 재현)") {
                    val soleAdminOrg = organizationRepository.save(Organization(name = "sole-admin-org-${System.nanoTime()}", created = Instant.now()))
                    val soleAdmin = userRepository.save(User(loginId = "sole-admin-${System.nanoTime()}", name = "단독관리자", email = "sole-admin-${System.nanoTime()}@yona.io"))
                    val plainMember = userRepository.save(User(loginId = "plain-member-${System.nanoTime()}", name = "일반멤버", email = "plain-member-${System.nanoTime()}@yona.io"))
                    organizationUserRepository.save(OrganizationUser(user = soleAdmin, organization = soleAdminOrg, role = orgAdminRole))
                    organizationUserRepository.save(OrganizationUser(user = plainMember, organization = soleAdminOrg, role = orgMemberRole))
                    val plainMemberDetails = YonaUserDetails(
                        id = plainMember.id!!,
                        loginId = plainMember.loginId,
                        passwordVal = "hashed",
                        passwordSalt = "salt",
                        authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                    )

                    val result = mockMvc.perform(
                        delete("/organizations/${soleAdminOrg.name}/leave")
                            .with(SecurityMockMvcRequestPostProcessors.user(plainMemberDetails))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                    ).andReturn()

                    result.response.status shouldBe 403
                    organizationUserRepository.findByOrganizationIdAndUserId(soleAdminOrg.id!!, plainMember.id!!).isPresent shouldBe true
                }
            }
        }
    }
}
