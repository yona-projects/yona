package dev.yona.access

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharer
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectTransfer
import dev.yona.domain.project.ProjectTransferRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v1.6 `utils.AccessControl`(`app/utils/AccessControl.java`)의 권한 규칙을 검증한다.
 *
 * 규칙 설명은 `yona-v2-wiki/subsystems/access-control.md`와 v1.6 위키 `domain/permissions-and-roles.md`를 본다.
 * 테스트는 규칙 한 줄이 테스트 하나에 대응하도록 썼다. 이름의 순서는 `AccessControl.java`의 평가 순서를 따른다.
 */
@Transactional
class AccessControlTest : AbstractIntegrationTest() {

    @Autowired private lateinit var accessControl: AccessControl
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var transfers: ProjectTransferRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var issueSharers: IssueSharerRepository

    // --- 테스트 데이터 도우미 ---

    private var sequence = 0

    private fun user(prefix: String = "u", guest: Boolean = false): User {
        val n = ++sequence
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, isGuest = guest))
    }

    private fun organization(): Organization = organizations.save(Organization(name = "org${++sequence}"))

    private fun project(scope: ProjectScope, owner: User, organization: Organization? = null): Project =
        projects.save(
            Project(name = "p${++sequence}", owner = owner.loginId, projectScope = scope, organization = organization)
        )

    private fun join(user: User, project: Project, role: RoleType) {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
    }

    private fun joinOrg(user: User, organization: Organization, role: RoleType) {
        organizationUsers.save(OrganizationUser(user = user, organization = organization, role = roles.findById(role.id).get()))
    }

    private fun projectResource(project: Project, type: ResourceType, id: String = "1", container: Resource? = null, authorId: Long? = null) =
        ProjectResource(id, type, project, container, authorId)

    private fun allowed(user: User?, resource: Resource, operation: Operation) = accessControl.isAllowed(user, resource, operation)

    // --- 최상위 규칙 ---

    @Test
    fun `사이트 관리자는 무엇이든 허용된다`() {
        val owner = user()
        val admin = user("admin")
        siteAdmins.save(SiteAdmin(admin = admin))
        val project = project(ProjectScope.PRIVATE, owner)
        Operation.entries.forEach { op ->
            assertTrue(allowed(admin, projectResource(project, ResourceType.ISSUE_POST), op), "$op")
        }
        assertTrue(allowed(admin, GlobalResource(owner.id.toString(), ResourceType.USER), Operation.DELETE))
    }

    @Test
    fun `프로젝트 리소스가 프로젝트를 잃으면 예외이다`() {
        assertFailsWith<IllegalStateException> {
            allowed(user(), ProjectResource("1", ResourceType.ISSUE_POST, null), Operation.READ)
        }
    }

    // --- 전역 리소스 ---

    @Test
    fun `프로젝트가 아닌 전역 리소스는 누구나 읽을 수 있다`() {
        val owner = user()
        assertTrue(allowed(null, GlobalResource(owner.id.toString(), ResourceType.USER), Operation.READ))
        assertTrue(allowed(user(), GlobalResource("1", ResourceType.ORGANIZATION), Operation.READ))
    }

    @Test
    fun `공개 프로젝트는 게스트가 아닌 사용자가 읽을 수 있다`() {
        val owner = user()
        val project = project(ProjectScope.PUBLIC, owner)
        val resource = GlobalResource(project.id.toString(), ResourceType.PROJECT)
        assertTrue(allowed(user(), resource, Operation.READ))
        assertTrue(allowed(null, resource, Operation.READ), "익명도 공개 프로젝트를 읽는다")
        assertFalse(allowed(user(guest = true), resource, Operation.READ), "게스트는 읽지 못한다")
    }

    @Test
    fun `비공개 프로젝트는 멤버와 조직 관리자만 읽는다`() {
        val owner = user()
        val org = organization()
        val project = project(ProjectScope.PRIVATE, owner, org)
        val resource = GlobalResource(project.id.toString(), ResourceType.PROJECT)
        val member = user().also { join(it, project, ProjectRole.MEMBER) }
        val orgAdmin = user().also { joinOrg(it, org, RoleType.ORG_ADMIN) }
        val orgMember = user().also { joinOrg(it, org, RoleType.ORG_MEMBER) }
        assertTrue(allowed(member, resource, Operation.READ))
        assertTrue(allowed(orgAdmin, resource, Operation.READ))
        assertFalse(allowed(orgMember, resource, Operation.READ), "비공개 프로젝트는 조직 구성원에게도 닫혀 있다")
        assertFalse(allowed(user(), resource, Operation.READ))
    }

    @Test
    fun `보호된 프로젝트는 같은 조직 구성원이 읽는다`() {
        val owner = user()
        val org = organization()
        val project = project(ProjectScope.PROTECTED, owner, org)
        val resource = GlobalResource(project.id.toString(), ResourceType.PROJECT)
        val orgMember = user().also { joinOrg(it, org, RoleType.ORG_MEMBER) }
        assertTrue(allowed(orgMember, resource, Operation.READ))
        assertFalse(allowed(user(), resource, Operation.READ))
    }

    @Test
    fun `없는 프로젝트는 읽을 수 없다`() {
        assertFalse(allowed(user(), GlobalResource("999999", ResourceType.PROJECT), Operation.READ))
    }

    @Test
    fun `프로젝트 구독은 공개 프로젝트의 로그인 사용자와 멤버에게 허용된다`() {
        val owner = user()
        val publicProject = project(ProjectScope.PUBLIC, owner)
        val privateProject = project(ProjectScope.PRIVATE, owner)
        val publicRes = GlobalResource(publicProject.id.toString(), ResourceType.PROJECT)
        val privateRes = GlobalResource(privateProject.id.toString(), ResourceType.PROJECT)
        assertTrue(allowed(user(), publicRes, Operation.WATCH))
        assertFalse(allowed(null, publicRes, Operation.WATCH), "익명은 구독하지 못한다")
        assertFalse(allowed(user(), privateRes, Operation.WATCH))
        val member = user().also { join(it, privateProject, ProjectRole.MEMBER) }
        assertTrue(allowed(member, privateRes, Operation.WATCH))
    }

    @Test
    fun `프로젝트 탈퇴는 소유자가 아닌 멤버만 할 수 있다`() {
        val owner = user()
        val project = project(ProjectScope.PUBLIC, owner)
        val resource = GlobalResource(project.id.toString(), ResourceType.PROJECT)
        join(owner, project, ProjectRole.MANAGER)
        val member = user().also { join(it, project, ProjectRole.MEMBER) }
        assertTrue(allowed(member, resource, Operation.LEAVE))
        assertFalse(allowed(owner, resource, Operation.LEAVE), "소유자는 떠날 수 없다")
        assertFalse(allowed(user(), resource, Operation.LEAVE), "멤버가 아니면 떠날 수 없다")
    }

    @Test
    fun `사용자와 아바타는 본인만 고치고 지운다`() {
        val me = user()
        val other = user()
        listOf(ResourceType.USER, ResourceType.USER_AVATAR).forEach { type ->
            val resource = GlobalResource(me.id.toString(), type)
            assertTrue(allowed(me, resource, Operation.UPDATE), "$type")
            assertTrue(allowed(me, resource, Operation.DELETE), "$type")
            assertFalse(allowed(other, resource, Operation.UPDATE), "$type")
        }
    }

    @Test
    fun `프로젝트는 매니저와 조직 관리자가 고치고 지운다`() {
        val owner = user()
        val org = organization()
        val project = project(ProjectScope.PUBLIC, owner, org)
        val resource = GlobalResource(project.id.toString(), ResourceType.PROJECT)
        val manager = user().also { join(it, project, ProjectRole.MANAGER) }
        val member = user().also { join(it, project, ProjectRole.MEMBER) }
        val orgAdmin = user().also { joinOrg(it, org, RoleType.ORG_ADMIN) }
        assertTrue(allowed(manager, resource, Operation.UPDATE))
        assertTrue(allowed(orgAdmin, resource, Operation.DELETE))
        assertFalse(allowed(member, resource, Operation.UPDATE), "일반 멤버는 프로젝트를 고치지 못한다")
    }

    @Test
    fun `조직은 조직 관리자만 고친다`() {
        val org = organization()
        val admin = user().also { joinOrg(it, org, RoleType.ORG_ADMIN) }
        val member = user().also { joinOrg(it, org, RoleType.ORG_MEMBER) }
        val resource = GlobalResource(org.id.toString(), ResourceType.ORGANIZATION)
        assertTrue(allowed(admin, resource, Operation.UPDATE))
        assertFalse(allowed(member, resource, Operation.UPDATE))
    }

    @Test
    fun `정의되지 않은 전역 리소스의 수정은 거부한다`() {
        assertFalse(allowed(user(), GlobalResource("1", ResourceType.LABEL), Operation.UPDATE))
    }

    @Test
    fun `이슈 담당자 지정은 멤버와 공개 또는 보호 프로젝트의 조직 구성원에게 허용된다`() {
        val owner = user()
        val org = organization()
        val publicProject = project(ProjectScope.PUBLIC, owner, org)
        val privateProject = project(ProjectScope.PRIVATE, owner, org)
        val orgMember = user().also { joinOrg(it, org, RoleType.ORG_MEMBER) }
        val member = user().also { join(it, privateProject, ProjectRole.MEMBER) }
        assertTrue(allowed(orgMember, GlobalResource(publicProject.id.toString(), ResourceType.PROJECT), Operation.ASSIGN_ISSUE))
        assertFalse(allowed(orgMember, GlobalResource(privateProject.id.toString(), ResourceType.PROJECT), Operation.ASSIGN_ISSUE))
        assertTrue(allowed(member, GlobalResource(privateProject.id.toString(), ResourceType.PROJECT), Operation.ASSIGN_ISSUE))
    }

    @Test
    fun `임시 첨부는 올린 사람만 쓸 수 있다`() {
        val uploader = user()
        val attachment = GlobalResource("7", ResourceType.ATTACHMENT, container = GlobalResource(uploader.id.toString(), ResourceType.USER))
        assertTrue(allowed(uploader, attachment, Operation.READ))
        assertFalse(allowed(user(), attachment, Operation.READ))
    }

    // --- 프로젝트 리소스 ---

    @Test
    fun `프로젝트 이전 요청은 받는 사용자만 수락한다`() {
        val owner = user()
        val target = user("target")
        val project = project(ProjectScope.PUBLIC, owner)
        val transfer = transfers.save(ProjectTransfer(sender = owner, destination = target.loginId, project = project))
        val resource = projectResource(project, ResourceType.PROJECT_TRANSFER, transfer.id.toString())
        assertTrue(allowed(target, resource, Operation.ACCEPT))
        assertFalse(allowed(user(), resource, Operation.ACCEPT))
        assertFalse(allowed(target, resource, Operation.READ), "수락 외의 연산은 거부한다")
    }

    @Test
    fun `받는 곳이 조직이면 그 조직 관리자가 수락한다`() {
        val owner = user()
        val org = organization()
        val admin = user().also { joinOrg(it, org, RoleType.ORG_ADMIN) }
        val project = project(ProjectScope.PUBLIC, owner)
        val transfer = transfers.save(ProjectTransfer(sender = owner, destination = org.name, project = project))
        val resource = projectResource(project, ResourceType.PROJECT_TRANSFER, transfer.id.toString())
        assertTrue(allowed(admin, resource, Operation.ACCEPT))
        assertFalse(allowed(user(), resource, Operation.ACCEPT))
    }

    @Test
    fun `프로젝트 조직의 관리자는 모든 프로젝트 리소스를 다룬다`() {
        val org = organization()
        val project = project(ProjectScope.PRIVATE, user(), org)
        val admin = user().also { joinOrg(it, org, RoleType.ORG_ADMIN) }
        Operation.entries.forEach { assertTrue(allowed(admin, projectResource(project, ResourceType.BOARD_POST), it), "$it") }
    }

    @Test
    fun `프로젝트 매니저도 코드는 삭제할 수 없다 (R-09)`() {
        // v1.6은 매니저 판정이 "코드는 삭제할 수 없다" 규칙보다 먼저 평가되어 매니저는 코드도 지울 수 있었다
        val project = project(ProjectScope.PUBLIC, user())
        val manager = user().also { join(it, project, ProjectRole.MANAGER) }
        val member = user().also { join(it, project, ProjectRole.MEMBER) }
        val code = projectResource(project, ResourceType.CODE)
        assertFalse(allowed(manager, code, Operation.DELETE), "매니저도 코드를 지우지 못한다")
        assertFalse(allowed(member, code, Operation.DELETE), "일반 멤버는 코드를 지우지 못한다")
    }

    @Test
    fun `작성자는 자신의 글과 댓글을 다룬다`() {
        val project = project(ProjectScope.PRIVATE, user())
        val author = user()
        val stranger = user()
        // 이슈 리소스는 담당자 검사를 위해 실제 이슈를 조회하므로 실제 이슈 번호를 쓴다(없으면 v1.6도 오류이다).
        val issueId = issues.save(Issue(project = project, number = 1, state = State.OPEN)).id.toString()
        listOf(
            ResourceType.ISSUE_POST, ResourceType.ISSUE_COMMENT, ResourceType.NONISSUE_COMMENT, ResourceType.BOARD_POST,
            ResourceType.COMMIT_COMMENT, ResourceType.COMMENT_THREAD, ResourceType.REVIEW_COMMENT
        ).forEach { type ->
            val resource = projectResource(project, type, if (type == ResourceType.ISSUE_POST) issueId else "1", authorId = author.id)
            assertTrue(allowed(author, resource, Operation.UPDATE), "$type")
            assertTrue(allowed(author, resource, Operation.DELETE), "$type")
            assertFalse(allowed(stranger, resource, Operation.UPDATE), "$type")
        }
        // 작성자 권한이 없는 타입
        assertFalse(allowed(author, projectResource(project, ResourceType.MILESTONE, authorId = author.id), Operation.UPDATE))
    }

    @Test
    fun `이슈 담당자는 이슈를 다룬다`() {
        val owner = user()
        val project = project(ProjectScope.PRIVATE, owner)
        val assigneeUser = user()
        val assignee = assignees.save(Assignee(user = assigneeUser, project = project))
        val issue = issues.save(Issue(project = project, number = 1, state = State.OPEN, assignee = assignee))
        val resource = projectResource(project, ResourceType.ISSUE_POST, issue.id.toString())
        assertTrue(allowed(assigneeUser, resource, Operation.UPDATE))
        assertFalse(allowed(user(), resource, Operation.UPDATE))
    }

    @Test
    fun `없는 이슈 번호의 리소스는 담당자 권한이 없고 500이 아니다 (C151, R-10)`() {
        val project = project(ProjectScope.PRIVATE, user())
        // v1.6은 없는 이슈에서 담당자를 읽다가 널 참조로 500이었다
        assertFalse(allowed(user(), projectResource(project, ResourceType.ISSUE_POST, "987654321"), Operation.UPDATE))
    }

    @Test
    fun `담당자가 없는 이슈는 담당자 권한이 없다`() {
        val project = project(ProjectScope.PRIVATE, user())
        val issue = issues.save(Issue(project = project, number = 1, state = State.OPEN))
        assertFalse(allowed(user(), projectResource(project, ResourceType.ISSUE_POST, issue.id.toString()), Operation.UPDATE))
    }

    @Test
    fun `컨테이너에 의존하는 리소스는 컨테이너의 권한을 따른다`() {
        val project = project(ProjectScope.PUBLIC, user())
        val member = user().also { join(it, project, ProjectRole.MEMBER) }
        val stranger = user()
        val issue = issues.save(Issue(project = project, number = 1, state = State.OPEN))
        val container = projectResource(project, ResourceType.ISSUE_POST, issue.id.toString())
        val attachment = projectResource(project, ResourceType.ATTACHMENT, "9", container = container)
        // 읽기: 컨테이너를 읽을 수 있으면 읽는다(공개 프로젝트)
        assertTrue(allowed(stranger, attachment, Operation.READ))
        // 수정과 삭제: 컨테이너를 수정할 수 있어야 한다(멤버)
        assertTrue(allowed(member, attachment, Operation.UPDATE))
        assertFalse(allowed(stranger, attachment, Operation.UPDATE))
        assertTrue(allowed(member, attachment, Operation.DELETE))
    }

    @Test
    fun `공유자는 이슈를 읽을 수 있다`() {
        val project = project(ProjectScope.PRIVATE, user())
        val sharer = user()
        val issue = issues.save(Issue(project = project, number = 1, state = State.OPEN))
        issueSharers.save(IssueSharer(user = sharer, issue = issue, loginId = sharer.loginId))
        val resource = projectResource(project, ResourceType.ISSUE_POST, issue.id.toString())
        assertTrue(allowed(sharer, resource, Operation.READ))
        assertFalse(allowed(sharer, resource, Operation.UPDATE))
        assertFalse(allowed(user(), resource, Operation.READ))
    }

    @Test
    fun `부모 이슈의 공유자도 자식 이슈를 읽을 수 있다`() {
        val project = project(ProjectScope.PRIVATE, user())
        val sharer = user()
        val parent = issues.save(Issue(project = project, number = 1, state = State.OPEN))
        issueSharers.save(IssueSharer(user = sharer, issue = parent, loginId = sharer.loginId))
        val child = issues.save(Issue(project = project, number = 2, state = State.OPEN, parent = parent))
        assertTrue(allowed(sharer, projectResource(project, ResourceType.ISSUE_POST, child.id.toString()), Operation.READ))
    }

    // --- 연산별 규칙 ---

    @Test
    fun `읽기 규칙 - 공개, 멤버, 조직 구성원`() {
        val owner = user()
        val org = organization()
        val publicProject = project(ProjectScope.PUBLIC, owner)
        val privateProject = project(ProjectScope.PRIVATE, owner)
        val protectedProject = project(ProjectScope.PROTECTED, owner, org)
        val orgMember = user().also { joinOrg(it, org, RoleType.ORG_MEMBER) }
        val member = user().also { join(it, privateProject, ProjectRole.MEMBER) }
        assertTrue(allowed(null, projectResource(publicProject, ResourceType.BOARD_POST), Operation.READ))
        assertFalse(allowed(user(guest = true), projectResource(publicProject, ResourceType.BOARD_POST), Operation.READ))
        assertFalse(allowed(user(), projectResource(privateProject, ResourceType.BOARD_POST), Operation.READ))
        assertTrue(allowed(member, projectResource(privateProject, ResourceType.BOARD_POST), Operation.READ))
        assertTrue(allowed(orgMember, projectResource(protectedProject, ResourceType.BOARD_POST), Operation.READ))
    }

    @Test
    fun `수정 승인 종료 재개 규칙 - 멤버와 조직 구성원만`() {
        val owner = user()
        val org = organization()
        val publicGroupProject = project(ProjectScope.PUBLIC, owner, org)
        val member = user().also { join(it, publicGroupProject, ProjectRole.MEMBER) }
        val orgMember = user().also { joinOrg(it, org, RoleType.ORG_MEMBER) }
        listOf(Operation.UPDATE, Operation.ACCEPT, Operation.CLOSE, Operation.REOPEN).forEach { op ->
            val resource = projectResource(publicGroupProject, ResourceType.PULL_REQUEST)
            assertTrue(allowed(member, resource, op), "$op 멤버")
            assertTrue(allowed(orgMember, resource, op), "$op 조직 구성원")
            assertFalse(allowed(user(), resource, op), "$op 비멤버")
        }
    }

    @Test
    fun `삭제 규칙 - 멤버만(이슈와 게시글과 댓글은 작성자와 관리자만), 코드는 불가`() {
        val project = project(ProjectScope.PUBLIC, user())
        val member = user().also { join(it, project, ProjectRole.MEMBER) }
        assertTrue(allowed(member, projectResource(project, ResourceType.MILESTONE), Operation.DELETE))
        assertFalse(allowed(user(), projectResource(project, ResourceType.MILESTONE), Operation.DELETE))
        assertFalse(allowed(member, projectResource(project, ResourceType.CODE), Operation.DELETE))
        // 이슈, 게시글, 댓글은 구성원이어도 남의 것을 지우지 못한다(X-36, D-48). 작성자와 관리자만 지운다
        assertFalse(allowed(member, projectResource(project, ResourceType.BOARD_POST), Operation.DELETE))
    }

    @Test
    fun `구독 규칙은 프로젝트 리소스에도 적용된다`() {
        val publicProject = project(ProjectScope.PUBLIC, user())
        assertTrue(allowed(user(), projectResource(publicProject, ResourceType.BOARD_POST), Operation.WATCH))
        assertFalse(allowed(null, projectResource(publicProject, ResourceType.BOARD_POST), Operation.WATCH))
    }

    @Test
    fun `정의되지 않은 연산은 거부한다`() {
        val project = project(ProjectScope.PUBLIC, user())
        val member = user().also { join(it, project, ProjectRole.MEMBER) }
        assertFalse(allowed(member, projectResource(project, ResourceType.BOARD_POST), Operation.LEAVE))
        assertFalse(allowed(member, projectResource(project, ResourceType.BOARD_POST), Operation.ASSIGN_ISSUE))
    }

    // --- 생성 권한 ---

    @Test
    fun `전역 리소스는 로그인한 사용자가 만들 수 있다`() {
        assertTrue(accessControl.isGlobalResourceCreatable(user()))
        assertFalse(accessControl.isGlobalResourceCreatable(null))
    }

    @Test
    fun `익명은 프로젝트 리소스를 만들 수 없다`() {
        val project = project(ProjectScope.PUBLIC, user())
        assertFalse(accessControl.isProjectResourceCreatable(null, project, ResourceType.ISSUE_POST))
    }

    @Test
    fun `멤버와 조직 관리자와 조직 구성원은 무엇이든 만든다`() {
        val owner = user()
        val org = organization()
        val project = project(ProjectScope.PROTECTED, owner, org)
        val member = user().also { join(it, project, ProjectRole.MEMBER) }
        val orgAdmin = user().also { joinOrg(it, org, RoleType.ORG_ADMIN) }
        val orgMember = user().also { joinOrg(it, org, RoleType.ORG_MEMBER) }
        listOf(member, orgAdmin, orgMember).forEach {
            assertTrue(accessControl.isProjectResourceCreatable(it, project, ResourceType.MILESTONE))
        }
    }

    @Test
    fun `비공개 프로젝트의 비멤버는 만들 수 없다`() {
        val project = project(ProjectScope.PRIVATE, user())
        assertFalse(accessControl.isProjectResourceCreatable(user(), project, ResourceType.ISSUE_POST))
    }

    @Test
    fun `공개 프로젝트의 로그인 사용자는 정해진 종류만 만들 수 있다`() {
        val project = project(ProjectScope.PUBLIC, user())
        val visitor = user()
        listOf(
            ResourceType.ISSUE_POST, ResourceType.BOARD_POST, ResourceType.ISSUE_COMMENT, ResourceType.NONISSUE_COMMENT,
            ResourceType.FORK, ResourceType.COMMIT_COMMENT, ResourceType.REVIEW_COMMENT
        ).forEach { assertTrue(accessControl.isProjectResourceCreatable(visitor, project, it), "$it") }
        listOf(ResourceType.MILESTONE, ResourceType.ISSUE_LABEL, ResourceType.WEBHOOK, ResourceType.CODE).forEach {
            assertFalse(accessControl.isProjectResourceCreatable(visitor, project, it), "$it")
        }
    }

    @Test
    fun `컨테이너의 작성자는 그 아래에 리소스를 만들 수 있다`() {
        val project = project(ProjectScope.PRIVATE, user())
        val author = user()
        val issue = issues.save(Issue(project = project, number = 1, state = State.OPEN))
        val container = projectResource(project, ResourceType.ISSUE_POST, issue.id.toString(), authorId = author.id)
        assertTrue(accessControl.isResourceCreatable(author, container, ResourceType.ISSUE_COMMENT))
        assertFalse(accessControl.isResourceCreatable(user(), container, ResourceType.ISSUE_COMMENT))
    }

    @Test
    fun `프로젝트 컨테이너 아래의 생성은 그 프로젝트 기준으로 판단한다`() {
        val project = project(ProjectScope.PUBLIC, user())
        val container = GlobalResource(project.id.toString(), ResourceType.PROJECT)
        assertTrue(accessControl.isResourceCreatable(user(), container, ResourceType.ISSUE_POST))
        assertFalse(accessControl.isResourceCreatable(user(), container, ResourceType.MILESTONE))
    }

    /**
     * v1.6 결함 재현: 프로젝트가 아닌 전역 리소스(사용자 등)를 컨테이너로 쓰면 `container.getProject()`가
     * `UnsupportedOperationException`을 던진다([refactoring-backlog.md] R-08).
     */
    @Test
    fun `프로젝트가 아닌 전역 컨테이너 아래의 생성은 예외 없이 전역 규칙으로 판단한다 (R-08, v1_6은 예외로 실패했다)`() {
        val container = GlobalResource("1", ResourceType.USER)
        val user = user()
        assertEquals(accessControl.isGlobalResourceCreatable(user), accessControl.isResourceCreatable(user, container, ResourceType.ATTACHMENT))
    }

    // --- 이슈, 게시글, 댓글의 삭제는 작성자와 관리자만, 수정은 GitHub처럼 구성원에게 허용 (X-36, D-48) ---

    private fun contentFixture(): Triple<Project, Resource, Map<String, User>> {
        val owner = user(); val project = project(ProjectScope.PRIVATE, owner)
        val author = user("author"); val member = user("member"); val manager = user("manager"); val assigneeUser = user("assignee"); val outsider = user("outsider")
        join(author, project, RoleType.MEMBER); join(member, project, RoleType.MEMBER); join(manager, project, RoleType.MANAGER); join(assigneeUser, project, RoleType.MEMBER)
        val assignee = assignees.save(Assignee(user = assigneeUser, project = project))
        val issue = issues.save(Issue(project = project, number = 1, state = State.OPEN, assignee = assignee))
        val resource = projectResource(project, ResourceType.ISSUE_POST, issue.id.toString(), authorId = author.id)
        return Triple(project, resource, mapOf("author" to author, "member" to member, "manager" to manager, "assignee" to assigneeUser, "outsider" to outsider))
    }

    @Test
    fun `이슈 삭제는 작성자와 관리자만 하고 다른 구성원과 담당자는 하지 못한다`() {
        val (project, issue, who) = contentFixture()
        assertTrue(allowed(who.getValue("author"), issue, Operation.DELETE)); assertTrue(allowed(who.getValue("manager"), issue, Operation.DELETE))
        assertFalse(allowed(who.getValue("member"), issue, Operation.DELETE), "남의 이슈를 지우지 못한다"); assertFalse(allowed(who.getValue("assignee"), issue, Operation.DELETE), "담당자도 지우지 못한다")
        assertFalse(allowed(who.getValue("outsider"), issue, Operation.DELETE))
        val admin = user("siteadmin"); siteAdmins.save(SiteAdmin(admin = admin)); assertTrue(allowed(admin, issue, Operation.DELETE))
        assertEquals(project, project)
    }

    @Test
    fun `이슈의 속성 변경은 구성원과 담당자에게 계속 허용한다`() {
        val (_, issue, who) = contentFixture()
        listOf("author", "member", "manager", "assignee").forEach { assertTrue(allowed(who.getValue(it), issue, Operation.UPDATE), it) }
        assertFalse(allowed(who.getValue("outsider"), issue, Operation.UPDATE))
    }

    @Test
    fun `이슈 본문과 댓글의 수정은 GitHub처럼 구성원에게 계속 허용한다`() {
        val (project, issue, who) = contentFixture()
        listOf("author", "member", "manager", "assignee").forEach { assertTrue(allowed(who.getValue(it), issue, Operation.UPDATE), it) }
        val comment = projectResource(project, ResourceType.ISSUE_COMMENT, "1", authorId = who.getValue("author").id)
        listOf("author", "member", "manager").forEach { assertTrue(allowed(who.getValue(it), comment, Operation.UPDATE), "댓글 수정 $it") }
        assertFalse(allowed(who.getValue("outsider"), comment, Operation.UPDATE))
    }

    @Test
    fun `댓글과 게시글과 게시판 댓글도 같은 규칙이다`() {
        val owner = user(); val project = project(ProjectScope.PRIVATE, owner); val author = user("a"); val member = user("m"); val manager = user("g")
        join(author, project, RoleType.MEMBER); join(member, project, RoleType.MEMBER); join(manager, project, RoleType.MANAGER)
        listOf(ResourceType.ISSUE_COMMENT, ResourceType.NONISSUE_COMMENT).forEach { type ->
            val comment = projectResource(project, type, "1", authorId = author.id)
            assertTrue(allowed(author, comment, Operation.UPDATE), "$type"); assertTrue(allowed(author, comment, Operation.DELETE), "$type")
            assertTrue(allowed(manager, comment, Operation.UPDATE), "$type"); assertTrue(allowed(manager, comment, Operation.DELETE), "$type")
            assertTrue(allowed(member, comment, Operation.UPDATE), "$type 남의 댓글 수정은 구성원도 한다"); assertFalse(allowed(member, comment, Operation.DELETE), "$type 남의 댓글 삭제")
        }
        val post = projectResource(project, ResourceType.BOARD_POST, "1", authorId = author.id)
        assertTrue(allowed(member, post, Operation.UPDATE), "게시글 수정은 구성원도 한다"); assertFalse(allowed(member, post, Operation.DELETE)); assertTrue(allowed(author, post, Operation.DELETE)); assertTrue(allowed(manager, post, Operation.DELETE))
    }

    @Test
    fun `코드 리뷰 댓글과 풀 리퀘스트 댓글과 커밋 댓글도 삭제는 작성자와 관리자만 하고 수정은 구성원에게 허용한다 (D-48 확장)`() {
        val owner = user(); val project = project(ProjectScope.PRIVATE, owner); val author = user("a"); val member = user("m"); val manager = user("g")
        join(author, project, RoleType.MEMBER); join(member, project, RoleType.MEMBER); join(manager, project, RoleType.MANAGER)
        listOf(ResourceType.COMMIT_COMMENT, ResourceType.REVIEW_COMMENT, ResourceType.COMMENT_THREAD).forEach {
            val resource = projectResource(project, it, "1", authorId = author.id)
            assertTrue(allowed(author, resource, Operation.DELETE), "$it 작성자 삭제"); assertTrue(allowed(manager, resource, Operation.DELETE), "$it 관리자 삭제")
            assertFalse(allowed(member, resource, Operation.DELETE), "$it 남의 것 삭제")
            assertTrue(allowed(member, resource, Operation.UPDATE), "$it 수정(스레드 열고 닫기 포함)은 구성원에게 열려 있다")
        }
    }
}

/** 프로젝트 역할 이름을 테스트에서 짧게 쓰기 위한 별칭이다. */
private object ProjectRole {
    val MANAGER = RoleType.MANAGER
    val MEMBER = RoleType.MEMBER
}

/** 익명을 허용하지 않는 사이트에서는 익명 사용자의 모든 요청이 거부된다. */
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.allows-anonymous-access=false"])
class AccessControlClosedSiteTest : AbstractIntegrationTest() {

    @Autowired private lateinit var accessControl: AccessControl
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository

    @Test
    fun `익명을 막는 사이트는 익명의 읽기도 거부한다`() {
        val owner = users.save(User(loginId = "closed1", email = "closed1@example.com", state = UserState.ACTIVE))
        val project = projects.save(Project(name = "closedp", owner = owner.loginId, projectScope = ProjectScope.PUBLIC))
        assertFalse(accessControl.isAllowed(null, GlobalResource(project.id.toString(), ResourceType.PROJECT), Operation.READ))
        assertFalse(accessControl.isResourceCreatable(null, GlobalResource("1", ResourceType.PROJECT), ResourceType.ATTACHMENT))
    }
}
