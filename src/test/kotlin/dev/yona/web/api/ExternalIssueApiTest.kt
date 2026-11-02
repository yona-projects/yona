package dev.yona.web.api

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.board.PostingComment
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.issue.IssueCreatedActivity
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.text.SimpleDateFormat
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.RecentProject
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.FavoriteOrganizationRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 외부용 이슈 API를 검증한다: 조회, 등록, 수정, 상태 바꾸기, 댓글, 라벨, 글을 이슈로 가져오기.
 *
 * 원본: `app/controllers/api/IssueApi.java`(`getIssue`, `newIssues`, `updateIssue`, `updateIssueState`, `newIssueComment`, `updateIssueLabel`, `imports`).
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class ExternalIssueApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var titleHeads: dev.yona.domain.project.TitleHeadRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var events: ApplicationEvents
    @Autowired private lateinit var notificationEvents: dev.yona.domain.notification.NotificationEventRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var messages: org.springframework.context.MessageSource

    private var sequence = 0

    private fun newUser(token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "ex$n", name = "ex$n", email = "ex$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), token = token))
        token?.let { dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, it) }
        return saved
    }

    private fun member(token: String): Pair<User, Project> {
        val user = newUser("tk$token")
        val project = newProject()
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return user to project
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "exo$n", name = "exp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun issue(project: Project, author: User, number: Long = 1): Issue {
        project.lastIssueNumber = maxOf(project.lastIssueNumber ?: 0, number); projects.save(project)
        return issues.save(Issue(project = project, number = number, title = "제목", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)))
    }

    private fun api(project: Project, path: String) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", java.util.Locale.ENGLISH)

    // --- 조회 ---

    @Test
    fun `시각을 생략한 이슈는 만든 시각을 지금으로 채우고 고친 시각은 만든 시각과 같게 한다 (v1_6 결함 수정, D-83)`() {
        val (user, project) = member("nulldate-tk")
        val before = System.currentTimeMillis() - 2000
        json(post(api(project, "issues")), user.token, """{"issues":[{"title":"시각 없음","body":"내용","number":3}]}""").andExpect(status().isCreated)
        entityManager.flush(); entityManager.clear()
        val saved = issues.findAll().single { it.project?.id == project.id }
        // v1.6 IssueApi는 createdAt, updatedAt을 생략하면 생성자의 기본값을 null로 덮어써 기간 조건과 목록 정렬에서 빠졌다. v2는 지금 시각으로 채운다
        assertNotNull(saved.createdDate); assertTrue(saved.createdDate!!.time >= before && saved.createdDate!!.time <= System.currentTimeMillis() + 2000)
        assertEquals(saved.createdDate, saved.updatedDate)
        mockMvc.perform(get("/${project.owner}/${project.name}/issue/3")).andExpect(status().isOk)
        mockMvc.perform(get("/${project.owner}/${project.name}/issues")).andExpect(status().isOk)
        json(get(api(project, "issues/3")), user.token!!, null).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.createdAt").value(org.hamcrest.Matchers.not(""))).andExpect(jsonPath("$.result.updatedAt").value(org.hamcrest.Matchers.not("")))
    }

    @Test
    fun `읽을 수 없는 시각도 지금으로 채우고 ISO-8601 시각은 받는다 (D-83)`() {
        val (user, project) = member("isodate-tk")
        json(post(api(project, "issues")), user.token, """{"issues":[{"title":"깨진 시각","body":"a","number":4,"createdAt":"어제 오후"},{"title":"ISO 시각","body":"b","number":5,"createdAt":"2026-02-01T09:00:00Z","updatedAt":"2026-02-03T00:00:00+09:00"}]}""").andExpect(status().isCreated)
        entityManager.flush(); entityManager.clear()
        val broken = issues.findAll().single { it.project?.id == project.id && it.number == 4L }; val iso = issues.findAll().single { it.project?.id == project.id && it.number == 5L }
        assertNotNull(broken.createdDate); assertNotNull(broken.updatedDate)
        assertEquals(java.time.Instant.parse("2026-02-01T09:00:00Z").toEpochMilli(), iso.createdDate!!.time)
        assertEquals(java.time.Instant.parse("2026-02-02T15:00:00Z").toEpochMilli(), iso.updatedDate!!.time)
    }

    @Test
    fun `토큰으로 이슈와 이벤트를 조회한다`() {
        val (user, project) = member("a")
        val i = issue(project, user)
        entityManager.persist(IssueEvent(created = Date(1_700_000_001_000L), senderLoginId = user.loginId, issue = i, eventType = EventType.ISSUE_STATE_CHANGED, oldValue = "open", newValue = "closed"))
        entityManager.flush()
        json(get(api(project, "issues/1")), user.token!!.removePrefix(""), null).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.number").value(1)).andExpect(jsonPath("$.result.title").value("제목")).andExpect(jsonPath("$.result.type").value("ISSUE_POST"))
            .andExpect(jsonPath("$.result.events[0].eventType").value("ISSUE_STATE_CHANGED")).andExpect(jsonPath("$.result.events[0].eventDescription").value(messages.getMessage("notification.type.issue.state.changed", null, java.util.Locale.ENGLISH)))
            .andExpect(jsonPath("$.result.events[0].oldValue").value("open")).andExpect(jsonPath("$.result.events[0].actor.loginId").value(user.loginId)).andExpect(jsonPath("$.result.events[0].actor.name").value(user.name))
            .andExpect(jsonPath("$.result.events[0].createdDate").isString).andExpect(jsonPath("$.result.events[0].id").isNumber)
    }

    @Test
    fun `조회의 오류`() {
        val (user, project) = member("b")
        json(get(api(project, "issues/1")), null, null).andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("unauthorized request"))
        json(get("/-_-api/v1/owners/nobody/projects/none/issues/1"), user.token, null).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("no project by request"))
        json(get(api(project, "issues/9")), user.token, null).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("no issue by request"))
    }

    @Test
    fun `구성원이 아닌 토큰은 비공개 프로젝트의 이슈를 읽지 못한다(v1_6과 다르다, D-36 4번)`() {
        val (user, _) = member("pv")
        val secret = newProject(ProjectScope.PRIVATE); issue(secret, newUser())
        json(get(api(secret, "issues/1")), user.token, null).andExpect(status().isForbidden)
    }

    // --- 등록 ---

    @Test
    fun `한글 프로젝트 이름의 응답 주소는 퍼센트 인코딩한다 (v1_6 역방향 라우트)`() {
        val (user, project) = member("kr")
        project.name = "한글프로젝트"; projects.save(project)
        val encodedName = java.net.URLEncoder.encode("한글프로젝트", Charsets.UTF_8)
        json(post(java.net.URI.create("/-_-api/v1/owners/${project.owner}/projects/$encodedName/issues")), user.token, """{"issues":[{"title":"제목","body":"내용","number":3}]}""")
            .andExpect(status().isCreated).andExpect(jsonPath("$[0].location").value("/${project.owner}/$encodedName/issue/3"))
    }

    @Test
    fun `이슈 여러 개를 모든 항목과 함께 등록한다`() {
        val (user, project) = member("c")
        val author = newUser(); val assignee = newUser()
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "Type", isExclusive = false))
        val label = labels.save(IssueLabel(project = project, category = category, name = "bug", color = "#fff"))
        val created = dateFormat.format(Date(1_600_000_000_000L)); val due = dateFormat.format(Date(1_900_000_000_000L))
        val body = """{"issues":[{"title":"가져온 이슈","body":"내용","state":"closed","author":{"email":"${author.email}","loginId":"x","name":"x"},"createdAt":"$created","updatedAt":"$created",
            "assignees":[{"email":"${assignee.email}"}],"milestoneTitle":"v1","dueDate":"$due","labels":[{"labelName":"bug","category":"Type"}],"number":7}],"sendNotification":true}"""
        json(post(api(project, "issues")), user.token, body).andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].status").value(201)).andExpect(jsonPath("$[0].location").value("/${project.owner}/${project.name}/issue/7"))
        entityManager.flush(); entityManager.clear()
        val saved = issues.findAll().single { it.project?.id == project.id }
        assertEquals(7L, saved.number); assertEquals("가져온 이슈", saved.title); assertEquals(State.CLOSED, saved.state); assertEquals(author.loginId, saved.authorLoginId)
        assertEquals(assignee.id, saved.assignee?.user?.id); assertEquals(milestone.id, saved.milestone?.id); assertEquals(setOf(label.id), saved.labels.map { it.id }.toSet())
        assertEquals(1_600_000_000_000L / 1000, saved.createdDate!!.time / 1000); assertEquals(0, saved.numOfComments)
        // v1.6 `afterNewIssue`는 JSON의 작성자가 아니라 요청을 보낸 로그인 사용자를 보낸 사람으로 쓴다
        assertEquals(listOf(user.id), events.stream(IssueCreatedActivity::class.java).map { it.actor.id }.toList())
        assertEquals(1, events.stream(IssueCreatedActivity::class.java).count())
    }

    @Test
    fun `번호를 주지 않으면 다음 번호를 쓰고 작성자는 요청한 사용자이다`() {
        val (user, project) = member("d")
        json(post(api(project, "issues")), user.token, """{"issues":[{"title":"a"},{"title":"b"}]}""").andExpect(status().isCreated).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].location").value("/${project.owner}/${project.name}/issue/1")).andExpect(jsonPath("$[1].location").value("/${project.owner}/${project.name}/issue/2"))
        entityManager.flush(); entityManager.clear()
        val saved = issues.findAll().filter { it.project?.id == project.id }.sortedBy { it.number }
        assertEquals(user.loginId, saved[0].authorLoginId); assertEquals(State.OPEN, saved[0].state)
        assertEquals(0, events.stream(IssueCreatedActivity::class.java).count())
    }

    @Test
    fun `모르는 작성자는 사용자를 만들지 않고 익명 사용자가 작성자가 된다 (v1_6 findByEmail은 null을 주지 않는다)`() {
        val (user, project) = member("e")
        json(post(api(project, "issues")), user.token, """{"issues":[{"title":"t","author":{"loginId":"imported","name":"가져온 사람","email":"imported@example.com"}}]}""").andExpect(status().isCreated)
        entityManager.flush(); entityManager.clear()
        // v1.6 IssueApi.findAuthor: User.findByEmail이 NullUser를 돌려주므로 createUserNode에 닿지 못한다
        assertNull(users.findByEmail("imported@example.com"))
        val saved = issues.findAll().single { it.project?.id == project.id }
        assertEquals(-1L, saved.authorId); assertEquals("", saved.authorLoginId)
    }

    @Test
    fun `보조 이메일이나 대소문자만 다른 이메일의 작성자도 기존 사용자로 찾는다 (v1_6 3단계 조회)`() {
        val (user, project) = member("h")
        val upper = user.email!!.uppercase()
        json(post(api(project, "issues")), user.token, """{"issues":[{"title":"t","author":{"loginId":"x","name":"x","email":"$upper"}}]}""").andExpect(status().isCreated)
        entityManager.flush(); entityManager.clear()
        assertEquals(user.id, issues.findAll().single { it.project?.id == project.id }.authorId)
    }

    @Test
    fun `작성자를 만들 수 없으면 익명 사용자가 작성자가 된다`() {
        val (user, project) = member("g")
        // 이미 있는 로그인 아이디라 사용자 생성이 거절된다 (v1.6 findAuthor는 이때 NullUser, id -1을 쓴다)
        json(post(api(project, "issues")), user.token, """{"issues":[{"title":"t","author":{"loginId":"${user.loginId}","name":"중복","email":"dup-author@example.com"}}]}""").andExpect(status().isCreated)
        entityManager.flush(); entityManager.clear()
        val saved = issues.findAll().single { it.project?.id == project.id }
        assertEquals(-1L, saved.authorId); assertEquals("", saved.authorLoginId)
        assertEquals(null, users.findByEmail("dup-author@example.com"))
    }

    @Test
    fun `등록의 오류`() {
        val (user, project) = member("f")
        json(post(api(project, "issues")), user.token, """{"nope":1}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("No issues key exists or value wasn't array!"))
        mockMvc.perform(post(api(project, "issues")).header("Authorization", "token ${user.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Expecting Json data"))
        val secret = newProject(ProjectScope.PRIVATE)
        json(post(api(secret, "issues")), user.token, """{"issues":[{"title":"t"}]}""").andExpect(status().isForbidden)
    }

    // --- 수정 ---

    @Test
    fun `이슈를 고치면 이벤트가 남고 결과를 돌려준다`() {
        val (user, project) = member("g")
        val assignee = newUser(); val i = issue(project, user)
        milestones.save(Milestone(project = project, title = "v2", state = State.OPEN))
        json(put(api(project, "issues/1")), user.token, """{"title":"새 제목","body":"새 본문","milestoneTitle":"v2","state":"CLOSED","assignees":[{"loginId":"${assignee.loginId}","email":"${assignee.email}"}]}""")
            .andExpect(status().isOk).andExpect(jsonPath("$.result.title").value("새 제목")).andExpect(jsonPath("$.result.state").value("CLOSED")).andExpect(jsonPath("$.result.milestoneTitle").value("v2"))
            .andExpect(jsonPath("$.result.assignees[0].loginId").value(assignee.loginId)).andExpect(jsonPath("$.result.events.length()").value(2))
        entityManager.flush(); entityManager.clear()
        val types = entityManager.createQuery("select e.eventType from IssueEvent e where e.issue.id = :id order by e.id", EventType::class.java).setParameter("id", i.id).resultList
        assertEquals(listOf(EventType.ISSUE_STATE_CHANGED, EventType.ISSUE_ASSIGNEE_CHANGED), types)
        assertEquals("새 본문", issues.findById(i.id!!).get().body)
    }

    @Test
    fun `수정의 오류와 권한`() {
        val (user, project) = member("h")
        issue(project, user)
        val body = """{"title":"t","body":"b","state":"OPEN","assignees":[]}"""
        json(put(api(project, "issues/1")), null, body).andExpect(status().isUnauthorized)
        json(put(api(project, "issues/1")), newUser("tkoutsider").token, body).andExpect(status().isForbidden)
        mockMvc.perform(put(api(project, "issues/1")).header("Authorization", "token ${user.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
    }

    @Test
    fun `상태만 바꾼다`() {
        val (user, project) = member("i")
        val i = issue(project, user)
        json(patch(api(project, "issues/1")), user.token, """{"state":"closed"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.state").value("CLOSED")).andExpect(jsonPath("$.result.events.length()").value(1))
        json(patch(api(project, "issues/1")), user.token, """{"state":"closed"}""").andExpect(jsonPath("$.result.events.length()").value(1))
        json(patch(api(project, "issues/1")), null, """{"state":"open"}""").andExpect(status().isUnauthorized)
        entityManager.flush(); entityManager.clear()
        assertEquals(State.CLOSED, issues.findById(i.id!!).get().state)
    }

    @Test
    fun `수정, 상태, 라벨 변경은 v1_6 save처럼 제목 머리말 개수를 늘린다`() {
        val (user, project) = member("t")
        val i = issue(project, user); i.title = "[버그] 제목"; issues.save(i)
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val x = labels.save(IssueLabel(project = project, category = category, name = "x", color = "#fff"))
        val frequency = { entityManager.flush(); entityManager.clear(); titleHeads.findFirstByProjectIdAndHeadKeyword(project.id!!, "버그")?.frequency ?: 0 }
        json(patch(api(project, "issues/1")), user.token, """{"state":"closed"}""").andExpect(status().isOk)
        assertEquals(1, frequency())
        json(post(api(project, "issuelabel/1")), user.token, "[\"${x.id}\"]").andExpect(status().isOk)
        assertEquals(2, frequency())
        json(put(api(project, "issues/1")), user.token, """{"title":"[버그] 제목","body":"b","state":"OPEN","assignees":[]}""").andExpect(status().isOk)
        assertEquals(3, frequency())
    }

    // --- 댓글 ---

    @Test
    fun `토큰으로 댓글을 달면 이슈 결과를 돌려준다`() {
        val (user, project) = member("j")
        issue(project, user)
        json(post(api(project, "issues/1/comments")), user.token, """{"comment":"토큰 댓글"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.comments[0].body").value("토큰 댓글")).andExpect(jsonPath("$.result.comments[0].author.loginId").value(user.loginId))
        entityManager.flush(); entityManager.clear()
        val saved = comments.findAll().single()
        assertEquals(user.loginId, saved.authorLoginId); assertEquals(project.id, saved.projectId)
        assertEquals(1, issues.findAll().single { it.project?.id == project.id }.numOfComments)
    }

    @Test
    fun `본문에 comment가 없으면 가져오기 흐름으로 작성자와 시각을 정한다 (v1_6의 세션 흐름, 이제 토큰 인증)`() {
        val owner = newUser(); val project = newProject(); val writer = newUser("tkwriter")
        issue(project, owner)
        val created = dateFormat.format(Date(1_600_000_000_000L))
        mockMvc.perform(post(api(project, "issues/1/comments")).header("Authorization", "token ${writer.token}").contentType(MediaType.APPLICATION_JSON).content("""{"body":"본문 댓글","createdAt":"$created","author":{"email":"${owner.email}"}}"""))
            .andExpect(status().isCreated).andExpect(jsonPath("$.status").value(201)).andExpect(jsonPath("$.location").value(org.hamcrest.Matchers.startsWith("/${project.owner}/${project.name}/issue/1#comment-")))
        entityManager.flush(); entityManager.clear()
        val saved = comments.findAll().single()
        assertEquals(owner.loginId, saved.authorLoginId); assertEquals(1_600_000_000_000L / 1000, saved.createdDate!!.time / 1000)
    }

    @Test
    fun `댓글의 오류`() {
        val (user, project) = member("k")
        issue(project, user)
        mockMvc.perform(post(api(project, "issues/1/comments")).header("Authorization", "token ${user.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
        mockMvc.perform(post(api(project, "issues/1/comments")).header("Authorization", "token nobody").contentType(MediaType.APPLICATION_JSON).content("""{"comment":"x"}""")).andExpect(status().isUnauthorized)
        val secret = newProject(ProjectScope.PRIVATE); issue(secret, user)
        val outsider = newUser("tkoutsider2")
        mockMvc.perform(post(api(secret, "issues/1/comments")).header("Authorization", "token ${outsider.token}").contentType(MediaType.APPLICATION_JSON).content("""{"body":"x"}""")).andExpect(status().isForbidden)
    }

    // --- 라벨 ---

    @Test
    fun `이슈의 라벨을 바꾼다`() {
        val (user, project) = member("l")
        val i = issue(project, user)
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val x = labels.save(IssueLabel(project = project, category = category, name = "x", color = "#fff"))
        json(post(api(project, "issuelabel/1")), user.token, "[\"${x.id}\"]").andExpect(status().isOk).andExpect(jsonPath("$.id").value(project.owner)).andExpect(jsonPath("$.labels").value(1))
        entityManager.flush(); entityManager.clear()
        assertEquals(setOf(x.id), issues.findById(i.id!!).get().labels.map { it.id }.toSet())
        json(post(api(project, "issuelabel/1")), newUser("tkz").token, "[]").andExpect(status().isForbidden)
        mockMvc.perform(post(api(project, "issuelabel/1")).header("Authorization", "token ${user.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
    }

    // --- 가져오기 ---

    @Test
    fun `글을 이슈로 가져오면 댓글과 첨부가 옮겨지고 글은 사라진다`() {
        val (user, project) = member("m")
        project.lastPostingNumber = 1; projects.save(project)
        val post = postings.save(Posting(project = project, number = 1, title = "글", body = "본문", authorId = user.id, authorLoginId = user.loginId, authorName = user.name, notice = false, readme = false, numOfComments = 2, history = "이력", createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)))
        val parent = PostingComment(contents = "부모", createdDate = Date(1_700_000_001_000L), authorId = user.id, authorLoginId = user.loginId, authorName = user.name, posting = post, projectId = project.id!!)
        entityManager.persist(parent); entityManager.flush()
        entityManager.persist(PostingComment(contents = "자식", createdDate = Date(1_700_000_002_000L), authorId = user.id, authorLoginId = user.loginId, authorName = user.name, posting = post, projectId = project.id!!, parentComment = parent))
        val file = attachments.save(Attachment(name = "a.txt", hash = "hash-import", containerType = ResourceType.BOARD_POST, containerId = post.id!!, mimeType = "text/plain", size = 1L, createdDate = Date(), ownerLoginId = user.loginId))
        entityManager.persist(dev.yona.domain.notification.NotificationEvent(title = "t", senderId = user.id, created = Date(), resourceType = ResourceType.BOARD_POST, resourceId = post.id.toString(), eventType = dev.yona.domain.enumeration.EventType.NEW_POSTING))
        entityManager.flush(); entityManager.clear()
        json(post(api(project, "issues/imports?postNumber=1")), user.token, null).andExpect(status().isOk).andExpect(jsonPath("$.number").value(1))
        entityManager.flush(); entityManager.clear()
        val imported = issues.findAll().single { it.project?.id == project.id }
        assertEquals("글", imported.title); assertEquals("이력", imported.history); assertEquals(Date(1_700_000_000_000L), imported.createdDate)
        val copied = comments.findAll().filter { it.issue?.id == imported.id }.sortedBy { it.createdDate }
        assertEquals(listOf("부모", "자식"), copied.map { it.contents }); assertEquals(copied[0].id, copied[1].parentComment?.id)
        assertNull(postings.findById(post.id!!).orElse(null))
        // v1.6 `posting.deleteOnly()`는 구독 기록만 지우고 알림 이벤트는 지우지 않는다
        assertEquals(1, notificationEvents.findAll().count { it.resourceType == ResourceType.BOARD_POST && it.resourceId == post.id.toString() })
        val moved = attachments.findAll().single { it.hash == "hash-import" }
        assertEquals(ResourceType.ISSUE_POST, moved.containerType); assertEquals(imported.id, moved.containerId); assertNotNull(file.id)
    }

    @Test
    fun `가져오기의 오류와 권한`() {
        val (user, project) = member("n")
        json(post(api(project, "issues/imports")), user.token, null).andExpect(status().isBadRequest)
        json(post(api(project, "issues/imports?postNumber=abc")), user.token, null).andExpect(status().isBadRequest)
        json(post(api(project, "issues/imports?postNumber=9")), user.token, null).andExpect(status().isNotFound)
        project.lastPostingNumber = 1; projects.save(project)
        postings.save(Posting(project = project, number = 1, title = "글", body = "본문", notice = false, readme = false, numOfComments = 0, createdDate = Date(), updatedDate = Date()))
        json(post(api(project, "issues/imports?postNumber=1")), newUser("tkq").token, null).andExpect(status().isForbidden)
    }
}
