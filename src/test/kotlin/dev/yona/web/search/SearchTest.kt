package dev.yona.web.search

import dev.yona.support.selectFirst
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 통합 검색(`GET /search`, `/organizations/:name/search`, `/:user/:project/search`)을 검증한다.
 *
 * 원본: `app/controllers/SearchApp.java`, `app/models/Search.java`, `app/models/SearchResult.java`, `app/views/search 아래 템플릿`.
 */
@AutoConfigureMockMvc
@Transactional
class SearchTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var issueComments: IssueCommentRepository
    @Autowired private lateinit var postingComments: PostingCommentRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository

    private var sequence = 0
    private var number = 0L
    private var clock = 1_700_000_000_000L

    private fun newUser(name: String? = null, state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "sr$n", name = name ?: "sr$n", email = "sr$n@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(owner: User, name: String, scope: ProjectScope = ProjectScope.PUBLIC, org: Organization? = null, overview: String? = null): Project =
        projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = scope, organization = org, overview = overview, createdDate = Date()))
            .also { menuSettings.save(ProjectMenuSetting(project = it, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true)) }

    private fun join(user: User, project: Project) = projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))

    private fun organization(name: String, vararg members: User): Organization {
        val org = organizations.save(Organization(name = name, descr = "d", created = Date()))
        members.forEach { organizationUsers.save(OrganizationUser(user = it, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get())) }
        return org
    }

    private fun issue(project: Project, title: String, author: User, body: String = "본문", assignee: User? = null): Issue = issues.save(Issue(
        project = project, number = ++number, title = title, body = body, state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
        assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, numOfComments = 0, weight = 0, createdDate = Date(++clock), updatedDate = Date(clock)
    ))

    private fun search(path: String, session: MockHttpSession?, vararg params: Pair<String, String>): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) }; params.forEach { (k, v) -> param(k, v) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun all(session: MockHttpSession?, type: String, keyword: String, vararg more: Pair<String, String>) =
        search("/search", session, "searchType" to type, "keyword" to keyword, *more)

    private fun titles(doc: Document) = doc.select("ul.search-list-wrap li.search-list-item a.title").map { it.text().trim() }

    // --- 검증 ---

    @Test
    fun `keyword나 searchType이 없거나 알 수 없으면 400이다`() {
        mockMvc.perform(get("/search").param("searchType", "issue")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/search").param("keyword", "a")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/search").param("keyword", "a").param("searchType", "nope")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/search").param("keyword", "a").param("searchType", "not available")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/search").param("keyword", "a").param("searchType", "ISSUE")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/search").param("keyword", "").param("searchType", "issue")).andExpect(status().isBadRequest)
    }

    @Test
    fun `공백 하나도 유효한 검색어이다`() {
        mockMvc.perform(get("/search").param("keyword", " ").param("searchType", "issue")).andExpect(status().isOk)
    }

    // --- AUTO와 탭 ---

    @Test
    fun `AUTO는 건수가 있는 첫 탭을 고른다`() {
        val owner = newUser("엘리스"); val p = project(owner, "autoproj", overview = "zebraword 개요")
        postings.save(Posting(project = p, number = ++number, title = "zebraword 글", body = "b", authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, createdDate = Date(), updatedDate = Date()))
        val doc = all(null, "auto", "zebraword")
        assertEquals("project", doc.selectFirst("#searchInnerForm input[name=searchType]")!!.attr("value"))
        assertEquals(listOf("project"), doc.select("ul.search-category-wrap li.active a").map { it.attr("data-type") })
    }

    @Test
    fun `모두 0이면 AUTO는 이슈 탭이다`() {
        val doc = all(null, "auto", "nothingmatchesthis")
        assertEquals("issue", doc.selectFirst("#searchInnerForm input[name=searchType]")!!.attr("value"))
        assertTrue(doc.select(".empty-result").isNotEmpty())
    }

    @Test
    fun `탭은 여덟 개이고 건수가 없으면 empty이다`() {
        val owner = newUser(); val p = project(owner, "tabs"); issue(p, "tabcount 이슈", owner); issue(p, "tabcount 둘째", owner)
        val doc = all(null, "issue", "tabcount")
        val tabs = doc.select("ul.search-category-wrap li")
        assertEquals(listOf("issue", "user", "project", "post", "milestone", "issue_comment", "post_comment", "review"), tabs.map { it.selectFirst("a")!!.attr("data-type") })
        assertEquals("2", tabs[0].selectFirst(".num-badge")!!.text())
        assertTrue(tabs[0].hasClass("active")); assertFalse(tabs[0].hasClass("empty"))
        assertTrue(tabs[1].hasClass("empty"))
        assertEquals("search-category", tabs[0].selectFirst("a")!!.attr("data-toggle"))
    }

    @Test
    fun `결과 제목에 건수와 탭 이름이 들어간다`() {
        val owner = newUser(); val p = project(owner, "titleproj"); issue(p, "headline 이슈", owner)
        val title = all(null, "issue", "headline").selectFirst("h3.search-result-title")!!
        assertEquals("1", title.selectFirst("strong")!!.text())
        assertTrue(title.text().contains("Issues"))
    }

    // --- 이슈 ---

    @Test
    fun `익명은 공개 프로젝트의 이슈만 최근 순으로 본다`() {
        val owner = newUser(); val open = project(owner, "pub"); val closed = project(owner, "priv", ProjectScope.PRIVATE)
        issue(open, "needle 첫째", owner); issue(open, "needle 둘째", owner); issue(closed, "needle 비공개", owner)
        assertEquals(listOf("needle 둘째", "needle 첫째"), titles(all(null, "issue", "needle")))
    }

    @Test
    fun `제목과 본문에서 대소문자 구분 없이 찾는다`() {
        val owner = newUser(); val p = project(owner, "case")
        issue(p, "Haystack 제목", owner); issue(p, "무관한 제목", owner, body = "본문에 HAYSTACK 이 있다"); issue(p, "무관", owner)
        assertEquals(setOf("Haystack 제목", "무관한 제목"), titles(all(null, "issue", "haystack")).toSet())
    }

    @Test
    fun `검색어의 역슬래시는 LIKE의 이스케이프 문자로 쓰인다 (v1_6 Ebean은 기본 이스케이프를 그대로 쓴다)`() {
        // 패턴 `%\%`는 "퍼센트로 끝나는 글"이다. 역슬래시를 글자 그대로 찾는 것이 아니다
        val owner = newUser(); val p = project(owner, "bslash")
        issue(p, "끝이 퍼센트 100%", owner); issue(p, "역슬래시 a\\b", owner)
        assertEquals(listOf("끝이 퍼센트 100%"), titles(all(null, "issue", "\\")))
    }

    @Test
    fun `구성원은 비공개 프로젝트의 이슈도 본다`() {
        val owner = newUser(); val member = newUser(); val priv = project(owner, "privmember", ProjectScope.PRIVATE)
        issue(priv, "secretneedle", owner); join(member, priv)
        assertEquals(listOf("secretneedle"), titles(all(login(member), "issue", "secretneedle")))
        assertEquals(emptyList(), titles(all(login(newUser()), "issue", "secretneedle")))
    }

    @Test
    fun `내가 쓴 이슈와 내가 담당한 이슈는 권한을 잃은 프로젝트에서도 찾는다`() {
        val owner = newUser(); val me = newUser(); val priv = project(owner, "lost", ProjectScope.PRIVATE)
        issue(priv, "ownneedle 내가 씀", me); issue(priv, "ownneedle 내가 담당", owner, assignee = me); issue(priv, "ownneedle 남의 것", owner)
        assertEquals(setOf("ownneedle 내가 씀", "ownneedle 내가 담당"), titles(all(login(me), "issue", "ownneedle")).toSet())
    }

    @Test
    fun `내 조직의 보호 프로젝트는 조직 구성원에게만 보인다`() {
        val owner = newUser(); val member = newUser(); val outsider = newUser()
        val org = organization("acme-search", member); val p = project(owner, "prot", ProjectScope.PROTECTED, org)
        issue(p, "protneedle", owner)
        assertEquals(listOf("protneedle"), titles(all(login(member), "issue", "protneedle")))
        assertEquals(emptyList(), titles(all(login(outsider), "issue", "protneedle")))
    }

    @Test
    fun `이슈 항목에는 번호와 링크와 발췌와 프로젝트와 작성자가 있다`() {
        val owner = newUser("작성자 이름"); val p = project(owner, "itemproj")
        val one = issue(p, "itemneedle", owner, body = "앞 내용 itemneedle 뒷 내용")
        val item = all(null, "issue", "itemneedle").selectFirst("li.search-list-item")!!
        assertEquals("#${one.number}", item.selectFirst(".post-id")!!.text())
        assertEquals("/${owner.loginId}/itemproj/issue/${one.number}", item.selectFirst("a.title")!!.attr("href"))
        assertEquals(listOf("앞 내용 itemneedle 뒷 내용"), item.select("p.search-content-body").map { it.text().trim() })
        assertEquals("${owner.loginId}/itemproj", item.selectFirst("a.project-link")!!.text().trim())
        assertEquals("/${owner.loginId}", item.select(".search-meta-info a.meta-item").last()!!.attr("href"))
        assertEquals("작성자 이름", item.select(".search-meta-info a.meta-item").last()!!.text().trim())
    }

    @Test
    fun `발췌가 본문보다 짧으면 말줄임이 붙는다`() {
        val owner = newUser(); val p = project(owner, "ellipsis")
        issue(p, "ellneedle", owner, body = "x".repeat(100) + "ellneedle" + "y".repeat(100))
        val body = all(null, "issue", "ellneedle").selectFirst("p.search-content-body")!!.text()
        assertTrue(body.endsWith("....."))
    }

    @Test
    fun `쪽은 20개씩이고 pageNum으로 넘긴다`() {
        val owner = newUser(); val p = project(owner, "paging")
        (1..25).forEach { issue(p, "pagingneedle$it", owner) }
        val first = all(null, "issue", "pagingneedle"); val second = all(null, "issue", "pagingneedle", "pageNum" to "2")
        assertEquals(20, titles(first).size); assertEquals(5, titles(second).size)
        assertTrue(first.html().contains("yobi.Pagination.update(\$(\"#pagination\"), 2)"))
        assertEquals("pagingneedle5", titles(second).first())
    }

    // --- 사용자, 프로젝트 ---

    @Test
    fun `사용자는 활성 사용자만 이름 순으로 이름이나 로그인 ID로 찾는다`() {
        newUser("Zed usersearch"); newUser("Amy usersearch"); newUser("Locked usersearch", UserState.LOCKED)
        val doc = all(null, "user", "usersearch")
        assertEquals(listOf("Amy usersearch", "Zed usersearch"), doc.select("a.title.user-link").map { it.text().substringBefore(" (@") })
        assertTrue(doc.select(".infos").first()!!.text().contains("Member since"))
    }

    @Test
    fun `프로젝트는 이름과 개요에서 이름 순으로 찾고 개요를 보인다`() {
        val owner = newUser()
        project(owner, "zproj", overview = "projneedle 설명"); project(owner, "aproj-projneedle"); project(owner, "hidden-projneedle", ProjectScope.PRIVATE)
        val doc = all(null, "project", "projneedle")
        assertEquals(listOf("${owner.loginId}/aproj-projneedle", "${owner.loginId}/zproj"), doc.select("a.title.project-link").map { it.text().trim() })
        assertEquals("projneedle 설명", doc.select(".search-content-body").map { it.text().trim() }.first { it.isNotEmpty() })
        assertTrue(doc.select("#pagination").isEmpty())
    }

    @Test
    fun `프로젝트는 구성원이면 비공개도 보인다`() {
        val owner = newUser(); val member = newUser(); val priv = project(owner, "privproj-needle2", ProjectScope.PRIVATE)
        join(member, priv)
        assertEquals(listOf("${owner.loginId}/privproj-needle2"), all(login(member), "project", "needle2").select("a.title.project-link").map { it.text().trim() })
    }

    // --- 마일스톤, 댓글, 리뷰 ---

    @Test
    fun `마일스톤은 마감일 내림차순이고 마감일과 남은 기간을 보인다`() {
        val owner = newUser(); val p = project(owner, "mile")
        milestones.save(Milestone(project = p, title = "milneedle 가까움", contents = "c", state = State.OPEN, dueDate = Date(System.currentTimeMillis() + 86_400_000L * 3)))
        milestones.save(Milestone(project = p, title = "milneedle 멀음", contents = "c", state = State.OPEN, dueDate = Date(System.currentTimeMillis() + 86_400_000L * 30)))
        val doc = all(null, "milestone", "milneedle")
        assertEquals(listOf("milneedle 멀음", "milneedle 가까움"), titles(doc))
        assertTrue(doc.select(".search-meta-info").first()!!.text().contains("Due Date"))
    }

    @Test
    fun `마감일이 없는 마일스톤은 맨 뒤에 둔다`() {
        val owner = newUser(); val p = project(owner, "milenull")
        milestones.save(Milestone(project = p, title = "nullneedle 없음", contents = "c", state = State.OPEN))
        milestones.save(Milestone(project = p, title = "nullneedle 있음", contents = "c", state = State.OPEN, dueDate = Date(System.currentTimeMillis() + 86_400_000L * 3)))
        // v1.6(MariaDB)은 desc("dueDate")에서 NULL을 맨 뒤에 둔다. PostgreSQL에서도 같게 nulls last로 맞춘다.
        assertEquals(listOf("nullneedle 있음", "nullneedle 없음"), titles(all(null, "milestone", "nullneedle")))
    }

    @Test
    fun `이슈 댓글은 이슈 링크에 댓글 앵커를 달고 Re) 제목이다`() {
        val owner = newUser(); val p = project(owner, "icomment"); val one = issue(p, "원 이슈", owner)
        val c = issueComments.save(IssueComment(contents = "commentneedle 내용", authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, issue = one, projectId = p.id!!, createdDate = Date()))
        val link = all(null, "issue_comment", "commentneedle").selectFirst("li.search-list-item a")!!
        assertEquals("/${owner.loginId}/icomment/issue/${one.number}#comment-${c.id}", link.attr("href"))
        assertEquals("Re) 원 이슈", link.text().trim())
    }

    @Test
    fun `키워드의 퍼센트와 밑줄은 와일드카드이다 (v1_6 Ebean icontains는 이스케이프하지 않는다)`() {
        val owner = newUser(); val p = project(owner, "wildcard")
        issue(p, "wildxcard 제목", owner); issue(p, "wildcard 제목", owner); issue(p, "무관한 제목", owner)
        // '_'는 한 글자 와일드카드라 wild_card는 wildxcard만 찾고 wildcard는 찾지 않는다
        assertEquals(setOf("wildxcard 제목"), titles(all(null, "issue", "wild_card")).toSet())
        // '%'는 임의 길이 와일드카드이다
        assertEquals(setOf("wildxcard 제목", "wildcard 제목"), titles(all(null, "issue", "wild%card")).toSet())
    }

    @Test
    fun `작성자 행이 없으면 NullUser의 이름 링크를 그린다 (v1_6 User_findByLoginId)`() {
        val owner = newUser(); val p = project(owner, "nulluser"); val one = issue(p, "원 이슈", owner)
        issueComments.save(IssueComment(contents = "ghostneedle 내용", authorId = -1, authorLoginId = "", authorName = "", issue = one, projectId = p.id!!, createdDate = Date()))
        val meta = all(null, "issue_comment", "ghostneedle").selectFirst("li.search-list-item .search-meta-info a.meta-item[data-bs-toggle=tooltip]")!!
        // NullUser: 이름은 user.notExists.name, 로그인 ID는 빈 문자열이라 링크는 "/"이다
        assertEquals("/", meta.attr("href"))
        assertEquals(java.util.ResourceBundle.getBundle("messages", java.util.Locale.getDefault()).getString("user.notExists.name"), meta.text().trim())
    }

    @Test
    fun `게시글과 게시글 댓글을 찾는다`() {
        val owner = newUser(); val p = project(owner, "board")
        val post = postings.save(Posting(project = p, number = ++number, title = "게시 제목", body = "postneedle 본문", authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, createdDate = Date(), updatedDate = Date()))
        val c = postingComments.save(PostingComment(contents = "pcneedle 댓글", createdDate = Date(), authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, posting = post, projectId = p.id!!))
        assertEquals("/${owner.loginId}/board/post/${post.number}", all(null, "post", "postneedle").selectFirst("li.search-list-item a.title")!!.attr("href"))
        assertEquals("/${owner.loginId}/board/post/${post.number}#comment-${c.id}", all(null, "post_comment", "pcneedle").selectFirst("li.search-list-item a")!!.attr("href"))
    }

    @Test
    fun `이름이 비어 있는 작성자의 게시글 댓글은 키 이름이 아니라 작성자 없음 문구를 보인다 (C028)`() {
        val owner = newUser(); val ghost = newUser(name = ""); val p = project(owner, "noauthorboard")
        val post = postings.save(Posting(project = p, number = ++number, title = "게시 제목", body = "본문", authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, createdDate = Date(), updatedDate = Date()))
        postingComments.save(PostingComment(contents = "pcghost 댓글", createdDate = Date(), authorId = ghost.id, authorLoginId = ghost.loginId, authorName = "", posting = post, projectId = p.id!!))
        val html = all(null, "post_comment", "pcghost").html()
        assertFalse(html.contains("posting.noAuthor"), "메시지 키 이름이 그대로 보이면 안 된다")
        assertTrue(html.contains(java.util.ResourceBundle.getBundle("messages", java.util.Locale.ROOT).getString("posting.noAuthor")), "화면 언어는 영어(기본 번들)이다")
    }

    @Test
    fun `코드 리뷰 댓글을 찾는다`() {
        val owner = newUser(); val p = project(owner, "review")
        val thread = threads.save(NonRangedCodeCommentThread(commitId = "abcdef0").also { it.project = p; it.createdDate = Date() })
        reviewComments.save(ReviewComment(contents = "reviewneedle 내용", createdDate = Date(), authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, thread = thread))
        val doc = all(null, "review", "reviewneedle")
        assertEquals(1, doc.select("li.search-list-item").size)
        assertTrue(doc.selectFirst("li.search-list-item a")!!.attr("href").startsWith("/${owner.loginId}/review/commit/abcdef0#thread-${thread.id}"))
    }

    // --- 조직 ---

    @Test
    fun `조직 범위는 그 조직의 프로젝트만 찾고 프로젝트 탭이 있다`() {
        val owner = newUser(); val org = organization("orgscope"); val inOrg = project(owner, "inorg", org = org); val outside = project(owner, "outside")
        issue(inOrg, "orgneedle 안", owner); issue(outside, "orgneedle 밖", owner)
        val doc = search("/organizations/orgscope/search", null, "searchType" to "issue", "keyword" to "orgneedle")
        assertEquals(listOf("orgneedle 안"), titles(doc))
        assertEquals("/organizations/orgscope/search", doc.selectFirst("#searchInnerForm")!!.attr("action"))
        assertTrue(doc.select("ul.search-category-wrap li a[data-type=project]").isNotEmpty())
    }

    @Test
    fun `조직 범위 사용자는 조직 구성원만이다`() {
        val member = newUser("Member orgusersearch"); newUser("Other orgusersearch"); organization("orguserscope", member)
        val doc = search("/organizations/orguserscope/search", null, "searchType" to "user", "keyword" to "orgusersearch")
        assertEquals(listOf("Member orgusersearch"), doc.select("a.title.user-link").map { it.text().substringBefore(" (@") })
    }

    @Test
    fun `조직 범위의 보호 프로젝트는 그 조직 구성원에게만 보인다`() {
        val owner = newUser(); val member = newUser(); val org = organization("orgprot", member); val p = project(owner, "orgprotproj", ProjectScope.PROTECTED, org)
        issue(p, "orgprotneedle", owner)
        val params = arrayOf("searchType" to "issue", "keyword" to "orgprotneedle")
        assertEquals(listOf("orgprotneedle"), titles(search("/organizations/orgprot/search", login(member), *params)))
        assertEquals(emptyList(), titles(search("/organizations/orgprot/search", login(newUser()), *params)))
    }

    @Test
    fun `조직 범위의 오류 응답`() {
        organization("orgerr")
        mockMvc.perform(get("/organizations/orgerr/search").param("searchType", "issue")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/organizations/nosuchorg/search").param("searchType", "issue").param("keyword", "a")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/organizations/orgerr/search").param("searchType", "bad").param("keyword", "a")).andExpect(status().isBadRequest)
    }

    // --- 프로젝트 ---

    @Test
    fun `프로젝트 범위는 그 프로젝트만 찾고 프로젝트 탭이 없다`() {
        val owner = newUser(); val a = project(owner, "scopea"); val b = project(owner, "scopeb")
        issue(a, "scopeneedle A", owner); issue(b, "scopeneedle B", owner)
        val doc = search("/${owner.loginId}/scopea/search", null, "searchType" to "issue", "keyword" to "scopeneedle")
        assertEquals(listOf("scopeneedle A"), titles(doc))
        assertTrue(doc.select("ul.search-category-wrap li a[data-type=project]").isEmpty())
        assertTrue(doc.select(".search-meta-info a.project-link").isEmpty())
        assertEquals("/${owner.loginId}/scopea/search", doc.selectFirst("#searchInnerForm")!!.attr("action"))
    }

    @Test
    fun `프로젝트 범위 사용자는 프로젝트 구성원만이다`() {
        val owner = newUser(); val p = project(owner, "scopeusers"); val member = newUser("Member psusersearch"); newUser("Other psusersearch"); join(member, p)
        val doc = search("/${owner.loginId}/scopeusers/search", null, "searchType" to "user", "keyword" to "psusersearch")
        assertEquals(listOf("Member psusersearch"), doc.select("a.title.user-link").map { it.text().substringBefore(" (@") })
    }

    @Test
    fun `프로젝트 범위의 오류 응답과 권한`() {
        val owner = newUser(); project(owner, "scopeerr"); project(owner, "scopepriv", ProjectScope.PRIVATE)
        mockMvc.perform(get("/${owner.loginId}/scopeerr/search").param("searchType", "project").param("keyword", "a")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/${owner.loginId}/scopeerr/search").param("searchType", "issue")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/${owner.loginId}/nosuch/search").param("searchType", "issue").param("keyword", "a")).andExpect(status().isForbidden)
        mockMvc.perform(get("/${owner.loginId}/scopepriv/search").param("searchType", "issue").param("keyword", "a")).andExpect(status().isForbidden)
    }
}
