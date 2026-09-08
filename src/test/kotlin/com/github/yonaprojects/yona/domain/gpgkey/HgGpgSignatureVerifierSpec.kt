package com.github.yonaprojects.yona.domain.gpgkey

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.Email
import com.github.yonaprojects.yona.domain.user.EmailRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.HgRepository
import io.github.search5.hg4j.api.Hg
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * yona-wiki P3-19 — Mercurial 쪽 GPG Verified/Unverified 배지. GpgSignatureVerifierSpec.kt(git)와
 * 완전히 동일한 실사용 검증 수준을 목표로 한다: 실제 `gpg` 바이너리로 만든 진짜 키, 실제 DB(GpgKeyService/
 * GpgKeyRepository)를 그대로 재사용한다. 다만 실제 Mercurial의 `gpg` 익스텐션/`.hgsigs`로는 이 티켓이
 * 채택한 포맷(changelog `extra` 딕셔너리에 내장된 gpgsig, git의 gpgsig 헤더와 동일한 셰이프)을 재현할
 * 수 없으므로(설계 결정: p3-19.md 완료 로그 참고), `hg` CLI 대신 hg4j 자체 API
 * (`CommitCommand.setGpgSigner()`)로 서명 커밋을 직접 만든다 — 서명 자체는 실제 `gpg --detach-sign`을
 * 호출해 만들어(git 테스트의 `git commit -S`와 동일한 신뢰 수준), 진짜 OpenPGP 서명을 검증한다.
 * gpg가 설치돼 있지 않은 환경에서는 스킵한다.
 */
