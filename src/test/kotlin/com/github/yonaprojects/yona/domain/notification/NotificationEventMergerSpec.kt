package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Optional

// yona models/NotificationMail.java의 mergeEvents() 대응 (P1-27).
class NotificationEventMergerSpec : DescribeSpec({
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val merger = NotificationEventMerger(issueCommentRepository, postingCommentRepository, reviewCommentRepository)

    val project = Project(id = 1L, name = "proj", owner = "owner")
    val issue = Issue(id = 100L, title = "이슈", project = project)
    val thread = CodeCommentThread(id = 200L, project = project)
    val alice = User(id = 1L, loginId = "alice", name = "앨리스")
    val bob = User(id = 2L, loginId = "bob", name = "밥")
    val carol = User(id = 3L, loginId = "carol", name = "캐롤")

    fun stateChanged(sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "상태 변경", senderId = sender, created = at,
        resourceType = ResourceType.ISSUE_STATE, resourceId = "100",
        eventType = EventType.ISSUE_STATE_CHANGED, oldValue = "OPEN", newValue = "CLOSED",
        receivers = receivers.toMutableSet()
    )

    fun newComment(commentId: String, sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "새 댓글", senderId = sender, created = at,
        resourceType = ResourceType.ISSUE_COMMENT, resourceId = commentId,
        eventType = EventType.NEW_COMMENT, newValue = "댓글 내용",
        receivers = receivers.toMutableSet()
    )

    fun threadStateChanged(sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "스레드 상태 변경", senderId = sender, created = at,
        resourceType = ResourceType.COMMENT_THREAD, resourceId = "200",
        eventType = EventType.REVIEW_THREAD_STATE_CHANGED, oldValue = "OPEN", newValue = "CLOSED",
        receivers = receivers.toMutableSet()
    )

    fun newReviewComment(commentId: String, sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "새 리뷰 댓글", senderId = sender, created = at,
        resourceType = ResourceType.REVIEW_COMMENT, resourceId = commentId,
        eventType = EventType.NEW_REVIEW_COMMENT, newValue = "댓글 내용",
        receivers = receivers.toMutableSet()
    )

    // NONISSUE_COMMENT/COMMIT_COMMENT 및 비정상 resourceId 케이스를 만들기 위한 범용 헬퍼.
    fun commentOf(resourceType: ResourceType, commentId: String, sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "새 댓글", senderId = sender, created = at,
        resourceType = resourceType, resourceId = commentId,
        eventType = EventType.NEW_COMMENT, newValue = "댓글 내용",
        receivers = receivers.toMutableSet()
    )

    val posting = Posting(id = 300L, title = "게시글", project = project)

    beforeTest {
        val comment = IssueComment(id = 500L, contents = "댓글", issue = issue)
        every { issueCommentRepository.findById(500L) } returns Optional.of(comment)
        val reviewComment = ReviewComment(id = 700L, contents = "리뷰 댓글", thread = thread)
        every { reviewCommentRepository.findById(700L) } returns Optional.of(reviewComment)
    }

    describe("NotificationEventMerger.mergeEvents") {
        it("서로 무관한 이벤트는 각각 단독으로 남아야 한다") {
            val e1 = stateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = newComment("999", 2L, setOf(bob), Instant.parse("2026-01-01T00:00:05Z"))
            every { issueCommentRepository.findById(999L) } returns Optional.empty()

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // legacy 클래스 주석대로 "이슈/리뷰 스레드를 열거나 닫는 알림 + 같은 사람이 남긴 '이전' 댓글 알림"의
        // 병합이므로, 댓글이 먼저(더 오래됨) 발생하고 상태변경이 나중(더 최근)에 발생해야 병합된다.
        it("댓글을 남긴 뒤 같은 사용자가 같은 수신자에게 상태변경을 하면 하나로 합쳐져야 한다") {
            val e1 = newComment("500", sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 1
            merged.first().messageSources shouldBe listOf(e1, e2)
            merged.first().receivers shouldBe setOf(alice, bob)
        }

        it("수신자 집합이 다르면 교집합/댓글전용/상태변경전용 세 갈래로 쪼개져야 한다") {
            val e1 = newComment("500", sender = 1L, receivers = setOf(bob, carol), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 3
            val byReceivers = merged.map { it.receivers }.toSet()
            byReceivers shouldBe setOf(setOf(bob), setOf(alice), setOf(carol))
        }

        it("발신자가 다르면 합쳐지지 않아야 한다") {
            val e1 = newComment("500", sender = 2L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // P1-50이 NEW_REVIEW_COMMENT/REVIEW_THREAD_STATE_CHANGED 프로듀서를 추가하면서 이 병합 로직이
        // 함께 갱신되지 않아 리뷰 댓글은 항상 단독 이벤트로 남던 문제(P1-51에서 발견) 대응.
        it("리뷰 댓글을 남긴 뒤 같은 사용자가 같은 수신자에게 스레드 상태를 변경하면 하나로 합쳐져야 한다") {
            val e1 = newReviewComment("700", sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = threadStateChanged(sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 1
            merged.first().messageSources shouldBe listOf(e1, e2)
            merged.first().receivers shouldBe setOf(alice, bob)
        }

        it("리뷰 댓글과 스레드 상태변경의 수신자 집합이 다르면 세 갈래로 쪼개져야 한다") {
            val e1 = newReviewComment("700", sender = 1L, receivers = setOf(bob, carol), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = threadStateChanged(sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 3
            val byReceivers = merged.map { it.receivers }.toSet()
            byReceivers shouldBe setOf(setOf(bob), setOf(alice), setOf(carol))
        }

        // containerMergeKey()의 NONISSUE_COMMENT 분기(BOARD_POST 컨테이너 정규화) 커버.
        // selfMergeKey는 ISSUE_STATE_CHANGED만 ISSUE_POST로 정규화하고 REVIEW_THREAD_STATE_CHANGED는
        // event.resourceType을 그대로 키로 쓰므로, resourceType=BOARD_POST인 REVIEW_THREAD_STATE_CHANGED
        // 이벤트를 직접 구성하면 NONISSUE_COMMENT 컨테이너 키(BOARD_POST)와 정확히 매치시킬 수 있다
        // (실제 도메인상 조합은 아니지만, containerMergeKey의 NONISSUE_COMMENT 분기 자체를 격리해서
        // 검증하기 위한 것).
        it("게시글 댓글의 컨테이너 키가 정상 조회되면 매칭되는 상태변경 이벤트와 하나로 합쳐져야 한다") {
            val postingComment = PostingComment(id = 600L, contents = "게시글 댓글", posting = posting)
            every { postingCommentRepository.findById(600L) } returns Optional.of(postingComment)
            val stateEvent = NotificationEvent(
                title = "상태 변경", senderId = 1L, created = Instant.parse("2026-01-01T00:00:05Z"),
                resourceType = ResourceType.BOARD_POST, resourceId = "300",
                eventType = EventType.REVIEW_THREAD_STATE_CHANGED, oldValue = "OPEN", newValue = "CLOSED",
                receivers = mutableSetOf(alice, bob)
            )
            val e1 = commentOf(ResourceType.NONISSUE_COMMENT, "600", sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:00Z"))

            val merged = merger.mergeEvents(listOf(e1, stateEvent))

            merged.size shouldBe 1
            merged.first().messageSources shouldBe listOf(e1, stateEvent)
        }

        it("게시글 댓글의 컨테이너 조회가 실패하면(존재하지 않는 댓글) 병합되지 않고 단독으로 남아야 한다") {
            every { postingCommentRepository.findById(601L) } returns Optional.empty()
            val e1 = commentOf(ResourceType.NONISSUE_COMMENT, "601", sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        it("게시글 댓글의 resourceId가 숫자가 아니면 컨테이너 조회 없이 단독으로 남아야 한다") {
            val e1 = commentOf(ResourceType.NONISSUE_COMMENT, "not-a-number", sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // containerMergeKey()의 ISSUE_COMMENT 분기 중 resourceId가 숫자가 아닌 경우(toLongOrNull null) 커버.
        it("이슈 댓글의 resourceId가 숫자가 아니면 컨테이너 조회 없이 단독으로 남아야 한다") {
            val e1 = newComment("not-a-number", sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // containerMergeKey()의 REVIEW_COMMENT 분기 중 resourceId가 숫자가 아닌 경우 커버.
        it("리뷰 댓글의 resourceId가 숫자가 아니면 컨테이너 조회 없이 단독으로 남아야 한다") {
            val e1 = newReviewComment("not-a-number", sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = threadStateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // containerMergeKey()의 REVIEW_COMMENT 분기 중 리뷰 댓글 조회 자체가 실패하는 경우 커버.
        it("리뷰 댓글 조회가 실패하면(존재하지 않는 댓글) 단독으로 남아야 한다") {
            every { reviewCommentRepository.findById(701L) } returns Optional.empty()
            val e1 = newReviewComment("701", sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = threadStateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // containerMergeKey()의 REVIEW_COMMENT 분기 중 comment.thread가 null인 경우(`comment.thread?.id ?: return null`) 커버.
        it("리뷰 댓글에 연결된 스레드가 없으면 단독으로 남아야 한다") {
            val orphanComment = ReviewComment(id = 702L, contents = "고아 리뷰 댓글", thread = null)
            every { reviewCommentRepository.findById(702L) } returns Optional.of(orphanComment)
            val e1 = newReviewComment("702", sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = threadStateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // containerMergeKey()의 when에서 ISSUE_COMMENT/NONISSUE_COMMENT/REVIEW_COMMENT 어디에도 속하지
        // 않는 else 분기(null) 커버 — 클래스 KDoc에 명시된 대로 COMMIT_COMMENT는 NEW_COMMENT로 발행되지만
        // 대응되는 "커밋 상태변경" 개념 자체가 legacy에 없어 항상 컨테이너 병합 대상에서 제외된다.
        it("커밋 댓글(COMMIT_COMMENT)은 컨테이너 정규화 대상이 아니므로 단독으로 남아야 한다") {
            val e1 = commentOf(ResourceType.COMMIT_COMMENT, "900", sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // 상태변경도 아니고(NEW_COMMENT/NEW_REVIEW_COMMENT도 아닌) 병합 대상 자체가 아닌 이벤트 타입은
        // 두 if 모두 스킵하고 그대로 단독 이벤트로 추가되는 경로(line 51 OR의 양변 모두 false)를 태운다.
        it("상태변경도 댓글도 아닌 이벤트는 그대로 단독으로 남아야 한다") {
            val e1 = NotificationEvent(
                title = "새 이슈", senderId = 1L, created = Instant.parse("2026-01-01T00:00:00Z"),
                resourceType = ResourceType.ISSUE_POST, resourceId = "100",
                eventType = EventType.NEW_ISSUE, newValue = "새 이슈 내용",
                receivers = mutableSetOf(alice)
            )

            val merged = merger.mergeEvents(listOf(e1))

            merged.size shouldBe 1
            merged.first().main shouldBe e1
        }

        // containerMergeKey()의 REVIEW_COMMENT 분기 중 comment.thread는 존재하지만 그 thread의 id가
        // 아직 없는 경우(comment.thread?.id ?: return null)의 별도 분기 — thread 자체가 null인
        // 케이스와는 다른 경로다.
        it("리뷰 댓글의 스레드는 있지만 스레드 id가 없으면 단독으로 남아야 한다") {
            val unpersistedThread = CodeCommentThread(id = null, project = project)
            val reviewComment = ReviewComment(id = 703L, contents = "스레드 id 없음", thread = unpersistedThread)
            every { reviewCommentRepository.findById(703L) } returns Optional.of(reviewComment)
            val e1 = newReviewComment("703", sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = threadStateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }
    }
})
