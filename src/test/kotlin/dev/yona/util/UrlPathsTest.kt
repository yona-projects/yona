package dev.yona.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** v1.6 `HttpUtil.getEncodeEachPathName`을 검증한다. Java `String.split("/")`처럼 끝의 빈 조각을 버린다. 기대값은 v1.6 본문을 실행해 얻었다. */
class UrlPathsTest {

    @Test
    fun `경로 끝의 슬래시는 버린다`() {
        assertEquals("a/b", UrlPaths.encodeEach("a/b"))
        assertEquals("a/b", UrlPaths.encodeEach("a/b/"))
        assertEquals("a", UrlPaths.encodeEach("a/"))
        assertEquals("", UrlPaths.encodeEach("/"))
        assertEquals("", UrlPaths.encodeEach("//"))
        assertEquals("/a", UrlPaths.encodeEach("/a"))
        assertEquals("/a", UrlPaths.encodeEach("/a/"))
        assertEquals("a//b", UrlPaths.encodeEach("a//b"))
        assertEquals("a", UrlPaths.encodeEach("a//"))
        assertEquals("dir/sub", UrlPaths.encodeEach("dir/sub/"))
    }

    @Test
    fun `각 부분을 인코딩하고 공백은 퍼센트 20이다`() {
        assertEquals("a%20b/c%20d", UrlPaths.encodeEach("a b/c d"))
        assertEquals("%ED%95%9C%EA%B8%80/%ED%8C%8C%EC%9D%BC.txt", UrlPaths.encodeEach("한글/파일.txt"))
        assertEquals("a%2Bb/c", UrlPaths.encodeEach("a+b/c"))
        assertEquals("", UrlPaths.encodeEach(""))
        assertEquals(" ", UrlPaths.encodeEach(" "))
    }
}
