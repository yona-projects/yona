package dev.yona.web.svn

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.io.InputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.security.Principal
import java.util.Collections

/**
 * Jakarta 서블릿의 요청과 응답을 `javax.servlet` 서블릿 API로 바꿔 SVNKit의 DAV 서블릿(`DAVServlet`)에 넘긴다. v1.6 `utils.PlayServletRequest`,
 * `PlayServletResponse`, `PlayServletContext`(Play의 요청을 서블릿으로 바꾸던 클래스들)에 해당한다.
 *
 * DAV 서블릿은 요청과 응답의 몇 가지 메서드만 쓰므로 동적 프록시로 그 메서드만 연결하고 나머지는 [UnsupportedOperationException]으로 둔다
 * (v1.6의 어댑터도 같다). 요청의 값은 v1.6과 같고, 아래 두 가지만 다르다: 스킴과 호스트, 포트는 실제 요청의 값을 쓰고(v1.6은 항상 `http`이다),
 * 응답은 파이프로 상태를 기다리지 않고 서블릿 응답에 바로 쓴다(A-98).
 */
object JavaxServletBridge {

    private val loader = JavaxServletBridge::class.java.classLoader

    /**
     * 응답 헤더 값에서 ASCII 바깥의 글자를 UTF-8 퍼센트 인코딩으로 바꾼다. DAV 서블릿은 파일 이름이 든 `ETag`(`1//한글 a.txt`)를 그대로 쓰는데,
     * Tomcat은 이런 헤더를 거부해 응답이 500이 된다(v1.6의 Netty는 오류 없이 내보냈다). 값은 ETag로 같은 항목을 구별하는 데만 쓰인다(A-137).
     */
    internal fun headerValue(value: String?): String? =
        if (value == null || value.all { it.code in 0x20..0x7E || it == '\t' }) value
        else buildString { value.forEach { c -> if (c.code in 0x20..0x7E || c == '\t') append(c) else c.toString().toByteArray(Charsets.UTF_8).forEach { append('%').append("%02X".format(it)) } } }

    /**
     * @param real 실제 요청
     * @param pathInfo `소유자` 뒤의 경로(프로젝트 이름부터). 디코딩한 그대로 넘긴다. 1.10.11은 컨테이너가 디코딩한 값을 기대한다(v1.6의 1.8.15는 인코딩해서 받아 직접 디코딩했다)
     * @param username 인증한 사용자의 로그인 ID. 익명이면 빈 문자열
     * @param serverInfo 서블릿 컨텍스트가 알려 줄 서버 이름 (`yona.server`)
     */
    fun request(real: HttpServletRequest, pathInfo: String, username: String, serverInfo: String): javax.servlet.http.HttpServletRequest {
        var characterEncoding: String? = null
        val attributes = HashMap<String, Any?>()
        val session = session(serverInfo)
        // 서블릿 경로는 컨텍스트 경로 뒤 `/svn/소유자/`까지이다. 원본 주소에서 잘라 퍼센트 인코딩 길이에 영향을 받지 않게 한다.
        val relative = real.requestURI.removePrefix(real.contextPath)
        val servletPath = Regex("^/[^/]+/[^/]*/").find(relative)?.value ?: relative
        // DAV 서블릿은 응답의 href를 `컨텍스트 경로 + 서블릿 경로(앞의 /를 뗀 것)`로 만든다. 컨텍스트 경로가 비어 있으면 `/`가 하나 더 있어야
        // 절대 경로(`/svn/소유자/…`)가 남는다. svn 명령줄 클라이언트는 `/`가 빠진 href를 쓰지 못한다(v1.6은 이 보정이 없다, A-98).
        val davServletPath = if (real.contextPath.isEmpty()) "/$servletPath" else servletPath
        return proxy(javax.servlet.http.HttpServletRequest::class.java) { method, args ->
            when (method.name) {
                "getPathInfo" -> pathInfo
                "getRemoteUser" -> username
                "getUserPrincipal" -> Principal { username }
                "getServletPath" -> davServletPath

                "getRequestURI" -> real.requestURI
                "getRequestURL" -> StringBuffer(listOfNotNull(real.requestURI, real.queryString).joinToString("?"))
                "getContextPath" -> real.contextPath
                "getQueryString" -> real.queryString
                "getMethod" -> real.method
                "getHeader" -> lockDepth(real, args[0] as String) ?: real.getHeader(args[0] as String)
                "getHeaders" -> lockDepth(real, args[0] as String)?.let { Collections.enumeration(listOf(it)) } ?: real.getHeaders(args[0] as String)
                "getHeaderNames" -> real.headerNames
                "getDateHeader" -> try { real.getDateHeader(args[0] as String) } catch (e: IllegalArgumentException) { -1L }
                "getIntHeader" -> real.getHeader(args[0] as String)?.toInt() ?: -1
                "getContentLength" -> real.contentLength
                "getContentLengthLong" -> real.contentLengthLong
                "getContentType" -> real.contentType
                "getCharacterEncoding" -> characterEncoding
                "setCharacterEncoding" -> { characterEncoding = args[0] as String?; null }
                "getScheme" -> real.scheme
                "getServerName" -> real.serverName
                "getServerPort" -> real.serverPort
                "isSecure" -> real.isSecure
                "getRemoteAddr" -> real.remoteAddr
                "getRemoteHost" -> null
                "getAuthType" -> null
                // v1.6은 인코딩한 경로로 만든다
                "getPathTranslated" -> File(org.tmatesoft.svn.core.internal.util.SVNEncodingUtil.uriEncode(pathInfo)).absolutePath
                "getRealPath" -> File(args[0] as String).absolutePath
                "getSession" -> session
                "getAttribute" -> attributes[args[0] as String]
                "setAttribute" -> { attributes[args[0] as String] = args[1]; null }
                "removeAttribute" -> { attributes.remove(args[0] as String); null }
                "getAttributeNames" -> Collections.enumeration(attributes.keys)
                "getLocale" -> real.locale
                "getLocales" -> real.locales
                "getParameter" -> real.getParameter(args[0] as String)
                "getParameterValues" -> real.getParameterValues(args[0] as String)
                "getParameterMap" -> real.parameterMap
                "getParameterNames" -> real.parameterNames
                "isAsyncStarted", "isAsyncSupported" -> false
                "getInputStream" -> servletInput(real.inputStream)
                else -> throw UnsupportedOperationException(method.name)
            }
        }
    }

