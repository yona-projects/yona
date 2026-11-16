package dev.yona.ssh.svn

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Subversion `ra_svn` 프로토콜의 항목이다. 프로토콜은 네 가지 항목을 공백으로 나누어 주고받는다: 숫자(`123`), 단어(`success`), 문자열(`5:hello`, 길이는 바이트 수),
 * 목록(`( ... )`). 모든 항목 뒤에는 공백이나 줄바꿈이 하나 온다. 형식은 Subversion의 `notes/ra-svn` 문서와 실제 `svnserve -t`가 주고받은 바이트로 확인했다.
 */
sealed interface RaItem

/** 숫자이다. 부호 없는 64비트 정수이며 최댓값(`18446744073709551615`)이 "알 수 없음"(`SVN_INVALID_FILESIZE`)을 뜻하는 자리가 있어 [Long]에 비트 그대로 담는다(−1). */
data class RaNumber(val value: Long) : RaItem

/** 단어이다(`success`, `edit-pipeline`, `true`). 글자와 숫자와 `-`로 이루어진다. */
data class RaWord(val value: String) : RaItem

/** 문자열이다. 바이트열이며 글자가 아닐 수 있다(파일 내용, svndiff 조각). 글자로 읽을 때는 [text]이다(UTF-8). */
class RaString(val bytes: ByteArray) : RaItem {
    constructor(text: String) : this(text.toByteArray(Charsets.UTF_8))

    val text: String get() = String(bytes, Charsets.UTF_8)

    override fun equals(other: Any?): Boolean = other is RaString && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "RaString($text)"
}

/** 목록이다. 중첩할 수 있다. 명령은 `( 이름 ( 인자... ) )`, 응답은 `( success ( 값... ) )` 모양의 목록이다. */
data class RaList(val items: List<RaItem>) : RaItem

/** 목록을 만드는 도우미이다. */
fun raList(vararg items: RaItem): RaList = RaList(items.toList())

/**
 * SVNKit 속성 값의 바이트이다. 값이 문자열로 저장된 속성은 [org.tmatesoft.svn.core.SVNPropertyValue.getBytes]가 비어 있으므로 문자열을 UTF-8로 바꾸고,
 * 바이트로 저장된 속성(이진 값)은 그대로 쓴다. 값이 없으면 빈 바이트이다.
 */
fun org.tmatesoft.svn.core.SVNPropertyValue?.toRaBytes(): ByteArray = when {
    this == null -> ByteArray(0)
    isString -> string.toByteArray(Charsets.UTF_8)
    else -> bytes ?: ByteArray(0)
}

/** 프로토콜 규칙을 어긴 입력이다(끊김, 모르는 모양). 연결을 닫아야 한다. */
class RaSvnProtocolException(message: String) : IOException(message)

/**
 * 입력에서 `ra_svn` 항목을 하나씩 읽는다. 항목 하나를 읽고 그 뒤의 공백 한 글자까지 먹으며 그 이상은 읽지 않으므로, 상대가 다음 항목을 보내기 전에 기다려도 멈추지 않는다.
 * 스레드마다 하나씩 쓴다.
 */
class RaSvnReader(private val input: InputStream) {

    private var pushed = NONE

    /** 다음 항목이다. 항목이 시작하기 전에 입력이 끝났으면 `null`이다. 항목 도중에 끝나면 [RaSvnProtocolException]이다. */
    fun read(): RaItem? {
        val first = skipWhitespace()
        if (first < 0) return null
        return item(first)
    }

    /** 다음 항목이 단어일 때만 읽는다. 아니면 [RaSvnProtocolException]이다. */
    fun readWord(): String = (read() as? RaWord)?.value ?: throw RaSvnProtocolException("단어가 와야 한다")

    private fun item(first: Int): RaItem = when {
        first == '('.code -> list()
        first == ')'.code -> throw RaSvnProtocolException("짝이 없는 닫는 괄호")
        first in '0'.code..'9'.code -> numberOrString(first)
        isWordStart(first) -> word(first)
        else -> throw RaSvnProtocolException("알 수 없는 글자: $first")
    }

    private fun list(): RaList {
        // 여는 괄호 뒤에는 공백이 하나 온다
        val items = mutableListOf<RaItem>()
        while (true) {
            val next = skipWhitespace()
            if (next < 0) throw RaSvnProtocolException("목록이 끝나기 전에 입력이 끊겼다")
            if (next == ')'.code) { consumeTrailingWhitespace(); return RaList(items) }
            items += item(next)
        }
    }

