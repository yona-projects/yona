package dev.yona.web.api

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.sshkey.SshKeyAddition
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.sshkey.TestSshKey
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
 * SSH 키 REST(X-3 4단계, `decisions.md` D-66)를 검증한다: 내 키 목록과 한 건 조회, 등록, 삭제와 다른 사용자의 공개키 목록. 범위는 읽기 `read:ssh_key`,
 * 등록 `write:ssh_key`, 삭제 `admin:ssh_key`이다(GPG 키와 같다). 다른 사용자의 키 목록은 `read:user`이다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalSshKeyApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var keyService: SshKeyService

    private val mapper = JsonMapper.builder().build()
    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun newUser(scopes: Collection<String>): Pair<User, String> {
        val n = ++sequence
        val user = users.save(User(loginId = "sk$n-$stamp", name = "키$n", email = "sk$n-$stamp@example.com", state = UserState.ACTIVE))
        val token = "st$n$stamp"
        dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, user, token, scopes)
        return user to token
    }

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String? = null) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun body(name: String?, key: String) = mapper.writeValueAsString(linkedMapOf("name" to name, "publicKey" to key))

    private fun add(owner: User, key: TestSshKey, name: String = "k") = (keyService.add(owner, name, key.publicLine) as SshKeyAddition.Added).key

    @Test
    fun `토큰이 없으면 401이고 범위가 모자라면 403이다`() {
        json(get("/-_-api/v1/user/ssh_keys"), null).andExpect(status().isUnauthorized)
        val (_, token) = newUser(listOf("user"))
        json(get("/-_-api/v1/user/ssh_keys"), token).andExpect(status().isForbidden)
        json(post("/-_-api/v1/user/ssh_keys"), token, "{}").andExpect(status().isForbidden)
    }

    @Test
    fun `내 키 목록은 최근 등록한 순서이다`() {
        val (me, token) = newUser(listOf("read:ssh_key"))
        val first = add(me, TestSshKey.generate("a@x"), "첫째")
        val second = add(me, TestSshKey.generate("b@x"), "둘째")
        val keys = mapper.readTree(json(get("/-_-api/v1/user/ssh_keys"), token).andExpect(status().isOk).andReturn().response.contentAsString).get("keys")
        assertTrue(keys.toList().map { it.get("id").asLong(0L) }.let { it.indexOf(second.id!!) < it.indexOf(first.id!!) })
        val node = keys.toList().first { it.get("id").asLong(0L) == first.id!! }
        assertEquals("첫째", node.get("name").asString())
        assertEquals("ssh-ed25519", node.get("keyType").asString())
        assertEquals(first.fingerprint, node.get("fingerprint").asString())
        assertEquals(first.publicKey, node.get("publicKey").asString())
    }

    @Test
    fun `다른 사용자의 키는 목록에 없고 한 건 조회는 404이다`() {
        val (me, token) = newUser(listOf("read:ssh_key"))
        val (other, _) = newUser(emptyList())
        val theirs = add(other, TestSshKey.generate())
        json(get("/-_-api/v1/user/ssh_keys"), token).andExpect(status().isOk).andExpect(jsonPath("$.keys.length()").value(0))
        json(get("/-_-api/v1/user/ssh_keys/${theirs.id}"), token).andExpect(status().isNotFound)
        val mine = add(me, TestSshKey.generate())
        json(get("/-_-api/v1/user/ssh_keys/${mine.id}"), token).andExpect(status().isOk).andExpect(jsonPath("$.result.fingerprint").value(mine.fingerprint!!))
    }

    @Test
    fun `공개키를 등록하면 201과 만든 키이고 write 범위는 읽기도 포함한다`() {
        val (_, token) = newUser(listOf("write:ssh_key"))
        val source = TestSshKey.generate("me@laptop")
        json(post("/-_-api/v1/user/ssh_keys"), token, body("내 키", source.publicLine))
            .andExpect(status().isCreated).andExpect(jsonPath("$.result.name").value("내 키")).andExpect(jsonPath("$.result.fingerprint").value(source.fingerprint))
        json(get("/-_-api/v1/user/ssh_keys"), token).andExpect(status().isOk)
    }

    @Test
    fun `받을 수 없는 키는 400, 같은 키는 409, 본문이 없으면 400이다`() {
        val (_, token) = newUser(listOf("write:ssh_key"))
        json(post("/-_-api/v1/user/ssh_keys"), token, body("x", "not a key")).andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/ssh_keys"), token, "{}").andExpect(status().isBadRequest)
        json(post("/-_-api/v1/user/ssh_keys"), token, null).andExpect(status().isBadRequest)
        val source = TestSshKey.generate()
        json(post("/-_-api/v1/user/ssh_keys"), token, body("a", source.publicLine)).andExpect(status().isCreated)
        json(post("/-_-api/v1/user/ssh_keys"), token, body("b", source.publicLine)).andExpect(status().isConflict)
    }

    @Test
    fun `삭제는 admin 범위가 필요하고 성공하면 204이다`() {
        val (me, writeToken) = newUser(listOf("write:ssh_key"))
        val mine = add(me, TestSshKey.generate())
        json(delete("/-_-api/v1/user/ssh_keys/${mine.id}"), writeToken).andExpect(status().isForbidden)
        assertEquals(1, keyService.list(me).size)
        val (owner, ownerToken) = newUser(listOf("admin:ssh_key"))
        val key = add(owner, TestSshKey.generate())
        json(delete("/-_-api/v1/user/ssh_keys/${key.id}"), ownerToken).andExpect(status().isNoContent)
        assertEquals(0, keyService.list(owner).size)
        json(delete("/-_-api/v1/user/ssh_keys/${key.id}"), ownerToken).andExpect(status().isNotFound)
    }

    @Test
    fun `다른 사용자의 키는 지울 수 없다`() {
        val (me, _) = newUser(emptyList())
        val mine = add(me, TestSshKey.generate())
        val (_, otherToken) = newUser(listOf("admin:ssh_key"))
        json(delete("/-_-api/v1/user/ssh_keys/${mine.id}"), otherToken).andExpect(status().isNotFound)
        assertEquals(1, keyService.list(me).size)
    }

    @Test
    fun `다른 사용자의 공개키 목록을 read user 범위로 읽고 키 이름은 보이지 않는다`() {
        val (_, token) = newUser(listOf("read:user"))
        val (target, _) = newUser(emptyList())
        val key = add(target, TestSshKey.generate(), "비공개 이름")
        val node = mapper.readTree(json(get("/-_-api/v1/users/${target.loginId}/ssh_keys"), token).andExpect(status().isOk).andReturn().response.contentAsString).get("keys")[0]
        assertEquals(key.fingerprint, node.get("fingerprint").asString())
        assertEquals(key.publicKey, node.get("publicKey").asString())
        assertEquals(false, node.has("name"))
        json(get("/-_-api/v1/users/nobody-$stamp/ssh_keys"), token).andExpect(status().isNotFound)
    }
}
