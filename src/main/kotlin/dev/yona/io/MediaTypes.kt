package dev.yona.io

import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.mime.MimeTypesFactory
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/**
 * 이름과 내용으로 파일 종류를 감지한다. v1.6 `utils.FileUtil.detectMediaType`과 `new Tika().detect(...)`에 해당한다.
 *
 * v1.6은 Tika 1.2를 썼으므로 그 mime 표(`/mime/tika-mimetypes-1.2.xml`)와 판정 규칙([V16MimeDetector])으로 감지한다.
 * 텍스트이면 문자 집합(`charset`)을 덧붙이는데, v1.6은 Tika가 읽은 앞부분(65536바이트)을 건너뛴 나머지로 감지하므로
 * 65536바이트 이하의 파일은 항상 UTF-8이 된다 ([refactoring-backlog.md] R-17).
 */
object MediaTypes {

    /** Tika `MimeTypes.getMinLength()`이다. 1.2와 3.x가 같다. */

    private val detector: V16MimeDetector by lazy {
        val xml = requireNotNull(MediaTypes::class.java.getResourceAsStream("/mime/tika-mimetypes-1.2.xml")) { "tika-mimetypes-1.2.xml이 없다" }
        V16MimeDetector(xml.use { MimeTypesFactory.create(it) })
    }

    private fun metadata(name: String?) = Metadata().also { m -> name?.let { m.add(TikaCoreProperties.RESOURCE_NAME_KEY, it) } }

    /** v1.6 `new Tika().detect(bytes, name)` 계열이다: 문자 집합 없이 종류만 돌려준다. [name]이 `null`이면 내용만 본다. */
    fun detectRaw(bytes: ByteArray, name: String?): String = detector.detect(ByteArrayInputStream(bytes), metadata(name)).toString()

    /** v1.6 `new Tika().detect(name)`: 이름만으로 감지한다 (엑셀 내려받기의 `Content-Type`). */
    fun detectName(name: String): String = detector.detect(null, metadata(name)).toString()

    /**
     * v1.6 `FileUtil.detectMediaType(...)`.toString()이다. [bytes]와 파일 이름 [name]으로 감지한 종류이다.
     * 예: `text/plain; charset=UTF-8`, `image/png`.
     *
     * Ogg는 이름의 확장자가 `ogv`이면 `video/ogg`이다. v1.6은 이름이 없으면 이 자리에서 `NullPointerException`(500)이 되지만,
     * v2는 오류 없이 `audio/ogg`를 돌려준다 (R-93).
     */
    fun detect(bytes: ByteArray, name: String?): String {
        val media = detector.detect(ByteArrayInputStream(bytes), metadata(name))
        return when {
            media.type.lowercase() == "text" -> {
                // v1.6은 Tika가 앞 65536바이트를 읽은 스트림의 나머지로 문자 집합을 감지해 그 이하 파일은 항상 UTF-8이었다. 파일 전체로 감지한다(R-17).
                // v1.6은 Charset.forName(...).name()으로 JVM 정규 이름을 쓴다 (예: BIG5는 Big5, SHIFT_JIS는 Shift_JIS)
                "$media; charset=${Charset.forName(CharsetDetection.detect(bytes)).name()}"
            }
            media.toString() == "audio/ogg" && name?.substringAfterLast('.', "")?.lowercase() == "ogv" -> "video/ogg"
            else -> media.toString()
        }
    }

    /** `text/plain; charset=UTF-8` 같은 값에서 문자 집합만 뽑는다. 없으면 `null`이다 (v1.6 `FileUtil.getCharset`). */
    fun charset(mediaType: String): String? = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(mediaType)?.groupValues?.get(1)
}
