package dev.yona.oauth2server.cimd

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** CIMD 문서를 가져오지 못했다. [message]는 사람이 읽는 이유이다(감사 기록과 로그에만 쓰고 사용자에게 자세히 보이지 않는다). */
class CimdFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 가져온 문서이다. [maxAgeSeconds]는 응답의 `Cache-Control: max-age`이고 `no-store`나 `no-cache`이면 0, 없으면 `null`이다. */
data class CimdResponse(val body: String, val maxAgeSeconds: Long?)

/** CIMD 문서를 가져오는 길이다. 시험에서는 가짜로 바꾼다. */
interface CimdTransport {
    @Throws(CimdFetchException::class)
    fun fetch(url: String): CimdResponse
}

/**
 * CIMD 문서를 가져오는 실제 전송이다(추가 개발 항목 X-1, `decisions.md` D-71, IETF 초안 5절과 8.6절). 서버가 외부가 준 주소로 연결하므로 서버 내부 자원을 대신 읽게 하는 공격(SSRF)을 막는 규칙을 모두 지킨다.
 *
 * - `https`만 쓰고 인증서 호스트 이름을 확인한다(TLS 1.2 이상).
 * - 이름을 풀어 얻은 **모든** 주소가 공인 주소([AddressPolicy])여야 한다. 확인한 바로 그 주소로 직접 연결해(이름을 다시 풀지 않는다) 확인과 연결 사이에 주소가 바뀌는 재바인딩을 막는다.
 * - HTTP 리디렉션을 따라가지 않고, 상태 코드가 200이 아니면 실패한다. 압축을 요청하지 않는다.
 * - 연결과 전체 시간은 [TIMEOUT_MILLIS]까지, 본문은 [MAX_BODY]바이트까지, 머리글은 [MAX_HEADERS]바이트까지이다.
 *
 * @param allowPrivateHosts 공인 주소가 아닌 곳도 허용한다. **개발과 시험에서만** 켠다(초안 8.6절)
 */
