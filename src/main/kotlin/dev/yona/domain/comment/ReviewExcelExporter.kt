package dev.yona.domain.comment

import jxl.Workbook
import jxl.format.Alignment
import jxl.format.Border
import jxl.format.BorderLineStyle
import jxl.format.Colour
import jxl.format.ScriptStyle
import jxl.format.UnderlineStyle
import jxl.format.VerticalAlignment
import jxl.write.DateFormat
import jxl.write.DateTime
import jxl.write.Label
import jxl.write.WritableCellFormat
import jxl.write.WritableFont
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.util.Calendar

/**
 * 리뷰 스레드를 엑셀(xls)로 만든다. v1.6 `ReviewThreadApp.excelFrom`(`app/controllers/ReviewThreadApp.java:78-140`)에 해당한다.
 *
 * 스레드의 리뷰 댓글마다 한 줄이다. 댓글 내용이 스레드의 첫 댓글 내용과 같으면 제목 줄(`REVIEW TITLE`, `Thread Author`)로,
 * 다르면 응답 줄(`Response Text`, `Response`)로 쓴다. 그래서 첫 댓글과 같은 글을 단 응답도 제목 줄이 된다.
 */
@Component
class ReviewExcelExporter {

    /** [threads]를 엑셀 파일의 바이트로 만든다. */
    @Transactional(readOnly = true)
    fun export(threads: List<CommentThread>): ByteArray {
        val output = ByteArrayOutputStream()
        val workbook = Workbook.createWorkbook(output)
        val sheet = workbook.createSheet(todayMillis().toString(), 0)
        val header = headerFormat()
        val body = bodyFormat()
        val date = dateFormat()

        TITLES.forEachIndexed { column, title ->
            sheet.addCell(Label(column, 0, title, header))
            sheet.setColumnView(column, COLUMN_WIDTH)
        }

        var row = 0
        for (thread in threads) {
            val comments = thread.reviewComments.sortedWith(compareBy({ it.createdDate }, { it.id }))
            val firstComment = comments.firstOrNull()
            val first = firstComment?.contents.orEmpty()
            val commitId = when (thread) {
                is NonRangedCodeCommentThread -> thread.commitId
                is CodeCommentThread -> thread.commitId
                else -> null
            }.orEmpty()
            for (comment in comments) {
                var column = 0
                // v1.6은 첫 댓글과 내용이 같은 응답도 제목 줄로 써서 응답이 빠졌다(R-65-3). 제목 줄은 첫 댓글 하나뿐이다
                val response = if (comment === firstComment) "" else comment.contents.orEmpty()
                fun text(value: String?) { sheet.addCell(Label(column++, row + 1, value.orEmpty(), body)) }
                text((row + 1).toString())
                text(commitId.take(COMMIT_LENGTH))
                text(thread.id.toString())
                text(if (response.isEmpty()) first else "")
                text(if (response.isEmpty()) thread.authorName else "")
                text(response)
                text(if (response.isNotEmpty()) comment.authorName else "")
                text(thread.state?.name)
                text((thread.pullRequest != null).toString())
                comment.createdDate?.let { sheet.addCell(DateTime(column, row + 1, it, date)) }
                row++
            }
        }
        workbook.write()
        workbook.close()
        return output.toByteArray()
    }

    private fun todayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun font(size: Int, bold: Boolean) =
        WritableFont(WritableFont.ARIAL, size, if (bold) WritableFont.BOLD else WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT)

    private fun headerFormat() = WritableCellFormat(font(14, true)).apply {
        setBorder(Border.ALL, BorderLineStyle.DOUBLE)
        setAlignment(Alignment.CENTRE)
    }

    private fun bodyFormat() = WritableCellFormat(font(12, false)).apply {
        setBorder(Border.ALL, BorderLineStyle.THIN)
        setWrap(true)
        setVerticalAlignment(VerticalAlignment.TOP)
    }

    private fun dateFormat() = WritableCellFormat(DateFormat("yyyy-MM-dd HH:mm")).apply {
        setFont(font(12, false))
        setShrinkToFit(true)
        setBorder(Border.ALL, BorderLineStyle.THIN)
        setAlignment(Alignment.CENTRE)
        setVerticalAlignment(VerticalAlignment.TOP)
    }

    private companion object {
        const val COLUMN_WIDTH = 20
        const val COMMIT_LENGTH = 7
        val TITLES = listOf("No", "COMMIT ID", "REVIEW ID", "REVIEW TITLE", "Thread Author", "Response Text", "Response", "REVIEW STATE", "is PullRequest?", "Date")
    }
}
