package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.support.YonaUpdateService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.assertions.withClue
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.thymeleaf.context.Context
import org.thymeleaf.context.WebContext
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.web.servlet.JakartaServletWebApplication
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import java.util.Locale

/**
 * 그룹13 `site` 하위 사이트 관리자 화면(#210~223) 동치성 검증.
 * TemplateEquivalenceSpec.kt와 동일한 하네스(AbstractIntegrationTest + MockMvc + Jsoup)를 재사용하되,
 * 항목 수가 많아 별도 파일로 분리했다(백로그 TDD 절차 2단계 안내를 따름).
 */
class SiteAdminTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val yonaUpdateService: YonaUpdateService,
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

        describe("Thymeleaf 사이트 관리자 템플릿 동치성 회귀 검증") {
            val siteAdmin = userRepository.findByLoginId("site-admin-13").orElseGet {
                userRepository.save(
                    User(loginId = "site-admin-13", name = "관리자13", email = "site-admin-13@yona.io", state = UserState.SITE_ADMIN)
                )
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

            // site/setting.html은 legacy에서도 라우트가 없던 죽은 스텁이라 실제 컨트롤러가 없다. 하지만 그 안에
            // 포함하는 site/layout::gnb 조각은 sec:authorize를 쓰는데, thymeleaf-extras-springsecurity6는
            // sec:authorize 평가 시 IContext에서 실제 HttpServletRequest/Response를 꺼내 FilterInvocation을
            // 만든다(AuthUtils$MvcAuthUtils.authorizeUsingAccessExpressionMvc) — 평범한 org.thymeleaf.context.Context
            // (비-웹 컨텍스트)로는 request가 없어 예외가 난다. 그래서 이 화면은 전체 렌더링 대신, 클래스패스에 올라간
            // 템플릿 리소스의 원문을 직접 읽어 구조(조각 참조 + TODO 텍스트)를 검증한다.
            fun readTemplateResource(templateName: String): String {
                val path = "templates/$templateName.html"
                val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
                    ?: error("템플릿 리소스를 찾을 수 없습니다: $path")
                return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

            describe("[SiteAdmin-1] siteMngLayout.scala.html 사이드바 업데이트 알림 배지 (#210)") {
                it("업데이트가 필요하면 사이드바 update 항목에 notification-badge가 노출되어야 한다") {
                    setUpdateState(true, "9.9.9")
                    val result = mockMvc.perform(
                        get("/sites/userList").with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    ).andExpect(status().isOk).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val updateLink = doc.select("ul.site-setting-nav a[href=/sites/update]").first()
                    updateLink shouldNotBe null
                    updateLink!!.select("span.notification-badge").isEmpty() shouldBe false
                }

                it("업데이트가 필요하지 않으면 배지가 노출되지 않아야 한다") {
                    setUpdateState(false, null)
                    val result = mockMvc.perform(
                        get("/sites/userList").with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    ).andExpect(status().isOk).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val updateLink = doc.select("ul.site-setting-nav a[href=/sites/update]").first()
                    updateLink!!.select("span.notification-badge").isEmpty() shouldBe true
                }
            }

            describe("[SiteAdmin-2] <title> 메시지 키 정합성 (data/diagnostic/postList/issueList/userList/projectList/update, #211~220)") {
                it("data 화면은 legacy가 넘기는 title.siteSetting('사이트 설정')을 <title>에 써야 한다") {
                    val result = mockMvc.perform(
                        get("/sites/data").with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    ).andExpect(status().isOk).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    // 수정 전에는 head(#{site.sidebar.data}) = "데이터"를 <title>에 썼다(legacy는 title.siteSetting="사이트 설정").
                    doc.title().contains("사이트 설정") shouldBe true
                }

                it("projectList 화면은 legacy가 넘기는 title.projectList('프로젝트 목록')를 <title>에 써야 한다") {
                    val result = mockMvc.perform(
                        get("/sites/projectList").with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    ).andExpect(status().isOk).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    // 수정 전에는 head('프로젝트 관리')로 하드코딩돼 있었다(legacy는 title.projectList="프로젝트 목록").
                    doc.title().contains("프로젝트 목록") shouldBe true
                }

                it("userList/issueList/postList/update 화면도 title.siteSetting('사이트 설정')을 <title>에 써야 한다") {
                    for (path in listOf("/sites/userList", "/sites/issueList", "/sites/postList", "/sites/update")) {
                        val result = mockMvc.perform(
                            get(path).with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                        ).andExpect(status().isOk).andReturn()
                        val doc = Jsoup.parse(result.response.contentAsString)
                        withClue(path) {
                            doc.title().contains("사이트 설정") shouldBe true
                        }
                    }
                }
            }

            describe("[SiteAdmin-3] div 태그 균형 회귀 (data/massMail/postList의 스트레이 </div> 버그, #211/#216/#219)") {
                it("data/massMail/postList 응답 HTML의 <div 개수와 </div> 개수가 일치해야 한다") {
                    for (path in listOf("/sites/data", "/sites/massmail", "/sites/postList")) {
                        val result = mockMvc.perform(
                            get(path).with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                        ).andExpect(status().isOk).andReturn()
                        val body = result.response.contentAsString
                        val opens = Regex("<div[ >]").findAll(body).count()
                        val closes = Regex("</div>").findAll(body).count()
                        withClue("$path 의 <div 개수($opens) != </div> 개수($closes)") {
                            opens shouldBe closes
                        }
                    }
                }
            }

            describe("[SiteAdmin-4] 사이트 관리자 목록 화면 페이지네이션 위젯 정합성 (userList/projectList/issueList, #217/#218/#220)") {
                it("legacy처럼 yobi.Pagination.js 클라이언트 위젯용 div#pagination을 쓰고, yona 독자 서버사이드 ul.pagination을 쓰지 않아야 한다") {
                    for (path in listOf("/sites/userList", "/sites/projectList", "/sites/issueList")) {
                        val result = mockMvc.perform(
                            get(path).with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                        ).andExpect(status().isOk).andReturn()
                        val doc = Jsoup.parse(result.response.contentAsString)
                        withClue(path) {
                            doc.select("div#pagination").isEmpty() shouldBe false
                            doc.select("ul.pagination").isEmpty() shouldBe true
                            result.response.contentAsString.contains("yobi.Pagination.update") shouldBe true
                            result.response.contentAsString.contains("\"paramNameForPage\": \"page\"") shouldBe true
                        }
                    }
                }
            }

            describe("[SiteAdmin-5] site/setting.html 신규 이식 (legacy 죽은 TODO 스텁, #213)") {
                it("legacy site/setting.scala.html과 동일하게 siteMngLayout 뼈대(gnb/breadcrumb/sidebar/footer/scripts 조각)를 조합하고 본문엔 TODO 텍스트만 담아야 한다") {
                    val html = readTemplateResource("site/setting")
                    html.contains("site/layout :: head") shouldBe true
                    html.contains("site/layout :: gnb") shouldBe true
                    html.contains("site/layout :: breadcrumb") shouldBe true
                    html.contains("site/layout :: sidebar(") shouldBe true
                    html.contains("site/layout :: footer") shouldBe true
                    html.contains("site/layout :: scripts") shouldBe true

                    val doc = Jsoup.parse(html)
                    doc.select("div.span10").text().trim() shouldBe "TODO"
                }
            }

            describe("[SiteAdmin-6] partial_pagination.html / partial_paginationForUserList.html 신규 이식 (legacy 죽은 파샬, #222/#223)") {
                // 파라미터화된 프래그먼트(pagination(page, pageNumWindow, divId[, listUrl]))는 th:replace의
                // ~{...} 프래그먼트 표현식을 통해서만 인자 바인딩이 이뤄지므로, src/test/resources/templates/site/
                // __test_partial_*_wrapper.html(테스트 전용 픽스처)로 감싸 렌더링한다.
                it("partial_pagination: 전체 페이지 수가 창(window)보다 작으면 1..N 번호가 모두 렌더링되고 현재 페이지가 active여야 한다") {
                    val page = PageImpl<Any>(emptyList(), PageRequest.of(1, 1), 3) // 0-based index=1 → 2페이지, 총 3페이지
                    val context = Context()
                    context.setVariable("p", page)
                    context.setVariable("window", 5)
                    context.setVariable("divId", "testDiv")
                    context.setVariable("listUrl", "/sites/userList")
                    val html = templateEngine.process("site/__test_partial_pagination_wrapper", context)
                    val doc = Jsoup.parse(html)
                    val div = doc.select("div#testDiv.pagination").first()
                    div shouldNotBe null
                    val links = div!!.select("li a").map { it.text() }
                    links shouldBe listOf("Prev", "1", "2", "3", "Next")
                    div.select("li.active a").text() shouldBe "2"
                    div.select("li.disabled").isEmpty() shouldBe true
                }

                it("partial_pagination: 첫 페이지에서는 Prev가 disabled 상태여야 한다") {
                    val page = PageImpl<Any>(emptyList(), PageRequest.of(0, 1), 3)
                    val context = Context()
                    context.setVariable("p", page)
                    context.setVariable("window", 5)
                    context.setVariable("divId", "testDiv2")
                    context.setVariable("listUrl", "/sites/userList")
                    val html = templateEngine.process("site/__test_partial_pagination_wrapper", context)
                    val doc = Jsoup.parse(html)
                    doc.select("li.disabled").text() shouldBe "Prev"
                }

                it("partial_paginationForUserList: 링크가 legacy pageNum 대신 yona의 1-based page 파라미터로 /sites/userList를 가리켜야 한다") {
                    val page = PageImpl<Any>(emptyList(), PageRequest.of(1, 1), 3)
                    // 이 파샬은 @{/sites/userList(...)}처럼 URL 표현식을 쓰므로 일반 Context가 아니라
                    // IWebContext가 필요하다(그렇지 않으면 "cannot be context relative" 예외 발생).
                    val servletContext = MockServletContext()
                    val webApplication = JakartaServletWebApplication.buildApplication(servletContext)
                    val exchange = webApplication.buildExchange(MockHttpServletRequest(servletContext), MockHttpServletResponse())
                    val context = WebContext(exchange, Locale.KOREAN)
                    context.setVariable("p", page)
                    context.setVariable("window", 5)
                    context.setVariable("divId", "testDiv3")
                    val html = templateEngine.process("site/__test_partial_paginationForUserList_wrapper", context)
                    val doc = Jsoup.parse(html)
                    val activeHref = doc.select("li.active a").attr("href")
                    activeHref shouldBe "/sites/userList?page=2"
                }
            }

            describe("[SiteAdmin-7] user/lostPassword.html 독자 페이지 버그 수정 (#221)") {
                it("legacy siteLayout처럼 전체 GNB/footer를 포함해야 한다(기존엔 독자 <head>뿐이었음)") {
                    val result = mockMvc.perform(get("/lostPassword")).andExpect(status().isOk).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("header.gnb-outer").isEmpty() shouldBe false
                    doc.select("footer.page-footer-outer").isEmpty() shouldBe false
                    doc.title().contains("비밀번호 재") shouldBe true
                }

                it("아이디/이메일이 일치하지 않으면 site.resetPasswordEmail.invalidRequest 메시지를 노출해야 한다") {
                    val result = mockMvc.perform(
                        post("/lostPassword")
                            .param("loginId", "no-such-user-13")
                            .param("emailAddress", "nope@yona.io")
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                    ).andExpect(status().isOk).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("div.alert-error").text().contains("잘못된 비밀번호") shouldBe true
                }
            }
        }
    }
}
