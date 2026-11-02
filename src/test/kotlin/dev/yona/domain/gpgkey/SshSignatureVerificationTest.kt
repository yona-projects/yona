package dev.yona.domain.gpgkey

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.user.Email
import dev.yona.domain.user.EmailRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SSH 키로 서명한 커밋과 주석 태그의 판정을 검증한다(`decisions.md` D-68). 커밋과 태그는 실제 `git`(`gpg.format=ssh`)이 `ssh-keygen`으로 서명한 것이다. Verified이려면 서명이 맞고,
 * 서명한 키가 등록된 사용자 키이며, 주인이 활성 사용자이고, 커밋터(태그를 단 사람) 이메일이 주인이 확인한 이메일(대표 이메일 또는 확인한 보조 이메일)이어야 한다.
 * SSH 키에는 사용자 ID가 없어 GPG의 `bad_email`은 없다. 배포 키는 서명자가 될 수 없다.
 */
@Transactional
class SshSignatureVerificationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var verifier: GpgVerificationService
    @Autowired private lateinit var sshKeys: SshKeyService
    @Autowired private lateinit var deployKeys: DeployKeyService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var emails: EmailRepository
    @Autowired private lateinit var projects: ProjectRepository

    @TempDir lateinit var dir: File

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE, email: String = "ss${++sequence}@example.com") = users.save(User(loginId = "ss$sequence", name = "ss$sequence", email = email, state = state))

    /** ssh-keygen이 만든 키 한 벌의 개인키 파일이다. 공개키 한 줄은 [publicLine]이다. */
    private class Key(val file: File) {
        val publicLine: String get() = File(file.path + ".pub").readText().trim()
    }

    private fun newKey(): Key {
        val file = File(dir, "k-${++sequence}")
        check(ProcessBuilder("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "t", "-f", file.path).redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0)
        return Key(file)
    }

    private fun run(work: File, vararg command: String): String {
        val process = ProcessBuilder(*command).directory(work).redirectErrorStream(true).start()
        val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor() == 0) { "${command.toList()} 실패: $out" }
        return out
    }

    /** [key]로 서명한 커밋과 주석 태그의 원문이다. 커밋터 이메일은 [email]이다. */
    private class Signed(val commit: ByteArray, val tag: ByteArray)

    private fun signed(key: Key, email: String): Signed {
        val work = File(dir, "repo-${++sequence}").also { it.mkdirs() }
        val git = arrayOf("git", "-c", "user.name=T", "-c", "user.email=$email", "-c", "gpg.format=ssh", "-c", "user.signingkey=${key.file.path}")
        run(work, *git, "init", "-q", "-b", "master"); File(work, "a.txt").writeText("hello"); run(work, *git, "add", ".")
        run(work, *git, "commit", "-q", "-S", "-m", "서명한 커밋")
        run(work, *git, "tag", "-s", "-m", "서명한 태그", "v1")
        fun raw(type: String, ref: String) = ProcessBuilder("git", "cat-file", type, ref).directory(work).start().inputStream.readBytes()
        return Signed(raw("commit", "HEAD"), raw("tag", "v1"))
    }

    private fun payload(raw: ByteArray, tag: Boolean = false) = assertNotNull(if (tag) GitSignatures.ofTag(raw) else GitSignatures.ofCommit(raw))

    private fun register(owner: User, key: Key) = sshKeys.add(owner, "k", key.publicLine)

    @Test
    fun `등록한 키로 서명하고 커밋터 이메일이 주인의 이메일이면 valid이고 서명한 사용자를 알려 준다`() {
        val owner = user(email = "alice@example.com"); val key = newKey(); register(owner, key)
        val result = verifier.verifyCommit(payload(signed(key, "alice@example.com").commit), "alice@example.com")
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals(owner.id, result.signerId); assertEquals(owner.loginId, result.signerLogin)
        assertEquals("ssh", result.format)
        assertTrue(result.fingerprint!!.startsWith("SHA256:"), "SSH 서명은 키 지문을 알려 준다")
    }

    @Test
    fun `주석 태그의 SSH 서명도 검증한다`() {
        val owner = user(email = "bob@example.com"); val key = newKey(); register(owner, key)
        val result = verifier.verifyTag(payload(signed(key, "bob@example.com").tag, tag = true), "bob@example.com")
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals(owner.id, result.signerId)
    }

    @Test
    fun `커밋터 이메일은 대소문자를 구분하지 않고 확인한 보조 이메일도 인정한다`() {
        val owner = user(email = "Carol@Example.com"); val key = newKey(); register(owner, key)
        emails.save(Email(user = owner, email = "carol.work@example.com", valid = true))
        emails.save(Email(user = owner, email = "pending@example.com", valid = false))
        val s = signed(key, "x@example.com")
        assertEquals(GpgReason.VALID, verifier.verifyCommit(payload(s.commit), "carol@EXAMPLE.com").reason)
        assertEquals(GpgReason.VALID, verifier.verifyCommit(payload(s.commit), "carol.work@example.com").reason)
        assertEquals(GpgReason.UNVERIFIED_EMAIL, verifier.verifyCommit(payload(s.commit), "pending@example.com").reason, "확인하지 않은 보조 이메일")
        assertEquals(GpgReason.UNVERIFIED_EMAIL, verifier.verifyCommit(payload(s.commit), "other@example.com").reason)
        assertEquals(GpgReason.UNVERIFIED_EMAIL, verifier.verifyCommit(payload(s.commit), null).reason)
    }

    @Test
    fun `등록하지 않은 키의 서명은 unknown_key이다`() {
        val key = newKey()
        val result = verifier.verifyCommit(payload(signed(key, "a@example.com").commit), "a@example.com")
        assertEquals(GpgReason.UNKNOWN_KEY, result.reason)
        assertEquals("ssh", result.format)
        assertNull(result.signerId)
    }

    @Test
    fun `배포 키는 서명자가 될 수 없다`() {
        val key = newKey(); val p = projects.save(Project(owner = "sso${++sequence}", name = "ssp$sequence", vcs = "GIT"))
        deployKeys.add(p, "d", key.publicLine, false)
        assertEquals(GpgReason.UNKNOWN_KEY, verifier.verifyCommit(payload(signed(key, "a@example.com").commit), "a@example.com").reason)
    }

    @Test
    fun `내용을 바꾸면 invalid이다`() {
        val owner = user(email = "dave@example.com"); val key = newKey(); register(owner, key)
        val original = payload(signed(key, "dave@example.com").commit)
        val tampered = SignedPayload(String(original.data, Charsets.UTF_8).replace("서명한 커밋", "바뀐 메시지").toByteArray(), original.signature)
        assertEquals(GpgReason.INVALID, verifier.verifyCommit(tampered, "dave@example.com").reason)
    }

    @Test
    fun `잠기거나 삭제된 주인의 키는 no_user이다`() {
        for (state in listOf(UserState.LOCKED, UserState.DELETED)) {
            val owner = user(state, "eve${++sequence}@example.com"); val key = newKey(); register(owner, key)
            assertEquals(GpgReason.NO_USER, verifier.verifyCommit(payload(signed(key, owner.email!!).commit), owner.email).reason, state.name)
        }
    }

    @Test
    fun `읽을 수 없는 SSH 서명은 malformed_signature이다`() {
        val broken = SignedPayload("data".toByteArray(), "-----BEGIN SSH SIGNATURE-----\nAAAA\n-----END SSH SIGNATURE-----\n".toByteArray())
        assertEquals(GpgReason.MALFORMED_SIGNATURE, verifier.verifyCommit(broken, "a@example.com").reason)
    }

    @Test
    fun `서명이 없는 커밋은 unsigned이고 GPG 서명은 기존대로 판정한다`() {
        assertEquals(GpgReason.UNSIGNED, verifier.verifyCommit(null, "a@example.com").reason)
        assertEquals("gpg", verifier.verifyCommit(null, "a@example.com").format)
    }
}
