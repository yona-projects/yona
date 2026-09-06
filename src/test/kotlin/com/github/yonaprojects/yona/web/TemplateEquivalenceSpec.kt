package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.AssigneeRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.support.YonaUpdateService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.context.TestPropertySource
import org.springframework.web.context.WebApplicationContext
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.user.UserSetting
import com.github.yonaprojects.yona.domain.user.UserSettingRepository
import com.github.yonaprojects.yona.domain.user.UserVerification
import com.github.yonaprojects.yona.domain.user.UserVerificationRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.user.FavoriteProject
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.SimpleCommentThread
import com.github.yonaprojects.yona.domain.user.UserIdent
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.context.Context as ThymeleafContext
import java.util.Locale

@TestPropertySource(properties = ["github.allow.migration=true"])
class TemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val postingRepository: PostingRepository,
    private val issueRepository: IssueRepository,
    private val assigneeRepository: AssigneeRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val yonaUpdateService: YonaUpdateService,
    private val recentIssueService: RecentIssueService,
    private val userSettingRepository: UserSettingRepository,
    private val userVerificationRepository: UserVerificationRepository,
    private val milestoneRepository: MilestoneRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val templateEngine: SpringTemplateEngine
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("Thymeleaf 템플릿 동치성 회귀 검증") {
            // Given: 테스트용 기본 데이터 준비
            val owner = userRepository.findByLoginId("owner").orElseGet {
                userRepository.save(User(loginId = "owner", name = "소유자", email = "owner@yona.io"))
            }
            val member = userRepository.findByLoginId("member").orElseGet {
                userRepository.save(User(loginId = "member", name = "멤버", email = "member@yona.io"))
            }
            val nonmember = userRepository.findByLoginId("nonmember").orElseGet {
                userRepository.save(User(loginId = "nonmember", name = "외부인", email = "nonmember@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            // 프로젝트 1: 일반 공개 프로젝트 (코드 비공개 해제)
            val publicProj = projectRepository.findAll().find { it.name == "public-proj" } ?: projectRepository.save(
                Project(
                    name = "public-proj",
                    owner = "owner",
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = false,
                    vcs = "GIT"
                )
            )
            val category = issueLabelCategoryRepository.findAll().find { it.project.id == publicProj.id }
                ?: issueLabelCategoryRepository.save(
                    IssueLabelCategory(
                        name = "테스트카테고리",
                        project = publicProj
                    )
                )

            val label = issueLabelRepository.findAll().find { it.category.id == category.id }
                ?: issueLabelRepository.save(
                    IssueLabel(
                        name = "테스트라벨",
                        color = "#FF0000",
                        category = category,
                        project = publicProj
                    )
                )

            // 프로젝트 2: 공개 프로젝트이지만 코드는 멤버전용인 프로젝트
            val codeMemberOnlyProj = projectRepository.findAll().find { it.name == "memberonly-proj" } ?: projectRepository.save(
                Project(
                    name = "memberonly-proj",
                    owner = "owner",
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = true,
                    vcs = "GIT"
                )
            )

            // 멤버 관계 형성
            if (!projectUserRepository.existsByProjectIdAndUserId(codeMemberOnlyProj.id!!, member.id!!)) {
                projectUserRepository.save(
                    ProjectUser(
                        project = codeMemberOnlyProj,
                        user = member,
                        role = roleMember
                    )
                )
            }

            val memberDetails = YonaUserDetails(
                id = member.id!!,
                loginId = member.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val nonMemberDetails = YonaUserDetails(
                id = nonmember.id!!,
                loginId = nonmember.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val siteAdmin = userRepository.findByLoginId("siteadmin").orElseGet {
                userRepository.save(User(loginId = "siteadmin", name = "관리자", email = "siteadmin@yona.io", state = UserState.SITE_ADMIN))
            }.let {
                if (it.state != UserState.SITE_ADMIN) {
                    it.state = UserState.SITE_ADMIN
                    userRepository.save(it)
                } else it
            }

            val siteAdminDetails = YonaUserDetails(
                id = siteAdmin.id!!,
                loginId = siteAdmin.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE", "ROLE_SITE_ADMIN")
            )

            fun setUpdateState(updateRequired: Boolean, latestVersion: String?, watched: Boolean = true) {
                val requiredField = yonaUpdateService.javaClass.getDeclaredField("isUpdateRequired")
                requiredField.isAccessible = true
                requiredField.setBoolean(yonaUpdateService, updateRequired)

                val versionField = yonaUpdateService.javaClass.getDeclaredField("latestVersion")
                versionField.isAccessible = true
                versionField.set(yonaUpdateService, latestVersion)

                yonaUpdateService.isWatched = watched
            }

            describe("[Test-19-1] GNB 프로젝트 메뉴(projectMenu) 탭 동치성 렌더링 검증") {
                it("공개 프로젝트에 비로그인 접근 시, CODE/PULL_REQUEST/REVIEW 탭이 정상적으로 렌더링되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    // GNB 탭 체크
                    val menu = doc.select(".project-menu-nav")
                    menu.size shouldNotBe 0
                    menu.select("a[href*='/code']").size shouldNotBe 0
                    menu.select("a[href*='/pulls']").size shouldNotBe 0
                    menu.select("a[href*='/reviews']").size shouldNotBe 0
                }

                it("코드 멤버전용 프로젝트에 비로그인 접근 시, CODE/PULL_REQUEST/REVIEW 탭이 렌더링에서 제외되어야 한다") {
                    val result = mockMvc.perform(get("/owner/memberonly-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    val menu = doc.select(".project-menu-nav")
                    menu.select("a[href*='/code']").size shouldBe 0
                    menu.select("a[href*='/pulls']").size shouldBe 0
                    menu.select("a[href*='/reviews']").size shouldBe 0
                }

                it("코드 멤버전용 프로젝트에 비멤버로 로그인 접근 시, CODE/PULL_REQUEST/REVIEW 탭이 숨겨져야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/memberonly-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(nonMemberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    val menu = doc.select(".project-menu-nav")
                    menu.select("a[href*='/code']").size shouldBe 0
                    menu.select("a[href*='/pulls']").size shouldBe 0
                    menu.select("a[href*='/reviews']").size shouldBe 0
                }

                it("코드 멤버전용 프로젝트에 프로젝트 멤버로 로그인 접근 시, CODE/PULL_REQUEST/REVIEW 탭이 정상 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/memberonly-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    val menu = doc.select(".project-menu-nav")
                    menu.select("a[href*='/code']").size shouldNotBe 0
                    menu.select("a[href*='/pulls']").size shouldNotBe 0
                    menu.select("a[href*='/reviews']").size shouldNotBe 0
                }
            }

            describe("[Test-19-2] 게시판 상세 뷰(board/view.html) 렌더링 동치성 검증") {
                val post = postingRepository.findAll().find { it.project.id == publicProj.id } ?: postingRepository.save(
                    Posting(
                        title = "테스트포스트",
                        body = "포스트내용",
                        project = publicProj,
                        authorId = owner.id!!,
                        authorLoginId = owner.loginId,
                        authorName = owner.name,
                        number = 1L
                    )
                )

                it("로그인 사용자가 게시판 상세 조회 시, 댓글 입력 폼 및 파일 업로더 드롭존 마크업이 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj/post/${post.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    // 댓글 작성 폼과 업로드 드롭존 검증
                    doc.select("#comment-form").size shouldBe 1
                    doc.select(".upload-wrap[data-resource-type]").size shouldBe 1
                    doc.select("input[name='filePath']").size shouldBe 1
                }

                it("비로그인 사용자가 상세 조회 시, 비활성화된 알림 메시지와 함께 댓글 등록 창이 제한되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj/post/${post.number}"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    // 폼 자체는 배제되고 비로그인 알림 상자가 노출되는지 체크
                    doc.select("#comment-form").size shouldBe 0
                    val disabledBox = doc.select(".write-comment-box[data-login='required']")
                    disabledBox.size shouldBe 1
                    disabledBox.select("textarea.comment.disabled").size shouldBe 1
                }
            }

            describe("[Test-19-3] 이슈 상세 뷰(issue/view.html) 렌더링 동치성 검증") {
                val issue = issueRepository.findAll().find { it.project.id == publicProj.id } ?: issueRepository.save(
                    Issue(
                        title = "테스트이슈",
                        body = "이슈내용",
                        project = publicProj,
                        authorId = owner.id!!,
                        authorLoginId = owner.loginId,
                        authorName = owner.name,
                        number = 1L
                    )
                )

                it("로그인 사용자가 이슈 상세 조회 시, 댓글 입력 폼 및 파일 업로더 드롭존 마크업이 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("#comment-form").size shouldBe 1
                    doc.select(".upload-wrap[data-resource-type]").size shouldBe 1
                    doc.select("input[name='filePath']").size shouldBe 1
                }

                it("비로그인 사용자가 이슈 조회 시, 댓글 폼이 차단되고 비로그인 대체 마크업이 표시되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj/issue/${issue.number}"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("#comment-form").size shouldBe 0
                    val disabledBox = doc.select(".write-comment-box[data-login='required']")
                    disabledBox.size shouldBe 1
                    disabledBox.select("textarea.comment.disabled").size shouldBe 1
                }
            }

            describe("[Test-19-4] 게시판 목록 뷰(board/list.html) 렌더링 동치성 검증") {
                it("게시판 목록 조회 시 라벨 드롭다운 필터 및 단축키 도움말 조각이 렌더링되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj/posts")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    // 1. 라벨 드롭다운 필터 존재 여부 검증
                    doc.select(".board-labels").size shouldNotBe 0
                    doc.select(".board-labels select").size shouldNotBe 0

                    // 2. 단축키 도움말 조각 존재 여부 검증
                    doc.select("#helpKeys").size shouldNotBe 0
                    doc.select(".board-footer").size shouldNotBe 0
                }
            }

            describe("[Test-19-5] 레이아웃 공통 조각(site/layout.html) 동치성 검증") {
                it("사이트 관리자에게 업데이트가 존재하고 알림을 끄지 않았다면, 업데이트 알림 배너가 노출되어야 한다") {
                    setUpdateState(updateRequired = true, latestVersion = "9.9.9", watched = true)

                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val notice = doc.select("p.center-txt a[href*='github.com/yona-projects/yona/releases']")
                    notice.size shouldBe 1
                    notice.text() shouldNotBe ""

                    val hideBtn = doc.select("p.center-txt button[data-request-method='post'][data-request-uri='/sites/unwatchUpdate']")
                    hideBtn.size shouldBe 1
                }

                it("업데이트가 필요하지 않으면 사이트 관리자에게도 업데이트 알림 배너가 노출되지 않아야 한다") {
                    setUpdateState(updateRequired = false, latestVersion = null, watched = true)

                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("button[data-request-uri='/sites/unwatchUpdate']").size shouldBe 0
                }

                it("업데이트가 존재해도 사이트 관리자가 아닌 사용자에게는 업데이트 알림 배너가 노출되지 않아야 한다") {
                    setUpdateState(updateRequired = true, latestVersion = "9.9.9", watched = true)

                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("button[data-request-uri='/sites/unwatchUpdate']").size shouldBe 0

                    setUpdateState(updateRequired = false, latestVersion = null, watched = true)
                }

                it("head 조각에 og/twitter 메타 태그가 렌더링되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("meta[property='og:title']").size shouldBe 1
                    doc.select("meta[property='og:url']").size shouldBe 1
                    doc.select("meta[property='og:type']").attr("content") shouldBe "website"
                    doc.select("meta[name='twitter:card']").attr("content") shouldBe "summary"
                    doc.select("meta[name='twitter:title']").size shouldBe 1
                    doc.select("meta[name='twitter:url']").size shouldBe 1
                }

                it("공통 스크립트 조각에 NProgress 라이브러리 로드/초기화 및 ViewerJS 자산이 포함되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("link[href*='lib/nprogress/nprogress.css']").size shouldBe 1
                    doc.select("script[src*='lib/nprogress/nprogress.js']").size shouldBe 1
                    html.contains("NProgress.configure(") shouldBe true

                    doc.select("link[href*='lib/viewerjs/viewer.css']").size shouldBe 1
                    doc.select("script[src*='lib/viewerjs/viewer.js']").size shouldBe 1
                    doc.select("script[src*='lib/viewerjs/jquery-viewer.js']").size shouldBe 1
                    html.contains(".markdown-wrap").shouldBe(true)
                }

                it("sendYonaUsage 설정 기본값이 꺼져 있으면 메인 레이아웃에도 구글 애널리틱스 스크립트가 렌더링되지 않아야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    result.response.contentAsString.contains("google-analytics.com/analytics.js") shouldBe false
                }
            }

            describe("[Test-19-6] framed 레이아웃(site/layout_framed.html) 동치성 검증") {
                it("사이드바 프레임 페이지에 og/twitter 메타 태그와 nprogress/magnific-popup 자산이 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/user/sidebar")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("meta[property='og:title']").size shouldBe 1
                    doc.select("meta[property='og:url']").size shouldBe 1
                    doc.select("meta[name='twitter:card']").attr("content") shouldBe "summary"
                    doc.select("link[href*='lib/nprogress/nprogress.css']").size shouldBe 1
                    doc.select("link[href*='lib/magnific-popup/magnific-popup.css']").size shouldBe 1
                }

                it("사이드바 프레임 body 클래스는 theme-default와 framed-body를 모두 가져야 한다") {
                    val result = mockMvc.perform(
                        get("/user/sidebar")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val bodyClass = doc.select("body#html-body").attr("class")
                    bodyClass.contains("theme-default") shouldBe true
                    bodyClass.contains("framed-body") shouldBe true
                }

                it("프로젝트/조직 목록의 popover 마크업이 실제로 초기화되는 스크립트가 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/user/sidebar")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    html.contains("[data-toggle=\"popover\"]").shouldBe(true)
                    html.contains(".popover()").shouldBe(true)
                }

                it("sendYonaUsage 설정 기본값이 꺼져 있으면 구글 애널리틱스 스크립트가 렌더링되지 않아야 한다") {
                    val result = mockMvc.perform(
                        get("/user/sidebar")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    html.contains("google-analytics.com/analytics.js") shouldBe false
                }
            }

            describe("[Test-19-7] 사이트 관리자 레이아웃(siteLayout.scala.html 대응) 동치성 검증") {
                it("사이트 관리자 화면은 legacy siteLayout처럼 공통 footer를 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/sites/userList")
                            .with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("footer.page-footer-outer").size shouldBe 1
                }
            }

            describe("[Test-19-8] GNB navbar/usermenu(common/navbar.scala.html, common/usermenu.scala.html) 동치성 검증") {
                val guest = userRepository.findByLoginId("guestuser").orElseGet {
                    userRepository.save(User(loginId = "guestuser", name = "게스트", email = "guestuser@yona.io", isGuest = true))
                }
                val guestDetails = YonaUserDetails(
                    id = guest.id!!,
                    loginId = guest.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                it("게스트 사용자에게는 전체 목록 링크와 새 그룹 만들기 링크가 숨겨져야 한다") {
                    val result = mockMvc.perform(
                        get("/notifications")
                            .with(SecurityMockMvcRequestPostProcessors.user(guestDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".gnb-nav a[href='/projects']").size shouldBe 0
                    doc.select(".gnb-usermenu a[href='/organizations/new']").size shouldBe 0
                }

                it("일반 회원에게는 전체 목록 링크와 새 그룹 만들기 링크가 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/notifications")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".gnb-nav a[href='/projects']").size shouldBe 1
                    doc.select(".gnb-usermenu a[href='/organizations/new']").size shouldBe 1
                }

                it("배정된 미해결 이슈가 있으면 내 이슈 링크 옆에 카운터 배지가 노출되어야 한다") {
                    val myIssue = issueRepository.findAll().find { it.title == "내배지테스트이슈" } ?: issueRepository.save(
                        Issue(
                            title = "내배지테스트이슈",
                            body = "내용",
                            project = publicProj,
                            authorId = owner.id!!,
                            authorLoginId = owner.loginId,
                            authorName = owner.name,
                            number = 900L
                        )
                    )
                    if (myIssue.assignee == null) {
                        val assignee = assigneeRepository.save(Assignee(user = member, project = publicProj))
                        myIssue.assignee = assignee
                        issueRepository.save(myIssue)
                    }

                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val myIssueLink = doc.select(".gnb-usermenu a[href='/user/issues']")
                    myIssueLink.size shouldBe 1
                    myIssueLink.select(".counter-badge").size shouldNotBe 0
                }
            }

            describe("[Test-19-9] 공용 스크립트 조각(common/scripts.scala.html) 동치성 검증") {
                it("토스트 알림 템플릿, U 단축키, pageshow NProgress 해제, iframe 히스토리 동기화 스크립트가 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("script#tplYobiToast").size shouldBe 1

                    html.contains("\"U\":") shouldBe true
                    html.contains("user\\/${member.loginId}") shouldBe true

                    html.contains("pageshow") shouldBe true
                    html.contains("NProgress.done()") shouldBe true

                    html.contains(".head-anchor") shouldBe true
                    html.contains(".share-link") shouldBe true
                    html.contains("window.parent.history.pushState") shouldBe true
                }

                it("비로그인 사용자에게 렌더링되는 로그인 모달이 jquery-ui 스크립트를 로드해야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("script[src*='lib/jquery/jquery-ui-1.10.4.custom.min.js']").size shouldBe 1
                }
            }

            describe("[Test-19-10] 사용자 메뉴 탭 콘텐츠(common/usermenu_tab_content_list.scala.html) 동치성 검증") {
                it("최근 방문한 이슈가 있으면 최근 읽은 이슈 탭 패널에 목록이 렌더링되어야 한다") {
                    val recentIssue = issueRepository.findAll().find { it.title == "최근방문이슈테스트" } ?: issueRepository.save(
                        Issue(
                            title = "최근방문이슈테스트",
                            body = "내용",
                            project = publicProj,
                            authorId = owner.id!!,
                            authorLoginId = owner.loginId,
                            authorName = owner.name,
                            number = 901L
                        )
                    )
                    recentIssueService.recordIssueVisit(member, recentIssue)

                    val result = mockMvc.perform(
                        get("/user/usermenuTabContentList")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val pane = doc.select("#myRecentIssueList")
                    pane.size shouldNotBe 0
                    pane.select("li.user-li").size shouldNotBe 0
                    pane.select("li.user-li").text().contains("최근방문이슈테스트") shouldBe true
                }

                it("GNB 사이드바 탭 메뉴에 최근 방문 이슈 탭 버튼이 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("#mySidenav a[href='#myRecentIssueList']").size shouldNotBe 0
                }
            }

            describe("[Test-19-11] 개인 3탭 메뉴(common/mySeriesMenuTab.scala.html) 동치성 검증") {
                it("현재 페이지가 로그인 기본 페이지로 이미 지정돼 있으면 '기본 페이지로 설정' 버튼이 숨겨져야 한다") {
                    userSettingRepository.findByUserId(member.id!!).ifPresentOrElse(
                        { it.loginDefaultPage = "notifications"; userSettingRepository.save(it) },
                        { userSettingRepository.save(UserSetting(user = member, loginDefaultPage = "notifications")) }
                    )

                    val result = mockMvc.perform(
                        get("/notifications")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("#setDefaultLoginPage").size shouldBe 0
                }

                it("현재 페이지가 로그인 기본 페이지가 아니면 '기본 페이지로 설정' 버튼이 노출되어야 한다") {
                    userSettingRepository.findByUserId(member.id!!).ifPresentOrElse(
                        { it.loginDefaultPage = "/somewhere-else"; userSettingRepository.save(it) },
                        { userSettingRepository.save(UserSetting(user = member, loginDefaultPage = "/somewhere-else")) }
                    )

                    val result = mockMvc.perform(
                        get("/notifications")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("#setDefaultLoginPage").size shouldBe 1
                }

                it("내 파일 목록 페이지도 공용 3탭 메뉴(알림/내 이슈/내 파일)를 공유해야 한다") {
                    val result = mockMvc.perform(
                        get("/user/files")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val tabs = doc.select("ul.nav-tabs")
                    tabs.select("a[href='/notifications']").size shouldBe 1
                    tabs.select("a[href='/user/issues']").size shouldBe 1
                    tabs.select("a[href='/user/files']").size shouldBe 1
                    tabs.select("li.active a[href='/user/files']").size shouldBe 1
                }
            }

            describe("[Test-19-12] 태스크리스트 진행률 바(common/tasklistBar.scala.html) 동치성 검증") {
                it("이슈 상세 페이지의 본문 영역에 태스크리스트 진행률 바 셸과 yona.Tasklist.js 로드가 있어야 한다") {
                    val issue = issueRepository.findAll().find { it.project.id == publicProj.id && it.title == "테스트이슈" }!!

                    val result = mockMvc.perform(
                        get("/owner/public-proj/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    val bar = doc.select("#issue-body-${issue.number} > .tasklist")
                    bar.size shouldBe 1
                    bar.select(".task-title .done-counter").size shouldBe 1
                    bar.select(".task-progress .bar.red").size shouldBe 1
                    doc.select("script[src*='common/yona.Tasklist.js']").size shouldBe 1
                }

                it("게시판 상세 페이지의 본문 영역에도 태스크리스트 진행률 바 셸과 스크립트 로드가 있어야 한다") {
                    val post = postingRepository.findAll().find { it.project.id == publicProj.id }!!

                    val result = mockMvc.perform(
                        get("/owner/public-proj/post/${post.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)

                    val bar = doc.select("#post-body-${post.number} > .tasklist")
                    bar.size shouldBe 1
                    doc.select("script[src*='common/yona.Tasklist.js']").size shouldBe 1
                }
            }

            describe("[Test-19-14] 에러 페이지(error/*_default.scala.html) 동치성 검증") {
                it("존재하지 않는 프로젝트 접근 시 404 뷰는 legacy notfound_default 전용 D2 Program footer를 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/__no-such-project-xyz__")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("footer.page-footer-outer .d2-program").size shouldBe 1
                }

                it("비공개 프로젝트에 비로그인 접근 시 403 뷰는 siteLayout처럼 검색폼 있는 전체 GNB와 사이트 공용 footer를 가져야 한다") {
                    val privateProj = projectRepository.findAll().find { it.name == "private-proj-403test" } ?: projectRepository.save(
                        Project(
                            name = "private-proj-403test",
                            owner = "owner",
                            projectScope = ProjectScope.PRIVATE,
                            isCodeAccessibleMemberOnly = true
                        )
                    )

                    val result = mockMvc.perform(get("/owner/${privateProj.name}")).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                }

                it("400/500 에러 뷰 템플릿 파일이 전체 GNB(gnb 조각)와 사이트 공용 footer 조각을 참조해야 한다") {
                    for (viewName in listOf("400", "500")) {
                        val content = java.io.File(
                            "src/main/resources/templates/error/$viewName.html"
                        ).readText()
                        content.contains("site/layout :: gnb") shouldBe true
                        content.contains("site/layout :: footer") shouldBe true
                        content.contains("site/layout :: errorGnb") shouldBe false
                    }
                }
            }

            describe("[Test-19-15] 로그인 홈(index.scala.html → index.html)의 3탭 메뉴 중복 이식 검증") {
                it("루트 경로(/)는 공용 mySeriesMenuTab 조각을 공유하고, legacy처럼 루트 경로에서는 기본 페이지 설정 버튼이 숨겨져야 한다") {
                    // IndexController.index()는 loginDefaultPage가 설정돼 있으면 그 경로로 리다이렉트하므로,
                    // index.html 본문을 직접 검증하려면 loginDefaultPage가 비어 있어야 한다.
                    userSettingRepository.findByUserId(member.id!!).ifPresent {
                        it.loginDefaultPage = null
                        userSettingRepository.save(it)
                    }

                    val result = mockMvc.perform(
                        get("/").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val tabs = doc.select("ul.nav-tabs")
                    tabs.select("a[href='/notifications']").size shouldBe 1
                    tabs.select("a[href='/user/issues']").size shouldBe 1
                    tabs.select("a[href='/user/files']").size shouldBe 1
                    tabs.select("li.active a[href='/notifications']").size shouldBe 1
                    // legacy mySeriesMenuTab의 !path.equals("/") 조건과 동일 — 루트 경로에서는 항상 숨김
                    doc.select("#setDefaultLoginPage").size shouldBe 0
                }
            }

            describe("[Test-19-13] 이슈 라벨 동적 CSS(common/issueLabelColor.scala.html, LabelStyleController) 링크 이식 검증") {
                it("이슈 상세/게시판 상세/게시판 목록 페이지가 프로젝트별 labels.css를 링크해야 한다") {
                    val issue = issueRepository.findAll().find { it.project.id == publicProj.id && it.title == "테스트이슈" }!!
                    val post = postingRepository.findAll().find { it.project.id == publicProj.id }!!

                    val issueViewDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/issue/${issue.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    issueViewDoc.select("link[href*='/owner/public-proj/issue/labels.css']").size shouldBe 1

                    val boardViewDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/post/${post.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    boardViewDoc.select("link[href*='/owner/public-proj/issue/labels.css']").size shouldBe 1

                    val boardListDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/posts").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    boardListDoc.select("link[href*='/owner/public-proj/issue/labels.css']").size shouldBe 1

                    // legacy project/partial_dashboard_issuesbylabel.scala.html:26도 동일한 링크를 갖고 있고,
                    // yona는 이 파샬을 project/home.html의 대시보드 탭에 인라인했으므로(#95, TASK-0237) 거기도
                    // 같이 링크돼야 한다 — 이전엔 빠져 있었음(#37 재검토로 발견).
                    val projectHomeDashboardDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj").param("tabId", "dashboard")
                                .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    projectHomeDashboardDoc.select("link[href*='/owner/public-proj/issue/labels.css']").size shouldBe 1
                }
            }

            describe("[Test-19-16] 회원가입 이메일 인증 완료 화면(user/verified.scala.html) 동치성 검증") {
                it("유효한 인증 코드로 접근 시 siteLayout 기반(전체 GNB+footer) 인증 완료 화면이 렌더링되어야 한다") {
                    val verifyUser = userRepository.findByLoginId("verify-target-user").orElseGet {
                        userRepository.save(User(loginId = "verify-target-user", name = "인증대상", email = "verify-target@yona.io"))
                    }
                    userVerificationRepository.findByLoginIdAndVerificationCode(verifyUser.loginId, "test-code-123")
                        ?: userVerificationRepository.save(
                            UserVerification(
                                user = verifyUser,
                                loginId = verifyUser.loginId,
                                verificationCode = "test-code-123",
                                timestamp = System.currentTimeMillis()
                            )
                        )

                    val result = mockMvc.perform(
                        get("/user/verify").param("loginId", verifyUser.loginId).param("code", "test-code-123")
                    ).andExpect(status().isOk).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.text().contains(verifyUser.loginId) shouldBe true
                }
            }

            describe("[Test-19-17] 로그인 화면(user/login.scala.html) 동치성 검증") {
                it("로그인 화면에 이메일 인증 안내 문구가 노출되어야 한다") {
                    val result = mockMvc.perform(get("/users/loginform")).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".email-verification-help").size shouldBe 1
                }

                // yona UserApp.useSocialLoginOnly(application.use.social.login.only) 대응 (P-템플릿 #15).
                // 기본값(false)일 때는 legacy와 동일하게 아이디/비밀번호 폼과 로그인유지/비밀번호찾기가
                // 그대로 노출돼야 한다(true일 때 숨김 처리는 별도 스프링 컨텍스트 프로퍼티 오버라이드가
                // 필요해 이 통합 스펙에서는 검증하지 않음 — th:if/th:unless 자체는 다른 곳에서도 이미
                // 검증된 안전한 패턴이라 낮은 리스크로 판단).
                it("useSocialLoginOnly 기본값(false)일 때 아이디/비밀번호 로그인 폼과 로그인유지 체크박스가 노출돼야 한다") {
                    val result = mockMvc.perform(get("/users/loginform")).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".login-form-wrap input#password").size shouldBe 1
                    doc.select(".login-form-wrap #remember-me").size shouldBe 1
                    doc.text().contains("소셜 로그인을 통한 로그인만 가능합니다") shouldBe false
                }
            }

            describe("[Test-19-18] 회원가입 화면(user/signup.scala.html) 동치성 검증") {
                it("회원가입 화면은 site/layout 기반 전체 GNB와 footer, 실시간 검증 스크립트를 포함해야 한다") {
                    val result = mockMvc.perform(get("/signup")).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)

                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select("script[src*='lib/validate.js']").size shouldBe 1
                    doc.select("script[src*='service/yobi.user.SignUp.js']").size shouldBe 1

                    val html = result.response.contentAsString
                    html.contains("sLogindIdCheckUrl") shouldBe true
                    html.contains("sEmailCheckUrl") shouldBe true
                }

                it("아이디 중복 확인 엔드포인트(GET /user/isUsed)가 legacy와 동일한 JSON 형식으로 응답해야 한다") {
                    val result = mockMvc.perform(get("/user/isUsed").param("name", member.loginId))
                        .andExpect(status().isOk)
                        .andReturn()

                    val json = tools.jackson.databind.ObjectMapper().readTree(result.response.contentAsString)
                    json.get("isExist").asBoolean() shouldBe true
                    json.has("isReserved") shouldBe true
                }

                it("이메일 중복 확인 엔드포인트(GET /user/isEmailExist)가 legacy와 동일한 JSON 형식으로 응답해야 한다") {
                    val result = mockMvc.perform(get("/user/isEmailExist").param("email", member.email))
                        .andExpect(status().isOk)
                        .andReturn()

                    val json = tools.jackson.databind.ObjectMapper().readTree(result.response.contentAsString)
                    json.get("isExist").asBoolean() shouldBe true
                }
            }

            describe("[Test-19-19] 비밀번호 재설정 화면(user/resetPassword.scala.html) 동치성 검증") {
                it("비밀번호 재설정 화면은 site/layout 기반 전체 GNB와 footer, resetPassword 모듈 스크립트를 포함해야 한다") {
                    val result = mockMvc.perform(get("/user/reset-password").param("hash", "dummy-hash")).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)

                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select("script[src*='lib/validate.js']").size shouldBe 1
                    doc.select("script[src*='service/yobi.resetPassword.js']").size shouldBe 1
                }
            }

            describe("[Test-19-20] 프로젝트 헤더(project/header.scala.html) 동치성 검증") {
                it("비멤버 로그인 사용자에게 프로젝트 가입 요청 버튼이 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(nonMemberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".project-util a.enrollBtn").size shouldBe 1
                    doc.select(".project-util a.enrollBtn[data-request-uri*='/api/projects/${publicProj.id}/enroll']").size shouldBe 1
                }

                it("프로젝트 멤버에게는 가입 요청 버튼이 노출되지 않아야 한다") {
                    // member는 codeMemberOnlyProj의 실제 ProjectUser임(상위 describe 블록에서 세팅됨)
                    val result = mockMvc.perform(
                        get("/owner/memberonly-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".project-util a.enrollBtn").size shouldBe 0
                }

                it("PROTECTED(그룹 전용) 프로젝트에는 group 배지가 노출되어야 한다") {
                    val protectedProj = projectRepository.findAll().find { it.name == "protected-proj-header-test" }
                        ?: projectRepository.save(
                            Project(
                                name = "protected-proj-header-test",
                                owner = "owner",
                                projectScope = ProjectScope.PROTECTED
                            )
                        )
                    if (!projectUserRepository.existsByProjectIdAndUserId(protectedProj.id!!, member.id!!)) {
                        val role = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                            roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                        }
                        projectUserRepository.save(ProjectUser(project = protectedProj, user = member, role = role))
                    }

                    val result = mockMvc.perform(
                        get("/owner/${protectedProj.name}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".project-protected").size shouldBe 1
                }
            }

            val settingProj = projectRepository.findAll().find { it.name == "settings-test-proj" } ?: projectRepository.save(
                Project(
                    name = "settings-test-proj",
                    owner = "owner",
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = false
                )
            )
            if (!projectUserRepository.existsByProjectIdAndUserId(settingProj.id!!, member.id!!)) {
                val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }
                projectUserRepository.save(ProjectUser(project = settingProj, user = member, role = managerRole))
            } else {
                val existing = projectUserRepository.findByProjectIdAndUserId(settingProj.id!!, member.id!!).get()
                if (existing.role.id != RoleType.MANAGER.roleType) {
                    val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                        roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                    }
                    existing.role = managerRole
                    projectUserRepository.save(existing)
                }
            }

            describe("[Test-19-21] VCS 변경 화면(project/change_vcs.scala.html) 동치성 검증") {
                it("VCS 변경 화면은 site/layout 기반 전체 GNB/footer와 project/header, project/menu, setting_menu 조각을 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/${settingProj.name}/changeVCS")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select(".project-menu-outer").size shouldBe 1
                    doc.select("script[src*='service/yobi.project.ChangeVCS.js']").size shouldBe 1
                }
            }

            describe("[Test-19-22] 프로젝트 설정 하위 화면들의 독자 GNB 제거 검증 (delete/transfer/watchers/setting_webhook)") {
                it("delete/transfer/watchers/setting_webhook 화면이 전부 site/layout 기반 전체 GNB와 footer를 포함해야 한다") {
                    val deleteDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/deleteform").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    deleteDoc.select("form[name='gnb-search-form']").size shouldBe 1
                    deleteDoc.select("footer.page-footer-outer").size shouldBe 1

                    val transferDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/transfer").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    transferDoc.select("form[name='gnb-search-form']").size shouldBe 1
                    transferDoc.select("footer.page-footer-outer").size shouldBe 1

                    val watchersDoc = Jsoup.parse(
                        mockMvc.perform(get("/owner/${settingProj.name}/watchers")).andReturn().response.contentAsString
                    )
                    watchersDoc.select("form[name='gnb-search-form']").size shouldBe 1
                    watchersDoc.select("footer.page-footer-outer").size shouldBe 1

                    val webhookDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/projects/owner/${settingProj.name}/webhooks").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    webhookDoc.select("form[name='gnb-search-form']").size shouldBe 1
                    webhookDoc.select("footer.page-footer-outer").size shouldBe 1
                }
            }

            // yona-wiki P3-04(브랜치 보호) 2라운드 — setting_webhook과 동일한 패턴(신규
            // BranchProtectionController + project/setting_branch_protection.html)으로 추가한
            // 관리 화면이 실제 Spring 컨텍스트에서 렌더링 오류 없이 GNB/footer/setting_menu를
            // 정상 포함하는지 검증한다. standaloneSetup 기반 BranchProtectionControllerSpec은
            // view 이름만 확인할 뿐 실제 Thymeleaf 렌더링을 태우지 않으므로 이 스펙에서 보강한다.
            describe("[Test-19-24] 브랜치 보호 설정 화면(project/setting_branch_protection.html) 렌더링 검증") {
                it("브랜치 보호 설정 화면은 site/layout 기반 전체 GNB/footer와 project/header, setting_menu 조각, 새 규칙 추가 폼을 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/projects/owner/${settingProj.name}/branch-protections")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    result.response.status shouldBe 200
                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select("#subMenuBranchProtection").size shouldBe 1
                    doc.select("#formNewBranchProtection").size shouldBe 1
                    doc.select("input[name='branchPattern']").size shouldBe 1
                }

                it("프로젝트 매니저가 아닌 로그인 사용자는 403으로 거부된다") {
                    val result = mockMvc.perform(
                        get("/projects/owner/${settingProj.name}/branch-protections")
                            .with(SecurityMockMvcRequestPostProcessors.user(nonMemberDetails))
                    ).andReturn()

                    result.response.status shouldBe 403
                }
            }

            describe("[Test-19-23] 이슈 라벨 설정 화면(project/issuelabels.scala.html) 동치성 검증") {
                // 2026-08-23 재감사: #108이 "보류(현행 커스텀 구현 유지)"로 남겨뒀던 라벨/카테고리 CRUD를
                // legacy 실제 정적 모듈(yobi.issue.LabelEditor.js) + 서버렌더 파샬(partial_issuelabels_list/
                // editcategory/editlabel) 기반으로 교체(TASK-0262) — JSON REST(/api/projects/{id}/labels)
                // 커스텀 구현을 걷어내고 legacy와 동일한 폼 제출/모달 구조로 재작성했다.
                val settingCategory = issueLabelCategoryRepository.findAll().find { it.project.id == settingProj.id && it.name == "설정테스트카테고리" }
                    ?: issueLabelCategoryRepository.save(IssueLabelCategory(name = "설정테스트카테고리", project = settingProj))
                val settingLabel = issueLabelRepository.findAll().find { it.category.id == settingCategory.id }
                    ?: issueLabelRepository.save(IssueLabel(name = "설정테스트라벨", color = "#2196f3", category = settingCategory, project = settingProj))

                it("이슈 라벨 설정 화면은 site/layout 기반 전체 GNB/footer와 project/header, setting_menu 조각, 새 라벨/편집 폼의 프리셋 색상 29개(신규 17 + 수정모달 12)를 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/${settingProj.name}/issue/labelsform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select("#subMenuIssueLabel.active").size shouldBe 1
                    doc.select("button.issue-label.btn-preset-color").size shouldBe 29
                    doc.select("script[src*='code.jquery.com']").size shouldBe 0
                }

                it("legacy partial_issuelabels_list.scala.html과 동일하게 카테고리별 라벨 목록·수정/삭제 버튼의 data-uri, 라벨 복사 폼, 수정 모달 2종을 렌더링해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/issue/labelsform")
                                .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    // legacy가 실제로 로드하는 정적 모듈(REST JSON 커스텀 구현이 아님)
                    doc.select("script[src='/javascripts/service/yobi.issue.LabelEditor.js']").size shouldBe 1

                    val categoryWrap = doc.select("div.category-wrap[data-category-name='설정테스트카테고리']")
                    categoryWrap.size shouldBe 1
                    categoryWrap.select("span.issue-label[data-label-id='${settingLabel.id}']").text() shouldBe "설정테스트라벨"
                    categoryWrap.select("button[data-delete-uri]").attr("data-delete-uri") shouldBe
                        "/owner/${settingProj.name}/issue/label/${settingLabel.id}/delete"
                    categoryWrap.select("button[data-update-uri]").attr("data-update-uri") shouldBe
                        "/owner/${settingProj.name}/issue/label/${settingLabel.id}"
                    categoryWrap.select("button[data-category-update-uri]").attr("data-category-update-uri") shouldBe
                        "/owner/${settingProj.name}/issue/label/category/${settingCategory.id}"

                    doc.select("form#copyLabel[action='/owner/${settingProj.name}/copyLabels']").size shouldBe 1
                    doc.select("form#frmNewLabel[action='/owner/${settingProj.name}/issue/labels']").size shouldBe 1
                    doc.select("#editCategory.yobiDialog").size shouldBe 1
                    doc.select("#editLabel.yobiDialog select[name='category.id'] option").size shouldBe 1
                }
            }

            describe("[Test-19-24] 이슈 목록 화면(issue/list.scala.html) 동치성 검증") {
                val openMilestone = milestoneRepository.findAll()
                    .find { it.project.id == settingProj.id && it.title == "열린 마일스톤" }
                    ?: milestoneRepository.save(Milestone(title = "열린 마일스톤", project = settingProj, state = State.OPEN))
                val closedMilestone = milestoneRepository.findAll()
                    .find { it.project.id == settingProj.id && it.title == "닫힌 마일스톤" }
                    ?: milestoneRepository.save(Milestone(title = "닫힌 마일스톤", project = settingProj, state = State.CLOSED))

                it("마일스톤 검색 필터는 legacy와 동일하게 열림/닫힘 optgroup을 모두 포함해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/issues").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("select#milestoneId optgroup").size shouldBe 2
                    doc.select("select#milestoneId option[value='${openMilestone.id}']").size shouldBe 1
                    doc.select("select#milestoneId option[value='${closedMilestone.id}']").size shouldBe 1
                }

                it("라벨 관리 링크는 매니저에게만 노출되고 일반 멤버에게는 노출되지 않아야 한다(legacy isManagerOf 권한 이식)") {
                    val managerDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/issues").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    managerDoc.select("div.labels-wrap").size shouldBe 1

                    val memberDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${codeMemberOnlyProj.name}/issues").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    memberDoc.select("div.labels-wrap").size shouldBe 0
                }
            }

            describe("[Test-19-25] 프로젝트 생성 화면(project/create.scala.html) 동치성 검증") {
                val orgRole = roleRepository.findById(RoleType.ORG_ADMIN.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN"))
                }
                val org = organizationRepository.findAll().find { it.name == "create-test-org" }
                    ?: organizationRepository.save(Organization(name = "create-test-org"))
                if (organizationUserRepository.findAll().none { it.organization.id == org.id && it.user.id == member.id }) {
                    organizationUserRepository.save(OrganizationUser(user = member, organization = org, role = orgRole))
                }
                val existingOrgProj = projectRepository.findAll().find { it.owner == org.name && it.name == "dup-proj" }
                    ?: projectRepository.save(Project(name = "dup-proj", owner = org.name, projectScope = ProjectScope.PUBLIC))

                it("PROTECTED(그룹공개) 옵션이 legacy와 동일하게 존재해야 한다(백엔드는 이미 ProjectScope.PROTECTED 지원)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/projectform").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("input#protected[value=PROTECTED]").size shouldBe 1
                    doc.select("li#opt-protected").size shouldBe 1
                    // 소유자 기본값은 본인 계정(user 타입)이라 opt-protected는 초기 숨김 상태여야 한다
                    doc.select("li#opt-protected[style*=display:none]").size shouldBe 1
                }

                it("검증 실패로 폼이 재표시될 때 legacy의 Form 바인딩처럼 입력값이 보존되어야 한다") {
                    val result = mockMvc.perform(
                        post("/projectform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .param("owner", org.name)
                            .param("name", existingOrgProj.name)
                            .param("overview", "재입력 검증용 설명")
                            .param("projectScope", "PROTECTED")
                            .param("vcs", "GIT")
                            .param("code", "true")
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("textarea#description").text() shouldBe "재입력 검증용 설명"
                    doc.select("input#project-name").attr("value") shouldBe existingOrgProj.name
                    doc.select("input#protected").hasAttr("checked") shouldBe true
                    doc.select("option[value='${org.name}']").hasAttr("selected") shouldBe true
                    // 소유자가 조직이므로 opt-protected는 노출 상태로 재표시되어야 한다
                    doc.select("li#opt-protected[style*=display:block]").size shouldBe 1
                }
            }

            describe("[Test-19-27] 게시판 목록 화면(board/list.scala.html) 동치성 검증") {
                val noticePost = postingRepository.findAll().find { it.project.id == publicProj.id && it.number == 20L }
                    ?: postingRepository.save(
                        Posting(
                            title = "[공지] 점검 안내",
                            body = "내용",
                            project = publicProj,
                            authorId = owner.id!!,
                            authorLoginId = owner.loginId,
                            authorName = owner.name,
                            number = 20L,
                            notice = true
                        )
                    )
                val bracketPost = postingRepository.findAll().find { it.project.id == publicProj.id && it.number == 21L }
                    ?: postingRepository.save(
                        Posting(
                            title = "[bugfix] 버그 수정 안내",
                            body = "내용",
                            project = publicProj,
                            authorId = owner.id!!,
                            authorLoginId = owner.loginId,
                            authorName = owner.name,
                            number = 21L
                        )
                    )

                it("공지글은 legacy처럼 상단 공지 목록에만 노출되고 일반 목록에는 중복 노출되지 않아야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${publicProj.name}/posts").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("ul.notice-wrap li").size shouldBe 1
                    doc.select("ul.post-list-wrap:not(.notice-wrap) li[href*='/post/${noticePost.number}']").size shouldBe 0
                }

                it("제목의 대괄호 접두어는 legacy처럼 .bracket-word로 분리되고 링크 텍스트에서는 제거되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${publicProj.name}/posts").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    val row = doc.select("li[href*='/post/${bracketPost.number}']").first()
                    row?.select(".bracket-word")?.text() shouldBe "[bugfix]"
                    row?.select("a.title")?.text() shouldBe "버그 수정 안내"
                }
            }

            describe("[Test-19-28] 게시판 상세 화면(board/view.scala.html) 삭제 확인 모달 i18n 검증") {
                it("삭제 확인 모달의 예/아니오 버튼은 하드코딩 텍스트가 아니라 메시지 키를 사용해야 한다") {
                    val post = postingRepository.findAll().find { it.project.id == publicProj.id }!!
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${publicProj.name}/post/${post.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("#deleteConfirm .modal-footer button.ybtn-danger").text() shouldBe "예"
                    doc.select("#deleteConfirm .modal-footer button[data-dismiss=modal]").text() shouldBe "아니요"
                }
            }

            describe("[Test-19-29] 페이지 제목 |:| 컨벤션(layout.scala.html:8) 동치성 검증") {
                val ogIssue = issueRepository.findAll().find { it.project.id == publicProj.id && it.title == "테스트이슈" }!!

                it("이슈 상세 화면은 <title>/og:title엔 이슈 제목만, og:description엔 본문 미리보기를 사용해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/issue/${ogIssue.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("title").text() shouldBe "테스트이슈 - Yona"
                    doc.select("meta[property=og:title]").attr("content") shouldBe "테스트이슈"
                    doc.select("meta[property=og:description]").attr("content") shouldBe "이슈내용"
                    doc.select("meta[name=twitter:description]").attr("content") shouldBe "이슈내용"
                }

                it("|:| 구분자가 없는 일반 화면은 title을 og:title/og:description에 그대로 동일하게 사용해야 한다(하위호환)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/${settingProj.owner}/${settingProj.name}/issue/labelsform").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    val ogTitle = doc.select("meta[property=og:title]").attr("content")
                    val ogDesc = doc.select("meta[property=og:description]").attr("content")
                    ogTitle shouldBe ogDesc
                }
            }

            describe("[Test-19-30] 프로젝트 메뉴(projectMenu.scala.html) 동치성 검증") {
                it("SVN 프로젝트에서는 Pull Request 탭이 노출되지 않아야 한다(legacy project.vcs==GIT 조건)") {
                    val svnProj = projectRepository.findAll().find { it.name == "svn-menu-test-proj" } ?: projectRepository.save(
                        Project(name = "svn-menu-test-proj", owner = "owner", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                    )
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/${svnProj.name}")).andReturn().response.contentAsString
                    )
                    doc.select(".project-menu-nav a[href*='/pulls'], .project-menu-nav a[href*='PullRequests']").size shouldBe 0
                }

                it("매니저에게는 설정 메뉴에 가입요청 대기 인원 수 배지와 키보드 단축키(htKeyMap) 스크립트가 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    doc.select("script:containsData(htKeyMap)").size shouldBe 1
                    doc.select("script:containsData(project.Global)").size shouldBe 1
                }
            }

            describe("[Test-19-31] GNB 검색범위/커스텀링크 권한 게이트(common/navbar.scala.html) 동치성 검증") {
                val guest2 = userRepository.findByLoginId("guestuser2").orElseGet {
                    userRepository.save(User(loginId = "guestuser2", name = "게스트2", email = "guestuser2@yona.io", isGuest = true))
                }
                val guest2Details = YonaUserDetails(
                    id = guest2.id!!,
                    loginId = guest2.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                it("일반 로그인 사용자에게는 '모든 프로젝트' 검색범위가 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/public-proj").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))).andReturn().response.contentAsString
                    )
                    doc.select("a[data-toggle=search-scope][data-action=/search]").size shouldBe 1
                }

                it("게스트 사용자에게는 '모든 프로젝트' 검색범위가 legacy와 동일하게 숨겨져야 한다(HIDE_PROJECT_LISTING||guest 게이트)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/public-proj").with(SecurityMockMvcRequestPostProcessors.user(guest2Details))).andReturn().response.contentAsString
                    )
                    doc.select("a[data-toggle=search-scope][data-action=/search]").size shouldBe 0
                }

                it("사이트관리자는 게스트여도 '모든 프로젝트' 검색범위가 보여야 한다(legacy isSiteManager 예외)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/public-proj").with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))).andReturn().response.contentAsString
                    )
                    doc.select("a[data-toggle=search-scope][data-action=/search]").size shouldBe 1
                }

                it("커스텀 네비게이션 링크는 기본 설정(빈 값)에서는 렌더링되지 않아야 한다(yona.application.navbar.custom-link.name 미설정 시)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))).andReturn().response.contentAsString
                    )
                    // myIssue 항목 하나만 남아야 한다 — 커스텀 링크 <li>는 th:if로 렌더링에서 제외됨
                    doc.select("a.user-item-btn.loggged-in").size shouldBe 1
                }
            }

            describe("[Test-19-32] 프로젝트 헤더 즐겨찾기 별표(project/header.scala.html) 동치성 검증") {
                it("즐겨찾기 등록한 프로젝트는 서버사이드 초기 상태부터 별표에 starred 클래스가 붙어야 한다") {
                    if (favoriteProjectRepository.findByUserIdAndProjectId(member.id!!, publicProj.id!!).isEmpty) {
                        favoriteProjectRepository.save(FavoriteProject(user = member, project = publicProj))
                    }
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/public-proj").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))).andReturn().response.contentAsString
                    )
                    doc.select(".user-project-list i.star.starred").size shouldBe 1
                }

                it("즐겨찾기 등록하지 않은 프로젝트는 별표에 starred 클래스가 없어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/memberonly-proj").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))).andReturn().response.contentAsString
                    )
                    doc.select(".user-project-list i.star.starred").size shouldBe 0
                    doc.select(".user-project-list i.star").size shouldBe 1
                }
            }

            describe("[Test-19-33] 마이그레이션 화면(migration/home.scala.html, migrationPageLayout.scala.html) 동치성 검증") {
                it("migrationPageLayout이 감싸던 전체 GNB/footer, migration 전용 자산이 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/migration").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)

                    // migrationPageLayout = partial_update_notification() + common.navbar(...) + @content + common.scripts()
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1

                    // migrationPageLayout 전용 헤드/스크립트 자산(공용 site/layout::head/scripts에는 없음)
                    doc.select("script[src*='lib/jquery/jquery-1.9.0.js']").size shouldBe 1
                    doc.select("script[src*='lib/jquery/jquery.browser.js']").size shouldBe 1
                    doc.select("script[src*='lib/jquery/jquery.pjax.js']").size shouldBe 1
                    doc.select("script[src*='common/yobi.Common.js']").size shouldBe 1
                    doc.select("script[src*='lib/vendor.js']").size shouldBe 1
                    doc.select("script[src*='service/yona.Migration.js']").size shouldBe 1
                    doc.select("link[href*='fonts.googleapis.com/css?family=Montserrat']").size shouldBe 1

                    // AngularJS 앱 컨테이너는 토큰 유무와 무관하게 항상 렌더링된다
                    doc.select("div.yobi-migration[ng-app=yona.migration]").size shouldBe 1
                }

                it("code 파라미터(및 그에 따른 token)가 없으면 header-pannel 전체가 렌더링되지 않아야 한다(legacy ng-if=\"'@token'\")") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/migration").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select(".yobi-migration > .header-pannel").size shouldBe 0
                }

                it("로그인하지 않은 사용자는 로그인 폼으로 리다이렉트되어야 한다") {
                    mockMvc.perform(get("/migration"))
                        .andExpect(status().is3xxRedirection)
                }
            }

            describe("[Test-19-34] 통합 검색 화면(search/*.scala.html) 동치성 검증") {
                val searchKeyword = "zzsearchkw31"
                val longBody = "긴 본문 테스트를 위한 앞부분 채우기 문장입니다 " +
                    "$searchKeyword 그리고 스니펫이 본문 전체보다 짧아져서 말줄임표가 붙는지 확인하기 위해 " +
                    "이 뒤에도 계속 이어지는 아주 긴 문장을 덧붙입니다 테스트 테스트 테스트 테스트 테스트"

                val searchProj = projectRepository.findAll().find { it.name == "search-fixture-proj" } ?: projectRepository.save(
                    Project(
                        name = "search-fixture-proj",
                        owner = "owner",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT",
                        overview = "프로젝트 설명 $searchKeyword",
                        lastPushedDate = java.time.Instant.now()
                    )
                )

                val searchForkProj = projectRepository.findAll().find { it.name == "search-fixture-fork-proj" } ?: projectRepository.save(
                    Project(
                        name = "search-fixture-fork-proj",
                        owner = "owner",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT",
                        overview = "포크 프로젝트 설명 $searchKeyword",
                        originalProject = searchProj
                    )
                )

                val issueWithAuthor = issueRepository.findAll().find { it.title == "검색이슈-작성자있음" } ?: issueRepository.save(
                    Issue(
                        title = "검색이슈-작성자있음",
                        body = longBody,
                        project = searchProj,
                        authorId = member.id,
                        authorLoginId = member.loginId,
                        authorName = member.name,
                        number = 9101L
                    )
                )

                val issueNoAuthor = issueRepository.findAll().find { it.title == "검색이슈-작성자없음" } ?: issueRepository.save(
                    Issue(
                        title = "검색이슈-작성자없음",
                        body = "작성자 없는 이슈 본문 $searchKeyword",
                        project = searchProj,
                        authorId = null,
                        authorLoginId = null,
                        authorName = null,
                        number = 9102L
                    )
                )

                val postWithAuthor = postingRepository.findAll().find { it.title == "검색게시글-작성자있음" } ?: postingRepository.save(
                    Posting(
                        title = "검색게시글-작성자있음",
                        body = longBody,
                        project = searchProj,
                        authorId = member.id,
                        authorLoginId = member.loginId,
                        authorName = member.name,
                        number = 9201L
                    )
                )

                val milestoneWithDue = milestoneRepository.findAll().find { it.title == "검색마일스톤-기한있음" } ?: milestoneRepository.save(
                    Milestone(
                        title = "검색마일스톤-기한있음",
                        contents = "마일스톤 설명 $searchKeyword",
                        dueDate = java.time.Instant.now().plusSeconds(86400 * 3),
                        project = searchProj
                    )
                )

                val milestoneNoDue = milestoneRepository.findAll().find { it.title == "검색마일스톤-기한없음" } ?: milestoneRepository.save(
                    Milestone(
                        title = "검색마일스톤-기한없음",
                        contents = "마일스톤 설명2 $searchKeyword",
                        dueDate = null,
                        project = searchProj
                    )
                )

                val searchUser = userRepository.findByLoginId("search-fixture-user").orElseGet {
                    userRepository.save(User(loginId = "search-fixture-user", name = "검색픽스처유저$searchKeyword", email = "search-fixture-user@yona.io"))
                }

                val issueCommentNoAuthor = issueCommentRepository.findAll().find { it.contents.contains(searchKeyword) && it.issue.id == issueWithAuthor.id }
                    ?: issueCommentRepository.save(
                        IssueComment(contents = "이슈댓글 내용 작성자없음 $searchKeyword", authorId = null, authorLoginId = null, authorName = null, issue = issueWithAuthor)
                    )

                val postCommentWithAuthor = postingCommentRepository.findAll().find { it.contents.contains(searchKeyword) && it.posting.id == postWithAuthor.id }
                    ?: postingCommentRepository.save(
                        PostingComment(contents = "게시글댓글 내용 $searchKeyword", authorId = member.id, authorLoginId = member.loginId, authorName = member.name, posting = postWithAuthor)
                    )

                val pr = pullRequestRepository.findAll().find { it.title == "검색PR제목" } ?: pullRequestRepository.save(
                    PullRequest(title = "검색PR제목", toProject = searchProj, fromProject = searchProj, contributor = member, number = 9301L)
                )

                val prThread = commentThreadRepository.findAll().find { it.pullRequest?.id == pr.id } ?: commentThreadRepository.save(
                    SimpleCommentThread(author = UserIdent(member), pullRequest = pr, project = searchProj)
                )

                val prReviewComment = reviewCommentRepository.findAll().find { it.thread?.id == prThread.id && it.contents.contains(searchKeyword) }
                    ?: reviewCommentRepository.save(
                        ReviewComment(contents = "PR 리뷰 댓글 $searchKeyword", author = UserIdent(member), thread = prThread)
                    )

                val commitThread = commentThreadRepository.findAll().find { it.pullRequest == null && it.commitId == "abcdef1234567890" }
                    ?: commentThreadRepository.save(
                        SimpleCommentThread(author = UserIdent(member), project = searchProj, commitId = "abcdef1234567890")
                    )

                val commitReviewComment = reviewCommentRepository.findAll().find { it.thread?.id == commitThread.id && it.contents.contains(searchKeyword) }
                    ?: reviewCommentRepository.save(
                        ReviewComment(contents = "커밋리뷰 댓글 $searchKeyword", author = UserIdent(member), thread = commitThread)
                    )

                it("이슈 탭: 스니펫 말줄임표, 작성자 링크/작성자없음 폴백, 프로젝트링크 owner/name 표기, 페이지네이션 위젯이 legacy와 일치해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/search").param("keyword", searchKeyword).param("searchType", "issue"))
                            .andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    val items = doc.select("div.search-result-wrap li.search-list-item")

                    val authored = items.first { it.select(".title").text() == issueWithAuthor.title }
                    authored.select(".search-content-body").text().contains(".....") shouldBe true
                    val authorLink = authored.select(".search-meta-info a.meta-item:not(.project-link)")
                    authorLink.attr("href") shouldBe "/${member.loginId}"
                    authorLink.text() shouldBe member.name
                    authored.select(".project-link").text() shouldBe "${searchProj.owner}/${searchProj.name}"

                    val noAuthor = items.first { it.select(".title").text() == issueNoAuthor.title }
                    noAuthor.select(".search-meta-info span.meta-item").first()!!.text() shouldBe "작성자 없음"

                    doc.select("#pagination").size shouldBe 1
                    doc.html().contains("yobi.Pagination.update") shouldBe true
                }

                it("프로젝트 탭: 로고/포크뱃지/전체 개요(스니펫 아님)/생성일·코드업데이트 문구가 legacy와 일치해야 하고, 페이지네이션이 없어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/search").param("keyword", searchKeyword).param("searchType", "project"))
                            .andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    val items = doc.select("div.search-result-wrap li.search-list-item.project")

                    val original = items.first { it.select(".title").text() == "${searchProj.owner}/${searchProj.name}" }
                    original.select(".search-content-body").text() shouldBe searchProj.overview
                    original.select(".search-content-body").text().contains(".....") shouldBe false
                    original.text().contains("마지막 코드 업데이트") shouldBe true
                    original.select(".search-meta-info.nm.np").size shouldBe 0

                    val fork = items.first { it.select(".title").text() == "${searchForkProj.owner}/${searchForkProj.name}" }
                    fork.select(".search-meta-info.nm.np").size shouldBe 1
                    fork.select(".search-meta-info.nm.np").text().contains("원본 프로젝트") shouldBe true
                    fork.select(".search-meta-info.nm.np a").attr("href") shouldBe "/${searchProj.owner}/${searchProj.name}"

                    doc.select("#pagination").size shouldBe 0
                }

                it("사용자 탭: 아바타 이미지와 userinfo.since 가입일 문구가 legacy와 일치해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/search").param("keyword", searchKeyword).param("searchType", "user"))
                            .andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    val item = doc.select("div.search-result-wrap li.search-list-item.project")
                        .first { it.select(".title").text().startsWith(searchUser.name) }

                    item.select("a.avatar-wrap img").attr("src") shouldBe "/assets/images/default-avatar-128.png"
                    item.select(".infos-item").text().contains("가입일") shouldBe true
                }

                it("마일스톤 탭: 기한이 없으면 기한 영역 자체가 렌더링되지 않아야 한다(legacy 그대로)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/search").param("keyword", searchKeyword).param("searchType", "milestone"))
                            .andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    val items = doc.select("div.search-result-wrap li.search-list-item")

                    val withDue = items.first { it.select(".title").text() == milestoneWithDue.title }
                    withDue.select(".due-date.meta-item").size shouldBe 1
                    withDue.select(".due-date.meta-item").text().contains("기한") shouldBe true

                    val noDue = items.first { it.select(".title").text() == milestoneNoDue.title }
                    noDue.select(".due-date.meta-item").size shouldBe 0
                }

                it("이슈댓글/게시글댓글 탭: 제목이 legacy 그대로 'Re) ' 접두사여야 하고 noAuthor 메시지키가 도메인별로 달라야 한다") {
                    val issueCommentDoc = Jsoup.parse(
                        mockMvc.perform(get("/search").param("keyword", searchKeyword).param("searchType", "issue_comment"))
                            .andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    val icItem = issueCommentDoc.select("div.search-result-wrap li.search-list-item")
                        .first { it.select(".title-wrap a").text() == "Re) ${issueWithAuthor.title}" }
                    icItem.select(".title-wrap a").attr("href").endsWith("#comment-${issueCommentNoAuthor.id}") shouldBe true
                    icItem.select(".search-meta-info span.meta-item").first()!!.text() shouldBe "작성자 없음"

                    val postCommentDoc = Jsoup.parse(
                        mockMvc.perform(get("/search").param("keyword", searchKeyword).param("searchType", "post_comment"))
                            .andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    val pcItem = postCommentDoc.select("div.search-result-wrap li.search-list-item")
                        .first { it.select(".title-wrap a").text() == "Re) ${postWithAuthor.title}" }
                    pcItem.select(".title-wrap a").attr("href").endsWith("#comment-${postCommentWithAuthor.id}") shouldBe true
                    pcItem.select(".search-meta-info a.meta-item:not(.project-link)").text() shouldBe member.name
                }

                it("리뷰(코드리뷰댓글) 탭: PR 스레드는 /pull/ 라우트로, 커밋 스레드는 제목없이 전체가 링크로 감싸여야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/search").param("keyword", searchKeyword).param("searchType", "review"))
                            .andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    val items = doc.select("div.search-result-wrap li.search-list-item")

                    val prItem = items.first { it.select(".title-wrap a").text() == "Re) ${pr.title}" }
                    val prLink = prItem.select(".title-wrap a").attr("href")
                    prLink.contains("/${searchProj.owner}/${searchProj.name}/pull/${pr.number}") shouldBe true
                    prLink.contains("/pullRequest/") shouldBe false

                    val commitItem = items.first { it.select(".search-content-body").text().contains("커밋리뷰") }
                    commitItem.select(".title-wrap").size shouldBe 0
                    val wrappingLink = commitItem.select(".search-content > a")
                    wrappingLink.size shouldBe 1
                    wrappingLink.attr("href").contains("/${searchProj.owner}/${searchProj.name}/commit/${commitThread.commitId}") shouldBe true
                }

                it("카테고리 탭: 현재 검색타입에 active 클래스가, 결과 0건인 타입에 empty 클래스가 legacy와 동일하게 붙어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/search").param("keyword", searchKeyword).param("searchType", "issue"))
                            .andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    val issueTab = doc.select("a[data-type=issue]").first()!!.parent()!!
                    issueTab.hasClass("active") shouldBe true
                    issueTab.hasClass("empty") shouldBe false
                }
            }

            describe("[Test-19-35] 도움말 화면(help/*.scala.html, 그룹15 #234~238) 동치성 검증") {
                it("toc.html(#234)은 legacy와 동일하게 6개의 Q&A 항목을 렌더링해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/_help")).andExpect(status().isOk).andReturn().response.contentAsString
                    )

                    doc.select("ul.qas > li.qa").size shouldBe 6
                    doc.select(".site-breadcrumb-inner h3").text() shouldBe "도움말"
                }

                it("toc.html(#234)의 <title>은 하드코딩 문자열이 아니라 title.help 메시지 키를 사용해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/_help")).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    doc.select("title").text() shouldBe "도움말 - Yona"

                    val tocSource = com.github.yonaprojects.yona.web.TemplateEquivalenceSpec::class.java
                        .getResourceAsStream("/templates/help/toc.html")!!
                        .bufferedReader(Charsets.UTF_8).readText()
                    tocSource.contains("head(#{title.help})") shouldBe true
                    tocSource.contains("head('도움말')") shouldBe false
                }

                it("UIKit.html(#237)은 site GNB/footer 없이 독자적인 standalone 페이지로 렌더링되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/_UIKit")).andExpect(status().isOk).andReturn().response.contentAsString
                    )

                    doc.select("title").text() shouldBe "Yobi UI"
                    doc.select("header.gnb-outer > span.subtitle").text() shouldBe "Yobi UI"
                    doc.select(".ybtn.ybtn-primary").text() shouldBe "Primary"
                    doc.select(".avatar-wrap.xlarge").size shouldBe 1
                    doc.select(".switch.switch-square").size shouldBe 1
                    doc.select(".gnb-search").size shouldBe 0
                }

                it("markdown.html(#235)은 이슈 작성 에디터에 포함되어 legacy와 동일하게 10개의 문법 탭을 제공해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/issueform").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )

                    doc.select(".markdown-help .markdown-help-nav li.help-nav").size shouldBe 10
                    doc.select(".markdown-help .markdown-help-wrap li.markdown-help-item").size shouldBe 10
                    doc.select(".markdown-help-item.markdownShortLinks .markdown-wrap a").first()?.attr("href") shouldBe
                        "http://demo.yobi.io/yobi/yobi/issue/2"
                }

                it("keymap.html(#236)은 section 값에 따라 게시판 목록/상세에서 서로 다른 안내 항목을 노출해야 한다") {
                    val listDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/posts").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    listDoc.select("#helpKeys").select("span.help-inline:containsOwn(글쓰기)").size shouldBe 1
                    listDoc.select("#helpKeys").select("span.help-inline:containsOwn(이전 페이지)").size shouldBe 1
                    listDoc.select("#helpKeys").select("span.help-inline:containsOwn(다음 페이지)").size shouldBe 1
                    listDoc.select("#helpKeys").select("span.help-inline:containsOwn(목록)").size shouldBe 0

                    val post = postingRepository.findAll().find { it.project.id == publicProj.id }!!
                    val viewDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/post/${post.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    viewDoc.select("#helpKeys").select("span.help-inline:containsOwn(목록)").size shouldBe 1
                    viewDoc.select("#helpKeys").select("span.help-inline:containsOwn(수정)").size shouldBe 1
                    viewDoc.select("#helpKeys").select("span.help-inline:containsOwn(이전 페이지)").size shouldBe 0
                    viewDoc.select("#helpKeys").select("span.help-inline:containsOwn(다음 페이지)").size shouldBe 0
                }

                it("experimental.html(#238)은 legacy와 동일하게 실험실 안내 모달 마크업을 렌더링해야 한다(legacy도 미참조 상태의 독립 조각)") {
                    val html = templateEngine.process("help/experimental", ThymeleafContext(Locale.KOREAN))
                    val doc = Jsoup.parse(html)

                    doc.select("#experimentalHelp.modal.hide.fade").size shouldBe 1
                    doc.select("#experimentalHelp h4").text() shouldBe "실험적인 기능: 새롭게 개발 중인 기능을 선보입니다"
                    doc.select("#experimentalHelp .modal-body.center-txt").html().replace("\n", "") shouldBe
                        "이 기능은 아직 개발 진행 중으로 언제든지 변경되거나 개발 중단될 수 있습니다.<br>너그러운 마음으로 응원해주세요."
                    doc.select("#experimentalHelp button.ybtn-info[data-dismiss=modal]").text() shouldBe "확인"
                }
            }
        }
    }
}
