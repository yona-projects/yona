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
import com.github.yonaprojects.yona.domain.project.GitService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommits
import java.io.File
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.RefSpec
import java.nio.file.Files
import java.time.Instant
import java.util.Date
import java.util.TimeZone
import io.mockk.verifyOrder

// 실제 bare 저장소에 순차 커밋 2개를 만들고 (초기 커밋 objectId, 두번째 커밋 objectId, bare 저장소 디렉터리)를 반환한다.
// handleGitPostReceiveEvent()는 gitService.getRepositoryPath()가 반환한 File로 RepositoryBuilder를 직접
// 여는 실제 코드라 mock으로 우회할 수 없다(BareCommitSpec/PullRequestServiceSpec과 동일한 이유).
private fun seedTwoCommits(branch: String = "main"): Triple<ObjectId, ObjectId, File> {
    val gitBaseDir = Files.createTempDirectory("yona-gitpostreceive-test").toFile()
    val bareDir = File(gitBaseDir, "tester/repo.git")
    Git.init().setDirectory(bareDir).setBare(true).call().close()

    val workingDir = Files.createTempDirectory("yona-gitpostreceive-work").toFile()
    val git = Git.init().setDirectory(workingDir).call()
    val file = File(workingDir, "file.txt")
    file.writeText("first")
    git.add().addFilepattern("file.txt").call()
    val author = PersonIdent("tester", "tester@yona.io", Date(946684800000L), TimeZone.getTimeZone("UTC"))
    val committer = PersonIdent("committer", "committer@yona.io")
    val firstCommit = git.commit().setSign(false).setAuthor(author).setCommitter(committer).setMessage("fix #42 first commit #42").call()

    file.writeText("second")
    git.add().addFilepattern("file.txt").call()
    val secondCommit = git.commit().setSign(false).setAuthor("tester", "tester@yona.io").setMessage("second commit").call()

    val config = git.repository.config
    config.setString("remote", "origin", "url", bareDir.absolutePath)
    config.save()
    git.push().setRemote("origin").setRefSpecs(RefSpec("HEAD:refs/heads/$branch")).call()
    git.close()

    return Triple(firstCommit.id, secondCommit.id, bareDir)
}


