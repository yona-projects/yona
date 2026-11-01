package dev.yona.domain.wiki

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 위키 링크 문법 `[[Page]]`와 상대 링크의 변환을 검증한다(로드맵 X-17, `decisions.md` D-61). GitHub와 Forgejo의 문법이다: `[[페이지]]`, `[[글자|페이지]]`, 그림 `[[그림.png]]`.
 */
class WikiLinksTest {

    private val base = "/o/p/wiki"
    private val exists = setOf("Home", "Install-Guide", "MariaDB-설치-(Windows)")

    private fun expand(text: String) = WikiLinks.expand(text, base) { it in exists }

    // --- [[Page]] → 마크다운 링크 ---

    @Test
    fun `있는 페이지로 가는 링크이다`() {
        assertEquals("[Home](/o/p/wiki/Home)", expand("[[Home]]"))
    }

    @Test
    fun `글자와 페이지를 따로 줄 수 있고 공백 제목은 하이픈 이름으로 푼다`() {
        assertEquals("[설치 안내](/o/p/wiki/Install-Guide)", expand("[[설치 안내|Install Guide]]"))
        assertEquals("[Install Guide](/o/p/wiki/Install-Guide)", expand("[[Install Guide]]"))
    }

    @Test
    fun `없는 페이지는 새 페이지 만들기로 간다`() {
        assertEquals("[Missing Page](/o/p/wiki/_new?title=Missing%20Page)", expand("[[Missing Page]]"))
    }

    @Test
    fun `괄호가 든 이름은 마크다운 링크가 깨지지 않게 퍼센트 인코딩한다`() {
        assertEquals("[설치](/o/p/wiki/MariaDB-%EC%84%A4%EC%B9%98-%28Windows%29)", expand("[[설치|MariaDB 설치 (Windows)]]".replace("MariaDB 설치 (Windows)", "MariaDB 설치 (Windows)")))
    }

    @Test
    fun `바깥 주소로 가는 링크는 그대로 이어 준다`() {
        assertEquals("[사이트](https://example.com/a)", expand("[[사이트|https://example.com/a]]"))
        assertEquals("[메일](mailto:a@b.c)", expand("[[메일|mailto:a@b.c]]"))
    }

    @Test
    fun `그림은 이미지 문법으로 바꾸고 상대 경로는 uploads 아래로 본다`() {
        assertEquals("![photo.png](uploads/photo.png)", expand("[[photo.png]]"))
        assertEquals("![설명](uploads/photo.png)", expand("[[설명|photo.png]]"))
        assertEquals("![설명](uploads/photo.png)", expand("[[설명|uploads/photo.png]]"))
        assertEquals("![x](https://example.com/x.gif)", expand("[[x|https://example.com/x.gif]]"))
    }

    @Test
    fun `글자 안의 닫는 대괄호는 이스케이프한다`() {
        assertEquals("[a \\] b](/o/p/wiki/Home)", expand("[[a ] b|Home]]"))
    }

    @Test
    fun `문장 안과 한 줄의 여러 링크를 바꾼다`() {
        assertEquals("앞 [Home](/o/p/wiki/Home) 중간 [Install-Guide](/o/p/wiki/Install-Guide) 뒤", expand("앞 [[Home]] 중간 [[Install-Guide]] 뒤"))
    }

    // --- 바꾸지 않는 것 ---

    @Test
    fun `올바르지 않은 제목은 그대로 둔다`() {
        for (text in listOf("[[]]", "[[   ]]", "[[a/b]]", "[[_Other]]", "[[x|]]", "[[a\\b]]")) assertEquals(text, expand(text), text)
    }

    @Test
    fun `코드 블록과 인라인 코드 안은 바꾸지 않는다`() {
        val text = "```\n[[Home]]\n```\n`[[Home]]` 그리고 [[Home]]\n~~~\n[[Home]]\n~~~"
        assertEquals("```\n[[Home]]\n```\n`[[Home]]` 그리고 [Home](/o/p/wiki/Home)\n~~~\n[[Home]]\n~~~", expand(text))
    }

    @Test
    fun `대괄호가 아닌 일반 글과 마크다운 링크는 그대로이다`() {
        val text = "글 [링크](Install-Guide) 와 [단일] 과 ![그림](a.png)"
        assertEquals(text, expand(text))
    }

    // --- 렌더링된 HTML의 상대 링크 ---

    private fun rewrite(html: String) = WikiLinks.rewriteHtml(html, base)

    @Test
    fun `상대 링크는 위키 페이지 주소로 바꾼다`() {
        assertEquals("""<a href="/o/p/wiki/Install-Guide">x</a>""", rewrite("""<a href="Install-Guide">x</a>"""))
        assertEquals("""<a href="/o/p/wiki/Install-Guide#sec">x</a>""", rewrite("""<a href="Install-Guide#sec">x</a>"""))
        assertEquals("""<a class="c" href="/o/p/wiki/A%20B">x</a>""", rewrite("""<a class="c" href="A%20B">x</a>"""))
    }

    @Test
    fun `uploads 아래의 링크와 이미지는 파일 주소로 바꾼다`() {
        assertEquals("""<img src="/o/p/wiki/_uploads/photo.png" alt="x">""", rewrite("""<img src="uploads/photo.png" alt="x">"""))
        assertEquals("""<a href="/o/p/wiki/_uploads/doc.pdf">d</a>""", rewrite("""<a href="uploads/doc.pdf">d</a>"""))
    }

    @Test
    fun `절대 주소와 바깥 주소와 앵커는 그대로 둔다`() {
        for (html in listOf(
            """<a href="https://example.com/a">x</a>""", """<a href="//cdn.example.com/a">x</a>""", """<a href="/other/path">x</a>""", """<a href="#top">x</a>""",
            """<a href="mailto:a@b.c">x</a>""", """<a href="?q=1">x</a>""", """<img src="https://example.com/x.png">""", """<img src="logo.png">"""
        )) assertEquals(html, rewrite(html), html)
    }
}
