package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranch
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgKeyRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgKeyService
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.Email
import com.github.yonaprojects.yona.domain.user.EmailRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.lib.HgRepository as NativeHgRepository
import io.github.search5.hg4j.lib.NodeId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * yona-wiki P3-21 — HgGpgSignatureVerifierSpec.kt와 동일한 신뢰 수준(실제 `gpg` 바이너리로 만든
 * 진짜 키, 실제 hg4j CommitCommand.setGpgSigner(), 실제 DB)으로
 * HgBranchProtectionPrePushkeyHook의 require_signed_commits 판정을 검증한다 — 단위 테스트
 * (HgPushHooksSpec.kt)는 GpgSignatureVerifier를 mock으로 대체하므로, "실제 서명 검증 파이프라인과
 * 끝까지 연결됐는가"는 이 통합 테스트가 유일하게 증명한다. gpg가 설치돼 있지 않은 환경에서는 스킵한다.
 */
class HgPushHooksIntegrationSpec @Autowired constructor(
    private val gpgKeyService: GpgKeyService,
    private val gpgKeyRepository: GpgKeyRepository,
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    private val userRepository: UserRepository,
    private val emailRepository: EmailRepository,
    private val projectRepository: ProjectRepository,
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository
) : AbstractIntegrationTest() {

    private fun gpgAvailable(): Boolean =
        try {
            ProcessBuilder("gpg", "--version").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }

    private data class GeneratedKey(val gnupgHome: File, val keyId: String, val armoredPublicKey: String, val email: String)

    // HgGpgSignatureVerifierSpec.kt와 동일한 실제 gpg 키 생성 절차.
    private fun generateGpgKey(emailLocalPart: String): GeneratedKey {
        val gnupgHome = Files.createTempDirectory(Paths.get("/tmp"), "hg-pushhooks-it-home-").toFile()
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
            Name-Real: Test Hg Pusher
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

    private fun commitTo(repoDir: File, path: String, author: String, message: String, signerKey: GeneratedKey? = null): String {
        val file = File(repoDir, path)
        file.parentFile?.mkdirs()
        file.writeText(message)
        return Hg.open(repoDir).use { hg ->
            hg.add().addFile(path).call()
            val commitCommand = hg.commit().setAuthor(author).setMessage(message)
            if (signerKey != null) {
                commitCommand.setGpgSigner(signerKey.keyId) { payload -> gpgSign(signerKey, payload) }
            }
            val node = commitCommand.call()
            NodeId(node).toHex()
        }
    }

    private fun contextOf(nativeRepo: NativeHgRepository, key: String, old: String, new: String): MutableMap<String, Any> {
        val map = HashMap<String, Any>()
        map["namespace"] = "bookmarks"
        map["key"] = key
        map["old"] = old
        map["new"] = new
        map["repository"] = nativeRepo
        return map
    }

    init {
        describe("HgBranchProtectionPrePushkeyHook의 require_signed_commits (실제 gpg 바이너리 + hg4j CommitCommand.setGpgSigner)") {
            beforeEach {
                gpgKeyRepository.deleteAll()
                emailRepository.deleteAll()
                protectedBranchRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            afterSpec {
                gpgKeyRepository.deleteAll()
                emailRepository.deleteAll()
                protectedBranchRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("서명되지 않은 새 커밋을 require_signed_commits 브랜치에 push하면 거부돼야 한다") {
                if (!gpgAvailable()) return@it

                val pusher = userRepository.save(User(loginId = "hg-pushhooks-unsigned", name = "서명안함", email = "unsigned-pusher@example.com"))
                val project = projectRepository.save(Project(name = "hg-pushhooks-project", owner = "owner1"))
                protectedBranchRepository.save(ProtectedBranch(project = project, branchPattern = "main", requireSignedCommits = true))

                val repoDir = Files.createTempDirectory("hg-pushhooks-it-repo-").toFile()
                Hg.init().setDirectory(repoDir).call()
                val hex = commitTo(repoDir, "a.txt", "Unsigned <unsigned-pusher@example.com>", "unsigned commit")

                val nativeRepo = NativeHgRepository(repoDir)
                val hook = HgBranchProtectionPrePushkeyHook(
                    project, pusher, protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
                )
                val result = hook.run(contextOf(nativeRepo, "main", "", hex))

                result shouldBe false
            }

            it("실제 gpg로 서명하고 키가 등록·이메일 인증까지 된 커밋은 require_signed_commits 브랜치에 push를 허용해야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("hg-pushhooks-verified")
                val pusher = userRepository.save(
                    User(loginId = "hg-pushhooks-verified-user", name = "서명확인유저", email = generatedKey.email)
                )
                emailRepository.save(Email(user = pusher, email = generatedKey.email, valid = true))
                gpgKeyService.create(pusher, generatedKey.armoredPublicKey)

                val project = projectRepository.save(Project(name = "hg-pushhooks-project-verified", owner = "owner2"))
                protectedBranchRepository.save(ProtectedBranch(project = project, branchPattern = "main", requireSignedCommits = true))

                val repoDir = Files.createTempDirectory("hg-pushhooks-it-repo-verified-").toFile()
                Hg.init().setDirectory(repoDir).call()
                val hex = commitTo(repoDir, "a.txt", "Test Hg Pusher <${generatedKey.email}>", "signed commit", generatedKey)

                val nativeRepo = NativeHgRepository(repoDir)
                val hook = HgBranchProtectionPrePushkeyHook(
                    project, pusher, protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
                )
                val result = hook.run(contextOf(nativeRepo, "main", "", hex))

                result shouldBe true
            }

            it("실제 gpg로 서명했지만 키가 등록돼 있지 않으면 거부돼야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("hg-pushhooks-unregistered")
                val pusher = userRepository.save(
                    User(loginId = "hg-pushhooks-unregistered-user", name = "미등록유저", email = generatedKey.email)
                )
                val project = projectRepository.save(Project(name = "hg-pushhooks-project-unregistered", owner = "owner3"))
                protectedBranchRepository.save(ProtectedBranch(project = project, branchPattern = "main", requireSignedCommits = true))

                val repoDir = Files.createTempDirectory("hg-pushhooks-it-repo-unregistered-").toFile()
                Hg.init().setDirectory(repoDir).call()
                val hex = commitTo(repoDir, "a.txt", "Test Hg Pusher <${generatedKey.email}>", "signed but unregistered", generatedKey)

                val nativeRepo = NativeHgRepository(repoDir)
                val hook = HgBranchProtectionPrePushkeyHook(
                    project, pusher, protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
                )
                val result = hook.run(contextOf(nativeRepo, "main", "", hex))

                result shouldBe false
            }
        }
    }
}
