package dev.yona.observability

import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import org.springframework.core.env.Environment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 시스템 지표와 상태 확인 묶음(로드맵 X-10 C5a, `decisions.md` D-64 5번)을 검증한다: 톰캣 스레드 풀 지표를 켜고, 데이터 디렉터리 디스크 용량을
 * 지표로 내고, readiness는 DB와 데이터 디렉터리를 보며 메일과 LDAP, 스트리밍 대상은 보지 않는다.
 */
class ObservabilitySystemTest : AbstractIntegrationTest() {

    @Autowired private lateinit var registry: MeterRegistry
    @Autowired private lateinit var env: Environment
    @Autowired private lateinit var groups: HealthEndpointGroups

    @Test
    fun `톰캣 스레드 풀 지표를 켠다`() {
        assertEquals("true", env.getProperty("server.tomcat.mbeanregistry.enabled"))
    }

    @Test
    fun `데이터 디렉터리 디스크 지표가 있다`() {
        val total = registry.find("yona.storage.disk.total").gauge()
        val free = registry.find("yona.storage.disk.free").gauge()
        assertNotNull(total); assertNotNull(free)
        assertTrue(total.value() > 0 && free.value() in 0.0..total.value(), "total=${total.value()} free=${free.value()}")
        assertEquals("bytes", total.id.baseUnit)
    }

    @Test
    fun `readiness는 DB와 데이터 디렉터리를 보고 메일과 LDAP은 보지 않는다`() {
        val readiness = groups.get("readiness")
        assertNotNull(readiness)
        assertTrue(readiness.isMember("db") && readiness.isMember("dataDirectory") && readiness.isMember("readinessState"))
        assertTrue(listOf("mail", "ldap", "diskSpace").none { readiness.isMember(it) })
    }

    @Test
    fun `liveness는 프로세스 상태만 본다`() {
        val liveness = groups.get("liveness")
        assertNotNull(liveness)
        assertTrue(liveness.isMember("livenessState") && !liveness.isMember("db") && !liveness.isMember("dataDirectory"))
    }

    @Test
    fun `지표 태그 이름에 Prometheus 예약 레이블이 없다`() {
        // OTLP를 Prometheus로 받으면 서비스 이름이 job, 인스턴스가 instance 레이블이 된다. 같은 이름의 태그는 사라지거나 덮어써진다(실제로 job 태그가 사라졌다)
        val reserved = setOf("job", "instance", "le", "quantile")
        val offending = registry.meters.filter { it.id.name.startsWith("yona.") }.flatMap { m -> m.id.tags.map { it.key } }.filter { it in reserved }.toSet()
        assertEquals(emptySet(), offending)
    }

    @Autowired private lateinit var context: org.springframework.context.ApplicationContext

    @Test
    fun `스프링 시큐리티의 임시 사용자와 비밀번호를 만들지 않는다`() {
        // 만들면 "Using generated security password"가 WARN 로그로 남아 OTLP 로그로 수집기에 나간다
        assertEquals(0, context.getBeanNamesForType(org.springframework.security.core.userdetails.UserDetailsService::class.java).size)
    }
}
