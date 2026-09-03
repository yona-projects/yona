package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.filter.CharacterEncodingFilter
import io.mockk.clearMocks
import org.springframework.test.web.servlet.result.MockMvcResultHandlers

class MarkdownControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val markdownService = mockk<MarkdownService>()

    val markdownController = MarkdownController(
        projectRepository,
        markdownService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(markdownController)
        .addFilters<StandaloneMockMvcBuilder>(
            CharacterEncodingFilter("UTF-8", true)
        )
        .build()

    beforeTest {
        clearMocks(projectRepository, markdownService)
    }

    describe("MarkdownController 마크다운 렌더링 API TDD 검증") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner")

        it("마크다운 렌더링 API 요청 시 200 OK와 렌더링된 HTML을 반환해야 한다 (TDD Red 예상)") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
            every { markdownService.render("## 테스트 마크다운", true, project) } returns "<h2>테스트 마크다운</h2>\n"

            val requestJson = """
                {
                    "body": "## 테스트 마크다운",
                    "breaks": true
                }
            """.trimIndent()

            mockMvc.perform(
                post("/markdown/owner/TestProject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson)
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk)
                .andExpect(content().string("<h2>테스트 마크다운</h2>\n"))
        }

        // projectRepository.findByOwnerAndNameOrPreviousPlace(...) ?: return notFound() 엘비스의
        // null 분기 — 존재하지 않는 프로젝트로 요청하면 404를 반환하고 markdownService는 호출되지
        // 않아야 한다.
        it("존재하지 않는 프로젝트로 요청하면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

            val requestJson = """
                {
                    "body": "## 테스트 마크다운",
                    "breaks": true
                }
            """.trimIndent()

            mockMvc.perform(
                post("/markdown/owner/nosuch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson)
            )
                .andExpect(status().isNotFound)

            io.mockk.verify(exactly = 0) { markdownService.render(any(), any(), any()) }
        }
    }

    describe("MarkdownRenderRequest") {
        it("breaks 기본값은 true여야 하고, data class 자동생성 메서드가 정상 동작해야 한다") {
            val default = MarkdownRenderRequest(body = "# 제목")
            default.breaks shouldBe true

            val a = MarkdownRenderRequest(body = "본문", breaks = false)
            val b = MarkdownRenderRequest(body = "본문", breaks = false)
            val c = a.copy(breaks = true)

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
            (a == c) shouldBe false
            c.breaks shouldBe true
            a.component1() shouldBe "본문"
            a.component2() shouldBe false
            a.toString() shouldContain "본문"
        }
    }
})
