package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import com.github.yonaprojects.yona.domain.vcs.GitCommit
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.vcs.GitRepository
import com.github.yonaprojects.yona.domain.vcs.HgRepository
import com.github.yonaprojects.yona.domain.vcs.HgCommit
import com.github.yonaprojects.yona.domain.vcs.Commit
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.lib.NodeId
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.event.PullRequestMergeEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueReferenceParser
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import io.micrometer.core.instrument.MeterRegistry
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.ThreeWayMerger
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.Instant

@Service
class PullRequestServiceImpl(
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val repositoryService: RepositoryService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val watchService: WatchService,
    private val issueRepository: IssueRepository,
    private val issueEventRepository: IssueEventRepository,
    private val commentService: CommentService,
    // PR 라벨 추가/제거용(addLabel/removeLabel).
    private val issueLabelRepository: IssueLabelRepository,
    // merge() 시 toBranch에 걸린 ProtectedBranch 규칙 검사용.
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository,
    // submitReview()/getReviews() 및 checkApprovalsForMerge()가 require_approvals를 실제 판정과
    // 연결하는 데 사용한다.
    private val pullRequestReviewRepository: PullRequestReviewRepository,
    // checkSignedCommitsForMerge()가 require_signed_commits를 실제로 검사하는 데 필요.
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    @Value("\${yona.site-name:Yona}")
    private val siteName: String,
    // PullRequestEventRepository.recordWithDraftMerge()에 그대로 전달한다.
    private val meterRegistry: MeterRegistry
) : PullRequestService {

    // PullRequest.fromBranch/toBranch에는 항상 짧은 브랜치 이름이 저장된다. 그런데 JGit의
    // 로컬(파일시스템) fetch 연결(BaseConnection.getRef())은 광고된 ref 맵에서 정확히 일치하는
    // 전체 이름만 찾고 짧은 이름을 "refs/heads/"로 보정해주지 않는다 — RefSpec 소스로 짧은 이름을
    // 그대로 넘기면 "Remote does not have <branch> available for fetch"로 실패한다. resolve()
    // 계열(toBranch 조회 등)은 짧은 이름을 지원해 문제가 없다 — fetch RefSpec 소스에만 이 보정이
    // 필요하다. attemptMerge/previewMerge/merge/updateMerge 네 곳 모두 동일한 fetch 패턴을
    // 반복하고 있어 공용 헬퍼로 뽑았다.
    private fun qualifyBranchRef(branch: String): String =
        if (branch.startsWith("refs/")) branch else "refs/heads/$branch"

    @Transactional
    override fun attemptMerge(pullRequestId: Long): PullRequestMergeResult {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        val playRepo = repositoryService.getRepository(pullRequest.toProject)
        // Mercurial 프로젝트는 JGit이 아니라 hg4j 기반 계산으로 분기한다(클래스 하단 "Mercurial(hg4j)
        // 대응" 섹션 참고).
        if (playRepo is HgRepository) {
            return hgAttemptMerge(pullRequest)
        }
        val gitDir = playRepo.getDirectory()

        return FileRepositoryBuilder().setGitDir(gitDir).build().use { repo ->
            val fromGitDir = repositoryService.getRepository(pullRequest.fromProject).getDirectory().absolutePath
            val tempBranch = "refs/yobi/pull-check/${pullRequest.fromProject.owner}/${pullRequest.fromProject.name}/${pullRequest.fromBranch}"

            // 소스 리포지토리로부터 임시 브랜치로 fetch
            Git(repo).fetch()
                .setRemote(fromGitDir)
                .setRefSpecs(
                    RefSpec()
                        .setSource(qualifyBranchRef(pullRequest.fromBranch))
                        .setDestination(tempBranch)
                        .setForceUpdate(true)
                )
                .call()

            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true) as ThreeWayMerger
            val leftParent = repo.resolve(pullRequest.toBranch)
                ?: throw IllegalArgumentException("Target branch '${pullRequest.toBranch}' not found")
            val rightParent = repo.resolve(tempBranch)
                ?: throw IllegalArgumentException("Fetched source branch not found")

            val success = merger.merge(leftParent, rightParent)
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            if (success) {
                result.setResolvedStateOfPullRequest()
            } else {
                result.setConflictStateOfPullRequest()
            }

            result.gitCommits = diffCommits(repo, leftParent, rightParent)
            pullRequest.lastCommitId = rightParent.name
            pullRequestRepository.save(pullRequest)

            // 임시 브랜치 삭제
            val refUpdate = repo.updateRef(tempBranch)
            refUpdate.isForceUpdate = true
            refUpdate.delete()

            result
        }
    }

    // attemptMerge(pullRequestId)와 동일한 JGit 흐름(임시 ref로 fetch → 3-way merge 시도 → 커밋
    // diff 계산 → 임시 ref 삭제)이지만, 저장된 PullRequest 엔티티를 조회/저장하지 않고 임의의
    // fromProject/toProject/fromBranch/toBranch만으로 동작한다.
    @Transactional(readOnly = true)
    override fun previewMerge(fromProject: Project, toProject: Project, fromBranch: String, toBranch: String): MergePreviewResult {
        val playRepo = repositoryService.getRepository(toProject)
        if (playRepo is HgRepository) {
            return hgPreviewMerge(fromProject, toProject, fromBranch, toBranch)
        }
        val gitDir = playRepo.getDirectory()

        return FileRepositoryBuilder().setGitDir(gitDir).build().use { repo ->
            val fromGitDir = repositoryService.getRepository(fromProject).getDirectory().absolutePath
            val tempBranch = "refs/yobi/pull-check/${fromProject.owner}/${fromProject.name}/$fromBranch"

            // 소스 리포지토리로부터 임시 브랜치로 fetch (legacy fetchSourceTemporarilly() 대응)
            Git(repo).fetch()
                .setRemote(fromGitDir)
                .setRefSpecs(
                    RefSpec()
                        .setSource(qualifyBranchRef(fromBranch))
                        .setDestination(tempBranch)
                        .setForceUpdate(true)
                )
                .call()

            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true) as ThreeWayMerger
            val leftParent = repo.resolve(toBranch)
                ?: throw IllegalArgumentException("Target branch '$toBranch' not found")
            val rightParent = repo.resolve(tempBranch)
                ?: throw IllegalArgumentException("Source branch '$fromBranch' not found")

            val success = merger.merge(leftParent, rightParent)
            val commits = diffCommits(repo, leftParent, rightParent)
            val (suggestedTitle, suggestedBody) = suggestTitleAndBody(commits)

            // 임시 브랜치 삭제 (legacy attemptMerge()가 fetchSourceTemporarilly()로 만든 임시 ref를
            // 병합 성공/실패와 무관하게 매번 정리하는 것과 동일)
            val refUpdate = repo.updateRef(tempBranch)
            refUpdate.isForceUpdate = true
            refUpdate.delete()

            MergePreviewResult(
                commits = commits,
                conflict = !success,
                suggestedTitle = suggestedTitle,
                suggestedBody = suggestedBody
            )
        }
    }

    // 커밋이 1개면 첫 줄을 title로, 나머지 줄들을 body로 쓰고, 2개 이상이면 title 없이 각 커밋의
    // 첫 줄만 모아 body로 쓴다.
    private fun suggestTitleAndBody(commits: List<Commit>): Pair<String?, String?> {
        if (commits.isEmpty()) {
            return null to null
        }
        if (commits.size == 1) {
            val messages = (commits[0].getMessage() ?: "").split("\n")
            return if (messages.size > 1) {
                messages[0] to messages.drop(1).joinToString("\n").trim()
            } else {
                messages[0] to ""
            }
        }
        val firstMessages = commits.map { (it.getMessage() ?: "").split("\n").firstOrNull() ?: "" }
        return null to firstMessages.joinToString("\n")
    }

    // attemptMerge()는 PullRequestViewController가 페이지 렌더링마다 호출하는 부수효과 없는
    // 미리보기라 여기서 부수효과를 추가하면 조회할 때마다 알림/이벤트가 잘못 발생한다 —
    // updateMerge()(부수효과 있음)와 attemptMerge()(부수효과 없음)의 경계를 별도 메서드로 유지한다.
    @Transactional
    override fun processMergeCheck(pullRequestId: Long, sender: User, isNewPullRequest: Boolean): PullRequestMergeResult {
        val before = pullRequestRepository.findById(pullRequestId).orElse(null)
        val beforeMergedCommitIdTo = before?.mergedCommitIdTo
        // updateMerge() 호출 전(재검사 이전) 상태를 미리 캡처해둔다.
        val wasConflict = before?.isConflict ?: false

        val result = updateMerge(pullRequestId)
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        if (result.hasDiffCommits()) {
            val newCommits = updatePullRequestCommits(pullRequest, result.gitCommits)
            result.newCommits = newCommits

            if (newCommits.isNotEmpty()) {
                if (!isNewPullRequest) {
                    notifyCommitChanged(pullRequest, sender)
                }
                recordCommitChangedEvent(pullRequest, sender, newCommits, beforeMergedCommitIdTo)

                // yona PullRequest.clearReviewers() 대응 — 새 커밋이 들어왔으니 기존 리뷰를 무효화하고
                // 재검토를 강제한다.
                pullRequest.reviewers.clear()
                pullRequestRepository.save(pullRequest)
            }
        } else if (pullRequest.state != State.MERGED) {
            // yona의 hasDiffCommits()==false 분기(diff가 사라짐 = 이미 다른 경로로 모든 변경이 대상
            // 브랜치에 반영됨) 대응 — 실제 병합 동작 없이 상태만 MERGED로 자동 전환한다.
            pullRequest.isConflict = false
            pullRequest.receiver = sender
            pullRequestRepository.save(pullRequest)
            changeState(pullRequestId, State.MERGED, sender.loginId)
        }

        // diff/커밋 처리와 완전히 별개로, 재검사 결과 conflict 여부 자체가 바뀌면(충돌 없다가 발생/
        // 충돌이 해소됨) 알림+타임라인을 남긴다. eventType은 이름과 달리 "머지 완료"가 아니라 이
        // conflict 전환 전용이다(실제 머지 완료는 위 changeState(..., MERGED, ...)의
        // PULL_REQUEST_STATE_CHANGED가 담당).
        if (!wasConflict && result.conflicts()) {
            notifyMergeConflictChanged(pullRequest, sender, State.CONFLICT)
        }
        if (wasConflict && !result.conflicts()) {
            notifyMergeConflictChanged(pullRequest, sender, State.RESOLVED)
        }

        return result
    }

    private fun notifyMergeConflictChanged(pullRequest: PullRequest, sender: User, state: State) {
        val notificationEvent = NotificationEvent(
            title = formatReplyTitle(pullRequest),
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            eventType = EventType.PULL_REQUEST_MERGED,
            newValue = state.state()
        )
        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(pullRequest.contributor),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            projectId = pullRequest.toProject.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == sender.id }
        notificationEvent.receivers = receivers
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(pullRequest, EventType.PULL_REQUEST_MERGED, sender.loginId, null, state.state())
    }

    // yona NotificationEvent.afterPullRequestCommitChanged() 대응.
    private fun notifyCommitChanged(pullRequest: PullRequest, sender: User) {
        val title = "[${pullRequest.toProject.name}] PR #${pullRequest.number} 새 커밋이 추가되었습니다"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            eventType = EventType.PULL_REQUEST_COMMIT_CHANGED,
            newValue = buildCommitChangedMessage(pullRequest)
        )
        val receivers = watchService.findActualWatchers(
            baseWatchers = emptySet(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            projectId = pullRequest.toProject.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == sender.id }
        notificationEvent.receivers = receivers
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    // yona NotificationEvent.newPullRequestCommitChangedMessage() 대응 — 현재 CURRENT 상태인 커밋을
    // 최신순으로 나열한다.
    private fun buildCommitChangedMessage(pullRequest: PullRequest): String {
        val commits = pullRequestCommitRepository.findByPullRequestAndState(pullRequest, PullRequestCommit.State.CURRENT)
            .sortedByDescending { it.authorDate }
        val builder = StringBuilder("### 현재 커밋\n")
        for (commit in commits) {
            builder.append(commit.commitShortId).append(" ").append(commit.getCommitShortMessage()).append("\n")
        }
        return builder.toString()
    }

    // recordPullRequestEvent()(recordWithDraftMerge 경유)를 거치지 않고 항상 그대로 저장하는
    // 유일한 PullRequestEvent 생성 지점이라 직접 저장한다.
    private fun recordCommitChangedEvent(
        pullRequest: PullRequest,
        sender: User,
        newCommits: List<PullRequestCommit>,
        beforeMergedCommitIdTo: String?
    ) {
        // 이전 mergedCommitIdTo가 없으면(최초 재검사) oldValue도 null, 있으면 "이전,새" 쌍.
        val oldValue = beforeMergedCommitIdTo?.let { "$it,${pullRequest.mergedCommitIdTo}" }
        val newValue = newCommits.joinToString(",") { it.id.toString() }
        pullRequestEventRepository.save(
            PullRequestEvent(
                pullRequest = pullRequest,
                senderLoginId = sender.loginId,
                eventType = EventType.PULL_REQUEST_COMMIT_CHANGED,
                oldValue = oldValue,
                newValue = newValue,
                created = Instant.now()
            )
        )
    }

    @Transactional
    override fun merge(pullRequestId: Long, updater: User): PullRequestMergeResult {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        // 이 가드가 없으면 이미 MERGED된 PR을 다시 merge할 때마다 새 머지 커밋을 만들어
        // refs/heads/{toBranch}에 중복으로 이어붙인다. 머지는 OPEN 상태에서만 의미가 있으므로 그 외
        // 상태(MERGED/CLOSED)면 여기서 즉시 거절한다.
        if (pullRequest.state != State.OPEN) {
            throw IllegalArgumentException(
                "이미 ${pullRequest.state} 상태인 풀 리퀘스트는 머지할 수 없습니다."
            )
        }

        // toBranch에 걸린 ProtectedBranch 규칙 검사. LackingReviewerException(아래) 검사보다 먼저
        // 두는 특별한 이유는 없다 — 둘 다 merge()를 조기에 거부하는 독립적인 가드일 뿐이라 순서는 임의다.
        checkBranchProtectionForMerge(pullRequest, updater)

        // require_approvals를 실제 PullRequestReview 판정과 연결한다. fetch 이전(leftParent/
        // rightParent가 필요 없는 검사)이라 checkBranchProtectionForMerge()와 같은 지점에서 호출한다.
        checkApprovalsForMerge(pullRequest, updater)

        // 최소 리뷰어 검증 조건 체크
        val project = pullRequest.toProject
        if (project.isUsingReviewerCount) {
            val currentReviewersCount = pullRequest.reviewers.size
            if (currentReviewersCount < project.defaultReviewerCount) {
                throw LackingReviewerException(
                    "리뷰어 수가 부족하여 머지할 수 없습니다. " +
                    "(필요: ${project.defaultReviewerCount}명, 현재: ${currentReviewersCount}명)"
                )
            }
        }

        val playRepo = repositoryService.getRepository(pullRequest.toProject)
        if (playRepo is HgRepository) {
            return hgMerge(pullRequest, updater)
        }
        val gitDir = playRepo.getDirectory()

        return FileRepositoryBuilder().setGitDir(gitDir).build().use { repo ->
            val fromGitDir = repositoryService.getRepository(pullRequest.fromProject).getDirectory().absolutePath
            val fetchSourceRef = "refs/yobi/pull/${pullRequest.id}/head"

            // 공식 fetch source branch로 fetch (qualifyBranchRef 관련 근본원인은 클래스 상단 주석 참고)
            Git(repo).fetch()
                .setRemote(fromGitDir)
                .setRefSpecs(
                    RefSpec()
                        .setSource(qualifyBranchRef(pullRequest.fromBranch))
                        .setDestination(fetchSourceRef)
                        .setForceUpdate(true)
                )
                .call()

            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true) as ThreeWayMerger
            val leftParent = repo.resolve(pullRequest.toBranch)
                ?: throw IllegalArgumentException("Target branch '${pullRequest.toBranch}' not found")
            val rightParent = repo.resolve(fetchSourceRef)
                ?: throw IllegalArgumentException("Source head ref not found")

            // toBranch에 걸린 규칙이 require_signed_commits라면, 실제로 병합될 커밋(leftParent..rightParent
            // 범위)을 여기서 검사한다. leftParent/rightParent가 확정된 시점에서만 그 범위를 계산할 수
            // 있으므로 checkBranchProtectionForMerge()(fetch 전, restrict_push_to만 검사)보다
            // 늦게 실행된다.
            checkSignedCommitsForMerge(pullRequest, updater, repo, leftParent, rightParent)

            val success = merger.merge(leftParent, rightParent)
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            if (!success) {
                result.setConflictStateOfPullRequest()
                result.gitCommits = diffCommits(repo, leftParent, rightParent)
                pullRequestRepository.save(pullRequest)
                return@use result
            }

            // 머지 성공: 머지 커밋 생성
            val whoMerges = PersonIdent(updater.name, updater.email ?: "yona@yona.io")
            val diff = diffCommits(repo, leftParent, rightParent)
            // additionalTargetRef로 실제 대상 브랜치도 함께 갱신한다(아래 createMergeCommitAndUpdateRef() 참고).
            val mergeCommitId = createMergeCommitAndUpdateRef(
                repo, pullRequest, leftParent, rightParent, merger, whoMerges, diff,
                additionalTargetRef = qualifyBranchRef(pullRequest.toBranch)
            )

            // DB 업데이트
            result.gitCommits = diff
            result.setMergedStateOfPullRequest(updater)
            pullRequest.mergedCommitIdFrom = leftParent.name
            pullRequest.mergedCommitIdTo = mergeCommitId.name
            pullRequest.lastCommitId = rightParent.name
            pullRequest.received = Instant.now()
            pullRequest.isMerging = false

            pullRequestRepository.save(pullRequest)
            updatePullRequestCommits(pullRequest, diff)

            // 비동기 이벤트 발행
            eventPublisher.publishEvent(
                PullRequestMergeEvent(
                    pullRequestId = pullRequest.id!!,
                    sender = updater,
                    isNewPullRequest = false
                )
            )

            result
        }
    }

    // toBranch에 매칭되는 ProtectedBranch 규칙이 있으면 merge()를 거부할지 판정한다.
    //
    // requirePullRequest 필드는 이 메서드에서 어떤 검사도 하지 않는다 — merge()는 정의상
    // PullRequestId를 통해서만 호출되는 PR 병합 경로이므로(직접 push로 브랜치를 갱신하는 경로가
    // 아님) 항상 이 조건을 만족한다. 직접 push 차단은 BranchProtectionPreReceiveHook(GitPushHooks.kt)의 몫이다.
    //
    // requireApprovals는 checkApprovalsForMerge()가 별도로 실제 검사를 수행한다(merge()에서 이
    // 메서드 바로 다음에 호출).
    //
    // requireSignedCommits는 checkSignedCommitsForMerge()가 실제 검사를 수행한다. 이 메서드는
    // fetch 이전(leftParent/rightParent를 아직 모르는 시점)에 호출되므로 병합 대상 커밋 범위가
    // 필요한 requireSignedCommits는 여기서 검사할 수 없다 — merge()가 leftParent/rightParent를
    // 확정한 직후 별도로 호출한다.
    //
    // restrictPushTo만 실질적으로 검사한다 — merge()가 toBranch에 병합 커밋을 직접 기록하는
    // ref 갱신이라는 점에서 git push와 동등하게 취급한다(admins_can_bypass=true인 프로젝트
    // 매니저는 우회 가능).
    private fun checkBranchProtectionForMerge(pullRequest: PullRequest, updater: User) {
        val toProjectId = pullRequest.toProject.id ?: return
        val branch = pullRequest.toBranch.removePrefix("refs/heads/")
        val rule = findMatchingProtectedBranchRule(toProjectId, branch) ?: return

        if (rule.adminsCanBypass && isProjectManager(toProjectId, updater)) return

        val allowedPushers = rule.restrictedLoginIds()
        if (allowedPushers.isNotEmpty() && updater.loginId !in allowedPushers) {
            throw BranchProtectionException(
                "브랜치 '$branch'는 지정된 사용자만 병합할 수 있습니다(restrict_push_to)."
            )
        }
    }

    // require_approvals를 실제 판정(PullRequestReview)과 연결한다. GitHub 방식 기본값에 따라 두
    // 조건을 검사한다:
    //   1. 최신 판정이 REQUEST_CHANGES인 리뷰어가 하나라도 있으면, 승인 개수와 무관하게 무조건
    //      병합을 거부한다("변경 요청이 하나라도 살아있으면 차단").
    //   2. 그렇지 않으면 최신 판정이 APPROVE인 리뷰어 수가 requireApprovals 이상이어야 한다.
    // admins_can_bypass 처리는 checkBranchProtectionForMerge()/checkSignedCommitsForMerge()와
    // 동일하게 이 규칙 전체에 적용한다.
    private fun checkApprovalsForMerge(pullRequest: PullRequest, updater: User) {
        val toProjectId = pullRequest.toProject.id ?: return
        val branch = pullRequest.toBranch.removePrefix("refs/heads/")
        val rule = findMatchingProtectedBranchRule(toProjectId, branch) ?: return
        if (rule.requireApprovals <= 0) return
        if (rule.adminsCanBypass && isProjectManager(toProjectId, updater)) return

        val latestDecisions = latestDecisiveReviewByReviewer(pullRequest)

        if (latestDecisions.containsValue(PullRequestReview.ReviewState.REQUEST_CHANGES)) {
            throw BranchProtectionException(
                "브랜치 '$branch'는 변경 요청(request changes)이 해소되기 전에는 병합할 수 없습니다" +
                    "(require_approvals)."
            )
        }

        val approvalCount = latestDecisions.values.count { it == PullRequestReview.ReviewState.APPROVE }
        if (approvalCount < rule.requireApprovals) {
            throw BranchProtectionException(
                "브랜치 '$branch'는 승인이 ${rule.requireApprovals}건 이상 필요합니다(require_approvals) — " +
                    "현재 $approvalCount 건."
            )
        }
    }

    // 리뷰어별로 가장 최근에 남긴 APPROVE/REQUEST_CHANGES 판정만 남긴다(설계 결정 2번 — 재판정 시
    // 이전 판정은 정책 판단에서 더 이상 유효하지 않다). COMMENT는 정책 판단에서 완전히 제외한다 —
    // GitHub도 Comment 전용 리뷰는 그 리뷰어의 기존 Approve/Request changes 상태를 바꾸지 않는다
    // (판정 자체가 없는 리뷰이므로 "가장 최근 판정"의 후보가 될 수 없다). createdDate 오름차순으로
    // 순회하며 덮어쓰므로 맵에 마지막까지 남는 값이 항상 그 리뷰어의 최신 판정이다.
    private fun latestDecisiveReviewByReviewer(pullRequest: PullRequest): Map<Long, PullRequestReview.ReviewState> {
        val reviews = pullRequestReviewRepository.findByPullRequestOrderByCreatedDateAsc(pullRequest)
        val latest = linkedMapOf<Long, PullRequestReview.ReviewState>()
        for (review in reviews) {
            if (review.state == PullRequestReview.ReviewState.COMMENT) continue
            val reviewerId = review.reviewer.id ?: continue
            latest[reviewerId] = review.state
        }
        return latest
    }

    // toBranch에 매칭되는 규칙의 require_signed_commits가 켜져 있으면, 실제로 병합될 커밋
    // (leftParent에는 없고 rightParent에는 있는 커밋, 즉 diffCommits()와 동일한 범위)을
    // GpgSignatureVerifier로 검사해 하나라도
    // VERIFIED가 아니면 병합을 거부한다. admins_can_bypass 처리는 checkBranchProtectionForMerge()와
    // 동일하게 이 규칙 전체에 대해 적용한다(BranchProtectionPreReceiveHook의 admins_can_bypass가
    // 규칙 전체를 우회하는 것과 동일한 의미).
    private fun checkSignedCommitsForMerge(
        pullRequest: PullRequest,
        updater: User,
        repo: Repository,
        leftParent: ObjectId,
        rightParent: ObjectId
    ) {
        val toProjectId = pullRequest.toProject.id ?: return
        val branch = pullRequest.toBranch.removePrefix("refs/heads/")
        val rule = findMatchingProtectedBranchRule(toProjectId, branch) ?: return
        if (!rule.requireSignedCommits) return
        if (rule.adminsCanBypass && isProjectManager(toProjectId, updater)) return

        val commitsBeingMerged = Git(repo).log().addRange(leftParent, rightParent).call()
        for (commit in commitsBeingMerged) {
            if (gpgSignatureVerifier.verify(commit) != GpgVerificationStatus.VERIFIED) {
                throw BranchProtectionException(
                    "브랜치 '$branch'는 서명된 커밋만 병합할 수 있습니다(require_signed_commits) — " +
                        "커밋 ${commit.name.take(8)}가 서명되지 않았거나 서명 검증에 실패했습니다."
                )
            }
        }
    }

    private fun findMatchingProtectedBranchRule(projectId: Long, branch: String) =
        protectedBranchRepository.findByProjectId(projectId).firstOrNull { it.matches(branch) }

    private fun isProjectManager(projectId: Long, user: User): Boolean {
        val userId = user.id ?: return false
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    // merge()(실제 병합)와 updateMerge()(재검사 미리보기) 둘 다 "머지 커밋을 만들어
    // refs/yobi/pull/{id}/merged를 갱신"하는 동일한 절차를 쓰므로 공용 헬퍼로 추출했다.
    private fun createMergeCommitAndUpdateRef(
        repo: Repository,
        pullRequest: PullRequest,
        leftParent: ObjectId,
        rightParent: ObjectId,
        merger: ThreeWayMerger,
        whoMerges: PersonIdent,
        diff: List<GitCommit>,
        // 이 헬퍼는 merge()(실제 병합)/updateMerge()(재검사 미리보기) 둘 다 refs/yobi/pull/{id}/merged를
        // 갱신하지만, 실제 병합 시에는 대상 브랜치(toBranch)도 함께 갱신해야 `git pull`로 반영된다.
        // merge()만 이 파라미터로 qualifyBranchRef(toBranch)를 넘겨 대상 브랜치도 함께
        // fast-forward한다 — updateMerge()는 그대로 null을 넘겨 mergedRef만 갱신하는 기존
        // 부수효과 경계를 유지한다.
        additionalTargetRef: String? = null
    ): ObjectId {
        val reusableTreeId = getMergedTreeIfReusable(repo, leftParent, rightParent, pullRequest)
        val mergeCommit = CommitBuilder().apply {
            setTreeId(reusableTreeId ?: merger.resultTreeId)
            setParentIds(leftParent, rightParent)
            setAuthor(whoMerges)
            setCommitter(whoMerges)
            setMessage(makeMergeCommitMessage(pullRequest, diff))
        }

        val inserter = repo.newObjectInserter()
        val mergeCommitId = inserter.insert(mergeCommit)
        inserter.flush()
        inserter.close()

        val mergedRef = "refs/yobi/pull/${pullRequest.id}/merged"
        val refUpdate = repo.updateRef(mergedRef)
        refUpdate.setNewObjectId(mergeCommitId)
        refUpdate.isForceUpdate = true
        refUpdate.refLogIdent = whoMerges
        refUpdate.setRefLogMessage("merged", true)
        val rc = refUpdate.update()
        if (rc != RefUpdate.Result.NEW && rc != RefUpdate.Result.FAST_FORWARD && rc != RefUpdate.Result.FORCED) {
            throw PullRequestException("Ref update failed for $mergedRef: $rc")
        }

        if (additionalTargetRef != null) {
            // mergeCommit의 첫 부모가 정확히 leftParent(toBranch의 현재 tip)이므로 이 갱신은
            // 항상 fast-forward다 — setExpectedOldObjectId로 그 사이 다른 push가 toBranch를
            // 움직이지 않았음을 한 번 더 보장한다(동시성 안전장치, RefUpdate.Result.
            // LOCK_FAILURE로 감지 가능).
            val branchRefUpdate = repo.updateRef(additionalTargetRef)
            branchRefUpdate.setExpectedOldObjectId(leftParent)
            branchRefUpdate.setNewObjectId(mergeCommitId)
            branchRefUpdate.refLogIdent = whoMerges
            branchRefUpdate.setRefLogMessage("merge pull request #${pullRequest.number}", false)
            val branchRc = branchRefUpdate.update()
            if (branchRc != RefUpdate.Result.NEW &&
                branchRc != RefUpdate.Result.FAST_FORWARD &&
                branchRc != RefUpdate.Result.FORCED
            ) {
                throw PullRequestException("Ref update failed for $additionalTargetRef: $branchRc")
            }
        }

        return mergeCommitId
    }

    // attemptMerge()(뷰 전용, 임시 브랜치로 fetch 후 삭제, 부수효과 없음)와 달리 소스를 영구
    // ref(refs/yobi/pull/{id}/head)로 fetch하고, 충돌이 없으면 실제 "미리보기 병합 커밋"을 만들어
    // refs/yobi/pull/{id}/merged를 갱신한 뒤 그 커밋의 부모/자신 해시를
    // mergedCommitIdFrom/mergedCommitIdTo에 기록한다. processMergeCheck() 전용이며,
    // PullRequestViewController가 페이지 렌더링마다 호출하는 attemptMerge()는 이 메서드를 거치지
    // 않는다. 실제 push한 sender가 아니라 사이트 시스템 계정(siteName)으로 커밋한다.
    private fun updateMerge(pullRequestId: Long): PullRequestMergeResult {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        val playRepo = repositoryService.getRepository(pullRequest.toProject)
        // Mercurial은 진짜 changelog가 append-only라 Git처럼 "재검사 때마다
        // refs/yobi/pull/{id}/merged에 버려질 수 있는 미리보기 커밋"을 만들 방법이 없다(만들면
        // 재검사할 때마다 실제 프로젝트 히스토리에 영구 쓰레기 changeset이 쌓인다). 그래서
        // hgAttemptMerge()와 동일한 순수 계산(TreeMergeCommand)만 수행하고 mergedCommitIdFrom/
        // mergedCommitIdTo는 항상 null로 남긴다 — Git의 attemptMerge()도 이 두 필드는 건드리지
        // 않으므로 "미리보기 전용 부수효과가 없다"는 계약은 동일하게 지켜진다(다만 outdated 리뷰
        // 코멘트 감지처럼 이 필드에 기대는 부가 기능은 Hg에서 동작하지 않는다).
        if (playRepo is HgRepository) {
            return hgAttemptMerge(pullRequest)
        }
        val gitDir = playRepo.getDirectory()

        return FileRepositoryBuilder().setGitDir(gitDir).build().use { repo ->
            val fromGitDir = repositoryService.getRepository(pullRequest.fromProject).getDirectory().absolutePath
            val fetchedSourceRef = "refs/yobi/pull/${pullRequest.id}/head"

            Git(repo).fetch()
                .setRemote(fromGitDir)
                .setRefSpecs(
                    RefSpec()
                        .setSource(qualifyBranchRef(pullRequest.fromBranch))
                        .setDestination(fetchedSourceRef)
                        .setForceUpdate(true)
                )
                .call()

            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true) as ThreeWayMerger
            val leftParent = repo.resolve(pullRequest.toBranch)
                ?: throw IllegalArgumentException("Target branch '${pullRequest.toBranch}' not found")
            val rightParent = repo.resolve(fetchedSourceRef)
                ?: throw IllegalArgumentException("Fetched source branch not found")

            val success = merger.merge(leftParent, rightParent)
            val result = PullRequestMergeResult(pullRequest = pullRequest)
            val diff = diffCommits(repo, leftParent, rightParent)

            if (success) {
                val whoMerges = PersonIdent(siteName, "yona@yona.io")
                val mergeCommitId = createMergeCommitAndUpdateRef(repo, pullRequest, leftParent, rightParent, merger, whoMerges, diff)
                result.setResolvedStateOfPullRequest()
                pullRequest.mergedCommitIdFrom = leftParent.name
                pullRequest.mergedCommitIdTo = mergeCommitId.name
            } else {
                result.setConflictStateOfPullRequest()
            }

            result.gitCommits = diff
            pullRequest.lastCommitId = rightParent.name
            pullRequestRepository.save(pullRequest)

            result
        }
    }

    private fun diffCommits(repo: Repository, from: ObjectId, to: ObjectId): List<GitCommit> {
        val git = Git(repo)
        val commits = git.log().addRange(from, to).call().toList()
        val userResolver: (String?, String?) -> User? = { _, email ->
            if (email != null) userRepository.findByEmail(email).orElse(null) else null
        }
        return commits.map { GitCommit(it, userResolver) }
    }

    private fun getMergedTreeIfReusable(
        repo: Repository,
        leftParent: ObjectId,
        rightParent: ObjectId,
        pullRequest: PullRequest
    ): ObjectId? {
        val refName = "refs/yobi/pull/${pullRequest.id}/merged"
        var commit: RevCommit? = null
        try {
            val ref = repo.findRef(refName)
            if (ref != null && ref.objectId != null) {
                commit = RevWalk(repo).parseCommit(ref.objectId)
            }
        } catch (e: Exception) {
            // Ignore log
        }
        if (commit != null && commit.parentCount == 2 &&
            commit.getParent(0) == leftParent &&
            commit.getParent(1) == rightParent
        ) {
            return commit.tree.toObjectId()
        }
        return null
    }

    // yona PullRequestMergeResult.saveCommits()/findNewCommits()/updatePriorCommits() 대응.
    // 반환값(새로 저장된 커밋)은 processMergeCheck()가 PullRequestEvent/알림 생성에 사용한다.
    private fun updatePullRequestCommits(pullRequest: PullRequest, gitCommits: List<Commit>): List<PullRequestCommit> {
        val priorCommits = pullRequestCommitRepository.findByPullRequestAndState(
            pullRequest, PullRequestCommit.State.CURRENT
        )
        val priorCommitIds = priorCommits.map { it.commitId }.toSet()

        val newCommits = mutableListOf<PullRequestCommit>()
        for (commit in gitCommits) {
            val commitId = commit.getId()
            if (!priorCommitIds.contains(commitId)) {
                newCommits.add(PullRequestCommit.bindPullRequestCommit(commit, pullRequest))
            }
        }
        val savedNewCommits = pullRequestCommitRepository.saveAll(newCommits)

        val gitCommitIds = gitCommits.map { it.getId() }.toSet()
        val updatedCommits = mutableListOf<PullRequestCommit>()
        for (priorCommit in priorCommits) {
            if (!gitCommitIds.contains(priorCommit.commitId)) {
                priorCommit.state = PullRequestCommit.State.PRIOR
                updatedCommits.add(priorCommit)
            }
        }
        pullRequestCommitRepository.saveAll(updatedCommits)

        return savedNewCommits
    }

    private fun makeMergeCommitMessage(pullRequest: PullRequest, commits: List<Commit>): String {
        val builder = StringBuilder()
        val shortenedFrom = Repository.shortenRefName(pullRequest.fromBranch)
        builder.append("Merge branch '$shortenedFrom'")

        if (pullRequest.fromProject != pullRequest.toProject) {
            builder.append(" of ${pullRequest.fromProject.owner}/${pullRequest.fromProject.name}")
        }

        val shortenedTo = Repository.shortenRefName(pullRequest.toBranch)
        if (shortenedTo == "master" || shortenedTo == "heads/master" || pullRequest.toBranch == "refs/heads/master") {
            builder.append("\n\n")
        } else {
            builder.append(" into '$shortenedTo'\n\n")
        }
        builder.append("from pull-request ${pullRequest.number}\n\n")

        builder.append("* $shortenedFrom:\n")
        for (gitCommit in commits) {
            builder.append("  ${gitCommit.getShortMessage()}\n")
        }
        builder.append("\n")

        for (user in pullRequest.reviewers) {
            builder.append("Reviewed-by: ${user.name} <${user.email ?: ""}>\n")
        }

        return builder.toString()
    }

    @Transactional(readOnly = true)
    override fun getPullRequests(toProjectId: Long, state: State?): List<PullRequest> {
        val project = projectRepository.findById(toProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $toProjectId") }
        return if (state != null) {
            pullRequestRepository.findByToProjectAndState(project, state)
        } else {
            pullRequestRepository.findByToProject(project)
        }
    }

    @Transactional(readOnly = true)
    override fun getPullRequest(toProjectId: Long, number: Long): PullRequest? {
        val project = projectRepository.findById(toProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $toProjectId") }
        return pullRequestRepository.findByToProjectAndNumber(project, number)
    }

    @Transactional
    override fun createPullRequest(
        title: String,
        body: String?,
        fromProjectId: Long,
        toProjectId: Long,
        fromBranch: String,
        toBranch: String,
        contributor: User
    ): PullRequest {
        val fromProject = projectRepository.findById(fromProjectId)
            .orElseThrow { IllegalArgumentException("Source project not found: $fromProjectId") }
        val toProject = projectRepository.findById(toProjectId)
            .orElseThrow { IllegalArgumentException("Target project not found: $toProjectId") }

        // PullRequest는 전용 카운터 컬럼 없이 매번 findFirstByToProjectOrderByNumberDesc()로
        // 최댓값을 조회해 +1한다. pull_request 테이블의 (to_project_id, number) UNIQUE 제약이
        // "조용한 손상"을 "명확한 제약 위반 실패"로 바꿔주고, PullRequestController.createPullRequest()가
        // 이 실패를 잡아 전체 재시도한다(이 메서드 전체가 @Transactional이라 실패 시 부수효과가
        // 전부 롤백되므로 안전).
        val lastPr = pullRequestRepository.findFirstByToProjectOrderByNumberDesc(toProject)
        val nextNumber = (lastPr?.number ?: 0L) + 1L

        val pullRequest = PullRequest(
            title = title,
            body = body,
            fromProject = fromProject,
            toProject = toProject,
            fromBranch = fromBranch,
            toBranch = toBranch,
            contributor = contributor,
            state = State.OPEN,
            created = Instant.now(),
            updated = Instant.now(),
            number = nextNumber
        )

        val saved = pullRequestRepository.save(pullRequest)

        try {
            // 최초 커밋 목록의 PullRequestCommit 영속화/PullRequestEvent 기록까지 여기서 함께
            // 이뤄진다(알림만 isNewPullRequest=true라 생략됨).
            processMergeCheck(saved.id!!, contributor, isNewPullRequest = true)
        } catch (e: Exception) {
            // JGit merge 예외가 발생하더라도 PR 생성 자체는 허용
        }

        val title = "[${toProject.name}] 새 풀 리퀘스트: #${saved.number} ${saved.title}"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = contributor.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = saved.id.toString(),
            eventType = EventType.NEW_PULL_REQUEST,
            newValue = saved.body
        )
        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(contributor),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = saved.id.toString(),
            projectId = toProject.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        // 신규 PR 본문의 @멘션도 수신자에 포함한다.
        receivers.addAll(commentService.extractMentionedUsers(saved.body ?: ""))
        receivers.removeIf { it.id == contributor.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(saved, EventType.NEW_PULL_REQUEST, contributor.loginId, null, saved.body)

        return saved
    }

    @Transactional
    override fun updatePullRequest(
        pullRequestId: Long,
        title: String,
        body: String?,
        fromBranch: String,
        toBranch: String
    ): PullRequest {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }

        // yona hasSameBranchesWith()/findDuplicatedPullRequest() 대응 — 브랜치가 바뀌는 경우에만
        // 동일한 from/to 프로젝트·브랜치 조합의 OPEN PR이 이미 있는지 검사한다.
        if (pr.fromBranch != fromBranch || pr.toBranch != toBranch) {
            val duplicated = pullRequestRepository.findByFromBranchAndToBranchAndFromProjectAndToProjectAndState(
                fromBranch, toBranch, pr.fromProject, pr.toProject, State.OPEN
            )
            if (duplicated != null) {
                throw DuplicatedPullRequestException(
                    "동일한 브랜치 조합(from=$fromBranch, to=$toBranch)의 풀 리퀘스트가 이미 열려 있습니다: ${duplicated.id}"
                )
            }
        }

        // yona PullRequest.updateWith() 대응 — deleteIssueEvents() -> 필드 갱신 -> addNewIssueEvents()
        deleteIssueReferenceEvents(pr)

        pr.toBranch = toBranch
        pr.fromBranch = fromBranch
        pr.title = title
        pr.body = body
        pr.updated = Instant.now()
        val updated = pullRequestRepository.save(pr)

        addIssueReferenceEvents(updated)

        return updated
    }

    private fun deleteIssueReferenceEvents(pullRequest: PullRequest) {
        val newValue = pullRequest.id.toString()
        val oldEvents = issueEventRepository.findByNewValueAndSenderLoginIdAndEventType(
            newValue, pullRequest.contributor.loginId, EventType.ISSUE_REFERRED_FROM_PULL_REQUEST
        )
        if (oldEvents.isNotEmpty()) {
            issueEventRepository.deleteAll(oldEvents)
        }
    }

    // title+body에서 "#숫자" 형태로 참조된 이슈들을 toProject에서 찾아
    // ISSUE_REFERRED_FROM_PULL_REQUEST 이벤트를 새로 만든다.
    private fun addIssueReferenceEvents(pullRequest: PullRequest) {
        val issueNumbers = IssueReferenceParser.findReferredIssueNumbers(pullRequest.title + (pullRequest.body ?: ""))
        val newValue = pullRequest.id.toString()
        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(pullRequest.toProject, number) ?: continue
            issueEventRepository.save(
                IssueEvent(
                    issue = issue,
                    senderLoginId = pullRequest.contributor.loginId,
                    newValue = newValue,
                    created = Instant.now(),
                    eventType = EventType.ISSUE_REFERRED_FROM_PULL_REQUEST
                )
            )
        }
    }

    @Transactional
    override fun changeState(
        pullRequestId: Long,
        state: State,
        updaterLoginId: String
    ): PullRequest {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val oldState = pr.state
        if (oldState == state) {
            return pr
        }

        // 가드가 없으면 이미 실제 git 커밋까지 병합 완료된 PR이 CLOSED/OPEN을 오가며 상태만
        // 바뀌어(물리적으로는 여전히 병합된 채로) 화면/CLI에 "OPEN"으로 잘못 표시된다. MERGED는
        // 이 서비스 안에서 종결 상태다 — 여기로 들어오는 CLOSED/OPEN 전환
        // 요청(close/reopen 명령이 유일한 호출부, 위 merge()는 이 메서드를 거치지 않고 직접
        // pullRequestRepository.save()로 MERGED를 반영한다)만 막고, MERGED로의 전환 자체는
        // (State.MERGED 파라미터로 이 메서드를 호출하는 다른 내부 경로가 있을 수 있어) 막지 않는다.
        if (oldState == State.MERGED) {
            throw IllegalArgumentException("이미 머지된 풀 리퀘스트의 상태는 변경할 수 없습니다.")
        }

        pr.state = state
        pr.updated = Instant.now()
        val saved = pullRequestRepository.save(pr)

        val updater = userRepository.findByLoginId(updaterLoginId).orElse(null)
        val title = "[${saved.toProject.name}] PR #${saved.number} 상태 변경: $oldState -> $state"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = updater?.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = saved.id.toString(),
            eventType = EventType.PULL_REQUEST_STATE_CHANGED,
            oldValue = oldState.toString(),
            newValue = state.toString()
        )
        // yona NotificationEvent.getReceivers(sender, pullRequest)(=pullRequest.getWatchers() - sender) 대응.
        // 기존에는 contributor 한 명만 고정 수신자였는데, PR을 실제로 감시 중인 다른 사용자에게는
        // 전혀 알림이 가지 않는 누락이 있었다.
        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(saved.contributor),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = saved.id.toString(),
            projectId = saved.toProject.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(saved, EventType.PULL_REQUEST_STATE_CHANGED, updaterLoginId, oldState.toString(), state.toString())

        return saved
    }

    // draft-time 병합/취소 최적화는 recordWithDraftMerge에서 처리한다.
    private fun recordPullRequestEvent(
        pullRequest: PullRequest,
        eventType: EventType,
        senderLoginId: String?,
        oldValue: String?,
        newValue: String?
    ) {
        val event = PullRequestEvent(
            pullRequest = pullRequest,
            senderLoginId = senderLoginId,
            eventType = eventType,
            oldValue = oldValue,
            newValue = newValue,
            created = Instant.now()
        )
        pullRequestEventRepository.recordWithDraftMerge(event, meterRegistry)
    }

    @Transactional
    override fun addReviewer(pullRequestId: Long, reviewer: User) {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val user = userRepository.findById(reviewer.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${reviewer.id}") }

        if (pr.reviewers.add(user)) {
            pullRequestRepository.save(pr)
            notifyReviewerChanged(pr, user, "DONE")
        }
    }

    @Transactional
    override fun removeReviewer(pullRequestId: Long, reviewer: User) {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val user = userRepository.findById(reviewer.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${reviewer.id}") }

        if (pr.reviewers.remove(user)) {
            pullRequestRepository.save(pr)
            notifyReviewerChanged(pr, user, "CANCEL")
        }
    }


    // GitHub의 Approve/Request changes/Comment에 대응하는 PR 전체 판정을 새로 남긴다.
    // addReviewer()(자기등록)와 달리 이미 등록된 리뷰어인지 여부와 무관하게 언제나 새 이력을
    // 추가한다 — 자기등록과 판정은 별개 개념이다.
    @Transactional
    override fun submitReview(
        pullRequestId: Long,
        reviewer: User,
        state: PullRequestReview.ReviewState,
        body: String?
    ): PullRequestReview {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }

        // 설계 결정 3번(GitHub 방식 기본값) — 자기 자신의 PR은 자기가 승인/변경요청할 수 없다.
        // Comment는 자기 PR에도 허용한다(GitHub도 코멘트 자체는 막지 않는다).
        if (state != PullRequestReview.ReviewState.COMMENT && pr.contributor.id == reviewer.id) {
            throw SelfReviewException("자기 자신의 풀 리퀘스트는 승인하거나 변경을 요청할 수 없습니다.")
        }

        val review = pullRequestReviewRepository.save(
            PullRequestReview(
                pullRequest = pr,
                reviewer = reviewer,
                state = state,
                body = body,
                createdDate = Instant.now()
            )
        )

        notifyReviewed(pr, reviewer, state)

        return review
    }

    @Transactional(readOnly = true)
    override fun getReviews(pullRequestId: Long): List<PullRequestReview> {
        return pullRequestReviewRepository.findByPullRequestIdOrderByCreatedDateAsc(pullRequestId)
    }

    @Transactional(readOnly = true)
    override fun getLatestReviewStates(pullRequestId: Long): Map<Long, PullRequestReview.ReviewState> {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        return latestDecisiveReviewByReviewer(pr)
    }

    // yona NotificationEvent.afterReviewed() 대응 패턴 — notifyReviewerChanged()(자기등록/취소)와
    // 동일한 제목/수신자 규칙을 쓰되 eventType만 PULL_REQUEST_REVIEWED로 구분한다. body 전문은
    // NotificationEvent/PullRequestEvent 둘 다에 담지 않는다(PullRequestReview 자체가 원본 이력을
    // 이미 영속화하고 있고, 알림 문구에까지 자유 서식 본문을 그대로 노출하면 다른 PR 알림들의
    // "짧은 요약 + newValue" 관례와 어긋난다) — state만 newValue로 남긴다.
    private fun notifyReviewed(pullRequest: PullRequest, reviewer: User, state: PullRequestReview.ReviewState) {
        val notificationEvent = NotificationEvent(
            title = formatReplyTitle(pullRequest),
            senderId = reviewer.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            eventType = EventType.PULL_REQUEST_REVIEWED,
            newValue = state.name
        )
        val receivers = mutableSetOf(pullRequest.contributor)
        receivers.addAll(pullRequest.reviewers)
        receivers.removeIf { it.id == reviewer.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(pullRequest, EventType.PULL_REQUEST_REVIEWED, reviewer.loginId, null, state.name)
    }

    // IssueServiceImpl.updateIssue()의 assigneeId 처리와 동일하게, 기존 Assignee 로우를 재사용하지
    // 않고 매번 새로 만든다(Assignee는 (user, project) 값 객체에 가까움).
    @Transactional
    override fun setAssignee(pullRequestId: Long, assigneeUser: User?): PullRequest {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }

        pr.assignee = if (assigneeUser != null) {
            Assignee(user = assigneeUser, project = pr.toProject)
        } else {
            null
        }
        pr.updated = Instant.now()
        return pullRequestRepository.save(pr)
    }

    // 라벨 정의 자체는 만들지 않고 프로젝트에 이미 존재하는 IssueLabel만 참조한다.
    @Transactional
    override fun addLabel(pullRequestId: Long, labelId: Long): PullRequest {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val label = issueLabelRepository.findById(labelId)
            .orElseThrow { IllegalArgumentException("IssueLabel not found: $labelId") }

        // labelId를 id로만 조회하고 그 라벨이 실제로 이 PR의 프로젝트(toProject) 소속인지
        // 검증하지 않으면(IDOR), 컨트롤러가 URL 경로의 project에 대한 쓰기 권한만 확인하므로 자기
        // 프로젝트에 PR 라벨을 추가할 권한만 있으면 labelId를 다른(멤버가 아닌 PRIVATE) 프로젝트의
        // 라벨 번호로 바꿔 그 라벨을 노출·연결할 수 있다.
        if (label.project.id != pr.toProject.id) {
            throw IllegalArgumentException("IssueLabel not found: $labelId")
        }

        pr.labels.add(label)
        pr.updated = Instant.now()
        return pullRequestRepository.save(pr)
    }

    @Transactional
    override fun removeLabel(pullRequestId: Long, labelId: Long): PullRequest {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }

        pr.labels.removeIf { it.id == labelId }
        pr.updated = Instant.now()
        return pullRequestRepository.save(pr)
    }

    // CodeReviewServiceImpl.addReviewer/removeReviewer와 동일한 알림/타임라인 기록.
    // PullRequestController(REST)가 이 서비스를, ReviewApiController가 CodeReviewService를 각각
    // 사용하는 중복 구현 구조는 그대로 남아있지만, 최소한 두 경로 모두 알림이 발송되도록 맞춘다.
    // 리뷰어 참여/취소를 구분하는 임의의 문장 대신, 다른 PR 알림들과 동일한 "Re: [project] title
    // (#number)" 범용 포맷을 쓴다.
    private fun formatReplyTitle(pullRequest: PullRequest): String =
        "Re: [${pullRequest.toProject.name}] ${pullRequest.title} (#${pullRequest.number})"

    private fun notifyReviewerChanged(pullRequest: PullRequest, reviewer: User, newValue: String) {
        val notificationEvent = NotificationEvent(
            title = formatReplyTitle(pullRequest),
            senderId = reviewer.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED,
            // yona NotificationEvent.afterReviewed()의 oldValue = reviewAction.getOppositAction().name() 대응.
            oldValue = if (newValue == "DONE") "CANCEL" else "DONE",
            newValue = newValue
        )
        val receivers = mutableSetOf(pullRequest.contributor)
        receivers.addAll(pullRequest.reviewers)
        receivers.removeIf { it.id == reviewer.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(pullRequest, EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, reviewer.loginId, null, newValue)
    }

    override fun getDiff(pullRequest: PullRequest): List<FileDiff> {
        val playRepoA = repositoryService.getRepository(pullRequest.toProject)

        if (pullRequest.mergedCommitIdFrom != null && pullRequest.mergedCommitIdTo != null) {
            @Suppress("UNCHECKED_CAST")
            return playRepoA.getDiff(pullRequest.mergedCommitIdFrom!!, pullRequest.mergedCommitIdTo!!) as List<FileDiff>
        }
        
        val playRepoB = repositoryService.getRepository(pullRequest.fromProject)
        if (playRepoA is GitRepository && playRepoB is GitRepository) {
            val revA = pullRequest.toBranch
            val revB = pullRequest.lastCommitId ?: pullRequest.fromBranch
            return playRepoA.getDiff(revA, playRepoB, revB)
        }
        
        val revA = pullRequest.mergedCommitIdFrom ?: pullRequest.toBranch
        val revB = pullRequest.mergedCommitIdTo ?: pullRequest.lastCommitId ?: pullRequest.fromBranch
        @Suppress("UNCHECKED_CAST")
        return playRepoA.getDiff(revA, revB) as List<FileDiff>
    }

    override fun getDiff(pullRequest: PullRequest, commitId: String): List<FileDiff> {
        val project = if (pullRequest.state == State.MERGED) pullRequest.toProject else pullRequest.fromProject
        val playRepo = repositoryService.getRepository(project)
        @Suppress("UNCHECKED_CAST")
        return playRepo.getDiff(commitId) as List<FileDiff>
    }

    @Transactional
    override fun deleteFromBranch(pullRequestId: Long): PullRequest {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        if (pullRequest.state != State.MERGED) {
            throw InvalidBranchOperationException("병합된 PR만 원본 브랜치를 삭제할 수 있습니다.")
        }

        val playRepo = repositoryService.getRepository(pullRequest.fromProject)
        val branch = playRepo.getBranches().firstOrNull { isSameBranch(it.name, pullRequest.fromBranch) }
            ?: throw InvalidBranchOperationException("원본 브랜치를 찾을 수 없습니다: ${pullRequest.fromBranch}")

        playRepo.deleteBranch(pullRequest.fromBranch)
        pullRequest.lastCommitId = branch.headCommit.getId()

        return pullRequestRepository.save(pullRequest)
    }

    @Transactional
    override fun restoreFromBranch(pullRequestId: Long): PullRequest {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        val lastCommitId = pullRequest.lastCommitId
            ?: throw InvalidBranchOperationException("복원할 브랜치의 커밋 정보가 없습니다.")

        val playRepo = repositoryService.getRepository(pullRequest.fromProject)
        val alreadyExists = playRepo.getBranches().any { isSameBranch(it.name, pullRequest.fromBranch) }
        if (alreadyExists) {
            throw InvalidBranchOperationException("이미 존재하는 브랜치입니다: ${pullRequest.fromBranch}")
        }

        playRepo.createBranch(pullRequest.fromBranch, lastCommitId)

        return pullRequest
    }

    private fun isSameBranch(refName: String, branchName: String): Boolean {
        val shortRefName = refName.removePrefix("refs/heads/")
        val shortBranchName = branchName.removePrefix("refs/heads/")
        return shortRefName == shortBranchName
    }

    // ============================================================================================
    // Mercurial(hg4j) 대응.
    //
    // 설계 요약(상세 근거는 docs/yona-wiki/plans/p3-33-hg4j-incore-merge-commit.md 참고):
    //   - attemptMerge()/previewMerge()/processMergeCheck() 내부의 updateMerge()는 hg4j의
    //     TreeMergeCommand(작업 디렉터리를 전혀 건드리지 않는 순수 3-way merge 계산, JGit
    //     ThreeWayMerger와 동등)로 충돌/diff만 계산한다 — 서버가 공유하는 프로젝트 저장소의
    //     작업 디렉터리/dirstate를 절대 건드리지 않으므로 동시 요청 간 경합이 없다.
    //   - merge()(실제 확정 병합)도 동일하게 작업 디렉터리를 건드리지 않는다. hg4j
    //     MergeCommitCommand(TreeMergeResult + 두 부모 노드ID + 커밋 메타데이터만으로 changelog/
    //     manifest/filelog에 직접 새 리비전을 쓰는, JGit inCore 병합 커밋과 동등한 API)로
    //     toProject 저장소에 곧바로 병합 커밋을 만든다 — "toProject 전체를 임시 디렉터리에 클론 →
    //     체크아웃 → hg4j merge()/commit() → push → 임시 디렉터리 삭제" 방식은 비용이 "PR이 바꾼
    //     파일 수"가 아니라 "toProject 전체 크기"에 비례해 폐기했다. MergeCommitCommand는 항상
    //     명시적 2-parent changeset만 만들 뿐 "지름길(fast-forward)"이라는 개념 자체가 없으므로,
    //     이 앱의 기존 Git 구현(createMergeCommitAndUpdateRef, git의 --no-ff와 동일한 "항상 명시적
    //     머지 커밋" 정책)과 동일한 동작이 별도 보정 없이 자연스럽게 나온다.
    //   - fromProject != toProject(포크 PR)일 때 hg4j의 FetchCommand는 원격에만 있는 bookmark를
    //     로컬에 그대로 새로 만든다(BookmarkCommand.mergeFromRemote()) — Git의 임시 ref(병합
    //     확인 후 삭제, 목록에 노출 안 됨)와 달리 그대로 두면 fromBranch 이름의 bookmark가
    //     toProject에 영구히 노출된다. hgImportBranchWithoutBookmarkLeak()이 fetch 직후 "이번에
    //     새로 생긴" bookmark만 찾아 즉시 지워 이 흔적을 없앤다 — 다만 fromProject의 실제
    //     changeset 자체(changelog/manifest/filelog)는 Mercurial이 append-only라 되돌릴 방법이
    //     없어 toProject 저장소에 영구히 남는다(알려진 한계).
    //   - 동시성(JGit의 setExpectedOldObjectId와 동일한 낙관적 동시성): 병합 계산(TreeMergeCommand)에
    //     쓴 leftHex를 "이 병합이 전제한 toBranch의 기준점"으로 삼아, 실제 리비전을 쓰기 직전
    //     (hg4j MergeCommitCommand가 fail-fast lockStore()로 revlog 쓰기 구간을 보호하기 바로 전)에
    //     toBranch bookmark가 여전히 leftHex를 가리키는지 재확인한다. 다르면(그 사이 다른 병합/push가
    //     있었으면) 대기 없이 즉시 PullRequestException을 던져 호출자가 처음부터 재시도하게 한다 —
    //     "기다리는" 락은 이 저장소에서 실제 데드락을 겪은 전례가 있어 채택하지 않는다
    //     (HgRepository.lockStore(int)의 javadoc 참고).
    // ============================================================================================

    private data class HgMergeComputation(
        val conflict: Boolean,
        val commits: List<Commit>,
        val leftHex: String,
        val rightHex: String
    )

    // attemptMerge()/previewMerge()의 공용 계산 core. TreeMergeCommand는 순수 계산이라 여기서
    // 열어보는 Hg 인스턴스가 실제 toProject 저장소 그 자체여도(작업 디렉터리를 건드리지 않으므로)
    // 안전하다.
    private fun hgComputeMerge(
        toProject: Project,
        fromProject: Project,
        toBranchRef: String,
        fromBranchRef: String
    ): HgMergeComputation {
        val toDir = repositoryService.getRepository(toProject).getDirectory()
        val fromDir = repositoryService.getRepository(fromProject).getDirectory()
        val toBookmark = toBranchRef.removePrefix("refs/heads/")
        val fromBookmark = fromBranchRef.removePrefix("refs/heads/")

        return Hg.open(toDir).use { hg ->
            val leftHex = hgResolveRevisionHex(hg, toBookmark)
                ?: throw IllegalArgumentException("Target branch '$toBranchRef' not found")
            val rightHex = if (fromProject.id != toProject.id) {
                hgImportBranchWithoutBookmarkLeak(hg, fromDir, fromBookmark, toBookmark)
            } else {
                hgResolveRevisionHex(hg, fromBookmark)
            } ?: throw IllegalArgumentException("Source branch '$fromBranchRef' not found")

            val treeMergeResult = hg.treeMerge()
                .setOurs(NodeId.fromHex(leftHex).getBytes())
                .setTheirs(NodeId.fromHex(rightHex).getBytes())
                .call()

            val commits = hgDiffCommits(hg, leftHex, rightHex)
            HgMergeComputation(treeMergeResult.isConflicted, commits, leftHex, rightHex)
        }
    }

    // attemptMerge(pullRequestId)/(내부) updateMerge(pullRequestId) 공용 — 위 클래스 상단 주석
    // 참고, Hg에서는 이 둘의 동작이 동일하다(진짜 미리보기 머지 커밋을 만들지 않음).
    private fun hgAttemptMerge(pullRequest: PullRequest): PullRequestMergeResult {
        val computation = hgComputeMerge(
            pullRequest.toProject, pullRequest.fromProject, pullRequest.toBranch, pullRequest.fromBranch
        )
        val result = PullRequestMergeResult(pullRequest = pullRequest)
        if (!computation.conflict) {
            result.setResolvedStateOfPullRequest()
        } else {
            result.setConflictStateOfPullRequest()
        }
        result.gitCommits = computation.commits
        pullRequest.lastCommitId = computation.rightHex
        pullRequestRepository.save(pullRequest)
        return result
    }

    private fun hgPreviewMerge(
        fromProject: Project, toProject: Project, fromBranch: String, toBranch: String
    ): MergePreviewResult {
        val computation = hgComputeMerge(toProject, fromProject, toBranch, fromBranch)
        val (suggestedTitle, suggestedBody) = suggestTitleAndBody(computation.commits)
        return MergePreviewResult(
            commits = computation.commits,
            conflict = computation.conflict,
            suggestedTitle = suggestedTitle,
            suggestedBody = suggestedBody
        )
    }

    // merge()의 Hg 대응 — toProject 저장소를 직접 열어 hg4j MergeCommitCommand로 그 자리에서
    // 병합 커밋을 만든다(클래스 상단 주석 참고, 임시 클론/체크아웃/push/삭제 전부 제거).
    private fun hgMerge(pullRequest: PullRequest, updater: User): PullRequestMergeResult {
        val toProject = pullRequest.toProject
        val fromProject = pullRequest.fromProject
        val toDir = repositoryService.getRepository(toProject).getDirectory()
        val fromDir = repositoryService.getRepository(fromProject).getDirectory()
        val toBookmark = pullRequest.toBranch.removePrefix("refs/heads/")
        val fromBookmark = pullRequest.fromBranch.removePrefix("refs/heads/")

        return Hg.open(toDir).use { hg ->
            val leftHex = hgResolveRevisionHex(hg, toBookmark)
                ?: throw IllegalArgumentException("Target branch '${pullRequest.toBranch}' not found")
            val rightHex = if (fromProject.id != toProject.id) {
                val imported = hgImportBranchWithoutBookmarkLeak(hg, fromDir, fromBookmark, toBookmark)
                // fetch(fromDir)는 fromProject가 toBookmark와 "우연히 같은 이름"의 bookmark를
                // 갖고 있으면(예: 양쪽 다 기본 브랜치를 "master"라 부르는 흔한 경우)
                // BookmarkCommand.mergeFromRemote()가 그 이름의 로컬 bookmark까지 fromProject
                // 쪽 위치로 전진시켜버릴 수 있다 — 이건 fromBranch 콘텐츠를 가져오는 fetch의
                // 부수효과일 뿐 toBranch 자체가 실제로 이동한 게 아니므로, 병합 계산(leftHex)이
                // 전제한 toBranch 기준점으로 즉시 되돌린다(안 그러면 바로 다음 낙관적 동시성
                // 검사가 이 부수효과를 "동시 수정"으로 오판해 매번 실패한다).
                if (!leftHex.equals(hg.bookmark().call()[toBookmark], ignoreCase = true)) {
                    hg.bookmark().setBookmarkName(toBookmark).setRevision(leftHex).setForce(true).call()
                }
                imported
            } else {
                hgResolveRevisionHex(hg, fromBookmark)
            } ?: throw IllegalArgumentException("Source head ref not found")

            val diff = hgDiffCommits(hg, leftHex, rightHex)
            checkSignedCommitsForMerge(pullRequest, updater, diff)

            val treeMergeResult = hg.treeMerge()
                .setOurs(NodeId.fromHex(leftHex).getBytes())
                .setTheirs(NodeId.fromHex(rightHex).getBytes())
                .call()

            val result = PullRequestMergeResult(pullRequest = pullRequest)

            if (treeMergeResult.isConflicted) {
                result.setConflictStateOfPullRequest()
                result.gitCommits = diff
                pullRequestRepository.save(pullRequest)
                return@use result
            }

            // 낙관적 동시성 확인(클래스 상단 주석 참고, JGit setExpectedOldObjectId와 동일 지점의
            // 동일 검사) — 병합 계산에 쓴 leftHex 기준점에서, 실제 리비전을 쓰기 직전 시점에
            // toBranch가 여전히 그 기준점을 가리키는지 재확인한다. 다르면(그 사이 다른 병합/push가
            // toBranch를 옮겼으면) 대기 없이 즉시 실패시켜 호출자가 처음부터 재시도하게 한다.
            val currentToBookmarkHex = hg.bookmark().call()[toBookmark]
            if (!leftHex.equals(currentToBookmarkHex, ignoreCase = true)) {
                throw PullRequestException(
                    "브랜치 '$toBookmark'가 병합 계산 이후 동시에 이동했습니다" +
                        "(기준점 $leftHex, 현재 $currentToBookmarkHex) — 병합을 처음부터 다시 시도하십시오."
                )
            }

            val authorStr = "${updater.name} <${updater.email ?: "yona@yona.io"}>"
            val mergeCommitBytes = hg.mergeCommit()
                .setParents(NodeId.fromHex(leftHex).getBytes(), NodeId.fromHex(rightHex).getBytes())
                .setTreeMergeResult(treeMergeResult)
                .setAuthor(authorStr)
                .setMessage(makeMergeCommitMessage(pullRequest, diff))
                .call()
            val mergeCommitHex = NodeId(mergeCommitBytes).toHex()

            // MergeCommitCommand는 bookmark를 모르게 유지한다(클래스 상단 주석 참고, 단일 책임) —
            // 여기서 toBranch bookmark를 새 병합 커밋으로 전진시킨다. 새 커밋은 항상 leftHex의
            // 자손(parent1)이라 real hg의 fast-forward 판정을 그대로 통과해 force 없이도 이동된다.
            hg.bookmark().setBookmarkName(toBookmark).setRevision(mergeCommitHex).call()

            result.gitCommits = diff
            result.setMergedStateOfPullRequest(updater)
            pullRequest.mergedCommitIdFrom = leftHex
            pullRequest.mergedCommitIdTo = mergeCommitHex
            pullRequest.lastCommitId = rightHex
            pullRequest.received = Instant.now()
            pullRequest.isMerging = false

            pullRequestRepository.save(pullRequest)
            updatePullRequestCommits(pullRequest, diff)

            eventPublisher.publishEvent(
                PullRequestMergeEvent(
                    pullRequestId = pullRequest.id!!,
                    sender = updater,
                    isNewPullRequest = false
                )
            )

            result
        }
    }

    // 위 checkSignedCommitsForMerge(repo: Repository, ...) 오버로드의 Hg 대응.
    // Commit.getGpgVerificationStatus()가 이미 GitCommit/HgCommit 양쪽에서 정확히 동일한
    // gpgSignatureVerifier.verify(...) 호출로 구현돼 있어, VCS 종류에 무관하게 재사용 가능한 하나의
    // 검사 로직으로 작성한다.
    private fun checkSignedCommitsForMerge(pullRequest: PullRequest, updater: User, commits: List<Commit>) {
        val toProjectId = pullRequest.toProject.id ?: return
        val branch = pullRequest.toBranch.removePrefix("refs/heads/")
        val rule = findMatchingProtectedBranchRule(toProjectId, branch) ?: return
        if (!rule.requireSignedCommits) return
        if (rule.adminsCanBypass && isProjectManager(toProjectId, updater)) return

        for (commit in commits) {
            if (commit.getGpgVerificationStatus() != GpgVerificationStatus.VERIFIED) {
                throw BranchProtectionException(
                    "브랜치 '$branch'는 서명된 커밋만 병합할 수 있습니다(require_signed_commits) — " +
                        "커밋 ${commit.getId().take(8)}가 서명되지 않았거나 서명 검증에 실패했습니다."
                )
            }
        }
    }

    // Hg 브랜치(=bookmark) 이름/"tip"/40자 hex를 실제 hex 노드ID로 해석한다.
    // HgRepository.resolveRevisionNumber()와 목적은 같지만 그건 특정 (baseDir/owner/project)로
    // 고정된 저장소 전용 private 메서드라, 임시 클론까지 포함해 임의의 Hg 인스턴스를 다뤄야 하는
    // 여기서는 최소 기능만 별도로 재구현한다(named branch 조회까지는 필요 없음 — PR의 from/toBranch는
    // 항상 bookmark다).
    private fun hgResolveRevisionHex(hg: Hg, ref: String): String? {
        val name = ref.removePrefix("refs/heads/")
        return when {
            name.isEmpty() || name == "tip" || name == "HEAD" -> {
                val commits = hg.log().call()
                if (commits.isEmpty()) null else commits.first().nodeId.toHex()
            }
            name.length == 40 && name.matches(Regex("^[0-9a-fA-F]{40}$")) -> name
            else -> hg.bookmark().call()[name]
        }
    }

    // GitCommit 버전 diffCommits(repo, from, to)의 Hg 대응 — "to에는 있고 from에는 없는" 커밋을
    // 반환한다. hg4j의 LogCommand는 git의 addRange(from, to) 같은 리비전 범위 연산자가 없어(포셀린
    // API에 revset 지원이 없음) setFollowAncestors(true)로 각 브랜치의 전체 조상 집합을 구해 차집합을
    // 취한다 — 공통 조상은 어차피 diff 대상이 아니므로 이 방식으로도 git의 range 결과와 실질적으로
    // 동일한 집합이 나온다.
    private fun hgDiffCommits(hg: Hg, fromHex: String, toHex: String): List<Commit> {
        val allCommits = hg.log().call()
        val toAncestors = hg.log().setFollowAncestors(true).setStartRev(toHex).call().map { it.revision }.toSet()
        val fromAncestors = hg.log().setFollowAncestors(true).setStartRev(fromHex).call().map { it.revision }.toSet()
        val onlyInTo = toAncestors - fromAncestors
        val userResolver: (String?, String?) -> User? = { _, email ->
            if (email != null) userRepository.findByEmail(email).orElse(null) else null
        }
        return allCommits
            .filter { it.revision in onlyInTo }
            .map { native -> HgCommit(native, userResolver) { nativeCommit -> gpgSignatureVerifier.verify(nativeCommit) } }
    }

    // fromProject != toProject(포크 PR)일 때 fromProject의 브랜치를 toProject의 저장소로 가져온다.
    // hg4j의 FetchCommand는 작업 디렉터리/dirstate는 전혀 건드리지 않지만(PullCommand와 달리),
    // 원격에만 있던 bookmark는 그대로 로컬에 새로 만든다(BookmarkCommand.mergeFromRemote()) — Git의
    // 임시 ref(쓰고 나서 바로 삭제, 목록에 노출 안 됨)와 동등한 "겉보기 흔적 없음"을 위해 fetch로
    // 새로 생긴 bookmark만 찾아 즉시 지운다. 단, 가져온 changeset 자체는 Mercurial의 append-only
    // 저장 구조상 되돌릴 방법이 없어 toProject 저장소에 영구히 남는다(알려진 한계 — Git의 "임시 ref
    // 삭제로 사실상 dangling object가 되어 결국 GC됨"과 달리, Hg changelog에 한번 들어간 changeset은
    // hg strip 같은 파괴적 재작성 없이는 지울 수 없고, 공유 저장소에서 그런 재작성을 자동으로
    // 트리거하는 것은 안전하지 않다고 판단해 하지 않는다).
    // 반환값은 fetch 직후(=삭제 이전) 시점에 해석한 fromBookmark의 hex다 — 정리를 먼저 하고
    // 나중에 이름으로 다시 찾으려 하면(이전 버전의 버그) 지워버린 bookmark를 스스로 못 찾아
    // "Source ... not found"가 나므로, 반드시 해석 -> 정리 순서를 지킨다.
    private fun hgImportBranchWithoutBookmarkLeak(hg: Hg, fromDir: File, fromBookmark: String, toBookmark: String): String? {
        val before = hg.bookmark().call().keys.toSet()
        println("DEBUG before=$before fromDir=$fromDir")
        hg.fetch().setSource(fromDir.absolutePath).call()
        println("DEBUG afterFetch bookmarks=${hg.bookmark().call()}")
        val rightHex = hgResolveRevisionHex(hg, fromBookmark)
        println("DEBUG rightHex=$rightHex")
        if (fromBookmark != toBookmark && fromBookmark !in before) {
            val after = hg.bookmark().call()
            if (after.containsKey(fromBookmark)) {
                try {
                    hg.bookmark().setBookmarkName(fromBookmark).setDelete(true).call()
                } catch (e: Exception) {
                    // 정리 실패해도 병합 계산/실행 자체는 이미 끝난 뒤이므로 무시한다
                    // (best-effort cleanup — 임시 클론 쪽은 어차피 통째로 삭제되고, toProject
                    // 실제 저장소 쪽은 다음 preview/merge 호출 때 다시 정리가 시도된다).
                }
            }
        }
        return rightHex
    }
}
