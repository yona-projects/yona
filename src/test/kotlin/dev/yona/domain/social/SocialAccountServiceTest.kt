package dev.yona.domain.social

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserCredentialRepository
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 소셜 로그인 계정 연결 규칙을 검증한다. v1.6 `service.YonaUserServicePlugin`(`app/service/YonaUserServicePlugin.java`)과
 * `UserApp.createLocalUserWithOAuth`, `linkWithExistedOrCreateLocalUser`에 해당한다.
 */
@Transactional
class SocialAccountServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var social: SocialAccountService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var credentials: UserCredentialRepository
    @Autowired private lateinit var linkedAccounts: LinkedAccountRepository

    private var sequence = 0

    private fun identity(provider: String = "github", id: String = "gh-${++sequence}", email: String? = "sl$sequence@example.com", name: String? = "소셜 $sequence") =
        SocialIdentity(provider, id, email, name)

    private fun localUser(email: String, loginId: String = "local${++sequence}", state: UserState = UserState.ACTIVE): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = loginId, email = email, state = state, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    // --- 처음 보는 신원 (save) ---

    @Test
    fun `처음 보는 신원이고 같은 이메일의 사용자가 없으면 사용자를 만들어 로그인시킨다`() {
        val id = identity(email = "newbie@example.com", name = "새 사용자")
        val result = social.signIn(id)
        val user = assertNotNull(result.user)
        assertEquals("newbie", user.loginId)
        assertEquals("새 사용자", user.name)
        assertEquals("newbie@example.com", user.email)
        assertEquals(UserState.ACTIVE, user.state)
        assertFalse(result.forceLogout)
        val credential = assertNotNull(credentials.findAll().firstOrNull { it.email == "newbie@example.com" })
        assertEquals(true, credential.active)
        assertEquals(true, credential.emailValidated)
        assertEquals("newbie", credential.loginId)
        assertEquals(user.id, credential.user?.id)
        val linked = linkedAccounts.findByUserCredentialId(credential.id!!).single()
        assertEquals("github", linked.providerKey)
        assertEquals(id.providerUserId, linked.providerUserId)
    }

    @Test
    fun `만든 사용자의 비밀번호는 무작위이고 이메일 인증과 관계없이 활성이다`() {
        val a = assertNotNull(social.signIn(identity(email = "pw-a@example.com")).user)
        val b = assertNotNull(social.signIn(identity(email = "pw-b@example.com")).user)
        assertTrue(a.password!!.isNotBlank() && a.password != b.password)
        assertNotNull(a.passwordSalt)
    }

    @Test
    fun `로그인 ID가 겹치면 -yona를 붙이고 또 겹치면 숫자를 이어 붙인다`() {
        localUser("other1@example.com", loginId = "dup")
        assertEquals("dup-yona", social.signIn(identity(email = "dup@example.com")).user!!.loginId)
        // v1.6은 숫자를 이어 붙여 만든다: dup2, 겹치면 dup23, dup234
        assertEquals("dup2", social.signIn(identity(email = "dup@other.example.com")).user!!.loginId)
        assertEquals("dup23", social.signIn(identity(email = "dup@third.example.com")).user!!.loginId)
    }

    @Test
    fun `같은 이메일의 활성 사용자가 있으면 새로 만들지 않고 그 사용자로 로그인시킨다`() {
        val existing = localUser("same@example.com", loginId = "samelocal")
        val result = social.signIn(identity(email = "same@example.com"))
        assertEquals(existing.id, result.user?.id)
        assertEquals(1, users.findAll().count { it.email == "same@example.com" })
    }

    @Test
    fun `같은 이메일의 사용자가 잠겨 있으면 로그인시키지 않는다`() {
        localUser("locked@example.com", loginId = "lockedlocal", state = UserState.LOCKED)
        val result = social.signIn(identity(email = "locked@example.com"))
        assertNull(result.user)
        assertFalse(result.forceLogout)
    }

    @Test
    fun `이메일을 알 수 없으면 자격 증명을 지우고 안내와 함께 강제 로그아웃한다`() {
        val before = credentials.count()
        val result = social.signIn(identity(email = null))
        assertNull(result.user)
        assertTrue(result.forceLogout)
        assertEquals("app.warn.cannot.access.email.information", result.flash["error"])
        assertEquals(before, credentials.count())
        val viaText = social.signIn(identity(email = "null"))
        assertTrue(viaText.forceLogout)
    }

    // --- 이미 연결된 신원 (getLocalIdentity) ---

    @Test
    fun `이미 연결된 신원은 연결된 사용자로 로그인시킨다`() {
        val id = identity(email = "again@example.com")
        val first = social.signIn(id).user!!
        val second = social.signIn(id)
        assertEquals(first.id, second.user?.id)
        assertEquals(1, credentials.findAll().count { it.email == "again@example.com" })
    }

    @Test
    fun `이메일이 바뀌어도 자격 증명의 이메일이나 연결된 사용자로 찾는다`() {
        val id = identity(email = "before@example.com")
        val created = social.signIn(id).user!!
        val moved = id.copy(email = "after@elsewhere.example.com")
        assertEquals(created.id, social.signIn(moved).user?.id)
    }

    @Test
    fun `연결된 사용자가 잠겨 있으면 안내와 함께 강제 로그아웃한다`() {
        val id = identity(email = "later-locked@example.com")
        val created = social.signIn(id).user!!
        created.state = UserState.LOCKED
        users.save(created)
        val result = social.signIn(id)
        assertNull(result.user)
        assertTrue(result.forceLogout)
        assertEquals("user.locked", result.flash["warning"])
    }

    @Test
    fun `이름 동기화가 꺼져 있으면 제공자의 이름이 바뀌어도 그대로 둔다`() {
        val id = identity(email = "keep-name@example.com", name = "처음 이름")
        social.signIn(id)
        val result = social.signIn(id.copy(name = "바뀐 이름"))
        assertEquals("처음 이름", result.user?.name)
    }

    // --- 연결 (link) ---

    @Test
    fun `로그인한 신원에 새 신원을 연결한다`() {
        val old = identity(provider = "github", email = "link-old@example.com")
        social.signIn(old)
        val other = identity(provider = "google", email = "link-new@example.com")
        assertFalse(social.isLinked(other))
        social.link(old, other)
        assertTrue(social.isLinked(other))
        assertEquals(social.signIn(old).user?.id, social.signIn(other).user?.id)
    }

    @Test
    fun `연결 대상 신원이 없으면 아무 일도 하지 않는다`() {
        val before = linkedAccounts.count()
        social.link(identity(email = "ghost-old@example.com"), identity(provider = "google", email = "ghost-new@example.com"))
        assertEquals(before, linkedAccounts.count())
    }

    // --- 로그인 화면에서의 이어받기 (linkWithExistedOrCreateLocalUser) ---

    @Test
    fun `소셜 세션이 있는 채로 로그인 화면에 오면 연결된 사용자로 로그인시킨다`() {
        val id = identity(email = "resume@example.com")
        val created = social.signIn(id).user!!
        assertEquals(created.id, social.resume(id).user?.id)
    }

    @Test
    fun `이어받을 때 잠긴 사용자는 로그인시키지 않는다`() {
        val id = identity(email = "resume-locked@example.com")
        val created = social.signIn(id).user!!
        created.state = UserState.LOCKED
        users.save(created)
        assertNull(social.resume(id).user)
    }

    @Test
    fun `이어받을 때 연결된 사용자가 지워졌으면 새로 만들지만 로그인은 다음 방문에서 한다`() {
        val id = identity(email = "resume-gone@example.com")
        val created = social.signIn(id).user!!
        val credential = credentials.findAll().first { it.email == "resume-gone@example.com" }
        credential.loginId = null
        credential.user = null
        credentials.save(credential)
        users.delete(created)
        users.flush()
        val result = social.resume(id)
        assertNull(result.user)
        assertNotNull(users.findByLoginId("resume-gone"))
        assertEquals("resume-gone", credentials.findById(credential.id!!).get().loginId)
    }

    @Test
    fun `연결되지 않은 신원은 이어받을 것이 없다`() {
        assertNull(social.resume(identity(email = "nobody@example.com")).user)
    }
}
