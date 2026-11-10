package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 제목 머리말(`[`) 자동완성을 At.js에서 `<title-input>` 컴포넌트로 바꾼 결과를 고정한다(`decisions.md` D-102, A-408).
 *
 * - 머리말을 쓰는 화면(이슈 작성, 이슈 수정, 게시글 작성)의 제목 입력란만 `<title-input>`이다. 본문의 멘션(`@`, `:`, `#`)은 그대로 At.js를 쓴다.
 * - 컴포넌트는 `yona` 저장소가 아니라 `components/title-input`에서 빌드해 단일 번들만 들여온다(sha256 기준 목록 `js-vendor-manifest.tsv`).
 * - 제목 입력란을 일반 입력란처럼 읽고 쓰는 기존 코드(`$("#title").val()`, `.focus()`, `keydown`)는 그대로 동작해야 한다.
 */
class TitleInputIntegrationTest {

    private val templates = File("src/main/resources/templates")
    private val js = File("src/main/resources/static/assets/javascripts")
    private val bundle = "/assets/javascripts/lib/title-input/title-input.min.js"
    private val withHead = listOf("issue/create.html", "issue/edit.html", "board/create.html")

    private fun template(path: String) = File(templates, path).readText()

    /** 주석(설명하는 글)은 빼고 코드만 읽는다. */
    private fun code(file: File) = file.readLines().filterNot { line ->
        line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") || it.startsWith("<!--") }
    }.joinToString("\n")

