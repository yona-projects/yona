package dev.yona.repository.hg

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.gpgkey.GpgKeyRepository
import dev.yona.domain.gpgkey.GpgKeyService
import dev.yona.domain.gpgkey.GpgReason
import dev.yona.domain.gpgkey.GpgVerificationService
import dev.yona.domain.gpgkey.TestGpgKey
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mercurial 커밋의 서명을 Git과 같은 `GpgVerificationService`로 판정한다(X-7 9c, `decisions.md` D-88). 서명한 키가 사용자의 등록 키이고 그 키의 이메일이 커밋 작성자
 * 이메일과 같으면 Verified이고, 다른 작성자의 커밋에 붙이면 `bad_email`, 서명이 없으면 `unsigned`이다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-signature"])
class HgSignatureVerificationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var verification: GpgVerificationService
    @Autowired private lateinit var keys: GpgKeyService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var repositories: HgRepositories

    private val stamp = System.nanoTime() % 1_000_000

    private fun user(): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "hv$stamp", name = "hv", email = "hv$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun sigLine(key: TestGpgKey, node: String): String {
        val binary = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(java.io.ByteArrayInputStream(key.sign("$node\n".toByteArray(Charsets.US_ASCII)).toByteArray())).readAllBytes()
        return "$node 0 ${Base64.getEncoder().encodeToString(binary)}"
    }

    @Test
    fun `등록한 키의 서명은 작성자 이메일이 같으면 Verified이고 다른 작성자나 서명 없음은 아니다`() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val owner = user()
        val key = TestGpgKey.generate(userId = "Signer <${owner.email}>")
        keys.add(owner, "k", key.publicArmored)
        repositories.create("hvo$stamp", "p"); val dir = repositories.directoryOf("hvo$stamp", "p")
        val mine = HgTestSupport.commit(dir, "내 커밋", mapOf("a.txt" to "one\n"), "2026-11-01 10:00 +0900", "나 <${owner.email}>")
        val others = HgTestSupport.commit(dir, "남의 커밋", mapOf("b.txt" to "b\n"), "2026-11-02 10:00 +0900", "남 <other@example.com>")
        val unsigned = HgTestSupport.commit(dir, "서명 안 한 커밋", mapOf("c.txt" to "c\n"), "2026-11-03 10:00 +0900", "나 <${owner.email}>")
        HgTestSupport.commit(dir, "서명을 더하는 커밋", mapOf(".hgsigs" to sigLine(key, mine) + "\n" + sigLine(key, others) + "\n"), "2026-11-04 10:00 +0900", "나 <${owner.email}>")
        HgTestSupport.bookmark(dir, "main")
        val reader = HgCommitReader(repositories)
        val project = Project(owner = "hvo$stamp", name = "p", vcs = "Mercurial")
        fun reasonOf(node: String) = reader.commit(project, node)!!.let { verification.verifyCommit(it.signature, it.committerEmail).reason }

        assertEquals(GpgReason.VALID, reasonOf(mine))
        assertEquals(GpgReason.BAD_EMAIL, reasonOf(others), "다른 사람의 커밋에 내 서명을 붙여도 Verified가 아니다")
        assertEquals(GpgReason.UNSIGNED, reasonOf(unsigned))
        assertTrue(verification.verifyCommit(reader.commit(project, mine)!!.signature, "나 <x>").reason != GpgReason.VALID)
    }
}
