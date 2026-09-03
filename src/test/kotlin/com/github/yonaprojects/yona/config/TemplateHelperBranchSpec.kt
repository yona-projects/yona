package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.SimpleCommentThread
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.support.ReviewSearchCondition
import com.github.yonaprojects.yona.domain.support.ReviewThreadService
import com.github.yonaprojects.yona.domain.user.FavoriteProject
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.MessageSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.time.ZoneId
import java.util.Optional

/**
 * TemplateHelper의 라인/분기 커버리지 보강용 순수 단위 테스트(mockk, Spring 컨텍스트 미기동).
 * ProjectServiceImplSpec 패턴을 따라 모든 리포지토리/서비스 의존성을 mockk로 대체하고
 * TemplateHelper를 직접 생성자 호출로 구성한다 — TemplateHelperSpec(통합테스트)과는 별개 파일.
 */
class TemplateHelperBranchSpec : DescribeSpec({

    val messageSource = mockk<MessageSource>()
    val watchRepository = mockk<WatchRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val issueRepository = mockk<IssueRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val postingRepository = mockk<PostingRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val issueLabelCategoryRepository = mockk<IssueLabelCategoryRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val reviewThreadService = mockk<ReviewThreadService>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val favoriteProjectRepository = mockk<FavoriteProjectRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()

    val templateHelper = TemplateHelper(
        messageSource,
        watchRepository,
        attachmentRepository,
        issueRepository,
        pullRequestRepository,
        postingRepository,
        projectUserRepository,
        issueLabelRepository,
        issueLabelCategoryRepository,
        milestoneRepository,
        reviewThreadService,
        organizationUserRepository,
        favoriteProjectRepository,
        commentThreadRepository
    )

    // messageSource 기본 스텁: 실제 messages.properties 내용과 무관하게, 어떤 키/인자로 호출됐는지를
    // 그대로 문자열로 되돌려줘 "어느 분기가 실행됐는지"를 결과 문자열로 검증할 수 있게 한다.
    every { messageSource.getMessage(any<String>(), any(), any<Locale>()) } answers {
        val code = firstArg<String>()
        val args = secondArg<Array<Any?>?>()
        if (args.isNullOrEmpty()) code else "$code:${args.joinToString(",")}"
    }
    // 4-arg(defaultMessage 포함) 오버로드 기본값: 별도로 every를 재정의하지 않는 한 defaultMessage를 그대로 반환.
    every { messageSource.getMessage(any<String>(), any(), any<String>(), any<Locale>()) } answers {
        thirdArg<String>()
    }

    fun project(id: Long? = 1L, owner: String? = "gildong", name: String = "myproject", vcs: String? = "GIT"): Project =
        Project(id = id, name = name, owner = owner, vcs = vcs)

    fun user(id: Long? = 1L, loginId: String = "user1", name: String = "사용자1", state: UserState = UserState.ACTIVE): User =
        User(id = id, name = name, loginId = loginId, email = "$loginId@yona.io", state = state)

    fun role(type: RoleType): Role = Role(id = type.roleType, name = type.name)

    fun organization(id: Long? = 1L, name: String = "org1"): Organization = Organization(id = id, name = name)

    afterEach {
        RequestContextHolder.resetRequestAttributes()
    }

    describe("agoOrDateString") {
        it("instant가 null이면 빈 문자열을 반환한다") {
            templateHelper.agoOrDateString(null) shouldBe ""
        }
        it("8일 미만이면 agoString() 결과(상대시간)를 반환한다") {
            val result = templateHelper.agoOrDateString(Instant.now().minusSeconds(3600))
            result shouldContain "common.time.hour"
        }
        it("8일 이상이고 올해면 MM-dd 형식으로 반환한다") {
            val result = templateHelper.agoOrDateString(Instant.now().minus(20, ChronoUnit.DAYS))
            result.length shouldBe 5
            result[2] shouldBe '-'
        }
        it("8일 이상이고 작년 이전이면 yyyy-MM-dd 형식으로 반환한다") {
            val result = templateHelper.agoOrDateString(Instant.now().atZone(ZoneId.systemDefault()).minusYears(2).toInstant())
            result.length shouldBe 10
        }
    }

    describe("agoString") {
        it("1일이면 단수 'common.time.day' 키를 사용한다") {
            templateHelper.agoString(Duration.ofDays(1)) shouldBe "common.time.day:1"
        }
        it("2일이면 복수 'common.time.days' 키를 사용한다") {
            templateHelper.agoString(Duration.ofDays(2)) shouldBe "common.time.days:2"
        }
        it("1시간이면 단수 'common.time.hour' 키를 사용한다") {
            templateHelper.agoString(Duration.ofSeconds(3661)) shouldBe "common.time.hour:1"
        }
        it("2시간이면 복수 'common.time.hours' 키를 사용한다") {
            templateHelper.agoString(Duration.ofSeconds(7200)) shouldBe "common.time.hours:2"
        }
        it("1분이면 단수 'common.time.minute' 키를 사용한다") {
            templateHelper.agoString(Duration.ofSeconds(90)) shouldBe "common.time.minute:1"
        }
        it("2분이면 복수 'common.time.minutes' 키를 사용한다") {
            templateHelper.agoString(Duration.ofSeconds(150)) shouldBe "common.time.minutes:2"
        }
        it("1초면 단수 'common.time.second' 키를 사용한다") {
            templateHelper.agoString(Duration.ofSeconds(1)) shouldBe "common.time.second:1"
        }
        it("5초면 복수 'common.time.seconds' 키를 사용한다") {
            templateHelper.agoString(Duration.ofSeconds(5)) shouldBe "common.time.seconds:5"
        }
        it("0초면 'common.time.just'를 사용한다") {
            templateHelper.agoString(Duration.ZERO) shouldBe "common.time.just"
        }
    }

    describe("getWatchingCount") {
        it("projectId가 null이면 0을 반환한다") {
            templateHelper.getWatchingCount(null) shouldBe 0L
        }
        it("projectId가 있으면 repository 결과를 반환한다") {
            every { watchRepository.countByResourceTypeAndResourceId(ResourceType.PROJECT, "10") } returns 3L
            templateHelper.getWatchingCount(10L) shouldBe 3L
        }
    }

    describe("hasProjectLogo") {
        it("projectId가 null이면 false") {
            templateHelper.hasProjectLogo(null) shouldBe false
        }
        it("첨부파일이 없으면 false") {
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PROJECT, "1") } returns emptyList()
            templateHelper.hasProjectLogo(1L) shouldBe false
        }
        it("첨부파일이 있으면 true") {
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PROJECT, "2") } returns listOf(Attachment())
            templateHelper.hasProjectLogo(2L) shouldBe true
        }
    }

    describe("showHeaderWordsInBracketsIfExist") {
        it("title이 null이면 빈 문자열") {
            templateHelper.showHeaderWordsInBracketsIfExist(null) shouldBe ""
        }
        it("title이 빈 문자열이면 빈 문자열") {
            templateHelper.showHeaderWordsInBracketsIfExist("") shouldBe ""
        }
        it("[..] 패턴이 앞에 있으면 매치된 값을 반환") {
            templateHelper.showHeaderWordsInBracketsIfExist("[공지] 제목") shouldBe "[공지]"
        }
        it("[..] 패턴이 없으면 빈 문자열") {
            templateHelper.showHeaderWordsInBracketsIfExist("그냥 제목") shouldBe ""
        }
    }

    describe("removeHeaderWords") {
        it("title이 null이면 빈 문자열") {
            templateHelper.removeHeaderWords(null) shouldBe ""
        }
        it("title이 빈 문자열이면 빈 문자열") {
            templateHelper.removeHeaderWords("") shouldBe ""
        }
        it("[..] 접두어가 있으면 제거") {
            templateHelper.removeHeaderWords("[공지] 제목") shouldBe "제목"
        }
        it("[..] 접두어가 없으면 그대로 반환") {
            templateHelper.removeHeaderWords("그냥 제목") shouldBe "그냥 제목"
        }
    }

    describe("titleMain") {
        it("title이 null이면 빈 문자열") {
            templateHelper.titleMain(null) shouldBe ""
        }
        it("title이 빈 문자열이면 빈 문자열") {
            templateHelper.titleMain("") shouldBe ""
        }
        it("구분자( |:| )가 있으면 첫 조각을 반환") {
            templateHelper.titleMain("메인제목 |:| 부제목") shouldBe "메인제목"
        }
        it("구분자가 없으면 전체를 반환") {
            templateHelper.titleMain("단일제목") shouldBe "단일제목"
        }
    }

    describe("titleOgDescription") {
        it("title이 null이면 빈 문자열") {
            templateHelper.titleOgDescription(null) shouldBe ""
        }
        it("title이 빈 문자열이면 빈 문자열") {
            templateHelper.titleOgDescription("") shouldBe ""
        }
        it("구분자가 있으면 마지막 조각을 반환") {
            templateHelper.titleOgDescription("메인제목 |:| 부제목") shouldBe "부제목"
        }
        it("구분자가 없으면 전체를 반환") {
            templateHelper.titleOgDescription("단일제목") shouldBe "단일제목"
        }
    }

    describe("ogDescriptionPreview") {
        it("body가 null이면 빈 문자열") {
            templateHelper.ogDescriptionPreview(null) shouldBe ""
        }
        it("body가 빈 문자열이면 빈 문자열") {
            templateHelper.ogDescriptionPreview("") shouldBe ""
        }
        it("body가 기본 maxLen(200)보다 짧으면 전체 반환") {
            templateHelper.ogDescriptionPreview("짧은 본문") shouldBe "짧은 본문"
        }
        it("body가 커스텀 maxLen보다 길면 잘라서 반환") {
            templateHelper.ogDescriptionPreview("0123456789", 5) shouldBe "01234"
        }
        it("body 길이가 maxLen과 정확히 같으면 전체 반환") {
            templateHelper.ogDescriptionPreview("01234", 5) shouldBe "01234"
        }
    }

    describe("hasChildIssue") {
        it("issue.id가 null이면 false") {
            val p = project()
            val issue = Issue(id = null, project = p)
            templateHelper.hasChildIssue(issue) shouldBe false
        }
        it("자식 이슈 수가 0보다 크면 true") {
            val p = project()
            val issue = Issue(id = 5L, project = p)
            every { issueRepository.countByParentId(5L) } returns 2L
            templateHelper.hasChildIssue(issue) shouldBe true
        }
        it("자식 이슈 수가 0이면 false") {
            val p = project()
            val issue = Issue(id = 6L, project = p)
            every { issueRepository.countByParentId(6L) } returns 0L
            templateHelper.hasChildIssue(issue) shouldBe false
        }
    }

    describe("notFoundActiveMenu") {
        it("issue_post -> issue") { templateHelper.notFoundActiveMenu("issue_post") shouldBe "issue" }
        it("board_post -> board") { templateHelper.notFoundActiveMenu("board_post") shouldBe "board" }
        it("milestone -> milestone") { templateHelper.notFoundActiveMenu("milestone") shouldBe "milestone" }
        it("code -> code") { templateHelper.notFoundActiveMenu("code") shouldBe "code" }
        it("알 수 없는 값 -> 빈 문자열") { templateHelper.notFoundActiveMenu("unknown") shouldBe "" }
        it("null -> 빈 문자열") { templateHelper.notFoundActiveMenu(null) shouldBe "" }
    }

    describe("notFoundReturnUrl") {
        val p = project(owner = "gildong", name = "proj1")
        it("issue_post -> 이슈 목록") {
            templateHelper.notFoundReturnUrl(p, "issue_post") shouldBe "/gildong/proj1/issues?state=all"
        }
        it("board_post -> 게시판 목록") {
            templateHelper.notFoundReturnUrl(p, "board_post") shouldBe "/gildong/proj1/posts"
        }
        it("milestone -> 마일스톤 목록") {
            templateHelper.notFoundReturnUrl(p, "milestone") shouldBe "/gildong/proj1/milestones"
        }
        it("code -> 프로젝트 설정") {
            templateHelper.notFoundReturnUrl(p, "code") shouldBe "/gildong/proj1/setting"
        }
        it("알 수 없는 값 -> 뒤로가기") {
            templateHelper.notFoundReturnUrl(p, "unknown") shouldBe "javascript:history.back();"
        }
        it("null -> 뒤로가기") {
            templateHelper.notFoundReturnUrl(p, null) shouldBe "javascript:history.back();"
        }
    }

    describe("notFoundMessage") {
        it("targetType이 null이면 error.notfound 기본 메시지") {
            templateHelper.notFoundMessage("아무title", null) shouldBe "error.notfound"
        }
        it("targetType이 빈 문자열이면 error.notfound 기본 메시지") {
            templateHelper.notFoundMessage("아무title", "") shouldBe "error.notfound"
        }
        it("targetType에 대한 메시지가 존재하면 그 메시지를 반환한다(fallback 아님)") {
            every {
                messageSource.getMessage("error.notfound.known", any(), any<String>(), any())
            } returns "브랜치 title을 찾을 수 없습니다"
            templateHelper.notFoundMessage("title", "known") shouldBe "브랜치 title을 찾을 수 없습니다"
        }
        it("targetType에 대한 메시지가 없으면(null 반환) fallback(error.notfound)으로 대체한다") {
            every {
                messageSource.getMessage("error.notfound.unresolvable", any(), any<String>(), any())
            } returns null
            templateHelper.notFoundMessage("title", "unresolvable") shouldBe "error.notfound"
        }
    }

    describe("countByParentIssueIdAndState / findByParentId / findByParentIdAndState (위임 메서드, 메서드 커버리지용)") {
        it("countByParentIssueIdAndState는 repository에 위임한다") {
            every { issueRepository.countByParentIdAndState(1L, State.OPEN) } returns 4L
            templateHelper.countByParentIssueIdAndState(1L, State.OPEN) shouldBe 4L
        }
        it("findByParentId는 repository에 위임한다") {
            val p = project()
            val child = Issue(id = 2L, project = p)
            every { issueRepository.findByParentId(1L) } returns listOf(child)
            templateHelper.findByParentId(1L) shouldBe listOf(child)
        }
        it("findByParentIdAndState는 repository에 위임한다") {
            val p = project()
            val child = Issue(id = 3L, project = p)
            every { issueRepository.findByParentIdAndState(1L, State.CLOSED) } returns listOf(child)
            templateHelper.findByParentIdAndState(1L, State.CLOSED) shouldBe listOf(child)
        }
    }

    describe("getPercent / getPercentFormatted") {
        it("분모가 0이면 0.0을 반환한다") {
            templateHelper.getPercent(5.0, 0.0) shouldBe 0.0
        }
        it("분모가 0이 아니면 백분율을 계산한다") {
            templateHelper.getPercent(50.0, 200.0) shouldBe 25.0
        }
        it("getPercentFormatted는 legacy와 동일하게 절삭(truncate)한다") {
            // 2/(2+1)*100 = 66.66... -> 절삭 66 (반올림이면 67이 되어 legacy와 달라짐)
            templateHelper.getPercentFormatted(2L, 1L) shouldBe "66"
        }
    }

    describe("isOverDueDate") {
        val p = project()
        it("dueDate가 null이면 false") {
            templateHelper.isOverDueDate(Issue(project = p, dueDate = null)) shouldBe false
        }
        it("OPEN 상태이고 마감일이 지났으면 true") {
            templateHelper.isOverDueDate(
                Issue(project = p, state = State.OPEN, dueDate = Instant.now().minus(1, ChronoUnit.DAYS))
            ) shouldBe true
        }
        it("OPEN 상태이고 마감일이 남았으면 false") {
            templateHelper.isOverDueDate(
                Issue(project = p, state = State.OPEN, dueDate = Instant.now().plus(1, ChronoUnit.DAYS))
            ) shouldBe false
        }
        it("OPEN이 아니면(CLOSED) 마감일이 지났어도 false") {
            templateHelper.isOverDueDate(
                Issue(project = p, state = State.CLOSED, dueDate = Instant.now().minus(1, ChronoUnit.DAYS))
            ) shouldBe false
        }
    }

    describe("until(Issue)") {
        val p = project()
        it("dueDate가 null이면 빈 문자열") {
            templateHelper.until(Issue(project = p, dueDate = null)) shouldBe ""
        }
        it("오늘이 마감일이면 common.time.today") {
            templateHelper.until(Issue(project = p, dueDate = Instant.now())) shouldBe "common.time.today"
        }
        it("마감일이 남았으면 common.time.default.day(양수)") {
            val result = templateHelper.until(Issue(project = p, dueDate = Instant.now().plus(3, ChronoUnit.DAYS)))
            result shouldContain "common.time.default.day"
        }
        it("마감일이 지났으면 common.time.default.day(음수를 양수로 변환)") {
            val result = templateHelper.until(Issue(project = p, dueDate = Instant.now().minus(3, ChronoUnit.DAYS)))
            result shouldContain "common.time.default.day"
        }
    }

    describe("getDueDateString / getUserSinceDateString / getDateString") {
        it("getDueDateString: null이면 빈 문자열") {
            templateHelper.getDueDateString(null) shouldBe ""
        }
        it("getDueDateString: 값이 있으면 yyyy-MM-dd 형식") {
            templateHelper.getDueDateString(Instant.now()).length shouldBe 10
        }
        it("getUserSinceDateString: null이면 빈 문자열") {
            templateHelper.getUserSinceDateString(null) shouldBe ""
        }
        it("getUserSinceDateString: 값이 있으면 'MMM dd, yyyy'(Locale.US) 형식") {
            templateHelper.getUserSinceDateString(Instant.now()).shouldContain(",")
        }
        it("getDateString: null이면 빈 문자열") {
            templateHelper.getDateString(null) shouldBe ""
        }
        it("getDateString: 값이 있으면 포맷된 문자열") {
            templateHelper.getDateString(Instant.now()).isNotEmpty() shouldBe true
        }
    }

    describe("until(Milestone)") {
        val p = project()
        it("dueDate가 null이면 빈 문자열") {
            templateHelper.until(Milestone(project = p, dueDate = null)) shouldBe ""
        }
        it("오늘이 마감일이면 common.time.today") {
            templateHelper.until(Milestone(project = p, dueDate = Instant.now())) shouldBe "common.time.today"
        }
        it("마감일이 지났으면 common.time.overday") {
            val result = templateHelper.until(Milestone(project = p, dueDate = Instant.now().minus(3, ChronoUnit.DAYS)))
            result shouldContain "common.time.overday"
        }
        it("마감일이 남았으면 common.time.leftday") {
            val result = templateHelper.until(Milestone(project = p, dueDate = Instant.now().plus(3, ChronoUnit.DAYS)))
            result shouldContain "common.time.leftday"
        }
    }

    describe("urlToCommentThread") {
        it("PR 리뷰 스레드면 PR 화면 링크") {
            val toP = project(id = 1L, owner = "own", name = "proj")
            val fromP = project(id = 2L, owner = "own2", name = "proj2")
            val contributor = user(id = 9L, loginId = "contrib")
            val pr = PullRequest(id = 1L, toProject = toP, fromProject = fromP, contributor = contributor, number = 7L)
            val thread = SimpleCommentThread(id = 100L, pullRequest = pr)
            templateHelper.urlToCommentThread(thread) shouldBe "/own/proj/pull/7#thread-100"
        }
        it("커밋 리뷰 스레드(프로젝트 있음)면 커밋 화면 링크") {
            val p = project(id = 3L, owner = "own3", name = "proj3")
            val thread = SimpleCommentThread(id = 101L, pullRequest = null, project = p, commitId = "abc123")
            templateHelper.urlToCommentThread(thread) shouldBe "/own3/proj3/commit/abc123#thread-101"
        }
        it("PR도 project도 없으면 컨테이너 없이 앵커만") {
            val thread = SimpleCommentThread(id = 102L, pullRequest = null, project = null)
            templateHelper.urlToCommentThread(thread) shouldBe "#thread-102"
        }
    }

    describe("getIssueLabelsString") {
        it("labels가 null이면 빈 문자열") {
            templateHelper.getIssueLabelsString(null) shouldBe ""
        }
        it("labels가 비어있으면 빈 문자열") {
            templateHelper.getIssueLabelsString(emptySet()) shouldBe ""
        }
        it("labels가 있으면 카테고리명/이름순 정렬 후 '|'로 join") {
            val p = project()
            val catB = IssueLabelCategory(id = 1L, name = "B카테고리", project = p, isExclusive = true)
            val catA = IssueLabelCategory(id = 2L, name = "A카테고리", project = p, isExclusive = false)
            val label1 = IssueLabel(id = 10L, category = catB, project = p, name = "라벨1")
            val label2 = IssueLabel(id = 11L, category = catA, project = p, name = "라벨2")
            val result = templateHelper.getIssueLabelsString(setOf(label1, label2))
            // A카테고리가 먼저 정렬돼야 함 ($categoryName,$id(라벨 id),$name,$categoryId,$isExclusive)
            result shouldBe "A카테고리,11,라벨2,2,false|B카테고리,10,라벨1,1,true"
        }
    }

    describe("countIssues / countPullRequests / countBoardPosts / countReviews (위임 메서드)") {
        val p = project()
        it("countIssues는 issueRepository에 위임") {
            every { issueRepository.countByProjectAndState(p, State.OPEN) } returns 3L
            templateHelper.countIssues(p) shouldBe 3L
        }
        it("countPullRequests는 pullRequestRepository에 위임") {
            every { pullRequestRepository.countByToProjectAndState(p, State.OPEN) } returns 2L
            templateHelper.countPullRequests(p) shouldBe 2L
        }
        it("countBoardPosts는 postingRepository에 위임") {
            every { postingRepository.countByProject(p) } returns 5L
            templateHelper.countBoardPosts(p) shouldBe 5L
        }
        it("countReviews는 reviewThreadService에 위임") {
            every { reviewThreadService.countReviewThreads(p, any<ReviewSearchCondition>()) } returns 1L
            templateHelper.countReviews(p) shouldBe 1L
        }
    }

    describe("isOrganizationMemberOrAdmin") {
        it("org가 null이면 false") {
            templateHelper.isOrganizationMemberOrAdmin(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isOrganizationMemberOrAdmin(organization(), null) shouldBe false
        }
        it("가입 관계가 있으면 true") {
            every { organizationUserRepository.existsByOrganizationIdAndUserId(1L, 1L) } returns true
            templateHelper.isOrganizationMemberOrAdmin(organization(1L), user(1L)) shouldBe true
        }
        it("가입 관계가 없으면 false") {
            every { organizationUserRepository.existsByOrganizationIdAndUserId(1L, 2L) } returns false
            templateHelper.isOrganizationMemberOrAdmin(organization(1L), user(2L)) shouldBe false
        }
    }

    describe("isFavoriteProject") {
        it("project가 null이면 false") {
            templateHelper.isFavoriteProject(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isFavoriteProject(project(), null) shouldBe false
        }
        it("즐겨찾기가 있으면 true") {
            every { favoriteProjectRepository.findByUserIdAndProjectId(1L, 1L) } returns Optional.of(mockk<FavoriteProject>())
            templateHelper.isFavoriteProject(project(1L), user(1L)) shouldBe true
        }
        it("즐겨찾기가 없으면 false") {
            every { favoriteProjectRepository.findByUserIdAndProjectId(2L, 1L) } returns Optional.empty()
            templateHelper.isFavoriteProject(project(1L), user(2L)) shouldBe false
        }
    }

    describe("getVotersExceptCurrentUser") {
        val u1 = user(1L, "u1")
        val u2 = user(2L, "u2")
        it("currentUser가 null이면 전체 목록을 반환") {
            templateHelper.getVotersExceptCurrentUser(listOf(u1, u2), null) shouldBe listOf(u1, u2)
        }
        it("currentUser가 목록에 있으면 제외하고 반환") {
            templateHelper.getVotersExceptCurrentUser(listOf(u1, u2), u1) shouldBe listOf(u2)
        }
        it("currentUser가 목록에 없으면 전체가 그대로 남는다") {
            val other = user(3L, "u3")
            templateHelper.getVotersExceptCurrentUser(listOf(u1, u2), other) shouldBe listOf(u1, u2)
        }
    }

    describe("getVotersForAvatar (위임/슬라이스 메서드)") {
        it("size만큼 앞에서 잘라 반환한다") {
            val voters = listOf(user(1L, "u1"), user(2L, "u2"), user(3L, "u3"))
            templateHelper.getVotersForAvatar(voters, 2) shouldBe listOf(voters[0], voters[1])
        }
    }

    describe("getVotersForName") {
        val voters = listOf(user(1L, "u1"), user(2L, "u2"), user(3L, "u3"))
        it("정상 범위면 fromIndex부터 size개를 반환") {
            templateHelper.getVotersForName(voters, 1, 2) shouldBe listOf(voters[1], voters[2])
        }
        it("fromIndex가 목록 크기 이상이면 빈 리스트") {
            templateHelper.getVotersForName(voters, 10, 2) shouldBe emptyList()
        }
        it("fromIndex가 음수면 시작 위치만 0으로 clamp되어 처리된다(끝 위치는 fromIndex+size 그대로 계산)") {
            // start = max(0, -1) = 0, end = min(3, -1+3) = 2
            templateHelper.getVotersForName(voters, -1, 3) shouldBe listOf(voters[0], voters[1])
        }
    }

    describe("getVotersTooltip") {
        it("남은 인원이 없으면(hasMore=false) 이름 목록만 반환") {
            val voters = listOf(user(1L, "u1", "홍길동"), user(2L, "u2", "김철수"))
            templateHelper.getVotersTooltip(voters, 0, 2) shouldBe "홍길동<br>김철수"
        }
        it("남은 인원이 있으면(hasMore=true) 말줄임표를 덧붙인다") {
            val voters = listOf(user(1L, "u1", "홍길동"), user(2L, "u2", "김철수"), user(3L, "u3", "이영희"))
            templateHelper.getVotersTooltip(voters, 0, 2) shouldBe "홍길동<br>김철수<br>&hellip;"
        }
    }

    describe("isMember") {
        it("project가 null이면 false") {
            templateHelper.isMember(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isMember(project(), null) shouldBe false
        }
        it("project.id가 null이면 false") {
            templateHelper.isMember(project(id = null), user(1L)) shouldBe false
        }
        it("user.id가 null이면 false") {
            templateHelper.isMember(project(1L), user(id = null)) shouldBe false
        }
        it("가입돼 있으면 true") {
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 1L) } returns true
            templateHelper.isMember(project(1L), user(1L)) shouldBe true
        }
        it("가입돼 있지 않으면 false") {
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 2L) } returns false
            templateHelper.isMember(project(1L), user(2L)) shouldBe false
        }
    }

    describe("isManager") {
        it("project가 null이면 false") {
            templateHelper.isManager(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isManager(project(), null) shouldBe false
        }
        it("project.id가 null이면 false") {
            templateHelper.isManager(project(id = null), user(1L)) shouldBe false
        }
        it("user.id가 null이면 false") {
            templateHelper.isManager(project(1L), user(id = null)) shouldBe false
        }
        it("조회 결과가 없으면(Optional.empty) false") {
            every { projectUserRepository.findByProjectIdAndUserId(1L, 3L) } returns Optional.empty()
            templateHelper.isManager(project(1L), user(3L)) shouldBe false
        }
        it("MANAGER 역할이면 true") {
            val p = project(1L)
            val u = user(1L)
            every { projectUserRepository.findByProjectIdAndUserId(1L, 1L) } returns
                Optional.of(ProjectUser(project = p, user = u, role = role(RoleType.MANAGER)))
            templateHelper.isManager(p, u) shouldBe true
        }
        it("MANAGER가 아닌 역할이면 false") {
            val p = project(1L)
            val u = user(2L)
            every { projectUserRepository.findByProjectIdAndUserId(1L, 2L) } returns
                Optional.of(ProjectUser(project = p, user = u, role = role(RoleType.MEMBER)))
            templateHelper.isManager(p, u) shouldBe false
        }
    }

    describe("isEnrolled") {
        it("project가 null이면 false") {
            templateHelper.isEnrolled(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isEnrolled(project(), null) shouldBe false
        }
        it("enrolledProjects에 포함돼 있으면 true") {
            val p = project(1L)
            val u = user(1L)
            u.enrolledProjects.add(p)
            templateHelper.isEnrolled(p, u) shouldBe true
        }
        it("enrolledProjects에 없으면 false") {
            val p = project(1L)
            val u = user(1L)
            u.enrolledProjects.add(project(2L))
            templateHelper.isEnrolled(p, u) shouldBe false
        }
    }

    describe("getLabelCategories / getLabelsByCategory / getProjectLabels") {
        it("getLabelCategories: project가 null이면 빈 리스트") {
            templateHelper.getLabelCategories(null) shouldBe emptyList()
        }
        it("getLabelCategories: project가 있으면 repository 결과") {
            val p = project()
            val cat = IssueLabelCategory(id = 1L, project = p)
            every { issueLabelCategoryRepository.findByProject(p) } returns listOf(cat)
            templateHelper.getLabelCategories(p) shouldBe listOf(cat)
        }
        it("getLabelsByCategory: category가 null이면 빈 리스트") {
            templateHelper.getLabelsByCategory(null) shouldBe emptyList()
        }
        it("getLabelsByCategory: category가 있으면 repository 결과") {
            val p = project()
            val cat = IssueLabelCategory(id = 1L, project = p)
            val label = IssueLabel(id = 1L, category = cat, project = p)
            every { issueLabelRepository.findByCategory(cat) } returns listOf(label)
            templateHelper.getLabelsByCategory(cat) shouldBe listOf(label)
        }
        it("getProjectLabels: project가 null이면 빈 리스트") {
            templateHelper.getProjectLabels(null) shouldBe emptyList()
        }
        it("getProjectLabels: project가 있으면 repository 결과") {
            val p = project()
            val cat = IssueLabelCategory(id = 1L, project = p)
            val label = IssueLabel(id = 1L, category = cat, project = p)
            every { issueLabelRepository.findByProject(p) } returns listOf(label)
            templateHelper.getProjectLabels(p) shouldBe listOf(label)
        }
    }

    describe("canBeDeleted") {
        val p = project()
        it("issue가 null이면 false") {
            templateHelper.canBeDeleted(null, emptyList()) shouldBe false
        }
        it("comments가 null이면 true") {
            templateHelper.canBeDeleted(Issue(project = p, authorLoginId = "author1"), null) shouldBe true
        }
        it("comments가 비어있으면 true") {
            templateHelper.canBeDeleted(Issue(project = p, authorLoginId = "author1"), emptyList()) shouldBe true
        }
        it("모든 댓글이 이슈 작성자 본인이면 true") {
            val issue = Issue(project = p, authorLoginId = "author1")
            val comments = listOf(IssueComment(issue = issue, authorLoginId = "author1"))
            templateHelper.canBeDeleted(issue, comments) shouldBe true
        }
        it("작성자가 아닌 댓글이 하나라도 있으면 false") {
            val issue = Issue(project = p, authorLoginId = "author1")
            val comments = listOf(
                IssueComment(issue = issue, authorLoginId = "author1"),
                IssueComment(issue = issue, authorLoginId = "other")
            )
            templateHelper.canBeDeleted(issue, comments) shouldBe false
        }
    }

    describe("getAssignableUsers") {
        it("project가 null이면 빈 리스트") {
            templateHelper.getAssignableUsers(null) shouldBe emptyList()
        }
        it("project.id가 null이면 빈 리스트") {
            templateHelper.getAssignableUsers(project(id = null)) shouldBe emptyList()
        }
        it("project가 있으면 소속 사용자 목록을 반환") {
            val p = project(1L)
            val u = user(1L)
            every { projectUserRepository.findByProjectId(1L) } returns listOf(ProjectUser(project = p, user = u, role = role(RoleType.MEMBER)))
            templateHelper.getAssignableUsers(p) shouldBe listOf(u)
        }
    }

    describe("getOpenMilestones") {
        it("project가 null이면 빈 리스트") {
            templateHelper.getOpenMilestones(null) shouldBe emptyList()
        }
        it("project가 있으면 OPEN 마일스톤 목록") {
            val p = project()
            val m = Milestone(id = 1L, project = p, state = State.OPEN)
            every { milestoneRepository.findByProjectAndState(p, State.OPEN) } returns listOf(m)
            templateHelper.getOpenMilestones(p) shouldBe listOf(m)
        }
    }

    describe("getMilestoneProgress") {
        val p = project()
        it("이슈가 없고 마감일이 없으면 진행률 0, 연체 아님") {
            val m = Milestone(id = 1L, project = p, dueDate = null)
            every { issueRepository.findByMilestone(m) } returns emptyList()
            val progress = templateHelper.getMilestoneProgress(m)
            progress.openCount shouldBe 0
            progress.closedCount shouldBe 0
            progress.completionRate shouldBe 0
            progress.isOverdue shouldBe false
        }
        it("이슈가 섞여 있고 마감일이 지났으면 진행률>0, 연체=true") {
            val m = Milestone(id = 2L, project = p, dueDate = Instant.now().minus(1, ChronoUnit.DAYS))
            every { issueRepository.findByMilestone(m) } returns listOf(
                Issue(project = p, state = State.OPEN),
                Issue(project = p, state = State.CLOSED),
                Issue(project = p, state = State.CLOSED)
            )
            val progress = templateHelper.getMilestoneProgress(m)
            progress.openCount shouldBe 1
            progress.closedCount shouldBe 2
            progress.completionRate shouldBe 66
            progress.isOverdue shouldBe true
        }
        it("마감일이 남아있으면 연체=false") {
            val m = Milestone(id = 3L, project = p, dueDate = Instant.now().plus(1, ChronoUnit.DAYS))
            every { issueRepository.findByMilestone(m) } returns emptyList()
            templateHelper.getMilestoneProgress(m).isOverdue shouldBe false
        }
    }

    describe("isMac") {
        it("요청 컨텍스트가 없으면 false") {
            templateHelper.isMac() shouldBe false
        }
        it("User-Agent 헤더가 없으면 false") {
            val request = MockHttpServletRequest()
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            templateHelper.isMac() shouldBe false
        }
        it("User-Agent에 Macintosh가 포함되면 true") {
            val request = MockHttpServletRequest()
            request.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)")
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            templateHelper.isMac() shouldBe true
        }
        it("User-Agent에 Macintosh가 없으면 false") {
            val request = MockHttpServletRequest()
            request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            templateHelper.isMac() shouldBe false
        }
    }

    describe("branchItemName") {
        it("refs/heads/master 형태면 마지막 세그먼트만 반환") {
            templateHelper.branchItemName("refs/heads/master") shouldBe "master"
        }
        it("이미 짧은 이름이면(슬래시 없음) 그대로 반환") {
            templateHelper.branchItemName("master") shouldBe "master"
        }
        it("3조각이지만 refs로 시작하지 않으면 그대로 반환") {
            templateHelper.branchItemName("foo/bar/baz") shouldBe "foo/bar/baz"
        }
    }

    describe("branchItemType") {
        it("refs/heads/* -> branch") {
            templateHelper.branchItemType("refs/heads/master") shouldBe "branch"
        }
        it("refs/tags/* -> tag") {
            templateHelper.branchItemType("refs/tags/v1") shouldBe "tag"
        }
        it("refs/기타/* -> 두번째 세그먼트 그대로") {
            templateHelper.branchItemType("refs/notes/x") shouldBe "notes"
        }
        it("슬래시 없는 짧은 이름 -> 그대로 반환") {
            templateHelper.branchItemType("master") shouldBe "master"
        }
        it("refs로 시작하지 않는 2조각 -> 그대로 반환") {
            templateHelper.branchItemType("foo/bar") shouldBe "foo/bar"
        }
    }

    describe("branchInHtml") {
        it("refs/heads/xxx(3조각 이상)면 라벨 span을 붙인다") {
            templateHelper.branchInHtml("refs/heads/master") shouldBe
                "<span class=\"label branch\">branch</span>master"
        }
        it("refs/tags/xxx면 tag 라벨을 붙인다") {
            templateHelper.branchInHtml("refs/tags/v1") shouldBe
                "<span class=\"label tag\">tag</span>v1"
        }
        it("refs로 시작하지만 2조각뿐이면(3조각 미만) 그대로 반환") {
            templateHelper.branchInHtml("refs/heads") shouldBe "refs/heads"
        }
        it("refs로 시작하지 않으면 그대로 반환") {
            templateHelper.branchInHtml("master") shouldBe "master"
        }
    }

    describe("isOrganizationAdmin") {
        it("org가 null이면 false") {
            templateHelper.isOrganizationAdmin(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isOrganizationAdmin(organization(), null) shouldBe false
        }
        it("organizationUsers가 비어있으면 false") {
            templateHelper.isOrganizationAdmin(organization(1L), user(1L)) shouldBe false
        }
    }

    describe("isOrganizationAdmin 상세 분기") {
        it("같은 user가 ORG_ADMIN 역할이면 true") {
            val org = organization(1L)
            val u = user(1L)
            org.organizationUsers.add(OrganizationUser(user = u, organization = org, role = role(RoleType.ORG_ADMIN)))
            templateHelper.isOrganizationAdmin(org, u) shouldBe true
        }
        it("같은 user지만 ORG_ADMIN이 아니면 false") {
            val org = organization(1L)
            val u = user(1L)
            org.organizationUsers.add(OrganizationUser(user = u, organization = org, role = role(RoleType.ORG_MEMBER)))
            templateHelper.isOrganizationAdmin(org, u) shouldBe false
        }
        it("다른 user가 ORG_ADMIN이면(본인 아님) false") {
            val org = organization(1L)
            val u = user(1L)
            val other = user(2L, "other")
            org.organizationUsers.add(OrganizationUser(user = other, organization = org, role = role(RoleType.ORG_ADMIN)))
            templateHelper.isOrganizationAdmin(org, u) shouldBe false
        }
    }

    describe("isOrganizationMember") {
        it("org가 null이면 false") {
            templateHelper.isOrganizationMember(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isOrganizationMember(organization(), null) shouldBe false
        }
        it("같은 user가 ORG_MEMBER 역할이면 true") {
            val org = organization(1L)
            val u = user(1L)
            org.organizationUsers.add(OrganizationUser(user = u, organization = org, role = role(RoleType.ORG_MEMBER)))
            templateHelper.isOrganizationMember(org, u) shouldBe true
        }
        it("같은 user지만 ORG_MEMBER가 아니면 false") {
            val org = organization(1L)
            val u = user(1L)
            org.organizationUsers.add(OrganizationUser(user = u, organization = org, role = role(RoleType.ORG_ADMIN)))
            templateHelper.isOrganizationMember(org, u) shouldBe false
        }
        it("다른 user가 ORG_MEMBER면(본인 아님) false") {
            val org = organization(1L)
            val u = user(1L)
            val other = user(2L, "other")
            org.organizationUsers.add(OrganizationUser(user = other, organization = org, role = role(RoleType.ORG_MEMBER)))
            templateHelper.isOrganizationMember(org, u) shouldBe false
        }
    }

    describe("isOrganizationGuest") {
        it("org가 null이면 false") {
            templateHelper.isOrganizationGuest(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isOrganizationGuest(organization(), null) shouldBe false
        }
        it("사이트매니저면 false") {
            val siteManager = user(1L, state = UserState.SITE_ADMIN)
            templateHelper.isOrganizationGuest(organization(), siteManager) shouldBe false
        }
        it("조직에 속하지 않은 일반 사용자는 게스트(true)") {
            val org = organization(1L)
            val u = user(1L)
            templateHelper.isOrganizationGuest(org, u) shouldBe true
        }
        it("이미 조직 구성원이면 게스트가 아니다(false)") {
            val org = organization(1L)
            val u = user(1L)
            org.organizationUsers.add(OrganizationUser(user = u, organization = org, role = role(RoleType.ORG_MEMBER)))
            templateHelper.isOrganizationGuest(org, u) shouldBe false
        }
    }

    describe("isEnrolledOrganization") {
        it("org가 null이면 false") {
            templateHelper.isEnrolledOrganization(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isEnrolledOrganization(organization(), null) shouldBe false
        }
        it("enrolledOrganizations에 포함되면 true") {
            val org = organization(1L)
            val u = user(1L)
            u.enrolledOrganizations.add(org)
            templateHelper.isEnrolledOrganization(org, u) shouldBe true
        }
        it("enrolledOrganizations에 없으면 false") {
            val org = organization(1L)
            val u = user(1L)
            u.enrolledOrganizations.add(organization(2L, "other-org"))
            templateHelper.isEnrolledOrganization(org, u) shouldBe false
        }
    }

    describe("getCommentThreads (위임 메서드)") {
        it("commentThreadRepository에 위임한다") {
            val toP = project(1L, "own", "proj")
            val fromP = project(2L, "own2", "proj2")
            val contributor = user(9L, "contrib")
            val pr = PullRequest(id = 1L, toProject = toP, fromProject = fromP, contributor = contributor)
            val thread = SimpleCommentThread(id = 1L, pullRequest = pr)
            every { commentThreadRepository.findByPullRequest(pr) } returns listOf(thread)
            templateHelper.getCommentThreads(pr) shouldBe listOf(thread)
        }
    }

    describe("getReviewProgressPercent") {
        val toP = project(1L, "own", "proj")
        val fromP = project(2L, "own2", "proj2")
        val contributor = user(9L, "contrib")
        it("스레드가 없으면 0.0") {
            val pr = PullRequest(id = 1L, toProject = toP, fromProject = fromP, contributor = contributor)
            every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
            templateHelper.getReviewProgressPercent(pr) shouldBe 0.0
        }
        it("일부만 CLOSED면 비율을 계산한다") {
            val pr = PullRequest(id = 2L, toProject = toP, fromProject = fromP, contributor = contributor)
            every { commentThreadRepository.findByPullRequest(pr) } returns listOf(
                SimpleCommentThread(id = 1L, pullRequest = pr, state = CommentThread.ThreadState.CLOSED),
                SimpleCommentThread(id = 2L, pullRequest = pr, state = CommentThread.ThreadState.OPEN)
            )
            templateHelper.getReviewProgressPercent(pr) shouldBe 50.0
        }
    }

    describe("countCommentThreadsByState") {
        val toP = project(1L, "own", "proj")
        val fromP = project(2L, "own2", "proj2")
        val contributor = user(9L, "contrib")
        it("state와 일치하는 스레드 개수를 센다") {
            val pr = PullRequest(id = 3L, toProject = toP, fromProject = fromP, contributor = contributor)
            every { commentThreadRepository.findByPullRequest(pr) } returns listOf(
                SimpleCommentThread(id = 1L, pullRequest = pr, state = CommentThread.ThreadState.CLOSED),
                SimpleCommentThread(id = 2L, pullRequest = pr, state = CommentThread.ThreadState.OPEN)
            )
            templateHelper.countCommentThreadsByState(pr, CommentThread.ThreadState.CLOSED) shouldBe 1L
        }
        it("일치하는 스레드가 없으면 0을 반환한다") {
            val pr = PullRequest(id = 4L, toProject = toP, fromProject = fromP, contributor = contributor)
            every { commentThreadRepository.findByPullRequest(pr) } returns listOf(
                SimpleCommentThread(id = 1L, pullRequest = pr, state = CommentThread.ThreadState.OPEN)
            )
            templateHelper.countCommentThreadsByState(pr, CommentThread.ThreadState.CLOSED) shouldBe 0L
        }
    }

    describe("isWatchingProject") {
        it("project가 null이면 false") {
            templateHelper.isWatchingProject(null, user()) shouldBe false
        }
        it("user가 null이면 false") {
            templateHelper.isWatchingProject(project(), null) shouldBe false
        }
        it("project.id가 null이면 false") {
            templateHelper.isWatchingProject(project(id = null), user(1L)) shouldBe false
        }
        it("watch 기록이 있으면 true") {
            val p = project(1L)
            val u = user(1L)
            every { watchRepository.findByUserAndResourceTypeAndResourceId(u, ResourceType.PROJECT, "1") } returns
                Watch(user = u, resourceType = ResourceType.PROJECT, resourceId = "1")
            templateHelper.isWatchingProject(p, u) shouldBe true
        }
        it("watch 기록이 없으면 false") {
            val p = project(2L)
            val u = user(1L)
            every { watchRepository.findByUserAndResourceTypeAndResourceId(u, ResourceType.PROJECT, "2") } returns null
            templateHelper.isWatchingProject(p, u) shouldBe false
        }
    }

    describe("getCloneUrl") {
        it("요청 컨텍스트가 없으면 http://localhost 기본값을 쓰고, user가 없으면 계정 없이 반환") {
            val p = project(owner = "own", name = "proj")
            templateHelper.getCloneUrl(p, null) shouldBe "http://localhost/git/own/proj.git"
        }
        it("scheme=http, port=80(기본포트)이면 포트를 생략한다") {
            val request = MockHttpServletRequest()
            request.scheme = "http"
            request.serverName = "example.com"
            request.serverPort = 80
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            val p = project(owner = "own", name = "proj")
            templateHelper.getCloneUrl(p, null) shouldBe "http://example.com/git/own/proj.git"
        }
        it("scheme=http, port=8080(비기본포트)이면 포트를 붙인다") {
            val request = MockHttpServletRequest()
            request.scheme = "http"
            request.serverName = "example.com"
            request.serverPort = 8080
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            val p = project(owner = "own", name = "proj")
            templateHelper.getCloneUrl(p, null) shouldBe "http://example.com:8080/git/own/proj.git"
        }
        it("scheme=https, port=443(기본포트)이면 포트를 생략한다") {
            val request = MockHttpServletRequest()
            request.scheme = "https"
            request.serverName = "example.com"
            request.serverPort = 443
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            val p = project(owner = "own", name = "proj")
            templateHelper.getCloneUrl(p, null) shouldBe "https://example.com/git/own/proj.git"
        }
        it("scheme=https, port=8443(비기본포트)이면 포트를 붙인다") {
            val request = MockHttpServletRequest()
            request.scheme = "https"
            request.serverName = "example.com"
            request.serverPort = 8443
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            val p = project(owner = "own", name = "proj")
            templateHelper.getCloneUrl(p, null) shouldBe "https://example.com:8443/git/own/proj.git"
        }
        it("user가 있어도 project.id가 null이면 멤버로 취급하지 않는다") {
            val request = MockHttpServletRequest()
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            val p = project(id = null, owner = "own", name = "proj")
            val u = user(1L, "member1")
            templateHelper.getCloneUrl(p, u) shouldBe "http://localhost/git/own/proj.git"
        }
        it("user.id가 null이면 멤버로 취급하지 않는다") {
            val request = MockHttpServletRequest()
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            val p = project(id = 1L, owner = "own", name = "proj")
            val u = user(id = null, loginId = "member1")
            templateHelper.getCloneUrl(p, u) shouldBe "http://localhost/git/own/proj.git"
        }
        it("프로젝트 멤버면 loginId@host 형태로 계정을 끼워넣는다") {
            val request = MockHttpServletRequest()
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            val p = project(id = 1L, owner = "own", name = "proj")
            val u = user(id = 1L, loginId = "member1")
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 1L) } returns true
            templateHelper.getCloneUrl(p, u) shouldBe "http://member1@localhost/git/own/proj.git"
        }
        it("프로젝트 멤버가 아니면 계정 없이 반환한다") {
            val request = MockHttpServletRequest()
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            val p = project(id = 1L, owner = "own", name = "proj")
            val u = user(id = 2L, loginId = "nonmember1")
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 2L) } returns false
            templateHelper.getCloneUrl(p, u) shouldBe "http://localhost/git/own/proj.git"
        }
    }
})
