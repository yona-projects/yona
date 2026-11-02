package dev.yona.domain.twofactor

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 글을 QR 코드로 바꾼다(추가 개발 항목 X-2, `decisions.md` D-69). 화면에 그대로 넣을 수 있는 인라인 SVG로 내보내므로 이미지 파일이나 외부 서비스가 필요 없다(비밀 값이 밖으로 나가지 않는다). */
object QrCode {

    /** [text]를 담은 QR 코드의 SVG 마크업이다. 여백(quiet zone) 네 칸을 포함하고 `viewBox`로 크기가 조절된다. */
    fun svg(text: String): String {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
        val path = StringBuilder()
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix.get(x, y)) path.append("M$x,${y}h1v1h-1z")
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${matrix.width} ${matrix.height}" shape-rendering="crispEdges" role="img"><rect width="100%" height="100%" fill="#fff"/><path d="$path" fill="#000"/></svg>"""
    }
}
