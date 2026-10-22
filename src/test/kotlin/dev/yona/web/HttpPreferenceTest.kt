package dev.yona.web

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * v1.6 `HttpUtil.getPreferType`의 규칙을 검증한다.
 *
 * Accept 헤더의 미디어 범위를 선호도 순서로 훑으면서, 각 범위가 받아들이는 첫 번째 지원 타입을 고른다.
 * 일치하는 것이 없으면 `text/html`이다.
 */
class HttpPreferenceTest {

    private val html = "text/html"
    private val json = "application/json"

    @Test
    fun `Accept 헤더가 없으면 html을 고른다`() {
        assertEquals(html, HttpPreference.preferredType(null, html, json))
    }

    @Test
    fun `와일드카드는 지원 타입 중 앞선 것을 고른다`() {
        assertEquals(html, HttpPreference.preferredType("*/*", html, json))
        assertEquals(json, HttpPreference.preferredType("*/*", json, html))
    }

    @Test
    fun `json만 요청하면 json을 고른다`() {
        assertEquals(json, HttpPreference.preferredType("application/json", html, json))
    }

    @Test
    fun `선호도가 높은 범위가 먼저 평가된다`() {
        assertEquals(json, HttpPreference.preferredType("text/html;q=0.5, application/json", html, json))
        assertEquals(html, HttpPreference.preferredType("text/html, application/json;q=0.5", html, json))
    }

    @Test
    fun `브라우저의 일반적인 Accept는 html이다`() {
        val browser = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        assertEquals(html, HttpPreference.preferredType(browser, html, json))
    }

    @Test
    fun `지원하지 않는 타입만 요청하면 html로 대체한다`() {
        assertEquals(html, HttpPreference.preferredType("image/png", html, json))
    }

    @Test
    fun `json 선호 여부를 판단한다`() {
        assertEquals(true, HttpPreference.isJsonPreferred("application/json, text/javascript, */*; q=0.01"))
        assertEquals(false, HttpPreference.isJsonPreferred("text/html"))
        assertEquals(false, HttpPreference.isJsonPreferred(null))
    }
}
