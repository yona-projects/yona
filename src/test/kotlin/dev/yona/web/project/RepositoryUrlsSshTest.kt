package dev.yona.web.project

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SSH 복제 주소(X-3 6단계, `decisions.md` D-66)를 검증한다. 내장 SSH 서버를 켰을 때만 있고(`yona.ssh.enabled`), 꺼져 있으면 `null`이라 화면이 v1.6과 같다.
 * 포트가 22이면 `git@호스트:소유자/이름.git`(scp 형식), 그 밖이면 `ssh://git@호스트:포트/소유자/이름.git`이다(GitHub 방식).
 */
class RepositoryUrlsSshTest {

    private fun urls(ssh: YonaProperties.Ssh, hostname: String = "", port: Int? = null) = RepositoryUrls(YonaProperties(hostname = hostname, port = port, ssh = ssh))

    private val git = Project(owner = "owner", name = "repo", vcs = "GIT")

    private fun request(host: String = "yona.example.com", port: Int = 80) = MockHttpServletRequest().apply { serverName = host; serverPort = port; addHeader("Host", if (port == 80) host else "$host:$port") }

    @Test
    fun `내장 SSH 서버가 꺼져 있으면 SSH 주소가 없다`() = assertNull(urls(YonaProperties.Ssh(enabled = false)).sshUrlFor(git, request(), 2222))

    @Test
    fun `22가 아닌 포트는 ssh 스킴 주소이고 호스트는 요청의 호스트 이름이다`() {
        assertEquals("ssh://git@yona.example.com:2222/owner/repo.git", urls(YonaProperties.Ssh(enabled = true)).sshUrlFor(git, request(port = 9000), 2222))
    }

    @Test
    fun `22번 포트는 scp 형식 주소이다`() {
        assertEquals("git@yona.example.com:owner/repo.git", urls(YonaProperties.Ssh(enabled = true)).sshUrlFor(git, request(), 22))
    }

    @Test
    fun `설정한 호스트 이름이 있으면 포트를 떼고 그 이름을 쓴다`() {
        assertEquals("ssh://git@code.example.org:2222/owner/repo.git", urls(YonaProperties.Ssh(enabled = true), hostname = "code.example.org", port = 8080).sshUrlFor(git, request(), 2222))
    }

    @Test
    fun `사용자 이름은 설정을 따른다`() {
        assertEquals("ssh://deploy@yona.example.com:2222/owner/repo.git", urls(YonaProperties.Ssh(enabled = true, user = "deploy")).sshUrlFor(git, request(), 2222))
    }

    @Test
    fun `한글과 공백이 든 이름은 경로에 맞게 인코딩한다`() {
        val p = Project(owner = "소유자", name = "my repo", vcs = "GIT")
        assertEquals("ssh://git@yona.example.com:2222/%EC%86%8C%EC%9C%A0%EC%9E%90/my%20repo.git", urls(YonaProperties.Ssh(enabled = true)).sshUrlFor(p, request(), 2222))
    }

    @Test
    fun `Git이 아닌 프로젝트에는 SSH 주소가 없다`() {
        assertNull(urls(YonaProperties.Ssh(enabled = true)).sshUrlFor(Project(owner = "o", name = "n", vcs = "Subversion"), request(), 2222))
    }
}
