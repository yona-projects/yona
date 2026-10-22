package dev.yona.markdown

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** v1.6 `CacheStore.renderedMarkdown`에 해당하는 렌더 결과 캐시를 검증한다. */
class RenderedMarkdownCacheTest {

    @Test
    fun `같은 원문은 한 번만 렌더링한다`() {
        val cache = RenderedMarkdownCache(maximumSize = 10)
        var calls = 0
        repeat(3) { cache.getOrRender("# a") { calls++; "<h1>a</h1>" } }
        assertEquals(1, calls)
    }

    @Test
    fun `저장된 결과를 그대로 돌려준다`() {
        val cache = RenderedMarkdownCache(maximumSize = 10)
        cache.getOrRender("한글 원문") { "<p>한글 결과</p>" }
        assertEquals("<p>한글 결과</p>", cache.getOrRender("한글 원문") { error("다시 렌더링하면 안 된다") })
    }

    @Test
    fun `원문이 다르면 따로 렌더링한다`() {
        val cache = RenderedMarkdownCache(maximumSize = 10)
        assertEquals("1", cache.getOrRender("a") { "1" })
        assertEquals("2", cache.getOrRender("b") { "2" })
    }

    @Test
    fun `해시 코드가 같은 서로 다른 원문을 섞지 않는다`() {
        // "Aa"와 "BB"는 String.hashCode()가 같다(v1.6 캐시는 이 경우 결과가 뒤바뀐다).
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val cache = RenderedMarkdownCache(maximumSize = 10)
        cache.getOrRender("Aa") { "first" }
        assertEquals("second", cache.getOrRender("BB") { "second" })
    }

    @Test
    fun `최대 개수를 넘으면 오래된 항목을 내보낸다`() {
        val cache = RenderedMarkdownCache(maximumSize = 2)
        (1..50).forEach { n -> cache.getOrRender("src$n") { "out$n" } }
        cache.cleanUp()
        assert(cache.size() <= 2) { "크기: ${cache.size()}" }
    }
}
