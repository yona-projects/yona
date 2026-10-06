package com.github.yonaprojects.yona.config.analytics

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.util.UUID

// 설치형 배포의 사용 현황 집계(GA4) 설정. 브라우저 태그(gtag)만 사용하며 서버에서 GA로 직접 보내는
// 이벤트는 없다. yona.analytics.send-usage=false이면 태그가 렌더링되지 않는다.
@Component
class AnalyticsSettings(
    @Value("\${yona.analytics.send-usage:true}") val sendUsage: Boolean,
    @Value("\${yona.analytics.measurement-id:}") val measurementId: String,
    @Value("\${yona.analytics.install-id-path:\${yona.data:data}/analytics/install-id}")
    private val installIdPath: String
) {
    val clientEnabled: Boolean get() = sendUsage && measurementId.isNotBlank()

    // 최초 1회 무작위 UUID를 만들어 yona.data 아래에 저장하고 이후 재사용한다(JwkKeyPairProvider와
    // 같은 패턴). 호스트명·IP·사용자 정보와 무관한 값이라 설치 인스턴스를 구분하는 데만 쓴다.
    val installId: String by lazy {
        val file = File(installIdPath)
        file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also {
                file.parentFile?.mkdirs()
                file.writeText(it)
            }
    }
}
