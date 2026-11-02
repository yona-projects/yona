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
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
 * Git LFS의 `tus`(이어올리기) 전송(X-8 단계 7, `decisions.md` D-79 1번)을 검증한다. tus 1.0.0의 핵심 규격과 `creation` 확장을 따른다: `POST`로 만들고(`Upload-Length`), `HEAD`로 지금까지 받은 위치(`Upload-Offset`)를 알려 주며, `PATCH`로 그 위치부터 이어 보낸다. 다 받으면 SHA-256을 확인해 객체로 옮긴다.
 * batch는 클라이언트가 `tus`를 고를 수 있다고 하면 올리기에만 `tus`를 고른다. 권한과 한도는 기본 전송과 같다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-lfs-tus"])
class LfsTusApiTest : AbstractIntegrationTest() {

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
    private val offsetStream = MediaType.parseMediaType("application/offset+octet-stream")

    @BeforeEach
    fun defaults() { settings.setLfsEnabled(true); settings.setLfsMaxObjectSize(0); settings.setLfsProjectQuota(0) }

    @AfterEach
    fun clean() { File("build/tmp/it-lfs-tus").deleteRecursively() }

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "tu$k-$stamp", name = "tu$k", email = "tu$k-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(): Project = projects.save(Project(owner = "tuo$stamp", name = "tup${++n}", vcs = "GIT", projectScope = ProjectScope.PRIVATE, createdDate = java.util.Date()))

