package dev.yona.audit

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 글자 크기와 줄 높이가 `rem`으로 정해져 있는지 지키는 구조 검사이다(`decisions.md` D-55, A-298).
 *
 * 자체 SCSS가 `font-size`나 `line-height`를 px로 적으면 사용자가 기본 글자 크기를 키워도 글자와 줄 간격이 따라가지 않는다.
 * 기본 설정(루트 16px)에서는 `px/16`의 `rem`과 화면이 같으므로 새 규칙은 `rem`(또는 `em`, `%`, 비율)으로 적는다.
 */
class RelativeUnitsTest {

    private val px = Regex("""(?<![-\w$])(font-size|line-height)\s*:\s*\d*\.?\d+px""")

    @Test
    fun `자체 SCSS는 글자 크기와 줄 높이를 px로 적지 않는다`() {
        val offenders = File("app/assets/stylesheets/scss").listFiles { f -> f.name.endsWith(".scss") }!!
            .sortedBy { it.name }
            .flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    if (px.containsMatchIn(code)) "${f.name}:${i + 1}: ${line.trim()}" else null
                }
            }
        assertTrue(offenders.isEmpty(), "px로 적힌 글자 크기나 줄 높이가 ${offenders.size}곳 있다:\n${offenders.take(10).joinToString("\n")}")
    }

    @Test
    fun `본문 줄 높이는 rem 변수로 덮는다`() {
        val base = File("app/assets/stylesheets/scss/_base.scss").readText()
        assertTrue(Regex("""--bs-body-line-height:\s*1\.25rem""").containsMatchIn(base))
    }
}
