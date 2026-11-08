package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * v1.6 시절에 화면(템플릿)이 사라졌는데 JS만 남은 죽은 코드를 지운 결과를 고정한다(`decisions.md` D-97, A-402).
 *
 * - 담당자 선택을 option 목록에서 비동기 검색으로 바꾼 2017-08-05 커밋(`5170f1c21`) 뒤로 `data-force-change`, `data-non-member`를
 *   달던 option과 `name="assignee.user.id"` 요소는 없다. 그 속성과 이름을 읽던 JS가 남아 있었다.
 * - 프로젝트 라벨(태그) 기능은 2017-01-12 커밋(`133a42e60`)에서 화면이 사라졌다. `project.Home.js`의 라벨 편집 코드와
 *   그 템플릿(`#label-template` 등)을 가리키던 호출이 남아 있었다.
 */
class V16DeadScriptRemovalTest {

    private val js = File("src/main/resources/static/assets/javascripts")
    private val templates = File("src/main/resources/templates")

    /** 주석(설명하는 글)은 빼고 코드만 읽는다. */
    private fun code(file: File) = file.readLines().filterNot { line ->
        line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") || it.startsWith("<!--") }
    }.joinToString("\n")

    private fun appFiles() = (listOf("common", "service").flatMap { File(js, it).walkTopDown().filter { f -> f.isFile && f.extension == "js" }.toList() } +
        templates.walkTopDown().filter { it.isFile && it.extension == "html" }.toList())

    @Test
    fun `담당자 선택의 옛 option 속성과 옛 이름을 읽는 코드는 없다`() {
        val banned = listOf("forceChange", "force-change", "nonMember", "non-member", "assignee.user.id").map { Regex(Regex.escape(it)) }
        val hits = appFiles().flatMap { f -> code(f).let { text -> banned.filter { it.containsMatchIn(text) }.map { "${f.path}: ${it.pattern}" } } }
        assertEquals(emptyList(), hits, "담당자 선택의 죽은 코드가 남은 곳")
    }

    @Test
    fun `프로젝트 라벨 편집의 죽은 코드와 없는 템플릿 호출은 없다`() {
        val banned = listOf(
            "#label-template", "#plus-button-template", "#category-template", "#label-delete-button-template",
            "_initLabels", "_createLabel", "_appendLabels", "__addCategory", "_createCategory", "_submitLabel", "_labelFromInput",
            "_onKeyPressNewLabel", "_onKeyPressNewCategory", "_onClickNewCategory", "_onClickPlusCategory", "_onClickPlusLabel",
            "_setLabelsRemovability", "_hideLabelEditor", "_showLabelEditor",
            "sURLProjectLabels", "sURLLabelCategories", "sURLLabels", "nProjectId", "welLabelBoard", "welLabelEditorToggle"
        ).map { Regex(Regex.escape(it)) }
        val files = listOf(File(js, "service/yona.project.Home.js"), File(templates, "project/home.html"))
        val hits = files.flatMap { f -> code(f).let { text -> banned.filter { it.containsMatchIn(text) }.map { "${f.path}: ${it.pattern}" } } }
        assertEquals(emptyList(), hits, "프로젝트 라벨 편집의 죽은 코드가 남은 곳")
    }

    @Test
    fun `프로젝트 홈의 살아 있는 동작은 그대로 남아 있다`() {
        val text = code(File(js, "service/yona.project.Home.js"))
        for (live in listOf("yona.Clipboard.copy", "descriptionSaveBtn", "projectLeaveBtn", "sURLProject")) {
            assertEquals(true, text.contains(live), "$live 가 없다")
        }
    }
}
