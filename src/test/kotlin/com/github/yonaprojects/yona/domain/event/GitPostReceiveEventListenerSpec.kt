package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.project.GitService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import com.github.yonaprojects.yona.domain.event.GitPostReceiveEvent
import java.io.File
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.RefSpec
import java.nio.file.Files

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
    val firstCommit = git.commit().setSign(false).setAuthor("tester", "tester@yona.io").setMessage("fix #42 first commit").call()

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

private fun testCommit(message: String): RevCommit {
    val repo = InMemoryRepository(DfsRepositoryDescription())
    val inserter = repo.newObjectInserter()
    val blobId = inserter.insert(Constants.OBJ_BLOB, "content".toByteArray())
    val tree = TreeFormatter()
    tree.append("file.txt", FileMode.REGULAR_FILE, blobId)
    val treeId = inserter.insert(tree)
    val commitBuilder = CommitBuilder()
    commitBuilder.setTreeId(treeId)
    val ident = PersonIdent("tester", "tester@yona.io")
    commitBuilder.author = ident
    commitBuilder.committer = ident
    commitBuilder.message = message
    val commitId = inserter.insert(commitBuilder)
    inserter.flush()
    return RevWalk(repo).use { it.parseCommit(commitId) }
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
        gitService, notificationEventRecorder, issueRepository, issueEventRepository, webhookService,
        watchService, eventPublisher
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

    describe("GitPostReceiveEventListener.recordReferredIssues") {
        it("커밋 메시지가 언급한 이슈가 프로젝트에 존재하면 IssueEvent를 기록해야 한다") {
            val issue = Issue(id = 100L, title = "버그", body = "...", project = project, number = 42L)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val savedSlot = slot<IssueEvent>()
            every { issueEventRepository.save(capture(savedSlot)) } answers { firstArg() }

            listener.recordReferredIssues("fix #42 bug", "abc123commit", project, sender)

            savedSlot.captured.issue shouldBe issue
            savedSlot.captured.senderLoginId shouldBe "gildong"
            savedSlot.captured.senderEmail shouldBe "gildong@example.com"
            savedSlot.captured.newValue shouldBe "abc123commit"
            savedSlot.captured.eventType shouldBe EventType.ISSUE_REFERRED_FROM_COMMIT
        }

        it("언급된 이슈 번호가 프로젝트에 존재하지 않으면 조용히 스킵해야 한다") {
            every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

            listener.recordReferredIssues("close #999", "def456", project, sender)

            verify(exactly = 0) { issueEventRepository.save(any()) }
        }

        it("커밋 메시지에 이슈 참조가 없으면 아무 것도 저장하지 않아야 한다") {
            listener.recordReferredIssues("just a regular commit", "ghi789", project, sender)

            verify(exactly = 0) { issueRepository.findByProjectAndNumber(any(), any()) }
            verify(exactly = 0) { issueEventRepository.save(any()) }
        }

        it("커밋 메시지에 여러 이슈가 언급되면 각각에 대해 IssueEvent를 기록해야 한다") {
            val issue1 = Issue(id = 1L, title = "A", body = "", project = project, number = 1L)
            val issue2 = Issue(id = 2L, title = "B", body = "", project = project, number = 2L)
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue1
            every { issueRepository.findByProjectAndNumber(project, 2L) } returns issue2
            every { issueEventRepository.save(any()) } answers { firstArg() }

            listener.recordReferredIssues("fixes #1 and #2", "jkl012", project, sender)

            verify(exactly = 2) { issueEventRepository.save(any()) }
        }
    }

    describe("GitPostReceiveEventListener.processCommitsNotification (P1-25, yona Webhook.sendRequestToPayloadUrl(commits,...) 대응)") {
        it("push된 커밋이 있으면 NotificationEvent를 저장하고 NEW_COMMIT 웹훅을 발송해야 한다") {
            val commit = testCommit("fix bug")
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            listener.processCommitsNotification(listOf(commit), listOf("refs/heads/master"), project, sender)

            savedNotification.captured.eventType shouldBe EventType.NEW_COMMIT
            savedNotification.captured.senderId shouldBe sender.id

            verify(exactly = 1) {
                webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, any())
            }
        }

        it("push된 커밋이 없으면 알림도 웹훅도 발생시키지 않아야 한다") {
            listener.processCommitsNotification(emptyList(), listOf("refs/heads/master"), project, sender)

            verify(exactly = 0) { notificationEventRecorder.record(any()) }
            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }

        it("record()가 null을 반환하면(이미 처리된 이벤트) publishEvent를 호출하지 않고도 웹훅은 발송해야 한다") {
            val commit = testCommit("fix bug")
            every { notificationEventRecorder.record(any()) } returns null

            listener.processCommitsNotification(listOf(commit), listOf("refs/heads/master"), project, sender)

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, any()) }
        }

        it("push된 커밋이 있으면 프로젝트 워처를 수신자로 계산해 NotificationEvent를 publish해야 한다 (P1-46)") {
            val watcher = User(id = 20L, loginId = "watcher1", name = "워처")
            every {
                watchService.findActualWatchers(
                    baseWatchers = emptySet(),
                    resourceType = ResourceType.PROJECT,
                    resourceId = "1",
                    projectId = 1L,
                    eventType = EventType.NEW_COMMIT
                )
            } returns setOf(watcher, sender)

            val commit = testCommit("fix bug")
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            listener.processCommitsNotification(listOf(commit), listOf("refs/heads/master"), project, sender)

            // 워처 목록에 발신자 본인이 섞여 있어도 자기 자신에게는 알림을 보내지 않는다(기존 다른 리스너들과 동일한 관례)
            savedNotification.captured.receivers shouldBe setOf(watcher)
            verify(exactly = 1) { eventPublisher.publishEvent(savedNotification.captured) }
        }

        it("ref가 2개 이상으로 push되면 제목에 개별 브랜치명 대신 총 커밋수 문구를 써야 한다") {
            val commit = testCommit("fix bug")
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRecorder.record(capture(savedNotification)) } answers { firstArg() }

            listener.processCommitsNotification(listOf(commit), listOf("refs/heads/a", "refs/heads/b"), project, sender)

            savedNotification.captured.title shouldBe "[yona-project] 1개의 커밋이 푸시되었습니다."
        }
    }

    // handleGitPostReceiveEvent()는 gitService.getRepositoryPath()가 반환한 File로 RepositoryBuilder를
    // 직접 열어 RevWalk까지 실행하는 실제 코드라, isNewOrUpdateCommand/parseCommitsFrom/
    // processIssueReferredFromCommit의 분기는 실제 bare 저장소 없이는 태울 수 없다.
    describe("GitPostReceiveEventListener.handleGitPostReceiveEvent (실제 bare 저장소)") {
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
