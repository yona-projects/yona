package dev.yona.domain.site

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.PropertyName
import dev.yona.domain.misc.PropertyRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 서버 상태를 점검한다. 문제가 있으면 설명 문자열을, 없으면 빈 목록을 돌려준다. v1.6 `utils.Diagnostic`이다. */
fun interface DiagnosticCheck {
    fun check(): List<String>
}

/**
 * 등록된 점검을 모두 실행해 문제를 모은다. v1.6 `Diagnostic.checkAll`(`app/utils/Diagnostic.java:47-60`)에 해당한다.
 * 점검이 예외를 던지면 그 점검의 결과 대신 `Failed to diagnose: ` 와 스택 트레이스를 문제로 더한다.
 */
@Service
class DiagnosticService(private val checks: List<DiagnosticCheck>) {

    fun checkAll(): List<String> = checks.flatMap { check ->
        try {
            check.check()
        } catch (e: Exception) {
            listOf("Failed to diagnose: " + e.stackTraceToString())
        }
    }
}

/** 포트만 정하고 호스트 이름을 정하지 않은 설정을 알린다 (v1.6 `Config.onStart`의 점검). */
@Component
class ConfigDiagnostic(private val properties: YonaProperties) : DiagnosticCheck {
    override fun check(): List<String> =
        if (properties.port != null && properties.hostname.isEmpty()) listOf("application.port may be ignored because application.hostname is not configured.") else emptyList()
}

/** 같은 이름의 속성이 둘 이상 저장된 것을 알린다 (v1.6 `Property.onStart`의 점검). */
@Component
class PropertyDiagnostic(private val properties: PropertyRepository) : DiagnosticCheck {

    @Transactional(readOnly = true)
    override fun check(): List<String> = PropertyName.entries.mapNotNull { name ->
        val values = properties.findByName(name)
        if (values.size > 1) "Property '$name' has duplicated values: ${values.map { it.value }}" else null
    }
}
