package dev.yona.web.code

import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.enumeration.Side
import dev.yona.domain.project.Project
import dev.yona.repository.diff.DiffLine
import dev.yona.repository.diff.DiffLineType
import dev.yona.repository.diff.DiffService
import dev.yona.repository.diff.FileDiff
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RawText
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

/**
 * 파일별 차이를 화면용 HTML로 그린다. v1.6 `partial_diff`, `partial_filediff`, `partial_diff_line`, `partial_diff_comment_on_line`과
 * `TemplateHelper.DiffRenderer`(`app/views/partial_diff.scala.html` 외)에 해당한다.
 *
 * v1.6도 줄 단위 HTML을 문자열로 이어 붙였다. 줄 사이에 끼는 코드 리뷰 스레드는 호출하는 쪽이 준 [ThreadRenderer]가 그린다.
 * 같은 줄에서 지우고 더한 쌍을 낱말 단위로 비교하는 기능은 v1.6도 만들지 않았다("Not implemented yet"). 줄 내용은 이스케이프한다.
 */
@Component
class DiffHtmlRenderer(private val messages: MessageSource, private val siteUrls: SiteUrls) {

    /** 스레드 여러 개를 이어 그린 HTML을 돌려준다. */
    fun interface ThreadRenderer {
        fun render(threads: List<CodeCommentThread>): String
    }

    /**
     * [diffs]를 파일마다 그린다.
     *
     * @param threads 코드 범위에 달린 스레드. 없으면 빈 목록이다
     * @param projectA 바뀌기 전 쪽의 프로젝트(파일 링크에 쓴다)
     * @param projectB 바뀐 뒤 쪽의 프로젝트
     */
    fun render(diffs: List<FileDiff>, threads: List<CodeCommentThread>, projectA: Project, projectB: Project, threadRenderer: ThreadRenderer): String {
        val byLine = threads.filter { it.path != null }.groupBy { threadKey(it.path, it.endSide, it.endLine) }
        val out = StringBuilder()
        if (diffs.size >= DiffService.DIFF_FILE_LIMIT) {
            out.append("<p class=\"alert\">").append(text("code.fileDiffLimitExceeded", DiffService.DIFF_FILE_LIMIT)).append("</p>\n")
        }
        diffs.forEach { out.append(file(it, byLine, projectA, projectB, threadRenderer)) }
        return out.toString()
    }

    private fun text(key: String, vararg args: Any): String = messages.getMessage(key, args, LocaleContextHolder.getLocale())

    private fun esc(value: String?) = HtmlUtils.htmlEscape(value.orEmpty())

    private fun threadKey(path: String?, side: Side?, line: Int?) = "$path:$side:$line"

    private fun lineThreads(map: Map<String, List<CodeCommentThread>>, path: String?, side: Side, line: Int) = map[threadKey(path, side, line)].orEmpty()

    // --- 파일 ---

    private fun file(diff: FileDiff, threads: Map<String, List<CodeCommentThread>>, projectA: Project, projectB: Project, renderer: ThreadRenderer): String {
        val binA = diff.isBinaryA
        val binB = diff.isBinaryB
        fun header(type: DiffEntry.ChangeType) = fileHeader(type, diff)
        return when (diff.changeType) {
            DiffEntry.ChangeType.MODIFY -> when {
                binA && binB -> frame(diff, header(DiffEntry.ChangeType.MODIFY), codeIsBinary("", ""), projectA, projectB)
                binA -> frame(diff, header(DiffEntry.ChangeType.MODIFY), addedLines(diff, threads, true, renderer), projectA, projectB)
                binB -> frame(diff, header(DiffEntry.ChangeType.MODIFY), removedLines(diff, threads, true, renderer), projectA, projectB)
                else -> frame(diff, diff.pathB, diffLines(diff, threads, renderer), projectA, projectB)
            }
            DiffEntry.ChangeType.ADD ->
                frame(diff, header(DiffEntry.ChangeType.ADD), if (binB) codeIsBinary("", "") else addedLines(diff, threads, false, renderer), projectA, projectB)
            DiffEntry.ChangeType.DELETE ->
                if (binA) frame(diff, header(DiffEntry.ChangeType.DELETE), codeIsBinary("", ""), projectA, projectB)
                else frame(diff, text("code.deletedPath", diff.pathA.orEmpty()), removedLines(diff, threads, false, renderer), projectA, projectB)
            DiffEntry.ChangeType.RENAME -> when {
                binA && binB -> frame(diff, header(DiffEntry.ChangeType.RENAME), codeIsBinary("", ""), projectA, projectB)
                binA -> frame(diff, header(DiffEntry.ChangeType.RENAME), addedLines(diff, threads, true, renderer), projectA, projectB)
                binB -> frame(diff, header(DiffEntry.ChangeType.RENAME), removedLines(diff, threads, true, renderer), projectA, projectB)
                else -> frame(diff, text("code.renamedPath", diff.pathA.orEmpty(), diff.pathB.orEmpty()), diffLines(diff, threads, renderer), projectA, projectB)
            }
            DiffEntry.ChangeType.COPY ->
                if (binB) frame(diff, header(DiffEntry.ChangeType.COPY), codeIsBinary("", ""), projectA, projectB)
                else frame(diff, text("code.copiedPath", diff.pathA.orEmpty(), diff.pathB.orEmpty()), addedLines(diff, threads, false, renderer), projectA, projectB)
            else -> ""
        }
    }

