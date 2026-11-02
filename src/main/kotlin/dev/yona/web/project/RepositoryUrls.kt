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

    /** 내장 SSH 서버이다. 꺼져 있으면 없다. 복제 주소에 쓰는 실제 포트를 알려 준다. */
    @set:org.springframework.beans.factory.annotation.Autowired(required = false)
    var sshServer: dev.yona.ssh.SshServerLifecycle? = null

    /**
     * SSH 복제 주소를 돌려준다(추가 개발 항목 X-3, `decisions.md` D-66). 내장 SSH 서버와 시스템 sshd 연동을 모두 켜지 않았거나 Git 프로젝트가 아니면 `null`이다. 포트가 22이면 `git@호스트:소유자/이름.git`(scp 형식),
     * 그 밖이면 `ssh://git@호스트:포트/소유자/이름.git`이다. 호스트는 설정한 호스트 이름(`yona.hostname`, 포트는 뗀다)이고 없으면 요청의 호스트 이름이다.
     *
     * @param port 주소에 쓸 포트. 기본은 `yona.ssh.public-port`, 그것이 0이면 서버가 실제로 받는 포트이다
     */
    fun sshUrlFor(project: Project, request: HttpServletRequest, port: Int = sshPort()): String? {
        if (project.vcs != "GIT") return null
        return sshUrl(request, port, "${encode(project.owner)}/${encode(project.name)}.git")
    }

    /**
     * 프로젝트 위키 저장소의 SSH 복제 주소이다(`decisions.md` D-68). 프로젝트의 저장소 종류와 무관하게 git이며 이름 끝이 `.wiki.git`이다. SSH를 켜지 않았으면 `null`이다.
     */
    fun sshWikiUrlFor(project: Project, request: HttpServletRequest, port: Int = sshPort()): String? =
        sshUrl(request, port, "${encode(project.owner)}/${encode(project.name)}.wiki.git")

    private fun sshUrl(request: HttpServletRequest, port: Int, path: String): String? {
        if (!(properties.ssh.enabled || properties.ssh.relay.enabled)) return null
        val host = properties.hostname.ifEmpty { request.serverName }
        val user = properties.ssh.user
        return if (port == 22) "$user@$host:$path" else "ssh://$user@$host:$port/$path"
    }

    /** 복제 주소의 포트이다. `yona.ssh.public-port`, 내장 서버가 있으면 실제 포트, 시스템 sshd 방식만 켰으면 22(sshd의 기본 포트), 그 밖에는 설정한 포트이다. */
    private fun sshPort(): Int = properties.ssh.publicPort.takeIf { it > 0 }
        ?: sshServer?.boundPort?.takeIf { it > 0 }
        ?: if (properties.ssh.relay.enabled && !properties.ssh.enabled) 22 else properties.ssh.port

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

    /**
     * 프로젝트 위키 저장소의 복제 주소 `<스킴>://<호스트[:포트]><컨텍스트>/<소유자>/<이름>.wiki.git`이다(X-17, D-60. Forgejo 방식). 프로젝트의 VCS와 무관하게 git
     * 주소이다. [loginId]가 있으면 주소에 `사용자@`를 넣는다.
     */
    fun wikiUrlFor(project: Project, request: HttpServletRequest, loginId: String? = null): String {
        val base = "${scheme(request)}://${hostPort(request)}${request.contextPath.orEmpty().trimEnd('/')}"
        val url = "$base/${encode(project.owner)}/${encode(project.name)}.wiki.git"
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
