package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.event.GitPostReceiveEvent
import com.github.yonaprojects.yona.domain.event.RelatedPullRequestMergeEvent
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.role.RoleType
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
 * yona-wiki P3-04(브랜치 보호) — legacy에는 대응 로직이 전혀 없는 신규 인프라. `ProtectedBranch`
 * (`domain/branchprotection/`)의 branch_pattern이 매칭되는 규칙을 찾아 직접 push를 정책대로
 * 거부한다. 검사 순서: (1) admins_can_bypass가 켜져 있고 pusher가 프로젝트 매니저면 이 규칙 전체를
 * 우회, (2) require_pull_request가 켜져 있으면 DELETE를 제외한 모든 직접 push(CREATE/UPDATE/
 * UPDATE_NONFASTFORWARD)를 거부(=PR 병합 경로로만 갱신 가능), (3) disallow_delete가 켜져 있으면
 * DELETE 거부, (4) disallow_force_push가 켜져 있으면 UPDATE_NONFASTFORWARD 거부, (5)
 * restrict_push_to가 설정돼 있으면 그 목록에 없는 pusher의 모든 push 거부. 여러 규칙이 같은
 * 브랜치에 매칭될 가능성(중복 patterns)은 이 계획의 DoD 범위 밖이라 첫 매칭 규칙만 적용한다.
 *
 * `RejectPushToReservedRefsPreReceiveHook`과 마찬가지로 `PreReceiveHookChain.newChain()`으로
 * 체이닝된다(`GitServletConfig` 참고) — refs/yobi 예약 ref 거부가 먼저 실행되므로 이미 다른
 * 이유로 거부된 커맨드는 건드리지 않는다(command.result가 NOT_ATTEMPTED일 때만 검사).
 */
class BranchProtectionPreReceiveHook(
    private val project: Project,
    private val pusher: User?,
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectUserRepository: ProjectUserRepository
) : PreReceiveHook {
    override fun onPreReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
        val projectId = project.id ?: return
        val rules = protectedBranchRepository.findByProjectId(projectId)
        if (rules.isEmpty()) return

        for (command in commands) {
            if (command.result != ReceiveCommand.Result.NOT_ATTEMPTED) continue
            if (!command.refName.startsWith(BRANCH_PREFIX)) continue
            val branch = command.refName.removePrefix(BRANCH_PREFIX)
            val rule = rules.firstOrNull { it.matches(branch) } ?: continue

            if (rule.adminsCanBypass && isProjectManager()) continue

            if (rule.requirePullRequest && command.type != ReceiveCommand.Type.DELETE) {
                reject(command, branch, "이 브랜치는 Pull Request를 통해서만 갱신할 수 있습니다(require_pull_request).")
                continue
            }
            if (rule.disallowDelete && command.type == ReceiveCommand.Type.DELETE) {
                reject(command, branch, "이 브랜치는 삭제할 수 없습니다(disallow_delete).")
                continue
            }
            if (rule.disallowForcePush && command.type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
                reject(command, branch, "이 브랜치는 강제 push할 수 없습니다(disallow_force_push).")
                continue
            }
            val allowedPushers = rule.restrictedLoginIds()
            if (allowedPushers.isNotEmpty() && pusher?.loginId !in allowedPushers) {
                reject(command, branch, "이 브랜치는 지정된 사용자만 push할 수 있습니다(restrict_push_to).")
                continue
            }
        }
    }

    private fun reject(command: ReceiveCommand, branch: String, reason: String) {
        command.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "branch '$branch' protected: $reason")
    }

    private fun isProjectManager(): Boolean {
        val userId = pusher?.id ?: return false
        val projectId = project.id ?: return false
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
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
