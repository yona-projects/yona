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
        val text = Totp.otpauthUri(Totp.newSecret(), "Yona", "alice@example.com")
        val svg = QrCode.svg(text)
        assertTrue(svg.startsWith("<svg") && !svg.contains("<image") && !svg.contains("href"))
        val size = Regex("viewBox=\"0 0 (\\d+) (\\d+)\"").find(svg)!!.destructured.let { it.component1().toInt() }
        val dark = Regex("M(\\d+),(\\d+)h1v1h-1z").findAll(svg).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toSet()
        val scale = 6
        val pixels = IntArray(size * scale * size * scale) { i -> val x = (i % (size * scale)) / scale; val y = (i / (size * scale)) / scale; if ((x to y) in dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size * scale, size * scale, pixels))))
        assertEquals(text, result.text)
    }
}
