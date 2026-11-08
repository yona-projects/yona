package dev.yona.domain.twofactor

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** QR 코드 SVG(X-2)를 검증한다: 같은 글을 담은 QR 코드이고 외부 자원을 쓰지 않는다. */
class QrCodeTest {

    @Test
    fun `만든 SVG를 다시 읽으면 원래 글이 나온다`() {
        // 비밀값이 무작위라 여러 번 확인한다. 아래는 SVG의 칸 배치를 그대로 디코더에 넣어 읽어, 그림 판독기(검출기)의 우연한 실패에 기대지 않는다
        repeat(500) { decodeOnce() }
    }

    /**
     * SVG의 어두운 칸(`M x,y h1v1h-1z`)을 `BitMatrix`로 옮겨 ZXing QR 디코더에 직접 넣는다. 이 시험이 보려는 것은 SVG가 같은 글을 담은 QR 코드인가이다.
     * 예전에는 칸 배치를 그림으로 그려 `MultiFormatReader`가 찾게 했는데, 그 검출기가 완벽한 합성 그림에서도 가끔 코드를 못 찾아(전체 시험에서 한 번,
     * 따로 12번 돌려 한 번 실패) 시험이 불안정했다. 코드가 틀린 것이 아니라 그림 판독기의 한계였다.
     */
    private fun decodeOnce() {
        val text = Totp.otpauthUri(Totp.newSecret(), "Yona", "alice@example.com")
        val svg = QrCode.svg(text)
        assertTrue(svg.startsWith("<svg") && !svg.contains("<image") && !svg.contains("href"))
        val size = Regex("viewBox=\"0 0 (\\d+) (\\d+)\"").find(svg)!!.destructured.let { it.component1().toInt() }
        // SVG는 사방 4칸의 여백(quiet zone)을 포함한다(`QrCode.svg`). 디코더는 코드 부분만 받으므로 여백을 잘라 낸다
        val quiet = 4
        val matrix = BitMatrix(size - quiet * 2)
        Regex("M(\\d+),(\\d+)h1v1h-1z").findAll(svg).forEach {
            val x = it.groupValues[1].toInt() - quiet
            val y = it.groupValues[2].toInt() - quiet
            if (x >= 0 && y >= 0 && x < matrix.width && y < matrix.height) matrix.set(x, y)
        }
        assertEquals(text, Decoder().decode(matrix).text)
    }
}
