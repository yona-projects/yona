package dev.yona.domain.notification

import dev.yona.util.diff_match_patch
import org.springframework.web.util.HtmlUtils

/**
 * 본문이 바뀌었다는 알림 메일에 넣는 변경 차이 표시이다. v1.6 `utils.DiffUtil`(`app/utils/DiffUtil.java`)에 해당한다.
 *
 * 이슈 변경 이력(`IssueHistory`)과 달리 메일에는 인라인 스타일이 필요하고, 일반 텍스트 메일에는 `---`와 `+++` 접두어를 쓴다.
 * 같은 부분이 100자를 넘으면 앞뒤 50자만 남기고 말줄임표로 줄인다. 일반 텍스트에서도 v1.6처럼 HTML 이스케이프를 적용한다.
 */
object MailDiff {

    private const val EQUAL_TEXT_ELLIPSIS_SIZE = 100
    private const val EQUAL_TEXT_BASE_SIZE = 50
    private const val DIFF_EDIT_COST: Short = 8

    private const val DELETE_STYLE = "<span style='background-color: #fda9a6;padding: 2px 0;'>"
    private const val INSERT_STYLE = "<span style='background-color: #abdd52;padding: 2px 0;'>"
    private const val HTML_ELLIPSIS = "<span style='color: #bdbdbd;font-size: 16px;font-family: serif;'>...\n......\n......\n...</span>"
    private const val TEXT_ELLIPSIS = "......\n......\n...\n"

    /** [oldValue]에서 [newValue]로 바뀐 차이를 HTML로 만든다 (v1.6 `getDiffText`). 줄바꿈은 `&nbsp<br/>`로 바꾼다. */
    fun html(oldValue: String?, newValue: String?): String {
        val sb = StringBuilder()
        for (diff in diffs(oldValue, newValue)) {
            when (diff.operation) {
                diff_match_patch.Operation.DELETE -> sb.append(DELETE_STYLE).append(escape(diff.text)).append("</span>")
                diff_match_patch.Operation.INSERT -> sb.append(INSERT_STYLE).append(escape(diff.text)).append("</span>")
                diff_match_patch.Operation.EQUAL ->
                    if (diff.text.length > EQUAL_TEXT_ELLIPSIS_SIZE) sb.append(head(diff.text)).append(HTML_ELLIPSIS).append(tail(diff.text))
                    else sb.append(escape(diff.text))
                else -> Unit
            }
        }
        return sb.toString().replace("\n", "&nbsp<br/>\n")
    }

    /** [oldValue]에서 [newValue]로 바뀐 차이를 일반 텍스트로 만든다 (v1.6 `getDiffPlainText`). */
    fun plain(oldValue: String?, newValue: String?): String {
        val sb = StringBuilder()
        for (diff in diffs(oldValue, newValue)) {
            when (diff.operation) {
                diff_match_patch.Operation.DELETE -> sb.append("--- ").append(escape(diff.text)).append("\n")
                diff_match_patch.Operation.INSERT -> sb.append("+++ ").append(escape(diff.text)).append("\n")
                diff_match_patch.Operation.EQUAL -> {
                    if (diff.text.length > EQUAL_TEXT_ELLIPSIS_SIZE) sb.append(head(diff.text)).append(TEXT_ELLIPSIS).append(tail(diff.text))
                    else sb.append(escape(diff.text))
                    sb.append("\n")
                }
                else -> Unit
            }
        }
        return sb.toString()
    }

    private fun diffs(oldValue: String?, newValue: String?): List<diff_match_patch.Diff> {
        val dmp = diff_match_patch().also { it.Diff_EditCost = DIFF_EDIT_COST }
        return dmp.diff_main(oldValue.orEmpty(), newValue.orEmpty()).also { dmp.diff_cleanupEfficiency(it) }
    }

    private fun head(text: String) = escape(text.substring(0, EQUAL_TEXT_BASE_SIZE))

    private fun tail(text: String) = escape(text.substring(text.length - EQUAL_TEXT_BASE_SIZE))

    private fun escape(text: String) = HtmlUtils.htmlEscape(text)
}
