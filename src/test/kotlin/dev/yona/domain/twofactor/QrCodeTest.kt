package dev.yona.domain.twofactor

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** QR 코드 SVG(X-2)를 검증한다: 같은 글을 담은 QR 코드이고 외부 자원을 쓰지 않는다. */
class QrCodeTest {

    @Test
    fun `만든 SVG를 다시 읽으면 원래 글이 나온다`() {
        // 비밀값이 무작위라 한 번만 확인하면 가끔만 실패한다. 여러 번 확인해 불안정함을 드러낸다
        repeat(500) { decodeOnce() }
    }

    private fun decodeOnce() {
        val text = Totp.otpauthUri(Totp.newSecret(), "Yona", "alice@example.com")
        val svg = QrCode.svg(text)
        assertTrue(svg.startsWith("<svg") && !svg.contains("<image") && !svg.contains("href"))
        val size = Regex("viewBox=\"0 0 (\\d+) (\\d+)\"").find(svg)!!.destructured.let { it.component1().toInt() }
        val dark = Regex("M(\\d+),(\\d+)h1v1h-1z").findAll(svg).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toSet()
        // QR 규격은 코드 둘레에 4칸의 흰 여백(quiet zone)을 요구한다. 화면에서는 흰 배경 위에 놓여 여백이 생기지만, 이 시험은 SVG만 그리므로 직접 둔다
        val margin = 4
        // 같은 QR 코드도 그림 크기에 따라 판독기(ZXing)가 가끔 찾지 못한다(0.3% 안팎, 코드는 크기 2에서는 읽힌다). 올바른 코드는 어떤 크기에서든 읽히면 되므로 여러 크기를 시도한다
        val result = listOf(6, 2, 3, 4, 8).firstNotNullOfOrNull { scale ->
            val side = (size + margin * 2) * scale
            val pixels = IntArray(side * side) { i -> val x = (i % side) / scale - margin; val y = (i / side) / scale - margin; if ((x to y) in dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
            try { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))) } catch (e: com.google.zxing.NotFoundException) { null }
        } ?: error("어떤 크기에서도 읽히지 않았다: $text")
        assertEquals(text, result.text)
    }
}
