package dev.yona.web.ssh

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.sshkey.TestSshKey
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.ssh.SshRelayTokens
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 시스템 sshd가 부르는 내부 인증 주소(X-3b, `decisions.md` D-67)를 검증한다. `AuthorizedKeysCommand` 훅이 접속자의 공개키를 보내면 등록된 사용자 키나 배포 키인지 알려 주고,
 * 맞으면 중계 소켓에 제시할 인증 표를 돌려준다. 루프백 주소와 공유 비밀 값을 모두 요구한다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-ssh-internal", "yona.ssh.relay.enabled=true", "yona.ssh.relay.internal-secret=test-internal-secret"])
class SshInternalControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var sshKeys: SshKeyService
    @Autowired private lateinit var deployKeys: DeployKeyService
    @Autowired private lateinit var tokens: SshRelayTokens

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE) = users.save(User(loginId = "si${++sequence}", name = "si$sequence", email = "si$sequence@example.com", state = state))

    private fun authenticate(publicKey: String?, secret: String? = "test-internal-secret", remote: String = "127.0.0.1", body: String? = null) =
        mockMvc.perform(
            post("/internal/ssh/authenticate").contentType(MediaType.APPLICATION_JSON).with { it.remoteAddr = remote; it }
                .apply { secret?.let { header("X-Yona-Internal-Secret", it) } }
                .content(body ?: """{"publicKey": ${if (publicKey == null) "null" else "\"" + publicKey + "\""}}""")
        )

    @Test
    fun `등록한 사용자 키는 사용자 인증 표를 돌려준다`() {
        val u = user(); val key = TestSshKey.generate()
        sshKeys.add(u, "k", key.publicLine)
        val principal = tokens.verify(
            tools.jackson.databind.json.JsonMapper.builder().build().readTree(authenticate(key.publicLine.substringBeforeLast(" ")).andExpect(status().isOk).andReturn().response.contentAsString).get("principal").asString("")
        )
        assertEquals(u.id, principal?.userId)
    }

    @Test
    fun `등록한 배포 키는 배포 키 인증 표를 돌려준다`() {
        val p = projects.save(Project(owner = "sio${++sequence}", name = "sip$sequence", vcs = "GIT")); val key = TestSshKey.generate()
        deployKeys.add(p, "d", key.publicLine, false)
        val token = tools.jackson.databind.json.JsonMapper.builder().build().readTree(authenticate(key.publicLine).andExpect(status().isOk).andReturn().response.contentAsString).get("principal").asString("")
        assertNotNull(tokens.verify(token)?.deployKeyId)
    }

    @Test
    fun `등록하지 않은 키와 잠긴 사용자의 키는 404이다`() {
        authenticate(TestSshKey.generate().publicLine).andExpect(status().isNotFound)
        val locked = user(UserState.LOCKED); val key = TestSshKey.generate()
        sshKeys.add(locked, "k", key.publicLine)
        authenticate(key.publicLine).andExpect(status().isNotFound)
    }

    @Test
    fun `공유 비밀 값이 없거나 틀리면 403이다`() {
        val u = user(); val key = TestSshKey.generate(); sshKeys.add(u, "k", key.publicLine)
        authenticate(key.publicLine, secret = null).andExpect(status().isForbidden)
        authenticate(key.publicLine, secret = "wrong").andExpect(status().isForbidden)
        authenticate(key.publicLine, secret = "").andExpect(status().isForbidden)
    }

    @Test
    fun `루프백이 아닌 주소에서 온 요청은 비밀 값이 맞아도 403이다`() {
        val u = user(); val key = TestSshKey.generate(); sshKeys.add(u, "k", key.publicLine)
        authenticate(key.publicLine, remote = "203.0.113.9").andExpect(status().isForbidden)
        authenticate(key.publicLine, remote = "::1").andExpect(status().isOk)
    }

    @Test
    fun `공개키 형식이 아니거나 본문이 틀리면 400이다`() {
        authenticate("not a key").andExpect(status().isBadRequest)
        authenticate(null).andExpect(status().isBadRequest)
        authenticate(null, body = "not json").andExpect(status().isBadRequest)
        authenticate(null, body = "{}").andExpect(status().isBadRequest)
    }

    @Test
    fun `응답에는 표만 있고 사용자 정보가 없다`() {
        val u = user(); val key = TestSshKey.generate(); sshKeys.add(u, "k", key.publicLine)
        authenticate(key.publicLine).andExpect(jsonPath("$.principal").isString).andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.loginId").doesNotExist())
    }
}
