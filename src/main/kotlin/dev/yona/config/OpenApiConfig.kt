package dev.yona.config

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.security.ApiRequirement
import dev.yona.security.ApiScopeRules
import dev.yona.web.api.docs.ApiTags
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI 문서의 바탕 설정이다(로드맵 X-31, `decisions.md` D-50). 문서는 springdoc이 컨트롤러의 어노테이션에서 만들고, 여기서는 제목과 인증 방식, 태그 순서를 정하며
 * **동작마다 반복되는 내용을 범위 대응표([ApiScopeRules])에서 채운다**: 토큰 인증 요구, 필요한 범위, 공통 오류 응답(401, 403), 경로 매개변수 설명.
 * 범위를 어노테이션에 따로 적지 않아 문서와 실제 검사가 어긋나지 않는다.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun yonaOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info().title("Yona 외부 REST API").version("v1").description(
                """
                Yona의 외부 도구(명령줄 도구, 자동화, AI 에이전트, 이슈 이전 도구)가 쓰는 REST API이다. 모든 경로는 `/-_-api/v1` 아래에 있고 **개인용 API 토큰으로만** 인증한다(세션과 쿠키는 무시한다).

                ## 인증
                설정 화면에서 만든 토큰을 `Authorization: token <토큰>` 헤더로 보낸다(`Bearer <토큰>`과 `Yona-Token: <토큰>`도 받는다). 이 화면의 **Authorize** 단추에 `token <토큰>`을 넣으면 `Try it out`으로 바로 호출해 볼 수 있다.

                ## 범위와 권한
                토큰이 할 수 있는 일은 **범위가 허용하는 일과 토큰 주인의 실제 권한의 교집합**이다. 동작마다 필요한 범위가 설명에 있다. 범위는 GitHub의 Classic 범위와 같은 이름과 포함 관계를 쓴다(`repo`는 `public_repo`와 웹훅 범위를, `admin:org`는 `write:org`와 `read:org`를 포함한다). 범위가 있어도 사용자에게 그 일을 할 권한이 없으면 `403`이다.

                ## 오류
                실패하면 상태 코드와 함께 `{"message": "..."}`를 돌려준다. `401`은 토큰이 없거나 틀림, `403`은 범위나 권한 부족, `404`는 없는 자원, `400`은 요청 본문이나 값이 틀림, `409`는 이미 있는 이름 같은 충돌이다. 같은 주소에서 토큰 인증이 5번 실패하면 일정 시간 `429`로 막는다.

                ## 시각과 경로
                시각은 `yyyy-MM-dd'T'HH:mm:ssZ` 문자열이다. 프로젝트는 `{owner}/{projectName}`로 가리키고, 소유자는 사용자 로그인 ID나 조직 이름이다.
                """.trimIndent()
            )
        )
        .components(
            Components().addSecuritySchemes(
                TOKEN_AUTH,
                SecurityScheme().type(SecurityScheme.Type.APIKEY).`in`(SecurityScheme.In.HEADER).name("Authorization")
                    .description("개인용 API 토큰. 값은 `token <토큰>` 형식이다(예: `token yona_pat_...`).")
            )
        )
        .tags(ApiTags.ALL)

    /** 동작마다 반복되는 내용(인증, 범위, 공통 오류, 매개변수 설명)을 채운다. */
    @Bean
    fun yonaOpenApiCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.components = (openApi.components ?: Components()).addSchemas(ERROR_SCHEMA, errorSchema())
        openApi.paths?.forEach { (path, item) -> item.readOperationsMap().forEach { (method, operation) -> customize(path, method, operation) } }
        normalizeMediaTypes(openApi)
        markRequiredProperties(openApi)
    }

    /** 응답과 요청 본문의 미디어 형식이 지정되지 않아 와일드카드 형식으로 나온 것을 `application/json`으로 바꾼다(이 API의 본문은 모두 JSON이다. 멀티파트는 그대로 둔다). */
    private fun normalizeMediaTypes(openApi: OpenAPI) {
        fun fix(content: Content?) {
            val any = content?.get("*/*") ?: return
            content.remove("*/*")
            if (!content.containsKey("application/json")) content["application/json"] = any
        }
        openApi.paths?.values?.forEach { item ->
            item.readOperations().forEach { operation ->
                fix(operation.requestBody?.content)
                operation.responses?.values?.forEach { fix(it.content) }
            }
        }
    }

    /**
     * 응답에만 쓰이는 모델의 필수 항목을 정한다. 문서용 모델에서 널을 허용하지 않는 항목은 JSON에 항상 있는 키이고(`?`가 붙은 항목은 값이 `null`이거나 키가 없다),
     * 이를 `required`로 옮겨 클라이언트 생성기와 계약 시험(`OpenApiContractSupport`)이 키가 빠진 응답을 알아보게 한다. 요청 본문에 쓰이는 모델은 `@Schema(requiredMode)`로
     * 밝힌 것만 필수이다(널을 허용하지 않아도 API가 생략을 받아 줄 수 있어서이다).
     */
    private fun markRequiredProperties(openApi: OpenAPI) {
        val schemas = openApi.components?.schemas ?: return
        val request = mutableSetOf<String>()
        val response = mutableSetOf<String>()
        fun collect(schema: Schema<*>?, into: MutableSet<String>) {
            if (schema == null) return
            schema.`$ref`?.substringAfterLast('/')?.let { name -> if (into.add(name)) collect(schemas[name], into) }
            schema.properties?.values?.forEach { collect(it, into) }
            collect(schema.items, into)
            (schema.additionalProperties as? Schema<*>)?.let { collect(it, into) }
            listOfNotNull(schema.oneOf, schema.anyOf, schema.allOf).flatten().forEach { collect(it, into) }
        }
        openApi.paths?.values?.forEach { item ->
            item.readOperations().forEach { operation ->
                operation.requestBody?.content?.values?.forEach { collect(it.schema, request) }
                operation.responses?.values?.forEach { r -> r.content?.values?.forEach { collect(it.schema, response) } }
            }
        }
        (response - request).forEach { name ->
            val schema = schemas[name] ?: return@forEach
            val properties = schema.properties ?: return@forEach
            if (!schema.required.isNullOrEmpty()) return@forEach
            val required = properties.filter { (_, property) -> !isNullable(property) }.keys.toList()
            if (required.isNotEmpty()) schema.required = required
        }
    }

    private fun isNullable(schema: Schema<*>): Boolean =
        schema.nullable == true || schema.types?.contains("null") == true || schema.type == "null" ||
            listOfNotNull(schema.oneOf, schema.anyOf).flatten().any { it.types?.contains("null") == true || it.type == "null" }

    private fun customize(path: String, method: PathItem.HttpMethod, operation: Operation) {
        val requirement = ApiScopeRules.requirementByPlainPath(method.name, path)
        operation.parameters?.forEach { parameter -> if (parameter.description.isNullOrBlank()) PARAMETER_DESCRIPTIONS[parameter.name]?.let { parameter.description = it } }
        if (requirement == null || requirement == ApiRequirement.None) return
        operation.security = listOf(SecurityRequirement().addList(TOKEN_AUTH))
        val scopeText = scopeText(requirement)
        operation.description = (operation.description?.trimEnd().orEmpty() + "\n\n**필요한 범위**: $scopeText").trimStart()
        operation.addExtension("x-required-scopes", scopeNames(requirement))
        val responses = operation.responses ?: io.swagger.v3.oas.models.responses.ApiResponses().also { operation.responses = it }
        responses.addApiResponse("401", errorResponse("토큰이 없거나 틀렸다."))
        responses.addApiResponse("403", errorResponse("토큰의 범위가 모자라거나 토큰 주인에게 그 일을 할 권한이 없다."))
    }

    private fun errorResponse(description: String) = ApiResponse().description(description)
        .content(Content().addMediaType("application/json", MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/$ERROR_SCHEMA"))))

    private fun errorSchema(): Schema<*> = Schema<Any>().type("object").description("실패 응답이다.")
        .addProperty("message", Schema<String>().type("string").description("실패 이유").example("Forbidden request"))

    /** 요구 범위를 사람이 읽을 문장으로 만든다. 포함 관계로 더 넓은 범위도 허용함을 덧붙인다. */
    private fun scopeText(requirement: ApiRequirement): String = when (requirement) {
        ApiRequirement.Repo -> "`repo`(공개 프로젝트이면 `public_repo`도 가능)"
        ApiRequirement.RepoOnly -> "`repo`"
        ApiRequirement.RepoRead -> "`repo` 또는 `read:repo`(공개 프로젝트이면 `public_repo`도 가능)"
        ApiRequirement.RepoOnlyRead -> "`repo` 또는 `read:repo`"
        ApiRequirement.AnyRepoRead -> "`repo`, `read:repo` 또는 `public_repo`"
        ApiRequirement.AnyRepo -> "`repo` 또는 `public_repo`"
        is ApiRequirement.Scope -> {
            val wider = ApiScopes.selectable(siteManager = true).filter { it != requirement.name && ApiScopes.implies(setOf(it), requirement.name) }
            "`${requirement.name}`" + if (wider.isEmpty()) "" else "(이 범위를 포함하는 ${wider.joinToString(", ") { "`$it`" }}도 가능)"
        }
        ApiRequirement.None -> "없음"
    }

    private fun scopeNames(requirement: ApiRequirement): List<String> = when (requirement) {
        ApiRequirement.Repo -> listOf("repo", "public_repo")
        ApiRequirement.RepoOnly -> listOf("repo")
        ApiRequirement.RepoRead -> listOf("repo", "read:repo", "public_repo")
        ApiRequirement.RepoOnlyRead -> listOf("repo", "read:repo")
        ApiRequirement.AnyRepoRead -> listOf("repo", "read:repo", "public_repo")
        ApiRequirement.AnyRepo -> listOf("repo", "public_repo")
        is ApiRequirement.Scope -> listOf(requirement.name)
        ApiRequirement.None -> emptyList()
    }

    private companion object {
        const val TOKEN_AUTH = "tokenAuth"
        const val ERROR_SCHEMA = "ErrorResponse"

        /** 경로 매개변수의 공통 설명이다. 동작에서 따로 밝히지 않은 매개변수에 쓴다. */
        val PARAMETER_DESCRIPTIONS = mapOf(
            "owner" to "프로젝트의 소유자이다. 사용자 로그인 ID나 조직 이름이다.",
            "projectName" to "프로젝트 이름이다.",
            "number" to "프로젝트 안에서 매겨진 번호이다(이슈, 게시글, 풀 리퀘스트).",
            "commentId" to "댓글 번호이다.",
            "labelId" to "라벨 번호이다.",
            "milestoneId" to "마일스톤 번호이다.",
            "hookId" to "웹훅 번호이다.",
            "org" to "조직 이름이다.",
            "loginId" to "사용자 로그인 ID이다.",
            "user" to "사용자 로그인 ID이다.",
            "threadId" to "코드 리뷰 스레드 번호이다.",
            "reviewId" to "리뷰 번호이다.",
            "reviewer" to "리뷰어의 로그인 ID이다.",
            "id" to "GPG 키(주 키) 번호이다. 목록 응답의 `id`이다.",
            "name" to "이름이다. 태그는 슬래시가 있어도 되고(`release/1.0`), 위키 페이지는 제목의 공백을 `-`로 바꾼 값이다(`My-First-Page`)."
        )
    }
}
