package dev.yona.translation

import dev.yona.config.YonaProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 외부 번역 서비스를 부른다. v1.6 `IssueApi.translate`, `merge`, `getTranslations`(`app/controllers/api/IssueApi.java:1004-1080`)에 해당한다.
 *
 * 한국어를 영어로 번역(`source=ko&target=en`)하며, 요청 본문은 v1.6처럼 인코딩하지 않은 `text=` 뒤에 글을 그대로 붙인다.
 * 호출하는 쪽이 `&`를 `%26`으로 바꿔 넘긴다. 서비스는 설정한 헤더로 인증한다.
 */
@Component
class TranslationClient(private val properties: YonaProperties) {
    private val http = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    /** [text] 한 덩어리를 번역해 번역문을 돌려준다. 비었거나 응답에 번역문이 없으면 `null`이다. */
    fun translate(text: String): String? {
        if (text.isBlank()) return null
        val builder = HttpRequest.newBuilder(URI.create(properties.translationApi))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Accept", "application/json,application/x-www-form-urlencoded,text/html,*/*")
            .POST(HttpRequest.BodyPublishers.ofString("source=ko&target=en&text=$text", Charsets.UTF_8))
        if (properties.translationHeaderKey.isNotBlank()) builder.header(properties.translationHeaderKey, properties.translationHeaderValue)
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        return json.readTree(response.body()).path("result").path("translatedText").takeIf { !it.isMissingNode && !it.isNull }?.asString()
    }

    /** 줄 목록을 [LIMIT]자 안쪽 덩어리로 묶어 차례로 번역하고 번역문을 줄바꿈으로 이어 붙인다. 공백뿐인 덩어리는 줄바꿈 하나이고, 응답에 번역문이 없으면 그 덩어리의 원문을 그대로 둔다(v1.6 `String.join`은 문자열 `null`을 이어 붙였다, R-75). */
    fun translateLines(lines: List<String>): String = merge(lines).joinToString(NEWLINE) { if (it.isBlank()) NEWLINE else translate(it) ?: it.trimEnd('\r', '\n') }

    companion object {
        /** 한 번에 보내는 글자 수의 한도이다 (v1.6 `TRANSLATE_TEXT_LENGTH_LIMIT`). */
        const val LIMIT = 4500
        const val NEWLINE = "\r\n"

        /** 줄마다 줄바꿈을 붙여 이어 가다가 지금까지의 길이와 줄 길이의 합이 한도 미만이 아니면 새 덩어리를 시작한다 (v1.6 `merge`). */
        fun merge(texts: List<String>): List<String> {
            val results = mutableListOf<String>()
            var chunk = ""
            var length = 0
            for (text in texts) {
                if (length + text.length < LIMIT) {
                    chunk += text + NEWLINE; length += text.length
                } else {
                    results += chunk
                    chunk = text + NEWLINE; length = text.length
                }
            }
            results += chunk
            return results
        }
    }
}
