package dev.yona.domain.issue

import org.springframework.web.util.HtmlUtils

/**
 * 라벨 색을 CSS로 바꾼다. v1.6 `app/views/common/issueLabelColor.scala.html`에 해당한다.
 *
 * 글자색은 배경색의 밝기(`R*0.21 + G*0.72 + B*0.07`)가 192를 넘으면 `dimgray`, 아니면 `white`이다.
 * 색은 `rgb(r,g,b)` 또는 16진수(`#` 생략 가능)를 읽는다. 읽을 수 없는 색은 흰색으로 본다.
 *
 * v1.6 구현은 3자리 16진수(`#abc`)를 6자리로 늘리려다 문자열 전체를 두 번 반복해서(`"abc" * 2`) 길이가 맞지 않게 되고,
 * 그 결과 흰색으로 처리한다. 화면에서 3자리 색을 입력하면 그대로 드러나는 동작이라 같게 재현한다
 * ([refactoring-backlog.md] R-22).
 */
object IssueLabelStyles {

    private const val DARK_THRESHOLD = 192
    private val HEX_LIKE = Regex("^[#]*[0-9a-z]+$")

    /** 라벨 목록의 스타일 시트 본문이다. */
    fun css(labels: List<IssueLabel>): String = buildString {
        for (label in labels) {
            val color = HtmlUtils.htmlEscape(label.color.orEmpty())
            append(".issue-label[data-label-id=\"${label.id}\"]{\n")
            append("    box-shadow: inset 2px 0 0px $color;\n")
            append("    -webkit-box-shadow: inset 2px 0 0px $color;\n")
            append("    -moz-box-shadow: inset 2px 0 0px $color;\n")
            append("}\n")
            append(".issue-label.active[data-label-id=\"${label.id}\"]{\n")
            append("    background-color: $color;\n")
            append("    color: ${textColor(label.color.orEmpty())};\n")
            append("}\n")
        }
    }

    /** 배경색 [background] 위에 쓸 글자색이다. */
    fun textColor(background: String): String {
        val rgb = parse(background.lowercase())
        val brightness = rgb[0] * 0.21 + rgb[1] * 0.72 + rgb[2] * 0.07
        return if (brightness > DARK_THRESHOLD) "dimgray" else "white"
    }

    private fun parse(color: String): IntArray = try {
        when {
            color.startsWith("rgb") -> parseRgb(color)
            HEX_LIKE.matches(color) -> parseHex(color)
            else -> WHITE
        }
    } catch (e: RuntimeException) {
        WHITE
    }

    private fun parseRgb(color: String): IntArray {
        val parts = color.substring(color.indexOf('(') + 1, color.indexOf(')')).split(",")
        return intArrayOf(parts[0].trim().toInt(), parts[1].trim().toInt(), parts[2].trim().toInt())
    }

    private fun parseHex(color: String): IntArray {
        val prefixed = if (color.indexOf('#') != 0) "#$color" else color
        // v1.6 결함 재현: 3자리 색은 문자열 전체를 반복하므로 6자리가 되지 못한다.
        val base = if (prefixed.length == 4) "#" + prefixed.substring(1).repeat(2) + prefixed.substring(2).repeat(2) + prefixed.substring(3).repeat(2) else prefixed
        if (base.length != 7) return WHITE
        return intArrayOf(base.substring(1, 3).toInt(16), base.substring(3, 5).toInt(16), base.substring(5, 7).toInt(16))
    }

    private val WHITE get() = intArrayOf(255, 255, 255)
}
