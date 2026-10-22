package dev.yona.domain.issue

import dev.yona.domain.user.User
import dev.yona.util.diff_match_patch
import org.springframework.web.util.HtmlUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 글 본문의 변경 이력(HTML)을 만든다. v1.6 `AbstractPostingApp.addToHistory`, `getHistoryMadeBy`, `getDiffText`
 * (`app/controllers/AbstractPostingApp.java:133-210`)에 해당한다.
 *
 * 이력은 수정한 사람과 시각, 추가(+)와 삭제(-)된 조각 수를 적은 머리와 바뀐 본문의 차이(삭제는 `diff-deleted`, 추가는 `diff-added`,
 * 100자를 넘게 같은 부분은 앞뒤 50자만 남기고 `diff-ellipsis`)로 이루어지고 줄바꿈은 `</br>`로 바꾼다. 새 이력은 이전 이력 앞에 쌓인다.
 * 본문은 모두 이스케이프한다.
 */
object IssueHistory {

    private const val EDIT_COST: Short = 16
    private const val EQUAL_TEXT_ELLIPSIS_SIZE = 100
    private const val EDGE_SIZE = 50

    /**
     * 이전 본문 [oldBody]에서 [newBody]로 바뀐 이력 한 건을 만든다.
     *
     * @param editor 수정한 사람
     * @param at 수정한 시각
     */
    fun entry(oldBody: String, newBody: String, editor: User, at: Date): String {
        val diffs = diffs(oldBody, newBody)
        val insertions = diffs.count { it.operation == diff_match_patch.Operation.INSERT }
        val deletions = diffs.count { it.operation == diff_match_patch.Operation.DELETE }
        val madeBy = StringBuilder("<div class='history-made-by'>").append(editor.name).append("(").append(editor.loginId).append(") ")
        if (insertions > 0) madeBy.append("<span class='added'> ").append(" + ").append(insertions).append(" </span>")
        if (deletions > 0) madeBy.append("<span class='deleted'> ").append(" - ").append(deletions).append(" </span>")
        madeBy.append(" at ").append(SimpleDateFormat("yyyy-MM-dd h:mm:ss a", Locale.getDefault()).format(at)).append("</div><hr/>\n")
        return (madeBy.toString() + diffText(oldBody, newBody) + "\n").replace("\n", "</br>\n")
    }

    private fun diffs(oldText: String, newText: String): List<diff_match_patch.Diff> {
        val dmp = diff_match_patch().also { it.Diff_EditCost = EDIT_COST }
        return dmp.diff_main(oldText, newText).also { dmp.diff_cleanupEfficiency(it) }
    }

    private fun diffText(oldValue: String, newValue: String): String {
        val sb = StringBuilder()
        for (diff in diffs(oldValue, newValue)) {
            when (diff.operation) {
                diff_match_patch.Operation.DELETE -> sb.append("<span class='diff-deleted'>").append(esc(diff.text)).append("</span>")
                diff_match_patch.Operation.INSERT -> sb.append("<span class='diff-added'>").append(esc(diff.text)).append("</span>")
                else -> {
                    val length = diff.text.length
                    if (length > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        if (diff.text.substring(0, EDGE_SIZE) != oldValue.substring(0, EDGE_SIZE)) sb.append(esc(diff.text.substring(0, EDGE_SIZE)))
                        sb.append("<span class='diff-ellipsis'>...\n......\n......\n...</span>")
                        if (diff.text.substring(length - EDGE_SIZE) != oldValue.substring(oldValue.length - EDGE_SIZE)) sb.append(esc(diff.text.substring(length - EDGE_SIZE)))
                    } else {
                        sb.append(esc(diff.text))
                    }
                }
            }
        }
        return sb.toString().replace("\n", "&nbsp<br/>\n")
    }

    /** v1.6 `StringEscapeUtils.escapeHtml4`와 같이 `& < > "`를 이스케이프한다. */
    private fun esc(text: String): String = HtmlUtils.htmlEscape(text).replace("&#39;", "'")
}
