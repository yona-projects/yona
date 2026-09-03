package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.Optional

class RecentIssueServiceSpec : DescribeSpec({
    val recentIssueRepository = mockk<RecentIssueRepository>()
    val service = RecentIssueService(recentIssueRepository)

    val project = Project(id = 1L, name = "yona-project", owner = "gildong")
    val user = User(id = 10L, loginId = "gildong", name = "홍길동")

    beforeTest {
        clearMocks(recentIssueRepository, answers = false)
        every { recentIssueRepository.save(any()) } answers { firstArg() }
    }

    describe("RecentIssueService.recordIssueVisit") {
        it("처음 방문한 이슈는 새 RecentIssue로 저장되어야 한다") {
            val issue = Issue(id = 100L, title = "버그 리포트", project = project, number = 7L)
            every { recentIssueRepository.findByUserIdAndIssueId(10L, 100L) } returns Optional.empty()
            every { recentIssueRepository.findByUserIdOrderByIdDesc(10L) } returns emptyList()

            val captured = slot<RecentIssue>()
            every { recentIssueRepository.save(capture(captured)) } answers { firstArg() }

            service.recordIssueVisit(user, issue)

            captured.captured.userId shouldBe 10L
            captured.captured.issueId shouldBe 100L
            captured.captured.postingId shouldBe null
            captured.captured.title shouldBe "버그 리포트"
            captured.captured.url shouldBe "/gildong/yona-project/issue/7"
        }

        it("이미 본 이슈를 재방문하면 기존 항목을 지우고 새로 저장해야 한다") {
            val issue = Issue(id = 100L, title = "버그 리포트", project = project, number = 7L)
            val existing = RecentIssue(id = 1L, userId = 10L, issueId = 100L, title = "버그 리포트", url = "/gildong/yona-project/issue/7")
            every { recentIssueRepository.findByUserIdAndIssueId(10L, 100L) } returns Optional.of(existing)
            every { recentIssueRepository.delete(existing) } returns Unit
            every { recentIssueRepository.findByUserIdOrderByIdDesc(10L) } returns emptyList()

            service.recordIssueVisit(user, issue)

            verify(exactly = 1) { recentIssueRepository.delete(existing) }
            verify(exactly = 1) { recentIssueRepository.save(any()) }
        }

        it("user.id가 없으면 아무 작업도 하지 않고 반환해야 한다") {
            val noIdUser = User(id = null, loginId = "noid", name = "아이디없음")
            val issue = Issue(id = 100L, title = "버그 리포트", project = project, number = 7L)

            service.recordIssueVisit(noIdUser, issue)

            verify(exactly = 0) { recentIssueRepository.save(any()) }
        }

        it("issue.id가 없으면 아무 작업도 하지 않고 반환해야 한다") {
            val noIdIssue = Issue(id = null, title = "미저장 이슈", project = project, number = 8L)

            service.recordIssueVisit(user, noIdIssue)

            verify(exactly = 0) { recentIssueRepository.save(any()) }
        }

        it("사용자당 100개를 초과하면 가장 오래된 항목부터 삭제해야 한다") {
            val issue = Issue(id = 999L, title = "새 이슈", project = project, number = 50L)
            every { recentIssueRepository.findByUserIdAndIssueId(10L, 999L) } returns Optional.empty()

            val overflowing = (1L..101L).map {
                RecentIssue(id = it, userId = 10L, issueId = it, title = "old-$it", url = "/x", createdDate = Instant.now())
            }
            every { recentIssueRepository.findByUserIdOrderByIdDesc(10L) } returns overflowing.sortedByDescending { it.id }
            every { recentIssueRepository.delete(any()) } returns Unit

            service.recordIssueVisit(user, issue)

            verify(exactly = 1) { recentIssueRepository.delete(match { it.id == 1L }) }
        }
    }

    describe("RecentIssueService.recordPostingVisit") {
        it("처음 방문한 게시글은 새 RecentIssue로 저장되어야 한다") {
            val posting = Posting(id = 200L, title = "공지사항", project = project, number = 3L)
            every { recentIssueRepository.findByUserIdAndPostingId(10L, 200L) } returns Optional.empty()
            every { recentIssueRepository.findByUserIdOrderByIdDesc(10L) } returns emptyList()

            val captured = slot<RecentIssue>()
            every { recentIssueRepository.save(capture(captured)) } answers { firstArg() }

            service.recordPostingVisit(user, posting)

            captured.captured.userId shouldBe 10L
            captured.captured.postingId shouldBe 200L
            captured.captured.issueId shouldBe null
            captured.captured.title shouldBe "공지사항"
            captured.captured.url shouldBe "/gildong/yona-project/post/3"
        }

        it("이미 본 게시글을 재방문하면 기존 항목을 지우고 새로 저장해야 한다") {
            val posting = Posting(id = 200L, title = "공지사항", project = project, number = 3L)
            val existing = RecentIssue(id = 2L, userId = 10L, postingId = 200L, title = "공지사항", url = "/gildong/yona-project/post/3")
            every { recentIssueRepository.findByUserIdAndPostingId(10L, 200L) } returns Optional.of(existing)
            every { recentIssueRepository.delete(existing) } returns Unit
            every { recentIssueRepository.findByUserIdOrderByIdDesc(10L) } returns emptyList()

            service.recordPostingVisit(user, posting)

            verify(exactly = 1) { recentIssueRepository.delete(existing) }
            verify(exactly = 1) { recentIssueRepository.save(any()) }
        }

        it("user.id가 없으면 아무 작업도 하지 않고 반환해야 한다") {
            val noIdUser = User(id = null, loginId = "noid", name = "아이디없음")
            val posting = Posting(id = 200L, title = "공지사항", project = project, number = 3L)

            service.recordPostingVisit(noIdUser, posting)

            verify(exactly = 0) { recentIssueRepository.save(any()) }
        }

        it("posting.id가 없으면 아무 작업도 하지 않고 반환해야 한다") {
            val noIdPosting = Posting(id = null, title = "미저장 게시글", project = project, number = 4L)

            service.recordPostingVisit(user, noIdPosting)

            verify(exactly = 0) { recentIssueRepository.save(any()) }
        }
    }

    describe("RecentIssueService.getRecentIssues") {
        it("사용자의 최근 방문 목록을 id 내림차순으로 반환해야 한다") {
            val list = listOf(
                RecentIssue(id = 3L, userId = 10L, issueId = 5L, title = "c", url = "/c"),
                RecentIssue(id = 2L, userId = 10L, issueId = 4L, title = "b", url = "/b")
            )
            every { recentIssueRepository.findByUserIdOrderByIdDesc(10L) } returns list

            service.getRecentIssues(user) shouldBe list
        }

        it("user.id가 없으면 빈 목록을 반환해야 한다") {
            val noIdUser = User(id = null, loginId = "noid", name = "아이디없음")

            service.getRecentIssues(noIdUser) shouldBe emptyList()
        }
    }

    describe("RecentIssueService.deleteAll (P1-41)") {
        it("사용자의 최근 방문 이력을 모두 삭제해야 한다") {
            every { recentIssueRepository.deleteByUserId(10L) } returns Unit

            service.deleteAll(user)

            verify(exactly = 1) { recentIssueRepository.deleteByUserId(10L) }
        }

        it("user.id가 없으면 아무 작업도 하지 않고 반환해야 한다") {
            val noIdUser = User(id = null, loginId = "noid", name = "아이디없음")

            service.deleteAll(noIdUser)

            verify(exactly = 0) { recentIssueRepository.deleteByUserId(any()) }
        }
    }
})
