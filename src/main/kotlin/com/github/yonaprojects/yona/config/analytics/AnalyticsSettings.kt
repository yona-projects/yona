package com.github.yonaprojects.yona.config.analytics

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
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
    private val logger = LoggerFactory.getLogger(AnalyticsSettings::class.java)

    val clientEnabled: Boolean get() = sendUsage && measurementId.isNotBlank()

    // 최초 1회 무작위 UUID를 만들어 yona.data 아래에 저장하고 이후 재사용한다(JwkKeyPairProvider와
    // 같은 패턴). 호스트명·IP·사용자 정보와 무관한 값이라 설치 인스턴스를 구분하는 데만 쓴다.
    // 저장 경로를 쓸 수 없어도 예외를 던지지 않는다. 이 값은 템플릿 렌더링 중에 읽히므로 예외가 나면
    // 태그가 켜진 모든 페이지가 실패한다. 대신 이번 실행에서만 쓸 UUID를 돌려주고 경고를 남긴다.
    val installId: String by lazy {
        val file = File(installIdPath)
        try {
            file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
                ?: UUID.randomUUID().toString().also {
                    file.parentFile?.mkdirs()
                    file.writeText(it)
                }
        } catch (e: IOException) {
            logger.warn("analytics install-id를 $installIdPath 에 읽거나 저장하지 못해 이번 실행 동안만 쓰는 임시 값을 사용합니다: ${e.message}")
            UUID.randomUUID().toString()
        }
    }
}
