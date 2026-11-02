package dev.yona.web.lfs

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.lfs.LfsStorage
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Git LFS의 batch와 객체 전송(basic) API(X-8 단계 1, `decisions.md` D-79)를 검증한다. 권한은 git HTTP와 같고(개인용 토큰, 읽기 `repo`나 `read:repo`, 쓰기 `repo`,
 * 공개 프로젝트의 익명 내려받기), 주소는 `프로젝트.git`과 `프로젝트` 둘 다 받는다. 올라온 내용의 해시를 확인하고 한도를 넘으면 거절한다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-lfs-api"])
class LfsApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var apiTokens: ApiTokenService
    @Autowired private lateinit var storage: LfsStorage
    @Autowired private lateinit var settings: SiteSettingService

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000
    private val lfsJson = MediaType.parseMediaType("application/vnd.git-lfs+json")

    @BeforeEach
    fun defaults() { settings.setLfsEnabled(true); settings.setLfsMaxObjectSize(SiteSettingService.DEFAULT_LFS_MAX_OBJECT_SIZE); settings.setLfsProjectQuota(0) }

    @AfterEach
    fun clean() { File("build/tmp/it-lfs-api").deleteRecursively() }

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "lf$k-$stamp", name = "lf$k", email = "lf$k-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(scope: ProjectScope = ProjectScope.PRIVATE, vcs: String = "GIT", member: User? = null): Project {
        val k = ++n
        val p = projects.save(Project(owner = "lo$stamp", name = "lp$k", vcs = vcs, projectScope = scope, createdDate = java.util.Date()))
        member?.let { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(RoleType.MEMBER.id).get())) }
        return p
    }

    private fun basic(user: User, scopes: Set<String> = setOf("repo")): String {
        val token = (apiTokens.create(user, "lfs-${++n}", scopes, null) as TokenCreation.Created).value
        return "Basic " + Base64.getEncoder().encodeToString("${user.loginId}:$token".toByteArray())
    }

    private fun oidOf(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun base(p: Project, suffix: String = ".git") = "/${p.owner}/${p.name}$suffix/info/lfs"

    private fun batch(p: Project, operation: String, objects: List<Pair<String, Long>>, auth: String? = null, suffix: String = ".git", transfers: String? = null): MockHttpServletRequestBuilder {
        val list = objects.joinToString(",") { """{"oid":"${it.first}","size":${it.second}}""" }
        val body = """{"operation":"$operation",${transfers?.let { "\"transfers\":$it," } ?: ""}"objects":[$list]}"""
        return post("${base(p, suffix)}/objects/batch").contentType(lfsJson).accept(lfsJson).content(body).apply { auth?.let { header(HttpHeaders.AUTHORIZATION, it) } }
    }

    private fun putObject(p: Project, oid: String, data: ByteArray, auth: String?) =
        put("${base(p)}/objects/$oid").contentType(MediaType.APPLICATION_OCTET_STREAM).content(data).apply { auth?.let { header(HttpHeaders.AUTHORIZATION, it) } }

    // --- batch: 내려받기 ---

    @Test
    fun `내려받기 batch는 있는 객체에 download 동작을 주고 없는 객체에는 404 오류를 준다`() {
        val member = user(); val p = project(member = member); val data = "내용".toByteArray(); val oid = oidOf(data)
        storage.store(p, oid, data.inputStream(), Long.MAX_VALUE); val missing = "b".repeat(64)
        mockMvc.perform(batch(p, "download", listOf(oid to data.size.toLong(), missing to 5), basic(member)))
            .andExpect(status().isOk).andExpect(content().contentTypeCompatibleWith(lfsJson))
            .andExpect(jsonPath("$.transfer").value("basic")).andExpect(jsonPath("$.hash_algo").value("sha256"))
            .andExpect(jsonPath("$.objects[0].oid").value(oid)).andExpect(jsonPath("$.objects[0].size").value(data.size))
            .andExpect(jsonPath("$.objects[0].actions.download.href").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("http"), org.hamcrest.Matchers.endsWith("/${p.owner}/${p.name}.git/info/lfs/objects/$oid"))))
            .andExpect(jsonPath("$.objects[1].oid").value(missing)).andExpect(jsonPath("$.objects[1].error.code").value(404)).andExpect(jsonPath("$.objects[1].actions").doesNotExist())
    }

    @Test
    fun `주소는 프로젝트 점 git과 프로젝트 둘 다 받는다`() {
        val member = user(); val p = project(member = member); val oid = "a".repeat(64)
        for (suffix in listOf(".git", "")) mockMvc.perform(batch(p, "download", listOf(oid to 1), basic(member), suffix)).andExpect(status().isOk)
    }

    // --- batch: 올리기 ---

    @Test
    fun `올리기 batch는 없는 객체에 upload와 verify 동작을 주고 이미 있는 객체에는 동작을 주지 않는다`() {
        val member = user(); val p = project(member = member); val data = "내용".toByteArray(); val oid = oidOf(data); val fresh = "c".repeat(64)
        storage.store(p, oid, data.inputStream(), Long.MAX_VALUE)
        mockMvc.perform(batch(p, "upload", listOf(oid to data.size.toLong(), fresh to 9), basic(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.objects[0].actions").doesNotExist()).andExpect(jsonPath("$.objects[0].error").doesNotExist())
            .andExpect(jsonPath("$.objects[1].actions.upload.href").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("http"), org.hamcrest.Matchers.endsWith("/${p.owner}/${p.name}.git/info/lfs/objects/$fresh"))))
            .andExpect(jsonPath("$.objects[1].actions.verify.href").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("http"), org.hamcrest.Matchers.endsWith("/${p.owner}/${p.name}.git/info/lfs/verify"))))
    }

    @Test
    fun `객체 하나의 최대 크기를 넘으면 그 객체만 413 오류이다`() {
        val member = user(); val p = project(member = member); settings.setLfsMaxObjectSize(100)
        mockMvc.perform(batch(p, "upload", listOf("a".repeat(64) to 100, "b".repeat(64) to 101), basic(member)))
            .andExpect(jsonPath("$.objects[0].actions.upload").exists()).andExpect(jsonPath("$.objects[1].error.code").value(413)).andExpect(jsonPath("$.objects[1].actions").doesNotExist())
    }

    @Test
    fun `프로젝트 용량 한도를 넘는 올리기는 413 오류이다`() {
        val member = user(); val p = project(member = member); settings.setLfsProjectQuota(150)
        val data = ByteArray(100) { 1 }; storage.store(p, oidOf(data), data.inputStream(), Long.MAX_VALUE)
        mockMvc.perform(batch(p, "upload", listOf("a".repeat(64) to 60), basic(member))).andExpect(jsonPath("$.objects[0].error.code").value(413))
        mockMvc.perform(batch(p, "upload", listOf("a".repeat(64) to 50), basic(member))).andExpect(jsonPath("$.objects[0].actions.upload").exists())
    }

    @Test
    fun `올바르지 않은 oid나 크기는 그 객체만 422 오류이고 잘못된 요청은 422이다`() {
        val member = user(); val p = project(member = member)
        mockMvc.perform(batch(p, "download", listOf("../etc" to 1, "a".repeat(64) to -1), basic(member)))
            .andExpect(status().isOk).andExpect(jsonPath("$.objects[0].error.code").value(422)).andExpect(jsonPath("$.objects[1].error.code").value(422))
        mockMvc.perform(batch(p, "delete", listOf("a".repeat(64) to 1), basic(member))).andExpect(status().isUnprocessableContent)
        mockMvc.perform(post("${base(p)}/objects/batch").contentType(lfsJson).content("not json").header(HttpHeaders.AUTHORIZATION, basic(member))).andExpect(status().isUnprocessableContent)
        mockMvc.perform(batch(p, "download", listOf("a".repeat(64) to 1), basic(member), transfers = """["tus"]""")).andExpect(status().isUnprocessableContent)
        mockMvc.perform(batch(p, "download", listOf("a".repeat(64) to 1), basic(member), transfers = """["tus","basic"]""")).andExpect(status().isOk)
    }

    // --- 권한 ---

    @Test
    fun `비공개 프로젝트는 인증이 없으면 401과 인증 요청이고 구성원이 아니면 403이다`() {
        val member = user(); val outsider = user(); val p = project(member = member); val item = listOf("a".repeat(64) to 1L)
        mockMvc.perform(batch(p, "download", item)).andExpect(status().isUnauthorized).andExpect(header().string("WWW-Authenticate", "Basic realm=\"Yobi\""))
            .andExpect(content().contentTypeCompatibleWith(lfsJson)).andExpect(jsonPath("$.message").exists())
        mockMvc.perform(batch(p, "download", item, basic(outsider))).andExpect(status().isForbidden)
        mockMvc.perform(batch(p, "download", item, basic(member))).andExpect(status().isOk)
    }

    @Test
    fun `공개 프로젝트는 익명도 내려받을 수 있지만 올릴 수는 없다`() {
        val member = user(); val p = project(ProjectScope.PUBLIC, member = member); val item = listOf("a".repeat(64) to 1L)
        mockMvc.perform(batch(p, "download", item)).andExpect(status().isOk)
        mockMvc.perform(batch(p, "upload", item)).andExpect(status().isUnauthorized)
        mockMvc.perform(batch(p, "upload", item, basic(user()))).andExpect(status().isForbidden)
    }

    @Test
    fun `토큰 범위가 읽기만이면 내려받기만 되고 올리기는 403이다`() {
        val member = user(); val p = project(member = member); val item = listOf("a".repeat(64) to 1L); val readOnly = basic(member, setOf("read:repo"))
        mockMvc.perform(batch(p, "download", item, readOnly)).andExpect(status().isOk)
        mockMvc.perform(batch(p, "upload", item, readOnly)).andExpect(status().isForbidden)
        mockMvc.perform(put("${base(p)}/objects/${"a".repeat(64)}").content(byteArrayOf(1)).header(HttpHeaders.AUTHORIZATION, readOnly)).andExpect(status().isForbidden)
    }

    @Test
    fun `비밀번호는 받지 않고 토큰을 쓰라고 안내한다`() {
        val member = user(); val p = project(member = member)
        val password = "Basic " + Base64.getEncoder().encodeToString("${member.loginId}:1234".toByteArray())
        mockMvc.perform(batch(p, "download", listOf("a".repeat(64) to 1L), password)).andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("personal access token")))
    }

    @Test
    fun `없는 프로젝트와 LFS를 지원하지 않는 저장소와 꺼진 사이트 설정은 404이다`() {
        val member = user(); val svn = project(vcs = "Subversion", member = member); val p = project(member = member)
        mockMvc.perform(post("/nobody/none.git/info/lfs/objects/batch").contentType(lfsJson).content("{}").header(HttpHeaders.AUTHORIZATION, basic(member))).andExpect(status().isNotFound)
        mockMvc.perform(batch(svn, "download", listOf("a".repeat(64) to 1L), basic(member))).andExpect(status().isNotFound)
        settings.setLfsEnabled(false)
        mockMvc.perform(batch(p, "download", listOf("a".repeat(64) to 1L), basic(member))).andExpect(status().isNotFound)
    }

    // --- 객체 전송 ---

    @Test
    fun `올린 객체는 그대로 내려받고 이어받기와 HEAD를 지원한다`() {
        val member = user(); val p = project(member = member); val auth = basic(member); val data = "0123456789".toByteArray(); val oid = oidOf(data)
        mockMvc.perform(putObject(p, oid, data, auth)).andExpect(status().isOk)
        assertTrue(storage.exists(p, oid))
        mockMvc.perform(get("${base(p)}/objects/$oid").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk).andExpect(content().bytes(data))
            .andExpect(header().string("Content-Length", "10")).andExpect(header().string("Content-Type", "application/octet-stream"))
        mockMvc.perform(get("${base(p)}/objects/$oid").header(HttpHeaders.AUTHORIZATION, auth).header(HttpHeaders.RANGE, "bytes=3-5")).andExpect(status().isPartialContent).andExpect(content().bytes("345".toByteArray()))
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("${base(p)}/objects/$oid").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk).andExpect(header().string("Content-Length", "10"))
        mockMvc.perform(putObject(p, oid, data, auth)).andExpect(status().isOk)
    }

    @Test
    fun `내용의 해시가 oid와 다르면 422이고 저장하지 않으며 한도를 넘으면 413이다`() {
        val member = user(); val p = project(member = member); val auth = basic(member); val data = "abc".toByteArray(); val oid = oidOf(data)
        mockMvc.perform(putObject(p, oid, "abd".toByteArray(), auth)).andExpect(status().isUnprocessableContent)
        assertFalse(storage.exists(p, oid))
        settings.setLfsMaxObjectSize(2)
        mockMvc.perform(putObject(p, oid, data, auth)).andExpect(status().isPayloadTooLarge)
        mockMvc.perform(putObject(p, "zz", data, auth)).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `없는 객체는 404이고 올리기는 쓰기 권한이 필요하다`() {
        val member = user(); val reader = user(); val p = project(ProjectScope.PUBLIC, member = member)
        mockMvc.perform(get("${base(p)}/objects/${"d".repeat(64)}")).andExpect(status().isNotFound)
        mockMvc.perform(putObject(p, oidOf("x".toByteArray()), "x".toByteArray(), null)).andExpect(status().isUnauthorized)
        mockMvc.perform(putObject(p, oidOf("x".toByteArray()), "x".toByteArray(), basic(reader))).andExpect(status().isForbidden)
    }

    @Test
    fun `verify는 올린 객체의 크기가 맞으면 200이고 아니면 404나 422이다`() {
        val member = user(); val p = project(member = member); val auth = basic(member); val data = "verify".toByteArray(); val oid = oidOf(data)
        fun verify(oid: String, size: Long) = post("${base(p)}/verify").contentType(lfsJson).content("""{"oid":"$oid","size":$size}""").header(HttpHeaders.AUTHORIZATION, auth)
        mockMvc.perform(verify(oid, data.size.toLong())).andExpect(status().isNotFound)
        storage.store(p, oid, data.inputStream(), Long.MAX_VALUE)
        mockMvc.perform(verify(oid, data.size.toLong())).andExpect(status().isOk)
        mockMvc.perform(verify(oid, data.size + 1L)).andExpect(status().isUnprocessableContent)
    }
}
