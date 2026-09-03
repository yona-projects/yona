package com.github.yonaprojects.yona.config.security

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectTransfer
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

// P1-85 1b AccessControl.kt 분기 커버리지 보강(3개 에이전트 병렬 작업 중 하나) — 이 파일은 아래 8개
// 오버로드/메서드만 담당한다: isAllowed(User, Project, PullRequest/CommitComment/CommentThread/
// ReviewComment/Milestone/Webhook/ResourceType, Operation), isAllowed(User, ProjectTransfer, Operation),
// isAllowedAttachment(User, Attachment, Operation). AccessControlSpec.kt와의 파일 충돌을 피하기 위해
// 기존 파일을 수정하지 않고 독립된 DescribeSpec으로 fixture를 그대로 복사해 재구성한다.
class AccessControlPullRequestSpec : DescribeSpec({
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

    // allowsAnonymousAccess=false 설정 — 각 대상 메서드의 `isAnonymousNotAllowed() && user == null`
    // 분기의 true 쪽을 실제로 태우기 위한 별도 인스턴스(AccessControlSpec.kt의 restrictedAccessControl과
    // 동일한 목적, PROJECT 리소스가 아닌 나머지 8개 오버로드 각각의 고유 바이트코드를 커버하기 위해 필요).
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

    member.projectUsers.add(ProjectUser(id = 100L, user = member, project = privateProject, role = Role(id = RoleType.MEMBER.roleType)))
    managerUser.projectUsers.add(ProjectUser(id = 101L, user = managerUser, project = privateProject, role = Role(id = RoleType.MANAGER.roleType)))

    fun stubOrgRole(organization: Organization, user: User, roleType: RoleType) {
        every { organizationUserRepository.findByOrganizationIdAndUserId(organization.id!!, user.id!!) } returns
            Optional.of(OrganizationUser(user = user, organization = organization, role = Role(id = roleType.roleType)))
    }

    // isAllowedIfGroupMember(project, user)는 organizationUserRepository가 아니라
    // organization.organizationUsers 컬렉션을 직접 조회한다 — 이 컬렉션에 직접 멤버를 추가해 매니저도
    // 프로젝트 직접 멤버도 아닌 "조직(그룹) 멤버" 전용 분기를 프로젝트 멤버 여부와 분리해 검증한다.
    val groupOrg = Organization(id = 900L, name = "groupOrg")
    val groupProtectedProject = Project(id = 900L, name = "groupProtected", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
    val groupMemberUser = User(id = 900L, loginId = "groupMember", name = "groupMember")
    groupOrg.organizationUsers.add(OrganizationUser(user = groupMemberUser, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType)))

    // isAuthor 분기(작성자 우회)와 "비멤버는 거부" 분기를 서로 겹치지 않게 검증하려면 stranger를
    // 테스트 대상(비멤버/비작성자)으로만 쓰고, 엔티티의 작성자는 stranger와 무관한 별도 사용자로 둬야
    // 한다 — stranger를 그대로 작성자로 쓰면 isAuthor==true가 돼 "비멤버는 거부" 기대값이 깨진다.
    val fillerAuthor = User(id = 800L, loginId = "fillerAuthor", name = "fillerAuthor")

    describe("isAllowed(user, project, pullRequest, operation) - PULL_REQUEST 리소스 (추가 분기)") {
        val pr = PullRequest(id = 1000L, toProject = privateProject, fromProject = privateProject, contributor = stranger)
        val publicPr = PullRequest(id = 1001L, toProject = publicProject, fromProject = publicProject, contributor = stranger)
        val groupPr = PullRequest(id = 1002L, toProject = groupProtectedProject, fromProject = groupProtectedProject, contributor = stranger)

        it("사이트매니저는 항상 허용") {
            accessControl.isAllowed(siteManager, privateProject, pr, Operation.DELETE) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            restrictedAccessControl.isAllowed(null, privateProject, pr, Operation.READ) shouldBe false
        }
        it("조직관리자는 소속 프로젝트에서 허용") {
            val orgPr = PullRequest(id = 1003L, toProject = protectedProject, fromProject = protectedProject, contributor = stranger)
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, protectedProject, orgPr, Operation.UPDATE) shouldBe true
        }
        it("익명은 기본 설정에서 PUBLIC 프로젝트 PR READ는 허용되지만 WATCH는 거부된다") {
            accessControl.isAllowed(null, publicProject, publicPr, Operation.READ) shouldBe true
            accessControl.isAllowed(null, publicProject, publicPr, Operation.WATCH) shouldBe false
        }
        it("게스트는 PUBLIC 프로젝트 PR READ는 거부, WATCH는 허용") {
            accessControl.isAllowed(guest, publicProject, publicPr, Operation.READ) shouldBe false
            accessControl.isAllowed(guest, publicProject, publicPr, Operation.WATCH) shouldBe true
        }
        it("멤버는 READ/UPDATE/DELETE 모두 허용, 비멤버는 DELETE 거부") {
            accessControl.isAllowed(member, privateProject, pr, Operation.READ) shouldBe true
            accessControl.isAllowed(member, privateProject, pr, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(member, privateProject, pr, Operation.DELETE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, pr, Operation.DELETE) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 멤버에게 허용, 비멤버에게 거부") {
            accessControl.isAllowed(member, privateProject, pr, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(member, privateProject, pr, Operation.CLOSE) shouldBe true
            accessControl.isAllowed(member, privateProject, pr, Operation.REOPEN) shouldBe true
            accessControl.isAllowed(stranger, privateProject, pr, Operation.ACCEPT) shouldBe false
        }
        it("조직(그룹) 멤버는 PROTECTED 프로젝트에서 READ/UPDATE/WATCH/ACCEPT 모두 허용") {
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupPr, Operation.READ) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupPr, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupPr, Operation.WATCH) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupPr, Operation.ACCEPT) shouldBe true
        }
        it("정의되지 않은 연산(LEAVE/ASSIGN_ISSUE)은 멤버라도 거부(else 분기)") {
            accessControl.isAllowed(member, privateProject, pr, Operation.LEAVE) shouldBe false
            accessControl.isAllowed(member, privateProject, pr, Operation.ASSIGN_ISSUE) shouldBe false
        }
    }

    describe("isAllowed(user, project, commitComment, operation) - COMMIT_COMMENT 리소스 (추가 분기)") {
        val comment = CommitComment(id = 1010L, project = privateProject, author = UserIdent(fillerAuthor), commitId = "c1")
        val publicComment = CommitComment(id = 1011L, project = publicProject, author = UserIdent(fillerAuthor), commitId = "c2")
        val groupComment = CommitComment(id = 1012L, project = groupProtectedProject, author = UserIdent(fillerAuthor), commitId = "c3")

        it("사이트매니저는 항상 허용") {
            accessControl.isAllowed(siteManager, privateProject, comment, Operation.DELETE) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            restrictedAccessControl.isAllowed(null, privateProject, comment, Operation.READ) shouldBe false
        }
        it("조직관리자는 소속 프로젝트에서 허용") {
            val orgComment = CommitComment(id = 1013L, project = protectedProject, author = UserIdent(fillerAuthor), commitId = "c4")
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, protectedProject, orgComment, Operation.UPDATE) shouldBe true
        }
        it("매니저는 작성자가 아니어도 허용") {
            accessControl.isAllowed(managerUser, privateProject, comment, Operation.DELETE) shouldBe true
        }
        it("익명은 기본 설정에서 PUBLIC 프로젝트 댓글 READ는 허용, WATCH는 거부") {
            accessControl.isAllowed(null, publicProject, publicComment, Operation.READ) shouldBe true
            accessControl.isAllowed(null, publicProject, publicComment, Operation.WATCH) shouldBe false
        }
        it("게스트는 READ 거부, WATCH 허용") {
            accessControl.isAllowed(guest, publicProject, publicComment, Operation.READ) shouldBe false
            accessControl.isAllowed(guest, publicProject, publicComment, Operation.WATCH) shouldBe true
        }
        it("멤버는 READ/UPDATE 허용, 비멤버는 UPDATE 거부") {
            accessControl.isAllowed(member, privateProject, comment, Operation.READ) shouldBe true
            accessControl.isAllowed(member, privateProject, comment, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, comment, Operation.UPDATE) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 멤버에게 허용, 비멤버에게 거부") {
            accessControl.isAllowed(member, privateProject, comment, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(member, privateProject, comment, Operation.CLOSE) shouldBe true
            accessControl.isAllowed(member, privateProject, comment, Operation.REOPEN) shouldBe true
            accessControl.isAllowed(stranger, privateProject, comment, Operation.CLOSE) shouldBe false
        }
        it("조직(그룹) 멤버는 PROTECTED 프로젝트에서 READ/UPDATE/WATCH/ACCEPT 모두 허용") {
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupComment, Operation.READ) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupComment, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupComment, Operation.WATCH) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupComment, Operation.ACCEPT) shouldBe true
        }
        it("정의되지 않은 연산은 멤버라도 거부(else 분기)") {
            accessControl.isAllowed(member, privateProject, comment, Operation.LEAVE) shouldBe false
        }
        it("작성자가 없는(author=null) 댓글은 비멤버면 거부") {
            val noAuthorComment = CommitComment(id = 1014L, project = privateProject, author = null, commitId = "c5")
            accessControl.isAllowed(stranger, privateProject, noAuthorComment, Operation.UPDATE) shouldBe false
        }
    }

    describe("isAllowed(user, project, commentThread, operation) - COMMENT_THREAD 리소스 (추가 분기)") {
        val thread = SimpleCommentThread(id = 1020L, project = privateProject, author = UserIdent(fillerAuthor))
        val publicThread = SimpleCommentThread(id = 1021L, project = publicProject, author = UserIdent(fillerAuthor))
        val groupThread = SimpleCommentThread(id = 1022L, project = groupProtectedProject, author = UserIdent(fillerAuthor))

        it("사이트매니저는 항상 허용") {
            accessControl.isAllowed(siteManager, privateProject, thread, Operation.DELETE) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            restrictedAccessControl.isAllowed(null, privateProject, thread, Operation.READ) shouldBe false
        }
        it("조직관리자는 소속 프로젝트에서 허용") {
            val orgThread = SimpleCommentThread(id = 1023L, project = protectedProject, author = UserIdent(fillerAuthor))
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, protectedProject, orgThread, Operation.UPDATE) shouldBe true
        }
        it("매니저는 작성자가 아니어도 허용") {
            accessControl.isAllowed(managerUser, privateProject, thread, Operation.DELETE) shouldBe true
        }
        it("익명은 기본 설정에서 PUBLIC 프로젝트 스레드 READ는 허용, WATCH는 거부") {
            accessControl.isAllowed(null, publicProject, publicThread, Operation.READ) shouldBe true
            accessControl.isAllowed(null, publicProject, publicThread, Operation.WATCH) shouldBe false
        }
        it("게스트는 READ 거부, WATCH 허용") {
            accessControl.isAllowed(guest, publicProject, publicThread, Operation.READ) shouldBe false
            accessControl.isAllowed(guest, publicProject, publicThread, Operation.WATCH) shouldBe true
        }
        it("멤버는 READ/UPDATE 허용, 비멤버는 UPDATE 거부") {
            accessControl.isAllowed(member, privateProject, thread, Operation.READ) shouldBe true
            accessControl.isAllowed(member, privateProject, thread, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, thread, Operation.UPDATE) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 멤버에게 허용, 비멤버에게 거부") {
            accessControl.isAllowed(member, privateProject, thread, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(member, privateProject, thread, Operation.CLOSE) shouldBe true
            accessControl.isAllowed(member, privateProject, thread, Operation.REOPEN) shouldBe true
            accessControl.isAllowed(stranger, privateProject, thread, Operation.REOPEN) shouldBe false
        }
        it("조직(그룹) 멤버는 PROTECTED 프로젝트에서 READ/UPDATE/WATCH/ACCEPT 모두 허용") {
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupThread, Operation.READ) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupThread, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupThread, Operation.WATCH) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupThread, Operation.ACCEPT) shouldBe true
        }
        it("정의되지 않은 연산은 멤버라도 거부(else 분기)") {
            accessControl.isAllowed(member, privateProject, thread, Operation.LEAVE) shouldBe false
        }
        it("작성자가 없는(author=null) 스레드는 비멤버면 거부") {
            val noAuthorThread = SimpleCommentThread(id = 1024L, project = privateProject, author = null)
            accessControl.isAllowed(stranger, privateProject, noAuthorThread, Operation.UPDATE) shouldBe false
        }
    }

    describe("isAllowed(user, project, reviewComment, operation) - REVIEW_COMMENT 리소스 (추가 분기)") {
        val comment = ReviewComment(id = 1030L, author = UserIdent(fillerAuthor))
        val publicComment = ReviewComment(id = 1031L, author = UserIdent(fillerAuthor))
        val groupComment = ReviewComment(id = 1032L, author = UserIdent(fillerAuthor))

        it("사이트매니저는 항상 허용") {
            accessControl.isAllowed(siteManager, privateProject, comment, Operation.DELETE) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            restrictedAccessControl.isAllowed(null, privateProject, comment, Operation.READ) shouldBe false
        }
        it("조직관리자는 소속 프로젝트에서 허용") {
            val orgComment = ReviewComment(id = 1033L, author = UserIdent(fillerAuthor))
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, protectedProject, orgComment, Operation.UPDATE) shouldBe true
        }
        it("매니저는 작성자가 아니어도 허용") {
            accessControl.isAllowed(managerUser, privateProject, comment, Operation.DELETE) shouldBe true
        }
        it("익명은 기본 설정에서 PUBLIC 프로젝트 리뷰댓글 READ는 허용, WATCH는 거부") {
            accessControl.isAllowed(null, publicProject, publicComment, Operation.READ) shouldBe true
            accessControl.isAllowed(null, publicProject, publicComment, Operation.WATCH) shouldBe false
        }
        it("게스트는 READ 거부, WATCH 허용") {
            accessControl.isAllowed(guest, publicProject, publicComment, Operation.READ) shouldBe false
            accessControl.isAllowed(guest, publicProject, publicComment, Operation.WATCH) shouldBe true
        }
        it("멤버는 READ/UPDATE 허용, 비멤버는 UPDATE 거부") {
            accessControl.isAllowed(member, privateProject, comment, Operation.READ) shouldBe true
            accessControl.isAllowed(member, privateProject, comment, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, comment, Operation.UPDATE) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 멤버에게 허용, 비멤버에게 거부") {
            accessControl.isAllowed(member, privateProject, comment, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(member, privateProject, comment, Operation.CLOSE) shouldBe true
            accessControl.isAllowed(member, privateProject, comment, Operation.REOPEN) shouldBe true
            accessControl.isAllowed(stranger, privateProject, comment, Operation.CLOSE) shouldBe false
        }
        it("조직(그룹) 멤버는 PROTECTED 프로젝트에서 READ/UPDATE/WATCH/ACCEPT 모두 허용") {
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupComment, Operation.READ) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupComment, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupComment, Operation.WATCH) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupComment, Operation.ACCEPT) shouldBe true
        }
        it("정의되지 않은 연산은 멤버라도 거부(else 분기)") {
            accessControl.isAllowed(member, privateProject, comment, Operation.LEAVE) shouldBe false
        }
        it("작성자가 없는(author=null) 리뷰댓글은 비멤버면 거부") {
            val noAuthorComment = ReviewComment(id = 1034L, author = null)
            accessControl.isAllowed(stranger, privateProject, noAuthorComment, Operation.UPDATE) shouldBe false
        }
    }

    describe("isAllowed(user, project, milestone, operation) - MILESTONE 리소스 (추가 분기, 작성자 개념 없음)") {
        val milestone = Milestone(id = 1040L, project = privateProject)
        val publicMilestone = Milestone(id = 1041L, project = publicProject)
        val groupMilestone = Milestone(id = 1042L, project = groupProtectedProject)

        it("사이트매니저는 항상 허용") {
            accessControl.isAllowed(siteManager, privateProject, milestone, Operation.DELETE) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            restrictedAccessControl.isAllowed(null, privateProject, milestone, Operation.READ) shouldBe false
        }
        it("조직관리자는 소속 프로젝트에서 허용") {
            val orgMilestone = Milestone(id = 1043L, project = protectedProject)
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, protectedProject, orgMilestone, Operation.UPDATE) shouldBe true
        }
        it("매니저는 이른 분기에서 즉시 허용") {
            accessControl.isAllowed(managerUser, privateProject, milestone, Operation.DELETE) shouldBe true
        }
        it("익명은 기본 설정에서 PUBLIC 프로젝트 마일스톤 READ는 허용, WATCH는 거부") {
            accessControl.isAllowed(null, publicProject, publicMilestone, Operation.READ) shouldBe true
            accessControl.isAllowed(null, publicProject, publicMilestone, Operation.WATCH) shouldBe false
        }
        it("게스트는 READ 거부, WATCH 허용") {
            accessControl.isAllowed(guest, publicProject, publicMilestone, Operation.READ) shouldBe false
            accessControl.isAllowed(guest, publicProject, publicMilestone, Operation.WATCH) shouldBe true
        }
        it("멤버는 READ/DELETE 허용, 비멤버는 DELETE 거부") {
            accessControl.isAllowed(member, privateProject, milestone, Operation.READ) shouldBe true
            accessControl.isAllowed(member, privateProject, milestone, Operation.DELETE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, milestone, Operation.DELETE) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 멤버에게 허용, 비멤버에게 거부") {
            accessControl.isAllowed(member, privateProject, milestone, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(member, privateProject, milestone, Operation.CLOSE) shouldBe true
            accessControl.isAllowed(member, privateProject, milestone, Operation.REOPEN) shouldBe true
            accessControl.isAllowed(stranger, privateProject, milestone, Operation.ACCEPT) shouldBe false
        }
        it("조직(그룹) 멤버는 PROTECTED 프로젝트에서 READ/UPDATE/WATCH/ACCEPT 모두 허용") {
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupMilestone, Operation.READ) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupMilestone, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupMilestone, Operation.WATCH) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupMilestone, Operation.ACCEPT) shouldBe true
        }
        it("정의되지 않은 연산은 멤버라도 거부(else 분기)") {
            accessControl.isAllowed(member, privateProject, milestone, Operation.LEAVE) shouldBe false
        }
    }

    describe("isAllowed(user, project, webhook, operation) - WEBHOOK 리소스 (추가 분기, 작성자 개념 없음)") {
        val webhook = Webhook(id = 1050L, project = privateProject)
        val publicWebhook = Webhook(id = 1051L, project = publicProject)
        val groupWebhook = Webhook(id = 1052L, project = groupProtectedProject)

        it("사이트매니저는 항상 허용") {
            accessControl.isAllowed(siteManager, privateProject, webhook, Operation.DELETE) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            restrictedAccessControl.isAllowed(null, privateProject, webhook, Operation.READ) shouldBe false
        }
        it("조직관리자는 소속 프로젝트에서 허용") {
            val orgWebhook = Webhook(id = 1053L, project = protectedProject)
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, protectedProject, orgWebhook, Operation.UPDATE) shouldBe true
        }
        it("매니저는 이른 분기에서 즉시 허용") {
            accessControl.isAllowed(managerUser, privateProject, webhook, Operation.DELETE) shouldBe true
        }
        it("익명은 기본 설정에서 PUBLIC 프로젝트 webhook READ는 허용, WATCH는 거부") {
            accessControl.isAllowed(null, publicProject, publicWebhook, Operation.READ) shouldBe true
            accessControl.isAllowed(null, publicProject, publicWebhook, Operation.WATCH) shouldBe false
        }
        it("게스트는 READ 거부, WATCH 허용") {
            accessControl.isAllowed(guest, publicProject, publicWebhook, Operation.READ) shouldBe false
            accessControl.isAllowed(guest, publicProject, publicWebhook, Operation.WATCH) shouldBe true
        }
        it("멤버는 READ/DELETE 허용, 비멤버는 DELETE 거부") {
            accessControl.isAllowed(member, privateProject, webhook, Operation.READ) shouldBe true
            accessControl.isAllowed(member, privateProject, webhook, Operation.DELETE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, webhook, Operation.DELETE) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 멤버에게 허용, 비멤버에게 거부") {
            accessControl.isAllowed(member, privateProject, webhook, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(member, privateProject, webhook, Operation.CLOSE) shouldBe true
            accessControl.isAllowed(member, privateProject, webhook, Operation.REOPEN) shouldBe true
            accessControl.isAllowed(stranger, privateProject, webhook, Operation.CLOSE) shouldBe false
        }
        it("조직(그룹) 멤버는 PROTECTED 프로젝트에서 READ/UPDATE/WATCH/ACCEPT 모두 허용") {
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupWebhook, Operation.READ) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupWebhook, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupWebhook, Operation.WATCH) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, groupWebhook, Operation.ACCEPT) shouldBe true
        }
        it("정의되지 않은 연산은 멤버라도 거부(else 분기)") {
            accessControl.isAllowed(member, privateProject, webhook, Operation.LEAVE) shouldBe false
        }
    }

    // CODE 특수 케이스(DELETE 무조건 거부/매니저 우회)는 AccessControlSpec.kt에 이미 있어 여기서는
    // 중복하지 않고, 그 외 아직 커버되지 않은 사이트매니저/익명/조직관리자/매니저/게스트/그룹멤버/else
    // 분기 및 CODE가 아닌 타입의 DELETE 거부 케이스만 보강한다.
    describe("isAllowed(user, project, resourceType, operation) - 엔티티 없는 일반 리소스 (추가 분기)") {
        it("사이트매니저는 항상 허용") {
            accessControl.isAllowed(siteManager, privateProject, ResourceType.WIKI_PAGE, Operation.DELETE) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            restrictedAccessControl.isAllowed(null, privateProject, ResourceType.WIKI_PAGE, Operation.READ) shouldBe false
        }
        it("조직관리자는 소속 프로젝트에서 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, protectedProject, ResourceType.WIKI_PAGE, Operation.UPDATE) shouldBe true
        }
        it("매니저는 이른 분기에서 즉시 허용") {
            accessControl.isAllowed(managerUser, privateProject, ResourceType.WIKI_PAGE, Operation.DELETE) shouldBe true
        }
        it("익명은 기본 설정에서 PUBLIC 프로젝트 리소스 READ는 허용, WATCH는 거부") {
            accessControl.isAllowed(null, publicProject, ResourceType.WIKI_PAGE, Operation.READ) shouldBe true
            accessControl.isAllowed(null, publicProject, ResourceType.WIKI_PAGE, Operation.WATCH) shouldBe false
        }
        it("게스트는 READ 거부, WATCH 허용") {
            accessControl.isAllowed(guest, publicProject, ResourceType.WIKI_PAGE, Operation.READ) shouldBe false
            accessControl.isAllowed(guest, publicProject, ResourceType.WIKI_PAGE, Operation.WATCH) shouldBe true
        }
        it("멤버는 READ/UPDATE 허용, 비멤버는 UPDATE 거부") {
            accessControl.isAllowed(member, privateProject, ResourceType.WIKI_PAGE, Operation.READ) shouldBe true
            accessControl.isAllowed(member, privateProject, ResourceType.WIKI_PAGE, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(stranger, privateProject, ResourceType.WIKI_PAGE, Operation.UPDATE) shouldBe false
        }
        it("CODE가 아닌 타입은 비멤버면 DELETE 거부(멤버 허용 케이스는 AccessControlSpec.kt에 이미 있음)") {
            accessControl.isAllowed(stranger, privateProject, ResourceType.WIKI_PAGE, Operation.DELETE) shouldBe false
        }
        it("ACCEPT/CLOSE/REOPEN은 멤버에게 허용, 비멤버에게 거부") {
            accessControl.isAllowed(member, privateProject, ResourceType.WIKI_PAGE, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(member, privateProject, ResourceType.WIKI_PAGE, Operation.CLOSE) shouldBe true
            accessControl.isAllowed(member, privateProject, ResourceType.WIKI_PAGE, Operation.REOPEN) shouldBe true
            accessControl.isAllowed(stranger, privateProject, ResourceType.WIKI_PAGE, Operation.REOPEN) shouldBe false
        }
        it("조직(그룹) 멤버는 PROTECTED 프로젝트에서 READ/UPDATE/WATCH/ACCEPT 모두 허용") {
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, ResourceType.WIKI_PAGE, Operation.READ) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, ResourceType.WIKI_PAGE, Operation.UPDATE) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, ResourceType.WIKI_PAGE, Operation.WATCH) shouldBe true
            accessControl.isAllowed(groupMemberUser, groupProtectedProject, ResourceType.WIKI_PAGE, Operation.ACCEPT) shouldBe true
        }
        it("정의되지 않은 연산은 멤버라도 거부(else 분기)") {
            accessControl.isAllowed(member, privateProject, ResourceType.WIKI_PAGE, Operation.LEAVE) shouldBe false
        }
        it("CODE 타입도 비멤버면 일반 규칙(멤버 아니면 거부)을 그대로 따른다(READ)") {
            accessControl.isAllowed(stranger, privateProject, ResourceType.CODE, Operation.READ) shouldBe false
        }
    }

    // 기존 AccessControlSpec.kt는 destinationUser/receivingOrg가 각각 존재하는 케이스 위주로 커버돼
    // 있어, 여기서는 사이트매니저 우회, 익명 차단, ACCEPT가 아닌 연산의 user==null 조합, 대상/조직 모두
    // 존재하지 않는 케이스를 보강한다.
    describe("isAllowed(user, projectTransfer, operation) - PROJECT_TRANSFER 리소스 (추가 분기)") {
        it("사이트매니저는 항상 허용") {
            val transfer = ProjectTransfer(id = 1060L, sender = stranger, destination = "anyone", project = privateProject)
            accessControl.isAllowed(siteManager, transfer, Operation.READ) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            val transfer = ProjectTransfer(id = 1061L, sender = stranger, destination = "anyone", project = privateProject)
            restrictedAccessControl.isAllowed(null, transfer, Operation.ACCEPT) shouldBe false
        }
        it("기본 설정에서 ACCEPT + 익명(user=null)은 거부") {
            val transfer = ProjectTransfer(id = 1062L, sender = stranger, destination = "anyone", project = privateProject)
            accessControl.isAllowed(null, transfer, Operation.ACCEPT) shouldBe false
        }
        it("대상 loginId도, 같은 이름의 조직도 존재하지 않으면 거부") {
            every { userRepository.findByLoginId("nowhere") } returns Optional.empty()
            every { organizationRepository.findByName("nowhere") } returns Optional.empty()
            val transfer = ProjectTransfer(id = 1063L, sender = stranger, destination = "nowhere", project = privateProject)
            accessControl.isAllowed(stranger, transfer, Operation.ACCEPT) shouldBe false
        }
    }

    // containerType별 위임 분기를 대상으로, AccessControlSpec.kt가 이미 다룬 NOT_A_RESOURCE/USER_AVATAR/
    // ISSUE_POST(일부)/ORGANIZATION(일부) 외의 컨테이너 타입(USER, BOARD_POST, MILESTONE, COMMIT_COMMENT,
    // REVIEW_COMMENT), containerId 파싱 실패, 각 컨테이너의 "찾을 수 없음" 및 목록에 없는 타입(else) 분기를
    // 보강한다.
    describe("isAllowedAttachment(user, attachment, operation) - 컨테이너 동적 해석 (추가 분기)") {
        it("사이트매니저는 항상 허용") {
            val attachment = Attachment(id = 1070L, containerType = ResourceType.NOT_A_RESOURCE, containerId = "0", ownerLoginId = "someone")
            accessControl.isAllowedAttachment(siteManager, attachment, Operation.DELETE) shouldBe true
        }
        it("allowsAnonymousAccess=false면 익명은 거부") {
            val attachment = Attachment(id = 1071L, containerType = ResourceType.NOT_A_RESOURCE, containerId = "0", ownerLoginId = "someone")
            restrictedAccessControl.isAllowedAttachment(null, attachment, Operation.READ) shouldBe false
        }
        it("containerType=USER(코드리뷰 임시 첨부)도 NOT_A_RESOURCE와 동일하게 업로더 본인만 허용") {
            val attachment = Attachment(id = 1072L, containerType = ResourceType.USER, containerId = "0", ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(member, attachment, Operation.READ) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachment, Operation.READ) shouldBe false
        }
        it("containerId가 숫자로 파싱되지 않으면 거부") {
            val attachment = Attachment(id = 1073L, containerType = ResourceType.ORGANIZATION, containerId = "not-a-number", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
        it("ORGANIZATION 컨테이너를 찾을 수 없으면 거부") {
            every { organizationRepository.findById(9999L) } returns Optional.empty()
            val attachment = Attachment(id = 1074L, containerType = ResourceType.ORGANIZATION, containerId = "9999", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
        it("ORGANIZATION 컨테이너의 READ/UPDATE 이외 연산은 거부") {
            every { organizationRepository.findById(1L) } returns Optional.of(org)
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            val attachment = Attachment(id = 1075L, containerType = ResourceType.ORGANIZATION, containerId = "1", ownerLoginId = "x")
            accessControl.isAllowedAttachment(orgAdminUser, attachment, Operation.WATCH) shouldBe false
        }
        it("ISSUE_POST 컨테이너의 UPDATE/DELETE는 이슈 UPDATE 권한을 따른다") {
            val issue = Issue(id = 1076L, project = privateProject, authorId = member.id)
            every { issueRepository.findById(1076L) } returns Optional.of(issue)
            val attachment = Attachment(id = 1076L, containerType = ResourceType.ISSUE_POST, containerId = "1076", ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(member, attachment, Operation.DELETE) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachment, Operation.DELETE) shouldBe false
        }
        it("ISSUE_POST 컨테이너를 찾을 수 없으면 거부") {
            every { issueRepository.findById(8888L) } returns Optional.empty()
            val attachment = Attachment(id = 1077L, containerType = ResourceType.ISSUE_POST, containerId = "8888", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.UPDATE) shouldBe false
        }
        it("BOARD_POST 컨테이너는 게시글 권한 규칙을 그대로 따른다") {
            val posting = Posting(id = 1078L, project = privateProject, authorId = stranger.id)
            every { postingRepository.findById(1078L) } returns Optional.of(posting)
            val attachment = Attachment(id = 1078L, containerType = ResourceType.BOARD_POST, containerId = "1078", ownerLoginId = "x")
            accessControl.isAllowedAttachment(member, attachment, Operation.READ) shouldBe true
            accessControl.isAllowedAttachment(guest, attachment, Operation.READ) shouldBe false
        }
        it("BOARD_POST 컨테이너를 찾을 수 없으면 거부") {
            every { postingRepository.findById(7777L) } returns Optional.empty()
            val attachment = Attachment(id = 1079L, containerType = ResourceType.BOARD_POST, containerId = "7777", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.DELETE) shouldBe false
        }
        it("MILESTONE 컨테이너는 마일스톤 권한 규칙을 그대로 따른다") {
            val milestone = Milestone(id = 1080L, project = privateProject)
            every { milestoneRepository.findById(1080L) } returns Optional.of(milestone)
            val attachment = Attachment(id = 1080L, containerType = ResourceType.MILESTONE, containerId = "1080", ownerLoginId = "x")
            accessControl.isAllowedAttachment(member, attachment, Operation.UPDATE) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachment, Operation.UPDATE) shouldBe false
        }
        it("MILESTONE 컨테이너를 찾을 수 없으면 거부") {
            every { milestoneRepository.findById(6666L) } returns Optional.empty()
            val attachment = Attachment(id = 1081L, containerType = ResourceType.MILESTONE, containerId = "6666", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
        it("COMMIT_COMMENT 컨테이너는 커밋댓글 권한 규칙을 그대로 따른다") {
            val commitComment = CommitComment(id = 1082L, project = privateProject, author = UserIdent(fillerAuthor), commitId = "cc1")
            every { commitCommentRepository.findById(1082L) } returns Optional.of(commitComment)
            val attachment = Attachment(id = 1082L, containerType = ResourceType.COMMIT_COMMENT, containerId = "1082", ownerLoginId = "x")
            accessControl.isAllowedAttachment(member, attachment, Operation.READ) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachment, Operation.DELETE) shouldBe false
        }
        it("COMMIT_COMMENT의 project가 없으면 거부") {
            val commitCommentNoProject = CommitComment(id = 1083L, project = null, author = UserIdent(fillerAuthor), commitId = "cc2")
            every { commitCommentRepository.findById(1083L) } returns Optional.of(commitCommentNoProject)
            val attachment = Attachment(id = 1083L, containerType = ResourceType.COMMIT_COMMENT, containerId = "1083", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
        it("COMMIT_COMMENT 컨테이너를 찾을 수 없으면 거부") {
            every { commitCommentRepository.findById(5555L) } returns Optional.empty()
            val attachment = Attachment(id = 1084L, containerType = ResourceType.COMMIT_COMMENT, containerId = "5555", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
        it("REVIEW_COMMENT 컨테이너는 리뷰댓글 권한 규칙을 그대로 따른다") {
            val thread = SimpleCommentThread(id = 1085L, project = privateProject, author = UserIdent(fillerAuthor))
            val reviewComment = ReviewComment(id = 1085L, author = UserIdent(fillerAuthor), thread = thread)
            every { reviewCommentRepository.findById(1085L) } returns Optional.of(reviewComment)
            val attachment = Attachment(id = 1085L, containerType = ResourceType.REVIEW_COMMENT, containerId = "1085", ownerLoginId = "x")
            accessControl.isAllowedAttachment(member, attachment, Operation.READ) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachment, Operation.DELETE) shouldBe false
        }
        it("REVIEW_COMMENT의 thread(프로젝트 경로)가 없으면 거부") {
            val reviewCommentNoThread = ReviewComment(id = 1086L, author = UserIdent(fillerAuthor), thread = null)
            every { reviewCommentRepository.findById(1086L) } returns Optional.of(reviewCommentNoThread)
            val attachment = Attachment(id = 1086L, containerType = ResourceType.REVIEW_COMMENT, containerId = "1086", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
        it("REVIEW_COMMENT 컨테이너를 찾을 수 없으면 거부") {
            every { reviewCommentRepository.findById(4444L) } returns Optional.empty()
            val attachment = Attachment(id = 1087L, containerType = ResourceType.REVIEW_COMMENT, containerId = "4444", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
        it("목록에 없는 컨테이너 타입(CODE)은 항상 거부(else 분기)") {
            val attachment = Attachment(id = 1088L, containerType = ResourceType.CODE, containerId = "1", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
    }
})
