package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * jquery-pjax를 htmx로 바꾼 결과를 정적으로 고정한다(`decisions.md` D-96, A-401).
 *
 * - 라이브러리는 공식 배포 파일 htmx 2.0.11이고 sha256이 기준 목록과 같다. `jquery.pjax.js`는 없다.
 * - 앱 코드와 템플릿에는 `$.pjax`, `pjax:` 이벤트, `jquery.pjax` 로드가 남아 있지 않다.
 *   `pjax-container`, `pjax-filter`, `pjax-page`, `data-pjax`는 Yona가 마크업에 쓰는 자체 표지이므로 이름을 그대로 둔다.
 * - 영역을 바꾸는 목록 화면은 컨테이너와 검색 폼에 htmx 속성을 가진다.
 */
class PjaxToHtmxTest {

    private val assets = File("src/main/resources/static/assets")
    private val js = File(assets, "javascripts")
    private val templates = File("src/main/resources/templates")

    private fun appScripts() = listOf("common", "service").flatMap {
        File(js, it).walkTopDown().filter { f -> f.isFile && f.extension == "js" }.toList()
    }

    private fun templateFiles() = templates.walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun manifest() = File("src/test/resources/js-vendor-manifest.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.associateBy { it[0] }

    @Test
    fun `htmx는 공식 2_0_11 파일이고 jquery_pjax는 지웠다`() {
        val htmx = File(js, "lib/htmx.js")
        assertTrue(htmx.isFile, "lib/htmx.js가 없다")
        assertTrue(htmx.readText().contains("version: '2.0.11'"), "htmx 버전이 2.0.11이 아니다")
        assertTrue(!File(js, "lib/jquery/jquery.pjax.js").exists(), "jquery.pjax.js가 남아 있다")
    }

    @Test
    fun `기준 목록에 htmx의 sha256이 있고 파일과 같다`() {
        val row = manifest()["htmx.js"]
        assertTrue(row != null, "htmx.js가 기준 목록에 없다")
        assertEquals("2.0.11", row[2])
        assertEquals(row[3], sha256(File(js, "lib/htmx.js")))
        assertTrue(manifest().keys.none { it.contains("pjax") }, "기준 목록에 pjax가 남아 있다")
    }

    @Test
    fun `앱 코드와 템플릿에 pjax 라이브러리 사용이 없다`() {
        val banned = listOf("""\$\.pjax""", """\.pjax\(""", """jquery\.pjax""", """["']pjax:""", """\$\.support\.pjax""").map { Regex(it) }
        // 주석(옮긴 내용을 설명하는 글)은 제외하고 코드만 본다
        val hits = (appScripts() + templateFiles()).flatMap { f ->
            val text = f.readLines().filterNot { line -> line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") || it.startsWith("<!--") } }.joinToString("\n")
            banned.filter { it.containsMatchIn(text) }.map { "${f.path}: ${it.pattern}" }
        }
        assertEquals(emptyList(), hits, "pjax 라이브러리 사용이 남은 곳")
    }

    @Test
    fun `목록 화면의 pjax-container는 htmx 속성으로 영역 교체를 선언한다`() {
        // 속성 값 안의 '>'(hx-select="div[pjax-container] > *")를 견디도록 따옴표 안은 통째로 넘긴다
        val attr = """(?:[^>"']|"[^"]*"|'[^']*')*"""
        // 조직 풀 리퀘스트 목록은 v1.6에서도 영역 교체 없이 전체 문서 이동이었으므로 htmx 속성이 없어야 한다(아래 별도 시험)
        val containers = templateFiles().filter { it.path != "src/main/resources/templates/organization/pullrequest-list.html" }
            .flatMap { f -> Regex("""<div$attr\bpjax-container\b$attr>""").findAll(f.readText()).map { f.path to it.value }.toList() }
        assertTrue(containers.size >= 4, "pjax-container가 ${containers.size}곳뿐이다")
        for ((path, tag) in containers) {
            assertTrue(tag.contains("""hx-target="this""""), "$path: hx-target=\"this\"가 없다 ($tag)")
            assertTrue(tag.contains("""hx-select="div[pjax-container] > *""""), "$path: hx-select가 없다 ($tag)")
            assertTrue(tag.contains("""hx-swap="innerHTML show:window:top""""), "$path: hx-swap이 없다 ($tag)")
            assertTrue(tag.contains("""hx-push-url="true""""), "$path: hx-push-url이 없다 ($tag)")
            assertTrue(tag.contains("hx-request"), "$path: hx-request(시간 제한)가 없다 ($tag)")
        }
    }

    @Test
    fun `목록 화면의 검색 폼은 hx-get으로 제출한다`() {
        // 영역을 바꾸는 목록 화면의 검색 폼이 있는 템플릿들이다(컨테이너와 같은 파일이 아닐 수도 있다)
        val withContainer = listOf("issue/partial-searchform.html", "issue/my-partial-search.html", "organization/issue-list.html",
            "pullrequest/partial-search.html").map { File(templates, it) }
        val attr = """(?:[^>"']|"[^"]*"|'[^']*')*"""
        val forms = withContainer.flatMap { f -> Regex("""<form${attr}name="search"$attr>""").findAll(f.readText()).map { f.path to it.value }.toList() }
        assertTrue(forms.size >= 4, "검색 폼이 ${forms.size}곳뿐이다")
        for ((path, tag) in forms) {
            assertTrue(tag.contains("hx-get="), "$path: hx-get이 없다 ($tag)")
        }
    }

    @Test
    fun `조직 풀 리퀘스트 목록은 v1_6처럼 htmx를 쓰지 않고 전체 문서 이동을 한다`() {
        val text = File(templates, "organization/pullrequest-list.html").readText()
        assertTrue(!Regex("""\bhx-[a-z-]+=|th:hx-""").containsMatchIn(text), "조직 풀 리퀘스트 목록에 htmx 속성이 있다")
        assertTrue(!text.contains("yona.Htmx"), "조직 풀 리퀘스트 목록이 yona.Htmx를 부른다")
    }

    @Test
    fun `속성 정착을 꺼서 select2가 붙인 속성이 되돌려지지 않게 한다(공식 설정 attributesToSettle)`() {
        val text = File(js, "common/yona.Htmx.js").readText()
        assertTrue(Regex("""htmx\.config\.attributesToSettle\s*=\s*\[\s*]""").containsMatchIn(text), "attributesToSettle을 비우는 설정이 없다")
    }

    @Test
    fun `일반 템플릿은 jquery_pjax를 불러오지 않고 레이아웃 번들이 htmx와 설정 파일을 싣는다`() {
        for (name in listOf("welcome/restart.html", "welcome/secret.html", "layout/migration-layout.html")) {
            assertTrue(!File(templates, name).readText().contains("pjax"), "$name 이 pjax를 불러온다")
        }
        val bundles = File("build-tools/js-bundle/bundles.mjs").readText()
        assertTrue(bundles.contains("lib/htmx.js") && bundles.contains("common/yona.Htmx.js"), "번들 입력에 htmx와 yona.Htmx.js가 없다")
        assertTrue(!bundles.contains("jquery.pjax"), "번들 입력에 jquery.pjax가 남아 있다")
        assertTrue(bundles.indexOf("lib/htmx.js") < bundles.indexOf("common/yona.Htmx.js"), "htmx가 설정 파일보다 앞에 있어야 한다")
    }
}
