package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class HelpControllerSpec : DescribeSpec({
    val controller = HelpController()
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    describe("HelpController 단위 테스트") {
        describe("GET /_help") {
            it("도움말 뷰와 title 모델 데이터를 성공적으로 반환해야 한다") {
                mockMvc.perform(get("/_help"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("help/toc"))
                    .andExpect(model().attribute("title", "도움말"))
            }
        }

        describe("GET /_UIKit") {
            it("UIKit UI 컴포넌트 데모 뷰를 성공적으로 반환해야 한다") {
                mockMvc.perform(get("/_UIKit"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("help/UIKit"))
            }
        }
    }
})
