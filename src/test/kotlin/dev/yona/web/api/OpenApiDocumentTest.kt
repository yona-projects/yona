package dev.yona.web.api

import dev.yona.security.ApiRequirement
import dev.yona.security.ApiScopeRules
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OpenAPI 문서(로드맵 X-31, `decisions.md` D-50)를 검증한다. 문서는 springdoc 어노테이션으로 코드에서 만들고, 이 시험이 **범위 대응표(`ApiScopeRules`)와 명세가
 * 어긋나지 않는지**, 모든 동작에 요약과 설명과 응답이 있는지, 응답과 요청 본문의 모양이 밝혀져 있는지를 확인한다. 새 외부 API를 더하면 이 시험이 문서를 쓰게 만든다.
 */
@AutoConfigureMockMvc
class OpenApiDocumentTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    private val mapper = JsonMapper.builder().build()

    private val spec: JsonNode by lazy {
        mapper.readTree(mockMvc.perform(get("/-_-api/docs/openapi")).andExpect(status().isOk).andReturn().response.contentAsString)
    }

    /** 명세의 (메서드, 경로) 전체이다. 시험 전용 경로(`__t`)는 뺀다. */
    private fun operations(): List<Triple<String, String, JsonNode>> = spec.get("paths").properties().flatMap { (path, item) ->
        item.properties().filter { it.key in METHODS && !path.contains("/__t/") }.map { Triple(it.key.uppercase(), path, it.value) }
    }

    /** 대응표의 경로 패턴에서 정규식을 뗀다(`{number:[0-9]+}`는 명세에서 `{number}`). */
    private fun plain(pattern: String) = pattern.replace(Regex("\\{(\\w+):[^}]*\\}"), "{$1}")

    @Test
    fun `명세와 화면이 누구나 열린다`() {
        mockMvc.perform(get("/-_-api/docs/openapi.yaml")).andExpect(status().isOk)
        mockMvc.perform(get("/-_-api/docs")).andExpect(status().isFound)
        mockMvc.perform(get("/-_-api/swagger-ui/index.html")).andExpect(status().isOk)
        assertEquals("3.1.0", spec.get("openapi").asString())
    }

    @Test
    fun `범위 대응표의 모든 동작이 명세에 있고 명세의 모든 동작이 대응표에 있다`() {
        val documented = operations().map { it.first to it.second }.toSet()
        // `/-_-api`와 `/-_-api/v1/`는 홈 화면의 별칭(v1.6)이라 API 문서에 넣지 않는다
        val table = ApiScopeRules.operations().map { it.first to plain(it.second) }.filterNot { it.second == "/-_-api" || it.second == "/-_-api/v1/" }.toSet()
        assertEquals(emptySet(), table - documented, "대응표에는 있으나 명세에 없는 동작")
        assertEquals(emptySet(), documented - table, "명세에는 있으나 대응표에 없는 동작")
    }

    @Test
    fun `모든 동작에 한국어 요약과 설명과 고유한 식별자와 태그와 응답이 있다`() {
        val ids = mutableSetOf<String>()
        val problems = mutableListOf<String>()
        operations().forEach { (method, path, op) ->
            val label = "$method $path"
            val summary = op.get("summary")?.asString("").orEmpty()
            val description = op.get("description")?.asString("").orEmpty()
            if (summary.isBlank() || !summary.any { it in '가'..'힣' }) problems += "$label: 한국어 요약이 없다"
            if (description.isBlank() || !description.any { it in '가'..'힣' }) problems += "$label: 한국어 설명이 없다"
            val id = op.get("operationId")?.asString("").orEmpty()
            if (id.isBlank() || id.matches(Regex(".*_\\d+$"))) problems += "$label: operationId가 없거나 자동으로 붙은 번호이다($id)"
            else if (!ids.add(id)) problems += "$label: operationId가 겹친다($id)"
            if (op.get("tags")?.size() != 1) problems += "$label: 태그는 하나여야 한다"
            val responses = op.get("responses")
            if (responses == null || responses.properties().none { it.key.startsWith("2") }) problems += "$label: 성공 응답이 없다"
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `성공 응답과 요청 본문은 모양이 밝혀져 있고 경로 매개변수에는 설명이 있다`() {
        val problems = mutableListOf<String>()
        operations().forEach { (method, path, op) ->
            val label = "$method $path"
            op.get("responses").properties().filter { it.key.startsWith("2") && it.key != "204" }.forEach { (code, response) ->
                val schema = response.get("content")?.properties()?.firstOrNull()?.value?.get("schema")
                val shaped = schema != null && (schema.has("\$ref") || schema.has("properties") || schema.has("items") || schema.has("oneOf") || schema.has("additionalProperties"))
                if (!shaped) problems += "$label: $code 응답의 모양이 밝혀져 있지 않다"
            }
            op.get("requestBody")?.let { body ->
                val schema = body.get("content")?.properties()?.firstOrNull()?.value?.get("schema")
                if (schema == null || !(schema.has("\$ref") || schema.has("properties") || schema.has("items"))) problems += "$label: 요청 본문의 모양이 밝혀져 있지 않다"
            }
            op.get("parameters")?.forEach { parameter ->
                if (parameter.get("description")?.asString("").isNullOrBlank()) problems += "$label: 매개변수 ${parameter.get("name").asString()}에 설명이 없다"
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `인증과 범위가 동작마다 밝혀져 있다`() {
        assertTrue(spec.get("components").get("securitySchemes").has("tokenAuth"))
        val problems = mutableListOf<String>()
        operations().forEach { (method, path, op) ->
            val requirement = ApiScopeRules.requirement(method, path.let { p -> ApiScopeRules.operations().firstOrNull { it.first == method && plain(it.second) == p }?.second ?: p })
            val needsToken = requirement != null && requirement != ApiRequirement.None
            val secured = op.has("security") && op.get("security").size() > 0
            if (needsToken && !secured) problems += "$method $path: 토큰 인증이 밝혀져 있지 않다"
            if (!needsToken && secured) problems += "$method $path: 인증이 필요 없는데 토큰 인증이 적혀 있다"
            if (needsToken && !op.get("description").asString("").contains("필요한 범위")) problems += "$method $path: 필요한 범위가 설명에 없다"
            if (needsToken && !op.get("responses").has("401")) problems += "$method $path: 401 응답이 없다"
            if (needsToken && !op.get("responses").has("403")) problems += "$method $path: 403 응답이 없다"
        }
        assertEquals(emptyList(), problems)
    }

    private companion object {
        val METHODS = setOf("get", "post", "put", "patch", "delete")
    }
}
