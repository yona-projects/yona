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

// code/view.html에서 발견된 것과 동일한 유형(P0-27)의 버그가 code/history.html, code/compare.html,
// code/compare_svn.html에도 그대로 있었음 — 전수 감사(사용자 요청)에서 기계적으로 확인:
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
            // 경로라 반쪽짜리 bare 저장소가 남을 수 있음, 실측 확인 2026-09-07) — HEAD 파일
            // 존재로 실제 초기화 완료 여부를 판단한다.
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