    private fun postfixed(path: String?, binary: Boolean) = path.orEmpty() + if (binary) " (Binary)" else ""

    private fun fileHeader(type: DiffEntry.ChangeType, diff: FileDiff): String = when (type) {
        DiffEntry.ChangeType.MODIFY -> postfixed(diff.pathB, diff.isBinaryB)
        DiffEntry.ChangeType.ADD -> text("code.addedPath", postfixed(diff.pathB, diff.isBinaryB))
        DiffEntry.ChangeType.DELETE -> text("code.deletedPath", postfixed(diff.pathA, diff.isBinaryA))
        DiffEntry.ChangeType.RENAME -> text("code.renamedPath", postfixed(diff.pathA, diff.isBinaryA), postfixed(diff.pathB, diff.isBinaryB))
        DiffEntry.ChangeType.COPY -> text("code.copiedPath", postfixed(diff.pathA, diff.isBinaryA), postfixed(diff.pathB, diff.isBinaryB))
        else -> ""
    }

    private fun shortId(commit: String) = if (commit.length < 7) commit else commit.substring(0, 7)

    private fun commitLink(project: Project, commit: String?, path: String?): String {
        if (commit == null || path == null) return "&nbsp;"
        val href = siteUrls.path("/${project.owner}/${project.name}/code/${UrlPaths.encode(commit)}/${UrlPaths.encodeEach(path)}")
        return "<a href=\"${esc(href)}\" title=\"${esc(commit)}\" target=\"_blank\">${esc(shortId(commit))}</a>"
    }

    private fun frame(diff: FileDiff, header: String?, rows: String, projectA: Project, projectB: Project): String {
        val headId = (diff.pathB ?: diff.pathA).orEmpty().replace("/", "-").replace(".", "-")
        val filePath = diff.pathB?.ifEmpty { null } ?: diff.pathA
        val out = StringBuilder()
        out.append("<div id=\"").append(esc(headId)).append("\" class=\"diff-partial-outer\">\n<div class=\"diff-partial-inner\">\n<div class=\"diff-partial-meta\">\n<div class=\"diff-partial-commit\">\n")
        out.append("<div class=\"diff-partial-commit-id\">").append(commitLink(projectA, diff.commitA, diff.pathA)).append("</div>\n")
        out.append("<div class=\"diff-partial-commit-id\">").append(commitLink(projectB, diff.commitB, diff.pathB)).append("</div>\n")
        out.append("</div>\n<div class=\"diff-partial-file\"><span class=\"filename\">").append(esc(header)).append("</span></div>\n</div>\n")
        out.append("<div class=\"diff-partial-code\" data-hashcode=\"").append(diff.hashCode()).append("\">\n<div class=\"patch-header\">\n")
        if (!diff.pathA.isNullOrEmpty()) out.append("<div class=\"path\">--- ").append(esc(diff.pathA)).append("</div>\n")
        if (!diff.pathB.isNullOrEmpty()) out.append("<div class=\"path\">+++ ").append(esc(diff.pathB)).append("</div>\n")
        out.append("</div>\n<table class=\"diff-container show-comments\" data-path-a=\"").append(esc(diff.pathA)).append("\" data-path-b=\"").append(esc(diff.pathB))
        out.append("\" data-commit-a=\"").append(esc(diff.commitA)).append("\" data-commit-b=\"").append(esc(diff.commitB))
        out.append("\" data-file-path=\"").append(esc(filePath)).append("\">\n<tbody>\n").append(rows).append("</tbody>\n</table>\n</div>\n</div>\n</div>\n")
        return out.toString()
    }

