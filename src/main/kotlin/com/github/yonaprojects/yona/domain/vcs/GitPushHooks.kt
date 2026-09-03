package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.event.GitPostReceiveEvent
import com.github.yonaprojects.yona.domain.event.RelatedPullRequestMergeEvent
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.User
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.eclipse.jgit.transport.PostReceiveHook
import org.eclipse.jgit.transport.PreReceiveHook
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceivePack
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant

private const val RESERVED_REF = "refs/yobi"
private const val RESERVED_REF_PREFIX = "refs/yobi/"
private const val BRANCH_PREFIX = "refs/heads/"
private val RECENTLY_PUSHED_WINDOW: Duration = Duration.ofHours(1)

/**
 * yona의 playRepository/hooks/RejectPushToReservedRefs.java 대응.
 * refs/yobi 하위 ref는 내부적으로 PR 병합 상태 추적 등에 쓰이는 예약 ref이므로
 * 클라이언트가 직접 push하지 못하도록 막는다.
 */
class RejectPushToReservedRefsPreReceiveHook : PreReceiveHook {
    override fun onPreReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
        for (command in commands) {
            val refName = command.refName
            if (refName == RESERVED_REF || refName.startsWith(RESERVED_REF_PREFIX)) {
                command.setResult(
                    ReceiveCommand.Result.REJECTED_OTHER_REASON,
                    "refs/yobi/* is reserved for internal use"
                )
            }
        }
    }
}

/**
 * yona의 UpdateLastPushedDate / NotifyPushedCommits / PullRequestCheck(브랜치 삭제 부분)를
 * 하나의 PostReceiveHook으로 통합 이식한 것.
 */
class YonaPostReceiveHook(
    private val project: Project,
    private val pusher: User,
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    // yona-wiki P3-01(Observability) 계측 지점 6 대응.
    private val meterRegistry: MeterRegistry
) : PostReceiveHook {

    override fun onPostReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
        val sample = Timer.start(meterRegistry)
        try {
            updateLastPushedDate()
            notifyPushedCommits(commands)
            notifyRelatedPullRequestsForUpdatedBranches(commands)
            cleanupPullRequestsForDeletedBranches(commands)
            updateRecentlyPushedBranches(commands)
        } finally {
            sample.stop(meterRegistry.timer("yona.git.push_hook.duration"))
        }
    }

    // yona playRepository/hooks/PullRequestCheck.java의 onPostReceive() 첫 번째 루프
    // (ReceiveCommandUtil.getUpdatedBranches→RelatedPullRequestMergingActor) 대응 (P1-146).
    // 새 커밋이 갱신된(생성이 아닌) 브랜치를 fromBranch로 하는 PR들의 병합/충돌 상태를 재검사해야
    // 하므로, UPDATE/UPDATE_NONFASTFORWARD 커맨드에 대해서만 이벤트를 발행한다.
    private fun notifyRelatedPullRequestsForUpdatedBranches(commands: Collection<ReceiveCommand>) {
        commands
            .filter {
                (it.type == ReceiveCommand.Type.UPDATE || it.type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) &&
                    it.refName.startsWith(BRANCH_PREFIX)
            }
            .map { it.refName.removePrefix(BRANCH_PREFIX) }
            .forEach { branch ->
                eventPublisher.publishEvent(RelatedPullRequestMergeEvent(project, branch, pusher))
            }
    }

    private fun updateLastPushedDate() {
        project.lastPushedDate = Instant.now()
        projectRepository.save(project)
    }

    private fun notifyPushedCommits(commands: Collection<ReceiveCommand>) {
        eventPublisher.publishEvent(GitPostReceiveEvent(project, pusher, commands.toList()))
    }

    private fun cleanupPullRequestsForDeletedBranches(commands: Collection<ReceiveCommand>) {
        commands
            .filter { it.type == ReceiveCommand.Type.DELETE && it.refName.startsWith(BRANCH_PREFIX) }
            .map { it.refName.removePrefix(BRANCH_PREFIX) }
            .forEach { branch ->
                val related = pullRequestRepository.findRelatedPullRequests(project, branch)
                if (related.isNotEmpty()) {
                    pullRequestRepository.deleteAll(related)
                }
            }
    }

    // yona playRepository/hooks/UpdateRecentlyPushedBranch.java 대응 (P1-24)
    private fun updateRecentlyPushedBranches(commands: Collection<ReceiveCommand>) {
        removeOldPushedBranches()
        saveRecentlyPushedBranches(commands)
        removeDeletedPushedBranches(commands)
    }

    private fun removeOldPushedBranches() {
        val cutoff = Instant.now().minus(RECENTLY_PUSHED_WINDOW)
        val old = pushedBranchRepository.findByProjectAndPushedDateBefore(project, cutoff)
        if (old.isNotEmpty()) {
            pushedBranchRepository.deleteAll(old)
        }
    }

    private fun saveRecentlyPushedBranches(commands: Collection<ReceiveCommand>) {
        val pushedBranches = commands
            .filter {
                it.type == ReceiveCommand.Type.CREATE ||
                    it.type == ReceiveCommand.Type.UPDATE ||
                    it.type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD
            }
            .filter { it.refName.startsWith(BRANCH_PREFIX) }
            .map { it.refName.removePrefix(BRANCH_PREFIX) }
            .toSet()

        meterRegistry.counter("yona.git.push_hook.pushed_branches").increment(pushedBranches.size.toDouble())

        for (branch in pushedBranches) {
            val existing = pushedBranchRepository.findByProjectAndName(project, branch).orElse(null)
            if (existing != null) {
                existing.pushedDate = Instant.now()
                pushedBranchRepository.save(existing)
                continue
            }

            // yona isNotExistsPushedBranch(): 이미 이 브랜치를 fromBranch로 하는 PR이 있으면
            // 별도로 PushedBranch를 만들지 않는다(PR 자체가 이미 그 브랜치를 추적하므로).
            if (pullRequestRepository.existsByFromProjectAndFromBranch(project, branch)) {
                continue
            }

            pushedBranchRepository.save(PushedBranch(name = branch, pushedDate = Instant.now(), project = project))
        }
    }

    private fun removeDeletedPushedBranches(commands: Collection<ReceiveCommand>) {
        commands
            .filter { it.type == ReceiveCommand.Type.DELETE && it.refName.startsWith(BRANCH_PREFIX) }
            .map { it.refName.removePrefix(BRANCH_PREFIX) }
            .forEach { branch ->
                pushedBranchRepository.findByProjectAndName(project, branch)
                    .ifPresent { pushedBranchRepository.delete(it) }
            }
    }
}
