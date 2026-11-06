package dev.yona.web.project

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 저장소 종류별 복제 주소를 확인한다. Git과 Mercurial은 프로젝트 주소 그대로이고 Subversion은 `/svn/`이 붙는다. */
class RepositoryUrlsTest {

    private val urls = RepositoryUrls(YonaProperties())

    private fun request() = MockHttpServletRequest().apply { serverName = "yona.example.com"; serverPort = 80; addHeader("Host", "yona.example.com") }

    @Test
    fun `Git과 Mercurial은 프로젝트 주소이고 Subversion은 svn 아래이다`() {
        assertEquals("http://yona.example.com/owner/repo", urls.urlFor(Project(owner = "owner", name = "repo", vcs = "GIT"), request()))
        assertEquals("http://yona.example.com/owner/repo", urls.urlFor(Project(owner = "owner", name = "repo", vcs = "Mercurial"), request()))
        assertEquals("http://yona.example.com/svn/owner/repo", urls.urlFor(Project(owner = "owner", name = "repo", vcs = "Subversion"), request()))
    }

    @Test
    fun `로그인 아이디를 주면 주소에 넣고 모르는 종류는 null이다`() {
        assertEquals("http://dev@yona.example.com/owner/repo", urls.urlFor(Project(owner = "owner", name = "repo", vcs = "Mercurial"), request(), "dev"))
        assertNull(urls.urlFor(Project(owner = "owner", name = "repo", vcs = "Bazaar"), request()))
    }

    @Test
    fun `Mercurial에는 SSH 복제 주소를 아직 주지 않는다`() {
        val ssh = RepositoryUrls(YonaProperties(ssh = YonaProperties.Ssh(enabled = true)))
        assertNull(ssh.sshUrlFor(Project(owner = "owner", name = "repo", vcs = "Mercurial"), request(), 2222))
    }
}
