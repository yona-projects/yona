package dev.yona.markdown

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * README의 상대 경로를 저장소 경로로 바꾸는 규칙을 검증한다.
 *
 * 원본: `app/utils/Markdown.java:358-376` (`replaceImageLinkPath`, `replaceContentsLinkToCodeBrowerPath`).
 */
class RelativeLinkRewriterTest {

    private val rewriter = RelativeLinkRewriter(owner = "alice", name = "demo", defaultBranch = "refs/heads/main", contextPath = "")

    @Test
    fun `상대 경로 이미지는 파일 주소로 바뀐다`() {
        assertEquals("![로고](/alice/demo/files/main/img/a.png)", rewriter.forCodeBrowser("![로고](./img/a.png)"))
    }

    @Test
    fun `슬래시로 시작하는 점 경로도 바뀐다`() {
        assertEquals("![x](/alice/demo/files/main/a.png)", rewriter.forCodeBrowser("![x](/./a.png)"))
    }

    @Test
    fun `절대 주소 이미지는 그대로 둔다`() {
        val source = "![a](https://x.io/a.png) ![b](http://x.io/b.png) ![c](ftp://x.io/c.png) ![d](file://x/d.png)"
        assertEquals(source, rewriter.forCodeBrowser(source))
    }

    @Test
    fun `코드 브라우저용은 일반 링크를 바꾸지 않는다`() {
        assertEquals("[문서](./docs/a.md)", rewriter.forCodeBrowser("[문서](./docs/a.md)"))
    }

    @Test
    fun `README용은 일반 링크를 코드 주소로 바꾼다`() {
        assertEquals("보기 [문서](/alice/demo/code/main/docs/a.md)", rewriter.forReadme("보기 [문서](./docs/a.md)"))
        assertEquals("\n[문서](/alice/demo/code/main/docs/a.md)", rewriter.forReadme("\n[문서](./docs/a.md)"))
    }

    @Test
    fun `문서 맨 앞의 일반 링크도 바꾼다 (R-14, v1_6 정규식은 앞 문자 하나를 요구해 놓쳤다)`() {
        assertEquals("[문서](/alice/demo/code/main/docs/a.md)", rewriter.forReadme("[문서](./docs/a.md)"))
    }

    @Test
    fun `README용은 이미지도 함께 바꾼다`() {
        assertEquals(
            "![로고](/alice/demo/files/main/a.png) [문서](/alice/demo/code/main/b.md)",
            rewriter.forReadme("![로고](./a.png) [문서](./b.md)")
        )
    }

    @Test
    fun `한 줄에 상대 링크가 여럿이면 모두 바꾼다 (C087, v1_6 탐욕 정규식은 첫 링크만 바꿨다)`() {
        assertEquals(
            "[a](/alice/demo/code/main/x.md) and [b](/alice/demo/code/main/y.md)",
            rewriter.forReadme("[a](./x.md) and [b](./y.md)")
        )
        assertEquals(
            "[a](/alice/demo/code/main/1.md), [b](/alice/demo/code/main/2.md), [c](/alice/demo/code/main/3.md)",
            rewriter.forReadme("[a](./1.md), [b](./2.md), [c](./3.md)")
        )
        assertEquals(
            "| [a](/alice/demo/code/main/a.md) | [b](/alice/demo/code/main/b.md) |",
            rewriter.forReadme("| [a](./a.md) | [b](./b.md) |")
        )
    }

    @Test
    fun `한 줄에 상대 이미지가 여럿이면 모두 바꾼다 (C087)`() {
        val expected = "![a](/alice/demo/files/main/1.png) ![b](/alice/demo/files/main/2.png)"
        assertEquals(expected, rewriter.forCodeBrowser("![a](./1.png) ![b](./2.png)"))
        assertEquals(expected, rewriter.forReadme("![a](./1.png) ![b](./2.png)"))
    }

    @Test
    fun `절대 주소와 섞여 있어도 상대 링크만 바꾼다 (C087)`() {
        assertEquals(
            "[a](/alice/demo/code/main/x.md) [s](http://z.com) [c](/alice/demo/code/main/c.md)",
            rewriter.forReadme("[a](./x.md) [s](http://z.com) [c](./c.md)")
        )
        assertEquals(
            "[a](/alice/demo/code/main/x.md) (참고) [b](/alice/demo/code/main/y.md)",
            rewriter.forReadme("[a](./x.md) (참고) [b](./y.md)")
        )
    }

    @Test
    fun `파일 이름에 괄호가 있어도 결과가 같고 뒤의 링크도 바뀐다 (C087)`() {
        assertEquals("[a](/alice/demo/code/main/a(1).md)", rewriter.forReadme("[a](./a(1).md)"))
        assertEquals(
            "[a](/alice/demo/code/main/a(1).md) [b](/alice/demo/code/main/b.md)",
            rewriter.forReadme("[a](./a(1).md) [b](./b.md)")
        )
    }

    @Test
    fun `컨텍스트 경로가 있으면 경로와 소유자 사이에 슬래시가 있다 (R-14, v1_6은 구분자가 빠졌다)`() {
        val withContext = RelativeLinkRewriter("alice", "demo", "refs/heads/main", "yona")
        assertEquals("![x](/yona/alice/demo/files/main/a.png)", withContext.forCodeBrowser("![x](./a.png)"))
        assertEquals("[d](/yona/alice/demo/code/main/b.md)", withContext.forReadme("[d](./b.md)"))
    }

    @Test
    fun `기본 브랜치가 refs heads 로 시작하지 않아도 그대로 쓴다`() {
        val head = RelativeLinkRewriter("alice", "demo", "HEAD", "")
        assertEquals("![x](/alice/demo/files/HEAD/a.png)", head.forCodeBrowser("![x](./a.png)"))
    }
}
