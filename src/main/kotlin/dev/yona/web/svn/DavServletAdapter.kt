package dev.yona.web.svn

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import jakarta.servlet.http.HttpSession
import java.io.File
import java.io.InputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.security.Principal
import java.util.Collections

/**
 * SVNKit의 DAV 서블릿(`DAVServlet`, SCM-Manager가 Jakarta용으로 다시 포장한 `sonia.svnkit:svnkit-dav`)에 넘길 요청, 응답, 설정을 만든다.
 * v1.6 `utils.PlayServletRequest`, `PlayServletResponse`, `PlayServletContext`(Play의 요청을 서블릿으로 바꾸던 클래스들)에 해당한다.
 *
 * DAV 서블릿은 요청과 응답의 몇 가지 값만 쓰므로 실제 요청과 응답을 감싸는 래퍼에서 그 값들만 바꾼다. 요청의 값은 v1.6과 같고, 아래 두 가지만 다르다: 스킴과 호스트, 포트는
 * 실제 요청의 값을 쓰고(v1.6은 항상 `http`이다), 응답은 파이프로 상태를 기다리지 않고 서블릿 응답에 바로 쓴다(A-98).
 *
 * 이전에는 Maven Central의 `svnkit-dav`가 javax 서블릿 API만 지원해서 동적 프록시로 javax와 Jakarta를 변환했다(D-26). Jakarta용 빌드를 쓰면서 그 변환 층이 필요 없어졌다(A-617).
 */
object DavServletAdapter {

    private val loader = DavServletAdapter::class.java.classLoader

    /**
     * 응답 헤더 값에서 ASCII 바깥의 글자를 UTF-8 퍼센트 인코딩으로 바꾼다. DAV 서블릿은 파일 이름이 든 `ETag`(`1//한글 a.txt`)를 그대로 쓰는데,
     * Tomcat은 이런 헤더를 거부해 응답이 500이 된다(v1.6의 Netty는 오류 없이 내보냈다). 값은 ETag로 같은 항목을 구별하는 데만 쓰인다(A-137).
     */
    internal fun headerValue(value: String?): String? =
        if (value == null || value.all { it.code in 0x20..0x7E || it == '\t' }) value
        else buildString { value.forEach { c -> if (c.code in 0x20..0x7E || c == '\t') append(c) else c.toString().toByteArray(Charsets.UTF_8).forEach { append('%').append("%02X".format(it)) } } }

    /**
     * 서블릿이 보는 요청이다.
     *
     * @param real 실제 요청
     * @param pathInfo `소유자` 뒤의 경로(프로젝트 이름부터). 디코딩한 그대로 넘긴다. 1.10은 컨테이너가 디코딩한 값을 기대한다(v1.6의 1.8.15는 인코딩해서 받아 직접 디코딩했다)
     * @param username 인증한 사용자의 로그인 ID. 익명이면 빈 문자열
     * @param serverInfo 서블릿 컨텍스트가 알려 줄 서버 이름 (`yona.server`)
     */
    fun request(real: HttpServletRequest, pathInfo: String, username: String, serverInfo: String): HttpServletRequest = DavRequest(real, pathInfo, username, serverInfo)

    /** 응답의 상태, 헤더, 본문을 실제 응답에 바로 쓴다. 헤더를 더할 때는 v1.6처럼 같은 이름의 앞선 값 뒤에 쉼표로 이어 붙인다. */
    fun response(real: HttpServletResponse): HttpServletResponse = DavResponse(real)

    /** 이름이 `SVNParentPath`이면 [parentPath]를, 그 밖의 초기화 값은 `null`로 돌려주는 서블릿 설정이다 (v1.6 `createDavServlet`). */
    fun config(parentPath: String, serverInfo: String): ServletConfig {
        val context = context(serverInfo)
        return proxy(ServletConfig::class.java) { method, args ->
            when (method.name) {
                "getInitParameter" -> if (args[0] == "SVNParentPath") parentPath else null
                "getServletContext" -> context
                else -> throw UnsupportedOperationException(method.name)
            }
        }
    }

    /** 서블릿이 보는 요청이다. 서블릿이 쓰는 값만 바꾸고 나머지는 실제 요청에 맡긴다. */
    private class DavRequest(real: HttpServletRequest, private val pathInfo: String, private val username: String, serverInfo: String) : HttpServletRequestWrapper(real) {
        private var characterEncoding: String? = null
        private val attributes = HashMap<String, Any?>()
        private val session = session(serverInfo)

        // 서블릿 경로는 컨텍스트 경로 뒤 `/svn/소유자/`까지이다. 원본 주소에서 잘라 퍼센트 인코딩 길이에 영향을 받지 않게 한다.
        // DAV 서블릿은 응답의 href를 `컨텍스트 경로 + 서블릿 경로(앞의 /를 뗀 것)`로 만든다. 컨텍스트 경로가 비어 있으면 `/`가 하나 더 있어야
        // 절대 경로(`/svn/소유자/…`)가 남는다. svn 명령줄 클라이언트는 `/`가 빠진 href를 쓰지 못한다(v1.6은 이 보정이 없다, A-98).
        private val davServletPath: String = run {
            val relative = real.requestURI.removePrefix(real.contextPath)
            val servletPath = Regex("^/[^/]+/[^/]*/").find(relative)?.value ?: relative
            if (real.contextPath.isEmpty()) "/$servletPath" else servletPath
        }

