package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 화면이 쓰는 JS 라이브러리의 버전과 템플릿의 참조를 고정한다(JS 라이브러리 업그레이드, `decisions.md` D-91, `architecture/js-upgrade-plan.md`).
 * 라이브러리를 올리고 이 시험의 기준을 고치지 않거나, 올리지 않은 라이브러리가 몰래 바뀌면 실패한다.
 *
 * - jQuery는 3.7.1이다(4.0은 하지 않기로 했다). 주 화면의 `yona-layout.js` 번들, 마이그레이션과 첫 설정 화면의 단독 파일이 모두 같은 버전이다.
 * - jQuery UI는 효과만 담은 1.14.2 맞춤 묶음이다.
 */
class JsLibraryVersionsTest {

    private val assets = File("src/main/resources/static/assets")
    private val templates = File("src/main/resources/templates")

    private fun asset(path: String) = File(assets, path)

    private fun templateFiles() = templates.walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

    @Test
    fun `jQuery는 3_7_1 파일이다`() {
        val file = asset("javascripts/lib/jquery/jquery-3.7.1.js")
        assertTrue(file.isFile, "jquery-3.7.1.js가 있어야 한다")
        assertTrue(file.readText().take(400).contains("jQuery JavaScript Library v3.7.1"), file.readText().take(200))
    }

    @Test
    fun `레이아웃 번들 yona-layout_js의 jQuery는 3_7_1이다`() {
        val bundle = asset("javascripts/yona-layout.js").readText()
        assertTrue(bundle.contains("\"3.7.1\""), "번들에 jQuery 3.7.1의 버전 문자열이 있어야 한다")
        assertTrue(!bundle.contains("\"3.3.1\""), "옛 jQuery 3.3.1이 번들에 남아 있으면 안 된다")
    }

    @Test
    fun `jQuery를 불러오는 템플릿은 모두 3_7_1 파일을 쓴다`() {
        val pattern = Regex("""jquery-(\d+\.\d+\.\d+)\.js""")
        val refs = templateFiles().flatMap { f -> pattern.findAll(f.readText()).map { f.name to it.groupValues[1] }.toList() }
        assertTrue(refs.isNotEmpty(), "jQuery를 단독으로 불러오는 템플릿이 있어야 한다(마이그레이션과 첫 설정 화면)")
        assertEquals(emptyList(), refs.filter { it.second != "3.7.1" }, "3.7.1이 아닌 jQuery를 불러오는 템플릿")
    }

    @Test
    fun `jQuery UI는 1_14_2 효과 묶음이고 템플릿은 그것만 쓴다`() {
        val file = asset("javascripts/lib/jquery/jquery-ui-1.14.2.custom.min.js")
        assertTrue(file.isFile, "jquery-ui-1.14.2.custom.min.js가 있어야 한다")
        val head = file.readText().take(600)
        assertTrue(head.contains("jQuery UI - v1.14.2"), head)
        for (effect in listOf("effect.js", "effect-blind.js", "effect-bounce.js", "effect-drop.js", "effect-fade.js", "effect-fold.js", "effect-highlight.js", "effect-shake.js", "effect-transfer.js")) {
            assertTrue(head.contains(effect), "효과 묶음에 $effect 가 들어 있어야 한다: $head")
        }
        val users = templateFiles().filter { it.readText().contains("jquery-ui") }
        assertTrue(users.isNotEmpty())
        users.forEach { assertTrue(it.readText().contains("jquery-ui-1.14.2.custom.min.js") && !it.readText().contains("jquery-ui-1.10.4"), "${it.name}이 1.14.2 묶음을 쓰지 않는다") }
    }

    @Test
    fun `템플릿이 참조하는 JS 파일은 모두 실제로 있다`() {
        // 스타일시트는 빌드에서 SCSS로 만들어지고 이미지는 별도이므로 JS(`javascripts/`, `bootstrap/js/`)만 본다. 파일 이름을 바꿀 때 404가 나는 것을 막는다
        val pattern = Regex("""@\{/assets/((?:javascripts|bootstrap/js)/[^}]+\.js)}""")
        val missing = templateFiles().flatMap { f -> pattern.findAll(f.readText()).map { f.name to it.groupValues[1] }.toList() }
            .filter { (_, path) -> !File(assets, path).isFile }
            .distinct()
        assertEquals(emptyList(), missing, "템플릿이 가리키지만 없는 JS 파일")
    }

    @Test
    fun `벤더 파일은 기준 목록의 공식 배포 파일과 byte 단위로 같다`() {
        val manifest = File("src/test/resources/js-vendor-manifest.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        assertTrue(manifest.size >= 10, "기준 목록이 비어 있다")
        val wrong = manifest.filter { (path, _, _, sha) ->
            val file = File(assets, "javascripts/lib/$path")
            !file.isFile || MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) } != sha
        }.map { "${it[0]} (${it[1]} ${it[2]})" }
        assertEquals(emptyList(), wrong, "기준 목록과 다른 벤더 파일(올렸다면 기준 목록도 고친다)")
    }

    @Test
    fun `레이아웃 번들의 NProgress는 0_2_0이다`() {
        val bundle = asset("javascripts/yona-layout.js").readText()
        assertTrue(bundle.contains("{version:\"0.2.0\"}"), "번들에 NProgress 0.2.0 표기가 있어야 한다")
        assertTrue(!bundle.contains("0.1.6"), "옛 NProgress 0.1.6이 남아 있으면 안 된다")
    }

    @Test
    fun `moment는 모든 로케일 묶음 2_31_0을 쓰고 옛 lang 호출이 없다`() {
        val users = templateFiles().filter { it.readText().contains("moment") }
        assertTrue(users.isNotEmpty())
        users.forEach { assertTrue(!it.readText().contains("moment-with-langs"), "${it.name}이 옛 moment 파일을 가리킨다") }
        assertTrue(templateFiles().none { it.readText().contains("moment.lang(") }, "moment 2.8부터 lang은 locale로 바뀌었다")
        assertTrue(templateFiles().any { it.readText().contains("moment-with-locales.min.js") })
    }

    @Test
    fun `쓰이지 않는 옛 벤더 파일은 저장소에 없다`() {
        // 참조를 검사해 지운 파일이다(D-91, A-393). 템플릿, JS, 번들 입력 목록, Kotlin 어디에서도 불러오지 않는다. v1.6 원본 사본은 `public/`에 그대로 있다
        val removed = listOf(
            "jquery/jquery-1.7.2.js", "jquery/jquery-1.7.2.min.js", "jquery/jquery-1.9.0.js", "jquery/jquery-3.3.1.js",
            "jquery/jquery-ui-1.10.4.custom.min.js", "jquery/jquery-ui.custom.js", "moment-with-langs.min.js", "underscore.js",
            "magnific-popup/jquery.magnific-popup.js", "magnific-popup/jquery.magnific-popup.min.js", "jquery/jquery.dynatree.js",
            "jquery/jquery.event.drag-2.2.js", "jquery/jquery.validate.min.js", "jquery/jquery.scrollTo.js"
        )
        assertEquals(emptyList(), removed.filter { File(assets, "javascripts/lib/$it").exists() }, "지웠던 파일이 다시 들어왔다")
    }
}
