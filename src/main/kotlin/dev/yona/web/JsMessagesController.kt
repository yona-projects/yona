package dev.yona.web

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.util.Locale
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

/**
 * 브라우저 스크립트가 쓰는 메시지 함수를 만든다. v1.6 `Application.jsMessages`(`GET /messages.js`)에 해당한다.
 *
 * v1.6은 play-jsmessages 라이브러리로 전역 함수 `Messages(키, 인자...)`를 만들고 `yobi.*.js`가
 * 그대로 호출한다. 이름과 호출 방식이 같은 함수를 만든다.
 *
 * 함수의 모양도 play-jsmessages와 같다.
 *
 * - 키가 없으면 키를 그대로 돌려준다. 키 배열을 주면 목록에서 처음으로 있는 키를 쓰고, 하나도 없으면 첫 키를 쓴다.
 * - `{0}`, `{1}`... 자리표시자를 인자로 바꾼다. 자바스크립트 `String.replace`처럼 처음 나오는 하나만 바꾼다.
 * - 메시지 파일의 `''`(작은따옴표 두 개)는 `'` 하나로 바꾸고, 값의 끝 공백은 지운다(Play의 메시지 파서와 같다).
 * - Play 기본 검증 메시지(`messages-play`)의 키도 담는다.
 */
@RestController
class JsMessagesController {

    @GetMapping("/messages.js")
    fun jsMessages(): ResponseEntity<ByteArray> {
        val locale = LocaleContextHolder.getLocale()
        val script = SCRIPT_TEMPLATE.replace("__MESSAGES__", MAPPER.writeValueAsString(messagesFor(locale)))
        return ResponseEntity.ok()
            .contentType(MediaType("application", "javascript"))
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
            .cacheControl(CacheControl.noCache())
            .body(script.toByteArray(Charsets.UTF_8))
    }

    /** 영어 기본 메시지 위에 요청 언어의 번역을 덮어쓴 전체 메시지를 키 순서대로 돌려준다. */
    private fun messagesFor(locale: Locale): Map<String, String> {
        val bundle = ResourceBundle.getBundle("messages", locale, UTF8_CONTROL)
        val play = ResourceBundle.getBundle("messages-play", locale, UTF8_CONTROL)
        val keys = (bundle.keySet() + play.keySet()).sorted()
        return keys.associateWith { (if (bundle.containsKey(it)) bundle else play).getString(it).replace("''", "'").trimEnd() }
    }

    private companion object {
        val MAPPER: JsonMapper = JsonMapper.builder().build()

        /**
         * 메시지 파일을 UTF-8로 읽는 규칙이다. 시스템 기본 로캘로 대체하지 않고 지원 언어가 아니면
         * 기본(영어) 파일만 쓴다.
         */
        val UTF8_CONTROL = object : ResourceBundle.Control() {
            override fun getFallbackLocale(baseName: String, locale: Locale): Locale? = null

            override fun newBundle(
                baseName: String, locale: Locale, format: String, loader: ClassLoader, reload: Boolean
            ): ResourceBundle? {
                if (format != "java.properties") return null
                val resource = toResourceName(toBundleName(baseName, locale), "properties")
                val stream = loader.getResourceAsStream(resource) ?: return null
                return stream.reader(Charsets.UTF_8).use { PropertyResourceBundle(it) }
            }
        }

        val SCRIPT_TEMPLATE = listOf(
            "Messages=(function(u){function f(k){",
            "var m;",
            "if(typeof k==='object'){",
            "for(var i=0,l=k.length;i<l&&f.messages[k[i]]===u;++i);",
            "m=f.messages[k[i]]||k[0]",
            "}else{",
            "m=((f.messages[k]!==u)?f.messages[k]:k)",
            "}",
            "for(i=1;i<arguments.length;i++){",
            "m=m.replace('{'+(i-1)+'}',arguments[i])",
            "}",
            "return m};",
            "f.messages=__MESSAGES__;",
            "return f})()"
        ).joinToString("\n")
    }
}
