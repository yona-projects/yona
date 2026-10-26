package dev.yona.io

import org.apache.tika.detect.Detector
import org.apache.tika.detect.TextStatistics
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.mime.MediaType
import org.apache.tika.mime.MimeTypes
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException

/**
 * v1.6이 쓰던 Tika 1.2의 `MimeTypes.detect` 규칙을 Tika 3.x의 공개 API 위에서 재현하는 감지기이다.
 *
 * 확장자와 매직 표는 [MimeTypes] 인스턴스(Tika 1.2의 `tika-mimetypes.xml`로 만든 것)가 맡고, 이 클래스는 1.2의 판정 순서만 다시 쓴다.
 * 라이브러리 자체는 고치지 않는다.
 *
 * - 내용: 앞 [MimeTypes.getMinLength]바이트로 매직을 평가한다. 매직이 없는 텍스트는 비ASCII 비율이 낮을 때만 `text/plain`이다.
 *   Tika 3.x는 UTF-8로 읽히는 텍스트도 `text/plain`으로 보지만 1.2는 그렇지 않아, 한글 UTF-8 텍스트는 `application/octet-stream`이 된다.
 * - 이름: `URI`로 해석한 경로의 마지막 조각으로 확장자 표를 찾고, 내용 판정의 하위 형식일 때만 채택한다.
 *   주소로 해석할 수 없으면 이름 전체를 쓴다. `#` 뒤는 조각(fragment)으로 버려진다.
 */
class V16MimeDetector(private val types: MimeTypes) : Detector {

    /** 1.2의 `getMimeType(byte[])`: 매직 결과이고, 매직이 없으면 거의 ASCII일 때만 `text/plain`이다. */
    private fun contentType(prefix: ByteArray): MediaType {
        var type = types.detect(ByteArrayInputStream(prefix), Metadata())
        if (prefix.isNotEmpty() && MediaType.TEXT_PLAIN == type) {
            val statistics = TextStatistics().also { it.addData(prefix, 0, prefix.size) }
            if (!statistics.isMostlyAscii) {
                // 3.x의 새 규칙으로 생긴 text/plain인지, 매직이 맞은 text/plain인지 가린다. 끝부분을 제어 문자로 바꾼 사본으로 다시 감지하면
                // 매직이 있는 경우는 같은 형식이, 없는 경우는 octet-stream이 나온다.
                val min = types.minLength
                val tail = maxOf(64, prefix.size / 10)
                val probe = if (prefix.size + tail <= min) prefix.copyOf(prefix.size + tail) else prefix.clone()
                probe.fill(0x01, probe.size - tail, probe.size)
                type = types.detect(ByteArrayInputStream(probe), Metadata())
            }
        }
        return type
    }

    override fun detect(input: InputStream?, metadata: Metadata): MediaType {
        var type = MediaType.OCTET_STREAM
        if (input != null) {
            val min = types.minLength
            input.mark(min)
            val prefix = try { input.readNBytes(min) } finally { input.reset() }
            type = contentType(prefix)
        }
        val resourceName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY)
        if (resourceName != null) {
            val name = try {
                val path = URI(resourceName).path
                if (path != null && path.lastIndexOf('/') + 1 < path.length) path.substring(path.lastIndexOf('/') + 1) else null
            } catch (e: URISyntaxException) {
                resourceName
            }
            if (name != null) {
                val hint = types.getMimeType(name).type
                if (types.mediaTypeRegistry.isSpecializationOf(hint, type)) type = hint
            }
        }
        return type
    }
}
