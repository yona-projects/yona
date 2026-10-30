package dev.yona.audit

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bootstrap 전환(로드맵 X-38, `decisions.md` D-53)의 구조 검사이다. 템플릿, 자체 JS, 자체 LESS에서 **단계마다 사라져야 할 낡은 표기**를 세어 둔 기준
 * (`src/test/resources/bs/legacy-baseline.json`)과 대조한다.
 *
 * - 기준보다 **늘면** 실패한다: 전환 중에 낡은 표기가 새로 들어오는 것을 막는다.
 * - 기준보다 **줄어도** 실패한다: 기준을 새로 쓰라는 알림이다(`UPDATE_BS_BASELINE=1`로 실행하면 현재 값으로 다시 쓴다). 그래서 기준은 항상 현재 값과 같고, 전환이 진행된 정도가 기준 파일의 변화로 남는다.
 * - 각 패턴은 어느 단계(`removedAt`)에서 0이 되어야 하는지 가진다. 그 단계를 마쳤다고 선언하려면 [DONE_STAGES]에 올리고, 올린 단계의 패턴은 0이어야 한다.
 *
 * 대상은 우리 코드이다. 번들된 외부 라이브러리(`static/assets/bootstrap`, `javascripts/lib`)와 컴파일된 CSS는 세지 않는다.
 */
class LegacyBootstrapLintTest {

    /** 낡은 표기 하나이다. [regex]가 [scope]의 파일 본문에서 찾는 대상이고 [removedAt] 단계가 끝나면 0이어야 한다. */
    private data class Legacy(val id: String, val regex: Regex, val scope: Scope, val removedAt: Int, val note: String)

    private enum class Scope { TEMPLATE_CLASS, TEMPLATE_ATTR, JS, LESS }