    private fun numberOrString(first: Int): RaItem {
        var value = (first - '0'.code).toULong()
        while (true) {
            val c = input.read()
            when {
                c in '0'.code..'9'.code -> value = value * 10u + (c - '0'.code).toUInt()
                c == ':'.code -> return string(value)
                c < 0 -> throw RaSvnProtocolException("숫자가 끝나기 전에 입력이 끊겼다")
                isWhitespace(c) -> return RaNumber(value.toLong())
                else -> throw RaSvnProtocolException("숫자 뒤에 올 수 없는 글자: $c")
            }
        }
    }

    private fun string(length: ULong): RaString {
        if (length > MAX_STRING) throw RaSvnProtocolException("문자열이 너무 길다: $length")
        val total = length.toInt()
        // 선언한 길이만큼 미리 잡지 않는다. 실제로 받은 만큼만 늘려 가므로, 길이만 크게 보내고 멈추는 입력이 메모리를 차지하지 못한다
        val out = ByteArrayOutputStream(minOf(total, FIRST_BLOCK))
        val buffer = ByteArray(minOf(total, BLOCK).coerceAtLeast(1))
        var remaining = total
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (n < 0) throw RaSvnProtocolException("문자열이 끝나기 전에 입력이 끊겼다")
            out.write(buffer, 0, n); remaining -= n
        }
        consumeTrailingWhitespace()
        return RaString(out.toByteArray())
    }

    private fun word(first: Int): RaWord {
        val builder = StringBuilder().append(first.toChar())
        while (true) {
            val c = input.read()
            when {
                c < 0 -> return RaWord(builder.toString())
                isWhitespace(c) -> return RaWord(builder.toString())
                c == ')'.code -> { pushed = c; return RaWord(builder.toString()) }
                else -> builder.append(c.toChar())
            }
        }
    }

    /** 항목 뒤의 공백 한 글자를 먹는다. 없거나 입력이 끝났으면 아무것도 하지 않는다(항목은 이미 다 읽었다). */
    private fun consumeTrailingWhitespace() {
        // 모든 항목은 공백으로 끝난다. 여기서 더 읽으면 상대의 다음 메시지를 기다리며 멈출 수 있으므로 공백 하나만 읽는다.
        val c = input.read()
        if (c >= 0 && !isWhitespace(c)) pushed = c
    }

    private fun skipWhitespace(): Int {
        if (pushed != NONE) { val c = pushed; pushed = NONE; if (!isWhitespace(c)) return c }
        while (true) {
            val c = input.read()
            if (c < 0) return -1
            if (!isWhitespace(c)) return c
        }
    }

    private fun isWhitespace(c: Int) = c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code

    private fun isWordStart(c: Int) = (c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code)

    private companion object {
        const val NONE = -2

        /**
         * 문자열 하나의 상한이다(64MB). 클라이언트가 보내는 문자열은 경로, 로그 메시지, 속성, svndiff 조각(보통 100KB 안팎)이라 이보다 훨씬 작다. 상한이 없으면 인증된
         * 사용자 한 명이 큰 길이를 선언해 서버의 메모리를 쓸 수 있다.
         */
        val MAX_STRING = 64UL * 1024UL * 1024UL

        /** 처음 잡는 버퍼와 한 번에 읽는 크기이다. */
        const val FIRST_BLOCK = 8 * 1024

        const val BLOCK = 64 * 1024
    }
}

/** 출력에 `ra_svn` 항목을 쓴다. 모든 항목 뒤에 공백을 하나 붙인다(`svnserve`와 같다). 스레드마다 하나씩 쓴다. */
class RaSvnWriter(private val output: OutputStream) {

    fun write(item: RaItem) {
        when (item) {
            is RaNumber -> output.write("${java.lang.Long.toUnsignedString(item.value)} ".toByteArray())
            is RaWord -> output.write("${item.value} ".toByteArray())
            is RaString -> { output.write("${item.bytes.size}:".toByteArray()); output.write(item.bytes); output.write(' '.code) }
            is RaList -> { output.write("( ".toByteArray()); item.items.forEach { write(it) }; output.write(") ".toByteArray()) }
        }
    }

    fun flush() = output.flush()

    /** 항목을 이어 붙인 바이트이다(시험과 조각 만들기용). */
    fun toBytes(item: RaItem): ByteArray = ByteArrayOutputStream().also { RaSvnWriter(it).write(item) }.toByteArray()
}
