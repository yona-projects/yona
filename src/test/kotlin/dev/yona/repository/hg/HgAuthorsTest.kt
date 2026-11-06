package dev.yona.repository.hg

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Mercurial 사용자 문자열 읽기를 확인한다. */
class HgAuthorsTest {

    @Test
    fun `이름과 이메일을 가른다`() {
        assertThat(HgAuthors.parse("Alice Kim <alice@example.com>")).isEqualTo("Alice Kim" to "alice@example.com")
        assertThat(HgAuthors.parse("  Bob   <bob@example.com>  ")).isEqualTo("Bob" to "bob@example.com")
    }

    @Test
    fun `이름이 없으면 이메일을 이름으로 쓰고 이메일이 없으면 비운다`() {
        assertThat(HgAuthors.parse("<only@example.com>")).isEqualTo("only@example.com" to "only@example.com")
        assertThat(HgAuthors.parse("bob")).isEqualTo("bob" to "")
        assertThat(HgAuthors.parse("Bob Lee")).isEqualTo("Bob Lee" to "")
    }

    @Test
    fun `이메일 주소만 있으면 이름과 이메일이 모두 그 주소이다`() {
        assertThat(HgAuthors.parse("carol@example.com")).isEqualTo("carol@example.com" to "carol@example.com")
    }

    @Test
    fun `꺾쇠 안이 비어 있으면 이메일은 비어 있다`() {
        assertThat(HgAuthors.parse("Dan <>")).isEqualTo("Dan" to "")
    }
}
