package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * select2를 4.x로 올린 뒤에는 `change` 이벤트 객체에 `val` 속성이 없다(3.x는 선택한 값을 `event.val`에 실어 주었다).
 * 이벤트 객체의 `val`을 읽는 처리기는 4.x에서 `undefined`를 읽어 오류가 나거나 엉뚱한 곳으로 이동한다.
 * 화면 스크립트와 템플릿은 `event.target.value` 또는 `$(this).val()`로 값을 읽어야 한다.
 *
 * 원본: v1.6 `yobi.project.New.js`, `yobi.code.Browser.js`, `yobi.code.Diff.js`, `code/history.scala.html`, `board/view.scala.html`
 * (select2 3.x의 `event.val`에 기댔다).
 */
class Select2EventValueTest {

    /** 이벤트 객체로 보이는 이름(`e`, `evt`, `event`, `weEvt`)의 `.val`을 호출이 아닌 속성으로 읽는 곳이다. */
    private val eventVal = Regex("""\b(e|evt|event|weEvt)\.val\b(?!\s*\()""")

    private fun scan(dir: String, ext: String): List<String> =
        File(dir).walkTopDown().filter { it.isFile && it.extension == ext && !it.path.contains("/lib/") && !it.name.contains(".min.") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if (eventVal.containsMatchIn(line)) "${f.path}:${i + 1}: ${line.trim()}" else null } }
            .toList()

    @Test
    fun `이벤트 객체의 val 속성을 읽는 스크립트와 템플릿이 없다`() {
        val found = scan("src/main/resources/static/assets/javascripts", "js") + scan("src/main/resources/templates", "html")
        assertEquals(emptyList(), found, "select2 4.x change 이벤트에는 val이 없다")
    }
}
