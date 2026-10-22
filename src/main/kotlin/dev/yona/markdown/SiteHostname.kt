package dev.yona.markdown

import dev.yona.config.YonaProperties
import org.springframework.stereotype.Component
import java.net.InetAddress

/**
 * 이 사이트의 호스트 이름이다. v1.6 `utils.Config.getHostname()`에 해당한다.
 *
 * 설정 `yona.hostname`이 있으면 그 값을, 없으면 서버의 기본 호스트 이름을 쓰고, 알 수 없으면 `localhost`이다.
 */
@Component
class SiteHostname(private val properties: YonaProperties) {

    /** 호스트 이름이다. */
    fun name(): String {
        if (properties.hostname.isNotEmpty()) return properties.hostname
        return try {
            InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "localhost"
        }
    }
}
