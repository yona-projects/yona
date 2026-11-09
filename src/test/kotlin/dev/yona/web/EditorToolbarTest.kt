package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 편집기 서식 단추(GitHub의 `markdown-toolbar-element`, D-105)를 확인한다.
 * v1.6 편집기에는 도움말만 있었고 서식 단추는 추가 개발 항목(D-28)이다. 단추는 입력란 위에 두고, 모듈 하나(번들 없음)를 `type="module"`로 불러온다.
 */
class EditorToolbarTest {
    private val templates = File("src/main/resources/templates")
    private val vendor = File("src/main/resources/static/assets/javascripts/lib/markdown-toolbar")
    private val editor get() = File(templates, "common/editor.html").readText()

    private val buttons = listOf(
        "md-header" to "common.editor.toolbar.header", "md-bold" to "common.editor.toolbar.bold", "md-italic" to "common.editor.toolbar.italic",
        "md-quote" to "common.editor.toolbar.quote", "md-code" to "common.editor.toolbar.code", "md-link" to "common.editor.toolbar.link",
        "md-unordered-list" to "common.editor.toolbar.unorderedList", "md-ordered-list" to "common.editor.toolbar.orderedList",
        "md-mention" to "common.editor.toolbar.mention", "md-ref" to "common.editor.toolbar.ref",
    )

    @Test
    fun `서식 단추 모듈은 공식 2_2_3 파일 그대로이고 라이선스가 함께 있다`() {
        val js = File(vendor, "markdown-toolbar-element.js")
        assertTrue(js.isFile, "공식 dist/index.js를 그대로 들인 파일")
        assertTrue(File(vendor, "LICENSE").readText().contains("MIT"))
        val row = File("src/test/resources/js-vendor-manifest.tsv").readLines().first { it.startsWith("markdown-toolbar/markdown-toolbar-element.js") }.split("\t")
        assertEquals("2.2.3", row[2])
        assertEquals(MessageDigest.getInstance("SHA-256").digest(js.readBytes()).joinToString("") { "%02x".format(it) }, row[3])
    }

    @Test
    fun `레이아웃이 서식 단추 모듈을 type module로 한 번 불러온다`() {
        val scripts = File(templates, "common/scripts.html").readText()
        assertTrue(Regex("""<script[^>]*type="module"[^>]*lib/markdown-toolbar/markdown-toolbar-element\.js""").containsMatchIn(scripts) ||
            Regex("""<script[^>]*lib/markdown-toolbar/markdown-toolbar-element\.js[^>]*type="module"""").containsMatchIn(scripts), "scripts.html에 module 태그")
    }

    @Test
    fun `편집기 조각은 textarea 위에 단추 열 개를 두고 for가 textarea의 id를 가리킨다`() {
        val toolbarAt = editor.indexOf("<markdown-toolbar")
        val textareaAt = editor.indexOf("<textarea")
        assertTrue(toolbarAt in 0 until textareaAt, "markdown-toolbar가 textarea 앞에 있다")
        assertTrue(editor.contains("""th:attr="for=${'$'}{'editor-' + textareaName + '-' + wrapId}""""), "for가 textarea id 식(editor-이름-식별자)과 같다")
        assertTrue(editor.contains("""th:id="'editor-' + ${'$'}{textareaName} + '-' + ${'$'}{wrapId}""""), "textarea id 식")
        for ((tag, key) in buttons) {
            assertTrue(editor.contains("<$tag"), "$tag 단추")
            assertTrue(editor.contains("#{$key}"), "$key 로 라벨을 단다")
        }
        assertTrue(editor.contains("add-task-list-button"), "v1.6의 체크리스트 추가 단추는 그대로 둔다")
    }

    @Test
    fun `단추 라벨 키가 모든 언어 번들에 있다`() {
        for (bundle in listOf("messages.properties", "messages_ko_KR.properties", "messages_ja_JP.properties", "messages_ru_RU.properties", "messages_uz_UZ.properties")) {
            val props = Properties().also { p -> File("src/main/resources/$bundle").reader(Charsets.UTF_8).use { p.load(it) } }
            for ((_, key) in buttons) assertTrue(props.getProperty(key)?.isNotBlank() == true, "$bundle 에 $key")
        }
    }
}