class HgGpgSignatureVerifierSpec @Autowired constructor(
    private val gpgKeyService: GpgKeyService,
    private val gpgKeyRepository: GpgKeyRepository,
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    private val userRepository: UserRepository,
    private val emailRepository: EmailRepository
) : AbstractIntegrationTest() {

    private fun gpgAvailable(): Boolean =
        try {
            ProcessBuilder("gpg", "--version").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }

    private data class GeneratedKey(val gnupgHome: File, val keyId: String, val armoredPublicKey: String, val email: String)

    private fun generateGpgKey(emailLocalPart: String): GeneratedKey {
        // GpgSignatureVerifierSpec.kt(git)와 동일한 이유로 /tmp 아래에 GNUPGHOME을 만든다(유닉스
        // 소켓 경로 길이 제한 회피).
        val gnupgHome = Files.createTempDirectory(Paths.get("/tmp"), "hg-gpg-it-home-").toFile()
        gnupgHome.setExecutable(true, true)
        gnupgHome.setReadable(true, true)
        gnupgHome.setWritable(true, true)
        val email = "$emailLocalPart@example.com"

        val batchFile = File(gnupgHome, "gen-key.batch")
        batchFile.writeText(
            """
            %no-protection
            Key-Type: EDDSA
            Key-Curve: ed25519
            Subkey-Type: EDDSA
            Subkey-Curve: ed25519
            Name-Real: Test Hg Committer
            Name-Email: $email
            Expire-Date: 0
            %commit
            """.trimIndent()
        )

        fun run(vararg cmd: String): String {
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .also { it.environment()["GNUPGHOME"] = gnupgHome.absolutePath }
                .start()
            val output = process.inputStream.bufferedReader().readText()
            withClue(output) { process.waitFor() shouldBe 0 }
            return output
        }

        run("gpg", "--batch", "--generate-key", batchFile.absolutePath)
        val listing = run("gpg", "--list-secret-keys", "--keyid-format=long", "--with-colons")
        val keyId = listing.lineSequence().first { it.startsWith("sec:") }.split(":")[4]

        val exportProcess = ProcessBuilder("gpg", "--armor", "--export", keyId)
            .also { it.environment()["GNUPGHOME"] = gnupgHome.absolutePath }
            .start()
        val armoredPublicKey = exportProcess.inputStream.bufferedReader().readText()
        withClue(armoredPublicKey) { exportProcess.waitFor() shouldBe 0 }

        return GeneratedKey(gnupgHome, keyId, armoredPublicKey, email)
    }

    // 실제 gpg 바이너리로 detached ASCII-armor 서명을 만든다 — git 테스트의 `git commit -S`가
    // 내부적으로 gpg를 셸아웃하는 것과 동일한 신뢰 수준(BouncyCastle로 흉내내지 않고 진짜 gpg가
    // 서명). hg4j의 CommitCommand.GpgSigner는 함수형 인터페이스라 람다로 바로 넘길 수 있다.
    private fun gpgSign(generatedKey: GeneratedKey, payload: ByteArray): String {
        val process = ProcessBuilder(
            "gpg", "--batch", "--yes", "--local-user", generatedKey.keyId,
            "--detach-sign", "--armor"
        )
            .also { it.environment()["GNUPGHOME"] = generatedKey.gnupgHome.absolutePath }
            .start()
        process.outputStream.use { it.write(payload) }
        val output = process.inputStream.bufferedReader().readText()
        val errorOutput = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        withClue(errorOutput) { exit shouldBe 0 }
        return output
    }

    private fun newHgRepository(): Pair<HgRepository, File> {
        val baseDir = Files.createTempDirectory("hg-gpg-it-repo-").toFile()
        val repo = HgRepository(
            ownerName = "owner",
            projectName = "project",
            baseDir = baseDir.absolutePath,
            userResolver = { _, _ -> null },
            gpgVerifier = { nativeCommit -> gpgSignatureVerifier.verify(nativeCommit) }
        )
        repo.create()
        return repo to repo.getDirectory()
    }

    // signerKeyForCommit == null이면 서명하지 않는다. author는 hg의 "Name <email>" 관례 문자열.
    private fun commitTo(repoDir: File, author: String, message: String, signerKey: GeneratedKey? = null): Unit {
        val file = File(repoDir, "a.txt")
        file.parentFile.mkdirs()
        file.writeText(message)
        Hg.open(repoDir).use { hg ->
            hg.add().addFile("a.txt").call()
            val commitCommand = hg.commit().setAuthor(author).setMessage(message)
            if (signerKey != null) {
                commitCommand.setGpgSigner(signerKey.keyId) { payload -> gpgSign(signerKey, payload) }
            }
            commitCommand.call()
        }
    }

    init {
        describe("HgRepository의 GPG Verified 배지(실제 gpg 바이너리 + hg4j CommitCommand.setGpgSigner)") {
            beforeEach {
                gpgKeyRepository.deleteAll()
                emailRepository.deleteAll()
                userRepository.deleteAll()
            }

            afterSpec {
                gpgKeyRepository.deleteAll()
                emailRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("서명 없는 커밋은 UNSIGNED여야 한다") {
                val (repo, repoDir) = newHgRepository()
                commitTo(repoDir, "Unsigned Committer <unsigned@example.com>", "unsigned commit")

                val commit = repo.getCommit("tip")
                commit.shouldNotBeNull()
                commit.getGpgVerificationStatus() shouldBe GpgVerificationStatus.UNSIGNED
            }

            it("실제 서명된 커밋은, 서명 키가 등록되고 author 이메일이 인증돼 있으면 VERIFIED여야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("hg-gpgit-verified")
                val user = userRepository.save(User(loginId = "hg-gpgit-verified-user", name = "GPG확인유저", email = generatedKey.email))
                emailRepository.save(Email(user = user, email = generatedKey.email, valid = true))
                gpgKeyService.create(user, generatedKey.armoredPublicKey)

                val (repo, repoDir) = newHgRepository()
                commitTo(repoDir, "Test Hg Committer <${generatedKey.email}>", "signed commit", generatedKey)

                val commit = repo.getCommit("tip")
                commit?.getGpgVerificationStatus() shouldBe GpgVerificationStatus.VERIFIED
            }

            it("서명 키가 등록돼 있지 않으면 UNVERIFIED여야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("hg-gpgit-unregistered")
                // 키를 등록하지 않고 서명된 커밋만 만든다.
                val (repo, repoDir) = newHgRepository()
                commitTo(repoDir, "Test Hg Committer <${generatedKey.email}>", "signed but unregistered", generatedKey)

                val commit = repo.getCommit("tip")
                commit?.getGpgVerificationStatus() shouldBe GpgVerificationStatus.UNVERIFIED
            }

            // 보안 리뷰 항목 — git 쪽과 동일하게, 서명자가 아닌 다른(등록된) 사람의 키로는
            // 검증에 성공하면 안 된다.
            it("등록된 키가 있어도 실제 서명자의 키가 아니면 UNVERIFIED여야 한다") {
                if (!gpgAvailable()) return@it

                val signerKey = generateGpgKey("hg-gpgit-real-signer")
                val (repo, repoDir) = newHgRepository()
                commitTo(repoDir, "Test Hg Committer <${signerKey.email}>", "signed by real signer", signerKey)

                val otherKey = generateGpgKey("hg-gpgit-other-registered")
                val otherUser = userRepository.save(User(loginId = "hg-gpgit-other-user", name = "다른유저", email = otherKey.email))
                emailRepository.save(Email(user = otherUser, email = otherKey.email, valid = true))
                gpgKeyService.create(otherUser, otherKey.armoredPublicKey)

                val commit = repo.getCommit("tip")
                commit?.getGpgVerificationStatus() shouldBe GpgVerificationStatus.UNVERIFIED
            }

            it("등록은 됐지만 author 이메일이 그 키의 인증된 UID와 다르면 UNVERIFIED여야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("hg-gpgit-email-mismatch")
                val user = userRepository.save(User(loginId = "hg-gpgit-mismatch-user", name = "불일치유저", email = generatedKey.email))
                emailRepository.save(Email(user = user, email = generatedKey.email, valid = true))
                gpgKeyService.create(user, generatedKey.armoredPublicKey)

                val (repo, repoDir) = newHgRepository()
                // 같은 키로 서명하되 author 이메일을 등록된 것과 다르게 커밋한다.
                commitTo(repoDir, "Spoofed Author <someone-else@example.com>", "mismatched author email", generatedKey)

                val commit = repo.getCommit("tip")
                commit?.getGpgVerificationStatus() shouldBe GpgVerificationStatus.UNVERIFIED
            }
        }
    }
}
