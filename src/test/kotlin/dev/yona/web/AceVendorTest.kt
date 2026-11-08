package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 코드 보기의 편집기 Ace를 최신 공식 배포(1.45.2)로 고정한다(`decisions.md` D-104, A-411).
 *
 * 코드 보기(`yona.code.Browser.js`)는 `ace.edit`, `setTheme("ace/theme/clouds")`, `setMode("ace/mode/<이름>")`, `setReadOnly` 등 안정된 API만 쓴다.
 * 파일은 npm `ace-builds`의 `src-min-noconflict` 그대로이고(바이트 단위로 같다), 화면이 불러오지 않는 확장, 다른 테마, 키 바인딩, 스니펫은 두지 않는다.
 */
class AceVendorTest {

    private val dir = File("src/main/resources/static/assets/javascripts/lib/ace")
    private val manifest = File("src/test/resources/ace-vendor-manifest.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }

    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `Ace는 1_45_2 공식 파일 그대로이고 그 밖의 파일은 없다`() {
        assertTrue(manifest.size > 150, "기준 목록이 비어 있다")
        assertTrue(dir.resolve("ace.js").readText().contains("version=\"1.45.2\""), "ace.js가 1.45.2가 아니다")
        val wrong = manifest.filter { (name, hash) -> !dir.resolve(name).isFile || sha(dir.resolve(name)) != hash }.map { it[0] }
        assertEquals(emptyList(), wrong, "기준 목록과 다른 파일")
        val extra = dir.listFiles()!!.map { it.name }.filter { n -> manifest.none { it[0] == n } && n != "LICENSE" }.sorted()
        assertEquals(emptyList(), extra, "화면이 쓰지 않는 파일(확장, 다른 테마, 키 바인딩, 스니펫)이 남아 있다")
        assertTrue(dir.resolve("LICENSE").isFile, "Ace의 라이선스(BSD) 파일이 있어야 한다")
    }

    @Test
    fun `코드 보기가 고를 수 있는 모든 모드와 테마 clouds가 있다`() {
        val script = File("src/main/resources/static/assets/javascripts/service/yona.code.Browser.js").readText()
        val map = script.substringAfter("htVar.htExtMap = htVar.htExtMap || {").substringBefore("};")
        val modes = Regex("""^\s*"([a-z0-9_]+)":\s*\[""", RegexOption.MULTILINE).findAll(map).map { it.groupValues[1] }.toList()
        assertTrue(modes.size >= 30, "모드 이름을 읽지 못했다: $modes")
        assertEquals(emptyList(), modes.filter { !dir.resolve("mode-$it.js").isFile }, "확장자 표가 가리키는데 없는 모드")
        assertTrue(dir.resolve("theme-clouds.js").isFile)
        assertTrue(script.contains("ace/theme/clouds"))
    }

    @Test
    fun `코드 보기 템플릿은 ace_js만 불러온다`() {
        val view = File("src/main/resources/templates/code/view.html").readText()
        assertTrue(view.contains("/assets/javascripts/lib/ace/ace.js"))
        assertEquals(1, Regex("""lib/ace/""").findAll(view).count(), "ace.js 하나만 불러온다(모드, 테마, 작업자는 Ace가 필요할 때 불러온다)")
    }
}
