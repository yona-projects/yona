package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.SimpleCommentThread
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.support.DiffUtil
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.diff.RawText
import org.springframework.context.MessageSource
import java.time.Instant
import java.util.Locale
import java.util.Optional

// yona models/NotificationEvent.java의 getMessage(Lang)/getPlainMessage(Lang) 대응 (P1-27).
class NotificationMessageResolverSpec : DescribeSpec({
    val messageSource = mockk<MessageSource>()
    val userRepository = mockk<UserRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val repositoryService = mockk<RepositoryService>()
    val resolver = NotificationMessageResolver(messageSource, userRepository, reviewCommentRepository, repositoryService)
    val locale = Locale.KOREAN

    beforeTest {
        // key와 args를 그대로 이어붙여 반환 — 어떤 메시지 키/인자로 해석했는지만 검증한다.
        every { messageSource.getMessage(any<String>(), any(), any<String>(), any()) } answers {
            val key = firstArg<String>()
            val args = secondArg<Array<Any>>()
            "$key(${args.joinToString(",")})"
        }
    }

    fun event(
        eventType: EventType,
        oldValue: String? = null,
        newValue: String? = null,
        resourceType: ResourceType = ResourceType.ISSUE_POST,
        resourceId: String = "1",
        senderId: Long? = null
    ) = NotificationEvent(
        title = "제목", senderId = senderId, created = Instant.now(),
        resourceType = resourceType, resourceId = resourceId,
        eventType = eventType, oldValue = oldValue, newValue = newValue
    )

    describe("getMessage") {
        it("ISSUE_STATE_CHANGED: newValue가 CLOSED면 closed 메시지를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_STATE_CHANGED, newValue = "CLOSED"), locale) shouldBe "notification.issue.closed()"
        }

        it("ISSUE_STATE_CHANGED: 그 외에는 reopened 메시지를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_STATE_CHANGED, newValue = "OPEN"), locale) shouldBe "notification.issue.reopened()"
        }

        it("ISSUE_ASSIGNEE_CHANGED: newValue가 없으면 unassigned를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_ASSIGNEE_CHANGED, newValue = null), locale) shouldBe "notification.issue.unassigned()"
        }

        it("ISSUE_ASSIGNEE_CHANGED: newValue가 있으면 assigned에 담당자 이름을 담아 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_ASSIGNEE_CHANGED, newValue = "홍길동"), locale) shouldBe "notification.issue.assigned(홍길동)"
        }

        it("ISSUE_ASSIGNEE_CHANGED: newValue가 빈 문자열이어도 unassigned로 처리한다(isNullOrBlank)") {
            resolver.getMessage(event(EventType.ISSUE_ASSIGNEE_CHANGED, newValue = ""), locale) shouldBe "notification.issue.unassigned()"
        }

        it("ISSUE_MILESTONE_CHANGED: newValue가 비어있으면 noMilestone을 인자로 담는다") {
            every { messageSource.getMessage("issue.noMilestone", any(), any<String>(), Locale.getDefault()) } returns "마일스톤 없음"
            resolver.getMessage(event(EventType.ISSUE_MILESTONE_CHANGED, newValue = ""), locale) shouldBe "notification.milestone.changed(마일스톤 없음)"
        }

        it("ISSUE_MILESTONE_CHANGED: yona는 마일스톤 ID가 아니라 제목 문자열을 그대로 저장하므로 조회 없이 그대로 쓴다") {
            resolver.getMessage(event(EventType.ISSUE_MILESTONE_CHANGED, newValue = "1.0 릴리즈"), locale) shouldBe "notification.milestone.changed(1.0 릴리즈)"
        }

        it("ISSUE_MILESTONE_CHANGED: newValue가 null이어도(isNullOrBlank의 null 분기) noMilestone을 인자로 담는다") {
            every { messageSource.getMessage("issue.noMilestone", any(), any<String>(), Locale.getDefault()) } returns "마일스톤 없음"
            resolver.getMessage(event(EventType.ISSUE_MILESTONE_CHANGED, newValue = null), locale) shouldBe "notification.milestone.changed(마일스톤 없음)"
        }

        it("NEW_ISSUE/NEW_POSTING/NEW_PULL_REQUEST/NEW_COMMIT/COMMENT_UPDATED는 newValue를 그대로 반환한다") {
            resolver.getMessage(event(EventType.NEW_ISSUE, newValue = "이슈 본문"), locale) shouldBe "이슈 본문"
            resolver.getMessage(event(EventType.NEW_POSTING, newValue = "게시글 본문"), locale) shouldBe "게시글 본문"
            resolver.getMessage(event(EventType.NEW_PULL_REQUEST, newValue = "PR 본문"), locale) shouldBe "PR 본문"
            resolver.getMessage(event(EventType.NEW_COMMIT, newValue = "커밋 메시지"), locale) shouldBe "커밋 메시지"
            resolver.getMessage(event(EventType.COMMENT_UPDATED, newValue = "수정된 댓글"), locale) shouldBe "수정된 댓글"
        }

        it("NEW_ISSUE: newValue가 null이면(orEmpty의 null 분기) 빈 문자열을 반환한다") {
            resolver.getMessage(event(EventType.NEW_ISSUE, newValue = null), locale) shouldBe ""
        }

        it("PULL_REQUEST_COMMIT_CHANGED는 newValue를 그대로 반환한다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_COMMIT_CHANGED, newValue = "커밋 변경"), locale) shouldBe "커밋 변경"
        }

        it("PULL_REQUEST_COMMIT_CHANGED: newValue가 null이면(orEmpty의 null 분기) 빈 문자열을 반환한다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_COMMIT_CHANGED, newValue = null), locale) shouldBe ""
        }

        it("NEW_COMMENT: oldValue가 null이어도 'null' 문자열을 붙이지 않는다") {
            resolver.getMessage(event(EventType.NEW_COMMENT, newValue = "댓글 내용", oldValue = null), locale) shouldBe "댓글 내용"
        }

        it("NEW_COMMENT: newValue가 null이고 oldValue가 있으면(두 orEmpty의 나머지 분기) oldValue만 반환한다") {
            resolver.getMessage(event(EventType.NEW_COMMENT, newValue = null, oldValue = "인용된 이전 내용"), locale) shouldBe "인용된 이전 내용"
        }

        it("ISSUE_BODY_CHANGED/POSTING_BODY_CHANGED는 DiffUtil로 렌더링한다") {
            val message = resolver.getMessage(event(EventType.ISSUE_BODY_CHANGED, oldValue = "old", newValue = "new"), locale)
            message shouldBe DiffUtil.getDiffText("old", "new")

            // POSTING_BODY_CHANGED도 같은 when 분기 값이지만 별도 case label이라 개별적으로 태운다.
            val postingMessage = resolver.getMessage(event(EventType.POSTING_BODY_CHANGED, oldValue = "old2", newValue = "new2"), locale)
            postingMessage shouldBe DiffUtil.getDiffText("old2", "new2")
        }

        it("PULL_REQUEST_STATE_CHANGED: newValue가 OPEN이면 reopened를 반환한다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_STATE_CHANGED, newValue = "OPEN"), locale) shouldBe "notification.pullrequest.reopened()"
        }

        it("PULL_REQUEST_STATE_CHANGED: 그 외에는 소문자로 변환한 상태를 key suffix로 쓴다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_STATE_CHANGED, newValue = "CLOSED"), locale) shouldBe "notification.pullrequest.closed(CLOSED)"
        }

        it("PULL_REQUEST_STATE_CHANGED: newValue가 null이면(OPEN이 아니므로 else, newValue의 두 orEmpty 모두 null 분기) 빈 key suffix를 쓴다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_STATE_CHANGED, newValue = null), locale) shouldBe "notification.pullrequest.()"
        }

        it("PULL_REQUEST_MERGED: 코드워드 키가 없으면 원본 값을 default로 보여준다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_MERGED, oldValue = "부가 설명", newValue = "임의 제목"), locale) shouldBe
                "notification.type.pullrequest.merged.임의 제목(임의 제목)\n부가 설명"
        }

        it("PULL_REQUEST_MERGED: newValue/oldValue가 모두 null이면(각 orEmpty의 null 분기) 빈 문자열들로 채운다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_MERGED, oldValue = null, newValue = null), locale) shouldBe
                "notification.type.pullrequest.merged.()\n"
        }

        it("MEMBER_ENROLL_REQUEST: REQUEST/CANCEL 값에 따라 분기한다") {
            resolver.getMessage(event(EventType.MEMBER_ENROLL_REQUEST, newValue = "REQUEST"), locale) shouldBe "notification.member.enroll.request()"
            resolver.getMessage(event(EventType.MEMBER_ENROLL_REQUEST, newValue = "CANCEL"), locale) shouldBe "notification.member.enroll.cancel()"
        }

        it("MEMBER_ENROLL_REQUEST: 실제 데이터에서는 쓰이지 않지만 ACCEPT 값도 방어적으로 처리한다") {
            resolver.getMessage(event(EventType.MEMBER_ENROLL_REQUEST, newValue = "ACCEPT"), locale) shouldBe "notification.member.enroll.accept()"
        }

        it("ORGANIZATION_MEMBER_ENROLL_REQUEST: REQUEST/ACCEPT/CANCEL 값에 따라 분기한다") {
            resolver.getMessage(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, newValue = "REQUEST"), locale) shouldBe
                "notification.organization.member.enroll.request()"
            resolver.getMessage(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, newValue = "ACCEPT"), locale) shouldBe
                "notification.organization.member.enroll.accept()"
            resolver.getMessage(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, newValue = "CANCEL"), locale) shouldBe
                "notification.organization.member.enroll.cancel()"
        }

        it("MEMBER_ENROLL_ACCEPT: legacy의 default 분기(요청 locale과 무관하게 항상 기본 로케일)를 재현한다") {
            every { messageSource.getMessage("notification.member.enroll.accept", any(), any<String>(), Locale.getDefault()) } returns "수락됨"
            resolver.getMessage(event(EventType.MEMBER_ENROLL_ACCEPT), locale) shouldBe "수락됨"
        }

        it("PULL_REQUEST_REVIEW_STATE_CHANGED: 발신자 loginId를 인자로 담는다") {
            every { userRepository.findById(5L) } returns Optional.of(User(id = 5L, loginId = "reviewer1", name = "리뷰어"))
            resolver.getMessage(event(EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, newValue = "DONE", senderId = 5L), locale) shouldBe
                "notification.pullrequest.reviewed(reviewer1)"
        }

        it("PULL_REQUEST_REVIEW_STATE_CHANGED: DONE이 아니면 unreviewed를 반환한다") {
            every { userRepository.findById(5L) } returns Optional.of(User(id = 5L, loginId = "reviewer1", name = "리뷰어"))
            resolver.getMessage(event(EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, newValue = "PENDING", senderId = 5L), locale) shouldBe
                "notification.pullrequest.unreviewed(reviewer1)"
        }

        it("PULL_REQUEST_REVIEW_STATE_CHANGED: senderId가 없으면 빈 loginId를 인자로 담는다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, newValue = "DONE", senderId = null), locale) shouldBe
                "notification.pullrequest.reviewed()"
        }

        it("PULL_REQUEST_REVIEW_STATE_CHANGED: senderId는 있지만 사용자를 찾을 수 없으면(?.loginId의 null 분기) 빈 loginId를 인자로 담는다") {
            every { userRepository.findById(6L) } returns Optional.empty()
            resolver.getMessage(event(EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, newValue = "DONE", senderId = 6L), locale) shouldBe
                "notification.pullrequest.reviewed()"
        }

        it("REVIEW_THREAD_STATE_CHANGED: CLOSED 여부로 분기한다") {
            resolver.getMessage(event(EventType.REVIEW_THREAD_STATE_CHANGED, newValue = "CLOSED"), locale) shouldBe "notification.reviewthread.closed()"
            resolver.getMessage(event(EventType.REVIEW_THREAD_STATE_CHANGED, newValue = "OPEN"), locale) shouldBe "notification.reviewthread.reopened()"
        }

        it("ISSUE_MOVED: oldValue/newValue를 함께 인자로 담는다") {
            resolver.getMessage(event(EventType.ISSUE_MOVED, oldValue = "구프로젝트", newValue = "신프로젝트"), locale) shouldBe
                "notification.type.issue.moved(구프로젝트,신프로젝트)"
        }

        it("ISSUE_MOVED: oldValue/newValue가 모두 null이면(각 orEmpty의 null 분기) 빈 문자열들로 채운다") {
            resolver.getMessage(event(EventType.ISSUE_MOVED, oldValue = null, newValue = null), locale) shouldBe
                "notification.type.issue.moved(,)"
        }

        it("ISSUE_SHARER_CHANGED: newValue(loginId)가 있으면 해당 사용자 이름으로 added 메시지를 만든다") {
            every { userRepository.findByLoginId("sharer1") } returns Optional.of(User(loginId = "sharer1", name = "공유대상"))
            resolver.getMessage(event(EventType.ISSUE_SHARER_CHANGED, newValue = "sharer1"), locale) shouldBe "notification.issue.sharer.added(공유대상)"
        }

        // 도달 불가능 분기 메모: `user?.name ?: newValue`(라인 123)와 RESOURCE_DELETED의
        // `user?.name ?: newValue.orEmpty()`(라인 140)는 User.name이 User.kt에서
        // `@Column(nullable = false) var name: String = ""`로 널이 아니게 선언돼 있어, user가 non-null이면
        // user.name도 항상 non-null이다. 즉 "user는 non-null인데 elvis가 발동"하는 조합은 코드상 존재할 수
        // 없다 - JaCoCo는 safe-call(user null 체크)과 elvis(결과 null 체크)를 별개 분기로 세어 이 불가능한
        // 조합 1개씩을 항상 미실행으로 남긴다(아래 두 테스트로 도달 가능한 나머지 조합은 모두 태운다).
        it("ISSUE_SHARER_CHANGED: newValue(loginId)로 사용자를 찾을 수 없으면(user?.name의 null 분기, elvis 발동) newValue 자체를 이름으로 쓴다") {
            every { userRepository.findByLoginId("unknown-sharer") } returns Optional.empty()
            resolver.getMessage(event(EventType.ISSUE_SHARER_CHANGED, newValue = "unknown-sharer"), locale) shouldBe "notification.issue.sharer.added(unknown-sharer)"
        }

        it("ISSUE_SHARER_CHANGED: newValue가 비어있고 oldValue만 있으면 deleted 메시지를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_SHARER_CHANGED, oldValue = "sharer1", newValue = ""), locale) shouldBe "notification.issue.sharer.deleted()"
        }

        it("ISSUE_SHARER_CHANGED: newValue/oldValue가 모두 없으면 빈 문자열을 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_SHARER_CHANGED, oldValue = null, newValue = null), locale) shouldBe ""
        }

        it("ISSUE_SHARER_CHANGED: newValue/oldValue가 모두 빈 문자열이면(oldValue의 isNullOrBlank가 blank-true 분기) 빈 문자열을 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_SHARER_CHANGED, oldValue = "", newValue = ""), locale) shouldBe ""
        }

        it("ISSUE_LABEL_CHANGED: yona는 loginId가 아니라 라벨 이름 목록을 저장하므로 그대로 인자로 쓴다") {
            resolver.getMessage(event(EventType.ISSUE_LABEL_CHANGED, newValue = "bug, urgent"), locale) shouldBe "notification.issue.label.added(bug, urgent)"
        }

        it("ISSUE_LABEL_CHANGED: newValue가 없고 oldValue만 있으면 deleted 메시지를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_LABEL_CHANGED, oldValue = "bug", newValue = ""), locale) shouldBe "notification.issue.label.deleted()"
        }

        it("ISSUE_LABEL_CHANGED: newValue/oldValue가 모두 빈 문자열이면(oldValue의 isNullOrBlank가 blank-true 분기) 빈 문자열을 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_LABEL_CHANGED, oldValue = "", newValue = ""), locale) shouldBe ""
        }

        it("ISSUE_LABEL_CHANGED: newValue/oldValue가 모두 없으면 빈 문자열을 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_LABEL_CHANGED, oldValue = null, newValue = null), locale) shouldBe ""
        }

        it("RESOURCE_DELETED: yona는 삭제된 리소스의 표시 제목을 저장하므로 loginId로 못 찾으면 원본 값을 그대로 보여준다") {
            every { userRepository.findByLoginId("삭제된 글 제목") } returns Optional.empty()
            resolver.getMessage(event(EventType.RESOURCE_DELETED, newValue = "삭제된 글 제목"), locale) shouldBe "notification.resource.deleted(삭제된 글 제목)"
        }

        it("RESOURCE_DELETED: newValue로 사용자를 찾으면 그 사용자 이름을 인자로 쓴다") {
            every { userRepository.findByLoginId("deleter1") } returns Optional.of(User(loginId = "deleter1", name = "삭제자"))
            resolver.getMessage(event(EventType.RESOURCE_DELETED, newValue = "deleter1"), locale) shouldBe "notification.resource.deleted(삭제자)"
        }

        it("RESOURCE_DELETED: newValue가 null이면(newValue?.let의 null 분기, orEmpty의 null 분기) 빈 문자열을 인자로 쓴다") {
            resolver.getMessage(event(EventType.RESOURCE_DELETED, newValue = null), locale) shouldBe "notification.resource.deleted()"
        }

        it("정의되지 않은 이벤트 타입은 warn 로그를 남기고 eventType.messageKey를 기본 로케일로 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_REFERRED_FROM_COMMIT), locale) shouldBe
                "notification.type.issue.referred.from.commit()"
            resolver.getMessage(event(EventType.ISSUE_REFERRED_FROM_PULL_REQUEST), locale) shouldBe
                "notification.type.issue.referred.from.pullrequest()"
        }

        it("msg(): MessageSource가 null을 반환하면 키를 그대로 사용한다") {
            every { messageSource.getMessage("notification.issue.closed", any(), any<String>(), any()) } returns null
            resolver.getMessage(event(EventType.ISSUE_STATE_CHANGED, newValue = "CLOSED"), locale) shouldBe "notification.issue.closed"
        }
    }

    describe("getPlainMessage") {
        it("ISSUE_BODY_CHANGED는 DiffUtil의 plain text 버전을 사용한다") {
            val plain = resolver.getPlainMessage(event(EventType.ISSUE_BODY_CHANGED, oldValue = "old", newValue = "new"), locale)
            plain shouldBe DiffUtil.getDiffPlainText("old", "new")
        }

        it("그 외에는 getMessage 결과에서 '\\n\\n<br />\\n' 패턴만 제거한다") {
            resolver.getPlainMessage(event(EventType.NEW_ISSUE, newValue = "본문\n\n<br />\n이어짐"), locale) shouldBe "본문\n\n이어짐"
        }
    }

    describe("MergedNotificationEvent 대응") {
        it("여러 messageSources를 '\\n\\n---\\n\\n'로 join한다") {
            val e1 = event(EventType.NEW_ISSUE, newValue = "첫 번째")
            val e2 = event(EventType.NEW_ISSUE, newValue = "두 번째")
            val merged = MergedNotificationEvent(e1, listOf(e1, e2))

            resolver.getMessage(merged, locale) shouldBe "첫 번째\n\n---\n\n두 번째"
        }

        it("getPlainMessage(merged)도 각 messageSources의 getPlainMessage를 join한다") {
            val e1 = event(EventType.NEW_ISSUE, newValue = "본문1\n\n<br />\n이어짐1")
            val e2 = event(EventType.NEW_ISSUE, newValue = "본문2")
            val merged = MergedNotificationEvent(e1, listOf(e1, e2))

            resolver.getPlainMessage(merged, locale) shouldBe "본문1\n\n이어짐1\n\n---\n\n본문2"
        }
    }

    // yona NotificationEvent.buildCommentedCodeMessage() 대응 - resolveReviewCommentMessage/buildCommentedCodeMessage의
    // 모든 분기(리소스ID 파싱 실패, 댓글/스레드/프로젝트/커밋 부재, 리포지토리 예외, diff 매칭, hunk 유무, 라인 종류)를 태운다.
    //
    // 도달 불가능 분기 메모: resolveReviewCommentMessage의
    // `buildCommentedCodeMessage(reviewComment, locale) ?: event.newValue.orEmpty()` (라인 177) 중
    // elvis의 null 분기(및 그 안에서만 평가되는 event.newValue.orEmpty()의 두 하위 분기)는 테스트로 만들 수 없다.
    // buildCommentedCodeMessage()의 모든 return 경로는 reviewComment.contents(ReviewComment.kt의
    // `var contents: String = ""`, 널이 아닌 String)이거나 message.toString()이라 실제로 null을 반환하는
    // 경로가 코드상 존재하지 않는다. 함수 시그니처가 방어적으로 String?를 쓸 뿐, 구현이 null을 만들지 않으므로
    // 임의 판단이 아니라 소스 코드 근거로 확인된 도달 불가능 분기다(총 6개 미실행 분기 중 3개).
    describe("NEW_REVIEW_COMMENT (resolveReviewCommentMessage/buildCommentedCodeMessage)") {
        it("resourceId가 숫자가 아니면 newValue를 그대로 반환한다") {
            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "폴백", resourceId = "abc"), locale) shouldBe "폴백"
        }

        it("resourceId가 숫자가 아니고 newValue도 null이면(orEmpty의 null 분기) 빈 문자열을 반환한다") {
            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = null, resourceId = "abc"), locale) shouldBe ""
        }

        it("리뷰 댓글을 찾을 수 없으면 newValue를 그대로 반환한다") {
            every { reviewCommentRepository.findById(900L) } returns Optional.empty()
            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "폴백2", resourceId = "900"), locale) shouldBe "폴백2"
        }

        it("리뷰 댓글을 찾을 수 없고 newValue도 null이면(orEmpty의 null 분기) 빈 문자열을 반환한다") {
            every { reviewCommentRepository.findById(920L) } returns Optional.empty()
            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = null, resourceId = "920"), locale) shouldBe ""
        }

        it("thread가 없으면 댓글 내용을 그대로 반환한다") {
            val comment = ReviewComment(id = 901L, contents = "쓰레드 없는 댓글", thread = null)
            every { reviewCommentRepository.findById(901L) } returns Optional.of(comment)

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "901"), locale) shouldBe "쓰레드 없는 댓글"
        }

        it("스레드의 첫 댓글이 아니면 댓글 내용을 그대로 반환한다") {
            val thread = CodeCommentThread(id = 910L)
            val firstComment = ReviewComment(id = 902L, contents = "첫 댓글", createdDate = Instant.parse("2020-01-01T00:00:00Z"))
            val secondComment = ReviewComment(id = 903L, contents = "두번째 댓글", createdDate = Instant.parse("2020-01-02T00:00:00Z"))
            thread.addComment(firstComment)
            thread.addComment(secondComment)
            every { reviewCommentRepository.findById(903L) } returns Optional.of(secondComment)

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "903"), locale) shouldBe "두번째 댓글"
        }

        it("스레드에 댓글이 하나도 등록돼 있지 않아 getFirstReviewComment()가 예외를 던지면 바깥 catch에서 newValue로 폴백한다") {
            val thread = CodeCommentThread(id = 911L)
            // addComment()를 쓰지 않고 thread만 직접 연결해 reviewComments가 비어있는 상태를 재현한다.
            val orphanComment = ReviewComment(id = 904L, contents = "고아", thread = thread)
            every { reviewCommentRepository.findById(904L) } returns Optional.of(orphanComment)

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "예외 폴백", resourceId = "904"), locale) shouldBe "예외 폴백"
        }

        it("바깥 catch로 폴백하는 상황에서 newValue도 null이면(orEmpty의 null 분기) 빈 문자열을 반환한다") {
            val thread = CodeCommentThread(id = 919L)
            val orphanComment = ReviewComment(id = 912L, contents = "고아2", thread = thread)
            every { reviewCommentRepository.findById(912L) } returns Optional.of(orphanComment)

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = null, resourceId = "912"), locale) shouldBe ""
        }

        it("스레드가 CodeCommentThread가 아니면(SimpleCommentThread) 댓글 내용을 그대로 반환한다") {
            val thread = SimpleCommentThread(id = 912L)
            val comment = ReviewComment(id = 905L, contents = "단순 스레드 댓글")
            thread.addComment(comment)
            every { reviewCommentRepository.findById(905L) } returns Optional.of(comment)

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "905"), locale) shouldBe "단순 스레드 댓글"
        }

        it("스레드에 project가 없으면 댓글 내용을 그대로 반환한다") {
            val thread = CodeCommentThread(id = 913L, project = null)
            val comment = ReviewComment(id = 906L, contents = "프로젝트 없는 댓글")
            thread.addComment(comment)
            every { reviewCommentRepository.findById(906L) } returns Optional.of(comment)

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "906"), locale) shouldBe "프로젝트 없는 댓글"
        }

        it("리포지토리 조회 중 예외가 발생하면 댓글 내용을 그대로 반환한다") {
            val project = Project(id = 50L, name = "repoproj", owner = "own")
            val thread = CodeCommentThread(id = 914L, project = project)
            val comment = ReviewComment(id = 907L, contents = "리포 예외 댓글")
            thread.addComment(comment)
            every { reviewCommentRepository.findById(907L) } returns Optional.of(comment)
            every { repositoryService.getRepository(project) } throws RuntimeException("리포 접근 실패")

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "907"), locale) shouldBe "리포 예외 댓글"
        }

        it("commitId가 없으면 댓글 내용을 그대로 반환한다") {
            val project = Project(id = 51L, name = "nocommitproj", owner = "own")
            val thread = CodeCommentThread(id = 915L, project = project, commitId = null)
            val comment = ReviewComment(id = 908L, contents = "커밋 없는 댓글")
            thread.addComment(comment)
            every { reviewCommentRepository.findById(908L) } returns Optional.of(comment)
            every { repositoryService.getRepository(project) } returns mockk<PlayRepository>()

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "908"), locale) shouldBe "커밋 없는 댓글"
        }

        it("prevCommitId가 없으면(isBlank) commitId 하나만으로 diff를 조회하고, path가 일치하는 diff가 없으면 댓글 내용을 그대로 반환한다") {
            val project = Project(id = 52L, name = "diffproj", owner = "own")
            val thread = CodeCommentThread(
                id = 916L, project = project, commitId = "abc123", prevCommitId = "",
                codeRange = CodeRange(path = "src/Target.kt", endSide = CodeRange.Side.B, endLine = 5)
            )
            val comment = ReviewComment(id = 909L, contents = "매칭 안되는 댓글")
            thread.addComment(comment)
            every { reviewCommentRepository.findById(909L) } returns Optional.of(comment)

            val repo = mockk<PlayRepository>()
            every { repositoryService.getRepository(project) } returns repo
            val unrelatedDiff = FileDiff().apply { pathB = "src/Other.kt" }
            every { repo.getDiff("abc123") } returns listOf(unrelatedDiff)

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "909"), locale) shouldBe "매칭 안되는 댓글"
        }

        it("prevCommitId가 있으면 두 커밋 사이 diff를 조회하고, path가 일치하는 diff를 찾으면 안내 문구+댓글을 반환한다(hunks 없음)") {
            val project = Project(id = 53L, name = "diffproj2", owner = "own")
            val thread = CodeCommentThread(
                id = 917L, project = project, commitId = "def456", prevCommitId = "abc123",
                codeRange = CodeRange(path = "src/Target.kt", endSide = CodeRange.Side.B, endLine = 5)
            )
            val comment = ReviewComment(id = 910L, contents = "hunks 없는 댓글")
            thread.addComment(comment)
            every { reviewCommentRepository.findById(910L) } returns Optional.of(comment)

            val repo = mockk<PlayRepository>()
            every { repositoryService.getRepository(project) } returns repo
            val unrelatedDiff = FileDiff().apply { pathB = "src/Other.kt" }
            // editList/a/b를 설정하지 않아 getHunks()가 null을 반환하는 diff.
            val matchedDiff = FileDiff().apply { pathB = "src/Target.kt" }
            every { repo.getDiff("abc123", "def456") } returns listOf(unrelatedDiff, matchedDiff)

            resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "910"), locale) shouldBe
                "notification.reviewthread.inTheFile(src/Target.kt)\nhunks 없는 댓글"
        }

        it("hunks가 있으면 diff 코드블록을 만들고 codeRange가 가리키는 줄에 댓글을 삽입한다") {
            val project = Project(id = 54L, name = "diffproj3", owner = "own")
            val thread = CodeCommentThread(
                id = 918L, project = project, commitId = "def789", prevCommitId = "",
                codeRange = CodeRange(path = "src/Target.kt", endSide = CodeRange.Side.B, endLine = 5)
            )
            val comment = ReviewComment(id = 911L, contents = "인라인 삽입 댓글")
            thread.addComment(comment)
            every { reviewCommentRepository.findById(911L) } returns Optional.of(comment)

            val repo = mockk<PlayRepository>()
            every { repositoryService.getRepository(project) } returns repo

            // FileDiffSpec에서 이미 검증된, 10줄 파일의 5번째 줄만 바뀌는 단일 edit + context=3 조합을 그대로 사용한다.
            val matchedDiff = FileDiff().apply {
                pathA = "src/Target.kt"
                pathB = "src/Target.kt"
                a = RawText(((0..9).joinToString("\n") { "line$it" } + "\n").toByteArray())
                b = RawText(((0..9).joinToString("\n") { if (it == 5) "CHANGED" else "line$it" } + "\n").toByteArray())
                editList = EditList().apply { add(Edit(5, 6, 5, 6)) }
                context = 3
            }
            every { repo.getDiff("def789") } returns listOf(matchedDiff)

            val message = resolver.getMessage(event(EventType.NEW_REVIEW_COMMENT, newValue = "무시됨", resourceId = "911"), locale)

            message shouldContain "notification.reviewthread.inTheFile(src/Target.kt)"
            message shouldContain "```diff"
            message shouldContain ">  line4" // CONTEXT: "> " + " " + content
            message shouldContain "> -line5" // REMOVE: "> " + "-" + content
            message shouldContain "> +CHANGED" // ADD: "> " + "+" + content, endLine(5)과 일치해 댓글이 삽입되는 지점
            message shouldContain "인라인 삽입 댓글"
            message shouldContain ">  line6" // trailing CONTEXT
        }
    }
})
