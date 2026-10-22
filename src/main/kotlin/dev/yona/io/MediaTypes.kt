package dev.yona.io

import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties

/**
 * 이름과 내용으로 파일 종류를 감지한다. v1.6 `utils.FileUtil.detectMediaType`에 해당한다.
 *
 * 텍스트이면 문자 집합(`charset`)을 덧붙인다. v1.6은 앞부분을 버퍼에 읽은 뒤 스트림 나머지로 문자 집합을 감지하므로,
 * 8192바이트 이하의 파일은 항상 UTF-8이 된다([refactoring-backlog.md] R-17).
 */
object MediaTypes {

    private const val BUFFER_SIZE = 8192

    /** [bytes]와 파일 이름 [name]으로 감지한 종류이다. 예: `text/plain; charset=UTF-8`, `image/png`. */
    fun detect(bytes: ByteArray, name: String?): String {
        val metadata = Metadata()
        name?.let { metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, it) }
        val media = Tika().detector.detect(bytes.inputStream().buffered(), metadata)
        return when {
            media.type.lowercase() == "text" -> {
                val rest = if (bytes.size > BUFFER_SIZE) bytes.copyOfRange(BUFFER_SIZE, bytes.size) else ByteArray(0)
                "$media; charset=${CharsetDetection.detect(rest)}"
            }
            media.toString() == "audio/ogg" && name?.substringAfterLast('.', "")?.lowercase() == "ogv" -> "video/ogg"
            else -> media.toString()
        }
    }

    /** `text/plain; charset=UTF-8` 같은 값에서 문자 집합만 뽑는다. 없으면 `null`이다 (v1.6 `FileUtil.getCharset`). */
    fun charset(mediaType: String): String? = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(mediaType)?.groupValues?.get(1)
}
