package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.CommitIssueReferenceService
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.HgBookmarkChangeType
import com.github.yonaprojects.yona.domain.vcs.HgBookmarkMove
import com.github.yonaprojects.yona.domain.vcs.HgRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommits
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.lib.NodeId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.io.File
import java.nio.file.Files
import java.time.Instant
import io.mockk.verifyOrder

// yona-wiki P3-22 — GitPostReceiveEventListenerSpec와 대칭인 Mercurial 버전. parseCommitsFrom()이
// 실제 changelog Revlog(파일시스템)를 여는 실제 코드라(BareCommitSpec/PullRequestServiceSpec과
// 동일한 이유로 mock 불가) 실제 로컬 hg4j 저장소를 만들어 검증한다.
private fun newTempRepoDir(): File {
    val dir = Files.createTempDirectory("yona-hgpostreceive-test").toFile()
    Hg.init().setDirectory(dir).call()
    return dir
}

private fun commitFile(repoDir: File, path: String, content: String, message: String, author: String = "tester <tester@example.com>"): String {
    val file = File(repoDir, path)
    file.parentFile?.mkdirs()
    file.writeText(content)
    return Hg.open(repoDir).use { hg ->
        hg.add().addFile(path).call()
        val node = hg.commit().setAuthor(author).setDate(946684800L, 0).setMessage(message).call()
        NodeId(node).toHex()
    }
}

