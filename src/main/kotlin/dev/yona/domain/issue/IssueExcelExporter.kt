package dev.yona.domain.issue

import dev.yona.domain.comment.IssueComment
import jakarta.persistence.EntityManager
import jxl.Workbook
import jxl.format.Alignment
import jxl.format.Border
import jxl.format.BorderLineStyle
import jxl.format.Colour
import jxl.format.ScriptStyle
import jxl.format.UnderlineStyle
import jxl.format.VerticalAlignment
import jxl.write.DateFormat
import jxl.write.Label
import jxl.write.WritableCellFormat
import jxl.write.WritableFont
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * 이슈 목록을 엑셀(xls) 파일로 만든다. v1.6 `Issue.excelFrom`(`app/models/Issue.java:290-400`)에 해당한다.
 *
 * 열은 번호, 상태, 제목, 담당자, 본문, 라벨, 만든 날짜, 마감일, 마일스톤, 주소, 댓글, 댓글 작성자, 댓글 작성 일시이다.
 * 이슈의 댓글은 작성 순서대로 이슈 줄부터 아래로 한 줄씩 이어 쓴다.
 */
@Component
class IssueExcelExporter(private val messages: MessageSource, private val entityManager: EntityManager) {

    /** [issues]를 엑셀 파일의 바이트로 만든다. */
    @Transactional(readOnly = true)
    fun export(issues: List<Issue>): ByteArray {
        val output = ByteArrayOutputStream()
        val workbook = Workbook.createWorkbook(output)
        val sheet = workbook.createSheet(todayMillis().toString(), 0)
        val header = headerFormat()
        val body = bodyFormat()
        val date = dateFormat()

        val titles = listOf(
            "No", message("issue.state"), message("title"), message("issue.assignee"), message("issue.content"), message("issue.label"),
            message("issue.createdDate"), message("issue.dueDate"), message("milestone"), "URL", message("common.comment"),
            message("common.comment.author"), message("common.comment.created")
        )
        titles.forEachIndexed { column, title ->
            sheet.addCell(Label(column, 0, title, header))
            sheet.setColumnView(column, COLUMN_WIDTH)
        }

        val commentsByIssue = comments(issues.mapNotNull { it.id })
        var line = 0
        for (issue in issues) {
            line++
            val comments = commentsByIssue[issue.id].orEmpty()
            var column = 0
            fun text(value: String?) { sheet.addCell(Label(column++, line, value.orEmpty(), body)) }
            text(issue.number.toString())
            text(issue.state?.name)
            text(issue.title)
            text(issue.assignee?.user?.name)
            text(issue.body)
            text(issue.labels.sortedWith(compareBy({ it.category?.name }, { it.name })).joinToString(", ") { it.name.orEmpty() })
            sheet.addCell(jxl.write.DateTime(column++, line, issue.createdDate, date))
            text(issue.dueDate?.let { SimpleDateFormat("yyyy-MM-dd").format(it) })
            text(issue.milestone?.title)
            text("/${issue.project?.owner}/${issue.project?.name}/issue/${issue.number}")
            comments.forEachIndexed { offset, comment ->
                sheet.addCell(Label(column, line + offset, comment.contents.orEmpty(), body))
                sheet.addCell(Label(column + 1, line + offset, comment.authorName.orEmpty(), body))
                sheet.addCell(jxl.write.DateTime(column + 2, line + offset, comment.createdDate, date))
            }
            if (comments.isNotEmpty()) line += comments.size - 1
        }

        workbook.write()
        workbook.close()
        return output.toByteArray()
    }

    private fun comments(issueIds: List<Long>): Map<Long, List<IssueComment>> {
        if (issueIds.isEmpty()) return emptyMap()
        return entityManager.createQuery(
            "select c from IssueComment c where c.issue.id in :ids order by c.createdDate, c.id", IssueComment::class.java
        ).setParameter("ids", issueIds).resultList.groupBy { requireNotNull(it.issue?.id) }
    }

    private fun message(key: String): String = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key

    /** 오늘 0시의 시각(밀리초)이다. v1.6은 이 값을 시트 이름으로 쓴다. */
    private fun todayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun font(size: Int, bold: Boolean) =
        WritableFont(WritableFont.ARIAL, size, if (bold) WritableFont.BOLD else WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT)

    private fun headerFormat() = WritableCellFormat(font(14, true)).apply {
        setBorder(Border.ALL, BorderLineStyle.THIN)
        setAlignment(Alignment.CENTRE)
    }

    private fun bodyFormat() = WritableCellFormat(font(12, false)).apply {
        setBorder(Border.NONE, BorderLineStyle.THIN)
        setVerticalAlignment(VerticalAlignment.TOP)
    }

    private fun dateFormat() = WritableCellFormat(DateFormat("yyyy-MM-dd HH:mm")).apply {
        setFont(font(12, false))
        setShrinkToFit(true)
        setAlignment(Alignment.CENTRE)
        setVerticalAlignment(VerticalAlignment.TOP)
    }

    private companion object {
        const val COLUMN_WIDTH = 20
    }
}
