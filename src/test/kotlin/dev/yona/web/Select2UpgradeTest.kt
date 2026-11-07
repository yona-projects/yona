package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * select2를 3.4.5에서 4.1.0으로 올린 결과를 정적으로 고정한다(`decisions.md` D-95, `architecture/select2-upgrade-plan.md`).
 *
 * - 라이브러리는 공식 배포 파일 4.1.0과 Bootstrap 5 테마(`select2-bootstrap-5-theme` 1.3.0)이다.
 * - 4.x는 숨은 `<input>`을 지원하지 않으므로 select2를 붙이는 곳은 모두 `<select>`이다.
 * - 앱 코드(`common`, `service`, 템플릿)에 3.x 전용 옵션, 이벤트, 메서드가 남아 있으면 안 된다.
 */
class Select2UpgradeTest {

    private val assets = File("src/main/resources/static/assets")
    private val lib = File(assets, "javascripts/lib/select2")
    private val templates = File("src/main/resources/templates")

    private fun appScripts() = listOf("common", "service").flatMap {
        File(assets, "javascripts/$it").walkTopDown().filter { f -> f.isFile && f.extension == "js" }.toList()
    }

    private fun templateFiles() = templates.walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `select2는 공식 4_1_0 파일이다`() {
        val js = File(lib, "select2.js")
        assertTrue(js.isFile)
        assertTrue(js.readText().take(200).contains("Select2 4.1.0"), js.readText().take(120))
        assertTrue(File(lib, "select2.css").readText().contains(".select2-container--default"), "4.x 기본 테마 CSS여야 한다")
        assertTrue(File(lib, "select2-bootstrap-5-theme.css").readText().take(200).contains("Bootstrap 5 theme v1.3.0"))
    }

    @Test
    fun `3_x 전용 파일은 지웠다`() {
        for (old in listOf("select2-bootstrap.css", "select2.png", "select2x2.png", "select2-spinner.gif", "select2.jquery.json", "select2.min.js")) {
            assertTrue(!File(lib, old).exists(), "$old 가 남아 있다")
        }
    }

    @Test
    fun `기준 목록에 select2 4_1_0 파일과 테마의 sha256이 있고 파일과 같다`() {
        val rows = File("src/test/resources/js-vendor-manifest.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        val byPath = rows.associateBy { it[0] }
        for ((path, version) in listOf("select2/select2.js" to "4.1.0", "select2/select2.css" to "4.1.0", "select2/select2-bootstrap-5-theme.css" to "1.3.0")) {
            val row = byPath[path]
            assertTrue(row != null, "$path 가 기준 목록에 없다")
            assertEquals(version, row!![2], path)
            assertEquals(row[3], sha256(File(assets, "javascripts/lib/$path")), path)
        }
    }

    @Test
    fun `앱 코드에 select2 3_x 전용 표현이 없다`() {
        val banned = listOf(
            "formatResult", "formatSelection", "initSelection", "select2-selecting", "select2-removing", "select2-open",
            "adaptContainerCssClass", "\\.select2\\(\\s*[\"']val[\"']", "data\\([\"']select2[\"']\\)",
            "evt\\.added", "evt\\.removed", "quietMillis"
        ).map { Regex(it) }
        // 주석(옮긴 내용을 설명하는 글)은 제외하고 코드만 본다
        val hits = (appScripts() + templateFiles()).flatMap { f ->
            val text = f.readLines().filterNot { line -> line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") || it.startsWith("<!--") } }.joinToString("\n")
            banned.filter { it.containsMatchIn(text) }.map { "${f.path}: ${it.pattern}" }
        }
        assertEquals(emptyList(), hits, "3.x 전용 표현이 남은 곳")
    }

    @Test
    fun `select2를 붙이는 담당자, 공유자, 리뷰 요청 입력은 select 요소이다`() {
        val hidden = Regex("""<input[^>]*type="hidden"[^>]*id="(assignee|issueSharer|review-request)"""")
        val hits = templateFiles().flatMap { f -> hidden.findAll(f.readText()).map { "${f.path}: #${it.groupValues[1]}" }.toList() }
        assertEquals(emptyList(), hits, "숨은 input에는 4.x select2를 붙일 수 없다")
    }

    @Test
    fun `common select2 조각은 4_x 로케일과 테마를 싣고 옛 클래스 복사 설정이 없다`() {
        val text = File(templates, "common/select2.html").readText()
        assertTrue(!text.contains("adaptContainerCssClass"))
        assertTrue(text.contains("select2-bootstrap-5-theme.css") || File(templates, "layout/layout.html").readText().contains("select2-bootstrap-5-theme.css"),
            "테마 CSS를 불러와야 한다")
        for (locale in listOf("select2_locale_ko.js", "select2_locale_ja.js", "select2_locale_en.js")) {
            assertTrue(File(lib, locale).isFile, "$locale 이 있어야 한다")
        }
        assertTrue(File(lib, "select2_locale_ko.js").readText().contains("select2/i18n/ko"), "4.x 형식(AMD i18n)이어야 한다")
    }
}
