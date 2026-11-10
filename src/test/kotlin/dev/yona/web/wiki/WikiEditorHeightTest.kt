package dev.yona.web.wiki

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 위키 편집창의 높이를 지킨다(`decisions.md` A-452).
 *
 * 이슈와 게시글 화면의 편집창은 옛 스타일 `.content-wrap textarea.content { height: 300px }`(`yobi.css`) 덕분에 300px이다. 위키 화면은 `.content-wrap` 안에 있지 않아
 * 이 규칙을 받지 못하고 기본값(두 줄, 약 40px)으로 그려졌다. 위키 전용 규칙이 같은 높이를 정해야 한다.
 */
class WikiEditorHeightTest {

    @Test
    fun `위키 편집 폼의 편집창은 이슈 편집창과 같은 300px 높이이다`() {
        val scss = File("app/assets/stylesheets/scss/_wiki.scss").readText()
        val rule = Regex("\\.wiki-form\\s+textarea\\.content\\s*\\{([^}]*)\\}").find(scss)
        assertTrue(rule != null && Regex("height:\\s*300px").containsMatchIn(rule.groupValues[1]), "_wiki.scss에 `.wiki-form textarea.content { height: 300px }` 규칙이 있어야 한다")
    }

    @Test
    fun `위키 편집 템플릿은 폼에 wiki-form 클래스를 쓴다`() {
        assertTrue(File("src/main/resources/templates/wiki/edit.html").readText().contains("class=\"wiki-form\""))
    }
}
