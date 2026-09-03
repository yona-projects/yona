package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Label
import com.github.yonaprojects.yona.domain.project.LabelRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageRequest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import io.mockk.clearMocks

class LabelControllerSpec : DescribeSpec({
    val labelRepository = mockk<LabelRepository>()
    val labelController = LabelController(labelRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(labelController).build()

    beforeTest {
        clearMocks(labelRepository)
    }

    describe("LabelController 자동완성 API 테스트") {

        describe("GET /labels") {
            it("[Test-15-1-1] limit 파라미터가 없으면 400 Bad Request를 반환해야 한다") {
                mockMvc.perform(get("/labels").param("query", "test"))
                    .andExpect(status().isBadRequest)
            }

            it("[Test-15-1-2] limit 파라미터가 주어지면 조건에 매칭되는 라벨 목록을 200 OK로 반환해야 한다") {
                val label1 = Label(id = 1L, category = "issue", name = "bug")
                val label2 = Label(id = 2L, category = "issue", name = "feature")

                every { labelRepository.countByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase("issue", "bu") } returns 1L
                every {
                    labelRepository.findByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase(
                        "issue",
                        "bu",
                        PageRequest.of(0, 10)
                    )
                } returns listOf(label1)

                mockMvc.perform(
                    get("/labels")
                        .param("category", "issue")
                        .param("query", "bu")
                        .param("limit", "10")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.size()").value(1))
                    .andExpect(jsonPath("$[0]").value("bug"))
            }

            it("limit이 최대치(1000)를 초과하면 1000으로 clamp되어야 한다") {
                every { labelRepository.countByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase("", "") } returns 0L
                every {
                    labelRepository.findByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase("", "", PageRequest.of(0, 1000))
                } returns emptyList()

                mockMvc.perform(get("/labels").param("limit", "5000"))
                    .andExpect(status().isOk)
            }

            it("전체 개수가 limit보다 많으면 Content-Range 헤더를 포함해야 한다") {
                val label1 = Label(id = 1L, category = "issue", name = "bug")
                every { labelRepository.countByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase("", "") } returns 5L
                every {
                    labelRepository.findByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase("", "", PageRequest.of(0, 1))
                } returns listOf(label1)

                mockMvc.perform(get("/labels").param("limit", "1"))
                    .andExpect(status().isOk)
                    .andExpect(MockMvcResultMatchers.header().string("Content-Range", "items 1/5"))
            }
        }

        describe("GET /categories") {
            it("[Test-15-2-1] limit 파라미터가 없으면 400 Bad Request를 반환해야 한다") {
                mockMvc.perform(get("/categories"))
                    .andExpect(status().isBadRequest)
            }

            it("[Test-15-2-2] limit 파라미터가 주어지면 중복을 제거한 DISTINCT 카테고리 목록을 200 OK로 반환해야 한다") {
                every { labelRepository.countDistinctCategories() } returns 2L
                every { labelRepository.findDistinctCategories(PageRequest.of(0, 5)) } returns listOf("issue", "release")

                mockMvc.perform(
                    get("/categories")
                        .param("limit", "5")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.size()").value(2))
                    .andExpect(jsonPath("$[0]").value("issue"))
                    .andExpect(jsonPath("$[1]").value("release"))
            }

            it("query가 주어지면 DISTINCT 카테고리를 검색어로 필터링해야 한다") {
                every { labelRepository.countDistinctCategoriesContaining("is") } returns 1L
                every { labelRepository.findDistinctCategoriesContaining("is", PageRequest.of(0, 5)) } returns listOf("issue")

                mockMvc.perform(
                    get("/categories")
                        .param("limit", "5")
                        .param("query", "is")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.size()").value(1))
                    .andExpect(jsonPath("$[0]").value("issue"))
            }

            it("limit이 최대치(1000)를 초과하면 1000으로 clamp되어야 한다") {
                every { labelRepository.countDistinctCategories() } returns 0L
                every { labelRepository.findDistinctCategories(PageRequest.of(0, 1000)) } returns emptyList()

                mockMvc.perform(get("/categories").param("limit", "5000"))
                    .andExpect(status().isOk)
            }

            it("전체 개수가 limit보다 많으면 Content-Range 헤더를 포함해야 한다") {
                every { labelRepository.countDistinctCategories() } returns 5L
                every { labelRepository.findDistinctCategories(PageRequest.of(0, 1)) } returns listOf("issue")

                mockMvc.perform(get("/categories").param("limit", "1"))
                    .andExpect(status().isOk)
                    .andExpect(MockMvcResultMatchers.header().string("Content-Range", "items 1/5"))
            }
        }
    }
})
