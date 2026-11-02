package dev.yona.ssh

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 시스템 sshd 연동의 인증 표(X-3b, `decisions.md` D-67)를 검증한다. 훅이 키를 인증하면 서버가 이 표를 돌려주고, 표는 `authorized_keys`의 강제 명령에 실려 세션이 시작될 때 중계 소켓에
 * 제시된다. 소켓에 연결할 수 있는 다른 계정이 사용자나 배포 키를 사칭하지 못하게 공유 비밀 값으로 서명하고 유효 시간을 둔다.
 */
class SshRelayTokensTest {

    private val now = Instant.parse("2026-11-02T00:00:00Z")
    private fun tokens(secret: String = "s3cret", at: Instant = now) = SshRelayTokens({ secret }, Clock.fixed(at, ZoneOffset.UTC))

    @Test
    fun `사용자 키와 배포 키의 표를 만들고 다시 읽는다`() {
        val user = tokens().issue(SshPrincipal("SHA256:u", userId = 5, deployKeyId = null))
        assertEquals(SshPrincipal(SshRelayTokens.FINGERPRINT, 5, null), tokens().verify(user))
        val deploy = tokens().issue(SshPrincipal("SHA256:d", userId = null, deployKeyId = 9))
        assertEquals(SshPrincipal(SshRelayTokens.FINGERPRINT, null, 9), tokens().verify(deploy))
    }

    @Test
    fun `내용을 바꾸거나 다른 비밀 값으로 서명했거나 모양이 틀린 표는 받지 않는다`() {
        val token = tokens().issue(SshPrincipal("f", 5, null))
        assertNull(tokens().verify(token.replace(".5.", ".6.")), "번호를 바꾸면 서명이 맞지 않는다")
        assertNull(tokens("other").verify(token), "다른 비밀 값")
        assertNull(tokens().verify(token.dropLast(2)), "서명이 잘렸다")
        listOf("", "garbage", "v1..", "v1.user.x.1.sig", "v2.user.5.99999999999.sig", "sshkey:5").forEach { assertNull(tokens().verify(it), it) }
    }

    @Test
    fun `유효 시간이 지난 표는 받지 않는다`() {
        val token = tokens().issue(SshPrincipal("f", 5, null))
        assertEquals(5L, tokens(at = now.plus(SshRelayTokens.VALID.minusSeconds(1))).verify(token)!!.userId)
        assertNull(tokens(at = now.plus(SshRelayTokens.VALID).plusSeconds(1)).verify(token))
        assertEquals(Duration.ofMinutes(5), SshRelayTokens.VALID)
    }

    @Test
    fun `사용자와 배포 키가 모두 없는 주체는 표를 만들지 못한다`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> { tokens().issue(SshPrincipal("f", null, null)) }
    }
}
