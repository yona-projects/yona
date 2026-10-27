package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.servlet.HandlerExecutionChain
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.ServletRequestPathUtils
import java.io.File
import kotlin.test.assertEquals

/**
 * v1.6 `conf/routes`의 306개 항목이 v2에서 처리되는지 하나씩 확인한다. 소스에서 어노테이션 경로를 정규식으로 뽑지 않고, 실제로 뜬 스프링의
 * 핸들러 매핑에 v1.6 요청을 넣어 어떤 핸들러가 받는지 본다. `{*path}`, 선택적 끝 슬래시, 정규식 제약 변수도 같은 방식으로 확인된다.
 *
 * 스프링 매핑이 받지 않는 요청은 서블릿 필터가 받는 것들이다([FILTER_HANDLED]). 필터가 실제로 그 요청을 받는지는 각 필터의 시험이 확인한다.
 * 결과 표는 `build/route-coverage.tsv`에 쓴다(v1.6 메서드, 경로, v1.6 액션, v2 핸들러).
 *
 * 원본: `conf/routes` (`src/test/resources/v16/routes.tsv`).
 */
class RouteCoverageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var context: ApplicationContext

    /** v1.6 경로 패턴을 요청 가능한 예시 주소로 바꾼다. 정규식 제약은 첫 번째 선택지, 없으면 `1`을 쓴다. */
    private fun sample(pattern: String): List<String> {
        var path = pattern
        path = Regex("\\$[A-Za-z]+<([^>]*)>").replace(path) { m ->
            val regex = m.groupValues[1]
            if (regex.contains('|')) regex.substringBefore('|') else "1"
        }
        // `/*paths`는 끝 슬래시를 떼는 라우트(`removeTrailer`)이므로 끝 슬래시가 있는 주소를 쓴다
        path = if (path == "/*paths") "/a/b/" else Regex("\\*[A-Za-z]+").replace(path, "a/b.txt")
        path = Regex(":[A-Za-z]+").replace(path, "1")
        return if (path.endsWith("/?")) listOf(path.removeSuffix("/?"), path.removeSuffix("?")) else listOf(path)
    }

    /** 요청 본문 형식에 따라 갈리는 매핑(`consumes`)까지 찾도록 형식을 바꿔 가며 확인한다. */
    private fun lookup(method: String, uri: String): String? {
        for (contentType in listOf(null, "application/x-www-form-urlencoded", "multipart/form-data", "application/json")) {
            lookup(method, uri, contentType)?.let { return it }
        }
        return null
    }

    private fun lookup(method: String, uri: String, contentType: String?): String? {
        val mappings = context.getBeansOfType(HandlerMapping::class.java).values.sortedBy { (it as? org.springframework.core.Ordered)?.order ?: Int.MAX_VALUE }
        for (mapping in mappings) {
            val request = MockHttpServletRequest(method, uri)
            request.requestURI = uri
            contentType?.let { request.contentType = it }
            ServletRequestPathUtils.parseAndCache(request)
            val chain: HandlerExecutionChain? = try { mapping.getHandler(request) } catch (e: Exception) { null }
            val handler = chain?.handler ?: continue
            // 정적 자원 핸들러는 모든 경로에 맞으므로 `/assets/`일 때만 이 라우트를 받는 것으로 본다
            if (handler is org.springframework.web.servlet.resource.ResourceHttpRequestHandler && !uri.startsWith("/assets/")) continue
            return handler.toString().removePrefix("dev.yona.").take(120)
        }
        return null
    }

    @Test
    fun `v1_6 라우트 306개는 스프링 매핑이나 필터가 받는다`() {
        val rows = File(javaClass.getResource("/v16/routes.tsv")!!.toURI()).readLines().filter { it.isNotBlank() }.map { it.split('\t') }
        assertEquals(306, rows.size)
        val unmatched = mutableListOf<String>()
        val table = StringBuilder()
        for ((method, pattern, action) in rows) {
            val results = sample(pattern).map { uri -> uri to lookup(method, uri) }
            val handler = results.firstNotNullOfOrNull { it.second }
            table.append("$method\t$pattern\t$action\t${handler ?: "-"}\n")
            // 선택적 끝 슬래시(`/?`)는 두 가지 모두 받아야 한다
            if (results.any { it.second == null }) unmatched += "$method $pattern"
        }
        File("build/route-coverage.tsv").also { it.parentFile.mkdirs() }.writeText(table.toString())
        val unexpected = unmatched.filter { it !in FILTER_HANDLED }
        assertEquals(emptyList<String>(), unexpected, "스프링 매핑에도 필터 목록에도 없는 v1.6 라우트")
    }

    @Test
    fun `가짜 서브버전 경로는 404이다 (v1_6은 프로젝트 이름이 비어 404)`() {
        val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context as org.springframework.web.context.WebApplicationContext).build()
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/!svn-fake/sevice/"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)
    }

    companion object {
        /** 스프링 핸들러 매핑 밖에서 서블릿 필터가 받는 v1.6 라우트이다. */
        val FILTER_HANDLED: Set<String> = setOf(
            // 매핑이 없는 주소는 `GlobalErrorHandler.notFound`가 끝 슬래시를 떼어 이동하거나 404를 낸다(`TrailingSlashRedirectTest`)
            "GET /*paths",
            // v1.6에서 프로젝트 이름이 빈 문자열이라 404인 가짜 경로이다. v2도 매핑이 없어 404이다(아래 시험)
            "POST /!svn-fake/sevice/"
        )
    }
}
