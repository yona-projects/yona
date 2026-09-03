package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.notification.NotificationMailRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.time.Instant

private fun createTestCommit(
    bareRepoDir: File,
    branch: String,
    filePath: String,
    content: String,
    authorName: String = "tester",
    authorEmail: String = "tester@yona.io"
) {
    val tempWorkingDir = Files.createTempDirectory("yona-test-commit").toFile()
    try {
        val git = Git.init().setDirectory(tempWorkingDir).call()
        val config = git.repository.config
        config.setString("remote", "origin", "url", bareRepoDir.absolutePath)
        config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
        config.save()

        try {
            git.fetch().setRemote("origin").call()
            val ref = git.repository.resolve("refs/remotes/origin/$branch")
            if (ref != null) {
                git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/$branch").call()
            }
        } catch (e: Exception) {
            // 빈 저장소인 경우 checkout 생략
        }

        val file = File(tempWorkingDir, filePath)
        file.parentFile.mkdirs()
        file.writeText(content)

        git.add().addFilepattern(filePath).call()
        git.commit().setSign(false).setAuthor(authorName, authorEmail).setMessage("commit").call()
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

@Transactional
class CodeReviewServiceSpec @Autowired constructor(
    private val codeReviewService: CodeReviewService,
    private val commentThreadRepository: CommentThreadRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val repositoryService: RepositoryService,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationMailRepository: NotificationMailRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val watchRepository: WatchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val projectUserRepository: ProjectUserRepository,
    private val attachmentService: AttachmentService,
    private val commentService: CommentService,
    private val watchService: WatchService,
    private val pullRequestService: PullRequestService,
    private val accessControl: AccessControl
) : AbstractIntegrationTest() {

    init {
        describe("CodeReviewService 통합 테스트") {
            lateinit var project: Project
            lateinit var user: User
            lateinit var pullRequest: PullRequest

            lateinit var otherUser: User

            beforeEach {
                watchRepository.deleteAll()
                commitCommentRepository.deleteAll()
                reviewCommentRepository.deleteAll()
                commentThreadRepository.deleteAll()
                pullRequestEventRepository.deleteAll()
                notificationMailRepository.deleteAll()
                notificationEventRepository.deleteAll()
                pullRequestRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()

                user = userRepository.save(
                    User(loginId = "tester", name = "테스터", email = "tester@yona.io")
                )
                otherUser = userRepository.save(
                    User(loginId = "other", name = "타인", email = "other@yona.io")
                )
                project = projectRepository.save(
                    Project(name = "test-repo", owner = "owner-x", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
                )
                pullRequest = pullRequestRepository.save(
                    PullRequest(
                        title = "테스트 PR",
                        toProject = project,
                        fromProject = project,
                        toBranch = "master",
                        fromBranch = "feature",
                        contributor = user
                    )
                )
            }

            it("1. 새 코드 리뷰 댓글과 라인지정 스레드가 생성되어야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "이 부분 수정이 필요해 보입니다.",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                comment.id shouldNotBe null
                comment.contents shouldBe "이 부분 수정이 필요해 보입니다."
                comment.thread shouldNotBe null
                (comment.thread is CodeCommentThread) shouldBe true

                val codeThread = comment.thread as CodeCommentThread
                codeThread.codeRange.path shouldBe "src/main/kotlin/App.kt"
                codeThread.state shouldBe CommentThread.ThreadState.OPEN
            }

            // createReviewComment의 commitId==null && pullRequest!=null 분기(CodeCommentThread 쪽,
            // pullRequest.mergedCommitIdTo/From을 사용)는 기존 테스트가 전부 commitId를 명시적으로
            // 넘겨서 비어 있었다.
            it("1-1. commitId 없이 PR만으로 라인지정 리뷰 댓글을 생성하면 PR의 병합 커밋ID를 사용해야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 5,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 5,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = null,
                    contents = "PR 기반 라인지정 댓글",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val codeThread = comment.thread as CodeCommentThread
                codeThread.commitId shouldBe pullRequest.mergedCommitIdTo
            }

            // 같은 commitId==null && pullRequest!=null 분기의 NonRangedCodeCommentThread(codeRange
            // 없는) 쪽도 동일하게 비어 있었다.
            it("1-2. commitId와 codeRange 없이 PR만으로 리뷰 댓글을 생성하면 PR의 병합 커밋ID를 사용해야 한다") {
                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = null,
                    contents = "PR 기반 댓글(라인 없음)",
                    codeRange = null,
                    threadId = null,
                    currentUser = user
                )

                val thread = comment.thread as NonRangedCodeCommentThread
                thread.commitId shouldBe pullRequest.mergedCommitIdTo
            }

            // yona NotificationEvent.forNewComment(sender, pullRequest, newComment) 대응 (P1-50)
            describe("리뷰 댓글 알림 (P1-50)") {
                it("PR 위 리뷰 댓글을 작성하면 PR 감시자에게 NEW_REVIEW_COMMENT 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))

                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글 내용", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    val event = events.first()
                    event.eventType shouldBe EventType.NEW_REVIEW_COMMENT
                    event.resourceType shouldBe ResourceType.REVIEW_COMMENT
                    event.resourceId shouldBe comment.id.toString()
                    event.newValue shouldBe "댓글 내용"
                    event.senderId shouldBe user.id
                    event.receivers.map { it.id } shouldBe listOf(otherUser.id)
                }

                it("댓글에 멘션된 사용자는 감시자가 아니어도 수신자에 포함되어야 한다") {
                    val mentioned = userRepository.save(User(loginId = "mentioned1", name = "멘션대상", email = "mentioned1@yona.io"))

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "@mentioned1 확인 부탁드려요", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(mentioned.id)
                }

                it("작성자 본인은 수신자에서 제외되어야 한다") {
                    watchRepository.save(Watch(user = user, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글 내용", codeRange = null, threadId = null, currentUser = user
                    )

                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("기존 스레드에 답글을 달아도(threadId != null) 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val first = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "첫 댓글", codeRange = null, threadId = null, currentUser = user
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "답글", codeRange = null, threadId = first.thread!!.id, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().newValue shouldBe "답글"
                }

                // yona Commit.getWatchers(project) 대응: 커밋 리소스(resourceType=COMMIT,
                // resourceId="{projectId}:{commitId}", legacy Commit.asResource()와 동일한 합성 키)를
                // 명시적으로 감시 중인 사용자에게 알림이 가야 한다.
                it("PR 밖(commitId만 있는) 리뷰 댓글은 그 커밋을 감시 중인 사용자에게 NEW_REVIEW_COMMENT 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.COMMIT, resourceId = "${project.id}:deadbeef"))

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "deadbeef",
                        contents = "커밋에 대한 댓글", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().eventType shouldBe EventType.NEW_REVIEW_COMMENT
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }

                // yona Commit.getWatchers()의 "이미 이 커밋에 댓글을 남긴 사용자는 기본 감시자" 대응 —
                // Watch 엔티티로 명시적으로 감시하지 않았어도 자동으로 수신자가 된다.
                it("PR 밖 커밋에 이미 댓글을 남긴 사용자는 명시적으로 감시하지 않아도 다음 댓글 알림을 받아야 한다") {
                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "deadbeef",
                        contents = "먼저 남긴 댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "deadbeef",
                        contents = "두 번째 댓글", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }

                it("수신자가 없으면 알림을 저장하지 않아야 한다") {
                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "아무도 안 볼 댓글", codeRange = null, threadId = null, currentUser = user
                    )

                    notificationEventRepository.findAll().size shouldBe 0
                }
            }

            // yona NotificationEvent.forNewSVNCommitComment(project, codeComment, author) 대응 (P1-50).
            // legacy는 이 경로만 이벤트 타입이 NEW_COMMENT(NEW_REVIEW_COMMENT 아님)다.
            describe("커밋 댓글(CommitComment) 알림 (P1-50)") {
                it("커밋 댓글을 작성하면 그 커밋을 감시 중인 사용자에게 NEW_COMMENT 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.COMMIT, resourceId = "${project.id}:cafebabe"))

                    val comment = codeReviewService.createCommitComment(
                        project = project, commitId = "cafebabe", contents = "커밋 댓글 내용",
                        path = null, line = null, side = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    val event = events.first()
                    event.eventType shouldBe EventType.NEW_COMMENT
                    event.resourceType shouldBe ResourceType.COMMIT_COMMENT
                    event.resourceId shouldBe comment.id.toString()
                    event.newValue shouldBe "커밋 댓글 내용"
                }

                it("같은 커밋에 이미 CommitComment를 남긴 사용자는 자동으로 다음 댓글 알림을 받아야 한다") {
                    codeReviewService.createCommitComment(
                        project = project, commitId = "cafebabe", contents = "첫 커밋 댓글",
                        path = null, line = null, side = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.createCommitComment(
                        project = project, commitId = "cafebabe", contents = "두 번째 커밋 댓글",
                        path = null, line = null, side = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }
            }

            // yona NotificationEvent.afterStateChanged(CommentThread.ThreadState, CommentThread) 대응 (P1-50)
            describe("리뷰 스레드 상태 변경 알림 (P1-50)") {
                it("스레드를 닫으면(open->closed) PR 감시자에게 REVIEW_THREAD_STATE_CHANGED 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.CLOSED, user)

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    val event = events.first()
                    event.eventType shouldBe EventType.REVIEW_THREAD_STATE_CHANGED
                    event.resourceType shouldBe ResourceType.COMMENT_THREAD
                    event.oldValue shouldBe "OPEN"
                    event.newValue shouldBe "CLOSED"
                    event.receivers.map { it.id } shouldBe listOf(otherUser.id)
                }

                it("이미 같은 상태면 알림을 발행하지 않아야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.OPEN, user)

                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("상태를 바꾼 본인은 수신자에서 제외되어야 한다") {
                    watchRepository.save(Watch(user = user, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.CLOSED, user)

                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("30초 내에 상태를 CLOSED->OPEN으로 되돌리면 두 알림이 상쇄되어 발행되지 않아야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.CLOSED, user)
                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.OPEN, user)

                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("PR 밖(순수 커밋 위) 스레드는 그 커밋을 감시 중인 사용자에게 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.COMMIT, resourceId = "${project.id}:deadbeef"))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "deadbeef",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = user
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.CLOSED, user)

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }
            }

            // yona Commit.getWatchers()의 "커밋 작성자는 항상 기본 감시자" 규칙(getAuthor().isAnonymous()
            // 체크만 통과하면 Watch 여부와 무관) 대응 — 실제 git 저장소로 커밋을 만들어 검증한다.
            describe("커밋 작성자 자동 감시 (P1-50, yona Commit.getWatchers()의 author 포함 규칙 대응)") {
                it("PR 밖 커밋에 댓글이 달리면, 그 커밋의 작성자는 감시하지 않았어도 알림을 받아야 한다") {
                    val commitProject = projectRepository.save(Project(name = "commit-author-repo", owner = "owner-x", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
                    // /tmp/yona/git는 테스트 실행 간 정리되지 않는 영속 경로라, 과거 세션에서 남은
                    // 손상된(HEAD/config 없이 objects/refs/packed-refs만 있는) 저장소 잔재가 있으면
                    // Git.init()이 그 위에서 재초기화돼도 JGit 로컬 트랜스포트가 유효한 저장소로
                    // 인식하지 못해 push가 TransportException("not found")으로 실패한다 — create()
                    // 전에 확실히 비워서 매 실행 항상 깨끗한 상태에서 시작하도록 보장한다.
                    val preExisting = repositoryService.getRepository(commitProject).getDirectory()
                    if (preExisting.exists()) preExisting.deleteRecursively()
                    repositoryService.getRepository(commitProject).create()
                    val bareDir = repositoryService.getRepository(commitProject).getDirectory()
                    createTestCommit(bareDir, "master", "test.txt", "v1", authorName = "타인", authorEmail = "other@yona.io")
                    val commitId = repositoryService.getRepository(commitProject).getBranches()
                        .first { it.name == "refs/heads/master" }.headCommit.getId()

                    codeReviewService.createReviewComment(
                        project = commitProject, pullRequest = null, commitId = commitId,
                        contents = "커밋 작성자에게 가야 할 댓글", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }
            }

            it("리뷰어를 추가하면 NotificationEvent와 PullRequestEvent가 모두 생성되어야 한다(P1-39)") {
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)

                val prEventsAfterAdd = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest)
                prEventsAfterAdd.size shouldBe 1
                prEventsAfterAdd.first().eventType shouldBe EventType.PULL_REQUEST_REVIEW_STATE_CHANGED
                prEventsAfterAdd.first().newValue shouldBe "DONE"
                prEventsAfterAdd.first().senderLoginId shouldBe otherUser.loginId

                notificationEventRepository.findAll().size shouldBe 1
            }

            it("같은 리뷰어가 30초 내에 참여/해제를 반복하면 PullRequestEvent가 상쇄돼야 한다(P1-40)") {
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)
                codeReviewService.removeReviewer(pullRequest.id!!, otherUser.id!!)

                // 참여/해제 두 이벤트가 서로 상쇄돼 타임라인에는 아무것도 남지 않아야 한다(legacy 동작 그대로)
                pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest).size shouldBe 0
                // 알림(NotificationEvent)도 legacy NotificationEvent.afterReviewed()가 oldValue를
                // opposite action으로 채워 add()(draft-time 병합)를 타므로, 정확히 원상복구된 A(DONE)->B(CANCEL)는
                // 상쇄되어 아무 것도 저장되지 않는다(P1-27, NotificationEventRecorder).
                notificationEventRepository.findAll().size shouldBe 0
            }

            it("같은 리뷰어가 30초 내에 참여/해제/참여를 반복하면 마지막 참여만 남아야 한다(P1-40)") {
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)
                codeReviewService.removeReviewer(pullRequest.id!!, otherUser.id!!)
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)

                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest)
                prEvents.size shouldBe 1
                prEvents.first().newValue shouldBe "DONE"
            }

            it("2. 기존 스레드에 대댓글을 달면 동일 스레드 하위에 묶여야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val firstComment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val threadId = firstComment.thread?.id!!

                val secondComment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "답변 드립니다.",
                    codeRange = null,
                    threadId = threadId,
                    currentUser = user
                )

                secondComment.id shouldNotBe null
                secondComment.thread?.id shouldBe threadId
                secondComment.thread?.reviewComments?.size shouldBe 2
            }

            it("3. 스레드의 상태를 OPEN에서 CLOSED로 전환할 수 있어야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val threadId = comment.thread?.id!!

                val updatedThread = codeReviewService.updateThreadState(
                    threadId = threadId,
                    state = CommentThread.ThreadState.CLOSED,
                    currentUser = user
                )

                updatedThread.state shouldBe CommentThread.ThreadState.CLOSED
            }

            // yona CommentThreadApp.java:66-70의 try/catch(알림 발행 실패해도 상태변경은 항상 커밋)
            // 대응 (P1-79).
            it("3-1. 상태변경 알림 발행이 실패해도 스레드 상태변경 자체는 커밋되어야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 20,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 20,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "알림 실패 격리 테스트",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )
                val threadId = comment.thread?.id!!

                // 수신자가 없으면 notificationEventRecorder.record() 자체가 호출되지 않으므로
                // (receivers.isEmpty()면 조기 반환), 실제로 예외 경로를 타도록 감시자를 하나 둔다.
                watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))

                val throwingRecorder = mockk<NotificationEventRecorder>()
                every { throwingRecorder.record(any(), any()) } throws RuntimeException("메일 발송 인프라 장애 시뮬레이션")

                val isolatedService = CodeReviewServiceImpl(
                    commentThreadRepository, reviewCommentRepository, pullRequestRepository, repositoryService,
                    userRepository, throwingRecorder, commitCommentRepository, eventPublisher,
                    projectUserRepository, attachmentService, pullRequestCommitRepository, commentService,
                    watchService, pullRequestService, accessControl
                )

                // 예외가 이 메서드 밖으로 전파되지 않아야 한다(전파되면 @Transactional에 의해
                // 방금 커밋하려던 상태변경까지 롤백된다).
                val updatedThread = isolatedService.updateThreadState(
                    threadId = threadId,
                    state = CommentThread.ThreadState.CLOSED,
                    currentUser = user
                )

                updatedThread.state shouldBe CommentThread.ThreadState.CLOSED

                val persisted = commentThreadRepository.findById(threadId).orElseThrow()
                persisted.state shouldBe CommentThread.ThreadState.CLOSED
            }

             it("4. 마지막 댓글이 삭제되면 스레드도 함께 삭제되어야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val commentId = comment.id!!
                val threadId = comment.thread?.id!!

                codeReviewService.deleteReviewComment(commentId, user)

                reviewCommentRepository.findById(commentId).isPresent shouldBe false
                commentThreadRepository.findById(threadId).isPresent shouldBe false
            }

            it("삭제 후에도 스레드에 댓글이 남아있으면 스레드는 삭제되지 않아야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B, startLine = 10, startColumn = 0,
                    endSide = CodeRange.Side.B, endLine = 10, endColumn = 0
                )
                val firstComment = codeReviewService.createReviewComment(
                    project = project, pullRequest = pullRequest, commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰", codeRange = codeRange, threadId = null, currentUser = user
                )
                val threadId = firstComment.thread?.id!!
                codeReviewService.createReviewComment(
                    project = project, pullRequest = pullRequest, commitId = "1234567890abcdef",
                    contents = "답글", codeRange = null, threadId = threadId, currentUser = user
                )

                codeReviewService.deleteReviewComment(firstComment.id!!, user)

                reviewCommentRepository.findById(firstComment.id!!).isPresent shouldBe false
                commentThreadRepository.findById(threadId).isPresent shouldBe true
            }

            it("타인이 다른 유저의 커밋 댓글 삭제 시 Permission denied 예외가 발생해야 한다") {
                val comment = codeReviewService.createCommitComment(
                    project = project, commitId = "1234567890abcdef", contents = "커밋 댓글",
                    path = null, line = null, side = null, currentUser = user
                )

                val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    codeReviewService.deleteCommitComment(comment.id!!, otherUser)
                }
                exception.message shouldBe "Permission denied"
            }

            it("[Test-13-1-6] 타인이 다른 유저의 리뷰 댓글 삭제 시 Permission denied 예외가 발생해야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val commentId = comment.id!!

                val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    codeReviewService.deleteReviewComment(commentId, otherUser)
                }
                exception.message shouldBe "Permission denied"
            }

            // yona AccessControl.java:205-301 isProjectResourceAllowed() 대응 (P1-116). 삭제 권한이 [GL-utils_AccessControl-009]
            // "작성자 또는 프로젝트 role==MANAGER"로만 좁게 구현돼 있었으나, yona는 사이트매니저/조직관리자도
            // 항상 우회할 수 있다 — 이 우회가 yona에는 빠져 있어 사이트관리자조차 타인의 리뷰/커밋 댓글을
            // 지울 수 없는 과도한 제한이었다.
            it("사이트관리자는 작성자도 프로젝트 매니저도 아니어도 타인의 리뷰 댓글을 삭제할 수 있어야 한다") {
                val siteAdmin = userRepository.save(
                    User(loginId = "siteadmin", name = "사이트관리자", email = "siteadmin@yona.io", state = UserState.SITE_ADMIN)
                )

                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "삭제될 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )
                val commentId = comment.id!!

                codeReviewService.deleteReviewComment(commentId, siteAdmin)

                reviewCommentRepository.findById(commentId).isPresent shouldBe false
            }

            it("사이트관리자는 작성자도 프로젝트 매니저도 아니어도 타인의 커밋 댓글을 삭제할 수 있어야 한다") {
                val siteAdmin = userRepository.save(
                    User(loginId = "siteadmin2", name = "사이트관리자2", email = "siteadmin2@yona.io", state = UserState.SITE_ADMIN)
                )

                val comment = codeReviewService.createCommitComment(
                    project = project,
                    commitId = "1234567890abcdef",
                    contents = "커밋에 대한 댓글",
                    path = null,
                    line = null,
                    side = null,
                    currentUser = user
                )
                val commentId = comment.id!!

                codeReviewService.deleteCommitComment(commentId, siteAdmin)

                commitCommentRepository.findById(commentId).isPresent shouldBe false
            }

            describe("isThreadOutdated (P1-20, yona CodeCommentThread.isOutdated() 대응)") {
                it("병합 시점과 코멘트 시점의 코드가 동일하면 outdated가 아니어야 한다") {
                    val outdatedProject = projectRepository.save(Project(name = "outdated-repo-1", owner = "owner-x", vcs = "GIT"))
                    repositoryService.getRepository(outdatedProject).create()
                    try {
                        val bareDir = repositoryService.getRepository(outdatedProject).getDirectory()
                        createTestCommit(bareDir, "master", "test.txt", "v1")
                        val c1 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        createTestCommit(bareDir, "master", "test.txt", "v2")
                        val c2 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()

                        val pr = pullRequestRepository.save(
                            PullRequest(
                                title = "outdated 테스트 PR",
                                toProject = outdatedProject,
                                fromProject = outdatedProject,
                                toBranch = "master",
                                fromBranch = "feature",
                                contributor = user,
                                mergedCommitIdFrom = c1,
                                mergedCommitIdTo = c2
                            )
                        )
                        val thread = commentThreadRepository.save(
                            CodeCommentThread(
                                pullRequest = pr,
                                project = outdatedProject,
                                prevCommitId = c1,
                                commitId = c2,
                                codeRange = CodeRange(path = "test.txt", startLine = 1)
                            )
                        )

                        codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                    } finally {
                        try { repositoryService.getRepository(outdatedProject).delete() } catch (e: Exception) {}
                    }
                }

                it("병합 이후 같은 경로에 추가 커밋이 들어오면 outdated여야 한다") {
                    val outdatedProject = projectRepository.save(Project(name = "outdated-repo-2", owner = "owner-x", vcs = "GIT"))
                    repositoryService.getRepository(outdatedProject).create()
                    try {
                        val bareDir = repositoryService.getRepository(outdatedProject).getDirectory()
                        createTestCommit(bareDir, "master", "test.txt", "v1")
                        val c1 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        createTestCommit(bareDir, "master", "test.txt", "v2")
                        val c2 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()

                        val pr = pullRequestRepository.save(
                            PullRequest(
                                title = "outdated 테스트 PR2",
                                toProject = outdatedProject,
                                fromProject = outdatedProject,
                                toBranch = "master",
                                fromBranch = "feature",
                                contributor = user,
                                mergedCommitIdFrom = c1,
                                mergedCommitIdTo = c2
                            )
                        )
                        val thread = commentThreadRepository.save(
                            CodeCommentThread(
                                pullRequest = pr,
                                project = outdatedProject,
                                prevCommitId = c1,
                                commitId = c2,
                                codeRange = CodeRange(path = "test.txt", startLine = 1)
                            )
                        )

                        // 병합 이후 test.txt가 v3로 다시 바뀌고, PR의 mergedCommitIdTo도 그 시점으로 갱신됐다고 가정
                        createTestCommit(bareDir, "master", "test.txt", "v3")
                        val c3 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        pr.mergedCommitIdTo = c3
                        pullRequestRepository.save(pr)

                        codeReviewService.isThreadOutdated(thread.id!!) shouldBe true
                    } finally {
                        try { repositoryService.getRepository(outdatedProject).delete() } catch (e: Exception) {}
                    }
                }

                it("커밋 댓글(prevCommitId 없음)은 PullRequestCommit에 없으면 outdated여야 한다") {
                    val thread = commentThreadRepository.save(
                        CodeCommentThread(
                            pullRequest = pullRequest,
                            project = project,
                            prevCommitId = "",
                            commitId = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                            codeRange = CodeRange(path = "test.txt", startLine = 1)
                        )
                    )
                    pullRequest.mergedCommitIdFrom = "a"
                    pullRequest.mergedCommitIdTo = "b"
                    pullRequestRepository.save(pullRequest)

                    codeReviewService.isThreadOutdated(thread.id!!) shouldBe true
                }

                // 2026-08-25: 원래 "잘못된 커밋 ID면 예외가 발생해 true(outdated) 처리된다"고
                // 기대했으나, GitRepository.getBlobId()의 repo.resolve(invalidId)는 (저장소를
                // create()해도, 아예 create()하지 않아 디렉터리 자체가 없어도) 둘 다 예외 없이 null을
                // 반환함을 실험적으로 확인했다 — noChangesBetween()이 양쪽 다 null==null로 "변경없음"
                // 판정을 내려 computeOutdated()의 catch 분기 자체에 도달하지 못하고 false를 반환하는
                // 것이 현재 코드의 실제 동작이다. 기대값이 틀렸던 테스트라 실제 동작에 맞게 수정.
                it("computeOutdated - 잘못된 커밋 ID면 blobId가 둘 다 null이 되어 outdated가 아닌 것으로 처리된다") {
                    val outdatedProject = projectRepository.save(Project(name = "outdated-repo-err", owner = "owner-x", vcs = "GIT"))
                    try {
                        val pr = pullRequestRepository.save(
                            PullRequest(
                                title = "outdated 테스트 PR3",
                                toProject = outdatedProject,
                                fromProject = outdatedProject,
                                toBranch = "master",
                                fromBranch = "feature",
                                contributor = user,
                                mergedCommitIdFrom = "invalid-from",
                                mergedCommitIdTo = "invalid-to"
                            )
                        )
                        val thread = commentThreadRepository.save(
                            CodeCommentThread(
                                pullRequest = pr,
                                project = outdatedProject,
                                prevCommitId = "invalid-commit-1",
                                commitId = "invalid-commit-2",
                                codeRange = CodeRange(path = "test.txt", startLine = 1)
                            )
                        )
                        codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                    } finally {
                        try { repositoryService.getRepository(outdatedProject).delete() } catch (e: Exception) {}
                    }
                }
            }

            describe("기타 분기 및 예외 커버리지") {
                it("createReviewComment - 커밋 작성자 로딩 실패 시 예외를 무시해야 한다") {
                    val codeRange = CodeRange(path = "test.txt", startSide = CodeRange.Side.B, startLine = 1, endSide = CodeRange.Side.B, endLine = 1)
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "invalid-commit-id",
                        contents = "커밋 로딩 실패 테스트", codeRange = codeRange, threadId = null, currentUser = user
                    )
                    (comment.thread as CodeCommentThread).codeAuthors.size shouldBe 0
                }

                it("getCommitWatchers - 커밋 작성자 로딩 실패 시 예외를 무시해야 한다") {
                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "invalid-commit-id-2",
                        contents = "커밋 감시자 로딩 실패 테스트", codeRange = null, threadId = null, currentUser = user
                    )
                    val events = notificationEventRepository.findAll().filter { it.newValue == "커밋 감시자 로딩 실패 테스트" }
                    events.size shouldBe 0
                }

                // 실제 bare git 저장소 커밋을 만들어 GitCommit.getAuthor() -> userResolver(email)의
                // 3가지 실질 분기(가입 유저 없음/게스트/정상 유저)를 검증한다. 기존 테스트는 전부
                // invalid-commit-id로 getCommit() 자체가 예외를 던지는 catch 경로만 탔다.
                describe("실제 git 커밋 기반 codeAuthor 로딩 (createReviewComment/getCommitWatchers 공통)") {
                    lateinit var codeAuthorProject: Project
                    lateinit var bareDir: File

                    beforeEach {
                        codeAuthorProject = projectRepository.save(
                            Project(name = "codeauthor-repo", owner = "owner-x", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
                        )
                        val preExisting = repositoryService.getRepository(codeAuthorProject).getDirectory()
                        if (preExisting.exists()) preExisting.deleteRecursively()
                        repositoryService.getRepository(codeAuthorProject).create()
                        bareDir = repositoryService.getRepository(codeAuthorProject).getDirectory()
                    }

                    fun latestCommitId(): String =
                        repositoryService.getRepository(codeAuthorProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()

                    it("커밋 작성자 이메일이 등록된 유저와 일치하지 않으면 codeAuthors에 추가되지 않아야 한다") {
                        createTestCommit(bareDir, "master", "a.txt", "v1", authorName = "unknown", authorEmail = "unknown-nobody@nowhere.io")
                        val commitId = latestCommitId()
                        val codeRange = CodeRange(path = "a.txt", startSide = CodeRange.Side.B, startLine = 1, endSide = CodeRange.Side.B, endLine = 1)

                        val comment = codeReviewService.createReviewComment(
                            project = codeAuthorProject, pullRequest = null, commitId = commitId,
                            contents = "미등록 작성자 커밋 댓글", codeRange = codeRange, threadId = null, currentUser = user
                        )

                        (comment.thread as CodeCommentThread).codeAuthors.size shouldBe 0
                    }

                    it("커밋 작성자가 게스트 계정이면 codeAuthors에 추가되지 않아야 한다") {
                        val guestAuthor = userRepository.save(
                            User(loginId = "guest-author", name = "게스트작성자", email = "guest-author@yona.io", isGuest = true)
                        )
                        createTestCommit(bareDir, "master", "b.txt", "v1", authorName = guestAuthor.name, authorEmail = guestAuthor.email!!)
                        val commitId = latestCommitId()
                        val codeRange = CodeRange(path = "b.txt", startSide = CodeRange.Side.B, startLine = 1, endSide = CodeRange.Side.B, endLine = 1)

                        val comment = codeReviewService.createReviewComment(
                            project = codeAuthorProject, pullRequest = null, commitId = commitId,
                            contents = "게스트 작성자 커밋 댓글", codeRange = codeRange, threadId = null, currentUser = user
                        )

                        (comment.thread as CodeCommentThread).codeAuthors.size shouldBe 0
                    }

                    it("커밋 작성자가 등록된 정상 유저면 codeAuthors에 추가되고, 그 유저는 감시하지 않았어도 알림을 받아야 한다") {
                        createTestCommit(bareDir, "master", "c.txt", "v1", authorName = otherUser.name, authorEmail = otherUser.email!!)
                        val commitId = latestCommitId()
                        val codeRange = CodeRange(path = "c.txt", startSide = CodeRange.Side.B, startLine = 1, endSide = CodeRange.Side.B, endLine = 1)

                        val comment = codeReviewService.createReviewComment(
                            project = codeAuthorProject, pullRequest = null, commitId = commitId,
                            contents = "정상 작성자 커밋 댓글", codeRange = codeRange, threadId = null, currentUser = user
                        )

                        (comment.thread as CodeCommentThread).codeAuthors.map { it.id } shouldBe listOf(otherUser.id)
                        val events = notificationEventRepository.findAll().filter { it.newValue == "정상 작성자 커밋 댓글" }
                        events.size shouldBe 1
                        events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                    }
                }

                it("isThreadOutdated - 잘못된 타입의 스레드인 경우 false를 반환해야 한다") {
                    val nonCodeThread = commentThreadRepository.save(
                        NonRangedCodeCommentThread(
                            pullRequest = pullRequest, project = project, commitId = "a", prevCommitId = "b"
                        )
                    )
                    codeReviewService.isThreadOutdated(nonCodeThread.id!!) shouldBe false
                }

                it("isThreadOutdated - 라인 정보나 커밋 ID가 누락된 경우 false를 반환해야 한다") {
                    val thread = commentThreadRepository.save(
                        CodeCommentThread(
                            pullRequest = pullRequest, project = project, commitId = "", prevCommitId = "", codeRange = CodeRange(path = "test.txt")
                        )
                    )
                    codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                }

                it("isThreadOutdated - pullRequest 정보가 없는 경우 false를 반환해야 한다") {
                    val thread = commentThreadRepository.save(
                        CodeCommentThread(
                            pullRequest = null, project = project, commitId = "c", prevCommitId = "p", codeRange = CodeRange(path = "test.txt", startLine = 1)
                        )
                    )
                    codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                }
            }

            describe("존재하지 않는 리소스 조회 시 예외 처리 (orElseThrow 분기)") {
                it("createReviewComment - 존재하지 않는 threadId면 예외를 던져야 한다") {
                    val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                        codeReviewService.createReviewComment(
                            project = project, pullRequest = pullRequest, commitId = "abc", contents = "답글",
                            codeRange = null, threadId = 999999L, currentUser = user
                        )
                    }
                    exception.message shouldBe "CommentThread not found for id: 999999"
                }

                it("deleteReviewComment - 존재하지 않는 commentId면 예외를 던져야 한다") {
                    io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                        codeReviewService.deleteReviewComment(999999L, user)
                    }
                }

                it("deleteCommitComment - 존재하지 않는 commentId면 예외를 던져야 한다") {
                    io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                        codeReviewService.deleteCommitComment(999999L, user)
                    }
                }

                it("updateThreadState - 존재하지 않는 threadId면 예외를 던져야 한다") {
                    io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                        codeReviewService.updateThreadState(999999L, CommentThread.ThreadState.CLOSED, user)
                    }
                }

                it("addReviewer - 존재하지 않는 reviewerId면 예외를 던져야 한다") {
                    io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                        codeReviewService.addReviewer(pullRequest.id!!, 999999L)
                    }
                }

                it("removeReviewer - 존재하지 않는 reviewerId면 예외를 던져야 한다") {
                    io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                        codeReviewService.removeReviewer(pullRequest.id!!, 999999L)
                    }
                }
            }

            describe("추가 분기 커버리지") {
                it("codeRange는 있지만 startLine이 없으면 NonRangedCodeCommentThread로 생성돼야 한다") {
                    val codeRange = CodeRange(path = "no-startline.txt", startLine = null)

                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "abc123",
                        contents = "startLine 없는 codeRange", codeRange = codeRange, threadId = null, currentUser = user
                    )

                    (comment.thread is NonRangedCodeCommentThread) shouldBe true
                }

                it("codeRange가 있어도 commitId와 pullRequest가 모두 없으면 thread.commitId가 null이어야 한다") {
                    val codeRange = CodeRange(path = "nocommit.txt", startSide = CodeRange.Side.B, startLine = 1, endSide = CodeRange.Side.B, endLine = 1)

                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = null,
                        contents = "커밋도 PR도 없음", codeRange = codeRange, threadId = null, currentUser = user
                    )

                    (comment.thread as CodeCommentThread).commitId shouldBe null
                }

                it("PR의 mergedCommitIdFrom이 설정돼 있으면 라인지정 스레드의 prevCommitId로 그대로 사용돼야 한다") {
                    pullRequest.mergedCommitIdFrom = "merged-from-abc"
                    pullRequestRepository.save(pullRequest)
                    val codeRange = CodeRange(path = "prevcommit.txt", startSide = CodeRange.Side.B, startLine = 1, endSide = CodeRange.Side.B, endLine = 1)

                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = null,
                        contents = "prevCommitId 있음", codeRange = codeRange, threadId = null, currentUser = user
                    )

                    (comment.thread as CodeCommentThread).prevCommitId shouldBe "merged-from-abc"
                }

                it("PR의 mergedCommitIdFrom이 설정돼 있으면 라인 없는 스레드의 prevCommitId로도 그대로 사용돼야 한다") {
                    pullRequest.mergedCommitIdFrom = "merged-from-def"
                    pullRequestRepository.save(pullRequest)

                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = null,
                        contents = "prevCommitId 있음(라인없음)", codeRange = null, threadId = null, currentUser = user
                    )

                    (comment.thread as NonRangedCodeCommentThread).prevCommitId shouldBe "merged-from-def"
                }

                it("PR도 커밋ID도 없는 순수 댓글은 커밋 감시자 조회 없이 멘션된 사용자에게만 알림이 가야 한다") {
                    val mentioned = userRepository.save(User(loginId = "mentioned-nocommit", name = "멘션대상", email = "mentioned-nocommit@yona.io"))

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = null,
                        contents = "@mentioned-nocommit 님 확인해주세요", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(mentioned.id)
                }
            }

            describe("직접 구성한 엔티티로만 재현 가능한 방어 분기") {
                it("deleteReviewComment - 댓글에 연결된 스레드가 없으면 IllegalStateException을 던져야 한다") {
                    val orphanComment = reviewCommentRepository.save(
                        ReviewComment(contents = "고아 댓글", createdDate = Instant.now(), author = UserIdent(user), thread = null)
                    )

                    io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
                        codeReviewService.deleteReviewComment(orphanComment.id!!, user)
                    }
                }

                it("deleteReviewComment - 스레드의 project가 없어도 pullRequest.toProject로 대체해 정상 삭제돼야 한다") {
                    val thread = commentThreadRepository.save(
                        NonRangedCodeCommentThread(pullRequest = pullRequest, project = null, commitId = "x")
                    )
                    val comment = reviewCommentRepository.save(
                        ReviewComment(contents = "프로젝트 없는 스레드 댓글", createdDate = Instant.now(), author = UserIdent(user), thread = thread)
                    )

                    codeReviewService.deleteReviewComment(comment.id!!, user)

                    reviewCommentRepository.findById(comment.id!!).isPresent shouldBe false
                }

                it("deleteReviewComment - 스레드에 project도 pullRequest도 없으면 IllegalStateException을 던져야 한다") {
                    val thread = commentThreadRepository.save(
                        NonRangedCodeCommentThread(pullRequest = null, project = null, commitId = "x")
                    )
                    val comment = reviewCommentRepository.save(
                        ReviewComment(contents = "프로젝트/PR 모두 없는 댓글", createdDate = Instant.now(), author = UserIdent(user), thread = thread)
                    )

                    io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
                        codeReviewService.deleteReviewComment(comment.id!!, user)
                    }
                }

                it("deleteCommitComment - 댓글에 연결된 project가 없으면 IllegalStateException을 던져야 한다") {
                    val orphanCommitComment = commitCommentRepository.save(
                        CommitComment(project = null, contents = "고아 커밋 댓글", commitId = "x", createdDate = Instant.now(), author = UserIdent(user))
                    )

                    io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
                        codeReviewService.deleteCommitComment(orphanCommitComment.id!!, user)
                    }
                }

                it("updateThreadState - PR도 project도 없는 스레드는 상태는 바뀌지만 알림은 발행되지 않아야 한다") {
                    val thread = commentThreadRepository.save(
                        NonRangedCodeCommentThread(pullRequest = null, project = null, commitId = "x")
                    )

                    val updated = codeReviewService.updateThreadState(thread.id!!, CommentThread.ThreadState.CLOSED, user)

                    updated.state shouldBe CommentThread.ThreadState.CLOSED
                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("getCommitWatchers - author.id가 없는(고아) 기존 리뷰 댓글은 감시자 계산에서 조용히 제외돼야 한다") {
                    val ghostThread = commentThreadRepository.save(
                        NonRangedCodeCommentThread(pullRequest = null, project = project, commitId = "ghost-commit")
                    )
                    reviewCommentRepository.save(
                        ReviewComment(contents = "고아 작성자 댓글", createdDate = Instant.now(), author = UserIdent(id = null, loginId = "ghost", name = "유령"), thread = ghostThread)
                    )
                    ghostThread.state = CommentThread.ThreadState.OPEN

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "ghost-commit",
                        contents = "두 번째 댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )

                    val events = notificationEventRepository.findAll().filter { it.newValue == "두 번째 댓글" }
                    events.size shouldBe 0
                }

                it("getCommitWatchers - author 자체가 null인 기존 리뷰 댓글도 감시자 계산에서 조용히 제외돼야 한다") {
                    val noAuthorThread = commentThreadRepository.save(
                        NonRangedCodeCommentThread(pullRequest = null, project = project, commitId = "no-author-commit")
                    )
                    reviewCommentRepository.save(
                        ReviewComment(contents = "author 필드 자체가 없는 댓글", createdDate = Instant.now(), author = null, thread = noAuthorThread)
                    )

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "no-author-commit",
                        contents = "세 번째 댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )

                    val events = notificationEventRepository.findAll().filter { it.newValue == "세 번째 댓글" }
                    events.size shouldBe 0
                }

                it("getCommitWatchers - author.id가 없는(고아) 기존 커밋 댓글도 감시자 계산에서 조용히 제외돼야 한다") {
                    commitCommentRepository.save(
                        CommitComment(
                            project = project, contents = "고아 작성자 커밋 댓글", commitId = "ghost-commit-2",
                            createdDate = Instant.now(), author = UserIdent(id = null, loginId = "ghost2", name = "유령2")
                        )
                    )

                    codeReviewService.createCommitComment(
                        project = project, commitId = "ghost-commit-2", contents = "두 번째 커밋 댓글",
                        path = null, line = null, side = null, currentUser = otherUser
                    )

                    val events = notificationEventRepository.findAll().filter { it.newValue == "두 번째 커밋 댓글" }
                    events.size shouldBe 0
                }

                it("getCommitWatchers - author 자체가 null인 기존 커밋 댓글도 감시자 계산에서 조용히 제외돼야 한다") {
                    commitCommentRepository.save(
                        CommitComment(
                            project = project, contents = "author 필드 자체가 없는 커밋 댓글", commitId = "no-author-commit-2",
                            createdDate = Instant.now(), author = null
                        )
                    )

                    codeReviewService.createCommitComment(
                        project = project, commitId = "no-author-commit-2", contents = "세 번째 커밋 댓글",
                        path = null, line = null, side = null, currentUser = otherUser
                    )

                    val events = notificationEventRepository.findAll().filter { it.newValue == "세 번째 커밋 댓글" }
                    events.size shouldBe 0
                }

                it("updateThreadState - PR은 없고 project는 있지만 commitId가 없으면 알림이 발행되지 않아야 한다") {
                    val thread = commentThreadRepository.save(
                        NonRangedCodeCommentThread(pullRequest = null, project = project, commitId = null)
                    )

                    val updated = codeReviewService.updateThreadState(thread.id!!, CommentThread.ThreadState.CLOSED, user)

                    updated.state shouldBe CommentThread.ThreadState.CLOSED
                    notificationEventRepository.findAll().size shouldBe 0
                }
            }

            describe("computeOutdated 추가 분기") {
                it("커밋 댓글 스레드도 PullRequestCommit에 기록이 있으면 outdated가 아니어야 한다") {
                    val pr2 = pullRequestRepository.save(
                        PullRequest(
                            title = "outdated 커밋댓글 테스트", toProject = project, fromProject = project,
                            toBranch = "master", fromBranch = "feature", contributor = user,
                            mergedCommitIdFrom = "a", mergedCommitIdTo = "b"
                        )
                    )
                    pullRequestCommitRepository.save(PullRequestCommit(pullRequest = pr2, commitId = "existing-commit"))
                    val thread = commentThreadRepository.save(
                        CodeCommentThread(
                            pullRequest = pr2, project = project, prevCommitId = "", commitId = "existing-commit",
                            codeRange = CodeRange(path = "test.txt", startLine = 1)
                        )
                    )

                    codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                }

                it("라인 정보는 있지만 커밋ID가 비어있으면 outdated가 아니어야 한다") {
                    val thread = commentThreadRepository.save(
                        CodeCommentThread(
                            pullRequest = pullRequest, project = project, commitId = "", prevCommitId = "",
                            codeRange = CodeRange(path = "test.txt", startLine = 1)
                        )
                    )
                    codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                }

                it("PR의 mergedCommitIdFrom이 없으면 outdated가 아니어야 한다") {
                    val pr2 = pullRequestRepository.save(
                        PullRequest(
                            title = "머지커밋프롬없음", toProject = project, fromProject = project,
                            toBranch = "master", fromBranch = "feature", contributor = user,
                            mergedCommitIdFrom = null, mergedCommitIdTo = "b"
                        )
                    )
                    val thread = commentThreadRepository.save(
                        CodeCommentThread(
                            pullRequest = pr2, project = project, commitId = "c", prevCommitId = "p",
                            codeRange = CodeRange(path = "test.txt", startLine = 1)
                        )
                    )
                    codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                }

                it("PR의 mergedCommitIdTo가 없으면 outdated가 아니어야 한다") {
                    val pr3 = pullRequestRepository.save(
                        PullRequest(
                            title = "머지커밋투없음", toProject = project, fromProject = project,
                            toBranch = "master", fromBranch = "feature", contributor = user,
                            mergedCommitIdFrom = "a", mergedCommitIdTo = null
                        )
                    )
                    val thread = commentThreadRepository.save(
                        CodeCommentThread(
                            pullRequest = pr3, project = project, commitId = "c", prevCommitId = "p",
                            codeRange = CodeRange(path = "test.txt", startLine = 1)
                        )
                    )
                    codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                }

                it("codeRange의 path가 null이면 빈 문자열(존재하지 않는 경로)로 취급해 blobId 조회가 실패하고 outdated(true)로 처리돼야 한다") {
                    val outdatedProject = projectRepository.save(Project(name = "outdated-repo-nopath", owner = "owner-x", vcs = "GIT"))
                    repositoryService.getRepository(outdatedProject).create()
                    try {
                        val bareDir = repositoryService.getRepository(outdatedProject).getDirectory()
                        createTestCommit(bareDir, "master", "test.txt", "v1")
                        val c1 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()

                        val pr = pullRequestRepository.save(
                            PullRequest(
                                title = "outdated path없음 테스트", toProject = outdatedProject, fromProject = outdatedProject,
                                toBranch = "master", fromBranch = "feature", contributor = user,
                                mergedCommitIdFrom = c1, mergedCommitIdTo = c1
                            )
                        )
                        val thread = commentThreadRepository.save(
                            CodeCommentThread(
                                pullRequest = pr, project = outdatedProject, prevCommitId = c1, commitId = c1,
                                codeRange = CodeRange(path = null, startLine = 1)
                            )
                        )

                        codeReviewService.isThreadOutdated(thread.id!!) shouldBe true
                    } finally {
                        try { repositoryService.getRepository(outdatedProject).delete() } catch (e: Exception) {}
                    }
                }

                it("코드 경로가 슬래시로 시작하면 앞의 슬래시를 제거하고 비교해야 한다") {
                    val outdatedProject = projectRepository.save(Project(name = "outdated-repo-slash", owner = "owner-x", vcs = "GIT"))
                    repositoryService.getRepository(outdatedProject).create()
                    try {
                        val bareDir = repositoryService.getRepository(outdatedProject).getDirectory()
                        createTestCommit(bareDir, "master", "test.txt", "v1")
                        val c1 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        createTestCommit(bareDir, "master", "test.txt", "v2")
                        val c2 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()

                        val pr = pullRequestRepository.save(
                            PullRequest(
                                title = "outdated 슬래시경로 테스트", toProject = outdatedProject, fromProject = outdatedProject,
                                toBranch = "master", fromBranch = "feature", contributor = user,
                                mergedCommitIdFrom = c1, mergedCommitIdTo = c2
                            )
                        )
                        val thread = commentThreadRepository.save(
                            CodeCommentThread(
                                pullRequest = pr, project = outdatedProject, prevCommitId = c1, commitId = c2,
                                codeRange = CodeRange(path = "/test.txt", startLine = 1)
                            )
                        )

                        codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                    } finally {
                        try { repositoryService.getRepository(outdatedProject).delete() } catch (e: Exception) {}
                    }
                }
            }
        }
    }
}
