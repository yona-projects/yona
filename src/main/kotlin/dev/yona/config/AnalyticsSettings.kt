package dev.yona.config

import dev.yona.repository.DataDirectory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.util.UUID

/**
 * 사용 현황 집계(GA4) 설정을 화면에 알려 준다. v1.6에 없는 추가 항목이다 (로드맵 X-21, `decisions.md` A-186).
 *
 * 브라우저 태그(`gtag`)만 쓰며 서버가 GA로 직접 보내는 이벤트는 없다. 템플릿은 `@analyticsSettings`로 이 빈을 읽는다.
 * v1.6의 UA 태그(`yona.send-yona-usage`)와는 별개이다.
 */
@Component
class AnalyticsSettings @Autowired constructor(private val properties: YonaProperties, private val dataDirectory: DataDirectory) {

    /** 단위 시험용이다. 설정만으로 데이터 디렉터리를 정한다. */
    constructor(properties: YonaProperties) : this(properties, DataDirectory(properties))

    /** GA4 측정 ID이다. */
    val measurementId: String get() = properties.analytics.measurementId.trim()

    /** 켜져 있고 측정 ID가 있을 때만 `true`이다. 이때만 태그를 낸다. */
    val clientEnabled: Boolean get() = properties.analytics.sendUsage && measurementId.isNotEmpty()

    /**
     * 설치를 구분하는 무작위 UUID이다. 처음 한 번 만들어 파일에 저장하고 다시 쓴다. 호스트명, IP, 사용자 정보와 관계없는 값이다.
     */
    val installId: String by lazy {
        val configured = properties.analytics.installIdPath
        val file = if (configured.isNotEmpty()) File(configured) else File(File(dataDirectory.root(), "analytics"), "install-id")
        file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also {
                file.parentFile?.mkdirs()
                file.writeText(it)
            }
    }
}
