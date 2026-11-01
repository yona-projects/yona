package dev.yona.domain.wiki

import dev.yona.util.UrlPaths

/**
 * 위키 마크다운의 링크 변환이다(로드맵 X-17, `decisions.md` D-61).
 *
 * 1. [expand]는 GitHub와 Forgejo의 위키 링크 문법을 표준 마크다운으로 푼다: `[[페이지]]`, `[[글자|페이지]]`, 그림 `[[그림.png]]`, `[[설명|그림.png]]`.
 *    없는 페이지로 가는 링크는 새 페이지 만들기(`_new?title=`)로 보낸다. 코드 블록과 인라인 코드 안은 바꾸지 않는다.
 * 2. [rewriteHtml]은 렌더링된 HTML의 상대 링크를 위키 주소로 바꾼다. 위키 첫 화면은 `/wiki`라서 `[글](Install-Guide)` 같은 상대 링크가 그대로 있으면
 *    `/wiki/Install-Guide`가 아니라 `/Install-Guide`로 풀린다. `uploads/` 아래는 첨부 파일 주소(`_uploads`)로 바꾼다. 마크다운 원문은 clone한 위키에서도
 *    그대로 읽히도록 상대 경로를 유지한다.
 */
object WikiLinks {

    private val WIKI_LINK = Regex("""\[\[([^\n]+?)]]""")
    private val CODE_SPAN = Regex("""(`+).+?\1""")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")
    private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""")
    private val ABSOLUTE_OR_SPECIAL = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*:|//|/|#|\?)""")
    private val ANCHOR = Regex("""(<a\b[^>]*?\bhref=")([^"]*)(")""")
    private val IMAGE = Regex("""(<img\b[^>]*?\bsrc=")([^"]*)(")""")

    /** [text]의 `[[...]]`를 마크다운 링크로 푼다. [base]는 위키 주소(`/소유자/이름/wiki`), [exists]는 그 이름의 페이지가 있는지이다. */
    fun expand(text: String, base: String, exists: (String) -> Boolean): String {
        var fence: String? = null
        return text.split("\n").joinToString("\n") { line ->
            val trimmed = line.trimStart()
            val marker = if (trimmed.startsWith("```")) "```" else if (trimmed.startsWith("~~~")) "~~~" else null
            when {
                fence != null -> { if (marker == fence) fence = null; line }
                marker != null -> { fence = marker; line }
                else -> expandLine(line, base, exists)
            }
        }
    }

    private fun expandLine(line: String, base: String, exists: (String) -> Boolean): String {
        if (!line.contains("[[")) return line
        val result = StringBuilder()
        var last = 0
        for (span in CODE_SPAN.findAll(line)) {
            result.append(replaceLinks(line.substring(last, span.range.first), base, exists)).append(span.value)
            last = span.range.last + 1
        }
        return result.append(replaceLinks(line.substring(last), base, exists)).toString()
    }

    private fun replaceLinks(text: String, base: String, exists: (String) -> Boolean): String =
        WIKI_LINK.replace(text) { match -> convert(match.groupValues[1], base, exists) ?: match.value }

    /** `[[...]]` 안쪽 글을 마크다운 링크로 바꾼다. 올바르지 않으면 `null`이다(원문을 그대로 둔다). */
    private fun convert(inner: String, base: String, exists: (String) -> Boolean): String? {
        val bar = inner.indexOf('|')
        val label = if (bar >= 0) inner.substring(0, bar).trim() else null
        val target = (if (bar >= 0) inner.substring(bar + 1) else inner).trim()
        if (target.isEmpty()) return null
        val text = (label?.takeIf { it.isNotEmpty() } ?: target).replace("]", "\\]")
        val extension = target.substringAfterLast('.', "").lowercase()
        if (extension in IMAGE_EXTENSIONS) return "![$text](${imageUrl(target)})"
        if (SCHEME.containsMatchIn(target)) return "[$text]($target)"
        val name = try {
            WikiService.nameOf(target)
        } catch (e: WikiTitleInvalidException) {
            return null
        }
        return if (exists(name)) "[$text]($base/${UrlPaths.encode(name)})" else "[$text]($base/_new?title=${UrlPaths.encode(target)})"
    }

    private fun imageUrl(target: String): String {
        if (SCHEME.containsMatchIn(target) || target.startsWith("/") || target.startsWith("//")) return target
        val path = if (target.startsWith("uploads/")) target else "uploads/$target"
        return path.split("/").joinToString("/") { UrlPaths.encode(it) }
    }

    /** 렌더링된 [html]의 상대 링크와 `uploads/` 이미지를 위키 주소로 바꾼다. 절대 주소, `//`, 앵커, 질의, `mailto:`는 그대로 둔다. */
    fun rewriteHtml(html: String, base: String): String {
        val anchors = ANCHOR.replace(html) { m -> m.groupValues[1] + target(m.groupValues[2], base, image = false) + m.groupValues[3] }
        return IMAGE.replace(anchors) { m -> m.groupValues[1] + target(m.groupValues[2], base, image = true) + m.groupValues[3] }
    }

    private fun target(value: String, base: String, image: Boolean): String = when {
        value.isEmpty() || ABSOLUTE_OR_SPECIAL.containsMatchIn(value) -> value
        value.startsWith("uploads/") -> "$base/_uploads/${value.removePrefix("uploads/")}"
        image -> value
        else -> "$base/$value"
    }
}
