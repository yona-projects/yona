package dev.yona.ssh

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SSH로 들어오는 git 명령 읽기(X-3 5단계, `decisions.md` D-66)를 검증한다. 클라이언트는 `ssh://git@호스트:포트/소유자/저장소.git`이면 `git-upload-pack '/소유자/저장소.git'`을,
 * `git@호스트:소유자/저장소.git`이면 `git-upload-pack '소유자/저장소.git'`을 보낸다.
 */
class GitSshCommandTest {

    private fun parse(command: String) = GitSshHandler.parse(command)

    @Test
    fun `복제는 upload-pack이고 읽기 요청이다`() {
        val request = parse("git-upload-pack '/owner/repo.git'")!!
        assertEquals(SshRepositoryRequest("GIT", "owner", "repo", write = false), request)
    }

    @Test
    fun `푸시는 receive-pack이고 쓰기 요청이다`() {
        assertEquals(SshRepositoryRequest("GIT", "owner", "repo", write = true), parse("git-receive-pack '/owner/repo.git'"))
    }

    @Test
    fun `앞의 슬래시와 접미어 git과 끝 슬래시와 따옴표 없는 경로를 모두 받는다`() {
        val expected = SshRepositoryRequest("GIT", "owner", "repo", write = false)
        listOf("git-upload-pack 'owner/repo.git'", "git-upload-pack '/owner/repo'", "git-upload-pack '/owner/repo.git/'", "git-upload-pack /owner/repo.git", "git-upload-pack \"/owner/repo.git\"")
            .forEach { assertEquals(expected, parse(it), it) }
    }

    @Test
    fun `구식 명령 git upload-pack도 받는다`() {
        assertEquals(SshRepositoryRequest("GIT", "owner", "repo", write = false), parse("git upload-pack '/owner/repo.git'"))
        assertEquals(SshRepositoryRequest("GIT", "owner", "repo", write = true), parse("git receive-pack '/owner/repo.git'"))
    }

    @Test
    fun `점이 든 이름과 한글 이름을 받는다`() {
        assertEquals(SshRepositoryRequest("GIT", "my.org", "repo.v2", write = false), parse("git-upload-pack '/my.org/repo.v2.git'"))
        assertEquals(SshRepositoryRequest("GIT", "소유자", "저장소", write = false), parse("git-upload-pack '/소유자/저장소.git'"))
    }

    @Test
    fun `지원하지 않는 명령은 null이다`() {
        listOf("", "ls", "git-upload-archive '/owner/repo.git'", "scp -t /tmp", "git-upload-pack", "rm -rf /", "git-upload-pack '/owner/repo.git' ; ls", "git-upload-pack '/owner/repo.git' extra")
            .forEach { assertNull(parse(it), it) }
    }

    @Test
    fun `경로가 두 단계가 아니거나 위험하면 null이다`() {
        listOf("git-upload-pack '/owner'", "git-upload-pack '/a/b/c.git'", "git-upload-pack '/../repo.git'", "git-upload-pack '/owner/..'", "git-upload-pack '//repo.git'", "git-upload-pack '/owner/re\\po.git'",
            "git-upload-pack '/owner/re po.git'", "git-upload-pack '/owner/repo;ls.git'", "git-upload-pack '/owner/repo\$(id).git'", "git-upload-pack '/owner/.git'")
            .forEach { assertNull(parse(it), it) }
    }
}
