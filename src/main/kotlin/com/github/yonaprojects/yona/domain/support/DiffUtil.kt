package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.util.diff_match_patch
import org.apache.commons.lang3.StringEscapeUtils

/**
 * legacy utils/DiffUtil.java 대응. 핵심 diff 알고리즘 자체(Myers diff + 효율성 정리)는
 * 원본 그대로인 `com.github.yonaprojects.yona.util.diff_match_patch`(Google diff-match-patch,
 * `utils/diff_match_patch.java`를 패키지 선언만 바꿔 그대로 옮김)에 위임하고, 이 클래스는 그 결과를
 * HTML/plain text로 렌더링하는 원본 `DiffUtil.java`의 로직을 그대로 포팅한다.
 */
object DiffUtil {
    const val EQUAL_TEXT_ELLIPSIS_SIZE = 100
    const val EQUAL_TEXT_BASE_SIZE = 50
    const val DIFF_EDITCOST: Short = 8

    fun getDiffText(oldValue: String?, newValue: String?): String {
        val oldVal = oldValue ?: ""
        val newVal = newValue ?: ""

        val dmp = diff_match_patch()
        dmp.Diff_EditCost = DIFF_EDITCOST
        val sb = StringBuilder()

        val diffs = dmp.diff_main(oldVal, newVal)
        dmp.diff_cleanupEfficiency(diffs)

        for (diff in diffs) {
            when (diff.operation) {
                diff_match_patch.Operation.DELETE -> {
                    val deleteStyle = "<span style='background-color: #fda9a6;padding: 2px 0;'>"
                    sb.append(addDiffStyle(diff, deleteStyle))
                }
                diff_match_patch.Operation.EQUAL -> {
                    val textLength = diff.text.length
                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        sb.append(addHeadOfDiff(diff))
                        sb.append(addEllipsis())
                        sb.append(addTailOfDiff(diff))
                    } else {
                        sb.append(addAllDiff(diff))
                    }
                }
                diff_match_patch.Operation.INSERT -> {
                    val insertStyle = "<span style='background-color: #abdd52;padding: 2px 0;'>"
                    sb.append(addDiffStyle(diff, insertStyle))
                }
                else -> {}
            }
        }

        return sb.toString().replace("\n", "&nbsp<br/>\n")
    }

    fun getDiffPlainText(oldValue: String?, newValue: String?): String {
        val oldVal = oldValue ?: ""
        val newVal = newValue ?: ""

        val dmp = diff_match_patch()
        dmp.Diff_EditCost = DIFF_EDITCOST
        val sb = StringBuilder()

        val diffs = dmp.diff_main(oldVal, newVal)
        dmp.diff_cleanupEfficiency(diffs)

        for (diff in diffs) {
            when (diff.operation) {
                diff_match_patch.Operation.DELETE -> {
                    sb.append(addDiffText(diff, "--- "))
                }
                diff_match_patch.Operation.EQUAL -> {
                    val textLength = diff.text.length
                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        sb.append(addHeadOfDiff(diff))
                        sb.append(addEllipsisText())
                        sb.append(addTailOfDiff(diff))
                    } else {
                        sb.append(addAllDiff(diff))
                    }
                    sb.append("\n")
                }
                diff_match_patch.Operation.INSERT -> {
                    sb.append(addDiffText(diff, "+++ "))
                }
                else -> {}
            }
        }

        return sb.toString()
    }

    private fun addHeadOfDiff(diff: diff_match_patch.Diff): String {
        return StringEscapeUtils.escapeHtml4(diff.text.substring(0, EQUAL_TEXT_BASE_SIZE))
    }

    private fun addTailOfDiff(diff: diff_match_patch.Diff): String {
        return StringEscapeUtils.escapeHtml4(diff.text.substring(diff.text.length - EQUAL_TEXT_BASE_SIZE))
    }

    private fun addAllDiff(diff: diff_match_patch.Diff): String {
        return StringEscapeUtils.escapeHtml4(diff.text)
    }

    private fun addEllipsis(): String {
        return "<span style='color: #bdbdbd;font-size: 16px;font-family: serif;'>...\n" +
            "......\n" +
            "......\n" +
            "...</span>"
    }

    private fun addDiffStyle(diff: diff_match_patch.Diff, style: String): String {
        return style + StringEscapeUtils.escapeHtml4(diff.text) + "</span>"
    }

    private fun addDiffText(diff: diff_match_patch.Diff, text: String): String {
        return text + StringEscapeUtils.escapeHtml4(diff.text) + "\n"
    }

    private fun addEllipsisText(): String {
        return "......\n......\n...\n"
    }
}
