package dev.yona.mcp

import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditOutcome
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.site.SiteSettingService
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.util.UriUtils
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale

/** MCP 도구가 알리는 오류이다. 메시지는 AI 에이전트가 그대로 읽는다. */
class McpToolException(message: String) : RuntimeException(message)

/**
 * MCP 도구가 기존 외부 REST API(`/-_-api/v1`)를 같은 길로 부르게 하는 다리이다(추가 개발 항목 X-6, `decisions.md` D-72).
 * 컨트롤러를 직접 부르면 접근 검사(`@AnonymousCheck`, `@IsCreatable` 등 인터셉터가 맡는 검사)와 범위 검사(`ApiScopeInterceptor`)를 건너뛰므로,
 * **서블릿 디스패처에 가상 요청을 넣어** 인터셉터, 예외 처리, 메시지 변환을 REST와 똑같이 거친다. 그래서 도구의 권한과 범위 규칙은 REST와 항상 같다
 * (읽기는 `read:repo`나 `repo`, 쓰기는 `repo`, 프로젝트 접근 권한은 사용자의 실제 권한).
 *
 * 인증은 [McpAccessFilter]가 이미 보안 컨텍스트에 채웠으므로 필터 체인은 다시 지나지 않는다. 가상 요청은 원래 요청의 속성과 분리되고 응답은 따로 모은다.
 */