    @Test
    fun `머리말을 쓰는 세 화면의 제목 입력란은 title-input이다`() {
        for (path in withHead) {
            val text = template(path)
            assertTrue(Regex("""<title-input\b[^>]*\bid="title"""").containsMatchIn(text), "$path: <title-input id=\"title\">이 있어야 한다")
            assertFalse(Regex("""<input\b[^>]*\bid="title"""").containsMatchIn(text), "$path: 옛 <input id=\"title\">이 남아 있으면 안 된다")
        }
    }

    @Test
    fun `제목 입력란의 속성은 호스트에 그대로 옮겨졌다(name, maxlength, tabindex, placeholder, 오류 표시)`() {
        for (path in withHead) {
            val tag = Regex("""<title-input\b[^>]*>""", RegexOption.DOT_MATCHES_ALL).find(template(path))!!.value
            for (needed in listOf("name=\"title\"", "maxlength=\"250\"", "tabindex=\"1\"", "th:placeholder=", "errors['title'] != null} ? 'error'")) {
                assertTrue(tag.contains(needed), "$path: <title-input>에 $needed 가 있어야 한다: $tag")
            }
            assertFalse(tag.contains("form-control"), "$path: 호스트에 form-control을 붙이면 안쪽 입력란과 모양이 겹친다")
        }
        assertTrue(Regex("""<title-input\b[^>]*th:value="\$\{titleValue\}"""").containsMatchIn(template("issue/edit.html")), "수정 화면은 th:value로 현재 제목을 채운다")
        assertTrue(Regex("""<title-input\b[^>]*th:title="#\{title\.help\.key\}"""").containsMatchIn(template("issue/create.html")), "작성 화면의 도움말 툴팁(title 속성)")
        assertTrue(Regex("""<title-input\b[^>]*class="zen-mode text title""").containsMatchIn(template("board/create.html")), "게시글 작성은 zen-mode 표지를 유지한다")
    }

    @Test
    fun `머리말을 쓰는 세 화면만 번들을 module로 불러오고 본문 멘션은 레이아웃의 text-expander가 맡는다`() {
        for (path in withHead) {
            val text = template(path)
            assertTrue(Regex("""<script[^>]*type="module"[^>]*th:src="@\{$bundle\}"""").containsMatchIn(text) ||
                Regex("""<script[^>]*th:src="@\{$bundle\}"[^>]*type="module"""").containsMatchIn(text), "$path: 번들을 type=module로 불러와야 한다")
            assertFalse(text.contains("lib/atjs/"), "$path: At.js는 text-expander로 바뀌어 더 불러오지 않는다(D-106)")
        }
        val others = templates.walkTopDown().filter { it.isFile && it.extension == "html" }.map { it.relativeTo(templates).path }
            .filter { it !in withHead && File(templates, it).readText().contains("title-input") }.toList()
        assertEquals(emptyList(), others, "머리말을 쓰지 않는 화면이 title-input을 불러오면 안 된다")
    }

    @Test
    fun `세 화면은 머리말 모듈을 title-input 선택자로 부르고 후보 주소를 넘긴다`() {
        for (path in withHead) {
            val text = template(path)
            // 인라인 표현 `${...}`에 `}`가 들어 있어 정규식으로 끝을 찾지 않고 호출 시작부터 일정 길이를 읽는다.
            val call = text.substringAfter("yonaTitleHeadModule(", "").take(400)
            assertTrue(Regex("""["']target["']\s*:\s*['"]title-input#title['"]""").containsMatchIn(call), "$path: target이 title-input#title이어야 한다(옛 input[id=title]이면 새 요소를 찾지 못한다): $call")
            assertTrue(call.contains("titleHeads"), "$path: 머리말 후보 주소(titleHeads)를 넘겨야 한다: $call")
        }
    }

    @Test
    fun `머리말 모듈은 At js와 jQuery browser 없이 title-input 이벤트로 라벨을 고른다`() {
        val text = code(File(js, "esm/TitleHeadAutoCompletion.js"))
        for (banned in listOf("atwho", "jQuery.browser", "keypress", "nKeyupEventGenerator", "remoteFilter")) assertFalse(text.contains(banned), "$banned 가 남아 있다")
        for (needed in listOf("title-input-select", "preventDefault", "labelIds", "change.select2", "title-input-loadstart", "title-input-loadend", "yonaTitleHeadModule")) {
            assertTrue(text.contains(needed), "$needed 가 있어야 한다")
        }
    }

    @Test
    fun `제목을 찾는 화면 스크립트는 태그 이름에 기대지 않는다(title-input도 찾는다)`() {
        // 게시글 작성(`board/create`는 title-input, `board/edit`는 input)이 같은 스크립트를 쓴다. `input#title`로 찾으면 title-input에서는 포커스와 Enter 처리가 붙지 않는다.
        val text = code(File(js, "esm/service/board.Write.js"))
        assertFalse(text.contains("input#title"), "yona.board.Write.js가 input#title로 제목을 찾는다")
        assertTrue(Regex("""welInputTitle\s*=\s*\$\(["']#title["']\)""").containsMatchIn(text), "제목은 #title로 찾아야 한다")
    }

    @Test
    fun `번들은 벤더 파일 기준 목록에 있고 컴포넌트 빌드와 byte 단위로 같다`() {
        val file = File(js, "lib/title-input/title-input.min.js")
        assertTrue(file.isFile, "번들이 있어야 한다")
        val row = File("src/test/resources/js-vendor-manifest.tsv").readLines().filter { !it.startsWith("#") && it.startsWith("title-input/") }.map { it.split('\t') }
        assertEquals(1, row.size, "기준 목록에 title-input 번들 한 줄이 있어야 한다")
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(sha, row.single()[3])
        val built = File("../components/title-input/dist/title-input.min.js")
        if (built.isFile) assertEquals(sha, MessageDigest.getInstance("SHA-256").digest(built.readBytes()).joinToString("") { "%02x".format(it) }, "components에서 다시 빌드했다면 번들과 기준 목록을 함께 갱신한다")
    }

    @Test
    fun `스타일은 v1_6 제목 입력란의 규칙을 title-input 호스트와 안쪽 part에 준다`() {
        val scss = File("app/assets/stylesheets/scss/_page.scss").readText()
        val block = Regex("""title-input\.title\s*\{[^}]*\}""").find(scss)?.value ?: ""
        assertTrue(block.contains("calc(97% + 12px)"), "호스트 폭은 옛 입력란(width:97%에 안쪽 여백 12px)의 바깥 폭과 같아야 한다: $block")
        assertTrue(block.contains("margin") && block.contains("1.125rem"), "여백 15px와 글자 크기 1.125rem: $block")
        assertTrue(block.contains("border: none") && block.contains("vertical-align: baseline"), "호스트에는 `.content-wrap .title`의 밑줄이 따로 그려지지 않게 한다(밑줄은 안쪽 입력란의 것 하나뿐): $block")
        val part = Regex("""title-input\.title::part\(input\)\s*\{[^}]*\}""").find(scss)?.value ?: ""
        assertTrue(part.contains("border-bottom") && part.contains("#ddd") && part.contains("padding: 4px 6px"), "밑줄 1px #ddd와 안쪽 여백 4px 6px: $part")
        val placeholder = Regex("""title-input\.title::part\(input\)::placeholder\s*\{[^}]*\}""").find(scss)?.value ?: ""
        assertTrue(placeholder.contains("#595959") && placeholder.contains("opacity: 1"), "안내 글자 색은 옛 입력란(rgb(89, 89, 89))과 같아야 한다: $placeholder")
        val focus = Regex("""title-input\.title::part\(input\):focus\s*\{[^}]*\}""").find(scss)?.value ?: ""
        assertTrue(focus.contains("#F36C22", ignoreCase = true) && focus.contains("box-shadow: none"), "포커스는 주황 밑줄, 그림자 없음: $focus")
    }
}