    // --- 줄 ---

    private fun message(key: String) = "<tr><td colspan=3>${text(key)}</td></tr>\n"

    private fun rangeRow(hunk: String) =
        "<tr class=\"range\"><td class=\"linenum\"><div class=\"line-number\" data-line-num=\"...\"><span class=\"hidden\">...</span></div></td>" +
            "<td class=\"linenum\"><div class=\"line-number\" data-line-num=\"...\"><span class=\"hidden\">...</span></div></td><td class=\"hunk\">$hunk</td></tr>\n"

    private fun codeIsBinary(klass: String, prefix: String) =
        "<tr class=\"$klass\"><td class='linenum'><div class='line-number'></div></td><td class='linenum'><div class='line-number'></div></td><td class=\"isBinary\">$prefix${esc(text("code.isBinary"))}</td></tr>\n"

    private fun diffLines(diff: FileDiff, threads: Map<String, List<CodeCommentThread>>, renderer: ThreadRenderer): String {
        val out = StringBuilder()
        if (diff.isFileModeChanged()) {
            val old = diff.oldMode.toString(); val new = diff.newMode.toString()
            out.append("<tr><td class=\"linenum\"><div class=\"line-number\" data-line-num=\"$old\"></div><span class=\"hidden\">$old</span></td><td class=\"linenum\"><div class=\"line-number\" data-line-num=\"$new\"></div><span class=\"hidden\">$new</span></td><td class=\"isBinary\">${text("code.fileModeChanged")}</td></tr>\n")
        }
        when {
            diff.hasError(FileDiff.Error.OTHERS_SIZE_EXCEEDED) -> out.append(message("code.skipDiff"))
            diff.hasAnyError(FileDiff.Error.A_SIZE_EXCEEDED, FileDiff.Error.B_SIZE_EXCEEDED) -> out.append(message("code.tooBigFile"))
            diff.hasError(FileDiff.Error.DIFF_SIZE_EXCEEDED) -> out.append(message("code.tooBigDiff"))
            diff.hasError() -> out.append(message("code.unknownError"))
            else -> {
                val hunks = diff.hunks()
                if (hunks == null || hunks.isEmpty()) {
                    if (!diff.isFileModeChanged()) out.append(message("code.noChanges"))
                } else {
                    for (hunk in hunks) {
                        out.append(rangeRow("@@ -${hunk.beginA + 1},${hunk.endA - hunk.beginA} +${hunk.beginB + 1},${hunk.endB - hunk.beginB} @@"))
                        hunk.lines.forEach { out.append(line(it, threads, renderer, eolMissing(diff, it))) }
                    }
                }
            }
        }
        return out.toString()
    }

    private fun eolMissing(diff: FileDiff, line: DiffLine): Boolean = when (line.kind) {
        DiffLineType.REMOVE -> diff.a?.let { (line.numA!! + 1) == it.size() && it.isMissingNewlineAtEnd } == true
        else -> diff.b?.let { (line.numB!! + 1) == it.size() && it.isMissingNewlineAtEnd } == true
    }

    private fun noNewlineAtEof() = "<span style='color: red'>(${text("code.eolMissing")})</span>"

