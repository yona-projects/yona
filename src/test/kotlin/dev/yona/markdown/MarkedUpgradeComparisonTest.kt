package dev.yona.markdown

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory
import java.io.File
import java.util.Random
import javax.script.Invocable
import javax.script.ScriptEngine
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 서버의 marked(운영, [MarkedEngine])가 v1.6의 marked 1.2.9(`src/test/resources/marked.js`, 소스 기준 판)와 같은 HTML을 내는지 같은 입력으로 비교한다.
 * marked를 올릴 때(D-117 다음 단계, 선생님 승인: 4.3.0)의 안전망이다. 입력은 앞서 공식 v1.16.0과 비교하던 모음이다
 * (당시의 20개 입력, `tools/v16-diff/scenarios/md1.py`의 조각, `tools/v16-diff/mdfuzz.py`의 조각 조합)에 올림으로 달라질 수 있는 곳(제목 슬러그, 작업 목록, 제목 앵커)을 더했다.
 *
 * 달라진 입력은 `build/marked-upgrade-differences.txt`에 쓴다. 선생님이 항목별로 승인한 차이만 [APPROVED]에 이름으로 올려 통과시킨다.
 */
class MarkedUpgradeComparisonTest {

    private val production = MarkedEngine()

    /** v1.6 기준 marked(1.2.9 + Yona 패치)를 운영과 같은 옵션으로 실행하는 따로 만든 엔진. */
    private val baseline: ScriptEngine = NashornScriptEngineFactory().scriptEngine.also { e ->
        e.eval(MarkedEngine.ES5_SPLICE)
        e.eval(File("src/test/resources/marked.js").readText())
    }

    private fun baselineRender(source: String): String {
        if (source.isEmpty()) return source
        val options = baseline.eval("new Object({gfm: true, tables: true, breaks: true, headerIds: true, pedantic: false, sanitize: false, smartLists: true})")
        return try {
            (baseline as Invocable).invokeFunction("marked", source, options) as String
        } catch (e: javax.script.ScriptException) {
            // 운영 MarkedEngine과 같은 v1.6의 오류 처리: 원문을 이스케이프해 <pre>로 감싼다(Nashorn에 없는 Array.prototype.includes 등)
            "<pre>" + org.apache.commons.lang.StringEscapeUtils.escapeHtml(source) + "</pre>"
        }
    }

    /**
     * 이메일 주소 난독화(`mangle`)는 `Math.random()`으로 글자를 숫자 참조로 바꿔 실행마다 달라진다(v1.6부터). 숫자 참조를 글자로 되돌려 비교한다.
     */
    private fun unmangle(html: String): String = Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(html) {
        val v = it.groupValues[1]
        (if (v.startsWith("x")) v.substring(1).toInt(16) else v.toInt()).toChar().toString()
    }

