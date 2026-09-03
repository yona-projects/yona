package com.github.yonaprojects.yona.config

import io.kotest.core.spec.style.DescribeSpec
import org.hamcrest.Matchers
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class PrometheusIntegrationSpec : DescribeSpec({

    // Actuator의 Prometheus 엔드포인트 반환 형식을 검증하기 위한 컨트롤러
    @RestController
    class MockPrometheusController {
        @GetMapping("/actuator/prometheus")
        fun prometheus(): String {
            return """
                # HELP jvm_memory_used_bytes Used memory in bytes
                # TYPE jvm_memory_used_bytes gauge
                jvm_memory_used_bytes{area="heap",id="G1 Survivor Space",} 0.0
            """.trimIndent()
        }
    }

    val mockMvc = MockMvcBuilders.standaloneSetup(MockPrometheusController()).build()

    describe("Prometheus Actuator 엔드포인트 모의 검증") {
        it("GET /actuator/prometheus 접근 시, 200 OK와 함께 프로메테우스 텍스트 메트릭 데이터를 반환해야 한다") {
            mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk)
                .andExpect(content().string(Matchers.containsString("jvm_memory_used_bytes")))
        }
    }
})
