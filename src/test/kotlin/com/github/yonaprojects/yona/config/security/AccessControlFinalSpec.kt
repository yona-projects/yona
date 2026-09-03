package com.github.yonaprojects.yona.config.security

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.SimpleCommentThread
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.webhook.Webhook
import com.github.yonaprojects.yona.domain.attachment.Attachment
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.util.Optional

// TASK-0259 AccessControl.kt 분기 커버리지 마무리(3번째 배치, 목표 95%). 기존 3개 테스트 파일
// (AccessControlSpec.kt/AccessControlIssuePostingSpec.kt/AccessControlPullRequestSpec.kt)을 정독해
// 이미 커버된 케이스는 제외하고, 남은 141개 미실행 분기 중 실제 코드 조건으로 도달 가능한 것만 채운다.
// 파일 충돌 방지를 위해 기존 3개 파일은 건드리지 않고 fixture 구성 방식만 그대로 복사해 재구성한다.
//
// 핵심적으로 반복 발견된 미실행 패턴 3가지:
// 1) ACCEPT/CLOSE/REOPEN 케이스(when 분기)에서 user==null 조합이 어떤 리소스 타입에도 테스트된 적이
//    없었다(각 리소스 타입의 when 절이 서로 다른 바이트코드 위치라 공유되지 않는다).
// 2) WATCH 케이스의 `user?.isMemberOf(project) == true` 항이 true가 되는 경로(그룹멤버가 아닌 순수
//    프로젝트 멤버의 WATCH)가 CommitComment/CommentThread/ReviewComment/Milestone/Webhook/
//    ResourceType/PullRequest 6~7개 타입에서 전혀 테스트되지 않았다(그룹멤버 테스트는 이 항이 아니라
//    3번째 항으로 true가 되므로 이 항 자체는 short-circuit으로 미평가).
// 3) CommitComment/CommentThread/ReviewComment의 DELETE 케이스는 지금까지 매니저/사이트매니저의
//    조기 return을 통해서만 호출돼, when 절의 DELETE 케이스 코드 자체(멤버 여부 분기)가 한 번도
//    실행되지 않았다.
class AccessControlFinalSpec : DescribeSpec({
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()

    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository
    )

    val restrictedAccessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository,
        allowsAnonymousAccess = false
    )

    beforeTest {
        clearMocks(
            projectUserRepository, organizationUserRepository, userRepository, organizationRepository,
            issueRepository, postingRepository, reviewCommentRepository, commitCommentRepository,
            milestoneRepository,
            answers = false
        )
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    }

    val org = Organization(id = 1L, name = "org")
    val publicProject = Project(id = 10L, name = "public", projectScope = ProjectScope.PUBLIC)
    val privateProject = Project(id = 11L, name = "private", projectScope = ProjectScope.PRIVATE)
    val protectedProject = Project(id = 12L, name = "protected", projectScope = ProjectScope.PROTECTED, organization = org)

    val siteManager = User(id = 1L, loginId = "site", name = "site", state = UserState.SITE_ADMIN)
    val guest = User(id = 2L, loginId = "guest", name = "guest", isGuest = true)
    val member = User(id = 3L, loginId = "member", name = "member")
    val stranger = User(id = 4L, loginId = "stranger", name = "stranger")
    val managerUser = User(id = 5L, loginId = "manager", name = "manager")
    val orgAdminUser = User(id = 6L, loginId = "orgadmin", name = "orgadmin")
    val fillerAuthor = User(id = 800L, loginId = "fillerAuthor", name = "fillerAuthor")

    member.projectUsers.add(ProjectUser(id = 100L, user = member, project = privateProject, role = Role(id = RoleType.MEMBER.roleType)))
    managerUser.projectUsers.add(ProjectUser(id = 101L, user = managerUser, project = privateProject, role = Role(id = RoleType.MANAGER.roleType)))

    val groupOrg = Organization(id = 900L, name = "groupOrg")
    val groupProtectedProject = Project(id = 900L, name = "groupProtected", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
    val groupMemberUser = User(id = 900L, loginId = "groupMember", name = "groupMember")
    groupOrg.organizationUsers.add(OrganizationUser(user = groupMemberUser, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType)))

    fun stubOrgRole(organization: Organization, user: User, roleType: RoleType) {
        every { organizationUserRepository.findByOrganizationIdAndUserId(organization.id!!, user.id!!) } returns
            Optional.of(OrganizationUser(user = user, organization = organization, role = Role(id = roleType.roleType)))
    }

    // ==================== isIssueCommentCreatable / isPostingCommentCreatable ====================

    describe("isIssueCommentCreatable(user, project, issue) - 잔여 분기") {
        it("issue.authorId가 null이면 작성자 우회 첫 조건이 short-circuit으로 평가된다(작성자/담당자/공유자 전부 아니므로 프로젝트 권한 규칙을 따른다)") {
            val issueNoAuthorAtAll = Issue(id = 9000L, title = "작성자 없는 이슈", project = privateProject, number = 900L)
            accessControl.isIssueCommentCreatable(stranger, privateProject, issueNoAuthorAtAll) shouldBe false
        }
    }

    describe("isPostingCommentCreatable(user, project, posting) - 잔여 분기") {
        it("기본 설정(allowsAnonymousAccess=true)에서도 비로그인 사용자는 작성자 우회 첫 조건(user!=null)에서 바로 false가 된다") {
            val postingOnPublic = Posting(id = 9001L, title = "공개 글", project = publicProject, number = 901L, authorId = fillerAuthor.id)
            accessControl.isPostingCommentCreatable(null, publicProject, postingOnPublic) shouldBe false
        }
    }

    // ==================== isAllowedIfGroupMember: 잔여 4개 분기는 도달 불가능 ====================
    // 이전 배치에서 이미 발견/문서화된 것과 동일한 구조적 사유: `val organization = project.organization
    // ?: return false`로 이미 organization이 non-null임을 확정한 직후, 바로 다음 줄의
    // `val hasGroup = project.organization != null`이 같은 필드를 다시 null 체크한다. 두 줄 사이에
    // organization을 바꿀 수 있는 코드가 없으므로 이 두 번째 null 체크의 "false"쪽(및 이를 소비하는
    // `if (hasGroup && ...)`의 hasGroup==false 분기)은 런타임에 절대 발생할 수 없는 죽은 코드다.
    // project.isPublic/isProtected 두 조건과 organizationUsers.any{} 람다(id 일치/ORG_MEMBER/ORG_ADMIN
    // 각각의 T/F)는 기존 3개 파일의 describe("isAllowedIfGroupMember(project, user)") 블록에서 이미
    // 전부 실행됐으므로 이 메서드에는 추가로 작성할 테스트가 없다(자의적 판단이 아니라 위 코드 흐름
    // 근거).

    // ==================== getVisibleProjects ====================

    describe("getVisibleProjects - 잔여 분기 보강") {
        it("hideProjectListing=true이면 비로그인(익명) 사용자에게도 빈 목록을 반환한다") {
            val hiddenListingAccessControl = AccessControl(
                projectUserRepository, organizationUserRepository,
                userRepository, organizationRepository,
                issueRepository, postingRepository,
                reviewCommentRepository, commitCommentRepository,
                milestoneRepository,
                hideProjectListing = true
            )
            val hideOrg2 = Organization(id = 9010L, name = "hideListingOrg2")
            val hidePublicProject2 = Project(id = 9011L, name = "hidePublic2", projectScope = ProjectScope.PUBLIC, organization = hideOrg2)
            hideOrg2.projects = mutableListOf(hidePublicProject2)

            val result = hiddenListingAccessControl.getVisibleProjects(hideOrg2, null)
            result shouldBe emptyList()
        }
    }

    // ==================== isAllowed(user, project, operation) - PROJECT 잔여 분기 ====================

    describe("isAllowed(user, project, operation) - PROJECT 잔여 분기") {
        it("비로그인 사용자가 ASSIGN_ISSUE/READ/WATCH/LEAVE가 아닌 일반 연산(UPDATE)에 도달하면 매니저/조직관리자 null-safe 체크로 거부된다") {
            accessControl.isAllowed(null, privateProject, Operation.UPDATE) shouldBe false
        }
    }

    // ==================== isAllowed(user, project, issue/issueComment/posting/postingComment, operation) ====================
    // ACCEPT/CLOSE/REOPEN + user==null 조합과, WATCH 케이스에서 project.isPublic이 false인 채로 첫 항의
    // user!=null까지 평가하지 않고 넘어가는 경로(비공개 프로젝트+익명)는 4개 리소스 타입 모두에서
    // 테스트된 적이 없었다.

    describe("isAllowed(user, project, issue, operation) - 잔여 분기") {
        it("ACCEPT는 비로그인 사용자에게 거부된다(user==null 조합)") {
            val issue = Issue(id = 9020L, project = privateProject, authorId = fillerAuthor.id)
            accessControl.isAllowed(null, privateProject, issue, Operation.ACCEPT) shouldBe false
        }
        it("WATCH는 비공개 프로젝트+비로그인 사용자에게 거부된다(공개 프로젝트 케이스와 다른 isPublic=false 분기)") {
            val issue = Issue(id = 9021L, project = privateProject, authorId = fillerAuthor.id)
            accessControl.isAllowed(null, privateProject, issue, Operation.WATCH) shouldBe false
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다(전항 all-false 조합)") {
            val issue = Issue(id = 9022L, project = privateProject, authorId = fillerAuthor.id)
            accessControl.isAllowed(stranger, privateProject, issue, Operation.WATCH) shouldBe false
        }
    }

    describe("isAllowed(user, project, issueComment, operation) - 잔여 분기") {
        it("ACCEPT는 비로그인 사용자에게 거부된다") {
            val issue = Issue(id = 9030L, project = privateProject, authorId = fillerAuthor.id)
            val comment = IssueComment(id = 9040L, issue = issue, authorId = fillerAuthor.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.ACCEPT) shouldBe false
        }
        it("WATCH는 비공개 프로젝트+비로그인 사용자에게 거부된다") {
            val issue = Issue(id = 9031L, project = privateProject, authorId = fillerAuthor.id)
            val comment = IssueComment(id = 9041L, issue = issue, authorId = fillerAuthor.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.WATCH) shouldBe false
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            val issue = Issue(id = 9032L, project = privateProject, authorId = fillerAuthor.id)
            val comment = IssueComment(id = 9042L, issue = issue, authorId = fillerAuthor.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.WATCH) shouldBe false
        }
    }

    describe("isAllowed(user, project, posting, operation) - 잔여 분기") {
        it("ACCEPT는 비로그인 사용자에게 거부된다") {
            val posting = Posting(id = 9050L, project = privateProject, authorId = fillerAuthor.id)
            accessControl.isAllowed(null, privateProject, posting, Operation.ACCEPT) shouldBe false
        }
        it("WATCH는 비공개 프로젝트+비로그인 사용자에게 거부된다") {
            val posting = Posting(id = 9051L, project = privateProject, authorId = fillerAuthor.id)
            accessControl.isAllowed(null, privateProject, posting, Operation.WATCH) shouldBe false
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            val posting = Posting(id = 9052L, project = privateProject, authorId = fillerAuthor.id)
            accessControl.isAllowed(stranger, privateProject, posting, Operation.WATCH) shouldBe false
        }
    }

    describe("isAllowed(user, project, postingComment, operation) - 잔여 분기") {
        it("ACCEPT는 비로그인 사용자에게 거부된다") {
            val posting = Posting(id = 9060L, project = privateProject, authorId = fillerAuthor.id)
            val comment = PostingComment(id = 9070L, posting = posting, authorId = fillerAuthor.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.ACCEPT) shouldBe false
        }
        it("WATCH는 비공개 프로젝트+비로그인 사용자에게 거부된다") {
            val posting = Posting(id = 9061L, project = privateProject, authorId = fillerAuthor.id)
            val comment = PostingComment(id = 9071L, posting = posting, authorId = fillerAuthor.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.WATCH) shouldBe false
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            val posting = Posting(id = 9062L, project = privateProject, authorId = fillerAuthor.id)
            val comment = PostingComment(id = 9072L, posting = posting, authorId = fillerAuthor.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.WATCH) shouldBe false
        }
    }

    // ==================== PullRequest / CommitComment / CommentThread / ReviewComment / Milestone /
    // Webhook / ResourceType 공통 잔여 패턴 ====================
    // WATCH 케이스의 `user?.isMemberOf(project) == true` 항 자체가 true가 되는 경로(순수 프로젝트
    // 멤버, 그룹멤버 아님)가 이 7개 타입 전부에서 미실행이었다(그룹멤버 테스트는 3번째 항에서 이미
    // true가 돼 이 항은 short-circuit으로 평가되지 않았음). CommitComment/CommentThread/ReviewComment는
    // 추가로 DELETE 케이스 자체가(매니저/사이트매니저의 조기 return이 아니라) when 절을 통해 실행된 적이
    // 없었다.

    describe("isAllowed(user, project, pullRequest, operation) - 잔여 분기") {
        val pr = PullRequest(id = 9100L, toProject = privateProject, fromProject = privateProject, contributor = fillerAuthor)

        it("WATCH는 순수 프로젝트 멤버(그룹멤버 아님)에게 허용된다(2번째 OR항 자체가 true)") {
            accessControl.isAllowed(member, privateProject, pr, Operation.WATCH) shouldBe true
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            accessControl.isAllowed(stranger, privateProject, pr, Operation.WATCH) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, pr, Operation.ACCEPT) shouldBe false
        }
    }

    describe("isAllowed(user, project, commitComment, operation) - 잔여 분기") {
        val comment = CommitComment(id = 9110L, project = privateProject, author = UserIdent(fillerAuthor), commitId = "final1")

        it("WATCH는 순수 프로젝트 멤버(그룹멤버 아님)에게 허용된다") {
            accessControl.isAllowed(member, privateProject, comment, Operation.WATCH) shouldBe true
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            accessControl.isAllowed(stranger, privateProject, comment, Operation.WATCH) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, comment, Operation.REOPEN) shouldBe false
        }
        it("DELETE는 when 절을 통해(매니저 조기 return 없이) 순수 멤버에게 허용, 비멤버에게 거부된다") {
            accessControl.isAllowed(member, privateProject, comment, Operation.DELETE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, comment, Operation.DELETE) shouldBe false
        }
    }

    describe("isAllowed(user, project, commentThread, operation) - 잔여 분기") {
        val thread = SimpleCommentThread(id = 9120L, project = privateProject, author = UserIdent(fillerAuthor))

        it("WATCH는 순수 프로젝트 멤버(그룹멤버 아님)에게 허용된다") {
            accessControl.isAllowed(member, privateProject, thread, Operation.WATCH) shouldBe true
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            accessControl.isAllowed(stranger, privateProject, thread, Operation.WATCH) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, thread, Operation.CLOSE) shouldBe false
        }
        it("DELETE는 when 절을 통해 순수 멤버에게 허용, 비멤버에게 거부된다") {
            accessControl.isAllowed(member, privateProject, thread, Operation.DELETE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, thread, Operation.DELETE) shouldBe false
        }
    }

    describe("isAllowed(user, project, reviewComment, operation) - 잔여 분기") {
        val comment = ReviewComment(id = 9130L, author = UserIdent(fillerAuthor))

        it("WATCH는 순수 프로젝트 멤버(그룹멤버 아님)에게 허용된다") {
            accessControl.isAllowed(member, privateProject, comment, Operation.WATCH) shouldBe true
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            accessControl.isAllowed(stranger, privateProject, comment, Operation.WATCH) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, comment, Operation.ACCEPT) shouldBe false
        }
        it("DELETE는 when 절을 통해 순수 멤버에게 허용, 비멤버에게 거부된다") {
            accessControl.isAllowed(member, privateProject, comment, Operation.DELETE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, comment, Operation.DELETE) shouldBe false
        }
        it("READ는 비공개 프로젝트+비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, comment, Operation.READ) shouldBe false
        }
        it("UPDATE는 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, comment, Operation.UPDATE) shouldBe false
        }
        it("allowsAnonymousAccess=false에서도 로그인한 순수 멤버는 READ 정상 허용된다") {
            restrictedAccessControl.isAllowed(member, privateProject, comment, Operation.READ) shouldBe true
        }
    }

    describe("isAllowed(user, project, milestone, operation) - 잔여 분기") {
        val milestone = Milestone(id = 9140L, project = privateProject)

        it("WATCH는 순수 프로젝트 멤버(그룹멤버 아님)에게 허용된다") {
            accessControl.isAllowed(member, privateProject, milestone, Operation.WATCH) shouldBe true
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            accessControl.isAllowed(stranger, privateProject, milestone, Operation.WATCH) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, milestone, Operation.ACCEPT) shouldBe false
        }
        it("UPDATE는 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, milestone, Operation.UPDATE) shouldBe false
        }
    }

    describe("isAllowed(user, project, webhook, operation) - 잔여 분기") {
        val webhook = Webhook(id = 9150L, project = privateProject)

        it("WATCH는 순수 프로젝트 멤버(그룹멤버 아님)에게 허용된다") {
            accessControl.isAllowed(member, privateProject, webhook, Operation.WATCH) shouldBe true
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            accessControl.isAllowed(stranger, privateProject, webhook, Operation.WATCH) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, webhook, Operation.ACCEPT) shouldBe false
        }
        it("UPDATE는 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, webhook, Operation.UPDATE) shouldBe false
        }
    }

    describe("isAllowed(user, project, resourceType, operation) - 잔여 분기") {
        it("WATCH는 순수 프로젝트 멤버(그룹멤버 아님)에게 허용된다") {
            accessControl.isAllowed(member, privateProject, ResourceType.WIKI_PAGE, Operation.WATCH) shouldBe true
        }
        it("WATCH는 비공개 프로젝트의 비멤버·비그룹멤버에게 거부된다") {
            accessControl.isAllowed(stranger, privateProject, ResourceType.WIKI_PAGE, Operation.WATCH) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, ResourceType.WIKI_PAGE, Operation.ACCEPT) shouldBe false
        }
        it("UPDATE는 비로그인 사용자에게 거부된다") {
            accessControl.isAllowed(null, privateProject, ResourceType.WIKI_PAGE, Operation.UPDATE) shouldBe false
        }
        it("CODE 타입도 비로그인 사용자에게는 DELETE 특수분기 이전에 이미 거부된다") {
            accessControl.isAllowed(null, privateProject, ResourceType.CODE, Operation.DELETE) shouldBe false
        }
    }

    // ==================== isAllowedAttachment - 잔여 분기 ====================

    describe("isAllowedAttachment(user, attachment, operation) - 잔여 분기") {
        it("NOT_A_RESOURCE 컨테이너는 기본 설정에서도 비로그인 사용자에게 거부된다(user!=null 첫 조건 자체가 false)") {
            val attachment = Attachment(id = 9200L, containerType = ResourceType.NOT_A_RESOURCE, containerId = "0", ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(null, attachment, Operation.READ) shouldBe false
        }
        it("USER_AVATAR 컨테이너는 containerId가 숫자로 파싱되지 않으면 UPDATE가 거부된다") {
            val attachment = Attachment(id = 9201L, containerType = ResourceType.USER_AVATAR, containerId = "not-a-number", ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.UPDATE) shouldBe false
        }
        it("USER_AVATAR 컨테이너는 UPDATE에서 비로그인 사용자가 거부된다(user?.id!=null 첫 조건이 false)") {
            val attachment = Attachment(id = 9202L, containerType = ResourceType.USER_AVATAR, containerId = member.id.toString(), ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(null, attachment, Operation.UPDATE) shouldBe false
        }
        it("ORGANIZATION 컨테이너는 DELETE도 UPDATE와 동일하게(when 라벨 자체) 조직관리자에게 허용된다") {
            every { organizationRepository.findById(1L) } returns Optional.of(org)
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            val attachment = Attachment(id = 9203L, containerType = ResourceType.ORGANIZATION, containerId = "1", ownerLoginId = "x")
            accessControl.isAllowedAttachment(orgAdminUser, attachment, Operation.DELETE) shouldBe true
        }
        it("ISSUE_POST 컨테이너는 READ/UPDATE/DELETE 이외 연산(else 분기)이면 거부된다") {
            val issue = Issue(id = 9204L, project = publicProject, authorId = fillerAuthor.id)
            every { issueRepository.findById(9204L) } returns Optional.of(issue)
            val attachment = Attachment(id = 9204L, containerType = ResourceType.ISSUE_POST, containerId = "9204", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.WATCH) shouldBe false
        }
        it("ISSUE_POST 컨테이너의 UPDATE는(DELETE와 별개의 when 라벨) 이슈 UPDATE 권한을 따른다") {
            val issue = Issue(id = 9205L, project = privateProject, authorId = member.id)
            every { issueRepository.findById(9205L) } returns Optional.of(issue)
            val attachment = Attachment(id = 9205L, containerType = ResourceType.ISSUE_POST, containerId = "9205", ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(member, attachment, Operation.UPDATE) shouldBe true
        }
        it("BOARD_POST 컨테이너는 UPDATE/DELETE에서 게시글 UPDATE 권한을 따르고, else 분기(WATCH)면 거부된다") {
            val posting = Posting(id = 9206L, project = privateProject, authorId = member.id)
            every { postingRepository.findById(9206L) } returns Optional.of(posting)
            val attachmentUpdate = Attachment(id = 9206L, containerType = ResourceType.BOARD_POST, containerId = "9206", ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(member, attachmentUpdate, Operation.UPDATE) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachmentUpdate, Operation.DELETE) shouldBe false
            accessControl.isAllowedAttachment(member, attachmentUpdate, Operation.WATCH) shouldBe false
        }
        it("MILESTONE 컨테이너는 READ 성공 경로와 DELETE 라벨, else 분기(WATCH)를 모두 커버한다") {
            val milestone = Milestone(id = 9207L, project = privateProject)
            every { milestoneRepository.findById(9207L) } returns Optional.of(milestone)
            val attachment = Attachment(id = 9207L, containerType = ResourceType.MILESTONE, containerId = "9207", ownerLoginId = "x")
            accessControl.isAllowedAttachment(member, attachment, Operation.READ) shouldBe true
            accessControl.isAllowedAttachment(member, attachment, Operation.DELETE) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachment, Operation.DELETE) shouldBe false
            accessControl.isAllowedAttachment(member, attachment, Operation.WATCH) shouldBe false
        }
        it("COMMIT_COMMENT 컨테이너는 UPDATE 라벨과 else 분기(WATCH)를 커버한다") {
            val commitComment = CommitComment(id = 9208L, project = privateProject, author = UserIdent(fillerAuthor), commitId = "final-cc")
            every { commitCommentRepository.findById(9208L) } returns Optional.of(commitComment)
            val attachment = Attachment(id = 9208L, containerType = ResourceType.COMMIT_COMMENT, containerId = "9208", ownerLoginId = "x")
            accessControl.isAllowedAttachment(member, attachment, Operation.UPDATE) shouldBe true
            accessControl.isAllowedAttachment(member, attachment, Operation.WATCH) shouldBe false
        }
        it("REVIEW_COMMENT 컨테이너는 UPDATE 라벨과 else 분기(WATCH)를 커버한다") {
            val thread = SimpleCommentThread(id = 9209L, project = privateProject, author = UserIdent(fillerAuthor))
            val reviewComment = ReviewComment(id = 9209L, author = UserIdent(fillerAuthor), thread = thread)
            every { reviewCommentRepository.findById(9209L) } returns Optional.of(reviewComment)
            val attachment = Attachment(id = 9209L, containerType = ResourceType.REVIEW_COMMENT, containerId = "9209", ownerLoginId = "x")
            accessControl.isAllowedAttachment(member, attachment, Operation.UPDATE) shouldBe true
            accessControl.isAllowedAttachment(member, attachment, Operation.WATCH) shouldBe false
        }
    }

    // ==================== isOrganizationAdmin/isOrganizationMember 람다: role.id가 null인 경우 ====================
    // Role.id는 Long?(nullable, @Id지만 영속화 전에는 null일 수 있는 도메인 모델)이고 RoleType.roleType은
    // 비 nullable Long이라 `it.role.id == RoleType.X.roleType` 비교의 null-분기는 실제로 도달 가능하다
    // (죽은 코드 아님) — 지금까지 모든 테스트가 항상 concrete id를 가진 Role만 사용해 이 경로가 빠졌다.

    describe("isOrganizationAdmin/isOrganizationMember - role.id가 null인 경우(구조적으로 도달 가능한 잔여 분기)") {
        it("isOrganizationAdmin(project, user) private 오버로드: role.id가 null이면 false(isAllowedToReadProject 경유)") {
            val roleNullOrg = Organization(id = 9300L, name = "roleNullOrg")
            val roleNullUser = User(id = 9301L, loginId = "roleNullUser", name = "roleNullUser")
            val roleNullProject = Project(id = 9302L, name = "roleNullProject", projectScope = ProjectScope.PROTECTED, organization = roleNullOrg)
            every { organizationUserRepository.findByOrganizationIdAndUserId(9300L, 9301L) } returns
                Optional.of(OrganizationUser(user = roleNullUser, organization = roleNullOrg, role = Role(id = null)))
            accessControl.isAllowedToReadProject(roleNullUser, roleNullProject) shouldBe false
        }
        it("isOrganizationAdmin(organization, user) public 오버로드: role.id가 null이면 false") {
            val roleNullOrg2 = Organization(id = 9310L, name = "roleNullOrg2")
            val roleNullUser2 = User(id = 9311L, loginId = "roleNullUser2", name = "roleNullUser2")
            every { organizationUserRepository.findByOrganizationIdAndUserId(9310L, 9311L) } returns
                Optional.of(OrganizationUser(user = roleNullUser2, organization = roleNullOrg2, role = Role(id = null)))
            accessControl.isOrganizationAdmin(roleNullOrg2, roleNullUser2) shouldBe false
        }
        it("isOrganizationMember(organization, user) private: role.id가 null이면 false(ASSIGN_ISSUE 경유)") {
            val roleNullOrg3 = Organization(id = 9320L, name = "roleNullOrg3")
            val roleNullUser3 = User(id = 9321L, loginId = "roleNullUser3", name = "roleNullUser3")
            val roleNullPublicProject = Project(id = 9322L, name = "roleNullPublicProject", projectScope = ProjectScope.PUBLIC, organization = roleNullOrg3)
            every { organizationUserRepository.findByOrganizationIdAndUserId(9320L, 9321L) } returns
                Optional.of(OrganizationUser(user = roleNullUser3, organization = roleNullOrg3, role = Role(id = null)))
            accessControl.isAllowed(roleNullUser3, roleNullPublicProject, Operation.ASSIGN_ISSUE) shouldBe false
        }
    }
})
