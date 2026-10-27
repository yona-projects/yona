package dev.yona.web.svn

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/** 위치 구간 응답의 경로 앞 슬래시를 떼는 스트림이 쓰기 경계와 상관없이 같은 결과를 내는지 검증한다. */
class LocationSegmentResponseTest {

    private val pattern = "<S:location-segment path=\"/".toByteArray()
    private val replacement = "<S:location-segment path=\"".toByteArray()
    private val xml = "<a><S:location-segment path=\"/branches/b1\" range-start=\"1\"/><S:location-segment path=\"/trunk\"/><b path=\"/keep\"/></a>"
    private val expected = "<a><S:location-segment path=\"branches/b1\" range-start=\"1\"/><S:location-segment path=\"trunk\"/><b path=\"/keep\"/></a>"

    private fun run(chunk: Int): String {
        val sink = ByteArrayOutputStream()
        val stream = LocationSegmentResponse.ReplacingOutputStream(sink, pattern, replacement)
        val bytes = xml.toByteArray()
        var i = 0
        while (i < bytes.size) { val n = minOf(chunk, bytes.size - i); stream.write(bytes, i, n); i += n }
        stream.finish()
        return sink.toString(Charsets.UTF_8)
    }

    @Test
    fun `쓰기 단위가 얼마여도 같은 결과이다`() {
        for (chunk in listOf(1, 2, 3, 5, 7, 13, 28, 29, 30, 64, 1000)) assertEquals(expected, run(chunk), "chunk=$chunk")
    }

    @Test
    fun `패턴이 없는 본문은 그대로 나간다`() {
        val sink = ByteArrayOutputStream()
        val stream = LocationSegmentResponse.ReplacingOutputStream(sink, pattern, replacement)
        stream.write("<S:update-report>한글</S:update-report>".toByteArray()); stream.finish()
        assertEquals("<S:update-report>한글</S:update-report>", sink.toString(Charsets.UTF_8))
    }
}
