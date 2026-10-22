package dev.yona.markdown

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * commonmark-java(v2)와 marked.js(v1.6)의 결과를 같은 입력으로 비교해 차이를 보고서로 남긴다(D-17).
 * 모든 입력의 결과가 같아야 통과한다. 차이가 생기면 `build/markdown-differences.txt`에 보고서를 남기고 실패한다.
 * 의도한 차이는 선생님의 승인을 받은 뒤 위키 `web/markdown.md`에 기록하고 해당 입력을 이 모음에서 분리한다.
 */
class MarkdownDifferenceTest {

    private val renderer = MarkdownRenderer(RenderedMarkdownCache())

    private val corpus = linkedMapOf(
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
        "코드 안 별표" to "`a*b*c`"
    )

    /** 속성 순서와 공백 차이는 무시하도록 속성을 정렬해 표준 형태로 만든다. */
    private fun normalize(html: String): String {
        val body = Jsoup.parseBodyFragment(html).body()
        body.allElements.forEach { el ->
            val sorted = el.attributes().asList().sortedBy { it.key }.map { it.key to it.value }
            sorted.forEach { el.removeAttr(it.first) }
            sorted.forEach { el.attr(it.first, it.second) }
        }
        return body.html().replace(Regex("\\s+"), " ").trim()
    }

    @Test
    fun `v1 6의 marked 결과와 같은 HTML을 만든다`() {
        val out = StringBuilder()
        var same = 0
        corpus.forEach { (name, md) ->
            val v2 = normalize(renderer.render(md))
            val v16 = normalize(renderer.sanitize(MarkedReference.render(md)))
            if (v2 == v16) {
                same++
            } else {
                out.append("## $name\n입력: ${md.replace("\n", "\\n")}\nv1.6: $v16\nv2  : $v2\n\n")
            }
        }
        val report = "동일 $same / 전체 ${corpus.size}\n\n$out"
        File("build/markdown-differences.txt").apply { parentFile.mkdirs() }.writeText(report)
        assertEquals(0, corpus.size - same, report)
    }
}
