package dev.yona.web.project

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils

/**
 * 프로젝트 저장소의 복제(clone) 주소를 만든다. v1.6 `CodeApp.getURL`, `CodeApp.getURLWithLoginId`,
 * `utils.Url.createWithContext`, `utils.Config.getScheme/getHostport`에 해당한다.
 *
 * - Git 프로젝트: `<스킴>://<호스트[:포트]><컨텍스트>/<소유자>/<이름>`
 * - Subversion 프로젝트: `<스킴>://<호스트[:포트]><컨텍스트>/svn/<소유자>/<이름>`
 *
 * 스킴과 호스트는 설정(`yona.scheme`, `yona.hostname`, `yona.port`)이 있으면 그 값을, 없으면 요청에서 가져온다.
 */
@Component
class RepositoryUrls(private val properties: YonaProperties) {

    /**
     * 저장소 주소를 돌려준다. 알 수 없는 VCS이면 `null`이다.
     *
     * @param loginId 로그인한 사용자의 ID. 있으면 주소에 `사용자@`를 넣어 인증 정보를 미리 채워 준다
     */
    fun urlFor(project: Project, request: HttpServletRequest, loginId: String? = null): String? {
        val segments = when (project.vcs) {
            "GIT" -> listOf(encode(project.owner), encode(project.name))
            "Subversion" -> listOf("svn", project.owner.orEmpty(), project.name.orEmpty())
            else -> return null
        }
        val base = "${scheme(request)}://${hostPort(request)}${request.contextPath.orEmpty().trimEnd('/')}"
        val url = base + segments.joinToString("") { "/$it" }
        return if (loginId.isNullOrEmpty()) url else url.replace("://", "://$loginId@")
    }

    /** `<스킴>://<호스트[:포트]>`이다 (v1.6 `Config.getScheme() + "://" + Config.getHostport()`). */
    fun origin(request: HttpServletRequest): String = "${scheme(request)}://${hostPort(request)}"

    /** 설정이 없으면 요청의 스킴이 아니라 `http`이다 (v1.6 `Config.getScheme`). */
    @Suppress("UNUSED_PARAMETER")
    private fun scheme(request: HttpServletRequest): String = properties.scheme.ifEmpty { "http" }

    /** 설정한 호스트 이름이 있으면 `호스트[:포트]`(포트 80은 생략), 없으면 요청의 Host 헤더이다 (v1.6 `Config.getHostport`). */
    private fun hostPort(request: HttpServletRequest): String {
        if (properties.hostname.isEmpty()) {
            val port = request.serverPort
            val default = (request.scheme == "http" && port == 80) || (request.scheme == "https" && port == 443)
            return request.getHeader("Host") ?: if (default) request.serverName else "${request.serverName}:$port"
        }
        val port = properties.port
        return if (port != null && port != 80) "${properties.hostname}:$port" else properties.hostname
    }

    private fun encode(value: String?): String = UriUtils.encodePathSegment(value.orEmpty(), Charsets.UTF_8)
}
