package dev.yona.web.api

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 게시판 쓰기 REST의 b단계(로드맵 X-29b, `decisions.md` D-47)를 검증한다. 경로는 v1.6 접두사(`/-_-api/v1/owners/{owner}/projects/{projectName}`)에 GitHub 자원 경로를 얹은 것이다.
 * 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이고(D-36 4번), 삭제는 작성자와 관리자만, 수정은 구성원도 한다(D-48).
 */
@AutoConfigureMockMvc
@Transactional
class ExternalBoardWriteApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var postingComments: PostingCommentRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "bw$n", name = "bw$n", email = "bw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        token?.let { tokens[saved.id!!] = "bt$n$it"; dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!)) }
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "bwo$n", name = "bwp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun joined(project: Project, role: RoleType = RoleType.MEMBER): Pair<User, String> {
        val user = newUser("a"); projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user to tokenOf(user)
    }

    private fun posting(project: Project, author: User, number: Long = 1, title: String = "제목", notice: Boolean = false): Posting {
        project.lastPostingNumber = maxOf(project.lastPostingNumber ?: 0, number); projects.save(project)
        return postings.save(Posting(project = project, number = number, title = title, body = "본문", authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, notice = notice, createdDate = Date(), updatedDate = Date()))
    }

    private fun comment(posting: Posting, author: User, text: String = "댓글"): PostingComment =
        postingComments.save(PostingComment(contents = text, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, posting = posting, projectId = requireNotNull(posting.project?.id))).also {
            posting.numOfComments = (posting.numOfComments ?: 0) + 1; postings.save(posting)
        }

    private fun api(project: Project, path: String) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun reload(p: Posting): Posting { entityManager.flush(); entityManager.clear(); return postings.findById(p.id!!).get() }

    private fun labelsOf(project: Project): Triple<IssueLabel, IssueLabel, IssueLabel> {
        val exclusive = categories.save(IssueLabelCategory(project = project, name = "Type", isExclusive = true))
        val free = categories.save(IssueLabelCategory(project = project, name = "Free", isExclusive = false))
        return Triple(labels.save(IssueLabel(project = project, category = exclusive, name = "a", color = "#fff")), labels.save(IssueLabel(project = project, category = exclusive, name = "b", color = "#fff")), labels.save(IssueLabel(project = project, category = free, name = "c", color = "#fff")))
    }

    // --- 조회 ---

    @Test
    fun `게시글 한 건은 라벨과 공지 여부와 댓글을 담고 읽을 수 없는 토큰은 거절한다`() {
        val secret = newProject(ProjectScope.PRIVATE); val (member, token) = joined(secret); val p = posting(secret, member, notice = true)
        comment(p, member, "첫 댓글")
        val (a, _, _) = labelsOf(secret); p.labels.add(a); postings.save(p)
        json(get(api(secret, "posts/1")), token, null).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.title").value("제목")).andExpect(jsonPath("$.result.notice").value(true))
            .andExpect(jsonPath("$.result.labels[0].labelName").value("a")).andExpect(jsonPath("$.result.comments[0].body").value("첫 댓글"))
        val outsider = tokenOf(newUser("x").also { }.let { it })
        json(get(api(secret, "posts/1")), outsider, null).andExpect(status().isForbidden)
        json(get(api(secret, "posts/9")), token, null).andExpect(status().isNotFound)
        json(get(api(secret, "posts/1")), null, null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `게시글 목록은 검색과 정렬과 쪽 나누기를 하고 공지도 함께 보이며 라벨로 거른다`() {
        val project = newProject(); val (member, token) = joined(project)
        posting(project, member, 1, "가 첫째"); posting(project, member, 2, "나 공지", notice = true); posting(project, member, 3, "다 셋째")
        val (a, _, _) = labelsOf(project); postings.findByProjectIdAndNumber(project.id!!, 3)!!.let { it.labels.add(a); postings.save(it) }
        json(get(api(project, "posts")), token, null).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(3)).andExpect(jsonPath("$.items[0].number").value(3)).andExpect(jsonPath("$.items[1].notice").value(true))
        json(get(api(project, "posts?q=첫째")), token, null).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].title").value("가 첫째"))
        json(get(api(project, "posts?sort=number&order=asc&pageSize=2&page=2")), token, null).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].number").value(3)).andExpect(jsonPath("$.totalPages").value(2))
        json(get(api(project, "posts?labelIds=${a.id}")), token, null).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].number").value(3))
        json(get(api(project, "posts?sort=nothing")), token, null).andExpect(status().isBadRequest)
        json(get(api(project, "posts?order=up")), token, null).andExpect(status().isBadRequest)
        json(get(api(project, "posts")), tokenOf(newUser("x")), null).andExpect(status().isOk)
        val secret = newProject(ProjectScope.PRIVATE)
        json(get(api(secret, "posts")), tokenOf(newUser("y")), null).andExpect(status().isForbidden)
    }

    // --- 수정 ---

    @Test
    fun `부분 수정은 보낸 항목만 바꾸고 구성원은 남의 글도 고치며 이력과 공지 여부는 지킨다`() {
        val project = newProject(); val (author, _) = joined(project); val (_, memberToken) = joined(project)
        val p = posting(project, author, notice = true)
        json(patch(api(project, "posts/1")), memberToken, """{"title":"새 제목"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.title").value("새 제목")).andExpect(jsonPath("$.result.body").value("본문"))
        var saved = reload(p)
        assertEquals("새 제목", saved.title); assertEquals("본문", saved.body); assertEquals(true, saved.notice)
        json(patch(api(project, "posts/1")), memberToken, """{"body":"새 본문"}""").andExpect(status().isOk)
        saved = reload(p)
        assertEquals("새 제목", saved.title); assertEquals("새 본문", saved.body)
        assertTrue(saved.history.orEmpty().isNotEmpty(), "본문이 바뀌면 변경 이력이 쌓인다")
    }

    @Test
    fun `부분 수정의 오류와 권한`() {
        val project = newProject(); val (author, token) = joined(project); posting(project, author)
        json(patch(api(project, "posts/1")), token, """{}""").andExpect(status().isBadRequest)
        json(patch(api(project, "posts/1")), token, """{"title":"  "}""").andExpect(status().isBadRequest)
        json(patch(api(project, "posts/1")), token, null).andExpect(status().isBadRequest)
        json(patch(api(project, "posts/9")), token, """{"title":"x"}""").andExpect(status().isNotFound)
        json(patch(api(project, "posts/1")), tokenOf(newUser("x")), """{"title":"x"}""").andExpect(status().isForbidden)
        json(patch(api(project, "posts/1")), null, """{"title":"x"}""").andExpect(status().isUnauthorized)
    }

    // --- 삭제 ---

    @Test
    fun `게시글은 작성자와 관리자만 지우고 다른 구성원과 구성원이 아닌 토큰은 지우지 못한다 (D-48)`() {
        val project = newProject(); val (author, authorToken) = joined(project); val (_, memberToken) = joined(project); val (_, managerToken) = joined(project, RoleType.MANAGER)
        val own = posting(project, author, 1); val managed = posting(project, author, 2)
        json(delete(api(project, "posts/1")), tokenOf(newUser("x")), null).andExpect(status().isForbidden)
        json(delete(api(project, "posts/1")), memberToken, null).andExpect(status().isForbidden)
        assertTrue(postings.findById(own.id!!).isPresent)
        json(delete(api(project, "posts/1")), authorToken, null).andExpect(status().isNoContent)
        json(delete(api(project, "posts/2")), managerToken, null).andExpect(status().isNoContent)
        entityManager.flush(); entityManager.clear()
        assertNull(postings.findById(own.id!!).orElse(null)); assertNull(postings.findById(managed.id!!).orElse(null))
        json(delete(api(project, "posts/1")), authorToken, null).andExpect(status().isNotFound)
    }

    // --- 댓글 ---

    @Test
    fun `댓글 목록은 오래된 순서이고 읽을 수 없는 토큰은 거절한다`() {
        val secret = newProject(ProjectScope.PRIVATE); val (member, token) = joined(secret); val p = posting(secret, member)
        comment(p, member, "하나"); comment(p, member, "둘")
        json(get(api(secret, "posts/1/comments")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(2)).andExpect(jsonPath("$.result[0].body").value("하나"))
        json(get(api(secret, "posts/1/comments")), tokenOf(newUser("x")), null).andExpect(status().isForbidden)
    }

    @Test
    fun `댓글은 구성원이 고치고 비어 있으면 거절하며 다른 글의 댓글은 찾지 못한다`() {
        val project = newProject(); val (author, _) = joined(project); val (_, memberToken) = joined(project)
        val p = posting(project, author); val other = posting(project, author, 2); val c = comment(p, author, "원래"); val foreign = comment(other, author)
        json(patch(api(project, "posts/1/comments/${c.id}")), memberToken, """{"body":"고친 댓글"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.body").value("고친 댓글"))
        entityManager.flush(); entityManager.clear()
        assertEquals("고친 댓글", postingComments.findById(c.id!!).get().contents)
        json(patch(api(project, "posts/1/comments/${c.id}")), memberToken, """{"body":" "}""").andExpect(status().isBadRequest)
        json(patch(api(project, "posts/1/comments/${foreign.id}")), memberToken, """{"body":"x"}""").andExpect(status().isNotFound)
        json(patch(api(project, "posts/1/comments/${c.id}")), tokenOf(newUser("x")), """{"body":"x"}""").andExpect(status().isForbidden)
    }

    @Test
    fun `댓글은 작성자와 관리자만 지우고 글의 댓글 수가 줄어든다 (D-48)`() {
        val project = newProject(); val (author, authorToken) = joined(project); val (_, memberToken) = joined(project); val (_, managerToken) = joined(project, RoleType.MANAGER)
        val p = posting(project, author); val a = comment(p, author); val b = comment(p, author)
        json(delete(api(project, "posts/1/comments/${a.id}")), tokenOf(newUser("x")), null).andExpect(status().isForbidden)
        json(delete(api(project, "posts/1/comments/${a.id}")), memberToken, null).andExpect(status().isForbidden)
        json(delete(api(project, "posts/1/comments/${a.id}")), authorToken, null).andExpect(status().isNoContent)
        json(delete(api(project, "posts/1/comments/${b.id}")), managerToken, null).andExpect(status().isNoContent)
        entityManager.flush(); entityManager.clear()
        assertEquals(0, postings.findById(p.id!!).get().numOfComments)
    }

    // --- 라벨 ---

    @Test
    fun `라벨을 더하고 하나씩 떼며 같은 분류의 중복과 다른 프로젝트의 라벨은 거절한다`() {
        val project = newProject(); val other = newProject(); val (member, token) = joined(project); val p = posting(project, member)
        val (a, b, c) = labelsOf(project)
        val foreign = labels.save(IssueLabel(project = other, category = categories.save(IssueLabelCategory(project = other, name = "o", isExclusive = false)), name = "f", color = "#fff"))
        json(post(api(project, "posts/1/labels")), token, """{"labels":[${a.id}]}""").andExpect(status().isOk).andExpect(jsonPath("$.result.labels.length()").value(1))
        json(post(api(project, "posts/1/labels")), token, """{"labels":[${c.id}]}""").andExpect(status().isOk).andExpect(jsonPath("$.result.labels.length()").value(2))
        json(post(api(project, "posts/1/labels")), token, """{"labels":[${b.id}]}""").andExpect(status().isBadRequest)
        json(post(api(project, "posts/1/labels")), token, """{"labels":[${foreign.id}]}""").andExpect(status().isBadRequest)
        json(post(api(project, "posts/1/labels")), token, """{"labels":[]}""").andExpect(status().isBadRequest)
        json(delete(api(project, "posts/1/labels/${a.id}")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.result.labels.length()").value(1))
        json(delete(api(project, "posts/1/labels/${a.id}")), token, null).andExpect(status().isOk)
        assertEquals(setOf(c.id), reload(p).labels.map { it.id }.toSet())
        json(post(api(project, "posts/1/labels")), tokenOf(newUser("x")), """{"labels":[${a.id}]}""").andExpect(status().isForbidden)
        json(delete(api(project, "posts/1/labels/${c.id}")), tokenOf(newUser("y")), null).andExpect(status().isForbidden)
    }
}
