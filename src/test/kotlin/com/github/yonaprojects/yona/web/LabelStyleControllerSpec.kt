package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*
import io.mockk.clearMocks
import org.hamcrest.Matchers

class LabelStyleControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val issueLabelRepository = mockk<IssueLabelRepository>()

    val controller = LabelStyleController(projectRepository, issueLabelRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(projectRepository, issueLabelRepository)
    }

    describe("LabelStyleController 단위 테스트") {
        val project = Project(id = 10L, name = "testproject", owner = "testowner")
        val category = mockk<IssueLabelCategory>()

        describe("GET /{user}/{projectName}/issue/labels.css") {
            it("라벨 목록에 대해 올바른 CSS 콘텐츠를 생성하여 반환해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "#ff0000", name = "Bug", project = project)
                val label2 = IssueLabel(id = 2L, category = category, color = "#ffffff", name = "Question", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1, label2)

                val eTag = "\"${listOf(label1, label2).map { "${it.id}-${it.color}" }.hashCode()}\""

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("text/css;charset=UTF-8"))
                    .andExpect(header().string("ETag", eTag))
                    .andExpect(content().string(Matchers.containsString(".issue-label[data-label-id=\"1\"]")))
                    .andExpect(content().string(Matchers.containsString("box-shadow: inset 2px 0 0px #ff0000;")))
                    .andExpect(content().string(Matchers.containsString("color: white;"))) // #ff0000는 어두우므로 white
                    .andExpect(content().string(Matchers.containsString("color: dimgray;"))) // #ffffff는 밝으므로 dimgray
            }

            it("If-None-Match가 헤더 ETag와 같을 때 304 Not Modified를 반환해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "#ff0000", name = "Bug", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                val eTag = "\"${listOf(label1).map { "${it.id}-${it.color}" }.hashCode()}\""

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                        .header("If-None-Match", eTag)
                )
                    .andExpect(status().isNotModified)
                    .andExpect(header().string("ETag", eTag))
            }

            it("If-None-Match 헤더가 있지만 현재 ETag와 다르면 304가 아닌 200을 반환해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "#ff0000", name = "Bug", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                        .header("If-None-Match", "\"stale-etag\"")
                )
                    .andExpect(status().isOk)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("nosuchowner", "nosuchproject") } returns Optional.empty()

                mockMvc.perform(
                    get("/nosuchowner/nosuchproject/issue/labels.css")
                ).andExpect(status().isNotFound)
            }

            it("라벨이 하나도 없으면 빈 CSS 본문과 함께 200을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns emptyList()

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(""))
            }

            it("rgb(...) 형식의 색상은 파싱해서 밝기에 따라 텍스트 색을 결정해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "rgb(0,0,0)", name = "Black", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("color: white;"))) // 검정 배경 -> 밝은 텍스트
            }

            it("rgb(...) 형식이지만 숫자 파싱에 실패하면 기본 흰색(255,255,255)으로 처리해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "rgb(a,b,c)", name = "Broken", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("color: dimgray;"))) // 기본값(흰색)은 밝으므로 어두운 텍스트
            }

            it("3자리 축약 헥스 색상(#f00 형태)도 파싱해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "#f00", name = "ShortHex", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("color: white;")))
            }

            it("길이가 4자리도 7자리도 아닌 헥스 형태 문자열은 기본 흰색으로 처리해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "#12345", name = "WeirdHex", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("color: dimgray;")))
            }

            it("#으로 시작하지 않는 순수 헥스 문자열도 #을 붙여 파싱해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "ff0000", name = "BareHex", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("color: white;")))
            }

            it("rgb도 헥스도 아닌 색상 문자열은 기본 흰색으로 처리해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "notacolor", name = "Named", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("color: dimgray;")))
            }
        }
    }
})
