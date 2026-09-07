package com.github.yonaprojects.yona.domain.gpgkey

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.Email
import com.github.yonaprojects.yona.domain.user.EmailRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * yona-wiki P3-03 Step7/Step8 — 실제 `gpg`/`git commit -S` 바이너리로 만든 진짜 서명 커밋을
 * 대상으로 GpgSignatureVerifier가 실제로 암호학적 검증을 수행하는지 검증한다(단순히 "서명이
 * 있다"만 확인하는 무의미한 배지가 아님을 증명하는 핵심 테스트 — 작업 지시문 보안 리뷰 항목).
 * gpg가 설치돼 있지 않은 환경에서는 스킵한다.
 */
class GpgSignatureVerifierSpec @Autowired constructor(
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
        // macOS의 java.io.tmpdir(/var/folders/.../T)은 경로가 길어 GNUPGHOME 안에 만들어지는
        // gpg-agent 유닉스 소켓(S.gpg-agent 등) 경로가 커널의 sun_path 길이 제한(~104바이트)을
        // 넘겨 "gpg: error running '.../gpg-agent': exit status 2"로 키 생성 자체가 실패한다
        // (실측 재현 완료, 2026-09-07). /tmp는 항상 짧으므로 명시적으로 그 아래에 만든다.
        val gnupgHome = Files.createTempDirectory(Paths.get("/tmp"), "gpg-it-home-").toFile()
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
            Name-Real: Test Committer
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

    private fun createSignedCommit(generatedKey: GeneratedKey, message: String = "signed commit"): RevCommit {
        val repoDir = Files.createTempDirectory("gpg-it-repo-").toFile()

        fun run(vararg cmd: String) {
            val process = ProcessBuilder(*cmd)
                .directory(repoDir)
                .redirectErrorStream(true)
                .also { it.environment()["GNUPGHOME"] = generatedKey.gnupgHome.absolutePath }
                .start()
            val output = process.inputStream.bufferedReader().readText()
            withClue(output) { process.waitFor() shouldBe 0 }
        }

        run("git", "init", "-q", "-b", "main")
        run("git", "config", "user.name", "Test Committer")
        run("git", "config", "user.email", generatedKey.email)
        run("git", "config", "user.signingkey", generatedKey.keyId)
        run("git", "config", "gpg.program", "gpg")
        File(repoDir, "file.txt").writeText("hello")
        run("git", "add", "file.txt")
        run("git", "commit", "-S", "-q", "-m", message)

        Git.open(repoDir).use { git ->
            val head = git.repository.resolve("HEAD")
            git.repository.newObjectReader().use { reader ->
                return org.eclipse.jgit.revwalk.RevWalk(reader).use { walk -> walk.parseCommit(head) }
            }
        }
    }

    private fun createUnsignedCommit(): RevCommit {
        val repoDir = Files.createTempDirectory("gpg-it-repo-unsigned-").toFile()
        fun run(vararg cmd: String) {
            val process = ProcessBuilder(*cmd).directory(repoDir).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            withClue(output) { process.waitFor() shouldBe 0 }
        }
        run("git", "init", "-q", "-b", "main")
        run("git", "config", "user.name", "Unsigned Committer")
        run("git", "config", "user.email", "unsigned@example.com")
        // 실행 머신의 전역 git 설정(~/.gitconfig)에 commit.gpgsign=true가 걸려 있으면, -S 없이
        // 커밋해도 전역 signingkey로 실제 서명이 붙어버려 이 테스트의 전제(UNSIGNED)가 깨진다
        // (실측 재현 완료, 2026-09-07) — 전역 설정과 무관하게 확실히 미서명이 되도록 로컬에서 끈다.
        run("git", "config", "commit.gpgsign", "false")
        File(repoDir, "file.txt").writeText("hello")
        run("git", "add", "file.txt")
        run("git", "commit", "-q", "-m", "unsigned commit")

        Git.open(repoDir).use { git ->
            val head = git.repository.resolve("HEAD")
            git.repository.newObjectReader().use { reader ->
                return org.eclipse.jgit.revwalk.RevWalk(reader).use { walk -> walk.parseCommit(head) }
            }
        }
    }

    init {
        describe("GpgSignatureVerifier + GpgKeyServiceImpl (실제 gpg/git 바이너리)") {
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
                val commit = createUnsignedCommit()
                gpgSignatureVerifier.verify(commit) shouldBe GpgVerificationStatus.UNSIGNED
            }

            it("실제 서명된 커밋은, 서명 키가 등록되고 author 이메일이 인증돼 있으면 VERIFIED여야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("gpgit-verified")
                val user = userRepository.save(User(loginId = "gpgit-verified-user", name = "GPG확인유저", email = generatedKey.email))
                emailRepository.save(Email(user = user, email = generatedKey.email, valid = true))

                gpgKeyService.create(user, generatedKey.armoredPublicKey)

                val commit = createSignedCommit(generatedKey)

                gpgSignatureVerifier.verify(commit) shouldBe GpgVerificationStatus.VERIFIED
            }

            it("서명 키가 등록돼 있지 않으면 UNVERIFIED여야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("gpgit-unregistered")
                // 키를 등록하지 않고 서명된 커밋만 만든다.
                val commit = createSignedCommit(generatedKey)

                gpgSignatureVerifier.verify(commit) shouldBe GpgVerificationStatus.UNVERIFIED
            }

            // 보안 리뷰 항목 — 서명이 "있다"만 보고 배지를 붙이면 안 된다. 서로 다른 키로 서명된
            // 커밋에 대해, 그 키가 아닌 엉뚱한(등록된) 다른 사람의 키로는 검증에 성공하면 안 된다.
            it("등록된 키가 있어도 실제 서명자의 키가 아니면(다른 사람의 유효한 서명) UNVERIFIED여야 한다") {
                if (!gpgAvailable()) return@it

                val signerKey = generateGpgKey("gpgit-real-signer")
                val commit = createSignedCommit(signerKey)

                // signerKey와 무관한 다른 키를 등록해둔다 — issuer key id가 다르므로 후보에도
                // 안 잡혀야 한다.
                val otherKey = generateGpgKey("gpgit-other-registered")
                val otherUser = userRepository.save(User(loginId = "gpgit-other-user", name = "다른유저", email = otherKey.email))
                emailRepository.save(Email(user = otherUser, email = otherKey.email, valid = true))
                gpgKeyService.create(otherUser, otherKey.armoredPublicKey)

                gpgSignatureVerifier.verify(commit) shouldBe GpgVerificationStatus.UNVERIFIED
            }

            it("등록은 됐지만 author 이메일이 그 키의 인증된 UID와 다르면 UNVERIFIED여야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("gpgit-email-mismatch")
                val user = userRepository.save(User(loginId = "gpgit-mismatch-user", name = "불일치유저", email = generatedKey.email))
                emailRepository.save(Email(user = user, email = generatedKey.email, valid = true))
                gpgKeyService.create(user, generatedKey.armoredPublicKey)

                // 같은 키로 서명하되, author 이메일을 등록된 것과 다르게 커밋해본다.
                val repoDir = Files.createTempDirectory("gpg-it-repo-mismatch-").toFile()
                fun run(vararg cmd: String) {
                    val process = ProcessBuilder(*cmd).directory(repoDir).redirectErrorStream(true)
                        .also { it.environment()["GNUPGHOME"] = generatedKey.gnupgHome.absolutePath }.start()
                    val output = process.inputStream.bufferedReader().readText()
                    withClue(output) { process.waitFor() shouldBe 0 }
                }
                run("git", "init", "-q", "-b", "main")
                run("git", "config", "user.name", "Spoofed Author")
                run("git", "config", "user.email", "someone-else@example.com")
                run("git", "config", "user.signingkey", generatedKey.keyId)
                run("git", "config", "gpg.program", "gpg")
                File(repoDir, "file.txt").writeText("hello")
                run("git", "add", "file.txt")
                run("git", "commit", "-S", "-q", "-m", "mismatched author email")

                val commit = Git.open(repoDir).use { git ->
                    val head = git.repository.resolve("HEAD")
                    git.repository.newObjectReader().use { reader ->
                        org.eclipse.jgit.revwalk.RevWalk(reader).use { walk -> walk.parseCommit(head) }
                    }
                }

                gpgSignatureVerifier.verify(commit) shouldBe GpgVerificationStatus.UNVERIFIED
            }

            // 보안 리뷰 항목 — 계정 소유로 인증되지 않은 이메일을 UID로 가진 키는 애초에 등록을
            // 거부해야 한다(다른 사람 이메일을 UID에 넣어 가짜 Verified 배지를 노리는 시도 방지).
            it("계정 소유로 인증되지 않은 이메일만 UID에 있는 키는 등록을 거부해야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("gpgit-unverified-email")
                val user = userRepository.save(User(loginId = "gpgit-unowned-user", name = "미인증유저", email = "different-owned@example.com"))
                // generatedKey.email을 이 계정의 인증 이메일로 등록하지 않는다.

                shouldThrow<IllegalArgumentException> {
                    gpgKeyService.create(user, generatedKey.armoredPublicKey)
                }
            }

            it("같은 GPG 키(지문)를 두 번 등록하면 거부해야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("gpgit-dup")
                val user = userRepository.save(User(loginId = "gpgit-dup-user", name = "중복유저", email = generatedKey.email))
                emailRepository.save(Email(user = user, email = generatedKey.email, valid = true))
                gpgKeyService.create(user, generatedKey.armoredPublicKey)

                shouldThrow<IllegalArgumentException> {
                    gpgKeyService.create(user, generatedKey.armoredPublicKey)
                }
            }
        }
    }
}
