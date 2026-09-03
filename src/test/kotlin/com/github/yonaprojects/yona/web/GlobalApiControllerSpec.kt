package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

// yona controllers/api/GlobalApi.java hello() 대응 (P2-54)
class GlobalApiControllerSpec : DescribeSpec({
    val controller = GlobalApiController()
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    describe("GET /-_-api/v1/hello") {
        it("legacy와 동일한 헬스체크 응답을 반환한다") {
            mockMvc.perform(get("/-_-api/v1/hello"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.message").value("I'm alive!"))
                .andExpect(jsonPath("$.ok").value(true))
        }
    }
})