class SocketCimdTransport(
    private val sslContext: SSLContext = SSLContext.getDefault(),
    private val allowPrivateHosts: Boolean = false,
    private val resolver: (String) -> List<InetAddress> = { host -> InetAddress.getAllByName(host).toList() }
) : CimdTransport {

    override fun fetch(url: String): CimdResponse {
        CimdDocument.urlProblem(url)?.let { throw CimdFetchException("클라이언트 ID 주소가 올바르지 않다: $it") }
        val uri = URI(url)
        val host = uri.host; val port = if (uri.port == -1) 443 else uri.port
        val addresses = try { resolver(host) } catch (e: Exception) { throw CimdFetchException("이름을 풀지 못했다", e) }
        if (addresses.isEmpty()) throw CimdFetchException("이름을 풀지 못했다")
        if (!allowPrivateHosts && addresses.any { !AddressPolicy.isPublic(it) }) throw CimdFetchException("공인 주소가 아닌 곳으로 풀린다")
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(addresses.first(), port), TIMEOUT_MILLIS.toInt())
            raw.soTimeout = remaining(deadline)
            val socket = sslContext.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; protocols = arrayOf("TLSv1.3", "TLSv1.2") }
            socket.startHandshake()
            val target = (uri.rawPath.ifEmpty { "/" })
            val request = "GET $target HTTP/1.1\r\nHost: ${if (uri.port == -1) host else "$host:$port"}\r\nAccept: application/json\r\nUser-Agent: Yona-OAuth-CIMD\r\nConnection: close\r\n\r\n"
            socket.outputStream.apply { write(request.toByteArray(Charsets.US_ASCII)); flush() }
            return readResponse(socket.inputStream, deadline, socket)
        } catch (e: CimdFetchException) {
            throw e
        } catch (e: Exception) {
            throw CimdFetchException("가져오지 못했다: ${e.javaClass.simpleName}", e)
        } finally {
            runCatching { raw.close() }
        }
    }

    private fun remaining(deadline: Long): Int = (deadline - System.currentTimeMillis()).coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun readResponse(input: InputStream, deadline: Long, socket: SSLSocket): CimdResponse {
        val head = ByteArrayOutputStream()
        var matched = 0; val end = byteArrayOf(13, 10, 13, 10)
        while (matched < 4) {
            socket.soTimeout = remaining(deadline)
            val b = input.read(); if (b < 0) throw CimdFetchException("응답이 끝났다")
            head.write(b); if (head.size() > MAX_HEADERS) throw CimdFetchException("응답 머리글이 너무 크다")
            matched = if (b == end[matched].toInt()) matched + 1 else if (b == 13) 1 else 0
        }
        val lines = head.toString(Charsets.ISO_8859_1).split("\r\n").filter { it.isNotEmpty() }
        val status = lines.first().split(' ').getOrNull(1)?.toIntOrNull() ?: throw CimdFetchException("상태 줄을 읽지 못했다")
        if (status != 200) throw CimdFetchException("상태 코드가 200이 아니다: $status")
        val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        headers["content-encoding"]?.let { if (!it.equals("identity", true)) throw CimdFetchException("압축 응답은 받지 않는다") }
        headers["content-type"]?.let { if (!it.lowercase().contains("json")) throw CimdFetchException("JSON이 아니다") }
        val body = when {
            headers["transfer-encoding"]?.contains("chunked", true) == true -> readChunked(input, deadline, socket)
            headers["content-length"] != null -> {
                val length = headers["content-length"]!!.toLongOrNull() ?: throw CimdFetchException("길이를 읽지 못했다")
                if (length > MAX_BODY) throw CimdFetchException("본문이 너무 크다")
                readFixed(input, length.toInt(), deadline, socket)
            }
            else -> readToEnd(input, deadline, socket)
        }
        val cache = headers["cache-control"]?.lowercase().orEmpty()
        val maxAge = when {
            "no-store" in cache || "no-cache" in cache -> 0L
            else -> Regex("max-age=(\\d+)").find(cache)?.groupValues?.get(1)?.toLongOrNull()
        }
        return CimdResponse(String(body, Charsets.UTF_8), maxAge)
    }

    private fun readFixed(input: InputStream, length: Int, deadline: Long, socket: SSLSocket): ByteArray {
        val out = ByteArray(length); var read = 0
        while (read < length) { socket.soTimeout = remaining(deadline); val n = input.read(out, read, length - read); if (n < 0) throw CimdFetchException("본문이 짧다"); read += n }
        return out
    }

    private fun readToEnd(input: InputStream, deadline: Long, socket: SSLSocket): ByteArray {
        val out = ByteArrayOutputStream(); val buffer = ByteArray(1024)
        while (true) {
            socket.soTimeout = remaining(deadline); val n = input.read(buffer); if (n < 0) break
            out.write(buffer, 0, n); if (out.size() > MAX_BODY) throw CimdFetchException("본문이 너무 크다")
        }
        return out.toByteArray()
    }

    private fun readChunked(input: InputStream, deadline: Long, socket: SSLSocket): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val size = readLine(input, deadline, socket).substringBefore(';').trim().toIntOrNull(16) ?: throw CimdFetchException("청크 크기를 읽지 못했다")
            if (size == 0) break
            if (out.size() + size > MAX_BODY) throw CimdFetchException("본문이 너무 크다")
            out.write(readFixed(input, size, deadline, socket)); readLine(input, deadline, socket)
        }
        return out.toByteArray()
    }

    private fun readLine(input: InputStream, deadline: Long, socket: SSLSocket): String {
        val line = ByteArrayOutputStream()
        while (true) {
            socket.soTimeout = remaining(deadline); val b = input.read(); if (b < 0) throw CimdFetchException("응답이 끝났다")
            if (b == 10) break; if (b != 13) line.write(b); if (line.size() > 64) throw CimdFetchException("청크 줄이 너무 길다")
        }
        return line.toString(Charsets.ISO_8859_1)
    }

    companion object {
        /** 연결과 응답 전체의 제한 시간(밀리초)이다. */
        const val TIMEOUT_MILLIS = 5_000L

        /** 본문 최대 크기(바이트)이다. 초안은 5 KB를 권하고 여유를 두어 8 KB까지 받는다. */
        const val MAX_BODY = 8 * 1024

        /** 응답 머리글 최대 크기(바이트)이다. */
        const val MAX_HEADERS = 8 * 1024
    }
}