class GitPostReceiveEventListenerSpec : DescribeSpec({
    val gitService = mockk<GitService>()
    val notificationEventRecorder = mockk<NotificationEventRecorder>(relaxed = true)
    val issueRepository = mockk<IssueRepository>()
    val issueEventRepository = mockk<IssueEventRepository>()
    val webhookService = mockk<WebhookService>(relaxed = true)
    val watchService = mockk<WatchService>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    val listener = GitPostReceiveEventListener(
        gitService, CommitPostProcessingService(
            notificationEventRecorder, CommitIssueReferenceService(issueRepository, issueEventRepository),
            webhookService, watchService, eventPublisher
        )
    )

    val project = Project(id = 1L, name = "yona-project", owner = "gildong")
    val sender = User(id = 9L, loginId = "gildong", name = "길동", email = "gildong@example.com")

    beforeTest {
        clearMocks(issueRepository, issueEventRepository, webhookService, notificationEventRecorder, watchService, eventPublisher, answers = false)
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

    
    describe("GitPostReceiveEventListener.handleGitPostReceiveEvent") {
        it("should return early if repoDir does not exist") {
            val mockFile = mockk<File>()
            every { mockFile.exists() } returns false
            every { mockFile.absolutePath } returns "/fake/path"
            every { gitService.getRepositoryPath(any(), any()) } returns mockFile
            
            val event = GitPostReceiveEvent(project, sender, emptyList())
            listener.handleGitPostReceiveEvent(event)
            
            verify(exactly = 0) { notificationEventRecorder.record(any()) }
        }
        
        it("should handle exceptions silently") {
            val mockFile = mockk<File>()
            every { mockFile.exists() } returns true
            every { gitService.getRepositoryPath(any(), any()) } returns mockFile
            // This will throw exception because it's not a real git repo
            
            val event = GitPostReceiveEvent(project, sender, listOf(mockk(relaxed = true)))
            listener.handleGitPostReceiveEvent(event)
            
            verify(exactly = 0) { notificationEventRecorder.record(any()) }
        }
    }



    // Exercise collection and conversion through real Git objects, not listener internals.
    describe("GitPostReceiveEventListener.handleGitPostReceiveEvent (실제 bare 저장소)") {
        it("two CREATE refs preserve two events with the pusher and processing time") {
            val (firstId, _, bareDir) = seedTwoCommits()
            every { gitService.getRepositoryPath(any(), any()) } returns bareDir
            val issue = Issue(id = 100L, title = "bug", project = project, number = 42L)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val saved = mutableListOf<IssueEvent>()
            every { issueEventRepository.save(capture(saved)) } answers { firstArg() }
            val pushed = slot<PushedVcsCommits>()
            every { webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, capture(pushed)) } returns Unit
            val before = Instant.now()
            listener.handleGitPostReceiveEvent(GitPostReceiveEvent(project, sender, listOf(
                ReceiveCommand(ObjectId.zeroId(), firstId, "refs/heads/a"),
                ReceiveCommand(ObjectId.zeroId(), firstId, "refs/heads/b")
            )))
            val after = Instant.now()
            saved.size shouldBe 2
            saved.forEach {
                it.issue shouldBe issue
                it.senderLoginId shouldBe sender.loginId
                it.senderEmail shouldBe sender.email
                it.newValue shouldBe firstId.name
                it.oldValue shouldBe null
                it.eventType shouldBe EventType.ISSUE_REFERRED_FROM_COMMIT
                (it.created >= before && it.created <= after) shouldBe true
            }
            pushed.captured.refNames shouldBe listOf("refs/heads/a", "refs/heads/b")
            pushed.captured.commits.forEach {
                it.id shouldBe firstId.name
                it.message shouldBe "fix #42 first commit #42"
                it.authorName shouldBe "tester"
                it.authorEmail shouldBe "tester@yona.io"
                it.committerName shouldBe "committer"
                it.committerEmail shouldBe "committer@yona.io"
                it.timestamp shouldBe Instant.parse("2000-01-01T00:00:00Z")
            }
            verifyOrder {
                notificationEventRecorder.record(any())
                webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, any())
                issueEventRepository.save(any())
                issueEventRepository.save(any())
            }
        }
        it("UPDATE 커맨드로 push된 커밋을 파싱해 알림·이슈참조·웹훅까지 전부 처리해야 한다") {
            val (firstId, secondId, bareDir) = seedTwoCommits()
            every { gitService.getRepositoryPath(any(), any()) } returns bareDir
            every { issueRepository.findByProjectAndNumber(any(), any()) } returns null
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            val command = ReceiveCommand(firstId, secondId, "refs/heads/main")
            listener.handleGitPostReceiveEvent(GitPostReceiveEvent(project, sender, listOf(command)))

            savedNotification.captured.eventType shouldBe EventType.NEW_COMMIT
            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, any()) }
        }

        it("CREATE 커맨드(oldId가 zero)로 새 브랜치 최초 커밋만 있으면 그 커밋 하나만 처리하고 이슈 참조도 기록해야 한다") {
            val (firstId, _, bareDir) = seedTwoCommits()
            every { gitService.getRepositoryPath(any(), any()) } returns bareDir
            val issue = Issue(id = 100L, title = "버그", body = "...", project = project, number = 42L)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val savedSlot = slot<IssueEvent>()
            every { issueEventRepository.save(capture(savedSlot)) } answers { firstArg() }
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            val command = ReceiveCommand(ObjectId.zeroId(), firstId, "refs/heads/new-branch")
            listener.handleGitPostReceiveEvent(GitPostReceiveEvent(project, sender, listOf(command)))

            savedNotification.captured.title shouldBe "[yona-project] 1개의 커밋이 refs/heads/new-branch 브랜치로 푸시되었습니다."
            savedSlot.captured.issue shouldBe issue
        }

        it("DELETE 커맨드는 isNewOrUpdateCommand=false라 처리 대상에서 제외돼야 한다") {
            val (firstId, _, bareDir) = seedTwoCommits()
            every { gitService.getRepositoryPath(any(), any()) } returns bareDir

            val deleteCommand = ReceiveCommand(firstId, ObjectId.zeroId(), "refs/heads/main")
            listener.handleGitPostReceiveEvent(GitPostReceiveEvent(project, sender, listOf(deleteCommand)))

            verify(exactly = 0) { notificationEventRecorder.record(any()) }
            verify(exactly = 0) { issueEventRepository.save(any()) }
        }

        it("UPDATE_NONFASTFORWARD 커맨드도 UPDATE와 동일하게 처리 대상이어야 한다") {
            val (firstId, secondId, bareDir) = seedTwoCommits()
            every { gitService.getRepositoryPath(any(), any()) } returns bareDir
            every { issueRepository.findByProjectAndNumber(any(), any()) } returns null
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            val command = ReceiveCommand(firstId, secondId, "refs/heads/main", ReceiveCommand.Type.UPDATE_NONFASTFORWARD)
            listener.handleGitPostReceiveEvent(GitPostReceiveEvent(project, sender, listOf(command)))

            savedNotification.captured.eventType shouldBe EventType.NEW_COMMIT
        }

        it("프로젝트 owner가 null이면 빈 문자열로 저장소 경로를 조회해야 한다") {
            val (firstId, secondId, bareDir) = seedTwoCommits()
            project.owner = null
            every { gitService.getRepositoryPath("", "yona-project") } returns bareDir
            every { issueRepository.findByProjectAndNumber(any(), any()) } returns null
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            val command = ReceiveCommand(firstId, secondId, "refs/heads/main")
            listener.handleGitPostReceiveEvent(GitPostReceiveEvent(project, sender, listOf(command)))

            savedNotification.captured.eventType shouldBe EventType.NEW_COMMIT
        }

        it("존재하지 않는 objectId를 파싱하려 하면 예외를 삼키고 빈 커밋 목록으로 처리해야 한다") {
            val (_, _, bareDir) = seedTwoCommits()
            every { gitService.getRepositoryPath(any(), any()) } returns bareDir

            val bogusId = ObjectId.fromString("1111111111111111111111111111111111111111")
            val command = ReceiveCommand(ObjectId.zeroId(), bogusId, "refs/heads/main")
            listener.handleGitPostReceiveEvent(GitPostReceiveEvent(project, sender, listOf(command)))

            verify(exactly = 0) { notificationEventRecorder.record(any()) }
            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }
    }
})