    private val corpus: Map<String, String> by lazy {
        val m = linkedMapOf(
            "제목" to "# 제목1\n## 제목 둘\n### a b c",
            "강조" to "**굵게** *기울임* ~~취소~~ `코드`",
            "줄바꿈" to "첫 줄\n둘째 줄\n\n새 문단",
            "목록" to "- a\n- b\n  - c\n\n1. x\n2. y",
            "목록 바로 뒤 목록" to "- a\n- b\n1. x\n2. y",
            "작업 목록" to "- [x] 완료\n- [ ] 미완료",
            "표" to "| a | b |\n|---|:-:|\n| 1 | 2 |",
            "코드 블록" to "```kotlin\nval a = 1\n```",
            "들여쓴 코드" to "    indented code",
            "인용" to "> 인용\n> 두 줄",
            "링크" to "[문서](https://yona.io) 와 https://yona.io/auto",
            "이미지" to "![alt](/img.png)",
            "수평선" to "---",
            "원시 HTML" to "<b>bold</b> <script>alert(1)</script>",
            "자바스크립트 링크" to "[x](javascript:alert(1))",
            "한글 제목 앵커" to "# 한글 제목",
            "중복 제목" to "# a\n# a",
            "이스케이프" to "\\*not em\\* & < >",
            "번호 시작" to "3. c\n4. d",
            "코드 안 별표" to "`a*b*c`",
            // 올림으로 달라질 수 있는 곳(Yona 패치 5개의 자리와 해석이 바뀐 곳)
            "슬러그 일본어" to "# 日本語の見出し",
            "슬러그 키릴" to "# Привет мир",
            "슬러그 악센트" to "# café résumé",
            "슬러그 혼합" to "# 한글 English 123 _under_ & 기호!?",
            "슬러그 이모지" to "# 😀 웃음",
            "제목 중복 번호" to "# 같음\n# 같음\n# 같음-1\n# 같음",
            "제목 안 강조와 링크" to "## **굵은** [링크](http://a.b) `코드` 제목",
            "setext 제목" to "제목\n===\n\n둘째\n---",
            "작업 목록 중첩" to "- [ ] a\n  - [x] b\n    - [ ] c\n- [x] d",
            "작업 목록 느슨함" to "- [ ] a\n\n- [x] b\n\n  문단",
            "작업 목록 번호" to "1. [ ] a\n2. [x] b",
            "목록 안 코드와 인용" to "- a\n\n      코드\n\n  > 인용\n- b",
            "목록 시작 번호와 기호 바뀜" to "1. a\n1. b\n\n- c\n+ d\n* e",
            "중첩 강조" to "***굵고 기울임*** **굵게 *안* 기울임** _밑줄 __둘__ 끝_ a*b*c a_b_c",
            "줄바꿈 두 칸과 역슬래시" to "a  \nb\\\nc\n\nd",
            "표 가장자리" to "| a | b |\n|---|---|\n| 1 |\n| 1 | 2 | 3 |\n\n|x|\n|-|\n|y|",
            "표 정렬과 이스케이프" to "| 왼 | 가운데 | 오른 |\n|:--|:-:|--:|\n| a\\|b | `c|d` | **e** |",
            "참조 링크" to "[a][1] [b] [c][]\n\n[1]: http://a.b \"제목\"\n[b]: <http://c.d>\n[c]: /e",
            "자동 링크" to "http://a.b/c?d=e&f=g www.x.com <a@b.c> a@b.c https://x.y/(z)",
            "HTML 블록과 주석" to "<div>\n**안쪽**\n</div>\n\n<!-- 주석 -->\n\n<details><summary>s</summary>\n\n본문\n\n</details>",
            "엔티티" to "&amp; &lt; &#35; &copy; &unknown; AT&T",
            "펜스 코드 정보와 물결" to "~~~js title\nx\n~~~\n\n``` \ny\n```",
            "이미지 참조와 제목" to "![a][i] ![b](/x.png \"t\")\n\n[i]: /i.png",
            "수식처럼 보이는 글" to "a < b > c && d || e \$x\$ \\(y\\)",
            "빈 입력과 공백" to "   \n\n  ",
            "긴 줄" to "a".repeat(300) + "\n\n" + "한글 ".repeat(80),
        )
        val snippets = ObjectMapper().readTree(File("src/test/resources/markdown/v16-diff-snippets.json"))
        snippets.forEach { m["조각 ${it["name"].asText()}"] = it["body"].asText() }
        val tokens = listOf("# ", "## ", "### ", "- ", "* ", "+ ", "1. ", "2) ", "> ", ">> ", "    ", "\t", "```", "~~~", "`", "``", "**", "*", "__", "_", "~~", "[", "]", "(", ")", "![", "<", ">",
            "&", "&amp;", "|", "---", "***", "===", "\\", "\n", "\n\n", "  \n", "http://a.b/c", "www.x.com", "a@b.c", "#1", "@tester", "tester/proj1#1", "5559df3", "abc", "한글", "😀",
            " ", "  ", "<b>", "</b>", "<div>", "</div>", "<br>", "<!--", "-->", "<script>", "|a|b|", "|--|--|", ":smile:", "[x]", "[ ]", "<kbd>", "<a href='x'>", "</a>", "\"", "'", "%", "{", "}")
        val random = Random(20261110)
        for (i in 1..800) {
            val text = (1..(1 + random.nextInt(40))).joinToString("") { tokens[random.nextInt(tokens.size)] }
            m["무작위 $i"] = text
        }
        m
    }

