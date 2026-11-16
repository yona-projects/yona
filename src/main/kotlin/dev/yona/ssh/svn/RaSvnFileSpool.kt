package dev.yona.ssh.svn

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * 파일 내용을 받아 두는 곳이다. [MEMORY_LIMIT]까지는 메모리에, 넘으면 임시 파일에 쓴다. 쓰면서 MD5를 계산하므로 `get-file`이 내용을 다 읽기 전에 응답 머리에 넣을 체크섬을
 * 알 수 있다. 큰 파일을 통째로 메모리에 올리지 않으려는 것이다. [close]로 임시 파일을 지운다.
 */
class RaSvnFileSpool(private val directory: File? = null) : OutputStream(), AutoCloseable {

    private val digest = MessageDigest.getInstance("MD5")
    private var memory: ByteArrayOutputStream? = ByteArrayOutputStream()
    private var file: File? = null
    private var out: OutputStream? = null

    /** 지금까지 쓴 바이트 수이다. */
    var size = 0L
        private set

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        digest.update(bytes, offset, length)
        size += length
        val inMemory = memory
        if (inMemory != null && size <= MEMORY_LIMIT) { inMemory.write(bytes, offset, length); return }
        if (out == null) {
            val spool = File.createTempFile("ra-svn-file", ".tmp", directory).also { file = it }
            out = FileOutputStream(spool).buffered().also { stream -> inMemory?.let { stream.write(it.toByteArray()) } }
            memory = null
        }
        out!!.write(bytes, offset, length)
    }

    /** 쓴 내용의 MD5(16진 문자열)이다. [open] 전에 읽어도 된다. */
    fun md5(): String = (digest.clone() as MessageDigest).digest().joinToString("") { "%02x".format(it) }

    /** 쓴 내용을 처음부터 읽는 스트림이다. 여러 번 열 수 있다. */
    fun open(): InputStream {
        out?.flush()
        return file?.inputStream()?.buffered() ?: java.io.ByteArrayInputStream(memory?.toByteArray() ?: ByteArray(0))
    }

    override fun close() { runCatching { out?.close() }; file?.delete(); file = null; out = null; memory = null }

    private companion object {
        const val MEMORY_LIMIT = 4L * 1024 * 1024
    }
}