        override fun getPathInfo(): String = pathInfo
        override fun getRemoteUser(): String = username
        override fun getUserPrincipal(): Principal = Principal { username }
        override fun getServletPath(): String = davServletPath

        // v1.6은 인코딩한 경로로 만든다
        override fun getPathTranslated(): String = File(org.tmatesoft.svn.core.internal.util.SVNEncodingUtil.uriEncode(pathInfo)).absolutePath

        override fun getRequestURL(): StringBuffer = StringBuffer(listOfNotNull(requestURI, queryString).joinToString("?"))

        override fun getHeader(name: String): String? = lockDepth(name) ?: super.getHeader(name)
        override fun getHeaders(name: String): java.util.Enumeration<String> = lockDepth(name)?.let { Collections.enumeration(listOf(it)) } ?: super.getHeaders(name)
        override fun getDateHeader(name: String): Long = try { super.getDateHeader(name) } catch (e: IllegalArgumentException) { -1L }
        override fun getIntHeader(name: String): Int = super.getHeader(name)?.toInt() ?: -1

        override fun getCharacterEncoding(): String? = characterEncoding
        override fun setCharacterEncoding(env: String?) { characterEncoding = env }
        override fun getRemoteHost(): String? = null
        override fun getAuthType(): String? = null

        override fun getSession(): HttpSession = session
        override fun getSession(create: Boolean): HttpSession = session

        override fun getAttribute(name: String): Any? = attributes[name]
        override fun setAttribute(name: String, value: Any?) { attributes[name] = value }
        override fun removeAttribute(name: String) { attributes.remove(name) }
        override fun getAttributeNames(): java.util.Enumeration<String> = Collections.enumeration(attributes.keys)

        override fun isAsyncStarted(): Boolean = false
        override fun isAsyncSupported(): Boolean = false
        override fun getInputStream(): ServletInputStream = servletInput(super.getInputStream())

        /**
         * `LOCK` 요청의 `Depth` 머리글이 없거나 `infinity`이면 `0`으로 바꿔 알린다. svn 명령줄 클라이언트는 `Depth` 없이 파일 하나를 잠그는데,
         * 이 SVNKit 버전(1.10)의 `LOCK` 처리기는 깊이 `infinity`로 파일을 잠그려 하면 `400`으로 거절한다(v1.6의 1.8은 받았다).
         * svn은 파일만 잠그므로 깊이 `0`과 결과가 같다.
         */
        private fun lockDepth(name: String): String? =
            if (method == "LOCK" && name.equals("Depth", ignoreCase = true) && super.getHeader("Depth").let { it == null || it.equals("infinity", ignoreCase = true) }) "0" else null
    }

    /** 서블릿이 보는 응답이다. 헤더 값의 인코딩, 쉼표로 잇는 헤더 더하기, 오류 화면 없이 상태와 본문을 직접 정하기를 맡는다. */
    private class DavResponse(private val real: HttpServletResponse) : HttpServletResponseWrapper(real) {

        private fun appendHeader(name: String, value: String) {
            val head = real.getHeader(name)
            real.setHeader(name, if (head.isNullOrBlank()) value else "$head,$value")
        }

        override fun setHeader(name: String, value: String?) = real.setHeader(name, headerValue(value))
        override fun addHeader(name: String, value: String) = appendHeader(name, headerValue(value)!!)
        override fun addIntHeader(name: String, value: Int) = appendHeader(name, value.toString())
        override fun addDateHeader(name: String, date: Long) =
            appendHeader(name, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.Instant.ofEpochMilli(date).atZone(java.time.ZoneOffset.UTC)))

        // 인코딩을 정하지 않았으면 UTF-8로 쓴다 (v1.6 `Config.getCharset`).
        override fun getWriter(): java.io.PrintWriter {
            if (real.contentType?.contains("charset", ignoreCase = true) != true) real.characterEncoding = "UTF-8"
            return real.writer
        }

        override fun resetBuffer() { real.writer.flush() }

        // 서블릿 컨테이너의 오류 화면을 거치지 않고 상태와 본문을 직접 정한다 (v1.6 `sendError`).
        override fun sendError(sc: Int) { real.status = sc }

        override fun sendError(sc: Int, msg: String?) {
            real.status = sc
            if (msg != null) {
                real.contentType = "text/plain"
                real.characterEncoding = "UTF-8"
                real.writer.write(msg)
            }
        }

        override fun sendRedirect(location: String) { real.setHeader("Location", location); real.status = 302 }
    }

    private fun context(serverInfo: String): ServletContext = proxy(ServletContext::class.java) { method, _ ->
        when (method.name) {
            // v1.6 `PlayServletContext.getServerInfo()`는 `application.server`를 돌려준다
            "getServerInfo" -> serverInfo
            else -> throw UnsupportedOperationException(method.name)
        }
    }

    private fun session(serverInfo: String): HttpSession {
        val context = context(serverInfo)
        val created = System.currentTimeMillis()
        return proxy(HttpSession::class.java) { method, _ ->
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
    private fun servletInput(stream: InputStream): ServletInputStream = object : ServletInputStream() {
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
        override fun setReadListener(readListener: ReadListener?) = throw UnsupportedOperationException("setReadListener")
    }

    /** 컨텍스트와 세션처럼 서블릿이 한두 메서드만 부르는 작은 객체를 만드는 동적 프록시이다. 연결하지 않은 메서드는 [UnsupportedOperationException]이다(v1.6의 어댑터도 같다). */
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
