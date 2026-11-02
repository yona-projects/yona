package dev.yona.domain.oauth2server

import java.net.URI

/**
 * OAuth 앱의 리디렉션 주소 규칙이다(추가 개발 항목 X-1, `decisions.md` D-71, `architecture/oauth2-server-plan.md` 3-6, 3-9).
 *
 * 등록할 수 있는 주소는 `https` 주소와 루프백(`localhost`, `127.0.0.1`, `[::1]`)의 `http` 주소뿐이다. 조각(`#`)과 사용자 정보(`user:pw@`)가 있거나 호스트가 없거나 길이가 500자를 넘으면 거부한다.
 * 요청한 주소는 등록한 주소와 **정확히** 같아야 하되, 등록한 주소가 루프백이면 포트를 무시하고 세 루프백 호스트를 같게 본다(RFC 8252. 인증 앱이 임의의 로컬 포트를 쓰고
 * Claude Code가 `localhost`와 `127.0.0.1` 형태를 모두 보낸 적이 있다).
 */
object RedirectUris {

    const val MAX_LENGTH = 500
    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

    /** [value]를 등록할 수 없으면 이유를 돌려주고, 등록할 수 있으면 `null`이다. */
    fun problem(value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return "비어 있다"
        if (text.length > MAX_LENGTH) return "너무 길다"
        if (text.any { it.isWhitespace() || it.isISOControl() }) return "공백이나 제어 문자가 있다"
        val uri = try { URI(text) } catch (e: Exception) { return "주소 모양이 아니다" }
        if (uri.fragment != null || text.contains('#')) return "조각(#)이 있다"
        if (uri.userInfo != null) return "사용자 정보가 있다"
        val host = uri.host ?: return "호스트가 없다"
        return when (uri.scheme?.lowercase()) {
            "https" -> null
            "http" -> if (isLoopback(host)) null else "http는 루프백 주소만 쓸 수 있다"
            else -> "https 주소만 쓸 수 있다"
        }
    }

    /** [requested]가 [registered] 가운데 하나와 맞으면 `true`이다. */
    fun matches(registered: List<String>, requested: String): Boolean {
        val target = parse(requested) ?: return false
        return registered.any { entry ->
            val candidate = parse(entry) ?: return@any false
            if (entry == requested) return@any true
            candidate.loopback && target.loopback && candidate.scheme == target.scheme && candidate.path == target.path && candidate.query == target.query
        }
    }

    /** 줄 단위 글을 주소 목록으로 읽는다. 공백과 빈 줄과 중복은 정리하고 순서는 지킨다. */
    fun parse(raw: String?): List<String> = raw.orEmpty().split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** 주소 목록을 저장용 줄 단위 글로 만든다. */
    fun format(values: Collection<String>): String = values.joinToString("\n")

    private class Parts(val scheme: String, val loopback: Boolean, val path: String, val query: String?)

    private fun parse(value: String): Parts? {
        val uri = try { URI(value.trim()) } catch (e: Exception) { return null }
        val host = uri.host ?: return null
        return Parts(uri.scheme?.lowercase() ?: return null, isLoopback(host), uri.rawPath.orEmpty(), uri.rawQuery)
    }

    private fun isLoopback(host: String) = host.lowercase() in LOOPBACK_HOSTS
}
