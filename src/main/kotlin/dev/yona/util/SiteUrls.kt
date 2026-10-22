package dev.yona.util

import dev.yona.config.YonaProperties
import org.springframework.stereotype.Component
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.net.InetAddress

/**
 * 요청 밖(스케줄러가 만드는 메일 등)에서 사이트의 절대 주소를 만든다. v1.6 `utils.Url.create(String)`과
 * `utils.Config.getScheme()`, `getHostport()`에 해당한다.
 *
 * 스킴은 설정 `yona.scheme`, 없으면 지금 처리 중인 요청의 스킴, 그것도 없으면 `http`이다. 호스트는 설정 `yona.hostname`(`yona.port`가
 * 80이 아니면 `:포트`를 붙인다), 없으면 요청의 `Host` 헤더, 그것도 없으면 서버의 주소이며 알 수 없으면 `localhost`이다. 서버를 하위 경로에 둔 경우 그 경로를 [path]가 붙여 준다.
 */
@Component
class SiteUrls(private val properties: YonaProperties) {

    /** 서버를 하위 경로에 둘 때의 경로 접두어이다(`/경로`). 없으면 빈 문자열이다. */
    val contextPath: String get() = properties.applicationContext.trim('/').let { if (it.isEmpty()) "" else "/$it" }

    /** 애플리케이션 경로에 컨텍스트 경로를 붙인다. 예: `/a/b/issue/1` -> `/yona/a/b/issue/1`. */
    fun path(applicationPath: String): String = contextPath + applicationPath

    /** `스킴://호스트[:포트]` 부분이다. */
    fun origin(): String = "${properties.scheme.ifEmpty { currentRequest()?.scheme ?: "http" }}://${hostport()}"

    /** 컨텍스트 경로가 이미 붙은 [path]로 절대 주소를 만든다. */
    fun absolute(path: String): String = origin() + path

    private fun currentRequest(): HttpServletRequest? = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun hostport(): String {
        if (properties.hostname.isNotEmpty()) {
            val port = properties.port
            return if (port != null && port != 80) "${properties.hostname}:$port" else properties.hostname
        }
        currentRequest()?.let { request -> request.getHeader("Host")?.let { return it } }
        return try {
            InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            "localhost"
        }
    }
}
