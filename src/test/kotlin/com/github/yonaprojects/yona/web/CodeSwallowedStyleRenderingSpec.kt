package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

// code/view.html에서 발견된 것과 동일한 유형의 버그가 code/history.html, code/compare.html,
// code/compare_svn.html에도 그대로 있었다:
// `<head th:replace="~{site/layout :: head(...)}"> <style>...</style> </head>` 형태로 페이지 전용
// <style>을 th:replace가 통째로 치환하는 <head> 태그 "안"에 둬서 한 번도 렌더링되지 않는 죽은
// 코드였다. 세 파일 모두 `.code-browse-wrap` 카드 배경/테두리만 잃는 순수 시각적 문제(다행히
// code/view.html의 `.list-wrap{display:none}`처럼 콘텐츠 자체가 숨겨지는 치명적 케이스는 아님).
// 추가로 code/compare.html/compare_svn.html의 `.commitId`는 공용 yobi.css의 더 구체적인
// `.code-browse-wrap .commitId`(색상 다름)에 명시도로 밀리므로 스코프도 함께 필요.
class CodeSwallowedStyleRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
    @Value("\${yona.git.base-dir:/tmp/yona/git}") private val gitBaseDir: String
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        }

        describe("code/history.html, code/compare.html 페이지 전용 CSS 실제 렌더링") {
            val owner = userRepository.findByLoginId("css-owner").orElseGet {
                userRepository.save(User(loginId = "css-owner", name = "코드스타일오너", email = "css-owner@yona.io"))
            }
            val project = projectRepository.findAll().find { it.name == "css-proj" } ?: projectRepository.save(
                Project(name = "css-proj", owner = "css-owner", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
            )
            val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
            // CodeBrowserListWrapRenderingSpec과 동일한 이유(gitBaseDir가 세션을 넘나드는 고정
            // 경로라 반쪽짜리 bare 저장소가 남을 수 있음) — HEAD 파일 존재로 실제 초기화 완료
            // 여부를 판단한다.
            if (!File(gitDir, "HEAD").exists()) {
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDir).commitTextFile("README.md", "# css-proj", "첫 커밋")
                BareCommit(project, owner, gitBaseDir).commitTextFile("README.md", "# css-proj v2", "두번째 커밋")
            }

            it("code/history.html: 페이지 전용 .code-browse-wrap 스타일이 실제 응답 본문에 렌더링돼야 한다") {
                val body = mockMvc.perform(get("/${project.owner}/${project.name}/commits/main"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain ".code-browse-wrap {"
            }

            it("code/compare.html: 페이지 전용 .code-browse-wrap 스타일과 .commitId 스코프가 실제 응답 본문에 렌더링돼야 한다") {
                val body = mockMvc.perform(get("/${project.owner}/${project.name}/compare/main..main"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain ".code-browse-wrap {"
                body shouldContain ".code-browse-wrap .commitId {"
            }

            // code/compare.html·code/diff.html의 인라인 댓글 폼은 Thymeleaf가 아니라 JS 템플릿
            // 문자열로 런타임에 DOM에 삽입되는 진짜 네이티브 <form>이다(.submit()을 가로채는 JS
            // 핸들러가 전혀 없음 — 클릭 시 브라우저가 그대로 전체 페이지 POST 내비게이션을
            // 수행하고, 수신측 ReviewViewController도 "redirect:..."를 반환하는 고전적
            // POST-Redirect-GET이라 th:action 자동 주입이 적용될 수 없다). 그래서 Thymeleaf의
            // _csrf 요청 attribute 값을 JS 변수로 직접 심어 히든 필드를 수동 채운다 — 캐치올 체인이
            // CSRF를 활성화했으므로(SecurityConfig) 실제 서버 필터 체인을 태우면 이 값이 채워져야
            // 한다(webAppContextSetup, springSecurity() 미적용 — 이 파일은 필터 체인 없이
            // DispatcherServlet만 태우지만 CsrfFilter 없이도 컨트롤러 코드 자체가 "_csrf가 있으면
            // 그 값을 그대로 쓴다"는 걸 검증하기엔 충분하다).
            it("code/compare.html: _csrf 요청 attribute 값이 인라인 댓글 폼 히든 필드 JS 변수에 그대로 노출돼야 한다") {
                val csrfToken = org.springframework.security.web.csrf.DefaultCsrfToken(
                    "X-XSRF-TOKEN", "_csrf", "compare-view-csrf-test-token"
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/compare/main..main")
                        .requestAttr("_csrf", csrfToken)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "const csrfParameterName = \"_csrf\""
                body shouldContain "const csrfTokenValue = \"compare-view-csrf-test-token\""
            }

            it("code/diff.html: _csrf 요청 attribute 값이 인라인 댓글 폼 히든 필드 JS 변수에 그대로 노출돼야 한다") {
                val log = repositoryService.getRepository(project).getHistory(0, 10, "main", null)
                val headCommitId = log.first().getId()

                val csrfToken = org.springframework.security.web.csrf.DefaultCsrfToken(
                    "X-XSRF-TOKEN", "_csrf", "diff-view-csrf-test-token"
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/commit/$headCommitId")
                        .requestAttr("_csrf", csrfToken)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "const csrfParameterName = \"_csrf\""
                body shouldContain "const csrfTokenValue = \"diff-view-csrf-test-token\""
            }
        }

        // code/compare_svn.html은 SVN 저장소 통합환경 구성이 무거워 렌더링 통합테스트 대신, 같은
        // 버그를 정적 파일 텍스트로 직접 확인한다(GnbUserMenuDropdownColorSpec과 동일한 검증 방식).
        describe("code/compare_svn.html 정적 파일 감사") {
            val html = File("src/main/resources/templates/code/compare_svn.html").readText()

            it("페이지 전용 <style> 태그가 <head th:replace> 밖(body)에 있어야 한다") {
                val headEnd = html.indexOf("</head>")
                // 주석 본문에 "<style>" 문자열이 설명용으로 등장하므로, 실제 <style> 태그(뒤에 CSS
                // 규칙이 바로 이어지는 지점)만 찾기 위해 </head> 이후 구간에서 찾는다.
                val styleStart = html.indexOf("<style>", headEnd)
                (styleStart > headEnd) shouldBe true
            }

            it(".commitId가 legacy yobi.css의 더 구체적인 .code-browse-wrap .commitId에 밀리지 않게 스코프돼야 한다") {
                html shouldContain ".code-browse-wrap .commitId {"
            }
        }
    }
}
