package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * jquery.tmpl을 Mustache로 바꾼 결과를 정적으로 고정한다(`decisions.md` D-94, `architecture/js-library-alternatives.md`).
 *
 * - 템플릿 엔진은 Mustache 4.2.0 공식 파일이고 `yona-common.js` 번들에 들어 있다. jquery.tmpl 파일과 호출은 남아 있으면 안 된다.
 * - Mustache로 그리는 `<script type="text/x-mustache">` 블록에는 jquery.tmpl의 `${}` 표기가 없다.
 * - Yona 자체 도우미 `$yona.tmpl`(`${}` 치환)은 jquery.tmpl과 무관해 그대로 쓰며, 그 블록은 `text/x-yona-template`이다.
 */
class MustacheMigrationTest {

    private val assets = File("src/main/resources/static/assets")
    private val templates = File("src/main/resources/templates")

    private fun appScripts() = listOf("common", "service", "template").flatMap {
        File(assets, "javascripts/$it").walkTopDown().filter { f -> f.isFile && f.extension == "js" }.toList()
    }

    private fun templateFiles() = templates.walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

    private fun code(file: File) = file.readLines()
        .filterNot { line -> line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") || it.startsWith("<!--") } }
        .joinToString("\n")

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `Mustache는 공식 4_2_0 파일이고 기준 목록과 같다`() {
        val file = File(assets, "javascripts/lib/mustache.js")
        assertTrue(file.isFile, "lib/mustache.js가 있어야 한다")
        assertTrue(file.readText().contains("mustache.js - Logic-less {{mustache}} templates with JavaScript"))
        val row = File("src/test/resources/js-vendor-manifest.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
            .firstOrNull { it[0] == "mustache.js" }
        assertTrue(row != null, "기준 목록에 mustache.js가 없다")
        assertEquals("4.2.0", row!![2])
        assertEquals(row[3], sha256(file))
    }

    @Test
    fun `jquery_tmpl 파일은 지웠고 번들 입력에서 빠졌으며 Mustache가 들어 있다`() {
        assertTrue(!File(assets, "javascripts/lib/jquery/jquery.tmpl.js").exists(), "jquery.tmpl.js가 남아 있다")
        val bundles = File("build-tools/js-bundle/bundles.mjs").readText()
        assertTrue(!bundles.contains("jquery.tmpl"), "번들 입력에 jquery.tmpl이 남아 있다")
        assertTrue(bundles.contains("lib/mustache.js"), "번들 입력에 mustache.js가 없다")
    }

    @Test
    fun `앱 코드와 템플릿에 jquery_tmpl 호출이 없다`() {
        val banned = listOf(Regex("""\$\.tmpl\s*\("""), Regex("""\)\s*\.tmpl\s*\("""), Regex("""\.tmpl\(\)"""), Regex("x-jquery-tmpl"))
        val hits = (appScripts() + templateFiles()).flatMap { f ->
            val text = code(f)
            banned.filter { it.containsMatchIn(text) }.map { "${f.path}: ${it.pattern}" }
        }
        assertEquals(emptyList(), hits, "jquery.tmpl 호출이나 type이 남은 곳")
    }

    @Test
    fun `Mustache 블록은 중괄호 표기만 쓰고 자체 도우미 블록은 달러 표기만 쓴다`() {
        val block = Regex("""<script[^>]*type="text/x-(mustache|yona-template)"[^>]*>([\s\S]*?)</script>""")
        val dollar = Regex("""\$\{[^}]*}""")
        val wrong = templateFiles().flatMap { f ->
            block.findAll(f.readText()).mapNotNull { m ->
                val body = m.groupValues[2]
                when (m.groupValues[1]) {
                    "mustache" -> if (dollar.containsMatchIn(body)) "${f.name}: mustache 블록에 \${}가 있다" else null
                    else -> if (body.contains("{{")) "${f.name}: 자체 도우미 블록에 {{}}가 있다" else null
                }
            }.toList()
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `템플릿에서 jquery_tmpl을 불러오는 script는 없다`() {
        val hits = templateFiles().filter { it.readText().contains("jquery.tmpl") }.map { it.path }
        assertEquals(emptyList(), hits)
    }
}
