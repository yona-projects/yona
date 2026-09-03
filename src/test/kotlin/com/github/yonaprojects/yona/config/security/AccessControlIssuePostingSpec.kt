package com.github.yonaprojects.yona.config.security

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueSharer
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.util.Optional

// P1-85 1b AccessControl.isAllowed(...) 오버로드 중 ISSUE_POST/ISSUE_COMMENT/BOARD_POST/NONISSUE_COMMENT
// 4개 리소스 타입의 JaCoCo 분기 커버리지 보강 전담 파일(TASK-0259, 커버리지 백로그). 파일 충돌 방지를
// 위해 기존 AccessControlSpec.kt는 건드리지 않고, 그 파일의 fixture 구성을 그대로 복사해 이 파일 안에서
// 독립적으로 재구성한다. AccessControlSpec.kt에 이미 있는 기본 케이스(작성자/담당자/공유자 우회 등)는
// 중복하지 않고, 4개 메서드 각각의 미실행 분기(사이트매니저/조직관리자/매니저 우회, READ/UPDATE/DELETE/
// ACCEPT·CLOSE·REOPEN/WATCH 각 연산의 OR항 true/false 조합, else 분기, 익명 접근 차단 설정)를 골고루
// 추가로 커버한다.
class AccessControlIssuePostingSpec : DescribeSpec({
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

    // isAnonymousNotAllowed() && user==null 분기의 true 경로(익명 완전 차단)를 태우기 위한 별도 인스턴스.
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
    // isAllowedIfGroupMember()는 organizationUserRepository가 아니라 Organization.organizationUsers
    // 엔티티 컬렉션을 직접 순회하므로, repository stub이 아니라 엔티티 컬렉션에 직접 추가해야 한다.
    val groupMemberUser = User(id = 7L, loginId = "groupmember", name = "groupmember")

    member.projectUsers.add(ProjectUser(id = 100L, user = member, project = privateProject, role = Role(id = RoleType.MEMBER.roleType)))
    member.projectUsers.add(ProjectUser(id = 102L, user = member, project = protectedProject, role = Role(id = RoleType.MEMBER.roleType)))
    managerUser.projectUsers.add(ProjectUser(id = 101L, user = managerUser, project = privateProject, role = Role(id = RoleType.MANAGER.roleType)))
    org.organizationUsers.add(OrganizationUser(id = 900L, user = groupMemberUser, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType)))

    fun stubOrgRole(organization: Organization, user: User, roleType: RoleType) {
        every { organizationUserRepository.findByOrganizationIdAndUserId(organization.id!!, user.id!!) } returns
            Optional.of(OrganizationUser(user = user, organization = organization, role = Role(id = roleType.roleType)))
    }

    // ==================== ISSUE_POST ====================
    describe("isAllowed(user, project, issue, operation) - ISSUE_POST 미실행 분기 보강") {
        it("익명 접근 차단 설정에서 비로그인 사용자는 READ도 거부") {
            val issue = Issue(id = 1000L, project = privateProject, authorId = stranger.id)
            restrictedAccessControl.isAllowed(null, privateProject, issue, Operation.READ) shouldBe false
        }
        it("익명 접근 차단 설정이라도 로그인한 프로젝트 멤버는 정상적으로 허용된다") {
            val issue = Issue(id = 1001L, project = privateProject, authorId = stranger.id)
            restrictedAccessControl.isAllowed(member, privateProject, issue, Operation.UPDATE) shouldBe true
        }
        it("사이트매니저는 어떤 이슈든 연산과 무관하게 항상 허용") {
            val issue = Issue(id = 1002L, project = publicProject, authorId = stranger.id)
            accessControl.isAllowed(siteManager, publicProject, issue, Operation.DELETE) shouldBe true
        }
        it("조직 관리자는 소속 프로젝트의 이슈 연산 전체를 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            val issue = Issue(id = 1003L, project = protectedProject, authorId = stranger.id)
            accessControl.isAllowed(orgAdminUser, protectedProject, issue, Operation.DELETE) shouldBe true
        }
        it("매니저는 작성자/담당자가 아니어도 이슈 연산 전체를 허용") {
            val issue = Issue(id = 1004L, project = privateProject, authorId = stranger.id)
            accessControl.isAllowed(managerUser, privateProject, issue, Operation.DELETE) shouldBe true
        }
        it("authorId가 null인 이슈는 작성자 우회가 적용되지 않는다") {
            val issue = Issue(id = 1005L, project = privateProject, authorId = null)
            accessControl.isAllowed(stranger, privateProject, issue, Operation.READ) shouldBe false
        }
        it("담당자로 지정되지 않은 사용자는 담당자 우회가 적용되지 않는다") {
            val assignee = Assignee(user = member, project = privateProject)
            val issue = Issue(id = 1006L, project = privateProject, authorId = null, assignee = assignee)
            accessControl.isAllowed(stranger, privateProject, issue, Operation.READ) shouldBe false
        }

        it("READ: 공개 프로젝트는 게스트가 아닌 비회원 로그인 사용자에게도 허용") {
            val issue = Issue(id = 1010L, project = publicProject, authorId = managerUser.id)
            accessControl.isAllowed(stranger, publicProject, issue, Operation.READ) shouldBe true
        }
        it("READ: 비공개 프로젝트 비회원은 거부") {
            val issue = Issue(id = 1011L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, issue, Operation.READ) shouldBe false
        }
        it("READ: 프로젝트 멤버는 작성자가 아니어도 허용") {
            val issue = Issue(id = 1012L, project = privateProject, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, issue, Operation.READ) shouldBe true
        }
        it("READ: 비공개 프로젝트에서 비로그인 사용자는 거부(공유자/그룹멤버 조건도 user!=null에서 막힘)") {
            val issue = Issue(id = 1013L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, issue, Operation.READ) shouldBe false
        }
        it("READ: 조직 그룹멤버는 프로젝트 비멤버라도 PROTECTED 프로젝트 이슈를 읽을 수 있다") {
            val issue = Issue(id = 1014L, project = protectedProject, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, issue, Operation.READ) shouldBe true
        }

        it("UPDATE: 그룹멤버는 프로젝트 비멤버라도 이슈를 수정할 수 있다") {
            val issue = Issue(id = 1020L, project = protectedProject, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, issue, Operation.UPDATE) shouldBe true
        }
        it("UPDATE: 비로그인 사용자는 거부") {
            val issue = Issue(id = 1021L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, issue, Operation.UPDATE) shouldBe false
        }

        it("DELETE: 프로젝트 멤버는 작성자가 아니어도 허용") {
            val issue = Issue(id = 1030L, project = privateProject, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, issue, Operation.DELETE) shouldBe true
        }
        it("DELETE: 비회원은 거부") {
            val issue = Issue(id = 1031L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, issue, Operation.DELETE) shouldBe false
        }
        it("DELETE: 비로그인 사용자는 거부") {
            val issue = Issue(id = 1032L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, issue, Operation.DELETE) shouldBe false
        }

        it("ACCEPT: 프로젝트 멤버는 허용") {
            val issue = Issue(id = 1040L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, issue, Operation.ACCEPT) shouldBe true
        }
        it("CLOSE: 그룹멤버는 허용") {
            val issue = Issue(id = 1041L, project = protectedProject, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, issue, Operation.CLOSE) shouldBe true
        }
        it("REOPEN: 비회원은 거부") {
            val issue = Issue(id = 1042L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, issue, Operation.REOPEN) shouldBe false
        }

        it("WATCH: 공개 프로젝트에서는 게스트도 허용(READ와 달리 게스트 제한 없음)") {
            val issue = Issue(id = 1050L, project = publicProject, authorId = managerUser.id)
            accessControl.isAllowed(guest, publicProject, issue, Operation.WATCH) shouldBe true
        }
        it("WATCH: 공개 프로젝트라도 비로그인 사용자는 거부") {
            val issue = Issue(id = 1051L, project = publicProject, authorId = managerUser.id)
            accessControl.isAllowed(null, publicProject, issue, Operation.WATCH) shouldBe false
        }
        it("WATCH: 비공개 프로젝트 멤버는 허용") {
            val issue = Issue(id = 1052L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, issue, Operation.WATCH) shouldBe true
        }
        it("WATCH: 그룹멤버는 허용") {
            val issue = Issue(id = 1053L, project = protectedProject, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, issue, Operation.WATCH) shouldBe true
        }

        it("LEAVE 연산은 이슈 리소스에 정의돼 있지 않아 항상 거부(else 분기)") {
            val issue = Issue(id = 1060L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, issue, Operation.LEAVE) shouldBe false
        }
        it("ASSIGN_ISSUE 연산도 이슈 리소스에 정의돼 있지 않아 항상 거부(else 분기)") {
            val issue = Issue(id = 1061L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, issue, Operation.ASSIGN_ISSUE) shouldBe false
        }
    }

    // ==================== ISSUE_COMMENT ====================
    describe("isAllowed(user, project, issueComment, operation) - ISSUE_COMMENT 미실행 분기 보강") {
        it("익명 접근 차단 설정에서 비로그인 사용자는 READ도 거부") {
            val issue = Issue(id = 1100L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1200L, issue = issue, authorId = stranger.id)
            restrictedAccessControl.isAllowed(null, privateProject, comment, Operation.READ) shouldBe false
        }
        it("익명 접근 차단 설정이라도 로그인한 프로젝트 멤버는 정상적으로 허용된다") {
            val issue = Issue(id = 1101L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1201L, issue = issue, authorId = stranger.id)
            restrictedAccessControl.isAllowed(member, privateProject, comment, Operation.UPDATE) shouldBe true
        }
        it("사이트매니저는 어떤 이슈 댓글이든 항상 허용") {
            val issue = Issue(id = 1102L, project = publicProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1202L, issue = issue, authorId = stranger.id)
            accessControl.isAllowed(siteManager, publicProject, comment, Operation.DELETE) shouldBe true
        }
        it("조직 관리자는 소속 프로젝트의 이슈 댓글 연산 전체를 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            val issue = Issue(id = 1103L, project = protectedProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1203L, issue = issue, authorId = stranger.id)
            accessControl.isAllowed(orgAdminUser, protectedProject, comment, Operation.DELETE) shouldBe true
        }
        it("매니저는 작성자가 아니어도 이슈 댓글 연산 전체를 허용") {
            val issue = Issue(id = 1104L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1204L, issue = issue, authorId = stranger.id)
            accessControl.isAllowed(managerUser, privateProject, comment, Operation.DELETE) shouldBe true
        }
        it("authorId가 null인 댓글은 작성자 우회가 적용되지 않는다") {
            val issue = Issue(id = 1105L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1205L, issue = issue, authorId = null)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.READ) shouldBe false
        }

        it("READ: 공개 프로젝트는 게스트가 아닌 비회원 로그인 사용자에게도 허용") {
            val issue = Issue(id = 1110L, project = publicProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1210L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(stranger, publicProject, comment, Operation.READ) shouldBe true
        }
        it("READ: 비공개 프로젝트 비회원은 거부") {
            val issue = Issue(id = 1111L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1211L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.READ) shouldBe false
        }
        it("READ: 프로젝트 멤버는 작성자가 아니어도 허용") {
            val issue = Issue(id = 1112L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1212L, issue = issue, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.READ) shouldBe true
        }
        it("READ: 비공개 프로젝트에서 비로그인 사용자는 거부") {
            val issue = Issue(id = 1113L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1213L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.READ) shouldBe false
        }
        it("READ: 이슈가 공유되지 않은 댓글은 공유자 우회가 적용되지 않는다") {
            val issue = Issue(id = 1114L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1214L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.READ) shouldBe false
        }
        it("READ: 그룹멤버는 프로젝트 비멤버라도 PROTECTED 프로젝트 댓글을 읽을 수 있다") {
            val issue = Issue(id = 1115L, project = protectedProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1215L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, comment, Operation.READ) shouldBe true
        }

        it("UPDATE: 그룹멤버는 프로젝트 비멤버라도 댓글을 수정할 수 있다") {
            val issue = Issue(id = 1120L, project = protectedProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1220L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, comment, Operation.UPDATE) shouldBe true
        }
        it("UPDATE: 비로그인 사용자는 거부") {
            val issue = Issue(id = 1121L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1221L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.UPDATE) shouldBe false
        }

        it("DELETE: 프로젝트 멤버는 작성자가 아니어도 허용") {
            val issue = Issue(id = 1130L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1230L, issue = issue, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.DELETE) shouldBe true
        }
        it("DELETE: 비회원은 거부") {
            val issue = Issue(id = 1131L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1231L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.DELETE) shouldBe false
        }
        it("DELETE: 비로그인 사용자는 거부") {
            val issue = Issue(id = 1132L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1232L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.DELETE) shouldBe false
        }

        it("ACCEPT: 프로젝트 멤버는 허용") {
            val issue = Issue(id = 1140L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1240L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.ACCEPT) shouldBe true
        }
        it("CLOSE: 그룹멤버는 허용") {
            val issue = Issue(id = 1141L, project = protectedProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1241L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, comment, Operation.CLOSE) shouldBe true
        }
        it("REOPEN: 비회원은 거부") {
            val issue = Issue(id = 1142L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1242L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.REOPEN) shouldBe false
        }

        it("WATCH: 공개 프로젝트에서는 게스트도 허용") {
            val issue = Issue(id = 1150L, project = publicProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1250L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(guest, publicProject, comment, Operation.WATCH) shouldBe true
        }
        it("WATCH: 공개 프로젝트라도 비로그인 사용자는 거부") {
            val issue = Issue(id = 1151L, project = publicProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1251L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(null, publicProject, comment, Operation.WATCH) shouldBe false
        }
        it("WATCH: 비공개 프로젝트 멤버는 허용") {
            val issue = Issue(id = 1152L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1252L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.WATCH) shouldBe true
        }
        it("WATCH: 그룹멤버는 허용") {
            val issue = Issue(id = 1153L, project = protectedProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1253L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, comment, Operation.WATCH) shouldBe true
        }

        it("LEAVE 연산은 댓글 리소스에 정의돼 있지 않아 항상 거부(else 분기)") {
            val issue = Issue(id = 1160L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1260L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.LEAVE) shouldBe false
        }
        it("ASSIGN_ISSUE 연산도 댓글 리소스에 정의돼 있지 않아 항상 거부(else 분기)") {
            val issue = Issue(id = 1161L, project = privateProject, authorId = managerUser.id)
            val comment = IssueComment(id = 1261L, issue = issue, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.ASSIGN_ISSUE) shouldBe false
        }
    }

    // ==================== BOARD_POST ====================
    describe("isAllowed(user, project, posting, operation) - BOARD_POST 미실행 분기 보강") {
        it("익명 접근 차단 설정에서 비로그인 사용자는 READ도 거부") {
            val posting = Posting(id = 1300L, project = privateProject, authorId = stranger.id)
            restrictedAccessControl.isAllowed(null, privateProject, posting, Operation.READ) shouldBe false
        }
        it("익명 접근 차단 설정이라도 로그인한 프로젝트 멤버는 정상적으로 허용된다") {
            val posting = Posting(id = 1301L, project = privateProject, authorId = stranger.id)
            restrictedAccessControl.isAllowed(member, privateProject, posting, Operation.UPDATE) shouldBe true
        }
        it("사이트매니저는 어떤 게시글이든 항상 허용") {
            val posting = Posting(id = 1302L, project = publicProject, authorId = stranger.id)
            accessControl.isAllowed(siteManager, publicProject, posting, Operation.DELETE) shouldBe true
        }
        it("조직 관리자는 소속 프로젝트의 게시글 연산 전체를 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            val posting = Posting(id = 1303L, project = protectedProject, authorId = stranger.id)
            accessControl.isAllowed(orgAdminUser, protectedProject, posting, Operation.DELETE) shouldBe true
        }
        it("매니저는 작성자가 아니어도 게시글 연산 전체를 허용") {
            val posting = Posting(id = 1304L, project = privateProject, authorId = stranger.id)
            accessControl.isAllowed(managerUser, privateProject, posting, Operation.DELETE) shouldBe true
        }
        it("authorId가 null인 게시글은 작성자 우회가 적용되지 않는다") {
            val posting = Posting(id = 1305L, project = privateProject, authorId = null)
            accessControl.isAllowed(stranger, privateProject, posting, Operation.READ) shouldBe false
        }

        it("READ: 공개 프로젝트는 게스트가 아닌 비회원 로그인 사용자에게도 허용") {
            val posting = Posting(id = 1310L, project = publicProject, authorId = managerUser.id)
            accessControl.isAllowed(stranger, publicProject, posting, Operation.READ) shouldBe true
        }
        it("READ: 비공개 프로젝트 비회원은 거부") {
            val posting = Posting(id = 1311L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, posting, Operation.READ) shouldBe false
        }
        it("READ: 프로젝트 멤버는 작성자가 아니어도 허용") {
            val posting = Posting(id = 1312L, project = privateProject, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, posting, Operation.READ) shouldBe true
        }
        it("READ: 비공개 프로젝트에서 비로그인 사용자는 거부") {
            val posting = Posting(id = 1313L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, posting, Operation.READ) shouldBe false
        }
        it("READ: 그룹멤버는 프로젝트 비멤버라도 PROTECTED 프로젝트 게시글을 읽을 수 있다") {
            val posting = Posting(id = 1314L, project = protectedProject, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, posting, Operation.READ) shouldBe true
        }

        it("UPDATE: 그룹멤버는 프로젝트 비멤버라도 게시글을 수정할 수 있다") {
            val posting = Posting(id = 1320L, project = protectedProject, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, posting, Operation.UPDATE) shouldBe true
        }
        it("UPDATE: 비로그인 사용자는 거부") {
            val posting = Posting(id = 1321L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, posting, Operation.UPDATE) shouldBe false
        }

        it("DELETE: 비로그인 사용자는 거부") {
            val posting = Posting(id = 1332L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, posting, Operation.DELETE) shouldBe false
        }

        it("ACCEPT: 프로젝트 멤버는 허용") {
            val posting = Posting(id = 1340L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, posting, Operation.ACCEPT) shouldBe true
        }
        it("CLOSE: 그룹멤버는 허용") {
            val posting = Posting(id = 1341L, project = protectedProject, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, posting, Operation.CLOSE) shouldBe true
        }
        it("REOPEN: 비회원은 거부") {
            val posting = Posting(id = 1342L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, posting, Operation.REOPEN) shouldBe false
        }

        it("WATCH: 공개 프로젝트에서는 게스트도 허용") {
            val posting = Posting(id = 1350L, project = publicProject, authorId = managerUser.id)
            accessControl.isAllowed(guest, publicProject, posting, Operation.WATCH) shouldBe true
        }
        it("WATCH: 공개 프로젝트라도 비로그인 사용자는 거부") {
            val posting = Posting(id = 1351L, project = publicProject, authorId = managerUser.id)
            accessControl.isAllowed(null, publicProject, posting, Operation.WATCH) shouldBe false
        }
        it("WATCH: 비공개 프로젝트 멤버는 허용") {
            val posting = Posting(id = 1352L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, posting, Operation.WATCH) shouldBe true
        }
        it("WATCH: 그룹멤버는 허용") {
            val posting = Posting(id = 1353L, project = protectedProject, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, posting, Operation.WATCH) shouldBe true
        }

        it("LEAVE 연산은 게시글 리소스에 정의돼 있지 않아 항상 거부(else 분기)") {
            val posting = Posting(id = 1360L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, posting, Operation.LEAVE) shouldBe false
        }
        it("ASSIGN_ISSUE 연산도 게시글 리소스에 정의돼 있지 않아 항상 거부(else 분기)") {
            val posting = Posting(id = 1361L, project = privateProject, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, posting, Operation.ASSIGN_ISSUE) shouldBe false
        }
    }

    // ==================== NONISSUE_COMMENT ====================
    describe("isAllowed(user, project, postingComment, operation) - NONISSUE_COMMENT 미실행 분기 보강") {
        it("익명 접근 차단 설정에서 비로그인 사용자는 READ도 거부") {
            val posting = Posting(id = 1400L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1500L, posting = posting, authorId = stranger.id)
            restrictedAccessControl.isAllowed(null, privateProject, comment, Operation.READ) shouldBe false
        }
        it("익명 접근 차단 설정이라도 로그인한 프로젝트 멤버는 정상적으로 허용된다") {
            val posting = Posting(id = 1401L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1501L, posting = posting, authorId = stranger.id)
            restrictedAccessControl.isAllowed(member, privateProject, comment, Operation.UPDATE) shouldBe true
        }
        it("사이트매니저는 어떤 게시글 댓글이든 항상 허용") {
            val posting = Posting(id = 1402L, project = publicProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1502L, posting = posting, authorId = stranger.id)
            accessControl.isAllowed(siteManager, publicProject, comment, Operation.DELETE) shouldBe true
        }
        it("조직 관리자는 소속 프로젝트의 게시글 댓글 연산 전체를 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            val posting = Posting(id = 1403L, project = protectedProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1503L, posting = posting, authorId = stranger.id)
            accessControl.isAllowed(orgAdminUser, protectedProject, comment, Operation.DELETE) shouldBe true
        }
        it("매니저는 작성자가 아니어도 게시글 댓글 연산 전체를 허용") {
            val posting = Posting(id = 1404L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1504L, posting = posting, authorId = stranger.id)
            accessControl.isAllowed(managerUser, privateProject, comment, Operation.DELETE) shouldBe true
        }
        it("authorId가 null인 댓글은 작성자 우회가 적용되지 않는다") {
            val posting = Posting(id = 1405L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1505L, posting = posting, authorId = null)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.READ) shouldBe false
        }

        it("READ: 공개 프로젝트는 게스트가 아닌 비회원 로그인 사용자에게도 허용") {
            val posting = Posting(id = 1410L, project = publicProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1510L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(stranger, publicProject, comment, Operation.READ) shouldBe true
        }
        it("READ: 비공개 프로젝트 비회원은 거부") {
            val posting = Posting(id = 1411L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1511L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.READ) shouldBe false
        }
        it("READ: 프로젝트 멤버는 작성자가 아니어도 허용") {
            val posting = Posting(id = 1412L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1512L, posting = posting, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.READ) shouldBe true
        }
        it("READ: 비공개 프로젝트에서 비로그인 사용자는 거부") {
            val posting = Posting(id = 1413L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1513L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.READ) shouldBe false
        }
        it("READ: 그룹멤버는 프로젝트 비멤버라도 PROTECTED 프로젝트 댓글을 읽을 수 있다") {
            val posting = Posting(id = 1414L, project = protectedProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1514L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, comment, Operation.READ) shouldBe true
        }

        it("UPDATE: 그룹멤버는 프로젝트 비멤버라도 댓글을 수정할 수 있다") {
            val posting = Posting(id = 1420L, project = protectedProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1520L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, comment, Operation.UPDATE) shouldBe true
        }
        it("UPDATE: 비로그인 사용자는 거부") {
            val posting = Posting(id = 1421L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1521L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.UPDATE) shouldBe false
        }

        it("DELETE: 프로젝트 멤버는 작성자가 아니어도 허용") {
            val posting = Posting(id = 1430L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1530L, posting = posting, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.DELETE) shouldBe true
        }
        it("DELETE: 비회원은 거부") {
            val posting = Posting(id = 1431L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1531L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.DELETE) shouldBe false
        }
        it("DELETE: 비로그인 사용자는 거부") {
            val posting = Posting(id = 1432L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1532L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(null, privateProject, comment, Operation.DELETE) shouldBe false
        }

        it("ACCEPT: 프로젝트 멤버는 허용") {
            val posting = Posting(id = 1440L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1540L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.ACCEPT) shouldBe true
        }
        it("CLOSE: 그룹멤버는 허용") {
            val posting = Posting(id = 1441L, project = protectedProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1541L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, comment, Operation.CLOSE) shouldBe true
        }
        it("REOPEN: 비회원은 거부") {
            val posting = Posting(id = 1442L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1542L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(stranger, privateProject, comment, Operation.REOPEN) shouldBe false
        }

        it("WATCH: 공개 프로젝트에서는 게스트도 허용") {
            val posting = Posting(id = 1450L, project = publicProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1550L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(guest, publicProject, comment, Operation.WATCH) shouldBe true
        }
        it("WATCH: 공개 프로젝트라도 비로그인 사용자는 거부") {
            val posting = Posting(id = 1451L, project = publicProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1551L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(null, publicProject, comment, Operation.WATCH) shouldBe false
        }
        it("WATCH: 비공개 프로젝트 멤버는 허용") {
            val posting = Posting(id = 1452L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1552L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.WATCH) shouldBe true
        }
        it("WATCH: 그룹멤버는 허용") {
            val posting = Posting(id = 1453L, project = protectedProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1553L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(groupMemberUser, protectedProject, comment, Operation.WATCH) shouldBe true
        }

        it("LEAVE 연산은 댓글 리소스에 정의돼 있지 않아 항상 거부(else 분기)") {
            val posting = Posting(id = 1460L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1560L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.LEAVE) shouldBe false
        }
        it("ASSIGN_ISSUE 연산도 댓글 리소스에 정의돼 있지 않아 항상 거부(else 분기)") {
            val posting = Posting(id = 1461L, project = privateProject, authorId = managerUser.id)
            val comment = PostingComment(id = 1561L, posting = posting, authorId = managerUser.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.ASSIGN_ISSUE) shouldBe false
        }
    }
})
