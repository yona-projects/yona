package dev.yona.domain.sso

import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * IdP 메타데이터 주소에서 XML을 내려받는다(X-16). 사이트 관리자가 정한 주소만 받으며 https만 허용한다(개발용 루프백만 http). 응답은 1 MiB까지만 읽고 시간 제한이 있으며
 * 다른 주소로의 이동은 따르지 않는다. 사내 IdP를 쓰는 곳이 많아 사설 주소는 막지 않는다([OidcDiscoveryClient]와 같다).
 */
@Component
class Saml2MetadataFetcher {

    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()

    /** [url]의 본문이다. 주소가 맞지 않거나 받지 못하면 `null`이다. */
    fun fetch(url: String): String? {
        val uri = runCatching { URI.create(url.trim()) }.getOrNull() ?: return null
        val loopback = uri.host == "localhost" || uri.host == "127.0.0.1" || uri.host == "[::1]"
        if (!(uri.scheme == "https" || (uri.scheme == "http" && loopback)) || uri.host.isNullOrEmpty()) return null
        return try {
            val response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Accept", "application/samlmetadata+xml, application/xml, text/xml").GET().build(), HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() != 200) return null
            response.body().use { it.readNBytes(MAX_BYTES + 1) }.takeIf { it.size <= MAX_BYTES }?.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private companion object { const val MAX_BYTES = 1024 * 1024 }
}
