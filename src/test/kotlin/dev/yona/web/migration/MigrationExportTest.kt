package dev.yona.web.migration

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * 이전(Migration) 화면의 JSON 주소(`/migration/projects`, `/migration/:owner/projects/:name` 아래)를 검증한다.
 * 접근은 D-23이다: 출발 프로젝트 목록과 같은 기준(그 프로젝트의 매니저, 소속 조직의 관리자, 사이트 매니저)만 내보낼 수 있다.
 *
 * 원본: `app/controllers/MigrationApp.java:93-450`.
 */
@AutoConfigureMockMvc
@Transactional
class MigrationExportTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var postingComments: PostingCommentRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labelCategories: IssueLabelCategoryRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private var number = 0L
    private var clock = 1_700_000_000_000L
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    private fun newUser(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mg$n", name = name ?: "mg$n", email = "mg$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(owner: User, name: String, scope: ProjectScope = ProjectScope.PRIVATE, org: Organization? = null) =
        projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = scope, organization = org, createdDate = Date()))

    private fun join(user: User, project: Project, role: RoleType) = projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))

    private fun organization(name: String, admin: User? = null, member: User? = null): Organization {
        val org = organizations.save(Organization(name = name, descr = "d", created = Date()))
        admin?.let { organizationUsers.save(OrganizationUser(user = it, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get())) }
        member?.let { organizationUsers.save(OrganizationUser(user = it, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get())) }
        return org
    }

    private fun issue(project: Project, title: String, author: User, body: String = "본문", state: State = State.OPEN, assignee: User? = null, milestone: Milestone? = null) = issues.save(Issue(
        project = project, number = ++number, title = title, body = body, state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
        assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, milestone = milestone, numOfComments = 0, weight = 0,
        createdDate = Date(++clock), updatedDate = Date(clock)
    ))

    private fun at(millis: Long) = LocalDateTime.ofInstant(Date(millis).toInstant(), ZoneId.systemDefault()).format(formatter)

    private fun exportOf(session: MockHttpSession?, path: String, vararg params: Pair<String, String>): ResultActions =
        mockMvc.perform(get(path).apply { session?.let { session(it) }; params.forEach { (k, v) -> param(k, v) } })

    private val endpoints = listOf("", "/labels", "/issuelabel", "/milestones", "/issues", "/posts")

    // --- 접근 ---

    @Test
    fun `로그인하지 않으면 로그인 폼으로 보낸다`() {
        val owner = newUser(); val p = project(owner, "acc")
        exportOf(null, "/migration/projects").andExpect(status().isSeeOther).andExpect(header().string("Location", "/users/loginform?redirectUrl=/migration/projects"))
        endpoints.forEach { exportOf(null, "/migration/${owner.loginId}/projects/acc$it").andExpect(status().isSeeOther) }
    }

    @Test
    fun `프로젝트 매니저와 조직 관리자와 사이트 매니저는 내보낼 수 있다`() {
        val owner = newUser(); val manager = newUser(); val orgAdmin = newUser(); val siteManager = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }
        val org = organization("acme-mig", admin = orgAdmin); val p = project(owner, "granted", org = org)
        join(manager, p, RoleType.MANAGER)
        listOf(manager, orgAdmin, siteManager).forEach { user ->
            val session = login(user)
            endpoints.forEach { exportOf(session, "/migration/${owner.loginId}/projects/granted$it").andExpect(status().isOk) }
        }
    }

    @Test
    fun `구성원이나 조직 구성원이나 소유자만으로는 내보낼 수 없다`() {
        val owner = newUser(); val member = newUser(); val orgMember = newUser(); val stranger = newUser()
        val org = organization("acme-mig2", member = orgMember); val p = project(owner, "denied", org = org)
        join(member, p, RoleType.MEMBER)
        listOf(member, orgMember, stranger, owner).forEach { user ->
            val session = login(user)
            endpoints.forEach { exportOf(session, "/migration/${owner.loginId}/projects/denied$it").andExpect(status().isForbidden) }
        }
    }

    @Test
    fun `없는 프로젝트는 404이다`() {
        val session = login(newUser())
        endpoints.forEach { exportOf(session, "/migration/nobody/projects/nothing$it").andExpect(status().isNotFound) }
    }

    // --- 출발 프로젝트 목록 ---

    @Test
    fun `목록은 내가 관리자인 조직의 모든 프로젝트와 내가 매니저인 프로젝트를 이름 순으로 담는다`() {
        val me = newUser(); val other = newUser(); val member = newUser()
        val org = organization("zorg", admin = me)
        val orgPrivate = project(other, "org-private", ProjectScope.PRIVATE, org); val orgPublic = project(other, "a-org-public", ProjectScope.PUBLIC, org)
        val managed = project(other, "managed", ProjectScope.PRIVATE); join(me, managed, RoleType.MANAGER); join(member, managed, RoleType.MEMBER)
        val justMember = project(other, "just-member", ProjectScope.PRIVATE); join(me, justMember, RoleType.MEMBER)
        project(other, "unrelated", ProjectScope.PUBLIC)
        val body = exportOf(login(me), "/migration/projects").andExpect(status().isOk).andReturn().response.contentAsString
        val names = Regex("\"full_name\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(listOf("${other.loginId}/a-org-public", "${other.loginId}/managed", "${other.loginId}/org-private"), names)
        exportOf(login(me), "/migration/projects").andExpect(jsonPath("$[1].owner").value(other.loginId!!)).andExpect(jsonPath("$[1].projectName").value("managed"))
            .andExpect(jsonPath("$[1].private").value(true)).andExpect(jsonPath("$[1].members").value(2)).andExpect(jsonPath("$[0].private").value(false))
    }

    @Test
    fun `사이트 매니저는 자신이 구성원인 프로젝트만 목록에 담긴다`() {
        val siteManager = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }; val other = newUser()
        val mine = project(other, "sm-mine", ProjectScope.PUBLIC); join(siteManager, mine, RoleType.MEMBER)
        project(other, "sm-other", ProjectScope.PUBLIC)
        exportOf(login(siteManager), "/migration/projects").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].projectName").value("sm-mine"))
    }

    // --- 프로젝트 요약 ---

    @Test
    fun `프로젝트 요약은 담당자와 개수를 담는다`() {
        val manager = newUser("관리 담당"); val assignee = newUser("담당자 이름"); val p = project(manager, "summary"); join(manager, p, RoleType.MANAGER)
        issue(p, "이슈1", manager, assignee = assignee); issue(p, "이슈2", manager)
        postings.save(Posting(project = p, number = ++number, title = "글", body = "b", authorId = manager.id, authorLoginId = manager.loginId, authorName = manager.name, createdDate = Date(), updatedDate = Date()))
        milestones.save(Milestone(project = p, title = "m1", contents = "c", state = State.OPEN))
        exportOf(login(manager), "/migration/${manager.loginId}/projects/summary").andExpect(status().isOk)
            .andExpect(jsonPath("$.owner").value(manager.loginId!!)).andExpect(jsonPath("$.projectName").value("summary")).andExpect(jsonPath("$.full_name").value("${manager.loginId}/summary"))
            .andExpect(jsonPath("$.memberCount").value(1)).andExpect(jsonPath("$.issueCount").value(2)).andExpect(jsonPath("$.postCount").value(1)).andExpect(jsonPath("$.milestoneCount").value(1))
            .andExpect(jsonPath("$.assignees.length()").value(1)).andExpect(jsonPath("$.assignees[0].name").value("담당자 이름"))
            .andExpect(jsonPath("$.assignees[0].login").value(assignee.loginId!!)).andExpect(jsonPath("$.assignees[0].email").value(assignee.email!!))
    }

    // --- 라벨 ---

    @Test
    fun `라벨은 번호를 키로 이름과 분류를 담고 라벨과 이슈의 짝은 따로 낸다`() {
        val manager = newUser(); val p = project(manager, "lab"); join(manager, p, RoleType.MANAGER)
        val category = labelCategories.save(IssueLabelCategory(project = p, name = "종류"))
        val label = labels.save(IssueLabel(project = p, category = category, name = "버그", color = "#fff"))
        val one = issue(p, "라벨 이슈", manager)
        entityManager.createNativeQuery("insert into issue_issue_label(issue_id, issue_label_id) values (:i, :l)").setParameter("i", one.id).setParameter("l", label.id).executeUpdate()
        val session = login(manager)
        exportOf(session, "/migration/${manager.loginId}/projects/lab/labels").andExpect(status().isOk)
            .andExpect(jsonPath("$.labels['${label.id}'].id").value(label.id!!.toInt())).andExpect(jsonPath("$.labels['${label.id}'].name").value("버그"))
            .andExpect(jsonPath("$.labels['${label.id}'].categoryId").value(category.id!!.toInt())).andExpect(jsonPath("$.labels['${label.id}'].categoryName").value("종류"))
        exportOf(session, "/migration/${manager.loginId}/projects/lab/issuelabel").andExpect(status().isOk)
            .andExpect(jsonPath("$.issueLabelPairs.length()").value(1)).andExpect(jsonPath("$.issueLabelPairs[0].issueId").value(one.id!!.toInt()))
            .andExpect(jsonPath("$.issueLabelPairs[0].issueLabelId").value(label.id!!.toInt()))
    }

    // --- 마일스톤 ---

    @Test
    fun `마일스톤은 상태와 설명과 마감을 담는다`() {
        val manager = newUser(); val p = project(manager, "mile"); join(manager, p, RoleType.MANAGER)
        val due = milestones.save(Milestone(project = p, title = "마감 있음", contents = "설명", state = State.CLOSED, dueDate = Date(1_700_100_000_000L)))
        val plain = milestones.save(Milestone(project = p, title = "마감 없음", contents = null, state = State.OPEN))
        val session = login(manager)
        exportOf(session, "/migration/${manager.loginId}/projects/mile/milestones").andExpect(status().isOk)
            .andExpect(jsonPath("$.milestones[0].milestone.id").value(due.id!!.toInt())).andExpect(jsonPath("$.milestones[0].milestone.title").value("마감 있음"))
            .andExpect(jsonPath("$.milestones[0].milestone.state").value("closed")).andExpect(jsonPath("$.milestones[0].milestone.description").value("설명"))
            .andExpect(jsonPath("$.milestones[0].milestone.due_on").value(at(1_700_100_000_000L)))
            .andExpect(jsonPath("$.milestones[1].milestone.state").value("open")).andExpect(jsonPath("$.milestones[1].milestone.due_on").doesNotExist())
        org.junit.jupiter.api.Assertions.assertNotNull(plain.id)
    }

    // --- 이슈 ---

    @Test
    fun `이슈는 본문 앞에 원 작성자 안내가 붙고 댓글과 담당자와 마일스톤을 담는다`() {
        val manager = newUser("작성자"); val assignee = newUser(); val p = project(manager, "iss"); join(manager, p, RoleType.MANAGER)
        val milestone = milestones.save(Milestone(project = p, title = "v1", contents = "c", state = State.OPEN))
        val one = issue(p, "이슈 제목", manager, body = "이슈 본문", assignee = assignee, milestone = milestone)
        val closed = issue(p, "닫힌 이슈", manager, state = State.CLOSED)
        val late = comments.save(IssueComment(contents = "나중 댓글", authorId = manager.id, authorLoginId = manager.loginId, authorName = "작성자", issue = one, projectId = p.id!!, createdDate = Date(1_700_000_900_000L)))
        val early = comments.save(IssueComment(contents = "먼저 댓글", authorId = manager.id, authorLoginId = manager.loginId, authorName = "작성자", issue = one, projectId = p.id!!, createdDate = Date(1_700_000_100_000L)))
        exportOf(login(manager), "/migration/${manager.loginId}/projects/iss/issues").andExpect(status().isOk)
            .andExpect(jsonPath("$.issues.length()").value(2))
            .andExpect(jsonPath("$.issues[0].issue.id").value(one.id!!.toInt())).andExpect(jsonPath("$.issues[0].issue.title").value("이슈 제목"))
            .andExpect(jsonPath("$.issues[0].issue.body").value("@${manager.loginId} (작성자) 님이 작성한 [이슈](/${manager.loginId}/iss/issue/${one.number})입니다. \n\\---\n\n이슈 본문"))
            .andExpect(jsonPath("$.issues[0].issue.created_at").value(at(one.createdDate!!.time))).andExpect(jsonPath("$.issues[0].issue.assignee").value(assignee.loginId!!))
            .andExpect(jsonPath("$.issues[0].issue.milestone").value("v1")).andExpect(jsonPath("$.issues[0].issue.milestoneId").value(milestone.id!!.toInt()))
            .andExpect(jsonPath("$.issues[0].issue.closed").value(false)).andExpect(jsonPath("$.issues[1].issue.closed").value(true))
            .andExpect(jsonPath("$.issues[1].issue.assignee").doesNotExist()).andExpect(jsonPath("$.issues[1].issue.milestone").doesNotExist())
            .andExpect(jsonPath("$.issues[0].comments.length()").value(2))
            .andExpect(jsonPath("$.issues[0].comments[0].body").value("@${manager.loginId} (작성자) 님이 작성한 [코멘트](/${manager.loginId}/iss/issue/${one.number}#comment-${early.id})입니다. \n\\---\n\n먼저 댓글"))
            .andExpect(jsonPath("$.issues[0].comments[0].created_at").value(at(1_700_000_100_000L))).andExpect(jsonPath("$.issues[0].comments[1].body").value(org.hamcrest.Matchers.endsWith("나중 댓글")))
        org.junit.jupiter.api.Assertions.assertNotNull(late.id); org.junit.jupiter.api.Assertions.assertNotNull(closed.id)
    }

    @Test
    fun `첨부는 본문 뒤에 목록으로 붙는다`() {
        val manager = newUser(); val p = project(manager, "att"); join(manager, p, RoleType.MANAGER)
        val one = issue(p, "첨부 이슈", manager, body = "본문")
        val file = attachments.save(Attachment(name = "a#b.txt", hash = "att-h", containerType = ResourceType.ISSUE_POST, containerId = one.id!!, mimeType = "text/plain", size = 3, createdDate = Date(), ownerLoginId = manager.loginId))
        val session = login(manager)
        exportOf(session, "/migration/${manager.loginId}/projects/att/issues").andExpect(jsonPath("$.issues[0].issue.body").value(org.hamcrest.Matchers.endsWith("본문\n\n--- attachments ---\n[a#b.txt](//files/${file.id})")))
        exportOf(session, "/migration/${manager.loginId}/projects/att/issues", "withWikiCommit" to "true")
            .andExpect(jsonPath("$.issues[0].issue.body").value(org.hamcrest.Matchers.endsWith("본문\n\n--- attachments ---\n[a#b.txt](../wiki/files/${file.id}/a%23b.txt)")))
    }

    @Test
    fun `위키 커밋 방식에서는 본문의 상대 링크가 위키 경로로 바뀐다`() {
        val manager = newUser(); val p = project(manager, "wiki"); join(manager, p, RoleType.MANAGER)
        issue(p, "링크 이슈", manager, body = "참고 [문서](/o/p/post/1) 그림 <img src='/files/9'>")
        val session = login(manager)
        exportOf(session, "/migration/${manager.loginId}/projects/wiki/issues").andExpect(jsonPath("$.issues[0].issue.body").value(org.hamcrest.Matchers.endsWith("참고 [문서](/o/p/post/1) 그림 <img src='/files/9'>")))
        exportOf(session, "/migration/${manager.loginId}/projects/wiki/issues", "withWikiCommit" to "true")
            .andExpect(jsonPath("$.issues[0].issue.body").value(org.hamcrest.Matchers.endsWith("참고 [문서](../wiki/o/p/post/1/문서) 그림 <img src='/files/9'>")))
    }

    // --- 게시글 ---

    @Test
    fun `게시글은 이슈 모양의 issue 키에 제목과 본문과 시각을 담고 댓글을 붙인다`() {
        val manager = newUser("글쓴이"); val p = project(manager, "post"); join(manager, p, RoleType.MANAGER)
        val post = postings.save(Posting(project = p, number = ++number, title = "게시 제목", body = "게시 본문", authorId = manager.id, authorLoginId = manager.loginId, authorName = "글쓴이", createdDate = Date(1_700_000_500_000L), updatedDate = Date()))
        val c = postingComments.save(PostingComment(contents = "게시 댓글", createdDate = Date(1_700_000_600_000L), authorId = manager.id, authorLoginId = manager.loginId, authorName = "글쓴이", posting = post, projectId = p.id!!))
        exportOf(login(manager), "/migration/${manager.loginId}/projects/post/posts").andExpect(status().isOk)
            .andExpect(jsonPath("$.issues[0].issue.title").value("게시 제목"))
            .andExpect(jsonPath("$.issues[0].issue.body").value("@${manager.loginId} (글쓴이) 님이 작성한 [게시글](/${manager.loginId}/post/post/${post.number})입니다. \n\\---\n\n게시 본문"))
            .andExpect(jsonPath("$.issues[0].issue.created_at").value(at(1_700_000_500_000L))).andExpect(jsonPath("$.issues[0].issue.id").doesNotExist())
            .andExpect(jsonPath("$.issues[0].comments[0].body").value("@${manager.loginId} (글쓴이) 님이 작성한 [코멘트](/${manager.loginId}/post/post/${post.number}#comment-${c.id})입니다. \n\\---\n\n게시 댓글"))
    }

    @Test
    fun `화면 설정이 꺼져 있으면 화면은 403이지만 JSON 주소는 열려 있다`() {
        val manager = newUser(); val p = project(manager, "flag"); join(manager, p, RoleType.MANAGER)
        val session = login(manager)
        exportOf(session, "/migration").andExpect(status().isForbidden)
        exportOf(session, "/migration/projects").andExpect(status().isOk)
    }
}
