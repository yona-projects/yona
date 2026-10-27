package dev.yona.web.svn

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintWriter

/**
 * `REPORT` 응답에서 `get-location-segments-report`의 `<S:location-segment path="/브랜치">`의 앞 슬래시를 떼어 내보낸다.
 *
 * v1.6의 `svnkit-dav` 1.8.15는 `path="branches/b1"`처럼 슬래시 없는 상대 경로를 내보냈고 mod_dav_svn도 그렇다. v2가 쓰는 1.10.11은
 * `path="/branches/b1"`을 내보내, svn 1.14 클라이언트가 `svn switch` 때 `svn_relpath_is_canonical` 단언 실패로 멈춘다(A-171).
 * 응답은 커서 모을 수 없으므로(체크아웃의 `update-report`) 쓰는 대로 지나가며 고정 문자열만 바꾼다. 문자열이 두 번의 쓰기에 걸쳐도 바꾼다.
 */
class LocationSegmentResponse(response: HttpServletResponse) : HttpServletResponseWrapper(response) {

    private val replacer = ReplacingOutputStream(response.outputStream, PATTERN, REPLACEMENT)
    private var writer: PrintWriter? = null

    override fun getOutputStream(): ServletOutputStream = object : ServletOutputStream() {
        override fun write(b: Int) = replacer.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = replacer.write(b, off, len)
        override fun flush() = replacer.flush()
        override fun isReady(): Boolean = true
        override fun setWriteListener(listener: WriteListener?) = throw UnsupportedOperationException("setWriteListener")
    }

    override fun getWriter(): PrintWriter = writer ?: PrintWriter(replacer.writer(Charsets.UTF_8), false).also { writer = it }

    /** 고치면 길이가 줄어드므로 서블릿이 정한 길이는 버린다. */
    override fun setContentLength(len: Int) {}
    override fun setContentLengthLong(len: Long) {}
    override fun setHeader(name: String, value: String?) {
        if (!name.equals("Content-Length", ignoreCase = true)) super.setHeader(name, value)
    }

    /** 서블릿 처리가 끝난 뒤 한 번 부른다. 아직 내보내지 않은 꼬리를 쓴다. */
    fun finish() {
        writer?.flush()
        replacer.finish()
    }

    /** [pattern]을 [replacement]로 바꾸며 [out]에 쓰는 스트림이다. 패턴이 쓰기 경계에 걸릴 수 있어 끝의 `pattern.size - 1`바이트는 다음 쓰기까지 쥐고 있는다. */
    internal class ReplacingOutputStream(private val out: OutputStream, private val pattern: ByteArray, private val replacement: ByteArray) : OutputStream() {
        private var carry = ByteArray(0)

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            val data = ByteArray(carry.size + len)
            System.arraycopy(carry, 0, data, 0, carry.size)
            System.arraycopy(b, off, data, carry.size, len)
            val result = ByteArrayOutputStream(data.size)
            var i = 0
            val safeEnd = data.size - (pattern.size - 1)
            while (i < safeEnd) {
                if (matches(data, i)) { result.write(replacement); i += pattern.size } else result.write(data[i++].toInt())
            }
            carry = data.copyOfRange(maxOf(i, 0), data.size)
            out.write(result.toByteArray())
        }

        private fun matches(data: ByteArray, at: Int): Boolean {
            for (k in pattern.indices) if (data[at + k] != pattern[k]) return false
            return true
        }

        override fun flush() = out.flush()

        fun finish() {
            out.write(carry)
            carry = ByteArray(0)
            out.flush()
        }
    }

    private companion object {
        val PATTERN = "<S:location-segment path=\"/".toByteArray(Charsets.UTF_8)
        val REPLACEMENT = "<S:location-segment path=\"".toByteArray(Charsets.UTF_8)
    }
}
