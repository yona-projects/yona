package dev.yona.web.code

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 코드 화면의 브랜치 선택 상자 폭을 고정하는 스타일이 있는지 확인한다. 선택 상자에 `form-select`(폭 100%)를 달면 select2가 그 폭을 읽어 상자가 줄 전체를 차지하고,
 * 오른쪽으로 띄워진 상자 옆으로 탭(파일, 커밋, 브랜치, 태그)이 세로로 밀려 깨진다. 그래서 상자 폭을 220px로 고정한다.
 */
class BranchSelectWidthTest {

    @Test
    fun `브랜치 선택 상자의 select2 폭을 고정하는 규칙이 있다`() {
        val css = File("app/assets/stylesheets/scss/_page.scss").readText()
        assertTrue(Regex("""\.code-browse-header\s*>\s*\.select2-container\.float-start\s*\{\s*width:\s*220px\s*!important""").containsMatchIn(css), "폭 고정 규칙이 없다")
    }

    @Test
    fun `커밋 이력과 코드 화면의 브랜치 선택 상자는 위치 클래스를 단다`() {
        File("src/main/resources/templates/code/history.html").readText().let { assertTrue(it.contains("""id="branches" data-toggle="select2" data-format="branch" data-dropdown-css-class="branches" class="float-end form-select"""")) }
        File("src/main/resources/templates/code/view.html").readText().let { assertTrue(it.contains("""id="branches" data-toggle="select2" data-format="branch" data-dropdown-css-class="branches" class="float-start form-select"""")) }
    }
}
