package com.github.yonaprojects.yona.domain.support

import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.mozilla.universalchardet.UniversalDetector
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

object FileUtil {

    fun detectCharset(bytes: ByteArray): String {
        val detector = UniversalDetector(null)
        var offset = 0

        do {
            val blockSize = Math.min(4096, bytes.size - offset)
            detector.handleData(bytes, offset, blockSize)
            offset += blockSize
        } while (offset < bytes.size)
        detector.dataEnd()

        return detector.detectedCharset ?: "UTF-8"
    }

    fun detectCharset(inputStream: InputStream): String {
        val detector = UniversalDetector(null)
        val buf = ByteArray(4096)
        var nRead: Int

        while (inputStream.read(buf).also { nRead = it } > 0 && !detector.isDone) {
            detector.handleData(buf, 0, nRead)
        }
        detector.dataEnd()

        return detector.detectedCharset ?: "UTF-8"
    }

    // yona FileUtil.detectMediaType() 대응. Apache Tika로 실제 파일 내용(매직
    // 바이트)을 콘텐츠 기반으로 감지한다 — 원본 파일명(name)은 힌트로만 쓰이고, 확장자가 없는
    // 해시 파일명이어도 정확히 감지된다(JDK Files.probeContentType()은 사실상 확장자 기반이라
    // 해시 파일명에서 거의 항상 감지에 실패한다).
    fun detectMediaType(file: File, name: String): String {
        val metadata = Metadata()
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name)
        // Tika 4.x부터 저수준 Detector SPI(Tika().detector.detect())의 시그니처가 바뀌어서
        // (TikaInputStream + 추가 파라미터 요구) 내부 SPI에 직접 의존하지 않는 상위 Tika 파사드의
        // detect(InputStream, Metadata) 편의 메서드로 대체한다 — 반환 타입이 MediaType이 아니라
        // String(예: "text/plain")이라 아래 주타입 판별을 substringBefore로 바꿨다.
        val mediaType = file.inputStream().use { input ->
            Tika().detect(BufferedInputStream(input), metadata)
        }

        return when {
            mediaType.substringBefore('/').lowercase() == "text" -> {
                val charset = file.inputStream().use { detectCharset(it) }
                "$mediaType; charset=${Charset.forName(charset).name()}"
            }
            // Tika가 ogg 비디오를 audio/ogg로 오판하는 것을 보정 (yona FileUtil 동일 대응).
            mediaType == "audio/ogg" && name.substringAfterLast('.', "").lowercase() == "ogv" ->
                "video/ogg"
            else -> mediaType
        }
    }
}
