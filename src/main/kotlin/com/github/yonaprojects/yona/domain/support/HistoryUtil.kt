package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.util.diff_match_patch
import org.apache.commons.lang3.StringEscapeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * yona의 controllers/AbstractPostingApp.java의 editPosting()/addToHistory()/getHistoryMadeBy()/
 * (로컬) getDiffText() 대응 (P2-02). Issue/Posting 본문 수정 시 변경 이력을 `history`에 누적한다.
 *
 * yona는 이슈 초안(draft)을 최초 발행(isPublish)할 때 history를 초기화하는 별도 분기가 있지만,
 * yona는 이슈 초안 발행 플로우 자체를 아직 이식하지 않았으므로(별도 범위) 이 유틸은 "본문이
 * 바뀌면 항상 이력을 이어붙인다"는 본질만 이식한다.
 */
object HistoryUtil {
    // yona AbstractPostingApp.Diff_EditCost(이력용, 16) — DiffUtil.DIFF_EDITCOST(알림용, 8)와는 별개 상수다.
    private const val HISTORY_DIFF_EDIT_COST: Short = 16
    private const val EQUAL_TEXT_ELLIPSIS_SIZE = 100
    private const val EQUAL_TEXT_BASE_SIZE = 50

    // yona AbstractPostingApp.addToHistory() 대응
    fun appendHistory(
        originalBody: String?,
        newBody: String?,
        updaterName: String,
        updaterLoginId: String,
        updatedDate: Instant?,
        existingHistory: String?
    ): String {
        val dmp = diff_match_patch()
        dmp.Diff_EditCost = HISTORY_DIFF_EDIT_COST
        val diffs = dmp.diff_main(originalBody ?: "", newBody ?: "")
        dmp.diff_cleanupEfficiency(diffs)

        val entry = (
            historyMadeBy(updaterName, updaterLoginId, updatedDate, diffs) +
                historyDiffText(originalBody, newBody) +
                "\n"
            ).replace("\n", "</br>\n")

        return entry + (existingHistory ?: "")
    }

    // yona AbstractPostingApp.getHistoryMadeBy() 대응
    private fun historyMadeBy(
        updaterName: String,
        updaterLoginId: String,
        updatedDate: Instant?,
        diffs: List<diff_match_patch.Diff>
    ): String {
        var insertions = 0
        var deletions = 0
        for (diff in diffs) {
            when (diff.operation) {
                diff_match_patch.Operation.DELETE -> deletions++
                diff_match_patch.Operation.INSERT -> insertions++
                else -> {}
            }
        }

        val sb = StringBuilder()
        sb.append("<div class='history-made-by'>").append(updaterName)
            .append("(").append(updaterLoginId).append(") ")
        if (insertions > 0) {
            sb.append("<span class='added'> ").append(" + ").append(insertions).append(" </span>")
        }
        if (deletions > 0) {
            sb.append("<span class='deleted'> ").append(" - ").append(deletions).append(" </span>")
        }
        sb.append(" at ").append(formatDate(updatedDate)).append("</div><hr/>\n")
        return sb.toString()
    }

    private fun formatDate(date: Instant?): String {
        if (date == null) return ""
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss a")
        return formatter.format(date.atZone(ZoneId.systemDefault()))
    }

    // yona AbstractPostingApp의 로컬 getDiffText() 대응 - DiffUtil.getDiffText와는 CSS 클래스와
    // 생략 구간 중복 방지 처리가 다른 별도 변형이다.
    private fun historyDiffText(oldValue: String?, newValue: String?): String {
        if (oldValue == null) {
            return ""
        }

        val dmp = diff_match_patch()
        dmp.Diff_EditCost = HISTORY_DIFF_EDIT_COST
        val sb = StringBuilder()

        val diffs = dmp.diff_main(oldValue, newValue ?: "")
        dmp.diff_cleanupEfficiency(diffs)

        for (diff in diffs) {
            when (diff.operation) {
                diff_match_patch.Operation.DELETE -> {
                    sb.append("<span class='diff-deleted'>")
                    sb.append(StringEscapeUtils.escapeHtml4(diff.text))
                    sb.append("</span>")
                }
                diff_match_patch.Operation.EQUAL -> {
                    val textLength = diff.text.length
                    if (textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                        if (diff.text.substring(0, EQUAL_TEXT_BASE_SIZE) != oldValue.substring(0, EQUAL_TEXT_BASE_SIZE)) {
                            sb.append(StringEscapeUtils.escapeHtml4(diff.text.substring(0, EQUAL_TEXT_BASE_SIZE)))
                        }
                        sb.append("<span class='diff-ellipsis'>...\n")
                            .append("......\n")
                            .append("......\n")
                            .append("...</span>")
                        if (diff.text.substring(textLength - EQUAL_TEXT_BASE_SIZE) !=
                            oldValue.substring(oldValue.length - EQUAL_TEXT_BASE_SIZE)
                        ) {
                            sb.append(StringEscapeUtils.escapeHtml4(diff.text.substring(textLength - EQUAL_TEXT_BASE_SIZE)))
                        }
                    } else {
                        sb.append(StringEscapeUtils.escapeHtml4(diff.text))
                    }
                }
                diff_match_patch.Operation.INSERT -> {
                    sb.append("<span class='diff-added'>")
                    sb.append(StringEscapeUtils.escapeHtml4(diff.text))
                    sb.append("</span>")
                }
                else -> {}
            }
        }

        return sb.toString().replace("\n", "&nbsp<br/>\n")
    }
}