    /**
     * `LOCK` 요청의 `Depth` 머리글이 없거나 `infinity`이면 `0`으로 바꿔 알린다. svn 명령줄 클라이언트는 `Depth` 없이 파일 하나를 잠그는데,
     * 이 SVNKit 버전(1.10)의 `LOCK` 처리기는 깊이 `infinity`로 파일을 잠그려 하면 `400`으로 거절한다(v1.6의 1.8은 받았다).
     * svn은 파일만 잠그므로 깊이 `0`과 결과가 같다.
     */
    private fun lockDepth(real: HttpServletRequest, name: String): String? =
        if (real.method == "LOCK" && name.equals("Depth", ignoreCase = true) && real.getHeader("Depth").let { it == null || it.equals("infinity", ignoreCase = true) }) "0" else null

    /** 응답의 상태, 헤더, 본문을 실제 응답에 바로 쓴다. 헤더를 더할 때는 v1.6처럼 같은 이름의 앞선 값 뒤에 쉼표로 이어 붙인다. */
    fun response(real: HttpServletResponse): javax.servlet.http.HttpServletResponse {
        fun addHeader(name: String, value: String) {
            val head = real.getHeader(name)
            real.setHeader(name, if (head.isNullOrBlank()) value else "$head,$value")
        }
        return proxy(javax.servlet.http.HttpServletResponse::class.java) { method, args ->
            when (method.name) {
                "setStatus" -> { real.status = args[0] as Int; null }
                "getStatus" -> real.status
                "setContentType" -> { real.contentType = args[0] as String?; null }
                "getContentType" -> real.contentType
                "setContentLength" -> { real.setContentLength(args[0] as Int); null }
                "setContentLengthLong" -> { real.setContentLengthLong(args[0] as Long); null }
                "setHeader" -> { real.setHeader(args[0] as String, headerValue(args[1] as String?)); null }
                "addHeader" -> { addHeader(args[0] as String, headerValue(args[1] as String)!!); null }
                "addIntHeader" -> { addHeader(args[0] as String, (args[1] as Int).toString()); null }
                "setIntHeader" -> { real.setIntHeader(args[0] as String, args[1] as Int); null }
                "addDateHeader" -> { addHeader(args[0] as String, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.Instant.ofEpochMilli(args[1] as Long).atZone(java.time.ZoneOffset.UTC))); null }
                "containsHeader" -> real.containsHeader(args[0] as String)
                "getHeader" -> real.getHeader(args[0] as String)
                "getHeaders" -> real.getHeaders(args[0] as String)
                "getHeaderNames" -> real.headerNames
                "getCharacterEncoding" -> real.characterEncoding
                "setCharacterEncoding" -> { real.characterEncoding = args[0] as String?; null }
                "getOutputStream" -> servletOutput(real)
                // 인코딩을 정하지 않았으면 UTF-8로 쓴다 (v1.6 `Config.getCharset`).
                "getWriter" -> { if (real.contentType?.contains("charset", ignoreCase = true) != true) real.characterEncoding = "UTF-8"; real.writer }
                "flushBuffer" -> { real.flushBuffer(); null }
                "isCommitted" -> real.isCommitted
                "resetBuffer" -> { real.writer.flush(); null }
                // 서블릿 컨테이너의 오류 화면을 거치지 않고 상태와 본문을 직접 정한다 (v1.6 `sendError`).
                "sendError" -> {
                    real.status = args[0] as Int
                    val message = args.getOrNull(1) as String?
                    if (message != null) {
                        real.contentType = "text/plain"
                        real.characterEncoding = "UTF-8"
                        real.writer.write(message)
                    }
                    null
                }
                "sendRedirect" -> { real.setHeader("Location", args[0] as String); real.status = 302; null }
                else -> throw UnsupportedOperationException(method.name)
            }
        }
    }

    /** 이름이 `SVNParentPath`이면 [parentPath]를, 그 밖의 초기화 값은 `null`로 돌려주는 서블릿 설정이다 (v1.6 `createDavServlet`). */
    fun config(parentPath: String, serverInfo: String): javax.servlet.ServletConfig {
        val context = context(serverInfo)
        return proxy(javax.servlet.ServletConfig::class.java) { method, args ->
            when (method.name) {
                "getInitParameter" -> if (args[0] == "SVNParentPath") parentPath else null
                "getServletContext" -> context
                else -> throw UnsupportedOperationException(method.name)
            }
        }
    }

    private fun context(serverInfo: String): javax.servlet.ServletContext = proxy(javax.servlet.ServletContext::class.java) { method, _ ->
        when (method.name) {
            // v1.6 `PlayServletContext.getServerInfo()`는 `application.server`를 돌려준다
            "getServerInfo" -> serverInfo
            else -> throw UnsupportedOperationException(method.name)
        }
    }

    private fun session(serverInfo: String): javax.servlet.http.HttpSession {
        val context = context(serverInfo)
        val created = System.currentTimeMillis()
        return proxy(javax.servlet.http.HttpSession::class.java) { method, _ ->
            when (method.name) {
                "getServletContext" -> context
                "getLastAccessedTime", "getCreationTime" -> created
                else -> throw UnsupportedOperationException(method.name)
            }
        }
    }

    /**
     * 요청 본문 스트림이다. v1.6은 본문을 모두 받아 `ByteArrayInputStream`으로 넘겼으므로 `available()`이 남은 바이트 수이다.
     * 길이 없이 쪼개 보내는(chunked) 본문은 톰캣 스트림의 `available()`이 아직 읽지 않은 동안 0이라, 그 값으로 본문이 있는지 보는
     * SVNKit의 처리기(`LOCK` 등)가 빈 본문으로 오해한다. 첫 바이트를 먼저 읽어 두고 그만큼을 더해 알린다.
     */
    private fun servletInput(stream: InputStream): javax.servlet.ServletInputStream = object : javax.servlet.ServletInputStream() {
        private var finished = false
        private var peeked = false
        private var head = -1

        private fun peek() {
            if (!peeked) { head = stream.read(); peeked = true; if (head < 0) finished = true }
        }

        override fun read(): Int {
            if (peeked && head >= 0) return head.also { head = -1 }
            peek()
            if (head >= 0) return head.also { head = -1 }
            return stream.read().also { if (it < 0) finished = true }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            peek()
            if (head >= 0) {
                b[off] = head.toByte(); head = -1
                if (len == 1) return 1
                val more = stream.available().coerceAtMost(len - 1)
                val n = if (more > 0) stream.read(b, off + 1, more) else 0
                return 1 + n.coerceAtLeast(0)
            }
            return stream.read(b, off, len).also { if (it < 0) finished = true }
        }

        override fun available(): Int {
            peek()
            return (if (head >= 0) 1 else 0) + if (finished) 0 else stream.available()
        }

        override fun close() = stream.close()
        override fun isFinished(): Boolean = finished
        override fun isReady(): Boolean = true
        override fun setReadListener(readListener: javax.servlet.ReadListener?) = throw UnsupportedOperationException("setReadListener")
    }

    private fun servletOutput(real: HttpServletResponse): javax.servlet.ServletOutputStream = object : javax.servlet.ServletOutputStream() {
        private val out get() = real.outputStream

        override fun write(b: Int) = out.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun flush() = out.flush()
        override fun close() = out.close()
        override fun isReady(): Boolean = true
        override fun setWriteListener(writeListener: javax.servlet.WriteListener?) = throw UnsupportedOperationException("setWriteListener")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, handler: (Method, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(loader, arrayOf(type), InvocationHandler { proxy, method, args ->
            try {
                when {
                    method.declaringClass == Any::class.java -> when (method.name) {
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args[0]
                        else -> type.simpleName + "@" + Integer.toHexString(System.identityHashCode(proxy))
                    }
                    else -> handler(method, args ?: emptyArray())
                }
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }) as T
}
