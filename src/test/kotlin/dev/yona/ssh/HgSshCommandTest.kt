package dev.yona.ssh

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SSH로 들어오는 Mercurial 명령 읽기(X-7 6d)를 검증한다. `hg`는 `ssh://git@호스트:포트/소유자/이름`이면 원격에서 `hg -R 소유자/이름 serve --stdio`를 실행한다.
 * 읽기와 쓰기는 같은 명령이라 명령 줄만으로는 구분하지 못한다(푸시 여부는 연결 안에서 정해진다).
 */
class HgSshCommandTest {

    private fun parse(command: String) = HgSshHandler.parse(command)
    private val expected = SshRepositoryRequest("Mercurial", "owner", "repo", write = false, service = "serve", writeOptional = true)

    @Test
    fun `serve stdio 명령은 쓰기가 선택인 읽기 요청이다`() {
        assertEquals(expected, parse("hg -R owner/repo serve --stdio"))
    }

    @Test
    fun `앞의 슬래시와 따옴표와 끝 슬래시를 모두 받는다`() {
        listOf("hg -R /owner/repo serve --stdio", "hg -R 'owner/repo' serve --stdio", "hg -R \"/owner/repo/\" serve --stdio", "hg -R owner/repo/ serve --stdio")
            .forEach { assertEquals(expected, parse(it), it) }
    }

    @Test
    fun `점이 든 이름은 접미어를 떼지 않고 그대로 받는다`() {
        assertEquals(SshRepositoryRequest("Mercurial", "my.org", "repo.git", write = false, service = "serve", writeOptional = true), parse("hg -R my.org/repo.git serve --stdio"))
    }

    @Test
    fun `지원하지 않는 명령과 위험한 경로는 null이다`() {
        listOf(
            "", "hg", "hg serve --stdio", "hg -R owner/repo serve", "hg -R owner/repo log", "hg -R owner/repo serve --stdio ; ls", "hg -R owner serve --stdio",
            "hg -R a/b/c serve --stdio", "hg -R ../repo serve --stdio", "hg -R owner/.. serve --stdio", "hg -R 'owner/re po' serve --stdio", "hg -R owner/repo;ls serve --stdio",
            "git-upload-pack '/owner/repo.git'"
        ).forEach { assertNull(parse(it), it) }
    }
}
