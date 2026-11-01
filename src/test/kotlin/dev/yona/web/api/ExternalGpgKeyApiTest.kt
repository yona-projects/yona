package dev.yona.web.api

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.gpgkey.GpgKeyAddition
import dev.yona.domain.gpgkey.GpgKeyService
import dev.yona.domain.gpgkey.TestGpgKey
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GPG 키 REST(X-4 5단계, `decisions.md` D-65)를 검증한다: 내 키 목록과 한 건 조회, 등록, 삭제와 다른 사용자의 공개키 목록. 범위는 읽기 `read:gpg_key`,
 * 등록 `write:gpg_key`, 삭제 `admin:gpg_key`이다(GitHub와 같다). 다른 사용자의 키 목록은 `read:user`이다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalGpgKeyApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var keyService: GpgKeyService

    private val mapper = JsonMapper.builder().build()
    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun newUser(scopes: Collection<String>): Pair<User, String> {
        val n = ++sequence
        val user = users.save(User(loginId = "gk$n-$stamp", name = "키$n", email = "gk$n-$stamp@example.com", state = UserState.ACTIVE))
        val token = "gt$n$stamp"
        dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, user, token, scopes)
        return user to token
    }

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String? = null) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun body(name: String?, key: String) = mapper.writeValueAsString(linkedMapOf("name" to name, "publicKey" to key))

    private fun add(owner: User, key: TestGpgKey, name: String = "k") = (keyService.add(owner, name, key.publicArmored) as GpgKeyAddition.Added).key

    @Test
    fun `토큰이 없으면 401이고 범위가 모자라면 403이다`() {
        json(get("/-_-api/v1/user/gpg_keys"), null).andExpect(status().isUnauthorized)
        val (_, token) = newUser(listOf("user"))
        json(get("/-_-api/v1/user/gpg_keys"), token).andExpect(status().isForbidden)
        json(post("/-_-api/v1/user/gpg_keys"), token, "{}").andExpect(status().isForbidden)
    }

    @Test
    fun `내 키 목록은 최근 등록한 순서이고 하위 키와 이메일을 담는다`() {
        val (me, token) = newUser(listOf("read:gpg_key"))
        val first = add(me, TestGpgKey.generate(userId = "Me <me@example.com>", signingSubkey = true), "첫째")
        val second = add(me, TestGpgKey.generate(userId = "Me <me2@example.com>"), "둘째")
        val response = json(get("/-_-api/v1/user/gpg_keys"), token).andExpect(status().isOk).andReturn().response.contentAsString
        val keys = mapper.readTree(response).get("keys")
        assertEquals(listOf(second.id!!, first.id!!), keys.toList().map { it.get("id").asLong(0L) })
        val firstNode = keys[1]
        assertEquals("첫째", firstNode.get("name").asString())
        assertEquals(first.fingerprint, firstNode.get("fingerprint").asString())
        assertEquals(first.keyId, firstNode.get("keyId").asString())
        assertEquals(listOf("me@example.com"), firstNode.get("emails").toList().map { it.asString("") })
        assertEquals(true, firstNode.get("canSign").asBoolean())
        assertEquals(1, firstNode.get("subkeys").size())
        assertTrue(firstNode.get("subkeys")[0].get("canSign").asBoolean())
    }

    @Test
    fun `다른 사용자의 키는 목록에 없고 한 건 조회는 404이다`() {
        val (me, token) = newUser(listOf("read:gpg_key"))
        val (other, _) = newUser(emptyList())
        val theirs = add(other, TestGpgKey.generate())
        json(get("/-_-api/v1/user/gpg_keys"), token).andExpect(status().isOk).andExpect(jsonPath("$.keys.length()").value(0))
        json(get("/-_-api/v1/user/gpg_keys/${theirs.id}"), token).andExpect(status().isNotFound)
        val mine = add(me, TestGpgKey.generate())
        json(get("/-_-api/v1/user/gpg_keys/${mine.id}"), token).andExpect(status().isOk).andExpect(jsonPath("$.result.fingerprint").value(mine.fingerprint!!))
    }

    @Test
    fun `공개키를 등록하면 201과 만든 키이다`() {
        val (_, token) = newUser(listOf("write:gpg_key"))
        val source = TestGpgKey.generate(userId = "Me <me@example.com>")
        json(post("/-_-api/v1/user/gpg_keys"), token, body("내 키", source.publicArmored))
            .andExpect(status().isCreated).andExpect(jsonPath("$.result.name").value("내 키")).andExpect(jsonPath("$.result.fingerprint").value(source.fingerprint))
            .andExpect(jsonPath("$.result.emails[0]").value("me@example.com"))
    }

    @Test
    fun `write 범위는 read 범위를 포함해 목록도 읽는다`() {
        val (_, token) = newUser(listOf("write:gpg_key"))
        json(get("/-_-api/v1/user/gpg_keys"), token).andExpect(status().isOk)
    }

    @Test
    fun `받을 수 없는 키는 400, 같은 키는 409, 본문이 없으면 400이다`() {
        val (_, token) = newUser(listOf("write:gpg_key"))
        json(post("/-_-api/v1/user/gpg_keys"), token, body("x", "not a key")).andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/gpg_keys"), token, "{}").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/gpg_keys"), token, null).andExpect(status().isBadRequest)
        val source = TestGpgKey.generate()
        json(post("/-_-api/v1/user/gpg_keys"), token, body("a", source.publicArmored)).andExpect(status().isCreated)
        json(post("/-_-api/v1/user/gpg_keys"), token, body("b", source.publicArmored)).andExpect(status().isConflict)
    }

    @Test
    fun `삭제는 admin 범위가 필요하고 성공하면 204이다`() {
        val (me, writeToken) = newUser(listOf("write:gpg_key"))
        val mine = add(me, TestGpgKey.generate())
        json(delete("/-_-api/v1/user/gpg_keys/${mine.id}"), writeToken).andExpect(status().isForbidden)
        assertEquals(1, keyService.list(me).size)
        val (owner, ownerToken) = newUser(listOf("admin:gpg_key"))
        val key = add(owner, TestGpgKey.generate())
        json(delete("/-_-api/v1/user/gpg_keys/${key.id}"), ownerToken).andExpect(status().isNoContent)
        assertEquals(0, keyService.list(owner).size)
        json(delete("/-_-api/v1/user/gpg_keys/${key.id}"), ownerToken).andExpect(status().isNotFound)
    }

    @Test
    fun `다른 사용자의 키는 지울 수 없다`() {
        val (me, _) = newUser(emptyList())
        val mine = add(me, TestGpgKey.generate())
        val (_, otherToken) = newUser(listOf("admin:gpg_key"))
        json(delete("/-_-api/v1/user/gpg_keys/${mine.id}"), otherToken).andExpect(status().isNotFound)
        assertEquals(1, keyService.list(me).size)
    }

    @Test
    fun `다른 사용자의 공개키 목록을 read user 범위로 읽는다`() {
        val (viewer, token) = newUser(listOf("read:user"))
        val (target, _) = newUser(emptyList())
        val key = add(target, TestGpgKey.generate(userId = "T <t@example.com>"), "비공개 이름")
        val response = json(get("/-_-api/v1/users/${target.loginId}/gpg_keys"), token).andExpect(status().isOk).andReturn().response.contentAsString
        val node = mapper.readTree(response).get("keys")[0]
        assertEquals(key.fingerprint, node.get("fingerprint").asString())
        assertEquals(false, node.has("name"), "다른 사용자의 키 이름은 보이지 않는다(GitHub처럼 공개키 정보만)")
        json(get("/-_-api/v1/users/nobody-$stamp/gpg_keys"), token).andExpect(status().isNotFound)
        assertTrue(viewer.id != null)
    }
}
