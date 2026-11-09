package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * jQuery 4로 올릴 준비를 고정한다(`decisions.md` D-103, A-410, `architecture/js-upgrade-plan.md`).
 *
 * jQuery 4는 `$.trim`, `$.parseJSON`, `$.isNumeric`, `$.isFunction`, `$.isArray`, `$.isWindow`, `$.camelCase`, `$.nodeName`, `$.type`, `$.now`, `$.unique`와
 * `$.expr.filters`를 지운다. 이들은 표준 JS(`String.prototype.trim`, `JSON.parse`, `Array.isArray`, `typeof`, `Date.now` 등)로 바꿀 수 있어
 * 앱 코드와 Yona가 손대는 플러그인에서 먼저 없앤다. jQuery는 아직 3.7.1이므로 이 시험은 "올릴 수 있는 상태"만 지킨다.
 *
 * 함께, 호출하는 곳이 없는 것으로 확인되어 지운 파일이 다시 생기거나 참조가 남지 않도록 고정한다.
 */
class Jquery4ReadinessTest {

    private val js = File("src/main/resources/static/assets/javascripts")
    private val bootstrapJs = File("src/main/resources/static/assets/bootstrap/js")
    private val templates = File("src/main/resources/templates")

    private val removedApi = Regex("""(?:\$|jQuery)\.(trim|parseJSON|isNumeric|isFunction|isArray|isWindow|camelCase|nodeName|type|now|unique)\s*\(|\.expr\.filters""")

    /** 주석(설명하는 글)은 빼고 코드만 읽는다. */
    private fun code(file: File) = file.readLines().filterNot { line ->
        line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
    }

    /** 앱이 직접 쓰거나 Yona가 고쳐 쓰는 스크립트. 공식 배포 그대로인 라이브러리(jQuery, jQuery UI, select2 등)는 뺀다. */
    private fun ownScripts(): List<File> =
        File(js, "common").listFiles { f -> f.extension == "js" }!!.toList() +
            File(js, "service").listFiles { f -> f.extension == "js" }!!.toList() +
            listOf(
                "lib/jquery/jquery.form.js", "lib/jquery/jquery.requestAs.js", "lib/elevator/jquery.elevator.js",
                "lib/viewerjs/jquery-viewer.js", "lib/jquery.pageslide.js",
            ).map { File(js, it) } +
            listOf("bootstrap-typeahead.js", "bootstrap-better-typeahead.js").map { File(bootstrapJs, it) }

    @Test
    fun `앱 코드와 Yona가 고쳐 쓰는 플러그인은 jQuery 4에서 지워지는 API를 쓰지 않는다`() {
        val hits = ownScripts().flatMap { f -> code(f).mapIndexedNotNull { i, line -> if (removedApi.containsMatchIn(line)) "${f.path}:${i + 1}: ${line.trim().take(90)}" else null } }
        assertEquals(emptyList(), hits, "jQuery 4에서 지워지는 API를 아직 쓰는 곳(표준 JS로 바꾼다)")
    }

    @Test
    fun `템플릿의 인라인 스크립트도 jQuery 4에서 지워지는 API를 쓰지 않는다`() {
        val hits = templates.walkTopDown().filter { it.isFile && it.extension == "html" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (!line.trim().startsWith("<!--") && removedApi.containsMatchIn(line)) "${f.path}:${i + 1}: ${line.trim().take(90)}" else null }
        }.toList()
        assertEquals(emptyList(), hits, "템플릿 인라인 스크립트에서 아직 쓰는 곳")
    }

    @Test
    fun `호출하는 곳이 없어 지운 파일은 다시 생기지 않는다`() {
        val removed = listOf(
            "lib/mentionjs/mention.js", "lib/mentionjs/mention.css", "lib/jquery/jquery.cookie.js", "lib/jquery/jquery.search.js", "lib/atjs/jquery.browser.js",
            "lib/autosize/autosize.js", "lib/pikaday/pikaday.min.js", "lib/elevator/jquery.elevator.min.js", "lib/elevator/jquery.elevator.min.css",
            "lib/tasklist/gfm-task-list.js", "lib/tasklist/gfm-task-list.css", "common/yona.WatcherList.js", "common/yona.ui.Mergely.js",
            "lib/canvas-to-blob.js",
        )
        assertEquals(emptyList(), removed.filter { File(js, it).exists() }, "지운 파일이 남아 있다")
        val themes = File(js, "lib/highlight/styles").listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("default.css"), themes, "코드 강조 테마는 markdown.html이 쓰는 default.css만 남긴다")
    }

    @Test
    fun `지운 파일을 불러오는 템플릿이나 번들 목록은 없다`() {
        val names = listOf("mentionjs/mention", "jquery.cookie.js", "jquery.search.js", "atjs/jquery.browser.js", "yona.WatcherList", "yona.ui.Mergely", "gfm-task-list", "jquery.elevator.min", "canvas-to-blob")
        val refs = templates.walkTopDown().filter { it.isFile && it.extension == "html" }.flatMap { f ->
            val text = f.readText(); names.filter { text.contains(it) }.map { "${f.path}: $it" }
        }.toList()
        assertEquals(emptyList(), refs, "지운 파일을 가리키는 템플릿")
        val bundles = File("build-tools/js-bundle/bundles.mjs").readText()
        assertFalse(bundles.contains("jquery.search.js"), "번들 목록에 지운 jquery.search.js가 남아 있다")
    }

    @Test
    fun `아바타 자르기는 표준 canvas toBlob을 쓰고 폴리필은 없다`() {
        // canvas-to-blob.js(blueimp 2.0.5)는 toBlob이 없는 브라우저에서만 toBlob을 채우고 dataURLtoBlob은 어디서도 부르지 않았다.
        // Chromium과 Firefox의 toBlob이 PNG를 돌려주는 것을 확인했고, 양쪽에서 폴리필은 아무것도 바꾸지 않았다.
        val text = code(File(js, "service/yona.user.Setting.js")).joinToString("\n")
        assertTrue(text.contains("elCanvas.toBlob(function(oFile)"), "자르기 결과는 표준 toBlob으로 만든다")
        assertFalse(text.contains("dataURLtoBlob"))
    }

    @Test
    fun `타입어헤드는 쓰이고 있으므로 남긴다`() {
        // yona.ui.Typeahead(프로젝트와 조직 구성원 추가, 사이트 대량 메일), yona.issue.LabelEditor, data-provider="typeahead" 입력란이 쓴다.
        assertTrue(File(bootstrapJs, "bootstrap-typeahead.js").isFile)
        assertTrue(File("build-tools/js-bundle/bundles.mjs").readText().contains("bootstrap-typeahead.js"))
        assertTrue(File(js, "common/yona.ui.Typeahead.js").isFile)
    }
}
