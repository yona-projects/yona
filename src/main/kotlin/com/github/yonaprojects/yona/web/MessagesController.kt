package com.github.yonaprojects.yona.web

import tools.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*
import java.util.concurrent.TimeUnit

@RestController
class MessagesController(
    private val objectMapper: ObjectMapper
) {

    @GetMapping("/messages.js", produces = ["application/javascript;charset=UTF-8"])
    fun getJsMessages(locale: Locale): ResponseEntity<String> {
        val messagesMap = loadMessagesForJs(locale)
        val json = objectMapper.writeValueAsString(messagesMap)
        
        val jsCode = """
            (function(window) {
                var messages = $json;
                window.Messages = function(key) {
                    var msg = messages[key];
                    if (msg === undefined || msg === null) {
                        return key;
                    }
                    // 원래 요나(jsmessages)와 동일하게 싱글 쿼트 두 개('')를 한 개(')로 변환해주는 이스케이프 보정 적용
                    msg = msg.replace(/''/g, "'");
                    for (var i = 1; i < arguments.length; i++) {
                        msg = msg.replace('{' + (i - 1) + '}', arguments[i]);
                    }
                    return msg;
                };
            })(window);
        """.trimIndent()

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
            .body(jsCode)
    }

    private fun loadMessagesForJs(locale: Locale): Map<String, String> {
        // 기본 messages.properties 읽기
        val defaultMap = readProperties("messages/messages.properties")
        
        // 특정 로캘 properties 읽기
        val localeFilename = when (locale.language) {
            "ko" -> "messages_ko_KR.properties"
            "ja" -> "messages_ja_JP.properties"
            "ru" -> "messages_ru_RU.properties"
            "uz" -> "messages_uz_UZ.properties"
            else -> null
        }
        
        val combinedMap = mutableMapOf<String, String>()
        combinedMap.putAll(defaultMap)
        
        if (localeFilename != null) {
            val localeMap = readProperties("messages/$localeFilename")
            combinedMap.putAll(localeMap)
        }
        
        return combinedMap
    }

    private fun readProperties(path: String): Map<String, String> {
        val resource = ClassPathResource(path)
        if (!resource.exists()) {
            return emptyMap()
        }
        val properties = Properties()
        try {
            resource.inputStream.use { stream ->
                // UTF-8 리더를 사용하여 한글 깨짐 방지 처리와 함께 Java 표준 규격대로 Properties 로드
                properties.load(stream.reader(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // 예외 방어
        }
        val map = mutableMapOf<String, String>()
        for (name in properties.stringPropertyNames()) {
            map[name] = properties.getProperty(name)
        }
        return map
    }
}
