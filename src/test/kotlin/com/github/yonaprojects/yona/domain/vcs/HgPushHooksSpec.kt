package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranch
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.event.HgPostReceiveEvent
import com.github.yonaprojects.yona.domain.event.RelatedPullRequestMergeEvent
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.lib.HgRepository as NativeHgRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.io.File
import java.nio.file.Files
import java.util.Optional

// yona-wiki P3-21/P3-22 — GitPushHooksSpec와 대칭인 Mercurial(pushkey) 버전. 실제 훅 지점
// (hg4j Wire1Commands.pushkey()가 만드는 context)을 그대로 흉내 내어 HgBranchProtectionPrePushkeyHook/
// HgYonaPostPushkeyHook을 호출한다 — classify()/newlyIntroducedRevisions()가 실제 changelog
// Revlog(파일시스템)를 읽으므로, mock이 아니라 실제 로컬 hg4j 저장소를 만들어 검증한다
// (HgRepositorySpec과 동일한 패턴).
class HgPushHooksSpec : DescribeSpec({

    fun newTempRepoDir(): File {
        val dir = Files.createTempDirectory("yona-hg-push-hooks-test").toFile()
        Hg.init().setDirectory(dir).call()
        return dir
    }

    fun commitFile(repoDir: File, path: String, content: String, message: String, author: String = "tester <tester@example.com>"): String {
        val file = File(repoDir, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return Hg.open(repoDir).use { hg ->
            hg.add().addFile(path).call()
            val node = hg.commit().setAuthor(author).setMessage(message).call()
            io.github.search5.hg4j.lib.NodeId(node).toHex()
        }
    }

    fun contextOf(nativeRepo: NativeHgRepository, key: String, old: String, new: String, namespace: String = "bookmarks"): MutableMap<String, Any> {
        val map = HashMap<String, Any>()
        map["namespace"] = namespace
        map["key"] = key
        map["old"] = old
        map["new"] = new
        map["repository"] = nativeRepo
        return map
    }

    describe("HgBookmarkAncestry.classify") {
        it("old가 비어있고 new가 실제 리비전이면 CREATE") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            val revlog = HgBookmarkAncestry.changelogRevlogOf(nativeRepo)

            HgBookmarkAncestry.classify(revlog, "", hex) shouldBe HgBookmarkChangeType.CREATE
        }

        it("new가 비어있으면 DELETE") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            val revlog = HgBookmarkAncestry.changelogRevlogOf(nativeRepo)

            HgBookmarkAncestry.classify(revlog, hex, "") shouldBe HgBookmarkChangeType.DELETE
        }

        it("old가 new의 조상이면 UPDATE(fast-forward)") {
            val repoDir = newTempRepoDir()
            val hex1 = commitFile(repoDir, "a.txt", "hello", "v1")
            val hex2 = commitFile(repoDir, "a.txt", "hello2", "v2")
            val nativeRepo = NativeHgRepository(repoDir)
            val revlog = HgBookmarkAncestry.changelogRevlogOf(nativeRepo)

            HgBookmarkAncestry.classify(revlog, hex1, hex2) shouldBe HgBookmarkChangeType.UPDATE
        }

        it("old가 new의 조상이 아니면 UPDATE_NONFASTFORWARD(강제 push)") {
            val repoDir = newTempRepoDir()
            val hex1 = commitFile(repoDir, "a.txt", "hello", "v1")
            val hex2 = commitFile(repoDir, "a.txt", "hello2", "v2")
            // v1으로 되돌아가 v2와는 무관한 다른 갈래(v1b)를 만든다 — v1b는 v1의 자손이지만
            // v2의 자손은 아니다(v1b의 부모는 v1이지 v2가 아니다). 북마크가 v2에 있다가 v1b로
            // "강제로" 이동하는 시나리오를 재현한다(old=v2, new=v1b).
            Hg.open(repoDir).use { hg -> hg.update().setRevision(hex1).call() }
            val hex1b = commitFile(repoDir, "b.txt", "branch", "v1b")
            val nativeRepo = NativeHgRepository(repoDir)
            val revlog = HgBookmarkAncestry.changelogRevlogOf(nativeRepo)

            HgBookmarkAncestry.classify(revlog, hex2, hex1b) shouldBe HgBookmarkChangeType.UPDATE_NONFASTFORWARD
        }
    }

    describe("HgBranchProtectionPrePushkeyHook") {
        val project = Project(id = 1L, name = "yona-project", owner = "gildong")
        val pusher = User(id = 9L, loginId = "gildong", name = "길동")
        val protectedBranchRepository = mockk<ProtectedBranchRepository>()
        val projectUserRepository = mockk<ProjectUserRepository>()
        val gpgSignatureVerifier = mockk<GpgSignatureVerifier>()

        fun newHook() = HgBranchProtectionPrePushkeyHook(
            project, pusher, protectedBranchRepository, projectUserRepository, gpgSignatureVerifier
        )

        beforeTest {
            clearMocks(protectedBranchRepository, projectUserRepository, gpgSignatureVerifier)
            every { projectUserRepository.findByProjectIdAndUserId(any(), any()) } returns Optional.empty()
            every { gpgSignatureVerifier.verify(any<io.github.search5.hg4j.api.HgCommit>()) } returns GpgVerificationStatus.VERIFIED
        }

        it("네임스페이스가 bookmarks가 아니면 검사하지 않고 항상 허용해야 한다") {
            val repoDir = newTempRepoDir()
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", requirePullRequest = true))

            val result = newHook().run(contextOf(nativeRepo, "main", "", "a".repeat(40), namespace = "phases"))

            result shouldBe true
        }

        it("이 프로젝트에 브랜치 보호 규칙이 전혀 없으면 어떤 push도 거부하지 않아야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns emptyList()

            val result = newHook().run(contextOf(nativeRepo, "main", "", hex))

            result shouldBe true
        }

        it("require_pull_request가 켜진 북마크로의 직접 push(CREATE)는 거부돼야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", requirePullRequest = true))

            val result = newHook().run(contextOf(nativeRepo, "main", "", hex))

            result shouldBe false
        }

        it("require_pull_request가 켜져 있어도 삭제(DELETE)는 거부하지 않아야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", requirePullRequest = true))

            val result = newHook().run(contextOf(nativeRepo, "main", hex, ""))

            result shouldBe true
        }

        it("disallow_delete가 켜진 북마크의 삭제는 거부돼야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", disallowDelete = true))

            val result = newHook().run(contextOf(nativeRepo, "main", hex, ""))

            result shouldBe false
        }

        it("disallow_force_push가 켜진 북마크로의 강제 push(non-fast-forward)는 거부돼야 한다") {
            val repoDir = newTempRepoDir()
            val hex1 = commitFile(repoDir, "a.txt", "hello", "v1")
            val hex2 = commitFile(repoDir, "a.txt", "hello2", "v2")
            Hg.open(repoDir).use { hg -> hg.update().setRevision(hex1).call() }
            val hex1b = commitFile(repoDir, "b.txt", "branch", "v1b")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", disallowForcePush = true))

            val result = newHook().run(contextOf(nativeRepo, "main", hex2, hex1b))

            result shouldBe false
        }

        it("restrict_push_to 목록에 없는 사용자의 push는 거부돼야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", restrictPushTo = "alice, bob"))

            val result = newHook().run(contextOf(nativeRepo, "main", "", hex))

            result shouldBe false
        }

        it("restrict_push_to 목록에 있는 사용자의 push는 허용돼야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", restrictPushTo = "alice, gildong"))

            val result = newHook().run(contextOf(nativeRepo, "main", "", hex))

            result shouldBe true
        }

        it("require_signed_commits가 켜져 있고 새로 들여오는 커밋이 검증되지 않으면 거부돼야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", requireSignedCommits = true))
            every { gpgSignatureVerifier.verify(any<io.github.search5.hg4j.api.HgCommit>()) } returns GpgVerificationStatus.UNSIGNED

            val result = newHook().run(contextOf(nativeRepo, "main", "", hex))

            result shouldBe false
        }

        it("require_signed_commits가 켜져 있고 새로 들여오는 커밋이 전부 검증되면 허용돼야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            every { protectedBranchRepository.findByProjectId(1L) } returns
                listOf(ProtectedBranch(project = project, branchPattern = "main", requireSignedCommits = true))
            every { gpgSignatureVerifier.verify(any<io.github.search5.hg4j.api.HgCommit>()) } returns GpgVerificationStatus.VERIFIED

            val result = newHook().run(contextOf(nativeRepo, "main", "", hex))

            result shouldBe true
        }

        describe("admins_can_bypass") {
            fun makeManager() {
                every { projectUserRepository.findByProjectIdAndUserId(1L, 9L) } returns
                    Optional.of(ProjectUser(user = pusher, project = project, role = Role(id = RoleType.MANAGER.roleType)))
            }

            it("admins_can_bypass=true(기본값)이면 매니저는 disallow_delete를 우회할 수 있어야 한다") {
                makeManager()
                val repoDir = newTempRepoDir()
                val hex = commitFile(repoDir, "a.txt", "hello", "v1")
                val nativeRepo = NativeHgRepository(repoDir)
                every { protectedBranchRepository.findByProjectId(1L) } returns
                    listOf(ProtectedBranch(project = project, branchPattern = "main", disallowDelete = true, adminsCanBypass = true))

                val result = newHook().run(contextOf(nativeRepo, "main", hex, ""))

                result shouldBe true
            }

            it("admins_can_bypass=false이면 매니저도 우회할 수 없어야 한다") {
                makeManager()
                val repoDir = newTempRepoDir()
                val hex = commitFile(repoDir, "a.txt", "hello", "v1")
                val nativeRepo = NativeHgRepository(repoDir)
                every { protectedBranchRepository.findByProjectId(1L) } returns
                    listOf(ProtectedBranch(project = project, branchPattern = "main", disallowDelete = true, adminsCanBypass = false))

                val result = newHook().run(contextOf(nativeRepo, "main", hex, ""))

                result shouldBe false
            }
        }
    }

    describe("HgYonaPostPushkeyHook") {
        val projectRepository = mockk<ProjectRepository>()
        val pullRequestRepository = mockk<PullRequestRepository>()
        val pushedBranchRepository = mockk<PushedBranchRepository>()
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val project = Project(id = 1L, name = "yona-project", owner = "gildong")
        val pusher = User(id = 9L, loginId = "gildong", name = "길동")
        lateinit var meterRegistry: SimpleMeterRegistry

        fun newHook() = HgYonaPostPushkeyHook(
            project, pusher, projectRepository, pullRequestRepository, pushedBranchRepository, eventPublisher, meterRegistry
        )

        beforeTest {
            clearMocks(projectRepository, pullRequestRepository, pushedBranchRepository, eventPublisher)
            meterRegistry = SimpleMeterRegistry()
            every { projectRepository.save(any()) } returns project
            every { pushedBranchRepository.findByProjectAndPushedDateBefore(any(), any()) } returns emptyList()
            every { pushedBranchRepository.findByProjectAndName(any(), any()) } returns Optional.empty()
            every { pullRequestRepository.existsByFromProjectAndFromBranch(any(), any()) } returns false
            every { pullRequestRepository.findRelatedPullRequests(any(), any()) } returns emptyList()
            every { pushedBranchRepository.save(any()) } answers { it.invocation.args[0] as PushedBranch }
            every { pushedBranchRepository.deleteAll(any<List<PushedBranch>>()) } returns Unit
            every { pushedBranchRepository.delete(any()) } returns Unit
        }

        it("push가 일어나면 project.lastPushedDate가 갱신되어야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)

            newHook().run(contextOf(nativeRepo, "main", "", hex))

            verify { projectRepository.save(project) }
            meterRegistry.timer("yona.hg.push_hook.duration").count() shouldBe 1L
        }

        it("push가 일어나면 HgPostReceiveEvent가 발행되어야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            val captured = slot<HgPostReceiveEvent>()
            every { eventPublisher.publishEvent(capture(captured)) } returns Unit

            newHook().run(contextOf(nativeRepo, "main", "", hex))

            captured.captured.project shouldBe project
            captured.captured.user shouldBe pusher
            captured.captured.move.name shouldBe "main"
            captured.captured.move.newNodeHex shouldBe hex
            captured.captured.move.changeType shouldBe HgBookmarkChangeType.CREATE
        }

        it("새 북마크 생성(CREATE)은 RelatedPullRequestMergeEvent를 발행하지 않아야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)

            newHook().run(contextOf(nativeRepo, "main", "", hex))

            verify(exactly = 0) { eventPublisher.publishEvent(match<Any> { it is RelatedPullRequestMergeEvent }) }
        }

        it("북마크가 갱신(UPDATE)되면 RelatedPullRequestMergeEvent가 발행되어야 한다") {
            val repoDir = newTempRepoDir()
            val hex1 = commitFile(repoDir, "a.txt", "hello", "v1")
            val hex2 = commitFile(repoDir, "a.txt", "hello2", "v2")
            val nativeRepo = NativeHgRepository(repoDir)
            val captured = slot<RelatedPullRequestMergeEvent>()
            every { eventPublisher.publishEvent(capture(captured)) } returns Unit

            newHook().run(contextOf(nativeRepo, "main", hex1, hex2))

            captured.captured.project shouldBe project
            captured.captured.branch shouldBe "main"
            captured.captured.sender shouldBe pusher
        }

        it("북마크가 삭제(DELETE)되면 관련 PullRequest가 삭제되고 RelatedPullRequestMergeEvent는 발행되지 않아야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)
            val relatedPr = mockk<PullRequest>(relaxed = true)
            every { pullRequestRepository.findRelatedPullRequests(project, "main") } returns listOf(relatedPr)
            every { pullRequestRepository.deleteAll(any<List<PullRequest>>()) } returns Unit

            newHook().run(contextOf(nativeRepo, "main", hex, ""))

            verify { pullRequestRepository.deleteAll(listOf(relatedPr)) }
            verify(exactly = 0) { eventPublisher.publishEvent(match<Any> { it is RelatedPullRequestMergeEvent }) }
        }

        describe("최근 push된 브랜치 추적") {
            it("새 북마크가 push되면 PushedBranch가 새로 저장되어야 한다") {
                val repoDir = newTempRepoDir()
                val hex = commitFile(repoDir, "a.txt", "hello", "v1")
                val nativeRepo = NativeHgRepository(repoDir)
                val saved = slot<PushedBranch>()
                every { pushedBranchRepository.save(capture(saved)) } answers { saved.captured }

                newHook().run(contextOf(nativeRepo, "feature-x", "", hex))

                saved.captured.name shouldBe "feature-x"
                saved.captured.project shouldBe project
            }

            it("이미 기록된 북마크를 다시 push하면 새로 만들지 않고 pushedDate만 갱신해야 한다") {
                val repoDir = newTempRepoDir()
                val hex1 = commitFile(repoDir, "a.txt", "hello", "v1")
                val hex2 = commitFile(repoDir, "a.txt", "hello2", "v2")
                val nativeRepo = NativeHgRepository(repoDir)
                val existing = PushedBranch(id = 5L, name = "main", project = project)
                every { pushedBranchRepository.findByProjectAndName(project, "main") } returns Optional.of(existing)

                newHook().run(contextOf(nativeRepo, "main", hex1, hex2))

                verify { pushedBranchRepository.save(existing) }
            }

            it("이 브랜치를 fromBranch로 하는 PullRequest가 이미 있으면 PushedBranch를 새로 만들지 않아야 한다") {
                val repoDir = newTempRepoDir()
                val hex = commitFile(repoDir, "a.txt", "hello", "v1")
                val nativeRepo = NativeHgRepository(repoDir)
                every { pullRequestRepository.existsByFromProjectAndFromBranch(project, "pr-branch") } returns true

                newHook().run(contextOf(nativeRepo, "pr-branch", "", hex))

                verify(exactly = 0) { pushedBranchRepository.save(any()) }
            }

            it("push마다 1시간 이상 지난 오래된 PushedBranch를 정리해야 한다") {
                val repoDir = newTempRepoDir()
                val hex = commitFile(repoDir, "a.txt", "hello", "v1")
                val nativeRepo = NativeHgRepository(repoDir)
                val old = PushedBranch(id = 1L, name = "old-branch", project = project)
                every { pushedBranchRepository.findByProjectAndPushedDateBefore(project, any()) } returns listOf(old)

                newHook().run(contextOf(nativeRepo, "main", "", hex))

                verify { pushedBranchRepository.deleteAll(listOf(old)) }
            }

            it("북마크가 삭제되면 해당 PushedBranch 레코드도 함께 삭제되어야 한다") {
                val repoDir = newTempRepoDir()
                val hex = commitFile(repoDir, "a.txt", "hello", "v1")
                val nativeRepo = NativeHgRepository(repoDir)
                val existing = PushedBranch(id = 7L, name = "feature-y", project = project)
                every { pushedBranchRepository.findByProjectAndName(project, "feature-y") } returns Optional.of(existing)

                newHook().run(contextOf(nativeRepo, "feature-y", hex, ""))

                verify { pushedBranchRepository.delete(existing) }
            }
        }

        it("namespace가 bookmarks가 아니면 아무 것도 하지 않아야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "v1")
            val nativeRepo = NativeHgRepository(repoDir)

            newHook().run(contextOf(nativeRepo, "main", "", hex, namespace = "phases"))

            verify(exactly = 0) { projectRepository.save(any()) }
            verify(exactly = 0) { pushedBranchRepository.save(any()) }
        }
    }
})