    private val patterns = listOf(
        // --- 3 단계(2→3)에서 사라진다: 그리드와 BS2 전용 클래스 ---
        Legacy("grid.span", Regex("""(?<![\w-])span(?:1[0-2]|[1-9])(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "span1~12를 col-*로"),
        Legacy("grid.offset", Regex("""(?<![\w-])offset(?:1[0-2]|[1-9])(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "offset1~12를 col-*-offset-*로"),
        Legacy("grid.row-fluid", Regex("""(?<![\w-])row-fluid(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "row-fluid를 row로"),
        Legacy("btn.size", Regex("""(?<![\w-])btn-(?:mini|small|large)(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "btn-mini/small/large를 btn-xs/sm/lg로"),
        Legacy("form.append", Regex("""(?<![\w-])input-(?:append|prepend)(?![\w-])|(?<![\w-])add-on(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "input-group으로"),
        Legacy("form.horizontal", Regex("""(?<![\w-])control-group(?![\w-])|(?<![\w-])controls(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "form-group으로"),
        Legacy("visibility", Regex("""(?<![\w-])(?:hidden|visible)-(?:phone|tablet|desktop)(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "hidden-xs 등으로"),
        Legacy("icon.i", Regex("""(?<![\w-])icon-[a-z]+(?:-[a-z]+)*(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "BS2 아이콘(glyph)"),
        Legacy("nav.legacy", Regex("""(?<![\w-])nav-list(?![\w-])|(?<![\w-])navbar-inner(?![\w-])|(?<![\w-])img-polaroid(?![\w-])|(?<![\w-])hero-unit(?![\w-])|(?<![\w-])thumbnails(?![\w-])|(?<![\w-])tabbable(?![\w-])"""), Scope.TEMPLATE_CLASS, 3, "BS2에만 있는 컴포넌트"),
        // --- 4 단계(3→4)에서 사라진다: 패널과 float 도구 ---
        Legacy("panel", Regex("""(?<![\w-])panel(?:-[a-z]+)?(?![\w-])"""), Scope.TEMPLATE_CLASS, 4, "panel을 card로"),
        Legacy("well", Regex("""(?<![\w-])well(?:-[a-z]+)?(?![\w-])"""), Scope.TEMPLATE_CLASS, 4, "well을 card로"),
        Legacy("glyphicon", Regex("""(?<![\w-])glyphicon(?:-[a-z-]+)?(?![\w-])"""), Scope.TEMPLATE_CLASS, 4, "Glyphicons 대체"),
        Legacy("pull", Regex("""(?<![\w-])pull-(?:left|right)(?![\w-])"""), Scope.TEMPLATE_CLASS, 4, "float-left/right 또는 flex 도구로"),
        Legacy("col.xs", Regex("""(?<![\w-])col-xs-\d+(?![\w-])|(?<![\w-])hidden-(?:xs|sm|md|lg)(?![\w-])|(?<![\w-])img-responsive(?![\w-])"""), Scope.TEMPLATE_CLASS, 4, "BS3 그리드와 가시성 도구"),
        Legacy("btn.default", Regex("""(?<![\w-])btn-default(?![\w-])"""), Scope.TEMPLATE_CLASS, 4, "btn-secondary로"),
        // --- 5 단계(4→5)에서 사라진다: 속성과 jQuery 플러그인 호출 ---
        Legacy("attr.data-toggle", Regex("""\bdata-toggle="(?:tooltip|tab|dropdown|modal|popover|collapse|pill|button|buttons)"""), Scope.TEMPLATE_ATTR, 5, "data-bs-toggle로"),
        Legacy("attr.data-target", Regex("""\bdata-(?:target|dismiss|placement|slide|parent|backdrop|keyboard)="""), Scope.TEMPLATE_ATTR, 5, "data-bs-*로"),
        Legacy("js.plugin", Regex("""\.(?:modal|alert|popover|tooltip|tab|collapse|dropdown|scrollspy|affix|carousel|typeahead|button)\("""), Scope.JS, 5, "BS5 JS API로"),
        Legacy("util.margin", Regex("""(?<![\w-])(?:ml|mr|pl|pr)-(?:[0-5]|auto)(?![\w-])|(?<![\w-])float-(?:left|right)(?![\w-])|(?<![\w-])text-(?:left|right)(?![\w-])|(?<![\w-])badge-pill(?![\w-])|(?<![\w-])sr-only(?![\w-])"""), Scope.TEMPLATE_CLASS, 5, "ms/me/ps/pe, float-start/end 등으로"),
        Legacy("form.group", Regex("""(?<![\w-])form-group(?![\w-])|(?<![\w-])form-row(?![\w-])|(?<![\w-])custom-(?:control|select|file|range|checkbox|radio)(?![\w-])"""), Scope.TEMPLATE_CLASS, 5, "BS5 폼 구조로")
    )

    /** 끝났다고 선언한 단계이다. 선언한 단계 이하의 패턴은 0이어야 한다. 단계를 마칠 때 이 값을 올린다. */
    private val DONE_STAGES = 3

    private val mapper = JsonMapper.builder().build()
    private val baselineFile = File("src/test/resources/bs/legacy-baseline.json")

    private fun files(root: String, vararg extensions: String): List<File> =
        File(root).walkTopDown().filter { f -> f.isFile && extensions.any { f.name.endsWith(it) } }.toList()

    private val templateFiles by lazy { files("src/main/resources/templates", ".html") }
    private val jsFiles by lazy {
        files("src/main/resources/static/assets/javascripts", ".js").filter { !it.path.contains("/javascripts/lib/") && !it.name.endsWith(".min.js") }
    }
    private val lessFiles by lazy { files("app/assets/stylesheets", ".less") }

    /** 템플릿의 `class="..."` 값만 모은다(본문 글에 들어 있는 낱말을 세지 않는다). 타임리프의 `th:class`, `th:classappend`도 포함한다. */
    private fun classValues(text: String): List<String> =
        Regex("""\b(?:th:)?class(?:append)?\s*=\s*"([^"]*)"""").findAll(text).map { it.groupValues[1] }.toList()

    /** 템플릿 주석(`<!--/* ... */-->`와 `<!-- ... -->`)은 세지 않는다. */
    private fun withoutComments(text: String): String = text.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

    private fun count(p: Legacy): Int = when (p.scope) {
        Scope.TEMPLATE_CLASS -> templateFiles.sumOf { f -> classValues(withoutComments(f.readText())).sumOf { p.regex.findAll(it).count() } }
        Scope.TEMPLATE_ATTR -> templateFiles.sumOf { f -> p.regex.findAll(withoutComments(f.readText())).count() }
        Scope.JS -> jsFiles.sumOf { f -> p.regex.findAll(f.readText()).count() }
        Scope.LESS -> lessFiles.sumOf { f -> p.regex.findAll(f.readText()).count() }
    }

    @Test
    fun `낡은 표기의 수는 기준과 같고 끝난 단계의 패턴은 0이다`() {
        val current = patterns.associate { it.id to count(it) }
        if (System.getenv("UPDATE_BS_BASELINE") == "1") {
            baselineFile.parentFile.mkdirs()
            baselineFile.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(current.toSortedMap()) + "\n")
        }
        assertTrue(baselineFile.exists(), "기준 파일이 없다. UPDATE_BS_BASELINE=1로 한 번 실행해 만든다")
        val baseline: Map<String, Int> = mapper.readValue(baselineFile, mapper.typeFactory.constructMapType(Map::class.java, String::class.java, Int::class.javaObjectType))

        val grew = current.filter { (id, n) -> n > (baseline[id] ?: 0) }.map { (id, n) -> "$id: 기준 ${baseline[id] ?: 0}, 현재 $n (낡은 표기가 늘었다)" }
        val shrank = current.filter { (id, n) -> n < (baseline[id] ?: Int.MAX_VALUE) }.map { (id, n) -> "$id: 기준 ${baseline[id]}, 현재 $n (줄었다: UPDATE_BS_BASELINE=1로 기준을 새로 쓴다)" }
        assertEquals(emptyList(), grew + shrank, "낡은 표기의 수가 기준과 다르다")

        val remaining = patterns.filter { it.removedAt <= DONE_STAGES && (current[it.id] ?: 0) > 0 }.map { "${it.id}(${it.note}): ${current[it.id]}" }
        assertEquals(emptyList(), remaining, "끝났다고 선언한 단계(${DONE_STAGES})의 낡은 표기가 남아 있다")
    }
}
