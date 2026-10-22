package dev.yona.io

import org.mozilla.universalchardet.UniversalDetector

/**
 * 바이트 내용을 보고 문자 인코딩을 추정한다. v1.6 `utils.FileUtil.detectCharset`에 해당한다.
 * 추정하지 못하면 UTF-8로 본다.
 */
object CharsetDetection {

    private const val BLOCK_SIZE = 4096

    /** [bytes]의 인코딩 이름이다. 예: `UTF-8`, `EUC-KR`. */
    fun detect(bytes: ByteArray): String {
        val detector = UniversalDetector(null)
        var offset = 0
        do {
            val size = minOf(BLOCK_SIZE, bytes.size - offset)
            detector.handleData(bytes, offset, size)
            offset += size
        } while (offset < bytes.size)
        detector.dataEnd()
        return detector.detectedCharset ?: "UTF-8"
    }
}
