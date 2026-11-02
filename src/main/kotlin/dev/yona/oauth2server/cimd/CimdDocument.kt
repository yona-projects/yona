package dev.yona.oauth2server.cimd

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.oauth2server.OAuthScopes
import dev.yona.domain.oauth2server.RedirectUris
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI

/**
 * 클라이언트 ID 메타데이터 문서(CIMD, IETF 초안 draft-ietf-oauth-client-id-metadata-document-02)의 검증이다(추가 개발 항목 X-1, `decisions.md` D-71). 클라이언트 ID는 `https://` 주소이고
 * 그 주소의 문서가 앱의 이름과 리디렉션 주소를 알린다. 공개 앱만 받는다(공유 비밀 값 인증과 `client_secret`, 개인 키가 든 문서는 거부한다).
 */
object CimdDocument {

    /** 문서의 이름 최대 길이이다. */
    const val NAME_MAX = 100
    private const val MAX_URL = 500
    private val mapper = JsonMapper()
    private val ALLOWED_GRANTS = setOf("authorization_code", "refresh_token")
    private val SECRET_METHODS = setOf("client_secret_basic", "client_secret_post", "client_secret_jwt")

    sealed interface Result {
        data class Valid(val name: String, val homeUrl: String, val redirectUris: List<String>, val scopes: Set<String>) : Result
        data class Invalid(val reason: String) : Result
    }

    /** [clientId]가 CIMD 클라이언트 ID(`https://`로 시작)이면 `true`이다. */
    fun isClientIdUrl(clientId: String): Boolean = clientId.startsWith("https://")

    /** 주소가 클라이언트 ID로 쓸 수 없으면 이유를 돌려준다(초안 3절): https, 호스트와 경로 필수, 사용자 정보와 조각과 점 경로와 질의 없음. */
    fun urlProblem(value: String): String? {
        if (value.length > MAX_URL) return "너무 길다"
        if (value.any { it.isWhitespace() || it.isISOControl() }) return "공백이나 제어 문자가 있다"
        val uri = try { URI(value) } catch (e: Exception) { return "주소 모양이 아니다" }
        if (uri.scheme?.lowercase() != "https") return "https가 아니다"
        if (uri.host.isNullOrEmpty()) return "호스트가 없다"
        if (uri.userInfo != null) return "사용자 정보가 있다"
        if (uri.rawFragment != null || value.contains('#')) return "조각이 있다"
        if (uri.rawQuery != null) return "질의가 있다"
        val path = uri.rawPath.orEmpty()
        if (path.isEmpty() || path == "/") return "경로가 없다"
        if (path.split('/').any { it == "." || it == ".." }) return "점 경로가 있다"
        return null
    }

    /** [json]을 [clientUrl]의 문서로 읽고 검증한다. */
    fun parse(clientUrl: String, json: String): Result {
        val root: JsonNode = try { mapper.readTree(json) } catch (e: Exception) { return Result.Invalid("JSON이 아니다") }
        if (!root.isObject) return Result.Invalid("객체가 아니다")
        if (root.path("client_id").asString("") != clientUrl) return Result.Invalid("client_id가 문서 주소와 다르다")
        for (forbidden in listOf("client_secret", "client_secret_expires_at")) if (root.has(forbidden)) return Result.Invalid("$forbidden 은(는) 쓸 수 없다")
        if (root.path("token_endpoint_auth_method").asString("none").let { it in SECRET_METHODS || (it != "none" && it.isNotEmpty() && it != "private_key_jwt") }) return Result.Invalid("공유 비밀 값 인증은 쓸 수 없다")
        root.path("jwks").path("keys").let { keys -> if (keys.isArray && keys.values().any { k -> listOf("d", "p", "q", "dp", "dq", "qi", "k").any { f -> k.has(f) } }) return Result.Invalid("개인 키가 들어 있다") }
        if (root.has("grant_types") && !root.path("grant_types").let { n -> n.isArray && n.values().all { it.asString("") in ALLOWED_GRANTS } }) return Result.Invalid("지원하지 않는 grant_types")
        if (root.has("response_types") && !root.path("response_types").let { n -> n.isArray && n.values().all { it.asString("") == "code" } }) return Result.Invalid("지원하지 않는 response_types")
        val redirectNode = root.path("redirect_uris")
        if (!redirectNode.isArray || redirectNode.isEmpty || redirectNode.size() > MAX_REDIRECTS) return Result.Invalid("redirect_uris가 없거나 너무 많다")
        val redirects = mutableListOf<String>()
        for (node in redirectNode.values()) { if (!node.isString) return Result.Invalid("redirect_uris에 글자가 아닌 값이 있다"); redirects += node.asString() }
        if (redirects.any { RedirectUris.problem(it) != null }) return Result.Invalid("리디렉션 주소가 올바르지 않다")
        val name = root.path("client_name").asString("").trim().ifEmpty { URI(clientUrl).host }.take(NAME_MAX)
        val home = root.path("client_uri").asString("").trim().takeIf { validHome(it) }.orEmpty()
        val requested = if (root.has("scope")) ApiScopes.parseOrEmpty(root.path("scope").asString("").split(" ", ",").filter { it.isNotBlank() }.joinToString(",")) else null
        val grantable = OAuthScopes.grantable().toSet()
        val scopes = if (requested == null) grantable else requested.filter { it in grantable }.toSet()
        if (scopes.isEmpty()) return Result.Invalid("허용되는 범위가 없다")
        return Result.Valid(name, home, RedirectUris.parse(redirects.joinToString("\n")), scopes)
    }

    private const val MAX_REDIRECTS = 10

    private fun validHome(value: String): Boolean = value.isNotEmpty() && value.length <= MAX_URL && try {
        val u = URI(value); u.scheme?.lowercase() in setOf("http", "https") && !u.host.isNullOrEmpty() && u.userInfo == null
    } catch (e: Exception) { false }
}
