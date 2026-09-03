package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.support.ReviewSearchCondition
import com.github.yonaprojects.yona.domain.support.ReviewThreadService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
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
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

@Controller
class ReviewThreadController(
    private val projectRepository: ProjectRepository,
    private val reviewThreadService: ReviewThreadService,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val accessControl: AccessControl
) {

    @GetMapping("/{owner}/{projectName}/reviews")
    fun reviewThreads(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        condition: ReviewSearchCondition,
        @RequestParam(value = "format", required = false) format: String?,
        authentication: Authentication?,
        model: Model
    ): Any {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val currentUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        val isCodeAccessible = checkCodeAccessibility(project, currentUser)
        if (!isCodeAccessible) {
            // yona ReviewThreadApp.java:41 @IsAllowed(value = Operation.READ) 대응 (P-템플릿 #47) —
            // IsAllowedAction.call()이 접근 거부 시 forbidden(ErrorViews.Forbidden.render(
            // "error.forbidden", project))를 돌려준다. 프로젝트는 이미 찾았으므로 컨텍스트 인지형 403.
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        if (format == "xls") {
            val threads = reviewThreadService.getReviewThreads(project, condition)
            val fileBytes = excelFrom(threads)
            val filename = "${project.name}_reviews_${DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())}.xls"

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileBytes)
        }

        val pageable = PageRequest.of(condition.pageNum - 1, 15)
        val commentThreads = reviewThreadService.getReviewThreads(project, condition, pageable)

        val countEveryone = reviewThreadService.countReviewThreads(project, condition.clone().setAuthorId(null).setParticipantId(null))
        val countParticipant = currentUser?.let {
            reviewThreadService.countReviewThreads(project, condition.clone().setAuthorId(null).setParticipantId(it.id))
        } ?: 0L
        val countAuthor = currentUser?.let {
            reviewThreadService.countReviewThreads(project, condition.clone().setParticipantId(null).setAuthorId(it.id))
        } ?: 0L

        val countOpen = reviewThreadService.countReviewThreads(project, condition.clone().setState("OPEN"))
        val countClosed = reviewThreadService.countReviewThreads(project, condition.clone().setState("CLOSED"))

        model.addAttribute("project", project)
        model.addAttribute("commentThreads", commentThreads)
        // yona reviewthread/list.scala.html의 "param"(ReviewSearchCondition) 대응. Thymeleaf 3에서
        // "param"은 HTTP 요청 파라미터를 가리키는 예약된 암묵 객체라 동일 이름의 모델 속성과 충돌해
        // "${param}" 단독 참조 시 SpEL 평가 예외가 나므로 searchCondition으로 개명한다.
        model.addAttribute("searchCondition", condition)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("countEveryone", countEveryone)
        model.addAttribute("countParticipant", countParticipant)
        model.addAttribute("countAuthor", countAuthor)
        model.addAttribute("countOpen", countOpen)
        model.addAttribute("countClosed", countClosed)

        return "reviewthread/list"
    }

    private fun excelFrom(commentThreads: List<CommentThread>): ByteArray {
        val bos = ByteArrayOutputStream()
        val workbook = Workbook.createWorkbook(bos)
        val todayStr = Instant.now().toEpochMilli().toString()
        val sheet = workbook.createSheet(todayStr, 0)

        val headerCellFormat = getHeaderCellFormat()
        val bodyCellFormat = getBodyCellFormat()
        val dateCellFormat = getDateCellFormat()

        val titles = arrayOf("No", "COMMIT ID", "REVIEW ID", "REVIEW TITLE", "Thread Author", "Response Text", "Response", "REVIEW STATE", "is PullRequest?", "Date")

        for (i in titles.indices) {
            sheet.addCell(Label(i, 0, titles[i], headerCellFormat))
            sheet.setColumnView(i, 20)
        }

        var rowNumber = 0
        for (idx in commentThreads.indices) {
            val commentThread = commentThreads[idx]
            val commitId = commentThread.commitId ?: ""
            val threadFirstComment = commentThread.getFirstReviewComment().contents
            for (j in commentThread.reviewComments.indices) {
                val comment = commentThread.reviewComments[j]
                var columnPos = 0
                val responseComment = if (threadFirstComment == comment.contents) "" else comment.contents
                
                sheet.addCell(Label(columnPos++, rowNumber + 1, (rowNumber + 1).toString(), bodyCellFormat))
                sheet.addCell(Label(columnPos++, rowNumber + 1, if (commitId.length >= 7) commitId.substring(0, 7) else commitId, bodyCellFormat))
                sheet.addCell(Label(columnPos++, rowNumber + 1, commentThread.id.toString(), bodyCellFormat))
                sheet.addCell(Label(columnPos++, rowNumber + 1, if (responseComment.isEmpty()) threadFirstComment else "", bodyCellFormat))
                sheet.addCell(Label(columnPos++, rowNumber + 1, if (responseComment.isEmpty()) (commentThread.author?.name ?: "") else "", bodyCellFormat))
                sheet.addCell(Label(columnPos++, rowNumber + 1, responseComment, bodyCellFormat))
                sheet.addCell(Label(columnPos++, rowNumber + 1, if (responseComment.isNotEmpty()) (comment.author?.name ?: "") else "", bodyCellFormat))
                sheet.addCell(Label(columnPos++, rowNumber + 1, commentThread.state.toString(), bodyCellFormat))
                sheet.addCell(Label(columnPos++, rowNumber + 1, commentThread.isOnPullRequest().toString(), bodyCellFormat))
                sheet.addCell(DateTime(columnPos++, rowNumber + 1, Date.from(comment.createdDate), dateCellFormat))
                rowNumber++
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

    private fun getHeaderCellFormat(): WritableCellFormat {
        val headerFont = WritableFont(WritableFont.ARIAL, 14, WritableFont.BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT)
        val headerCell = WritableCellFormat(headerFont)
        headerCell.setBorder(Border.ALL, BorderLineStyle.DOUBLE)
        headerCell.alignment = Alignment.CENTRE
        return headerCell
    }

    private fun getBodyCellFormat(): WritableCellFormat {
        val baseFont = WritableFont(WritableFont.ARIAL, 12, WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT)
        val cellFormat = WritableCellFormat(baseFont)
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN)
        cellFormat.wrap = true
        cellFormat.verticalAlignment = VerticalAlignment.TOP
        return cellFormat
    }

    private fun getDateCellFormat(): WritableCellFormat {
        val baseFont = WritableFont(WritableFont.ARIAL, 12, WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT)
        val valueFormatDate = DateFormat("yyyy-MM-dd HH:mm")
        val cellFormat = WritableCellFormat(valueFormatDate)
        cellFormat.setFont(baseFont)
        cellFormat.setShrinkToFit(true)
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN)
        cellFormat.alignment = Alignment.CENTRE
        cellFormat.verticalAlignment = VerticalAlignment.TOP
        return cellFormat
    }

    private fun checkCodeAccessibility(project: Project, user: User?): Boolean {
        if (project.projectScope != ProjectScope.PUBLIC) {
            if (user == null) return false
            return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
                accessControl.isAllowedIfGroupMember(project, user)
        }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (user == null) return false
            return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
                accessControl.isAllowedIfGroupMember(project, user)
        }
        return true
    }
}