@Component
class McpRestBridge(
    private val dispatcher: DispatcherServlet,
    private val settings: SiteSettingService,
    private val audit: AuditRecorder,
    private val meters: MeterRegistry
) {
    private val mapper = JsonMapper.builder().build()

    /** REST 호출의 결과이다. [body]는 JSON이면 읽은 값(맵이나 목록)이고 아니면 글이다. */
    private class Outcome(val status: Int, val contentType: String?, val text: String)

    /**
     * [tool] 도구가 [method]와 [path]로 REST API를 부르고 응답 본문을 돌려준다. [query]는 쿼리 문자열이고 [body]는 JSON 본문이다. 쓰기(`GET`이 아닌 요청)는
     * 관리자가 쓰기 도구를 껐으면 거부한다. 2xx가 아니면 [McpToolException]이다.
     */
    fun call(tool: String, method: String, path: String, query: Map<String, Any?> = emptyMap(), body: Map<String, Any?>? = null, target: String? = null): Any? {
        val write = !method.equals("GET", ignoreCase = true)
        val result = try {
            if (write && !settings.mcpWriteEnabled()) throw McpToolException("관리자가 MCP 쓰기 도구를 껐습니다. / The administrator disabled MCP write tools.")
            val outcome = dispatch(method.uppercase(), path, query.filterValues { it != null }.mapValues { it.value.toString() }, body?.let { mapper.writeValueAsBytes(it) })
            if (outcome.status !in 200..299) throw McpToolException(errorMessage(outcome))
            toValue(outcome)
        } catch (e: McpToolException) {
            record(tool, write, target, e.message, false)
            throw e
        }
        record(tool, write, target, null, true)
        return result
    }

    private fun record(tool: String, write: Boolean, target: String?, message: String?, ok: Boolean) {
        meters.counter("yona.mcp.tool_calls", "tool", tool, "result", if (ok) "ok" else "error").increment()
        if (write) audit.recordIndependent(AuditEvent("mcp.tool_called", if (ok) AuditOutcome.SUCCESS else AuditOutcome.FAILURE, targetType = "project", targetLabel = target, details = mapOf("tool" to tool, "error" to message)))
    }

    private fun dispatch(method: String, path: String, query: Map<String, String>, body: ByteArray?): Outcome {
        val original = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: throw McpToolException("요청 문맥이 없습니다. / No request context.")
        ensureInitialized(original)
        val response = CapturedResponse()
        val virtual = VirtualRequest(original, method, path, query, body)
        try {
            dispatcher.service(virtual, response)
        } catch (e: Exception) {
            // 일부 REST 경로는 오류를 화면(`ErrorViews`)으로 돌려준다. 디스패처는 화면을 그리기 전에 상태를 먼저 정하므로, 화면을 가상 요청에서 그리지 못해도 상태는 알 수 있다.
            // 오류 상태가 정해져 있으면 그 상태를 알리고, 아니면 처리하지 못한 것이다
            if (response.getStatus() >= 400) return Outcome(response.getStatus(), response.contentType, "")
            throw McpToolException("요청을 처리하지 못했습니다: ${(e.cause ?: e).message ?: e.javaClass.simpleName}")
        }
        return Outcome(response.getStatus(), response.contentType, response.text())
    }

    /**
     * 디스패처가 서블릿 컨테이너에 등록되어 시작된 적이 없으면(MockMvc 시험처럼 컨테이너가 없는 환경) 직접 시작한다. 운영에서는 이미 시작되어 있어 아무것도 하지 않는다.
     */
    private fun ensureInitialized(original: HttpServletRequest) {
        if (dispatcher.servletConfig != null) return
        synchronized(dispatcher) {
            if (dispatcher.servletConfig != null) return
            dispatcher.init(object : jakarta.servlet.ServletConfig {
                override fun getServletName(): String = "dispatcherServlet"
                override fun getServletContext(): jakarta.servlet.ServletContext = original.servletContext
                override fun getInitParameter(name: String): String? = null
                override fun getInitParameterNames(): java.util.Enumeration<String> = Collections.emptyEnumeration()
            })
        }
    }

    private fun errorMessage(outcome: Outcome): String {
        val detail = if (outcome.contentType?.contains("json") == true) runCatching { mapper.readTree(outcome.text).get("message")?.asString() }.getOrNull() else null
        val reason = when (outcome.status) {
            401 -> "인증이 필요합니다."
            403 -> "이 작업을 할 권한이나 범위가 없습니다."
            404 -> "찾을 수 없습니다."
            else -> "요청이 거부되었습니다."
        }
        return "$reason (${outcome.status})" + (detail?.let { ": $it" } ?: "")
    }

    private fun toValue(outcome: Outcome): Any? {
        if (outcome.status == 204 || outcome.text.isBlank()) return mapOf("ok" to true)
        val value: Any? = if (outcome.contentType?.contains("json") == true) runCatching { mapper.readValue(outcome.text, Any::class.java) }.getOrNull() ?: outcome.text else outcome.text
        if (outcome.text.length > MAX_CHARS) {
            return mapOf("truncated" to true, "totalLength" to outcome.text.length, "content" to outcome.text.take(MAX_CHARS), "message" to "결과가 길어 앞부분만 보냈습니다.")
        }
        return value
    }

    /** 가상 요청이다. 메서드, 주소, 쿼리, 본문만 바꾸고 속성은 따로 둔다(원래 `/mcp` 요청의 속성을 건드리지 않는다). */
    private class VirtualRequest(
        original: HttpServletRequest, private val virtualMethod: String, private val route: String, private val query: Map<String, String>, private val body: ByteArray?
    ) : HttpServletRequestWrapper(original) {
        private val attributes = HashMap<String, Any?>()
        private val encodedPath = route.split('/').joinToString("/") { it }

        override fun getMethod(): String = virtualMethod
        override fun getRequestURI(): String = contextPath.orEmpty() + encodedPath
        override fun getRequestURL(): StringBuffer {
            val base = super.getRequestURL().toString().let { it.substring(0, it.indexOf(super.getRequestURI())) }
            return StringBuffer(base + requestURI)
        }
        override fun getServletPath(): String = encodedPath
        override fun getPathInfo(): String? = null
        override fun getQueryString(): String? = query.entries.joinToString("&") { "${UriUtils.encodeQueryParam(it.key, Charsets.UTF_8)}=${UriUtils.encodeQueryParam(it.value, Charsets.UTF_8)}" }.ifEmpty { null }
        override fun getAttribute(name: String): Any? = attributes[name]
        override fun getAttributeNames(): java.util.Enumeration<String> = Collections.enumeration(attributes.keys)
        override fun setAttribute(name: String, o: Any?) { if (o == null) attributes.remove(name) else attributes[name] = o }
        override fun removeAttribute(name: String) { attributes.remove(name) }
        override fun getContentType(): String? = if (body != null) "application/json;charset=UTF-8" else null
        override fun getCharacterEncoding(): String = "UTF-8"
        override fun getContentLength(): Int = body?.size ?: -1
        override fun getContentLengthLong(): Long = (body?.size ?: -1).toLong()
        override fun getInputStream(): jakarta.servlet.ServletInputStream {
            val stream = ByteArrayInputStream(body ?: ByteArray(0))
            return object : jakarta.servlet.ServletInputStream() {
                override fun read(): Int = stream.read()
                override fun isFinished(): Boolean = stream.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(listener: jakarta.servlet.ReadListener?) {}
            }
        }
        override fun getReader(): java.io.BufferedReader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8))
        override fun getParameter(name: String): String? = query[name]
        override fun getParameterValues(name: String): Array<String>? = query[name]?.let { arrayOf(it) }
        override fun getParameterNames(): java.util.Enumeration<String> = Collections.enumeration(query.keys)
        override fun getParameterMap(): Map<String, Array<String>> = query.mapValues { arrayOf(it.value) }
        override fun getHeader(name: String): String? = when (name.lowercase()) {
            "content-type" -> contentType
            "accept" -> "application/json"
            "content-length" -> body?.size?.toString()
            else -> super.getHeader(name)
        }
        override fun getDispatcherType(): jakarta.servlet.DispatcherType = jakarta.servlet.DispatcherType.REQUEST
    }

    /** 가상 요청의 응답이다. 실제 응답에는 아무것도 쓰지 않고 상태, 형식, 본문만 모은다. */
    private class CapturedResponse : HttpServletResponseWrapper(unusedResponse()) {
        private val buffer = ByteArrayOutputStream()
        private var code = 200
        private var type: String? = null
        private var encoding: String = "UTF-8"
        private val headers = LinkedHashMap<String, MutableList<String>>()
        private var printWriter: PrintWriter? = null
        private var committed = false

        override fun getContentType(): String? = type
        fun text(): String { printWriter?.flush(); return buffer.toString(Charsets.UTF_8) }

        override fun setStatus(sc: Int) { code = sc }
        override fun getStatus(): Int = code
        override fun sendError(sc: Int) { code = sc; committed = true }
        override fun sendError(sc: Int, msg: String?) { code = sc; committed = true }
        override fun sendRedirect(location: String?) { code = 302; headers["Location"] = mutableListOf(location.orEmpty()); committed = true }
        override fun setContentType(type: String?) { this.type = type }
        override fun setCharacterEncoding(charset: String?) { encoding = charset ?: "UTF-8" }
        override fun getCharacterEncoding(): String = encoding
        override fun setContentLength(len: Int) {}
        override fun setContentLengthLong(len: Long) {}
        override fun setHeader(name: String, value: String?) { headers[name] = mutableListOf(value.orEmpty()); if (name.equals("Content-Type", true)) type = value }
        override fun addHeader(name: String, value: String?) { headers.getOrPut(name) { mutableListOf() }.add(value.orEmpty()); if (name.equals("Content-Type", true)) type = value }
        override fun setIntHeader(name: String, value: Int) { setHeader(name, value.toString()) }
        override fun addIntHeader(name: String, value: Int) { addHeader(name, value.toString()) }
        override fun setDateHeader(name: String, date: Long) { setHeader(name, date.toString()) }
        override fun addDateHeader(name: String, date: Long) { addHeader(name, date.toString()) }
        override fun containsHeader(name: String): Boolean = headers.containsKey(name)
        override fun getHeader(name: String): String? = headers[name]?.firstOrNull()
        override fun getHeaders(name: String): Collection<String> = headers[name].orEmpty()
        override fun getHeaderNames(): Collection<String> = headers.keys
        override fun addCookie(cookie: jakarta.servlet.http.Cookie) {}
        override fun getOutputStream(): ServletOutputStream = object : ServletOutputStream() {
            override fun write(b: Int) { buffer.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { buffer.write(b, off, len) }
            override fun isReady(): Boolean = true
            override fun setWriteListener(listener: WriteListener?) {}
        }
        override fun getWriter(): PrintWriter = printWriter ?: PrintWriter(java.io.OutputStreamWriter(buffer, Charsets.UTF_8), false).also { printWriter = it }
        override fun flushBuffer() { printWriter?.flush(); committed = true }
        override fun isCommitted(): Boolean = committed
        override fun reset() { buffer.reset(); headers.clear(); code = 200 }
        override fun resetBuffer() { buffer.reset() }
        override fun setBufferSize(size: Int) {}
        override fun getBufferSize(): Int = 8192
        override fun setLocale(loc: Locale?) {}
        override fun getLocale(): Locale = Locale.getDefault()
    }

    companion object {
        /** 한 도구가 돌려주는 결과의 최대 글자 수이다. */
        const val MAX_CHARS = 200_000

        /** 쪽 크기의 기본값과 최대값이다. */
        const val DEFAULT_PAGE_SIZE = 30
        const val MAX_PAGE_SIZE = 100

        /** 경로 한 조각을 주소에 쓸 수 있게 인코딩한다. */
        fun seg(value: String): String = UriUtils.encodePathSegment(value, Charsets.UTF_8)

        /** 쪽 크기를 한도 안으로 맞춘다. */
        fun pageSize(requested: Int?): Int = (requested ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
    }
}

/** [McpRestBridge.CapturedResponse]의 바탕이 되는 응답이다. 어떤 메서드도 실제로 쓰지 않으므로 호출되면 안 된다. */
private fun unusedResponse(): HttpServletResponse = java.lang.reflect.Proxy.newProxyInstance(
    HttpServletResponse::class.java.classLoader, arrayOf<Class<*>>(HttpServletResponse::class.java),
    java.lang.reflect.InvocationHandler { _, method, _ -> throw IllegalStateException("가상 응답이 ${method.name}을 바탕 응답에 넘겼습니다.") }
) as HttpServletResponse
