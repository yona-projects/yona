package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import jxl.CellType
import jxl.DateCell
import jxl.Workbook
import org.springframework.context.MessageSource
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// IssueExcelService.excelFrom()이 실제로 만들어내는 jxl 워크북(엑셀 파일)의 내용을
// 다시 열어서(jxl.Workbook.getWorkbook) 셀 값까지 검증하는 스펙.
// 담당자/마일스톤/라벨/등록일/마감일/댓글 유무 등 실제 코드에서 발견한 분기를 각각 케이스로 나눈다.
class IssueExcelServiceSpec : DescribeSpec({
    val messageSource = mockk<MessageSource>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val service = IssueExcelService(messageSource, issueCommentRepository)

    val zoneId = ZoneId.systemDefault()
    val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zoneId)

    beforeTest {
        clearMocks(messageSource, issueCommentRepository)
        // getMessage(key, args, default, locale) 호출 시 항상 기본값(3번째 인자, 한국어 문자열)을 그대로 반환
        every { messageSource.getMessage(any<String>(), any(), any<String>(), any()) } answers { thirdArg() }
    }

    fun project(id: Long = 1L, owner: String = "owner1", name: String = "proj") =
        Project(id = id, name = name, owner = owner)

    fun issue(
        id: Long,
        number: Long,
        proj: Project = project(),
        title: String = "이슈 제목",
        body: String? = "이슈 본문",
        state: State = State.OPEN,
        milestone: Milestone? = null,
        assignee: Assignee? = null,
        labels: MutableSet<IssueLabel> = mutableSetOf(),
        createdDate: Instant? = null,
        dueDate: Instant? = null
    ) = Issue(
        id = id,
        title = title,
        body = body,
        project = proj,
        number = number,
        state = state,
        milestone = milestone,
        assignee = assignee,
        labels = labels,
        createdDate = createdDate,
        dueDate = dueDate
    )

    fun readWorkbook(bytes: ByteArray) = Workbook.getWorkbook(ByteArrayInputStream(bytes))

    // jxl은 DateTime 셀에 Date를 쓸 때 시스템 기본 타임존 기준 로컬 시각 필드를 그대로(마치 GMT인 것처럼)
    // 직렬화하는 방식으로 동작한다(jxl 라이브러리 자체의 특성이며 IssueExcelService 코드의 버그가 아니다).
    // 그래서 다시 읽은 DateCell.getDate()는 원본 Instant보다 시스템 타임존 오프셋만큼 앞선 시각으로 온다.
    // 검증 시에도 동일한 오프셋을 반영해 "실제로 그 날짜가 올바르게 기록됐는지"를 확인한다.
    fun expectedRoundTrip(instant: Instant): Instant {
        val offset = zoneId.rules.getOffset(instant)
        return instant.plusSeconds(offset.totalSeconds.toLong())
    }

    describe("IssueExcelService.excelFrom") {
        it("이슈 목록이 비어있으면 헤더 행만 있는 워크북을 생성해야 한다") {
            val bytes = service.excelFrom(emptyList())
            val sheet = readWorkbook(bytes).getSheet(0)

            sheet.rows shouldBe 1
            sheet.getCell(0, 0).contents shouldBe "No"
            sheet.getCell(1, 0).contents shouldBe "상태"
            sheet.getCell(2, 0).contents shouldBe "제목"
            sheet.getCell(3, 0).contents shouldBe "담당자"
            sheet.getCell(4, 0).contents shouldBe "이슈 본문"
            sheet.getCell(5, 0).contents shouldBe "이슈 라벨"
            sheet.getCell(6, 0).contents shouldBe "작성일"
            sheet.getCell(7, 0).contents shouldBe "목표 완료일"
            sheet.getCell(8, 0).contents shouldBe "마일스톤"
            sheet.getCell(9, 0).contents shouldBe "URL"
            sheet.getCell(10, 0).contents shouldBe "댓글"
            sheet.getCell(11, 0).contents shouldBe "댓글 작성자"
            sheet.getCell(12, 0).contents shouldBe "댓글 작성일"
        }

        it("담당자/마일스톤/라벨/본문/날짜가 모두 없는 이슈는 기본값(미지정, 빈 문자열)으로 채워져야 한다") {
            val theIssue = issue(
                id = 1L, number = 100L, body = null,
                milestone = null, assignee = null, labels = mutableSetOf(),
                createdDate = null, dueDate = null
            )
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(1L) } returns emptyList()

            val bytes = service.excelFrom(listOf(theIssue))
            val sheet = readWorkbook(bytes).getSheet(0)

            sheet.rows shouldBe 2
            sheet.getCell(0, 1).contents shouldBe "100"
            sheet.getCell(1, 1).contents shouldBe "OPEN"
            sheet.getCell(2, 1).contents shouldBe "이슈 제목"
            sheet.getCell(3, 1).contents shouldBe "미지정"
            sheet.getCell(4, 1).contents shouldBe ""
            sheet.getCell(5, 1).contents shouldBe ""
            sheet.getCell(6, 1).contents shouldBe ""
            sheet.getCell(6, 1).type shouldBe CellType.LABEL
            sheet.getCell(7, 1).contents shouldBe ""
            sheet.getCell(8, 1).contents shouldBe ""
            sheet.getCell(9, 1).contents shouldBe "/owner1/proj/issue/100"
        }

        it("담당자/마일스톤/라벨(2개)/본문/날짜가 모두 있는 이슈와 댓글 1건이 정상적으로 채워져야 한다") {
            val proj = project()
            val category = IssueLabelCategory(id = 1L, name = "카테고리", isExclusive = false, project = proj)
            val label1 = IssueLabel(id = 1L, category = category, color = "#ff0000", name = "버그", project = proj)
            val label2 = IssueLabel(id = 2L, category = category, color = "#00ff00", name = "긴급", project = proj)
            val assignee = Assignee(id = 1L, user = User(id = 1L, name = "김철수", loginId = "chulsoo", email = "c@x.com"), project = proj)
            val milestone = Milestone(id = 1L, title = "1.0 마일스톤", project = proj)
            val createdDate = Instant.parse("2024-03-15T10:30:00Z")
            val dueDate = Instant.parse("2024-04-01T00:00:00Z")

            val theIssue = issue(
                id = 2L, number = 101L, proj = proj, body = "이슈 본문 내용",
                milestone = milestone, assignee = assignee,
                labels = mutableSetOf(label1, label2),
                createdDate = createdDate, dueDate = dueDate
            )
            val comment = IssueComment(
                id = 1L, contents = "댓글 내용", createdDate = Instant.parse("2024-03-16T09:00:00Z"),
                authorName = "김철수", authorLoginId = "chulsoo", issue = theIssue
            )
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(2L) } returns listOf(comment)

            val bytes = service.excelFrom(listOf(theIssue))
            val sheet = readWorkbook(bytes).getSheet(0)

            sheet.rows shouldBe 2
            sheet.getCell(3, 1).contents shouldBe "김철수"
            sheet.getCell(4, 1).contents shouldBe "이슈 본문 내용"
            sheet.getCell(5, 1).contents shouldBe "버그, 긴급"

            val createdCell = sheet.getCell(6, 1)
            createdCell.type shouldBe CellType.DATE
            (createdCell as DateCell).date.toInstant() shouldBe expectedRoundTrip(createdDate)

            sheet.getCell(7, 1).contents shouldBe dateOnlyFormatter.format(dueDate)
            sheet.getCell(8, 1).contents shouldBe "1.0 마일스톤"

            // 댓글은 이슈 본문과 같은 행(row 1)에 기록된다
            sheet.getCell(10, 1).contents shouldBe "댓글 내용"
            sheet.getCell(11, 1).contents shouldBe "김철수"
            val commentDateCell = sheet.getCell(12, 1)
            commentDateCell.type shouldBe CellType.DATE
            (commentDateCell as DateCell).date.toInstant() shouldBe
                expectedRoundTrip(Instant.parse("2024-03-16T09:00:00Z"))
        }

        it("댓글이 여러 건이면 댓글 수만큼 행이 늘어나고, 작성자명이 없으면 로그인ID로, 둘 다 없으면 빈문자열로 대체돼야 한다") {
            val theIssue = issue(id = 3L, number = 102L)
            // 첫 댓글: authorName 없음 -> authorLoginId로 대체, 작성일 있음(DateTime)
            val comment1 = IssueComment(
                id = 1L, contents = "댓글1", createdDate = Instant.parse("2024-05-01T00:00:00Z"),
                authorName = null, authorLoginId = "chulsoo", issue = theIssue
            )
            // 둘째 댓글: authorName, authorLoginId 모두 없음 -> 빈문자열, 작성일도 없음(Label)
            val comment2 = IssueComment(
                id = 2L, contents = "댓글2", createdDate = null,
                authorName = null, authorLoginId = null, issue = theIssue
            )
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(3L) } returns listOf(comment1, comment2)

            val bytes = service.excelFrom(listOf(theIssue))
            val sheet = readWorkbook(bytes).getSheet(0)

            // 헤더 1행 + 이슈행 1행 + 댓글 2건 중 1건 추가 행(첫 댓글은 이슈행과 같은 행 공유)
            sheet.rows shouldBe 3

            sheet.getCell(10, 1).contents shouldBe "댓글1"
            sheet.getCell(11, 1).contents shouldBe "chulsoo"
            sheet.getCell(12, 1).type shouldBe CellType.DATE

            sheet.getCell(10, 2).contents shouldBe "댓글2"
            sheet.getCell(11, 2).contents shouldBe ""
            sheet.getCell(12, 2).contents shouldBe ""
            sheet.getCell(12, 2).type shouldBe CellType.LABEL
        }

        it("이슈가 여러 건이면 각 이슈 번호와 행 위치가 댓글 수에 맞춰 이어져야 한다") {
            val issue1 = issue(id = 10L, number = 200L)
            val issue2 = issue(id = 11L, number = 201L)
            val c1 = IssueComment(id = 1L, contents = "이슈1 댓글A", authorName = "A", issue = issue1)
            val c2 = IssueComment(id = 2L, contents = "이슈1 댓글B", authorName = "B", issue = issue1)
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(10L) } returns listOf(c1, c2)
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(11L) } returns emptyList()

            val bytes = service.excelFrom(listOf(issue1, issue2))
            val sheet = readWorkbook(bytes).getSheet(0)

            // 헤더 1 + 이슈1행(댓글2건 -> +1행) + 이슈2행 = 4행
            sheet.rows shouldBe 4
            sheet.getCell(0, 1).contents shouldBe "200"
            sheet.getCell(10, 1).contents shouldBe "이슈1 댓글A"
            sheet.getCell(10, 2).contents shouldBe "이슈1 댓글B"
            // 두번째 이슈는 댓글로 밀린 다음 행(row 3)에서 시작한다
            sheet.getCell(0, 3).contents shouldBe "201"
        }
    }
})
