package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

/**
 * `GET /messages.js`(v1.6 `Application.jsMessages`)가 브라우저 스크립트용 메시지 함수를 제공하는지 검증한다.
 *
 * v1.6은 play-jsmessages 라이브러리로 `Messages(키, 인자...)` 전역 함수를 만들어 `yobi.*.js`가 그대로 쓴다.
 * 함수의 이름과 호출 방식이 같아야 기존 JS가 동작한다.
 */
@AutoConfigureMockMvc
class JsMessagesTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun body(acceptLanguage: String? = null): String {
        val request = get("/messages.js")
        acceptLanguage?.let { request.header("Accept-Language", it) }
        return mockMvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString
    }

    @Test
    fun `자바스크립트로 응답한다`() {
        mockMvc.perform(get("/messages.js"))
            .andExpect(content().contentTypeCompatibleWith("application/javascript"))
    }

    @Test
    fun `전역 Messages 함수를 정의한다`() {
        assertTrue(body().contains("Messages"), body().take(200))
    }

    @Test
    fun `기본 로캘의 메시지를 담는다`() {
        assertTrue(body().contains("\"button.login\":\"Log in\""), "영어 메시지 누락")
    }

    @Test
    fun `요청 로캘의 메시지를 담는다`() {
        val korean = body("ko-KR")
        assertTrue(korean.contains("\"button.login\":\"로그인\""), "한국어 메시지 누락")
    }

    @Test
    fun `번역이 없는 키는 영어 기본 메시지로 채운다`() {
        // 일본어 파일에 없는 키도 영어 기본값으로 포함되어야 JS에서 키 이름이 그대로 보이지 않는다.
        val japanese = body("ja-JP")
        assertTrue(japanese.contains("\"common.comment.delete.confirm\""), "기본 메시지 누락")
    }

    @Test
    fun `따옴표와 줄바꿈과 역슬래시는 JS 문자열로 이스케이프된다`() {
        val script = body()
        // 원본: "Wow, that''s huge!<br>Please submit file smaller than {0}." (작은따옴표 두 개 -> 하나)
        assertTrue(script.contains("Wow, that's huge!<br>Please submit file smaller than {0}."), "작은따옴표 해석 실패")
        assertTrue(!script.contains("''"), "작은따옴표 두 개가 남아 있다")
    }

    @Test
    fun `응답은 서버에서 캐시할 수 있도록 로캘에 따라 달라진다고 알린다`() {
        mockMvc.perform(get("/messages.js").header("Accept-Language", "ko-KR"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Vary", org.hamcrest.Matchers.containsString("Accept-Language")))
    }
}
