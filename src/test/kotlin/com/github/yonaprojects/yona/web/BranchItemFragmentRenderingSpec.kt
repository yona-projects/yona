package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.config.TemplateHelper
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.WebApplicationContext
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.WebContext
import org.thymeleaf.web.servlet.JakartaServletWebApplication
import java.util.Locale

// P3-48 화면별 재현 세션에서 발견: code/svnDiff.html이 유일하게 사용하는
// common/branchItem.html 프래그먼트가 "@templateHelper.branchInHtml(branch)"/
// "@templateHelper.branchItemName(branch)"처럼 SpEL 빈 참조 문법(@)으로 TemplateHelper를
// 호출하고 있었다 — 이 저장소의 다른 모든 화면(code/history.html 등)은 전부
// "${templateHelper.xxx(...)}"(GlobalModelAttributeAdvice가 매 요청 모델에 얹어주는 일반
// 모델 속성)으로 호출하는 것과 다른 방식이었다. th:each로 반복되는 fragment 호출 안에서 "@" 빈
// 참조 SpEL을 쓰면 Thymeleaf가 "Instantiation of new objects and access to static classes or
// parameters is forbidden in this context"라는 TemplateProcessingException을 던진다 — 실제로
// Playwright/curl로 SVN 프로젝트의 커밋 상세 화면(브랜치가 하나라도 있으면 항상)을 열어보면
// 응답이 정확히 이 프래그먼트를 렌더링하는 지점에서 끊겨(net::ERR_INCOMPLETE_CHUNKED_ENCODING)
// 재현했다. 기존 CodeViewControllerSpec의 svnDiff 관련 테스트들은 전부 getRefNames()를
// emptyList()로 스텁해 th:each 자체가 한 번도 실행되지 않았고(그 스펙은 standaloneSetup이라
// Thymeleaf 렌더링 자체를 하지도 않음), 그래서 이 버그가 지금까지 발견되지 않았다.
//
// 이 스펙은 실제 서블릿 요청 없이도 진짜 Thymeleaf 엔진으로 이 프래그먼트 하나만 정확히
// 재현한다(JakartaServletWebApplication + Mock 요청/응답으로 IWebContext를 직접 구성 — @{...}
// 링크 표현식이 요구하는 최소 조건).
class BranchItemFragmentRenderingSpec @Autowired constructor(
    private val templateEngine: ITemplateEngine,
    private val templateHelper: TemplateHelper,
    private val wac: WebApplicationContext
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    init {
        describe("P3-48: common/branchItem 프래그먼트는 th:each 반복 안에서도 예외 없이 렌더링돼야 한다") {
            it("branchItem(project, branch, selected)는 TemplateProcessingException 없이 앵커 태그를 렌더링해야 한다") {
                val servletContext = wac.servletContext
                val webApplication = JakartaServletWebApplication.buildApplication(servletContext)
                val request = MockHttpServletRequest(servletContext)
                val response = MockHttpServletResponse()
                val webExchange = webApplication.buildExchange(request, response)

                val project = Project(id = 1L, owner = "branchitem-owner", name = "branchitem-proj", vcs = "SUBVERSION")
                val variables = mapOf<String, Any>(
                    "project" to project,
                    "branch" to "HEAD",
                    "selected" to true,
                    "templateHelper" to templateHelper
                )
                val context = WebContext(webExchange, Locale.KOREAN, variables)

                val html = templateEngine.process("common/branchItem", setOf("branchItem"), context)

                html.shouldContain("<a")
                html.shouldContain("HEAD")
            }
        }
    }
}
