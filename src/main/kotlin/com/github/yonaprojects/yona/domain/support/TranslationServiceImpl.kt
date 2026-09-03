package com.github.yonaprojects.yona.domain.support

import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Service
class TranslationServiceImpl(
    private val objectMapper: ObjectMapper
) : TranslationService {
    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val limit = 4500
    private val newline = "\r\n"

    @Value("\${yona.translation.api:}")
    private lateinit var translationApi: String

    @Value("\${yona.translation.header-key:}")
    private lateinit var translationHeaderKey: String

    @Value("\${yona.translation.header-value:}")
    private lateinit var translationHeaderValue: String

    override fun translate(text: String): String? {
        if (translationApi.isBlank()) {
            log.warn("Translation API URL is not configured.")
            return null
        }
        if (text.isBlank()) {
            return ""
        }

        // 레거시와 동일한 로직 적용: &을 %26으로 치환하고 줄바꿈 단위로 쪼갠 뒤 청크 병합
        val replacedText = text.replace("&", "%26")
        val lines = replacedText.split(newline, "\n")
        val mergedChunks = merge(lines)

        val translatedChunks = mutableListOf<String>()
        for (chunk in mergedChunks) {
            if (chunk.isBlank()) {
                translatedChunks.add(newline)
                continue
            }
            val translatedText = callExternalApi(chunk) ?: return null
            translatedChunks.add(translatedText)
        }

        return translatedChunks.joinToString(newline)
    }

    private fun merge(texts: List<String>): List<String> {
        val results = mutableListOf<String>()
        var chunkLength = 0
        val chunk = StringBuilder()
        for (t in texts) {
            if (chunkLength + t.length < limit) {
                chunk.append(t).append(newline)
                chunkLength += t.length
            } else {
                results.add(chunk.toString())
                chunk.clear()
                chunk.append(t).append(newline)
                chunkLength = t.length
            }
        }
        if (chunk.isNotEmpty()) {
            results.add(chunk.toString())
        }
        return results
    }

    private fun callExternalApi(chunk: String): String? {
        return try {
            val encodedText = URLEncoder.encode(chunk, StandardCharsets.UTF_8)
            val requestBody = "source=ko&target=en&text=$encodedText"

            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(translationApi))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json,application/x-www-form-urlencoded,text/html,*/*")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))

            if (translationHeaderKey.isNotBlank() && translationHeaderValue.isNotBlank()) {
                requestBuilder.header(translationHeaderKey, translationHeaderValue)
            }

            val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.error("Failed to translate via API. Status code: ${response.statusCode()}, Body: ${response.body()}")
                return null
            }

            val rootNode = objectMapper.readTree(response.body())
            val resultNode = rootNode.findPath("result")
            val translatedTextNode = resultNode.findPath("translatedText")
            
            if (translatedTextNode.isMissingNode) {
                log.error("translatedText field is missing in translation API response. Response: ${response.body()}")
                null
            } else {
                translatedTextNode.textValue()
            }
        } catch (e: Exception) {
            log.error("Error occurred while calling translation API", e)
            null
        }
    }
}
