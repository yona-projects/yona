package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import jxl.Workbook
import jxl.write.*
import jxl.write.DateFormat
import jxl.format.Alignment
import jxl.format.Border
import jxl.format.BorderLineStyle
import jxl.format.Colour
import jxl.format.ScriptStyle
import jxl.format.UnderlineStyle
import jxl.format.VerticalAlignment
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@Service
class IssueExcelService(
    private val messageSource: MessageSource,
    private val issueCommentRepository: IssueCommentRepository
) {

    @Transactional(readOnly = true)
    fun excelFrom(issueList: List<Issue>): ByteArray {
        val bos = ByteArrayOutputStream()
        val workbook = Workbook.createWorkbook(bos)
        val sheet = workbook.createSheet(Date().time.toString(), 0)

        val headerCellFormat = getHeaderCellFormat()
        val bodyCellFormat = getBodyCellFormat()
        val dateCellFormat = getDateCellFormat()

        val locale = LocaleContextHolder.getLocale()

        val titles = arrayOf(
            "No",
            messageSource.getMessage("issue.state", null, "상태", locale),
            messageSource.getMessage("title", null, "제목", locale),
            messageSource.getMessage("issue.assignee", null, "담당자", locale),
            messageSource.getMessage("issue.content", null, "이슈 본문", locale),
            messageSource.getMessage("issue.label", null, "이슈 라벨", locale),
            messageSource.getMessage("issue.createdDate", null, "작성일", locale),
            messageSource.getMessage("issue.dueDate", null, "목표 완료일", locale),
            messageSource.getMessage("milestone", null, "마일스톤", locale),
            "URL",
            messageSource.getMessage("common.comment", null, "댓글", locale),
            messageSource.getMessage("common.comment.author", null, "댓글 작성자", locale),
            messageSource.getMessage("common.comment.created", null, "댓글 작성일", locale)
        )

        for (i in titles.indices) {
            sheet.addCell(Label(i, 0, titles[i], headerCellFormat))
            sheet.setColumnView(i, 20)
        }

        var lineNumber = 0
        val zoneId = ZoneId.systemDefault()
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zoneId)
        val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zoneId)

        for (idx in 1..issueList.size) {
            val issue = issueList[idx - 1]
            val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)

            lineNumber++
            var columnPos = 0
            val milestoneName = issue.milestone?.title ?: ""

            // No
            sheet.addCell(Label(columnPos++, lineNumber, issue.number.toString(), bodyCellFormat))
            // 상태
            sheet.addCell(Label(columnPos++, lineNumber, issue.state.toString(), bodyCellFormat))
            // 제목
            sheet.addCell(Label(columnPos++, lineNumber, issue.title ?: "", bodyCellFormat))
            // 담당자
            val assigneeName = issue.assignee?.user?.name ?: "미지정"
            sheet.addCell(Label(columnPos++, lineNumber, assigneeName, bodyCellFormat))
            // 내용
            sheet.addCell(Label(columnPos++, lineNumber, issue.body ?: "", bodyCellFormat))
            // 라벨
            val labelsStr = issue.labels.joinToString(", ") { it.name }
            sheet.addCell(Label(columnPos++, lineNumber, labelsStr, bodyCellFormat))
            // 등록일
            val createdDateVal = if (issue.createdDate != null) Date.from(issue.createdDate) else null
            if (createdDateVal != null) {
                sheet.addCell(DateTime(columnPos++, lineNumber, createdDateVal, dateCellFormat))
            } else {
                sheet.addCell(Label(columnPos++, lineNumber, "", bodyCellFormat))
            }
            // 마감일 (dueDate)
            val dueDateStr = if (issue.dueDate != null) dateOnlyFormatter.format(issue.dueDate) else ""
            sheet.addCell(Label(columnPos++, lineNumber, dueDateStr, bodyCellFormat))
            // 마일스톤
            sheet.addCell(Label(columnPos++, lineNumber, milestoneName, bodyCellFormat))
            // URL
            val issueUrl = "/${issue.project.owner}/${issue.project.name}/issue/${issue.number}"
            sheet.addCell(Label(columnPos++, lineNumber, issueUrl, bodyCellFormat))

            // 댓글들
            if (comments.isNotEmpty()) {
                for (j in comments.indices) {
                    val comment = comments[j]
                    sheet.addCell(Label(columnPos, lineNumber + j, comment.contents ?: "", bodyCellFormat))
                    sheet.addCell(Label(columnPos + 1, lineNumber + j, comment.authorName ?: comment.authorLoginId ?: "", bodyCellFormat))
                    val commentCreatedVal = if (comment.createdDate != null) Date.from(comment.createdDate) else null
                    if (commentCreatedVal != null) {
                        sheet.addCell(DateTime(columnPos + 2, lineNumber + j, commentCreatedVal, dateCellFormat))
                    } else {
                        sheet.addCell(Label(columnPos + 2, lineNumber + j, "", bodyCellFormat))
                    }
                }
                lineNumber += comments.size - 1
            }
        }

        workbook.write()
        try {
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return bos.toByteArray()
    }

    private fun getDateCellFormat(): WritableCellFormat {
        val baseFont = WritableFont(
            WritableFont.ARIAL,
            12,
            WritableFont.NO_BOLD,
            false,
            UnderlineStyle.NO_UNDERLINE,
            Colour.BLACK,
            ScriptStyle.NORMAL_SCRIPT
        )
        val valueFormatDate = DateFormat("yyyy-MM-dd HH:mm")
        val cellFormat = WritableCellFormat(valueFormatDate)
        cellFormat.setFont(baseFont)
        cellFormat.setShrinkToFit(true)
        cellFormat.setAlignment(Alignment.CENTRE)
        cellFormat.setVerticalAlignment(VerticalAlignment.TOP)
        return cellFormat
    }

    private fun getBodyCellFormat(): WritableCellFormat {
        val baseFont = WritableFont(
            WritableFont.ARIAL,
            12,
            WritableFont.NO_BOLD,
            false,
            UnderlineStyle.NO_UNDERLINE,
            Colour.BLACK,
            ScriptStyle.NORMAL_SCRIPT
        )
        val cellFormat = WritableCellFormat(baseFont)
        cellFormat.setBorder(Border.NONE, BorderLineStyle.THIN)
        cellFormat.verticalAlignment = VerticalAlignment.TOP
        return cellFormat
    }

    private fun getHeaderCellFormat(): WritableCellFormat {
        val headerFont = WritableFont(
            WritableFont.ARIAL,
            14,
            WritableFont.BOLD,
            false,
            UnderlineStyle.NO_UNDERLINE,
            Colour.BLACK,
            ScriptStyle.NORMAL_SCRIPT
        )
        val headerCell = WritableCellFormat(headerFont)
        headerCell.setBorder(Border.ALL, BorderLineStyle.THIN)
        headerCell.alignment = Alignment.CENTRE
        return headerCell
    }
}
