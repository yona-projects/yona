package dev.yona.web.svn

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil
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
     * @param real 실제 요청
     * @param pathInfo `소유자` 뒤의 경로(프로젝트 이름부터). URI 인코딩해서 넘긴다
     * @param username 인증한 사용자의 로그인 ID. 익명이면 빈 문자열
     */
    fun request(real: HttpServletRequest, pathInfo: String, username: String): javax.servlet.http.HttpServletRequest {
        val encodedPathInfo = SVNEncodingUtil.uriEncode(pathInfo)
        var characterEncoding: String? = null
        val attributes = HashMap<String, Any?>()
        val session = session()
        return proxy(javax.servlet.http.HttpServletRequest::class.java) { method, args ->
            when (method.name) {
                "getPathInfo" -> encodedPathInfo
                "getRemoteUser" -> username
                "getUserPrincipal" -> Principal { username }
                "getServletPath" -> real.requestURI.let { it.substring(0, it.length - encodedPathInfo.length) }
                "getRequestURI" -> real.requestURI
                "getRequestURL" -> StringBuffer(listOfNotNull(real.requestURI, real.queryString).joinToString("?"))
                "getContextPath" -> ""
                "getQueryString" -> real.queryString
                "getMethod" -> real.method
                "getHeader" -> real.getHeader(args[0] as String)
                "getHeaders" -> real.getHeaders(args[0] as String)
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
                "getPathTranslated" -> File(encodedPathInfo).absolutePath
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
                "setHeader" -> { real.setHeader(args[0] as String, args[1] as String?); null }
                "addHeader" -> { addHeader(args[0] as String, args[1] as String); null }
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
    fun config(parentPath: String): javax.servlet.ServletConfig {
        val context = context()
        return proxy(javax.servlet.ServletConfig::class.java) { method, args ->
            when (method.name) {
                "getInitParameter" -> if (args[0] == "SVNParentPath") parentPath else null
                "getServletContext" -> context
                else -> throw UnsupportedOperationException(method.name)
            }
        }
    }

    private fun context(): javax.servlet.ServletContext = proxy(javax.servlet.ServletContext::class.java) { method, _ ->
        when (method.name) {
            "getServerInfo" -> "Yona"
            else -> throw UnsupportedOperationException(method.name)
        }
    }

    private fun session(): javax.servlet.http.HttpSession {
        val context = context()
        val created = System.currentTimeMillis()
        return proxy(javax.servlet.http.HttpSession::class.java) { method, _ ->
            when (method.name) {
                "getServletContext" -> context
                "getLastAccessedTime", "getCreationTime" -> created
                else -> throw UnsupportedOperationException(method.name)
            }
        }
    }

    private fun servletInput(stream: InputStream): javax.servlet.ServletInputStream = object : javax.servlet.ServletInputStream() {
        private var finished = false

        override fun read(): Int = stream.read().also { if (it < 0) finished = true }
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len).also { if (it < 0) finished = true }
        override fun available(): Int = stream.available()
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
