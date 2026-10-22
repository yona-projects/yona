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
    fun `문서 맨 앞의 일반 링크는 바뀌지 않는다 (v1 6 결함 재현)`() {
        // v1.6 정규식은 링크 앞에 `!`가 아닌 문자가 하나 있어야 해서 맨 앞 링크를 놓친다 (refactoring-backlog R-14).
        assertEquals("[문서](./docs/a.md)", rewriter.forReadme("[문서](./docs/a.md)"))
    }

    @Test
    fun `README용은 이미지도 함께 바꾼다`() {
        assertEquals(
            "![로고](/alice/demo/files/main/a.png) [문서](/alice/demo/code/main/b.md)",
            rewriter.forReadme("![로고](./a.png) [문서](./b.md)")
        )
    }

    @Test
    fun `컨텍스트 경로가 있으면 소유자 앞에 슬래시 없이 붙는다 (v1 6 결함 재현)`() {
        // v1.6은 "/" + "/yona" + 소유자로 이어 붙여서 소유자 앞 구분자가 빠진다 (refactoring-backlog R-14).
        val withContext = RelativeLinkRewriter("alice", "demo", "refs/heads/main", "yona")
        assertEquals("![x](//yonaalice/demo/files/main/a.png)", withContext.forCodeBrowser("![x](./a.png)"))
    }

    @Test
    fun `기본 브랜치가 refs heads 로 시작하지 않아도 그대로 쓴다`() {
        val head = RelativeLinkRewriter("alice", "demo", "HEAD", "")
        assertEquals("![x](/alice/demo/files/HEAD/a.png)", head.forCodeBrowser("![x](./a.png)"))
    }
}
