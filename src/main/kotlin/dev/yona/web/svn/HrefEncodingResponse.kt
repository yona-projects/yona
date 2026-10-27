package dev.yona.web.svn

import dev.yona.util.UrlPaths
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

/**
 * `PROPFIND` 응답의 본문을 모았다가 `<D:href>` 안의 주소에서 공백과 ASCII 바깥의 글자를 퍼센트 인코딩해서 내보낸다.
 *
 * v1.6의 `svnkit-dav` 1.8.15는 경로를 직접 디코딩하고 자식 항목의 주소를 인코딩해서 만든다. v2가 쓰는 1.10.11은 서블릿 컨테이너가 이미
 * 디코딩한 경로를 받는 대신, 하위 항목의 주소(`…/!svn/bc/1/한글 b.txt`)를 인코딩하지 않고 내보낸다. 그러면 `svn ls`, `svn info`, `svn log`가
 * 그 항목을 찾지 못한다. 응답 본문은 항목 수에 비례하는 작은 XML이라 모아서 고쳐도 부담이 작다(A-137).
 */
class HrefEncodingResponse(response: HttpServletResponse) : HttpServletResponseWrapper(response) {

    private val buffer = ByteArrayOutputStream()
    private var writer: PrintWriter? = null

    override fun getOutputStream(): ServletOutputStream = object : ServletOutputStream() {
        override fun write(b: Int) = buffer.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)
        override fun isReady(): Boolean = true
        override fun setWriteListener(listener: WriteListener?) = throw UnsupportedOperationException("setWriteListener")
    }

    override fun getWriter(): PrintWriter = writer ?: PrintWriter(buffer.writer(Charsets.UTF_8), false).also { writer = it }

    /** 길이는 고친 본문으로 다시 정하므로 서블릿이 정한 값은 버린다. */
    override fun setContentLength(len: Int) {}
    override fun setContentLengthLong(len: Long) {}
    override fun setHeader(name: String, value: String?) {
        if (!name.equals("Content-Length", ignoreCase = true)) super.setHeader(name, value)
    }

    override fun flushBuffer() {}
    override fun isCommitted(): Boolean = false
    override fun resetBuffer() = buffer.reset()

    /** 모은 본문을 고쳐서 실제 응답에 쓴다. 서블릿 처리가 끝난 뒤 한 번 부른다. */
    fun finish() {
        writer?.flush()
        val text = buffer.toString(Charsets.UTF_8)
        val fixed = HREF.replace(text) { m -> m.groupValues[1] + UrlPaths.encodeLocation(m.groupValues[2]) + m.groupValues[3] }
        val bytes = fixed.toByteArray(Charsets.UTF_8)
        super.setContentLengthLong(bytes.size.toLong())
        super.getOutputStream().write(bytes)
        super.flushBuffer()
    }

    private companion object {
        val HREF = Regex("(<D:href>)([^<]*)(</D:href>)")
    }
}
