package dev.yona.domain.search

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** 검색 결과 본문 발췌(`SearchSnippets`)를 검증한다. 원본: `app/models/SearchResult.java:54-107`(`makeSnippets`). */
class SearchSnippetsTest {

    @Test
    fun `키워드 앞뒤 40자를 잘라 낸다`() {
        val text = "a".repeat(100) + "KEY" + "b".repeat(100)
        assertEquals(listOf("a".repeat(40) + "KEY" + "b".repeat(40)), SearchSnippets.make(text, "key", 40))
    }

    @Test
    fun `본문이 짧으면 처음과 끝까지이다`() {
        assertEquals(listOf("앞 키워드 뒤"), SearchSnippets.make("앞 키워드 뒤", "키워드", 40))
    }

    @Test
    fun `가까운 일치는 한 조각으로 합친다`() {
        val text = "x".repeat(10) + "KEY" + "y".repeat(20) + "KEY" + "z".repeat(10)
        assertEquals(listOf(text), SearchSnippets.make(text, "key", 40))
    }

    @Test
    fun `먼 일치는 따로 조각낸다`() {
        val text = "KEY" + "-".repeat(200) + "KEY"
        assertEquals(listOf("KEY" + "-".repeat(40), "-".repeat(40) + "KEY"), SearchSnippets.make(text, "key", 40))
    }

    @Test
    fun `겹치지 않게 일치를 찾는다`() {
        assertEquals(listOf("aaaa"), SearchSnippets.make("aaaa", "aa", 1))
    }

    @Test
    fun `일치가 없으면 비어 있다`() {
        assertEquals(emptyList(), SearchSnippets.make("본문", "없음", 40))
        assertEquals(emptyList(), SearchSnippets.make(null, "없음", 40))
    }

    @Test
    fun `원문의 대소문자를 유지한다`() {
        assertEquals(listOf("Hello World"), SearchSnippets.make("Hello World", "WORLD", 40))
    }
}
