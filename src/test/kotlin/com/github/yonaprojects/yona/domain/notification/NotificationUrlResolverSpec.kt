package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.SimpleCommentThread
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Optional

// yona utils/RouteUtil.java + NotificationEvent.getUrlToView()/getProject() 대응 (P1-27).
class NotificationUrlResolverSpec : DescribeSpec({
    val issueRepository = mockk<IssueRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val resolver = NotificationUrlResolver(
        issueRepository, issueCommentRepository, postingRepository, postingCommentRepository,
        pullRequestRepository, commitCommentRepository, reviewCommentRepository, commentThreadRepository,
        userRepository, projectRepository, organizationRepository, "https://yona.example.com"
    )

    fun event(eventType: EventType, resourceType: ResourceType, resourceId: String) = NotificationEvent(
        title = "제목", created = Instant.now(),
        resourceType = resourceType, resourceId = resourceId, eventType = eventType
    )

    describe("getUrlToView") {
        it("ISSUE_POST는 project owner/name/issue/number 형식의 URL을 만든다") {
            val project = Project(id = 1L, name = "myproj", owner = "gildong")
            val issue = Issue(id = 10L, title = "제목", body = "본문", project = project, number = 7L)
            every { issueRepository.findById(10L) } returns Optional.of(issue)

            resolver.getUrlToView(event(EventType.NEW_ISSUE, ResourceType.ISSUE_POST, "10")) shouldBe
                "https://yona.example.com/gildong/myproj/issue/7"
        }

        it("ISSUE_COMMENT는 이슈 URL에 #comment-{id} 앵커를 붙인다") {
            val project = Project(id = 1L, name = "myproj", owner = "gildong")
            val issue = Issue(id = 10L, title = "제목", body = "본문", project = project, number = 7L)
            val comment = IssueComment(id = 55L, contents = "댓글", issue = issue)
            every { issueCommentRepository.findById(55L) } returns Optional.of(comment)
            every { issueRepository.findById(10L) } returns Optional.of(issue)

            resolver.getUrlToView(event(EventType.NEW_COMMENT, ResourceType.ISSUE_COMMENT, "55")) shouldBe
                "https://yona.example.com/gildong/myproj/issue/7#comment-55"
        }

        it("MEMBER_ENROLL_REQUEST는 프로젝트 멤버 페이지로 링크한다") {
            val project = Project(id = 1L, name = "myproj", owner = "gildong")
            every { projectRepository.findById(1L) } returns Optional.of(project)

            resolver.getUrlToView(event(EventType.MEMBER_ENROLL_REQUEST, ResourceType.PROJECT, "1")) shouldBe
                "https://yona.example.com/gildong/myproj/members"
        }

        it("MEMBER_ENROLL_ACCEPT는 프로젝트 메인 페이지로 링크한다") {
            val project = Project(id = 1L, name = "myproj", owner = "gildong")
            every { projectRepository.findById(1L) } returns Optional.of(project)

            resolver.getUrlToView(event(EventType.MEMBER_ENROLL_ACCEPT, ResourceType.PROJECT, "1")) shouldBe
                "https://yona.example.com/gildong/myproj"
        }

        it("ORGANIZATION_MEMBER_ENROLL_REQUEST는 그룹 멤버 페이지로 링크한다") {
            val org = Organization(id = 2L, name = "myorg")
            every { organizationRepository.findById(2L) } returns Optional.of(org)

            resolver.getUrlToView(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, ResourceType.ORGANIZATION, "2")) shouldBe
                "https://yona.example.com/organizations/myorg/members"
        }

        it("NEW_COMMIT은 커밋 SHA만으로는 프로젝트를 되짚을 수 없어 null을 반환한다") {
            resolver.getUrlToView(event(EventType.NEW_COMMIT, ResourceType.COMMIT, "abcdef123")) shouldBe null
        }

        it("리소스를 찾을 수 없으면 null을 반환한다") {
            every { issueRepository.findById(999L) } returns Optional.empty()

            resolver.getUrlToView(event(EventType.NEW_ISSUE, ResourceType.ISSUE_POST, "999")) shouldBe null
        }

        it("MEMBER_ENROLL_REQUEST는 resourceId가 숫자가 아니면 null을 반환한다 (projectOf의 toLongOrNull 실패 분기)") {
            resolver.getUrlToView(event(EventType.MEMBER_ENROLL_REQUEST, ResourceType.PROJECT, "abc")) shouldBe null
        }

        it("MEMBER_ENROLL_ACCEPT는 resourceType이 PROJECT가 아니면 null을 반환한다 (projectOf의 when-else 분기)") {
            resolver.getUrlToView(event(EventType.MEMBER_ENROLL_ACCEPT, ResourceType.ORGANIZATION, "1")) shouldBe null
        }

        it("NEW_COMMIT은 resourceType이 PROJECT이고 프로젝트를 찾으면 commits 페이지로 링크한다") {
            val project = Project(id = 3L, name = "commitsproj", owner = "hong")
            every { projectRepository.findById(3L) } returns Optional.of(project)

            resolver.getUrlToView(event(EventType.NEW_COMMIT, ResourceType.PROJECT, "3")) shouldBe
                "https://yona.example.com/hong/commitsproj/commits"
        }

        it("ORGANIZATION_MEMBER_ENROLL_REQUEST는 resourceId가 숫자가 아니면 null을 반환한다 (organizationOf의 toLongOrNull 실패 분기)") {
            resolver.getUrlToView(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, ResourceType.ORGANIZATION, "xyz")) shouldBe null
        }

        it("ORGANIZATION_MEMBER_ENROLL_ACCEPT는 그룹 메인 페이지로 링크한다") {
            val org = Organization(id = 4L, name = "acceptorg")
            every { organizationRepository.findById(4L) } returns Optional.of(org)

            resolver.getUrlToView(event(EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT, ResourceType.ORGANIZATION, "4")) shouldBe
                "https://yona.example.com/organizations/acceptorg"
        }

        it("ORGANIZATION_MEMBER_ENROLL_ACCEPT는 resourceId가 숫자가 아니면 null을 반환한다") {
            resolver.getUrlToView(event(EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT, ResourceType.ORGANIZATION, "xyz")) shouldBe null
        }
    }

    describe("getUrl") {
        it("NONISSUE_COMMENT는 게시글 URL에 #comment-{id} 앵커를 붙인다") {
            val project = Project(id = 20L, name = "boardproj", owner = "kim")
            val posting = Posting(id = 30L, title = "게시글", project = project, number = 5L)
            val comment = PostingComment(id = 40L, contents = "댓글", posting = posting)
            every { postingCommentRepository.findById(40L) } returns Optional.of(comment)
            every { postingRepository.findById(30L) } returns Optional.of(posting)

            resolver.getUrl(ResourceType.NONISSUE_COMMENT, "40") shouldBe
                "https://yona.example.com/kim/boardproj/post/5#comment-40"
        }

        it("NONISSUE_COMMENT는 댓글을 찾을 수 없으면 null을 반환한다") {
            every { postingCommentRepository.findById(999L) } returns Optional.empty()

            resolver.getUrl(ResourceType.NONISSUE_COMMENT, "999") shouldBe null
        }

        it("NONISSUE_COMMENT는 댓글은 있지만 게시글이 삭제된 경우(orphan) null을 반환한다") {
            val project = Project(id = 21L, name = "orphanboard", owner = "kim")
            val posting = Posting(id = 31L, title = "삭제된 게시글", project = project, number = 6L)
            val comment = PostingComment(id = 41L, contents = "댓글", posting = posting)
            every { postingCommentRepository.findById(41L) } returns Optional.of(comment)
            every { postingRepository.findById(31L) } returns Optional.empty()

            resolver.getUrl(ResourceType.NONISSUE_COMMENT, "41") shouldBe null
        }

        it("BOARD_POST는 project owner/name/post/number 형식의 URL을 만든다") {
            val project = Project(id = 22L, name = "directboard", owner = "lee")
            val posting = Posting(id = 32L, title = "직접조회 게시글", project = project, number = 8L)
            every { postingRepository.findById(32L) } returns Optional.of(posting)

            resolver.getUrl(ResourceType.BOARD_POST, "32") shouldBe
                "https://yona.example.com/lee/directboard/post/8"
        }

        it("BOARD_POST는 게시글을 찾을 수 없으면 null을 반환한다") {
            every { postingRepository.findById(998L) } returns Optional.empty()

            resolver.getUrl(ResourceType.BOARD_POST, "998") shouldBe null
        }

        it("COMMIT_COMMENT는 project owner/name/commit/commitId#comment-{id} 형식의 URL을 만든다") {
            val project = Project(id = 23L, name = "codeproj", owner = "park")
            val comment = CommitComment(id = 50L, project = project, contents = "커밋댓글", commitId = "abc123")
            every { commitCommentRepository.findById(50L) } returns Optional.of(comment)

            resolver.getUrl(ResourceType.COMMIT_COMMENT, "50") shouldBe
                "https://yona.example.com/park/codeproj/commit/abc123#comment-50"
        }

        it("COMMIT_COMMENT는 project가 null이면 null을 반환한다") {
            val comment = CommitComment(id = 51L, project = null, contents = "프로젝트 없는 댓글", commitId = "def456")
            every { commitCommentRepository.findById(51L) } returns Optional.of(comment)

            resolver.getUrl(ResourceType.COMMIT_COMMENT, "51") shouldBe null
        }

        it("COMMIT_COMMENT는 댓글을 찾을 수 없으면 null을 반환한다") {
            every { commitCommentRepository.findById(997L) } returns Optional.empty()

            resolver.getUrl(ResourceType.COMMIT_COMMENT, "997") shouldBe null
        }

        it("PULL_REQUEST는 toProject owner/name/pull/number 형식의 URL을 만든다") {
            val toProject = Project(id = 24L, name = "toproj", owner = "choi")
            val fromProject = Project(id = 25L, name = "fromproj", owner = "choi")
            val contributor = User(id = 60L, name = "기여자", loginId = "contributor1")
            val pr = PullRequest(
                id = 70L, toProject = toProject, fromProject = fromProject,
                contributor = contributor, number = 9L
            )
            every { pullRequestRepository.findById(70L) } returns Optional.of(pr)

            resolver.getUrl(ResourceType.PULL_REQUEST, "70") shouldBe
                "https://yona.example.com/choi/toproj/pull/9"
        }

        it("PULL_REQUEST는 찾을 수 없으면 null을 반환한다") {
            every { pullRequestRepository.findById(996L) } returns Optional.empty()

            resolver.getUrl(ResourceType.PULL_REQUEST, "996") shouldBe null
        }

        it("REVIEW_COMMENT는 댓글을 찾을 수 없으면 null을 반환한다") {
            every { reviewCommentRepository.findById(995L) } returns Optional.empty()

            resolver.getUrl(ResourceType.REVIEW_COMMENT, "995") shouldBe null
        }

        it("REVIEW_COMMENT는 thread가 null이면 null을 반환한다") {
            val comment = ReviewComment(id = 80L, contents = "쓰레드 없는 리뷰댓글", thread = null)
            every { reviewCommentRepository.findById(80L) } returns Optional.of(comment)

            resolver.getUrl(ResourceType.REVIEW_COMMENT, "80") shouldBe null
        }

        it("REVIEW_COMMENT는 PR에 속한 쓰레드면 pull 페이지 URL에 #comment-{id}를 붙인다") {
            val toProject = Project(id = 26L, name = "prthreadproj", owner = "jung")
            val fromProject = Project(id = 27L, name = "prthreadfrom", owner = "jung")
            val contributor = User(id = 61L, name = "기여자2", loginId = "contributor2")
            val pr = PullRequest(
                id = 71L, toProject = toProject, fromProject = fromProject,
                contributor = contributor, number = 11L
            )
            val thread = SimpleCommentThread(id = 90L, pullRequest = pr, project = null)
            val comment = ReviewComment(id = 81L, contents = "PR 리뷰댓글", thread = thread)
            every { reviewCommentRepository.findById(81L) } returns Optional.of(comment)

            resolver.getUrl(ResourceType.REVIEW_COMMENT, "81") shouldBe
                "https://yona.example.com/jung/prthreadproj/pull/11#comment-81"
        }

        it("REVIEW_COMMENT는 쓰레드에 PR도 project도 없으면(urlToContainer가 null) null을 반환한다") {
            val thread = SimpleCommentThread(id = 91L, pullRequest = null, project = null)
            val comment = ReviewComment(id = 82L, contents = "고아 쓰레드 리뷰댓글", thread = thread)
            every { reviewCommentRepository.findById(82L) } returns Optional.of(comment)

            resolver.getUrl(ResourceType.REVIEW_COMMENT, "82") shouldBe null
        }

        it("COMMENT_THREAD는 찾을 수 없으면 null을 반환한다") {
            every { commentThreadRepository.findById(994L) } returns Optional.empty()

            resolver.getUrl(ResourceType.COMMENT_THREAD, "994") shouldBe null
        }

        it("COMMENT_THREAD는 project에 속한(PR 없는) 커밋 쓰레드면 commit 페이지 URL에 #thread-{id}를 붙인다") {
            val project = Project(id = 28L, name = "committhreadproj", owner = "yoon")
            val thread = SimpleCommentThread(id = 92L, pullRequest = null, project = project, commitId = "cafebabe")
            every { commentThreadRepository.findById(92L) } returns Optional.of(thread)

            resolver.getUrl(ResourceType.COMMENT_THREAD, "92") shouldBe
                "https://yona.example.com/yoon/committhreadproj/commit/cafebabe#thread-92"
        }

        it("COMMENT_THREAD는 PR도 project도 없으면(urlToContainer가 null) 빈 문자열을 반환한다") {
            val thread = SimpleCommentThread(id = 93L, pullRequest = null, project = null)
            every { commentThreadRepository.findById(93L) } returns Optional.of(thread)

            resolver.getUrl(ResourceType.COMMENT_THREAD, "93") shouldBe ""
        }

        it("USER_AVATAR는 /user/{loginId} 형식의 URL을 만든다") {
            val user = User(id = 100L, name = "아바타유저", loginId = "avataruser")
            every { userRepository.findById(100L) } returns Optional.of(user)

            resolver.getUrl(ResourceType.USER_AVATAR, "100") shouldBe
                "https://yona.example.com/user/avataruser"
        }

        it("USER_AVATAR는 사용자를 찾을 수 없으면 null을 반환한다") {
            every { userRepository.findById(993L) } returns Optional.empty()

            resolver.getUrl(ResourceType.USER_AVATAR, "993") shouldBe null
        }

        it("PROJECT는 owner/name 형식의 URL을 직접 만든다") {
            val project = Project(id = 29L, name = "directproj", owner = "han")
            every { projectRepository.findById(29L) } returns Optional.of(project)

            resolver.getUrl(ResourceType.PROJECT, "29") shouldBe
                "https://yona.example.com/han/directproj"
        }

        it("PROJECT는 찾을 수 없으면 null을 반환한다") {
            every { projectRepository.findById(992L) } returns Optional.empty()

            resolver.getUrl(ResourceType.PROJECT, "992") shouldBe null
        }

        it("ISSUE_COMMENT는 댓글을 찾을 수 없으면 null을 반환한다") {
            every { issueCommentRepository.findById(991L) } returns Optional.empty()

            resolver.getUrl(ResourceType.ISSUE_COMMENT, "991") shouldBe null
        }

        it("ISSUE_COMMENT는 댓글은 있지만 이슈가 삭제된 경우(orphan) null을 반환한다") {
            val project = Project(id = 31L, name = "orphanissue", owner = "kang")
            val issue = Issue(id = 110L, title = "삭제된 이슈", project = project, number = 12L)
            val comment = IssueComment(id = 120L, contents = "고아 댓글", issue = issue)
            every { issueCommentRepository.findById(120L) } returns Optional.of(comment)
            every { issueRepository.findById(110L) } returns Optional.empty()

            resolver.getUrl(ResourceType.ISSUE_COMMENT, "120") shouldBe null
        }

        it("지원하지 않는 resourceType(else 분기)은 null을 반환한다") {
            resolver.getUrl(ResourceType.CODE, "1") shouldBe null
        }

        it("resourceId가 숫자로 변환되지 않으면 null을 반환한다") {
            resolver.getUrl(ResourceType.ISSUE_POST, "not-a-number") shouldBe null
        }

        it("리포지토리 조회 중 예외가 발생하면 잡아서 null을 반환한다") {
            every { issueRepository.findById(200L) } throws RuntimeException("DB 오류")

            resolver.getUrl(ResourceType.ISSUE_POST, "200") shouldBe null
        }
    }
})
