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
 * - 앱 코드, 템플릿, 서버 코드에는 `pjax`라는 이름이 남아 있지 않다(D-98). 마크업 표지는 `list-container`, `list-filter`, `list-page`이고,
 *   서버는 htmx가 보내는 `HX-Request` 헤더와 `format=fragment`로 목록 조각을 구분한다.
 * - 영역을 바꾸는 목록 화면은 컨테이너와 검색 폼에 htmx 속성을 가진다.
 */
class HtmxListSwapTest {

    private val assets = File("src/main/resources/static/assets")
    private val js = File(assets, "javascripts")
    private val templates = File("src/main/resources/templates")

    private fun appScripts() = listOf("common", "service", "esm").flatMap {
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
    fun `앱 코드, 템플릿, 서버 코드에 pjax라는 이름이 남아 있지 않다`() {
        val kotlin = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val pjax = Regex("pjax", RegexOption.IGNORE_CASE)
        // 주석(옮긴 내용을 설명하는 글)은 제외하고 코드만 본다
        val hits = (appScripts() + templateFiles() + kotlin).flatMap { f ->
            val text = f.readLines().filterNot { line -> line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") || it.startsWith("<!--") } }.joinToString("\n")
            if (pjax.containsMatchIn(text)) listOf(f.path) else emptyList()
        }
        assertEquals(emptyList(), hits, "pjax라는 이름이 남은 곳")
    }

    @Test
    fun `마크업 표지는 list-container, list-filter, list-page이다`() {
        val templatesText = templateFiles().joinToString("\n") { it.readText() }
        for (mark in listOf("list-container", "list-filter")) assertTrue(templatesText.contains(mark), "템플릿에 $mark 가 없다")
        val scripts = appScripts().joinToString("\n") { it.readText() }
        for (mark in listOf("[list-container]", "a[list-filter]", "a[list-page]")) assertTrue(scripts.contains(mark), "스크립트에 $mark 가 없다")
        assertTrue(!templatesText.contains("data-pjax"), "죽은 속성 data-pjax가 남아 있다")
    }

    @Test
    fun `목록 화면의 list-container는 htmx 속성으로 영역 교체를 선언한다`() {
        // 속성 값 안의 '>'(hx-select="div[list-container] > *")를 견디도록 따옴표 안은 통째로 넘긴다
        val attr = """(?:[^>"']|"[^"]*"|'[^']*')*"""
        // 조직 풀 리퀘스트 목록은 v1.6에서도 영역 교체 없이 전체 문서 이동이었으므로 htmx 속성이 없어야 한다(아래 별도 시험)
        val containers = templateFiles().filter { it.path != "src/main/resources/templates/organization/pullrequest-list.html" }
            .flatMap { f -> Regex("""<div$attr\blist-container\b$attr>""").findAll(f.readText()).map { f.path to it.value }.toList() }
        assertTrue(containers.size >= 4, "list-container가 ${containers.size}곳뿐이다")
        for ((path, tag) in containers) {
            assertTrue(tag.contains("""hx-target="this""""), "$path: hx-target=\"this\"가 없다 ($tag)")
            assertTrue(tag.contains("""hx-select="div[list-container] > *""""), "$path: hx-select가 없다 ($tag)")
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
    fun `설정 파일은 옛 X-PJAX와 X-Requested-With 헤더를 붙이지 않는다`() {
        val text = File(js, "common/yona.Htmx.js").readText()
        val code = text.lines().filterNot { l -> l.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") } }.joinToString("\n")
        assertTrue(!code.contains("X-PJAX") && !code.contains("X-Requested-With"), "옛 헤더를 붙이는 코드가 남아 있다")
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
        assertTrue(!bundles.contains("pjax"), "번들 입력에 pjax가 남아 있다")
        assertTrue(bundles.indexOf("lib/htmx.js") < bundles.indexOf("common/yona.Htmx.js"), "htmx가 설정 파일보다 앞에 있어야 한다")
    }
}
