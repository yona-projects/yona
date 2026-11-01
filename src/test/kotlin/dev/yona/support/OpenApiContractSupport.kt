package dev.yona.support

import org.springdoc.webmvc.api.OpenApiWebMvcResource
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.web.servlet.HandlerMapping
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.Locale

/**
 * OpenAPI 문서의 **계약 시험**을 모든 `MockMvc` 시험에 건다(로드맵 X-31, `decisions.md` D-50). 시험이 `/-_-api/v1` 아래 요청을 보내 JSON 응답을 받으면, 그 응답이
 * 문서(명세)에 적힌 응답 모양과 맞는지 확인한다. 문서에 없는 키, 널이 아닌데 빠진 키, 값의 형식이 다른 것은 시험을 실패시킨다. 그래서 REST 시험이 늘수록 문서의 정확도가
 * 함께 검증되고, 응답을 바꾸면 문서를 고치게 된다.
 *
 * 문서에 없는 상태 코드의 응답, JSON이 아닌 응답, 시험 전용 경로는 건너뛴다. 값이 없을 수 있는 항목은 `null`이거나 키가 없는 것을 모두 허용한다.
 */
@Component
class OpenApiContractSupport(private val resource: OpenApiWebMvcResource) : MockMvcBuilderCustomizer {

    private val mapper = JsonMapper.builder().build()

    /** 명세이다. 처음 쓸 때 한 번 만든다. */
    private val spec: JsonNode by lazy {
        mapper.readTree(resource.openapiJson(MockHttpServletRequest("GET", DOCS_PATH), DOCS_PATH, Locale.KOREA))
    }

    override fun customize(builder: ConfigurableMockMvcBuilder<*>) {
        (builder as DefaultMockMvcBuilder).alwaysDo<DefaultMockMvcBuilder>(ResultHandler { result ->
            val request = result.request
            val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: return@ResultHandler
            if (!pattern.startsWith("/-_-api/v1") || pattern.contains("/__t/")) return@ResultHandler
            val response = result.response
            // JSON 줄(`application/x-ndjson`)은 한 문서가 아니라 줄마다 한 건이라 건너뛴다(감사 로그 내보내기)
            if (response.contentType?.contains("json", ignoreCase = true) != true || response.contentType?.contains("ndjson", ignoreCase = true) == true) return@ResultHandler
            val body = response.contentAsString.takeIf { it.isNotBlank() } ?: return@ResultHandler
            val method = request.method?.lowercase() ?: return@ResultHandler
            val operation = spec.get("paths")?.get(dev.yona.security.ApiScopeRules.plain(pattern))?.get(method) ?: return@ResultHandler
            val schema = operation.get("responses")?.get(response.status.toString())?.get("content")?.get("application/json")?.get("schema") ?: return@ResultHandler
            val actual = try { mapper.readTree(body) } catch (e: Exception) { return@ResultHandler }
            val problems = mutableListOf<String>()
            validate(actual, schema, "$", problems)
            check(problems.isEmpty()) {
                "OpenAPI 문서와 실제 응답이 다르다: ${request.method} $pattern -> ${response.status}\n" + problems.take(12).joinToString("\n") { "  - $it" } +
                    "\n문서(`docs` 패키지의 모델과 컨트롤러의 `@ApiResponse`)와 응답 중 틀린 쪽을 고친다. 응답: ${body.take(400)}"
            }
        })
    }

    /** 스키마의 형식 이름들이다. `null`은 뺀다(널 허용은 값이 `null`이면 건너뛰는 것으로 다룬다). */
    private fun typesOf(schema: JsonNode): List<String> {
        val type = schema.get("type") ?: return emptyList()
        val names = if (type.isArray) type.toList().map { it.asString() } else listOf(type.asString())
        return names.filter { it != "null" }
    }

    /** [value]가 [schema]에 맞는지 본다. 문제는 [problems]에 경로와 함께 모은다. */
    private fun validate(value: JsonNode?, schema: JsonNode?, path: String, problems: MutableList<String>) {
        if (schema == null || value == null) return
        schema.get("\$ref")?.asString()?.let { ref -> return validate(value, spec.get("components").get("schemas").get(ref.substringAfterLast('/')), path, problems) }
        if (value.isNull) return
        listOf("oneOf", "anyOf").forEach { key ->
            schema.get(key)?.let { options ->
                val remaining = options.toList().filterNot { typesOf(it).isEmpty() && it.get("type") != null }
                if (remaining.size == 1) return validate(value, remaining[0], path, problems)
                return
            }
        }
        val types = typesOf(schema)
        when {
            value.isObject -> {
                if (types.isNotEmpty() && "object" !in types) return problems.add("$path: 객체가 아니라 ${types.joinToString("|")}이어야 한다").let { }
                val properties = schema.get("properties")
                val additional = schema.get("additionalProperties")
                if (properties == null) {
                    if (additional != null && additional.isObject) value.properties().forEach { (k, v) -> validate(v, additional, "$path.$k", problems) }
                    return
                }
                value.properties().forEach { (key, child) ->
                    val childSchema = properties.get(key)
                    if (childSchema == null) { if (additional == null) problems.add("$path.$key: 문서에 없는 키이다") } else validate(child, childSchema, "$path.$key", problems)
                }
                schema.get("required")?.forEach { name -> if (!value.has(name.asString())) problems.add("$path.${name.asString()}: 필수 키가 없다") }
            }
            value.isArray -> {
                if (types.isNotEmpty() && "array" !in types) return problems.add("$path: 배열이 아니라 ${types.joinToString("|")}이어야 한다").let { }
                val items = schema.get("items")
                value.forEachIndexed { i, child -> validate(child, items, "$path[$i]", problems) }
            }
            value.isString -> if (types.isNotEmpty() && "string" !in types) problems.add("$path: 문자열이 아니라 ${types.joinToString("|")}이어야 한다")
            value.isBoolean -> if (types.isNotEmpty() && "boolean" !in types) problems.add("$path: 불리언이 아니라 ${types.joinToString("|")}이어야 한다")
            value.isNumber -> if (types.isNotEmpty() && "integer" !in types && "number" !in types) problems.add("$path: 숫자가 아니라 ${types.joinToString("|")}이어야 한다")
        }
    }

    private companion object {
        /** 명세 주소이다. springdoc이 서버 주소를 요청 주소에서 이 경로를 떼어 구하므로 요청도 같은 주소로 만든다. */
        const val DOCS_PATH = "/-_-api/docs/openapi"
    }
}
