package com.github.yonaprojects.yona.web

import tools.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*
import org.hamcrest.Matchers

class MessagesControllerSpec : DescribeSpec({
    val objectMapper = ObjectMapper()
    val controller = MessagesController(objectMapper)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    describe("MessagesController 단위 테스트") {
        describe("GET /messages.js") {
            it("한국어 로케일에 대해 ko-KR 메시지 데이터를 포함한 JS 번들을 렌더링해야 한다") {
                mockMvc.perform(
                    get("/messages.js")
                        .locale(Locale.KOREA)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("application/javascript;charset=UTF-8"))
                    .andExpect(header().exists("Cache-Control"))
                    .andExpect(content().string(Matchers.containsString("window.Messages = function(key)")))
                    .andExpect(content().string(Matchers.containsString("app.name")))
            }

            it("영어/기본 로케일에 대해 기본 messages 데이터를 포함한 JS 번들을 렌더링해야 한다") {
                mockMvc.perform(
                    get("/messages.js")
                        .locale(Locale.US)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("application/javascript;charset=UTF-8"))
                    .andExpect(content().string(Matchers.containsString("window.Messages = function(key)")))
                    .andExpect(content().string(Matchers.containsString("app.name")))
            }

            it("일본어 로케일에 대해 ja-JP 메시지 데이터를 포함한 JS 번들을 렌더링해야 한다") {
                mockMvc.perform(
                    get("/messages.js")
                        .locale(Locale.JAPAN)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("window.Messages = function(key)")))
            }

            it("러시아어 로케일에 대해 ru-RU 메시지 데이터를 포함한 JS 번들을 렌더링해야 한다") {
                mockMvc.perform(
                    get("/messages.js")
                        .locale(Locale.of("ru", "RU"))
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("window.Messages = function(key)")))
            }

            it("우즈베크어 로케일에 대해 uz-UZ 메시지 데이터를 포함한 JS 번들을 렌더링해야 한다") {
                mockMvc.perform(
                    get("/messages.js")
                        .locale(Locale.of("uz", "UZ"))
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(Matchers.containsString("window.Messages = function(key)")))
            }
        }

        // readProperties()는 private이라 리플렉션으로 직접 호출한다. 실제 서비스되는 4개 로케일
        // 파일(ko_KR/ja_JP/ru_RU/uz_UZ)이 모두 리소스에 존재해서 공개 API(getJsMessages)만으로는
        // `!resource.exists()` 분기(빈 맵 반환)를 태울 수 없다.
        describe("readProperties() (private, 리플렉션 호출)") {
            it("존재하지 않는 properties 경로면 빈 맵을 반환해야 한다") {
                val method = MessagesController::class.java.getDeclaredMethod("readProperties", String::class.java)
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val result = method.invoke(controller, "messages/no-such-file.properties") as Map<String, String>

                result shouldBe emptyMap()
            }
        }
    }
})
