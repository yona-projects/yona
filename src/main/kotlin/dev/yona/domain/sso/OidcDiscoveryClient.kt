package dev.yona.domain.sso

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * OIDC 발견 문서(`<발급자>/.well-known/openid-configuration`)를 받아 필요한 값만 읽는다(X-16). 관리자가 설정을 저장할 때 한 번 쓰고 결과를 저장해 두므로 로그인마다 부르지 않는다.
 * 주소는 사이트 관리자만 정하며 사내 IdP를 쓰는 곳이 많아 사설 주소도 받는다. 응답은 1 MiB까지만 읽고 시간 제한이 있다.
 */
@Component
class OidcDiscoveryClient {

    /** 발견 결과이다. 성공하면 [discovery], 아니면 [error]가 있다. */
    data class Result(val discovery: OidcDiscovery? = null, val error: SsoSaveError? = null)

    private val mapper = JsonMapper.builder().build()
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()

    fun fetch(issuer: String): Result {
        val body = try {
            val request = HttpRequest.newBuilder(URI.create(issuer.trimEnd('/') + "/.well-known/openid-configuration")).timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() != 200) return Result(error = SsoSaveError.UNREACHABLE)
            response.body().use { it.readNBytes(MAX_BYTES + 1) }.takeIf { it.size <= MAX_BYTES } ?: return Result(error = SsoSaveError.INVALID_DISCOVERY)
        } catch (e: Exception) {
            return Result(error = SsoSaveError.UNREACHABLE)
        }
        val node = runCatching { mapper.readTree(body) }.getOrNull()?.takeIf { it.isObject } ?: return Result(error = SsoSaveError.INVALID_DISCOVERY)
        fun text(name: String) = node.get(name)?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotBlank() }
        val found = text("issuer") ?: return Result(error = SsoSaveError.INVALID_DISCOVERY)
        if (found != issuer) return Result(error = SsoSaveError.ISSUER_MISMATCH)
        val authorization = text("authorization_endpoint"); val token = text("token_endpoint"); val jwks = text("jwks_uri")
        if (authorization == null || token == null || jwks == null) return Result(error = SsoSaveError.INVALID_DISCOVERY)
        return Result(OidcDiscovery(found, authorization, token, jwks, text("userinfo_endpoint")))
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