    private fun member(p: Project): User = user().also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(RoleType.MEMBER.id).get())) }

    private fun basic(user: User, scopes: Set<String> = setOf("repo")): String {
        val token = (apiTokens.create(user, "tus-${++n}", scopes, null) as TokenCreation.Created).value
        return "Basic " + Base64.getEncoder().encodeToString("${user.loginId}:$token".toByteArray())
    }

    private fun oidOf(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun lfs(p: Project) = "/${p.owner}/${p.name}.git/info/lfs"
    private fun tus(p: Project, oid: String) = "${lfs(p)}/tus/$oid"
    private fun meta(oid: String) = "oid " + Base64.getEncoder().encodeToString(oid.toByteArray())

    private fun create(p: Project, oid: String, length: Long, auth: String) =
        post(tus(p, oid)).header("Tus-Resumable", "1.0.0").header("Upload-Length", length.toString()).header("Upload-Metadata", meta(oid)).header(HttpHeaders.AUTHORIZATION, auth)

    private fun send(p: Project, oid: String, offset: Long, bytes: ByteArray, auth: String) =
        patch(tus(p, oid)).header("Tus-Resumable", "1.0.0").header("Upload-Offset", offset.toString()).contentType(offsetStream).content(bytes).header(HttpHeaders.AUTHORIZATION, auth)

    private fun batch(p: Project, operation: String, transfers: String?, auth: String, oid: String = "a".repeat(64)) =
        post("${lfs(p)}/objects/batch").contentType(lfsJson).accept(lfsJson).header(HttpHeaders.AUTHORIZATION, auth)
            .content("""{"operation":"$operation",${transfers?.let { "\"transfers\":$it," } ?: ""}"objects":[{"oid":"$oid","size":10}]}""")

    @Test
    fun `batch는 클라이언트가 tus를 고를 수 있다고 하면 올리기에만 tus를 고르고 다운로드와 기본 요청은 basic이다`() {
        val p = project(); val u = member(p); val auth = basic(u)
        mockMvc.perform(batch(p, "upload", """["tus","basic"]""", auth)).andExpect(status().isOk).andExpect(jsonPath("$.transfer").value("tus"))
            .andExpect(jsonPath("$.objects[0].actions.upload.href").value(org.hamcrest.Matchers.endsWith("${lfs(p)}/tus/${"a".repeat(64)}")))
        mockMvc.perform(batch(p, "download", """["tus","basic"]""", auth)).andExpect(jsonPath("$.transfer").value("basic"))
        mockMvc.perform(batch(p, "upload", """["basic"]""", auth)).andExpect(jsonPath("$.transfer").value("basic"))
        mockMvc.perform(batch(p, "upload", null, auth)).andExpect(jsonPath("$.transfer").value("basic"))
        mockMvc.perform(batch(p, "upload", """["tus"]""", auth)).andExpect(jsonPath("$.transfer").value("tus"))
        // 실제 클라이언트는 basic을 앞에 두고 tus를 뒤에 덧붙인다(`lfs.tustransfers`를 켰을 때만). 그래도 tus를 골라야 한다
        mockMvc.perform(batch(p, "upload", """["basic","tus","ssh","lfs-standalone-file"]""", auth)).andExpect(jsonPath("$.transfer").value("tus"))
        mockMvc.perform(batch(p, "download", """["tus"]""", auth)).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `tus batch는 올릴 객체의 업로드를 미리 만들어 곧바로 HEAD가 위치 0을 알려 준다 - 실제 클라이언트는 따로 만들지 않는다`() {
        val p = project(); val auth = basic(member(p)); val oid = "d".repeat(64)
        mockMvc.perform(batch(p, "upload", """["basic","tus"]""", auth, oid)).andExpect(jsonPath("$.transfer").value("tus"))
        mockMvc.perform(head(tus(p, oid)).header("Tus-Resumable", "1.0.0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk).andExpect(header().string("Upload-Offset", "0")).andExpect(header().string("Upload-Length", "10"))
    }

    @Test
    fun `Tus-Resumable 헤더가 없거나 다르면 412와 지원 버전을 알린다`() {
        val p = project(); val auth = basic(member(p)); val oid = "a".repeat(64)
        mockMvc.perform(post(tus(p, oid)).header("Upload-Length", "10").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isPreconditionFailed).andExpect(header().string("Tus-Version", "1.0.0"))
        mockMvc.perform(head(tus(p, oid)).header("Tus-Resumable", "0.2.0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isPreconditionFailed)
    }

    @Test
    fun `만들고 조각으로 나눠 보내면 객체가 저장되고 HEAD가 받은 위치를 알려 준다`() {
        val p = project(); val auth = basic(member(p)); val data = "0123456789abcdefghij".toByteArray(); val oid = oidOf(data)
        mockMvc.perform(head(tus(p, oid)).header("Tus-Resumable", "1.0.0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound)
        mockMvc.perform(create(p, oid, data.size.toLong(), auth)).andExpect(status().isCreated).andExpect(header().string("Tus-Resumable", "1.0.0")).andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/tus/$oid")))
        mockMvc.perform(head(tus(p, oid)).header("Tus-Resumable", "1.0.0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk)
            .andExpect(header().string("Upload-Offset", "0")).andExpect(header().string("Upload-Length", "20")).andExpect(header().string("Cache-Control", "no-store"))
        mockMvc.perform(send(p, oid, 0, data.copyOfRange(0, 8), auth)).andExpect(status().isNoContent).andExpect(header().string("Upload-Offset", "8"))
        assertFalse(storage.exists(p, oid))
        mockMvc.perform(head(tus(p, oid)).header("Tus-Resumable", "1.0.0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(header().string("Upload-Offset", "8"))
        mockMvc.perform(send(p, oid, 8, data.copyOfRange(8, 20), auth)).andExpect(status().isNoContent).andExpect(header().string("Upload-Offset", "20"))
        assertTrue(storage.exists(p, oid)); assertEquals(20L, storage.sizeOf(p, oid))
        mockMvc.perform(head(tus(p, oid)).header("Tus-Resumable", "1.0.0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk).andExpect(header().string("Upload-Offset", "20"))
    }

    @Test
    fun `위치가 맞지 않으면 409이고 내용 종류가 다르면 415이며 길이를 넘으면 413이다`() {
        val p = project(); val auth = basic(member(p)); val data = "0123456789".toByteArray(); val oid = oidOf(data)
        mockMvc.perform(create(p, oid, 10, auth)).andExpect(status().isCreated)
        mockMvc.perform(send(p, oid, 3, data, auth)).andExpect(status().isConflict)
        mockMvc.perform(patch(tus(p, oid)).header("Tus-Resumable", "1.0.0").header("Upload-Offset", "0").contentType(MediaType.APPLICATION_OCTET_STREAM).content(data).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isUnsupportedMediaType)
        mockMvc.perform(send(p, oid, 0, "0123456789XX".toByteArray(), auth)).andExpect(status().isPayloadTooLarge)
        mockMvc.perform(send(p, "b".repeat(64), 0, data, auth)).andExpect(status().isNotFound)
    }

    @Test
    fun `다 받은 내용의 해시가 이름과 다르면 422이고 받은 조각을 버린다`() {
        val p = project(); val auth = basic(member(p)); val oid = oidOf("원래".toByteArray()); val wrong = "다른내".toByteArray()
        mockMvc.perform(create(p, oid, wrong.size.toLong(), auth)).andExpect(status().isCreated)
        mockMvc.perform(send(p, oid, 0, wrong, auth)).andExpect(status().isUnprocessableContent)
        assertFalse(storage.exists(p, oid))
        mockMvc.perform(head(tus(p, oid)).header("Tus-Resumable", "1.0.0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound)
    }

    @Test
    fun `이미 있는 객체는 만들기가 성공하고 HEAD가 다 받았다고 알린다`() {
        val p = project(); val auth = basic(member(p)); val data = "exists".toByteArray(); val oid = oidOf(data)
        storage.store(p, oid, data.inputStream(), Long.MAX_VALUE)
        mockMvc.perform(create(p, oid, data.size.toLong(), auth)).andExpect(status().isCreated)
        mockMvc.perform(head(tus(p, oid)).header("Tus-Resumable", "1.0.0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(header().string("Upload-Offset", data.size.toString())).andExpect(header().string("Upload-Length", data.size.toString()))
    }

    @Test
    fun `최대 크기와 용량 한도를 넘는 만들기는 413이다`() {
        val p = project(); val auth = basic(member(p)); settings.setLfsMaxObjectSize(100)
        mockMvc.perform(create(p, "a".repeat(64), 101, auth)).andExpect(status().isPayloadTooLarge)
        mockMvc.perform(create(p, "a".repeat(64), 100, auth)).andExpect(status().isCreated)
        settings.setLfsMaxObjectSize(0); settings.setLfsProjectQuota(150)
        val data = ByteArray(100) { 1 }; storage.store(p, oidOf(data), data.inputStream(), Long.MAX_VALUE)
        mockMvc.perform(create(p, "c".repeat(64), 60, auth)).andExpect(status().isPayloadTooLarge)
        mockMvc.perform(post(tus(p, "c".repeat(64))).header("Tus-Resumable", "1.0.0").header("Upload-Length", "-1").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isBadRequest)
    }

    @Test
    fun `권한은 기본 전송의 올리기와 같다`() {
        val p = project(); val m = member(p); val outsider = user(); val oid = "a".repeat(64)
        mockMvc.perform(create(p, oid, 5, "Basic " + Base64.getEncoder().encodeToString("x:y".toByteArray()))).andExpect(status().isUnauthorized)
        mockMvc.perform(create(p, oid, 5, basic(outsider))).andExpect(status().isForbidden)
        mockMvc.perform(create(p, oid, 5, basic(m, setOf("read:repo")))).andExpect(status().isForbidden)
        mockMvc.perform(send(p, oid, 0, byteArrayOf(1), basic(outsider))).andExpect(status().isForbidden)
    }
}
