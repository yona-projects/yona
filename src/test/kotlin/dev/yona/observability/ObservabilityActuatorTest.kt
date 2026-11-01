package dev.yona.observability

import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

/**
 * 관측성의 기본 상태(로드맵 X-10 C1, C2, `decisions.md` D-64)를 검증한다. 상태 확인은 `-_-` 이름 공간(v1.6의 `/:user` 라우트와 겹치지 않게)에서
 * 인증 없이 열고 상세를 숨기며, 그 밖의 엔드포인트는 열지 않는다. 수집기 주소가 없으면 어떤 신호도 내보내지 않는다.
 */
@AutoConfigureMockMvc
class ObservabilityActuatorTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var context: ApplicationContext

    @Test
    fun `상태 확인은 인증 없이 UP만 알리고 상세를 숨긴다`() {
        mockMvc.perform(get("/-_-actuator/health")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist())
    }

    @Test
    fun `liveness와 readiness 그룹이 있다`() {
        mockMvc.perform(get("/-_-actuator/health/liveness")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("UP"))
        mockMvc.perform(get("/-_-actuator/health/readiness")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `상태 확인 밖의 엔드포인트는 열지 않는다`() {
        listOf("env", "beans", "metrics", "prometheus", "loggers", "heapdump", "threaddump", "configprops", "mappings", "info", "conditions").forEach {
            val code = mockMvc.perform(get("/-_-actuator/$it")).andReturn().response.status
            assertTrue(code == 403 || code == 404, "엔드포인트 $it 이(가) 열려 있다: $code")
        }
    }

    @Test
    fun `기본 경로 actuator는 사용자 이름 라우트와 겹치지 않게 쓰지 않는다`() {
        val body = mockMvc.perform(get("/actuator/health")).andReturn().response
        assertTrue(!body.contentAsString.contains("\"status\":\"UP\""), "기본 경로에 상태 확인이 열려 있다")
    }

    @Test
    fun `수집기 주소가 없으면 OTLP 지표와 추적과 로그를 내보내는 장치가 없다`() {
        val registries = context.getBeansOfType(MeterRegistry::class.java).values.map { it.javaClass.simpleName }
        assertTrue(registries.none { it.contains("Otlp", ignoreCase = true) }, "OTLP 지표 레지스트리가 켜져 있다: $registries")
        val exporters = context.beanDefinitionNames.filter { it.contains("otlp", ignoreCase = true) && (it.contains("Exporter", true) || it.contains("Registry", true)) }
        assertTrue(exporters.isEmpty(), "OTLP 내보내기 장치가 켜져 있다: $exporters")
    }
}