    private fun line(line: DiffLine, threads: Map<String, List<CodeCommentThread>>, renderer: ThreadRenderer, eolMissing: Boolean): String {
        val (num, numA, numB, found) = when (line.kind) {
            DiffLineType.ADD -> LineNumbers(line.numB!! + 1, null, line.numB + 1, lineThreads(threads, line.file.pathB, Side.B, line.numB + 1))
            DiffLineType.REMOVE -> LineNumbers(line.numA!! + 1, line.numA + 1, null, lineThreads(threads, line.file.pathA, Side.A, line.numA + 1))
            DiffLineType.CONTEXT -> LineNumbers(line.numB!! + 1, line.numA!! + 1, line.numB + 1, lineThreads(threads, line.file.pathB, Side.B, line.numB + 1))
        }
        val klass = line.kind.name.lowercase()
        val prefix = when (line.kind) { DiffLineType.ADD -> "+"; DiffLineType.REMOVE -> "-"; else -> " " }
        val side = if (klass == "remove") "A" else "B"
        return "<tr class=\"$klass\" data-line=\"$num\" data-type=\"$klass\" data-side=\"$side\">\n" +
            "<td class=\"linenum\"><i class=\"yobicon-comments\"></i><div class=\"line-number\" data-line-num=\"${numA ?: ""}\"></div><span class=\"hidden\">${numA ?: ""}</span></td>\n" +
            "<td class=\"linenum\"><div class=\"line-number\" data-line-num=\"${numB ?: ""}\"></div><span class=\"hidden\">${numB ?: ""}</span></td>\n" +
            "<td class=\"code\"><pre class=\"diff-partial-codeline\">$prefix${esc(line.content)}</pre>${if (eolMissing) noNewlineAtEof() else ""}</td>\n</tr>\n" +
            threadRows(found, renderer)
    }

    private data class LineNumbers(val num: Int, val numA: Int?, val numB: Int?, val threads: List<CodeCommentThread>)

    private fun threadRows(threads: List<CodeCommentThread>, renderer: ThreadRenderer): String =
        if (threads.isEmpty()) "" else "<tr class=\"comments board-comment-wrap\" data-commit-id=\"${esc(threads[0].commitId)}\">\n<td colspan=\"3\">\n${renderer.render(threads)}</td>\n</tr>\n"

    private fun rawFile(klass: String, path: String?, raw: RawText, side: Side, prefix: String, threads: Map<String, List<CodeCommentThread>>, renderer: ThreadRenderer): String {
        val out = StringBuilder()
        for (i in 0 until raw.size()) {
            out.append("<tr class='$klass' data-line='${i + 1}' data-type='$klass' data-side='${if (klass == "remove") "A" else "B"}'><td class='linenum'><i class='yobicon-comments'></i><div class='line-number'></div></td>")
            out.append("<td class='linenum'><div class='line-number' data-line-num='${i + 1}'></div><span class='hidden'>${i + 1}</span></td><td class='code'><pre class='diff-partial-codeline'>")
            out.append(prefix).append(esc(raw.getString(i)))
            if (i + 1 == raw.size() && raw.isMissingNewlineAtEnd) out.append(noNewlineAtEof())
            out.append("</pre></td></tr>")
            out.append(threadRows(lineThreads(threads, path, side, i + 1), renderer).trim())
        }
        return out.toString() + "\n"
    }

    private fun addedLines(diff: FileDiff, threads: Map<String, List<CodeCommentThread>>, binaryOverwritten: Boolean, renderer: ThreadRenderer): String = when {
        diff.hasError(FileDiff.Error.OTHERS_SIZE_EXCEEDED) -> message("code.skipDiff")
        diff.hasError(FileDiff.Error.B_SIZE_EXCEEDED) -> message("code.tooBigFile")
        diff.hasError() -> message("code.unknownError")
        else -> rangeRow("@@ -0,0 +1,${diff.b?.size() ?: 0} @@") + (if (binaryOverwritten) codeIsBinary("remove", "-") else "") +
            rawFile("add", diff.pathB, requireNotNull(diff.b), Side.B, "+", threads, renderer)
    }

    private fun removedLines(diff: FileDiff, threads: Map<String, List<CodeCommentThread>>, overwrittenByBinary: Boolean, renderer: ThreadRenderer): String = when {
        diff.hasError(FileDiff.Error.OTHERS_SIZE_EXCEEDED) -> message("code.skipDiff")
        diff.hasError(FileDiff.Error.A_SIZE_EXCEEDED) -> message("code.tooBigFile")
        diff.hasError() -> message("code.unknownError")
        else -> rangeRow("@@ -1,${diff.a?.size() ?: 0} +0,0 @@") + rawFile("remove", diff.pathA, requireNotNull(diff.a), Side.A, "-", threads, renderer) + (if (overwrittenByBinary) codeIsBinary("add", "+") else "")
    }
}
