package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranch
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgKeyRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgKeyService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.Email
import com.github.yonaprojects.yona.domain.user.EmailRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.event.PullRequestMergeEventListener
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

@Transactional
class PullRequestServiceSpec @Autowired constructor(
    private val pullRequestService: PullRequestService,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val issueRepository: IssueRepository,
    private val issueService: IssueService,
    private val pullRequestMergeEventListener: PullRequestMergeEventListener,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val watchRepository: WatchRepository,
    private val issueEventRepository: IssueEventRepository,
    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — setAssignee/addLabel/removeLabel 검증용.
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    // yona-wiki P3-04(브랜치 보호) Step 4/5 — merge() 시 ProtectedBranch 체크 검증용.
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    // yona-wiki P3-03/P3-04 연결 작업(2026-09-07) — require_signed_commits가 merge()에서 실제로
    // 검사되는지 실제 gpg 서명 커밋으로 검증하는 데 필요.
    private val gpgKeyService: GpgKeyService,
    private val gpgKeyRepository: GpgKeyRepository,
    private val emailRepository: EmailRepository,
    // yona-wiki P3-15(PR 승인/변경요청 워크플로) — submitReview/getReviews/getLatestReviewStates 및
    // require_approvals 연결(checkApprovalsForMerge) 검증용.
    private val pullRequestReviewRepository: PullRequestReviewRepository
) : AbstractIntegrationTest() {

    init {
        describe("PullRequestService 통합 테스트") {
            lateinit var contributor: User
            lateinit var receiver: User
            lateinit var toProject: Project
            lateinit var fromProject: Project

            beforeEach {
                watchRepository.deleteAll()
                pullRequestEventRepository.deleteAll()
                notificationEventRepository.deleteAll()
                pullRequestCommitRepository.deleteAll()
                // yona-wiki P3-15 — pull_request FK가 걸려 있으므로 pullRequestRepository.deleteAll()보다 먼저 지운다.
                pullRequestReviewRepository.deleteAll()
                pullRequestRepository.deleteAll()
                issueEventRepository.deleteAll()
                issueRepository.deleteAll()
                issueLabelRepository.deleteAll()
                issueLabelCategoryRepository.deleteAll()
                // yona-wiki P3-04(브랜치 보호) — project FK가 걸려 있으므로 projectRepository.deleteAll()보다 먼저 지운다.
                protectedBranchRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                // yona-wiki P3-03/P3-04 연결 작업 — user FK가 걸려 있으므로 userRepository.deleteAll()보다 먼저 지운다.
                gpgKeyRepository.deleteAll()
                emailRepository.deleteAll()
                userRepository.deleteAll()

                val uniqueSuffix = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(6)

                contributor = userRepository.save(
                    User(loginId = "contrib-$uniqueSuffix", name = "기여자", email = "contrib-$uniqueSuffix@yona.io")
                )
                receiver = userRepository.save(
                    User(loginId = "receive-$uniqueSuffix", name = "수신자", email = "receive-$uniqueSuffix@yona.io")
                )

                toProject = projectRepository.save(
                    Project(name = "to-repo-$uniqueSuffix", owner = "owner-a", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
                )
                fromProject = projectRepository.save(
                    Project(name = "from-repo-$uniqueSuffix", owner = "owner-b", vcs = "GIT")
                )

                // 물리 Git 저장소 초기 생성
                repositoryService.getRepository(toProject).create()
                repositoryService.getRepository(fromProject).create()
            }

            afterEach {
                try {
                    repositoryService.getRepository(toProject).delete()
                } catch (e: Exception) {}
                try {
                    repositoryService.getRepository(fromProject).delete()
                } catch (e: Exception) {}
            }

            fun createCommit(
                bareRepoDir: File,
                branch: String,
                filePath: String,
                content: String,
                commitMsg: String,
                // updatePullRequestCommits()의 "이전 커밋이 새 diff에 없으면 PRIOR로 전환" 분기(#10)
                // 검증용 - branch와 다른 baseBranch를 지정하면 해당 브랜치를 base로 강제 리셋(=amend/rebase
                // 시뮬레이션)하여 branch의 기존 커밋을 완전히 대체한다.
                baseBranch: String = branch
            ) {
                val tempWorkingDir = Files.createTempDirectory("yona-test-work").toFile()
                try {
                    val git = Git.init().setDirectory(tempWorkingDir).call()
                    val config = git.repository.config
                    config.setString("remote", "origin", "url", bareRepoDir.absolutePath)
                    config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
                    config.save()

                    try {
                        git.fetch().setRemote("origin").call()
                        val ref = git.repository.resolve("refs/remotes/origin/$baseBranch")
                        if (ref != null) {
                            git.checkout()
                                .setCreateBranch(true)
                                .setName(branch)
                                .setStartPoint("origin/$baseBranch")
                                .call()
                        } else {
                            val originMaster = git.repository.resolve("refs/remotes/origin/master")
                            if (originMaster != null) {
                                git.checkout()
                                    .setCreateBranch(true)
                                    .setName(branch)
                                    .setStartPoint("origin/master")
                                    .call()
                            }
                        }
                    } catch (e: Exception) {
                        // 빈 저장소인 경우 checkout 생략 (기본 master 브랜치 상태로 작업)
                    }

                    // 파일 작성
                    val file = File(tempWorkingDir, filePath)
                    file.parentFile.mkdirs()
                    file.writeText(content)

                    // 커밋 및 푸시
                    git.add().addFilepattern(filePath).call()
                    git.commit()
                        .setSign(false)
                        .setAuthor("tester", "tester@yona.io")
                        .setMessage(commitMsg)
                        .call()
                    
                    git.push()
                        .setRemote("origin")
                        .setRefSpecs(RefSpec("HEAD:refs/heads/$branch"))
                        .setForce(true)
                        .call()
                    
                    git.repository.close()
                    git.close()
                } finally {
                    tempWorkingDir.deleteRecursively()
                }
            }
            fun syncRepository(srcBareDir: File, destBareDir: File, branch: String) {
                val tempWorkingDir = Files.createTempDirectory("yona-test-sync").toFile()
                try {
                    val git = Git.init().setDirectory(tempWorkingDir).call()
                    val config = git.repository.config
                    config.setString("remote", "origin", "url", srcBareDir.absolutePath)
                    config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
                    config.save()
                    
                    git.fetch().setRemote("origin").call()
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(branch)
                        .setStartPoint("origin/$branch")
                        .call()
                        
                    config.setString("remote", "dest", "url", destBareDir.absolutePath)
                    config.save()
                    git.push()
                        .setRemote("dest")
                        .setRefSpecs(RefSpec("HEAD:refs/heads/$branch"))
                        .setForce(true)
                        .call()
                        
                    git.repository.close()
                    git.close()
                } finally {
                    tempWorkingDir.deleteRecursively()
                }
            }

            // yona-wiki P3-03/P3-04 연결 작업(2026-09-07) — require_signed_commits가 실제로
            // merge()에서 검사되는지 검증하려면 진짜 gpg 서명 커밋이 필요하다(GpgSignatureVerifierSpec/
            // YonaMinaSshServerIntegrationSpec과 동일한 방식 — 실제 `gpg`/`git commit -S` 바이너리
            // 사용, 순수 mock으로는 의미있게 테스트할 수 없다).
            fun gpgAvailable(): Boolean =
                try {
                    ProcessBuilder("gpg", "--version").start().waitFor() == 0
                } catch (e: Exception) {
                    false
                }

            data class GeneratedGpgKey(val gnupgHome: File, val keyId: String, val armoredPublicKey: String, val email: String)

            fun generateGpgKey(emailLocalPart: String): GeneratedGpgKey {
                val gnupgHome = Files.createTempDirectory("pr-it-gpg-home-").toFile()
                val email = "$emailLocalPart@example.com"

                val batchFile = File(gnupgHome, "gen-key.batch")
                batchFile.writeText(
                    """
                    %no-protection
                    Key-Type: EDDSA
                    Key-Curve: ed25519
                    Subkey-Type: EDDSA
                    Subkey-Curve: ed25519
                    Name-Real: PR Test Committer
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

                return GeneratedGpgKey(gnupgHome, keyId, armoredPublicKey, email)
            }

            // createCommit()과 동일한 checkout 관례(baseBranch가 없으면 origin/master로 폴백)를
            // 따르되, 실제 `git`/`gpg` 바이너리로 진짜 서명 커밋을 만든다(JGit의 CommitCommand는
            // 이 환경의 실제 gpg 서명 검증을 그대로 재현하기 어려워 GpgSignatureVerifierSpec과
            // 동일하게 git CLI를 그대로 사용한다).
            fun createSignedCommit(
                bareRepoDir: File,
                branch: String,
                filePath: String,
                content: String,
                commitMsg: String,
                generatedKey: GeneratedGpgKey,
                baseBranch: String = branch
            ) {
                val tempWorkingDir = Files.createTempDirectory("pr-it-signed-work-").toFile()
                try {
                    fun run(vararg cmd: String, env: Map<String, String> = emptyMap()) {
                        val process = ProcessBuilder(*cmd)
                            .directory(tempWorkingDir)
                            .redirectErrorStream(true)
                            .also { pb -> env.forEach { (k, v) -> pb.environment()[k] = v } }
                            .start()
                        val output = process.inputStream.bufferedReader().readText()
                        withClue(output) { process.waitFor() shouldBe 0 }
                    }

                    run("git", "init", "-q", "-b", branch)
                    run("git", "remote", "add", "origin", bareRepoDir.absolutePath)
                    run("git", "fetch", "-q", "origin")

                    val hasBaseBranch = ProcessBuilder("git", "rev-parse", "--verify", "refs/remotes/origin/$baseBranch")
                        .directory(tempWorkingDir).redirectErrorStream(true).start().waitFor() == 0
                    if (hasBaseBranch) {
                        run("git", "checkout", "-q", "-B", branch, "origin/$baseBranch")
                    } else {
                        val hasOriginMaster = ProcessBuilder("git", "rev-parse", "--verify", "refs/remotes/origin/master")
                            .directory(tempWorkingDir).redirectErrorStream(true).start().waitFor() == 0
                        if (hasOriginMaster) {
                            run("git", "checkout", "-q", "-B", branch, "origin/master")
                        }
                    }

                    val file = File(tempWorkingDir, filePath)
                    file.parentFile?.mkdirs()
                    file.writeText(content)

                    run("git", "add", filePath)
                    run("git", "config", "user.name", "PR Signed Tester")
                    run("git", "config", "user.email", generatedKey.email)
                    run("git", "config", "user.signingkey", generatedKey.keyId)
                    run("git", "config", "gpg.program", "gpg")
                    run(
                        "git", "commit", "-S", "-q", "-m", commitMsg,
                        env = mapOf("GNUPGHOME" to generatedKey.gnupgHome.absolutePath)
                    )
                    run("git", "push", "-f", "origin", "HEAD:refs/heads/$branch")
                } finally {
                    tempWorkingDir.deleteRecursively()
                }
            }

            it("1. 충돌이 없는 경우 - attemptMerge 및 merge 성공 검증") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                // 공통 조상 생성 (toProject의 master 브랜치에 test.txt 작성)
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                // toProject master 커밋을 fromProject master 로 동기화하여 공통 조상 해시 일치
                syncRepository(toBareDir, fromBareDir, "master")

                // 1) Target 프로젝트 (toProject) master 브랜치에 변경 사항 추가
                createCommit(toBareDir, "master", "test2.txt", "target modification", "Update target")

                // 2) Source 프로젝트 (fromProject) feature 브랜치에 변경 사항 추가
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "자동 머지 테스트 PR",
                        body = "충돌이 없는 PR입니다.",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // 3) attemptMerge 수행 - 충돌 미발생이어야 함
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                attemptResult.conflicts() shouldBe false
                attemptResult.gitCommits.size shouldBe 1
                attemptResult.gitCommits.first().getShortMessage() shouldBe "Update source"

                val updatedPrAfterAttempt = pullRequestRepository.findById(pr.id!!).orElse(null)
                updatedPrAfterAttempt.isConflict shouldBe false

                // 4) merge 수행 - 머지 완료 및 커밋 영속화 확인
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                val mergedPr = pullRequestRepository.findById(pr.id!!).orElse(null)
                mergedPr.state shouldBe State.MERGED
                mergedPr.isConflict shouldBe false
                mergedPr.mergedCommitIdTo shouldNotBe null
                mergedPr.receiver?.id shouldBe receiver.id

                // PullRequestCommit 엔티티 저장 검증
                val commits = pullRequestCommitRepository.findByPullRequest(mergedPr)
                commits.size shouldBe 1
                commits.first().commitMessage.trim() shouldBe "Update source"
                commits.first().state shouldBe PullRequestCommit.State.CURRENT

                // TASK-0423(P3-02 11라운드, "pr merge가 실제 브랜치 ref를 갱신하지 않는" 이월 결함) —
                // merge()는 refs/yobi/pull/{id}/merged 캐시 ref뿐 아니라 실제 대상 브랜치
                // (toProject의 refs/heads/master)도 병합 커밋으로 갱신해야 한다. 이래야
                // 병합 후 toProject를 clone/pull한 클라이언트가 실제로 병합 결과를 받는다 —
                // 예전에는 이 ref가 전혀 움직이지 않아 pull해도 병합 내용이 보이지 않았다.
                Git.open(toBareDir).use { git ->
                    git.repository.resolve("master")!!.name shouldBe mergedPr.mergedCommitIdTo
                }
            }

            // TASK-0424(P3-02 11라운드) — 실서버+실 yona-cli로 "이미 MERGED된 PR을 다시 merge"를
            // 실측하다가 발견: 가드가 없으면 merge()를 다시 호출할 때마다 대상 브랜치에 병합
            // 커밋이 계속 쌓였다(실측: 동일 PR로 `pr merge`를 두 번 호출하니 refs/heads/master에
            // 병합 커밋이 중복 2개). 이제 OPEN이 아닌 PR에 merge()를 호출하면 예외가 발생하고
            // 대상 브랜치가 더 이상 움직이지 않아야 한다.
            it("1-2. 이미 MERGED된 PR을 다시 merge하려 하면 예외가 발생하고 중복 머지 커밋이 생기지 않아야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "재머지 방지 테스트 PR",
                        body = "이미 머지된 PR 재머지 시도",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                pullRequestService.merge(pr.id!!, receiver)
                val refAfterFirstMerge = Git.open(toBareDir).use { it.repository.resolve("master")!!.name }

                shouldThrow<IllegalArgumentException> {
                    pullRequestService.merge(pr.id!!, receiver)
                }

                Git.open(toBareDir).use { git ->
                    git.repository.resolve("master")!!.name shouldBe refAfterFirstMerge
                }
            }

            // TASK-0424(P3-02 11라운드) — 실서버+실 yona-cli로 "이미 MERGED된 PR을 close/reopen"을
            // 실측하다가 발견: 가드가 없으면 물리적으로는 이미 병합 완료된 PR이 CLOSED/OPEN을
            // 오가며 상태만 바뀐다(실제로는 되돌릴 수 없는 병합인데도).
            it("1-3. 이미 MERGED된 PR을 close/reopen하려 하면 예외가 발생해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "머지 후 상태변경 방지 테스트 PR",
                        body = "이미 머지된 PR 상태변경 시도",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                pullRequestService.merge(pr.id!!, receiver)

                shouldThrow<IllegalArgumentException> {
                    pullRequestService.changeState(pr.id!!, State.CLOSED, receiver.loginId)
                }
                shouldThrow<IllegalArgumentException> {
                    pullRequestService.changeState(pr.id!!, State.OPEN, receiver.loginId)
                }

                pullRequestRepository.findById(pr.id!!).orElse(null).state shouldBe State.MERGED
            }

            // yona PullRequestMerger의 refs/yobi/pull/{id}/merged 캐시 재사용 대응 — getMergedTreeIfReusable()가
            // 이전 attemptMerge()가 만든 병합 커밋의 부모 2개가 이번 부모와 동일하면 재계산 없이 그 tree를
            // 재사용하는 분기는, 기존 테스트가 전부 동일 PR에 attemptMerge를 단 한 번만 호출해서 비어 있었다.
            it("1-1. 새 커밋 없이 attemptMerge를 연속 호출하면 캐시된 병합 tree를 재사용해도 동일한 결과를 반환해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature-reuse", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "캐시 재사용 테스트 PR",
                        body = "동일 부모로 재검사되는 PR입니다.",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature-reuse",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                val firstAttempt = pullRequestService.attemptMerge(pr.id!!)
                firstAttempt.conflicts() shouldBe false

                // 새 커밋 없이 동일 부모로 재검사 — getMergedTreeIfReusable()의 캐시 히트 분기를 탄다.
                val secondAttempt = pullRequestService.attemptMerge(pr.id!!)
                secondAttempt.conflicts() shouldBe false
                secondAttempt.gitCommits.size shouldBe firstAttempt.gitCommits.size
            }

            it("2. 충돌이 발생하는 경우 - attemptMerge 및 merge 실패 검증") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                // 공통 조상 생성
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                // 1) Target 프로젝트 (toProject) master 브랜치에 동일 파일(test.txt) 수정
                createCommit(toBareDir, "master", "test.txt", "hello common\ntarget edit", "Target conflict commit")

                // 2) Source 프로젝트 (fromProject) feature-conflict 브랜치에 동일 파일(test.txt) 충돌 수정
                createCommit(fromBareDir, "feature-conflict", "test.txt", "hello common\nsource edit", "Source conflict commit")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "충돌 머지 테스트 PR",
                        body = "충돌이 발생하는 PR입니다.",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature-conflict",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // 3) attemptMerge 수행 - 충돌 감지되어야 함
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                attemptResult.conflicts() shouldBe true

                val updatedPrAfterAttempt = pullRequestRepository.findById(pr.id!!).orElse(null)
                updatedPrAfterAttempt.isConflict shouldBe true

                // yona-wiki P3-02 13라운드(TASK-0430) — 12라운드가 실서버+실 git으로 수동
                // 확인만 하고 자동화하지 못한 채 이월한 항목. merge() 시도 전 대상 브랜치
                // (refs/heads/master) tip을 기록해두고, 충돌 머지 이후에도 이 ref가 전혀
                // 움직이지 않았는지 검증한다(TASK-0423이 고친 "실제 merge()만 대상 브랜치를
                // fast-forward한다" 경로가 충돌 시에는 아예 호출되지 않아야 함을 고정).
                val masterRefBeforeMerge = Git.open(toBareDir).use { it.repository.resolve("master")!!.name }

                // 4) merge 수행 - 머지 실패(충돌 상태 보존 및 MERGED 미변경)
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe true

                val mergedPr = pullRequestRepository.findById(pr.id!!).orElse(null)
                mergedPr.state shouldBe State.OPEN
                mergedPr.isConflict shouldBe true

                val masterRefAfterMerge = Git.open(toBareDir).use { it.repository.resolve("master")!!.name }
                masterRefAfterMerge shouldBe masterRefBeforeMerge
            }

            it("3. 이슈 자동 닫기(Issue Auto-Close) 연동 검증") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                // 공통 조상 생성
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                // 이슈 3개 생성 (이슈 번호는 1, 2, 3으로 순차 생성됨)
                val issue1 = issueService.createIssue(
                    Issue(title = "이슈 1", body = "첫 번째 이슈", project = toProject),
                    receiver, null, null, null
                )
                val issue2 = issueService.createIssue(
                    Issue(title = "이슈 2", body = "두 번째 이슈", project = toProject),
                    receiver, null, null, null
                )
                val issue3 = issueService.createIssue(
                    Issue(title = "이슈 3", body = "세 번째 이슈", project = toProject),
                    receiver, null, null, null
                )

                issue1.number shouldBe 1L
                issue2.number shouldBe 2L
                issue3.number shouldBe 3L

                // Target 프로젝트 (toProject) master 브랜치에 변경 사항 추가
                createCommit(toBareDir, "master", "test2.txt", "target modification", "Update target")

                // Source 프로젝트 (fromProject) feature 브랜치에 변경 사항 추가 (커밋 메시지에 Resolves #2 작성)
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source\n\nResolves #2")

                // PR 본문에 Closes #1 작성하여 PullRequest 생성
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "자동 머지 및 이슈 닫기 테스트 PR",
                        body = "이 PR은 closes #1 을 해결합니다.",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // attemptMerge 수행
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                attemptResult.conflicts() shouldBe false

                // merge 수행
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                // 트랜잭션 격리 및 비동기 우회를 위해 리스너의 이슈 자동 닫기 로직을 직접 동기 호출
                pullRequestMergeEventListener.closeReferredIssues(pr, receiver.loginId)

                val updatedIssue1 = issueRepository.findById(issue1.id!!).get()
                val updatedIssue2 = issueRepository.findById(issue2.id!!).get()
                val updatedIssue3 = issueRepository.findById(issue3.id!!).get()

                updatedIssue1.state shouldBe State.CLOSED
                updatedIssue2.state shouldBe State.CLOSED
                updatedIssue3.state shouldBe State.OPEN
            }

            it("4. 리뷰어 참여 및 해제 기능 테스트") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "리뷰어 테스트 PR",
                        body = "리뷰어 추가 삭제 검증용 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // 1) 리뷰어 추가 검증
                pullRequestService.addReviewer(pr.id!!, receiver)
                
                val prAfterAdd = pullRequestRepository.findById(pr.id!!).get()
                prAfterAdd.reviewers.size shouldBe 1
                prAfterAdd.reviewers.first().id shouldBe receiver.id

                // 2) 리뷰어 해제 검증
                pullRequestService.removeReviewer(pr.id!!, receiver)

                val prAfterRemove = pullRequestRepository.findById(pr.id!!).get()
                prAfterRemove.reviewers.size shouldBe 0
            }

            it("리뷰어 추가/해제 시 NotificationEvent와 PullRequestEvent가 발행되어야 한다(P1-49)") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "리뷰어 알림 테스트 PR",
                        body = "리뷰어 추가/해제 알림 검증용 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                pullRequestService.addReviewer(pr.id!!, receiver)

                val notiEventsAfterAdd = notificationEventRepository.findAll()
                notiEventsAfterAdd.size shouldBe 1
                notiEventsAfterAdd.first().eventType shouldBe EventType.PULL_REQUEST_REVIEW_STATE_CHANGED
                notiEventsAfterAdd.first().newValue shouldBe "DONE"
                // yona NotificationEvent.afterReviewed()의 title = formatReplyTitle(pullRequest) 대응 (P1-63).
                // 리뷰어 참여/취소 여부와 무관하게 다른 PR 알림들과 동일한 "Re: [project] title (#number)" 포맷.
                notiEventsAfterAdd.first().title shouldBe "Re: [${toProject.name}] ${pr.title} (#${pr.number})"

                val prEventsAfterAdd = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr)
                prEventsAfterAdd.size shouldBe 1
                prEventsAfterAdd.first().eventType shouldBe EventType.PULL_REQUEST_REVIEW_STATE_CHANGED
            }

            // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 담당자 지정/해제.
            // 레거시 Play `yona`의 PullRequest.java에는 이 개념 자체가 없었음을 확인했다(신규 기능
            // 확장). IssueServiceImpl.updateIssue()와 동일하게 담당자를 바꿀 때마다 새 Assignee를
            // 만든다.
            it("PR 담당자를 지정하고 해제할 수 있어야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "담당자 테스트 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                val updated = pullRequestService.setAssignee(pr.id!!, receiver)
                updated.assignee shouldNotBe null
                updated.assignee?.user?.id shouldBe receiver.id
                updated.assignee?.project?.id shouldBe toProject.id

                val cleared = pullRequestService.setAssignee(pr.id!!, null)
                cleared.assignee shouldBe null
            }

            it("존재하지 않는 PR의 담당자를 지정하려 하면 예외가 발생해야 한다") {
                shouldThrow<IllegalArgumentException> {
                    pullRequestService.setAssignee(999999L, receiver)
                }
            }

            // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 라벨 추가/제거. 라벨
            // 정의 자체는 만들지 않고(project.labels/IssueLabel과 무관한 신규 개념 도입 없음) 이미
            // 존재하는 IssueLabel만 참조한다.
            it("PR에 라벨을 추가하고 제거할 수 있어야 한다") {
                val category = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "type", project = toProject)
                )
                val label = issueLabelRepository.save(
                    IssueLabel(name = "bug", color = "red", category = category, project = toProject)
                )
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "라벨 테스트 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                val added = pullRequestService.addLabel(pr.id!!, label.id!!)
                added.labels.map { it.id } shouldBe listOf(label.id)

                val removed = pullRequestService.removeLabel(pr.id!!, label.id!!)
                removed.labels.size shouldBe 0
            }

            it("존재하지 않는 라벨을 PR에 추가하려 하면 예외가 발생해야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "존재하지 않는 라벨 테스트 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                shouldThrow<IllegalArgumentException> {
                    pullRequestService.addLabel(pr.id!!, 999999L)
                }
            }

            // yona-wiki P3-02 14라운드(실서버 REST API로 재현한 실제 IDOR, TASK-0426/이슈 라벨
            // IDOR와 같은 근본원인) — addLabel()이 labelId로만 조회하고 그 라벨이 이 PR의
            // 프로젝트(toProject) 소속인지 검증하지 않았다. `POST .../pull-requests/{number}/labels`가
            // labelId를 그대로 받는 REST API라, 자기 프로젝트에 PR 라벨 추가 권한만 있으면 labelId를
            // 다른(멤버가 아닌 PRIVATE) 프로젝트의 라벨 번호로 바꿔 그 라벨을 노출·연결할 수 있었다.
            it("다른 프로젝트 소속 라벨을 PR에 추가하려 하면 예외가 발생하고 매핑되지 않아야 한다") {
                val otherProject = projectRepository.save(
                    Project(name = "다른프로젝트-${UUID.randomUUID()}", owner = "someone-else", projectScope = ProjectScope.PRIVATE)
                )
                val otherCategory = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "다른프로젝트카테고리", project = otherProject)
                )
                val otherLabel = issueLabelRepository.save(
                    IssueLabel(name = "남의라벨", color = "black", category = otherCategory, project = otherProject)
                )
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "교차 프로젝트 라벨 시도 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                shouldThrow<IllegalArgumentException> {
                    pullRequestService.addLabel(pr.id!!, otherLabel.id!!)
                }

                pullRequestRepository.findById(pr.id!!).get().labels.size shouldBe 0
            }

            it("5. 최소 리뷰어 수 미달 시 머지 실패 검증") {
                // 프로젝트 설정 변경: 리뷰어 수 제한 설정 활성화, 최소 리뷰어 수 1명 요구
                toProject.isUsingReviewerCount = true
                toProject.defaultReviewerCount = 1
                projectRepository.save(toProject)

                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                createCommit(toBareDir, "master", "test2.txt", "target modification", "Update target")
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "최소 리뷰어 미달 병합 제어 테스트 PR",
                        body = "최소 리뷰어가 부족할 때 병합 차단 검증",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // attemptMerge는 병합 시도가 아니므로 통과해야 함
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                attemptResult.conflicts() shouldBe false

                // 1) 리뷰어가 전혀 등록되지 않은 상태에서 merge 호출 시 LackingReviewerException 예외가 던져져야 함
                io.kotest.assertions.throwables.shouldThrow<LackingReviewerException> {
                    pullRequestService.merge(pr.id!!, receiver)
                }

                // 2) 리뷰어를 추가하여 기준을 만족시킨 뒤 merge 호출 시 성공해야 함
                pullRequestService.addReviewer(pr.id!!, receiver)

                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                val mergedPr = pullRequestRepository.findById(pr.id!!).get()
                mergedPr.state shouldBe State.MERGED
            }

            // yona-wiki P3-04(브랜치 보호) Step 4/5 — legacy에 대응 로직이 전혀 없는 신규 인프라.
            // toBranch(toProject의 "master")에 ProtectedBranch 규칙이 걸려 있을 때
            // PullRequestServiceImpl.merge()가 그 규칙을 검사하는지 확인한다. require_pull_request는
            // merge()가 정의상 항상 PullRequest 객체를 통해서만 호출되므로(직접 push 경로가 아님)
            // 병합 자체를 막을 대상이 없다 — Step1 스파이크 결론과 동일하게 require_approvals는
            // 항상 통과 처리됨을 테스트로 고정한다. require_signed_commits는 더 이상 no-op이
            // 아니다 — yona-wiki P3-03/P3-04 연결 작업(2026-09-07, P3-03 4부 완료 로그에 "후속
            // 과제"로 명시적으로 남겨뒀던 항목)으로 실제 검사가 연결되었다(아래 별도 describe).
            describe("5-1. 브랜치 보호 정책(ProtectedBranch) 검증") {
                fun makeOpenPrWithMergeableCommits(): PullRequest {
                    val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                    val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                    createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                    syncRepository(toBareDir, fromBareDir, "master")
                    createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                    return pullRequestRepository.save(
                        PullRequest(
                            title = "브랜치 보호 검증 PR",
                            body = "ProtectedBranch 체크 검증용",
                            toProject = toProject,
                            fromProject = fromProject,
                            toBranch = "refs/heads/master",
                            fromBranch = "refs/heads/feature",
                            contributor = contributor,
                            receiver = receiver,
                            created = Instant.now(),
                            state = State.OPEN
                        )
                    )
                }

                fun makeManager(user: User) {
                    val role = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                        roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                    }
                    projectUserRepository.save(ProjectUser(user = user, project = toProject, role = role))
                }

                it("restrict_push_to 목록에 없는 사용자가 병합하면 BranchProtectionException이 발생해야 한다") {
                    protectedBranchRepository.save(
                        ProtectedBranch(project = toProject, branchPattern = "master", restrictPushTo = "다른사람")
                    )
                    val pr = makeOpenPrWithMergeableCommits()

                    io.kotest.assertions.throwables.shouldThrow<BranchProtectionException> {
                        pullRequestService.merge(pr.id!!, receiver)
                    }

                    pullRequestRepository.findById(pr.id!!).get().state shouldBe State.OPEN
                }

                it("restrict_push_to 목록에 있는 사용자는 정상적으로 병합할 수 있어야 한다") {
                    protectedBranchRepository.save(
                        ProtectedBranch(project = toProject, branchPattern = "master", restrictPushTo = receiver.loginId)
                    )
                    val pr = makeOpenPrWithMergeableCommits()

                    val mergeResult = pullRequestService.merge(pr.id!!, receiver)

                    mergeResult.conflicts() shouldBe false
                    pullRequestRepository.findById(pr.id!!).get().state shouldBe State.MERGED
                }

                it("branch_pattern이 매칭되지 않는 규칙은 적용되지 않아야 한다") {
                    protectedBranchRepository.save(
                        ProtectedBranch(project = toProject, branchPattern = "release/*", restrictPushTo = "다른사람")
                    )
                    val pr = makeOpenPrWithMergeableCommits()

                    val mergeResult = pullRequestService.merge(pr.id!!, receiver)

                    mergeResult.conflicts() shouldBe false
                }

                it("admins_can_bypass=true(기본값)이면 restrict_push_to에 없는 프로젝트 매니저도 병합할 수 있어야 한다") {
                    makeManager(receiver)
                    protectedBranchRepository.save(
                        ProtectedBranch(
                            project = toProject, branchPattern = "master",
                            restrictPushTo = "다른사람", adminsCanBypass = true
                        )
                    )
                    val pr = makeOpenPrWithMergeableCommits()

                    val mergeResult = pullRequestService.merge(pr.id!!, receiver)

                    mergeResult.conflicts() shouldBe false
                    pullRequestRepository.findById(pr.id!!).get().state shouldBe State.MERGED
                }

                it("admins_can_bypass=false이면 프로젝트 매니저도 restrict_push_to 제한을 우회할 수 없어야 한다") {
                    makeManager(receiver)
                    protectedBranchRepository.save(
                        ProtectedBranch(
                            project = toProject, branchPattern = "master",
                            restrictPushTo = "다른사람", adminsCanBypass = false
                        )
                    )
                    val pr = makeOpenPrWithMergeableCommits()

                    io.kotest.assertions.throwables.shouldThrow<BranchProtectionException> {
                        pullRequestService.merge(pr.id!!, receiver)
                    }
                }

                // yona-wiki P3-04 Step1 스파이크 결론(계획 문서 참고) 회귀 고정 — require_pull_request는
                // merge()가 정의상 PullRequest를 통해서만 호출되므로 정상적인 PR 병합을 막지 않아야
                // 한다. require_signed_commits는 이 테스트에서 제외한다 — 아래 별도 describe("5-2")에서
                // 실제로 검사됨을 검증한다. require_approvals는 P3-15 연결 작업(2026-09-07)으로 더 이상
                // 항상 통과가 아니다 — 아래 별도 describe("5-3")에서 실제로 검사됨을 검증한다(이 테스트는
                // requireApprovals=0인 채로 남겨 require_pull_request만 회귀 고정한다).
                it("require_pull_request가 켜져 있어도 정상 PR 병합은 항상 통과해야 한다") {
                    protectedBranchRepository.save(
                        ProtectedBranch(
                            project = toProject, branchPattern = "master",
                            requirePullRequest = true
                        )
                    )
                    val pr = makeOpenPrWithMergeableCommits()

                    val mergeResult = pullRequestService.merge(pr.id!!, receiver)

                    mergeResult.conflicts() shouldBe false
                    pullRequestRepository.findById(pr.id!!).get().state shouldBe State.MERGED
                }
            }

            // yona-wiki P3-03/P3-04 연결 작업(2026-09-07) — P3-03 4부 완료 로그에 "후속 과제"로
            // 명시적으로 남겨뒀던 갭. require_signed_commits가 PullRequestServiceImpl.merge()에서
            // 실제로 검사되는지 검증한다.
            describe("5-2. require_signed_commits(GPG 서명 검증 연결) 검증") {
                fun makeOpenPrWithMergeableCommits(): PullRequest {
                    val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                    val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                    createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                    syncRepository(toBareDir, fromBareDir, "master")
                    createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                    return pullRequestRepository.save(
                        PullRequest(
                            title = "GPG 서명 검증 연결 검증 PR",
                            body = "require_signed_commits 실제 검사 검증용",
                            toProject = toProject,
                            fromProject = fromProject,
                            toBranch = "refs/heads/master",
                            fromBranch = "refs/heads/feature",
                            contributor = contributor,
                            receiver = receiver,
                            created = Instant.now(),
                            state = State.OPEN
                        )
                    )
                }

                it("서명되지 않은 커밋이 있는 브랜치는 require_signed_commits가 켜져 있으면 병합이 거부되어야 한다") {
                    protectedBranchRepository.save(
                        ProtectedBranch(project = toProject, branchPattern = "master", requireSignedCommits = true, adminsCanBypass = false)
                    )
                    val pr = makeOpenPrWithMergeableCommits()

                    shouldThrow<BranchProtectionException> {
                        pullRequestService.merge(pr.id!!, receiver)
                    }

                    pullRequestRepository.findById(pr.id!!).get().state shouldBe State.OPEN
                }

                it("require_signed_commits가 켜져 있어도 실제로 서명되고 검증되는 커밋이면 병합이 성공해야 한다") {
                    if (!gpgAvailable()) return@it

                    val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                    val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                    createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                    syncRepository(toBareDir, fromBareDir, "master")

                    val generatedKey = generateGpgKey("pr-sig-ok")
                    // author 이메일이 GPG 키의 (계정 소유로 인증된) UID 이메일과 일치해야 VERIFIED로
                    // 판정된다 — GpgSignatureVerifierSpec/YonaMinaSshServerIntegrationSpec과 동일한 정책.
                    val signer = userRepository.save(
                        User(loginId = "pr-sig-ok-signer-${System.currentTimeMillis()}", name = "PR서명유저", email = generatedKey.email)
                    )
                    gpgKeyService.create(signer, generatedKey.armoredPublicKey)

                    createSignedCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source", generatedKey)

                    protectedBranchRepository.save(
                        ProtectedBranch(project = toProject, branchPattern = "master", requireSignedCommits = true, adminsCanBypass = false)
                    )

                    val pr = pullRequestRepository.save(
                        PullRequest(
                            title = "GPG 서명 검증 성공 PR",
                            body = "실제 서명되고 검증되는 커밋 병합 성공 검증용",
                            toProject = toProject,
                            fromProject = fromProject,
                            toBranch = "refs/heads/master",
                            fromBranch = "refs/heads/feature",
                            contributor = contributor,
                            receiver = receiver,
                            created = Instant.now(),
                            state = State.OPEN
                        )
                    )

                    val mergeResult = pullRequestService.merge(pr.id!!, receiver)

                    mergeResult.conflicts() shouldBe false
                    pullRequestRepository.findById(pr.id!!).get().state shouldBe State.MERGED
                }
            }

            // yona-wiki P3-15(PR 승인/변경요청 워크플로) — GitHub의 Approve/Request changes/Comment에
            // 대응하는 PullRequestReview 신규 도메인 로직 + require_approvals 연결 검증.
            describe("5-3. PullRequestReview(승인/변경요청/코멘트 판정) 검증") {
                fun makeOpenPr(): PullRequest {
                    return pullRequestRepository.save(
                        PullRequest(
                            title = "리뷰 판정 검증 PR",
                            body = "PullRequestReview 검증용",
                            toProject = toProject,
                            fromProject = fromProject,
                            toBranch = "refs/heads/master",
                            fromBranch = "refs/heads/feature",
                            contributor = contributor,
                            receiver = receiver,
                            created = Instant.now(),
                            state = State.OPEN
                        )
                    )
                }

                it("submitReview는 새 PullRequestReview를 저장하고 getReviews로 오래된 순 조회할 수 있어야 한다") {
                    val pr = makeOpenPr()

                    val review = pullRequestService.submitReview(
                        pr.id!!, receiver, PullRequestReview.ReviewState.APPROVE, "LGTM"
                    )

                    review.id shouldNotBe null
                    review.state shouldBe PullRequestReview.ReviewState.APPROVE
                    review.body shouldBe "LGTM"

                    val reviews = pullRequestService.getReviews(pr.id!!)
                    reviews.size shouldBe 1
                    reviews[0].reviewer.id shouldBe receiver.id
                }

                it("같은 리뷰어가 재판정하면 매 번 새 이력이 추가되어야 한다(update가 아니라 insert)") {
                    val pr = makeOpenPr()

                    pullRequestService.submitReview(pr.id!!, receiver, PullRequestReview.ReviewState.REQUEST_CHANGES, "고쳐주세요")
                    pullRequestService.submitReview(pr.id!!, receiver, PullRequestReview.ReviewState.APPROVE, "이제 됐습니다")

                    val reviews = pullRequestService.getReviews(pr.id!!)
                    reviews.size shouldBe 2
                    reviews.map { it.state } shouldBe listOf(
                        PullRequestReview.ReviewState.REQUEST_CHANGES,
                        PullRequestReview.ReviewState.APPROVE
                    )
                }

                it("getLatestReviewStates는 리뷰어별 가장 최근 APPROVE/REQUEST_CHANGES만 반영해야 한다") {
                    val pr = makeOpenPr()
                    val anotherReviewer = userRepository.save(
                        User(loginId = "another-${System.currentTimeMillis()}", name = "다른리뷰어", email = "another-${System.currentTimeMillis()}@yona.io")
                    )

                    pullRequestService.submitReview(pr.id!!, receiver, PullRequestReview.ReviewState.APPROVE, null)
                    pullRequestService.submitReview(pr.id!!, receiver, PullRequestReview.ReviewState.REQUEST_CHANGES, null)
                    pullRequestService.submitReview(pr.id!!, anotherReviewer, PullRequestReview.ReviewState.APPROVE, null)

                    val latest = pullRequestService.getLatestReviewStates(pr.id!!)

                    latest[receiver.id] shouldBe PullRequestReview.ReviewState.REQUEST_CHANGES
                    latest[anotherReviewer.id!!] shouldBe PullRequestReview.ReviewState.APPROVE
                }

                it("COMMENT 전용 판정은 정책 판단(getLatestReviewStates)에서 제외되어야 한다") {
                    val pr = makeOpenPr()

                    pullRequestService.submitReview(pr.id!!, receiver, PullRequestReview.ReviewState.APPROVE, null)
                    pullRequestService.submitReview(pr.id!!, receiver, PullRequestReview.ReviewState.COMMENT, "추가 의견")

                    val latest = pullRequestService.getLatestReviewStates(pr.id!!)

                    // 가장 최근 판정은 COMMENT이지만, COMMENT는 정책 판단에서 제외되므로 그 이전의
                    // APPROVE가 여전히 유효해야 한다(GitHub 방식 — Comment는 기존 승인 상태를 바꾸지 않는다).
                    latest[receiver.id] shouldBe PullRequestReview.ReviewState.APPROVE
                }

                it("자기 자신의 PR을 APPROVE하려 하면 SelfReviewException이 발생해야 한다") {
                    val pr = makeOpenPr()

                    shouldThrow<SelfReviewException> {
                        pullRequestService.submitReview(pr.id!!, contributor, PullRequestReview.ReviewState.APPROVE, null)
                    }
                }

                it("자기 자신의 PR을 REQUEST_CHANGES하려 해도 SelfReviewException이 발생해야 한다") {
                    val pr = makeOpenPr()

                    shouldThrow<SelfReviewException> {
                        pullRequestService.submitReview(pr.id!!, contributor, PullRequestReview.ReviewState.REQUEST_CHANGES, null)
                    }
                }

                it("자기 자신의 PR에는 COMMENT는 허용되어야 한다(GitHub도 코멘트 자체는 막지 않는다)") {
                    val pr = makeOpenPr()

                    val review = pullRequestService.submitReview(pr.id!!, contributor, PullRequestReview.ReviewState.COMMENT, "내 PR에 코멘트")

                    review.id shouldNotBe null
                }

                describe("require_approvals 연결(checkApprovalsForMerge) 검증") {
                    fun makeOpenPrWithMergeableCommits(): PullRequest {
                        val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                        val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                        createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                        syncRepository(toBareDir, fromBareDir, "master")
                        createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                        return makeOpenPr()
                    }

                    it("승인이 부족하면 BranchProtectionException으로 병합이 거부되어야 한다") {
                        protectedBranchRepository.save(
                            ProtectedBranch(project = toProject, branchPattern = "master", requireApprovals = 1)
                        )
                        val pr = makeOpenPrWithMergeableCommits()

                        shouldThrow<BranchProtectionException> {
                            pullRequestService.merge(pr.id!!, receiver)
                        }

                        pullRequestRepository.findById(pr.id!!).get().state shouldBe State.OPEN
                    }

                    it("요구 승인 수를 채우면 병합이 성공해야 한다") {
                        protectedBranchRepository.save(
                            ProtectedBranch(project = toProject, branchPattern = "master", requireApprovals = 1)
                        )
                        val pr = makeOpenPrWithMergeableCommits()
                        pullRequestService.submitReview(pr.id!!, receiver, PullRequestReview.ReviewState.APPROVE, "승인합니다")

                        val mergeResult = pullRequestService.merge(pr.id!!, receiver)

                        mergeResult.conflicts() shouldBe false
                        pullRequestRepository.findById(pr.id!!).get().state shouldBe State.MERGED
                    }

                    it("승인 수를 채워도 최신 판정이 REQUEST_CHANGES인 리뷰어가 있으면 무조건 병합이 거부되어야 한다") {
                        protectedBranchRepository.save(
                            ProtectedBranch(project = toProject, branchPattern = "master", requireApprovals = 1)
                        )
                        val pr = makeOpenPrWithMergeableCommits()
                        val anotherReviewer = userRepository.save(
                            User(loginId = "reqchg-${System.currentTimeMillis()}", name = "변경요청자", email = "reqchg-${System.currentTimeMillis()}@yona.io")
                        )
                        pullRequestService.submitReview(pr.id!!, receiver, PullRequestReview.ReviewState.APPROVE, "승인합니다")
                        pullRequestService.submitReview(pr.id!!, anotherReviewer, PullRequestReview.ReviewState.REQUEST_CHANGES, "고쳐주세요")

                        shouldThrow<BranchProtectionException> {
                            pullRequestService.merge(pr.id!!, receiver)
                        }

                        pullRequestRepository.findById(pr.id!!).get().state shouldBe State.OPEN
                    }

                    it("admins_can_bypass=true(기본값)이면 승인이 부족해도 프로젝트 매니저는 병합할 수 있어야 한다") {
                        val role = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                            roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                        }
                        projectUserRepository.save(ProjectUser(user = receiver, project = toProject, role = role))
                        protectedBranchRepository.save(
                            ProtectedBranch(project = toProject, branchPattern = "master", requireApprovals = 1, adminsCanBypass = true)
                        )
                        val pr = makeOpenPrWithMergeableCommits()

                        val mergeResult = pullRequestService.merge(pr.id!!, receiver)

                        mergeResult.conflicts() shouldBe false
                        pullRequestRepository.findById(pr.id!!).get().state shouldBe State.MERGED
                    }

                    it("requireApprovals=0(기본값)이면 승인 여부와 무관하게 병합이 통과해야 한다") {
                        protectedBranchRepository.save(
                            ProtectedBranch(project = toProject, branchPattern = "master", requireApprovals = 0)
                        )
                        val pr = makeOpenPrWithMergeableCommits()

                        val mergeResult = pullRequestService.merge(pr.id!!, receiver)

                        mergeResult.conflicts() shouldBe false
                        pullRequestRepository.findById(pr.id!!).get().state shouldBe State.MERGED
                    }
                }
            }

            it("6. 풀 리퀘스트 변경 사항(getDiff) 추출 기능 테스트") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                createCommit(toBareDir, "master", "test2.txt", "target modification", "Update target")
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "코드 비교 테스트 PR",
                        body = "코드 비교 검증용 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // attemptMerge를 해서 pr.lastCommitId 등을 세팅
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                val savedPr = pullRequestRepository.findById(pr.id!!).get()

                // 1) 전체 Diff 가져오기 검증
                val diffs = pullRequestService.getDiff(savedPr)
                diffs.size shouldNotBe 0
                diffs.any { it.pathB == "test3.txt" } shouldBe true

                // 2) 특정 커밋 Diff 가져오기 검증
                val lastCommitId = savedPr.lastCommitId!!
                val specificDiffs = pullRequestService.getDiff(savedPr, lastCommitId)
                specificDiffs.size shouldNotBe 0
                specificDiffs.any { it.pathB == "test3.txt" } shouldBe true
            }

            it("병합된 PR의 원본 브랜치를 삭제하면 lastCommitId가 기록되고 브랜치가 사라져야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature-to-delete", "delete-me.txt", "content", "브랜치 삭제 테스트용 커밋")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "브랜치 삭제 테스트 PR",
                        body = "...",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature-to-delete",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.MERGED
                    )
                )

                repositoryService.getRepository(fromProject).getBranches()
                    .any { it.name == "refs/heads/feature-to-delete" } shouldBe true

                val updated = pullRequestService.deleteFromBranch(pr.id!!)

                updated.lastCommitId shouldNotBe null
                repositoryService.getRepository(fromProject).getBranches()
                    .any { it.name == "refs/heads/feature-to-delete" } shouldBe false
            }

            it("병합되지 않은 PR의 브랜치는 삭제할 수 없어야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature-open", "open.txt", "content", "미병합 PR 테스트용 커밋")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "미병합 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-open",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                io.kotest.assertions.throwables.shouldThrow<InvalidBranchOperationException> {
                    pullRequestService.deleteFromBranch(pr.id!!)
                }
            }

            it("삭제된 브랜치를 restoreFromBranch로 복원하면 동일한 커밋으로 다시 존재해야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature-to-restore", "restore-me.txt", "content", "브랜치 복원 테스트용 커밋")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "브랜치 복원 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-to-restore",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.MERGED
                    )
                )

                val deleted = pullRequestService.deleteFromBranch(pr.id!!)
                repositoryService.getRepository(fromProject).getBranches()
                    .any { it.name == "refs/heads/feature-to-restore" } shouldBe false

                val savedAfterDelete = pullRequestRepository.findById(pr.id!!).get()
                pullRequestService.restoreFromBranch(savedAfterDelete.id!!)

                val restoredBranch = repositoryService.getRepository(fromProject).getBranches()
                    .firstOrNull { it.name == "refs/heads/feature-to-restore" }

                restoredBranch shouldNotBe null
                restoredBranch!!.headCommit.getId() shouldBe deleted.lastCommitId
            }

            it("PR을 생성하면 NotificationEvent(NEW_PULL_REQUEST)와 PullRequestEvent가 모두 생성되어야 한다(P1-39)") {
                // NotificationEventRecorder(P1-27)는 legacy와 동일하게 수신자가 없으면 저장하지 않으므로
                // (contributor 본인은 수신자에서 제외된다), 실제 수신자가 될 프로젝트 감시자를 한 명 둔다.
                watchRepository.save(Watch(user = receiver, resourceType = ResourceType.PROJECT, resourceId = toProject.id.toString()))

                val created = pullRequestService.createPullRequest(
                    title = "신규 PR 이벤트 테스트",
                    body = "본문 내용",
                    fromProjectId = fromProject.id!!,
                    toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/master",
                    toBranch = "refs/heads/master",
                    contributor = contributor
                )

                val notiEvents = notificationEventRepository.findAll()
                notiEvents.size shouldBe 1
                notiEvents.first().eventType shouldBe EventType.NEW_PULL_REQUEST
                notiEvents.first().newValue shouldBe "본문 내용"

                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(created)
                prEvents.size shouldBe 1
                prEvents.first().eventType shouldBe EventType.NEW_PULL_REQUEST
                prEvents.first().senderLoginId shouldBe contributor.loginId
            }

            // yona NotificationEvent.java:1425-1428 getDefaultReceivers(pullRequest)의
            // getMentionedUsers(body) 대응 (P1-127). 신규 PR 본문의 @멘션도 알림 수신자에
            // 포함되어야 한다.
            it("PR 본문에 멘션이 포함되어 있으면 멘션된 사용자도 신규 PR 알림 수신자에 포함되어야 한다") {
                val mentioned = userRepository.save(User(loginId = "pr-mentioned", name = "PR멘션대상", email = "pr-mentioned@yona.io"))

                pullRequestService.createPullRequest(
                    title = "멘션 포함 PR",
                    body = "@pr-mentioned 님 리뷰 부탁드립니다.",
                    fromProjectId = fromProject.id!!,
                    toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/master",
                    toBranch = "refs/heads/master",
                    contributor = contributor
                )

                val notiEvents = notificationEventRepository.findAll()
                notiEvents.size shouldBe 1
                notiEvents.first().receivers.map { it.loginId } shouldBe listOf("pr-mentioned")
            }

            it("PR 상태를 변경하면 NotificationEvent와 PullRequestEvent가 모두 생성되어야 한다") {
                // NotificationEventRecorder(P1-27) + PULL_REQUEST_STATE_CHANGED의 실제 감시자 알림 보강
                // (legacy NotificationEvent.getReceivers(sender, pullRequest)) 대응 — contributor 본인이
                // 상태를 바꾸면 본인은 수신자에서 빠지므로, 실제 수신자가 될 프로젝트 감시자를 한 명 둔다.
                watchRepository.save(Watch(user = receiver, resourceType = ResourceType.PROJECT, resourceId = toProject.id.toString()))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "상태 변경 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-state",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                val updated = pullRequestService.changeState(pr.id!!, State.CLOSED, contributor.loginId)

                updated.state shouldBe State.CLOSED

                val notiEvents = notificationEventRepository.findAll()
                notiEvents.size shouldBe 1
                notiEvents.first().eventType shouldBe EventType.PULL_REQUEST_STATE_CHANGED
                notiEvents.first().oldValue shouldBe State.OPEN.toString()
                notiEvents.first().newValue shouldBe State.CLOSED.toString()

                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(updated)
                prEvents.size shouldBe 1
                prEvents.first().eventType shouldBe EventType.PULL_REQUEST_STATE_CHANGED
                prEvents.first().senderLoginId shouldBe contributor.loginId
            }

            it("PULL_REQUEST_STATE_CHANGED는 30초 내 연속 변경이어도 상쇄되지 않고 모두 남아야 한다(P1-40)") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "상태 병합 제외 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-no-merge",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                pullRequestService.changeState(pr.id!!, State.CLOSED, contributor.loginId)
                pullRequestService.changeState(pr.id!!, State.REJECTED, contributor.loginId)

                // yona PullRequestEvent.needToDeleteEvent는 PULL_REQUEST_REVIEW_STATE_CHANGED 타입에만 적용되므로
                // 상태 변경 이벤트는 IssueEvent(P1-38)와 달리 병합되지 않고 둘 다 그대로 남는다.
                pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr).size shouldBe 2
            }

            it("동일한 상태로 변경을 시도하면 이벤트를 생성하지 않아야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "동일 상태 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-same",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                pullRequestService.changeState(pr.id!!, State.OPEN, contributor.loginId)

                notificationEventRepository.findAll().size shouldBe 0
                pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr).size shouldBe 0
            }

            // yona actors/PullRequestActor.processPullRequestMerging() 대응 (P1-52).
            it("관련 PR 재검사(processMergeCheck) 중 새 커밋이 발견되면 PullRequestCommit 저장·PullRequestEvent 기록·리뷰어 초기화·알림 발행이 모두 이뤄져야 한다") {
                watchRepository.save(Watch(user = receiver, resourceType = ResourceType.PROJECT, resourceId = toProject.id.toString()))

                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature", "test2.txt", "new feature", "Add feature")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "커밋 변경 재검사 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN,
                        reviewers = mutableSetOf(receiver)
                    )
                )

                val result = pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)

                result.hasDiffCommits() shouldBe true
                result.newCommits.size shouldBe 1

                val savedCommits = pullRequestCommitRepository.findByPullRequestAndState(pr, PullRequestCommit.State.CURRENT)
                savedCommits.size shouldBe 1
                savedCommits.first().commitMessage.trim() shouldBe "Add feature"

                val updated = pullRequestRepository.findById(pr.id!!).orElse(null)
                updated.reviewers.size shouldBe 0

                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr)
                prEvents.size shouldBe 1
                prEvents.first().eventType shouldBe EventType.PULL_REQUEST_COMMIT_CHANGED
                prEvents.first().senderLoginId shouldBe contributor.loginId

                val notiEvents = notificationEventRepository.findAll()
                notiEvents.size shouldBe 1
                notiEvents.first().eventType shouldBe EventType.PULL_REQUEST_COMMIT_CHANGED
            }

            it("PR 생성 시(isNewPullRequest=true)에는 커밋 변경 알림은 생략되지만 PullRequestEvent는 기록되어야 한다") {
                watchRepository.save(Watch(user = receiver, resourceType = ResourceType.PROJECT, resourceId = toProject.id.toString()))

                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature-new", "test2.txt", "new feature", "Add feature")

                val created = pullRequestService.createPullRequest(
                    title = "생성 시점 커밋 이벤트 테스트",
                    body = "본문",
                    fromProjectId = fromProject.id!!,
                    toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/feature-new",
                    toBranch = "refs/heads/master",
                    contributor = contributor
                )

                // NotificationEvent는 NEW_PULL_REQUEST 하나뿐이어야 한다(커밋 변경 알림은 생성 시점엔 생략).
                val notiEvents = notificationEventRepository.findAll()
                notiEvents.size shouldBe 1
                notiEvents.first().eventType shouldBe EventType.NEW_PULL_REQUEST

                // PullRequestEvent는 PULL_REQUEST_COMMIT_CHANGED + NEW_PULL_REQUEST 둘 다 남아야 한다.
                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(created)
                prEvents.size shouldBe 2
                prEvents.map { it.eventType } shouldBe listOf(
                    EventType.PULL_REQUEST_COMMIT_CHANGED,
                    EventType.NEW_PULL_REQUEST
                )

                val savedCommits = pullRequestCommitRepository.findByPullRequestAndState(created, PullRequestCommit.State.CURRENT)
                savedCommits.size shouldBe 1
            }

            it("재검사 시 diff가 완전히 사라지면 PR이 자동으로 MERGED 상태로 전환되어야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "자동 병합 전환 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/master",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                val result = pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)

                result.hasDiffCommits() shouldBe false

                val updated = pullRequestRepository.findById(pr.id!!).orElse(null)
                updated.state shouldBe State.MERGED
                updated.isConflict shouldBe false
                updated.receiver?.id shouldBe contributor.id
            }

            // yona PullRequest.updateMerge()/updateMergedCommitId() 대응 (P1-53). P1-52에서 이 부분을
            // lastCommitId 전/후 쌍으로 축약했었는데, 사용자가 legacy 메커니즘을 그대로 이식하라고
            // 재지시해 실제 "미리보기 병합 커밋" 생성+ref 갱신으로 교체했다.
            it("processMergeCheck를 반복 호출하면 mergedCommitIdFrom/mergedCommitIdTo가 실제 병합 미리보기 커밋을 반영하고, PullRequestEvent.oldValue는 그 값의 변경 전/후 쌍이어야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature-mc", "test2.txt", "first change", "First change")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "mergedCommitId 추적 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-mc",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                // 1차 재검사: 이전 mergedCommitIdTo가 없으므로 oldValue는 null이어야 한다.
                pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)

                val afterFirst = pullRequestRepository.findById(pr.id!!).orElse(null)
                afterFirst.mergedCommitIdFrom shouldNotBe null
                afterFirst.mergedCommitIdTo shouldNotBe null
                val firstMergedCommitIdTo = afterFirst.mergedCommitIdTo

                val firstEvent = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr).first()
                firstEvent.oldValue shouldBe null

                // 소스 브랜치에 새 커밋을 추가한 뒤 2차 재검사: mergedCommitIdTo가 갱신되고,
                // 새 PullRequestEvent.oldValue는 "이전 mergedCommitIdTo,새 mergedCommitIdTo" 쌍이어야 한다.
                createCommit(fromBareDir, "feature-mc", "test3.txt", "second change", "Second change")
                pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)

                val afterSecond = pullRequestRepository.findById(pr.id!!).orElse(null)
                afterSecond.mergedCommitIdTo shouldNotBe firstMergedCommitIdTo

                val events = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr)
                events.size shouldBe 2
                events[1].oldValue shouldBe "$firstMergedCommitIdTo,${afterSecond.mergedCommitIdTo}"
            }

            // yona PullRequestActor.processPullRequestMerging()의 conflict 상태 전환 추적 대응 (P1-71).
            it("병합 재검사 중 conflict 상태가 바뀌면(충돌 발생/해소) PULL_REQUEST_MERGED 알림과 타임라인이 남아야 한다") {
                watchRepository.save(Watch(user = receiver, resourceType = ResourceType.PROJECT, resourceId = toProject.id.toString()))

                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(toBareDir, "master", "test.txt", "hello common\ntarget edit", "Target conflict commit")
                createCommit(fromBareDir, "feature-conflict-check", "test.txt", "hello common\nsource edit", "Source conflict commit")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "conflict 전환 추적 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-conflict-check",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                // 1차 재검사: 충돌 없다가(wasConflict=false) 충돌 발생 -> CONFLICT 전환 기록.
                pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)

                val afterConflict = pullRequestRepository.findById(pr.id!!).orElse(null)
                afterConflict.isConflict shouldBe true

                val notiEventsAfterConflict = notificationEventRepository.findAll()
                    .filter { it.eventType == EventType.PULL_REQUEST_MERGED }
                notiEventsAfterConflict.size shouldBe 1
                notiEventsAfterConflict.first().newValue shouldBe "conflict"

                val prEventsAfterConflict = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr)
                    .filter { it.eventType == EventType.PULL_REQUEST_MERGED }
                prEventsAfterConflict.size shouldBe 1
                prEventsAfterConflict.first().newValue shouldBe "conflict"
                prEventsAfterConflict.first().senderLoginId shouldBe contributor.loginId

                // 소스 브랜치를 대상 브랜치와 동일한 내용으로 갱신해 충돌을 해소한 뒤 2차 재검사.
                createCommit(fromBareDir, "feature-conflict-check", "test.txt", "hello common\ntarget edit", "Resolve conflict")
                pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)

                val afterResolved = pullRequestRepository.findById(pr.id!!).orElse(null)
                afterResolved.isConflict shouldBe false

                val notiEventsAfterResolved = notificationEventRepository.findAll()
                    .filter { it.eventType == EventType.PULL_REQUEST_MERGED }
                notiEventsAfterResolved.size shouldBe 2
                notiEventsAfterResolved.last().newValue shouldBe "resolved"

                val prEventsAfterResolved = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr)
                    .filter { it.eventType == EventType.PULL_REQUEST_MERGED }
                prEventsAfterResolved.size shouldBe 2
                prEventsAfterResolved.last().newValue shouldBe "resolved"
            }

            // yona PullRequest.updateWith()/hasSameBranchesWith()/findDuplicatedPullRequest() 대응 (P1-68).
            it("PR 수정 시 from/toBranch를 재할당할 수 있어야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "브랜치 재할당 테스트 PR", body = "본문",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-a",
                        contributor = contributor, state = State.OPEN
                    )
                )

                val updated = pullRequestService.updatePullRequest(
                    pullRequestId = pr.id!!,
                    title = "브랜치 재할당 테스트 PR",
                    body = "본문",
                    fromBranch = "refs/heads/feature-b",
                    toBranch = "refs/heads/develop"
                )

                updated.fromBranch shouldBe "refs/heads/feature-b"
                updated.toBranch shouldBe "refs/heads/develop"
            }

            it("재할당하려는 브랜치 조합으로 이미 열려있는 PR이 있으면 DuplicatedPullRequestException을 던지고 변경하지 않아야 한다") {
                pullRequestRepository.save(
                    PullRequest(
                        title = "기존에 열려있는 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/develop", fromBranch = "refs/heads/feature-b",
                        contributor = contributor, state = State.OPEN
                    )
                )
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "수정하려는 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-a",
                        contributor = contributor, state = State.OPEN
                    )
                )

                shouldThrow<DuplicatedPullRequestException> {
                    pullRequestService.updatePullRequest(
                        pullRequestId = pr.id!!,
                        title = "수정하려는 PR",
                        body = "...",
                        fromBranch = "refs/heads/feature-b",
                        toBranch = "refs/heads/develop"
                    )
                }

                val unchanged = pullRequestRepository.findById(pr.id!!).orElse(null)
                unchanged.fromBranch shouldBe "refs/heads/feature-a"
                unchanged.toBranch shouldBe "refs/heads/master"
            }

            it("PR 제목/본문에서 참조하는 이슈가 바뀌면 ISSUE_REFERRED_FROM_PULL_REQUEST 이벤트가 재동기화되어야 한다") {
                val issue1 = issueService.createIssue(
                    Issue(title = "이슈 1", body = "...", project = toProject), receiver, null, null, null
                )
                val issue2 = issueService.createIssue(
                    Issue(title = "이슈 2", body = "...", project = toProject), receiver, null, null, null
                )

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "이슈 참조 테스트 PR", body = "관련 이슈 #${issue1.number}",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-c",
                        contributor = contributor, state = State.OPEN
                    )
                )
                // 최초 생성 시점의 참조 이벤트를 직접 만들어둔다(생성 경로는 createPullRequest에서
                // 별도로 처리하므로, 이 테스트는 updateWith()의 재동기화만 검증한다).
                issueEventRepository.save(
                    IssueEvent(
                        issue = issue1, senderLoginId = contributor.loginId,
                        newValue = pr.id.toString(),
                        eventType = EventType.ISSUE_REFERRED_FROM_PULL_REQUEST
                    )
                )

                pullRequestService.updatePullRequest(
                    pullRequestId = pr.id!!,
                    title = "이슈 참조 테스트 PR",
                    body = "관련 이슈 #${issue2.number}",
                    fromBranch = pr.fromBranch,
                    toBranch = pr.toBranch
                )

                val issue1Events = issueEventRepository.findByIssueOrderByCreatedAsc(issue1)
                issue1Events.size shouldBe 0

                val issue2Events = issueEventRepository.findByIssueOrderByCreatedAsc(issue2)
                issue2Events.size shouldBe 1
                issue2Events.first().eventType shouldBe EventType.ISSUE_REFERRED_FROM_PULL_REQUEST
                issue2Events.first().newValue shouldBe pr.id.toString()
                issue2Events.first().senderLoginId shouldBe contributor.loginId
            }

            // yona PullRequestApp.mergeResult()/PullRequest.attemptMerge()(전용 프리뷰 경로) 대응
            // (#178, TASK-0257). attemptMerge(pullRequestId)와 달리 저장된 PullRequest가 전혀 없는
            // 임의의 from/to 브랜치 조합에 대해 부수효과 없이 커밋 프리뷰 + 충돌 여부를 계산한다.
            it("previewMerge - 충돌이 없는 임의 브랜치 조합의 커밋 프리뷰와 제목/본문 추천을 반환하고 PullRequest를 저장하지 않아야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "preview-feature", "test2.txt", "preview change", "Preview commit title\n\nPreview commit body line1\nline2")

                val preview = pullRequestService.previewMerge(
                    fromProject = fromProject,
                    toProject = toProject,
                    fromBranch = "refs/heads/preview-feature",
                    toBranch = "refs/heads/master"
                )

                preview.conflict shouldBe false
                preview.commits.size shouldBe 1
                preview.commits.first().getShortMessage() shouldBe "Preview commit title"
                // yona suggestTitleAndBodyFromDiffCommit() 대응 - 커밋이 1개면 첫 줄이 title, 나머지가 body.
                preview.suggestedTitle shouldBe "Preview commit title"
                preview.suggestedBody shouldBe "Preview commit body line1\nline2"

                // 저장된 PullRequest가 전혀 생기지 않아야 한다(legacy도 이 프리뷰 경로에서 PullRequest를
                // 저장하지 않는다 - createNewPullRequest()는 순수 in-memory 객체).
                pullRequestRepository.findAll().size shouldBe 0
            }

            it("previewMerge - 충돌이 발생하는 임의 브랜치 조합은 conflict=true를 반환해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(toBareDir, "master", "test.txt", "hello common\ntarget edit", "Target conflict commit")
                createCommit(fromBareDir, "preview-conflict", "test.txt", "hello common\nsource edit", "Source conflict commit")

                val preview = pullRequestService.previewMerge(
                    fromProject = fromProject,
                    toProject = toProject,
                    fromBranch = "refs/heads/preview-conflict",
                    toBranch = "refs/heads/master"
                )

                preview.conflict shouldBe true
                preview.commits.size shouldBe 1
            }

            it("previewMerge - 커밋이 2개 이상이면 제목 추천 없이 각 커밋의 첫 줄만 모아 본문을 추천해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "preview-multi", "test2.txt", "change1", "First commit")
                createCommit(fromBareDir, "preview-multi", "test3.txt", "change2", "Second commit")

                val preview = pullRequestService.previewMerge(
                    fromProject = fromProject,
                    toProject = toProject,
                    fromBranch = "refs/heads/preview-multi",
                    toBranch = "refs/heads/master"
                )

                preview.commits.size shouldBe 2
                preview.suggestedTitle shouldBe null
                preview.suggestedBody shouldBe "Second commit\nFirst commit"
            }

            it("previewMerge - 변경 사항이 없으면 빈 커밋 목록과 conflict=false를 반환해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")

                val preview = pullRequestService.previewMerge(
                    fromProject = toProject,
                    toProject = toProject,
                    fromBranch = "refs/heads/master",
                    toBranch = "refs/heads/master"
                )

                preview.commits.size shouldBe 0
                preview.conflict shouldBe false
                preview.suggestedTitle shouldBe null
                preview.suggestedBody shouldBe null
            }

            it("previewMerge - 커밋이 1개이고 메시지가 한 줄뿐이면 body는 빈 문자열이어야 한다") {
                // suggestTitleAndBody()의 messages.size > 1 분기가 false인 경로 - 커밋 메시지에
                // 개행이 전혀 없는 단일 줄 메시지인 경우를 검증한다.
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "preview-single-line", "test2.txt", "change", "Single line message")

                val preview = pullRequestService.previewMerge(
                    fromProject = fromProject,
                    toProject = toProject,
                    fromBranch = "refs/heads/preview-single-line",
                    toBranch = "refs/heads/master"
                )

                preview.commits.size shouldBe 1
                preview.suggestedTitle shouldBe "Single line message"
                preview.suggestedBody shouldBe ""
            }

            it("getPullRequests - state를 지정하지 않으면 대상 프로젝트의 모든 PR을, 지정하면 해당 상태의 PR만 반환해야 한다") {
                pullRequestRepository.save(
                    PullRequest(
                        title = "OPEN PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-open-list",
                        contributor = contributor, state = State.OPEN
                    )
                )
                pullRequestRepository.save(
                    PullRequest(
                        title = "CLOSED PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-closed-list",
                        contributor = contributor, state = State.CLOSED
                    )
                )

                val all = pullRequestService.getPullRequests(toProject.id!!, null)
                all.size shouldBe 2

                val onlyOpen = pullRequestService.getPullRequests(toProject.id!!, State.OPEN)
                onlyOpen.size shouldBe 1
                onlyOpen.first().state shouldBe State.OPEN
            }

            // 참고: rightParent(fetch된 source ref) null 체크("Fetched source branch not found" /
            // "Source branch not found" 예외) 분기는 실제로는 도달 불가능함을 실험적으로 확인했다 -
            // JGit FetchCommand가 RefSpec의 source로 원격에 존재하지 않는 ref를 지정하면 fetch 자체가
            // TransportException을 던지며 즉시 실패하고, 그 이후의 repo.resolve(tempBranch) null 체크
            // 지점까지 도달하지 않는다(attemptMerge/previewMerge/merge/updateMerge 네 메서드 모두 동일한
            // fetch 흐름을 공유). 따라서 leftParent(대상 브랜치, 로컬 저장소에서 fetch 없이 바로
            // resolve)의 null 체크만 테스트 가능하다.
            it("attemptMerge - 대상 브랜치가 존재하지 않으면 IllegalArgumentException을 던져야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature", "test.txt", "hello", "Source commit")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "대상 브랜치 없음 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/no-such-branch", fromBranch = "refs/heads/feature",
                        contributor = contributor, state = State.OPEN
                    )
                )

                val ex = shouldThrow<IllegalArgumentException> {
                    pullRequestService.attemptMerge(pr.id!!)
                }
                ex.message shouldBe "Target branch 'refs/heads/no-such-branch' not found"
            }

            it("previewMerge - 대상 브랜치가 존재하지 않으면 IllegalArgumentException을 던져야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature", "test.txt", "hello", "Source commit")

                val exTarget = shouldThrow<IllegalArgumentException> {
                    pullRequestService.previewMerge(
                        fromProject = fromProject,
                        toProject = toProject,
                        fromBranch = "refs/heads/feature",
                        toBranch = "refs/heads/no-such-target"
                    )
                }
                exTarget.message shouldBe "Target branch 'refs/heads/no-such-target' not found"
            }

            it("merge - 대상 브랜치가 존재하지 않으면 IllegalArgumentException을 던져야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature", "test.txt", "hello", "Source commit")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "머지 대상 브랜치 없음 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/no-such-merge-target", fromBranch = "refs/heads/feature",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )

                val ex = shouldThrow<IllegalArgumentException> {
                    pullRequestService.merge(pr.id!!, receiver)
                }
                ex.message shouldBe "Target branch 'refs/heads/no-such-merge-target' not found"
            }

            // yona PullRequest.Merger.Success 대응 - 병합 미리보기 트리(getMergedTreeIfReusable)와
            // 그 재사용 여부를 검증한다. processMergeCheck를 새 커밋 없이 두 번째로 호출하면 diff 자체는
            // 여전히 존재하지만(hasDiffCommits=true) 이전 재검사에서 이미 저장된 커밋뿐이라 newCommits는
            // 빈 리스트가 되고, leftParent/rightParent가 첫 호출과 동일하므로 refs/yobi/pull/{id}/merged의
            // 트리를 그대로 재사용해야 한다.
            it("processMergeCheck를 새 커밋 없이 재호출하면 newCommits는 비어있고 이전 병합 트리를 재사용해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature-reuse", "test2.txt", "change", "Only change")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "재사용 트리 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-reuse",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )

                val first = pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)
                first.hasDiffCommits() shouldBe true
                first.newCommits.size shouldBe 1
                val afterFirst = pullRequestRepository.findById(pr.id!!).orElse(null)
                val firstMergedCommitIdTo = afterFirst.mergedCommitIdTo
                firstMergedCommitIdTo shouldNotBe null

                // Git 커밋 타임스탬프는 초 단위 정밀도라 동일 초 내에 동일한 트리/부모로 병합 커밋을
                // 다시 만들면 완전히 동일한 커밋 해시가 나와 RefUpdate 결과가 NO_CHANGE가 되고
                // createMergeCommitAndUpdateRef()가 이를 실패로 간주해 IOException을 던진다. 두 번째
                // 호출은 재사용 분기(leftParent/rightParent가 동일해 getMergedTreeIfReusable가 트리를
                // 재사용)를 검증하려는 것이므로, 초 경계를 넘겨 새 커밋 해시가 나오도록 한다.
                Thread.sleep(1100)
                val second = pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)
                second.hasDiffCommits() shouldBe true
                second.newCommits.size shouldBe 0

                // 새 PullRequestEvent(PULL_REQUEST_COMMIT_CHANGED)가 추가로 기록되지 않아야 한다
                // (newCommits가 비어 recordCommitChangedEvent 자체가 호출되지 않음).
                pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr)
                    .count { it.eventType == EventType.PULL_REQUEST_COMMIT_CHANGED } shouldBe 1
            }

            it("이미 MERGED 상태인 PR을 diff 없이 재검사해도 상태 전환이 다시 발생하지 않아야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "이미 병합된 PR 재검사 테스트", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/master",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )

                pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)
                val afterFirst = pullRequestRepository.findById(pr.id!!).orElse(null)
                afterFirst.state shouldBe State.MERGED

                val eventsAfterFirst = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr).size

                // 동일 초 내 동일한 병합 커밋 재생성으로 인한 NO_CHANGE 충돌을 피하기 위해 초 경계를
                // 넘긴다(위 재사용 트리 테스트와 동일한 이유).
                Thread.sleep(1100)
                // 이미 MERGED인 상태에서 diff 없는 재검사를 한 번 더 수행 - state != MERGED 분기가
                // false가 되어 changeState()가 다시 호출되지 않아야 한다(추가 이벤트 없음).
                pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)
                val afterSecond = pullRequestRepository.findById(pr.id!!).orElse(null)
                afterSecond.state shouldBe State.MERGED

                pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr).size shouldBe eventsAfterFirst
            }

            it("processMergeCheck - 존재하지 않는 pullRequestId로 호출하면 IllegalArgumentException을 던져야 한다") {
                shouldThrow<IllegalArgumentException> {
                    pullRequestService.processMergeCheck(-999999L, contributor, isNewPullRequest = false)
                }
            }

            // yona PullRequestMergeResult.updatePriorCommits() 대응 - 소스 브랜치가 강제로 재작성(amend/
            // rebase)되어 이전에 CURRENT였던 커밋이 새 diff 범위에서 완전히 사라지면 PRIOR로 전환되어야
            // 한다.
            it("소스 브랜치가 강제로 재작성되면 이전 CURRENT 커밋이 PRIOR로 전환되고 새 커밋이 CURRENT로 저장되어야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature-amend", "a.txt", "v1", "Original commit")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "강제 재작성 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-amend",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )

                pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)
                val currentAfterFirst = pullRequestCommitRepository.findByPullRequestAndState(pr, PullRequestCommit.State.CURRENT)
                currentAfterFirst.size shouldBe 1
                currentAfterFirst.first().commitMessage.trim() shouldBe "Original commit"

                // master를 base로 다시 체크아웃해 feature-amend를 완전히 대체(force-push) - 기존
                // "Original commit"은 더 이상 diff 범위에 존재하지 않게 된다.
                createCommit(fromBareDir, "feature-amend", "b.txt", "v2", "Amended commit", baseBranch = "master")
                pullRequestService.processMergeCheck(pr.id!!, contributor, isNewPullRequest = false)

                val priorCommits = pullRequestCommitRepository.findByPullRequestAndState(pr, PullRequestCommit.State.PRIOR)
                priorCommits.size shouldBe 1
                priorCommits.first().commitMessage.trim() shouldBe "Original commit"

                val currentAfterSecond = pullRequestCommitRepository.findByPullRequestAndState(pr, PullRequestCommit.State.CURRENT)
                currentAfterSecond.size shouldBe 1
                currentAfterSecond.first().commitMessage.trim() shouldBe "Amended commit"
            }

            // yona PullRequest.mergeMessage() 대응 - 대상 브랜치가 master가 아니면 " into '<브랜치>'"
            // 절이 커밋 메시지에 포함되어야 한다.
            it("merge - 대상 브랜치가 master가 아니면 병합 커밋 메시지에 into 절이 포함되어야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "develop", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "develop")
                createCommit(toBareDir, "develop", "test2.txt", "target change", "Target change on develop")
                createCommit(fromBareDir, "feature-develop", "test3.txt", "source change", "Source change", baseBranch = "develop")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "master가 아닌 대상 브랜치 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/develop", fromBranch = "refs/heads/feature-develop",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )

                pullRequestService.attemptMerge(pr.id!!)
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                val mergedPr = pullRequestRepository.findById(pr.id!!).get()
                mergedPr.mergedCommitIdTo shouldNotBe null

                val repo = org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                    .setGitDir(toBareDir).build()
                val revWalk = org.eclipse.jgit.revwalk.RevWalk(repo)
                val commit = revWalk.parseCommit(repo.resolve(mergedPr.mergedCommitIdTo))
                commit.fullMessage shouldContain "into 'develop'"
                revWalk.close()
                repo.close()
            }

            // yona PullRequest.mergeMessage()의 "fromProject != toProject일 때만 owner/name 절 추가"
            // 대응 - 동일 프로젝트 내부 브랜치 간 PR은 그 절이 생략되어야 한다.
            it("merge - fromProject와 toProject가 동일하면 병합 커밋 메시지에 프로젝트 소유자 절이 없어야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                createCommit(toBareDir, "feature-same-project", "test2.txt", "change", "Same project change", baseBranch = "master")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "동일 프로젝트 PR", body = "...",
                        toProject = toProject, fromProject = toProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-same-project",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )

                pullRequestService.attemptMerge(pr.id!!)
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                val mergedPr = pullRequestRepository.findById(pr.id!!).get()
                val repo = org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                    .setGitDir(toBareDir).build()
                val revWalk = org.eclipse.jgit.revwalk.RevWalk(repo)
                val commit = revWalk.parseCommit(repo.resolve(mergedPr.mergedCommitIdTo))
                commit.fullMessage shouldContain "Merge branch 'feature-same-project'\n\n"
                (commit.fullMessage.contains(" of ")) shouldBe false
                revWalk.close()
                repo.close()
            }

            // TASK-0416 부수 발견(P3-02 10라운드) — 실제 서버 + 실제 yona-cli로 `pr create` ->
            // `pr merge` 골든패스를 재현하다가 발견한 회귀. 이 파일의 다른 merge 테스트들은 전부
            // fromBranch/toBranch를 "refs/heads/..." 형태로 직접 만들어 호출하는데, 실제 운영
            // 경로(REST API의 CreatePullRequestRequest, 세션 웹 UI의 PR 생성 폼)는 항상 짧은
            // 브랜치 이름("feature-1")을 그대로 저장한다 — 이 스펙은 createPullRequest()를 통해
            // 짧은 이름으로 PR을 만들어(실제 운영 경로와 동일하게) merge()가 여전히 성공하는지
            // 검증한다.
            it("merge - fromBranch/toBranch가 짧은 이름(refs/heads/ 접두어 없음)이어도 병합에 성공해야 한다") {
                createCommit(repositoryService.getRepository(toProject).getDirectory(), "master", "test.txt", "hello", "Initial commit")
                createCommit(
                    repositoryService.getRepository(toProject).getDirectory(), "short-branch-feature", "test2.txt", "change", "짧은 이름 브랜치 변경",
                    baseBranch = "master"
                )

                val created = pullRequestService.createPullRequest(
                    title = "짧은 브랜치 이름 PR", body = "본문",
                    fromProjectId = toProject.id!!, toProjectId = toProject.id!!,
                    fromBranch = "short-branch-feature", toBranch = "master",
                    contributor = contributor
                )

                val mergeResult = pullRequestService.merge(created.id!!, receiver)

                mergeResult.conflicts() shouldBe false
                val mergedPr = pullRequestRepository.findById(created.id!!).get()
                mergedPr.mergedCommitIdTo shouldNotBe null
            }

            it("createPullRequest - 동일 대상 프로젝트에 두 번째 PR을 생성하면 번호가 순차 증가해야 한다") {
                val first = pullRequestService.createPullRequest(
                    title = "첫 번째 PR", body = "본문",
                    fromProjectId = fromProject.id!!, toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/feature-1", toBranch = "refs/heads/master",
                    contributor = contributor
                )
                val second = pullRequestService.createPullRequest(
                    title = "두 번째 PR", body = "본문",
                    fromProjectId = fromProject.id!!, toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/feature-2", toBranch = "refs/heads/master",
                    contributor = contributor
                )

                first.number shouldBe 1L
                second.number shouldBe 2L
            }

            it("createPullRequest - 병합 재검사 중 예외가 발생해도 PR 생성 자체는 성공해야 한다") {
                // fromBranch가 실제로 존재하지 않아 processMergeCheck 내부(updateMerge)에서
                // IllegalArgumentException이 던져지지만, createPullRequest의 try/catch가 이를 흡수하고
                // PR 저장 자체는 그대로 유지되어야 한다.
                val created = pullRequestService.createPullRequest(
                    title = "예외 발생해도 생성되는 PR", body = "본문",
                    fromProjectId = fromProject.id!!, toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/no-such-branch-at-all", toBranch = "refs/heads/no-such-target-either",
                    contributor = contributor
                )

                created.id shouldNotBe null
                val saved = pullRequestRepository.findById(created.id!!).orElse(null)
                saved shouldNotBe null
                saved.title shouldBe "예외 발생해도 생성되는 PR"
            }

            it("createPullRequest - body가 null이면 멘션 추출도 정상적으로 빈 결과를 반환해야 한다") {
                val created = pullRequestService.createPullRequest(
                    title = "본문 없는 PR", body = null,
                    fromProjectId = fromProject.id!!, toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/feature-null-body", toBranch = "refs/heads/master",
                    contributor = contributor
                )

                created.body shouldBe null
                // 감시자/멘션 대상이 전혀 없어 NotificationEvent 자체가 기록되지 않아야 한다
                // (notificationEventRecorder.record()가 null을 반환하는 분기).
                notificationEventRepository.findAll().size shouldBe 0
            }

            it("changeState - 존재하지 않는 updaterLoginId로 호출하면 알림 발신자 없이도 상태가 변경되어야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "알 수 없는 갱신자 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-unknown-updater",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )
                watchRepository.save(Watch(user = receiver, resourceType = ResourceType.PROJECT, resourceId = toProject.id.toString()))

                val updated = pullRequestService.changeState(pr.id!!, State.CLOSED, "no-such-updater-login-id")

                updated.state shouldBe State.CLOSED
                val notiEvents = notificationEventRepository.findAll()
                notiEvents.size shouldBe 1
                notiEvents.first().senderId shouldBe null

                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr)
                prEvents.first().senderLoginId shouldBe "no-such-updater-login-id"
            }

            it("updatePullRequest - body가 null이면 이슈 참조 이벤트가 재동기화되지 않아야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "본문 없는 수정 테스트 PR", body = "관련 이슈 없음",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-null-update-body",
                        contributor = contributor, state = State.OPEN
                    )
                )

                val updated = pullRequestService.updatePullRequest(
                    pullRequestId = pr.id!!,
                    title = "본문 없는 수정 테스트 PR",
                    body = null,
                    fromBranch = pr.fromBranch,
                    toBranch = pr.toBranch
                )

                updated.body shouldBe null
                issueEventRepository.findAll().size shouldBe 0
            }

            it("updatePullRequest - 존재하지 않는 이슈 번호를 참조하면 이슈 이벤트가 생성되지 않아야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "존재하지 않는 이슈 참조 PR", body = "본문",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-missing-issue",
                        contributor = contributor, state = State.OPEN
                    )
                )

                pullRequestService.updatePullRequest(
                    pullRequestId = pr.id!!,
                    title = "존재하지 않는 이슈 참조 PR",
                    body = "관련 이슈 #9999",
                    fromBranch = pr.fromBranch,
                    toBranch = pr.toBranch
                )

                issueEventRepository.findAll().size shouldBe 0
            }

            it("restoreFromBranch - lastCommitId가 없으면 InvalidBranchOperationException을 던져야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "lastCommitId 없는 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-no-last-commit",
                        contributor = contributor, state = State.OPEN
                    )
                )

                shouldThrow<InvalidBranchOperationException> {
                    pullRequestService.restoreFromBranch(pr.id!!)
                }
            }

            it("restoreFromBranch - 브랜치가 이미 존재하면 InvalidBranchOperationException을 던져야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature-already-exists", "f.txt", "content", "이미 존재 테스트용 커밋")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "이미 존재하는 브랜치 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-already-exists",
                        contributor = contributor, state = State.MERGED,
                        lastCommitId = "0000000000000000000000000000000000000000"
                    )
                )

                shouldThrow<InvalidBranchOperationException> {
                    pullRequestService.restoreFromBranch(pr.id!!)
                }
            }

            it("getDiff - mergedCommitIdFrom/To가 모두 설정된 병합 완료 PR은 그 값으로 바로 diff를 계산해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature-merged-diff", "test2.txt", "change", "Merged diff change")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "병합 완료 diff 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-merged-diff",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )
                pullRequestService.attemptMerge(pr.id!!)
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                val mergedPr = pullRequestRepository.findById(pr.id!!).get()
                mergedPr.mergedCommitIdFrom shouldNotBe null
                mergedPr.mergedCommitIdTo shouldNotBe null

                val diffs = pullRequestService.getDiff(mergedPr)
                diffs.any { it.pathB == "test2.txt" } shouldBe true
            }

            it("getDiff - lastCommitId가 없는 새 PR은 fromBranch를 그대로 비교 대상으로 사용해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature-fresh-diff", "test2.txt", "change", "Fresh diff change")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "미검사 PR diff 테스트", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-fresh-diff",
                        contributor = contributor, state = State.OPEN
                    )
                )
                // attemptMerge/processMergeCheck를 한 번도 호출하지 않아 lastCommitId가 null인 상태.
                pr.lastCommitId shouldBe null

                val diffs = pullRequestService.getDiff(pr)
                diffs.any { it.pathB == "test2.txt" } shouldBe true
            }

            it("getDiff - toProject가 SVN이면 GitRepository가 아니므로 revA/revB를 필드 값으로 계산 후 위임하며 예외가 전파되어야 한다") {
                // SvnRepository.getDiff()는 미구현으로 항상 UnsupportedOperationException을 던진다.
                // 이 테스트의 목적은 실제 SVN diff 결과가 아니라, PullRequestServiceImpl.getDiff()의
                // "playRepoA is GitRepository && playRepoB is GitRepository"가 false인 분기 및 그 이후
                // revA/revB 계산용 엘비스 체인(모두 null인 경우 - toBranch/fromBranch로 폴백)이 실제로
                // 실행되는지 검증하는 것이다.
                toProject.vcs = "SVN"
                projectRepository.save(toProject)

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "SVN 대상 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                        contributor = contributor, state = State.OPEN
                    )
                )

                shouldThrow<UnsupportedOperationException> {
                    pullRequestService.getDiff(pr)
                }
            }

            it("getDiff - mergedCommitIdFrom만 설정되고 SVN이면 revA/revB 엘비스 체인의 non-null 경로를 타야 한다") {
                toProject.vcs = "SVN"
                projectRepository.save(toProject)

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "부분 병합 정보 SVN PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                        contributor = contributor, state = State.OPEN,
                        // mergedCommitIdTo는 비워두어 "모두 설정됨" 첫 분기를 피하고, mergedCommitIdFrom/
                        // lastCommitId는 채워 revA/revB 엘비스 체인의 non-null 분기를 태운다.
                        lastCommitId = "1111111111111111111111111111111111111111"
                    )
                )
                pr.mergedCommitIdFrom = "2222222222222222222222222222222222222222"
                pullRequestRepository.save(pr)

                shouldThrow<UnsupportedOperationException> {
                    pullRequestService.getDiff(pr)
                }
            }

            it("getDiff - fromProject만 SVN이면 playRepoB is GitRepository가 false가 되어 fallback 경로로 위임해야 한다") {
                fromProject.vcs = "SVN"
                projectRepository.save(fromProject)

                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "소스만 SVN인 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                        contributor = contributor, state = State.OPEN
                    )
                )

                // playRepoA(toProject, GIT)는 GitRepository이지만 playRepoB(fromProject, SVN)는
                // 아니므로 "playRepoA is GitRepository && playRepoB is GitRepository"가 false가 되고,
                // fallback으로 playRepoA(GitRepository).getDiff(revA, revB) 2-인자 오버로드(단일 저장소
                // 내 비교)가 호출된다. revB가 fromBranch("refs/heads/feature")로 폴백되는데 toProject
                // 저장소에는 그 브랜치가 없어 idB가 null이 되고, GitRepository.getFileDiffs()는 null
                // 커밋을 빈 트리로 취급하므로 master의 모든 파일이 DELETE 타입 diff로 나타난다
                // (예외 없이 정상 완료됨을 확인).
                val diffs = pullRequestService.getDiff(pr)
                diffs.any { it.pathA == "test.txt" } shouldBe true
            }
            
            it("attemptMerge - Target branch not found throws exception") {
                val pr = pullRequestRepository.save(PullRequest(title = "Title", body = "Body", toProject = toProject, fromProject = fromProject, toBranch = "refs/heads/invalid-target", fromBranch = "refs/heads/feature", contributor = contributor, receiver = receiver, created = Instant.now(), state = State.OPEN))
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    pullRequestService.attemptMerge(pr.id!!)
                }.message shouldContain "Target branch"
            }

            // 2026-08-25: 아래 3개는 원래 "IllegalArgumentException(Fetched source branch not found)"을
            // 기대했으나, 위 1155~1161행 주석에서 이미 실험적으로 확인했듯 존재하지 않는 source ref로
            // fetch를 시도하면 JGit FetchCommand가 그 자리에서 TransportException을 던져 즉시 실패하고
            // (attemptMerge/previewMerge/merge 전부 동일 fetch 흐름 공유) IllegalArgumentException 분기
            // 자체에 도달하지 못한다 — 기대 예외 타입이 처음부터 틀렸던 테스트라 실제 동작에 맞게 수정.
            it("attemptMerge - Source branch not found throws exception") {
                val pr = pullRequestRepository.save(PullRequest(title = "Title", body = "Body", toProject = toProject, fromProject = fromProject, toBranch = "refs/heads/master", fromBranch = "refs/heads/invalid-source", contributor = contributor, receiver = receiver, created = Instant.now(), state = State.OPEN))
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")

                io.kotest.assertions.throwables.shouldThrow<org.eclipse.jgit.api.errors.TransportException> {
                    pullRequestService.attemptMerge(pr.id!!)
                }
            }

            it("previewMerge - Source branch not found throws exception") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello", "init")

                io.kotest.assertions.throwables.shouldThrow<org.eclipse.jgit.api.errors.TransportException> {
                    pullRequestService.previewMerge(fromProject, toProject, "invalid-source", "refs/heads/master")
                }
            }

            it("merge - Source head ref not found throws exception") {
                val pr = pullRequestRepository.save(PullRequest(title = "Title", body = "Body", toProject = toProject, fromProject = fromProject, toBranch = "refs/heads/master", fromBranch = "refs/heads/invalid-source", contributor = contributor, receiver = receiver, created = Instant.now(), state = State.OPEN))
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")

                io.kotest.assertions.throwables.shouldThrow<org.eclipse.jgit.api.errors.TransportException> {
                    pullRequestService.merge(pr.id!!, receiver)
                }
            }

            // yona errors/PullRequestException.java 대응 (P2-50) — ref 갱신이 NEW/FAST_FORWARD/FORCED가
            // 아닌 결과로 실패하면 IOException이 아닌 전용 PullRequestException을 던져야 한다. merged ref의
            // lock 파일을 미리 점유해 JGit RefUpdate가 LOCK_FAILURE를 반환하도록 강제해 재현한다.
            it("merge - merged ref 갱신이 lock 충돌로 실패하면 PullRequestException을 던져야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                createCommit(fromBareDir, "feature", "test2.txt", "source change", "Source change")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "lock 충돌 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                        contributor = contributor, receiver = receiver, state = State.OPEN
                    )
                )

                val lockFile = java.io.File(toBareDir, "refs/yobi/pull/${pr.id}/merged.lock")
                lockFile.parentFile.mkdirs()
                lockFile.writeText("locked by another process")

                try {
                    shouldThrow<PullRequestException> {
                        pullRequestService.merge(pr.id!!, receiver)
                    }
                } finally {
                    lockFile.delete()
                }
            }

            it("updatePullRequest - throws DuplicatedPullRequestException when duplicate exists") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")

                val pr1 = pullRequestRepository.save(PullRequest(title = "Title 1", body = "Body", toProject = toProject, fromProject = fromProject, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature", contributor = contributor, receiver = receiver, created = Instant.now(), state = State.OPEN))
                val pr2 = pullRequestRepository.save(PullRequest(title = "Title 2", body = "Body", toProject = toProject, fromProject = fromProject, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature2", contributor = contributor, receiver = receiver, created = Instant.now(), state = State.OPEN))
                
                io.kotest.assertions.throwables.shouldThrow<DuplicatedPullRequestException> {
                    pullRequestService.updatePullRequest(pr2.id!!, "Title 2", "Body", "refs/heads/feature", "refs/heads/master")
                }
            }

            it("changeState - same state does not trigger event") {
                val pr = pullRequestRepository.save(PullRequest(title = "Title", body = "Body", toProject = toProject, fromProject = fromProject, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature", contributor = contributor, receiver = receiver, created = Instant.now(), state = State.OPEN))
                val updated = pullRequestService.changeState(pr.id!!, State.OPEN, contributor.loginId)
                updated.state shouldBe State.OPEN
            }

            it("getDiff(pullRequest, commitId) - state가 MERGED면 toProject 저장소에서 diff를 조회해야 한다") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                createCommit(toBareDir, "master", "test2.txt", "second", "Second commit on target")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "MERGED 상태 diff(commitId) 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                        contributor = contributor, receiver = receiver, state = State.MERGED
                    )
                )

                val repo = org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                    .setGitDir(toBareDir).build()
                val headCommitId = repo.resolve("refs/heads/master")!!.name
                repo.close()

                val diffs = pullRequestService.getDiff(pr, headCommitId)
                diffs.any { it.pathB == "test2.txt" } shouldBe true
            }

            describe("존재하지 않는 리소스 조회 시 예외 처리 (orElseThrow 분기)") {
                it("attemptMerge - 존재하지 않는 pullRequestId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.attemptMerge(999999L)
                    }
                }

                it("merge - 존재하지 않는 pullRequestId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.merge(999999L, receiver)
                    }
                }

                it("getPullRequests - 존재하지 않는 toProjectId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.getPullRequests(999999L, null)
                    }
                }

                it("getPullRequest - 존재하지 않는 toProjectId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.getPullRequest(999999L, 1L)
                    }
                }

                it("getPullRequest - 정상적으로 프로젝트/번호에 해당하는 PR을 반환해야 한다") {
                    val pr = pullRequestService.createPullRequest(
                        "조회용 PR", "...", fromProject.id!!, toProject.id!!,
                        "refs/heads/feature", "refs/heads/master", contributor
                    )

                    val found = pullRequestService.getPullRequest(toProject.id!!, pr.number!!)

                    found?.id shouldBe pr.id
                }

                it("createPullRequest - 존재하지 않는 fromProjectId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.createPullRequest(
                            "제목", "본문", 999999L, toProject.id!!,
                            "refs/heads/feature", "refs/heads/master", contributor
                        )
                    }
                }

                it("createPullRequest - 존재하지 않는 toProjectId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.createPullRequest(
                            "제목", "본문", fromProject.id!!, 999999L,
                            "refs/heads/feature", "refs/heads/master", contributor
                        )
                    }
                }

                it("updatePullRequest - 존재하지 않는 pullRequestId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.updatePullRequest(999999L, "제목", "본문", "refs/heads/feature", "refs/heads/master")
                    }
                }

                it("changeState - 존재하지 않는 pullRequestId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.changeState(999999L, State.CLOSED, contributor.loginId)
                    }
                }

                it("addReviewer - 존재하지 않는 pullRequestId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.addReviewer(999999L, receiver)
                    }
                }

                it("addReviewer - 존재하지 않는 reviewer면 예외를 던져야 한다") {
                    val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                    createCommit(toBareDir, "master", "test.txt", "hello", "init")
                    val pr = pullRequestRepository.save(
                        PullRequest(
                            title = "리뷰어없음 PR", body = "...", toProject = toProject, fromProject = fromProject,
                            toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                            contributor = contributor, receiver = receiver, state = State.OPEN
                        )
                    )
                    val ghostReviewer = User(id = 999999L, loginId = "ghost-reviewer", name = "유령리뷰어")

                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.addReviewer(pr.id!!, ghostReviewer)
                    }
                }

                it("addReviewer - 이미 리뷰어로 등록된 유저를 다시 추가해도 알림이 중복 발행되지 않아야 한다") {
                    val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                    createCommit(toBareDir, "master", "test.txt", "hello", "init")
                    val pr = pullRequestRepository.save(
                        PullRequest(
                            title = "리뷰어중복 PR", body = "...", toProject = toProject, fromProject = fromProject,
                            toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                            contributor = contributor, receiver = receiver, state = State.OPEN
                        )
                    )
                    pullRequestService.addReviewer(pr.id!!, receiver)

                    pullRequestService.addReviewer(pr.id!!, receiver)

                    pullRequestRepository.findById(pr.id!!).get().reviewers.size shouldBe 1
                }

                it("removeReviewer - 리뷰어로 등록돼 있지 않은 유저를 제거해도 예외 없이 아무 일도 없어야 한다") {
                    val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                    createCommit(toBareDir, "master", "test.txt", "hello", "init")
                    val pr = pullRequestRepository.save(
                        PullRequest(
                            title = "리뷰어없음 제거시도 PR", body = "...", toProject = toProject, fromProject = fromProject,
                            toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                            contributor = contributor, receiver = receiver, state = State.OPEN
                        )
                    )

                    pullRequestService.removeReviewer(pr.id!!, receiver)

                    pullRequestRepository.findById(pr.id!!).get().reviewers.size shouldBe 0
                }

                it("removeReviewer - 존재하지 않는 pullRequestId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.removeReviewer(999999L, receiver)
                    }
                }

                it("removeReviewer - 존재하지 않는 reviewer면 예외를 던져야 한다") {
                    val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                    createCommit(toBareDir, "master", "test.txt", "hello", "init")
                    val pr = pullRequestRepository.save(
                        PullRequest(
                            title = "리뷰어없음 제거 PR", body = "...", toProject = toProject, fromProject = fromProject,
                            toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
                            contributor = contributor, receiver = receiver, state = State.OPEN
                        )
                    )
                    val ghostReviewer = User(id = 999999L, loginId = "ghost-reviewer2", name = "유령리뷰어2")

                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.removeReviewer(pr.id!!, ghostReviewer)
                    }
                }

                it("deleteFromBranch - 존재하지 않는 pullRequestId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.deleteFromBranch(999999L)
                    }
                }

                it("restoreFromBranch - 존재하지 않는 pullRequestId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        pullRequestService.restoreFromBranch(999999L)
                    }
                }
            }
        }
    }
}