class HgPostReceiveEventListenerSpec : DescribeSpec({
    val repositoryService = mockk<RepositoryService>()
    val notificationEventRecorder = mockk<NotificationEventRecorder>(relaxed = true)
    val issueRepository = mockk<IssueRepository>()
    val issueEventRepository = mockk<IssueEventRepository>()
    val webhookService = mockk<WebhookService>(relaxed = true)
    val watchService = mockk<WatchService>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    val listener = HgPostReceiveEventListener(
        repositoryService, CommitPostProcessingService(
            notificationEventRecorder, CommitIssueReferenceService(issueRepository, issueEventRepository),
            webhookService, watchService, eventPublisher
        )
    )

    val project = Project(id = 1L, name = "yona-project", owner = "gildong")
    val sender = User(id = 9L, loginId = "gildong", name = "길동", email = "gildong@example.com")

    beforeTest {
        clearMocks(repositoryService, issueRepository, issueEventRepository, webhookService, notificationEventRecorder, watchService, eventPublisher, answers = false)
        every {
            watchService.findActualWatchers(
                baseWatchers = emptySet(),
                resourceType = ResourceType.PROJECT,
                resourceId = "1",
                projectId = 1L,
                eventType = EventType.NEW_COMMIT
            )
        } returns emptySet()
    }



    // handleHgPostReceiveEvent()는 RepositoryService.getRepository(project).getDirectory()로 실제
    // 로컬 hg 저장소를 열어 Revlog까지 실행하는 실제 코드라, mock으로 우회할 수 없다.
    describe("HgPostReceiveEventListener.handleHgPostReceiveEvent (실제 로컬 hg 저장소)") {
        it("two CREATE bookmarks preserve two events with the pusher and processing time") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "fix #42 #42", "author <author@example.com>")
            val hgRepo = mockk<HgRepository>()
            every { hgRepo.getDirectory() } returns repoDir
            every { repositoryService.getRepository(project) } returns hgRepo
            val issue = Issue(id = 100L, title = "bug", project = project, number = 42L)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val saved = mutableListOf<IssueEvent>()
            every { issueEventRepository.save(capture(saved)) } answers { firstArg() }
            val pushed = mutableListOf<PushedVcsCommits>()
            every { webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, capture(pushed)) } returns Unit
            val before = Instant.now()
            for (bookmark in listOf("a", "b")) {
                listener.handleHgPostReceiveEvent(HgPostReceiveEvent(
                    project, sender, HgBookmarkMove(bookmark, "", hex, HgBookmarkChangeType.CREATE)
                ))
            }
            val after = Instant.now()
            saved.size shouldBe 2
            saved.forEach {
                it.issue shouldBe issue
                it.senderLoginId shouldBe sender.loginId
                it.senderEmail shouldBe sender.email
                it.newValue shouldBe hex
                it.oldValue shouldBe null
                it.eventType shouldBe EventType.ISSUE_REFERRED_FROM_COMMIT
                (it.created >= before && it.created <= after) shouldBe true
            }
            pushed.map { it.refNames } shouldBe listOf(listOf("refs/heads/a"), listOf("refs/heads/b"))
            pushed.forEach { push ->
                val commit = push.commits.single()
                commit.id shouldBe hex
                commit.message shouldBe "fix #42 #42"
                commit.authorName shouldBe "author"
                commit.authorEmail shouldBe "author@example.com"
                commit.committerName shouldBe commit.authorName
                commit.committerEmail shouldBe commit.authorEmail
                commit.timestamp shouldBe Instant.parse("2000-01-01T00:00:00Z")
            }
            verifyOrder {
                notificationEventRecorder.record(any())
                webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, any<PushedVcsCommits>())
                issueEventRepository.save(any())
                notificationEventRecorder.record(any())
                webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, any<PushedVcsCommits>())
                issueEventRepository.save(any())
            }
        }
        it("UPDATE(북마크 갱신)로 push된 커밋을 파싱해 알림·이슈참조·웹훅까지 전부 처리해야 한다") {
            val repoDir = newTempRepoDir()
            val hex1 = commitFile(repoDir, "a.txt", "first", "first commit")
            val hex2 = commitFile(repoDir, "a.txt", "second", "fix #42 second commit")
            val hgRepo = mockk<HgRepository>()
            every { hgRepo.getDirectory() } returns repoDir
            every { repositoryService.getRepository(project) } returns hgRepo
            every { issueRepository.findByProjectAndNumber(any(), any()) } returns null
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            val move = HgBookmarkMove("main", hex1, hex2, HgBookmarkChangeType.UPDATE)
            listener.handleHgPostReceiveEvent(HgPostReceiveEvent(project, sender, move))

            savedNotification.captured.eventType shouldBe EventType.NEW_COMMIT
            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, any<PushedVcsCommits>()) }
        }

        it("CREATE(북마크 생성)로 새 커밋만 있으면 그 커밋 하나만 처리하고 이슈 참조도 기록해야 한다") {
            val repoDir = newTempRepoDir()
            val hex = commitFile(repoDir, "a.txt", "hello", "fix #42 initial")
            val hgRepo = mockk<HgRepository>()
            every { hgRepo.getDirectory() } returns repoDir
            every { repositoryService.getRepository(project) } returns hgRepo
            val issue = Issue(id = 100L, title = "버그", body = "...", project = project, number = 42L)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val savedSlot = slot<IssueEvent>()
            every { issueEventRepository.save(capture(savedSlot)) } answers { firstArg() }
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            val move = HgBookmarkMove("new-branch", "", hex, HgBookmarkChangeType.CREATE)
            listener.handleHgPostReceiveEvent(HgPostReceiveEvent(project, sender, move))

            savedNotification.captured.title shouldBe "[yona-project] 1개의 커밋이 refs/heads/new-branch 브랜치로 푸시되었습니다."
            savedSlot.captured.issue shouldBe issue
        }

        it("DELETE(북마크 삭제)는 새로 알릴 커밋이 없어 처리 대상에서 제외돼야 한다") {
            val move = HgBookmarkMove("feature-x", "a".repeat(40), "", HgBookmarkChangeType.DELETE)
            listener.handleHgPostReceiveEvent(HgPostReceiveEvent(project, sender, move))

            verify(exactly = 0) { notificationEventRecorder.record(any()) }
            verify(exactly = 0) { issueEventRepository.save(any()) }
            verify(exactly = 0) { repositoryService.getRepository(any()) }
        }

        it("저장소 디렉터리가 존재하지 않으면 조용히 리턴해야 한다") {
            val missingDir = File(Files.createTempDirectory("yona-hgpostreceive-missing").toFile(), "does-not-exist")
            val hgRepo = mockk<HgRepository>()
            every { hgRepo.getDirectory() } returns missingDir
            every { repositoryService.getRepository(project) } returns hgRepo

            val move = HgBookmarkMove("main", "", "a".repeat(40), HgBookmarkChangeType.CREATE)
            listener.handleHgPostReceiveEvent(HgPostReceiveEvent(project, sender, move))

            verify(exactly = 0) { notificationEventRecorder.record(any()) }
        }
    }
})