    /**
     * 선생님이 승인한 차이(입력 이름 → 이유, 2026-11-10, D-118). 이름 있는 입력의 차이는 모두 여기에 이유와 함께 적는다.
     * 무작위 입력의 차이는 같은 원인(아래 [FUZZ_DIFFERENCES])이라 입력별로 적지 않고 건수로 지킨다.
     */
    private val APPROVED = mapOf(
        "작업 목록 느슨함" to "느슨한 작업 목록에서 체크박스 뒤 공백이 줄바꿈으로 바뀐다(화면은 같다)",
        "중첩 강조" to "`***x***`가 `<strong><em>`에서 CommonMark 순서 `<em><strong>`으로 바뀐다(화면은 같다)",
        "자동 링크" to "자동 링크의 `href`에서 `&`를 `&amp;`로 쓰지 않고(정화 뒤 같아진다), 맨몸 이메일(`a@b.c`)이 링크가 된다(GFM 확장)",
        "조각 강조" to "`***둘다***`의 `<strong><em>` 순서",
        "조각 링크" to "자동 링크의 `&` 표기와 맨몸 이메일의 링크화",
    )

    /**
     * 무작위 800건 중 다른 입력의 수(시드 고정). 원인은 CommonMark 0.30 준수와 GFM 자동 링크 확장이다(맨몸 이메일의 링크화, 인라인 HTML 판정, 닫히지 않은 펜스 코드, 강조 경계,
     * 목록 안 HTML 블록의 끝, 탭 보존, 끝 공백 제거). marked나 `marked-yona.js`를 바꾸면 이 수가 달라질 수 있으므로 달라지면 차이를 다시 훑어 승인받는다.
     */
    private val FUZZ_DIFFERENCES = 352

    @Test
    fun `서버의 marked는 4_3_0이다`() {
        val head = MarkedEngine::class.java.getResourceAsStream("/markdown/marked.js")!!.readBytes().decodeToString().take(300)
        assertTrue(head.contains("marked v4.3.0"), head)
    }

    @Test
    fun `운영 marked의 결과가 v1_6 기준과 같다(승인한 차이만 다르다)`() {
        val different = linkedMapOf<String, Triple<String, String, String>>()
        corpus.forEach { (name, md) ->
            val a = unmangle(baselineRender(md))
            val b = unmangle(production.render(md))
            if (a != b) different[name] = Triple(md, a, b)
        }
        val report = StringBuilder("입력 ${corpus.size}건 중 다른 것 ${different.size}건\n\n")
        different.forEach { (name, t) -> report.append("--- $name\n입력: ${t.first.take(200)}\n기준: ${t.second.take(400)}\n운영: ${t.third.take(400)}\n\n") }
        File("build").mkdirs()
        File("build/marked-upgrade-differences.txt").writeText(report.toString())
        val named = different.filterKeys { !it.startsWith("무작위 ") }
        assertEquals(emptyList<String>(), (named.keys - APPROVED.keys).toList(), "승인되지 않은 차이, build/marked-upgrade-differences.txt를 보십시오")
        assertEquals(FUZZ_DIFFERENCES, different.keys.count { it.startsWith("무작위 ") }, "무작위 입력의 차이 수가 달라졌다. 새 차이를 훑어 승인받으십시오")
    }

    @Test
    fun `승인한 차이가 가리키는 입력은 모두 모음에 있다`() {
        assertEquals(emptyList<String>(), (APPROVED.keys - corpus.keys).toList())
    }
}
