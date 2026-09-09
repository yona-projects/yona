package com.github.yonaprojects.yona.domain.support

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

// yona utils/ZipUtil.java 대응. 문자열을 Deflate로 압축/해제하는 용도.
object ZipUtil {
    fun compress(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        DeflaterOutputStream(baos).use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
        return baos.toByteArray()
    }

    fun decompress(bytes: ByteArray): String {
        val baos = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(8192)
            var len: Int
            while (input.read(buffer).also { len = it } > 0) {
                baos.write(buffer, 0, len)
            }
        }
        return String(baos.toByteArray(), StandardCharsets.UTF_8)
    }
}
